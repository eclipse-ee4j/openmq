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
 * @(#)VRFileRAF.java	1.11 08/28/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.messaging.jmq.resources.*;

/**
 * THIS CLASS IMPLEMENTS THE VRFILE INTERFACE USING
 * RamdomAccessFile.
 *
 * @see VRFile.java
 */

public class VRFileRAF extends VRFile {

    private static boolean DEBUG = Boolean.getBoolean("vrfile.debug");


    private RandomAccessFile myRAF = null; // always keep open????
    private FileChannel myChannel = null; // always keep open????

    /**
     * Instantiate a VRFileRAF object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     */
    public VRFileRAF(File file) {
	this(file, DEFAULT_INITIAL_FILE_SIZE, false, false);
    }

    /**
     * Instantiate a VRFileRAF object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     */
    public VRFileRAF(String name) {
	this(new File(name), DEFAULT_INITIAL_FILE_SIZE, false, false);
    }

    /**
     * Instantiate a VRFileRAF object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     * @parameter size	initial file size; when a new file is created, a
     *			file of this size is mapped
     */
    public VRFileRAF(String name, long size, boolean isMinimumWrites, boolean interruptSafe) {
	this(new File(name), size, isMinimumWrites, interruptSafe);
    }

    /**
     * Instantiate a VRFileRAF object with the specified file as the backing
     * file.
     * @parameter file	the backing file
     * @parameter size	initial file size; when a new file is created, a
     *			file of this size is mapped
     */
    public VRFileRAF(File file, long size, boolean isMinimumWrites, boolean interruptSafe) {
	super(file, size, isMinimumWrites, interruptSafe);
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

	myRAF = new RandomAccessFile(backingFile, "rw");
	myChannel = myRAF.getChannel();
	long fsize = myRAF.length();

	if (fsize == 0) {
	    if (create) {
		initNewFile(myRAF);
	    } else {
		return;
	    }
	} else {
	    loadFile(myRAF);
	}
	this.fileSize = myRAF.length();

	opened = true;

	force();

	if (DEBUG) {
	    System.out.println("file version="+fileversion);
	    System.out.println("number of allocated buffer loaded "
					+allocated.size());
	    System.out.println("number of free buffer loaded "+numFree);
	    System.out.println("safe="+safe);
	}

	// make sure warning is thrown at last so that
	// all processing can be done
	if (warning != null) {
	    throw warning;
	}
    }

    // Close the VRFile and free up any resources
    public synchronized void close() {
	if (!opened) return;

	if (DEBUG) {
	    System.out.println(backingFile + ": closing...");
	    System.out.println("filePointer = "+filePointer);
	    System.out.println("number of allocated buffers =" 
				+allocated.size());
	    System.out.println("number of free buffers = "+numFree);
	}

	try {
	    force();
	    myChannel.close();
	    myRAF.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
	myRAF = null;

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

	if ((allocated.size() + numFree) == 0) {
	    return (new int[0]);
	}

	int[] map = new int[allocated.size() + numFree];
	int offset = (fileversion == FILE_VERSION) ?
				FILE_HEADER_SIZE : FILE_HEADER_SIZE_1;
	long frompos = offset;
	boolean done = false;

	int index = 0;
	while (!done) {
	    myRAF.seek(frompos);
	    try {
		//int magic = myRAF.readInt();
		myRAF.readInt();
		int cap = myRAF.readInt();
		short state = adjustRecordState(fileversion,
						myRAF.readShort());

		switch (state) {
		case STATE_ALLOCATED:
		case STATE_PROPERTIES:
		    map[index++] = cap;
		    break;
		case STATE_FREE:
		    map[index++] = (0-cap);
		    break;
		case _STATE_LAST:
		    done = true;
		    break;
		default:
		    break;
		}
		frompos += cap;
	    } catch (IOException e) {
		// should not happen
		e.printStackTrace();
	    }
	}

	return map;
    }

    /**
     * Allocate a record of at least size "size". The actual size allocated
     * will be a multiple of the block size and may be larger than requested.
     * The actual size allocated can
     * be determined by inspecting the returned records capacity().
     * Write the data into the allocated record
     * 
     * data includes
     */
    public synchronized VRecord allocateAndWrite(int size, byte[] data) throws IOException {
    	checkOpenAndWrite();

    	// here the data array also includes 
    	//  short : the record state
    	//  short : reserved
    	
    	// allocateSize is a multiple of the blocksize
    	int allocateSize =
    		(size+RECORD_HEADER_SIZE+blockSize-1)/blockSize*blockSize;

    	if (DEBUG) {
    	    System.out.println("allocating " + allocateSize +" size="+size + " dataSize="+data.length);
    	}

    	// get it from free list first
    	VRecordRAF record = (VRecordRAF)findFreeRecord(allocateSize);
    	if (record != null) {

    	    if (DEBUG) {
    		System.out.println("reuse: "+record);
    	    }
    	    
    	    record.allocateAndWrite(data);
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

    	    if (remaining() < allocateSize) {
    		// grow file
    		growfile(allocateSize + RECORD_HEADER_SIZE);
    	    }

    	   // record = (VRecordRAF)getNewSlice(allocateSize);
    	   // record.allocateAndWrite(data);
    	    
    	    record = (VRecordRAF)getNewSliceAndWrite(allocateSize,data);
    	  
    	    
    	}

    	putAllocatedList(record);

    	if (DEBUG) {
    	    System.out.println("allcoated record: "+record);
    	}
    	
    	
    	
    	return record;
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

	    if (DEBUG) {
		System.out.println("reuse: "+record);
	    }
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

	    if (remaining() < allocateSize) {
		// grow file
		growfile(allocateSize + RECORD_HEADER_SIZE);
	    }

	    record = getNewSlice(allocateSize);
	}

	putAllocatedList(record);

	if (DEBUG) {
	    System.out.println("allcoated record: "+record);
	}
	return record;
    }

    /**
     * Calling method is responsible for synchronize on VRFile.
     */
    private long remaining() {
	return (fileSize - filePointer);
    }

    /**
     * Force all changes made to all records to be written to
     * disk. Note that the VRFile implementation may at times choose to
     * force data to disk independent of this method.
     */
    public synchronized void force() throws IOException {
        try {
	    checkOpen();
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        }

        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.getFD().sync();
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
	    // bug 5042763:
	    // use FileChannel.force(false) to improve file sync performance
	    myChannel.force(false);
        }
    }

    /**
     * Clear all records.
     */
    public synchronized void clear(boolean truncate) throws IOException {
	// reset whole file
	if (opened) {

	    reset();
	    opened = true;

	    if (truncate) {
		// truncate file to initial file size
		myRAF.seek(0);
		initNewFile(myRAF);
		fileSize = myRAF.length();
	    } else {
		myRAF.seek(0);
		writeFileHeader(myRAF);
		writeLastRecordHeader(myRAF);

		filePointer = FILE_HEADER_SIZE;
	    }
	    force();
	} else {

	    if (!backingFile.exists()) {
		// nothing to do
		return;
	    }

	    // since the file has not been opened and loaded yet,
	    // just truncate the file if it exists
	    RandomAccessFile raf = new RandomAccessFile(backingFile, "rw");
	    if (truncate) {
		raf.setLength(0);
	    } else {
		writeFileHeader(raf);
		writeLastRecordHeader(raf);
	    }

	    // bug 5042763:
	    // don't sync meta data for performance reason
	    raf.getChannel().force(false);

	    raf.close();
	}
    }

    public String toString() {
	return ("VRFileRAF:" + backingFile + ":# of buffers=" + allocated.size()
		+ ":# of free buffers=" + numFree + ":file pointer="
		+ filePointer);
    }

    // initialize a new file:
    // set the size to be initialFileSize
    // write file header
    // write last record header
    // set file pointer
    private void initNewFile(RandomAccessFile raf) throws IOException {

	if (DEBUG) {
	    System.out.println("Creating new backing file with initial "+
			"size " + initialFileSize);
	}

	raf.setLength(initialFileSize);

	// write header
	writeFileHeader(raf);

	// write last record info
	writeLastRecordHeader(raf);
	filePointer = FILE_HEADER_SIZE;
    }

    // scan the file for allocated and free records
    private void loadFile(RandomAccessFile raf) throws IOException {

	if (DEBUG) {
	    System.out.println("Loading backing file with size "+
			raf.length());
	}

	// check header: read in version 1 header first
	byte[] bbuf = new byte[FILE_HEADER_SIZE_1];
	int num = raf.read(bbuf);
	if (num != FILE_HEADER_SIZE_1) {
	    throw new IOException(
		SharedResources.getResources().getString(
			SharedResources.E_UNRECOGNIZED_VRFILE_FORMAT,
			backingFile, Integer.valueOf(num)));
	}
	short fversion = checkFileHeader(ByteBuffer.wrap(bbuf));
	if (fversion == FILE_VERSION) {
	    // to advance the fileptr pass the properties record index
	    long propptr = raf.readLong();
	}

	// existing file: load records
	boolean done = false;
	long frompos = raf.getFilePointer();
	long filelength = raf.length();
	ByteBuffer recordheader = ByteBuffer.wrap(new byte[RECORD_HEADER_SIZE]);

	while (!done) {
	    int capacity = 0;
	    VRecord record = null;

	    raf.seek(frompos);
	    short state = getRecordState(raf, recordheader, frompos,
				filelength);
	    switch (state) {
	    case STATE_ALLOCATED:
	    case STATE_FREE:
		capacity = recordheader.getInt(RECORD_CAPACITY_OFFSET);
		record = new VRecordRAF(this, frompos, capacity, state); 
		if (state == STATE_ALLOCATED) {
		    if (DEBUG) {
			System.out.println("loaded record:" + record);
		    }
		    putAllocatedList(record);
		} else {
		    if (DEBUG) {
			System.out.println("loaded free record:" + record);
		    }
		    // true->insert free record in the begining of the list
		    putFreeList(record, true);
		}

		capacity = recordheader.getInt(RECORD_CAPACITY_OFFSET);
		break;

	    case _STATE_LAST:
		done = true;
		break;

	    case STATE_BAD_MAGIC_NUMBER:
	    case STATE_BAD_NEXT_MAGIC_NUMBER:
	    case STATE_BAD_STATE:
	    case STATE_BAD_CAPACITY:
	    case STATE_BAD_CAPACITY_TOO_SMALL:
	    case STATE_BAD_TRUNCATED_HEADER:

		// skip over the bad ones and just continue with the
		// next good one if one can be found
		long nextstart = findGoodRecord(raf, frompos, filelength);
		if (nextstart == filelength) {
		    // no more good record found
		    raf.seek(frompos);
		    writeLastRecordHeader(raf);
		    done = true;
		    addWarning(getNewWarning(), state, frompos,
					recordheader, null);
		} else {
		    capacity = (int)(nextstart - frompos);
		    record = new VRecordRAF(this, frompos, capacity,
					STATE_FREE, true);

		    // true->insert free record in the begining of the list
		    putFreeList(record, true);

		    addWarning(getNewWarning(), state, frompos,
					recordheader, record);
		}
		break;

	    case STATE_PROPERTIES:
		// we don't have properties record yet
		break;
	    }

	    if (done) {
		raf.seek(frompos);
		this.filePointer = frompos;
	    } else {
		frompos += capacity;
	    }
	}
    }

    /**
     * Get a new slice of the specified size.
     * Caller should have checked that we have enough space to do this.
     * Calling method is responsible for synchronize on VRFile.
     */
    private VRecord getNewSlice(int size) throws IOException {
	// bytes left after this allocation
	long bytesLeft = remaining() - (long)size;

	long newPosition = filePointer + (long)size;

	// slice it again
	VRecord record = new VRecordRAF(this, filePointer, size,
					STATE_ALLOCATED, true);

	filePointer += size;

	if (DEBUG) {
	    System.out.println("getNewSlice("+size+"):");
	    System.out.println("filePointer advanced to "+filePointer);
	}

	// write the last Record header
	myRAF.seek(filePointer);
	writeLastRecordHeader(myRAF);

	if (safe) {
	    record.force();
	}

	return record;
    }
    
    /**
     * Get a new slice of the specified size and allocate with provided data.
     * Caller should have checked that we have enough space to do this.
     * Calling method is responsible for synchronize on VRFile.
     */
    private VRecord getNewSliceAndWrite(int size, byte[] data) throws IOException {
	// bytes left after this allocation
	long bytesLeft = remaining() - (long)size;

	long newPosition = filePointer + (long)size;

	// slice it again and write the last Record header
	VRecordRAF record = new VRecordRAF(this, filePointer, size,
					STATE_ALLOCATED, data, lastRecordHeader);

	filePointer += size;

	if (DEBUG) {
	    System.out.println("getNewSliceAndWrite("+size+"):");
	    System.out.println("filePointer advanced to "+filePointer);
	}

	if (safe) {
	    record.force();
	}	

	return record;
    }
    
    

    /**
     * Grow the file and map in the new section.
     * Calling method is responsible for synchronize on VRFile.
     */
    private void growfile(int needSize) throws IOException {
	if (DEBUG) {
	    System.out.println("growfile(): need to grow file; "+
				"remaining = "+ remaining() +
				"; need = "+needSize);
	}

	// find size to grow
	// Bug 6431962 - Tom Ross
        // changed the varibale usage (growthFactor) to method call
        //
        // find size to grow
	long newfileSize = 0;
	if (isThresholdReached()){

            //System.out.println("growfile(): threshold has been reached");
            newfileSize = (long)(fileSize + (getThreshold() * getThresholdFactor()));

        } else {

            //System.out.println("gro/wfile(): threshold has not been reached");
            newfileSize = (long)(fileSize + fileSize * getGrowthFactor());
        }
	
	long remaining = remaining() + (newfileSize-fileSize);

	while (remaining < needSize) {
	    // make sure we grow enough to accommodate required size
		if (isThresholdReached()){
	    		newfileSize = (long)(newfileSize + (getThreshold() * getThresholdFactor() ) );
	        } else {

			newfileSize = (long)(newfileSize + newfileSize * getGrowthFactor());	    	
		}
		
		remaining = remaining() + (newfileSize-fileSize);
		if ( DEBUG){

                	System.out.println("Remaing = " + remaining() + " needSize = " + needSize);

            	}
	}


	try {
	    // grow the file to new size
	    myRAF.setLength(newfileSize);
	    fileSize = newfileSize;

	} catch (IOException e) {

	    if (DEBUG) {
	    	System.out.println(e);
		e.printStackTrace();
		System.out.println("file position= "+filePointer);
		System.out.println("current file size= "+fileSize);
		System.out.println("new file size= "+newfileSize);
	    }
	    throw e;
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

    FileChannel getChannel() {
	checkOpen();

	return myChannel;
    }

    synchronized int writeByteBuffer(long pos, ByteBuffer bbuf)
	throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:writeByteBuffer writing "
		+ bbuf.remaining() + " bytes from " + pos);
	}
        if (interruptSafe) {
            int p = bbuf.position();
            int m = bbuf.remaining();
            byte[] bytes = new byte[m];
            bbuf.get(bytes);
            writeData(pos, bytes);
            bbuf.position(p+m);
            return m;
        } else {
	    return myChannel.write(bbuf, pos);
        }
    }

    synchronized int readByteBuffer(long pos, ByteBuffer bbuf)
	throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:readByteBuffer reading "
		+ bbuf.remaining() + " bytes from " + pos);
	}
        if (interruptSafe) {
            int m = bbuf.remaining();
            byte[] bytes = new byte[m];
            read(pos, bytes, 0, m);
            bbuf.put(bytes);
            return m;
        } else {
	    return myChannel.read(bbuf, pos);
        }
    }
    
    synchronized void writeData(long pos, byte[] data)
	throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:writeData writing "
		+ data.length + " bytes from " + pos);
	}
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        myRAF.write(data);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
	    myRAF.seek(pos);
	    myRAF.write(data);
        }
	
    }

    synchronized void writeShort(long pos, short v) throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:writeShort writing "
		+ v + " from " + pos);
	}
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        myRAF.writeShort(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
	    myRAF.seek(pos);
	    myRAF.writeShort(v);
        }

	/*
	assert (myRAF.getFilePointer() == (pos + 2)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 2));
	*/
    }

    synchronized int readInt(long pos) throws IOException {
	int data = -1;
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        data = myRAF.readInt();
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            data = myRAF.readInt();
        }

	if (DEBUG) {
	    System.out.println("VRFileRAF:readInt read "
		+ data + " from " +pos);
	}

	/*
	assert (myRAF.getFilePointer() == (pos + 4)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 4));
	*/
	return data;
    }

    synchronized short readShort(long pos) throws IOException {
	short data = -1;
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        data = myRAF.readShort();
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            data = myRAF.readShort();
        }

	if (DEBUG) {
	    System.out.println("VRFileRAF:readShort read "+data+" from "+pos);
	}

	/*
	assert (myRAF.getFilePointer() == (pos + 2)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 2));
	*/
	return data;
    }

    synchronized long readLong(long pos) throws IOException {
	long data = -1L;
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        data = myRAF.readLong();
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            data = myRAF.readLong();
        }

	if (DEBUG) {
	    System.out.println("VRFileRAF:readLong read "+data+" from "+pos);
	}

	/*
	assert (myRAF.getFilePointer() == (pos + 8)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 8));
	*/
	return data;
    }

    synchronized void writeLong(long pos, long v) throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:writeLong writing "+v+" from "+pos);
	}
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        myRAF.writeLong(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
	    myRAF.seek(pos);
	    myRAF.writeLong(v);
        }

	/*
	assert (myRAF.getFilePointer() == (pos + 8)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 8));
	*/
    }

    synchronized void writeInt(long pos, int v) throws IOException {
	if (DEBUG) {
	    System.out.println("VRFRAF:writeInt writing "+v+" from "+pos);
	}
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
                myRAF.writeInt(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            myRAF.writeInt(v);
        }

	/*
	assert (myRAF.getFilePointer() == (pos + 4)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + 4));
	*/
    }

    void write(long pos, byte[] buf) throws IOException {
	write(pos, buf, 0, buf.length);
    }

    synchronized void write(long pos, byte[] buf, int offset, int len)
        throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:write writing " + len
		+ " bytes from the byte array at offset " + offset 
		+ " from file position " + pos);
	}
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
                myRAF.write(buf, offset, len);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            myRAF.write(buf, offset, len);
        }

	/*
	assert (myRAF.getFilePointer() == (pos + len)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + len));
	*/
    }

    int read(long pos, byte[] buf) throws IOException {
	return read(pos, buf, 0, buf.length);
    }

    synchronized int read(long pos, byte[] buf, int offset, int len)
        throws IOException {
	if (DEBUG) {
	    System.out.println("VRFileRAF:read reading up to " + len
		+ " bytes of data from file position " + pos
		+ " into a byte array at offset " + offset);
	}
	myRAF.seek(pos);
	int n =  -1;
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.currentThread().interrupted();
	        myRAF.seek(pos);
	        n = myRAF.read(buf, offset, len);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            myRAF.seek(pos);
            n = myRAF.read(buf, offset, len);
        }

	/*
	assert (myRAF.getFilePointer() == (pos + n)) :
		(" unexpected file ptr "+myRAF.getFilePointer()+";expected "
		+ (pos + n));
	*/

	return n;
    }

/*
    VRFileWarning addWarning(VRFileWarning w, short code, long from,
	ByteBuffer header, VRecord r) {

	StringBuffer wstr = new StringBuffer("\nbad record found:");
	wstr.append("\n  at: "+ from);
	wstr.append("\n  reason: "+ getReason(code));
	if (code != STATE_BAD_TRUNCATED_HEADER) {
	    // add loaded record header

	    header.rewind();
	    wstr.append("\n  record header loaded:");
	    wstr.append("\n    magic number: " + header.getInt());
	    wstr.append("\n    capacity: " + header.getInt());
	    wstr.append("\n    state: " + header.getShort());
	}

	if (r == null) {
	    wstr.append("\n  repair: No more good record found," +
			"\n              last record marked at "+ from);
	} else {
	    wstr.append("\n  repair: another good record found;" +
			"\n              free record formed - \n" + r.toString());
	}

	w.addWarning(wstr.toString());
	return w;
    }
*/
}

