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
 * @(#)DstMsgStore.java	1.31 08/31/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.io.disk.VRFile;
import com.sun.messaging.jmq.io.disk.VRFileRAF;
import com.sun.messaging.jmq.io.disk.VRecordRAF;
import com.sun.messaging.jmq.util.DestMetricsCounters;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DstMsgStore keeps track of messages for a destination.
 * Messages are either stored in records in a vrfile or in their own file.
 */
class DstMsgStore extends RandomAccessStore {

    BrokerConfig config = Globals.getConfig();

    static final String USE_FILE_CHANNEL_PROP
		= FileStore.FILE_PROP_PREFIX + "message.use_file_channel";
    static final boolean DEFAULT_USE_FILE_CHANNEL = false;

    static String VRFILE_NAME = "vrfile";

    /* == variables used for storing/retrieving messages == */

    static boolean useFileChannel = Globals.getConfig().getBooleanProperty(
			USE_FILE_CHANNEL_PROP, DEFAULT_USE_FILE_CHANNEL);

    DestinationUID myDestination = null;

    //private MsgStore parent = null;

    // cache of all messages of the destination; message id -> MessageInfo
    private ConcurrentHashMap messageMap = new ConcurrentHashMap(1000);

    //
    // These variables are related to initial loading of messages
    //
    private AtomicBoolean loaded = new AtomicBoolean(false);

    // Added byt Tom Ross fix for bug 6431962
    // 16 June 2006

    // initializing file store properties
    // growth_factor (default 50%)
    // this is a percentage of hte current size by which the file store will grow when grown
    protected float growthFactor = config.getPercentageProperty(Globals.IMQ + ".persist.file.message.vrfile.growth_factor", VRFile.DEFAULT_GROWTH_FACTOR);

    // threshold file size after which VR file start to grow
    protected long threshold = config.getLongProperty(Globals.IMQ + ".persist.file.message.vrfile.threshold",VRFile.DEFAULT_THRESHOLD);

    // this is the new value by which a file store will be grow by
    // when its size reaches the treshold above
    protected float thresholdFactor = config.getPercentageProperty(Globals.IMQ + ".persist.file.message.vrfile.threshold_factor",VRFile.DEFAULT_THRESHOLD_FACTOR);

    // skip 'vrfile'
    private static FilenameFilter vrfileFilter = new FilenameFilter() {
	public boolean accept(File dir, String name) {
	    return (!name.equals(VRFILE_NAME));
	}
    };

    private static Enumeration emptyEnum = new Enumeration() {
	public boolean hasMoreElements() {
	    return false;
	}

        public Object nextElement() {
	    return null;
	}
    };

    /**
     * The msgCount and byteCount are
     * - initialized when the DstMsgStore is instantiated
     * - incremented when new messages are stored
     * - decremented when messages are removed
     * - and reset to 0 when the dst is purged or removed
     */
    private int msgCount = 0;
    private long byteCount = 0;
    private Object countLock = new Object();

    // backing file
    private VRFileRAF vrfile = null;

    int maxRecordSize = 0;

    DstMsgStore(MsgStore p, DestinationUID dst, File dir)
	throws BrokerException {
	this(p, dst, dir, false);
    }

    DstMsgStore(MsgStore p, DestinationUID dst, File dir, boolean load)
	throws BrokerException {

	super(dir, p.msgfdlimit, p.poollimit, p.cleanratio);
	//parent = p;
	myDestination = dst;

	try {
	    long fsize = p.initialFileSize.getBytes();
	    if (fsize > 0) {
		maxRecordSize = (int)p.maxRecordSize.getBytes();

		vrfile = new VRFileRAF(new File(dir, VRFILE_NAME), fsize, 
                             Globals.isMinimumWritesFileStore(), Broker.isInProcess());
		vrfile.setBlockSize(p.blockSize);

		try {
                    vrfile.setGrowthFactor(growthFactor);
                } catch (IllegalArgumentException iiEx) {

                    vrfile.setGrowthFactor(vrfile.DEFAULT_GROWTH_FACTOR);
                    growthFactor = vrfile.DEFAULT_GROWTH_FACTOR;

                    logger.log(logger.INFO,"Invalid growth_factor value. Using default value of 50%.");
                }

                try {
                    vrfile.setThreshold(threshold);
                } catch ( IllegalArgumentException iiEx){

                    vrfile.setThreshold(vrfile.DEFAULT_THRESHOLD);
                    threshold = vrfile.DEFAULT_THRESHOLD;

                    logger.log(logger.INFO,"Invalid threshold value. Using default value of 0.");
                }

		try {
                    vrfile.setThresholdFactor(thresholdFactor);
                } catch ( IllegalArgumentException iiEx){

                    vrfile.setThresholdFactor(vrfile.DEFAULT_THRESHOLD_FACTOR);
                    thresholdFactor = vrfile.DEFAULT_THRESHOLD_FACTOR;

                    logger.log(logger.INFO,"Invalid threshold_factor value. Using default value of 0%.");
                }

                try {
                    if ( threshold != 0 || thresholdFactor != 0.0f){
                        vrfile.checkGrowthFactorSanity();
                    }
                } catch (IllegalStateException isEx){
                    String exMsg = isEx.getMessage();
                    logger.log(logger.INFO,exMsg);
                }

		try {
		    vrfile.open();
		} catch (com.sun.messaging.jmq.io.disk.VRFileWarning e) {
		    logger.log(logger.WARNING,
			"possible data loss for " + myDestination, e);
		}
	    }

	    // initialize message count and byte count
	    initCounts();
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_MESSAGE_FILE_FAILED,
			dir, dst, e);
	    throw new BrokerException(br.getString(
			br.X_LOAD_MESSAGE_FILE_FAILED, dir, dst), e);
	} catch (Throwable t) {
	    logger.log(logger.ERROR, br.X_LOAD_MESSAGE_FILE_FAILED,
			dir, dst, t);
	    throw new BrokerException(br.getString(
			br.X_LOAD_MESSAGE_FILE_FAILED, dir, dst), t);
	}

	if (load) {
	    loadMessages();
	}
    }

    // return info about vfile
    public HashMap getStorageInfo() throws BrokerException {

	if (vrfile == null) {
	    return new HashMap();
	}

	HashMap info = new HashMap(3);
	long used = vrfile.getBytesUsed();
	long free = vrfile.getBytesFree();
	info.put(DestMetricsCounters.DISK_USED, Long.valueOf(used));
	info.put(DestMetricsCounters.DISK_RESERVED, Long.valueOf(used + free));
	info.put(DestMetricsCounters.DISK_UTILIZATION_RATIO,
			Integer.valueOf((int)(vrfile.getUtilizationRatio()*100)));
	return info;
    }

    // compact the vfile
    void compact() throws BrokerException {
	if (vrfile == null) {
	    return;
	}

	// synchronize on vrfile to prevent others to access this destination
	synchronized (vrfile) {
	    try {
		vrfile.close();

		try {
		    vrfile.compact();
		} catch (com.sun.messaging.jmq.io.disk.VRFileWarning e) {
		    logger.log(logger.WARNING,
			"possible data loss for " + myDestination, e);
		}

		try {
		    vrfile.open();
		} catch (com.sun.messaging.jmq.io.disk.VRFileWarning e) {
		    logger.log(logger.WARNING,
			"possible data loss for " + myDestination, e);
		}

		// After the file is compacted, all VRecords needs to be reloaded.
		messageMap.clear();
		Iterator itr = vrfile.getRecords().iterator();
		Enumeration e = new MsgEnumeration(this, itr, emptyEnum);
		while (e.hasMoreElements()) {
		    e.nextElement();
		}
	    } catch (IOException e) {
		throw new BrokerException(
			"Failed to compact file: " + vrfile.getFile(), e);
	    }
	}
    }

    MessageInfo storeMessage(Packet message, ConsumerUID[] iids, int[] states,
	boolean sync) throws IOException, BrokerException {

	SysMessageID id = message.getSysMessageID();

        // just check the cached map; all messages should be
        // loaded before this is called
        if (messageMap.containsKey(id)) {
            logger.log(logger.ERROR, br.E_MSG_EXISTS_IN_STORE, id, myDestination);
            throw new BrokerException(
                br.getString(br.E_MSG_EXISTS_IN_STORE, id, myDestination));
        }

        try {
            int msgsize = message.getPacketSize();

            MessageInfo info = null;
            if (vrfile != null &&
                (maxRecordSize == 0 || msgsize < maxRecordSize)) {

                // store in vrfile
                info = new MessageInfo(this, vrfile, message, iids, states, sync);
            } else {
                // store in individual file
                info = new MessageInfo(this, message, iids, states, sync);
            }

            // cache it, make sure to use the cloned SysMessageID
            messageMap.put(info.getID(), info);

            // increate destination message count and byte count
            incrMsgCount(msgsize);

            return info;
        } catch (IOException e) {
            logger.log(logger.ERROR, br.X_PERSIST_MESSAGE_FAILED,
                    id.toString(), e);
            throw e;
        }
    }

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param id	the system message id of the message to be removed
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     */
    void removeMessage(SysMessageID id, boolean sync)
	throws IOException, BrokerException {

	MessageInfo oldmsg = (MessageInfo)messageMap.remove(id);

        if (oldmsg == null) {
            logger.log(logger.ERROR, br.E_MSG_NOT_FOUND_IN_STORE,
                id, myDestination);
            throw new BrokerException(
                br.getString(br.E_MSG_NOT_FOUND_IN_STORE,
                    id, myDestination));
        }

        oldmsg.free(sync);

	// decrement destination message count and byte count
	decrMsgCount(oldmsg.getSize());
    }

    /**
     * Remove all messages associated with this destination.
     *
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     */
    void removeAllMessages(boolean sync) throws IOException, BrokerException {

        if (vrfile != null) {
            vrfile.clear(false); // false->don't truncate
        }

        removeAllData(sync);

        messageMap.clear();
        clearCounts();
    }

    // will delete the whole directory hierarchy
    void releaseMessageDir(boolean sync) throws IOException {

        if (vrfile != null) {
            // clear backing file
            vrfile.clear(false);
            vrfile.close();
            vrfile = null;
        }

        // get rid of the whole directory
        reset(true); // true -> remove top directory
        super.close(false);

        // clear cache
        messageMap.clear();
        clearCounts();
    }

    /**
     * Return an enumeration of all persisted messages.
     * Use the Enumeration methods on the returned object to fetch
     * and load each message sequentially.
     *
     * <p>
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @return an enumeration of all persisted messages.
     */
    Enumeration messageEnumeration() {

	if (loaded.get()) {
	    return new MsgEnumeration(this, getMessageIterator());
	} else {
	    Iterator recitr = null;
	    if (vrfile != null) {
		recitr = vrfile.getRecords().iterator();
	    }
	    // false -> not peek only but load message
	    return new MsgEnumeration(this, recitr, getFileEnumeration());
	}
    }

    /**
     * return the number of messages in this file
     */
    int getMessageCount() throws BrokerException {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "DstMsgStore:getMessageCount()");
	}

	return msgCount;
    }

    /**
     * return the number of bytes in this file
     */
    long getByteCount() throws BrokerException {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "DstMsgStore:getByteCount()");
	}

	return byteCount;
    }

    protected void close(boolean cleanup) {

	// vrfile
	if (vrfile != null) {
	    vrfile.close();
	}

	// individual files
	super.close(cleanup);

	messageMap.clear();
    }

    VRFileRAF getVRFile() {
	return vrfile;
    }

    /**
     * Load all messages in the backing file.
     *
     * returned if no messages exist in the store
     * @exception BrokerException if an error occurs while getting the data
     */
    private void loadMessages() throws BrokerException {

	Enumeration e = this.messageEnumeration();
	while (e.hasMoreElements()) {
	    e.nextElement();
	}

	logger.log(logger.DEBUG, "loaded "+messageMap.size()+" messages");
    }

    MessageInfo getMessageInfo(SysMessageID mid) throws BrokerException {

        MessageInfo info = (MessageInfo)messageMap.get(mid);
        if (info == null) {
            String emsg = br.getKString(
                br.E_MSG_NOT_FOUND_IN_STORE, mid, myDestination);
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg);
        }

        return info;
    }

    // BEGIN: implement super class (RandomAccessStore) abstract method
    /**
     * parse the message and it's associated interest list from
     * the given buffers. This is loaded from individual message files.
     * Returns the sysMessageID.
     */
    Object parseData(byte[] data, byte[] attachment) throws IOException {

	MessageInfo minfo = new MessageInfo(this, data, attachment);

	// if everything is ok, we cache it
	// make sure to use the cloned SysMessageID
	SysMessageID mid = minfo.getID();
	messageMap.put(mid, minfo);

	return mid;
    }

    FilenameFilter getFilenameFilter() {
	return vrfileFilter;
    }
    // END: implement super class (RandomAccessStore) abstract method

    // synchronized access to messageMap.put()
    private void cacheMessageInfo(MessageInfo minfo) {
        messageMap.put(minfo.getID(), minfo);
    }

    // synchronized access to messageMap.keySet().iterator();
    private Iterator getMessageIterator() {
        return messageMap.keySet().iterator();
    }

    private void setLoadedFlag(boolean flag) {
        loaded.set(flag);
    }

    private void incrMsgCount(int msgSize) throws BrokerException {
	synchronized (countLock) {
	    msgCount++;
	    byteCount += msgSize;
	}
    }

    private void decrMsgCount(int msgSize) throws BrokerException {
	synchronized (countLock) {
	    msgCount--;
	    byteCount -= msgSize;
	}
    }

    private void clearCounts() {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG,
		"DstMsgStore:clearCounts for " + myDestination);
	}

	synchronized (countLock) {
	    msgCount = 0;
	    byteCount = 0;
	}
    }

    /**
     * The msgCount and byteCount are
     * - initialized when the DstMsgStore is instantiated
     * - incremented when new messages are stored
     * - decremented when messages are removed
     * - and reset to 0 when the dst is purged or removed
     */
    private void initCounts() throws BrokerException {

	// check vrfile first
	if (vrfile != null) {
	    Set msgs = vrfile.getRecords();

	    Iterator itr = msgs.iterator();
	    while (itr.hasNext()) {
		// get all packetSize
		VRecordRAF record = (VRecordRAF)itr.next();

		// sanity check
		short cookie = record.getCookie();
		if (cookie == MessageInfo.PENDING ||
		    cookie != MessageInfo.DONE) {

		    // writing not finish: log error, free record
		    String warning = myDestination + ": found a " +
				"corrupted message at vrecord(" + record +
				"), a message might be lost";
		    logger.log(logger.WARNING, warning);

		    // free the bad VRecord
		    try {
			vrfile.free(record);
		    } catch (IOException e) {
			logger.log(logger.ERROR,
				"Failed to free the corrupted vrecord: " + e);
		    }

		} else {

		    try {
			msgCount++;
			byteCount += record.readInt();
		    } catch (Throwable t) {
			logger.log(logger.ERROR, br.X_READ_FROM_VRECORD_FAILED,
					vrfile.getFile(), t);
			throw new BrokerException(br.getString(
					br.X_READ_FROM_VRECORD_FAILED,
					vrfile.getFile()), t);
		    }
		}
	    }
	}

        long[] cnts = initCountsFromIndividualFiles();
        msgCount += (int)cnts[0];
        byteCount += cnts[1];

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "DstMsgStore: initialized "+
				"msg count=" + msgCount +
				"; byte count = " + byteCount);
	}
    }

    private static class MsgEnumeration implements Enumeration {
	DstMsgStore parent = null;
	Iterator itr = null;
	Iterator recitr = null;
	Enumeration msgEnum = null;

        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();

	Object objToReturn = null;

	MsgEnumeration(DstMsgStore p, Iterator i) {
	    parent = p;
	    itr = i;
	}

	MsgEnumeration(DstMsgStore p, Iterator i, Enumeration e) {
	    parent = p;
	    recitr = i;
	    msgEnum = e;
	}

	public boolean hasMoreElements() {
	    Packet msg = null;
	    if (itr != null) {
		if (itr.hasNext()) {
		    objToReturn = itr.next();
		    return true;	// RETURN TRUE
		} else {
		    return false;
		}
	    } else {
		if (recitr != null) {
		  while (recitr.hasNext()) {
		    // load message from VRecordRAF
		    try {
			MessageInfo minfo = new MessageInfo(parent,
						(VRecordRAF)recitr.next());
			objToReturn = minfo.getMessage();

			// first time loaded from file; cache info
			parent.cacheMessageInfo(minfo);

			return true;	// RETURN TRUE
		    } catch (IOException e) {
			// log an error and continue
			logger.log(logger.ERROR, br.X_PARSE_MESSAGE_FAILED,
					parent.myDestination, e);
		    }
		  }
		}

		if (msgEnum.hasMoreElements()) {
		    // load message from individual file
		    objToReturn = msgEnum.nextElement();
		    return true;
		} else {
		    parent.setLoadedFlag(true);
		    return false;
		}
	    }
	}

	public Object nextElement() {
	    if (objToReturn != null) {
		Object result = null;;
		if (objToReturn instanceof SysMessageID) {
		    try {
			result = parent.getMessage((SysMessageID)objToReturn);
		    } catch (IOException e) {
			// failed to load message
			// log error; and continue
			logger.log(logger.ERROR, br.X_RETRIEVE_MESSAGE_FAILED,
					objToReturn, parent.myDestination, e);
			throw new NoSuchElementException();
		    } catch (BrokerException e) {
			// msg not found
			// log error; and continue
			logger.log(logger.ERROR, br.X_RETRIEVE_MESSAGE_FAILED,
					objToReturn, parent.myDestination, e);
			throw new NoSuchElementException();
		    }
		} else {
		    result = objToReturn;
		}
		objToReturn = null;
		return result;
	    } else {
		throw new NoSuchElementException();
	    }
	}
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	int numInVrfile = 0;
	if (vrfile != null) {
	    numInVrfile = vrfile.getNRecords();
	}
	int numInFiles = msgCount - numInVrfile;

	Hashtable t = new Hashtable();
	t.put((myDestination+":messages in vrfile"),
			String.valueOf(numInVrfile));
	t.put((myDestination+":messages in its own file"),
			String.valueOf(numInFiles));
	return t;
    }

    // the following 3 methods are added to make sure
    // writing/reading to/from the backing file is synchronized

    void storeInterestStates(SysMessageID mid,
	ConsumerUID[] iids, int[] states, boolean sync)
	throws IOException, BrokerException {

        getMessageInfo(mid).storeStates(iids, states, sync);
    }

    void updateInterestState(
	SysMessageID mid, ConsumerUID iid, int state, boolean sync)
	throws IOException, BrokerException {

        getMessageInfo(mid).updateState(iid, state, sync);
    }

    boolean containsMsg(SysMessageID mid) {
        return messageMap.containsKey(mid);
    }

    Packet getMessage(SysMessageID mid) throws IOException, BrokerException {
        MessageInfo msginfo = (MessageInfo)messageMap.get(mid);
        if (msginfo == null) {
            String emsg =  br.getKString(
                br.E_MSG_NOT_FOUND_IN_STORE, mid, myDestination);
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg);
        } else {
            return msginfo.getMessage();
        }
    }

    void sync() throws BrokerException {
        try {
        	if (Store.getDEBUG_SYNC()) {
				logger.log(Logger.DEBUG, "sync called on " + myDestination);
			}
            vrfile.force();
        } catch (IOException e) {
            throw new BrokerException(
                "Failed to synchronize data to disk for file: " + vrfile, e);
        }
    }
}

