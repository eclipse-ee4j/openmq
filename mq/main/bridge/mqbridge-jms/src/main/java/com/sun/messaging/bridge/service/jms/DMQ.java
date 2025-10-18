/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.bridge.service.jms;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.MessageProducer;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import jakarta.jms.Connection;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;
import jakarta.jms.ExceptionListener;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.service.jms.xml.JMSBridgeXMLConstant;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 * a JMS bridge DMQ
 *
 * @author amyk
 *
 */
public class DMQ {

    public enum DMQReason {
        MESSAGE_EXPIRED, TRANSFORMER_FAILURE, FIRST_TRANSFORMER_NOTRANSFER, FIRST_TRANSFORMER_BRANCHTO, FIRST_TRANSFORMER_AS_SOURCE_CHANGE, SEND_FAILURE,
        COMMIT_FAILURE, ACK_FAILURE
    }

    public enum DMQProperty {

        JMS_SUN_JMSBRIDGE_SOURCE_MESSAGEID, JMS_SUN_JMSBRIDGE_SOURCE_TIMESTAMP, JMS_SUN_JMSBRIDGE_SOURCE_CORRELATIONID, JMS_SUN_JMSBRIDGE_SOURCE_JMSTYPE,
        JMS_SUN_JMSBRIDGE_SOURCE_DESTINATION, JMS_SUN_JMSBRIDGE_TARGET_DESTINATION, JMS_SUN_JMSBRIDGE_TARGET_CURRENT_DESTINATION,
        JMS_SUN_JMSBRIDGE_SOURCE_PROVIDER, JMS_SUN_JMSBRIDGE_TARGET_PROVIDER, JMS_SUN_JMSBRIDGE_DMQ_REASON, JMS_SUN_JMSBRIDGE_DMQ_EXCEPTION,
        JMS_SUN_JMSBRIDGE_DMQ_TIMESTAMP, JMS_SUN_JMSBRIDGE_DMQ_BODY_TRUNCATED,
    }

    private enum DMQState {
        UNINITIALIZED, STARTING, STARTED, STOPPING, STOPPED
    }

    private Logger _logger = null;

    private Refable _cf = null;
    private Object _dest = null;

    private MessageProducer _producer = null;
    private Session _session = null;
    private Connection _conn = null;

    private Properties _dmqAttrs = null;
    private Properties _dmqProps = null;
    private JMSBridge _parent = null;
    private boolean _stayConnected = true;
    private long _timeToLive = 0;

    private int _maxSendAttempts = 1;
    private long _sendInterval = 5 * 1000;

    private DMQState _state = DMQState.UNINITIALIZED;
    private String _name = null;
    private boolean _connException = false;
    private String _providerName = null;
    private EventNotifier _notifier = null;

    private MessageTransformer<Message, Message> _msgTransformer = null;

