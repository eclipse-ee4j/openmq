/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.*;

/**
 * Listens for TCP connections from servers and periodically checks for connection timeouts.
 */
class HelperThread extends Thread {
    private ServerSocket ss = null;
    private ServerLinkTable parent;
    private String servletName = null;
    private boolean closed = false;

    // start regular ServerSocket
    HelperThread(int serverPort, String servletHost, int rxBufSize, ServerLinkTable p) throws IOException {
        this.parent = p;
        closed = false;

        // if (servletHost == null) {
        // ss = new ServerSocket(serverPort);
        // } else {
        // InetAddress listenAddr = InetAddress.getByName(servletHost);
        // ss = new ServerSocket(serverPort, 50, listenAddr);

        // Why backlog = 50? According the JDK 1.4 javadoc,
        // that's the default value for ServerSocket().
        // }

        // create a regular server socket. (SSLServerSocketFactory is null).
        ss = createServerSocket(null, serverPort, servletHost);

        try {
            ss.setSoTimeout(5000);
        } catch (SocketException e) {
            parent.servletContext.log("WARNING: HttpTunnelTcpListener[" + ss + "]setSoTimeout(" + 5000 + "): " + e);
        }

        if (rxBufSize > 0) {
            try {
                ss.setReceiveBufferSize(rxBufSize);
            } catch (SocketException e) {
                parent.servletContext.log("WARNING: HttpTunnelTcpListener[" + ss + "]setReceiveBufferSize(" + rxBufSize + "): " + e);
            }
        }

        setName("HttpTunnelTcpListener");
        setDaemon(true);
        servletName = "HttpTunnelServlet";

        parent.servletContext.log(servletName + ": listening on port " + serverPort + " ...");
    }

    // start SSLServerSocket
    HelperThread(int serverPort, String servletHost, int rxBufSize, String ksloc, String password, ServerLinkTable p) throws IOException {
        this.parent = p;
        closed = false;

        SSLServerSocketFactory ssf = getServerSocketFactory(ksloc, password);

        // if (servletHost == null) {
        // ss = (ServerSocket) ssf.createServerSocket(serverPort);
        // } else {
        // InetAddress listenAddr = InetAddress.getByName(servletHost);
        // ss = (ServerSocket) ssf.createServerSocket(serverPort, 50,
        // listenAddr);

        // Why backlog = 50? According the JDK 1.4 javadoc,
        // that's the default value for ServerSocket().
        // }

        ss = createServerSocket(ssf, serverPort, servletHost);

        try {
            ss.setSoTimeout(5000);
        } catch (SocketException e) {
            parent.servletContext.log("WARNING: HttpsTunnelTcpListener[" + ss + "]setSoTimeout(" + 5000 + "): " + e);
        }

        if (rxBufSize > 0) {
            try {
                ss.setReceiveBufferSize(rxBufSize);
            } catch (SocketException e) {
                parent.servletContext.log("WARNING: HttpsTunnelTcpListener[" + ss + "]setReceiveBufferSize(" + rxBufSize + "): " + e);
            }

        }

        setName("HttpsTunnelTcpListener");
        setDaemon(true);
        servletName = "HttpsTunnelServlet";

        parent.servletContext.log(servletName + ": listening on port " + serverPort + " ...");
    }

    private ServerSocket createServerSocket(SSLServerSocketFactory ssf, int serverPort, String servletHost) throws IOException {

        ServerSocket serverSocket = null;
        int retryCount = 0;

        while (serverSocket == null) {

            retryCount++;

            try {

                if (ssf != null) {
                    serverSocket = doCreateSSLServerSocket(ssf, serverPort, servletHost);
                } else {
                    serverSocket = doCreateServerSocket(serverPort, servletHost);
                }

            } catch (java.net.BindException ioe) {

                // we only retry if it is a BindException.
                if (retryCount > 7) {
                    throw ioe;
                } else {
                    parent.servletContext.log(ioe.toString(), ioe);
                }

                pause(3000);
            }
        }

        return serverSocket;
    }

    private ServerSocket doCreateServerSocket(int serverPort, String servletHost) throws IOException {
        ServerSocket serverSocket = null;

        if (servletHost == null) {
            serverSocket = new ServerSocket(serverPort);
        } else {
            InetAddress listenAddr = InetAddress.getByName(servletHost);
            serverSocket = new ServerSocket(serverPort, 50, listenAddr);

            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
        }

        return serverSocket;
    }

    private ServerSocket doCreateSSLServerSocket(SSLServerSocketFactory ssf, int serverPort, String servletHost) throws IOException {

        ServerSocket serverSocket = null;

        if (servletHost == null) {
            serverSocket = ssf.createServerSocket(serverPort);
        } else {
            InetAddress listenAddr = InetAddress.getByName(servletHost);
            serverSocket = ssf.createServerSocket(serverPort, 50, listenAddr);

            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
        }

        return serverSocket;
    }

    /**
     * pause for the specified milli seconds.
     *
     * @param ptime
     */
    private void pause(long ptime) {
        try {
            Thread.sleep(ptime);
        } catch (Exception e) {
            
        }
    }

    @Override
    public void run() {
        while (!closed) {
            try {
                Socket s = ss.accept();

                synchronized (this) {
                    if (closed) {
                        s.close();

                        break;
                    }

                    parent.addServer(s);
                }

                parent.servletContext.log(servletName + ": accepted socket connection. rcvbuf = " + s.getReceiveBufferSize());
            } catch (InterruptedIOException e1) {
                parent.checkConnectionTimeouts();
            } catch (Exception e2) {
                parent.servletContext.log(servletName + ": accept(): " + e2.getMessage());
            }
        }

        parent.servletContext.log(servletName + ": listen socket closed");
    }

    public synchronized void close() {
        closed = true;

        try {
            if (ss != null) {
                ss.close();
            }
        } catch (Exception e) {
        }
    }

    private SSLServerSocketFactory getServerSocketFactory(String ksloc, String password) throws IOException {
        SSLServerSocketFactory ssf = null;

        try {
            // set up key manager to do server authentication
            // Don't i18n Strings here. They are key words
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;

            // Get Keystore Location and Passphrase here .....
            // Check if the keystore exists. If not throw exception.
            // This is done first as if the keystore does not exist, then
            // there is no point in going further.
            File kf = new File(ksloc);

            if (!kf.exists()) {
                throw new IOException("Keystore does not exist - " + ksloc);
            }

            char[] passphrase = password.toCharArray();

            // Magic key to select the TLS protocol needed by JSSE
            // do not i18n these key strings.
            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509"); // Cert type
            ks = KeyStore.getInstance("JKS"); // Keystore type

            ks.load(new FileInputStream(ksloc), passphrase);
            kmf.init(ks, passphrase);

            TrustManager[] tm = new TrustManager[1];
            tm[0] = new DefaultTrustManager();

            // SHA1 random number generator
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

            ctx.init(kmf.getKeyManagers(), tm, random);

            ssf = ctx.getServerSocketFactory();

            return ssf;
        } catch (IOException e) {
            throw e;
        } catch (Exception ex) {
            throw new IOException(ex.getMessage());
        }
    }
}

