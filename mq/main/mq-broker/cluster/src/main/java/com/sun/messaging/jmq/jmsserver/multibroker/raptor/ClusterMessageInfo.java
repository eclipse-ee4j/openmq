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
 * @(#)ClusterMessageInfo.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterMessageInfo 
{
    protected Logger logger = Globals.getLogger();

    private static final String PROP_PREFIX_CUID_DCT = "CUID-DCT:";
    private static final String PROP_REDELIVERED = "redelivered";

    private PacketReference ref = null;
    private ArrayList<Consumer> consumers =  null; 
    private ArrayList<Integer> deliveryCnts =  null; 
    private boolean redelivered =  false; 
    private boolean sendMessageDeliveredAck = false;
    private Cluster c = null;

    private GPacket pkt = null;
    private DataInputStream dis = null;

    private ClusterMessageInfo(PacketReference ref, 
                               ArrayList<Consumer> consumers,
                               ArrayList<Integer> deliveryCnts,
                               boolean redelivered,
                               boolean sendMessageDeliveredAck, Cluster c) {
        this.ref = ref;
        this.consumers = consumers;
        this.deliveryCnts = deliveryCnts;
        this.redelivered = redelivered;
        this.sendMessageDeliveredAck = sendMessageDeliveredAck;
        this.c = c;
    }

    private ClusterMessageInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    /**
     * Destination to GPacket
     *
     * @param d The Destination to be marshaled to GPacket
     */
    public static ClusterMessageInfo newInstance(
        PacketReference ref,
        ArrayList<Consumer> consumers, 
        ArrayList<Integer> deliveryCnts,
        boolean redelivered,
        boolean sendMessageDeliveredAck, Cluster c) {

        return new ClusterMessageInfo(ref, consumers, deliveryCnts,
                          redelivered, sendMessageDeliveredAck, c);
    }

    /**
     * GPacket to Destination 
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterMessageInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterMessageInfo(pkt, c);
    }

    public GPacket getGPacket() throws Exception {
        assert ( ref !=  null );
        assert ( consumers !=  null );

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_MESSAGE_DATA);
        gp.putProp("D", Boolean.valueOf(sendMessageDeliveredAck));
        gp.putProp("C", Integer.valueOf(consumers.size()));
        if (Globals.getDestinationList().isPartitionMode()) {
            gp.putProp("partitionID", Long.valueOf(
                ref.getPartitionedStore().getPartitionID().longValue()));
        }
        c.marshalBrokerAddress(c.getSelfAddress(), gp);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);


        Packet roPkt = null;
        try {
            for (int i = 0; i < consumers.size(); i++) {
                ConsumerUID intid = consumers.get(i).getConsumerUID();
                ClusterConsumerInfo.writeConsumerUID(intid, dos);
                gp.putProp(PROP_PREFIX_CUID_DCT+intid.longValue(),
                           deliveryCnts.get(i));
            }
            if (redelivered) {
                gp.putProp(PROP_REDELIVERED, Boolean.valueOf(redelivered));
            }
            roPkt = ref.getPacket();
            if (roPkt == null) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                          BrokerResources.X_NULL_PACKET_FROM_REF, ref.toString()));
            }
            roPkt.generateTimestamp(false);
            roPkt.generateSequenceNumber(false);

            roPkt.writePacket(dos);
            dos.flush();
            bos.flush();
           
        } catch (Exception e) {
            String emsg =  Globals.getBrokerResources().getKString(
                           BrokerResources.X_EXCEPTION_WRITE_PKT_ON_SEND_MSG_REMOTE, ref.toString(), e.getMessage());
            if (e instanceof BrokerException) {
                logger.log(Logger.WARNING, emsg);
                throw e;
            } 
            logger.logStack(Logger.WARNING, emsg, e);
            throw e;
        }

        byte[] buf = bos.toByteArray();
        gp.setPayload(ByteBuffer.wrap(buf));

        return gp;
    }

    public String toString() {
        if (consumers == null || ref == null) {
            return super.toString();
        }
        StringBuffer buf = new StringBuffer("\n");
        for (int i = 0; i < consumers.size(); i++) {
            ConsumerUID intid = ((Consumer) consumers.get(i)).getConsumerUID();
            buf.append("\t").append(intid).append("\n");
        }
        return buf.toString();
    }

    public Long getPartitionID() {
        assert (pkt != null);
        return (Long)pkt.getProp("partitionID");
    }

    public BrokerAddress getHomeBrokerAddress() throws Exception {
        assert (pkt != null);
        return c.unmarshalBrokerAddress(pkt);
    }

    public boolean getSendMessageDeliveredAck() {
        assert (pkt != null);
        return ((Boolean) pkt.getProp("D")).booleanValue();
    }

    public int getConsumerCount() {
        assert (pkt != null);
        return ((Integer) pkt.getProp("C")).intValue();
    }

    private Boolean getRedelivered() {
        assert (pkt != null);
        return (Boolean)pkt.getProp(PROP_REDELIVERED);
    }

    /**
     * @return null if not found
     */
    public Integer getDeliveryCount(ConsumerUID cuid) {
        assert (pkt != null);
        return (Integer)pkt.getProp(PROP_PREFIX_CUID_DCT+cuid.longValue());
    }

    /**
     * must called in the following order: 
     *
     * initPayloadRead()
     * readPayloadConsumerUIDs()
     * readPayloadMessage()
     */
    public void initPayloadRead() {
        assert ( pkt != null );
        ByteArrayInputStream bis = new ByteArrayInputStream(pkt.getPayload().array());
        dis = new DataInputStream(bis);
    }
    public Iterator readPayloadConsumerUIDs() {
        assert ( pkt !=  null );
        assert ( dis !=  null );

        return new ProtocolConsumerUIDIterator(dis, getConsumerCount());
    }
    public Packet readPayloadMessage() throws IOException {
        assert ( pkt !=  null );
        assert ( dis !=  null );

        Packet roPkt = new Packet(false);
        roPkt.generateTimestamp(false);
        roPkt.generateSequenceNumber(false);
        roPkt.readPacket(dis);
        Boolean b = getRedelivered();
        if (b != null) {
            roPkt.setRedelivered(b.booleanValue());
        }
        return roPkt;
    }

    public boolean needReply() {
        assert ( pkt != null );
        return pkt.getBit(pkt.A_BIT);
    }

    public GPacket getReplyGPacket(int status) {
        assert ( pkt != null );

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_MESSAGE_DATA_REPLY);
        gp.putProp("S", Integer.valueOf(status));
        // TBD: ADD SysMessageID as property?

        return gp;
    }

}

