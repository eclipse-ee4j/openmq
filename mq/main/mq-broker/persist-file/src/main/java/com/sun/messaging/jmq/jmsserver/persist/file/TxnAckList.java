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
 * @(#)TxnAckList.java	1.31 08/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.PHashMapMMF;
import com.sun.messaging.jmq.io.disk.VRFileWarning;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;

import java.io.*;
import java.util.*;

/**
 * Keep track of acknowledgements for transactions using PHashMap.
 */
class TxnAckList {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    BrokerConfig config = Globals.getConfig();

    static final String BASENAME = "txnack"; // basename of data file

    // maps tid -> HashSet of TransactionAcknowledgement
    private PHashMap tidMap = null;

    private boolean useMemoryMappedFile = true;

    private HashMap emptyHashMap = new HashMap();
    private TransactionAcknowledgement[] emptyAckArray =
        new TransactionAcknowledgement[0];

    private File backingFile = null;

    private LoadException loadException = null;

    // when instantiated, all data are loaded
    TxnAckList(FileStore p, File topDir, boolean clear) throws BrokerException {

	SizeString filesize = config.getSizeProperty(TidList.TXN_FILE_SIZE_PROP,
					TidList.DEFAULT_TXN_FILE_SIZE);

	backingFile = new File(topDir, BASENAME);

	try {
	    // safe=false; caller controls data synchronization
            useMemoryMappedFile = config.getBooleanProperty(
                TidList.TXN_USE_MEMORY_MAPPED_FILE_PROP,
                TidList.DEFAULT_TXN_USE_MEMORY_MAPPED_FILE);

            if (useMemoryMappedFile) {
	        tidMap = new PHashMapMMF(backingFile, filesize.getBytes(), 1024, false, 
                             clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            } else {
                tidMap = new PHashMap(backingFile, filesize.getBytes(), 1024, false, 
                             clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            }
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TXNACK_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TXNACK_FAILED), e);
	}

	try {
	    tidMap.load(p);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TXNACK_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TXNACK_FAILED), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_LOAD_TXNACK_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_TXNACK_FAILED), e);
	} catch (PHashMapLoadException le) {
	    while (le != null) {
		logger.log(Logger.WARNING, br.X_FAILED_TO_LOAD_A_TXNACK, le);

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
				"TxnAckList initialized with clear option");
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "TxnAckList: loaded acks for "+
				tidMap.size() + " transactions");
	}
    }

    LoadException getLoadException() {
	return loadException;
    }

    // when instantiated, old data are upgraded
    TxnAckList(FileStore p, File topDir, File oldTop, boolean deleteold)
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
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    olddata.load(p);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
	    		backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
	    		backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING,
			br.X_FAILED_TO_LOAD_A_TXNACK_FROM_OLDSTORE, le);

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
	    // pass in safe=false; let caller decide when to sync
	    // safe=false; reset=false
            if (config.getBooleanProperty(TidList.TXN_USE_MEMORY_MAPPED_FILE_PROP,
                TidList.DEFAULT_TXN_USE_MEMORY_MAPPED_FILE)) {
	        tidMap = new PHashMapMMF(backingFile, oldFile.length(), 1024, false, 
                             false, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            } else {
                tidMap = new PHashMap(backingFile, oldFile.length(), 1024, false, 
                             false, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
            }
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    tidMap.load(p);
	} catch (IOException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
	    		backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
	    		backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_TXNACK_FAILED, oldFile,
	    		backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_TXNACK_FAILED,
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
	    tidMap.put(key, value);
	}
	olddata.close();

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "TxnAckList: upgraded acks for "+
					tidMap.size() + " txns");
	}

	// if upgradeinplace, remove oldfile
	if (deleteold) {
	    if (!oldFile.delete()) {
		logger.log(logger.ERROR, br.I_DELETE_FILE_FAILED, oldFile);
	    }
	}

    }

    /**
     * Store the acknowledgement for the specified transaction.
     *
     * @param tid	the transaction id with which the acknowledgment is
     *			to be stored
     * @param ack	the acknowledgement to be stored
     * @exception BrokerException if the transaction id is not found in the
     *			store, if the acknowledgement already exists, or
     *			if it failed to persist the data
     */
    void storeAck(TransactionUID tid, TransactionAcknowledgement ack,
	boolean sync) throws BrokerException {

        try {
            boolean putIfAbsent = false;
            HashSet acks = (HashSet)tidMap.get(tid);

            if (acks == null) {
                putIfAbsent = true;
                acks = new HashSet();
            } else {
                if (acks.contains(ack)) {
                    logger.log(logger.ERROR,
                        br.E_ACK_EXISTS_IN_STORE, ack, tid);
                    throw new BrokerException(
                        br.getString(br.E_ACK_EXISTS_IN_STORE, ack, tid));
                }
            }
            acks.add(ack);

            Object oldValue = tidMap.put(tid, acks, putIfAbsent);
            if (putIfAbsent && (oldValue != null)) {
                // If we're unable to update the map, then another ack
                // has been added before we we get a chance; so try again!
                acks = (HashSet)tidMap.get(tid);
                acks.add(ack);
                tidMap.put(tid, acks);
            }

            if (sync) {
                sync(tid);
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_TXNACK_FAILED,
                ack.toString(), tid.toString());
            throw new BrokerException(
                br.getString(br.X_PERSIST_TXNACK_FAILED,
                ack.toString(), tid.toString()), e);
	}
    }

    /**
     * Store the acknowledgements for the specified transaction.
     *
     * @param tid	the transaction id with which the acknowledgments are
     *			to be stored
     * @param txnAcks	the acknowledgements to be stored
     * @exception BrokerException if the transaction id is not found in the
     *			store, or if it failed to persist the data
     */
    void storeAcks(TransactionUID tid, TransactionAcknowledgement[] txnAcks,
	boolean sync) throws BrokerException {

        List ackList = Arrays.asList(txnAcks); // Convert array to a List

        try {
            boolean putIfAbsent = false;
            HashSet acks = (HashSet)tidMap.get(tid);

            if (acks == null) {
                putIfAbsent = true;
                acks = new HashSet(ackList.size());
            }
            acks.addAll(ackList);

            Object oldValue = tidMap.put(tid, acks, putIfAbsent);
            if (putIfAbsent && (oldValue != null)) {
                // If we're unable to update the map, then another ack
                // has been added before we we get a chance; so try again!
                acks = (HashSet)tidMap.get(tid);
                acks.addAll(ackList);
                tidMap.put(tid, acks);
            }

            if (sync) {
                sync(tid);
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_TXNACK_FAILED,
                ackList.toString(), tid.toString());
            throw new BrokerException(
                br.getString(br.X_PERSIST_TXNACK_FAILED,
                ackList.toString(), tid.toString()), e);
        }
    }

    /**
     * Return all acknowledgement list in the form of a HashMap
     * Not synchronized; assumed to be called once at broker startup.
     */
    HashMap getAllAcks() {

	if (tidMap.size() == 0) {
	    return emptyHashMap;
	}

	HashMap allacks = new HashMap(tidMap.size());

	Set entries = tidMap.entrySet();
	Iterator itor = entries.iterator();
	while (itor.hasNext()) {
	    Map.Entry entry = (Map.Entry)itor.next();

	    // get acks into an array of TransactionAcknowledgement
	    HashSet set = (HashSet)entry.getValue();
	    TransactionAcknowledgement[] acks = (TransactionAcknowledgement[])
		set.toArray(emptyAckArray);

	    allacks.put(entry.getKey(), acks);
	}

	return allacks;
    }

    /**
     * Returns a array of all TransactionUIDs in the TxnAckList.
     * We don't just return a set view because of the possible
     * fragility of PHashMap and iterators.
     */
    public TransactionUID[] getAllTids() {
        TransactionUID[] tids = null;
        Set s = tidMap.keySet();
        tids = new TransactionUID[s.size()];
        int i = 0;
        for (Iterator itr = s.iterator(); itr.hasNext(); ) {
            tids[i] = (TransactionUID)itr.next();
        }
        return tids;
    }

    /**
     * Return acks for the specified transaction.
     * @param tid	id of the transaction whose acknowledgements
     *			are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    TransactionAcknowledgement[] getAcks(TransactionUID tid)
	throws BrokerException {

        HashSet acks = (HashSet)tidMap.get(tid);

        if (acks != null) {
            return (TransactionAcknowledgement[])
                    acks.toArray(emptyAckArray);
        } else {
            return emptyAckArray;
        }
    }

    /**
     * remove all acks for the specified transaction id
     */
    void removeAcks(TransactionUID tid, boolean sync) throws BrokerException {

        try {
            HashSet acks = (HashSet)tidMap.remove(tid);

            if (sync) {
                sync(tid);
            }

            if (acks != null) {
                acks.clear();
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_REMOVE_TXNACK_FAILED, tid.toString());
            throw new BrokerException(
                br.getString(br.X_REMOVE_TXNACK_FAILED, tid.toString()), e);
        }
    }

    /**
     * Clear all acks for all transactions.
     */
    // clear the store; when this method returns, the store has a state
    // that is the same as an empty store (same as when TxnAckList is
    // instantiated with the clear argument set to true
    void clearAll(boolean sync) {

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH, "TxnAckList.clearAll() called");
	}

        try {
            tidMap.clear();

            if (sync) {
                sync(null);
            }
        } catch (Exception e) {
            // at least log it
            logger.log(logger.ERROR, br.getString(
                br.X_CLEAR_TXNACK_FAILED, backingFile), e);
        }
    }

    void close(boolean cleanup) {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
		"TxnAckList: closing, "+tidMap.size()+" transactions has acks");
	}

	tidMap.close();
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("Txn acks", String.valueOf(tidMap.size()));
	return t;
    }

    void printInfo(PrintStream out) {
	out.println("\nTransaction acknowledgements");
	out.println("----------------------------");

	out.println("backing file: "+ backingFile);
	out.println("Number of transactions containing acknowledgements: "+
			tidMap.size());
    }

    /**
     * @return the total number of transaction acknowledgements in the store
     */
    public int getNumberOfTxnAcks() {
	int size = 0;
	Iterator itr = tidMap.entrySet().iterator();
	while (itr.hasNext()) {
	    Map.Entry entry = (Map.Entry)itr.next();
	    HashSet acks = (HashSet)entry.getValue();
	    size += acks.size();
	}
	return size;
    }

    void sync(TransactionUID tid) throws BrokerException {
	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "TxnAckList sync() "+tid;
			logger.log(Logger.DEBUG,msg);
		}
	    tidMap.force(tid);
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize data to disk for file: " + backingFile, e);
	}
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
    
    public void deleteAndBackupAllFiles(File rootDir) throws IOException {
		FileUtil.copyFile(backingFile, new File(rootDir, this.BASENAME
				+ ".backup"));
		boolean deleted = backingFile.delete();
		if (!deleted) {
			logger.log(logger.ERROR, "Could not delete " + backingFile
					+ " . Will delete on exit");

			backingFile.deleteOnExit();
		}
	}
}


