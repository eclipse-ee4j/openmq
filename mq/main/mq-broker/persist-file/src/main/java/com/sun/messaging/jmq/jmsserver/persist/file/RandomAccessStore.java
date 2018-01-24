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
 * @(#)RandomAccessStore.java	1.27 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;

import java.io.*;
import java.util.*;

/**
 * RandomAccessStore is an abstract class encapsulating a
 * directory of files accessed by RandomAccessFiles objects.
 */
abstract class RandomAccessStore {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();

    // beginning tag of files to indicate whether the file
    // contains valid data (GOOD) or it's available for reuse (FREE)
    // files tagged with "...." are corrupted
    public static final String FREE_FILE = "FREE";	// contains no data
    public static final String GOOD = "GOOD";		// contains valid data
    public static final String WRITING = "....";	// writing in progress
    public static final long START_OF_DATA = 6;
    
    // len of a long in file
    static final int LONG_LEN = 8;
    static final int INT_LEN = 4;

    // operation on a HashMap
    private static final int PUT = 0;
    private static final int REMOVE = 1;
    private static final int GET = 2;

    // opened RandomAccessFile pool
    private RAFilePool raFilePool = null;

    // free file pool
    private FilePool filePool = null;

    // numeric file names
    // low is the lowest number being used now
    // high is the next accending number that should be used
    public static final int LOWEST_FILE_NUM = 0;
    public int high = LOWEST_FILE_NUM + 1;
    public int low = LOWEST_FILE_NUM + 1;

    /* data directory */
    protected File directory = null;

    // cache of data id to RandomAccessFile
    protected HashMap idToRAFile = new HashMap();

    // cache of data id to File
    protected HashMap idToFile = new HashMap();
    private static final boolean interruptSafe = Broker.isInProcess();

    RandomAccessStore(File dir, int openlimit, int poollimit, int cleanratio)
	throws BrokerException {

        // RAFilePool is typically not used any more. I.e. openlimit == 0 
	this.raFilePool = new RAFilePool(this, openlimit);

	// Create the file pool 
        int L2capacity = (cleanratio * poollimit) / 100;
        int L1capacity = poollimit - L2capacity;
	this.filePool = new FilePool(this, L1capacity, L2capacity);

	this.directory = dir;

	// create the directory if it does not exist
	if (!directory.exists() && !directory.mkdirs()) {
	    logger.log(logger.ERROR, br.E_CANNOT_CREATE_STORE_HIERARCHY,
			directory.toString());
	    throw new BrokerException(br.getString(
					br.E_CANNOT_CREATE_STORE_HIERARCHY,
					directory.toString()), Status.NOT_ALLOWED);
	}
    }

    /**
     * - parse the data and attachment retrieved from a file
     * - called when data is loaded from a file
     * - returns the id of the data
     */
    abstract Object parseData(byte[] data, byte[] attachment)
	throws IOException;

    /**
     * use to filter filenames
     */
    abstract FilenameFilter getFilenameFilter();

    protected void printFileInfo(PrintStream out) {
	raFilePool.printFileInfo(out);
	filePool.printFileInfo(out);
    }

    /**
     * If cleanup is true; all data files will be truncated to the extent
     * of valid data and free files are truncated to size 0.
     */
    protected void close(boolean cleanup) {
	printStatistics("At close():");

	// close and truncate all opened files that contain data
	FileInfo[] info = (FileInfo[])idToRAFile.values().toArray(
					new FileInfo[idToRAFile.size()]);

	closeFiles(info, cleanup);

	// truncate all other valid files
	if (cleanup) {
	    info = (FileInfo[])idToFile.values().toArray(
					new FileInfo[idToFile.size()]);
	    truncateFiles(info);
	}

	// truncate all free files in opened file pool
	raFilePool.close(cleanup);

	// truncate all free files in file pool
	filePool.close(cleanup);
    }

    void printStatistics(String msg) {

	// print statistics
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "class="+this);
	    logger.log(logger.DEBUG, msg);
	    logger.log(logger.DEBUG, "high = "+high);
	    logger.log(logger.DEBUG, "low = "+low);
	    logger.log(logger.DEBUG, "idToRAFile.size = "+idToRAFile.size());
	    logger.log(logger.DEBUG, "idToFile.size = "+idToFile.size());
	    raFilePool.printStatistics();
	    filePool.printStatistics();
	}
    }

    // return true if everything runs ok
    boolean writeAttachment(Object id, byte[] buf, boolean sync)
	throws IOException {

	boolean ok = true;
	boolean doclose = false;
	RandomAccessFile rfile = null;

	// find the file to write

	// is the file in opened file list?
	FileInfo fileinfo = (FileInfo)idToRAFileOp(GET, id, null);

	if (fileinfo != null) {
	    rfile = (RandomAccessFile)fileinfo.file;
	} else {
	    // is the file in the other list?
	    fileinfo = (FileInfo)idToFileOp(GET, id, null);
	    if (fileinfo != null) {
		rfile = new RandomAccessFile((File)fileinfo.file, "rw");
		doclose = true;
	    } else {
		// cannot find file to write attachment???
		ok = false;
	    }
	}

	if (ok) {
	    writeAttachment(rfile, fileinfo.endofdata, buf, sync);

	    // update endoffile pointer
	    fileinfo.endoffile = fileinfo.endofdata + LONG_LEN + buf.length;

	    if (doclose) {
		rfile.close();
	    }
	}

	return ok;
    }

    // return true if everything runs ok
	boolean writeAttachmentData(Object id, long offset, int intValue,
			boolean sync) throws IOException {
		return writeAttachmentData(id, offset, false, intValue, sync);
	}

    // return true if everything runs ok
    boolean writeAttachmentData(Object id, long offset, boolean fromStart,int intValue,
	boolean sync) throws IOException {

	boolean ok = true;
	boolean doclose = false;
	RandomAccessFile rfile = null;

	// find the file to write

	// is the file in opened file list?
	FileInfo fileinfo = (FileInfo)idToRAFileOp(GET, id, null);

	if (fileinfo != null) {
	    rfile = (RandomAccessFile)fileinfo.file;
	} else {
	    // is the file in the other list?
	    fileinfo = (FileInfo)idToFileOp(GET, id, null);
	    if (fileinfo != null) {
		rfile = new RandomAccessFile((File)fileinfo.file, "rw");
		doclose = true;
	    } else {
		// cannot find file to write attachment???
		ok = false;
	    }
	}

	if (ok) {
		 // calculate offset
		// is offset relative to start of record or into attachment?
		long myoffset = 0;
		if(fromStart){
			// START_OF_DATA = UTF file status
			// LONG_LEN = length of data
			myoffset = START_OF_DATA  + LONG_LEN + offset;
		}
		else{
			myoffset = fileinfo.endofdata + LONG_LEN + offset;
		}
	    writeAttachmentData(rfile, myoffset, intValue, sync);
	    if (doclose) {
		rfile.close();
	    }
	}
	return ok;
    }

    private void writeAttachment(RandomAccessFile rfile, long pos,
	byte[] buf, boolean sync) throws IOException {

	// writing in progress
	markWriting(rfile);

	// write attachment after data
	seek(rfile, pos);
	writeLong(rfile, (long)buf.length);
	write(rfile, buf);


	// mark it good
	markGood(rfile);

	if (sync) {
            sync(rfile);
	}
    }

    private void writeAttachmentData(
	RandomAccessFile rfile, long pos, int intvalue, boolean sync)
	throws IOException {

	seek(rfile, pos);
	writeInt(rfile, intvalue);

	if (sync) {
            sync(rfile);
	}
    }

    // load the data again. id is the id of the data
    byte[] loadData(Object id) throws IOException {

	// is file in opened file list?
	FileInfo info = (FileInfo)idToRAFileOp(GET, id, null);

	byte[] data = null;
	if (info != null) {
	    data = loadDataFromFile((RandomAccessFile)info.file);
	} else {
	    // if we are here; then need to find the file from the other list
	    info = (FileInfo)idToFileOp(GET, id, null);

	    if (info != null) {
		RandomAccessFile rfile = new RandomAccessFile(
						(File)info.file, "r");
		data = loadDataFromFile(rfile);
		rfile.close();
	    }
	}
	return data;
    }

    // return true if everything runs ok
    boolean removeData(Object id, boolean sync) throws IOException {

	boolean ok = true;

	// is file in opened file list?
	FileInfo info = (FileInfo)idToRAFileOp(REMOVE, id, null);

	if (info != null) {
	    raFilePool.putRAFile((RandomAccessFile)info.file, sync);
	    return ok;
	}

	// if we are here; then need to find the file from the other list
	info = (FileInfo)idToFileOp(REMOVE, id, null);

	if (info != null) {
	    filePool.putFile((File)info.file, sync);
	} else {
	    // where did we lose it??
	    ok = false;
	}

	return ok;
    }

    void removeAllData(boolean sync) throws IOException {

	boolean ok = true;

	// handle files in opened file list first
	Iterator itr = idToRAFile.values().iterator();
	while (itr.hasNext()) {
	    FileInfo info = (FileInfo)itr.next();
	    ok = raFilePool.putRAFile((RandomAccessFile)info.file, sync);
	}
	idToRAFile.clear();
	if (!ok) {
	    System.out.println("Failed to tag some file free");
	}

	// handle files in the other list
	itr = idToFile.values().iterator();
	while (itr.hasNext()) {
	    FileInfo info = (FileInfo)itr.next();
	    filePool.putFile((File)info.file, sync);
	}
	idToFile.clear();
    }

    // return a RandomAccessFile object for the specified id
    protected RandomAccessFile getRAF(Object id) throws IOException {

	// check if the id is cached already
	FileInfo fileinfo = (FileInfo)idToRAFileOp(GET, id, null);
	if (fileinfo != null) {
	    return (RandomAccessFile)fileinfo.file;
	} else {
	    fileinfo = (FileInfo)idToFileOp(GET, id, null);
	    if (fileinfo != null) {
		return new RandomAccessFile((File)fileinfo.file, "rw");
	    }
	}

	// if we are here, the id is new
	// get a free file
	RandomAccessFile rfile = raFilePool.getRAFile();
	if (rfile != null) {
	    idToRAFileOp(PUT, id, new FileInfo(rfile, 0, 0));
	} else {
	    File file = filePool.getFile();
	    try {
		rfile = new RandomAccessFile(file, "rw");
		idToFileOp(PUT, id, new FileInfo(file, 0, 0));
	    } catch (IOException e) {
	        // put file back if writing data fails
		filePool.putFile(file, false);
		throw e;
	    }
	}
	return rfile;
    }

    // the RandomAccessFile object is returned when it is done writing
    // to the file; record endofdata and endoffile
    protected void releaseRAF(Object id, RandomAccessFile raf,
	long endofdata, long endoffile) {

	FileInfo info = (FileInfo)idToRAFileOp(GET, id, null);
	if (info == null) {
	    info = (FileInfo)idToFileOp(GET, id, null);
	    if (info != null) {
		info.endofdata = endofdata;
		info.endoffile = endoffile;
	    } else {
		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUGHIGH, "cannot find owner for raf");
		}
	    }
	    try {
		raf.close();
	    } catch (IOException e) {
		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUGHIGH,
				"failed to close RA files " + info.file, e);
		}
	    }
	} else {
	    info.endofdata = endofdata;
	    info.endoffile = endoffile;
	}
    }

    /**
     * note that the file is not truncated to the
     * end of valid data for performance; the file may be truncated
     * in close() when broker shuts down
     */
    protected void writeData(Object id, byte[] buf, byte[] attachment,
	boolean sync) throws IOException {

	long alen = LONG_LEN + ((attachment==null)?0:attachment.length);

	// get a free file
	RandomAccessFile rfile = raFilePool.getRAFile();
	if (rfile != null) {
	    try {
		long len = writeData(rfile, buf, attachment, sync);
		idToRAFileOp(PUT, id, new FileInfo(rfile, len, len+alen));
	    } catch (IOException e) {
	        // put rfile back if writing data fails
		raFilePool.putRAFile(rfile, sync);
		throw e;
	    }
	} else {
	    File file = filePool.getFile();
	    try {
		rfile = new RandomAccessFile(file, "rw");
		long len = writeData(rfile, buf, attachment, sync);

		idToFileOp(PUT, id, new FileInfo(file, len, len+alen));
	    } catch (IOException e) {
	        // put file back if writing data fails
		filePool.putFile(file, sync);
		throw e;
	    } finally {
		if (rfile != null) {
		    try {
			rfile.close();
		    } catch (IOException e) {
			if (Store.getDEBUG()) {
			    logger.log(logger.DEBUGHIGH,
				"failed to close RA files " + file, e);
			}
		    }
		}
	    }
	}
    }

    static void markWriting(RandomAccessFile raf) throws IOException {
	seek(raf, 0);
	writeUTF(raf, WRITING);
    }

    static void markGood(RandomAccessFile raf) throws IOException {
	seek(raf, 0);
	writeUTF(raf, GOOD);
    }

    static void markFree(RandomAccessFile raf) throws IOException {
	seek(raf, 0);
	writeUTF(raf, FREE_FILE);
    }

    // format:
    // file tag (UTF), 
    // length of data (long),
    // data
    // length of attachment (long)
    // attachment
    // 
    // return index of end of data
    //
    private long writeData(RandomAccessFile rfile, byte[] buf,
	byte[] attachment, boolean sync) throws IOException {

	long	endofdata;

	markWriting(rfile);
	writeLong(rfile, (long)buf.length);
	write(rfile, buf);

	endofdata = rfile.getFilePointer();

	// write attachment
	if (attachment == null) {
	    writeLong(rfile, 0);
	} else {
	    writeLong(rfile, (long)attachment.length);
	    write(rfile, attachment);
	}

	// mark it good
	markGood(rfile);

	if (sync) {
            sync(rfile);
	}

	return endofdata;
    }

    private static void seek(RandomAccessFile rfile, long pos) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.seek(pos);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            rfile.seek(pos);
        }
    }

    private static void write(RandomAccessFile rfile, byte[] buf) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.write(buf);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            rfile.write(buf);
        }
    }

    /*
    private static String readUTF(RandomAccessFile rfile) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                return rfile.readUTF();
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            return rfile.readUTF();
        }
    }
    */

    private static void writeUTF(RandomAccessFile rfile, String v) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.writeUTF(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            rfile.writeUTF(v);
        }
    }

    private static void writeInt(RandomAccessFile rfile, int v) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.writeInt(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            rfile.writeInt(v);
        }
    }

    private static void writeLong(RandomAccessFile rfile, long v) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.writeLong(v);
            } catch (InterruptedIOException e) {
                interrupted = true;
                throw e;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            rfile.writeLong(v);
        }
    }

    private static void sync(RandomAccessFile rfile) throws IOException {
        if (interruptSafe) {
            boolean interrupted = false;
            try {
                interrupted = Thread.interrupted();
                rfile.getFD().sync();
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
            // don't sync meta data for performance reason
            rfile.getChannel().force(false);
        }
    }

    /**
     * close and truncate.
     * Close the given RandomAccessFile objects.
     * Truncate the files to the length of valid date
     * if cleanup is false, don't truncate
     */
    private void closeFiles(FileInfo[] rfiles, boolean cleanup) {

	int count = 0;
	for (int i = 0; i < rfiles.length; i++) {
	    try {
		if (cleanup) {
		    RandomAccessFile rfile = (RandomAccessFile)rfiles[i].file;

		    // truncate the file to end of attachment
		    if (rfile.length() > rfiles[i].endoffile) {
			rfile.setLength(rfiles[i].endoffile);
			count++;
		    }
		}
	    } catch (IOException e) {
		File file = raFilePool.getFile(
				(RandomAccessFile)rfiles[i].file);
		logger.log(logger.INFO, br.I_TRUNCATE_FILE_FAILED,
				((file != null)?file.toString():""), e);
	    } finally {
		try {
		    ((RandomAccessFile)rfiles[i].file).close();
		} catch (IOException e) {}
	    }
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "Truncated "+count+" files");
	}
    }

    /**
     * Truncate files.
     * Truncate the files to the length of valid date
     */
    protected void truncateFiles(FileInfo[] files) {

	int count = 0;
	for (int i = 0; i < files.length; i++) {
	    RandomAccessFile rfile = null;
	    try {
		File file = (File)files[i].file;
		if (file.length() > files[i].endoffile) {
		    rfile = new RandomAccessFile((File)files[i].file, "rw");
		    rfile.setLength(files[i].endoffile);
		    count++;
		}
	    } catch (IOException e) {
		logger.log(logger.INFO, br.I_TRUNCATE_FILE_FAILED,
					files[i].file, e);
	    } finally {
		if (rfile != null) {
		    try {
			rfile.close();
		    } catch (IOException e) {}
		}
	    }
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "Truncated "+count+" files");
	}
    }

    int getNumFreeFiles() {
	return filePool.getNumFreeFiles() + raFilePool.freeFiles.size();
    }

    // reset the state of the store an empty store
    // bruteforce approach; reset all variables and delete all files
    // in the directory
    void reset(boolean removeTopDir) {
	// do we need synchronization here ??

	// reset all variables
	high = LOWEST_FILE_NUM + 1;
	low = LOWEST_FILE_NUM + 1;

	// clear out all cache
	raFilePool.clear();
	filePool.clear();
	idToRAFile.clear();
	idToFile.clear();

	// delete all file
	try {
	    FileUtil.removeFiles(directory, removeTopDir);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_RESET_MESSAGES_FAILED, directory, e);
	}
    }

    // set high and low of numbers being used as file names
    private void addFileNum(int num) {
	if (num >= high)
	    high = num + 1;	// high is the next number to be used
	else if (num < low)
	    low = num;	// low is the lowest number being used
    }

    // cache object id
    // depending on whether maxNum is reached, id might map to
    // an open RandomAccessFile or a File object representing it's file name
    private void addRAFile(Object id, RandomAccessFile rfile,
	File file, long endofdata, long endoffile) {

/*
 * DONT PUT IN FD POOL YET; TO DO OTHERWISE, UNCOMMENT THIS AND THE LAST BLOCK

	if (raFilePool.addRAFile(rfile, file, false)) { // false=>not free
	    // added to opened file pool
	    idToRAFileOp(PUT, id, new FileInfo(rfile, endofdata, endoffile));
	} else {
*/
	    try {
		rfile.close();
	    } catch (IOException e) {
		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUG,
				"closing RandomAccessFile failed", e);
		}
	    }
	    idToFileOp(PUT, id, new FileInfo(file, endofdata, endoffile));
