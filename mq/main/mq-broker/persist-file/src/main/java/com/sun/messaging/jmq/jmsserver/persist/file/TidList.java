/*
 * Copyright (c) 2000, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)TidList.java	1.39 08/30/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.PHashMapMMF;
import com.sun.messaging.jmq.io.disk.VRFileWarning;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;

import java.io.*;
import java.util.*;


/**
 * Keep track of all persisted transaction states by using PHashMap.
 */
class TidList {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    BrokerConfig config = Globals.getConfig();

    // initial size of backing file
    static final String TXN_USE_MEMORY_MAPPED_FILE_PROP
        = FileStore.FILE_PROP_PREFIX + "transaction.memorymappedfile.enabled";
    static final String TXN_UPDATE_OPTIMIZATION_PROP
        = FileStore.FILE_PROP_PREFIX +
            "transaction.memorymappedfile.updateoptimization.enabled";
    static final String TXN_FILE_SIZE_PROP
        = FileStore.FILE_PROP_PREFIX + "transaction.file.size";

    static final boolean DEFAULT_TXN_USE_MEMORY_MAPPED_FILE = true;
    static final boolean DEFAULT_TXN_UPDATE_OPTIMIZATION = true;
    static final long DEFAULT_TXN_FILE_SIZE = 1024; // 1024k = 1M

    // 1 byte for modified txn state + 16 byte for modified broker states
    static final int CLIENT_DATA_SIZE = 17;

    static final String BASENAME = "txn"; // basename of data file

    // cache all persisted transaction ids
    // maps tid -> txn info
    private PHashMap tidMap = null;

    private boolean useMemoryMappedFile = true;
    private boolean updateOptimization = true;
    private File backingFile = null;

    // object encapsulates persistence of all transactions' ack lists
    private TxnAckList txnAckList = null;

    private LoadException loadException = null;

