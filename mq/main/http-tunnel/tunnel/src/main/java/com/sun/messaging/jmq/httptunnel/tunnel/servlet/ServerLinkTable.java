/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelPacket;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import java.net.Socket;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

public class ServerLinkTable implements HttpTunnelDefaults {
    private static final int RUNNING = 0;
    private static final int SHUTTINGDOWN = 1;
    private static final int DESTROYED = 2;
    private int linkTableState = RUNNING;
    private int servletPort;
    private int rxBufSize;
    private String servletHost = null;
    private HelperThread helperThread;
    private Hashtable linkTable; // Maps serverName <--> ServerLink
    private Hashtable connTable; // Maps (serverName, connId) <--> Connection
    private Vector tmpList;
    private int nextConnId = 0;
    private long lastCheck = 0;
    protected ServletContext servletContext;

    public ServerLinkTable(ServletConfig cfg) throws Exception {
        this(cfg, false);
    }

    public ServerLinkTable(ServletConfig cfg, boolean useSSL) throws Exception {
        servletContext = cfg.getServletContext();
        linkTableState = RUNNING;
        nextConnId = (int) System.currentTimeMillis();
        lastCheck = 0;

        linkTable = new Hashtable();
        connTable = new Hashtable();
        tmpList = new Vector();

        rxBufSize = 0;

        String rxBufSizeStr = cfg.getInitParameter("rxBufSize");

        if (rxBufSizeStr != null) {
            try {
                rxBufSize = Integer.parseInt(rxBufSizeStr);
            } catch (Exception e) {
                servletContext.log("Exception in HttpTunnelServlet : " + e.getMessage());
            }
        }

        // determine port number to use
        int defaultPort = (useSSL ? DEFAULT_HTTPS_TUNNEL_PORT : DEFAULT_HTTP_TUNNEL_PORT);

        String servletPortString = cfg.getInitParameter("servletPort");

        if (servletPortString == null) {
            // check if the user has specified "serverPort" as the servlet init parameters
            // TODO remove this line: this is kept for backward compatability, one shd use servletPort instead of serverPort
            servletPortString = cfg.getInitParameter("serverPort");
        }

        if (servletPortString != null) {
            try {
                servletPort = Integer.parseInt(servletPortString);
            } catch (Exception e) {
                servletPort = defaultPort;
            }
        } else {
            servletPort = defaultPort;
        }

        servletHost = cfg.getInitParameter("servletHost");

        if (servletHost == null) {
            // check if the user has specified "serverHost" as the servlet init parameters
            // TODO remove this line: this is kept for backward compatability, one shd use servletHost instead of serverHost
            servletHost = cfg.getInitParameter("serverHost");
        }

        if (useSSL) {
            String keystoreloc = cfg.getInitParameter("keystoreLocation");

            if ((keystoreloc == null) || keystoreloc.equals("")) {
                throw new Exception("keystore location not specified");
            }

            String kspassword = cfg.getInitParameter("keystorePassword");

            if ((kspassword == null) || kspassword.equals("")) {
                throw new Exception("keystore password not specified");
            }

            helperThread = new HelperThread(servletPort, servletHost, rxBufSize, keystoreloc, kspassword, this);
        } else {
            helperThread = new HelperThread(servletPort, servletHost, rxBufSize, this);
        }

        helperThread.start();
    }

