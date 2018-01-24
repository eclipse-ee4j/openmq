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

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;

/**
 */
public class ClusterNewMasterBrokerInfo 
{

    private Long xid = null;
    private String uuid = null;

    private BrokerAddress newmaster = null;
    private BrokerAddress oldmaster = null;
    Cluster c = null;

    private GPacket pkt = null; 

    private ClusterNewMasterBrokerInfo(BrokerAddress newmaster,
                                       BrokerAddress oldmaster, 
                                       String uuid, Long xid, Cluster c) {
        this.xid = xid;
        this.uuid = uuid;
        this.c = c;
        this.newmaster = newmaster;
        this.oldmaster = oldmaster;
    }

    private ClusterNewMasterBrokerInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterNewMasterBrokerInfo newInstance(BrokerAddress newmaster, 
                                              BrokerAddress oldmaster, 
                                              String uuid, Long xid, Cluster c) {
        return new ClusterNewMasterBrokerInfo(newmaster, oldmaster, uuid, xid, c); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterNewMasterBrokerInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterNewMasterBrokerInfo(pkt, c);
    }

    public GPacket getGPacket() throws Exception { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_NEW_MASTER_BROKER);
        gp.putProp("TS", Long.valueOf(System.currentTimeMillis()));
        gp.putProp("X", xid);
        gp.putProp("UUID", uuid);
        gp.putProp("oldMasterBroker", oldmaster.toProtocolString());
        c.marshalBrokerAddress(newmaster, gp);
        gp.setBit(gp.A_BIT, true);

        return gp;
    }

    public BrokerAddress getNewMasterBroker() throws Exception {
        assert( pkt != null);
        newmaster = c.unmarshalBrokerAddress(pkt);
        return newmaster;
    }

    public BrokerAddress getOldMasterBroker() throws Exception {
        assert( pkt != null);
        String oldm = (String)pkt.getProp("oldMasterBroker");
        oldmaster = Globals.getMyAddress().fromProtocolString(oldm);
        return oldmaster;
    }

    public String getUUID() {
        assert( pkt != null);
        return (String)pkt.getProp("UUID");
    }
    public Long getXid() {
        assert( pkt != null);
        return (Long)pkt.getProp("X");
    }
    public Long getTimestamp() {
        assert( pkt != null);
        return (Long)pkt.getProp("TS");
    }

    public GPacket getReplyGPacket(int status, String reason) {
        assert( pkt != null);
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY);
        gp.putProp("X", (Long)pkt.getProp("X"));
        gp.putProp("UUID", (String)pkt.getProp("UUID"));
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    public String toString() {
        if (pkt == null) {
            return "[newMasterBroker="+newmaster+
            ", oldMasterBroker="+oldmaster+", xid="+xid+", uuid="+uuid+"]";
        } 
        return (newmaster == null ? "":"[newMasterBroker="+newmaster)+
               (oldmaster == null ? "":"[oldMasterBroker="+oldmaster)+
               ", xid="+getXid()+", ts="+getTimestamp()+", uuid="+getUUID()+"]";
    }

    public static Long getReplyPacketXid(GPacket gp) {
        return (Long)gp.getProp("X");
    }
}
