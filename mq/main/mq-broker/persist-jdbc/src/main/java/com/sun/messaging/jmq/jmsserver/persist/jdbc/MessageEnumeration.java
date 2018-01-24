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

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.io.Packet;

import java.util.*;
import java.io.IOException;
import java.sql.*;

public class MessageEnumeration implements Enumeration {

        ResultSet rs = null;
        PreparedStatement pstmt = null;
        Connection conn = null;
        String sql = null; 
        MessageDAOImpl dao = null;
        Packet nextPacket = null;
        Store store = null;
        boolean closed = false;

        MessageEnumeration( ResultSet rs, PreparedStatement pstmt,
                            Connection conn, String sql, 
                            MessageDAOImpl dao, Store store ) {
            this.rs = rs;
            this.pstmt = pstmt;
            this.conn = conn;
            this.sql = sql;
            this.dao = dao;
            this.store = store;
        }

        public boolean hasMoreElements() {
            try {
                nextPacket = (Packet)dao.loadData( rs, true );
                if (nextPacket == null) {
                    if (setClosed()) {
                        try {
                            Util.close(rs, pstmt, conn, null); 
                        } catch (Throwable t) {
                            dao.logger.log(Logger.WARNING, dao.br.getKString(
                            BrokerResources.W_EXCEPTION_CLOSE_MSG_ENUM_RESOURCE, t.toString()));
                        }
                    }
                }
            } catch (Throwable e) {
                Throwable myex = e;
                nextPacket = null;
                if ( e instanceof IOException ) {
                    myex = DBManager.wrapIOException("[" + sql + "]", (IOException)e);
                } else if ( e instanceof SQLException ) {
                    myex = DBManager.wrapSQLException("[" + sql + "]", (SQLException)e);
                } 
                dao.logger.logStack(Logger.ERROR, myex.getMessage(), e);
                if (setClosed()) {
                    try {
                        Util.close(rs, pstmt, conn, myex);
                    } catch (Exception ee) {
                        dao.logger.log(Logger.WARNING, dao.br.getKString(
                        BrokerResources.W_EXCEPTION_CLOSE_MSG_ENUM_RESOURCE, ee.toString()));
                    }
                }
            }
            return (nextPacket != null);
        }

        public Object nextElement() {
            if (nextPacket == null) {
                throw new NoSuchElementException();
            }
            if (store.isClosed()) {
                throw new NoSuchElementException(
                dao.br.getKString(BrokerResources.I_STORE_CLOSING));
            }
            return nextPacket;
        }

        public void cancel() {
            
            if (!closed) {
                try {
                     pstmt.cancel();
                } catch (Throwable t) {
                     dao.logger.log(Logger.WARNING, dao.br.getKString(
                     BrokerResources.W_EXCEPTION_CANCEL_MSG_ENUM, 
                     "["+sql+"]", t.toString()));
                }
            }
        }

        private synchronized boolean setClosed() {
            if (!closed) {
                closed = true;
                return true;
            }
            return false;
        }

        public void close() {
            if (setClosed()) {
                try {
                    Util.close(rs, pstmt, conn, null);
                } catch (Exception e) {
                    dao.logger.log(Logger.WARNING, dao.br.getKString(
                    BrokerResources.W_EXCEPTION_CLOSE_MSG_ENUM_RESOURCE, e.toString()));
                }
            }
        }
}
