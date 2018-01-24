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
 * @(#)ConfigRecordDAO.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;

import java.sql.Connection;
import java.util.List;

/**
 * This class is an interface for the Configuration Change Record table
 * which will be implemented by database specific code.
 */
public interface ConfigRecordDAO extends BaseDAO {

    /**
     * Configuration Change Record table:
     * Holds change record; used by master broker only.
     *
     * CREATE TABLE MQCREC<schemaVersion>[C<clusterID>|S<brokerID>] (
     *      RECORD          LONGVARBINARY NOT NULL,
     *      CREATED_TS      BIGINT NOT NULL
     * );
     *
     * RECORD - Configuration Record
     * CREATED_TS - Timestamp when the entry was created
     */
    public static final String TABLE = "MQCREC";
    public static final String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    public static final String RECORD_COLUMN = "RECORD";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";

    void insert( Connection conn, byte[] recordData, long timeStamp )
        throws BrokerException;

    List<ChangeRecordInfo> getRecordsSince( Connection conn, long timestamp ) throws BrokerException;

    List<ChangeRecordInfo> getAllRecords( Connection conn ) throws BrokerException;
}
