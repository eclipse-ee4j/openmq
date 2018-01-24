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
 * @(#)IMQConnection.java	1.106 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.net.*;
import java.util.*;

import com.sun.messaging.jmq.util.ServiceState;

import com.sun.messaging.jmq.jmsserver.service.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

import java.security.Principal;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.auth.JMQAccessControlContext;
import com.sun.messaging.jmq.auth.api.server.AccessControlContext;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;

import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.RollbackReason;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Producer;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ProducerSpi;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.util.lists.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;

public abstract class IMQConnection extends Connection 
        implements com.sun.messaging.jmq.util.lists.EventListener
{

    public static final boolean DEBUG_TXN = Globals.getConfig().getBooleanProperty(
                                           Globals.IMQ + ".cluster.debug.txn");

    private String destroyReason = null;
    protected int msgsToConsumer = 0;
    protected int msgsIn = 0;

    private int pauseFlowCnt = 0;
    private int resumeFlowCnt =0;

    boolean BLOCKING = false; 

    private Set tmpDestinations = Collections.synchronizedSet(new HashSet());

    // Known data which may be tagged on a connection
    public static final String CLIENT_ID = "client id";
    public static final String TRANSACTION_LIST = "transaction";
    public static final String TRANSACTION_IDMAP = "tidmap";
    public static final String TRANSACTION_CACHE = "txncache";
    public static final String USER_AGENT = "useragent";


    /**
     * overriding packet dump flag
     */
    static boolean DEBUG = Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".packet.debug.info");

   // XXX-CODE TO OVERRIDE BEHAVIOR OF PACKETS

   // to override the type of packet ... 
   //  jmq.packet.[ctrl|read|fill].override = [direct/heap/unset]
   //        direct -> always use direct packets
   //        heap -> always use heap packets
   //        unset -> current behavior
   //  fill is the "waitingForWrite packet"

    public void setDestroyReason(String r) {
        this.destroyReason = r;
    }

    public String getDestroyReason() {
        return destroyReason;
    }

    public void debug(String prefix)
    {
        if (prefix == null)
            prefix = "";
        dumpState();
       Iterator itr = sessions.values().iterator();
       while (itr.hasNext()) {
            ((Session)itr.next()).debug("  ");
       }
    }

    /**
     * Metric counters
     */
    protected MetricCounters counters = new MetricCounters();

    /**
     * connection information (used by admin)
     */
    ConnectionInfo coninfo;

    byte[] empty = {0};
    /**
     * remote IP address (retrieve from hello protocol packet)
     */
    byte[] remoteIP = empty;

    protected PartitionedStore pstore = null;

    /**
     * constructor
     */
    public IMQConnection(Service svc) throws BrokerException
    {
        super(svc);
        setConnectionUID(new ConnectionUID());
        accessController = AccessController.getInstance(svc.getName(),
                                                        svc.getServiceType());
        this.sessions = new HashMap(); // current session list
    }

    public UID attachStorePartition(UID storeSession) throws BrokerException {
        this.pstore = Globals.getDestinationList().
            assignStorePartition(getService().getServiceType(), 
                       getConnectionUID(), storeSession);
        return pstore.getPartitionID();
    }

    public PartitionedStore getPartitionedStore() { 
        return pstore;
    }

// -------------------------------------------------------------------------
//   General connection information and metrics
// -------------------------------------------------------------------------
    public void dumpState() {
        logger.log(Logger.INFO,
                "Dumping state of " + this);
        logger.log(Logger.INFO,
                "\tsessions = " + sessions.size());
        logger.log(Logger.INFO,
                "\tbusySessions = " + busySessions.size());
        logger.log(Logger.INFO,
                "\trunningMsgs = " + runningMsgs);
        logger.log(Logger.INFO,
                "\tpaused = " + paused);
        logger.log(Logger.INFO,
                "\twaitingForResumeFlow = " + waitingForResumeFlow);
    }

   public boolean isBlocking() {
        return BLOCKING;
   }

    public void dump() {
       logger.log(Logger.INFO,"DUMPING CONNECTION " + this);
       dumpState();
       logger.log(Logger.INFO,"Sessions (size) :" + sessions.size());
       logger.log(Logger.INFO,"Sessions (list) :" + sessions);
       logger.log(Logger.INFO,"Busy (size) :" + busySessions.size());
       logger.log(Logger.INFO,"Busy (list) :" + busySessions);
       logger.log(Logger.INFO,"----------- sessions -----------");
       Iterator itr = sessions.values().iterator();
       while (itr.hasNext()) {
            ((Session)itr.next()).dump("\t");
       }
       logger.log(Logger.INFO,"----------- busy sessions -----------");
       itr = sessions.values().iterator();
       while (itr.hasNext()) {
            logger.log(Logger.INFO, "\t" + ((Session)itr.next()).toString());
       }
    }


    /** 
     * The debug state of this object
     */
    public synchronized Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        ht.put("pauseFlowCnt", String.valueOf(pauseFlowCnt));
        ht.put("resumeFlowCnt", String.valueOf(resumeFlowCnt));
        ht.put("producerCnt", String.valueOf(producers.size()));
        if (producers.size() > 0) {
            Vector v = new Vector();
            Iterator itr = producers.keySet().iterator();
            while (itr.hasNext()) {
                ProducerUID p = (ProducerUID)itr.next();
                v.add(p.toString());
            }
            ht.put("producers", v);
        }
        ht.put("msgsToConsumer", String.valueOf(msgsToConsumer));
        ht.put("sessionCnt", String.valueOf(sessions.size()));
        if (sessions.size() > 0) {
            Vector v = new Vector();
            Iterator itr = sessions.values().iterator();
            while (itr.hasNext()) {
                Session p = (Session)itr.next();
                v.add(p.getSessionUID().toString());
            }
            ht.put("sessions", v);
        }
        ht.put("busySessionCnt", String.valueOf(busySessions.size()));
        if (busySessions.size() > 0) {
             Vector v = new Vector();
             Iterator itr = busySessions.iterator();
             while (itr.hasNext()) {
                Session p = (Session)itr.next();
                v.add(p.getSessionUID().toString());
             }
             ht.put("busySessions", v);
        }
        ht.put("tempDestCnt", String.valueOf(tmpDestinations.size()));
        if (tmpDestinations.size() > 0) {
             Vector v = new Vector();
             Iterator itr = tmpDestinations.iterator();
             while (itr.hasNext()) {
                DestinationUID p = (DestinationUID)itr.next();
                v.add(p.toString());
             }
             ht.put("tempDestinations", v);
        }
        ht.put("runningMsgs", String.valueOf(runningMsgs));
        ht.put("paused", String.valueOf(paused));
        ht.put("waitingForResumeFlow", String.valueOf(waitingForResumeFlow));
        ht.put("flowCount", String.valueOf(flowCount));
        ht.put("sentCount", String.valueOf(sent_count));
        ht.put("userName", getUserName());
        ht.put("remoteString", getRemoteConnectionString());
//        ht.put("read_assigned", read_assigned.toString());
//        ht.put("write_assigned", write_assigned.toString());
        return ht;
    }


    public Vector getDebugMessages(boolean full) {
        Vector ht = new Vector();
        return ht;
       
    }

    /**
     * Remember IP address of remote end of connection
     */
    public void setRemoteIP(byte[] remoteIP)
    {
        this.remoteIP = remoteIP;
        if (coninfo != null)
            coninfo.remoteIP = remoteIP;
    }

    /**
     * Return IP address of remote end of connection. May be null if
     * if IP address is unknown.
     */ 
    public byte[] getRemoteIP()
    {
        return this.remoteIP;
    }

    public void resetCounters()
    {
        counters = new MetricCounters();
    }

    public ConnectionInfo getConnectionInfo() {
        if (coninfo == null) {
            coninfo = new ConnectionInfo();
            coninfo.id = (conId == null ? empty:conId.toString().getBytes());
            coninfo.remoteIP = remoteIP;
            coninfo.service = service.getName();
        }
        coninfo.user = null;
        Principal principal = null;
        try {
            if ((principal = getAuthenticatedName()) != null) {
                coninfo.user = principal.getName();
            }
        }
        catch (BrokerException e) {
            logger.log(Logger.DEBUG,"Exception getting authentication name "
                + conId );
                    
            coninfo.user = e.getMessage();
        }

        coninfo.uuid = this.conId.longValue();

        coninfo.metrics = (MetricCounters)counters.clone();
        coninfo.clientID = (String)getClientData(CLIENT_ID);
        coninfo.nproducers = producers.size();

        if ((coninfo.userAgent = (String)getClientData(USER_AGENT)) == null) {
            coninfo.userAgent = "";
        }

        int cnt = 0;
        synchronized(sessions) {
            Iterator itr = sessions.values().iterator();
            while (itr.hasNext()) {
               cnt += ((Session)itr.next()).getConsumerCnt();
            }
        }
        coninfo.nconsumers = cnt;

        return coninfo;
    }

    /**
     * Retrieve metric counters
     */
    public MetricCounters getMetricCounters() {
        return counters;
    }

    public abstract boolean useDirectBuffers();

