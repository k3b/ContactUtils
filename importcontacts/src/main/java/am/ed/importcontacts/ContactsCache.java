/*
 * ContactsCache.java
 *
 * Copyright (C) 2011 to 2013 Tim Marston <tim@ed.am>
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class ContactsCache
{
	/**
	 * A thing that can be used to identify (or lookup) a contact within the
	 * contacts cache.  It is not a reference to a cache entry and may not
	 * identify an existing contact in the cache.
	 */
	public static class CacheIdentifier
	{
		public enum Type { NAME, ORGANISATION, PRIMARY_NUMBER, PRIMARY_EMAIL }

		private Type _type;
		private String _detail;

		/**
		 * Obtain a cache identifier.  This routine is designed to be as robust
		 * as possible (in terms of bad or null detail values), and to return
		 * null when a cache identifier can not be created.
		 *
		 * @param type the detail type
		 * @param detail the detail
		 * @return the cache identifier, or null
		 */
		public static CacheIdentifier factory( Type type, String detail )
		{
			switch( type )
			{
			case NAME: detail = normaliseName( detail ); break;
			case ORGANISATION: detail = normaliseOrganisation( detail ); break;
			case PRIMARY_NUMBER: detail = normalisePhoneNumber( detail ); break;
			case PRIMARY_EMAIL: detail = normaliseEmailAddress( detail ); break;
			default: return null;
			}
			if( detail == null ) return null;
			return new CacheIdentifier( type, detail );
		}

		/**
		 * Obtain a cache identifier from contact data.  This routine is
		 * designed to be as robust as possible and may return null when a cache
		 * identifier can not be created.
		 *
		 * @param contact the contact data
		 * @return the cache identifier, or null
		 */
		public static CacheIdentifier factory( Importer.ContactData contact )
		{
			CacheIdentifier identifier = null;

			if( contact.hasName() )
				identifier = factory( CacheIdentifier.Type.NAME,
					contact.getName() );
			if( identifier != null ) return identifier;

			if( contact.hasPrimaryOrganisation() )
				identifier = factory( CacheIdentifier.Type.ORGANISATION,
					contact.getPrimaryOrganisation() );
			if( identifier != null ) return identifier;

			if( contact.hasPrimaryNumber() )
				identifier = factory( CacheIdentifier.Type.PRIMARY_NUMBER,
					contact.getPrimaryNumber() );
			if( identifier != null ) return identifier;

			if( contact.hasPrimaryEmail() )
				identifier = factory( CacheIdentifier.Type.PRIMARY_EMAIL,
					contact.getPrimaryEmail() );
			if( identifier != null ) return identifier;

			return null;
		}

		protected CacheIdentifier( Type type, String detail )
		{
			_type = type;
			_detail = detail;
		}

		public Type getType()
		{
			return _type;
		}

		public String getDetail()
		{
			return _detail;
		}
	}

	// mappings of contact names, organisations and primary numbers to ids
	private HashMap< String, Long > _contactsByName
		= new HashMap< String, Long >();
	private HashMap< String, Long > _contactsByOrg
		= new HashMap< String, Long >();
	private HashMap< String, Long > _contactsByNumber
		= new HashMap< String, Long >();
	private HashMap< String, Long > _contactsByEmail
		= new HashMap< String, Long >();

	// mapping of contact ids to sets of associated data
	private HashMap< Long, HashSet< String > > _contactNumbers
		= new HashMap< Long, HashSet< String > >();
	private HashMap< Long, HashSet< String > > _contactEmails
		= new HashMap< Long, HashSet< String > >();
	private HashMap< Long, HashSet< String > > _contactAddresses
		= new HashMap< Long, HashSet< String > >();
	private HashMap< Long, HashSet< String > > _contactOrganisations
		= new HashMap< Long, HashSet< String > >();
	private HashMap< Long, HashSet< String > > _contactNotes
		= new HashMap< Long, HashSet< String > >();
	private HashMap< Long, String > _contactBirthdays
		= new HashMap< Long, String >();

	public boolean canLookup( CacheIdentifier identifier )
	{
		return lookup( identifier ) != null;
	}

	/**
	 * Retrieve the contact id of a contact identified by the specified cache
	 * identifier, if it exists.
	 *
	 * @param identifier the cache identifier
	 * @return a contact id, or null
	 */
	public Long lookup( CacheIdentifier identifier )
	{
		switch( identifier.getType() )
		{
		case NAME:
			return _contactsByName.get( identifier.getDetail() );
		case ORGANISATION:
			return _contactsByOrg.get( identifier.getDetail() );
		case PRIMARY_NUMBER:
			return _contactsByNumber.get( identifier.getDetail() );
		case PRIMARY_EMAIL:
			return _contactsByEmail.get( identifier.getDetail() );
		}
		return null;
	}

	/**
	 * Remove any cache entry that is identified by the cache identifier.
	 *
	 * @param identifier the cache identifier
	 * @return the contact id of the contact that was removed, or null
	 */
	public Long removeLookup( CacheIdentifier identifier )
	{
		switch( identifier.getType() )
		{
		case NAME:
			return _contactsByName.remove( identifier.getDetail() );
		case ORGANISATION:
			return _contactsByOrg.remove( identifier.getDetail() );
		case PRIMARY_NUMBER:
			return _contactsByNumber.remove( identifier.getDetail() );
		case PRIMARY_EMAIL:
			return _contactsByEmail.remove( identifier.getDetail() );
		}
		return null;
	}

	/**
	 * Add a lookup from a contact identifier to a contact id to the cache.
	 *
	 * @param identifier the cache identifier
	 * @param id teh contact id
	 */
	public void addLookup( CacheIdentifier identifier, Long id )
	{
		switch( identifier.getType() )
		{
		case NAME:
			_contactsByName.put( identifier.getDetail(), id );
			break;
		case ORGANISATION:
			_contactsByOrg.put( identifier.getDetail(), id );
			break;
		case PRIMARY_NUMBER:
			_contactsByNumber.put( identifier.getDetail(), id );
			break;
		case PRIMARY_EMAIL:
			_contactsByEmail.put( identifier.getDetail(), id );
			break;
		}
	}

	/**
	 * Remove any data that is associated with an contact id.
	 *
	 * @param id
	 */
	public void removeAssociatedData( Long id )
	{
		_contactNumbers.remove( id );
		_contactEmails.remove( id );
		_contactAddresses.remove( id );
		_contactOrganisations.remove( id );
		_contactNotes.remove( id );
	}

	public boolean hasAssociatedNumber( Long id, String number )
	{
		number = normalisePhoneNumber( number );
		if( number == null ) return false;

		HashSet< String > set = _contactNumbers.get( id );
		return set != null && set.contains( number );
	}

	public void addAssociatedNumber( Long id, String number )
	{
		number = normalisePhoneNumber( number );
		if( number == null ) return;

		HashSet< String > set = _contactNumbers.get( id );
		if( set == null ) {
			set = new HashSet< String >();
			_contactNumbers.put( id, set );
		}
		set.add( number );
	}

	public boolean hasAssociatedEmail( Long id, String email )
	{
		email = normaliseEmailAddress( email );
		if( email == null ) return false;

		HashSet< String > set = _contactEmails.get( id );
		return set != null && set.contains( email );
	}

	public void addAssociatedEmail( Long id, String email )
	{
		email = normaliseEmailAddress( email );
		if( email == null ) return;

		HashSet< String > set = _contactEmails.get( id );
		if( set == null ) {
			set = new HashSet< String >();
			_contactEmails.put( id, set );
		}
		set.add( email );
	}

	public boolean hasAssociatedAddress( Long id, String address )
	{
		address = normaliseAddress( address );
		if( address == null ) return false;

		HashSet< String > set = _contactAddresses.get( id );
		return set != null && set.contains( address );
	}

	public void addAssociatedAddress( Long id, String address )
	{
		address = normaliseAddress( address );
		if( address == null ) return;

		HashSet< String > set = _contactAddresses.get( id );
		if( set == null ) {
			set = new HashSet< String >();
			_contactAddresses.put( id, set );
		}
		set.add( address );
	}

	public boolean hasAssociatedOrganisation( Long id, String organisation )
	{
		organisation = normaliseOrganisation( organisation );
		if( organisation == null ) return false;

		HashSet< String > set = _contactOrganisations.get( id );
		return set != null && set.contains( organisation );
	}

	public void addAssociatedOrganisation( Long id, String organisation )
	{
		organisation = normaliseOrganisation( organisation );
		if( organisation == null ) return;

		HashSet< String > set = _contactOrganisations.get( id );
		if( set == null ) {
			set = new HashSet< String >();
			_contactOrganisations.put( id, set );
		}
		set.add( organisation );
	}

	public boolean hasAssociatedNote( Long id, String note )
	{
		note = normaliseNote( note );
		if( note == null ) return false;

		HashSet< String > set = _contactNotes.get( id );
		return set != null && set.contains( note );
	}

	public void addAssociatedNote( Long id, String note )
	{
		note = normaliseNote( note );
		if( note == null ) return;

		HashSet< String > set = _contactNotes.get( id );
		if( set == null ) {
			set = new HashSet< String >();
			_contactNotes.put( id, set );
		}
		set.add( note );
	}

	public boolean hasAssociatedBirthday( Long id, String birthday )
	{
		birthday = normaliseBirthday( birthday );
		if( birthday == null ) return false;

		String found = _contactBirthdays.get( id );
		return found != null && found.equalsIgnoreCase( birthday );
	}

	public void addAssociatedBirthday( Long id, String birthday )
	{
		birthday = normaliseBirthday( birthday );
		if( birthday == null ) return;

		_contactBirthdays.put( id, birthday );
	}

	static public String normaliseName( String name )
	{
		if( name == null ) return null;
		name = name.trim();
		return name.length() > 0? name : null;
	}

	static public String normalisePhoneNumber( String number )
	{
		if( number == null ) return null;
		number = number.trim().replaceAll( "[-\\(\\) ]", "" );
		return number.length() > 0? number : null;
	}

	static public String normaliseEmailAddress( String email )
	{
		if( email == null ) return null;
		email = email.trim().toLowerCase( Locale.ENGLISH );
		return email.length() > 0? email : null;
	}

	static public String normaliseOrganisation( String organisation )
	{
		if( organisation == null ) return null;
		organisation = organisation.trim();
		return organisation.length() > 0? organisation : null;
	}

	static public String normaliseAddress( String address )
	{
		if( address == null ) return null;
		address = address.trim();
		return address.length() > 0? address : null;
	}

	static public String normaliseNote( String note )
	{
		if( note == null ) return null;
		note = note.trim();
		return note.length() > 0? note : null;
	}

	static public String normaliseBirthday( String birthday )
	{
		if( birthday == null ) return null;
		birthday = birthday.trim();
		return birthday.length() > 0? birthday : null;
	}
}
