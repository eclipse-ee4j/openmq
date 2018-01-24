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
 * @(#)BrokerStateHandler.java	1.42 07/11/07
 */ 

package com.sun.messaging.jmq.jmsserver;

import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.common.handlers.InfoRequestHandler;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.OperationNotAllowedException;
import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStoreUtil;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.ExclusiveRequest;
import com.sun.messaging.jmq.util.DiagManager;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.api.BridgeServiceManager;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Class which handles shutting down and quiescing a broker.
 * <P>
 * <b>XXX</b> tasks to do:
 *  <UL> <LI>shutdown timeout</LI>
 *       <LI>Wait for queisce to complete</LI>
 *       <LI>handle dont takeover flag</LI>
 *  </UL>
 */

public class BrokerStateHandler
{
     private Logger logger = Globals.getLogger();
     private BrokerResources br = Globals.getBrokerResources();

     private static final Object exclusiveRequestLock = new Object();
     private static ExclusiveRequest exclusiveRequestInProgress = null;

     private static boolean shuttingDown = false;
     private static boolean shutdownStarted = false;

     private static Thread shutdownThread = null;
     private static boolean storeShutdownStage0 = false;
     private static boolean storeShutdownStage1 = false;
     private static boolean storeShutdownStage2 = false;

     QuiesceRunnable qrun = null;
     //boolean prepared = false;


     long targetShutdownTime = 0;

     private static int restartCode = Globals.getConfig().getIntProperty(
               Globals.IMQ +".restart.code", 255);

     ClusterListener cl = new StateMonitorListener();
     private FaultInjection fi = null;

     public BrokerStateHandler() {
         Globals.getClusterManager().addEventListener(cl);
         fi = FaultInjection.getInjection();
     }

     public static final boolean isShuttingDown() {
         return shuttingDown;
     }
     public static final void setShuttingDown(boolean b) {
         shuttingDown = b;
     }

     public static final boolean isShutdownStarted() {
         return shutdownStarted;
     }
     static final void setShutdownStarted(boolean b) {
         shutdownStarted = b;
     }

     public static final boolean isStoreShutdownStage0() {
         return storeShutdownStage0;
     }

     public static final boolean isStoreShutdownStage1() {
         return storeShutdownStage1;
     }

     public static final boolean isStoreShutdownStage2() {
         return storeShutdownStage2;
     }

     public static final Thread getShutdownThread() {
         return shutdownThread;
     }

     public void destroy() {
         Globals.getClusterManager().addEventListener(cl);
     }


     public static int getRestartCode() {
         return restartCode;
     }
     

     public long getShutdownRemaining() {
         if (targetShutdownTime == 0) return -1;
         long remaining = targetShutdownTime - System.currentTimeMillis();
         if (remaining < 0) remaining = 0;
         return remaining;
    }

    public void takeoverBroker(String brokerID, Object extraInfo, boolean force)
        throws BrokerException
    {
        ClusterManager cm = Globals.getClusterManager();
        if (!cm.isHA() && !Globals.isBDBStore()) {
            throw new BrokerException( 
               Globals.getBrokerResources().getKString(
               BrokerResources.X_NO_ADMIN_TAKEOVER_SUPPORT));
        } else {
            Object extraInfo2 = null; 
            HAClusteredBroker hcb = null;
            if (Globals.getJDBCHAEnabled()) {
                hcb = (HAClusteredBroker)cm.getBroker(brokerID);
            } else if (Globals.isBDBStore()) {
                hcb = (HAClusteredBroker)cm.getBrokerByNodeName(brokerID);
                extraInfo2 = MigratableStoreUtil.
                             parseEffectiveBrokerIDToStoreSessionUID(brokerID);
            }
            if (hcb == null) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_UNKNOWN_BROKERID, brokerID));
            }
            if (hcb.isLocalBroker()) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_CANNOT_TAKEOVER_SELF));
            } 
            Globals.getHAMonitorService().takeoverBroker(hcb, extraInfo, extraInfo2, force);
        }
    }

    /**
     * @return the host:port of the broker that takes over this broker
     */
    public String takeoverME(String brokerID, 
                           Long syncTimeout, Connection conn)
                           throws BrokerException {

        ClusterManager cm = Globals.getClusterManager();
        if (!Globals.isBDBStore()) {
            throw new OperationNotAllowedException( 
               Globals.getBrokerResources().getKString(
               BrokerResources.X_NO_ADMIN_TAKEOVER_SUPPORT),
               MessageType.getString(MessageType.MIGRATESTORE_BROKER));
        } 
        HAClusteredBroker hcb = (HAClusteredBroker)cm.getBrokerByNodeName(brokerID);
        if (hcb == null) {
            throw new OperationNotAllowedException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_UNKNOWN_BROKERID, brokerID),
                MessageType.getString(MessageType.MIGRATESTORE_BROKER));
        }
        if (hcb.isLocalBroker()) {
             throw new OperationNotAllowedException(
                 Globals.getBrokerResources().getKString(
                 BrokerResources.X_ADMIN_TAKEOVERME_BY_ME, hcb),
                 MessageType.getString(MessageType.MIGRATESTORE_BROKER));
        }
        try {
            BrokerAddress addr = Globals.getClusterBroadcast().
                                     lookupBrokerAddress(brokerID);
            if (addr == null) {
                throw new OperationNotAllowedException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_CLUSTER_BROKER_NOT_ONLINE, hcb+"["+brokerID+"]"),
                    MessageType.getString(MessageType.MIGRATESTORE_BROKER));
            }
            Globals.getServiceManager().stopNewConnections(ServiceType.NORMAL);
            prepareShutdown(false, false, addr);
            shutdownServices("admin:migratestore["+brokerID+"]", 0, conn);
        } catch (Throwable t) {
            throw new BrokerException(t.getMessage(),
                Status.PRECONDITION_FAILED); 
        }
        return Globals.getHAMonitorService().takeoverME(hcb, brokerID, syncTimeout);
    }

    public static void setExclusiveRequestLock(ExclusiveRequest req) 
    throws BrokerException {
        synchronized(exclusiveRequestLock) {
            if (req != null) {
                if (exclusiveRequestInProgress != null) {
                    throw new BrokerException(
                        exclusiveRequestInProgress.toString(true), Status.CONFLICT);
                } 
                exclusiveRequestInProgress = req;
                return;
            } 
        }
    }

    public static void unsetExclusiveRequestLock(ExclusiveRequest req) {
        synchronized(exclusiveRequestLock) {
            if (exclusiveRequestInProgress == null) {
                return;
            }
            if (exclusiveRequestInProgress == req) {
                exclusiveRequestInProgress = null;
            }
        }
    }

    /**
     * Stop allowing new jms connections to the broker.
     * This allows an administrator to "drain" the broker before
     * shutting it down to prevent the loss of non-persistent  
     * state.
     */
    public void quiesce() 
       throws IllegalStateException, BrokerException
   {
        if (qrun != null) {
            // throw exception
            throw new IllegalStateException("Already Quiescing");
        }
        synchronized (this) {
            qrun = new QuiesceRunnable();
        }
        Thread thr = new MQThread(qrun,
                                "quiesce thread");
        thr.start();
    }

    /**
     * Start allowing new jms connections to the broker.
     * This allows an administrator to stop the "drain" the broker before
     * shutting it down to prevent the loss of non-persistent  
     * state.
     */
    public void stopQuiesce() 
        throws BrokerException
    {
         try {
             // if we are in the process of quiescing, stop it
             // then un-quiesce
             QuiesceRunnable qr = null;
             synchronized (this) {
                 qr = qrun;
             }
             if (qr != null) 
                qr.breakQuiesce(); // stop quiesce
             // now, unquiesce

             // stop accepting new jms threads
             ServiceManager sm = Globals.getServiceManager();
             sm.startNewConnections(ServiceType.NORMAL);

             ClusteredBroker cb = Globals.getClusterManager().getLocalBroker();
             cb.setState(BrokerState.OPERATING);
             logger.log(Logger.INFO, BrokerResources.I_UNQUIESCE_DONE);
         } catch (Exception ex) {
              Globals.getLogger().logStack(Logger.WARNING,
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "exception during unquiesce", ex); 
              throw new BrokerException(
                       Globals.getBrokerResources().getKString(
                          BrokerResources.E_INTERNAL_BROKER_ERROR,
                          "unable to unquiesce"), ex);
         }
    }

    /**
     * shutdown down the broker at the specific time.
     * @param time milliseconds delay before starting shutdown
     *             or 0 if no delay
     * @param requestedBy why is the broker shutting down
     * @param exitCode exitcode to use on shutdown
     * @param threadOff if true, run in a seperate thread
     * @param noFailover if true, the broker does not want
     *             another broker to take over its store.
     * @param exit should we should really exit
     */
    public void initiateShutdown(String requestedBy, long time,
                      boolean triggerFailover, int exitCode, boolean threadOff)
    {
        initiateShutdown(requestedBy, time, triggerFailover,
              exitCode, threadOff, true, true);
    }
    public void initiateShutdown(String requestedBy, long time,
                      boolean triggerFailover, int exitCode, boolean threadOff,
                      boolean exit, boolean cleanupJMX) 
    {
    	
    	
    	
        synchronized (this) {
            if (shutdownStarted)  {
                if (targetShutdownTime > 0) {
                   if (time > 0) {
                         targetShutdownTime = System.currentTimeMillis() + 
                              time;
                   } else {
                         targetShutdownTime = 0;
                   }
                    
                   this.notifyAll();
                }
                return;
            }
            shutdownStarted = true;
        }

	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.notifyShutdownStart();
	}

        if (time > 0) {
            targetShutdownTime = System.currentTimeMillis() + 
                         time;
        } else {
            targetShutdownTime = 0;
        }
        ShutdownRunnable runner = new ShutdownRunnable(requestedBy, targetShutdownTime,
                        triggerFailover, exitCode, cleanupJMX);
        if (threadOff) {
            Thread thr = new MQThread(runner,
                                "shutdown thread");
            thr.setDaemon(false);
            thr.start();
        } else {
            int shutdown = runner.shutdown(); // run in current thread
            if (exit)
                System.exit(shutdown);
        }
    }

    public class QuiesceRunnable implements Runnable
    {
        boolean breakQuiesce = false;

        public QuiesceRunnable() 
            throws BrokerException
        {
            logger.log(Logger.INFO,
                BrokerResources.I_QUIESCE_START);

	    Agent agent = Globals.getAgent();
	    if (agent != null)  {
	        agent.notifyQuiesceStart();
	    }

            try {
                ClusteredBroker cb = Globals.getClusterManager().getLocalBroker();
                cb.setState(BrokerState.QUIESCE_STARTED);

                // stop accepting new jms threads
                ServiceManager sm = Globals.getServiceManager();
                sm.stopNewConnections(ServiceType.NORMAL);
            } catch (Exception ex) {
                throw new BrokerException(
                    BrokerResources.X_QUIESCE_FAILED, ex);
            }
        }
        public void run() {
            try {

                // ok, now wait until connection count goes to 0 and 
                // message count goes to 0

               // we are going to poll (vs trying to get a notification) because
               // I dont want to worry about a possible deadlock

               synchronized (this) {
                    while (!breakQuiesce) {
                        // XXX - check state
                        // if OK, break
                        ServiceManager smgr = Globals.getServiceManager();
                        int ccnt = smgr.getConnectionCount(ServiceType.NORMAL) ;
                        int msgcnt = Globals.getDestinationList().totalCountNonPersist();
                        if (ccnt == 0 && msgcnt == 0) {
                            break;
                        }
                        logger.log(logger.INFO, 
                               br.getKString(BrokerResources.I_MONITOR_QUIESCING, ccnt, msgcnt));
                        this.wait(10*1000);
                    }
               }
               if (!breakQuiesce)  {      
                    ClusteredBroker cb = Globals.getClusterManager().getLocalBroker();
                    cb.setState(BrokerState.QUIESCE_COMPLETED);
               }
               logger.log(Logger.INFO, BrokerResources.I_QUIESCE_DONE);
               synchronized(this) {
                    qrun = null; // we are done
                }
	       Agent agent = Globals.getAgent();
	       if (agent != null)  {
	           agent.notifyQuiesceComplete();
	       }

            } catch (Exception ex) {
                Globals.getLogger().logStack(
                    Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "quiescing broker ", ex); 
            }

       }

       public synchronized void breakQuiesce() {
            breakQuiesce = true;
            notifyAll();
       }

   }

    public void prepareShutdown(boolean failover, boolean force) {
        prepareShutdown(failover, force, null);
    } 

    private void prepareShutdown(boolean failover,
                 boolean force, BrokerAddress excludedBroker) {
        //prepared = true;

        BridgeServiceManager bridgeManager =  Globals.getBridgeServiceManager();
        if (bridgeManager != null) {
            try {
                Globals.getLogger().log(Logger.INFO,
                        Globals.getBrokerResources().I_STOP_BRIDGE_SERVICE_MANAGER);

                bridgeManager.stop();
                Globals.setBridgeServiceManager(null);

                Globals.getLogger().log(Logger.INFO,
                        Globals.getBrokerResources().I_STOPPED_BRIDGE_SERVICE_MANAGER);
            } catch (Throwable t) {
                logger.logStack(Logger.WARNING,
                       Globals.getBrokerResources().W_STOP_BRIDGE_SERVICE_MANAGER_FAILED, t);
            }
        }

        if (Globals.getMemManager() != null)
            Globals.getMemManager().stopManagement();

        // First stop creating new destinations
        if (excludedBroker == null) {
            Globals.getDestinationList().shutdown();
        }

        // Next, close all the connections with clustered brokers
        // so that we don't get stuck processing remote events..

        // XXX - tell cluster whether or not failover should happen
        Globals.getClusterBroadcast().stopClusterIO(failover, force, excludedBroker);
    }

    private void shutdownServices(String requestedBy, 
                                  int exitCode, Connection excludedConn)
                                  throws BrokerException { 

        ServiceManager sm = Globals.getServiceManager();

        ConnectionManager cmgr = Globals.getConnectionManager();
        Globals.getLogger().logToAll(Logger.INFO,
                                     BrokerResources.I_BROADCAST_GOODBYE);
        int id = GoodbyeReason.SHUTDOWN_BKR;
        String msg = Globals.getBrokerResources().getKString(
                         BrokerResources.M_ADMIN_REQ_SHUTDOWN,
                         requestedBy);
        if (exitCode == getRestartCode()) {
            id = GoodbyeReason.RESTART_BKR;
            msg = Globals.getBrokerResources().getKString(
                      BrokerResources.M_ADMIN_REQ_RESTART,
                      requestedBy);
        }
        cmgr.broadcastGoodbye(id, msg, excludedConn);

        Globals.getLogger().logToAll(Logger.INFO,
                                     BrokerResources.I_FLUSH_GOODBYE);
        cmgr.flushControlMessages(1000);
    
        // XXX - should be notify other brokers we are going down ?
        sm.stopAllActiveServices(true, (excludedConn == null ? null: excludedConn.getService().getName()));
    }

    public class ShutdownRunnable implements Runnable
    {

        String requestedBy = "unknown";
        //long targetTime = 0;
        int exitCode = 0;
        boolean failover = false;
        boolean cleanupJMX = false;

        public ShutdownRunnable(String who, long target, boolean trigger,
                      int exitCode, boolean cleanupJMX)
        {
            logger.log(Logger.DEBUG,"Shutdown requested by " + who);
            requestedBy = who;
            //this.targetTime = target;
            this.failover = trigger;
            this.exitCode = exitCode;
            this.cleanupJMX = cleanupJMX;
        }

        public void run() {
            int exit = shutdown();
            Broker.getBroker().exit(exit,
                Globals.getBrokerResources().getKString(
                     BrokerResources.I_SHUTDOWN_REQ, requestedBy),
                (exitCode == getRestartCode()) ?
                    BrokerEvent.Type.RESTART :
                    BrokerEvent.Type.SHUTDOWN);
        }

        public int shutdown() {
            ClusteredBroker cb = null;
            BrokerState state = null;
            try {
                shutdownThread = Thread.currentThread();
                storeShutdownStage0 = true;
                cb = Globals.getClusterManager().getLocalBroker();
                try {
                    state = cb.getState();
                    if (state != BrokerState.FAILOVER_STARTED
                       && state != BrokerState.FAILOVER_PENDING
                       && state != BrokerState.FAILOVER_COMPLETE ) {
                        cb.setState(BrokerState.SHUTDOWN_STARTED);
                    }
                } catch (Throwable t) {
                    // Just log the error & continue
                    Globals.getLogger().logStack(
                        Logger.WARNING, BrokerResources.E_SHUTDOWN, t);
                }

                storeShutdownStage1 = true;
                storeShutdownStage0 = false;

                if (getShutdownRemaining() > 0) {
                    logger.log(Logger.INFO,
                            BrokerResources.I_SHUTDOWN_IN_SEC,
                            String.valueOf(getShutdownRemaining()/1000),
                            String.valueOf(getShutdownRemaining()));
                    // notify client
                    List l = Globals.getConnectionManager()
                              .getConnectionList(null);
                    Iterator itr = l.iterator();
                    while (itr.hasNext()) {
                        IMQConnection c = (IMQConnection)itr.next();
                        if (!c.isAdminConnection() && 
                            c.getClientProtocolVersion() >= 
                                  Connection.HAWK_PROTOCOL) {
                             InfoRequestHandler.sendInfoPacket(
                                   InfoRequestHandler.REQUEST_STATUS_INFO, c, 0);
                        }
                    }
                                      
                    synchronized (BrokerStateHandler.this) {
                        try {
                            logger.log(Logger.INFO,
                                  BrokerResources.I_SHUTDOWN_AT,
                                  (new Date(targetShutdownTime)).toString());
                            BrokerStateHandler.this.wait(getShutdownRemaining());
                        } catch (Exception ex) {
                        }
                    }
                }

                // XXX should this be updated to include why ???
                Globals.getLogger().logToAll(Logger.INFO,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.I_SHUTDOWN_BROKER)+"["+requestedBy+":"+Thread.currentThread()+"]");

                if (Broker.getBroker().getDiagInterval() == 0) {
                    // Log diagnostics at shutdown
                    Globals.getLogger().log(Logger.INFO, DiagManager.allToString());
                }

                shuttingDown = true;
                shutdownStarted = true;

                prepareShutdown(failover, false); // in case not called yet

                ServiceManager sm = Globals.getServiceManager();
                sm.stopNewConnections(ServiceType.ADMIN);

                shutdownServices(requestedBy, exitCode, null);

   	        // stop JMX connectors
                if (cleanupJMX) {
	            Agent agent = Globals.getAgent();
	            if (agent != null)  {
	                agent.stop();
		        agent.unloadMBeans();
	            }
                } else {
                    Globals.getLogger().log(Logger.INFO,
                        BrokerResources.I_JMX_NO_SHUTDOWN);
                }
            } catch (Exception ex) {
                Globals.getLogger().logStack(
                    Logger.WARNING, BrokerResources.E_SHUTDOWN, ex); 
                // XXX do we do this if we are already in the exit thread ???
                return 1;
            } finally {
                storeShutdownStage2 = true;
                storeShutdownStage1 = false;
                try {
                    if (cb != null && (
                      state != BrokerState.FAILOVER_STARTED
                      && state != BrokerState.FAILOVER_PENDING
                      && state != BrokerState.FAILOVER_COMPLETE ))
                    {
                        try {
                            if (failover) {
                                cb.setState(BrokerState.SHUTDOWN_FAILOVER);
                            } else {
                                cb.setState(BrokerState.SHUTDOWN_COMPLETE);
                            }
                        } catch (Throwable t) {
                            // Just log the error & continue
                            Globals.getLogger().logStack(
                                Logger.WARNING, BrokerResources.E_SHUTDOWN, t);
                        }
                    }
                    storeShutdownStage2 = false;
                    storeShutdownStage1 = true;
                    // close down the persistence database
                    Globals.releaseStore();
                } catch  (Exception ex) {
                    Globals.getLogger().logStack(
                        Logger.WARNING, BrokerResources.E_SHUTDOWN, ex); 
                    // XXX do we do this if we are already in the exit thread ???
                    return 1;
                }

            }
            Globals.getPortMapper().destroy();

            Globals.getLogger().logToAll(Logger.INFO,
                     BrokerResources.I_SHUTDOWN_COMPLETE);

            if (exitCode == getRestartCode()) {
                Globals.getLogger().log(Logger.INFO,
                    BrokerResources.I_BROKER_RESTART);
	        if (fi.FAULT_INJECTION) {
                    fi.checkFaultAndSleep(
                        FaultInjection.FAULT_RESTART_EXIT_SLEEP,
                        null, true);
	        }
            }

            // XXX do we do this if we are already in the exit thread ???
           return exitCode;
       }
   }


    /**
     * listener who handles sending cluster info back to the client
     */
    static class StateMonitorListener implements ClusterListener
    {
       // send cluster information to all 4.0 or later clients
       void notifyClients() {
            List l = Globals.getConnectionManager()
                              .getConnectionList(null);
            Iterator itr = l.iterator();
            while (itr.hasNext()) {
                IMQConnection c = (IMQConnection)itr.next();
                if (!c.isAdminConnection() &&  
                        c.getClientProtocolVersion() >=
                        Connection.HAWK_PROTOCOL) {
                    InfoRequestHandler.sendInfoPacket(
                        InfoRequestHandler.REQUEST_CLUSTER_INFO,
                        c, 0);
                }
 
            }
       }


       /**
        * Called to notify ClusterListeners when the cluster service
        * configuration. Configuration changes include:
        * <UL><LI>cluster service port</LI>
        *     <LI>cluster service hostname</LI>
        *     <LI>cluster service transport</LI>
        * </UL><P>
        *
        * @param name the name of the changed property
        * @param value the new value of the changed property
        */
        public void clusterPropertyChanged(String name, String value)
        {
            // we dont care
        }

    
    
       /**
        * Called when a new broker has been added.
        * @param brokerSession uid associated with the added broker
        * @param broker the new broker added.
        */
        public void brokerAdded(ClusteredBroker broker, UID brokerSession)
        {
             notifyClients();
        }

    
       /**
        * Called when a broker has been removed.
        * @param broker the broker removed.
        * @param brokerSession uid associated with the removed broker
        */
        public void brokerRemoved(ClusteredBroker broker, UID brokerSession)
        {
             notifyClients();
        }

    
       /**
        * Called when the broker who is the master broker changes
        * (because of a reload properties).
        * @param oldMaster the previous master broker.
        * @param newMaster the new master broker.
        */
        public void masterBrokerChanged(ClusteredBroker oldMaster,
                        ClusteredBroker newMaster)
        {
            // we dont care
        }

    
       /**
        * Called when the status of a broker has changed. The
        * status may not be accurate if a previous listener updated
        * the status for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldStatus the previous status.
        * @param brokerSession uid associated with the change
        * @param newStatus the new status.
        * @param userData data associated with the state change
        */
        public void brokerStatusChanged(String brokerid,
                      int oldStatus, int newStatus, UID brokerSession,
                      Object userData) {

            if (!Globals.getDestinationList().isPartitionMigratable()) {
                return;
            }
            ClusteredBroker cb = Globals.getClusterManager().getBroker(brokerid);
            if (cb.isLocalBroker()) {
                return;
            }
            if (BrokerStatus.getBrokerLinkIsUp(oldStatus) &&
                BrokerStatus.getBrokerLinkIsDown(newStatus)) {
                Globals.getDestinationList().registerPartitionArrivalCheckEvent();
            }
        }

    
       /**
        * Called when the state of a broker has changed. The
        * state may not be accurate if a previous listener updated
        * the state for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldState the previous state.
        * @param newState the new state.
        */
        public void brokerStateChanged(String brokerid,
                      BrokerState oldState, BrokerState newState)
        {
            // we dont care
        }

    
       /**
        * Called when the version of a broker has changed. The
        * state may not be accurate if a previous listener updated
        * the version for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldVersion the previous version.
        * @param newVersion the new version.
        */
        public void brokerVersionChanged(String brokerid,
                      int oldVersion, int newVersion)
        {
            // we dont care
        }
    
       /**
        * Called when the url address of a broker has changed. The
        * address may not be accurate if a previous listener updated
        * the address for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param newAddress the previous address.
        * @param oldAddress the new address.
        */
        public void brokerURLChanged(String brokerid,
                      MQAddress oldAddress, MQAddress newAddress)
        {
             notifyClients();
        }

    
    
    }    
    
}


