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
 * @(#)MultibrokerRouter.java   1.51 7/30/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.router;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.Queue;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.multibroker.Protocol;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.AckEntryNotFoundException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;
import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.lists.EventType;
import com.sun.messaging.jmq.util.lists.Prioritized;
import com.sun.messaging.jmq.util.lists.Reason;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.io.txnlog.TransactionLogType;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.NoPersistPartitionedStoreImpl;

/**
 * This class handles the processing of messages from other brokers
 * in the cluster.
 */
public class MultibrokerRouter implements ClusterRouter
{
    private static boolean DEBUG = false;

    private static boolean DEBUG_CLUSTER_TXN =
                Globals.getConfig().getBooleanProperty(
                        Globals.IMQ + ".cluster.debug.txn");
    private static boolean DEBUG_CLUSTER_MSG =
               Globals.getConfig().getBooleanProperty(
               Globals.IMQ + ".cluster.debug.msg");

    private static Logger logger = Globals.getLogger();
    private static final FaultInjection FI = FaultInjection.getInjection();

    private static final String ENFORCE_REMOTE_DEST_LIMIT_PROP = 
            Globals.IMQ + ".cluster.enforceRemoteDestinationLimit";
    private static boolean ENFORCE_REMOTE_DEST_LIMIT =
        Globals.getConfig().getBooleanProperty(ENFORCE_REMOTE_DEST_LIMIT_PROP, false);

    //obsolete private property
    //private static String ROUTE_REJECTED_REMOTE_MSG_PROP = 
               //Globals.IMQ+".cluster.routeRejectedRemoteMsg"; //4.5

    private ArrayList loggedFullDestsOnHandleJMSMsg = new ArrayList();

    ClusterBroadcast cb = null;
    Protocol p = null;

    BrokerConsumers bc = null;
    DestinationList DL = Globals.getDestinationList();

    protected static boolean getDEBUG() {
        return (DEBUG || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG);
    }

    public MultibrokerRouter(ClusterBroadcast cb)
    {
        this.cb = cb;
        this.p = (Protocol)cb.getProtocol();
        bc = new BrokerConsumers(p);
    }

    public static String msgToString(int id)
    {
        switch(id) {
            case ClusterBroadcast.MSG_DELIVERED:
                return "MSG_DELIVERED";
            case ClusterBroadcast.MSG_ACKNOWLEDGED:
                return "MSG_ACKNOWLEDGED";
            case ClusterBroadcast.MSG_PREPARE:
                return "MSG_PREPARE";
            case ClusterBroadcast.MSG_ROLLEDBACK:
                return "MSG_ROLLEDBACK";
            case ClusterBroadcast.MSG_IGNORED:
                return "MSG_IGNORED";
            case ClusterBroadcast.MSG_UNDELIVERABLE:
                return "MSG_UNDELIVERABLE";
            case ClusterBroadcast.MSG_DEAD:
                return "MSG_DEAD";
        }
        return "UNKNOWN";
    }

    public void addConsumer(Consumer c) 
       throws BrokerException, IOException, SelectorFormatException
    {
        bc.addConsumer(c);
    }

    public void removeConsumer(com.sun.messaging.jmq.jmsserver.core.ConsumerUID c,
        Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs, boolean cleanup)
        throws BrokerException, IOException {

        bc.removeConsumer(c, pendingMsgs, cleanup);
    }


    public void removeConsumers(ConnectionUID uid)
       throws BrokerException, IOException
    {
        bc.removeConsumers(uid);
    }


    public void brokerDown(com.sun.messaging.jmq.jmsserver.core.BrokerAddress ba)
       throws BrokerException, IOException
    {
        bc.brokerDown(ba);
    }

    public void forwardMessage(PacketReference ref, Collection consumers) {
        bc.forwardMessageToRemote(ref, consumers);
    }


    public void shutdown() {
        bc.destroy();
    }

    public void handleJMSMsg(Packet p, Map<ConsumerUID, Integer> consumers,
                             BrokerAddress sender, boolean sendMsgDeliveredAck)
                             throws BrokerException {

        Map<ConsumerUID, Integer> deliveryCnts = consumers;
        boolean hasflowcontrol = true;
        ArrayList<Consumer> targetVector = new ArrayList<Consumer>();
        ArrayList ignoreVector = new ArrayList();
        PartitionedStore pstore = Globals.getStore().getPrimaryPartition(); //PART

        Iterator<Map.Entry<ConsumerUID, Integer>> itr = 
                        consumers.entrySet().iterator();
        Map.Entry<ConsumerUID, Integer> entry = null;
        while (itr.hasNext()) {
            entry = itr.next();
            ConsumerUID uid = entry.getKey();
            Consumer interest = Consumer.getConsumer(uid);
            if (interest != null && interest.isValid()) {
                // we need the interest for updating the ref
                targetVector.add(interest);
                ConsumerUID suid = interest.getStoredConsumerUID();
                if ((suid == null || suid.equals(uid)) &&
                    interest.getSubscription() == null) {
                    hasflowcontrol = false;
                }
            } else {
                ignoreVector.add(uid);
            }
        }

        if (targetVector.isEmpty()) {
            sendIgnoreAck(p.getSysMessageID(), null, sender, ignoreVector);
            return;
        }

        boolean exists = false;
        PacketReference ref = DL.get(null, p.getSysMessageID()); //PART
        if (ref != null) {
            BrokerAddress addr = ref.getBrokerAddress();
            if (addr == null || !addr.equals(sender)) {
                if (DEBUG) {
                logger.log(Logger.INFO, 
                "Remote message "+ref.getSysMessageID()+ " home broker "+addr+" changed to "+sender);
                }
                DL.remoteCheckMessageHomeChange(ref, sender);
                if (addr == null) {
                    Object[] args = { ref.getSysMessageID(), sender, targetVector };
                    logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                               BrokerResources.W_IGNORE_REMOTE_MSG_SENT_FROM, args));
                    ignoreVector.addAll(targetVector);
                    targetVector.clear();
                    sendIgnoreAck(p.getSysMessageID(), ref, sender, ignoreVector);
                    return;
                }
            }
        }
        List dsts = null;
        ref = DL.get(null, p.getSysMessageID()); //PART
        boolean acquiredWriteLock = false;
        try {

        if (ref != null) {
            ref.acquireDestroyRemoteWriteLock();
            acquiredWriteLock = true;
            if (ref.isInvalid() || ref.isDestroyed()) {
                ref.clearDestroyRemoteWriteLock();
                acquiredWriteLock = false;
                ref = DL.get(null, p.getSysMessageID()); //PART
                if (ref != null) {
                    ref.acquireDestroyRemoteWriteLock();
                    acquiredWriteLock = true;
                }
            }
        }
        if (ref != null) {
            if (ref.getBrokerAddress() == null) {
                ref.clearDestroyRemoteWriteLock();
                acquiredWriteLock = false;
                Object[] args = { ref.getSysMessageID(), sender, targetVector };
                logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                           BrokerResources.W_IGNORE_REMOTE_MSG_SENT_FROM, args));
                ignoreVector.addAll(targetVector);
                targetVector.clear();
                sendIgnoreAck(p.getSysMessageID(), ref, sender, ignoreVector);
                return;
            } else {
                exists = true;
                ref.setBrokerAddress(sender);
                if (p.getRedelivered()) {
                    ref.overrideRedeliver();
                }
            }
        } else {
            ref = PacketReference.createReference(pstore, p, null); //PART
            ref.setBrokerAddress(sender);
        }
        // XXX - note, we send a reply for all message delivered
        //       acks, that is incorrect
        //       really, we should have a sendMsgDeliveredAck per
        //       consumer
        if (sendMsgDeliveredAck) {
            for (int i=0; i < targetVector.size(); i ++) {
                Consumer c = (Consumer)targetVector.get(i);
                //ref.addMessageDeliveredAck(c.getStoredConsumerUID());
                ref.addMessageDeliveredAck(c.getConsumerUID());
            }
        }
        try {
            if (ref.getDestinationUID().isWildcard()) {
                List[] dss = DL.findMatchingIDs(pstore, ref.getDestinationUID());
                dsts = dss[0];
            } else {
                // ok, autocreate the destination if necessary
                Destination[] ds = DL.getDestination(pstore,
                    ref.getDestinationUID().getName(), 
                    ref.getDestinationUID().isQueue() ? DestType.DEST_TYPE_QUEUE
                       : DestType.DEST_TYPE_TOPIC, true, true);
                Destination d = ds[0]; 
                if (d != null) {
                    dsts = new ArrayList();
                    dsts.add(d.getDestinationUID());
                }
            }
            if (dsts == null || dsts.isEmpty()) {
               ignoreVector.addAll(targetVector); 
               targetVector.clear();
            } else {
                if (!exists && !targetVector.isEmpty()) {
                    ref.setNeverStore(true);
                    // OK .. we dont need to route .. its already happened
                    ref.storeRemoteInterests(targetVector, deliveryCnts);
                    itr = dsts.iterator();
                    while (itr.hasNext()) {
                        DestinationUID did = (DestinationUID)itr.next();
                        Destination[] ds = DL.getDestination(pstore, did);
                        Destination d = ds[0];
                        if (DEBUG) {
                            logger.log(logger.INFO, "Route remote message "+ref+ 
                                " sent from "+sender+" to destination(s) "+did+
                                " for consumer(s) "+targetVector+" hasflowcontrol="+
                                 hasflowcontrol+", enforcelimit="+ENFORCE_REMOTE_DEST_LIMIT);
                        }
                        //add to message count
                        d.acquireQueueRemoteLock(); 
                        try {
                            PacketReference newref = d.getMessage(p.getSysMessageID());
                            if (newref != null) {
                                ignoreVector.addAll(targetVector);
                                targetVector.clear();
                                Object[] args = { p.getSysMessageID(), 
                                                  ""+d.getDestinationUID(), sender, 
                                                  newref+"["+newref.getBrokerAddress()+"]" };
                                logger.log(logger.INFO, Globals.getBrokerResources().
                                    getKString(BrokerResources.I_REMOTE_NEW_MSG_ROUTED_ALREADY, args));
                                break;
                            }
                            d.queueMessage(ref, false, ENFORCE_REMOTE_DEST_LIMIT);
                        } finally {
                            d.clearQueueRemoteLock();
                        }
                    }
                } else if (exists) {
                    ref.addRemoteInterests(targetVector);
                }
            }
        } catch (Exception ex) {
            Object[] args = { ""+ref, sender, targetVector }; 
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.W_EXCEPTION_PROCESS_REMOTE_MSG, args);
            if (!(ex instanceof BrokerException)) {
                logger.logStack(logger.WARNING, emsg, ex);
            } else {
                BrokerException be = (BrokerException)ex;
                int status = be.getStatusCode();
                if (status != Status.RESOURCE_FULL &&
                    status != Status.ENTITY_TOO_LARGE) {
                    logger.logStack(logger.WARNING, emsg, ex);
                } else {
                    Object[] args1 = { sender, targetVector };
                    emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.W_PROCESS_REMOTE_MSG_DST_LIMIT, args1);
                    int level = Logger.DEBUG;
                    if (ref == null || 
                        !loggedFullDestsOnHandleJMSMsg.contains(ref.getDestinationUID())) {
                        level = Logger.WARNING;
                        loggedFullDestsOnHandleJMSMsg.add(ref.getDestinationUID());
                    }
                    logger.log(level, emsg+(level == Logger.DEBUG ? ": "+ex.getMessage():""));
                }
            }
        } 

        } finally {
            if (ref != null && acquiredWriteLock) {
                ref.clearDestroyRemoteWriteLock();
            }
        }

        // Now deliver the message...
        StringBuffer debugString = new StringBuffer();
        debugString.append("\n");

        int i;
        for (i = 0; i < targetVector.size(); i++) {
            Consumer interest = (Consumer)targetVector.get(i);

            if (!interest.routeMessage(ref, false)) {
                // it disappeard on us, take care of it
               try {
                    if (ref.acknowledged(interest.getConsumerUID(),
                          interest.getStoredConsumerUID(), true, false)) {
                        try {

                        if (dsts == null) {
                            continue;
                        }
                        itr = dsts.iterator();
                        while (itr.hasNext()) {
                            DestinationUID did = (DestinationUID)itr.next();
                            Destination[] ds = DL.getDestination(pstore, did);
                            Destination d = ds[0];
                            d.removeRemoteMessage(ref.getSysMessageID(),
                                          RemoveReason.ACKNOWLEDGED, ref);
                        }

                        } finally {
                            ref.postAcknowledgedRemoval();
                        }
                    }
                } catch (Exception ex) {
                    logger.log(logger.INFO,"Internal error processing ack", ex);

                } finally {
                    ref.removeRemoteConsumerUID(
                        interest.getStoredConsumerUID(),
                        interest.getConsumerUID());
                }
            }

            if (DEBUG) {
                debugString.append(
                    "\t" + interest.getConsumerUID() + "\n");
            }
        }

        if (DEBUG) {
            logger.log(logger.DEBUGHIGH,
                "MessageBus: Delivering message to : {0}",
                debugString.toString());
        }
        sendIgnoreAck(p.getSysMessageID(), ref, sender, ignoreVector);
    }

    private void sendIgnoreAck(SysMessageID sysid, 
                 PacketReference ref, BrokerAddress sender,
                 List ignoredConsumers) {
        
        List ignoreVector = ignoredConsumers;
        StringBuffer debugString = new StringBuffer();
        debugString.append("\n");
        Object o = null;
        ConsumerUID cuid = null;
        Consumer interest = null;
        for (int i = 0; i < ignoreVector.size(); i++) {
            try {
                o = ignoreVector.get(i);
                if (o instanceof Consumer) {
                    cuid = ((Consumer)o).getConsumerUID(); 
                } else {
                    cuid = (ConsumerUID)o;
                }
                cb.acknowledgeMessage(sender, 
                    (ref == null ? sysid: ref.getSysMessageID()), cuid,
                    ClusterBroadcast.MSG_IGNORED, null, false);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, "sendMessageAck IGNORE failed to "+sender, e);
            } 
            if (DEBUG) {
                debugString.append("\t" + ignoreVector.get(i) + "\n");
            }
        }

        if (DEBUG) {
            if (ignoreVector.size() > 0)
                logger.log(logger.DEBUGHIGH,
                    "MessageBus: Invalid targets : {0}", debugString.toString());
        }
    }

    public void handleAck(int type, SysMessageID sysid, ConsumerUID cuid,
                          Map optionalProps) throws BrokerException 
    {
       bc.acknowledgeMessageFromRemote(type, sysid, cuid, optionalProps);
    }

    public void handleAck2P(int type, SysMessageID[] sysids, ConsumerUID[] cuids,
                          Map optionalProps, Long txnID, 
                          com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker) 
                          throws BrokerException 
    {
       bc.acknowledgeMessageFromRemote2P(type, sysids, cuids, 
           optionalProps, txnID, txnHomeBroker);
    }


    public void handleCtrlMsg(int type, HashMap props)
        throws BrokerException
    {
        // not implemented
    }

    public Hashtable getDebugState() {
        return bc.getDebugState();
    }
}

