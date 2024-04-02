/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.jmsserver.cluster.router;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.multibroker.Protocol;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;

/**
 * This class handles the processing of messages from other brokers in the cluster.
 */
public class MultibrokerRouter implements ClusterRouter {
    private static boolean DEBUG = false;

    private static boolean DEBUG_CLUSTER_TXN = Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.debug.txn");
    private static boolean DEBUG_CLUSTER_MSG = Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.debug.msg");

    private static Logger logger = Globals.getLogger();

    private static final String ENFORCE_REMOTE_DEST_LIMIT_PROP = Globals.IMQ + ".cluster.enforceRemoteDestinationLimit";
    private static boolean ENFORCE_REMOTE_DEST_LIMIT = Globals.getConfig().getBooleanProperty(ENFORCE_REMOTE_DEST_LIMIT_PROP, false);

    // obsolete private property
    // private static String ROUTE_REJECTED_REMOTE_MSG_PROP =
    // Globals.IMQ+".cluster.routeRejectedRemoteMsg"; //4.5

    private ArrayList loggedFullDestsOnHandleJMSMsg = new ArrayList();

    ClusterBroadcast cb = null;
    Protocol p = null;

    BrokerConsumers bc = null;
    DestinationList DL = Globals.getDestinationList();

    protected static boolean getDEBUG() {
        return (DEBUG || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG);
    }

    public MultibrokerRouter(ClusterBroadcast cb) {
        this.cb = cb;
        this.p = (Protocol) cb.getProtocol();
        bc = new BrokerConsumers(p);
    }