    // called from Servlet.destroy() - may have service threads running
    public void shuttingDown() {
        // stop listen thread, no more server link
        helperThread.close();

        synchronized (linkTable) {
            linkTableState = SHUTTINGDOWN;
        }

        synchronized (linkTable) {
            String serverName;
            ServerLink link;
            Enumeration servers = linkTable.keys();

            while (servers.hasMoreElements()) {
                serverName = (String) servers.nextElement();
                link = (ServerLink) linkTable.get(serverName);
                linkTable.remove(serverName);
                link.shutdown(); // stop link reading thread
                link.linkDown(); // close link io/socket
            }
        }

        synchronized (tmpList) {
            ServerLink link;

            for (int i = tmpList.size() - 1; i >= 0; i--) {
                link = (ServerLink) tmpList.elementAt(i);
                link.shutdown();
                link.linkDown();
            }
        }

        synchronized (connTable) {
            Enumeration conns = connTable.keys();

            while (conns.hasMoreElements()) {
                ConnKey s = (ConnKey) conns.nextElement();
                Connection conn = (Connection) connTable.get(s);
                Vector pullQ = conn.getPullQ();

                synchronized (pullQ) {
                    pullQ.notifyAll();
                }
            }
        }
    }

    // called from Servlet.destroy() when no more service threads
    public void destroy() {
        linkTableState = DESTROYED;

        synchronized (linkTable) {
            linkTable.clear();
        }

        synchronized (connTable) {
            connTable.clear();
        }

        synchronized (tmpList) {
            tmpList.removeAllElements();
        }
    }

    /**
     * This method is to close the server socket. Any exception is ignored on purpose.
     */
    public void close() {
        try {
            this.helperThread.close();
        } catch (Exception e) {
            
        }
    }

    protected void addServer(Socket s) {
        try {
            ServerLink link = new ServerLink(s, this);

            synchronized (tmpList) {
                tmpList.addElement(link);
            }
        } catch (Exception e) {
        }
    }

    /** @throws IllegalStateException */
    public void updateServerName(ServerLink link) {
        String serverName = link.getServerName();

        synchronized (linkTable) {
            if (linkTableState >= SHUTTINGDOWN) {
                throw new IllegalStateException("HttpTunnelServlet: in destory");
            }

            ServerLink oldlink = (ServerLink) linkTable.get(serverName);

            if ((oldlink != null) && !oldlink.isDone()) {
                throw new IllegalStateException("HttpTunnelServlet: ServerName " + serverName + " conflict");
            }

            linkTable.put(serverName, link);
        }

        synchronized (tmpList) {
            int i = tmpList.indexOf(link);

            if (i > -1) {
                tmpList.removeElementAt(i);
            }
        }
    }

    /** @throws IllegalStateException */
    public void updateConnection(int connId, int pullPeriod, ServerLink link) {
        Connection conn = new Connection(link);
        conn.setPullPeriod(pullPeriod);

        synchronized (connTable) {
            if (linkTableState >= SHUTTINGDOWN) {
                throw new IllegalStateException("HttpTunnelServlet: in destory");
            }

            connTable.put(new ConnKey(link.getServerName(), connId), conn);
        }
    }

    private boolean sameServerName(String serverName, Connection conn) {
        String servname = conn.getServerLink().getServerName();

        if ((servname == null) || (serverName == null) || !servname.equals(serverName)) {
            return false;
        }

        return true;
    }

