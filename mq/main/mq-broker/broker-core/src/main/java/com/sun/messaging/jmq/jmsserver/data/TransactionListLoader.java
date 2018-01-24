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

package com.sun.messaging.jmq.jmsserver.data;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.log.Logger;

public class TransactionListLoader {

	static Logger logger = Globals.getLogger();
	static boolean loaded = false;


	// txn log

	public static void loadTransactions(PartitionedStore store, TransactionList transactionList) 
        throws BrokerException, IOException {
		logger.log(Logger.INFO, BrokerResources.I_PROCESSING_TRANS);
		// This method is called after the message store has been loaded
		// and after the transaction log has been replayed.

		// The state of the system should be :
		// 1) All committed transactions since the last checkpoint should
		// have been replayed and persisted to the message store.
		// 2) There should be no incomplete transactions
		// (a partial write of a transaction will have been discarded).
		// 3) The message store will contain a set of prepared transactions
		// either from XA or from clustered/remote transactions.
		loadLocalTransactions(store,transactionList);
		loadClusterTransactions(store,transactionList);
		loadRemoteTransactions(store,transactionList);

	}
	
	
	
	public static void rollbackAllTransactions(PartitionedStore store)
	{
		logger.log(Logger.INFO, "rolling back all transactions");
		((TxnLoggingStore)store).rollbackAllTransactions();
		
	}
	
	
	
	private static void loadDestinations(PartitionedStore store) throws BrokerException {
		if (!loaded) {
			loaded = true;
			Globals.getDestinationList().loadDestinations(store);
			Iterator[] itrs = Globals.getDestinationList().getAllDestinations(store);
                        Iterator itr = itrs[0];
			while (itr.hasNext()) {
				Destination d = (Destination) itr.next();
				d.load();
			}
		}
	}

	private static void loadLocalTransactions(PartitionedStore store, TransactionList transactionList) 
        throws BrokerException, IOException {

		List<BaseTransaction> incompleteTxns = ((TxnLoggingStore)store)
				.getIncompleteTransactions(BaseTransaction.LOCAL_TRANSACTION_TYPE);

		String msg = " loading " + incompleteTxns.size()
				+ " incomplete Local transactions:  ";
		logger.log(Logger.DEBUG, msg);

		Iterator<BaseTransaction> iter = incompleteTxns.iterator();
		while (iter.hasNext()) {
			BaseTransaction baseTxn = iter.next();
			TransactionUID tid = baseTxn.getTid();

			msg = " loadTransactions: processing local transaction " + tid;
			logger.log(Logger.DEBUG, msg);

			TransactionState state = baseTxn.getTransactionState();
			transactionList.addTransactionID(tid, state, false);
			TransactionWork txnWork = baseTxn.getTransactionWork();
			handleTransactionWork(transactionList,tid, txnWork);
                        if (state.getState() == TransactionState.PREPARED &&
                            state.getOnephasePrepare()) {
                            transactionList.addDetachedTransactionID(tid);
                        }
		}
	}

	private static void loadClusterTransactions(PartitionedStore store, TransactionList transactionList)
        throws BrokerException, IOException {

		List<BaseTransaction> incompleteTxns = ((TxnLoggingStore)store)
				.getIncompleteTransactions(BaseTransaction.CLUSTER_TRANSACTION_TYPE);
		String msg = " loading " + incompleteTxns.size()
				+ " incomplete cluster transactions:  ";
                if (transactionList.DEBUG_CLUSTER_TXN) {
		    logger.log(Logger.INFO, msg);
                } 
		Iterator<BaseTransaction> iter = incompleteTxns.iterator();
		while (iter.hasNext()) {
			ClusterTransaction clusterTxn = (ClusterTransaction) iter.next();
			TransactionUID tid = clusterTxn.getTid();

			msg = " loadTransactions: processing cluster transaction " + tid;
			logger.log(Logger.DEBUG, msg);

			TransactionState state = clusterTxn.getTransactionState();
			transactionList.addTransactionID(tid, state, false);

			TransactionBroker[] transactionBrokers = clusterTxn
					.getTransactionBrokers();
			transactionList.logClusterTransaction(tid, state,
					transactionBrokers, true, false);
			TransactionWork txnWork = clusterTxn.getTransactionWork();
                        if (state.getState() == TransactionState.PREPARED) {
                            if (transactionList.DEBUG_CLUSTER_TXN) {
		                logger.log(Logger.INFO, "Loading cluster transaction "+tid+"["+state+"]");
                            } 
                            handleTransactionWork(transactionList, tid, txnWork);
                            if (state.getOnephasePrepare()) {
                                transactionList.addDetachedTransactionID(tid);
                            }
			} else if (state.getState() == TransactionState.COMMITTED) {
				// incomplete cluster transaction
				// the work has already been committed locally but
				// there may be remote brokers that have not confirmed they have
				// committed this transaction
				// Add to transaction reaper
				transactionList.removeTransaction(tid, false);
			}
				
		}
	}
	
