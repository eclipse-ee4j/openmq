/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAOJMSBG;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.JMSBGDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.JMSBGDAOImpl;

/**
 * Factory for generic implementation of DAO object.
 */
public class GenericDAOFactory extends DAOFactory {

    @Override
    public VersionDAO getVersionDAO() throws BrokerException {

        if (versionDAO == null) {
            versionDAO = new VersionDAOImpl();
        }
        return versionDAO;
    }

    @Override
    public BrokerDAO getBrokerDAO() throws BrokerException {

        if (brokerDAO == null) {
            brokerDAO = new BrokerDAOImpl();
        }
        return brokerDAO;
    }

    @Override
    public StoreSessionDAO getStoreSessionDAO() throws BrokerException {

        if (storeSessionDAO == null) {
            storeSessionDAO = new StoreSessionDAOImpl();
        }
        return storeSessionDAO;
    }

    @Override
    public PropertyDAO getPropertyDAO() throws BrokerException {

        if (propertyDAO == null) {
            propertyDAO = new PropertyDAOImpl();
        }
        return propertyDAO;
    }

    @Override
    public MessageDAO getMessageDAO() throws BrokerException {

        if (messageDAO == null) {
            messageDAO = new MessageDAOImpl();
        }
        return messageDAO;
    }

    @Override
    public DestinationDAO getDestinationDAO() throws BrokerException {

        if (destinationDAO == null) {
            destinationDAO = new DestinationDAOImpl();
        }
        return destinationDAO;
    }

    @Override
    public ConsumerDAO getConsumerDAO() throws BrokerException {

        if (consumerDAO == null) {
            consumerDAO = new ConsumerDAOImpl();
        }
        return consumerDAO;
    }

    @Override
    public ConsumerStateDAO getConsumerStateDAO() throws BrokerException {

        if (consumerStateDAO == null) {
            consumerStateDAO = new ConsumerStateDAOImpl();
        }
        return consumerStateDAO;
    }

    @Override
    public ConfigRecordDAO getConfigRecordDAO() throws BrokerException {

        if (configRecordDAO == null) {
            configRecordDAO = new ConfigRecordDAOImpl();
        }
        return configRecordDAO;
    }

    @Override
    public TransactionDAO getTransactionDAO() throws BrokerException {

        if (transactionDAO == null) {
            transactionDAO = new TransactionDAOImpl();
        }
        return transactionDAO;
    }

    @Override
    public TMLogRecordDAO getTMLogRecordDAOJMSBG() throws BrokerException {

        if (tmLogRecordDAOJMSBG == null) {
            tmLogRecordDAOJMSBG = new TMLogRecordDAOJMSBG();
        }
        return tmLogRecordDAOJMSBG;
    }

    @Override
    public JMSBGDAO getJMSBGDAO() throws BrokerException {

        if (jmsbgDAO == null) {
            jmsbgDAO = new JMSBGDAOImpl();
        }
        return jmsbgDAO;
    }
}
