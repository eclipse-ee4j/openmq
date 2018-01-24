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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * Factory for Oracle implementation of DAO object.
 */
public class MySQLDAOFactory extends GenericDAOFactory {

    private static final boolean enableStoredProc =  Globals.getConfig().
        getBooleanProperty("imq.persist.jdbc.mysql.enableStoredProc", false);

    public MessageDAO getMessageDAO() throws BrokerException {
        if (!enableStoredProc) {
            return super.getMessageDAO();
        }

        if ( messageDAO == null ) {
            messageDAO = new MySQLMessageDAOImpl();
        } 
        return messageDAO;
    }

    public BrokerDAO getBrokerDAO() throws BrokerException {
        if (!enableStoredProc) {
            return super.getBrokerDAO();
        }

        if ( brokerDAO == null ) {
            brokerDAO = new MySQLBrokerDAOImpl();
        }
        return brokerDAO;
    }
}
