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

public class ClusterTakeoverMEInfo implements ClusterProtocolHelper
{
    protected Logger logger = Globals.getLogger();

    private String groupName = null;
    private String nodeName = null;
    private String masterHostPort = null;
    private String targetNodeName = null;
    private String uuid = null;
    private Long xid = null;
    private Cluster c = null;

    private GPacket pkt = null;
    private RaptorProtocol parent = null;

    private ClusterTakeoverMEInfo(String groupName, String nodeName,
                                         String masterHostPort,
                                         String targetNodeName,
                                         String uuid, Long xid,
                                         Cluster c) {
        this.groupName = groupName;
        this.nodeName = nodeName;
        this.masterHostPort = masterHostPort;
        this.targetNodeName = targetNodeName;
        this.uuid = uuid;
        this.xid = xid;
        this.c = c;
    }

    private ClusterTakeoverMEInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    //only used by receiver
    public void setParent(RaptorProtocol parent) {
        this.parent = parent;
    }

    public static ClusterTakeoverMEInfo newInstance(
                      String groupName, String nodeName, 
                      String masterHostPort, String targetNodeName,
                      String uuid, Long xid, Cluster c) {
        return new ClusterTakeoverMEInfo(groupName, nodeName,
                       masterHostPort, targetNodeName, uuid, xid, c);
    }

    public static ClusterTakeoverMEInfo newInstance(
                      String myBrokerID, 
                      String targetBrokerID,
                      String uuid, Long xid, Cluster c) {
        return new ClusterTakeoverMEInfo(null, myBrokerID,
                       null, targetBrokerID, uuid, xid, c);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTakeoverMEInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterTakeoverMEInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TAKEOVER_ME);
        if (groupName != null) {
            gp.putProp("groupName", groupName);
        }
        gp.putProp("nodeName", nodeName);
        if (masterHostPort != null) {
            gp.putProp("masterHostPort", masterHostPort);
        }
        gp.putProp("clusterid", Globals.getClusterID());
        gp.putProp("targetNodeName", targetNodeName);
        gp.putProp("X", xid);
        gp.putProp("uuid", uuid);
        gp.putProp("TS", Long.valueOf(System.currentTimeMillis()));
        c.marshalBrokerAddress(c.getSelfAddress(), gp); 
        gp.setBit(gp.A_BIT, true);

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

    public String getClusterID() {
        assert ( pkt != null );
        return (String)pkt.getProp("clusterid");
    }

    public String getTargetNodeName() {
        assert ( pkt != null );
        return (String)pkt.getProp("targetNodeName");
    }

    public String getUUID() {
        assert ( pkt != null );
        return (String)pkt.getProp("uuid");
    }

    public Long getXid() {
        assert ( pkt != null );
        return (Long)pkt.getProp("X");
    }

    public BrokerAddress getOwnerAddress() throws Exception {
        assert ( pkt != null );
        return c.unmarshalBrokerAddress(pkt);
    }

    public Long getTimestamp() {
        assert( pkt != null);
        return (Long)pkt.getProp("TS");
    }

    public boolean needReply() {
        assert ( pkt != null );
        return pkt.getBit(pkt.A_BIT);
    }

    public GPacket getReplyGPacket(int status, String reason) {
        assert( pkt != null);
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TAKEOVER_ME_REPLY);
        gp.putProp("X", (Long)pkt.getProp("X"));
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    public static GPacket getReplyAckGPacket(GPacket reply) {
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK);
        gp.putProp("X", (Long)reply.getProp("X"));
        gp.putProp("S", Integer.valueOf(Status.OK));
        return gp;
    }

    public void sendReply(BrokerAddress to, int status,
                          String reason, Object extraInfo) {
        if (!needReply()) {
            return;
        }
        parent.sendTakeoverMEReply(this, status, reason, to);
    }

    /**
     */
    public String toString() {

        if (pkt == null) {
            return "["+groupName+"["+nodeName+", "+masterHostPort+"]target="+
                   targetNodeName+", xid="+xid+", uuid="+uuid+"]";
        }
        return "["+getGroupName()+"["+getNodeName()+", "+getMasterHostPort()+"]target="+
               getTargetNodeName()+", xid="+getXid()+
               ", uuid="+getUUID()+", time="+getTimestamp()+"]";
    }

    protected String getReplyToString(GPacket reply) {
        return toString()+":[status="+reply.getProp("S")+", "+reply.getProp("reason")+"]";
    }

    protected static String getReplyAckToString(GPacket replyack) {
        return "[xid="+replyack.getProp("X")+"]";
    }

    public static Long getReplyPacketXid(GPacket gp) {
        return (Long)gp.getProp("X");
    }

}
