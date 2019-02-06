/*
 * Backend.java
 *
 * Copyright (C) 2012 to 2013 Tim Marston <tim@ed.am>
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

import de.k3b.contactlib.ContactData;
import de.k3b.contactlib.ContactsCache;

public interface Backend
{
	/**
	 * Build-up our contacts cache, using contacts on the device.
	 *
	 * @param cache the contacts cache to populate
	 */
	public void populateCache( ContactsCache cache );

	/**
	 * Delete a contact from the device.
	 *
	 * @param id of the contact to delete
	 */
	public void deleteContact( Long id );

	@SuppressWarnings("serial")
	public class ContactCreationException extends Exception { };

	/**
	 * Add a contact to the device.
	 *
	 * @param name name of the new contact, or null if there isn't one
	 * @return the new contact's id
	 * @throws ContactCreationException
	 */
	public Long addContact( String name ) throws ContactCreationException;

	/**
	 * Add a phone number to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param number the phone number
	 * @param data data about the number
	 * @throws ContactCreationException
	 */
	public void addContactPhone( Long id, String number,
		ContactData.NumberDetail data ) throws ContactCreationException;

	/**
	 * Add an email address to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param email the email address
	 * @param data data about the email address
	 * @throws ContactCreationException
	 */
	public void addContactEmail( Long id, String email,
		ContactData.EmailDetail data ) throws ContactCreationException;

	/**
	 * Add an address to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param address the address
	 * @param data data about the address
	 * @throws ContactCreationException
	 */
	public void addContactAddresses( Long id, String address,
		ContactData.AddressDetail data ) throws ContactCreationException;

	/**
	 * Add a title and organisation to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param organisation the organisation
	 * @param data data about the organisation
	 * @throws ContactCreationException
	 */
	public void addContactOrganisation( Long id, String organisation,
		ContactData.OrganisationDetail data ) throws ContactCreationException;

	/**
	 * Add a note to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param note the note
	 * @throws ContactCreationException
	 */
	public void addContactNote( Long id, String note )
		throws ContactCreationException;

	/**
	 * Add a birthday to an existing contact on the device.
	 *
	 * @param id the existing contact's id
	 * @param birthday the birthday
	 * @throws ContactCreationException
	 */
	public void addContactBirthday( Long id, String birthday )
		throws ContactCreationException;
}
