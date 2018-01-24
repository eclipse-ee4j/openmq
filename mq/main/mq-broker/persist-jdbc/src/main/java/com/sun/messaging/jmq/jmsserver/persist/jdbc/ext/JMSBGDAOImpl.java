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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.ext;

import java.util.*;
import java.sql.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.StoreBeingTakenOverException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.DBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.BaseDAOImpl;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.bridge.api.DupKeyException;
import com.sun.messaging.bridge.api.KeyNotFoundException;


/**
 * @author amyk
 */
public class JMSBGDAOImpl extends BaseDAOImpl implements JMSBGDAO {

    private final String tableName;

    private final String insertSQL;
    private final String updateBrokerIdSQL;
    private final String deleteSQL;
    private final String selectBrokerIdSQL;
    private final String selectUpdatedTimeSQL;
    private final String selectCreatedTimeSQL;
    private final String selectNamesByBrokerIdSQL;

    /**
     */
    public JMSBGDAOImpl() throws BrokerException {

        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName(TABLE_NAME_PREFIX);

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( NAME_COLUMN ).append( ", " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append( ", " )
            .append( UPDATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?)" )
            .toString();

        updateBrokerIdSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( BROKER_ID_COLUMN ).append( " = ?, " )
			.append( UPDATED_TS_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
			.append( " AND " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .append( Util.brokerNotTakenOverClause(dbMgr) )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
			.append( " AND " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();

        selectBrokerIdSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( BROKER_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectUpdatedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( UPDATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectCreatedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectNamesByBrokerIdSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( NAME_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();
    }

    /**
     */
    public final String getTableNamePrefix() {
        return TABLE_NAME_PREFIX;
    }

    /**
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * @param conn database connection
     * @param name to identify the TM 
     * @param logger_ can be null
     * @throws DupKeyException if already exist
     *         else Exception on error
     */
    public void insert(Connection conn,
                       String name,
                       java.util.logging.Logger logger_)
                       throws DupKeyException, Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            try {
                pstmt = dbMgr.createPreparedStatement(conn, insertSQL);
                pstmt.setString(1, name);
                pstmt.setString(2, dbMgr.getBrokerID());
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.setLong(4, 0L);
                pstmt.executeUpdate();
            } catch (Exception e) {
                myex = e;
                try {
                    if ((conn != null) && !conn.getAutoCommit()) {
                        conn.rollback();
                    }
                } catch (SQLException e1) {
                    String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                    logger.log(Logger.ERROR, emsg, e1);
                    Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
                }

                checkDupKeyOnException(conn, name, logger_);

                throw e;
            }
        } catch (Exception e) {
            myex = e;
            throw e;
        } finally {
            closeSQL(null, pstmt, myconn, myex, logger_);
        }
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name 
     * @param newBrokerId
     * @param expectedBrokerId
     * @param logger_ can be null
     * @throws KeyNotFoundException if not found
     *         StoreBeingTakenOverException if being takeover 
     *         Exception on any other error
     */
    public void updateBrokerId(Connection conn,
                               String name, 
                               String newBrokerId,
                               String expectedBrokerId,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException,
                               StoreBeingTakenOverException,
							   Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, updateBrokerIdSQL);
            pstmt.setString(1, newBrokerId);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, name);
            pstmt.setString(4, expectedBrokerId);
            if (Globals.getHAEnabled()) {
                pstmt.setString(5, dbMgr.getBrokerID());
            }

            if (pstmt.executeUpdate() == 0) {
                Util.checkBeingTakenOver(conn, dbMgr, logger, logger_);
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }

        } catch ( Exception e ) {
            myex = e;
            try {
                if ((conn != null) && !conn.getAutoCommit()) {
                   conn.rollback();
                }
            } catch (SQLException e1) {
                String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                logger.log( Logger.ERROR, emsg, e1);
                Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
            }

            throw e;
        } finally {
            closeSQL(null, pstmt, myconn, myex, logger_);
        }
    }

    /**
     * @param conn database connection
     * @param name to identify the TM
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public void delete(Connection conn,
                       String name,
                       java.util.logging.Logger logger_)
                       throws Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, deleteSQL);
            pstmt.setString(1, name);
            pstmt.setString(2, dbMgr.getBrokerID());
            if (pstmt.executeUpdate() == 0) {
                throw new KeyNotFoundException("Name "+name+" not found in store ");
            } 
        } catch (Exception e) {
            myex = e;
            try {
                if ((conn != null) && !conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                logger.log(Logger.ERROR, emsg, e1);
                Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
            }

            throw e;

        } finally {
            closeSQL(null,pstmt, myconn, myex, logger_);
        }
    }

    /**
     * Delete all entries for this broker
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll(Connection conn) throws BrokerException {

        DBManager dbMgr = DBManager.getDBManager();

        String whereClause = new StringBuffer(128)
               .append( BROKER_ID_COLUMN ).append( " = '" )
               .append( dbMgr.getBrokerID() ).append( "'" )
               .toString();
 
        deleteAll(conn, whereClause, null, 0);
    }


    /**
     * @param conn database connection
     * @param name jmsbridge name 
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public String getBrokerId(Connection conn,
                              String name,
                              java.util.logging.Logger logger_)
                              throws Exception {
        String brokerId = null;

        Connection myconn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, selectBrokerIdSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }
            brokerId = rs.getString(1);
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectBrokerIdSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return brokerId;
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getUpdatedTime(Connection conn, String name,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException, Exception {

        long updatedTime = -1;

        Connection myconn = conn;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, selectUpdatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException("Name "+name+" not found in store");
            }
            updatedTime = rs.getLong(1);
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectUpdatedTimeSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex,  logger_);
        }

        return updatedTime;
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getCreatedTime(Connection conn, String name,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException, Exception {
        long createdTime = -1;

        Connection myconn = conn;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, selectCreatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }
            createdTime = rs.getLong(1);
         } catch (Exception e) {
             myex = e;
             try {
                 if ( (conn != null) && !conn.getAutoCommit() ) {
                     conn.rollback();
                 }
             } catch ( SQLException rbe ) {
                 logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCreatedTimeSQL+"]", rbe );
             }
             throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return createdTime;
    }

    /**
     * @param conn database connection
     * @param brokerID 
     * @param logger_ can be null;
     * @return list of names
     * @throws Exception
     */
    public List getNamesByBroker(Connection conn, String brokerID,
                                 java.util.logging.Logger logger_)
                                 throws Exception {
        List list = new ArrayList();

        Connection myconn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = dbMgr.createPreparedStatement(conn, selectNamesByBrokerIdSQL);
            pstmt.setString(1, brokerID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add((rs.getString(1)));
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectNamesByBrokerIdSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return list;
    }

    /**
     * (same impl as in other DAO impls)
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo(Connection conn) {

        HashMap map = new HashMap();
        int count = -1;

        try {
            count = getRowCount(null, null);
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage(), e.getCause() );
        }

        map.put("JMSBridge(" + tableName + ")", String.valueOf(count));
        return map;
    }

    /**
     * To be called when exception occurred on a connection
     *
     * @Exception DupKeyException if xid already exists
     */
    private void checkDupKeyOnException(Connection conn,
                                        String name,
                                        java.util.logging.Logger logger_)
                                        throws DupKeyException {
        if (conn == null) return; 

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DBManager.getDBManager().createPreparedStatement(conn, selectCreatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                throw new DupKeyException("Name "+name +" already exists in DB");
            }
        } catch (Exception e) {
            try {
                if ( !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCreatedTimeSQL+"]", rbe );
            }

            if (e instanceof DupKeyException) throw (DupKeyException)e;

            String emsg = br.getKString(BrokerResources.X_INTERNAL_EXCEPTION,
                              "Exception on checkDupKey for name " + name);
            logger.logStack(Logger.WARNING, emsg, e);
            Util.logExt(logger_, java.util.logging.Level.WARNING, emsg, e);
        } finally {
            closeSQL(rs, pstmt, null, null, logger_);
        }
    }

    private void closeSQL(ResultSet rset, 
                          PreparedStatement pstmt, 
                          Connection conn, Throwable myex,
                          java.util.logging.Logger logger_) {
        try {
            Util.close(rset, pstmt, conn, myex);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            String emsg = "Unable to close SQL connection or statement: "+
                           e.getMessage()+(cause == null ? "":" - "+cause.getMessage());
            logger.log(Logger.WARNING, emsg, e);
            Util.logExt(logger_, java.util.logging.Level.WARNING, emsg, e);
        }
    }

}