    protected void serverDown(ServerLink link) {
        String serverName = link.getServerName();

        if (serverName != null) {
            ServerLink curlink = null;

            synchronized (linkTable) {
                curlink = (ServerLink) linkTable.get(serverName);

                if (curlink == link) {
                    linkTable.remove(serverName);
                } else {
                    return;
                }
            }
        }

        link.shutdown();

        synchronized (connTable) {
            Enumeration conns = connTable.keys();

            while (conns.hasMoreElements()) {
                ConnKey s = (ConnKey) conns.nextElement();
                Connection conn = (Connection) connTable.get(s);

                if (conn.getServerLink() == link) {
                    int connId = s.getConnId();

                    Vector pullQ = conn.getPullQ();
                    abortClientConnection(connId, pullQ);
                }
            }
        }
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

    private void abortClientConnection(int connId, Vector pullQ) {
        HttpTunnelPacket p = genAbortPacket(connId);

        synchronized (pullQ) {
            pullQ.addElement(p);
            pullQ.notifyAll();
        }
    }

    private void abortServerConnection(int connId, Connection conn) {
        HttpTunnelPacket p = genAbortPacket(connId);

        conn.getServerLink().sendPacket(p);
    }

    public String getDefaultServer() {
        String serverName = null;

        try {
            ServerLink link = (ServerLink) linkTable.elements().nextElement();

            if (link != null) {
                serverName = link.getServerName();
            }
        } catch (Exception e) {
        }

        return serverName;
    }

    public boolean getListenState(String serverName) {
        if (serverName == null) {
            return false;
        }

        // First find the ServerLink.
        ServerLink link = null;

        synchronized (linkTable) {
            if (linkTableState >= SHUTTINGDOWN) {
                return false;
            }

            link = (ServerLink) linkTable.get(serverName);

            if (link == null) {
                return false;
            }
        }

        return link.getListenState();
    }

    public int createNewConn(String serverName) {
        if (serverName == null) {
            return -1;
        }

        // First find the ServerLink.
        ServerLink link = null;

        synchronized (linkTable) {
            if (linkTableState >= SHUTTINGDOWN) {
                return -1;
            }

            link = (ServerLink) linkTable.get(serverName);

            if (link == null) {
                return -1;
            }
        }

        synchronized (connTable) {
            int connId = nextConnId++;
            ConnKey connKey = new ConnKey(link.getServerName(), connId);
            connTable.put(connKey, new Connection(link));

            return connId;
        }
    }

    public void destroyConn(int connId, String serverName) {
        if (serverName == null) {
            return;
        }

        synchronized (connTable) {
            connTable.remove(new ConnKey(serverName, connId));
        }
    }

    /**
     * Intercept the packets from client.
     */
    public void sendPacket(HttpTunnelPacket p, String serverName) {
        if (serverName == null) {
            return;
        }

        ConnKey connKey = new ConnKey(serverName, p.getConnId());

        Connection conn = (Connection) connTable.get(connKey);

        if (conn == null) {
            return;
        }

        if (!sameServerName(serverName, conn)) {
            return;
        }

        if (p.getPacketType() == CONN_OPTION_PACKET) {
            interceptConnOption(conn, p);
        }

        conn.getServerLink().sendPacket(p);
    }

    protected void receivePacket(HttpTunnelPacket p, ServerLink link) {
        Vector pullQ = null;

        synchronized (connTable) {
            ConnKey connKey = new ConnKey(link.getServerName(), p.getConnId());
            Connection conn = (Connection) connTable.get(connKey);

            if (conn == null) {
                return;
            }

            pullQ = conn.getPullQ();
        }

        synchronized (pullQ) {
            pullQ.addElement(p);
            pullQ.notifyAll();
        }
    }

    private void interceptConnOption(Connection conn, HttpTunnelPacket p) {
        byte[] buf = p.getPacketBody();
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bis);

        try {
            int optname = dis.readInt();

            switch (optname) {
            case CONOPT_PULL_PERIOD:

                int pullPeriod = dis.readInt();
                conn.setPullPeriod(pullPeriod);

                break;
            }
        } catch (Exception e) {
        }
    }

    public HttpTunnelPacket waitForPacket(String connIdStr, String serverName) {
        int connId = -1;

        try {
            connId = Integer.parseInt(connIdStr);
        } catch (Exception e) {
            return null;
        }

        if (serverName == null) {
            return genAbortPacket(connId);
        }

        ConnKey connKey = new ConnKey(serverName, connId);

        Connection conn = null;

        synchronized (connTable) {
            conn = (Connection) connTable.get(connKey);
        }

        if (conn == null) {
            if (linkTable.get(serverName) != null) {
                return genAbortPacket(connId);
            }

            return null;
        }

        if (!sameServerName(serverName, conn)) {
            return genAbortPacket(connId);
        }

        conn.setInUse(true);

        HttpTunnelPacket p = waitForPacket(connKey, conn);

        conn.setInUse(false);

        return p;
    }

