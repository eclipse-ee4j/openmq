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
 * @(#)ServerLink.java	1.10 09/11/07
 */ 
 
package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

import com.sun.messaging.jmq.httptunnel.tunnel.*;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.io.*;

import java.net.*;

import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;


/**
 * This class implements the servlet end of the link between the
 * servlet and the server application (JMQ broker).
 */
public class ServerLink extends Link implements HttpTunnelDefaults {
    private static boolean DEBUG = Boolean.getBoolean("httptunnel.debug");
    private Socket serverConn = null;
    private ServerLinkTable parent = null;
    private String serverName = null;
    private boolean listenState = false;
    private boolean serverReady = false;

    public ServerLink(Socket serverConn, ServerLinkTable parent)
        throws IOException {
        try {
        serverConn.setTcpNoDelay(true);
        } catch (SocketException e) {
        parent.servletContext.log("WARNING: HttpTunnelTcpLink()["+serverConn.toString()+
                                  "]setTcpNoDelay: " + e.toString(), e);
        }

        this.serverConn = serverConn;
        this.parent = parent;

        is = serverConn.getInputStream();
        os = serverConn.getOutputStream();

        setName("HttpTunnelTcpLink[" + serverConn + "]");

        start();
    }

    protected void createLink() {
    }

    protected void handleLinkDown() {
        try {
            serverConn.close();
        } catch (Exception e) {
        }

        parent.serverDown(this);
    }

    protected void linkDown() {
        super.linkDown();
    }

    protected boolean isDone() {
        return done;
    }

    protected String getServerName() {
        return serverName;
    }

    protected boolean getListenState() {
        return listenState;
    }

    /**
     * Enqueue the packet to the appropriate connection queue.
     * This should wakeup the appropriate pull thread.
     */
    protected void receivePacket(HttpTunnelPacket p) {
        if (serverReady) {
            if (p.getPacketType() == LISTEN_STATE_PACKET) {
                receiveListenStatePacket(p);

                return;
            }

            if (DEBUG) {
                log("Received Packet : " + p);
            }

            parent.receivePacket(p, this);

            return;
        }

        if (p.getPacketType() != LINK_INIT_PACKET) {
            parent.servletContext.log("HttpTunnelServlet: ServerLink[" +
                serverName + "] received " + "unexpected packet type " +
                p.getPacketType());
            shutdown();
            linkDown();

            return;
        }

        // Recreate the connection table...
        byte[] buf = p.getPacketBody();
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bis);

        try {
            serverName = dis.readUTF();

            int n = dis.readInt();

            for (int i = 0; i < n; i++) {
                int connId = dis.readInt();
                int pullPeriod = dis.readInt();
                parent.updateConnection(connId, pullPeriod, this);
            }

            parent.servletContext.log("HttpTunnelServlet: ServerLink[" +
                serverName + "]" + " link initialized");

            parent.updateServerName(this);

            serverReady = true;
        } catch (Exception e) {
            parent.servletContext.log("HttpTunnelServlet: ServerLink[" +
                serverName + "]" + " init link failed: " + e.getMessage(), e);
            shutdown();
            linkDown();
        }
    }

    private void receiveListenStatePacket(HttpTunnelPacket p) {
        byte[] buf = p.getPacketBody();

        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bis);

        String sname = null;

        try {
            sname = dis.readUTF();
            listenState = dis.readBoolean();
        } catch (Exception e) {
            parent.servletContext.log("HttpTunnelServlet: ServerLink[" + sname +
                "]" + " receiveListenStatePacket failed: " + e.getMessage(), e);
            shutdown();
            linkDown();
        }
    }
}

/*
 * EOF
 */
