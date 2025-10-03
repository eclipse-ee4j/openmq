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

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.util.*;
import java.io.*;
import java.net.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.net.MQServerSocketFactory;
import com.sun.messaging.jmq.util.log.Logger;

import javax.net.ServerSocketFactory;

class ClusterServiceListener extends Thread {
    private static final BrokerResources br = Globals.getBrokerResources();
    private static final Logger logger = Globals.getLogger();
    ClusterImpl callback = null;
    boolean done = false;
    ServerSocket ss = null;
    private boolean nodelay;
    private boolean isSSL = false;

    private static ServerSocketFactory ssf = MQServerSocketFactory.getDefault();

    ClusterServiceListener(ClusterImpl callback) throws IOException {
        this.callback = callback;
        setName("ClusterServiceListener");
        setDaemon(true);
        this.nodelay = callback.getTCPNodelay();

        if (callback.getTransport().equalsIgnoreCase("ssl")) {
            nodelay = callback.getSSLNodelay();
            isSSL = true;
            initSSLListener();
        } else {
            initTCPListener();
        }

        start();
    }

    private void initSSLListener() throws IOException {
        if (ClusterImpl.DEBUG) {
            logger.log(logger.INFO, "ClusterImpl.initSSLListener[nodelay=" + nodelay + ", inbufsz=" + callback.getSSLInputBufferSize() + ", outbufsz="
                    + callback.getSSLOutputBufferSize() + "]");
        }

        ServerSocketFactory sslfactory = null;
        try {
            Class TLSProtocolClass = Class.forName("com.sun.messaging.jmq.jmsserver.net.tls.TLSProtocol");

            if (ClusterImpl.DEBUG) {
                logger.log(logger.DEBUG, "ClusterImpl.initSSLListener. " + "Initializing SSLServerSocketFactory");
            }

            /*
             * SSLServerSocketFactory ssf = (SSLServerSocketFactory) TLSProtocol.getServerSocketFactory();
             */
            java.lang.reflect.Method m = TLSProtocolClass.getMethod("getServerSocketFactory", (Class[]) null);
            sslfactory = (ServerSocketFactory) m.invoke(null, (Object[]) null);
        } catch (Exception e) {
            Throwable t = e;
            if (e instanceof java.lang.reflect.InvocationTargetException) {
                t = e.getCause();
                if (t == null) {
                    t = e;
                }
                if (ClusterImpl.DEBUG && t != e) {
                    logger.logStack(Logger.ERROR, e.getMessage(), e);
                }
            }
            logger.logStack(Logger.ERROR, t.getMessage(), t);
            throw new IOException(t.getMessage());
        }

        InetAddress listenHost = callback.getListenHost();
        int listenPort = callback.getListenPort();
        HashMap h = null;

        if (ClusterImpl.DEBUG) {
            logger.log(logger.DEBUG, "ClusterImpl.initSSLListener. " + "Initializing ServerSocket");
        }

        if (listenHost == null) {
            ss = sslfactory.createServerSocket(listenPort);
        } else {
            ss = sslfactory.createServerSocket(listenPort, 50, listenHost);
            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
            // Also even if a connection gets refused, that broker
            // will try again after sometime anyway...

            h = new HashMap();
            h.put("hostname", listenHost.getHostName());
            h.put("hostaddr", listenHost.getHostAddress());
        }

        if (Globals.getPoodleFixEnabled()) {
            Globals.applyPoodleFix(ss, "ClusterListener");
        }
        Globals.getPortMapper().addService(ClusterImpl.SERVICE_NAME, "ssl", ClusterImpl.SERVICE_TYPE, ss.getLocalPort(), h);

        if (ClusterImpl.DEBUG) {
            logger.log(logger.INFO, "ClusterImpl.initSSLListener: " + ss + " " + MQServerSocketFactory.serverSocketToString(ss));
        }
    }

    private void initTCPListener() throws IOException {
        if (ClusterImpl.DEBUG) {
            logger.log(logger.INFO, "ClusterImpl.initTCPListener[TcpNoDelay=" + nodelay + ", inbufsz=" + callback.getTCPInputBufferSize() + ", outbufsz="
                    + callback.getTCPOutputBufferSize() + "]");
        }

        InetAddress listenHost = callback.getListenHost();
        int listenPort = callback.getListenPort();
        HashMap h = null;

        if (listenHost == null) {
            ss = ssf.createServerSocket(listenPort);
        } else {
            ss = ssf.createServerSocket(listenPort, 50, listenHost);
            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
            // Also even if a connection gets refused, that broker
            // will try again after sometime anyway...

            h = new HashMap();
            h.put("hostname", listenHost.getHostName());
            h.put("hostaddr", listenHost.getHostAddress());
        }

        Globals.getPortMapper().addService(ClusterImpl.SERVICE_NAME, "tcp", ClusterImpl.SERVICE_TYPE, ss.getLocalPort(), h);

        if (ClusterImpl.DEBUG) {
            logger.log(logger.DEBUG, "ClusterImpl.initTCPListener: " + ss + " " + MQServerSocketFactory.serverSocketToString(ss));
        }
    }

    public String getServerSocketString() {
        ServerSocket ssocket = ss;
        if (ssocket != null) {
            return ssocket.getInetAddress() + ":" + ssocket.getLocalPort();
        }
        return null;
    }

    public synchronized void shutdown() {
        done = true;
        try {
            ss.close();
        } catch (Exception e) { /* Ignore */
            /* Ignore. This happens when ServerSocket is closed.. */
        }
    }

    private synchronized boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        String oomstr = br.getKString(br.M_LOW_MEMORY_CLUSTER);
        while (true) {
            if (isDone()) {
                break;
            }
            Socket sock = null;
            try {
                sock = ss.accept();
                try {
                    sock.setTcpNoDelay(nodelay);
                } catch (SocketException e) {
                    logger.log(Logger.WARNING, getClass().getSimpleName() + ".run(): [" + sock.toString() + "]setTcpNoDelay(" + nodelay + "): " + e.toString(),
                            e);
                }
                callback.acceptConnection(sock, isSSL);
            } catch (Exception e) {
                /* Ignore. This happens when ServerSocket is closed.. */
            } catch (OutOfMemoryError e) {
                if (isDone()) {
                    return;
                }
                try {
                    if (sock != null) {
                        sock.close();
                    }
                } catch (Throwable t) {
                } finally {
                    logger.log(Logger.WARNING, oomstr);
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ee) {
                }
            }
        }
    }
}