    private HttpTunnelPacket waitForPacket(ConnKey connKey, Connection conn) {
        Vector pullQ = conn.getPullQ();
        int pullPeriod = conn.getPullPeriod();
        HttpTunnelPacket p = null;

        boolean removeConn = false;

        synchronized (pullQ) {
            if (pullPeriod > 0) {
                if (pullQ.isEmpty()) {
                    return null; // Don't tie-up web server resources...
                }
            }

            long startTime = System.currentTimeMillis();
            long maxwait = MAX_PULL_BLOCK_PERIOD;

            while (pullQ.isEmpty() && (linkTableState == RUNNING)) {
                try {
                    pullQ.wait(maxwait);
                } catch (Exception e) {
                }

                maxwait -= (System.currentTimeMillis() - startTime);

                if (maxwait <= 0) {
                    return null;
                }
            }

            if (pullQ.isEmpty()) {
                return null;
            }

            p = (HttpTunnelPacket) pullQ.elementAt(0);
            pullQ.removeElementAt(0);

            switch (p.getPacketType()) {
            case CONN_ABORT_PACKET:
                removeConn = true;
                pullQ.insertElementAt(p, 0); // Let all threads find the CONN_ABORT_PACKET
                pullQ.notifyAll();

                break;

            case CONN_SHUTDOWN:
                removeConn = true;
                pullQ.insertElementAt(p, 0); // Let all threads find the CONN_SHUTDOWN
                pullQ.notifyAll();
                p = null;

                break;

            case CONN_OPTION_PACKET:
                interceptConnOption(conn, p);

                break;
            }
        }

        // lock connTable outside pullQ lock
        if (removeConn) {
            synchronized (connTable) {
                connTable.remove(connKey);
            }
        }

        return p;
    }

    public Vector waitForPackets(String connIdStr, String serverName) {
        int connId = -1;

        try {
            connId = Integer.parseInt(connIdStr);
        } catch (Exception e) {
            return null;
        }

        if (serverName == null) {
            Vector v1 = new Vector();
            v1.addElement(genAbortPacket(connId));

            return v1;
        }

        ConnKey connKey = new ConnKey(serverName, connId);

        Connection conn = null;

        synchronized (connTable) {
            conn = (Connection) connTable.get(connKey);
        }

        if (conn == null) {
            if (linkTable.get(serverName) != null) {
                Vector v2 = new Vector();
                v2.addElement(genAbortPacket(connId));

                return v2;
            }

            return null;
        }

        if (!sameServerName(serverName, conn)) {
            Vector v3 = new Vector();
            v3.addElement(genAbortPacket(connId));

            return v3;
        }

        conn.setInUse(true);

        Vector v = waitForPackets(connKey, conn);

        conn.setInUse(false);

        return v;
    }

