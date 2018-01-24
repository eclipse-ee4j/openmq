/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;


/**
 *
 * A TransactionManager adapter interface.  At startup, the method call sequences are 
 *     1. instantiation
 *     2. setLogger
 *     4. init(props)
 *     5. if (registerRM()) registerRM(...)  <-- 0 or more times
 *     6. ...
 *     7. shutdown
 *
 * @author amyk
 */
public interface TransactionManagerAdapter {

    /**
     */
    public void setLogger(Logger logger);

    /**
     * This will be the first method to be called after instantiation
     * @param props the properties 
     * @param reset if true, clear existing data  
     */
    public void init(Properties props, boolean reset) throws Exception; 


    /**
     * 
     * @return true if RM pre-registration is required
     */
    public boolean registerRM();

    /**
     * Register a resource manager to the transaction manager
     *
     * @param rmName resource manager name which uniquely identify
     *               the RM in global transactions that it's going 
     *               be participanting
     * @param xar a XAResource object that is representing the RM
     *
     */
    public void registerRM(String rmName, XAResource xar) throws Exception;

    /**
     * Unregister a resource manager from the transaction manager. 
     * Afterward, the resource manager should not participant
     * any global trasanctions that are managed by this transactio manager 
     *
     * @param rmName The resource manage name that is used in registerRM()
     */
    public void unregisterRM(String rmName) throws Exception;

    /**
     * @return the transaction manager object that implements javax.transaction.TransactionManager
     */
    public TransactionManager getTransactionManager() throws Exception;

    /**
     * @return all transactions
     */
    public String[] getAllTransactions() throws Exception;

    /** 
     * Shutdown the transaction manager
     */
    public void shutdown() throws Exception;

}
