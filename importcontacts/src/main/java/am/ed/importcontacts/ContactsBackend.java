/*
 * ContactsBackend.java
 *
 * Copyright (C) 2012 to 2013 Tim Marston <tim@ed.am>
 *
 * This file is part of the Import Contacts program (hereafter referred
 * to as "this program").  For more information, see
 * http://ed.am/dev/android/import-contacts
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

package am.ed.importcontacts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import am.ed.importcontacts.ContactsCache.CacheIdentifier;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts;

@SuppressWarnings( "deprecation" )
public class ContactsBackend implements Backend
{
	private Activity _activity = null;

	ContactsBackend( Activity activity )
	{
		_activity = activity;
	}

	@Override
	public void populateCache( ContactsCache cache )
	{
		Cursor cur;

		// set of contact ids that we have not yet added
		HashSet< Long > unadded_ids = new HashSet< Long >();

		// notes
		HashMap< Long, String > notes = new HashMap< Long, String >();

		// get all contacts
		cur = _activity.getContentResolver().query(
			Contacts.People.CONTENT_URI,
			new String[] {
				Contacts.People._ID,
				Contacts.People.NAME,
				Contacts.People.NOTES,
			}, null, null, null );
		while( cur.moveToNext() ) {
			Long id = cur.getLong(
				cur.getColumnIndex( Contacts.People._ID ) );
			String name = cur.getString(
					cur.getColumnIndex( Contacts.People.NAME ) );
			String note = cur.getString(
					cur.getColumnIndex( Contacts.People.NOTES ) );

			// if we can, add a lookup for the contact id by name
			CacheIdentifier cache_identifier = CacheIdentifier.factory(
				CacheIdentifier.Type.NAME, name );
			if( cache_identifier != null ) {
				cache.addLookup( cache_identifier, id );

				// add any associated notes (this would get done at the end but,
				// since it is most common that contacts are identified by name,
				// it is worth doing a special case here
				cache.addAssociatedNote( id, note );
			}
			else
			{
				// record that a lookup for this contact's id still needs to be
				// added by some other means
				unadded_ids.add( id );

				// store this contact's notes, so that they can be added to the
				// cache at the end, after this contact has been added (by
				// whatever identifying means)
				if( note != null && note.length() > 0 )
					notes.put( id, note );
			}
		}
		cur.close();

		// get contact organisations, primary ones first
		cur = _activity.getContentResolver().query(
			Contacts.Organizations.CONTENT_URI,
			new String[] {
				Contacts.Phones.PERSON_ID,
				Contacts.Organizations.COMPANY,
			}, null, null, Contacts.Organizations.ISPRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long id = cur.getLong( cur.getColumnIndex(
				Contacts.Organizations.PERSON_ID ) );
			String organisation = cur.getString(
				cur.getColumnIndex( Contacts.Organizations.COMPANY ) );

			// if this is an organisation name for a contact for whom we have
			// not added a lookup, add a lookup for the contact id by
			// organisation
			if( unadded_ids.contains( id ) ) {
				CacheIdentifier cache_identifier = CacheIdentifier.factory(
					CacheIdentifier.Type.ORGANISATION, organisation );
				if( cache_identifier != null ) {
					cache.addLookup( cache_identifier, id );
					unadded_ids.remove( id );
				}
			}

			// add associated data
			cache.addAssociatedOrganisation( id, organisation );
		}
		cur.close();

		// get all phone numbers, primary ones first
		cur = _activity.getContentResolver().query(
			Contacts.Phones.CONTENT_URI,
			new String[] {
				Contacts.Phones.PERSON_ID,
				Contacts.Phones.NUMBER,
			}, null, null, Contacts.Phones.ISPRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long id = cur.getLong(
				cur.getColumnIndex( Contacts.Phones.PERSON_ID ) );
			String number = cur.getString(
				cur.getColumnIndex( Contacts.Phones.NUMBER ) );

			// if this is a number for a contact for whom we have not
			// added a lookup, add a lookup for the contact id by phone
			// number
			if( unadded_ids.contains( id ) ) {
				CacheIdentifier cache_identifier = CacheIdentifier.factory(
					CacheIdentifier.Type.PRIMARY_NUMBER, number );
				if( cache_identifier != null ) {
					cache.addLookup( cache_identifier, id );
					unadded_ids.remove( id );
				}
			}

			// add associated data
			cache.addAssociatedNumber( id, number );
		}
		cur.close();

		// now get all email addresses, primary ones first, and postal addresses
		cur = _activity.getContentResolver().query(
			Contacts.ContactMethods.CONTENT_URI,
			new String[] {
				Contacts.ContactMethods.PERSON_ID,
				Contacts.ContactMethods.DATA,
				Contacts.ContactMethods.KIND,
			}, Contacts.ContactMethods.KIND + " IN( ?, ? )",
			new String[] {
				"" + Contacts.KIND_EMAIL,
				"" + Contacts.KIND_POSTAL,
			}, Contacts.ContactMethods.ISPRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long id = cur.getLong(
				cur.getColumnIndex( Contacts.ContactMethods.PERSON_ID ) );
			int kind = cur.getInt(
				cur.getColumnIndex( Contacts.ContactMethods.KIND ) );
			if( kind == Contacts.KIND_EMAIL )
			{
				String email = cur.getString(
					cur.getColumnIndex( Contacts.ContactMethods.DATA ) );

				// if this is an email address for a contact for whom we have
				// not added a lookup, add a lookup for the contact id by email
				// address
				if( unadded_ids.contains( id ) ) {
					CacheIdentifier cache_identifier = CacheIdentifier.factory(
						CacheIdentifier.Type.PRIMARY_EMAIL, email );
					if( cache_identifier != null ) {
						cache.addLookup( cache_identifier, id );
						unadded_ids.remove( id );
					}
				}

				// add associated data
				cache.addAssociatedEmail( id, email );
			}
			else if( kind == Contacts.KIND_POSTAL )
			{
				String address = cur.getString(
					cur.getColumnIndex( Contacts.ContactMethods.DATA ) );

				// add associated data
				cache.addAssociatedAddress( id, address );
			}
		}
		cur.close();

		// finally, add the notes that we stored earlier (we have to add these
		// at the end because we can't be sure which piece of contact data will
		// cause the contact to be added to the cache
		Iterator< Long > i = notes.keySet().iterator();
		while( i.hasNext() ) {
			Long id = i.next();
			cache.addAssociatedNote( id, notes.get( id ) );
		}
	}

	@Override
	public void deleteContact( Long id )
	{
		Uri contact_uri =
			ContentUris.withAppendedId( Contacts.People.CONTENT_URI, id );
		_activity.getContentResolver().delete( contact_uri, null, null );
	}

	@Override
	public Long addContact( String name ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		if( name != null )
			values.put( Contacts.People.NAME, name );
		Uri contact_uri = _activity.getContentResolver().insert(
			Contacts.People.CONTENT_URI, values );
		Long id = ContentUris.parseId( contact_uri );
		if( id == 0 )
			throw new ContactCreationException();

		// try to add them to the "My Contacts" group
		try {
			Contacts.People.addToMyContactsGroup(
				_activity.getContentResolver(), id );
		}
		catch( IllegalStateException e ) {
			// ignore any failure
		}

		return id;
	}

	private int convertTypeToBackendType( Class< ? > cls, int type )
		throws ContactCreationException
	{
		if( cls == Contacts.Phones.class )
		{
			switch( type )
			{
			case ContactData.TYPE_HOME:
				return Contacts.PhonesColumns.TYPE_HOME;
			case ContactData.TYPE_WORK:
				return Contacts.PhonesColumns.TYPE_WORK;
			case ContactData.TYPE_MOBILE:
				return Contacts.PhonesColumns.TYPE_MOBILE;
			case ContactData.TYPE_FAX_HOME:
				return Contacts.PhonesColumns.TYPE_FAX_HOME;
			case ContactData.TYPE_FAX_WORK:
				return Contacts.PhonesColumns.TYPE_FAX_WORK;
			case ContactData.TYPE_PAGER:
				return Contacts.PhonesColumns.TYPE_PAGER;
			}
		}
		else if( cls == Contacts.ContactMethods.class )
		{
			switch( type )
			{
			case ContactData.TYPE_HOME:
				return Contacts.ContactMethodsColumns.TYPE_HOME;
			case ContactData.TYPE_WORK:
				return Contacts.ContactMethodsColumns.TYPE_WORK;
			}
		}

		// still here?
		throw new ContactCreationException();
	}

	@Override
	public void addContactPhone( Long id, String number,
		ContactData.NumberDetail data ) throws ContactCreationException
	{
		Uri contact_phones_uri = Uri.withAppendedPath(
			ContentUris.withAppendedId( Contacts.People.CONTENT_URI, id ),
			Contacts.People.Phones.CONTENT_DIRECTORY );

		ContentValues values = new ContentValues();
		values.put( Contacts.Phones.TYPE,
			convertTypeToBackendType( Contacts.Phones.class,
				data.getType() ) );
		values.put( Contacts.Phones.NUMBER, number );
		if( data.isPreferred() )
			values.put( Contacts.Phones.ISPRIMARY, 1 );

		_activity.getContentResolver().insert( contact_phones_uri, values );
	}

	@Override
	public void addContactEmail( Long id, String email,
		ContactData.EmailDetail data ) throws ContactCreationException
	{
		Uri contact_contact_methods_uri = Uri.withAppendedPath(
			ContentUris.withAppendedId( Contacts.People.CONTENT_URI, id ),
			Contacts.People.ContactMethods.CONTENT_DIRECTORY );

		ContentValues values = new ContentValues();
		values.put( Contacts.ContactMethods.KIND, Contacts.KIND_EMAIL );
		values.put( Contacts.ContactMethods.DATA, email );
		values.put( Contacts.ContactMethods.TYPE,
			convertTypeToBackendType( Contacts.ContactMethods.class,
				data.getType() ) );
		if( data.isPreferred() )
			values.put( Contacts.ContactMethods.ISPRIMARY, 1 );

		_activity.getContentResolver().insert( contact_contact_methods_uri,
			values );
	}

	@Override
	public void addContactAddresses( Long id, String address,
		ContactData.AddressDetail data ) throws ContactCreationException
	{
		Uri contact_contact_methods_uri = Uri.withAppendedPath(
			ContentUris.withAppendedId( Contacts.People.CONTENT_URI, id ),
			Contacts.People.ContactMethods.CONTENT_DIRECTORY );

		ContentValues values = new ContentValues();
		values.put( Contacts.ContactMethods.KIND, Contacts.KIND_POSTAL );
		values.put( Contacts.ContactMethods.DATA, address );
		values.put( Contacts.ContactMethods.TYPE,
			convertTypeToBackendType( Contacts.ContactMethods.class,
				data.getType() ) );

		_activity.getContentResolver().insert( contact_contact_methods_uri,
			values );
	}

	@Override
	public void addContactOrganisation( Long id, String organisation,
		ContactData.OrganisationDetail data ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( Contacts.Organizations.PERSON_ID, id );
		values.put( Contacts.Organizations.COMPANY, organisation );
		values.put( Contacts.ContactMethods.TYPE,
			Contacts.OrganizationColumns.TYPE_WORK );
		if( data.getExtra() != null )
			values.put( Contacts.Organizations.TITLE, data.getExtra() );

		_activity.getContentResolver().insert(
			Contacts.Organizations.CONTENT_URI, values );
	}

	@Override
	public void addContactNote( Long id, String note )
		throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( Contacts.People.NOTES, note );
		_activity.getContentResolver().update(
			ContentUris.withAppendedId( Contacts.People.CONTENT_URI, id ),
			values, null, null );
	}

	@Override
	public void addContactBirthday( Long id, String birthday )
		throws ContactCreationException
	{
		// this contacts API doesn't support birthdays, so just ignore them
	}
}
