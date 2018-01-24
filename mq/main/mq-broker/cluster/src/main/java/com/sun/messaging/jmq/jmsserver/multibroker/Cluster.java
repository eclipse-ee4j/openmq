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
 * @(#)Cluster.java	1.19 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.FileTransferCallback;

/**
 * This interface defines the basic topology neutral broker-to-broker
 * packet exchange mechanism. Each topology implementation exposes
 * just this interface and hides all the routing/topology management
 * details.
 */
public interface Cluster {
    /**
     * Setup the callback for received events. This method is called
     * only by the <code> MessageBus </code>.
     */
    public void setCallback(ClusterCallback cb);

    /**
     * Set the cluster 'matchProps'. When brokers connect with
     * each other, these properties are exchanged and compared during
     * the initial handshake. If the values do not match, the brokers
     * will not be able to communicate with each other.
     */
    public void setMatchProps(Properties matchProps);

    /**
     * Get the local BrokerAddress. This method returns the topology
     * specific <code> BrokerAddress </code> object representing this
     * broker.
     */
    public BrokerAddress getSelfAddress();

    /**
     * Get the address of the broker designated as the configuration
     * server.
     */
    public BrokerAddress getConfigServer() throws BrokerException;

    /**
     */
    public void marshalBrokerAddress(BrokerAddress ddr, GPacket gp);

    /**
     * return null if protocol version < 400 
     */
    public BrokerAddress unmarshalBrokerAddress(GPacket gp) throws Exception;

    /**
     * Begin the cluster I/O operations. This method is called after
     * the initialization phase is complete.
     */
    public void start() throws IOException;

    /**
     * Shutdown the cluster topology driver.
     * @param excludedBroker if not null, do not shutdown
     */
    public void shutdown(boolean force, 
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress excludedBroker);

    /**
     * Shutdown link to a remote broker instance
     *
     * @param force 
     *
     */
    public void closeLink(BrokerAddress remote, boolean force);

    /**
     * @return millisecs
     */
    public long getLinkInitWaitTime(); 

    /**
     *
     * @param timeout Timeout in second
     */
    public boolean isReachable(BrokerAddress remote, int timeout) throws IOException;

    /**
     * @param remote remote broker address
     * @param o opaque object from the link to the remote broker
     */
    public boolean isLinkModified(BrokerAddress remote, Object o);

    public void enablePingLogging(BrokerAddress remote);

    /**
     * Switch to the raptot GPacket format.
     */
    public void useGPackets(boolean useGPackets);

    /**
     * Tell all the brokers in the cluster to stop sending messages.
     */
    public void stopMessageFlow() throws IOException;

    /**
     * Tell all the brokers in the cluster to resume sending messages.
     */
    public void resumeMessageFlow() throws IOException;

    /**
     * Send a packet to the specified broker.
     * @param addr Destination broker address
     * @param gp Packet.
     * @param flowControl Is this packet subject to flow control check.
     */
    public void unicast(BrokerAddress addr, GPacket gp, boolean flowControl)
        throws IOException;

    public void unicastUrgent(BrokerAddress addr, GPacket gp)
        throws IOException;

    /**
     * Send a packet to the specified broker.
     * @param addr Destination broker address
     * @param gp Packet.
     */
    public void unicast(BrokerAddress addr, GPacket gp) throws IOException;

    /**
     * Send a packet to the specified broker and close output to the link  
     * @param addr Destination broker address
     * @param gp Packet.
     */
    public void unicastAndClose(BrokerAddress addr, GPacket gp) throws IOException;

    /**
     * Broadcast a packet to all the known brokers in the cluster.
     * This method is used mainly by the interest manager to broadcast
     * interest updates.
     * @param gp Packet.
     */
    public Map<BrokerAddress, Object> broadcast(GPacket gp) throws IOException;

    public Map<BrokerAddress, Object> broadcastUrgent(GPacket gp) throws IOException;

    /**
     * Wait for broker links init 
     */
    public void waitClusterInit();

    /**
     * Send a packet to the specified broker.
     * @param addr Destination broker address
     * @param destId Tells the destination broker how this message
     * shoule be handled
     * @param pkt Packet data.
     * @param flowControl Is this packet subject to flow control check.
     */
    public void unicast(BrokerAddress addr, int destId, byte[] pkt,
        boolean flowControl) throws IOException;

    /**
     * Send a packet to the specified broker.
     * @param addr Destination broker address
     * @param destId Tells the destination broker how this message
     * shoule be handled
     * @param pkt Packet data.
     */
    public void unicast(BrokerAddress addr, int destId, byte[] pkt)
        throws IOException;

    /**
     * Broadcast a packet to all the known brokers in the cluster.
     * This method is used mainly by the interest manager to broadcast
     * interest updates.
     * @param destId Tells the destination broker how this message
     * shoule be handled
     * @param pkt Packet data.
     */
    public void broadcast(int destId, byte[] pkt)
        throws IOException;

    /**
     * Refresh the configuration properties and rewire the cluster.
     * Typically the administrators will change the cluster configuration
     * (e.g. add more brokers) and issue a "cluster reload" command.
     */
    public void reloadCluster();

    /**
     * Change master broker 
     */
    public void changeMasterBroker(BrokerAddress newmaster, BrokerAddress oldmaster)
    throws BrokerException; 

    /**
     */
    public void transferFiles(String[] fileNames, BrokerAddress targetBroker,
                              Long syncTimeout, String uuid, String myBrokerID,
                              String module, FileTransferCallback callback)
                              throws BrokerException;

    /**
     */
    public void receivedFileTransferRequest(BrokerAddress from, String uuid);

	public Hashtable getDebugState();
}

/*
 * EOF
 */
