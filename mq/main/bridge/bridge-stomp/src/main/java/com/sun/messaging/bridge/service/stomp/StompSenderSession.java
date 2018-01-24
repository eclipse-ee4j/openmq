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

package com.sun.messaging.bridge.service.stomp;

import java.util.Iterator;
import java.util.Enumeration;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.*;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.StompMessage;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;


/**
 * @author amyk 
 */
public class StompSenderSession implements StompSession  {

    protected Logger logger = null;
    protected StompBridgeResources sbr = null;

    protected Session session = null;
    protected boolean closed = false;

    protected MessageProducer producer = null;
    protected StompConnectionImpl stompconn = null;
    protected Connection connection = null;

    public StompSenderSession(StompConnectionImpl stompc) throws Exception {
        sbr = StompServer.getStompBridgeResources();
        stompconn = stompc;
        logger = stompc.getProtocolHandler().getLogger();
        connection = stompconn.getConnection();
        session = createSession();
        producer = session.createProducer(null);
    }

    protected Session createSession() throws JMSException {
        return connection.createSession(false, 0);
    }

    protected Session getJMSSession() throws Exception {
        checkSession();
        return session;
    }

    public void sendStompMessage(StompFrameMessage message) throws Exception { 
        checkSession();

	MessageTransformer<Message, Message> mt = 
            stompconn.getProtocolHandler().getMessageTransformer();

        StompMessageImpl msg = new StompMessageImpl(mt);
        stompconn.getProtocolHandler().fromStompFrameMessage(message, msg);
        Message jmsmsg = msg.jmsmsg;
        Destination jmsdest = msg.jmsdest;

        if (mt != null) {
            mt.init(session, Bridge.STOMP_TYPE);
            jmsmsg = mt.transform(jmsmsg, false, "UTF-8",
                     MessageTransformer.STOMP, MessageTransformer.SUN_MQ, msg.propsForTransformer);
            if (jmsmsg == null) {
                throw new JMSException("null returned from "+ mt.getClass().getName()+ " transform() method");
            }
        }
        producer.send(jmsdest, jmsmsg, jmsmsg.getJMSDeliveryMode(),
            jmsmsg.getJMSPriority(), jmsmsg.getJMSExpiration());
        logger.log(Level.FINE, "Sent message "+jmsmsg.getJMSMessageID());
    }


    /**
     */
    public void close() throws Exception {
        session.close();
        closed = true;
    }

    protected void checkSession() throws Exception { 
        if (closed) {
            throw new JMSException(StompServer.getStompBridgeResources().getKString(
                                             StompBridgeResources.X_SESSION_CLOSED));
        }
    }

    @Override
    public StompDestination createStompDestination(String name, boolean isQueue)
    throws Exception {
        if (isQueue) {
            return new StompDestinationImpl(
                session.createQueue(name));
        }
        return new StompDestinationImpl(
            session.createTopic(name));
    }

    @Override
    public StompDestination createTempStompDestination(boolean isQueue)
    throws Exception {
        if (isQueue) {
            return new StompDestinationImpl(
                session.createTemporaryQueue());
        }
        return new StompDestinationImpl(
            session.createTemporaryTopic());
    }

    class StompMessageImpl implements StompMessage {
        Message jmsmsg = null;
        Destination jmsdest = null;
        MessageTransformer mt = null;
        Properties propsForTransformer = null;

        public StompMessageImpl(MessageTransformer mt) {
            this.mt = mt;
        }

