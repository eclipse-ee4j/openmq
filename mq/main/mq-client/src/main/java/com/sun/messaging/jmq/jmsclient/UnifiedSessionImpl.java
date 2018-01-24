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
 * @(#)UnifiedSessionImpl.java	1.14 08/02/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;

import java.util.Enumeration;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;

/** A UnifiedSession provides methods for creating QueueReceiver's,
  * QueueSender's, QueueBrowser's and TemporaryQueues.
  *
  * <P>If there are messages that have been received but not acknowledged
  * when a QueueSession terminates, these messages will be retained and
  * redelivered when a consumer next accesses the queue.
  *
  * @see         javax.jms.Session
  * @see         javax.jms.QueueConnection#createQueueSession(boolean, int)
  * @see         javax.jms.XAQueueSession#getQueueSession()
  * @see         com.sun.messaging.jms.Session
  */

public class UnifiedSessionImpl extends SessionImpl implements com.sun.messaging.jms.Session {
    //Now we support NO_ACKNOWLEDGE mode.
    public UnifiedSessionImpl
            (ConnectionImpl connection, boolean transacted, int ackMode) throws JMSException {

        super (connection, transacted, ackMode);
    }

    public UnifiedSessionImpl
            (ConnectionImpl connection, boolean transacted,
             int ackMode, JMSRAManagedConnection mc) throws JMSException {

        super (connection, transacted, ackMode, mc);
    }

    //Now we support NO_ACKNOWLEDGE mode.
    public UnifiedSessionImpl
            (ConnectionImpl connection, int ackMode)
            throws JMSException {

        super (connection, ackMode);
    }

    /** Create a queue identity given a Queue name.
      *
      * <P>This facility is provided for the rare cases where clients need to
      * dynamically manipulate queue identity. This allows the creation of a
      * queue identity with a provider specific name. Clients that depend
      * on this ability are not portable.
      *
      * <P>Note that this method is not for creating the physical topic.
      * The physical creation of topics is an administration task and not
      * to be initiated by the JMS interface. The one exception is the
      * creation of temporary topics is done using the createTemporaryTopic
      * method.
      *
      * @param queueName the name of this queue
      *
      * @return a Queue with the given name.
      *
      * @exception JMSException if a session fails to create a queue
      *                         due to some JMS error.
      */

    public Queue createQueue(String queueName) throws JMSException {

        checkSessionState();

        return new com.sun.messaging.BasicQueue(queueName);
    }


    /** Create a QueueReceiver to receive messages from the specified queue.
      *
      * @param queue the queue to access
      *
      * @exception JMSException if a session fails to create a receiver
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Queue specified.
      */

    public QueueReceiver
    createReceiver(Queue queue) throws JMSException {

        checkSessionState();

        return new QueueReceiverImpl (this, queue);
    }


    /** Create a QueueReceiver to receive messages from the specified queue.
      *
      * @param queue the queue to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      *
      * @exception JMSException if a session fails to create a receiver
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Queue specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      *
      */

    public QueueReceiver
    createReceiver(Queue queue, String messageSelector) throws JMSException {

        checkSessionState();

        return new QueueReceiverImpl (this, queue, messageSelector);
    }

    /** Create a QueueSender to send messages to the specified queue.
      *
      * @param queue the queue to access, or null if this is an unidentifed
      * producer.
      *
      * @exception JMSException if a session fails to create a sender
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Queue specified.
      */

    public QueueSender
    createSender(Queue queue) throws JMSException {

        checkSessionState();

        return new QueueSenderImpl (this, queue);
    }

    /** Create a QueueBrowser to peek at the messages on the specified queue.
      *
      * @param queue the queue to access
      *
      * @exception JMSException if a session fails to create a browser
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Queue specified.
      */

    public QueueBrowser
    createBrowser(Queue queue) throws JMSException {

        checkSessionState();

        return new QueueBrowserImpl(this, queue);
    }


    /** Create a QueueBrowser to peek at the messages on the specified queue.
      *
      * @param queue the queue to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      *
      * @exception JMSException if a session fails to create a browser
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Queue specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      */

    public QueueBrowser
    createBrowser(Queue queue, String messageSelector) throws JMSException {

        checkSessionState();

        return new QueueBrowserImpl(this, queue, messageSelector);
    }


    /** Create a temporary queue. It's lifetime will be that of the
      * QueueConnection unless deleted earlier.
      *
      * @return a temporary queue identity
      *
      * @exception JMSException if a session fails to create a Temporary Queue
      *                         due to some JMS error.
      */

    public TemporaryQueue
    createTemporaryQueue() throws JMSException {

        checkSessionState();
        return new TemporaryQueueImpl(connection);
    }

    /** Create a topic identity given a Topic name.
      *
      * <P>This facility is provided for the rare cases where clients need to
      * dynamically manipulate topic identity. This allows the creation of a
      * topic identity with a provider specific name. Clients that depend
      * on this ability are not portable.
      *
      * <P>Note that this method is not for creating the physical topic.
      * The physical creation of topics is an administration task and not
      * to be initiated by the JMS interface. The one exception is the
      * creation of temporary topics is done using the createTemporaryTopic
      * method.
      *
      * @param topicName the name of this topic
      *
      * @return a Topic with the given name.
      *
      * @exception JMSException if a session fails to create a topic
      *                         due to some JMS error.
      */

    public Topic createTopic(String topicName) throws JMSException {

        checkSessionState();

        return new com.sun.messaging.BasicTopic(topicName);
    }


    /** Create a non-durable Subscriber to the specified topic.
      *
      * <P>A client uses a TopicSubscriber for receiving messages that have
      * been published to a topic.
      *
      * <P>Regular TopicSubscriber's are not durable. They only receive
      * messages that are published while they are active.
      *
      * <P>In some cases, a connection may both publish and subscribe to a
      * topic. The subscriber NoLocal attribute allows a subscriber to
      * inhibit the delivery of messages published by its own connection.
      * The default value for this attribute is false.
      *
      * @param topic the topic to subscribe to
      *
      * @exception JMSException if a session fails to create a subscriber
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Topic specified.
      */
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException {

        checkSessionState();

        return createSubscriber (topic, null, false);
    }


    /** Create a non-durable Subscriber to the specified topic.
      *
      * <P>A client uses a TopicSubscriber for receiving messages that have
      * been published to a topic.
      *
      * <P>Regular TopicSubscriber's are not durable. They only receive
      * messages that are published while they are active.
      *
      * <P>Messages filtered out by a subscriber's message selector will
      * never be delivered to the subscriber. From the subscriber's
      * perspective they simply don't exist.
      *
      * <P>In some cases, a connection may both publish and subscribe to a
      * topic. The subscriber NoLocal attribute allows a subscriber to
      * inhibit the delivery of messages published by its own connection.
      *
      * @param topic the topic to subscribe to
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. This value may be null.
      * @param noLocal if set, inhibits the delivery of messages published
      * by its own connection.
      *
      * @exception JMSException if a session fails to create a subscriber
      *                         due to some JMS error or invalid selector.
      * @exception InvalidDestinationException if invalid Topic specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      */

    public TopicSubscriber
    createSubscriber(Topic topic,
             String messageSelector,
             boolean noLocal) throws JMSException {

        checkSessionState();

        return new TopicSubscriberImpl (this, topic, messageSelector, noLocal);
    }

    /**
     * Creates an unshared durable subscription 
     */
    public TopicSubscriber
    createDurableSubscriber(Topic topic, String name) throws JMSException {

        return createDurableSubscriber(topic, name, null, false);
    }

    /**
     * Creates an unshared durable subscription on the specified topic (if one
     * does not already exist), specifying a message selector and the
     * {@code noLocal} parameter, and creates a consumer on that durable
     * subscription.
     * <p>
     * A durable subscription is used by an application which needs to receive
     * all the messages published on a topic, including the ones published when
     * there is no active consumer associated with it. The JMS provider retains
     * a record of this durable subscription and ensures that all messages from
     * the topic's publishers are retained until they are delivered to, and
     * acknowledged by, a consumer on this durable subscription or until they
     * have expired.
     * <p>
     * A durable subscription will continue to accumulate messages until it is
     * deleted using the {@code unsubscribe} method.
     * <p>
     * This method may only be used with unshared durable subscriptions. Any
     * durable subscription created using this method will be unshared. This
     * means that only one active (i.e. not closed) consumer on the subscription
     * may exist at a time. The term "consumer" here means a
     * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
     * object in any client.
     * <p>
     * An unshared durable subscription is identified by a name specified by the
     * client and by the client identifier, which must be set. An application
     * which subsequently wishes to create a consumer on that unshared durable
     * subscription must use the same client identifier.
     * <p>
     * If an unshared durable subscription already exists with the same name and
     * client identifier, and the same topic, message selector and
     * {@code noLocal} value has been specified, and there is no consumer
     * already active (i.e. not closed) on the durable subscription then
     * this method creates a {@code TopicSubscriber} on the existing durable subscription.
     * <p>
     * If an unshared durable subscription already exists with the same name and
     * client identifier, and there is a consumer already active (i.e. not
     * closed) on the durable subscription, then a {@code JMSException} will be
     * thrown.
     * <p>
     * If an unshared durable subscription already exists with the same name and
     * client identifier but a different topic, message selector or
     * {@code noLocal} value has been specified, and there is no consumer
     * already active (i.e. not closed) on the durable subscription then this is
     * equivalent to unsubscribing (deleting) the old one and creating a new
     * one.
     * <p>
     * if {@code noLocal} is set to true then any messages published to the topic
     * using this session's connection, or any other connection with the same client
     * identifier, will not be added to the durable subscription.
     * <p>
     * A shared durable subscription and an unshared durable subscription may
     * not have the same name and client identifier. If a shared durable
     * subscription already exists with the same name and client identifier then
     * a {@code JMSException} is thrown.
     * <p>
     * There is no restriction on durable subscriptions and shared non-durable
     * subscriptions having the same name and clientId. Such subscriptions would
     * be completely separate.
     * <p>
     * This method is identical to the corresponding
     * {@code createDurableConsumer} method except that it returns a
     * {@code TopicSubscriber} rather than a {@code MessageConsumer} to
     * represent the consumer.
     *
     * @param topic
     *            the non-temporary {@code Topic} to subscribe to
     * @param name
     *            the name used to identify this subscription
     * @param messageSelector
     *            only messages with properties matching the message selector
     *            expression are added to the durable subscription. A value of
     *            null or an empty string indicates that there is no message
     *            selector for the durable subscription.
     * @param noLocal
     *            if true then any messages published to the topic using this
     *            session's connection, or any other connection with the same
     *            client identifier, will not be added to the durable
     *            subscription.
     * @exception InvalidDestinationException
     *                if an invalid topic is specified.
     * @exception InvalidSelectorException
     *                if the message selector is invalid.
     * @exception IllegalStateException
     *                if the client identifier is unset
     * @exception JMSException
     *                <ul>
     *                <li>if the session fails to create the unshared durable
     *                subscription and {@code TopicSubscriber} due to some
     *                internal error
     *                <li>
     *                if an unshared durable subscription already exists with
     *                the same name and client identifier, and there is a
     *                consumer already active
     *                <li>if a shared durable
     *                subscription already exists with the same name and client
     *                identifier
     *                </ul>
     */
    public TopicSubscriber
    createDurableSubscriber(Topic topic, String name,
                            String messageSelector,
                            boolean noLocal) 
                            throws JMSException {

        checkSessionState();
        checkTemporaryDestination(topic);
        checkClientIDWithBroker(true /*require clientid*/);
        return new TopicSubscriberImpl (this, topic, name,
                       messageSelector, noLocal, false /*shared*/);
    }

    /** Create a Publisher for the specified topic.
      *
      * <P>A client uses a TopicPublisher for publishing messages on a topic.
      * Each time a client creates a TopicPublisher on a topic, it defines a
      * new sequence of messages that have no ordering relationship with the
      * messages it has previously sent.
      *
      * @param topic the topic to publish to, or null if this is an
      * unidentifed producer.
      *
      * @exception JMSException if a session fails to create a publisher
      *                         due to some JMS error.
      * @exception InvalidDestinationException if invalid Topic specified.
     */

    public TopicPublisher
    createPublisher(Topic topic) throws JMSException {

        checkSessionState();

        return new TopicPublisherImpl ( this, topic );
    }


    /** Create a temporary topic. It's lifetime will be that of the
      * TopicConnection unless deleted earlier.
      *
      * @return a temporary topic identity
      *
      * @exception JMSException if a session fails to create a temporary
      *                         topic due to some JMS error.
      */

    public TemporaryTopic
    createTemporaryTopic() throws JMSException {

        checkSessionState();
        return new TemporaryTopicImpl(connection);
    }


    /** Unsubscribes a durable subscription that has been created by a client.
      *
      * <P>This method deletes the state being maintained on behalf of the
      * subscriber by its provider.
      * <p>
      * A durable subscription is identified by a name specified by the client
      * and by the client identifier if set. If the client identifier was set
      * when the durable subscription was created then a client which
      * subsequently wishes to use this method to
      * delete a durable subscription must use the same client identifier.
      *
      * <P>It is erroneous for a client to delete a durable subscription
      * while there is an active <CODE>MessageConsumer</CODE>
      * or <CODE>TopicSubscriber</CODE> for the
      * subscription, or while a consumed message is part of a pending
      * transaction or has not been acknowledged in the session.
      *
      * @param name the name used to identify this subscription
      *
      * @exception JMSException if the session fails to unsubscribe to the
      * durable subscription due to some internal error.
      * @exception InvalidDestinationException if an invalid subscription name
      * is specified.
      *
      * @since 2.0
      */
    public void
    unsubscribe(String name) throws JMSException {
        MessageConsumerImpl consumer = null;
        boolean deregistered = false;

        checkSessionState();

        //check if the consumer is active
        Enumeration enum2 = consumers.elements();
        while ( enum2.hasMoreElements() ) {
            consumer = (MessageConsumerImpl) enum2.nextElement();
            if ( consumer.getDurable() ) {
                if ( consumer.getDurableName().equals(name) ) {
                    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_DURABLE_INUSE,
                                         consumer.getDurableName());

                    throw new JMSException(errorString, AdministeredObject.cr.X_DURABLE_INUSE);
                }
            }
        }

        //unsubscribe
        checkClientIDWithBroker(false /*require clientid*/);
        connection.unsubscribe ( name );
        deregistered = true;
    }

    /** Creates a MessageProducer to send messages to the specified destination
      *
      * <P>A client uses a <CODE>MessageProducer</CODE> object to send
      * messages to a destination. Since <CODE>Queue</CODE> and <CODE>Topic</CODE>
      * both inherit from <CODE>Destination</CODE>, they can be used in
      * the destination parameter to create a MessageProducer.
      *
      * @param destination the <CODE>Destination</CODE> to send to,
      * or null if this is an producer which does not have a specified
      * destination.
      *
      * @exception JMSException if the session fails to create a MessageProducer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      * is specified.
      *
      * @since 1.1
      *
     */

    public MessageProducer
    createProducer(Destination destination) throws JMSException {
        if (destination == null) {
            return new MessageProducerImpl(this, destination);
        }
        if (destination instanceof Queue) {
            return createSender((Queue)destination);
        } else {
            if (destination instanceof Topic) {
                return createPublisher((Topic)destination);
            } else {
                String errorString = AdministeredObject.cr.getKString(
                                        AdministeredObject.cr.X_INVALID_DESTINATION_CLASS,
                                        destination.getClass().getName());
                throw new JMSException(errorString, AdministeredObject.cr.X_INVALID_DESTINATION_CLASS);
            }
        }
    }

      /** Creates a <CODE>MessageConsumer</CODE> object to receive messages from the
      * specified destination. Since <CODE>Queue</CODE> and <CODE>Topic</CODE>
      * both inherit from <CODE>Destination</CODE>, they can be used in
      * the destination parameter to create a MessageConsumer.
      *
      * @param destination the <CODE>Destination</CODE> to access.
      *
      * @exception JMSException if the session fails to create a consumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      *                         is specified.
      *
      * @since 1.1
      */

    public MessageConsumer
    createConsumer(Destination destination) throws JMSException {
        return createConsumer(destination, null, false);
    }

      /** Creates a message consumer to the specified destination, using a
      * message selector. Since <CODE>Queue</CODE> and <CODE>Topic</CODE>
      * both inherit from <CODE>Destination</CODE>, they can be used in
      * the destination parameter to create a MessageConsumer.
      *
      * <P>A client uses a <CODE>MessageConsumer</CODE> object to receive
      * messages that have been semt to a Destination.
      *
      *
      * @param destination the <CODE>Destination</CODE> to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector
      * for the message consumer.
      *
      *
      * @exception JMSException if the session fails to create a MessageConsumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
       * is specified.

      * @exception InvalidSelectorException if the message selector is invalid.
      *
      * @since 1.1
      */
    public MessageConsumer
    createConsumer(Destination destination, java.lang.String messageSelector)
    throws JMSException {
        return createConsumer(destination, messageSelector, false);
    }


     /** Creates a message consumer to the specified destination, using a
      * message selector. This method can specify whether messages published by
      * its own connection should be delivered to it, if the destination is a
      * topic.
      *
      * <P>A client uses a <CODE>MessageConsumer</CODE> object to receive
      * messages that have been published to a destination. Since
      * <CODE>Queue</CODE> and <CODE>Topic</CODE>
      * both inherit from <CODE>Destination</CODE>, they can be used in
      * the destination parameter to create a MessageConsumer.
      *
      *
      * <P>In some cases, a connection may both publish and subscribe to a
      * topic. The consumer <CODE>NoLocal</CODE> attribute allows a consumer
      * to inhibit the delivery of messages published by its own connection.
      * The default value for this attribute is FALSE. The noLocal value
      *  must be supported by topics.
      *
      * @param destination the <CODE>Destination</CODE> to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector
      * for the message consumer.
      * @param NoLocal  - if True, and the destination is a topic,
      *                   inhibits the delivery of messages published
      *                   by its own connection.  The behavior for NoLocal is
      *                   not specified if the destination is a queue.
      *
      * @exception JMSException if the session fails to create a MessageConsumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      * is specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      *
      * @since 1.1
      *
      */
    public MessageConsumer
    createConsumer(Destination destination, java.lang.String messageSelector,
    boolean NoLocal)   throws JMSException {
        if (destination == null) {
            String errorString = AdministeredObject.cr.getKString(
                    AdministeredObject.cr.X_DESTINATION_NOTFOUND, "null" );
            throw new InvalidDestinationException(errorString,
                    AdministeredObject.cr.X_DESTINATION_NOTFOUND);
        }
        if (destination instanceof Queue) {
            return createReceiver((Queue)destination, messageSelector);
        } else {
            if (destination instanceof Topic) {
                return createSubscriber((Topic)destination, messageSelector, NoLocal);
            } else {
                String errorString = AdministeredObject.cr.getKString(
                                        AdministeredObject.cr.X_INVALID_DESTINATION_CLASS,
                                        destination.getClass().getName());
                throw new JMSException(errorString, AdministeredObject.cr.X_INVALID_DESTINATION_CLASS);
            }
        }

    }

    private void checkClientIDWithBroker(boolean clientIDRequired)
    throws JMSException {

        String clientID = connection.getClientID();

	if ( clientIDRequired && clientID == null ) {
            String errorString = AdministeredObject.cr.getKString(
                       AdministeredObject.cr.X_INVALID_CLIENT_ID, "\"\"");
            throw new javax.jms.IllegalStateException (errorString, 
                          AdministeredObject.cr.X_INVALID_CLIENT_ID);
	}

        if (clientID != null) {
            if (connection.getProtocolHandler().isClientIDsent() == false) {
                connection.getProtocolHandler().setClientID( clientID );
            }
        }
    }

    protected void checkTemporaryDestination(Topic topic) throws JMSException {
        if ((topic == null) || topic instanceof TemporaryTopic) {
            String errorString = AdministeredObject.cr.getKString(
                        AdministeredObject.cr.X_INVALID_DESTINATION_NAME,
                        (topic == null ? "" : topic.getTopicName()));
            throw new InvalidDestinationException(errorString,
                        AdministeredObject.cr.X_INVALID_DESTINATION_NAME);

        }
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
        String sharedSubscriptionName) throws JMSException {

        return createSharedConsumer(topic, sharedSubscriptionName, null);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
        String sharedSubscriptionName, String messageSelector) throws JMSException {
        checkSessionState();
        checkTemporaryDestination(topic);
        if (sharedSubscriptionName == null || 
            sharedSubscriptionName.trim().length() == 0) {
            String errorString = AdministeredObject.cr.getKString(
                AdministeredObject.cr.X_INVALID_SHARED_SUBSCRIPTION_NAME, 
                ""+sharedSubscriptionName);
            JMSException jmse = new JMSException(errorString, 
                AdministeredObject.cr.X_INVALID_SHARED_SUBSCRIPTION_NAME);
            ExceptionHandler.throwJMSException(jmse);
        }
        checkClientIDWithBroker(false /*require clientid*/);
        return new TopicSubscriberImpl(this, topic, 
            messageSelector, sharedSubscriptionName);
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
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name)
    throws JMSException {
        return createSharedDurableConsumer(topic, name, null);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name,
         String messageSelector)
         throws JMSException {
        checkSessionState();
        checkTemporaryDestination(topic);
        checkClientIDWithBroker(false /*require clientid*/);
        return new TopicSubscriberImpl (this, topic, name,
                       messageSelector, false /* noLocal */, true /*shared*/);
    }

}
