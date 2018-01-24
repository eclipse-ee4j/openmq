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
 * @(#)PortMapperClient.java	1.30 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.Map;
import javax.jms.*;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.io.PortMapperTable;
import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.ReadOnlyPacket;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.protocol.ssl.SSLUtil;

/**
 *
 * This class provide a way for the client to recover itself if the
 * connection goes away.
 *
 * <p>The API user is transparent to the recovering attempt.  All
 * consumers are registered to the broker with their original IDs.
 * All producers are added with the same parameters.
 *
 */


public class PortMapperClient {

    protected ConnectionImpl connection = null;
    protected PortMapperTable portMapperTable = null;

    protected boolean useMQAddress = false;
    protected MQAddress addr = null;

    private static final Version version = com.sun.messaging.jmq.jmsclient.ConnectionImpl.version;

    private boolean debug = Debug.debug;

    public PortMapperClient (ConnectionImpl connection) throws JMSException{
        this.connection = connection;
        init();
    }

    public PortMapperClient (MQAddress addr, ConnectionImpl connection)
        throws JMSException {
        this.addr = addr;
        this.useMQAddress = true;
        this.connection = connection;
        init();
    }

    public int getPortForProtocol(String protocol){
        String type = connection.getConnectionType();
        return getPort(protocol, type, null);
    }

    //bug 4959114.
    public int
    getPortForService(String protocol, String service) throws JMSException {
        String type = connection.getConnectionType();
        int port = getPort(protocol, type, service);

        if ( port == -1 ) {
            String errorString =
            AdministeredObject.cr.getKString(AdministeredObject.cr.X_UNKNOWN_BROKER_SERVICE, service);
            JMSException jmse =new com.sun.messaging.jms.JMSException
            (errorString, AdministeredObject.cr.X_UNKNOWN_BROKER_SERVICE);

            ExceptionHandler.throwJMSException(jmse);

        }

        return port;
    }

    //bug 4959114.
    private int getPort(String protocol, String type, String servicename) {
        //int port = 25374;
        int port = -1;
        Map table = portMapperTable.getServices();
        PortMapperEntry pme = null;

        Iterator it = table.values().iterator();

        while (it.hasNext()){
            pme = (PortMapperEntry) it.next();
            if (pme.getProtocol().equals(protocol)){
                if (pme.getType().equals(type)){
                    if (servicename == null){
                        port = pme.getPort();
                        break;
                    } else {
                        if (pme.getName().equals(servicename)){
                            port = pme.getPort();
                            break;
                        }
                    }
                }
            }
        }

        return port;
    }

    protected void init() throws JMSException {
        try {
            readBrokerPorts();
            checkPacketVersion();
        } catch (JMSException jmse) {

            String str = this.getHostName() + ":" + this.getHostPort();
            connection.setLastContactedBrokerAddress(str);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    private void checkPacketVersion() throws JMSException {
        String pktversion = portMapperTable.getPacketVersion();
        String clientMVersion = version.getImplementationVersion();

        // Raptor (3.5) clients can talk to a Falcon (3.0) broker.
        if (Version.compareVersions(pktversion, "3.0") < 0) {
            String errorString = AdministeredObject.cr.getKString(
                AdministeredObject.cr.X_VERSION_MISMATCH,
                 clientMVersion, pktversion);

            JMSException jmse =
            new com.sun.messaging.jms.JMSException
            (errorString,AdministeredObject.cr.X_VERSION_MISMATCH);

            ExceptionHandler.throwJMSException(jmse);
        }

        // Use Packet version 200 for brokers older than 3.5
        if (Version.compareVersions(pktversion, "3.0.1", false) < 0) {
            ReadOnlyPacket.setDefaultVersion(Packet.VERSION2);
        }
    }

    private String getHostName() {
        if (useMQAddress) {
            return addr.getHostName();
        }
        return connection.getProperty(
            ConnectionConfiguration.imqBrokerHostName);
    }

    private boolean getHostPortSSLEnabled() {
        if (useMQAddress) {
            return addr.isSSLPortMapperScheme();
        }
        return false;
    }

    private boolean getIsHostTrusted() {
        if (useMQAddress) {
            return addr.getIsSSLHostTrustedSet();
        }
        return false;
    }

    public int getHostPort() {
        if (useMQAddress) {
            return addr.getPort();
        }
        String prop = connection.getProperty(
            ConnectionConfiguration.imqBrokerHostPort);
        return Integer.parseInt(prop);
    }

    protected void readBrokerPorts() throws JMSException {

        String host = getHostName();
        //port mapper port
        int port = getHostPort();
        boolean ssl = getHostPortSSLEnabled();
        boolean isHostTrusted = getIsHostTrusted();

        if ( debug ) {
            Debug.println("Connecting to portmapper host: "+host+"  port: "+ port+" ssl: "+ssl);
        }

        InputStream is = null;
        OutputStream os = null;
        Socket socket = null;
        try {
            String version =
                String.valueOf(PortMapperTable.PORTMAPPER_VERSION) + "\n";

            // bug 6696742 - add ability to set connect timeout 
            int timeout = connection.getSocketConnectTimeout();
            Integer sotimeout = connection.getPortMapperSoTimeout();
            socket = makePortMapperClientSocket(host, port, timeout, 
                                         sotimeout, ssl, isHostTrusted);
            
            is = socket.getInputStream();
            os = socket.getOutputStream();

            // Write version of portmapper we support to broker
            try {
                os.write(version.getBytes());
                os.flush();
            } catch (IOException e) {
                // This can sometimes fail if the server already wrote
                // the port table and closed the connection
            }

            portMapperTable = new PortMapperTable();
            portMapperTable.read(is);

            is.close();
            socket.close();

        } catch ( Exception e ) {
            try {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception ee) {
                /* ignore */
            }
            connection.getExceptionHandler().handleConnectException (e, host, port);
        }
    }
    
    
    private Socket makePortMapperClientSocket(String host, int port, 
                                              int timeout, Integer sotimeout,
                                              boolean ssl, boolean isHostTrusted)
                                              throws Exception {
        Socket socket = null;
        if (timeout > 0) {
            ConnectionImpl.getConnectionLogger().fine(
                "Connecting to portmapper with timeout=" + timeout);

            if (ssl) {
                socket = SSLUtil.makeSSLSocket(null, 0, isHostTrusted, 
                             connection.getProperty(
                                 ConnectionConfiguration.imqKeyStore, null),
                             connection.getProperty(
                                 ConnectionConfiguration.imqKeyStorePassword, null),
                             ConnectionImpl.connectionLogger, AdministeredObject.cr);
            } else {
                socket = new Socket();
            }
            InetSocketAddress socketAddr = new InetSocketAddress (host, port);
            socket.connect(socketAddr, timeout);
            socket.setSoTimeout(0);
        } else {
            ConnectionImpl.getConnectionLogger().fine(
                "Connecting to portmapper without timeout ...");
            if (ssl) {
                socket = SSLUtil.makeSSLSocket(host, port, isHostTrusted,
                             connection.getProperty(
                                 ConnectionConfiguration.imqKeyStore, null),
                             connection.getProperty(
                                 ConnectionConfiguration.imqKeyStorePassword, null),
                             ConnectionImpl.connectionLogger, AdministeredObject.cr);
            } else {
                socket = new Socket(host, port);
            }
        }
        if (sotimeout !=  null) {
            socket.setSoTimeout(sotimeout.intValue());
        } 
        ConnectionImpl.getConnectionLogger().fine ("socket connected., host=" + host + ", port="+ port);
    	return socket;
    }

    public static void main (String args[]) {
        try {
            PortMapperClient pmc = new PortMapperClient (null);
            String protocol = "tcp";

            String prop = System.getProperty("protocol");
            if ( prop != null ) {
                protocol = prop;
            }

            int port = pmc.getPortForProtocol(protocol);

            if ( Debug.debug ) {
                Debug.println ("port = " + port );
            }

        } catch (Exception e) {
            Debug.printStackTrace(e);
        }
    }
}

