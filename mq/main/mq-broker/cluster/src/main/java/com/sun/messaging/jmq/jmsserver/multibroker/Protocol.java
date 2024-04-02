/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.FileTransferCallback;

public interface Protocol extends ClusterCallback {
    /**
     * Get the cluster protocol version used by this protocol implementation.
     */
    int getClusterVersion() throws BrokerException;

    /**
     * sets the list of properties that must match for brokers to connect
     */
    void setMatchProps(Properties matchProps);

    void startClusterIO();

    void stopClusterIO(boolean requestTakeover, boolean force, com.sun.messaging.jmq.jmsserver.core.BrokerAddress excludedBroker);

    void reloadCluster();

    void stopMessageFlow() throws IOException;

    void resumeMessageFlow() throws IOException;

    boolean waitForConfigSync();

    void sendMessage(PacketReference pkt, Collection<Consumer> targets, boolean sendMsgDeliveredAck);

    void sendMessageAck(com.sun.messaging.jmq.jmsserver.core.BrokerAddress msgHome, SysMessageID mid,
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID cid, int ackType, Map optionalProps, boolean ackack) throws BrokerException;

    void sendMessageAck2P(com.sun.messaging.jmq.jmsserver.core.BrokerAddress msgHome, SysMessageID[] mids,
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID[] cids, int ackType, Map optionalProps, Long txnID, UID txnStoreSession, boolean ackack,
            boolean async) throws BrokerException;

    void clientClosed(ConnectionUID conid, boolean notify);

    /**
     * Obtain a cluster-wide "shared" lock on a resource. Unlike the normal "exclusive" locks, the shared locks allow more
     * than one clients to access the same resource. This method ensures that the resource cannot be locked as shared and
     * exclusive at the same time!
     *
     * @param resId Resource name. The caller must ensure that there are no name space conflicts between different types of
     * resources. This can be achieved by simply using resource names like -"durable:foo", "queue:foo", "clientid:foo"...
     * @param owner The object representing the owner of the resource
     * @return ProtocolGlobals.G_LOCK_SUCCESS if the resource was locked successfully. ProtocolGlobals.G_LOCK_FAILURE if the
     * resource could not be locked.
     */
    int lockSharedResource(String resId, Object owner);

    /**
     * Obtain a cluster-wide lock on a resource. This method is used to ensure mutual exclusion for durable subscriptions,
     * queue receivers, client IDs etc.
     *
     * @param resId Resource name. The caller must ensure that there are no name space conflicts between different types of
     * resources. This can be achieved by simply using resource names like -"durable:foo", "queue:foo", "clientid:foo"...
     *
     * @param timestamp The creation time for the resource. In case of a lock contention the older resource automatically
     * wins.
     *
     * @param owner the owner object of the resource
     *
     * @return MB_LOCK_SUCCESS if the resource was locked successfully. MB_LOCK_FAILURE if the resource could not be locked.
     */
    int lockResource(String resId, long timestamp, Object owner);

    /**
     * Unlocks a resource.
     */
    void unlockResource(String resId);

    /**
     * Record the destination create / update event with the master broker. This method must be called before the
     * destination is added. If it throws an exception, the error must be propagated back to the client.
     */
    void recordUpdateDestination(Destination d) throws BrokerException;

    /**
     * Record the destroy destination event with the master broker. This method must be called before the destination is
     * deleted. If it throws an exception, the error must be propagated back to the client.
     */
    void recordRemoveDestination(Destination d) throws BrokerException;

    void sendNewDestination(Destination d) throws BrokerException;

    void sendRemovedDestination(Destination d) throws BrokerException;

    void sendUpdateDestination(Destination d) throws BrokerException;

    void recordCreateSubscription(Subscription sub) throws BrokerException;

    void recordUnsubscribe(Subscription sub) throws BrokerException;

    void sendNewSubscription(Subscription sub, Consumer cons, boolean active) throws BrokerException;

    void sendNewConsumer(Consumer intr, boolean active) throws BrokerException;

    void sendRemovedConsumer(Consumer intr, Map pendingMsgs, boolean cleanup) throws BrokerException;

    void handleGPacket(MessageBusCallback mbcb, com.sun.messaging.jmq.jmsserver.core.BrokerAddress sender, GPacket pkt);

    void preTakeover(String brokerID, UID storeSession, String brokerHost, UID brokerSession) throws BrokerException;

    void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify);

    void sendClusterTransactionInfo(long tid, com.sun.messaging.jmq.jmsserver.core.BrokerAddress to);

    void sendTransactionInquiry(TransactionUID tid, com.sun.messaging.jmq.jmsserver.core.BrokerAddress to);

    void sendPreparedTransactionInquiries(List<TransactionUID> tids, com.sun.messaging.jmq.jmsserver.core.BrokerAddress to);

    // in seconds
    int getClusterAckWaitTimeout();

    com.sun.messaging.jmq.jmsserver.core.BrokerAddress lookupBrokerAddress(String brokerid);

    com.sun.messaging.jmq.jmsserver.core.BrokerAddress lookupBrokerAddress(BrokerMQAddress mqaddr);

    String lookupStoreSessionOwner(UID session);

    void changeMasterBroker(BrokerMQAddress newmaster, BrokerMQAddress oldmaster) throws BrokerException;

    String sendTakeoverMEPrepare(String brokerID, byte[] commitToken, Long syncTimeout, String uuid) throws BrokerException;

    String sendTakeoverME(String brokerID, String uuid) throws BrokerException;

    void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID) throws BrokerException;

    void transferFiles(String[] fileNames, String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID, String module,
            FileTransferCallback callback) throws BrokerException;

    void notifyPartitionArrival(UID partitionID, String brokerID) throws BrokerException;

    Hashtable getDebugState();
}
