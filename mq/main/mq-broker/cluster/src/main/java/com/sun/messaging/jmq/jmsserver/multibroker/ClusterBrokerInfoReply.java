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
 * @(#)ClusterBrokerInfoReply.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.*;
import java.util.*;
import java.nio.*;

import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/**
 * BROKER_INFO_REPLY
 */

public class ClusterBrokerInfoReply 
{
    private static boolean DEBUG = false;

    private BrokerInfo brokerInfo = null;
    private int status = ProtocolGlobals.G_BROKER_INFO_OK;

    private GPacket pkt = null;

    private ClusterBrokerInfoReply(BrokerInfo bi, int status) {
        this.brokerInfo = bi;
        this.status = status;
    }

    private ClusterBrokerInfoReply(GPacket pkt) throws Exception {

        assert ( pkt.getType() == ProtocolGlobals.G_BROKER_INFO_REPLY );

        this.pkt = pkt;
        status = ((Integer)pkt.getProp("S")).intValue(); 

        ByteArrayInputStream bis = new ByteArrayInputStream(pkt.getPayload().array());
        ObjectInputStream ois = new FilteringObjectInputStream(bis);
        brokerInfo = (BrokerInfo) ois.readObject();
 
    }

    /**
     */
    public static ClusterBrokerInfoReply newInstance(BrokerInfo bi, int status) {
        return new ClusterBrokerInfoReply(bi, status);
    }

    /**
     */
    public static ClusterBrokerInfoReply newInstance(GPacket pkt) throws Exception {
        return new ClusterBrokerInfoReply(pkt);
    }

    public GPacket getGPacket() throws Exception { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_BROKER_INFO_REPLY);
        gp.setBit(pkt.A_BIT, false);
        gp.putProp("S", Integer.valueOf(status));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(brokerInfo);
        oos.flush();
        oos.close();

        byte[] buf = bos.toByteArray();
        gp.setPayload(ByteBuffer.wrap(buf));
        return gp;
    }

    public int getStatus() {
        return status;
    }

    public BrokerInfo getBrokerInfo() {
        return brokerInfo;
    }

    public boolean isTakingover() {
        return (getStatus() == ProtocolGlobals.G_BROKER_INFO_TAKINGOVER);
    }

    public boolean sendAndClose() {
        return isTakingover();
    }
}
