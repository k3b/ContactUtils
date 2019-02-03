/*
 * Exporter.java
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

import java.util.ArrayList;

import android.content.SharedPreferences;
import android.os.Message;


public class Exporter extends Thread
{
	public final static int ACTION_ABORT = 1;
	public final static int ACTION_ALLDONE = 2;

	public final static int RESPONSE_NEGATIVE = 0;
	public final static int RESPONSE_POSITIVE = 1;

	public final static int RESPONSEEXTRA_NONE = 0;
	public final static int RESPONSEEXTRA_ALWAYS = 1;

	private Doit _doit;
	private int _response;
	private boolean _abort = false;
	private boolean _is_finished = false;

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

		class OrganisationDetail
		{
			protected String _org;
			protected String _title;

			public OrganisationDetail( String org, String title )
			{
				_org = org != null && org.length() > 0? org : null;
				_title = title != null && title.length() > 0? title : null;
			}

			public String getOrganisation()
			{
				return _org;
			}

			public String getTitle()
			{
				return _title;
			}
		}

		class NumberDetail
		{
			protected int _type;
			protected String _num;

			public NumberDetail( int type, String num )
			{
				_type = type;
				_num = num != null && num.length() > 0? num : null;
			}

			public int getType()
			{
				return _type;
			}

			public String getNumber()
			{
				return _num;
			}
		}

		class EmailDetail
		{
			protected int _type;
			protected String _email;

			public EmailDetail( int type, String email )
			{
				_type = type;
				_email = email != null && email.length() > 0? email : null;
			}

			public int getType()
			{
				return _type;
			}

			public String getEmail()
			{
				return _email;
			}
		}

		class AddressDetail
		{
			protected int _type;
			protected String _addr;

			public AddressDetail( int type, String addr )
			{
				_type = type;
				_addr = addr != null && addr.length() > 0? addr : null;
			}

			public int getType()
			{
				return _type;
			}

			public String getAddress()
			{
				return _addr;
			}
		}

		protected String _name = null;
		protected ArrayList< OrganisationDetail > _organisations = null;
		protected ArrayList< NumberDetail > _numbers = null;
		protected ArrayList< EmailDetail > _emails = null;
		protected ArrayList< AddressDetail > _addresses = null;
		protected ArrayList< String > _notes = null;
		protected String _birthday = null;

		public void setName( String name )
		{
			_name = name != null && name.length() > 0? name : null;
		}

		public String getName()
		{
			return _name;
		}

		public void addOrganisation( OrganisationDetail organisation )
		{
			if( organisation.getOrganisation() == null ) return;
			if( _organisations == null )
				_organisations = new ArrayList< OrganisationDetail >();
			_organisations.add( organisation );
		}

		public ArrayList< OrganisationDetail > getOrganisations()
		{
			return _organisations;
		}

		public void addNumber( NumberDetail number )
		{
			if( number.getNumber() == null ) return;
			if( _numbers == null )
				_numbers = new ArrayList< NumberDetail >();
			_numbers.add( number );
		}

		public ArrayList< NumberDetail > getNumbers()
		{
			return _numbers;
		}

		public void addEmail( EmailDetail email )
		{
			if( email.getEmail() == null ) return;
			if( _emails == null )
				_emails = new ArrayList< EmailDetail >();
			_emails.add( email );
		}

		public ArrayList< EmailDetail > getEmails()
		{
			return _emails;
		}

		public void addAddress( AddressDetail address )
		{
			if( address.getAddress() == null ) return;
			if( _addresses == null )
				_addresses = new ArrayList< AddressDetail >();
			_addresses.add( address );
		}

		public ArrayList< AddressDetail > getAddresses()
		{
			return _addresses;
		}

		public void addNote( String note )
		{
			if( _notes == null )
				_notes = new ArrayList< String >();
			_notes.add( note );
		}

		public ArrayList< String > getNotes()
		{
			return _notes;
		}

		public void setBirthday( String birthday )
		{
			_birthday = birthday;
		}

		public String getBirthday()
		{
			return _birthday;
		}

		public String getPrimaryIdentifier()
		{
			if( _name != null )
				return _name;

			if( _organisations != null &&
				_organisations.get( 0 ).getOrganisation() != null )
				return _organisations.get( 0 ).getOrganisation();

			if( _numbers!= null &&
				_numbers.get( 0 ).getNumber() != null )
				return _numbers.get( 0 ).getNumber();

			if( _emails!= null &&
				_emails.get( 0 ).getEmail() != null )
				return _emails.get( 0 ).getEmail();

			// no primary identifier
			return null;
		}
	}

	@SuppressWarnings( "serial" )
	protected class AbortExportException extends Exception { };

	public Exporter( Doit doit )
	{
		_doit = doit;
	}

	@Override
	public void run()
	{
		try
		{
			// update UI
			setProgressMessage( R.string.doit_scanning );

			// do the export
			exportContacts();

			// done!
			finish( ACTION_ALLDONE );
		}
		catch( AbortExportException e )
		{}

		// flag as finished to prevent interrupts
		setIsFinished();
	}

	synchronized private void setIsFinished()
	{
		_is_finished = true;
	}

	protected void exportContacts() throws AbortExportException
	{
		// set up a contact reader
		Backend backend = null;
		if( Integer.parseInt( android.os.Build.VERSION.SDK ) >= 5 )
			backend = new ContactsContractBackend( _doit, this );
		else
			backend = new ContactsBackend( _doit, this );

		// check we have contacts
		int num_contacts = backend.getNumContacts();
		if( num_contacts == 0 )
			showError( R.string.error_nothingtodo );

		// count the number of contacts and set the progress bar max
		setProgress( 0 );
		setProgressMax( num_contacts );

		checkAbort();
		preExport();

		// loop through contacts
		int count = 0;
		while( true ) {
			checkAbort();
			ContactData contact = new ContactData();
			if( !backend.getNextContact( contact ) )
				break;

			// export this one
			checkAbort();
			if( exportContact( contact ) )
				_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTWRITTEN );
			else
				_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTSKIPPED );
			setProgress( count++ );
		}
		setProgress( num_contacts );

		postExport();
	}

	public void wake()
	{
		wake( 0 );
	}

	synchronized public void wake( int response )
	{
		_response = response;
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

	protected void showError( int res ) throws AbortExportException
	{
		showError( _doit.getText( res ).toString() );
	}

	synchronized protected void showError( String message )
			throws AbortExportException
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

	protected void showContinueOrAbort( int res ) throws AbortExportException
	{
		showContinueOrAbort( _doit.getText( res ).toString() );
	}

	synchronized protected void showContinueOrAbort( String message )
			throws AbortExportException
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

	protected void setProgressMessage( int res ) throws AbortExportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain( _doit._handler,
			Doit.MESSAGE_SETPROGRESSMESSAGE, getText( res ) ) );
	}

	protected void setProgressMax( int max_progress )
			throws AbortExportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETMAXPROGRESS,
			Integer.valueOf( max_progress ) ) );
	}

	protected void setTmpProgress( int tmp_progress )
		throws AbortExportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETTMPPROGRESS,
			Integer.valueOf( tmp_progress ) ) );
	}

	protected void setProgress( int progress ) throws AbortExportException
	{
		checkAbort();
		_doit._handler.sendMessage( Message.obtain(
			_doit._handler, Doit.MESSAGE_SETPROGRESS,
			Integer.valueOf( progress ) ) );
	}

	protected void finish( int action ) throws AbortExportException
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
		throw new AbortExportException();
	}

	protected CharSequence getText( int res )
	{
		return _doit.getText( res );
	}

	protected void skipContact() throws AbortExportException
	{
		checkAbort();
		_doit._handler.sendEmptyMessage( Doit.MESSAGE_CONTACTSKIPPED );
	}

	synchronized protected void checkAbort() throws AbortExportException
	{
		if( _abort ) {
			// stop
			throw new AbortExportException();
		}
	}

	protected void preExport() throws AbortExportException
	{
	}

	protected boolean exportContact( ContactData contact )
		throws AbortExportException
	{
		throw new UnsupportedOperationException();
	}

	protected void postExport() throws AbortExportException
	{
	}

}
