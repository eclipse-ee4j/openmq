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
 * @(#)IMQIPService.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.io.*;

import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.pool.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import java.util.*;




public abstract class IMQIPService extends IMQService implements Runnable, 
            ProtocolCallback
{

    private static boolean DEBUG = false;

    // ONLY turn on when it needs to be used (since it opens us
    // up for a denial of service attack
    //
    // OK .. if the property is set, listen to it, else ...
    //      look @ os
    private static boolean WORKAROUND_4244135 = false;


    // WORKAROUND_HTTP : Due to a bug in HTTP tunneling protocol
    // do not close the "server socket" if the service is running
    // on HTTP tunnel protocol..
    // This workaround should get removed when BugId 4435337 is
    // fixed in the next release.
    private boolean WORKAROUND_HTTP = false;

    static {

        Properties cfg = System.getProperties();
        BrokerConfig cprops = Globals.getConfig();
        // According to the bug report this problem is "supposed"
        // to be fixed in 1.3.1 .. however its still happening in
        // the 3/28/01 build .. for now I'm running this on
        // ANY linux/hotspot VM in 1.3

        // the workaround can also be explicitly turned on/off

        try {
            if (cprops.getProperty(Globals.IMQ
                    + ".workaround_4244135") != null)
            {

                WORKAROUND_4244135 =  cprops.getBooleanProperty(
                                       Globals.IMQ + ".workaround_4244135");
            } else {
                WORKAROUND_4244135 =   cfg.getProperty("os.name")
                        .startsWith("Linux") &&
                      (cfg.getProperty("java.vm.name")
                           .indexOf("HotSpot") > -1) &&
                      cfg.getProperty("java.version")
                           .startsWith(/* "1.3.0" */ "1.3");
            }
        } catch (Throwable thr) {
            Globals.getLogger().log(Logger.ERROR, thr.toString(), thr);
        } finally {
            if (WORKAROUND_4244135)
                Globals.getLogger().log(Logger.DEBUG,
                    "Using workaround for bug 4244135");
        }


    }
    /**
     * the list of connections which this service knows about
     */
    private ConnectionManager connectionList = Globals.getConnectionManager();

    protected PacketRouter router = null;
    //private AuthCacheData authCacheData = new AuthCacheData();
    protected ThreadPool pool = null;
    //protected Thread acceptThread = null;
    protected RunnableFactory runfac = null;
    protected Protocol protocol = null;
    protected Thread listenThread = null;

    public IMQIPService(String name, Protocol protocol, 
            int type, PacketRouter router, int min, int max) 
    {
	super(name, type);
        this.protocol = protocol;
        this.router = router;
        runfac = getRunnableFactory();

        if (protocol.canPause() == false) {
            WORKAROUND_HTTP = true;
        }

        if (max == 0) {
            throw new RuntimeException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_MAX_THREAD_ILLEGAL_VALUE, 
                    name, String.valueOf(max)));
        }

        pool = new ThreadPool(name, min, max, runfac);
//        pool.setPriority(priority);
        if (protocol.getHostName() != null && 
             !protocol.getHostName().equals(Globals.HOSTNAME_ALL))
            addServiceProp("hostname", protocol.getHostName());
        protocol.registerProtocolCallback(this, null);
         
    }

    public Hashtable getPoolDebugState() {
        return pool.getDebugState();
    }


    public void dumpPool()  {
        pool.debug();
    }

    protected abstract RunnableFactory getRunnableFactory();

    public Protocol getProtocol() {
        return protocol;
    }

    public synchronized int getMinThreadpool() {
        if (pool == null) {
            return 0;
        }
        return pool.getMinimum();
    }

    public synchronized int getMaxThreadpool() {
        if (pool == null) {
            return 0;
        }
        return pool.getMaximum();
    }

    public synchronized int getActiveThreadpool() {
        if (pool == null) {
            return 0;
        }
        return pool.getThreadNum();
    }
    public void setPriority(int priority)
    {
        pool.setPriority(priority);
    }

    /**
     * @return int[0] - min; int[1] - max; -1 means no change
     */
    @Override
    public synchronized int[] setMinMaxThreadpool(int min, int max) {
        if (pool == null) {
            return null;
        }
        return pool.setMinMax(min, max);
    }


    // XXX - this implemenetation assumes that we have 1 distinct
    // listen thread per/service
    // revisit  (may not always be true)

    public synchronized  void startService(boolean startPaused) {
        if (isServiceRunning() || listenThread != null) {
            /*
             * this error should never happen in normal operation
             * if its does, we will need to add an error message 
             * to the resource bundle
             */
            logger.log(Logger.DEBUG, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "unable to start service, already started.");
            return;
        }
        setState(ServiceState.STARTED);
        {
            String args[] = { getName(), 
                              protocol.toString(), 
                              String.valueOf(getMinThreadpool()),
                              String.valueOf(getMaxThreadpool()) };
            logger.log(Logger.INFO, BrokerResources.I_SERVICE_START, args);
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
        }
     
        listenThread = new MQThread(this, getName() +"_ACCEPT");
        listenThread.start();
        pool.start();
        if (startPaused) {
            setServiceRunning(false);
            setState(ServiceState.PAUSED);
            if (!WORKAROUND_HTTP) {
                try {
                    protocol.close();
                    Globals.getPortMapper().updateServicePort(name, 0);
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING, 
                          BrokerResources.E_INTERNAL_BROKER_ERROR, 
                          "starting paused service " + this, ex);
                }
            }
        } else {
            setServiceRunning(true);
            setState(ServiceState.RUNNING);
        }
        notifyAll();
    }

    public void stopService(boolean all) { 
        synchronized (this) {

            if (isShuttingDown()) {
                // we have already been called
                return;
            }

            String strings[] = { getName(), protocol.toString() };
            if (all) {
                logger.log(Logger.INFO, 
                           BrokerResources.I_SERVICE_STOP, 
                           strings);
            } else if (!isShuttingDown()) {
                logger.log(Logger.INFO, 
                           BrokerResources.I_SERVICE_SHUTTINGDOWN, 
                           strings);
            } 
           
            setShuttingDown(true);
        }

        try {
            protocol.close();
        } catch (Exception ex) {
            logger.log(Logger.DEBUG,"Exception shutting down "
               + " protocol, ignoring since we are exiting", ex);
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
            this.notifyAll();
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

        synchronized (this) {
            setState(ServiceState.STOPPED);
            this.notifyAll();
        }

        if (pool.isValid())
            pool.waitOnDestroy(getDestroyWaitTime());

        if (DEBUG) {
            logger.log(Logger.DEBUG, 
                "Destroying Service {0} with protocol {1} ", 
                getName(), protocol.toString());
        }
    }

    public void stopNewConnections() 
        throws IOException, IllegalStateException
    {
        if (getState() != ServiceState.RUNNING) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_STOP_SERVICE));
        }
        setState(ServiceState.QUIESCED);
        if (!WORKAROUND_4244135 &&!WORKAROUND_HTTP) {
                if (protocol != null && protocol.isOpen())
                    protocol.close();
                Globals.getPortMapper().updateServicePort(name, 0);
        }
    }

    public void startNewConnections() 
        throws IOException
    {
        if (getState() != ServiceState.QUIESCED && getState() != ServiceState.PAUSED) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_START_SERVICE));
        }

        if (!WORKAROUND_4244135 && !WORKAROUND_HTTP) {
            try {
                protocol.open();
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    "starting connections" + this, ex);
            }

        }

        synchronized (this) {
            setState(ServiceState.RUNNING);
            this.notifyAll();
        }
    }

    public void pauseService(boolean all) {

        if (!isServiceRunning()) {
            logger.log(Logger.DEBUG, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    "unable to pause service " 
                    + name + ", already paused.");
            return;
        }  

        String strings[] = { getName(), protocol.toString() };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_PAUSE, strings);

        try {
            stopNewConnections();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, 
                      BrokerResources.E_INTERNAL_BROKER_ERROR, 
                      "pausing service " + this, ex);
        }
        setState(ServiceState.PAUSED);

        if (all) {
            pool.suspend();
        }
        setServiceRunning(false);
    }

    public void resumeService() {
        if (isServiceRunning()) {
             logger.log(Logger.DEBUG, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    "unable to resume service " 
                    + name + ", already running.");
           return;
        }
        String strings[] = { getName(), protocol.toString() };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_RESUME, strings);
        try {
            startNewConnections();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, 
                      BrokerResources.E_INTERNAL_BROKER_ERROR, 
                      "pausing service " + this, ex);
        }
        pool.resume();
        setServiceRunning(true);

        synchronized (this) {
            setState(ServiceState.RUNNING);
            this.notifyAll();
        }
        
    }

    @Override
    public void updateService(int port, int min, int max) 
        throws IOException, PropertyUpdateException, BrokerException
    {
        String name = this.getName();
        String protoname = Globals.getConfig().getProperty(
                           IMQIPServiceFactory.SERVICE_PREFIX + 
                           name + ".protocoltype");
        Protocol p = getProtocol();

        String args[] = { name, 
                          String.valueOf(port), 
                          String.valueOf(min), 
                          String.valueOf(max)};
        logger.log(Logger.INFO, BrokerResources.I_UPDATE_SERVICE_REQ, args);
        if (port > -1) {
            // OK 
            Map params = new HashMap();
            params.put("port", String.valueOf(port));
            p.checkParameters(params);

            if (!WORKAROUND_4244135) {

                p.setParameters(params);

                // Re-Register port with portmapper
                Globals.getPortMapper().removeService(name);
                Globals.getPortMapper().addService(name, protoname, 
                Globals.getConfig().getProperty(
                           IMQIPServiceFactory.SERVICE_PREFIX + 
                           name + ".servicetype"),
                p.getLocalPort(), getServiceProperties());
            }

            Globals.getConfig().updateProperty(
                IMQIPServiceFactory.SERVICE_PREFIX +
                name + "." + protoname + 
                ".port", String.valueOf(port));
        }

        if (min > -1 || max > -1) {
            try {
                if (max == 0) {
                    throw new BrokerException(
                         Globals.getBrokerResources().getKString(
                             BrokerResources.X_MAX_THREAD_ILLEGAL_VALUE, 
                             name, String.valueOf(max)));
                }
                int[] rets = this.setMinMaxThreadpool(min, max);
                if (rets != null) {
                    if (rets[0] > -1) {
                        Globals.getConfig().updateProperty(
                         IMQIPServiceFactory.SERVICE_PREFIX + name +
                        ".min_threads", String.valueOf(rets[0]));
                    }
                    if (rets[1] > -1) {
                        Globals.getConfig().updateProperty(
                        IMQIPServiceFactory.SERVICE_PREFIX + name +
                        ".max_threads", String.valueOf(rets[1]));        
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new BrokerException(
                     Globals.getBrokerResources().getKString(
                         BrokerResources.X_THREADPOOL_BAD_SET, 
                         String.valueOf(min), String.valueOf(max)), 
                     e);
            }
        }
        if (port > -1 && WORKAROUND_4244135) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.E_LNX_SERVICE_UPDATE_FAILED));
        }
    }

    public synchronized void socketUpdated(Object data, 
            int port, String hostname) {
        if (port == -1) port = protocol.getLocalPort();

        String args[] = { name, String.valueOf(port), 
                   (hostname == null) ? Globals.HOSTNAME_ALL : hostname};
        logger.log(Logger.DEBUG, 
                   BrokerResources.I_SERVICE_PROTOCOL_UPDATED, 
                   args);
        Globals.getPortMapper().updateServicePort(name, port);
        if (hostname != null) {
            addServiceProp("hostname", hostname);
            Globals.getPortMapper().updateServiceProperties(
                 name, getServiceProperties());
        }
    }

    // XXX - we dont need a seperate thread to look for connections
    // for all subclasses, but this is cleaner

    public void run() {
        
        if (DEBUG) {
            logger.log(Logger.DEBUGMED, 
                 "Starting thread to listen for connections"
                 + " on {0} with protocol {1}", 
                getName(), protocol.toString());
        }
        while (true) {
            ProtocolStreams ps = null;
            // amy - check shuttingDown
            if (!isShuttingDown() && !isServiceRunning()) { 
                synchronized (this)  {
                    while (!isShuttingDown() && !isServiceRunning() &&
                                   (!WORKAROUND_4244135 ||
                                  getState() != ServiceState.PAUSED) &&
                                   (!WORKAROUND_HTTP ||
                                  getState() != ServiceState.PAUSED)) {
                        try {
                            // for workaround WORKAROUND_4244135 ..
                            // we want to always be waiting in "accept"
                            // to get and close the next connection
                            // so dont go here
                            wait();
                        } catch (InterruptedException ex) {
                            // no need to log, the exception
                            // is expected
                        }
                    }

                }
            }
            /*
             * if shutting down, exit listenThread
             */
            if (isShuttingDown()) {
                break; 
            }
             
            try {
                try {
                   ps = protocol.accept();
                } catch (Exception ex) {
                   // maybe we close, try again
                  if (getState() == ServiceState.PAUSED || getState() == ServiceState.QUIESCED) {
                      // we are paused .. wait for a resume
                      synchronized (this) {
                          if (getState() == ServiceState.PAUSED || getState() == ServiceState.QUIESCED) {
                              try {
                                  this.wait();
                              } catch (InterruptedException ex1) {
                                  // should not happen but if it
                                  // does, its valid
                              }
                          }
                      }
                      continue;
                  }
                  // valid condition, only log in debug more
                  if (!isShuttingDown()) {
                      logger.log(Logger.DEBUG,"Exception accepting connection "
                          + protocol +  " expected", ex);
                  } else {
                      break;
                  }

                  ps = protocol.accept();
                }

                // if 4244135 isnt fixed ... we can get a connection
                // when paused, if we do, close it
                if (WORKAROUND_4244135 || WORKAROUND_HTTP) {
                    if (getState() == ServiceState.PAUSED) {
                         // we are paused .. wait for a resume
                         synchronized (this) {
                             if (getState() == ServiceState.PAUSED) {
                                 try {
                                     ps.close(); 
                                 }
                                 catch (IOException e) {
                                     // XXX -  I belive this is valid
                                     // if not, should be higher level
                                     logger.log(Logger.DEBUG,
                                         "Exception closing down "
                                          + protocol, e);
                                 }
                                 continue;
                             } 
                         }
                     }
                }

                // XXX - REVISIT .. for debug purposes, we should
                // probably pass in the port # .. to identify the
                // TCP version (OR a name .. if we have a property for
                // ONE ... 
                if (DEBUG) {
                   String strings[] = { getName(), 
                                        protocol.toString(), 
                                        ps.toString() };
                   logger.log(Logger.DEBUGHIGH, "Accepted new connection"
                              +" for Service( {0},{1}, {2})", 
                                strings);
                }
                IMQIPConnection con = createConnection(ps);
                if (con == null) {
                   logger.log(Logger.WARNING,
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "null connection " + ps);
                   continue;
                }
		try {
                    synchronized (this) {
                        if (isShuttingDown()) {
                            if (DEBUG) {
                            String strings[] = { getName(), 
                                                 protocol.toString(), 
                                                 ps.toString() };
                            logger.log(Logger.DEBUGHIGH, 
                                "Remove new connection for Service( "
                                +"{0},{1}, {2}) is in shutting down", 
                                strings);

                            }
                            con.setConnectionState(
                                Connection.STATE_UNAVAILABLE);
                            ps.close();
                            setServiceRunning(false);
                            return;

                        } else {
                            connectionList.addConnection(con);
                        }
                    }
                } catch (BrokerException ex) {
                    con.setConnectionState(Connection.STATE_UNAVAILABLE);
                    logger.log(Logger.WARNING, ex.getMessage());
                }
                try {
                    acceptConnection(con);
                } catch (Throwable ex) {
                    // something went wrong, remove from list
                    connectionList.removeConnection(con.getConnectionUID(),
                            true, GoodbyeReason.CON_FATAL_ERROR, 
                            ex.toString());
                    if (!isShuttingDown()) {
                        int level = Logger.ERROR;
                        if (ex instanceof BrokerException &&
                            ((BrokerException)ex).getStatusCode() 
                              == Status.NOT_ALLOWED) 
                        {
                            level = Logger.DEBUG;
                        }
                         
                        logger.logStack(level, 
                            BrokerResources.E_INTERNAL_BROKER_ERROR, 
                            "Unable to allocate connection" 
                             + con.toString() + " on service " 
                             + name + ", closing", ex);
                    }
                    if (ps != null) {
                         try {
                            ps.close();
                         } catch (Exception ex1) {}
                     }
                    // valid during shutdown, only log at debug level
                    logger.log(Logger.DEBUG,
                          "Exception closing "
                          + protocol, ex);
                }

            } catch (IOException ex) {
                if (!isShuttingDown() && getState() != ServiceState.QUIESCED  )
                    logger.logStack(Logger.ERROR, 
                        BrokerResources.E_RUNNING_SERVICE, name, ex);
                // valid during shutdown, only log at debug level
                logger.log(Logger.DEBUG,
                          "Exception closing "
                          + protocol, ex);
                 break;
            } catch (Throwable ex) {
                if (!isShuttingDown())
                    logger.logStack(Logger.ERROR, 
                        BrokerResources.E_RUNNING_SERVICE, name, ex);
                // valid during shutdown, only log at debug level
                logger.log(Logger.DEBUG,
                          "Exception closing "
                          + protocol, ex);
                 if (ps != null) {
                     try {
                       ps.close();
                     } catch (Exception ex1) {}
                 }

                 break;
            }
        }
        setServiceRunning(false);

    }

    public IMQIPConnection createConnection(ProtocolStreams streams) 
        throws IOException, BrokerException
    {
        // First create a Connection
        IMQIPConnection con = new IMQIPConnection(this, streams, router);
        return con;
    }

    protected abstract void acceptConnection(IMQIPConnection con)
        throws IOException, BrokerException;
}

