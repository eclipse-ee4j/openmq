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
 * @(#)BrokerDAOImpl.java	1.30 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverLockException;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;

/**
 * BrokerDAOImpl defines/implements the generic DAO API for the Broker table.
 */
class BrokerDAOImpl extends BaseDAOImpl implements BrokerDAO {

    public static final String STORE_SESSION_ID_COLUMN = "STORE_SESSION_ID";

    protected final String tableName;

    // SQLs
    private final String insertSQL;
    private final String updateVersionSQL;
    private final String updateURLSQL;
    private final String resetTakeoverBrokerSQL;
    private final String restoreOnTakeoverFailSQL;
    private final String restoreHeartbeatOnTakeoverFailSQL;
    private final String updateHeartbeatSQL;
    private final String updateHeartbeatAndCheckStateSQL;
    private final String updateStateThisBrokerSQL;
    private final String updateStateOtherBrokerSQL;
    private final String takeoverSQL;
    private final String deleteSQL;
    private final String selectSQL;
    private final String selectAllSQL;
    private final String selectAllByStateSQL;
    private final String selectHeartbeatSQL;
    private final String selectAllHeartbeatsSQL;
    private final String selectStateSQL;
    private final String selectAllStatesSQL;
    protected final String selectIsBeingTakenOverSQL;

    private FaultInjection fi = null;

    /**
     * Constructor
     * @throws BrokerException
     */
    BrokerDAOImpl() throws BrokerException {

        fi = FaultInjection.getInjection();

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( URL_COLUMN ).append( ", " )
            .append( VERSION_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TAKEOVER_BROKER_COLUMN ).append( ", " )
            .append( HEARTBEAT_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ? )" )
            .toString();

        updateVersionSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( VERSION_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        updateURLSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( URL_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        resetTakeoverBrokerSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = NULL, " )
            .append( STATE_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " IS NOT NULL")
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = ")
            .append(BrokerState.I_FAILOVER_COMPLETE)
            .toString();

        restoreOnTakeoverFailSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = NULL, " )
            .append( STATE_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = " )
            .append(BrokerState.I_FAILOVER_STARTED)
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = ?" )
            .toString();

        restoreHeartbeatOnTakeoverFailSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = " )
            .append(BrokerState.I_FAILOVER_STARTED)
            .append( " AND " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ? " )
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = ?" )
            .toString();

        updateHeartbeatSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        updateHeartbeatAndCheckStateSQL = new StringBuffer( updateHeartbeatSQL )
            .append( " AND " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " NOT IN (" )
            .append( BrokerState.I_FAILOVER_PENDING ).append( ", " )
            .append( BrokerState.I_FAILOVER_STARTED ).append( ", " )
            .append( BrokerState.I_FAILOVER_COMPLETE ).append( ", " )
            .append( BrokerState.I_FAILOVER_FAILED ).append( ")" )
            .toString();

        updateStateThisBrokerSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " IS NULL " )
            .toString();

        updateStateOtherBrokerSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = ?" )
            .toString();

        takeoverSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " = ?, " )
            .append( STATE_COLUMN ).append( " = ?, " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( HEARTBEAT_TS_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TAKEOVER_BROKER_COLUMN ).append( " is NULL" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        /*
         * All the supported store sessions for a broker is kept in the session
         * table but for convenience we will load the current store session
         * when load the broker info. If a broker doesn't have a current store
         * session, i.e. taken over by another broker, then its value will be 0.
         */

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT bTbl." )
            .append( ID_COLUMN ).append( ", " )
            .append( URL_COLUMN ).append( ", " )
            .append( VERSION_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TAKEOVER_BROKER_COLUMN ).append( ", " )
            .append( HEARTBEAT_TS_COLUMN ).append( ", sTbl." )
            .append( StoreSessionDAO.ID_COLUMN ).append( " AS " )
            .append( STORE_SESSION_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append(   " bTbl LEFT JOIN " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sTbl ON bTbl." ).append( ID_COLUMN ).append( " = sTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " AND sTbl." )
            .append(   StoreSessionDAO.IS_CURRENT_COLUMN ).append( " = 1" )
            .toString();

        selectAllByStateSQL = new StringBuffer(128)
            .append( selectAllSQL )
            .append( " WHERE bTbl." )
            .append( STATE_COLUMN ).append( " = ?" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( selectAllSQL )
            .append( " WHERE bTbl." )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectHeartbeatSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( HEARTBEAT_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectAllHeartbeatsSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( HEARTBEAT_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        selectStateSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectAllStatesSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        // SQL that can be embedded in EXISTS clause to check if the specified
        // broker is being takenover (uses by other DAOs)
        selectIsBeingTakenOverSQL = new StringBuffer(128)
            .append( "SELECT 1 FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ? AND " )
            .append( STATE_COLUMN ).append( " IN (" )
            .append( BrokerState.I_FAILOVER_PENDING ).append( ", " )
            .append( BrokerState.I_FAILOVER_STARTED ).append( ", " )
            .append( BrokerState.I_FAILOVER_COMPLETE ).append( ", " )
            .append( BrokerState.I_FAILOVER_FAILED ).append( ")" )
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
     * @param id Broker ID
     * @param takeoverID Broker ID taken over the store
     * @param url the broker's URL
     * @param version the broker's version
     * @param state the broker's state
     * @param sessionID the broker's session ID
     * @param heartbeat the broker's heartbeat timestamp
     * @param additionalSession list of additional store sessions to create
     * @throws BrokerException
     */

    public void insert( Connection conn, String id, String takeoverID, String url,
        int version, int state, Long sessionID, long heartbeat, List<UID> additionalSessions)
        throws BrokerException {

        Exception myex = null;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            // First insert broker info
            pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
            pstmt.setString( 1, id );
            pstmt.setString( 2, url );
            pstmt.setInt( 3, version );
            pstmt.setInt( 4, state );
            Util.setString( pstmt, 5, takeoverID );
            Util.setLong( pstmt, 6, heartbeat );

            pstmt.executeUpdate();
            pstmt.close();

            if (sessionID != null) { 
                dbMgr.getDAOFactory().getStoreSessionDAO().insert( conn, id, sessionID.longValue(), true);
                if (additionalSessions != null) {
                    UID uid = null;
                    Iterator<UID> itr = additionalSessions.iterator(); 
                    while (itr.hasNext()) {
                        uid = itr.next();
                        logger.log(logger.INFO, "XXX Create additional store session "+ uid+ " for this broker");
                        dbMgr.getDAOFactory().getStoreSessionDAO().insert(
                            conn, id, uid.longValue(), 0, id, System.currentTimeMillis());
                    }
                }
            }

            if ( myConn ) {
                conn.commit();
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
                ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_BROKERINFO_FAILED,
                    id ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update an existing entry.
     * @param conn database connection
     * @param id Broker ID
     * @param updateType (as defined in HABrokerInfo)
     * @param oldValue (depending on updateType)
     * @param newValue (depending on updateType)
     * @return current active store session UID only if reseted takeover broker
     * @throws BrokerException
     */
    public UID update( Connection conn, String id, 
                        int updateType, Object oldValue, Object newValue )
                        throws BrokerException {

        String _updatesql = "";

        UID currentID = null;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            switch (updateType) {
                case HABrokerInfo.UPDATE_VERSION:
                    _updatesql = updateVersionSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, updateVersionSQL );
                    pstmt.setInt( 1, ((Integer)newValue).intValue() );
                    pstmt.setString( 2, id );
                    pstmt.executeUpdate();
                    pstmt.close();
                    break;
                case HABrokerInfo.UPDATE_URL:
                    _updatesql = updateURLSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, updateURLSQL );
                    pstmt.setString( 1, (String)newValue );
                    pstmt.setString( 2, id );
                    pstmt.executeUpdate();
                    pstmt.close();
                    break;
                case HABrokerInfo.RESET_TAKEOVER_BROKER_READY_OPERATING:
                    _updatesql = resetTakeoverBrokerSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, resetTakeoverBrokerSQL );
                    pstmt.setInt( 1, (BrokerState.OPERATING).intValue() );
                    pstmt.setString( 2, id );
                    int updateCnt = pstmt.executeUpdate();
                    pstmt.close();
                    if (updateCnt == 1) {
                        logger.log(logger.INFO,  br.getKString(
                            BrokerResources.I_THIS_BROKER_RESETED_TAKEOVER_BROKER, TAKEOVER_BROKER_COLUMN, id));
                        long ssid = dbMgr.getDAOFactory().getStoreSessionDAO().insert( 
                                          conn, id, ((UID)newValue).longValue(), false);
                        logger.log(logger.INFO,  br.getKString(
                            BrokerResources.I_THIS_BROKER_CURRENT_STORE_SESSION, id, String.valueOf(ssid)));
                        currentID = new UID(ssid);
                        break;
                    } else if (updateCnt == 0) {
                        _updatesql = updateStateThisBrokerSQL;
                        if (!updateState( conn, id, BrokerState.OPERATING,
                                          (BrokerState)oldValue, true )) {
                            HABrokerInfo info = getBrokerInfo( conn, id);
                            throw new BrokerException(
                            "IllegalStateException for updating state "+oldValue+
                            " to "+BrokerState.OPERATING.toString()+": "+info);
                        }
                        break;
                    } else {
                        throw new BrokerException(
                        "Unexpected affected row count "+updateCnt+" for updating broker info "+id);
                    }
                case HABrokerInfo.RESTORE_ON_TAKEOVER_FAIL:
                    
                    if (fi.FAULT_INJECTION) {
                        fi.checkFaultAndThrowBrokerException(
                           FaultInjection.FAULT_HA_TAKEOVER_RESTORE_EXCEPTION, null);
                        fi.checkFaultAndExit(
                           FaultInjection.FAULT_HA_TAKEOVER_RESTORE_HALT, null, 2, false);
                    }

                    _updatesql = restoreOnTakeoverFailSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, restoreOnTakeoverFailSQL );
                    pstmt.setInt( 1, ((HABrokerInfo)newValue).getState() );
                    pstmt.setString( 2, id );
                    pstmt.setString( 3, (String)oldValue );
                    updateCnt = pstmt.executeUpdate();
                    pstmt.close();
                    if (updateCnt != 1) {
                        throw new BrokerException(
                        "Unexpected affected row count "+updateCnt+" for restoring broker info "+id);
                    }
                    break;

                case HABrokerInfo.RESTORE_HEARTBEAT_ON_TAKEOVER_FAIL:
                    _updatesql = restoreHeartbeatOnTakeoverFailSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, restoreHeartbeatOnTakeoverFailSQL );
                    pstmt.setLong( 1, ((HABrokerInfo)newValue).getHeartbeat() );
                    pstmt.setString( 2, id );
                    pstmt.setLong( 3, ((HABrokerInfo)newValue).getTakeoverTimestamp() );
                    pstmt.setString( 4, (String)oldValue );
                    pstmt.executeUpdate();
                    pstmt.close();
                    break;

                default: 
                    String emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Unknown update type "+updateType+" for updating broker info "+id);
                    BrokerException ex = new BrokerException(emsg);
                    logger.logStack(logger.ERROR, emsg, ex);
                    throw ex;
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
                ex = DBManager.wrapSQLException("[" + _updatesql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_BROKERINFO_FAILED, id ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update the broker heartbeat timestamp to the current time.
     * @param conn database connection
     * @param id Broker ID
     * @return new heartbeat timestamp if set else null
     * @throws BrokerException
     */
    public Long updateHeartbeat( Connection conn, String id)
        throws BrokerException {

        Long newheartbeat = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                try {
                    conn = dbMgr.getConnectionNoRetry( true );
                    myConn = true;
                } catch (BrokerException e) {
                    e.setSQLReconnect(true);
                    e.setSQLRecoverable(true);
                    throw e;
                }
            }

            long heartbeat = System.currentTimeMillis();
            pstmt = dbMgr.createPreparedStatement( conn, updateHeartbeatSQL );
            pstmt.setLong( 1, heartbeat );
            pstmt.setString( 2, id );
            if ( pstmt.executeUpdate() == 1 ) {
                 newheartbeat = Long.valueOf( heartbeat );
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
                ex = DBManager.wrapSQLException("[" + updateHeartbeatSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_UPDATE_HEARTBEAT_TS_FAILED, id ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        return newheartbeat;
    }

    /**
     * Update the broker heartbeat timestamp only if the specified lastHeartbeat
     * value match the value store in the DB.
     * @param conn database connection
     * @param id Broker ID
     * @param lastHeartbeat broker's last heartbeat
     * @return new heartbeat timestamp if set else null
     * @throws BrokerException
     */
    public Long updateHeartbeat( Connection conn, String id,
        long lastHeartbeat ) throws BrokerException {

        Long newheartbeat = null;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                try {
                    conn = dbMgr.getConnectionNoRetry( true );
                    myConn = true;
                } catch (BrokerException e) {
                    e.setSQLReconnect(true);
                    e.setSQLRecoverable(true);
                    throw e;
                }
            }

            long heartbeat = System.currentTimeMillis();
            pstmt = dbMgr.createPreparedStatement( conn, updateHeartbeatAndCheckStateSQL );
            pstmt.setLong( 1, heartbeat );
            pstmt.setString( 2, id );
            pstmt.setLong( 3, lastHeartbeat );
            if ( pstmt.executeUpdate() == 0 ) {
                HABrokerInfo bkrInfo = getBrokerInfo( conn, id );
                if ( bkrInfo == null ) {
                    String errorMsg = br.getKString(
                        BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, id );
                    throw new BrokerException( br.getKString(
                        BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, id, errorMsg ) );
                }

                // Verify if persistent store is being taken over
                String takeoverBroker = bkrInfo.getTakeoverBrokerID();
                int state = bkrInfo.getState();
                if ( takeoverBroker != null && takeoverBroker.length() > 0 &&
                     ( state == BrokerState.I_FAILOVER_PENDING ||
                       state == BrokerState.I_FAILOVER_STARTED ||
                       state == BrokerState.I_FAILOVER_COMPLETE ||
                       state == BrokerState.I_FAILOVER_FAILED ) ) {
                    BrokerException be = new StoreBeingTakenOverException(
                        br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );
                    logger.log( Logger.ERROR, br.getKString(
                        BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, id,
                        bkrInfo.toString() ), be );
                    throw be;
                }
            } else {
                newheartbeat = Long.valueOf( heartbeat );
            }
        } catch ( Exception e ) {
            myex = e;
            boolean recoverable = false;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                    recoverable = true;
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateHeartbeatAndCheckStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            String arg = "Expected last heartbeat " + lastHeartbeat;
            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, id, arg ), ex );
            be.setSQLRecoverable(recoverable);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        return newheartbeat;
    }

    /**
     * Update the state of a broker only if the current state matches the
     * expected state.
     * @param conn database connection
     * @param id Broker ID
     * @param newState the new state
     * @param expectedState the expected state
     * @return true if state is successfully updated.
     * @throws BrokerException
     */
    public boolean updateState( Connection conn, String id,
                                BrokerState newState, 
                                BrokerState expectedState,
                                boolean local) throws BrokerException {

        String _updatesql = "";

        boolean updated = false;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                try {
                    conn = dbMgr.getConnectionNoRetry( true );
                    myConn = true;
                } catch (BrokerException e) {
                    e.setSQLReconnect(true);
                    e.setSQLRecoverable(true);
                    throw e;
                }
            }

            if ( local ) {
                _updatesql = updateStateThisBrokerSQL;
                pstmt = dbMgr.createPreparedStatement( conn, updateStateThisBrokerSQL );
                pstmt.setInt( 1, newState.intValue() );
                pstmt.setString( 2, id );
                pstmt.setInt( 3, expectedState.intValue() );
                if (pstmt.executeUpdate() == 1) {
                    updated = true;
                }

            } else {
                _updatesql = updateStateOtherBrokerSQL;
                pstmt = dbMgr.createPreparedStatement( conn, updateStateOtherBrokerSQL );
                pstmt.setInt( 1, newState.intValue() );
                pstmt.setString( 2, id );
                pstmt.setInt( 3, expectedState.intValue() );
                pstmt.setString( 4, DBManager.getDBManager().getBrokerID() );
                if ( pstmt.executeUpdate() == 1 ) {
                    updated = true;
                }
            }
        } catch ( Exception e ) {
            myex = e;
            boolean recoverable = false;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                    recoverable = true;
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + _updatesql  + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_BROKERINFO_FAILED, id ), ex );
            be.setSQLRecoverable(recoverable);
            throw be;
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
     * Update the state and other relevant attributes of a broker to signify
     * the store is being taken over by another broker. If the operation is
     * successful then this means that the current broker was able to acquire
     * the lock and it is now responsible for taken over the store of the
     * target broker.
     * @param conn database connection
     * @param id the current or local broker ID
     * @param targetBrokerID the broker ID of the store being taken over
     * @param lastHeartbeat broker's last heartbeat
     * @param expectedState the expected state
     * @param newHeartbeat the new timestamp
     * @param newState the new state
     * @throws TakeoverLockException if the current broker is unable to acquire
     *      the takeover lock
     * @throws BrokerException
     * @return previous broker's info associated with the broker
     */
    public HABrokerInfo takeover( Connection conn, String id,
        String targetBrokerID, long lastHeartbeat, BrokerState expectedState,
        long newHeartbeat, BrokerState newState) throws BrokerException {

        HABrokerInfo bkrInfo = null;

        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Save the broker's state before updating
            bkrInfo = getBrokerInfo( conn, targetBrokerID );
            if ( bkrInfo == null ) {
                String errorMsg = br.getKString(
                    BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, targetBrokerID );
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTERNAL_BROKER_ERROR, errorMsg ) );
            }

            DBManager dbMgr = DBManager.getDBManager();
            pstmt = dbMgr.createPreparedStatement( conn, takeoverSQL );
            pstmt.setString( 1, id );
            pstmt.setInt( 2, newState.intValue() );
            pstmt.setLong( 3, newHeartbeat );
            pstmt.setString( 4, targetBrokerID );
            pstmt.setInt( 5, expectedState.intValue() );
            pstmt.setLong( 6, lastHeartbeat );

            if ( pstmt.executeUpdate() != 1 ) {
                HABrokerInfo binfo = getBrokerInfo( conn, targetBrokerID );
                String errorMsg = br.getKString(
                    BrokerResources.E_UNABLE_TO_ACQUIRE_TAKEOVER_LOCK, targetBrokerID );
                TakeoverLockException ex = new TakeoverLockException( errorMsg );
                ex.setBrokerInfo( binfo ); // Store broker info
                throw ex;
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
                br.getKString( BrokerResources.E_UNABLE_TO_TAKEOVER_BROKER, targetBrokerID ), ex );
        } finally {
            Util.close( null, pstmt, null, myex );
        }

        return bkrInfo;
    }

    /**
     * Delete an entry.
     * @param conn database connection
     * @param id Broker ID
     * @throws BrokerException
     */
    public void delete( Connection conn, String id )
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
            pstmt.setString( 1, id );
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
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_BROKERINFO_FAILED, id ), ex );
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
     * @throws BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        if ( Globals.getHAEnabled() ) {
            return; // // Broker table cannot be reset
        } else {
            super.deleteAll( conn );
        }
    }

    /**
     * Get the heartbeat timestamp for the specified brokerID.
     * @param conn database connection
     * @param id Broker ID
     * @return heartbeat timestamp
     * @throws BrokerException
     */
    public long getHeartbeat( Connection conn, String id )
        throws BrokerException {

        long heartBeat = -1;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectHeartbeatSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                heartBeat = rs.getLong( 1 );
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
                ex = DBManager.wrapSQLException("[" + selectHeartbeatSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_BROKERINFO_FAILED, id ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return heartBeat;
    }

    /**
     * Get the heartbeat timestamp for all brokers in an HA cluster.
     * @param conn database connection
     * @return a HashMap object where the key is the broker ID and the entry
     * value is the broker's heartbeat timestamps
     * @throws BrokerException
     */
    public HashMap getAllHeartbeats( Connection conn )
        throws BrokerException {

        HashMap data = new HashMap();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAllHeartbeatsSQL );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                String id = rs.getString( 1 );
                long timestamp = rs.getLong( 2 );
                data.put( id, Long.valueOf( timestamp ) );
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
                ex = DBManager.wrapSQLException("[" + selectAllHeartbeatsSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_ALL_BROKERINFO_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return data;
    }

    /**
     * Get the state for the specified brokerID.
     * @param conn database connection
     * @param id Broker ID
     * @return state of the broker
     * @throws BrokerException
     */
    public BrokerState getState( Connection conn, String id )
        throws BrokerException {

        BrokerState state = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                try {
                    conn = dbMgr.getConnectionNoRetry( true );
                    myConn = true;
                } catch (BrokerException e) {
                    e.setSQLReconnect(true);
                    e.setSQLRecoverable(true);
                    throw e;
                }
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectStateSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                state = BrokerState.getState( rs.getInt( 1 ) );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, id ),
                    Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_BROKERINFO_FAILED, id ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return state;
    }

    /**
     * Get the state for all brokers in an HA cluster.
     * @param conn database connection
     * @return an array of Object whose 1st element contains an ArrayList
     * of broker IDs and the 2nd element contains an ArrayList of BrokerState
     * @throws BrokerException
     */
    public Object[] getAllStates( Connection conn )
        throws BrokerException {

        ArrayList ids = new ArrayList();
        ArrayList states = new ArrayList();
        Object[] data = new Object[2];
        data[0] = ids;
        data[1] = states;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAllStatesSQL );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                String id = rs.getString( 1 );
                int state = rs.getInt( 2 );
                ids.add( id );
                states.add( BrokerState.getState( state ) );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllStatesSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllStatesSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_ALL_BROKERINFO_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return data;
    }

    /**
     * Get broker information.
     * @param conn database connection
     * @param id the broker ID.
     * @return a HABrokerInfo object
     * @throws BrokerException
     */
    public HABrokerInfo getBrokerInfo( Connection conn, String id )
        throws BrokerException {

        HABrokerInfo bkrInfo = null;

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
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                bkrInfo = loadData( rs );
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
                br.getKString( BrokerResources.X_LOAD_BROKERINFO_FAILED, id ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return bkrInfo;
    }

    /**
     * Get broker information for all brokers.
     * @param conn database connection
     * @param loadSession specify if store sessions should be loaded
     * @return a HashMap object containing HABrokerInfo for all brokers
     * @throws BrokerException
     */
    public HashMap getAllBrokerInfos( Connection conn, boolean loadSession )
        throws BrokerException {

        HashMap data = new HashMap();

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
                HABrokerInfo bkrInfo = loadData( rs );
                data.put( bkrInfo.getId(), bkrInfo );
            }

            if ( loadSession ) {
                rs.close();
                pstmt.close();
                
                Map sessionMap =
                    dbMgr.getDAOFactory().getStoreSessionDAO().getAllStoreSessions( conn );
                Iterator itr = sessionMap.entrySet().iterator();
                while ( itr.hasNext() ) {
                    Map.Entry entry = (Map.Entry)itr.next();
                    String brokerID = (String)entry.getKey();
                    HABrokerInfo bkrInfo = (HABrokerInfo)data.get( brokerID );
                    if ( bkrInfo != null ) {
                        bkrInfo.setSessionList( (List)entry.getValue() );
                    }
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

        	Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_ALL_BROKERINFO_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return data;
    }

    /**
     * Get broker information for all brokers in an HA cluster by state.
     * @param conn database connection
     * @param state the state of the broker
     * @return a HashMap object containing HABrokerInfo for all brokers
     * @throws BrokerException
     */
    public HashMap getAllBrokerInfosByState( Connection conn, BrokerState state )
        throws BrokerException {

        HashMap data = new HashMap();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAllByStateSQL );
            pstmt.setInt( 1, state.intValue() );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                HABrokerInfo bkrInfo = loadData( rs );
                data.put( bkrInfo.getId(), bkrInfo );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllByStateSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllByStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_ALL_BROKERINFO_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return data;
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new HashMap();
        StringBuffer strBuf = new StringBuffer( 512 );

        try {
            // Get info for all brokers in the cluster
            Collection data = getAllBrokerInfos( conn, false ).values();
            Iterator itr = data.iterator();
            while ( itr.hasNext() ) {
                Object obj = itr.next();
                strBuf.append( obj.toString() ).append( BrokerResources.NL );
            }

        } catch ( Exception e ) {}

        map.put( "Broker(" + tableName + ")", strBuf.toString() );
        return map;
    }

    /**
     * Check whether the specified broker is being taken over by another broker.
     * @param conn database connection
     * @param id Broker ID
     * @return true if the specified broker is being taken over
     * @throws BrokerException
     */
    public boolean isBeingTakenOver( Connection conn, String id )
        throws BrokerException {

        BrokerState brokerState = getState( conn, id );
        int state = brokerState.intValue();
        return ( state == BrokerState.I_FAILOVER_PENDING ||
                 state == BrokerState.I_FAILOVER_STARTED ||
                 state == BrokerState.I_FAILOVER_COMPLETE ||
                 state == BrokerState.I_FAILOVER_FAILED );
    }

    /**
     * Load the broker info to a value object.
     */
    protected HABrokerInfo loadData( ResultSet rs )
        throws SQLException {

        HABrokerInfo brokerInfo = new HABrokerInfo(
            rs.getString( ID_COLUMN ),
            rs.getString( TAKEOVER_BROKER_COLUMN ),
            rs.getString( URL_COLUMN ),
            rs.getInt( VERSION_COLUMN ),
            rs.getInt( STATE_COLUMN ),
            rs.getLong( STORE_SESSION_ID_COLUMN ),
            rs.getLong( HEARTBEAT_TS_COLUMN )
        );

        return brokerInfo;
    }
}
