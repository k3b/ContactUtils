/*
 * Intro.java
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Bundle;
import android.text.util.Linkify;
import android.widget.TextView;

public class Intro extends WizardActivity {

	@Override
	protected void onCreate( Bundle saved_instance_state )
	{
		setContentView( R.layout.intro );
		super.onCreate( saved_instance_state );

		setNextActivity( ConfigureVCF.class );

		TextView link = (TextView)findViewById( R.id.intro_link );
		Linkify.addLinks( link,
			Pattern.compile( "The Import Contacts webpage" ),
			"", null, new Linkify.TransformFilter() {
				public String transformUrl( Matcher match, String url ) {
					return "http://ed.am/dev/android/import-contacts";
				}
			}
		);
	}

}
