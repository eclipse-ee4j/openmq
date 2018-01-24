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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageDeliveryMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessagePriority;

/**
 *  DirectProducer encapsulates JMS MessageProducer behavior for MQ DIRECT mode
 *  operation
 */
public class DirectProducer
        implements MessageProducer, QueueSender, TopicPublisher
    {
    
    /**
     *  The JMSService for this DirectProducer
     */
    private JMSService jmsservice;

    /**
     *  The parent DirectSession that created this DirectProducer
     */
    private DirectSession ds;

    /**
     *  The connectionId of the parent DirectConnectio
     */
    private long connectionId;

    /**
     *  The sessionId of the parent DirectSession
     */
    private long sessionId;

    /**
     *  The producerId for this DirectProducer when the Destination is specified
     *  at Producer creation time.
     */
    private long producerId = 0L;

    /**
     *  The default deliveyMode for this DirectProducer
     */
    private MessageDeliveryMode deliveryMode;
    private int jmsDeliveryMode = javax.jms.Message.DEFAULT_DELIVERY_MODE;

    /**
     *  The default message priority for this DirectProducer
     */
    private MessagePriority priority;
    private int jmsPriority = javax.jms.Message.DEFAULT_PRIORITY;

    /**
     *  The default timeToLive for messages produced by this DirectProducer
     */
    private long jmsTimeToLive = javax.jms.Message.DEFAULT_TIME_TO_LIVE;

    /**
     *  The default deliveryDelay for messages produced by this DirectProducer
     */
    private long jmsDeliveryDelay = javax.jms.Message.DEFAULT_DELIVERY_DELAY;

    /**
     *  The JMS Destination that is associated with this DirectProducer when the
     *  Destination is specified at Producer creation time.
     */
    private Destination destination = null;

    /**
     *  The JMSService Destination that is associated with this DirectProducer
     */
    private com.sun.messaging.jmq.jmsservice.Destination jmsservice_destination;

    /**
     *  Holds whether MessageID is disabled or not
     */
    private boolean disableMessageID = false;

    /**
     *  Holds whether MessageTimestamp is disabled or not
     */
    private boolean disableMessageTimestamp = false;

    /**
     *  Holds the closed state of this DirectProducer
     */
    private boolean isClosed;
    private boolean isClosing;

    /**
     *  Holds the map of producerId entries for a DirectProducer that is created
     *  with an unspecified Destination
     */
    private HashMap<String, Long> producerIds;
    
    /** For optimized logging while messaging */
    protected int _logLevel;
    protected boolean _logFINE = false;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectProducer";
    private static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSProducer =
            "javax.jms.MessageProducer.mqjmsra";
    private static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    private static transient final Logger _loggerJMP =
            Logger.getLogger(_lgrNameJMSProducer);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_DMP";
    private static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    private static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    private static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    private static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    private static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Creates a new instance of DirectProducer with unspecified destination */
    public DirectProducer(DirectSession ds, JMSService jmsservice) {
        Object params[] = new Object[2];
        params[0] = ds;
        params[1] = jmsservice;
        _loggerOC.entering(_className, "constructor()", params);        
        this.ds = ds;
        this.jmsservice = jmsservice;
        this.connectionId = ds.getConnectionId();
        this.sessionId = ds.getSessionId();
        producerIds = new HashMap<String, Long>();
    }

    /** Creates a new instance of DirectProducer with a specified destination */
    public DirectProducer(DirectSession ds, JMSService jmsservice,
            long producerId, Destination destination,
            com.sun.messaging.jmq.jmsservice.Destination jmsservice_dest) {
        Object params[] = new Object[5];
        params[0] = ds;
        params[1] = jmsservice;
        params[2] = producerId;
        params[3] = destination;
        params[4] = jmsservice_dest;
        _loggerOC.entering(_className, "constructor()", params);        
        this.ds = ds;
        this.jmsservice = jmsservice;
        this.producerId = producerId;
        this.destination = destination;
        if (this.destination == null) {
            //Initialize producerIds table if DirectProducer created for
            //unspecified Destination (i.e. null)
            producerIds = new HashMap<String, Long>();
        }
        this.jmsservice_destination = jmsservice_dest;
        this.connectionId = ds.getConnectionId();
        this.sessionId = ds.getSessionId();
        java.util.logging.Level _level = _loggerJMP.getLevel();
        if (_level != null) {
            this._logLevel = _level.intValue();
            if (this._logLevel <= java.util.logging.Level.FINE.intValue()){
                this._logFINE = true;
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.MessageProducer
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Close this MessageProducer
     */
    public synchronized void close()
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+"close():");
        //harmless if already closed
        if (isClosed){
            return;
        } else {
            ds.removeProducer(this);
            this._close();
        }
    }

    /**
     *  Return the default JMS Message DeliveryMode for this MessageProducer
     *
     *  @return The deliveryMode
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     *
     *  @see javax.jms.DeliveryMode javax.jms.DeliveryMode
     */
    public int getDeliveryMode()
    throws JMSException{
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getDeliveryMode():"+this.deliveryMode);
        this._checkIfClosed("getDeliveryMode()");
        return this.jmsDeliveryMode;
        /*
        switch(this.deliveryMode) {
            case NON_PERSISTENT:
                return javax.jms.DeliveryMode.NON_PERSISTENT;
            case PERSISTENT:
            default:
                return javax.jms.DeliveryMode.PERSISTENT;
        }
        */
    }

    /**
     *  Return the Destination associated with this MessageProducer
     *
     *  @return The Destination
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public Destination getDestination()
    throws JMSException {
        String methodName = "getDestination()";
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                methodName +
                ((this.jmsservice_destination == null) 
                ? null : this.jmsservice_destination.toString()));
        this._checkIfClosed("getDestination()");
        return this.destination;
    }

    /**
     *  Return whether MessageIDs are disabled by this MessageProducer.<p>
     *
     *  @return {@code true} If MessageIDs are diabled;<br> {@code false}
     *          If MessageIDs are not diabled.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public boolean getDisableMessageID()
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getDisableMessageID():"+this.disableMessageID);
        this._checkIfClosed("getDisableMessageID()");
        return this.disableMessageID;
    }

    /**
     *  Return whether MessageIDs are disabled by this MessageProducer.<p>
     *
     *  @return {@code true} If MessageIDs are diabled;<br> {@code false}
     *          If MessageIDs are not diabled.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public boolean getDisableMessageTimestamp()
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getDisableMessageTimestamp():"+this.disableMessageTimestamp);
        this._checkIfClosed("getDisableMessageTimestamp()");
        return this.disableMessageTimestamp;
    }

    /**
     *  Return the default JMS Message Priority for this MessageProducer
     *
     *  @return The priority
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     *
     *  @see javax.jms.Message#DEFAULT_PRIORITY javax.jms.Message.DEFAULT_PRIORITY
     *  @see com.sun.messaging.jmq.jmsservice.JMSService.MessagePriority
     */
    public int getPriority()
    throws JMSException{
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getPriority():"+this.priority);
        this._checkIfClosed("getPriority()");
        return this.jmsPriority;
        /*
        switch(this.priority) {
            case PRIORITY_0:
                return 0;
            case PRIORITY_1:
                return 1;
            case PRIORITY_2:
                return 2;
            case PRIORITY_3:
                return 3;
            case PRIORITY_4:
                return 4;
            case PRIORITY_5:
                return 5;
            case PRIORITY_6:
                return 6;
            case PRIORITY_7:
                return 7;
            case PRIORITY_8:
                return 8;
            case PRIORITY_9:
                return 9;
            default:
                return javax.jms.Message.DEFAULT_PRIORITY;
        }
        */
    }

    /**
     *  Return the timeToLive associated with this MessageProducer
     *
     *  @return The timeToLive
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public long getTimeToLive()
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getTimeToLive():"+this.jmsTimeToLive);
        this._checkIfClosed("getTimeToLive()");
        return this.jmsTimeToLive;
    }

    /**
     *  Send a Message to the specified Destination using this MessageProducer
     *
     *  @param  message The JMS Message to send
     *  @param  destination The JMS Destination to use when sending the
     *          message.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Destination
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a MessageProducer hat specified a Destination at creation
     *          time.
     */
    public void send(Destination destination, Message message)
    throws JMSException {
        String methodName = "send(Destination, Message)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, destination);
        this._createAndAddProducerId(destination);
        this._send(destination, message,
                this.jmsDeliveryMode, this.jmsPriority, this.jmsTimeToLive);
    }

    /**
     *  Send a Message to the specified Destination using this MessageProducer
     *
     *  @param  message The JMS Message to send
     *  @param  destination The JMS Destination to use when sending the
     *          message.
     *  @param  deliveryMode The JMS DeliveryMode to use when sending the
     *          message.
     *  @param  priority The JMS Priority to use when sending the message.
     *  @param  timeToLive The JMS Expiration to use when sending the message
     *          in milliseconds.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Destination
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a MessageProducer hat specified a Destination at creation
     *          time.
     */
    public void send(Destination destination, Message message, int deliveryMode,
            int priority, long timeToLive)
    throws JMSException {
        String methodName = "send(Destination, Message, deliveryMode, priority, timeToLive)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, destination);
        this._createAndAddProducerId(destination);
        this._send(destination, message, deliveryMode, priority, timeToLive);
    }

    /**
     *  Send a Message to the Destination specified by this MessageProducer
     *  using the values of deliveryMode, priority, timeToLive that are
     *  configured in this MessageProducer.
     *
     *  @param  message The JMS Message to send
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If the Destination is
     *          invalid at the time of the send.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a MessageProducer that did not specify a Destination at
     *          creation time.
     */
    public void send(Message message)
    throws JMSException {
        String methodName = "send(Message)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkForUnsupportedOperation(methodName, null, true);
        this._send(this.destination, message,
                this.jmsDeliveryMode, this.jmsPriority, this.jmsTimeToLive);
    }

    /**
     *  Send a Message to the Destination specified by this MessageProducer
     *  using the specified deliveryMode, priority, and timeToLive.
     *
     *  @param  message The JMS Message to send
     *  @param  deliveryMode The JMS DeliveryMode to use when sending the
     *          message.
     *  @param  priority The JMS Priority to use when sending the message.
     *  @param  timeToLive The JMS Expiration to use when sending the message
     *          in milliseconds.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If the Destination is
     *          invalid at the time of the send.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a MessageProducer that did not specify a Destination at
     *          creation time.
     */
    public void send(Message message, int deliveryMode, int priority,
            long timeToLive)
    throws JMSException {
        String methodName = "send(Message, deliveryMode, priority, timeToLive)";
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        this._checkIfClosed(methodName);
        this._checkForUnsupportedOperation(methodName, null, true);
        this._send(this.destination, message, deliveryMode, priority, timeToLive);
        
    }

    /**
     *  Set the default JMS Message DeliveryMode for this MessageProducer
     *
     *  @param  deliveryMode The deliveryMode to set for this MessageProducer
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     *
     *  @see javax.jms.DeliveryMode javax.jms.DeliveryMode
     */
    public void setDeliveryMode(int deliveryMode)
    throws JMSException{
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setDeliveryMode():"+deliveryMode);
        this._checkIfClosed("getDeliveryMode()");
        switch(deliveryMode) {
            case javax.jms.DeliveryMode.PERSISTENT:
                this.deliveryMode = JMSService.MessageDeliveryMode.PERSISTENT;
                this.jmsDeliveryMode = javax.jms.DeliveryMode.PERSISTENT;
                break;
            case javax.jms.DeliveryMode.NON_PERSISTENT:
                this.deliveryMode = JMSService.MessageDeliveryMode.NON_PERSISTENT;
                this.jmsDeliveryMode = javax.jms.DeliveryMode.NON_PERSISTENT;
                break;
            default:
                String excMsg = _lgrMID_EXC + "setDeliveryMode():" +
                    "Invalid deliveryMode=" + deliveryMode;
            _loggerJMP.warning(excMsg);
            throw new JMSException(excMsg);
        }
    }

    /**
     *  Set whether MessageID is disabled by this MessageProducer.<p>
     *
     *  @param  disableMessageID {@code true} if MessageIDs are to be disabled<br>
     *          {@code false} if MessageIDs are to be enabled
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public void setDisableMessageID(boolean disableMessageID)
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setDisableMessageID():"+disableMessageID);
        this._checkIfClosed("setDisableMessageID()");
        this.disableMessageID = disableMessageID;
    }

    /**
     *  Set whether MessageTimestamp is disabled by this MessageProducer.<p>
     *
     *  @param  disableMessageTimestamp {@code true} If MessageTimestamp is
     *          diabled<br>
     *          {@code false} If MessageTimestamp is not diabled.
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public void setDisableMessageTimestamp(boolean disableMessageTimestamp)
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setDisableMessageTimestamp():"+disableMessageTimestamp);
        this._checkIfClosed("setDisableMessageTimestamp()");
        this.disableMessageTimestamp = disableMessageTimestamp;
    }

    /**
     *  Set the JMS Message Priority for this MessageProducer
     *
     *  @param  priority The priority to set for this MessageProducer
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     *
     *  @see javax.jms.Message#DEFAULT_PRIORITY javax.jms.Message.DEFAULT_PRIORITY
     *  @see com.sun.messaging.jmq.jmsservice.JMSService.MessagePriority
     */
    public void setPriority(int priority)
    throws JMSException{
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setPriority():"+priority);
        this._checkIfClosed("setPriority()");
        switch(priority) {
            case 0:
                this.priority = JMSService.MessagePriority.PRIORITY_0;
                break;
            case 1:
                this.priority = JMSService.MessagePriority.PRIORITY_1;
                break;
            case 2:
                this.priority = JMSService.MessagePriority.PRIORITY_2;
                break;
            case 3:
                this.priority = JMSService.MessagePriority.PRIORITY_3;
                break;
            case 4:
                this.priority = JMSService.MessagePriority.PRIORITY_4;
                break;
            case 5:
                this.priority = JMSService.MessagePriority.PRIORITY_5;
                break;
            case 6:
                this.priority = JMSService.MessagePriority.PRIORITY_6;
                break;
            case 7:
                this.priority = JMSService.MessagePriority.PRIORITY_7;
                break;
            case 8:
                this.priority = JMSService.MessagePriority.PRIORITY_8;
                break;
            case 9:
                this.priority = JMSService.MessagePriority.PRIORITY_9;
                break;
            default:
                String excMsg = _lgrMID_EXC + "setPriority():" +
                    "Invalid to set priority outside (0-9):value=" + priority;
            _loggerJMP.warning(excMsg);
            throw new JMSException(excMsg);
        }
        this.jmsPriority = priority;
    }

    /**
     *  Set the default timeToLive associated with this MessageProducer
     *
     *  @param  timeToLive The default message expiration time in milliseconds
     *
     *  @throws javax.jms.JMSException If the MessageProducer has been closed.
     */
    public void setTimeToLive(long timeToLive)
    throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setTimeToLive():"+timeToLive);
        this._checkIfClosed("setTimeToLive()"+timeToLive);
        if (timeToLive < 0){
                String excMsg = _lgrMID_EXC + "setTimeToLive():" +
                    "Invalid to set timeToLive as negative:value=" + timeToLive;
            _loggerJMP.warning(excMsg);
            throw new JMSException(excMsg);
        } else {
            this.jmsTimeToLive = timeToLive;
        }
    }

    /** Sets the default minimum length of time in milliseconds from its dispatch time
     * before a produced message becomes visible on the target destination and available
     * for delivery to consumers.  
     *
     * <P>deliveryDelay is set to zero by default.
     *
     * @param deliveryDelay the delivery delay in milliseconds.
     *
     * @exception JMSRuntimeException if the JMS provider fails to set the delivery
     *                           delay due to some internal error.
     *
     * @see javax.jms.MessagingContext#getDeliveryDelay
     * @see javax.jms.Message#DEFAULT_DELIVERY_DELAY
     */
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "setDeliveryDelay():"+deliveryDelay);
        this._checkIfClosed("setDeliveryDelay()"+deliveryDelay);
        if (deliveryDelay < 0L){
                String excMsg = _lgrMID_EXC + "setDeliveryDelay():" +
                    "Invalid to set deliveryDelay as negative:value=" + deliveryDelay;
            _loggerJMP.warning(excMsg);
            throw new JMSException(excMsg);
        } else {
            this.jmsDeliveryDelay = deliveryDelay;
        }
    }

    /** Gets the default minimum length of time in milliseconds from its dispatch time
     * before a produced message becomes visible on the target destination and available
     * for delivery to consumers.  
     *
     * @return the delivery delay in milliseconds.
     *
     * @exception JMSRuntimeException if the JMS provider fails to get the delivery 
     *                         delay due to some internal error.
     *
     * @see javax.jms.MessagingContext#setDeliveryDelay
     */ 
    public long getDeliveryDelay() throws JMSException {
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                "getDeliveryDelay():"+this.jmsDeliveryDelay);
        this._checkIfClosed("getDeliveryDelay()");
        return this.jmsDeliveryDelay;
    }

    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.MessageProducer
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.QueueSender
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Return the Queue associated with this QueueSender
     *
     *  @return The Queue
     *
     *  @throws javax.jms.JMSException If the QueueSender has been closed.
     */
    public Queue getQueue()
    throws JMSException {
        String methodName = "getQueue()";
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":" + methodName);
        this._checkIfClosed(methodName);
        if (this.destination == null) {
            return (Queue)null;
        } else {
            if (this.destination instanceof Queue) {
                return (Queue)this.destination;
            }
        }
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                methodName + " called on Destination:"+this.destination);
        return (Queue)null;
    }

    /**
     *  Send a Message to the specified Queue using this QueueSender
     *
     *  @param  message The JMS Message to send
     *  @param  queue The JMS Queue to use when sending the message.
     *
     *  @throws javax.jms.JMSException If the QueueSender has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Destination
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a QueueSender hat specified a Destination at creation
     *          time.
     */
    public void send(Queue queue, Message message)
    throws JMSException {
        String methodName = "send(Queue, Message)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, queue);
        this._createAndAddProducerId(queue);
        this._send(queue, message,
                this.jmsDeliveryMode, this.jmsPriority, this.jmsTimeToLive);
    }

    /**
     *  Send a Message to the specified Queue using this QueueSender
     *
     *  @param  message The JMS Message to send
     *  @param  queue The JMS Queue to use when sending the
     *          message.
     *  @param  deliveryMode The JMS DeliveryMode to use when sending the
     *          message.
     *  @param  priority The JMS Priority to use when sending the message.
     *  @param  timeToLive The JMS Expiration to use when sending the message
     *          in milliseconds.
     *
     *  @throws javax.jms.JMSException If the QueueSender has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Queue
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a QueueSender that specified a Queue at creation
     *          time.
     */
    public void send(Queue queue, Message message, int deliveryMode,
            int priority, long timeToLive)
    throws JMSException {
        String methodName = "send(Queue, Message, deliveryMode, priority, timeToLive)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, queue);
        this._createAndAddProducerId(queue);
        this._send(queue, message, deliveryMode, priority, timeToLive);
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.QueueSender
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.TopicPublisher
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Return the Topic associated with this TopicPublisher
     *
     *  @return The Topic
     *
     *  @throws javax.jms.JMSException If the TopicPublisher has been closed.
     */
    public Topic getTopic()
    throws JMSException {
        String methodName = "getTopic()";
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+methodName);
        this._checkIfClosed(methodName);
        if (this.destination == null){
            return (Topic)null;
        } else {
            if (this.destination instanceof Topic) {
                return (Topic)this.destination;
            }
        }
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+
                methodName+" called on Destination:"+this.destination);
        return (Topic)null;
    }

    /**
     *  Publish a Message to the Topic specified by this TopicPublisher
     *  using the values of deliveryMode, priority, timeToLive that are
     *  configured in this TopicPublisher.
     *
     *  @param  message The JMS Message to send
     *
     *  @throws javax.jms.JMSException If the TopicPublisher has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If the Topic is
     *          invalid at the time of the send.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a TopicPublisher that did not specify a Topic at
     *          creation time.
     */
    public void publish(Message message)
    throws JMSException {
        String methodName = "publish(Message)";
        _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        this._checkIfClosed(methodName);
        this._checkForUnsupportedOperation(methodName, null, true);
        this._send(this.destination, message,
                this.jmsDeliveryMode, this.jmsPriority, this.jmsTimeToLive);
    }

    /**
     *  Send a Message to the Topic specified by this TopicPublisher
     *  using the specified deliveryMode, priority, and timeToLive.
     *
     *  @param  message The JMS Message to send
     *  @param  deliveryMode The JMS DeliveryMode to use when sending the
     *          message.
     *  @param  priority The JMS Priority to use when sending the message.
     *  @param  timeToLive The JMS Expiration to use when sending the message
     *          in milliseconds.
     *
     *  @throws javax.jms.JMSException If the TopicPublisher has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If the Topic is
     *          invalid at the time of the send.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a TopicPublisher that did not specify a Destination at
     *          creation time.
     */
    public void publish(Message message, int deliveryMode, int priority,
            long timeToLive)
    throws JMSException {
        String methodName = "publish(Message, deliveryMode, priority, timeToLive)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkForUnsupportedOperation(methodName, null, true);
        this._send(this.destination, message, deliveryMode, priority, timeToLive);
    }

    /**
     *  Publish a Message to the specified Topic using this TopicPublisher
     *
     *  @param  message The JMS Message to send
     *  @param  topic The JMS Topic to use when sending the message.
     *
     *  @throws javax.jms.JMSException If the TopicPublisher has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Destination
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a TopicPublisher that specified a Topic at creation
     *          time.
     */
    public void publish(Topic topic, Message message)
    throws JMSException {
        String methodName = "publish(Topic, Message)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, topic);
        this._createAndAddProducerId(topic);
        this._send(topic, message,
                this.jmsDeliveryMode, this.jmsPriority, this.jmsTimeToLive);
    }

    /**
     *  Publish a Message to the specified Topic using this TopicPublisher
     *
     *  @param  message The JMS Message to send
     *  @param  topic The JMS Topic to use when sending the
     *          message.
     *  @param  deliveryMode The JMS DeliveryMode to use when sending the
     *          message.
     *  @param  priority The JMS Priority to use when sending the message.
     *  @param  timeToLive The JMS Expiration to use when sending the message
     *          in milliseconds.
     *
     *  @throws javax.jms.JMSException If the TopicPublisher has been closed or
     *          it fails to send the message due to an internal error in the
     *          provider.
     *  @throws javax.jms.MessageFormatException If an invalid Message is used
     *  @throws javax.jms.InvalidDestinationException If an invalid Topic
     *          is used.
     *  @throws javax.jms.UnsupportedOperationException If this method is used
     *          with a TopicPublisher that specified a Topic at creation
     *          time.
     */
    public void publish(Topic topic, Message message, int deliveryMode,
            int priority, long timeToLive)
    throws JMSException {
        String methodName = "publish(Topic, Message, deliveryMode, priority, timeToLive)";
        if (this._logFINE){
            _loggerJMP.fine(_lgrMID_INF+"producerId="+producerId+":"+ methodName);
        }
        this._checkIfClosed(methodName);
        this._checkDestinationParam(methodName, topic);
        this._createAndAddProducerId(topic);
        this._send(topic, message, deliveryMode, priority, timeToLive);
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.TopicPublisher
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Return the connectionId for this DirectProducer
     *
     *  @return The connectionId
     */
    public long getConnectionId() {
        return this.connectionId;
    }

    /**
     *  Return the sessionId for this DirectProducer
     *
     *  @return The sessionId
     */
    public long getSessionId() {
        return this.sessionId;
    }

    /**
     *  Return the producerId for this DirectProducer
     *
     *  @return The producerId
     */
    public long getProducerId() {
        return this.producerId;
    }

    /**
     *  Return the closed state of this DirectProducer
     *
     *  @return {@code true} if this producer has been closed;
     *          {@code false} otherwise
     */
    public synchronized boolean isClosed() {
        return this.isClosed;
    }

    /**
     *  Check if the DirectProducer is closed prior to performing an
     *  operation and throw a JMSException if it is closed.
     *
     *  @param  methodname The name of the method from which this is called
     *
     *  @throws JMSException if it is closed
     */
    private void _checkIfClosed(String methodname)
    throws JMSException {
        if (isClosed()) {
            String closedmsg = _lgrMID_EXC + methodname +
                    "MessageProducer is closed:Id=" + producerId;
            _loggerJMP.warning(closedmsg);
            throw new javax.jms.IllegalStateException(closedmsg);
        }
    }

    /**
     *  Create and add a producerId to this DirectProducer
     */
    private void _createAndAddProducerId(Destination destination)
    throws JMSException {
        //Spcified destination case
        String _name = null, dName = null;
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
                dName = _name;
            } else {
                dName = _type + _name;
            }
            
        }
        if (destination instanceof javax.jms.Topic){
            _name = ((Topic)destination).getTopicName();
            _type = com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC;
            if (destination instanceof javax.jms.TemporaryTopic){
                _life =
                    com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY;
                dName = _name;
            } else {
                dName = _type + ":" + _name;
            }
        }
        com.sun.messaging.jmq.jmsservice.Destination _destination =
                new com.sun.messaging.jmq.jmsservice.Destination(_name,
                _type, _life);
        //XXX:tharakan:Need to Verify Destination first ???
        //dc._verifyDestination(_destination);
        //Destination may have been removed even if it was created before
        this.ds.getConnection()._createDestination(_destination);
        //Check producerIds for existing producerId for this destination (dName)
        if (!this.producerIds.containsKey(dName)) {
            long producerId = this.ds._createProducerId(_destination);
            this.producerIds.put(dName, producerId);
        }
    }
    /**
     *  Check if a null Destination was used when a non-null Destination is
     *  required.
     *
     *  @param  methodname The name of the method from which this is called
     *  @param  dest The Destination parameter used to the calling
     *          method
     *
     *  @throws InvalidDestinationException If a null Destination is used
     *  @throws UnsupportedOperationException If a non-null Destination
     *          parameter method is used to produce a message when the
     *          MessageProducer has been created with a specified Destination
     */
    private void _checkDestinationParam(String methodname, Destination dest)
    throws JMSException {
        if (dest == null){
            String nullDestMsg = _lgrMID_EXC + methodname +
                    ":Null Destination used:";
            _loggerJMP.warning(nullDestMsg);
            throw new InvalidDestinationException(nullDestMsg,_lgrMID_EXC);
        } else {
            this._checkForUnsupportedOperation(methodname, dest, false);
        }
    }

    /**
     *  Check for UnsupportedOperation
     *
     *  @param  methodname The name of the method from which this is called
     *  @param  dest The Destination parameter used to the calling
     *          method
     *  @param  mpDest_Required Indicates whether the MessageProducer
     *          destination is required to be non-null or not.<br>
     *          {@code true} if it is required to be non-null.<br>
     *          {@code false} if it is required to be null
     */
    private void _checkForUnsupportedOperation(String methodname,
            Destination dest, boolean mpDest_Required) {
        if (mpDest_Required == false){
            //the MessageProducer destination is required to be null
            //If found throw UnsupportedOperationException
            if (this.destination != null){
                String nonNullDestMsg = null;
                String dest_name = null;
                try {
                    dest_name = ((dest instanceof javax.jms.Queue)
                        ? ((Queue) dest).getQueueName() 
                        : ((Topic) dest).getTopicName());
                } catch (JMSException ex) {
                    dest_name = "null";
                }
                nonNullDestMsg = _lgrMID_EXC + methodname +
                        ":Unsupported Operation:" + 
                        "Producing to destination:" + dest_name +
                        ":in MessageProducer with specified Destination";
                if (this.jmsservice_destination != null) {
                    nonNullDestMsg = nonNullDestMsg + "=" +
                        this.jmsservice_destination.getType() + ":" +
                        this.jmsservice_destination.getName();
                }
                _loggerJMP.warning(nonNullDestMsg);
                throw new UnsupportedOperationException(nonNullDestMsg);
            }
        } else {
            //the MessageProducer destination is required to be non-null
            //If not found throw UnsupportedOperationException
            if (this.destination == null){
                String nullDestMsg = _lgrMID_EXC + methodname + 
                        ":Unsupported Operation:"+
                        "Producing destination not specified (null) " +
                        "in MessageProducer with unspecified Destination";
                _loggerJMP.warning(nullDestMsg);
                throw new UnsupportedOperationException(nullDestMsg);
            }
        }
    }

    /**
     *  Send a Message
     */
    private void _send(Destination destination, Message msg, int deliveryMode,
            int priority, long timeToLive)
    throws JMSException{
        JMSPacket msgPkt = null;
        DirectPacket dPkt = null;
        boolean foreignMessageConverted = false;
        //System.out.println("_send:Message is instanceof-"+msg.getClass().getName());
        if (msg instanceof DirectPacket) {
            dPkt = (DirectPacket)msg;
        } else {
            dPkt = DirectPacket.constructFromForeignMessage(this.jmsservice,
                    this.ds, msg);
            foreignMessageConverted = true;
        }
        assert dPkt != null;
        dPkt.setJMSDestination(destination);
        dPkt.setJMSDeliveryMode(deliveryMode);
        dPkt.setJMSPriority((priority));
        dPkt.setJMSExpiration(timeToLive);
        dPkt.setJMSDeliveryTime(jmsDeliveryDelay);
        dPkt.preparePacketForSend();
        msgPkt = (JMSPacket)dPkt;
        this.ds._sendMessage(msgPkt);
        if (foreignMessageConverted){
            DirectPacket.updateForeignMessageAfterSend(dPkt, msg);
        }
    }

    /**
     *  Close Producer for use when Session.close is used
     */
    protected synchronized void _close()
    throws JMSException{
        //harmless if already closed
        if (isClosed){
            return;
        } else {
            isClosing = true;
            //Anything?
        }
        try {
            if (destination !=null) {
                //System.out.println("DP:Destroying prducerId="+producerId+":connectionId="+connectionId);
                jmsservice.deleteProducer(connectionId, sessionId, producerId);
            } else {
                //must cycle through producerIds and delete them one by one
                long t_producerId;
                String dName = null;
                Iterator<String> k = producerIds.keySet().iterator();
                while (k.hasNext()) {
                    dName = k.next();
                    t_producerId = producerIds.get(dName);
                    try {
                        //System.out.println("DP:Destroying t_producerId="+t_producerId);
                        jmsservice.deleteProducer(connectionId, sessionId,
                                t_producerId);
                    } catch (JMSServiceException jmssel){
                        _loggerJMP.warning(_lgrMID_WRN+
                                "producerId="+t_producerId+":"+"close():"+
                                "JMSService.deleteProducer():"+
                                "Destination=:" + dName +
                                ":JMSServiceException="+
                                jmssel.getMessage());
                    }
                    k.remove();
                }
                producerIds.clear();
            }
        } catch (JMSServiceException jmsse){
            _loggerJMP.warning(_lgrMID_WRN+
                    "producerId="+producerId+":"+"close():"+
                    "JMSService.deleteProducer():"+
                    "Destination=:" + destination +
                    ":JMSServiceException="+
                    jmsse.getMessage());
        }
        this.isClosed = true;
        this.isClosing = false;
    }

	@Override
	public void send(Message message, CompletionListener completionListener)
			throws JMSException {
            throw new JMSException(_lgrMID_EXC+"Method send(Message, CompletionListener) is not allowed");
		
	}

	@Override
	public void send(Message message, int deliveryMode, int priority,
			long timeToLive, CompletionListener completionListener)
			throws JMSException {
            throw new JMSException(_lgrMID_EXC+"Method send(Message, int, int, long, CompletionListener) is not allowed");
	}

	@Override
	public void send(Destination destination, Message message,
			CompletionListener completionListener) throws JMSException {
            throw new JMSException(_lgrMID_EXC+"Method send(Destination, Message, CompletionListener) is not allowed");
	}

	@Override
	public void send(Destination destination, Message message,
			int deliveryMode, int priority, long timeToLive,
			CompletionListener completionListener) throws JMSException {
            throw new JMSException(_lgrMID_EXC+"Method send(Destination, Message, int, int, long, CompletionListener) is not allowed");
	}
}
