/*
 * WizardActivity.java
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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class WizardActivity extends Activity
{
	private Class< ? > _next_class;

	@Override
	protected void onCreate( Bundle saved_instance_state )
	{
		super.onCreate( saved_instance_state );

		// enable back button based on intent data
		Bundle extras = getIntent().getExtras();
		if( extras != null )//&& extras.getBoolean( "back-enabled" ) )
			( (Button)findViewById( R.id.back ) ).setEnabled( true );

		// set up next button
		Button next = (Button)findViewById( R.id.next );
		next.setOnClickListener( new View.OnClickListener() {
			public void onClick( View view ) {
				onNext();
			}
		} );

		// set up back button
		Button back = (Button)findViewById( R.id.back );
		back.setOnClickListener( new View.OnClickListener() {
			public void onClick( View view ) {
				onBack();
			}
		} );
	}

	@Override
	protected void onActivityResult( int request_code, int result_code,
		Intent data )
	{
		if( result_code == RESULT_OK ) {
			setResult( RESULT_OK );
			finish();
		}
	}

	protected void onNext()
	{
		// create bundle with back enabled state
		Bundle bundle = new Bundle();
		bundle.putBoolean( "back-enabled", true );
		Intent i = new Intent( this, _next_class );
		i.putExtras( bundle );

		// start next activity
		startActivityForResult( i, 0 );
	}

	protected void onBack()
	{
		setResult( RESULT_CANCELED );
		finish();
	}

	protected void setNextActivity( Class< ? > cls )
	{
		_next_class = cls;

		// enable next button
		( (Button)findViewById( R.id.next ) ).setEnabled( true );
	}

	public SharedPreferences getSharedPreferences()
	{
		return super.getSharedPreferences( "ImportContacts", 0 );
	}

	@Override
	public void onConfigurationChanged( Configuration new_config ) {
		super.onConfigurationChanged( new_config );
	}
}
