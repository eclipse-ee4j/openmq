/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import com.sun.messaging.jmq.jmsserver.Globals;

import java.sql.*;
import javax.sql.PooledConnection;
import javax.sql.ConnectionEventListener;

class ConnectionInfo {

    Object conn;
    Throwable thr = null;
    ConnectionEventListener listener = null;
    boolean validating = false;
    long idleStartTime = System.currentTimeMillis();
    long creationTime = System.currentTimeMillis();

    ConnectionInfo(Object conn, ConnectionEventListener listener) {
        this.conn = conn;
        this.listener = listener;
        if (conn instanceof PooledConnection) {
            ((PooledConnection) conn).addConnectionEventListener(listener);
        }
    }

    public Object getKey() {
        return conn;
    }

    public void idleStart() {
        idleStartTime = System.currentTimeMillis();
    }

    public long getIdleStartTime() {
        return idleStartTime;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setValidating(boolean b) {
        validating = b;
    }

    public boolean inValidating() {
        return validating;
    }

    public void setException(Throwable t) {
        thr = t;
    }

    public Throwable getException() {
        return thr;
    }

    public Connection getConnection() throws SQLException {
        if (conn instanceof PooledConnection) {
            return ((PooledConnection) conn).getConnection();
        } else {
            return (Connection) conn;
        }
    }

    public void destroy() {
        try {
            if (conn instanceof PooledConnection) {
                ((PooledConnection) conn).removeConnectionEventListener(listener);
                ((PooledConnection) conn).close();
            } else {
                ((Connection) conn).close();
            }
        } catch (Throwable t) { //NOPMD
            Globals.getLogger().log(Globals.getLogger().WARNING, Globals.getBrokerResources().W_DB_CONN_CLOSE_EXCEPTION, this.toString(), t.toString());
        }
    }

    @Override
    public String toString() {
        return (conn instanceof Connection ? "[Connection" : "[PooledConnection") + ":0x" + conn.hashCode() + ", lastIdleTime=" + idleStartTime
                + (thr == null ? "" : ", " + thr.toString()) + "]";
    }
}