// -------------------------------------------------------------------------
//   Basic Operation implementation
// -------------------------------------------------------------------------

    public boolean isValid() {
        return getConnectionState() < STATE_DESTROYING;
    }
    public boolean isAuthenticated() {
        return getConnectionState() ==  STATE_AUTHENTICATED;
    }
    public boolean isStarted(){
        return getConnectionState() > STATE_INITIALIZED;
    }
 
    public boolean isBeingDestroyed() {
        return getConnectionState() > STATE_AUTHENTICATED;
    }

// -------------------------------------------------------------------------
//  Object Methods (hashCode, toString, etc)
// -------------------------------------------------------------------------
    /**
     * Compares connections to each other or connections
     * to connection ID's
     */
    public boolean equals(Object obj) {
        if (obj instanceof Connection) {
             return ((Connection) obj).getConnectionUID().equals(
                      this.getConnectionUID());
        }
        return false;
    }

    /**
     * calculates hashCode for the object
     */
    public int hashCode() {
        if (conId == null) 
            return 0;
        return conId.hashCode();
    }

    /**
     * default toString method, sub-classes should override
     */
    public String toString() {
        return "IMQConn[" +getConnectionStateString(state) 
                   +","+getRemoteConnectionString() + "," 
                   + localServiceString() +"]";
    }

    /**
     * methods used by debugging, subclasses should override
     */
    public String toDebugString() {
        return super.toString() + " state: " + state;
    }

    public abstract String remoteHostString();
    public abstract String getRemoteConnectionString();
    protected abstract String localServiceString();

    /**
     * Get the name of the user that is authenticated on this connection
     */
    public String getUserName() {
        String userString = "???";
        try {
            Principal principal = getAuthenticatedName();
            if (principal != null) {
                userString = principal.getName();
            }
        } catch (BrokerException e) { 
            logger.log(Logger.DEBUG,"Exception getting authentication name "
                + conId, e);
        }
                    
        return userString;
    }

    public String userReadableString() {
        return getRemoteConnectionString() + "->" + localServiceString();
    }


