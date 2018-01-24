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
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.RemoteTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

public class RemoteTransactionManager extends BaseTransactionManager {

	RemoteTransactionManager(TransactionLogManager transactionLogManager) {
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
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + " generateEvent ");
		}
		RemoteTransactionEvent result = null;

		if(completion)
		{
			result = new RemoteTransaction2PCompleteEvent();
		}
		else if (baseTxn.getState() == TransactionState.PREPARED) {
			result = new RemoteTransaction2PPrepareEvent();
		} else {
			// TO DO FILL IN HERE
			throw new UnsupportedOperationException();
		}
		result.remoteTransaction = (RemoteTransaction) baseTxn;
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
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + " processTxnCompletion " + tid);
		}
		// We are committing a prepared remote transaction.
		// Check if it is in the prepared transaction store
		// If it is, mark it as committed and remove from prepared store 

		boolean removeFromStore = true;
		return processTxnCompletion(tid, state, removeFromStore);
	}

	void replayTransactionEvent(TransactionEvent txnEvent, HashSet dstLoadedSet)
			throws BrokerException, IOException {

		if (Store.getDEBUG()) {
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + " replayTransactionEvent");
		}
		RemoteTransactionEvent remoteTxnEvent = (RemoteTransactionEvent) txnEvent;
		// replay to store on commit
		RemoteTransaction remoteTxn = remoteTxnEvent.remoteTransaction;
		int state = remoteTxn.getState();
		TransactionUID tid = remoteTxn.getTid();
		if (remoteTxnEvent.getSubType() == RemoteTransactionEvent.Type2PPrepareEvent) {
			// 2-phase prepare
			// check if it is stored 
			// (this should only be the case if a failure occurred between saving 
			// in prepared txn store and resetting the transaction log
			if (incompleteStored.containsKey(tid)) {
				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " found matching txn in prepared store on replay "
							+ remoteTxn;
					Globals.getLogger().log(Logger.DEBUG, msg);
				}
			} else {
				addToIncompleteUnstored(remoteTxn);
			}

		} else if (remoteTxnEvent.getSubType() == RemoteTransactionEvent.Type2PCompleteEvent) {
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
				existingWork = removeFromIncompleteUnstored(tid);
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
				RemoteTransaction remoteTransaction = (RemoteTransaction)existingWork;
				if (state == TransactionState.COMMITTED) {
					
					TransactionAcknowledgement[] txnAcks = remoteTransaction.getTxnAcks();
					DestinationUID[] destIds = remoteTransaction.getDestIds();
					transactionLogManager.transactionLogReplayer.replayRemoteAcks(txnAcks, destIds, tid, dstLoadedSet);
				}
			} else {
				logger.log(Logger.ERROR,
						"Could not find prepared work for completing two-phase transaction "
								+ remoteTxn.getTid());
			}
		}

	}

	String getPrefix() {
		return "RemoteTransactionManager: " + Thread.currentThread().getName();
	}

}
