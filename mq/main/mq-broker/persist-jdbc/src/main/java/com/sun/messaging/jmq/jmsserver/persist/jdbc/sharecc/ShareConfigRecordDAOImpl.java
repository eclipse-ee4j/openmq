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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.sql.*;
import java.io.IOException;

/**
 * This class implements ShareConfigRecordDAO
 */
public class ShareConfigRecordDAOImpl extends ShareConfigRecordBaseDAOImpl
implements ShareConfigRecordDAO {

     private final String tableName;

    // SQLs

    private final String insertSQLOracle;
    private final String selectSeqSQLOracle;

    private final String insertSQL;
    private final String insertResetRecordSQL;
    private final String insertResetRecordWithLockSQL;
    private final String selectTypeFlagByMaxSeqUKeySQL;
    private final String selectMaxSeqFlagUKeySQL;
    private final String selectSinceWithResetRecordSQL;
    private final String selectAllSQL;
    private final String selectSeqByUUIDSQL;
    private final String updateResetRecordUUIDSQL;
    private final String setResetRecordFLAGNULLSQL;
    private final String selectResetRecordUUIDSQL;
    private final String selectLockIDSQL;
    private final String updateLockIDSQL;

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    ShareConfigRecordDAOImpl() throws BrokerException {

        tableName = getDBManager().getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( UKEY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append(", ")
            .append( FLAG_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ?, ? )" )
            .toString();

        insertSQLOracle = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( SEQ_COLUMN ).append( ", " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( UKEY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append(", ")
            .append( FLAG_COLUMN )
            .append( ") VALUES ("+tableName+"_seq.NEXTVAL, ?, ?, ?, ?, ?, ? )" )
            .toString();

        selectSeqSQLOracle = new StringBuffer(128)
            .append( "SELECT " ).append( tableName+"_seq.CURRVAL " )
            .append( "FROM DUAL" )
            .toString();

        insertResetRecordSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( SEQ_COLUMN ).append( ", " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( UKEY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( 1, ?, ?, ?, ?, ? )" )
            .toString();

        insertResetRecordWithLockSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( SEQ_COLUMN ).append( ", " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( UKEY_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append(", ")
            .append( LOCK_ID_COLUMN )
            .append( ") VALUES ( 1, ?, ?, ?, ?, ?, ? )" )
            .toString();

        selectTypeFlagByMaxSeqUKeySQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( TYPE_COLUMN ).append(", ")
            .append( FLAG_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE "   ).append( SEQ_COLUMN ).append( " IN (" )
            .append( " SELECT MAX(" ).append( SEQ_COLUMN ).append( ") " )
            .append( "FROM ").append(  tableName )
            .append( " WHERE ").append( UKEY_COLUMN ).append( " = ? )")
            .toString();

        selectMaxSeqFlagUKeySQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( SEQ_COLUMN ).append(", ")
            .append( FLAG_COLUMN )
            .append( " FROM ").append(  tableName )
            .append( " WHERE ").append( UKEY_COLUMN ).append( " = ? ")
            .append( " AND " )
            .append( SEQ_COLUMN ).append( " = " )
            .append( "(SELECT MAX(" ).append( SEQ_COLUMN ).append( ") " )
            .append( " FROM ").append(  tableName )
            .append( " WHERE ").append( UKEY_COLUMN ).append( " = ? )")
            .toString();

        selectSinceWithResetRecordSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( SEQ_COLUMN ).append( ", " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( SEQ_COLUMN ).append( " > ?" )
            .append( " OR " )
            .append( TYPE_COLUMN ).append(  " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .append( " ORDER BY " ).append( SEQ_COLUMN )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( SEQ_COLUMN ).append( ", " )
            .append( UUID_COLUMN ).append( ", " )
            .append( RECORD_COLUMN ).append( ", " )
            .append( TYPE_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append( ", " )
            .append( LOCK_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " ORDER BY " ).append( SEQ_COLUMN )
            .toString();

        selectSeqByUUIDSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( SEQ_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( UUID_COLUMN ).append( " = ?" )
            .toString();

        selectLockIDSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( LOCK_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .toString();

        updateResetRecordUUIDSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( UUID_COLUMN ).append( " = ?" )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .toString();

        setResetRecordFLAGNULLSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( FLAG_COLUMN ).append( " = NULL" )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .toString();

        selectResetRecordUUIDSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( UUID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .toString();

        updateLockIDSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( LOCK_ID_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( TYPE_COLUMN ).append( " = " )
            .append( ChangeRecordInfo.TYPE_RESET_PERSISTENCE )
            .append( " AND " )
            .append( LOCK_ID_COLUMN ).append( " = ? " )
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
     *
     * @param conn database connection
     * @param rec the record to be inserted   
     * @return the record inserted     
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public ChangeRecordInfo insert( Connection conn, 
                                    ChangeRecordInfo rec)
                                    throws BrokerException {

        String sql = null;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( ((mgr.isDerby() || mgr.isDB2()) ? true:false) );
                myConn = true;
            }
            String resetUUID = rec.getResetUUID(); 

            if (rec.isDuraAddRecord()) {
                Integer flag = hasLastSeqForUKeyType( conn, rec.getUKey(), rec.getType() );
                if (flag != null) {
                    String emsg = br.getKString(br.I_SHARECC_RECORD_UKEY_TYPE_EXIST,
                                      rec.getUKey())+"["+rec.getType()+"]"+
                                      ChangeRecordInfo.getFlagString(flag);
                    logger.log(logger.INFO, emsg);
                    if (flag.intValue() != rec.getFlag()) {
                        throw new BrokerException(emsg);
                    }
                }
            }

            if (mgr.isOracle()) {
                sql = insertSQLOracle;
            } else {
                sql = insertSQL;
            }
            if (mgr.supportsGetGeneratedKey()) {
                if (mgr.isPostgreSQL() || mgr.isDB2()) {
                    pstmt = mgr.createPreparedStatement( conn, sql, Statement.RETURN_GENERATED_KEYS );
                } else {
                    pstmt = mgr.createPreparedStatement( conn, sql, new String[]{SEQ_COLUMN} );
                }
            } else {
                pstmt = mgr.createPreparedStatement( conn, sql );
            }
            pstmt.setString( 1, rec.getUUID() );
            Util.setBytes( pstmt, 2, rec.getRecord() );
            pstmt.setInt( 3, rec.getType() );
            pstmt.setString( 4, rec.getUKey() );
            pstmt.setLong( 5, rec.getTimestamp() );
            pstmt.setInt( 6, rec.getFlag() );
            pstmt.executeUpdate();
            Long seq = null;
            if (mgr.supportsGetGeneratedKey()) {
                rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    seq = Long.valueOf(rs.getLong( 1 ));
                }
            } else if (mgr.isOracle()) {
                Statement st = conn.createStatement();  
                rs = mgr.executeQueryStatement( st, selectSeqSQLOracle );
                if (rs.next()) {
                    seq = Long.valueOf(rs.getLong( 1 ));
                }
                rs.close();
                rs = null;
                st.close();
            } else {
                seq = getSequenceByUUID( conn, rec.getUUID() );
            }
            if (seq == null) {
                throw new BrokerException(
                    br.getKString(br.X_SHARECC_FAIL_GET_SEQ_ON_INSERT, rec));
            }
            String currResetUUID = getResetRecordUUID( conn );
            if (resetUUID != null && !currResetUUID.equals(resetUUID)) {
                throw new BrokerException(br.getKString(br.X_SHARECC_TABLE_RESET,
                   "["+resetUUID+", "+currResetUUID+"]"), Status.PRECONDITION_FAILED);
            }

            if ( myConn  && !conn.getAutoCommit() ) {
                conn.commit();
            }

            if (rec.isDuraAddRecord()) {
                Integer flag = checkLastUKeyHasSeq(conn, rec.getUKey(), seq.longValue()); 
                if (flag != null) {
                    String emsg = br.getKString(br.I_SHARECC_RECORD_UKEY_TYPE_EXIST,
                                      rec.getUKey())+"["+rec.getType()+"]"+
                                      ChangeRecordInfo.getFlagString(flag);
                    logger.log(logger.INFO, emsg);
                    if (flag.intValue() != rec.getFlag()) {
                        throw new BrokerException(emsg);
                    }
               }
            }

            if (resetUUID == null) {
                rec.setResetUUID( currResetUUID ); 
            }
            rec.setSeq(seq);

            return rec;

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
                ex = getDBManager().wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( br.X_SHARECC_INSERT_RECORD_FAIL, 
                    rec.toString(), ex.getMessage() ), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    /**
     * Insert reset record.
     *
     * @param conn database connection
     * @param rec the reset record to be inserted   
     * @param canExist Exception if the reset record already exist
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void insertResetRecord( Connection conn, 
                                   ChangeRecordInfo rec,
                                   String lockID)
                                   throws BrokerException {

        String sql = null;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( false );
                myConn = true;
            }

            if (!hasResetRecord( conn )) {
                sql = insertResetRecordSQL;
                if (lockID != null) {
                    sql = insertResetRecordWithLockSQL;
                }
                pstmt = mgr.createPreparedStatement( conn, sql );
                pstmt.setString( 1, rec.getUUID() );
                Util.setBytes( pstmt, 2, rec.getRecord() );
                pstmt.setInt( 3, ChangeRecordInfo.TYPE_RESET_PERSISTENCE );
                pstmt.setString( 4, rec.getUKey());
                pstmt.setLong( 5, rec.getTimestamp() );
                if (lockID != null) {
                    pstmt.setString( 6,  lockID);
                }
                int count = pstmt.executeUpdate();
                if ( count == 1) {
                    setResetRecordUUID( conn, rec.getUUID() );
                 } else if (!hasResetRecord(conn) || lockID != null ) {
                     throw new BrokerException("Unexpected affected row count "+count+" on  "+sql);
                 }
            } else if (lockID != null) {
                 String m = br.getKString(br.E_SHARECC_TABLE_NOT_EMPTY,
                                          getDBManager().getClusterID());
                 throw new BrokerException(m, br.E_SHARECC_TABLE_NOT_EMPTY,
                                 null, Status.MOVED_PERMANENTLY);
            }
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

            try {
                if (hasResetRecord( conn )) {
                    if (lockID != null) {
                        String m = br.getKString(br.E_SHARECC_TABLE_NOT_EMPTY,
                                                 getDBManager().getClusterID());
                        throw new BrokerException(m, br.E_SHARECC_TABLE_NOT_EMPTY, 
                                  null, Status.MOVED_PERMANENTLY);
                    }
                    myex = null;
                    return;
                }
            } catch ( Exception e2 ) {
                 logger.log( Logger.ERROR, br.getKString(
                     br.E_SHARECC_CHECK_EXIST_EXCEPTION, e2.getMessage()));
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = getDBManager().wrapSQLException("[" + insertResetRecordSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString(br.X_SHARECC_INSERT_RESET_RECORD_FAIL, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, pstmt, conn, myex );
            } else {
                closeSQLObjects( null, pstmt, null, myex );
            }
        }
    }

    /**
     *
     * @param conn database connection
     * @param uuid  
     * @param record 
     * @param timestamp
     * @throws BrokerException if locked or other error 
     */
    public boolean hasResetRecord( Connection conn )
    throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, selectAllSQL );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                int typ  = rs.getInt( 4 );
                if (typ == ChangeRecordInfo.TYPE_RESET_PERSISTENCE) {
                    String lock = rs.getString(6);
                    if (lock != null) {
                        getDBManager().throwTableLockedException(lock);
                    }
                    return true;
                } 
                throw new BrokerException(
                "Unexpected 1st record type "+typ+" for first record in database table "+getTableName());
            } else {
                return false;
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
                ex = getDBManager().wrapSQLException("[" + selectAllSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(br.getKString(
                br.X_SHARECC_QUERY_RESET_RECORD_FAIL, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, pstmt, conn, myex );
            } else {
                closeSQLObjects( null, pstmt, null, myex );
            }
        }
    }

    /**
     *
     * @param conn database connection
     * @param uuid  
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void setResetRecordUUID( Connection conn, String uuid )
                                    throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, updateResetRecordUUIDSQL );
            pstmt.setString( 1, uuid );
            if ( pstmt.executeUpdate() < 1 ) {
                throw new BrokerException(
                "Unexpected affected row count for "+updateResetRecordUUIDSQL);
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
                ex = getDBManager().wrapSQLException("[" + updateResetRecordUUIDSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(br.getKString(
            br.X_SHARECC_SET_RESET_RECORD_UUID_FAIL, uuid, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    /**
     *
     * @param conn database connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public void setResetRecordFLAGNULL( Connection conn )
    throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, setResetRecordFLAGNULLSQL );
            if ( pstmt.executeUpdate() < 1 ) {
                throw new BrokerException(
                "Unexpected affected row count for "+setResetRecordFLAGNULLSQL);
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
                ex = getDBManager().wrapSQLException("[" +setResetRecordFLAGNULLSQL+ "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(br.getKString(
            br.X_SHARECC_CLEAR_RESET_RECORD_FLAG_FAIL, FLAG_COLUMN, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }


    /**
     *
     * @param conn database connection
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public String getResetRecordUUID( Connection conn )
                                throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, selectResetRecordUUIDSQL );
            rs = pstmt.executeQuery();
            String uuid = null;
            if ( rs.next() ) {
                uuid = rs.getString( 1 );
            }
            if (uuid == null) {
                throw new BrokerException(
                "No reset record found in database table "+getTableName());
            }
            return uuid;
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
                ex = getDBManager().wrapSQLException("[" + selectResetRecordUUIDSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(br.getKString(
            br.X_SHARECC_QUERY_RESET_RECORD_UUID_FAIL, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    /**
     * Get records since sequence seq (exclusive) or get all records 
     *
     * @param conn database connection
     * @param seq sequence number, null if get all records
     * @param resetUUID last reset UUID this broker has processed 
     * @return an array of ShareConfigRecord 
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    public List<ChangeRecordInfo> getRecords( Connection conn, Long seq,
                                              String resetUUID, boolean canReset )
                                              throws BrokerException {

        ArrayList<ChangeRecordInfo> records =  new ArrayList<ChangeRecordInfo>();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String selectSQL = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( false );
                myConn = true;
            }

            if (seq == null || resetUUID == null) {
                records = getAllRecords( conn, null );

            } else {
                selectSQL  = selectSinceWithResetRecordSQL;
                pstmt = mgr.createPreparedStatement( conn, selectSQL );
                pstmt.setLong( 1, seq.longValue() );
         
                rs = pstmt.executeQuery();
                long seqv = -1;
                String uuidv = null;
                byte[] buf =  null;
                int typv = 0;
                long tsv = -1;
                ChangeRecordInfo cri = null;
                boolean loadfail = false;
                boolean reseted = false, foundreset = false;
                String newResetUUID = null;
                while ( rs.next() ) {
                    try {
                        seqv = rs.getLong( 1 );
                        uuidv  = rs.getString( 2 );
                        buf = Util.readBytes( rs, 3 );
                        typv  = rs.getInt( 4 );
                        tsv  = rs.getLong( 5 );
                        if (typv == ChangeRecordInfo.TYPE_RESET_PERSISTENCE) {
                            foundreset = true;
                            if (uuidv.equals(resetUUID)) {
                                continue;
                            }
                            newResetUUID = uuidv;
                            reseted = true;
                            break;
                        }
                        cri = new ChangeRecordInfo(Long.valueOf(seqv), uuidv, buf, typv, null, tsv);
                        cri.setResetUUID(resetUUID);
                        cri.setIsSelectAll(false);
                        records.add(cri);
                    } catch (IOException e) {
                        loadfail = true;
                        IOException ex = getDBManager().wrapIOException(
                                         "[" + selectAllSQL + "]", e );
                        logger.logStack( Logger.ERROR,
                            BrokerResources.X_PARSE_CONFIGRECORD_FAILED,
                            String.valueOf( seq ), ex);
                    }
                }
                if (!foundreset) {
                    throw new BrokerException(
                    "Unexpected: shared database table "+getTableName()+" has no reset record",
                    Status.PRECONDITION_FAILED);
                }
                if (reseted) {
                    if (!canReset) {
                        throw new BrokerException(br.getKString(br.X_SHARECC_TABLE_RESET,
                        "["+resetUUID+", "+newResetUUID+"]"), Status.PRECONDITION_FAILED);
                    }
                    logger.log(logger.INFO, br.getKString(br.I_SHARECC_USE_RESETED_TABLE,
                                            "["+resetUUID+", "+newResetUUID+"]"));
                    records = getAllRecords( conn, null );
                }
            }
            if (myConn) {
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
                ex = getDBManager().wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( br.getKString(
                br.X_SHARECC_QUERY_RECORDS_FAIL, ex.getMessage()), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }

        return records;
    }

    public ArrayList<ChangeRecordInfo> getAllRecords( Connection conn, String query )
    throws BrokerException {

        String sql = (query == null ? selectAllSQL:query);
        ArrayList<ChangeRecordInfo> records =  new ArrayList<ChangeRecordInfo>();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, sql );
            rs = pstmt.executeQuery();
            long seqv = -1;
            String uuidv = null;
            byte[] buf =  null;
            int typv = 0;
            long tsv = -1;
            ChangeRecordInfo cri = null;
            boolean foundreset = false;
            while ( rs.next() ) {
                try {
                    seqv = rs.getLong( 1 );
                    uuidv  = rs.getString( 2 );
                    buf = Util.readBytes( rs, 3 );
                    typv  = rs.getInt( 4 );
                    if (typv == ChangeRecordInfo.TYPE_RESET_PERSISTENCE) {
                        foundreset = true;
                    }
                    tsv  = rs.getLong( 5 );
                    cri = new ChangeRecordInfo(Long.valueOf(seqv), uuidv, buf, typv, null, tsv);
                    cri.setIsSelectAll(true);
                    records.add(cri);
                } catch (IOException e) {
                    IOException ex = getDBManager().wrapIOException(
                        "[" + selectAllSQL + "]", e );
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_CONFIGRECORD_FAILED,
                        String.valueOf( seqv ), ex);
                    throw new BrokerException(ex.getMessage(), Status.PRECONDITION_FAILED);
                }
            }
            if (!foundreset) {
                throw new BrokerException(
                "Unexpected: shared cluster change record table ["+
                 (query == null ? getTableName():sql)+"] has no reset record",
                Status.PRECONDITION_FAILED);
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
                ex = getDBManager().wrapSQLException("["+sql+ "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( br.getKString(
                br.X_SHARECC_QUERY_ALL_RECORDS_FAIL+
                "["+(query == null ? getTableName():sql)+"]", ex.getMessage()), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
        return records;
    }


    /**
     * Get the sequence number of the specified record
     *
     * @param conn database connection
     * @param uuid uid of the record 
     * @return null if no such record
     */
    private Long getSequenceByUUID( Connection conn, String uuid )
        throws BrokerException {

        Long seq = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            CommDBManager mgr =  getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, selectSeqByUUIDSQL );
            pstmt.setString( 1, uuid );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                long seqv = rs.getLong( 1 );
                seq = Long.valueOf(seqv);
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
                ex = getDBManager().wrapSQLException("[" + selectSeqByUUIDSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            String[] args = new String[] { SEQ_COLUMN, String.valueOf(seq),
                                           ex.getMessage() };
            throw new BrokerException( br.getKString(
                br.X_SHARECC_QUERY_SEQ_BY_UUID_FAIL, args), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }

        return seq;
    }

    private Integer hasLastSeqForUKeyType( Connection conn, String ukey, int type )
    throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectTypeFlagByMaxSeqUKeySQL;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, ukey );
            rs = pstmt.executeQuery();
            if ( !rs.next() ) {
                return null;
            }
            int t = rs.getInt( 1 );
            int flag = rs.getInt( 2 );
            if (t == type) {
                return Integer.valueOf(flag);
            } 
            return null;
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
                ex = getDBManager().wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( br.getKString(
                br.X_SHARECC_QUERY_MAX_SEQ_UKEY, 
                ukey+"["+String.valueOf(type)+"]")+": "+ex.toString(), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    private Integer checkLastUKeyHasSeq( Connection conn, String ukey, long expectedSeq )
    throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectMaxSeqFlagUKeySQL;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, sql );
            pstmt.setString( 1, ukey );
            pstmt.setString( 2, ukey );
            rs = pstmt.executeQuery();
            if ( !rs.next() ) {
                return null;
            }
            long s = rs.getLong( 1 );
            int flag = rs.getInt( 2 );
            if (s == expectedSeq) {
                return null;
            } 
            return Integer.valueOf(flag);
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
                ex = getDBManager().wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException( br.getKString(
                br.X_SHARECC_QUERY_MAX_SEQ_UKEY, 
                ukey+"["+expectedSeq+"]")+": "+ex.toString(), ex );
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }

    }

    /**
     * @return "" if no reset record entry
     */
    public String getLockID( Connection conn ) throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = selectLockIDSQL;
        try {
            // Get a connection
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( true );
                myConn = true;
            }

            pstmt = mgr.createPreparedStatement( conn, sql );
            rs = pstmt.executeQuery();
            if ( !rs.next() ) {
                return "";
            }
            return rs.getString(1);
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+":"+sql, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = getDBManager().wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }
            throw new BrokerException(br.getKString(
                br.X_SHARECC_QUERY_LOCKID, ex.getMessage()), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    /**
     */
    public void updateLockID( Connection conn, 
        String newLockID, String oldLockID)
        throws BrokerException {

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        String sql = updateLockIDSQL;
        try {
            CommDBManager mgr = getDBManager();
            if ( conn == null ) {
                conn = mgr.getConnection( false );
                myConn = true;
            }
            pstmt = mgr.createPreparedStatement( conn, sql );
            Util.setString( pstmt, 1, newLockID ); 
            pstmt.setString( 2, oldLockID ); 
            int cnt = pstmt.executeUpdate();
            if (cnt != 0) {
                return;
            }
            if (hasResetRecord( conn )) {
                String m = br.getKString(br.E_SHARECC_TABLE_NOT_EMPTY,
                                         getDBManager().getClusterID());
                throw new BrokerException(m, br.E_SHARECC_TABLE_NOT_EMPTY,
                          null, Status.MOVED_PERMANENTLY);
            }
            return;

        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+":"+sql, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = getDBManager().wrapSQLException("[" + sql + "]", (SQLException)e);
            } else {
                ex = e;
            }
            throw new BrokerException(br.getKString(br.X_SHARECC_UPDATE_LOCKID,
                          oldLockID, newLockID)+": "+ex.getMessage(), ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( rs, pstmt, conn, myex );
            } else {
                closeSQLObjects( rs, pstmt, null, myex );
            }
        }
    }

    public void insertAll( List<ChangeRecordInfo> recs, String oldTableName ) 
    throws BrokerException {

        Connection conn = null;
        Exception myex = null;
        try {
            conn = getDBManager().getConnection( false );

            ChangeRecordInfo rec = null;
            Iterator<ChangeRecordInfo> itr = recs.iterator();
            while ( itr.hasNext() ) {
                rec = itr.next();
                insert( conn, rec );
            }
            conn.commit();

        } catch ( Exception e ) {
            myex = e;
            String emsg = br.getKString(br.X_SHARECC_MIGRATE_DATA, 
                              oldTableName, e.getMessage());
            try {
                if ( conn != null ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+emsg+"]:"+rbe, rbe );
            }
            Exception ex = e;
            if ( e instanceof SQLException ) {
                ex = getDBManager().wrapSQLException("["+emsg+"]", (SQLException)e);
            } 
            throw new BrokerException(emsg, ex);

        } finally {
            closeSQLObjects( null, null, conn, myex );
        }
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new LinkedHashMap();
        StringBuffer buf = new StringBuffer();
        ArrayList<ChangeRecordInfo> records = null;
        try {
            records = getAllRecords( conn, null );
            Iterator<ChangeRecordInfo>  itr = records.iterator();
            ChangeRecordInfo rec = null;
            while (itr.hasNext()) {
                rec = itr.next();
                buf.append(rec.toString()).append("\n");
            }
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage(), e.getCause() );
        }

        map.put("Cluster Config Change Records:\n", buf.toString());
        if (records != null) {
            map.put( "Count", records.size());
        }
        return map;
    }
}
