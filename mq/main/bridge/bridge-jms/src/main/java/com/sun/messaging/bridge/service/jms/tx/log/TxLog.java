/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;
import com.sun.messaging.bridge.service.jms.tx.BranchXid;
import com.sun.messaging.bridge.api.BridgeException;

/**
 *
 * @author amyk
 */

public abstract class TxLog {

    public static final String FILETYPE = "file";
    public static final String JDBCTYPE = "jdbc";
    public static final String FILECLASS = "com.sun.messaging.bridge.service.jms.tx.log.FileTxLogImpl";
    public static final String JDBCCLASS = "com.sun.messaging.bridge.service.jms.tx.log.JDBCTxLogImpl";
    public static final int DEFAULT_REAPLIMIT = 50;
    public static final int DEFAULT_REAPINTERVAL = 60; // 1min

    Logger _logger = null;

    private boolean _closed = false;
    private Object _closedLock = new Object();
    private int _inprogressCount = 0;
    private Object _inprogressLock = new Object();

    private TransactionReaper _reaper = null;
    private int _reapLimit = DEFAULT_REAPLIMIT;
    private int _reapInterval = DEFAULT_REAPINTERVAL;

    protected String _tmname = null;
    protected String _jmsbridge = null;

    public void setLogger(Logger logger) {
        _logger = logger;
    }

    public Logger getLogger() {
        return _logger;
    }

    /**
     * @param props The properties is guaranteed to contain "txlogDir", "txlogSuffix", "txlogMaxBranches"
     * @param reset true to reset the txlog
     */
    public void init(Properties props, boolean reset) throws Exception {
        if (props != null) {
            _jmsbridge = props.getProperty("jmsbridge");
            _tmname = props.getProperty("tmname");
            String v = props.getProperty("txlogReapLimit");
            if (v != null) {
                _reapLimit = Integer.parseInt(v);
            }
            v = props.getProperty("txlogReapInterval");
            if (v != null) {
                _reapInterval = Integer.parseInt(v);
            }
        }
        if (_jmsbridge == null) {
            throw new IllegalArgumentException("Property 'jmsbridge' not set");
        }
        if (_tmname == null) {
            throw new IllegalStateException("Property 'tmname' not set");
        }
        _reaper = new TransactionReaper(this, _reapLimit, _reapInterval);
    }

    public String getTMName() {
        return _tmname;
    }

    /**
     * @param lr the LogRecord to log
     */
    public abstract void logGlobalDecision(LogRecord lr) throws Exception;

    /**
     * @param lr the LogRecord to identify the record to update
     * @param bxid the branch xid to update
     */
    public abstract void logHeuristicBranch(BranchXid bxid, LogRecord lr) throws Exception;

    /**
     *
     * @param gxid the GlobalXid
     * @return a copy of LogRecord corresponding gxid or null if not exist
     */
    public abstract LogRecord getLogRecord(GlobalXid gxid) throws Exception;

    /**
     * @return a list of all log records
     */
    public abstract List getAllLogRecords() throws Exception;

    /**
     * @param gxid the global xid record to remove
     */
    public void remove(GlobalXid gxid) throws Exception {
        _reaper.addTransaction(gxid);
    }

    /**
     * @param gxid the global xid record to remove
     */
    public abstract void reap(String gxid) throws Exception;

    public boolean isClosed() {
        return _closed;
    }

    /**
     * subclass override
     */
    public void close() throws Exception {
        synchronized (_closedLock) {
            if (!isClosed()) {
                _closed = true;
            }
        }
        if (_reaper != null) {
            _reaper.destroy();
        }
    }

    // The following methods are similar as in Store.java
    protected void setClosedAndWait() {
        synchronized (_closedLock) {
            _closed = true;
        }
        synchronized (_inprogressLock) {
            while (_inprogressCount > 0) {
                try {
                    _inprogressLock.wait();
                } catch (Exception e) {
                }
            }
        }
    }

    protected void checkClosedAndSetInProgress() throws Exception {
        synchronized (_closedLock) {
            if (_closed) {
                String emsg = "Accessing TM txLog after store closed";
                throw new BridgeException(emsg, Status.UNAVAILABLE);
            } else {
                setInProgress(true);
            }
        }
    }

    protected void setInProgress(boolean flag) {
        synchronized (_inprogressLock) {
            if (flag) {
                _inprogressCount++;
            } else {
                _inprogressCount--;
            }

            if (_inprogressCount == 0) {
                _inprogressLock.notifyAll();
            }
        }
    }
}