    public static String msgToString(int id) {
        switch (id) {
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

    @Override
    public void addConsumer(Consumer c) throws BrokerException, IOException, SelectorFormatException {
        bc.addConsumer(c);
    }

    @Override
    public void removeConsumer(com.sun.messaging.jmq.jmsserver.core.ConsumerUID c, Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs,
            boolean cleanup) throws BrokerException, IOException {

        bc.removeConsumer(c, pendingMsgs, cleanup);
    }

    @Override
    public void removeConsumers(ConnectionUID uid) throws BrokerException, IOException {
        bc.removeConsumers(uid);
    }

    @Override
    public void brokerDown(com.sun.messaging.jmq.jmsserver.core.BrokerAddress ba) throws BrokerException, IOException {
        bc.brokerDown(ba);
    }

    @Override
    public void forwardMessage(PacketReference ref, Collection consumers) {
        bc.forwardMessageToRemote(ref, consumers);
    }

    @Override
    public void shutdown() {
        bc.destroy();
    }

    @Override
    public void handleJMSMsg(Packet p, Map<ConsumerUID, Integer> consumers, BrokerAddress sender, boolean sendMsgDeliveredAck) throws BrokerException {

        Map<ConsumerUID, Integer> deliveryCnts = consumers;
        boolean hasflowcontrol = true;
        ArrayList<Consumer> targetVector = new ArrayList<>();
        ArrayList ignoreVector = new ArrayList();
        PartitionedStore pstore = Globals.getStore().getPrimaryPartition(); // PART

        Iterator<Map.Entry<ConsumerUID, Integer>> itr = consumers.entrySet().iterator();
        Map.Entry<ConsumerUID, Integer> entry = null;
        while (itr.hasNext()) {
            entry = itr.next();
            ConsumerUID uid = entry.getKey();
            Consumer interest = Consumer.getConsumer(uid);
            if (interest != null && interest.isValid()) {
                // we need the interest for updating the ref
                targetVector.add(interest);
                ConsumerUID suid = interest.getStoredConsumerUID();
                if ((suid == null || suid.equals(uid)) && interest.getSubscription() == null) {
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
        PacketReference ref = DL.get(null, p.getSysMessageID()); // PART
        if (ref != null) {
            BrokerAddress addr = ref.getBrokerAddress();
            if (addr == null || !addr.equals(sender)) {
                if (DEBUG) {
                    logger.log(Logger.INFO, "Remote message " + ref.getSysMessageID() + " home broker " + addr + " changed to " + sender);
                }
                DL.remoteCheckMessageHomeChange(ref, sender);
                if (addr == null) {
                    Object[] args = { ref.getSysMessageID(), sender, targetVector };
                    logger.log(logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_IGNORE_REMOTE_MSG_SENT_FROM, args));
                    ignoreVector.addAll(targetVector);
                    targetVector.clear();
                    sendIgnoreAck(p.getSysMessageID(), ref, sender, ignoreVector);
                    return;
                }
            }
        }
        List dsts = null;
        ref = DL.get(null, p.getSysMessageID()); // PART
        boolean acquiredWriteLock = false;
        try {

            if (ref != null) {
                ref.acquireDestroyRemoteWriteLock();
                acquiredWriteLock = true;
                if (ref.isInvalid() || ref.isDestroyed()) {
                    ref.clearDestroyRemoteWriteLock();
                    acquiredWriteLock = false;
                    ref = DL.get(null, p.getSysMessageID()); // PART
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
                    logger.log(logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_IGNORE_REMOTE_MSG_SENT_FROM, args));
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
                ref = PacketReference.createReference(pstore, p, null); // PART
                ref.setBrokerAddress(sender);
            }
            // XXX - note, we send a reply for all message delivered
            // acks, that is incorrect
            // really, we should have a sendMsgDeliveredAck per
            // consumer
            if (sendMsgDeliveredAck) {
                for (int i = 0; i < targetVector.size(); i++) {
                    Consumer c = targetVector.get(i);
                    // ref.addMessageDeliveredAck(c.getStoredConsumerUID());
                    ref.addMessageDeliveredAck(c.getConsumerUID());
                }
            }
            try {
                if (ref.getDestinationUID().isWildcard()) {
                    List[] dss = DL.findMatchingIDs(pstore, ref.getDestinationUID());
                    dsts = dss[0];
                } else {
                    // ok, autocreate the destination if necessary
                    Destination[] ds = DL.getDestination(pstore, ref.getDestinationUID().getName(),
                            ref.getDestinationUID().isQueue() ? DestType.DEST_TYPE_QUEUE : DestType.DEST_TYPE_TOPIC, true, true);
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
                            DestinationUID did = (DestinationUID) itr.next();
                            Destination[] ds = DL.getDestination(pstore, did);
                            Destination d = ds[0];
                            if (DEBUG) {
                                logger.log(logger.INFO,
                                        "Route remote message " + ref + " sent from " + sender + " to destination(s) " + did + " for consumer(s) "
                                                + targetVector + " hasflowcontrol=" + hasflowcontrol + ", enforcelimit=" + ENFORCE_REMOTE_DEST_LIMIT);
                            }
                            // add to message count
                            d.acquireQueueRemoteLock();
                            try {
                                PacketReference newref = d.getMessage(p.getSysMessageID());
                                if (newref != null) {
                                    ignoreVector.addAll(targetVector);
                                    targetVector.clear();
                                    Object[] args = { p.getSysMessageID(), "" + d.getDestinationUID(), sender, newref + "[" + newref.getBrokerAddress() + "]" };
                                    logger.log(logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_REMOTE_NEW_MSG_ROUTED_ALREADY, args));
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
                Object[] args = { "" + ref, sender, targetVector };
                String emsg = Globals.getBrokerResources().getKString(BrokerResources.W_EXCEPTION_PROCESS_REMOTE_MSG, args);
                if (!(ex instanceof BrokerException)) {
                    logger.logStack(logger.WARNING, emsg, ex);
                } else {
                    BrokerException be = (BrokerException) ex;
                    int status = be.getStatusCode();
                    if (status != Status.RESOURCE_FULL && status != Status.ENTITY_TOO_LARGE) {
                        logger.logStack(logger.WARNING, emsg, ex);
                    } else {
                        Object[] args1 = { sender, targetVector };
                        emsg = Globals.getBrokerResources().getKString(BrokerResources.W_PROCESS_REMOTE_MSG_DST_LIMIT, args1);
                        int level = Logger.DEBUG;
                        if (!loggedFullDestsOnHandleJMSMsg.contains(ref.getDestinationUID())) {
                            level = Logger.WARNING;
                            loggedFullDestsOnHandleJMSMsg.add(ref.getDestinationUID());
                        }
                        logger.log(level, emsg + (level == Logger.DEBUG ? ": " + ex.getMessage() : ""));
                    }
                }
            }

        } finally {
            if (ref != null && acquiredWriteLock) {
                ref.clearDestroyRemoteWriteLock();
            }
        }

        // Now deliver the message...
        StringBuilder debugString = new StringBuilder();
        debugString.append('\n');

        int i;
        for (i = 0; i < targetVector.size(); i++) {
            Consumer interest = targetVector.get(i);

            if (!interest.routeMessage(ref, false)) {
                // it disappeard on us, take care of it
                try {
                    if (ref.acknowledged(interest.getConsumerUID(), interest.getStoredConsumerUID(), true, false)) {
                        try {

                            if (dsts == null) {
                                continue;
                            }
                            itr = dsts.iterator();
                            while (itr.hasNext()) {
                                DestinationUID did = (DestinationUID) itr.next();
                                Destination[] ds = DL.getDestination(pstore, did);
                                Destination d = ds[0];
                                d.removeRemoteMessage(ref.getSysMessageID(), RemoveReason.ACKNOWLEDGED, ref);
                            }

                        } finally {
                            ref.postAcknowledgedRemoval();
                        }
                    }
                } catch (Exception ex) {
                    logger.log(logger.INFO, "Internal error processing ack", ex);

                } finally {
                    ref.removeRemoteConsumerUID(interest.getStoredConsumerUID(), interest.getConsumerUID());
                }
            }

            if (DEBUG) {
                debugString.append("\t" + interest.getConsumerUID() + "\n");
            }
        }

        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "MessageBus: Delivering message to : {0}", debugString.toString());
        }
        sendIgnoreAck(p.getSysMessageID(), ref, sender, ignoreVector);
    }

    private void sendIgnoreAck(SysMessageID sysid, PacketReference ref, BrokerAddress sender, List ignoredConsumers) {

        List ignoreVector = ignoredConsumers;
        StringBuilder debugString = new StringBuilder();
        debugString.append('\n');
        Object o = null;
        ConsumerUID cuid = null;
        for (int i = 0; i < ignoreVector.size(); i++) {
            try {
                o = ignoreVector.get(i);
                if (o instanceof Consumer) {
                    cuid = ((Consumer) o).getConsumerUID();
                } else {
                    cuid = (ConsumerUID) o;
                }
                cb.acknowledgeMessage(sender, (ref == null ? sysid : ref.getSysMessageID()), cuid, ClusterBroadcast.MSG_IGNORED, null, false);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, "sendMessageAck IGNORE failed to " + sender, e);
            }
            if (DEBUG) {
                debugString.append("\t" + ignoreVector.get(i) + "\n");
            }
        }

        if (DEBUG) {
            if (ignoreVector.size() > 0) {
                logger.log(logger.DEBUGHIGH, "MessageBus: Invalid targets : {0}", debugString.toString());
            }
        }
    }

    @Override
    public void handleAck(int type, SysMessageID sysid, ConsumerUID cuid, Map optionalProps) throws BrokerException {
        bc.acknowledgeMessageFromRemote(type, sysid, cuid, optionalProps);
    }

    @Override
    public void handleAck2P(int type, SysMessageID[] sysids, ConsumerUID[] cuids, Map optionalProps, Long txnID,
            com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker) throws BrokerException {
        bc.acknowledgeMessageFromRemote2P(type, sysids, cuids, optionalProps, txnID, txnHomeBroker);
    }

    @Override
    public void handleCtrlMsg(int type, HashMap props) throws BrokerException {
        // not implemented
    }

    @Override
    public Hashtable getDebugState() {
        return bc.getDebugState();
    }
}

