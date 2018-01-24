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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecord;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.DBConnectionPool;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.net.*;
import javax.sql.*;

/**
 */
public final class ShareConfigChangeDBManager extends CommDBManager {

    static final String JDBC_PROP_PREFIX = ShareConfigChangeStore.STORE_TYPE_PROP+
                                           "."+Store.JDBC_STORE_TYPE;

    static final int LONGEST_TABLENAME_LEN = 15; //add index

    private BrokerResources br = Globals.getBrokerResources();

    // cluster id to make table name unique per cluster
    private String clusterID = null;

    private ShareConfigRecordDAOFactory daoFactory = null;
    private DBConnectionPool dbpool = null;
    private boolean storeInited = false;

    private static final Object classLock = ShareConfigChangeDBManager.class;
    private static ShareConfigChangeDBManager dbMgr = null;

    protected boolean getDEBUG() {
        return ShareConfigChangeStore.getDEBUG();
    }


    /**
     * Get DBManager method for singleton pattern.
     * @return DBManager
     * @throws BrokerException
     */
    public static
    ShareConfigChangeDBManager getDBManager() throws BrokerException {
        synchronized (classLock) {
            if (dbMgr == null) {
                dbMgr = new ShareConfigChangeDBManager();
                dbMgr.loadTableSchema();
                dbMgr.dbpool = new DBConnectionPool(dbMgr, "ccshare", true);
                dbMgr.initDBMetaData();
            }
        }
        return dbMgr;
    }

    protected String getJDBCPropPrefix() {
        return JDBC_PROP_PREFIX;
    }

    protected String getStoreTypeProp() {
        return ShareConfigChangeStore.STORE_TYPE_PROP;
    }

    protected String getCreateStoreProp() {
        return ShareConfigChangeStore.CREATE_STORE_PROP;
    }

    protected boolean getCreateStorePropDefault() {
        return ShareConfigChangeStore.CREATE_STORE_PROP_DEFAULT;
    }

    protected String getLogStringTag() {
        return "["+JDBC_PROP_PREFIX+"]";
    }

    public String toString() {
        return "CCShareDBManger";
    }

    protected Connection getConnection() throws BrokerException {
        return dbpool.getConnection();
    }

    public void freeConnection(Connection c, Throwable thr)
    throws BrokerException {

        dbpool.freeConnection(c, thr);
    }


