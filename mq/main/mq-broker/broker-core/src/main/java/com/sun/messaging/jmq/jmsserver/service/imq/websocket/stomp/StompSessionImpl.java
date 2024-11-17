/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Hashtable;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessagePriority;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.bridge.api.StompMessage;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;
import com.sun.messaging.bridge.api.StompProtocolException;

/**
 * @author amyk
 */
@SuppressWarnings("JdkObsolete")
public abstract class StompSessionImpl implements StompSession {

    protected static final String QUEUE_CLASS_NAME = "com.sun.messaging.BasicQueue";
    protected static final String TOPIC_CLASS_NAME = "com.sun.messaging.BasicTopic";
    protected static final String TEMP_QUEUE_CLASS_NAME = "com.sun.messaging.jmq.jmsclient.TemporaryQueueImpl";
    protected static final String TEMP_TOPIC_CLASS_NAME = "com.sun.messaging.jmq.jmsclient.TemporaryTopicImpl";

    protected static final Logger logger = Globals.getLogger();
    protected static final BrokerResources br = Globals.getBrokerResources();

    protected JMSService jmsservice = null;
    protected long connectionId = 0L;
    protected boolean isTransacted = false;
    protected SessionAckMode ackMode = SessionAckMode.AUTO_ACKNOWLEDGE;
    protected boolean clientackThisMessage = false;
    protected long sessionId = 0L;

    protected StompConnectionImpl stompconn = null;

    protected Object closeLock = new Object();
    protected boolean closing = false, closed = false;

    public StompSessionImpl(StompConnectionImpl stompc, StompAckMode ackmode, boolean transacted) throws Exception {
        stompconn = stompc;
        jmsservice = stompc.getJMSService();
        Long cid = stompc.getConnectionID();
        if (cid == null) {
            throw new StompProtocolException("Not connected");
        }
        connectionId = cid.longValue();
        isTransacted = transacted;
        if (transacted) {
            this.ackMode = SessionAckMode.TRANSACTED;
        } else {
            if (ackmode == StompAckMode.AUTO_ACK) {
                this.ackMode = SessionAckMode.AUTO_ACKNOWLEDGE;
            } else if (ackmode == StompAckMode.CLIENT_ACK) {
                this.ackMode = SessionAckMode.CLIENT_ACKNOWLEDGE;
            } else if (ackmode == StompAckMode.CLIENT_INDIVIDUAL_ACK) {
                this.ackMode = SessionAckMode.CLIENT_ACKNOWLEDGE;
                clientackThisMessage = true;
            } else {
                throw new IllegalArgumentException("Unsupported ack mode:" + ackmode);
            }
        }
        JMSServiceReply reply = jmsservice.createSession(connectionId, this.ackMode);
        sessionId = reply.getJMQSessionID();
    }

    protected final boolean getDEBUG() {
        return (logger.isFineLoggable() || stompconn.getProtocolHandler().getDEBUG());
    }

    protected final boolean isTransacted() {
        return isTransacted;
    }

    // subclass override
    protected long getTransactionId() {
        return 0L;
    }

    public void close() throws Exception {
        synchronized (closeLock) {
            if (closing) {
                return;
            }
            closing = true;
        }
        try {
            closeProducers();
            jmsservice.stopSession(connectionId, sessionId, true);
            closeSubscribers();
        } catch (Exception e) {
            logger.logStack(logger.WARNING, e.getMessage(), e);
        } finally {
            jmsservice.destroySession(connectionId, sessionId);
        }
        synchronized (closeLock) {
            closed = true;
        }
    }

    protected boolean isClosing() {
        synchronized (closeLock) {
            return (closed || closing);
        }
    }

    protected void closeProducers() {
    }

    protected void closeSubscribers() {
    }

    protected void unsubscribeDurable(String duraname) throws Exception {
        if (duraname == null) {
            return;
        }
        jmsservice.deleteConsumer(connectionId, sessionId, 0L, null, false, duraname, stompconn.getClientID());
    }

    protected void checkSession() throws Exception {
        synchronized (closeLock) {
            if (closing || closed) {
                throw new StompProtocolException("Session " + this + " is closed");
            }
        }
    }

    @Override
    public StompDestination createStompDestination(String name, boolean isQueue) throws Exception {
        if (isQueue) {
            return new StompDestinationImpl(
                    new Destination(name, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE, com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD));
        }
        return new StompDestinationImpl(
                new Destination(name, com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC, com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD));
    }

    @Override
    public StompDestination createTempStompDestination(boolean isQueue) throws Exception {
        String name = null;
        if (isQueue) {
            name = ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_QUEUE_URI_NAME + stompconn.getIdForTemporaryDestination();
            return new StompDestinationImpl(new Destination(name, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE,
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY));

        }
        name = ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_TOPIC_URI_NAME + stompconn.getIdForTemporaryDestination();
        return new StompDestinationImpl(
                new Destination(name, com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC, com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY));
    }

