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
 * @(#)StoreSessionDAOImpl.java	1.13 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo.StoreSession;

import java.util.*;
import java.util.Date;
import java.sql.*;

/**
 * StoreSessionDAOImpl defines/implements the generic DAO API for the Store Session table.
 */
class StoreSessionDAOImpl extends BaseDAOImpl implements StoreSessionDAO {

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String takeoverSQL;
    private final String deleteSQL;
    private final String deleteByBrokerSQL;
    private final String deleteInactiveByBrokerSQL;
    private final String selectSQL;
    private final String selectIfOwnStoreSessionSQL;
    private final String selectAllSQL;
    private final String selectAllOldSessionsSQL;
    private final String selectCurrentSessionSQL;
    private final String selectIDsByBrokerSQL;
    private final String moveStoreSessionSQL;

    private volatile long localStoreSessionID = 0;

    private FaultInjection fi = null;

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    StoreSessionDAOImpl() throws BrokerException {

        fi = FaultInjection.getInjection();

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( IS_CURRENT_COLUMN ).append( ", " )
            .append( CREATED_BY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ? )" )
            .toString();

        /*
        updateIsCurrentSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( IS_CURRENT_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();
        */

        takeoverSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( BROKER_ID_COLUMN ).append( " = ?, " )
            .append( CREATED_TS_COLUMN ).append( " = ?, " )
            .append( IS_CURRENT_COLUMN ).append( " = 0" )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        deleteByBrokerSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();

        StringBuffer tmpbuf = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND NOT EXISTS (" );
        if (dbMgr.isUseDerivedTableForUnionSubQueries()) {
            tmpbuf
            .append(  "SELECT 1 FROM (" );
        }
        tmpbuf
            .append(   "SELECT 1 FROM " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " )
            .append(   MessageDAO.STORE_SESSION_ID_COLUMN ).append( " = ?" )
            .append(   " UNION " )
            .append(   "SELECT 1 FROM " )
            .append(   dbMgr.getTableName( TransactionDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " )
            .append(   TransactionDAO.STORE_SESSION_ID_COLUMN ).append( " = ?" );
        if (dbMgr.isUseDerivedTableForUnionSubQueries()) {
            tmpbuf
            .append( ") tmptbl)" );
        } else {
            tmpbuf
            .append( ")" );
        }
        deleteInactiveByBrokerSQL = tmpbuf.toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( IS_CURRENT_COLUMN ).append( ", " )
            .append( CREATED_BY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectIfOwnStoreSessionSQL = new StringBuffer(128)
            .append( "SELECT 1" )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND NOT EXISTS (" )
            .append( ((BrokerDAOImpl)dbMgr.getDAOFactory().getBrokerDAO())
                     .selectIsBeingTakenOverSQL + ")" )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( IS_CURRENT_COLUMN ).append( ", " )
            .append( CREATED_BY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " ORDER BY " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .toString();

        selectAllOldSessionsSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append( ", " )
            .append( BROKER_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( IS_CURRENT_COLUMN ).append( " = 0" )
            .toString();

        selectCurrentSessionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( IS_CURRENT_COLUMN ).append( " = 1" )
            .toString();

        /*
        selectPreviousSessionSQL = new StringBuffer(128)
            .append( "SELECT sTbl." )
            .append( ID_COLUMN ).append( ", sTbl." ).append( CREATED_TS_COLUMN )
            .append( " FROM " )
            .append( tableName ).append( " sTbl, " )
            .append( dbMgr.getTableName( BrokerDAO.TABLE_NAME_PREFIX ) )
            .append( " bTbl WHERE bTbl." )
            .append( BrokerDAO.ID_COLUMN ).append( " = ?" )
            .append( " AND bTbl." )
            .append( BrokerDAO.ID_COLUMN ).append( " = sTbl." )
            .append( CREATED_BY_COLUMN )
            .append( " AND bTbl." )
            .append( BrokerDAO.TAKEOVER_BROKER_COLUMN ).append( " = sTbl." )
            .append( BROKER_ID_COLUMN )
            .append( " ORDER BY sTbl." )
            .append( CREATED_TS_COLUMN )
            .append( " DESC" )
            .toString();
        */

        selectIDsByBrokerSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();

        moveStoreSessionSQL = new StringBuffer(128)
            .append( " UPDATE " ).append( tableName )
            .append( " SET " ).append( BROKER_ID_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ? " )
            .append( " AND " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( IS_CURRENT_COLUMN ).append( " <> 1 " )
            .append( " AND NOT EXISTS (" )
            .append( ((BrokerDAOImpl)dbMgr.getDAOFactory().getBrokerDAO())
                     .selectIsBeingTakenOverSQL + ")" )
            .append( " AND NOT EXISTS (" )
            .append( ((BrokerDAOImpl)dbMgr.getDAOFactory().getBrokerDAO())
                     .selectIsBeingTakenOverSQL + ")" )
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
     * @param brokerID Broker ID
     * @param sessionID the broker's session ID
     * @return current session ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public long insert( Connection conn, String brokerID, long sessionID, boolean failExist)
        throws BrokerException {

        if (sessionID == 0) {
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Unexpected store session uid "+sessionID+ " to add for broker "+brokerID);
            BrokerException ex = new BrokerException(emsg);
            logger.logStack(logger.ERROR, emsg, ex);
            throw ex;
        }
        boolean myConn = false;
        PreparedStatement pstmt = null;
        String sql = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            // Get current store session
            long currentID = 0;
            sql = selectCurrentSessionSQL;
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, brokerID );
            ResultSet rs = pstmt.executeQuery();
            if ( rs.next() ) {
                currentID = rs.getLong( 1 );
                if (failExist) {
                    throw new BrokerException(
                    "Unexpected current "+ID_COLUMN+" "+currentID+
                    " already exists in table "+tableName+" for broker "+brokerID);
                }
                if ( rs.next() ) {
                    throw new BrokerException(
                    "Unexpected more than 1 current "+ID_COLUMN+" "+currentID+", "+rs.getLong( 1 )+
                    " exists in table "+tableName+" for broker "+brokerID);
                }
                if ( currentID == 0 ) {
                    throw new BrokerException(
                    "Unexpected current "+ID_COLUMN+" value "+currentID+
                    " stored in table "+tableName+" for broker "+brokerID);
                }
            }
            rs.close();
            pstmt.close();

            if (currentID == 0) {
                insert( conn, brokerID, sessionID, 1, brokerID, System.currentTimeMillis() );
                currentID = sessionID;
            }

            if ( myConn ) {
                conn.commit();
            }
            return currentID;

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
                br.getKString( BrokerResources.X_PERSIST_STORE_SESSION_FAILED,
                    String.valueOf( sessionID ) ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param brokerID Broker ID
     * @param sessionID the broker's session ID
     * @param isCurrent Specify whether the session is current
     * @param createdBy Broker ID that creates this session
     * @param createdTS timestamp
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void insert( Connection conn, String brokerID, long sessionID,
        int isCurrent, String createdBy, long createdTS )
        throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        String sql = insertSQL;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setLong( 1, sessionID );
            pstmt.setString( 2, brokerID );
            pstmt.setInt( 3, isCurrent );
            pstmt.setString( 4, createdBy );
            pstmt.setLong( 5, createdTS );
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
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
                br.getKString( BrokerResources.X_PERSIST_STORE_SESSION_FAILED,
                    String.valueOf( sessionID ) ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }
    
    /**
     * Take over the store sessions.
     * @param conn database connection
     * @param brokerID the current or local broker ID
     * @param targetBrokerID the broker ID of the store being taken over
     * @return a List of all store sessions the target broker owns
     * @throws BrokerException
     */
    public List<Long> takeover( Connection conn, String brokerID,
        String targetBrokerID) throws BrokerException {

        List<Long> list = new ArrayList<Long>();

        String sql = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            
            if (fi.FAULT_INJECTION) {
                fi.checkFaultAndThrowBrokerException(
                   FaultInjection.FAULT_HA_TAKEOVER_SWITCH_SS_EXCEPTION, null);
                fi.checkFaultAndExit(
                   FaultInjection.FAULT_HA_TAKEOVER_SWITCH_SS_HALT, null, 2, false);
            }

            // First retrieve all store sessions for the target broker
            DBManager dbMgr = DBManager.getDBManager();
            sql = selectIDsByBrokerSQL;
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, targetBrokerID );
            ResultSet rs = pstmt.executeQuery();
            while ( rs.next() ) {
                long ssid = rs.getLong( 1 );
                list.add( Long.valueOf( ssid ) );
            }
            rs.close();
            pstmt.close();

            // Now takeover those sessions

            // Note: Use the CREATED_TS column to store the takeover TS which
            // will assist us in knowning when it is safe to reap it
            // as it becomes inactive, i.e. all messages have been drained.
            pstmt = dbMgr.createPreparedStatement( conn, takeoverSQL );
            pstmt.setString( 1, brokerID );
            pstmt.setLong( 2, System.currentTimeMillis() );
            pstmt.setString( 3, targetBrokerID );
            int count = pstmt.executeUpdate();

            // Verify that we are able to takeover all sessions
            if ( count != list.size() ) {
                // This shouldn't occur but just being safe
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TAKEOVER_STORE_SESSIONS_FAILED,
                        targetBrokerID ) );
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
                ex = DBManager.wrapSQLException("[" + takeoverSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_TAKEOVER_STORE_SESSIONS_FAILED,
                    targetBrokerID ), ex );
        } finally {
            Util.close( null, pstmt, null, myex );
        }

        return list;
    }

    /**
     * Delete an entry.
     * @param conn database connection
     * @param id Store Session ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void delete( Connection conn, long id )
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

            pstmt = dbMgr.createPreparedStatement( conn, deleteSQL );
            pstmt.setLong( 1, id );
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+deleteSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_STORE_SESSION_FAILED,
                String.valueOf( id ) ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete all entries for the specified broker ID.
     * @param conn database connection
     * @param brokerID Broker ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void deleteByBrokerID( Connection conn, String brokerID )
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

            pstmt = dbMgr.createPreparedStatement( conn, deleteByBrokerSQL );
            pstmt.setString( 1, brokerID );
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+deleteByBrokerSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + deleteByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_STORE_SESSIONS_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete all entries.
     * @param conn database connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        if ( Globals.getHAEnabled() ) {
            return; // Session table cannot be reset
        } else {
            super.deleteAll( conn );
        }
    }

    public List<Long> deleteInactiveStoreSession( Connection conn )
        throws BrokerException {

        List reaped = new ArrayList<Long>();

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

            // Retrieves all old sessions, i.e. isCurrent flag is 0
            HashMap sessionMap = new HashMap();
            long currentTime = System.currentTimeMillis();

            pstmt = dbMgr.createPreparedStatement( conn, selectAllOldSessionsSQL );
            ResultSet rs = pstmt.executeQuery();
            while ( rs.next() ) {
                long ssid = rs.getLong( 1 );
                long takeoverTS = rs.getLong( 2 );
                String brokerID = rs.getString( 3 );

                // It is safe to delete store session that has been taking over
                // for more than 30 mins. If we reap the store session too soon,
                // the broker will not be able to re-connect the client.
                if (currentTime > takeoverTS + 1800000) {
                    sessionMap.put( Long.valueOf(ssid), brokerID );
                }
            }
            rs.close();

            if ( ! sessionMap.isEmpty() ) {
                pstmt.close();
                pstmt = dbMgr.createPreparedStatement( conn, deleteInactiveByBrokerSQL );

                Iterator itr = sessionMap.entrySet().iterator();
                while ( itr.hasNext() ) {
                    Map.Entry entry = (Map.Entry)itr.next();
                    long ssid = ((Long)entry.getKey()).longValue();
                    String brokerID = (String)entry.getValue();

                    // Delete the session if it doesn't have any msgs or txns
                    try {
                        pstmt.setLong( 1, ssid );
                        pstmt.setLong( 2, ssid );
                        pstmt.setLong( 3, ssid );

                        if ( pstmt.executeUpdate() > 0 ) {
                            reaped.add(Long.valueOf(ssid));
                            logger.log( Logger.INFO, br.getKString(
                                BrokerResources.I_REAP_INACTIVE_STORE_SESSION,
                                String.valueOf(ssid), brokerID ) );
                        }
                    } catch ( SQLException e ) {
                        // Just log error and continue
                        SQLException ex = DBManager.wrapSQLException(
                            "[" + deleteInactiveByBrokerSQL + "]", e);
                        logger.logStack( Logger.ERROR,
                            BrokerResources.X_REMOVE_INACTIVE_STORE_SESSION_FAILED,
                            Long.valueOf(ssid), brokerID, ex );
                    }
                }
            }
            return reaped;
        } catch ( Exception e ) {
            myex = e;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_JDBC_QUERY_FAILED,
                    selectAllOldSessionsSQL ), e );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Get the current store session for the specified brokerID.
     * @param conn database connection
     * @param brokerID Broker ID
     * @return current store session ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public long getStoreSession( Connection conn, String brokerID )
        throws BrokerException {

        DBManager dbMgr = DBManager.getDBManager();
        boolean isLocalBroker = dbMgr.getBrokerID().equals( brokerID );
        if ( isLocalBroker && localStoreSessionID > 0 ) {
            // Returns cached value for performance since new value only
            // get allocated when the broker is restarted after a crash!
            return localStoreSessionID;
        }

        long sessionID = 0;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectCurrentSessionSQL );
            pstmt.setString( 1, brokerID );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                sessionID = rs.getLong( 1 );
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
                ex = DBManager.wrapSQLException("[" + selectCurrentSessionSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_CURRENT_STORE_SESSION_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        if ( isLocalBroker && localStoreSessionID == 0 ) {
            localStoreSessionID = sessionID;
        }

        return sessionID;
    }

    /**
     * Get the broker that owns the specified store session ID.
     * @param conn database connection
     * @param id store session ID
     * @return the broker ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public String getStoreSessionOwner( Connection conn, long id )
        throws BrokerException {

        String brokerID = null;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                brokerID = rs.getString( BROKER_ID_COLUMN );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_SESSION_FAILED,
                    String.valueOf(id)), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return brokerID;
    }

    /**
     * Find out if a broker owns a store session 
     *
     * @param conn database connection
     * @param id store session ID
     * @param brokerID
     * @return true if brokerID owns the store session ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public boolean ifOwnStoreSession( Connection conn, long id, String brokerID )
        throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, selectIfOwnStoreSessionSQL );
            pstmt.setLong( 1, id );
            pstmt.setString( 2, brokerID );
            pstmt.setString( 3, brokerID );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                 return true;
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+
                            "["+selectIfOwnStoreSessionSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + 
                     selectIfOwnStoreSessionSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( "["+selectIfOwnStoreSessionSQL+"]", 
                "("+String.valueOf(id)+", "+brokerID+"): "+ex.getMessage(), ex );

        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return false;
    }

    /**

    /**
     * Get the broker that creates the specified store session ID.
     * @param conn database connection
     * @param id store session ID
     * @return the broker ID
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public String getStoreSessionCreator( Connection conn, long id )
        throws BrokerException {

        String brokerID = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex =null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                brokerID = rs.getString( CREATED_BY_COLUMN );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_SESSION_FAILED,
                    String.valueOf(id)), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return brokerID;
    }

    /**
     * Get all store sessions.
     * @param conn database connection
     * @return A map of broker ID to a list of StoreSessions that it owns.
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public Map getAllStoreSessions( Connection conn )
        throws BrokerException {

        HashMap map = new HashMap();

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
                long id = rs.getLong( 1 );
                String brokerID = rs.getString( 2 );
                int isCurrent = rs.getInt( 3 );
                String createdBy = rs.getString( 4 );
                long createdTS = rs.getLong( 5 );

                List sessionList = (List)map.get( brokerID );
                if ( sessionList == null ) {
                    sessionList = new ArrayList();
                    map.put( brokerID, sessionList );
                }

                // Add new entry to the list
                sessionList.add( new HABrokerInfo.StoreSession(
                    id, brokerID, isCurrent, createdBy, createdTS ) );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( br.getKString(
                BrokerResources.X_LOAD_ALL_STORE_SESSIONS_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return map;
    }

    /**
     * Get all store sessions for the specified broker ID.
     * @param conn database connection
     * @param brokerID Broker ID
     * @return a List of all store sessions the target broker owns
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public List<Long> getStoreSessionsByBroker( Connection conn, String brokerID )
        throws BrokerException {

        ArrayList<Long> ids = new ArrayList<Long>();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectIDsByBrokerSQL );
            pstmt.setString( 1, brokerID );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                long ssid = rs.getLong( 1 );
                ids.add( Long.valueOf( ssid ) );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectIDsByBrokerSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectIDsByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_SESSIONS_BY_BROKER_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return ids;
    }

    /** 
     * @param storeSession the store session to be moved
     * @param targetBrokerID the target broker
     *
     */ 
    public void moveStoreSession( Connection conn, long storeSession, String targetBrokerID )
        throws BrokerException {

        boolean replaycheck = false;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        String sql = moveStoreSessionSQL;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            // Get a connection
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }
            if (!ifOwnStoreSession( conn, storeSession, dbMgr.getBrokerID() )) {
                throw new BrokerException(
                    "XXXThis broker does not own partition "+storeSession,
                     Status.NOT_MODIFIED);
            }
            if (isCurrent( conn, storeSession )) {
                throw new BrokerException(
                    "XXXCan't move this broker's primary partition "+storeSession,
                     Status.NOT_MODIFIED);
            }
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, targetBrokerID );
            pstmt.setString( 2, dbMgr.getBrokerID() );
            pstmt.setLong( 3, Long.valueOf(storeSession) );
            pstmt.setString( 4, dbMgr.getBrokerID() );
            pstmt.setString( 5, targetBrokerID );
            int cnt = pstmt.executeUpdate();
            if (cnt == 1) {
                if ( myConn ) {
                    replaycheck = true;
                    conn.commit();
                }
            } else if (cnt == 0) {
                BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                if ( dao.isBeingTakenOver( conn, targetBrokerID ) ) {
                    throw new BrokerException(
                    "Target broker "+targetBrokerID+" is in being taken over state",
                    Status.NOT_MODIFIED);
                }
                if ( dao.isBeingTakenOver( conn, dbMgr.getBrokerID() )) {
                    BrokerException be = new StoreBeingTakenOverException(
                        br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ), 
                        Status.NOT_MODIFIED );
                    try {
                         HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, dbMgr.getBrokerID() );
                         logger.logStack( Logger.ERROR, be.getMessage()+"["+
                             (bkrInfo == null ? ""+dbMgr.getBrokerID():bkrInfo.toString())+"]", be );
                    } catch (Throwable t) { /* Ignore error */ }

                    throw be;
                }

                throw new BrokerException(
                "XXXMove partition "+storeSession+" to broker failed", Status.NOT_MODIFIED);
            } else {
                throw new BrokerException(
                "XXXMove partition "+storeSession+
                " to broker failed, unexpected affected row count "+cnt, Status.NOT_MODIFIED);
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                    replaycheck = false;
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
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
                "XXXMove partition "+storeSession+" to broker failed", ex,
                     (replaycheck ? Status.ERROR:Status.NOT_MODIFIED));
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
     }

    /**
     * Check whether the specified ID is the current store session ID.
     * @param conn database connection
     * @param id store session ID
     * @return return true if it is the current store session ID
     */
    public boolean isCurrent( Connection conn, long id )
        throws BrokerException {

        boolean isCurrent = false;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                if ( rs.getInt( IS_CURRENT_COLUMN ) == 1 ) {
                    isCurrent = true;
                }
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_STORE_SESSION_FAILED,
                String.valueOf(id)), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return isCurrent;
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new HashMap();
        StringBuffer strBuf = new StringBuffer( 512 );

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
                long createdTS = rs.getLong( 5 );
                strBuf.append( "(")
                    .append( "sessionID=" ).append( rs.getLong( 1 ) )
                    .append( ", brokerID=" ).append( rs.getString( 2 ) )
                    .append( ", isCurrent=" ).append( rs.getInt( 3 ) == 1 )
                    .append( ", createdBy=" ).append( rs.getString( 4 ) )
                    .append( ", createdTS=" ).append( createdTS )
                    .append( (createdTS > 0) ? " [" + new Date( createdTS ) + "]" : "" )
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
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllSQL+"]", rbe );
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

        map.put( "Store Session(" + tableName + ")", strBuf.toString() );
        return map;
    }

    protected static void checkStoreSessionOwner( Connection conn, Long storeSession, String brokerID)
	throws BrokerException {

	DBManager dbMgr = DBManager.getDBManager();
	String id = dbMgr.getDAOFactory().getStoreSessionDAO().
                     getStoreSessionOwner( conn, storeSession.longValue() );
	if (id == null) {
            throw new BrokerException(
            "XXX Store session "+storeSession+" not found in store");
	}
        if (!id.equals(brokerID)) {
            throw new BrokerException(
            "XXX Store session "+storeSession+" has owner broker "+id+", not "+brokerID);
	}
    }
}
