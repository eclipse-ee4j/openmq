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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.cluster.api.RemoteTransactionAckEntry;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.log.Logger;

public class ToTxnLogConverter {

	static Logger logger = Globals.getLogger();

	public static boolean DEBUG = 
          (Globals.getLogger().getLevel() <= Logger.DEBUG) ? true : false;

	public static void convertToTxnLogFormat(TransactionList transactionList,
			TxnLoggingStore store) throws BrokerException {

		Map translist = transactionList.getTransactionListMap();
		convertTxnList(translist.values(), transactionList, store);

		Map remoteTranslist = transactionList.getRemoteTransactionListMap();
		convertTxnList(remoteTranslist.values(), transactionList, store);

	}

	private static void convertTxnList(Collection txlist,
			TransactionList transactionList, TxnLoggingStore store)
			throws BrokerException {
		if (DEBUG) {
			logger.log(Logger.DEBUG, getPrefix() + " convertTxnList  "
					+ txlist.size());
		}
		Iterator<TransactionInformation> txIter = txlist.iterator();
		while (txIter.hasNext()) {
			TransactionInformation txnInfo = txIter.next();
			int type = txnInfo.getType();
			LocalTxnConverter localConverter = new LocalTxnConverter(
					transactionList, store);
			ClusterTxnConverter clusterConverter = new ClusterTxnConverter(
					transactionList, store);
			RemoteTxnConverter remoteConverter = new RemoteTxnConverter(
					transactionList, store);
			switch (type) {
			case TransactionInfo.TXN_LOCAL:
				localConverter.convert(txnInfo);
				break;
			case TransactionInfo.TXN_CLUSTER:
				clusterConverter.convert(txnInfo);
				break;
			case TransactionInfo.TXN_REMOTE:
				remoteConverter.convert(txnInfo);
				break;
			default: {
				String msg = getPrefix()
						+ "convertToTxnLogFormat: unknown transaction type "
						+ type + " for " + txnInfo;
				logger.log(Logger.ERROR, msg);
			}
			}

		}
	}

	private static String getPrefix() {
		return Thread.currentThread() + " ToTxnLogConverter.";
	}

}

class TxnConverter {
	TransactionList transactionList;
	TxnLoggingStore store;
	static Logger logger = Globals.getLogger();
        DestinationList DL = Globals.getDestinationList();

	TxnConverter(TransactionList transactionList, TxnLoggingStore store) {
		this.transactionList = transactionList;
		this.store = store;
	}

	String getPrefix() {
		return Thread.currentThread() + " ToTxnLogConverter.TxnConverter.";
	}

	void getSentMessages(TransactionInformation txnInfo, TransactionWork txnWork)
			throws BrokerException {
		// get messages for this txn
		List<SysMessageID> sentMessageIds = txnInfo.getPublishedMessages();
		Iterator<SysMessageID> msgIter = sentMessageIds.iterator();
		while (msgIter.hasNext()) {
			SysMessageID mid = msgIter.next();
			PacketReference packRef = DL.get((PartitionedStore)store, mid);
			if (packRef == null) {
				String msg = getPrefix()
						+ " convertLocalToTxnLogFormat: can not find packet for sent msg "
						+ mid + " in txn " + txnInfo;
				logger.log(Logger.WARNING, msg);
			} else {

				DestinationUID destUID = packRef.getDestination()
						.getDestinationUID();
				ConsumerUID[] interests = ((PartitionedStore)store).getConsumerUIDs(destUID, mid);
				TransactionWorkMessage twm = new TransactionWorkMessage();
				twm.setStoredInterests(interests);
				twm.setPacketReference(packRef);
				twm.setDestUID(destUID);
				txnWork.addMessage(twm);

			}
		}
	}

