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
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterProtocolHelper;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 */

public class ClusterReplicationGroupInfo implements ClusterProtocolHelper
{
    protected Logger logger = Globals.getLogger();

    private String groupName = null;
    private String nodeName = null;
    private String masterHostPort = null;
    private Cluster c = null;

    private GPacket pkt = null;

    private ClusterReplicationGroupInfo(String groupName, String nodeName,
                                        String masterHostPort, Cluster c) {
        this.groupName = groupName;
        this.nodeName = nodeName;
        this.masterHostPort = masterHostPort;
        this.c = c;
    }

    private ClusterReplicationGroupInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterReplicationGroupInfo newInstance(
                      String groupName, String nodeName, 
                      String masterHostPort, Cluster c) {
        return new ClusterReplicationGroupInfo(groupName, nodeName, masterHostPort, c);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterReplicationGroupInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterReplicationGroupInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_REPLICATION_GROUP_INFO);
        gp.putProp("groupName", groupName);
        gp.putProp("nodeName", nodeName);
        gp.putProp("masterHostPort", masterHostPort);
        gp.putProp("clusterid", Globals.getClusterID());
        gp.putProp("TS", Long.valueOf(System.currentTimeMillis()));
        c.marshalBrokerAddress(c.getSelfAddress(), gp); 
        gp.setBit(gp.A_BIT, false);

        return gp;
    }

    public String getGroupName() {
        assert ( pkt != null );
        return (String)pkt.getProp("groupName");
    }

    public String getNodeName() {
        assert ( pkt != null );
        return (String)pkt.getProp("nodeName");
    }

    public String getMasterHostPort() {
        assert ( pkt != null );
        return (String)pkt.getProp("masterHostPort");
    }

    public String getClusterId() {
        assert ( pkt != null );
        return (String)pkt.getProp("clusterid");
    }

    public Long getTimestamp() {
        assert ( pkt != null );
        return (Long)pkt.getProp("TS");
    }

    public BrokerAddress getOwnerAddress() throws Exception {
        assert ( pkt != null );
        return c.unmarshalBrokerAddress(pkt);
    }

    public void sendReply(BrokerAddress recipient, int status,
                          String reason, Object extraInfo) {
        return;
    }

    /**
     * To be called by sender
     */
    public String toString() {

        if (pkt == null) {
            return "["+groupName+"["+nodeName+", "+masterHostPort+"]";
        } 
        return "["+getGroupName()+"["+getNodeName()+", "+getMasterHostPort()+"]"+getTimestamp()+"]";
    }
}
