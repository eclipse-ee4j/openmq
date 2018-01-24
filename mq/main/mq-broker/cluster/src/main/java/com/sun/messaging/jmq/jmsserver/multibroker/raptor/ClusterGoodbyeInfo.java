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
 * @(#)ClusterGoodbyeInfo.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterGoodbyeInfo 
{
    private boolean requestTakeover = false;
    private Cluster c = null;

    private GPacket pkt = null;
    private BrokerAddress sender = null;

    private ClusterGoodbyeInfo(boolean requestTakeover, Cluster c) {
        this.requestTakeover = requestTakeover;
        this.c = c;
    }

    private ClusterGoodbyeInfo(GPacket pkt, Cluster c) throws Exception {
        assert (pkt.getType() == ProtocolGlobals.G_GOODBYE );

        this.pkt = pkt;
        this.c = c;

        sender = c.unmarshalBrokerAddress(pkt);
        if (sender.getHAEnabled()) {
            requestTakeover = ((Boolean)pkt.getProp("requestTakeover")).booleanValue();
        }
    }

    /**
     */
    public static ClusterGoodbyeInfo newInstance(boolean requestTakeover, Cluster c) {
        return new ClusterGoodbyeInfo(requestTakeover, c);
    }

    /**
     */
    public static ClusterGoodbyeInfo newInstance(Cluster c) {
        return new ClusterGoodbyeInfo(false, c);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterGoodbyeInfo newInstance(GPacket pkt, Cluster c) throws Exception {
        return new ClusterGoodbyeInfo(pkt, c);
    }

    public GPacket getGPacket() { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_GOODBYE);
        gp.setBit(gp.A_BIT, true);
        c.marshalBrokerAddress(c.getSelfAddress(), gp);
        if (c.getSelfAddress().getHAEnabled()) {
            gp.putProp("requestTakeover", Boolean.valueOf(requestTakeover));
        }

        return gp;
    }

    public boolean getRequestTakeover() {
        assert ( pkt != null );
        Boolean b = (Boolean)pkt.getProp("requestTakeover");
        if (b == null) return false;
        return b.booleanValue();
    }


    public boolean needReply() {
        assert ( pkt != null );
        return pkt.getBit(pkt.A_BIT);
    }

    public String toString() {
        if (pkt == null) {
            if (Globals.getHAEnabled()) {
                return "requestTakeover="+requestTakeover+" "+c.getSelfAddress().toString();
            }
            return  c.getSelfAddress().toString();
        }

        if (sender.getHAEnabled()) {
            return "requestTakeover="+requestTakeover+" "+sender.toString();
        }
        return sender.toString();
    }

    public static GPacket getReplyGPacket(int status) {
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_GOODBYE_REPLY);
        gp.putProp("S", Integer.valueOf(status));
        return gp;
    }
}
