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
 * @(#)PropertyDAOImpl.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;

import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic PropertyDAO.
 */
class PropertyDAOImpl extends  BaseDAOImpl implements PropertyDAO {

    private final String tableName;

    // SQLs
    private final String insertSQL;
    private final String updateSQL;
    private final String deleteSQL;
    private final String selectSQL;
    private final String selectAllNamesSQL;
    private final String selectAllSQL;

    /**
     * Constructor
     * @throws BrokerException
     */
    PropertyDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( PROPNAME_COLUMN ).append( ", " )
            .append( PROPVALUE_COLUMN )
            .append( ") VALUES ( ?, ? )" )
            .toString();

        updateSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( PROPVALUE_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( PROPNAME_COLUMN ).append( " = ?" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( PROPNAME_COLUMN ).append( " = ?" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( PROPVALUE_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( PROPNAME_COLUMN ).append( " = ?" )
            .toString();

        selectAllNamesSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( PROPNAME_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( PROPNAME_COLUMN ).append( ", " )
            .append( PROPVALUE_COLUMN )
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
     * Persist the specified property name/value pair.
     * If the property identified by name exists in the store already,
     * it's value will be updated with the new value.
     * If value is null, the property will be removed.
     * The value object needs to be serializable.
     * @param conn the database connection
     * @param name the property name
     * @param value the property value
     * @throws BrokerException
     */
    public void update( Connection conn, String name, Object value )
        throws BrokerException {

        boolean myConn = false;
        String sql = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true;
            }

            // Check to see if the property exists
            if ( hasProperty( conn, name ) ) {
                if ( value != null ) {
                    // Update
                    sql = updateSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, sql );
                    Util.setObject( pstmt, 1, value );
                    pstmt.setString( 2, name );
                    pstmt.executeUpdate();
                } else {
                    // Delete
                    sql = deleteSQL;
                    pstmt = dbMgr.createPreparedStatement( conn, sql );
                    pstmt.setString( 1, name );
                    pstmt.executeUpdate();
                }
            } else if ( value != null ) {
                // Add
                sql = insertSQL;
                pstmt = dbMgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, name );
                Util.setObject( pstmt, 2, value );
                pstmt.executeUpdate();
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
                br.getKString( BrokerResources.X_PERSIST_PROPERTY_FAILED, name ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete an entry.
     * @param conn
     * @param name name name of the property whose value is to be deleted
     * @throws BrokerException
     */
    public void delete( Connection conn, String name )
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

            pstmt = dbMgr.createPreparedStatement( conn, deleteSQL );
            pstmt.setString( 1, name );
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
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_PROPERTY_FAILED, name ), ex );
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
     * @param conn
     * @throws BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        if ( Globals.getHAEnabled() ) {
            return; // Share table cannot be reset    
        } else {
            String whereClause = new StringBuffer(128)
               .append( PROPNAME_COLUMN ).append( " <> '" )
               .append( DBTool.STORE_PROPERTY_SUPPORT_JMSBRIDGE ).append( "'" )
               .toString();
 
            super.deleteAll( conn, whereClause, null, 0 );
        }
    }

    /**
     * Retrieve the value for the specified property.
     * @param conn database connection
     * @param name name of the property whose value is to be retrieved
     * @return the property value; null is returned if the specified
     *		property does not exist in the store
     * @exception BrokerException if an error occurs while retrieving the data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public Object getProperty( Connection conn, String name )
        throws BrokerException {

        Object propObj = null;

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
            pstmt.setString( 1, name );
            rs = pstmt.executeQuery();

            if ( rs.next() ) {
                try {
                    propObj = Util.readObject( rs, 1 );
                } catch ( IOException e ) {
                    // fail to parse object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_PROPERTY_FAILED, name, e );
                }
            }
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
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_PROPERTY_FAILED, name ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return propObj;
    }

    /**
     * Return the names of all persisted properties.
     * @param conn database connection
     * @return a List of property names; an empty List will be returned
     *		if no property exists in the store
     */
    public List getPropertyNames( Connection conn )
        throws BrokerException {

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

            pstmt = dbMgr.createPreparedStatement( conn, selectAllNamesSQL );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                String name = rs.getString( 1 );
                list.add( name );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllNamesSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllNamesSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_PROPERTIES_FAILED ), ex );
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
     * Load all properties from DB.
     * @param conn database connection
     * @return the Properties object
     * @throws BrokerException
     */
    public Properties getProperties( Connection conn ) throws BrokerException {

        Properties props = new Properties();

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
                String name = rs.getString( PROPNAME_COLUMN );
                try {
                    Object obj = Util.readObject( rs, 2 );
                    props.put( name, obj );
                } catch ( IOException e ) {
                    // fail to parse one object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_PROPERTY_FAILED, name, e );
                }
            }

            if ( Store.getDEBUG() ) {
                logger.log( Logger.DEBUG, "LOADED " +
                    props.size() + " PROPERTIES FROM DATABASE" );
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectAllSQL+"]", rbe );
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
                br.getKString( BrokerResources.X_LOAD_PROPERTIES_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return props;
    }

    /**
     * Check whether the specified property exists.
     * @param conn database connection
     * @param name name of the property
     * @return return true if the specified property exists
     */
    public boolean hasProperty( Connection conn, String name )
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

            pstmt = dbMgr.createPreparedStatement( conn, selectSQL );
            pstmt.setString( 1, name );
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
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectSQL+"]", rbe );
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
                br.getKString( BrokerResources.X_LOAD_PROPERTY_FAILED,
                name ), ex );
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

        map.put( "Properties(" + tableName + ")", String.valueOf( count ) );
        return map;
    }
}
