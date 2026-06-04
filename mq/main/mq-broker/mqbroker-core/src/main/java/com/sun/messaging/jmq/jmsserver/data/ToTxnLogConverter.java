/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

@SuppressWarnings({"ForEachIterable"})
public class ToTxnLogConverter {

    static Logger logger = Globals.getLogger();

    public static boolean DEBUG = Globals.getLogger().getLevel() <= Logger.DEBUG;

    public static void convertToTxnLogFormat(TransactionList transactionList, TxnLoggingStore store) throws BrokerException {

        Map translist = transactionList.getTransactionListMap();
        convertTxnList(translist.values(), transactionList, store);

        Map remoteTranslist = transactionList.getRemoteTransactionListMap();
        convertTxnList(remoteTranslist.values(), transactionList, store);

    }

    private static void convertTxnList(Collection txlist, TransactionList transactionList, TxnLoggingStore store) throws BrokerException {
        if (DEBUG) {
            logger.log(Logger.DEBUG, getPrefix() + " convertTxnList  " + txlist.size());
        }
        Iterator<TransactionInformation> txIter = txlist.iterator();
        while (txIter.hasNext()) {
            TransactionInformation txnInfo = txIter.next();
            int type = txnInfo.getType();
            TxnConverter converter;
            switch (type) {
            case TransactionInfo.TXN_LOCAL:
                converter = new LocalTxnConverter(transactionList, store);
                break;
            case TransactionInfo.TXN_CLUSTER:
                converter = new ClusterTxnConverter(transactionList, store);
                break;
            case TransactionInfo.TXN_REMOTE:
                converter = new RemoteTxnConverter(transactionList, store);
                break;
            default: {
                converter = null;
                String msg = getPrefix() + "convertToTxnLogFormat: unknown transaction type " + type + " for " + txnInfo;
                logger.log(Logger.ERROR, msg);
            }
            }
            if (converter != null) {
                converter.convert(txnInfo);
            }

        }
    }

    private static String getPrefix() {
        return Thread.currentThread() + " ToTxnLogConverter.";
    }

}

