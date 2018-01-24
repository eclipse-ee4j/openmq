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

package com.sun.messaging.jms.ra;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSSecurityException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsclient.ExceptionHandler;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jms.IllegalStateException;
/**
 *  DirectSession encapsulates JMS Session behavior for MQ DIRECT mode operation
 */
public class DirectSession
        implements javax.jms.Session,
        javax.jms.QueueSession,
        javax.jms.TopicSession {

    /**
     *  The JMSService for this DirectSession
     */
    protected JMSService jmsservice;

    /**
     *  The parent DirectConnection that created this DirectSession
     */
    protected DirectConnection dc;

    /**
     *  The connectionId of the parent DirectConnectio
     */
    protected long connectionId;

    /**
     *  The sessionId for this DirectSession
     */
    protected long sessionId;

    /**
     *
     */
    protected SessionAckMode ackMode;

    /**
     *  Holds the closed state of this DirectSession
     */
    protected boolean isClosed;
    protected boolean isClosing;

    /**
     *  Holds the stopped state of this DirectSession
     */
    protected boolean isStopped;

    /**
     *  Indicates whether the DirectSession is servicing an Async Consumer.
     *  When a session has an async consumer on it, it can no longer be used
     *  by any thread other than the thread that delivers messages to that
     *  asynch consumer.
     */
    protected boolean isAsync;
    /** inDeliver is true as long as the session is delivering a message */
    protected boolean inDeliver;
    /** Holds the ThreadId of the server Thread used to deliver messages */
    protected long deliverThreadId = 0L;
    protected boolean enableThreadCheck = false;

    /** Indicates whether this DirectSesion is being used for an MDB */
    protected boolean isMDBSession = false;

    /**
     *  Holds the xaTransacted state of this DirectSession
     */
    private boolean isXATransacted;

    /** Holds the transactionId of this DirectSession if it is transcated */
    protected long transactionId = 0L;
    protected boolean ackOnFetch = false;

    /**
     *  DirectConsumers made by this DirectSession
     */
    private transient Vector <DirectConsumer> consumers = null;

    /**
     *  Asynch consumers in this DirectSession
     */
    private transient Vector <DirectConsumer> asyncConsumers = null;

    /**
     *  DirectProducers made by this DirectSession
     */
    private transient Vector <DirectProducer> producers = null;

    /**
     *  DirectBrowsers made by this DirectSession
     */
    private transient Vector <DirectQueueBrowser> browsers = null;

    /**
     *  Unacknowledged messages' SysMessageID objects kept in order for recover
     */
    private transient ArrayList <SysMessageID> unackedMessageIDs = null;

    /**
     *  Unacknowledged messages'consumerId longs kept in order for recover
     */
    private transient ArrayList <Long> unackedConsumerIDs = null;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectSession";
    private static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSSession =
            "javax.jms.Session.mqjmsra";
    protected static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final Logger _loggerJS =
            Logger.getLogger(_lgrNameJMSSession);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_DS";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** For optimized logging */
    protected static final int _logLevel;
    protected static final boolean _logFINE;


    static {                                                                        
//        _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
//        _loggerJS = Logger.getLogger(_lgrNameJMSSession);

        int tmplevel = java.util.logging.Level.INFO.intValue();
        boolean tmplogfine = false;
        java.util.logging.Level _level = _loggerJS.getLevel();
        if (_level != null) {
            tmplevel = _level.intValue();
            if (tmplevel <= java.util.logging.Level.FINE.intValue()){
                tmplogfine = true;
            }
        }
        _logLevel = tmplevel;
        _logFINE = tmplogfine;
    }

    /** Creates a new instance of DirectSession */
    public DirectSession(DirectConnection dc,
        JMSService jmsservice, long sessionId, SessionAckMode ackMode)
        throws JMSException {

        Object params[] = new Object[4];
        params[0] = dc;
        params[1] = jmsservice;
        params[2] = sessionId;
        params[3] = ackMode;
        _loggerOC.entering(_className, "constructor()", params);
        this.dc = dc;
        this.jmsservice = jmsservice;
        this.connectionId = dc.getConnectionId();
        this.sessionId = sessionId;
        this.ackMode = ackMode;
        producers = new Vector <DirectProducer> ();
        consumers = new Vector <DirectConsumer> ();
        asyncConsumers = new Vector <DirectConsumer> ();
        browsers = new Vector <DirectQueueBrowser> ();
        unackedMessageIDs = new ArrayList <SysMessageID> ();
        unackedConsumerIDs = new ArrayList <Long> ();
        
        _initSession();
    }

    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.Session
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Close the JMS Session
     */
    public synchronized void close()
    throws JMSException {
        _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+"close()");
        //harmless if already closed
        if (isClosed){
            return;
        } else {
            dc.removeSession(this);
            this._close();
        }
    }

    /**
     *  Commit all messages acknowledged in this JMS Sesssion
     */
    public void commit()
    throws JMSException {
        String methodName = "commit()";
        _loggerJS.fine(_lgrMID_INF+
               "sessionId="+sessionId+":"+methodName);
        //XXX:handle commit inside deliver() includes the current message
        this._checkIfClosed(methodName);
        this._checkTransactedState(methodName, true); //allow only if transacted
        //JMSServiceReply reply = null;
        try {
            //reply = jmsservice.commitTransaction(
            jmsservice.commitTransaction(
                    this.connectionId, this.transactionId, null, 0);
        } catch (JMSServiceException jmsse){
            _loggerJS.warning(_lgrMID_WRN+
                    "sessionId="+sessionId+":"+methodName+
                    ":JMSServiceException="+
                    jmsse.getMessage());
        }
        this._startTransaction(methodName);
    }

    /**
     *  Create a QueueBrowser to peek at the messages on the specified queue
     */
    public QueueBrowser createBrowser(Queue queue)
    throws JMSException {
        return (QueueBrowser)this._createAndAddBrowser(
                "createBrowser(Queue)",
                queue, null);
    }

    /**
     *  Create a QueueBrowser to peek at the messages on the specified queue
     *  using a message selector
     */
    public QueueBrowser createBrowser(Queue queue, String selector)
    throws JMSException {
        return (QueueBrowser)this._createAndAddBrowser(
                "createBrowser(Queue, Selector)",
                queue, selector);
    }

    /**
     *  Create a BytesMessage
     */
    public BytesMessage createBytesMessage()
    throws JMSException {
        String methodName = "createBytesMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+
                    "sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (BytesMessage) new DirectBytesPacket(this);

    }

    /**
     *  Create a MessageConsumer for the specified Destination
     */
    public MessageConsumer createConsumer(Destination destination)
    throws JMSException {
        return (MessageConsumer)this._createAndAddConsumer(
            "createConsumer(Destination)", destination, null, false);
    }

    /**
     *  Create a MessageConsumer for the specified Destination using a selector
     */
    public MessageConsumer createConsumer(Destination destination, String selector)
    throws JMSException {
        return (MessageConsumer)this._createAndAddConsumer(
            "createConsumer(Destination, selector)", 
            destination, selector, false);
    }

    /**
     *  Create a MessageConsumer for the specified Destination using a selector
     *  and specifying whether messages published by its own connection should
     *  be delivered to it.
     */
    public MessageConsumer createConsumer(Destination destination,
        String selector, boolean noLocal)
        throws JMSException {
        return (MessageConsumer)this._createAndAddConsumer(
                "createConsumer(Destination, selector, noLocal)",
                destination, selector, noLocal);
    }

    /**
     *  Create a TopicSubscriber for the specified Topic with the specified
     *  subscription name
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, String name)
    throws JMSException {
        return (TopicSubscriber)this._createAndAddConsumer(
                "createDurableSubscriber(Topic, name)",
                topic, null, name, true, false, false, false);
    }

    /**
     *  Create a TopicSubscriber for the specified Topic with the specified
     *  subscription name and selector and specifying whether messages published
     *  by its own connection should be delivered to it.
     */
    public TopicSubscriber createDurableSubscriber(Topic topic,
        String name, String selector, boolean noLocal)
        throws JMSException {
        return (TopicSubscriber)this._createAndAddConsumer(
                "createDurableSubscriber(Topic, name, selector, noLocal)",
                topic, selector, name, true, false, false, noLocal);
    }
    
    /**
     *  Create a TopicSubscriber for the specified Topic with the specified
     *  subscription name
     */
    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name)
    throws JMSException {
        return createDurableSubscriber(topic, name);
    }

    /**
     *  Create a TopicSubscriber for the specified Topic with the specified
     *  subscription name and selector and specifying whether messages published
     *  by its own connection should be delivered to it.
     */
    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name,
        String messageSelector, boolean noLocal) throws JMSException {
        return createDurableSubscriber(topic, name, messageSelector, noLocal);
    }   
    
    
    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
        String sharedSubscriptionName) throws JMSException {
        return createSharedConsumer(topic, sharedSubscriptionName, null);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
        String sharedSubscriptionName, String messageSelector) 
        throws JMSException {
        boolean noLocal=false;
        return (TopicSubscriber)this._createAndAddConsumer(
            "createSharedConsumer(Topic, sharedSubscriptionName, messageSelector)",
            topic, messageSelector, sharedSubscriptionName, false, true, true, noLocal);
    }    

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name)
    throws JMSException {
	return createSharedDurableConsumer(topic, name, null);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name,
	String messageSelector)
	throws JMSException {
        boolean noLocal=false;
        return (TopicSubscriber)this._createAndAddConsumer(
            "createSharedDurableConsumer(Topic, name, messageSelector)",
            topic, messageSelector, name, true, true, true, noLocal);
    }

    /**
     *  Create a MapMessage
     */
    public MapMessage createMapMessage()
    throws JMSException {
        String methodName = "createMapMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+
                    "sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (MapMessage) new DirectMapPacket(this);
    }

    /**
     *  Create a Message
     */
    public Message createMessage()
    throws JMSException {
        String methodName = "createMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+
                    "sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (Message) new DirectPacket(this);
    }

    /**
     *  Create a ObjectMessage
     */
    public ObjectMessage createObjectMessage()
    throws JMSException {
        String methodName = "createObjectMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (ObjectMessage) new
                DirectObjectPacket(this, null);
    }

    /**
     *  Create a ObjectMessage
     */
    public ObjectMessage createObjectMessage(Serializable object)
    throws JMSException {
        String methodName = "createObjectMessage(object)";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (ObjectMessage) new
                DirectObjectPacket(this, object);
    }

    /**
     *  Create a MessageProducer for the specified Destination
     */
    public MessageProducer createProducer(Destination destination)
    throws JMSException {
        return (MessageProducer)this._createAndAddProducer("createProducer()",
                destination);
        /*
        String methodName = "createProducer()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" + sessionId +
                    ":"+methodName+"destination="+destination);
        }
        this._checkIfClosed(methodName);
        if (destination !=null) {
            //Spcified destination case
            String _name = null;
            com.sun.messaging.jmq.jmsservice.Destination.Type _type =
                    com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
            com.sun.messaging.jmq.jmsservice.Destination.Life _life =
                    com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD;

            if (destination instanceof javax.jms.Queue){
                _name = ((Queue)destination).getQueueName();
                _type = com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
                if (destination instanceof javax.jms.TemporaryQueue){
                    _life =
                        com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
                }
            }
            if (destination instanceof javax.jms.Topic){
                _name = ((Topic)destination).getTopicName();
                _type = com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC;
                if (destination instanceof javax.jms.TemporaryTopic){
                    _life =
                        com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
                }
            }
            com.sun.messaging.jmq.jmsservice.Destination _destination =
                    new com.sun.messaging.jmq.jmsservice.Destination(_name,
                    _type, _life);
            //XXX:tharakan:Need to Verify Destination first.
            //dc._verifyDestination(_destination);
            dc._createDestination(_destination);
            long producerId = _createProducerId(_destination);
            DirectProducer dp = new DirectProducer(this, jmsservice,
                    producerId, destination, _destination);
            this.addProducer(dp);
            return (MessageProducer)dp;
        } else {
            DirectProducer dp = new DirectProducer(this, jmsservice);
            this.addProducer(dp);
            return (MessageProducer)dp;
        }
        */
    }

    /**
     *  Create a Queue identity object with the specified queue name
     *
     *  @param queueName The name of the Queue Destination
     *
     *  @throws InvalidDestinationException If the queueName contains illegal
     *          syntax.
     */
    public Queue createQueue(String queueName)
    throws JMSException {
        String methodName = "createQueue(queueName)";
        _loggerJS.fine(_lgrMID_INF+
               "sessionId="+sessionId+":"+ methodName + "=" + queueName);
        this._checkIfClosed(methodName);
        return (Queue) new com.sun.messaging.BasicQueue(queueName);
    }

    /**
     *  Create a StreamMessage
     */
    public StreamMessage createStreamMessage()
    throws JMSException {
        String methodName = "createStreamMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (StreamMessage) new
                DirectStreamPacket(this);

    }

    /**
     *  Create a TemporaryQueue identity object
     */
    public javax.jms.TemporaryQueue createTemporaryQueue()
    throws JMSException {
        String methodName = "createTemporaryQueue()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        TemporaryQueue tq = new TemporaryQueue(this.dc);
        this.dc._createDestination(tq._getDestination());
        this.dc.addTemporaryDestination(tq);
        return tq;
    }

    /**
     *  Create a TemporaryTopic identity object
     */
    public javax.jms.TemporaryTopic createTemporaryTopic()
    throws JMSException {
        String methodName = "createTemporaryTopic()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        TemporaryTopic tt = new TemporaryTopic(this.dc);
        this.dc._createDestination(tt._getDestination());
        this.dc.addTemporaryDestination(tt);
        return tt;
    }

    /**
     *  Create a TextMessage
     */
    public TextMessage createTextMessage()
    throws JMSException {
        String methodName = "createTextMessage()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (TextMessage) new DirectTextPacket(this, null);
    }

    /**
     *  Create a TextMessage initialized with the specified String
     */
    public TextMessage createTextMessage(String text)
    throws JMSException {
        String methodName = "createTextMessage(text)";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" + 
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (TextMessage) new DirectTextPacket(this, text);
    }

    /**
     *  Create a Topic identity object with the specified topic name
     *
     *  @param topicName The name of the Topic Destination
     *
     *  @throws InvalidDestinationException If the topicName contains illegal
     *          syntax.
     */
    public Topic createTopic(String topicName)
    throws JMSException {
        String methodName = "createTopic(topicName)";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" + sessionId + 
                    ":"+ methodName + "=" + topicName);
        }
        this._checkIfClosed(methodName);
        return (Topic) new com.sun.messaging.BasicTopic(topicName);
    }

    /**
     *  Return the acknowledgement mode of the Session
     */
    public int getAcknowledgeMode()
    throws JMSException {
        String methodName = "getAcknowledgeMode()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        switch(this.ackMode) {
            case AUTO_ACKNOWLEDGE:
                return Session.AUTO_ACKNOWLEDGE;
            case CLIENT_ACKNOWLEDGE:
                return Session.CLIENT_ACKNOWLEDGE;
            case DUPS_OK_ACKNOWLEDGE:
                return Session.DUPS_OK_ACKNOWLEDGE;
            case TRANSACTED:
                return Session.SESSION_TRANSACTED;
            case NO_ACKNOWLEDGE:
                return com.sun.messaging.jms.Session.NO_ACKNOWLEDGE;
            default:
                throw new JMSException(methodName + ":Unknown aknowledge mode");
        }
    }

    /**
     *  Return the Session's distinguished MessageListener
     */
    public MessageListener getMessageListener()
    throws JMSException {
        String methodName = "getMessageListener()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return (MessageListener)null;
    }

    /**
     *  Return whether the Session is transacted or not
     */
    public boolean getTransacted()
    throws JMSException {
        String methodName = "getTransacted()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        return getTransactedNoCheck();
    }

    protected boolean getTransactedNoCheck() {
        return (this.ackMode == SessionAckMode.TRANSACTED);
    }

    /**
     *  Restart this Session's message delivery starting with the oldest
     *  unacknowledged message
     */
    public void recover()
    throws JMSException {
        String methodName = "recover()";
        //JMSServiceReply reply;
        //JMSServiceReply.Status status;
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        this._checkTransactedState(methodName, false); //DONOT allowIfTransacted
        //Session.recover() is a NOP for an MDB Session 
        //and
        //all for all Session Acknowledge modes except CLIENT_ACKNOWLEDGE
        if ((this._isMDBSession()) ||
                (this.ackMode != SessionAckMode.CLIENT_ACKNOWLEDGE)){
            return;
        }
        //XXX:tharakan
        //Stop the sesssion before recover
        //this._stop();
        try {
            //reply = jmsservice.redeliverMessages(this.connectionId, this.sessionId,
            jmsservice.redeliverMessages(this.connectionId, this.sessionId,
                    unackedMessageIDs.toArray(new SysMessageID[0]),
                    unackedConsumerIDs.toArray(new Long[0]),
                    this.transactionId, true);
        } catch (JMSServiceException jmsse) {
            //status = jmsse.getJMSServiceReply().getStatus();
            jmsse.getJMSServiceReply().getStatus();
            String failure_cause = "unknown JMSService error";
            /*
            switch (status) {
                case NOT_FOUND:
                    failure_cause = "message not found and cannot be acknowledged";
                    break;
                default:
                    failure_cause = "unkown JMSService server error.";
            }
            */
            String exerrmsg = 
                    "redeliverMessage on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ this.connectionId +
                    " and sessionId:" + this.sessionId +
                    " due to " + failure_cause;
            _loggerJS.severe(exerrmsg);
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            throw jmse;
        } finally{
            this.unackedMessageIDs.clear();
            this.unackedConsumerIDs.clear();
            //XXX:tharakan
            //Start the session
            //this._start();
        }
    }

    /**
     *  Rollback all messages acknowledged in this transacted session
     *  unacknowledged message
     */
    public void rollback()
    throws JMSException {
        String methodName = "rollback()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        this._checkTransactedState(methodName, true); //allow only if transacted
        //XXX:handle rollback inside deliver() includes the current message
        this._rollback(methodName);
        this._startTransaction(methodName);
    }

    /**
     *  Run message delivery of the messages that have been loaded in this
     *  sesssion.
     */
    public void run(){
        String methodName = "run()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        try {
            this._checkIfClosed(methodName);
            this._checkTransactedState(methodName, false); //allow only if non-tx
            this._unsupported("Session.recover()");
        } catch (JMSException ex) {
            _loggerJS.severe(this._lgrMID_EXC +
                    "sessionId=" + sessionId +
                    ":"+ methodName + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    /**
     *  Set the Session's distinguished MessageListener
     */
    public void setMessageListener(javax.jms.MessageListener listener)
    throws JMSException {
        String methodName = "setMessageListener()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._unsupported("Session.setMessageListener()");
        this._checkIfClosed(methodName);
    }

    /**
     *  Unsubscribe the durable subscription specified by name
     */
    public void unsubscribe(String name)
    throws JMSException {
        String methodName = "unsubscribe()";
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+methodName);
        }
        this._checkIfClosed(methodName);
        if (name == null || "".equals(name)){
            throw new InvalidDestinationException(
                    "NULL or empty name for unsubscribe");
        }
        //cycle through consumers and check if one of them is lose them one by one
        int t_consumerId;
        DirectConsumer dc = null;
        Iterator<DirectConsumer> k = this.dc._getDurableConsumers().iterator();
        while (k.hasNext()) {
            dc = k.next();
            if (name.equals(dc.getDurableName())) {
                String exErrMsg = _lgrMID_WRN+
                        "sessionId="+sessionId+":"+methodName+
                        ":name:"+ name +
                        ":Is in use";
                _loggerJS.warning(exErrMsg);
                JMSException jmse = new JMSException(exErrMsg);
                throw jmse;
            }
        }
        try {
        	// this method returns a JMSServiceReply but we don't need to check it since
        	// an exception will be thrown if it is not OK
            jmsservice.deleteConsumer(this.connectionId, this.sessionId, 0L, null, false, name, this.dc._getClientID());
        } catch (JMSServiceException jmsse) {
            String exErrMsg = _lgrMID_EXC+
                    "sessionId="+sessionId+":"+methodName+
                    ":name:"+ name + ":error="+
                    jmsse.getMessage();
            _loggerJS.warning(exErrMsg);
            JMSException jmse = new InvalidDestinationException(exErrMsg);
            jmse.initCause(jmsse);
            throw jmse;
        }
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.Session
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.QueueSession
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Create a QueueReceiver for the specified Queue
     */
    public QueueReceiver createReceiver(Queue queue)
    throws JMSException {
        return (QueueReceiver)this._createAndAddConsumer(
            "createReceiver(Queue)", queue, null, false);
    }

    /**
     *  Create a QueueReceiver for the specified Queue with the specified
     *  selector
     */
    public QueueReceiver createReceiver(Queue queue, String selector)
    throws JMSException {
        return (QueueReceiver)this._createAndAddConsumer(
            "createReceiver(Queue, selector)", queue, selector, false);
    }

    /**
     *  Create a QueueSender for the specified Queue
     *
     *  @param  queue The Queue to be used when creating the QueueSender
     */
    public QueueSender createSender(Queue queue)
    throws JMSException {
        return (QueueSender)this._createAndAddProducer("createSender(Queue)",
                queue);
        /*
        String methodName = "createSender(Queue)";
        String _name = null;
        if (queue != null){
            _name = queue.getQueueName();
        }
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+ methodName + ":Queue="+_name);
        }
        this._checkIfClosed(methodName);
        com.sun.messaging.jmq.jmsservice.Destination.Life _life =
                com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD;
        if (queue !=null) {
            //Spcified destination case
            if (queue instanceof javax.jms.TemporaryQueue){
                _life =
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
            }
            com.sun.messaging.jmq.jmsservice.Destination _destination =
                    new com.sun.messaging.jmq.jmsservice.Destination(_name,
                    com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE,
                    _life);
            dc._createDestination(_destination);
            long producerId = _createProducerId(_destination);
            DirectProducer dp = new DirectProducer(this, this.jmsservice,
                    producerId, queue, _destination);
            this.addProducer(dp);
            return (QueueSender)dp;
        } else {
            DirectProducer dp = new DirectProducer(this, this.jmsservice);
            this.addProducer(dp);
            return (QueueSender)dp;
        }
        */
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.QueueSession
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.TopicSession
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Create a TopicPublisher for the specified Topic
     *
     *  @param  topic The Topic to be used when creating the TopicPublisher
     */
    public TopicPublisher createPublisher(Topic topic)
    throws JMSException {
        return (TopicPublisher)this._createAndAddProducer("createPublisher()",
                topic);
        /*
        String methodName = "createPublisher(Topic)";
        String _name = null;
        if (topic != null) {
            _name = topic.getTopicName();
        }
        com.sun.messaging.jmq.jmsservice.Destination.Life _life =
                com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD;
        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" +
                    sessionId+":"+ methodName + "=" + _name);
        }
        this._checkIfClosed(methodName);
        if (topic !=null) {
            //Spcified destination case
            if (topic instanceof javax.jms.TemporaryTopic){
                _life =
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
            }
            com.sun.messaging.jmq.jmsservice.Destination _destination =
                    new com.sun.messaging.jmq.jmsservice.Destination(_name,
                    com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC,
                    _life);
            //XXX:tharakan:Need to Verify Destination first ???
            //dc._verifyDestination(_destination);
            dc._createDestination(_destination);
            long producerId = _createProducerId(_destination);
            DirectProducer dp = new DirectProducer(this, this.jmsservice,
                    producerId, topic, _destination);
            this.addProducer(dp);
            return (TopicPublisher)dp;
        } else {
            DirectProducer dp = new DirectProducer(this, this.jmsservice);
            this.addProducer(dp);
            return (TopicPublisher)dp;
        }
        */
    }

    /**
     *  Create a TopicSubscriber for the specified Topic.
     */
    public TopicSubscriber createSubscriber(Topic topic)
    throws JMSException {
        return (TopicSubscriber)this._createAndAddConsumer(
            "createSubscriber(Topic)", topic, null, false);
    }

    /**
     *  Create a TopicSubscriber for the specified Topic using a selector
     *  and specifying whether messages published by its own connection should
     *  be delivered to it.
     */
    public TopicSubscriber createSubscriber(Topic topic, 
        String selector, boolean noLocal)
        throws JMSException {
        return (TopicSubscriber)this._createAndAddConsumer(
            "createSubscriber(Topic, selector, noLocal)",
            topic, selector, noLocal);
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.TopicSession
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Initialize this DirectSession - this is overridden by sub classes
     */
    protected void _initSession()
    throws JMSException {
        String methodName = "_initSession()";
        _loggerOC.entering(_className, methodName);
        this.isAsync = false;
        this.inDeliver = false;
        this.isClosed = false;
        this.isClosing = false;
        this.isStopped = true;
        this.ackOnFetch =
              (((this.ackMode == JMSService.SessionAckMode.AUTO_ACKNOWLEDGE) ||
                (this.ackMode == JMSService.SessionAckMode.TRANSACTED) ||
                (this.ackMode == JMSService.SessionAckMode.DUPS_OK_ACKNOWLEDGE))
               ? true : false);
        if (this.isTransacted() && !this.dc.isManaged()) {
            this._startTransaction(methodName);
        }
        if (!this.dc.isStopped()){
            this._start();
        }
    }
    /**
     *  Start the JMS Session
     */
    protected void _start(){
        this.isStopped = false;
        try {
            jmsservice.startSession(this.connectionId, this.sessionId);
        } catch (JMSServiceException jmsse){
            _loggerJS.warning(_lgrMID_WRN+
                    "sessionId="+sessionId+":"+"_start():"+
                    "JMSService.startSession():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
    }

    /**
     *  Start a transaction for this transacted session
     */
    protected void _startTransaction(String fromMethod)
    throws JMSException {
        JMSServiceReply reply = null;
        try {
            reply = jmsservice.startTransaction(this.connectionId,
                    this.sessionId, null, 0,
                    JMSService.TransactionAutoRollback.UNSPECIFIED, 0L);
            try {
                this.transactionId = reply.getJMQTransactionID();
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC +
                        "sessionId=" + this.sessionId +
                        ":_startTransaction from " + fromMethod +
                        ":JMSServiceException:Missing JMQTransactionID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJS.severe(exerrmsg);
                throw jmse;
            }
        } catch (JMSServiceException jmsse){
            _loggerJS.severe(_lgrMID_EXC +
                    "sessionId=" + this.sessionId +
                    ":_startTransaction from " + fromMethod +
                    "JMSServiceException=" +
                    jmsse.getMessage());
        }
    }

    /**
     *  Rollback the transaction for this session
     */
    protected void _rollback(String methodName)
    throws JMSException{
        //JMSServiceReply reply = null;
        try {
            //XXX:Set the Redelivered Flag until CTS changes the test
            //reply = jmsservice.rollbackTransaction(this.connectionId,
            jmsservice.rollbackTransaction(this.connectionId,
                    this.transactionId, null, true, true);
            //XXX:Set the Redelivered Flag until CTS changes the test
        } catch (JMSServiceException jmsse){
            _loggerJS.warning(_lgrMID_WRN+
                    "sessionId="+sessionId+":"+methodName+
                    ":JMSServiceException="+
                    jmsse.getMessage());
        }
    }

    /**
     *  Stop the JMS Session
     */
    protected void _stop(){
        try {
            jmsservice.stopSession(this.connectionId, this.sessionId, true);
        } catch (JMSServiceException jmsse){
            _loggerJS.warning(_lgrMID_WRN+
                    "sessionId="+sessionId+":"+"_stop():"+
                    "JMSService.stopSession():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
    }

    /**
     *  Return the DirectConnection for this DirectSession
     */
    public final DirectConnection getConnection(){
        return this.dc;
    }
    /**
     *  Return the connectionId for this DirectSession
     *
     *  @return The connectionId
     */
    public final long getConnectionId() {
        return this.connectionId;
    }

    /**
     *  Return the sessionId for this DirectSession
     *
     *  @return The sessionId
     */
    public final long getSessionId() {
        return this.sessionId;
    }

    protected long _getTransactionId(){
        return this.transactionId;
    }

    /**
     *  Return the closed state of this DirectSession
     *
     *  @return {@code true} if this session has been closed;
     *          {@code false} otherwise
     */
    public synchronized boolean isClosed() {
        return this.isClosed;
    }

    /**
     *  Return the Async state of this DirectSession
     *
     *  @return {@code true} if this session is dedicated to async consumption
     *          {@code false} otherwise
     */
    public synchronized boolean isAsync() {
        return this.isAsync;
    }

    /**
     *  Set the Async state of this DirectSession
     * 
     * 
     * @param isAsync The value to set the async state of this DirectSession
     */
    protected synchronized void _setAsync(boolean isAsync) {
        this.isAsync = isAsync;
        //If the dc has started,then start this session
        //else when the dc starts start this session 
        //only if this is asynch?
    }

    protected synchronized final boolean inDeliver() {
        return this.inDeliver;
    }

    /**
     *  Return the XATransacted state of this DirectSession
     *
     *  @return {@code true} if this session is involved in an XA Transaction
     *          {@code false} if it is not involved in an XA Transaction
     */
    public synchronized boolean isXATransacted() {
        return this.isXATransacted;
    }

    /**
     *  Return the Transacted state of this DirectSession
     *
     *  @return {@code true} if this session is involved in a JMS Transaction
     *          {@code false} if it is not involved in a JMS Transaction
     */    public boolean isTransacted() {
        return (this.ackMode == SessionAckMode.TRANSACTED);
    }

     /**
     *  Check if the DirectSession is closed prior to performing an
     *  operation an throw a JMSException if the session is closed.
     *
     *  @param methodname The name of the method from which this check is called
     *
     *  @throws JMSException if it is closed
     */
    private void _checkIfClosed(String methodname)
    throws JMSException {
        if (isClosed()) {
            String closedmsg = _lgrMID_EXC + methodname +
                    "Session is closed:Id=" + sessionId;
            _loggerJS.warning(closedmsg);
            throw new javax.jms.IllegalStateException(closedmsg);
        }
    }

    /**
     *  Check whether the Session transacted state is legal.<p>
     *  If the DirectSession is transacted, then it is illegal to call
     *  non-transacted APIs.<br>
     *  If the DirectSession is non-transacted, then it is illegal to call
     *  transacted APIs.
     *
     *  @param  methodname The method from which this check is called
     *  @param  allowIfTransacted Indicates whether to allow an operation
     *          for a transacted session or disallow it.<br>
     *          If {@code true} then the operation will be allowed on a
     *          transacted session.
     *          i.e. it will <b>not</b> throw an IllegalStateException if
     *          the session is transacted.<br>
     *          If {@code false} then the operation will be allowed only if
     *          the session is a non-transacted session.
     *          i.e. it will <b>not</b> throw an IllegalStateException if
     *          the session is non-transacted.<br>
     *
     *  @throws IllegalStateException if the operation is illegal
     */
    private void _checkTransactedState(String methodName, boolean allowIfTransacted)
    throws JMSException {
        String illegalStateMsg = null;
        if (this.isTransacted() && !allowIfTransacted){
            illegalStateMsg = this._lgrMID_EXC + methodName +
                    ":Illegal for a transacted Session:sessionId=" +
                    this.sessionId;
        } else {
            if (!this.isTransacted() && allowIfTransacted){
                illegalStateMsg = this._lgrMID_EXC + methodName +
                        ":Illegal for a non-transacted Session:sessionId=" +
                        this.sessionId;
            }
        }
        if (illegalStateMsg != null) {
            _loggerJS.warning(illegalStateMsg);
            throw new javax.jms.IllegalStateException(illegalStateMsg);
        }
    }

    /**
     *  Check whether the Session acknowledge mode is legal.<p>
     *  If the DirectSession is transacted, then it is illegal to call
     *  non-transacted APIs.<br>
     *  If the DirectSession is non-transacted, then it is illegal to call
     *  transacted APIs.
     *
     *  @param  methodname The method from which this check is called
     *  @param  validAckMode
     * 
     * Indicates whether to allow an operation
     *          for a transacted session or disallow it.<br>
     *          If {@code true} then the operation will be allowed on a
     *          transacted session.
     *          i.e. it will <b>not</b> throw an IllegalStateException if
     *          the session is transacted.<br>
     *          If {@code false} then the operation will be allowed only if
     *          the session is a non-transacted session.
     *          i.e. it will <b>not</b> throw an IllegalStateException if
     *          the session is non-transacted.<br>
     *
     *  @throws IllegalStateException if the operation is illegal
     *
    private void _checkAcknowledgeMode(String methodName, 
            JMSService.SessionAckMode validAckMode)
    throws JMSException {
        String illegalStateMsg = null;
        if (this.isTransacted() && !allowIfTransacted){
            illegalStateMsg = this._lgrMID_EXC + methodName +
                    ":Illegal for a transacted Session:sessionId=" +
                    this.sessionId;
        } else {
            if (!this.isTransacted() && allowIfTransacted){
                illegalStateMsg = this._lgrMID_EXC + methodName +
                        ":Illegal for a non-transacted Session:sessionId=" +
                        this.sessionId;
            }
        }
        if (illegalStateMsg != null) {
            _loggerJS.warning(illegalStateMsg);
            throw new javax.jms.IllegalStateException(illegalStateMsg);
        }
    }
     */

    /**
     *  Close Session intended for use when connection.close() is used
     */
    protected synchronized void _close()
    throws JMSException{
        //harmless if already closed
        if (isClosed){
            return;
        } else {
            this.isClosing = true;
            if (this.isTransacted() && !this.dc.isManaged()){
                this._rollback("_close()");
            }
            this._closeAndClearProducers();
            this._stop();
            this._closeAndClearConsumers();
            this._closeAndClearBrowsers();
        }
        try {
            //System.out.println("DS:Destroying sessionId="+sessionId+":connectionId="+connectionId);
            jmsservice.destroySession(connectionId, sessionId);
        } catch (JMSServiceException jmsse){
            _loggerJS.warning(_lgrMID_WRN+
                    "sessionId="+sessionId+":"+"close():"+
                    "JMSService.destroySession():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
        this.isClosed = true;
        this.isClosing = false;
    }

    /**
     *  Checks a Destination for consumption
     *
     *  @param  destination The JMS Destination that needs to be checked for
     *          validity of creating a consumer
     *
     *  @return The com.sun.messaging.jmq.jmsservice.Destination to use in
     *          creating the DirectConsumer
     */
    private com.sun.messaging.jmq.jmsservice.Destination
            _checkDestinationForConsumer(Destination destination)
    throws JMSException {
        JMSException jmse;
        String jmserrmsg;
        com.sun.messaging.jmq.jmsservice.Destination _destination = null;
        if (destination == null){
            jmserrmsg = _lgrMID_EXC +
                "_checkDestination:Destination is null";
            jmse = new InvalidDestinationException(jmserrmsg);
            _loggerJS.severe(jmserrmsg);
            throw jmse;
        }
        String _name = null;
        com.sun.messaging.jmq.jmsservice.Destination.Type _type =
                com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
        com.sun.messaging.jmq.jmsservice.Destination.Life _life =
                com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD;
        if (destination instanceof TemporaryDestination){
            if (!dc._hasTemporaryDestination((TemporaryDestination)destination)){
                jmserrmsg = _lgrMID_EXC +
                        "_checkDestination:Temporary Destination not owned by "+
                        " parent connectionId="+ dc.getConnectionId();
                jmse = new JMSException(jmserrmsg);
                _loggerJS.severe(jmserrmsg);
                throw jmse;
            }
            _life = com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
        }
        if (destination instanceof javax.jms.Queue){
            _name = ((Queue)destination).getQueueName();
            _type = com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
        }
        if (destination instanceof javax.jms.Topic){
            _name = ((Topic)destination).getTopicName();
            _type = com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC;
        }
        _destination = new com.sun.messaging.jmq.jmsservice.Destination(_name,
                _type, _life);
        //XXX:tharakan:Need to Verify Destination first.
        //dc._verifyDestination(_destination);
        dc._createDestination(_destination);
        return _destination;
    }

    /**
     *  Throw a JMSException with the appropriate message for unsupported
     *  operations.
     *
     *  @param  methodname The method name for which this unsupported
     *          exception is to be thrown.
     */
    private void _unsupported(String methodname)
    throws JMSException {
        String unsupported = _lgrMID_WRN+
                    "sessionId="+sessionId+":"+methodname;
        _loggerJS.warning(unsupported);
        throw new JMSException(unsupported);
    }

    /**
     *  Add a Consumer to the list of JMS MessageConsumer objects created by
     *  this DirectSession.
     *
     *  @param  consumer The DirectConsumer to be added
     */
    protected void addConsumer(DirectConsumer consumer) {
        this.consumers.add(consumer);
    }

    /**
     *  Remove a Consumer from the list of JMS MessageConsumer objects created
     *  by this DirectSession.
     *
     *  @param  consumer  The DirectConsumer to be removed
     */
    protected void removeConsumer(DirectConsumer consumer) {
        boolean result = this.consumers.remove(consumer);
        //This session *has* to be in the list else something went wrong :)
        assert (result == true);
    }

    /**
     *  Add a Producer to the list of JMS MessageProducer objects created by
     *  this DirectSession.
     *
     *  @param  producer The DirectProducer to be added
     */
    protected void addProducer(DirectProducer producer) {
        this.producers.add(producer);
    }

    /**
     *  Remove a Producer from the list of JMS MessageProducer objects created
     *  by this DirectSession.
     *
     *  @param  producer  The DirectProducer to be removed
     */
    protected void removeProducer(DirectProducer producer) {
        boolean result = this.producers.remove(producer);
        //This session *has* to be in the list else something went wrong :)
        assert (result == true);
    }

    /**
     *  Add a QueueBrowser to the list of JMS QueueBrowsers objects created by
     *  this DirectSession.
     *
     *  @param  browser The DirectQueueBrowser to be added
     */
    protected void addBrowser(DirectQueueBrowser browser) {
        this.browsers.add(browser);
    }

    /**
     *  Remove a QueueBrowser from the list of JMS QueueBrowser objects created
     *  by this DirectSession.
     *
     *  @param  browser  The DirectQueueBrowser to be removed
     */
    protected void removeBrowser(DirectQueueBrowser browser) {
        boolean result = this.browsers.remove(browser);
        //This session *has* to be in the list else something went wrong :)
        assert (result == true);
    }

    /**
     *  Create a producer with the jmsservice and return a producerId.<p>
     *  Used by createAndAddProducer in DirectSession as well as 
     *  _createAndAddProducerId method in
     *  DirectProducer when a JMS message is produced using a DirectProducer
     *  that was created using an unspeificed JMS Destination (i.e. null)
     *
     *  @param  destination The com.sun.messaging.jmsservice.Destination object
     *  identifying the destination on which the producer is to be created.
     *
     *  @return The producerId to be used by the DirectProducer object
     *          that is returned by the JMS API method
     *
     *  @throws JMSException if any JMS server error occurred
     */
    protected long _createProducerId(
            com.sun.messaging.jmq.jmsservice.Destination destination)
    throws JMSException {
        JMSServiceReply reply;
        long producerId = 0L;
        try {
            reply = jmsservice.addProducer(this.connectionId, this.sessionId,
                    destination);
            try {
                producerId = reply.getJMQProducerID();                
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC +
                        "JMSServiceException:Missing JMQProducerID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJS.severe(exerrmsg);
                throw jmse;
            }

        } catch (JMSServiceException jse) {
            JMSServiceReply.Status status = jse.getJMSServiceReply().getStatus();
            String failure_cause;
            String exerrmsg = 
                    "createProducer on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and sessionId:" + sessionId +
                    " due to ";
            JMSException jmsse = null; 
            switch (status) {
                case FORBIDDEN:
                    failure_cause = "client forbidden to send messages to this destination.";
                    exerrmsg = exerrmsg + failure_cause;
                    jmsse = new JMSSecurityException(exerrmsg, jse.getJMSServiceReply().getErrorCode());
                    break;
                case NOT_FOUND:
                    failure_cause = "destination not found and cannot be auto-created.";
                    break;
                case CONFLICT:
                    failure_cause = "destination limit for number of producers exceeded.";
                    break;
                default:
                    failure_cause = "unkown JMSService server error.";
            }
            _loggerJS.severe(exerrmsg);
            if (jmsse == null) {
                jmsse = new JMSException(exerrmsg+failure_cause);
            }
            jmsse.initCause(jse);
            throw jmsse;
        }
        return producerId;
    }

    /**
     *  Create a MessageProducer object that can be returned by the
     *  JMS API method.<p>
     *  This method is used by the methods implementing javax.jms.Session,
     *  javax,jms.QueueSession, and javax.jms.TopicSession<br>
     *  A successfully created DirectProducer is added to the table of Producer
     *  objects maintained by this DirectSession.<br>
     *  If the destination was explicitly specified, then a producerId is
     *  created with the jmsservice.<br>
     *  If the destination was not specified, then a producerId of 0L is used
     *  along with a null (unspecified) destination. In this case, a producerId
     *  is only created when a message is produced if no producerId for that
     *  destination exists.
     *
     *  @param  methodName The JMS API method that was called.
     *  @param  destination The JMS Destination object identifying the
     *          destination on which the producer is to be created. This can be
     *          null.
     *
     *  @return The DirectProduder object to be returned by the JMS API method.
     *
     *  @throws JMSException if any JMS error occurred.
     */
    private DirectProducer _createAndAddProducer(String methodName,
            Destination destination)
    throws JMSException {
        long producerId = 0L;
        com.sun.messaging.jmq.jmsservice.Destination jmsservice_dest = null;

        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" + this.sessionId +
                    ":" + methodName +
                    ":Destination=" + destination);
        }
        this._checkIfClosed(methodName);
        if (destination !=null) {
            //Spcified destination case
            String _name = null;
            com.sun.messaging.jmq.jmsservice.Destination.Type _type =
                    com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
            com.sun.messaging.jmq.jmsservice.Destination.Life _life =
                    com.sun.messaging.jmq.jmsservice.Destination.Life.STANDARD;

            if (destination instanceof javax.jms.Queue){
                _name = ((Queue)destination).getQueueName();
                _type = com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE;
                if (destination instanceof javax.jms.TemporaryQueue){
                    _life =
                        com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
                }
            }
            if (destination instanceof javax.jms.Topic){
                _name = ((Topic)destination).getTopicName();
                _type = com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC;
                if (destination instanceof javax.jms.TemporaryTopic){
                    _life =
                        com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
                }
            }
            jmsservice_dest =
                    new com.sun.messaging.jmq.jmsservice.Destination(_name,
                    _type, _life);
            //XXX:tharakan:Need to Verify Destination first.
            //dc._verifyDestination(_destination);
            dc._createDestination(jmsservice_dest);
            producerId = _createProducerId(jmsservice_dest);
        }
        //producerId and destination will be 0L and null unless destination
        //is specified in the JMS API method call to create the MessageProducer
        DirectProducer dp = new DirectProducer(this, this.jmsservice,
                    producerId, destination, jmsservice_dest);
        this.addProducer(dp);
        return dp;
    }

    private DirectConsumer _createAndAddConsumer(
        String methodName, Destination destination,
        String selector,  boolean noLocal)
        throws JMSException {

        return _createAndAddConsumer(methodName, 
            destination, selector, null, false, false, false, noLocal);
    }

    /**
     *  Create a consumerId with the jmsservice and return a MessageConsumer
     *  object that can be returned by the JMS API method.<p>
     *  This method is used by the methods implementing javax.jms.Session,
     *  javax,jms.QueueSession, and javax.jms.TopicSession<br>
     *  A successfully created DirectConsumer is added to the table of Consumer
     *  objects maintained by this DirectSession.<br>
     *  A successfully created durable DirectConsumer is added to the table
     *  of durable Consumer objects maintained by the DirectConnection of this
     *  DirectSession.
     *
     *  @param  methodName The JMS API method that was called.
     *  @param  destination The JMS Destination object identifying the
     *          destination on which the consumer is to be created.
     *  @param  selector The JMS Message selector to be used.
     *  @param subscriptionName if dest is Topic and
     *         if either durable true or share true, the subscription name
     *  @param durable if dest is Topic, if true, this is a durable subscription
     *  @param share if dest is Topic, if true, this is a shared subscription
     *  @param jmsshare if dest is Topic,
     *         if true and share true, this is a JMS 2.0 Shared Subscription
     *         if false and share true, this is a MQ Shared Subscription
     *
     *         MQ Shared Subscription: messages will be shared with other
     *         consumers in the same group that have the same
     *         clientId+DurableName for Shared Durable Subscriptions<p>
     *         OR<p>
     *         clientId+TopicName+Selector for Shared Non-Durable Sunscriptions
     *
     *  @param  noLocal If {@code true} then this consumer does not want to
     *          messages produced on it'sconnection to be delivered to it.
     *
     *  @return The DirectConsumer object to be returned by the JMS API method.
     *
     *  @throws JMSException if any JMS error occurred.
     */
    private DirectConsumer _createAndAddConsumer(
        String methodName, Destination destination,
        String selector, String subscriptionName, boolean durable,
        boolean share, boolean jmsshare, boolean noLocal)
        throws JMSException {

        JMSServiceReply reply;
        long consumerId = 0L;
        com.sun.messaging.jmq.jmsservice.Destination jmsservice_dest;

        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF + "sessionId=" + this.sessionId +
                    ":" + methodName +
                    ":Destination=" + destination + ":Selector=" + selector +
                    ":subscriptionName=" + subscriptionName + 
                    ":durable="+durable+":share="+share+":jmsshare="+jmsshare+
                    ":noLocal=" + noLocal);
        }
        this._checkIfClosed(methodName);

        jmsservice_dest = this._checkDestinationForConsumer(destination);
        String duraname = (durable ? subscriptionName:null);
        DirectConsumer consumer = new DirectConsumer(this, jmsservice,
                destination, jmsservice_dest, noLocal, selector, duraname);
        try {
            //adjusted for JMS 2.0 
            reply = jmsservice.addConsumer(connectionId, sessionId,
                    jmsservice_dest, selector, subscriptionName, durable,
                    share, jmsshare, this.getConnection()._getClientID(),
                    noLocal);
            /*
            reply = jmsservice.addConsumer(connectionId, sessionId,
                    jmsservice_dest, selector, durableName,
                    this.getConnection()._getClientID(),
                    noLocal,
                    //XXX:tharakan:using false for shared temporarily
                    false, false);
            */
            try {
                //Set consumerId right away
                consumerId = reply.getJMQConsumerID();
                consumer._setConsumerId(consumerId);
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC +
                        "JMSServiceException:Missing JMQConsumerID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJS.severe(exerrmsg);
                throw jmse;
            }

        } catch (JMSServiceException jse) {
            JMSServiceReply.Status status = jse.getJMSServiceReply().getStatus();
            String failure_cause;
            JMSException jmsse = null;
            String exerrmsg = 
                    "createConsumer on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and sessionId:" + sessionId +
                    " due to ";
            switch (status) {
                case FORBIDDEN:
                    failure_cause = "client forbidden to receive messages from this destination.";
                    exerrmsg = exerrmsg + failure_cause;
                    jmsse = new JMSSecurityException(exerrmsg, jse.getJMSServiceReply().getErrorCode());
                    break;
                case NOT_FOUND:
                    failure_cause = "destination not found and cannot be auto-created.";
                    break;
                case CONFLICT:
                    failure_cause = "destination limit for number of consumers exceeded.";
                    break;
                case BAD_REQUEST:
                    failure_cause = "invalid selector="+selector;
                    exerrmsg = exerrmsg + failure_cause;
                    jmsse = new InvalidSelectorException(exerrmsg);
                    break;
                case PRECONDITION_FAILED:
                	if (jse.getCause()!=null && jse.getCause() instanceof BrokerException){
                    	failure_cause = jse.getCause().getMessage();
                    	exerrmsg = exerrmsg + failure_cause;
                		jmsse = new IllegalStateException(exerrmsg,((BrokerException)jse.getCause()).getErrorCode());               		
                		break;
                	} 
                default:
                    failure_cause = "unknown JMSService server error.";
            }
            _loggerJS.severe(exerrmsg);
            if (jmsse == null){
                exerrmsg = exerrmsg + failure_cause;
                jmsse = new JMSException(exerrmsg);
            }
            jmsse.initCause(jse);
            throw jmsse;
        }
        this.addConsumer(consumer);
        if (subscriptionName != null && durable) {
            this.dc.addDurableConsumer(consumer);
        }
        if (destination instanceof TemporaryDestination){
            this.dc._incrementTemporaryDestinationUsage(
                    (TemporaryDestination)destination);
        }
        return consumer;
    }

    /**
     *  Create a Browser with the jmsservice and return a consumerId.
     *  Used by the methods implementing javax.jms.Session,
     *  javax,jms.QueueSession, and javax.jms.TopicSession
     *
     *  @param  methodName The JMS API method that was called.
     *  @param  destination The JMS Destination object identifying the
     *          destination on which the browser is to be created.
     *  @param  selector The JMS Message selector to be used.
     *
     *  @return The DirectQueueBrowser object to be returned by the JMS API
     *          method.
     *
     *  @throws JMSException if any JMS error occurred.
     */
    private DirectQueueBrowser _createAndAddBrowser(String methodName,
            Queue destination, String selector)
    throws JMSException {
        JMSServiceReply reply;
        long consumerId = 0L;
        DirectQueueBrowser browser = null;
        com.sun.messaging.jmq.jmsservice.Destination jmsservice_dest;

        if (_logFINE){
            _loggerJS.fine(_lgrMID_INF+"sessionId="+sessionId+":"+methodName+
                    ":Destination="+destination+":selector="+selector);
        }
        this._checkIfClosed(methodName);

        jmsservice_dest = this._checkDestinationForConsumer(destination);
        try {
            reply = jmsservice.addBrowser(connectionId, sessionId,
                    jmsservice_dest, selector);
            try {
                //Must get a consumerId from the addBrowser method
                consumerId = reply.getJMQConsumerID();
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC + methodName +
                        "JMSServiceException:Missing JMQConsumerID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJS.severe(exerrmsg);
                throw jmse;
            }
            browser = new DirectQueueBrowser(this, jmsservice,
                    consumerId, destination, jmsservice_dest, selector);
        } catch (JMSServiceException jse) {
            JMSServiceReply.Status status = jse.getJMSServiceReply().getStatus();
            String failure_cause;
            JMSException jmsse = null;
            String exerrmsg = 
                    "createBrowser on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and sessionId:" + sessionId +
                    " due to ";
            switch (status) {
                case FORBIDDEN:
                    failure_cause = "client forbidden to browse messages from this destination.";
                    exerrmsg = exerrmsg + failure_cause;
                    jmsse = new JMSSecurityException(exerrmsg, jse.getJMSServiceReply().getErrorCode());
                    break;
                case NOT_FOUND:
                    failure_cause = "destination not found and cannot be auto-created.";
                    break;
                case CONFLICT:
                    failure_cause = "destination limit for number of consumers exceeded.";
                    break;
                case BAD_REQUEST:
                    failure_cause = "invalid selector="+selector;
                    exerrmsg = exerrmsg + failure_cause;
                    jmsse = new InvalidSelectorException(exerrmsg);
                    break;
                default:
                    failure_cause = "unkown JMSService server error.";
            }
            _loggerJS.severe(exerrmsg);
            if (jmsse == null){
                exerrmsg = exerrmsg + failure_cause;
                jmsse = new JMSException(exerrmsg);
            }
            jmsse.initCause(jse);
            throw jmsse;
        }
        this.addBrowser(browser);
        return browser;
    }

    /**
     *  Close and clear producers table
     */
    private void _closeAndClearProducers() {
        //cycle through producers and close them one by one
        DirectProducer dp = null;
        Iterator<DirectProducer> k = this.producers.iterator();
        while (k.hasNext()) {
            dp = k.next();
            try {
                dp._close();
                k.remove();
            } catch (JMSException jmsedpc) {
                _loggerJS.warning(_lgrMID_WRN+
                        "sessionId="+sessionId+":"+"close_producer:"+
                        "producerId:"+ dp.getProducerId() +
                        ":JMSException="+
                        jmsedpc.getMessage());
            }
        }
        this.producers.clear();
    }

    /**
     *  Close and clear consumers table
     */
    private void _closeAndClearConsumers() {
        //cycle through consumers and close them one by one
        int t_consumerId;
        DirectConsumer dc = null;
        Iterator<DirectConsumer> k = this.consumers.iterator();
        while (k.hasNext()) {
            dc = k.next();
            try {
                dc._close();
                k.remove();
            } catch (JMSException jmsedcc) {
                _loggerJS.warning(_lgrMID_WRN+
                        "sessionId="+sessionId+":"+"close_consumer:"+
                        "consumerId:"+ dc.getConsumerId() +
                        ":JMSException="+
                        jmsedcc.getMessage());
            }
        }
        this.consumers.clear();
    }

    /**
     *  Close and clear browsers table
     */
    private void _closeAndClearBrowsers() {
        //cycle through browsers and close them one by one
        DirectQueueBrowser dqb = null;
        Iterator<DirectQueueBrowser> k = this.browsers.iterator();
        while (k.hasNext()) {
            dqb = k.next();
            try {
                dqb._close();
                k.remove();
            } catch (JMSException jmsedpc) {
                _loggerJS.warning(_lgrMID_WRN+
                        "sessionId="+sessionId+":"+"close_producer:"+
                        "consumerId:"+ dqb.getConsumerId() +
                        ":JMSException="+
                        jmsedpc.getMessage());
            }
        }
        this.browsers.clear();
    }

    /**
     *  Send a message from this DirectSession - only one thread can do this
     *  at a time. Note that it is ok to do this from a Message Consumer
     */
    protected synchronized void _sendMessage(JMSPacket msgPkt)
    throws JMSException {
        //Add transactionId if needed
        if (ResourceAdapter._isFixBUGDB18849350()) {

            if (this.dc.isManaged() && this.dc.isEnlisted()) {
                msgPkt.getPacket().setTransactionID(
                    this.dc._getXAResource()._getTransactionId());
            } else if (this.transactionId != 0L) {
                msgPkt.getPacket().setTransactionID(this.transactionId);
            } else {
                //Clear it out
                msgPkt.getPacket().setTransactionID(0L);
                msgPkt.getPacket().setIsTransacted(false);
            }

        } else {

            if (this.transactionId != 0L){
                msgPkt.getPacket().setTransactionID(this.transactionId);
            } else {
                if (this.dc.isManaged() && this.dc.isEnlisted()) {
                    msgPkt.getPacket().setTransactionID(
                        this.dc._getXAResource()._getTransactionId());
                } else {
                    //Clear it out
                   msgPkt.getPacket().setTransactionID(0L);
                   msgPkt.getPacket().setIsTransacted(false);
               }
            }
        }
        try {
            this.jmsservice.sendMessage(this.connectionId, msgPkt);
        } catch (JMSServiceException jmsse) {
            String exerrmsg = _lgrMID_EXC +
                    "JMSServiceException on send message:"+
                    jmsse.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            _loggerJS.severe(exerrmsg);
            throw jmse;
        }
    }

    /**
     *  Deliver a message from this DirectSession - only one thread can do this
     *  at a time.
     */
    protected synchronized JMSAck _deliverMessage(
            javax.jms.MessageListener msgListener, JMSPacket jmsPacket,
            long consumerId) throws ConsumerClosedNoDeliveryException {
        JMSAck jmsAck = null;
        SysMessageID messageID = null;
        long xaTxnId = 0L;
        if (this.enableThreadCheck) {
            //Relies on the *same* thread being used to deliver all messages
            //while this sesion is alive
            long tId = Thread.currentThread().getId();
            if (this.deliverThreadId == 0L) {
                //first time
                this.deliverThreadId = tId;
            } else {
                if (this.deliverThreadId != tId) {
                    throw new RuntimeException("Invalid to call deliver from two different threads!");
                }
            }
        }
        javax.jms.Message jmsMsg = null;
        if (msgListener == null) {
            throw new RuntimeException("DirectConsumer:MessageListener not set!");
        }
        if (jmsPacket == null){
            throw new RuntimeException(
                    "DirectConsumer:JMSPacket is null!");
        }
        try {
            jmsMsg = DirectPacket.constructMessage(jmsPacket, consumerId,
                    this, this.jmsservice, false);
        } catch (Exception e) {
            
        }
                
        if (jmsMsg == null) {
            throw new RuntimeException(
                    "DirectConsumer:JMS Message in Packet is null!");
        }
        try {
            this.inDeliver = true;
            msgListener.onMessage(jmsMsg);
            //this.ds._deliverMessage(this.msgListener, jmsMsg);
            this.inDeliver = false;
            messageID = ((DirectPacket)jmsMsg).getReceivedSysMessageID();
            if (this.ackMode != SessionAckMode.CLIENT_ACKNOWLEDGE) {
                /*
                if (this.dc.isManaged() && this.dc.isEnlisted()){
                    xaTxnId = this.dc._getXAResource()._getTransactionId();
                } else {
                    xaTxnId = this._getTransactionId();
                }
                */
                xaTxnId = this._getTransactionId();
                jmsAck = new DirectAck(this.connectionId, this.sessionId,
                        consumerId, messageID, xaTxnId,
                        JMSService.MessageAckType.ACKNOWLEDGE);
            } else {
                //Do not need to recover an MDB Session
                if (!this._isMDBSession()){
                    //Insert message's SysMessageID and consumerId for recover
                    //for non-MDB Session
                    unackedMessageIDs.add(messageID);
                    unackedConsumerIDs.add(consumerId);
                }
            }
        } catch (ConsumerClosedNoDeliveryException e) {
            throw e;
        } catch (Exception e){
            System.out.println(
                    "DirectConsumer:Caught Exception delivering message"
                    + e.getMessage());
            //Re-attempt redelivery semantics here
            //Ack UNDELIVERABLE or DEAD
        }
        return jmsAck;
    }

    /**
     *  Fetch a message for a consumer performing a sync receive.<p>
     *  Only one thread in a session can do this at a time.
     */
    protected synchronized javax.jms.Message _fetchMessage(DirectConsumer consumer, long consumerId,
            long timeout, String methodName)
    throws JMSException {
        JMSPacket jmsPacket = null;
        javax.jms.Message jmsMsg = null;
        SysMessageID messageID = null;
        long xaTxnId = 0L;
        if (false && this.dc.isStopped()) {
            String excMsg = _lgrMID_INF+"consumerId="+consumerId+":"+
                    methodName+":Connection has not been started!";
            _loggerJS.warning(excMsg);
            throw new JMSException(excMsg);
        }
        if (this.dc.isManaged() && this.dc.isEnlisted()){
            xaTxnId = this.dc._getXAResource()._getTransactionId();
        } else {
            xaTxnId = this._getTransactionId();
        }
        try {
            jmsPacket = this.jmsservice.fetchMessage(this.connectionId,
                    this.sessionId, consumerId, timeout, this.ackOnFetch,
                    xaTxnId);
        } catch (JMSServiceException jmsse) {
            
        }
        if (jmsPacket == null){
            return null;
        } else {
            try {
                jmsMsg = DirectPacket.constructMessage(jmsPacket, consumerId,
                        this, this.jmsservice, false);
                consumer.setLastMessageSeen(((DirectPacket)jmsMsg).getReceivedSysMessageID());
                if (this.ackMode == SessionAckMode.CLIENT_ACKNOWLEDGE) {
                    messageID =
                            ((DirectPacket)jmsMsg).getReceivedSysMessageID();
                    //Insert the message's ReceivedSysMessageID + consumerId
                    //for recover
                    unackedMessageIDs.add(messageID);
                    unackedConsumerIDs.add(consumerId);
                }
                return jmsMsg;
            } catch (Exception e) {
                String exerrmsg = _lgrMID_EXC +
                        "receive:Exception constructing message:"+
                        e.getMessage();
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(e);
                _loggerJS.warning(exerrmsg);
                throw jmse;
            }
        }
    }
    
	/**
	 * Fetch the body of a message for a consumer performing a sync receive.
	 * <p>
	 * Only one thread in a session can do this at a time.
	 */
	protected synchronized <T> T _fetchMessageBody(DirectConsumer consumer, long consumerId, long timeout, Class<T> c, String methodName)
			throws JMSException {
		T body = null;
		MessageFormatException savedMFE = null;
		JMSPacket jmsPacket = null;
		javax.jms.Message jmsMsg = null;
		SysMessageID messageID = null;
		long xaTxnId = 0L;
		if (false && this.dc.isStopped()) {
			String excMsg = _lgrMID_INF + "consumerId=" + consumerId + ":" + methodName
					+ ":Connection has not been started!";
			_loggerJS.warning(excMsg);
			throw new JMSException(excMsg);
		}
		if (this.dc.isManaged() && this.dc.isEnlisted()) {
			xaTxnId = this.dc._getXAResource()._getTransactionId();
		} else {
			xaTxnId = this._getTransactionId();
		}
		try {
			// get the next message but don't auto-acknowledge it
			boolean autoAcknowledge=false;
			jmsPacket = this.jmsservice.fetchMessage(this.connectionId, this.sessionId, consumerId, timeout,
					autoAcknowledge, xaTxnId);
		} catch (JMSServiceException jmsse) {
			throw new com.sun.messaging.jms.JMSException(jmsse.getMessage(),null,jmsse);
		}
		if (jmsPacket != null) {
			try {
				jmsMsg = DirectPacket.constructMessage(jmsPacket, consumerId, this, this.jmsservice, false);
				if (this.ackMode == SessionAckMode.CLIENT_ACKNOWLEDGE) {
					messageID = ((DirectPacket) jmsMsg).getReceivedSysMessageID();
					// Insert the message's ReceivedSysMessageID + consumerId
					// for recover
					unackedMessageIDs.add(messageID);
					unackedConsumerIDs.add(consumerId);
				}
			} catch (Exception e) {
				String exerrmsg = _lgrMID_EXC + "receive:Exception constructing message:" + e.getMessage();
				JMSException jmse = new JMSException(exerrmsg);
				jmse.initCause(e);
				_loggerJS.warning(exerrmsg);
				throw jmse;
			}
			try {
				body = returnPayload(jmsMsg,c);
				consumer.setLastMessageSeen(((DirectPacket)jmsMsg).getReceivedSysMessageID());
			} catch (MessageFormatException mfe) {
				// message could not be converted
				if (xaTxnId==0L && (getAcknowledgeMode()==Session.AUTO_ACKNOWLEDGE || getAcknowledgeMode()==Session.DUPS_OK_ACKNOWLEDGE)){
					// put the message back 
                                        // note that we don't call setLastMessageSeen in this case
					try {					
						SysMessageID[] messageIDs = new SysMessageID[1];
						messageIDs[0]=jmsPacket.getPacket().getSysMessageID();
						Long[] consumerIDs = new Long[1];
						consumerIDs[0]=consumerId;
						boolean setRedelivered=false;
						jmsservice.redeliverMessages(this.connectionId, this.sessionId,messageIDs,consumerIDs,xaTxnId, setRedelivered);
					} catch (JMSServiceException jmsse) {
						throw new com.sun.messaging.jms.JMSException(jmsse.getMessage(),null,jmsse);
					}	
					// throw now before we acknowledge it
					throw mfe;
				} else {
					consumer.setLastMessageSeen(((DirectPacket)jmsMsg).getReceivedSysMessageID());
					// throw at end of method
					savedMFE=mfe;
				}
			}		
			if (this.ackOnFetch) {
				try {
					// ??
					SysMessageID sysMessageID = jmsPacket.getPacket().getSysMessageID();
					jmsservice.acknowledgeMessage(connectionId,sessionId,consumerId,sysMessageID,xaTxnId,MessageAckType.ACKNOWLEDGE);
				} catch (JMSServiceException e) {
				}
			}			
		}
		
		if (savedMFE!=null) throw savedMFE;
		return body;		
	}
    
	private <T> T returnPayload(Message message, Class<T> c) throws JMSException {
		T body;
		body = message.getBody(c);
		if (body==null){
			// must be a Message
			// this doesn't have a payload, and we can't return null because this would clash with the "no message received" case,
			// so we throw an exception
			// "Message has no body and so cannot be returned using this method" 
			String errorString = AdministeredObject.cr.getKString(ClientResources.X_MESSAGE_HAS_NO_BODY);
			JMSException jmse = new javax.jms.MessageFormatException(errorString, ClientResources.X_MESSAGE_HAS_NO_BODY);
			ExceptionHandler.throwJMSException(jmse);
		}
		return body;
	}

    /**
     *  Acknowledgea a single message in an outbound session
     */
    protected void _acknowledgeThisMessage(
            DirectPacket msgPkt, long consumerId,
            JMSService.MessageAckType ackType)
    throws JMSException {
        long transactionId = 0L;
        if (this.dc.isManaged()){
            DirectXAResource dxar = this.dc._getXAResource();
            if ( dxar != null && dxar.isEnlisted()){
                transactionId = dxar._getTransactionId();
            }
        } else {
            transactionId = this.transactionId;
        }
        this._acknowledgeMessage(msgPkt, consumerId, transactionId, ackType, -1);
        //Remove the specific message from the UnackedMsgIDs
        SysMessageID _sysMsgID = msgPkt.getReceivedSysMessageID();
        int index = this.unackedMessageIDs.indexOf(_sysMsgID);
        this.unackedMessageIDs.remove(index);
        this.unackedConsumerIDs.remove(index);
    }

    /**
     *  Acknowledge a single message in an MDB session
     */
    protected void _acknowledgeThisMessageForMDB(DirectPacket msgPkt,
            long consumerId, JMSService.MessageAckType ackType,
            DirectXAResource dxar, int retryCount)
    throws JMSException{
        long transactionId = 0L;
        if (dxar != null && dxar.isEnlisted()){
            transactionId = dxar._getTransactionId();
        }
        this._acknowledgeMessage(msgPkt, consumerId, transactionId, ackType, retryCount);
    }

    protected void _acknowledgeMessage(DirectPacket msgPkt, long consumerId,
            long transactionId, JMSService.MessageAckType ackType, int retryCount)
    throws JMSException {
        JMSServiceReply.Status status;
        try {
            if (retryCount > 0) {
                jmsservice.acknowledgeMessage(this.connectionId,
                        this.sessionId, consumerId,
                        msgPkt.getReceivedSysMessageID(), transactionId,
                        ackType, retryCount);
            } else {
                jmsservice.acknowledgeMessage(this.connectionId,
                        this.sessionId, consumerId,
                        msgPkt.getReceivedSysMessageID(), transactionId,
                        ackType);
            }
        } catch (JMSServiceException jmsse) {
            status = jmsse.getJMSServiceReply().getStatus();
            String failure_cause;
            switch (status) {
                case NOT_FOUND:
                    failure_cause = "message not found and cannot be acknowledged";
                    break;
                default:
                    failure_cause = "unkown JMSService server error.";
            }
            String exerrmsg = 
                    "acknowledgeMessage on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ this.connectionId +
                    " and sessionId:" + this.sessionId +
                    " and consumerId:" + consumerId +
                    " due to " + failure_cause;
            _loggerJS.severe(exerrmsg);
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            throw jmse;
        }
    }

    /**
     * Set this session to indicate that it is being used by an MDB
     */
    protected void _setMDBSession(boolean isMDBSession) {
        this.isMDBSession = isMDBSession;
    }

    /**
     *  Returns whether this DirectSession is used by an MDB
     */
    protected boolean _isMDBSession() {
        return this.isMDBSession;
    }
    /////////////////////////////////////////////////////////////////////////
    //  end MQ methods
    /////////////////////////////////////////////////////////////////////////
}