	private static void loadRemoteTransactions(PartitionedStore store, TransactionList transactionList) 
        throws BrokerException, IOException {

		List<BaseTransaction> incompleteTxns = ((TxnLoggingStore)store)
				.getIncompleteTransactions(BaseTransaction.REMOTE_TRANSACTION_TYPE);
		String msg = " loading " + incompleteTxns.size()
				+ " incomplete remote transactions:  ";
		logger.log(Logger.DEBUG, msg);
		Iterator<BaseTransaction> iter = incompleteTxns.iterator();
		while (iter.hasNext()) {
			RemoteTransaction remoteTxn = (RemoteTransaction)iter.next();
			TransactionUID tid = remoteTxn.getTid();

		
			TransactionState state = remoteTxn.getTransactionState();
			TransactionAcknowledgement  tas[] = remoteTxn.getTxnAcks();
			DestinationUID  destIds[] = remoteTxn.getDestIds();
			msg = " loadTransactions: processing remote transaction " + tid + " state= "+state;
			logger.log(Logger.DEBUG, msg);
			BrokerAddress remoteTransactionHomeBroker = remoteTxn.getTxnHomeBroker();
			
			transactionList.logRemoteTransaction(tid, state, tas, 
					remoteTransactionHomeBroker, true, true, false);
			
			for(int i=0;i<tas.length;i++)
			{
				TransactionAcknowledgement ta = tas[i];
				DestinationUID destId = destIds[i];
				unrouteLoadedTransactionAckMessage(store, destId,ta.getSysMessageID(),ta.getStoredConsumerUID());
			}
		}
	}

	static void  handleTransactionWork(TransactionList transactionList,TransactionUID tid, TransactionWork txnWork)
			throws BrokerException {
		
		
		
		handleSentMessages(transactionList, txnWork);
		handleMessageAcks(transactionList,tid,txnWork);
		
	}
	
	static void  handleSentMessages(TransactionList transactionList, TransactionWork txnWork) throws BrokerException
	{
		for (int i = 0; i < txnWork.numSentMessages(); i++) {

			TransactionWorkMessage msg = txnWork.getSentMessages().get(i);
			Packet packet = msg.getMessage();

			DestinationUID duid = msg.getDestUID();

			logger.log(Logger.DEBUG,
					" handleSentMessages: duid= "+duid);
						
			PacketReference pr = PacketReference.createReference(
                                                 transactionList.getPartitionedStore(),
                                                 packet, duid, null);
			Destination[] ds = Globals.getDestinationList().getDestination(
                                            transactionList.getPartitionedStore(), duid);
                        Destination d = ds[0];
			if (d == null) {
				// Could be an auto-created dest that was reaped on load.
				// Lets recreate it here.
				try {
                                    int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE
				        	: DestType.DEST_TYPE_TOPIC);
					ds = Globals.getDestinationList().getDestination(
                                                transactionList.getPartitionedStore(),
                                                duid.getName(), type, true, true);
                                        d = ds[0];
				} catch (IOException e) {
					throw new BrokerException("Could not recreate destination "
							+ duid, e);
				}
			}
			
			// check it is loaded
			d.load();
			
			logger.log(Logger.DEBUG,
					" loadTransactions: processing prepared sent message "
							+ packet.getMessageID()) ;

			// queue message
			boolean result = d.queueMessage(pr, true);

			// store (should not really be persisted as we are using txnLog)
			// pr.store();

			// add message to transaction
			transactionList.addMessage(pr.getTransactionID(), pr
					.getSysMessageID(), true);

		}
	}
	
	static void handleMessageAcks(TransactionList transactionList,TransactionUID tid, TransactionWork txnWork)
			throws BrokerException {
		for (int i = 0; i < txnWork.numMessageAcknowledgments(); i++) {
			TransactionWorkMessageAck msgAck = txnWork
					.getMessageAcknowledgments().get(i);			
			DestinationUID destID = msgAck.getDestUID();
			handleAck(transactionList,tid, destID, msgAck.getSysMessageID(), msgAck.getConsumerID());

		}
	}
	
	
	static void handleAck(TransactionList transactionList,TransactionUID tid, DestinationUID destID, SysMessageID ackedSysMsgID,
			ConsumerUID consumerID) throws BrokerException {
		logger.log(Logger.DEBUG,
				" loadTransactions: processing prepared acknowledged message "
						+ ackedSysMsgID);

		transactionList.addAcknowledgement(tid, ackedSysMsgID, consumerID,
				consumerID, true, false);
		transactionList
				.addOrphanAck(tid, ackedSysMsgID, consumerID, consumerID);

		unrouteLoadedTransactionAckMessage(transactionList.getPartitionedStore(),
                                                   destID, ackedSysMsgID, consumerID);

	}

	private static void unrouteLoadedTransactionAckMessage(PartitionedStore store, 
                            DestinationUID destID, SysMessageID ackedSysMsgID,
			ConsumerUID consumerID) throws BrokerException {
		logger.log(Logger.DEBUG,
				" trying to unroute prepared acknowledged message: destID =  "
						+ destID + " ackedMsgId=" + ackedSysMsgID);
		
		Destination dest = null;
		PacketReference ackedMessage = null;
		if (destID != null) {
			Destination[] ds = Globals.getDestinationList().
                                              getDestination(store, destID);
                        dest = ds[0];
			if (dest != null) {
				dest.load();
				ackedMessage = dest.getMessage(ackedSysMsgID);
				if (ackedMessage == null) {

					String msg = "Could not find packet for " + ackedSysMsgID
							+ "in dest " + dest;
					logger.log(Logger.WARNING, msg);
					return;
				}
			} else {
				// this could happen e.g. if empty auto dest has been destroyed
				String msg = "Could not find destination for " + destID;
				logger.log(Logger.WARNING, msg);
				return;
			}

		} else {
			// no destid stored for some reason so need to load all dests
			logger.log(Logger.WARNING,
					"No dest ID for acked message. Will need to load all dests "
							+ ackedSysMsgID);
			loadDestinations(store);

			ackedMessage = Globals.getDestinationList().get(store, ackedSysMsgID);
			dest = ackedMessage.getDestination();
		}

		// need to unroute messages that have been consumed in a prepared
		// transaction.
		// they cannot be redelivered unless the transaction rolls back.

		dest.unrouteLoadedTransactionAckMessage(ackedMessage, consumerID);

	}

}
