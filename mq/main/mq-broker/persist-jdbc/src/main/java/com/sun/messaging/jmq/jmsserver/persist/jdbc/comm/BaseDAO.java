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
 * @(#)BaseDAO.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.HashMap;

/**
 * This class is an interface for the base DAO.
 */
public interface BaseDAO {

    /**
     * Get the prefix name of the table.
     * @return table name
     */
    public String getTableNamePrefix();

    /**
     * Get the name of the table.
     * @return table name
     */
    public String getTableName();

    /**
     * Create the table.
     * @param conn database connection
     * @throws BrokerException
     */
    public void createTable( Connection conn ) throws BrokerException;

    public void createStoredProc( Connection conn ) throws BrokerException;

    public void dropStoredProc( Connection conn ) throws BrokerException;


    /**
     * Drop the table.
     * @param conn database connection
     * @throws BrokerException
     */
    public void dropTable( Connection conn ) throws BrokerException;

    /**
     * Delete all entries.
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll( Connection conn ) throws BrokerException;

    /**
     * Get row count.
     * @param conn database connection
     * @param whereClause the where clause for the SQL command
     * @return the number of rows in a query
     */
    public int getRowCount( Connection conn, String whereClause )
        throws BrokerException;

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn );
}
