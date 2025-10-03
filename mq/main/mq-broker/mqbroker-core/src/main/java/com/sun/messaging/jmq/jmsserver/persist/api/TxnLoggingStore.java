/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

/*
 */
package com.sun.messaging.jmq.jmsserver.persist.api;

import java.util.List;
import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 */
public interface TxnLoggingStore {

    void init() throws BrokerException;

    void logTxn(BaseTransaction txnWork) throws BrokerException;

    void logTxnCompletion(TransactionUID tid, int state, int type) throws BrokerException;

    void loggedCommitWrittenToMessageStore(TransactionUID tid, int type);

    boolean isTxnConversionRequired();

    void convertTxnFormats(TransactionList transactionList) throws BrokerException, IOException;

    List<BaseTransaction> getIncompleteTransactions(int type);

    void rollbackAllTransactions();

    /**
     * Perform a checkpoint Only applicable to FileStore with new txn log
     *
     * @param sync Flag to determine whther method block until checpoint is complete
     * @return status of checkpoint. Will return 0 if completed ok.
     */
    int doCheckpoint(boolean sync);

    /**************************************************
     * OLD Transaction Logging Methods
     **************************************************/

    boolean initTxnLogger() throws BrokerException;

    void logTxn(int type, byte[] data) throws IOException;

}
