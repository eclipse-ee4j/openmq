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
 * @(#)MessageDAOImpl.java	1.55 08/17/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.FaultInjection; 
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.InvalidPacketException;
import com.sun.messaging.jmq.io.PacketReadEOFException;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.util.SupportUtil;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;
import java.io.*;

/**
 * This class implement a generic MessageDAO.
 */
class MessageDAOImpl extends BaseDAOImpl implements MessageDAO {

    protected final String tableName;
    protected static int msgColumnType = -Integer.MAX_VALUE;

    protected String insertSQL;
    protected String updateDestinationSQL;
    private final String repairSysMessageIDSQL;
    private final String deleteSQL;
    private final String deleteByDstSQL;
    private final String deleteByDstBySessionSQL;
    private final String selectSQL;
    protected String selectMsgsBySessionSQL;
    private final String selectMsgsByBrokerSQL;
    private final String selectMsgIDsAndDstIDsByBrokerSQL;
    protected String selectForUpdateSQL;
    private final String selectBrokerSQL;
    private final String selectCountByDstBrokerSQL;
    private final String selectCountByDstSessionSQL;
    private final String selectCountByBrokerSQL;
    private final String selectCountByConsumerAckedSQL;
    private final String selectIDsByDstBrokerSQL;
    private final String selectIDsByDstSessionSQL;
    private final String selectMsgsByDstBrokerSQL;
    private final String selectMsgsByDstSessionSQL;
    private final String selectExistSQL;
    private final String selectCanInsertSQL;

    protected FaultInjection fi = null;

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    MessageDAOImpl() throws BrokerException {
        fi = FaultInjection.getInjection();

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( MESSAGE_SIZE_COLUMN ).append( ", " )
            .append( STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( DESTINATION_ID_COLUMN ).append( ", " )
            .append( TRANSACTION_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append( ", " )
            .append( MESSAGE_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, ? )" )
            .toString();

        updateDestinationSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?, " )
            .append( MESSAGE_SIZE_COLUMN ).append( " = ?, " )
            .append( MESSAGE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        repairSysMessageIDSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        deleteByDstSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STORE_SESSION_ID_COLUMN )
            .append( " IN (SELECT " ).append( StoreSessionDAO.ID_COLUMN )
            .append( " FROM " )
            .append( dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append( " WHERE " )
            .append( StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?)" )
            .toString();

        deleteByDstBySessionSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( STORE_SESSION_ID_COLUMN )
            .append( " IN (SELECT " ).append( StoreSessionDAO.ID_COLUMN )
            .append( " FROM " )
            .append( dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append( " WHERE " )
            .append( StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ? " )
            .append( " AND " ).append( StoreSessionDAO.ID_COLUMN ).append ( " = ?)")
            .toString();

        selectCountByBrokerSQL = new StringBuffer(128)
            .append( "SELECT COUNT(*) FROM " )
            .append(   tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append( StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .toString();

        // This query is a bit more complex because we want the SQL to count
        // the # of msgs, calculate the total size, and also determine if the
        // destination does exists
        selectCountByDstBrokerSQL = new StringBuffer(128)
            .append( "SELECT totalmsg, totalsize, " )
            .append( DestinationDAO.ID_COLUMN )
            .append( " FROM " )
            .append( dbMgr.getTableName( DestinationDAO.TABLE_NAME_PREFIX  ) )
            .append( ", (SELECT COUNT(msgTbl." )
            .append(     ID_COLUMN ).append( ") AS totalmsg, SUM(" )
            .append(     MESSAGE_SIZE_COLUMN ).append( ") AS totalsize")
            .append(   " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(     dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(     " sesTbl" )
            .append(   " WHERE sesTbl." )
            .append(     StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append(   " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(     " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append(   " AND " )
            .append(     DESTINATION_ID_COLUMN ).append( " = ?) msgtable" )
            .append( " WHERE " )
            .append( DestinationDAO.ID_COLUMN ).append( " = ?" )
            .toString();

        selectCountByDstSessionSQL = new StringBuffer(128)
            .append( "SELECT totalmsg, totalsize, " )
            .append( DestinationDAO.ID_COLUMN )
            .append( " FROM " )
            .append( dbMgr.getTableName( DestinationDAO.TABLE_NAME_PREFIX  ) )
            .append( ", (SELECT COUNT(" )
            .append(     ID_COLUMN ).append( ") AS totalmsg, SUM(" )
            .append(     MESSAGE_SIZE_COLUMN ).append( ") AS totalsize")
            .append(   " FROM " ).append( tableName )
            .append(   " WHERE ").append( STORE_SESSION_ID_COLUMN ).append( "= ?" )
            .append(   " AND " )
            .append(     DESTINATION_ID_COLUMN ).append( " = ?) msgtable" )
            .append( " WHERE " )
            .append( DestinationDAO.ID_COLUMN ).append( " = ?" )
            .toString();

        selectCountByConsumerAckedSQL = new StringBuffer(128)
            .append( "SELECT COUNT(*) AS total, SUM(CASE WHEN " )
            .append( ConsumerStateDAO.STATE_COLUMN )
            .append( " = " ).append( PartitionedStore.INTEREST_STATE_ACKNOWLEDGED )
            .append( " THEN 1 ELSE 0 END) AS totalAcked" )
            .append( " FROM " )
            .append( dbMgr.getTableName( ConsumerStateDAO.TABLE_NAME_PREFIX ) )
            .append( " WHERE " )
            .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( " = ?" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( MESSAGE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectMsgsBySessionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( MESSAGE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( STORE_SESSION_ID_COLUMN ).append( " = ?" )
            .toString();

        selectMsgsByBrokerSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( MESSAGE_COLUMN )
            .append( " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .toString();

        selectMsgIDsAndDstIDsByBrokerSQL = new StringBuffer(128)
            .append( "SELECT msgTbl." )
            .append( ID_COLUMN ).append( ", msgTbl." )
            .append( DESTINATION_ID_COLUMN )
            .append( " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .toString();

        selectForUpdateSQL = new StringBuffer(128)
            .append( selectSQL )
            .append( " FOR UPDATE" )
            .toString();

        selectBrokerSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( StoreSessionDAO.BROKER_ID_COLUMN )
            .append( " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE msgTbl." )
            .append(   ID_COLUMN ).append( " = ?" )
            .append( " AND msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append(   " = sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .toString();

        selectIDsByDstBrokerSQL = new StringBuffer(128)
            .append( "SELECT msgTbl." )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append( " AND " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .toString();

        selectIDsByDstSessionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE STORE_SESSION_ID_COLUMN = ?" )
            .append( " AND " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .toString();


        selectMsgsByDstBrokerSQL = new StringBuffer(128)
            .append( "SELECT msgTbl." )
            .append( MESSAGE_COLUMN )
            .append( " FROM " ).append( tableName ).append( " msgTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl" )
            .append( " WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append( " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = msgTbl." ).append( STORE_SESSION_ID_COLUMN )
            .append( " AND " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .toString();

        selectMsgsByDstSessionSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( MESSAGE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE ").append( STORE_SESSION_ID_COLUMN ).append( "= ?" )
            .append( " AND " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?" )
            .toString();

        selectExistSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        // A single query that can tell us if the msg & destination exist and
        // broker is being taken over by other broker
        // If value of col1 is greater than 0, then the msg does exist.
        // If value of col2 is greater than 0, then the dst does exist.
        // If value of col3 is greater than 0, then broker is being taken over.
        StringBuffer strBuff = new StringBuffer(256)
            .append( "SELECT MAX(msgTS), MAX(dstTS), MAX(bkrState) FROM (" )
            .append(   "SELECT " ).append( CREATED_TS_COLUMN )
            .append(   " AS msgTS, 0 AS dstTS, 0 AS bkrState FROM " ).append( tableName )
            .append(   " WHERE " ).append( ID_COLUMN ).append( " = ?" )
            .append( " UNION " )
            .append(   "SELECT 0 AS msgTS, " ).append( DestinationDAO.CREATED_TS_COLUMN )
            .append(   " AS dstTS, 0 AS bkrState FROM " )
            .append(   dbMgr.getTableName( DestinationDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " ).append( DestinationDAO.ID_COLUMN ).append( " = ?" );
        if ( Globals.getHAEnabled() ) {
            strBuff
            .append( " UNION " )
            .append(   "SELECT 0 AS msgTS, 0 AS dstTS, " ).append( BrokerDAO.STATE_COLUMN )
            .append(   " AS bkrState FROM " )
            .append(   dbMgr.getTableName( BrokerDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " ).append( BrokerDAO.ID_COLUMN ).append( " = ? AND " )
            .append(   BrokerDAO.STATE_COLUMN ).append( " IN (" )
            .append(   BrokerState.I_FAILOVER_PENDING ).append( ", " )
            .append(   BrokerState.I_FAILOVER_STARTED ).append( ", " )
            .append(   BrokerState.I_FAILOVER_COMPLETE ).append( ", " )
            .append(   BrokerState.I_FAILOVER_FAILED ).append( ")" );
        }
        strBuff.append( ") tbl" );
        selectCanInsertSQL = strBuff.toString();
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
     * @param message the message to be persisted
     * @param dstUID the destination
     * @param conUIDs an array of interest ids whose states are to be
     *      stored with the message
     * @param states an array of states
     * @param storeSessionID the store session ID that owns the msg
     * @param createdTime timestamp
     * @param checkMsgExist check if message & destination exist in the store
     * @exception BrokerException if a message with the same id exists
     *  in the store already
     */
    public void insert( Connection conn, DestinationUID dstUID, Packet message,
        ConsumerUID[] conUIDs, int[] states, long storeSessionID,
        long createdTime, boolean checkMsgExist, boolean replaycheck )
        throws BrokerException {

        String dstID = null;
        if ( dstUID != null ) {
            dstID = dstUID.toString();
        }

        insert( conn, dstID, message, conUIDs, states, storeSessionID,
                createdTime, checkMsgExist, replaycheck );
    }

    public void insert( Connection conn, String dstID, Packet message,
        ConsumerUID[] conUIDs, int[] states, long storeSessionID,
        long createdTime, boolean checkMsgExist, boolean replaycheck )
         throws BrokerException {

        SysMessageID sysMsgID = (SysMessageID)message.getSysMessageID();
        String id = sysMsgID.getUniqueName();
        int size = message.getPacketSize();
        long txnID = message.getTransactionID();

        if ( dstID == null ) {
            dstID = DestinationUID.getUniqueString(
                message.getDestination(), message.getIsQueue() );
        }

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

            if ( checkMsgExist ) {
                try {
                    canInsertMsg( conn, id, dstID, dbMgr.getBrokerID() );
                } catch (BrokerException e) {
                    if (!(e instanceof StoreBeingTakenOverException) &&
                          e.getStatusCode() != Status.CONFLICT &&
                          e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                        e.setSQLReplayCheck(replaycheck);
                    }
                    if (!(e instanceof StoreBeingTakenOverException) &&
                          e.getStatusCode() == Status.CONFLICT) {
                        if (replaycheck) {
                            if ( conUIDs != null ) {
                                HashMap map = null;
                                try {
                                    map = dbMgr.getDAOFactory().getConsumerStateDAO().getStates( conn, sysMsgID );
                                } catch (BrokerException ee) {
                                    e.setSQLRecoverable(true);
                                    e.setSQLReplayCheck(true);
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
                                    logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY, id+"["+dstID+"]"+cids);
                                    return;
                                }
                            } else {
                                logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY, id+"["+dstID+"]");
                                return;
                            }
                        }
                    }
                    throw e;
                }
            }

            try {
                // Get the msg as bytes array
                byte[] data = message.getBytes();

                pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
                if (fi.FAULT_INJECTION) {
                    if (fi.checkFault(FaultInjection.FAULT_HA_BADSYSID, null)) {
                        fi.unsetFault(FaultInjection.FAULT_HA_BADSYSID);
                        id = id+"abc";
                    }
                }
                pstmt.setString( 1, id );
                pstmt.setInt( 2, size );
                pstmt.setLong( 3, storeSessionID );
                pstmt.setString( 4, dstID );
                Util.setLong( pstmt, 5, (( txnID == 0 ) ? -1 : txnID) );
                pstmt.setLong( 6, createdTime );
                Util.setBytes( pstmt, 7, data );

                pstmt.executeUpdate();

                // Store the consumer's states if any
                if ( conUIDs != null ) {
                    dbMgr.getDAOFactory().getConsumerStateDAO().insert(
                        conn, dstID, sysMsgID, conUIDs, states, false, false );
                }

                // Commit all changes
                if ( myConn ) {
                    conn.commit();
                }
            } catch ( Exception e ) {
                myex = e;
                boolean replayck = false;
                try {
                    if ( !conn.getAutoCommit() ) {
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

                BrokerException ee = new BrokerException(
                    br.getKString( BrokerResources.X_PERSIST_MESSAGE_FAILED,
                    id ), ex );
                ee.setSQLRecoverable(true);
                if (replayck) {
                    ee.setSQLReplayCheck(true);
                }
                throw ee;
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
     * Move a message to another destination.
     * @param conn database connection
     * @param message the message
     * @param fromDst the destination
     * @param toDst the destination to move to
     * @param conUIDs an array of interest ids whose states are to be
     *      stored with the message
     * @param states an array of states
     * @throws IOException
     * @throws BrokerException
     */
    public void moveMessage( Connection conn, Packet message,
        DestinationUID fromDst, DestinationUID toDst, ConsumerUID[] conUIDs,
        int[] states ) throws IOException, BrokerException {

	SysMessageID sysMsgID = (SysMessageID)message.getSysMessageID().clone();
        String id = sysMsgID.getUniqueName();
        int size = message.getPacketSize();
        
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

            // Get the msg as bytes array
            byte[] data = message.getBytes();

            pstmt = dbMgr.createPreparedStatement( conn, updateDestinationSQL );
            pstmt.setString( 1, toDst.toString() );
            pstmt.setInt( 2, size );
            Util.setBytes( pstmt, 3, data );
            pstmt.setString( 4, id );

            if ( pstmt.executeUpdate() == 0 ) {
                // We're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        id, fromDst ), Status.NOT_FOUND );
            }

            /**
             * Update consumer states:
             * 1. remove the old states
             * 2. re-insert the states
             */
            ConsumerStateDAO conStateDAO = dbMgr.getDAOFactory().getConsumerStateDAO();
            conStateDAO.deleteByMessageID( conn, sysMsgID.getUniqueName() );
            if ( conUIDs != null || states != null ) {
                conStateDAO.insert(
                    conn, toDst.toString(), sysMsgID, conUIDs, states, false, false );
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
                ex = DBManager.wrapSQLException("[" + updateDestinationSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            Object[] args = { id, fromDst, toDst };
            throw new BrokerException(
                br.getKString( BrokerResources.X_MOVE_MESSAGE_FAILED,
                args ), ex );
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
     * @param conn Database Connection
     * @param dstUID the destination
     * @param id the SysMessageID
     * @throws BrokerException
     */
    @Override
    public void delete( Connection conn, DestinationUID dstUID,
        String id, boolean replaycheck ) throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true; // Set to true since this is our connection
            }

            if (fi.FAULT_INJECTION) {
                HashMap fips = new HashMap();
                fips.put(FaultInjection.DST_NAME_PROP, 
                     DestinationUID.getUniqueString(dstUID.getName(), dstUID.isQueue()));
                fi.checkFaultAndExit(FaultInjection.FAULT_TXN_COMMIT_1_8, fips, 2, false);
            }
                     
            // Now delete the message
            boolean deleteFailed = false;
            pstmt = dbMgr.createPreparedStatement( conn, deleteSQL );
            pstmt.setString( 1, id );
            if ( pstmt.executeUpdate() == 0 ) {
                deleteFailed = true;
            } else {
                // For HA mode, make sure this broker still owns the store
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
            }
            
            // Delete states
            dbMgr.getDAOFactory().getConsumerStateDAO().deleteByMessageID( conn, id );

            if (deleteFailed && replaycheck) {
                logger.log(Logger.INFO, BrokerResources.I_CANCEL_SQL_REPLAY, id+"["+dstUID+"]delete");
                return;
            }
            if (deleteFailed) {
                // We'll assume the msg does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        id, dstUID ), Status.NOT_FOUND );
            }

            // Check whether to commit or not
            if ( myConn ) {
                conn.commit();
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
                if (!(e instanceof StoreBeingTakenOverException) &&
                    ((BrokerException)e).getStatusCode() != Status.NOT_FOUND) { 
                    ((BrokerException)e).setSQLRecoverable(true);
                    ((BrokerException)e).setSQLReplayCheck(replayck);
                }
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_MESSAGE_FAILED,
                id ), ex );
            be.setSQLRecoverable(true);
            be.setSQLReplayCheck(replayck);
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
     * Delete all messages from a destination for the current broker.
     * @param conn Database Connection
     * @param dstUID the destination
     * @param storeSession null if delete the destination from all store sessions 
     * @return the number of msgs deleted
     * @throws BrokerException
     */
    public int deleteByDestinationBySession( Connection conn, 
        DestinationUID dstUID, Long storeSession )
        throws BrokerException {

        int msgCount;
        String dstID = dstUID.toString();

        String sql = deleteByDstSQL;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true; // Set to true since this is our connection
            }

            dbMgr.getDAOFactory().getDestinationDAO().checkDestination( conn, dstID );

            // First delete consumer states for the destination since
            // Consumer State table is a child table, i.e. it has to join with
            // the Message table to select message IDs for the destination that
            // will be deleted.
            dbMgr.getDAOFactory().getConsumerStateDAO().
                  deleteByDestinationBySession( conn, dstUID, storeSession );

            // Now delete all msgs associated with the destination
            if (storeSession != null) {
                sql = deleteByDstBySessionSQL;
            }
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, dstID );
            pstmt.setString( 2, dbMgr.getBrokerID() );
            if (storeSession != null) {
                pstmt.setLong( 3, storeSession.longValue() );
            }
          
            msgCount = pstmt.executeUpdate();

            // Check whether to commit or not
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
                br.getKString( BrokerResources.X_REMOVE_MESSAGES_FOR_DST_FAILED,
                dstID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        return msgCount;
    }

    /**
     * Delete all entries.
     * @param conn Database Connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        String whereClause = null;        
        if ( Globals.getHAEnabled() ) {
            DBManager dbMgr = DBManager.getDBManager();

            // Only delete messages that belong to the running broker,
            // construct the where clause for the delete statement:
            //   DELETE FROM mqmsg41cmycluster
            //   WHERE EXISTS
            //     (SELECT id FROM mqses41cmycluster
            //      WHERE  id = mqmsg41cmycluster.store_session_id AND
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
     * Get the broker ID that owns the specified message.
     * @param conn database connection
     * @param id the system message id of the message
     * @return the broker ID
     * @throws BrokerException
     */
    public String getBroker( Connection conn, DestinationUID dstUID, String id )
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

            pstmt = dbMgr.createPreparedStatement( conn, selectBrokerSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                brokerID = rs.getString( 1 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        id, dstUID ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectBrokerSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_MESSAGE_FAILED, id), ex);
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
     * Get the message.
     * @param conn database connection
     * @param dstUID the destination
     * @param sysMsgID the SysMessageID
     * @return Packet the message
     * @throws BrokerException if message does not exist in the store
     */
    public Packet getMessage( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID ) throws BrokerException {

        return getMessage( conn, dstUID, sysMsgID.toString() );
    }

    /**
     * Get a Message.
     * @param conn database connection
     * @param dstUID the destination
     * @param id the system message id of the message
     * @return Packet the message
     * @throws BrokerException
     */
    public Packet getMessage( Connection conn, DestinationUID dstUID, String id )
        throws BrokerException {

        Packet msg = null;

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
            msg = (Packet)loadData( rs, true );
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
            } else if ( e instanceof InvalidPacketException ) {
                InvalidPacketException ipe = (InvalidPacketException)e;
                ipe.appendMessage("["+id+", "+dstUID+"]["+selectSQL+"]");
                ex = ipe;
            } else if ( e instanceof PacketReadEOFException ) {
                PacketReadEOFException pre = (PacketReadEOFException)e;
                pre.appendMessage("["+id+", "+dstUID+"]["+selectSQL+"]");
                ex = pre;
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + selectSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_MESSAGE_FAILED, id), ex);
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        if ( msg == null ) {
            throw new BrokerException(
                br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                    id, dstUID ), Status.NOT_FOUND );
        }

        return msg;
    }

    /**
     * Get all message IDs for a broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a List of all messages the specified broker owns
     * @throws BrokerException
     */
    public List getMessagesByBroker( Connection conn, String brokerID )
        throws BrokerException {

        List list = Collections.EMPTY_LIST;

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

            // Retrieve all messages for the target broker
            pstmt = dbMgr.createPreparedStatement( conn, selectMsgsByBrokerSQL );
            pstmt.setString( 1, brokerID );
            rs = pstmt.executeQuery();
            list = (List)loadData( rs, false );
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
            } else if ( e instanceof InvalidPacketException ) {
                InvalidPacketException ipe = (InvalidPacketException)e;
                ipe.appendMessage("["+selectMsgsByBrokerSQL+"]");
                ex = ipe;
            } else if ( e instanceof PacketReadEOFException ) {
                PacketReadEOFException pre = (PacketReadEOFException)e;
                pre.appendMessage("["+selectMsgsByBrokerSQL+"]");
                ex = pre;
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + selectMsgsByBrokerSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectMsgsByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_LOAD_MSG_FOR_BROKER_FAILED,
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
     * Get all message IDs and corresponding destination IDs for a broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a Map of all messages corresponding destinations the specified broker owns
     * @throws BrokerException
     */
    public Map<String, String> getMsgIDsAndDstIDsByBroker( 
        Connection conn, String brokerID )
        throws BrokerException {

        Map<String, String> map = new HashMap<String, String>();

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

            if ( brokerID == null ) {
                brokerID = dbMgr.getBrokerID();
            }

            // Retrieve all message IDs and corresponding for the target broker
            pstmt = dbMgr.createPreparedStatement( conn, selectMsgIDsAndDstIDsByBrokerSQL );
            pstmt.setString( 1, brokerID );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                map.put( rs.getString( 1 ), rs.getString( 2 ) );
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
                ex = DBManager.wrapIOException("[" + selectMsgIDsAndDstIDsByBrokerSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectMsgIDsAndDstIDsByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_LOAD_MSG_FOR_BROKER_FAILED,
                    brokerID ), ex );
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
     * Get all message IDs for a destination and current/local broker.
     * @param conn database connection
     * @param dst the destination
     * @param brokerID the broker ID
     * @param storeSession can be null
     * @return a List of all persisted destination names.
     * @throws BrokerException
     */
    public List getIDsByDst( Connection conn, Destination dst, String brokerID, Long storeSession )
        throws BrokerException {

        ArrayList list = new ArrayList();

        String dstID = dst.getUniqueName();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectIDsByDstBrokerSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }
            if (storeSession != null) {
                StoreSessionDAOImpl.checkStoreSessionOwner( conn, storeSession, brokerID );
                sql = selectIDsByDstSessionSQL;
            }
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            if (storeSession != null) {
                pstmt.setLong( 1, storeSession.longValue() ); 
            } else {
                pstmt.setString( 1, brokerID );
            }
            pstmt.setString( 2, dstID );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                String msgID = rs.getString( 1 );
                list.add( msgID );
            }
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
                br.getKString( BrokerResources.X_LOAD_MESSAGES_FOR_DST_FAILED ),
                dstID, ex );
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
     * Return an enumeration of all persisted messages for the given destination.
     * Use the Enumeration methods on the returned object to fetch and load
     * each message sequentially.
     *
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @param dst the destination
     * @param brokerID the broker ID
     * @return an enumeration of all persisted messages, an empty
     *		enumeration will be returned if no messages exist for the
     *		destionation
     * @exception BrokerException if an error occurs while getting the data
     */
    public Enumeration messageEnumeration( Destination dst, String brokerID, Long storeSession )
        throws BrokerException {

        Connection conn = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            conn = dbMgr.getConnection( true );

            // Verify destination exists
            dbMgr.getDAOFactory().getDestinationDAO().checkDestination(
                conn, dst.getUniqueName() );

            Iterator msgIDItr = getIDsByDst( conn, dst, brokerID, storeSession ).iterator();
            return new MsgEnumeration( dst.getDestinationUID(), this, msgIDItr );
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex );
        }
    }

    /*
     * This method is to be used at broker startup to load persisted
     * messages on demand.  
     *
     * This method returns a message enumeration that uses ResultSet cursor.
     * Caller must call Store.closeEnumeration() after use 
     *
     * @param dst the destination
     * @param brokerID the broker ID
     * @return an enumeration of all persisted messages, an empty
     *      enumeration will be returned if no messages exist for the
     *      destionation
     * @exception BrokerException if an error occurs while getting the data
     */
    public Enumeration messageEnumerationCursor( Destination dst, String brokerID, Long storeSession )
        throws BrokerException {

        String dstID = dst.getUniqueName();

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String sql = selectMsgsByDstBrokerSQL;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            conn = dbMgr.getConnection( true );


            // Verify destination exists
            dbMgr.getDAOFactory().getDestinationDAO().checkDestination( conn, dstID );
            if (storeSession != null) {
                StoreSessionDAOImpl.checkStoreSessionOwner( conn, storeSession, brokerID );
                sql = selectMsgsByDstSessionSQL;
            }
            
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            if (storeSession != null) {
                pstmt.setLong( 1, storeSession.longValue() );
            } else {
                pstmt.setString( 1, brokerID );
            }
            pstmt.setString( 2, dstID );
            rs = pstmt.executeQuery();

            return new MessageEnumeration( rs, pstmt, conn, sql,
                                           this, Globals.getStore() );
        } catch ( Throwable e ) {
            Throwable ex = e;
            try {
                if ( e instanceof BrokerException ) {
                    throw (BrokerException)e;
                } else if ( e instanceof SQLException ) {
                    ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
                } 
                throw new BrokerException( br.getKString(
                    BrokerResources.X_LOAD_MESSAGES_FOR_DST_FAILED),
                    dstID, ex );
            } finally {
                Util.close( rs, pstmt, conn, ex );
            }
        }
    }


    /**
     * Check if a a message has been acknowledged by all interests, i.e. consumers.
     * @param conn database connection
     * @param dstUID the destination
     * @param sysMsgID sysMsgID the SysMessageID
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID ) throws BrokerException {

        int total = -1;
        int totalAcked = -1;
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

            pstmt = dbMgr.createPreparedStatement( conn, selectCountByConsumerAckedSQL );
            pstmt.setString( 1, id );
            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                total = rs.getInt( 1 );
                totalAcked = rs.getInt( 2 );
            } else {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        id, dstUID ), Status.NOT_FOUND );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCountByConsumerAckedSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                if (((BrokerException)e).getStatusCode() != Status.NOT_FOUND) {
                    ((BrokerException)e).setSQLRecoverable(true);
                }
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectCountByConsumerAckedSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            BrokerException be = new BrokerException(
                br.getKString( BrokerResources.X_LOAD_MESSAGE_FAILED,
                id ), ex );
            be.setSQLRecoverable(true);
            throw be;
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        // Return true if all interests have acknowledged. To be safe,
        // message is considered unrouted if interest list is empty (total = 0).
        if ( total > 0 && total == totalAcked ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check whether the specified message exists.
     * @param conn database connection
     * @param id the system message id of the message to be checked
     * @return return true if the specified message exists
     */
    public boolean hasMessage( Connection conn, String id ) throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, selectExistSQL );
            pstmt.setString( 1, id );
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
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectExistSQL+"]", rbe );
            }
 
            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectExistSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_MESSAGE_FAILED,
                id ), ex );
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
     * Check whether the specified message exists.
     * @param conn
     * @param dstID the destination
     * @param mid the system message id of the message to be checked
     * @throws BrokerException if the message does not exist in the store
     */
    public void checkMessage( Connection conn, String dstID, String mid )
        throws BrokerException {

        if ( !hasMessage( conn, mid ) ) {
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"[checkMessage():"+mid+", "+dstID+"]", rbe );
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                    mid, dstID ), Status.NOT_FOUND );
        }
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return A HashMap of name value pair of information
     */
    @Override
    public HashMap getDebugInfo( Connection conn ) {

        if (!Boolean.getBoolean(getTableName())) {
            return super.getDebugInfo( conn );
        }
        HashMap map = new LinkedHashMap();
        HashMap baddata = new LinkedHashMap();
        String brokerid = null;
        Object data = null;
        try {
            brokerid = DBManager.getDBManager().getBrokerID();
            data = getMsgIDsAndDstIDsByBroker( null, brokerid );
            map.put("["+tableName+"]RowCount(brokerID="+brokerid+")", 
                     String.valueOf(((Map)data).size()));
            String sysid = null;
            Iterator itr =  ((Map)data).keySet().iterator();
            while (itr.hasNext()) {
                sysid = (String)itr.next(); 
                String dst = (String)((Map)data).get(sysid);
                boolean bad = false;
                try {
                    SysMessageID.get(sysid);
                } catch (RuntimeException e) {
                    bad = true;
                    baddata.put("EXCEPTION-MESSAGE_ID["+sysid+"]", dst);
                    logger.logStack(logger.ERROR, 
                        "Failed to validate SysMessageID for message ID="+
                         sysid+" in destination "+dst+": "+e.getMessage(), e);
                }
                try {
                     Packet pkt = getMessage( null,  new DestinationUID(dst), sysid );
                     if (bad) {
                         logger.logToAll(logger.INFO, "Packet for message ID="+sysid+"["+pkt+"]"); 
                     }
                } catch (Exception e) {
                     baddata.put("EXCEPTION-PACKET["+sysid+"]", dst);
                     logger.logStack(logger.ERROR, 
                         "Failed to retrieve mesage Packet for  message ID="+
                          sysid+" in destination "+dst+": "+e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            data = e.getMessage();
            logger.logStack(logger.ERROR, e.getMessage(), e);
        }
        map.put("["+tableName+"]SysMessageID:DestinationID(brokerID="+brokerid+")\n", data);
        if (!baddata.isEmpty()) {
            map.put("EXCEPTION!!["+tableName+"]SysMessageID:DestinationID(brokerID="+brokerid+")\n", baddata);
        }
        return map;
    }

    /**
     * Return the message count for the given broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return the message count
     */
    public int getMessageCount( Connection conn, String brokerID ) throws BrokerException {

        int size = -1;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectCountByBrokerSQL );
            pstmt.setString( 1, brokerID );
            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                size = rs.getInt( 1 );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCountByBrokerSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectCountByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_GET_MSG_COUNTS_FOR_BROKER_FAILED,
                    brokerID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return size;
    }

    /**
     * Return the number of persisted messages and total number of bytes for
     * the given destination.
     * @param conn database connection
     * @param dst the destination
     * @return a HashMap
     */
    public HashMap getMessageStorageInfo( Connection conn, Destination dst, Long storeSession )
        throws BrokerException {

        HashMap data = new HashMap( 2 );

        String dstID = dst.getUniqueName();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectCountByDstBrokerSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }
            if (storeSession != null) {
                StoreSessionDAOImpl.checkStoreSessionOwner( conn, storeSession, dbMgr.getBrokerID() );
                sql = selectCountByDstSessionSQL;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            if (storeSession != null) {
                pstmt.setLong( 1, storeSession.longValue() );
            } else {
                pstmt.setString( 1, dbMgr.getBrokerID() );
            }
            pstmt.setString( 2, dstID );
            pstmt.setString( 3, dstID );

            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                data.put( DestMetricsCounters.CURRENT_MESSAGES,
                    Integer.valueOf( rs.getInt( 1 ) ) );
                data.put( DestMetricsCounters.CURRENT_MESSAGE_BYTES,
                    Long.valueOf( rs.getLong( 2 ) ) );
            } else {
                // Destination doesn't exists
                throw new BrokerException(
                    br.getKString( BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                    dstID ), Status.NOT_FOUND );
            }
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
                br.getKString( BrokerResources.X_GET_COUNTS_FROM_DATABASE_FAILED,
                dstID ), ex );
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
     * Load a single message or messages from a ResultSet.
     * @param rs the ResultSet
     * @param isSingleRow specify interesed in only the 1st row of the ResultSet
     * @return a message or a List of messages
     * @throws IOException
     * @throws SQLException
     */
    protected Object loadData( ResultSet rs, boolean isSingleRow )
        throws IOException, SQLException {

        ArrayList list = null;
        if ( !isSingleRow ) {
            list = new ArrayList( 100 );
        }

        while ( rs.next() ) {
            Packet msg = new Packet(false);
            msg.generateTimestamp(false);
            msg.generateSequenceNumber(false);

            InputStream is = null;
            InvalidPacketException ipex = null;
            IOException origex = null;
            Blob blob = null;
            long bloblen = -1;
            try { 
                if ( getMsgColumnType(rs, 1) == Types.BLOB ) {
                    blob = rs.getBlob( 1 );
                    is = blob.getBinaryStream();
                    bloblen = blob.length();
                } else {
                    is = rs.getBinaryStream( 1 );
                }
                try {
                    if (fi.FAULT_INJECTION) {
                        if (fi.checkFault(FaultInjection.FAULT_HA_BADPKT_EXCEPTION, null)) {
                            fi.unsetFault(FaultInjection.FAULT_HA_BADPKT_EXCEPTION);
                            throw new StreamCorruptedException(
                                FaultInjection.FAULT_HA_BADPKT_EXCEPTION);
                        }
                        if (fi.checkFault(FaultInjection.FAULT_HA_PKTREADEOF_RECONNECT_EXCEPTION, null)) {
                            fi.unsetFault(FaultInjection.FAULT_HA_PKTREADEOF_RECONNECT_EXCEPTION);
                            PacketReadEOFException e = new PacketReadEOFException(
                                FaultInjection.FAULT_HA_PKTREADEOF_RECONNECT_EXCEPTION);
                            e.setBytesRead(0);
                            throw e;
                        }
                    }
                    msg.readPacket(is);
                    if (fi.FAULT_INJECTION) {
                        if (fi.checkFault(FaultInjection.FAULT_HA_PKTREADEOF_EXCEPTION, null)) {
                            fi.unsetFault(FaultInjection.FAULT_HA_PKTREADEOF_EXCEPTION);
                            PacketReadEOFException e = new PacketReadEOFException(
                                FaultInjection.FAULT_HA_PKTREADEOF_EXCEPTION+
                                ":pktsize="+msg.getPacketSize()+", blobsize="+
                                 (blob != null ? bloblen:rs.getBytes(1).length));
                            e.setBytesRead(msg.getPacketSize());
                            throw e;
                        }
                    }
                } catch (StreamCorruptedException e) {
                    origex = e;
                    ipex = new InvalidPacketException(e.getMessage(), e);
                } catch (IllegalArgumentException e) {
                    origex = new IOException(e.getMessage(), e);
                    ipex = new InvalidPacketException(e.getMessage(), e);
                } catch (PacketReadEOFException e) {
                    origex = e;
                    if (blob != null && e.getBytesRead() < bloblen) {
                        throw e; //for auto-reconnect 
                    }
                    ipex = new InvalidPacketException(e.getMessage(), e);
                }
                if (ipex != null) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        /* ignore */
                    }
                    is = null;
                    byte[] bytes = null;
                    if (blob != null) {
                        try {
                            bytes = blob.getBytes(1L, (int)bloblen);
                        } catch (Exception e) {
                            String es = SupportUtil.getStackTraceString(e);
                            bytes = es.getBytes("UTF-8");
                        }
                    } else {
                        bytes = rs.getBytes(1);
                        if (bytes != null && 
                            (origex instanceof PacketReadEOFException)) {
                            if (((PacketReadEOFException)origex).getBytesRead() < bytes.length) {
                                throw origex;
                            }
                        }
                    }
                    if (bytes == null) {
                        throw origex;
                    }
                    ipex.setBytes(bytes);
                    throw ipex;
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        /*ignore*/
                    }
                }
            }

            if (DEBUG) {
                logger.log(Logger.INFO, 
                    "MessageDAOImpl.loadData(isSingleRow="+isSingleRow+"):"+
                    "Loaded message from database for "+ msg.getMessageID());
            }

            if ( isSingleRow ) {
                return msg;
            } else {
                list.add( msg );
            }
        }
        if (DEBUG && list != null) {
            logger.log(Logger.INFO, 
            "MessageDAOImpl.loadData(): ResultSet[size="+list.size()+"]");
        }
        return list;
    }

    /**
     * Get Message column type (e.g. is it a Blob?)
     * @param rs the ResultSet
     * @param msgColumnIndex the index of the Message column
     * @return column type
     */
    private int getMsgColumnType( ResultSet rs, int msgColumnIndex )
        throws SQLException {

        // Cache the result
        if ( msgColumnType == -Integer.MAX_VALUE ) {
            msgColumnType = rs.getMetaData().getColumnType( msgColumnIndex );
        }

        return msgColumnType;
    }

    /**
     * Check if a msg can be inserted. A BrokerException is thrown if the msg
     * already exists in the store, the destination doesn't exist, or
     * the specified broker is being taken over by another broker (HA mode).
     * @param conn database connection
     * @param msgID message ID
     * @param dstID destination ID
     * @param brokerID broker ID
     * @throws BrokerException if msg cannot be inserted
     */
    protected void canInsertMsg( Connection conn, String msgID, String dstID,
        String brokerID ) throws BrokerException {

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        final String sql = selectCanInsertSQL;
        try {
   
            pstmt = DBManager.getDBManager().createPreparedStatement( conn, sql );
            pstmt.setString( 1, msgID );
            pstmt.setString( 2, dstID );
            if ( Globals.getHAEnabled() ) {
                pstmt.setString( 3, brokerID );
            }

            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                // Make sure msg doesn't exist, i.e. created timestamp == 0
                if ( rs.getLong( 1 ) > 0 ) {
                    throw new BrokerException( br.getKString(
                        BrokerResources.E_MSG_EXISTS_IN_STORE, msgID, dstID ),
                        Status.CONFLICT );
                }

                // Make sure dst does exist, i.e. created timestamp > 0
                if ( rs.getLong( 2 ) == 0 ) {
                    throw new BrokerException( br.getKString(
                        BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                        dstID ), Status.NOT_FOUND );
                }

                // Make sure broker is not being taken over, i.e. state == 0
                if ( Globals.getHAEnabled() ) {
                    if ( rs.getInt( 3 ) > 0 ) {
                        BrokerException be = new StoreBeingTakenOverException(
                            br.getKString(BrokerResources.E_STORE_BEING_TAKEN_OVER) );
                        try {
                            DBManager dbMgr = DBManager.getDBManager();
                            BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                            HABrokerInfo bkrInfo = dao.getBrokerInfo( conn, dbMgr.getBrokerID() );
                            logger.logStack( Logger.ERROR, be.getMessage()+"["+ 
                                (bkrInfo == null ? ""+dbMgr.getBrokerID():bkrInfo.toString())+"]", be );
                        } catch (Throwable t) { /* Ignore error */ }

                        throw be;
                    }

                }
            } else {
                // Shouldn't happen
                throw new BrokerException(
                    br.getKString( BrokerResources.X_JDBC_QUERY_FAILED, sql ) );
            }
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
                br.getKString( BrokerResources.X_JDBC_QUERY_FAILED, sql ), ex );
        } finally {
            Util.close( rs, pstmt, null, myex );
        }
    }

    /**
     * Message Enumeration class.
     */
    private static class MsgEnumeration implements Enumeration {

        DestinationUID dID = null;
        MessageDAO msgDAO = null;
        Iterator msgIDItr = null;
        Object msgToReturn = null;

        MsgEnumeration( DestinationUID dstUID, MessageDAO dao, Iterator itr ) {
            dID = dstUID;
            msgDAO = dao;
            msgIDItr = itr;
        }

        public boolean hasMoreElements() {
            Packet msg = null;
            while ( msgIDItr.hasNext() ) {
                String mid = null;
                try {
                    mid = (String)msgIDItr.next();
                    msg = msgDAO.getMessage( null, dID, mid );
                    msgToReturn = msg;
                    return true;
                } catch ( Exception e ) {
                    Globals.getLogger().logStack( Logger.ERROR,
                        BrokerResources.X_LOAD_MESSAGE_FAILED, mid, e );
                }
            }

            // no more
            msgToReturn = null;
            return false;
        }

        public Object nextElement() {
            if ( msgToReturn != null ) {
                return msgToReturn;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    @Override
    public void repairCorruptedSysMessageID( Connection conn,
        SysMessageID realSysId, String badSysIdStr, String duidStr)
        throws BrokerException {

	SysMessageID sysid = (SysMessageID)realSysId.clone();
        String realId = sysid.getUniqueName();
        
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;

        final String sql = repairSysMessageIDSQL;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1,  realId);
            pstmt.setString( 2, badSysIdStr );
            pstmt.setString( 3, duidStr );

            if ( pstmt.executeUpdate() == 0 ) {
                // assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        badSysIdStr, duidStr ), Status.NOT_FOUND );
            }

        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+sql+"]", rbe );
            }

            Exception ex = e;
            if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" +sql+ "]", (SQLException)e);
            }
            String[] args = { badSysIdStr, realId, duidStr, e.getMessage() };
            throw new BrokerException( br.getKString(
                BrokerResources.X_REPAIR_CORRUPTED_MSGID_IN_STORE, args), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }
}
