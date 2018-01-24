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
 * @(#)DBConnectionPool.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SupportUtil;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.FaultInjection;

import java.io.IOException;
import java.sql.*;
import javax.sql.PooledConnection;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.InvocationTargetException;

/**
 * The DBConnection class represents a pool of connections to one database.
 */
public class DBConnectionPool {

    private static boolean DEBUG = false;

    private static final String REAP_INTERVAL_PROP_SUFFIX = ".connection.reaptime";
    private static final int DEFAULT_REAP_INTERVAL = 300; // secs

    private static final int DEFAULT_POLL_TIMEOUT= 180; //secs
    private static final String POLL_TIMEOUT_PROP_SUFFIX = ".connection.pollTimeout";

    private static final String TIMEOUT_IDLE_PROP_SUFFIX = ".connection.timeoutIdle";

    //deprecated
    public static final String NUM_CONN_PROP_SUFFIX = ".connection.limit";

    private static final String MIN_CONN_PROP_SUFFIX = ".min_connections";
    private static final String MAX_CONN_PROP_SUFFIX = ".max_connections";
    private static final int DEFAULT_NUM_CONN = 5;

    /**
     * a SQL SELECT statement that returns at least one row.
     */
    private static final String VALIDATION_QUERY_PROP_SUFFIX = ".connection.validationQuery";

    public static final String VALIDATE_ON_GET_PROP_SUFFIX =".connection.validateOnGet";
    public static final String INVALIDATE_ALL_PROP_SUFFIX =".connection.invalidateAll";

    private int minConnections;
    private int maxConnections;
    private int pollTimeout = DEFAULT_POLL_TIMEOUT;

    private boolean initialized = false;
    private ReentrantLock lock = new ReentrantLock();

    private LinkedBlockingQueue<ConnectionInfo> idleConnections = 
                               new LinkedBlockingQueue<ConnectionInfo>();
    private  ConcurrentHashMap<ConnectionInfo, Thread> activeConnections = 
                              new ConcurrentHashMap<ConnectionInfo, Thread>();

    private Map<Object, ConnectionInfo> connMap = Collections.synchronizedMap(
                                        new HashMap<Object, ConnectionInfo>());

    private ConnectionReaperTask connectionReaper = null;
    private ConnectionEventListener connectionListener = null;
    private long reapInterval = DEFAULT_REAP_INTERVAL*1000L;

    private CommDBManager dbmgr = null;
    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();

    private String validationQuery = null;
    private boolean validateOnGet = false;
    private boolean invalidateAll = false;
    private boolean timeoutIdle = true;
    private boolean isPoolDataSource = false;
    private String name = null;
    private boolean dedicated = false;

    private Object invalidateAllTimestampLock = new Object();
    private Long invalidateAllTimestamp = null; 

    private ConfigListener cfgListener = new ConfigListener() {
        public void validate(String name, String value)
            throws PropertyUpdateException {
            if (name.equals(dbmgr.getJDBCPropPrefix()+MIN_CONN_PROP_SUFFIX)) {
                int min = 0;
                try {
                    min = Integer.parseInt(value);
                } catch (Exception e) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, br.getString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
                }
                if (min < 1) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting,
                        "A minimum value of 1 connection is required");
                } else if (min > maxConnections) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting,
                        "Minimum connections " + min +
                        " is greater than maximum connections " + maxConnections);
                }
            } else if (name.equals(dbmgr.getJDBCPropPrefix()+MAX_CONN_PROP_SUFFIX)) {
                int max = 0;
                try {
                    max = Integer.parseInt(value);
                } catch (Exception e) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, br.getString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
                }
                if (max < minConnections) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting,
                        "Maximum connections " + max +
                        " is less than minimum connections " + minConnections);
                }
            } else if (name.equals(dbmgr.getJDBCPropPrefix()+POLL_TIMEOUT_PROP_SUFFIX)) {
                try {
                    Integer.parseInt(value);
                } catch (Exception e) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, br.getString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
                }
            } else if (name.equals(dbmgr.getJDBCPropPrefix()+REAP_INTERVAL_PROP_SUFFIX)) {
                int reaptime = 0;
                try {
                    reaptime = Integer.parseInt(value);
                } catch (Exception e) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, br.getString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
                }
                if (reaptime < 60) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting,
                        "A minimum value of 60 seconds is required for reap time interval");
                }
            }
        }

        public boolean update(String name, String value) {
            BrokerConfig cfg = Globals.getConfig();

            long newReapInterval = reapInterval;
            lock.lock();
            try {
                if (name.equals(dbmgr.getJDBCPropPrefix()+MAX_CONN_PROP_SUFFIX)) {
                    maxConnections = cfg.getIntProperty(dbmgr.getJDBCPropPrefix()+MAX_CONN_PROP_SUFFIX);
                } else if (name.equals(dbmgr.getJDBCPropPrefix()+MIN_CONN_PROP_SUFFIX)) {
                    minConnections = cfg.getIntProperty(dbmgr.getJDBCPropPrefix()+MIN_CONN_PROP_SUFFIX);
                } else if (name.equals(dbmgr.getJDBCPropPrefix()+POLL_TIMEOUT_PROP_SUFFIX)) {
                    pollTimeout = cfg.getIntProperty(dbmgr.getJDBCPropPrefix()+POLL_TIMEOUT_PROP_SUFFIX);
                } else if (name.equals(dbmgr.getJDBCPropPrefix()+REAP_INTERVAL_PROP_SUFFIX)) {
                    newReapInterval = cfg.getLongProperty(dbmgr.getJDBCPropPrefix()+REAP_INTERVAL_PROP_SUFFIX)*1000L;
                } else if (name.equals(dbmgr.getJDBCPropPrefix()+INVALIDATE_ALL_PROP_SUFFIX)) {
                    invalidateAll = cfg.getBooleanProperty(dbmgr.getJDBCPropPrefix()+INVALIDATE_ALL_PROP_SUFFIX);
                    logger.log(logger.INFO, dbmgr.getJDBCPropPrefix()+INVALIDATE_ALL_PROP_SUFFIX+"="+invalidateAll);
                } else if (name.equals(dbmgr.getJDBCPropPrefix()+VALIDATE_ON_GET_PROP_SUFFIX)) {
                    validateOnGet = cfg.getBooleanProperty(dbmgr.getJDBCPropPrefix()+VALIDATE_ON_GET_PROP_SUFFIX);
                    logger.log(logger.INFO, dbmgr.getJDBCPropPrefix()+VALIDATE_ON_GET_PROP_SUFFIX+"="+validateOnGet);
                }
            } finally {
                lock.unlock();
            }

            if (newReapInterval != reapInterval) {
                reapInterval = newReapInterval;
                logger.log(logger.INFO, dbmgr.getJDBCPropPrefix()+
                    REAP_INTERVAL_PROP_SUFFIX+"="+reapInterval/1000);
                ConnectionReaperTask task = connectionReaper;
                if (task != null) {
                    task.cancel(); 
                }
                connectionReaper = new ConnectionReaperTask();
                Globals.getTimer().schedule(connectionReaper,
                                   reapInterval, reapInterval);
            }

            return true;
        }
    };

    /**
     * Establish a pool of database connections.
     */
    public DBConnectionPool(CommDBManager mgr, String name)
    throws BrokerException {
       this(mgr, name, false);
    }

    public DBConnectionPool(CommDBManager mgr, String name, boolean dedicated)
    throws BrokerException {

        if ( !initialized ) {
            lock.lock();
            try {
                if ( initialized ) {
                    return;
                }

                dbmgr = mgr;
                this.name = name;
                this.dedicated = dedicated;
                isPoolDataSource = dbmgr.isPoolDataSource();

                String key = dbmgr.getJDBCPropPrefix()+VALIDATION_QUERY_PROP_SUFFIX;
                validationQuery = Globals.getConfig().getProperty(key);

                if (validationQuery != null && validationQuery.trim().length() == 0) {
                    validationQuery = null;
                }
                initValidationQuery();
                if (validationQuery != null) {
                    logger.log(logger.INFO, key+"="+validationQuery);
                }

                key = dbmgr.getJDBCPropPrefix()+VALIDATE_ON_GET_PROP_SUFFIX;
                validateOnGet = Globals.getConfig().getBooleanProperty(key, Globals.getHAEnabled());
                logger.log(logger.INFO, key+"="+validateOnGet);

                key = dbmgr.getJDBCPropPrefix()+INVALIDATE_ALL_PROP_SUFFIX;
                invalidateAll = Globals.getConfig().getBooleanProperty(key, Globals.getHAEnabled());
                logger.log(logger.INFO, key+"="+invalidateAll);

                key = dbmgr.getJDBCPropPrefix()+TIMEOUT_IDLE_PROP_SUFFIX;
                timeoutIdle = Globals.getConfig().getBooleanProperty(key, true);
                logger.log(logger.INFO, key+"="+timeoutIdle);

                if (!dedicated) {
                    // Check deprecated "imq.persist.jdbc.connection.limit" property
                    key = dbmgr.getJDBCPropPrefix()+NUM_CONN_PROP_SUFFIX;
                    int numConnections = Globals.getConfig().getIntProperty(key, DEFAULT_NUM_CONN);

                    if (numConnections < 1) {
                        numConnections = DEFAULT_NUM_CONN;
                        logger.log(Logger.WARNING,
                            "Invalid number of connections specified, set to default of " +
                             numConnections+toString());
                    }

                    key = dbmgr.getJDBCPropPrefix()+MIN_CONN_PROP_SUFFIX;
                    minConnections = Globals.getConfig().getIntProperty(key, numConnections);

                    if (minConnections < 1) {
                        minConnections = numConnections;
                        logger.log(Logger.WARNING, 
                            "Invalid number of minimum connections specified, set to default of " +
                            minConnections+toString());
                    }

                    key = dbmgr.getJDBCPropPrefix()+MAX_CONN_PROP_SUFFIX;
                    maxConnections = Globals.getConfig().getIntProperty(key, numConnections);

                    if (maxConnections < minConnections) {
                        maxConnections = minConnections;
                        logger.log(Logger.WARNING, 
                            "Invalid number of maximum connections specified, set to default of " +
                            maxConnections+toString());
                    }
                } else {
                    minConnections = 1;
                    maxConnections = 2;
                }
                key = dbmgr.getJDBCPropPrefix()+POLL_TIMEOUT_PROP_SUFFIX;
                pollTimeout = Globals.getConfig().getIntProperty(key, DEFAULT_POLL_TIMEOUT);

                key = dbmgr.getJDBCPropPrefix()+REAP_INTERVAL_PROP_SUFFIX;
                long reapTime = Globals.getConfig().getLongProperty(key, DEFAULT_REAP_INTERVAL);

                if (reapTime < 60) {
                    reapTime = DEFAULT_REAP_INTERVAL;
                    logger.log(Logger.WARNING,
                        "Invalid reap time interval for pool maintenance thread specified, set to default of " +
                        reapTime+toString());
                }
                logger.log(logger.INFO, key+"="+reapTime);
                
                reapInterval = reapTime * 1000L;

                // With embedded DB, if autocreate store is enabled then we
                // need to create the DB now; otherwise we run into a chicken and
                // egg problem because we will not be able to create a connection
                // to check if the store exists.               
                if (dbmgr.getCreateDBURL() != null &&
                    Globals.getConfig().getBooleanProperty(
                        dbmgr.getCreateStoreProp(),
                        dbmgr.getCreateStorePropDefault())) {
                    try {
                        Connection conn = dbmgr.connectToCreate();
                        conn.close();
                    } catch (Exception e) {
                        String url = dbmgr.getCreateDBURL();
                        String emsg = br.getKString(
                            BrokerResources.E_CREATE_DATABASE_TABLE_FAILED, url);
                        logger.log(Logger.ERROR, emsg+toString(), e);
                        throw new BrokerException(emsg, e);
                    }
                }
                connectionListener = new DBConnectionListener();

                if (!dedicated) {
                    logger.log(logger.INFO, dbmgr.getJDBCPropPrefix()+
                               MIN_CONN_PROP_SUFFIX+"="+minConnections);
                    logger.log(logger.INFO, dbmgr.getJDBCPropPrefix()+
                               MAX_CONN_PROP_SUFFIX+"="+maxConnections);
                }

                for (int i = 0; i < minConnections; i++) {
                    ConnectionInfo cinfo = createConnection();
                    idleConnections.offer(cinfo);
                }
                if (!dedicated) {                
                    // Registerd listener so we can dynamically changed pool value
                    Globals.getConfig().addListener(dbmgr.getJDBCPropPrefix()+
                                            MIN_CONN_PROP_SUFFIX, cfgListener);
                    Globals.getConfig().addListener(dbmgr.getJDBCPropPrefix()+
                                            MAX_CONN_PROP_SUFFIX, cfgListener);
                    Globals.getConfig().addListener(dbmgr.getJDBCPropPrefix()+
                                            REAP_INTERVAL_PROP_SUFFIX, cfgListener);
                }
                Globals.getConfig().addListener(dbmgr.getJDBCPropPrefix()+
                                            POLL_TIMEOUT_PROP_SUFFIX, cfgListener);
                Globals.getConfig().addListener(dbmgr.getJDBCPropPrefix()+
                                            INVALIDATE_ALL_PROP_SUFFIX, cfgListener);

                // Start connection reaper to remove excess connections and idle connections
                if (connectionReaper != null) {
                    connectionReaper.cancel(); // Cancel the old reaper task
                }
                connectionReaper = new ConnectionReaperTask();
                Globals.getTimer().schedule(connectionReaper, reapInterval, reapInterval);

                initialized = true;
            } finally {
                lock.unlock();
            }
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("initialized", String.valueOf(initialized));
        ht.put("minConnections", String.valueOf(minConnections));
        ht.put("maxConnections", String.valueOf(maxConnections));
        ht.put("reapInterval", String.valueOf(reapInterval));
        ht.put("timeoutIdle", Boolean.valueOf(timeoutIdle));
        ht.put("validateQuery", Boolean.valueOf(validationQuery));
        ht.put("validateOnGet", Boolean.valueOf(validateOnGet));
        ht.put("invalidateAll", Boolean.valueOf(invalidateAll));
        ht.put("isPoolDataSource", Boolean.valueOf(isPoolDataSource));
        ht.put("activeConnections.size", String.valueOf(activeConnections.size()));
        ht.put("idleConnections.size", String.valueOf(idleConnections.size()));
        return ht;
    }

    /**
     * Closes all available connections. Should be called when the broker is
     * shutting down and all store operations are done so there should not
     * be any connection in the activeConnections list.
     */
    public void close() {

        if (!initialized) {
	    return;
        }

        lock.lock();
        try {
            if (connectionReaper != null) {
                connectionReaper.cancel();
                connectionReaper = null;
            }

            Globals.getConfig().removeListener(
                dbmgr.getJDBCPropPrefix()+MIN_CONN_PROP_SUFFIX, cfgListener);
            Globals.getConfig().removeListener(
                dbmgr.getJDBCPropPrefix()+MAX_CONN_PROP_SUFFIX, cfgListener);
            Globals.getConfig().removeListener(
                dbmgr.getJDBCPropPrefix()+POLL_TIMEOUT_PROP_SUFFIX, cfgListener);
            Globals.getConfig().removeListener(
                dbmgr.getJDBCPropPrefix()+REAP_INTERVAL_PROP_SUFFIX, cfgListener);

            // Close all connections
            Iterator<ConnectionInfo> itr = idleConnections.iterator();
            while (itr.hasNext()) {
                ConnectionInfo cinfo = itr.next();
                destroyConnection(cinfo);
            }

            idleConnections.clear();

            initialized = false;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        return "("+name+")["+activeConnections.size()+","+idleConnections.size()+"]";
    }
    /**
     * Recreates all the connections.
     * 
     * Should be used to remove stale connections after the DB is restarted.
     */
    public void reset() throws BrokerException {
        
        if (DEBUG) {
	    logger.log(Logger.INFO, toString()+".reset");
        }
                
        if (!initialized) {
	    return;
        }
        
        Collection<ConnectionInfo> oldConnections = new ArrayList<ConnectionInfo>(maxConnections);
        
        lock.lock();
        try {                       
            activeConnections.clear();
            idleConnections.drainTo(oldConnections); 
            
             // Recreates the connections
            for (int i = 0; i < minConnections; i++) {
                ConnectionInfo cinfo = createConnection();
                idleConnections.offer(cinfo);
            }
            
            // Now, close the old connections
            Iterator<ConnectionInfo> itr = oldConnections.iterator();
            while (itr.hasNext()) {
                ConnectionInfo cinfo = itr.next();
                destroyConnection(cinfo);
            }
        } finally {
            lock.unlock();
        }        
    }

    /**
     */
    private ConnectionInfo createConnection() throws BrokerException {
        Object conn = dbmgr.getNewConnection();
        ConnectionInfo cinfo = new ConnectionInfo(conn, connectionListener);
        connMap.put(conn, cinfo);
        return cinfo;
    }

    /**
     */ 
    private void destroyConnection(ConnectionInfo cinfo) {
        if (DEBUG) {
            logger.log(logger.INFO, "DBConnectionPool.destroyConnection "+cinfo);
        }
        cinfo.destroy();
        connMap.remove(cinfo.getKey());
    }

    /**
     * Checks out a connection from the pool.
     * @throws BrokerException
     */
    public Connection getConnection() throws BrokerException {

        if (DEBUG) {
            logger.log(Logger.INFO, "["+Thread.currentThread()+"]"+toString()+".getConnection");
        }

        if (DEBUG) {
            FaultInjection fi = FaultInjection.getInjection();
            if (fi.FAULT_INJECTION) {
                fi.checkFaultAndSleep(FaultInjection.FAULT_JDBC_GETCONN_1, null);
            }
        }
        
        Connection conn = null;

        boolean createdNew = false, pollWait = false;
        ConnectionInfo cinfo = (ConnectionInfo)idleConnections.poll();
        if (cinfo == null && (activeConnections.size() < maxConnections)) {
            cinfo = createConnection();
            try {
                conn = cinfo.getConnection();
            } catch (Exception e) {
                destroyConnection(cinfo);
                throw new BrokerException(cinfo+e.getMessage(), e);
            }
            if (DEBUG) {
                createdNew = true;
            }
        } else {
            // Wait until a connetion is free up
            while (cinfo == null) {
                try {
                    if (DEBUG) {
                        if (!pollWait) pollWait = true;
                    }

                    int pollto = pollTimeout;
                    if (BrokerStateHandler.isStoreShutdownStage1()) {
                        throw new BrokerException(br.getKString(br.W_DB_POOL_CLOSING, name));
                    }
                    if (BrokerStateHandler.getShutdownThread() == Thread.currentThread()) {
                        pollto = 60;
                    }
                    
                    int slept = 0;
                    while (!BrokerStateHandler.isStoreShutdownStage1() &&
                           (pollto <= 0 || slept < pollto)) {
                        if (slept != 0 && (slept%15 == 0)) {    
                            logger.log(logger.INFO, br.getKString(
                                br.I_DB_POOL_POLL_WAIT, Thread.currentThread())+toString());
                        }
                        cinfo = (ConnectionInfo)idleConnections.poll(1, TimeUnit.SECONDS);
                        if (cinfo != null) {
                            break;
                        }
                        if (BrokerStateHandler.isStoreShutdownStage1()) {
                            throw new BrokerException(br.getKString(br.W_DB_POOL_CLOSING, name));
                        }
                        cinfo = (ConnectionInfo)idleConnections.poll();
                        if (cinfo != null) {
                            break;
                        }
                        if (activeConnections.size() < maxConnections) {
                            cinfo = createConnection();
                            if (DEBUG) {
                                createdNew = true;
                            }
                            break;
                        }
                        slept++;
                        if (slept%60 == 0 || (pollto > 0 && slept == pollto)) {
                            StringBuffer buff = new StringBuffer(1024);
                            Iterator itr = activeConnections.entrySet().iterator();
                            while (itr.hasNext()) {
                                Map.Entry e = (Map.Entry)itr.next();
                                Thread t = (Thread)e.getValue();
                                buff.append("\n")
                                    .append(t.getName())
                                    .append(": using connection: ")
                                    .append(e.getKey());
                                StackTraceElement[] trace = t.getStackTrace();
                                for (int i=0; i < trace.length; i++) {
                                   buff.append("\n\tat " + trace[i]);
                                }
                            }
                            String emsg = br.getKTString(br.I_DB_CONN_POLL_TIMEOUT,
                                "("+activeConnections.size()+","+idleConnections.size()+
                                ")["+minConnections+","+maxConnections+"]",
                                 String.valueOf(slept))+"\n"+buff.toString(); 
                            logger.log(Logger.WARNING, emsg+toString());
                        }
                    }
                    if (cinfo == null) {
                        throw new BrokerException(br.getKTString(br.W_DB_POOL_POLL_TIMEOUT));
                    }
                } catch (Exception e) {
                    if (e instanceof BrokerException) {
                        throw (BrokerException)e;
                    }
                    if (DEBUG) {
                        logger.logStack(Logger.INFO, toString()+
                            ".getConnection: "+e.getMessage(), e);
                    }
                } 
            }

            boolean valid = true;
            if (getInvalidateAllTimestamp(cinfo.getCreationTime()) != null) {
                valid = false;
            }

            if (!valid || !validateConnection(cinfo, validateOnGet, true)) {
                 destroyConnection(cinfo);

                try {
                    cinfo = createConnection();
                    conn = cinfo.getConnection();

                    logger.log(Logger.INFO, br.getKString(
                        BrokerResources.I_RECONNECT_TO_DB, 
                        ""+cinfo, dbmgr.getOpenDBURL())+toString());
                } catch (Exception e) {
                    destroyConnection(cinfo);
                    String emsg = br.getString(
                        BrokerResources.X_RECONNECT_TO_DB_FAILED,
                        dbmgr.getOpenDBURL());
                    logger.logStack(Logger.ERROR, emsg+toString(), e);
                    throw new BrokerException(emsg, e);
                }
            } else {
                try {
                    conn = cinfo.getConnection();
                } catch (Exception e) {
                    destroyConnection(cinfo);
                    throw new BrokerException(cinfo+e.getMessage(), e);
                }
            }

        }

        // move the connection in the activeConnections list
        Thread borrower = Thread.currentThread();
        activeConnections.put(cinfo, borrower);

        if (DEBUG) {
            logger.log(Logger.INFO, toString()+".getConnection["+createdNew+","+pollWait+"]: " +
                borrower.getName() + " [" + new Date() +
                "]: check out connection: 0x" + conn.hashCode()+cinfo);
        }

        return conn;
    }

    /**
     * Checks in a connection to the pool.
     */
    public void freeConnection(Connection conn, Throwable ex) {

        if (DEBUG) {
	    logger.log(Logger.INFO, toString()+".freeConnection: connection: 0x"+
                    conn.hashCode()+(ex == null ? "":", ex="+ex));
        }

        boolean destroy = false;
        Throwable cause = ex;
        if (ex instanceof BrokerException) {
            cause  = ex.getCause();
        }
        if ((cause instanceof SQLException) || 
            (cause instanceof IOException)) {
            if (DEBUG) {
                logger.logStack(Logger.INFO, 
                    br.getKString(br.I_DB_CONN_EX_TOBE_DESTROYED,
                    "0x"+conn.hashCode(), cause.toString())+toString(), cause);
            } else {
                logger.log(Logger.INFO, 
                    br.getKString(br.I_DB_CONN_EX_TOBE_DESTROYED,
                    "0x"+conn.hashCode(), cause.toString())+toString());
            }
            destroy = true;
        }

        if (!destroy && isPoolDataSource) {
            try {
                conn.close();
                return;
            } catch (Throwable e) {
                logger.log(logger.WARNING,
                    br.getKString(br.W_DB_CONN_CLOSE_EXCEPTION,
                    "0x"+conn.hashCode(), e.toString())+toString());
                ex = e;
                destroy = true;
            }
        }

        ConnectionInfo cinfo = connMap.get(conn);

        if (cinfo == null) {
            logger.log(logger.WARNING,
                br.getKString(br.W_DB_CONN_RETURN_UNKNOWN,
                "0x"+conn.hashCode())+toString());
            try {
                conn.close();
            } catch (Exception e) {
                logger.log(logger.WARNING, 
                    br.getKString(br.W_DB_CONN_CLOSE_EXCEPTION,
                    "0x"+conn.hashCode(), e.toString())+toString());
            }
            return;
        }
        cinfo.setException(ex);
        returnConnection(cinfo, ex, destroy);
    }

    /**
     * @param destroy if true, only to be called from 
     *        connectionErrorOccurred for PooledConnection 
     */
    private void returnConnection(ConnectionInfo cinfo, Throwable ex, boolean destroy) {
        if (DEBUG) {
	    logger.log(Logger.INFO, toString()+".returnConnection: connection: "+cinfo+
            (ex == null ? "":", ex="+ex)+(!destroy ? "":", destroy="+destroy));
        }
        if (destroy && Util.isConnectionError(ex, dbmgr, false)) {
            setInvalidateAllTimestamp();
        }

        Thread thread = activeConnections.remove(cinfo);

        if (thread == null) {
            if (destroy) {
                logger.log(Logger.INFO, br.getKString(
                    br.I_DB_DESTROY_INACTIVE_CONN, 
                    cinfo.toString(), ex.toString())+toString());
                 
                if (!idleConnections.remove(cinfo)) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, toString()+".returnConnection: "+
                        "Destroy an inactive/non-idle database connection "+
                         cinfo.toString());
                    }
                }
            } else {
                if (DEBUG) {
                    logger.log(Logger.WARNING, toString()+".returnConnection("+
                    cinfo+(ex == null ? "":", ex="+ex)+
                    "): not found in connection pool\n"+ SupportUtil.getStackTrace(""));
                } else {
                    logger.log(Logger.WARNING,
                    br.getKString(br.W_DB_CONN_RETURN_NOT_FOUND_INPOOL,
                    ""+cinfo+"["+(ex == null ? "":", ex="+ex)+ "]")+toString());
                }
            }
            destroyConnection(cinfo);

        } else {

            if (destroy) {
                logger.log(Logger.INFO, br.getKString(
                    br.I_DB_DESTROY_ACTIVE_CONN,
                    cinfo.toString(), ex.toString())+toString());
                destroyConnection(cinfo);
                return;
            }
            if (ex != null) { 
                if (!validateConnection(cinfo, 
                                        (ex instanceof SQLException) ||
                                        (ex.getCause() instanceof SQLException), false)) {
                    destroyConnection(cinfo);
                    return;
                }
            }
            if (invalidateAll &&
                getInvalidateAllTimestamp(cinfo.getCreationTime()) != null) { 
                destroyConnection(cinfo);
                return;
            }
            cinfo.idleStart();
            idleConnections.offer(cinfo);
        }
    }

    private void setInvalidateAllTimestamp() {
        Long ts = Long.valueOf(System.currentTimeMillis());
        synchronized(invalidateAllTimestampLock) {
            invalidateAllTimestamp = ts;
        }
    }

    private Long getInvalidateAllTimestamp(long creationTime) {
        synchronized(invalidateAllTimestampLock) {
            if (invalidateAllTimestamp == null) {
                return null;
            }
            if (creationTime > invalidateAllTimestamp.longValue()) {
                invalidateAllTimestamp = null;
                return null;
            }
            return invalidateAllTimestamp;
        }
    }

    private void initValidationQuery() throws BrokerException {

        if (dbmgr.isMysql()) {
            validationQuery = "/* ping */";

        } else if (dbmgr.isOracle()) {
            validationQuery = "SELECT 1 FROM DUAL";

        } else if (validationQuery == null && dbmgr.isStoreInited()) {
            try {
                validationQuery = "SELECT 1 FROM "+ 
                    ((BaseDAO)dbmgr.getFirstDAO()).getTableName();
            } catch (Exception e) {}
        } 
    }

    /**
     * @return true if valid connection 
     */
    private boolean validateConnection(ConnectionInfo cinfo,
                                       boolean ping, boolean get) {

        boolean doping = ping;

        Connection conn = null;
        boolean destroyIdles = false;

        cinfo.setValidating(true);
        try {
            Object o = cinfo.getKey();
            if (o instanceof Connection) {
                if (((Connection)o).isClosed()) {
                    return false;
                }
                if (Util.isConnectionError(cinfo.getException(), dbmgr)) {
                    return false;
                }
            } else if (cinfo.getException() != null) {
                return false;
            }
            if (get &&
                ((System.currentTimeMillis()-cinfo.getIdleStartTime())
                 >= reapInterval)) {
                if (timeoutIdle) {
                    return false;
                }
                doping = true;  
            }
            if (!doping) {
                return true;
            }

            conn = cinfo.getConnection(); 
            if (conn == null) {
                return false;
            }

            Statement stmt = null;
            ResultSet rs = null;
            Boolean valid = null;
            try {
                stmt = conn.createStatement();
                int queryTimeout = stmt.getQueryTimeout();
                if (dbmgr.isJDBC4()) {
                    try {
                        Class cc = java.sql.Connection.class;
                        Method m = cc.getMethod("isValid", new Class[]{java.lang.Integer.TYPE});
                        long startime = System.currentTimeMillis();
                        boolean b = ((Boolean)m.invoke(conn,
                            new Object[]{Integer.valueOf(queryTimeout)})).booleanValue();
                        if (!b) {
                            if (queryTimeout > 0 && 
                                (System.currentTimeMillis() < (startime+queryTimeout*1000L))) {
                                valid = Boolean.valueOf(false);
                                destroyIdles = true;
                                setInvalidateAllTimestamp();
                            } else if (queryTimeout == 0) {
                                valid = Boolean.valueOf(false);
                                destroyIdles = true;
                                setInvalidateAllTimestamp();
                            }
                        } else {
                            valid = Boolean.valueOf(b);
                            if (DEBUG) {
                                FaultInjection fi = FaultInjection.getInjection();
                                if (fi.FAULT_INJECTION &&
                                    fi.checkFault(FaultInjection.FAULT_JDBC_VALIDATECONN_1, null)) {
                                    fi.unsetFault(FaultInjection.FAULT_JDBC_VALIDATECONN_1);
                                    valid = Boolean.valueOf(false);
                                    destroyIdles = true;
                                    setInvalidateAllTimestamp();
                                }
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        dbmgr.setJDBC4(false);
                    } catch (Throwable t) {
                        if (t instanceof InvocationTargetException) {
                            Throwable cause = ((InvocationTargetException)t).getTargetException();
                            if (cause == null) {
                                cause = t.getCause();
                            }
                            if (cause != null && (cause instanceof AbstractMethodError)) {
                                dbmgr.setJDBC4(false);
                            }
                        } 
                        if (dbmgr.isJDBC4() && (DEBUG)) {
                            logger.logStack(logger.INFO, toString()+".validateConnection: "+
                            "Exception in invoking Connection.isValid("+
                             queryTimeout+")", t);
                        }
                    }
                }

                String sql = null;

                if (valid == null) {
                    sql = validationQuery;
                    if (sql == null) {
                        valid = Boolean.valueOf(true);
                    }
                }
                if (valid == null) {
                    try {
                        try {
                            rs = dbmgr.executeQueryStatement(stmt, sql);
                            if (DEBUG) {
                                FaultInjection fi = FaultInjection.getInjection();
                                if (fi.FAULT_INJECTION &&
                                    fi.checkFault(FaultInjection.FAULT_JDBC_VALIDATECONN_1, null)) {
                                    fi.unsetFault(FaultInjection.FAULT_JDBC_VALIDATECONN_1);
                                    throw new SQLException(FaultInjection.FAULT_JDBC_VALIDATECONN_1);
                                }
                            }
                        } catch (SQLException e) {
                            destroyIdles = true;
                            setInvalidateAllTimestamp();
                            throw e;
                        }
                        if (rs.next()) {
                            valid = Boolean.valueOf(true);
                        } else {
                            valid = Boolean.valueOf(false);
                        }
                    } finally {
                        try {
                            if (!conn.getAutoCommit()) {
                                conn.rollback();
                            }
                        } catch (Exception e) {
                            logger.log(logger.WARNING,
                                br.getKString(br.W_DB_CONN_VALIDATION_EXCEPTION,
                                "["+sql+"]"+cinfo, e.toString())+toString());
                            valid = Boolean.valueOf(false);
                        }
                    }
                }
            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (o instanceof PooledConnection) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        logger.log(logger.WARNING,
                            br.getKString(br.W_DB_CONN_VALIDATION_EXCEPTION,
                            ""+cinfo+"[0x"+conn.hashCode()+"]", e.toString())+toString());
                        valid = Boolean.valueOf(false);
                    }
                }
            }
            if (valid == null) {
                valid = Boolean.valueOf(false);
            }
            return valid.booleanValue();

        } catch (Exception e) {
            logger.logStack(logger.WARNING,
                br.getKString(br.W_DB_CONN_VALIDATION_EXCEPTION,
                cinfo.toString(), e.getMessage())+toString(), e);
            return false;
        } finally {
            cinfo.setValidating(false);
            cinfo.setException(null);

            if (destroyIdles && invalidateAll) {
                destroyIdleConnections();
            }
        }
    }

    private void destroyIdleConnections() {

        int idleCnt = idleConnections.size();

        int cnt = 0;
        ConnectionInfo cinfo = (ConnectionInfo)idleConnections.poll();
        while (cinfo != null && cnt < idleCnt) {
            destroyConnection(cinfo);
            cnt++;
            cinfo = (ConnectionInfo)idleConnections.poll();
        }
        Object[] args = { Integer.valueOf(cnt) };
        logger.log(Logger.INFO, br.getKTString(
            br.I_DESTROYED_IDLE_DB_CONNECTIONS, args)+toString());
    }

    private void reapExcessConnection() {


        int activeCnt = activeConnections.size();
        int idleCnt = idleConnections.size();

        if (DEBUG) {
            logger.log(Logger.INFO, toString()+".reapExcessConnection: "+
                "pool size: min="+minConnections+", max="+maxConnections+
                ", active="+activeCnt+", idle="+idleCnt);
        }

        int reapCnt = 0;
        ConnectionInfo cinfo = null;

        while (idleCnt > 0 && (activeCnt + idleCnt) > minConnections) {
            cinfo = (ConnectionInfo)idleConnections.poll();
            if (cinfo == null) {
                break;
            }
            destroyConnection(cinfo);
            reapCnt++;
            activeCnt = activeConnections.size();
            idleCnt = idleConnections.size();
        }

        if (!dedicated || reapCnt != 0) {
            boolean log = true; 
            if (reapCnt == 0 && !(DEBUG)) {
                log = false;
            }
            if (log) {
                logger.log(logger.INFO, br.getKString(
                br.I_DB_REAP_EXCESSIVE_CONNS, Integer.valueOf(reapCnt))+toString());
            }
        }

        if (timeoutIdle) { 
            timeoutIdleConnections();
        }

        //ensure minConnections

        int createCnt = 0;
        activeCnt = activeConnections.size();
        idleCnt = idleConnections.size();
        while ((activeCnt+idleCnt) < minConnections) {
            try {
                cinfo = createConnection();
                if (activeConnections.size()+idleConnections.size() < minConnections) {
                    idleConnections.offer(cinfo);
                    createCnt++;
                } else {
                    destroyConnection(cinfo);
                    break;
                }
                activeCnt = activeConnections.size();
                idleCnt = idleConnections.size();
            } catch (BrokerException e) {
                if (DEBUG) {
                    logger.logStack(logger.WARNING, 
                    "JDBC connection pool reaper thread failed to create new connection", e);
                } else {
                    logger.log(logger.WARNING, 
                        br.getKString(br.W_DB_POOL_REAPER_CREATE_NEW_CONN_FAIL, e.getMessage()));
                }
                break;
            }
        }
        if (createCnt > 0) {
            logger.log(logger.INFO, br.getKString(
                br.I_CREATED_DB_CONNECTIONS, 
                Integer.valueOf(createCnt))+toString());
        }
    }

    private void timeoutIdleConnections() {

        ArrayList list = new ArrayList();
        Object[] o = idleConnections.toArray();
        int cnt = o.length;
        long currtime = System.currentTimeMillis();
        boolean found = false;
        for (int i = 0; i < cnt; i++) {
            list.add(o[i]);
            if ((currtime - ((ConnectionInfo)o[i]).getIdleStartTime()) >= reapInterval) {
                found = true;
            }
            if (getInvalidateAllTimestamp(((ConnectionInfo)o[i]).getCreationTime()) != null) {
                found = true;
            }
        }
        if (!found || list.size() == 0) {
            return;
        }
          
        ConnectionInfo cinfo = (ConnectionInfo)idleConnections.peek();
        if (cinfo == null) {
            return;
        }
        
        ArrayList seen = new ArrayList();
        Thread borrower = Thread.currentThread();
        int i = 0, idleTimeoutCnt = 0;
        while (i < cnt) {
            if (DEBUG) {
                logger.log(logger.INFO, 
                "DBConnectionPool.reapExcessConnection idleTimeoutCnt="+idleTimeoutCnt+", cnt="+cnt+", i="+i);
            }
            cinfo = (ConnectionInfo)idleConnections.peek();
            if (cinfo == null || !list.contains(cinfo) || seen.contains(cinfo)) {
                break;
            }
            cinfo = idleConnections.poll();
            if (cinfo == null) {
                break;
            }
            seen.add(cinfo);
            i++;
            activeConnections.put(cinfo, borrower);
            if (list.contains(cinfo) && 
                ((currtime - cinfo.getIdleStartTime()) >= reapInterval ||
                 getInvalidateAllTimestamp(cinfo.getCreationTime()) != null)) {
                activeConnections.remove(cinfo);
                destroyConnection(cinfo);
                idleTimeoutCnt++;
            } else {
                activeConnections.remove(cinfo);
                idleConnections.offer(cinfo);
            }
        }
        if (idleTimeoutCnt > 0) {
            logger.log(logger.INFO, br.getKString(
                   br.I_DB_REAP_IDLE_CONNS,
                   Integer.valueOf(idleTimeoutCnt))+toString());
        }
    }

    private class ConnectionReaperTask extends TimerTask
    {
        private volatile boolean canceled = false;

        public boolean cancel() {
            canceled = true;
            return super.cancel();
        }

        public void run() {
            if (canceled) {
                return;
            }

            try {
                reapExcessConnection();
            } catch (Exception e) {
                Globals.getLogger().logStack( Logger.ERROR,
                    BrokerResources.E_DB_POOL_REAPER_THREAD_EXCEPTION+
                    toString(), e );
            }
        }
    }

    private class DBConnectionListener implements ConnectionEventListener {

        public DBConnectionListener() {}
   
        /**
         * Notifies this <code>ConnectionEventListener</code> that
         * the application has called the method <code>close</code> on its
         * representation of a pooled connection.
         *
         * @param event an event object describing the source of
         * the event
         */
        public void connectionClosed(ConnectionEvent event) {
            PooledConnection pconn = (PooledConnection) event.getSource();
            ConnectionInfo cinfo = connMap.get(pconn);
            if (cinfo == null) {
                throw new IllegalStateException(
                "No mapping for PooledConnection 0x"+pconn.hashCode()+
                "["+pconn.getClass().getName()+"]");
            }
            if (DEBUG) {
                logger.log(logger.INFO, toString()+".connectionClosed event on "+cinfo);
            }
            if (!cinfo.inValidating()) {
                boolean destroy = false;
                Throwable e = cinfo.getException();
                if (e != null) {
                    Throwable cause = e;
                    if (e instanceof BrokerException) {
                        cause  = e.getCause();
                    }
                    if ((cause instanceof SQLException) ||
                        (cause instanceof IOException)) {
                        if (DEBUG) {
                            logger.logStack(Logger.INFO, 
                                br.getKString(br.I_DB_CONN_EX_TOBE_DESTROYED,
                                cinfo.toString(), cause.toString())+toString(), cause);
                        } else { 
                            logger.log(Logger.INFO, 
                                br.getKString(br.I_DB_CONN_EX_TOBE_DESTROYED,
                                cinfo.toString(), cause.toString())+toString());
                        }
                        destroy = true;
                    } 
                }
                returnConnection(cinfo, cinfo.getException(), destroy);
            }
        }

        /**
         * Notifies this <code>ConnectionEventListener</code> that
         * a fatal error has occurred and the pooled connection can
         * no longer be used.  The driver makes this notification just
         * before it throws the application the <code>SQLException</code>
         * contained in the given <code>ConnectionEvent</code> object.
         *
         * @param event an event object describing the source of
         * the event and containing the <code>SQLException</code> that the
         * driver is about to throw
         */
        public void connectionErrorOccurred(ConnectionEvent event) {
            PooledConnection pconn = (PooledConnection)event.getSource();
            pconn.removeConnectionEventListener(this);
            ConnectionInfo cinfo = connMap.get(pconn);
            if (cinfo == null) {
                throw new IllegalStateException(
                "connectionErrorOccurred: No mapping for PooledConnection 0x"+
                 pconn.hashCode()+"["+pconn.getClass().getName()+"]");
            }
            SQLException ex = event.getSQLException();
            logger.log(logger.WARNING, 
                       br.getKString(br.W_DB_CONN_ERROR_EVENT,
                       ""+cinfo, ""+ex)+toString());
            if (ex == null) {
                ex = new SQLException();
            }
            cinfo.setException(ex);
            if (!cinfo.inValidating()) {
                returnConnection(cinfo, cinfo.getException(), true);
            }
        }
    }
}

