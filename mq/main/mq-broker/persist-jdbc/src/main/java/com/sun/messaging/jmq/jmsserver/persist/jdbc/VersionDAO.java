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

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;

import java.sql.Connection;

/**
 * This class is an interface for the Version table which will be implemented by database specific code.
 */
public interface VersionDAO extends BaseDAO {

    /**
     * Version table: Version information about the current store.
     *
     * CREATE TABLE MQVER<schemaVersion>[C<clusterID>|S<brokerID>] ( STORE_VERSION INTEGER NOT NULL, LOCK_ID VARCHAR(100));
     *
     * STORE_VERSION - Version of this store LOCK_ID - Identifier of the broker or imqdbmgr that is currently using the store
     */
    String TABLE = "MQVER";
    String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    String STORE_VERSION_COLUMN = "STORE_VERSION";
    String LOCK_ID_COLUMN = "LOCK_ID";

    void insert(Connection conn, int storeVersion) throws BrokerException;

    boolean updateLock(Connection conn, int storeVersion, String newLockID, String oldLockID) throws BrokerException;

    int getStoreVersion(Connection conn) throws BrokerException;

    String getLock(Connection conn, int storeVersion) throws BrokerException;
}
