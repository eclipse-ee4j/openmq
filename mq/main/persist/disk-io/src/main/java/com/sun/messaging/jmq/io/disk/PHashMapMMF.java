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
 * @(#)PHashMapMMF.java	1.6 08/28/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

import java.io.*;
import java.nio.*;

/**
 * PHashMapMMF is a persistent HashMap that is backed by a memory-mapped file.
 * Due to the nature of memory-mapped file, PHashMapMMF implementation can
 * provide partial entry udpate optimization which greatly improves PHashMap
 * update performance. In a standard PHashMap, the key and value object is
 * either serialized or externalized as a VRecord and it is written to the
 * backing file when an entry is updated, i.e. even when only a single field
 * in the value object is modified. Partial entry update optimization is
 * implemented by providing a client data section as part of the record and
 * additional APIs to update just the client data section of the record.
 * So instead of rewriting the whole record, the user of this class can just
 * update the client data portion of the record, e.g. value of the modified
 * field for the value object.
 *
 * Note: to use partial entry update optimization, the user of the this class
 * is responsible for processing the client data and update the value object
 * when PHashMapMMF is loaded (see
 * com.sun.messaging.jmq.jmsserver.persist.file.TidList for example usage).
 *
 * Each record has the following layout:
 *
<blockquote>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |                         record header                         |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |                     serialized key object                     |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |                    serialized value object                    |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |%CD|             client data section (optional)                |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
</blockquote>
 */
public class PHashMapMMF extends PHashMap {

    // Client data info for partial entry udpate optimization
    static final byte[] CLIENT_DATA_MARKER = {'%', 'C', 'D'};   // CD's marker
    static final byte[] EMPTY_CLIENT_DATA_MARKER = {0, 0, 0};   // Empty marker
    int maxClientDataSize = 0;        // size of client data (# bytes)

    /**
     * Construct the hash table backed by the specified file.
     * If the file exists, the hash table will be initialized
     * with entries from the file.
     * @param filename	Backing file.
     * @param safe		Indicate whether the underlying file
     *				should be sync'ed as soon as possible.
     * @param reset		If true, all data in the file will be cleared.
     */
    public PHashMapMMF(File filename, boolean safe, boolean reset, boolean isMinimumWrites, boolean interruptSafe)
	throws IOException {

	this(filename, VRFileMap.DEFAULT_INITIAL_FILE_SIZE,
            DEFAULT_INITIAL_MAP_CAPACITY, safe, reset, isMinimumWrites, interruptSafe);
    }

    /**
     * Construct the hash table backed by the specified file.
     * If the file exists, the hash table will be initialized
     * with entries from the file.
     * @param filename	Backing file.
     * @param size		Initialize size of backing file.
     * @param safe		Indicate whether the underlying file
     *				should be sync'ed as soon as possible.
     * @param reset		If true, all data in the file will be cleared.
     */
    public PHashMapMMF(File filename, long size, boolean safe, boolean reset, boolean isMinimumWrites, boolean interruptSafe)
        throws IOException {

	super(filename, size, DEFAULT_INITIAL_MAP_CAPACITY, safe, reset, isMinimumWrites, interruptSafe);
    }

    /**
     * Construct the hash table backed by the specified file.
     * If the file exists, the hash table will be initialized
     * with entries from the file.
     * @param filename	Backing file.
     * @param size		Initialize size of backing file.
     * @param mapCapacity	Initialize map capacity.
     * @param safe		Indicate whether the underlying file
     *				should be sync'ed as soon as possible.
     * @param reset		If true, all data in the file will be cleared.
     */
    public PHashMapMMF(File filename, long size, int mapCapacity,
        boolean safe, boolean reset, boolean isMinimumWrites, boolean interruptSafe) throws IOException {

	super(filename, size, mapCapacity, safe, reset, isMinimumWrites, interruptSafe);
    }

    protected void initBackingFile(File filename, long size, boolean isMinimumWrites, boolean interruptSafe) {
	backingFile = new VRFileMap(filename, size, isMinimumWrites, interruptSafe);
    }

    public void intClientData(int size) {

        if (size <= 0) {
            throw new IllegalArgumentException("Invalid client data size");
        }

        maxClientDataSize = size;
    }

    public void load(ObjectInputStreamCallback ocb) throws IOException, ClassNotFoundException,
	PHashMapLoadException {

	PHashMapLoadException loadException = null;

	Set entries = backingFile.getRecords();
        int eSize = entries.size();
        int mSize = this.size();
        if (eSize > mSize) {
            recordMap = new ConcurrentHashMap(eSize);
        } else {
            recordMap = new ConcurrentHashMap(mSize);
        }

	Iterator iter = entries.iterator();
	while (iter.hasNext()) {
	    VRecordMap record = (VRecordMap)iter.next();

	    Object key = null;
	    Object value = null;
	    Throwable kex = null;
	    Throwable vex = null;
	    Throwable ex = null;
	    try {
                ByteBuffer buffer = record.getBuffer();
                buffer.position(0);
                int limit = buffer.limit();
                byte[] data = new byte[limit];
                buffer.get(data);
                ByteArrayInputStream bais = new ByteArrayInputStream(data);

                // Use our version of ObjectInputStream so we can load old
                // serialized object from an old store, i.e. store migration
                ObjectInputStream ois = ocb.getObjectInputStream(bais);
	    	try {
		    key = ois.readObject();
	    	} catch (Throwable e) {
		    if (e instanceof ClassNotFoundException) {
			throw (ClassNotFoundException)e;
		    } else {
			kex = e;
		    }
		}

		try {
		    value = ois.readObject();
		} catch (Throwable e) {
		    if (e instanceof ClassNotFoundException) {
			throw (ClassNotFoundException)e;
		    } else {
			vex = e;
		    }
		}

                // Mark client data starting position
                if (maxClientDataSize > 0) {
                    // Since we've read in all data in the buffer, we need to
                    // reset the position back to client data starting position
                    int pos = limit - bais.available();
                    buffer.position(pos);
                    buffer.mark();
                }

		ois.close();
		bais.close();
	    } catch (IOException e) {
		ex = e;
	    }

	    if (kex != null || vex != null || ex != null) {

		PHashMapLoadException le = new PHashMapLoadException(
                    "Failed to load data in [" + record.toString() + "]");
		le.setKey(key);
		le.setValue(value);
		le.setKeyCause(kex);
		le.setValueCause(vex);
		le.setNextException(loadException);
		le.initCause(ex);
		loadException = le;

		if (key != null && value != null) {
		    // we have the key, keep the record
		    recordMap.put(key, record);
		    putInHashMap(key, value, false);
		} else {
		    // delete bad record
		    backingFile.free(record);
		}
	    } else {
		// cache info
		recordMap.put(key, record);
		putInHashMap(key, value, false);
	    }
	}

	loaded = true;

	if (loadException != null) {
	    throw loadException;
	}
    }

    /**
     * Maps the specified key to the specified value in this HashMap.
     * The entry will be persisted in the backing file.
     */
    Object doPut(Object key, Object value, boolean putIfAbsent) {
	checkLoaded();

        boolean error = false;
        Object oldValue = null;
	try {
            oldValue = putInHashMap(key, value, putIfAbsent);
            if (putIfAbsent && (oldValue != null)) {
                // nothing to do since there was a mapping for key
                return oldValue;
            }

            // serialize the key and value
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            ObjectOutputStream bos = new ObjectOutputStream(baos);

            bos.writeObject(key);
            bos.writeObject(value);
            bos.close();

            byte[] data = baos.toByteArray();
            int dataLength = data.length;
            if (maxClientDataSize > 0) {
                // Add addtional space for client data and marker
                // to indicate the existence of client data
                dataLength += maxClientDataSize + CLIENT_DATA_MARKER.length;
            }

            VRecordMap record = (VRecordMap)recordMap.get(key);

            synchronized (backingFile) {
                if (record == null) {
                    record = (VRecordMap)backingFile.allocate(dataLength);
                } else {
                    if (record.getDataCapacity() < dataLength) {
                        // need another VRecordMap
                        backingFile.free(record);
                        record = (VRecordMap)backingFile.allocate(dataLength);
                    }
                }
                MappedByteBuffer buffer = (MappedByteBuffer)record.getBuffer();
                buffer.rewind();
                buffer.put(data);

                // Mark client data starting position
                if (maxClientDataSize > 0) {
                    buffer.mark();

                    // Clear the old data by removing the old's marker
                    buffer.put(EMPTY_CLIENT_DATA_MARKER);

                    // Now, resets buffer's to starting position of client data
                    buffer.reset();
                }

                if (safe) {
                    record.force();
                }
            }

            // update internal records map
            recordMap.put(key, record);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	} finally {
            if (error) {
                if (oldValue == null) {
                    super.remove(key);
                } else {
                    putInHashMap(key, oldValue, false); // put back the old value
                }
            }
        }

        return oldValue;
    }

    /**
     * Update the client data section of the record.
     *
     * @param key key with which the specified client data is to be associated
     * @param cData client data to be associated with the specified key
     * @exception IllegalStateException if client data size has not been
     * initialized or the record is not found
     */
    public final void putClientData(Object key, byte[] cData) {
        checkLoaded();

        if (maxClientDataSize <= 0) {
            throw new IllegalStateException(
                "Client data size has not been initialized");
        }

        if (cData.length > maxClientDataSize) {
            throw new IllegalArgumentException(
                "Client data size of " + cData.length +
                " bytes is larger than the byte limit (maxClientDataSize) of " +
                maxClientDataSize + " [key=" + key + ", cData=" + Arrays.toString(cData) + "]");
        }

        VRecordMap record = (VRecordMap)recordMap.get(key);
        if (record == null) {
            throw new IllegalStateException(
                "Record not found [key=" + key + ", cData=" + Arrays.toString(cData) + "]");
        }

        try {
            synchronized (backingFile) {
                // Just update the client data portion of the record
                MappedByteBuffer buffer = (MappedByteBuffer)record.getBuffer();

                try {
                    // Resets buffer's to starting position of client data
                    buffer.reset();
                } catch (InvalidMarkException e) {
                    // Try to reset the marker
                    setClientDataMarker(buffer);
                }

                buffer.put(CLIENT_DATA_MARKER); // Put marker
                buffer.put(cData);              // Put client data

                if (safe) {
                    record.force();
                }
            }

            // update internal HashMap
            recordMap.put(key, record);
        } catch (IOException e) {
            throw new RuntimeException(
                "Unable to update client data [key=" + key +
                ", cData=" + Arrays.toString(cData) + "]", e);
        }
    }

    /**
     * Get client data for the specified key; null if there's none.
     */
    public final byte[] getClientData(Object key) {

        byte[] cData = null;

        // load client data if available
        try {
            if (maxClientDataSize > 0) {
                VRecordMap record = (VRecordMap)recordMap.get(key);
                if (record != null) {
                    synchronized (backingFile) {
                        ByteBuffer buffer = record.getBuffer();

                        // Resets buffer's to starting position of client data
                        try {
                            buffer.reset();
                        } catch (InvalidMarkException e) {
                            // Try to reset the marker
                            setClientDataMarker(buffer);
                        }

                        // Check for existence of client data marker, i.e. "%CD"
                        byte b1 = buffer.get();
                        byte b2 = buffer.get();
                        byte b3 = buffer.get();
                        if (b1 == CLIENT_DATA_MARKER[0] &&
                            b2 == CLIENT_DATA_MARKER[1] &&
                            b3 == CLIENT_DATA_MARKER[2]) {
                            cData = new byte[maxClientDataSize];
                            buffer.get(cData); // Read in client data
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return cData;
    }

    /**
     * Mark starting position of client data for the specified ByteBuffer.
     *
     * Note:
     * Calling method is responsible for synchronize on VRFile (backing file).
     */
    private void setClientDataMarker(ByteBuffer buffer) {

        // Try to look for the marker, i.e. "%CD"
        for (int i = 0, limit = buffer.limit(); i < limit; i++) {
            if (buffer.get(i) == CLIENT_DATA_MARKER[0]) {
                // Found '%', so check if the next 2 bytes is "CD"
                if ((i + 2) < limit &&
                    buffer.get(i+1) == CLIENT_DATA_MARKER[1] &&
                    buffer.get(i+2) == CLIENT_DATA_MARKER[2]) {
                    buffer.position(i);
                    buffer.mark();
                    return;     // marker has been found and set
                }
            }
        }

        // Record doesn't contain client data so just reset the marker at the
        // end of the value object; the position where client data should start.
        // The crude way to do this is to re-read the key and value object.
        Object key = null;
        Object value = null;
        Throwable kex = null;
        Throwable vex = null;
        Throwable ex = null;
        try {
            buffer.position(0);
            int limit = buffer.limit();
            byte[] data = new byte[limit];
            buffer.get(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new FilteringObjectInputStream(bais);

            try {
                key = ois.readObject();
            } catch (Throwable e) {
                kex = e;
            }

            try {
                value = ois.readObject();
            } catch (Throwable e) {
                vex = e;
            }

            // Mark starting position of client data
            if (maxClientDataSize > 0) {
                // Since we've already read in the whole buffer we need to
                // reset buffer's position back to the start of client data
                int pos = limit - bais.available();
                buffer.position(pos);
                buffer.mark();
            }

            ois.close();
            bais.close();
        } catch (IOException e) {
            ex = e;
        }

        if (kex != null || vex != null || ex != null) {
            PHashMapLoadException le = new PHashMapLoadException(
                "Failed to set client data marker");
            le.setKey(key);
            le.setValue(value);
            le.setKeyCause(kex);
            le.setValueCause(vex);
            le.initCause(ex);
            throw new RuntimeException(le);
        }
    }
}
