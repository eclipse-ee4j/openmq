/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;

/** A {@code TopicSession} object provides methods for creating 
  * {@code TopicPublisher}, {@code TopicSubscriber}, and 
  * {@code TemporaryTopic} objects. It also provides a method for 
  * deleting its client's durable subscribers.
  *
  *<P>A {@code TopicSession} is used for creating Pub/Sub specific
  * objects. In general, use the  {@code Session} object, and 
  *  use {@code TopicSession}  only to support
  * existing code. Using the {@code Session} object simplifies the 
  * programming model, and allows transactions to be used across the two 
  * messaging domains.
  * 
  * <P>A {@code TopicSession} cannot be used to create objects specific to the 
  * point-to-point domain. The following methods inherit from 
  * {@code Session}, but must throw an 
  * {@code IllegalStateException} 
  * if used from {@code TopicSession}:
  *<UL>
  *   <LI>{@code createBrowser}
  *   <LI>{@code createQueue}
  *   <LI>{@code createTemporaryQueue}
  *</UL>
  *
  * @see javax.jms.Session
  * @see javax.jms.Connection#createSession(boolean, int)
  * @see javax.jms.TopicConnection#createTopicSession(boolean, int)
  * @see javax.jms.XATopicSession#getTopicSession()
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface TopicSession extends Session {

    /** Creates a topic identity given a {@code Topic} name.
      *
      * <P>This facility is provided for the rare cases where clients need to
      * dynamically manipulate topic identity. This allows the creation of a
      * topic identity with a provider-specific name. Clients that depend 
      * on this ability are not portable.
      *
      * <P>Note that this method is not for creating the physical topic. 
      * The physical creation of topics is an administrative task and is not
      * to be initiated by the JMS API. The one exception is the
      * creation of temporary topics, which is accomplished with the 
      * {@code createTemporaryTopic} method.
      *  
      * @param topicName the name of this {@code Topic}
      *
      * @return a {@code Topic} with the given name
      *
      * @exception JMSException if the session fails to create a topic
      *                         due to some internal error.
      */

    Topic
    createTopic(String topicName) throws JMSException;


    /** Creates a nondurable subscriber to the specified topic.
      *  
      * <P>A client uses a {@code TopicSubscriber} object to receive 
      * messages that have been published to a topic.
      *
      * <P>Regular {@code TopicSubscriber} objects are not durable. 
      * They receive only messages that are published while they are active.
      *
      * <P>In some cases, a connection may both publish and subscribe to a 
      * topic. The subscriber {@code NoLocal} attribute allows a subscriber
      * to inhibit the delivery of messages published by its own connection.
      * The default value for this attribute is false.
      *
      * @param topic the {@code Topic} to subscribe to
      *  
      * @exception JMSException if the session fails to create a subscriber
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid topic is specified.
      */ 

    TopicSubscriber
    createSubscriber(Topic topic) throws JMSException;


    /** Creates a nondurable subscriber to the specified topic, using a
      * message selector or specifying whether messages published by its
      * own connection should be delivered to it.
      *
      * <P>A client uses a {@code TopicSubscriber} object to receive 
      * messages that have been published to a topic.
      *  
      * <P>Regular {@code TopicSubscriber} objects are not durable. 
      * They receive only messages that are published while they are active.
      *
      * <P>Messages filtered out by a subscriber's message selector will 
      * never be delivered to the subscriber. From the subscriber's 
      * perspective, they do not exist.
      *
      * <P>In some cases, a connection may both publish and subscribe to a 
      * topic. The subscriber {@code NoLocal} attribute allows a subscriber
      * to inhibit the delivery of messages published by its own connection.
      * The default value for this attribute is false.
      *
      * @param topic the {@code Topic} to subscribe to
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector 
      * for the message consumer.
      * @param noLocal if set, inhibits the delivery of messages published
      * by its own connection
      * 
      * @exception JMSException if the session fails to create a subscriber
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid topic is specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      */

    TopicSubscriber 
    createSubscriber(Topic topic, 
		     String messageSelector,
		     boolean noLocal) throws JMSException;


	/**
	 * Creates an unshared durable subscription on the specified topic (if one
	 * does not already exist) and creates a consumer on that durable
	 * subscription. 
  	 * This method creates the durable subscription without a message selector 
  	 * and with a {@code noLocal} value of {@code false}. 
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
	 * This method may only be used with non-shared durable subscriptions. Any
	 * durable subscription created using this method will be non-shared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by
	 * the client and by the client identifier, which must be set. An
	 * application which subsequently wishes to create a consumer on that
	 * non-shared durable subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier and the same topic and message selector, and there
	 * is no consumer already active (i.e. not closed) on the durable
	 * subscription, and no consumed messages from that subscription are still
	 * part of a pending transaction or are not yet acknowledged in the session,
	 * then this method creates a {@code TopicSubscriber} on the existing
	 * durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, or consumed messages from that
	 * subscription are still part of a pending transaction or are not yet
	 * acknowledged in the session, then a {@code JMSException} will be thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier but a different topic, message selector or {@code noLocal}
	 * value has been specified, and there is no consumer already active (i.e.
	 * not closed) on the durable subscription, and no consumed messages from
	 * that subscription are still part of a pending transaction or are not yet
	 * acknowledged in the session, then the durable subscription will be
	 * deleted and a new one created.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable subscriptions having
	 * the same name and clientId. Such subscriptions would be completely
	 * separate.
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
	 * @exception InvalidDestinationException
	 *                if an invalid topic is specified.
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the non-shared durable
	 *                subscription and {@code TopicSubscriber} due to some
	 *                internal error 
	 *                <li>if the client identifier is unset 
	 *                <li>
	 *                if an unshared durable subscription already exists with
	 *                the same name and client identifier, and there is a
	 *                consumer already active 
	 *                <li>if a shared durable subscription already exists 
	 *                with the same name and client identifier
	 *                </ul>
	 *
	 */
    TopicSubscriber createDurableSubscriber(Topic topic, 
			    String name) throws JMSException;

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
	 * This method may only be used with non-shared durable subscriptions. Any
	 * durable subscription created using this method will be non-shared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by
	 * the client and by the client identifier, which must be set. An
	 * application which subsequently wishes to create a consumer on that
	 * non-shared durable subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier and the same topic and message selector, and there
	 * is no consumer already active (i.e. not closed) on the durable
	 * subscription, and no consumed messages from that subscription are still
	 * part of a pending transaction or are not yet acknowledged in the session,
	 * then this method creates a {@code TopicSubscriber} on the existing
	 * durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, or consumed messages from that
	 * subscription are still part of a pending transaction or are not yet
	 * acknowledged in the session, then a {@code JMSException} will be thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name
	 * and client identifier but a different topic, message selector or {@code noLocal}
	 * value has been specified, and there is no consumer already active (i.e.
	 * not closed) on the durable subscription, and no consumed messages from
	 * that subscription are still part of a pending transaction or are not yet
	 * acknowledged in the session, then the durable subscription will be
	 * deleted and a new one created.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * If {@code noLocal} is set to true then any messages published to the
	 * topic using this session's connection, or any other connection with the
	 * same client identifier, will not be added to the durable subscription.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable subscriptions having
	 * the same name and clientId. Such subscriptions would be completely
	 * separate.
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
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the non-shared durable
	 *                subscription and {@code TopicSubscriber} due to some
	 *                internal error 
	 *                <li>if the client identifier is unset 
	 *                <li>
	 *                if an unshared durable subscription already exists with
	 *                the same name and client identifier, and there is a
	 *                consumer already active 
	 *                <li>if a shared durable
	 *                subscription already exists with the same name and client
	 *                identifier
	 *                </ul>
	 *                
	 */
	TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
			throws JMSException;

    /** Creates a publisher for the specified topic.
      *
      * <P>A client uses a {@code TopicPublisher} object to publish 
      * messages on a topic.
      * Each time a client creates a {@code TopicPublisher} on a topic, it
      * defines a 
      * new sequence of messages that have no ordering relationship with the 
      * messages it has previously sent.
      *
      * @param topic the {@code Topic} to publish to, or null if this is an
      * unidentified producer
      *
      * @exception JMSException if the session fails to create a publisher
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid topic is specified.
     */

    TopicPublisher 
    createPublisher(Topic topic) throws JMSException;


    /** Creates a {@code TemporaryTopic} object. Its lifetime will be that 
      * of the {@code TopicConnection} unless it is deleted earlier.
      *
      * @return a temporary topic identity
      *
      * @exception JMSException if the session fails to create a temporary
      *                         topic due to some internal error.
      */
 
    TemporaryTopic
    createTemporaryTopic() throws JMSException;


    /** Unsubscribes a durable subscription that has been created by a client.
      *  
      * <P>This method deletes the state being maintained on behalf of the 
      * subscriber by its provider.
      *
      * <P>It is erroneous for a client to delete a durable subscription
      * while there is an active {@code TopicSubscriber} for the 
      * subscription, or while a consumed message is part of a pending 
      * transaction or has not been acknowledged in the session.
      *
      * @param name the name used to identify this subscription
      *  
      * @exception JMSException if the session fails to unsubscribe to the 
      *                         durable subscription due to some internal error.
      * @exception InvalidDestinationException if an invalid subscription name
      *                                        is specified.
      */

    void
    unsubscribe(String name) throws JMSException;
}
