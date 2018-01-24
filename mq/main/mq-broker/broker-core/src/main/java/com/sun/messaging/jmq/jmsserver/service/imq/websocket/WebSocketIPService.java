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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Properties;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport; 
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.websockets.WebSocketAddOn;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.pool.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.Operation;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQIPServiceFactory;
import com.sun.messaging.jmq.jmsserver.service.imq.NotificationInfo;
import com.sun.messaging.jmq.jmsserver.service.imq.grizzly.GrizzlyService;
import com.sun.messaging.jmq.jmsserver.service.imq.grizzly.GrizzlyIPService;


/**
 * @author amyk
 */
public class WebSocketIPService extends IMQService
implements GrizzlyService, NotificationInfo
{
    private static boolean DEBUG = (false || Globals.getLogger().getLevel() <= Logger.DEBUG); 

    private BrokerResources br = Globals.getBrokerResources();

    private static final String ALLOWED_ORIGINS_PROP_SUFFIX =".allowedOrigins";
    private static final String ALL_ORIGIN = "*";

    protected PacketRouter router = null;
    private WebSocketProtocolImpl protocol = null;
    private HttpServer httpServer = null;
    private GrizzlyExecutorService writerPool = null;

    private Object writeLock = new Object();
    private LinkedHashMap<ConnectionUID, WebSocketMQIPConnection> pendingWrites = 
                         new LinkedHashMap<ConnectionUID, WebSocketMQIPConnection>();

    private boolean dedicatedWriter = Globals.getConfig().getBooleanProperty(
                        "imq.websocketIPService.dedicatedWriterThread", false);
    private AtomicInteger readerPoolThreadCnt= new AtomicInteger(0);
    private AtomicInteger writerPoolThreadCnt= new AtomicInteger(0);
    private NetworkListener networkListener = null;

    private List<String> enabledSubServices = new ArrayList<String>();
    private List<URL> allowedOrigins = null;

    private URL myurl = null;

    public WebSocketIPService(String name, int type, PacketRouter router,
                              int min, int max, WebSocketIPServiceFactory parent)
                              throws BrokerException {
        super(name, type);
        this.router = router;

        String key =  WebSocketIPServiceFactory.SERVICE_PREFIX+
                          name+ALLOWED_ORIGINS_PROP_SUFFIX;
        logger.log(logger.INFO, key+"="+Globals.getConfig().getProperty(key));
        List<String> origins =  Globals.getConfig().getList(key);
        if (origins != null && !origins.contains(ALL_ORIGIN)) {
            allowedOrigins = new ArrayList<URL>();

            Iterator<String> itr = origins.iterator();
            while (itr.hasNext()) {
                try {
                    allowedOrigins.add(new URL(itr.next()));
                } catch (Exception e) {
                    throw new BrokerException(e.getMessage(), e);
                }
            }
        }

        key =  WebSocketIPServiceFactory.SERVICE_PREFIX +name+".services";
        List subservs =  Globals.getConfig().getList(key);
        if (subservs == null || subservs.isEmpty()) {
            throw new BrokerException(br.getKString(br.X_PROPERTY_NOT_SPECIFIED, key));
        }
        Iterator itr = subservs.iterator();
        String val = null, subserv = null;
        while (itr.hasNext()) {
            val = (String)itr.next();
            subserv = val.trim().toLowerCase(Locale.getDefault());
            if (MQWebSocketServiceApp.isSupportedSubService(subserv)) {
                enabledSubServices.add(subserv);
            } else {
                String[] args = { val, key, "UNKNOWN" };
                String emsg = br.getKString(br.W_IGNORE_VALUE_IN_PROPERTY_LIST, args);
                logger.log(logger.WARNING, emsg);
            }
        }
        if (enabledSubServices.isEmpty()) {
            val = Globals.getConfig().getProperty(key);
            throw new BrokerException(
                br.getKString(br.X_BAD_PROPERTY_VALUE, key+"="+val));
        }

        key =  WebSocketIPServiceFactory.SERVICE_PREFIX +name+".protocoltype";
        String p =  Globals.getConfig().getProperty(key);
        if (p == null || (!p.equals("ws") && !p.equals("wss"))) { 
            throw new BrokerException(
                br.getKString(br.X_PROTOCOLTYPE_NO_SUPPORT, 
                              (p == null ? "null":p), name));
        }
        String prefix = IMQIPServiceFactory.PROTOCOL_PREFIX + p;
        String serviceprefix = WebSocketIPServiceFactory.SERVICE_PREFIX+name +"."+p;

        try {

             String docroot = Globals.getJMQ_INSTANCES_HOME() + File.separator
                  +Globals.getConfigName()+File.separator+ "docroot" + File.separator;
            File dir = new File(docroot);
            if (!dir.exists()) { //XXclean
                if (!dir.mkdir()) {
                    logger.log(logger.WARNING, "Unable to make directory "+docroot);
                }
            }
     
            Map params = parent.getProtocolParams(p, serviceprefix);
            params.put("serviceFactoryHandlerName", parent.getFactoryHandlerName());
            protocol = new WebSocketProtocolImpl(this, p);
            protocol.checkParameters(params);
            protocol.setParameters(params);
            protocol.setMinMaxThreads(min, max, getName());

            boolean nodelay = Globals.getConfig().getBooleanProperty(prefix + ".nodelay", true);
            protocol.setNoDelay(nodelay);

            //XXX
            int inputBufferSize = Globals.getConfig().getIntProperty(prefix+ ".inbufsz", 0);
            int outputBufferSize = Globals.getConfig().getIntProperty(prefix + ".outbufsz", 0);
            protocol.setInputBufferSize(inputBufferSize);
            protocol.setOutputBufferSize(outputBufferSize);

            SSLEngineConfigurator wssServerConfig = null;
            if (p.equals("wss")) {
                wssServerConfig = GrizzlyIPService.initializeSSL(getName(), protocol);
            }

            String pname = "MQ-writer-thread-pool["+getName()+"]";
            final ThreadPoolConfig wpc = ThreadPoolConfig.defaultConfig().copy().
                                         setPoolName(pname).
                                         setCorePoolSize(protocol.getMinThreads()).
                                         setMaxPoolSize(protocol.getMaxThreads());
            wpc.getInitialMonitoringConfig().addProbes(
                new ThreadPoolProbeImpl(pname, writerPoolThreadCnt));
            writerPool = GrizzlyExecutorService.createInstance(wpc);

            pname = "MQ-reader-thread-pool["+getName()+"]";
            final ThreadPoolConfig rpc = ThreadPoolConfig.defaultConfig().copy().
                                         setPoolName(pname).
                                         setCorePoolSize(protocol.getMinThreads()).
                                         setMaxPoolSize(protocol.getMaxThreads());
            rpc.getInitialMonitoringConfig().addProbes(
                new ThreadPoolProbeImpl(pname, readerPoolThreadCnt));
            GrizzlyExecutorService pool = GrizzlyExecutorService.createInstance(rpc);

            PortRange prange = protocol.getPortRange();
            String hostn = protocol.getHostName();
            if (hostn == null) {
                hostn = NetworkListener.DEFAULT_NETWORK_HOST;
            }

            httpServer = new HttpServer();
	    final ServerConfiguration cf = httpServer.getServerConfiguration();
            cf.setName("MQWebSocketService["+name+"]");
            cf.setMaxFormPostSize(4096);
            cf.setMaxBufferedPostSize(4096);
            StaticHttpHandler hh = new StaticHttpHandler(docroot);
            hh.setFileCacheEnabled(false);
            cf.addHttpHandler(hh, "/");
            networkListener = new NetworkListener("MQWebSocketService["+name+"]", hostn, prange);
            httpServer.addListener(networkListener);

            TCPNIOTransport transport = configureTransport();

            if (wssServerConfig != null) {
                networkListener.setSecure(true); 
                networkListener.setSSLEngineConfig(wssServerConfig);
            }

            transport.setWorkerThreadPool(pool);

            final WebSocketAddOn addon = new WebSocketAddOn();
            networkListener.registerAddOn(addon);
            final WebSocketApplication app = new MQWebSocketServiceApp(this);
            WebSocketEngine.getEngine().register(app);

        } catch (Exception e) {
            String emsg = br.getKString(br.X_INIT_TRANSPORT_FOR_SERVICE, name, e.getMessage());
            logger.logStack(logger.ERROR, emsg, e);
            if (httpServer != null) {
                try {
                    httpServer.stop();
                } catch (Exception ee) {}
                    httpServer = null;
            }
            if (writerPool != null) {
                writerPool.shutdown();
            }
            throw new BrokerException(emsg);
        }
    }

    protected List<URL> getAllowedOrigins() {
        return allowedOrigins;
    }

    protected synchronized URL getMyURL() throws Exception {
        if (myurl != null) {
            if (myurl.getPort() != networkListener.getPort()) {
                myurl = new URL((networkListener.isSecure() ? "https":"http")+"://"+
                        myurl.getHost()+":"+networkListener.getPort());
            }
        } else {
            myurl = new URL((networkListener.isSecure() ? "https":"http")+"://"+
                        Globals.getBrokerInetAddress().getCanonicalHostName()+
                        ":"+networkListener.getPort());
        }
        return myurl;
    }

    protected boolean isSubServiceEnabled(String subserv) {
        return enabledSubServices.contains(subserv);
    }

    protected boolean useDedicatedWriter() {
        return dedicatedWriter;
    }

    private void unbindTransport() throws Exception {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.unbindTransport() for service "+name);
        }
        networkListener.getTransport().unbindAll();
        logger.log(Logger.INFO, br.getKString(
            br.I_UNBOUND_TRANSPORT_FOR_SERVICE, 
            getName()+"["+protocol.getType()+"]"));
    }

    private TCPNIOTransport configureTransport() {

        TCPNIOTransport transport = networkListener.getTransport();

        transport.setReuseAddress(true);
	transport.setTcpNoDelay(protocol.getNoDelay());

	int v = protocol.getTimeout();
	if (v > 0) {
            transport.setServerSocketSoTimeout(v*1000);
	}

        v = protocol.getLingerTimeout();
	if (v > 0) {
            transport.setLinger(v*1000);
	}

	int backlog = protocol.getBacklog();
        transport.setServerConnectionBackLog(backlog);

        transport.setReadBufferSize(protocol.getInputBufferSize());
        transport.setWriteBufferSize(protocol.getOutputBufferSize());
        return transport;
    }

    private void bindTransport(boolean checkpause) throws Exception {
	if (DEBUG) {
            logger.log(Logger.INFO,
            "WebSocketIPService.bindTransport("+checkpause+") for service "+name);
	}
        unbindTransport();

        TCPNIOTransport transport = configureTransport();

        String hostn = protocol.getHostName();
	int portn = protocol.getPort();

	logger.log(Logger.INFO, br.getKString(br.I_BINDING_TRANSPORT_FOR_SERVICE,
            getName()+"["+protocol.getType()+", "+(hostn == null ? "*":hostn)+", "+portn+"]"));
	if (getState() == ServiceState.PAUSED) {
            transport.resume(); //Grizzly: need resume to bind
	}
        if (hostn == null) {
            transport.bind(new InetSocketAddress(portn));
	} else {
            transport.bind(hostn, portn);
            addServiceProp("hostname", hostn);
	}
        int lportn = getLocalPort();
	logger.log(Logger.INFO, br.getKString(br.I_BIND_TRANSPORT_FOR_SERVICE,
            getName()+"["+protocol.getType()+"]", (hostn == null ? "":hostn)+":"+lportn+"("+portn+")"));

	if (checkpause &&
            (getState() == ServiceState.PAUSED ||
             getState() == ServiceState.QUIESCED)) {
            try {
                unbindTransport();
            } finally {
		if (getState() == ServiceState.PAUSED) {
                    networkListener.pause();
                }
            }
        }
        Globals.getPortMapper().addService(getName(), protocol.getType(),
            Globals.getConfig().getProperty(
        	WebSocketIPServiceFactory.SERVICE_PREFIX+getName()+".servicetype"),
        	lportn, getServiceProperties());
    }   


    public int getLocalPort() {
       int port = networkListener.getPort();
       return (port < 0 ? 0:port);
    }

    public boolean isOpen() {
        return httpServer != null;
    }

    @Override
    public synchronized int getMinThreadpool() {
        return protocol.getMinThreads()*2;
    }

    @Override
    public synchronized int getMaxThreadpool() {
        return protocol.getMaxThreads()*2;
    }

    @Override
    public synchronized int getActiveThreadpool() {
        int cntr = readerPoolThreadCnt.get();
        int cntw = writerPoolThreadCnt.get();
        return cntr+cntw;
    }

    /**
     * Should only be called after protocol.setMinMaxThreads()
     */
    @Override
    public synchronized int[] setMinMaxThreadpool(int min, int max) {
        if (writerPool != null) {
            ThreadPoolConfig pc = writerPool.getConfiguration();
            pc.setMaxPoolSize(max);
            pc.setCorePoolSize(min);
            synchronized(writeLock) {
               if (writerPool.isShutdown()) {
                   throw new IllegalStateException(
                   "Service "+getName()+" is shutting down");
               }
            }
            writerPool.reconfigure(pc);
        }
        TCPNIOTransport transport = networkListener.getTransport();
        ThreadPoolConfig pc = transport.getWorkerThreadPoolConfig();
        pc.setMaxPoolSize(max);
        pc.setCorePoolSize(min);
        ((GrizzlyExecutorService)transport.getWorkerThreadPool()).reconfigure(pc);
        return null;
    }

    @Override
    public Protocol getProtocol() {
        return protocol;

    }

    public synchronized void startService(boolean startPaused) {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.startService("+startPaused+") for service "+getName());
        }
        if (isServiceRunning()) {
            logger.log(Logger.DEBUG, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "unable to start service, already started.");
            return;
        }
        setState(ServiceState.STARTED);
        try {       
            configureTransport();

            if (startPaused) {
                networkListener.pause();
            } else if (getState() == ServiceState.PAUSED) {
                networkListener.resume();
            }
            httpServer.start();
            if (startPaused) { 
                networkListener.pause();
            }
            String args[] = { getName(), 
                              getProtocol().toString(), 
                              String.valueOf(getMinThreadpool()),
                              String.valueOf(getMaxThreadpool()) };
            String msg = br.getKString(BrokerResources.I_SERVICE_START, args);
            logger.log(Logger.INFO, msg+"[Grizzly "+Grizzly.getDotedVersion()+"]");
            try {
            logger.log(Logger.INFO, BrokerResources.I_SERVICE_USER_REPOSITORY,
                       AccessController.getInstance(
                       getName(), getServiceType()).getUserRepository(),
                       getName());
            } catch (BrokerException e) {
            logger.log(Logger.WARNING, 
                       BrokerResources.W_SERVICE_USER_REPOSITORY,
                       getName(), e.getMessage());
            }
            Globals.getPortMapper().addService(name, protocol.getType(), 
                Globals.getConfig().getProperty(
                IMQIPServiceFactory.SERVICE_PREFIX +
                name + ".servicetype"), getLocalPort(), getServiceProperties());
            if (startPaused) {
                setServiceRunning(false);
                setState(ServiceState.PAUSED);
            } else {
                setServiceRunning(true);
                setState(ServiceState.RUNNING);
            }
        } catch (Exception e) {
            String emsg = br.getKString(br.X_START_SERVICE_EXCEPTION,
                                        name, e.getMessage());
            logger.logStack(Logger.ERROR, emsg, e);
            try {
                stopService(true);
            } catch (Exception ee) {
                logger.logStack(Logger.WARNING, 
                    br.getKString(br.W_CANT_STOP_SERVICE, name+"["+getProtocol()+"]"), ee);
                setServiceRunning(false);
            }
        }
    }

    @Override
    public void stopService(boolean all) { 
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.stopService("+all+") for service "+getName());
        }
        synchronized (this) {

            if (isShuttingDown()) {
                // we have already been called
                return;
            }

            String strings[] = { getName(), getProtocol().toString() };
            if (all) {
                logger.log(Logger.INFO, BrokerResources.I_SERVICE_STOP, strings);
            } else if (!isShuttingDown()) {
                logger.log(Logger.INFO, BrokerResources.I_SERVICE_SHUTTINGDOWN, strings);
            } 
           
            setShuttingDown(true);
        }
        try {
            networkListener.getTransport().unbindAll();
        } catch (Exception e) {
            logger.logStack(Logger.WARNING, 
            br.getKString(br.W_CANT_STOP_SERVICE, name+"["+getProtocol()+"]"), e);
        }

        if (this.getServiceType() == ServiceType.NORMAL) {
            List cons = connectionList.getConnectionList(this);
            Connection con = null;
            for (int i = cons.size()-1; i >= 0; i--) {
                con = (Connection)cons.get(i);
                con.stopConnection();
            }
        }

        synchronized (this) {
            setState(ServiceState.SHUTTINGDOWN);
        }

        if (!all) {
            return;
        }

        // set operation state to EXITING
        if (this.getServiceType() == ServiceType.NORMAL) {
            List cons = connectionList.getConnectionList(this);
            Connection con = null;
            for (int i = cons.size()-1; i >= 0; i--) {
                con = (Connection)cons.get(i);
                con.destroyConnection(true, GoodbyeReason.SHUTDOWN_BKR, 
                    Globals.getBrokerResources().getKString(
                        BrokerResources.M_SERVICE_SHUTDOWN));
            }
        }
        try {
            httpServer.stop();
        } catch (Exception e)  {
            logger.logStack(Logger.WARNING, 
            br.getKString(br.W_CANT_STOP_SERVICE, name+"["+getProtocol()+"]"), e);
        }

        synchronized (this) {
            setState(ServiceState.STOPPED);
        }
        if (writerPool != null) {
            synchronized(writeLock) {
                writerPool.shutdown();
            }
            long endtime = System.currentTimeMillis()+getDestroyWaitTime();
            synchronized(writeLock) {
                while (pendingWrites.size() > 0) {
                    try {
                        writeLock.wait(getDestroyWaitTime());
                    } catch (InterruptedException e) {}
                    if (System.currentTimeMillis() >= endtime) {
                        break;
                    }
                }
            }
            try {
                long remaining = endtime - System.currentTimeMillis();
                if (remaining > 0L) {
                    writerPool.awaitTermination(remaining,
                               TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) { 
                logger.logStack(Logger.INFO, 
                "Exception in waiting writer thread pool terminate on stopping service "+getName(), e);
            }
        }

        if (DEBUG) {
            logger.log(Logger.INFO, 
            "Stopped Service {0} with protocol {1} ", getName(), getProtocol());
        }
    }

    @Override
    public synchronized void stopNewConnections() 
    throws IOException, IllegalStateException {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.stopNewConnections() for service "+getName());
        }

        if (getState() != ServiceState.RUNNING) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_STOP_SERVICE));
        }
        try {
            unbindTransport();
        } catch (Exception e) {
            throw new IOException("Unable to unbind transport for service "+name, e);
        }
        setState(ServiceState.QUIESCED);
        Globals.getPortMapper().updateServicePort(name, 0);
    }

    @Override
    public synchronized void startNewConnections() 
    throws IOException {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.startNewConnections() for service "+getName());
        }

        if (getState() != ServiceState.QUIESCED && getState() != ServiceState.PAUSED) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_START_SERVICE));
        }
        try {
            bindTransport(false); 
        } catch (Exception e) {
            throw new IOException("Unable to bind transport for service "+name, e);
        }
        setState(ServiceState.RUNNING);
        Globals.getPortMapper().updateServicePort(name, getProtocol().getLocalPort());
    }

    @Override
    public synchronized void pauseService(boolean all) {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.pauseService("+all+") for service "+getName());
        }

        if (!isServiceRunning()) {
            logger.log(Logger.DEBUG, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "unable to pause service " + name + ", not running.");
            return;
        }  

        String strings[] = { getName(), getProtocol().toString() };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_PAUSE, strings);

        try {
            stopNewConnections();
            networkListener.pause();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, Globals.getBrokerResources().
                getKString(BrokerResources.X_PAUSE_SERVICE, 
                    getName()+"["+getProtocol()+"]", ex.getMessage()), ex);
        }
        setState(ServiceState.PAUSED);
        setServiceRunning(false);
    }

    @Override
    public synchronized void resumeService() {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "WebSocketIPService.resumeService() for service "+getName());
        }
        if (isServiceRunning()) {
             logger.log(Logger.DEBUG, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                        "unable to resume service " + name + ", already running.");
           return;
        }
        String strings[] = { getName(), getProtocol().toString() };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_RESUME, strings);
        try {
            startNewConnections();
            networkListener.resume();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, Globals.getBrokerResources().
                getKString(BrokerResources.X_RESUME_SERVICE, 
                    getName()+"["+getProtocol()+"]", ex.getMessage()), ex);
        }
        setServiceRunning(true);
        setState(ServiceState.RUNNING);
    }

    /**
     * @param min if -1 no change 
     * @param max if -1 no change 
     */
    @Override
    public synchronized void updateService(int port, int min, int max) 
    throws IOException, PropertyUpdateException, BrokerException {
        updateService(port, min, max, true);
    }

    protected synchronized void updateService(int port, int min, int max, boolean store) 
    throws IOException, PropertyUpdateException, BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, 
            "WebSocketIPService.updateService("+port+", "+min+", "+max+", "+store+
            ") for service "+getName());
        }
        String args[] = { getName(),
                          String.valueOf(port),
                          String.valueOf(min),
                          String.valueOf(max)};
	logger.log(Logger.INFO, BrokerResources.I_UPDATE_SERVICE_REQ, args);

        try {
            if (min > -1 || max > -1) {
                int[] rets = protocol.setMinMaxThreads(min, max, getName());
                this.setMinMaxThreadpool(protocol.getMinThreads(), 
                                         protocol.getMaxThreads());
                if (store) {
                    if (rets != null) {
                        if (rets[0] > -1) {
                            Globals.getConfig().updateProperty(
                            IMQIPServiceFactory.SERVICE_PREFIX + name +
                            ".min_threads", String.valueOf(2*protocol.getMinThreads()));
                        }
                        if (rets[1] > -1) {
                            Globals.getConfig().updateProperty(
                            IMQIPServiceFactory.SERVICE_PREFIX + name +
                            ".max_threads", String.valueOf(2*protocol.getMaxThreads()));
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.X_THREADPOOL_BAD_SET,
                String.valueOf(min), String.valueOf(max))+": "+e.getMessage();
            logger.logStack(logger.ERROR, emsg, e);
            throw new BrokerException(emsg, e);
        }

       	if (port > -1) {
            boolean dostore = store;
            Map params = new HashMap();
            params.put("port", String.valueOf(port));
            protocol.checkParameters(params);
            Map oldparams = protocol.setParameters(params);
            if (oldparams != null) {
                Globals.getPortMapper().removeService(name);
                try {
                    bindTransport(true);
                } catch (Exception e) {
                    dostore = false;
                    String emsg = br.getKString(br.X_BIND_TRANSPORT_FOR_SERVICE, getName(), e.getMessage());
                    logger.logStack(Logger.ERROR, emsg, e);
                    protocol.setParameters(oldparams);
                    Globals.getPortMapper().removeService(name);
                    try {
                        bindTransport(true);
                    } catch (Exception ee) {
                        emsg = br.getKString(br.X_BIND_TRANSPORT_FOR_SERVICE, getName(), e.getMessage());
                        logger.logStack(Logger.ERROR, emsg, ee);
                        throw new BrokerException(emsg, ee); 
                    }
                }
            }
            if (dostore) {
                Globals.getConfig().updateProperty(IMQIPServiceFactory.SERVICE_PREFIX +
                    name + "." +protocol.getType()+".port", String.valueOf(port));
            }
	}
    }

    public WebSocketMQIPConnection createConnection(MQWebSocket websocket)
    throws IOException, BrokerException {
        return new WebSocketMQIPConnection(this, router, websocket);
    }

    /**************************************
     * Implement NotificationInfo interface
     **********************************************/
    public void setReadyToWrite(IMQConnection con, boolean ready) {
        setReadyToWrite(con, ready, null);
    }

    private void setReadyToWrite(IMQConnection con, 
        boolean ready, Exception exception) {
        if (dedicatedWriter) {
            return;
        }

        Map.Entry<ConnectionUID, WebSocketMQIPConnection> entry = null;
        WebSocketMQIPConnection pc = (WebSocketMQIPConnection)con;

        boolean submit = false;
        synchronized(writeLock) {
            pendingWrites.put(pc.getConnectionUID(), pc);
            if (pc.assignWriteThread(true)) {
                pendingWrites.remove(pc.getConnectionUID());
                submit = true;
                writeLock.notifyAll();
            }
        }
        if (!submit) {
            return;
        }

        final WebSocketMQIPConnection c = pc;
        try {
            writerPool.execute(new Runnable() {
            public void run() {
                int ret = Operation.PROCESS_PACKETS_REMAINING;
                try {
                    ret = c.writeData(false);
                } catch (Throwable t) {
                    try {
                         c.handleWriteException(t);
                    } catch (Throwable e) {
                        int loglevel = (c.isValid() ? Logger.WARNING:Logger.INFO);
                        if (c.getDEBUG() || loglevel == logger.WARNING) {
                            logger.logStack(loglevel,
                                "Exception in writing data to connection "+
                                c+" for service "+getName(), e);
                        }
                    }
                } finally {
                    c.assignWriteThread(false);
                    boolean resubmit = false;
                    switch(ret) {
                        case Operation.PROCESS_PACKETS_REMAINING:
                             resubmit = true;
                        break;
                        case Operation.PROCESS_PACKETS_COMPLETE:
                             break;
                        case Operation.PROCESS_WRITE_INCOMPLETE:
                        break;
                    }
                    
                    synchronized(writeLock) {
                        if (pendingWrites.get(c.getConnectionUID()) != null) {
                            resubmit = true;
                        }
                    }
                    if (resubmit) {
                        setReadyToWrite(c, true);
                    }
                }
            } 
            });

        } catch (Exception e) {
            c.assignWriteThread(false);
            if (exception != null) {
                throw new RuntimeException(e.getMessage(), exception);
            }
            if (!isShuttingDown()) { 
                setReadyToWrite(c, true, e);
            }
        }
    }

    public void assigned(IMQConnection con, int events) 
    throws IllegalAccessException {
        throw new UnsupportedOperationException(
        "Unsupported call WebSocketIPServer.assigned()");
    }

    public void released(IMQConnection con, int events) {
        throw new UnsupportedOperationException(
        "Unsupported call WebSocketIPServer.assigned()");
    }

    public void destroy(String reason) {
    }

    public void dumpState() {
        logger.log(Logger.INFO, getStateInfo());
    }

    public String getStateInfo() {
        synchronized (writeLock) {
            return 
            "WebSocketIPService["+getName()+"]"+
            "pendingWriteCount: " + pendingWrites.size()+"\n"+
            "WebSocketIPService["+getName()+"]"+
            "writerPoolQueueSize: " + 
             writerPool.getConfiguration().getQueue().size();
        }
        
    }

    class ThreadPoolProbeImpl implements ThreadPoolProbe {
        private String pname = null;
        private AtomicInteger counter = null; 

        public ThreadPoolProbeImpl(String pname, AtomicInteger counter) {
            this.pname = pname;
            this.counter = counter;
        }
        public void onThreadPoolStartEvent(AbstractThreadPool threadPool) {
            if (DEBUG) {
                logger.log(logger.INFO, "ThreadPool["+pname+"] started, "+threadPool);
            }
        }
        public void onThreadPoolStopEvent(AbstractThreadPool threadPool) {
            if (DEBUG) {
                logger.log(logger.INFO, "ThreadPool["+pname+"] stopped");
            }
        }
        public void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread) {
            int cnt = counter.getAndIncrement();
            if (DEBUG) {
                logger.log(logger.INFO, 
                "ThreadPool["+pname+"] thread allocated["+(++cnt)+"]");
            }
        }
        public void onThreadReleaseEvent(AbstractThreadPool threadPool, Thread thread) {
            int cnt = counter.getAndDecrement();
            if (DEBUG) {
                logger.log(logger.INFO, 
                "ThreadPool["+pname+"] thread released["+(--cnt)+"]");
            }
        }
        public void onMaxNumberOfThreadsEvent(AbstractThreadPool threadPool,
                                              int maxNumberOfThreads) {
            if (DEBUG) {
                logger.log(logger.INFO, 
                "ThreadPool["+pname+"] threads max "+maxNumberOfThreads+" reached");
            }
        }
        public void onTaskQueueEvent(AbstractThreadPool threadPool, Runnable task) {
            if (DEBUG) {
                logger.log(logger.DEBUGHIGH, 
                "ThreadPool["+pname+"] task queue event:"+task);
            }
        }
        public void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task) {
            if (DEBUG) {
                logger.log(logger.DEBUGHIGH, 
                "ThreadPool["+pname+"] task dequeue event:"+task);
            }
        }
        public void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task) {
            if (DEBUG) {
                logger.log(logger.DEBUGHIGH, 
                "ThreadPool["+pname+"] task complete event:"+task);
            }
        }
        public void onTaskQueueOverflowEvent(AbstractThreadPool threadPool) {
            if (DEBUG) {
                logger.log(logger.DEBUGHIGH, 
                "ThreadPool["+pname+"] task queue overflow event");
            }
        }
        public void onTaskCancelEvent(AbstractThreadPool threadPool, Runnable task) {
            if (DEBUG) {
                logger.log(logger.DEBUGHIGH, 
                "ThreadPool["+pname+"] task cancel event");
            }
        }
    }
}

