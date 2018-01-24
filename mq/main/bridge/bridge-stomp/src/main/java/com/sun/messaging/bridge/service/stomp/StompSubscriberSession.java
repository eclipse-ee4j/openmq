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

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.*;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.StompMessage;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompConnection;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompSubscriber;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompUnrecoverableAckException;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;


/**
 * @author amyk 
 */
public class StompSubscriberSession
implements StompSession, StompSubscriber, MessageListener {

    private Logger _logger = null;
    private String _subid = null;

    private Session _session = null;
    private MessageConsumer _subscriber = null;

    private StompOutputHandler _out = null;
    private ArrayList<Message> _unacked = new ArrayList<Message>();
    private String _duraName = null;
    private StompConnectionImpl stompconn = null;
    private StompBridgeResources _sbr = null;
    private boolean _clientack = false;
    private boolean _clientack_thismsg = false;
    private int _ackfailureCount = 0;
    private int MAX_CONSECUTIVE_ACK_FAILURES = 3;

    public StompSubscriberSession(
        String id, StompAckMode ackMode, StompConnectionImpl sc) 
        throws Exception {

        stompconn = sc;
        _logger = stompconn.getProtocolHandler().getLogger();
        _subid = id;
        _sbr = StompServer.getStompBridgeResources();

        int jmsackmode = Session.AUTO_ACKNOWLEDGE;
        if (ackMode == StompAckMode.AUTO_ACK) {
        } else if (ackMode == StompAckMode.CLIENT_ACK) {
            jmsackmode = Session.CLIENT_ACKNOWLEDGE;
            _clientack = true; 
        } else if (ackMode == StompAckMode.CLIENT_INDIVIDUAL_ACK) {
            jmsackmode = Session.CLIENT_ACKNOWLEDGE;
            _clientack = true; 
            _clientack_thismsg = true; 
        } else {
            throw new StompProtocolException("Unsupported ackMode:"+ackMode);
        }
        _session = sc.getConnection().createSession(false, jmsackmode);
     
    }

    public StompSubscriber createSubscriber(
        StompDestination d, String selector, String duraname, 
        boolean nolocal, StompOutputHandler out) 
        throws Exception {

        Destination dest = ((StompDestinationImpl)d).getJMSDestination();

        if (_subscriber != null) {
            throw new javax.jms.IllegalStateException("createSubscriber(): Unexpected call");
        }
        _out = out;

        if (dest instanceof Queue) {
            _subscriber = _session.createConsumer(dest, selector);
        } else if (duraname != null) { 
            _subscriber = _session.createDurableSubscriber(
                                   (Topic)dest, duraname, selector, nolocal);
            _duraName = duraname;
        } else {
           _subscriber = _session.createConsumer(dest, selector, nolocal);
        }
        return this;
    }

    public void startDelivery() throws Exception {
        _subscriber.setMessageListener(this);
    }

    public Session getJMSSession() {
        return _session;
    }

    public String getDurableName() {
        return _duraName;
    }

    public void onMessage(Message msg) {
        String msgid = "";
        try { 
            if (_clientack) {
                synchronized(_unacked) {
                    _unacked.add(msg);
                }
            }
            msgid = msg.getJMSMessageID();
            _out.sendToClient(
                toStompFrameMessage(msg, _subid, _session,
                stompconn.getProtocolHandler()));
        } catch (Throwable t) {

            try {

            String[] eparam = {msgid, _subid, t.getMessage()};
            if (t instanceof java.nio.channels.ClosedChannelException) {
                _logger.log(Level.WARNING, _sbr.getKString(_sbr.W_UNABLE_DELIVER_MSG_TO_SUB, eparam));
                RuntimeException re = new RuntimeException(t.getMessage());
                re.initCause(t);
                throw re;
            } 

            _logger.log(Level.WARNING, _sbr.getKString(_sbr.W_UNABLE_DELIVER_MSG_TO_SUB, eparam), t);

            StompFrameMessage err = null;
            try {
                err = stompconn.getProtocolHandler().toStompErrorMessage(
                          "Subscriber["+_subid+"].onMessage", t, true);

            } catch (Throwable tt) {
                _logger.log(Level.WARNING, _sbr.getKString(_sbr.E_UNABLE_CREATE_ERROR_MSG, t.getMessage()), tt);
                RuntimeException re = new RuntimeException(t.getMessage());
                re.initCause(t);
                throw re;
            }

            try {
                 _out.sendToClient(err);
            } catch (Throwable ee) {
                if (ee instanceof java.nio.channels.ClosedChannelException) {
                    _logger.log(Level.WARNING, _sbr.getKString(_sbr.E_UNABLE_SEND_ERROR_MSG, t.getMessage(), ee.getMessage()));
                
                } else {
                    _logger.log(Level.WARNING, _sbr.getKString(_sbr.E_UNABLE_SEND_ERROR_MSG, t.getMessage(), ee.getMessage()), ee);
                }
            }
            RuntimeException re = new RuntimeException(t.getMessage());
            re.initCause(t);
            throw re;

            } finally {

            try {
            closeSubscriber();
            } catch (Exception e) {
            _logger.log(Level.FINE, "Close subscriber "+this+" failed:"+e.getMessage(), e);
            }

            }
        }
    }

    public void ack(String msgid) throws Exception {
        if (_session.getAcknowledgeMode() != Session.CLIENT_ACKNOWLEDGE) {
            throw new JMSException(_sbr.getKString(_sbr.X_NOT_CLIENT_ACK_MODE, msgid, _subid));
        }

        synchronized(_unacked) {

            Message msg = null;
            int end = _unacked.size() -1;
            boolean found = false;
            int i = 0;
            for (i = end; i >= 0; i--) { 
                msg = _unacked.get(i);
                if (msgid.equals(msg.getJMSMessageID())) {
                    try {
                        if (!_clientack_thismsg) {
                            ((com.sun.messaging.jmq.jmsclient.MessageImpl)msg).acknowledgeUpThroughThisMessage(); 
                        } else {
                            ((com.sun.messaging.jmq.jmsclient.MessageImpl)msg).acknowledgeThisMessage(); 
                            _unacked.remove(i);
                            break;
                        }
                        _ackfailureCount = 0;
                    } catch (Exception e) { 
                        _ackfailureCount++;
                        Exception ex = null;
                        if ((e instanceof JMSException) &&
                            (_session instanceof com.sun.messaging.jmq.jmsclient.SessionImpl) &&
                            ((com.sun.messaging.jmq.jmsclient.SessionImpl)_session)._appCheckRemoteException((JMSException)e)) {
                            ex = new StompUnrecoverableAckException(
                            "An unrecoverable ACK failure has occurred in subscriber "+this, e);
                            throw ex;
                        }
                        if (_ackfailureCount > MAX_CONSECUTIVE_ACK_FAILURES) { 
                            ex = new StompUnrecoverableAckException(
                            "Maximum consecutive ACK failures "+MAX_CONSECUTIVE_ACK_FAILURES+" has occurred in subscriber "+this, e);
                            throw ex;
                        }
                        throw e;
                    }
                    found = true;
                    break;
                } 
            }
            if (found && !_clientack_thismsg) {
                for (int j = 0; j <= i; j++) { 
                    _unacked.remove(0); 
                }
                return;
            }
        }

        throw new JMSException(_sbr.getKString(_sbr.X_ACK_MSG_NOT_FOUND_IN_SUB, msgid, _subid));
    }

    protected void closeSubscriber() throws Exception {
        if (_subscriber != null) _subscriber.close();
    }

    public void close() throws Exception {
        try {
            _subscriber.close();
        } catch (Exception e) {
        } finally {
            try {
            _session.close();
            } finally {
            synchronized(_unacked) {
             _unacked.clear();
            }
            }
        }
    }

    protected static StompFrameMessage toStompFrameMessage(
        Message jmsmsg, final String subid, Session ss,
        final StompProtocolHandlerImpl sph)
        throws Exception {

        MessageTransformer<Message, Message> mt = sph.getMessageTransformer();
        if (mt != null) {
            mt.init(ss, Bridge.STOMP_TYPE);
            Message oldmsg = jmsmsg;
            jmsmsg = mt.transform(jmsmsg, true, null, 
                     MessageTransformer.SUN_MQ, MessageTransformer.STOMP, null);
            if (jmsmsg == null) { 
                throw new JMSException("null returned from "+mt.getClass().getName()+
                " transform() method for JMS message "+oldmsg.toString()+" in subscription "+subid);
            }
        }
        final Message msg = jmsmsg;
        final boolean needAck = (ss.getAcknowledgeMode() != Session.AUTO_ACKNOWLEDGE);
        return sph.toStompFrameMessage(new StompMessage() {
            public String getSubscriptionID() throws Exception {
                return subid;
            }
            public String getDestination() throws Exception {
                Destination jmsdest = msg.getJMSDestination();
                return sph.toStompFrameDestination(
                    new StompDestinationImpl(jmsdest), false);
            }
            public String getReplyTo() throws Exception {
                Destination jmsdest = msg.getJMSReplyTo();
                if (jmsdest == null) {
                    return null;
                }
                return sph.toStompFrameDestination(
                    new StompDestinationImpl(jmsdest), true);
            }
            public String getJMSMessageID() throws Exception {
                return msg.getJMSMessageID();
            }
            public String getJMSCorrelationID() throws Exception { 
                return msg.getJMSCorrelationID(); 
            }
            public String getJMSExpiration() throws Exception {
                return String.valueOf(msg.getJMSExpiration());
            }
            public String getJMSRedelivered() throws Exception {
                return String.valueOf(msg.getJMSRedelivered());
            }
            public String getJMSPriority() throws Exception {
                return String.valueOf(msg.getJMSPriority());
            }
            public String getJMSTimestamp() throws Exception {
                return String.valueOf(msg.getJMSTimestamp());
            }
            public String getJMSType() throws Exception {
                return msg.getJMSType(); 
            }
            public Enumeration getPropertyNames() throws Exception {
                return msg.getPropertyNames();
            }
            public String getProperty(String name) throws Exception {
                return (msg.getObjectProperty(name)).toString();
            }
            public boolean isTextMessage() throws Exception {
                return (msg instanceof TextMessage);
            }
            public boolean isBytesMessage() throws Exception {
                return (msg instanceof BytesMessage); 
            }
            public String getText() throws Exception {
                return ((TextMessage)msg).getText();
            }
            public byte[] getBytes() throws Exception {
                BytesMessage m = (BytesMessage)msg;
                byte[] data = new byte[(int)m.getBodyLength()];
                m.readBytes(data);
                return data;
            }
            public void setText(StompFrameMessage message) throws Exception {
                throw new RuntimeException("Unexpected call: setText()");
            }
            public void setBytes(StompFrameMessage message) throws Exception {
                throw new RuntimeException("Unexpected call: setBytes()");
            }
            public void setDestination(String stompdest) throws Exception {
                throw new RuntimeException("Unexpected call: setDestination()");
            }
            public void setPersistent(String stompdest) throws Exception {
                throw new RuntimeException("Unexpected call: setPersistent()");
            }
            public void setReplyTo(String replyto) throws Exception {
                throw new RuntimeException("Unexpected call: setReplyTo()");
            }
            public void setJMSCorrelationID(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSCorrelationID()");
            }
            public void setJMSExpiration(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSExpiration()");
            }
            public void setJMSPriority(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSPriority()");
            }
            public void setJMSType(String value) throws Exception {
                throw new RuntimeException("Unexpected call: setJMSType()");
            }
            public void setProperty(String name, String value) throws Exception {
                throw new RuntimeException("Unexpected call: setProperty()");
            }
        }, needAck);
    }

    @Override
    public StompDestination createStompDestination(String name, boolean isQueue)
    throws Exception {
	if (isQueue) {
            return new StompDestinationImpl(
                _session.createQueue(name));
	}
        return new StompDestinationImpl(
           _session.createTopic(name));
    }

    @Override
    public StompDestination createTempStompDestination(boolean isQueue)
    throws Exception {
        if (isQueue) {
            return new StompDestinationImpl(
               _session.createTemporaryQueue());
        }
        return new StompDestinationImpl(
            _session.createTemporaryTopic());
    }
}
