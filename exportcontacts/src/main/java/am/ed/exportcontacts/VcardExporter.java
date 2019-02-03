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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;

import android.content.SharedPreferences;

public class VcardExporter extends Exporter
{
	protected FileOutputStream _ostream = null;
	protected boolean _first_contact = true;

	public VcardExporter( Doit doit )
	{
		super( doit );
	}

	@Override
	protected void preExport() throws AbortExportException
	{
		SharedPreferences prefs = getSharedPreferences();

		// create output filename
		File file = new File( ConfigureVCF.getSdCardPathPrefix() +
			prefs.getString( "path", "/" ) +
			prefs.getString( "filename", "android-contacts.vcf" ) );

		// check if the output file already exists
		if( file.exists() && file.length() > 0 )
			showContinueOrAbort( R.string.error_vcf_exists );

		// open file
		try {
			_ostream = new FileOutputStream( file );
		}
		catch( FileNotFoundException e ) {
			showError( getText( R.string.error_filenotfound ) +
				file.getPath() );
		}
	}

	/**
	 * Do line folding at 75 chars
	 * @param raw string
	 * @return folded string
	 */
	private String fold( String line )
	{
		StringBuilder ret = new StringBuilder( line.length() );

		// keep pulling off the first line's worth of chars, while the string is
		// still longer than a line should be
		while( line.length() > 75 )
		{
			// length of the line we'll be pulling off
			int len = 75;

			// if splitting at this length would break apart a codepoint, use
			// one less char
			if( Character.isHighSurrogate( line.charAt( len - 1 ) ) )
				len--;

			// count how many backslashes would be at the end of the line we're
			// pulling off
			int count = 0;
			for( int a = len - 1; a >= 0; a-- )
				if( line.charAt( a ) == '\\' )
					count++;
				else
					break;

			// if there would be an odd number of slashes at the end of the line
			// then pull off one fewer characters so that we don't break apart
			// escape sequences
			if( count % 2 == 1 )
				len--;

			// pull off the line and add it to the output, folded
			ret.append( line.substring( 0, len ) + "\n " );
			line = line.substring( len );
		}

		// add any remaining data
		ret.append( line );

		return ret.toString();
	}

	/**
	 * Do unsafe character escaping
	 * @param raw string
	 * @return escaped string
	 */
	private String escape( String str )
	{
		StringBuilder ret = new StringBuilder( str.length() );
		for( int a = 0; a < str.length(); a++ )
		{
			int c = str.codePointAt( a );
			switch( c )
			{
			case '\n':
				// append escaped newline
				ret.append( "\\n" );
				break;
			case ',':
			case ';':
			case '\\':
				// append return character
				ret.append( '\\' );
				// fall through
			default:
				// append character
				ret.append( Character.toChars( c ) );
			}
		}

		return ret.toString();
	}

	/**
	 * join
	 */
	@SuppressWarnings( "rawtypes" )
	public static String join( AbstractCollection s, String delimiter)
	{
		StringBuffer buffer = new StringBuffer();
		Iterator iter = s.iterator();
		if( iter.hasNext() ) {
			buffer.append( iter.next() );
			while( iter.hasNext() ) {
				buffer.append( delimiter );
				buffer.append( iter.next() );
			}
		}
		return buffer.toString();
	}

	/**
	 * Is the provided value a valid date-and-or-time, as per the spec?
	 *
	 * @param value the value
	 * @return true if it is
	 */
	protected boolean isValidDateAndOrTime( String value )
	{
		// ISO 8601:2004 4.1.2 date with 4.1.2.3 a) and b) reduced accuracy
		String date =
			"[0-9]{4}(?:-?[0-9]{2}(?:-?[0-9]{2})?)?";

		// ISO 8601:2000 5.2.1.3 d), e) and f) truncated date representation
		String date_trunc =
			"--(?:[0-9]{2}(?:-?[0-9]{2})?|-[0-9]{2})";

		// ISO 8601:2004 4.2.2 time with 4.2.2.3 reduced accuracy, 4.2.4 UTC and
		// 4.2.5 zone offset, no 4.2.2.4 decimal fraction and no 4.2.3 24:00
		// midnight
		String time =
			"(?:[0-1][0-9]|2[0-3])(?::?[0-5][0-9](?::?(?:60|[0-5][0-9]))?)?" +
			"(?:Z|[-+](?:[0-1][0-9]|2[0-3])(?::?[0-5][0-9])?)?";

		// ISO 8601:2000 5.3.1.4 a), b) and c) truncated time representation
		String time_trunc =
			"-(?:[0-5][0-9](?::?(?:60|[0-5][0-9]))?|-(?:60|[0-5][0-9]))";

		// RFC6350 (vCard 3.0) date-and-or-time with mandatory time designator
		String date_and_or_time =
			"(?:" + date + "|" + date_trunc + ")?" +
			"(?:T(?:" + time + "|" + time_trunc + "))?";

		return value.matches( date_and_or_time );
	}

	protected void writeToFile( byte data[], String identifier )
		throws AbortExportException
	{
		// write to file
		try {
			_ostream.write( data );
			_ostream.flush();
		}
		catch( IOException e ) {
			showError( R.string.error_ioerror );
		}
	}

