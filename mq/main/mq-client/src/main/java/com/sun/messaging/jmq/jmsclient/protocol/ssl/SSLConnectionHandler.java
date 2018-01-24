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
 * @(#)SSLConnectionHandler.java	1.32 06/29/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.ssl;

import java.net.*;
import java.io.*;

import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;
import com.sun.messaging.jmq.jmsclient.protocol.SocketConnectionHandler;

import java.security.*;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;

 /**
  * This class is the SSL protocol handler for the iMQ JMS client
  * implementation.  It uses SSL protocol to communicate with the Broker.
  */
public class SSLConnectionHandler extends SocketConnectionHandler {

    private static boolean isRegistered = false;
    //private static boolean debug = Debug.debug;

    private SSLSocket sslSocket = null;

    private String host = null;

    private int baseport = 0;
    private int directport = 0;
    private int port = 0;

    /**
     * Constructor.  Called by SSLStreamHandler.
     * This creates a SSL socket connection to the broker.
     * bug 4959114.
     */
    SSLConnectionHandler (Object conn) throws JMSException {

        ConnectionImpl connection = (ConnectionImpl) conn;
        //int port = 0;
        //String host = null;
        directport = 0;
        try {
            doRegister(connection);

            // First, gather the configuration attributes.
            host = connection.getProperty(
                ConnectionConfiguration.imqBrokerHostName);
            baseport = Integer.parseInt(connection.getProperty(
                ConnectionConfiguration.imqBrokerHostPort));
            directport = Integer.parseInt(connection.getProperty(
                ConnectionConfiguration.imqBrokerServicePort));
            String namedservice = connection.getProperty(
                ConnectionConfiguration.imqBrokerServiceName);
            boolean isHostTrusted = Boolean.valueOf(connection.getProperty(
                ConnectionConfiguration.imqSSLIsHostTrusted)).booleanValue();

            // Resolve the service port if necessary.
            if (directport == 0) {
                PortMapperClient pmc = new PortMapperClient(connection);
                if (namedservice != null && !("".equals(namedservice))) {
                    port = pmc.getPortForService("tls", namedservice);
                } else {
                    port = pmc.getPortForProtocol("tls");
                }

            } else {
                port = directport;
            }

            ConnectionImpl.checkHostPort (host, port);

            // Create the connection
            this.sslSocket = SSLUtil.makeSSLSocket(host, port, isHostTrusted,
                                 connection.getProperty(
                                     ConnectionConfiguration.imqKeyStore, null),
                                 connection.getProperty(
                                     ConnectionConfiguration.imqKeyStorePassword, null),
                                 connection.getConnectionLogger(), AdministeredObject.cr);
        } catch (JMSException jmse) {
            throw jmse;
        } catch (Exception e) {
            connection.getExceptionHandler().handleConnectException (
                e, host, port);
        } finally {
            connection.setLastContactedBrokerAddress(getBrokerAddress());
        }
    }

    /**
     * Constructor.  Called by SSLStreamHandler.
     * This creates a SSL socket connection to the broker.
     * bug 4959114.
     */
    SSLConnectionHandler (MQAddress addr, ConnectionImpl conn)
        throws JMSException {

        ConnectionImpl connection = (ConnectionImpl) conn;
        //int port = 0;
        //String host = null;

        try {
            doRegister(connection);

            // First, gather the configuration attributes.
            host = addr.getHostName();
            directport = 0;
            if (addr.isServicePortFinal())
                directport = addr.getPort();
            String namedservice = addr.getServiceName();
            /**
             * If 'isHostTrusted' is set in address list, it is used.
             * Otherwise, 'imqSSLIsHostTrusted' prop is used.
             */
            boolean isHostTrusted = true;
            if (addr.getIsSSLHostTrustedSet()) {
                isHostTrusted = Boolean.valueOf(
                    addr.getProperty(MQAddress.isHostTrusted)).booleanValue();
            } else {
                isHostTrusted = Boolean.valueOf(connection.getProperty(
                ConnectionConfiguration.imqSSLIsHostTrusted)).booleanValue();

            }

            // Resolve the service port if necessary.
            if (directport == 0) {
                PortMapperClient pmc = new PortMapperClient(addr, connection);
                baseport = pmc.getHostPort();
                if (namedservice != null && !("".equals(namedservice))) {
                    port = pmc.getPortForService("tls", namedservice);
                } else {
                    port = pmc.getPortForProtocol("tls");
                }

            } else {
                port = directport;

            }

            ConnectionImpl.checkHostPort (host, port);

            // Create the connection
            this.sslSocket = SSLUtil.makeSSLSocket(host, port, isHostTrusted,
                                 connection.getProperty(
                                     ConnectionConfiguration.imqKeyStore, null),
                                 connection.getProperty(
                                     ConnectionConfiguration.imqKeyStorePassword, null),
                                 connection.getConnectionLogger(), AdministeredObject.cr);
        } catch (JMSException jmse) {
            throw jmse;
        } catch (Exception e) {
            connection.getExceptionHandler().handleConnectException (
                e, host, port);
        } finally {
            connection.setLastContactedBrokerAddress(getBrokerAddress());
        }
    }

    private void doRegister(ConnectionImpl connection) throws Exception {

        // Not needed for JDK 1.4 or later; for backward compatibility execute
        // the registration code if imq.registerSSLProvider prop is set to true
        if ( Boolean.getBoolean("imq.registerSSLProvider") &&
             isRegistered == false ) {
            synchronized ( this.getClass() ) {
                String name =
                connection.getProperty(ConnectionConfiguration.imqSSLProviderClassname);
                Provider provider = (Provider) Class.forName(name).newInstance();
                Security.addProvider( provider );
                isRegistered = true;
            }
        }
    }

    /*
     * Get SSL socket input stream.
     */
    public InputStream
    getInputStream() throws IOException {
        return sslSocket.getInputStream();
    }

     /*
     * Get SSL socket output stream.
     */
    public OutputStream
    getOutputStream() throws IOException {
        return sslSocket.getOutputStream();
    }

     /*
     * Get SSL socket local port for the current connection.
     */
    public int
    getLocalPort() throws IOException {
        return sslSocket.getLocalPort();
    }

	protected void closeSocket() throws IOException {
		sslSocket.close();
	}

    public String getBrokerHostName() {
        return this.host;
    }

    public int getBrokerPort() {
        return this.port;
    }

    public String getBrokerAddress() {
        if (directport == 0) {
            return host + ":" + baseport + "(" + port + ")";
        } else {
            return host + ":" + directport;
        }
        //return host + ":" + port;
    }

}
