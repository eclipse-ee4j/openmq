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
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 */

public class ClusterNotifyPartitionArrivalInfo 
{
    protected Logger logger = Globals.getLogger();

    private UID partitionID = null;
    private String targetBrokerID = null;
    private Long xid = null;
    private Cluster c = null;
    private GPacket pkt = null;

    private ClusterNotifyPartitionArrivalInfo(UID partitionID, String targetBrokerID,
                                              Long xid, Cluster c) {
        this.partitionID = partitionID;
        this.targetBrokerID = targetBrokerID;
        this.xid = xid;
        this.c = c;
    }

    private ClusterNotifyPartitionArrivalInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterNotifyPartitionArrivalInfo newInstance(
                      UID partitionID, String targetBrokerID,
                      Long xid, Cluster c) {
        return new ClusterNotifyPartitionArrivalInfo(partitionID, targetBrokerID, xid, c);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterNotifyPartitionArrivalInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterNotifyPartitionArrivalInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL);
        gp.putProp("targetBrokerID", targetBrokerID);
        gp.putProp("partitionID", Long.valueOf(partitionID.longValue()));
        gp.putProp("X", xid);
        gp.putProp("TS", Long.valueOf(System.currentTimeMillis()));
        c.marshalBrokerAddress(c.getSelfAddress(), gp); 
        gp.setBit(gp.A_BIT, true);

        return gp;
    }

    public UID getPartitionID() {
        assert ( pkt != null );
        return new UID(((Long)pkt.getProp("partitionID")).longValue());
    }

    public String getTargetBrokerID() {
        assert ( pkt != null );
        return (String)pkt.getProp("targetBrokerID");
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
        gp.setType(ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY);
        gp.putProp("X", (Long)pkt.getProp("X"));
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    /**
     */
    public String toString() {

        if (pkt == null) {
            return "["+partitionID+", "+targetBrokerID+"]";
        }
        return "["+getPartitionID()+", "+getTargetBrokerID()+"]";
    }

    protected String getReplyToString(GPacket reply) {
        return toString()+":[status="+reply.getProp("S")+", "+reply.getProp("reason")+"]";
    }

    public static Long getReplyPacketXid(GPacket gp) {
        return (Long)gp.getProp("X");
    }
}
