/*
 * ContactsContactAccessor.java
 *
 * Copyright (C) 2011 to 2012 Tim Marston <tim@ed.am>
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

package de.k3b.android.contactlib;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Contacts;

import java.io.IOException;

import de.k3b.contactlib.ContactData;
import de.k3b.contactlib.IConatactsReader;

@SuppressWarnings( "deprecation" )
@TargetApi(3)
public class ConatactsReaderAndroid3Impl implements IConatactsReader
{
	ContentResolver contentResolver = null;
	Cursor _cur = null;

	public ConatactsReaderAndroid3Impl(ContentResolver contentResolver)	{
		this.contentResolver = contentResolver;
	}

    public int getNumContacts()
    {
        Cursor cursor = this.contentResolver.query(
                Contacts.People.CONTENT_URI,
                new String[] {
                        Contacts.People._ID,
                }, null, null, null );

        final int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private int convertBackendTypeToType( Class< ? > cls, int type )
	{
		if( cls == Contacts.Phones.class )
		{
			switch( type )
			{
			case Contacts.PhonesColumns.TYPE_MOBILE:
				return ContactData.TYPE_MOBILE;
			case Contacts.PhonesColumns.TYPE_FAX_HOME:
				return ContactData.TYPE_FAX_HOME;
			case Contacts.PhonesColumns.TYPE_FAX_WORK:
				return ContactData.TYPE_FAX_WORK;
			case Contacts.PhonesColumns.TYPE_PAGER:
				return ContactData.TYPE_PAGER;
			case Contacts.PhonesColumns.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == Contacts.ContactMethods.class )
		{
			switch( type )
			{
			case Contacts.ContactMethodsColumns.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}

		return ContactData.TYPE_HOME;
	}

	@Override
	public boolean getNextContact( ContactData contact )
	{
		// set up cursor
		if( _cur == null )
		{
			// get all contacts
			_cur = contentResolver.query(
				Contacts.People.CONTENT_URI,
				new String[] {
					Contacts.People._ID,
					Contacts.People.NAME,
					Contacts.People.NOTES,
                        Contacts.People.ISPRIMARY,

                }, null, null, null );
		}

		// if there are no more contacts, abort
		if( _cur == null ) return false;
		if( !_cur.moveToNext() ) {
			_cur.close();
			_cur = null;
			return false;
		}

		// get this contact's id
		Long id = _cur.getLong( _cur.getColumnIndex( Contacts.People._ID ) );

		// set name
		contact.setName(
			_cur.getString( _cur.getColumnIndex( Contacts.People.NAME ) ) );

		// add notes
		String note = _cur.getString(
			_cur.getColumnIndex( Contacts.People.NOTES ) );
		if( note != null && note.length() > 0 )
			contact.addNote( note );

		// add the organisations
		Cursor cur = contentResolver.query(
			Contacts.Organizations.CONTENT_URI,
			new String[] {
				Contacts.Organizations.COMPANY,
				Contacts.Organizations.TITLE,
                Contacts.Organizations.ISPRIMARY,
			}, Contacts.Organizations.PERSON_ID + " = ?",
			new String[] { id.toString() },
			Contacts.Organizations.ISPRIMARY + " DESC, " +
				Contacts.Organizations.PERSON_ID + " ASC" );
		while( cur.moveToNext() ) {
            boolean isPrimary = getInt(cur,Contacts.Organizations.ISPRIMARY)  != 0;

            final String organisation = cur.getString(cur.getColumnIndex(
                    Contacts.Organizations.COMPANY));
            final String title = cur.getString(cur.getColumnIndex(
                    Contacts.Organizations.TITLE));
            contact.addOrganisation( organisation, title, isPrimary);
        }
		cur.close();

		// add the phone numbers
		cur = contentResolver.query(
			Contacts.Phones.CONTENT_URI,
			new String[] {
				Contacts.Phones.NUMBER,
				Contacts.Phones.TYPE,
                    Contacts.Phones.ISPRIMARY,

            }, Contacts.Phones.PERSON_ID + " = ?",
			new String[] { id.toString() },
			Contacts.Phones.ISPRIMARY + " DESC," +
				Contacts.Phones.PERSON_ID + " ASC" );
		while( cur.moveToNext() ) {
            boolean isPrimary = getInt(cur,Contacts.Phones.ISPRIMARY)  != 0;

            final int type = convertBackendTypeToType(Contacts.Phones.class,
                    cur.getInt(cur.getColumnIndex(Contacts.Phones.TYPE)));
            final String number = cur.getString(cur.getColumnIndex(
                    Contacts.Phones.NUMBER));

            contact.addNumber( number, type, isPrimary );
        }
		cur.close();

		// add the email and postal addresses
		cur = contentResolver.query(
			Contacts.ContactMethods.CONTENT_URI,
			new String[] {
				Contacts.ContactMethods.KIND,
				Contacts.ContactMethods.TYPE,
				Contacts.ContactMethods.DATA,
                Contacts.ContactMethods.ISPRIMARY,

            },
			Contacts.ContactMethods.PERSON_ID + " = ? AND " +
				Contacts.ContactMethods.KIND + " IN( ?, ? )",
			new String[] {
				id.toString(),
				"" + Contacts.KIND_EMAIL,
				"" + Contacts.KIND_POSTAL,
			},
			Contacts.ContactMethods.ISPRIMARY + " DESC," +
				Contacts.ContactMethods.PERSON_ID + " ASC" );
		while( cur.moveToNext() ) {
			int kind = cur.getInt( cur.getColumnIndex(
				Contacts.ContactMethods.KIND ) );
			boolean isPrimary = getInt(cur,Contacts.ContactMethods.ISPRIMARY)  != 0;


			if( kind == Contacts.KIND_EMAIL ) {
                final int type = convertBackendTypeToType(Contacts.ContactMethods.class,
                        getInt(cur, Contacts.ContactMethods.TYPE));
                final String email = getString(cur, Contacts.ContactMethods.DATA);
                contact.addEmail( email, type,isPrimary);
            }
			else {
                final int type = convertBackendTypeToType(Contacts.ContactMethods.class,
                        cur.getInt(cur.getColumnIndex(
                                Contacts.ContactMethods.TYPE)));
                final String adresse = cur.getString(cur.getColumnIndex(
                        Contacts.ContactMethods.DATA));
                contact.addAddress( adresse, type);
            }
		}
		cur.close();

		return true;
	}

    private String getString(Cursor detailCursor, String columnName) {
        return detailCursor.getString(detailCursor.getColumnIndex(
                columnName));
    }

    private int getInt(Cursor detailCursor, String colName) {
        return detailCursor.getInt( detailCursor.getColumnIndex(
                colName) );
    }

    @Override
    public void close() throws IOException {
        if (_cur !=  null)  _cur.close();
        _cur = null;
    }
}
