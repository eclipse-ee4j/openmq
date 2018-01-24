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
 
package com.sun.messaging.jmq.httptunnel.tunnel.server;

import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelConnection;
import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelDriver;
import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelPacket;
import com.sun.messaging.jmq.httptunnel.tunnel.Link;
import com.sun.messaging.jmq.httptunnel.api.server.HttpTunnelServerDriver;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class provides unreliable packet delivery mechanism on
 * the server side. It also uses a dedicated thread to continuously
 * read incoming packets from the servlet over the TCP connection.
 */
public class HttpTunnelServerDriverImpl extends Link implements HttpTunnelDefaults,
    HttpTunnelDriver, HttpTunnelServerDriver {
    private static boolean DEBUG = Boolean.getBoolean("httptunnel.debug");

    public static boolean getDEBUG() {
        return DEBUG;
    }

    protected static final boolean DEBUGLINK = Boolean.getBoolean(
            "httptunnel.link.debug");

    protected Socket serverConn = null;
    protected Hashtable connTable = null;
    protected String serviceName;
    protected InetAddress webServerHost = null;
    protected int webServerPort = 0;
    protected int inactiveConnAbortInterval = MAX_CONNECTION_RETRY_WAIT;
    protected int totalRetryWaited = 0;
    private Vector listenQ = null;
    private boolean listenState = false;
    protected int rxBufSize = 0;

    public HttpTunnelServerDriverImpl() {}

    /**
     * Creates an HTTP tunnel interface.
     */
    public void init(String serviceName) throws IOException {
        init(serviceName, InetAddress.getLocalHost().getHostAddress(),
            DEFAULT_HTTP_TUNNEL_PORT);
    }

    /**
     * Creates an HTTP tunnel interface.
     */
    public void init(String serviceName, String webServerHostName,
        int webServerPort) throws IOException {
        this.serviceName = serviceName;

        this.webServerHost = InetAddress.getByName(webServerHostName);
        this.webServerPort = webServerPort;

        connTable = new Hashtable();
        listenQ = new Vector();
        setName("HttpTunnelServerDriver");
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    public Vector getListenQ() {
        return listenQ;
    }

    public void listen(boolean listenState) throws IOException {
        boolean oldListenState = this.listenState;
        this.listenState = listenState;

        if (listenState != oldListenState) {
            // Inform the servlet now to start or stop accepting
            // new connections.
            sendListenStatePacket();
        }
    }

    public int getInactiveConnAbortInterval() {
        return inactiveConnAbortInterval;
    }

    public void setInactiveConnAbortInterval(int inactiveConnAbortInterval) {
        this.inactiveConnAbortInterval = inactiveConnAbortInterval;
    }

    /**
     * Waits for a TCP connection from the servlet. When
     * <code>accept</code> returns successfully, this method sends
     * the current state of the connection table to the servlet
     * and resumes normal operation.
     */
    protected void createLink() {
        totalRetryWaited = 0;

        if (DEBUG) {
            log("http:connecting to " + webServerHost + ":" + webServerPort);
        }

        while (true) {
            try {
                /*
                 * The Socket.setReceiveBufferSize() method must be
                 * called before the connection is established.
                 */
                serverConn = new Socket();

                if (rxBufSize > 0) {
                    try {
                    serverConn.setReceiveBufferSize(rxBufSize);
                    } catch (SocketException e) {
                    log(Level.WARNING, "HTTP socket["+webServerHost+":"+webServerPort+
                              "]setReceiveBufferSize("+rxBufSize+"): "+e.toString(), e);
                    }
                }

                InetSocketAddress addr = new InetSocketAddress(webServerHost,
                        webServerPort);
                serverConn.connect(addr);

                try {
                serverConn.setTcpNoDelay(true);
                } catch (SocketException e) {
                log(Level.WARNING, "HTTP socket["+webServerHost+":"+webServerPort+
                                   "]setTcpNoDelay: "+e.toString(), e);
                }

                if (DEBUG) {
                    log("######## rcvbuf = " +
                        serverConn.getReceiveBufferSize());
                }

                is = serverConn.getInputStream();
                os = serverConn.getOutputStream();

                if (DEBUG || DEBUGLINK) {
                    log("Broker HTTP link up");
                }

                totalRetryWaited = 0;

                break;
            } catch (Exception e) {
            }

            try {
                Thread.sleep(CONNECTION_RETRY_INTERVAL);
                totalRetryWaited += CONNECTION_RETRY_INTERVAL;

                if (totalRetryWaited >= (inactiveConnAbortInterval * 1000)) {
                    if (DEBUG || DEBUGLINK) {
                        log("Retry connect to servlet timeout " +
                            "- cleanup all (" + connTable.size() +
                            ") connections ...");
                    }

                    cleanupAllConns();
                    totalRetryWaited = 0;
                }
            } catch (Exception se) {
            }
        }

        sendLinkInitPacket();
        sendListenStatePacket();
    }

    protected void sendListenStatePacket() {
        HttpTunnelPacket p = new HttpTunnelPacket();
        p.setPacketType(LISTEN_STATE_PACKET);
        p.setConnId(0);
        p.setSequence(0);
        p.setWinsize(0);
        p.setChecksum(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            dos.writeUTF(serviceName);
            dos.writeBoolean(listenState);
            dos.flush();
            bos.flush();
        } catch (Exception e) {
            if (DEBUG || DEBUGLINK) {
                log("Got exception while sending LISTEN_STATE_PACKET " +
                    "packet: " + e.getMessage());
            }
        }

        byte[] buf = bos.toByteArray();
        p.setPacketBody(buf);

        sendPacket(p);
    }

    void sendLinkInitPacket() {
        // Help the servlet/web server recreate the connection table...
        HttpTunnelPacket p = new HttpTunnelPacket();
        p.setPacketType(LINK_INIT_PACKET);
        p.setConnId(0);
        p.setSequence(0);
        p.setWinsize(0);
        p.setChecksum(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            dos.writeUTF(serviceName);
            dos.writeInt(connTable.size());

            for (Enumeration e = connTable.elements(); e.hasMoreElements();) {
                HttpTunnelConnection conn = (HttpTunnelConnection) e.nextElement();
                dos.writeInt(conn.getConnId());
                dos.writeInt(conn.getPullPeriod());
            }

            dos.flush();
            bos.flush();
        } catch (Exception e) {
            if (DEBUG || DEBUGLINK) {
                log("Got exception while sending LINK_INIT_PACKET " +
                    "packet: " + e.getMessage());
            }
        }

        byte[] buf = bos.toByteArray();
        p.setPacketBody(buf);

        sendPacket(p);
    }

    //to be called only from createLink()
    protected void cleanupAllConns() {
        Vector connids = new Vector();

        synchronized (connTable) {
            for (Enumeration e = connTable.keys(); e.hasMoreElements();) {
                connids.addElement((String) e.nextElement());
            }
        }

        HttpTunnelConnection conn;
        HttpTunnelPacket pkt;
        String connId;

        for (int i = connids.size() - 1; i >= 0; i--) {
            connId = (String) connids.elementAt(i);
            conn = (HttpTunnelConnection) connTable.get(connId);

            if (conn != null) {
                pkt = genAbortPacket(conn.getConnId());
                receivePacket(pkt);
            }
        }

        connTable.clear();
        connids = null;
    }

    /**
     * Handle TCP connection failure.
     */
    protected void handleLinkDown() {
        if (DEBUG || DEBUGLINK) {
            if (serverConn != null) {
                log("Broker HTTP link down");
            }
        }

        try {
            serverConn.close();
        } catch (Exception e) {
        }

        serverConn = null;
    }

    public boolean isLinkReady() {
        return (serverConn != null);
    }

    /**
     * Receive a packet from the network side. (i.e. from the servlet).
     */
    protected void receivePacket(HttpTunnelPacket p) {
        int packetType = p.getPacketType();

        if (DEBUG) {
            log("Received Packet : " + p);
        }

        switch (packetType) {
        case CONN_INIT_PACKET:
            handleNewConn(p);

            break;

        case CONN_CLOSE_PACKET:
            handleConnClose(p);

            break;

        case CONN_ABORT_PACKET:
            handleConnAbort(p);

            break;

        case CONN_OPTION_PACKET:
            handleConnOption(p);

            break;

        case DATA_PACKET:
        case ACK:
            handlePacket(p);

            break;

        case DUMMY_PACKET:
            handleDummyPacket(p);

            break;

        default:
            break;
        }
    }

    /**
     * Handles new connection requests.
     * The new connection is added to the listen queue resulting in
     * waking up a thread blocked in accept() if any.
     */
    private void handleNewConn(HttpTunnelPacket p) {
        int connId = p.getConnId();
        String remoteip = null;
        byte[] payload = p.getPacketBody();
        if (payload != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(payload);
            DataInputStream dis = new DataInputStream(bis);
            try {
                //String sname = dis.readUTF();
                dis.readUTF();
                remoteip = dis.readUTF();
            } catch (Exception e) {
                log(Level.WARNING, "Got exception while reading CONN_INIT_PACKET " + e.getMessage(), e);
            }
            try {
                dis.close();
            } catch (Exception e) {}
        }
        HttpTunnelConnection conn = new HttpTunnelConnection(connId, this);
        conn.setRemoteAddr(remoteip);

        synchronized (connTable) {
            connTable.put(Integer.toString(connId), conn);
        }

        HttpTunnelPacket reply = new HttpTunnelPacket();
        reply.setPacketType(CONN_INIT_ACK);
        reply.setPacketBody(null);
        reply.setConnId(connId);
        reply.setSequence(0);
        reply.setWinsize(0);
        reply.setChecksum(0);

        sendPacket(reply);

        synchronized (listenQ) {
            listenQ.addElement(conn);
            listenQ.notifyAll();
        }
    }

    /**
     * Handle connection close request.
     */
    private void handleConnClose(HttpTunnelPacket p) {
        int connId = p.getConnId();

        HttpTunnelConnection conn;

        synchronized (connTable) {
            conn = (HttpTunnelConnection) connTable.get(Integer.toString(connId));
        }

        if (conn == null) {
            return;
        }

        conn.handleClose(p);
    }

    /**
     * Handle connection close request.
     */
    private void handleConnAbort(HttpTunnelPacket p) {
        int connId = p.getConnId();

        HttpTunnelConnection conn;

        synchronized (connTable) {
            conn = (HttpTunnelConnection) connTable.get(Integer.toString(connId));
        }

        if (conn == null) {
            return;
        }

        conn.handleAbort(p);
    }

    /**
     * Handle connection close request.
     */
    private void handleConnOption(HttpTunnelPacket p) {
        int connId = p.getConnId();

        HttpTunnelConnection conn;

        synchronized (connTable) {
            conn = (HttpTunnelConnection) connTable.get(Integer.toString(connId));
        }

        if (conn == null) {
            return;
        }

        conn.handleConnOption(p);
    }

    /**
     * Handles data packets from the network.
     */
    private void handlePacket(HttpTunnelPacket p) {
        int connId = p.getConnId();

        HttpTunnelConnection conn;

        synchronized (connTable) {
            conn = (HttpTunnelConnection) connTable.get(Integer.toString(connId));
        }

        if (conn == null) {
            // TBD : Error - Tell the client ???
            return;
        }

        conn.receivePacket(p, false);
    }

    private void handleDummyPacket(HttpTunnelPacket p) {
        if (DEBUG) {
            log("#### Received dummy packet :");
            log(p + "\n");
        }
    }

    public synchronized void shutdown(int connId) {
        synchronized (connTable) {
            connTable.remove(Integer.toString(connId));
        }

        // Also, tell the servlet to do the same...
        // Note : this packet is NOT delivered to the other end.
        // It just tells the servlet to cleanup its connTable entry..
        HttpTunnelPacket p = new HttpTunnelPacket();
        p.setPacketType(CONN_SHUTDOWN);
        p.setPacketBody(null);
        p.setConnId(connId);
        p.setSequence(0);
        p.setWinsize(0);
        p.setChecksum(0);

        sendPacket(p);
    }

    private static HttpTunnelPacket genAbortPacket(int connId) {
        HttpTunnelPacket p = new HttpTunnelPacket();
        p.setPacketType(CONN_ABORT_PACKET);
        p.setConnId(connId);
        p.setSequence(0);
        p.setWinsize(0);
        p.setChecksum(0);
        p.setPacketBody(null);

        return p;
    }

    public Hashtable getDebugState() {
        return new Hashtable();
    }
}

/*
 * EOF
 */
