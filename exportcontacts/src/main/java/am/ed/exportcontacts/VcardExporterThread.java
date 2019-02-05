/*
 * ExporterThread.java
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

package am.ed.exportcontacts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.SharedPreferences;
import de.k3b.contactlib.VcardWriter;

import de.k3b.contactlib.ContactData;

public class VcardExporterThread extends ExporterThread {
	protected VcardWriter vcardWriter = null;

	public VcardExporterThread(DoExportActivity doExportActivity) {
		super(doExportActivity);
	}

	@Override
	protected void preExport() throws AbortExportException {
		SharedPreferences prefs = getSharedPreferences();

		// create output filename
		File file = new File(ConfigureVCFExportActivity.getSdCardPathPrefix() +
				prefs.getString("path", "/") +
				prefs.getString("filename", "android-contacts.vcf"));

		// check if the output file already exists
		if (file.exists() && file.length() > 0)
			showContinueOrAbort(R.string.error_vcf_exists);

		// open file
		try {
			vcardWriter = new VcardWriter(new FileOutputStream(file));
		} catch (FileNotFoundException e) {
			showError(getText(R.string.error_filenotfound) +
					file.getPath());
		}

	}

	@Override
	protected boolean exportContact(ContactData contact)
			throws ExporterThread.AbortExportException {
		try {
			final boolean success = vcardWriter.exportContact(contact);
			if (!success) {
				showContinueOrAbort( R.string.error_vcf_noname );
			}
			return success;

		} catch (IOException ex) {
			showError( R.string.error_ioerror );
		}
		return false;
	}
}