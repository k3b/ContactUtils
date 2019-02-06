/*
 * ImportActivity.java
 *
 * Copyright (C) 2009 Tim Marston <tim@ed.am>
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ImportActivity extends WizardActivity
{
	private final static int DIALOG_ERROR = 0;
	private final static int DIALOG_CONTINUEORABORT = 1;
	private final static int DIALOG_MERGEPROMPT = 2;

	public final static int MESSAGE_ALLDONE = 0;
	public final static int MESSAGE_ABORT = 1;
	public final static int MESSAGE_ERROR = 3;
	public final static int MESSAGE_CONTINUEORABORT = 4;
	public final static int MESSAGE_SETPROGRESSMESSAGE = 5;
	public final static int MESSAGE_SETMAXPROGRESS = 6;
	public final static int MESSAGE_SETTMPPROGRESS = 7;
	public final static int MESSAGE_SETPROGRESS = 8;
	public final static int MESSAGE_MERGEPROMPT = 9;
	public final static int MESSAGE_CONTACTOVERWRITTEN = 10;
	public final static int MESSAGE_CONTACTCREATED = 11;
	public final static int MESSAGE_CONTACTMERGED = 12;
	public final static int MESSAGE_CONTACTSKIPPED = 13;

	public final static int ACTION_PROMPT = 0;
	public final static int ACTION_KEEP = 1;
	public final static int ACTION_MERGE_MERGE = 2;
	public final static int ACTION_OVERWRITE = 3;

	public final static int NEXT_BEGIN = 0;
	public final static int NEXT_CLOSE = 1;

	private boolean _started_progress;
	private int _max_progress;
	private int _tmp_progress;
	private int _progress;
	protected String _dialog_message;
	private Dialog _merge_prompt_dialog;
	private boolean _merge_prompt_always_selected;
	private int _next_action;
	private int _current_dialog_id;

	private int _count_overwrites;
	private int _count_creates;
	private int _count_merges;
	private int _count_skips;

	protected ImporterThread _importerThread = null;

	public Handler _handler;

	public class DoitHandler extends Handler
	{
		@Override
		public void handleMessage( Message msg ) {
			switch( msg.what )
			{
			case MESSAGE_ALLDONE:
				( (TextView)findViewById( R.id.doit_alldone ) ).
					setVisibility( View.VISIBLE );
				( (Button)findViewById( R.id.back ) ).setEnabled( false );
				updateNext( NEXT_CLOSE );
				findViewById( R.id.doit_abort_disp ).setVisibility(
					View.GONE );
				break;
			case MESSAGE_ABORT:
				manualAbort();
				break;
			case MESSAGE_ERROR:
				_dialog_message = (String)msg.obj;
				showDialog( DIALOG_ERROR );
				break;
			case MESSAGE_CONTINUEORABORT:
				_dialog_message = (String)msg.obj;
				showDialog( DIALOG_CONTINUEORABORT );
				break;
			case MESSAGE_SETPROGRESSMESSAGE:
				( (TextView)findViewById( R.id.doit_percentage ) ).
					setText( (String)msg.obj );
				break;
			case MESSAGE_SETMAXPROGRESS:
				if( _max_progress > 0 ) {
					if( _tmp_progress == _max_progress - 1 )
						_tmp_progress = (Integer)msg.obj;
					if( _progress == _max_progress - 1 )
						_progress = (Integer)msg.obj;
				}
				_max_progress = (Integer)msg.obj;
				updateProgress();
				break;
			case MESSAGE_SETTMPPROGRESS:
				_tmp_progress = (Integer)msg.obj;
				updateProgress();
				break;
			case MESSAGE_SETPROGRESS:
				_started_progress = true;
				_progress = (Integer)msg.obj;
				updateProgress();
				break;
			case MESSAGE_MERGEPROMPT:
				_dialog_message = (String)msg.obj;
				showDialog( DIALOG_MERGEPROMPT );
				break;
			case MESSAGE_CONTACTOVERWRITTEN:
				_count_overwrites++;
				updateStats();
				break;
			case MESSAGE_CONTACTCREATED:
				_count_creates++;
				updateStats();
				break;
			case MESSAGE_CONTACTMERGED:
				_count_merges++;
				updateStats();
				break;
			case MESSAGE_CONTACTSKIPPED:
				_count_skips++;
				updateStats();
				break;
			default:
				super.handleMessage( msg );
			}
		}
	}

	@Override
	protected void onCreate(Bundle saved_instance_state)
	{
		setContentView( R.layout.doit );
		super.onCreate( saved_instance_state );

		// hide page 2
		( findViewById( R.id.doit_page_2 ) ).setVisibility( View.GONE );

		// set up abort button
		Button begin = (Button)findViewById( R.id.abort );
		begin.setOnClickListener( new View.OnClickListener() {
			public void onClick( View view ) {
				manualAbort();
			}
		} );

		_started_progress = false;
		_max_progress = 0;
		_tmp_progress = 0;
		_progress = 0;
		_handler = new DoitHandler();

		_count_overwrites = 0;
		_count_creates = 0;
		_count_merges = 0;
		_count_skips = 0;

		updateNext( NEXT_BEGIN );

		updateProgress();
		updateStats();
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		// saving the state of an import sounds complicated! Lets just abort!
		if( _next_action != NEXT_CLOSE )
			manualAbort( true );
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		switch( id )
		{
		case DIALOG_ERROR:
			return new AlertDialog.Builder( this )
				.setIcon( R.drawable.alert_dialog_icon )
				.setTitle( R.string.error_title )
				.setMessage( "" )
				.setPositiveButton( R.string.error_ok,
					new DialogInterface.OnClickListener() {
						public void onClick( DialogInterface dialog,
							int whichButton )
						{
							if( ImportActivity.this != null )
								ImportActivity.this._importerThread.wake();
						}
					} )
				.setOnCancelListener( _dialog_on_cancel_listener )
				.create();
		case DIALOG_CONTINUEORABORT:
			return new AlertDialog.Builder( this )
				.setIcon( R.drawable.alert_dialog_icon )
				.setTitle( R.string.error_title )
				.setMessage( "" )
				.setPositiveButton( R.string.error_continue,
					new DialogInterface.OnClickListener() {
						public void onClick( DialogInterface dialog,
							int which_button )
						{
							if( ImportActivity.this != null )
								ImportActivity.this._importerThread.wake(
									ImporterThread.RESPONSE_POSITIVE );
						}
					} )
				.setNegativeButton( R.string.error_abort,
					new DialogInterface.OnClickListener() {
						public void onClick( DialogInterface dialog,
							int which_button )
						{
							if( ImportActivity.this != null )
								ImportActivity.this._importerThread.wake(
									ImporterThread.RESPONSE_NEGATIVE );
						}
					} )
				.setOnCancelListener( _dialog_on_cancel_listener )
				.create();
		case DIALOG_MERGEPROMPT:
			// custom layout in an AlertDialog
			LayoutInflater factory = LayoutInflater.from( this );
			final View dialog_view = factory.inflate(
				R.layout.mergeprompt, null );
			( (CheckBox)dialog_view.findViewById( R.id.mergeprompt_always ) ).
				setOnCheckedChangeListener(
					new CompoundButton.OnCheckedChangeListener() {
						public void onCheckedChanged(
							CompoundButton button_view, boolean is_checked )
						{
							if( ImportActivity.this != null )
								ImportActivity.this._merge_prompt_always_selected =
									is_checked;
						}
					} );
			( (Button)dialog_view.findViewById( R.id.merge_keep ) ).
				setOnClickListener( _merge_prompt_button_listener );
			( (Button)dialog_view.findViewById( R.id.merge_overwrite ) ).
				setOnClickListener( _merge_prompt_button_listener );
			( (Button)dialog_view.findViewById( R.id.merge_merge ) ).
				setOnClickListener( _merge_prompt_button_listener );
			( (Button)dialog_view.findViewById( R.id.abort ) ).
				setOnClickListener( _merge_prompt_button_listener );
			_merge_prompt_always_selected = false;
			return new AlertDialog.Builder( this )
				.setIcon( R.drawable.alert_dialog_icon )
				.setTitle( R.string.mergeprompt_title )
				.setView( dialog_view )
				.setOnCancelListener( _dialog_on_cancel_listener )
				.create();
		}
		return null;
	}

	private OnClickListener _merge_prompt_button_listener =
		new OnClickListener()
	{
		public void onClick( View view )
		{
			if( ImportActivity.this == null ) return;

			// handle abort
			if( view.getId() == R.id.abort )
				manualAbort();

			// else, response (just check we haven't aborted already!)
			else if( ImportActivity.this._importerThread != null ) {
				int response_extra = _merge_prompt_always_selected?
					ImporterThread.RESPONSEEXTRA_ALWAYS : ImporterThread.RESPONSEEXTRA_NONE;
				ImportActivity.this._importerThread.wake( convertIdToAction( view.getId() ),
					response_extra );
			}

			// close dialog and free (don't keep a reference)
			ImportActivity.this._merge_prompt_dialog.dismiss();
			ImportActivity.this._merge_prompt_dialog = null;
		}
	};

	@Override
	protected void onNext()
	{
		Button next = (Button)findViewById( R.id.next );
		next.setEnabled( false );

		switch( _next_action )
		{
		case NEXT_BEGIN:
			importContacts();
			break;
		case NEXT_CLOSE:
			setResult( RESULT_OK );
			finish();
			break;
		}
	}

	private void manualAbort()
	{
		manualAbort( false );
	}

	private void manualAbort( boolean show_toaster_popup )
	{
		abortImport( show_toaster_popup );

		updateNext( NEXT_CLOSE );
		( (Button)findViewById( R.id.back ) ).setEnabled( true );
		findViewById( R.id.doit_abort_disp ).setVisibility( View.GONE );
		( (TextView)findViewById( R.id.doit_aborted ) ).
			setVisibility( View.VISIBLE );
		( (TextView)findViewById( R.id.doit_alldone ) ).
			setVisibility( View.GONE );

		// close any open dialogs
		try {
			dismissDialog( _current_dialog_id );
		}
		catch( Exception e ) {
			// ignore errors
		}
	}

	private void updateNext( int next_action )
	{
		Button next = (Button)findViewById( R.id.next );
		switch( next_action ) {
		case NEXT_BEGIN:	next.setText( R.string.doit_begin ); break;
		case NEXT_CLOSE:	next.setText( R.string.doit_close ); break;
		}
		next.setEnabled( true );
		_next_action = next_action;
	}

	public static int convertIdToAction( int id ) {
		switch( id ) {
		case R.id.merge_keep:		return ACTION_KEEP;
		case R.id.merge_merge:		return ACTION_MERGE_MERGE;
		case R.id.merge_overwrite:	return ACTION_OVERWRITE;
		default: return ACTION_PROMPT;
		}
	}

	public static int convertActionToId( int action ) {
		switch( action ) {
		case ACTION_KEEP:		return R.id.merge_keep;
		case ACTION_MERGE_MERGE:return R.id.merge_merge;
		case ACTION_OVERWRITE:	return R.id.merge_overwrite;
		default: return R.id.merge_prompt;
		}
	}

	private DialogInterface.OnCancelListener _dialog_on_cancel_listener =
		new DialogInterface.OnCancelListener()
	{
		public void onCancel( DialogInterface dialog ) {
			manualAbort();
		}
	};


	@Override
	protected void onActivityResult( int request_code, int result_code,
		Intent data )
	{
		// if we're cancelling, abort any import
		if( result_code == RESULT_CANCELED )
			abortImport( true );
	}

	@Override
	protected void onPrepareDialog( int id, Dialog dialog )
	{
		_current_dialog_id = id;

		switch( id )
		{
		case DIALOG_ERROR:	// fall through
		case DIALOG_CONTINUEORABORT:
			// set dialog message
			( (AlertDialog)dialog ).setMessage( _dialog_message );
			break;
		case DIALOG_MERGEPROMPT:
			// set contact's name
			( (TextView)dialog.findViewById( R.id.mergeprompt_name ) )
				.setText( _dialog_message );
			// and set up reference to dialog
			_merge_prompt_dialog = dialog;
			break;
		}

		super.onPrepareDialog( id, dialog );
	}

	private void importContacts()
	{
		// switch interfaces
		( findViewById( R.id.doit_page_1 ) ).setVisibility( View.GONE );
		( findViewById( R.id.doit_page_2 ) ).setVisibility( View.VISIBLE );

		// disable back button
		( (Button)findViewById( R.id.back ) ).setEnabled( false );

		// create importer
		_importerThread = new VcardImporterThread( this );

		// start the service's thread
		_importerThread.start();
	}

	private void updateProgress()
	{
		ProgressBar bar = (ProgressBar)findViewById( R.id.doit_progress );
		TextView out_of = (TextView)findViewById( R.id.doit_outof );

		if( _max_progress > 0 )
		{
			bar.setMax( _max_progress );
			bar.setSecondaryProgress( _tmp_progress );

			if( _started_progress )
			{
				( (TextView)findViewById( R.id.doit_percentage ) ).setText(
					(int)Math.round( 100 * _progress / _max_progress ) + "%" );
				out_of.setText( _progress + "/" + _max_progress );
				bar.setProgress( _progress );
			}
		}
	}

	private void updateStats()
	{
		( (TextView)findViewById( R.id.doit_overwrites ) ).setText(
			"" + _count_overwrites );
		( (TextView)findViewById( R.id.doit_creates ) ).setText(
			"" + _count_creates );
		( (TextView)findViewById( R.id.doit_merges ) ).setText(
			"" + _count_merges );
		( (TextView)findViewById( R.id.doit_skips ) ).setText(
			"" + _count_skips );
	}

	private void abortImport( boolean show_toaster_popup )
	{
		if( _importerThread != null )
		{
			// try and flag worker thread - did we need to?
			if( _importerThread.setAbort() )
			{
				// wait for worker thread to end
				while( true ) {
					try {
						_importerThread.join();
						break;
					}
					catch( InterruptedException e ) {}
				}

				// notify the user
				if( show_toaster_popup )
					Toast.makeText( this, R.string.doit_importaborted,
						Toast.LENGTH_LONG ).show();
			}
		}

		// destroy some stuff
		_importerThread = null;
		_handler = null;
	}
}
