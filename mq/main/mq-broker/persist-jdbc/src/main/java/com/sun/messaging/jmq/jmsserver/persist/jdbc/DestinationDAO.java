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
 * @(#)DestinationDAO.java	1.12 07/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;

import java.sql.Connection;
import java.util.List;

/**
 * This class is an interface for the Destination table which will be implemented
 * by database specific code.
 */
public interface DestinationDAO extends BaseDAO {

    /**
     * Destination table:
     * Holds all the destination in the system.
     *
     * CREATE TABLE MQDST<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      ID                  VARCHAR(100) NOT NULL,
     *      DESTINATION         LONGVARBINARY NOT NULL,
     *      IS_LOCAL            INTEGER NOT NULL,
     *      CONNECTION_ID       BIGINT,
     *      CONNECTED_TS        BIGINT,
     *      STORE_SESSION_ID    BIGINT,
     *      CREATED_TS          BIGINT NOT NULL,
     *      PRIMARY KEY(ID)
     * );
     *
     * ID - Unique name of the Destination object
     * DESTINATION - Serialized Destination object
     * IS_LOCAL - Specify whether the destination is local
     * CONNECTION_ID - Connection ID for temporary destination
     * CONNECTED_TS - Timestamp when a temporary destination was created or
     *      when a consumer connected to the destination
     * STORE_SESSION_ID - Store session ID associated with the temporary destination
     * CREATED_TS - Timestamp when the entry was created
     */
    public static final String TABLE = "MQDST";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String ID_COLUMN = "ID";
    public static final String DESTINATION_COLUMN = "DESTINATION";
    public static final String IS_LOCAL_COLUMN = "IS_LOCAL";
    public static final String CONNECTION_ID_COLUMN = "CONNECTION_ID";
    public static final String CONNECTED_TS_COLUMN = "CONNECTED_TS";
    public static final String STORE_SESSION_ID_COLUMN = "STORE_SESSION_ID";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    void insert( Connection conn, Destination destination, long storeSessionID,
        long connectedTime, long createdTime ) throws BrokerException;

    void update( Connection conn, Destination destination )
        throws BrokerException;
    
    void updateConnectedTime( Connection conn, Destination destination,
        long connectedTime ) throws BrokerException;

    boolean delete( Connection conn, DestinationUID dstUID, int type )
        throws BrokerException;

    boolean delete( Connection conn, Destination destination, Long storeSessionID )
        throws BrokerException;

    List getAllDestinations( Connection conn, String brokerID )
        throws BrokerException;

    List getLocalDestinationsByBroker( Connection conn, String brokerID )
        throws BrokerException;

    Destination getDestination( Connection conn, String destName )
        throws BrokerException;

    long getDestinationConnectedTime( Connection conn, String destName )
        throws BrokerException;

    void checkDestination( Connection conn, String destName )
        throws BrokerException;

    boolean hasDestination( Connection conn, String destName )
        throws BrokerException;
}
