package de.k3b.contactlib;

import java.util.ArrayList;

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

    public class OrganisationDetail
    {
        protected String _org;
        protected String _title;

        public OrganisationDetail( String org, String title )
        {
            _org = getValueOrNull(org);
            _title = getValueOrNull(title);
        }

        public String getOrganisation()
        {
            return _org;
        }

        public String getTitle()
        {
            return _title;
        }
    }

    public class NumberDetail
    {
        protected int _type;
        protected String _num;

        public NumberDetail( int type, String num )
        {
            _type = type;
            _num = getValueOrNull(num);
        }

        public int getType()
        {
            return _type;
        }

        public String getNumber()
        {
            return _num;
        }
    }

    public class EmailDetail
    {
        protected int _type;
        protected String _email;

        public EmailDetail( int type, String email )
        {
            _type = type;
            _email = getValueOrNull(email);
        }

        public int getType()
        {
            return _type;
        }

        public String getEmail()
        {
            return _email;
        }
    }

    public class AddressDetail
    {
        protected int _type;
        protected String _addr;

        public AddressDetail( int type, String addr )
        {
            _type = type;
            _addr = getValueOrNull(addr);
        }

        public int getType()
        {
            return _type;
        }

        public String getAddress()
        {
            return _addr;
        }
    }

    protected long _id = 0;
    protected String _name = null;
    protected ArrayList< OrganisationDetail > _organisations = null;
    protected ArrayList<NumberDetail> _numbers = null;
    protected ArrayList<EmailDetail> _emails = null;
    protected ArrayList<AddressDetail> _addresses = null;
    protected ArrayList< String > _notes = null;
    protected ArrayList< String > _groups = null;

    protected String _birthday = null;

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
        _name = getValueOrNull(name);
    }

    public String getName()
    {
        return _name;
    }

    public void addOrganisation( OrganisationDetail organisation )
    {
        if( organisation.getOrganisation() == null ) return;
        if( _organisations == null )
            _organisations = new ArrayList<OrganisationDetail>();
        _organisations.add( organisation );
    }

    public ArrayList<OrganisationDetail> getOrganisations()
    {
        return _organisations;
    }

    public void addNumber( NumberDetail number )
    {
        if( number.getNumber() == null ) return;
        if( _numbers == null )
            _numbers = new ArrayList<NumberDetail>();
        _numbers.add( number );
    }

    public ArrayList<NumberDetail> getNumbers()
    {
        return _numbers;
    }

    public void addEmail( EmailDetail email )
    {
        if( email.getEmail() == null ) return;
        if( _emails == null )
            _emails = new ArrayList<EmailDetail>();
        _emails.add( email );
    }

    public ArrayList<EmailDetail> getEmails()
    {
        return _emails;
    }

    public void addAddress( AddressDetail address )
    {
        if( address.getAddress() == null ) return;
        if( _addresses == null )
            _addresses = new ArrayList<AddressDetail>();
        _addresses.add( address );
    }

    public ArrayList<AddressDetail> getAddresses()
    {
        return _addresses;
    }

    public void addNote( String note )
    {
        if( _notes == null )
            _notes = new ArrayList< String >();
        _notes.add( note );
    }

    public ArrayList< String > getNotes()
    {
        return _notes;
    }

    public void addGroup( String group ) {
        if (group != null) {
            if (_groups == null)
                _groups = new ArrayList<String>();
            _groups.add(group);
        }
    }

    public ArrayList< String > getGroups()
    {
        return _groups;
    }


    public void setBirthday( String birthday )
    {
        _birthday = birthday;
    }

    public String getBirthday()
    {
        return _birthday;
    }

    /**
     *
     * @return fist  of name, fist-organisatio, first-number, first-email
     */
    public String getPrimaryIdentifier()
    {
        if( _name != null )
            return _name;

        if( _organisations != null &&
            _organisations.get( 0 ).getOrganisation() != null )
            return _organisations.get( 0 ).getOrganisation();

        if( _numbers!= null &&
            _numbers.get( 0 ).getNumber() != null )
            return _numbers.get( 0 ).getNumber();

        if( _emails!= null &&
            _emails.get( 0 ).getEmail() != null )
            return _emails.get( 0 ).getEmail();

        // no primary identifier
        return null;
    }

    /**
     * @return null if value.len is 0
     */
    private static String getValueOrNull(String value) {
        return value != null && value.length() > 0? value : null;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()+"#" + getId() +
                ":"  + getPrimaryIdentifier();
    }
}
