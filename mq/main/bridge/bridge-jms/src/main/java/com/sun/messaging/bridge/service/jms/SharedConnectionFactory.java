/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.bridge.service.jms;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.XAConnection;
import jakarta.jms.JMSException;
import com.sun.messaging.bridge.service.jms.xml.JMSBridgeXMLConstant;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;
import lombok.Getter;

/**
 *
 * @author amyk
 *
 */

public class SharedConnectionFactory implements Runnable {

    private Logger _logger = null;

    @Getter
    private Object cF = null;

    @Getter
    private int idleTimeout = 0; // in secs

    @Getter
    private int maxRetries = 0;

    @Getter
    private int retryInterval = 0;

    private ScheduledExecutorService _scheduler = null;

    private final ReentrantLock _lock = new ReentrantLock();
    private final Condition _refcnt0 = _lock.newCondition();
    private SharedConnection _conn = null;

    @Getter
    private int refCount = 0;
    private ScheduledFuture _future = null;
    private boolean _closed = false;

    private String _username = null;
    private String _password = null;

    private EventNotifier _notifier = new EventNotifier();
    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public SharedConnectionFactory(Object cf, Properties attrs, Logger logger) {
        cF = cf;
        _logger = logger;

        String val = attrs.getProperty(JMSBridgeXMLConstant.CF.USERNAME);
        if (val != null) {
            _username = val.trim();
            _password = attrs.getProperty(JMSBridgeXMLConstant.CF.PASSWORD);
        }
        idleTimeout = Integer.parseInt(attrs.getProperty(JMSBridgeXMLConstant.CF.IDLETIMEOUT, JMSBridgeXMLConstant.CF.IDLETIMEOUT_DEFAULT));
        if (idleTimeout < 0) {
            idleTimeout = 0;
        }
        maxRetries = Integer.parseInt(attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTS, JMSBridgeXMLConstant.CF.CONNECTATTEMPTS_DEFAULT));
        retryInterval = Integer
                .parseInt(attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL, JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT));
        if (retryInterval < 0) {
            retryInterval = 0;
        }

        _scheduler = Executors.newSingleThreadScheduledExecutor();

    }

    public Connection obtainConnection(Connection c, String logstr, Object caller, boolean doReconnect) throws Exception {
        _lock.lockInterruptibly();
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Obtaining shared connection from shared connection factory " + this);
        }
        if (_closed) {
            if (c == null) {
                throw new JMSException(_jbr.getString(_jbr.X_SHARED_CF_CLOSED, this.toString()));
            }
            try {
                c.close();
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Unable to close conneciton from shared connection factory " + this);
            }
            throw new JMSException(_jbr.getString(_jbr.X_SHARED_CF_CLOSED, this.toString()));
        }
        if (c != null) {
            if (c instanceof XAConnection) {
                _conn = new SharedXAConnectionImpl((XAConnection) c);
            } else {
                _conn = new SharedConnectionImpl(c);
            }
        }
        if (_conn != null && !_conn.isValid()) {
            try {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_INVALID_CONN_IN_SHAREDCF, c.toString(), this.toString()));
                ((Connection) _conn).close();
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Unable to close invalid connection " + _conn + " from shared connection factory " + this);
            }
            _conn = null;
        }
        try {
            if (_conn == null) {
                Connection cn = null;
                EventListener l = new EventListener(this);
                try {
                    _notifier.addEventListener(EventListener.EventType.CONN_CLOSE, l);
                    cn = JMSBridge.openConnection(cF, maxRetries, retryInterval, _username, _password, logstr, caller, l, _logger, doReconnect);
                } finally {
                    _notifier.removeEventListener(l);
                }
                if (cF instanceof XAConnectionFactory) {
                    _conn = new SharedXAConnectionImpl((XAConnection) cn);
                } else {
                    _conn = new SharedConnectionImpl(cn);
                }
            }
            if (_closed) {
                try {
                    ((Connection) _conn).close();
                } catch (Exception e) {
                    _logger.log(Level.FINE, "Exception on closing connection " + _conn + ": " + e.getMessage());
                }
                _conn = null;
                throw new JMSException(_jbr.getString(_jbr.X_SHARED_CF_CLOSED, this.toString()));
            }
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Increment refcnt in shared connection factory " + this);
            }
            refCount++;
            if (_future != null) {
                _future.cancel(true);
                _future = null;
            }
        } finally {
            _lock.unlock();
        }
        return (Connection) _conn;
    }

    public void returnConnection(Connection conn) throws Exception {
        _lock.lock();
        try {
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Decrement refcnt in shared connection factory " + this);
            }
            refCount--;
            assert refCount >= 0;
            if (refCount == 0 && idleTimeout > 0) {
                if (_future != null) {
                    _future.cancel(true);
                }
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_SCHEDULE_TIMEOUT_FOR_SHAREDCF, idleTimeout, this.toString()));
                _future = _scheduler.schedule(this, idleTimeout, TimeUnit.SECONDS);
            }
            if (refCount == 0) {
                _refcnt0.signalAll();
            }
        } finally {
            _lock.unlock();
        }
    }

    @Override
    public void run() {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Check idle timeout in shared connection factory " + this);
        }
        try {
            _lock.lockInterruptibly();
        } catch (Throwable t) {
            _logger.log(Level.WARNING, "Unable to get lock for idle connection check in shared connection factory " + this + ": " + t.getMessage());
            return;
        }
        try {
            if (refCount > 0) {
                return;
            }
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_TIMEOUT_CONN_IN_SHAREDCF, _conn.toString(), this.toString()));
            try {
                ((Connection) _conn).close();
            } catch (Throwable t) {
                _logger.log(Level.WARNING,
                        "Exception in closing idle timed out shared connection: " + t.getMessage() + " in shared connection factory " + this);
            } finally {
                _conn = null;
            }
        } finally {
            _lock.unlock();
        }
    }

    public void close() throws Exception {
        _closed = true;

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_SHAREDCF, this.toString()));

        _notifier.notifyEvent(EventListener.EventType.CONN_CLOSE, this);
        _scheduler.shutdownNow();

        boolean done = false;
        try {
            _lock.lockInterruptibly();
            try {

                if (_conn == null) {
                    return;
                }

                if (refCount > 0) {
                    /**
                     * should not happen, all links/dmqs have been closed by now we want to close as soon as possible, no need to wait
                     */
                    _logger.log(Level.WARNING, "Force close shared connection factory " + this + " with outstanding reference count " + refCount);
                }
                ((Connection) _conn).close();
                done = true;

            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in closing shared connection " + e.getMessage());

            } finally {
                _lock.unlock();
            }

        } finally {
            Connection c = (Connection) _conn;
            try {
                if (c != null && !done) {
                    c.close();
                }
            } catch (Exception e) {
                _logger.log(Level.FINE, "Exception in closing shared connection " + c + ": " + e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return cF + "[" + refCount + "]";
    }

}
