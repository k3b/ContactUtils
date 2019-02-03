/*
 * ConfigureVCFExportActivity.java
 *
 * Copyright (C) 2010 Tim Marston <tim@ed.am>
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

import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ConfigureVCFExportActivity extends WizardActivity
{
	public final static int DIALOG_FILECHOOSER = 1;
	public final static int DIALOG_NOSDCARD = 2;

	private FileChooser _file_chooser = null;

	// the sdcard path prefix
	private String _sdcard_prefix;

	// the save path
	private String _path;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		setContentView( R.layout.configure_vcf );
		super.onCreate( savedInstanceState );

		setNextActivity( Doit.class );

		// get sdcard prefix
		_sdcard_prefix = getSdCardPathPrefix();
		if( _sdcard_prefix == null )
			showDialog( DIALOG_NOSDCARD );

		// create file chooser
		_file_chooser = new FileChooser( this );
		_file_chooser.setMode( FileChooser.MODE_DIR );
//		String[] extensions = { "vcf" };
//		_file_chooser.setExtensions( extensions );
		_file_chooser.setDismissListener(
			new DialogInterface.OnDismissListener() {
				public void onDismiss( DialogInterface dialog )
				{
					if( _file_chooser.getOk() ) {
						_path = _file_chooser.getPath();
						updatePathButton();
					}
				}
			} );
		if( _sdcard_prefix != null )
			_file_chooser.setPathPrefix( _sdcard_prefix );

		// set up browser button
		Button path_button = (Button)findViewById( R.id.path );
		path_button.setOnClickListener( new View.OnClickListener() {
			public void onClick( View view ) {
				onBrowse();
			}
		} );
	}

	@Override
	protected void onPause() {
		super.onPause();

		SharedPreferences.Editor editor = getSharedPreferences().edit();

		// path and filename
		editor.putString( "path", _path );
		EditText filename = (EditText)findViewById( R.id.filename );
		editor.putString( "filename", filename.getText().toString() );

		editor.commit();
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences prefs = getSharedPreferences();

/*		// default filename
		Calendar now = Calendar.getInstance();
		NumberFormat formatter = new DecimalFormat( "00" );
		String date = now.get( Calendar.YEAR ) + "-" +
			formatter.format( now.get( Calendar.MONTH ) ) + "-" +
			formatter.format( now.get( Calendar.DAY_OF_MONTH ) );
*/
		// path and filename
		_path = prefs.getString( "path", "/" );
		updatePathButton();
		EditText filename = (EditText)findViewById( R.id.filename );
		filename.setText( prefs.getString( "filename",
			"android-contacts.vcf" ) );
	}

	static protected String getSdCardPathPrefix()
	{
		// check sdcard status
		String state = Environment.getExternalStorageState();
		if( !Environment.MEDIA_MOUNTED.equals( state ) &&
			!Environment.MEDIA_MOUNTED_READ_ONLY.equals( state ) )
		{
			// no sdcard mounted
			return null;
		}

		// get sdcard path
		String sdcard_path;
		try {
			sdcard_path = Environment.getExternalStorageDirectory()
				.getCanonicalPath();
			if( sdcard_path.charAt( sdcard_path.length() - 1 ) == '/' )
				sdcard_path =
					sdcard_path.substring( 0, sdcard_path.length() - 1 );
		}
		catch( IOException e ) {
			sdcard_path = null;
		}

		return sdcard_path;
	}

	protected void updatePathButton()
	{
		Button path_button = (Button)findViewById( R.id.path );
		if( _sdcard_prefix != null )
			path_button.setText(
				_file_chooser.prettyPrint( _sdcard_prefix + _path, true ) );
	}

	protected void onBrowse()
	{
		// get path
		Button path_button = (Button)findViewById( R.id.path );

		// set a path for this incantation
		_file_chooser.setPath( path_button.getText().toString() );

		showDialog( DIALOG_FILECHOOSER );
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		Dialog ret = null;

		switch( id )
		{
		case DIALOG_FILECHOOSER:
			ret = _file_chooser.onCreateDialog();
			break;

		case DIALOG_NOSDCARD:
			ret = new AlertDialog.Builder( this )
			.setIcon( R.drawable.alert_dialog_icon )
			.setTitle( R.string.error_title )
			.setMessage( R.string.error_nosdcard )
			.setPositiveButton( R.string.error_ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
						int whichButton)
					{
						// close the whole app!
						setResult( RESULT_OK );
						finish();
					}
				} )
			.create();
			break;
		}

		return ret;
	}

	@Override
	protected void onPrepareDialog( int id, Dialog dialog )
	{
		switch( id )
		{
		case DIALOG_FILECHOOSER:
			_file_chooser.onPrepareDialog( this, dialog );
			break;
		}

		super.onPrepareDialog( id, dialog );
	}
}
