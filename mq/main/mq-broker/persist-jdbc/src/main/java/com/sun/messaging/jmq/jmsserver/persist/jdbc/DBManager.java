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
 * @(#)DBManager.java	1.72 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.Password;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.DBConnectionPool;
import com.sun.messaging.jmq.io.Status;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.net.*;
import java.lang.reflect.*;
import javax.sql.*;

/**
 * DB Manager for imq.persist.jdbc store - JDBCStore
 */
public final class DBManager extends CommDBManager implements DBConstants {

    /**
     * properties used:
     *
     * - vendor specific jdbc drvier
     * jmq.persist.dbVendor=<Database vendor>
     *
     * - vendor specific jdbc drvier
     * jmq.persist.jdbc.<dbVendor>.driver=<jdbcdriver class>
     * jmq.persist.jdbc.driver=<jdbcdriver class>
     *
     * - vendor specific database url to get a database connection
     * jmq.persist.jdbc.<dbVendor>.opendburl=<url to open database>
     * jmq.persist.jdbc.opendburl=<url to open database>
     *
     * - vendor specific database url to get a database connection to create
     *   a database
     * jmq.persist.jdbc.<dbVendor>.createdburl=<url to create database>
     * jmq.persist.jdbc.createdburl=<url to create database>
     *
     * - vendor specific database url to shutdown the connection (optional)
     * jmq.persist.jdbc.closedburl=<url to close database connection>
     * jmq.persist.jdbc.<dbVendor>.closedburl=<url to close database connection>
     *
     * - user name used to open database connection (optional)
     * jmq.persist.jdbc.<dbVendor>.user=<username>
     * jmq.persist.jdbc.user=<username>
     *
     * - password used to open database connection (private)
     * jmq.persist.jdbc.<dbVendor>.password=<password>
     * jmq.persist.jdbc.password=<password>
     *
     * - password used to open database connection (optional)
     * jmq.persist.jdbc.<dbVendor>.needpassword=[true|false]
     * jmq.persist.jdbc.needpassword=[true|false]
     *
     * - brokerid to make table names unique per broker instance
     * jmq.brokerid=<alphanumeric id>
     *
     * - clusterid to make table names unique for shared store per HA cluster
     * imq.cluster.clusterid=<alphanumeric id>
     */

    private static final String STORE_TYPE_PROP = Globals.IMQ + ".persist.store";
    public static final String JDBC_PROP_PREFIX = Globals.IMQ + ".persist.jdbc";

	public static final String FALLBACK_USER_PROP = 
        JDBC_PROP_PREFIX+CommDBManager.FALLBACK_USER_PROP_SUFFIX;
	public static final String FALLBACK_PWD_PROP = 
        JDBC_PROP_PREFIX+CommDBManager.FALLBACK_PWD_PROP_SUFFIX;

    private static final String BROKERID_PROP = Globals.IMQ + ".brokerid";

    private static final int LONGEST_TABLENAME_LEN = 17; //add index

    // cluster id to make table names unique per cluster
    private String clusterID = null;

    // broker id to make table names unique per broker
    private String brokerID = null;

    // DAO factory
    private DAOFactory daoFactory = null;
    private DBConnectionPool dbpool = null;

    private boolean storeInited = false;

    private static final Object classLock = DBManager.class;
    private static volatile DBManager dbMgr = null;

    // array of table names used in version 370 store
    private static String v370tableNames[] = {
                                         VERSION_TBL_37,
                                         CONFIGRECORD_TBL_37,
                                         DESTINATION_TBL_37,
                                         INTEREST_TBL_37,
                                         MESSAGE_TBL_37,
                                         PROPERTY_TBL_37,
                                         INTEREST_STATE_TBL_37,
                                         TXN_TBL_37,
                                         TXNACK_TBL_37
    };

    // array of table names used in version 350 store
    private static String v350tableNames[] = {
                                         VERSION_TBL_35,
                                         CONFIGRECORD_TBL_35,
                                         DESTINATION_TBL_35,
                                         INTEREST_TBL_35,
                                         MESSAGE_TBL_35,
                                         PROPERTY_TBL_35,
                                         INTEREST_STATE_TBL_35,
                                         TXN_TBL_35,
                                         TXNACK_TBL_35
    };

    protected boolean getDEBUG() {
        return Store.getDEBUG();
    }
    /**
     * Get DBManager method for singleton pattern.
     * @return DBManager
     * @throws BrokerException
     */
    public static DBManager getDBManager() throws BrokerException {
        if (dbMgr == null) {
            synchronized(classLock) {
                if (dbMgr == null) {
                    dbMgr = new DBManager();
                    dbMgr.loadTableSchema();
                    dbMgr.dbpool = new DBConnectionPool(dbMgr, "dbp");
                    dbMgr.initDBMetaData();
                }
            }
        }
        return dbMgr;
    }

    protected String getJDBCPropPrefix() {
        return JDBC_PROP_PREFIX;
    }

    protected String getStoreTypeProp() {
        return STORE_TYPE_PROP;
    }

    protected String getCreateStoreProp() {
        return Store.CREATE_STORE_PROP;
    }

    protected boolean getCreateStorePropDefault() {
        return Store.CREATE_STORE_PROP_DEFAULT;
    }

    protected String getLogStringTag() {
        return "";
    }

    public String toString() {
        return "DBManager";
    }

    protected void
    checkMaxTableNameLength(int maxTableNameLength) throws BrokerException {
        if (maxTableNameLength > 0) {
            // We do know the max number of chars allowed for a table
            // name so verify brokerID or clusterID is within limit.
            if (Globals.getHAEnabled()) {
                if ((clusterID.length()+LONGEST_TABLENAME_LEN+1) > maxTableNameLength) {
                    Object[] args = { clusterID, Integer.valueOf(maxTableNameLength),
                                      Integer.valueOf(LONGEST_TABLENAME_LEN+1) };
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_CLUSTER_ID_TOO_LONG, args));
                }
            } else {
                if ((brokerID.length()+LONGEST_TABLENAME_LEN+1) > maxTableNameLength) {
                    Object[] args = { brokerID, Integer.valueOf(maxTableNameLength),
                                      Integer.valueOf(LONGEST_TABLENAME_LEN+1) };
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_BROKER_ID_TOO_LONG, args));
                }
            }
        }
    }

    protected boolean isStoreInited() {
        return storeInited;
    }

    protected void setStoreInited(boolean b) {
        storeInited = b;
    }


    /**
     * When instantiated, the object configures itself by reading the
     * properties specified in BrokerConfig.
     */
    private DBManager() throws BrokerException {

        initDBManagerProps();

        // Fix version 370 & 350 table names
        fixOldTableNames();

        initDBDriver();
    }

    protected void initTableSuffix() throws BrokerException {
        brokerID = Globals.getBrokerID();
        if (brokerID == null || brokerID.length() == 0 ||
            !Util.isAlphanumericString(brokerID)) {
            throw new BrokerException(
                br.getKString(BrokerResources.E_BAD_BROKER_ID, brokerID));
        }

        // table suffix
        if (Globals.getHAEnabled()) {
            clusterID = Globals.getClusterID();
            if (clusterID == null || clusterID.length() == 0 ||
                !Util.isAlphanumericString(clusterID)) {
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_BAD_CLUSTER_ID, clusterID));
            }

            // Use cluster ID as the suffix
            tableSuffix = "C" + clusterID;
        } else {
            // Use broker ID as the suffix
            tableSuffix = "S" + brokerID;
        }
    }

    public String getBrokerID() {
        return brokerID;
    }

    @Override
    public String getClusterID() {
        return clusterID;
    }


    public int checkStoreExists(Connection conn) throws BrokerException {
        return super.checkStoreExists(conn, null);
    }

    /**
     */
    protected Connection getConnection() throws BrokerException {
        return dbpool.getConnection();
    }

    public void freeConnection(Connection conn, Throwable thr)
    throws BrokerException {

        dbpool.freeConnection(conn, thr);
    }

    public void
    closeSQLObjects(ResultSet rset, Statement stmt, 
                    Connection conn, Throwable ex)
                    throws BrokerException {

        Util.close(rset, stmt, conn, ex);
    }

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        ht.put("storeInited", Boolean.valueOf(storeInited));
        ht.put("clusterID", ""+clusterID);
        ht.put("brokerID", ""+brokerID);
        ht.put(dbpool.toString(), dbpool.getDebugState());
        return ht;
    }

    public boolean isHAClusterActive(Connection conn) throws BrokerException {

        boolean isActive = false;

        BrokerDAO bkrDAO = getDBManager().getDAOFactory().getBrokerDAO();
        HashMap bkrMap = bkrDAO.getAllBrokerInfos(conn, false);
        Iterator itr = bkrMap.values().iterator();
        long currentTime = System.currentTimeMillis();
        while ( itr.hasNext() ) {
            HABrokerInfo bkrInfo = (HABrokerInfo)itr.next();
            int state = bkrInfo.getState();
            if ( !BrokerState.getState(state).isActiveState() ) {
                continue; // broker is not active
            }

            // We've a broker in active state, re-verify w/ last heartbeat;
            // If heartbeat is older than 3 minutes then consider it not active
            long lastHeartBeat = bkrInfo.getHeartbeat();
            if ( lastHeartBeat + 180000 > currentTime ) {
                isActive = true;
                break;
            }
        }

        return isActive;
    }

    public DAOFactory getDAOFactory() {
        if (daoFactory == null) {
            synchronized(classLock) {
                if (daoFactory == null) {
                    // Create a DAO factory for the specified DB vendor
                    if ( isHADB ) {
                        logger.log( Logger.DEBUG, "Instantiating HADB DAO factory" );
                        daoFactory = new HADBDAOFactory();
                    } else if ( isOracle ) {
                        logger.log( Logger.DEBUG, "Instantiating Oracle DAO factory" );
                        daoFactory = new OracleDAOFactory();
                    } else if ( isMysql ) {
                        logger.log( Logger.DEBUG, "Instantiating MySQL DAO factory" );
                        daoFactory = new MySQLDAOFactory();
                    } else {
                        logger.log( Logger.DEBUG, "Instantiating generic DAO factory" );
                        daoFactory = new GenericDAOFactory();
                    }                    
                }
            }
        }
        return daoFactory;
    }

    protected BaseDAO getFirstDAO() throws BrokerException {
        return (BaseDAO)getDAOFactory().getAllDAOs().get(0);
    }

    public void resetConnectionPool() throws BrokerException {
        dbpool.reset();   // Reset/clear connection pool
    }
    
    protected void close() {
        synchronized (classLock) {
            dbpool.close(); 
            super.close();
            if (dbMgr != null) {
                dbMgr = null;
            }
        }
    }

    // Get all names of tables used in a specific store version; i.e. pre-4.0
    public String[] getTableNames(int version) {
        String names[] = new String[0];
        if (version == JDBCStore.STORE_VERSION) {
            names = (String[]) tableSchemas.keySet().toArray(names);
        } else if (version == JDBCStore.OLD_STORE_VERSION_370) {
            names = v370tableNames;
        } else if (version == JDBCStore.OLD_STORE_VERSION_350) {
            names = v350tableNames;
        }
        return names;
    }

    public boolean hasSupplementForCreateDrop(String tableName) {
        return false;
    }

    public Iterator allDAOIterator() throws BrokerException {
        return getDAOFactory().getAllDAOs().iterator();
    }

    protected String getTableLockTableName() throws BrokerException {
        VersionDAO dao = getDBManager().getDAOFactory().getVersionDAO();
        return dao.getTableName();
    }

    /**
     * This method should only be called at store initialization
     * or from dbmgr
     *
     * @return must not null; the current lock id or "" if no lock
     */
    protected String getCurrentTableLock( Connection conn, boolean doLock ) 
    throws BrokerException {

        // Check if there is a lock; if the store does not exist, VersionDAO
        // will throw a BrokerException with the status set to Status.NOT_FOUND
        VersionDAO verDAO = getDBManager().getDAOFactory().getVersionDAO();
        String currLock = verDAO.getLock( conn, JDBCStore.STORE_VERSION );
        if ( currLock == null ) {
            // We've a problem, version data not found
            if ( !doLock ) {
                Globals.getLogger().log(Logger.WARNING,
                    BrokerResources.E_BAD_STORE_NO_VERSIONDATA, verDAO.getTableName());

                // insert version info in the version table
                verDAO.insert( conn, JDBCStore.STORE_VERSION );
                return "";
            } else {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.E_BAD_STORE_NO_VERSIONDATA,
                        verDAO.getTableName() ), Status.NOT_FOUND );
            }
        }
        return currLock;
    }

    /**
     * @param newLockID null if it's unlock
     */
    protected void updateTableLock(Connection conn, 
        String newLockID, String oldLockID, Object extra)
        throws BrokerException {

        VersionDAO verDAO = getDBManager().getDAOFactory().getVersionDAO();
        boolean updated = verDAO.updateLock(
                conn, JDBCStore.STORE_VERSION, newLockID, oldLockID );
        if ( !updated ) {
            if ( newLockID == null ) {
                // Failed to remove lock
                Globals.getLogger().log( Logger.ERROR,
                    BrokerResources.E_REMOVE_STORE_LOCK_FAILED );
            }

            // Unable to get lock, e.g. another broker get to it first?
            String currLock = verDAO.getLock( conn, JDBCStore.STORE_VERSION );
            throwTableLockedException( currLock );
        }
    }

    public void throwTableLockedException(String lockid) 
    throws BrokerException {
        throwTableLockedException( new TableLock( lockid, getTableLockTableName() ) ); 
    }

    protected void throwTableLockedException(TableLock lock) 
    throws BrokerException {
        BrokerResources br = Globals.getBrokerResources();
        String emsg = null;
        if (lock.port != 0) {
            emsg = br.getKString(br.E_TABLE_LOCKED_BY_BROKER,
                                 lock.host, String.valueOf(lock.port));
            throw new BrokerException(emsg, 
                br.E_TABLE_LOCKED_BY_BROKER, null, Status.CONFLICT);
        } 
        emsg = br.getKString(br.E_TABLE_LOCKED_BY_DBMGR);
        throw new BrokerException(emsg, 
            br.E_TABLE_LOCKED_BY_DBMGR, null, Status.CONFLICT);
    }

    // add the brokerid to the old table names
    private void fixOldTableNames() {
        for (int i = 0; i < v370tableNames.length; i++) {
            v370tableNames[i] = v370tableNames[i] + brokerID;
        }

        for (int i = 0; i < v350tableNames.length; i++) {
            v350tableNames[i] = v350tableNames[i] + brokerID;
        }
    }
}
