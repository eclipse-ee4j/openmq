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

/** A client uses a {@code TopicPublisher} object to publish messages on a 
  * topic. A {@code TopicPublisher} object is the publish-subscribe form
  * of a message producer.
  *
  * <P>Normally, the {@code Topic} is specified when a 
  * {@code TopicPublisher} is created.  In this case, an attempt to use 
  * the {@code publish} methods for an unidentified 
  * {@code TopicPublisher} will throw a 
  * {@code java.lang.UnsupportedOperationException}.
  *
  * <P>If the {@code TopicPublisher} is created with an unidentified 
  * {@code Topic}, an attempt to use the {@code publish} methods that 
  * assume that the {@code Topic} has been identified will throw a 
  * {@code java.lang.UnsupportedOperationException}.
  *
  * <P>During the execution of its {@code publish} method,
  * a message must not be changed by other threads within the client. 
  * If the message is modified, the result of the {@code publish} is 
  * undefined.
  * 
  * <P>After publishing a message, a client may retain and modify it
  * without affecting the message that has been published. The same message
  * object may be published multiple times.
  * 
  * <P>The following message headers are set as part of publishing a 
  * message: {@code JMSDestination}, {@code JMSDeliveryMode}, 
  * {@code JMSExpiration}, {@code JMSPriority}, 
  * {@code JMSMessageID} and {@code JMSTimeStamp}.
  * When the message is published, the values of these headers are ignored. 
  * After completion of the {@code publish}, the headers hold the values 
  * specified by the method publishing the message. It is possible for the 
  * {@code publish} method not to set {@code JMSMessageID} and 
  * {@code JMSTimeStamp} if the 
  * setting of these headers is explicitly disabled by the 
  * {@code MessageProducer.setDisableMessageID} or
  * {@code MessageProducer.setDisableMessageTimestamp} method.
  *
  *<P>Creating a {@code MessageProducer} provides the same features as
  * creating a {@code TopicPublisher}. A {@code MessageProducer} object is 
  * recommended when creating new code. The  {@code TopicPublisher} is
  * provided to support existing code.

  *
  *<P>Because {@code TopicPublisher} inherits from 
  * {@code MessageProducer}, it inherits the
  * {@code send} methods that are a part of the {@code MessageProducer} 
  * interface. Using the {@code send} methods will have the same
  * effect as using the
  * {@code publish} methods: they are functionally the same.
  *
  * @see Session#createProducer(Destination) 
  * @see TopicSession#createPublisher(Topic)
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface TopicPublisher extends MessageProducer {

    /** Gets the topic associated with this {@code TopicPublisher}.
      *
      * @return this publisher's topic
      *  
      * @exception JMSException if the JMS provider fails to get the topic for
      *                         this {@code TopicPublisher}
      *                         due to some internal error.
      */

    Topic 
    getTopic() throws JMSException;

 
    /** Publishes a message to the topic.
      * Uses the {@code TopicPublisher}'s default delivery mode, priority,
      * and time to live.
      *
      * @param message the message to publish
      *
      * @exception JMSException if the JMS provider fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses this method
      *                         with a {@code TopicPublisher} with
      *                         an invalid topic.
      * @exception java.lang.UnsupportedOperationException if a client uses this
      *                         method with a {@code TopicPublisher} that
      *                         did not specify a topic at creation time.
      * 
      * @see javax.jms.MessageProducer#getDeliveryMode()
      * @see javax.jms.MessageProducer#getTimeToLive()
      * @see javax.jms.MessageProducer#getPriority()
      */

    void 
    publish(Message message) throws JMSException;


    /** Publishes a message to the topic, specifying delivery mode,
      * priority, and time to live.
      *
      * @param message the message to publish
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds)
      *
      * @exception JMSException if the JMS provider fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses this method
      *                         with a {@code TopicPublisher} with
      *                         an invalid topic.
      * @exception java.lang.UnsupportedOperationException if a client uses this
      *                         method with a {@code TopicPublisher} that
      *                         did not specify a topic at creation time.
      */
 
    void
    publish(Message message, 
            int deliveryMode, 
	    int priority,
	    long timeToLive) throws JMSException;


    /** Publishes a message to a topic for an unidentified message producer. 
      * Uses the {@code TopicPublisher}'s default delivery mode, 
      * priority, and time to live.
      *  
      * <P>Typically, a message producer is assigned a topic at creation 
      * time; however, the JMS API also supports unidentified message producers,
      * which require that the topic be supplied every time a message is
      * published.
      *
      * @param topic the topic to publish this message to
      * @param message the message to publish
      *  
      * @exception JMSException if the JMS provider fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid topic.
      * 
      * @see javax.jms.MessageProducer#getDeliveryMode()
      * @see javax.jms.MessageProducer#getTimeToLive()
      * @see javax.jms.MessageProducer#getPriority()
      */ 

    void
    publish(Topic topic, Message message) throws JMSException;


    /** Publishes a message to a topic for an unidentified message 
      * producer, specifying delivery mode, priority and time to live.
      *  
      * <P>Typically, a message producer is assigned a topic at creation
      * time; however, the JMS API also supports unidentified message producers,
      * which require that the topic be supplied every time a message is
      * published.
      *
      * @param topic the topic to publish this message to
      * @param message the message to publish
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds)
      *  
      * @exception JMSException if the JMS provider fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid topic.
      */ 

    void
    publish(Topic topic, 
            Message message, 
            int deliveryMode, 
            int priority,
	    long timeToLive) throws JMSException;
}