    private FaultInjection _fi = FaultInjection.getInjection();
    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public synchronized void init(Properties dmqAttrs, Properties dmqProps, JMSBridge parent) throws Exception {
        _dmqAttrs = dmqAttrs;
        _dmqProps = dmqProps;
        _parent = parent;
        _notifier = parent._notifier;
        if (_dest == null || _cf == null) {
            throw new IllegalStateException("DMQ information unknown !");
        }
        if (_logger == null) {
            throw new IllegalStateException("No logger set for dmq " + this);
        }

        if (_cf instanceof XAConnectionFactory) {
            throw new IllegalArgumentException(
                    JMSBridge.getJMSBridgeResources().getKString(JMSBridge.getJMSBridgeResources().X_DMQ_NOT_SUPPORT, "XAConnectionFactory"));
        }

        Integer.parseInt(_dmqAttrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTS, JMSBridgeXMLConstant.CF.CONNECTATTEMPTS_DEFAULT));
        Long.parseLong(_dmqAttrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL, JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT));

        String val = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.STAYCONNECTED, JMSBridgeXMLConstant.DMQ.STAYCONNECTED_DEFAULT);
        _stayConnected = Boolean.parseBoolean(val);

        val = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.TIMETOLIVE, JMSBridgeXMLConstant.DMQ.TIMETOLIVE_DEFAULT);
        _timeToLive = Long.parseLong(val);

        val = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.SENDATTEMPTS, JMSBridgeXMLConstant.DMQ.SENDATTEMPTS_DEFAULT);
        _maxSendAttempts = Integer.parseInt(val);
        if (_maxSendAttempts <= 0) {
            _maxSendAttempts = 1;
        }

        val = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.SENDATTEMPTINTERVAL, JMSBridgeXMLConstant.DMQ.SENDATTEMPTINTERVAL_DEFAULT);
        _sendInterval = Long.parseLong(val) * 1000;
        if (_sendInterval < 0) {
            _sendInterval = 0;
        }

        String cn = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.MTFCLASS);
        if (cn != null) {
            _msgTransformer = (MessageTransformer<Message, Message>) Class.forName(cn).getDeclaredConstructor().newInstance();
        }

        _state = DMQState.STOPPED;
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(_dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.ENABLED, JMSBridgeXMLConstant.DMQ.ENABLED_DEFAULT));
    }

    public synchronized void start(boolean doReconnect) throws Exception {

        if (_state == DMQState.UNINITIALIZED) {
            throw new IllegalStateException(JMSBridge.getJMSBridgeResources().getKString(JMSBridge.getJMSBridgeResources().X_DMQ_NOT_INITED, this.toString()));
        }
        if (_state == DMQState.STARTED) {
            _logger.log(Level.INFO, JMSBridge.getJMSBridgeResources().getString(JMSBridgeResources.I_DMQ_ALREADY_STARTED, this.toString()));
            return;
        }
        _state = DMQState.STARTING;

        try {
            if (_stayConnected) {
                initJMS(doReconnect);
            }
            _state = DMQState.STARTED;

        } catch (Exception e) {
            _logger.log(Level.SEVERE, JMSBridge.getJMSBridgeResources().getKString(JMSBridgeResources.E_UNABLE_START_DMQ, this.toString(), e.getMessage()), e);
            try {
                stop();
            } catch (Throwable t) { //NOPMD
                _logger.log(Level.WARNING,
                        JMSBridge.getJMSBridgeResources().getKString(JMSBridgeResources.W_UNABLE_STOP_DMQ_AFTER_FAILED_START, this.toString()), t);
            }
            throw e;
        }
    }

    public void stop() throws Throwable {

        _notifier.notifyEvent(EventListener.EventType.DMQ_STOP, this);

        synchronized (this) {
            _state = DMQState.STOPPING;
            closeJMS();
            _state = DMQState.STOPPED;
        }

    }

    private void initJMS() throws Exception {
        initJMS(true);
    }

    private void initJMS(boolean doReconnect) throws Exception {
        _connException = false;

        String val = _dmqAttrs.getProperty(JMSBridgeXMLConstant.DMQ.CLIENTID);
        if (_stayConnected || val != null) {

            String[] param = { "DMQ", (val == null ? "" : "[ClientID=" + val + "]"), this.toString() };
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_DEDICATED_CONN, param));

            EventListener l = new EventListener(this);
            try {
                _notifier.addEventListener(EventListener.EventType.DMQ_STOP, l);
                _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, l);
                _conn = JMSBridge.openConnection(_cf, _parent.getCFAttributes(_cf), "DMQ", this, l, _logger, doReconnect);
            } finally {
                _notifier.removeEventListener(l);
            }
            if (val != null) {
                _conn.setClientID(val);
            }

        } else {
            _conn = _parent.obtainConnection(_cf, "DMQ", this, doReconnect);
        }

        _conn.setExceptionListener(new ExceptionListener() {

            @Override
            public void onException(JMSException exception) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_ON_CONN_EXCEPTION, this.toString()), exception);
                _connException = true;
                if (_conn instanceof PooledConnection) {
                    ((PooledConnection) _conn).invalid();
                } else if (_conn instanceof SharedConnection) {
                    ((SharedConnection) _conn).invalid();
                }
            }
        });

        try {
            ConnectionMetaData md = _conn.getMetaData();
            _providerName = md.getJMSProviderName();
        } catch (Exception e) {
            _providerName = null;
            _logger.log(Level.WARNING,
                    () -> String.format("Unable to get JMSProvider from conn %s in dmq %s: %s",
                            _conn,
                            this,
                            e.getMessage()));
        }

        _session = _conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        if (_dest instanceof Destination) {
            _producer = _session.createProducer((Destination) _dest);
        } else if (_dest instanceof AutoDestination) {
            AutoDestination ad = (AutoDestination) _dest;
            if (ad.isQueue()) {
                _producer = _session.createProducer(_session.createQueue(ad.getName()));
            } else {
                _producer = _session.createProducer(_session.createTopic(ad.getName()));
            }
        } else {
            throw new IllegalArgumentException("Unknown destination type: " + _dest.getClass().getName() + " in dmq " + this);
        }
    }

    private synchronized void closeJMS() {
        _connException = false;
        if (_conn == null) {
            return;
        }

        if (_conn instanceof SharedConnection || _conn instanceof PooledConnection) {
            try {
                _parent.returnConnection(_conn, _cf);
            } catch (Throwable t) { //NOPMD
                logWarning(_jbr.getKString(_jbr.W_UNABLE_RETURN_CONN, _conn, this.toString()), t);
            }
            return;
        }

        try {
            _logger.log(Level.INFO,
                    _jbr.getString(_jbr.I_CLOSE_DMQ_CONNECTION, _conn.getClass().getName() + '@' + Integer.toHexString(_conn.hashCode()), this.toString()));
            _conn.close();
        } catch (Throwable t) { //NOPMD
            _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_UNABLE_CLOSE_CONN, _conn, this.toString()), t);
        }
    }

    private void logWarning(String msg, Throwable t) {
        if (_state == DMQState.STOPPING || _state == DMQState.STOPPED) {
            _logger.log(Level.WARNING, () -> String.format("%s: %s", msg, t.getMessage()));
        } else {
            _logger.log(Level.WARNING, msg, t);
        }
    }

    public synchronized void sendMessage(Message m, String mid, DMQReason reason, Throwable ex, Link l) throws Exception {
        if (_state == DMQState.STOPPING || _state == DMQState.STOPPED) {
            throw new JMSException(_jbr.getKString(_jbr.X_STOPPED, this.toString()));
        }

        if (_connException) {
            closeJMS();
            initJMS();
        } else if (!_stayConnected) {
            initJMS();
        }

        ObjectMessage om = _session.createObjectMessage();
        if (mid != null) {
            try {
                om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_MESSAGEID.toString(), mid);
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting JMSMessageID to DMQ message from message " + m + " for link " + l, e);
            }
        }
        long timestamp = 0;
        try {
            timestamp = m.getJMSTimestamp();
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in getting JMSTimestamp from message " + m + " for link " + l, e);
        }
        if (timestamp != 0) {
            try {
                om.setLongProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_TIMESTAMP.toString(), timestamp);
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting source timestamp to DMQ message from message " + m + " for link " + l, e);
            }
        }
        String val = null;
        try {
            val = m.getJMSCorrelationID();
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in getting JMSCorrelationID from message " + m + " for link " + l, e);
        }
        if (val != null) {
            try {
                om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_CORRELATIONID.toString(), val);
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting source correlation id to DMQ message from message " + m + " for link " + l, e);
            }
        }
        val = null;
        try {
            val = m.getJMSType();
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in getting JMSType from message " + m + " for " + l, e);
        }
        if (val != null) {
            try {
                om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_JMSTYPE.toString(), val);
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting source JMS type to DMQ message from message " + m + " for link " + l, e);
            }
        }
        Enumeration en = null;
        try {
            en = m.getPropertyNames();
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in getting PropertyNames from message " + m + " in " + l, e);
        }
        String key = null;
        Object value = null;
        while (en != null && en.hasMoreElements()) {
            key = (String) en.nextElement();
            value = m.getObjectProperty(key);
            try {
                om.setObjectProperty(key, value);
            } catch (Exception e) {
                String[] eparam = { key + "=" + value, "" + mid, l.toString() };
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.X_EXCEPTION_SET_DMQ_PROPERTY, eparam), e);
            }
        }

        try {
            om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_DESTINATION.toString(), l.getSourceDestinationName());
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in setting source destination to DMQ message for message " + m + " in " + l, e);
        }

        try {
            om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_TARGET_DESTINATION.toString(), l.getTargetDestinationName());
        } catch (Exception e) {
            _logger.log(Level.WARNING,
                    "Exception in setting target destination " + l.getTargetDestinationName() + " to DMQ message for message " + m + " in " + l, e);
        }

        if (l.getTargetCurrentDestinationName() != null) {
            try {
                om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_TARGET_CURRENT_DESTINATION.toString(), l.getTargetCurrentDestinationName());
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting target current destination " + l.getTargetCurrentDestinationName()
                        + " to DMQ message for message " + m + " in " + l, e);
            }
        }

        try {
            om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_SOURCE_PROVIDER.toString(), l.getSourceProviderName());
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in setting source provider to DMQ message for message " + m + " in " + l, e);
        }

        try {
            om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_TARGET_PROVIDER.toString(), l.getTargetProviderName());
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in setting target provider to DMQ message for message " + m + " in " + l, e);
        }

        try {
            om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_DMQ_REASON.toString(), reason.toString());
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in setting dmq reason " + reason + " to DMQ message for message " + m + " in " + l, e);
        }

        if (ex != null && ex.getMessage() != null) {
            try {
                om.setStringProperty(DMQProperty.JMS_SUN_JMSBRIDGE_DMQ_EXCEPTION.toString(), ex.getMessage());
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Exception in setting dmq reason " + reason + " to DMQ message for message " + m + " in " + l, e);
            }
        }

        try {
            om.setLongProperty(DMQProperty.JMS_SUN_JMSBRIDGE_DMQ_TIMESTAMP.toString(), System.currentTimeMillis());
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in setting dmq timestamp to DMQ message for message " + m + " in " + l, e);
        }
        int deliveryMode = DeliveryMode.PERSISTENT;
        try {
            deliveryMode = m.getJMSDeliveryMode();
        } catch (Exception e) {
            deliveryMode = DeliveryMode.PERSISTENT;
            _logger.log(Level.WARNING, "Exception in getting JMSDeliveryMode: " + e.getMessage() + " for message " + m + " in " + l, e);
        }

        int priority = 4;
        try {
            priority = m.getJMSPriority();
        } catch (Exception e) {
            priority = 4;
            _logger.log(Level.WARNING, "Exception in getting JMSPriority: " + e.getMessage() + " for message " + m + " in " + l, e);
        }

        boolean sent = false;
        int attempts = 0;
        EventListener el = new EventListener(this);
        _notifier.addEventListener(EventListener.EventType.DMQ_STOP, el);
        _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, el);
        Message msgToSend = m;
        try {
            do {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException(_jbr.getKString(_jbr.X_DMQ_SENDRETRY_INTERRUPTED, mid, this.toString()));
                }
                if (attempts > 0 && _sendInterval > 0) {
                    Thread.sleep(_sendInterval);
                }
                try {
                    if (_connException) {
                        closeJMS();
                        initJMS();
                    } else if (!_stayConnected) {
                        initJMS();
                    }
                    if (attempts == 0) {
                        if (_msgTransformer != null) {
                            _msgTransformer.init(_session, Bridge.JMS_TYPE);
                            try {

                                msgToSend = _msgTransformer.transform(m, true, null, l.getSourceProviderName(), l.getTargetProviderName(), _dmqProps);
                                if (_fi.FAULT_INJECTION) {
                                    Map p = new HashMap();
                                    p.put(FaultInjection.DMQ_NAME_PROP, _name);
                                    _fi.setLogger(_logger);
                                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_DMQ_TRANSFORM_2, p, "jakarta.jms.JMSException", true);
                                }

                            } catch (Exception e) {
                                msgToSend = m;
                                String[] eparam = { "MessageTransformer", "" + mid, l.toString() };
                                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_EXCEPTION_DMQ_MSG, eparam), e);
                            }
                        }
                        boolean truncate = true;
                        if (msgToSend instanceof Serializable) {
                            truncate = false;
                            try {
                                om.setObject((Serializable) msgToSend);
                            } catch (Exception e) {
                                truncate = true;
                                String[] eparam = { "ObjectMessage.setObject()", "" + mid, l.toString() };
                                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_EXCEPTION_DMQ_MSG, eparam), e);
                            }
                        }
                        if (truncate) {
                            try {
                                om.setBooleanProperty(DMQProperty.JMS_SUN_JMSBRIDGE_DMQ_BODY_TRUNCATED.toString(), Boolean.TRUE);
                            } catch (Exception e) {
                                _logger.log(Level.WARNING, "Exception in setting DMQ body-truncated property for DMQ message " + m + "in " + l, e);
                            }
                            try {
                                om.setObject(msgToSend.toString());
                            } catch (Exception e) {
                                String[] eparam = { "ObjectMessage.setObject()", "" + mid, l.toString() };
                                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_EXCEPTION_TRUNCATE_DMQ_MSG, eparam), e);
                            }
                        }
                    }

                    if (_fi.FAULT_INJECTION) {
                        Map p = new HashMap();
                        p.put(FaultInjection.DMQ_NAME_PROP, _name);
                        _fi.setLogger(_logger);
                        _fi.checkFaultAndThrowException(FaultInjection.FAULT_DMQ_SEND_1, p, "jakarta.jms.JMSException", true);
                    }
                    _producer.send(om, deliveryMode, priority, _timeToLive);
                    sent = true;

                } catch (Throwable t) { //NOPMD
                    attempts++;
                    _connException = true;
                    String[] eparam = { "" + mid, this.toString(), l.toString(), attempts + "(" + _sendInterval + ")" };
                    _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_FAIL_SEND_ATTEMPTS, eparam), t);
                }
            } while (!sent && attempts <= _maxSendAttempts && !el.hasEventOccurred());
        } finally {
            _notifier.removeEventListener(el);
            if (!_stayConnected) {
                closeJMS();
            }
        }
    }

    @FunctionalInterface
    interface HeaderFunction {
        Object getFrom(Message m) throws Exception;
    }

    static String format(String headerName, HeaderFunction header, Message msg, String mid, Link l) {
        try {
            return "\t" + headerName + "=" + header.getFrom(msg) + '\n';
        } catch (Throwable t) { //NOPMD
            return "\tUnable to get " + headerName + " header from message " + mid + " for " + l + ": " + t.getMessage() + '\n';
        }
    }

    public static void logMessage(Message msg, String mid, Link l, Logger logger) {

        StringBuilder buf = new StringBuilder();
        try {

            buf.append("Logging message going to DMQ for ").append(l);
            buf.append('\n');
            buf.append("\tJMS Headers:");
            buf.append('\n');
            buf.append(format("JMSMessageID", Message::getJMSMessageID, msg, mid, l));
            buf.append(format("JMSDestination", Message::getJMSDestination, msg, mid, l));
            buf.append(format("JMSTimestamp", Message::getJMSTimestamp, msg, mid, l));
            buf.append(format("JMSExpiration", Message::getJMSExpiration, msg, mid, l));
            buf.append(format("JMSDeliveryMode", Message::getJMSDeliveryMode, msg, mid, l));
            buf.append(format("JMSCorrelationID", Message::getJMSCorrelationID, msg, mid, l));
            buf.append(format("JMSPriority", Message::getJMSPriority, msg, mid, l));
            buf.append(format("JMSRedelivered", Message::getJMSRedelivered, msg, mid, l));
            buf.append(format("JMSReplyTo", Message::getJMSReplyTo, msg, mid, l));
            buf.append(format("JMSType", Message::getJMSType, msg, mid, l));
            buf.append('\n');
            buf.append("\tJMS Properties:");
            buf.append('\n');
            Enumeration en = null;
            try {
                en = msg.getPropertyNames();
            } catch (Throwable t) { //NOPMD
                buf.append("Unable to get PropertyNames from message " + mid + " for " + l + ": " + t.getMessage());
            }
            buf.append('\n');
            String key = null;
            while (en != null && en.hasMoreElements()) {
                key = (String) en.nextElement();
                try {
                    buf.append("\t" + key + "=" + msg.getObjectProperty(key));
                } catch (Throwable t) { //NOPMD
                    buf.append("Unable to get property " + key + " value from message " + mid + " for " + l + ": " + t.getMessage());
                }
                buf.append('\n');
            }
            buf.append('\n');
            buf.append("\tMessage.toString:");
            buf.append('\n');
            try {
                buf.append("\ttoString=" + msg);
            } catch (Throwable t) { //NOPMD
                buf.append("\tUnable to get Message.toString() from message " + mid + " for " + l + ": " + t.getMessage());
            }

        } finally {
            logger.log(Level.INFO, buf.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("dmq(").append(getName()).append(")[");
        sb.append(_cf.getRef()).append("::");
        sb.append(getDestinationName());
        sb.append(']');
        return sb.toString();
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getProviderName() {
        String pn = _providerName;
        if (pn != null) {
            return pn;
        }

        return _cf.getRefed().getClass().getName();
    }

    public String getDestinationName() {
        try {

            if (_dest instanceof Queue) {
                return "queue:" + ((Queue) (_dest)).getQueueName();
            } else if (_dest instanceof Topic) {
                return "topic:" + ((Topic) (_dest)).getTopicName();
            } else {
                return _dest.toString();
            }

        } catch (Exception e) {
            _logger.log(Level.WARNING, "Exception in get destination name for dmq " + this, e);
            return _dest.toString();
        }
    }

    public void setConnectionFactory(Refable cf) {
        _cf = cf;
    }

    public void setDestination(Object dest) {
        _dest = dest;
    }

    public Object getDestination() {
        return _dest;
    }

    public void setLogger(Logger l) {
        _logger = l;
    }
}
