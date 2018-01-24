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
 * @(#)NoCluster.java	1.38 07/23/07
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
 * Simple message bus implementation which can be used
 * in non-clustered environments.
 */
public class NoCluster implements ClusterBroadcast {

    private static final Object noOwner = new Object();

    private static BrokerAddress noAddress = 
         new BrokerAddress() {
            String address="localhost";

            public Object clone() {
               return this;
            }
            public boolean equals(Object o) {
                return o instanceof BrokerAddress;
            }
            public int hashCode() {
                return address.hashCode();
            }

            public String toProtocolString() {
                return null;
            }

            public BrokerAddress fromProtocolString(String s) throws Exception {
                throw new UnsupportedOperationException(
                          this.getClass().getName()+".fromProtocolString");
            }

            public void writeBrokerAddress(DataOutputStream os) 
                throws IOException
            {
            }
             
            public void readBrokerAddress(DataInputStream dis) 
                throws IOException
            {
            }

            public boolean getHAEnabled() { 
                return false; 
            }
            public String getBrokerID() {
                return null; 
            }
            public UID getBrokerSessionUID() { 
                return null; 
            }
            public UID getStoreSessionUID() { 
                return null; 
            }
            public void setStoreSessionUID(UID uid) { 
            }
            public String getInstanceName() { 
                return null; 
            }
         };

    public int getClusterVersion() {
        return VERSION_350;
    }

    public void messageDelivered(SysMessageID id, ConsumerUID uid,
                BrokerAddress ba)
    {
    }

    public void init(int connLimit,  int version)
    throws BrokerException {
    }

    public Object getProtocol() {
        return null;
    }

    /**
     * Set the matchProps for the cluster.
     */
    public void setMatchProps(Properties matchProps) {
    }

    /**
     *
     */
    public boolean waitForConfigSync() {
        return false;
    }



    public void startClusterIO() {
    }

    public void pauseMessageFlow() throws IOException {
    }
    public void resumeMessageFlow() throws IOException {
    }

    public void forwardMessage(PacketReference ref, Collection consumers)
    {
    }

    public void stopClusterIO(boolean requestTakeover, boolean force,
                              BrokerAddress excludedBroker) {
    }

    /**
     * Returns the address of this broker.
     * @return <code> BrokerAddress </code> object representing this
     * broker.
     */
    public BrokerAddress getMyAddress() {
        return noAddress;
    }

    private static Map map = Collections.synchronizedMap(
             new HashMap());

    public boolean lockSharedResource(String resource, Object owner) {
        return true;
    }

    public boolean lockExclusiveResource(String resource, Object owner) {
        return true;
    }

    public void unlockExclusiveResource(String resource, Object owner) {
    }

    public boolean lockDestination(DestinationUID uid, Object owner)
    {
        // unnecessary in single broker implementation
        return true;
    }

    public void unlockDestination(DestinationUID uid, Object owner) {
        // unnecessary in single broker 
    }

    public synchronized int lockClientID(String clientid, Object owner, boolean shared)
    {
        if (shared) {
            throw new RuntimeException("shared clientID's not supported w/o cluster");
        }
        String lockid = "clientid:" + clientid;
        return lockResource(lockid, System.currentTimeMillis(), owner);
    }
    public synchronized void unlockClientID(String clientid, Object owner) {
        String lockid = "clientid:" + clientid;
        unlockResource(lockid);
    }

    public boolean getConsumerLock(ConsumerUID uid,
                    DestinationUID duid, int position,
                    int maxActive, Object owner)
            throws BrokerException
    {

        return true;
    }


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

    public void acknowledgeMessage(BrokerAddress address, SysMessageID sysid, 
                                   ConsumerUID cuid, int ackType, Map optionalProps,
                                   boolean ackack) throws BrokerException
    {
    }

    public void acknowledgeMessage2P(BrokerAddress address, SysMessageID[] sysids, 
                                   ConsumerUID[] cuids, int type,
                                   Map optProp, Long txnID, UID txnStoreSession, 
                                   boolean ackack, boolean async) 
                                   throws BrokerException
    {
       throw new BrokerException("Broker Internal Error: unexpected call acknowledgeMessage");
    }


    public void recordUpdateDestination(Destination d)
        throws BrokerException {
    }

    public void recordRemoveDestination(Destination d)
        throws BrokerException {
    }

    public void createDestination(Destination dest) 
            throws BrokerException
    {
    }

    public void recordCreateSubscription(Subscription sub)
        throws BrokerException {
    }

    public void recordUnsubscribe(Subscription sub)
        throws BrokerException {
    }

    public void createSubscription(Subscription sub, Consumer cons)
            throws BrokerException
    {
    }

    public void createConsumer(Consumer con)
            throws BrokerException {
    }

    public void updateDestination(Destination dest)
            throws BrokerException {
    }

    public void updateSubscription(Subscription sub)
            throws BrokerException {
    }

    public void updateConsumer(Consumer con)
            throws BrokerException {
    }


    public void destroyDestination(Destination dest)
            throws BrokerException {
    }

    public void destroyConsumer(Consumer con, Map pendingMsgs, boolean cleanup)
            throws BrokerException {
    }

    public void connectionClosed(ConnectionUID uid, boolean admin) {
        freeAllLocks(uid);
    }

    public void reloadCluster() {
    }

    public Hashtable getAllDebugState() {
        return new Hashtable();
    }

    public boolean lockUIDPrefix(short p){
        return true;
    }

    public void preTakeover(String brokerID, UID storeSession,
                String brokerHost, UID brokerSession) throws BrokerException { 
        throw new BrokerException("Not Supported");
    }

    public void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify) {};

    public void sendClusterTransactionInfo(long tid, BrokerAddress address) {};

    public BrokerAddress lookupBrokerAddress(String brokerid) {
        return null;
    };

    public BrokerAddress lookupBrokerAddress(BrokerMQAddress mqaddr) {
        return null;
    };

    public String lookupStoreSessionOwner(UID storeSession) {
        return null;
    }

    /**
     * Change master broker
     */
    public void changeMasterBroker(BrokerMQAddress newmaster, BrokerMQAddress oldmaster)
    throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    public String sendTakeoverMEPrepare(String brokerID, byte[] token,
                                        Long syncTimeout, String uuid)
                                        throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    public String sendTakeoverME(String brokerID, String uuid)
    throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    public void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout,
                                        String uuid, String myBrokerID)
                                        throws BrokerException {
        throw new BrokerException("Not Supported");
    }

    public void transferFiles(String[] fileNames, String targetBrokerID,
                              Long syncTimeout, String uuid, String myBrokerID,
                              String module, FileTransferCallback callback)
                              throws BrokerException { 
        throw new BrokerException("Not Supported");
    }

    public void syncChangeRecordOnStartup() throws BrokerException {
    }

    public void notifyPartitionArrival(UID partitionId, String brokerID)
    throws BrokerException {
    }
}

