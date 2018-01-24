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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.WaitTimeoutException;
import com.sun.messaging.jmq.util.log.Logger;

public abstract class BaseTransactionManager {

	public static final Logger logger = Globals.getLogger();

	PreparedTxnStore preparedTxnStore;
	TransactionLogManager transactionLogManager;

	Set<TransactionUID> playingToMessageStore = Collections.synchronizedSet(new HashSet<TransactionUID>());
	Map<TransactionUID, BaseTransaction> completeStored = new Hashtable<TransactionUID, BaseTransaction>();
	Map<TransactionUID, BaseTransaction> incompleteUnstored = new Hashtable<TransactionUID, BaseTransaction>();
	Map<TransactionUID, BaseTransaction> incompleteStored = new Hashtable<TransactionUID, BaseTransaction>();

	BaseTransactionManager(TransactionLogManager transactionLogManager) {
		this.transactionLogManager = transactionLogManager;
		preparedTxnStore = transactionLogManager.preparedTxnStore;
	}

	abstract String getPrefix();

	public List<BaseTransaction> getAllIncompleteTransactions() {
		List<BaseTransaction> result = new ArrayList<BaseTransaction>();
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " getAllIncompleteTransactions  "
					+ " num incompleteUnstored = " + incompleteUnstored.size()
					+ " num incompleteStored = " + incompleteStored.size();
			logger.log(Logger.DEBUG, msg);
		}
		result.addAll(incompleteUnstored.values());
		result.addAll(incompleteStored.values());
		return result;
	}
	
	public HashMap getAllTransactionsMap() {
		List<BaseTransaction> txns = getAllIncompleteTransactions();
		HashMap map = new HashMap(txns.size());
		Iterator<BaseTransaction> itr = txns.iterator();
		while (itr.hasNext()) {
			BaseTransaction txn = itr.next();
			TransactionState txnState = new TransactionState(txn
					.getTransactionState());
			map.put(txn.getTid(), txnState);
		}
		return map;
	}

	public void addToCompleteStored(BaseTransaction baseTxn) {
		completeStored.put(baseTxn.getTid(), baseTxn);
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " adding  " + baseTxn.getTid()
					+ "  to completeStored. Total = " + completeStored.size();
			logger.log(Logger.DEBUG, msg);
		}
	}

	public void removeFromCompleteStored(TransactionUID tid) {
		completeStored.remove(tid);
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " removing " + tid
					+ " from completeStored. Total = " + completeStored.size();
			logger.log(Logger.DEBUG, msg);
		}
	}

	void addToIncompleteStored(BaseTransaction baseTxn) {
		incompleteStored.put(baseTxn.getTid(), baseTxn);
		if (Store.getDEBUG()) {
			String msg = getPrefix()

			+ " adding  " + baseTxn.getTid()
					+ "  to incompleteStored. Total = "
					+ incompleteStored.size();
			logger.log(Logger.DEBUG, msg);
		}
	}

	BaseTransaction removeFromIncompleteStored(TransactionUID tid) {
		BaseTransaction result = incompleteStored.remove(tid);
		if (Store.getDEBUG()) {
			String msg = getPrefix()

			+ " removing  " + tid + " from incompleteStored. Total = "
					+ incompleteStored.size();
			logger.log(Logger.DEBUG, msg);
		}
		return result;
	}

	void addToIncompleteUnstored(BaseTransaction baseTxn) {
		incompleteUnstored.put(baseTxn.getTid(), baseTxn);
		if (Store.getDEBUG()) {
			String msg = getPrefix()

			+ " adding  " + baseTxn.getTid()
					+ "  to incompleteUnstored. Total = "
					+ incompleteUnstored.size();
			logger.log(Logger.DEBUG, msg);
		}
	}

	BaseTransaction removeFromIncompleteUnstored(TransactionUID tid) {
		BaseTransaction result = incompleteUnstored.remove(tid);
		if (Store.getDEBUG()) {
			String msg = getPrefix()

			+ " removing  " + tid + " from incompleteUnstored. Total = "
					+ incompleteUnstored.size();
			logger.log(Logger.DEBUG, msg);
		}
		return result;
	}

	abstract void processStoredTxnOnStartup(BaseTransaction baseTxn);

	public void waitForPlayingToMessageStoreCompletion(boolean nowait) 
        throws WaitTimeoutException {
		synchronized (playingToMessageStore) {
			if (Store.getDEBUG()) {
				String msg = getPrefix() + " num playingToMessageStore ="
						+ playingToMessageStore.size();
				logger.log(Logger.DEBUG, msg);
			}
			try {
				while (playingToMessageStore.size() > 0) {
					if (Store.getDEBUG()) {
						String msg = getPrefix() + " waiting for "
								+ playingToMessageStore.size()
								+ " playingToMessageStore";
						logger.log(Logger.DEBUG, msg);
					}
                                        if (nowait) {
                                            throw new WaitTimeoutException(this.getClass().getSimpleName());
                                        }
					playingToMessageStore.wait(1000);
				}
                                transactionLogManager.notifyPlayToStoreCompletion();

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * this method is called by transactionHandler to notify that 
	 * the committed transaction has been written to the message store.
	 * 
	 * Keeping track of committed transactions and when they have been written to the message store 
	 * allows the TransactionlogManager to know when it is safe to do a checkpoint. 
	 */
	public void playingToMessageStoreComplete(TransactionUID tid) {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " playingToMessageStoreComplete " + tid;
			logger.log(Logger.DEBUG, msg);
		}
		synchronized (playingToMessageStore) {
			Object found = playingToMessageStore.remove(tid);
			if (found == null) {
				String msg = getPrefix()
						+ " playingToMessageStoreComplete(): could not find "
						+ tid;
				logger.log(Logger.WARNING, msg);
			}
			playingToMessageStore.notifyAll();
		}
		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " remove transaction from  playingToMessageStore."
					+ " tid=" + tid + " size = " + playingToMessageStore.size();
			logger.log(Logger.DEBUG, msg);
		}
	}

	// on checkpoint, we need to store any newly logged prepared transactions to the preparedTxnStore
	// as the transaction log is about to be reset
	void writePreparedTransactionsToPreparedTxnStoreOnCheckpoint()
			throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " writePreparedTransactionsToPreparedTxnStoreOnCheckpoint"
					+ " num incompleteUnstored=" + incompleteUnstored.size();
			logger.log(Logger.DEBUG, msg);
		}

		ArrayList<TransactionUID> incmps = null;
		synchronized(incompleteUnstored) {
			incmps = new ArrayList<TransactionUID>(incompleteUnstored.keySet());
		}

		TransactionUID tid = null;
		BaseTransaction baseTxn = null;
		Iterator<TransactionUID> iter = incmps.iterator();
		while (iter.hasNext()) {
			tid = iter.next();
			if (!preparedTxnStore.containsTransaction(tid)) {
				baseTxn = (BaseTransaction)incompleteUnstored.get(tid);
				if (baseTxn == null) {
					continue;
				}
				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " transaction storing preparedTransaction "
							+ baseTxn;
					logger.log(Logger.DEBUG, msg);
				}
				try {					
					preparedTxnStore.storeTransaction(baseTxn, true);
					addToIncompleteStored(baseTxn);
				} catch (IOException ioe) {
					throw new BrokerException(
							"failed to store transaction in preparedTxnStore "
									+ baseTxn, ioe);
				}
			} else {

				
					String msg = getPrefix()
							+ " transaction already exists in preparedTxnStore "
							+ tid+"["+incompleteUnstored.get(tid)+"]";
					logger.log(Logger.INFO, msg);
				
			}
		}
		incompleteUnstored.clear();
	}

	void removeCompleteTransactionsAfterCheckpoint() throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " removeCompleteTransactionsAfterCheckpoint"
					+ "num completeStored=" + completeStored.size();
			logger.log(Logger.DEBUG, msg);
		}

		ArrayList cmptids = null;
		synchronized(completeStored) {
			cmptids = new ArrayList(completeStored.keySet());
		}
		Iterator<TransactionUID> iter = cmptids.iterator();
		while (iter.hasNext()) {
			TransactionUID tid = iter.next();
			if (preparedTxnStore.containsTransaction(tid)) {
				preparedTxnStore.removeTransaction(tid, true);

				if (Store.getDEBUG()) {
					String msg = getPrefix() + " removed transaction " + tid;
					logger.log(Logger.DEBUG, msg);
				}
			} else {

				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " Could not find transaction in preparedTxnStore "
							+ tid;
					logger.log(Logger.DEBUG, msg);
				}
			}
		}
		completeStored.clear();

	}
	
	void rollbackAllTransactions() {
		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " rollbackAllTransactions"
					+ " num incompleteStored=" + incompleteStored.size()
					+ " num incompleteUnstored=" + incompleteUnstored.size();
			logger.log(Logger.DEBUG, msg);
		}
		Collection<BaseTransaction> storedTxns = incompleteStored.values();
		Collection<BaseTransaction> storedTxnsCopy = new ArrayList<BaseTransaction>(
				storedTxns);
		rollbackTransactions(storedTxnsCopy);
		
		Collection<BaseTransaction> unstoredTxns = incompleteUnstored.values();
		Collection<BaseTransaction> unstoredTxnsCopy = new ArrayList<BaseTransaction>(
				unstoredTxns);
		rollbackTransactions(unstoredTxnsCopy);

	}
	
	void rollbackTransactions(Collection<BaseTransaction> txns) {
		Iterator<BaseTransaction> iter = txns.iterator();
		while (iter.hasNext()) {
			BaseTransaction txn = iter.next();
			try {
				processTxnCompletion(txn.getTid(), TransactionState.ROLLEDBACK,
						true);
			} catch (IOException ioe) {
				logger.log(Logger.ERROR, "could not rollback " + txn, ioe);

			} catch (BrokerException be) {
				logger.log(Logger.ERROR, "could not rollback " + txn, be);
			}

		}

	}

	abstract void processTxn(BaseTransaction baseTxn) throws IOException,
			BrokerException;

	abstract TransactionEvent generateEvent(BaseTransaction baseTxn, boolean completion)
			throws IOException, BrokerException;

	abstract BaseTransaction processTxnCompletion(TransactionUID tid, int state)
			throws IOException, BrokerException;

	BaseTransaction processTxnCompletion(TransactionUID tid, int state,
			boolean fullyComplete) throws IOException, BrokerException {

		// We are committing a prepared entry.
		// Check if it is in the prepared transaction store
		// If it is mark it as committed, so that it can be cleaned up after next checkpoint 
		if(state==TransactionState.COMMITTED)
		{
			this.playingToMessageStore.add(tid);
			if (Store.getDEBUG()) {
				String msg = getPrefix()
						+ " add transaction to  playingToMessageStore."
						+ " tid=" + tid + " size = " + playingToMessageStore.size();
				logger.log(Logger.DEBUG, msg);
			}
		}
		
		BaseTransaction existingTxn = null;
		boolean stored = false;
		existingTxn = incompleteUnstored.get(tid);
		if (existingTxn == null) {
			existingTxn = incompleteStored.get(tid);
			if (existingTxn != null) {
				stored = true;
			} else {
				String msg = getPrefix() + " processTxnCompletion: Could not find txn for " + tid;
				logger.log(Logger.WARNING, msg);
				throw new BrokerException(msg);
			}
		}
		existingTxn.getTransactionDetails().setState(state);

		if (stored) {
			// update the stored transaction from prepared to committed/rolledback state			
			//update state on file by writing at fixed offset

			updateStoredState(tid, state);

			if (fullyComplete) {
				incompleteStored.remove(tid);
				completeStored.put(tid, existingTxn);
			}

		} else {
			if (fullyComplete) {
				removeFromIncompleteUnstored(tid);
			}
		}
		messageListLogged(existingTxn);
		

		return existingTxn;

	}
	
	protected void messageListLogged(BaseTransaction baseTxn) {
		TransactionWork txnWork = baseTxn.getTransactionWork();
		if (txnWork != null) {
			List<TransactionWorkMessage> sentMessages = txnWork
					.getSentMessages();
			if (sentMessages != null) {
				transactionLogManager.loggedMessageHelper
						.messageListLogged(sentMessages);
			}
		}
	}
	

	void updateStoredState(TransactionUID tid, int state) throws IOException,
			BrokerException {
		preparedTxnStore.updateTransactionState(tid, state, true);

		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " updated transaction state of stored prepared transaction to "
					+ TransactionState.toString(state);
			logger.log(Logger.DEBUG, msg);
		}
	}

	void updateStoredCompletion(TransactionUID tid, boolean complete)
			throws IOException, BrokerException {
		preparedTxnStore.updateTransactionCompletion(tid, complete, true);

		if (Store.getDEBUG()) {
			String msg = getPrefix()
					+ " updated completion status of stored transaction to "
					+ complete;
			logger.log(Logger.DEBUG, msg);
		}
	}

	abstract void replayTransactionEvent(TransactionEvent localTxnEvent,
			HashSet dstLoadedSet) throws BrokerException, IOException;

}
