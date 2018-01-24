/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessageAck;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.WaitTimeoutException;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.txnlog.CheckPointListener;
import com.sun.messaging.jmq.io.txnlog.TransactionLogRecord;
import com.sun.messaging.jmq.io.txnlog.TransactionLogWriter;
import com.sun.messaging.jmq.io.txnlog.file.FileTransactionLogWriter;

/**
 * @author gsivewright
 * 
 * This class manages the transfer of data between the message store and the
 * transaction log.
 * 
 */

/*
 * Order of handling prepared txns
 * 
 * The following scenario describes a sequence of updates to the transaction log
 * and message store. This sequence is used to illustrate how consistent state
 * can be recreated on restart after a failure of the broker at various specific
 * processing points.
 * 
 * 1.write prepared txn to txnLog 2.checkpoint1 (see checkpoint sequence)
 * 3.write commit record to txnLog 4.write txn to store 5.write commit record to
 * preparedTxnStore 6.checkpoint2 (see checkpoint sequence)
 * 
 * checkpoint sequence:
 * 
 * 1.sync message store 2.write prepared txns (since last checkpoint) to
 * prepared store 3.sync prepareTxnStore 4.reset txn log 5.remove committed txns
 * (since last checkpoint) from prepared store
 * 
 * 
 * replay after failure at 1 -> 2.2
 * 
 * 1.read preparedTxnStore (empty) 2.read prepared txn from txnLog 3.check if
 * txn is in preparedTxnStore. (add). 4.checkpoint
 * 
 * replay after failure at 2.3 -> 2.4
 * 
 * 1.read txns from preparedTxnStore 2.read prepared txn from txnLog 3.check if
 * txn is in preparedTxnStore. (already there) 4.checkpoint
 * 
 * replay after failure at 2.5
 * 
 * 1.read preparedTxn from preparedTxnStore 2.checkpoint
 * 
 * replay after failure at 3 -> 5
 * 
 * 1.read txns from preparedTxnStore 2.read commit record from txnLog 3.write
 * txn to message store (replace if there) 4.write commit record to
 * preparedTxnStore (if not committed) 5.checkpoint
 * 
 * replay after failure at 6.4 1)read txns from preparedTxnStore (find committed
 * txn) 2)do not replay (not in reset txnLog) 3)checkpoint
 * 
 */

public class TransactionLogManager implements CheckPointListener {

    private static boolean DEBUG = false;

	static final String TXNLOG_PROP_PREFIX = Globals.IMQ
			+ ".persist.file.txnLog";
	static final String TXNLOG_FILE_SIZE_PROP = TXNLOG_PROP_PREFIX
			+ ".file.size";
	

	static final long DEFAULT_TXNLOG_FILE_SIZE_KB = 
            FileTransactionLogWriter.DEFAULT_MAX_SIZE_KB;

	static final String MSG_LOG_FILENAME = "txnlog";

	
	static final String INCOMPLETE_TXN_STORE = "incompleteTxnStore";
	public static final BrokerResources br = Globals.getBrokerResources();
	FileStore store;
	File rootDir;	
        boolean closed = false;
	
	 // whether non-transacted persistent message sent should be logged
	// by default, log non transacted message sends and acks if sync is enabled.
	// user can override this behavior by setiing properties explicitly.
	private static boolean defaultLogNonTransactedMsgSend = Destination.PERSIST_SYNC;
	public static final String LOG_NON_TRANSACTED_MSG_SEND_PROP = Globals.IMQ
			+ ".persist.file.txnLog.logNonTransactedMsgSend";
	public static final boolean logNonTransactedMsgSend = Globals.getConfig()
			.getBooleanProperty(LOG_NON_TRANSACTED_MSG_SEND_PROP,
					defaultLogNonTransactedMsgSend);

