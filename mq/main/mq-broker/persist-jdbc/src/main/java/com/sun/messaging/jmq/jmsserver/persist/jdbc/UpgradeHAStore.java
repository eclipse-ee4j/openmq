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
 * @(#)UpgradeHAStore.java	1.15 07/19/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * UpgradeStore contains static methods to upgrade old data to
 * the latest version.
 */
public class UpgradeHAStore implements DBConstants {

    private BrokerResources br = Globals.getBrokerResources();
    private Logger logger = Globals.getLogger();
    private DBManager dbMgr;
    private String brokerID;

    UpgradeHAStore() throws BrokerException {

        dbMgr = DBManager.getDBManager();
        brokerID = dbMgr.getBrokerID();

        if ( !Globals.getHAEnabled() ) {
            String reason = br.getKString(BrokerResources.I_HA_NOT_ENABLE, brokerID);
            throw new BrokerException(br.getKString(
                BrokerResources.E_UPGRADE_HASTORE_FAILED, reason));
        }
    }

    void upgradeStore(Connection conn)
	throws BrokerException {

	// log informational messages
	logger.logToAll(Logger.INFO, br.getString(
            BrokerResources.I_UPGRADE_HASTORE_IN_PROGRESS,
            String.valueOf(JDBCStore.STORE_VERSION), brokerID));

        DAOFactory daoFactory = dbMgr.getDAOFactory();

        // Check if HA store exists
        int version = -1;
        try {
            VersionDAO verDAO = daoFactory.getVersionDAO();
            version = verDAO.getStoreVersion( conn );
        } catch ( BrokerException e ) {
            // Assume store doesn't exist
        }

        boolean createStore = false;
        if ( version == JDBCStore.STORE_VERSION ) {
            // Check if store has been upgraded
            BrokerDAO bkrDAO = daoFactory.getBrokerDAO();
            HABrokerInfo bkrInfo = bkrDAO.getBrokerInfo( conn, brokerID );
            if ( bkrInfo != null ) {
                String reason = br.getString(
                    BrokerResources.I_HASTORE_ALREADY_UPGRADED, brokerID);
                throw new BrokerException(br.getKString(
                    BrokerResources.E_UPGRADE_HASTORE_FAILED, reason));
            }
        } else if ( version == -1 ) {
            createStore = true;
        } else {
            // Bad version
            String reason = br.getString(BrokerResources.E_BAD_STORE_VERSION,
                String.valueOf( version ), String.valueOf( JDBCStore.STORE_VERSION ));
            throw new BrokerException(br.getKString(
                BrokerResources.E_UPGRADE_HASTORE_FAILED, reason));
        }

	try {
	    // create the tables first
            if ( createStore ) {
                DBTool.createTables( conn );
            }
	} catch (Throwable e) {
            String url = dbMgr.getCreateDBURL();
            if ( url == null || url.length() == 0 ) {
                url = dbMgr.getOpenDBURL();
            }
            String errorMsg = br.getKString(
                BrokerResources.E_CREATE_DATABASE_TABLE_FAILED, url);
            logger.logToAll(Logger.ERROR, errorMsg, e);
            throw new BrokerException(errorMsg, e);
	}

	try {
            conn.setAutoCommit(false);

            upgradeStoreSessions(conn);
	    upgradeDestinations(conn);
            upgradeInterests(conn);
	    upgradeMessages(conn);
	    upgradeTxns(conn);

	    logger.logToAll(Logger.INFO,
                br.getString(BrokerResources.I_UPGRADE_STORE_DONE));
	} catch (Exception e) {
	    // upgrade failed; log message
	    logger.logToAll(Logger.ERROR, BrokerResources.I_REMOVE_UPGRADE_HASTORE_DATA,
                brokerID);

            try {
		// remove all entries associated w/ the broker and return
                DestinationDAO dstDAO = daoFactory.getDestinationDAO();
                dstDAO.deleteAll(conn);

                ConsumerDAO conDAO = daoFactory.getConsumerDAO();
                conDAO.deleteAll(conn);

                MessageDAO msgDAO = daoFactory.getMessageDAO();
                msgDAO.deleteAll(conn);

                TransactionDAO txnDAO = daoFactory.getTransactionDAO();
                txnDAO.deleteAll(conn);
	    } catch (Exception ex) {
                logger.logStack(Logger.ERROR,
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Failed to clean up after upgrade failed", ex);
            }

            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else {
                throw new BrokerException(br.getKString(
                    BrokerResources.E_UPGRADE_HASTORE_FAILED, e.getMessage()), e);
            }
	}
    }

    /**
     * Upgrade store sessions.
     */
    void upgradeStoreSessions(Connection conn) throws BrokerException {

        StoreSessionDAO sesDAO = dbMgr.getDAOFactory().getStoreSessionDAO();

	// Non-HA destination table
	String oldtable = StoreSessionDAO.TABLE_NAME_PREFIX + "S" + brokerID;

        // SQL to insert store sessions from Non-HA table into new table
        String insertAllStoreSessionsFromOldSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( sesDAO.getTableName() )
            .append( " ( " )
            .append( StoreSessionDAO.ID_COLUMN ).append( ", " )
            .append( StoreSessionDAO.BROKER_ID_COLUMN ).append( ", " )
            .append( StoreSessionDAO.IS_CURRENT_COLUMN ).append( ", " )
            .append( StoreSessionDAO.CREATED_BY_COLUMN ).append( ", " )
            .append( StoreSessionDAO.CREATED_TS_COLUMN )
            .append( ") SELECT " )
            .append( StoreSessionDAO.ID_COLUMN ).append( ", " )
            .append( StoreSessionDAO.BROKER_ID_COLUMN ).append( ", " )
            .append( StoreSessionDAO.IS_CURRENT_COLUMN ).append( ", " )
            .append( StoreSessionDAO.CREATED_BY_COLUMN ).append( ", " )
            .append( StoreSessionDAO.CREATED_TS_COLUMN )
            .append( " FROM " ).append( oldtable )
            .toString();

	Statement stmt = null;
    Exception myex = null;
	try {
            stmt = conn.createStatement();
	    dbMgr.executeStatement( stmt, insertAllStoreSessionsFromOldSQL );
	    conn.commit();
	} catch (Exception e) {
            myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_STORE_SESSIONS_FAILED);
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( null, stmt, null, myex );
        }
    }

    /**
     * Upgrade destinations.
     */
    void upgradeDestinations(Connection conn) throws BrokerException {

        DestinationDAO dstDAO = dbMgr.getDAOFactory().getDestinationDAO();

	// Non-HA destination table
	String oldtable = DestinationDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	// SQL to select all destination from Non-HA table
	String getAllDestFromOldSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( DestinationDAO.DESTINATION_COLUMN ).append( ", " )
            .append( DestinationDAO.CREATED_TS_COLUMN ).append( ", " )
            .append( DestinationDAO.CONNECTED_TS_COLUMN ).append( ", " )
            .append( DestinationDAO.STORE_SESSION_ID_COLUMN )
            .append( " FROM " ).append( oldtable )
            .append( " WHERE " )
            .append( DestinationDAO.ID_COLUMN )
            .append( " NOT IN (SELECT " ).append( DestinationDAO.ID_COLUMN )
            .append( " FROM " ).append( dstDAO.getTableName() ).append( ")" )
            .toString();

        // SQL to insert a destination to new table
        String insertDestSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( dstDAO.getTableName() )
            .append( " ( " )
            .append( DestinationDAO.ID_COLUMN ).append( ", " )
            .append( DestinationDAO.DESTINATION_COLUMN ).append( ", " )
            .append( DestinationDAO.IS_LOCAL_COLUMN ).append( ", " )
            .append( DestinationDAO.CONNECTION_ID_COLUMN ).append( ", " )
            .append( DestinationDAO.CONNECTED_TS_COLUMN ).append( ", " )
            .append( DestinationDAO.STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( DestinationDAO.CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, ? )" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates() && !dbMgr.isHADB();
	Statement stmt = null;
	PreparedStatement pstmt = null;
	ResultSet rs = null;
	Destination dst = null;
    Exception myex = null;
	try {
            pstmt = dbMgr.createPreparedStatement( conn, insertDestSQL );

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllDestFromOldSQL );
	    while (rs.next()) {
                Object obj = Util.readObject(rs, 1);
		dst = (Destination)obj;

                long createdTS = rs.getLong(2);

                String destName = dst.getUniqueName();
                int isLocal = dst.getIsLocal() ? 1 : 0;
                long connectionID = -1;
                long connectedTS = -1;
                long sessionID = -1;
                if ( isLocal > 0 ) {
                    // Store additional info for temp destination
                    ConnectionUID cUID = dst.getConnectionUID();
                    if ( cUID != null ) {
                        connectedTS = rs.getLong(3);
                        connectionID = cUID.longValue();
                    }
                }
                sessionID = rs.getLong(4);

		// put destinations in new table
		try {
                    pstmt.setString( 1, destName );
                    Util.setObject( pstmt, 2, dst );
                    pstmt.setInt( 3, isLocal );
                    Util.setLong( pstmt, 4, connectionID );
                    Util.setLong( pstmt, 5, connectedTS );
                    pstmt.setLong( 6, sessionID );
                    pstmt.setLong( 7, createdTS );

		    if ( dobatch ) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (IOException e) {
		    IOException ex = DBManager.wrapIOException(
                        "[" + insertDestSQL + "]", e);
		    throw ex;
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertDestSQL + "]", e);
		    throw ex;
		}
	    }

	    if ( dobatch ) {
		pstmt.executeBatch();
	    }

	    conn.commit();
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_DESTINATIONS_FAILED,
                (dst == null ? "loading" : dst.getUniqueName()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade interests
     */
    private void upgradeInterests(Connection conn) throws BrokerException {

        ConsumerDAO conDAO = dbMgr.getDAOFactory().getConsumerDAO();

        // Non-HA interest(consumer) table
	String oldtbl = ConsumerDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	// SQL to select all interest from Non-HA table
	String getAllInterestFromOldSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ConsumerDAO.CONSUMER_COLUMN ).append( ", " )
            .append( ConsumerDAO.CREATED_TS_COLUMN ).append( ", " )
            .append( ConsumerDAO.ID_COLUMN )
            .append( " FROM " ).append( oldtbl )
            .append( " WHERE " )
            .append( ConsumerDAO.ID_COLUMN )
            .append( " NOT IN (SELECT " ).append( ConsumerDAO.ID_COLUMN )
            .append( " FROM " ).append( conDAO.getTableName() ).append( ")" )
            .toString();

        // SQL to insert interest to new table
        String insertInterestSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( conDAO.getTableName() )
            .append( " ( " )
            .append( ConsumerDAO.ID_COLUMN ).append( ", " )
            .append( ConsumerDAO.CONSUMER_COLUMN ).append( ", " )
            .append( ConsumerDAO.DURABLE_NAME_COLUMN ).append( ", " )
            .append( ConsumerDAO.CLIENT_ID_COLUMN ).append( ", " )
            .append( ConsumerDAO.CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ? )" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates() && !dbMgr.isHADB();
	PreparedStatement pstmt = null;
	Statement stmt = null;
	ResultSet rs = null;
	Consumer consumer = null;
        Exception myex = null;
	try {
            pstmt = dbMgr.createPreparedStatement(conn, insertInterestSQL);

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllInterestFromOldSQL );
	    while (rs.next()) {
		consumer = (Consumer)Util.readObject(rs, 1);
                long createdTS = rs.getLong(2);

                String durableName = null;
                String clientID = null;
                if ( consumer instanceof Subscription ) {
                    Subscription sub = (Subscription)consumer;
                    durableName = sub.getDurableName();
                    clientID = sub.getClientID();
                }

		// write to new table
		try {
                    pstmt.setLong( 1, consumer.getConsumerUID().longValue() );
                    Util.setObject( pstmt, 2, consumer );
                    Util.setString( pstmt, 3, durableName );
                    Util.setString( pstmt, 4, clientID );
                    pstmt.setLong( 5, createdTS );

		    if ( dobatch ) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (IOException e) {
		    IOException ex = DBManager.wrapIOException(
                        "[" + insertInterestSQL + "]", e);
		    throw ex;
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertInterestSQL + "]", e);
		    throw ex;
		}
	    }

	    if ( dobatch ) {
		pstmt.executeBatch();
	    }
	    conn.commit();
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_INTERESTS_FAILED,
                (consumer == null ? "loading" : consumer.toString()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade messages and the associated interest list.
     */
    private void upgradeMessages(Connection conn) throws BrokerException {

        // Non-HA message table
	String oldtbl = MessageDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	String sql = null;
	int nummsg = 0;

	// first find out how many messages we have in the old table
	Statement stmt = null;
        ResultSet rs = null;
    Exception myex = null;
	try {
	    sql = "SELECT COUNT(*) FROM " + oldtbl;
	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement(stmt, sql);
	    if (rs.next()) {
		nummsg = rs.getInt(1);
	    }
	} catch (SQLException e) {
        myex = e;
	    logger.log(Logger.ERROR, BrokerResources.X_JDBC_QUERY_FAILED, sql, e);
	    throw new BrokerException(
                br.getString(BrokerResources.X_JDBC_QUERY_FAILED, sql), e);
	} finally {
            Util.close( rs, stmt, null, myex );
        }

        // print info message
        logger.logToAll(Logger.INFO, br.getString(
            BrokerResources.I_UPGRADING_MESSAGES, String.valueOf(nummsg)));

        doUpgradeMsg(conn);
    }

    private void doUpgradeMsg(Connection conn)
	throws BrokerException {

        MessageDAO msgDAO = dbMgr.getDAOFactory().getMessageDAO();

	String oldmsgtbl = MessageDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	HashMap msgToDst = new HashMap();

	// SQL to select all messages from Non-HA table
	String getAllMsgFromOldSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( MessageDAO.ID_COLUMN ).append( ", " )
            .append( MessageDAO.MESSAGE_COLUMN ).append( ", " )
            .append( MessageDAO.DESTINATION_ID_COLUMN ).append( ", " )
            .append( MessageDAO.STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( MessageDAO.CREATED_TS_COLUMN )
            .append( " FROM " ).append( oldmsgtbl )
            .toString();

	Statement stmt = null;
	ResultSet rs = null;
        String msgID = null;
	Packet msg = null;
    Exception myex = null;
	try {
	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllMsgFromOldSQL );
	    while (rs.next()) {
                msgID = rs.getString(1);

		msg = new Packet(false);
		msg.generateTimestamp(false);
		msg.generateSequenceNumber(false);
                InputStream is = null;
                Blob blob = rs.getBlob(2);
                is = blob.getBinaryStream();
		msg.readPacket(is);
		is.close();

                String dstID = rs.getString(3);
                long sessionID = rs.getLong(4);
                long createdTS = rs.getLong(5);

                try {
                    msgDAO.insert( conn, dstID, msg, null, null, sessionID,
                        createdTS, true, false );
                } catch (BrokerException be) {
                    // If msg exist, just logged and continue
                    if (be.getStatusCode() == Status.CONFLICT) {
                        logger.log(Logger.WARNING, be.getMessage() + ": Ignore");
                    } else {
                        throw be;
                    }
                }

                msgToDst.put(msgID, dstID);
	    }
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_MESSAGES_FAILED,
                (msg == null ? msgID : msg.getSysMessageID().toString()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
        }

	// upgrade interest list

        ConsumerStateDAO stateDAO = dbMgr.getDAOFactory().getConsumerStateDAO();

        String oldstatetbl = ConsumerStateDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	// SQL to select all interest states from Non-HA table
	String getAllStateFromOldSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.CONSUMER_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.STATE_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.CREATED_TS_COLUMN )
            .append( " FROM " )
            .append( oldstatetbl )
            .append( " WHERE " )
            .append( TINTSTATE_CSTATE ).append( " <> " ).append( PartitionedStore.INTEREST_STATE_ACKNOWLEDGED )
            .toString();

        String insertStateSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( stateDAO.getTableName() )
            .append( " ( " )
            .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.CONSUMER_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.STATE_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN ).append( ", " )
            .append( ConsumerStateDAO.CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ? )" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates() && !dbMgr.isHADB();
        PreparedStatement pstmt = null;
	String mid = null;
	long cuid = 0;
	try {
	    pstmt = dbMgr.createPreparedStatement(conn, insertStateSQL);

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllStateFromOldSQL );
	    while (rs.next()) {
		mid = rs.getString(1);
		cuid = rs.getLong(2);
		int state = rs.getInt(3);
                long txnID = rs.getLong(4);
                long createdTS = rs.getLong(5);

		String dst = (String)msgToDst.get(mid);

		// ignore a state whose dst or message does not exists
		if (dst == null) {
                    continue;
		}

		try {
                    pstmt.setString( 1, mid );
                    pstmt.setLong( 2, cuid );
                    pstmt.setInt( 3, state );
                    if ( txnID > 0 ) {
                        pstmt.setLong( 4, txnID );
                    } else {
                        pstmt.setNull( 4, Types.BIGINT );
                    }
                    pstmt.setLong( 5, createdTS );

		    if ( dobatch ) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertStateSQL + "]", e);
		    throw ex;
		}
	    }

	    msgToDst.clear();

	    if ( dobatch ) {
		pstmt.executeBatch();
	    }
            conn.commit();
	} catch (SQLException e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_MESSAGES_FAILED,
                (mid == null ? "loading" : mid));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade transactions
     */
    private void upgradeTxns(Connection conn) throws BrokerException {

        TransactionDAO txnDAO = dbMgr.getDAOFactory().getTransactionDAO();

	// Non-HA table
	String oldtxntbl = TransactionDAO.TABLE_NAME_PREFIX + "S" + brokerID;

	// SQL to select all transactions from version Non_HA table
	String getAllTxnsFromOldSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TransactionDAO.ID_COLUMN ).append( ", " )
            .append( TransactionDAO.TYPE_COLUMN ).append( ", " )
            .append( TransactionDAO.STATE_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_STATE_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_HOME_BROKER_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_BROKERS_COLUMN ).append( ", " )
            .append( TransactionDAO.STORE_SESSION_ID_COLUMN )
            .append( " FROM " ).append(  oldtxntbl )
            .append( " WHERE " )
            .append( TransactionDAO.ID_COLUMN ).append( " NOT IN (SELECT " )
            .append( TransactionDAO.ID_COLUMN )
            .append( " FROM " ).append( txnDAO.getTableName() ).append(  ")" )
            .toString();

        // SQL to insert transactions to new table
	String insertTxnSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( txnDAO.getTableName() )
            .append( " ( " )
            .append( TransactionDAO.ID_COLUMN ).append( ", " )
            .append( TransactionDAO.TYPE_COLUMN ).append( ", " )
            .append( TransactionDAO.STATE_COLUMN ).append( ", " )
            .append( TransactionDAO.AUTO_ROLLBACK_COLUMN ).append( ", " )
            .append( TransactionDAO.XID_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_STATE_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_HOME_BROKER_COLUMN ).append( ", " )
            .append( TransactionDAO.TXN_BROKERS_COLUMN ).append( ", " )
            .append( TransactionDAO.STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( TransactionDAO.EXPIRED_TS_COLUMN ).append( ", " )
            .append( TransactionDAO.ACCESSED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates() && !dbMgr.isHADB();
	PreparedStatement pstmt = null;
	Statement stmt = null;
	ResultSet rs = null;
	TransactionUID tid  = null;
    Exception myex = null;
	try {
	    pstmt = dbMgr.createPreparedStatement(conn, insertTxnSQL);

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement(stmt, getAllTxnsFromOldSQL);
	    while (rs.next()) {
		long id = rs.getLong(1);
		tid = new TransactionUID(id);
                int type = rs.getInt(2);
                int state = rs.getInt(3);

                TransactionState txnState = (TransactionState)Util.readObject(rs, 4);
                txnState.setState(state);

                BrokerAddress txnHomeBroker = (BrokerAddress)Util.readObject(rs, 5);
                TransactionBroker[] txnBrokers = (TransactionBroker[])Util.readObject(rs, 6);

                long sessionID = rs.getLong(7);

		// insert in new table
		try {
                    pstmt.setLong( 1, id );
                    pstmt.setInt( 2, type );
                    pstmt.setInt( 3, state );
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

                    pstmt.setLong( 9, sessionID );
                    pstmt.setLong( 10, txnState.getExpirationTime() );
                    pstmt.setLong( 11, txnState.getLastAccessTime() );

		    if (dobatch) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (IOException e) {
		    IOException ex = DBManager.wrapIOException(
                        "[" + insertTxnSQL + "]", e);
		    throw ex;
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertTxnSQL + "]", e);
		    throw ex;
		}
	    }

	    if (dobatch) {
		pstmt.executeBatch();
	    }
	    conn.commit();
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_TRANSACTIONS_FAILED,
                (tid == null ? "loading" : tid.toString()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }
}


