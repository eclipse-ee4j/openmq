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
 * @(#)RollingFileOutputStream.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.util.log;

import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Date;

import com.sun.messaging.jmq.resources.SharedResources;

/**
 * An output stream for writing bytes to a file in a "rolling" 
 * fasion.  The filename and rollover point are configurable.
 * Rolling files work by checking if the rollover point
 * (size of file in bytes or time since creation) has been exceeded before
 * every write.  If the rollover point is exceeded, the file is closed,
 * renamed to the filename followed by a "_1" (with all previous files
 * "shifted down" by one), and a new file is created.  If the "shift down"
 * causes the maximum number of saved files to be exceeded, the oldest
 * file is deleted.
 *
 * Part of this code was taken from Java Web Server
 */
public class RollingFileOutputStream extends OutputStream {

    /**
     * Size of file. We set this when we initially open the file
     * and increment it on every write. This way we don't have to keep
     * going to the File object to get the file length.
     */ 
    long file_size = 0L;

    File outfile;

    /** 
     * Allowed file size before rollover. 0 to never rollover based on size.
     */
    long rollover_bytes = 0;

    /** 
     * Allowed age of file before rollover. 0 to never rollover based on time.
     */
    long rollover_ms = 0;

    /**
     * Time file was created
     */
    long file_creation_time = 0;

    /**
     * Number of backup files
     */
    private int num_files = 9;

    /**
     * The outputstream logged to.
     */
    private FileOutputStream out;

    private static SharedResources rb = SharedResources.getResources();
    private RandomAccessFile raf = null;


    /**
     * Creates a new output stream with rollover criteria
     *
     * @param outfile the file to write output to.
     *
     * @param rollover_bytes the size of the file before rollover occurs. 
     *                       0 means never rollover on size.
     * @param rollover_secs   the age of the file before rollover occurs in
     *			      secs. 
     *                       0 means never rollover on age.
     */
    public RollingFileOutputStream(
	File outfile, 
	long rollover_bytes,
	long rollover_secs
    ) throws IOException {
        this.outfile = outfile;

	setRolloverBytes(rollover_bytes);
	setRolloverSecs(rollover_secs);

	if (outfile.exists()) {
	    if (!outfile.canWrite()) {
		throw new FileNotFoundException(
		    rb.getString(rb.X_FILE_WRITE, outfile));
	    } else if (outfile.isDirectory()) {
		throw new FileNotFoundException(
		    rb.getString(rb.X_DIR_NOT_FILE, outfile));
	    }
	}

	file_size = outfile.length();

        // Create file, appending if exists
	raf = null;
	try {
	    raf = new RandomAccessFile(outfile, "rw");
	} catch (IOException exc) {
	    throw exc;
	}

	if (file_size == 0) {
            out = new FileOutputStream(raf.getFD());
	    file_creation_time = System.currentTimeMillis();
	    try {
	        writeCreationTime(out, file_creation_time);
            } catch (IOException e) {
		throw new IOException(
		    rb.getString(rb.X_FILE_WRITE_TIMESTAMP, outfile));
            }
        } else {
	    try {
	        file_creation_time = readCreationTime(raf);
            } catch (IOException e) {
		throw new IOException(
		    rb.getString(rb.X_FILE_READ_TIMESTAMP, outfile));
            }
            raf.seek(raf.length());
            out = new FileOutputStream(raf.getFD());
	}

	// check if file is already too big, if so, move it
	if (doRollover()) {
            rolloverFile();
        }
    }

    /**
     * Write the creation time to a file. We encode the creation
     * time at the start of the file (since there is no way to
     * retrieve the creation time from a file in java). The first
     * line of a log file looks like:
     *
     * # 968803396174 Do not modify this line.
     *
     * Where 968803396174 is the creation time as specified by
     * System.currentTimeMillis()
     */
    private void writeCreationTime(OutputStream os, long time)
	throws IOException {

	String s = "# " + time + " Do not modify this line" + rb.NL;
	os.write(s.getBytes("us-ascii"));
    }

    /**
     * Read the creation time from a file.
     */
    private long readCreationTime(RandomAccessFile raf)
	throws IOException, EOFException {


	raf.seek(0);
	if (raf.skipBytes(2) != 2) {
	    throw new IOException("Could not skip bytes");
	};

	byte[] timeBytes = new byte[128];
	long time = 0;
	byte digit;
	int n;

	// Read digits until we hit the first non digit
	for (n = 0; n < 128; n++) { 
	    digit = raf.readByte();
	    if (digit < 48 || digit > 57) {
		break;
            } else {
                timeBytes[n] = digit;
            }
        }

	int base = 0;
	for (n = n - 1; n >= 0; n--) {
	    time += ((timeBytes[n] - 48) * Math.pow(10, base));
	    base++;
	}

        raf.seek(raf.length());

	return time;
    }

    /**
     * Creates a new output stream with rollover criteria
     *
     * @param outfile the file to write output to.
     *
     * @param rollover_bytes the size of the file before rollover occurs. 
     *                       0 means never roll overon size.
     */
    public RollingFileOutputStream(
	File outfile, 
	long rollover_bytes
    ) throws IOException {
	this(outfile, 1024, 0);
    }


    /**
     * Creates a new output stream with defaults
     * @param outfile the output file
     */
    public RollingFileOutputStream(File outfile) throws IOException {
        this(outfile, 1024);
    }

    public void setNumFiles(int n) {
	num_files = n;
    }

    public int getNumFiles() {
	return num_files;
    }

    /**
     * Writes a byte of data to the output stream. If the rollover point
     * is exceeded, the existing file will be rolled over to a new file.
     * @param b the byte to be written
     * @exception IOException if an I/O error has occurred
     */
    public synchronized void write(int b) throws IOException {
        file_size++;
        out.write(b);
    }


    /**
     *  Writes an array of bytes to the output stream.  If the
     *  rollover point is exceeded, the existing file will be rolled
     *  over to a new file.
     *  @param b the bytes to be written.
     *  @exception IOException If an I/O error has occurred.
     */
    public void write(byte[] b) throws IOException {
        write(b,0,b.length);
    }

    /**
     * Writes an array of bytes to the output stream. This method will block
     * until all the bytes have been written.  If the rollover point is
     * exceeded, the existing file will be rolled over to a new file.
     * @param b the bytes to be written
     * @param off the start offset of the bytes
     * @param len the number of bytes to write
     * @exception IOException if an I/O error has occurred
     */
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (doRollover()) {
            rolloverFile();
        }

        file_size += len;
        out.write(b, off, len);
    }

    /**
     * Closes the output stream.
     * @exception IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (raf != null) raf.close();
        out.close();
    }

    /**
     * Flushes the output stream.
     * @throws IOException if an I/O error has occurred
     */
    public void flush() throws IOException {
        out.flush();
    }

    /*
     * Move files around.
     */
    void rolloverFile() throws IOException {
        
        File testfile;
        File newfile;
	String  s;

        if (out != null) {
            out.flush();
            out.close();
        }
	s = outfile.getAbsolutePath();

        testfile = new File(generateFilename(s, num_files));
        if (testfile.exists()) testfile.delete();

        for (int i = num_files - 1; i > 0; i--) {
	    testfile = new File(generateFilename(s, i));

            if (testfile.exists()) {
	        newfile = new File(generateFilename(s, i + 1));

                testfile.renameTo(newfile);
            }
        }
	newfile = new File(generateFilename(s, 1));
        outfile.renameTo(newfile);

        out = new FileOutputStream(outfile);

	file_creation_time = System.currentTimeMillis();

	try {
	    writeCreationTime(out, file_creation_time);
        } catch (Exception e) {
	    System.out.println("Got exception" + e);
        }

        file_size = outfile.length();
    }

    /*
     * Insert rollover number into file name. It is placed at the
     * end of the file name unless there is a ".suffix" in which 
     * case it is placed before the ".suffix". I.e.:
     *
     *  log     -> log_1
     *  log.txt -> log_1.txt
     */
    public static String generateFilename(String name, int i) {

	int n = name.lastIndexOf('.');

	if (n == -1) {
            return name + "_" + i;
	} else {
            return name.substring(0, n) + "_" + i + name.substring(n);
	}
    }

    /**
     * Check if rollover condition has been met
     */
    boolean doRollover() {
        return
	    (rollover_bytes != 0 && file_size >= rollover_bytes) ||
	    (rollover_ms    != 0 && (System.currentTimeMillis() -
				    file_creation_time > rollover_ms));
    }

    public void setRolloverSecs(long seconds) {
        this.rollover_ms = seconds * 1000;
    }

    public long getRolloverSecs() {
	return this.rollover_ms / 1000;
    }

    public void setRolloverBytes(long bytes) {
	this.rollover_bytes = bytes;
    }

    public long getRolloverBytes() {
	return this.rollover_bytes;
    }
}
