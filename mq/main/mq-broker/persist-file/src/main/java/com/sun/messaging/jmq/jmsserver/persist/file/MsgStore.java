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
 * @(#)MsgStore.java	1.58 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.util.BrokerExitCode;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.txnlog.TransactionLogType;

/**
 * MsgStore provides methods to persist/retrieve messages.
 */
class MsgStore {

    // properties used by the message store

    // property name and default of fd pool limit per destination
    static final String MESSAGE_FDPOOL_LIMIT_PROP
	    = FileStore.FILE_PROP_PREFIX + "message.fdpool.limit";
    static final int DEFAULT_MESSAGE_FDPOOL_LIMIT = 0;

    // property name and default of max number of files to keep in file pool
    // per destination
    static final String FILE_POOL_LIMIT_PROP
	    = FileStore.FILE_PROP_PREFIX + "destination.message.filepool.limit";
    static final int DEFAULT_MESSAGE_FILE_POOL_LIMIT = 100;

    // property name and default of file pool clean ratio
    static final String FILE_POOL_CLEANRATIO_PROP
	    = FileStore.FILE_PROP_PREFIX + "message.filepool.cleanratio";
    static final int DEFAULT_FILE_POOL_CLEANRATIO = 60;

    // property name and default of whether to clean up message store
    // when the broker exits
    static final String CLEANUP_MSGSTORE_PROP
		= FileStore.FILE_PROP_PREFIX + "message.cleanup";
    static final boolean DEFAULT_CLEANUP_MSGSTORE = true;

    // property name and default of initial vrfile size
    static final String INITIAL_VRFILE_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "message.vrfile.initial_size";
    static final long DEFAULT_INITIAL_VRFILE_SIZE = 1024; // 1024k=1m

    // property name and default of vrfile block size
    static final String VRFILE_BLOCK_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "message.vrfile.block_size";
    static final int DEFAULT_VRFILE_BLOCK_SIZE = 256;

    // property name and default of vrfile maximum record size
    static final String VRFILE_MAX_RECORD_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "message.max_record_size";
    static final long DEFAULT_VRFILE_MAX_RECORD_SIZE = 1024; // 1024k=1m

    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();
    private BrokerConfig config = Globals.getConfig();

    /**
     * directory hierarchy of the file based message store: <br>
     * "message" - directory to store messages, messages for each
     *		   destination will be located in its own directory which
     *		   is named using the destination name. Messages are either
     *		   stored in a vrfile or in their own individual files with
     *		   numeric file names.  The vrfile consists of variable
     *		   sized records. Each record either contains a message
     *		   and its corresponding interest list or is free to be reused.
     *		   The property imq.persist.file.message.max_record_size
     *		   controls whether a message is stored in the vrfile or
     *		   in its own file.
     */
    private static final String MESSAGE_DIR = "message" + File.separator;

    private File msgDir = null;

    // used for 1 message/file store
    int msgfdlimit = DEFAULT_MESSAGE_FDPOOL_LIMIT;
    int poollimit = DEFAULT_MESSAGE_FILE_POOL_LIMIT;
    int cleanratio = DEFAULT_FILE_POOL_CLEANRATIO;

    // used for vrfile store
    SizeString initialFileSize = null;	// vrfile initial file size
    SizeString maxRecordSize = null;	// maximum record size
    int blockSize = 0;			// vrfile block size

    // map destination to its messages ; DestinationUID->DstMsgStore
    private HashMap dstMap = new HashMap();

    protected FileStore parent = null;

    static final private Enumeration emptyEnum = new Enumeration() {
	public boolean hasMoreElements() {
	    return false;
	}
	public Object nextElement() {
	    return null;
	}
    };

    /**
     * Messages are loaded on demand.
     * if reset is true, remove all messages.
     */
    MsgStore(FileStore p, File top, boolean reset)
	throws BrokerException {

	init(p, top);

	if (reset) {
	    if (Store.getDEBUG()) {
		logger.log(logger.DEBUGHIGH,
			"MsgStore initialized with reset option");
	    }

	    // clear messages
	    clearAll(false);
	}
    }

    private void init(FileStore p, File top)
	throws BrokerException {

	msgfdlimit = config.getIntProperty(MESSAGE_FDPOOL_LIMIT_PROP,
					DEFAULT_MESSAGE_FDPOOL_LIMIT);

	poollimit = config.getIntProperty(FILE_POOL_LIMIT_PROP,
					DEFAULT_MESSAGE_FILE_POOL_LIMIT);

        cleanratio = config.getIntProperty(FILE_POOL_CLEANRATIO_PROP,
                                        DEFAULT_FILE_POOL_CLEANRATIO);

	initialFileSize = config.getSizeProperty(INITIAL_VRFILE_SIZE_PROP,
					DEFAULT_INITIAL_VRFILE_SIZE);

	blockSize = config.getIntProperty(VRFILE_BLOCK_SIZE_PROP,
					DEFAULT_VRFILE_BLOCK_SIZE);

	maxRecordSize = config.getSizeProperty(VRFILE_MAX_RECORD_SIZE_PROP,
					DEFAULT_VRFILE_MAX_RECORD_SIZE);

	msgDir = new File(top, MESSAGE_DIR);
	if (!msgDir.exists() && !msgDir.mkdirs()) {
	    logger.log(logger.ERROR, br.E_CANNOT_CREATE_STORE_HIERARCHY,
			msgDir.toString());
	    throw new BrokerException(br.getString(
					br.E_CANNOT_CREATE_STORE_HIERARCHY,
					msgDir.toString()));
	}

	this.parent = p;
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	synchronized (dstMap) {
	    Iterator itr = dstMap.values().iterator();
	    while (itr.hasNext()) {
		DstMsgStore dstMsgStore = (DstMsgStore)itr.next();
		t.putAll(dstMsgStore.getDebugState());
	    }
	}
	return t;
    }

    /**
     * Store a message, which is uniquely identified by it's SysMessageID,
     * and it's list of interests and their states.
     *
     * @param message	the message to be persisted
     * @param iids	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the data
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     */
    void storeMessage(DestinationUID dst, Packet message, ConsumerUID[] iids,
			int[] states, boolean sync) throws IOException, BrokerException {

		if (Store.getDEBUG()) {
			logger.log(Logger.DEBUGHIGH, "storeMessage for " + dst);
		}

		if (Globals.isNewTxnLogEnabled() && TransactionLogManager.logNonTransactedMsgSend  && !TransactionLogManager.isReplayInProgress()) {
			long tid = message.getTransactionID();
			if (tid <= 0) {

				TransactionWorkMessage twm = new TransactionWorkMessage(dst,
						message, iids);
				parent.logNonTxnMessage(twm);
				// set sync to false as we have already logged
				sync=false;
			}
		}
		// get from cache; instantiate=true, load=true, create=true
		DstMsgStore msgstore = getDstMsgStore(dst, true, true, true);

		MessageInfo info = msgstore.storeMessage(message, iids, states, sync);

		// log message produce
		if (Globals.logNonTransactedMsgSend() && !Globals.isNewTxnLogEnabled()) {

			// old txn log
			long tid = message.getTransactionID();
			if (tid <= 0) {
				byte[] msgBytes = info.getCachedMessageBytes();
				if (msgBytes == null) {
					msgBytes = message.getBytes();
				}

				ByteBuffer bbuf = ByteBuffer.allocate(msgBytes.length + 12);
				bbuf.putLong(tid); // Transaction ID (8 bytes)
				bbuf.putInt(1); // Number of msgs (4 bytes)
				bbuf.put(msgBytes);

				parent.logTxn(TransactionLogType.PRODUCE_TRANSACTION, bbuf
						.array());
			}
		}

	}

    /**
	 * Return a message with the specified message id.
	 */
    Packet getMessage(DestinationUID dst, SysMessageID mid)
	throws BrokerException {

	try {
            return getDstMsgStore(dst).getMessage(mid);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_MESSAGE_FAILED,
				mid.toString(), e);
	    throw new BrokerException(
				br.getString(br.X_LOAD_MESSAGE_FAILED,
				mid.toString()), e);
	}
    }

    /**
     * Tests whether the message exists.
     */
    boolean containsMessage(DestinationUID dst, SysMessageID mid)
	throws BrokerException {

        // get from cache; instantiate=true, load=true, create=true
        DstMsgStore dstMsgStore = getDstMsgStore(dst, true, true, true);
        return dstMsgStore.containsMsg(mid);
    }

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param id	the system message id of the message to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     */
    void removeMessage(DestinationUID dst, SysMessageID id, boolean sync)
	throws IOException, BrokerException {

        getDstMsgStore(dst).removeMessage(id, sync);
    }

    void moveMessage(Packet message, DestinationUID from,
	DestinationUID to, ConsumerUID[] ints, int[] states, boolean sync)
	throws IOException, BrokerException {

	SysMessageID mid = message.getSysMessageID();

	// sanity check
	// get from cache; instantiate=true, load=true, create=false
	DstMsgStore fromdst = getDstMsgStore(from, true, true, false);
	if (fromdst == null || !fromdst.containsMsg(mid)) {
	    logger.log(logger.ERROR, br.E_MSG_NOT_FOUND_IN_STORE, mid, from);
	    throw new BrokerException(
                br.getString(br.E_MSG_NOT_FOUND_IN_STORE, mid, from));
	}

	// first save the message and then remove the message
	storeMessage(to, message, ints, states, sync);

	try {
	    fromdst.removeMessage(message.getSysMessageID(), sync);
	} catch (BrokerException e) {
	    // if we fails to remove the message; undo store
	    getDstMsgStore(to).removeMessage(message.getSysMessageID(), sync);

	    Object[] args = { mid, from, to };
	    logger.log(logger.ERROR, br.X_MOVE_MESSAGE_FAILED, args, e);
	    throw e;
	}
    }

    /**
     * Check if a a message has been acknowledged by all interests.
     *
     * @param dst  the destination the message is associated with
     * @param mid   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked(DestinationUID dst, SysMessageID mid)
	throws BrokerException {

        return getDstMsgStore(dst).getMessageInfo(mid).hasMessageBeenAck();
    }

    /**
     * Get information about the underlying storage for the specified
     * destination. Only return info about vrfile.
     * @return A HashMap of name value pair of information
     */
    public HashMap getStorageInfo(Destination destination)
	throws BrokerException {

	// get from cache and instantiate if not found
	DstMsgStore msgfile = getDstMsgStore(destination.getDestinationUID(),
						true, false, false);

	if (msgfile != null) {
	    return msgfile.getStorageInfo();
	} else {
	    // no info to return
	    return new HashMap();
	}
    }

    /**
     * Compact the vrfile associated with the specified destination.
     * If null is specified, data assocated with all persisted destination
     * will be compacted.
     */
    void compactDestination(Destination destination) throws BrokerException {
	if (destination != null) {

	    // get from cache and instantiate if not found
	    DstMsgStore msgfile = getDstMsgStore(
					destination.getDestinationUID(),
					true, false, false);

	    if (msgfile != null) {
		msgfile.compact();
	    }
	} else {
	    Iterator itr = null;
	    synchronized (dstMap) {
		itr = ((HashMap)dstMap.clone()).values().iterator();
	    }

	    while (itr.hasNext()) {
		DstMsgStore file = (DstMsgStore)itr.next();
		file.compact();
	    }
	}
    }

    /**
     * Remove all messages associated with the specified destination
     * from the persistent store.
     *
     * @param dst	the destination whose messages are to be removed
     * @exception IOException if an error occurs while removing the messages
     * @exception BrokerException if the destination is not found in the store
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    void removeAllMessages(DestinationUID dst, boolean sync)
	throws IOException, BrokerException {

	// get from cache and instantiate if not found
	DstMsgStore dstMsgStore = getDstMsgStore(dst, true, false, false);

	if (dstMsgStore != null) {
	    dstMsgStore.removeAllMessages(sync);
	}
    }

    /**
     * Destination is being removed and so the associated
     * backing file for it's messages can be released.
     */
    void releaseMessageDir(DestinationUID dst, boolean sync)
	throws IOException, BrokerException {

	synchronized (dstMap) {
	    DstMsgStore dstMsgStore = (DstMsgStore)dstMap.remove(dst);

	    if (dstMsgStore != null) {
		dstMsgStore.releaseMessageDir(sync);
	    }
	}
    }

    /**
     * Return an enumeration of all persisted messages.
     * Use the Enumeration methods on the returned object to fetch
     * and load each message sequentially.
     * Not synchronized.
     *
     * <p>
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @return an enumeration of all persisted messages.
     */
    Enumeration messageEnumeration() {
	Enumeration msgEnum = new Enumeration() {
	    // get all destinations
	    Iterator dstitr =
		parent.getDstStore().getDestinations().iterator();
	    Enumeration tempenum = null;
	    Object nextToReturn = null;

	    // will enumerate through all messages of all destinations
	    public boolean hasMoreElements() {
		while (true) {
		    if (tempenum != null) {
			if (tempenum.hasMoreElements()) {
			    // got the next message
			    nextToReturn = tempenum.nextElement(); 
			    return true;
			} else {
			    // continue to get the next enumeration
			    tempenum = null;
			}
		    } else {
			// get next enumeration
			while (dstitr.hasNext()) {
			    Destination dst = (Destination)dstitr.next();
			    try {
				// got the next enumeration
			    	tempenum = messageEnumeration(
						dst.getDestinationUID());
				break;
			    } catch (BrokerException e) {
				// log error and try to load messages for
				// the next destionation
				logger.log(logger.ERROR,
					br.X_LOAD_MESSAGES_FOR_DST_FAILED,
					dst.getDestinationUID(), e);
			    }
			}
			if (tempenum == null) {
			    // no more
			    return false;
			}
		    }
		}
	    }

	    public Object nextElement() {
		if (nextToReturn != null) {
		    Object tmp = nextToReturn;
		    nextToReturn = null;
		    return tmp;
		} else {
		    throw new NoSuchElementException();
		}
	    }
	};

	return msgEnum;
    }

    /**
     * Return an enumeration of all persisted messages for the given
     * destination.
     * Use the Enumeration methods on the returned object to fetch
     * and load each message sequentially.
     *
     * <p>
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @return an enumeration of all persisted messages.
     */
    Enumeration messageEnumeration(DestinationUID dst) throws BrokerException {

	// get from cache and instantiate if not found
	DstMsgStore dstMsgStore = getDstMsgStore(dst, true, false, false);

	if (dstMsgStore != null) {
	    return dstMsgStore.messageEnumeration();
	} else {
	    return emptyEnum;
	}
    }

    /**
     * Return the message count for the given destination.
     */
    int getMessageCount(DestinationUID dst) throws BrokerException {

	// get from cache and instantiate if not found
	DstMsgStore dstMsgStore = getDstMsgStore(dst, true, false, false);

	if (dstMsgStore != null) {
	    return dstMsgStore.getMessageCount();
	} else {
	    return 0;
	}
    }

    /**
     * Return the byte count for the given destination.
     */
    long getByteCount(DestinationUID dst) throws BrokerException {

	// get from cache and instantiate if not found
	DstMsgStore dstMsgStore = getDstMsgStore(dst, true, false, false);

	if (dstMsgStore != null) {
	    return dstMsgStore.getByteCount();
	} else {
	    return 0;
	}
    }

    /**
     * Store the given list of interests and their states with the
     * specified message.  The message should not have an interest
     * list associated with it yet.
     *
     * @param mid	system message id of the message that the interest
     *			is associated with
     * @param iids	an array of interest ids whose states are to be
     *			stored
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk

     * @exception BrokerException if the message is not in the store;
     *				if there's an interest list associated with
     *				the message already; or if an error occurs
     *				while persisting the data
     */
    void storeInterestStates(DestinationUID dst,
        SysMessageID mid, ConsumerUID[] iids, int[] states, boolean sync)
        throws BrokerException {

        try {
            if(Globals.isNewTxnLogEnabled()) {
               /**
                * If using fast txn optimisation, a message 
                * may not have been added to store yet, hence
                * destination may not have been created.
                * Force creation now.	
                */
               getDstMsgStore(dst, true, true, true);
	    }
	
            getDstMsgStore(dst).storeInterestStates(mid, iids, states, sync);
        } catch (IOException e) {
            logger.log(logger.ERROR, br.X_PERSIST_INTEREST_LIST_FAILED,
                       mid.toString());
            throw new BrokerException(
                br.getString(br.X_PERSIST_INTEREST_LIST_FAILED,
                             mid.toString()), e);
        }
    }

    /**
     * Update the state of the interest associated with the specified
     * message.  The interest should already be in the interest list
     * of the message.
     *
     * @param mid	system message id of the message that the interest
     *			is associated with
     * @param iid	id of the interest whose state is to be updated
     * @param state	state of the interest
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not in the store; if the
     *			interest is not associated with the message; or if
     *			an error occurs while persisting the data
     */
    void updateInterestState(DestinationUID dst, SysMessageID mid,
	ConsumerUID iid, int state, boolean sync) throws BrokerException {

	try {
            getDstMsgStore(dst).updateInterestState(mid, iid, state, sync);
	} catch (IOException e) {
	    // only this state is affected
	    logger.log(logger.ERROR, br.X_PERSIST_INTEREST_STATE_FAILED,
				iid.toString(), mid.toString());
	    throw new BrokerException(
			br.getString(br.X_PERSIST_INTEREST_STATE_FAILED,
			iid.toString(), mid.toString()), e);
	}
    }

    int getInterestState(DestinationUID dst, SysMessageID mid, ConsumerUID iid)
	throws BrokerException {

        return getDstMsgStore(dst).getMessageInfo(mid).getInterestState(iid);
    }

    HashMap getInterestStates(DestinationUID dst, SysMessageID mid)
        throws BrokerException {

        return getDstMsgStore(dst).getMessageInfo(mid).getInterestStates();
    }

    /**
     * don't return id with state==INTEREST_STATE_ACKNOWLEDGED
     */
    ConsumerUID[] getConsumerUIDs(DestinationUID dst, SysMessageID mid)
	throws BrokerException {

        return getDstMsgStore(dst).getMessageInfo(mid).getConsumerUIDs();
    }

    // clear the store; when this method returns, the store has a state
    // that is the same as an empty store (same as when MsgStore
    // is instantiated with the clear argument set to true
    void clearAll(boolean sync) throws BrokerException {

        // clear all maps and internal data
        // delete all files

        // synchronized on messageMap; give a chance to other threads to finish
        synchronized (dstMap) {
            // false=no clean up needed
            // since we are going to delete all files afterwards
            closeAllDstMsgStore(false);
            dstMap.clear();

            // delete all files under the message directory
            try {
                FileUtil.removeFiles(msgDir, false);
            } catch (IOException e) {
                logger.log(logger.ERROR, br.X_RESET_MESSAGES_FAILED, msgDir, e);
                throw new BrokerException(
                    br.getString(br.X_RESET_MESSAGES_FAILED, msgDir), e);
            }
        }
    }

    void sync(DestinationUID dst) throws BrokerException {
        DstMsgStore dstMsgStore = null;
	synchronized (dstMap) {
	    dstMsgStore = (DstMsgStore)dstMap.get(dst);
	}

        if (dstMsgStore != null) {
            dstMsgStore.sync();
        }
    }

    // synchronized by caller
    void close(boolean cleanup) {

	boolean msgCleanup = config.getBooleanProperty(CLEANUP_MSGSTORE_PROP,
				DEFAULT_CLEANUP_MSGSTORE);

	closeAllDstMsgStore(msgCleanup);

	dstMap.clear();
    }

    /**
     * Construct the directory name for the given destination.
     * TODO: need to handle the case of long destination name
     */
    File getDirName(DestinationUID dst) {
	StringBuffer dstname = new StringBuffer(dst.toString());
        // remove any : in the file - disallowed on win32
        int indx = -1;
        while ((indx = dstname.indexOf(":")) != -1) {
            dstname.deleteCharAt(indx);
        }
	return new File(msgDir, dstname.toString());
    }

    private DstMsgStore getDstMsgStore(DestinationUID dst)
	throws BrokerException {

        DstMsgStore dstMsgStore = null;
	synchronized (dstMap) {
	    dstMsgStore = (DstMsgStore)dstMap.get(dst);
	}

        if (dstMsgStore == null) {
            String emsg = br.getKString(br.E_DESTINATION_NOT_FOUND_IN_STORE, dst);
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg);
        } else {
            return dstMsgStore;
        }
    }

    /**
     * synchronized access to dstMap to get DstMsgStore
     * 1. get it from cache
     * 2. if not found and instantiate is true, get file name and try to
     *    instantiate a DstMsgStore object passing in the load argument
     * 3. instantiation is done only if the directory already exists or the
     *    create argument is true.
     *
     * @exception BrokerException if the destination is not found in the store
     */
    private DstMsgStore getDstMsgStore(DestinationUID dst, boolean instantiate,
	boolean load, boolean create) throws BrokerException {

	// throw exception if dst is not found
	parent.getDstStore().checkDestination(dst.toString());

	DstMsgStore dstMsgStore = null;

	/**
	 * it's important that we synchronize on dstMap when getting
	 * a new DstMsgStore so that only 1 DstMsgStore will be
	 * instantiated.
	 */
        try {
            synchronized (dstMap) {
                dstMsgStore = (DstMsgStore)dstMap.get(dst);

                if (dstMsgStore == null && instantiate) {
                    File dir = getDirName(dst);

                    // only do it if the file exists or create is true
                    if (dir.exists() || create) {
                        dstMsgStore = new DstMsgStore(this, dst, dir, load);
                        dstMap.put(dst, dstMsgStore);
                    }
                }
            }
        } catch (BrokerException ex) {
            // Fatal error if unable to create store directory; bug 6173086
            if ((instantiate || create) &&
                (ex.getStatusCode() == Status.NOT_ALLOWED)) {
                logger.log(Logger.ERROR, ex.toString());
                Broker.getBroker().exit(BrokerExitCode.IOEXCEPTION,
                    ex.toString(), BrokerEvent.Type.FATAL_ERROR, ex,
                    true, true, false);
            }

            throw ex;
        }

	return dstMsgStore;
    }

    // close all stores
    private void closeAllDstMsgStore(boolean msgCleanup) {

	Iterator itr = dstMap.values().iterator();
	while (itr.hasNext()) {
	    DstMsgStore dstMsgStore = (DstMsgStore)itr.next();
	    if (dstMsgStore != null)
	    	dstMsgStore.close(msgCleanup);
	}
    }
}
