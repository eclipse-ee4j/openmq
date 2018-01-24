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
 * @(#)VRFile.java	1.28 06/27/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.messaging.jmq.resources.*;

/**
 * VRFile is a simple implementation of a variable sized record based file.
 * The implementation is optimized for simiplicity and speed at the expense
 * of diskspace.
 * <p>
 * A VRFile is backed by a disk file. The file is split into records as
 * records are requested by the caller. Record size is defined to be a
 * multiple of a block size to increase the likelyhood of record-reuse. 
 * Free'd records are tracked for re-use. Overtime it is possible for
 * holes to occur in the file of record sizes that are never re-used.
 * A compact() method is provided to compact() the backing file -- this
 * could be an expensive operation depending on the amount of data in the file.
 * <p>
 * The initial design will support no record splitting or coelesceing
 * (except via compact).
 *  Free'd buffers will simply be put on a free list and re-used
 *  if they are large enough to satisfy an allocate() request. This is
 *  based on the assumption that buffer sizes will not vary wildly for
 *  any given allocator.
 *
 * <p>
 * Specific Details:
 *
 * <p>
 *  The backing file has the following header:
<blockquote>
         0                   1                   2                   3<br>
        |0 1 2 3 4 5 6 7|8 9 0 1 2 3 4 5|6 7 8 9 0 1 2 3|4 5 6 7 8 9 0 1|<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
       0|                         magic #                               |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
       4|             version           |      reserved for later use   |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
       8|                                                               |<br>
        +              Application cookie                               +<br>
      12|                                                               |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
      16|                                                               |<br>
        +              index to properties record			|<br>
      20|                                                               |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
</blockquote>
<br>   
 * magic #: A constant that helps us validate the file is of
 *          the correct type <br>
 * version: Identifies the version of the file <br>   
 * application cookie: 64 bits reserved for use by the application. This
 * gives applications place to store version information, etc.  <br>   
 * <p>
 * Each allocated record has the following header:
 *
<blockquote>
         0                   1                   2                   3<br>
        |0 1 2 3 4 5 6 7|8 9 0 1 2 3 4 5|6 7 8 9 0 1 2 3|4 5 6 7 8 9 0 1|<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |                         magic #                               |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |                         capacity                              |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>
        |   state                       |    reserved for later use     |<br>
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+<br>

</blockquote>
<p>
 *  magic #: Helps identify the start of an allocation. It is only used
 *           as a sanity check to help ensure we don't read bad data. It
 *           can also be used to search for the start of an allocation if
 *           a file gets corrupted.  <br>
 *  capacity: The size of the allocation including this header <br>
 *
<blockquote>
    state: State of the allocation. One of:
            1 = Free
            2 = Allocated
	    3 = Last
	    4 = Properties Record
            5 = Pending (operation is pending).

</blockquote>
 *
 * When an allocator is first created the backing file is created of size
 * initialCapacity and the entire file is mapped. The header is written. <p>
 *
 * When the first allocation request comes in the allocation size is
 * rounded up to be a multiple of the block size. A slice is created from
 * the MappedByteBuffer and the record header is written with a state
 * of ALLOCATED. A VRecord is created and added to a list of
 * allocated records and returned.  <p>
 *
 * When a record is free'd it's state is updated to FREE and it is 
 * moved from the allocated list to the free list that is sorted by capacity.
 * <p>
 * When subsequent allocation requests come in the free list is searched
 * first for a buffer that is of sufficient capacity. If one is found
 * it is marked as ALLOCATED, moved to the allocated list and returned.
 * If one is not found a new one is allocated from the backing file.
 * If the file is not large enough we grow it using the growth factor.
 * <p>
 * When compact() is called a new empty file is created and all records
 * whose state is not free are written to it. The file is then grown to be
 * of at least initialCapacity if need be. The old file is renamed to back
 * it up, and the new file is renamed to become the new backing store.
 * The free list is disgarded and the new file is loaded.
 * <p>
 * When a file is loaded, the header is read to verify file
 * type and version. Then the file is scanned and records are placed on the
 * free and allocated lists as dictated by their state.
 *
 * Notes:
 *
 *  - We should be able to easily extend VRecord to provide an indexed
 *    file where the key is always a long.
 *
 *    The current message store also has this risk (if the system crashes
 *    while a message is being stored), but the consequence is loosing at
 *    most one message. We must be sure that if a record is corrupted we
 *    loose at most that one record and not the entire file. That is why
 *    we have magic numbers in the record headers (maybe they should be longer?)
 *    so the code has a chance to recover. That is also why we don't mess
 *    with storing any indexes, free lists, etc on disk -- that is just
 *    more stuff that can become corrupted.
 *
 */

public abstract class VRFile {

    private static boolean DEBUG = Boolean.getBoolean("vrfile.debug");

    // file version 1 definitions:
    public static final short FILE_VERSION_1 = 1;
    public static final int FILE_HEADER_SIZE_1 = 16;
    public static final short STATE_LAST_1 = 0;
    public static final short STATE_FREE_1 = 1;
    public static final short STATE_ALLOCATED_1 = 2;

    // file header constants
    public static final short FILE_VERSION = 2;
    public static final int FILE_HEADER_SIZE = 24;
    public static final int FILE_MAGIC_NUMBER = 0x5555AAAA;
    public static final short RESERVED_SHORT = 0;

    // record header constants
    public static final int RECORD_HEADER_SIZE = 12;
    public static final int RECORD_MAGIC_NUMBER = 0xAAAA5555;
    public static final int RECORD_CAPACITY_OFFSET = 4;
    public static final int RECORD_STATE_OFFSET = 8;
    public static final int RECORD_COOKIE_OFFSET = 10;
    public static final short STATE_CUTOFF = -1;
    public static final short STATE_BAD_MAGIC_NUMBER = -2;
    public static final short STATE_BAD_STATE = -3;
    public static final short STATE_BAD_CAPACITY_TOO_SMALL = -4;
    public static final short STATE_BAD_NEXT_MAGIC_NUMBER = -5;
    public static final short STATE_BAD_CAPACITY = -6;
    public static final short STATE_BAD_TRUNCATED_HEADER = -7;
    public static final short STATE_FREE = 1;
    public static final short STATE_ALLOCATED = 2;
    public static final short STATE_LAST = 3;
    public static final short STATE_PROPERTIES = 4;

    protected static final short _STATE_LAST = 1001; 

    public static final int SHORT_LEN = 2; // len of short
    public static final int INT_LEN = 4; // len of int
    public static final int LONG_LEN = 8; // len of long

    // Default block size is 128 bytes
    public final static int   DEFAULT_BLOCK_SIZE = 128;

    // Default initial file size is 10 MB
    public final static long  DEFAULT_INITIAL_FILE_SIZE = 10 * (1024 * 1024);
    public final static long  MINIMUM_INITIAL_FILE_SIZE
				= FILE_HEADER_SIZE + RECORD_HEADER_SIZE;

    

    // Default growth factor is 50%
    public final static float DEFAULT_GROWTH_FACTOR = 0.5f;
    public final static float DEFAULT_THRESHOLD_FACTOR = 0.0f;
    public final static long DEFAULT_THRESHOLD = 0;

    protected long threshold = DEFAULT_THRESHOLD;

    // growth factor after the threshold is reached
    protected float thresholdFactor = DEFAULT_THRESHOLD_FACTOR;



    // Block size. Every allocation is a multiple of this.
    protected int blockSize   = DEFAULT_BLOCK_SIZE;

    // Initial file size. When a new (empty) file is created a file of this
    // size will be mapped.
    protected long initialFileSize = DEFAULT_INITIAL_FILE_SIZE;

    // The amount the file grows by when more backing storage is needed.
    // If this is 1.0 then the file size doubles. If it is 0.25 then
    // the file is grown by 1/4. In general we want to minimize the
    // number of MappedByteBuffers we allocate.
    protected float growthFactor    = DEFAULT_GROWTH_FACTOR;

    // If true do all we can do ensure data is persisted to disk as
    // soon as possible. This sacrifices performance for safety.
    protected boolean safe = false;

    // information about our backing file
    protected short fileversion = 2;	// will be changed if a file is loaded
    protected long fileSize = 0;	// current file size
    protected long filePointer = 0;	// ptr to end of allocated section
    protected File backingFile = null;	// name of backing file

    // all allocated buffers
    protected HashSet allocated = null;
    protected long bytesAllocated = 0;

    // free buffers: map hashed by capacity
    // (capacity->LinkedList of free records)
    protected TreeMap freeMap = null;		// hashed by capacity
    protected int numFree = 0;			// number of free records

    protected long cookie = 0;
    
    long fileCookie = 0; /// the value of the cookie read from the file 

    // record header with a state of STATE_LAST marking the last record
    // in the file; this record spans to the end of the file
    protected byte[] lastRecordHeader = null;

    // whether the file is opened and loaded
    protected boolean opened = false;

    protected VRFileWarning warning = null;

    // statistics
    protected int hits = 0;
    protected int misses = 0;
    protected boolean isMinimumWrites = false;
    protected boolean interruptSafe = false;

    protected VRFile(File file, long size, boolean isMinimumWrites, boolean interruptSafe) {

	if (DEBUG) {
	    System.out.println("backing file: "+file);
	}
        this.isMinimumWrites = isMinimumWrites; 
        this.interruptSafe = interruptSafe;

	backingFile = file;
	initialFileSize = (size < MINIMUM_INITIAL_FILE_SIZE ?
				MINIMUM_INITIAL_FILE_SIZE : size);

	// set up last record header
	lastRecordHeader = new byte[RECORD_HEADER_SIZE];
	ByteBuffer temp = ByteBuffer.wrap(lastRecordHeader);
	temp.putInt(RECORD_MAGIC_NUMBER);
	temp.putInt(0);
	temp.putShort(STATE_LAST);
	temp.putShort(RESERVED_SHORT);

	allocated = new HashSet(1000);
	freeMap = new TreeMap();
    }

    protected boolean isMinimumWrites() {
        return isMinimumWrites;
    }

    public File getFile() {
	return backingFile;
    }

    /**
     * Return the number of allocated records currently in the file.
     */
    public short getFileVersion() {
	checkOpen();
        return fileversion;
    }

    /**
     * Open and load the backing file.
     * If the backing file does not exist, it will be created and it
     * size set to the initial file size. Otherwise, all records
     * (allocated or free) will be loaded in memory.
     */
    public abstract void open() throws IOException, VRFileWarning;

    // Close the VRFile and free up any resources
    public abstract void close();

    protected void reset() {
	allocated.clear();
	freeMap.clear();
	opened = false;
	numFree = 0;
	bytesAllocated = 0;
	hits = 0;
	misses = 0;
    }

    /**
     * Set safe.
     * If true do all we can to ensure data is persisted to disk as
     * soon as possible. This sacrifices performance for safety.
     * It is still up to the caller to invoke VRecord.force() to
     * force their writes to disk.
     */
    public void setSafe(boolean safe) {
	this.safe = safe;
    }

    public boolean getSafe() {
	return this.safe;
    }

    /**
     * Set the block size to use. Every record will be a multiple of
     * this. Default is 128 bytes. You may change the block size at any time.
     * We round up to the block size since it is a way for us to improve
     * the likelyhood of similar sized requests reusing buffers.
     */
    public void setBlockSize(int n) {
	if (n <= 0) {
	    throw new IllegalArgumentException(
		"Block size must be postive. Illegal block size: " + n);
	}
        blockSize = n;
    }

    public int getBlockSize() {
        return blockSize;
    }

    /**
     * The amount the file grows by when more backing storage is needed.
     * Default is 0.5.
     */
    public void setGrowthFactor(float n) {
	if (n <= 0) {
	    throw new IllegalArgumentException(
		"Growth factor must be postive. Illegal growth factor: " + n);
	}
        growthFactor = n;
    }

    public float getGrowthFactor() {
        return growthFactor;
    }

    /**
     * Return the number of allocated records currently in the file.
     */
    public int getNRecords() {
	checkOpen();
        return allocated.size();
    }

    /**
     * Return the number of free records currently in the file
     */
    public int getNFreeRecords() {
	checkOpen();
        return numFree;
    }

    /**
     * Get number of allocated bytes
     */
    public long getBytesUsed() {
	checkOpen();
	return bytesAllocated;
    }

    /**
     * Get number of free bytes 
     * Note that filePointer is used instead of fileSize
     */
    public long getBytesFree() {
	checkOpen();
	int offset = (fileversion == FILE_VERSION) ?
			FILE_HEADER_SIZE : FILE_HEADER_SIZE_1;
	return (filePointer - getBytesUsed() - offset);
    }

    /**
     * Get number of hits. A hit is defined to be when we are able
     * to satisfy an allocation request by using a record on the free list.
     */
    public int getHits() {
	return hits;
    }

    /**
     * Get number of misses. A miss is defined to be when we must allocate
     * a new record from the backing store.
     */
    public int getMisses() {
	return misses;
    }

    /**
     * Get hit ratio. A value of 1 means we are always re-using records.
     * A value of 0 means we are never re-using records. A value of 0.5
     * means we are reusing records half of the time
     */
    public float getHitRatio() {
	if ((hits + misses) == 0)
	    return 0;
	else
	    return (((float)(hits*1.00))/(hits+misses));
    }

    /**
     * Get the fragmentation ratio. This returns some measurement of
     * how badly the file is fragmented. A value of 1 means the file is
     * totally fragmented. A value of 0 means the file is not fragmented
     * at all. WARNING! This may be an expensive operation.
     * [To be honest I'm not sure how to compute this, but we need something
     * to give the caller an indication of when it is time to compact()].
     */
    public float getFragmentationRatio() {
	throw new UnsupportedOperationException();
    }

    /**
     * Get the utilization ratio. This returns a number indicating
     * how much of the file is used. A value of 1 means the file is
     * 100% used. A value of 0 means the file is not used at all.
     */
    public float getUtilizationRatio() {
	// used/total
	int offset = (fileversion == FILE_VERSION) ?
			FILE_HEADER_SIZE : FILE_HEADER_SIZE_1;
	long total = filePointer - offset;
	if (total == 0) {
	    return 1;
	} else {
	    float r = (((float)(getBytesUsed()*1.00))/total);
	    return r;
	}
    }

    /**
     * Get a map of what the record layout of the file looks like. The
     * array represents the sequence of records. The value represents
     * the record's size (capacity). If the value is positive the record
     * allocated, if the value is negative the record is free.
     * WARNING! This may be an expensive operation.
     */
    public abstract int[] getMap() throws IOException;

    /**
     * Allocate a record of at least size "size". The actual size allocated
     * will be a multiple of the block size and may be larger than requested.
     * The actual size allocated can
     * be determined by inspecting the returned records capacity().
     */
    public abstract VRecord allocate(int size) throws IOException;

    protected VRecord findFreeRecord(int size) {
	// get it from free list
	Integer cap = Integer.valueOf(size);

	LinkedList list = (LinkedList)freeMap.get(cap);

	if (list != null && !list.isEmpty()) {
	    return ((VRecord)list.removeLast());
	} else {
	    // try next bigger free record
	    SortedMap tail = freeMap.tailMap(cap);
	    Iterator itr = tail.entrySet().iterator();
	    while (itr.hasNext()) {
		Map.Entry entry = (Map.Entry)itr.next();
		list = (LinkedList)entry.getValue();
		if (list != null && !list.isEmpty()) {
		    return ((VRecord)list.removeLast());
		}
	    }
	    return null;
	}
    }

    /**
     * Free a record.
     */
    public synchronized void free(VRecord vr) throws IOException {

	checkOpenAndWrite();

	if (DEBUG) {
	    System.out.println("free record:"+vr);
	}
	boolean in = allocated.remove(vr);
	if (!in) {
	    String msg = backingFile.toString() + ":" + vr;
	    throw new IllegalStateException(
			SharedResources.getResources().getString(
			    SharedResources.E_UNRECOGNIZED_VRECORD,
			    msg));
	}

	bytesAllocated -= vr.getCapacity();
	putFreeList(vr, false);

	try {
	    vr.free();
	    if (safe) {
		vr.force();
	    }
	} catch (BufferOverflowException e) {
	    String errmsg = "Failed to free vrecord:" + backingFile.toString()
			+ ":" + vr + ":";
	    throw new IOException(errmsg + e.toString());
	}
    }

    /**
     * Get all the allocated records in no particular order. This is
     * typically called immediately after open() to get all records
     * that contain data.
     */
    public synchronized Set getRecords() {
	checkOpen();

	return (Set)allocated.clone();
    }

    /**
     * Compact the file. This may be a very time consuming operation.
     * compact() may only be called on a closed file. After compact()
     * completes the file may be open()ed.
     */
    public synchronized void compact() throws IOException, VRFileWarning {
	if (opened) {
	    throw new IllegalStateException(
			SharedResources.getResources().getString(
			    SharedResources.E_CANNOT_COMPACT_ON_OPENED_FILE));
	}

	if (fileversion < FILE_VERSION) {
	    throw new IllegalStateException(backingFile +
		"Cannot compact a file of version " + fileversion);
	}

	if (!backingFile.exists() || backingFile.length() == 0) {
	    // nothing to do
	    return;
	}

	// backing file
	RandomAccessFile from = new RandomAccessFile(backingFile, "r");

	// check file header
	byte[] barray = new byte[FILE_HEADER_SIZE];
	int num = from.read(barray);
	if (num != FILE_HEADER_SIZE) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_UNRECOGNIZED_VRFILE_FORMAT,
			backingFile, Integer.valueOf(num)));
	}
	checkFileHeader(ByteBuffer.wrap(barray));

	// temp file
	File tempfile = new File(backingFile.getParentFile(),
				(backingFile.getName() + ".temp"));
	RandomAccessFile temp = new RandomAccessFile(tempfile, "rw");

	// write header
	temp.write(barray);

	// write all allocated record to the temp file
	int numtransferred = 0;
	int numfreeskipped = 0;
	long filelength = from.length();
	long frompos = from.getFilePointer();
	boolean done = false;
	ByteBuffer recordheader = ByteBuffer.wrap(new byte[RECORD_HEADER_SIZE]);

	while (!done) {
	    int capacity = 0;
	    short state = getRecordState(from, recordheader, frompos,
					filelength);

	    switch (state) {
	    case STATE_ALLOCATED:
	    case STATE_PROPERTIES:
		capacity = recordheader.getInt(RECORD_CAPACITY_OFFSET);
		byte buf[] = new byte[capacity];
		from.seek(frompos);
		from.read(buf);
		temp.write(buf);

		numtransferred++;
		break;

	    case STATE_FREE:
		capacity = recordheader.getInt(RECORD_CAPACITY_OFFSET);

		numfreeskipped++;
		break;

	    case _STATE_LAST:
		writeLastRecordHeader(temp);
		done = true;
		break;

	    case STATE_BAD_MAGIC_NUMBER:
	    case STATE_BAD_NEXT_MAGIC_NUMBER:
	    case STATE_BAD_STATE:
	    case STATE_BAD_CAPACITY:
	    case STATE_BAD_CAPACITY_TOO_SMALL:
	    case STATE_BAD_TRUNCATED_HEADER:

		// skip over the bad ones and just continue with the next
		// good one if one can be found
		long nextstart = findGoodRecord(from, frompos, filelength);
		if (nextstart == filelength) {
		    // no more good record found; write last record header
		    writeLastRecordHeader(temp);
		    done = true;
		} else {
		    frompos = nextstart;
		}

		addCompactWarning(getNewWarning(), state, frompos,
					recordheader, nextstart);
		break;
	    }

	    frompos += capacity;
	    from.seek(frompos);
	}

	temp.close();
	from.close();

	if (DEBUG) {
	    System.out.println("compact(): size of original file is " +
			backingFile.length());
	    System.out.println("compact(): size of new file is " +
			tempfile.length());
	}

	// rename backingFile to backupfile
	File backupFile = new File(backingFile.getParentFile(),
				(backingFile.getName() + ".bak"));
	if (!backingFile.renameTo(backupFile)) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_RENAME_TO_BACKUP_FILE_FAILED,
			backingFile, backupFile));
	}

	// rename tempfile to backingFile
	if (!tempfile.renameTo(backingFile)) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_RENAME_TO_BACKING_FILE_FAILED,
			tempfile, backingFile));
	}

	// get rid of the backupfile
	if (!backupFile.delete()) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_DELETE_BACKUP_FILE_FAILED,
			backupFile));
	}

	// reset counts
	hits = 0;
	misses = 0;

	if (DEBUG) {
	    System.out.println("compact(): number of records written is "+
			numtransferred);
	    System.out.println("compact(): number of free records skipped is "+
			numfreeskipped);
	}

	// make sure warning is thrown at last so that
	// all processing can be done
	if (warning != null) {
	    throw warning;
	}
    }

    // this method returns a state value that is independent of the file
    // version, in specific, the state of last record is converted to
    // _STATE_LAST
    // it also makes sure that the state is valid
    protected short adjustRecordState(short fversion, short s) {
	if (fversion == FILE_VERSION_1) {
	    if ((s < STATE_LAST_1) || (s > STATE_ALLOCATED_1)) {
		return STATE_BAD_STATE;
	    }
	} else { // file version 2 cases
	    if ((s < STATE_FREE) || (s > STATE_PROPERTIES)) {
		return STATE_BAD_STATE;
	    }
	}

	if (fversion == FILE_VERSION_1 && s == STATE_LAST_1) {
	    return _STATE_LAST;
	} else if (fversion == FILE_VERSION && s == STATE_LAST) {
	    return _STATE_LAST;
	} else {
	    return s;
	}
    }

    // file pointer is at the record to be checked
    // pos and limit are passed in to avoid invoking RandomAccessFile methods
    short getRecordState(RandomAccessFile file, ByteBuffer recordheader,
	long pos, long limit) throws IOException {

	// read record header
	int n = file.read(recordheader.array());
	if (n != RECORD_HEADER_SIZE) {
	    return STATE_BAD_TRUNCATED_HEADER;
	}
	recordheader.rewind();

	int magic = recordheader.getInt();
	int capacity = recordheader.getInt();
	short state = adjustRecordState(fileversion, recordheader.getShort());

	if (magic != RECORD_MAGIC_NUMBER) {
	    // 1. check record magic number first
	    if (DEBUG) {
		System.out.println(
			"BAD RECORD("+pos+"): BAD MAGIC NUMER:"+magic);
	    }
	    return STATE_BAD_MAGIC_NUMBER;
	} else if (state == STATE_BAD_STATE) {
	    // 2. check state
	    return state;
	} else if (state == _STATE_LAST) {
	    // 3. if this is the last record do some sanity check
	    if (capacity != 0) {
		if (DEBUG) {
		    System.out.println(
			"BAD RECORD("+pos+"): LAST RECORD WOTH CAP="+capacity);
		}
		return STATE_BAD_CAPACITY;
	    } else {
		return state;
	    }
	} else if (capacity <= RECORD_HEADER_SIZE) {
	    // 4. do more sanity check
	    if (DEBUG) {
		System.out.println(
			"BAD RECORD("+pos+"): CAP<RECORD_HEADER:"+capacity);
	    }
	    return STATE_BAD_CAPACITY_TOO_SMALL;
	} else if ((pos + capacity) <= (limit - 4)) {
	    // (limit - 4) to make sure we have at least 4 bytes to
	    // read the next magic number
	    // 5. check magic number of the next record
	    file.seek(pos + capacity);
	    magic = file.readInt();
	    file.seek(pos);

	    if (magic != RECORD_MAGIC_NUMBER) {
		return STATE_BAD_NEXT_MAGIC_NUMBER;
	    } else {
		return state;
	    }
	} else {
	    // this record spans to the end of the file or beyond
	    // but it's not marked as the last record; consider it bad
	    return STATE_BAD_CAPACITY;
	}
    }

    /**
     * Force all changes made to all records to be written to
     * disk. Note that the VRFile implementation may at times choose to
     * force data to disk independent of this method.
     */
    public abstract void force() throws IOException;

    /**
     * Set header cookie.
     */
    public synchronized void setCookie(long n) throws IOException {
	cookie = n;
    }

    /**
     * Get header cookie.
     */
    public synchronized long getCookie() {
	return cookie;
    }

    /**
     * Clear all records.
     */
    public abstract void clear(boolean truncate) throws IOException;

    /**
     * Get any warning messages generated while the file is loaded.
     */
    public VRFileWarning getWarning() {
	return warning;
    }

    // write last record header into the file
    // if file length is shorter than initialFileSize; extend it to
    // initialFileSize
    protected void writeLastRecordHeader(RandomAccessFile file)
	throws IOException {

	if (file.length() < initialFileSize) {
	    // extend file to initialFileSize
	    file.setLength(initialFileSize);
	}

	// write last record header
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        file.write(lastRecordHeader);
            } catch (InterruptedIOException e) {
		interrupted = true;
		throw e;
            } finally {
		if (interrupted) {
                    Thread.currentThread().interrupt();
		}
            }
	} else {
            file.write(lastRecordHeader);
	}

    }

    // write file header into ByteBuffer
    protected void writeFileHeader(ByteBuffer buf) throws IOException {
	buf.putInt(FILE_MAGIC_NUMBER);
	buf.putShort(FILE_VERSION);
	buf.putShort(RESERVED_SHORT);
	buf.putLong(cookie);
	buf.putLong(0);
    }

    // write file header into DataOutput
    protected void writeFileHeader(DataOutput out) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        out.writeInt(FILE_MAGIC_NUMBER);
                out.writeShort(FILE_VERSION);
                out.writeShort(RESERVED_SHORT);
                out.writeLong(cookie);
                out.writeLong(0);
            } catch (InterruptedIOException e) {
		interrupted = true;
		throw e;
            } finally {
		if (interrupted) {
                    Thread.currentThread().interrupt();
		}
            }
	} else {
            out.writeInt(FILE_MAGIC_NUMBER);
            out.writeShort(FILE_VERSION);
            out.writeShort(RESERVED_SHORT);
            out.writeLong(cookie);
            out.writeLong(0);
	}
    }


    /**
     * check file header and return the file version
     */
    protected short checkFileHeader(ByteBuffer buf) throws IOException {
	// check header
	int magic = buf.getInt();

	if (magic != FILE_MAGIC_NUMBER) {
	    Object[] args = { backingFile,
				Integer.toString(magic),
				Integer.toString(FILE_MAGIC_NUMBER) };

	    throw new StreamCorruptedException(
		SharedResources.getResources().getString(
			SharedResources.E_BAD_FILE_MAGIC_NUMBER, args));
	}

	fileversion = buf.getShort();
	if ((fileversion != FILE_VERSION_1) && (fileversion != FILE_VERSION)) {
	    Object[] args = { backingFile,
				Short.toString(fileversion),
				Short.toString(FILE_VERSION) };

	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_BAD_VRFILE_VERSION, args));
	}

	// not used yet
	buf.getShort();

	fileCookie = buf.getLong();
	/* we don't check the application cookie
	if (filecookie != cookie) {
	    Object[] args = { backingFile,
				Long.toString(filecookie),
				Long.toString(cookie) };

	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_BAD_APPLICATION_COOKIE, args));
	}
	*/

	return fileversion;
    }

    /**
     * Forces any changes to the storage device if 'safe' is true.
     */
    protected void doForce() throws IOException {
	if (safe) {
	    force();
	}
    }

    /**
     * Put the VRecord in the allocated list
     */
    protected void putAllocatedList(VRecord vr) {
	allocated.add(vr);
	bytesAllocated += vr.getCapacity();
    }

    /**
     * Put the VRecord in the appropriate list
     * If addToHead is true, add the record at the beginning of the
     * list, otherwise, add to the end.
     */
    protected void putFreeList(VRecord vr, boolean addToHead) {

	Integer cap = Integer.valueOf(vr.getCapacity());

	LinkedList list = (LinkedList)freeMap.get(cap);
	if (list == null) {
	    list = new LinkedList();
	    freeMap.put(cap, list);
	}
	if (addToHead) {
	    list.addFirst(vr);
	} else {
	    list.add(vr);
	}
	numFree++;
    }

    /**
     * Starting from pos, scan for a good record.
     * First find the record magic number and get the capacity.
     * A good record will be one with a record magic number after
     * the end of it (indicating another record); or it's the LAST record
     * @return the position of the good record or end point of the file if
     *		none is found
     */
    protected long findGoodRecord(
	RandomAccessFile file, long pos, long filelen) {

	while (pos < filelen) {
	    try {
		file.seek(pos);
		int magic = file.readInt();

		if (magic == RECORD_MAGIC_NUMBER) {
		    int capacity = file.readInt();
		    short state = adjustRecordState(fileversion,
							file.readShort());
		    long nextpos = pos + capacity;

		    if (state == STATE_BAD_STATE) {
			; // continue
		    } else if ((state != _STATE_LAST)
			&& (capacity <= RECORD_HEADER_SIZE)) {
			; // continue
		    } else if (state == _STATE_LAST) {
			if (capacity == 0) {
			    return pos;
			} // else continue
		    } else if (nextpos < filelen) {
			file.seek(nextpos);
			magic = file.readInt();
			if (magic == RECORD_MAGIC_NUMBER) {
			    return pos;
			} // continue to search
		    }
		}
		pos += INT_LEN;
	    } catch (IOException e) {
		// set position to file length to get out of the loop
		pos = filelen;
	    }
	}
	return pos;
    }

    // throw IllegalStateException if we are not opened and loaded
    protected void checkOpen() {
        if (!opened) {
            throw new IllegalStateException(
                SharedResources.getResources().getString(
                SharedResources.E_VRFILE_NOT_OPEN)+"["+backingFile+"]");
        }
    }

    // throw IllegalStateException if we are not opened and loaded
    // or if the file is of an earlier version and a write operation
    // is attempted
    protected void checkOpenAndWrite() {
	if (!opened) {
	    throw new IllegalStateException(
			SharedResources.getResources().getString(
			    SharedResources.E_VRFILE_NOT_OPEN));
	}

	if (fileversion < FILE_VERSION) {
	    throw new IllegalStateException(backingFile +
		"Cannot write to a file of version " + fileversion);
	}
    }

    // allocate a VRFileWarning if it has not been done so
    VRFileWarning getNewWarning() {
	if (warning == null) {
	    // No need to translate this string
	    warning = new VRFileWarning("Found bad record while loading " +
			backingFile + "(length=" + backingFile.length() + ")");
	}
	return warning;
    }

    /**
     * The following strings do not need to be translated.
     * They are warning messages logged in the broker log
     * when corrupted records are found in the backing file.
     */
    static StringBuffer getWarningPrefix(short code, long from,
	ByteBuffer header) {

	StringBuffer wstr = new StringBuffer(
		    "\n    Bad record found:");
	wstr.append("\n    =================");
	wstr.append("\n      At file position: " +
		    "\n        " + from);
	if (code != STATE_BAD_TRUNCATED_HEADER) {
	    // add loaded record header
	    header.rewind();
	    wstr.append("\n      Record header loaded:");
	    wstr.append("\n        magic number: " + header.getInt());
	    wstr.append("\n        capacity: " + header.getInt());
	    wstr.append("\n        state: " + header.getShort());
	}
	wstr.append("\n      Reason: "+ getReason(code));

	return wstr;
    }

    /**
     * The following strings do not need to be translated.
     * They are warning messages logged in the broker log
     * when corrupted records are found in the backing file.
     */
    static VRFileWarning addCompactWarning(VRFileWarning w, short code,
	long from, ByteBuffer header, long to) {

	StringBuffer wstr = getWarningPrefix(code, from, header);
	wstr.append("\n      Skipped to:" + to);

	w.addWarning(wstr.toString());
	return w;
    }

    /**
     * The following strings do not need to be translated.
     * They are warning messages logged in the broker log
     * when corrupted records are found in the backing file.
     */
    static VRFileWarning addWarning(VRFileWarning w, short code, long from,
	ByteBuffer header, VRecord r) {

	StringBuffer wstr = getWarningPrefix(code, from, header);

	if (r == null) {
	    wstr.append(
"\n      Resolution:" +
"\n        Could not find any valid records beyond this file position." +
"\n        Any data stored after this position is lost.");
	} else {
	    long to = from + r.getCapacity();

	    wstr.append(
"\n      Resolution:" +
"\n        A valid record is found at file position " + to + "." +
"\n        The block between " + from + " and " + to +
"\n        will be returned to the free record pool." +
"\n        Any data stored between them is lost.");
	}

	w.addWarning(wstr.toString());
	return w;
    }

    /**
     * The following strings do not need to be translated.
     * They are warning messages logged in the broker log
     * when corrupted records are found in the backing file.
     */
    static String getReason(short code) {
	switch (code) {
	    case STATE_BAD_MAGIC_NUMBER:
		return
"\n        bad magic number, expected " + RECORD_MAGIC_NUMBER;

	    case STATE_BAD_NEXT_MAGIC_NUMBER:
		return
"\n        bad magic number found at the next record, possibly due to" +
"\n        corrupted capacity or the next record was corrupted";

	    case STATE_BAD_STATE:
		return
"\n        bad state";

	    case STATE_BAD_CAPACITY:
		return
"\n        bad capacity - either found non-zero capacity in the last record" +
"\n        header or the capacity makes the record span beyond the end of file";

	    case STATE_BAD_CAPACITY_TOO_SMALL:
		return
"\n        bad capacity: it's less than the size of record header";

	    case STATE_BAD_TRUNCATED_HEADER:
		return
"\n        record header truncated; end of file reached";

	    default:
		return
"\n        unknown error code found: " + code;
	}
    }

