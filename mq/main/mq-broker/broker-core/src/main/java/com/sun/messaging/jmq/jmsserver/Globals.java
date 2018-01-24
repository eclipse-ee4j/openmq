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
 * @(#)Globals.java	1.121 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver;

import java.io.*;
import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.Properties;
import java.util.Enumeration;
import java.net.InetAddress;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.management.mbeans.resources.MBeanResources;
import com.sun.messaging.jmq.jmsserver.service.PortMapper;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.BrokerShutdownRuntimeException;
import com.sun.messaging.jmq.jmsserver.util.LoggerManager;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.jmsserver.memory.MemoryManager;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.timer.MQTimer;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.BrokerExitCode;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.bridge.api.BridgeServiceManager;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;
import com.sun.messaging.jmq.jmsserver.data.protocol.Protocol;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.portunif.PUService;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;

import java.io.File;
/**
 * Singleton class which contains any Globals for the
 * system.<P>
 *
 * Other singleton classes which can be considered static
 * once they are retrieved (they do not need to be retrieved
 * from the static method each time they are used) should
 * also be defined here <P>
 */

public final class Globals extends CommGlobals
{
    /**
     * Hostname that signifies bind-to-all interfaces
     */
    public static final String HOSTNAME_ALL = "*";

    /**
     * the thread pool OutOfMemory Error handler (if any)
     */
    private static GlobalErrorHandler errhandler = null;

    private static volatile Globals globals = null;

    private static Protocol protocol = null;
    
    private static volatile MBeanResources mbr = null;

    private volatile static Version version = null;

    private static volatile PUService puService = null;
    private static volatile PortMapper portMapper = null;

    private static MQAddress mqAddress = null;

    private static ServiceManager serviceManager = null;

    private static ConnectionManager connectionManager = null;

    private static ClusterBroadcast messageBus = null;

    private static Object heartbeatService = null;

    private static MetricManager metricManager = null;

    private static volatile MQTimer timer = null;

    private static volatile Boolean HAEnabled = null;
    private static volatile Boolean useSharedConfigRecord = null;

    private static String clusterID = null;

    private static UID HAStoreSession =  new UID(-1);
    private static UID brokerSession =  null;

    private static String brokerID = null;

    private static String hostname = null;

    private static String jmxHostname = null;

    private static BrokerAddress myaddr = null;

    private static InetAddress brokerInetAddress = null;

    private static InetAddress jmxInetAddress = null;

    private static PacketRouter[] routers = null;

    private static volatile MemoryManager mem_manager = null;
    private static boolean useMem = true;

    private static Agent agent = null;

    private static BrokerStateHandler stateHandler = null;

    private static HAMonitorService hasvc = null;

    public static final String INTERNAL_PREFIX = "MQ_";

    //------------------------------------------------------------------------
    //--                 static brokerConfig objects                 --
    //------------------------------------------------------------------------
  
    /**
     * singleton instance of ClusterManager
     */
    private static ClusterManager clusterConfig = null;

    /**
     */
    private static volatile CoreLifecycleSpi coreLifecycle = null;
    private static HashMap<String, CoreLifecycleSpi> corePlugins =
                           new HashMap<String, CoreLifecycleSpi>();

    /**
     */
    private static BridgeServiceManager bridgeManager = null; 

    private static boolean apiDirectTwoThreadSyncReplies = true;

    public static void cleanup()
    {
        cleanupComm();

        clusterConfig = null;
        coreLifecycle = null;
        corePlugins.clear();
        globals = null;

        myaddr = null;
        version = null;
        puService = null;
        portMapper = null;
        mqAddress = null;
        serviceManager = null;
        connectionManager = null;
        messageBus = null;
        heartbeatService = null;
        metricManager = null;
        timer = null;
        HAEnabled = null;
        useSharedConfigRecord = null;
        clusterID = null;
        HAStoreSession =  null;
        brokerID = null;
        hostname = null;
        jmxHostname = null;
        brokerInetAddress = null;
        jmxInetAddress = null;
        routers = null;
        mem_manager = null;
        agent = null;
        stateHandler = null;
        bridgeManager = null;
        apiDirectTwoThreadSyncReplies = true;
    }

    private Globals() {
    }

    /**
     * Return whether the property imq.cluster.masterbroker was specified
     * on the command line or read from standard input
     * @return
     */
    public static boolean isMasterBrokerSpecified() {
        Properties params = getParameters();
        if (params == null) {
            return false;
        }
        return (params.get(ClusterManager.CONFIG_SERVER) != null);
    }

    public static void setMemMgrOn(boolean setting)
    {
        useMem = setting;
    }

    public static MemoryManager getMemManager() {
        if (!useMem) return null;
        if (mem_manager == null) {
            synchronized(lock) {
                if (mem_manager == null)
                    mem_manager = new MemoryManager();
            }
        }
        return mem_manager;
    }

    public static void setAgent(Agent ag)  {
        agent = ag;
    }

    public static Agent getAgent()  {
        return (agent);
    }

    public static String getPrimaryOwnerName()  {
        return(Globals.getConfig().getProperty(PRIMARY_OWNER_NAME_PROPERTY,
                       System.getProperty("user.name")));
    }

    public static String getPrimaryOwnerContact()  {
        return(Globals.getConfig().getProperty(PRIMARY_OWNER_CONTACT_PROPERTY,
                       System.getProperty("user.name")));
    }

    public static String[] getBrokerAdminDefinedRoles()  {
        String countPropName = BROKER_ADMIN_DEFINED_ROLES_PROPERTY_BASE + ".count";
        String countStr = Globals.getConfig().getProperty(countPropName);
	String ret[] = null;
	int count = 0;

	if ((countStr == null) || (countStr.equals("")))  {
	    return (getDefaultBrokerAdminDefinedRoles());
	}

	try  {
	    count = Integer.parseInt(countStr);
	} catch(Exception e)  {
            Logger logger = getLogger();
            logger.log(Logger.WARNING, "Invalid value for property "
			+ countPropName
			+ ": "
			+ countStr);
	    return (getDefaultBrokerAdminDefinedRoles());
	}

	if (count == 0)  {
	    return (getDefaultBrokerAdminDefinedRoles());
	}

	ret = new String [ count ];

	for (int i = 0; i < count; ++i)  {
	    String	propName = BROKER_ADMIN_DEFINED_ROLES_PROPERTY_BASE + ".name" + i;

	    ret[i] = getConfig().getProperty(propName);
	}

	return (ret);
    }

    public static String[] getDefaultBrokerAdminDefinedRoles()  {
        /**
         * Default admin defined role is simply the broker instance name.
         */
        String[] ret = {
		getConfig().getProperty("imq.instancename")
		};

	return(ret);
    }

    public static void setProtocol(Protocol impl) {
        protocol = impl;
    }

    public static Protocol getProtocol() {
       return protocol;
    }

    public static void setBrokerStateHandler(BrokerStateHandler sh)  {
        stateHandler = sh;
    }

    public static BrokerStateHandler getBrokerStateHandler()  {
        return (stateHandler);
    }

    public static void setHAMonitorService(HAMonitorService sh)  {
        hasvc = sh;
    }

    public static HAMonitorService getHAMonitorService()  {
        return (hasvc);
    }

    public static void setBridgeServiceManager(BridgeServiceManager bm)  {
        bridgeManager = bm;
    }

    public static BridgeServiceManager getBridgeServiceManager()  {
        return (bridgeManager);
    }

    public static boolean bridgeEnabled()  {
        return BridgeBaseContextAdapter.bridgeEnabled();
    }

    public static Globals getGlobals() {
        if (globals == null) {
            synchronized(lock) {
                if (globals == null)
                    globals = new Globals();
            }
        }
        return globals;
    }


    public static MQTimer getTimer() {
        return getTimer(false);
    }
    public static MQTimer getTimer(boolean purge) {
        if (timer == null) {
            synchronized(lock) {
                if (timer == null) {
                    timer = new MQTimer(true);
                    timer.setLogger(getLogger());
                    timer.initUncaughtExceptionHandler();
                }
            }
        }
        if (purge) timer.purge();
        return timer;
    }

    public static MBeanResources getMBeanResources() {
	if (mbr == null) {
            synchronized(lock) {
	        if (mbr == null) {
	            mbr = MBeanResources.getResources(
		    Locale.getDefault());
		}
	    }
	}
	return mbr;
    }

    public static Version getVersion() {
	if (version == null) {
            synchronized(lock) {
	        if (version == null) {
		    version = new Version(false);
		}
	    }
	}
	return version;
    }

    public static PUService getPUService() {
	if (puService == null) {
        synchronized(lock) {
	        if (puService == null) {
                if (isPortUnifEnabled() && !isNucleusManagedBroker()) {
                    try {
                        Class c = Class.forName("com.sun.messaging.portunif.PUService");
                        puService = (PUService)c.newInstance();
                    } catch (Exception e) {
                        getLogger().logStack(Logger.ERROR, e.getMessage(), e);
                        Broker.getBroker().exit(-1,
                            "Internal Error: Unable to init PUService. Exiting",
                            BrokerEvent.Type.FATAL_ERROR);
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
    }
    return puService;
    }

    public static boolean isPortUnifEnabled() {
         return getConfig().getBooleanProperty(PUSERVICE_ENABLED_PROP, false);
    }

    public static PortMapper getPortMapper() {
        if (portMapper == null) {
            synchronized(lock) {
	        if (portMapper == null) {
                    portMapper = new PortMapper(getConfigName());
                    try {
                        portMapper.configure(getConfig());
                        // Force portmapper to attempt to bind to port
                        portMapper.bind();
                    } catch (Exception e) {
                        portMapper = null;
                        Logger logger = getLogger();
                        if (e instanceof PropertyUpdateException) {
                            logger.log(Logger.ERROR, e.getMessage());
                        } else {
                            logger.logStack(Logger.ERROR, e.getMessage(), e);
                       }
                    }
                }
	    }
	}
	return portMapper;
    }

    /**
     * Get the configured hostname. Can be null of imq.hostname is not
     * configured.
     */
    public static String getHostname() {
        return hostname;
    }

    /**
     * Get the configured hostname for JMX connections/traffic. Can be null 
     * if imq.jmx.hostname or imq.hostname is not configured.
     */
    public static String getJMXHostname() {
	if (jmxHostname != null)
            return jmxHostname;
	
	return (getHostname());
    }

    public static boolean getCoherenceServerEnabled() { 
        return StoreManager.isConfiguredCoherenceServer();
    }

    public static boolean isFileStore() {
        return StoreManager.isConfiguredFileStore();
    }

    public static boolean isBDBStore() {
        return StoreManager.isConfiguredBDBStore();
    }

    public static boolean isJDBCStore() {
        return StoreManager.isConfiguredJDBCStore();
    }

    public static boolean getBDBREPEnabled() {
        return StoreManager.bdbREPEnabled();
    }

    public static boolean getSFSHAEnabled() {
        return StoreManager.isConfiguredBDBSharedFS() && getHAEnabled();
    }

    public static boolean getJDBCHAEnabled() {
        return StoreManager.isConfiguredJDBCStore() && getHAEnabled();
    }

    public static boolean getHAEnabled() {
        if (HAEnabled == null) {
            BrokerConfig conf = getConfig();
            boolean isHA = conf.getBooleanProperty(HA_ENABLED_PROPERTY, HA_ENABLED_DEFAULT);
            String clusterID = conf.getProperty(Globals.CLUSTERID_PROPERTY);
            synchronized(lock) {
                if (HAEnabled == null) {
                   if (isHA) {
                       if (clusterID == null || clusterID.length() == 0) {
                           throw new RuntimeException(
                               getBrokerResources().getKString(
                               BrokerResources.X_CID_MUST_BE_SET_HA));
                       }
                       Globals.HAEnabled = Boolean.TRUE;
                       Globals.clusterID = clusterID;
                    } else {
                        if (clusterID != null && clusterID.length() != 0) {
                            Globals.clusterID = clusterID;
                        }
                        Globals.HAEnabled = Boolean.FALSE;
                    }
                }
            }
        }
        return HAEnabled.booleanValue();
    }


    public static ServiceManager getServiceManager() {
        return serviceManager;
    }

    public static MetricManager getMetricManager() {
        return metricManager;
    }

    public static ConnectionManager getConnectionManager() 
        throws BrokerShutdownRuntimeException {

        ConnectionManager cm = connectionManager;
        if (cm != null) {
            return cm;
        }
        throw new BrokerShutdownRuntimeException(
            getBrokerResources().getKString(
                BrokerResources.W_BROKER_IS_SHUTDOWN));
    }

    public static ClusterBroadcast getClusterBroadcast() {
        return messageBus;
    }

    public static void setHostname(String hostname) {
        Globals.hostname = hostname;
    }

    public static void setJMXHostname(String hostname) {
        Globals.jmxHostname = hostname;
    }

    public static String getClusterID() {
        return clusterID;
    }


    public static UID getStoreSession() throws BrokerException{
        if (HAStoreSession == null || HAStoreSession.longValue() == -1) {
            String emsg = BrokerResources.E_INTERNAL_ERROR+
                "Globals.getStoreSession(): HA store session UID has not been initialized";
            BrokerException be = new BrokerException(emsg);
            Globals.getLogger().logStack(Logger.ERROR, emsg,  be);
            throw be;
        } 
        return HAStoreSession;
    }

    public static void setStoreSession(UID uid) {
        HAStoreSession = uid;
    }

    public static UID getBrokerSessionID() {
        return brokerSession;
    }

    /**
     */
    public static String getBrokerID()
    {
        if (brokerID == null) {
            if (getSFSHAEnabled()) {
                brokerID = getConfigName();
            } else {
                brokerID = getConfig().getProperty(BROKERID_PROPERTY,
                               getConfig().getProperty(JDBCBROKERID_PROPERTY));
            }
            //XXX if brokerID is still null, should we use instancename
        }
        return Globals.brokerID;
    }

    public static String getIdentityName() {
        String id = Globals.getBrokerID();
        if (id != null) return id;
        return getConfigName();
    }

    public static void setServiceManager(ServiceManager sm) {
        serviceManager = sm;
    }

    public static void setMetricManager(MetricManager mm) {
        metricManager = mm;
    }

    public static void setConnectionManager(ConnectionManager cm) {
        connectionManager = cm;
    }

    public static void setClusterBroadcast(ClusterBroadcast mm) {
        messageBus = mm;
    }

    public static void registerHeartbeatService(Object hbs) {
        heartbeatService = hbs;
    }

    public static Object getHeartbeatService() {
        return heartbeatService;
    }

    public static void setMyAddress(BrokerAddress mm) {
        myaddr = mm;
        setMyAddressObject(mm);
    }

    public static BrokerAddress getMyAddress() {
        return myaddr;
    }

    /**
     * Set the InetAddress for this broker.
     */
    public static void setBrokerInetAddress(InetAddress ia) {
        brokerInetAddress = ia;
    }

    /**
     * Get the InetAddress for this broker. Must have been previously
     * set
     */
    public static InetAddress getBrokerInetAddress() {
        return brokerInetAddress;
    }

    /**
     * Set the InetAddress for JMX traffic.
     */
    public static void setJMXInetAddress(InetAddress ia) {
        jmxInetAddress = ia;
    }

    /**
     * Get the InetAddress for JMX traffic. Must have been previously
     * set
     */
    public static InetAddress getJMXInetAddress() {
	if (jmxInetAddress != null)  {
            return jmxInetAddress;
	}

	return (getBrokerInetAddress());
    }

    /**
     * Get the hostname that this broker is running on. setBrokerInetAddress
     * must be called before calling this method otherwise this routine
     * will return null.
     */
    public static String getBrokerHostName() {

        if (hostname != null && !hostname.equals(Globals.HOSTNAME_ALL)) {
            return hostname;
        }

        if (brokerInetAddress == null) {
            return null;
        } else {
            // This is fast so we don't need to cache it.
            return brokerInetAddress.getCanonicalHostName();
        }
    }

    public static void setGlobalErrorHandler(GlobalErrorHandler handler) {
        errhandler = handler;
    }    

    public static void handleGlobalError(Throwable thr, String msg) {
        handleGlobalError(thr, msg, null);
    }

    static void handleGlobalError(Throwable thr, String msg, Integer exitCode) {

        if (!errhandler.handleGlobalError(thr, msg, exitCode)) {
            logger.logStack(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "received unexpected exception  ", thr);
            Throwable trace = new Throwable();
            trace.fillInStackTrace();
            logger.logStack(Logger.DEBUG,"Calling stack trace", trace);
        }
    }

    static void setPacketRouters(PacketRouter[] newrouters) {
        routers = newrouters;
    }

    public static PacketRouter getPacketRouter(int type) 
        throws IndexOutOfBoundsException
    {
        if (routers == null || type > routers.length) {
            throw new IndexOutOfBoundsException(
                getBrokerResources().getKString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                "requested invalid packet router " + type ));
        }
        return routers[type];
    }

    public static DestinationList getDestinationList() { 
        return getCoreLifecycle().getDestinationList();
    }

    public static CoreLifecycleSpi getCoreLifecycle() {
        if (coreLifecycle == null) {
            synchronized(lock) {
            if (coreLifecycle == null) {
                try {
                     coreLifecycle = new com.sun.messaging.jmq.jmsserver.core.CoreLifecycleImpl();
                     if (getConfig().getBooleanProperty(
                         IMQ+".core.plugin.coherenceMessagePattern.enabled", false)) {
                         Class c = Class.forName(
                         "com.sun.messaging.jmq.jmsserver.plugin.impl.msgpattern.MessagePatternCoreLifecycle");
                         CoreLifecycleSpi sub = (CoreLifecycleSpi)c.newInstance();
                         corePlugins.put(sub.getType(), sub);
                     }
                } catch (Exception e) {
                     getLogger().logStack(Logger.ERROR, e.getMessage(), e);
                     Broker.getBroker().exit(-1,
                         "Internal Error: Unable to init core lifecycle. Exiting",
                          BrokerEvent.Type.FATAL_ERROR);
                }
            }
            }
        }
        return coreLifecycle;
    }

    public static CoreLifecycleSpi getCorePlugin(String type) {
        return corePlugins.get(type);
    }


    /**
     * @return true if uses NoClusterManager 
     */
    public static boolean initClusterManager(MQAddress address)
        throws BrokerException {
        synchronized (lock) {
            if (clusterConfig != null) {
                return (clusterConfig instanceof NoClusterManager);
            }
            String classname = null;
            if (getJDBCHAEnabled()) {
                classname = getConfig().
                    getProperty(Globals.IMQ+".hacluster.jdbc.manager.class");
            } else if (getSFSHAEnabled()) {
                classname = getConfig().
                    getProperty(Globals.IMQ+".hacluster.bdbsfs.manager.class");
            } else {
                if (isBDBStore()) {
                    classname = getConfig().
                         getProperty(Globals.IMQ+".cluster.migratable.bdb.manager.class");
                } else if (getCoherenceServerEnabled()) {
                    classname = "com.sun.messaging.jmq.jmsserver.cluster.manager.BasicAutoClusterManagerImpl";
                }
                if (getCoherenceServerEnabled()) {
                    getConfig().put(AUTOCLUSTER_BROKERMAP_CLASS_PROP,
                        "com.sun.messaging.jmq.jmsserver.persist.coherence.AutoClusterBrokerMapImpl");
                }
            }
            boolean deft = false;
            String deftclassname = "com.sun.messaging.jmq.jmsserver.cluster.api.NoClusterManager";
            if (classname == null) {
                classname = getConfig().
                    getProperty(Globals.IMQ+".cluster.manager.class");
                deft = true;
             }
             try {
                 if (Globals.isNucleusManagedBroker()) {
                     clusterConfig = Globals.getHabitat().
                                         getService(ClusterManager.class, classname);
                     if (clusterConfig == null && deft) {
                         logger.log(logger.WARNING, "ClassNotFound: "+classname);
                         classname = deftclassname;
                         clusterConfig = Globals.getHabitat().
                                             getService(ClusterManager.class, classname);
                     }
                     if (clusterConfig == null) {
                          throw new BrokerException(
                          "Class "+classname+" not found", Status.NOT_FOUND);
                     }
                 } else {
                     Class c = null;
                     try {
                         c = Class.forName(classname);
                     } catch (ClassNotFoundException e) {
                         logger.log(logger.WARNING, e.toString());
                         if (!deft) {
                             throw e;
                         }
                         classname = deftclassname;
                         c = Class.forName(classname);
                     }
                     clusterConfig = (ClusterManager)c.newInstance();
                 }
                 clusterConfig.initialize(address);
                 mqAddress = address;
                     
                 ClusteredBroker bkr = clusterConfig.getLocalBroker();
                 brokerSession = bkr.getBrokerSessionUID();

                 return (classname.equals(deftclassname));

             } catch (Exception ex) {
                 if (ex instanceof BrokerException) {
                     throw (BrokerException)ex;
                 }
                 throw new BrokerException(
                     getBrokerResources().getKString(
                     BrokerResources.E_INITING_CLUSTER), ex);
            }
        }
    }
    public static ClusterManager getClusterManager() {
        return clusterConfig;
    }

    public static Store getStore() throws BrokerException {
	return StoreManager.getStore();
    }

    /**
     * method to release the singleton Store instance
     */
    public static void releaseStore() { 
	StoreManager.releaseStore(true); // always do clean up
    }

    public static void setMQAddress(MQAddress addr) {
        ClusterManager c = getClusterManager();
        try {
            c.setMQAddress(addr);
        } catch (Exception ex) {
            logger.logStack(logger.ERROR,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Received bad address " + addr +
                            " ignoring", ex);
            return;
        }
        mqAddress = addr;
    }
    public static MQAddress getMQAddress() {
        return mqAddress;
    }

    public static boolean nowaitForMasterBroker() {
        return getConfig().getBooleanProperty(NOWAIT_MASTERBROKER_PROP, false);
    }

    public static boolean dynamicChangeMasterBrokerEnabled() {
        return (getConfig().getBooleanProperty(
            DYNAMIC_CHANGE_MASTERBROKER_ENABLED_PROP, false) || isBDBStore());
    }

    public static boolean useMasterBroker() {
        if (getHAEnabled()) {
            return false;
        }
        if (useSharedConfigRecord()) {
            return false;
        }
        return (getClusterManager().getMasterBroker() != null);
    }
 
    public static boolean useSharedConfigRecord() {
        if (useSharedConfigRecord == null) {
            synchronized(lock) {
            if (useSharedConfigRecord == null) {

            if (getHAEnabled() && !getSFSHAEnabled()) {
                useSharedConfigRecord = Boolean.FALSE;
            } else {
                boolean nomb = getConfig().getBooleanProperty(
                               Globals.NO_MASTERBROKER_PROP, false);
                if (nomb) {
                    if (getClusterID() == null) {
                        throw new RuntimeException(
                            getBrokerResources().getKString(
                            BrokerResources.X_CID_MUST_BE_SET_NOMASTER,
                            Globals.CLUSTERID_PROPERTY,
                            Globals.NO_MASTERBROKER_PROP+"=true"));
                    }
                    useSharedConfigRecord = Boolean.TRUE;
                } else {
                    useSharedConfigRecord = Boolean.FALSE;
                }
            }
            }
            }
        }
        return useSharedConfigRecord.booleanValue();
    }

    public static boolean isConfigForCluster() {
        return (getHAEnabled() || 
                getConfig().getProperty(AUTOCONNECT_CLUSTER_PROPERTY) != null ||
                getConfig().getProperty(MANUAL_AUTOCONNECT_CLUSTER_PROPERTY) != null); 
    }

    /*---------------------------------------------
     *          global static variables
     *---------------------------------------------*/

    public final static String
        KEYSTORE_USE_PASSFILE_PROP = Globals.IMQ + ".passfile.enabled",
        KEYSTORE_PASSDIR_PROP      = Globals.IMQ + ".passfile.dirpath",
        KEYSTORE_PASSFILE_PROP     = Globals.IMQ + ".passfile.name";

    /**
     * If this property is set to true then the broker will read properties (including passwords) from standard input
     */
    public static final String READ_PROPERTIES_FROM_STDIN = Globals.IMQ + ".readstdin.enabled";

     //--------------------------------------------------------------
     // HA property names
     //--------------------------------------------------------------
    /**
     * The property name to retrieve this brokers id.
     */
    public static final String BROKERID_PROPERTY =
        Globals.IMQ + ".brokerid";

    /**
     * The property name to retrieve this brokers id.
     */
    public static final String JDBCBROKERID_PROPERTY =
        Globals.IMQ + ".persist.jdbc.brokerid";

    /**
     * The property name to retrieve the cluster's id.
     */
    public static final String CLUSTERID_PROPERTY =
        Globals.IMQ + ".cluster.clusterid";

    /**
     * The property name to retrieve if HA is enabled.
     */
    public static final String HA_ENABLED_PROPERTY =
         Globals.IMQ + ".cluster.ha";

    public static final boolean HA_ENABLED_DEFAULT = false;

    /**
     * The property name for this broker's primary owner name.
     * This defaults to the value of the system property user.name.
     *
     * Brokers can be run for different applications, for different
     * projects, this property helps identify the person/owner
     * of a particular broker. This is currently only used by
     * JESMF.
     */
    public static final String PRIMARY_OWNER_NAME_PROPERTY =
        Globals.IMQ + ".primaryowner.name";

    /**
     * The property name for this broker's primary owner contact info.
     * This defaults to the value of the system property user.name.
     *
     * Brokers can be run for different applications, for different
     * projects, this property helps identify the person/owner's
     * contact info of a particular broker. This is currently only 
     * used by JESMF.
     */
    public static final String PRIMARY_OWNER_CONTACT_PROPERTY =
        Globals.IMQ + ".primaryowner.contact";

    /**
     * Property base name for the admin defined roles for the broker.
     * Example setting of this:
     *
     *  imq.broker.adminDefinedRoles.count=2
     *  imq.broker.adminDefinedRoles.name0=JMS provider for domain1 appserver instance on host myhost
     *  imq.broker.adminDefinedRoles.name1=Used by test harness running on host myhost
     *
     * This is currently used by JESMF.
     */
    public static final String BROKER_ADMIN_DEFINED_ROLES_PROPERTY_BASE =
        Globals.IMQ + ".broker.adminDefinedRoles";

    /**
     * Property name for the install root for MQ.
     *
     * This is currently only used by JESMF.
     */
    public static final String INSTALL_ROOT =
        Globals.IMQ + ".install.root";

    public static final String NOWAIT_MASTERBROKER_PROP =
        IMQ + ".cluster.nowaitForMasterBroker";

    public static final String DYNAMIC_CHANGE_MASTERBROKER_ENABLED_PROP =
        IMQ + ".cluster.dynamicChangeMasterBrokerEnabled";

    public static final String NO_MASTERBROKER_PROP =
        IMQ + ".cluster.nomasterbroker";

    public static final String AUTOCONNECT_CLUSTER_PROPERTY =
        IMQ + ".cluster.brokerlist";

    public static final String MANUAL_AUTOCONNECT_CLUSTER_PROPERTY =
        IMQ + ".cluster.brokerlist.manual";

    public static final String AUTOCLUSTER_BROKERMAP_CLASS_PROP =
        IMQ + ".cluster.autocluster.brokermapclass";

    public static final String PUSERVICE_ENABLED_PROP =
        IMQ + ".portunif.enabled";

    public static boolean txnLogEnabled() {
        return StoreManager.txnLogEnabled();
    }
    
    public static boolean isNewTxnLogEnabled() {
        return StoreManager.newTxnLogEnabled();
    }

    // whether non-transacted persistent message sent should be logged
    private static volatile Boolean _logNonTransactedMsgSend = null;

    public static final String LOG_NONTRANSACTEDMSGSEND_PROP =
        Globals.IMQ + ".persist.file.txnLog.nonTransactedMsgSend.enabled";

    public static boolean logNonTransactedMsgSend() {

        if (_logNonTransactedMsgSend == null) {
            synchronized(lock) {
            if (_logNonTransactedMsgSend == null) {

            _logNonTransactedMsgSend = Boolean.valueOf(
                txnLogEnabled() &&
                getConfig().getBooleanProperty(LOG_NONTRANSACTEDMSGSEND_PROP));
            }
            }
        }
        return _logNonTransactedMsgSend.booleanValue();
    }
    
   
    
    
    /**
     * whether to use minimum write optimisations
     */
    private static volatile Boolean _minimizeWritesFileStore = null;
    private static volatile Boolean _minimizePersist = null;
    private static volatile Boolean _minimizePersistLevel2 = null;

    public static final String MINIMIZE_WRITES_FILESTORE_PROP =
        Globals.IMQ + ".persist.file.minimizeWrites";

    public static final String MINIMIZE_PERSIST_PROP =
        Globals.IMQ + ".persist.minimizeWrites";

    public static final String MINIMIZE_PERSIST_LEVEL2_PROP =
        Globals.IMQ + ".persist.minimizeWritesLevel2";

    public static boolean isMinimumWritesFileStore() {
        if (_minimizeWritesFileStore == null) {
        synchronized(lock) {
            if (_minimizeWritesFileStore == null) {
                _minimizeWritesFileStore = Boolean.valueOf(getConfig().
                    getBooleanProperty(MINIMIZE_WRITES_FILESTORE_PROP, false));
                getLogger().log(Logger.INFO, MINIMIZE_WRITES_FILESTORE_PROP+
                                "="+_minimizeWritesFileStore);
            }
        }
        }
        return _minimizeWritesFileStore.booleanValue();
    }

    public static boolean isMinimumPersist() {
        if (_minimizePersist == null) {
        synchronized(lock) {
            if (_minimizePersist == null) {
                _minimizePersist = Boolean.valueOf(
                    getConfig().getBooleanProperty(MINIMIZE_PERSIST_PROP, true));
                getLogger().log(Logger.INFO, MINIMIZE_PERSIST_PROP+"="+_minimizePersist);
            }
        }
        }
        if (isFileStore() && isMinimumWritesFileStore()) {
            return true;
        }
        return _minimizePersist.booleanValue();
    }

    public static boolean isMinimumPersistLevel2() {
        if (_minimizePersistLevel2 == null) {
        synchronized(lock) {
            if (_minimizePersistLevel2 == null) {
                _minimizePersistLevel2 = Boolean.valueOf(
                    getConfig().getBooleanProperty(MINIMIZE_PERSIST_LEVEL2_PROP, true));
                if ((isNewTxnLogEnabled() ||  //for self doc
                     (!isBDBStore() && !isJDBCStore())) &&
                    _minimizePersistLevel2.booleanValue()) {
                    getLogger().log(Logger.INFO, Globals.getBrokerResources().getKString(
                        BrokerResources.W_IGNORE_PROP_SETTING, 
                        MINIMIZE_PERSIST_LEVEL2_PROP+"="+_minimizePersistLevel2));
                    _minimizePersistLevel2 = Boolean.valueOf(false);
                } else {
                    getLogger().log(Logger.INFO, MINIMIZE_PERSIST_LEVEL2_PROP+"="+_minimizePersistLevel2);
                }
            }
        }
        }
        return _minimizePersistLevel2.booleanValue();
    }


    /**
     * whether delivery data is persisted
     */
    private static volatile Boolean _deliveryStateNotPersisted = null;

    public static final String DELIVERY_STATE_NOT_PERSITED_PROP =
        Globals.IMQ + ".persist.file.deliveryStateNotPersisted";

    public static boolean isDeliveryStateNotPersisted() {

        if (_deliveryStateNotPersisted == null) {
            synchronized(lock) {
                if (_deliveryStateNotPersisted == null) {
        	    _deliveryStateNotPersisted = Boolean.valueOf(
                        getConfig().getBooleanProperty(
                            DELIVERY_STATE_NOT_PERSITED_PROP));
                }
            }
        }
        return _deliveryStateNotPersisted.booleanValue();
    }
    
    /**
     * Return whether properties (including passwords) should be read from stdin at broker startup
     * @return
     */
    public static boolean isReadPropertiessFromStdin(){
    	return getConfig().getBooleanProperty(READ_PROPERTIES_FROM_STDIN);
    }

    public static void setAPIDirectTwoThreadSyncReplies(boolean v) {
        apiDirectTwoThreadSyncReplies = v;
    }

    public static boolean getAPIDirectTwoThreadSyncReplies() {
        return apiDirectTwoThreadSyncReplies;
    }

    static final String USE_FILELOCK_FOR_LOCKFILE_PROP =
        Globals.IMQ+".useFileLockForLockFile.enabled";

    private static volatile Boolean useFileLockForLockFile = null;

    public static boolean getUseFileLockForLockFile() {
        if (useFileLockForLockFile == null) {
        synchronized(lock) {
            if (useFileLockForLockFile == null) {
                useFileLockForLockFile = Boolean.valueOf(
                    getConfig().getBooleanProperty(USE_FILELOCK_FOR_LOCKFILE_PROP, true));
            }
        }
        }
        return useFileLockForLockFile.booleanValue();
    }

    
    private static final String SECURITY_POODLE_FIX_PROP = IMQ+".poodleFix.enabled";
    private static final String SECURITY_POODLE_FIX_HTTPS_PROP = IMQ+".protocol.https.poodleFix.enabled";

    public static boolean getPoodleFixEnabled() {
        return getConfig().getBooleanProperty(SECURITY_POODLE_FIX_PROP, true);
    }

    public static boolean getPoodleFixHTTPSEnabled() {
        boolean b = getConfig().getBooleanProperty(SECURITY_POODLE_FIX_PROP, true);
        return getConfig().getBooleanProperty(SECURITY_POODLE_FIX_HTTPS_PROP, b);
    }

    /**
     * see http://www.oracle.com/technetwork/java/javase/documentation/cve-2014-3566-2342133.html
     */
    public static void applyPoodleFix(Object socket,  String caller) {
        javax.net.ssl.SSLServerSocket ssock = null;
        javax.net.ssl.SSLSocket sock = null;
        if (socket instanceof javax.net.ssl.SSLServerSocket) {
            ssock = (javax.net.ssl.SSLServerSocket)socket;
        } else {
            sock = (javax.net.ssl.SSLSocket)socket;
        }
        String[] protocols;
        if (ssock != null) {
            protocols = ssock.getEnabledProtocols();
        } else  {
            protocols = sock.getEnabledProtocols();
        }
        String orig = Arrays.toString(protocols);

        Set<String> set = new LinkedHashSet<String>();
        for (String s : protocols) {
             if (s.equals("SSLv3")) {
                continue;
             } 
             if (ssock == null) {
                 if (s.equals("SSLv2Hello")) {
                     continue;
                 }
            }
            set.add(s);
        }
        getLogger().log(Logger.INFO, "["+caller+"]: ["+orig+"], setEnabledProtocols["+set+"]");
        if (ssock != null) {
            ssock.setEnabledProtocols(set.toArray(new String[set.size()]));
        } else {
            sock.setEnabledProtocols(set.toArray(new String[set.size()]));
        }
        return;
    }

    public static String[] getKnownSSLEnabledProtocols(String caller) {
        return KeystoreUtil.getKnownSSLEnabledProtocols(caller);
    }
}