	void getConsumedMessages(TransactionInformation txnInfo,
			TransactionWork txnWork) throws BrokerException {
		// get acks for this txn
		LinkedHashMap<SysMessageID, List<ConsumerUID>> cm = txnInfo
				.getConsumedMessages(false);
		Iterator<Map.Entry<SysMessageID, List<ConsumerUID>>> iter = cm
				.entrySet().iterator();

		HashMap cuidToStored = transactionList
				.retrieveStoredConsumerUIDs(txnInfo.tid);

		while (iter.hasNext()) {
			Map.Entry<SysMessageID, List<ConsumerUID>> entry = iter.next();
			SysMessageID mid = entry.getKey();
			List<ConsumerUID> consumers = entry.getValue();

			PacketReference packRef = DL.get((PartitionedStore)store, mid);
			if (packRef == null) {
				String msg = getPrefix()
						+ " convertLocalToTxnLogFormat: can not find packet for consumed msg"
						+ mid + " in txn " + txnInfo;
				logger.log(Logger.WARNING, msg);
			} else {
				DestinationUID destUID = packRef.getDestination()
						.getDestinationUID();
				if (consumers != null) {
					for (int i = 0; i < consumers.size(); i++) {
						ConsumerUID cid = consumers.get(i);
						ConsumerUID storedcid = (ConsumerUID) cuidToStored
								.get(cid);
						if (storedcid == null) {
							if (ToTxnLogConverter.DEBUG) {
								String msg = getPrefix()
										+ " storedcid=null for " + cid;
								logger.log(Logger.DEBUG, msg);
							}
							storedcid = cid;
						}
						TransactionWorkMessageAck twma = new TransactionWorkMessageAck(
								destUID, mid, storedcid);
						if (ToTxnLogConverter.DEBUG) {
							String msg = getPrefix()
									+ " convertLocalToTxnLogFormat: converting messageAck:"
									+ " mid=" + mid + " destID=" + destUID
									+ " consumerID=" + cid + " storedCid="
									+ storedcid + " txid=" + txnInfo.tid;
							logger.log(Logger.DEBUG, msg);
						}
						txnWork.addMessageAcknowledgement(twma);

					}
				}

			}

		}
	}

	void deleteSentMessagesFromStore(TransactionWork txnWork)
			throws BrokerException {

		// now delete any sent messages from store ( they are stored in txn
		// log or prepared msg store)
		Iterator<TransactionWorkMessage> sent = txnWork.getSentMessages()
				.iterator();
		while (sent.hasNext()) {
			TransactionWorkMessage twm = sent.next();
			DestinationUID duid = twm.getDestUID();
			SysMessageID mid = twm.getMessage().getSysMessageID();
			try {
				((PartitionedStore)store).removeMessage(duid, mid, true);
			} catch (IOException e) {
				String msg = "Could not remove transacted sent message during txn conversion";
				logger.logStack(Logger.ERROR, msg, e);
			}
		}
	}

}

class LocalTxnConverter extends TxnConverter {

	LocalTxnConverter(TransactionList transactionList, TxnLoggingStore store) {
		super(transactionList, store);
	}

	String getPrefix() {
		return Thread.currentThread() + " ToTxnLogConverter.LocalTxnConverter.";
	}

	void convert(TransactionInformation txnInfo) throws BrokerException {
		if (ToTxnLogConverter.DEBUG) {
			logger.log(Logger.DEBUG, getPrefix()
					+ " convertLocalToTxnLogFormat " + txnInfo);
		}
		// should be a prepared transaction
		int state = txnInfo.getState().getState();
		if (state != TransactionState.PREPARED) {
			String msg = getPrefix()
					+ " convertLocalToTxnLogFormat: ignoring state  " + state
					+ " for " + txnInfo;
			logger.log(Logger.INFO, msg);
		}
		TransactionWork txnWork = new TransactionWork();

		getSentMessages(txnInfo, txnWork);
		getConsumedMessages(txnInfo, txnWork);

		TransactionUID txid = txnInfo.getTID();
		JMQXid xid = txnInfo.getState().getXid();
		LocalTransaction localTxn = new LocalTransaction(txid, state, xid,
				txnWork);
		TransactionState newState = new TransactionState(txnInfo.getState());

		localTxn.setTransactionState(newState);

		store.logTxn(localTxn);
		deleteSentMessagesFromStore(txnWork);

	}

}

class ClusterTxnConverter extends TxnConverter {

