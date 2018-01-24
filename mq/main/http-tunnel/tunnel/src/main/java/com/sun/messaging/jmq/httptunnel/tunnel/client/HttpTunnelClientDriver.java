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
 * @(#)HttpTunnelClientDriver.java	1.14 06/28/07
 */ 
 
package com.sun.messaging.jmq.httptunnel.tunnel.client;

import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelConnection;
import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelDriver;
import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelPacket;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class provides unreliable packet delivery mechanism on
 * the client side. It also uses a dedicated thread to continuously
 * send HTTP pull requests to fetch packets sent by the server.
 */
public class HttpTunnelClientDriver extends Thread implements HttpTunnelDefaults,
    HttpTunnelDriver {
    private static boolean DEBUG = Boolean.getBoolean("httptunnel.debug");
    private boolean stopThread = false;
    private String urlString = null;
    private String urlParam = null;
    private URL pushUrl = null;
    private URL pullUrl = null;
    private URLConnection uc = null;
    private HttpTunnelPush pushWorker = null;
    private int connId;
    private HttpTunnelConnection conn = null;
    private long lastConnectTime = 0;
    private Logger logger = Logger.getLogger("Http Tunneling");

    public HttpTunnelClientDriver(String urlString) {
        int index = urlString.lastIndexOf('?');

        if (index >= 0) {
            this.urlString = urlString.substring(0, index);
            urlParam = "&" + urlString.substring(index + 1);
        } else {
            this.urlString = urlString;
            urlParam = "";
        }

        setName("HttpTunnelClientDriver");
        setDaemon(true);
        pushWorker = new HttpTunnelPush();
    }

    private void handleConnInitAck(HttpTunnelPacket p) {
        if (conn != null) {
            return;
        }

        conn = new HttpTunnelConnection(connId, this);
    }

    private void handleConnClose(HttpTunnelPacket p) {
        if (conn == null) {
            return;
        }

        conn.handleClose(p);
    }

    private void handleConnAbort(HttpTunnelPacket p) {
        if (conn == null) {
            return;
        }

        conn.handleAbort(p);
    }

    private void handleConnOption(HttpTunnelPacket p) {
        if (conn == null) {
            return;
        }

        conn.handleConnOption(p);
    }

    /**
     * Handles data packets from the network.
     */
    private void handlePacket(HttpTunnelPacket p, boolean moreData) {
        if (conn == null) {
            // TBD : Error - Tell the client ???
            return;
        }

        conn.receivePacket(p, moreData);
    }

    private void handleDummyPacket(HttpTunnelPacket p) {
        if (DEBUG) {
            log("#### Received dummy packet :");
            log(p + "\n");
        }
    }

    /**
     * Implements the connection establishment protocol.
     */
    public HttpTunnelConnection doConnect() throws IOException {
        URL connUrl = new URL(urlString + "?Type=connect" + urlParam);

        HttpTunnelPacket p = new HttpTunnelPacket();
        p.setPacketType(CONN_INIT_PACKET);
        p.setPacketBody(null);
        p.setConnId(0);
        p.setSequence(0);
        p.setWinsize(0);
        p.setChecksum(0);

        try {
            HttpTunnelPacket resp = pushWorker.sendPacketDirect(connUrl, p, true);
            connId = resp.getConnId();

            String serverName = new String(resp.getPacketBody(), "UTF8");
            String newurlParam = "&" + serverName;

            if (!urlParam.equals("") && !newurlParam.equals(urlParam)) {
                throw new IOException("Unexpected new ServerName: " +
                    serverName);
            }

            if (urlParam.equals("")) {
                urlParam = newurlParam;
            }

            pushUrl = new URL(urlString + "?Type=push" + urlParam);
            pullUrl = new URL(urlString + "?Type=pull&ConnId=" + connId +
                    urlParam);

            while (conn == null) {
                Vector v = pullPackets();

                if ((v == null) || (v.size() == 0)) {
                    continue;
                }

                HttpTunnelPacket ack = (HttpTunnelPacket) v.elementAt(0);

                if (ack != null) {
                    switch (ack.getPacketType()) {
                    case CONN_SHUTDOWN:
                        throw new IOException("Connection refused");

                    case CONN_INIT_ACK:
                        handleConnInitAck(ack);

                        break;

                    case CONN_CLOSE_PACKET:
                        handleConnClose(p);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            String message = "Connection refused : ";

            if (e instanceof EOFException) {
                message = "Connection refused : " +
                    "Make sure that the broker is running and " +
                    "its HTTP service is active...";
            }

            // Concatenate the underlying exception's message.
            if (e.getMessage() != null) {
                message = message + e.getMessage();
            }

            ConnectException ce = new ConnectException(message);

            try {
                ce.initCause(e);
            } catch (Throwable t) {
                // e.g. NoSuchMethodError when running with ancient
                // JDKs that do not have Throwable.initCause(). In
                // this case we will not be able to provide the full
                // stack trace of the underlying cause. The users will
                // have to rely on the concatenated message string.
                // Too bad. They should upgrade...
            }

            throw ce;
        }

        pushWorker.startPushThread(pushUrl);
        start();

        return conn;
    }

    /**
     * Sends a packet.
     */
    public void sendPacket(HttpTunnelPacket p) {
        pushWorker.sendPacket(p);

        if (DEBUG) {
            log("Sending packet" + p);
        }
    }

    /**
     * Shutdown the driver. Stops accepting packets from the peer.
     */
    public void shutdown(int connId) {
        pushWorker.shutdown();
        stopThread = true;

        if ((uc != null) && uc instanceof HttpURLConnection) {
            try {
                ((HttpURLConnection) uc).disconnect();
            } catch (Throwable t) {
            }
        }

        conn = null;
    }

    /**
     * Send a HTTP pull request to fetch any packets sent by the
     * server. The server-side driver can send multiple packets
     * with each HTTP pull response.
     * @return A <code>Vector</code> containing received packets.
     */
    private Vector pullPackets() throws Exception {
        Vector v = null;
        int responseCode = HttpURLConnection.HTTP_OK;

        try {
            uc = pullUrl.openConnection();
            uc.setDoInput(true);
            uc.setDoOutput(false);
            uc.setUseCaches(false);
            uc.connect();

            if (uc instanceof HttpURLConnection) {
                responseCode = ((HttpURLConnection) uc).getResponseCode();
            } else {
                uc.getContentType();

                // We don't really need the content type. This just forces
                // the uc to do its job.
            }
        } catch (IOException e) {
            handleHTTPConnectError();
            throw e;
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            handleHTTPConnectError();
        } else {
            lastConnectTime = System.currentTimeMillis();
            v = new Vector();

            InputStream is = uc.getInputStream();

            while (true) {
                try {
                    HttpTunnelPacket p = new HttpTunnelPacket();
                    p.readPacket(is);
                    v.addElement(p);
                } catch (Exception e) {
                    if (v.size() > 1) {
                        break;
                    }

                    if (v.size() == 1) {
                        HttpTunnelPacket p = (HttpTunnelPacket) v.elementAt(0);

                        if (p.getPacketType() != NO_OP_PACKET) {
                            break;
                        }
                    }

                    // The 'pull' request came back empty handed..
                    int pullPeriod = conn.getPullPeriod();

                    if (pullPeriod > 0) {
                        try {
                            Thread.sleep(pullPeriod * 1000L);
                        } catch (Exception se) {
                        }

                        break;
                    } else {
                        throw e;
                    }
                }
            }

            is.close();
        }

        uc = null;

        return v;
    }

    private void handleHTTPConnectError() {
        if (conn.getConnectionTimeout() <= 0) {
            return;
        }

        if (lastConnectTime == 0) {
            return;
        }

        if ((System.currentTimeMillis() - lastConnectTime) > (conn.getConnectionTimeout() * 1000L)) {
            // Abort the connection by generating a CONN_ABORT_PACKET.
            HttpTunnelPacket p = new HttpTunnelPacket();
            p.setPacketType(CONN_ABORT_PACKET);
            p.setConnId(connId);
            p.setSequence(0);
            p.setWinsize(0);
            p.setChecksum(0);
            p.setPacketBody(null);

            handleConnAbort(p);
        }
    }

    private void handleHttpPullError() {
    }

    public void run() {
        while (!stopThread) {
            try {
                Vector v = pullPackets();

                if ((v == null) || (v.size() == 0)) {
                    handleHttpPullError();

                    continue;
                }

                int i;
                int j;

                for (j = v.size() - 1; j >= 0; j--) {
                    HttpTunnelPacket p = (HttpTunnelPacket) v.elementAt(j);

                    if (p.getPacketType() == DATA_PACKET) {
                        break;
                    }
                }

                // Now j points to the last data packet in v
                for (i = 0; i < v.size(); i++) {
                    HttpTunnelPacket p = (HttpTunnelPacket) v.elementAt(i);

                    if (p.getPacketType() == CONN_SHUTDOWN) {
                        // TBD: Connection aborted...
                    }

                    if (DEBUG) {
                        log("Received packet:" + p);
                    }

                    switch (p.getPacketType()) {
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
                        handlePacket(p, (i != j));

                        // i == j is true for the last data packet in v
                        break;

                    case DUMMY_PACKET:
                        handleDummyPacket(p);

                        break;

                    default:
                        break;
                    }

                    // receivePacket(p);
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (Exception se) {
                }

                handleHttpPullError();
            }
        }
    }

    public Hashtable getDebugState() {
        return new Hashtable();
    }

    private void log(String msg) {
        logger.log(Level.INFO, msg);
    }
}

/*
 * EOF
 */