// -------------------------------------------------------------------------
//   Basic Connection Management
// -------------------------------------------------------------------------

    protected boolean runningMsgs = false;
    protected boolean paused = false;
    protected boolean waitingForResumeFlow = false;
    protected int flowCount = 0; // 0 == unlimited
    protected int sent_count = 0;


    public void setFlowCount(int count) {
       flowCount = count;
    }

    public int getFlowCount() {
        return flowCount; 
    }
            
    public  void resumeFlow(int count) {
        sent_count = 0;
        if (count != -1) setFlowCount(count);
        synchronized (stateLock) {
            waitingForResumeFlow = false;
            resumeFlowCnt ++;
            checkState();
        }
    }

    public void haltFlow() {
        synchronized (stateLock) {
            waitingForResumeFlow = true;
            pauseFlowCnt ++;
            checkState();
        }
    }

    /**
     * start sending JMS messages to the connection
     */
    public void startConnection() {
        synchronized (stateLock) {
            runningMsgs = true;
            checkState();
            Globals.getConnectionManager().
                    getConsumerInfoNotifyManager().connectionStarted(this);
        }
    }

    public boolean isConnectionStarted() {
        synchronized (stateLock) {
            return runningMsgs;
        }
    }

    /**
     * stop sending JMS messages to the connection
     * (does not stop control messages)
     */
    public void stopConnection() {
        synchronized (stateLock) {
            runningMsgs = false;
            checkState();
        }
    }


    public void suspend() {
        synchronized (stateLock) {
            paused = true;
            checkState();
        }
    }

    public void resume() {
        synchronized (stateLock) {
            paused = false;
            checkState();
        }
    }

    public synchronized void cleanupConnection() { 
        boolean successful = false;
        try {
            if (state >= Connection.STATE_CLEANED) {
                wakeup();
                successful = true;
                return ;
             }
            if (state < Connection.STATE_CLEANED)
                state = Connection.STATE_CLEANED;
            stopConnection();
            cleanup(false);
            wakeup();
            successful = true;
        } finally {
           if (!successful)
                state = Connection.STATE_AUTHENTICATED;
           
        }

    }

    protected void cleanup(boolean shutdown) {
        try {
            if (!shutdown) {
                cleanUpConsumers();
                cleanUpProducers();
            }
            cleanUpTransactions();
            lockToSession.clear();
            sessions.clear();
            busySessions.clear();
            cleanUpTempDest(shutdown);
	    cleanupControlPackets(shutdown);
        } finally {
            try {
                Globals.getDestinationList().unassignStorePartition(getConnectionUID(), pstore);
            } finally {
                Globals.getClusterBroadcast().connectionClosed(getConnectionUID(),
                    isAdminConnection());
            }
        }
    }

    protected abstract void cleanupControlPackets(boolean shutdown);

    private synchronized void cleanUpTransactions() 
    {
        logger.log(Logger.DEBUG, "Cleaning up transactions on connection " + this);

        List conlist = (List)getClientData(TRANSACTION_LIST);
        if (conlist != null) {
            boolean xaretainall = Globals.getConfig().getBooleanProperty(
                    TransactionList.XA_TXN_DETACHED_RETAINALL_PROP, false);
            ArrayList timeoutTIDs = new ArrayList();
            TransactionUID tid = null; 
            boolean xaretainallLogged = false;
            TransactionHandler rollbackHandler = (TransactionHandler)
                                   Globals.getPacketRouter(0).getHandler(
                                          PacketType.ROLLBACK_TRANSACTION);
            TransactionList[] tls = Globals.getDestinationList().getTransactionList(pstore);
            TransactionList tl = tls[0];
            TransactionUID[] tuids = (TransactionUID[])conlist.toArray(
                                     new TransactionUID[conlist.size()]); 
            for (int i = 0; i < tuids.length; i ++) {
                tid = (TransactionUID) tuids[i];
                TransactionState ts = tl.retrieveState(tid);
                if (ts == null) {
                    // nothing to do if no transaction state
                    continue;
                }
                int tstate = ts.getState();
                if (tstate == TransactionState.PREPARED &&
                    ts.getOnephasePrepare()) {
                    ts.detachedFromConnection();
                    timeoutTIDs.add(tid); 
                    String[] args = { ""+tid+"(XID="+ts.getXid()+")",
                                      TransactionState.toString(tstate)+"[onephase=true]",
                                      getConnectionUID().toString() };
                    logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                               BrokerResources.I_CONN_CLEANUP_KEEP_TXN, args));
                    continue;
                }
                if (ts.getXid() != null) {
                    if (xaretainall) {
                        if (!xaretainallLogged) {
                        logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                                   BrokerResources.I_CONN_CLEANUP_RETAIN_XA));
                        xaretainallLogged = true;
                        }
                        continue;
                    }
                    if(tstate > TransactionState.COMPLETE) {
                       String[] args = { ""+tid+"(XID="+ts.getXid()+")",
                                         TransactionState.toString(tstate),
                                         getConnectionUID().toString() };
                        logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                                          BrokerResources.I_CONN_CLEANUP_KEEP_TXN, args));
                        continue;
                    }
                    if (tstate == TransactionState.INCOMPLETE ||
                        tstate == TransactionState.COMPLETE ) {
                        ts.detachedFromConnection();
                        timeoutTIDs.add(tid); 
                        String[] args = { ""+tid+"(XID="+ts.getXid()+")",
                                          TransactionState.toString(tstate),
                                          getConnectionUID().toString() };
                        logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                                          BrokerResources.I_CONN_CLEANUP_KEEP_TXN, args));
                        continue;
                    }
                }
                if (tstate == TransactionState.PREPARED ||
                    tstate == TransactionState.COMMITTED ||
                    tstate == TransactionState.ROLLEDBACK) { 
                    String[] args = { ""+tid,
                                      TransactionState.toString(tstate),
                                      getConnectionUID().toString() };
                    logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                               BrokerResources.I_CONN_CLEANUP_KEEP_TXN, args));
                    continue;
                }
                if (DEBUG || DEBUG_TXN) {
                    logger.log(Logger.INFO, "Cleanup connection ["+getConnectionUID()+
                    "]: cleaning up transaction "+tid+"["+TransactionState.toString(tstate)+"]");
                }
                try {
                     rollbackHandler.doRollback(tl, tid, ts.getXid(), null, ts, conlist, 
                                   null, RollbackReason.CONNECTION_CLEANUP);
                } catch (Exception e) {
                     String[] args = { ""+tid+"["+TransactionState.toString(tstate)+"]",
                                       getConnectionUID().toString(), e.getMessage() };
                     logger.logStack(logger.WARNING, 
                            Globals.getBrokerResources().getString(
                            BrokerResources.W_CONN_CLEANUP_ROLLBACK_TRAN_FAIL, args), e);
               }
           }
           Iterator itr = timeoutTIDs.iterator();
           while (itr.hasNext()) {
               tid = (TransactionUID)itr.next();
               tl.addDetachedTransactionID(tid);
           }
           timeoutTIDs.clear();
           conlist.clear();
       }
    }


    /**
     * cleanup connections when broker shutting down
     */
    public synchronized void shutdownConnection(String reason) { 
        if (DEBUG) {
            logger.log(Logger.DEBUGMED, "Shuting down Connection {0}",
                      this.toString());
        }
       
        closeConnection(true, GoodbyeReason.SHUTDOWN_BKR, reason); 
        destroyConnection(true, GoodbyeReason.SHUTDOWN_BKR, reason);
    }


    /**
     * Sets the ConnectionUID for this connection.
     */
    public void setConnectionUID(ConnectionUID id) {
        this.conId = id;
        if (coninfo != null)
            coninfo.id = id.toString().getBytes();
    }

