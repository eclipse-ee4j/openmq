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

/*
 * @(#)DAOFactory.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;

import java.util.List;
import java.util.ArrayList;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.JMSBGDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAOJMSBG;

/**
 * Factory for DAO object.
 */
public abstract class DAOFactory {

    protected VersionDAO versionDAO = null;
    protected BrokerDAO brokerDAO = null;
    protected StoreSessionDAO storeSessionDAO = null;
    protected PropertyDAO propertyDAO = null;
    protected MessageDAO messageDAO = null;
    protected DestinationDAO destinationDAO = null;
    protected ConsumerDAO consumerDAO = null;
    protected ConsumerStateDAO consumerStateDAO = null;
    protected ConfigRecordDAO configRecordDAO = null;
    protected TransactionDAO transactionDAO = null;
    protected List daoList = null;

    //JMSBridgeStore interface support
    protected TMLogRecordDAO tmLogRecordDAOJMSBG = null;
    protected JMSBGDAO jmsbgDAO = null;

    public abstract VersionDAO getVersionDAO() throws BrokerException;

    public abstract BrokerDAO getBrokerDAO() throws BrokerException;

    public abstract StoreSessionDAO getStoreSessionDAO() throws BrokerException;

    public abstract PropertyDAO getPropertyDAO() throws BrokerException;

    public abstract MessageDAO getMessageDAO() throws BrokerException;

    public abstract DestinationDAO getDestinationDAO() throws BrokerException;

    public abstract ConsumerDAO getConsumerDAO() throws BrokerException;

    public abstract ConsumerStateDAO getConsumerStateDAO() throws BrokerException;

    public abstract ConfigRecordDAO getConfigRecordDAO() throws BrokerException;

    public abstract TransactionDAO getTransactionDAO() throws BrokerException;

    public abstract TMLogRecordDAO getTMLogRecordDAOJMSBG() throws BrokerException;
    public abstract JMSBGDAO getJMSBGDAO() throws BrokerException;

    public List getAllDAOs() throws BrokerException {

        if ( daoList == null ) {
            synchronized( this ) {
                if ( daoList == null ) {
                    ArrayList list = new ArrayList(10);
                    list.add( getVersionDAO() );
                    list.add( getBrokerDAO() );
                    list.add( getStoreSessionDAO() );
                    list.add( getPropertyDAO() );
                    list.add( getConfigRecordDAO() );
                    list.add( getConsumerDAO() );
                    list.add( getConsumerStateDAO() );
                    list.add( getDestinationDAO() );
                    list.add( getMessageDAO() );
                    list.add( getTransactionDAO() );
                    list.add( getTMLogRecordDAOJMSBG() );
                    list.add( getJMSBGDAO() );
                    daoList = list;
                }
            }
        }

        return daoList;
    }
}
