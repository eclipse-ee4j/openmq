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
 * @(#)MQServerSocketFactory.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util.net;

import javax.net.ServerSocketFactory;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.IOException;

/**
 * Our versino of a ServerSocketFactory. We do this to centralize
 * creation of server sockets.
 */
public class MQServerSocketFactory extends javax.net.ServerSocketFactory {

    boolean reuseAddr = true;

    ServerSocketFactory  ssf = null;

    protected MQServerSocketFactory() {
        this.ssf = null;
    }

    protected MQServerSocketFactory(ServerSocketFactory ssf) {
        this.ssf = ssf;
    }

    public void setReuseAddress(boolean on) {
        this.reuseAddr = on;
    }

    /**
     * Create an unbound ServerSocket.
     */
    public ServerSocket createServerSocket() throws IOException {
        ServerSocket ss;
        if (this.ssf != null) {
            /* Use wrapped ServerSocketFactory to create ServerSocket */
            ss = ssf.createServerSocket();
        } else {
            /* No wrapped factory, use ServerSocket constructor */
            ss = new ServerSocket();
        }

        // Bug 6294767: Force SO_REUSEADDRR to true
        ss.setReuseAddress(reuseAddr);
        return ss;
    }

    /**
     * Create a ServerSocket, bound to the specified port (on all interfaces)
     */
    public ServerSocket createServerSocket(int port) throws IOException {
        ServerSocket ss = createServerSocket();
        ss.bind(new InetSocketAddress(port));
        return ss;
    }

    /**
     * Create a ServerSocket, bound to the specified port (on all interfaces)
     * with the specified backlog
     */
    public ServerSocket createServerSocket(int port,
                                           int backlog)
                                           throws IOException {
        ServerSocket ss = createServerSocket();
        ss.bind(new InetSocketAddress(port), backlog);
        return ss;
    }

    /**
     * Create a ServerSocket, bound to the specified port with 
     * the specified backlog, on the specified interface.
     */
    public ServerSocket createServerSocket(int port,
                                           int backlog,
                                           InetAddress ifAddress)
                                           throws IOException {

        ServerSocket ss = createServerSocket();
        ss.bind(new InetSocketAddress(ifAddress, port), backlog);
        return ss;
    }

    /**
     * Get the default factory;
     */
    public static ServerSocketFactory getDefault() {
        return new MQServerSocketFactory();
    }

    /**
     * Create a factory that wraps the specified factory.
     */
    public static ServerSocketFactory wrapFactory(ServerSocketFactory ssf) {
        return new MQServerSocketFactory(ssf);
    }


    /**
     * Return a string description of a ServerSocket
     */
    public static String serverSocketToString(ServerSocket s) {

        try {
            return "SO_RCVBUF=" + s.getReceiveBufferSize() +
                ", SO_REUSEADDR=" + s.getReuseAddress() +
                ", SO_TIMEOUT=" + s.getSoTimeout();
        } catch (IOException e) {
            return "Bad serverSocket: " + e;
        }
    }
}
