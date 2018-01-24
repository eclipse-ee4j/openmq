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
 * @(#)DBTool.java	1.108 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.PassfileObfuscator;
import com.sun.messaging.jmq.util.PassfileObfuscatorImpl;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.persist.file.FileStore;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.ShareConfigChangeDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.JDBCShareConfigChangeStore;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.ShareConfigRecordDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.ShareConfigRecordDAOImpl;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.DBConnectionPool;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.TableSchema;
import com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecord;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.bridge.api.BridgeServiceManager;
import com.sun.messaging.bridge.api.JMSBridgeStore;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * This class is used to create, delete, and recreate database tables.
 * It may also be used to create a database.
 * All database specific information is obtained from property file,
 * except for username and password which may also be specified as command
 * line arguments.
 */
public class DBTool implements DBConstants {

    static final String SQLFILEDIR_PROP =
		DBManager.JDBC_PROP_PREFIX + "sqlfile.dirpath";

    static final String SQLFILENAME_PROP =
		DBManager.JDBC_PROP_PREFIX + "sqlfile.name";

    /*
     * All internal properties must start with "imq."
     */
    static final String STORE_PROPERTY_HABROKERS = "imq.cluster.haBrokers";
    static final String STORE_PROPERTY_SUPPORT_JMSBRIDGE = "imq.bridge.jmsbridge.tables";

    // parser exception reasons
    private static int EXTRA_CMD_SPECIFIED	= 0;
    private static int BAD_CMD_ARG		= 1;
    private static int BAD_OPT			= 2;
    private static int BAD_OPT_ARG		= 3;
    private static int MISSING_OPT_ARG		= 4;
    private static int MISSING_CMD_ARG		= 5;
    private static int MISSING_OPT          = 6;

    private static String CMD_NAME		= "dbmgrcmd";
    private static String CREATE_ALL_CMD	= "createall";
    private static String CREATE_TBL_CMD	= "createtbl";
    private static String CREATE_SHARECCTBL_CMD	= "create sharecc_tbl";
    private static String DELETE_TBL_CMD	= "deletetbl";
    private static String DELETE_SHARECCTBL_CMD	= "delete sharecc_tbl";
    private static String RECREATE_TBL_CMD	= "recreatetbl";
    private static String RECREATE_SHARECCTBL_CMD = "recreate sharecc_tbl";
    private static String REMOVE_BKR_CMD	= "removebkr";
    private static String REMOVE_JMSBRIDGE_CMD = "removejmsbridge";
    private static String DUMP_CMD		= "dump";
    private static String DUMP_SHARECCTBL_CMD   = "dump sharecc_tbl";
    private static String DROPTBL_CMD	        = "droptbl";
    private static String RESET_CMD		= "reset";
    private static String BACKUP_CMD            = "backup";
    private static String BACKUP_SHARECCTBL_CMD            = "backup sharecc_tbl";
    private static String RESTORE_CMD           = "restore";
    private static String RESTORE_SHARECCTBL_CMD = "restore sharecc_tbl";
    private static String UPGRADE_STORE_CMD     = "upgradestore";
    private static String UPGRADE_HASTORE_CMD   = "upgradehastore";
    private static String QUERY_CMD             = "query";

    private static String ARG_NAME		= "dbmgrarg";

    private static String CREATE_CMD_STR = "create";
    private static String DELETE_CMD_STR = "delete";
    private static String RECREATE_CMD_STR = "recreate";
    private static String REMOVE_CMD_STR = "remove";
    private static String UPGRADE_CMD_STR = "upgrade";
    private static String ARGU_ALL = "all";
    private static String ARGU_TBL = "tbl";
    private static String ARGU_SHARECCTBL = "sharecc_tbl";
    private static String ARGU_BKR = "bkr";
    private static String ARGU_JMSBRIDGE = "jmsbridge";
    private static String ARGU_OLDTBL = "oldtbl";
    private static String ARGU_LCK = "lck";
    private static String ARGU_STORE = "store";
    private static String ARGU_HASTORE = "hastore";
    private static String OPT_H = "-h";
    private static String OPT_LH = "-help";
    private static String OPT_V = "-v";
    private static String OPT_LV = "-version";
    private static String OPT_B = "-b";
    private static String OPT_N = "-n";
    private static String OPT_U = "-u";
    private static String OPT_P = "-p";
    private static String OPT_PW = "-pw";
    private static String OPT_PASSFILE = "-passfile";
    private static String OPT_D = "-D";
    private static String OPT_VARHOME = "-varhome";
    private static String OPT_VERBOSE = "-verbose";
    private static String OPT_DEBUG = "-debug";
    private static String OPT_DIR = "-dir";
    private static String OPT_FILE = "-file";
    private static String OPT_FORCE = "-f";
    private static String JMSBRIDGE_NAME_PROPERTY = "jmsbridge.name";

    private static BrokerResources br = Globals.getBrokerResources();
    private static BrokerConfig config;
    private static Logger logger;

    private Version version;
    private DBManager dbmgr = null;
    private boolean standalone = true;
    private boolean cliPasswdSpecified = false;
    private boolean debugSpecified = false;
    private boolean forceSpecified = false;

    DBTool(boolean standalone) {
	this.version = new Version();
	this.standalone = standalone;
    }

    private void doCreate(boolean createdb)
	throws BrokerException {
        doCreate(createdb, null);
    }

    private void doCreate(boolean createdb, CommDBManager mgrArg)
	throws BrokerException {

        CommDBManager mgr = (mgrArg == null ? dbmgr:mgrArg);
	Connection conn = null;
	try {
            if (createdb) {
                conn = mgr.connectToCreate();
                conn.setAutoCommit( true );
            } else {
                conn = mgr.getNewConnection( true ); // set autoCommit to true
            }

            // Check if store exist
            boolean continueOnError = false;
            int status = mgr.checkStoreExists( conn );
            if (status > 0) {
                if (!(mgr instanceof ShareConfigChangeDBManager)) { 
                // All tables have already been created
                throw new BrokerException(br.getKString(
                    BrokerResources.E_DATABASE_TABLE_ALREADY_CREATED));
                } else {
                throw new BrokerException(br.getKString(
                    BrokerResources.E_SHARECC_TABLE_ALREADY_CREATED,
                    Globals.getClusterID()));
                }
            } else if (status < 0) {
                // Some tables are missings so try to create the tables
                // but ignore error if table already exists
                continueOnError = true;
            }

            createTables( conn, continueOnError, mgr );

	    if (standalone) {
                if ( Globals.getHAEnabled() ) {
                    System.out.println( br.getString(
                        BrokerResources.I_DATABASE_TABLE_HA_CREATED,
                        Globals.getClusterID() ) );
                } else if (mgr instanceof ShareConfigChangeDBManager) {
                    System.out.println( br.getString(
                        BrokerResources.I_SHARECC_DATABASE_TABLE_CREATED,
                        Globals.getClusterID() ) );
                } else {
                    System.out.println(
                        br.getString(BrokerResources.I_DATABASE_TABLE_CREATED));
                }
	    }
	} catch (Throwable t) {
	    String url;
	    if (createdb)
		url = mgr.getCreateDBURL();
	    else
		url = mgr.getOpenDBURL();

	    throw new BrokerException(br.getKString(
                BrokerResources.E_CREATE_DATABASE_TABLE_FAILED, url), t);
	} finally {
            if ( createdb && conn != null ) {
                // Since the connection is not from the pool; we've to close it
                try {
                    conn.close();
                } catch (SQLException e) {
                    throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                            BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "Unable to close JDBC resources", e ) );
                }
            }
	}
    }

    // create database tables used in the current version of persistent store
    static void createTables( Connection conn ) throws BrokerException {
        createTables( conn, false );
    }

    static void createTables( Connection conn, boolean continueOnError )
        throws BrokerException {
        createTables(conn, continueOnError, null, null);
    }

    public static void createTables( Connection conn, 
                                     boolean continueOnError,
                                     CommDBManager mgrArg )
        throws BrokerException {
        createTables(conn, continueOnError, null, mgrArg);
    }

    static void createTables( Connection conn, boolean continueOnError, 
                              ArrayList tableDAOs)
        throws BrokerException {
        createTables(conn, continueOnError, tableDAOs, null);
    }

    //if tableDAOs != null, only create these tables 
    //else create current version of persist store
    static void createTables( Connection conn, boolean continueOnError, 
                              ArrayList tableDAOs, CommDBManager mgrArg)
        throws BrokerException {

        CommDBManager mgr = mgrArg; 
        if (mgr == null) {
            mgr = DBManager.getDBManager();
        }
        Iterator itr = null; 
        if (tableDAOs != null) {
            itr = tableDAOs.iterator();
        } else {
            itr = mgr.allDAOIterator();
        }
        while ( itr.hasNext() ) {
            BaseDAO dao = (BaseDAO)itr.next();
            try {
                Util.RetryStrategy retry = null;
                do {
                    try {
                        dao.createTable( conn );
                        break; // table created so break from retry loop
                    } catch ( Exception e ) {
                        // Exception will be log & re-throw if operation cannot be retry
                        if ( retry == null ) {
                            retry = new Util.RetryStrategy(mgr);
                        }
                        retry.assertShouldRetry( e );
                    }
                } while ( true );
            } catch (BrokerException be) {
                if ( Globals.getHAEnabled() ||
                     (mgr instanceof ShareConfigChangeDBManager) ) {
                    // Paused for a few secs to prevent race condition when two
                    // or more brokers try to create the tables at the same time
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {}
                }

                // Verify if the store has already been created
                if ( mgr.checkStoreExists( conn ) > 0 ) {
                    if (tableDAOs == null) {
                    Globals.getLogger().log(Logger.WARNING,
                        BrokerResources.E_CREATE_DATABASE_TABLE_FAILED,
                        Globals.getBrokerResources().getString(
                            BrokerResources.E_DATABASE_TABLE_ALREADY_CREATED));
                    continueOnError = true;
                    } else {
                    Globals.getLogger().log(Logger.WARNING,
                        BrokerResources.E_CREATE_DATABASE_TABLE_FAILED,
                        Globals.getBrokerResources().getString(
                            BrokerResources.E_THE_DATABASE_TABLE_ALREADY_CREATED, dao.getTableName()));
                    }
                    break;
                } else if (continueOnError) {
                    // Just log msg and continue
                    Globals.getLogger().log(Logger.WARNING, be.toString(), be.getCause());
                } else {
                    throw be;
                }
            }
        }

        if ( tableDAOs != null || !(mgr instanceof DBManager) ) {
            return;
        }

        DAOFactory daoFactory = ((DBManager)mgr).getDAOFactory();

	    // Insert version info in the version table
        VersionDAO versionDAO = daoFactory.getVersionDAO();
        try {
            if ( continueOnError ) {
                // Do this only if version is missing from version table
                int storeVersion = versionDAO.getStoreVersion( conn );
                if ( storeVersion != JDBCStore.STORE_VERSION ) {
                    versionDAO.insert( conn, JDBCStore.STORE_VERSION );
                }
            } else {
                versionDAO.insert( conn, JDBCStore.STORE_VERSION );
            }
        } catch (BrokerException be) {
            if ( Globals.getHAEnabled() ) {
                // Re-check if version info has been added by another broker
                int storeVersion = versionDAO.getStoreVersion( conn );
                if ( storeVersion != JDBCStore.STORE_VERSION ) {
                    throw be; // Re-throw the exception
                }
            } else {
                throw be; // Re-throw the exception
            }
        }

        // Insert a unique store session for the stand-alone broker
        if ( !Globals.getHAEnabled() ) {
            // Do this only if store session is missing from session table
            String brokerID = Globals.getBrokerID();
            StoreSessionDAO sessionDAO = daoFactory.getStoreSessionDAO();
            if ( sessionDAO.getStoreSession( conn, brokerID ) <= 0 ) {
                sessionDAO.insert( conn, brokerID, new UID().longValue(), true );
            }
        }

        PropertyDAO dao = daoFactory.getPropertyDAO();
        dao.update( conn, STORE_PROPERTY_SUPPORT_JMSBRIDGE, Boolean.valueOf(true)); 

        if (JDBCStore.ENABLE_STORED_PROC) {
            createStoredProcs( conn );
        }
    }

    /**
     */
    private static void createStoredProcs( Connection conn )
	throws BrokerException {

        ArrayList<BaseDAO> daos = new ArrayList<BaseDAO>();

	CommDBManager mgr = DBManager.getDBManager();
        Iterator itr = mgr.allDAOIterator();
        while ( itr.hasNext() ) {
            BaseDAO dao = (BaseDAO)itr.next();
            try {
        	Util.RetryStrategy retry = null;
        	do {
                    try {
        		dao.createStoredProc( conn );
        		break; 
                    } catch ( Exception e ) {
        		// Exception will be log & re-throw if operation cannot be retry
        		if ( retry == null ) {
                            retry = new Util.RetryStrategy(mgr);
        		}
        		retry.assertShouldRetry( e );
                    }
        	} while ( true );
                daos.add(dao);
            } catch (BrokerException be) {
                try {
                dropStoredProcs( conn, daos );
                } catch (Exception e) {
                Globals.getLogger().logStack(Logger.WARNING, be.getMessage(), be);
                }
                throw be;
            }
        }
    }


    /** 
     */
    private static void dropStoredProcs( Connection conn, ArrayList<BaseDAO> daos )
        throws BrokerException {

        CommDBManager mgr = DBManager.getDBManager();

        Iterator<BaseDAO> itr = null;
        if (daos == null) {
            itr = mgr.allDAOIterator();
        } else {
            itr = daos.iterator();
        }
        while ( itr.hasNext() ) {
            BaseDAO dao = itr.next();
            try {
                Util.RetryStrategy retry = null;
                do {
                    try {
                        dao.dropStoredProc( conn );
                        break; // table created so break from retry loop
                    } catch ( Exception e ) {
                        // Exception will be log & re-throw if operation cannot be retry
                        if ( retry == null ) {
                            retry = new Util.RetryStrategy(mgr);
                        }
                        retry.assertShouldRetry( e );
                    }
                } while ( true );
            } catch (BrokerException be) {
                Globals.getLogger().logStack(Logger.WARNING, be.getMessage(), be);
            }
        }
    }


    static void updateStoreVersion410IfNecessary(Connection conn, DBManager dbMgr)
        throws BrokerException {

        try {
            if (!Globals.getHAEnabled()) {
               dbMgr.lockTables(conn, true);
            }
            DAOFactory daoFactory = dbMgr.getDAOFactory();
            VersionDAO versionDAO  = daoFactory.getVersionDAO();
            int storeVersion = versionDAO.getStoreVersion(conn);

            if (storeVersion != JDBCStore.STORE_VERSION) {
                throw new BrokerException(br.getKString(
                          BrokerResources.E_BAD_STORE_VERSION,
                          String.valueOf(storeVersion), 
                          String.valueOf(JDBCStore.STORE_VERSION)));
            }
            PropertyDAO dao = daoFactory.getPropertyDAO();
            if (!dao.hasProperty(conn, STORE_PROPERTY_SUPPORT_JMSBRIDGE)) {
                ArrayList daos = new ArrayList();
                daos.add(daoFactory.getTMLogRecordDAOJMSBG());
                daos.add(daoFactory. getJMSBGDAO());
                createTables(conn, false, daos);

                dao.update( conn, STORE_PROPERTY_SUPPORT_JMSBRIDGE, Boolean.valueOf(true)); 
            }

        } finally {
            if (!Globals.getHAEnabled()) {
                dbMgr.lockTables(conn, false);
            }
        }
    }

    private void doReset() throws BrokerException {

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(true);

            // unlock the tables
	    dbmgr.lockTables(conn, false); // false = unlock
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex );
        }
    }

    private void doDelete(String arg, CommDBManager mgrArg, 
                          DeleteStatus deleteStatus)
                          throws BrokerException {

    CommDBManager mgr = (mgrArg == null ? dbmgr:mgrArg);
    boolean deleted = false;
    Connection conn = null;
    Exception myex = null;
	try {
            conn = mgr.getConnection(true);

	    if (arg == null || arg.length() == 0) {
                if (mgr instanceof ShareConfigChangeDBManager) {
                    String[] oldnames = mgr.getAllOldTableNames();
                    if (oldnames != null && oldnames.length > 0) {
                        dropTables( conn, mgr.getAllOldTableNames(), true, true, mgr );
                    }
                }
                // Check if store exist
                boolean continueOnError = false;
                int status = mgr.checkStoreExists( conn );
                if (status > 0 && !(mgr instanceof ShareConfigChangeDBManager)) {
                    // Verify cluster is not active in HA mode
                    if (!forceSpecified && Globals.getHAEnabled() &&
                        ((DBManager)mgr).isHAClusterActive(conn)) {
                        throw new BrokerException(br.getKString(
                            BrokerResources.E_HA_CLUSTER_STILL_ACTIVE, dbmgr.getClusterID()));
                    }

                    try {
                        // lock the tables first (implemented since version 350);
                        // note that we don't need to unlock the tables since
                        // the tables will be dropped when we are done
                        if (!forceSpecified) {
                            mgr.lockTables(conn, true); // true = lock
                        }
                    } catch ( BrokerException e ) {
                        if ( e.getStatusCode() == Status.NOT_FOUND ) {
                            // For some reason if version table doesn't exist or
                            // version data is not found we can just ignore the error!
                            continueOnError = true;
                        } else {
                            throw e;
                        }
                    }
                } else if (status > 0 && (mgr instanceof ShareConfigChangeDBManager)) {

                } else if (status < 0) {
                    // Some tables are missings so try to delete the rest
                    // but ignore error if table does not exists
                    continueOnError = true;
                } else {
                    if (deleteStatus == null) {
                        if (!(mgr instanceof ShareConfigChangeDBManager)) {
                            throw new BrokerException(br.getKString(
                               BrokerResources.E_DATABASE_TABLE_ALREADY_DELETED));
                        } else {
                            throw new BrokerException(br.getKString(
                               BrokerResources.E_SHARECC_TABLE_ALREADY_DELETED,
                               Globals.getClusterID()));
                        }
                    } else { 
                        deleteStatus.deleted = true;
                    }
                }

                if (deleteStatus == null || !deleteStatus.deleted) {
		    deleted = dropTables(conn, continueOnError, mgrArg);
                    if (deleted && deleteStatus != null) {
                        deleteStatus.deleted = true;
                    }
                }
	    } else if (arg.equals(ARGU_OLDTBL)) {
                int oldStoreVersion = -1;
                if ( checkVersion( conn,
                    VERSION_TBL_37 + dbmgr.getBrokerID(), dbmgr) ) {
                    oldStoreVersion = JDBCStore.OLD_STORE_VERSION_370;
                } else if ( checkVersion( conn,
                    VERSION_TBL_35 + dbmgr.getBrokerID(), dbmgr ) ) {
                    oldStoreVersion = JDBCStore.OLD_STORE_VERSION_350;
                } else {
                    throw new BrokerException("Old persistent store (version " +
                        JDBCStore.OLD_STORE_VERSION_370 + ") not found");
                }

                deleted = dropTables(conn, dbmgr.getTableNames(oldStoreVersion),
                                     false, false, dbmgr);
	    } else {
		// not possible since argument is checked already
	    }

	    if (standalone && deleted && deleteStatus == null) {
                if ( Globals.getHAEnabled() ) {
                    System.out.println( br.getString(
                        BrokerResources.I_DATABASE_TABLE_HA_DELETED,
                        Globals.getClusterID() ) );
                } else if (mgr instanceof ShareConfigChangeDBManager) {
                    System.out.println( br.getString(
                        BrokerResources.I_SHARECC_DATABASE_TABLE_DELETED,
                        Globals.getClusterID() ) );
                } else {
                    System.out.println(br.getString(BrokerResources.I_DATABASE_TABLE_DELETED));
                }
	    }
	} catch (Exception e) {
        myex = e;
        if (debugSpecified) {
            e.printStackTrace();
        }
        if (!(mgr instanceof ShareConfigChangeDBManager)) {
	    throw new BrokerException(
                br.getKString(BrokerResources.E_DELETE_DATABASE_TABLE_FAILED,
                    mgr.getOpenDBURL()), e);
        } else {
	    throw new BrokerException(
                br.getKString(BrokerResources.E_SHARECC_FAIL_DELETE_TABLE,
                Globals.getClusterID(), mgr.getOpenDBURL()), e);
        }
	} finally {
        Util.close( null, null, conn, myex, mgrArg );
	}
    }

    public static boolean dropTables(Connection conn, String oldnames[], 
                                     boolean oldsupplementck, 
                                     boolean continueOnError, CommDBManager mgrArg)
                                     throws SQLException, BrokerException {

        if ( oldnames == null || oldnames.length == 0 ) {
             return true;
        }
        CommDBManager mgr = (mgrArg == null ? DBManager.getDBManager():mgrArg);
        boolean deleted = false;
        Exception myex = null;
        Statement stmt = null;
        try {
            for ( int i = 0, len = oldnames.length; i < len; i++ ) {
                try {
                    String oldname = oldnames[i];
                    stmt = conn.createStatement();
                    Globals.getLogger().logToAll( Logger.INFO,
                        br.getString( BrokerResources.I_DROP_TABLE, oldname ) );
                    mgr.executeUpdateStatement( stmt, "DROP TABLE " + oldname );
                    if ( oldsupplementck && 
                         mgr.hasSupplementForCreateDrop(oldname) ) {
                         mgr.dropOldTableSupplement(stmt, oldname, false);
                    }
                } catch (Exception e) {
                    if (continueOnError) {
                        Globals.getLogger().log(Logger.WARNING, e.toString(), e.getCause());
                    } else {
                        myex = e;
                        throw e;
                    }
                }
            }
            deleted = true;
        } catch (Exception e) {
            Object[] args = { Arrays.asList(oldnames), e.getMessage() };
            String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.X_DELETE_DATABASE_TABLES, args);
            Globals.getLogger().logStack(Logger.ERROR, emsg, e);
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(e.getMessage(), e);
        } finally {
            Util.close( null, stmt, null, myex, mgr );
        }
        return deleted;
    }

    public static boolean dropTables(Connection conn, 
        boolean continueOnError, CommDBManager mgrArg)
        throws SQLException, BrokerException {

        CommDBManager mgr = (mgrArg == null ? DBManager.getDBManager():mgrArg);
        boolean deleted = false;
        Exception myex = null;
        try {
            Iterator itr = mgr.allDAOIterator();
            while ( itr.hasNext() ) {
                BaseDAO dao = (BaseDAO)itr.next();
                try {
                     Util.RetryStrategy retry = null;
                     do {
                         try {
                             dao.dropTable( conn );
                             if (mgr instanceof DBManager) {
                                 dao.dropStoredProc( conn );
                             }
                             break; 
                         } catch ( Exception e ) {
                             if ( retry == null ) {
                                 retry = new Util.RetryStrategy(mgr);
                             }
                             retry.assertShouldRetry( e );
                         }
                     } while ( true );
                } catch (BrokerException be) {
                    if (continueOnError) {
                        Globals.getLogger().log(Logger.WARNING, be.toString(), be.getCause());
                    } else {
                        throw be;
                    }
                }
            }
            deleted = true;
        } catch ( Exception e ) {
            if (!(mgr instanceof ShareConfigChangeDBManager)) {
                Globals.getLogger().log(Logger.ERROR,
                    BrokerResources.E_DELETE_DATABASE_TABLE_FAILED,
                    e.toString(), e);
            } else {
                Globals.getLogger().log(Logger.ERROR,
                    BrokerResources.E_SHARECC_FAIL_DELETE_TABLE,
                    Globals.getClusterID(), e.toString(), e);
            }
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(e.getMessage(), e);
        }
	return deleted;
    }

    private static boolean checkVersion(Connection conn,
        String vtable, CommDBManager mgr)
        throws BrokerException {

        // get version from table
        String selectSQL = "SELECT * FROM " + vtable;

        Statement stmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            stmt = conn.createStatement();
            rs = mgr.executeQueryStatement(stmt, selectSQL);
        } catch (SQLException e) {
            myex = e;
            // assume that the table does not exist
            return false;
        } finally {
            Util.close(rs, stmt, null, myex);
        }

        return true;
    }

    private void doRecreate() throws BrokerException {
        doRecreate(null);
    }

    private static class DeleteStatus {
        boolean deleted = false;
    }

    private void doRecreate(CommDBManager mgrArg) throws BrokerException {

        CommDBManager mgr = (mgrArg == null ? dbmgr : mgrArg);  
        Connection conn = null;
        Throwable myex = null;
	try {
            DeleteStatus deleteStatus = new DeleteStatus();
            doDelete(null, mgr, deleteStatus); 
            conn = mgr.getConnection(true);

	    createTables( conn, false, mgrArg );

	    if (standalone) {
                if ( Globals.getHAEnabled() ) {
                    System.out.println( br.getString(
                        BrokerResources.I_DATABASE_TABLE_HA_CREATED,
                        Globals.getClusterID() ) );

                } else if (mgr instanceof ShareConfigChangeDBManager) {
                    System.out.println( br.getString(
                        BrokerResources.I_SHARECC_DATABASE_TABLE_CREATED,
                        Globals.getClusterID() ) );
                } else {
                    System.out.println(
                        br.getString(BrokerResources.I_DATABASE_TABLE_CREATED) );
                }
	    }
	} catch (Throwable t) {
            myex = t;
            if (!(mgr instanceof ShareConfigChangeDBManager)) {
	        throw new BrokerException(
                    br.getKString(BrokerResources.E_RECREATE_DATABASE_TABLE_FAILED,
                    mgr.getOpenDBURL()), t);
            } else {
	        throw new BrokerException(
                    br.getKString(BrokerResources.E_SHARECC_FAIL_RECREATE_TABLE,
                    Globals.getClusterID(), mgr.getOpenDBURL()), t);
            }
	} finally {
            Util.close( null, null, conn, myex, mgrArg );
	}
    }

    private void doRemoveBkr() throws BrokerException {

        String brokerID = Globals.getBrokerID();
	Connection conn = null;
    String errorMsg = null;
    Exception myex = null;
	try {
            conn = dbmgr.getConnection(true);

            // try to lock the tables first
            dbmgr.lockTables(conn, true); // true = lock

            try {
                DAOFactory daoFactory = dbmgr.getDAOFactory();
                BrokerDAO brokerDAO = daoFactory.getBrokerDAO();
                BrokerState state = brokerDAO.getState(conn, brokerID);
                if ( !state.isActiveState() ) {
                    try {
                        // Remove broker's and its data
                        conn.setAutoCommit(false); // do this in 1 txn
                        daoFactory.getConsumerStateDAO().deleteAll(conn);
                        daoFactory.getMessageDAO().deleteAll(conn);
                        daoFactory.getDestinationDAO().deleteAll(conn);
                        daoFactory.getTransactionDAO().deleteAll(conn);
                        daoFactory.getStoreSessionDAO().deleteByBrokerID(conn, brokerID);
                        brokerDAO.delete(conn, brokerID);
                        daoFactory.getTMLogRecordDAOJMSBG().deleteAll(conn);
                        daoFactory.getJMSBGDAO().deleteAll(conn);
                        conn.commit(); // commit changes
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } else {
                    errorMsg = br.getString(
                        BrokerResources.E_REMOVE_BROKER_FAILED,
                        brokerID, "broker is still active - " + state );
                }
            } finally {
                // remove the lock
                dbmgr.lockTables(conn, false); // false = unlock
            }
	} catch (Exception e) {
        myex = e;
	    throw new BrokerException(
		br.getKString( BrokerResources.E_REMOVE_BROKER_2_FAILED,
                    brokerID ), e );
	} finally {
        Util.close( null, null, conn, myex );
	}

        if ( standalone ) {
            if ( errorMsg == null ) {
                System.out.println(
                    br.getString( BrokerResources.I_BROKER_REMOVE, brokerID ) );
            } else {
                System.out.println(errorMsg);
            }
        }
    }

    private void doRemoveJMSBridge(String bname) throws BrokerException {

        if (bname == null) {
            try {
            checkArg(null, null, OPT_N, 0, 0);
            } catch (ParserException e) {
            handleParserException(e);
            exit(1);
            }
        }

        String brokerID = Globals.getBrokerID();
        Connection conn = null;
        String errorMsg = null;
        Exception myex = null;
        try {

            if (!askConfirmation("Removing JMS Bridge "+bname+".")) {
                return; 
            }

            conn = dbmgr.getConnection(true);

            dbmgr.lockTables(conn, true); // true = lock

            try {
                DAOFactory daoFactory = dbmgr.getDAOFactory();
                BrokerDAO brokerDAO = daoFactory.getBrokerDAO();
                BrokerState state = brokerDAO.getState(conn, brokerID);
                if ( !state.isActiveState() ) {
                    try {
                        conn.setAutoCommit(false); 
                        daoFactory.getJMSBGDAO().delete(conn, bname, null);
                        daoFactory.getTMLogRecordDAOJMSBG().deleteAllByName(conn, bname, null);
                        conn.commit();
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } else {
                    errorMsg = br.getString(BrokerResources.E_BROKER_STILL_ACTIVE);
                }
            } finally {
                dbmgr.lockTables(conn, false);
            }
	    } catch (Exception e) {
            myex = e;
            throw new BrokerException(br.getString(
            br.E_REMOVE_JMSBRIDGE_FAILED, bname, e.getMessage()), e);
	    } finally {
            Util.close( null, null, conn, myex );
	    }

        if ( standalone ) {
            if ( errorMsg == null ) {
                System.out.println(br.getString(
                BrokerResources.I_JMSBRIDGE_REMOVED_FROM_HA_STORE, bname ));
            } else {
                System.out.println(errorMsg);
            }
        }
    }

    private void doUpgrade(boolean haStore)
	throws BrokerException {

	Connection conn = null;
    BrokerException myex = null;
	try {
            conn = dbmgr.getConnection(true);

            if ( haStore ) {
                new UpgradeHAStore().upgradeStore( conn );
            } else {
                JDBCStore store = (JDBCStore)StoreManager.getStore();
                int oldStoreVersion = -1;
                if ( store.checkOldStoreVersion( conn,
                    dbmgr.getTableName(VersionDAO.TABLE + DBConstants.SCHEMA_VERSION_40),
                    VersionDAO.STORE_VERSION_COLUMN,
                    JDBCStore.OLD_STORE_VERSION_400 ) ) {
                    oldStoreVersion = JDBCStore.OLD_STORE_VERSION_400;
                } else if ( store.checkOldStoreVersion( conn,
                    VERSION_TBL_37 + dbmgr.getBrokerID(), TVERSION_CVERSION,
                    JDBCStore.OLD_STORE_VERSION_370 ) ) {
                    oldStoreVersion = JDBCStore.OLD_STORE_VERSION_370;
                } else if ( store.checkOldStoreVersion( conn,
                    VERSION_TBL_35 + dbmgr.getBrokerID(), TVERSION_CVERSION,
                    JDBCStore.OLD_STORE_VERSION_350 ) ) {
                    oldStoreVersion = JDBCStore.OLD_STORE_VERSION_350;
                } else {
                    throw new BrokerException("Old persistent store (version " +
                        JDBCStore.OLD_STORE_VERSION_400 + ") not found");
                }

                new UpgradeStore(store, oldStoreVersion).upgradeStore(conn);
            }
    } catch (BrokerException e) {
        myex = e;
        throw e;
	} finally {
        Util.close( null, null, conn, myex );
	}
    }

    /**
     * dump info about the database.
     * for debugging purpose
     */
    private void doDump(String arg, CommDBManager mgrArg)
    throws SQLException, BrokerException {

    CommDBManager mgr = (mgrArg == null ? dbmgr:mgrArg);

    String names[] = null;

    if (!(mgr instanceof ShareConfigChangeDBManager)) {
        int storeVersion = JDBCStore.STORE_VERSION;
	    if (arg != null) {
	        try {
                storeVersion = Integer.parseInt(arg);
	        } catch (NumberFormatException e) {
		        storeVersion = 0;
	        }
	    }

        if ( storeVersion != JDBCStore.STORE_VERSION ) {
            // Old store
            names = mgr.getTableNames(storeVersion);
            if (names == null || names.length == 0) {
                throw new BrokerException("version " + arg + " not supported");
            }
        }
    }

        Connection conn = null;
        Exception myex = null;
        try {
            conn = mgr.getConnection(true);
            doDump(conn, names, mgr);
        } catch (SQLException e) {
            myex = e;
            throw e;
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex, mgr );
        }
    }

    static void doDump(Connection dbconn, String tables[],
                       CommDBManager mgrArg)
                       throws BrokerException, SQLException {

        CommDBManager mgr = mgrArg;

        if ( tables != null && tables.length > 0 ) {
            Statement stmt = null;
            Exception myex = null;
            try {
                stmt = dbconn.createStatement();
                for (int i = 0; i < tables.length; i++) {
                    ResultSet r = null;
                    String tname = tables[i];
                    String sql = "SELECT COUNT(*) FROM " + tname;
                    try {
                        r = mgr.executeQueryStatement(stmt, sql);
                        if (r.next()) {
                            System.out.println(tname + ": number of row="
                                                + r.getInt(1));
                        }
                        r.close();
                    } catch (SQLException e) {
                        SQLException ex = mgr.wrapSQLException(
                                                        "[" + sql + "]", e);
                        logger.log(Logger.ERROR, "failed to dump tables", ex);
                    }
                }
            } catch (SQLException e) {
                myex = e;
                throw e;
            } finally {
                Util.close( null, stmt, null, myex, mgr );
            }
        } else {
            // Starting in 4.0, use info from BaseDAO.getDebugInfo()
            Iterator itr = mgr.allDAOIterator();
            while ( itr.hasNext() ) {
                BaseDAO dao = (BaseDAO)itr.next();
                System.out.println( dao.getDebugInfo( dbconn ).toString() );

            }
        }
    }

    /**
     * drop tables that start with mq and match the specified table name pattern.
     * for internal use!!!
     */
    private void doDropTablesByPattern(String arg) throws BrokerException {

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(true);
            Map map = dbmgr.getTableNamesFromDB(conn, arg, false);
            String[] names = (String[])map.values().toArray(new String[map.size()]);

            if ( names == null || names.length == 0 ) {
                System.out.println(
                    "There were no tables that match the name pattern 'MQ*" +
                    arg + "'.");
                return;
            }

            // List all tables that match the name pattern
            System.out.println(
                "Tables matching the name pattern 'MQ*" + arg + "':");

            for (int i = 0, len = names.length; i < len; i++) {
                System.out.println("\t" + names[i]);
            }

            if (!askConfirmation("Remove all tables that match 'MQ*"+ arg +"'.")) {
                // if not positive confirmation, do nothing!
                return;
            }

            dropTables(conn, names, false, true, dbmgr);
        } catch (Exception e) {
            myex = e;
            System.err.println(
                "Failed to drop tables by name pattern: " + e.getMessage());
            throw new BrokerException(
                br.getKString(BrokerResources.E_DELETE_DATABASE_TABLE_FAILED,
                    "table name pattern '" + arg + "'"), e);
        } finally {
            Util.close( null, null, conn, myex );
        }
    }


    private boolean askConfirmation(String prompt) throws IOException {
        // Ask for confirmation only if force option is not specified
        if (forceSpecified) return true;

        System.out.println(prompt+"\nDo you wish to proceed? [y/n] ");
        System.out.flush();

        String val = (new BufferedReader(new InputStreamReader(System.in))).readLine();

        if (!"y".equalsIgnoreCase(val) && !"yes".equalsIgnoreCase(val)) {
            return false;
        }
        return true;
    }


    /**
     * Backup the JDBC store to filebased backup files.
     */
    private void doBackup() throws BrokerException {

        if (!Globals.getHAEnabled()) {
            throw new BrokerException(br.getKString(BrokerResources.I_HA_NOT_ENABLE,
                dbmgr.getBrokerID()));
        }

        // instantiate the file store.
        Properties props = System.getProperties();
        String clusterID = Globals.getClusterID();
        String backupDir = (String)props.get(Globals.IMQ + ".backupdir") +
            File.separator + clusterID;

        logger.logToAll(Logger.INFO,
            "Backup persistent store for HA cluster " + clusterID);

        FileStore fileStore = new FileStore(backupDir, false);

        // for backup, need to clear the store before storing anything.
        fileStore.clearAll(false);

        JDBCStore jdbcStore = null;
        try {
            jdbcStore = (JDBCStore)StoreManager.getStore();

            /*
             * For data that are not broker specific, i.e. properties, change
             * records, consumers, brokers, and sessions, we will store
             * those data on the top level directory (a.k.a cluster filestore).
             */

            // Properties table
            Properties properties = jdbcStore.getAllProperties();
            Iterator propItr = properties.entrySet().iterator();
            while (propItr.hasNext()) {
                Map.Entry entry = (Map.Entry)propItr.next();
                fileStore.updateProperty(
                    (String)entry.getKey(), entry.getValue(), false);

            }
            propItr = null; properties = null;

            // Configuration Change Record table
            List<ChangeRecordInfo> records = jdbcStore.getAllConfigRecords();
            for (int i = 0, len = records.size(); i < len; i++) {
                fileStore.storeConfigChangeRecord(
                    records.get(i).getTimestamp(),
                    (byte[])records.get(i).getRecord(), false);
            }
            records = null;

            // Consumer table
            Consumer[] consumerArray = jdbcStore.getAllInterests();
            for (int i = 0; i < consumerArray.length; i++) {
                fileStore.storeInterest(consumerArray[i], false);
            }
            consumerArray = null;

            // Broker & Session table - store all HABrokerInfo as a property
            HashMap bkrMap = jdbcStore.getAllBrokerInfos(true);
            List haBrokers = new ArrayList(bkrMap.values());
            fileStore.updateProperty(STORE_PROPERTY_HABROKERS, haBrokers, true);

            /*
             * For each broker in the cluster, we will store broker specific
             * data, destinations, messages, transactions, acknowledgements
             * in their own filestore (a.k.a broker filestore).
             */
            Iterator bkrItr = haBrokers.iterator();
            while (bkrItr.hasNext()) {
                // Backup data for each broker
                HABrokerInfo bkrInfo = (HABrokerInfo)bkrItr.next();
                String brokerID = bkrInfo.getId();

                logger.logToAll(Logger.INFO,
                    "Backup persistent data for broker " + brokerID);

                FileStore bkrFS = null;
                JMSBridgeStore jmsbridgeStore = null;
                try {
                    String instanceRootDir = backupDir + File.separator + brokerID;
                    bkrFS = new FileStore(instanceRootDir, false);
                    bkrFS.clearAll(false);

                    // Destination table.
                    Destination[] dstArray = jdbcStore.getAllDestinations(brokerID);
                    for (int i = 0, len = dstArray.length; i < len; i++) {
                        DestinationUID did = dstArray[i].getDestinationUID();
                        Destination dst = bkrFS.getDestination(did);
                        if (dst == null) {
                            // Store the destination if not found
                            bkrFS.storeDestination(dstArray[i],  false);
                        }
                    }

                    // Storing messages for each destination.
                    for (int i = 0, len = dstArray.length; i < len; i++) {
                        Enumeration e = jdbcStore.messageEnumeration(dstArray[i]);
                        try {
                        for (; e.hasMoreElements();) {
                            DestinationUID did = dstArray[i].getDestinationUID();
                            Packet message = (Packet)e.nextElement();
                            SysMessageID mid = message.getSysMessageID();

                            // Get interest states for the message; Consumer State table
                            HashMap stateMap = jdbcStore.getInterestStates(did, mid);
                            if (stateMap == null || stateMap.isEmpty()) {
                                bkrFS.storeMessage(did, message, false);
                            } else {
                                int size = stateMap.size();
                                ConsumerUID[] iids = new ConsumerUID[size];
                                int[] states  = new int[size];
                                Iterator stateItr = stateMap.entrySet().iterator();
                                int j = 0;
                                while (stateItr.hasNext()) {
                                    Map.Entry entry = (Map.Entry)stateItr.next();
                                    iids[j] = (ConsumerUID)entry.getKey();
                                    states[j] = ((Integer)entry.getValue()).intValue();
                                    j++;
                                }
                                bkrFS.storeMessage(did, message, iids, states, false);
                            }

                        }

                        } finally {
                        jdbcStore.closeEnumeration(e);
                        }
                    }

                    // Transaction table
                    Collection txnList = jdbcStore.getTransactions(brokerID);
                    Iterator txnItr = txnList.iterator();
                    while (txnItr.hasNext()) {
                        TransactionUID tid = (TransactionUID)txnItr.next();
                        TransactionInfo txnInfo = jdbcStore.getTransactionInfo(tid);
                        TransactionAcknowledgement txnAck[] =
                            jdbcStore.getTransactionAcks(tid);
                        bkrFS.storeTransaction(tid, txnInfo, false);
                        for (int i = 0, len = txnAck.length; i < len; i++) {
                            bkrFS.storeTransactionAck(tid, txnAck[i], false);
                        }
                    }

                    /**************************************************
                     * JMSBridge
                     **************************************************/

                    Properties bp = new Properties();
                    bp.setProperty("instanceRootDir", instanceRootDir); 
                    bp.setProperty("reset", "true");
                    bp.setProperty("logdomain", "imqdbmgr");
                    bp.setProperty("identityName", Globals.getIdentityName());


                    BridgeServiceManager.getExportedService(JMSBridgeStore.class, "JMS", bp);

                    List bnames = jdbcStore.getJMSBridgesByBroker(brokerID, null);
                    Collections.sort(bnames);

                    String bname = null;
                    String currbname = null;
                    Iterator itr = bnames.iterator();
                    while (itr.hasNext()) {
                        bname = (String)itr.next();
                        if (currbname == null || !currbname.equals(bname)) {
                            currbname = bname;
                            bp.setProperty("jmsbridge", bname);
                            if (jmsbridgeStore != null) {
                                jmsbridgeStore.closeJMSBridgeStore();
                                jmsbridgeStore = null;
                            }
                            logger.logToAll(logger.INFO, "Backup JMS bridge "+bname);
                            jmsbridgeStore = (JMSBridgeStore)BridgeServiceManager.getExportedService(
                                             JMSBridgeStore.class, "JMS", bp);
                            List lrs = jdbcStore.getLogRecordsByNameByBroker(bname, brokerID, null);
                            logger.logToAll(logger.INFO, "\tBackup JMS bridge "+bname+" with "+lrs.size()+" TM log records");
                            byte[] lr = null;
                            Iterator itr1 = lrs.iterator();
                            while (itr1.hasNext()) {
                                lr = (byte[])itr1.next();
                                jmsbridgeStore.storeTMLogRecord(null, lr, currbname, true, null);
                            }
                         }
                    }

                } finally {
                    bkrFS.close();
                    if (jmsbridgeStore != null) jmsbridgeStore.closeJMSBridgeStore();
                }
            }

            logger.logToAll(Logger.INFO, "Backup persistent store complete.");
        } catch(Throwable ex){
            ex.printStackTrace();
            throw new BrokerException(ex.getMessage());
        } finally {
            if (fileStore != null) {
                try {
                    fileStore.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

            StoreManager.releaseStore(true);
        }
    }

    /*
     * Restore the JDBC store from filebased backup files.
     */
    private void doRestore() throws BrokerException {

        if (!Globals.getHAEnabled()) {
            throw new BrokerException(br.getKString(BrokerResources.I_HA_NOT_ENABLE,
                dbmgr.getBrokerID()));
        }

        // instantiate the file store.
        Properties props = System.getProperties();
        String clusterID = Globals.getClusterID();
        String backupDir = (String)props.get(Globals.IMQ + ".backupdir") +
            File.separator + clusterID;

        logger.logToAll(Logger.INFO,
            "Restore persistent store for HA cluster " + clusterID + " from backup dir: " + backupDir);

        FileStore fileStore = new FileStore(backupDir, false);

        // Brokers table.
        JDBCStore jdbcStore = null;
        try {
            // Re-create the jdbc store
            doRecreate();

            jdbcStore = (JDBCStore)StoreManager.getStore();

            /*
             * For data that are not broker specific, i.e. properties, change
             * records, consumers, brokers, and sessions, we will retrieve
             * those data from the top level directory (a.k.a cluster filestore).
             */

            // Properties table
            List haBrokers = null;
            Properties properties = fileStore.getAllProperties();
            Iterator propItr = properties.entrySet().iterator();
            while (propItr.hasNext()) {
                Map.Entry entry = (Map.Entry)propItr.next();
                String name = (String)entry.getKey();
                if (name.equals(STORE_PROPERTY_HABROKERS)) {
                    // Retrieve all HABrokerInfo from a property
                    haBrokers = (List)entry.getValue();
                } else {
                    jdbcStore.updateProperty(name, entry.getValue(), false);
                }
            }
            propItr = null; properties = null;

            if (haBrokers == null || haBrokers.isEmpty()) {
                throw new BrokerException(br.getKString(
                    BrokerResources.X_LOAD_ALL_BROKERINFO_FAILED));
            }

            // Configuration Change Record table
            List<ChangeRecordInfo> records = fileStore.getAllConfigRecords();
            for (int i = 0, len = records.size(); i < len; i++) {
                jdbcStore.storeConfigChangeRecord(
                    records.get(i).getTimestamp(),
                    records.get(i).getRecord(), false);
            }
            records = null; 

            // Consumer table
            Consumer[] consumerArray = fileStore.getAllInterests();
            for (int i = 0; i < consumerArray.length; i++) {
                jdbcStore.storeInterest(consumerArray[i], false);
            }
            consumerArray = null;

            // Broker & Session table.
            Iterator bkrItr = haBrokers.iterator();
            while (bkrItr.hasNext()) {
                HABrokerInfo bkrInfo = (HABrokerInfo)bkrItr.next();
                jdbcStore.addBrokerInfo(bkrInfo, false);
            }

            /*
             * For each broker in the cluster, we will retrieve broker specific
             * data, destinations, messages, transactions, acknowledgements
             * from their own filestore (a.k.a broker filestore).
             */
            bkrItr = haBrokers.iterator();
            while (bkrItr.hasNext()) {
                // Backup data for each broker
                HABrokerInfo bkrInfo = (HABrokerInfo)bkrItr.next();
                String brokerID = bkrInfo.getId();
                long sessionID = bkrInfo.getSessionID();

                logger.logToAll(Logger.INFO,
                    "Restore persistent data for broker " + brokerID);

                FileStore bkrFS = null;
                JMSBridgeStore jmsbridgeStore = null;
                try {
                    String instanceRootDir = backupDir + File.separator + brokerID;
                    bkrFS = new FileStore(instanceRootDir, false);

                    // Destination table.
                    Destination[] dstArray = bkrFS.getAllDestinations();
                    for (int i = 0, len = dstArray.length; i < len; i++) {
                        DestinationUID did = dstArray[i].getDestinationUID();
                        Destination dst = jdbcStore.getDestination(did);
                        if (dst == null) {
                            // Store the destination if not found
                            jdbcStore.storeDestination(dstArray[i], sessionID);
                        }
                    }

                    // Retrieve messages for each destination.
                    for (int i = 0, len = dstArray.length; i < len; i++) {
                        for (Enumeration e = bkrFS.messageEnumeration(dstArray[i]);
                            e.hasMoreElements();) {
                            DestinationUID did = dstArray[i].getDestinationUID();
                            Packet message = (Packet)e.nextElement();
                            SysMessageID mid = message.getSysMessageID();

                            // Get interest states for the message; Consumer State table
                            HashMap stateMap = bkrFS.getInterestStates(did, mid);
                            if (stateMap == null || stateMap.isEmpty()) {
                                jdbcStore.storeMessage(
                                    did, message, null, null, sessionID, false);
                            } else {
                                int size = stateMap.size();
                                ConsumerUID[] iids = new ConsumerUID[size];
                                int[] states  = new int[size];
                                Iterator stateItr = stateMap.entrySet().iterator();
                                int j = 0;
                                while (stateItr.hasNext()) {
                                    Map.Entry entry = (Map.Entry)stateItr.next();
                                    iids[j] = (ConsumerUID)entry.getKey();
                                    states[j] = ((Integer)entry.getValue()).intValue();
                                    j++;
                                }
                                jdbcStore.storeMessage(
                                    did, message, iids, states, sessionID, false);
                            }
                        }
                    }

                    // Transaction table
                    Collection txnList = bkrFS.getTransactions(brokerID);
                    Iterator txnItr = txnList.iterator();
                    while (txnItr.hasNext()) {
                        TransactionUID tid = (TransactionUID)txnItr.next();
                        TransactionInfo txnInfo = bkrFS.getTransactionInfo(tid);
                        TransactionAcknowledgement txnAck[] =
                            bkrFS.getTransactionAcks(tid);
                        jdbcStore.storeTransaction(tid, txnInfo, sessionID);
                        for (int i = 0, len = txnAck.length; i < len; i++) {
                            jdbcStore.storeTransactionAck(tid, txnAck[i], false);
                        }
                    }

                    /**************************************************
                     * JMSBridge
                     **************************************************/

                    Properties bp = new Properties();
                    bp.setProperty("instanceRootDir", instanceRootDir); 
                    bp.setProperty("reset", "false");
                    bp.setProperty("logdomain", "imqdbmgr");

                    jmsbridgeStore = (JMSBridgeStore)BridgeServiceManager.getExportedService(
                                     JMSBridgeStore.class, "JMS", bp);

                    if (jmsbridgeStore != null) {

                    List bnames = jmsbridgeStore.getJMSBridges(null);
                    String bname = null;
                    Iterator itr = bnames.iterator();
                    while (itr.hasNext()) {
                        bname = (String)itr.next();
                        jdbcStore.addJMSBridge(bname, true, null);
                    }
                    jmsbridgeStore.closeJMSBridgeStore();
                    jmsbridgeStore = null;

                    bname = null;
                    itr = bnames.iterator();
                    while (itr.hasNext()) {
                        bname = (String)itr.next();
                        bp.setProperty("jmsbridge", bname);
                        if (jmsbridgeStore != null) {
                            jmsbridgeStore.closeJMSBridgeStore();
                            jmsbridgeStore = null;
                        }
                        logger.logToAll(logger.INFO, "Restore JMS bridge "+bname);
                        jmsbridgeStore = (JMSBridgeStore)BridgeServiceManager.getExportedService(
                                         JMSBridgeStore.class, "JMS", bp);
                        List xids = jmsbridgeStore.getTMLogRecordKeysByName(bname, null);

                        logger.logToAll(logger.INFO, "\tRestore JMS bridge "+bname+" with "+xids.size()+" TM log records");

                        String xid = null;
                        byte[] lr = null;
                        Iterator itr1 = xids.iterator();
                        while (itr1.hasNext()) {
                            xid = (String)itr1.next();
                            lr = jmsbridgeStore.getTMLogRecord(xid, bname, null);
                            if (lr == null) {
                                logger.logToAll(Logger.INFO,
                                        "JMSBridge TM log record not found for "+xid);
                                continue;
                            }  
                            jdbcStore.storeTMLogRecord(xid, lr, bname, true, null);   
                         }
                    }
                    }

                } finally {
                    bkrFS.close();
                    if (jmsbridgeStore != null) jmsbridgeStore.closeJMSBridgeStore();
                }
            }

            logger.logToAll(Logger.INFO, "Restore persistent store complete.");
        } catch (Throwable ex){
            ex.printStackTrace();
            throw new BrokerException(ex.getMessage());
        } finally {
            if (fileStore != null) {
                try {
                    fileStore.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

            StoreManager.releaseStore(true);
        }
    }

    /**
     * Backup the shared table for cluster config change record to a file
     */
    private void doBackupSharecc() throws BrokerException {
        Properties props = System.getProperties();
        String clusterID = Globals.getClusterID();
        String backupfile = (String)props.get(
            ShareConfigChangeStore.CLUSTER_SHARECC_PROP_PREFIX+".backupfile");

        logger.logToAll(Logger.INFO, br.getKString(br.I_SHARECC_BACKUP, 
                                                   clusterID, backupfile));

        ShareConfigChangeDBManager mgr = ShareConfigChangeDBManager.getDBManager();
        ShareConfigRecordDAO dao = mgr.getDAOFactory().getShareConfigRecordDAO();
        try {
            List<ChangeRecordInfo> records = null;

            Util.RetryStrategy retry = null;
            do {
                try {
                    records = dao.getAllRecords( null, null );
                    break;
                } catch ( Exception e ) {
                    if ( retry == null ) {
                         retry = new Util.RetryStrategy(mgr);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );

            Globals.getLogger().logToAll(Logger.INFO, br.getKString(
                                         br.I_SHARECC_BACKUP_RECORDS,
                                         String.valueOf(records.size()),
                                         dao.getTableName()));

            ChangeRecord.backupRecords(records, backupfile, true);

            Globals.getLogger().logToAll(Logger.INFO, 
                                         br.getKString(br.I_SHARECC_BACKUP_RECORDS_SUCCESS,
                                         Globals.getClusterID(), backupfile));
        } catch (BrokerException e) {
            String emsg =  br.getKString(br.E_SHARECC_BACKUP_RECORDS_FAIL, e.getMessage());
            Globals.getLogger().logToAll(Logger.ERROR, emsg, e);
            throw e;
        }
    }

    /**
     * Restore the shared table for cluster config change record from a file
     */
    private void doRestoreSharecc() throws BrokerException {

        Properties props = System.getProperties();
        String clusterID = Globals.getClusterID();
        String backupfile = (String)props.get(
            ShareConfigChangeStore.CLUSTER_SHARECC_PROP_PREFIX+".backupfile");

        logger.logToAll(Logger.INFO, br.getKString(br.I_SHARECC_RESTORE,
                                                   clusterID, backupfile));

        ShareConfigChangeDBManager mgr = ShareConfigChangeDBManager.getDBManager();

        Connection conn = null;
        Exception myex = null;
        try {
            List<ChangeRecordInfo> records = ChangeRecord.prepareRestoreRecords(backupfile);

            ShareConfigRecordDAO dao = mgr.getDAOFactory().getShareConfigRecordDAO();

            Globals.getLogger().logToAll(Logger.INFO, br.getKString(
                                         br.I_SHARECC_RESTORE_RECORDS,
                                         String.valueOf(records.size()),
                                         backupfile));

            conn = mgr.getConnection( true );
            ChangeRecordInfo resetcri = ChangeRecord.makeResetRecord( true );
            try {

            mgr.lockTables( conn, true, resetcri );

            Connection myconn = null;
            Exception myee = null;
            try {
                 myconn = mgr.getConnection( false );
                 Util.RetryStrategy retry = null;
                 do {
                     try {
                         Iterator itr = records.iterator();
                         ChangeRecordInfo cri = null;
                         while (itr.hasNext()) {
                             cri = (ChangeRecordInfo)itr.next();
                             if (cri.getType() == ChangeRecordInfo.TYPE_RESET_PERSISTENCE) {
                                 itr.remove();
                                 continue;
                             }
                             cri.setResetUUID(resetcri.getUUID());
                             cri.setTimestamp(System.currentTimeMillis());
                             dao.insert( myconn, cri );
                         }
                         myconn.commit();
                         break;
                     } catch ( Exception e ) {
                         if ( retry == null ) {
                             retry = new Util.RetryStrategy(mgr);
                         }
                         retry.assertShouldRetry( e );
                     }
                } while ( true );
            } catch (BrokerException e) {
                myee = e;
                throw e;
            } finally {
                Util.close(null, null, myconn, myee, mgr);
            }

            } finally {
                try {
                    mgr.lockTables( conn, false );
                } catch (Exception e) {
                    logger.log(Logger.WARNING, br.getKString(
                        br.X_SHARECC_UNLOCK_TABLE, e.toString()), e);
                }
            }
            logger.logToAll(Logger.INFO, 
                br.getKString(br.I_SHARECC_RESTORE_RECORDS_SUCCESS,
                Globals.getClusterID(), backupfile));
        } catch (Exception e) {
            myex = e;
            String emsg =  br.getKString(br.E_SHARECC_RESTORE_RECORDS_FAIL, e.getMessage());
            Globals.getLogger().logToAll(Logger.ERROR, emsg, e);
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(emsg, e);
        } finally {
            Util.close( null, null, conn, myex, mgr ); 
        }
    }

    /*
     * Check persistence store.
     */
    private void doQuery() throws BrokerException {

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(true);

            if ( Globals.getHAEnabled() ) {
                System.out.println( br.getString(
                    BrokerResources.I_RUNNING_IN_HA,
                    Globals.getBrokerID(), Globals.getClusterID() ) );
            } else {
                System.out.println( br.getString(
                    BrokerResources.I_STANDALONE_INITIALIZED ) );
            }

            updateStoreVersion410IfNecessary( conn, dbmgr );
            int status = dbmgr.checkStoreExists( conn );
            if ( status == 0 ) {
                System.out.println( br.getString(
                    BrokerResources.E_NO_DATABASE_TABLES ) );
            } else if (status > 0) {
                // All tables have already been created
                System.out.println( br.getString(
                    BrokerResources.E_DATABASE_TABLE_ALREADY_CREATED ) );
            } else {
                // Some tables are missings
                System.out.println( br.getString(
                    BrokerResources.E_BAD_STORE_MISSING_TABLES ) );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex );
        }
    }

    private boolean printHelp(String[] args) {
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals(OPT_H) || args[i].equals(OPT_LH)) {
		return true;
	    }
	}
	return false;
    }

    private boolean printVersion(String[] args) {
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals(OPT_V) || args[i].equals(OPT_LV)) {
		return true;
	    }
	}
	return false;
    }

    // throw exception if argument is missing
    private void checkArg(String cmd, String opt, int next, int total)
	throws ParserException {
        checkArg(cmd, opt, null, next, total);
    }

    private void checkArg(String cmd, String opt, String missopt, int next, int total)
	throws ParserException {

	if (next >= total) {
	    ParserException pe;
	    if (cmd != null) {
		pe = new ParserException(MISSING_CMD_ARG);
		pe.cmd = cmd;
        } else if(missopt != null) {
		pe = new ParserException(MISSING_OPT);
		pe.opt = missopt;
	    } else {
		pe = new ParserException(MISSING_OPT_ARG);
		pe.opt = opt;
	    }

	    throw pe;
	}
    }

    private void throwParserException(int reason, String cmd, String cmdarg,
	String opt, String optarg) throws ParserException {
        throwParserException(reason, cmd, cmdarg, opt, optarg, null);
    }
    private void throwParserException(int reason, String cmd, String cmdarg,
	String opt, String optarg, Throwable cause) throws ParserException {

	ParserException pe = new ParserException(reason, cause);
	pe.cmd = cmd;
	pe.cmdarg = cmdarg;
	pe.opt = opt;
	pe.optarg = optarg;
	throw pe;
    }

    private void handleParserException(ParserException e) {
	if (e.reason == MISSING_CMD_ARG) {
	    System.out.println(br.getString(
			BrokerResources.E_MISSING_DBMGR_CMD_ARG, e.cmd));
	} else if (e.reason == MISSING_OPT_ARG) {
	    System.out.println(br.getString(
			BrokerResources.E_MISSING_DBMGR_OPT_ARG, e.opt));
	} else if (e.reason == MISSING_OPT) {
	    System.out.println(br.getString(
			BrokerResources.E_MISSING_DBMGR_OPT, e.opt));
	} else if (e.reason == BAD_CMD_ARG) {
	    System.out.println(br.getString(
			BrokerResources.E_INVALID_DBMGR_CMD_ARG, e.cmd, e.cmdarg));
	} else if (e.reason == BAD_OPT) {
	    if (e.opt.equals(OPT_P)) {
	        System.out.println(br.getString(BrokerResources.E_PASSWD_OPTION_NOT_SUPPORTED, e.opt));
	    } else {
	        System.out.println(br.getString(BrokerResources.E_INVALID_DBMGR_OPT, e.opt));
	    }
	} else if (e.reason == BAD_OPT_ARG) {
	    System.out.println(br.getString(
			BrokerResources.E_INVALID_DBMGR_OPT_ARG, e.opt, e.optarg));
	} else if (e.reason == EXTRA_CMD_SPECIFIED) {
	    System.out.println(br.getString(
			BrokerResources.E_EXTRA_DBMGR_CMD, e.cmd));
	}
    }

    private Properties parseArgs(String[] args) throws ParserException {

	Properties props = System.getProperties();
	boolean gotcmd = false;

	// check cmd and options: -b, -p, -u (-h and -v checked already)
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals(CREATE_CMD_STR)) {
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i], null,
					null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		// find argument
		if (args[i].equals(ARGU_ALL)) {
		    props.put(CMD_NAME, CREATE_ALL_CMD);
		} else if (args[i].equals(ARGU_TBL)) {
		    props.put(CMD_NAME, CREATE_TBL_CMD);
		} else if (args[i].equals(ARGU_SHARECCTBL)) {
		    props.put(CMD_NAME, CREATE_SHARECCTBL_CMD);
		} else {
		    throwParserException(BAD_CMD_ARG, CREATE_CMD_STR,
					args[i], null, null);
		}
	    } else if (args[i].equals(DELETE_CMD_STR)) {
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		// find argument
		if (args[i].equals(ARGU_TBL)) {
		    props.put(CMD_NAME, DELETE_TBL_CMD);
		} else if (args[i].equals(ARGU_SHARECCTBL)) {
		    props.put(CMD_NAME, DELETE_SHARECCTBL_CMD);
		} else if (args[i].equals(ARGU_OLDTBL)) {
		    props.put(CMD_NAME, DELETE_TBL_CMD);
		    props.put(ARG_NAME, ARGU_OLDTBL);
		} else {
		    throwParserException(BAD_CMD_ARG, DELETE_CMD_STR,
					args[i], null, null);
		}
	    } else if (args[i].equals(RECREATE_CMD_STR)) {
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		// find argument
		if (args[i].equals(ARGU_TBL)) {
		    props.put(CMD_NAME, RECREATE_TBL_CMD);
		} else if (args[i].equals(ARGU_SHARECCTBL)) {
		    props.put(CMD_NAME, RECREATE_SHARECCTBL_CMD);
		} else {
		    throwParserException(BAD_CMD_ARG, RECREATE_CMD_STR,
					args[i], null, null);
		}
	    } else if (args[i].equals(REMOVE_CMD_STR)) {
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		// find argument
		if (args[i].equals(ARGU_BKR)) {
		    props.put(CMD_NAME, REMOVE_BKR_CMD);
		} else if (args[i].equals(ARGU_JMSBRIDGE)) {
		    props.put(CMD_NAME, REMOVE_JMSBRIDGE_CMD);
		} else {
		    throwParserException(BAD_CMD_ARG, REMOVE_CMD_STR,
					args[i], null, null);
		}

        } else if (args[i].equals(DUMP_CMD)) {
		// private command for debug purpose only
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

        if (args[i].equals(ARGU_SHARECCTBL)) {
            props.put(CMD_NAME, DUMP_SHARECCTBL_CMD);
        } else {
            try {
                Integer.parseInt(args[i]);
            } catch (Exception e) {
                throwParserException(BAD_CMD_ARG, DUMP_CMD,
                args[i], null, null, e);     
            }
            props.put(CMD_NAME, DUMP_CMD);
            props.put(ARG_NAME, args[i]);
        }

        } else if (args[i].equals(DROPTBL_CMD)) {
		// private command to drop tables that match the specified
                // table name pattern for internal use only!!!
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		props.put(CMD_NAME, DROPTBL_CMD);
		props.put(ARG_NAME, args[i]);
	    } else if (args[i].equals(RESET_CMD)) {
		// command for resetting the table lock
		if (gotcmd) {
		    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
					null, null, null);
		} else {
		    gotcmd = true;
		}

		checkArg(args[i], null, ++i, args.length);

		// find argument
		if (args[i].equals(ARGU_LCK)) {
		    props.put(CMD_NAME, RESET_CMD);
		} else {
		    throwParserException(BAD_CMD_ARG, RESET_CMD,
					args[i], null, null);
		}
            } else if (args[i].equals(BACKUP_CMD)) {
                // command for backing up the jdbc store
                if (gotcmd) {
                    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
                    null, null, null);
                } else {
                    gotcmd = true;
                }

                checkArg(args[i], null, ++i, args.length);

                // find argument
                if (args[i].equals(OPT_DIR)) {
                    props.put(CMD_NAME, BACKUP_CMD);
                    checkArg(args[i], null, ++i, args.length);
                    props.put(Globals.IMQ + ".backupdir", args[i]);
                } else if (args[i].equals(ARGU_SHARECCTBL)) {
                    props.put(CMD_NAME, BACKUP_SHARECCTBL_CMD);
                    checkArg(null, null, OPT_FILE, ++i, args.length);
                    if (args[i].equals(OPT_FILE)) {
                        checkArg(null, args[i], null, ++i, args.length);
                        props.put(ShareConfigChangeStore.CLUSTER_SHARECC_PROP_PREFIX+
                                  ".backupfile", args[i]);
                    } else {
                        throwParserException(BAD_CMD_ARG, BACKUP_SHARECCTBL_CMD,
                            args[i], null, null);
                    }
                } else {
                    throwParserException(BAD_CMD_ARG, BACKUP_CMD,
                    args[i], null, null);
                }
            } else if (args[i].equals(RESTORE_CMD)) {
                // command for backing up the jdbc store
                if (gotcmd) {
                    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
                    null, null, null);
                } else {
                    gotcmd = true;
                }

                checkArg(args[i], null, ++i, args.length);

                // find argument
                if (args[i].equals(OPT_DIR)) {
                    props.put(CMD_NAME, RESTORE_CMD);
                    checkArg(args[i], null, ++i, args.length);
                    props.put(Globals.IMQ + ".backupdir", args[i]);
                } else if (args[i].equals(ARGU_SHARECCTBL)) {
                    props.put(CMD_NAME, RESTORE_SHARECCTBL_CMD);
                    checkArg(null, null, OPT_FILE, ++i, args.length);
                    if (args[i].equals(OPT_FILE)) {
                        checkArg(null, args[i], null, ++i, args.length);
                        props.put(ShareConfigChangeStore.CLUSTER_SHARECC_PROP_PREFIX+
                                  ".backupfile", args[i]);
                    } else {
                        throwParserException(BAD_CMD_ARG, RESTORE_SHARECCTBL_CMD,
                            args[i], null, null);
                    }
                } else {
                    throwParserException(BAD_CMD_ARG, RESTORE_CMD,
                    args[i], null, null);
                }
            } else if (args[i].equals(UPGRADE_CMD_STR)) {
                if (gotcmd) {
                    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
                                        null, null, null);
                } else {
                    gotcmd = true;
                }

                checkArg(args[i], null, ++i, args.length);

                // find argument
                if (args[i].equals(ARGU_STORE)) {
                    props.put(CMD_NAME, UPGRADE_STORE_CMD);
                } else if (args[i].equals(ARGU_HASTORE)) {
                    props.put(CMD_NAME, UPGRADE_HASTORE_CMD);
                } else {
                    throwParserException(BAD_CMD_ARG, UPGRADE_CMD_STR,
                                        args[i], null, null);
                }
            } else if (args[i].equals(QUERY_CMD)) {
                if (gotcmd) {
                    throwParserException(EXTRA_CMD_SPECIFIED, args[i],
                                        null, null, null);
                } else {
                    gotcmd = true;
                }
                props.put(CMD_NAME, QUERY_CMD);
            } else if (args[i].equals(OPT_B)) {

                checkArg(null, OPT_B, ++i, args.length);
                props.put(Globals.IMQ + ".instancename", args[i]);

            } else if (args[i].equals(OPT_N)) {

                checkArg(null, OPT_N, ++i, args.length);
                props.put(Globals.BROKERID_PROPERTY, args[i]);
                props.put(JMSBRIDGE_NAME_PROPERTY, args[i]);

	    } else if (args[i].equals(OPT_U)) {

		checkArg(null, OPT_U, ++i, args.length);
		props.put(DBManager.FALLBACK_USER_PROP, args[i]);

	    } else if (args[i].equals(OPT_PW)) {

		checkArg(null, OPT_PW, ++i, args.length);
		props.put(DBManager.FALLBACK_PWD_PROP, args[i]);
		cliPasswdSpecified = true;

	    } else if (args[i].equals(OPT_PASSFILE)) {

		checkArg(null, OPT_PASSFILE, ++i, args.length);

		File passfile = null;
		try {
		    passfile = (new File(args[i])).getCanonicalFile();
		} catch (Exception e) {
		    throwParserException(BAD_OPT, null, null, args[i], null);
		}
		props.put(Globals.KEYSTORE_USE_PASSFILE_PROP, "true");
		props.put(Globals.KEYSTORE_PASSDIR_PROP,
					passfile.getParent());
		props.put(Globals.KEYSTORE_PASSFILE_PROP,
					passfile.getName());

            } else if (args[i].equals(OPT_VERBOSE)) {
                // Handled by wrapper script
                ;
            } else if (args[i].equals(OPT_DEBUG)) {
                debugSpecified = true;
            } else if (args[i].equals(OPT_FORCE)) {
                forceSpecified = true;
            } else if (args[i].equals(OPT_VARHOME)) {
                // Handled by wrapper script
                i++;
	    } else if (args[i].startsWith(OPT_D)) {
		int value_index = 0;
		String prop_name = null, prop_value = "";

		value_index = args[i].indexOf('=');
		if (args[i].length() <= 2) {
		    // -D
		    continue;
		} else if (value_index < 0) {
		    // -Dfoo
		    prop_name = args[i].substring(2);
		} else if (value_index == args[i].length() - 1) {
		    // -Dfoo=
		    prop_name = args[i].substring(2, value_index);
                } else {
		    // -Dfoo=bar
		    prop_name = args[i].substring(2, value_index);
		    prop_value = args[i].substring(value_index + 1);
                }

		props.put(prop_name, prop_value);
	    } else {
		throwParserException(BAD_OPT, null, null, args[i], null);
	    }
        if (props.getProperty(REMOVE_JMSBRIDGE_CMD) != null &&
            props.getProperty(JMSBRIDGE_NAME_PROPERTY) == null) {
            checkArg(null, null, OPT_N, 0, 0);
        }
	}

	return props;
    }

    /*
     * Read/parse the passfile if it is specified.
     *
     * The objective is to obtain the database password.
     * It can be specified directly via the -p option
     * or via a passfile, specified using the -passfile option.
     *
     * This method is called to to see if a passfile is specified.
     * If it was, it is read and parsed.
     *
     * Matters are complicated by the fact that imqdbmgr reads
     * the broker's configuration files. This means that
     * A password or passfile can be specified either via
     * the broker's config.properties file or imqdbmgr's
     * -p/-passfile command line options.
     *
     * A slightly less frequent use case to consider is the one
     * where the user (intentionally or not) specifies a password
     * as well as a passfile. In this case, the password takes
     * precedence over the passfile.
     *
     * Summary of behavior:
     *  - Values specified on the command line (-p, -passfile) override
     *    config.properties (imq.persist.jdbc.password, imq.passfile.*).
     *
     *  - The passwd obtained from a 'password' configuration (-p or
     *    imq.persist.jdbc.password in config.properties) will override
     *    passwords obtained from a 'passfile' configuration (-passfile
     *    or imq.passfile.* properties in config.properties). This means
     *    -p overrides -passfile.
     *
     *  - A password obtained from -passfile will override a value for
     *    imq.persist.jdbc.password in config.properties.
     */
    private void parsePassfile() throws IOException {

        String	pf_value = null,
        	pf_dir = null,
		usePassfile = null;

	/*
	 * This method checks if a passfile was specified and merges
	 * it's contents (it should contain imq.persist.jdbc.password)
	 * with the broker configuration.
	 */

	/*
	 * Return if a passfile was not specified on the cmdline or via
	 * the broker configuration file.
	 */
        usePassfile = config.getProperty(Globals.KEYSTORE_USE_PASSFILE_PROP);
        if ((usePassfile == null) ||
	    !usePassfile.equalsIgnoreCase(Boolean.TRUE.toString())) {
	    return;
	}

	// get passfile location
        if ((pf_value = config.getProperty(Globals.KEYSTORE_PASSDIR_PROP))
                                                                != null) {
	    pf_value = StringUtil.expandVariables(pf_value, config);
	    pf_dir = pf_value;
	} else {
	    pf_dir = config.getProperty(Globals.IMQ + ".etchome")
					+File.separator + "security";
	}

	String passfile_location = pf_dir  +File.separator +
                    config.getProperty(Globals.KEYSTORE_PASSFILE_PROP);

	// Check if the passfile exists else throw exception
	File pf = new File(passfile_location);
	if (pf.exists()) {
            Properties props = new Properties();
            try {
                PassfileObfuscator po = new PassfileObfuscatorImpl();
                InputStream fis = po.retrieveObfuscatedFile(passfile_location, Globals.IMQ);
                props.load(fis);
                if (!po.isObfuscated(passfile_location, Globals.IMQ)) {
                    logger.log(Logger.WARNING,
                        Globals.getBrokerResources().getKString(
                        BrokerResources.W_UNENCODED_ENTRY_IN_PASSFILE,
                        passfile_location, "'imqusermgr encode'"));
                }
	    } catch (IOException e) {
                String emsg = Globals.getBrokerResources().getKString(
                    BrokerResources.X_READ_PASSFILE, passfile_location);
                throw new IOException(emsg, e);
	    }
            config.putAll(props);
        } else {
	    throw new FileNotFoundException(
                br.getKString(BrokerResources.E_GET_PASSFILE,
                    passfile_location));
	}
    }


    private void exit(int status) {
    if (standalone) {
	    System.exit(status);
    }
    }

    void doCommand(String[] args) throws SQLException,
			BrokerException, IOException {

	// print all
	if (args.length == 0) {
	    System.out.println(version.getBanner(true));
	    System.out.println(br.getString(BrokerResources.M_DBMGR_USAGE));
	    exit(0);
	}

	// print help
	if (printHelp(args)) {
	    System.out.println(br.getString(BrokerResources.M_DBMGR_USAGE));
	    exit(0);
	}

	// print version
	if (printVersion(args)) {
	    System.out.println(version.getBanner(true));
	    System.out.println(br.getString(BrokerResources.I_JAVA_VERSION) +
		System.getProperty("java.version") + " " +
		System.getProperty("java.vendor") + " " +
		System.getProperty("java.home"));
	    System.out.println(br.getString(BrokerResources.I_JAVA_CLASSPATH) +
		System.getProperty("java.class.path"));
	    exit(0);
	}

	Properties props = null;

	try {
            props = parseArgs(args);
	} catch (ParserException e) {
	    handleParserException(e);
	    exit(1);
	}

        props.getProperty(DBManager.JDBC_PROP_PREFIX+
                      DBConnectionPool.NUM_CONN_PROP_SUFFIX, "2");

	if (cliPasswdSpecified)  {
            System.err.println(br.getString(BrokerResources.W_PASSWD_OPTION_DEPRECATED, OPT_PW));
            System.err.println("");
	}

        // 1st check existence of broker instance because Globals.init()
        // method will create an instance if it does not exist!
        String configName = (String)props.getProperty(Globals.IMQ + ".instancename");
        if (configName != null && configName.length() > 0) {
            Globals.pathinit(null);
            String topname =
                Globals.getJMQ_INSTANCES_HOME() + File.separator + configName;

            if (!(new File(topname)).exists()) {
                System.err.println(br.getString(
                    BrokerResources.E_INSTANCE_NOT_EXIST, configName));
                System.exit(1);
            }
        }

	Globals.init(props, false, false);
        config = Globals.getConfig();
	logger = Globals.getLogger();

        // Password in passfile override broker's properties object
        parsePassfile();

        // User/Password passed in from command line option take precedence.
        // Inorder to override the User/Password we need to construct the
        // correct property based on the dbVendor value; we do this after
        // calling Globals.init() so we can determine the dbVendor value.
        String dbVendor = config.getProperty(DBManager.JDBC_PROP_PREFIX + ".dbVendor");
        String vendorPropPrefix = DBManager.JDBC_PROP_PREFIX + "." + dbVendor;
        String user = props.getProperty(DBManager.FALLBACK_USER_PROP);
        if (user != null && user.length() > 0) {
            config.put(vendorPropPrefix + ".user", user);
        }

        String pwd = props.getProperty(DBManager.FALLBACK_PWD_PROP);
        if (pwd != null && pwd.length() > 0) {
            config.put(vendorPropPrefix + ".password", pwd);
        }

	String cmd = props.getProperty(CMD_NAME);

    CommDBManager mgr = null;

	if (DELETE_SHARECCTBL_CMD.equals(cmd) ||
        RECREATE_SHARECCTBL_CMD.equals(cmd) ||
        CREATE_SHARECCTBL_CMD.equals(cmd) ||
        DUMP_SHARECCTBL_CMD.equals(cmd) ||
        BACKUP_SHARECCTBL_CMD.equals(cmd) ||
        RESTORE_SHARECCTBL_CMD.equals(cmd)) {
        if (debugSpecified) {
            System.out.println("cmd="+cmd+", use sharecc");
        }
        if (!Globals.useSharedConfigRecord()) {
            if (Globals.getHAEnabled()) {
            logger.logToAll(Logger.ERROR, 
                br.getKString(BrokerResources.E_CONFIGURED_HA_MODE));
            } else {
            logger.logToAll(Logger.ERROR, 
                br.getKString(BrokerResources.E_NOT_CONFIGURED_USE_SHARECC,
                Globals.NO_MASTERBROKER_PROP));
            }
            exit(1);
        }
        mgr = ShareConfigChangeDBManager.getDBManager();
    } else {
	    dbmgr = DBManager.getDBManager();
        mgr = dbmgr;
    }

	// print out info messages
	String brokerid = null;
    if (mgr instanceof DBManager) {
	    brokerid = ((DBManager)mgr).getBrokerID();
    }
        String url;
        if (CREATE_ALL_CMD.equals(cmd)) {
            url = mgr.getCreateDBURL();
        } else {
            url = mgr.getOpenDBURL();
        }
        if ( url == null ) {
            url = "not specified";
        }
        user = mgr.getUser();
        if ( user == null ) {
            user = "not specified";
        }

        if (!(mgr instanceof ShareConfigChangeDBManager)) {
           String msgArgs[] = { String.valueOf(JDBCStore.STORE_VERSION), brokerid,
                                url, user };
           logger.logToAll(Logger.INFO,
                           br.getString(BrokerResources.I_JDBC_STORE_INFO, msgArgs));
       } else {
           String msgArgs[] = { "",  String.valueOf(JDBCShareConfigChangeStore.SCHEMA_VERSION),
                                Globals.getClusterID(), url, user };
           logger.logToAll(Logger.INFO, br.getKString(
                           BrokerResources.I_SHARECC_JDBCSTORE_INFO, msgArgs));
       }

    if (debugSpecified) {
        System.out.println("cmd="+cmd);
    }
	if (CREATE_ALL_CMD.equals(cmd)) {
	    doCreate(true); // create database as well
	} else if (CREATE_TBL_CMD.equals(cmd)) {
	    doCreate(false); // just tables
	} else if (CREATE_SHARECCTBL_CMD.equals(cmd)) {
	    doCreate(false, ShareConfigChangeDBManager.getDBManager());
	} else if (DELETE_TBL_CMD.equals(cmd)) {
	    doDelete(props.getProperty(ARG_NAME), null, null);
	} else if (DELETE_SHARECCTBL_CMD.equals(cmd)) {
	    doDelete(props.getProperty(ARG_NAME), 
                ShareConfigChangeDBManager.getDBManager(), null);
        } else if (RECREATE_TBL_CMD.equals(cmd)) {
            doRecreate();
        } else if (RECREATE_SHARECCTBL_CMD.equals(cmd)) {
            doRecreate(ShareConfigChangeDBManager.getDBManager());
        } else if (REMOVE_BKR_CMD.equals(cmd)) {
            doRemoveBkr();
        } else if (REMOVE_JMSBRIDGE_CMD.equals(cmd)) {
            doRemoveJMSBridge(props.getProperty(JMSBRIDGE_NAME_PROPERTY));
        } else if (DUMP_CMD.equals(cmd)) {
            String arg = props.getProperty(ARG_NAME);
            doDump(arg, null);
        } else if (DUMP_SHARECCTBL_CMD.equals(cmd)) {
            doDump(null, ShareConfigChangeDBManager.getDBManager());
        } else if (DROPTBL_CMD.equals(cmd)) {
            String arg = props.getProperty(ARG_NAME);
            doDropTablesByPattern(arg);
	} else if (RESET_CMD.equals(cmd)) {
	    doReset();
        } else if(BACKUP_CMD.equals(cmd)) {
            doBackup();
        } else if(BACKUP_SHARECCTBL_CMD.equals(cmd)) {
            doBackupSharecc();
        } else if(RESTORE_CMD.equals(cmd)) {
            doRestore();
        } else if(RESTORE_SHARECCTBL_CMD.equals(cmd)) {
            doRestoreSharecc();
        } else if(UPGRADE_STORE_CMD.equals(cmd)) {
            doUpgrade(false);
        } else if(UPGRADE_HASTORE_CMD.equals(cmd)) {
            doUpgrade(true);
        } else if(QUERY_CMD.equals(cmd)) {
            doQuery();
	} else if (cmd == null) {
	    System.out.println(br.getString(BrokerResources.E_MISSING_DBMGR_CMD));
	} else {
	    System.out.println(br.getString(BrokerResources.E_INVALID_DBMGR_CMD, cmd));
	}
    }

    private static class ParserException extends Exception {
	String cmd;
	String cmdarg;
	String opt;
	String optarg;
	int reason;

	ParserException(int reason) {
        this(reason, null);
    }

	ParserException(int reason, Throwable cause) {
        super(cause);
	    this.reason = reason;
	}
    }

    public static void main(String[] args) {

        int exitCode = 0;
        DBTool tool = new DBTool(true); // standalone;
	try {
	    tool.doCommand(args);
	} catch (Exception e) {
            Globals.getLogger().logStack(Logger.ERROR, e.getMessage(), e);

            if (tool.debugSpecified) {
                e.printStackTrace();
            }
	    exitCode = 1;
	} finally {
            if (tool.dbmgr != null) {
                tool.dbmgr.close(); // Free connection from connection pool
            }
        }

        System.exit(exitCode);
    }
}
