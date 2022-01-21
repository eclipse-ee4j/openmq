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

import java.util.ArrayList;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.cluster.api.RemoteTransactionAckEntry;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

class RemoteTxnConverter extends TxnConverter {

    RemoteTxnConverter(TransactionList transactionList, TxnLoggingStore store) {
        super(transactionList, store);
    }

    @Override
    String getPrefix() {
        return Thread.currentThread() + " ToTxnLogConverter.RemoteTxnConverter.";
    }

    void convert(TransactionInformation txnInfo) throws BrokerException {
        if (ToTxnLogConverter.DEBUG) {
            logger.log(Logger.DEBUG, getPrefix() + " convert " + txnInfo);
        }

        // should be a prepared transaction
        int state = txnInfo.getState().getState();
        if (state != TransactionState.PREPARED) {
            String msg = getPrefix() + " convert: unknown state  " + state + " for " + txnInfo;
            logger.log(Logger.ERROR, msg);
        }

        TransactionUID txid = txnInfo.getTID();

        TransactionState newState = new TransactionState(txnInfo.getState());
        RemoteTransactionAckEntry[] rtaes = transactionList.getRecoveryRemoteTransactionAcks(txid);

        if (rtaes != null) {

            ArrayList<TransactionAcknowledgement> al = new ArrayList<>();
            for (int i = 0; i < rtaes.length; i++) {
                RemoteTransactionAckEntry rtae = rtaes[i];
                TransactionAcknowledgement[] txnAcks = rtae.getAcks();
                for (int j = 0; j < txnAcks.length; j++) {
                    al.add(txnAcks[j]);
                }
            }

            TransactionAcknowledgement[] txnAcks = al.toArray(new TransactionAcknowledgement[al.size()]);

            DestinationUID[] destIds = new DestinationUID[txnAcks.length];
            for (int i = 0; i < txnAcks.length; i++) {
                SysMessageID sid = txnAcks[i].getSysMessageID();
                PacketReference p = DL.get((PartitionedStore) store, sid);
                DestinationUID destID = null;
                if (p != null) {
                    destID = p.getDestinationUID();
                } else {
                    logger.log(Logger.WARNING, "Could not find packet for " + sid);
                }
                destIds[i] = destID;
            }
            TransactionBroker txnBroker = transactionList.getRemoteTransactionHomeBroker(txid);
            BrokerAddress txnHomeBroker = txnBroker.getBrokerAddress();

            RemoteTransaction remoteTxn = new RemoteTransaction(txid, newState, txnAcks, destIds, txnHomeBroker);

            store.logTxn(remoteTxn);
        } else {
            logger.log(Logger.ERROR, "Could not find RemoteTransactionAckEntry for " + txid);
        }

    }

}