    // when instantiated, all data are loaded
    TidList(FileStore p, File topDir, boolean clear) throws BrokerException {

	SizeString filesize = config.getSizeProperty(TXN_FILE_SIZE_PROP,
					DEFAULT_TXN_FILE_SIZE);

	backingFile = new File(topDir, BASENAME);
	try {
            useMemoryMappedFile = config.getBooleanProperty(
                TXN_USE_MEMORY_MAPPED_FILE_PROP,
                DEFAULT_TXN_USE_MEMORY_MAPPED_FILE);
            updateOptimization = useMemoryMappedFile &&
                config.getBooleanProperty(
                    TXN_UPDATE_OPTIMIZATION_PROP,
                    DEFAULT_TXN_UPDATE_OPTIMIZATION);

            // safe = false; caller controls data synchronization
            if (useMemoryMappedFile) {
                tidMap = new PHashMapMMF(backingFile, filesize.getBytes(), 1024, false,
                             clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
                if (updateOptimization) {
                    ((PHashMapMMF)tidMap).intClientData(CLIENT_DATA_SIZE);
                }
            } else {
                tidMap = new PHashMap(backingFile, filesize.getBytes(), 1024, false, 
                             clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            }
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TRANSACTIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TRANSACTIONS_FAILED), e);
	}

	try {
	    tidMap.load(p);

            // Note: To support cluster txn APIs in 4.1, we store the
            // TransactionInfo obj in place of the TransactionState obj.
            // Since this is a minor change and doesn't warrant a store
            // version upgrade, we need to check and update the value
            // store in the PHashMap if we're loading an old store.
            boolean oldStore = false;
            Set entries = tidMap.entrySet();
            Iterator itr = entries.iterator();
            if (itr.hasNext()) {
                // Only needs to check the 1st entry
                Map.Entry entry = (Map.Entry)itr.next();
                Object value = entry.getValue();
                if (value instanceof TransactionState) {
                    oldStore = true;
                }
            }

            // Process client data
            if (oldStore) {
                loadClientDataOldFormat();

                // Convert to new format
                itr = entries.iterator();
                while (itr.hasNext()) {
                    Map.Entry entry = (Map.Entry)itr.next();
                    Object key = entry.getKey();
                    Object value = entry.getValue();

                    // Replace TransactionState obj with TransactionInfo obj
                    tidMap.put(key, new TransactionInfo((TransactionState)value));
                }
            } else {
                loadClientData();
            }
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TRANSACTIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TRANSACTIONS_FAILED), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TRANSACTIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TRANSACTIONS_FAILED), e);
	} catch (PHashMapLoadException le) {

	    while (le != null) {
		logger.log(Logger.WARNING, br.X_FAILED_TO_LOAD_A_TXN, le);

		// save info in LoadException
		LoadException e = new LoadException(le.getMessage(),
						le.getCause());
		e.setKey(le.getKey());
		e.setValue(le.getValue());
		e.setKeyCause(le.getKeyCause());
		e.setValueCause(le.getValueCause());
		e.setNextException(loadException);
		loadException = e;

		// get the chained exception
		le = le.getNextException();
	    }
	}

	VRFileWarning w = tidMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of transaction data", w);
	}

	if (clear && Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
				"TidList initialized with clear option");
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "TidList: loaded "+
					tidMap.size() + " transactions");
	}

	// load transaction acknowledgements
	txnAckList = new TxnAckList(p, topDir, clear);
    }

    // when instantiated, old data are upgraded
    TidList(FileStore p, File topDir, File oldTop)
	throws BrokerException {

	File oldFile = new File(oldTop, BASENAME);
	PHashMap olddata = null;

	backingFile = new File(topDir, BASENAME);
	try {
	    // load old data
	    // safe=false; reset=false
	    olddata = new PHashMap(oldFile, false, false, 
                          Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    olddata.load(p);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException le) {

	    while (le != null) {
		logger.log(Logger.WARNING,
			br.X_FAILED_TO_LOAD_A_TXN_FROM_OLDSTORE, le);

		// save info in LoadException
		LoadException e = new LoadException(le.getMessage(),
						le.getCause());
		e.setKey(le.getKey());
		e.setValue(le.getValue());
		e.setKeyCause(le.getKeyCause());
		e.setValueCause(le.getValueCause());
		e.setNextException(loadException);
		loadException = e;

		// get the chained exception
		le = le.getNextException();
	    }
	}

	VRFileWarning w = olddata.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of transaction data in old store", w);
	}

	try {
            useMemoryMappedFile = config.getBooleanProperty(
                TXN_USE_MEMORY_MAPPED_FILE_PROP,
                DEFAULT_TXN_USE_MEMORY_MAPPED_FILE);
            updateOptimization = useMemoryMappedFile &&
                config.getBooleanProperty(
                    TXN_UPDATE_OPTIMIZATION_PROP,
                    DEFAULT_TXN_UPDATE_OPTIMIZATION);

            // pass in safe=false; caller decide when to sync
            // safe=false; reset=false
            if (useMemoryMappedFile) {
	        tidMap = new PHashMapMMF(backingFile, oldFile.length(), 1024, false, 
                             false, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
                if (updateOptimization) {
                    ((PHashMapMMF)tidMap).intClientData(CLIENT_DATA_SIZE);
                }
            } else {
                tidMap = new PHashMap(backingFile, oldFile.length(), 1024, false, 
                             false, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            }
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    tidMap.load(p);

            // Process client data
            loadClientData();
	} catch (ClassNotFoundException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	} catch (IOException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TRANSACTIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TRANSACTIONS_FAILED,
				oldFile, backingFile), e);
	}

	w = tidMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of transaction data", w);
	}

	// just copy old data to new store
	Iterator itr = olddata.entrySet().iterator();
	while (itr.hasNext()) {
	    Map.Entry entry = (Map.Entry)itr.next();
	    Object key = entry.getKey();
	    Object value = entry.getValue();

            // replace TransactionState obj with TransactionInfo obj
	    tidMap.put(key, new TransactionInfo((TransactionState)value));
	}
	olddata.close();

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG,
                "TidList: upgraded " + tidMap.size() + " transactions");
	}

	// if upgradeNoBackup, remove oldfile
	if (p.upgradeNoBackup()) {
	    if (!oldFile.delete()) {
		logger.log(logger.ERROR, br.I_DELETE_FILE_FAILED, oldFile);
	    }
	}

	// load transaction acknowledgements
	txnAckList = new TxnAckList(p, topDir, oldTop, p.upgradeNoBackup());
    }

    LoadException getLoadException() {
	return loadException;
    }

    LoadException getLoadTransactionAckException() {
	return txnAckList.getLoadException();
    }

    void close(boolean cleanup) {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
		"TidList: closing, "+tidMap.size()+" persisted transactions");
	}

	tidMap.close();
	txnAckList.close(cleanup);
    }

    /**
     * Store a transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param ts	the transaction's state to be persisted
     * @exception BrokerException if an error occurs while persisting or
     * the same transaction id exists the store already
     * @exception NullPointerException	if <code>id</code> is <code>null</code>
     */
    void storeTransaction(TransactionUID id, TransactionState ts, boolean sync)
	throws IOException, BrokerException {

        try {
            // TransactionState is mutable, so we must store a copy
            // See bug 4989708
            Object oldValue = tidMap.putIfAbsent(
                id, new TransactionInfo(new TransactionState(ts)));

            if (oldValue != null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id);
                throw new BrokerException(br.getString(
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id));
            }

            if (sync) {
                // TODO - we get better sync performance while holding the lock
                // so we should pass sync flag to the put method
                sync(id);
            }
        } catch (RuntimeException e) {
            logger.log(Logger.ERROR,
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id, e);
            throw new BrokerException(br.getString(
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id), e);
        }
    }

    /**
     * Store a cluster transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param ts	the transaction's state to be persisted
     * @param brokers	the transaction's participant brokers
     * @exception BrokerException if an error occurs while persisting or
     * the same transaction id exists the store already
     * @exception NullPointerException	if <code>id</code> is <code>null</code>
     */
    public void storeClusterTransaction(TransactionUID id, TransactionState ts,
        TransactionBroker[] brokers, boolean sync) throws BrokerException {

        TransactionInfo txnInfo = null;
        try {
            // TransactionState is mutable, so we must store a copy
            // See bug 4989708
            txnInfo = new TransactionInfo(new TransactionState(ts), null,
                brokers, TransactionInfo.TXN_CLUSTER);

            Object oldValue = tidMap.putIfAbsent(id, txnInfo);
            if (oldValue != null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id);
                throw new BrokerException(br.getString(
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id));
            }

            if (sync) {
                sync(id);
            }
        } catch (RuntimeException e) {
            String msg = (txnInfo != null) ?
                id + " " + txnInfo.toString() : id.toString();
            logger.log(Logger.ERROR,
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, msg, e);
            throw new BrokerException(br.getString(
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, msg), e);
        }
    }

    /**
     * Store a remote transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param ts	the transaction's state to be persisted
     * @param acks	the transaction's participant brokers
     * @param txnHomeBroker the transaction's home broker
     * @exception BrokerException if an error occurs while persisting or
     * the same transaction id exists the store already
     * @exception NullPointerException	if <code>id</code> is <code>null</code>
     */
    public void storeRemoteTransaction(TransactionUID id, TransactionState ts,
        TransactionAcknowledgement[] acks, BrokerAddress txnHomeBroker,
        boolean sync) throws BrokerException {

        TransactionInfo txnInfo = null;
        boolean removedAcksFlag = false;
        try {
            if (tidMap.containsKey(id)) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id);
                throw new BrokerException(br.getString(
                    BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE, id));
            }

            // We must store the acks first if provided
            if (acks != null && acks.length > 0) {
                txnAckList.storeAcks(id, acks, sync);
                removedAcksFlag = true;
            }

            // Now we store the txn;
            // TransactionState is mutable, so we must store a copy
            txnInfo = new TransactionInfo(new TransactionState(ts), txnHomeBroker,
                    null, TransactionInfo.TXN_REMOTE);
            tidMap.put(id, txnInfo);

            if (sync) {
                sync(id);
            }
        } catch (RuntimeException e) {
            String msg = (txnInfo != null) ?
                id + " " + txnInfo.toString() : id.toString();
            logger.log(Logger.ERROR,
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, msg, e);

            try {
                if (removedAcksFlag) {
                    txnAckList.removeAcks(id, sync);
                }
            } catch (Exception ex) {
                // Just ignore because error has been logged at lower level
            }

            throw new BrokerException(br.getString(
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, msg), e);
        }
    }

    /**
     * Store a transaction - should only be used for imqdbmgr
     * backup/restore operation.
     */
    void storeTransaction(TransactionUID id, TransactionInfo txnInfo, boolean sync)
	throws BrokerException {

        try {
            tidMap.put(id, txnInfo);

            if (sync) {
                sync(id);
            }
        } catch (RuntimeException e) {
            logger.log(Logger.ERROR,
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id, e);
            throw new BrokerException(br.getString(
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id), e);
        }
    }

    /**
     * Remove the transaction. The associated acknowledgements
     * will not be removed.
     *
     * @param id	the id of the transaction to be removed
     * @exception BrokerException if the transaction is not found in the store
     */
    void removeTransaction(TransactionUID id, boolean sync)
	throws BrokerException {

        try {
            Object txnInfo = tidMap.remove(id);

            if (txnInfo == null) {
                logger.log(logger.ERROR,
                    br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
                throw new BrokerException(
                    br.getString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                    Status.NOT_FOUND);
            }

            if (sync) {
                sync(id);
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_REMOVE_TRANSACTION_FAILED, id, e);
            throw new BrokerException(
                br.getString(br.X_REMOVE_TRANSACTION_FAILED, id), e);
        }
    }

    /**
     * Update the state of a transaction
     *
     * @param id	the transaction id to be updated
     * @param ts	the new transaction state
     * @exception BrokerException if an error occurs while persisting or
     * the same transaction id does NOT exists the store already
     * @exception NullPointerException	if <code>id</code> is <code>null</code>
     */
    void updateTransactionState(TransactionUID id, TransactionState tstate, boolean sync)
	throws IOException, BrokerException {

        try {
            TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);
            if (txnInfo == null) {
                logger.log(logger.ERROR,
                    br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
                throw new BrokerException(
                    br.getString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                    Status.NOT_FOUND);
            }

            TransactionState txnState = txnInfo.getTransactionState();
            int ts = tstate.getState();
            if (txnState.getState() != ts) {
                txnState.setState(ts);
                if (tstate.getOnephasePrepare()) {
                    txnState.setOnephasePrepare(true);
                }
                if (updateOptimization) {
                    // To improve I/O performance, just persist the new
                    // state as client data and not the whole record
                    byte[] cd = generateClientData(id, txnInfo);
                    PHashMapMMF tidMapMMF = (PHashMapMMF)tidMap;
                    tidMapMMF.putClientData(id, cd);
                } else {
                    tidMap.put(id, txnInfo);
                }
            } else {
                sync = false;   // No need to sync
            }

            if (sync) {
                sync(id);
            }
        } catch (Exception e) {
            logger.log(logger.ERROR, br.X_UPDATE_TXNSTATE_FAILED, id, e);
            throw new BrokerException(
                br.getString(br.X_UPDATE_TXNSTATE_FAILED, id), e);
        }
    }

    /**
     * Update transaction's participant brokers.
     *
     * @param id        the id of the transaction to be updated
     * @param brokers   the transaction's participant brokers
     * @exception BrokerException if the transaction is not found in the store
     */
    public void updateClusterTransaction(TransactionUID id,
        TransactionBroker[] brokers, boolean sync) throws BrokerException {

        try {
            TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);
            if (txnInfo == null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
                throw new BrokerException(br.getString(
                    BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                    Status.NOT_FOUND);
            }

            txnInfo.setType(TransactionInfo.TXN_CLUSTER);
            txnInfo.setTransactionBrokers(brokers);

            tidMap.put(id, txnInfo);

            if (sync) {
                sync(id);
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_TRANSACTION_FAILED, id, e);
            throw new BrokerException(
                br.getString(br.X_PERSIST_TRANSACTION_FAILED, id), e);
        }
    }

    /**
     * Update transaction's participant broker state if the txn's state
     * matches the expected state.
     *
     * @param id the id of the transaction to be updated
     * @param expectedTxnState the expected transaction state
     * @param txnBkr the participant broker to be updated
     * @exception BrokerException if the transaction is not found in the store
     * or the txn's state doesn't match the expected state (Status.CONFLICT)
     */
    void updateTransactionBrokerState(TransactionUID id, int expectedTxnState,
        TransactionBroker txnBkr, boolean sync) throws BrokerException {

        try {
            TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);

            if (txnInfo == null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
                throw new BrokerException(br.getString(
                    BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                    Status.NOT_FOUND);
            }

            TransactionState txnState = txnInfo.getTransactionState();
            if (txnState.getState() != expectedTxnState) {
                Object[] args = { txnBkr, id,
                    TransactionState.toString(expectedTxnState),
                    TransactionState.toString(txnState.getState()) };
                throw new BrokerException(br.getKString( BrokerResources.E_UPDATE_TXNBROKER_FAILED,
                    args ), Status.CONFLICT);
            }

            txnInfo.updateBrokerState(txnBkr);

            if (updateOptimization) {
                // To improve I/O performance, just persist the new
                // state as client data and not the whole record
                byte[] cd = generateClientData(id, txnInfo);
                PHashMapMMF tidMapMMF = (PHashMapMMF)tidMap;
                tidMapMMF.putClientData(id, cd);

            } else {
                tidMap.put(id, txnInfo);
            }

            if (sync) {
                sync(id);
            }
        } catch (Exception e) {
            logger.log(Logger.ERROR,
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id, e);
            throw new BrokerException(br.getString(
                BrokerResources.X_PERSIST_TRANSACTION_FAILED, id), e);
        }
    }

    /**
     * Return transaction state object for the specified transaction.
     * @param id id of the transaction
     * @exception BrokerException if the transaction id is not in the store
     */
    TransactionState getTransactionState(TransactionUID id)
        throws BrokerException {

        TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);

        if (txnInfo == null) {
            logger.log(Logger.ERROR,
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
            throw new BrokerException(br.getString(
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                Status.NOT_FOUND);
        }

        return new TransactionState(txnInfo.getTransactionState());
    }

    /**
     * Return transaction state for the specified transaction.
     * @param id id of the transaction
     */
    int getTransactionStateValue(TransactionUID id)
        throws BrokerException {

        TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);
        if (txnInfo != null) {
            TransactionState txnState = txnInfo.getTransactionState();
            if (txnState != null) {
                return txnState.getState();
            }
        }

        return TransactionState.NULL;
    }    

    /**
     * Return transaction info object for the specified transaction.
     * @param id id of the transaction
     * @exception BrokerException if the transaction id is not in the store
     */
    TransactionInfo getTransactionInfo(TransactionUID id)
        throws BrokerException {

        TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);

        if (txnInfo == null) {
            logger.log(Logger.ERROR,
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
            throw new BrokerException(br.getString(
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                Status.NOT_FOUND);
        }

        return (TransactionInfo)txnInfo.clone();
    }

    /**
     * Return transaction home broker for the specified transaction.
     * @param id id of the transaction
     * @exception BrokerException if the transaction id is not in the store
     */
    BrokerAddress getRemoteTransactionHomeBroker(TransactionUID id)
        throws BrokerException {

        TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);

        if (txnInfo == null) {
            logger.log(Logger.ERROR,
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
            throw new BrokerException(br.getString(
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                Status.NOT_FOUND);
        }

        return txnInfo.getTransactionHomeBroker();
    }

    /**
     * Return transaction's participant brokers for the specified transaction.
     * @param id id of the transaction whose participant brokers are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    TransactionBroker[] getClusterTransactionBrokers(TransactionUID id)
	throws BrokerException {

        TransactionInfo txnInfo = (TransactionInfo)tidMap.get(id);

        if (txnInfo == null) {
            logger.log(Logger.ERROR,
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id);
            throw new BrokerException(br.getString(
                BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE, id),
                Status.NOT_FOUND);
        }

        TransactionBroker[] txnBrokers = txnInfo.getTransactionBrokers();
        if (txnBrokers != null) {
            // Make a copy
            txnBrokers = (TransactionBroker[])txnBrokers.clone();
        }

        return txnBrokers;
    }

    /**
     * Retrieve all local and cluster transaction ids with their state
     * in the store.
     *
     * @return A HashMap. The key is a TransactionUID.
     * The value is a TransactionState.
     * @exception IOException if an error occurs while getting the data
     */
    HashMap getAllTransactionStates() throws IOException {
	HashMap map = new HashMap(tidMap.size());
        Iterator itr = tidMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry entry = (Map.Entry)itr.next();
            TransactionInfo txnInfo = (TransactionInfo)entry.getValue();
            int type = txnInfo.getType();
            if (type == TransactionInfo.TXN_LOCAL ||
                type == TransactionInfo.TXN_CLUSTER) {
                map.put(entry.getKey(), (new TransactionInfo(txnInfo)));
            }
        }

        return map;
    }

    /**
     * Retrieve all remote transaction ids with their state in the store.
     *
     * @return A HashMap. The key is a TransactionUID.
     * The value is a TransactionState.
     * @exception IOException if an error occurs while getting the data
     */
    HashMap getAllRemoteTransactionStates() throws IOException {
	HashMap map = new HashMap(tidMap.size());
        Iterator itr = tidMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry entry = (Map.Entry)itr.next();
            TransactionInfo txnInfo = (TransactionInfo)entry.getValue();
            int type = txnInfo.getType();
            if (type == TransactionInfo.TXN_REMOTE) {
                map.put(entry.getKey(), (new TransactionState(txnInfo.getTransactionState())));
            }
        }

        return map;
    }

    Collection getAllTransactions() {
        return tidMap.keySet();
    }

    /**
     * Clear all transaction ids and the associated ack lists(clear the store);
     * when this method returns, the store has a state that is the same as
     * an empty store (same as when TxnAckList is instantiated with the
     * clear argument set to true.
     */
    void clearAll(boolean sync) {

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH, "TidList.clearAll() called");
	}

        try {
            tidMap.clear();

            if (sync) {
                sync(null);
            }
        } catch (BrokerException e) {
            // at least log it
            logger.log(logger.ERROR, br.getString(
                            br.X_CLEAR_TRANSACTION_FILE_FAILED,
                            backingFile), e);
        } catch (RuntimeException e) {
            // at least log it
            logger.log(logger.ERROR, br.getString(
                            br.X_CLEAR_TRANSACTION_FILE_FAILED,
                            backingFile), e);
        }

	// clear ack lists
	txnAckList.clearAll(sync);
    }

    /**
     * Clear all transactions that are NOT in the specified state.
     *
     * @param state State of transactions to spare
     */
    void clear(int state, boolean sync) throws BrokerException {

        boolean error = false;
	Exception exception = null;

        Iterator itr = tidMap.entrySet().iterator();
        while (itr.hasNext()) {
            try {
                Map.Entry entry = (Map.Entry)itr.next();
                TransactionUID tid = (TransactionUID)entry.getKey();
                TransactionState ts = (TransactionState)entry.getValue();
                // Remove if not in prepared state
                    if (ts.getState() != state) {
                    // XXX PERF 12/7/2001 This operation updates disk
                    // with every call.
                    itr.remove();
                    try {
                        txnAckList.removeAcks(tid, sync);
                    } catch (BrokerException e) {
                        // TxnAckList Logs error
                        error = true;
                        exception = e;
                        break;
                    }
                }
            } catch (RuntimeException e) {
                error = true;
                exception = e;
                logger.log(logger.ERROR, br.X_CLEAR_TXN_NOTIN_STATE_FAILED,
                    Integer.valueOf(state), e);
            }

	    if (sync) {
		sync(null);
	    }
        }

        if (!error) {
            // We may have transactions left in the txnAckList that did not
            // have an entry in the tidMap (maybe it was removed by a commit
            // that never had a chance to complete processing the acks).
            // Check for those "orphaned" ack transactions here.
            TransactionUID[] tids = txnAckList.getAllTids();
            for (int i = 0; i < tids.length; i++) {
                TransactionInfo txnInfo = (TransactionInfo)tidMap.get(tids[i]);
                if (txnInfo == null || txnInfo.getTransactionStateValue() != state) {
                    // Orphan. Remove from txnList
                    try {
                        txnAckList.removeAcks(tids[i], sync);
                    } catch (BrokerException e) {
                        // TxnAckList Logs error
                        error = true;
			exception = e;
			break;
                    }
                }
            }
        }

        // If we got an error just clear all transactions. 
        if (error) {
            clearAll(sync);
	    throw new BrokerException(
			br.getString(br.X_CLEAR_TXN_NOTIN_STATE_FAILED,
			Integer.valueOf(state)), exception);
        }
    }

    /**
     * Store an acknowledgement for the specified transaction.
     */
    void storeTransactionAck(TransactionUID tid, TransactionAcknowledgement ack,
	boolean sync) throws BrokerException {

        if (!tidMap.containsKey(tid)) {
            logger.log(logger.ERROR, br.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    tid.toString());
            throw new BrokerException(
                    br.getString(br.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                                    tid.toString()));
        }

	txnAckList.storeAck(tid, ack, sync);
    }

    /**
     * Remove all acknowledgements associated with the specified
     * transaction from the persistent store.
     */
    void removeTransactionAck(TransactionUID id, boolean sync)
	throws BrokerException {
	txnAckList.removeAcks(id, sync);
    }

    /**
     * Retrieve all acknowledgements for the specified transaction.
     */
    TransactionAcknowledgement[] getTransactionAcks(TransactionUID tid)
	throws BrokerException {

	return txnAckList.getAcks(tid);
    }

    /**
     * Retrieve all acknowledgement list in the persistence store
     * together with their associated transaction id.
     */
    HashMap getAllTransactionAcks() {
	return txnAckList.getAllAcks();
    }

    /**
     * @return the total number of transaction acknowledgements in the store
     */
    public int getNumberOfTxnAcks() {
	return txnAckList.getNumberOfTxnAcks();
    }
    
    /**
     * @return the total number of transactions in the store
     */
    public int getNumberOfTxns() {
	return this.tidMap.size();
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("Transactions", String.valueOf(tidMap.size()));
	t.putAll(txnAckList.getDebugState());
	return t;
    }

    void printInfo(PrintStream out) {
	// transacation ids
	out.println("\nTransaction IDs");
	out.println("---------------");
	out.println("backing file: "+ backingFile);
	out.println("number of transaction ids: " + tidMap.size());

	// transaction acknowledgement list info
	txnAckList.printInfo(out);
    }

    /**
     * When update optimization is enabled, we'll only write out the modified
     * transaction state as client data instead of the whole record (key and
     * value) to improve I/O performance. So when the broker start up and the
     * persisted transaction states are loaded from file, we need to update the
     * TransactionState object with the modified value that is stored in the
     * client data section of the record.
     * @throws PHashMapLoadException if an error occurs while loading data
     */
    private void loadClientData() throws PHashMapLoadException {

        if (!updateOptimization) {
            return; // nothing to do
        }

        PHashMapLoadException loadException = null;

        Iterator itr = tidMap.entrySet().iterator();
        while (itr.hasNext()) {
            Throwable ex = null;
            Map.Entry entry = (Map.Entry)itr.next();
            Object key = entry.getKey();
            TransactionInfo value = (TransactionInfo)entry.getValue();
            byte[] cData = null;
            try {
                cData = ((PHashMapMMF)tidMap).getClientData(key);
                if (cData != null && cData.length > 0) {
                    int state = (int)cData[0]; // 1st byte is the modified state
                    value.getTransactionState().setState(state); // update txn state

                    // Now read in modified txn broker states
                    TransactionBroker[] bkrs = value.getTransactionBrokers();
                    if (bkrs != null) {
                        for (int i = 0, len = bkrs.length; i < len; i++) {
                            TransactionBroker bkr = bkrs[i];

                            // update bkr's state
                            boolean isComplete = ((int)cData[i+1] == 1); // 1 == true
                            bkr.setCompleted(isComplete);
                        }
                    }
                }
            } catch (Throwable e) {
                ex = e;
            }

            if (ex != null) {
                PHashMapLoadException le = new PHashMapLoadException(
                    "Failed to load client data [cData=" +Arrays.toString(cData)+"]");
                le.setKey(key);
                le.setValue(value);
                le.setNextException(loadException);
                le.initCause(ex);
                loadException = le;
            }
        }

        if (loadException != null) {
            throw loadException;
        }
    }

    private void loadClientDataOldFormat() throws PHashMapLoadException {

        if (!updateOptimization) {
            return; // nothing to do
        }

        PHashMapLoadException loadException = null;

        Iterator itr = tidMap.entrySet().iterator();
        while (itr.hasNext()) {
            Throwable ex = null;
            Map.Entry entry = (Map.Entry)itr.next();
            Object key = entry.getKey();
            TransactionState value = (TransactionState)entry.getValue();
            byte[] cData = null;
            try {
                cData = ((PHashMapMMF)tidMap).getClientData(key);
                if (cData != null && cData.length > 0) {
                    int state = (int)cData[0]; // 1st byte is the modified state
                    value.setState(state);     // update txn state
                }
            } catch (Throwable e) {
                ex = e;
            }

            if (ex != null) {
                PHashMapLoadException le = new PHashMapLoadException(
                    "Failed to load client data [cData=" +Arrays.toString(cData)+"]");
                le.setKey(key);
                le.setValue(value);
                le.setNextException(loadException);
                le.initCause(ex);
                loadException = le;
            }
        }

        if (loadException != null) {
            throw loadException;
        }
    }

    private byte[] generateClientData(TransactionUID tid,
        TransactionInfo txnInfo) throws BrokerException {

        byte[] cd = new byte[CLIENT_DATA_SIZE];
        cd[0] = (byte)txnInfo.getTransactionStateValue(); // txn state value

        TransactionBroker[] bkrs = txnInfo.getTransactionBrokers();
        int numBkrs = (bkrs != null) ? bkrs.length : 0;
        if (numBkrs > CLIENT_DATA_SIZE - 1) {
            // Big problem!!! CD only support 16 brokers max
            throw new BrokerException(
                "Internal Error: transaction broker list size of " + numBkrs +
                " is larger than the reserved client data byte limit of " +
                (CLIENT_DATA_SIZE - 1) + " for transaction " + tid);
        }

        for (int i = 1; i < CLIENT_DATA_SIZE; i++) {
            // 1 = true and 0 = false
            int bIndex = i - 1;
            if (bIndex < numBkrs) {
                cd[i] = (byte)(bkrs[bIndex].isCompleted() ? 1 : 0);
            } else {
                cd[i] = (byte)0;
            }
        }

        return cd;
    }

    void sync(Object key) throws BrokerException {
	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "TidList sync() ";
			logger.log(Logger.DEBUG,msg);
		}
	    tidMap.force(key);
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize data to disk for file: " + backingFile, e);
	}
    }
    
    void syncTransactionAck(TransactionUID tid) throws BrokerException {
        txnAckList.sync(tid);
    }
    
    
   
    
    public static void deleteAllFiles(File rootDir) throws BrokerException {

		File file = new File(rootDir, BASENAME);
		if (file.exists()) {
			boolean deleted = file.delete();
			if (!deleted) {
                String[] args = {BASENAME, rootDir.getPath(), BASENAME+".deleted"};
                Globals.getLogger().log(Logger.WARNING, 
                    Globals.getBrokerResources().getKString(
                        BrokerResources.W_UNABLE_DELETE_FILE_IN_DIR, args));
                File nf = new File(rootDir, BASENAME+".deleted");
		        if (!file.renameTo(nf)) {
                    Globals.getLogger().log(Logger.WARNING, 
                        Globals.getBrokerResources().getKString(
                            BrokerResources.W_UNABLE_RENAME_FILE, file.getPath(), nf.getPath()));

				    throw new BrokerException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_COULD_NOT_DELETE_FILE, file));
                }
			}
		}
	}

	public static boolean txFileExists(File rootDir) {
		boolean result = false;
		String filename = BASENAME;
		File txFile = new File(rootDir, filename);
		result = txFile.exists();		
		return result;
	}

	public static boolean txAckFileExists(File rootDir) {
		boolean result = false;
		String filename = TxnAckList.BASENAME;
		File txFile = new File(rootDir, filename);
		result = txFile.exists();
		return result;
	}

	public static void assertAllFilesExists(File rootDir)
			throws BrokerException {
		if (!txFileExists(rootDir)) {
			throw new BrokerException("assertion failure: " + BASENAME
					+ " file does not exist");
		}
		if (!txAckFileExists(rootDir)) {
			throw new BrokerException("assertion failure: "
					+ TxnAckList.BASENAME + " file does not exist");
		}
	}
    
}


