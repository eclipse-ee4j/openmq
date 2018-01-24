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

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import java.sql.*;
import java.util.HashMap;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;


/**
 */
class MySQLMessageDAOImpl extends MessageDAOImpl {

     private static String PROC_DELETE = null;
     private String dropStoredProcSQL = null;

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    MySQLMessageDAOImpl() throws BrokerException {
        super();

        PROC_DELETE = 
            "MQ"+JDBCStore.STORE_VERSION+"SP0MSG"+JDBCStore.STORED_PROC_VERSION+
             DBManager.getDBManager().getTableSuffix();

        dropStoredProcSQL = new StringBuffer(128)
            .append( "DROP PROCEDURE IF EXISTS "+PROC_DELETE)
            .toString();
    }

    @Override
    public void createStoredProc( Connection conn ) throws BrokerException {

        boolean myConn = false;
        Exception myex = null;
        String sql = "";
        Statement stmt = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true; // Set to true since this is our connection
            }

            sql = new StringBuffer(128)
            .append( "CREATE PROCEDURE " ).append( PROC_DELETE )
            .append( "( IN msgID VARCHAR (100), " )
            .append(   "IN brokerID VARCHAR(100), " )
            .append(   "OUT row_affected INT, ")
            .append(   "OUT beingTakenOver INT, ")
            .append(   "OUT brokerState INT )" )
            .append( " BEGIN " )
            .append( " DECLARE not_found INT; " )
            .append( " DECLARE CONTINUE HANDLER FOR NOT FOUND SET not_found=1; " )
            .append( " SET not_found=0; " )
            .append( " SET row_affected=1; " )
            .append( " SET beingTakenOver=0; " )
            .append( " SET autocommit=0; " )
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = " ).append( "msgID; " )
            .append( " IF not_found = 1 THEN " )
              .append( " SET row_affected=0; " )
            .append( " END IF; ")
            .append( " IF not_found = 0 AND brokerID IS NOT NULL THEN " )
              .append( " CALL "+MySQLBrokerDAOImpl.PROC_IS_BEING_TAKENOVER+"(")
              .append( "brokerID, beingTakenOver, brokerState); ") 
            .append( " END IF; " )
            .append( " IF beingTakenOver = 0 THEN ") 
              .append( "DELETE FROM " ).append( dbMgr.getDAOFactory().
                                 getConsumerStateDAO().getTableName() )
              .append( " WHERE " )
              .append( ConsumerStateDAO.MESSAGE_ID_COLUMN ).append( " = msgID; " )
            .append( " END IF; " )
            .append ( "END;" ).toString();

            stmt = conn.createStatement();
            try {
                dbMgr.executeUpdateStatement(stmt, sql);
            } catch (SQLException ee) {
                int ec = ee.getErrorCode();
                String et = ee.getSQLState();
                if (!(ec == 1304 && (et == null || et.equals("42000")))) {
                    throw ee;
                } else {
                    logger.log(Logger.INFO, 
                    br.getKString(br.I_STORED_PROC_EXISTS, PROC_DELETE));
                    return;
                }
            }

            Globals.getLogger().log(Logger.INFO, br.getKString(
                BrokerResources.I_CREATED_STORED_PROC, PROC_DELETE));
            if (DEBUG) {
                Globals.getLogger().log(Logger.INFO,  sql);
            }

        } catch (Exception e) {
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

            throw new BrokerException("Failed to execute "+sql, ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }

    @Override
    public void dropStoredProc( Connection conn ) throws BrokerException {

        boolean myConn = false;
        Exception myex = null;
        String sql = dropStoredProcSQL;
        Statement stmt = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( true );
                myConn = true; // Set to true since this is our connection
            }
            stmt = conn.createStatement();
            dbMgr.executeStatement(stmt, sql);
            if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,  "DONE "+sql);
            }

        } catch (Exception e) {
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

            throw new BrokerException("Failed to execute "+sql, ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }



    @Override
    public void delete( Connection conn, 
                        DestinationUID dstUID,
                        String id, boolean replaycheck )
                        throws BrokerException {
    
        boolean myConn = false;
        CallableStatement stmt = null;
        Exception myex = null;
        String sql = "{call "+PROC_DELETE +" (?, ?, ?, ?, ?)}";
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if ( conn == null ) {
                conn = dbMgr.getConnection( false );
                myConn = true; // Set to true since this is our connection
            }

            if (fi.FAULT_INJECTION) {
                HashMap fips = new HashMap();
                fips.put(FaultInjection.DST_NAME_PROP,
                     DestinationUID.getUniqueString(dstUID.getName(), dstUID.isQueue()));
                fi.checkFaultAndExit(FaultInjection.FAULT_TXN_COMMIT_1_8, fips, 2, false);
            }
            if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,  "before call "+sql+"("+id+")");    
            }
            String brokerid = dbMgr.getBrokerID();
            stmt = conn.prepareCall(sql);
            stmt.setString(1, id);
            if (Globals.getHAEnabled()) {
                stmt.setString(2,  brokerid);
            } else {
                stmt.setNull(2,  Types.VARCHAR);
            }
            stmt.registerOutParameter(3,  Types.INTEGER);
            stmt.registerOutParameter(4,  Types.INTEGER);
            stmt.registerOutParameter(5,  Types.INTEGER);
            stmt.execute();
            int row_affected = stmt.getInt(3);
            int beingTakenOver = stmt.getInt(4);
            int bstate = stmt.getInt(5);
            if (Globals.getHAEnabled() && beingTakenOver != 0 ) {
                HABrokerInfo binfo = null;
                try {
                    binfo = dbMgr.getDAOFactory().getBrokerDAO().
                                  getBrokerInfo(conn, brokerid);
                } catch (Exception e) {
                    /* ignore */
                }
                throw new StoreBeingTakenOverException(
                    br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER)+
                    "["+BrokerState.getState(bstate).toString()+"]"+binfo);
            }
            if (row_affected == 0) {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_MSG_NOT_FOUND_IN_STORE,
                    id, dstUID ), Status.NOT_FOUND );
            }
            if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,  "After call "+sql+"("+id+")");    
            }

            if (myConn) {
                conn.commit();
            }

        } catch (Exception e) {
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

            throw new BrokerException("Failed to execute "+sql, ex);
        } finally {
            if ( myConn ) {
                closeSQLObjects( null, stmt, conn, myex );
            } else {
                closeSQLObjects( null, stmt, null, myex );
            }
        }
    }

}
