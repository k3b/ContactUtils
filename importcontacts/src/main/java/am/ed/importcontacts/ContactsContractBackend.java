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

import am.ed.importcontacts.ContactsCache.CacheIdentifier;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;

@TargetApi(5)
public class ContactsContractBackend implements Backend
{
	private Activity _activity = null;
	private HashMap< Long, Long > _aggregate_to_raw_ids = null;

	ContactsContractBackend( Activity activity )
	{
		_activity = activity;
		_aggregate_to_raw_ids = new HashMap< Long, Long >();
	}

	@Override
	public void populateCache( ContactsCache cache )
	{
		Cursor cur;

		// build a set of aggregate contact ids that haven't been added to the
		// cache yet
		HashSet< Long > unadded_ids = new HashSet< Long >();
		cur = _activity.getContentResolver().query(
			ContactsContract.Contacts.CONTENT_URI,
			new String[] {
				ContactsContract.Contacts._ID,
			}, null, null, null );
		while( cur.moveToNext() ) {
			Long id = cur.getLong(
				cur.getColumnIndex( ContactsContract.Contacts._ID ) );
			unadded_ids.add( id );
		}
		cur.close();

		// build a mapping of the ids of raw contacts to the ids of their
		// aggregate contacts
		HashMap< Long, Long > raw_to_aggregate_ids =
			new HashMap< Long, Long >();
		cur = _activity.getContentResolver().query(
			ContactsContract.RawContacts.CONTENT_URI,
			new String[] {
				ContactsContract.RawContacts._ID,
				ContactsContract.RawContacts.CONTACT_ID,
			}, ContactsContract.RawContacts.DELETED + " = 0", null, null );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong(
				cur.getColumnIndex( ContactsContract.RawContacts._ID ) );
			Long id = cur.getLong(
				cur.getColumnIndex( ContactsContract.RawContacts.CONTACT_ID ) );
			raw_to_aggregate_ids.put( raw_id, id );
		}
		cur.close();

		// get structured names, primary ones first
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.StructuredName.DISPLAY_NAME,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'",
			null, ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String name = cur.getString( cur.getColumnIndex(
					CommonDataKinds.StructuredName.DISPLAY_NAME ) );

