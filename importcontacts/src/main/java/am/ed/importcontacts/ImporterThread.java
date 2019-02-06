/*
 * ImporterThread.java
 *
 * Copyright (C) 2009 to 2013 Tim Marston <tim@ed.am>
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import am.ed.importcontacts.Backend.ContactCreationException;
import de.k3b.contactlib.ContactData;
import de.k3b.contactlib.ContactsCache;

import android.content.SharedPreferences;
import android.os.Message;

public class ImporterThread extends Thread
{
	public final static int ACTION_ABORT = 1;
	public final static int ACTION_ALLDONE = 2;

	public final static int RESPONSE_NEGATIVE = 0;
	public final static int RESPONSE_POSITIVE = 1;

	public final static int RESPONSEEXTRA_NONE = 0;
	public final static int RESPONSEEXTRA_ALWAYS = 1;

	private ImportActivity _importActivity;
	private int _response;
	private int _response_extra;
	private int _merge_setting;
	private int _last_merge_decision;
	private boolean _abort = false;
	private boolean _is_finished = false;
	private ContactsCache _contacts_cache = null;
	private Backend _backend = null;

	@SuppressWarnings("serial")
	protected class AbortImportException extends Exception { };

	public ImporterThread(ImportActivity importActivity)
	{
		_importActivity = importActivity;

		SharedPreferences prefs = getSharedPreferences();
		_merge_setting = prefs.getInt( "merge_setting", ImportActivity.ACTION_PROMPT );
	}

	@Override
	public void run()
	{
		try
		{
			// update UI
			setProgressMessage( R.string.doit_caching );

			// create the appropriate backend
			if( Integer.parseInt( android.os.Build.VERSION.SDK ) >= 5 )
				_backend = new ContactsContractBackend(_importActivity);
			else
				_backend = new ContactsBackend(_importActivity);

			// create a cache of existing contacts and populate it
			_contacts_cache = new ContactsCache();
			_backend.populateCache( _contacts_cache );

			// do the import
			onImport();

			// done!
			finish( ACTION_ALLDONE );
		}
		catch( AbortImportException e )
		{}

		// flag as finished to prevent interrupts
		setIsFinished();
	}

	synchronized private void setIsFinished()
	{
		_is_finished = true;
	}

	protected void onImport() throws AbortImportException
	{
	}

	public void wake()
	{
		wake( 0, RESPONSEEXTRA_NONE );
	}

	public void wake( int response )
	{
		wake( response, RESPONSEEXTRA_NONE );
	}

	synchronized public void wake( int response, int response_extra )
	{
		_response = response;
		_response_extra = response_extra;
		notify();
	}

	synchronized public boolean setAbort()
	{
		if( !_is_finished && !_abort ) {
			_abort = true;
			notify();
			return true;
		}
		return false;
	}

	protected SharedPreferences getSharedPreferences()
	{
		return _importActivity.getSharedPreferences();
	}

	protected void showError( int res ) throws AbortImportException
	{
		showError( _importActivity.getText( res ).toString() );
	}

	synchronized protected void showError( String message )
			throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain(
			_importActivity._handler, ImportActivity.MESSAGE_ERROR, message ) );
		try {
			wait();
		}
		catch( InterruptedException e ) { }

		// no need to check if an abortion happened during the wait, we are
		// about to finish anyway!
		finish( ACTION_ABORT );
	}

	protected void showContinueOrAbort( int res ) throws AbortImportException
	{
		showContinueOrAbort( _importActivity.getText( res ).toString() );
	}

	synchronized protected void showContinueOrAbort( String message )
			throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain(
			_importActivity._handler, ImportActivity.MESSAGE_CONTINUEORABORT, message ) );
		try {
			wait();
		}
		catch( InterruptedException e ) { }

		// if we're aborting, there's no need to check if an abortion happened
		// during the wait
		if( _response == RESPONSE_NEGATIVE )
			finish( ACTION_ABORT );
		else
			checkAbort();
	}

	protected void setProgressMessage( int res ) throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain( _importActivity._handler,
			ImportActivity.MESSAGE_SETPROGRESSMESSAGE, getText( res ) ) );
	}

	protected void setProgressMax( int max_progress )
			throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain(
			_importActivity._handler, ImportActivity.MESSAGE_SETMAXPROGRESS,
			Integer.valueOf( max_progress ) ) );
	}

	protected void setTmpProgress( int tmp_progress )
		throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain(
			_importActivity._handler, ImportActivity.MESSAGE_SETTMPPROGRESS,
			Integer.valueOf( tmp_progress ) ) );
	}

	protected void setProgress( int progress ) throws AbortImportException
	{
		checkAbort();
		_importActivity._handler.sendMessage( Message.obtain(
			_importActivity._handler, ImportActivity.MESSAGE_SETPROGRESS,
			Integer.valueOf( progress ) ) );
	}

	protected void finish( int action ) throws AbortImportException
	{
		// update UI to reflect action
		int message;
		switch( action )
		{
		case ACTION_ALLDONE:	message = ImportActivity.MESSAGE_ALLDONE; break;
		default:	// fall through
		case ACTION_ABORT:		message = ImportActivity.MESSAGE_ABORT; break;
		}
		_importActivity._handler.sendEmptyMessage( message );

		// stop
		throw new AbortImportException();
	}

	protected CharSequence getText( int res )
	{
		return _importActivity.getText( res );
	}

	/**
	 * Should we skip a contact, given whether it exists or not and the current
	 * merge setting?  This routine handles throwing up a prompt, if required.
	 *
	 * @param contact_detail the display name of the contact
	 * @param exists true if this contact matches one in the cache
	 * @param merge_setting the merge setting to use
	 * @return true if the contact should be skipped outright
	 * @throws AbortImportException
	 */
	synchronized private boolean shouldWeSkipContact( String contact_detail,
		boolean exists, int merge_setting ) throws AbortImportException
	{
		_last_merge_decision = merge_setting;

		// handle special cases
		switch( merge_setting )
		{
		case ImportActivity.ACTION_KEEP:
			// if we are skipping on a duplicate, check for one
			return exists;

		case ImportActivity.ACTION_PROMPT:
			// if we are prompting on duplicate, then we can say that we won't
			// skip if there isn't one
			if( !exists ) return false;

			// ok, duplicate exists, so do prompt
			_importActivity._handler.sendMessage( Message.obtain( _importActivity._handler,
				ImportActivity.MESSAGE_MERGEPROMPT, contact_detail ) );
			try {
				wait();
			}
			catch( InterruptedException e ) { }

			// check if an abortion happened during the wait
			checkAbort();

			// if "always" was selected, make choice permanent
			if( _response_extra == RESPONSEEXTRA_ALWAYS )
				_merge_setting = _response;

			// recurse, with our new merge setting
			return shouldWeSkipContact( contact_detail, exists, _response );
		}

		// for all other cases (either overwriting or merging) we don't skip
		return false;
	}

	protected void skipContact() throws AbortImportException
	{
		checkAbort();

		// show that we're skipping a new contact
		_importActivity._handler.sendEmptyMessage( ImportActivity.MESSAGE_CONTACTSKIPPED );
	}

	protected void importContact( ContactData contact )
			throws AbortImportException
	{
		checkAbort();

		// It is expected that we use contact.getCacheIdentifier() here.  The
		// contact we are passed should have been successfully finalise()d,
		// which includes generating a valid cache identifier.
		ContactsCache.CacheIdentifier cache_identifier =
			contact.getCacheIdentifier();

//		if( !showContinue( "====[ IMPORTING ]====\n: " + contact._name ) )
//			finish( ACTION_ABORT );

		// attempt to lookup the id of an existing contact in the cache with
		// this contact data's cache identifier
		Long id = (Long)_contacts_cache.lookup( cache_identifier );

		// check to see if this contact should be skipped
		if( shouldWeSkipContact( cache_identifier.getDetail(), id != null,
			_merge_setting ) )
		{
			// show that we're skipping a contact
			_importActivity._handler.sendEmptyMessage( ImportActivity.MESSAGE_CONTACTSKIPPED );
			return;
		}

		// if a contact exists, and we're overwriting, destroy the existing
		// contact before importing
		boolean contact_deleted = false;
		if( id != null && _last_merge_decision == ImportActivity.ACTION_OVERWRITE )
		{
			contact_deleted = true;

			// remove from device
			_backend.deleteContact( id );

			// update cache
			_contacts_cache.removeLookup( cache_identifier );
			_contacts_cache.removeAssociatedData( id );

			// show that we're overwriting a contact
			_importActivity._handler.sendEmptyMessage( ImportActivity.MESSAGE_CONTACTOVERWRITTEN );

			// discard the contact id
			id = null;
		}

		try {
			// if we don't have a contact id yet (or we did, but we destroyed it
			// when we deleted the contact), we'll have to create a new contact
			if( id == null )
			{
				// create a new contact
				id = _backend.addContact( contact.getName() );

				// update cache
				_contacts_cache.addLookup( cache_identifier, id );

				// if we haven't already shown that we're overwriting a contact,
				// show that we're creating a new contact
				if( !contact_deleted )
					_importActivity._handler.sendEmptyMessage(
						ImportActivity.MESSAGE_CONTACTCREATED );
			}
			else
				// show that we're merging with an existing contact
				_importActivity._handler.sendEmptyMessage( ImportActivity.MESSAGE_CONTACTMERGED );

			// import contact parts
			if( contact.hasNumbers() )
				importContactPhones( id, contact.getNumbers() );
			if( contact.hasEmails() )
				importContactEmails( id, contact.getEmails() );
			if( contact.hasAddresses() )
				importContactAddresses( id, contact.getAddresses() );
			if( contact.hasOrganisations() )
				importContactOrganisations( id, contact.getOrganisations() );
			if( contact.hasNotes() )
				importContactNotes( id, contact.getNotes() );
			if( contact.hasBirthday() )
				importContactBirthday( id, contact.getBirthday() );
		}
		catch( Backend.ContactCreationException e )
		{
			showError( R.string.error_unabletoaddcontact );
		}
	}

	private void importContactPhones( Long id,
		Map< String, ContactData.NumberDetail > datas )
		throws ContactCreationException
	{
		// add phone numbers
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String number = i.next();
			ContactData.NumberDetail data = datas.get( number );

			// We don't want to add this number if it's crap, or it already
			// exists (which would cause a duplicate to be created).  We don't
			// take in to account the type when checking for duplicates.  This
			// is intentional: types aren't really very reliable.  We assume
			// that if the number exists at all, it doesn't need importing.
			// Because of this, we also can't update the cache (which we don't
			// need to anyway, so it's not a problem).
			if( _contacts_cache.hasAssociatedNumber( id, number ) )
				continue;

			// add phone number
			_backend.addContactPhone( id, number, data );

			// and add this address to the cache to prevent a addition of
			// duplicate date from another file
			_contacts_cache.addAssociatedNumber( id, number );
		}
	}

	private void importContactEmails( Long id,
		Map< String, ContactData.EmailDetail > datas )
		throws ContactCreationException
	{
		// add email addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String email = i.next();
			ContactData.EmailDetail data = datas.get( email );

			// we don't want to add this email address if it exists already or
			// we would introduce duplicates
			if( _contacts_cache.hasAssociatedEmail( id, email ) )
				continue;

			// add phone number
			_backend.addContactEmail( id, email, data );

			// and add this address to the cache to prevent a addition of
			// duplicate date from another file
			_contacts_cache.addAssociatedEmail( id, email );
		}
	}

	private void importContactAddresses( Long id,
		Map< String, ContactData.AddressDetail > datas )
		throws ContactCreationException
	{
		// add addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String address = i.next();
			ContactData.AddressDetail data = datas.get( address );

			// we don't want to add this address if it exists already or we
			// would introduce duplicates
			if( _contacts_cache.hasAssociatedAddress( id, address ) )
				continue;

			// add postal address
			_backend.addContactAddresses( id, address, data );

			// and add this address to the cache to prevent a addition of
			// duplicate date from another file
			_contacts_cache.addAssociatedAddress( id, address );
		}
	}

	private void importContactOrganisations( Long id,
		Map< String, ContactData.OrganisationDetail > datas )
		throws ContactCreationException
	{
		// add addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String organisation = i.next();
			ContactData.OrganisationDetail data = datas.get( organisation );

			// we don't want to add this address if it exists already or we
			// would introduce duplicates
			if( _contacts_cache.hasAssociatedOrganisation( id, organisation ) )
				continue;

			// add organisation address
			_backend.addContactOrganisation( id, organisation, data );

			// and add this address to the cache to prevent a addition of
			// duplicate date from another file
			_contacts_cache.addAssociatedOrganisation( id, organisation );
		}
	}

	private void importContactNotes( Long id, HashSet< String > datas )
		throws ContactCreationException
	{
		// add notes
		Iterator< String > i = datas.iterator();
		while( i.hasNext() ) {
			String note = i.next();

			// we don't want to add this note if it exists already or we would
			// introduce duplicates
			if( _contacts_cache.hasAssociatedNote( id, note ) )
				continue;

			// add note
			_backend.addContactNote( id, note );

			// and add this note to the cache to prevent a addition of duplicate
			// date from another file
			_contacts_cache.addAssociatedNote( id, note );
		}
	}

	private void importContactBirthday( Long id, String birthday )
		throws ContactCreationException
	{
		// we don't want to import this birthday if it already exists
		if( _contacts_cache.hasAssociatedBirthday( id, birthday ) )
			return;

		// add birthday
		_backend.addContactBirthday( id, birthday );

		// and update the cache
		_contacts_cache.addAssociatedBirthday( id, birthday );
	}

	synchronized protected void checkAbort() throws AbortImportException
	{
		if( _abort ) {
			// stop
			throw new AbortImportException();
		}
	}
}
