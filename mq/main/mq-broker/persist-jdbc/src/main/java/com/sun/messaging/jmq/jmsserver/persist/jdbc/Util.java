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
 * @(#)Util.java	1.42 08/17/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.MQSQLException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.StoreBeingTakenOverException;
import com.sun.messaging.jmq.jmsserver.persist.api.util.MQObjectInputStream;
import com.sun.messaging.jmq.util.log.Logger;

import java.io.*;
import java.sql.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * Contains utility functions.
 * - methods to set/get Object
 * - methods to generate SQL statements
 */
public class Util implements DBConstants {

    /**
     * Added support for setting String parameter to NULL.
     */
    public static void setString( PreparedStatement pstmt, int pos, String value )
    throws SQLException {
         setString(pstmt, pos, value, true);
    }
    static void setString( PreparedStatement pstmt, int pos, 
                           String value, boolean emptyStringToNull)
                           throws SQLException {

        if ( value != null && value.length() > 0 ) {
            pstmt.setString( pos, value );
        } else {
            if ( value != null && value.length() == 0 && !emptyStringToNull) {
                 throw new SQLException("Empty string for ["+pos+"] is not allowed");
            }
            pstmt.setNull( pos, Types.VARCHAR );
        }
    }

    /**
     * Added support for setting int parameter to NULL.
     */
    static void setInt( PreparedStatement pstmt, int pos, int value )
        throws SQLException {

        if ( value >= 0 ) {
            pstmt.setInt( pos, value );
        } else {
            pstmt.setNull( pos, Types.INTEGER );
        }
    }

    /**
     * Added support for setting long parameter to NULL.
     */
    static void setLong( PreparedStatement pstmt, int pos, long value )
        throws SQLException {

        if ( value >= 0 ) {
            pstmt.setLong( pos, value );
        } else {
            pstmt.setNull( pos, Types.BIGINT );
        }
    }

    static void setObject(PreparedStatement pstmt, int pos, Object obj)
	throws IOException, SQLException {

        if ( obj == null ) {
            pstmt.setNull( pos, Types.LONGVARBINARY );
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            ObjectOutputStream bos = new ObjectOutputStream(baos);
            bos.writeObject(obj);
            bos.close();

            byte[] buf = baos.toByteArray();
            setBytes(pstmt, pos, buf);
        }
    }

    public static void setBytes(PreparedStatement pstmt, int pos, byte[] data)
	throws IOException, SQLException {

        if (data == null) {
            pstmt.setNull( pos, Types.LONGVARBINARY );
        } else {
            pstmt.setBytes(pos, data);
        }
    }

    static void setBytesAsBinaryStream(PreparedStatement pstmt, int pos, byte[] data)
	throws IOException, SQLException {

        if (data == null) {
            pstmt.setNull( pos, Types.LONGVARBINARY );
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            pstmt.setBinaryStream(pos, bais, data.length);
            bais.close();
        }
    }

    static Object readObject(ResultSet rs, int pos)
	throws IOException, SQLException, ClassNotFoundException {

	InputStream is = rs.getBinaryStream(pos);

        // If column is empty then InputStream will be NULL
        if (is == null) {
            return null;
        }

        // Use our version of ObjectInputStream so we can load old
        // serialized object from an old store, i.e. store migration
        ObjectInputStream ois = null;
        try {
            ois = new MQObjectInputStream(is);
	    Object obj = ois.readObject();
	    return obj;
        } finally {
            if (ois != null) {
	        ois.close();
            }
        }
    }

    public static byte[] readBytes(ResultSet rs, int pos)
	throws IOException, SQLException {

	InputStream is = rs.getBinaryStream(pos);

        // If column is empty then InputStream will be NULL
        if (is == null) {
            return null;
        }

	ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);

	// read until either:
	//   returned number of bytes is 0 or -1: no more to read
	//   get an eof
	byte[] buf = new byte[1024];
	while (true) {
	    try {
		int cnt = is.read(buf, 0, 256);
		if (cnt > 0) {
		   bos.write(buf, 0, cnt);
		} else {
		   // cnt = 0 or -1; cannot read anything, we are done
		   break;
		}
	    } catch (EOFException e) {
		// done
		break;
	    }
	}

	return bos.toByteArray();
    }

    /**
     * This method must only be used by DB store of DBManager.java class 
     *
     * @param rset
     * @param stmt
     * @param conn
     */
    public static void close( ResultSet rset, Statement stmt,
                              Connection conn, Throwable ex )
                              throws BrokerException {
         close(rset, stmt, conn, ex, null);
    }

    public static void close( ResultSet rset, Statement stmt,
                              Connection conn, Throwable ex,
                              CommDBManager mgrArg )
                              throws BrokerException {

        try {
            if ( rset != null ) {
                rset.close();
            }
            if ( stmt != null ) {
                stmt.close();
            }
        } catch ( SQLException e ) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Unable to close JDBC resources", e ) );
        } finally {
            if ( conn != null ) {
                if (mgrArg == null) {
                    DBManager.getDBManager().freeConnection( conn, ex );
                } else {
                   mgrArg.freeConnection( conn, ex );
                }
            }
        }
    }

    /**
     * Returns true if the string contains only alphanumeric characters and '_'.
     * @param str the string to check
     */
    public static boolean isAlphanumericString( String str ) {

        boolean isValid = false;
        if (str != null && str.length() > 0) {
            char c;
            for (int i = 0, len = str.length(); i < len; i++) {
                c = str.charAt(i);
                isValid = Character.isLetterOrDigit(c);
                // Also allow '_' char
                if (!isValid && c != '_') {
                    break;
                }
            }
        }

        return isValid;
    }

    /*
     * Methods to invoke Oracle LOB APIs using reflection because we don't
     * want to have a depedency on Oracle driver for our build environment!
     */

    static boolean OracleBLOB_initialized = false;
    static Method OracleBLOB_empty_lob_method = null;
    static Method OracleBLOB_getBinaryOutputStream_method = null;
    static Method OraclePreparedStatement_setBLOB_method = null;

    /**
     * Initalize methods for Oracle LOB APIs.
     * @throws BrokerException
     */
    static final void OracleBLOB_init()
        throws BrokerException {

        if ( !OracleBLOB_initialized ) {
            try {
                Class BLOBCls = Class.forName( "oracle.sql.BLOB" );

                OracleBLOB_empty_lob_method =
                    BLOBCls.getMethod( "empty_lob", null );

                OracleBLOB_getBinaryOutputStream_method =
                    BLOBCls.getMethod( "getBinaryOutputStream", null );

                Class OraclePreparedStatementCls = Class.forName(
                    "oracle.jdbc.OraclePreparedStatement" );

                Class[] paramTypes = new Class[2];
                paramTypes[0] = Integer.TYPE;
                paramTypes[1] = BLOBCls;
                OraclePreparedStatement_setBLOB_method =
                    OraclePreparedStatementCls.getMethod( "setBLOB", paramTypes );

                OracleBLOB_initialized = true;
            } catch ( Exception e ) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Oracle LOB extension APIs not found" ), e );
            }
        }
    }

    /**
     * Invoke oracle.sql.BLOB.empty_lob() to create an empty LOB.
     *
     * Note:
     * Because an empty_lob() method creates a special marker that does not
     * contain a locator, a JDBC application cannot read or write to it.
     * The JDBC driver throws the exception ORA-17098 Invalid empty LOB
     * operation if a JDBC application attempts to read or write to an
     * empty LOB before it is stored in the database.
     */
    static final Blob OracleBLOB_empty_lob()
        throws Exception {

        if ( !OracleBLOB_initialized ) {
            OracleBLOB_init();
        }

        // An oracle.sql.BLOB object
        Blob blob = (Blob)OracleBLOB_empty_lob_method.invoke( null, null );

        return blob;
    }

    /**
     * Invoke oracle.sql.BLOB.getBinaryOutputStream()
     */
    static final OutputStream OracleBLOB_getBinaryOutputStream(
        Blob blob ) throws Exception {

        if ( !OracleBLOB_initialized ) {
            OracleBLOB_init();
        }

        OutputStream out = (OutputStream)
            OracleBLOB_getBinaryOutputStream_method.invoke( blob, null );

        return out;
    }

    /**
     * Invoke ((oracle.jdbc.OraclePreparedStatement)(pstmt)).setBLOB( pos, blob );
     */
    static void OraclePreparedStatement_setBLOB(
        PreparedStatement pstmt, int pos, Blob blob ) throws Exception {

        if ( !OracleBLOB_initialized ) {
            OracleBLOB_init();
        }

        Object[] arglist = new Object[2];
        arglist[0] = Integer.valueOf( pos );
        arglist[1] = blob;
        OraclePreparedStatement_setBLOB_method.invoke( pstmt, arglist );
    }

    /**
     * To set a Blob using Oracle driver, we need to do the following steps:
     *
     * BLOB blob = BLOB.empty_lob();
     * ((OraclePreparedStatement)(pstmt)).setBLOB( pos, blob );
     */
    static final Blob setOracleBLOB( PreparedStatement pstmt, int pos )
        throws Exception {

        Blob blob = null;
        blob = OracleBLOB_empty_lob();
        OraclePreparedStatement_setBLOB( pstmt, pos, blob );

        return blob;
    }

    /**
     * Returns true if the error is due to HADB running out of locks set:
     * HADB-E-02080: Too many locks set, out of request objects
     * HADB-E-02096: Too many locks held concurrently 
     * @param t throwable object
     * @return true if HADB is running out of locks set
     */
    static boolean isHADBTooManyLockError( Throwable t ) {

        if ( t instanceof SQLException ) {
            SQLException e = (SQLException)t;
            int errorCode = e.getErrorCode();
            return (errorCode == 2080 || errorCode == 2096);
        }
        return false;
    }

    /**
     * A convenient method to break down the total number of rows to
     * delete/update into smaller chunks. This method assumes that the
     * specified resultset is ordered by the timestamp column.
     *
     * @param rs the ResultSet
     * @param tsColumnIndex the index of the timestamp column
     * @param chunkSize the number of rows
     * @return a List of timestamp to delimit each chunk
     * @throws SQLException
     */
    public static List getChunkDelimiters(ResultSet rs,
        int tsColumnIndex, int chunkSize) throws SQLException {

        ArrayList list = new ArrayList(10);
        int rowCount = 0;
        while (rs.next()) {
            if (++rowCount == chunkSize) {
                rowCount = 0;
                list.add(Long.valueOf( rs.getLong(tsColumnIndex) ));
            }
        }

        // Since more rows could potentially qualify at delete-time than at
        // select-time, the last chunk should include all records that might
        // be added 60 secs from now...
        list.add(Long.valueOf( System.currentTimeMillis() + 60000 ));

        return list;
    }

    /**
     * Class to encapsulate database transaction retry strategy.
     *
     * Using the default delay time of 2 secs and max number of retry of 5:
     *
     * Retry #     Delay Time     Total Time
     * -------     ----------     ----------
     * 1            2 secs          2 secs
     * 2            4 secs          6 secs
     * 3            8 secs         14 secs
     * 4           16 secs         30 secs
     * 5           32 secs         62 secs
     */
    public static class RetryStrategy {

        CommDBManager dbMgr = null;

        Exception originalException = null; // Original exception
        int retryCount = 0;                 // Keep track # retries
        int retryMax;                       // Max number of retry
        long delayTime;                     // Keep track of delay time
        boolean retryConnect = false;
        boolean retryConnectRequestOnly = false;

        public RetryStrategy() throws BrokerException {
            this( DBManager.getDBManager() );
        }

        // Bootstrap constructor
        public RetryStrategy( CommDBManager mgr ) {
            this(mgr, mgr.txnRetryDelay, mgr.txnRetryMax, false, false);
        }

        public RetryStrategy( CommDBManager mgr,
                              long retryDelay, int max, boolean retryConnect ) {
            this(mgr, retryDelay, max, retryConnect, false);
        }
        public RetryStrategy( CommDBManager mgr,
                              long retryDelay, int max, boolean retryConnect, 
                              boolean retryConnectRequestOnly ) {
            dbMgr = mgr;
            delayTime = retryDelay;
            retryMax = max;
            this.retryConnect = retryConnect;
            this.retryConnectRequestOnly = retryConnectRequestOnly;
        }

        /**
         * Assert if JDBC operation should be retry. This method will log and
         * re-throw the original exception if the operation should not be retry
         * or the retry count has reached the maximum number of retries.
         *
         * @param e the exception
         * @throws BrokerException
         */
        public boolean assertShouldRetry( Exception e ) throws BrokerException {
            return assertShouldRetry( e, null );
        }

        public boolean assertShouldRetry( Exception e, Connection conn )
         throws BrokerException {

            boolean replaycheck = false;
            boolean transientretry = false;
            boolean recoverableretry = false;
            boolean matchretry = false;

            // Save the original exception
            if ( originalException == null ) {
                originalException = e;
            }

            if ( e instanceof StoreBeingTakenOverException &&
                 Globals.getHAEnabled() ) {

                String msg = Globals.getBrokerResources().getKString(
                    BrokerResources.E_SPLIT_BRAIN );
                Globals.getLogger().logStack( Logger.ERROR, msg, e );

                Broker.getBroker().exit(BrokerStateHandler.getRestartCode(), msg,
                    BrokerEvent.Type.RESTART, e, true, false, true);

                // Re-throw the exception
                throw (StoreBeingTakenOverException)e;
            }
            if (dbMgr.getIsClosing()) {
                if (originalException instanceof BrokerException) {
                    throw (BrokerException)originalException;
                }
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.I_STORE_CLOSING), originalException);
            }

            Throwable cause = e;

            // Get cause of exception if it is wrapped in a BrokerException
            Throwable tmpe = e;
            while ( tmpe instanceof BrokerException ) {
                tmpe = tmpe.getCause();
                if (tmpe == null) {
                    break;
                }
                if ((tmpe instanceof SQLException) ||
                    (tmpe instanceof IOException)) {
                    cause = tmpe;
                    break;
                }
            }

            if (cause instanceof MQSQLException) {
                cause = ((SQLException)cause).getNextException();
            } else if (cause instanceof IOException) {
                if (isConnectionError(cause, dbMgr, false)) {
                    cause = new SQLException(cause.getMessage(), cause);
                }
            }
            
            boolean retry = false;  // Assume operation cannot be retry

            if ( cause instanceof SQLException ) {
                SQLException ex = (SQLException)cause;
                int errorCode = ex.getErrorCode();
                String sqlState = ex.getSQLState();

                if (retryConnect || 
                    (retryConnectRequestOnly && 
                     (e instanceof BrokerException) && 
                      ((BrokerException)e).getSQLReconnect())) {
                    if (dbMgr.TRANSIENT_CONNECTION_SQLEX_CLASS != null &&
                        dbMgr.TRANSIENT_CONNECTION_SQLEX_CLASS.isInstance(ex)) {
                        retry = true;
                        transientretry = true;
                    } else if ((dbMgr.TRANSIENT_SQLEX_CLASS != null &&
                                dbMgr.TRANSIENT_SQLEX_CLASS.isInstance(ex)) ||
                               (sqlState != null && 
                                (sqlState.startsWith("08") ||
                                (sqlState.startsWith("40"))))) {
                        retry = true;
                        transientretry = true;
                    } else if (dbMgr.TIMEOUT_SQLEX_CLASS != null &&
                               dbMgr.TIMEOUT_SQLEX_CLASS.isInstance(ex)) {
                        retry = true;
                    } else if (dbMgr.TRANSACTION_ROLLBACK_SQLEX_CLASS != null &&
                               dbMgr.TRANSACTION_ROLLBACK_SQLEX_CLASS.isInstance(ex)) {
                        retry = true;
                    } else if (dbMgr.RECOVERABLE_SQLEX_CLASS != null &&
                               dbMgr.RECOVERABLE_SQLEX_CLASS.isInstance(ex)) {
                        retry = true;
                        recoverableretry = true;
                    } else {
                        List<String> paterns = dbMgr.getReconnectPatterns();
                        Iterator<String> itr = paterns.iterator();
                        String p = null;
                        while (itr.hasNext()) {
                            try {
                                p = itr.next();
                                if (ex.getMessage().matches(p)) { 
                                    matchretry = true;
                                    retry = true;
                                    break;
                                } 
                            } catch (Exception ee) {
                                Globals.getLogger().logStack(Logger.WARNING, ee.getMessage(), ee);
                            }
                        }
                    }
                }

                if (retry) {
                } else if (dbMgr.TRANSIENT_SQLEX_CLASS != null &&
                           dbMgr.TRANSIENT_SQLEX_CLASS.isInstance(ex)) {
                    retry = true;
                    transientretry = true;
                } else if (dbMgr.RECOVERABLE_SQLEX_CLASS != null &&
                           dbMgr.RECOVERABLE_SQLEX_CLASS.isInstance(ex)) {
                    if (e instanceof BrokerException) {
                        if (((BrokerException)e).getSQLRecoverable()) {
                            retry = true;
                            recoverableretry = true;
                        }
                        if (((BrokerException)e).getSQLReplayCheck()) {
                            replaycheck = true;
                        }
                    }
                } else if (sqlState != null) {
                    if (sqlState.startsWith("40") ||
                        sqlState.startsWith("08")) {
                        if (e instanceof BrokerException) {
                            if (((BrokerException)e).getSQLRecoverable()) {
                                retry = true;
                                recoverableretry = true;
                            }
                            if (((BrokerException)e).getSQLReplayCheck()) {
                                replaycheck = true;
                            }
                        }
                    }
                } else if (isConnectionError(ex, dbMgr, false)) {
                    if (e instanceof BrokerException) {
                        if (((BrokerException)e).getSQLRecoverable()) {
                            retry = true;
                            recoverableretry = true;
                        }
                        if (((BrokerException)e).getSQLReplayCheck()) {
                            replaycheck = true;
                        }
                    }
                }

                if (!retry) {
                    if (e instanceof BrokerException) {
                        if (((BrokerException)e).getSQLRecoverable()) {
                            if (dbMgr.isOracle()) {
                                if (errorCode == 30006 ||
                                    errorCode == 28) /*00028*/ {
                                    retry = true;
                                    recoverableretry = true;
                                }
                            }
                            if (!retry && dbMgr.isRetriableSQLErrorCode(errorCode)) { 
                                retry = true;
                                recoverableretry = true;
                            }
                            if (((BrokerException)e).getSQLReplayCheck()) {
                                replaycheck = true;
                            }
                        }
                    }
                }

                if (retry) {
                } else if ( dbMgr.isHADB() ) {
                    retry =
                        errorCode == 224        // The operation timed out
                        || errorCode == 2078    // Wrong dictionary version no
                        || errorCode == 2080    // Too many locks set
                        || errorCode == 2096    // Too many locks held concurrently
                        || errorCode == 2097    // Upgrade from shared to exclusive lock failed
                        || errorCode == 4576    // Client held transaction open for too long
                        || errorCode == 12815   // Table memory space exhausted
                        || errorCode == 25012   // Timed out waiting for reply from peer
                        || errorCode == 25017   // No connection to server
                        || errorCode == 25018   // Lost connection to the server
                        ;
                } else if ( dbMgr.isOracle() ) {
                    retry =
                        errorCode == 00020      // Maximum number of processes num exceeded
                        || errorCode == 00054   // Resource busy and acquire with NOWAIT specified
                        || errorCode == 17008   // Closed Connection
                        || errorCode == 17009   // Closed Statement
                        || errorCode == 17016   // Statement timed out
                        || errorCode == 12535   // TNS:operation timed out
                        ;
                } else if ( dbMgr.isMysql() ) {
                    String emsg = ex.getMessage();
                    retry =
                        errorCode == 1205       // Lock wait timeout exceeded; try restarting transaction 
                        || errorCode == 1213    // Deadlock found when trying to get lock; try restarting transaction
                        || (emsg.trim().toLowerCase().contains("got temporary error") && 
                            emsg.trim().toLowerCase().contains("from ndb"))
                        || (emsg.trim().toLowerCase().contains("no operations allowed after connection closed"))
                        || (emsg.trim().toLowerCase().contains("lock wait timeout exceeded"));
                        ;
                } else if ( dbMgr.isDerby() ) {
                    retry =
                        sqlState.equals("40001") // deadlock
                        ;
                } else {
                    String msg = ex.getMessage();
                    retry = (msg != null &&
                             (msg.toLowerCase().indexOf( "timed out" ) >= 0 ||
                              msg.toLowerCase().indexOf( "rerun the transaction" ) >=0));
                }
            } else if (cause instanceof com.sun.messaging.jmq.io.PacketReadEOFException) {
                retry = true;
            }

            // Verify if we should retry the operation
            if ( retry && ( retryCount < retryMax ) ) {
                retryCount++;
                Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_RETRY_DB_OP, retryCount+","+delayTime, cause.getMessage()+
                    (cause instanceof SQLException ? "["+((SQLException)cause).getErrorCode()+
                    "]["+((SQLException)cause).getSQLState()+"]":""))+
                    "("+transientretry+","+recoverableretry+"["+replaycheck+"]"+","+matchretry+")");
                long remain = delayTime;
                while (remain > 0L && !dbMgr.getIsClosing()) {
                    try {
                    Thread.sleep( 1000L );
                    remain -= 1000L;
                    } catch (Exception ie) {}
                }
                if (dbMgr.getIsClosing()) {
                    if (originalException instanceof BrokerException) {
                        throw (BrokerException)originalException;
                    }
                    throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_STORE_CLOSING), originalException);
                }

                // Log error for debugging
                Globals.getLogger().logStack( Logger.DEBUG,
                    "Attempt to retry database operation due to unexpected error [retryCount="
                    + retryCount + ", delayTime=" + delayTime + "]", e );

                if (!(retryConnect && !retryConnectRequestOnly)) {
                    delayTime *= 2;
                }

                if (recoverableretry && conn != null) {
                    BrokerException be = new RetrySQLRecoverableException("SQLRecoverableException", cause);
                    be.setSQLReplayCheck(replaycheck);
                    throw be;
                }

                return replaycheck;
            }

            // Operation cannot be retry, so log & re-throw the original exception
            if ( !(originalException instanceof BrokerException) ) {
                // This shouldn't happen since DAO class suppose to wrap
                // all exception as BrokerException!!!
                originalException = new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Unable to retry database operation" ), originalException );
            }

            throw (BrokerException)originalException;
        }
    }

    /**
     */
    public static boolean isConnectionError(Throwable t, CommDBManager mgr) {
        return isConnectionError(t, mgr, true);
    }

    public static boolean isConnectionError(Throwable t,
        CommDBManager mgr, boolean aggressiveOnNPDS) {

        Throwable cause = t;

        if (t instanceof com.sun.messaging.jmq.jmsserver.util.DestinationNotFoundException ||
            t instanceof com.sun.messaging.jmq.jmsserver.persist.api.TakeoverLockException ||
            t instanceof com.sun.messaging.jmq.jmsserver.util.StoreBeingTakenOverException ||
            t instanceof com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException ||
            t instanceof com.sun.messaging.bridge.api.DupKeyException ||
            t instanceof com.sun.messaging.bridge.api.KeyNotFoundException) {
            return false;
        }

        if (t instanceof BrokerException) {
            cause = t.getCause();
        } 
        if (cause instanceof MQSQLException) {
            cause = ((MQSQLException)cause).getNextException(); 
        }
        
        if (!(cause instanceof SQLException) &&
            !(cause instanceof IOException)) {
            return false;
        }

        if (aggressiveOnNPDS && !mgr.isPoolDataSource()) {
            return true;
        }

        int eCode = 0;
        String eSQLState = null;
        String eMessage = cause.getMessage();

        if (cause instanceof SQLException) {
            eCode = ((SQLException)cause).getErrorCode();
            eSQLState = ((SQLException)cause).getSQLState();
        }

        if (mgr.TRANSIENT_CONNECTION_SQLEX_CLASS != null &&
            mgr.TRANSIENT_CONNECTION_SQLEX_CLASS.isInstance(cause)) {
            return true;
        }

        if (mgr.RECOVERABLE_SQLEX_CLASS != null &&
            mgr.RECOVERABLE_SQLEX_CLASS.isInstance(cause)) {
            return true;
        }

        if (mgr.isMysql()) {
            if (eMessage.contains("Communication link failure")) {
                return true;
            }
            if (eMessage.contains("No operations allowed after connection closed")) {
                return true;
            }
            if (eMessage.startsWith("Got temporary error") && eMessage.endsWith("from NDB")) {
                return true;
            }
            if (eCode == 1205 || eMessage.contains("Lock wait timeout exceeded")) {
                return true;
            }
        } else if (mgr.isOracle()) {
            if (eMessage.toLowerCase().contains("connection timed out")) {
                return true;
            }
            if (eCode == 30006 ||
                eCode == 54 ||
                eCode == 28 ||
                eCode == 12514 ||
                eCode == 12505 ||
                eCode == 1089 ||
                eCode == 1033 ||
                eCode == 12528) {
                return true;
            }
        }

        if (eSQLState != null) {
            if (mgr.getSQLStateType() == DatabaseMetaData.sqlStateSQL99) {
                if (eSQLState.startsWith("08") ||
                    eSQLState.equals("01002") ||
                    eSQLState.equals("04501") ||
                    eSQLState.equals("HYT00") ||
                    eSQLState.equals("HYT01") ||
                    eSQLState.equals("S1T00")) {
                    return true;
                }
                return false;
            } 
            if (mgr.getSQLStateType() == DatabaseMetaData.sqlStateXOpen) {
                if (eSQLState.startsWith("08")) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static String brokerNotTakenOverClause(DBManager dbMgr) throws BrokerException {
        return (Globals.getHAEnabled() ?
                        " AND NOT EXISTS (" +
                        ((BrokerDAOImpl)dbMgr.getDAOFactory().getBrokerDAO())
                        .selectIsBeingTakenOverSQL + ")" : "");
    }

    public static void checkBeingTakenOver(Connection conn, DBManager dbMgr, Logger logger,
                                           java.util.logging.Logger logger_)
                                           throws BrokerException {
        if (!Globals.getHAEnabled()) return; 

        String brokerID = dbMgr.getBrokerID();
        BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
        if (dao.isBeingTakenOver(conn, brokerID)) {
            BrokerException be = new StoreBeingTakenOverException(
                                 Globals.getBrokerResources().getKString(
                                 BrokerResources.E_STORE_BEING_TAKEN_OVER));
            try {
                 HABrokerInfo bkrInfo = dao.getBrokerInfo(conn, brokerID);
                 String emsg = be.getMessage()+"["+
                     (bkrInfo == null ? ""+brokerID:bkrInfo.toString())+"]";
                 logger.logStack(Logger.ERROR, emsg, be);
                 logExt(logger_, java.util.logging.Level.SEVERE, emsg, be);
            } catch (Throwable t) { /* Ignore error */ }
            throw be;
        }
    }


    public static void logExt(java.util.logging.Logger logger_, 
                              java.util.logging.Level level,
                              String emsg, Throwable t) {
        if (logger_ == null) return;
        if (t != null) {
            logger_.log(level, emsg, t);
        } else {
            logger_.log(level, emsg);
        }
    }
}
