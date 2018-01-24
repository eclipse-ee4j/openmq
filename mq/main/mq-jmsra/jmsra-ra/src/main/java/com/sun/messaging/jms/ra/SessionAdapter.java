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

import javax.jms.*; 
import javax.resource.*;
import javax.resource.spi.*;

import java.io.Serializable;

import java.util.logging.Logger;

import com.sun.messaging.jms.ra.api.JMSRAXASession;
import com.sun.messaging.jms.ra.api.JMSRASessionAdapter;
import com.sun.messaging.jmq.jmsclient.ContextableSession;
import com.sun.messaging.jmq.jmsclient.XAConnectionImpl;
import com.sun.messaging.jmq.jmsclient.XASessionImpl;
import com.sun.messaging.jmq.jmsclient.XAQueueSessionImpl;
import com.sun.messaging.jmq.jmsclient.XATopicSessionImpl;
 
 
/**
 *  Implements the JMS Session interface for the Sun MQ JMS RA.
 */
 
public class SessionAdapter
implements javax.jms.Session,
           javax.jms.QueueSession,
           javax.jms.TopicSession, JMSRASessionAdapter, ContextableSession
{
    /** The ConnectionAdapter that is associated with this instance */
    private com.sun.messaging.jms.ra.ConnectionAdapter ca = null;

    /** The XAConnection instance that is associated with this instance */
    private com.sun.messaging.jmq.jmsclient.XAConnectionImpl xac = null;

    /** The XASession instance that will create Consumers, etc. */
    private com.sun.messaging.jmq.jmsclient.XASessionImpl xas = null;

    /** flag that this is a QueueSession */
    private boolean queueSession = false;

    /** flag that this is a TopicSession */
    private boolean topicSession = false;

    /** flag that this SessionAdapter is closed */
    private boolean closed = false;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.SessionAdapter";
    protected static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static transient final String _lgrNameJMSSession =
            "javax.jms.Session.mqjmsra";
    protected static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final Logger _loggerJS =
            Logger.getLogger(_lgrNameJMSSession);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_SA";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Constructor */
    public SessionAdapter(com.sun.messaging.jms.ra.ConnectionAdapter ca,
        com.sun.messaging.jmq.jmsclient.XAConnectionImpl xac,
        com.sun.messaging.jmq.jmsclient.XASessionImpl xas)
    { 
        Object params[] = new Object[3];
        params[0] = ca;
        params[1] = xac;
        params[2] = xas;

        _loggerOC.entering(_className, "constructor()", params);
        this.ca = ca;
        this.xac = xac;
        this.xas = xas;
    } 


    public XAConnectionImpl
    getXAConnection()
    {
        return xac;
    }

    public XASessionImpl
    getXASession()
    {
        return xas;
    }

    public JMSRAXASession 
    getJMSRAXASession()
    {
        return (JMSRAXASession)xas;
    }

    public void
    setQueueSession()
    {
        queueSession = true;
    }

    public void
    setTopicSession()
    {
        topicSession = true;
    }

    public void
    setConnectionAdapter(ConnectionAdapter ca)
    {
        this.ca = ca;
    }

    //Call from LT.begin
    protected void startLocalTransaction()
    throws JMSException
    {
        xas._startLocalTransaction();
    }

    //Called when ConnectionAdapter closes
    //It will remove all sessions
    protected void closeAdapter()
    {
        //System.out.println("MQRA:SA:closeAdapter()");
        if (closed) {
            return;
        }
        try {
            xas.close();
            closed = true;
        } catch (JMSException jmse) {
            System.err.println("MQRA:SA:closeAdapter:Exception-"+jmse.getMessage());
            jmse.printStackTrace();
        }
    }

    // Methods that implement javax.jms.Session //
    // Messages, Consumers, Producers //

    public BytesMessage
    createBytesMessage()
    throws JMSException
    {
        return xas.createBytesMessage();
    }

    public MapMessage
    createMapMessage()
    throws JMSException
    {
        return xas.createMapMessage();
    }

    public Message
    createMessage()
    throws JMSException
    {
        return xas.createMessage();
    }

    public ObjectMessage
    createObjectMessage()
    throws JMSException
    {
        return xas.createObjectMessage();
    }
 
    public ObjectMessage
    createObjectMessage(Serializable object)
    throws JMSException
    {
        return xas.createObjectMessage(object);
    }
 
    public StreamMessage
    createStreamMessage()
    throws JMSException
    {
        return xas.createStreamMessage();
    }
 
    public TextMessage
    createTextMessage()
    throws JMSException
    {
        return xas.createTextMessage();
    }
 
    public TextMessage
    createTextMessage(String string)
    throws JMSException
    {
        return xas.createTextMessage(string);
    }

    public MessageProducer
    createProducer(Destination destination)
    throws JMSException
    {
        return xas.createProducer(destination);
    }
 
    public MessageConsumer
    createConsumer(Destination destination)
    throws JMSException
    {
        return xas.createConsumer(destination);
    }
 
    public MessageConsumer
    createConsumer(Destination destination, String messageSelector)
    throws JMSException
    {
        return xas.createConsumer(destination, messageSelector);
    }
 
    public MessageConsumer
    createConsumer(Destination destination, String messageSelector, boolean noLocal)
    throws JMSException
    {
        return xas.createConsumer(destination, messageSelector, noLocal);
    }

    // QueueSession methods
    // Methods available to unified session throw exceptions if called in the wrong domain 
    
    public QueueReceiver
    createReceiver(Queue queue)
    throws JMSException
    {
        return xas.createReceiver(queue);
    }
 
    public QueueReceiver
    createReceiver(Queue queue, String messageSelector)
    throws JMSException
    {
        return xas.createReceiver(queue, messageSelector);
    }
 
    public QueueSender
    createSender(Queue queue)
    throws JMSException
    {
        if (topicSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:createSender() disallowed on TopicSession");
        }
        return xas.createSender(queue);
    }

    public QueueBrowser
    createBrowser(Queue queue)
    throws JMSException
    {
        if (topicSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:createBrowser() disallowed on TopicSession");
        }
        return xas.createBrowser(queue);
    }
 
    public QueueBrowser
    createBrowser(Queue queue, String messageSelector)
    throws JMSException
    {
        if (topicSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:createBrowser() disallowed on TopicSession");
        }
        return xas.createBrowser(queue,messageSelector);
    }

    public TopicSubscriber
    createSubscriber(Topic topic)
    throws JMSException
    {
        return xas.createSubscriber(topic);
    }
 
    public TopicSubscriber
    createSubscriber(Topic topic,
        String messageSelector,
        boolean noLocal)
    throws JMSException
    {
        return xas.createSubscriber(topic, messageSelector, noLocal);
    }
 
    public TopicSubscriber
    createDurableSubscriber(Topic topic, String name)
    throws JMSException
    {
        return xas.createDurableSubscriber(topic, name);
    }
 
    public TopicSubscriber
    createDurableSubscriber(Topic topic,
        String name,
        String messageSelector,
        boolean noLocal)
    throws JMSException
    {
        return xas.createDurableSubscriber(topic, name, messageSelector, noLocal);
    }
    
	@Override
	public MessageConsumer createDurableConsumer(Topic topic, String name)
			throws JMSException {
		return createDurableSubscriber(topic, name);
	}


	@Override
	public MessageConsumer createDurableConsumer(Topic topic, String name,
			String messageSelector, boolean noLocal) throws JMSException {
		return createDurableSubscriber(topic, name, messageSelector, noLocal);
	}

	@Override
	public MessageConsumer createSharedConsumer(Topic topic,
			String sharedSubscriptionName) throws JMSException {
            return xas.createSharedConsumer(
                topic, sharedSubscriptionName, null);
	}


	@Override
	public MessageConsumer createSharedConsumer(Topic topic,
			String sharedSubscriptionName, String messageSelector) throws JMSException {
            return xas.createSharedConsumer(
                topic, sharedSubscriptionName, messageSelector);
	}

        @Override 
        public MessageConsumer createSharedDurableConsumer(Topic topic, String name)
            throws JMSException {
            return xas.createSharedDurableConsumer(topic, name, null);
        }

        @Override 
        public MessageConsumer createSharedDurableConsumer(
            Topic topic, String name, String messageSelector)
            throws JMSException {
            return xas.createSharedDurableConsumer(
                topic, name, messageSelector);
        }

    public TopicPublisher
    createPublisher(Topic topic)
    throws JMSException
    {
        return xas.createPublisher(topic);
    }

    public void
    unsubscribe(String name)
    throws JMSException
    {
        if (queueSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:unsubscribe() disallowed on QueueSession");
        }
        xas.unsubscribe(name);
    }

    public Topic
    createTopic(String topicName)
    throws JMSException
    {
        return xas.createTopic(topicName);
    }

    public Queue
    createQueue(String queueName)
    throws JMSException
    {
        return xas.createQueue(queueName);
    }

    public javax.jms.TemporaryTopic
    createTemporaryTopic()
    throws JMSException
    {
        if (queueSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:createTemporaryTopic() disallowed on QueueSession");
        }
        return xas.createTemporaryTopic();
    }

    public javax.jms.TemporaryQueue
    createTemporaryQueue()
    throws JMSException
    {
        if (topicSession) {
            throw new javax.jms.IllegalStateException(
                    "MQRA:createTemporaryQueue() disallowed on TopicSession");
        }
        return xas.createTemporaryQueue();
    }



    // Methods that implement javax.jms.Session //
    // Session control methods //

    //XXX:Should this throw an exception?
    public void
    setMessageListener(javax.jms.MessageListener listener)
    throws JMSException
    {
        xas.setMessageListener(listener);
    }

    //XXX:Should this throw an exception?
    public javax.jms.MessageListener
    getMessageListener()
    throws JMSException
    {
        return (javax.jms.MessageListener)xas.getMessageListener();
    }

    //XXX:Should this throw an exception?
    public void
    run()
    {
        _loggerJS.entering(_className, "run()");
        throw new java.lang.UnsupportedOperationException(
            "MQRA:SA:Disallowed - Session.run()");
    }

    public void
    commit()
    throws JMSException
    {
        //System.out.println("MQRA:SA:commit()");
        xas.commit();
    }
 
    public void
    rollback()
    throws JMSException
    {
        //System.out.println("MQRA:SA:rollback()");
        xas.rollback();
    }
 
    public void
    recover()
    throws JMSException
    {
        //System.out.println("MQRA:SA:recover()");
        xas.recover();
    }
    
    /* (non-Javadoc)
     * @see com.sun.messaging.jmq.jmsclient.ContextableSession#clientAcknowledge()
     */
    public void clientAcknowledge() throws JMSException{
    	xas.clientAcknowledge();
    }
 
    public void close() throws JMSException {
         close(false);
    }

    protected void close(boolean fromConnection) throws JMSException {
        _loggerJS.entering(_className, "close()");
        if (closed) {
            return;
        }
        if (!fromConnection) {
            ca.removeSessionAdapter(this);
        }
        xas.close();
        closed = true;
        //// This generates a ManagedConnection close event
        //if (mc != null) {
            //mc.removeSessionAdapter(this);
            //mc.sendEvent(ConnectionEvent.CONNECTION_CLOSED, null, this);
            //mc = null;
        //}
        //if (xas != null) {
        //}
   }

    public int
    getAcknowledgeMode()
    throws JMSException
    {
        return xas.getAcknowledgeMode();
    }

    public boolean
    getTransacted()
    throws JMSException
    {
        return xas.getTransacted();
    }

    protected void checkClosed()
    throws JMSException
    {
        if (closed) {
            throw new com.sun.messaging.jms.IllegalStateException("MQRA:SA:IllegalState-Session is closed");
        }
    }
}