/**
 * This class represents the remote Consumers associated with
 * the brokers in this cluster.
 */
class BrokerConsumers implements Runnable, com.sun.messaging.jmq.util.lists.EventListener
{
    //obsolete private property
    private static String REDELIVER_REMOTE_REJECTED =
        Globals.IMQ+".cluster.disableRedeliverRemoteRejectedMsg"; //4.5

    Thread thr = null;

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    Protocol protocol = null;
    boolean valid = true;
    Set activeConsumers= Collections.synchronizedSet(new LinkedHashSet());
    Map consumers= Collections.synchronizedMap(new HashMap());
    Map listeners = Collections.synchronizedMap(new HashMap());

    private FaultInjection fi = null;


    public static int BTOBFLOW = Globals.getConfig().getIntProperty(
               Globals.IMQ + ".cluster.consumerFlowLimit",1000); 

    DestinationList DL = Globals.getDestinationList();
    Map deliveredMessages = new LinkedHashMap();
    Map cleanupList = new HashMap();

    private Map<com.sun.messaging.jmq.jmsserver.core.ConsumerUID, 
        Map<TransactionUID, Set<AckEntry>>> pendingConsumerUIDs = 
        Collections.synchronizedMap(
            new LinkedHashMap<com.sun.messaging.jmq.jmsserver.core.ConsumerUID, 
                              Map<TransactionUID, Set<AckEntry>>>());

    private Object pendingCheckTimerLock = new Object();
    private boolean pendingCheckTimerShutdown = false;
    private WakeupableTimer pendingCheckTimer = null;

    //0 no timeout, in seconds
    public static int pendingCheckInterval = Globals.getConfig().getIntProperty(
               Globals.IMQ + ".cluster.pendingTransactionCheckInterval", 180); 

    private static boolean getDEBUG() {
        return MultibrokerRouter.getDEBUG();
    }

    public BrokerConsumers(Protocol p)
    {
        this.protocol = p;
        if (pendingCheckInterval < p.getClusterAckWaitTimeout()) {
            pendingCheckInterval = p.getClusterAckWaitTimeout();
        }
        this.fi = FaultInjection.getInjection();
        Thread thr =new MQThread(this,"Cluster-BrokerConsumers");
        thr.setDaemon(true);
        thr.start();
    }

    public void notifyPendingCheckTimer() {
        synchronized(pendingCheckTimerLock) {    
            if (pendingCheckInterval > 0 && 
                pendingCheckTimer == null && !pendingCheckTimerShutdown) {
                pendingCheckTimer = new WakeupableTimer(
                        "ClusterRouterPendingTransactionTimer",
                        new PendingCheckEventHandler(),
                        pendingCheckInterval*1000L, pendingCheckInterval*1000L,
                        br.getKString(br.I_CLUSTER_ROUTER_PENDING_TXN_CHECK_THREAD_START),
                        br.getKString(br.I_CLUSTER_ROUTER_PENDING_TXN_CHECK_THREAD_EXIT));
            }
        }
    }

    private class PendingCheckEventHandler implements TimerEventHandler {
        public PendingCheckEventHandler() {
        }
        public void handleOOMError(Throwable e) {
            logger.logStack(logger.WARNING,
            "OutOfMemoryError[ClusterRouterPendingTransactionTimer]", e);
        }
        public void handleLogInfo(String msg) {
            logger.log(logger.INFO, msg+"[ClusterRouterPendingTransactionTimer]");
        }
        public void handleLogWarn(String msg, Throwable e) {
            logger.logStack(logger.WARNING, msg+"[ClusterRouterPendingTransactionTimer]", e);
        }
        public void handleLogError(String msg, Throwable e) {
            logger.logStack(logger.WARNING, msg+"[ClusterRouterPendingTransactionTimer]", e);
        }

        public void handleTimerExit(Throwable e) {
            synchronized(pendingCheckTimerLock) {
                pendingCheckTimer = null;
            }
            if (valid) {
                logger.log(logger.WARNING, br.getKString(
                    br.I_CLUSTER_ROUTER_PENDING_TXN_CHECK_THREAD_EXIT));
            }
        }

        public long runTask() {
            Set<Map<TransactionUID, Set<AckEntry>>> maps = null;
            synchronized(pendingConsumerUIDs) {
                maps = new LinkedHashSet<Map<TransactionUID, Set<AckEntry>>>(
                               pendingConsumerUIDs.values());
            }
            Map<TransactionUID, Set<AckEntry>> map = null;
            Iterator<Map<TransactionUID, Set<AckEntry>>> itr = maps.iterator();
            while (itr.hasNext()) {
                map = itr.next();
                if (map == null) {
                    continue;
                }
                TransactionUID tid = null;
                Set<AckEntry> entries = null;
                Map.Entry<TransactionUID, Set<AckEntry>> pair = null;
                Iterator<Map.Entry<TransactionUID, Set<AckEntry>>> itr1 =
                                                map.entrySet().iterator();
                while (itr1.hasNext()) {
                    pair = itr1.next();
                    tid = pair.getKey();
                    if (tid == null) {
                        continue;
                    }
                    entries = pair.getValue();
                    AckEntry entry = null;
                    Iterator<AckEntry> itr2 = entries.iterator();
                    while (itr2.hasNext()) {
                        entry = itr2.next();
                        if (entry.isPendingTimeout(pendingCheckInterval*1000L)) {
                            protocol.sendTransactionInquiry(tid, 
                                 entry.getConsumerUID().getBrokerAddress());
                        }
                    } 
                }
            }

            //now prepared pending transactions 
            TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
            TransactionList tl = null;
            ArrayList tids = null;
            TransactionUID tid = null;
            for (int i = 0; i < tls.length; i++) {
                tl = tls[i];
                if (tl == null) {
                    continue;
                }
                tids = tl.getPreparedRemoteTransactions(Long.valueOf(pendingCheckInterval*1000L));
                if (tids == null || tids.size() == 0) {
                    continue;
                }
                protocol.sendPreparedTransactionInquiries(tids, null);
            }
            return 0L;
        }
    }

