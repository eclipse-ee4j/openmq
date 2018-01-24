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
 * @(#)ConsumerDAO.java	1.11 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;

import java.sql.Connection;
import java.util.List;

/**
 * This class is an interface for the Consumer table which will be implemented
 * by database specific code.
 */
public interface ConsumerDAO extends BaseDAO {

    /**
     * Consumer table:
     * Holds durable subscriptions.
     *
     * CREATE TABLE MQCON<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      ID		    BIGINT NOT NULL,
     *      CLIENT_ID       VARCHAR(1024),
     *      DURABLE_NAME    VARCHAR(1024),
     *      CONSUMER	    LONGVARBINARY NOT NULL,
     *      CREATED_TS      BIGINT NOT NULL,
     *      PRIMARY KEY(ID)
     * );
     *
     * ID - Long value of the ConsumerUID of the consumer object
     * CONSUMER - Serialized Consumer object
     * DURABLE_NAME - JMS durable name
     * CLIENT_ID - JMS client ID
     * CREATED_TS - Timestamp when the entry was created
     */
    public static final String TABLE = "MQCON";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String ID_COLUMN = "ID";
    public static final String CLIENT_ID_COLUMN = "CLIENT_ID";
    public static final String DURABLE_NAME_COLUMN = "DURABLE_NAME";
    public static final String CONSUMER_COLUMN = "CONSUMER";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    void insert( Connection conn, Consumer consumer, long createdTS ) throws BrokerException;

    void delete( Connection conn, Consumer consumer ) throws BrokerException;

    List getAllConsumers( Connection conn ) throws BrokerException;

    Consumer getConsumer( Connection conn, ConsumerUID consumerUID ) throws BrokerException;
}
