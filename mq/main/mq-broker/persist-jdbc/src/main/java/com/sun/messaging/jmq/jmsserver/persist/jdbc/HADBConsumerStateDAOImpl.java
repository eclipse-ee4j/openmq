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
 * @(#)HADBConsumerStateDAOImpl.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;

import java.sql.*;

/**
 * This class implements ConsumerDAO interface for HADB.
 */
class HADBConsumerStateDAOImpl extends ConsumerStateDAOImpl {

    /**
     * Constructor
     * @throws com.sun.messaging.jmq.jmsserver.util.BrokerException
     */
    HADBConsumerStateDAOImpl() throws BrokerException {

        super();
    }

    /**
     * Delete all entries.
     */
    protected void deleteAll( Connection conn, String whereClause,
        String timestampColumn, int chunkSize ) throws BrokerException {

        super.deleteAll( conn, whereClause, CREATED_TS_COLUMN, HADB_CHUNK_SIZE );
    }

    /**
     * Update existing entry.
     * @param conn database connection
     * @param dstUID the destination ID
     * @param sysMsgID the system message ID
     * @param conUID the consumer id
     * @param state the state
     * @throws BrokerException
     */
    public void updateState( Connection conn, DestinationUID dstUID,
        SysMessageID sysMsgID, ConsumerUID conUID, int state )
        throws BrokerException {

        String msgID = sysMsgID.getUniqueName();

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

            if ( Globals.getHAEnabled() ) {
                BrokerDAO dao = dbMgr.getDAOFactory().getBrokerDAO();
                if ( dao.isBeingTakenOver( conn, dbMgr.getBrokerID() ) ) {
                    BrokerException be = new StoreBeingTakenOverException(
                        br.getKString( BrokerResources.E_STORE_BEING_TAKEN_OVER ) );
                    throw be;
                }
            }

            pstmt = dbMgr.createPreparedStatement( conn, updateStateSQL );
            pstmt.setInt( 1, state );
            pstmt.setString( 2, msgID );
            pstmt.setLong( 3, conUID.longValue() );

            if ( pstmt.executeUpdate() == 0 ) {
                // Otherwise we're assuming the entry does not exist
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_STATE_NOT_FOUND_IN_STORE,
                    conUID.toString(), msgID ), Status.NOT_FOUND );
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
                ex = DBManager.wrapSQLException("[" + updateStateSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_STATE_FAILED,
                conUID.toString(), sysMsgID.toString() ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }
}
