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

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.LinkedHashMap;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageDeliveryMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessagePriority;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;


/**
 * @author amyk 
 */
public class StompSenderSession extends StompSessionImpl  {

    //protected by closeLock
    protected Map<String, Long> producers = new HashMap<String, Long>();

    public StompSenderSession(StompConnectionImpl stompc) throws Exception {
        super(stompc, StompAckMode.AUTO_ACK, false);
    }

    protected StompSenderSession(StompConnectionImpl stompc, boolean transacted)
    throws Exception {
        super(stompc, StompAckMode.AUTO_ACK, transacted);
    }

    @Override
    public String toString() {
        return "[StompSenderSession@"+hashCode()+
               ", producers="+producers.size()+"]";
    }

    @Override
    protected synchronized void closeProducers() {
        Iterator<Long> itr = producers.values().iterator(); 
        Long prodid = null;
        while (itr.hasNext()) {
            prodid = itr.next();
            try {
                jmsservice.deleteProducer(connectionId, sessionId, prodid.longValue());
            } catch (Exception e) {
                if (!isClosing() || getDEBUG()) {
                    logger.logStack(logger.WARNING, e.getMessage(), e);
                }
            }
        }
        producers.clear();
    }

    public void sendStompMessage(StompFrameMessage message) throws Exception {
        checkSession();

        Packet pkt = new Packet();
        pkt.setPersistent(jmsservice.DEFAULT_MessageDeliveryMode ==
                          MessageDeliveryMode.PERSISTENT);
        pkt.setPriority(jmsservice.DEFAULT_MessagePriority.priority());
        pkt.setExpiration(jmsservice.DEFAULT_TIME_TO_LIVE);
        pkt.setDeliveryTime(jmsservice.DEFAULT_DELIVERY_DELAY);
        stompconn.fillRemoteIPAndPort(pkt);

        StompDestinationImpl d = fromStompFrameMessage(message, pkt);
        String stompdest = d.getStompDestinationString();

        try {
            jmsservice.createDestination(connectionId, d.getDestination());
        } catch (JMSServiceException jmsse) {
            JMSServiceReply.Status status = jmsse.getJMSServiceReply().getStatus();
            if (status == JMSServiceReply.Status.CONFLICT) {
                if (logger.isFineLoggable() || stompconn.getProtocolHandler().getDEBUG()) {
                    logger.log(logger.INFO, "Destination "+stompdest+" already exist");
                }
            } else {
                throw jmsse;
            }
        }
        synchronized(this) {
            Long prodid = producers.get(stompdest);
            if (prodid == null) {
                JMSServiceReply reply = jmsservice.addProducer(
                    connectionId, sessionId, d.getDestination());
                prodid = Long.valueOf(reply.getJMQProducerID());
                producers.put(stompdest, prodid);
            }
            pkt.setProducerID(prodid.longValue());
        }

        pkt.prepareToSend();
        synchronized(this) {
            if (isTransacted()) {
                pkt.setTransactionID(getTransactionId());
            } else {
                pkt.setTransactionID(0L);
            }
            final Packet p = pkt;
            jmsservice.sendMessage(connectionId, new JMSPacket() {
                       public Packet getPacket() {
                       return p;
                   }
                   });
        }
        if (logger.isFineLoggable() || stompconn.getProtocolHandler().getDEBUG()) {
            logger.log(logger.INFO, "Sent message "+pkt.getSysMessageID());
        }
    }
}
