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
import java.util.Properties;
import java.util.List;

/**
 * This class is an interface for the Property table which will be implemented by database specific code.
 */
public interface PropertyDAO extends BaseDAO {

    /**
     * Property table: General purpose name/value pair. One row per property name/value pair. Used to hold cluster related
     * properties.
     *
     * CREATE TABLE MQPROP<schemaVersion>[C<clusterID>|S<brokerID>] ( PROPNAME VARCHAR(100) NOT NULL, PROPVALUE
     * LONGVARBINARY, PRIMARY KEY(PROPNAME) );
     *
     * PROPNAME - property name PROPVALUE - property value; serialized object
     */
    String TABLE = "MQPROP";
    String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    String PROPNAME_COLUMN = "PROPNAME";
    String PROPVALUE_COLUMN = "PROPVALUE";

    void update(Connection conn, String name, Object value) throws BrokerException;

    void delete(Connection conn, String name) throws BrokerException;

    Object getProperty(Connection conn, String name) throws BrokerException;

    List getPropertyNames(Connection conn) throws BrokerException;

    Properties getProperties(Connection conn) throws BrokerException;

    boolean hasProperty(Connection conn, String propName) throws BrokerException;
}