    class AckEntry
    {
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = null;
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID storedcid = null;
        WeakReference pref = null;
        SysMessageID id = null;
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress address = null;
        TransactionUID tuid = null;
        long pendingStartTime =  0L;
        boolean markConsumed = false;

        public AckEntry(SysMessageID id,
              com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid,
              com.sun.messaging.jmq.jmsserver.core.BrokerAddress address) 
        { 
             assert id != null;
             assert uid != null;
             this.id = id;
             this.uid = uid;
             this.address = address;
             pref = null;
        }

        public void markConsumed() {
            markConsumed = true;
        }

        public boolean hasMarkConsumed() {
            return markConsumed;
        }

        public String toString() {
            return ""+id+"["+uid+", "+storedcid+"]TUID="+tuid+", ("+pendingStartTime+")";
        }

        /**
         * The time when this entry becomes pending when remote consumer closed
         */
        public void pendingStarted() {
            pendingStartTime = System.currentTimeMillis();
        }

        public boolean isPendingStarted() {
            return pendingStartTime != 0L;
        }

        public boolean isPendingTimeout(long timeout) {
            return ((System.currentTimeMillis()-pendingStartTime) >= timeout);
        }

        public void setTUID(TransactionUID uid) {
            this.tuid = uid;
        }

        public TransactionUID getTUID() {
            return tuid;
        }

        public com.sun.messaging.jmq.jmsserver.core.BrokerAddress 
               getBrokerAddress() {
             return address;
        }

        public com.sun.messaging.jmq.jmsserver.core.ConsumerUID 
               getConsumerUID() {
            return uid;
        }

        public com.sun.messaging.jmq.jmsserver.core.ConsumerUID
               getStoredConsumerUID() {
            return storedcid;
        }

        public SysMessageID getSysMessageID() {
            return id;
        }
        public PacketReference getReference() {
            return (PacketReference)pref.get();
        }

        public AckEntry(PacketReference ref, 
               com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, 
               com.sun.messaging.jmq.jmsserver.core.ConsumerUID storedUID) 
        {
            pref = new WeakReference(ref);
            id = ref.getSysMessageID();
            storedcid = storedUID;
            this.uid = uid;
        }

        public boolean acknowledged(boolean notify) 
        {

            assert pref != null;

            PacketReference ref = (PacketReference)pref.get();

            boolean done = true;
            try {
                if (ref == null) {
                    ref = DL.get(null, id); //PART
                }
                if (ref == null) { // nothing we can do ?
                    return true;
                }
                if (ref.acknowledged(uid, storedcid, !uid.isDupsOK(), 
                                     notify, tuid, null, null, true)) {
                    if (tuid != null && fi.FAULT_INJECTION) {
                        fi.checkFaultAndExit(
                           FaultInjection.FAULT_MSG_REMOTE_ACK_HOME_C_TXNCOMMIT_1_7,
                           null, 2, false);
                    }
                    Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                                         ref.getDestinationUID());
                    Destination d = ds[0];
                    d.removeMessage(ref.getSysMessageID(),
                      RemoveReason.ACKNOWLEDGED);
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, 
                       "Unable to process acknowledgement:["+id+","+uid+"]", ex);
                done = false;
            }
           return done;
        }

        public boolean equals(Object o) {
            if (! (o instanceof AckEntry)) {
                return false;
            }
            AckEntry ak = (AckEntry)o;
            return uid.equals(ak.uid) &&
                   id.equals(ak.id);
        }
        public int hashCode() {
            // uid is 4 bytes
            return id.hashCode()*15 + uid.hashCode();
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ArrayList l = null; 
        synchronized(deliveredMessages) {
            l = new ArrayList(deliveredMessages.values());
        }
        ht.put("CLUSTER_ROUTER:deliveredMessagesCount", l.size());
        Iterator itr = l.iterator();
        while (itr.hasNext()) {
            AckEntry e = (AckEntry)itr.next();
            SysMessageID id  = e.getSysMessageID();
            ht.put("[deliveredMessages]"+id.toString(), e.toString());
        }

        synchronized(consumers) {
            l = new ArrayList(consumers.keySet());
        }
        ht.put("consumersCount", l.size());
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            Consumer c = (Consumer)consumers.get(cuid);
            if (c instanceof Subscription) {
                ht.put("[consumers]"+cuid.toString(), "Subscription: "+c);
            } else {
                ht.put("[consumers]"+cuid.toString(), c.toString());
            }
        }

