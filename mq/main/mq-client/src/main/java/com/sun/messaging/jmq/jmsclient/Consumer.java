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
 * @(#)Consumer.java	1.29 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Vector;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;

import com.sun.messaging.AdministeredObject;

/**
 * A Consumer is an internal abstraction of common attributes and
 * operations for MessageConsumerImpl and ConnectionConsumerImpl
 */
abstract class Consumer {

    private static final String DMQ = "mq.sys.dmq";

    protected String messageSelector = null;

    protected ConnectionImpl connection = null;
    //XXX PROTOCOL2.1
    protected Long interestId = null;

    protected Integer destType = null;

    //protected boolean isTopic = true;
    protected boolean durable = false;
    protected boolean noLocal = false;
    protected Destination destination = null;
    private String destName = null; //for logging/error reporting only
    protected String durableName = null;
    protected String sharedSubscriptionName = null; //JMS2.0 non-durable
    protected boolean shared = false; //JMS 2.0, Topic only 

    protected boolean debug = Debug.debug;

    private boolean isRegistered = false;

    protected boolean isClosed = false;

    //XXX PROTOCOL3.5
    protected int prefetchMaxMsgCount = 100;
    protected int prefetchThresholdPercent = 50;
    protected boolean noprefetch = false;

    /**
     * This is used when adding a consumer to the broker.  Broker wants
     * to know what ack mode we are.  For transacted sessions and
     * connection consumers, this value is not changed (-1).
     */
    protected int acknowledgeMode = -1;
    protected boolean isDMQConsumer = false;

    public Consumer(ConnectionImpl connection,
                    Destination dest,
                    String messageSelector,
                    //boolean isTopic,
                    boolean noLocal) throws JMSException {

        if (dest == null) {
            String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DESTINATION_NAME, "null");

            JMSException jmse = new InvalidDestinationException(errorString,
                AdministeredObject.cr.X_INVALID_DESTINATION_NAME);

            ExceptionHandler.throwJMSException(jmse);
        }

        this.messageSelector = messageSelector;
        this.connection = connection;
        this.destination = dest;
        //this.isTopic = isTopic;
        this.noLocal = noLocal;

        checkConsumerCreation();

        //XXX PROTOCOL3.5
        this.prefetchMaxMsgCount = connection.prefetchMaxMsgCount;
        this.prefetchThresholdPercent = connection.prefetchThresholdPercent;
        this.noprefetch = !(connection.consumerFlowLimitPrefetch);

        /**
         * XXX PROTOCOL2.1 -- InterestId will be
         * assigned in ReadChannel when it receives ADD_CONSUMER_REPLY
         * or BROWSE packet.
         */
        //interestId = connection.getNextInterestId();
        if (dest instanceof Queue) {
            String tmpdn = ((Queue)dest).getQueueName();
            if (tmpdn.equals(DMQ)) {
                isDMQConsumer = true;
            }
            destName = "Q:"+tmpdn;
        } else if (dest instanceof Topic) {
            destName = "T:"+((Topic)dest).getTopicName();
        } else {
            destName = dest.toString();
        }
    }

    public Consumer(ConnectionImpl connection,
                    Destination dest,
                    String messageSelector) throws JMSException {

        this(connection, dest, messageSelector, false);
    }

    public Consumer(ConnectionImpl connection,
                    Destination dest) throws JMSException {

        this(connection, dest, null, false);
    }

    /**
     * Subclass should call this method if need register this consumer's
     * interest to broker
     *
     * @exception JMSException if fails to register
     */
    protected void registerInterest() throws JMSException {
        // The consumer flow control entry will be created by
        // ReadChannel when it receives the ADD_CONSUMER_REPLY.

        connection.addInterest (this);
        isRegistered = true;
    }

    /**
     * deregister this consumer's interest
     *
     * @param destroy IS NOT USED ANY MORE.  For durable and non-durable
     *                subscriber, we have to send DELETE_CONSUMER packet
     *                to the broker when message consumer is closed.
     *
     * @exception JMSException if fails to deregister
     */
    protected void deregisterInterest() throws JMSException {
        connection.removeInterest (this);

        //XXX PROTOCOL3.5
        FlowControl fc = connection.getReadChannel().flowControl;
        fc.removeConsumerFlowControl(this);

        isRegistered = false;
    }

    /**
     * If this is a temporary destination, ONLY the connection that
     * created it can consume messages from it.
     */
    protected void checkConsumerCreation() throws JMSException {

        TemporaryDestination.checkTemporaryDestinationConsumerAllowed(connection, destination);
    }

    //protected void checkConsumerCreation() throws JMSException {
        //String name = ((com.sun.messaging.Destination) destination).getName();
        //String tempProtocol = null;

        //if ( isTopic == true ) {
        //    tempProtocol = TemporaryTopicImpl.protocol;
        //} else {
        //    tempProtocol = TemporaryQueueImpl.protocol;
        //}

        /**
         * Temp destination may ONLY be used by the consumer to consume
         *  messages by the same connection that creates it.
         */
        //XXX:BUG this really needs to be isolated into TemporaryDestination
        //if ( name.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX) ) {
            //String tempDestPrefix =
                //tempProtocol + connection.getClientIDOrIPAddress() + "/" +
                //connection.getProtocolHandler().getLocalPort();

            //if ( name.startsWith(tempDestPrefix) == false ) {
                //String errorString = AdministeredObject.cr.getKString(
                                             //AdministeredObject.cr.X_TEMP_DESTINATION_INVALID);
                //throw new JMSException (errorString, AdministeredObject.cr.X_TEMP_DESTINATION_INVALID);
            //}
        //}
    //}


    /**
     * consume a message
     *
     * @param message the message to be consumed
     *
     * @exception IOException
     * @exception JMSException
     */
    abstract protected void
    onMessage (MessageImpl msg) throws  JMSException;

    /**
     * Return the consumer reader Id for this consumer
     *
     * @return the ReadQueue Id this consumer associated to
     */
    //XXX PROTOCOL2.1
    abstract protected Long getReadQueueId();

    //XXX PROTOCOL2.1
    protected Long getInterestId() {
        return interestId;
    }

    //XXX PROTOCOL2.1
    protected void setInterestId (Long id) {
        interestId = id;
    }

    //XXX PROTOCOL2.1
    protected Integer getDestType() {
        return destType;
    }
    //XXX PROTOCOL2.1
    protected void setDestType (Integer destType) {
        this.destType = destType;
    }

    protected boolean getIsRegistered() {
        return isRegistered;
    }

    protected void setDurable ( boolean flag ) {
        durable = flag;
    }

    protected boolean getDurable() {
        return durable;
    }

    protected void setShared ( boolean flag ) {
        shared = flag;
    }

    protected boolean getShared () {
        return shared;
    }

    //protected void setIsTopic ( boolean flag ) {
    //    isTopic = flag;
    //}

    //protected boolean getIsTopic() {
    //    return isTopic;
    //}

    protected void setNoLocal ( boolean flag ) {
        noLocal = flag;
    }


    protected boolean getNoLocal() throws JMSException {
        return noLocal;
    }

    protected void setMessageSelector (String selector) throws JMSException {
        messageSelector = selector;
    }


    protected Destination getDestination() {
        return destination;
    }

    protected String getDestName() {
        return destName;
    }

    protected void setDurableName (String name) {
        durableName = name;
    }

    protected String getDurableName() {
        return durableName;
    }

    protected void setSharedSubscriptionName (String name) {
        sharedSubscriptionName = name;
    }

    protected String getSharedSubscriptionName() {
        return sharedSubscriptionName;
    }

    protected ConnectionImpl getConnection() {
        return connection;
    }

    /** Get this message consumer's message selector expression.
      *
      * @return this message consumer's message selector
      *
      * @exception JMSException if JMS fails to get message
      *                         selector due to some JMS error
      */

    public String getMessageSelector() throws JMSException {
        checkState();
        return messageSelector;
    }

    protected void checkState() throws JMSException {

        if ( isClosed ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CONSUMER_CLOSED);

            JMSException jmse =
            new com.sun.messaging.jms.IllegalStateException
            (errorString, AdministeredObject.cr.X_CONSUMER_CLOSED);

            ExceptionHandler.throwJMSException(jmse);
        }

    }

    //XXX PROTOCOL3.5
    public void setPrefetchMaxMsgCount(int prefetchMaxMsgCount) {
        this.prefetchMaxMsgCount = prefetchMaxMsgCount;
    }

    //XXX PROTOCOL3.5
    public int getPrefetchMaxMsgCount() {
        return prefetchMaxMsgCount;
    }

    //XXX PROTOCOL3.5
    public void setPrefetchThresholdPercent(int prefetchThresholdPercent) {
        this.prefetchThresholdPercent = prefetchThresholdPercent;
    }

    //XXX PROTOCOL3.5
    public int getPrefetchThresholdPercent() {
        return prefetchThresholdPercent;
    }

    /*protected SessionImpl getSession() throws JMSException {
        return null;
    }*/

    protected void dump (PrintStream ps) {
        ps.println ("Interest ID: " + interestId);
        //ps.println ("isTopic: " + isTopic);
        ps.println ("is durable: " + durable);

        if ( durable ) {
            ps.println ("durableName: " + durableName);
        }
        if ( sharedSubscriptionName != null ) {
            ps.println ("sharedSubscriptionName: " + sharedSubscriptionName);
        }

        ps.println ("is registered: " + isRegistered);
        ps.println ("destination: " + destination);
    }

    protected Hashtable getDebugState(boolean verbose) {
        Hashtable ht = new Hashtable();

        ht.put("consumerID", String.valueOf(interestId));
        ht.put("noLocal", String.valueOf(noLocal));

        ht.put("Destination Class", destination.getClass().getName());
        if (destination instanceof com.sun.messaging.Destination) {
            ht.put("Destination",
                ((com.sun.messaging.Destination) destination).getName());
        }
        if (messageSelector != null)
            ht.put("selector", String.valueOf(messageSelector));

        ht.put("durable", String.valueOf(durable));
        if (durable)
            ht.put("durableName", String.valueOf(durableName));
        if (sharedSubscriptionName != null) {
            ht.put("sharedSubscriptionName", String.valueOf(sharedSubscriptionName));
        }

        ht.put("isRegistered", String.valueOf(isRegistered));
        ht.put("isClosed", String.valueOf(isClosed));
        ht.put("FlowControl",
            connection.getReadChannel().flowControl.getDebugState(this));

        return ht;
    }

    public String toString() {
        return "ConsumerID: " + interestId + ", ConnectionID=" + connection.getConnectionID();
    }

}
