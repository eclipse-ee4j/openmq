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
 * @(#)VersionDAOImpl.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;

/**
 * This class implement a generic VersionDAO.
 */
class VersionDAOImpl extends BaseDAOImpl implements VersionDAO {

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String updateLockSQL;
    private final String updateLockByLockIDSQL;
    private final String selectStoreVersionSQL;
    private final String selectLockSQL;
    private final String selectAllSQL;

    /**
     * Constructor
     * @throws BrokerException
     */
    VersionDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( STORE_VERSION_COLUMN ).append( ") VALUES ( ? )" )
            .toString();

        updateLockSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( LOCK_ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( STORE_VERSION_COLUMN ).append( " = ? AND " )
            .append( LOCK_ID_COLUMN ).append( " IS NULL" )
            .toString();

        updateLockByLockIDSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( LOCK_ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( STORE_VERSION_COLUMN ).append( " = ? AND " )
            .append( LOCK_ID_COLUMN ).append( " = ?" )
            .toString();

        selectStoreVersionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( STORE_VERSION_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        selectLockSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( LOCK_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( STORE_VERSION_COLUMN ).append( " = ?" )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( LOCK_ID_COLUMN ).append( ", " )
            .append( STORE_VERSION_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();
    }

    /**
     * Get the prefix name of the table.
     * @return table name
     */
    public final String getTableNamePrefix() {
        return TABLE_NAME_PREFIX;
    }

    /**
     * Get the name of the table.
     * @return table name
     */
    public final String getTableName() {
        return tableName;
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param storeVersion version of the the store
     * @throws BrokerException
     */
    public void insert( Connection conn, int storeVersion )
        throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
            pstmt.setInt( 1, storeVersion );
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_STORE_VERSION_FAILED,
                    tableName ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update the lock ID.
     * @param conn database connection
     * @param storeVersion version of the store
     * @param newLockID the borker ID or imqdbmgr that want to lock this store
     * @param oldLockID the borker ID or imqdbmgr that has the lock
     * @return true if lock ID has been updated, false otherwise
     * @throws BrokerException
     */
    public boolean updateLock( Connection conn, int storeVersion,
        String newLockID, String oldLockID ) throws BrokerException {

        boolean updated = false;
        boolean myConn = false;
        String sql = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            if ( oldLockID == null ) {
                sql = updateLockSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                Util.setString( pstmt, 1, newLockID );
                pstmt.setInt( 2, storeVersion );
            } else {
                sql = updateLockByLockIDSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                Util.setString( pstmt, 1, newLockID );
                pstmt.setInt( 2, storeVersion );
                pstmt.setString( 3, oldLockID );
            }

            if ( pstmt.executeUpdate() > 0 ) {
                updated = true;
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_UNABLE_TO_ACQUIRE_STORE_LOCK ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        return updated;
    }

    /**
     * Delete all entries.
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        if ( Globals.getHAEnabled() ) {
            return; // Version table cannot be reset
        } else {
            super.deleteAll( conn );
        }
    }

    /**
     * Get the version of the store.
     * @param conn database connection
     * @return version of the store
     * @throws BrokerException
     */
    public int getStoreVersion( Connection conn )
        throws BrokerException {

        int version = -1;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectStoreVersionSQL );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                version = rs.getInt( STORE_VERSION_COLUMN );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectStoreVersionSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_VERSION_FAILED,
                    tableName ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return version;
    }

    /**
     * Get the lock ID.
     * @param conn database connection
     * @param storeVersion version of the store
     * @return lockID that is currently using the store; empty string for no
     * lock and null value for record not found
     * @throws BrokerException
     */
    public String getLock( Connection conn, int storeVersion )
        throws BrokerException {

        String lockID = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectLockSQL );
            pstmt.setInt( 1, storeVersion );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                lockID = rs.getString( 1 );
                if ( lockID == null ) {
                    lockID = "";    // Set to empty string for no lock
                }
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            // Usually it's because the table does not exist
            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectLockSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_VERSION_FAILED,
                    tableName ), ex, Status.NOT_FOUND );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return lockID;
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new HashMap();
        StringBuffer strBuf = new StringBuffer( 256 );

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectAllSQL );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                strBuf.append( "(" )
                      .append( STORE_VERSION_COLUMN ).append( "=" )
                      .append( rs.getString( STORE_VERSION_COLUMN ) ).append( ", " )
                      .append( LOCK_ID_COLUMN ).append( "=" )
                      .append( rs.getString( LOCK_ID_COLUMN ) )
                      .append( ")" )
                      .append( BrokerResources.NL );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            logger.log( Logger.ERROR, BrokerResources.X_JDBC_QUERY_FAILED,
                selectAllSQL, e );
        } finally {
            try {
                if ( myConn ) {
                    Util.close( rs, pstmt, conn, myex );
                } else {
                    Util.close( rs, pstmt, null, myex );
                }
            } catch ( BrokerException be ) {
                logger.log( Logger.ERROR, be.getMessage(), be.getCause() );
            }
        }

        map.put( "Version(" + tableName + ")", strBuf.toString() );
        return map;
    }
}
