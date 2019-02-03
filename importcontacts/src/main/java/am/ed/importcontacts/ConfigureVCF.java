/*
 * ConfigureVCF.java
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

import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ConfigureVCF extends WizardActivity
{
	public final static int DIALOG_FILEORDIR = 1;
	public final static int DIALOG_FILECHOOSER = 2;
	public final static int DIALOG_NOSDCARD = 3;

	private FileChooser _file_chooser = null;

	// the sdcard path prefix
	private String _sdcard_prefix;

	// the save path
	private String _path;

	// was the dialog closed normally?
	private boolean _ok = false;

	// for the fileordir dialog, was file selected?
	boolean _ok_file;

	// reference to the dialog
	Dialog _dialog;

	@Override
	protected void onCreate( Bundle saved_instance_state )
	{
		setContentView( R.layout.configure_vcf );
		super.onCreate( saved_instance_state );

		setNextActivity( Merge.class );

		// get sdcard prefix
		_sdcard_prefix = getSdCardPathPrefix();
		if( _sdcard_prefix == null )
			showDialog( DIALOG_NOSDCARD );

		// create file chooser
		_file_chooser = new FileChooser( this );
		String[] extensions = { "vcf" };
		_file_chooser.setExtensions( extensions );
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
		Button button = (Button)findViewById( R.id.path );
		button.setOnClickListener( new View.OnClickListener() {
			public void onClick( View view ) {
				onBrowse();
			}
		} );
	}

	private void onBrowse()
	{
		showDialog( DIALOG_FILEORDIR );
	}

	private void showBrowseDialog()
	{
		// set a path for this incantation
		_file_chooser.setMode(
			_ok_file? FileChooser.MODE_FILE : FileChooser.MODE_DIR );
		_file_chooser.setPath( _path );

		// show dialog
		showDialog( DIALOG_FILECHOOSER );
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

		TextView location_text = (TextView)findViewById( R.id.location );
		location_text.setText( getString( _path.endsWith( "/" )?
			R.string.vcf_location_dir : R.string.vcf_location_file ) );
	}

	@Override
	protected void onPause() {
		super.onPause();

		SharedPreferences.Editor editor = getSharedPreferences().edit();

		editor.putString( "location", _path );

		editor.commit();
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences prefs = getSharedPreferences();

		// location
		_path = prefs.getString( "location", "/" );
		updatePathButton();
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		Dialog ret = null;

		switch( id )
		{
		case DIALOG_FILEORDIR:
			// custom layout in an AlertDialog
			LayoutInflater factory = LayoutInflater.from( this );
			final View dialogView = factory.inflate(
				R.layout.fileordir, null );

			// wire up buttons
			( (Button)dialogView.findViewById(  R.id.file ) )
				.setOnClickListener( new View.OnClickListener() {
					public void onClick( View view ) {
						_ok = true;
						_ok_file = true;
						_dialog.dismiss();
					}
				} );
			( (Button)dialogView.findViewById(  R.id.dir ) )
			.setOnClickListener( new View.OnClickListener() {
				public void onClick( View view ) {
					_ok = true;
					_ok_file = false;
					_dialog.dismiss();
				}
			} );

			_dialog = ret = new AlertDialog.Builder( this )
				.setTitle( "Do you want to?" )
				.setView( dialogView )
				.create();
			ret.setOnDismissListener(
				new DialogInterface.OnDismissListener() {
					public void onDismiss( DialogInterface dialog )
					{
						if( _ok ) showBrowseDialog();
					}
				} );
			break;

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
		case DIALOG_FILEORDIR:
			_ok = false;
			break;

		case DIALOG_FILECHOOSER:
			_file_chooser.onPrepareDialog( this, dialog );
			break;
		}

		super.onPrepareDialog( id, dialog );
	}
}
