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
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;

/**
 * This class implements the 'standalone' topology.
 */
public class ClusterImpl implements Cluster, ConfigListener {
    ClusterCallback cb = null;
    private BrokerAddressImpl self;

    /**
     * Creates and initializes a topology manager for the two broker topology using the broker configuration.
     */
    public ClusterImpl() {
        self = new BrokerAddressImpl();
    }

    @Override
    public void setCallback(ClusterCallback cb) {
        this.cb = cb;
    }

    @Override
    public void useGPackets(boolean useGPackets) {
    }

    @Override
    public void setMatchProps(Properties matchProps) {
    }

    @Override
    public void start() {
    }

    @Override
    public void shutdown(boolean force, BrokerAddress excludedBroker) {
    }

    @Override
    public void closeLink(BrokerAddress remote, boolean force) {
    }

    @Override
    public long getLinkInitWaitTime() {
        return 1L;
    }

    @Override
    public boolean isReachable(BrokerAddress remote, int timeout) throws IOException {
        return true;
    }

    @Override
    public void enablePingLogging(BrokerAddress remote) {
    }

    @Override
    public boolean isLinkModified(BrokerAddress remote, Object o) {
        return false;
    }

    @Override
    public BrokerAddress getSelfAddress() {
        return self;
    }

    @Override
    public BrokerAddress getConfigServer() throws BrokerException {
        return null;
    }

    @Override
    public void marshalBrokerAddress(BrokerAddress ddr, GPacket gp) {
    }

    @Override
    public BrokerAddress unmarshalBrokerAddress(GPacket gp) throws Exception {
        return null;
    }

    @Override
    public void stopMessageFlow() throws IOException {
    }

    @Override
    public void resumeMessageFlow() throws IOException {
    }

    @Override
    public void unicastAndClose(BrokerAddress addr, GPacket gp) throws IOException {
        if (cb != null) {
            cb.receiveUnicast(self, gp);
        }
    }

    @Override
    public void unicast(BrokerAddress addr, GPacket gp, boolean flowControl) throws IOException {
        if (cb != null) {
            cb.receiveUnicast(self, gp);
        }
    }

    @Override
    public void unicastUrgent(BrokerAddress addr, GPacket gp) throws IOException {
        if (cb != null) {
            cb.receiveUnicast(self, gp);
        }
    }

    @Override
    public void unicast(BrokerAddress addr, GPacket gp) throws IOException {
        unicast(addr, gp, false);
    }

    @Override
    public Map<BrokerAddress, Object> broadcast(GPacket gp) throws IOException {
        return broadcast();
    }

    @Override
    public Map<BrokerAddress, Object> broadcastUrgent(GPacket gp) throws IOException {
        return broadcast();
    }

    private Map<BrokerAddress, Object> broadcast() {
        Map<BrokerAddress, Object> m = new HashMap<>(1);
        m.put(self, null);
        return m;
    }

    @Override
    public void waitClusterInit() {
    }

    @Override
    public void unicast(BrokerAddress addr, int destId, byte[] pkt, boolean flowControl) throws IOException {
        if (cb != null) {
            cb.receiveUnicast(self, destId, pkt);
        }
    }

    @Override
    public void unicast(BrokerAddress addr, int destId, byte[] pkt) throws IOException {
        unicast(addr, destId, pkt, false);
    }

    @Override
    public void broadcast(int destId, byte[] pkt) {
    }

    public boolean election(int electionId, byte[] params) {
        return true;
    }

    @Override
    public void reloadCluster() {
    }

    @Override
    public Hashtable getDebugState() {
        return new Hashtable();
    }

    /**
     * Dynamic configuration property validation..
     */
    @Override
    public void validate(String name, String value) throws PropertyUpdateException {
    }

    /**
     * Dynamic configuration property updation..
     */
    @Override
    public boolean update(String name, String value) {
        return true;
    }

    @Override
    public void changeMasterBroker(BrokerAddress newmater, BrokerAddress oldmaster) throws BrokerException {
    }

    @Override
    public void receivedFileTransferRequest(BrokerAddress from, String uuid) {
    }

    @Override
    public void transferFiles(String[] fileNames, BrokerAddress targetBroker, Long syncTimeout, String uuid, String myBrokerID, String module,
            FileTransferCallback callback) throws BrokerException {
    }
}

