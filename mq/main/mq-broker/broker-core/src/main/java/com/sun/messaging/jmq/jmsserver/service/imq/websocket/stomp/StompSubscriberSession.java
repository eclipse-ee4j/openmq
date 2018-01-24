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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp;

import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.Consumer;
import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;
import com.sun.messaging.bridge.api.StompMessage;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompSubscriber;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;


/**
 * @author amyk 
 */
public class StompSubscriberSession 
extends StompSessionImpl implements StompSubscriber, Consumer {

    private StompOutputHandler out = null;
    private String subid = null;
    private String duraname = null;
    private String stompdest = null;
    private long consumerId = 0L;

    private List<SysMessageID> unackedMessages = 
        Collections.synchronizedList(new ArrayList<SysMessageID>());

    public StompSubscriberSession(String subid, 
        StompAckMode ackMode, StompConnectionImpl stompc)
        throws Exception {

        super(stompc, ackMode, false);
        this.subid = subid;
    }
    
    @Override
    public String toString() {
        return "[StompSubscriberSession@"+hashCode()+", subid="+
                subid+"["+consumerId+"], dura="+duraname+", stompdest+"+
                stompdest+", unacks="+unackedMessages.size()+"]";
    }

    @Override
    protected void closeSubscribers() {
        if (consumerId == 0L) {
            return;
        }
        try {
            SysMessageID lastseen = null;
            synchronized(unackedMessages) {
                int sz= unackedMessages.size();
                if (sz > 0) { 
                    lastseen = unackedMessages.get(sz-1);
                }
            }
            jmsservice.deleteConsumer(connectionId, sessionId,
                consumerId, lastseen, false, null,
                stompconn.getClientID());
            consumerId = 0L;
            unackedMessages.clear();
        } catch (Exception e) {
            if (!isClosing() || getDEBUG()) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            } 
        } 
    }

    public StompSubscriber createSubscriber(
        StompDestination d, String selector, String duraname, 
        boolean nolocal, StompOutputHandler out)
        throws Exception {

        if (consumerId != 0L) {
            throw new IllegalStateException("Subscriber already exists on this Session");
        }

        this.out = out;
        this.stompdest = stompconn.getProtocolHandler().toStompFrameDestination(d, false);
        this.duraname = duraname;
        Destination dest = ((StompDestinationImpl)d).getDestination();
        JMSServiceReply reply = null;
        try {
            reply = jmsservice.createDestination(connectionId, dest);
        } catch (JMSServiceException jmsse) {
            JMSServiceReply.Status status = jmsse.getJMSServiceReply().getStatus();
            if (status == JMSServiceReply.Status.CONFLICT) {
                if (logger.isFineLoggable() || 
                    stompconn.getProtocolHandler().getDEBUG()) {
                    logger.log(logger.INFO, "Destination "+stompdest+" already exist");
                }
            } else {
                throw jmsse;
            }
        }
        reply = jmsservice.startConnection(connectionId);
        reply = jmsservice.addConsumer(connectionId, sessionId,
                    dest, selector, duraname, (duraname != null), 
                    false, false, stompconn.getClientID(), nolocal);
        consumerId = reply.getJMQConsumerID();
        if (getDEBUG()) {
            logger.log(logger.INFO, "Created "+this);
        }
        return this;
    }

    @Override
    public void startDelivery() throws Exception {
        jmsservice.setConsumerAsync(connectionId, sessionId, consumerId, this);
    }

    public void ack(String msgid, boolean nack) throws Exception {
        checkSession();
        String cmd = (nack ? "[NACK]":"[ACK]");
        long conid = consumerId;
        if (conid == 0L) {
            throw new StompProtocolException(
            "Can't "+cmd+msgid+" because the subscriber "+subid + "is closed"); 
        }
        SysMessageID sysid = null;
        try {
            sysid = SysMessageID.get(msgid);
        } catch (RuntimeException e) {
            throw new StompProtocolException(
                cmd+"invalid message-id"+e.getMessage(), e); 
        }

        List<SysMessageID> list = new ArrayList<SysMessageID>();
        synchronized(unackedMessages) {
            int index = unackedMessages.indexOf(sysid);
            if (index < 0) {
                String emsg = cmd+br.getKString(
                    br.X_STOMP_MSG_NOTFOUND_ON_ACK, msgid, this.toString());;
                throw new StompProtocolException(emsg);
            }
            if (clientackThisMessage || nack) { 
                list.add(sysid);
            } else {
                SysMessageID tmpsysid = null;
                for (int i = 0; i <= index; i++) { 
                    tmpsysid = unackedMessages.get(i);
                    list.add(tmpsysid);
                }
            }
        }
        if (logger.isFineLoggable() || stompconn.getDEBUG()) {
            logger.logInfo(cmd+list.size()+" messages for subscriber "+
                           subid+" on connection "+stompconn, null);
        }
        Iterator<SysMessageID> itr = list.iterator();
        SysMessageID tmpsysid = null; 
        while (itr.hasNext()) {
            tmpsysid = itr.next();
            if (logger.isFinestLoggable() || stompconn.getDEBUG()) {
                logger.logInfo(cmd+"message "+tmpsysid+" for subscriber "+
                               subid+" on connection "+stompconn, null);
            }
            if (!nack) {
                jmsservice.acknowledgeMessage(connectionId, sessionId,
                    consumerId, tmpsysid, 0L, MessageAckType.ACKNOWLEDGE, 0);
            } else {
                jmsservice.acknowledgeMessage(connectionId, sessionId,
                    consumerId, tmpsysid, 0L, MessageAckType.DEAD, 1,
                    "STOMP:NACK", null);
            }
            unackedMessages.remove(tmpsysid);
        }
    }

    public String getDurableName() {
        return duraname;
    }

    @Override
    public JMSAck deliver(JMSPacket msgpkt)
    throws ConsumerClosedNoDeliveryException {

        if (closing || closed || stompconn.isClosed()) {
            throw new ConsumerClosedNoDeliveryException(
                "Subscriber "+this+" is closed");
        }
        try {
            final boolean needAck =  (ackMode != SessionAckMode.AUTO_ACKNOWLEDGE);
            StompFrameMessage msg = toStompFrameMessage(subid, stompdest,
                                        msgpkt.getPacket(), needAck);
            if (stompconn.getProtocolHandler().getDEBUG()) {
                logger.log(logger.INFO, 
                    " SEND message "+msg+" for "+toString());
            }
            if (ackMode != SessionAckMode.CLIENT_ACKNOWLEDGE) {
                out.sendToClient(msg, stompconn.getProtocolHandler(), null);
                return new Ack(msgpkt.getPacket(), MessageAckType.ACKNOWLEDGE);
            } else {
                unackedMessages.add(msgpkt.getPacket().getSysMessageID());
                out.sendToClient(msg, stompconn.getProtocolHandler(), null);
            }
           
        } catch (Exception e) {
            logger.logStack(logger.WARNING, e.getMessage(), e);
        }
        return null;
    }

    private class Ack implements JMSAck {
        private Packet msg = null;
        private MessageAckType acktype; 

        public Ack(Packet msg, MessageAckType acktype) {
            this.msg = msg;
            this.acktype = acktype;
        }

        public long getConnectionId() {
            return connectionId;
        }

        public long getSessionId() {
            return sessionId;
        }

        public long getConsumerId() {
            return consumerId;
        }

        public SysMessageID getSysMessageID() {
            return msg.getSysMessageID();
        }

        public long getTransactionId() {
            return 0L;
        }

        public MessageAckType getMessageAckType() {
            return acktype;
        }
    }
}
