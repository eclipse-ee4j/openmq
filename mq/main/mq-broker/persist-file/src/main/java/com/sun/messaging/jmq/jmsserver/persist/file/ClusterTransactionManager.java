/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.ClusterTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

public class ClusterTransactionManager extends BaseTransactionManager {

	
	ClusterTransactionManager(TransactionLogManager transactionLogManager) {
		super(transactionLogManager);

	}

	void processStoredTxnOnStartup(BaseTransaction baseTxn) {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processStoredTxnOnStartup " + baseTxn;
			logger.log(Logger.DEBUG, msg);
		}
		TransactionUID tid = baseTxn.getTid();
		int state = baseTxn.getState();
		if (state == TransactionState.COMMITTED
				|| state == TransactionState.ROLLEDBACK) {
			addToCompleteStored(baseTxn);

		} else if (state == TransactionState.PREPARED) {
			addToIncompleteStored(baseTxn);
		}
	}

	TransactionEvent generateEvent(BaseTransaction baseTxn, boolean completion) throws IOException,
			BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " generateEvent " + baseTxn;
			logger.log(Logger.DEBUG, msg);
		}
		ClusterTransactionEvent result = null;

		if(completion)
		{
			result = new ClusterTransaction2PCompleteEvent();
		}
		else if (baseTxn.getState() == TransactionState.PREPARED) {
			result = new ClusterTransaction2PPrepareEvent();
		} else {
			// TO DO FILL IN HERE
			throw new UnsupportedOperationException();
		}
		result.clusterTransaction = (ClusterTransaction) baseTxn;
		return result;
	}

	void processTxn(BaseTransaction baseTxn) throws IOException,
			BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processTxn " + baseTxn;
			logger.log(Logger.DEBUG, msg);
		}

		int state = baseTxn.getState();
		if (state == TransactionState.PREPARED) {
			addToIncompleteUnstored(baseTxn);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	BaseTransaction processTxnCompletion(TransactionUID tid, int state)
			throws IOException, BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processTxnCompletion " + tid;
			logger.log(Logger.DEBUG, msg);
		}
		// We are committing a prepared cluster entry.
		// Check if it is in the prepared transaction store
		// If it is mark it as committed
		// Do NOT remove from prepared store until all participating 
		// brokers in cluster have committed

		boolean removeFromStore = false;
		return processTxnCompletion(tid, state, removeFromStore);
	}

	void updateTransactionBrokerState(TransactionUID tid, int expectedTxnState,
			TransactionBroker txnBkr, boolean sync) throws BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " updateTransactionBrokerState: tx = " + tid
					+ " txnBkr=" + txnBkr;
			Globals.getLogger().log(Logger.DEBUG, msg);
		}
		// store txnBroker state update in txn log
		// if txn is complete then mark this also

		// clustered txnUpdate record
		// TO DO
		//first look in unstored
		ClusterTransaction clusterTxn = null;
		boolean stored = false;

		clusterTxn = (ClusterTransaction) incompleteUnstored.get(tid);
		if (clusterTxn == null) {
			clusterTxn = (ClusterTransaction) incompleteStored.get(tid);
			if (clusterTxn != null)
				stored = true;

		}
		if (clusterTxn != null) {
			boolean allComplete = updateTransactionBrokerState(txnBkr,
					clusterTxn);
			if (Store.getDEBUG()) {												
				Globals.getLogger().log(Logger.DEBUG, getPrefix() + " allComplete:  = " + allComplete);
			}
			if (allComplete) {
				clusterTxn.getTransactionDetails().setComplete(true);
				if (!stored) {
					removeFromIncompleteUnstored(tid);
				} else {
					removeFromIncompleteStored(tid);
					addToCompleteStored(clusterTxn);
					try {
						this.updateStoredCompletion(tid, true);
					} catch (IOException ioe) {
						throw new BrokerException(
								"Could not update completion state of stored cluster transaction "
										+ tid, ioe);
					}

				}

			}
		} else {

			logger.log(Logger.ERROR,
					"Could not find matching cluster transaction for " + tid);
		}

	}

	boolean updateTransactionBrokerState(TransactionBroker txnBkr,
			ClusterTransaction clusterTxn) {
		TransactionBroker[] txnBrokers = clusterTxn.getTransactionBrokers();
		TransactionBroker result = null;

		BrokerAddress b = txnBkr.getBrokerAddress();
		boolean allComplete = true;
		for (int i = 0; i < txnBrokers.length; i++) {
			BrokerAddress ba = txnBrokers[i].getCurrentBrokerAddress();
			if (ba == null)
				continue;
			if (ba.equals(b))
				result = txnBrokers[i];
			else {
				allComplete &= txnBrokers[i].isCompleted();
			}
		}

		result.setCompleted(true);
		return allComplete;
	}

	void replayTransactionEvent(TransactionEvent txnEvent, HashSet dstLoadedSet)
			throws BrokerException, IOException {

		if (Store.getDEBUG()) {
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + " replayTransactionEvent");
		}
		ClusterTransactionEvent clusterTxnEvent = (ClusterTransactionEvent) txnEvent;
		// replay to store on commit
		ClusterTransaction clusterTxn = clusterTxnEvent.clusterTransaction;
		int state = clusterTxn.getState();
		TransactionUID tid = clusterTxn.getTid();
		if (clusterTxnEvent.getSubType() == ClusterTransactionEvent.Type2PPrepareEvent) {
			// 2-phase prepare
			// check if it is stored 
			// (this should only be the case if a failure occurred between saving 
			// in prepared txn store and resetting the transaction log
			if (incompleteStored.containsKey(tid)) {
				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " found matching txn in prepared store on replay "
							+ clusterTxn;
					Globals.getLogger().log(Logger.DEBUG, msg);
				}
			} else {
				addToIncompleteUnstored(clusterTxn);
			}

		} else if (clusterTxnEvent.getSubType() == ClusterTransactionEvent.Type2PCompleteEvent) {
			// we are completing a transaction
			// the transaction could be 
			// a) unstored (prepare replayed earlier)
			// b) stored incomplete (prepare occurred before last checkpoint, 
			//    completion not written to prepared store yet)
			//    This should therefore be the last entry in log.
			// c) stored complete (prepare occurred before last checkpoint,
			//    and failure occurred after completion stored in prepared store
			BaseTransaction existingWork = null;
			if (incompleteUnstored.containsKey(tid)) {
				// a) unstored (prepare replayed earlier)
				if (state == TransactionState.ROLLEDBACK) {
					existingWork = removeFromIncompleteUnstored(tid);
				} else if (state == TransactionState.COMMITTED) {

					existingWork = incompleteUnstored.get(tid);
					existingWork.getTransactionDetails().setState(state);
					existingWork.getTransactionState().setState(state);
				}
				
			} else if (incompleteStored.containsKey(tid)) {
				// b) stored incomplete (prepare occurred before last checkpoint, 
				//    completion not written to prepared store yet)

				existingWork = removeFromIncompleteStored(tid);

				updateStoredState(tid, state);

				addToCompleteStored(existingWork);
			} else if (completeStored.containsKey(tid)) {
				// c) stored complete (prepare occurred before last checkpoint,
				//    and failure occurred after completion stored in prepared store
				existingWork = completeStored.get(tid);
			}
			if (existingWork != null) {
				if (state == TransactionState.COMMITTED) {
					transactionLogManager.transactionLogReplayer.replayTransactionWork(existingWork
							.getTransactionWork(), tid, dstLoadedSet);
				}
			} else {
				logger.log(Logger.ERROR,
						"Could not find prepared work for completing two-phase transaction "
								+ clusterTxn.getTid());
			}
		}

	}

	String getPrefix() {
		return "ClusterTransactionManager: " + Thread.currentThread().getName();
	}

}
