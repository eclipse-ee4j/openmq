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

import java.util.Map;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;
import java.util.Hashtable;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.io.SysMessageID;
import org.jvnet.hk2.annotations.Contract;
import jakarta.inject.Singleton;

/**
 * Hides access to the clustering interface.
 */
@Contract
@Singleton
public interface ClusterBroadcast {

    /** MessageBus protocol version */
    int VERSION_500 = 500;
    int VERSION_460 = 460;
    int VERSION_410 = 410;
    int VERSION_400 = 400;
    int VERSION_350 = 350;
    int VERSION_300 = 300;
    int VERSION_210 = 210;

    int MSG_DELIVERED = 0;
    int MSG_ACKNOWLEDGED = 1;
    int MSG_TXN_ACKNOWLEDGED_RN = 2;
    int MSG_PREPARE_RN = 3;
    int MSG_ROLLEDBACK_RN = 4;
    int MSG_IGNORED = 5;
    int MSG_UNDELIVERABLE = 6;
    int MSG_DEAD = 7;
    int MSG_PREPARE = 8;
    int MSG_ROLLEDBACK = 9;

    String RB_RELEASE_MSG_INACTIVE = "RB_RELEASE_MSG_INACTIVE";
    String RB_RELEASE_MSG_ACTIVE = "RB_RELEASE_MSG_ACTIVE";
    String RC_RELEASE_MSG_INACTIVE = "RC_RELEASE_MSG_INACTIVE";
    String RB_RELEASE_MSG_ORPHAN = "RB_RELEASE_MSG_ORPHAN";
    String MSG_NOT_SENT_TO_REMOTE = "MSG_NOT_SENT_TO_REMOTE";
    String MSG_DELIVERED_ACK = "MSG_DELIVERED_ACK";

    // obsolete
    String MSG_REMOTE_REJECTED = "MSG_REMOTE_REJECTED"; // 4.5
    // obsolete
    String MSG_OUT_TIME_MILLIS = "MSG_OUT_TIME_MILLIS"; // 4.5

    String CLIENTID_EXCLUSIVE_LOCK_PREFIX = "clientid:";
    String Q_CONSUMER_EXCLUSIVE_LOCK_PREFIX = "queue:";
    String DESTINATION_EXCLUSIVE_LOCK_PREFIX = "destCreate:";
    String TAKEOVER_EXCLUSIVE_LOCK_PREFIX = "takeover:";

    /**
     * cluster lock request return status
     */
    int LOCK_TIMEOUT = -1;
    int LOCK_SUCCESS = 0;
    int LOCK_FAILURE = 1;

    /**
     * Cluster protocol pkt # for RESET_PERSISTENCE
     */
    int TYPE_RESET_PERSISTENCE = 31;

    void init(int version) throws BrokerException;

    Object getProtocol();

    boolean waitForConfigSync();

    void setMatchProps(Properties match);

    /**
     * Get runtime cluster version
     */
    int getClusterVersion() throws BrokerException;

    void startClusterIO();

    /**
     * @param excludedBroker if not null, do not shutdown cluster service
     */
    void stopClusterIO(boolean requestTakeover, boolean force, BrokerAddress excludedBroker);

    void pauseMessageFlow() throws IOException;

    void resumeMessageFlow() throws IOException;

    void messageDelivered(SysMessageID id, ConsumerUID uid, BrokerAddress ba);

    void forwardMessage(PacketReference ref, Collection consumers);

    /**
     * Returns the address of this broker.
     *
     * @return <code> BrokerAddress </code> object representing this broker.
     */
    BrokerAddress getMyAddress();

    boolean lockSharedResource(String resource, Object owner);

    boolean lockExclusiveResource(String resource, Object owner);

    void unlockExclusiveResource(String resource, Object owner);

    boolean lockDestination(DestinationUID uid, Object owner);

    void unlockDestination(DestinationUID uid, Object owner);

    /**
     * @return LOCK_SUCCESS, LOCK_FAILURE, LOCK_TIMEOUT
     */
    int lockClientID(String clientid, Object owner, boolean shared);

    void unlockClientID(String clientid, Object owner);

    boolean getConsumerLock(ConsumerUID uid, DestinationUID duid, int position, int maxActive, Object owner) throws BrokerException;

