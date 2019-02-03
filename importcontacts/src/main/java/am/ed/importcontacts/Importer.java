/*
 * Importer.java
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import am.ed.importcontacts.Backend.ContactCreationException;
import android.content.SharedPreferences;
import android.os.Message;

public class Importer extends Thread
{
	public final static int ACTION_ABORT = 1;
	public final static int ACTION_ALLDONE = 2;

	public final static int RESPONSE_NEGATIVE = 0;
	public final static int RESPONSE_POSITIVE = 1;

	public final static int RESPONSEEXTRA_NONE = 0;
	public final static int RESPONSEEXTRA_ALWAYS = 1;

	private Doit _doit;
	private int _response;
	private int _response_extra;
	private int _merge_setting;
	private int _last_merge_decision;
	private boolean _abort = false;
	private boolean _is_finished = false;
	private ContactsCache _contacts_cache = null;
	private Backend _backend = null;

	/**
	 * Data about a contact
	 */
	public class ContactData
	{
		public final static int TYPE_HOME = 0;
		public final static int TYPE_WORK = 1;
		public final static int TYPE_MOBILE = 2;	// only used with phones
		public final static int TYPE_FAX_HOME = 3;	// only used with phones
		public final static int TYPE_FAX_WORK = 4;	// only used with phones
		public final static int TYPE_PAGER = 5;		// only used with phones

		class TypeDetail
		{
			protected int _type;

			public TypeDetail( int type )
			{
				_type = type;
			}

			public int getType()
			{
				return _type;
			}
		}

		class PreferredDetail extends TypeDetail
		{
			protected boolean _is_preferred;

			public PreferredDetail( int type, boolean is_preferred )
			{
				super( type );
				_is_preferred = is_preferred;
			}

			public boolean isPreferred()
			{
				return _is_preferred;
			}
		}

		class ExtraDetail extends PreferredDetail
		{
			protected String _extra;

			public ExtraDetail( int type, boolean is_preferred, String extra )
			{
				super( type, is_preferred );

				if( extra != null ) extra = extra.trim();
				_extra = extra;
			}

			public String getExtra()
			{
				return _extra;
			}

			public void setExtra( String extra )
			{
				if( extra != null ) extra = extra.trim();
				_extra = extra;
			}
		}

		@SuppressWarnings("serial")
		protected class ContactNotIdentifiableException extends Exception
		{
		}

		protected String _name = null;
		protected String _primary_organisation = null;
		protected boolean _primary_organisation_is_preferred;
		protected String _primary_number = null;
		protected int _primary_number_type;
		protected boolean _primary_number_is_preferred;
		protected String _primary_email = null;
		protected boolean _primary_email_is_preferred;
		protected HashMap< String, ExtraDetail > _organisations = null;
		protected HashMap< String, PreferredDetail > _numbers = null;
		protected HashMap< String, PreferredDetail > _emails = null;
		protected HashMap< String, TypeDetail > _addresses = null;
		protected HashSet< String > _notes = null;
		protected String _birthday = null;

		private ContactsCache.CacheIdentifier _cache_identifier = null;

		protected void setName( String name )
		{
			_name = name;
		}

		public boolean hasName()
		{
			return _name != null;
		}

		public String getName()
		{
			return _name;
		}

		protected void addOrganisation( String organisation, String title,
			boolean is_preferred )
		{
			organisation = organisation.trim();
			if( organisation.length() <= 0 )
			{
				// TODO: warn that an imported organisation is being ignored
				return;
			}

			if( title != null ) {
				title = title.trim();
				if( title.length() <= 0 ) title = null;
			}

			// add the organisation, as non-preferred (we prefer only one
			// organisation in finalise() after they're all imported)
			if( _organisations == null )
				_organisations = new HashMap< String, ExtraDetail >();
			if( !_organisations.containsKey( organisation ) )
				_organisations.put( organisation,
					new ExtraDetail( 0, false, title ) );

			// if this is the first organisation added, or it's a preferred
			// organisation and the current primary organisation isn't, then
			// record this as the primary organisation
			if( _primary_organisation == null ||
				( is_preferred && !_primary_organisation_is_preferred ) )
			{
				_primary_organisation = organisation;
				_primary_organisation_is_preferred = is_preferred;
			}
		}

		public boolean hasOrganisations()
		{
			return _organisations != null && _organisations.size() > 0;
		}

		public HashMap< String, ExtraDetail > getOrganisations()
		{
			return _organisations;
		}

		public boolean hasPrimaryOrganisation()
		{
			return _primary_organisation != null;
		}

		public String getPrimaryOrganisation()
		{
			return _primary_organisation;
		}

		protected void addNumber( String number, int type,
			boolean is_preferred )
		{
			number = sanitisePhoneNumber( number );
			if( number == null )
			{
				// TODO: warn that an imported phone number is being ignored
				return;
			}

			// add the number, as non-preferred (we prefer only one number
			// in finalise() after they're all imported)
			if( _numbers == null )
				_numbers = new HashMap< String, PreferredDetail >();
			if( !_numbers.containsKey( number ) )
				_numbers.put( number,
					new PreferredDetail( type, false ) );

			final Set< Integer > non_voice_types = new HashSet< Integer >(
				Arrays.asList( TYPE_FAX_HOME, TYPE_FAX_WORK, TYPE_PAGER ) );

			// if this is the first number added, or it's a preferred number
			// and the current primary number isn't, or this number is on equal
			// standing with the primary number in terms of preference and it is
			// a voice number and the primary number isn't, then record this as
			// the primary number
			if( _primary_number == null ||
				( is_preferred && !_primary_number_is_preferred ) ||
				( is_preferred == _primary_number_is_preferred &&
					!non_voice_types.contains( type ) &&
					non_voice_types.contains( _primary_number_type ) ) )
			{
				_primary_number = number;
				_primary_number_type = type;
				_primary_number_is_preferred = is_preferred;
			}
		}

		public boolean hasNumbers()
		{
			return _numbers != null && _numbers.size() > 0;
		}

		public HashMap< String, PreferredDetail > getNumbers()
		{
			return _numbers;
		}

		public boolean hasPrimaryNumber()
		{
			return _primary_number != null;
		}

		public String getPrimaryNumber()
		{
			return _primary_number;
		}

		protected void addEmail( String email, int type, boolean is_preferred )
		{

			email = sanitisesEmailAddress( email );
			if( email == null )
			{
				// TODO: warn that an imported email address is being ignored
				return;
			}

			// add the email, as non-preferred (we prefer only one email in
			// finalise() after they're all imported)
			if( _emails == null )
				_emails = new HashMap< String, PreferredDetail >();
			if( !_emails.containsKey( email ) )
				_emails.put( email, new PreferredDetail( type, false ) );

			// if this is the first email added, or it's a preferred email and
			// the current primary organisation isn't, then record this as the
			// primary email
			if( _primary_email == null ||
				( is_preferred && !_primary_email_is_preferred ) )
			{
				_primary_email = email;
				_primary_email_is_preferred = is_preferred;
			}
		}

		public boolean hasEmails()
		{
			return _emails != null && _emails.size() > 0;
		}

		public HashMap< String, PreferredDetail > getEmails()
		{
			return _emails;
		}

		public boolean hasPrimaryEmail()
		{
			return _primary_email != null;
		}

		public String getPrimaryEmail()
		{
			return _primary_email;
		}

		protected void addAddress( String address, int type )
		{
			address = address.trim();
			if( address.length() <= 0 )
			{
				// TODO: warn that an imported address is being ignored
				return;
			}

			if( _addresses == null ) _addresses =
				new HashMap< String, TypeDetail >();
			if( !_addresses.containsKey( address ) )
				_addresses.put( address, new TypeDetail( type ) );
		}

		public boolean hasAddresses()
		{
			return _addresses != null && _addresses.size() > 0;
		}

		public HashMap< String, TypeDetail > getAddresses()
		{
			return _addresses;
		}

		protected void addNote( String note )
		{
			if( _notes == null ) _notes = new HashSet< String >();
			if( !_notes.contains( note ) )
				_notes.add( note );
		}

		public boolean hasNotes()
		{
			return _notes != null && _notes.size() > 0;
		}

		public HashSet< String > getNotes()
		{
			return _notes;
		}

		public void setBirthday( String birthday )
		{
			_birthday = birthday;
		}

		public boolean hasBirthday()
		{
			return _birthday != null;
		}

		public String getBirthday()
		{
			return _birthday;
		}

		protected void finalise()
			throws ContactNotIdentifiableException
		{
			// Ensure that if there is a primary number, it is preferred so
			// that there is always one preferred number.  Android will assign
			// preference to one anyway so we might as well decide one sensibly.
			if( _primary_number != null ) {
				PreferredDetail data = _numbers.get( _primary_number );
				_numbers.put( _primary_number,
					new PreferredDetail( data.getType(), true ) );
			}

			// do the same for the primary email
			if( _primary_email != null ) {
				PreferredDetail data = _emails.get( _primary_email );
				_emails.put( _primary_email,
					new PreferredDetail( data.getType(), true ) );
			}

			// do the same for the primary organisation
			if( _primary_organisation != null ) {
				ExtraDetail data = _organisations.get( _primary_organisation );
				_organisations.put( _primary_organisation,
					new ExtraDetail( 0, true, data.getExtra() ) );
			}

			// create a cache identifier from this contact data, which can be
			// used to look-up an existing contact
			_cache_identifier = ContactsCache.CacheIdentifier.factory( this );
			if( _cache_identifier == null )
				throw new ContactNotIdentifiableException();
		}

		public ContactsCache.CacheIdentifier getCacheIdentifier()
		{
			return _cache_identifier;
		}

		private String sanitisePhoneNumber( String number )
		{
			number = number.trim();
			Pattern p = Pattern.compile( "^[-\\(\\) \\+0-9#*]+" );
			Matcher m = p.matcher( number );
			if( m.lookingAt() ) return m.group( 0 );
			return null;
		}

		private String sanitisesEmailAddress( String email )
		{
			email = email.trim();
			Pattern p = Pattern.compile(
				"^[^ @]+@[a-zA-Z]([-a-zA-Z0-9]*[a-zA-z0-9])?(\\.[a-zA-Z]([-a-zA-Z0-9]*[a-zA-z0-9])?)+$" );
			Matcher m = p.matcher( email );
			if( m.matches() ) {
				String[] bits = email.split( "@" );
				return bits[ 0 ] + "@" +
					bits[ 1 ].toLowerCase( Locale.ENGLISH );
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	protected class AbortImportException extends Exception { };

	public Importer( Doit doit )
	{
		_doit = doit;

		SharedPreferences prefs = getSharedPreferences();
		_merge_setting = prefs.getInt( "merge_setting", Doit.ACTION_PROMPT );
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
				_backend = new ContactsContractBackend( _doit );
			else
				_backend = new ContactsBackend( _doit );

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
		return _doit.getSharedPreferences();
	}

	protected void showError( int res ) throws AbortImportException
	{
		showError( _doit.getText( res ).toString() );
	}

	synchronized protected void showError( String message )
			throws AbortImportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_ERROR, message ) );
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
		showContinueOrAbort( _doit.getText( res ).toString() );
	}

	synchronized protected void showContinueOrAbort( String message )
			throws AbortImportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_CONTINUEORABORT, message ) );
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
		_doit._handler.sendMessage( Message.obtain( _doit._handler,
			Doit.MESSAGE_SETPROGRESSMESSAGE, getText( res ) ) );
	}

	protected void setProgressMax( int max_progress )
			throws AbortImportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETMAXPROGRESS,
			Integer.valueOf( max_progress ) ) );
	}

	protected void setTmpProgress( int tmp_progress )
		throws AbortImportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETTMPPROGRESS,
			Integer.valueOf( tmp_progress ) ) );
	}

	protected void setProgress( int progress ) throws AbortImportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETPROGRESS,
			Integer.valueOf( progress ) ) );
	}

	protected void finish( int action ) throws AbortImportException
	{
		// update UI to reflect action
		int message;
		switch( action )
		{
		case ACTION_ALLDONE:	message = Doit.MESSAGE_ALLDONE; break;
		default:	// fall through
		case ACTION_ABORT:		message = Doit.MESSAGE_ABORT; break;
		}
		_doit._handler.sendEmptyMessage( message );

		// stop
		throw new AbortImportException();
	}

	protected CharSequence getText( int res )
	{
		return _doit.getText( res );
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
		case Doit.ACTION_KEEP:
			// if we are skipping on a duplicate, check for one
			return exists;

		case Doit.ACTION_PROMPT:
			// if we are prompting on duplicate, then we can say that we won't
			// skip if there isn't one
			if( !exists ) return false;

			// ok, duplicate exists, so do prompt
			_doit._handler.sendMessage( Message.obtain( _doit._handler,
				Doit.MESSAGE_MERGEPROMPT, contact_detail ) );
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
		_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTSKIPPED );
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
			_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTSKIPPED );
			return;
		}

		// if a contact exists, and we're overwriting, destroy the existing
		// contact before importing
		boolean contact_deleted = false;
		if( id != null && _last_merge_decision == Doit.ACTION_OVERWRITE )
		{
			contact_deleted = true;

			// remove from device
			_backend.deleteContact( id );

			// update cache
			_contacts_cache.removeLookup( cache_identifier );
			_contacts_cache.removeAssociatedData( id );

			// show that we're overwriting a contact
			_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTOVERWRITTEN );

			// discard the contact id
			id = null;
		}

		try {
			// if we don't have a contact id yet (or we did, but we destroyed it
			// when we deleted the contact), we'll have to create a new contact
			if( id == null )
			{
				// create a new contact
				id = _backend.addContact( contact._name );

				// update cache
				_contacts_cache.addLookup( cache_identifier, id );

				// if we haven't already shown that we're overwriting a contact,
				// show that we're creating a new contact
				if( !contact_deleted )
					_doit._handler.sendEmptyMessage(
						Doit.MESSAGE_CONTACTCREATED );
			}
			else
				// show that we're merging with an existing contact
				_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTMERGED );

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
		HashMap< String, ContactData.PreferredDetail > datas )
		throws ContactCreationException
	{
		// add phone numbers
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String number = i.next();
			ContactData.PreferredDetail data = datas.get( number );

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
		HashMap< String, ContactData.PreferredDetail > datas )
		throws ContactCreationException
	{
		// add email addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String email = i.next();
			ContactData.PreferredDetail data = datas.get( email );

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
		HashMap< String, ContactData.TypeDetail > datas )
		throws ContactCreationException
	{
		// add addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String address = i.next();
			ContactData.TypeDetail data = datas.get( address );

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
		HashMap< String, ContactData.ExtraDetail > datas )
		throws ContactCreationException
	{
		// add addresses
		Set< String > datas_keys = datas.keySet();
		Iterator< String > i = datas_keys.iterator();
		while( i.hasNext() ) {
			String organisation = i.next();
			ContactData.ExtraDetail data = datas.get( organisation );

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
