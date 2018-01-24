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

import java.io.*;
import java.sql.*;
import java.util.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecord;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.DBTool;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;
import com.sun.messaging.jmq.util.synchronizer.CloseInProgressSynchronizer;
import com.sun.messaging.jmq.util.synchronizer.CloseInProgressCallback;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

/**
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.JDBCShareConfigChangeStore")
@Singleton
public class JDBCShareConfigChangeStore extends ShareConfigChangeStore
implements CloseInProgressCallback {

    private static boolean DEBUG = getDEBUG();

    public static final int VERSION_450 = 450;
    public static final int VERSION_500 = 500;
    public static final int VERSION = VERSION_500;

    public static final String SCHEMA_VERSION = "50";
    public static final String SCHEMA_VERSION_45= "45";

    private static final String CLOSEWAIT_TIMEOUT_PROP = 
        ShareConfigChangeStore.STORE_TYPE_PROP+"CloseWaitTimeoutInSeconds";
    private static final int CLOSEWAIT_TIMEOUT_PROP_DEFAULT = 30; 

    private static final String NO_UPGRADE_PROP_SUFFIX = ".noUpgrade"; 
    private static final boolean NO_UPGRADE_PROP_DEFAULT = false; 

    private boolean createStore = false;
	private final Logger logger = Globals.getLogger();
    private final BrokerResources br = Globals.getBrokerResources();
    private final BrokerConfig config = Globals.getConfig();

    private ShareConfigChangeDBManager dbmgr = null;
    private ShareConfigRecordDAOFactory daoFactory = null;

    private final CloseInProgressSynchronizer inprogresser =
                         new CloseInProgressSynchronizer(logger);

    private int closeWaitTimeout = CLOSEWAIT_TIMEOUT_PROP_DEFAULT;

    /**
     */
    public JDBCShareConfigChangeStore() throws BrokerException {

        inprogresser.reset();
        closeWaitTimeout = config.getIntProperty(CLOSEWAIT_TIMEOUT_PROP, 
                                                 CLOSEWAIT_TIMEOUT_PROP_DEFAULT);

        dbmgr = ShareConfigChangeDBManager.getDBManager();
        daoFactory = dbmgr.getDAOFactory();

        String url = dbmgr.getOpenDBURL();
        if (url == null) {
            url = "not specified";
        }

        String user = dbmgr.getUser();
        if (user == null) {
            user = "not specified";
        }

        createStore = config.getBooleanProperty(CREATE_STORE_PROP, 
                                                CREATE_STORE_PROP_DEFAULT);
        if (createStore) {
            String args[] = { br.getString(BrokerResources.I_AUTOCREATE_ON),
                              String.valueOf(SCHEMA_VERSION), 
                              dbmgr.getClusterID(), url, user };
            logger.logToAll(Logger.INFO, br.getKString(
                            BrokerResources.I_SHARECC_JDBCSTORE_INFO, args));
        } else {
            String args[] = { br.getString(BrokerResources.I_AUTOCREATE_OFF), 
                              String.valueOf(SCHEMA_VERSION), 
                              dbmgr.getClusterID(), url, user };
            logger.logToAll(Logger.INFO, br.getKString(
                            BrokerResources.I_SHARECC_JDBCSTORE_INFO, args));
        }

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(true);
            checkStore(conn);
        } catch (Exception e) {
            myex = e;
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(e.getMessage(), e);
        } finally {
             dbmgr.closeSQLObjects(null, null, conn, myex);
        }
        dbmgr.setStoreInited(true);

        if (DEBUG) {
	        logger.log(Logger.DEBUG, "JDBCShareConfigChangeStore instantiated.");
        }
    }

    private void checkStore( Connection conn ) throws BrokerException {

        //first check old table existence and whether is empty
        boolean hasOldTable = false;
        boolean oldTableEmpty = false;
        String oldtable = dbmgr.getTableName(ShareConfigRecordDAO.TABLE + SCHEMA_VERSION_45);
        String sql = "SELECT "+ShareConfigRecordDAO.SEQ_COLUMN+ " FROM "+oldtable;
        if (hasOldStoreTable(conn, dbmgr.getTableName(oldtable), sql)) {
            hasOldTable = true;
            sql = "SELECT "+ShareConfigRecordDAO.SEQ_COLUMN+ " FROM "+oldtable+ 
                  " WHERE "+ ShareConfigRecordDAO.TYPE_COLUMN+ " <> "+
                  ChangeRecordInfo.TYPE_RESET_PERSISTENCE;
            oldTableEmpty =  isOldStoreTableEmpty(conn, dbmgr.getTableName(oldtable), sql);
        }

        //check new table existence
        int ret=dbmgr.checkStoreExists(conn);
        if (ret == -1) { 
            logger.log(Logger.ERROR, br.E_SHARECC_JDBCSTORE_MISSING_TABLE);
            throw new BrokerException(br.getKString(
                br.E_SHARECC_JDBCSTORE_MISSING_TABLE));
        }

        if (ret == 0) { //new table not exist

            if (!createStore && !hasOldTable) { 
                logger.log(Logger.ERROR, br.E_NO_SHARECC_JDBCSTORE_TABLE);
                throw new BrokerException(br.getKString(br.E_NO_DATABASE_TABLES));
            }

            logger.logToAll(Logger.INFO, br.getKString(
                br.I_SHARECC_JDBCSTORE_CREATE_NEW,
                br.getString(br.I_DATABASE_TABLE))); //only 1 table

            try {
                 DBTool.createTables( conn, false, dbmgr );
            } catch (Exception e) {
                String url = dbmgr.getCreateDBURL();
                if ( url == null || url.length() == 0 ) {
                     url = dbmgr.getOpenDBURL();
                }
                String emsg = br.getKString(br.E_FAIL_TO_CREATE,
                                           br.getString(br.I_DATABASE_TABLE));
                logger.logToAll(Logger.ERROR, emsg+"-"+url, e);
                throw new BrokerException(emsg, e);
            }
        }
        if (!hasOldTable || oldTableEmpty) {
           return;
        }
        migrateOldTableData( conn, oldtable);
    }
    
    private boolean hasOldStoreTable( Connection conn, String table, String sql )
    throws BrokerException {

	try {
            Statement stmt = null;
            ResultSet rs = null;
            Exception myex = null;
            try {
                stmt = conn.createStatement();
		rs = dbmgr.executeQueryStatement( stmt, sql );
                return true;
            } catch ( SQLException e ) {
                myex = e;
                logger.log( Logger.DEBUG, 
                "Assume old schema sharecc table does not exist because: " + e.toString() );
            } finally {
                Util.close( rs, stmt, null, myex );
            }
	} catch ( Exception e ) {
            String emsg = br.getKString(br.X_SHARECC_CHECK_OLD_SCHEMA_TABLE, table, e.toString());
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg);
        }
        return false;
    }

    private boolean isOldStoreTableEmpty( Connection conn, String table, String sql) 
    throws BrokerException {

        Exception myex = null;
        Statement stmt = null;
        ResultSet rs = null;
	try {
            stmt = conn.createStatement();
            rs = dbmgr.executeQueryStatement( stmt, sql );
            if (rs.next()) {
                return false;
            }
            return true;
        } catch ( Exception e ) {
            myex = e;
            Exception ex = e;
            if ( e instanceof SQLException ) {
                ex = dbmgr.wrapSQLException("[" + sql + "]", (SQLException)e);
            }
            throw new BrokerException( 
                br.getKString(br.X_SHARECC_CHECK_OLD_SCHEMA_TABLE_EMPTY,
                table, ex.getMessage()), ex);
        } finally {
            Util.close( rs, stmt, null, myex );
        }
    }

    private void migrateOldTableData( Connection conn, String oldTable )
    throws BrokerException {
        ShareConfigRecordDAO dao = dbmgr.getDAOFactory().getShareConfigRecordDAO();
        long totalwait = 30000L;
        ChangeRecordInfo resetcri = ChangeRecord.makeResetRecord( true );
        while (true ) {
            try {
                dbmgr.lockTables( conn, true, resetcri );
                break;
            } catch (BrokerException e) {
                String ecode = e.getErrorCode();
                if (ecode != null && ecode.equals(br.E_SHARECC_TABLE_LOCKED_BY)) {
                    if (!dbmgr.getIsClosing() && totalwait > 0L) {
                        try {
                            Thread.sleep(5000L);
                        } catch (Exception ee) {}
                        totalwait -= 5000L;
                        continue;
                    }
                    throw e;
                }
                if (ecode != null && ecode.equals(br.E_SHARECC_TABLE_NOT_EMPTY)) {
                    //someone else migrated
                    return;
                }
                throw e;
            }
        }
        Object[] logargs = { "", oldTable, dao.getTableName() };
        logger.log(logger.INFO, br.getKString(br.I_SHARECC_MIGRATING_DB, logargs));
        ArrayList<ChangeRecordInfo> newcris = new ArrayList<ChangeRecordInfo>(); 
        try {
            String sql = "SELECT * FROM "+oldTable;
            List<ChangeRecordInfo> cris = dao.getAllRecords( conn, sql );
            List<ChangeRecord> records = ChangeRecord.compressRecords( cris );
            ChangeRecordInfo newcri = null;
            ChangeRecord rec = null;
            Iterator<ChangeRecord> itr = records.iterator();
            while ( itr.hasNext() ) {
                rec = itr.next();
                if (rec.isDiscard()) {
                    continue;
                }
                newcri = new ChangeRecordInfo((Long)null, rec.getUUID(), rec.getBytes(),
                                   rec.getOperation(), rec.getUniqueKey(),
                                   System.currentTimeMillis());
                newcri.setDuraAdd(rec.isDuraAdd());
                newcri.setResetUUID(resetcri.getUUID());
                newcris.add(newcri);
            }
            Object[] args = { newcris.size()+"("+cris.size()+")", oldTable, dao.getTableName() };
            logger.log(logger.INFO, br.getKString(br.I_SHARECC_MIGRATING_DB, args));
        } catch (Exception e) {
            String emsg = br.getKString(br.X_SHARECC_PROCESS_DATA_FOR_MIGRATION, oldTable);
            logger.logStack(logger.ERROR, emsg, e);
            throw new BrokerException(emsg, e);
        }
        dao.insertAll( newcris, oldTable );
        logger.log(logger.INFO, br.getKString(
                   br.I_SHARECC_MIGRATED_DB, oldTable, dao.getTableName()));
        try {
            String[] olds = new String[1];
            olds[0] = oldTable;
            DBTool.dropTables( conn, olds, true, true, dbmgr );
            logger.log(logger.INFO, br.getKString(br.I_DB_TABLE_DELETED, oldTable));
        } catch (Exception e) { 
            logger.log(logger.WARNING, e.getMessage(), e);
        } finally {
            try {
                dbmgr.lockTables( conn, false );
            } catch (Exception e) {
                String emsg = br.getKString(
                    br.X_SHARECC_UNLOCK_TABLE_AFTER_MIGRATION, e.getMessage());
                logger.logStack(logger.ERROR, emsg, e);
                throw new BrokerException(emsg, e);
            }
        }
    }

    public Properties getStoreShareProperties() {
        if (dbmgr == null) {
            throw new RuntimeException("JDBShareConfigChangeStore not initialized");
        }
        Properties p = new Properties();
        p.setProperty(dbmgr.getOpenDBUrlProp(), dbmgr.getOpenDBUrl());
        return p;
    }

    public String getVendorPropertySetting() {
        return dbmgr.getVendorProp()+"="+dbmgr.getVendor();
    }

    /**
     */
    public final String getStoreVersion() {
        return SCHEMA_VERSION;
    }

    private void checkClosedAndSetInProgress() throws BrokerException {

        try {
            inprogresser.checkClosedAndSetInProgressWithWait(closeWaitTimeout, 
                br.getKString(br.I_WAIT_ACCESS_SHARECC_JDBCSTORE));

        } catch (IllegalStateException e) {
            logger.log(Logger.ERROR, BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED);
            throw new BrokerException(
                br.getKString(BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED));

        } catch (java.util.concurrent.TimeoutException e) { 
            String msg = br.getKString(
                br.W_TIMEOUT_WAIT_ACCESS_SHARECC_JDBCSTORE,
                closeWaitTimeout);
            logger.log(Logger.ERROR, msg);
            throw new BrokerException(msg);

        } catch (Exception e) {
            String msg = br.getKString(
                br.E_FAIL_ACCESS_SHARECC_JDBCSTORE, e.toString());
            logger.log(Logger.ERROR, msg);
            throw new BrokerException(msg);
        }
    }

    public ChangeRecordInfo storeChangeRecord(ChangeRecordInfo rec, boolean sync)
                                              throws BrokerException {
	    if (DEBUG) {
	    logger.log(Logger.DEBUG,
                "JDBCShareCCStore.storeChangeRecord called");
	    }

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getShareConfigRecordDAO().insert(null, rec);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy(dbmgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            inprogresser.setInProgress(false);
        }
    }

    public void storeResetRecord(ChangeRecordInfo rec, boolean canExist, boolean sync)
                                  throws BrokerException {
        if (DEBUG) {
            logger.log(Logger.INFO, "JDBCShareCCStore.storeResetRecord called");
        }

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getShareConfigRecordDAO().insertResetRecord(null, rec, null);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy(dbmgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            inprogresser.setInProgress(false);
        }
    }

    /**
     * Retrieves all the records whose sequence number greater than
     * the specified seq 
     * 
     * @return a List 
     */
    public List<ChangeRecordInfo> getChangeRecordsSince(Long seq, String resetUUID, boolean canReset)
    throws BrokerException {

        if (DEBUG) {
            logger.log(Logger.DEBUG,
                       "JDBCShareCCStore.getChangeRecordsSince("+seq+", "+resetUUID+")");
        }

        checkClosedAndSetInProgress();

	    try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getShareConfigRecordDAO().getRecords(
                                      null, seq, resetUUID, canReset);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy(dbmgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	    } finally {
	        inprogresser.setInProgress(false);
	    }
    }

    /**
     * Return all config records
     *
     * @return 
     * @exception BrokerException if an error occurs while getting the data
     */
    public List<ChangeRecordInfo> getAllChangeRecords() throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.DEBUG,
                "JDBCShareCCStore.getAllChangeRecords() called");
	    }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getShareConfigRecordDAO().getRecords(null, null, null, true);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy(dbmgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            inprogresser.setInProgress(false);
        }
    }

    /**
     * Clear all config change records 
     *
     * @param sync ignored
     * @exception BrokerException 
     */
    public void clearAllChangeRecords(boolean sync) throws BrokerException {

        if (DEBUG) {
	        logger.log(Logger.DEBUG,
                "JDBCShareCCStore.clearAllChangeRecords() called");
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getShareConfigRecordDAO().deleteAll(null);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if (retry == null) {
                        retry = new Util.RetryStrategy(dbmgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
	        inprogresser.setInProgress(false);
        }
    }

    public void beforeWaitAfterSetClosed() {
        if (dbmgr != null) {
            dbmgr.setIsClosing();
        }
    }

    public void close() {

        try {
            inprogresser.setClosedAndWaitWithTimeout(this, closeWaitTimeout,
                br.getKString(br.I_WAIT_ON_CLOSED_SHARECC_JDBCSTORE));
        } catch (Exception e) {
            logger.log(logger.WARNING, br.getKString(
                br.W_CLOSE_SHARECC_JDBCSTORE_EXCEPTION, e.toString()));
        }

	    dbmgr.close();
        dbmgr.setStoreInited(false);

	    if (DEBUG) {
	        logger.log(Logger.DEBUG, "JDBCShareConfigChangeStore.close done.");
	    }
    }

    public String getStoreType() {
        return Store.JDBC_STORE_TYPE;
    }

    /*
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public Hashtable getDebugState() throws BrokerException {

        Hashtable t = new Hashtable();
        t.put("JDBCSharedConfigChangeStore",
              "version:"+String.valueOf(SCHEMA_VERSION));

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection( true );

            Iterator itr = daoFactory.getAllDAOs().iterator();
            while ( itr.hasNext() ) {
                t.putAll( ((BaseDAO)itr.next()).getDebugInfo( conn ) );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            dbmgr.closeSQLObjects( null, null, conn, myex );
        }
        t.put("DBManager", dbmgr.getDebugState());

        return t;
    }

}