        synchronized(activeConsumers) {
            l = new ArrayList(activeConsumers);
        }
        ht.put("activeConsumersCount", l.size());
        Vector v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            Consumer c = (Consumer)itr.next();
            if (c instanceof Subscription) {
                v.add("Subscription: "+c);
            } else {
                v.add(c.toString());
            }
        }
        ht.put("activeConsumers", v);

        synchronized(pendingConsumerUIDs) {
            l = new ArrayList(pendingConsumerUIDs.keySet());
        }
        ht.put("pendingConsumerUIDsCount", l.size());
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            Map<TransactionUID, Set<AckEntry>> pending0, pending = null;
            synchronized(deliveredMessages) {
                pending0 = pendingConsumerUIDs.get(cuid);
                if (pending0 != null) {
                    pending = new LinkedHashMap<TransactionUID, Set<AckEntry>>(pending0);
                } 
            }
            if (pending == null) {
                ht.put("[pendingConsumerUIDs]"+cuid.toString(), "null");
            } else {
                Hashtable htt = new Hashtable();
                Map.Entry<TransactionUID, Set<AckEntry>> pair = null;
                TransactionUID key = null;
                Iterator<Map.Entry<TransactionUID, Set<AckEntry>>> itr1 = 
                                            pending.entrySet().iterator();
                while (itr1.hasNext()) {
                    pair = itr1.next();
                    key = pair.getKey();
                    htt.put("PENDING-TID:"+(key ==  null ? "null":key),
                            new Vector(pair.getValue()));
                }
                ht.put("[pendingConsumerUIDs]"+cuid.toString(),  htt);
            }
        }

        synchronized(cleanupList) {
            l = new ArrayList(cleanupList.keySet());
        }
        ht.put("cleanupListCount", l.size());
        v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            v.add(cuid.toString());
        }
        ht.put("cleanupList", v);

        synchronized(listeners) {
            l = new ArrayList(listeners.keySet());
        }
        ht.put("listenersCount", l.size());
        v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            v.add(cuid.toString());
        }
        ht.put("listeners", v);

        return ht;
    }


    public void destroy() {
        valid = false;
        synchronized(activeConsumers) {
            activeConsumers.notifyAll();
        }
        synchronized(pendingCheckTimerLock) { 
            pendingCheckTimerShutdown = true;
            if (pendingCheckTimer != null) {
                pendingCheckTimer.cancel();
            }
        }
    }


    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) 
    {
        if (type != EventType.BUSY_STATE_CHANGED) {
            assert false; // bad type
        }
        // OK .. add to busy list
        Consumer c = (Consumer)target;

        synchronized(activeConsumers) {
            if (c.isBusy() ) {
                activeConsumers.add(c);
            }
            activeConsumers.notifyAll();
        }
    }

    public void brokerDown
         (com.sun.messaging.jmq.jmsserver.core.BrokerAddress address) 
        throws BrokerException
    {
        if (getDEBUG()) {
        logger.log(logger.INFO, "BrokerConsumers.brokerDown:"+address);
        }

        Set removedConsumers = new HashSet();
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = null;
        synchronized(consumers) {
            Iterator itr = consumers.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (getDEBUG()) {
                logger.log(logger.INFO, "Check remote consumer "+cuid+" from "+cuid.getBrokerAddress());
                }
                if (address.equals(cuid.getBrokerAddress())) {
                    if (address.getBrokerSessionUID() == null ||
                        address.getBrokerSessionUID().equals(
                            cuid.getBrokerAddress().getBrokerSessionUID())) {
                        removedConsumers.add(cuid);
                    }
                }
            }
        }
        synchronized(pendingConsumerUIDs) {
            Iterator itr = pendingConsumerUIDs.keySet().iterator();
            while (itr.hasNext()) {
                cuid  = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (getDEBUG()) {
                logger.log(logger.INFO, "Check closed remote consumer "+cuid+" from "+cuid.getBrokerAddress());
                }
                if (address.equals(cuid.getBrokerAddress())) {
                    if (address.getBrokerSessionUID() == null ||
                        address.getBrokerSessionUID().equals(
                            cuid.getBrokerAddress().getBrokerSessionUID())) {
                        removedConsumers.add(cuid);
                    }
                }
            }
        }
        // destroy consumers
        Iterator itr = removedConsumers.iterator();
        while (itr.hasNext()) {
            cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            removeConsumer(cuid, true);
        }
    }

    // topic send
    public void forwardMessageToRemote(PacketReference ref, Collection cons)
    {
         Iterator itr = cons.iterator();
         while (itr.hasNext()) {
             // hey create an ack entry since we are bypassing consumer
            Consumer consumer = (Consumer)itr.next();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = consumer.getStoredConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = consumer.getConsumerUID();

            // if we dont have an ack, go on
            if (uid.isNoAck()) continue;

            synchronized(removeConsumerLock) {
                if (consumers.get(uid) == null) {
                    if (getDEBUG()) {
                    Globals.getLogger().log(Logger.INFO,
                    "BrokerConsumers.forwardMessageToRemote(): "+ref+", ignore removed consumer: "+consumer);
                    }
                    try {
                        if (ref.acknowledged(uid, sid, !uid.isDupsOK(), false)) {
                            Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                                                 ref.getDestinationUID());
                            Destination d =  ds[0];
                            d.removeMessage(ref.getSysMessageID(), RemoveReason.ACKNOWLEDGED);
                        }
                    } catch (Exception e) {
                        logger.logStack(Logger.WARNING,
                        "Unable to cleanup message "+ref.getSysMessageID()+" for closed consumer "+uid, e);
                    }
                    continue;
                }

                AckEntry entry = new AckEntry(ref, uid, sid);
                synchronized(deliveredMessages) {
                    deliveredMessages.put(entry, entry);
                }
            }
         }
         protocol.sendMessage(ref, cons, false);
    }

    public void removeConsumers(ConnectionUID uid) 
        throws BrokerException
    {
        if (getDEBUG()) {
        logger.log(logger.INFO, "BrokerConsumers.removeConsumers for remote connection: "+uid);
        }
        Set removedConsumers = new HashSet();
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = null;
        synchronized(consumers) {
            Iterator itr = consumers.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (uid.equals(cuid.getConnectionUID())) {
                    // found one
                    removedConsumers.add(cuid);
                }
            }
        }

        synchronized(pendingConsumerUIDs) {
            Iterator itr = pendingConsumerUIDs.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (uid.equals(cuid.getConnectionUID())) {
                    // found one
                    removedConsumers.add(cuid);
                }
            }
        }
        // destroy consumers
        Iterator itr = removedConsumers.iterator();
        while (itr.hasNext()) {
            cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            removeConsumer(cuid, true);
        }
    }

    private Object removeConsumerLock = new Object();

    public void removeConsumer(
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, boolean cleanup) 
        throws BrokerException {
        removeConsumer(uid, null, cleanup);
    }

    public void removeConsumer(
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, 
        Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs,
        boolean cleanup) 
        throws BrokerException {

        if (getDEBUG()) {
           logger.log(logger.INFO, "BrokerConsumers.removeConsumer("+uid+
                                   ", "+pendingMsgs+ ", "+cleanup+")");
          }
          Consumer c = null;
          synchronized(removeConsumerLock) {
              c = (Consumer)consumers.remove(uid);
          }
          if (c == null && !cleanup) return;

          Destination d = null;
          if (c != null) {
              c.pause("MultiBroker - removing consumer");
              // remove it from the destination
              Destination[] ds = DL.getDestination(null, c.getDestinationUID());
              d = ds[0]; //PART

              // quit listening for busy events
              Object listener= listeners.remove(uid);
              if (listener != null) {
                  c.removeEventListener(listener);
              }

              // remove it from the active list
              activeConsumers.remove(c);
          }

          Set destroySet = new LinkedHashSet();
          LinkedHashSet openacks = new LinkedHashSet();

          Map<PartitionedStore, LinkedHashSet> openmsgmp = 
              new LinkedHashMap<PartitionedStore, LinkedHashSet>();

          Map<TransactionUID, Set<AckEntry>> mypending = 
              new LinkedHashMap<TransactionUID, Set<AckEntry>>();
          boolean haspendingtxn = false;

          // OK .. get the acks .. if its a FalconRemote
          // we sent the messages directly and must explicitly ack
          synchronized(deliveredMessages) {
              if (c != null) {
                  cleanupList.put(uid, c.getParentList());
              }
              Map cparentmp = (Map)cleanupList.get(uid);
              if (getDEBUG()) {
              logger.log(logger.INFO, "BrokerConsumers.removeConsumer:"+uid+
                          ", pending="+pendingMsgs+", cleanup="+cleanup+", cparentmp="+cparentmp);
              }
              Iterator itr = deliveredMessages.values().iterator();
              while (itr.hasNext()) {
                  AckEntry e = (AckEntry)itr.next();
                  if (!e.getConsumerUID().equals(uid)) {
                      continue; 
                  }
                  if (pendingMsgs != null) {
                      Iterator<Map.Entry<TransactionUID, LinkedHashMap<SysMessageID, Integer>>> 
                          itr1 = pendingMsgs.entrySet().iterator(); 
                      Map.Entry<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pair = null;
                      TransactionUID tid = null;
                      LinkedHashMap<SysMessageID, Integer> sysiddcts = null;
                      Set<SysMessageID> mysysids = null;
                      Set<AckEntry> ackentries = null;
                      boolean found = false;
                      Integer deliverCnt = null;
                      while (itr1.hasNext()) {
                          pair = itr1.next();
                          tid = pair.getKey();
                          sysiddcts = pair.getValue();
                          deliverCnt = sysiddcts.get(e.getSysMessageID());
                          if (deliverCnt != null) {
                              if (tid != null && tid.longValue() == 0L) {
                                  updateConsumed(e, deliverCnt, false);
                                  continue;
                              }
                              if (cleanup || e.getTUID() != null) {
                                  updateConsumed(e, deliverCnt, false);
                                  break;
                              }
                              found = true; 
                              ackentries = mypending.get(tid);
                              if (ackentries == null) {
                                  ackentries = new LinkedHashSet();
                                  mypending.put(tid, ackentries);
                              }
                              if (tid != null && !haspendingtxn) {
                                  haspendingtxn = true;
                              }
                              ackentries.add(e);
                              updateConsumed(e, deliverCnt, false);
                              break;
                          }
                      }
                      if (found) {
                          continue;
                      }
                  }
                  if (e.getTUID() != null) {
                      continue;
                  }
                  if (getDEBUG()) {
                      logger.log(logger.DEBUG, 
                      "BrokerConsumers.removeConsumer:"+uid+", remove AckEntry="+e+ ", c="+c);
                  }
                  itr.remove();
                  if (cleanup) {
                      updateConsumed(e, Integer.valueOf(1), true);
                  }
                  if (c != null) {
                      if (c.isFalconRemote()) {
                          e.acknowledged(false);
                      } else {
                          PacketReference ref = e.getReference();
                          if (ref != null)  {
                              ref.removeInDelivery(e.getStoredConsumerUID());
                              destroySet.add(ref);
                          }
                      }
                      continue;
                  }
                  PacketReference ref = e.getReference();
                  if (ref != null) {
                      ref.removeInDelivery(e.getStoredConsumerUID());
                  }
                  openacks.add(e);
              }
              itr = openacks.iterator();
              while (itr.hasNext()) {
                  AckEntry e = (AckEntry)itr.next();
                  if (cparentmp == null || cparentmp.size() == 0) {
                      e.acknowledged(false);
                  } else { 
                      PacketReference ref = e.getReference();
                      if (ref != null) {
                          PartitionedStore ps = null;  
                          if (!ref.getDestinationUID().isQueue()) {
                              ps = new NoPersistPartitionedStoreImpl(e.getStoredConsumerUID());
                          } else {
                              ps = ref.getPartitionedStore();
                          }
                          LinkedHashSet set = openmsgmp.get(ps);
                          if (set == null) {
                              set = new LinkedHashSet();
                              openmsgmp.put(ps, set);
                          }
                          set.add(ref);
                      }
                  }
              }
              if (cparentmp != null && cparentmp.size() > 0 && openmsgmp.size() > 0) {
                  itr = openmsgmp.entrySet().iterator();
                  PartitionedStore ps = null;  
                  Prioritized pl = null;
                  Map.Entry pair = null;
                  while (itr.hasNext()) {
                      pair = (Map.Entry)itr.next();
                      ps = (PartitionedStore)pair.getKey();
                      pl = (Prioritized)cparentmp.get(ps);
                      if (pl != null) {
                          pl.addAllOrdered((Set)pair.getValue());
                      } else {
                          logger.log(logger.WARNING, "Message(s) "+cparentmp.get(ps)+
                          "["+ps+"] parentlist not found on removing consumer "+uid);
                      }
                  }
              }
              if (cleanup || pendingMsgs == null) {
                  cleanupList.remove(uid);
                  pendingConsumerUIDs.remove(uid);
              } else if (mypending.size() > 0) {
                  pendingConsumerUIDs.put(uid, mypending);
              }
          }

          if (c != null) {
              c.destroyConsumer(destroySet, false, false);
              if (d != null)  {
              d.removeConsumer(uid, false);
              }
          }

          List<Set<AckEntry>> l = null;
          synchronized(pendingConsumerUIDs) {
              l = new ArrayList<Set<AckEntry>>(mypending.values());
          }
          Set<AckEntry> entries = null;
          Iterator<Set<AckEntry>> itr = l.iterator();
          while (itr.hasNext()) {
              entries = itr.next();
              AckEntry entry = null;
              Iterator<AckEntry> itr1 = entries.iterator();
              while (itr1.hasNext()) {
                  entry = itr1.next();
                  entry.pendingStarted();
              }
          }
          if (haspendingtxn) {
              notifyPendingCheckTimer();
          }
    }

    private void updateConsumed(AckEntry e, Integer deliverCnt, boolean cleanup) {
        int cnt = deliverCnt.intValue();
        if (cnt <= 0) {
            cnt = 1;
        }
        PacketReference pr = e.getReference();
        if (pr != null && !e.hasMarkConsumed()) {
            ConsumerUID cuid = e.getConsumerUID();
            ConsumerUID suid = e.getStoredConsumerUID();
            try {
                 pr.updateForJMSXDeliveryCount(suid, cnt, !cleanup);
                 e.markConsumed();
             } catch (Exception ex) {
                 Object[] args = { "["+pr+","+suid+"]", cuid, ex.getMessage() };
                 logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                 BrokerResources.W_UNABLE_UPDATE_REF_STATE_ON_CLOSE_CONSUMER, args)+
                 (cleanup ? "[RC]":"[R]"), ex);
             }
        }
    }

    /**
     *  This method must be called only when holding deliveredMessages lock
     */
    private void cleanupPendingConsumerUID(
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid,
                                           SysMessageID sysid) {

        assert Thread.holdsLock(deliveredMessages);

        Map<TransactionUID, Set<AckEntry>> pending = pendingConsumerUIDs.get(cuid);
        if (pending == null) {
            return;
        }
        Set<AckEntry> entries = null;
        Iterator<Map.Entry<TransactionUID, Set<AckEntry>>> itr = pending.entrySet().iterator();
        Map.Entry<TransactionUID, Set<AckEntry>> pair = null;
        while (itr.hasNext()) {
            pair = itr.next();
            entries = pair.getValue();
            if (entries.remove(new AckEntry(sysid, cuid, null))) {
                if (entries.isEmpty()) {
                    itr.remove();
                    if (pending.isEmpty()) { 
                        pendingConsumerUIDs.remove(cuid);
                        cleanupList.remove(cuid);
                    }
                }
                return;
            }
        }
    }

    private List<AckEntry> getPendingConsumerUID(TransactionUID tid) {

        assert Thread.holdsLock(deliveredMessages);

        Set<Map<TransactionUID, Set<AckEntry>>> maps = null;
        synchronized(pendingConsumerUIDs) {
            maps = new LinkedHashSet<Map<TransactionUID, Set<AckEntry>>>(
                           pendingConsumerUIDs.values());
        }
        Map<TransactionUID, Set<AckEntry>> map = null;
        Iterator<Map<TransactionUID, Set<AckEntry>>> itr = maps.iterator();
        while (itr.hasNext()) {
            map = itr.next();
            if (map == null) {
                continue;
            }
            TransactionUID mytid = null;
            Set<AckEntry> entries = null;
            Map.Entry<TransactionUID, Set<AckEntry>> pair = null;
            Iterator<Map.Entry<TransactionUID, Set<AckEntry>>> itr1 =
                                             map.entrySet().iterator();
            while (itr1.hasNext()) {
                pair = itr1.next();
                mytid = pair.getKey();
                if (mytid == null || !mytid.equals(tid)) {
                    continue;
                }
                return new ArrayList<AckEntry>(pair.getValue());
            }
        }
        return new ArrayList<AckEntry>();
    }

                    
    public boolean acknowledgeMessageFromRemote(int ackType, SysMessageID sysid,
                              com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid,
                              Map optionalProps) throws BrokerException {

        if (ackType == ClusterBroadcast.MSG_DELIVERED) {
            Consumer c = Consumer.getConsumer(cuid);
            if (c != null) {
                if (optionalProps != null) {
                    Integer pref = (Integer)optionalProps.get(Consumer.PREFETCH);
                    if (pref != null) {
                        int prefetch = Consumer.calcPrefetch(c, pref.intValue());
                        if (prefetch <= 0 || prefetch > BTOBFLOW) {
                            prefetch = BTOBFLOW;
                        }
                        c.resumeFlow(prefetch); 
                    } else {
                        c.resumeFlow();
                    }
                } else {
                    c.resumeFlow();
                }
            }
            return true;
        }
        if (ackType == ClusterBroadcast.MSG_IGNORED) {
            String tid = null, tidi = null, tido = null, tida= null;
            if (optionalProps != null && 
                ((tidi = (String)optionalProps.get(ClusterBroadcast.RB_RELEASE_MSG_INACTIVE)) != null ||
                 (tido = (String)optionalProps.get(ClusterBroadcast.RB_RELEASE_MSG_ORPHAN)) != null ||
                 (tida = (String)optionalProps.get(ClusterBroadcast.RB_RELEASE_MSG_ACTIVE)) != null ||
                 optionalProps.get(ClusterBroadcast.RC_RELEASE_MSG_INACTIVE) != null)) { 

                tid = (tidi == null ? (tido == null ? tida:tido):tidi);

                String logstrop = " for rollback remote transaction "+tid;
                String logstrtyp = "";
                if (tidi == null && tido == null && tida == null) {
                    logstrop = " CLIENT_ACKNOWLEDGE recover";
                } else {
                    logstrtyp = (tidi == null ? (tido == null ? "active":"orphaned"):"inactive");
                }
                if (getDEBUG()) {
                    logger.log(Logger.INFO, 
                    "Releasing message ["+sysid+", "+cuid+"]("+logstrtyp+")"+logstrop);
                }
                AckEntry entry = new AckEntry(sysid, cuid, null);
                AckEntry value = null;
                Consumer tidac = null; 
                synchronized(deliveredMessages) {
                    if (tida != null) {
                        value = (AckEntry)deliveredMessages.get(entry);
                        if (value != null) {
                            tidac = (Consumer)consumers.get(value.getConsumerUID());
                            if (tidac == null || 
                                value.isPendingStarted() ||
                                value.getTUID() == null || 
                                !value.getTUID().toString().equals(tida)) {
                                value = null;
                            }
                        }
                        if (value != null) {
                            value = (AckEntry)deliveredMessages.remove(entry);
                        }
                    } else {
                        value = (AckEntry)deliveredMessages.remove(entry);
                        cleanupPendingConsumerUID(cuid, sysid);
                    }
                }
                if (value == null) {
                    if (getDEBUG()) {
                        logger.log(Logger.INFO, 
                        "Releasing message ["+sysid+", "+cuid+"]("+logstrtyp+")"+logstrop+": entry not found");
                    }
                    return true;
                }
                PacketReference ref =value.getReference();
                if (ref == null) {
                    if (getDEBUG()) {
                        logger.log(Logger.INFO, 
                        "Releasing message ["+value+"]("+logstrtyp+") ref null"+logstrop);
                    }
                    return true;
                }
                if (tidac != null) {
                    Object[] args = { sysid, tid, tidac.toString() };
                    logger.log(logger.INFO, 
                        br.getKString(br.I_RELEASE_MSG_FOR_ACTIVE_REMOTE_CONSUMER, args)); 
                    ArrayList list = new ArrayList();
                    list.add(ref);
                    tidac.routeMessages(list, true /*front*/);
                    return true;
                }
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = value.getStoredConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = value.getConsumerUID();
                if (sid== null || sid == uid) {
                    try {
                        if (ref.acknowledged(uid, sid, !uid.isDupsOK(), false)) {
                            Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                                                 ref.getDestinationUID());
                            Destination d = ds[0];
                            d.removeMessage(ref.getSysMessageID(), RemoveReason.ACKNOWLEDGED);
                        }
                    } catch (Exception e) {
                        logger.logStack(Logger.WARNING,
                        "Unable to cleanup message "+ref.getSysMessageID()+logstrop, e);
                    }
                    return true;
                }
                    
                ref.removeInDelivery(value.getStoredConsumerUID());
                ref.getDestination().forwardOrphanMessage(ref, value.getStoredConsumerUID()); 
                return true;
            }

            if (optionalProps != null && 
                optionalProps.get(ClusterBroadcast.MSG_NOT_SENT_TO_REMOTE) != null &&
                ((String)optionalProps.get(ClusterBroadcast.MSG_NOT_SENT_TO_REMOTE)).equals("true")) {
                AckEntry entry = new AckEntry(sysid, cuid, null);
                AckEntry value = null;
                synchronized(deliveredMessages) {
                    value = (AckEntry)deliveredMessages.get(entry);
                }
                if (value == null) {
                    return true;
                }
                PacketReference ref =value.getReference();
                if (ref == null || ref.isDestroyed() || ref.isInvalid()) {
                    if (getDEBUG()) {
                    logger.log(Logger.INFO, "Cleanup dead message (not remote delivered): "+ value);
                    }
                    synchronized(deliveredMessages) {
                        deliveredMessages.remove(entry);
                    }
                }
                return true;
            }

           /* dont do anything .. we will soon be closing the consumer and that
            * will cause the right things to happen 
            */
            if (getDEBUG()) {
                logger.log(Logger.DEBUG, "got message ignored ack, can not process [" +
                                          sysid + "," + cuid+"]" + ackType);
            }
            return true;
        } 

        AckEntry entry = new AckEntry(sysid, cuid, null);
        AckEntry value = null;

        if (ackType == ClusterBroadcast.MSG_ACKNOWLEDGED) {
            synchronized(deliveredMessages) {
                value = (AckEntry)deliveredMessages.remove(entry);
                cleanupPendingConsumerUID(cuid, sysid);
            }
            if (value == null) {
                return true;
            }
            return value.acknowledged(false);
        }

        synchronized(deliveredMessages) {
            value = (AckEntry)deliveredMessages.remove(entry);
            cleanupPendingConsumerUID(cuid, sysid);

            if (ackType == ClusterBroadcast.MSG_DEAD ||
                ackType == ClusterBroadcast.MSG_UNDELIVERABLE) {
                boolean deliveredack = false;
                if (optionalProps != null) {
                    deliveredack = (optionalProps.get(ClusterBroadcast.MSG_DELIVERED_ACK) != null);
                }
                try {

                if (value != null && value.getTUID() != null) { //XXX 
                    logger.log(logger.WARNING, "Ignore mark message dead "+sysid+
                                      " for it's prepared with TUID= "+value.getTUID()); 
                    return false;
                }  
                if (value == null && !cuid.equals(PacketReference.getQueueUID())) {  
                    if (getDEBUG()) {
                        logger.log(logger.INFO, "Mark dead message: entry not found:"+sysid+","+cuid);
                    }
                    return false;
                }
                if (getDEBUG()) {
                    Globals.getLogger().log(logger.INFO,
                        "Dead message "+sysid+" notification from  "+cuid.getBrokerAddress()+
                        " for remote consumer "+ cuid);
                }

                if (value != null) {
                    PacketReference ref = value.getReference();
                    if (ref == null) {
                        ref = DL.get(null, sysid);
                    }
                    if (ref != null) {
                        ref.removeInDelivery(value.getStoredConsumerUID()); 
                        removeRemoteDeadMessage(ackType, ref, cuid, 
                             value.getStoredConsumerUID(), optionalProps);
                    }

                } else if (cuid.equals(PacketReference.getQueueUID())) {
                    Iterator itr = deliveredMessages.values().iterator();
                    int i = 0;
                    while (itr.hasNext()) {
                        AckEntry e = (AckEntry)itr.next();
                        if (e.getTUID() != null) {
                            continue;
                        }
                        if (!e.getConsumerUID().getBrokerAddress().equals(cuid.getBrokerAddress())) {
                            continue;
                        }
                        if (!e.getSysMessageID().equals(sysid)) {
                            continue;
                        }
                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = e.getStoredConsumerUID();
                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = e.getConsumerUID();

                        PacketReference ref = e.getReference();
                        if (ref == null) {
                            ref = DL.get(null, sysid); 
                        }
                        if (ref != null) {
                            ref.removeInDelivery(e.getStoredConsumerUID()); 
                            removeRemoteDeadMessage(ackType, ref, uid, sid, optionalProps);
                        } 
                        if (getDEBUG()) {
                            logger.log(Logger.INFO, "Cleanup remote dead ack entries("+(i++)+"th): "+ e);
                        }
                        itr.remove();
                    }
                }
                return true;

                } finally {
                if (deliveredack) {     
                    Consumer c = Consumer.getConsumer(cuid);
                    if (c != null) {
                        c.resumeFlow(1);
                    }
                }
                }

            } else {
                logger.log(logger.ERROR, 
                "Internal Error: ackMessageFromRemote: unexpetected ackType:"+ackType);
                return false;
            }
        }
    }

    private boolean removeRemoteDeadMessage(int ackType, PacketReference ref, 
                                            ConsumerUID cuid, ConsumerUID storedid,
                                            Map optionalProps)
                                            throws BrokerException {
        if (ref == null) {
            return true;
        }

        Destination d= ref.getDestination();
        Queue[] qs = DL.getDMQ(ref.getPartitionedStore());
        if (d == qs[0]) {
            // already gone, ignore
            return true;
        }
        // first pull out properties
        String comment = null;
        RemoveReason reason = null;
        Exception ex = null;
        Integer deliverCnt = null;
        Integer reasonInt = null;
        String deadbkr = null;
        if (optionalProps != null) {
            comment = (String)optionalProps.get(DMQ.UNDELIVERED_COMMENT);
            ex = (Exception)optionalProps.get(DMQ.UNDELIVERED_EXCEPTION);
            deliverCnt = (Integer)optionalProps.get(Destination.TEMP_CNT);
            reasonInt = (Integer)optionalProps.get("REASON");
            deadbkr = (String)optionalProps.get(DMQ.DEAD_BROKER);
        }
        RemoveReason rr = null;
        if (ackType == ClusterBroadcast.MSG_UNDELIVERABLE) {
            rr = RemoveReason.UNDELIVERABLE;
        } else {
            rr = RemoveReason.ERROR;
            if (reasonInt != null) {
                rr = RemoveReason.findReason(reasonInt.intValue());
            }
        }
        if (comment == null) {
            comment = "none";
        }
        if (ref.markDead(cuid, storedid, comment, ex, rr, 
                         (deliverCnt == null ? 0 : deliverCnt.intValue()),
                         deadbkr)) {
            try {

            if (ref.isDead()) { 
                if (getDEBUG()) {
                    Globals.getLogger().log(logger.INFO,
                    "Remove dead message "+ref+
                    " for remote consumer "+ cuid +" on destination "+d+" with reason "+rr);
                }
                try {
                    d.removeDeadMessage(ref);
                } catch (Exception e) {
                    logger.log(logger.WARNING, 
                    "Unable to remove dead["+rr+", "+deadbkr+"] message "+ref+"["+cuid+"]: "+e.getMessage(), e);
                }
            }

            } finally {
                ref.postAcknowledgedRemoval();
            }
            return true;
        } 
        return false;
    }

    public void acknowledgeMessageFromRemote2P(int ackType, SysMessageID[] sysids,
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID[] cuids, 
                Map optionalProps, Long txnID,
                com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker)
                throws BrokerException
    {
        if (txnID == null) {
            throw new BrokerException("Internal Error: call with null txnID"); 
        }

        //handle 2-phase remote ack

        TransactionUID tid = new TransactionUID(txnID.longValue());
        if (ackType == ClusterBroadcast.MSG_PREPARE) {

            PartitionedStore refpstore = null;
            TransactionAcknowledgement ta = null;
            ArrayList<TransactionAcknowledgement> tltas = null;
            TransactionAcknowledgement[] tas = null;
            HashMap<TransactionList, ArrayList<TransactionAcknowledgement>> tltasmap = 
                    new HashMap<TransactionList, ArrayList<TransactionAcknowledgement>>(); 
            TransactionList tl = null;
            AckEntry entry = null, value = null;
            StringBuffer dbuf = new StringBuffer();
            AckEntryNotFoundException ae = null;
            synchronized(deliveredMessages) {
                for (int i = 0; i < sysids.length; i++) {
                    entry =  new AckEntry(sysids[i], cuids[i], null); 
                    value = (AckEntry)deliveredMessages.get(entry);
                    if (value == null) { //XXX
                        String emsg = "["+sysids[i]+":"+cuids[i]+"]TID="+tid+" not found, maybe rerouted";
                        if (ae == null) {
                            ae = new AckEntryNotFoundException(emsg);
                        }
                        ae.addAckEntry(sysids[i], cuids[i]);
                        logger.log(logger.WARNING,
                        "["+sysids[i]+":"+cuids[i]+"] not found for preparing remote transaction "+tid+", maybe rerouted");
                        continue;
                    }
                    if (value.getTUID() != null) { 
                        String emsg = "["+sysids[i]+":"+cuids[i]+"]TID="+tid+"  has been rerouted";
                        if (ae == null) {
                            ae = new AckEntryNotFoundException(emsg);
                        }
                        ae.addAckEntry(sysids[i], cuids[i]);
                        logger.log(logger.WARNING, "["+sysids[i]+":"+cuids[i] + "] for preparing remote transaction "
                        +tid + " conflict with transaction "+value.getTUID());
                        continue;
                    }
                    PacketReference ref = value.getReference();
                    if (ref == null) {
                        deliveredMessages.remove(entry);
                        String emsg = "Unable to prepare ["+sysids[i]+":"+cuids[i]+"]TID="+tid+
                                      " because the message has been removed";
                        if (ae == null) {
                            ae = new AckEntryNotFoundException(emsg);
                        }
                        ae.addAckEntry(sysids[i], cuids[i]);
                        logger.log(logger.WARNING, emsg);
                        continue;
                    }
                    com.sun.messaging.jmq.jmsserver.core.ConsumerUID scuid = value.getStoredConsumerUID();
                    ta = new TransactionAcknowledgement(sysids[i], cuids[i], scuid);
                    if (!scuid.shouldStore() || !ref.isPersistent()) {
                        ta.setShouldStore(false); 
                    }
                    if (!Globals.getDestinationList().isPartitionMode()) {
                        TransactionList[] tls = DL.getTransactionList(
                                          Globals.getStore().getPrimaryPartition()); 
                        tl = tls[0];
                    } else {
                        refpstore = ref.getPartitionedStore();
                        TransactionList[] tls = Globals.getDestinationList().
                                                getTransactionList(refpstore);
                        tl = tls[0];
                        if (tl == null) {
                            deliveredMessages.remove(entry);
                            String emsg = "Unable to prepare ["+sysids[i]+":"+cuids[i]+"]TID="+tid+
                                          " because transaction list for partition "+refpstore+" not found";
                            if (ae == null) {
                                ae = new AckEntryNotFoundException(emsg);
                            }
                            ae.addAckEntry(sysids[i], cuids[i]);
                            logger.log(logger.WARNING, emsg);
                            continue;
                        }
                    }
                    tltas = tltasmap.get(tl);
                    if (tltas == null) {
                        tltas = new ArrayList<TransactionAcknowledgement>();
                        tltasmap.put(tl, tltas);
                    }
                    tltas.add(ta);
                    if (getDEBUG()) {
                        dbuf.append("\n\t["+ta+"]"+tl);
                    }
                }
                if (ae != null) {
                    throw ae;
                }
                Iterator<Map.Entry<TransactionList, 
                         ArrayList<TransactionAcknowledgement>>> itr = 
                    tltasmap.entrySet().iterator();
                Map.Entry<TransactionList, 
                    ArrayList<TransactionAcknowledgement>> pair = null;
                while (itr.hasNext()) {
                    pair = itr.next();
                    tl = pair.getKey();
                    tltas = pair.getValue();
                    tas = (TransactionAcknowledgement[])tltas.toArray(
                          new TransactionAcknowledgement[tltas.size()]);
                    TransactionState ts = new TransactionState(AutoRollbackType.NOT_PREPARED, 0L, true);
                    ts.setState(TransactionState.PREPARED);
                    if (getDEBUG()) {
                        logger.log(logger.INFO, 
                        "Preparing remote transaction "+tid + " for ["+tltas+"]"+tl+" from "+txnHomeBroker);
                    }
                    tl.logRemoteTransaction(tid, ts, tas, 
                                  txnHomeBroker, false, true, true);
                }
                for (int i = 0; i < sysids.length; i++) {
                    entry =  new AckEntry(sysids[i], cuids[i], null); 
                    value = (AckEntry)deliveredMessages.get(entry);
                    value.setTUID(tid);
                }
            }
            Iterator<TransactionList> itr = tltasmap.keySet().iterator();
            while (itr.hasNext()) {
                tl = itr.next();
                tl.pendingStartedForRemotePreparedTransaction(tid);
            }
            notifyPendingCheckTimer();
            if (getDEBUG()) {
                logger.log(logger.INFO, "Prepared remote transaction "+tid + " from "+txnHomeBroker+dbuf.toString());
            }
            return;
        }

        if (ackType == ClusterBroadcast.MSG_ROLLEDBACK) {
            if (getDEBUG()) {
            logger.log(logger.INFO, "Rolling back remote transaction "+tid + " from "+txnHomeBroker);
            }
            List<Object[]> list = TransactionList.getTransListsAndRemoteTranStates(tid);
            if (list == null) {
                if (getDEBUG()) {
                    logger.log(logger.INFO, "Rolling back non-prepared remote transaction "+tid + " from "+txnHomeBroker);
                }
                List<AckEntry> entries = null;
                synchronized(deliveredMessages) {
                    entries = getPendingConsumerUID(tid);
                    Iterator<AckEntry> itr = entries.iterator();
                    AckEntry e = null;
                    while (itr.hasNext()) {
                        e = itr.next();
                        if (deliveredMessages.get(e) == null) {
                            itr.remove();
                            continue;
                        }
                        if (consumers.get(e.getConsumerUID()) != null) {
                            itr.remove();
                            continue;
                        }
                    }
                    if (e != null) {
                        deliveredMessages.remove(e);
                        cleanupPendingConsumerUID(e.getConsumerUID(), e.getSysMessageID());
                    }
                }
                if (entries.size() == 0) {
                    logger.log(logger.INFO, br.getKString(
                        br.I_ROLLBACK_REMOTE_TXN_NOT_FOUND, tid, txnHomeBroker));
                    return;
                }
                Iterator<AckEntry> itr =  entries.iterator();
                while (itr.hasNext()) {
                    AckEntry e = itr.next();
                    SysMessageID sysid = e.getSysMessageID();
                    com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = e.getConsumerUID();
                    com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = e.getStoredConsumerUID();
                    if (suid == null) {
                        suid = cuid;
                    }
                    PacketReference ref = DL.get(null, sysid); //PART
                    if (ref == null) {
                        if (getDEBUG()) {
                        logger.log(logger.INFO,
                        "["+sysid+":"+cuid+"] reference not found in rolling back remote non-prepared transaction "+tid);
                        }
                        continue;
                    }
                    ref.removeInDelivery(suid);
                    ref.getDestination().forwardOrphanMessage(ref, suid);
                } 
                return;

            } //if list == null

            TransactionList tl =  null;
            Object[] oo = null;
            for (int li = 0; li < list.size(); li++) {

            oo =  list.get(li);
            tl = (TransactionList)oo[0];
            if (!tl.updateRemoteTransactionState(tid, 
                TransactionState.ROLLEDBACK, false, false, true)) {
                return;
            }

            if (tl.getRecoveryRemoteTransactionAcks(tid) != null) {
                rollbackRecoveryRemoteTransaction(tl, tid, txnHomeBroker); 
            }

            RemoteTransactionAckEntry tae =  tl.getRemoteTransactionAcks(tid);
            if (tae == null) {
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_NO_NONRECOVERY_TXNACK_TO_ROLLBACK, tid)); 
            } else if (tae.processed()) {
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_NO_MORE_TXNACK_TO_ROLLBACK, tid)); 
            } else if (!tae.isLocalRemote()) {

            TransactionAcknowledgement[] tas = tae.getAcks();
            Set s = new LinkedHashSet();
            AckEntry entry = null, value = null;
            for (int i = 0; i < tas.length; i++) {
                SysMessageID sysid = tas[i].getSysMessageID(); 
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = tas[i].getConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
                if (suid == null) {
                    suid = uid;
                }
                synchronized(deliveredMessages) {
                    entry =  new AckEntry(sysid, uid, null);
                    value = (AckEntry)deliveredMessages.get(entry);
                    if (value == null) {
                        if (getDEBUG()) {
                            logger.log(logger.INFO, 
                            "["+sysid+":"+uid+"] not found in rolling back remote transaction "+tid);
                        }
                        continue; 
                    }
                    if (value.getTUID() == null || !value.getTUID().equals(tid)) {
                        if (getDEBUG()) {
                            logger.log(logger.INFO, 
                            "["+sysid+":"+uid+"] with TUID="+value.getTUID()+
                            ", in confict for rolling back remote transaction "+tid);
                        }
                        continue;
                    }
                    if (consumers.get(uid) == null) {
                        deliveredMessages.remove(entry);
                        cleanupPendingConsumerUID(uid, sysid);
                        s.add(tas[i]);
                    } else {
                        value.setTUID(null);
                    }
                }
            }
            Iterator itr = s.iterator();
            while (itr.hasNext()) {
                TransactionAcknowledgement ta = (TransactionAcknowledgement)itr.next();
                SysMessageID sysid = ta.getSysMessageID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = ta.getConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = ta.getStoredConsumerUID();
                if (suid == null) {
                    suid = cuid;
                }
                PacketReference ref = DL.get(null, sysid); //PART
                if (ref == null) {
                    if (getDEBUG()) {
                    logger.log(logger.INFO, 
                    "["+sysid+":"+cuid+"] reference not found in rolling back remote transaction "+tid);
                    }
                    continue;
                }
                ref.removeInDelivery(suid); 
                ref.getDestination().forwardOrphanMessage(ref, suid); 
            }

            } //tas != null 

            try {
            tl.removeRemoteTransactionAck(tid); 
            } catch (Exception e) {
            logger.log(logger.WARNING, 
            "Unable to remove transaction ack for rolledback transaction "+tid+": "+e.getMessage());
            }
            try {
            tl.removeRemoteTransactionID(tid, true); 
            } catch (Exception e ) {
            logger.log(logger.WARNING, 
            "Unable to remove rolledback remote transaction "+tid+": "+e.getMessage());
            }

            } //for
            return;
        }

        int cLogRecordCount = 0;
        ArrayList cLogDstList = null;
        ArrayList cLogMsgList = null;
        ArrayList cLogIntList = null;

        if (ackType == ClusterBroadcast.MSG_ACKNOWLEDGED) {
            if (getDEBUG()) {
            logger.log(logger.INFO, "Committing remote transaction "+tid + " from "+txnHomeBroker);
            }
            List<Object[]> list = TransactionList.getTransListsAndRemoteTranStates(tid);
            if (list == null) {
                throw new BrokerException(
                "Committing remote transaction "+tid+" not found", Status.NOT_FOUND);
            }
            TransactionList tl = null;
            Object[] oo = null;
            for (int li = 0; li < list.size(); li++) {

            oo = list.get(li);
            tl = (TransactionList)oo[0];
            if (!tl.updateRemoteTransactionState(tid,
                    TransactionState.COMMITTED, (sysids == null), true, true)) {
                if (getDEBUG()) {
                logger.log(logger.INFO, "Remote transaction "+tid + " already committed, from "+txnHomeBroker);
                }
                continue;
            }
            boolean done = true;
            if (tl.getRecoveryRemoteTransactionAcks(tid) != null) {
                done = commitRecoveryRemoteTransaction(tl, tid, txnHomeBroker); 
            }
            RemoteTransactionAckEntry tae = tl.getRemoteTransactionAcks(tid);
            if (tae == null) {
                logger.log(logger.INFO, 
                "No non-recovery transaction acks to process for committing remote transaction "+tid);
            } else if (tae.processed()) {
                logger.log(logger.INFO, 
                "No more transaction acks to process for committing remote transaction "+tid);
            } else if (!tae.isLocalRemote()) {

            boolean found = false;
            TransactionAcknowledgement[] tas = tae.getAcks();
            for (int i = 0; i < tas.length; i++) {
                SysMessageID sysid = tas[i].getSysMessageID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = tas[i].getConsumerUID();
                if (sysids != null && !found) {
                    if (sysid.equals(sysids[0]) && cuid.equals(cuids[0])) { 
                        found = true;
                    }
                }

                String dstName = null;
                if (Globals.txnLogEnabled()) {
                    if (cLogDstList == null) {
                        cLogDstList = new ArrayList();
                        cLogMsgList = new ArrayList();
                        cLogIntList = new ArrayList();
                    }

                    PacketReference ref = DL.get(null, sysid);
                    if (ref != null && !ref.isDestroyed() && !ref.isInvalid()) {
                        Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                                             ref.getDestinationUID());
                        Destination dst = ds[0];
                        dstName = dst.getUniqueName();
                    }
                }

                if (acknowledgeMessageFromRemote(ackType, sysid, cuid, optionalProps)) {
                    if (dstName != null) {
                        // keep track for consumer txn log
                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
                        if (suid == null) suid = cuid;
                        cLogRecordCount++;
                        cLogDstList.add(dstName);
                        cLogMsgList.add(sysid);
                        cLogIntList.add(suid);
                    }
                } else {
                    done = false;
                }
            }
            
            // notify that message acks have been written to store
            if (Globals.isNewTxnLogEnabled()) {
                if (DL.isPartitionMode()) {
                    throw new BrokerException(
                    "Partition mode not supported if newTxnLog enabled");
                }
                ((TxnLoggingStore)Globals.getStore().getPrimaryPartition()).
                      loggedCommitWrittenToMessageStore(tid, 
                      BaseTransaction.REMOTE_TRANSACTION_TYPE);
            }
            
            if (sysids != null && !found) {
                logger.log(logger.ERROR, 
                "Internal Error: ["+sysids[0]+":"+cuids[0]+"] not found in remote transaction " +tid);
                done = false;
            }

            } //tae != null
            if (done) {
                try {
                tl.removeRemoteTransactionAck(tid); 
                } catch (Exception e) {
                logger.logStack(logger.WARNING, 
                "Unable to remove transaction ack for committed remote transaction "+tid, e);
                }
                try {
                tl.removeRemoteTransactionID(tid, true); 
                } catch (Exception e ) {
                logger.logStack(logger.WARNING, 
                "Unable to remove committed remote transaction "+tid, e);
                }
            } else if (Globals.getHAEnabled()) {
                throw new BrokerException(
                "Remote transaction processing incomplete, TUID="+tid);
            }

            } //for

            // log to txn log if enabled
            try {
                if (Globals.txnLogEnabled() && (cLogRecordCount > 0) ) {
                    // Log all acks for consuming txn
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(
                        (cLogRecordCount * (32 + SysMessageID.ID_SIZE + 8)) + 12);
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeLong(tid.longValue()); // Transaction ID (8 bytes)
                    dos.writeInt(cLogRecordCount); // Number of acks (4 bytes)
                    for (int i = 0; i < cLogRecordCount; i++) {
                        String dst = (String)cLogDstList.get(i);
                        dos.writeUTF(dst); // Destination
                        SysMessageID sysid = (SysMessageID)cLogMsgList.get(i);
                        sysid.writeID(dos); // SysMessageID
                        long intid =
                            ((com.sun.messaging.jmq.jmsserver.core.ConsumerUID)
                            cLogIntList.get(i)).longValue();
                        dos.writeLong(intid); // ConsumerUID
                    }
                    dos.close();
                    bos.close();
                    ((TxnLoggingStore)Globals.getStore().getPrimaryPartition()).logTxn(
                        TransactionLogType.CONSUME_TRANSACTION, bos.toByteArray()); //PART
                }
            } catch (IOException ex) {
                logger.logStack(Logger.ERROR,
                    Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR,
                    "Got exception while writing to transaction log", ex);
                throw new BrokerException(
                    "Internal Error: Got exception while writing to transaction log", ex);
            }

            return;
        }

        throw new BrokerException("acknowledgeMessageFromRemotePriv:Unexpected ack type:"+ackType);
    }

    private void rollbackRecoveryRemoteTransaction(TransactionList translist, TransactionUID tid, 
                 com.sun.messaging.jmq.jmsserver.core.BrokerAddress from)
                 throws BrokerException {
        logger.log(logger.INFO,"Rolling back recovery remote transaction " + tid + " from "+from);

        TransactionState ts = translist.getRemoteTransactionState(tid);
        if (ts == null || ts.getState() != TransactionState.ROLLEDBACK) { 
            throw new BrokerException(Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR,
            "Unexpected broker state "+ts+ " for processing Rolledback remote transaction "+tid);
        }
        TransactionBroker ba = translist.getRemoteTransactionHomeBroker(tid);
        BrokerAddress currba = (ba == null) ? null: ba.getCurrentBrokerAddress(); 
        if (currba == null || !currba.equals(from)) {
            logger.log(logger.WARNING, "Rolledback remote transaction "+tid+" home broker "+ba+ " not "+from);
        }
        RemoteTransactionAckEntry[] tae = translist.getRecoveryRemoteTransactionAcks(tid);
        if (tae == null) {
            logger.log(logger.WARNING, 
            "No recovery transaction acks to process for rolling back remote transaction "+tid);
            return;
        }
        for (int j = 0; j < tae.length; j++) { 
            if (tae[j].processed()) continue;
            TransactionAcknowledgement[] tas = tae[j].getAcks();
            for (int i = 0; i < tas.length; i++) {

            SysMessageID sysid = tas[i].getSysMessageID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = tas[i].getConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
            if (suid == null) suid = cuid;
            PacketReference ref = DL.get(null, sysid); //PART
            if (ref == null) {
                if (getDEBUG()) {
                logger.log(logger.INFO, 
                "["+sysid+":"+cuid+"] reference not found in rolling back recovery remote transaction "+tid);
                }
                continue;
            }
            ref.getDestination().forwardOrphanMessage(ref, suid); 

            }
        }
    }

    private boolean commitRecoveryRemoteTransaction(TransactionList translist, TransactionUID tid, 
                 com.sun.messaging.jmq.jmsserver.core.BrokerAddress from) 
                 throws BrokerException {
        logger.log(logger.INFO,"Committing recovery remote transaction " + tid + " from "+from);

        TransactionBroker ba = translist.getRemoteTransactionHomeBroker(tid);
        BrokerAddress currba = (ba == null) ? null: ba.getCurrentBrokerAddress();
        if (currba == null || !currba.equals(from)) {
            logger.log(logger.WARNING, "Committed remote transaction "+tid+" home broker "+ba+ " not "+from);
        }
        RemoteTransactionAckEntry[] tae = translist.getRecoveryRemoteTransactionAcks(tid);
        if (tae == null) {
            logger.log(logger.WARNING, 
            "No recovery transaction acks to process for committing remote transaction "+tid);
            return true;
        }
        boolean done = true;
        for (int j = 0; j < tae.length; j++) { 
            if (tae[j].processed()) continue;
            TransactionAcknowledgement[] tas = tae[j].getAcks();
            for (int i = 0; i < tas.length; i++) {

            SysMessageID sysid = tas[i].getSysMessageID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = tas[i].getConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
            if (suid == null) suid = uid;
            PacketReference ref = DL.get(null, sysid); //PART
            if (ref == null || ref.isDestroyed() || ref.isInvalid()) continue;
            try {
                if (ref.acknowledged(uid, suid, true, true)) {
                    ref.getDestination().removeMessage(ref.getSysMessageID(), 
                                                       RemoveReason.ACKNOWLEDGED);
                }
            } catch (Exception ex) {
                done = false;
                logger.logStack(Logger.ERROR, 
                Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR, ex.getMessage(), ex);
            }

            }
        }
        return done;
    }

    public void addConsumer(Consumer c) 
        throws BrokerException
    {
        if (getDEBUG()) {
            logger.log(logger.INFO, "BrokerConsumers.addConsumer: "+c);
        }

        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = c.getConsumerUID();
        if (consumers.get(cuid) != null) {
            String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.I_CONSUMER_ALREADY_ADDED, 
                              cuid, c.getDestinationUID());
            logger.log(logger.INFO, emsg+" (CLUSTER_ROUTER)");
            throw new ConsumerAlreadyAddedException(emsg);
        }

        DL.acquirePartitionLock(true);
        try {

        if (! (c instanceof Subscription)) {
            consumers.put(cuid, c);
            pendingConsumerUIDs.put(cuid, null);
            listeners.put(cuid, c.addEventListener(this, 
                 EventType.BUSY_STATE_CHANGED, null));
        }

        DestinationUID duid = c.getDestinationUID();

        int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE :
                     DestType.DEST_TYPE_TOPIC);

        Destination d= null;

        try {
            // ok handle adding a reference count
            // we'll try at most 2 times to get a
            // destination

            if (duid.isWildcard()) {
                d = null;

            } else {
                for (int i = 0; i < 2; i++) {
                    Destination[] ds = DL.getDestination(
                           Globals.getStore().getPrimaryPartition(),
                           duid.getName(), type, true, true);
                    d = ds[0];
    
                    try {
                        // increment the reference count
                        // this make sure that the destination
                        // is not removed by autocreate prematurely    
                        if (d != null) {
                            d.incrementRefCount();
    
                            break; // well we should break anyway, but
                                   // this makes it explicit
                        }
    
                    } catch (BrokerException ex) {
                        // incrementRefCount throws a BrokerException
                        // if the destination was destroyed
                        // if we are here then the destination went away
                        // try to get the destination again
                        d = null;
                    }
               }
               if (d == null)
                   throw new BrokerException("Unable to attach to destination "
                        + duid);
           }
        } catch (IOException ex) {
            throw new BrokerException("Unable to autocreate destination " +
                   duid , ex);
        }

        try {
            // OK, if we made it here, we have incremented the reference
            // count on the object, we need to make sure we decrement the RefCount
            // before we exit (so cleanup can occur)
            if (!c.getDestinationUID().isQueue() && 
                (!(c instanceof Subscription)) &&
                c.getSubscription() == null) {
                // directly send messages
                c.setFalconRemote(true);
            } else {
                int mp = (d == null ? -1 : d.getMaxPrefetch());
                if (mp <= 0 || mp > BTOBFLOW) {
                    mp = BTOBFLOW;
                }
                int prefetch = c.getRemotePrefetch();
                if (prefetch <= 0 || prefetch > mp) {
                    prefetch = mp;
                }
                Subscription sub = c.getSubscription();
                if (sub != null && sub.getShared()) {
                    prefetch = 1;
                }
                c.setPrefetch(prefetch);
            }

             try {
                 if (d == null && c.getSubscription() == null) {
                     //deal with wildcard subscription
                     Map<PartitionedStore, LinkedHashSet<Destination>> dmap =
                         DL.findMatchingDestinationMap(null, c.getDestinationUID());
                     LinkedHashSet dset = null;
                     Destination dd = null;
                     Iterator<LinkedHashSet<Destination>> itr  = dmap.values().iterator();
                     while (itr.hasNext()) {
                         dset = itr.next();
                         if (dset == null) {
                             continue;
                         }
                         Iterator<Destination> itr1 = dset.iterator();
                         while (itr1.hasNext()) {
                             dd = itr1.next();
                             if (dd == null) {
                                 continue;
                             }
                             try {
                                 dd.addConsumer(c, false);
                             } catch (ConsumerAlreadyAddedException e) {
                                 logger.log(logger.INFO, e.getMessage()+" (CLUSTER_ROUTER)");
                             }
                         }
                     }
                 } else if (c.getSubscription() == null) {
                     Map<PartitionedStore, LinkedHashSet<Destination>> dmap =
                         DL.findMatchingDestinationMap(null, c.getDestinationUID());
                     LinkedHashSet dset = null;
                     Destination dd = null;
                     Iterator<LinkedHashSet<Destination>> itr  = dmap.values().iterator();
                     while (itr.hasNext()) {
                         dset = itr.next();
                         if (dset == null) {
                             continue;
                         }
                         Iterator<Destination> itr1 = dset.iterator();
                         while (itr1.hasNext()) {
                             dd = itr1.next();
                             if (dd == null) {
                                 continue;
                             }
                             try {
                                 dd.addConsumer(c, false);
                             } catch (ConsumerAlreadyAddedException e) {
                                 logger.log(logger.INFO, e.getMessage()+" (CLUSTER_ROUTER)");
                             }
                         }
                     }
                 }
             } catch (SelectorFormatException ex) {
                 throw new BrokerException("unable to add destination " + d,
                       ex);
             }
        
            if (! (c instanceof Subscription)) {
                if (c.isBusy()) {
                    synchronized (activeConsumers) {
                        activeConsumers.add(c);
                        activeConsumers.notifyAll();
                    }
                }
            }
        } finally {
            // decrement the ref count since we've finished
            // processing the add consumer
            if (d != null) { // not wildcard
                d.decrementRefCount();
            }
        }

        } finally {
        DL.releasePartitionLock(true);
        }
    }

    public void run() {
        while (valid) {
            Consumer c = null;
            synchronized(activeConsumers) {
                while (valid && activeConsumers.isEmpty()) {
                    try {
                        activeConsumers.wait();
                    } catch (InterruptedException ex) {
                    }
                }
                if (valid) {
                    Iterator itr = activeConsumers.iterator();
                    c = (Consumer) itr.next();
                    itr.remove();
                    if (c.isBusy()) 
                        activeConsumers.add(c);
                }
            }
            if (c == null) continue;

            PacketReference ref =  null;
            HashSet s = null;
            boolean cb = false;
            synchronized(removeConsumerLock) {
                if (consumers.get(c.getConsumerUID()) == null) {
                    if (getDEBUG()) {
                    Globals.getLogger().log(Logger.INFO, 
                    "BrokerConsumers.run(): ignore removed consumer: "+c);
                    }
                    continue;
                }

                ref =  (PacketReference)c.getAndFillNextPacket(null);
                if (ref == null) continue;

                s = new HashSet();
                s.add(c);
                cb = ref.getMessageDeliveredAck(c.getConsumerUID()) 
                                 || c.isPaused();

                if (!c.getConsumerUID().isNoAck()) {
                    AckEntry entry = new AckEntry(ref, c.getConsumerUID(), 
                                                  c.getStoredConsumerUID());
                    synchronized(deliveredMessages) {
                        deliveredMessages.put(entry, entry);
                        if (getDEBUG()) {
                        logger.log(logger.DEBUG, "deliveredMessages:"+entry);
                        }
                    }
                }
            }
            protocol.sendMessage(ref, s, cb);
        }
    }
    
}

