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

import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.sun.messaging.jmq.httptunnel.api.server.HttpsTunnelServerDriver;


/**
 * This class extends HttpTunnelServerDriver and uses SSL sockets
 * to communicate with the tunneling servlet.
 */
public class HttpsTunnelServerDriverImpl extends HttpTunnelServerDriverImpl 
implements HttpsTunnelServerDriver 
{

    private static boolean DEBUG = getDEBUG();
    private static Logger logger = Logger.getLogger("Http Tunneling");
    protected boolean trustServlet = true;
    private boolean poodleFixEnabled = true;

    public HttpsTunnelServerDriverImpl() {}

    public void init(String serviceName, boolean trust, boolean poodleFixEnabled)
        throws IOException {
        init(serviceName, InetAddress.getLocalHost().getHostAddress(),
            DEFAULT_HTTPS_TUNNEL_PORT, trust, poodleFixEnabled);
    }

    public void init(String serviceName,
        String webServerHostName, int webServerPort, 
        boolean trust, boolean poodleFixEnabled)
        throws IOException {
        super.init(serviceName, webServerHostName, webServerPort);

        trustServlet = trust;
        setName("HttpsTunnelServerDriver");
        this.poodleFixEnabled = poodleFixEnabled;

        if (DEBUG || DEBUGLINK) {
            log("Created HttpsTunnelServerDriver for " + serviceName + " to " +
                webServerHostName + ":" + webServerPort+
                ", poodleFixEnabled="+poodleFixEnabled);
        }
    }

    /**
     * Create secured connection to the servlet. If accepted by
     * the servlet successfully, this method sends
     * the current state of the connection table to the servlet
     * and resumes normal operation.
     */
    @Override
    protected void createLink() {
        totalRetryWaited = 0;

        if (DEBUG) {
            log("http:connecting to " + webServerHost + ":" + webServerPort);
        }

        while (true) {
            try {
                if (rxBufSize > 0) {
                    serverConn = getSSLSocket(webServerHost, webServerPort,
                            rxBufSize, trustServlet);
                } else {
                    serverConn = getSSLSocket(webServerHost, webServerPort,
                            trustServlet);
                }
                if (poodleFixEnabled) {
                    assert serverConn instanceof SSLSocket : serverConn.getClass();
                    applyPoodleFix((SSLSocket)serverConn);
                }

                try {
                serverConn.setTcpNoDelay(true);
                } catch (SocketException e) {
                log(Level.WARNING, "HTTPS socket["+webServerHost+":"+webServerPort+
                                    "]setTcpNoDelay: "+e.toString(), e);
                }

                if (DEBUG) {
                    log("######## rcvbuf = " +
                        serverConn.getReceiveBufferSize());
                }

                is = serverConn.getInputStream();
                os = serverConn.getOutputStream();

                if (DEBUG || DEBUGLINK) {
                    log("Broker HTTPS link up");
                }

                totalRetryWaited = 0;

                break;
            } catch (Exception e) {
                if (DEBUG || DEBUGLINK) {
                    log("Got exception while connecting to servlet: " +
                        e.getMessage());
                }
            }

            try {
                Thread.sleep(CONNECTION_RETRY_INTERVAL);
                totalRetryWaited += CONNECTION_RETRY_INTERVAL;

                if (totalRetryWaited >= (inactiveConnAbortInterval * 1000)) {
                    if (DEBUG || DEBUGLINK) {
                        log("Retry connect to servlet timeout " +
                            "- cleanup all (" + connTable.size() + ") " +
                            "connections and stop retry ...");
                    }

                    cleanupAllConns();
                    totalRetryWaited = 0;
                }
            } catch (Exception se) {
                if (se instanceof IllegalStateException) {
                    throw (IllegalStateException) se;
                }
            }
        }

        sendLinkInitPacket();
        sendListenStatePacket();
    }

    /**
     * Create secured connection to the specified server.
     */
    private static SSLSocket getSSLSocket(InetAddress host, int port,
        boolean trust) throws IOException {
        if (DEBUG || DEBUGLINK) {
            logger.log(Level.INFO, "Creating SSL Socket...");
        }

        try {
            SSLSocketFactory factory = null;

            if (trust) {
                factory = getTrustedSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }

            SSLSocket s = (SSLSocket) factory.createSocket(host, port);

            return s;
        } catch (Exception e) {
            if (!(e instanceof IOException)) {
                IOException ex = new IOException(e.getMessage());
                ex.setStackTrace(e.getStackTrace());
                throw ex;
            } else {
                throw (IOException) e;
            }
        }
    }

    /**
     * Create secured connection to the specified server.
     */
    private static SSLSocket getSSLSocket(InetAddress host, int port,
        int rxBufSize, boolean trust) throws IOException {
        if (DEBUG || DEBUGLINK) {
            logger.log(Level.INFO, "Creating SSL Socket with rxBufSize...");
        }

        try {
            SSLSocketFactory factory = null;

            if (trust) {
                factory = getTrustedSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }

            SSLSocket s = (SSLSocket) factory.createSocket();
            try {
            s.setReceiveBufferSize(rxBufSize);
            } catch (SocketException e) {
            logger.log(Level.WARNING, "HTTPS socket["+host+":"+port+
                   "]setReceiveBufferSize("+rxBufSize+"): "+e.toString(), e);
            }
            InetSocketAddress addr = new InetSocketAddress(host, port);
            s.connect(addr);

            return s;
        } catch (Exception e) {
            if (!(e instanceof IOException)) {
                IOException ex = new IOException(e.getMessage());
                ex.setStackTrace(e.getStackTrace());
                throw ex;
            } else {
                throw (IOException) e;
            }
        }
    }

    /**
     * Return a socket factory that uses the our DefaultTrustManager
     */
    private static SSLSocketFactory getTrustedSocketFactory()
        throws Exception {
        SSLContext ctx;
        ctx = SSLContext.getInstance("TLS");

        TrustManager[] tm = new TrustManager[1];
        tm[0] = new DefaultTrustManager();

        ctx.init(null, tm, null);

        SSLSocketFactory factory = ctx.getSocketFactory();

        return factory;
    }

    public static void applyPoodleFix(SSLSocket sslSocket) {
        String[] protocols = sslSocket.getEnabledProtocols();
        String orig = Arrays.toString(protocols);
        Set<String> set = new LinkedHashSet<String>();
        for (String s : protocols) {
             if (s.equals("SSLv3") || s.equals("SSLv2Hello")) {
                continue;
            }
            set.add(s);
        }
        logger.log(Level.INFO, "[HttpsTunnelServerDriver]: ["+orig+"], setEnabledProtocols["+set+"]");
        sslSocket.setEnabledProtocols(set.toArray(new String[set.size()]));
        return;
    }
}

/*
 * EOF
 */