    private Vector waitForPackets(ConnKey connKey, Connection conn) {
        Vector pullQ = conn.getPullQ();
        int pullPeriod = conn.getPullPeriod();

        Vector v = new Vector();
        boolean removeConn = false;

        synchronized (pullQ) {
            if (pullPeriod > 0) {
                if (pullQ.isEmpty()) {
                    return null; // Don't tie-up web server resources...
                }
            }

            long startTime = System.currentTimeMillis();
            long maxwait = MAX_PULL_BLOCK_PERIOD;

            while (pullQ.isEmpty() && (linkTableState == RUNNING)) {
                try {
                    pullQ.wait(maxwait);
                } catch (Exception e) {
                }

                maxwait -= (System.currentTimeMillis() - startTime);

                if (maxwait <= 0) {
                    return null;
                }
            }

            if (pullQ.isEmpty()) {
                return v;
            }

            int size = 0;

            while (true && (linkTableState == RUNNING)) {
                HttpTunnelPacket p = (HttpTunnelPacket) pullQ.elementAt(0);

                switch (p.getPacketType()) {
                case CONN_ABORT_PACKET:
                    removeConn = true;

                    // Let all threads find the CONN_ABORT_PACKET
                    pullQ.notifyAll();
                    v.addElement(p);

                    break;

                case CONN_SHUTDOWN:
                    removeConn = true;

                    // Let all threads find the CONN_SHUTDOWN
                    pullQ.notifyAll();

                    break;

                case CONN_OPTION_PACKET:
                    interceptConnOption(conn, p);

                    break;
                }

                if (removeConn) {
                    break;
                }

                if ((size > 0) && ((size + p.getPacketSize()) > MAX_PACKETSIZE)) {
                    break;
                }

                v.addElement(p);
                size += p.getPacketSize();

                pullQ.removeElementAt(0);

                if (p.getPacketType() == CONN_INIT_ACK) {
                    break;
                }

                if (pullQ.isEmpty()) {
                    break;
                }
            }
        }

        if (removeConn) {
            synchronized (connTable) {
                connTable.remove(connKey);
            }
        }

        return v;
    }

    public void retrySendPacket(HttpTunnelPacket p, String connIdStr, String serverName) {
        if (serverName == null) {
            return;
        }

        ConnKey connKey;

        try {
            connKey = new ConnKey(serverName, connIdStr);
        } catch (Exception e) {
            return;
        }

        Connection conn = null;

        synchronized (connTable) {
            conn = (Connection) connTable.get(connKey);
        }

        if (conn == null) {
            return;
        }

        if (!sameServerName(serverName, conn)) {
            return;
        }

        Vector pullQ = conn.getPullQ();

        synchronized (pullQ) {
            pullQ.insertElementAt(p, 0);
            pullQ.notifyAll();
        }
    }

    public void retrySendPackets(Vector v, String connIdStr, String serverName) {
        if (serverName == null) {
            return;
        }

        ConnKey connKey;

        try {
            connKey = new ConnKey(serverName, connIdStr);
        } catch (Exception e) {
            return;
        }

        Connection conn = null;

        synchronized (connTable) {
            conn = (Connection) connTable.get(connKey);
        }

        if (conn == null) {
            return;
        }

        if (!sameServerName(serverName, conn)) {
            return;
        }

        Vector pullQ = conn.getPullQ();

        synchronized (pullQ) {
            for (int i = 0; i < v.size(); i++) {
                pullQ.insertElementAt(v.elementAt(i), i);
            }

            pullQ.notifyAll();
        }
    }

    protected void checkConnectionTimeouts() {
        long now = System.currentTimeMillis();

        if ((lastCheck != 0) && ((now - lastCheck) < 5000)) {
            return; // Don't check timeouts too often.
        }

        lastCheck = now;

        Vector removeList = new Vector();

        synchronized (connTable) {
            Enumeration conns = connTable.keys();

            while (conns.hasMoreElements()) {
                ConnKey s = (ConnKey) conns.nextElement();
                Connection conn = (Connection) connTable.get(s);

                if (conn.checkConnectionTimeout(now)) {
                    removeList.addElement(s);
                }
            }
        }

        for (int i = 0; i < removeList.size(); i++) {
            ConnKey s = (ConnKey) removeList.elementAt(i);
            Connection conn = null;

            synchronized (connTable) {
                conn = (Connection) connTable.get(s);
            }

            int connId = s.getConnId();
            abortServerConnection(connId, conn);

            synchronized (connTable) {
                connTable.remove(removeList.elementAt(i));
            }
        }
    }

    public Vector getServerList() {
        Vector list = new Vector();

        synchronized (linkTable) {
            for (Enumeration e = linkTable.keys(); e.hasMoreElements();) {
                list.addElement(e.nextElement());
            }
        }

        return list;
    }

    public int getServletPort() {
        return servletPort;
    }
}