// -------------------------------------------------------------------------
//   Queuing Messages
// -------------------------------------------------------------------------

    public abstract void sendControlMessage(Packet msg);

// -------------------------------------------------------------------------
//   Sending Messages
// -------------------------------------------------------------------------

    Object stateLock = new Object();
    boolean busy = false;

    protected abstract void checkState();

    public void wakeup() {
        synchronized(stateLock) {
            stateLock.notifyAll();
        }
    }

    public boolean isBusy() {
        return busy;
    }

// ---------------------------------------
//     Abstract Connection methods
// ---------------------------------------

    public void cleanupMemory(boolean persist) {
        // does nothing right now
    }

    public void flowPaused(long size) {
    }

    HashMap producers = new HashMap();

    public void addProducer(ProducerSpi p) {
        if (Globals.getMemManager() != null) {
            Globals.getMemManager().addProducer();
        }
        Object old = producers.put(p.getProducerUID(), p);
        assert old == null;
    }

    public void removeProducer(ProducerUID pid, String reason, CoreLifecycleSpi clc) 
        throws BrokerException
    {
        if (Globals.getMemManager() != null) {
            Globals.getMemManager().removeProducer();
        }
        Object o = producers.remove(pid);
        if (o == null)
            throw new BrokerException("Requested removal of "
              + " producer " + pid + " which is not associated with"
              + " connection " + getConnectionUID());
        
        clc.destroyProducer(pid, reason);
    }

    /*
     * Return list of session IDs for this connection
     */
    public List getSessions() {
        return new ArrayList(sessions.keySet());
    }

    public List getProducers() {
        return new ArrayList(producers.values());
    }

    public List getProducerIDs() {
        return new ArrayList(producers.keySet());
    }

    public int getProducerCnt() {
        return producers.size();
    }

    public List getConsumers() {
        ArrayList cons = new ArrayList();
        Iterator itr = sessions.values().iterator();
        while (itr.hasNext()) {
            Session s = (Session)itr.next();
            Iterator citr = s.getConsumers();
            while (citr.hasNext()) {
                ConsumerSpi c = (ConsumerSpi)citr.next();
                cons.add(c);
            }
        }
        return cons;
    }

    public List getConsumersIDs() {
        ArrayList cons = new ArrayList();
        Iterator itr = sessions.values().iterator();
        while (itr.hasNext()) {
            Session s = (Session)itr.next();
            Iterator citr = s.getConsumers();
            while (citr.hasNext()) {
                ConsumerSpi c = (ConsumerSpi)citr.next();
                cons.add(c.getConsumerUID());
            }
        }
        return cons;
    }

    /**
     * called when the connection is closed
     */
    private void cleanUpProducers() {
        if (Globals.getMemManager() != null)
            Globals.getMemManager().removeProducer(producers.size());
        synchronized(producers) {
            Iterator itr = producers.values().iterator();
            while (itr.hasNext()) {
               ProducerSpi p = (ProducerSpi)itr.next();
               if (coreLifecycle != null) {
                   coreLifecycle.destroyProducer(
                       p.getProducerUID(),"cleanup of connection " + this);
               } else {
                   Producer.destroyProducer(
                       p.getProducerUID(),"cleanup of connection " + this);
               }
               itr.remove();
            }
        }
    }

    /**
     * called when the connection is closed.
     */
    private void cleanUpConsumers() {
        Iterator keys = null;
        synchronized(sessions) {
            keys = new HashSet(sessions.keySet()).iterator();
        }

        while (keys.hasNext()) {
            SessionUID key = (SessionUID)keys.next();
            try {
                closeSession(key);
            } catch (BrokerException e) {
                // ok -> just ignore it
                // the exception is for catching bad protocol
            }
        }
        sessions.clear();
        busySessions.clear();
    }

    HashMap sessions = new HashMap();
    HashMap lockToSession = new HashMap();
    Set busySessions = Collections.synchronizedSet(new LinkedHashSet());

    public boolean hasBusySessions() {
        return busySessions.isEmpty();
    }
    public Session getSession(SessionUID uid) {
        synchronized(sessions) {
            return (Session)sessions.get(uid);
        }
    }

    public void attachTempDestination(DestinationUID d)
    {
        tmpDestinations.add(d);
    }

    public void detachTempDestination(DestinationUID d)
    {
        tmpDestinations.remove(d);
    }

    public void cleanUpTempDest(boolean shutdown) {
        if (shutdown && getConnectionUID().getCanReconnect()) {
            // dont destroy temp dests if we are reconnecting
            return;
        }
        synchronized(tmpDestinations) {
            Iterator itr = tmpDestinations.iterator();
            while (itr.hasNext()) {
                DestinationUID uid = (DestinationUID)
                     itr.next();
                logger.log(Logger.DEBUG,"Destroying temp destination "
                      + uid + " on connection death");
                try {
                    if (coreLifecycle != null) {
                        coreLifecycle.removeDestination(pstore, uid, true,
                            Globals.getBrokerResources().getString(
                            BrokerResources.M_CONNECTION_CLOSED,
                            getConnectionUID()));
                    } else {
                        Globals.getDestinationList().removeDestination(pstore,uid, true,
                            Globals.getBrokerResources().getString(
                            BrokerResources.M_CONNECTION_CLOSED,
                            getConnectionUID()));
                    }
                } catch (Exception ex) {
                    logger.log(Logger.INFO,"Error destination temp "
                       + " destination " + uid);
                }
            }
            tmpDestinations.clear();
        }
    }

    public void attachSession(Session s) {
        synchronized(sessions) {
            sessions.put(s.getSessionUID(), s);
            // add session listener
            lockToSession.put(s.getSessionUID(),
                 s.addEventListener(this, 
                    EventType.BUSY_STATE_CHANGED,
                    null));
        }
    }
    public void closeSession(SessionUID uid) 
        throws BrokerException
    {
        synchronized(sessions) {
            Session s = (Session)sessions.remove(uid);
            if (s == null)
                throw new BrokerException("Requested removal of "
                 + " session " + uid + " which is not associated with"
                 + " connection " + getConnectionUID());
            if (lockToSession != null) {
                lockToSession.remove(s.getSessionUID());
            }
        }
        Session.closeSession(uid);
    }

    public void  destroy(boolean goodbye, int reason, java.lang.String str) 
    {
        destroyConnection(goodbye, reason, str);
    }
    
    /**
	 * Return the transaction list from the connection's client data. If the
	 * list doesn't already exist, it is created.
	 * 
	 * This method is thread-safe, and if the list doesn't already exist,
	 * creates a list which is itself thread-safe.
	 * 
	 * This should be used in cases where this IMQConnection may be used by
	 * multiple threads concurrently (i.e. RADirect)
	 * 
	 * @return
	 */
	public synchronized List getTransactionListThreadSafe() {

		List conlist = (List) getClientData(IMQConnection.TRANSACTION_LIST);
		if (conlist == null) {
			conlist = Collections.synchronizedList(new ArrayList());
			addClientData(IMQConnection.TRANSACTION_LIST, conlist);
		}

		return conlist;
	}
    
}

