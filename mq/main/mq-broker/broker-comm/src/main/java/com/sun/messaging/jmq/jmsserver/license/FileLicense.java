/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 * @(#)FileLicense.java	1.5 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.license;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

import java.io.*;
import java.util.*;
import java.security.*;

/**
 * Represents a file based license.
 */
public class FileLicense extends LicenseBase {
    private File myLicenseFile = null;

    protected FileLicense() {
    }

    public FileLicense(File myLicenseFile, boolean autoChecking)
    	throws BrokerException {
    	this.myLicenseFile = myLicenseFile;
	setAutoChecking(autoChecking);
	readLicense();
    }

    public FileLicense(File myLicenseFile) throws BrokerException {
	this(myLicenseFile, true);
    }

    public File getLicenseFile() {
	return myLicenseFile;
    }

    //
    // Methods for reading and writing license files.
    //
    // License file format.
    //
    //   - Magic number (long)
    //   - 16 bytes MD5 checksum
    //   - Length of the scrambled license data
    //   - Scrampled license data.
    //
    // The license data (un-encrypted) uses the property file format.
    //

    /**
     * Write the encrypted license to the specified file.
     * @param file the file to write the license
     * @exception IOException if it fails to write the license
     */
    protected void writeLicense(File file)
        throws IOException, BrokerException {
        FileOutputStream fos = null;

        try {
            // check for the case where the trial license file is not
            // writable (fix for bug 4478642)
            fos = new FileOutputStream(file);
        } catch (IOException e) {
            if (file.exists() && !file.canWrite()) {
                throw new BrokerException(
                    br.getString(br.E_LICENSE_FILE_NOT_WRITABLE, file));
            } else {
                throw e;
            }
        }

        DataOutputStream dos = new DataOutputStream(fos);

        digest.reset();

        byte[] data = getEncryptedData();
        byte[] checksum = digest.digest(data);

        // Write the magic number
        dos.writeLong(LICENSE_MAGIC_NUMBER);

        // write 16 byte MD5 checkshum
        dos.write(checksum, 0, checksum.length);

        // write data
        // length of data in file
        dos.writeInt(data.length * 3);

        int cut = data.length/2;
        dos.write(data, 0, cut); // write first half
        dos.write(data, 0, data.length); // write all
        dos.write(data, 0, data.length); // write all again
        dos.write(data, cut, data.length - cut); // write second half

        dos.flush();
        dos.close();
        fos.close();
    }

    private byte[] getEncryptedData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.store(baos, null);
        byte[] buf = baos.toByteArray();
        baos.close();
        return scramble(buf);
    }


    /**
     * Rewrite the license to the file it is loaded from
     * if the dateString contains the "TRY[n]" pattern.
     * Calculate the expiration date and rewrite the license file.
     * @exception IOException if it fails to write the license
     * @exception IllegalStateException if the License object was not
     *      instantiated by loading data from a file
     */
    public void rewriteLicense()
        throws IOException, BrokerException {
        String dateString = props.getProperty(PROP_DATE_STRING);
        if (dateString == null)
            return;

        if (dateString.startsWith(TRY_STRING)) {
            // calculate and set date range
            Calendar cal = Calendar.getInstance();
            setStartDate(cal.getTime());

            cal.add(cal.DATE, getDaysToTry());
            setExpirationDate(cal.getTime());

            dateString = generateDateString();
            props.setProperty(PROP_DATE_STRING, dateString);

            writeLicense(myLicenseFile);
        }
    }

    private String generateDateString() {
        String string;

        if (!willExpire()) {
            string = NONE_STRING;
        } else if (getStartDate() != null || getExpirationDate() != null) {
            String start = ((getStartDate() != null) ?
                String.valueOf(getStartDate().getTime()) : "");
            String end = ((getExpirationDate() != null) ?
                String.valueOf(getExpirationDate().getTime()) : "");

            string = formatString(VALID_STRING, start+DASH+end);
        } else {
            string = formatString(TRY_STRING,
                String.valueOf(getDaysToTry()));
        }
        return string;
    }

    private String formatString(String prefix, String content) {
        return prefix + OPEN_BRACKET + content + CLOSE_BRACKET;
    }

    private static final int CHECKSUM_LEN = 16;
    private static final int BLOW_FACTOR = 3;
    private static final int FAKE_FACTOR = 2;
    private static final int CUT_FACTOR = 2;

    /**
     * Loads a license file.
     */
    private void readLicense() throws BrokerException {
        try {
            FileInputStream fis = new FileInputStream(myLicenseFile);
            DataInputStream dis = new DataInputStream(fis);

            // Check the magic number.
            long magic = dis.readLong();
            if (magic != LICENSE_MAGIC_NUMBER)
                throw new BrokerException(
                    br.getString(br.E_BAD_LICENSE_DATA));

            // Checksum
            byte[] checksum = new byte[CHECKSUM_LEN];
            dis.readFully(checksum);

            // read data
            int fakesize = dis.readInt();
            int size = fakesize/BLOW_FACTOR;

            int cut = size/CUT_FACTOR;

            byte[] data = new byte[size];

            // read the first half
            dis.read(data, 0, cut);

            // skip middle section
            dis.skipBytes(size * FAKE_FACTOR);

            // read the second half
            dis.read(data, cut, size - cut);
            dis.close();
            fis.close();

            // check checksum
            digest.reset();
            byte[] computed = digest.digest(data);

            if (!goodChecksum(checksum, computed)) {
                throw new BrokerException(
                    br.getString(br.E_BAD_LICENSE_DATA));
            }

            // unscramble and parse data
            parseData(scramble(data));
        } catch (IOException e) {
            throw new BrokerException(br.getString(br.E_BAD_LICENSE_DATA), e);
        }
    }

    /**
     * Check whether the checksum are the same.
     */
    private boolean goodChecksum(byte[] b1, byte[] b2) {
        if (b1 == null || b2 == null)
            return false;

        if (b1.length != b2.length)
            return false;

        for (int i = 0; i < b1.length; i++) {
            if (b1[i] != b2[i])
                return false;
        }

        return true;
    }

    /**
     * Parse the byte stream for license data.
     * Throws exception if the format is not what we expected.
     * If check is true, will also throw exception if the license
     * has expired or if the license version does not equal to the
     * defined ValidLicenseVersion.
     */
    private void parseData(byte[] data) throws IOException, BrokerException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        Properties tmp = new Properties();
        tmp.load(bais);

	superimpose(tmp);
    }

    // used to compute a checksum of data to be written to file
    private static MessageDigest digest;

    private static int ROTORSZ = 256;
    private static int MASK = 0377;

    private static int t1[] = new int[ROTORSZ];
    private static int t2[] = new int[ROTORSZ];
    private static int t3[] = new int[ROTORSZ];

    static {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }

        // set up static table
        int i, k, ic, temp, j;
        int seed = 12345;
        int random;

        for (i = 0; i < ROTORSZ; i++) {
            t1[i] = i;
            t3[i] = 0;
        }

        for (i = 0; i < ROTORSZ; i++) {
            random = (5*seed) % 65521;
            k = ROTORSZ-1 - i;
            ic = (random & MASK) % (k + 1);
            random >>= 8;
            temp = t1[k];
            t1[k] = t1[ic];
            t1[ic] = temp;
            if(t3[k]!=0) continue;
            ic = (random&MASK) % k;
            while(t3[ic]!=0) ic = (ic+1) % k;
            t3[k] = ic;
            t3[ic] = k;
        }

        for (i = 0; i < ROTORSZ; i++) {
            t2[t1[i]&MASK] = i;
        }
    }

    public static byte[] scramble(byte[] data) {
        int n1 = 0;
        int n2 = 0;

        byte[] newdata = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            newdata[i] =
                (byte)(t2[(t3[(t1[(data[i]+n1)&MASK]+n2)&MASK]-n2)&MASK]-n1);
            n1++;
            if (n1 == ROTORSZ) {
                n1 = 0;
                n2++;
                if (n2 == ROTORSZ) {
                    n2 = 0;
                }
            }
        }

        return newdata;
    }
}

/*
 * EOF
 */
