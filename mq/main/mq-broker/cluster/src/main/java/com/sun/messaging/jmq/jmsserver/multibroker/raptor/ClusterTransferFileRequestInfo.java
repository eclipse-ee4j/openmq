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
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 */

public class ClusterTransferFileRequestInfo 
{
    private static boolean DEBUG = false;

    private GPacket pkt = null;

    private String uuid = null;
    private String brokerID = null;
	private Long xid = null;

    private ClusterTransferFileRequestInfo(String brokerID, String uuid, Long xid) {
        this.brokerID = brokerID;
        this.uuid = uuid;
        this.xid = xid;
    }

    private ClusterTransferFileRequestInfo(GPacket pkt) {
        assert ( pkt.getType() == ProtocolGlobals.G_TRANSFER_FILE_REQUEST );
        this.pkt = pkt;
    }

    /**
     */
    public static ClusterTransferFileRequestInfo newInstance(String brokerID, String uuid, Long xid) {
        return new ClusterTransferFileRequestInfo(brokerID, uuid, xid);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTransferFileRequestInfo newInstance(GPacket pkt) {
        return new ClusterTransferFileRequestInfo(pkt);
    }

    public GPacket getGPacket() throws BrokerException { 
        if (pkt != null) {
           return pkt;
        }

        GPacket gp = GPacket.getInstance();
        gp.putProp("uuid", uuid);
        gp.putProp("brokerID", brokerID);
        gp.putProp("X", xid);
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_REQUEST);
        gp.setBit(gp.A_BIT, true);
        return gp;
    }

    public String getUUID() {
        assert ( pkt != null );
        return (String)pkt.getProp("uuid");
    }

    public String getBrokerID() {
        assert ( pkt != null );
        return (String)pkt.getProp("brokerID");
    }

    public Long getXid() {
        assert ( pkt != null );
        return (Long)pkt.getProp("X");
    }


    public GPacket getReplyGPacket(int status, String reason) {
        assert( pkt != null);
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY);
        gp.putProp("X", (Long)pkt.getProp("X"));
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    public static Long getReplyPacketXid(GPacket reply) {
        return (Long)reply.getProp("X");
    }

    public String toString() {
        if (pkt != null) {
            return "[brokerID="+getBrokerID()+", uuid="+getUUID()+"]";
        }
        return "[brokerID="+brokerID+", uuid="+uuid+"]";
    }
}
