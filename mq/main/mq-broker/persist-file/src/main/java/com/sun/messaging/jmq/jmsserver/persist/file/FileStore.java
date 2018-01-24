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
 * @(#)FileStore.java	1.123 08/30/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;

import com.sun.messaging.jmq.jmsserver.persist.api.util.MQObjectInputStream;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.disk.ObjectInputStreamCallback;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessageAck;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.DiskFileStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.util.timer.MQTimer;
import com.sun.messaging.jmq.io.txnlog.CheckPointListener;
import com.sun.messaging.jmq.io.txnlog.TransactionLogRecord;
import com.sun.messaging.jmq.io.txnlog.TransactionLogType;
import com.sun.messaging.jmq.io.txnlog.TransactionLogWriter;
import com.sun.messaging.jmq.io.txnlog.file.FileTransactionLogWriter;
import com.sun.messaging.jmq.util.UID;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

/**
 * FileStore provides file based persistence.
 * <br>
 * Note that some methods are NOT synchronized.
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.persist.file.FileStore")
@Singleton
public class FileStore extends Store implements PartitionedStore, 
    TxnLoggingStore, DiskFileStore, CheckPointListener, ObjectInputStreamCallback {

    // current version of store
    public static final int OLD_STORE_VERSION_200 = 200;
    public static final int OLD_STORE_VERSION = 350;
    public static final int STORE_VERSION = 370;

    /**
     * properties used:
     *
     * - whether to sync persistent operations; default is false
     * jmq.persist.file.sync.enable=[true|false]
     *
     * - whether to sync all operations, including those operations that
     *   truncate a file or tag a file 'FREE' are not sync'ed.
     * jmq.persist.file.sync.all = [true|false]	# private
     */
    static final String FILE_PROP_PREFIX = Globals.IMQ + ".persist.file.";

    static final String SYNC_ENABLED_PROP
				= FILE_PROP_PREFIX + "sync.enabled";

    static final String SYNC_ALL_PROP
				= FILE_PROP_PREFIX + "sync.all";

    // default for jmq.persist.file.sync.enabled
    static final boolean DEFAULT_SYNC_ENABLED = false;

    // default for jmq.persist.file.sync.all
    static final boolean DEFAULT_SYNC_ALL = false;

    // initial size of txn log file
    static final String TXNLOG_FILE_SIZE_PROP
                                = FILE_PROP_PREFIX + "txnLog.file.size";

    static final long DEFAULT_TXNLOG_FILE_SIZE = 1024 * 10; // 10M

    /**
     * directory hierarchy of the file based persistent store
     * "fs370" - top of directory hierarchy, 'fs' + version #
     * "message" - directory to store messages, one message per file with
     *		   numeric file names
     * "config" - directory to store cluster configuration information
     * "interest" - file under "fs370" to store interest objects
     * "destination" - a file under "fs370" to store destinations
     * "property" - a file under "fs370" to store name/value pairs
     * "tid" - a file under "fs370" to store transaction ids
     * "txnack" - files under "fs370" to store transaction
     *			acknowledgements of all transaction ids
     */
    static final String FILESTORE_BASENAME = "fs";
    static final String FILESTORE_TOP =
			FILESTORE_BASENAME + STORE_VERSION;

    // root directory of old (350) file store
    static final String FILESTORE350_TOP =
                        FILESTORE_BASENAME + OLD_STORE_VERSION;

    // root directory of old (200) file store
    static final String FILESTORE200_TOP = "filestore";

    // version file in old (200) file store
    static final String VERSIONFILE = "version";

    /**
     * static varibles used by all other classes in the package.
     */
    // whether to sync data-writing operations
    static final boolean syncEnabled = config.getBooleanProperty(SYNC_ENABLED_PROP,
						DEFAULT_SYNC_ENABLED);

    // whether to sync all write operations
    static final boolean syncAll = config.getBooleanProperty(SYNC_ALL_PROP, DEFAULT_SYNC_ALL);
    
    static final String NO_SYNC_FOR_DELIVERY_STATE_UPDATE = FILE_PROP_PREFIX+ "noSyncForDeliveryStateUpdate";
    static final boolean noSyncForDeliveryStateUpdate = config.getBooleanProperty(NO_SYNC_FOR_DELIVERY_STATE_UPDATE, false);

    /**
     * Instance variables
     */

    // root directory of the file store
    private File rootDir = null;

    // object encapsulates persistence of messages and their interest lists
    private MsgStore msgStore = null;

    // object encapsulates persistence of interests
    private InterestStore intStore = null;

    // object encapsulates persistence of destinations
    private DestinationListStore dstList = null;

    // object encapsulates persistence of transaction ids
    private TidList tidList = null;

    // object encapsulates persistence of configuration change record
    private ConfigChangeRecord configStore = null;

    // object encapsulates persistence of properties
    private PropertiesFile propFile = null;

 // object encapsulates persistence of prepared transactions outside of txnLog
   // private PreparedTxnStore preparedTxnStore = null;
    private TransactionLogManager txnLogManager = null;
    
    // basename of txn log files
    static final String MSG_LOG_FILENAME = "txnlogmsg";
    static final String ACK_LOG_FILENAME = "txnlogack";

    boolean txnLoggerInited = false;
    private TransactionLogWriter msgLogWriter = null;
    private TransactionLogWriter ackLogWriter = null;
    
    private UID partitionid = PartitionedStore.DEFAULT_UID;

    /**
     * When instantiated, the object configures itself by reading the
     * properties specified in BrokerConfig.
     */
    public FileStore() throws BrokerException {

	// get the jmq.persist.file.sync.enabled and
	// jmq.persist.file.sync.all properties

    	String fstop = Globals.getJMQ_INSTANCES_HOME() + File.separator +
                Globals.getConfigName() + File.separator;
    	logger.logToAll(Logger.INFO, br.getString(BrokerResources.I_FILE_STORE_INFO, fstop));
				
	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, SYNC_ENABLED_PROP+"="+syncEnabled);
	}


	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, SYNC_ALL_PROP+"="+syncAll);
	}
	
	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, NO_SYNC_FOR_DELIVERY_STATE_UPDATE+"="+noSyncForDeliveryStateUpdate);
	}
	
	
	
	// instance root directory
	String instancename = Globals.getJMQ_INSTANCES_HOME() + File.separator +
			Globals.getConfigName() + File.separator;

	File instanceDir = new File(instancename);

	// check file store & determine whether we need to upgrade an old store
	int upgrade = checkFileStore(instanceDir);

	// the file store root directory
	rootDir = new File(instanceDir, FILESTORE_TOP);

	// check if we need to remove the store
	if (removeStore) {
	    try {
		// remove everything and return
		FileUtil.removeFiles(rootDir, true);
		return;
	    } catch (IOException e) {
		logger.log(Logger.ERROR,
                    BrokerResources.E_REMOVE_STORE_FAILED, rootDir, e);
		throw new BrokerException(br.getString(
                    BrokerResources.E_REMOVE_STORE_FAILED, rootDir), e);
	    }
	} else if (!rootDir.exists() && !rootDir.mkdirs()) {
	    logger.log(Logger.ERROR,
                BrokerResources.E_CANNOT_CREATE_STORE_HIERARCHY, rootDir);
	    throw new BrokerException(br.getString(
                BrokerResources.E_CANNOT_CREATE_STORE_HIERARCHY, rootDir));
	}

        if (upgrade > 0) {
            File oldRoot = null; // Old file store root dir, i.e. 350 or 200!
            if ( Globals.isNewTxnLogEnabled() ) {
                String[] eargs = { String.valueOf(upgrade), 
                    StoreManager.NEW_TXNLOG_ENABLED_PROP+"=true",
                    StoreManager.NEW_TXNLOG_ENABLED_PROP+"=false",
                    StoreManager.NEW_TXNLOG_ENABLED_PROP+"=true" };
                String emsg = Globals.getBrokerResources().getKString(
                    BrokerResources.E_NO_UPGRADE_OLD_FSTORE_WITH_NEWTXNLOG, eargs);
                logger.log(Logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

	    try {
		// log message to do upgrade
		Object[] args = {(Integer.valueOf(STORE_VERSION)), FILESTORE_TOP};
		logger.logToAll(Logger.INFO,
                    BrokerResources.I_UPGRADE_STORE_IN_PROGRESS, args);

		if (resetMessage) {
		    // log message to remove old message
		    logger.logToAll(Logger.INFO,
                        BrokerResources.I_RESET_MESSAGES_IN_OLD_STORE);
		    logger.logToAll(Logger.INFO,
                        BrokerResources.I_UPGRADE_REMAINING_STORE_DATA);
		} else if (resetInterest) {
		    // log message to remove old interest
		    logger.logToAll(Logger.INFO,
                        BrokerResources.I_RESET_INTERESTS_IN_OLD_STORE);
		    logger.logToAll(Logger.INFO,
                        BrokerResources.I_UPGRADE_REMAINING_STORE_DATA);
		}

                if (upgrade == OLD_STORE_VERSION) {
                    // Upgrading from 350 to 370, we copies all files under
                    // fs350 to fs370 and migrate txn and txn acks table
                    oldRoot = new File(instanceDir, FILESTORE350_TOP);

                    try {
                        FileUtil.copyDirectory(oldRoot, rootDir);
                        FileUtil.removeFiles(new File(rootDir, TidList.BASENAME), false);
                        FileUtil.removeFiles(new File(rootDir, TxnAckList.BASENAME), false);
                    } catch (IOException e) {
                        // Upgrade failed - unable to copy files
                        String errorMsg =
                            "Failed to copy old persistent data under " +
                            oldRoot + " to " + rootDir;
                        logger.log(Logger.ERROR, errorMsg, e);
                        throw new BrokerException(errorMsg, e);
                    }

                    // always load destinations first
                    dstList = new DestinationListStore(this, rootDir, false);

                    msgStore = new MsgStore(this, rootDir, resetMessage);

                    intStore = new InterestStore(this, rootDir, resetInterest);

                    // transaction ids and associated ack lists
                    tidList = new TidList(this, rootDir, oldRoot);

                    // configuration change record
                    configStore = new ConfigChangeRecord(rootDir, false);

                    // properties
                    propFile = new PropertiesFile(this, rootDir, false);
                } else {
                    oldRoot = new File(instanceDir, FILESTORE200_TOP);
                    throw new BrokerException(
                    "Upgrade from old store version "+200+" is not supported:"+oldRoot);
                }

                if (Store.getDEBUG()) {
                    logger.log(Logger.DEBUG,
                        "FileStore upgraded successfully.");
                }

                if (upgradeNoBackup) {
                    // remove the remaining old store; just remove everything
                    try {
                        FileUtil.removeFiles(oldRoot, true);
                    } catch (IOException e2) {
                        // log something
                        logger.log(Logger.ERROR,
                            BrokerResources.E_REMOVE_STORE_FAILED, oldRoot, e2);
                    }
                }

                logger.logToAll(Logger.INFO, BrokerResources.I_UPGRADE_STORE_DONE);

                if (!upgradeNoBackup) {
                    // log message about the old store
                    logger.logToAll(Logger.INFO,
                        BrokerResources.I_REMOVE_OLD_FILESTORE, oldRoot);
                }
	    } catch (BrokerException e) {
		logger.log(Logger.INFO,
                    BrokerResources.I_REMOVE_NEW_STORE, rootDir);

		// upgrade failed with exception; remove the new store
		try {
		    FileUtil.removeFiles(rootDir, true);
		} catch (IOException e2) {
		    // log something
		    logger.log(Logger.ERROR,
                        BrokerResources.E_REMOVE_STORE_FAILED, rootDir, e);
		}
		throw e;
	    }

	} else {
			// always load destinations first
			dstList = new DestinationListStore(this, rootDir, resetStore);

			msgStore = new MsgStore(this, rootDir, (resetStore || resetMessage));

			intStore = new InterestStore(this, rootDir, (resetStore || resetInterest));

			if(resetStore){
				TxnConversionUtil.resetAllTransactionState(rootDir);
			}
			boolean isNewTxnLogEnabled = Globals.isNewTxnLogEnabled();
			TxnConversionUtil.checkForIncompleteTxnConversion(rootDir, isNewTxnLogEnabled);
				
			if (!isNewTxnLogEnabled || TxnConversionUtil.isTxnConversionRequired() ) {						
				tidList = new TidList(this, rootDir, resetStore);
			}
			
			if (isNewTxnLogEnabled || TxnConversionUtil.isTxnConversionRequired()) {

				txnLogManager = new TransactionLogManager(this, msgStore,
						rootDir, resetStore);

			}
			
			

	    // configuration change record
	    configStore = new ConfigChangeRecord(rootDir, resetStore);

	    // properties
	    propFile = new PropertiesFile(this, rootDir, resetStore);

	    if (Store.getDEBUG()) {
		logger.log(Logger.DEBUG,
				"FileStore instantiated successfully.");
	    }
	}
    }

    public ObjectInputStream getObjectInputStream(ByteArrayInputStream bis)
    throws IOException {
        return new MQObjectInputStream(bis);
    }
    
    void closeTidList()
    {
      if(tidList!=null)
    	  tidList.close(true);	
    }
    
    void closeTxnLogManager()
    {
      txnLogManager.close();	
    }
    
    public void convertTxnFormats(TransactionList transactionList) throws BrokerException, IOException
    {
    	TxnConversionUtil.convertTxnFormats(this, rootDir, transactionList);
    }
   
    
     
    

    
    public void init() throws BrokerException {
    	
    	// if we are in txn log mode or if we need to convert then txnLogManager should not be null
		if (txnLogManager!=null) {

			txnLogManager.startup();			
		}
		
	}
    
    /**
	 * Used for Store backup/resotre utility only. Instance directory is given
	 * explicitly. Depending on the clean input, store is cleaned and
	 * instantiated rather than just instantiated.
	 */
    public FileStore(String dir, boolean clean) throws BrokerException {

	// get the jmq.persist.file.sync.enabled and
	// jmq.persist.file.sync.all properties

	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, SYNC_ENABLED_PROP + "=" + syncEnabled);
	}

	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, SYNC_ALL_PROP + "=" + syncAll);
	}

        logger.log(Logger.INFO, br.getString(BrokerResources.I_FILE_STORE_INFO, dir));

	// the file store root directory
	rootDir = new File(dir, FILESTORE_TOP);

	// check if we need to clean the store
	if (clean) {
	    try {
		// remove everything under root dir but not the root dir.
		FileUtil.removeFiles(rootDir, false);
                return;
                
	    } catch (IOException e) {
		logger.log(Logger.ERROR, BrokerResources.E_REMOVE_STORE_FAILED, rootDir, e);
		throw new BrokerException(
				br.getString(BrokerResources.E_REMOVE_STORE_FAILED, rootDir),
				e);
	    }
	} else if (!rootDir.exists() && !rootDir.mkdirs()) {
	    logger.log(Logger.ERROR, BrokerResources.E_CANNOT_CREATE_STORE_HIERARCHY,
				rootDir.toString());
	    throw new BrokerException(br.getString(
					BrokerResources.E_CANNOT_CREATE_STORE_HIERARCHY,
					rootDir.toString()));
	}

        dstList = new DestinationListStore(this, rootDir, false);

        msgStore = new MsgStore(this, rootDir, false);

        intStore = new InterestStore(this, rootDir, false);

        // transaction ids and associated ack lists
        tidList = new TidList(this, rootDir, false);

        // configuration change record
        configStore = new ConfigChangeRecord(rootDir, false);

        // properties
        propFile = new PropertiesFile(this, rootDir, false);

        if (Store.getDEBUG()) {
            logger.log(Logger.DEBUG, "FileStore instantiated successfully.");
        }
    }

    
  
    
    /**
     * Return the LoadException for loading destinations; null if there's
     * none.
     */
    public LoadException getLoadDestinationException() {
	return dstList.getLoadException();
    }

    /**
     * Return the LoadException for loading consumers; null if there's none.
     */
    public LoadException getLoadConsumerException() {
	return intStore.getLoadException();
    }

    /**
     * Return the LoadException for loading Properties; null if there's none.
     */
    public LoadException getLoadPropertyException() {
	return propFile.getLoadException();
    }

    /**
     * Return the LoadException for loading transactions; null if there's none.
     */
    public LoadException getLoadTransactionException() {
	return tidList.getLoadException();
    }

    /**
     * Return the LoadException for loading transaction acknowledgements;
     * null if there's none.
     */
    public LoadException getLoadTransactionAckException() {
	return tidList.getLoadTransactionAckException();
    }

    /**
     * Close the store and releases any system resources associated with
     * it.
     */
    public void close(boolean cleanup) {

	// make sure all operations are done before we proceed to close
	super.setClosedAndWait();

	dstList.close(cleanup);
	if(tidList!=null)
		tidList.close(cleanup);
	configStore.close(cleanup);
	propFile.close(cleanup);
	intStore.close(cleanup);
	msgStore.close(cleanup);

        try {
            if (msgLogWriter != null) {
                msgLogWriter.close();
            }
            if (ackLogWriter != null) {
                ackLogWriter.close();
            }
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Got IOException while closing transaction log file", ex);
        }

        // If we're using memory-mapped file for txn and txn ack table,
        // calling System.gc() might free the mapped byte buffers because there
        // is no unmap API. We do this to reduce the risk of getting the error
        // "java.io.IOException: The requested operation cannot be performed on
        // a file with a user-mapped section open" when we re-open the store,
        // e.g. restart the broker with imqcmd. See bug 6354433.
        if (config.getBooleanProperty(TidList.TXN_USE_MEMORY_MAPPED_FILE_PROP,
            TidList.DEFAULT_TXN_USE_MEMORY_MAPPED_FILE)) {
            for (int i = 0; i < 3; i++) {
                System.gc();
                try {
                    // Pause for half a sec
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
            }
        }

    if(txnLogManager!=null)
    {
    	txnLogManager.close();
    }
	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, "FileStore.close("+ cleanup +") done.");
	}
    }

    /**
     * Clear the store. Remove all persistent data.
     * Note that this method is not synchronized.
     */
    public void clearAll(boolean sync) throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.clearAll() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    msgStore.clearAll(sync);
	    intStore.clearAll(sync);
	    dstList.clearAll(sync, false);// don't worry about messages since
					// they are removed already
	    if(tidList!=null)
	    	tidList.clearAll(sync);
	    configStore.clearAll(sync);
	    propFile.clearAll(sync);

            try {
                if (msgLogWriter != null) {
                    msgLogWriter.reset();
                }
                if (ackLogWriter != null) {
                    ackLogWriter.reset();
                }
            } catch (IOException ex) {
                logger.logStack(Logger.ERROR,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Got IOException while resetting transaction log file", ex);
            }

	    if (Store.getDEBUG()) {
		logger.log(Logger.DEBUG, "File store cleared");
	    }
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }
    
  
    
    public List<BaseTransaction> getIncompleteTransactions(int type) {
		// return this.getPreparedTxnStore().txnEnumeration();
		BaseTransactionManager tm = txnLogManager.getTransactionManager(type);
		return tm.getAllIncompleteTransactions();
    }
	

    public void rollbackAllTransactions() {
        txnLogManager.rollbackAllTransactions();
    }

    /**
	 * Store a message, which is uniquely identified by it's SysMessageID, and
	 * it's list of interests and their states.
	 * 
	 * @param message
	 *            the message to be persisted
	 * @param iids
	 *            an array of interest ids whose states are to be stored with
	 *            the message
	 * @param states
	 *            an array of states
	 * @param sync
	 *            if true, will synchronize data to disk
	 * @exception IOException
	 *                if an error occurs while persisting the data
	 * @exception BrokerException
	 *                if a message with the same id exists in the store already
	 * @exception NullPointerException
	 *                if <code>dst</code>, <code>message</code>,
	 *                <code>iids</code>, or <code>states</code> is
	 *                <code>null</code>
	 */
    public void storeMessage(DestinationUID dst, Packet message,
	ConsumerUID[] iids, int[] states, boolean sync)
	throws IOException, BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.storeMessage() with interests called for "
                       + message.getSysMessageID() + " sync= "+sync);
        }

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {

	    if (dst == null || message == null || iids == null || states == null) {
		throw new NullPointerException();
	    }

	    if (iids.length == 0 || iids.length != states.length) {
		throw new BrokerException(br.getString(BrokerResources.E_BAD_INTEREST_LIST));
	    }

	    msgStore.storeMessage(dst, message, iids, states, sync);

	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Get the file store version.
     * @return file store version
     */
    public final int getStoreVersion() {
        return STORE_VERSION;
    }

    static final private ConsumerUID[] emptyiid = new ConsumerUID[0];
    static final private int[] emptystate = new int[0];

    /**
     * Store a message which is uniquely identified by it's system message id.
     *
     * @param message	the readonly packet to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the message
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     */
    public void storeMessage(DestinationUID dst, Packet message, boolean sync)
	throws IOException, BrokerException {

    	if (Store.getDEBUG()) {
    	    logger.log(Logger.INFO, "FileStore.storeMessage() called for "
    			+ message.getSysMessageID() + " sync="+sync);
    	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    msgStore.storeMessage(dst, message, emptyiid, emptystate, sync);

	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID       the destination the message is associated with
     * @param mID       the system message id of the message to be removed
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException  if <code>dID</code> is
     *                  <code>null</code>
     */
    public void removeMessage(DestinationUID dID,
        SysMessageID mID, boolean sync) throws IOException, BrokerException
        {
          removeMessage(dID,mID,sync, false);
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
    public void removeMessage(DestinationUID dst, SysMessageID id, boolean sync, boolean onRollback)
	throws IOException, BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.removeMessage() called for "
                       + dst + ";" + id + " sync="+sync+ " onRollback="+onRollback);
        }

		if (Globals.isNewTxnLogEnabled() && onRollback) {
			// do nothing, as transacted messages will not have been added to
			// the persistent store
			return;
		}

		if (id == null) {
			throw new NullPointerException();
		}

		// make sure store is not closed then increment in progress count
		super.checkClosedAndSetInProgress();

		
		try {
			if(Globals.isNewTxnLogEnabled()) 
			{
				txnLogManager.getLoggedMessageHelper().preMessageRemoved(dst,id);
			}
			msgStore.removeMessage(dst, id, sync);			
		} finally {
			try {
				if (Globals.isNewTxnLogEnabled()) {
					txnLogManager.getLoggedMessageHelper().postMessageRemoved(
							dst, id);
				}
			} finally {
				// decrement in progress count
				super.setInProgress(false);
			}
		}
	}

    /**
     * Move the message from one destination to another.
     * The message will be stored in the target destination with the
     * passed in consumers and their corresponding states.
     * After the message is persisted successfully, the message in the
     * original destination will be removed.
     *
     * @param message	message to be moved
     * @param from	destination the message is currently in 
     * @param to	destination to move the message to
     * @param ints	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk.
     * @exception IOException if an error occurs while moving the message
     * @exception BrokerException if the message is not found in source
     *		destination
     * @exception NullPointerException	if <code>message</code>, 
     *			<code>from</code>, <code>to</code>,
     *			<code>iids</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public void moveMessage(Packet message, DestinationUID from,
	DestinationUID to, ConsumerUID[] ints, int[] states, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
		"FileStore.moveMessage() called for: "
			+ message.getSysMessageID() + " from "
			+ from + " to " + to);
	}

	if (message == null || from == null || to == null) {
	    throw new NullPointerException();
	}

	if (ints == null) {
	    ints = emptyiid;
	    states = emptystate;
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    msgStore.moveMessage(message, from, to, ints, states, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove all messages associated with the specified destination
     * from the persistent store.
     *
     * @param destination	the destination whose messages are to be
     *				removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the messages
     * @exception BrokerException if the destination is not found in the store
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void removeAllMessages(Destination destination, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
		"FileStore.removeAllMessages(Destination) called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    msgStore.removeAllMessages(destination.getDestinationUID(), sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
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
     * @return an enumeration of all persisted messages, an empty
     *		enumeration will be returned if no messages exist for the
     *		destionation
     * @exception BrokerException if an error occurs while getting the data
     */
    public Enumeration messageEnumeration(Destination dst)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
		"FileStore.messageEnumeration(Destination) called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return msgStore.messageEnumeration(dst.getDestinationUID());
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Return the message with the specified message id.
     *
     * @param id	the message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dst, String id)
	throws BrokerException {
        return getMessage(dst, SysMessageID.get(id));
    }

    /**
     * Return the message with the specified message id.
     *
     * @param id	the system message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dst, SysMessageID id)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getMessage() called");
	}

	if (id == null) {
	    throw new NullPointerException();
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return msgStore.getMessage(dst, id);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    public  void storeInterestStates(DestinationUID dID,
                SysMessageID mID, ConsumerUID[] iIDs, int[] states, boolean sync)
                throws BrokerException
    {
        storeInterestStates(dID,mID,iIDs,states,sync,null);
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
    public void storeInterestStates(DestinationUID dst,
	SysMessageID mid, ConsumerUID[] iids, int[] states, boolean sync, Packet msg)
	throws BrokerException {

        if (Store.getDEBUG()) {
            StringBuffer b = new StringBuffer();
            for (int i=0; i<states.length; i++) {
                b.append(states[i]).append(",");
            }
            logger.log(Logger.INFO,
            "FileStore.storeInterestStates("+b+", "+sync+") called");	   
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            if (mid == null || iids == null || states == null) {
                throw new NullPointerException();
            }

            if (iids.length == 0 || iids.length != states.length) {
                throw new BrokerException(
                    br.getString(BrokerResources.E_BAD_INTEREST_LIST));
            }

            /*
            if (Globals.isNewTxnLogEnabled() && msg != null) {
                try {
                    // we are really storing the message for the first time
                    // so need to add message data
                    if (Store.getDEBUG()) {
                        logger.log(Logger.DEBUG, "FileStore.storeInterestStates() REALLY storing");
                    }
                    msgStore.storeMessage(dst, msg, iids, states, false);

                } catch(IOException ex) {
                    throw new BrokerException(ex.toString(), ex);
                }
            } else {
            */
                msgStore.storeInterestStates(dst, mid, iids, states, sync);
          /*}*/

        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    /**
     * @deprecated
     * keep to support tests for old API
     * Now use method with transaction parameter
     */
    public  void updateInterestState(DestinationUID dID,
                SysMessageID mID, ConsumerUID iID, int state,  boolean sync)
                throws BrokerException
    {
        updateInterestState(dID,mID,iID,state,sync,null,false);
    }

    /**
     * Update the state of the interest associated with the specified
     * message.  The interest should already be in the interest list
     * of the message.
     *
     * @param mid	system message id of the message that the interest
     *			is associated with
     * @param cid	id of the interest whose state is to be updated
     * @param state	state of the interest
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not in the store; if the
     *			interest is not associated with the message; or if
     *			an error occurs while persisting the data
     */
    public void updateInterestState(DestinationUID dst, SysMessageID mid,
			ConsumerUID cid, int state, boolean sync, TransactionUID txid, boolean isLastAck)
			throws BrokerException {

		if (Store.getDEBUG()) {
			String msg = "FileStore.updateInterestState() called. mid=" +mid+" state= "+state
			+ " sync=" + sync + " txid=" + txid +" isLastAck= " + isLastAck;
			logger.log(Logger.INFO, msg);			
		}

		// make sure store is not closed then increment in progress count
		super.checkClosedAndSetInProgress();

		try {
			if (mid == null || cid == null) {
				throw new NullPointerException();
			}

			if (Globals.isNewTxnLogEnabled()
					&& (state == PartitionedStore.INTEREST_STATE_ACKNOWLEDGED)) {
				boolean ackLogged = false;
				if (txid != null) {
					ackLogged = true;
				} else if (TransactionLogManager.logNonTransactedMsgAck) {

					// log non-transacted message acknowledgement
					TransactionWorkMessageAck twma = new TransactionWorkMessageAck(
							dst, mid, cid);
					txnLogManager.logNonTxnMessageAck(twma);
					ackLogged = true;
					//dont sync message store update as we have already logged in txn log.
					sync=false;
				}

				if (ackLogged && isLastAck) {
					// Tell loggedMessageHelper that we have logged the last ack
					// for this message.
					// This will mean it won't have to create a log event when
					// the message is removed.
					// (See issue where logging a message without logging
					// message ack/removal
					// can result in duplicate on log replay).
					
					txnLogManager.loggedMessageHelper.lastAckLogged(dst, mid);

					// Performance optimisation: Don't bother updating store for
					// last ack if this ack has been logged.
					// The message entry will be removed from the store in a
					// subsequent call.

					// What about checkpoints: txnlog will be reset and message
					// may not have been removed.
					// So we need to keep track of lastAck logs and wait for
					// corresponding remove.
					// Checkpoint will wait until all pending removes have
					// completed.
				} else {					
					msgStore.updateInterestState(dst, mid, cid, state, false);
				}
			} else {
				
				boolean actualSync = sync;
				if(state == PartitionedStore.INTEREST_STATE_DELIVERED &&
                                   noSyncForDeliveryStateUpdate) {
					actualSync= false;
				}
				
				msgStore.updateInterestState(dst, mid, cid, state, actualSync);
			}

		} finally {
			// decrement in progress count
			super.setInProgress(false);
		}
	}

    /**
	 * Get the state of the interest associated with the specified message.
	 * 
	 * @param mid
	 *            system message id of the message that the interest is
	 *            associated with
	 * @param id
	 *            id of the interest whose state is to be returned
	 * @return state of the interest
	 * @exception BrokerException
	 *                if the specified interest is not associated with the
	 *                message; or if the message is not in the store
	 */
    public int getInterestState(DestinationUID dst, SysMessageID mid,
	ConsumerUID id) throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getInterestState() called");
	}

	if (mid == null || id == null) {
	    throw new NullPointerException();
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return msgStore.getInterestState(dst, mid, id);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve all interests and states associated with the specified message.
     * @param did	the destination the message is associated with
     * @param mid	the system message id of the message that the interest
     * @return HashMap of containing all consumer's state
     * @throws BrokerException
     */
    public HashMap getInterestStates(DestinationUID did,
        SysMessageID mid) throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.getInterestStates() called");
        }

        if (mid == null) {
            throw new NullPointerException();
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return msgStore.getInterestStates(did, mid);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }

    }

    /**
     * Retrieve all interest IDs associated with the specified message.
     * Note that the state of the interests returned is either
     * INTEREST_STATE_ROUTED or INTEREST_STATE_DELIVERED, i.e., interest
     * whose state is INTEREST_STATE_ACKNOWLEDGED will not be returned in the
     * array.
     *
     * @param mid	system message id of the message whose interests
     *			are to be returned
     * @return an array of ConsumerUID objects associated with the message; a
     *		zero length array will be returned if no interest is
     *		associated with the message
     * @exception BrokerException if the message is not in the store
     */
    public ConsumerUID[] getConsumerUIDs(DestinationUID dst, SysMessageID mid)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getConsumerUIDs() called");
	}

	if (mid == null) {
	    throw new NullPointerException();
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return msgStore.getConsumerUIDs(dst, mid);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Check if a a message has been acknowledged by all interests.
     *
     * @param dst  the destination the message is associated with
     * @param id   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked(DestinationUID dst, SysMessageID id)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(logger.INFO,
                "FileStore.hasMessageBeenAcked() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return msgStore.hasMessageBeenAcked(dst, id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    /**
     * Store an Interest which is uniquely identified by it's id.
     *
     * @param interest the interest to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the interest
     * @exception BrokerException if an interest with the same id exists in
     *			the store already
     */
    public void storeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.storeInterest("+interest+", "+sync+") called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    intStore.storeInterest(interest, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove the interest from the persistent store.
     *
     * @param interest	the interest to be removed from persistent store
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the interest
     * @exception BrokerException if the interest is not found in the store
     */
    public void removeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
        "FileStore.removeInterest("+interest+", "+sync+") called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    intStore.removeInterest(interest, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve all interests in the store.
     * Will return as many interests as we can read.
     * Any interests that are retrieved unsuccessfully will be logged as a
     * warning.
     *
     * @return an array of Interest objects; a zero length array is
     * returned if no interests exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Consumer[] getAllInterests() throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getAllInterests() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return intStore.getAllInterests();
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Store a Destination.
     *
     * @param destination	the destination to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting
     *		the destination
     * @exception BrokerException if the same destination exists
     *			the store already
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void storeDestination(Destination destination, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
        "FileStore.storeDestination("+destination+", "+sync+") called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (destination == null) {
		throw new NullPointerException();
	    }

	    dstList.storeDestination(destination, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Update the specified destination.
     *
     * @param destination	the destination to be updated
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     *				or if an error occurs while updating the
     *				destination
     */
    public void updateDestination(Destination destination, boolean sync)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.updateDestination("+destination+", "+sync+")");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (destination == null) {
		throw new NullPointerException();
	    }

	    dstList.updateDestination(destination, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove the destination from the persistent store.
     * All messages associated with the destination will be removed as well.
     *
     * @param destination	the destination to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the destination
     * @exception BrokerException if the destination is not found in the store
     */
    public void removeDestination(Destination destination, boolean sync)
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.removeDestination() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (destination == null) {
		throw new NullPointerException();
	    }

	    dstList.removeDestination(destination, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve a destination in the store.
     *
     * @param id the destination ID
     * @return a Destination object
     * @throws BrokerException if no destination exist in the store
     */
    public Destination getDestination(DestinationUID id)
        throws IOException, BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.getDestination() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            if (id == null) {
                throw new NullPointerException();
            }

            return dstList.getDestination(id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    /**
     * Retrieve all destinations in the store.
     *
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Destination[] getAllDestinations()
	throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getAllDestinations() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return dstList.getAllDestinations();
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Store a transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param ts	the transaction state to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting
     *		the transaction
     * @exception BrokerException if the same transaction id exists
     *			the store already
     * @exception NullPointerException	if <code>id</code> is
     *			<code>null</code>
     */
    public void storeTransaction(TransactionUID id, 
                         TransactionState ts, boolean sync) 
                         throws IOException, BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.storeTransaction() called");
        }

        if (Globals.isNewTxnLogEnabled()) {
            if (Store.getDEBUG()) {
                logger.log(Logger.INFO, "FileStore.storeTransaction() isFastLogTransactions true so returning");
            }
            return;
        }
	
        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

	try {
	    if (id == null || ts == null) {
		throw new NullPointerException();
	    }

	    tidList.storeTransaction(id, ts, sync);

	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove the transaction. The associated acknowledgements
     * will not be removed.
     *
     * @param id	the id of the transaction to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the transaction
     * @exception BrokerException if the transaction is not found
     *			in the store
     */
    public void removeTransaction(TransactionUID id, boolean sync)
	throws IOException, BrokerException {

        removeTransaction(id, false, sync);
    }

    /**
     * Remove the transaction. The associated acknowledgements
     * will not be removed.
     *
     * @param id	the id of the transaction to be removed
     * @param removeAcks if true, will remove all associated acknowledgements
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the transaction
     * @exception BrokerException if the transaction is not found
     *			in the store
     */
    public void removeTransaction(TransactionUID id, boolean removeAcks,
        boolean sync) throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.removeTransaction() called");
	}
	if(Globals.isNewTxnLogEnabled()){
		return;
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (id == null) {
		throw new NullPointerException();
	    }

            if (removeAcks) {
                tidList.removeTransactionAck(id, sync);
            }

	    tidList.removeTransaction(id, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Update the state of a transaction
     *
     * @param id	the transaction id to be updated
     * @param ts	the new transaction state
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting
     *		the transaction id
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     * @exception NullPointerException	if <code>id</code> is
     *			<code>null</code>
     */
    public void updateTransactionState(TransactionUID id, TransactionState ts,
        boolean sync) throws IOException, BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.updateTransactionState( id=" +
                id + ", ts=" + ts.getState() + ") called");
	}
	
	

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (id == null) {
		throw new NullPointerException();
	    }
            if (Globals.isNewTxnLogEnabled()) {	    	   	
   	     //  no op	
            } else {
                tidList.updateTransactionState(id, ts, sync);
            }
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    @Override
    public void updateTransactionStateWithWork(TransactionUID id, 
        TransactionState ts, TransactionWork txnwork, boolean sync)
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        "updateTransactionStateWithWork() not supported by "+getStoreType()+" store");
    
    }

    /**
     * Retrieve all local and cluster transaction ids in the store
     * with their state
     *
     * @return A HashMap. The key is a TransactionUID.
     * The value is a TransactionState.
     * @exception IOException if an error occurs while getting the data
     */
    public HashMap getAllTransactionStates() throws IOException,
			BrokerException {

		if (Store.getDEBUG()) {
			logger.log(Logger.INFO,
					"FileStore.getAllTransactionStates() called");
		}

		// make sure store is not closed then increment in progress count
		super.checkClosedAndSetInProgress();

		try {
			if (Globals.isNewTxnLogEnabled() && !TxnConversionUtil.convertingToTxnLog) {
				return txnLogManager.getAllTransactionStates();
			} else {
				return tidList.getAllTransactionStates();
			}
		} finally {
			// decrement in progress count
			super.setInProgress(false);
		}
	}

    /**
     * Store the acknowledgement for the specified transaction.
     *
     * @param tid	the transaction id with which the acknowledgment is to
     *			be stored
     * @param ack	the acknowledgement to be stored
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction id is not found in the
     *				store, if the acknowledgement already
     *				exists, or if it failed to persist the data
     */
    public void storeTransactionAck(TransactionUID tid,
	TransactionAcknowledgement ack, boolean sync) throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.storeTransactionAck() called");
	}
	
	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (tid == null || ack == null) {
		throw new NullPointerException();
	    }

	    tidList.storeTransactionAck(tid, ack, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Remove all acknowledgements associated with the specified
     * transaction from the persistent store.
     *
     * @param id	the transaction id whose acknowledgements are
     *			to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if error occurs while removing the
     *			acknowledgements
     */
    public void removeTransactionAck(TransactionUID id, boolean sync)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.removeTransactionAck() called");
	}
	
	if(Globals.isNewTxnLogEnabled())
	{
		return;
	}
	
	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (id == null)
		throw new NullPointerException();

	    tidList.removeTransactionAck(id, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve all acknowledgements for the specified transaction.
     *
     * @param tid	id of the transaction whose acknowledgements
     *			are to be returned
     * @exception BrokerException if the operation fails for some reason
     */
    public TransactionAcknowledgement[] getTransactionAcks(
	TransactionUID tid) throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getTransactionAcks() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (tid == null) {
		throw new NullPointerException();
	    }

	    return tidList.getTransactionAcks(tid);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve all acknowledgement list in the persistence store together
     * with their associated transaction id. The data is returned in the
     * form a HashMap. Each entry in the HashMap has the transaction id as
     * the key and an array of the associated TransactionAcknowledgement
     * objects as the value.
     * @return a HashMap object containing all acknowledgement lists in the
     *		persistence store
     */
    public HashMap getAllTransactionAcks() throws BrokerException {
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
			"FileStore.getAllTransactionAcks() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
		if (Globals.isNewTxnLogEnabled() && !TxnConversionUtil.convertingToTxnLog) {
			// this method is used by store API tests.
			// txn log manager does not store txnacks so just return empty map for now.
			// We may want to make txnlog manger hold on to txns and txn acks in memory
			// to support test.
			return new HashMap();
		} else {
	    return tidList.getAllTransactionAcks();
		}
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    public void storeTransaction(TransactionUID id, TransactionInfo txnInfo,
        boolean sync) throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.storeTransaction() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            tidList.storeTransaction(id, txnInfo, sync);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public void storeClusterTransaction(TransactionUID id, TransactionState ts,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.storeClusterTransaction() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
        	if (Globals.isNewTxnLogEnabled()) {
				// no op
			} else {
                tidList.storeClusterTransaction(id, ts, txnBrokers, sync);
			}
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public void updateClusterTransaction(TransactionUID id,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.updateClusterTransaction() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            tidList.updateClusterTransaction(id, txnBrokers, sync);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public TransactionBroker[] getClusterTransactionBrokers(TransactionUID id)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.updateClusterTransactionBrokerState() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getClusterTransactionBrokers(id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public void updateClusterTransactionBrokerState(TransactionUID id,
        int expectedTxnState, TransactionBroker txnBroker, boolean sync)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.updateClusterTransactionBrokerState() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
        	if (Globals.isNewTxnLogEnabled()) {
				txnLogManager.getClusterTransactionManager().updateTransactionBrokerState(id, expectedTxnState, txnBroker, sync);
			} else {
            tidList.updateTransactionBrokerState(
                id, expectedTxnState, txnBroker, sync);
			}
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public void storeRemoteTransaction(TransactionUID id, TransactionState ts,
        TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
        boolean sync) throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.storeRemoteTransaction() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            if (Globals.isNewTxnLogEnabled()) {
                throw new UnsupportedOperationException(
                    "storeRemoteTransaction not supported for isFastLogTransactions");
            } else {
                tidList.storeRemoteTransaction(id, ts, txnAcks, txnHomeBroker, sync);
            }
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public BrokerAddress getRemoteTransactionHomeBroker(TransactionUID id)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getRemoteTransactionHomeBroker() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getRemoteTransactionHomeBroker(id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public HashMap getAllRemoteTransactionStates()
        throws IOException, BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getAllRemoteTransactionStates() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getAllRemoteTransactionStates();
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public TransactionState getTransactionState(TransactionUID id)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getTransactionState() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getTransactionState(id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public TransactionInfo getTransactionInfo(TransactionUID id)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getTransactionInfo() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getTransactionInfo(id);
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public Collection getTransactions(String brokerID)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getTransactions() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return tidList.getAllTransactions();
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    /**
     * Persist the specified property name/value pair.
     * If the property identified by name exists in the store already,
     * it's value will be updated with the new value.
     * If value is null, the property will be removed.
     * The value object needs to be serializable.
     *
     * @param name name of the property
     * @param value value of the property
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting the
     *			data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public void updateProperty(String name, Object value, boolean sync)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.updateProperty() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (name == null)
		throw new NullPointerException();

	    propFile.updateProperty(name, value, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Retrieve the value for the specified property.
     *
     * @param name name of the property whose value is to be retrieved
     * @return the property value; null is returned if the specified
     *		property does not exist in the store
     * @exception BrokerException if an error occurs while retrieving the
     *			data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public Object getProperty(String name) throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getProperty() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (name == null)
		throw new NullPointerException();

	    return propFile.getProperty(name);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Return the names of all persisted properties.
     *
     * @return an array of property names; an empty array will be returned
     *		if no property exists in the store.
     */
    public String[] getPropertyNames() throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getPropertyNames() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return propFile.getPropertyNames();
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Return all persisted properties.
     *
     * @return a properties object.
     */
    public Properties getAllProperties() throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "FileStore.getAllProperties() called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return propFile.getProperties();
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    /**
     * Append a new record to the config change record store.
     * The timestamp is also persisted with the recordData.
     * The config change record store is an ordered list (sorted
     * by timestamp).
     *
     * @param timestamp The time when this record was created.
     * @param recordData The record data.
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting
     *			the data or if the timestamp is less than or
     *			equal to 0
     * @exception NullPointerException if <code>recordData</code> is
     *			<code>null</code>
     */
    public void storeConfigChangeRecord(
	long timestamp, byte[] recordData, boolean sync)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
			"FileStore.storeConfigChangeRecord() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    if (timestamp <= 0) {
		logger.log(Logger.ERROR, BrokerResources.E_INVALID_TIMESTAMP,
			Long.valueOf(timestamp));
		throw new BrokerException(
			br.getString(BrokerResources.E_INVALID_TIMESTAMP,
					Long.valueOf(timestamp)));
	    }

	    if (recordData == null) {
		throw new NullPointerException();
	    }

	    configStore.storeConfigChangeRecord(timestamp, recordData, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Get all the config change records since the given timestamp.
     * Retrieves all the entries with recorded timestamp greater than
     * the specified timestamp.
     * 
     * @return a list of ChangeRecordInfo, empty list if no record
     */
    public ArrayList<ChangeRecordInfo> getConfigChangeRecordsSince(long timestamp)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
			"FileStore.getConfigChangeRecordsSince() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return configStore.getConfigChangeRecordsSince(timestamp);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Return all config records with their corresponding timestamps.
     *
     * @return a list of ChangeRecordInfo
     * @exception BrokerException if an error occurs while getting the data
     */
    public List<ChangeRecordInfo> getAllConfigRecords() throws BrokerException {
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
			"FileStore.getAllConfigRecords() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return configStore.getAllConfigRecords();
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Clear all config change records in the store.
     *
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while clearing the data
     */
    public void clearAllConfigChangeRecords(boolean sync)
	throws BrokerException {
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
			"FileStore.clearAllConfigChangeRecords() called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    configStore.clearAll(sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Return the number of persisted messages and total number of bytes for
     * the given destination.
     *
     * @param dst the destination whose messages are to be counted
     * @return A HashMap of name value pair of information
     * @throws BrokerException if an error occurs while getting the data
     */
    public HashMap getMessageStorageInfo(Destination dst)
        throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "FileStore.getMessageStorageInfo(Destination) called");
        }

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            DestinationUID dstID = dst.getDestinationUID();
            HashMap data = new HashMap(2);
            data.put( DestMetricsCounters.CURRENT_MESSAGES,
                Integer.valueOf(msgStore.getMessageCount(dstID)) );
            data.put( DestMetricsCounters.CURRENT_MESSAGE_BYTES,
                Long.valueOf(msgStore.getByteCount(dstID)) );
            return data;
        } finally {
            // decrement in progress count
            super.setInProgress(false);
        }
    }

    public String getStoreType() {
	return FILE_STORE_TYPE;
    }

    public boolean isJDBCStore() {
        return false;
    }

    /**
     * Get information about the underlying storage for the specified
     * destination.
     * @return A HashMap of name value pair of information
     */
    public HashMap getStorageInfo(Destination destination)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.getStorageInfo(" +
			destination + ") called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    return msgStore.getStorageInfo(destination);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("File-based store", rootDir.getPath());
	t.put("Store version", String.valueOf(STORE_VERSION));
	t.putAll(dstList.getDebugState());
	t.putAll(msgStore.getDebugState());
	t.putAll(intStore.getDebugState());
	if(tidList!=null)
		t.putAll(tidList.getDebugState());
	t.putAll(propFile.getDebugState());
        t.putAll(configStore.getDebugState());
	return t;
    }

    /**
     * Compact the message file associated with the specified destination.
     * If null is specified, message files assocated with all persisted
     * destinations will be compacted..
     */
    public void compactDestination(Destination destination)
	throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.compactDestination(" +
			destination + ") called");
	}

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
	    msgStore.compactDestination(destination);

	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    /**
     * Synchronize data associated with the specified destination to disk.
     * If null is specified, data assocated with all persisted destinations
     * will be synchronized.
     */
    protected void syncDestination(Destination dst) throws BrokerException {

        if (dst == null) {
            // Sync all destination stores
            try {
                Destination[] dlist = dstList.getAllDestinations();
                for (int i = 0, len = dlist.length; i < len; i++) {
                    msgStore.sync(dlist[i].getDestinationUID());
                }
            } catch (IOException ex) {
                logger.logStack(Logger.ERROR,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Failed to synchronize message stores", ex);
                throw new BrokerException(
                    BrokerResources.X_LOAD_DESTINATIONS_FAILED, ex);
            }
        } else {
            msgStore.sync(dst.getDestinationUID());
        }
    }

    /**
     * Synchronize data associated with the specified transaction to disk.
     * If null is specified, data assocated with all persisted transactions
     * will be synchronized.
     */
    private void syncTransaction(TransactionUID tid) throws BrokerException {

    	if(!Globals.isNewTxnLogEnabled())
    	{
    		tidList.sync(tid);
    		tidList.syncTransactionAck(tid);
    	}
    }

    public void logTxn(int type, byte[] data) throws IOException {
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.logTxn(type="+type+") called");
	}

        // Check txnLoggerInited flag to ensure that
        // we don't log to the log files during playbacked!
        if (!txnLoggerInited) {
            return;
        }

        if (type == TransactionLogType.PRODUCE_TRANSACTION) {
            TransactionLogRecord record = msgLogWriter.newTransactionLogRecord();
            record.setType(type);
            record.setBody(data);
            msgLogWriter.write(record);
        } else {
            TransactionLogRecord record = ackLogWriter.newTransactionLogRecord();
            record.setType(type);
            record.setBody(data);
            ackLogWriter.write(record);
        }
    }
    
    
    public void logNonTxnMessage(TransactionWorkMessage twm) throws BrokerException 
    {
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "FileStore.logNonTxnMessage("+twm+") called");
	}
    	txnLogManager.logNonTxnMessage(twm);   	
    }
    
    
    
  /**
    * This is the new API to log transactional work
    * It can be used to log 
    * a) a prepared 2-phase transaction 
    * or 
    * b) a 1-phase transaction
   */
   public void logTxn(BaseTransaction baseTxn) throws BrokerException {
       if (Store.getDEBUG()) {
           logger.log(Logger.INFO, "logTxn("+baseTxn+") called");
       }
       txnLogManager.logTxn(baseTxn);
   }
   
  /** 
   *  This is the new API to log completion of 2-phase transaction.
   *  
   */
   public void logTxnCompletion(TransactionUID tid, int state, int type) throws BrokerException {    
       if (Store.getDEBUG()) {
           logger.log(Logger.INFO, "logTxnCompletion("+tid+", "+state+", "+type+") called");
       }
       txnLogManager.logTxnCompletion(tid,state, type);
   }
   
    public void loggedCommitWrittenToMessageStore(TransactionUID tid, int type) {
        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "loggedCommitWrittenToMessageStore " + tid);
        }
        txnLogManager.loggedCommitWrittenToMessageStore(tid, type);
    }

    protected MsgStore getMsgStore() {
        return msgStore;
    }

    protected DestinationListStore getDstStore() {
        return dstList;
    }
    
    // a file filter that returns true if pathname is a directory
    // whose name starts with "fs"
    private static FilenameFilter storeFilter = new FilenameFilter() {
	public boolean accept(File dir, String name) {
	    return ((new File(dir, name)).isDirectory()
			&& name.startsWith(FILESTORE_BASENAME));
	}
    };

    /**
     * The method does sanity checks on the file store.
     * Newroot is the file store we support in the this release.
     * Oldroot is a version of the store we recognize and might need
     * migration.
     *
     * Return 0 if store doesn't need to be upgrade or the version of the store
     * that needs to be upgrade, e.g. 350 or 200.
     */
    private int checkFileStore(File topDir) throws BrokerException {

	int upgrade = 0;

	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG, "topDir=" + topDir);
	}

	// look for any unsupported file store: a directory
	// whose name starts with 'fs' but is not one of those we expect
	// e.g. a file store of a future release
	String[] names = topDir.list(storeFilter);
	if (names != null) {
	    for (int i = 0; i < names.length; i++) {
		if (!names[i].equals(FILESTORE_TOP)
		    && !names[i].equals(FILESTORE350_TOP)) {

		    File badfs = new File(topDir, names[i]);
		    // unsupported store found
		    logger.log(Logger.ERROR, BrokerResources.E_UNSUPPORTED_FILE_STORE,
					badfs);
		    throw new BrokerException(br.getString(
				BrokerResources.E_UNSUPPORTED_FILE_STORE, badfs));
		}
	    }
	}

	File newRootDir = new File(topDir, FILESTORE_TOP);
        File oldRootDir350 = new File(topDir, FILESTORE350_TOP);
        File oldRootDir200 = new File(topDir, FILESTORE200_TOP);

        boolean storeExist200 = oldRootDir200.exists();
        boolean storeExist350 = oldRootDir350.exists();
        int oldStoreVersion = OLD_STORE_VERSION_200;
        File oldRootDir = oldRootDir200;
        if (storeExist350) {
            // 350 take precedence
            oldStoreVersion = OLD_STORE_VERSION;
            oldRootDir = oldRootDir350;
        } else if (storeExist200) {
            checkOldVersion(new File(oldRootDir, VERSIONFILE), oldStoreVersion);
        }

        if (newRootDir.exists()) {
            // new store exists
            if (!removeStore) {
                // log message only if removeStore is not true
                // log reminder message to remove old store
                if (storeExist350) {
                    logger.logToAll(Logger.INFO,
                        BrokerResources.I_REMOVE_OLDSTORE_REMINDER, oldRootDir );
                } else if (storeExist200) {
                    logger.logToAll(Logger.INFO,
                        BrokerResources.I_REMOVE_OLDSTORE_REMINDER, oldRootDir );
                }
            }
        } else if (storeExist200 || storeExist350) {
            // new store does not exist, but old store exists
            if (removeStore) {
                // do nothing, just log a message and return since
                // the caller will do the remove
                logger.logToAll(Logger.INFO,
                    BrokerResources.I_REMOVE_OLD_PERSISTENT_STORE, oldRootDir);
            } else if (resetStore) {
                // log message to remove old store
                logger.logToAll(Logger.INFO,
                    BrokerResources.I_RESET_OLD_PERSISTENT_STORE, oldRootDir);
                logger.logToAll(Logger.INFO,
                    BrokerResources.I_WILL_CREATE_NEW_STORE);

                // just remove everything
                try {
                    FileUtil.removeFiles(oldRootDir, true);
                } catch (IOException e) {
                    logger.log(Logger.ERROR,
                        BrokerResources.E_RESET_STORE_FAILED, oldRootDir, e);
                    throw new BrokerException(br.getString(
                        BrokerResources.E_RESET_STORE_FAILED, oldRootDir), e);
                }
            } else {
                // log message to do upgrade
                logger.logToAll(Logger.INFO, BrokerResources.I_UPGRADE_STORE_MSG,
                    Integer.valueOf(oldStoreVersion));

                if (upgradeNoBackup && !Broker.getBroker().force) {
                    // will throw BrokerException if the user backs out
                    getConfirmation();
                }

                // set return value to the version of old store need upgrading
                upgrade = oldStoreVersion;
            }
        }

	return upgrade;
    }

    // check the version of the old (200) store
    private void checkOldVersion(File versionfile, int version)
	throws BrokerException {

	if (!versionfile.exists()) {
	    // bad store; store with no version file; throw exception
	    logger.log(Logger.ERROR,
                BrokerResources.E_BAD_OLDSTORE_NO_VERSIONFILE, versionfile);
	    throw new BrokerException(br.getString(
                BrokerResources.E_BAD_OLDSTORE_NO_VERSIONFILE, versionfile));
	}

	RandomAccessFile raf = null;
	int integer = 0;
	try {
	    raf = new RandomAccessFile(versionfile, "r");
	    String str = raf.readLine();
	    raf.close();

	    try {
	    	integer = Integer.parseInt(str);
	    } catch (NumberFormatException e) {
		logger.log(Logger.ERROR,
                    BrokerResources.E_BAD_OLDSTORE_VERSION, str,
                    Integer.toString(version), e);
		throw new BrokerException(br.getString(
                    BrokerResources.E_BAD_OLDSTORE_VERSION, str,
                    Integer.toString(version)), e);
	    }

	    if (integer != version) {
		logger.log(Logger.ERROR,
                    BrokerResources.E_BAD_OLDSTORE_VERSION, str,
                    Integer.toString(version));
		throw new BrokerException(br.getString(
                    BrokerResources.E_BAD_OLDSTORE_VERSION, str,
                    Integer.toString(version)));
	    }
	} catch (IOException e) {
	    logger.log(Logger.ERROR,
                BrokerResources.X_STORE_VERSION_CHECK_FAILED, e);
	    throw new BrokerException(br.getString(
                BrokerResources.X_STORE_VERSION_CHECK_FAILED), e);
	}
    }

    // Initialize txn logging class
    public boolean initTxnLogger() throws BrokerException {

        boolean storeNeedsRestart = false;

        if (removeStore || !Globals.txnLogEnabled()) {
            return storeNeedsRestart;
        }

        logger.log(logger.INFO, BrokerResources.I_TXNLOG_ENABLED);

        // create txn log writers
        String filename = null;
        try {
            SizeString filesize = config.getSizeProperty(TXNLOG_FILE_SIZE_PROP,
                DEFAULT_TXNLOG_FILE_SIZE);

            filename = MSG_LOG_FILENAME;
            msgLogWriter = new FileTransactionLogWriter(
                rootDir, filename, filesize.getBytes());
            msgLogWriter.setCheckPointListener(this);

            filename = ACK_LOG_FILENAME;
            ackLogWriter = new FileTransactionLogWriter(
                rootDir, filename, filesize.getBytes());
            ackLogWriter.setCheckPointListener(this);

            if (resetMessage || resetStore) {
                msgLogWriter.reset();
                ackLogWriter.reset();
                txnLoggerInited = true;
                return storeNeedsRestart;
            }
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR,
                BrokerResources.E_CREATE_TXNLOG_FILE_FAILED, filename, ex);
            throw new BrokerException(br.getString(
                BrokerResources.E_CREATE_TXNLOG_FILE_FAILED, filename), ex);
        }

        // reconstruct persistence store if needed
        try {
            TransactionLogRecord rec;
            byte[] data;
            ByteArrayInputStream bis;
            DataInputStream dis;
            HashSet dstLoadedSet = new HashSet(); // Keep track of loaded dst

            // Check to see if we need to process log files
            if (msgLogWriter.playBackRequired()) {
                storeNeedsRestart = true;
                logger.log(logger.FORCE, BrokerResources.I_PROCESS_MSG_TXNLOG);

                // All destinations need to be loaded
                Globals.getCoreLifecycle().initDestinations();
                Globals.getCoreLifecycle().initSubscriptions();

                int count = 0;

                Iterator itr = msgLogWriter.iterator();
                while (itr.hasNext()) {
                    count++; // Keep track the number of records processed

                    // Read in the messages
                    rec = (TransactionLogRecord)itr.next();

                    int recType = rec.getType();
                    if (recType != TransactionLogType.PRODUCE_TRANSACTION) {
                        // Shouldn't happens
                        logger.log(logger.ERROR,
                            BrokerResources.E_PROCESS_TXNLOG_RECORD_FAILED,
                            String.valueOf(rec.getSequence()),
                            "record type " + recType + " is invalid");
                        continue;
                    }

                    data = rec.getBody();
                    bis = new ByteArrayInputStream(data);
                    dis = new DataInputStream(bis);

                    long tid = dis.readLong(); // Transaction ID
                    String tidStr = String.valueOf(tid);

                    logger.log(logger.FORCE,
                        BrokerResources.I_PROCESS_TXNLOG_RECORD,
                        tidStr, String.valueOf(recType));

                    // Process all msgs in the txn
                    processTxnRecMsgPart(dis, dstLoadedSet);

                    // Check to see if we need to commit the txn
                    if (tid > 0) {
                        TransactionUID tuid = new TransactionUID(tid);
                        TransactionState state = tidList.getTransactionState(tuid);
                        if (state.getState() != TransactionState.NULL &&
                            state.getState() != TransactionState.COMMITTED) {
                            logger.log(logger.FORCE,
                                BrokerResources.I_COMMIT_TXNLOG_RECORD, tidStr);
                            tidList.updateTransactionState(tuid, state, false);
                        }
                    }

                    dis.close();
                    bis.close();
                }

                logger.log(logger.FORCE, BrokerResources.I_LOAD_MSG_TXNLOG,
                    String.valueOf(count));
                logger.flush();
            }

            // Note: the ack log file contains message(s) consume but
            // it can also contains message(s) produce and consume in the
            // same txn. Instead of creating another log file for this type
            // of record, we'll just store it the same file for simplicity.
            if (ackLogWriter.playBackRequired()) {
                storeNeedsRestart = true;
                logger.log(logger.FORCE, BrokerResources.I_PROCESS_ACK_TXNLOG);

                // All destinations need to be loaded
                Globals.getCoreLifecycle().initDestinations();
                Globals.getCoreLifecycle().initSubscriptions();

                int count = 0;

                Iterator itr = ackLogWriter.iterator();
                while (itr.hasNext()) {
                    count++; // Keep track the number of records processed

                    // Read in the acks or msgs & acks
                    rec = (TransactionLogRecord)itr.next();

                    int recType = rec.getType();
                    if (!(recType == TransactionLogType.CONSUME_TRANSACTION ||
                        recType == TransactionLogType.PRODUCE_AND_CONSUME_TRANSACTION)) {
                        // shouldn't happens
                        logger.log(logger.ERROR,
                            BrokerResources.E_PROCESS_TXNLOG_RECORD_FAILED,
                            String.valueOf(rec.getSequence()),
                            "record type " + recType + " is invalid");
                        continue;
                    }

                    data = rec.getBody();
                    bis = new ByteArrayInputStream(data);
                    dis = new DataInputStream(bis);

                    long tid = dis.readLong(); // Transaction ID
                    String tidStr = String.valueOf(tid);

                    logger.log(logger.FORCE,
                        BrokerResources.I_PROCESS_TXNLOG_RECORD,
                        tidStr, String.valueOf(recType));

                    if (recType == TransactionLogType.PRODUCE_AND_CONSUME_TRANSACTION) {
                        // Process all msgs in the txn first!
                        processTxnRecMsgPart(dis, dstLoadedSet);
                    }

                    // Process all acks in the txn
                    processTxnRecAckPart(dis, dstLoadedSet);

                    // Check to see if we need to commit the txn
                    TransactionUID tuid = new TransactionUID(tid);
                    TransactionState state = tidList.getTransactionState(tuid);
                    if (state.getState() != TransactionState.NULL &&
                        state.getState() != TransactionState.COMMITTED) {
                        logger.log(logger.FORCE,
                            BrokerResources.I_COMMIT_TXNLOG_RECORD, tidStr);
                        tidList.updateTransactionState(tuid, state, false);
                    }

                    dis.close();
                    bis.close();
                }

                logger.log(logger.FORCE, BrokerResources.I_LOAD_ACK_TXNLOG,
                    String.valueOf(count));
                logger.flush();
            }

            if (storeNeedsRestart) {
                // Now unload all the destinations that we've loaded so msgs can be routed correctly later on by the broker
                Iterator itr = dstLoadedSet.iterator();
                while (itr.hasNext()) {
                    Destination d = (Destination)itr.next();
                    syncDestination(d); // Sync changes to disk
                    d.unload(true);
                }
                dstLoadedSet = null;

                // Sync changes to txn tables
                tidList.sync(null);
                tidList.syncTransactionAck(null);

                // Reset the txn log after the store is updated & synced
                msgLogWriter.reset();
                ackLogWriter.reset();

                logger.log(logger.FORCE, BrokerResources.I_RECONSTRUCT_STORE_DONE);
                logger.flush();
            }
        } catch (Throwable t) {
            logger.logStack(Logger.ERROR,
                BrokerResources.E_RECONSTRUCT_STORE_FAILED, t);
            throw new BrokerException(br.getString(
                BrokerResources.E_RECONSTRUCT_STORE_FAILED), t);
        }

        txnLoggerInited = true;

        return storeNeedsRestart;
    }
    
    /**
     * Perform a checkpoint
     * Only applicable to FileStore 
     *
     * @param sync Flag to determine whther method block until checpoint is complete    
     * @return status of checkpoint. Will return 0 if completed ok.
     */
    public int doCheckpoint(boolean sync) {
        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "doCheckpoint("+sync+") called");
        }
		int status = 0;
		if (Globals.isNewTxnLogEnabled()) {

			if (sync) {
				txnLogManager.doCheckpoint();
			} else {
				txnLogManager.checkpoint();
				
			}

		} else {
			if (sync) {
				StoreSyncTask sst = new StoreSyncTask();
				sst.run();

			} else {
				checkpoint();
				
			}

		}
		return status;
	}

    
    public final void checkpoint() {
        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "checkpoint() called");
        }
        // Use the timer to sync the store asynchronously from a different
        // thread because syncStore() will wait until inprogressCount
        // is 0 but if this method is being called from the same thread
        // that is originated from a store operation, then the condition
        // will not be met and it will wait forever.
    	
    	
			MQTimer timer = Globals.getTimer();
			timer.schedule(new StoreSyncTask(), 1000);
		
    }

    private void processTxnRecMsgPart(DataInputStream dis, Set dstLoadedSet)
        throws IOException, BrokerException {

        int msgCount = dis.readInt(); // Number of msgs to process
        for (int i = 0; i < msgCount; i++) {
            // Reconstruct the message
            Packet pkt = new Packet(false);
            pkt.generateTimestamp(false);
            pkt.generateSequenceNumber(false);
            pkt.readPacket(dis);

            SysMessageID mid = pkt.getSysMessageID();

            // Make sure dst exists; autocreate if possible
            Destination[] ds = Globals.getDestinationList().getDestination(this,
                pkt.getDestination(),
                pkt.getIsQueue() ? DestType.DEST_TYPE_QUEUE
                : DestType.DEST_TYPE_TOPIC, true, true);
            Destination dst = ds[0];
            DestinationUID did = dst.getDestinationUID();

            // Load all msgs inorder to verify if any msgs are missing
            if (!dstLoadedSet.contains(dst)) {
                dst.load();
                dstLoadedSet.add(dst); // Keep track of what has been loaded
            }

            // Check to see if the msg is in the store
            MsgStore msgStore = getMsgStore();
            if (msgStore.containsMessage(did, mid)) {
                logger.log(logger.FORCE,
                    BrokerResources.I_REPLACE_MSG_TXNLOG, mid, did);
                msgStore.removeMessage(did, mid, false);
            } else {
                logger.log(logger.FORCE,
                    BrokerResources.I_RECONSTRUCT_MSG_TXNLOG, mid, dst + "[load]");
            }

            PacketReference pr =
                PacketReference.createReferenceWithDestination(this, pkt, dst, null);

            try {
                dst.routeNewMessage(pr);
            } catch (SelectorFormatException e) {
                // shouldn't happens
                throw new BrokerException(br.getString(
                    BrokerResources.E_ROUTE_RECONSTRUCTED_MSG_FAILED, mid), e);
            }
        }
    }

    private void processTxnRecAckPart(DataInputStream dis, Set dstLoadedSet)
        throws IOException, BrokerException {

        int ackCount = dis.readInt(); // Number of acks
        for (int i = 0; i < ackCount; i++) {
            String name = dis.readUTF(); // Destination ID
            DestinationUID did = new DestinationUID(name);
            SysMessageID mid = new SysMessageID();
            mid.readID(dis); // SysMessageID
            ConsumerUID iid = new ConsumerUID(dis.readLong()); // ConsumerUID

            // Make sure dst exists; autocreate if possible
            Destination[] ds = Globals.getDestinationList().getDestination(this,
                did.getName(),
                did.isQueue() ? DestType.DEST_TYPE_QUEUE
                : DestType.DEST_TYPE_TOPIC, true, true);
            Destination dst = ds[0];

            // Load all msgs inorder to update consumer states
            if (!dstLoadedSet.contains(dst)) {
                dst.load();
                dstLoadedSet.add(dst); // Keep track of what has been loaded
            }

            if (msgStore.containsMessage(did, mid)) {
                logger.log(logger.FORCE,
                    BrokerResources.I_UPDATE_INT_STATE_TXNLOG, iid, mid);
                // For Queue, ensure the stored ConsumerUID is 0 otherwise
                // use try using the correct value; see bug 6516160
                if (dst.isQueue() && iid.longValue() != 0) {
                    msgStore.updateInterestState(did, mid, PacketReference.getQueueUID(),
                        PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
                } else {
                    msgStore.updateInterestState(did, mid, iid,
                        PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
                }
            } else {
                logger.log(logger.FORCE,
                    BrokerResources.I_DISREGARD_INT_STATE_TXNLOG, iid, mid);
            }
        }
    }

    /**
     * Synchronize the store for txn log checkpoint.
     */
    private void syncStore(TransactionLogWriter[] logWriters)
	throws IOException, BrokerException {
	// Holds on to the closedLock so that no new operation will start
	synchronized (closedLock) {

            // wait until all current store operations are done
            synchronized (inprogressLock) {
		while (inprogressCount > 0) {
                    try {
                        inprogressLock.wait();
                    } catch (Exception e) {
                    }
		}
            }

            // sync msg and txn stores
            syncDestination(null);
            syncTransaction(null);

            // Indicate store is in sync with the log files; this needs to
            // be done while the store is still locked.
            for (int i = 0, len = logWriters.length; i < len; i++) {
		logWriters[i].checkpoint();
            }
        }
    }

    private class StoreSyncTask extends TimerTask {

        public void run() {
            try {
                TransactionLogWriter[] lWriters = {msgLogWriter, ackLogWriter};
                syncStore(lWriters);
            } catch (Throwable e) {
                logger.logStack(Logger.ERROR,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Failed to synchronize persistence store for transaction log checkpoint", e);
            }
        }
    }

    /**
     * Synchronize the store for txn log checkpoint.
     * Used by the new transaction log implementation
     */

    protected void syncStoreOnCheckpoint() throws IOException, BrokerException {
        try {
            // prevent other transactions from starting
            // and wait for all in progress transactions complete
            // System.out.println("waiting for exclusive txn log lock");
            txnLogExclusiveLock.lock();

            /*
             * dont lock store as it can cause deadlock
             * Instead we will rely on txnLogExclusiveLock to stop other threads

            // Holds on to the closedLock so that no new operation will start
            synchronized (closedLock) {
                // wait until all current store operations are done
                synchronized (inprogressLock) {
                    while (inprogressCount > 0) {
                        try {
                            inprogressLock.wait();
                        } catch (Exception e) {
                        }
                    }
                }
                */

                // sync msg and txn stores
                syncDestination(null);
                syncTransaction(null);
           /*
            }
            */

        } finally {
            // System.out.println("releasing exclusive txn log lock");
            txnLogExclusiveLock.unlock();
        }
    }

    public TransactionLogManager getTxnLogManager() {
        return txnLogManager;
    }

    public boolean isTxnConversionRequired() {
        return TxnConversionUtil.isTxnConversionRequired();
    }

    /*******************************************************
     * Unsupported PartitionedStore Interface Methods
     ********************************************************/

    public int[] getTransactionUsageInfo(TransactionUID txnID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public long getTransactionAccessedTime(TransactionUID txnID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public void updateTransactionAccessedTime(TransactionUID txnID,
        long timestamp) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public void updateRemoteTransaction(TransactionUID txnUID,
        TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public long getDestinationConnectedTime(Destination destination)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /*******************************************************
     * Additional PartitionedStore Methods
     ********************************************************/

    /**
     * To close an enumeration retrieved from the store
     */
    public void closeEnumeration(Enumeration en) {
    }


    /********************************************
     * Partitioned Store Specific Methods
     **********************************************/

    public String toString() {
	return "["+getStoreType()+"]";
    }

    public int hashCode() {
	return partitionid.hashCode();
    }

    public boolean equals(Object anObject) {
	if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof PartitionedStore)) {
            return false;
	}
        return getPartitionID().equals(
            ((PartitionedStore)anObject).getPartitionID());

    }

    public void init(Store store, UID id, boolean isPrimary)
    throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public UID getPartitionID() {
        return partitionid;
    }

    public boolean isPrimaryPartition() {
        return true;
    }

    @Override
    public List<PartitionedStore> getAllStorePartitions() throws BrokerException {
        ArrayList<PartitionedStore> list = new ArrayList<PartitionedStore>();
        list.add(this);
        return list;
    }

    public PartitionedStore getPrimaryPartition() throws BrokerException {
        return this;
    }

}

