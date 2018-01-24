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
 * @(#)CallbackDispatcher.java	1.33 07/23/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterGoodbyeInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterMessageInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterMessageAckInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.RaptorProtocol;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;

/**
 * This class schedules the MessageBusCallback notification
 * invokations.
 */
public final class CallbackDispatcher extends Thread {

    private static boolean DEBUG = false; 

    private static final boolean DEBUG_CLUSTER_ALL =
                        Globals.getConfig().getBooleanProperty(
                                  Globals.IMQ + ".cluster.debug.all"); 

    private static final boolean DEBUG_CLUSTER_MSG =
                   (Globals.getConfig().getBooleanProperty(
                    Globals.IMQ + ".cluster.debug.msg") || DEBUG || DEBUG_CLUSTER_ALL);

    private static final boolean DEBUG_CLUSTER_TXN =
            (Globals.getConfig().getBooleanProperty(
             Globals.IMQ + ".cluster.debug.txn") || DEBUG || DEBUG_CLUSTER_ALL || DEBUG_CLUSTER_MSG);

    private static final Logger logger = Globals.getLogger();
    private MessageBusCallback cb = null;
    private LinkedList eventQ = null;
    private boolean stopThread = false;
    private ExecutorService commitAckExecutor = null;
    private ExecutorService syncAckExecutor = null;
    private ExecutorService msgDataExecutor = null;
    private BrokerResources br = Globals.getBrokerResources();

    private static FaultInjection fi = FaultInjection.getInjection();

    public CallbackDispatcher(MessageBusCallback cb) {
        this.cb = cb;
        eventQ = new LinkedList();
        setName("MessageBusCallbackDispatcher");
        setDaemon(true);
        start();
        commitAckExecutor = Executors.newSingleThreadExecutor();
        syncAckExecutor = Executors.newSingleThreadExecutor();
        msgDataExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Initial sync with the config server is complete.
     * We are now ready to accept connections from clients.
     */
    public void configSyncComplete() {
        CallbackEvent cbe = new ConfigSyncCompleteCallbackEvent();

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    public void processGPacket(BrokerAddress sender, GPacket pkt, Protocol p) {
        CallbackEvent cbe =
            new GPacketCallbackEvent(sender, pkt, p);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    public void processMessageAckReply(final BrokerAddress sender, final GPacket pkt, final Protocol p) {
        int type = pkt.getType();
        if (type != ProtocolGlobals.G_MESSAGE_ACK_REPLY) {
            throw new RuntimeException(
            "Internal Error: Unexpected packet type "+type+" passed to CallbackDispatcher.processMessageAckReply()");
        }
        if (processCommitAck(sender, pkt)) {
            return;
        }

        if (DEBUG_CLUSTER_MSG || DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, 
            "processMessageAckReply: Received "+ProtocolGlobals.getPacketTypeDisplayString(type)+
            " from "+sender+": "+ClusterMessageAckInfo.toString(pkt));
        }

        try {

            syncAckExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        p.handleGPacket(cb, sender, pkt);
                    } catch (Throwable t) { 
                        logger.logStack(logger.WARNING,
                        "Exception in processing "+ClusterMessageAckInfo.toString(pkt)+" from "+sender, t);
                    }
                }
                }
                );

        } catch (Throwable t) {
            if (stopThread) {
                logger.log(logger.DEBUG, 
                "Cluster shutdown, ignore event "+ClusterMessageAckInfo.toString(pkt)+" from "+sender);
            } else {
                logger.logStack(logger.WARNING, "Exception in submitting for processing "+ClusterMessageAckInfo.toString(pkt)+" from "+sender, t);
            }
        }
    }

    private boolean processCommitAck(final BrokerAddress sender, final GPacket pkt) {

        if (!ClusterMessageAckInfo.isAckAckAsync(pkt)) {
            return false; 
        }

        Integer acktype =  ClusterMessageAckInfo.getAckAckType(pkt);
        if (acktype == null || acktype.intValue() != ClusterGlobals.MB_MSG_CONSUMED) {
            return false;
        }

        Long transactionID = ClusterMessageAckInfo.getAckAckTransactionID(pkt);
        if (transactionID == null) {
            return false;
        }
        
        if (ClusterMessageAckInfo.getAckAckStatus(pkt) != Status.OK) {
            logger.log(logger.WARNING, br.getKString( br.W_CLUSTER_MSG_ACK_FAILED_FROM_HOME,
                                       sender, ClusterMessageAckInfo.toString(pkt)));
            return true;
        }

        if (DEBUG_CLUSTER_TXN || DEBUG) {
            logger.log(logger.INFO, 
            "processCommitAck: Received "+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+
            " from "+sender+": "+ClusterMessageAckInfo.toString(pkt));
        }

        if (fi.FAULT_INJECTION) {
            ClusterMessageAckInfo.CHECKFAULT(new HashMap(), 
                                  ClusterGlobals.MB_MSG_CONSUMED, transactionID,
                                  FaultInjection.MSG_REMOTE_ACK_P, FaultInjection.STAGE_3);
        }

        try {

            final TransactionUID tuid = new TransactionUID(transactionID.longValue());
            final UID ss = ClusterMessageAckInfo.getAckAckStoreSessionUID(pkt);
            commitAckExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        BrokerAddress addr = sender;
                        if (ss != null) {
                            addr = (BrokerAddress)sender.clone();
                            addr.setStoreSessionUID(ss);
                        }
                        Object[] oo = TransactionList.getTransListAndState(tuid, null, true, false);
                        if (oo == null) {
                            logger.log(logger.INFO,  Globals.getBrokerResources().getKString(
                                BrokerResources.W_TXN_NOT_FOUND_ON_UPDATE_TXN_COMPLETION_FOR_BKR,
                                tuid, addr));
                            return;
                        }
                        TransactionList tl = (TransactionList)oo[0];
                        tl.completeClusterTransactionBrokerState(tuid,
                                   TransactionState.COMMITTED, addr, true);
                    } catch (Throwable t) { 
                        Object[] args = { tuid, 
                            sender+"["+ClusterMessageAckInfo.toString(pkt)+"]", 
                            t.getMessage() };
                        String emsg = br.getKString(br.W_UNABLE_UPDATE_CLUSTER_TXN_COMPLETE_STATE, args); 
                        if (t instanceof BrokerException) {
                            if (((BrokerException)t).getStatusCode() == Status.NOT_FOUND) {
                                if (DEBUG_CLUSTER_TXN || DEBUG) {
                                    logger.log(logger.WARNING, emsg+" - already completed");
                                }
                                return;
                            }
                        }
                        logger.logStack(logger.WARNING, emsg, t); 
                    }
                }
                }
                );

        } catch (Throwable t) {
            if (stopThread) {
                logger.log(logger.DEBUG, 
                "Cluster shutdown, ignore event "+ClusterMessageAckInfo.toString(pkt)+" from "+sender);
            } else {
                logger.logStack(logger.WARNING, "Exception in submitting for processing "+ClusterMessageAckInfo.toString(pkt)+" from "+sender, t);
            }
        }

        return true;
    }


    public void processMessageData(final BrokerAddress sender, final GPacket pkt, final Protocol p) {
        int type = pkt.getType();
        if (type != ProtocolGlobals.G_MESSAGE_DATA &&
            type != ProtocolGlobals.G_NEW_INTEREST_REPLY &&
            type != ProtocolGlobals.G_INTEREST_UPDATE_REPLY &&
            type != ProtocolGlobals.G_DURABLE_ATTACH_REPLY &&
            type != ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY &&
            type != ProtocolGlobals.G_CLIENT_CLOSED_REPLY) {
            throw new RuntimeException(
            "Internal Error: Unexpected packet type "+type+" passed to CallbackDispatcher.processMessageData()");
        }

        if (DEBUG_CLUSTER_MSG || DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "processMessageData: Received "+
                                     ProtocolGlobals.getPacketTypeDisplayString(type)+ " from "+sender);
        }

        try {

            msgDataExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        p.handleGPacket(cb, sender, pkt);
                    } catch (Throwable t) {
                        logger.logStack(logger.WARNING, "Exception in processing "+
                        ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+" from "+sender, t);
                    }
                }
                }
                );

        } catch (Throwable t) {
            if (stopThread) {
                logger.log(logger.DEBUG,
                "Cluster shutdown, ignore packet "+ ProtocolGlobals.getPacketTypeDisplayString(type)+" from "+sender);
            } else {
                logger.logStack(logger.WARNING, "Exception in submitting for processing "+
                ProtocolGlobals.getPacketTypeDisplayString(type)+" from "+sender, t);
            }
        }
    }


    public void processGoodbye(BrokerAddress sender, ClusterGoodbyeInfo cgi) {
        CallbackEvent ev = null;
        synchronized (eventQ) {
            Iterator itr = eventQ.iterator();
            while (itr.hasNext()) {
                ev = (CallbackEvent)itr.next();
                if (!(ev instanceof GPacketCallbackEvent)) continue;
                GPacketCallbackEvent gpev = (GPacketCallbackEvent)ev;
                if (!gpev.getSender().equals(sender)) continue; //XXXbrokerSessionUID
                if (gpev.getEventType() == ProtocolGlobals.G_MESSAGE_ACK ||
                    gpev.getEventType() == ProtocolGlobals.G_MESSAGE_DATA) {
                    if (DEBUG_CLUSTER_MSG || DEBUG_CLUSTER_TXN) {
                        logger.log(logger.INFO, "Discard unprocessed "+
                            ProtocolGlobals.getPacketTypeString(gpev.getEventType())+
                            " because received GOODBYE from " +sender);
                    }
                    itr.remove();
                }
            }
        }
    }

    public void processGoodbyeReply(BrokerAddress sender) {
        CallbackEvent ev = null;
        synchronized (eventQ) {
            Iterator itr = eventQ.iterator();
            while (itr.hasNext()) {
                ev = (CallbackEvent)itr.next();
                if (!(ev instanceof GPacketCallbackEvent)) continue;
                GPacketCallbackEvent gpev = (GPacketCallbackEvent)ev;
                if (!gpev.getSender().equals(sender)) continue; //XXXbrokerSessionUID
                if (gpev.getEventType() != ProtocolGlobals.G_MESSAGE_ACK_REPLY) continue;
                if (DEBUG) {
                logger.log(logger.INFO, "Processed G_MESSAGE_ACK_REPLY from " +sender);
                }
                gpev.dispatch(cb);
                itr.remove();
            }
        }
    }

    /**
     * Interest creation notification. This method is called when
     * any local / remote interest is created.
     */
    public void interestCreated(Consumer intr) {
        CallbackEvent cbe = new InterestCreatedCallbackEvent(intr);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    /**
     * Interest removal notification. This method is called when
     * any local / remote interest is removed. USED by Falcon only
     */
    public void interestRemoved(Consumer intr, 
        Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs,
        boolean cleanup) {
        CallbackEvent cbe = new InterestRemovedCallbackEvent(
                                    intr, pendingMsgs, cleanup);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    public void activeStateChanged(ConsumerUID intid) {
        CallbackEvent cbe = new PrimaryInterestChangedCallbackEvent(intid);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    /**
     * Primary interest change notification. This method is called when
     * a new interest is chosen as primary interest for a failover queue.
     * USED by Falcon only
     */
    public void activeStateChanged(Consumer intr) {
        CallbackEvent cbe = new PrimaryInterestChangedCallbackEvent(intr);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    /**
     * Client down notification. This method is called when a local
     * or remote client connection is closed.
     */
    public void clientDown(ConnectionUID conid) {
        CallbackEvent cbe = new ClientDownCallbackEvent(conid);

        synchronized (eventQ) {
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
    }

    /**
     * Broker down notification. This method is called when any broker
     * in this cluster goes down.
     */
    public void brokerDown(BrokerAddress broker) {
        CallbackEvent e = null;
        synchronized (eventQ) {
            Iterator itr = eventQ.iterator();
            while (itr.hasNext()) {
                e = (CallbackEvent)itr.next();
                if (!(e instanceof GPacketCallbackEvent)) continue;
                GPacketCallbackEvent ge = (GPacketCallbackEvent)e;
                if (!ge.getSender().equals(broker)) continue; 
                if (ge.getSender().getBrokerSessionUID() != null &&
                    broker.getBrokerSessionUID() != null &&
                    (!ge.getSender().getBrokerSessionUID().equals(
                                     broker.getBrokerSessionUID()))) continue; 
                if (ge.getEventType() == ProtocolGlobals.G_MESSAGE_ACK_REPLY ||
                    ge.getEventType() == ProtocolGlobals.G_NEW_INTEREST ||
                    ge.getEventType() == ProtocolGlobals.G_INTEREST_UPDATE ||
                    ge.getEventType() == ProtocolGlobals.G_DURABLE_ATTACH ||
                    ge.getEventType() == ProtocolGlobals.G_NEW_PRIMARY_INTEREST ||
                    ge.getEventType() == ProtocolGlobals.G_REM_INTEREST ||
                    ge.getEventType() == ProtocolGlobals.G_REM_DURABLE_INTEREST ||
                    ge.getEventType() == ProtocolGlobals.G_REM_DESTINATION ||
                    ge.getEventType() == ProtocolGlobals.G_UPDATE_DESTINATION ||
                    ge.getEventType() == ProtocolGlobals.G_CLIENT_CLOSED) {

                    ge.dispatch(cb);
                }
                itr.remove();
            }
        }
        cb.brokerDown(broker);
    }

    /**
     * A new destination was created by the administrator on a remote
     * broker.  This broker should also add the destination if it is
     * not already present.
     */
    public void notifyCreateDestination(Destination d) {
        CallbackEvent cbe = new
            ClusterCreateDestinationCallbackEvent(d, new CallbackEventListener());

        synchronized (eventQ) {
            if (stopThread)  {
                logger.log(logger.DEBUG, 
                  "Cluster shutdown, ignore create destination event on " + d);
                return;
            }
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
        cbe.getEventListener().waitEventProcessed();
    }

    /**
     * A destination was removed by the administrator on a remote
     * broker. This broker should also remove the destination, if it
     * is present.
     */
    public void notifyDestroyDestination(DestinationUID uid) {
        CallbackEvent cbe = new
            ClusterDestroyDestinationCallbackEvent(uid, new CallbackEventListener());

        synchronized (eventQ) {
            if (stopThread)  {
                logger.log(logger.DEBUG, 
                  "Cluster shutdown, ignore destroy destination event on " + uid);
                return;
            }
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
        cbe.getEventListener().waitEventProcessed();
    }

    /**
     * A destination was removed by the administrator on a remote
     * broker. This broker should also remove the destination, if it
     * is present.
     */
    public void notifyUpdateDestination(DestinationUID uid, Map changes) {
        CallbackEvent cbe = new
            ClusterUpdateDestinationCallbackEvent(uid, changes, 
                                        new CallbackEventListener());
        synchronized (eventQ) {
            if (stopThread)  {
                logger.log(logger.DEBUG, 
                  "Cluster shutdown, ignore update destination event on " + uid);
                return;
            }
            eventQ.add(cbe);
            eventQ.notifyAll();
        }
        cbe.getEventListener().waitEventProcessed();
    }

    public void shutdown() {
        synchronized (eventQ) {
            stopThread = true;
            eventQ.notifyAll();
        }
        msgDataExecutor.shutdown();
        commitAckExecutor.shutdown();
        syncAckExecutor.shutdown();

        try {
            join(30000);
        }
        catch (InterruptedException e) {
            // unable to complete join
        }
        try {
            if (!msgDataExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.log(logger.INFO, "Force cluster msgDataExecutor thread shutdown");
                msgDataExecutor.shutdownNow(); 
                msgDataExecutor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            msgDataExecutor.shutdownNow();
        }

        try {
            if (!commitAckExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.log(logger.INFO, "Force cluster commitAckExecutor thread shutdown");
                commitAckExecutor.shutdownNow(); 
                commitAckExecutor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            commitAckExecutor.shutdownNow();
        }

        try {
            if (!syncAckExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.log(logger.INFO, "Force cluster syncAckExecutor thread shutdown");
                syncAckExecutor.shutdownNow(); 
                syncAckExecutor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            syncAckExecutor.shutdownNow();
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("stopThread", Boolean.valueOf(stopThread));
        Object[] events = null;
        synchronized(eventQ) {
            events = eventQ.toArray();
        }
        ht.put("eventQCount", Integer.valueOf(events.length));
        Vector v = new Vector();
        for (int i = 0; i < events.length; i++) { 
            v.add(events[i].toString()); 
        }
        ht.put("eventQ", v);

        return ht;
    }

    public void run() {
        int restartCode = BrokerStateHandler.getRestartCode();
        String oomsg = Globals.getBrokerResources().getKString(BrokerResources.M_CLUSTER_DISPATCHER_LOW_MEM);

        CallbackEvent cbe = null;
        try {

        boolean firstpass = true;
        while (true) {

            try {

            synchronized (eventQ) {
                while (!stopThread && eventQ.isEmpty()) {
                    try {
                        eventQ.wait();
                    }
                    catch (Exception e) {}
                }

                if (stopThread)
                    break; // Ignore the pending events.

                cbe = (CallbackEvent)eventQ.removeFirst();
            }

            try { 
                cbe.dispatch(cb);
                firstpass = true;
            } finally {
                CallbackEventListener l = cbe.getEventListener(); 
                if (l != null) l.eventProcessed();
            }

            } catch (Exception e) {

            logger.logStack(Logger.WARNING, 
                   "Cluster dispatcher thread encountered exception: "+e.getMessage(), e);

            } catch (OutOfMemoryError e) {

            if (firstpass) {
                firstpass = false;
                Globals.handleGlobalError(e, oomsg);
                try {
                sleep(1);
                } catch (InterruptedException ex) {};
                continue;
            }
            Broker.getBroker().exit(restartCode, oomsg, BrokerEvent.Type.RESTART, e, false, false, false);

            }
        }

        } catch (Throwable t) {
        logger.logStack(Logger.WARNING, "Cluster dispatcher thread got unrecoverable exception", t);

        } finally {

        if (!stopThread) {
        logger.log(Logger.WARNING, "Cluster dispatcher thread exiting");
        }

        synchronized(eventQ) {
            try {

            cbe = (CallbackEvent) eventQ.removeFirst();
            while (cbe != null) {
                CallbackEventListener l = cbe.getEventListener(); 
                if (l != null) l.eventProcessed();
                cbe = (CallbackEvent) eventQ.removeFirst();
            }

            } catch (NoSuchElementException e) { }
            stopThread = true;
        }

        }
    };
}

abstract class CallbackEvent {
    public abstract void dispatch(MessageBusCallback cb);
    public CallbackEventListener getEventListener() {
        return null;
    }
}

class CallbackEventListener {
    Object lock = new Object();
    boolean processed = false;

    public void waitEventProcessed()  {
        synchronized(lock) {
            while (!processed) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {}
            }
        }
    }
    public void eventProcessed() {
        synchronized(lock) {
            processed = true;
            lock.notifyAll();
        }
    }
}


class ConfigSyncCompleteCallbackEvent extends CallbackEvent {
    public ConfigSyncCompleteCallbackEvent() {
    }

    public void dispatch(MessageBusCallback cb) {
        cb.configSyncComplete();
    }

    public String toString() {
        return "ConfigSyncCompleted";
    }
}

class GPacketCallbackEvent extends CallbackEvent {
    private BrokerAddress sender;
    private GPacket pkt;
    private Protocol p;

    public GPacketCallbackEvent(BrokerAddress sender, GPacket pkt, Protocol p) {
        this.sender = sender;
        this.pkt = pkt;
        this.p = p;
    }

    public int getEventType() {
        return pkt.getType();
    }

    public BrokerAddress getSender() {
        return sender;
    }

    public void dispatch(MessageBusCallback cb) {
        p.handleGPacket(cb, sender, pkt);
    }

    public String toString() {
        return ProtocolGlobals.getPacketTypeString(pkt.getType())+", from "+sender;
    }
}


class InterestCreatedCallbackEvent extends CallbackEvent {
    private Consumer intr;

    public InterestCreatedCallbackEvent(Consumer intr) {
        this.intr = intr;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.interestCreated(intr);
    }

    public String toString() {
        return "InterestCreated: "+intr;
    }
}

class InterestRemovedCallbackEvent extends CallbackEvent {
    private Consumer intr;
    private Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs = null;
    private boolean cleanup = false;

    public InterestRemovedCallbackEvent(Consumer intr, 
        Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs,
        boolean cleanup) {
        this.intr = intr;
        this.pendingMsgs = pendingMsgs;
        this.cleanup = cleanup;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.interestRemoved(intr, pendingMsgs, cleanup);
    }

    public String toString() {
        return "InterestRemoved: "+intr+", cleanup="+cleanup+", pendingMsgs="+pendingMsgs;
    }
}

class PrimaryInterestChangedCallbackEvent extends CallbackEvent {
    private static final Logger logger = Globals.getLogger();
    private static BrokerResources br = Globals.getBrokerResources();
    private Consumer intr;
    private ConsumerUID intid;

    public PrimaryInterestChangedCallbackEvent(ConsumerUID intid) {
        this.intid = intid;
        this.intr = null;
    }

    public PrimaryInterestChangedCallbackEvent(Consumer intr) {
        this.intr = intr;
        this.intid = null;
    }

    public void dispatch(MessageBusCallback cb) {
        if (intr == null && intid != null) {
            intr = Consumer.getConsumer(intid);

            if (intr == null) {
                logger.log(logger.WARNING,
                    br.W_MBUS_BAD_PRIMARY_INT, intid);

                return;
            }
        }

        cb.activeStateChanged(intr);
    }

    public String toString() {
        return "PrimaryInterestChanged: " +intid;
    }
}

class ClientDownCallbackEvent extends CallbackEvent {
    private ConnectionUID conid;

    public ClientDownCallbackEvent(ConnectionUID conid) {
        this.conid = conid;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.clientDown(conid);
    }

    public String toString() {
        return "ClientDown: "+conid;
    }
}

class BrokerDownCallbackEvent extends CallbackEvent {
    private BrokerAddress broker;

    public BrokerDownCallbackEvent(BrokerAddress broker) {
        this.broker = broker;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.brokerDown(broker);
    }

    public String toString() { 
        return "BrokerDown: "+broker;
    }
}

class ClusterCreateDestinationCallbackEvent extends CallbackEvent {
    private Destination d;
    private CallbackEventListener l;

    public ClusterCreateDestinationCallbackEvent(
        Destination d, CallbackEventListener listener) {
        this.d = d;
        this.l = listener;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.notifyCreateDestination(d);
    }
   
    public CallbackEventListener getEventListener() {
        return l;
    }

    public String toString() {
        return "DestinationCreated: "+d;
    }
}
class ClusterUpdateDestinationCallbackEvent extends CallbackEvent {
    private DestinationUID duid;
    private Map changes;
    private CallbackEventListener l;

    public ClusterUpdateDestinationCallbackEvent(
        DestinationUID duid, Map changes, CallbackEventListener listener) {
        this.duid = duid;
        this.changes = changes;
        this.l = listener;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.notifyUpdateDestination(duid, changes);
    }

    public CallbackEventListener getEventListener() {
        return l;
    }    

    public String toString() {
        return "DestinationUpdated: "+duid;
    }
}

class ClusterDestroyDestinationCallbackEvent extends CallbackEvent {
    private DestinationUID d;
    private CallbackEventListener l;

    public ClusterDestroyDestinationCallbackEvent(
        DestinationUID d, CallbackEventListener listener) {
        this.d = d;
        this.l = listener;
    }

    public void dispatch(MessageBusCallback cb) {
        cb.notifyDestroyDestination(d);
    }

    public CallbackEventListener getEventListener() {
        return l;
    }    

    public String toString() {
        return "DestinationDetroyed: "+d;
    }
}

/*
 * EOF
 */
