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
 * @(#)DestinationDAOImpl.java	1.29 07/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic DestinationDAO.
 */
class DestinationDAOImpl extends BaseDAOImpl implements DestinationDAO {

    private final String tableName;

    // SQLs
    private String insertSQL;
    private String updateSQL;
    private String updateConnectedTimeSQL;
    private String deleteSQL;
    private String deleteBySessionSQL;
    private String deleteSharedDstSQL;
    private String selectSQL;
    private String selectConnectedTimeSQL;
    private String selectDstsByBrokerSQL;
    private String selectLocalDstsByBrokerSQL;
    private String selectExistSQL;

    /**
     * Constructor
     * @throws BrokerException
     */
    DestinationDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( DESTINATION_COLUMN ).append( ", " )
            .append( IS_LOCAL_COLUMN ).append( ", " )
            .append( CONNECTION_ID_COLUMN ).append( ", " )
            .append( CONNECTED_TS_COLUMN ).append( ", " )
            .append( STORE_SESSION_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ?, ? )" )
            .toString();

        updateSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( DESTINATION_COLUMN ).append( " = ?, " )
            .append( IS_LOCAL_COLUMN ).append( " = ?, " )
            .append( CONNECTION_ID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        updateConnectedTimeSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( CONNECTED_TS_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        deleteBySessionSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .append(   " AND " )
            .append( STORE_SESSION_ID_COLUMN ).append( " = ? " )
            .append(   " AND EXISTS (SELECT * FROM " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " )
            .append(   StoreSessionDAO.ID_COLUMN ).append( " = ? " )
            .append(   " AND " )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ? )" )
            .toString();

        deleteSharedDstSQL = new StringBuffer(128)
            .append( deleteSQL )
            .append( " AND NOT EXISTS (SELECT * FROM " )
            .append(   dbMgr.getTableName( MessageDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " )
            .append(   MessageDAO.DESTINATION_ID_COLUMN ).append( " = ?)" )
            .append( " AND NOT EXISTS (SELECT * FROM " )
            .append(   dbMgr.getTableName( BrokerDAO.TABLE_NAME_PREFIX ) )
            .append(   " WHERE " )
            .append(   BrokerDAO.ID_COLUMN ).append( " <> ? " )
            .append(   " AND " )
            .append(   BrokerDAO.STATE_COLUMN ).append( " = " )
            .append(   BrokerState.I_OPERATING ).append( ")" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( DESTINATION_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectConnectedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONNECTED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        StringBuffer tmpbuf = new StringBuffer(128)
            .append( "SELECT " )
            .append( DESTINATION_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN )
            .append( " IN (SELECT " ).append( ID_COLUMN );
        if (dbMgr.isUseDerivedTableForUnionSubQueries()) {
            tmpbuf
            .append(   " FROM " )
            .append( "(SELECT " ).append( ID_COLUMN );
        };
        tmpbuf
            .append(   " FROM " ).append( tableName )
            .append(   " WHERE " ).append( IS_LOCAL_COLUMN ).append( " = 0" )
            .append( " UNION SELECT dstTbl." ).append( ID_COLUMN )
            .append(   " FROM " ).append( tableName ).append( " dstTbl, " )
            .append(   dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append(   " sesTbl WHERE sesTbl." )
            .append(   StoreSessionDAO.BROKER_ID_COLUMN ).append( " = ?" )
            .append(   " AND sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(   " = dstTbl." ).append( STORE_SESSION_ID_COLUMN );
        if (dbMgr.isUseDerivedTableForUnionSubQueries()) {
            tmpbuf
            .append( ") tmptbl)" );
        } else {
            tmpbuf
            .append( ")" );
        }
        selectDstsByBrokerSQL = tmpbuf.toString();

        selectLocalDstsByBrokerSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( DESTINATION_COLUMN )
            .append( " FROM " ).append( tableName ).append( " dstTbl, " )
            .append( dbMgr.getTableName( StoreSessionDAO.TABLE_NAME_PREFIX ) )
            .append( " sesTbl WHERE " )
            .append( " sesTbl." ).append( StoreSessionDAO.BROKER_ID_COLUMN )
            .append(    " = ?" )
            .append( " AND " )
            .append( " sesTbl." ).append( StoreSessionDAO.ID_COLUMN )
            .append(    " = dstTbl." ).append( STORE_SESSION_ID_COLUMN )
            .toString();

        selectExistSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
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
     * @param destination the Destination
     * @param storeSessionID the store session ID
     * @param connectedTime timestamp
     * @param createdTime timestamp
     * @throws BrokerException if destination already exists in the store
     */
    public void insert( Connection conn, Destination destination,
        long storeSessionID, long connectedTime, long createdTime )
        throws BrokerException {

        String destName = destination.getUniqueName();

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

            if ( hasDestination( conn, destName) ) {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_DESTINATION_EXISTS_IN_STORE,
                    destName ) );
            }

            try {
                int isLocal = destination.getIsLocal() ? 1 : 0;
                if ( isLocal == 0 ) {
                    // non-local destination, i.e. cluster
                    storeSessionID = 0;
                }

                long connectionID = -1;
                if ( destination.isTemporary() ) {
                    // Store additional info for temp destination
                    ConnectionUID cUID = destination.getConnectionUID();
                    if ( cUID != null ) {
                        connectionID = cUID.longValue();
                        if ( connectedTime <= 0 ) {
                            connectedTime = System.currentTimeMillis();
                        }
                    }
                }

                pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
                pstmt.setString( 1, destName );
                Util.setObject( pstmt, 2, destination );
                pstmt.setInt( 3, isLocal );
                Util.setLong( pstmt, 4, connectionID );
                Util.setLong( pstmt, 5, connectedTime );
                Util.setLong( pstmt, 6, storeSessionID );
                pstmt.setLong( 7, createdTime );
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
                    br.getKString( BrokerResources.X_PERSIST_DESTINATION_FAILED,
                    destName ), ex );
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
     * Update existing entry.
     * @param conn database connection
     * @param destination the Destination
     * @throws BrokerException if destination does not exists in the store
     */
    public void update( Connection conn, Destination destination )
        throws BrokerException {

        String destName = destination.getUniqueName();

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

            int isLocal = destination.getIsLocal() ? 1 : 0;
            long connectionID = -1;
            if ( isLocal > 0 ) {
                // Store additional info for temp destination
                ConnectionUID cUID = destination.getConnectionUID();
                if ( cUID != null ) {
                    connectionID = cUID.longValue();
                }
            }

            pstmt = dbMgr.createPreparedStatement( conn, updateSQL );
            Util.setObject( pstmt, 1, destination );
            pstmt.setInt( 2, isLocal );
            Util.setLong( pstmt, 3, connectionID );
            pstmt.setString( 4, destName );

            if ( pstmt.executeUpdate() == 0 ) {
                // Otherwise we're assuming the entry does not exist
                throw new DestinationNotFoundException(
                    br.getKString( BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                    destName ), Status.NOT_FOUND );
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
                ex = DBManager.wrapIOException("[" + updateSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + updateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_DESTINATION_FAILED,
                destName ), ex );
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
     * @param destination the Destination
     * @throws BrokerException if destination does not exists in the store
     */
    public void updateConnectedTime( Connection conn, Destination destination,
        long connectedTime ) throws BrokerException {

        String destName = destination.getUniqueName();

        if ( !destination.getIsLocal() ) {
            // We've a problem, trying to update a non-local destination!
            throw new BrokerException(
                br.getKString( BrokerResources.E_UPDATE_NONLOCAL_DST_CONNECTED_TIME,
                    destName ) );
        }

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

            pstmt = dbMgr.createPreparedStatement( conn, updateConnectedTimeSQL );
            pstmt.setLong( 1, connectedTime );
            pstmt.setString( 2, destName );

            if ( pstmt.executeUpdate() == 0 ) {
                // Otherwise we're assuming the entry does not exist
                throw new DestinationNotFoundException(
                    br.getKString( BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                    destName ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + updateConnectedTimeSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_DESTINATION_FAILED,
                destName ), ex );
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
     * @param destination the Destination
     * @param storeSessionID null if for this entire broker
     * @return true if entry is deleted; false otherwise
     * @throws BrokerException
     */
    public boolean delete( Connection conn, Destination destination, Long storeSessionID )
        throws BrokerException {

        return delete( conn,
            destination.getDestinationUID(), destination.getType(), storeSessionID );
    }


    /**
     * Delete an existing entry.
     * @param conn Database Connection
     * @param dstUID the DestinationUID
     * @param type the type of destination
     * @return true if entry is deleted; false otherwise
     * @throws BrokerException
     */
    public boolean delete( Connection conn, DestinationUID dstUID, int type )
    throws BrokerException {
        return delete( conn, dstUID, type, null);
    }

    public boolean delete( Connection conn, DestinationUID dstUID,
        int type, Long storeSessionID ) throws BrokerException {

        boolean isDeleted = false;

        String destName = dstUID.toString();

        boolean myConn = false;
        String sql = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true;
            }

            // First remove all messages from this destination for current broker
            int msgCount = dbMgr.getDAOFactory().getMessageDAO().
                               deleteByDestinationBySession( conn, dstUID, storeSessionID );
            if ( msgCount > 0 ) {
                logger.log( Logger.WARNING, BrokerResources.W_REMOVING_DST_WITH_MSG,
                    String.valueOf(msgCount), dstUID );
            }

            // Now remove the destination
            if (storeSessionID != null) {
                sql = deleteBySessionSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, destName );
                pstmt.setLong( 2,  storeSessionID.longValue());
                pstmt.setLong( 3,  storeSessionID.longValue());
                pstmt.setString( 4, dbMgr.getBrokerID() );
            } if ( Globals.getHAEnabled() && DestType.isAutoCreated(type) &&
                   !DestType.isTemporary(type) ) {
                // Since auto-create destination are shared in HA mode,
                // delete only if there are no msgs for this destination
                sql = deleteSharedDstSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, destName );
                pstmt.setString( 2, destName );
                pstmt.setString( 3, dbMgr.getBrokerID() );
            } else {
                sql = deleteSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, destName );
            }

            int count = pstmt.executeUpdate();

            if ( myConn ) {
                conn.commit();
            }
            isDeleted = ( count > 0 ); // set return status
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
                br.getKString( BrokerResources.X_REMOVE_DESTINATION_FAILED,
                destName ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        return isDeleted;
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
            DBManager dbMgr = DBManager.getDBManager();

            // Only delete destinations that belong to the running broker,
            // construct the where clause for the delete statement:
            //   DELETE FROM mqdst41cmycluster
            //   WHERE EXISTS
            //     (SELECT id FROM mqses41cmycluster
            //      WHERE  id = mqdst41cmycluster.store_session_id AND
            //             broker_id = 'mybroker')
            whereClause = new StringBuffer(128)
                .append( "EXISTS (SELECT " ).append( StoreSessionDAO.ID_COLUMN )
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
     * Get a destination.
     * @param conn database connection
     * @param destName destination's name
     * @return Destination the Destination object
     * @throws BrokerException
     */
    public Destination getDestination( Connection conn, String destName )
        throws BrokerException {

        Destination dest = null;

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
            pstmt.setString( 1, destName );
            rs = pstmt.executeQuery();
            dest = (Destination)loadData( rs, true );
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
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_DESTINATION_FAILED,
                    destName ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return dest;
    }

    /**
     * Get connected timestamp for a local destination.
     * @param conn database connection
     * @param destName destination's name
     * @return connected timestamp
     * @throws BrokerException
     */
    public long getDestinationConnectedTime( Connection conn, String destName )
        throws BrokerException {

        long connectedTime = -1;

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

            pstmt = dbMgr.createPreparedStatement( conn, selectConnectedTimeSQL );
            pstmt.setString( 1, destName );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                connectedTime = rs.getLong( 1 );
            } else {
                throw new DestinationNotFoundException(
                    br.getKString( BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                    destName ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + selectConnectedTimeSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_DESTINATION_FAILED,
                    destName ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return connectedTime;
    }

    /**
     * Retrieve all non-local destinations and local destination for the
     * specified broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a List of Destination objects; an empty List is returned
     * if no destinations exist in the store
     */
    public List getAllDestinations( Connection conn, String brokerID )
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

            if ( brokerID == null ) {
                brokerID = dbMgr.getBrokerID();
            }

            pstmt = dbMgr.createPreparedStatement( conn, selectDstsByBrokerSQL );
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
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectDstsByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_DESTINATIONS_FAILED ), ex );
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
     * Retrieve all local destinations for the specified broker.
     * @param conn database connection
     * @param brokerID the broker ID
     * @return a List of Destination objects; an empty List is returned
     * if no destinations exist in the store
     */
    public List getLocalDestinationsByBroker( Connection conn, String brokerID )
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

            if ( brokerID == null ) {
                brokerID = dbMgr.getBrokerID();
            }

            // Retrieve all local destinations for the target broker
            pstmt = dbMgr.createPreparedStatement( conn, selectLocalDstsByBrokerSQL );
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
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectLocalDstsByBrokerSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.E_LOAD_DST_FOR_BROKER_FAILED,
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
     * Check whether the specified destination exists.
     * @param conn database connection
     * @param destName name of destination
     * @return return true if the specified destination exists
     */
    public boolean hasDestination( Connection conn, String destName ) throws BrokerException {

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
            pstmt.setString( 1, destName );
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
                ex = DBManager.wrapSQLException("[" + selectExistSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_JDBC_QUERY_FAILED,
                selectExistSQL ), ex );
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
     * Check whether the specified destination exists.
     * @param conn database connection
     * @param destName name of destination
     * @throws BrokerException if the destination does not exists in the store
     */
    public void checkDestination( Connection conn, String destName )
        throws BrokerException {

        if ( !hasDestination( conn, destName ) ) {
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"[checkDestination:"+destName+"]", rbe );
            }
            throw new DestinationNotFoundException(
                br.getKString( BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE,
                destName ), Status.NOT_FOUND );
        }
    }

    /**
     * Get debug information about the store.
     * @return a HashMap of name value pair of information
     * @param conn
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

        map.put( "Destinations(" + tableName + ")", String.valueOf( count ) );
        return map;
    }

    /**
     * Load a single destination or destinations from a ResultSet.
     * @param rs the ResultSet
     * @param isSingleRow specify interesed in only the 1st row of the ResultSet
     * @return a single Destination or List of Destinations
     * @throws SQLException
     */
    protected Object loadData( ResultSet rs, boolean isSingleRow )
        throws SQLException {

        ArrayList list = null;
        if ( !isSingleRow ) {
            list = new ArrayList( 100 );
        }

        while ( rs.next() ) {
            try {
                Destination dest = (Destination)Util.readObject( rs, 1 );
                if ( isSingleRow ) {
                    return dest;
                } else {
                    list.add( dest );
                }
            } catch ( Exception e ) {
                // fail to parse destination object; just log it
                logger.logStack( Logger.ERROR,
                    BrokerResources.X_PARSE_DESTINATION_FAILED, e );
            }
        }

        return list;
    }
}
