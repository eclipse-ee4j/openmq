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

import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

class ClusterTxnConverter extends TxnConverter {

    ClusterTxnConverter(TransactionList transactionList, TxnLoggingStore store) {
        super(transactionList, store);
    }

    @Override
    String getPrefix() {
        return Thread.currentThread() + " ToTxnLogConverter.ClusterTxnConverter.";
    }

    void convert(TransactionInformation txnInfo) throws BrokerException {
        if (ToTxnLogConverter.DEBUG) {
            logger.log(Logger.DEBUG, getPrefix() + " convertClusterToTxnLogFormat " + txnInfo);
        }
        // should be a prepared transaction
        int state = txnInfo.getState().getState();
        if (state != TransactionState.PREPARED) {
            String msg = getPrefix() + " convertClusterToTxnLogFormat: unknown state  " + state + " for " + txnInfo;
            logger.log(Logger.ERROR, msg);
        }
        TransactionWork txnWork = new TransactionWork();

        getSentMessages(txnInfo, txnWork);
        getConsumedMessages(txnInfo, txnWork);

        TransactionUID txid = txnInfo.getTID();

        TransactionState newState = new TransactionState(txnInfo.getState());
        TransactionBroker[] tbas = txnInfo.getClusterTransactionBrokers();
        ClusterTransaction clusterTxn = new ClusterTransaction(txid, newState, txnWork, tbas);

        store.logTxn(clusterTxn);
        deleteSentMessagesFromStore(txnWork);

    }

}

