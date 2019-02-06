/*
 * ExporterThread.java
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

import android.content.SharedPreferences;
import de.k3b.android.contactlib.ConatactsReaderAndroid3Impl;
import de.k3b.android.contactlib.ConatactsReaderAndroid5Impl;
import android.os.Message;

import java.io.IOException;

import de.k3b.contactlib.ContactData;
import de.k3b.contactlib.IConatactsReader;


public class ExporterThread extends Thread
{
	public final static int ACTION_ABORT = 1;
	public final static int ACTION_ALLDONE = 2;

	public final static int RESPONSE_NEGATIVE = 0;
	public final static int RESPONSE_POSITIVE = 1;

	public final static int RESPONSEEXTRA_NONE = 0;
	public final static int RESPONSEEXTRA_ALWAYS = 1;

	private DoExportActivity _doExportActivity;
	private int _response;
	private boolean _abort = false;
	private boolean _is_finished = false;

	@SuppressWarnings( "serial" )
	protected class AbortExportException extends Exception { };

	public ExporterThread(DoExportActivity doExportActivity)
	{
		_doExportActivity = doExportActivity;
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
		IConatactsReader conatactsReader = null;

		try {


			if (Integer.parseInt(android.os.Build.VERSION.SDK) >= 5)
				conatactsReader = new ConatactsReaderAndroid5Impl(_doExportActivity.getContentResolver());
			else
				conatactsReader = new ConatactsReaderAndroid3Impl(_doExportActivity.getContentResolver());

			// check we have contacts
			int num_contacts = conatactsReader.getNumContacts();
			if (num_contacts == 0)
				showError(R.string.error_nothingtodo);

			// count the number of contacts and set the progress bar max
			setProgress(0);
			setProgressMax(num_contacts);

			checkAbort();
			preExport();

			// loop through contacts
			int count = 0;
			while (true) {
				checkAbort();
				ContactData contact = new ContactData();
				if (!conatactsReader.getNextContact(contact))
					break;

				// export this one
				checkAbort();
				if (exportContact(contact))
					_doExportActivity._handler.sendEmptyMessage(DoExportActivity.MESSAGE_CONTACTWRITTEN);
				else
					_doExportActivity._handler.sendEmptyMessage(DoExportActivity.MESSAGE_CONTACTSKIPPED);
				setProgress(count++);
			}
			setProgress(num_contacts);

			postExport();
		} finally {
			if (conatactsReader != null) {
				try {
					conatactsReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
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
		return _doExportActivity.getSharedPreferences();
	}

	protected void showError( int res ) throws AbortExportException
	{
		showError( _doExportActivity.getText( res ).toString() );
	}

	synchronized protected void showError( String message )
			throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendMessage( Message.obtain(
			_doExportActivity._handler, DoExportActivity.MESSAGE_ERROR, message ) );
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
		showContinueOrAbort( _doExportActivity.getText( res ).toString() );
	}

	synchronized protected void showContinueOrAbort( String message )
			throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendMessage( Message.obtain(
			_doExportActivity._handler, DoExportActivity.MESSAGE_CONTINUEORABORT, message ) );
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
		_doExportActivity._handler.sendMessage( Message.obtain( _doExportActivity._handler,
			DoExportActivity.MESSAGE_SETPROGRESSMESSAGE, getText( res ) ) );
	}

	protected void setProgressMax( int max_progress )
			throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendMessage( Message.obtain(
			_doExportActivity._handler, DoExportActivity.MESSAGE_SETMAXPROGRESS,
			Integer.valueOf( max_progress ) ) );
	}

	protected void setTmpProgress( int tmp_progress )
		throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendMessage( Message.obtain(
			_doExportActivity._handler, DoExportActivity.MESSAGE_SETTMPPROGRESS,
			Integer.valueOf( tmp_progress ) ) );
	}

	protected void setProgress( int progress ) throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendMessage( Message.obtain(
			_doExportActivity._handler, DoExportActivity.MESSAGE_SETPROGRESS,
			Integer.valueOf( progress ) ) );
	}

	protected void finish( int action ) throws AbortExportException
	{
		// update UI to reflect action
		int message;
		switch( action )
		{
		case ACTION_ALLDONE:	message = DoExportActivity.MESSAGE_ALLDONE; break;
		default:	// fall through
		case ACTION_ABORT:		message = DoExportActivity.MESSAGE_ABORT; break;
		}
		_doExportActivity._handler.sendEmptyMessage( message );

		// stop
		throw new AbortExportException();
	}

	protected CharSequence getText( int res )
	{
		return _doExportActivity.getText( res );
	}

	protected void skipContact() throws AbortExportException
	{
		checkAbort();
		_doExportActivity._handler.sendEmptyMessage( DoExportActivity.MESSAGE_CONTACTSKIPPED );
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
