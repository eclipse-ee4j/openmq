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
 * @(#)HeartbeatInfo.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.heartbeat;

import java.io.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;

/**
 */
public class HeartbeatInfo { 

    public static final short HEARTBEAT_ALIVE = 1;
    public static final int HEARTBEAT_PROTOCOL_VERSION = 400;

    private String brokerID = null;
    private long brokerSession = 0;
    private BrokerMQAddress brokerAddress = null;
    private String toBrokerID = null;
    private long toBrokerSession = 0;
    private long sequence = 0;

    private GPacket pkt = null;

    private HeartbeatInfo(GPacket pkt) {
        this.pkt = pkt;
    }

    private HeartbeatInfo() {
    }

    public static HeartbeatInfo newInstance() {
        return new HeartbeatInfo();
    }

    public static HeartbeatInfo newInstance(byte[] data) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        GPacket pkt = GPacket.getInstance();
        pkt.read(bis);
        int ver = ((Integer)pkt.getProp("protocolVersion")).intValue();
        if (ver < HEARTBEAT_PROTOCOL_VERSION) {
            throw new IOException("Protocol version not supported:"+ver);
        }
        return new HeartbeatInfo(pkt);
    }

    public GPacket getGPacket() {
        GPacket gp = GPacket.getInstance();
        gp.generateSequenceNumber(false);
        gp.setType(HEARTBEAT_ALIVE);
        gp.setSequence(sequence);
        gp.putProp("protocolVersion", Integer.valueOf(HEARTBEAT_PROTOCOL_VERSION));
        gp.putProp("brokerID", brokerID);
        gp.putProp("brokerSession", Long.valueOf(brokerSession));
        gp.putProp("brokerAddress", brokerAddress.toString());
        gp.putProp("toBrokerID", toBrokerID);
        gp.putProp("toBrokerSession", Long.valueOf(toBrokerSession));
        return gp;
    }

    public static byte[] toByteArray(GPacket pkt) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            pkt.write(bos);
            bos.flush();
        }catch (Exception e) {}

        return bos.toByteArray();
    }

    public void setBrokerID(String id) {
        this.brokerID = id;
    }

    public void setBrokerSession(long uid) {
        this.brokerSession = uid;
    }

    public void setBrokerAddress(BrokerMQAddress ma) {
        this.brokerAddress = ma;
    }

    public void setToBrokerID(String id) {
        this.toBrokerID = id;
    }

    public void setToBrokerSession(long uid) {
        this.toBrokerSession = uid;
    }

    public void setSequence(long s) {
        this.sequence = s;
    }

    public String getBrokerID() {
        assert ( pkt != null ); 
        return (String)pkt.getProp("brokerID");
    }

    public long getBrokerSession() {
        assert ( pkt != null ); 
        return ((Long)pkt.getProp("brokerSession")).longValue();
    }

    public String getToBrokerID() {
        assert ( pkt != null ); 
        return (String)pkt.getProp("toBrokerID");
    }

    public long getToBrokerSession() {
        assert ( pkt != null ); 
        return ((Long)pkt.getProp("toBrokerSession")).longValue();
    }

    public long getSequence() {
        assert ( pkt != null ); 
        return pkt.getSequence();
    }

    public String toString() {
        if (pkt != null) {
            return "#"+getSequence()+" ["+getBrokerID()+","+getBrokerSession()+"] to " +
                   "["+getToBrokerID()+","+getToBrokerSession()+"]";
        } else {
            return "["+brokerID+","+brokerSession+"] to " +
                   "["+toBrokerID+","+toBrokerSession+"]";
        }
    }

}