	private static boolean defaultLogNonTransactedMsgAck = Destination.PERSIST_SYNC;
	public static final String LOG_NON_TRANSACTED_MSG_ACK_PROP = Globals.IMQ
			+ ".persist.file.txnLog.logNonTransactedMsgAck";
	public static final boolean logNonTransactedMsgAck = Globals.getConfig()
			.getBooleanProperty(LOG_NON_TRANSACTED_MSG_ACK_PROP,
					defaultLogNonTransactedMsgAck);

    /**
     * whether to use a separate thread for writing to the txn log
     */
   
    public static final String TXN_LOG_GROUP_COMMITS_PROP = Globals.IMQ
			+ ".persist.file.txnLog.groupCommits";

    public static final boolean isTxnLogGroupCommits = Globals.getConfig()
                         .getBooleanProperty(TXN_LOG_GROUP_COMMITS_PROP, false);

    public static final String WAIT_LOCAL_PLAYTO_STORE_WITH_EXLOCK_PROP =
           Globals.IMQ+ ".persist.file.txnLog.waitLocalPlayToStoreCompletionWithExLock";
    public static final boolean waitLocalPlayToStoreWithExLock = Globals.getConfig()
           .getBooleanProperty(WAIT_LOCAL_PLAYTO_STORE_WITH_EXLOCK_PROP, false);

    public static final String WAIT_REMOTE_PLAYTO_STORE_WITH_EXLOCK_PROP =
           Globals.IMQ+ ".persist.file.txnLog.waitRemotePlayToStoreCompletionWithExLock";
    public static final boolean waitRemotePlayToStoreWithExLock = Globals.getConfig()
           .getBooleanProperty(WAIT_REMOTE_PLAYTO_STORE_WITH_EXLOCK_PROP, false);

    private static boolean replayInProgress=false;

	private TransactionLogWriter msgLogWriter = null;
	
	TransactionLogReplayer transactionLogReplayer;
	
	LocalTransactionManager localTransactionManager;
	ClusterTransactionManager clusterTransactionManager;
	RemoteTransactionManager remoteTransactionManager;
	
	LoggedMessageHelper loggedMessageHelper;

	CheckpointManager checkpointManager;
	PreparedTxnStore preparedTxnStore;
	
        public static final Logger logger = Globals.getLogger();
		
        private boolean playToStoreCompletionNotified = false;
        private Object  playToStoreCompletionWaiter = new Object();

	public TransactionLogManager(Store store, MsgStore msgStore, File rootDir, boolean resetStore) throws BrokerException{
		
		this.rootDir = rootDir;
		this.store = (FileStore)store;
		File preparedTxnStoreDir = new File(rootDir, INCOMPLETE_TXN_STORE);
		if (resetStore) {
			clearPreparedTxnStore(preparedTxnStoreDir);
			resetTransactionLogOnStartUp();
		}

		preparedTxnStore = new PreparedTxnStore(msgStore, preparedTxnStoreDir,
				false);

		this.transactionLogReplayer = new TransactionLogReplayer(msgStore);
		this.localTransactionManager = new LocalTransactionManager(this);
		this.clusterTransactionManager = new ClusterTransactionManager(this);
		this.remoteTransactionManager = new RemoteTransactionManager(this);
		this.checkpointManager = new CheckpointManager(this);
		this.loggedMessageHelper = new LoggedMessageHelper(this);
		
	}
	
    public boolean getDEBUG() {
        return (Store.getDEBUG() || DEBUG);
    }
	 

