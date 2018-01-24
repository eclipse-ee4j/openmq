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
import java.nio.channels.SelectionKey;




public class IMQEmbeddedService extends IMQService 
{

    private static boolean DEBUG = false;


    //Properties cfg = System.getProperties();
    //BrokerConfig cprops = Globals.getConfig();

    /**
     * the list of connections which this service knows about
     */

    protected PacketRouter router = null;
    //private AuthCacheData authCacheData = new AuthCacheData();
    protected ThreadPool pool = null;
    protected RunnableFactory runfac = null;

    public IMQEmbeddedService(String name,
            int type, PacketRouter router, int min, int max) 
    {
	    super(name, type);
        this.router = router;
        runfac = getRunnableFactory();

        if (max == 0) {
            throw new RuntimeException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_MAX_THREAD_ILLEGAL_VALUE, 
                    name, String.valueOf(max)));
        }

        pool = new ThreadPool(name, min, max, runfac);
//        pool.setPriority(priority);
         
    }

    public Hashtable getPoolDebugState() {
        return pool.getDebugState();
    }


    public void dumpPool()  {
        pool.debug();
    }

    protected RunnableFactory getRunnableFactory()
    {
        return new OperationRunnableFactory(true);
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

    @Override
    public synchronized int[] setMinMaxThreadpool(int min, int max) {
        if (pool == null) {
            return null;
        }
        return pool.setMinMax(min, max);
    }


    public synchronized  void startService(boolean startPaused) {
        // we really don't do much on starting/stopping a service
        //
        // we will have to do something with concurrent queues when paused
        // maybe pausing the thread pool is enough
        //
        if (isServiceRunning()) {
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
                              "in-process connections", 
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
    
        // start the thread pool
        pool.start();
        if (startPaused) {
            setServiceRunning(false);
            setState(ServiceState.PAUSED);
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

            String strings[] = { getName(), "none" };
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
                getName(), "none");
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
    }

    public void startNewConnections() 
        throws IOException
    {
        if (getState() != ServiceState.QUIESCED && getState() != ServiceState.PAUSED) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_START_SERVICE));
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

        String strings[] = { getName(), "none"};
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
        String strings[] = { getName(), "none" };
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


    public IMQEmbeddedConnection createConnection()
        throws IOException, BrokerException
    {
        IMQEmbeddedConnection con = new IMQEmbeddedConnection(this, router);

        // ok, get threads
        OperationRunnable read = (OperationRunnable)pool.getAvailRunnable(false);
        OperationRunnable write = (OperationRunnable)pool.getAvailRunnable(false);

        // make sure we could get them
        if (read == null || write == null) {
            if (read != null) {
                read.release();
            }
            if (write != null) {
                write.release();
            }

            String args[] = {this.toString(),
                       String.valueOf(pool.getAssignedCnt()),
                       String.valueOf(pool.getMaximum())};
            logger.log(Logger.WARNING, 
                BrokerResources.E_NOT_ENOUGH_THREADS,
                args);

            pool.debug();
            con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, 
                 Globals.getBrokerResources().getKString(
                 BrokerResources.E_NOT_ENOUGH_THREADS, args));
            throw new BrokerException( 
                Globals.getBrokerResources().getKString(
                    BrokerResources.E_NOT_ENOUGH_THREADS,
                    args),
                BrokerResources.E_NOT_ENOUGH_THREADS,
                (Throwable) null,
                Status.NOT_ALLOWED);

        }

        // put on the connectionManager:
        connectionList.addConnection(con);

        // now assign them threads
        try {
            read.assignOperation(con, SelectionKey.OP_READ, OperationRunnable.FOREVER);
        } catch (IllegalAccessException ex) { //should not happen
            String emsg = "Unable to assign op to reader thread for connection: "+con;
            Globals.getLogger().logStack(Logger.ERROR, emsg, ex);
            write.release();
            con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, emsg);
            throw new BrokerException(emsg, ex);
        }
        try {
            write.assignOperation(con, SelectionKey.OP_WRITE, OperationRunnable.FOREVER);
        } catch (IllegalAccessException ex) { //should not happen
            String emsg = "Unable to assign op to writer thread for connection: "+con;
            Globals.getLogger().logStack(Logger.ERROR, emsg, ex);
            read.destroy();
            con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, emsg);
            throw new BrokerException(emsg, ex);
        }

        // ok, we are done so return the connection
        return con;
    }

}