class ConnectionInfo {

    Object conn;  
    Throwable thr = null;
    ConnectionEventListener listener = null;
    boolean validating = false;
    long idleStartTime = System.currentTimeMillis();
    long creationTime = System.currentTimeMillis();

    public ConnectionInfo(Object conn, ConnectionEventListener listener) {
        this.conn = conn;
        this.listener = listener;
        if (conn instanceof PooledConnection) {
            ((PooledConnection)conn).addConnectionEventListener(listener);
        }
    }
    
    public Object getKey() {
        return conn;
    }

    public void idleStart() {
        idleStartTime = System.currentTimeMillis();
    }

    public long getIdleStartTime() {
        return idleStartTime;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setValidating(boolean b) {
        validating = b;
    } 

    public boolean inValidating() {
        return validating;
    }

    public void setException(Throwable t) {
        thr = t;
    }

    public Throwable getException() {
        return thr;
    }

    public Connection getConnection() throws SQLException {
        if (conn instanceof PooledConnection) {
            return ((PooledConnection)conn).getConnection();
        } else {
            return (Connection)conn;
        }
    }

    public void destroy() {
        try {
            if (conn instanceof PooledConnection) {
                ((PooledConnection)conn).removeConnectionEventListener(listener);
                ((PooledConnection)conn).close();
            } else { 
                ((Connection)conn).close();
            }
        } catch (Throwable t) {
            Globals.getLogger().log(Globals.getLogger().WARNING, 
                Globals.getBrokerResources().W_DB_CONN_CLOSE_EXCEPTION,
                this.toString(), t.toString());
        }
    }

    public String toString() {
        return (conn instanceof Connection ? "[Connection":"[PooledConnection")+
                ":0x"+conn.hashCode()+", lastIdleTime="+idleStartTime+
                (thr == null ? "":", "+thr.toString()) +"]";
    }
} 

