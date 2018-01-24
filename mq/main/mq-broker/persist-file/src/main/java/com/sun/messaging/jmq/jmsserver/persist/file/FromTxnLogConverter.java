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
import java.util.Iterator;
import java.util.List;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.ClusterTransaction;
import com.sun.messaging.jmq.jmsserver.data.RemoteTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessageAck;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.log.Logger;

public class FromTxnLogConverter {

	Store store;
	FileStore fileStore;
	Logger logger = Globals.getLogger();
	private static boolean DEBUG = false;
	static {
		if (Globals.getLogger().getLevel() <= Logger.DEBUG)
			DEBUG = true;
	}

	public FromTxnLogConverter(Store store) {
		this.store = store;
	}

	public void convertFromTxnLogFormat() {

		// iterate through all transactions

		if (DEBUG) {
			logger.log(Logger.DEBUG, getPrefix() + " convertFromTxnLogFormat");
		}

		fileStore = (FileStore) store;
		TransactionLogManager txnLogManager = fileStore.getTxnLogManager();
		convert(txnLogManager.getLocalTransactionManager(),
				new LocalTxnConverter());
		convert(txnLogManager.getClusterTransactionManager(),
				new ClusterTxnConverter());
		convert(txnLogManager.getRemoteTransactionManager(),
				new RemoteTxnConverter());

	}

	void convert(BaseTransactionManager baseTxnManager, TxnConverter converter) {
		if (DEBUG) {
			logger.log(Logger.DEBUG, getPrefix() + " converting trxns from TxnManager");
		}
		List<BaseTransaction> list = baseTxnManager
				.getAllIncompleteTransactions();
		Iterator<BaseTransaction> iter = list.iterator();
		while (iter.hasNext()) {
			BaseTransaction baseTxn = iter.next();

			try {
				converter.convert(baseTxn);
			} catch (BrokerException be) {
				logger.logStack(Logger.ERROR, be.getMessage(), be);
			} catch (IOException ie) {
				logger.logStack(Logger.ERROR, ie.getMessage(), ie);
			}
		}
	}

	class TxnConverter {

		void convert(BaseTransaction baseTxn) throws BrokerException,
				IOException {
			if (DEBUG) {
				logger.log(Logger.DEBUG, getPrefix() + " convert txn "+baseTxn);
			}

			TransactionUID id = baseTxn.getTid();

			TransactionState ts = baseTxn.getTransactionState();

			ts.setState(TransactionState.STARTED);

			// TransactionState
			fileStore.storeTransaction(id, ts, true);

			TransactionWork txnWork = baseTxn.getTransactionWork();
			if (txnWork != null)
				convertWork(txnWork, ts, id);

			ts.setState(TransactionState.PREPARED);
			fileStore.updateTransactionState(id, ts, true);

		}

		void convertWork(TransactionWork work, TransactionState ts,
				TransactionUID txid) throws BrokerException, IOException {
			if (DEBUG) {
				logger.log(Logger.DEBUG, getPrefix() + " convertWork ");
			}
                        DestinationList DL = Globals.getDestinationList();
			List<TransactionWorkMessage> sentMsgs = work.getSentMessages();
			Iterator<TransactionWorkMessage> sentIter = sentMsgs.iterator();
			while (sentIter.hasNext()) {
				TransactionWorkMessage msg = sentIter.next();
				if (DEBUG) {
					logger.log(Logger.DEBUG, getPrefix() + " convert sent msg "+msg);
				}
				DestinationUID duid = msg.getDestUID();
				int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE
						: DestType.DEST_TYPE_TOPIC);

				// make sure destination exists
				// ( it may have been removed on load if it just contained
				// messages in a transaction)
				DL.getDestination(fileStore, duid.getName(), type, true, true);

				Packet message = msg.getMessage();
				ConsumerUID[] iids = msg.getStoredInterests();
				//if (iids != null) 
				if(false){
					int[] states = new int[iids.length];
					for (int i = 0; i < iids.length; i++) {
						states[i] = 0;
					}

					fileStore.storeMessage(duid, message, iids, states, true);
				} else {
					fileStore.storeMessage(duid, message, true);
				}
			}

			List<TransactionWorkMessageAck> consumedMsgs = work
					.getMessageAcknowledgments();
			Iterator<TransactionWorkMessageAck> consumedIter = consumedMsgs
					.iterator();
			while (consumedIter.hasNext()) {
				TransactionWorkMessageAck msgAck = consumedIter.next();
				if (DEBUG) {
					logger.log(Logger.DEBUG, getPrefix() + " convert consumed msg "+msgAck);
				}
				DestinationUID duid = msgAck.getDestUID();
				int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE
						: DestType.DEST_TYPE_TOPIC);
				Destination[] ds = DL.getDestination(fileStore, duid.getName(),
						type, true, true);
                                Destination dest = ds[0];
				dest.load();
				SysMessageID mid = msgAck.getSysMessageID();
				ConsumerUID cid = msgAck.getConsumerID();
				boolean sync = true;
				boolean isLastAck = false;
				TransactionAcknowledgement txAck = new TransactionAcknowledgement(mid, cid, cid);
				 fileStore.storeTransactionAck(txid, txAck, false);
//				fileStore.updateInterestState(duid, mid, cid, Store.INTEREST_STATE_ACKNOWLEDGED,
//						sync, txid, isLastAck);
			}

		}
	}

	class LocalTxnConverter extends TxnConverter {

	}

	class ClusterTxnConverter extends TxnConverter {

		void convert(BaseTransaction baseTxn) throws BrokerException,
				IOException {

			ClusterTransaction clusterTxn = (ClusterTransaction) baseTxn;
			TransactionUID id = baseTxn.getTid();

			TransactionState ts = baseTxn.getTransactionState();

			int finalState = ts.getState();

			ts.setState(TransactionState.STARTED);

			// TransactionState
			fileStore.storeTransaction(id, ts, true);

			TransactionWork txnWork = baseTxn.getTransactionWork();
			if (txnWork != null)
				convertWork(txnWork, ts, id);

			((PartitionedStore)store).updateClusterTransaction(id, clusterTxn
					.getTransactionBrokers(), Destination.PERSIST_SYNC);

			ts.setState(TransactionState.PREPARED);
			fileStore.updateTransactionState(id, ts, true);

		}

	}

	class RemoteTxnConverter extends TxnConverter {
		void convert(BaseTransaction baseTxn) throws BrokerException,
				IOException {

			RemoteTransaction remoteTxn = (RemoteTransaction) baseTxn;
			TransactionUID id = baseTxn.getTid();

			TransactionState ts = baseTxn.getTransactionState();

			((PartitionedStore)store).storeRemoteTransaction(id, ts, remoteTxn.getTxnAcks(),
					remoteTxn.getTxnHomeBroker(), Destination.PERSIST_SYNC);
		}
	}

	private String getPrefix() {
		return Thread.currentThread() + " TransactionConverter.";
	}
}