	@Override
	protected boolean exportContact( ContactData contact )
		throws AbortExportException
	{
		StringBuilder out = new StringBuilder();

		// skip if the contact has no identifiable features
		if( contact.getPrimaryIdentifier() == null )
			return false;

		// append newline
		if( _first_contact )
			_first_contact = false;
		else
			out.append( "\n" );

		// append header
		out.append( "BEGIN:VCARD\n" );
		out.append( "VERSION:3.0\n" );

		// append formatted name
		String identifier = contact.getPrimaryIdentifier();
		if( identifier != null ) identifier = identifier.trim();
		if( identifier == null || identifier.length() == 0 ) {
			showContinueOrAbort( R.string.error_vcf_noname );
			return false;
		}
		out.append( fold( "FN:" + escape( identifier ) ) + "\n" );

		// append name
		String name = contact.getName();
		if( name == null ) name = "";
		String[] bits = name.split( " +" );
		StringBuilder tmp = new StringBuilder();
		for( int a = 1; a < bits.length - 1; a++ ) {
			if( a > 1 ) tmp.append( " " );
			tmp.append( escape( bits[ a ] ) );
		}
		String value = escape( bits[ bits.length - 1 ] ) + ";" +
			( bits.length > 1? escape( bits[ 0 ] ) : "" ) + ";" +
			tmp.toString() + ";;";
		out.append( fold( "N:" + value ) + "\n" );

		// append organisations and titles
		ArrayList< Exporter.ContactData.OrganisationDetail > organisations =
			contact.getOrganisations();
		if( organisations != null ) {
			for( int a = 0; a < organisations.size(); a++ ) {
				if( organisations.get( a ).getOrganisation() != null )
					out.append( fold( "ORG:" + escape(
						organisations.get( a ).getOrganisation() ) ) + "\n" );
				if( organisations.get( a ).getTitle() != null )
					out.append( fold( "TITLE:" + escape(
						organisations.get( a ).getTitle() ) ) + "\n" );
			}
		}

		// append phone numbers
		ArrayList< Exporter.ContactData.NumberDetail > numbers =
			contact.getNumbers();
		if( numbers != null ) {
			for( int a = 0; a < numbers.size(); a++ ) {
				ArrayList< String > types = new ArrayList< String >();
				switch( numbers.get( a ).getType() ) {
				case ContactData.TYPE_HOME:
					types.add( "VOICE" ); types.add( "HOME" ); break;
				case ContactData.TYPE_WORK:
					types.add( "VOICE" ); types.add( "WORK" ); break;
				case ContactData.TYPE_FAX_HOME:
					types.add( "FAX" ); types.add( "HOME" ); break;
				case ContactData.TYPE_FAX_WORK:
					types.add( "FAX" ); types.add( "WORK" ); break;
				case ContactData.TYPE_PAGER:
					types.add( "PAGER" ); break;
				case ContactData.TYPE_MOBILE:
					types.add( "VOICE" ); types.add( "CELL" ); break;
				}
				if( a == 0 ) types.add( "PREF" );
				out.append( fold( "TEL" +
					( types.size() > 0? ";TYPE=" + join( types, "," ) : "" ) +
					":" + escape( numbers.get( a ).getNumber() ) ) + "\n" );
			}
		}

		// append email addresses
		ArrayList< Exporter.ContactData.EmailDetail > emails =
			contact.getEmails();
		if( emails != null ) {
			for( int a = 0; a < emails.size(); a++ ) {
				ArrayList< String > types = new ArrayList< String >();
				types.add( "INTERNET" );
				switch( emails.get( a ).getType() ) {
				case ContactData.TYPE_HOME:
					types.add( "HOME" ); break;
				case ContactData.TYPE_WORK:
					types.add( "WORK" ); break;
				}
				out.append( fold( "EMAIL" +
					( types.size() > 0? ";TYPE=" + join( types, "," ) : "" ) +
					":" + escape( emails.get( a ).getEmail() ) ) + "\n" );
			}
		}

		// append addresses
		ArrayList< Exporter.ContactData.AddressDetail > addresses =
			contact.getAddresses();
		if( addresses != null ) {
			for( int a = 0; a < addresses.size(); a++ ) {
				ArrayList< String > types = new ArrayList< String >();
				types.add( "POSTAL" );
				switch( addresses.get( a ).getType() ) {
				case ContactData.TYPE_HOME:
					types.add( "HOME" ); break;
				case ContactData.TYPE_WORK:
					types.add( "WORK" ); break;
				}
				// we use LABEL because is accepts formatted text (whereas ADR
				// expects semicolon-delimited fields with specific purposes)
				out.append( fold( "LABEL" +
					( types.size() > 0? ";TYPE=" + join( types, "," ) : "" ) +
					":" + escape( addresses.get( a ).getAddress() ) ) + "\n" );
			}
		}

		// append notes
		ArrayList< String > notes = contact.getNotes();
		if( notes != null )
			for( int a = 0; a < notes.size(); a++ )
				out.append( fold( "NOTE:" + escape( notes.get( a ) ) ) + "\n" );

		// append birthday
		String birthday = contact.getBirthday();
		if( birthday != null ) {
			birthday.trim();
			if( isValidDateAndOrTime( birthday ) )
				out.append( fold( "BDAY:" + escape( birthday ) ) + "\n" );
			else
				out.append(
					fold( "BDAY;VALUE=text:" + escape( birthday ) ) + "\n" );
		}

		// append footer
		out.append( "END:VCARD\n" );

		// replace '\n' with "\r\n" (spec requires CRLF)
		int pos = 0;
		while( true ) {
			pos = out.indexOf( "\n", pos );
			if( pos == -1 ) break;
			out.replace( pos, pos + 1, "\r\n" );

			// skip our inserted string
			pos += 2;
		}

		// write to file
		writeToFile( out.toString().getBytes(), identifier );

		return true;
	}

}
