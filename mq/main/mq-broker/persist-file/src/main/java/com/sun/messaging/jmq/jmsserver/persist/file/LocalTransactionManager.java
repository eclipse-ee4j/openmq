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
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.LocalTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

public class LocalTransactionManager extends BaseTransactionManager {

	LocalTransactionManager(TransactionLogManager transactionLogManager) {
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

	void replayTransactionEvent(TransactionEvent txnEvent, HashSet dstLoadedSet)
			throws BrokerException, IOException {

		if (Store.getDEBUG()) {
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + " replayTransactionEvent");
		}
		LocalTransactionEvent localTxnEvent = (LocalTransactionEvent) txnEvent;
		// replay to store on commit
		LocalTransaction localTxn = localTxnEvent.localTransaction;
		int state = localTxn.getState();
		TransactionUID tid = localTxn.getTid();
		if (localTxnEvent.getSubType() == LocalTransactionEvent.Type1PCommitEvent) {
			//one phase commit
			//Just replay it now

			transactionLogManager.transactionLogReplayer.replayTransactionWork(localTxn
					.getTransactionWork(), tid, dstLoadedSet);
		} else if (localTxnEvent.getSubType() == LocalTransactionEvent.Type2PPrepareEvent) {
			// 2-phase prepare
			// check if it is stored 
			// (this should only be the case if a failure occurred between saving 
			// in prepared txn store and resetting the transaction log
			if (incompleteStored.containsKey(tid)) {
				if (Store.getDEBUG()) {
					String msg = getPrefix()
							+ " found matching txn in prepared store on replay "
							+ localTxn;
					Globals.getLogger().log(Logger.DEBUG, msg);
				}
			} else {
				addToIncompleteUnstored(localTxn);
			}

		} else if (localTxnEvent.getSubType() == LocalTransactionEvent.Type2PCompleteEvent) {
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
				if (state == TransactionState.COMMITTED) {
					transactionLogManager.transactionLogReplayer.replayTransactionWork(existingWork
							.getTransactionWork(), tid, dstLoadedSet);
				}
			} else {
				logger.log(Logger.ERROR,
						"Could not find prepared work for completing two-phase transaction "
								+ localTxn.getTid());
			}
		}

	}

	

	TransactionEvent generateEvent(BaseTransaction baseTxn, boolean completion) throws IOException,
			BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " generateEvent " + baseTxn;
			logger.log(Logger.DEBUG, msg);
		}
		LocalTransactionEvent result = null;

		if(completion)
		{
			result = new LocalTransaction2PCompleteEvent();
		}
		else if (baseTxn.getState() == TransactionState.COMMITTED) {
			result = new LocalTransaction1PCommitEvent();			

		} else if (baseTxn.getState() == TransactionState.PREPARED) {
			result = new LocalTransaction2PPrepareEvent();

		}
		result.localTransaction = (LocalTransaction) baseTxn;
		return result;
	}

	void processTxn(BaseTransaction baseTxn) throws IOException,
			BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processTxn " + baseTxn;
			logger.log(Logger.DEBUG, msg);
		}

		int state = baseTxn.getState();
		// if one phase commit
		if (state == TransactionState.COMMITTED) {
			playingToMessageStore.add(baseTxn.getTid());
			// check if any messages are being logged.
			// If so, will need to notify LoggedMessageHelper
			messageListLogged(baseTxn);
		}

		else if (state == TransactionState.PREPARED) {
			addToIncompleteUnstored(baseTxn);
		}
	}

	BaseTransaction processTxnCompletion(TransactionUID tid, int state)
			throws IOException, BrokerException {
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " processTxnCompletion " + tid;
			logger.log(Logger.DEBUG, msg);
		}
		// We are committing a prepared entry.
		// Check if it is in the prepared transaction store
		// If it is mark it as committed, so that it can be cleaned up after next checkpoint 

		boolean removeFromStore = true;		
		return processTxnCompletion(tid, state, removeFromStore);

	}

	String getPrefix() {
		return "LocalTransactionManager: " + Thread.currentThread().getName();
	}

}
