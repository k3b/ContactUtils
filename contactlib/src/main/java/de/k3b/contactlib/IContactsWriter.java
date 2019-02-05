package de.k3b.contactlib;

import java.io.Closeable;
import java.io.IOException;

interface IContactsWriter extends Closeable {
    boolean exportContact(ContactData contact)
            throws IOException;
}
