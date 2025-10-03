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

import java.util.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

/**
 * Interface for processing messages and acknowledgements coming from the MessageBus.
 */
public interface MessageBusCallback {
    /**
     * Initial sync with the config server is complete. We are now ready to accept connections from clients.
     */
    void configSyncComplete();

    /**
     * @param consumers contains mapping for each consumer UID to its delivery count or null if unknown
     */
    void processRemoteMessage(Packet msg, Map<ConsumerUID, Integer> consumers, BrokerAddress home, boolean sendMsgRedeliver) throws BrokerException;

    /**
     * Process an acknowledgement.
     */
    void processRemoteAck(SysMessageID sysid, ConsumerUID cuid, int ackType, Map optionalProps) throws BrokerException;

    void processRemoteAck2P(SysMessageID[] sysids, ConsumerUID[] cuids, int ackType, Map optionalProps, Long txnID, BrokerAddress txnHomeBroker)
            throws BrokerException;

    /**
     * Interest creation notification. This method is called when any remote interest is created.
     */
    void interestCreated(Consumer intr);

    /**
     * Interest removal notification. This method is called when any remote interest is removed.
     */
    void interestRemoved(Consumer cuid, Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs, boolean cleanup);

    /**
     * Durable subscription unsubscribe notification. This method is called when a remote broker unsubscribes a durable
     * interest.
     */
    void unsubscribe(Subscription sub);

    /**
     * Primary interest change notification. This method is called when a new interest is chosen as primary interest for a
     * failover queue.
     */
    void activeStateChanged(Consumer intr);

    /**
     * Client down notification. This method is called when a local or remote client connection is closed.
     */
    void clientDown(ConnectionUID conid);

    /**
     * Broker down notification. This method is called when any broker in this cluster goes down.
     */
    void brokerDown(BrokerAddress broker);

    /**
     * A new destination was created by the administrator on a remote broker. This broker should also add the destination if
     * it is not already present.
     */
    void notifyCreateDestination(Destination d);

    /**
     * A destination was removed by the administrator on a remote broker. This broker should also remove the destination, if
     * it is present.
     */
    void notifyDestroyDestination(DestinationUID uid);

    /**
     * A destination was updated
     */
    void notifyUpdateDestination(DestinationUID uid, Map changes);

    /**
     * Set last change record received from remote broker that this broker has processed
     */
    void setLastReceivedChangeRecord(BrokerAddress remote, ChangeRecordInfo rec);

    /**
     * Synchronize cluster change record on remote broker join
     */
    void syncChangeRecordOnJoin(BrokerAddress broker, ChangeRecordInfo cri) throws BrokerException;

    /**
     * Get last change record generated (persisted) by this broker
     */
    ChangeRecordInfo getLastStoredChangeRecord();

}

