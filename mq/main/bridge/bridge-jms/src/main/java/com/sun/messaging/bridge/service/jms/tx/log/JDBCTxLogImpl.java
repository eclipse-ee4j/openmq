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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;
import com.sun.messaging.bridge.service.jms.tx.BranchXid;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.api.UpdateOpaqueDataCallback;
import com.sun.messaging.bridge.service.jms.JMSBridge;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 *
 * @author amyk
 */

public class JDBCTxLogImpl extends TxLog {

    private static final String _type = TxLog.JDBCTYPE;
 
    private JMSBridgeStore _store = null;

    private boolean _inited = false;
    private boolean _closed = false;

    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public void setJDBCStore(JMSBridgeStore store) 
                    throws IllegalArgumentException {
        if (store == null) {
            throw new IllegalArgumentException("null JDBC store");
        }
        _store = store;
    }

    public String getType() {
        return _type;
    }

    /**
     * @param props The properties is guaranteed to contain 
     *              "txlogDir", "txlogSuffix", 
     *              "txlogMaxBranches", "jmsbridge"
     * @param reset true to reset the txlog
     */
    public void init(Properties props, boolean reset) throws Exception {
        if (_store == null) {
            throw new IllegalStateException("JDBC store is null");
        }
        if (_logger == null) {
            throw new IllegalArgumentException("logger not set");
        }

        _jmsbridge = props.getProperty("jmsbridge");
        if (_jmsbridge == null) {
            throw new IllegalArgumentException("Property 'jmsbridge' not set");
        }
        _tmname = props.getProperty("tmname");
        if (_tmname == null) {
            throw new IllegalStateException("Property 'tmname' not set");
        }

        super.init(props, reset);

        _inited = true;
    }

    /**
     * @param lr the LogRecord to log
     */
    public void logGlobalDecision(LogRecord lr) throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "jdbcTxLog: log global decision  "+lr);
        }

		super.checkClosedAndSetInProgress();
        try {

        _store.storeTMLogRecord(lr.getGlobalXid().toString(),
                                lr.toBytes(), _jmsbridge, false, _logger);
        } finally {
        super.setInProgress(false);
        }
    }

    /**
     * @param bxid the branch xid to update 
     * @param lr the LogRecord to identify the record to update 
     */
    public void logHeuristicBranch(BranchXid bxid, LogRecord lr) throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "jdbcTxLog: log branch heuristic decision  "+lr);
        }

        final GlobalXid gxid = lr.getGlobalXid();
        final BranchXid branchXid = bxid;
        final LogRecord newlr = lr;

		super.checkClosedAndSetInProgress();
        try {

        UpdateOpaqueDataCallback callback = new UpdateOpaqueDataCallback() {
                         
            public Object update(Object currlr) throws Exception { 
                ObjectInputStream ois =  new FilteringObjectInputStream(
                                         new ByteArrayInputStream((byte[])currlr)); 
                LogRecord oldlr = (LogRecord)ois.readObject();
                if (oldlr == null) {
                    throw new IllegalArgumentException(
                    "Unexpected null current log record for "+gxid); 
                }
                if (!oldlr.getGlobalXid().equals(gxid)) {
                    throw new IllegalArgumentException(
                    "Unexpected global xid "+oldlr.getGlobalXid()+" from store, expected:"+gxid);
                }
                ois.close(); 
                
                if (oldlr.getBranchDecision(branchXid) == 
                    newlr.getBranchDecision(branchXid)) {
                    return currlr;
                }
                oldlr.setBranchDecision(branchXid, newlr.getBranchDecision(branchXid));
                return oldlr;
            }
        };
        _store.updateTMLogRecord(gxid.toString(), lr.toBytes(), _jmsbridge, 
                                 callback, true, true, _logger);
        } finally {
        super.setInProgress(false);
        }
    }
    
    /**
     * @param gxid the global xid record to remove 
     */
    public void reap(String gxid) throws Exception {
        String key = gxid;

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "jdbcTxLog: remove global transaction xid "+key);
        }

		super.checkClosedAndSetInProgress();
        try {

        _store.removeTMLogRecord(key, _jmsbridge, true, _logger);

        } finally {
        super.setInProgress(false);
        }
    } 


    /**
     *
     * @param gxid the GlobalXid
     * @return a copy of LogRecord corresponding gxid or null if not exist
     */
    public LogRecord getLogRecord(GlobalXid gxid) throws Exception {
        String key = gxid.toString();

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "jdbcTxLog: get log record for  xid "+key);
        }

		super.checkClosedAndSetInProgress();
        try {

        byte[] data = _store.getTMLogRecord(key, _jmsbridge, _logger);

        if (data == null) return null;

        ObjectInputStream ois =  new FilteringObjectInputStream(
                                 new ByteArrayInputStream(data)); 
        LogRecord lr = (LogRecord)ois.readObject();
        ois.close(); 
        return lr;

        } finally {
        super.setInProgress(false);
        }

    }

    public List getAllLogRecords() throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "jdbcTxLog: get all log records");
        }

        ArrayList<LogRecord> list = new ArrayList<LogRecord>();

        super.checkClosedAndSetInProgress();
        try {

        List abytes = _store.getTMLogRecordsByName(_jmsbridge, _logger);
        if (abytes == null) return list;

        byte[] data = null;
        Iterator<byte[]> itr = abytes.iterator();
        while (itr.hasNext()) {
            data = itr.next();
            ObjectInputStream ois =  new FilteringObjectInputStream(
                                     new ByteArrayInputStream(data));
            LogRecord lr = (LogRecord)ois.readObject();
            ois.close();
            list.add(lr);
        }
        return list;

        } finally {
        super.setInProgress(false);
        }

    }

    /**
     */
    public void close() throws Exception {
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_JDBCTXNLOG_CLOSE));

        super.setClosedAndWait();
        super.close();
    }
}
