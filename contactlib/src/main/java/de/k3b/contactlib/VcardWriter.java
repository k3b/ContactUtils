package de.k3b.contactlib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;

public class VcardWriter implements IContactsWriter {
    protected boolean _first_contact = true;
    private OutputStream _ostream;

    public VcardWriter(OutputStream ostream) {
        this._ostream = ostream;
    }
    /**
     * Do line folding at 75 chars
     * @param line string
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
     * @param str string
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
    protected static String join(AbstractCollection s, String delimiter)
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
            throws IOException
    {
        _ostream.write( data );
        _ostream.flush();
    }

    @Override
    public boolean exportContact(ContactData contact)
            throws IOException
    {
        // append formatted name
        String identifier = contact.getPrimaryIdentifier();
        if( identifier != null ) identifier = identifier.trim();
        if( identifier == null || identifier.length() == 0 ) {
            // skip if the contact has no identifiable features
            return false;
        }

        StringBuilder out = new StringBuilder();

        // append newline
        if( _first_contact )
            _first_contact = false;
        else
            out.append( "\n" );

        // append header
        out.append( "BEGIN:VCARD\n" );
        out.append( "VERSION:3.0\n" );

        out.append( fold( "FN:" + escape( identifier ) ) + "\n" );

        // append name
        appendName(out, contact.getName());

        // append organisations and titles
        appendOrganisationsAndTitles(out, contact.getOrganisations());

        // append phone numbers
        appendPhoneNumbers(out, contact.getNumbers());

        // append email addresses
        ArrayList< ContactData.EmailDetail > emails =
                contact.getEmails();
        appendEmailAddresses(out, emails);

        // append addresses
        appendAdresses(out, contact.getAddresses());

        // append notes
        appendLists(out, "NOTE:", contact.getNotes());

        if (LibContactGlobal.groupsEnabled) {
            appendLists(out, "X-GROUPS:", contact.getGroups());
        }

        // append birthday
        appendBirthday(out, contact.getBirthday());

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

    private void appendEmailAddresses(StringBuilder out, ArrayList<ContactData.EmailDetail> emails) {
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
    }

    private void appendPhoneNumbers(StringBuilder out, ArrayList<ContactData.NumberDetail> numbers) {
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
    }

    private void appendOrganisationsAndTitles(StringBuilder out, ArrayList<ContactData.OrganisationDetail> organisations) {
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
    }

    private void appendName(StringBuilder out, String name) {
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
    }

    private void appendBirthday(StringBuilder out, String birthday) {
        if( birthday != null ) {
            birthday.trim();
            if( isValidDateAndOrTime( birthday ) )
                out.append( fold( "BDAY:" + escape( birthday ) ) + "\n" );
            else
                out.append(
                        fold( "BDAY;VALUE=text:" + escape( birthday ) ) + "\n" );
        }
    }

    private void appendLists(StringBuilder out, final String tag,ArrayList<String> notes) {
        if( notes != null )
            for( int a = 0; a < notes.size(); a++ ) {
                out.append( fold( tag + escape( notes.get( a ) ) ) + "\n" );
            }
    }

    private void appendAdresses(StringBuilder out, ArrayList<ContactData.AddressDetail> addresses) {
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
    }

    @Override
    public void close() throws IOException {
        if (_ostream != null) _ostream.close();
        _ostream = null;
    }
}
