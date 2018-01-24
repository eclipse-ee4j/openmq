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
 * @(#)VRFileMap.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.messaging.jmq.resources.*;

/**
 * VRFileMap is a simple implementation of a variable sized record based file.
 * The implementation is optimized for simiplicity and speed at the expense
 * of diskspace.
 * <p>
 * A VRFileMap is backed by a disk file. The file is split into records as
 * records are requested by the caller. Record size is defined to be a
 * multiple of a block size to increase the likelyhood of record-reuse. 
 * Free'd records are tracked for re-use. Overtime it is possible for
 * holes to occur in the file of record sizes that are never re-used.
 * A compact() method is provided to compact() the backing file -- this
 * could be an expensive operation depending on the amount of data in the file.
 * <p>
 * The initial design will support no record splitting or coelesceing
 * (except via compact).
 * <p>
 * Nio is used for I/O to the file. In particular MappedByteBuffers are
 * used to reduce memory usage and data copies.
 *
 * <p>
 * How is it implemented?
 *
 * <p>
 *  JDK 1.4 java.nio MappedByteBuffer classe is used to map the backing
 *  file into memory and ByteBuffer.slice() is used to 
 *  create slices that are returned by allocate().
 *
 * <p>
 *  The implementation will not attempt to do ANY splitting or coelescing
 *  of buffers. Free'd buffers will simply be put on a free list and re-used
 *  if they are large enough to satisfy an allocate() request. This is
 *  based on the assumption that buffer sizes will not vary wildly for
 *  any given allocator.
 *
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
 * <p>
 * If the file is not large enough we grow it using the growth factor
 * and map in a new segment. A list of mapped segments must be maintained.
 * <p>
 * When compact() is called a new empty file is created and all records
 * whose state is not free are written to it. The file is then grown to be
 * of at least initialCapacity if need be. The old file is renamed to back
 * it up, and the new file is renamed to become the new backing store.
 * The free list is disgarded and the new file is loaded.
 * <p>
 * When a file is loaded it is mapped and the header is read to verify file
 * type and version. Then the file is scanned and records are placed on the
 * free and allocated lists as dictated by their state.
 *
 * Notes:
 *
 *  - Creating many MappedByteBuffers is expensive (creating a slice()
 *    of one should be cheap). That's why we want a relatively large
 *    initial file size and growth factor. Also MappedByteBuffers mappings
 *    are not closed until the objects are finalized.
 *
 * @see	VRFile.java
 */

public class VRFileMap extends VRFile {

    static final int EIGHT_K = 8192;

    private static boolean DEBUG = Boolean.getBoolean("vrfile.debug");


    // keep track of all MappedByteBuffer objects we have
    protected ArrayList mappedBuffers = null;
    protected MappedByteBuffer mbuffer = null;	// current mapped buffer

    // needed due to bug 4625907: record position of the first record in
    // each mapped buffer
    protected ArrayList startsAt = null;

    /**
     * Instantiate a VRFileMap object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     */
    public VRFileMap(File file) {
	this(file, DEFAULT_INITIAL_FILE_SIZE, false, false);
    }

    /**
     * Instantiate a VRFileMap object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     */
    public VRFileMap(String name) {
	this(new File(name), DEFAULT_INITIAL_FILE_SIZE, false, false);
    }

    /**
     * Instantiate a VRFileMap object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     * @parameter size	initial file size; when a new file is created, a
     *			file of this size is mapped
     */
    public VRFileMap(String name, long size, boolean isMinimumWrites, boolean interruptSafe) {
	this(new File(name), size, isMinimumWrites, interruptSafe);
    }

    /**
     * Instantiate a VRFileMap object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     * @parameter size	initial file size; when a new file is created, a
     *			file of this size is mapped
     */
    public VRFileMap(File file, long size, boolean isMinimumWrites, boolean interruptSafe) {
	super(file, size, isMinimumWrites, interruptSafe);

	mappedBuffers = new ArrayList(1);
	startsAt = new ArrayList(1);
    }

    /**
     * Open and load the backing file.
     * If the backing file does not exist, it will be created and it
     * size set to the initial file size. Otherwise, all records
     * (allocated or free) will be loaded in memory.
     */
    public synchronized void open() throws IOException, VRFileWarning {
	open(true);
    }

    private synchronized void open(boolean create)
	throws IOException, VRFileWarning {

	if (opened) return;

	RandomAccessFile raf = null;
	FileChannel fc = null;
	try {
	    raf = new RandomAccessFile(backingFile, "rw");
	    fc = raf.getChannel();
	    long fsize = raf.length();

	    if (fsize == 0) {
		if (create) {
		    initNewFile(raf, fc);
		} else {
		    return;
		}
	    } else {
		loadFile(raf, fc);
	    }

	    opened = true;

	    force();

	    if (DEBUG) {
		System.out.println("file version="+fileversion);
		System.out.println("number of allocated buffer loaded "
					+allocated.size());
		System.out.println("number of free buffer loaded "+numFree);
		System.out.println("safe="+safe);
		printMappedBuffers();
	    }

	    // make sure warning is thrown at last so that
	    // all processing can be done
	    if (warning != null) {
		throw warning;
	    }
	} finally {
	    if (fc != null) fc.close();
	    if (raf != null) raf.close();
	}
    }

    // Close the VRFileMap and free up any resources
    public synchronized void close() {
	if (!opened) return;

	if (DEBUG) {
	    System.out.println(backingFile + ": closing...");
	    System.out.println("filePointer = "+filePointer);
	    System.out.println("number of allocated buffers =" 
				+allocated.size());
	    System.out.println("number of free buffers = "+numFree);
	    printMappedBuffers();
	}

	force();

	mappedBuffers.clear();
	startsAt.clear();
	mbuffer = null;

	reset();
    }

    /**
     * Get a map of what the record layout of the file looks like. The
     * array represents the sequence of records. The value represents
     * the record's size (capacity). If the value is positive the record
     * allocated, if the value is negative the record is free.
     * WARNING! This may be an expensive operation.
     */
    public synchronized int[] getMap() throws IOException {

	if (!opened) {
	    try {
		open(false);
	    } catch (VRFileWarning w) {
		// the warning is saved and can be retrieved using
		// the getWarning() method
	    }
	}

	if (mappedBuffers.size() == 0) {
	    return (new int[0]);
	}

	int[] map = new int[allocated.size() + numFree];
	boolean done = false;
	for (int i = 0; i < mappedBuffers.size(); i++) {
	    MappedByteBuffer buf = (MappedByteBuffer)mappedBuffers.get(i);
	    int pos = buf.position();
	    int ptr = 0;

	    // skip to first record of the mapped buffer
	    ptr = ((Integer)startsAt.get(i)).intValue();
	    buf.position(ptr);

	    int index = 0;
	    while (!done && buf.hasRemaining()) {
		try {
		    //int magic = buf.getInt();
		    buf.getInt();
		    int cap = buf.getInt();
		    short state = adjustRecordState(fileversion,
							buf.getShort());

		    if ((ptr + cap) > buf.remaining()) {
			state = STATE_CUTOFF;
		    }

		    switch (state) {
		    case STATE_CUTOFF:
			done = true;
			break;
		    case STATE_ALLOCATED:
		    case STATE_PROPERTIES:
			map[index++] = cap;
			buf.position(ptr + cap);
			break;
		    case STATE_FREE:
			map[index++] = (0-cap);
			buf.position(ptr + cap);
			break;
		    case _STATE_LAST:
			done = true;
			break;
		    default:
			break;
		    }

		    ptr += cap;
		} catch (BufferUnderflowException e) {
		    // should not happen
		    e.printStackTrace();
		}
	    }
	    buf.position(pos);
	}

	return map;
    }

    /**
     * Allocate a record of at least size "size". The actual size allocated
     * will be a multiple of the block size and may be larger than requested.
     * The actual size allocated can
     * be determined by inspecting the returned records capacity().
     */
    public synchronized VRecord allocate(int size) throws IOException {

	checkOpenAndWrite();

	// allocateSize is a multiple of the blocksize
	int allocateSize =
		(size+RECORD_HEADER_SIZE+blockSize-1)/blockSize*blockSize;

	if (DEBUG) {
	    System.out.println("allocating " + allocateSize);
	}

	// get it from free list first
	VRecord record = findFreeRecord(allocateSize);
	if (record != null) {

	    record.allocate(STATE_ALLOCATED);
	    if (safe) {
		record.force();
	    }

	    numFree--;
	    hits++;

	    if (DEBUG) {
		System.out.println("allocate(): hit, requested " +
				size + ", allocated "+ record.getCapacity());
	    }
	} else {
	    // allocate one if cannot find a block of allocateSize in free list

	    if (numFree > 0) {
		misses++;
	    }

	    if (mbuffer.remaining() < allocateSize) {
		// grow file
		growfile(allocateSize + RECORD_HEADER_SIZE);
	    }

	    record = getNewSlice(allocateSize);
	}

	allocated.add(record);
	bytesAllocated += record.getCapacity();

	return record;
    }


    /**
     * Force all changes made to all records to be written to
     * disk. Note that the VRFileMap implementation may at times choose to
     * force data to disk independent of this method.
     */
    public synchronized void force() {
	checkOpen();

	int size = mappedBuffers.size();
	for (int i = 0; i < size; i++) {
	    MappedByteBuffer buf = (MappedByteBuffer)mappedBuffers.get(i);
	    buf.force();
	}
    }

    /**
     * Clear all records.
     */
    public synchronized void clear(boolean truncate) throws IOException {

    RandomAccessFile raf = null;
    try {

	// reset whole file
	if (opened) {

	    // get the first MappedByteBuffer
	    MappedByteBuffer buf = (MappedByteBuffer)mappedBuffers.get(0);
	    buf.position(0);
	    writeFileHeader(buf);
	    buf.put(lastRecordHeader);
	    buf.force();

	    mappedBuffers.clear();
	    allocated.clear();
	    freeMap.clear();

	    buf.clear();

	    if (fileSize > buf.capacity()) {
		fileSize = buf.capacity();

		// truncate the file
		raf = new RandomAccessFile(backingFile, "rw");
		raf.setLength(fileSize);
	    }

	    buf.position(FILE_HEADER_SIZE);
	    buf.limit(buf.capacity());
	    filePointer = FILE_HEADER_SIZE;
	    mappedBuffers.add(buf);
	    mbuffer = buf;

	    force();
	} else {

	    // since the file has not been opened and loaded yet,
	    // just truncate the file if it exists
	    if (backingFile.exists()) {
		raf = new RandomAccessFile(backingFile, "rw");
		raf.setLength(0);

		// bug 5042763:
		// don't sync meta data for performance reason
		raf.getChannel().force(false);

	    }
	}

    } finally {
    if (raf != null) {
        raf.close();
    }
    }

    }

    public String toString() {
	return ("VRFileMap:" + backingFile + ":# of buffers=" + allocated.size()
		+ ":# of free buffers=" + numFree + ":# of MappedByteBuffer="
		+ mappedBuffers.size());
    }

    // initialize a new file:
    // set the size to be initialFileSize
    // write file header
    // write last record header
    // set file pointer
    private void initNewFile(RandomAccessFile raf, FileChannel fc)
	throws IOException {

	if (DEBUG) {
	    System.out.println("Creating new backing file with initial "+
			"size " + initialFileSize);
	}

	this.fileSize = initialFileSize;
	raf.setLength(this.fileSize);

	// map in the whole file
	mbuffer = fc.map(FileChannel.MapMode.READ_WRITE, 0, this.fileSize); 
	mappedBuffers.add(mbuffer);

	// write header
	writeFileHeader(mbuffer);

	// write last record info
	mbuffer.put(lastRecordHeader);
	mbuffer.position(FILE_HEADER_SIZE);
	startsAt.add(Integer.valueOf(FILE_HEADER_SIZE));
	filePointer = FILE_HEADER_SIZE;
    }

    // map the buffers as we load
    private void loadFile(RandomAccessFile raf, FileChannel fc)
	throws IOException {

	if (DEBUG) {
	    System.out.println("Loading backing file with size "+
			raf.length());
	}

	// check header: read in version 1 header first
	byte[] barray = new byte[FILE_HEADER_SIZE_1];
	int num = raf.read(barray);
	if (num != FILE_HEADER_SIZE_1) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_UNRECOGNIZED_VRFILE_FORMAT,
			backingFile));
	}
	short fversion = checkFileHeader(ByteBuffer.wrap(barray));
	if (fversion == FILE_VERSION) {
	    // to advance the fileptr pass the properties record index
	    //long propptr = raf.readLong();
	    raf.readLong();
	}

	// existing file: map as we load records
	boolean done = false;
	long offset = 0;
	long left = raf.length();

	// file size or Integer.MAX_VALUE whichever is smaller
	long mapsize = (left <= Integer.MAX_VALUE) ? left : Integer.MAX_VALUE;

	// for the first mapped buffer, we need to set the position
	// after the file header
	boolean firstbuf = true;

	while (!done) {

	    // a mapped region needs to start on multiple of 8k
	    // otherwise MappedByteBuffer.force() will fail
	    // with IOException, see bug 4625907
	    // round it up a page boundary
	    // this is also the reason we cannot map the first buffer
	    // after the file header
	    long cut = (offset/EIGHT_K)*EIGHT_K;

	    int bufptr = 0;
	    if (left > mapsize) {
		mbuffer = fc.map(FileChannel.MapMode.READ_WRITE, cut,
					mapsize); 

		if (firstbuf) {
		    int headeroffset = (fileversion == FILE_VERSION) ?
					FILE_HEADER_SIZE : FILE_HEADER_SIZE_1;
		    mbuffer.position(headeroffset);
		    firstbuf = false;
		} else {
		    if (cut < offset) {
			// set position of new buffer to the offset
			int index = (int)(offset - cut);
			mbuffer.position(index);
		    }
		}
		startsAt.add(Integer.valueOf(mbuffer.position()));

		// false means this is not the last mapped buffer
		bufptr = load(mbuffer, false);
		mbuffer.limit(bufptr);

		int added = bufptr - (int)(offset - cut);
		offset += added;
		left -= added;
	    } else {
		if (!firstbuf && (left < initialFileSize)) {
		    // if this is not the first mapped buffer,
		    // extend the file if what's left to be mapped is
		    // less than the initialFileSize; this makes sure
		    // that the last mapped buffer is not too small
		    long fsize = offset + initialFileSize;
		    raf.setLength(fsize);
		    left = initialFileSize;
		}
		mbuffer = fc.map(FileChannel.MapMode.READ_WRITE, cut, left);

		if (firstbuf) {
		    int headeroffset = (fileversion == FILE_VERSION) ?
					FILE_HEADER_SIZE : FILE_HEADER_SIZE_1;
		    mbuffer.position(headeroffset);
		    firstbuf = false;
		} else {
		    if (cut < offset) {
			// set position of new buffer to the offset
			int index = (int)(offset - cut);
			mbuffer.position(index);
		    }
		}
		startsAt.add(Integer.valueOf(mbuffer.position()));

		// true means last mapped buffer
		bufptr = load(mbuffer, true);

		int added = bufptr - (int)(offset - cut);
	    	this.filePointer = offset + added;

		done = true;
	    }
	    mappedBuffers.add(mbuffer);

	}

	this.fileSize = raf.length();
    }

    // load all records from the given MappedByteBuffer
    private int load(MappedByteBuffer mbuf, boolean last)
	throws StreamCorruptedException, IOException {

	boolean done = false;

	// to store the record header for composing warning message
	ByteBuffer recordheader = ByteBuffer.wrap(new byte[RECORD_HEADER_SIZE]);

	// scan for slices
	int current = mbuf.position();
	while (!done && mbuf.hasRemaining()) {

	    short state = STATE_FREE;

	    ByteBuffer slice = null;
	    VRecord record = null;

	    // case 0:
	    // the case where the record's header was cut off at the 
	    // end of the mapped buffer
	    if (mbuf.remaining() < RECORD_HEADER_SIZE) {
		if (!last) {
		    break;
		} else {
		    state = STATE_BAD_TRUNCATED_HEADER;
		}
	    } else {
		state = checkRecord(mbuf, recordheader);
	    }

	    switch (state) {
	    case STATE_ALLOCATED:
	    case STATE_FREE:
		mbuf.position(current);
		slice = mbuf.slice();
	        record = new VRecordMap(this, mbuf, slice);
		int capacity = record.getCapacity();

		if (state == STATE_ALLOCATED) {
		    allocated.add(record);
		    bytesAllocated += capacity;
		} else {
		    // true->insert free record in the begining of the list
		    putFreeList(record, true);
		}

		current += capacity;
		mbuf.position(current);
		break;

	    case _STATE_LAST:
		mbuf.position(current);
		done = true;
		break;

	    case STATE_CUTOFF:
		mbuf.position(current);
		if (!last && current != 0) {
		    done = true;
		} else {
		    current = handleBadRecord(STATE_BAD_CAPACITY,
						recordheader,
						mbuf, last);
		    mbuf.position(current);
		}
		break;

	    case STATE_BAD_MAGIC_NUMBER:
	    case STATE_BAD_NEXT_MAGIC_NUMBER:
	    case STATE_BAD_STATE:
	    case STATE_BAD_CAPACITY:
	    case STATE_BAD_CAPACITY_TOO_SMALL:
	    case STATE_BAD_TRUNCATED_HEADER:

		mbuf.position(current);
		current = handleBadRecord(state, recordheader, mbuf, last);
		mbuf.position(current);
		break;

	    case STATE_PROPERTIES:
		// don't have this kind of record yet
		break;
	    }
	}
	return current;
    }

    private short checkRecord(ByteBuffer buf, ByteBuffer recordheader) {

	int position = buf.position();
	int magic = buf.getInt();
	int capacity = buf.getInt();
	short statefromfile = buf.getShort();

	// save header loaded from file
	recordheader.rewind();
	recordheader.putInt(magic);
	recordheader.putInt(capacity);
	recordheader.putShort(statefromfile);

	short state = adjustRecordState(fileversion, statefromfile);

	if (magic != RECORD_MAGIC_NUMBER)
	    return STATE_BAD_MAGIC_NUMBER;

	if (state == STATE_BAD_STATE)
	    return state;

	// check the next magic number as well
	if (state == _STATE_LAST) {
	    if (capacity != 0) {
		return STATE_BAD_CAPACITY;
	    } else {
		return state;
	    }
	} else if (capacity <= RECORD_HEADER_SIZE) {
	    return STATE_BAD_CAPACITY_TOO_SMALL;
	} else if (position + capacity == buf.limit()) {
	    // we are the last record in this mapped buffer
	    return state;
	} else if (((long)position + (long)capacity) > (long)buf.limit()) {
	    return STATE_CUTOFF; // real bad record or record cut off at the
			      // end of the mapped buffer
	} else if ((buf.limit() - position - capacity) > 4) {
	    // make sure we have 4 bytes to read
	    magic = buf.getInt(position+capacity);

	    if (magic != RECORD_MAGIC_NUMBER) {
		return STATE_BAD_NEXT_MAGIC_NUMBER;
	    } else {
		return state;
	    }
	} else {
	    // assume it's good
	    return state;
	}
    }

    /**
     * The bad section will be marked as a free record if a good record
     * is found; or as a last record if no good record is found and this
     * is the last MappedByteBuffer.
     * @return the position of the good record or limit of the buffer if
     *		none is found
     */
    private int handleBadRecord(short errcode, ByteBuffer h,
	MappedByteBuffer mbuf, boolean last) throws IOException {

	if (DEBUG) {
	    System.out.println("bad record found at "+mbuf.position()
				+ " in " + mbuf);
	}

	int current = mbuf.position();
	int pos = findGoodRecord(mbuf);
	mbuf.position(current); // set pos back to beginning of bad record

	if (pos == mbuf.limit() && last) {
	    if (mbuf.remaining() >= RECORD_HEADER_SIZE) {
		// if this is the last record in the last mapped buffer;
		// mark this as last record instead of a free record
		mbuf.put(lastRecordHeader);
	    } else {
		// No last record will be marked; the file will be
		// grown when a new record is allocated
		mbuf.limit(current);
	    }

	    addWarning(getNewWarning(), errcode, current, h, null);
	    return current;
	} else {
	    ByteBuffer slice = mbuf.slice();
	    VRecord record = new VRecordMap(this, mbuf, slice, (pos - current));
	    record.free();

	    // true->insert free record in the begining of the list
	    putFreeList(record, true);

	    addWarning(getNewWarning(), errcode, current, h, record);
	    return pos;
	}
    }

    /**
     * Get a new slice of the specified size.
     * Caller should have checked that we have enough space to do this.
     */
    private VRecord getNewSlice(int size) throws IOException {
	// bytes left after this allocation
	int bytesLeft = mbuffer.remaining() - size;

	int newPosition = mbuffer.position() + size;

	// slice it again
	ByteBuffer buf = mbuffer.slice();
	buf.limit(size);
	VRecord record = new VRecordMap(this, mbuffer, buf, size);

	mbuffer.position(newPosition);
	filePointer += size;

	if (DEBUG) {
	    System.out.println("getNewSlice("+size+"):");
	    System.out.println("Slice at "+(newPosition-size)+" on " + mbuffer);
	    System.out.println("filePointer advanced to "+filePointer);
	}

	// write the last Record header
	mbuffer.put(lastRecordHeader);
	mbuffer.position(newPosition);

	if (safe) {
	    record.force();
	}

	return record;
    }

    /**
     * Grow the file and map in the new section.
     */
    private void growfile(int needSize) throws IOException {
	if (DEBUG) {
	    System.out.println("growfile(): need to grow file; "+
				"remaining = "+ mbuffer.remaining() +
				"; need = "+needSize);
	}

	long newfileSize = 0;

    	if (isThresholdReached()){

    		newfileSize = (long)(fileSize + (getThreshold() * getThresholdFactor()));

    	} else {

    		newfileSize = (long)(fileSize + fileSize * getGrowthFactor());
    	}

	int remaining = mbuffer.remaining() + (int)(newfileSize-fileSize);

	while (remaining < needSize) {
            // make sure we grow enough to accommodate required size

            if (isThresholdReached()){

                newfileSize = (long)(newfileSize + (getThreshold() * getThresholdFactor()));

            } else {

                newfileSize = (long)(newfileSize + newfileSize * getGrowthFactor());
            }

            remaining = mbuffer.remaining() + (int)(newfileSize-fileSize);


    }

	// a mapped region needs to start on multiple of 8k
	// otherwise MappedByteBuffer.force() will fail
	// with IOException, see bug 4625907
	// round it up a page boundary
	long cut = (filePointer/EIGHT_K)*EIGHT_K;

	// since we cannot map a size that is > than Integer.MAX_VALUE
	// need to make sure we dont grow more than that at a time
	if ((newfileSize - cut) > Integer.MAX_VALUE) {
	    newfileSize = cut + Integer.MAX_VALUE;
	}

	RandomAccessFile raf = null;
	FileChannel fc = null;
	long mapsize = 0;

	try {
	    raf = new RandomAccessFile(backingFile, "rw");

	    // grow the file to new size
	    raf.setLength(newfileSize);

	    fc = raf.getChannel();


	    mapsize = (newfileSize - cut);

	    MappedByteBuffer newbuf = fc.map(FileChannel.MapMode.READ_WRITE,
					cut, mapsize); 
	    mappedBuffers.add(newbuf);

	    if (cut < filePointer) {
		// set position of new buffer to current file pointer
		int index = (int)(filePointer - cut);
		newbuf.position(index);
		startsAt.add(Integer.valueOf(index));
	    }
	    mbuffer.limit(mbuffer.position());

	    if (DEBUG) {
		System.out.println("number of records allocated="
					+allocated.size());
		System.out.println("growing file from "+fileSize+" to "+
					newfileSize);
		System.out.println("old buffer ="+mbuffer);
		System.out.println("new mapped buffer =" + newbuf);
		System.out.println("new mapped buffer starts at "+cut);
		System.out.println("filePointer = "+filePointer);
	    }

	    mbuffer = newbuf;
	    this.fileSize = newfileSize;
	} catch (IOException e) {

	    if (DEBUG) {
	    	System.out.println(e);
		e.printStackTrace();
		System.out.println("file position= "+filePointer);
		System.out.println("current file size= "+fileSize);
		System.out.println("new file size= "+newfileSize);
		System.out.println("tried to map from "+cut+" for "+mapsize);
		printMappedBuffers();
	    }

	    if (raf != null) {
		// reset file back to original size
		raf.setLength(fileSize);
	    }

	    throw e;

	} catch (RuntimeException e) {
	    if (DEBUG) {
	    	System.out.println(e);
		e.printStackTrace();
		System.out.println("file position= "+filePointer);
		System.out.println("current file size= "+fileSize);
		System.out.println("new file size= "+newfileSize);
		System.out.println("tried to map from "+cut+" for "+mapsize);
		printMappedBuffers();
	    }

	    throw new IOException(e.getMessage());

	} finally {
	    if (fc != null) fc.close();
	    if (raf != null) raf.close();
	}
    }

    /**
     * Starting from buf.position, scan for a good record.
     * First find the record magic number and get the capacity.
     * A good record will be one with a record magic number after
     * the end of it (indicating another record); or it's the LAST
     * record
     * @return the position of the good record or limit of the buffer if
     *		none is found
     */
    private int findGoodRecord(MappedByteBuffer buf) {

	int current = buf.position();
	int start = current;
	while (buf.hasRemaining()) {
	    try {
		int magic = buf.getInt();

		if (magic == RECORD_MAGIC_NUMBER) {
		    int capacity = buf.getInt();
		    short state = adjustRecordState(fileversion, buf.getShort());

		    int nextpos = current + capacity;

		    if (state == STATE_BAD_STATE) {
			; // continue
		    } else if ((state != _STATE_LAST)
			&& (capacity <= RECORD_HEADER_SIZE)) {
			; // continue;
		    } else if (state == _STATE_LAST) {
			if (capacity == 0) {
			    return current;
			} // else continue
		    } else if (nextpos == buf.limit()) {
			// assumes that this is good
			return current;
		    } else if (nextpos > buf.limit()) {
			if ((current - start) > RECORD_HEADER_SIZE) {
			    return current;	// treat it as a cutoff
			} // else continue
		    } else {
			magic = buf.getInt(nextpos);
			if (magic == RECORD_MAGIC_NUMBER) {
			    return current;
			} // continue to search
		    }
		}
		current += INT_LEN;
		buf.position(current);
	    } catch (IndexOutOfBoundsException e) {
		// set position to limit of the mapped buffer to get out of
		// the loop
		buf.position(buf.limit());
		current = buf.limit();
	    } catch (BufferUnderflowException e) {
		// set position to limit of the mapped buffer to get out of
		// the loop
		buf.position(buf.limit());
		current = buf.limit();
	    }
	}
	return current;
    }

    private void printMappedBuffers() {
	System.out.println("mapped buffers:");
	for (int i = 0; i < mappedBuffers.size(); i++) {
	    System.out.println((MappedByteBuffer)mappedBuffers.get(i));
	}
    }

/*
    public static void main(String args[]) throws Exception {
	if (args.length == 0) {
	    return;
	}

	VRFileMap vrfile = new VRFileMap(args[0]);

	vrfile.open();
	Set records = vrfile.getRecords();
	System.out.println("loaded "+records.size()+" records from "+
			args[0]);
    }
*/
}