        public void setText(StompFrameMessage message) throws Exception {
            jmsmsg = session.createTextMessage();
            ((TextMessage)jmsmsg).setText(message.getBodyText());
        }
        public void setBytes(StompFrameMessage message) throws Exception {
            jmsmsg = session.createBytesMessage();
            ((BytesMessage)jmsmsg).writeBytes(message.getBody());
        }
        public void setDestination(String stompdest) throws Exception {
            StompDestination d = stompconn.getProtocolHandler().
                toStompDestination(stompdest, StompSenderSession.this, false);
            this.jmsdest = ((StompDestinationImpl)d).getJMSDestination();
        }
        public void setReplyTo(String replyto) throws Exception {
            if (replyto == null) {
                return;
            }
            StompDestination dr = stompconn.getProtocolHandler().
                toStompDestination(replyto, StompSenderSession.this, false);
            Destination jmsdestr = ((StompDestinationImpl)dr).getJMSDestination();
            jmsmsg.setJMSReplyTo(jmsdestr);
        }
        public void setPersistent(String v) throws Exception {
            if (v == null) {
                return;
            }
            int deliveryMode = (Boolean.valueOf(v) ? 
                            DeliveryMode.PERSISTENT :
                            DeliveryMode.NON_PERSISTENT);
            jmsmsg.setJMSDeliveryMode(deliveryMode);
        }
        public void setJMSExpiration(String v) throws Exception {
            if (v == null) {
                return;
            }
            long timeToLive = Long.parseLong(v);
            if (timeToLive != 0L) {
                jmsmsg.setJMSExpiration(timeToLive);
            }
        }
        public void setJMSPriority(String v) throws Exception {
            if (v == null) {
                return;
            }
            int pri = Integer.parseInt(v);
            jmsmsg.setJMSPriority(pri);
        }
        public void setJMSCorrelationID(String v) throws Exception {
            if (v != null) {
                jmsmsg.setJMSCorrelationID(v);
            }
        }
        public void setJMSType(String v) throws Exception {
            if (v != null) {
                jmsmsg.setJMSType(v);
            }
        }
        public void setProperty(String name, String value) throws Exception {
            try {
                jmsmsg.setStringProperty(name, value);
            } catch (JMSException e) {
                if (mt == null) {
                    throw e;
                }
                propsForTransformer = new Properties();
                propsForTransformer.setProperty(name, value);
                String h = name+"="+value;
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.WARNING, 
                        sbr.getKString(sbr.W_SET_JMS_PROPERTY_FAILED, h, e.getMessage()), e);
                } else {
                    logger.log(Level.WARNING, 
                        sbr.getKString(sbr.W_SET_JMS_PROPERTY_FAILED, h, e.getMessage()));
                }
                logger.log(Level.INFO, 
                    sbr.getString(sbr.I_PASS_HEADER_TO_TRANSFORMER, h, mt.getClass().getName()));
            }
        }

        public String getSubscriptionID() throws Exception {
            throw new RuntimeException("Unexpected call: getSubscriptionID()");
        }
        public String getDestination() throws Exception {
            throw new RuntimeException("Unexpected call: getDestination()");
        }
        public String getReplyTo() throws Exception {
            throw new RuntimeException("Unexpected call: getReplyTo()");
        }
        public String getJMSMessageID() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSMessageID()");
        }
        public String getJMSCorrelationID() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSCorrelationID()");
        }
        public String getJMSExpiration() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSExpiration()");
        }
        public String getJMSRedelivered() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSRedelivered()");
        }
        public String getJMSPriority() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSPriority()");
        }
        public String getJMSTimestamp() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSTimestamp()");
        }
        public String getJMSType() throws Exception {
            throw new RuntimeException("Unexpected call: getJMSType()");
        }
        public Enumeration getPropertyNames() throws Exception {
            throw new RuntimeException("Unexpected call: getPropertyNames()");
        }
        public String getProperty(String name) throws Exception {
            throw new RuntimeException("Unexpected call: getProperty()");
        }
        public boolean isTextMessage() throws Exception {
            throw new RuntimeException("Unexpected call: isTextMessage()");
        }
        public boolean isBytesMessage() throws Exception {
            throw new RuntimeException("Unexpected call: isBytesMessage()");
        }
        public String getText() throws Exception {
            throw new RuntimeException("Unexpected call: getText()");
        }
        public byte[] getBytes() throws Exception {
            throw new RuntimeException("Unexpected call: getBytes()");
        }
    }

}