    protected void
    checkMaxTableNameLength(int maxTableNameLength) throws BrokerException {
        if (maxTableNameLength > 0) {
            // We do know the max number of chars allowed for a table
            // name so verify brokerID or clusterID is within limit.
            int baselen = LONGEST_TABLENAME_LEN+1;
            if ((clusterID.length()+baselen) > maxTableNameLength) {
                Object[] args = { clusterID, Integer.valueOf(maxTableNameLength),
                                  Integer.valueOf(baselen) };
                throw new BrokerException(br.getKString(
                    BrokerResources.E_CLUSTER_ID_TOO_LONG, args));
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
    private ShareConfigChangeDBManager() throws BrokerException {

        initDBManagerProps();
        initDBDriver();
    }

    protected void initTableSuffix() throws BrokerException {
        clusterID = Globals.getClusterID();
        if (clusterID == null || clusterID.length() == 0 ||
            !Util.isAlphanumericString(clusterID)) {
             throw new BrokerException(br.getKString(
                 BrokerResources.E_BAD_CLUSTER_ID, clusterID));
        }

        // Use cluster ID as the suffix
        tableSuffix = "C" + clusterID;
    }

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        ht.put("clusterID", ""+clusterID);
        ht.put(dbpool.toString(), dbpool.getDebugState());
        return ht;
    }

    public ShareConfigRecordDAOFactory getDAOFactory() {
        synchronized(classLock) {
            if (daoFactory == null) {
                daoFactory = new ShareConfigRecordDAOFactory();
            }
        }
        return daoFactory;
    }

    protected BaseDAO getFirstDAO() throws BrokerException {
        return (BaseDAO)((List)getDAOFactory().getAllDAOs()).get(0);
    }

    public Iterator allDAOIterator() throws BrokerException {
        return getDAOFactory().getAllDAOs().iterator();
    }

    @Override
    public String getClusterID() {
        return clusterID;
    }

    protected void close() {
        synchronized (classLock) {
            dbpool.close();
            super.close();
            dbMgr = null;
        }
    }

    @Override
    public String[] getAllOldTableNames() {
        String[] olds = new String[1];
        olds[0] =  getTableName(ShareConfigRecordDAO.TABLE+
                       JDBCShareConfigChangeStore.SCHEMA_VERSION_45);
        return olds;
    } 

    public String[] getTableNames(int version) {
        if (version == JDBCShareConfigChangeStore.VERSION) {
            return (String[]) tableSchemas.keySet().toArray(new String[tableSchemas.size()]);
        } else if (version == JDBCShareConfigChangeStore.VERSION_450) {
            String[] olds = new String[1];
            olds[0] =  getTableName(ShareConfigRecordDAO.TABLE+
                           JDBCShareConfigChangeStore.SCHEMA_VERSION_45);
            return olds;
        } else {
            return new String[0];
        }
    }

    public int checkStoreExists(Connection conn) throws BrokerException {
        return super.checkStoreExists(conn, 
            JDBCShareConfigChangeStore.SCHEMA_VERSION);
    }

    public boolean hasSupplementForCreateDrop(String tableName) {
        return true;
    }

    @Override
    public void dropOldTableSupplement(Statement stmt, 
    String oldTableName, boolean throwException) 
    throws BrokerException {

        String sql = null;
        if (oldTableName.endsWith(JDBCShareConfigChangeStore.SCHEMA_VERSION_45)) {
            if (isOracle()) {
                sql = "DROP SEQUENE "+oldTableName+"_seq";
            }
        }
	if (sql == null) {
            return;
	}
        logger.logToAll( Logger.INFO,
            br.getKString(br.I_EXEC_DROP_TABLE_SUPPLEMENT, sql, oldTableName) );
	try {
            int cnt = dbMgr.executeUpdateStatement( stmt, sql );
            if (cnt != 0 || stmt.getWarnings() != null) {
		String emsg = "["+sql+"]: "+stmt.getWarnings()+"(return="+cnt+")";
		throw new BrokerException(emsg);
            }
	} catch (Throwable t) {
            BrokerException ex = null;
            if (t instanceof BrokerException) {
            	ex = (BrokerException)t;
            } else {
            	String emsg = "["+sql+"]: "+t.getMessage();
            	ex = new BrokerException(emsg, t);
            }
            logger.logStack(logger.WARNING, ex.getMessage(), t);
            if (throwException) {
            	throw ex;
            }
	}
    }
  
    protected String getTableLockTableName() throws BrokerException {
        ShareConfigRecordDAO dao = getDAOFactory().getShareConfigRecordDAO();
        return dao.getTableName();
    }

    protected String getCurrentTableLock( Connection conn, boolean doLock )
    throws BrokerException {

        ShareConfigRecordDAO dao = getDAOFactory().getShareConfigRecordDAO();
        String currLock = dao.getLockID( conn );
        if (currLock == null) {
            String emsg = br.getKString(br.E_SHARECC_TABLE_NOT_EMPTY,
                                        getClusterID());
            throw new BrokerException(emsg, 
               br.E_SHARECC_TABLE_NOT_EMPTY, null, 
               Status.MOVED_PERMANENTLY);
        }
        return currLock;
    }

    /**
     * @param newLockID null if unlock
     */
    protected void updateTableLock( Connection conn, 
        String newLockID, String oldLockID, Object extra ) 
        throws BrokerException {

        ShareConfigRecordDAO dao = getDAOFactory().getShareConfigRecordDAO();
        if (oldLockID != null) {
            dao.updateLockID( conn, newLockID, oldLockID );
            return; 
        } else if (newLockID != null) {
            ChangeRecordInfo cri = (ChangeRecordInfo)extra;
            dao.insertResetRecord( null, cri, newLockID );
        } else {
            throw new BrokerException("Both old and new lock id parameters are passed null");
        }
    }

    public void throwTableLockedException( String lockID )
    throws BrokerException {
        throwTableLockedException( new TableLock( lockID, getTableLockTableName() ) );
    }

    protected void throwTableLockedException( TableLock lock )
    throws BrokerException {
        BrokerResources br = Globals.getBrokerResources();
        String emsg = null;
        if (lock.port != 0) {
            emsg = br.getKString(br.E_SHARECC_TABLE_LOCKED_BY,
                                 lock.host, String.valueOf(lock.port));
            throw new BrokerException(emsg, 
                br.E_SHARECC_TABLE_LOCKED_BY, null, Status.CONFLICT);
        }
        emsg = br.getKString(br.E_SHARECC_TABLE_LOCKED_BY_DBMGR, "imqdbmgr");
        throw new BrokerException(emsg, 
            br.E_SHARECC_TABLE_LOCKED_BY_DBMGR, null, Status.CONFLICT);
    }

    public void
    closeSQLObjects(ResultSet rset, Statement stmt, Connection conn, Throwable ex)
    throws BrokerException {
        try {
            if ( rset != null ) {
                rset.close();
            }
            if ( stmt != null ) {
                stmt.close();
            }
        } catch ( SQLException e ) {
            throw new BrokerException(
                br.getKString(br.E_UNEXPECTED_EXCEPTION, e.toString()), e );
        } finally {
            if ( conn != null ) {
                freeConnection( conn, ex );
            }
        }
    }
}
