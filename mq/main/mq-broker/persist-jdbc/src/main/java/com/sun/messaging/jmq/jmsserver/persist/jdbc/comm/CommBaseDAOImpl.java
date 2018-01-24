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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.util.log.Logger;

import java.sql.*;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;

/**
 * A common DAO base class, used by different JDBC stores, 
 * provides methods for creating and dropping table.
 *
 * Do not reference a particular store's DB manager in this class
 *
 */
public abstract class CommBaseDAOImpl implements BaseDAO {

    protected static boolean DEBUG = false;

    public Logger logger = Globals.getLogger();
    public BrokerResources br = Globals.getBrokerResources();
    protected BrokerConfig config = Globals.getConfig();

    protected abstract CommDBManager
    getDBManager() throws BrokerException;

    protected abstract void 
    closeSQLObjects( ResultSet rs, Statement stmt, 
                     Connection conn, Throwable ex )
                     throws BrokerException;

    /**
     * Get row count.
     * @param conn database connection
     * @param whereClause the where clause for the SQL command
     * @return the number of rows in a query
     */
    public int getRowCount( Connection conn, String whereClause )
        throws BrokerException {

        int count = -1;

        String sql = new StringBuffer(128)
            .append( "SELECT COUNT(*) FROM " )
            .append( getTableName() )
            .append( (whereClause != null && whereClause.length() > 0)
                     ? " WHERE " + whereClause : "" )
            .toString();

        boolean myConn = false;
        Statement stmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager dbMgr = getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            stmt = conn.createStatement();
            rs = dbMgr.executeQueryStatement( stmt, sql );

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
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = CommDBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_JDBC_QUERY_FAILED, sql ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, stmt, conn, myex );
            } else {
                closeSQLObjects( rs, stmt, null, myex );
            }
        }

        return count;
    }

    /**
     * Create the table.
     * @param conn database connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void createTable( Connection conn ) throws BrokerException {

        String tableName = getTableName();
        logger.logToAll( Logger.INFO,
            br.getString( BrokerResources.I_CREATE_TABLE, tableName ) );

        // Make sure table definition is specified
        CommDBManager dbMgr = getDBManager();
        TableSchema tableSchema = dbMgr.getTableSchema( tableName );

        boolean myConn = false;
        Statement stmt = null;
        String sql = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            // Create the table
            stmt = conn.createStatement();

            sql = tableSchema.tableSQL;
            dbMgr.executeUpdateStatement( stmt, sql );
            if (stmt.getWarnings() != null) {
                String emsg = "["+sql+"]: "+stmt.getWarnings();
                if (config.getBooleanProperty(dbMgr.getJDBCPropPrefix() +
                                              ".exitOnCreateTableWarning",
                    (Globals.getHAEnabled() && dbMgr.isMysql()))) {
                    logger.log(logger.ERROR, emsg);
                    try {
                        dropTable( conn );
                    } catch (Exception e) {
                        logger.log(logger.WARNING, e.toString());
                    }
                    throw new BrokerException(emsg);

                }
                logger.log(logger.WARNING, emsg);
            }

            //any supplement statement for create table 
            try {
                if ( dbMgr.hasSupplementForCreateDrop(tableName) ) {
                     dbMgr.dropTableSupplement(stmt, tableSchema, tableName, false);
                     dbMgr.createTableSupplement(stmt, tableSchema, tableName);
                }
            } catch (Throwable t) {
                try {
                    dropTable( conn );
                } catch (Throwable tt) {
                    logger.log(logger.WARNING, tt.toString());
                }
                if (t instanceof BrokerException) {
                    throw (BrokerException)t;
                }
                throw new BrokerException(t.getMessage(), t);
            }

            // Create the table index if any
            boolean createIndex = true;

            if ( dbMgr.isHADB() ) {
                // Create the table's index for HADB only if it is enabled;
                // currently we don't use index because of HADB bug 6489632
                // which has been fixed in 4.6.2
                createIndex = config.getBooleanProperty(
                    dbMgr.getJDBCPropPrefix() + ".hadb.tableIndex.enabled", false);
            }

            if ( createIndex ) {
                Iterator itr = tableSchema.indexIterator();
                while ( itr.hasNext() ) {
                    String indexName = (String)itr.next();
                    sql = tableSchema.getIndex( indexName );

                    logger.logToAll( Logger.INFO,
                        br.getString( BrokerResources.I_CREATE_TABLE_INDEX, indexName ) );

                    dbMgr.executeUpdateStatement( stmt, sql );
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
                ex = CommDBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_CREATE_TABLE_FAILED, tableName ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }

    public void createStoredProc( Connection conn ) throws BrokerException {
    }

    public void dropStoredProc( Connection conn ) throws BrokerException {
    }

    /**
     * Drop the table.
     * @param conn database connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void dropTable( Connection conn ) throws BrokerException {

        String tableName = getTableName();
        logger.logToAll( Logger.INFO,
            br.getString( BrokerResources.I_DROP_TABLE, tableName ) );

        CommDBManager dbMgr = getDBManager();
        TableSchema tableSchema = null;
        if ( dbMgr.hasSupplementForCreateDrop(tableName) ) {
            tableSchema = dbMgr.getTableSchema( tableName );
        }

        boolean myConn = false;
        String dropSQL = "DROP TABLE " + tableName;
        Statement stmt = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            stmt = conn.createStatement();
            dbMgr.executeUpdateStatement( stmt, dropSQL );

            if ( dbMgr.hasSupplementForCreateDrop(tableName) ) {
                 dbMgr.dropTableSupplement(stmt, tableSchema, tableName, true);
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
                ex = CommDBManager.wrapSQLException("[" + dropSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_DROP_TABLE_FAILED, tableName ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }

    /**
     * Delete all entries.
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll( Connection conn ) throws BrokerException {
        deleteAll( conn, null );
    }

    /**
     * Convenience method to delete all entries. The intended purpose of this
     * method is to provided the ability for the extended class to overide it
     * and specified the timestamp column and chunk size which will be used
     * to chunk the data to be deleted. This is a work-around for HADB running
     * out of lock sets when deleting large number of records. For all other
     * databases, timestampColumn parameter should be set to null and chunkSize
     * should be set to 0.
     * @param conn
     * @param whereClause the where clause for the SQL command
     * @param timestampColumn the timestamp column which will be used to chunk
     *   the data to be deleted
     * @param chunkSize the size of each chunk
     * @throws BrokerException
     */
    protected void deleteAll( Connection conn, String whereClause,
        String timestampColumn, int chunkSize ) throws BrokerException {

        if ( chunkSize > 0 ) {
            deleteAllInChunk( conn, whereClause, timestampColumn, chunkSize );
        } else {
            deleteAll( conn, whereClause );
        }
    }

    /**
     * Delete all entries using the specified where clause.
     * @param conn database connection
     * @param whereClause the where clause for the SQL command
     * @throws BrokerException
     */
    private void deleteAll( Connection conn, String whereClause )
        throws BrokerException {

        CommDBManager dbMgr = getDBManager();
        String tableName = getTableName();
        String sql = null;

        if ( dbMgr.isOracle() && whereClause == null) {
            sql = new StringBuffer(128)
                .append( "TRUNCATE TABLE " ).append( tableName )
                .toString();
        } else {
            sql = new StringBuffer(128)
                .append( "DELETE FROM " ).append( tableName )
                .append( (whereClause != null && whereClause.length() > 0)
                         ? " WHERE " + whereClause : "" )
                .toString();
        }

        boolean myConn = false;
        Statement stmt = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            stmt = conn.createStatement();
            dbMgr.executeUpdateStatement( stmt, sql );
        } catch ( SQLException e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            SQLException ex = CommDBManager.wrapSQLException("[" + sql + "]", e);
            throw new BrokerException(
                br.getKString( BrokerResources.X_JDBC_CLEAR_TABLE_FAILED, tableName ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }

    /**
     * Delete all entries in chunk to work-around HADB running out of
     * lock sets when deleting large number of records.
     * @param conn database connection
     * @param whereClause the where clause for the SQL command
     * @param timestampColumn the timestamp column which will be used to chunk
     *   the data to be deleted
     * @param chunkSize the size of each chunk
     * @throws BrokerException
     */
    private void deleteAllInChunk( Connection conn, String whereClause,
        String timestampColumn, int chunkSize ) throws BrokerException {

        // Set whereClause to empty if not specified, i.e. null
        if ( whereClause == null ) {
            whereClause = "";
        }

        String tableName = getTableName();
        
        boolean myConn = false;
        PreparedStatement pstmt = null;
        String sql = null;
        int txnIsolation = -1;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager dbMgr = getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            } else {
                if ( !conn.getAutoCommit() ) {
                    conn.setAutoCommit( true );
                }
            }

            // Get the number of rows to be deleted to see if we need to delete
            // data in multiple chunks, i.e. # rowToBeDeleted > chunkSize
            sql = new StringBuffer(128)
                .append( "SELECT COUNT(*)" )
                .append( " FROM " ).append( tableName )
                .append( whereClause.length() > 0 ? " WHERE " + whereClause : "" )
                .toString();

            pstmt = dbMgr.createPreparedStatement( conn, sql );
            ResultSet rs = pstmt.executeQuery();
            int rowCount = 0;
            if ( rs.next() ) {
                rowCount = rs.getInt( 1 );
            }
            closeSQLObjects( rs, pstmt, null, null );

            if ( rowCount == 0 ) {
                return; // Nothing to delete!
            } else if ( rowCount < chunkSize ) {
                deleteAll( conn, whereClause ); // Don't have to delete in chunk
                return;
            }

            // We will need to delete all records in multiple chunks; so
            // generate the timestamp delimeter for each chunk.
            sql = new StringBuffer(128)
                .append( "SELECT " )
                .append( timestampColumn )
                .append( " FROM " ).append( tableName )
                .append( whereClause.length() > 0 ? " WHERE " + whereClause : "" )
                .append( " ORDER BY " )
                .append( timestampColumn )
                .toString();
            pstmt = dbMgr.createPreparedStatement( conn, sql );
            rs = pstmt.executeQuery();
            List chunkList = Util.getChunkDelimiters( rs, 1, chunkSize );
            closeSQLObjects( rs, pstmt, null, null );

            // For HADB, change txn isolation level to reduce the # of lock sets
            txnIsolation = conn.getTransactionIsolation();
            if ( txnIsolation != Connection.TRANSACTION_READ_COMMITTED ) {
                conn.setTransactionIsolation( Connection.TRANSACTION_READ_COMMITTED );
            }

            // Now, we delete all data in multiple chunks...
            sql = new StringBuffer(128)
                .append( "DELETE FROM " ).append( tableName )
                .append( " WHERE " )
                .append( timestampColumn ).append( " < ?" )
                .append( whereClause.length() > 0 ? " AND " + whereClause : "" )
                .toString();
            pstmt = dbMgr.createPreparedStatement( conn, sql );

            Long[] a = (Long[])chunkList.toArray(new Long[chunkList.size()]);
            for ( int i = 0, len = a.length; i < len; i++ )  {
                pstmt.setLong( 1, a[i].longValue() );
                pstmt.executeUpdate();
            }
        } catch ( SQLException e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            SQLException ex = CommDBManager.wrapSQLException("[" + sql + "]", e);
            throw new BrokerException(
                br.getKString( BrokerResources.X_JDBC_CLEAR_TABLE_FAILED, tableName ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, pstmt, conn, myex );
            } else {
                closeSQLObjects( null, pstmt, null, myex );
            }

            // Restore txn isolation level to original value
            if ( txnIsolation != -1 ) {
                try {
                    conn.setTransactionIsolation( txnIsolation );
                } catch ( Exception e ) {}
            }
        }
    }

     /**
      */
     public HashMap getDebugInfo( Connection conn ) {

	LinkedHashMap map = new LinkedHashMap();
	int count = -1;

	try {
            count = getRowCount( null, null );
	} catch ( Exception e ) {
            logger.logStack( Logger.ERROR, e.getMessage(), e );
	}
        map.put( "["+getTableName()+"]RowCount", String.valueOf( count ) );
	return map;
    }
}
