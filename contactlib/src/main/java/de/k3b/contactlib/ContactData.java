package de.k3b.contactlib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final static Set< Integer > non_voice_types = new HashSet< Integer >(
            Arrays.asList( TYPE_FAX_HOME, TYPE_FAX_WORK, TYPE_PAGER ) );
    private List<String> _groups = new ArrayList<String>();
    private long _id = 0;


    private class TypeDetail {
        private final String text;
        protected int _type;

        public TypeDetail(String text, int type)
        {
            this.text = text;
            _type = type;
        }

        public int getType()
        {
            return _type;
        }

        public String getText() {
            return text;
        }
    }
    private class PreferredDetail extends TypeDetail {
        protected boolean _is_preferred;

        public PreferredDetail(String text, int type, boolean is_preferred)
        {
            super(text, type );
            _is_preferred = is_preferred;
        }

        public boolean isPreferred()
        {
            return _is_preferred;
        }
    }

    public class AddressDetail extends TypeDetail {

        public AddressDetail(String address, int type) {
            super(address, type);
        }
    }

    public class EmailDetail extends PreferredDetail {

        public EmailDetail(String email, int type, boolean is_preferred) {
            super(email, type, is_preferred);
        }
    }
    public class NumberDetail extends PreferredDetail{

        public NumberDetail(String number, int type, boolean is_preferred) {
            super(number, type, is_preferred);
        }
    }

    public class OrganisationDetail  extends PreferredDetail {
        private final String title;
        protected String _extra;
        public OrganisationDetail(String organisation, String title, int type, boolean is_preferred, String extra) {
            super(organisation, type, is_preferred );
            this.title = title;

            if( extra != null ) extra = extra.trim();
            _extra = extra;
        }

        public String getExtra()
        {
            return _extra;
        }

        public void setExtra( String extra )
        {
            if( extra != null ) extra = extra.trim();
            _extra = extra;
        }

        public String getTitle() {
            return title;
        }
    }
    @SuppressWarnings("serial")
    public class ContactNotIdentifiableException extends Exception {}

    protected String _name = null;
    protected String _primary_organisation = null;
    protected boolean _primary_organisation_is_preferred;
    protected String _primary_number = null;
    protected int _primary_number_type;
    protected boolean _primary_number_is_preferred;
    protected String _primary_email = null;
    protected boolean _primary_email_is_preferred;
    protected Map< String, OrganisationDetail > _organisations = new HashMap< String, OrganisationDetail>();
    protected Map< String, NumberDetail> _numbers = new HashMap< String, NumberDetail>();
    protected Map< String, EmailDetail> _emails = new HashMap< String, EmailDetail>();
    protected Map< String, AddressDetail> _addresses = new HashMap< String, AddressDetail>();
    protected HashSet< String > _notes = new HashSet<String>();
    protected String _birthday = null;

    private ContactsCache.CacheIdentifier _cache_identifier = null;

    public void setId( long id )
    {
        _id = id;
    }

    public long getId()
    {
        return _id;
    }

    public void setName( String name )
    {
        _name = name;
    }

    public boolean hasName()
    {
        return _name != null;
    }

    public String getName()
    {
        return _name;
    }

    /**
     *
     * @return fist  of name, fist-organisatio, first-number, first-email
     */
    public String getPrimaryIdentifier()
    {
        if( _name != null )
            return _name;

        if( this._primary_organisation != null  )
            return _primary_organisation;

        if( _primary_number != null )
            return _primary_number;

        if( _primary_email != null )
            return _primary_email;

        // no primary identifier
        return null;
    }

    public void addOrganisation(String organisation, String title,
                                boolean is_preferred)
    {
        organisation = organisation.trim();
        if( organisation.length() <= 0 )
        {
            // TODO: warn that an imported organisation is being ignored
            return;
        }

        if( title != null ) {
            title = title.trim();
            if( title.length() <= 0 ) title = null;
        }

        // add the organisation, as non-preferred (we prefer only one
        // organisation in finalise() after they're all imported)
        if( !_organisations.containsKey( organisation ) )
            _organisations.put( organisation,
                new OrganisationDetail( organisation, "", 0, false, title ) );

        // if this is the first organisation added, or it's a preferred
        // organisation and the current primary organisation isn't, then
        // record this as the primary organisation
        if( _primary_organisation == null ||
            ( is_preferred && !_primary_organisation_is_preferred ) )
        {
            _primary_organisation = organisation;
            _primary_organisation_is_preferred = is_preferred;
        }
    }

    public boolean hasOrganisations()
    {
        return _organisations != null && _organisations.size() > 0;
    }

    public Map< String, OrganisationDetail> getOrganisations()
    {
        return _organisations;
    }

    public boolean hasPrimaryOrganisation()
    {
        return _primary_organisation != null;
    }

    public String getPrimaryOrganisation()
    {
        return _primary_organisation;
    }

    public void addNumber(String number, int type,
                          boolean is_preferred)
    {
        number = sanitisePhoneNumber( number );
        if( number == null )
        {
            // TODO: warn that an imported phone number is being ignored
            return;
        }

        // add the number, as non-preferred (we prefer only one number
        // in finalise() after they're all imported)
        if( !_numbers.containsKey( number ) )
            _numbers.put( number,
                new NumberDetail(number, type, false ) );

        // if this is the first number added, or it's a preferred number
        // and the current primary number isn't, or this number is on equal
        // standing with the primary number in terms of preference and it is
        // a voice number and the primary number isn't, then record this as
        // the primary number
        if( _primary_number == null ||
            ( is_preferred && !_primary_number_is_preferred ) ||
            ( is_preferred == _primary_number_is_preferred &&
                !non_voice_types.contains( type ) &&
                non_voice_types.contains( _primary_number_type ) ) )
        {
            _primary_number = number;
            _primary_number_type = type;
            _primary_number_is_preferred = is_preferred;
        }
    }

    public boolean hasNumbers()
    {
        return _numbers != null && _numbers.size() > 0;
    }

    public Map< String, NumberDetail> getNumbers()
    {
        return _numbers;
    }

    public boolean hasPrimaryNumber()
    {
        return _primary_number != null;
    }

    public String getPrimaryNumber()
    {
        return _primary_number;
    }

    public void addEmail(String email, int type, boolean is_preferred)
    {

        email = sanitisesEmailAddress( email );
        if( email == null )
        {
            // TODO: warn that an imported email address is being ignored
            return;
        }

        // add the email, as non-preferred (we prefer only one email in
        // finalise() after they're all imported)
        if( !_emails.containsKey( email ) )
            _emails.put( email, new EmailDetail(email, type, false ) );

        // if this is the first email added, or it's a preferred email and
        // the current primary organisation isn't, then record this as the
        // primary email
        if( _primary_email == null ||
            ( is_preferred && !_primary_email_is_preferred ) )
        {
            _primary_email = email;
            _primary_email_is_preferred = is_preferred;
        }
    }

    public boolean hasEmails()
    {
        return _emails != null && _emails.size() > 0;
    }

    public Map< String, EmailDetail> getEmails()
    {
        return _emails;
    }

    public boolean hasPrimaryEmail()
    {
        return _primary_email != null;
    }

    public String getPrimaryEmail()
    {
        return _primary_email;
    }

    public void addAddress(String address, int type)
    {
        address = address.trim();
        if( address.length() <= 0 )
        {
            // TODO: warn that an imported address is being ignored
            return;
        }

        if( !_addresses.containsKey( address ) )
            _addresses.put( address, new AddressDetail( address, type ) );
    }

    public boolean hasAddresses()
    {
        return _addresses != null && _addresses.size() > 0;
    }

    public Map< String, AddressDetail> getAddresses()
    {
        return _addresses;
    }

    public void addNote(String note)
    {
        if( !_notes.contains( note ) )
            _notes.add( note );
    }

    public boolean hasNotes()
    {
        return _notes != null && _notes.size() > 0;
    }

    public HashSet< String > getNotes()
    {
        return _notes;
    }


    public void addGroup( String group ) {
        if (group != null) {
            _groups.add(group);
        }
    }

    public List< String > getGroups()
    {
        return _groups;
    }


    public void setBirthday( String birthday )
    {
        _birthday = birthday;
    }

    public boolean hasBirthday()
    {
        return _birthday != null;
    }

    public String getBirthday()
    {
        return _birthday;
    }

    protected void finalise()
        throws ContactNotIdentifiableException
    {
        // Ensure that if there is a primary number, it is preferred so
        // that there is always one preferred number.  Android will assign
        // preference to one anyway so we might as well decide one sensibly.
        if( _primary_number != null ) {
            NumberDetail data = _numbers.get( _primary_number );
            _numbers.put( _primary_number,
                new NumberDetail( _primary_number, data.getType(), true ) );
        }

        // do the same for the primary email
        if( _primary_email != null ) {
            EmailDetail data = _emails.get( _primary_email );
            _emails.put( _primary_email,
                new EmailDetail(_primary_email, data.getType(), true ) );
        }

        // do the same for the primary organisation
        if( _primary_organisation != null ) {
            OrganisationDetail data = _organisations.get( _primary_organisation );
            _organisations.put( _primary_organisation,
                new OrganisationDetail( _primary_organisation, "", 0, true, data.getExtra() ) );
        }

        // create a cache identifier from this contact data, which can be
        // used to look-up an existing contact
        _cache_identifier = ContactsCache.CacheIdentifier.factory( this );
        if( _cache_identifier == null )
            throw new ContactNotIdentifiableException();
    }

    public ContactsCache.CacheIdentifier getCacheIdentifier()
    {
        return _cache_identifier;
    }

    private String sanitisePhoneNumber( String number )
    {
        number = number.trim();
        Pattern p = Pattern.compile( "^[-\\(\\) \\+0-9#*]+" );
        Matcher m = p.matcher( number );
        if( m.lookingAt() ) return m.group( 0 );
        return null;
    }

    private String sanitisesEmailAddress( String email )
    {
        email = email.trim();
        Pattern p = Pattern.compile(
            "^[^ @]+@[a-zA-Z]([-a-zA-Z0-9]*[a-zA-z0-9])?(\\.[a-zA-Z]([-a-zA-Z0-9]*[a-zA-z0-9])?)+$" );
        Matcher m = p.matcher( email );
        if( m.matches() ) {
            String[] bits = email.split( "@" );
            return bits[ 0 ] + "@" +
                bits[ 1 ].toLowerCase( Locale.ENGLISH );
        }
        return null;
    }
}
