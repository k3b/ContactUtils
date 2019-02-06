/*
 * ContactsContractContactAccessor.java
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

package de.k3b.android.contactlib;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.k3b.contactlib.ContactData;
import de.k3b.contactlib.IConatactsReader;
import de.k3b.contactlib.LibContactGlobal;

@TargetApi(5)
public class ConatactsReaderAndroid5Impl implements IConatactsReader
{
    private final ContentResolver contentResolver;
	Cursor _contactCursor = null;
    private Map<String,String> _groupId2Name = null;
    private Map<String, Long> _statistics = null;
    private Map<String,Long> _statisticsUnknown = null;

    public ConatactsReaderAndroid5Impl(ContentResolver contentResolver)
	{
		this.contentResolver = contentResolver;
	}

	@Override
	public int getNumContacts()
	{
		// get number of aggregate contacts
		Cursor cur = this.contentResolver.query(
			ContactsContract.Contacts.CONTENT_URI,
			new String[] {
				ContactsContract.Contacts._ID,
			}, null, null, null );
		int ret = cur.getCount();
		cur.close();
		return ret;
	}

	private int convertBackendTypeToType( Class< ? > cls, int type )
	{
		if( cls == CommonDataKinds.Phone.class )
		{
			switch( type )
			{
			case CommonDataKinds.Phone.TYPE_MOBILE:
				return ContactData.TYPE_MOBILE;
			case CommonDataKinds.Phone.TYPE_FAX_HOME:
				return ContactData.TYPE_FAX_HOME;
			case CommonDataKinds.Phone.TYPE_FAX_WORK:
				return ContactData.TYPE_FAX_WORK;
			case CommonDataKinds.Phone.TYPE_PAGER:
				return ContactData.TYPE_PAGER;
			case CommonDataKinds.Phone.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == CommonDataKinds.Email.class )
		{
			switch( type )
			{
			case CommonDataKinds.Email.TYPE_WORK:
				return ContactData.TYPE_WORK;
			default:
				return ContactData.TYPE_HOME;
			}
		}
		else if( cls == CommonDataKinds.StructuredPostal.class )
		{
			switch( type )
			{
			case CommonDataKinds.StructuredPostal.TYPE_WORK:
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
        if( _contactCursor == null )
		{
		    this._groupId2Name = getGroups();
            if (LibContactGlobal.debugEnabled) {

                this._statistics = new HashMap<String, Long>();
                _statisticsUnknown = new HashMap<String, Long>();
            }
			// get all aggregate contacts
			_contactCursor = this.contentResolver.query(
				ContactsContract.Contacts.CONTENT_URI,
				new String[] {
					ContactsContract.Contacts._ID,
					ContactsContract.Contacts.DISPLAY_NAME,
				}, null, null, null );
		}

		// if there are no more aggregate contacts, abort
		if( _contactCursor == null ) return false;
		if( !_contactCursor.moveToNext() ) {
			_contactCursor.close();
			_contactCursor = null;
			return false;
		}

		// get this aggregate contact's id
		Long id = _contactCursor.getLong( _contactCursor.getColumnIndex(
			ContactsContract.Contacts._ID ) );

		// create contact
		contact.setName(getString(_contactCursor, ContactsContract.Contacts.DISPLAY_NAME));
		contact.setId(id);

		// get all contact data pertaining to the aggregate contact
		Cursor detailCursor = getDetailQuery(id);
		while( detailCursor.moveToNext() )
		{
			String type = getString(detailCursor, ContactsContract.Data.MIMETYPE);
            count(this._statistics, type);

            boolean isPrimary = (0  != getInt(detailCursor, ContactsContract.Data.IS_PRIMARY));
			// add phone numbers
			if( type.equals( CommonDataKinds.Phone.CONTENT_ITEM_TYPE ) )
				addPhone(contact, detailCursor, isPrimary);

			// add email addresses
			else if( type.equals( CommonDataKinds.Email.CONTENT_ITEM_TYPE ) )
				addEmail(contact, detailCursor, isPrimary);

			// add postal addresses
			else if( type.equals( CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ) )
				addAddress(contact, detailCursor);

			// add organisations/titles
			else if( type.equals( CommonDataKinds.Organization.CONTENT_ITEM_TYPE ) )
				addOrganization(contact, detailCursor, isPrimary);

                // add organisations/titles
            else if( type.equals( CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE ) ) {
                addGroup(contact, detailCursor);
                showCursor(contact, detailCursor);
            }

            // add notes
			else if( type.equals( CommonDataKinds.Note.CONTENT_ITEM_TYPE ) )
				addNote(contact, detailCursor);

			// add birthday
			else if( type.equals( CommonDataKinds.Event.CONTENT_ITEM_TYPE ) ) {
                addEvent(contact, detailCursor);
			} else  {
                count(this._statisticsUnknown, type);
                showCursor(contact, detailCursor);

            }
		}
		detailCursor.close();

		return true;
	}

    private void addGroup(ContactData contact, Cursor detailCursor) {
        final String id = getString(detailCursor, CommonDataKinds.GroupMembership.GROUP_ROW_ID);
        contact.addGroup(this._groupId2Name.get(id));
    }

    private void addEvent(ContactData contact, Cursor detailCursor) {
        int event = getInt(detailCursor, CommonDataKinds.Event.TYPE);
        if( event == CommonDataKinds.Event.TYPE_BIRTHDAY )
            contact.setBirthday(getString(detailCursor, CommonDataKinds.Event.START_DATE));
    }

    private void addNote(ContactData contact, Cursor detailCursor) {
        contact.addNote(getString(detailCursor, CommonDataKinds.Note.NOTE));
    }

    private void addOrganization(ContactData contact, Cursor detailCursor, boolean isPrimary) {
        final String company = getString(detailCursor, CommonDataKinds.Organization.COMPANY);
        final String title = getString(detailCursor, CommonDataKinds.Organization.TITLE);
        contact.addOrganisation(company,title,isPrimary);
    }

    private void addAddress(ContactData contact, Cursor detailCursor) {
        final int type = convertBackendTypeToType(CommonDataKinds.StructuredPostal.class,
                getInt(detailCursor, CommonDataKinds.StructuredPostal.TYPE));
        final String address = getString(detailCursor, CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
        contact.addAddress( address, type);
    }

    private void addEmail(ContactData contact, Cursor detailCursor, boolean isPrimary) {
        final String email = getString(detailCursor, CommonDataKinds.Email.DATA);
        final int type = convertBackendTypeToType(CommonDataKinds.Email.class,
                getInt(detailCursor, CommonDataKinds.Email.TYPE));
        contact.addEmail( email,type,isPrimary);
    }

    private void addPhone(ContactData contact, Cursor detailCursor, boolean isPrimary) {
        final String number = getString(detailCursor, CommonDataKinds.Phone.NUMBER);
        final int type = convertBackendTypeToType(CommonDataKinds.Phone.class,
                getInt(detailCursor, CommonDataKinds.Phone.TYPE));
        contact.addNumber(number,type,isPrimary);
    }

    private String getString(Cursor detailCursor, String columnName) {
        return detailCursor.getString(detailCursor.getColumnIndex(
                columnName));
    }

    private int getInt(Cursor detailCursor, String colName) {
        return detailCursor.getInt( detailCursor.getColumnIndex(
                colName) );
    }

    private Cursor getDetailQuery(Long id) {
        return this.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            new String[]{
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.IS_PRIMARY,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4,
                ContactsContract.Data.DATA5,
            },
            ContactsContract.Data.CONTACT_ID + " = ? ",
            new String[] {
                String.valueOf( id )
            },
            ContactsContract.Data.IS_SUPER_PRIMARY + " DESC, " +
                ContactsContract.Data.RAW_CONTACT_ID + ", " +
                ContactsContract.Data.IS_PRIMARY + " DESC" );
    }

    private Cursor getDetailQueryOrg(Long id) {
        return this.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.Data.IS_PRIMARY,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA2,
                        ContactsContract.Data.DATA4,
                },
                ContactsContract.Data.CONTACT_ID + " = ? AND " +
// column DELETED not found!?
//				ContactsContract.Data.DELETED + " = 0 AND " +
                        ContactsContract.Data.MIMETYPE + " IN ( ?, ?, ?, ?, ?, ? ) ",
                new String[] {
                        String.valueOf( id ),
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                },
                ContactsContract.Data.IS_SUPER_PRIMARY + " DESC, " +
                        ContactsContract.Data.RAW_CONTACT_ID + ", " +
                        ContactsContract.Data.IS_PRIMARY + " DESC" );
    }

    private void count(Map<String, Long> statistics, String type) {
        if (statistics != null) {
            Long count = statistics.get(type);
            if (count == null) count = 0l;
            statistics.put(type, count + 1l);
        }
    }

    private Map<String,String> getGroups() {
        Map<String, String> result = new HashMap<String, String>();

        Cursor c = null;
        try {
            final String[] GROUP_PROJECTION = new String[]{
                    ContactsContract.Groups._ID,
                    ContactsContract.Groups.TITLE
            };

            c = this.contentResolver.query(
                    ContactsContract.Groups.CONTENT_SUMMARY_URI,
                    GROUP_PROJECTION,
                    ContactsContract.Groups.DELETED + "!='1' AND " +
                            ContactsContract.Groups.GROUP_VISIBLE + "!='0' "
                    ,
                    null,
                    null);
            final int IDX_ID = c.getColumnIndex(ContactsContract.Groups._ID);
            final int IDX_TITLE = c.getColumnIndex(ContactsContract.Groups.TITLE);

            while (c.moveToNext()) {
                result.put(c.getString(IDX_ID), c.getString(IDX_TITLE));
            }
        } catch (Exception ignor) {
            ignor.printStackTrace();
        } finally {
            c.close();
        }
        showStatistics("Groups", result);
        return result;
    }

    @Override
	public void close() throws IOException {
		if (_contactCursor !=  null)  _contactCursor.close();
		_contactCursor = null;

        showStatistics("details" , this._statistics);
        showStatistics("unknown" , this._statisticsUnknown);
	}

    private void showCursor(ContactData detils, Cursor cur) {
        showCursor(detils.toString(), cur,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4,
                ContactsContract.Data.DATA5);
    }

    private void showCursor(String detils, Cursor cur, String... fields) {
        if (LibContactGlobal.debugEnabled) {
            StringBuilder result = new StringBuilder();
            result.append(detils);
            for (String k : fields) {
                result.append("\n\t").append(k).append(":").append(getString(cur, k));
            }
            Log.i(LibContactGlobal.TAG, result.toString());
        }
    }

    private <T> void showStatistics(String detils, Map<String,T> statistics) {
        if (LibContactGlobal.debugEnabled) {
            StringBuilder result = new StringBuilder();
            result.append(detils);
            String[] keys = statistics.keySet().toArray(new String[statistics.size()]);
            Arrays.sort(keys);
            for (String k : keys) {
                result.append("\n\t").append(k).append(":").append(statistics.get(k));
            }
            Log.i(LibContactGlobal.TAG, result.toString());
        }
    }
}