/*
 * AND THIS BLOCK
	}
*/
    }

    // depending on whether maxNum is reached
    // we may cache a RandomAccessFile or just the File object
    // representing a file available for writing new data
    void addFreeRAFile(RandomAccessFile rfile, File file) {

	// add file to opened file pool
	if (!raFilePool.addRAFile(rfile, file, true)) { // true=>free
	    // add to file pool
	    try {
		rfile.close();
	    } catch (IOException e) {
		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUG,
			"closing RandomAccessFile failed", e);
		}
	    }
	    filePool.putFile(file, false);
	}
    }

    // synchronized operations to idToFile
    private Object idToFileOp(int op, Object key, Object value) {
	Object obj = null;

	synchronized (idToFile) {
	    switch (op) {
	    case PUT:
		obj = idToFile.put(key, value);
		break;
	    case REMOVE:
		obj = idToFile.remove(key);
		break;
	    case GET:
		obj = idToFile.get(key);
		break;
            default:
	    }
	}
	return obj;
    }

    // synchronized operations to idToRAFile
    private Object idToRAFileOp(int op, Object key, Object value) {
	Object obj = null;

	synchronized (idToRAFile) {
	    switch (op) {
	    case PUT:
		obj = idToRAFile.put(key, value);
		break;
	    case REMOVE:
		obj = idToRAFile.remove(key);
		break;
	    case GET:
		obj = idToRAFile.get(key);
		break;
	    }
	}
	return obj;
    }

    // The following 2 variables are used by getEnumeration().
    // After the directory is scanned, dataFiles would have
    // cached all File object of files that contain data.
    // After data is loaded; dataFiles is updated.
    private boolean scanned = false;
    private HashSet dataFiles = new HashSet();

    // cache all files that contain data
    void addDataFile(File file) {
	dataFiles.add(file);
    }

    /**
     * All sub classes must call this method at instantiation
     * so that the individual files will be scanned
     */
    final long[] initCountsFromIndividualFiles() {
        long[] cnts = new long[2];
        cnts[0] = cnts[1] = 0L;

        Enumeration e = new FileEnumeration(this, directory);
        while (e.hasMoreElements()) {
            cnts[0]++;
            Long len = (Long)e.nextElement();
            cnts[1] += len.longValue();
        }
        scanned = true;
        return cnts;
    }

    /**
     * This method should only be called after the call to 
     * initCountsFromIndividualFiles
     *
     * Returns an enumeration of files in the directory to be used to
     * either load the data or to peek the size of data in each file
     * NOTE: This is meant to be used once to load the data. If this needs
     * to be used after all valid data is loaded, then dataFiles needs
     * to be updated as data file is added and removed
     */
    final Enumeration getFileEnumeration() {
        if (!scanned) {
            throw new IllegalStateException(br.getKString(
            br.E_INTERNAL_BROKER_ERROR, 
            getClass().getSimpleName()+".getFileEnumeration(): "+
            " files in "+directory+" have not been scanned yet"));
        }
        return new FileEnumeration(this, dataFiles.iterator());
    }

    private byte[] loadDataFromFile(RandomAccessFile rfile) throws IOException {

	// go to beginning of file
	seek(rfile, 0);

	// read file tag
	String value = rfile.readUTF();

	byte[] data = null;
	if (GOOD.equals(value)) {
	    // read length of data
	    long length = rfile.readLong();

	    // read data
	    data = new byte[(int)length];
	    rfile.readFully(data);
	}
	return data;
    }

    // This object holds a file that contains valid data
    //
    // file format:
    // file tag (UTF)
    // length of data (long)
    // data
    // length of attachment (long)
    // attachment
    private static class FileInfo {
	Object	file;		// either a File or RandomAccessFile Object
	long	endofdata;	// points to beginning of length of
				// attachment
	long	endoffile;	// points to endofattachment

	FileInfo(Object afile, long endofdata, long endoffile) {
	    this.file = afile;
	    this.endofdata = endofdata;
	    this.endoffile = endoffile;
	}
    }

    // keep track of the RandomAccessFile pool
    private static class RAFilePool {

	// max number of opened file descriptors
	int limit = 0;

	// free files in the pool
	LinkedList freeFiles = new LinkedList();

    	// cache of all opened
	// RandomAccessFile objects -> associated File objects
	HashMap allRAFiles = null;

	RandomAccessStore store;

        Logger logger = Globals.getLogger();

	RAFilePool(RandomAccessStore store, int limit) {
	    this.limit = limit;
	    this.store = store;
	    this.allRAFiles = new HashMap(limit);
	    if (Store.getDEBUG()) {
	        logger.log(logger.DEBUG, this.getClass().getName() +
                    ": Created new rafile pool: capacity=" + limit);
            }
	}

	// find one in the free RandomAccessFile pool
	// if not found and limit not reached; open one
	// if limit reached; return null
	synchronized RandomAccessFile getRAFile() throws IOException {
	    if (limit == 0) {
		return null;
	    }

	    RandomAccessFile rfile = null;
	    int size = freeFiles.size();
	    if (size > 0) {
		return (RandomAccessFile)freeFiles.remove(size - 1);
	    } else if (allRAFiles.size() < limit) {
		// open a new one
		File file = store.filePool.getFile();
		rfile = new RandomAccessFile(file, "rw");
		allRAFiles.put(rfile, file);
		return rfile;
	    } else {
		return null;
	    }
	}

	synchronized boolean addRAFile(
	    RandomAccessFile rfile, File file, boolean freefile) {

	    if (limit == 0) {
		return false;
	    }

	    boolean ok = true;
	    if (allRAFiles.size() < limit) {
		if (freefile && !putRAFile(rfile, false)) {
		    ok = false;
		}

		if (ok) {
		    allRAFiles.put(rfile, file);
		}
	    } else {
		ok = false;
	    }
	    return ok;
	}

	/**
	 * Tag the file free first.
	 * Then put it in the free file pool.
	 */
	synchronized boolean putRAFile(RandomAccessFile rfile, boolean sync) {
	    if (rfile == null)
		return false;

	    try {
		// mark it free
		markFree(rfile);

		if (sync) {
                    sync(rfile);
		}

		freeFiles.add(rfile);
		return true;
	    } catch (IOException e) {
		// trouble writing to this file; don't use it
		File file = (File)allRAFiles.remove(rfile);
		if (file != null) {
		    try {
			rfile.close();
		    } catch (IOException ex) { }
		}

		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUG,
			"Fail to tag free file" + file.toString(), e);
		}

		return false;
	    }
	}

	File getFile(RandomAccessFile rfile) {
	    return (File)allRAFiles.get(rfile);
	}

	void clear() {

	    if (limit == 0) {
		return;
	    }

	    // close all opened files
	    Object[] rfiles = allRAFiles.keySet().toArray();
	    for (int i = 0; i < rfiles.length; i++) {
		RandomAccessFile rfile = (RandomAccessFile)rfiles[i];
		try {
		    rfile.close();
		} catch (IOException e) {
		    if (Store.getDEBUG()) {
			logger.log(logger.DEBUGHIGH,
				"Got exception while closing file", e);
		    }
		}
	    }

	    freeFiles.clear();
	    allRAFiles.clear();
	}

	// close and truncate all free files
	// if cleanup is false, don't truncate
	void close(boolean cleanup) {

	    if (limit == 0) {
		return;
	    }

	    // close and truncate all free files in opened file pool
	    RandomAccessFile[] rfiles = (RandomAccessFile[])freeFiles.toArray(
					new RandomAccessFile[freeFiles.size()]);

	    for (int i = 0; i < rfiles.length; i++) {
		File file = (File)allRAFiles.remove(rfiles[i]);
		try {
		    if (cleanup) {
			try {
			    if (rfiles[i].length() != 0)
				rfiles[i].setLength(0);
			} catch (IOException e) {
			    if (Store.getDEBUG()) {
				logger.log(logger.DEBUG,
				    "truncate file failed for " +
				    (file != null?file.toString():""));
			    }
			}
		    }
		} finally {
		    try {
			rfiles[i].close();
		    } catch (IOException e) {
			if (Store.getDEBUG()) {
			    logger.log(logger.DEBUG, "failed to close "+file);
			}
		    }
		}
	    }
	}

	void printFileInfo(PrintStream out) {
	    out.println("number of opened files: " + allRAFiles.size());
	    out.println("number of available opened files: "
			+ freeFiles.size());
	}

	void printStatistics() {

	    // print statistics
	    if (Store.getDEBUG()) {
		logger.log(logger.DEBUG,
			"total number of opened files="+allRAFiles.size());
		logger.log(logger.DEBUG,
			"number of free files = "+freeFiles.size());
	    }
	}

    }

    private static class FileEnumeration implements Enumeration {
	RandomAccessStore parent = null;
	boolean peekonly = false;
	String[] filelist = null;
	File directory = null;
	int index = 0;
	Object obj = null;
	Iterator itr = null;
        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();

	FileEnumeration(RandomAccessStore p, Iterator i) {
	    parent = p;
	    peekonly = false;
	    itr = i;
	}

	FileEnumeration(RandomAccessStore p, File dir) {
	    parent = p;
	    peekonly = true;
	    directory = dir;

	    filelist = dir.list(p.getFilenameFilter());
	    if (filelist == null) {
		filelist = new String[0];
	    }
	}

	private boolean more() {
	    if (itr != null) {
		return itr.hasNext();
	    } else if (index < filelist.length && obj == null) {
		return true;
	    } else {
		return false;
	    }
	}

	public boolean hasMoreElements() {
	    obj = null;

	    if (itr == null && (index >= filelist.length)) {
		return false;
	    }

	    while (obj == null && more()) {
		File file = null;
		if (itr != null) {
		    file = (File)itr.next();
		} else {
		    try {
			// check file name
			int num = Integer.parseInt(filelist[index]);
			parent.addFileNum(num);
		    } catch (NumberFormatException e) {
			// delete all files whose name is not a number;
			// and log it
			File badfile = new File(directory, filelist[index]);
			logger.log(logger.WARNING, br.W_BAD_FILE_NAME,
				badfile.getAbsolutePath());
			if (!badfile.delete()) {
                            logger.log(logger.WARNING, 
                            br.I_DELETE_FILE_FAILED, badfile.getAbsolutePath());
                        }
			index++;
			continue;
		    }
		    file = new File(directory, filelist[index]);
		    index++;
		}

		RandomAccessFile rfile = null;
		try {

		    if (file.length() == 0) {
                	parent.filePool.putFile(file, false);
                	continue;
		    }

		    rfile = new RandomAccessFile(file, "rw");

		    // read file tag
		    String value = rfile.readUTF();

		    if (GOOD.equals(value)) {
			// read length of data
			long length = rfile.readLong();

			if (peekonly) {
			    // just return size
			    obj = Long.valueOf(length);
			    rfile.close();

			    if (itr == null) {
				// first scan of directory, add data file
			    	parent.addDataFile(file);
			    }
			} else {
			    // read data
			    byte[] data = new byte[(int)length];
			    rfile.read(data);
			    long endofdata = rfile.getFilePointer();

			    // read length of attachment
			    length = rfile.readLong();

			    byte[] attachment = null;
			    if (length > 0) {
				// read attachment
				attachment = new byte[(int)length];
				rfile.readFully(attachment);
			    }
			    long endoffile = endofdata + LONG_LEN + length;

			    // implemented by subclass
			    // return data id
			    obj = parent.parseData(data, attachment);

			    parent.addRAFile(obj, rfile, file, endofdata,
						endoffile);
			}
		    } else {
			if (!FREE_FILE.equals(value)) {
			    // corrupted file, log it
			    if (Store.getDEBUG()) {
				logger.log(logger.DEBUG,
					file + " was corrupted");
			    }
			}
			if (itr == null) {
			    // first scan of directory, add free file
			    parent.addFreeRAFile(rfile, file);
			}
		    }
		} catch (IOException e) {
		    // reset it
		    logger.log(logger.WARNING, br.W_CANNOT_READ_DATA_FILE,
				file, e);
		    if (itr == null) {
			// first scan of directory, add free file
			parent.addFreeRAFile(rfile, file);
		    }
		}
	    }

	    return (obj != null);
	}

	public Object nextElement() {
	    if (obj != null) {
		Object result = obj;
		obj = null;
		return result;
	    } else {
		throw new NoSuchElementException();
	    }
	}
    }

}

