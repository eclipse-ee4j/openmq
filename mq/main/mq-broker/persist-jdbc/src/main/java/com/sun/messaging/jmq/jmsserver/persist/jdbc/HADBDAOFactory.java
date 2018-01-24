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
 * @(#)HADBDAOFactory.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.*;

/**
 * Factory for HADB implementation of DAO object.
 */
public class HADBDAOFactory extends GenericDAOFactory {

    public MessageDAO getMessageDAO() throws BrokerException {

        if ( messageDAO == null ) {
            messageDAO = new HADBMessageDAOImpl();
        }
        return messageDAO;
    }

    public DestinationDAO getDestinationDAO() throws BrokerException {

        if ( destinationDAO == null ) {
            destinationDAO = new HADBDestinationDAOImpl();
        }
        return destinationDAO;
    }

    public ConsumerDAO getConsumerDAO() throws BrokerException {

        if ( consumerDAO == null ) {
            consumerDAO = new HADBConsumerDAOImpl();
        }
        return consumerDAO;
    }

    public ConsumerStateDAO getConsumerStateDAO() throws BrokerException {

        if ( consumerStateDAO == null ) {
            consumerStateDAO = new HADBConsumerStateDAOImpl();
        }
        return consumerStateDAO;
    }

    public ConfigRecordDAO getConfigRecordDAO() throws BrokerException {

        if ( configRecordDAO == null ) {
            configRecordDAO = new HADBConfigRecordDAOImpl();
        }
        return configRecordDAO;
    }

    public TransactionDAO getTransactionDAO() throws BrokerException {

        if ( transactionDAO == null ) {
            transactionDAO = new HADBTransactionDAOImpl();
        }
        return transactionDAO;
    }
}
