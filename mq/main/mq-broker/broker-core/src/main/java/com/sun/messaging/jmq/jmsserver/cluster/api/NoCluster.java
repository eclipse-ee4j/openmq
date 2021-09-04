/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.cluster.api;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * Simple message bus implementation which can be used in non-clustered environments.
 */
public class NoCluster implements ClusterBroadcast {

    private static final Object noOwner = new Object();

    private static BrokerAddress noAddress = new BrokerAddress() {
        private static final long serialVersionUID = -915287267942714056L;
        String address = "localhost";

        @Override
        public Object clone() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BrokerAddress;
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }

        @Override
        public String toProtocolString() {
            return null;
        }

        @Override
        public BrokerAddress fromProtocolString(String s) {
            throw new UnsupportedOperationException(this.getClass().getName() + ".fromProtocolString");
        }

        @Override
        public void writeBrokerAddress(DataOutputStream os) {
        }

        @Override
        public void readBrokerAddress(DataInputStream dis) {
        }

        @Override
        public boolean getHAEnabled() {
            return false;
        }

        @Override
        public String getBrokerID() {
            return null;
        }

        @Override
        public UID getBrokerSessionUID() {
            return null;
        }

        @Override
        public UID getStoreSessionUID() {
            return null;
        }

        @Override
        public void setStoreSessionUID(UID uid) {
        }

        @Override
        public String getInstanceName() {
            return null;
        }
    };

    @Override
    public int getClusterVersion() {
        return VERSION_350;
    }

    @Override
    public void messageDelivered(SysMessageID id, ConsumerUID uid, BrokerAddress ba) {
    }

    @Override
    public void init(int version) throws BrokerException {
    }

    @Override
    public Object getProtocol() {
        return null;
    }

    /**
     * Set the matchProps for the cluster.
     */
    @Override
    public void setMatchProps(Properties matchProps) {
    }

    @Override
    public boolean waitForConfigSync() {
        return false;
    }

    @Override
    public void startClusterIO() {
    }

    @Override
    public void pauseMessageFlow() throws IOException {
    }

    @Override
    public void resumeMessageFlow() throws IOException {
    }

    @Override
    public void forwardMessage(PacketReference ref, Collection consumers) {
    }

    @Override
    public void stopClusterIO(boolean requestTakeover, boolean force, BrokerAddress excludedBroker) {
    }

    /**
     * Returns the address of this broker.
     *
     * @return <code> BrokerAddress </code> object representing this broker.
     */
    @Override
    public BrokerAddress getMyAddress() {
        return noAddress;
    }

    private static Map map = Collections.synchronizedMap(new HashMap());

    @Override
    public boolean lockSharedResource(String resource, Object owner) {
        return true;
    }

    @Override
    public boolean lockExclusiveResource(String resource, Object owner) {
        return true;
    }

    @Override
    public void unlockExclusiveResource(String resource, Object owner) {
    }

    @Override
    public boolean lockDestination(DestinationUID uid, Object owner) {
        // unnecessary in single broker implementation
        return true;
    }

    @Override
    public void unlockDestination(DestinationUID uid, Object owner) {
        // unnecessary in single broker
    }

    @Override
    public synchronized int lockClientID(String clientid, Object owner, boolean shared) {
        if (shared) {
            throw new RuntimeException("shared clientID's not supported w/o cluster");
        }
        String lockid = "clientid:" + clientid;
        return lockResource(lockid, System.currentTimeMillis(), owner);
    }

    @Override
    public synchronized void unlockClientID(String clientid, Object owner) {
        String lockid = "clientid:" + clientid;
        unlockResource(lockid);
    }

    @Override
    public boolean getConsumerLock(ConsumerUID uid, DestinationUID duid, int position, int maxActive, Object owner) throws BrokerException {

        return true;
    }

    @Override
    public void unlockConsumer(ConsumerUID uid, DestinationUID duid, int position) {
        // for now, do nothing
    }

    public int lockResource(String id, long timestamp, Object owner) {
        synchronized (map) {
            Object val = map.get(id);
            if (val != null) {
                return ClusterBroadcast.LOCK_FAILURE;
            }
            if (owner == null) {
                owner = noOwner;
            }
            map.put(id, owner);
            return ClusterBroadcast.LOCK_SUCCESS;
        }
    }

    public void unlockResource(String id) {
        map.remove(id);
    }

    public void freeAllLocks(Object owner) {
        synchronized (map) {
            Iterator itr = map.values().iterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                if (o.equals(owner)) {
                    itr.remove();
                }
            }
        }
    }

    @Override
    public void acknowledgeMessage(BrokerAddress address, SysMessageID sysid, ConsumerUID cuid, int ackType, Map optionalProps, boolean ackack)
            throws BrokerException {
    }

    @Override
    public void acknowledgeMessage2P(BrokerAddress address, SysMessageID[] sysids, ConsumerUID[] cuids, int type, Map optProp, Long txnID, UID txnStoreSession,
            boolean ackack, boolean async) throws BrokerException {
        throw new BrokerException("Broker Internal Error: unexpected call acknowledgeMessage");
    }

    @Override
    public void recordUpdateDestination(Destination d) throws BrokerException {
    }

    @Override
    public void recordRemoveDestination(Destination d) throws BrokerException {
    }

    @Override
    public void createDestination(Destination dest) throws BrokerException {
    }

    @Override
    public void recordCreateSubscription(Subscription sub) throws BrokerException {
    }

    @Override
    public void recordUnsubscribe(Subscription sub) throws BrokerException {
    }

    @Override
    public void createSubscription(Subscription sub, Consumer cons) throws BrokerException {
    }

    @Override
    public void createConsumer(Consumer con) throws BrokerException {
    }

    @Override
    public void updateDestination(Destination dest) throws BrokerException {
    }

    @Override
    public void updateSubscription(Subscription sub) throws BrokerException {
    }

    @Override
    public void updateConsumer(Consumer con) throws BrokerException {
    }

    @Override
    public void destroyDestination(Destination dest) throws BrokerException {
    }

    @Override
    public void destroyConsumer(Consumer con, Map pendingMsgs, boolean cleanup) throws BrokerException {
    }

    @Override
    public void connectionClosed(ConnectionUID uid, boolean admin) {
        freeAllLocks(uid);
    }

    @Override
    public void reloadCluster() {
    }

    @Override
    public Hashtable getAllDebugState() {
        return new Hashtable();
    }

    @Override
    public boolean lockUIDPrefix(short p) {
        return true;
    }

    @Override
    public void preTakeover(String brokerID, UID storeSession, String brokerHost, UID brokerSession) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify) {
    }

    @Override
    public void sendClusterTransactionInfo(long tid, BrokerAddress address) {
    }

    @Override
    public BrokerAddress lookupBrokerAddress(String brokerid) {
        return null;
    }

    @Override
    public BrokerAddress lookupBrokerAddress(BrokerMQAddress mqaddr) {
        return null;
    }

    @Override
    public String lookupStoreSessionOwner(UID storeSession) {
        return null;
    }

    /**
     * Change master broker
     */
    @Override
    public void changeMasterBroker(BrokerMQAddress newmaster, BrokerMQAddress oldmaster) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public String sendTakeoverMEPrepare(String brokerID, byte[] token, Long syncTimeout, String uuid) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public String sendTakeoverME(String brokerID, String uuid) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public void transferFiles(String[] fileNames, String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID, String module,
            FileTransferCallback callback) throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    @Override
    public void syncChangeRecordOnStartup() throws BrokerException {
    }

    @Override
    public void notifyPartitionArrival(UID partitionId, String brokerID) throws BrokerException {
    }
}