	public void startup() throws BrokerException {
		// These are the stages on broker startup
		// 1) read in any prepared transactions held in preparedTxnStore.
		// 2) check if the transaction log needs replaying.
		// 3) replay committed transaction to message store
		// 4) after replay is complete, add any remaining transactions to
		// preparedTxnStore.
		// 5) sync preparedTxnStore
		// 6) sync message store.
		// 7) reset transaction log.
		// 8) remove any committed transactions from preparedTxnStore

		if (Store.getDEBUG()) {
			logger.log(Logger.DEBUG, getPrefix() + " startup");
		}
		// 1 read in any prepared transactions held in preparedTxnStore.
		processStoredTxnsOnStartup();

		// 2) check if the transaction log needs replaying.
		initTransactionLogOnStartUp();

		// 3) replay committed transaction to message store
		replayTransactionLogOnStartup();

	
		// 4. write prepared transactions to prepared transaction store
		localTransactionManager
				.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();
		clusterTransactionManager
				.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();
		remoteTransactionManager
				.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();

		// 5. sync prepareTxnStore
		preparedTxnStore.sync();

		// 6) checkpoint message store.
		store.syncDestination(null);

		try {
			// 7) reset transaction log.
			msgLogWriter.reset();
		} catch (IOException e) {
			throw new BrokerException("failed to reset transaction log", e);
		}

		// 8) remove any committed transactions from preparedTxnStore
		removeCommittedTransactionsOnStartup();

	}
	
	public void close()
	{
             closed = true;
		try{
			if(msgLogWriter!=null)
				msgLogWriter.close(false);
			if(preparedTxnStore!=null)
				preparedTxnStore.close(true);
		}
		catch(IOException e)
		{
			logger.logStack(Logger.ERROR, "caught exception closing", e);
		}
	}
	
	public static void deleteAllFileState(File rootDir) throws BrokerException {
		// delete txnLog file first
		// as deleting directory will not be atomic, and will probably be bottom
		// up
		// (harder to detect if some elements are missing)
		logger.log(Logger.DEBUG, "deleteAllFileState "+rootDir);
		File txnLogFile = new File(rootDir, MSG_LOG_FILENAME);
		boolean deleted = false;
		if (txnLogFile.exists()) {
			deleted = txnLogFile.delete();
			if (!deleted) {
				throw new BrokerException("Could not delete txnLog file "
						+ txnLogFile);
			}
		}
		File incompleteTxnStore = new File(rootDir, INCOMPLETE_TXN_STORE);
		try {
			if (incompleteTxnStore.exists())
				FileUtil.removeFiles(incompleteTxnStore, true);
		} catch (IOException e) {
			String msg = "Can not delete incomplete txn store "
					+ incompleteTxnStore;
			logger.log(Logger.ERROR, msg, e);
			throw new BrokerException(msg, e);
		}

	}
	
	
	public static boolean txnLogExists(File rootDir) {
		File txnLogFile = new File(rootDir, MSG_LOG_FILENAME);
		return txnLogFile.exists();
	}

	public static boolean incompleteTxnStoreExists(File rootDir) {
		File file = new File(rootDir, INCOMPLETE_TXN_STORE);
		return file.exists();
	}

	public static void assertAllFilesExist(File rootDir) throws BrokerException {

		if (!txnLogExists(rootDir)) {
			throw new BrokerException("assertion failure: " + MSG_LOG_FILENAME
					+ " file does not exist");
		}
		if (!incompleteTxnStoreExists(rootDir)) {
			throw new BrokerException("assertion failure: "
					+ INCOMPLETE_TXN_STORE + " file does not exist");
		}
	}
	
	
/*	
	List<BaseTransaction> getAllIncompleteTransactions()
	{
		List<BaseTransaction> local = localTransactionManager.getAllIncompleteTransactions();
		List<BaseTransaction> cluster = clusterTransactionManager.getAllIncompleteTransactions();
		List<BaseTransaction> remote = remoteTransactionManager.getAllIncompleteTransactions();
		
		List<BaseTransaction> all = new ArrayList<BaseTransaction>();
		all.addAll(local);
		all.addAll(cluster);
		all.addAll(remote);
		return all;
	}
	
*/
	
	
	
	
	HashMap getAllTransactionStates() throws IOException {
		
		HashMap localTxnMap = localTransactionManager.getAllTransactionsMap();
		HashMap clusterTxnMap = clusterTransactionManager.getAllTransactionsMap();
		
		HashMap txnMap = new HashMap(localTxnMap.size()+clusterTxnMap.size());
		txnMap.putAll(localTxnMap);
		txnMap.putAll(clusterTxnMap);
		
		return txnMap;
	}
	
