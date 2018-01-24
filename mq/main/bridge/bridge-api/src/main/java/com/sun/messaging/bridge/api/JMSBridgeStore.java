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

package com.sun.messaging.bridge.api;

import java.util.List;
import java.util.Set;

/**
 * Interface for JDBC persist service to JMS Bridge 
 *
 * @author amyk
 */
public interface JMSBridgeStore {

    /**
     * Store a log record
     *
     * @param xid the global XID 
     * @param logRecord the log record data for the xid
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception DupKeyException if already exist 
     *            else Exception on error
     */
    public void storeTMLogRecord(String xid, byte[] logRecord,
                                 String name, boolean sync,
                                 java.util.logging.Logger logger_)
                                 throws DupKeyException, Exception;

    /**
     * Update a log record
     *
     * @param xid the global XID 
     * @param logRecord the new log record data for the xid
     * @param name the jmsbridge name 
     * @param callback to obtain updated data if not null
     * @param addIfNotExist
     * @param sync - not used
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void updateTMLogRecord(String xid, byte[] logRecord, String name,
                                  UpdateOpaqueDataCallback callback,
                                  boolean addIfNotExist,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception;

    /**
     * Remove a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void removeTMLogRecord(String xid, String name,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception;
    /**
     * Get a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name 
     * @param logger_ can be null 
     * @return null if not found
     * @exception Exception if error
     */
    public byte[] getTMLogRecord(String xid, String name,
                                 java.util.logging.Logger logger_)
                                 throws Exception;


    /**
     * Get last update time of a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordUpdatedTime(String xid,  String name,
                                          java.util.logging.Logger logger_)
                                          throws KeyNotFoundException, Exception;

    /**
     * Get a log record creation time
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordCreatedTime(String xid, String name,
                                          java.util.logging.Logger logger_)
                                          throws Exception;

    /**
     * Get all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @return a list of log records
     * @exception Exception if error
     */
    public List getTMLogRecordsByName(String name, 
                                      java.util.logging.Logger logger_)
                                      throws Exception;
    /**
     * Get keys for all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @return a list of keys
     * @exception Exception if error
     */
    public List getTMLogRecordKeysByName(String name, 
                                         java.util.logging.Logger logger_)
                                         throws Exception;

    /********************************************************
     * Methods used only under HA mode by JMS bridge
     ********************************************************/

    /**
     * Add a JMS Bridge 
     *
     * @param name jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception DupKeyException if already exist 
     *            else Exception on error
     */
    public void addJMSBridge(String name, boolean sync,
                             java.util.logging.Logger logger_)
                             throws DupKeyException, Exception;

    /**
     * Get JMS bridges owned by this broker 
     *
     * @param name jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @return a list of names
     * @exception Exception if error
     */
    public List getJMSBridges(java.util.logging.Logger logger_)
                             throws Exception;


    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return updated time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeUpdatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception;

    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return created time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeCreatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception;

    public void closeJMSBridgeStore() throws Exception;
}
