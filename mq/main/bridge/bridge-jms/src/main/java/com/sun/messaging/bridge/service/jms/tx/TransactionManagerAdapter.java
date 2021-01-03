/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.bridge.service.jms.tx;

import java.util.logging.Logger;
import java.util.Properties;
import jakarta.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

/**
 *
 * A TransactionManager adapter interface. At startup, the method call sequences are 1. instantiation 2. setLogger 4.
 * init(props) 5. if (registerRM()) registerRM(...) <-- 0 or more times 6. ... 7. shutdown
 *
 * @author amyk
 */
public interface TransactionManagerAdapter {

    /**
     */
    void setLogger(Logger logger);

    /**
     * This will be the first method to be called after instantiation
     *
     * @param props the properties
     * @param reset if true, clear existing data
     */
    void init(Properties props, boolean reset) throws Exception;

    /**
     *
     * @return true if RM pre-registration is required
     */
    boolean registerRM();

    /**
     * Register a resource manager to the transaction manager
     *
     * @param rmName resource manager name which uniquely identify the RM in global transactions that it's going be
     * participanting
     * @param xar a XAResource object that is representing the RM
     *
     */
    void registerRM(String rmName, XAResource xar) throws Exception;

    /**
     * Unregister a resource manager from the transaction manager. Afterward, the resource manager should not participant
     * any global trasanctions that are managed by this transactio manager
     *
     * @param rmName The resource manage name that is used in registerRM()
     */
    void unregisterRM(String rmName) throws Exception;

    /**
     * @return the transaction manager object that implements jakarta.transaction.TransactionManager
     */
    TransactionManager getTransactionManager() throws Exception;

    /**
     * @return all transactions
     */
    String[] getAllTransactions() throws Exception;

    /**
     * Shutdown the transaction manager
     */
    void shutdown() throws Exception;

}