/*
    public static void main(String args[]) throws Exception {
	if (args.length == 0) {
	    return;
	}

	VRFile vrfile = new VRFile(args[0]);

	vrfile.open();
	Set records = vrfile.getRecords();
	System.out.println("loaded "+records.size()+" records from "+
			args[0]);
    }
*/
  public long getThreshold() {

        return threshold;
    }

    public void setThreshold(long threshold) {

        if (DEBUG){
            System.out.println("VRFile.setThreshold: " + threshold);
        }

        if (threshold < 0) {
            throw new IllegalArgumentException(
                "Threshold must be postive. Illegal threshold: " + threshold);
        }
        this.threshold = threshold;

    }

    public float getThresholdFactor() {

        return thresholdFactor;
    }

    public void setThresholdFactor(float thresholdFactor) {

        if (DEBUG){
            System.out.println("VRFile.setThresholdFactor: " + thresholdFactor);
        }

        if (thresholdFactor < 0.0f) {
            throw new IllegalArgumentException(
                "Threshold factor must be postive. Illegal threshold factor: " + thresholdFactor);
        }

        this.thresholdFactor = thresholdFactor;

    }


	public boolean isThresholdReached(){

        //System.out.println("tomr: isThresholdReached: - fileSize = " + this.fileSize + " - threshold = " + this.threshold);
        if (this.threshold > 0 ){
            if (this.threshold > this.fileSize){
                //System.out.println("tomr:isThresholdReeached: not");
                return false;
            } else {
                //System.out.println("tomr:isThresholdReached: yes");
                return true;
            }
        }

        return false;
    }

        public void checkGrowthFactorSanity() throws IllegalStateException {

        //System.out.println("tomr:Checking for thresholdFactor & threshold ");

        //System.out.println("tomr:threshold = " + threshold);
        //System.out.println("tomr:thresholdFactor = " + thresholdFactor);

        if (threshold > 0 && thresholdFactor > 0){

            //System.out.println("tomr:Sanity check ok");

        } else {

            //System.out.println("tomr:Sanity failed");
            threshold = this.DEFAULT_THRESHOLD;
            thresholdFactor = this.DEFAULT_THRESHOLD_FACTOR;
            //String errorText = br.getString( br.X_BAD_PROPERTY_VALUE,"Error both threshold and threshold_factor should be greater than zero. Broker will continue with default values.");
            String errorText = "Illegal values. Both threshold and threshold_factor must be greater than zero. Broker will continue using default values.";
            throw new IllegalStateException(errorText);
        }
     }

		public long getFileCookie() {
			return fileCookie;
		}
}