    protected StompDestination constructStompDestination(String destName, Packet pkt) throws Exception {

        if (destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_QUEUE_URI_NAME)
                || destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + Destination.Type.QUEUE)) {
            return new StompDestinationImpl(new Destination(destName, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE,
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY));
        }
        if (destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_TOPIC_URI_NAME)
                || destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + Destination.Type.TOPIC)) {
            return new StompDestinationImpl(new Destination(destName, com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC,
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY));
        }
        if (pkt.getIsQueue()) {
            return new StompDestinationImpl(new Destination(destName, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE,
                    com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD));
        }
        return new StompDestinationImpl(
                new Destination(destName, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE, com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD));
    }

    protected StompFrameMessage toStompFrameMessage(final String subid, final String stompdest, final Packet pkt, final boolean needAck) throws Exception {

        final StompProtocolHandler mysph = stompconn.getProtocolHandler();

        return mysph.toStompFrameMessage(new StompMessage() {

            @Override
            public String getSubscriptionID() {
                return subid;
            }

            @Override
            public String getDestination() {
                return stompdest;
            }

            @Override
            public String getReplyTo() throws Exception {
                String replyto = pkt.getReplyTo();
                if (replyto == null) {
                    return null;
                }
                StompDestination d = constructStompDestination(replyto, pkt);
                return mysph.toStompFrameDestination(d, true);
            }

            @Override
            public String getJMSMessageID() {
                return SysMessageID.ID_PREFIX + pkt.getSysMessageID().toString();
            }

            @Override
            public String getJMSCorrelationID() {
                return pkt.getCorrelationID();
            }

            @Override
            public String getJMSExpiration() {
                return String.valueOf(pkt.getExpiration());
            }

            @Override
            public String getJMSRedelivered() {
                return String.valueOf(pkt.getRedelivered());
            }

            @Override
            public String getJMSPriority() {
                return String.valueOf(pkt.getPriority());
            }

            @Override
            public String getJMSTimestamp() {
                return String.valueOf(pkt.getTimestamp());
            }

            @Override
            public String getJMSType() {
                return pkt.getMessageType();
            }

            @Override
            public Enumeration getPropertyNames() throws Exception {
                Hashtable props = pkt.getProperties();
                if (props == null) {
                    props = new Hashtable();
                }
                return props.keys();
            }

            @Override
            public String getProperty(String name) throws Exception {
                Hashtable props = pkt.getProperties();
                if (props == null) {
                    return null;
                }
                Object v = props.get(name);
                if (v == null) {
                    return null;
                }
                return v.toString();
            }

            @Override
            public boolean isTextMessage() {
                return (pkt.getPacketType() == PacketType.TEXT_MESSAGE);
            }

            @Override
            public boolean isBytesMessage() {
                return (pkt.getPacketType() == PacketType.BYTES_MESSAGE);
            }

            @Override
            public String getText() throws UnsupportedEncodingException {
                byte[] body = pkt.getMessageBodyByteArray();
                if (body == null) {
                    return null;
                }
                return new String(body, "UTF-8");
            }

            @Override
            public byte[] getBytes() {
                return pkt.getMessageBodyByteArray();
            }

            @Override
            public void setText(StompFrameMessage message) throws Exception {
                throw new RuntimeException("Unexpected call: setText()");
            }

            @Override
            public void setBytes(StompFrameMessage message) throws Exception {
                throw new RuntimeException("Unexpected call: setBytes()");
            }

            @Override
            public void setDestination(String stompdest) throws Exception {
                throw new RuntimeException("Unexpected call: setDestination()");
            }

            @Override
            public void setPersistent(String stompdest) throws Exception {
                throw new RuntimeException("Unexpected call: setPersistent()");
            }

            @Override
            public void setReplyTo(String replyto) throws Exception {
                throw new RuntimeException("Unexpected call: setReplyTo()");
            }

            @Override
            public void setJMSCorrelationID(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSCorrelationID()");
            }

            @Override
            public void setJMSExpiration(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSExpiration()");
            }

            @Override
            public void setJMSPriority(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSPriority()");
            }

            @Override
            public void setJMSType(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSType()");
            }

            @Override
            public void setProperty(String name, String value) throws Exception {
                throw new RuntimeException("Unexpected call: setProperty()");
            }

        }, needAck);
    }

    protected StompDestinationImpl fromStompFrameMessage(StompFrameMessage message, Packet pkt) throws Exception {

        StompMessageImpl msg = new StompMessageImpl(pkt);
        stompconn.getProtocolHandler().fromStompFrameMessage(message, msg);
        msg.setProperties();
        return msg.d;
    }

    class StompMessageImpl implements StompMessage {
        private Packet pkt = null;
        StompDestinationImpl d = null;
        private Hashtable properties = null;

        StompMessageImpl(Packet pkt) {
            this.pkt = pkt;
        }

        @Override
        public void setText(StompFrameMessage message) throws Exception {
            pkt.setPacketType(PacketType.TEXT_MESSAGE);
            pkt.setMessageBody(message.getBody());
        }

        @Override
        public void setBytes(StompFrameMessage message) throws Exception {
            pkt.setPacketType(PacketType.BYTES_MESSAGE);
            pkt.setMessageBody(message.getBody());
        }

        @Override
        public void setDestination(String stompdest) throws Exception {
            this.d = (StompDestinationImpl) stompconn.getProtocolHandler().toStompDestination(stompdest, StompSessionImpl.this, false/* from sub */);
            this.d.setStompDestinationString(stompdest);
            pkt.setDestination(d.getName());
            if (d.isQueue()) {
                pkt.setIsQueue(true);
                if (d.isTemporary()) {
                    pkt.setDestinationClass(TEMP_QUEUE_CLASS_NAME);
                } else {
                    pkt.setDestinationClass(QUEUE_CLASS_NAME);
                }
            } else {
                pkt.setIsQueue(false);
                if (d.isTemporary()) {
                    pkt.setDestinationClass(TEMP_TOPIC_CLASS_NAME);
                } else {
                    pkt.setDestinationClass(TOPIC_CLASS_NAME);
                }
            }
        }

        @Override
        public void setReplyTo(String replyto) throws Exception {
            if (replyto == null) {
                return;
            }
            StompDestination d = stompconn.getProtocolHandler().toStompDestination(replyto, StompSessionImpl.this, false/* from sub */);
            pkt.setReplyTo(d.getName());
            if (d.isQueue()) {
                if (d.isTemporary()) {
                    pkt.setDestinationClass(TEMP_QUEUE_CLASS_NAME);
                } else {
                    pkt.setDestinationClass(QUEUE_CLASS_NAME);
                }
            } else {
                if (d.isTemporary()) {
                    pkt.setDestinationClass(TEMP_TOPIC_CLASS_NAME);
                } else {
                    pkt.setDestinationClass(TOPIC_CLASS_NAME);
                }
            }
        }

        @Override
        public void setPersistent(String v) throws Exception {
            if (v != null && Boolean.valueOf(v)) {
                pkt.setPersistent(true);
            }
        }

        @Override
        public void setJMSExpiration(String v) throws Exception {
            if (v == null) {
                return;
            }
            long timeToLive = Long.parseLong(v);
            if (timeToLive != 0L) {
                long expiration = timeToLive + System.currentTimeMillis();
                pkt.setExpiration(expiration);
            }
        }

        @Override
        public void setJMSPriority(String v) throws Exception {
            if (v == null) {
                return;
            }
            int pri = Integer.parseInt(v);
            boolean valid = false;
            for (MessagePriority p : MessagePriority.values()) {
                if (p.priority() == pri) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new StompProtocolException("Invalid priority header value: " + pri);
            }
            pkt.setPriority(pri);
        }

        @Override
        public void setJMSCorrelationID(String v) throws Exception {
            if (v != null) {
                pkt.setCorrelationID(v);
            }
        }

        @Override
        public void setJMSType(String v) throws Exception {
            if (v != null) {
                pkt.setMessageType(v);
            }
        }

        @Override
        public void setProperty(String name, String value) throws Exception {
            if (properties == null) {
                properties = new Hashtable();
            }
            properties.put(name, value);
        }

        public void setProperties() throws Exception {
            pkt.setProperties(properties);
        }

        @Override
        public String getSubscriptionID() throws Exception {
            throw new RuntimeException("Unexpected call: getSubscriptionID()");
        }

        @Override
        public String getDestination() throws Exception {
            throw new RuntimeException("Unexpected call: getDestination()");
        }

        @Override
        public String getReplyTo() throws Exception {
            throw new RuntimeException("Unexpected call: getReplyTo()");
        }

        @Override
        public String getJMSMessageID() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSMessageID()");
        }

        @Override
        public String getJMSCorrelationID() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSCorrelationID()");
        }

        @Override
        public String getJMSExpiration() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSExpiration()");
        }

        @Override
        public String getJMSRedelivered() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSRedelivered()");
        }

        @Override
        public String getJMSPriority() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSPriority()");
        }

        @Override
        public String getJMSTimestamp() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSTimestamp()");
        }

        @Override
        public String getJMSType() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSType()");
        }

        @Override
        public Enumeration getPropertyNames() throws Exception {
            throw new RuntimeException("Unexpected call: getPropertyNames()");
        }

        @Override
        public String getProperty(String name) throws Exception {
            throw new RuntimeException("Unexpected call: getProperty()");
        }

        @Override
        public boolean isTextMessage() throws Exception {
            throw new RuntimeException("Unexpected call: isTextMessage()");
        }

        @Override
        public boolean isBytesMessage() throws Exception {
            throw new RuntimeException("Unexpected call: isBytesMessage()");
        }

        @Override
        public String getText() throws Exception {
            throw new RuntimeException("Unexpected call: getText()");
        }

        @Override
        public byte[] getBytes() throws Exception {
            throw new RuntimeException("Unexpected call: getBytes()");
        }
    }

}
