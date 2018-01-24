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

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.io.*;
import java.util.Hashtable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;
import com.sun.messaging.jmq.httptunnel.tunnel.client.HttpTunnelClientDriver;

/**
 * This class implements socket-like interface for the HTTP tunnel
 * connections.
 */
public class HttpTunnelSocketImpl implements HttpTunnelSocket {
    private HttpTunnelConnection conn = null;

    private InputStream is = null;
    private OutputStream os = null;
    private boolean sockClosed = false;

    public HttpTunnelSocketImpl() { }

    /**
     * Creates a socket and establishes a connection with the specified
     * server address.
     */
    public void init(String serverAddr) throws IOException {
        HttpTunnelClientDriver wire = new HttpTunnelClientDriver(serverAddr);
        conn = wire.doConnect();
        initSocket();
    }

    /**
     * Creates a socket with a given HTTP tunnel connection. Used
     * internally by the server socket (accept) implementation.
     */
    public HttpTunnelSocketImpl(HttpTunnelConnection conn) {
        this.conn = conn;
        initSocket();
    }

    protected void initSocket() {
        is = null; // Will be created on demand
        os = null; // Will be created on demand
        sockClosed = false;
    }

    /**
     * Returns an input stream for this socket.
     */
    public synchronized InputStream getInputStream() throws IOException {
        if (sockClosed) {
            throw new IOException("Socket closed");
        }
        if (is == null)
            is = new HttpTunnelInputStream(conn);
        return is;
    }

    /**
     * Returns an output stream for this socket.
     */
    public synchronized OutputStream getOutputStream() throws IOException {
        if (sockClosed) {
            throw new IOException("Socket closed");
        }
        if (os == null)
            os = new HttpTunnelOutputStream(conn);
        return os;
    }

    /**
     * Close this socket.
     */
    public synchronized void close() throws IOException {
        if (is != null)
            is.close();
        if (os != null)
            os.close();
        sockClosed = true;
        conn.closeConn();
    }

    /**
     * Get the unique connection ID.
     */
    public int getConnId() {
        return conn.getConnId();
    }

    public InetAddress getRemoteAddress() 
        throws UnknownHostException, SecurityException { 

        HttpTunnelConnection c = conn;
        if (c == null || c.getRemoteAddr() == null) return null;
        return InetAddress.getByName(c.getRemoteAddr());
    }

    public int getPullPeriod() {
        return conn.getPullPeriod();
    }

    public void setPullPeriod(int pullPeriod) throws IOException {
        conn.setPullPeriod(pullPeriod);
    }

    public int getConnectionTimeout() {
        return conn.getConnectionTimeout();
    }

    public void setConnectionTimeout(int connectionTimeout)
        throws IOException {
        conn.setConnectionTimeout(connectionTimeout);
    }

    public Hashtable getDebugState() {
        return conn.getDebugState();
    }
}

/*
 * EOF
 */
