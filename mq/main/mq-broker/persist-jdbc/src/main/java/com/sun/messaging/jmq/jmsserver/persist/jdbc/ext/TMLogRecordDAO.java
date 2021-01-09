/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.ext;

import java.util.List;
import java.sql.Connection;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.bridge.api.DupKeyException;
import com.sun.messaging.bridge.api.KeyNotFoundException;
import com.sun.messaging.bridge.api.UpdateOpaqueDataCallback;

/**
 * This class is an interface for XA transaction manager txlog that is used by JMS Bridge
 *
 * @author amyk
 */
public interface TMLogRecordDAO extends BaseDAO {

    /**
     * TMLogRecord table: Holds all the txlog records
     *
     * CREATE TABLE MQTMLR??<schemaVersion>[C<clusterID>|S<brokerID>] ( XID VARCHAR(256) NOT NULL,\ LOG_RECORD
     * VARBINARY(2048) NOT NULL,\ NAME VARCHAR(100) NOT NULL,\ BROKER_ID VARCHAR(100) NOT NULL,\ CREATED_TS DOUBLE INTEGER
     * NOT NULL,\ UPDATED_TS DOUBLE INTEGER NOT NULL,\ PRIMARY KEY(XID))
     *
     * XID - Global XID LOG_RECORD - Log record data NAME - the jmsbridge name BROKER_ID - The Broker ID CREATED_TS_COLUMN -
     * Timestamp when the record is created UPDATED_TS_COLUMN - Timestamp when the record was last updated
     */
    String XID_COLUMN = "XID";
    String LOG_RECORD_COLUMN = "LOG_RECORD";
    String NAME_COLUMN = "NAME";
    String BROKER_ID_COLUMN = "BROKER_ID";
    String CREATED_TS_COLUMN = "CREATED_TS";
    String UPDATED_TS_COLUMN = "UPDATED_TS";

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param logRecord log record data
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @throws DupKeyException if already exist else Exception on error
     */
    void insert(Connection conn, String xid, byte[] logRecord, String name, java.util.logging.Logger logger_) throws DupKeyException, Exception;

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param logRecord log record data
     * @param name the jmsbridge name
     * @param callback to obtain updated data
     * @param addIfNotExist
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found and addIfNotExist false else Exception on error
     */
    void updateLogRecord(Connection conn, String xid, byte[] logRecord, String name, UpdateOpaqueDataCallback callback, boolean addIfNotExist,
            java.util.logging.Logger logger_) throws KeyNotFoundException, Exception;

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found else Exception on error
     */
    void delete(Connection conn, String xid, String name, java.util.logging.Logger logger_) throws KeyNotFoundException, Exception;

    /**
     * Delete all by jmsbridge name for this broker
     *
     * @param conn database connection
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found else Exception on error
     */
    void deleteAllByName(Connection conn, String name, java.util.logging.Logger logger_) throws KeyNotFoundException, Exception;

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @return null if not found
     * @throws Exception
     */
    byte[] getLogRecord(Connection conn, String xid, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found else Exception on error
     */
    long getUpdatedTime(Connection conn, String xid, String name, java.util.logging.Logger logger_) throws KeyNotFoundException, Exception;

    /**
     * @param conn database connection
     * @param xid the global xid
     * @param name the jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found else Exception on error
     */
    long getCreatedTime(Connection conn, String xid, String name, java.util.logging.Logger logger_) throws KeyNotFoundException, Exception;

    /**
     * @param conn database connection
     * @param name the jmsbridge name
     * @param brokerID
     * @param logger_ can be null;
     * @return a list of log records
     * @throws Exception
     */
    List getLogRecordsByNameByBroker(Connection conn, String name, String brokerID, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param brokerID
     * @param logger_ can be null;
     * @return a list of names in all log records owned by the brokerID
     * @throws Exception
     */
    List getNamesByBroker(Connection conn, String brokerID, java.util.logging.Logger logger_) throws Exception;

}
