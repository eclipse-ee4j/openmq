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
 * @(#)ConsumerDAOImpl.java	1.16 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic ConsumerDAO.
 */
class ConsumerDAOImpl extends BaseDAOImpl implements ConsumerDAO {

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String insertNoDupSQL;
    private final String deleteSQL;
    private final String selectSQL;
    private final String selectAllSQL;
    private final String selectExistSQL;
    private final String selectExistByIDSQL;

    /**
     * Constructor
     * @throws BrokerException
     */
    ConsumerDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( CONSUMER_COLUMN ).append( ", " )
            .append( DURABLE_NAME_COLUMN ).append( ", " )
            .append( CLIENT_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ? )" )
            .toString();

        /*
        insertNoDupSQLDual = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( CONSUMER_COLUMN ).append( ", " )
            .append( DURABLE_NAME_COLUMN ).append( ", " )
            .append( CLIENT_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " ) SELECT ?, ?, ?, ?, ? FROM DUAL " )
            .append( " WHERE NOT EXISTS ").append( "(SELECT 1 FROM " ).append( tableName )
            .append( " WHERE ").append( DURABLE_NAME_COLUMN ).append(" = ? ") 
            .append( " AND ").append( CLIENT_ID_COLUMN ).append(" = ? )" ) 
            .toString();
        */

        insertNoDupSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( CONSUMER_COLUMN ).append( ", " )
            .append( DURABLE_NAME_COLUMN ).append( ", " )
            .append( CLIENT_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " ) SELECT ?, ?, ?, ?, ? FROM " ).append( tableName )
            .append( " WHERE ").append( DURABLE_NAME_COLUMN ).append(" = ? ") 
            .append( " AND ").append( CLIENT_ID_COLUMN ).append(" = ? " ) 
            .append( " HAVING COUNT(*) ").append( " = 0 " ) 
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        selectExistSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( DURABLE_NAME_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CLIENT_ID_COLUMN ).append( " = ?" )
            .toString();

        selectExistByIDSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
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
    public String getTableName() {
        return tableName;
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param consumer the Consumer
     * @param createdTS timestamp
     * @throws BrokerException if entry exists in the store already
     */
    public void insert( Connection conn, Consumer consumer, long createdTS )
        throws BrokerException {

        ConsumerUID consumerUID = consumer.getConsumerUID();
        String durableName = null;
        String clientID = null;
        if ( consumer instanceof Subscription ) {
            Subscription sub = (Subscription)consumer;
            durableName = sub.getDurableName();
            clientID = sub.getClientID();
        }

        boolean myConn = false;
        String sql = insertSQL;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            Consumer tmpc = checkConsumer( conn, consumer, true ); 
            if (tmpc != null) { 
                throwConflictException(tmpc, consumer);
            }
            if ( durableName != null) {
                tmpc = checkConsumer( conn, consumer, false ); 
                if (tmpc != null) { 
                    throwConflictException(tmpc, consumer);
                }
            }
            if (durableName != null && !dbMgr.isHADB() && !dbMgr.isDB2()) {
                sql = insertNoDupSQL;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            pstmt.setLong( 1, consumerUID.longValue() );
            Util.setObject( pstmt, 2, consumer );
            Util.setString( pstmt, 3, durableName );
            Util.setString( pstmt, 4, clientID, false );
            pstmt.setLong( 5, createdTS );
            if (durableName != null && !dbMgr.isHADB() && !dbMgr.isDB2()) {
                Util.setString( pstmt, 6, durableName );
                Util.setString( pstmt, 7, clientID, false );
            }
            if (pstmt.executeUpdate() == 0) {
                tmpc = checkConsumer( conn, consumer, true ); 
                if (tmpc != null) {
                    throwConflictException(tmpc, consumer);
                }
                if ( durableName != null) { 
                    tmpc = checkConsumer( conn, consumer, false ); 
                    if (tmpc != null) {
                        throwConflictException(tmpc, consumer);
                    }
                }
                throw new BrokerException(br.getKString(
                    BrokerResources.X_PERSIST_INTEREST_FAILED, consumerUID));
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
                ex = DBManager.wrapIOException("[" + sql + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    private void throwConflictException(Consumer existc, Consumer c)
    throws BrokerException {
        if (existc instanceof Subscription && 
            c instanceof Subscription) {
            Subscription existsub = (Subscription)existc;
            Subscription sub = (Subscription)c;

            if (existsub.getShared() != sub.getShared() ||
                existsub.getJMSShared() != sub.getJMSShared()) {
                throw new BrokerException(
                    br.getKString(BrokerResources.X_DURABLE_SUB_EXIST_IN_STORE_ALREADY,
                   "["+existsub.getDSubLongLogString()+"]"+existsub, 
                    existsub.getDestinationUID()), Status.CONFLICT );
            }
            throw new ConsumerAlreadyAddedException(
                br.getKString(BrokerResources.X_DURABLE_SUB_EXIST_IN_STORE_ALREADY,
                "["+existsub.getDSubLongLogString()+"]"+existsub, 
                 existsub.getDestinationUID()));
        }
        throw new ConsumerAlreadyAddedException(
            br.getKString(BrokerResources.E_INTEREST_EXISTS_IN_STORE,
            "["+existc+"]", existc.getDestinationUID()));
    }

    /**
     * Delete an existing entry.
     * @param conn database connection
     * @param consumer the Consumer
     * @throws BrokerException if entry does not exists in the store
     */
    public void delete( Connection conn, Consumer consumer )
        throws BrokerException {

        ConsumerUID consumerUID = consumer.getConsumerUID();

        boolean deleted = false;
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
            pstmt.setLong( 1, consumerUID.longValue() );
            if ( pstmt.executeUpdate() > 0 ) {
                deleted = true;
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

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        if ( !deleted ) {
            DestinationUID destinationUID = consumer.getDestinationUID();
            throw new BrokerException(
                br.getKString( BrokerResources.E_INTEREST_NOT_FOUND_IN_STORE,
                consumerUID, destinationUID ), Status.NOT_FOUND );
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
            return; // Share table cannot be reset    
        } else {
            super.deleteAll( conn );
        }
    }

    /**
     * Get a Consumer.
     * @param conn database connection
     * @param consumerUID the consumer ID
     * @return Consumer
     * @throws BrokerException
     */
    public Consumer getConsumer( Connection conn, ConsumerUID consumerUID )
        throws BrokerException {

        Consumer consumer = null;

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
            pstmt.setLong( 1, consumerUID.longValue() );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                try {
                    consumer = (Consumer)Util.readObject( rs, 1 );
                } catch ( IOException e ) {
                    // fail to parse consumer object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_INTEREST_FAILED, e );
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
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_RETRIEVE_INTEREST_FAILED,
                    consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return consumer;
    }

    /**
     * Retrieve all consumers in the store.
     * @param conn database connection
     * @return a List of Consumer objects; an empty List is returned
     * if no consumers exist in the store
     */
    public List getAllConsumers( Connection conn ) throws BrokerException {

        ArrayList list = new ArrayList();

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
                try {
                    Consumer consumer = (Consumer)Util.readObject( rs, 1 );
                    list.add( consumer );
                } catch ( IOException e ) {
                    // fail to parse consumer object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_INTEREST_FAILED, e );
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
                br.getKString( BrokerResources.X_LOAD_INTERESTS_FAILED ), ex );
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
     * Check whether the specified consumer exists.
     * @param conn database connection
     * @param consumer the Consumer
     * @return return true if the specified consumer exists
     * @throws BrokerException
     */
    private Consumer checkConsumer( Connection conn, Consumer consumer, boolean byId  )
    throws BrokerException {

        boolean found = false;
        ConsumerUID consumerUID = consumer.getConsumerUID();

        boolean myConn = false;
        String sql = selectExistByIDSQL;
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
            if (!byId) {
                sql = selectExistSQL;
            }

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            if (byId) {
                pstmt.setLong( 1, consumerUID.longValue() );
            } else {
                Util.setString( pstmt, 1, ((Subscription)consumer).getDurableName() );
                Util.setString( pstmt, 2, ((Subscription)consumer).getClientID(), false );
            }
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                 Consumer c = (Consumer)Util.readObject( rs, 1 );
                 return c;
            }
            return null;
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
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + sql + "]", (IOException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_RETRIEVE_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
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

        map.put( "Consumers(" + tableName + ")", String.valueOf( count ) );
        return map;
    }
}
