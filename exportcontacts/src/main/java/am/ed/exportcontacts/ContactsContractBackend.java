/*
 * ContactsContractContactAccessor.java
 *
 * Copyright (C) 2011 to 2013 Tim Marston <tim@ed.am>
 *
 * This file is part of the Export Contacts program (hereafter referred
 * to as "this program").  For more information, see
 * http://ed.am/dev/android/export-contacts
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package am.ed.exportcontacts;

import am.ed.exportcontacts.Exporter.ContactData;
import android.annotation.TargetApi;
import android.app.Activity;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;

@TargetApi(5)
public class ContactsContractBackend implements Backend
{
	Activity _activity = null;
	Exporter _exporter = null;
	Cursor _cur = null;

	public ContactsContractBackend( Activity activity,
		Exporter exporter )
	{
		_activity = activity;
		_exporter = exporter;
	}

	@Override
	public int getNumContacts()
	{
		// get number of aggregate contacts
		Cursor cur = _activity.getContentResolver().query(
			ContactsContract.Contacts.CONTENT_URI,
			new String[] {
				ContactsContract.Contacts._ID,
			}, null, null, null );
		int ret = cur.getCount();
		cur.close();
		return ret;
	}

	private int convertBackendTypeToType( Class< ? > cls, int type )
	{
		if( cls == CommonDataKinds.Phone.class )
		{
			switch( type )
			{
			case CommonDataKinds.Phone.TYPE_MOBILE:
				return ContactData.TYPE_MOBILE;
			case CommonDataKinds.Phone.TYPE_FAX_HOME:
				return ContactData.TYPE_FAX_HOME;
			case CommonDataKinds.Phone.TYPE_FAX_WORK:
				return ContactData.TYPE_FAX_WORK;
			case CommonDataKinds.Phone.TYPE_PAGER:
				return ContactData.TYPE_PAGER;
			case CommonDataKinds.Phone.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == CommonDataKinds.Email.class )
		{
			switch( type )
			{
			case CommonDataKinds.Email.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == CommonDataKinds.StructuredPostal.class )
		{
			switch( type )
			{
			case CommonDataKinds.StructuredPostal.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}

		return ContactData.TYPE_HOME;
	}

	@Override
	public boolean getNextContact( Exporter.ContactData contact )
	{
		// set up cursor
		if( _cur == null )
		{
			// get all aggregate contacts
			_cur = _activity.getContentResolver().query(
				ContactsContract.Contacts.CONTENT_URI,
				new String[] {
					ContactsContract.Contacts._ID,
					ContactsContract.Contacts.DISPLAY_NAME,
				}, null, null, null );
		}

		// if there are no more aggregate contacts, abort
		if( _cur == null ) return false;
		if( !_cur.moveToNext() ) {
			_cur.close();
			_cur = null;
			return false;
		}

		// get this aggregate contact's id
		Long id = _cur.getLong( _cur.getColumnIndex(
			ContactsContract.Contacts._ID ) );

		// create contact
		contact.setName( _cur.getString( _cur.getColumnIndex(
			ContactsContract.Contacts.DISPLAY_NAME ) ) );

		// get all contact data pertaining to the aggregate contact
		Cursor cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[]{
				ContactsContract.Data.MIMETYPE,
				ContactsContract.Data.IS_PRIMARY,
				ContactsContract.Data.DATA1,
				ContactsContract.Data.DATA2,
				ContactsContract.Data.DATA4,
			},
			ContactsContract.Data.CONTACT_ID + " = ? AND " +
// column DELETED not found!?
//				ContactsContract.Data.DELETED + " = 0 AND " +
				ContactsContract.Data.MIMETYPE + " IN ( ?, ?, ?, ?, ?, ? ) ",
			new String[] {
				String.valueOf( id ),
				CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
				CommonDataKinds.Email.CONTENT_ITEM_TYPE,
				CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
				CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
				CommonDataKinds.Note.CONTENT_ITEM_TYPE,
				CommonDataKinds.Event.CONTENT_ITEM_TYPE,
			},
			ContactsContract.Data.IS_SUPER_PRIMARY + " DESC, " +
				ContactsContract.Data.RAW_CONTACT_ID + ", " +
				ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() )
		{
			String type = cur.getString( cur.getColumnIndex(
				ContactsContract.Data.MIMETYPE ) );

			// add phone numbers
			if( type.equals( CommonDataKinds.Phone.CONTENT_ITEM_TYPE ) )
				contact.addNumber( contact.new NumberDetail(
					convertBackendTypeToType( CommonDataKinds.Phone.class,
						cur.getInt( cur.getColumnIndex(
							CommonDataKinds.Phone.TYPE ) ) ),
					cur.getString( cur.getColumnIndex(
						CommonDataKinds.Phone.NUMBER ) ) ) );

			// add email addresses
			else if( type.equals( CommonDataKinds.Email.CONTENT_ITEM_TYPE ) )
				contact.addEmail( contact.new EmailDetail(
					convertBackendTypeToType( CommonDataKinds.Email.class,
						cur.getInt( cur.getColumnIndex(
							CommonDataKinds.Email.TYPE ) ) ),
					cur.getString( cur.getColumnIndex(
						CommonDataKinds.Email.DATA ) ) ) );

			// add postal addresses
			else if( type.equals( CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ) )
				contact.addAddress( contact.new AddressDetail(
					convertBackendTypeToType( CommonDataKinds.StructuredPostal.class,
						cur.getInt( cur.getColumnIndex(
							CommonDataKinds.StructuredPostal.TYPE ) ) ),
					cur.getString( cur.getColumnIndex(
						CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS ) ) ) );

			// add organisations/titles
			else if( type.equals( CommonDataKinds.Organization.CONTENT_ITEM_TYPE ) )
				contact.addOrganisation( contact.new OrganisationDetail(
					cur.getString( cur.getColumnIndex(
						CommonDataKinds.Organization.COMPANY ) ),
					cur.getString( cur.getColumnIndex(
						CommonDataKinds.Organization.TITLE ) ) ) );

			// add notes
			else if( type.equals( CommonDataKinds.Note.CONTENT_ITEM_TYPE ) )
				contact.addNote( cur.getString( cur.getColumnIndex(
					CommonDataKinds.Note.NOTE ) ) );

			// add birthday
			else if( type.equals( CommonDataKinds.Event.CONTENT_ITEM_TYPE ) ) {
				int event = cur.getInt( cur.getColumnIndex(
					CommonDataKinds.Event.TYPE ) );
				if( event == CommonDataKinds.Event.TYPE_BIRTHDAY )
					contact.setBirthday( cur.getString( cur.getColumnIndex(
						CommonDataKinds.Event.START_DATE ) ) );
			}
		}
		cur.close();

		return true;
	}
}
