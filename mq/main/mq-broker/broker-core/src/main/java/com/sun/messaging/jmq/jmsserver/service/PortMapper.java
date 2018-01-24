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
 * @(#)PortMapper.java	1.35 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory; 
import java.util.concurrent.SynchronousQueue; 
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger; 
import javax.net.ServerSocketFactory;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.PortMapperTable;
import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.service.PortMapperClientHandler;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.LockFile;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.util.net.MQServerSocketFactory;
import com.sun.messaging.portunif.PUService;
import com.sun.messaging.jmq.jmsserver.service.portunif.PortMapperMessageFilter;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;
import com.sun.messaging.portunif.PortMapperProtocolFinder;
import com.sun.messaging.portunif.PUServiceCallback;

/**
 * The PortMapper is a simple service that hands out service/port pairs.
 * Basically a thread listens on a ServerSocket. When a client connects
 * the PortMapper dumps the port map to the socket, closes the connection
 * and goes back to listening on the socket.
 */
public class PortMapper implements Runnable, ConfigListener, 
    PortMapperClientHandler, PUServiceCallback {

    private static final int PORTMAPPER_VERSION_MAX_LEN = 
        PortMapperProtocolFinder.PORTMAPPER_VERSION_MAX_LEN;

    public static final int PORTMAPPER_DEFAULT_PORT = 7676;

    public static final String SERVICE_NAME = "portmapper";

    // Hostname can not be dynamically updated
    public static final String HOSTNAME_PROPERTY = Globals.IMQ+".portmapper.hostname";

    private static final String IMQHOSTNAME_PROPERTY = Globals.IMQ+".hostname";

    private static final String PORT_PROPERTY = Globals.IMQ+".portmapper.port";
    public static final String BIND_PROPERTY = Globals.IMQ+".portmapper.bind";
    private static final String BACKLOG_PROPERTY = Globals.IMQ+".portmapper.backlog";
    private static final String SOTIMEOUT_PROPERTY = Globals.IMQ+".portmapper.sotimeout";
    private static final String SOLINGER_PROPERTY = Globals.IMQ+".portmapper.solinger";
    private static final String LEAST_INFO_PROPERTY = Globals.IMQ+".portmapper.leastInfo";

    public static final String SSL_ENABLED_PROPERTY = 
                  Globals.IMQ+".portmapper.tls.enabled";
    public static final boolean SSL_ENABLED_PROPERTY_DEFAULT = false;

    public static final String TCP_ALLOWED_HOSTNAMES_PROPERTY = 
                  Globals.IMQ+".portmapper.tls.tcpAllowHostNames";

    private List<InetAddress> allowedHosts = 
          Collections.synchronizedList(new ArrayList<InetAddress>()); 

    private static boolean DEBUG = false;

    private BrokerResources rb = null;
    private BrokerConfig bc = null;

    protected Logger logger = null;
    protected PortMapperTable portMapTable = null;

    private ServerSocket   serverSocket = null;
    private int port = 7676;
    
    // specifies whether the portmapper should bind to the portmapper port,
    // or whether some other component will do that on our behalf 
    private boolean doBind = true;

    private int backlog = 100;
    private int sotimeout = 100;
    private int solinger = -1;
    private InetAddress bindAddr = null;
    private String hostname = null;

    private HashMap portmapperMap = null;

    private MQAddress mqaddress = null;

    private boolean sslEnabled = false; //always require client auth

    private boolean running = false;

    private static MQServerSocketFactory ssf = (MQServerSocketFactory)MQServerSocketFactory.getDefault();

    private ThreadPoolExecutor threadPool;
 
   private static AtomicInteger threadCount = new AtomicInteger(0);

    public boolean getDEBUG() {
        return DEBUG;
    }

    /**
     * updates the portmapper service
     */
    public void updateProperties() {
        // if we have a brokerid set, publish in in the portmapper
        // entry
        portmapperMap = new HashMap();
        if (Globals.getBrokerID() != null) {
            portmapperMap.put("brokerid", Globals.getBrokerID());
        }
        if (Globals.getBrokerSessionID() != null) {
            portmapperMap.put("sessionid", Globals.getBrokerSessionID().toString());

            if (!Globals.getConfig().getBooleanProperty(LEAST_INFO_PROPERTY, true)) {
	        String tmp;
	        tmp = Globals.getConfig().getProperty(Globals.JMQ_HOME_PROPERTY);
	        if ((tmp != null) && (!tmp.equals("")))  {
                    portmapperMap.put("imqhome", tmp);
	        }
	        tmp = Globals.getConfig().getProperty(Globals.JMQ_VAR_HOME_PROPERTY);
	        if ((tmp != null) && (!tmp.equals("")))  {
                   portmapperMap.put("imqvarhome", tmp);
	        }
            }
        }
        updateServiceProperties(SERVICE_NAME, portmapperMap );
    }
    /**
     * Create a portmapper for this instance of a broker.
     *
     * @param instance  Instance name of this broker
     */
    public PortMapper(String instance) {
        running = true;
        bc = Globals.getConfig();
        if (!bc.getBooleanProperty("imq.portmapper.reuseAddress", true)) {
            ssf.setReuseAddress(false);
        }
        if (bc.getBooleanProperty(SSL_ENABLED_PROPERTY, 
                   SSL_ENABLED_PROPERTY_DEFAULT)) {
            sslEnabled = true;
        }
        portMapTable = new PortMapperTable();
        portMapTable.setBrokerInstanceName(instance);
        portMapTable.setPacketVersion(String.valueOf(Packet.CURRENT_VERSION));
        logger = Globals.getLogger();
        rb = Globals.getBrokerResources();
        int minthrs = Runtime.getRuntime().availableProcessors();
        int maxthrs = bc.getIntProperty("imq.portmapper.max_threads", minthrs);
        int katime = bc.getIntProperty("imq.portmapper.keepAliveTime", 60); //secs
        if (maxthrs < 1) {
            maxthrs = 1;
        }
        if (maxthrs < minthrs) {
            minthrs = maxthrs;
        }
        threadPool = new ThreadPoolExecutor(minthrs, maxthrs, 
                             katime, TimeUnit.SECONDS, new SynchronousQueue(), 
                             new MyThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
        logger.log(Logger.INFO, rb.getKString(rb.I_CREATE_THREADPOOL_FOR_SERVICE, SERVICE_NAME, 
                                              "("+minthrs+", "+maxthrs+")("+katime+"s)"));

        addService(SERVICE_NAME, "tcp", "PORTMAPPER", port, portmapperMap);
        addService("cluster_discovery", "tcp", "CLUSTER_DISCOVERY", 0, null);

        bc.addListener(PORT_PROPERTY, this);
        bc.addListener(BACKLOG_PROPERTY, this);
    }

    public void destroy() {
        running = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException ex) {
            logger.logStack(Logger.INFO,"Error closing portmapper", ex);
        }
        serverSocket = null;
        PUService pu = Globals.getPUService();
        if (pu != null) {
            try {
                pu.destroy();
            } catch (IOException ex) {
                logger.logStack(Logger.INFO,
                "Error closing Grizzly PU service transport", ex);
            }
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }      
    }

    /**
     * Configure the portmapper with its properties
     */
    public void configure(BrokerConfig params)
        throws PropertyUpdateException {
    	
    	// doBind must be checked first
        doBind = params.getBooleanProperty(PortMapper.BIND_PROPERTY,true);

        // Hostname must be configured next
        String value = (String)params.getProperty(HOSTNAME_PROPERTY);
        if (value == null || value.trim().length() == 0) {
            // If portmapper specific hostname is not set, check imq.hostname
            value = (String)params.getProperty(IMQHOSTNAME_PROPERTY);
        }
        validate(HOSTNAME_PROPERTY, value);
        update(HOSTNAME_PROPERTY, value, true);

        value = (String)params.getProperty(PORT_PROPERTY);
        validate(PORT_PROPERTY, value);
        update(PORT_PROPERTY, value, true);

        value = (String)params.getProperty(BACKLOG_PROPERTY);
        validate(BACKLOG_PROPERTY, value);
        update(BACKLOG_PROPERTY, value, true);

        value = (String)params.getProperty(SOTIMEOUT_PROPERTY);
        if (value != null) {
            validate(SOTIMEOUT_PROPERTY, value);
            update(SOTIMEOUT_PROPERTY, value, true);
        }

        value = (String)params.getProperty(SOLINGER_PROPERTY);
        if (value != null) {
            validate(SOLINGER_PROPERTY, value);
            update(SOLINGER_PROPERTY, value, true);
        }
    }

    /**
     * Change the portmapper service's port
     */
    private synchronized void setPort(int port, boolean initOnly) {

        if (port == this.port) {
            return;
        }

        this.port = port;

        addService(SERVICE_NAME, "tcp", "PORTMAPPER", port, portmapperMap);

        LockFile lf = LockFile.getCurrentLockFile();

        try {
            if (lf != null) {
                lf.updatePort(port, Globals.getUseFileLockForLockFile());
            }
        } catch (IOException e) {
            logger.log(Logger.WARNING, rb.E_LOCKFILE_BADUPDATE, e);
        }

        if (serverSocket != null) {
            // If there is a server socket close it so we create a new
            // one with the new port
            try {
                serverSocket.close();
            } catch (IOException e) {}
        }
        PUService pu = Globals.getPUService();
        if (pu != null) { 
            try {
                pu.rebind(new InetSocketAddress(bindAddr, port), backlog);
            } catch (IOException e) {
                logger.logStack(logger.ERROR,
                    Globals.getBrokerResources().getKString(
                        BrokerResources.X_PU_SERVICE_REBIND, 
                        (bindAddr == null ? "":bindAddr.getHostAddress())+":"+port), e);
            }
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * Change the portmapper service's host interface
     */
    private synchronized void setHostname(String hostname, boolean initOnly)
        throws PropertyUpdateException {

        MQAddress mqaddr = null;
        try {
            String h = hostname;
            if (hostname != null && 
                hostname.equals(Globals.HOSTNAME_ALL)) {
                h = null;
            }
            mqaddr = MQAddress.getMQAddress(h, getPort());
        } catch (Exception e) {
            throw new PropertyUpdateException(
                PropertyUpdateException.InvalidSetting,
                hostname+": "+e.toString(), e);
        }

        if (hostname == null || hostname.equals(Globals.HOSTNAME_ALL) ||
            hostname.trim().length() == 0) {
            // Bind to all
            this.hostname = null;
            this.bindAddr = null;
            mqaddress = mqaddr;
            if (!initOnly) {
                PUService pu = Globals.getPUService();
                if (pu != null) {
                    try {
                        pu.rebind(new InetSocketAddress(bindAddr, port), backlog);
                    } catch (IOException e) {
                        logger.logStack(logger.ERROR, 
                            Globals.getBrokerResources().getKString(
                            BrokerResources.X_PU_SERVICE_REBIND, 
                           (bindAddr == null ? "":bindAddr.getHostAddress())+":"+port), e);
                    }
                }
            }
            return;
        }

        if (hostname.equals(this.hostname)) {
            return;
        }

        try {
            if (Globals.isConfigForCluster()) {
                String hn = hostname;
                if (hn.equals("localhost")) hn = null; 
                this.bindAddr = BrokerMQAddress.resolveBindAddress(hn, true);
                mqaddr = MQAddress.getMQAddress(
                    this.bindAddr.getHostAddress(), getPort());
            } else {
                this.bindAddr = InetAddress.getByName(hostname);
            }
        } catch (Exception e) {
            throw new PropertyUpdateException(
                    PropertyUpdateException.InvalidSetting,
                    rb.getString(rb.E_BAD_HOSTNAME, hostname),
                    e);
        }

        this.hostname = hostname;
        this.mqaddress =  mqaddr;

        LockFile lf = LockFile.getCurrentLockFile();

        try {
	        if (lf != null) {
	           lf.updateHostname(mqaddress.getHostName(), 
                       Globals.getUseFileLockForLockFile());
	        }
        } catch (IOException e) {
	        logger.log(Logger.WARNING, rb.E_LOCKFILE_BADUPDATE, e);
        }

        if (serverSocket != null) {
            // If there is a server socket close it so we create a new
            // one with the new port
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }

        if (!initOnly) {
            PUService pu = Globals.getPUService();
            if (pu != null) {
                try {
                    pu.rebind(new InetSocketAddress(bindAddr, port), backlog);
                } catch (IOException e) {
                    logger.logStack(logger.ERROR,
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_PU_SERVICE_REBIND, 
                        (bindAddr == null ? "":bindAddr.getHostAddress())+":"+port), e);
                }
            }
        }
    }

    /**
     * This should never return HOSTNAME_ALL when
     * called by Globals.getPortMapper().getHostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * This should never return null when called 
     * by Globals.getPortMapper().getMQAddress
     */
    public MQAddress getMQAddress() {
        return mqaddress;
    }

    public InetAddress getBindAddress() {
        return bindAddr;
    }

    /**
     * caller must not modify the returned table
     */
    public PortMapperTable getPortMapTable() {
        return portMapTable;
    }

    /**
     * Set the backlog parameter on the socket the portmapper is running on
     */
    private synchronized void setBacklog(int backlog, boolean initOnly) {
        this.backlog = backlog;

        if (serverSocket != null) {
            // If there is a server socket close it so we create a new
            // one with the new backlog
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }

        if (!initOnly) {
            PUService pu = Globals.getPUService();
            if (pu != null) {
                try {
                    pu.setBacklog(backlog);
                } catch (IOException e) {
                    logger.logStack(logger.WARNING, 
                    "Failed to set PU service backlog to "+backlog, e);
                }
            }
        }
    }

    /**
     * Add a service to the port mapper
     *
     * @param   name    Name of service
     * @param   protocl Transport protocol of service ("tcp", "ssl", etc)
     * @param   type    Service type (NORMAL, ADMIN, etc)
     * @param   port    Port service is runningon
     */
    public synchronized void addService(String name,
        String protocol, String type, int port, HashMap props) {
        PortMapperEntry pme = new PortMapperEntry();

        pme.setName(name);
        pme.setProtocol(protocol);
        pme.setType(type);
        pme.setPort(port);

        if (props != null) {
            pme.addProperties(props);
        }
        portMapTable.add(pme);

    }

    /**
     * update the port for a service
     * @param   name    Name of service
     * @param   port    Port service is runningon
     */

    public synchronized void updateServicePort(String name, int port)
    {
        PortMapperEntry pme = portMapTable.get(name);
        if (pme != null) {
           pme.setPort(port);
        }
    }

    /**
     * update the service properties
     * @param   name    Name of service
     * @param   props   Properties for a service
     */

    public synchronized void updateServiceProperties(String name, HashMap props)
    {
        PortMapperEntry pme = portMapTable.get(name);
        if (pme != null) {
            pme.addProperties(props);
        }
    }


    /**
     * Add a service to the port mapper
     *
     * @param   pme    A PortMapperEntry containing all information for
     *                  the service.
     */
    public synchronized void addService(String name, PortMapperEntry pme) {
        portMapTable.add(pme);
    }

    /**
     * Remove a service from the port mapper
     */
    public synchronized void removeService(String name) {
        portMapTable.remove(name);
    }

    /**
     * Get a hashtable containing information for each service.
     * This hashtable is indexed by the service name, and the values
     * are PortMapperEntry's
     */
    public synchronized Map getServices() {
        return portMapTable.getServices();
    }

    public synchronized String toString() {
        return portMapTable.toString();
    }

    /**
     * Bind the portmapper to the port
     * unless we have configured the portmapper to expect some other component to do this on our behalf
     */
    public synchronized void bind() throws Exception {
        PUService pu = Globals.getPUService();
        if (sslEnabled && pu  == null) {
            throw new BrokerException(Globals.getBrokerResources().
                getKString(BrokerResources.E_PROPERTY_SETTING_REQUIRES_PROPERTY,
                SSL_ENABLED_PROPERTY+"=true",  Globals.PUSERVICE_ENABLED_PROP+"=true"));
        }
    	if (doBind) {
            if (pu == null) {
                serverSocket = createPortMapperServerSocket(this.port, this.bindAddr);
            } else {
                try {
                    pu.register(PortMapperMessageFilter.
                        configurePortMapperProtocol(pu, this), this);
                    if (sslEnabled) {
                        Properties props = KeystoreUtil.getDefaultSSLContextConfig(
                                                        "Broker[portpapper]", null);
                        pu.registerSSL(PortMapperMessageFilter.
                            configurePortMapperSSLProtocol(pu, this,
                            props, true /*require client auth */), this);
                    }
                    pu.bind(new InetSocketAddress(this.bindAddr, this.port), backlog);
                    logger.logToAll(logger.INFO, Globals.getBrokerResources().getKString(
                        BrokerResources.I_PU_SERVICE_READY,  "["+
                        (this.bindAddr == null ? "":this.bindAddr)+":"+this.port+"]"+
                        (sslEnabled ? "TCP/SSL/TLS":"TCP")));

                } catch (Exception e) {
                    String emsg = "PU service failed to init";
                    logger.logStack(logger.ERROR, emsg, e);
                    throw new BrokerException(emsg);
                }
            }
        } else if (pu != null) {
            throw new BrokerException(
                Globals.PUSERVICE_ENABLED_PROP+"=true setting not allowed if nobind");
        }
    }

    public synchronized void startPUService() throws Exception {
        PUService pu = Globals.getPUService();
        if (pu == null) { 
            return;
        }
        if (sslEnabled) {
            List hosts = Globals.getConfig().getList(TCP_ALLOWED_HOSTNAMES_PROPERTY);
            if (hosts != null) {
                Iterator itr = hosts.iterator();
                while (itr.hasNext()) {
                    String host = (String)itr.next();
                    allowedHosts.add(BrokerMQAddress.createAddress(host, 7676).getHost());
                }
            }
        }
        pu.start();
    }

    /**
     * Return the ServerSocket the portmapper is using.
     * Returns null if portmapper is not currently bound to
     * a server socket.
     */
    public synchronized ServerSocket getServerSocket() {
    	return serverSocket;
    }

    /**
     * Create a ServerSocket for the portmapper
     *
     * @param   port        Port number to create socket on
     * @param   bindAddr    Interface to bind to. Null to bind to all.
     */
    private ServerSocket createPortMapperServerSocket(
                                int port, InetAddress bindAddr)  {

        ServerSocket serverSocket = null;
        try {
            serverSocket = ssf.createServerSocket(port, backlog, bindAddr);
        } catch (BindException e) {
            logger.log(Logger.ERROR, rb.E_BROKER_PORT_BIND, 
                SERVICE_NAME, String.valueOf(port));
            return null;
        } catch (IOException e) {
            logger.log(Logger.ERROR, rb.E_BAD_SERVICE_START, 
                SERVICE_NAME, Integer.valueOf(port), e);
            return null;
        }

        Object[] args = {SERVICE_NAME,
                         "tcp [ " + port + ", " + backlog + ", " +
                         (bindAddr != null ? bindAddr.getHostAddress() :
                         Globals.HOSTNAME_ALL) +
                         " ]",
                          Integer.valueOf(1), Integer.valueOf(1)};
        logger.log(Logger.INFO, rb.I_SERVICE_START, args);

        return serverSocket;
    }

    public void run() {
		int restartCode = BrokerStateHandler.getRestartCode();
		String restartOOMsg = rb.getKString(rb.M_LOW_MEMORY_PORTMAPPER_RESTART);

		String acceptOOMsg = rb.getKString(rb.M_LOW_MEMORY_PORTMAPPER_ACCEPT);

		Socket connection = null;
		boolean oom = false;
		try {

			if (serverSocket == null) {
				serverSocket = createPortMapperServerSocket(this.port, this.bindAddr);
			}

			if (DEBUG && serverSocket != null) {
				logger.log(Logger.INFO, "PortMapper: " + serverSocket + " "
						+ MQServerSocketFactory.serverSocketToString(serverSocket) + ", backlog=" + backlog + "");
			}

			boolean firstpass = true;
			while (running) {
				if (serverSocket == null) {
					logger.log(Logger.ERROR, rb.E_PORTMAPPER_EXITING);
					return;
				}

				try {
					connection = serverSocket.accept();
					firstpass = true;
				} catch (SocketException e) {
					if (e instanceof BindException || e instanceof ConnectException
							|| e instanceof NoRouteToHostException) {

						logger.log(Logger.ERROR, rb.E_PORTMAPPER_ACCEPT, e);
						// We sleep in case the exception is not repairable.
						// This
						// prevents us from a tight loop.
						sleep(1);
					} else {
						if (!running)
							break;
						// Serversocket was closed. Should be because something
						// like the port number has changed. Try to recreate
						// the server socket.
						try {
							// Make sure it is closed
							serverSocket.close();
						} catch (IOException ioe) {
						} catch (NullPointerException ioe) {
							if (!running)
								break;
						}
						serverSocket = createPortMapperServerSocket(this.port, this.bindAddr);
					}
					continue;
				} catch (IOException e) {
					logger.logStack(Logger.ERROR, rb.E_PORTMAPPER_ACCEPT, e);
					sleep(1); 
					continue; 
				} catch (OutOfMemoryError e) { 
					if (!running)
						break;
					if (firstpass) {
						firstpass = false;
						Globals.handleGlobalError(e, acceptOOMsg);
						sleep(1);
						continue;
					}
					Broker.getBroker().exit(restartCode, restartOOMsg, BrokerEvent.Type.RESTART, null, false, false,
							true);
					return;
				}

				// process the new portmapper client and close the socket
				if (DEBUG) {
					logger.log(Logger.INFO, "PortMapper call handleSocket("+connection+")");
				}
				
				final Socket s = connection;
				threadPool.execute(new Runnable() {          
					public void run() {
						handleSocket(s);
					}
					});

			}  

		} catch (OutOfMemoryError e) {
			oom = true;
			throw e;
		} finally {
			try {
				try {
					if (connection != null)
						connection.close();
					if (serverSocket != null)
						serverSocket.close();
				} catch (IOException e) {
				}

				if (oom && running) {
					logger.log(Logger.ERROR, restartOOMsg);
					Broker.getBroker().exit(restartCode, restartOOMsg, BrokerEvent.Type.RESTART, null, false, false,
							true);
				}
				if (running)
					logger.log(Logger.INFO, rb.M_PORTMAPPER_EXITING);
			} catch (OutOfMemoryError e) {
				if (running) {
					Broker.getBroker().exit(restartCode, restartOOMsg, BrokerEvent.Type.RESTART, null, false, false,
							true);
				}
			}
		}
	}
    
    /**
     * Process a newly-connected PortMapper client and then close the socket
     * 
     * This method takes a Socket and is intended to be called by this class's run() loop after
     * the incoming connection has been accepted and the new socket created
     * 
     * @param socket the newly-connected PortMapper client
     */
    public void handleSocket(Socket socket) {
        String connOOMsg = rb.getKString(rb.M_LOW_MEMORY_PORTMAPPER_CONNECTION);

        // Make sure socket is still connected. Client may
        // have disconnected if we were slow to accept connection
        if (!socket.isConnected()) {
            if (DEBUG) {
                logger.log(Logger.INFO, 
                    "PortMapper.handleSocket("+socket+ "): no longer connected. Ignoring.");
            }
            try {
                socket.close();
            } catch (IOException e) {}
            return;
        }

        // Got connection. Write port map and close connection
        try {
                /*
                 * Get version from client. 2.0 client does not send a version
                 * so we must set SoTimeout to timeout if there is nothing to
                 * read. 3.0 client does send version (101).
                 */
                socket.setSoTimeout(sotimeout);
                if (solinger > 0) {
                    socket.setSoLinger(true, solinger);
                }
                InputStream is = socket.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String version = "";
                try {
                    version = readLineWithLimit(br);
                } catch (SocketTimeoutException e) {
                    // 2.0 client did not send version
                }

                synchronized (this) {
                portMapTable.write(socket.getOutputStream());
                }

                // Force reads until EOF. This avoids leaving
                // sockets in TIME_WAIT. See 4750307. Typically
                // this will block until the client closes the
                // connection or SoTimeout expires.
                try {
                    int n = 0;
                    while (readLineWithLimit(br) != null) {
                        if (++n >= 5) break; // Don't read forever
                    }
                } catch (SocketTimeoutException e) {
                    // Client did not close socket before sotimeout
                    // expired. That's OK. Just means we leave connection
                    // in TIME_WAIT state.
                }
        } catch (IOException e) {
            InetAddress ia = socket.getInetAddress();
            logger.logStack(Logger.WARNING, rb.E_PORTMAPPER_EXCEPTION, ia.getHostAddress(), e);
        } catch (OutOfMemoryError e) {
            if (!running) return;
            try {
                socket.close();
            } catch (Throwable t) {
            } finally {
                logger.log(Logger.WARNING, connOOMsg);
            }
            sleep(1);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }

    private static class MyThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(SERVICE_NAME+"-"+threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
    
    private String readLineWithLimit(BufferedReader br) throws IOException {
        StringBuffer line = new StringBuffer();

        int cnt = 0;
        Character cc = null;
        Character cr = Character.valueOf('\r');
        Character lf = Character.valueOf('\n');
        while (true) {
            int c = br.read();
            if (c < 0) {
                return null;
            }
            cc = Character.valueOf((char)c);
            if (cc.equals(cr) || cc.equals(lf)) {
                break;
            }
            line.append((char)c);
            cnt++;
            if (cnt > PORTMAPPER_VERSION_MAX_LEN) {
                throw new IOException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.W_UNEXPECTED_READ_DATA_SIZE, 
                        String.valueOf(cnt), SERVICE_NAME));
            }
        }
        return line.toString();
    }

    /**
     * Process a newly-connected PortMapper client and then close the socket
     * 
     * This method takes a SocketChannel and is intended to be called by an external proxy 
     * which has accepted the connection for us and created the new socket
     * 
     * @param clientSocketChannel the newly-connected PortMapper client
     */
	public void handleRequest(SocketChannel clientSocketChannel) {
		if (doBind) throw new IllegalStateException("Should not call PortMapper.handleRequest() unless Broker has been started with the -noBind argument");
		
		handleSocket(clientSocketChannel.socket());
	}   

    public void validate(String name, String value) 
        throws PropertyUpdateException {

        if (!name.equals(PORT_PROPERTY) &&
            !name.equals(BACKLOG_PROPERTY) &&
            !name.equals(SOLINGER_PROPERTY) &&
            !name.equals(HOSTNAME_PROPERTY) &&
            !name.equals(SOTIMEOUT_PROPERTY)) {
            throw new PropertyUpdateException(
                rb.getString(rb.X_BAD_PROPERTY, name));
        }

        if (name.equals(HOSTNAME_PROPERTY)) {
            if (value == null || value.trim().length() == 0 ||
                value.equals(Globals.HOSTNAME_ALL)) {
                // null is OK. Means bind to all interfaces
                return;
            }
            try {
                if (Globals.isConfigForCluster()) {
                    BrokerMQAddress.resolveBindAddress(value, true);
                } else {
                    InetAddress.getByName(value);
                }
            } catch (Exception e) {
                throw new PropertyUpdateException(
                    PropertyUpdateException.InvalidSetting,
                    rb.getKString(rb.E_BAD_HOSTNAME_PROP, value, name)+": "+e.toString(),
                    e);
            }
            return;
        }

        // Will throw an exception if integer value is bad
        int n = getIntProperty(name, value);

        if (name.equals(PORT_PROPERTY)) {
            if (n == this.port)  {
                return;
            }
            if (n == 0) {
                throw new PropertyUpdateException(
                    PropertyUpdateException.InvalidSetting,
                    rb.getString(rb.X_BAD_PROPERTY_VALUE, name+"="+value));
            }
            if (isDoBind()){
                // Check if we will be able to bind to this port
                try {
                    canBind(n, this.bindAddr);
                } catch (BindException e) {
                    throw new PropertyUpdateException(
                        rb.getKString(rb.E_BROKER_PORT_BIND, SERVICE_NAME,
                            String.valueOf(value))+"\n"+e.toString());
                } catch (IOException e) {
                     throw new PropertyUpdateException(
                         rb.getKString(rb.E_BAD_SERVICE_START, SERVICE_NAME,
                             String.valueOf(value)) + "\n"+e.toString());
                }
            }
        }
    }

    public boolean update(String name, String value) {
        return update(name, value, false);
    }

    private boolean update(String name, String value, boolean initOnly) {
        try {
            if (name.equals(PORT_PROPERTY)) {
                setPort(getIntProperty(name, value), initOnly);
                if (mqaddress != null) {
                    mqaddress = MQAddress.getMQAddress(
                        mqaddress.getHostName()+":"+getPort());
                }
            } else if (name.equals(BACKLOG_PROPERTY)) {
                setBacklog(getIntProperty(name, value), initOnly);
            } else if (name.equals(SOTIMEOUT_PROPERTY)) {
                sotimeout = getIntProperty(name, value);
            } else if (name.equals(SOLINGER_PROPERTY)) {
                solinger = getIntProperty(name, value);
            } else {
                setHostname(value, initOnly);
            }
        } catch (Exception e) {
            logger.log(Logger.ERROR,
                rb.getString(rb.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
            return false;
        }
        return true;
    }

    public int getIntProperty(String name, String value)
                        throws PropertyUpdateException  {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PropertyUpdateException(
                rb.getString(rb.X_BAD_PROPERTY_VALUE, name + "=" + value));
        }
    }

    public static void sleep(int nseconds) {
	try {
	    Thread.sleep(nseconds * 1000L);
        } catch (Exception e) {}

    }

    /**
     * Check if you can open a server socket on the specified port
     * and address.
     *
     * @param port      Port number to bind to
     * @param bindAddr  Address to bind to. Null to bind to all connections
     *
     * Throws an IOException if you can't
     */
    public static void canBind(int port, InetAddress bindAddr)
        throws IOException {

        ServerSocket ss = null;
        ss = ssf.createServerSocket(port, 0, bindAddr);
        ss.close();
        return;
    }
    
    /**
     * Return whether the portmapper should bind to the portmapper port,
     * or whether some other component will do that on our behalf 
     * 
     * @return
     */
    public boolean isDoBind() {
        return doBind;
    }
  
    /*******************************************************
     * Implement PUServiceCallback interface
     ********************************************************************/
    public boolean allowConnection(InetSocketAddress sa, boolean ssl) {
        if (DEBUG) {
            logger.log(logger.INFO, 
            "PortMapper.alllowConnection("+sa+", "+ssl+"), sslEnabled="+sslEnabled+
            ",  allowedHosts="+allowedHosts+", broker="+
             Globals.getBrokerInetAddress());
        }
        if (!sslEnabled) {
            return true;
        }
        if (ssl) {
            return true;
        }
        if (sa.getAddress().equals(Globals.getBrokerInetAddress())) {
            return true;
        }
        if (sa.getAddress().isLoopbackAddress()) {
            return true;
        }
        if (allowedHosts.contains(sa.getAddress())) {
            return true;
        }

        ClusterManager cm = Globals.getClusterManager();   
        Iterator itr = cm.getConfigBrokers();
	ClusteredBroker cb = null;
        BrokerMQAddress addr = null;
        while (itr.hasNext()) {
            cb = (ClusteredBroker)itr.next();
            addr = (BrokerMQAddress)cb.getBrokerURL();
            if (DEBUG) {
                logger.log(logger.INFO, 
                "PortMapper.allowConnection("+sa+"), check configured cluster broker "+ addr);
            }
            if (addr.getHost().equals(sa.getAddress())) {
                return true;
            }
        } 
        return false;
    }

    public void logInfo(String msg) {
        logger.log(Logger.INFO, msg);
    }
    public void logWarn(String msg, Throwable e) {
        if (e != null) {
            logger.logStack(Logger.WARNING, msg, e);
        } else {
            logger.log(Logger.WARNING, msg);
        }
    }
    public void logError(String msg, Throwable e) {
        logger.logStack(Logger.ERROR, msg, e);
    }

}

