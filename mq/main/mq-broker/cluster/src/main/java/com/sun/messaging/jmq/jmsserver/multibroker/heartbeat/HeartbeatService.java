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
 * @(#)HeartbeatService.java	1.24 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.heartbeat;

import java.io.*;
import java.util.*;
import java.net.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.MQThreadGroup;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterListener;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerStatus;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.spi.Heartbeat;
import com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.spi.HeartbeatCallback;

/**
 */
public class HeartbeatService implements HeartbeatCallback, ClusterListener, ConfigListener {
    private static boolean DEBUG = false;

    public static final String SERVICE_NAME = "heartbeat";
    public static final String SERVICE_TYPE = "HEARTBEAT";

    public static final short HEARTBEAT_ALIVE = 0;
    public static final int   HEARTBEAT_PROTOCOL_VERSION = 400;

    public static final String HEARTBEAT_CLASS_PROP = Globals.IMQ+".cluster.heartbeat.class";
    public static final String HEARTBEAT_HOST_PROP = Globals.IMQ+".cluster.heartbeat.hostname";
    public static final String HEARTBEAT_PORT_PROP = Globals.IMQ+".cluster.heartbeat.port";
    public static final String HEARTBEAT_INTERVAL_PROP = Globals.IMQ+".cluster.heartbeat.interval";
    public static final String HEARTBEAT_THRESHOLD_PROP = Globals.IMQ+".cluster.heartbeat.threshold";

    private static final int HEARTBEAT_INTERVAL_DEFAULT =  5; //5 seconds
    private static final int HEARTBEAT_THRESHOLD_DEFAULT = 3; //3 times interval

    private Logger logger = Globals.getLogger();
	private BrokerResources br = Globals.getBrokerResources();

    private ClusterManager clsmgr = null;
    private Heartbeat hb = null;

    private Map<HeartbeatEntry, HeartbeatEntry> brokers = 
        Collections.synchronizedMap(new LinkedHashMap<HeartbeatEntry, HeartbeatEntry>());

    private TimeoutTimer timeoutTimer = null;

    private FaultInjection fi = null;

    public HeartbeatService() throws Exception {
        fi = FaultInjection.getInjection();
        clsmgr = Globals.getClusterManager();
        if (clsmgr == null)  {
            throw new BrokerException("No cluster manager");
        }
        if (!clsmgr.isHA()) {
            throw new BrokerException("Non HA cluster");
        }
        initHeartbeat();
        clsmgr.addEventListener(this);

    }

    private void initHeartbeat() throws ClassNotFoundException, 
                                        InstantiationException, 
                                        IllegalAccessException, 
                                        UnknownHostException, IOException {
        String  cs = Globals.getConfig().getProperty(HEARTBEAT_CLASS_PROP);
        Class c = Class.forName(cs);
        hb = (Heartbeat)c.newInstance();

        hb.setHeartbeatInterval(Globals.getConfig().
            getIntProperty(HEARTBEAT_INTERVAL_PROP, HEARTBEAT_INTERVAL_DEFAULT));
        hb.setTimeoutThreshold(Globals.getConfig().
            getIntProperty(HEARTBEAT_THRESHOLD_PROP, HEARTBEAT_THRESHOLD_DEFAULT));

        int p = Globals.getConfig().getIntProperty(HEARTBEAT_PORT_PROP, 
                                       clsmgr.getMQAddress().getPort());
        String  h = Globals.getConfig().getProperty(HEARTBEAT_HOST_PROP);
        if (h == null) {
            h = Globals.getBrokerHostName(); 
        }
        InetSocketAddress bindEndpoint = 
            new InetSocketAddress(InetAddress.getByName(h), p);

        hb.init(bindEndpoint, this);

        timeoutTimer = new TimeoutTimer();
        timeoutTimer.setPriority(Thread.MAX_PRIORITY);
		timeoutTimer.setDaemon(true);
        timeoutTimer.start();

        /*
        HashMap m = new HashMap();
        m.put("hostname", bindEndpoint.getAddress().getHostAddress());
        Globals.getPortMapper().addService(SERVICE_NAME, hb.getProtocol(), 
                                           SERVICE_TYPE, p, m);
        */
        Object[] args = { SERVICE_NAME + (hb.getName() == null ? "":" ["+hb.getName()+"]"),
                          hb.getProtocol() +" ( " + bindEndpoint + " )",
                          Integer.valueOf(1), Integer.valueOf(1) };
        logger.log(Logger.INFO, BrokerResources.I_SERVICE_START, args);
    }

    public String getHeartbeatHostAddress() {
        return hb.getBindEndpoint().getAddress().getHostAddress();
    }

    public int getHeartbeatPort() {
        return hb.getBindEndpoint().getPort();
    }

    public int getHeartbeatInterval() {
        return hb.getHeartbeatInterval();
    }

    /**
     */
    public void stopService() {
        try {
        hb.stop();
        } catch (IOException e) {
        logger.logStack(logger.WARNING,  
               br.getKString(br.W_CLUSTER_HB_STOP_SERVICE_EXCEPTION), e);
        }
        timeoutTimer.destroy();
        clsmgr.removeEventListener(this);
    }

    class TimeoutTimer extends Thread  {

        private Object lock = new Object();
        private boolean stopped = false;

        public TimeoutTimer() { 
            super(new MQThreadGroup("HeartbeatService", logger,
                  br.getKString(br.W_UNCAUGHT_EXCEPTION_IN_THREAD)),
                  "Heartbeat-Timeout-Timer");
            setPriority(Thread.MAX_PRIORITY);
            setDaemon(true);
        }

        public void run() {

            HeartbeatEntry[] hbes = null;
            HeartbeatEntry hbe = null;            
            HeartbeatEntry entry = null;            

            
            while (!stopped) {
            try {

                synchronized(lock) {
                    if (stopped) break;
                    try {
                    lock.wait(1000);
                    } catch (InterruptedException e) { /* Ignored */ }
                    if (stopped) break;
                    synchronized(brokers) {
                        Set<HeartbeatEntry> ks = brokers.keySet();
                        hbes = (HeartbeatEntry[])ks.toArray(new HeartbeatEntry[ks.size()]);
                    }
                }
                if (DEBUG) {
                    logger.log(logger.DEBUGHIGH, getName()+ " checking "+hbes.length + " endpoints"); 
                }
                for (int i = 0; i < hbes.length; i++) {
                    hbe = hbes[i];
                    long timeout = (hbe.heartbeatInterval * hb.getTimeoutThreshold()) * 1000L;
                    if (hbe.lastTimestamp < (System.currentTimeMillis() - timeout)) { 
                        if (hbe.indoubtTimestamp < (System.currentTimeMillis() - timeout)) {
                            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_HB_TIMEOUT, hbe)); 
                            if (!Globals.getSFSHAEnabled()) {
                                ClusteredBroker cb = clsmgr.getBroker(hbe.brokerID);
                                cb.setBrokerInDoubt(true,  new UID(hbe.sessionUID));
                            }
                            entry  = brokers.get(hbe);
                            if (entry != null) {
                                entry.indoubtTimestamp = System.currentTimeMillis();
                            }
                        }
                    }
                }

            } catch (Throwable t) {
            logger.logStack(logger.WARNING,  getName()+": "+ t.getMessage(), t);
            continue;
            }
            } //while

            if (!stopped) {
            logger.log(logger.WARNING, br.getKString(br.M_THREAD_EXITING, getName()));
            } else  if (DEBUG) {
            logger.log(logger.INFO, br.getKString(br.M_THREAD_EXITING, getName()));
            }
        }

        public void destroy() {
            synchronized(lock) {
                stopped = true;
                lock.notifyAll();
            }
        }
    }


    private static class HeartbeatEntry { 
        String brokerID = null ;
        long sessionUID = 0;
        InetSocketAddress endpoint = null;
        InetSocketAddress sender = null;
        int heartbeatInterval = 0; 
        long lastTimestamp  = 0;      
        long lastSequence = 0;
        int dataLength = 0;
        long indoubtTimestamp = 0;
        GPacket gp = null;

        public boolean equals(Object obj) {
            if (obj ==  null || !(obj instanceof HeartbeatEntry)) {
                return false;
            }
            HeartbeatEntry hbe = (HeartbeatEntry)obj;
            return brokerID.equals(hbe.brokerID) && sessionUID == hbe.sessionUID;
        }
    
        public int hashCode() {
            return brokerID.hashCode() + String.valueOf(sessionUID).hashCode(); 
        }

        public String toString() {
            return ((endpoint == null) ? "":endpoint.toString())+
                   " [brokerID="+brokerID+", brokerSession="+String.valueOf(sessionUID)+"] (seq#="+
                    lastSequence+", ts="+lastTimestamp+", interval="+heartbeatInterval+", len="+dataLength+")"+
                    ((sender == null) ? "": " sender="+sender.toString());
        }
        public String toStringKS() {
            return  "#"+lastSequence+" [brokerID="+brokerID+", brokerSession="+String.valueOf(sessionUID)+"]";
        }
        public String toStringK() {
            return  "[brokerID="+brokerID+", brokerSession="+String.valueOf(sessionUID)+"]";
        }
    }


    private static final int MAX_PORTMAPPER_CONNECT_RETRY = 3;

    private synchronized void addBroker(HAClusteredBroker cb, UID brokerSession, BrokerInfo brokerInfo) {

        HeartbeatEntry hbe = null;

        ArrayList<Long> brokerSessions =  new ArrayList();
        synchronized(brokers) {
            Iterator<HeartbeatEntry> itr = brokers.keySet().iterator();
            while (itr.hasNext()) {
                hbe = itr.next();
                if (cb.getBrokerName().equals(hbe.brokerID)) {
                    brokerSessions.add(Long.valueOf(hbe.sessionUID));
                }
            }
        }
        Iterator<Long> itr = brokerSessions.iterator();
        while (itr.hasNext()) {
            removeBroker(cb, new UID(itr.next().longValue()));
        }

        hbe = new HeartbeatEntry();
        try {
            hbe.brokerID = cb.getBrokerName();
            hbe.sessionUID = brokerSession.longValue();

            hbe.endpoint = new InetSocketAddress(
                               InetAddress.getByName(brokerInfo.getHeartbeatHostAddress()), 
                               brokerInfo.getHeartbeatPort());

            //my heartbeat pkt 
            HeartbeatInfo hbi = HeartbeatInfo.newInstance();
            HAClusteredBroker lcb = (HAClusteredBroker)clsmgr.getLocalBroker();
            hbi.setBrokerID(lcb.getBrokerName());
            hbi.setBrokerSession(lcb.getBrokerSessionUID().longValue());
            hbi.setBrokerAddress((BrokerMQAddress)lcb.getBrokerURL());
            hbi.setToBrokerID(cb.getBrokerName());
            hbi.setToBrokerSession(brokerSession.longValue());
            hbi.setSequence(0);
            hbe.gp = hbi.getGPacket();

            //calculate length for receiving
            hbi = HeartbeatInfo.newInstance();
            hbi.setBrokerID(cb.getBrokerName());
            hbi.setBrokerSession(brokerSession.longValue());
            hbi.setBrokerAddress((BrokerMQAddress)cb.getBrokerURL());
            hbi.setToBrokerID(lcb.getBrokerName());
            hbi.setToBrokerSession(lcb.getBrokerSessionUID().longValue());
            hbe.dataLength = HeartbeatInfo.toByteArray(hbi.getGPacket()).length;

            hbe.heartbeatInterval = brokerInfo.getHeartbeatInterval();
            hbe.lastTimestamp = System.currentTimeMillis();
            hbe.lastSequence = 0;
 
            brokers.put(hbe, hbe);

            hb.addEndpoint(hbe, hbe.endpoint, hbe.dataLength+500); //pad some bytes for safty
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_HB_ADD_ENDPOINT, hbe));

        } catch (Exception e) {
            logger.logStack(Logger.ERROR, br.getKString(
                   br.E_CLUSTER_HB_ADD_ENDPOINT_FAILED, hbe), e);
        }
    }

    public void removeHeartbeatEndpoint(HAClusteredBroker cb, UID brokerSession) { 
        removeBroker(cb, brokerSession);
    }

    private synchronized void removeBroker(HAClusteredBroker cb, UID brokerSession) {
        HeartbeatEntry hbe = new HeartbeatEntry();

        try {
            hbe.brokerID = cb.getBrokerName();
            hbe.sessionUID = brokerSession.longValue();
            HeartbeatEntry existed = brokers.remove(hbe);    
            if (existed == null) {
                logger.log(logger.WARNING, br.getKString(
                       br.W_CLUSTER_HB_REM_ENDPOINT_NOTFOUND, hbe));
                return;
            }
            if (hb.removeEndpoint(existed, existed.endpoint)) {
                synchronized(brokers) {
                    Iterator<HeartbeatEntry> itr = brokers.keySet().iterator();
                    while (itr.hasNext()) {
                        HeartbeatEntry entry = itr.next();     
                        if (cb.getBrokerName().equals(entry.brokerID) &&
                            entry.endpoint.equals(existed.endpoint)) {
                            itr.remove();
                        }
                    }
                }
            }
            logger.log(logger.INFO,  br.getKString(br.I_CLUSTER_HB_REM_ENDPOINT, existed));

        } catch (Exception e) {
            logger.logStack(logger.ERROR, br.getKString(
                   br.E_CLUSTER_HB_REM_ENDPOINT_FAILED, hbe), e);
        }
    }

    private void unsuspect(HAClusteredBroker cb, UID brokerSession) {
        HeartbeatEntry hbe = new HeartbeatEntry();
        hbe.brokerID = cb.getBrokerName();
        hbe.sessionUID = brokerSession.longValue();
        HeartbeatEntry existed = brokers.get(hbe);
        if (existed == null) {
            if (DEBUG) {
            logger.log(logger.INFO, "Unsuspect " + hbe+ " not exist");
            }
            return;
        }
        synchronized(existed) {
        existed.lastTimestamp = System.currentTimeMillis();
        existed.lastSequence = 0;
        }
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_HB_UNSUSPECTED, existed));
    }

    public boolean isHeartbeatTimedout(String brokerid, UID brokerSession) {
        HeartbeatEntry hbe = new HeartbeatEntry();
        hbe.brokerID = brokerid;
        hbe.sessionUID = brokerSession.longValue();
        logger.log(logger.INFO, br.getKString(br.I_CHECK_HEARTBEAT, hbe.toString()));
        HeartbeatEntry existed = brokers.get(hbe);
        if (existed == null) {
            logger.log(logger.INFO, 
                br.getKString(br.I_HEARTBEAT_ENTRY_NOTFOUND, hbe.toString()));
            return true;
        }
        long timestamp = 0L;
        int interval = 0;
        synchronized(existed) {
            timestamp = existed.lastTimestamp;
            interval = existed.heartbeatInterval;
        }
        long timeout = (interval * hb.getTimeoutThreshold()) * 1000L;
        if (timestamp < (System.currentTimeMillis() - timeout)) {
            return true;
        }
        return false;
    }


    /***************************************************************************
     ***************** Implement HeartbeatCallback Interface *******************
     ***************************************************************************/                           


    /**
     * The implementation of this method could check the validity
     * of the received data and throw IOException to indicate the
     * data should be discarded - that is it should not be counted
     * in calculating timeout
     *
     * @param sender The remote address where the data received from 
     * @param data The data received 
     *
     * @throws IOException if the data should be discarded
     */
    public void
    heartbeatReceived(InetSocketAddress sender, byte[] data) throws IOException {

        if (data == null) {
            if (DEBUG) {
            logger.log(logger.INFO, "HEARTBEAT: Ignore null data from " + sender);
            }
            throw new IOException("No data");
        }

        HeartbeatInfo hbi = null;
        HeartbeatEntry hbe = new HeartbeatEntry();
        try {
            hbi = HeartbeatInfo.newInstance(data);
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(logger.INFO, "HEARTBEAT: Ignore data from "+sender+" because "+e.getMessage() , e);
            }
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException(e.getMessage());
        }
        hbe.brokerID = hbi.getBrokerID();
        hbe.sessionUID = hbi.getBrokerSession();
        hbe.lastSequence = hbi.getSequence();

        HeartbeatEntry assigned = brokers.get(hbe); 

        if (assigned == null) {
            if (DEBUG) {
            logger.log(logger.INFO, "HEARTBEAT: Ignore heartbeat from "+sender+" "+hbe.toStringKS()); 
            }
            throw new IOException("Ignore heartbeat because of not found "+hbe.toStringK());
        }

        HAClusteredBroker lcb = (HAClusteredBroker)clsmgr.getLocalBroker();
        if (!hbi.getToBrokerID().equals(lcb.getBrokerName()) ||
            hbi.getToBrokerSession() != lcb.getBrokerSessionUID().longValue()) {
            if (DEBUG) {
            logger.log(logger.INFO, "HEARTBEAT: Ignore heartbeat not for me. "+ hbi);
            }
            throw new IOException("Ignore heartbeat not for me. " + hbi);
        }

        boolean order = false;
        synchronized(assigned) {
        order = hbe.lastSequence > assigned.lastSequence;
        }
        if (!order) {
           if (DEBUG) {
           logger.log(logger.INFO, "HEARTBEAT: Ignore duplicate or out-of-order heartbeat "+ 
                      hbe.toStringKS()+ "["+assigned+"] from " + sender);
           }
           throw new IOException("Ignore duplicate or out-of-order heartbeat "+hbe.toStringKS());
        }

        if (DEBUG) {
        logger.log(logger.INFO, "HEARTBEAT: Received heartbeat #"+hbe.lastSequence+ " from " +assigned);
        }

        if (fi.FAULT_INJECTION) {
            HashMap fips = new HashMap();
            fips.put(FaultInjection.BROKERID_PROP, hbi.getBrokerID());
            if (fi.checkFault(FaultInjection.FAULT_HB_RECV, null) ||
                fi.checkFault(FaultInjection.FAULT_HB_RECV_BROKERID, fips)) {
                logger.log(logger.INFO, "DISCARD heartbeat from " + assigned+" because of FAULT");
                return;
            }
        }

        synchronized(assigned) {
        assigned.lastTimestamp = System.currentTimeMillis(); 
        assigned.lastSequence = hbi.getSequence();
        }
        assigned.sender = sender;
    }


    /**
     * This method should be called before each send to the endpoint
     *
     * @param endpoint The endpoint 
     * @param privData The opaque data associated with this endpoint 
     *
     * @return array of bytes for sending to the endpoint
     *
     * @throws IOException
     */
    public byte[]
    getBytesToSend(Object key, InetSocketAddress endpoint) throws IOException {

        HeartbeatEntry hbe = (HeartbeatEntry)key;

        assert ( hbe.endpoint.equals(endpoint) );

        if (fi.FAULT_INJECTION) {
            HashMap fips = new HashMap();
            fips.put(FaultInjection.BROKERID_PROP, hbe.brokerID);
            if (fi.checkFault(FaultInjection.FAULT_HB_SEND, null) ||
                fi.checkFault(FaultInjection.FAULT_HB_SEND_BROKERID, fips)) {
                logger.log(logger.INFO, "NOT SEND heartbeat to " + hbe+" because of FAULT");
                return null;
            }
        }

        hbe.gp.setSequence(hbe.gp.getSequence()+1);
        return HeartbeatInfo.toByteArray(hbe.gp);
    }


    public void
    heartbeatIOException(Object key, InetSocketAddress endpoint, IOException reason) {
        heartbeatTimeout(key, endpoint, reason);
    }

    /**
     */
    public void
    heartbeatTimeout(Object key, InetSocketAddress endpoint, IOException reason) {
        HeartbeatEntry hbe = (HeartbeatEntry)key;

        assert ( endpoint.equals(hbe.endpoint) );

        HeartbeatEntry entry  = brokers.get(hbe);
        if (entry == null) {
            if (DEBUG) {
            logger.logStack(logger.INFO, "HEARTBEAT: NotFound: Heart beat timeout because "+
                   (reason == null ? "": reason.getMessage())+": "+hbe+". Ignore.", reason);
            }
            return;
        }

        //XXX check entry == hbe ?

        long indoubtTimestamp = 0;
        int heartbeatInterval = 0;
        synchronized (entry) {
            indoubtTimestamp = entry.indoubtTimestamp;
            heartbeatInterval = entry.heartbeatInterval;
        }
       
        long timeout = (heartbeatInterval * hb.getTimeoutThreshold()) * 1000L;
        if (indoubtTimestamp < (System.currentTimeMillis() - timeout)) {
            if (DEBUG) {
            logger.logStack(logger.INFO, "Heart beat timeout because "+
                            (reason == null ? "":reason.getMessage())+": "+entry, reason);
            } else {
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_HB_TIMEOUT, entry)+": "+
                                       (reason == null ? "": reason.getMessage()), reason); 
            }
            if (!Globals.getSFSHAEnabled()) {
                HAClusteredBroker cb = (HAClusteredBroker)clsmgr.getBroker(entry.brokerID);
                cb.setBrokerInDoubt(true, new UID(hbe.sessionUID));
            }
            synchronized (entry) {
                entry.indoubtTimestamp = System.currentTimeMillis();
            }
        }
    }


   /***************************************************************************
    ***************** Implement ClusterListener Interface *********************
    ***************************************************************************/                           
    

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
    public void clusterPropertyChanged(String name, String value) {
    //do nothing
    }


   /**
    * Called when a new broker has been added.
    * @param broker the new broker added.
    */
    public void brokerAdded(ClusteredBroker broker, UID brokerSession) {
    }


   /**
    * Called when a broker has been removed.
    * @param broker the broker removed.
    */
    public void brokerRemoved(ClusteredBroker broker, UID brokerSession) {
    }

   /**
    * Called when the broker who is the master broker changes
    * (because of a reload properties).
    * @param oldMaster the previous master broker.
    * @param newMaster the new master broker.
    */
    public void masterBrokerChanged(ClusteredBroker oldMaster,
                    ClusteredBroker newMaster) {
    //do nothing
    }

   /**
    * Called when the status of a broker has changed. The
    * status may not be accurate if a previous listener updated
    * the status for this specific broker.
    * @param userData optional data associated with the change
    * @param brokerid the name of the broker updated.
    * @param brokerSession uid associated with the changed broker
    * @param userData data associated with the state change
    * @param oldStatus the previous status.
    * @param newStatus the new status.
    */
    public void brokerStatusChanged(String brokerid,
                  int oldStatus, int newStatus, UID brokerSession, Object userData) {
        HAClusteredBroker cb = (HAClusteredBroker)clsmgr.getBroker(brokerid); 
        if (cb.isLocalBroker()) return;

        if (BrokerStatus.getBrokerIsDown(oldStatus) ||
            BrokerStatus.getBrokerLinkIsDown(oldStatus)) {
            if (BrokerStatus.getBrokerIsUp(newStatus) &&
                BrokerStatus.getBrokerLinkIsUp(newStatus)) {
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_HB_START_HEARTBEAT, brokerid));
                addBroker(cb, brokerSession, (BrokerInfo)userData);
            }
        }
        if (BrokerStatus.getBrokerInDoubt(oldStatus)) {
            if (BrokerStatus.getBrokerNotInDoubt(newStatus)) {
                logger.log(logger.INFO,  br.getKString(br.I_CLUSTER_HB_UNSUSPECT, brokerid));
                unsuspect(cb, brokerSession);
            }
        }
        if (BrokerStatus.getBrokerIsDown(newStatus)) {
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_HB_STOP_HEARTBEAT,
                                    brokerid+"[BrokerSession:"+brokerSession+"]"));
            removeBroker(cb, brokerSession);
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
                  BrokerState oldState, BrokerState newState) {
        ClusteredBroker cb = clsmgr.getBroker(brokerid);
        if (cb.isLocalBroker() &&
            (newState == BrokerState.SHUTDOWN_COMPLETE ||
             newState == BrokerState.SHUTDOWN_FAILOVER   )) {

                stopService();
        }

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
                  int oldVersion, int newVersion) {
    //do nothing
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
                  MQAddress oldAddress, MQAddress newAddress) {
    //do nothing
    }


   /***************************************************************************
    ***************** Implement ConfigListener Interface  *********************
    ***************************************************************************/                           

    public void validate(String name, String value) throws PropertyUpdateException {
        if (name.equals(HEARTBEAT_HOST_PROP) || name.equals(HEARTBEAT_PORT_PROP)) {
            throw new PropertyUpdateException(br.getKString(
                      br.X_DYNAMIC_UPDATE_PROPERTY_NOT_SUPPORT, name));
        }

        if (name.equals(HEARTBEAT_INTERVAL_PROP)) {
            throw new PropertyUpdateException(br.getKString(
                      br.X_DYNAMIC_UPDATE_PROPERTY_NOT_SUPPORT, name));
        }

        if (name.equals(HEARTBEAT_THRESHOLD_PROP)) { 
            throw new PropertyUpdateException(br.getKString(
                      br.X_DYNAMIC_UPDATE_PROPERTY_NOT_SUPPORT, name));
        }
    }

    public boolean update(String name, String value) {
        return true;
    }
}