	void rollbackAllTransactions()
	{
		localTransactionManager.rollbackAllTransactions();
		clusterTransactionManager.rollbackAllTransactions();
		remoteTransactionManager.rollbackAllTransactions();
		
	}
	

	void processStoredTxnsOnStartup() throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processStoredTxnsOnStartup";
			logger.log(Logger.DEBUG, msg);
		}

		preparedTxnStore.loadTransactions();
				
		// for now just read and sort into different types
		Enumeration<BaseTransaction> transactions = preparedTxnStore
				.txnEnumeration();
		while (transactions.hasMoreElements()) {
			BaseTransaction baseTxn = transactions.nextElement();
			// need to switch on all types
			int type = baseTxn.getType();
			BaseTransactionManager baseTxnMan = this
					.getTransactionManager(type);
			baseTxnMan.processStoredTxnOnStartup(baseTxn);
		}

	}

	void removeCommittedTransactionsOnStartup() throws BrokerException {

		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " removeCommitedTransactionsInPreparedTxnStore";
			logger.log(Logger.DEBUG, msg);
		}
		// for now just report on any transactions
		Enumeration<BaseTransaction> transactions = preparedTxnStore
				.txnEnumeration();
		List<BaseTransaction> committed = new ArrayList<BaseTransaction>();
		while (transactions.hasMoreElements()) {
			BaseTransaction baseTxn = transactions.nextElement();
			if (baseTxn.getState() == TransactionState.COMMITTED) {
				if (baseTxn.getType() == BaseTransaction.CLUSTER_TRANSACTION_TYPE
						&& !baseTxn.getTransactionDetails().isComplete()) {
					if (Store.getDEBUG()) {
						String msg = getPrefix()
								+ " not removing incomplete cluster transaction "
								+ baseTxn.getTransactionDetails();
						logger.log(Logger.DEBUG, msg);
					}
				} else {
					if (Store.getDEBUG()) {
						String msg = getPrefix() + " removing transaction "
								+ baseTxn.getTransactionDetails();
						logger.log(Logger.DEBUG, msg);
					}
					committed.add(baseTxn);
				}
			}

		}
		Iterator<BaseTransaction> iter = committed.iterator();
		while (iter.hasNext()) {
			BaseTransaction baseTxn = iter.next();
			TransactionUID tid = baseTxn.getTid();
			preparedTxnStore.removeTransaction(tid, true);
			if (Store.getDEBUG()) {
				String msg = getPrefix()
						+ " removed committed transaction from preparedTxnStore. Tid="
						+ tid;
				logger.log(Logger.DEBUG, msg);
			}
		}

	}
	private void resetTransactionLogOnStartUp() {
		
		String filename = MSG_LOG_FILENAME;
		File file = new File(rootDir, filename);
		logger.log(Logger.INFO, "resetting txn Log file "+file);
		if(!file.exists())
		{
			logger.log(Logger.INFO, "nothing to reset. txn Log file "+file+ " does not exist");
			return;
		}
		boolean deleted = file.delete();
		if (!deleted) {
			String msg = getPrefix() + " could not delete " + file;

			logger.log(Logger.DEBUG, msg);
		}
	}

	private void clearPreparedTxnStore(File preparedTxnStoreDir)
			throws BrokerException {
		// delete all files under the preparedTxnStoreDir directory
		try {
			
			FileUtil.removeFiles(preparedTxnStoreDir, false);
		} catch (IOException e) {
			logger.log(Logger.ERROR, BrokerResources.X_RESET_MESSAGES_FAILED,
					preparedTxnStoreDir, e);
			throw new BrokerException(br.getString(BrokerResources.X_RESET_MESSAGES_FAILED,
					preparedTxnStoreDir), e);
		}
	}
	
	public static boolean transactionLogExists(File rootDir) {
		boolean result = false;
		String filename = MSG_LOG_FILENAME;
		File txnLogFile = new File(rootDir, filename);
		result = txnLogFile.exists();
		return result;
	}

	private void initTransactionLogOnStartUp() throws BrokerException {

		if (Store.getDEBUG()) {
			String msg = getPrefix() + " initTransactionLogOnStartUp";
			logger.log(Logger.DEBUG, msg);
		}
		logger.log(Logger.INFO, "new transaction log enabled");
		logger.log(Logger.INFO, "sync writes to disk = "+ Destination.PERSIST_SYNC);
		logger.log(Logger.INFO, "logNonTransactedMsgSend = "+ logNonTransactedMsgSend);
		logger.log(Logger.INFO, "logNonTransactedMsgAck = "+ logNonTransactedMsgAck);
		

		// create txn log writers
		String filename = null;
		try {
			BrokerConfig config = Globals.getConfig();
			SizeString filesize = config.getSizeProperty(
                                            TXNLOG_FILE_SIZE_PROP,
					    DEFAULT_TXNLOG_FILE_SIZE_KB);
					
			filename = MSG_LOG_FILENAME;

			String mode = "rwd";
			boolean synch = true;
			if (!Destination.PERSIST_SYNC) {
				mode = "rw";
				synch = false;
			}
			logger.log(Logger.INFO, br.getKString(BrokerResources.I_OPEN_TXNLOG,
						mode, Long.valueOf(filesize.getBytes())));

			FileTransactionLogWriter ftlw = new FileTransactionLogWriter(
					rootDir, filename, filesize.getBytes(), mode, synch,
					isTxnLogGroupCommits,
					BaseTransaction.CURRENT_FORMAT_VERSION);
			long existingFormatVersion = ftlw.getExistingAppCookie();
			
			// 
			// Check version here
			// if(existingFormatVersion!=BaseTransaction.CURRENT_FORMAT_VERSION) 
			// then file may need to be converted.
			//
			// Note this appCookie format specifies the format of the transactions 
			// being stored and not the format of the transaction log itself, which may 
			// change less frequently and should be checked separately
			// Note also that transaction format is also dependent on any changes to packet format etc.
			// 
			// No need to do convert yet as we are on the first version
			// so this is just a sanity check.
			if(existingFormatVersion!=BaseTransaction.CURRENT_FORMAT_VERSION)
			{
				throw new BrokerException("Unexpected transaction log format. Format on file = "+existingFormatVersion + 
						" Current software version = "+BaseTransaction.CURRENT_FORMAT_VERSION);
			}
			
			msgLogWriter = ftlw;
			
			msgLogWriter.setCheckPointListener(this);
			if (Store.getDEBUG()) {
				logger.log(Logger.DEBUG, "created txn log");
			}
		} catch (IOException ex) {
			logger.logStack(Logger.ERROR,
					BrokerResources.E_CREATE_TXNLOG_FILE_FAILED, filename, ex);
			throw new BrokerException(br.getString(
					BrokerResources.E_CREATE_TXNLOG_FILE_FAILED, filename), ex);
		}

	}
	
	

	public void replayTransactionLogOnStartup() throws BrokerException {

		if (Store.getDEBUG()) {
			logger.log(Logger.DEBUG, getPrefix()
					+ " replayTransactionLogOnStartup");
		}

		try {
			setReplayInProgress(true);			
			if (msgLogWriter.playBackRequired()) {
				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " replayTransactionLogOnStartup: playBackRequired";
					logger.log(Logger.DEBUG, msg);
				}

				logger.log(Logger.FORCE, BrokerResources.I_PROCESS_MSG_TXNLOG);

				// All destinations need to be loaded
				Globals.getDestinationList().loadDestinations(store);
				Globals.getCoreLifecycle().initSubscriptions();
				HashSet dstLoadedSet = new HashSet(); // Keep track of loaded
				// dst

				int count = 0;

				
				Iterator itr = msgLogWriter.iterator();
				while (itr.hasNext()) {
					count++; // Keep track the number of records processed

					// Read in the acks or msgs & acks
					TransactionLogRecord rec = (TransactionLogRecord) itr
							.next();

					byte[] data = rec.getBody();
					TransactionEvent txnEvent = readTransactionEvent(data);
					int type = txnEvent.getType();
					if (Store.getDEBUG()) {
						String msg = getPrefix()
								+ " replayTransactionLogOnStartup() recordSeq= " + rec.getSequence()+ " txnEvent= "
								+ txnEvent;
						logger.log(Logger.DEBUG, msg);
					}
					if (type == BaseTransaction.NON_TRANSACTED_MSG_TYPE) {
						transactionLogReplayer.replayNonTxnMsg((NonTransactedMsgEvent)txnEvent,dstLoadedSet);
					} else if (type == BaseTransaction.NON_TRANSACTED_ACK_TYPE) {
						transactionLogReplayer.replayNonTxnMsgAck((NonTransactedMsgAckEvent)txnEvent,dstLoadedSet);
					} else if (type == BaseTransaction.MSG_REMOVAL_TYPE) {
						transactionLogReplayer.replayMessageRemoval((MsgRemovalEvent)txnEvent,dstLoadedSet);
					} else {

						BaseTransactionManager tm = getTransactionManager(type);
						tm.replayTransactionEvent(txnEvent, dstLoadedSet);
					}

				}

			} else {
				if (Store.getDEBUG()) {
					logger.log(Logger.DEBUG, "no playBackRequired");
				}
			}
		}

		catch (IOException e) {
			logger.log(Logger.ERROR, "exception in playback",e);
			throw new BrokerException("exception in playback",e);

		}finally{
			setReplayInProgress(false);
		}
	}

	

	TransactionEvent readTransactionEvent(byte[] data) throws IOException,
			BrokerException {
		TransactionEvent event = TransactionEvent.createFromBytes(data);
		return event;
	}

	
	public void loggedCommitWrittenToMessageStore(TransactionUID tid, int type) {
		BaseTransactionManager txnMan = getTransactionManager(type);

		txnMan.playingToMessageStoreComplete(tid);

	}

	BaseTransactionManager getTransactionManager(int type) {
		BaseTransactionManager r = null;
		switch (type) {
		case BaseTransaction.LOCAL_TRANSACTION_TYPE:
			r = localTransactionManager;
			break;
		case BaseTransaction.CLUSTER_TRANSACTION_TYPE:
			r = clusterTransactionManager;
			break;
		case BaseTransaction.REMOTE_TRANSACTION_TYPE:
			r = remoteTransactionManager;
			break;
		case BaseTransaction.UNDEFINED_TRANSACTION_TYPE:
			throw new UnsupportedOperationException(
					"UNDEFINED_TRANSACTION_TYPE");
		default:
			throw new UnsupportedOperationException("unknown type:" + type);
		}
		return r;
	}
	
	
	public void logMsgRemoval(DestinationUID dstID, SysMessageID mid)
			throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " logMsgRemoval() dstID=" + dstID + " mid="+mid;
			logger.log(Logger.DEBUG, msg);
		}
		try {
			store.txnLogSharedLock.lock();

			MsgRemovalEvent txnEvent = new MsgRemovalEvent(dstID,mid);
			byte[] data = txnEvent.writeToBytes();

			TransactionLogRecord record = msgLogWriter
					.newTransactionLogRecord();
			record.setBody(data);
			msgLogWriter.write(record);

		} catch (IOException ioe) {
			throw new BrokerException("error logging transaction", ioe);
		} finally {
			store.txnLogSharedLock.unlock();
		}
	}
	
	
	public void logNonTxnMessage(TransactionWorkMessage twm)
			throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " logNonTxnMessage() " + twm;
			logger.log(Logger.DEBUG, msg);
		}
		try {
			store.txnLogSharedLock.lock();

			NonTransactedMsgEvent txnEvent = new NonTransactedMsgEvent(twm);
			writeTransactionEvent(txnEvent);
			loggedMessageHelper.messageLogged(twm);
		} finally {
			store.txnLogSharedLock.unlock();
		}
	}
	
	
	public void logNonTxnMessageAck(TransactionWorkMessageAck twma)
			throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " logNonTxnMessageAck() " + twma;
			logger.log(Logger.DEBUG, msg);
		}
		try {
			store.txnLogSharedLock.lock();
			NonTransactedMsgAckEvent txnEvent = new NonTransactedMsgAckEvent(twma);
			writeTransactionEvent(txnEvent);					
		} finally {
			store.txnLogSharedLock.unlock();
		}
	}

	public void logTxn(BaseTransaction baseTxn) throws BrokerException {
		if (Store.getDEBUG()) {
			logger.log(Logger.DEBUG, getPrefix() + " logTxn() " + baseTxn);
		}

		try {
			store.txnLogSharedLock.lock();
			int type = baseTxn.getType();
			BaseTransactionManager txnManager = getTransactionManager(type);
			txnManager.processTxn(baseTxn);
			TransactionEvent txnEvent = txnManager
					.generateEvent(baseTxn, false);
			writeTransactionEvent(txnEvent);
			
		} catch (IOException ioe) {
			throw new BrokerException("error logging transaction", ioe);
		} finally {
			store.txnLogSharedLock.unlock();
		}
	}

	public void logTxnCompletion(TransactionUID tid, int completionState,
			int type) throws BrokerException {
		if (Store.getDEBUG()) {
			logger
					.log(Logger.DEBUG, getPrefix() + " logTxnCompletion() "
							+ tid);
		}
		try {
			store.txnLogSharedLock.lock();
			BaseTransactionManager txnMan = getTransactionManager(type);
			BaseTransaction existing = txnMan.processTxnCompletion(tid,
					completionState);
			TransactionEvent txnEvent = txnMan.generateEvent(existing, true);

			writeTransactionEvent(txnEvent);
		} catch (IOException ioe) {
			throw new BrokerException("error logging transaction", ioe);

		} finally {
			store.txnLogSharedLock.unlock();
		}
	}
	
	
	public void writeTransactionEvent(TransactionEvent txnEvent) throws BrokerException
	{
		try {			
			byte[] data = txnEvent.writeToBytes();
			TransactionLogRecord record = msgLogWriter
					.newTransactionLogRecord();
			record.setBody(data);
			msgLogWriter.write(record);

		} catch (IOException ioe) {
			throw new BrokerException("error logging transaction", ioe);
		}
	}

        public void notifyPlayToStoreCompletion() {
            synchronized(playToStoreCompletionWaiter) {
                playToStoreCompletionNotified = true;
                playToStoreCompletionWaiter.notifyAll();
            }
        }

	public void doCheckpoint() {

             if (closed) {
                 return;
             }
             if (Store.getDEBUG()) {
                 logger.log(Logger.INFO, br.getKString(br.I_CHECKPOINT_START));
             }        

		// 1. get exclusive lock on transaction log
		// 2. wait for all logged commits to be written to message store
		// 3. sync message store
		// 4. write prepared txns (since last checkpoint) to prepared store
		// 5. sync prepareTxnStore
		// 6. reset txn log
		// 7. remove committed txns (since last checkpoint) from prepared store

		FileStore store = null;
		try {
			store = (FileStore)Globals.getStore();
		} catch (Throwable e) {
			logger.logStack(Logger.ERROR, "failed to getStore", e);

			// fatal
		}

                boolean locked = false;
		try {

		    int count  = 0;
                    while (true) { 
                        if (closed) {
                            return;
                        }
                        // 1. get exclusive lock on txnLog to prevent any other updates occurring
                        store.txnLogExclusiveLock.lock();
                        locked = true;
                        try {
                             synchronized(playToStoreCompletionWaiter) {
                                 playToStoreCompletionNotified = false;
                             }
                             // 2. wait for all logged commits to be written to message store
                             localTransactionManager.waitForPlayingToMessageStoreCompletion(
                                                            !waitLocalPlayToStoreWithExLock);
                             clusterTransactionManager.waitForPlayingToMessageStoreCompletion(
                                                            !waitLocalPlayToStoreWithExLock);
                             remoteTransactionManager.waitForPlayingToMessageStoreCompletion(
                                                            !waitRemotePlayToStoreWithExLock);
                             //2.1 wait for pending removes to complete for logged last ack on message 
                             loggedMessageHelper.waitForPendingRemoveCompletion(
                                                !waitRemotePlayToStoreWithExLock);

                             break;
                        } catch (WaitTimeoutException e) {
                             store.txnLogExclusiveLock.unlock();
                             locked = false;
                             count++;
                             if (closed) {
                                 throw e;
                             }
                             synchronized(playToStoreCompletionWaiter) {
                                 int cnt = 0;
                                 while (!playToStoreCompletionNotified && !closed) {
                                     try { 
                                          playToStoreCompletionWaiter.wait(1000);
                                          cnt++;
                                     } catch (Exception ee) { }
                                     if (cnt%15 == 0) { 
                                          String[] args = { e.getMessage()+"("+cnt+", "+count+")" };
                                          logger.log(Logger.WARNING, br.getKTString(
                                                     br.W_CHECKPOINT_WAIT_PLAYTO_STORE_TIMEOUT, args));
                                     }
                                 }
                             }
                             continue;
                        }
                    }
	
			// 3. sync the message store
			store.syncStoreOnCheckpoint();

			// 4. write prepared transactions to prepared transaction store
			localTransactionManager
					.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();
			clusterTransactionManager
					.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();
			remoteTransactionManager
					.writePreparedTransactionsToPreparedTxnStoreOnCheckpoint();

			// 5. sync prepareTxnStore
			preparedTxnStore.sync();

			// 6. reset txn log
			msgLogWriter.checkpoint();

			// 7. remove committed txns (since last checkpoint) from prepared
			// store
			localTransactionManager.removeCompleteTransactionsAfterCheckpoint();
			clusterTransactionManager
					.removeCompleteTransactionsAfterCheckpoint();
			remoteTransactionManager
					.removeCompleteTransactionsAfterCheckpoint();
			
			// 8. reset logged message list
			loggedMessageHelper.onCheckpoint();

                        if (Store.getDEBUG()) {
                            logger.log(Logger.INFO, br.getKString(br.I_CHECKPOINT_END));
                        }

		} catch (Throwable e) {
			String msg = getPrefix()
					+ "Failed to synchronize persistence store for transaction log checkpoint";
			logger.logStack(Logger.ERROR,
					BrokerResources.E_INTERNAL_BROKER_ERROR, msg, e);
		} finally {
                        try {
                            if (locked) {
                                store.txnLogExclusiveLock.unlock();
                            }
                        } finally {

                            synchronized(playToStoreCompletionWaiter) {
                                playToStoreCompletionNotified = true;
                                playToStoreCompletionWaiter.notifyAll();
                            }
                        }

			if (getDEBUG()) {
				String msg = getPrefix() + " doCheckpoint complete";
				logger.log(Logger.INFO, msg);
			}
		}
	}

	// checkpoint handling

	public final void checkpoint() {
		if (Store.getDEBUG()) {
			logger.log(Logger.DEBUG, getPrefix() + " request a checkpoint");
		}

		checkpointManager.enqueueCheckpoint();
	}

	String getPrefix() {
		return "TransactionLogManager: " + Thread.currentThread().getName();
	}

	public LocalTransactionManager getLocalTransactionManager() {
		return localTransactionManager;
	}

	public ClusterTransactionManager getClusterTransactionManager() {
		return clusterTransactionManager;
	}

	public RemoteTransactionManager getRemoteTransactionManager() {
		return remoteTransactionManager;
	}

	public LoggedMessageHelper getLoggedMessageHelper() {
		return loggedMessageHelper;
	}



	public static boolean isReplayInProgress() {
		return replayInProgress;
	}



	public static void setReplayInProgress(boolean val) {
		TransactionLogManager.replayInProgress = val;
	}
}
