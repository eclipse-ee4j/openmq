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
 * @(#)TransactionDAOImpl.java	1.34 08/13/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic TransactionDAO.
 */
class TransactionDAOImpl extends BaseDAOImpl implements TransactionDAO {
    private static boolean DEBUG = false;

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String updateTxnStateSQL;
    private final String updateTxnHomeBrokerSQL;
    private final String updateTxnBrokersSQL;
    private final String updateAccessedTimeSQL;
    private final String deleteSQL;
    private final String selectTxnStateSQL;
    private final String selectTxnHomeBrokerSQL;
    private final String selectTxnBrokersSQL;
    private final String selectAccessedTimeSQL;
    private final String selectTxnInfoSQL;
    private final String selectTxnStatesByBrokerSQL;
    private final String selectTxnStatesBySessionSQL;
    private final String selectTxnStatesByBrokerAndTypeSQL;
    private final String selectTxnStatesBySessionAndTypeSQL;
    private final String selectRemoteTxnStatesByBrokerAndTypeSQL;
    private final String selectRemoteTxnStatesBySessionAndTypeSQL;
    private final String selectUsageInfoSQL;


    /**
     * Constructor
     * @throws BrokerException
     */
    TransactionDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        // Criteria to ensure the local broker still owns the store for HA
        String brokerNotTakenOverClause = Globals.getHAEnabled() ?
            " AND NOT EXISTS (" +
            ((BrokerDAOImpl)dbMgr.getDAOFactory().getBrokerDAO())
                .selectIsBeingTakenOverSQL + ")" : "";

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( AUTO_ROLLBACK_COLUMN ).append( ", " )
            .append( XID_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN ).append( ", " )
            .append( TXN_HOME_BROKER_COLUMN ).append( ", " )
            .append( TXN_BROKERS_COLUMN ).append( ", " )
            .append( STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( EXPIRED_TS_COLUMN ).append( ", " )
            .append( ACCESSED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )" )
            .toString();

        updateTxnStateSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( STATE_COLUMN ).append( " = ?, " )
            .append( TXN_STATE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( brokerNotTakenOverClause )
            .toString();

        updateTxnHomeBrokerSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TXN_HOME_BROKER_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( brokerNotTakenOverClause )
            .toString();

        updateTxnBrokersSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TYPE_COLUMN ).append( " = ?, " )
            .append( TXN_BROKERS_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( brokerNotTakenOverClause )
            .toString();

        updateAccessedTimeSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( ACCESSED_TS_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        /*
        deleteNotInStateSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( STATE_COLUMN ).append( " <> ?" )
            .toString();
         */

        selectTxnStateSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectTxnHomeBrokerSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TXN_HOME_BROKER_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectTxnBrokersSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TXN_BROKERS_COLUMN ).append( ", " )
            .append( STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectAccessedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ACCESSED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectTxnInfoSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN ).append( ", " )
            .append( TXN_HOME_BROKER_COLUMN ).append( ", " )
            .append( TXN_BROKERS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectTxnStatesByBrokerSQL = new StringBuffer(128)
            .append( "SELECT txnTbl." )
            .append( ID_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append(", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN ).append( ", " )
            .append( TXN_BROKERS_COLUMN )
            .append( " FROM " ).append( tableName ).append( " txnTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = txnTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append( " AND " )
            .append(  STATE_COLUMN ).append( " <> -1" )
            .append( " AND " )
            .append(  TYPE_COLUMN ).append( " IN (" )
            .append(  TransactionInfo.TXN_LOCAL ).append( ", " )
            .append(  TransactionInfo.TXN_CLUSTER ).append( ")" )
            .toString();

        selectTxnStatesBySessionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append(", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN ).append( ", " )
            .append( TXN_BROKERS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( STORE_SESSION_ID_COLUMN ).append( "= ?" )
            .append( " AND " )
            .append(  STATE_COLUMN ).append( " <> -1" )
            .append( " AND " )
            .append(  TYPE_COLUMN ).append( " IN (" )
            .append(  TransactionInfo.TXN_LOCAL ).append( ", " )
            .append(  TransactionInfo.TXN_CLUSTER ).append( ")" )
            .toString();

        selectTxnStatesByBrokerAndTypeSQL = new StringBuffer(128)
            .append( "SELECT txnTbl." )
            .append( ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN )
            .append( " FROM " ).append( tableName ).append( " txnTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = txnTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " <> -1" )
            .append( " AND " )
            .append( TYPE_COLUMN ).append( " = ?" )
            .toString();

        selectTxnStatesBySessionAndTypeSQL = new StringBuffer(128)
            .append( "SELECT txnTbl." )
            .append( ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( STORE_SESSION_ID_COLUMN ).append( "= ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " <> -1" )
            .append( " AND " )
            .append( TYPE_COLUMN ).append( " = ?" )
            .toString();

        /*
         * Cannot specify a LOB column in a SELECT...DISTINCT so use subquery
         */
        selectRemoteTxnStatesByBrokerAndTypeSQL = new StringBuffer(256)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN )
            .append( " FROM " )
            .append( tableName )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STORE_SESSION_ID_COLUMN )
            .append( " NOT IN (SELECT " ).append( StoreSessionDAO.ID_COLUMN )
            .append( " FROM ")
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append( " WHERE ")
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?)" )
            .append( " AND " )
            .append( ID_COLUMN )
            .append( " IN (SELECT DISTINCT c." )
            .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN )
            .append( " FROM " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " s, " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " m, " )
            .append(   dbMgr.getTableName( ConsumerStateDAO.TABLE_NAME_PREFIX ) )
            .append(   " c" )
            .append( " WHERE s." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND s." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = m." ).append( MessageDAO.STORE_SESSION_ID_COLUMN )
            .append( " AND m." ).append( MessageDAO.ID_COLUMN )
            .append(   " = c." ).append( ConsumerStateDAO.MESSAGE_ID_COLUMN )
            .append( " AND c." ).append( ConsumerStateDAO.TRANSACTION_ID_COLUMN )
            .append(   " IS NOT NULL)" )
            .toString();

        selectRemoteTxnStatesBySessionAndTypeSQL = new StringBuffer(256)
            .append( "SELECT " )
            .append( ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( TXN_STATE_COLUMN )
            .append( " FROM " )
            .append( tableName )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STORE_SESSION_ID_COLUMN ).append( "<> ?" )
            .append( " AND " )
            .append( ID_COLUMN )
            .append( " IN (SELECT DISTINCT c." )
            .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN )
            .append( " FROM " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " m, " )
            .append(   dbMgr.getTableName( ConsumerStateDAO.TABLE_NAME_PREFIX ) )
            .append(   " c" )
            .append( " WHERE m." )
            .append( MessageDAO.STORE_SESSION_ID_COLUMN ).append( "= ?" )
            .append( " AND m." ).append( MessageDAO.ID_COLUMN )
            .append(   " = c." ).append( ConsumerStateDAO.MESSAGE_ID_COLUMN )
            .append( " AND c." ).append( ConsumerStateDAO.TRANSACTION_ID_COLUMN )
            .append(   " IS NOT NULL)" )
            .toString();

        selectUsageInfoSQL = new StringBuffer(128)
            .append( "SELECT MAX(mcount), MAX(scount) FROM (" )
            .append(    "SELECT COUNT(*) AS mcount, 0 AS scount FROM " )
            .append(    dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(    " WHERE " )
            .append(    MessageDAO.TRANSACTION_ID_COLUMN ).append( " = ?" )
            .append( " UNION " )
            .append(    "SELECT 0 AS mcount, COUNT(*) AS scount FROM " )
            .append(    dbMgr.getTableName( ConsumerStateDAO.TABLE_NAME_PREFIX ) )
            .append(    " WHERE " )
            .append(    ConsumerStateDAO.TRANSACTION_ID_COLUMN ).append( " = ?" )
            .append( ") tmptbl" )
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
    public String getTableName() {
        return tableName;
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param txnState the TransactionState
     * @param storeSessionID the store session ID
     * @throws BrokerException
     */
    public void insert( Connection conn, TransactionUID txnUID,
        TransactionState txnState, long storeSessionID )
        throws BrokerException {

        insert( conn, txnUID, txnState, null, null, TransactionInfo.TXN_LOCAL,
            storeSessionID );
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param txnState the TransactionState
     * @param txnHomeBroker the home broker for a REMOTE txn
     * @param txnBrokers the participant brokers for a REMOTE/CLUSTER txn
     * @param storeSessionID the store session ID
     * @throws BrokerException
     */
    public void insert( Connection conn, TransactionUID txnUID,
        TransactionState txnState, BrokerAddress txnHomeBroker,
        TransactionBroker[] txnBrokers, int type, long storeSessionID )
        throws BrokerException {

        long id = txnUID.longValue();

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

            try {
                pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
                pstmt.setLong( 1, id );
                pstmt.setInt( 2, type );
                pstmt.setInt( 3, txnState.getState() );
                pstmt.setInt( 4, txnState.getType().intValue() );

                JMQXid jmqXid = txnState.getXid();
                if ( jmqXid != null ) {
                    pstmt.setString( 5, jmqXid.toString() );
                } else {
                    pstmt.setNull( 5, Types.VARCHAR );
                }

                Util.setObject( pstmt, 6, txnState );
                Util.setObject( pstmt, 7, txnHomeBroker );
                Util.setObject( pstmt, 8, txnBrokers );

                pstmt.setLong( 9, storeSessionID );
                pstmt.setLong( 10, txnState.getExpirationTime() );
                pstmt.setLong( 11, txnState.getLastAccessTime() );
                pstmt.executeUpdate();
            } catch ( Exception e ) {
                myex = e;
                try {
                    if ( !conn.getAutoCommit() ) {
                        conn.rollback();
                    }
                } catch ( SQLException rbe ) {
                    logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
                }

                try {
                    // We check for the existence of the txn here instead of
                    // doing it before the INSERT stmt for performance, i.e.
                    // the chance of inserting duplicate record is very small
                    if ( hasTransaction( conn, id) ) {
                        throw new BrokerException(
                            br.getKString( BrokerResources.E_TRANSACTIONID_EXISTS_IN_STORE,
                            txnUID.toString() ) );
                    }
                } catch ( Exception e2 ) {
                    // Ignore this exception so orig exception can be thrown!
                    logger.log( Logger.WARNING, br.getKString(
                        br.X_STORE_CHECK_EXISTENCE_TXN_AFTER_ADD_FAILURE, txnUID+
                        "["+storeSessionID+", "+txnHomeBroker+"]", e2.getMessage()) );
                }

                Exception ex;
                if ( e instanceof BrokerException ) {
                    throw (BrokerException)e;
                } else if ( e instanceof IOException ) {
                    ex = DBManager.wrapIOException("[" + insertSQL + "]", (IOException)e);
                } else if ( e instanceof SQLException ) {
                    ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
                } else {
                    ex = e;
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.X_PERSIST_TRANSACTION_FAILED,
                    txnUID ), ex );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update the TransactionState for the specified transaction.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param txnState the new state
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateTransactionState( Connection conn, TransactionUID txnUID,
        TransactionState txnState, boolean replaycheck ) throws BrokerException {

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

            if (replaycheck) {
                TransactionState st = null;
                try {
                    st = getTransactionState( conn, txnUID );
                    if (st.getState() == txnState.getState()) {
                        logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY,
                            "TID:"+txnUID+"["+txnState+"]");
                        return;
                    }
                } catch (BrokerException e) {
                    if (e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                        e.setSQLReplayCheck(true);
                        throw e;
                    }
                }
            }

            pstmt = dbMgr.createPreparedStatement( conn, updateTxnStateSQL );
            pstmt.setInt( 1, txnState.getState() );
            Util.setObject( pstmt, 2, txnState );
            pstmt.setLong( 3, txnUID.longValue() );

            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 4, dbMgr.getBrokerID() );
            }

            if ( pstmt.executeUpdate() == 0 ) {
                // For HA mode, check if this broker still owns the store
                if ( Globals.getHAEnabled() ) {
                    String brokerID = dbMgr.getBrokerID();
                    BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                    if ( dao.isBeingTakenOver( conn, brokerID ) ) {
                        BrokerException be = new StoreBeingTakenOverException(
                            br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );

                        try {
                            HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, brokerID );
                            logger.logStack( Logger.ERROR, be.getMessage()+"["+
                                (bkrInfo == null ? ""+brokerID:bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            boolean replayck = false;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                replayck = true;
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + updateTxnStateSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateTxnStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }
            BrokerException be =  new BrokerException(
                br.getKString( BrokerResources.X_UPDATE_TXNSTATE_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            if (replayck) {
                be.setSQLReplayCheck(true);
            }
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update the transaction home broker for the specified transaction.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param txnHomeBroker the home broker for a REMOTE txn
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateTransactionHomeBroker( Connection conn, TransactionUID txnUID,
        BrokerAddress txnHomeBroker) throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, updateTxnHomeBrokerSQL );
            Util.setObject( pstmt, 1, txnHomeBroker );
            pstmt.setLong( 2, txnUID.longValue() );

            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 3, dbMgr.getBrokerID() );
            }

            if ( pstmt.executeUpdate() == 0 ) {
                // For HA mode, check if this broker still owns the store
                if ( Globals.getHAEnabled() ) {
                    String brokerID = dbMgr.getBrokerID();
                    BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                    if ( dao.isBeingTakenOver( conn, brokerID ) ) {
                        BrokerException be = new StoreBeingTakenOverException(
                            br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );

                        try {
                            HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, brokerID );
                            logger.logStack( Logger.ERROR, be.getMessage()+"["+
                                (bkrInfo == null ? ""+brokerID:bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
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
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + updateTxnHomeBrokerSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateTxnHomeBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }
            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_TRANSACTION_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update transaction's participant brokers for the specified transaction.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param txnBrokers the transaction's participant brokers
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateTransactionBrokers( Connection conn, TransactionUID txnUID,
        TransactionBroker[] txnBrokers ) throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, updateTxnBrokersSQL );
            pstmt.setInt( 1, TransactionInfo.TXN_CLUSTER );
            Util.setObject( pstmt, 2, txnBrokers );
            pstmt.setLong( 3, txnUID.longValue() );

            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 4, dbMgr.getBrokerID() );
            }

            if ( pstmt.executeUpdate() == 0 ) {
                // For HA mode, check if this broker still owns the store
                if ( Globals.getHAEnabled() ) {
                    String brokerID = dbMgr.getBrokerID();
                    BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                    if ( dao.isBeingTakenOver( conn, brokerID ) ) {
                        BrokerException be = new StoreBeingTakenOverException(
                            br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );

                        try {
                            HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, brokerID );
                            logger.logStack( Logger.ERROR, be.getMessage()+"["+
                                (bkrInfo == null ? ""+brokerID:bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
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
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + updateTxnBrokersSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateTxnBrokersSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }
            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_TRANSACTION_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update transaction's participant broker state if the txn's state
     * matches the expected state.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param expectedTxnState the expected transaction state
     * @param txnBkr the participant broker to be updated
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateTransactionBrokerState( Connection conn, TransactionUID txnUID,
        int expectedTxnState, TransactionBroker txnBkr) throws BrokerException {

        long id = txnUID.longValue();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            // First, retrieve the TransactionBroker array
            int state;
            TransactionBroker[] txnBrokers;

            pstmt = dbMgr.createPreparedStatement( conn, selectTxnBrokersSQL );
            pstmt.setLong( 1, id );

            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                txnBrokers = (TransactionBroker[])Util.readObject( rs, 1 );
                state = rs.getInt( 2 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
            }

            if ( state != expectedTxnState ) {
                Object[] args = { txnBkr, txnUID,
                    TransactionState.toString(expectedTxnState),
                    TransactionState.toString(state) };
                throw new BrokerException(br.getKString( BrokerResources.E_UPDATE_TXNBROKER_FAILED,
                    args ), Status.CONFLICT);
            }

            // Update the participant broker state
            for ( int i = 0, len = txnBrokers.length; i < len; i++ ) {
                TransactionBroker bkr = txnBrokers[i];
                if ( bkr.equals( txnBkr ) ) {
                    bkr.copyState( txnBkr );
                    break; // done
                }
            }

            // Now update the DB entry
            updateTransactionBrokers( conn, txnUID, txnBrokers );

            // Commit all changes
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
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + selectTxnBrokersSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectTxnBrokersSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }
            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_TRANSACTION_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }
    }

    /**
     * Update the transaction accessed time.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @param accessedTime the new timestamp
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateAccessedTime( Connection conn, TransactionUID txnUID,
        long accessedTime ) throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, updateAccessedTimeSQL );
            pstmt.setLong( 1, accessedTime );
            pstmt.setLong( 2, txnUID.longValue() );

            if ( pstmt.executeUpdate() == 0 ) {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + updateAccessedTimeSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_TRANSACTION_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete an existing entry.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @throws BrokerException
     */
    public void delete( Connection conn, TransactionUID txnUID )
        throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, deleteSQL );
            pstmt.setLong( 1, txnUID.longValue() );

            if ( pstmt.executeUpdate() == 0 ) {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    txnUID ), Status.NOT_FOUND );
            } else {
                // For HA, there is an edge case where msgs is being redelivered
                // after the takeover eventhough the txn has been committed.
                // What happen is the broker has committed the txn but didn't
                // has a chance to ack or deleted the msgs and it crashed.
                // So to be on the safe side, we'll delete all consumer states
                // for this txn when it is committed, i.e. translate to txn is
                // being removed from data store.
                dbMgr.getDAOFactory().getConsumerStateDAO().deleteByTransaction( conn, txnUID );
            }

            // Commit all changes
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
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_TRANSACTION_FAILED,
                txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
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

        String whereClause = null;
        if ( Globals.getHAEnabled() ) {
            DBManager dbMgr = DBManager.getDBManager();

            // Only delete transactions that belong to the running broker,
            // construct the where clause for the delete statement:
            //   DELETE FROM mqtxn41cmycluster
            //   WHERE EXISTS
            //     (SELECT id FROM mqses41cmycluster
            //      WHERE  id = mqtxn41cmycluster.store_session_id AND
            //             broker_id = 'mybroker')
            whereClause = new StringBuffer(128)
                .append( "EXISTS (SELECT " )
                .append(   StoreSessionDAO.ID_COLUMN )
                .append(   " FROM " )
                .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
                .append(   " WHERE " )
                .append(   StoreSessionDAO.ID_COLUMN ).append( " = " )
                .append(   tableName ).append( "." ).append( STORE_SESSION_ID_COLUMN )
                .append(   " AND " )
                .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = '" )
                .append(   dbMgr.getBrokerID() ).append( "')" )
                .toString();
        }

        deleteAll( conn, whereClause, null, 0 );
    }

    /**
     * Get the TransactionState object.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return TransactionState object
     * @throws BrokerException
     */
    public TransactionState getTransactionState( Connection conn,
        TransactionUID txnUID ) throws BrokerException {

        TransactionState txnState = null;
        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTxnStateSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                try {
                    int state = rs.getInt( 1 );
                    txnState = (TransactionState)Util.readObject( rs, 2 );

                    // update state in TransactionState object
                    txnState.setState( state );
                } catch ( IOException e ) {
                    // fail to parse TransactionState object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_TRANSACTION_FAILED, e );
                }
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    String.valueOf( id ) ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectTxnStateSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectTxnStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return txnState;
    }

    /**
     * Get the transaction home broker for the specified transaction.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return BrokerAddress object
     * @throws BrokerException
     */
    public BrokerAddress getTransactionHomeBroker( Connection conn,
        TransactionUID txnUID ) throws BrokerException {

        BrokerAddress txnHomeBroker = null;
        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTxnHomeBrokerSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                txnHomeBroker = (BrokerAddress)Util.readObject( rs, 1 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    String.valueOf( id ) ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectTxnHomeBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return txnHomeBroker;
    }

    /**
     * Get transaction's participant brokers for the specified transaction..
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return an array of TransactionBroker object
     * @throws BrokerException
     */
    public TransactionBroker[] getTransactionBrokers( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        TransactionBroker[] txnBrokers = null;
        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTxnBrokersSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                txnBrokers = (TransactionBroker[])Util.readObject( rs, 1 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    String.valueOf( id ) ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectTxnBrokersSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return txnBrokers;
    }

    /**
     * Get the TransactionInfo object.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return TransactionInfo object
     * @throws BrokerException
     */
    public TransactionInfo getTransactionInfo( Connection conn,
        TransactionUID txnUID ) throws BrokerException {

        TransactionInfo txnInfo = null;
        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTxnInfoSQL );
            pstmt.setLong( 1, id );

            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                int type = rs.getInt( 1 );
                int state = rs.getInt( 2 );

                TransactionState txnState =
                    (TransactionState)Util.readObject( rs, 3 );

                // update state in TransactionState object
                txnState.setState( state );

                BrokerAddress txnHomeBroker =
                    (BrokerAddress)Util.readObject( rs, 4 );
                TransactionBroker[] txnBrokers =
                    (TransactionBroker[])Util.readObject( rs, 5 );

                txnInfo = new TransactionInfo( txnState, txnHomeBroker,
                    txnBrokers, type );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    String.valueOf( id ) ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectTxnInfoSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
            be.setSQLRecoverable(true);
            throw be; 
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return txnInfo;
    }

    /**
     * Get the time when the transaction was last accessed.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return Timestamp when the transaction was last accessed
     * @throws BrokerException
     */
    public long getAccessedTime( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        long accessedTime = -1;
        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAccessedTimeSQL );
            pstmt.setLong( 1, id );

            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                accessedTime = rs.getLong( 1 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                    String.valueOf( id ) ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectAccessedTimeSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return accessedTime;
    }

    /**
     * Retrieve all local and cluster transaction IDs owned by a broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a List of TransactionUID objects; an empty List is returned
     * if the broker does not have any transactions
     */
    public List getTransactionsByBroker( Connection conn, String brokerID )
        throws BrokerException {

        List list = new ArrayList();

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

            // Uses the same SQL to retrieve txn states
            // but just fetchs the IDs only
            pstmt = dbMgr.createPreparedStatement( conn, selectTxnStatesByBrokerSQL );
            pstmt.setString( 1, brokerID );

            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                list.add( new TransactionUID( rs.getLong( 1 ) ) );
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
                ex = DBManager.wrapSQLException("[" + selectTxnStatesByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TXNS_FOR_BROKER_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return list;
    }

    /**
     * Retrieve all remote transaction IDs that this broker participates in.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a List of TransactionUID objects; an empty List is returned
     * if the broker does not have any transactions
     */
    public List getRemoteTransactionsByBroker( Connection conn, String brokerID )
        throws BrokerException {

        List list = new ArrayList();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }
            if ( Globals.getHAEnabled() ) {
                sql = selectRemoteTxnStatesByBrokerAndTypeSQL;
            } else {
                sql = selectTxnStatesByBrokerAndTypeSQL;
            }      

            // If broker is running in non-HA mode, then just select the row
            // based on the type. Otherwise, a round about way is required
            // to retrieve the txns that the broker participates in.

            // Uses the same SQL to retrieve remote txn states
            // but just fetchs the IDs only

            if ( Globals.getHAEnabled() ) {
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setInt( 1, TransactionInfo.TXN_CLUSTER );
                pstmt.setString( 2, brokerID );
                pstmt.setString( 3, brokerID );
                if (DEBUG) {
                    logger.log(logger.INFO,
                    "TransactionDAOImpl.getRemoteTransactionStatesByBroker(): ["+
                    sql+"]("+TransactionInfo.TXN_CLUSTER+","+brokerID+","+brokerID+")"); 
                }
            } else {
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, brokerID );
                pstmt.setInt( 2, TransactionInfo.TXN_REMOTE );
                if (DEBUG) {
                    logger.log(logger.INFO, 
                    "TransactionDAOImpl.getRemoteTransactionStatesByBroker(): ["+
                    sql+"]("+brokerID+","+TransactionInfo.TXN_REMOTE+")"); 
                }
            }

            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                long id = rs.getLong( 1 );
                list.add( new TransactionUID( id ) );
                if (DEBUG) {
                    logger.log(logger.INFO, 
                    "TransactionDAOImpl.getRemoteTransactionStatesByBroker(): Result["+id+"]");
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
                ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TXNS_FOR_BROKER_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        if (DEBUG) {
            logger.log(logger.INFO, 
            "TransactionDAOImpl.getRemoteTransactionStatesByBroker(): ResultSet[size="+list.size()+"]");
        }
        return list;
    }

    /**
     * Retrieve all local and cluster transaction states.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a Map of transaction IDs and TransactionState objects;
     *  an empty Map is returned if no transactions exist in the store
     */
    public HashMap getTransactionStatesByBroker( Connection conn, String brokerID, Long storeSession )
        throws BrokerException {

        HashMap map = new HashMap();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectTxnStatesByBrokerSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            if ( brokerID == null ) {
                brokerID = dbMgr.getBrokerID();
            }

            if (storeSession != null) {
                StoreSessionDAOImpl.checkStoreSessionOwner( conn, storeSession, brokerID ); 
                sql = selectTxnStatesBySessionSQL;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            if (storeSession != null) {
                pstmt.setLong( 1, storeSession.longValue() );
            } else {
                pstmt.setString( 1, brokerID );
            }
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                try {
                    long id = rs.getLong( 1 );
                    int type = rs.getInt( 2 );
                    int state = rs.getInt( 3 );

                    TransactionState txnState =
                        (TransactionState)Util.readObject( rs, 4 );
                    txnState.setState( state );

                    TransactionBroker[] txnBrokers =
                        (TransactionBroker[])Util.readObject( rs, 5 );
                    
                    map.put( new TransactionUID( id ), 
                             new TransactionInfo(txnState, null, txnBrokers, type) );
                } catch ( IOException e ) {
                    // fail to parse TransactionState object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_TRANSACTION_FAILED, e );
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
                ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTIONS_FAILED ), ex );
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
     * Retrieve all remote transaction states that this broker participates in.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a Map of transaction IDs and TransactionState objects;
     *  an empty Map is returned if no transactions exist in the store
     */
    public HashMap getRemoteTransactionStatesByBroker( Connection conn, String brokerID, Long storeSession )
        throws BrokerException {

        HashMap map = new HashMap();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            if ( brokerID == null ) {
                brokerID = dbMgr.getBrokerID();
            }

            if ( Globals.getHAEnabled() ) {
                sql = selectRemoteTxnStatesByBrokerAndTypeSQL;
            } else {
                sql = selectTxnStatesByBrokerAndTypeSQL;
            }

            if (storeSession != null) {
                StoreSessionDAOImpl.checkStoreSessionOwner( conn, storeSession, brokerID );
                if ( Globals.getHAEnabled() ) {
                    sql = selectRemoteTxnStatesBySessionAndTypeSQL;
                } else {
                    sql = selectTxnStatesBySessionAndTypeSQL;
                }      
            }

            // If broker is running in non-HA mode, then just select the row
            // based on the type. Otherwise, a round about way is required
            // to retrieve the txns that the broker participates in.

            if ( Globals.getHAEnabled() ) {
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setInt( 1, TransactionInfo.TXN_CLUSTER );
                if (storeSession != null) {
                    pstmt.setLong( 2, storeSession.longValue() );
                    pstmt.setLong( 3, storeSession.longValue() );
                } else {
                    pstmt.setString( 2, brokerID );
                    pstmt.setString( 3, brokerID );
                }
            } else {
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                if (storeSession != null) {
                    pstmt.setLong( 1, storeSession.longValue() );
                } else {
                    pstmt.setString( 1, brokerID );
                }
                pstmt.setInt( 2, TransactionInfo.TXN_REMOTE );
            }

            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                try {
                    long id = rs.getLong( 1 );
                    int state = rs.getInt( 2 );
                    TransactionState txnState =
                        (TransactionState)Util.readObject( rs, 3 );

                    // update state in TransactionState object
                    txnState.setState( state );

                    map.put( new TransactionUID( id ), txnState );
                } catch ( IOException e ) {
                    // fail to parse TransactionState object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_TRANSACTION_FAILED, e );
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
                ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTIONS_FAILED ), ex );
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
     * Return the number of messages and the number of consumer states that
     * that associate with the specified transaction ID.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return an array of int whose first element contains the number of messages
     * and the second element contains the number of consumer states.
     */
    public int[] getTransactionUsageInfo( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        int[] data = { 0, 0 };

        long id = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectUsageInfoSQL );
            pstmt.setLong( 1, id );
            pstmt.setLong( 2, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                data[0] = rs.getInt( 1 );   // # of messages
                data[1] = rs.getInt( 2 );   // # of consumer states
            }
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage()+"["+selectUsageInfoSQL+"]");
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
                ex = DBManager.wrapSQLException("[" + selectUsageInfoSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED,
                    txnUID ), ex );
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
     * Check whether the specified transaction exists.
     * @param conn database connection
     * @param id transaction ID
     * @return return true if the specified transaction exists
     */
    public boolean hasTransaction( Connection conn, long id )
        throws BrokerException {

        boolean found = false;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAccessedTimeSQL );
            pstmt.setLong( 1, id );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                found = true;
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
                ex = DBManager.wrapSQLException("[" + selectAccessedTimeSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TRANSACTION_FAILED, 
                    String.valueOf(id) ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return found;
    }

    /**
     * Check whether the specified transaction exists.
     * @param conn database connection
     * @param id transaction ID
     * @throws BrokerException if the transaction does not exists in the store
     */
    public void checkTransaction( Connection conn, long id ) throws BrokerException {

        if ( !hasTransaction( conn, id ) ) {
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"[hasTransaction():"+id+"]", rbe );
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_TRANSACTIONID_NOT_FOUND_IN_STORE,
                String.valueOf( id ) ), Status.NOT_FOUND );
        }
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new HashMap();
        int count = -1;

        try {
            // Get row count
            count = getRowCount( null, null );
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage(), e.getCause() );
        }

        map.put( "Transactions(" + tableName + ")", String.valueOf( count ) );
        return map;
    }
}
