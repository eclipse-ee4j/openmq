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
 * @(#)ConfigRecordDAOImpl.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic ConfigRecordDAO.
 */
class ConfigRecordDAOImpl extends BaseDAOImpl implements ConfigRecordDAO {

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String selectRecordsSinceSQL;
    private final String selectAllSQL;

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    ConfigRecordDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ? )" )
            .toString();

        selectRecordsSinceSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( CREATED_TS_COLUMN ).append( " > ?" )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
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
     * @param recordData the record data
     * @param timeStamp
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void insert( Connection conn, byte[] recordData, long timeStamp )
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

            pstmt = dbMgr.createPreparedStatement( conn, insertSQL );
            Util.setBytes( pstmt, 1, recordData );
            pstmt.setLong( 2, timeStamp );

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
                ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_CONFIGRECORD_FAILED, 
                    String.valueOf(timeStamp) ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Get all records created since the specified timestamp.
     * @param conn database connection
     * @param timestamp the timestamp
     * @return a List of records.
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public List<ChangeRecordInfo> getRecordsSince( Connection conn, long timestamp )
        throws BrokerException {

        ArrayList records = new ArrayList();

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

            pstmt = dbMgr.createPreparedStatement( conn, selectRecordsSinceSQL );
            pstmt.setLong( 1, timestamp );
            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                try {
                    byte[] buf = Util.readBytes( rs, 1 );
                    records.add( new ChangeRecordInfo(buf, 0) );
                } catch (IOException e) {
                    // fail to load one record; just log the record TS
                    IOException ex = DBManager.wrapIOException(
                        "[" + selectRecordsSinceSQL + "]", e );
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_CONFIGRECORD_FAILED,
                        rs.getString( 2 ), ex);
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
                ex = DBManager.wrapSQLException("[" + selectRecordsSinceSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_CONFIGRECORDS_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return records;
    }

    /**
     * Return all records together with their corresponding timestamps.
     * @param conn database connection
     * @return a list of ChangeRecordInfo
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public List<ChangeRecordInfo> getAllRecords( Connection conn ) throws BrokerException {

        ArrayList records = new ArrayList();

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
                long createdTS = -1;
                try {
                    createdTS = rs.getLong( 2 );
                    byte[] buf = Util.readBytes( rs, 1 );
                    records.add( new ChangeRecordInfo(buf, createdTS) );
                } catch (IOException e) {
                    // fail to load one record; just log the record TS
                    IOException ex = DBManager.wrapIOException(
                        "[" + selectAllSQL + "]", e );
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_CONFIGRECORD_FAILED,
                        String.valueOf( createdTS ), ex);
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
                br.getKString( BrokerResources.X_LOAD_CONFIGRECORDS_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return records;
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

        map.put( "Config Change Records(" + tableName + ")", String.valueOf( count ) );
        return map;
    }
}