				// if this is a name for a contact for whom we have not added a
				// lookup, add a lookup for the contact id by name
				if( unadded_ids.contains( id ) ) {
					CacheIdentifier cache_identifier = CacheIdentifier.factory(
						CacheIdentifier.Type.NAME, name );
					if( cache_identifier != null ) {
						cache.addLookup( cache_identifier, id );
						unadded_ids.remove( id );
					}
				}
			}
		}
		cur.close();

		// get contact organisations, primary ones first
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.Organization.COMPANY,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.Organization.CONTENT_ITEM_TYPE + "'",
			null, ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String organisation = cur.getString( cur.getColumnIndex(
					CommonDataKinds.Organization.COMPANY ) );

				// if this is an organisation name for a contact for whom we
				// have not added a lookup, add a lookup for the contact id
				// by organisation
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
		}
		cur.close();

		// get all phone numbers, primary ones first
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.Phone.NUMBER,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
			null, ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String number = cur.getString( cur.getColumnIndex(
					CommonDataKinds.Phone.NUMBER ) );

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
		}
		cur.close();

		// get all email addresses, primary ones first
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.Email.DATA,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'",
			null, ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String email = cur.getString( cur.getColumnIndex(
					CommonDataKinds.Email.DATA ) );

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
		}
		cur.close();

		// get all postal addresses, primary ones first
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE + "'",
			null, ContactsContract.Data.IS_PRIMARY + " DESC" );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String address = cur.getString( cur.getColumnIndex(
					CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS ) );

				// add associated data
				cache.addAssociatedAddress( id, address );
			}
		}
		cur.close();

		// get all notes
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.Note.NOTE,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.Note.CONTENT_ITEM_TYPE + "'",
			null, null );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String note = cur.getString( cur.getColumnIndex(
					CommonDataKinds.Note.NOTE ) );

				// add associated data
				cache.addAssociatedNote( id, note );
			}
		}
		cur.close();

		// get all birthdays
		cur = _activity.getContentResolver().query(
			ContactsContract.Data.CONTENT_URI,
			new String[] {
				ContactsContract.Data.RAW_CONTACT_ID,
				CommonDataKinds.Event.START_DATE,
			},
			ContactsContract.Data.MIMETYPE + " = '" +
				CommonDataKinds.Event.CONTENT_ITEM_TYPE + "' AND " +
				CommonDataKinds.Event.TYPE + " = '" +
				CommonDataKinds.Event.TYPE_BIRTHDAY + "'",
			null, null );
		while( cur.moveToNext() ) {
			Long raw_id = cur.getLong( cur.getColumnIndex(
				ContactsContract.Data.RAW_CONTACT_ID ) );
			Long id = raw_to_aggregate_ids.get( raw_id );
			if( id != null )
			{
				String birthday = cur.getString( cur.getColumnIndex(
					CommonDataKinds.Event.START_DATE ) );

				// add associated data
				cache.addAssociatedBirthday( id, birthday );
			}
		}
		cur.close();
	}

	@Override
	public void deleteContact( Long id )
	{
		Uri contact_uri = ContentUris.withAppendedId(
			ContactsContract.Contacts.CONTENT_URI, id );
		_activity.getContentResolver().delete( contact_uri, null, null );
	}

	@Override
	public Long addContact( String name ) throws ContactCreationException
	{
		// create raw contact
		ContentValues values = new ContentValues();
		Uri contact_uri = _activity.getContentResolver().insert(
			ContactsContract.RawContacts.CONTENT_URI, values);
		Long raw_id = ContentUris.parseId( contact_uri );
		if( raw_id == 0 ) throw new ContactCreationException();

		// add name data for this raw contact
		if( name != null ) {
			values.put( ContactsContract.Data.RAW_CONTACT_ID, raw_id );
			values.put( ContactsContract.Data.MIMETYPE,
				CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE );
			values.put( CommonDataKinds.StructuredName.DISPLAY_NAME, name );
			_activity.getContentResolver().insert(
				ContactsContract.Data.CONTENT_URI, values );
		}

		// find corresponding aggregate contact
		contact_uri = Uri.withAppendedPath(
			ContentUris.withAppendedId(
				ContactsContract.RawContacts.CONTENT_URI, raw_id ),
			ContactsContract.RawContacts.Entity.CONTENT_DIRECTORY );
		Cursor cur = _activity.getContentResolver().query( contact_uri,
			new String[] {
				ContactsContract.RawContacts.CONTACT_ID,
			}, null, null, null );
		Long id = null;
		if( cur.moveToNext() )
			id = cur.getLong(
				cur.getColumnIndex( ContactsContract.RawContacts.CONTACT_ID ) );
		cur.close();
		if( id == null || id == 0 )
		{
			// we didn't find an aggregate contact id, so try to clean up (by
			// deleting the raw contact we just created) before bailing
			contact_uri = ContentUris.withAppendedId(
				ContactsContract.RawContacts.CONTENT_URI, id );
			_activity.getContentResolver().delete( contact_uri, null, null );

			throw new ContactCreationException();
		}

		return id;
	}

	/**
	 * Obtain the raw contact id for the phone-only raw contact that is
	 * associated with the aggregate contact id.  One will be created if
	 * necessary.
	 *
	 * @param id the aggregate contact id
	 * @return the raw contact id
	 * @throws ContactCreationException
	 */
	Long obtainRawContact( Long id ) throws ContactCreationException
	{
		// attempt to lookup cached value
		Long raw_id = _aggregate_to_raw_ids.get( id );
		if( raw_id != null ) return raw_id;

		// find a corresponding raw contact that has no account name/type
		Cursor cur = _activity.getContentResolver().query(
			ContactsContract.RawContacts.CONTENT_URI,
			new String[] {
				ContactsContract.RawContacts._ID,
				ContactsContract.RawContacts.ACCOUNT_NAME,
			},
			ContactsContract.RawContacts.DELETED + " = 0 AND " +
				ContactsContract.RawContacts.CONTACT_ID + " = ? AND " +
				"IFNULL( " + ContactsContract.RawContacts.ACCOUNT_NAME +
					", '' ) = '' AND " +
				"IFNULL( " + ContactsContract.RawContacts.ACCOUNT_TYPE +
					", '' ) = ''",
			new String[] {
				String.valueOf( id ),
			}, null );
		if( cur.moveToNext() )
			raw_id = cur.getLong(
				cur.getColumnIndex( ContactsContract.RawContacts._ID ) );
		cur.close();

		// if one wasn't found, we'll need to create one
		if( raw_id == null ) {
			ContentValues values = new ContentValues();
			Uri contact_uri = _activity.getContentResolver().insert(
				ContactsContract.RawContacts.CONTENT_URI, values);
			raw_id = ContentUris.parseId( contact_uri );
			if( raw_id == 0 ) throw new ContactCreationException();
		}

		// save value in our cache
		_aggregate_to_raw_ids.put( id, raw_id );
		return raw_id;
	}

	private int convertTypeToBackendType( Class< ? > cls, int type )
		throws ContactCreationException
	{
		if( cls == CommonDataKinds.Phone.class )
		{
			switch( type )
			{
			case ContactData.TYPE_HOME:
				return CommonDataKinds.Phone.TYPE_HOME;
			case ContactData.TYPE_WORK:
				return CommonDataKinds.Phone.TYPE_WORK;
			case ContactData.TYPE_MOBILE:
				return CommonDataKinds.Phone.TYPE_MOBILE;
			case ContactData.TYPE_FAX_HOME:
				return CommonDataKinds.Phone.TYPE_FAX_HOME;
			case ContactData.TYPE_FAX_WORK:
				return CommonDataKinds.Phone.TYPE_FAX_WORK;
			case ContactData.TYPE_PAGER:
				return CommonDataKinds.Phone.TYPE_PAGER;
			}
		}
		else if( cls == CommonDataKinds.Email.class )
		{
			switch( type )
			{
			case ContactData.TYPE_HOME:
				return CommonDataKinds.Email.TYPE_HOME;
			case ContactData.TYPE_WORK:
				return CommonDataKinds.Email.TYPE_WORK;
			}
		}
		else if( cls == CommonDataKinds.StructuredPostal.class )
		{
			switch( type )
			{
			case ContactData.TYPE_HOME:
				return CommonDataKinds.StructuredPostal.TYPE_HOME;
			case ContactData.TYPE_WORK:
				return CommonDataKinds.StructuredPostal.TYPE_WORK;
			}
		}

		// still here?
		throw new ContactCreationException();
	}

	@Override
	public void addContactPhone( Long id, String number,
		ContactData.NumberDetail data ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.Phone.CONTENT_ITEM_TYPE );
		values.put( CommonDataKinds.Phone.TYPE,
			convertTypeToBackendType( CommonDataKinds.Phone.class,
				data.getType() ) );
		values.put( CommonDataKinds.Phone.NUMBER, number );
		if( data.isPreferred() )
			values.put( CommonDataKinds.Phone.IS_PRIMARY, 1 );

		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}

	@Override
	public void addContactEmail( Long id, String email,
		ContactData.EmailDetail data ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.Email.CONTENT_ITEM_TYPE );
		values.put( CommonDataKinds.Email.TYPE,
			convertTypeToBackendType( CommonDataKinds.Email.class,
				data.getType() ) );
		values.put( CommonDataKinds.Email.DATA, email );
		if( data.isPreferred() )
			values.put( CommonDataKinds.Email.IS_PRIMARY, 1 );

		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}

	@Override
	public void addContactAddresses( Long id, String address,
		ContactData.AddressDetail data ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE );
		values.put( CommonDataKinds.StructuredPostal.TYPE,
			convertTypeToBackendType( CommonDataKinds.StructuredPostal.class,
				data.getType() ) );
		values.put(
			CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address );

		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}

	@Override
	public void addContactOrganisation( Long id, String organisation,
		ContactData.OrganisationDetail data ) throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.Organization.CONTENT_ITEM_TYPE );
		values.put( CommonDataKinds.Organization.TYPE,
			CommonDataKinds.Organization.TYPE_WORK );
		values.put(
			CommonDataKinds.Organization.COMPANY, organisation );
		if( data.getExtra() != null )
			values.put( CommonDataKinds.Organization.TITLE, data.getExtra() );

		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}

	@Override
	public void addContactNote( Long id, String note )
		throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.Note.CONTENT_ITEM_TYPE );
		values.put(
			CommonDataKinds.Note.NOTE, note );

		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}

	@Override
	public void addContactBirthday( Long id, String birthday )
		throws ContactCreationException
	{
		ContentValues values = new ContentValues();
		values.put( ContactsContract.Data.RAW_CONTACT_ID,
			obtainRawContact( id ) );
		values.put( ContactsContract.Data.MIMETYPE,
			CommonDataKinds.Event.CONTENT_ITEM_TYPE );
		values.put(
			CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY );
		values.put(
			CommonDataKinds.Event.START_DATE, birthday );
		_activity.getContentResolver().insert(
			ContactsContract.Data.CONTENT_URI, values );
	}
}
