/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import com.sun.messaging.jmq.jmsserver.persist.jdbc.DBConstants;

/**
 * This class is an interface for JMS Bridges table
 *
 * @author amyk
 */
public interface JMSBGDAO extends BaseDAO {

    /**
     * TMLogRecord table: Holds all the txlog records
     *
     * CREATE TABLE MQJMSBG<schemaVersion>[C<clusterID>|S<brokerID>] ( NAME VARCHAR(100) NOT NULL,\ BROKER_ID VARCHAR(100)
     * NOT NULL,\ CREATED_TS DOUBLE INTEGER NOT NULL,\ UPDATED_TS DOUBLE INTEGER NOT NULL,\ PRIMARY KEY(NAME))
     *
     * NAME - jmsbridge name BROKER_ID - The Broker ID who owns the jmsbridge CREATED_TS_COLUMN - Timestamp when the entry
     * is created UPDATED_TS_COLUMN - Timestamp when the entry was last updated
     */
    String TABLE = "MQJMSBG";
    String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    String NAME_COLUMN = "NAME";
    String BROKER_ID_COLUMN = "BROKER_ID";
    String CREATED_TS_COLUMN = "CREATED_TS";
    String UPDATED_TS_COLUMN = "UPDATED_TS";

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     */
    void insert(Connection conn, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param name to identify the TM
     * @param logger_ can be null;
     */
    void updateBrokerId(Connection conn, String name, String newBrokerId, String expectedBrokerId, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     */
    void delete(Connection conn, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return brokerId
     */
    String getBrokerId(Connection conn, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return updated time
     */
    long getUpdatedTime(Connection conn, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return created time
     */
    long getCreatedTime(Connection conn, String name, java.util.logging.Logger logger_) throws Exception;

    /**
     * Get JMS bridge names owned by a broker
     *
     * @param conn database connection
     * @param logger_ can be null;
     * @return list of names owned by the brokerId
     */
    List getNamesByBroker(Connection conn, String brokerID, java.util.logging.Logger logger_) throws Exception;

}
