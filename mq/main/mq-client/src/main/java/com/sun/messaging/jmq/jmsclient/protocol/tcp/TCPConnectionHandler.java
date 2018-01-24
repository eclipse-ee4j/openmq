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
 * @(#)TCPConnectionHandler.java	1.23 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.tcp;

import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;
import com.sun.messaging.jmq.jmsclient.protocol.SocketConnectionHandler;

import java.net.*;
import java.util.logging.Level;
import java.io.*;


/**
 * This class is the default protocol handler for the iMQ JMS client
 * implementation.  It uses TCP protocol to communicate with the Broker.
 */
public class TCPConnectionHandler extends SocketConnectionHandler {

    private static int connectionCount = 0;
    private int counter = 0;

    private Socket socket = null;
    
    private int socketConnectTimeout = 0;
    
    //private static int soLingerTime = 5;

    private String host = null;

    private int baseport = 0;
    private int directport = 0;
    private int port = 0;
    
    //check if host is reachable
    public static final boolean imqCheckHostIsReachable;
    
    //is reachable time out value in milli seconds.
    public static final int imqIsReachableTimeout;
          
    static {
        boolean tmpcheck = false;
        int tmptimeout = 30000;	
    	try {
    		//check is reachable
    		tmpcheck = Boolean.getBoolean("imqCheckHostIsReachable");
    		
    		//in milli secs
    		String tmp = System.getProperty("imqIsReachableTimeout", "30000");
    		
    		//is reachable timeout
    		tmptimeout = Integer.parseInt(tmp);
    		
    	} catch (Exception ex) {
    		ConnectionImpl.getConnectionLogger().log(Level.WARNING, ex.toString(), ex);
    	}
        imqCheckHostIsReachable = tmpcheck;
        imqIsReachableTimeout = tmptimeout;
    	 
    }
    
    /**
     * Constructor.  Called by TCPStreamHandler.
     * This creates a socket connection to the broker.
     */
    TCPConnectionHandler (Object conn) throws JMSException {
        ConnectionImpl connection = (ConnectionImpl) conn;
        directport = 0;

        // First, gather the configuration attributes.
        host = connection.getProperty(
            ConnectionConfiguration.imqBrokerHostName);
        baseport = Integer.parseInt(connection.getProperty(
            ConnectionConfiguration.imqBrokerHostPort));
        directport = Integer.parseInt(connection.getProperty(
            ConnectionConfiguration.imqBrokerServicePort));
        String namedservice = connection.getProperty(
            ConnectionConfiguration.imqBrokerServiceName);
        socketConnectTimeout=connection.getSocketConnectTimeout();
        

        // Resolve the service port if necessary.
        if (directport == 0) {
            PortMapperClient pmc = new PortMapperClient(connection);
            if (namedservice != null && !("".equals(namedservice))) {
                port = pmc.getPortForService("tcp", namedservice);
            } else {
                port = pmc.getPortForProtocol("tcp");
            }
        } else {
            port = directport;
        }

        ConnectionImpl.checkHostPort (host, port);

        // Create the connection
        try {
            connection.setLastContactedBrokerAddress( getBrokerAddress() );
            this.socket = makeSocket(host, port);
            counter = ++connectionCount;
        } catch ( Exception e ) {
            connection.getExceptionHandler().handleConnectException (
                e, host, port);
        }
    }

    /**
     * Constructor.  Called by TCPStreamHandler.
     * This creates a socket connection to the broker.
     */
    TCPConnectionHandler (MQAddress addr, ConnectionImpl conn)
        throws JMSException {
        ConnectionImpl connection = (ConnectionImpl) conn;
        port = 0;

        // First, gather the configuration attributes.
        host = addr.getHostName();
        directport = 0;
        if (addr.isServicePortFinal())
            directport = addr.getPort();
        String namedservice = addr.getServiceName();
        socketConnectTimeout=connection.getSocketConnectTimeout();

        // Resolve the service port if necessary.
        if (directport == 0) {
            PortMapperClient pmc = new PortMapperClient(addr, connection);
            baseport = pmc.getHostPort();
            if (namedservice != null && !("".equals(namedservice))) {
                port = pmc.getPortForService("tcp", namedservice);
            } else {
                port = pmc.getPortForProtocol("tcp");
            }

        } else {
            port = directport;
        }

        conn.setLastContactedBrokerAddress( getBrokerAddress() );

        ConnectionImpl.checkHostPort (host, port);

        // Create the connection
        try {
            this.socket = makeSocket(host, port);
            counter = ++connectionCount;
        } catch ( Exception e ) {
            connection.getExceptionHandler().handleConnectException (
                e, host, port);
        }
    }

    private Socket makeSocket(String host, int port) throws Exception {
        if (Debug.debug) {
            Debug.println("in TCPConnectionHandler.makeSocket()");
        }

        //tcp no delay flag
        boolean tcpNoDelay = true;
        String prop = System.getProperty("imqTcpNoDelay", "true");
        if ( prop.equals("false") ) {
            tcpNoDelay = false;
        }
        
        checkIsReachable(host, port);

        //Socket socket = new Socket(host, port);
        
        //bug 6696742 - be able to set connect timeout 
        Socket socket = makeSocketWithTimeout(host, port, socketConnectTimeout);
        
        socket.setTcpNoDelay( tcpNoDelay );

        return socket;
    }
    
    /**
     * Check if a host is reachable.
     * 
     * @param host 
     * @param port
     * @throws IOException
     */
    private void checkIsReachable (String host, int port) throws IOException {
    	
    	if (imqCheckHostIsReachable) {
    		
    		ConnectionImpl.getConnectionLogger().fine ("checking network is reachable");
    		
    		//get instance
    		InetAddress iaddr = InetAddress.getByName(host);
    		
    		//check if reachable
    		boolean isReachable = iaddr.isReachable(imqIsReachableTimeout);
    		
    		if ( isReachable == false ) {
    			
    			ConnectionImpl.getConnectionLogger().fine ("network is not reachable, host=" + host);
    			
    			throw new IOException ("Network is unreachable. Host= " + host);
    		} else {
    			ConnectionImpl.getConnectionLogger().fine ("network is reachable, host=" + host);
    		}
    	}
    	
    }
    
    private Socket makeSocketWithTimeout (String host, int port, int timeout) throws IOException {
    	
    	Socket socket = null;
    	
    	if (timeout > 0) {
    		
    		ConnectionImpl.getConnectionLogger().fine ("connecting with timeout=" + timeout);
    		
    		socket = new Socket();
    	
    		InetSocketAddress socketAddr = new InetSocketAddress (host, port);
    	
    		socket.connect(socketAddr, timeout);
    	
    		//disable the timeout
    		socket.setSoTimeout(0);
    		
    	} else {
    		
    		ConnectionImpl.getConnectionLogger().fine ("connecting with no timeout ...");
    		
    		socket = new Socket(host, port);
    	}
    	
    	ConnectionImpl.getConnectionLogger().fine ("socket connected., host=" + host + ", port="+ port);
    	
    	return socket;
    }
    
    

    /*
     * Get socket input stream.
     */
    public InputStream
    getInputStream() throws IOException {
        return socket.getInputStream();
    }

     /*
     * Get socket output stream.
     */
    public OutputStream
    getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

     /*
     * Get socket local port for the current connection.
     */
    public int
    getLocalPort() throws IOException {
        return socket.getLocalPort();
    }
    
    protected void closeSocket() throws IOException{
    	socket.close();
    }

    public String getBrokerHostName() {
        return this.host;
    }

    public String getBrokerAddress() {

        if (directport == 0) {
            return host + ":" + baseport + "(" + port + ")";
        } else {
            return host + ":" + directport;
        }
        //return host + ":" port;
    }
    
    public int getSocketConnectTimeout() {
		return socketConnectTimeout;
    }

    public String toString() {
        String info = null;
        try {
        info =  "TCPConnectionHandler: " + counter + "-" + getLocalPort();
        } catch (Exception e) {
            Debug.printStackTrace(e);
        }

        return info;
    }

}
