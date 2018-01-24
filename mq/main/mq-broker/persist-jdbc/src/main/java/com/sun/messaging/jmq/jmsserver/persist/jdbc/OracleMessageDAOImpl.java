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
 * @(#)OracleMessageDAOImpl.java	1.16 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;

import java.sql.*;
import java.util.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;

/**
 * This class implement Oracle MessageDAO.
 */
class OracleMessageDAOImpl extends MessageDAOImpl {

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    OracleMessageDAOImpl() throws BrokerException {

        super();

        // Initialize message column with an "empty" BLOB
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
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, EMPTY_BLOB() )" )
            .toString();

        // Blob column need to be update separately
        updateDestinationSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( DESTINATION_ID_COLUMN ).append( " = ?, " )
            .append( MESSAGE_SIZE_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param message the message to be persisted
     * @param dstID the destination
     * @param conUIDs an array of interest ids whose states are to be
     *      stored with the message
     * @param states an array of states
     * @param storeSessionID the store session ID that owns the msg
     * @param createdTime timestamp
     * @param checkMsgExist check if message & destination exist in the store
     * @param replaycheck true if replay needs to check previous insert succeeded
     * @exception com.sun.messaging.jmq.jmsserver.util.BrokerException if a message with the same id exists
     *  in the store already
     */
    public void insert( Connection conn, String dstID, Packet message,
        ConsumerUID[] conUIDs, int[] states, long storeSessionID, long createdTime,
        boolean checkMsgExist, boolean replaycheck ) throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();

            // Verify that we're using an Oracle driver because we're using
            // Oracle Extensions for LOBs.
            if ( ! dbMgr.isOracleDriver() ) {
                // Try generic implementation
                super.insert( conn, dstID, message, conUIDs, states, storeSessionID,
                              createdTime, checkMsgExist, replaycheck );
                return;
            }

            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            SysMessageID sysMsgID = (SysMessageID)message.getSysMessageID();
            String id = sysMsgID.getUniqueName();
            int size = message.getPacketSize();
            long txnID = message.getTransactionID();

            if ( dstID == null ) {
                dstID = DestinationUID.getUniqueString(
                    message.getDestination(), message.getIsQueue() );
            }

            if ( checkMsgExist ) {
                boolean hasmsg = false;
                try {
                    hasmsg = hasMessage( conn, id );
                } catch (BrokerException e) {
                    e.setSQLRecoverable(true);
                    e.setSQLReplayCheck(replaycheck);
                    throw e;
                }
                if ( hasmsg && replaycheck ) {
                    if ( conUIDs != null ) {
                        HashMap map = null;
                        try {
                            map = dbMgr.getDAOFactory().getConsumerStateDAO().getStates( conn, sysMsgID );
                        } catch (BrokerException e) {
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
                if ( hasmsg ) {
                    throw new BrokerException(
                        br.getKString(BrokerResources.E_MSG_EXISTS_IN_STORE, id, dstID ) );
                }
                try {
                    dbMgr.getDAOFactory().getDestinationDAO().checkDestination( conn, dstID );
                } catch (BrokerException e) {
                    if (e.getStatusCode() != Status.NOT_FOUND) {
                        e.setSQLRecoverable(true);
                    }
                    throw e;
                }
            }
            String sql = insertSQL;
            try {
                pstmt = dbMgr.createPreparedStatement( conn, sql );
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
                pstmt.executeUpdate();
                pstmt.close();

                // Obtain a "handle" to the BLOB for the message column
                sql = selectForUpdateSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, id );
                ResultSet rs = pstmt.executeQuery();
                rs.next();
                Blob blob = rs.getBlob(1);
                rs.close();
                pstmt.close();

                // Write out the message using the BLOB locator
                OutputStream bos = Util.OracleBLOB_getBinaryOutputStream( blob );
                message.writePacket( bos );
                bos.close();

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

                BrokerException be = new BrokerException(
                    br.getKString( BrokerResources.X_PERSIST_MESSAGE_FAILED,
                    id ), ex );
                be.setSQLRecoverable(true);
                if (replayck) {
                    be.setSQLReplayCheck(true);
                }
                throw be;
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
        String sql = selectForUpdateSQL;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();

            // Verify that we're using an Oracle driver because we're using
            // Oracle Extensions for LOBs.
            if ( ! dbMgr.isOracleDriver() ) {
                // Try generic implementation
                super.moveMessage( conn, message, fromDst, toDst, conUIDs, states );
                return;
            }

            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            // Obtain a "handle" to the BLOB for the message column
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, id );
            ResultSet rs = pstmt.executeQuery();

            if ( !rs.next() ) {
                // We're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                        id, fromDst ), Status.NOT_FOUND );
            }

            Blob blob = rs.getBlob(1);
            rs.close();
            pstmt.close();

            // Write out the message using the BLOB locator
            OutputStream bos = Util.OracleBLOB_getBinaryOutputStream( blob );
            message.writePacket( bos );
            bos.close();

            sql = updateDestinationSQL;
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, toDst.toString() );
            pstmt.setInt( 2, size );
            pstmt.setString( 3, id );
            pstmt.executeUpdate();

            /**
             * Update consumer states:
             * 1. remove the old states
             * 2. re-insert the states
             */
            ConsumerStateDAO conStateDAO = dbMgr.getDAOFactory().getConsumerStateDAO();
            conStateDAO.deleteByMessageID( conn, id );
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
}
