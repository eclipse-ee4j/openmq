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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.disk.VRFile;
import com.sun.messaging.jmq.io.disk.VRFileRAF;
import com.sun.messaging.jmq.io.disk.VRecordRAF;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * DstMsgStore keeps track of messages for a destination. Messages are either
 * stored in records in a vrfile or in their own file.
 */
class PreparedTxnStore extends RandomAccessStore {

	BrokerConfig config = Globals.getConfig();
	
	
	static final String USE_FILE_CHANNEL_PROP = FileStore.FILE_PROP_PREFIX
			+ "message.use_file_channel";

	static String VRFILE_NAME = "vrfile";

	/* == variables used for storing/retrieving messages == */

	String storeName = "incompleteTxnStore";

	// cache of all messages of the destination; message id -> MessageInfo
	private ConcurrentHashMap<TransactionUID, TransactionWorkInfo> transactionMap = new ConcurrentHashMap<TransactionUID, TransactionWorkInfo>(
			1000);

	//
	// These variables are related to initial loading of messages
	//
	private AtomicBoolean loaded = new AtomicBoolean(false);

	// Added byt Tom Ross fix for bug 6431962
	// 16 June 2006

	// initializing file store properties
	// growth_factor (default 50%)
	// this is a percentage of hte current size by which the file store will
	// grow when grown
	protected float growthFactor = config.getPercentageProperty(Globals.IMQ
			+ ".persist.file.message.vrfile.growth_factor",
			VRFile.DEFAULT_GROWTH_FACTOR);

	// threshold file size after which VR file start to grow
	protected long threshold = config.getLongProperty(Globals.IMQ
			+ ".persist.file.message.vrfile.threshold",
			VRFile.DEFAULT_THRESHOLD);

	// this is the new value by which a file store will be grow by
	// when its size reaches the treshold above
	protected float thresholdFactor = config.getPercentageProperty(Globals.IMQ
			+ ".persist.file.message.vrfile.threshold_factor",
			VRFile.DEFAULT_THRESHOLD_FACTOR);

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
	 * The txnCount and byteCount are 
         * - initialized when the PreparedTxnStore is instantiated
         * - incremented when new transaction is stored 
         * - decremented when transactions are removed
         * - reset to 0 when all transactions are removed
	 */
	private int txnCount = 0;
	private long byteCount = 0;
	private Object countLock = new Object();

	// backing file
	private VRFileRAF vrfile = null;

	int maxRecordSize = 0;

