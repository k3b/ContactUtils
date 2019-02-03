/*
 * ContactsContactAccessor.java
 *
 * Copyright (C) 2011 to 2012 Tim Marston <tim@ed.am>
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
import android.app.Activity;
import android.database.Cursor;
import android.provider.Contacts;

@SuppressWarnings( "deprecation" )
public class ContactsBackend implements Backend
{
	Activity _activity = null;
	Exporter _exporter = null;
	Cursor _cur = null;

	public ContactsBackend( Activity activity, Exporter exporter )
	{
		_activity = activity;
		_exporter = exporter;
	}

	@Override
	public int getNumContacts()
	{
		Cursor cursor = _activity.managedQuery(
			Contacts.People.CONTENT_URI,
			new String[] {
				Contacts.People._ID,
			}, null, null, null );
		return cursor.getCount();
	}

	private int convertBackendTypeToType( Class< ? > cls, int type )
	{
		if( cls == Contacts.Phones.class )
		{
			switch( type )
			{
			case Contacts.PhonesColumns.TYPE_MOBILE:
				return ContactData.TYPE_MOBILE;
			case Contacts.PhonesColumns.TYPE_FAX_HOME:
				return ContactData.TYPE_FAX_HOME;
			case Contacts.PhonesColumns.TYPE_FAX_WORK:
				return ContactData.TYPE_FAX_WORK;
			case Contacts.PhonesColumns.TYPE_PAGER:
				return ContactData.TYPE_PAGER;
			case Contacts.PhonesColumns.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == Contacts.ContactMethods.class )
		{
			switch( type )
			{
			case Contacts.ContactMethodsColumns.TYPE_WORK:
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
			// get all contacts
			_cur = _activity.getContentResolver().query(
				Contacts.People.CONTENT_URI,
				new String[] {
					Contacts.People._ID,
					Contacts.People.NAME,
					Contacts.People.NOTES,
				}, null, null, null );
		}

		// if there are no more contacts, abort
		if( _cur == null ) return false;
		if( !_cur.moveToNext() ) {
			_cur.close();
			_cur = null;
			return false;
		}

		// get this contact's id
		Long id = _cur.getLong( _cur.getColumnIndex( Contacts.People._ID ) );

		// set name
		contact.setName(
			_cur.getString( _cur.getColumnIndex( Contacts.People.NAME ) ) );

		// add notes
		String note = _cur.getString(
			_cur.getColumnIndex( Contacts.People.NOTES ) );
		if( note != null && note.length() > 0 )
			contact.addNote( note );

		// add the organisations
		Cursor cur = _activity.getContentResolver().query(
			Contacts.Organizations.CONTENT_URI,
			new String[] {
				Contacts.Organizations.COMPANY,
				Contacts.Organizations.TITLE,
			}, Contacts.Organizations.PERSON_ID + " = ?",
			new String[] { id.toString() },
			Contacts.Organizations.ISPRIMARY + " DESC, " +
				Contacts.Organizations.PERSON_ID + " ASC" );
		while( cur.moveToNext() )
			contact.addOrganisation( contact.new OrganisationDetail(
				cur.getString( cur.getColumnIndex(
					Contacts.Organizations.COMPANY ) ),
				cur.getString( cur.getColumnIndex(
					Contacts.Organizations.TITLE ) ) ) );
		cur.close();

		// add the phone numbers
		cur = _activity.getContentResolver().query(
			Contacts.Phones.CONTENT_URI,
			new String[] {
				Contacts.Phones.NUMBER,
				Contacts.Phones.TYPE,
			}, Contacts.Phones.PERSON_ID + " = ?",
			new String[] { id.toString() },
			Contacts.Phones.ISPRIMARY + " DESC," +
				Contacts.Phones.PERSON_ID + " ASC" );
		while( cur.moveToNext() )
			contact.addNumber( contact.new NumberDetail(
				convertBackendTypeToType( Contacts.Phones.class,
					cur.getInt( cur.getColumnIndex( Contacts.Phones.TYPE ) ) ),
				cur.getString( cur.getColumnIndex(
					Contacts.Phones.NUMBER ) ) ) );
		cur.close();

		// add the email and postal addresses
		cur = _activity.getContentResolver().query(
			Contacts.ContactMethods.CONTENT_URI,
			new String[] {
				Contacts.ContactMethods.KIND,
				Contacts.ContactMethods.TYPE,
				Contacts.ContactMethods.DATA,
			},
			Contacts.ContactMethods.PERSON_ID + " = ? AND " +
				Contacts.ContactMethods.KIND + " IN( ?, ? )",
			new String[] {
				id.toString(),
				"" + Contacts.KIND_EMAIL,
				"" + Contacts.KIND_POSTAL,
			},
			Contacts.ContactMethods.ISPRIMARY + " DESC," +
				Contacts.ContactMethods.PERSON_ID + " ASC" );
		while( cur.moveToNext() ) {
			int kind = cur.getInt( cur.getColumnIndex(
				Contacts.ContactMethods.KIND ) );
			if( kind == Contacts.KIND_EMAIL )
				contact.addEmail( contact.new EmailDetail(
					convertBackendTypeToType( Contacts.ContactMethods.class,
						cur.getInt( cur.getColumnIndex(
							Contacts.ContactMethods.TYPE ) ) ),
					cur.getString( cur.getColumnIndex(
						Contacts.ContactMethods.DATA ) ) ) );
			else
				contact.addAddress( contact.new AddressDetail(
					convertBackendTypeToType( Contacts.ContactMethods.class,
						cur.getInt( cur.getColumnIndex(
							Contacts.ContactMethods.TYPE ) ) ),
					cur.getString( cur.getColumnIndex(
						Contacts.ContactMethods.DATA ) ) ) );
		}
		cur.close();

		return true;
	}
}
