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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Properties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport; 
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import com.sun.messaging.portunif.PUService;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;
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


/**
 * prototype - configuration not in place yet
 */
public class GrizzlyIPService extends IMQService 
implements GrizzlyService, NotificationInfo
{
    private static boolean DEBUG = (false || Globals.getLogger().getLevel() <= Logger.DEBUG); 

    protected PacketRouter router = null;
    private TCPNIOTransport transport = null;
    private GrizzlyProtocolImpl protocol = null;
    private TCPNIOServerConnection serverConn = null;
    private BrokerResources br = Globals.getBrokerResources();
    private GrizzlyExecutorService writerPool = null;

    private Object writeLock = new Object();
    private LinkedHashMap<ConnectionUID, GrizzlyMQIPConnection> pendingWrites = 
                         new LinkedHashMap<ConnectionUID, GrizzlyMQIPConnection>();

    private boolean dedicatedWriter = Globals.getConfig().getBooleanProperty(
                                      "imq.grizzlyIPService.dedicatedWriterThread", false);
    private AtomicInteger readerPoolThreadCnt= new AtomicInteger(0);
    private AtomicInteger writerPoolThreadCnt= new AtomicInteger(0);

    public GrizzlyIPService(String name, int type, PacketRouter router,
                            int min, int max, GrizzlyIPServiceFactory parent)
                          throws BrokerException {
        super(name, type);
        this.router = router;

        String ptypestr =  GrizzlyIPServiceFactory.SERVICE_PREFIX +name+".protocoltype";
        String p =  Globals.getConfig().getProperty(ptypestr);
        if (p == null || (!p.equals("tcp") && !p.equals("tls"))) { 
            throw new BrokerException("GrizzlyIPService: Not supported protocol: "+p);
        }
        String prefix = IMQIPServiceFactory.PROTOCOL_PREFIX + p;
        String serviceprefix = GrizzlyIPServiceFactory.SERVICE_PREFIX+name +"."+p;

        try {

            Map params = parent.getProtocolParams(p, serviceprefix);
            params.put("serviceFactoryHandlerName", parent.getFactoryHandlerName());
            protocol = new GrizzlyProtocolImpl(this, p);
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

            FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
            filterChainBuilder.add(new TransportFilter());
            if (p.equals("tls")) {
                final SSLEngineConfigurator serverConfig = initializeSSL(name, protocol);
                final SSLEngineConfigurator clientConfig = serverConfig.copy().setClientMode(true);
                filterChainBuilder.add(new SSLFilter(serverConfig, clientConfig));
            }
            filterChainBuilder.add(new GrizzlyMQConnectionFilter(this));
            filterChainBuilder.add(new GrizzlyMQPacketFilter());
            filterChainBuilder.add(new GrizzlyMQPacketDispatchFilter());
            TCPNIOTransportBuilder niobuilder = TCPNIOTransportBuilder.newInstance();
            ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig().setDaemon(false); 
            niobuilder.setSelectorThreadPoolConfig(tpc);
            transport = niobuilder.build();
            transport.setReadBufferSize(protocol.getInputBufferSize());
            transport.setWriteBufferSize(protocol.getOutputBufferSize());
            transport.setWorkerThreadPool(pool);
            transport.setProcessor(filterChainBuilder.build());
            bindTransport(false);
        } catch (Exception e) {
            String emsg = br.getKString(br.X_INIT_TRANSPORT_FOR_SERVICE, name, e.getMessage());
            logger.logStack(logger.ERROR, emsg, e);
            try {
                if (serverConn != null) {
                    try {
                    serverConn.close();
                    } catch (Exception ee) {}
                    serverConn = null;
                }
                if (writerPool != null) {
                    writerPool.shutdown();
                }
                if (transport != null) {
                    transport.stop();
                }
            } catch (IOException e1) {
                logger.logStack(logger.WARNING,
                "Unable to stop transport after bind failure", e1);
            }
            throw new BrokerException(emsg);
        }

    }

    protected boolean useDedicatedWriter() {
        return dedicatedWriter;
    }

    private void unbindTransport() throws Exception {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "GrizzlyIPService.unbindTransport() for service "+name);
        }
        if (serverConn != null) {
            try {
                serverConn.preClose();
                serverConn.close();
            } catch (Exception e) {
                logger.logStack(Logger.WARNING,
                "Exception closing server socket connection for service "+getName(), e);
            }
            serverConn = null;
            transport.unbindAll();
            logger.log(Logger.INFO, br.getKString(
                br.I_UNBOUND_TRANSPORT_FOR_SERVICE,
                getName()+"["+protocol.getType()+"]"));
        }
    }

    private void bindTransport(boolean checkpause) throws Exception {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "GrizzlyIPService.bindTransport("+checkpause+") for service "+name);
        }
        unbindTransport();
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
        String hostn = protocol.getHostName();
        int portn = protocol.getPort();
        int backlog = protocol.getBacklog();

        logger.log(Logger.INFO, br.getKString(br.I_BINDING_TRANSPORT_FOR_SERVICE, 
            getName()+"["+protocol.getType()+", "+(hostn == null ? "*":hostn)+", "+portn+"]"));
        if (getState() == ServiceState.PAUSED) {
            transport.resume(); //Grizzly: need resume to bind
        }
        if (hostn == null) {
            serverConn =  transport.bind(new InetSocketAddress(portn), backlog);
        } else {
            serverConn =  transport.bind(hostn, portn, backlog);
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
                    transport.pause();
                }
            }
        }
        Globals.getPortMapper().addService(getName(), protocol.getType(),
            Globals.getConfig().getProperty(
                GrizzlyIPServiceFactory.SERVICE_PREFIX+getName()+".servicetype"),
                lportn, getServiceProperties()); 
    }

    public static final SSLEngineConfigurator initializeSSL(
        String servicen, GrizzlyProtocolImpl proto)
        throws Exception {

        boolean reqcauth = proto.getRequireClientAuth();
        Globals.getLogger().log(Logger.INFO,  Globals.getBrokerResources().getKString(
            BrokerResources.I_INIT_FOR_SERVICE, proto.getType()+"[ClientAuth="+reqcauth+"]", servicen));

        Properties sslprops = KeystoreUtil.getDefaultSSLContextConfig(servicen, null);
        SSLContextConfigurator sslcf = new SSLContextConfigurator();
        sslcf.setKeyManagerFactoryAlgorithm(sslprops.getProperty(KeystoreUtil.KEYSTORE_ALGORITHM));
        sslcf.setKeyStoreFile(sslprops.getProperty(KeystoreUtil.KEYSTORE_FILE));
        sslcf.setKeyStorePass(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_PASSWORD));
        sslcf.setKeyStoreType(sslprops.getProperty(KeystoreUtil.KEYSTORE_TYPE));

        sslcf.setTrustManagerFactoryAlgorithm(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_ALGORITHM));
        sslcf.setTrustStoreFile(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_FILE));
        sslcf.setTrustStorePass(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_PASSWORD));
        sslcf.setTrustStoreType(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_TYPE));

        sslcf.setSecurityProtocol(sslprops.getProperty(KeystoreUtil.SECURESOCKET_PROTOCOL));

        SSLEngineConfigurator ec = new SSLEngineConfigurator(sslcf.createSSLContext(),
                                                             false, reqcauth, reqcauth);
        if (Globals.getPoodleFixEnabled()) {
            PUService.applyPoodleFix(ec, 
                Globals.getKnownSSLEnabledProtocols("GrizzlyIPService["+servicen+"]"),
                "GrizzlyIPService["+servicen+"]");
        }
        return ec;
    }

    public int getLocalPort() {
        TCPNIOServerConnection sc = serverConn;
        if (sc == null) {
            return 0;
        }
        return ((InetSocketAddress)sc.getLocalAddress()).getPort();
    }

    @Override
    public synchronized boolean isOpen() {
        return serverConn != null;
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
        if (transport != null) {
            ThreadPoolConfig pc = transport.getWorkerThreadPoolConfig();
            pc.setMaxPoolSize(max);
            pc.setCorePoolSize(min);
            ((GrizzlyExecutorService)transport.getWorkerThreadPool()).reconfigure(pc);
        }
        return null;
    }

    @Override
    public Protocol getProtocol() {
        return protocol;

    }


    public synchronized void startService(boolean startPaused) {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "GrizzlyIPService.startService("+startPaused+") for service "+getName());
        }
        if (isServiceRunning()) {
            logger.log(Logger.DEBUG, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "unable to start service, already started.");
            return;
        }
        setState(ServiceState.STARTED);
        try {       
            if (startPaused) {
                unbindTransport(); 
            } else {
                if (serverConn == null) {
                    throw new IOException("No server connection");
                }
            }
            transport.start(); 
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
        } catch (Exception e) {
            String emsg = br.getKString(br.X_START_TRANSPORT_FOR_SERVICE, name, e.getMessage());
            logger.logStack(Logger.ERROR, emsg, e);
            try {
                if (serverConn != null) {
                    try {
                    serverConn.close();
                    } catch (Exception ee) {}
                    serverConn = null;
                }
                transport.stop();
            } catch (Exception e1) {
                logger.logStack(Logger.WARNING, 
                "Failed to stop transport after start failure", e1);
                setServiceRunning(false);
                return;
            }
        }
        if (startPaused) {
            try {
                setServiceRunning(false);
                setState(ServiceState.PAUSED);
            } catch (Exception e) {
                logger.logStack(Logger.ERROR,  br.getKString(br.X_PAUSE_SERVICE,
                                getName()+"["+getProtocol()+"]", e.getMessage()), e);
                stopService(true);
            }
        } else {
            setServiceRunning(true);
            setState(ServiceState.RUNNING);
        }
    }

    @Override
    public void stopService(boolean all) { 
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "GrizzlyIPService.stopService("+all+") for service "+getName());
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

            try {
                unbindTransport();
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, 
                "Exception unbinding transport for service "+getName()+"["+getProtocol()+"]", ex);
            }
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
            transport.stop();
        } catch (Exception e)  {
            logger.logStack(Logger.WARNING, 
            "Exception stopping transport for service "+getName()+"["+getProtocol()+"]"+
             ", ignoring since we are exiting", e);
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
                "Exception in waiting reader thread pool terminate on stopping service "+getName(), e);
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
            "GrizzlyIPService.stopNewConnections() for service "+getName());
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
            "GrizzlyIPService.startNewConnections() for service "+getName());
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
            "GrizzlyIPService.pauseService("+all+") for service "+getName());
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
            transport.pause();
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
            "GrizzlyIPService.resumeService() for service "+getName());
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
            transport.resume();
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
            "GrizzlyIPService.updateService("+port+", "+min+", "+max+", "+store+
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

    public GrizzlyMQIPConnection createConnection(org.glassfish.grizzly.Connection c)
    throws IOException, BrokerException {
        return new GrizzlyMQIPConnection(this, router, c);
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

        Map.Entry<ConnectionUID, GrizzlyMQIPConnection> entry = null;
        GrizzlyMQIPConnection pc = (GrizzlyMQIPConnection)con;

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

        final GrizzlyMQIPConnection c = pc;
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
        "Unsupported call GrizzlyIPServer.assigned()");
    }

    public void released(IMQConnection con, int events) {
        throw new UnsupportedOperationException(
        "Unsupported call GrizzlyIPServer.assigned()");
    }

    public void destroy(String reason) {
    }

    public void dumpState() {
        logger.log(Logger.INFO, getStateInfo());
    }

    public String getStateInfo() {
        synchronized (writeLock) {
            return 
            "GrizzlyIPService["+getName()+"]"+
            "pendingWriteCount: " + pendingWrites.size()+"\n"+
            "GrizzlyIPService["+getName()+"]"+
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
                "ThreadPool["+pname+"] task canceled event");
            }
        }
    }
}


