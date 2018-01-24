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
 * @(#)ClusterCallback.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * This interface defines a mechanism for receiving packets
 * from the broker cluster. Only the <code> MessageBus </code> class
 * implements this interface.
 */
public interface ClusterCallback {

    public int getHighestSupportedVersion();

    /**
     * Receive a unicast packet.
     * @param sender Address of the broker who sent this message.
     * @param pkt Packet.
     */
    public void receiveUnicast(BrokerAddress sender, GPacket pkt);

    /**
     * Receive a broadcast packet.
     * @param sender Address of the broker who sent this message.
     * @param pkt Packet.
     */
    public void receiveBroadcast(BrokerAddress sender, GPacket pkt);

    /**
     * Receive a unicast packet.
     * @param sender Address of the broker who sent this message.
     * @param destId Tells the this broker how this message
     * shoule be handled
     * @param pkt Packet data.
     */
    public void receiveUnicast(BrokerAddress sender, int destId, byte []pkt);

    /**
     * Receive a broadcast packet.
     * @param sender Address of the broker who sent this message.
     * @param destId Tells the this broker how this message
     * shoule be handled
     * @param pkt Packet data.
     */
    public void receiveBroadcast(BrokerAddress sender, int destId, byte []pkt);

    /**
     * Construct a BrokerInfo object that describes this broker.
     * This object is exchanged during initial handshake between
     * brokers.
     * @return BrokerInfo object describing the current state of the broker.
     */
    public BrokerInfo getBrokerInfo();

    /**
     */
    public ClusterBrokerInfoReply getBrokerInfoReply(BrokerInfo remote) throws Exception;

    public static final int ADD_BROKER_INFO_OK = 0;
    public static final int ADD_BROKER_INFO_RETRY = 1;
    public static final int ADD_BROKER_INFO_BAN = 2;

    /**
     * Add a new broker to the list of known brokers in this cluster.
     * This serves as a notification that a new broker has joined
     * the cluster so all the ongoing (unresolved) elections for
     * locking various resources must be repeated.
     *
     * @return false if the new broker is rejected due to some
     * state mismatch, otherwise true. If the return value is false,
     * the topology driver should forget all about the new broker
     * and let it retry the connection..
     */
    public int addBrokerInfo(BrokerInfo brokerInfo);

    /**
     * Remove a broker since it is no longer attached to this cluster.
     * This serves as a notification that a broker has left the cluster,
     * so all the interests local to that broker are no longer valid.
     *
     * @param broken link broken with IOException after handshake 
     *
     */
    public void removeBrokerInfo(BrokerAddress broker, boolean broken);

    /**
     * Synchronize cluster change record on remote broker join 
     */
    public void syncChangeRecordOnJoin(BrokerAddress broker,  ChangeRecordInfo cri)
    throws BrokerException;

    /**
     */
    public ChangeRecordInfo getLastStoredChangeRecord();
}

/*
 * EOF
 */
