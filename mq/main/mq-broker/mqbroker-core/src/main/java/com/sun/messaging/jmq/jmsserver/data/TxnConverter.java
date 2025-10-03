/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

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

    void getSentMessages(TransactionInformation txnInfo, TransactionWork txnWork) throws BrokerException {
        // get messages for this txn
        List<SysMessageID> sentMessageIds = txnInfo.getPublishedMessages();
        Iterator<SysMessageID> msgIter = sentMessageIds.iterator();
        while (msgIter.hasNext()) {
            SysMessageID mid = msgIter.next();
            PacketReference packRef = DL.get((PartitionedStore) store, mid);
            if (packRef == null) {
                String msg = getPrefix() + " convertLocalToTxnLogFormat: can not find packet for sent msg " + mid + " in txn " + txnInfo;
                logger.log(Logger.WARNING, msg);
            } else {

                DestinationUID destUID = packRef.getDestination().getDestinationUID();
                ConsumerUID[] interests = ((PartitionedStore) store).getConsumerUIDs(destUID, mid);
                TransactionWorkMessage twm = new TransactionWorkMessage();
                twm.setStoredInterests(interests);
                twm.setPacketReference(packRef);
                twm.setDestUID(destUID);
                txnWork.addMessage(twm);

            }
        }
    }

    void getConsumedMessages(TransactionInformation txnInfo, TransactionWork txnWork) throws BrokerException {
        // get acks for this txn
        LinkedHashMap<SysMessageID, List<ConsumerUID>> cm = txnInfo.getConsumedMessages(false);
        Iterator<Map.Entry<SysMessageID, List<ConsumerUID>>> iter = cm.entrySet().iterator();

        HashMap cuidToStored = transactionList.retrieveStoredConsumerUIDs(txnInfo.tid);

        while (iter.hasNext()) {
            Map.Entry<SysMessageID, List<ConsumerUID>> entry = iter.next();
            SysMessageID mid = entry.getKey();
            List<ConsumerUID> consumers = entry.getValue();

            PacketReference packRef = DL.get((PartitionedStore) store, mid);
            if (packRef == null) {
                String msg = getPrefix() + " convertLocalToTxnLogFormat: can not find packet for consumed msg" + mid + " in txn " + txnInfo;
                logger.log(Logger.WARNING, msg);
            } else {
                DestinationUID destUID = packRef.getDestination().getDestinationUID();
                if (consumers != null) {
                    for (int i = 0; i < consumers.size(); i++) {
                        ConsumerUID cid = consumers.get(i);
                        ConsumerUID storedcid = (ConsumerUID) cuidToStored.get(cid);
                        if (storedcid == null) {
                            if (ToTxnLogConverter.DEBUG) {
                                String msg = getPrefix() + " storedcid=null for " + cid;
                                logger.log(Logger.DEBUG, msg);
                            }
                            storedcid = cid;
                        }
                        TransactionWorkMessageAck twma = new TransactionWorkMessageAck(destUID, mid, storedcid);
                        if (ToTxnLogConverter.DEBUG) {
                            String msg = getPrefix() + " convertLocalToTxnLogFormat: converting messageAck:" + " mid=" + mid + " destID=" + destUID
                                    + " consumerID=" + cid + " storedCid=" + storedcid + " txid=" + txnInfo.tid;
                            logger.log(Logger.DEBUG, msg);
                        }
                        txnWork.addMessageAcknowledgement(twma);

                    }
                }

            }

        }
    }

    void deleteSentMessagesFromStore(TransactionWork txnWork) throws BrokerException {

        // now delete any sent messages from store ( they are stored in txn
        // log or prepared msg store)
        Iterator<TransactionWorkMessage> sent = txnWork.getSentMessages().iterator();
        while (sent.hasNext()) {
            TransactionWorkMessage twm = sent.next();
            DestinationUID duid = twm.getDestUID();
            SysMessageID mid = twm.getMessage().getSysMessageID();
            try {
                ((PartitionedStore) store).removeMessage(duid, mid, true);
            } catch (IOException e) {
                String msg = "Could not remove transacted sent message during txn conversion";
                logger.logStack(Logger.ERROR, msg, e);
            }
        }
    }

}

