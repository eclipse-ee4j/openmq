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
 * @(#)ConsumerStateDAOImpl.java	1.36 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.FaultInjection;

import java.util.*;
import java.sql.*;
import java.io.*;

/**
 *  This class implement a generic ConsumerStateDAO.
 */
class ConsumerStateDAOImpl extends BaseDAOImpl implements ConsumerStateDAO {

    private static boolean DEBUG = false;
    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String updateTransactionSQL;
    private final String updateTransactionNoCheckSQL; 
    protected final String updateStateSQL;
    private final String updateState2SQL;
    private final String clearTxnSQL;
    private final String deleteByTxnSQL;
    private final String deleteByDstSQL;
    private final String deleteByDstBySessionSQL;
    private final String deleteByMsgSQL;
    private final String selectStateSQL;
    private final String selectStatesByMsgSQL;
    private final String selectTransactionSQL;
    private final String selectCountByMsgSQL;
    private final String selectConsumerIDsByMsgSQL;
    private final String selectTransactionAcksSQL;
    private final String selectAllTransactionAcksSQL;

    private static FaultInjection FI = FaultInjection.getInjection();

    /**
     * Constructor
     * @throws BrokerException
     */
    ConsumerStateDAOImpl() throws BrokerException {

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
            .append( MESSAGE_ID_COLUMN ).append( ", " )
            .append( CONSUMER_ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ? )" )
            .toString();

        updateTransactionSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TRANSACTION_ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TRANSACTION_ID_COLUMN ).append( " IS NULL" )
            .append( brokerNotTakenOverClause )
            .toString();

        updateTransactionNoCheckSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TRANSACTION_ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( TRANSACTION_ID_COLUMN ).append( " <> ?" )
            .append( brokerNotTakenOverClause )
            .toString();

        updateStateSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .toString();

        updateState2SQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( STATE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " = ?" )
            .toString();

        clearTxnSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( TRANSACTION_ID_COLUMN ).append( " = NULL" )
            .append( " WHERE " )
            .append( TRANSACTION_ID_COLUMN ).append( " = ?" )
            .append( brokerNotTakenOverClause )
            .toString();

        deleteByTxnSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( TRANSACTION_ID_COLUMN ).append( " = ?" )
            .toString();

        deleteByDstSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " IN " )
            .append( "(SELECT msgTbl." ).append( MessageDAO.ID_COLUMN )
            .append( " FROM " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( MessageDAO.STORE_SESSION_ID_COLUMN )
            .append( " AND " )
            .append(   MessageDAO.DESTINATION_ID_COLUMN ).append( " = ?)")
            .toString();

        deleteByDstBySessionSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " IN " )
            .append( "(SELECT msgTbl." ).append( MessageDAO.ID_COLUMN )
            .append( " FROM " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( MessageDAO.STORE_SESSION_ID_COLUMN )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append(   MessageDAO.DESTINATION_ID_COLUMN ).append( " = ?)")
            .toString();

        deleteByMsgSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .toString();

        selectStateSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .toString();

        selectStatesByMsgSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_ID_COLUMN ).append( ", " )
            .append( STATE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .toString();

        selectTransactionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TRANSACTION_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CONSUMER_ID_COLUMN ).append( " = ?" )
            .toString();

        selectCountByMsgSQL = new StringBuffer(128)
            .append( "SELECT COUNT(*) FROM " )
            .append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ?" )
            .toString();

        selectConsumerIDsByMsgSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( MESSAGE_ID_COLUMN ).append( " = ? " )
            .append( " AND " )
            .append( STATE_COLUMN ).append( " <> " )
            .append( PartitionedStore.INTEREST_STATE_ACKNOWLEDGED )
            .toString();

        selectTransactionAcksSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_ID_COLUMN ).append( ", " )
            .append( MESSAGE_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( TRANSACTION_ID_COLUMN ).append( " = ?" )
            .toString();

        selectAllTransactionAcksSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TRANSACTION_ID_COLUMN ).append( ", " )
            .append( CONSUMER_ID_COLUMN ).append( ", " )
            .append( MESSAGE_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( TRANSACTION_ID_COLUMN ).append( " IS NOT NULL" )
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
     * @param dstID the destination ID
     * @param sysMsgID the system message ID
     * @param conUIDs an array of consumer ids
     * @param states an array of states
     * @throws BrokerException
     */
    public void insert( Connection conn, String dstID, SysMessageID sysMsgID,
        ConsumerUID[] conUIDs, int[] states, boolean checkMsgExist, boolean replaycheck )
        throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

        int count = 0;
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

            // No need to check for message existence
            if ( checkMsgExist ) {
                int cnt = -1;
                try {
                    cnt = getConsumerCount( conn, msgID );
                } catch (BrokerException e) {
                    e.setSQLRecoverable(true);
                    e.setSQLReplayCheck(replaycheck);
                    throw e;
                }
                if ( cnt > 0 && replaycheck) {
                    if ( conUIDs != null ) {
			HashMap map = null;
			try {
                            map = getStates( conn, sysMsgID );
			} catch (BrokerException e) {
                            e.setSQLRecoverable(true);
                            e.setSQLReplayCheck(replaycheck);
                            throw e;
			}
                        List cids = Arrays.asList(conUIDs);
                        Iterator itr = map.entrySet().iterator();
                        Map.Entry pair = null;
                        ConsumerUID cid = null;
                        while (itr.hasNext()) {
                            pair = (Map.Entry)itr.next();
                            cid = (ConsumerUID)pair.getKey();
                            int st = ((Integer)pair.getValue()).intValue();
                            for (int i = 0; i < conUIDs.length; i++) {
				if (conUIDs[i].equals(cid)) {
                                    if (states[i] == st) {
                                        cids.remove(conUIDs[i]);
                                    }
                                }
                            }
                        }
                        if (cids.size() == 0) {
                            logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY, msgID+"["+dstID+"]"+cids);
                            return;
                        }
                    }
                }
                if ( cnt > 0 ) {
                    // the message has a list already
                    throw new BrokerException(
                        br.getKString( BrokerResources.E_MSG_INTEREST_LIST_EXISTS, msgID ) );
                }

                try {
                    dbMgr.getDAOFactory().getMessageDAO().checkMessage( conn, dstID, msgID );
                } catch (BrokerException e) {
                    if (e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                    }
                    throw e;
                }
            }

            boolean dobatch = dbMgr.supportsBatchUpdates();
            pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
            for ( int len = conUIDs.length; count < len; count++ ) {
                pstmt.setString( 1, msgID );
                pstmt.setLong( 2, conUIDs[count].longValue() );
                pstmt.setInt( 3, states[count] );
                pstmt.setLong( 4, System.currentTimeMillis() );

                if ( dobatch ) {
                    pstmt.addBatch();
                } else {
                    pstmt.executeUpdate();
                }
            }

            if ( dobatch ) {
                pstmt.executeBatch();
            }

            if ( myConn ) {
                conn.commit();
            }
        } catch ( Exception e ) {
            myex = e;
            if (DEBUG && count < conUIDs.length ) {
                logger.log( Logger.INFO, "Failed to persist interest: "
                    + conUIDs[count].toString()
                    + "("+conUIDs[count].getUniqueName() + ")");
            }
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
                ex = DBManager.wrapIOException("[" + insertSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_LIST_FAILED,
                msgID ), ex );
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
     * Update existing entry.
     * @param conn database connection
     * @param dstUID the destination ID
     * @param sysMsgID the system message ID
     * @param conUID the consumer id
     * @param state the state
     * @throws BrokerException
     */
    public void updateState( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID, ConsumerUID conUID, int state, boolean replaycheck )
        throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

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

            // Get the broker ID of the msg which also checks if the msg exists
            String brokerID = null;
            if (Globals.getHAEnabled()) {
                try {
                    brokerID = dbMgr.getDAOFactory().getMessageDAO().getBroker( conn, dstUID, msgID );
                } catch (BrokerException e) {
                    if (e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                        e.setSQLReplayCheck(replaycheck);
                    }
                    throw e;
                }
            } else {
                brokerID = dbMgr.getBrokerID();
            }
            if (replaycheck) {
                try {
                    int currstate = getState( conn, sysMsgID, conUID);
                    if (currstate == state) {
                        logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY,
                                   msgID+"["+dstUID+"]"+conUID+":"+state);
                        return;
                    }
                } catch (BrokerException e) {
                    if (e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                        e.setSQLReplayCheck(true);
                    }
                    throw e;
                }
            }

            // For HA, state can only be udpated by the broker that owns the msg
            if ( Globals.getHAEnabled() && !dbMgr.getBrokerID().equals( brokerID ) ) {
                 String emsg = br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                                              conUID.toString(), sysMsgID.toString() );
                 BrokerException be = new StoreBeingTakenOverException(
                            br.getKString(BrokerResources.E_STORE_BEING_TAKEN_OVER+"["+emsg+"]") );
                 try {
                     BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                     HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, dbMgr.getBrokerID() );
                     logger.logStack( Logger.ERROR, be.getMessage()+"["+
                         (bkrInfo == null ? ""+dbMgr.getBrokerID():bkrInfo.toString())+"]", be );
                 } catch (Throwable t) { /* Ignore error */ }
                 throw be;
            }

            pstmt = dbMgr.createPreparedStatement( conn, updateStateSQL );
            pstmt.setInt( 1, state );
            pstmt.setString( 2, msgID );
            pstmt.setLong( 3, conUID.longValue() );

            if ( pstmt.executeUpdate() == 0 ) {
                // Otherwise we're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), msgID ), Status.NOT_FOUND );
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
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                conUID.toString(), sysMsgID.toString() ), ex );
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
     * Update existing entry.
     * @param conn database connection
     * @param dstUID the destination ID
     * @param sysMsgID the system message ID
     * @param conUID the consumer id
     * @param newState the new state
     * @param expectedState the expected state
     * @throws BrokerException
     */
    public void updateState( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID, ConsumerUID conUID, int newState,
        int expectedState ) throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

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

            // Since this method is used to update the state of a consumer
            // during a takeover, we will skip checking if the msg exists
            // to improve performance.            

            pstmt = dbMgr.createPreparedStatement( conn, updateState2SQL );
            pstmt.setInt( 1, newState );
            pstmt.setString( 2, msgID );
            pstmt.setLong( 3, conUID.longValue() );
            pstmt.setInt( 4, expectedState );

            if ( pstmt.executeUpdate() == 0 ) {
                // Verify if record is not updated because state doesn't match
                int currentState = getState( conn, sysMsgID, conUID );
                if ( currentState != expectedState ) {
                    String[] args = { conUID.toString(),
                                      sysMsgID.toString(),
                                      String.valueOf(expectedState),
                                      String.valueOf(currentState) };
                    throw new BrokerException(
                        br.getKString( BrokerResources.E_PERSIST_INTEREST_STATE_FAILED,
                        args ), Status.PRECONDITION_FAILED );
                }

                // Otherwise we're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), msgID ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + updateState2SQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                conUID.toString(), sysMsgID.toString() ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Update existing entry.
     * @param conn database connection
     * @param sysMsgID the system message ID
     * @param conUID the consumer id
     * @param txnUID the transaction id associated with an acknowledgment
     * @throws BrokerException
     */
    public void updateTransaction( Connection conn, SysMessageID sysMsgID,
        ConsumerUID conUID, TransactionUID txnUID ) throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        String sql = updateTransactionSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            // Make sure the transaction exists
            dbMgr.getDAOFactory().getTransactionDAO().checkTransaction(
                conn, txnUID.longValue() );

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setLong( 1, txnUID.longValue() );
            pstmt.setString( 2, msgID );
            pstmt.setLong( 3, conUID.longValue() );

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
                                (bkrInfo == null? ""+brokerID:bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }
                }

                // Check if ack exists
                long existingTxnId = getTransaction( conn, sysMsgID, conUID );
                if ( existingTxnId > 0 ) {
                     TransactionUID oldTxnUID = new TransactionUID(existingTxnId);
                     TransactionState ts = dbMgr.getDAOFactory().getTransactionDAO().
                                           getTransactionState( conn, oldTxnUID );
                      String[] args = { "["+sysMsgID+ ", "+conUID+"]", 
                                         oldTxnUID+"["+ts+"]", txnUID.toString() };
                      logger.log( logger.WARNING,
                          br.getKString( BrokerResources.W_STORE_TXN_ACK_EXIST, args));

                      //message can be redelivered but rollback transaction failed
                      updateTransactionNoCheck( conn, sysMsgID, conUID, txnUID );
                      return;
                }

                // We're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), msgID ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR,
                    BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
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
                br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                conUID.toString(), sysMsgID.toString() ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    private void updateTransactionNoCheck( Connection conn, SysMessageID sysMsgID,
        ConsumerUID conUID, TransactionUID txnUID ) throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        String sql = updateTransactionNoCheckSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            // Make sure the transaction exists
            dbMgr.getDAOFactory().getTransactionDAO().checkTransaction(
                conn, txnUID.longValue() );

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setLong( 1, txnUID.longValue() );
            pstmt.setString( 2, msgID );
            pstmt.setLong( 3, conUID.longValue() );
            pstmt.setLong( 4, txnUID.longValue() );

            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 5, dbMgr.getBrokerID() );
            }

            if ( pstmt.executeUpdate() == 0 ) {
                if ( Globals.getHAEnabled() ) {
                    String brokerID = dbMgr.getBrokerID();
                    BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                    if ( dao.isBeingTakenOver( conn, brokerID ) ) {
                        BrokerException be = new StoreBeingTakenOverException(
                            br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );

                        try {
                            HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, brokerID );
                            logger.logStack( Logger.ERROR, be.getMessage()+"["+
                                (bkrInfo == null? ""+brokerID:bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }
                }
                long existingTxnId = getTransaction( conn, sysMsgID, conUID );
                if ( existingTxnId > 0 ) {
                     TransactionUID oldTxnUID = new TransactionUID(existingTxnId);
                     TransactionState ts = dbMgr.getDAOFactory().getTransactionDAO().
                                           getTransactionState( conn, oldTxnUID );
                     String[] args = { "["+sysMsgID+ ", "+conUID+"]TID="+
                                        oldTxnUID+"("+ts+")", txnUID.toString()  };
                     throw new BrokerException(
                         br.getKString(br.E_ACK_EXISTS_IN_STORE, args));
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), msgID ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, 
                    BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" +sql+ "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                conUID.toString(), sysMsgID.toString() ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    int faultCount = 0;

    /**
     * Clear the transaction from all consumer states associated with it.
     * @param conn Database Connection
     * @param txnUID the transaction
     * @throws BrokerException
     */
    public void clearTransaction( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        long txnID = txnUID.longValue();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            if (FI.FAULT_INJECTION) {
                HashMap m = new HashMap();
		m.put(FaultInjection.FAULT_COUNT_PROP, String.valueOf(faultCount));
                if (FI.checkFault(FI.FAULT_TXN_ROLLBACK_DBCONSTATE_CLEARTID, m)) {
                    faultCount++;
                    throw new BrokerException("FAULT INJECTION:"+
                        FI.FAULT_TXN_ROLLBACK_DBCONSTATE_CLEARTID);
                }
            }
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, clearTxnSQL );
            pstmt.setLong( 1, txnID );

            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 2, dbMgr.getBrokerID() );
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
                            logger.logStack( Logger.ERROR,  
                                be.getMessage()+": "+ bkrInfo.toString(), be );
                            be.setStackLogged();
                         
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
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
                ex = DBManager.wrapSQLException("[" + clearTxnSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_CLEAR_TXN_FROM_INT_STATES_FAILED,
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
     * Delete all consumer states for a message.
     * @param conn Database Connection
     * @param sysMsgID the SysMessageID
     * @throws BrokerException
     */
    public void deleteByMessageID( Connection conn, String msgID )
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

            pstmt = dbMgr.createPreparedStatement( conn, deleteByMsgSQL );
            pstmt.setString( 1, msgID );
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
                ex = DBManager.wrapSQLException("[" + deleteByMsgSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_INTEREST_STATE_FAILED,
                msgID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete all consumer states for a transaction.
     * @param conn Database Connection
     * @param txnUID the transaction
     * @throws BrokerException
     */
    public void deleteByTransaction( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        long txnID = txnUID.longValue();

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

            pstmt = dbMgr.createPreparedStatement( conn, deleteByTxnSQL );
            pstmt.setLong( 1, txnID );
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
                ex = DBManager.wrapSQLException("[" + deleteByTxnSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_INT_STATES_FOR_TXN_FAILED,
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
     * Delete all consumer states for a destination.
     * Note: Because Consumer State table is a child table to the Message table,
     * this method will be call from MessageDAO.deleteByDestinationBySession() to delete
     * all consumer states before it deletes the messages.
     * @param conn Database Connection
     * @param dstUID the destination
     * @param storeSession null if delete all consumer state for a destination
     * @throws BrokerException
     */
    public void deleteByDestinationBySession( Connection conn, 
        DestinationUID dstUID, Long storeSession )
        throws BrokerException {

        String dstID = dstUID.toString();

        String sql = deleteByDstSQL;
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

            if (storeSession != null) {
                sql = deleteByDstBySessionSQL; 
            }
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, dbMgr.getBrokerID() );
            if (storeSession != null) {
                pstmt.setLong( 2, storeSession.longValue() );
                pstmt.setString( 3, dstID );
            } else {
                pstmt.setString( 2, dstID );
            }
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, 
                    BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
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
                br.getKString( BrokerResources.X_REMOVE_INT_STATES_FOR_DST_FAILED,
                    dstID ), ex );
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
     * @param conn Database Connection
     * @throws BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        String whereClause = null;
        if ( Globals.getHAEnabled() ) {
            // Only delete consumer states that belong to the running broker,
            // construct the where clause for the delete statement:
            //   DELETE FROM mqconstate41cmycluster WHERE message_id IN
            //    (SELECT id FROM mqmsg41cmycluster msgtbl,
            //                    mqses41cmycluster sestbl
            //     WHERE sestbl.broker_id = 'mybroker' AND
            //           sestbl.id = msgtbl.store_session_id)
            DBManager dbMgr = DBManager.getDBManager();
            whereClause = new StringBuffer(128)
                .append( MESSAGE_ID_COLUMN )
                .append( " IN (SELECT msgTbl." ).append( MessageDAO.ID_COLUMN )
                .append(   " FROM " )
                .append(     dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
                .append(     " msgTbl, " )
                .append(     dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
                .append(     " sesTbl" )
                .append(   " WHERE sesTbl." ).append( StoreSessionDAO.BROKER_ID_COLUMN )
                .append(     " = '" ).append( dbMgr.getBrokerID() )
                .append(     "' AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
                .append(     " = msgTbl." ).append( MessageDAO.STORE_SESSION_ID_COLUMN )
                .append( ")" )
                .toString();
        }

        deleteAll( conn, whereClause, null, 0 );        
    }

    /**
     * Get consumer's state.
     * @param conn database connection
     * @param sysMsgID the system message ID
     * @param conUID the consumer ID
     * @return consumer's state
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public int getState( Connection conn, SysMessageID sysMsgID,
        ConsumerUID conUID ) throws BrokerException {

        int state = -1;
        String id = sysMsgID.getUniqueName();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectStateSQL );
            pstmt.setString( 1, id );
            pstmt.setLong( 2, conUID.longValue() );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                state = rs.getInt( 1 );
            } else {
                // We are assuming the consumer state does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), id ), Status.NOT_FOUND );
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

            throw new BrokerException(
                br.getKString( BrokerResources.X_GET_INTEREST_STATE_FAILED,
                conUID.toString(), id ), ex );
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
     * Get all consumers and states associated with the messapge ID.
     * @param conn database connection
     * @return HashMap of containing all consumer's state
     * @throws BrokerException
     */
    public HashMap getStates( Connection conn, SysMessageID sysMsgID )
        throws BrokerException {

        HashMap map = new HashMap();
        String id = sysMsgID.getUniqueName();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectStatesByMsgSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                ConsumerUID cUID = new ConsumerUID( rs.getLong( 1 ) );
                int state = rs.getInt( 2 );
                map.put( cUID, Integer.valueOf( state ) );
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
                ex = DBManager.wrapSQLException("[" + selectStatesByMsgSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_LOAD_INT_STATES_FOR_MSG_FAILED, id ), ex );
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
     * Get consumer's transaction.
     * @param conn database connection
     * @param sysMsgID the system message ID
     * @param conUID the consumer ID
     * @return consumer's transaction
     */
    public long getTransaction( Connection conn, SysMessageID sysMsgID,
        ConsumerUID conUID ) throws BrokerException {

        long txnID = -1;
        String id = sysMsgID.getUniqueName();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTransactionSQL );
            pstmt.setString( 1, id );
            pstmt.setLong( 2, conUID.longValue() );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                txnID = rs.getLong( 1 );
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
                ex = DBManager.wrapSQLException("[" + selectTransactionSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_RETRIEVE_INTEREST_FAILED,
                conUID.toString() ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return txnID;
    }

    /**
     * Get all consumers associated with the messapge ID and state is not
     * INTEREST_STATE_ACKNOWLEDGED.
     * @param conn database connection
     * @param sysMsgID the system message ID
     * @return list of consumer IDs
     * @throws BrokerException
     */
    public List getConsumerUIDs( Connection conn, SysMessageID sysMsgID )
        throws BrokerException {

        List list = new ArrayList();
        String id = sysMsgID.getUniqueName();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectConsumerIDsByMsgSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                ConsumerUID cUID = new ConsumerUID( rs.getLong( 1 ) );
                list.add( cUID );
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
                ex = DBManager.wrapSQLException("[" + selectConsumerIDsByMsgSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_LOAD_INT_STATES_FOR_MSG_FAILED, id ), ex );
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
     * Retrieve all transaction acknowledgements for the specified transaction ID.
     * @param conn database connection
     * @param txnUID the transaction ID
     * @return List of transaction acks
     * @throws BrokerException
     */
    public List getTransactionAcks( Connection conn, TransactionUID txnUID )
        throws BrokerException {

        List data = new ArrayList();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectTransactionAcksSQL );
            pstmt.setLong( 1, txnUID.longValue() );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                ConsumerUID conID = new ConsumerUID( rs.getLong( 1 ) );
                try {
                    SysMessageID msgID = SysMessageID.get( rs.getString( 2 ) );
                    data.add( new TransactionAcknowledgement( msgID, conID, conID ) );
                } catch ( Exception e ) {
                    // fail to parse one object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_TXNACK_FAILED, txnUID, e );
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
                ex = DBManager.wrapSQLException("[" + selectTransactionAcksSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_ACKS_FOR_TXN_FAILED,
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
     * Retrieve all transaction acknowledgements for all transactions.
     * @param conn database connection
     * @return HashMap of containing all acknowledgement
     * @throws BrokerException
     */
    public HashMap getAllTransactionAcks( Connection conn )
        throws BrokerException {

        HashMap data = new HashMap(100);

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAllTransactionAcksSQL );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                TransactionUID txnUID = new TransactionUID( rs.getLong( 1 ) );
                ConsumerUID conID = new ConsumerUID( rs.getLong( 2 ) );
                try {
                    SysMessageID msgID = SysMessageID.get( rs.getString( 3 ) );

                    List ackList = (List)data.get( txnUID );
                    if ( ackList == null ) {
                        // Create a new list of acks for this txn
                        ackList = new ArrayList(25);
                        data.put( txnUID, ackList );
                    }

                    // Added ack to the list of acks
                    ackList.add( new TransactionAcknowledgement( msgID, conID, conID ) );
                } catch ( Exception e ) {
                    // fail to parse one object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_TXNACK_FAILED, txnUID, e );
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
                ex = DBManager.wrapSQLException("[" + selectAllTransactionAcksSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_TXNACK_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        // Transforms HashMap value to TransactionAcknowledgement[] instead of List
        Set keySet = data.keySet();
        if ( !keySet.isEmpty() ) {
            Iterator itr = keySet.iterator();
            while ( itr.hasNext() ) {
                TransactionUID txnUID = (TransactionUID)itr.next();
                List ackList = (List)data.get( txnUID );
                data.put( txnUID, ackList.toArray(
                    new TransactionAcknowledgement[ackList.size()]) );
            }
        }

        return data;
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return A HashMap of name value pair of information
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

        map.put( "Message/Consumer states(" + tableName + ")", String.valueOf( count ) );
        return map;
    }

    /**
     * Get the number of consumers (e.g. interest) for the specified message.
     * @param conn database connection
     * @param msgID the message ID
     * @return number of consumers
     * @throws BrokerException
     */
    public int getConsumerCount( Connection conn, String msgID )
        throws BrokerException {

        int count = -1;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectCountByMsgSQL );
            pstmt.setString( 1, msgID );
            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                count = rs.getInt( 1 );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCountByMsgSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectCountByMsgSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_INT_STATES_FOR_MSG_FAILED,
                msgID ), ex );

        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return count;
    }
}
