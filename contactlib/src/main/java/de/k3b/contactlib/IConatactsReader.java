/*
 * IConatactsReader.java
 *
 * Copyright (C) 2011 Tim Marston <tim@ed.am>
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

package de.k3b.contactlib;

import de.k3b.contactlib.ContactData;

public interface IConatactsReader
{
	/**
	 * Return the number of contacts that exist and that will be exported.
	 *
	 * @return number of existing contacts
	 */
	public int getNumContacts();

	/**
	 * Return the next contact.
	 *
	 * @return a ContactData
	 */
	public boolean getNextContact( ContactData contact );

}
