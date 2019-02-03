package android.k3b.de.androidcontactlibrary.de.k3b.android.contactlib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Contacts;

public class AndroidContactReader {
    private final ContentResolver contentResolver;
    public AndroidContactReader(ContentResolver contentResolver) {

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

}
