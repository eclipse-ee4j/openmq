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

package com.sun.messaging.bridge.service.jms;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit; 
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledFuture; 
import java.util.concurrent.ConcurrentLinkedQueue; 
import java.util.concurrent.ScheduledExecutorService; 
import javax.jms.XAConnection;
import javax.jms.Connection;
import javax.jms.JMSException;
import com.sun.messaging.bridge.service.jms.xml.JMSBridgeXMLConstant;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 *
 * @author amyk
 *
 */
public class PooledConnectionFactory implements Runnable {
    
    public static final String POOL_IDLE_TIMEOUT = "pool-idle-timeout";
    
    private Logger _logger = null;
    private Object _cf = null;
    private int _maxRetries = 0;
    private int _retryInterval = 0;

    private ScheduledExecutorService _scheduler = null;

    private ScheduledFuture _future = null;
 
    private int _idleTimeout = 0; //secs

    private ConcurrentLinkedQueue<PooledConnection> _idleConns = null;
    private ConcurrentLinkedQueue<PooledConnection> _outConns = null;
    private final EventNotifier _notifier = new EventNotifier();
    private boolean _closed = false;

    private String _username = null;
    private String _password = null;

    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public PooledConnectionFactory(Object cf, Properties attrs, Logger logger) throws Exception {
        _logger = logger;
        _cf = cf;

        String val = attrs.getProperty(JMSBridgeXMLConstant.CF.USERNAME);
        if (val != null) {
            _username = val.trim();
            _password = attrs.getProperty(JMSBridgeXMLConstant.CF.PASSWORD);
        }
        
        val = attrs.getProperty(JMSBridgeXMLConstant.CF.IDLETIMEOUT,
                                JMSBridgeXMLConstant.CF.IDLETIMEOUT_DEFAULT);
        if (val != null) {
            _idleTimeout = Integer.parseInt(val);
        }
        if (_idleTimeout < 0) _idleTimeout = 0;
        val = attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTS,
                                JMSBridgeXMLConstant.CF.CONNECTATTEMPTS_DEFAULT);
        if (val != null) {
            _maxRetries = Integer.parseInt(val);
        }
        val = attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL,
                                JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT);
        if (val != null) {
            _retryInterval = Integer.parseInt(val);
        }
        if (_retryInterval < 0) _retryInterval = 0;

        _idleConns = new ConcurrentLinkedQueue<PooledConnection>();
        _outConns = new ConcurrentLinkedQueue<PooledConnection>();
 
        _scheduler = Executors.newSingleThreadScheduledExecutor(); 
        if (_idleTimeout > 0) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_SCHEDULE_TIMEOUT_FOR_POOLCF,
                                                   _idleTimeout, this.toString()));
            _future = _scheduler.scheduleAtFixedRate(this, _idleTimeout, 
                                          _idleTimeout, TimeUnit.SECONDS);
        }
    }

    /** 
     */ 
    public Connection obtainConnection(Connection c, 
                                       String logstr,
                                       Object caller, boolean doReconnect) 
                                       throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Obtaining pooled connection from pooled connection factory "+this);
        }

        if (_closed)  {
            if (c == null) {
            throw new JMSException(_jbr.getKString(_jbr.X_POOLED_CF_CLOSED, this.toString()));
            }
            try {
            c.close();
            } catch (Exception e) {
            _logger.log(Level.WARNING, "Unable to close connection in pooled connection factory "+this);
            }
            throw new JMSException(_jbr.getKString(_jbr.X_POOLED_CF_CLOSED, this.toString()));
        }

        PooledConnection pconn = null;
        if (c != null) {
            if (c instanceof XAConnection) {
                pconn = new PooledXAConnectionImpl((XAConnection)c);
            } else {
                pconn = new PooledConnectionImpl(c);
            }
            _idleConns.offer(pconn);
        }

        while (true) {

        pconn =  _idleConns.poll();
        if (pconn == null) {
            if (_closed) {
               throw new JMSException(_jbr.getKString(_jbr.X_POOLED_CF_CLOSED, this.toString()));
            }
            Connection cn = null;
            EventListener l = new EventListener(this);
            try {
            _notifier.addEventListener(EventListener.EventType.CONN_CLOSE, l);
            cn = JMSBridge.openConnection(_cf, _maxRetries, _retryInterval, _username, _password,
                                          logstr, caller, l, _logger, doReconnect);
            } finally { 
            _notifier.removeEventListener(l);
            }
            if (cn instanceof XAConnection) { 
                pconn = new PooledXAConnectionImpl((XAConnection)cn);
            } else {
                pconn = new PooledConnectionImpl(cn);
            }
        } 
        if (!_closed && pconn.isValid()) {
            pconn.idleEnd();
            _outConns.offer(pconn);
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, 
                "Obtained pooled connection "+pconn+" from pooled connection factory "+this);
            }
            return (Connection)pconn;
        }
        try {
             if (_closed) {
                _logger.log(Level.INFO, 
                "Closing connection "+pconn+" for pooled connection factory "+this+" is closed");
             } else if (!pconn.isValid()) {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_INVALID_CONN_IN_POOLCF,
                                                       pconn.toString(), this.toString()));
             }

             ((Connection)pconn).close();
        } catch (Exception e) {
            _logger.log(Level.WARNING, 
            "Unable to close connection "+pconn+" in pooled connection factory "+this+": "+ e.getMessage());
        }

        } //while
    }

    /** 
     */ 
    public void returnConnection(Connection conn) 
                               throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Returning pooled connection "+conn+" to pooled connection factory "+this);
        }
        if (!(conn instanceof PooledConnection)) {
           throw new IllegalArgumentException(
           "Connection "+conn+" is not a pooled connection, can't return to pooled connection factory "+this);
        }
        if (!_outConns.contains(conn)) {
           throw new IllegalStateException(
           "Connection "+conn+" is not a in-use in pooled connection factory "+this);
        }

        _outConns.remove((PooledConnection)conn);
        ((PooledConnection)conn).idleStart();
        _idleConns.offer((PooledConnection)conn);

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Returned pooled connection "+conn+" to pooled connection factory "+this);
        }
    }

    public void close() {
        _closed = true;
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_POOLCF, this.toString()));

        _notifier.notifyEvent(EventListener.EventType.CONN_CLOSE, this);
        if (_outConns.size() > 0) {
            _logger.log(Level.WARNING, _jbr.getString(_jbr.W_FORCE_CLOSE_POOLCF, this.toString()));
        }
        if (_future != null) {
            _scheduler.shutdownNow();
            try {
            _scheduler.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {}
        }
        run();

    }

    public void run() {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Check idle timeout in pooled connection factory "+this);
        }
        ArrayList list = new ArrayList();
        PooledConnection c = _idleConns.peek();
        list.add(c);
        while (c != null) {

            long idlestime = c.getIdleStartTime();
            if (idlestime <= 0 && c.isValid() && !_closed) continue;

            c = _idleConns.poll();
            if (c == null) return;

            if (!c.isValid() || 
                (System.currentTimeMillis() - idlestime > _idleTimeout) || _closed) {

                _logger.log(Level.INFO, (c.isValid() ? 
                        _jbr.getString(_jbr.I_CLOSE_TIMEOUT_CONN_IN_POOLCF, c.toString(), this.toString()):
                        _jbr.getString(_jbr.I_CLOSE_INVALID_CONN_IN_POOLCF, c.toString(), this.toString())));

                try {
                    ((Connection)c).close();
                } catch (Exception e) {
                    _logger.log(Level.WARNING, 
                   "Failed to close "+(_closed ? "":(c.isValid() ? "idle timed out connection ":"invalid connection "))+c+
                  " in pooled connection factory "+this);
                   c.invalid();
                   _idleConns.offer(c);
                }
            } else {
                _idleConns.offer(c);
            }
            c =_idleConns.peek();
            if (list.contains(c)) break;
            list.add(c);
        }
    }

    public Object getCF() {
        return _cf;
    }

    public int getIdleTimeout() {
        return _idleTimeout;
    }

    public int getMaxRetries() {
        return _maxRetries;
    }

    public int getRetryInterval() {
        return _retryInterval;
    }

    public int getNumIdleConns() {
        return _idleConns.size();
    }

    public int getNumInUseConns() {
        return _outConns.size();
    }

    public String toString() {
        return _cf+"["+_outConns.size()+", "+_idleConns.size()+"]";
    }
}

