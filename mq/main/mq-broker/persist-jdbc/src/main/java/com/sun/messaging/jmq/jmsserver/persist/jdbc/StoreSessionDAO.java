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
 * @(#)StoreSessionDAO.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * This class is an interface for the Store Session table which will be
 * implemented by database specific code.
 */
public interface StoreSessionDAO extends BaseDAO {

    /**
     * Store Session table:
     * Keep track of store sessions that a broker currently owns.
     *
     * CREATE TABLE MQSES<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      ID              BIGINT NOT NULL,
     *      BROKER_ID	    VARCHAR(100) NOT NULL,
     *      IS_CURRENT      INTEGER NOT NULL,
     *      CREATED_BY      VARCHAR(100) NOT NULL,
     *      CREATED_TS      BIGINT NOT NULL,
     *      PRIMARY KEY(ID)
     * );
     *
     * ID - Unique store session ID associated with this run of the broker
     * BROKER_ID - Broker ID that owns or responsible for routing the messages
     *  associated with this session
     * IS_CURRENT - Specify whether the session is current
     * CREATED_BY_COLUMN - Broker ID that creates this session
     * CREATED_TS_COLUMN - Timestamp when the session created
     */
    public static final String TABLE = "MQSES";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String ID_COLUMN = "ID";
    public static final String BROKER_ID_COLUMN = "BROKER_ID";
    public static final String IS_CURRENT_COLUMN = "IS_CURRENT";
    public static final String CREATED_BY_COLUMN = "CREATED_BY";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    long insert( Connection conn, String brokerID, long sessionID, boolean failExist )
        throws BrokerException;

    void insert( Connection conn, String brokerID, long sessionID,
                 int isCurrent, String createdBy, long createdTS )
        throws BrokerException;

    void delete( Connection conn, long id ) throws BrokerException;

    void deleteByBrokerID( Connection conn, String brokerID )
        throws BrokerException;

    List<Long> deleteInactiveStoreSession( Connection conn ) throws BrokerException;

    List<Long> takeover( Connection conn, String brokerID, String targetBrokerID )
        throws BrokerException;

    long getStoreSession( Connection conn, String brokerID )
        throws BrokerException;

    String getStoreSessionOwner( Connection conn, long sessionID )
        throws BrokerException;

    boolean ifOwnStoreSession( Connection conn, long id, String brokerID )
        throws BrokerException;

    String getStoreSessionCreator( Connection conn, long sessionID )
        throws BrokerException;

    Map getAllStoreSessions( Connection conn ) throws BrokerException;

    List<Long> getStoreSessionsByBroker( Connection conn, String brokerID )
        throws BrokerException;

    boolean isCurrent( Connection conn, long sessionID ) throws BrokerException;

    void moveStoreSession( Connection conn, long sessionID, 
                   String targetBrokerID ) throws BrokerException;

}
