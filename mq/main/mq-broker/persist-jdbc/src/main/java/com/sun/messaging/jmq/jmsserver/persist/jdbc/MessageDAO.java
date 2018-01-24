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
 * @(#)MessageDAO.java	1.24 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;

import java.sql.Connection;
import java.io.IOException;
import java.util.List;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is an interface for the Message table which will be implemented
 * by database specific code.
 */
public interface MessageDAO extends BaseDAO {

    /**
     * Message table:
     * Holds persisted messages.
     *
     * CREATE TABLE IMQMSG<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      ID                  VARCHAR(100),
     *      MESSAGE             LONGVARBINARY,
     *      MESSAGE_SIZE        BIGINT,
     *      STORE_SESSION_ID    BIGINT NOT NULL,
     *      DESTINATION_ID      VARCHAR(100),
     *      TRANSACTION_ID      BIGINT,
     *      CREATED_TS          BIGINT,
     *      PRIMARY KEY(ID)
     * );
     *
     * ID - String format of SysMessageID for the message
     * MESSAGE - Wire format of message packet
     * MESSAGE_SIZE - Byte count of this message
     * STORE_SESSION_ID - Store session ID associated with the Broker
     *  responsible for routing the message
     * DESTINATION_ID - Unique name of the Destination of the message
     * TRANSACTION_ID - Transaction ID associated with an acknowledgement
     * 	(Sent when a message has sent in a transaction but not committed)
     * CREATED_TS - Timestamp when the message is created
     */
    public static final String TABLE = "MQMSG";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String ID_COLUMN = "ID";
    public static final String SYSMESSAGE_ID_COLUMN = "SYSMESSAGE_ID";
    public static final String MESSAGE_COLUMN = "MESSAGE";
    public static final String MESSAGE_SIZE_COLUMN = "MESSAGE_SIZE";
    public static final String STORE_SESSION_ID_COLUMN = "STORE_SESSION_ID";
    public static final String DESTINATION_ID_COLUMN = "DESTINATION_ID";
    public static final String TRANSACTION_ID_COLUMN = "TRANSACTION_ID";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    void insert( Connection conn, DestinationUID dstUID, Packet message,
        ConsumerUID[] consumerUIDs, int[] states, long storeSessionID,
        long createdTime, boolean checkMsgExist, boolean replaycheck )
        throws BrokerException;

    void insert( Connection conn, String dstID, Packet message, 
        ConsumerUID[] consumerUIDs, int[] states, long storeSessionID,
        long createdTime, boolean checkMsgExist, boolean replaycheck )
        throws BrokerException;

    /**
     * This method is for special case where ID column is found
     * corrupted for a message after loaded from the database table  
     * however the packet in MESSAGE column is found intact
     */
    void repairCorruptedSysMessageID( Connection conn, 
        SysMessageID realSysId, String badSysIdStr, String duidStr)
        throws BrokerException;

    void moveMessage( Connection conn, Packet message,
        DestinationUID from, DestinationUID to, ConsumerUID[] consumerUIDs,
        int[] states ) throws IOException, BrokerException;

    void delete( Connection conn, DestinationUID dstUID, String id, boolean replaycheck )
        throws BrokerException;

    int deleteByDestinationBySession( Connection conn, 
        DestinationUID dstUID, Long storeSession)
        throws BrokerException;

    String getBroker( Connection conn, DestinationUID dstUID, String id )
        throws BrokerException;

    Packet getMessage( Connection conn, DestinationUID dstUID, SysMessageID sysMsgID )
        throws BrokerException;

    Packet getMessage( Connection conn, DestinationUID dstUID, String id )
        throws BrokerException;

    List getMessagesByBroker( Connection conn, String brokerID )
        throws BrokerException;

    Map<String, String> getMsgIDsAndDstIDsByBroker( Connection conn, String brokerID )
        throws BrokerException;

    List getIDsByDst( Connection conn, Destination dst, String brokerID, Long storeSession )
        throws BrokerException;

    Enumeration messageEnumeration( Destination dst, String brokerID, Long storeSession  ) 
        throws BrokerException;

    Enumeration messageEnumerationCursor( Destination dst, String brokerID, Long storeSession ) 
        throws BrokerException;

    boolean hasMessageBeenAcked( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID ) throws BrokerException;
    
    boolean hasMessage( Connection conn, String id ) throws BrokerException;

    void checkMessage( Connection conn, String dstID, String id )
        throws BrokerException;

    int getMessageCount( Connection conn, String brokerID )
        throws BrokerException;

    HashMap getMessageStorageInfo( Connection conn, Destination dst, Long storeSession )
        throws BrokerException;
}
