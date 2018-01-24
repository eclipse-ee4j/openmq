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
 * @(#)ClusterImpl.java	1.18 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.standalone;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.cluster.api.FileTransferCallback;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;


/**
 * This class implements the 'standalone' topology.
 */
public class ClusterImpl implements Cluster, ConfigListener {
    ClusterCallback cb = null;
    private BrokerAddressImpl self;

    /**
     * Creates and initializes a topology manager for the two broker
     * topology using the broker configuration.
     */
    public ClusterImpl() {
        self = new BrokerAddressImpl();
    }

    public void setCallback(ClusterCallback cb) {
        this.cb = cb;
    }

    public void useGPackets(boolean useGPackets) {
    }

    public void setMatchProps(Properties matchProps) {
    }

    public void start() {
    }

    public void shutdown(boolean force, BrokerAddress excludedBroker) {
    }

    public void closeLink(BrokerAddress remote, boolean force) {
    }

    public long getLinkInitWaitTime() {
        return 1L;
    }

    public boolean isReachable(BrokerAddress remote, int timeout) throws IOException {
        return true;
    }

    public void enablePingLogging(BrokerAddress remote) {
    }

    public boolean isLinkModified(BrokerAddress remote, Object o) {
        return false;
    }

    public BrokerAddress getSelfAddress() {
        return (BrokerAddress) self;
    }

    public BrokerAddress getConfigServer() throws BrokerException {
        return null;
    }

    public void marshalBrokerAddress(BrokerAddress ddr, GPacket gp) {
    }

    public BrokerAddress unmarshalBrokerAddress(GPacket gp) throws Exception {
        return null;
    }
 

    public void stopMessageFlow() throws IOException {
    }

    public void resumeMessageFlow() throws IOException {
    }

    public void unicastAndClose(BrokerAddress addr, GPacket gp) throws IOException {
        if (cb != null) cb.receiveUnicast(self, gp);
    }

    public void unicast(BrokerAddress addr, GPacket gp, boolean flowControl)
        throws IOException {
        if (cb != null) cb.receiveUnicast(self, gp);
    }

    public void unicastUrgent(BrokerAddress addr, GPacket gp)
        throws IOException {
        if (cb != null) cb.receiveUnicast(self, gp);
    }

    public void unicast(BrokerAddress addr, GPacket gp) throws IOException {
        unicast(addr, gp, false);
    }

    public Map<BrokerAddress, Object> broadcast(GPacket gp) throws IOException {
        return broadcast(gp, false);
    }

    public Map<BrokerAddress, Object> broadcastUrgent(GPacket gp) throws IOException {
        return broadcast(gp, true);
    }

    private Map<BrokerAddress, Object> broadcast(GPacket gp, boolean urgent) 
    throws IOException {
        Map<BrokerAddress, Object> m = new HashMap<BrokerAddress, Object>(1);
        m.put(self, null);
        return m;
    }

    public void waitClusterInit() {
    }

    public void unicast(BrokerAddress addr, int destId, byte[] pkt,
        boolean flowControl) throws IOException {
        if (cb != null)
            cb.receiveUnicast(self, destId, pkt);
    }

    public void unicast(BrokerAddress addr, int destId, byte[] pkt)
        throws IOException {
        unicast(addr, destId, pkt, false);
    }

    public void broadcast(int destId, byte[] pkt) {
    }

    public boolean election(int electionId, byte[] params) {
        return true;
    }

    public void reloadCluster() {
    }

    public Hashtable getDebugState() {
        return new Hashtable();
	}

    /**
     * Dynamic configuration property validation..
     */
    public void validate(String name, String value)
        throws PropertyUpdateException {
    }

    /**
     * Dynamic configuration property updation..
     */
    public boolean update(String name, String value) {
        return true;
    }
    public void changeMasterBroker(BrokerAddress newmater, BrokerAddress oldmaster)
    throws BrokerException { 
    }

    public void receivedFileTransferRequest(BrokerAddress from, String uuid) {
    }

    public void transferFiles(String[] fileNames, BrokerAddress targetBroker,
                              Long syncTimeout, String uuid, String myBrokerID,
                              String module, FileTransferCallback callback)
                              throws BrokerException { 
    }
}

/*
 * EOF
 */