	PreparedTxnStore(MsgStore p, File dir, boolean load) throws BrokerException {

		super(dir, p.msgfdlimit, p.poollimit, p.cleanratio);
		try {
			long fsize = p.initialFileSize.getBytes();
			if (fsize > 0) {
				maxRecordSize = (int) p.maxRecordSize.getBytes();

				vrfile = new VRFileRAF(new File(dir, VRFILE_NAME), fsize, 
                                             Globals.isMinimumWritesFileStore(), Broker.isInProcess());
				vrfile.setBlockSize(p.blockSize);
				

				try {
					vrfile.setGrowthFactor(growthFactor);
				} catch (IllegalArgumentException iiEx) {

					vrfile.setGrowthFactor(VRFile.DEFAULT_GROWTH_FACTOR);
					growthFactor = VRFile.DEFAULT_GROWTH_FACTOR;

					logger.log(Logger.INFO,
									"Invalid growth_factor value. Using default value of 50%.");
				}

				try {
					vrfile.setThreshold(threshold);
				} catch (IllegalArgumentException iiEx) {

					vrfile.setThreshold(VRFile.DEFAULT_THRESHOLD);
					threshold = VRFile.DEFAULT_THRESHOLD;

					logger.log(Logger.INFO,
									"Invalid threshold value. Using default value of 0.");
				}

				try {
					vrfile.setThresholdFactor(thresholdFactor);
				} catch (IllegalArgumentException iiEx) {

					vrfile.setThresholdFactor(VRFile.DEFAULT_THRESHOLD_FACTOR);
					thresholdFactor = VRFile.DEFAULT_THRESHOLD_FACTOR;

					logger.log(Logger.INFO,
									"Invalid threshold_factor value. Using default value of 0%.");
				}

				try {
					if (threshold != 0 || thresholdFactor != 0.0f) {
						vrfile.checkGrowthFactorSanity();
					}
				} catch (IllegalStateException isEx) {
					String exMsg = isEx.getMessage();
					logger.log(Logger.INFO, exMsg);
				}

				try {
					// this cookie value will be written to the file header if it is
					// being created for the first time
					// if file exists, then this value will be ignored
					
					vrfile.setCookie(BaseTransaction.CURRENT_FORMAT_VERSION);
					vrfile.open();
					
					// read back actual file cookie
					long originalCookie = vrfile.getFileCookie();
					if (originalCookie < BaseTransaction.CURRENT_FORMAT_VERSION) {
						String msg = "Existing file: "
								+ storeName
								+ "has older cookie version than current version. "
								+ "Current version = " + BaseTransaction.CURRENT_FORMAT_VERSION
								+ ". Original file version = " + originalCookie;
						logger.log(Logger.WARNING, msg);
					} else if (originalCookie > BaseTransaction.CURRENT_FORMAT_VERSION) {
						String msg = "Existing file: "
								+ storeName
								+ " has newer cookie version than current version. "
								+ "Current version = " + BaseTransaction.CURRENT_FORMAT_VERSION
								+ ". Original file version = " + originalCookie;
						logger.log(Logger.ERROR, msg);

					}
					
					
				} catch (com.sun.messaging.jmq.io.disk.VRFileWarning e) {
					logger.log(Logger.WARNING, "possible data loss for "
							+ storeName, e);
				}
			}

			// initialize message count and byte count
			initCounts();
		} catch (IOException e) {
			logger.log(Logger.ERROR, "failed to load " + storeName, e);
			throw new BrokerException("failed to load " + storeName, e);
		} catch (Throwable t) {
			logger.log(Logger.ERROR, "failed to load " + storeName, t);
			throw new BrokerException("failed to load " + storeName, t);
		}

		if (load) {
			loadTransactions();
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
		info.put(DestMetricsCounters.DISK_UTILIZATION_RATIO, Integer.valueOf(
				(int) (vrfile.getUtilizationRatio() * 100)));
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
					logger.log(Logger.WARNING, "possible data loss for "
							+ storeName, e);
				}

				try {
					vrfile.open();
				} catch (com.sun.messaging.jmq.io.disk.VRFileWarning e) {
					logger.log(logger.WARNING, "possible data loss for "
							+ storeName, e);
				}

				// After the file is compacted, all VRecords needs to be
				// reloaded.
				transactionMap.clear();
				Iterator itr = vrfile.getRecords().iterator();
				Enumeration e = new TxnEnumeration(this, itr, emptyEnum);
				while (e.hasMoreElements()) {
					e.nextElement();
				}
			} catch (IOException e) {
				throw new BrokerException("Failed to compact file: "
						+ vrfile.getFile(), e);
			}
		}
	}

	TransactionWorkInfo storeTransaction(BaseTransaction baseTxn, boolean sync)
			throws IOException, BrokerException {

		TransactionUID id = baseTxn.getTid();

		// just check the cached map; all messages should be
		// loaded before this is called
		if (transactionMap.containsKey(id)) {
                    String emsg = br.getKString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id)+
                                                             ": "+storeName;
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
		}

		try {

			byte[] data = baseTxn.writeToBytes();

			int msgsize = data.length;
			if(sync&&Store.getDEBUG_SYNC())
			{
				String msg = " PreparedTxnStore storeTransaction sync " +baseTxn;
				Globals.getLogger().log(Logger.DEBUG, msg);
			}

			TransactionWorkInfo info = null;
			if (vrfile != null
					&& (maxRecordSize == 0 || msgsize < maxRecordSize)) {

				// store in vrfil		
				info = new TransactionWorkInfo(this, vrfile, baseTxn, data, sync);
			} else {
				// store in individual file
				info = new TransactionWorkInfo(this, baseTxn, data, sync);
			}

			// cache it, make sure to use the cloned SysMessageID		
			transactionMap.put(info.getID(), info);

			// increate destination message count and byte count
			incrTxnCount(msgsize);

			return info;
		} catch (IOException e) {
			logger.log(logger.ERROR, br.X_PERSIST_MESSAGE_FAILED,
					id.toString(), e);
			throw e;
		}
	}

	/**
	 * Remove the message from the persistent store. If the message has an
	 * interest list, the interest list will be removed as well.
	 * 
	 * @param id
	 *            the system message id of the message to be removed
	 * @exception IOException
	 *                if an error occurs while removing the message
	 * @exception BrokerException
	 *                if the message is not found in the store
	 */
	void removeTransaction(TransactionUID id, boolean sync)
			throws BrokerException {

		TransactionWorkInfo oldmsg = (TransactionWorkInfo) transactionMap
				.remove(id);

		if (oldmsg == null) {
                    String emsg = br.getKString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id)+
                                                             ": "+storeName;
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
		}

		try {
			oldmsg.free(sync);
		} catch (IOException e) {
			throw new BrokerException("failed to free transaction", e);
		}

		// decrement destination message count and byte count
		decrTxnCount(oldmsg.getSize());
	}

	/**
	 * Remove all messages associated with this destination.
	 * 
	 * @exception IOException
	 *                if an error occurs while removing the message
	 * @exception BrokerException
	 *                if the message is not found in the store
	 */
	void removeAllTransactions(boolean sync) throws IOException,
			BrokerException {

		if (vrfile != null) {
			vrfile.clear(false); // false->don't truncate
		}

		removeAllData(sync);

		transactionMap.clear();
		clearCounts();
	}

	// will delete the whole directory hierarchy
	void releasePreparedTxnDir(boolean sync) throws IOException {

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
		transactionMap.clear();
		clearCounts();
	}

	/**
	 * Return an enumeration of all persisted messages. Use the Enumeration
	 * methods on the returned object to fetch and load each message
	 * sequentially.
	 * 
	 * <p>
	 * This method is to be used at broker startup to load persisted messages on
	 * demand.
	 * 
	 * @return an enumeration of all persisted messages.
	 */
	Enumeration<BaseTransaction> txnEnumeration() {

		if (loaded.get()) {
			if (Store.getDEBUG()) {
				String msg = getPrefix()
						+ " returning getTransactionIterator()";
				logger.log(Logger.DEBUG, msg);
			}
			return new TxnEnumeration(this, getTransactionIterator());
		} else {
			Iterator recitr = null;
			if (vrfile != null) {
				recitr = vrfile.getRecords().iterator();
			}
			// false -> not peek only but load message
			if (Store.getDEBUG()) {
				String msg = getPrefix() + " returning getEnumeration()";
				logger.log(logger.DEBUG, msg);
			}
			return new TxnEnumeration(this, recitr, getFileEnumeration());
		}
	}

	/**
	 * return the number of transactions in this file
	 */
	int getTransactionCount() throws BrokerException {
		if (Store.getDEBUG()) {
			logger.log(logger.DEBUG, "PreparedTxnStore:getTransactionCount() "
					+ txnCount);
		}

		return txnCount;
	}

	/**
	 * return the number of bytes in this file
	 */
	long getByteCount() throws BrokerException {
		if (Store.getDEBUG()) {
			logger.log(logger.DEBUG, "PreparedTxnStore:getByteCount()");
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

		transactionMap.clear();
	}

	VRFileRAF getVRFile() {
		return vrfile;
	}

	/**
	 * Load all messages in the backing file.
	 * 
	 * returned if no messages exist in the store
	 * 
	 * @exception BrokerException
	 *                if an error occurs while getting the data
	 */
	public void loadTransactions() throws BrokerException {

		Enumeration<BaseTransaction> e = this.txnEnumeration();
		while (e.hasMoreElements()) {
			e.nextElement();
		}

		logger.log(Logger.DEBUG, getPrefix() + " loaded "
				+ transactionMap.size() + " transactions");
	}

	TransactionWorkInfo getTransactionInfo(TransactionUID tid)
			throws BrokerException {

		TransactionWorkInfo info = transactionMap.get(tid);
		if (info == null) {
                    String emsg = br.getKString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, tid)+
                                                             ": "+storeName;
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
		}

		return info;
	}

	// BEGIN: implement super class (RandomAccessStore) abstract method
	/**
	 * parse the message and it's associated interest list from the given
	 * buffers. This is loaded from individual message files. Returns the
	 * sysMessageID.
	 */
	Object parseData(byte[] data, byte[] attachment) throws IOException {

		TransactionWorkInfo minfo = new TransactionWorkInfo(this, data,
				attachment);

		// if everything is ok, we cache it
		// make sure to use the cloned SysMessageID
		TransactionUID tid = minfo.getID();
		transactionMap.put(tid, minfo);

		return tid;
	}

	FilenameFilter getFilenameFilter() {
		return vrfileFilter;
	}

	// END: implement super class (RandomAccessStore) abstract method

	// synchronized access to messageMap.put()
	private void cacheMessageInfo(TransactionWorkInfo minfo) {
		transactionMap.put(minfo.getID(), minfo);
	}

	// synchronized access to messageMap.keySet().iterator();
	private Iterator getTransactionIterator() {
		return transactionMap.keySet().iterator();
	}

	private void setLoadedFlag(boolean flag) {
		loaded.set(flag);
	}

	private void incrTxnCount(int msgSize) throws BrokerException {
		synchronized (countLock) {
			txnCount++;
			byteCount += msgSize;
		}
	}

	private void decrTxnCount(int msgSize) throws BrokerException {
		synchronized (countLock) {
			txnCount--;
			byteCount -= msgSize;
		}
	}

	private void clearCounts() {
		if (Store.getDEBUG()) {
			logger.log(logger.DEBUG, "DstMsgStore:clearCounts for "
					+ storeName);
		}

		synchronized (countLock) {
			txnCount = 0;
			byteCount = 0;
		}
	}

	/**
	 * The msgCount and byteCount are - initialized when the DstMsgStore is
	 * instantiated - incremented when new messages are stored - decremented
	 * when messages are removed - and reset to 0 when the dst is purged or
	 * removed
	 */
	private void initCounts() throws BrokerException {

		// check vrfile first
		if (vrfile != null) {
			Set msgs = vrfile.getRecords();

			Iterator itr = msgs.iterator();
			while (itr.hasNext()) {
				// get all packetSize
				VRecordRAF record = (VRecordRAF) itr.next();

				// sanity check
				short cookie = record.getCookie();
				if (cookie == MessageInfo.PENDING || cookie != MessageInfo.DONE) {

					// writing not finish: log error, free record
					String warning = storeName + ": found a "
							+ "corrupted message at vrecord(" + record
							+ "), a message might be lost";
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
						txnCount++;
						byteCount += record.readInt();
					} catch (Throwable t) {
						logger.log(logger.ERROR, br.X_READ_FROM_VRECORD_FAILED,
								vrfile.getFile(), t);
						throw new BrokerException(br
								.getString(br.X_READ_FROM_VRECORD_FAILED,
										vrfile.getFile()), t);
					}
				}
			}
		}

                long[] cnts = initCountsFromIndividualFiles();
                txnCount += (int)cnts[0];
                byteCount += cnts[1];

		if (Store.getDEBUG()) {
			logger.log(logger.DEBUG, "DstMsgStore: initialized " + "msg count="
					+ txnCount + "; byte count = " + byteCount);
		}
	}

	private static class TxnEnumeration implements Enumeration {
		PreparedTxnStore parent = null;
		Iterator itr = null;
		Iterator recitr = null;
		Enumeration msgEnum = null;

		Logger logger = Globals.getLogger();
		BrokerResources br = Globals.getBrokerResources();

		Object objToReturn = null;

		TxnEnumeration(PreparedTxnStore p, Iterator i) {
			parent = p;
			itr = i;
		}

		TxnEnumeration(PreparedTxnStore p, Iterator i, Enumeration e) {
			parent = p;
			recitr = i;
			msgEnum = e;
		}

		public boolean hasMoreElements() {
			Packet msg = null;
			if (itr != null) {
				if (itr.hasNext()) {
					objToReturn = itr.next();
					return true; // RETURN TRUE
				} else {
					return false;
				}
			} else {
				if (recitr != null) {
					while (recitr.hasNext()) {
						// load message from VRecordRAF
						try {
							TransactionWorkInfo minfo = new TransactionWorkInfo(
									parent, (VRecordRAF) recitr.next());
							objToReturn = minfo.getMessage();

							// first time loaded from file; cache info
							parent.cacheMessageInfo(minfo);

							return true; // RETURN TRUE
						} catch (IOException e) {
							// log an error and continue
							logger.log(logger.ERROR, br.X_PARSE_MESSAGE_FAILED,
									parent.storeName, e);
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
				Object result = null;
				;
				if (objToReturn instanceof TransactionUID) {
					try {
						result = parent
								.getTransaction((TransactionUID) objToReturn);
					} catch (IOException e) {
						// failed to load message
						// log error; and continue
						logger.log(logger.ERROR, br.X_RETRIEVE_MESSAGE_FAILED,
								objToReturn, parent.storeName, e);
						throw new NoSuchElementException();
					} catch (BrokerException e) {
						// msg not found
						// log error; and continue
						logger.log(logger.ERROR, br.X_RETRIEVE_MESSAGE_FAILED,
								objToReturn, parent.storeName, e);
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
	 * 
	 * @return A Hashtable of name value pair of information
	 */
	Hashtable getDebugState() {
		int numInVrfile = 0;
		if (vrfile != null) {
			numInVrfile = vrfile.getNRecords();
		}
		int numInFiles = txnCount - numInVrfile;

		Hashtable t = new Hashtable();
		t.put((storeName + ":messages in vrfile"), String
				.valueOf(numInVrfile));
		t.put((storeName + ":messages in its own file"), String
				.valueOf(numInFiles));
		return t;
	}

	boolean containsTransaction(TransactionUID tid) {
		return transactionMap.containsKey(tid);
	}

	BaseTransaction getTransaction(TransactionUID mid) throws IOException,
			BrokerException {
		TransactionWorkInfo msginfo = (TransactionWorkInfo) transactionMap
				.get(mid);
		if (msginfo == null) {
                    String emsg = br.getKString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, mid)+
                                                             ": "+storeName;
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
		} else {
			return msginfo.getMessage();
		}
	}

	void sync() throws BrokerException {
		try {
			if(Store.getDEBUG_SYNC())
			{
				String msg = "PreparedtxnStore sync() ";
				logger.log(Logger.DEBUG,msg);
			}
                        if (vrfile != null) {
 	 		vrfile.force();
                        }
		} catch (IOException e) {
			throw new BrokerException(
					"Failed to synchronize data to disk for file: " + vrfile, e);
		}
	}

	void updateTransactionState(TransactionUID tid, int state, boolean sync)
			throws IOException, BrokerException {

		getTransactionInfo(tid).updateState(state, sync);
	}

	void updateTransactionCompletion(TransactionUID tid, boolean complete, boolean sync)
			throws IOException, BrokerException {

		int completionVal = (complete?1:0);
		getTransactionInfo(tid).updateCompletion(completionVal, sync);
	}

	String getPrefix() {
		return "PreparedTxStore: " + Thread.currentThread().getName();
	}
}
