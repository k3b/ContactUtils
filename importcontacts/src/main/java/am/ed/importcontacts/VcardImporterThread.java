/*
 * VCFImporter.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import android.os.Environment;

import de.k3b.contactlib.ContactData;

public class VcardImporterThread extends ImporterThread
{
	private int _vcard_count = 0;
	private int _progress = 0;

	public VcardImporterThread(ImportActivity importActivity)
	{
		super(importActivity);
	}

	@Override
	protected void onImport() throws AbortImportException
	{
		SharedPreferences prefs = getSharedPreferences();

		// update UI
		setProgressMessage( R.string.doit_scanning );

		// get a list of vcf files
		File[] files = null;
		try
		{
			// check SD card is mounted
			String state = Environment.getExternalStorageState();
			if( !Environment.MEDIA_MOUNTED.equals( state ) &&
				!Environment.MEDIA_MOUNTED_READ_ONLY.equals( state ) )
			{
				showError( R.string.error_nosdcard );
			}

			// open directory
			File file = new File( Environment.getExternalStorageDirectory(),
				prefs.getString( "location", "/" ) );
			if( !file.exists() )
				showError( R.string.error_locationnotfound );

			// directory, or file?
			if( file.isDirectory() )
			{
				// get files
				class VCardFilter implements FilenameFilter {
					public boolean accept( File dir, String name ) {
						return name.toLowerCase( Locale.ENGLISH )
							.endsWith( ".vcf" );
					}
				}
				files = file.listFiles( new VCardFilter() );
			}
			else
			{
				// use just this file
				files = new File[ 1 ];
				files[ 0 ] = file;
			}
		}
		catch( SecurityException e ) {
			showError( R.string.error_locationpermissions );
		}

		// check num files and set progress max
		if( files != null && files.length > 0 )
			setProgressMax( files.length );
		else
			showError( R.string.error_locationnofiles );

		// scan through the files
		setTmpProgress( 0 );
		for( int i = 0; i < files.length; i++ ) {
			countVCardFile( files[ i ] );
			setTmpProgress( i );
		}
		setProgressMax( _vcard_count );	// will also update tmp progress

		// import them
		setProgress( 0 );
		for( int i = 0; i < files.length; i++ )
			importVCardFile( files[ i ] );
		setProgress( _vcard_count );
	}

	private void countVCardFile( File file ) throws AbortImportException
	{
		try
		{
			// open file
			BufferedReader reader = new BufferedReader(
				new FileReader( file ) );

			// read
			String line;
			boolean in_vcard = false;
			while( ( line = reader.readLine() ) != null )
			{
				if( !in_vcard )
				{
					// look for vcard beginning
					if( line.matches( "(?i)BEGIN[ \t]*:[ \t]*VCARD.*" ) ) {
						in_vcard = true;
						_vcard_count++;
					}
					// check for vMsg files
					else if( line.matches( "(?i)BEGIN[ \t]*:[ \t]*VMSG.*" ) ) {
						showError( getText( R.string.error_vcf_vmsgfile )
							+ file.getName() );
					}
				}
				else if( line.matches( "(?i)END[ \t]*:[ \t]*VCARD.*" ) )
					in_vcard = false;
			}
			reader.close();

		}
		catch( FileNotFoundException e ) {
			showError( getText( R.string.error_filenotfound ) +
				file.getName() );
		}
		catch( IOException e ) {
			showError( getText( R.string.error_ioerror ) + file.getName() );
		}
	}

	private void importVCardFile( File file ) throws AbortImportException
	{
		// check file is good
		if( !file.exists() )
			showError( getText( R.string.error_filenotfound ) +
				file.getName() );
		if( file.length() == 0 )
			showError( getText( R.string.error_fileisempty ) +
				file.getName() );

		try
		{
			// open/read file
			FileInputStream istream = new FileInputStream( file );
			byte[] content = new byte[ (int)file.length() ];
			istream.read( content );
			istream.close();

			// import
			importVCardFileContent( content, file.getName() );
		}
		catch( OutOfMemoryError e ) {
			showError( R.string.error_outofmemory );
		}
		catch( FileNotFoundException e ) {
			showError( getText( R.string.error_filenotfound ) +
				file.getName() );
		}
		catch( IOException e ) {
			showError( getText( R.string.error_ioerror ) + file.getName() );
		}
	}

	private void importVCardFileContent( byte[] content, String fileName )
		throws AbortImportException
	{
		// go through lines
		Vcard vcard = null;
		int vcard_start_line = 0;
		ContentLineIterator cli = new ContentLineIterator( content );
		while( cli.hasNext() )
		{
			ContentLine content_line = cli.next();

			// get a US-ASCII version of the string, for processing
			String line = content_line.getUsAsciiLine();

			if( vcard == null ) {
				// look for vcard beginning
				if( line.matches( "(?i)BEGIN[ \t]*:[ \t]*VCARD.*" ) ) {
					setProgress( _progress++ );
					vcard = new Vcard();
					vcard_start_line = cli.getLineNumber();
				}
			}
			else {
				// look for vcard content or ending
				if( line.matches( "(?i)END[ \t]*:[ \t]*VCARD.*" ) )
				{
					// finalise the vcard/contact
					try {
						vcard.finaliseVcard();

						// pass the finalised contact to the importer
						importContact( vcard );
					}
					catch( Vcard.ParseException e ) {
						showContinueOrAbort(
							getText( R.string.error_vcf_parse ).toString()
							+ fileName +
							getText( R.string.error_vcf_parse_line ).toString()
							+ cli.getLineNumber() + ":\n" + e.getMessage() );
						skipContact();
					}
					catch( ContactData.ContactNotIdentifiableException e ) {
						showContinueOrAbort(
							getText( R.string.error_vcf_parse ).toString()
							+ fileName +
							getText( R.string.error_vcf_parse_line ).toString()
							+ vcard_start_line + ":\n" + getText(
								R.string.error_vcf_notenoughinfo ).toString() );
						skipContact();
					}
					catch( Vcard.SkipImportException e ) {
						skipContact();
					}

					// discard this vcard
					vcard = null;
				}
				else
				{
					// try giving the line to the vcard
					try {
						vcard.parseLine( content_line );
					}
					catch( Vcard.ParseException e ) {
						skipContact();
						showContinueOrAbort(
							getText( R.string.error_vcf_parse ).toString()
							+ fileName +
							getText( R.string.error_vcf_parse_line ).toString()
							+ cli.getLineNumber() + "\n" + e.getMessage() );

						// Although we're continuing, we still need to abort
						// this vCard.  Further lines will be ignored until we
						// get to another BEGIN:VCARD line.
						vcard = null;
					}
					catch( Vcard.SkipImportException e ) {
						skipContact();
						// Abort this vCard.  Further lines will be ignored until
						// we get to another BEGIN:VCARD line.
						vcard = null;
					}
				}
			}
		}
	}

	class ContentLine
	{
		private ByteBuffer _buffer;
		private boolean _folded_next;
		private String _line;

		public ContentLine( ByteBuffer buffer, boolean folded_next )
		{
			_buffer = buffer;
			_folded_next = folded_next;
			_line = null;
		}

		public ByteBuffer getBuffer()
		{
			return _buffer;
		}

		public boolean doesNextLineLookFolded()
		{
			return _folded_next;
		}

		public String getUsAsciiLine()
		{
			// generated line and cache it
			if( _line == null ) {
				try {
					_line = new String( _buffer.array(), _buffer.position(),
						_buffer.limit() - _buffer.position(), "US-ASCII" );
				}
				catch( UnsupportedEncodingException e ) {
					// we know US-ASCII *is* supported, so appease the
					// compiler...
				}
			}

			// return cached line
			return _line;
		}
	}

	class ContentLineIterator implements Iterator< ContentLine >
	{
		protected byte[] _content = null;
		protected int _pos = 0;
		protected int _line = 0;

		public ContentLineIterator( byte[] content )
		{
			_content = content;
		}

		@Override
		public boolean hasNext()
		{
			return _pos < _content.length;
		}

		@Override
		public ContentLine next()
		{
			int initial_pos = _pos;

			// find newline
			for( ; _pos < _content.length; _pos++ )
				if( _content[ _pos ] == '\n' )
				{
					// adjust for a \r preceding the \n
					int to = ( _pos > 0 && _content[ _pos - 1 ] == '\r' &&
						_pos > initial_pos )? _pos - 1 : _pos;
					_pos++;
					_line++;
					return new ContentLine(
						ByteBuffer.wrap( _content, initial_pos,
							to - initial_pos ),
						doesNextLineLookFolded() );
				}

			// we didn't find one, but were there bytes left?
			if( _pos != initial_pos ) {
				int to = _pos;
				_pos++;
				_line++;
				return new ContentLine(
					ByteBuffer.wrap( _content, initial_pos,
						to - initial_pos ),
					doesNextLineLookFolded() );
			}

			// no bytes left
			throw new NoSuchElementException();
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Does the next line, if there is one, look like it should be folded
		 * onto the end of this one?
		 * @return
		 */
		private boolean doesNextLineLookFolded()
		{
			return _pos > 0 && _pos < _content.length &&
				_content[ _pos - 1 ] == '\n' &&
				( _content[ _pos ] == ' ' || _content[ _pos ] == '\t' );
		}

		public int getLineNumber()
		{
			return _line;
		}
	}

	private class Vcard extends ContactData
	{
		private final static int NAMELEVEL_NONE = 0;
		private final static int NAMELEVEL_N = 1;
		private final static int NAMELEVEL_FN = 2;

		private final static int MULTILINE_NONE = 0;
		private final static int MULTILINE_ENCODED = 1;	// v2.1 quoted-printable
		private final static int MULTILINE_ESCAPED = 2;	// v2.1 \\CRLF
		private final static int MULTILINE_FOLDED = 3;	// MIME-DIR folding

		private String _version = null;
		private Vector< ContentLine > _content_lines = null;
		private int _name_level = NAMELEVEL_NONE;
		private int _parser_multiline_state = MULTILINE_NONE;
		private String _parser_current_name_and_params = null;
		private String _parser_buffered_value_so_far = "";
		private String _cached_organisation = null;
		private String _cached_title = null;

		protected class UnencodeResult
		{
			private boolean _another_line_required;
			private ByteBuffer _buffer;

			public UnencodeResult( boolean another_line_required,
				ByteBuffer buffer )
			{
				_another_line_required = another_line_required;
				_buffer = buffer;
			}

			public boolean isAnotherLineRequired()
			{
				return _another_line_required;
			}

			public ByteBuffer getBuffer()
			{
				return _buffer;
			}
		}

		@SuppressWarnings("serial")
		protected class ParseException extends Exception
		{
			@SuppressWarnings("unused")
			public ParseException( String error )
			{
				super( error );
			}

			public ParseException( int res )
			{
				super( VcardImporterThread.this.getText( res ).toString() );
			}
		}

		@SuppressWarnings("serial")
		protected class SkipImportException extends Exception { }

		private String extractCollonPartFromLine( ContentLine content_line,
			boolean former )
		{
			// split line into name and value parts and check to make sure we
			// only got 2 parts and that the first part is not zero in length
			String[] parts = content_line.getUsAsciiLine().split( ":", 2 );
			if( parts.length == 2 && parts[ 0 ].length() > 0 )
				return parts[ former? 0 : 1 ].trim();

			return null;
		}

		private String extractNameAndParamsFromLine( ContentLine content_line )
		{
			return extractCollonPartFromLine( content_line, true );
		}

		private String extractValueFromLine( ContentLine content_line )
		{
			return extractCollonPartFromLine( content_line, false );
		}

		public void parseLine( ContentLine content_line )
			throws ParseException, SkipImportException,
			AbortImportException
		{
			// do we have a version yet?
			if( _version == null )
			{
				// tentatively get name and params from line
				String name_and_params =
					extractNameAndParamsFromLine( content_line );

				// is it a version line?
				if( name_and_params != null &&
					name_and_params.equalsIgnoreCase( "VERSION" ) )
				{
					// yes, get it!
					String value = extractValueFromLine( content_line );
					if( value == null || (
						!value.equals( "2.1" ) && !value.equals( "3.0" ) ) )
					{
						throw new ParseException( R.string.error_vcf_version );
					}
					_version = value;

					// parse any buffers we've been accumulating while we waited
					// for a version
					if( _content_lines != null )
						for( int i = 0; i < _content_lines.size(); i++ )
							parseLine( _content_lines.get( i ) );
					_content_lines = null;
				}
				else
				{
					// no, so stash this line till we get a version
					if( _content_lines == null )
						_content_lines = new Vector< ContentLine >();
					_content_lines.add( content_line );
				}
			}
			else
			{
				// name and params and the position in the buffer where the
				// "value" part of the line starts
				String name_and_params;
				int pos;

				if( _parser_multiline_state != MULTILINE_NONE )
				{
					// if we're currently in a multi-line value, use the stored
					// property name and parameters
					name_and_params = _parser_current_name_and_params;

					// skip some initial line characters, depending on the type
					// of multi-line we're handling
					pos = content_line.getBuffer().position();
					switch( _parser_multiline_state )
					{
					case MULTILINE_FOLDED:
						pos++;
						break;
					case MULTILINE_ENCODED:
						while( pos < content_line.getBuffer().limit() && (
							content_line.getBuffer().get( pos ) == ' ' ||
							content_line.getBuffer().get( pos ) == '\t' ) )
						{
							pos++;
						}
						break;
					default:
						// do nothing
					}

					// take us out of multi-line so that we can re-detect that
					// this line is a multi-line or not
					_parser_multiline_state = MULTILINE_NONE;
				}
				else
				{
					// skip empty lines
					if( content_line.getUsAsciiLine().trim().length() == 0 )
						return;

					// get name and params from line, and since we're not
					// parsing a subsequent line in a multi-line, this should
					// not fail, or it's an error
					name_and_params =
						extractNameAndParamsFromLine( content_line );
					if( name_and_params == null )
						throw new ParseException(
							R.string.error_vcf_malformed );

					// calculate how many chars to skip from beginning of line
					// so we skip the property "name:" part
					pos = content_line.getBuffer().position() +
						name_and_params.length() + 1;

					// reset the saved multi-line state
					_parser_current_name_and_params = name_and_params;
					_parser_buffered_value_so_far = "";
				}

				// get value from buffer, as raw bytes
				ByteBuffer value;
				value = ByteBuffer.wrap( content_line.getBuffer().array(), pos,
					content_line.getBuffer().limit() - pos );

				// get parameter parts
				String[] name_param_parts = name_and_params.split( ";", -1 );
				for( int i = 0; i < name_param_parts.length; i++ )
					name_param_parts[ i ] = name_param_parts[ i ].trim();

				// determine whether we care about this entry
				final HashSet< String > interesting_fields =
					new HashSet< String >( Arrays.asList( new String[] { "N",
						"FN", "ORG", "TITLE", "TEL", "EMAIL", "ADR", "LABEL" }
				) );
				boolean is_interesting_field =
					interesting_fields.contains(
						name_param_parts[ 0 ].toUpperCase( Locale.ENGLISH ) );

				// parse encoding parameter
				String encoding = checkParam( name_param_parts, "ENCODING" );
				if( encoding != null )
					encoding = encoding.toUpperCase( Locale.ENGLISH );
				if( is_interesting_field && encoding != null &&
					!encoding.equalsIgnoreCase( "8BIT" ) &&
					!encoding.equalsIgnoreCase( "QUOTED-PRINTABLE" ) )
					//&& !encoding.equalsIgnoreCase( "BASE64" ) )
				{
					throw new ParseException( R.string.error_vcf_encoding );
				}

				// parse charset parameter
				String charset = checkParam( name_param_parts, "CHARSET" );
				if( charset != null )
					charset = charset.toUpperCase( Locale.ENGLISH );
				if( charset != null &&
					!charset.equalsIgnoreCase( "US-ASCII" ) &&
					!charset.equalsIgnoreCase( "ASCII" ) &&
					!charset.equalsIgnoreCase( "UTF-8" ) )
				{
					throw new ParseException( R.string.error_vcf_charset );
				}

				// do unencoding (or default to a fake unencoding result with
				// the raw string)
				UnencodeResult unencoding_result = null;
				if( encoding != null &&
					encoding.equalsIgnoreCase( "QUOTED-PRINTABLE" ) )
				{
					unencoding_result = unencodeQuotedPrintable( value );
				}
//				else if( encoding != null &&
//					encoding.equalsIgnoreCase( "BASE64" ) )
//				{
//					unencoding_result = unencodeBase64( props[ 1 ], charset );
//				}
				if( unencoding_result != null ) {
					value = unencoding_result.getBuffer();
					if( unencoding_result.isAnotherLineRequired() )
						_parser_multiline_state = MULTILINE_ENCODED;
				}

				// convert 8-bit US-ASCII charset to UTF-8 (where no charset is
				// specified for a v2.1 vcard entry, we assume it's US-ASCII)
				if( ( charset == null && _version.equals( "2.1" ) ) ||
					( charset != null && (
						charset.equalsIgnoreCase( "ASCII" ) ||
						charset.equalsIgnoreCase( "US-ASCII" ) ) ) )
				{
					value = transcodeAsciiToUtf8( value );
				}

				// process charset (value is now in UTF-8)
				String string_value;
				try {
					string_value = new String( value.array(), value.position(),
						value.limit() - value.position(), "UTF-8" );
				} catch( UnsupportedEncodingException e ) {
					throw new ParseException( R.string.error_vcf_charset );
				}

				// for some entries that have semicolon-separated value parts,
				// check to see if the value ends in an escape character, which
				// indicates that we have a multi-line value
				if( ( name_param_parts[ 0 ].equalsIgnoreCase( "N" ) ||
					name_param_parts[ 0 ].equalsIgnoreCase( "ORG" ) ||
					name_param_parts[ 0 ].equalsIgnoreCase( "ADR" ) ) &&
					doesStringEndInAnEscapeChar( string_value ) )
				{
					_parser_multiline_state = MULTILINE_ESCAPED;
					string_value = string_value.substring( 0,
						string_value.length() - 1 );
				}

				// if we know we're not in an encoding-based multi-line, check
				// to see if we're in a folded multi-line
				if( _parser_multiline_state == MULTILINE_NONE &&
					content_line.doesNextLineLookFolded() )
				{
					_parser_multiline_state = MULTILINE_FOLDED;
				}

				// handle multi-lines by buffering them and parsing them when we
				// are processing the last line in a multi-line sequence
				if( _parser_multiline_state != MULTILINE_NONE ) {
					_parser_buffered_value_so_far += string_value;
					return;
				}
				String complete_value =
					( _parser_buffered_value_so_far + string_value ).trim();

				// ignore empty values
				if( complete_value.length() < 1 ) return;

				// parse some properties
				if( name_param_parts[ 0 ].equalsIgnoreCase( "N" ) )
					parseN( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "FN" ) )
					parseFN( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "ORG" ) )
					parseORG( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "TITLE" ) )
					parseTITLE( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "TEL" ) )
					parseTEL( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "EMAIL" ) )
					parseEMAIL( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "ADR" ) )
					parseADR( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "LABEL" ) )
					parseLABEL( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "NOTE" ) )
					parseNOTE( name_param_parts, complete_value );
				else if( name_param_parts[ 0 ].equalsIgnoreCase( "BDAY" ) )
					parseBDAY( name_param_parts, complete_value );
			}
		}

		private boolean doesStringEndInAnEscapeChar( String string )
		{
			// count the number of backslashes at the end of the string
			int count = 0;
			for( int a = string.length() - 1; a >= 0; a-- )
				if( string.charAt( a ) == '\\' )
					count++;
				else
					break;

			// if there are an even number of backslashes then the final one
			// doesn't count
			return ( count & 1 ) == 1;
		}

		private String[] splitValueByCharacter( String value, char character )
		{
			// split string in to parts by specified character
			ArrayList< String > parts = new ArrayList< String >(
				Arrays.asList( value.split( "" + character ) ) );

			// go through parts
			for( int a = 0; a < parts.size(); a++ )
			{
				String str = parts.get( a );

				// Look for parts that end in an escape character, but ignore
				// the final part.  We've already detected escape chars at the
				// end of the final part in parseLine() and handled multi-lines
				// accordingly.
				if( a < parts.size() - 1 &&
					doesStringEndInAnEscapeChar( str ) )
				{
					// append the escaped character, join the next part to this
					// part and remove the next part
					parts.set( a, str.substring( 0, str.length() - 1 ) +
						character + parts.get( a + 1 ) );
					parts.remove( a + 1 );

					// re-visit this part
					a--;
					continue;
				}

				// trim and replace string
				str = str.trim();
				parts.set( a, str );
			}

			String[] ret = new String[ parts.size() ];
			return parts.toArray( ret );
		}

		private String unescapeValue( String value )
		{
			StringBuilder ret = new StringBuilder( value.length() );
			boolean in_escape = false;
			for( int a = 0; a < value.length(); a++ )
			{
				int c = value.codePointAt( a );

				// process a normal character
				if( !in_escape ) {
					if( c == '\\' )
						in_escape = true;
					else
						ret.append( Character.toChars( c ) );
					continue;
				}

				// process an escape sequence
				in_escape = false;
				switch( c )
				{
				case 'T':
				case 't':
					// add tab (invalid/non-standard, but accepted)
					ret.append( '\t' );
					break;
				case 'N':
				case 'n':
					// add newline
					ret.append( '\n' );
					break;
				case '\\':
				case ',':
				case ';':
					// add escaped character
					ret.append( Character.toChars( c ) );
					break;
				default:
					// unknown escape sequence, so add it unescaped
					// (invalid/non-standard, but accepted)
					ret.append( "\\" );
					ret.append( Character.toChars( c ) );
					break;
				}
			}

			return ret.toString();
		}

		private void parseN( String[] params, String value )
		{
			// already got a better name?
			if( _name_level >= NAMELEVEL_N ) return;

			// get name parts
			String[] name_parts = splitValueByCharacter( value, ';' );

			// build name
			value = "";
			final int[] part_order = { 3, 1, 2, 0, 4 };
			for( int a = 0; a < part_order.length; a++ )
				if( name_parts.length > part_order[ a ] &&
					name_parts[ part_order[ a ] ].length() > 0 )
				{
					// split this part in to it's comma-separated bits
					String[] name_part_parts = splitValueByCharacter(
						name_parts[ part_order[ a ] ], ',' );
					for( int b = 0; b < name_part_parts.length; b++ )
						if( name_part_parts[ b ].length() > 0 )
						{
							if( value.length() > 0 ) value += " ";
							value += name_part_parts[ b ];
						}
				}

			// set name
			setName( unescapeValue( value ) );
			_name_level = NAMELEVEL_N;
		}

		private void parseFN( String[] params, String value )
		{
			// already got a better name?
			if( _name_level >= NAMELEVEL_FN ) return;

			// set name
			setName( unescapeValue( value ) );
			_name_level = NAMELEVEL_FN;
		}

		private void parseORG( String[] params, String value )
		{
			// get org parts
			String[] org_parts = splitValueByCharacter( value, ';' );
			if( org_parts == null || org_parts.length < 1 ) return;

			// build organisation name
			StringBuilder builder = new StringBuilder(
				String.valueOf( org_parts[ 0 ] ) );
			for( int a = 1; a < org_parts.length; a++ )
				builder.append( ", " ).append( org_parts[ a ] );
			String organisation = unescapeValue( builder.toString() );

			// set organisation name (using a title we've previously found)
			addOrganisation( organisation, _cached_title, true );

			// if we've not previously found a title, store this organisation
			// name (we'll need it when we find a title to update the
			// organisation, by name), else if we *have* previously found a
			// title, clear it (since we just used it)
			if( _cached_title == null )
				_cached_organisation = organisation;
			else
				_cached_title = null;
		}

		private void parseTITLE( String[] params, String value )
		{
			value = unescapeValue( value );

			// if we previously had an organisation, look it up and append this
			// title to it
			if( _cached_organisation != null && hasOrganisations() ) {
				Map< String, OrganisationDetail > datas = getOrganisations();
				OrganisationDetail detail = datas.get( _cached_organisation );
				if( detail != null )
					detail.setExtra( value );
			}

			// same as when handling organisation, if we've not previously found
			// an organisation we store this title, else we clear it (since we
			// just appended this title to it)
			if( _cached_organisation == null )
				_cached_title = value;
			else
				_cached_organisation = null;
		}

		private void parseTEL( String[] params, String value )
		{
			if( value.length() == 0 ) return;

			Set< String > types = extractTypes( params, Arrays.asList(
				"PREF", "HOME", "WORK", "VOICE", "FAX", "MSG", "CELL",
				"PAGER", "BBS", "MODEM", "CAR", "ISDN", "VIDEO" ) );

			// here's the logic...
			boolean is_preferred = types.contains( "PREF" );
			int type;
			if( types.contains( "FAX" ) )
				if( types.contains( "HOME" ) )
					type = TYPE_FAX_HOME;
				else
					type = TYPE_FAX_WORK;
			else if( types.contains( "CELL" ) || types.contains( "VIDEO" ) )
				type = TYPE_MOBILE;
			else if( types.contains( "PAGER" ) )
				type = TYPE_PAGER;
			else if( types.contains( "WORK" ) )
				type = TYPE_WORK;
			else
				type = TYPE_HOME;

			// add phone number
			addNumber( value, type, is_preferred );
		}

		public void parseEMAIL( String[] params, String value )
		{
			if( value.length() == 0 ) return;

			Set< String > types = extractTypes( params, Arrays.asList(
				"PREF", "WORK", "HOME", "INTERNET" ) );

			// add email address
			boolean is_preferred = types.contains( "PREF" );
			int type;
			if( types.contains( "WORK" ) )
				type = TYPE_WORK;
			else
				type = TYPE_HOME;

			addEmail( unescapeValue( value ), type, is_preferred );
		}

		private void parseADR( String[] params, String value )
		{
			// get address parts
			String[] adr_parts = splitValueByCharacter( value, ';' );

			// build address
			value = "";
			for( int a = 0; a < adr_parts.length; a++ )
				if( adr_parts[ a ].length() > 0 )
				{
					// version 3.0 vCards allow further splitting by comma
					if( _version.equals( "3.0" ) )
					{
						// split this part in to it's comma-separated bits and
						// add them on individual lines
						String[] adr_part_parts =
							splitValueByCharacter( adr_parts[ a ], ',' );
						for( int b = 0; b < adr_part_parts.length; b++ )
							if( adr_part_parts[ b ].length() > 0 )
							{
								if( value.length() > 0 ) value += "\n";
								value += adr_part_parts[ b ];
							}
					}
					else
					{
						// add this part on an individual line
						if( value.length() > 0 ) value += "\n";
						value += adr_parts[ a ];
					}
				}

			Set< String > types = extractTypes( params, Arrays.asList(
				"PREF", "WORK", "HOME" ) );

			// add address
			int type;
			if( types.contains( "WORK" ) )
				type = TYPE_WORK;
			else
				type = TYPE_HOME;

			addAddress( unescapeValue( value ), type );
		}

		private void parseLABEL( String[] params, String value )
		{
			Set< String > types = extractTypes( params, Arrays.asList(
				"PREF", "WORK", "HOME" ) );

			// add address
			int type;
			if( types.contains( "WORK" ) )
				type = TYPE_WORK;
			else
				type = TYPE_HOME;

			addAddress( unescapeValue( value ), type );
		}

		private void parseNOTE( String[] params, String value )
		{
			addNote( unescapeValue( value ) );
		}

		private void parseBDAY( String[] params, String value )
		{
			setBirthday( value );
		}

		public void finaliseVcard()
			throws ParseException, ContactNotIdentifiableException,
				SkipImportException, AbortImportException
		{
			// if there was content present, but no version line, then it must
			// be a version 2.1 vCard; process that content now
			if( _version == null && _content_lines != null ) {
				_version = "2.1";
				for( int i = 0; i < _content_lines.size(); i++ )
					parseLine( _content_lines.get( i ) );
				_content_lines = null;
			}

			// finalise the parent class
			finalise();
		}

		/**
		 * Amongst the params, find the value of the first, only, of any with
		 * the specified name.
		 *
		 * @param params
		 * @param name
		 * @return a value, or null
		 */
		private String checkParam( String[] params, String name )
		{
			String[] res = checkParams( params, name );
			return res.length > 0? res[ 0 ] : null;
		}

		/**
		 * Amongst the params, find the values of any with the specified name.
		 *
		 * @param params
		 * @param name
		 * @return an array of values, or null
		 */
		private String[] checkParams( String[] params, String name )
		{
			HashSet< String > ret = new HashSet< String >();

			Pattern p = Pattern.compile(
				"^" + name + "[ \\t]*=[ \\t]*(\"?)(.*)\\1$",
				Pattern.CASE_INSENSITIVE );
			for( int i = 0; i < params.length; i++ ) {
				Matcher m = p.matcher( params[ i ] );
				if( m.matches() )
					ret.add( m.group( 2 ) );
			}

			return (String[]) ret.toArray( new String[ ret.size() ] );
		}

		/**
		 * Amongst the params, return any type values present.  For v2.1 vCards,
		 * those types are just parameters.  For v3.0, they are prefixed with
		 * "TYPE=".  There may also be multiple type parameters.
		 *
		 * @param params an array of params to look for types in
		 * @param valid_types an list of upper-case type values to look for
		 * @return a set of present type values
		 */
		private Set< String > extractTypes( String[] params,
			List< String > valid_types )
		{
			HashSet< String > types = new HashSet< String >();

			// get 3.0-style TYPE= param
			String type_params[] = checkParams( params, "TYPE" );
			for( int a = 0; a < type_params.length; a++ )
			{
				// check for a comma-separated list of types (why? I don't think
				// this is in the specs!)
				String[] parts = type_params[ a ].split( "," );
				for( int i = 0; i < parts.length; i++ ) {
					String ucpart = parts[ i ].toUpperCase( Locale.ENGLISH );
					if( valid_types.contains( ucpart ) )
						types.add( ucpart );
				}
			}

			// get 2.1-style type param
			if( _version.equals( "2.1" ) ) {
				for( int i = 1; i < params.length; i++ ) {
					String ucparam = params[ i ].toUpperCase( Locale.ENGLISH );
					if( valid_types.contains( ucparam ) )
						types.add( ucparam );
				}
			}

			return types;
		}

		private UnencodeResult unencodeQuotedPrintable( ByteBuffer in )
		{
			boolean another = false;

			// unencode quoted-printable encoding, as per RFC1521 section 5.1
			byte[] out = new byte[ in.limit() - in.position() ];
			int j = 0;
			for( int i = in.position(); i < in.limit(); i++ )
			{
				// get next char and process...
				byte ch = in.array()[ i ];
				if( ch == '=' && i < in.limit() - 2 )
				{
					// we found a =XX format byte, add it
					out[ j ] = (byte)(
							Character.digit( in.array()[ i + 1 ], 16 ) * 16 +
							Character.digit( in.array()[ i + 2 ], 16 ) );
					i += 2;
				}
				else if( ch == '=' && i == in.limit() - 1 )
				{
					// we found a '=' at the end of a line signifying a multi-
					// line string, so we don't add it
					another = true;
					continue;
				}
				else
					// just a normal char...
					out[ j ] = (byte)ch;
				j++;
			}

			return new UnencodeResult( another, ByteBuffer.wrap( out, 0, j ) );
		}

		private ByteBuffer transcodeAsciiToUtf8( ByteBuffer in )
		{
			// transcode
			byte[] out = new byte[ ( in.limit() - in.position() ) * 2 ];
			int j = 0;
			for( int a = in.position(); a < in.limit(); a++ )
			{
				// if char is < 127, keep it as-is
				if( in.array()[ a ] >= 0 )
					out[ j++ ] = in.array()[ a ];

				// else, convert it to UTF-8
				else {
					int b = 0xff & (int)in.array()[ a ];
					out[ j++ ] = (byte)( 0xc0 | ( b >> 6 ) );
					out[ j++ ] = (byte)( 0x80 | ( b & 0x3f ) );
				}
			}

			return ByteBuffer.wrap( out, 0, j );
		}
	}
}
