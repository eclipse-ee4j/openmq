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
 * @(#)HTTPConnectionHandler.java	1.15 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.http;

import java.io.*;
import java.net.*;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;
import com.sun.messaging.jmq.jmsclient.protocol.SocketConnectionHandler;

import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;

/**
 * This class implements the HTTP protocol connection handler
 * for iMQ clients.
 */
public class HTTPConnectionHandler extends SocketConnectionHandler {

    private static final String socketClass =
        "com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelSocketImpl"; 

    private HttpTunnelSocket socket = null;

    private String URLString = null;
    /**
     * Create a connection with broker.
     */
    public HTTPConnectionHandler (Object conn) throws JMSException {
        ConnectionImpl connection = (ConnectionImpl) conn;
        URLString = connection.getProperty(ConnectionConfiguration.imqConnectionURL);

        if (URLString == null) {
            throw new JMSException(ConnectionConfiguration.imqConnectionURL + " property not found.");
        }

        try {
            socket = (HttpTunnelSocket)Class.forName(socketClass).newInstance();
            socket.init(URLString);
        } catch ( Exception e ) {
            connection.getExceptionHandler().handleConnectException (
                e, URLString);
        } finally {
            connection.setLastContactedBrokerAddress(URLString);
        }
    }

    public HTTPConnectionHandler (MQAddress addr, ConnectionImpl conn)
        throws JMSException {
        ConnectionImpl connection = (ConnectionImpl) conn;
        URLString = addr.getURL();

        if (URLString == null) {
            throw new JMSException("URL not found.");
        }

        try {
            socket = (HttpTunnelSocket)Class.forName(socketClass).newInstance();
            socket.init(URLString);
        } catch ( Exception e ) {
            connection.getExceptionHandler().handleConnectException (
            e, URLString );
        } finally {
            conn.setLastContactedBrokerAddress(URLString);
        }
    }

    /**
     * Get socket input stream.
     */
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    /**
     * Get socket output stream.
     */
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    /**
     * Get socket local port for the current connection.
     */
    public int
    getLocalPort() throws IOException {
        return socket.getConnId();
    }
    
	protected void closeSocket() throws IOException {
        socket.close();
	}

    public String getBrokerHostName() {
        return this.URLString;
    }

    public String getBrokerAddress() {
        return this.URLString;
    }
}

/*
 * EOF
 */