    void unlockConsumer(ConsumerUID uid, DestinationUID duid, int position);

    void acknowledgeMessage(BrokerAddress address, SysMessageID sysid, ConsumerUID cuid, int ackType, Map optionalProps, boolean ackack)
            throws BrokerException;

    void acknowledgeMessage2P(BrokerAddress address, SysMessageID[] sysids, ConsumerUID[] cuids, int ackType, Map optionalProps, Long txnID,
            UID txnStoreSession, boolean ackack, boolean async) throws BrokerException;

    void recordUpdateDestination(Destination d) throws BrokerException;

    void recordRemoveDestination(Destination d) throws BrokerException;

    void createDestination(Destination dest) throws BrokerException;

    void recordCreateSubscription(Subscription sub) throws BrokerException;

    void recordUnsubscribe(Subscription sub) throws BrokerException;

    void createSubscription(Subscription sub, Consumer cons) throws BrokerException;

    void createConsumer(Consumer con) throws BrokerException;

    void updateDestination(Destination dest) throws BrokerException;

    void updateSubscription(Subscription sub) throws BrokerException;

    void updateConsumer(Consumer con) throws BrokerException;

    void destroyDestination(Destination dest) throws BrokerException;

    void destroyConsumer(Consumer con, Map pendingMsgs, boolean cleanup) throws BrokerException;

    void connectionClosed(ConnectionUID uid, boolean admin);

    void reloadCluster();

    Hashtable getAllDebugState();

    /**
     * Ensures that the given "prefix" number is unique in the cluster. This method is used to ensure the uniqueness of the
     * UIDs generated by a broker.
     *
     * @return true if the number is unique. false if some other broker is using this number as a UID prefix.
     */
    boolean lockUIDPrefix(short p);

    void preTakeover(String brokerID, UID storeSession, String brokerHost, UID brokerSession) throws BrokerException;

    /**
     */
    void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify);

    void sendClusterTransactionInfo(long tid, BrokerAddress to);

    /**
     * Lookup the broker address for a broker ID - only for HA mode and BDBREP mode
     */
    BrokerAddress lookupBrokerAddress(String brokerid);

    /**
     * Lookup the BrokerAddress for a BrokerMQAddress
     */
    BrokerAddress lookupBrokerAddress(BrokerMQAddress mqaddr);

    /**
     * @return null if not found or unable to
     */
    String lookupStoreSessionOwner(UID storeSession);

    /**
     * This method can only be called if this broker is the current master broker else it throws BrokerException.
     *
     * If this method throws BrokerException with status codes Status.BAD_REQUEST, NOT_ALLOWED, UNAVAILABLE,
     * PRECONDITION_FAILED, then the failure didn't affect current master broker configuration;
     *
     * If this method throws BrokerException with any error status code other than the ones listed above,
     *
     * 1. It is possible the old master broker has switched to the new master but the new master broker has not switched
     * itself, for example, IO failure while storing new master configuration, network problem between the new and old
     * master broker at the last stage of the protocol; 2. It is ensured that the new master broker has up to date change
     * records of the cluster; 3. After the failure, while the old or new master broker continues running, it is ensured no
     * cluster change record operation will succeed on the old master. 4. The proper action to take is to shutdown the
     * cluster or all brokers in the cluster and restart with master broker configuration using the new master broker
     *
     * When this method returns successfully, it is possible a non-master broker that the change of master broker
     * notification had sent to has not switched to the new master broker due to wait timeout.
     *
     * Once this method returns successfully, it is ensured that no cluster change record operation will succeed on the old
     * master broker.
     */
    void changeMasterBroker(BrokerMQAddress newmaster, BrokerMQAddress oldmaster) throws BrokerException;

    String sendTakeoverMEPrepare(String brokerID, byte[] token, Long syncTimeout, String uuid) throws BrokerException;

    String sendTakeoverME(String brokerID, String uuid) throws BrokerException;

    void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID) throws BrokerException;

    void transferFiles(String[] fileNames, String targetBrokerID, Long syncTimeout, String uuid, String myBrokerID, String module,
            FileTransferCallback callback) throws BrokerException;

    void syncChangeRecordOnStartup() throws BrokerException;

    void notifyPartitionArrival(UID partitionID, String brokerID) throws BrokerException;
}