	ClusterTxnConverter(TransactionList transactionList, TxnLoggingStore store) {
		super(transactionList, store);
	}

	String getPrefix() {
		return Thread.currentThread()
				+ " ToTxnLogConverter.ClusterTxnConverter.";
	}

	void convert(TransactionInformation txnInfo) throws BrokerException {
		if (ToTxnLogConverter.DEBUG) {
			logger.log(Logger.DEBUG, getPrefix()
					+ " convertClusterToTxnLogFormat " + txnInfo);
		}
		// should be a prepared transaction
		int state = txnInfo.getState().getState();
		if (state != TransactionState.PREPARED) {
			String msg = getPrefix()
					+ " convertClusterToTxnLogFormat: unknown state  " + state
					+ " for " + txnInfo;
			logger.log(Logger.ERROR, msg);
		}
		TransactionWork txnWork = new TransactionWork();

		getSentMessages(txnInfo, txnWork);
		getConsumedMessages(txnInfo, txnWork);

		TransactionUID txid = txnInfo.getTID();
	
		TransactionState newState = new TransactionState(txnInfo.getState());
		TransactionBroker[] tbas = txnInfo.getClusterTransactionBrokers();
		ClusterTransaction clusterTxn = new ClusterTransaction(txid, newState,
				txnWork, tbas);

		store.logTxn(clusterTxn);
		deleteSentMessagesFromStore(txnWork);

	}

}

class RemoteTxnConverter extends TxnConverter {

	RemoteTxnConverter(TransactionList transactionList, TxnLoggingStore store) {
		super(transactionList, store);
	}

	String getPrefix() {
		return Thread.currentThread()
				+ " ToTxnLogConverter.RemoteTxnConverter.";
	}

	void convert(TransactionInformation txnInfo) throws BrokerException {
		if (ToTxnLogConverter.DEBUG) {
			logger.log(Logger.DEBUG, getPrefix() + " convert " + txnInfo);
		}

		// should be a prepared transaction
		int state = txnInfo.getState().getState();
		if (state != TransactionState.PREPARED) {
			String msg = getPrefix() + " convert: unknown state  " + state
					+ " for " + txnInfo;
			logger.log(Logger.ERROR, msg);
		}

		TransactionUID txid = txnInfo.getTID();

		TransactionState newState = new TransactionState(txnInfo.getState());
		RemoteTransactionAckEntry[] rtaes = transactionList
				.getRecoveryRemoteTransactionAcks(txid);

		if (rtaes != null) {

			ArrayList<TransactionAcknowledgement> al = new ArrayList<TransactionAcknowledgement>();
			for (int i = 0; i < rtaes.length; i++) {
				RemoteTransactionAckEntry rtae = rtaes[i];
				TransactionAcknowledgement[] txnAcks = rtae.getAcks();
				for (int j = 0; j < txnAcks.length; j++) {
					al.add(txnAcks[j]);
				}
			}

			TransactionAcknowledgement[] txnAcks = al
					.toArray(new TransactionAcknowledgement[al.size()]);

			DestinationUID[] destIds = new DestinationUID[txnAcks.length];
			for (int i = 0; i < txnAcks.length; i++) {
				SysMessageID sid = txnAcks[i].getSysMessageID();
				PacketReference p = DL.get((PartitionedStore)store, sid);
				DestinationUID destID = null;
				if (p != null) {
					destID = p.getDestinationUID();
				} else {
					logger.log(Logger.WARNING, "Could not find packet for "
							+ sid);
				}
				destIds[i] = destID;
			}
			TransactionBroker txnBroker = transactionList
					.getRemoteTransactionHomeBroker(txid);
			BrokerAddress txnHomeBroker = txnBroker.getBrokerAddress();

			RemoteTransaction remoteTxn = new RemoteTransaction(txid, newState,
					txnAcks, destIds, txnHomeBroker);

			((TxnLoggingStore)store).logTxn(remoteTxn);
		} else {
			logger.log(Logger.ERROR,
					"Could not find RemoteTransactionAckEntry for " + txid);
		}

	}

}
