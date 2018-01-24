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
 * @(#)UpgradeStore.java	1.23 07/19/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
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
public class UpgradeStore implements DBConstants {

    private BrokerResources br = Globals.getBrokerResources();
    private Logger logger = Globals.getLogger();
    private JDBCStore store;
    private DBManager dbMgr;
    private String brokerID;
    private long storeSessionID;
    private int oldStoreVersion;
    private String oldVersionTable;
    private String oldPropTable;
    private String oldConfigRecordTable;
    private String oldDestTable;
    private String oldMsgTable;
    private String oldStateTable;
    private String oldInterestTable;
    private String oldTxnTable;
    private String oldAckTable;
    private boolean useBlob;
    
    private static boolean DEBUG = false;
    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) DEBUG = true;
    }

    UpgradeStore(JDBCStore jdbcStore, int oldVersion) throws BrokerException {
        store = jdbcStore;
        dbMgr = DBManager.getDBManager();
        brokerID = dbMgr.getBrokerID();
        oldStoreVersion = oldVersion;

        if (oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400) {
            useBlob = true;
            oldVersionTable = dbMgr.getTableName(
                VersionDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldPropTable = dbMgr.getTableName(
                PropertyDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldConfigRecordTable = dbMgr.getTableName(
                ConfigRecordDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldDestTable = dbMgr.getTableName(
                DestinationDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldMsgTable = dbMgr.getTableName(
                MessageDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldStateTable = dbMgr.getTableName(
                ConsumerStateDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldInterestTable = dbMgr.getTableName(
                ConsumerDAO.TABLE + DBConstants.SCHEMA_VERSION_40);
            oldTxnTable = dbMgr.getTableName(
                TransactionDAO.TABLE + DBConstants.SCHEMA_VERSION_40);;
        } else if (oldStoreVersion == JDBCStore.OLD_STORE_VERSION_370) {
            useBlob = true;
            oldVersionTable = VERSION_TBL_37 + brokerID;
            oldPropTable = PROPERTY_TBL_37 + brokerID;
            oldConfigRecordTable = CONFIGRECORD_TBL_37 + brokerID;
            oldDestTable = DESTINATION_TBL_37 + brokerID;
            oldMsgTable = MESSAGE_TBL_37 + brokerID;
            oldStateTable = INTEREST_STATE_TBL_37 + brokerID;
            oldInterestTable = INTEREST_TBL_37 + brokerID;
            oldTxnTable = TXN_TBL_37 + brokerID;
            oldAckTable = TXNACK_TBL_37 + brokerID;
        } else {
            useBlob = false;
            oldVersionTable = VERSION_TBL_35 + brokerID;
            oldPropTable = PROPERTY_TBL_35 + brokerID;
            oldConfigRecordTable = CONFIGRECORD_TBL_35 + brokerID;
            oldDestTable = DESTINATION_TBL_35 + brokerID;
            oldMsgTable = MESSAGE_TBL_35 + brokerID;
            oldStateTable = INTEREST_STATE_TBL_35 + brokerID;
            oldInterestTable = INTEREST_TBL_35 + brokerID;
            oldTxnTable = TXN_TBL_35 + brokerID;
            oldAckTable = TXNACK_TBL_35 + brokerID;
        }
    }

    void upgradeStore(Connection conn)
	throws BrokerException {

	// log informational messages
	Object[] args = {(Integer.valueOf(JDBCStore.STORE_VERSION))};
	logger.logToAll(Logger.INFO,
            BrokerResources.I_UPGRADE_JDBCSTORE_IN_PROGRESS, args);

	if (store.resetMessage()) {
	    // log message to remove old message
	    logger.logToAll(Logger.INFO,
                BrokerResources.I_RESET_MESSAGES_IN_OLD_STORE);
	    logger.logToAll(Logger.INFO,
                BrokerResources.I_UPGRADE_REMAINING_STORE_DATA);
	} else if (store.resetInterest()) {
	    // log message to remove old interest
	    logger.logToAll(Logger.INFO,
                BrokerResources.I_RESET_INTERESTS_IN_OLD_STORE);
	    logger.logToAll(Logger.INFO,
                BrokerResources.I_UPGRADE_REMAINING_STORE_DATA);
	}

	try {
	    // create the tables first
            DBTool.createTables( conn );

            // initialized the store session
            storeSessionID =
                dbMgr.getDAOFactory().getStoreSessionDAO().getStoreSession(
                    conn, brokerID );
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

            upgradeProperties(conn);
            upgradeChangeRecords(conn);
	    upgradeDestinations(conn);
	    upgradeMessages(conn);
	    upgradeInterests(conn);
	    upgradeTxns(conn);
            upgradeTxnAcks(conn);

	    logger.logToAll(Logger.INFO, BrokerResources.I_UPGRADE_STORE_DONE);

            // Now we can drop the version table
	    if (store.upgradeNoBackup()) {
                dropTable(conn, oldVersionTable);
	    } else {
		// log message about the old store
		logger.logToAll(Logger.INFO, BrokerResources.I_REMOVE_OLD_JDBCSTORE);
	    }
	} catch (Exception e) {
	    // upgrade failed; log message
	    logger.logToAll(Logger.ERROR, BrokerResources.I_REMOVE_NEW_JDBC_STORE);
	    try {
		// remove everything and return
		DBTool.dropTables(conn, dbMgr.getTableNames(JDBCStore.STORE_VERSION), 
                                  false, false, dbMgr);
	    } catch (SQLException ex) {
		logger.logStack(Logger.ERROR,
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Failed to clean up new tables after upgrade failed", ex);
            }

            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else {
                throw new BrokerException( br.getKString(
                    BrokerResources.E_UPGRADE_STORE_FAILED), e );
            }
	}
    }

    /**
     * Upgrade destinations from version 350/370/400 table to current format (410)
     */
    private void upgradeDestinations(Connection conn) throws BrokerException {

        DestinationDAO dstDAO = dbMgr.getDAOFactory().getDestinationDAO();

	// SQL to select all destination from version 350/370 table
        StringBuffer strBuf = new StringBuffer(128);
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( "SELECT " )
                .append( DestinationDAO.DESTINATION_COLUMN ).append( ", " )
                .append( DestinationDAO.CONNECTION_ID_COLUMN ).append( ", " )
                .append( DestinationDAO.CONNECTED_TS_COLUMN ).append( ", " )
                .append( DestinationDAO.CREATED_TS_COLUMN );
        } else {
            strBuf.append( "SELECT " ).append( TDEST_CDEST );
        }
        strBuf.append( " FROM " ).append( oldDestTable );

        String getAllDestFromOldSQL = strBuf.toString();

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

                String destName = dst.getUniqueName();
                int isLocal = dst.getIsLocal() ? 1 : 0;
                long connectionID = -1;
                long connectedTS = -1;
                long createdTS = System.currentTimeMillis();

                if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
                    connectionID = rs.getLong(2);
                    connectedTS = rs.getLong(3);
                    createdTS = rs.getLong(4);
                } else if ( isLocal > 0 ) {
                    // Store additional info for temp destination
                    ConnectionUID cUID = dst.getConnectionUID();
                    if ( cUID != null ) {
                        connectionID = cUID.longValue();
                        connectedTS = createdTS;
                    }
                }

		// put destinations in new table
		try {
                    pstmt.setString( 1, destName );
                    Util.setObject( pstmt, 2, dst );
                    pstmt.setInt( 3, isLocal );
                    Util.setLong( pstmt, 4, connectionID );
                    Util.setLong( pstmt, 5, connectedTS );
                    Util.setLong( pstmt, 6, storeSessionID );
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

	    if (store.upgradeNoBackup()) {
		dropTable(conn, oldDestTable);
	    }
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
     * Upgrade messages and the associated interest list from
     * version 350/370/400 tables to current format (410)
     */
    private void upgradeMessages(Connection conn) throws BrokerException {

	String sql = null;
	int nummsg = 0;

	// first find out how many messages we have in the old table
	Statement stmt = null;
        ResultSet rs = null;
    Exception myex = null;
	try {
	    sql = "SELECT COUNT(*) FROM " + oldMsgTable;
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

	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG,
                "number of messages in old table = " + nummsg);
	}

	boolean nobackup = store.upgradeNoBackup();
	if (nummsg == 0) {
	    // no message in old table just return;
	    if (nobackup) {
		// need to delete old tables
		dropTable(conn, oldMsgTable);
		dropTable(conn, oldStateTable);
	    } // else do nothing
	} else if (store.resetMessage()) {
	    if (nobackup) {
		// just delete old tables
		dropTable(conn, oldMsgTable);
		dropTable(conn, oldStateTable);
	    } else {
		// reset old tables, i.e., delete all entries
		clearTable(conn, oldMsgTable);
		clearTable(conn, oldStateTable);
	    }
	} else {
	    // print info message
            logger.logToAll(Logger.INFO, br.getString(
                BrokerResources.I_UPGRADING_MESSAGES, String.valueOf(nummsg)));

	    doUpgradeMsg(conn);

	    if (nobackup) {
		dropTable(conn, oldMsgTable);
		dropTable(conn, oldStateTable);
	    }
	}
    }

    private void doUpgradeMsg(Connection conn) throws BrokerException {

        MessageDAO msgDAO = dbMgr.getDAOFactory().getMessageDAO();

        HashMap oldIDToNewID = new HashMap();
        HashMap msgToDst = new HashMap();

	// SQL to select all messages from version 350/370 table
        StringBuffer strBuf = new StringBuffer(128);
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( "SELECT " )
                .append( MessageDAO.ID_COLUMN ).append( ", " )
                .append( MessageDAO.MESSAGE_COLUMN ).append( ", " )
                .append( MessageDAO.DESTINATION_ID_COLUMN ).append( ", " )
                .append( MessageDAO.CREATED_TS_COLUMN );
        } else {
            strBuf.append( "SELECT " )
                .append( TMSG_CMID ).append( ", " )
                .append( TMSG_CMSG ).append( ", " )
                .append( TMSG_CDID ).append( ", " )
                .append( 0 );
        }
        strBuf.append( " FROM " ).append( oldMsgTable );

        String getAllMsgFromOldSQL = strBuf.toString();

	Statement stmt = null;
	ResultSet rs = null;
	Packet msg = null;
        String oldMsgID = null;
    Exception myex = null;
	try {
	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllMsgFromOldSQL );
	    while (rs.next()) {
                oldMsgID = rs.getString(1);

		msg = new Packet(false);
		msg.generateTimestamp(false);
		msg.generateSequenceNumber(false);
		InputStream is = useBlob ?
                    rs.getBlob(2).getBinaryStream() : rs.getBinaryStream(2);
		msg.readPacket(is);
		is.close();

                SysMessageID sysMsgID = msg.getSysMessageID();

                String dstID = rs.getString(3);
                long createdTS = createdTS = rs.getLong(4);

                if ( createdTS == 0 ) {
                    createdTS = sysMsgID.getTimestamp();
                }

                msgDAO.insert( conn, dstID, msg, null, null,
                    storeSessionID, createdTS, false, false );

                String newMsgID = sysMsgID.toString();
                oldIDToNewID.put(oldMsgID, newMsgID);
                msgToDst.put(newMsgID, dstID);
	    }
	} catch (Exception e) {
        myex =e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_MESSAGES_FAILED,
                (msg == null ? oldMsgID : msg.getSysMessageID().toString()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
        }

	// upgrade interest list
        ConsumerStateDAO stateDAO = dbMgr.getDAOFactory().getConsumerStateDAO();

	// SQL to select all interest states from version 350/370/400 table
        strBuf = new StringBuffer(128);
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( "SELECT " )
                .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( ", " )
                .append( ConsumerStateDAO.CONSUMER_ID_COLUMN ).append( ", " )
                .append( ConsumerStateDAO.STATE_COLUMN ).append( ", " )
                .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN ).append( ", " )
                .append( ConsumerStateDAO.CREATED_TS_COLUMN );
        } else {
            strBuf.append( "SELECT " )
                .append( TINTSTATE_CMID ).append( ", " )
                .append( TINTSTATE_CCUID ).append( ", " )
                .append( TINTSTATE_CSTATE );
        }
        strBuf.append( " FROM " ).append( oldStateTable )
            .append( " WHERE " ).append( TINTSTATE_CSTATE ).append( " <> " )
            .append( PartitionedStore.INTEREST_STATE_ACKNOWLEDGED );

        String getAllStateFromOldSQL = strBuf.toString();

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
	long cuid = 0;
        oldMsgID = null;
	try {
	    pstmt = dbMgr.createPreparedStatement(conn, insertStateSQL);

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllStateFromOldSQL );
	    while (rs.next()) {
		oldMsgID = rs.getString(1);
		cuid = rs.getLong(2);
		int state = rs.getInt(3);

                String newMsgID = (String)oldIDToNewID.get(oldMsgID);
                if (newMsgID == null) {
                    String errorMsg = br.getKString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Unable to update message ID format of interest " +
                        cuid + " for message " + oldMsgID);
                    throw new BrokerException(errorMsg);
                }

                String dst = (String)msgToDst.get(newMsgID);

		// ignore a state whose dst or message does not exists
		if (dst == null) {
                    logger.log(Logger.WARNING,
                        "Destination not found: ignore state of interest " +
                        cuid + " for message " + oldMsgID  );
                    continue;
		}

                long txnID = -1;
                long createdTS = System.currentTimeMillis();

                if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
                    txnID = rs.getLong(4);
                    if ( rs.wasNull() ) txnID = -1;
                    createdTS = rs.getLong(5);
                }

		try {
                    pstmt.setString( 1, newMsgID );
                    pstmt.setLong( 2, cuid );
                    pstmt.setInt( 3, state );
                    Util.setLong( pstmt, 4, txnID );
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
                (oldMsgID == null ? "loading" : oldMsgID));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade interests from version 350/370/400 table to current format (410)
     */
    private void upgradeInterests(Connection conn) throws BrokerException {

	String sql = "SELECT COUNT(*) FROM " + oldInterestTable;
	int numint = 0;

	// first find out how many interest we have in the old table
	Statement stmt = null;
        ResultSet rs = null;
    Exception myex = null;
	try {
	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement(stmt, sql);
	    if (rs.next()) {
		numint = rs.getInt(1);
	    }
	} catch (SQLException e) {
        myex = e;
	    logger.log(Logger.ERROR, BrokerResources.X_JDBC_QUERY_FAILED, sql, e);
	    throw new BrokerException(
                br.getString(BrokerResources.X_JDBC_QUERY_FAILED, sql), e);
	} finally {
            Util.close( rs, stmt, null, myex );
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.DEBUG,
                "number of interests in old table = " + numint);
	}

	boolean nobackup = store.upgradeNoBackup();
	if (numint == 0) {
	    // no interests in old table
	    if (nobackup) {
		// need to drop old table
		dropTable(conn, oldInterestTable);
	    } // else do nothing
	} else if (store.resetInterest()) {
	    if (nobackup) {
		// just delete old table
		dropTable(conn, oldInterestTable);
	    } else {
		// reset old table, i.e., delete all entries
		clearTable(conn, oldInterestTable);
	    }
	} else {
	    // upgrade
	    doUpgradeInterests(store, conn);
	}
    }

    private void doUpgradeInterests(JDBCStore store, Connection conn)
	throws BrokerException {

        ConsumerDAO conDAO = dbMgr.getDAOFactory().getConsumerDAO();

	// SQL to select all interest from version 350/370/400 table
        StringBuffer strBuf = new StringBuffer(128);
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( "SELECT " )
                .append( ConsumerDAO.CONSUMER_COLUMN ).append( ", " )
                .append( ConsumerDAO.CREATED_TS_COLUMN );
        } else {
            strBuf.append( "SELECT " )
                .append( TINT_CINTEREST );
        }
        strBuf.append( " FROM " ).append( oldInterestTable );

        String getAllInterestFromOldSQL = strBuf.toString();

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

                String durableName = null;
                String clientID = null;
                if ( consumer instanceof Subscription ) {
                    Subscription sub = (Subscription)consumer;
                    durableName = sub.getDurableName();
                    clientID = sub.getClientID();
                }

                long createdTS = System.currentTimeMillis();
                if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
                    createdTS = rs.getLong(2);
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

	    if (store.upgradeNoBackup()) {
		// drop old table
		dropTable(conn, oldInterestTable);
	    }
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
     * Upgrade transactions from version 350/370/400 table to current format (410)
     */
    private void upgradeTxns(Connection conn) throws BrokerException {

        TransactionDAO txnDAO = dbMgr.getDAOFactory().getTransactionDAO();

	// SQL to select all transactions from version 350/370 table
        StringBuffer strBuf = new StringBuffer(128);
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( "SELECT " )
                .append( TransactionDAO.ID_COLUMN ).append( ", " )
                .append( TransactionDAO.STATE_COLUMN ).append( ", " )
                .append( TransactionDAO.TXN_STATE_COLUMN );
        } else {
            strBuf.append( "SELECT " )
                .append( TTXN_CTUID ).append( ", " )
                .append( TTXN_CSTATE ).append( ", " )
                .append( TTXN_CSTATEOBJ );
        }
        strBuf.append( " FROM " ).append( oldTxnTable )
            .append( " WHERE " ).append( TTXN_CSTATE ).append( " <> " )
            .append( DBConstants.TXN_DELETED );

        String getAllTxnsFromOldSQL = strBuf.toString();

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
		int state = rs.getInt(2);
		
		TransactionState txnState = (TransactionState)Util.readObject(rs, 3);
		if(DEBUG)
		{
			String msg = "reading transaction from old format: state="+state+
			" txnState = "+txnState;
			logger.log(Logger.DEBUG, msg);
		}
		txnState.setState(state);

		// insert in new table
		try {
                    pstmt.setLong( 1, id );
                    pstmt.setInt( 2, TransactionInfo.TXN_LOCAL );
                    pstmt.setInt( 3, state );
                    pstmt.setInt( 4, txnState.getType().intValue() );

                    JMQXid jmqXid = txnState.getXid();
                    if ( jmqXid != null ) {
                        pstmt.setString( 5, jmqXid.toString() );
                    } else {
                        pstmt.setNull( 5, Types.VARCHAR );
                    }

                    Util.setObject( pstmt, 6, txnState );
                    Util.setObject( pstmt, 7, null );
                    Util.setObject( pstmt, 8, null );                    

                    pstmt.setLong( 9, storeSessionID );
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

            // Only delete txn table after we upgrade txn ack!!!
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

    /**
     * Upgrade transaction acknowledgements from version 350/370 table
     * to current format (410)
     */
    private void upgradeTxnAcks(Connection conn) throws BrokerException {

        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            // In 400, ack table has been folded into consumer state table
            return;
        }

        ConsumerStateDAO stateDAO = dbMgr.getDAOFactory().getConsumerStateDAO();

	// SQL to select all acknowledgements from version 350/370 table
	String getAllTxnAcksFromOldSQL =
            "SELECT atbl." + TTXNACK_CTUID + ", " + TTXNACK_CACK +
            " FROM " + oldTxnTable + " ttbl, " + oldAckTable + " atbl" +
            " WHERE ttbl." + TTXN_CTUID + " = atbl." + TTXNACK_CTUID;

        // SQL to insert acknowledgements to consumer state table;
        // for 400, txn ack is represent as a column in consumer state table.
        String insertTxnAckSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( stateDAO.getTableName() )
            .append( " SET " )
            .append( ConsumerStateDAO.TRANSACTION_ID_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( ConsumerStateDAO.CONSUMER_ID_COLUMN ).append( " = ?" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates() && !dbMgr.isHADB();
	PreparedStatement pstmt = null;
	Statement stmt = null;
	ResultSet rs = null;
	TransactionUID tid  = null;
    Exception myex = null;
	try {
	    pstmt = dbMgr.createPreparedStatement(conn, insertTxnAckSQL);

            stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement(stmt, getAllTxnAcksFromOldSQL);
	    while (rs.next()) {
		long id = rs.getLong(1);
		tid = new TransactionUID(id);
		TransactionAcknowledgement ack =
                    (TransactionAcknowledgement)Util.readObject(rs, 2);

		// insert in new table
		try {
                    pstmt.setLong( 1, id );
                    pstmt.setString( 2, ack.getSysMessageID().toString() );
                    pstmt.setLong( 3, ack.getStoredConsumerUID().longValue() );

		    if (dobatch) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertTxnAckSQL + "]", e);
		    throw ex;
		}
	    }

	    if (dobatch) {
		pstmt.executeBatch();
	    }
	    conn.commit();

	    if (store.upgradeNoBackup()) {
                dropTable(conn, oldTxnTable);
                dropTable(conn, oldAckTable);
	    }
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_TXNACK_FAILED,
                (tid == null ? "loading" : tid.toString() ) );
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade configuration records from version 350/370/400 table to
     * current format (410)
     */
    private void upgradeChangeRecords(Connection conn) throws BrokerException {

        ConfigRecordDAO recordDAO = dbMgr.getDAOFactory().getConfigRecordDAO();

	// SQL to select all ConfigRecord from version 350/370/400 table
	StringBuffer strBuf = new StringBuffer(128)
            .append( "SELECT " ).append( TCONFIG_CRECORD ).append( ", " );
        if ( oldStoreVersion == JDBCStore.OLD_STORE_VERSION_400 ) {
            strBuf.append( ConfigRecordDAO.CREATED_TS_COLUMN );
        } else {
            strBuf.append( TCONFIG_CTIME );
        }
        strBuf.append( " FROM " ).append( oldConfigRecordTable );

        String getAllRecordFromOldSQL = strBuf.toString();

        // SQL to insert ConfigRecord to new table
        String insertRecordSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( recordDAO.getTableName() )
            .append( " ( " )
            .append( ConfigRecordDAO.RECORD_COLUMN ).append( ", " )
            .append( ConfigRecordDAO.CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ? )" )
            .toString();

	PreparedStatement pstmt = null;
	Statement stmt = null;
	ResultSet rs = null;
	Long recordTS = null;
    Exception myex = null;
	try {
            pstmt = dbMgr.createPreparedStatement( conn, insertRecordSQL );

	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllRecordFromOldSQL );
	    while (rs.next()) {
		byte[] rec  = Util.readBytes(rs, 1);
                long ts = rs.getLong(2);
		recordTS = Long.valueOf( ts );

		// insert in new table
		try {
                    Util.setBytes( pstmt, 1, rec );
                    pstmt.setLong( 2, ts );

                    pstmt.executeUpdate();
		} catch (IOException e) {
		    IOException ex = DBManager.wrapIOException(
                        "[" + insertRecordSQL + "]", e);
		    throw ex;
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertRecordSQL + "]", e);
		    throw ex;
		}
	    }

	    conn.commit();

	    if (store.upgradeNoBackup()) {
		dropTable(conn, oldConfigRecordTable);
	    }
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_CRECORDS_FAILED,
                (recordTS == null ? "loading" : recordTS.toString()));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
	} finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    /**
     * Upgrade properties from version 350/370/400 table to current format (410)
     */
    private void upgradeProperties(Connection conn) throws BrokerException {

        PropertyDAO propDAO = dbMgr.getDAOFactory().getPropertyDAO();

	// SQL to select all property from version 350/370/400 table
	String getAllPropFromOldSQL =
            "SELECT " + TPROP_CNAME + ", " + TPROP_CVALUE + " FROM " + oldPropTable;

        // SQL to insert property to new table
        String insertPropSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( propDAO.getTableName() )
            .append( " ( " )
            .append( PropertyDAO.PROPNAME_COLUMN ).append( ", " )
            .append( PropertyDAO.PROPVALUE_COLUMN )
            .append( ") VALUES ( ?, ? )" )
            .toString();

        boolean dobatch = dbMgr.supportsBatchUpdates();
	Statement stmt = null;
	PreparedStatement pstmt = null;
	ResultSet rs = null;
	String name = null;
    Exception myex = null;
	try {
            pstmt = dbMgr.createPreparedStatement( conn, insertPropSQL );

	    stmt = conn.createStatement();
	    rs = dbMgr.executeQueryStatement( stmt, getAllPropFromOldSQL );
	    while (rs.next()) {
		name = rs.getString(1);
		Object value = Util.readObject(rs, 2);

		// insert in new table
		try {
                    pstmt.setString( 1, name );
                    Util.setObject( pstmt, 2, value );

		    if ( dobatch ) {
			pstmt.addBatch();
		    } else {
			pstmt.executeUpdate();
		    }
		} catch (IOException e) {
		    IOException ex = DBManager.wrapIOException(
                        "[" + insertPropSQL + "]", e);
		    throw ex;
		} catch (SQLException e) {
		    SQLException ex = DBManager.wrapSQLException(
                        "[" + insertPropSQL + "]", e);
		    throw ex;
		}
	    }

	    if ( dobatch ) {
		pstmt.executeBatch();
	    }
	    conn.commit();

	    if (store.upgradeNoBackup()) {
		dropTable(conn, oldPropTable);
	    }
	} catch (Exception e) {
        myex = e;
            String errorMsg = br.getKString(
                BrokerResources.X_JDBC_UPGRADE_PROPERTIES_FAILED,
                (name == null ? "loading" : name));
	    logger.logStack(Logger.ERROR, errorMsg, e);
	    throw new BrokerException(errorMsg, e);
        } finally {
            Util.close( rs, stmt, null, myex );
            Util.close( null, pstmt, null, myex );
        }
    }

    private void dropTable(Connection conn, String table)
        throws BrokerException {

	String sql = "DROP TABLE " + table;

        Statement stmt = null;
    Exception myex = null;
	try {
	    stmt = conn.createStatement();
	    dbMgr.executeUpdateStatement(stmt, sql);
	    conn.commit();

	    if (Store.getDEBUG()) {
		Globals.getLogger().log(Logger.DEBUG, "Dropped table " + table);
	    }
	} catch (SQLException e) {
        myex = e;
	    Globals.getLogger().log(Logger.ERROR,
                BrokerResources.I_DROP_TABLE_FAILED, table,
                DBManager.wrapSQLException("[" + sql + "]", e));
	} finally {
            Util.close( null, stmt, null, myex );
        }
    }

    private void clearTable(Connection conn, String table)
	throws BrokerException {

	String sql = "DELETE FROM " + table;

        Statement stmt = null;
    Exception myex = null;
	try {
	    stmt = conn.createStatement();
	    int numdeleted = dbMgr.executeUpdateStatement(stmt, sql);
	    conn.commit();

	    if (Store.getDEBUG()) {
		Globals.getLogger().log(Logger.DEBUG, "Deleted " + numdeleted +
                    " entries in " + table);
	    }
	} catch (SQLException e) {
        myex = e;
            SQLException ex = DBManager.wrapSQLException("[" + sql + "]", e);
            String errorMsg = Globals.getBrokerResources().getString(
                    BrokerResources.X_JDBC_CLEAR_TABLE_FAILED, table);
	    Globals.getLogger().log(Logger.ERROR, errorMsg, ex);
	    throw new BrokerException(errorMsg, ex);
	} finally {
            Util.close( null, stmt, null, myex );
        }
    }
}


