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
 * @(#)ConsumerStateDAO.java	1.18 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.io.SysMessageID;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

/**
 * This class is an interface for the Consumer State table which will be
 * implemented by database specific code.
 */
public interface ConsumerStateDAO extends BaseDAO {

    /**
     * Consumer State table:
     * This table is used to handle processing of message acknowledgements.
     * Associates a message to a consumer it was sent to and tracks the
     * acknowledgement state. For durable subscription and queue receivers
     * only. Unique Key is MESSAGE_ID + CONSUMER_ID.
     *
     * CREATE TABLE MQCONSTATE<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      MESSAGE_ID          VARCHAR(100) NOT NULL,
     *      CONSUMER_ID         BIGINT NOT NULL,
     *      STATE               INTEGER,
     *      TRANSACTION_ID      BIGINT,
     *      CREATED_TS          BIGINT NOT NULL
     * );
     *
     * MESSAGE_ID - SysMessageID for the message
     * CONSUMER_ID - Long value of the ConsumerUID of the consumer object
     * STATE - State of the consumer with respect to the message
     * TRANSACTION_ID - Long value of the TransactionUID associated with an
     * 	acknowledgement (sent when a message has been acknowledged but not committed)
     * CREATED_TS - Timestamp when the entry was created
     */
    public static final String TABLE = "MQCONSTATE";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String MESSAGE_ID_COLUMN = "MESSAGE_ID";
    public static final String CONSUMER_ID_COLUMN = "CONSUMER_ID";
    public static final String STATE_COLUMN = "STATE";
    public static final String TRANSACTION_ID_COLUMN = "TRANSACTION_ID";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    void insert( Connection conn, String dstID, SysMessageID sysMsgID,
        ConsumerUID[] consumerUIDs, int[] states, boolean checkMsgExist, boolean replaycheck )
        throws BrokerException;

    void updateState( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID, ConsumerUID consumerUID, int state, boolean replaycheck )
        throws BrokerException;

    void updateState( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID, ConsumerUID consumerUID, int newState,
        int expectedState ) throws BrokerException;

    void updateTransaction( Connection conn, SysMessageID sysMsgID,
        ConsumerUID consumerUID, TransactionUID txnUID ) throws BrokerException;

    void clearTransaction( Connection conn, TransactionUID txnUID )
        throws BrokerException;

    void deleteByMessageID( Connection conn, String id )
        throws BrokerException;

    public void deleteByTransaction( Connection conn, TransactionUID txnUID )
        throws BrokerException;

    void deleteByDestinationBySession( Connection conn, 
        DestinationUID dstUID, Long storeSession )
        throws BrokerException;

    int getState( Connection conn, SysMessageID sysMsgID,
        ConsumerUID consumerUID ) throws BrokerException;

    HashMap getStates( Connection conn, SysMessageID sysMsgID )
        throws BrokerException;

    long getTransaction( Connection conn, SysMessageID sysMsgID,
        ConsumerUID consumerUID ) throws BrokerException;

    List getConsumerUIDs( Connection conn, SysMessageID sysMsgID )
        throws BrokerException;

    List getTransactionAcks( Connection conn, TransactionUID txnUID )
        throws BrokerException;

    HashMap getAllTransactionAcks( Connection conn ) throws BrokerException;
}
