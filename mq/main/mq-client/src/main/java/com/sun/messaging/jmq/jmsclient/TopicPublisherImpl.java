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
 * @(#)TopicPublisherImpl.java	1.21 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;

//import com.sun.messaging.AdministeredObject;

/** A client uses a TopicPublisher for publishing messages on a topic.
  * TopicPublisher is the Pub/Sub variant of a JMS message producer.
  *
  * <P>Normally the Topic is specified when a TopicPublisher is created and
  * in this case, attempting to use the methods for an unidentified
  * TopicPublisher will throws an UnsupportedOperationException.
  *
  * <P>In the case that the TopicPublisher with an unidentified Topic is
  * created, the methods that assume the Topic has been identified throw
  * an UnsupportedOperationException.
  *
  * @see TopicSession#createPublisher(Topic)
  */

public class TopicPublisherImpl extends MessageProducerImpl implements TopicPublisher {
    
	Topic topic = null;
    
	//bug 6360068 - Class defines field that obscures a superclass field
    //SessionImpl session = null;

    public TopicPublisherImpl(SessionImpl session, Topic topic) throws JMSException{
        super(session, topic);
        //this.session = session;
        this.topic = topic;
    }

    /** Get the topic associated with this publisher.
      *
      * @return this publisher's topic
      *
      * @exception JMSException if JMS fails to get topic for
      *                         this topic publisher
      *                         due to some internal error.
      */

    public Topic
    getTopic() throws JMSException {
        checkState();
        return topic;
    }

    /** Publish a Message to the topic
      * Use the topics default delivery mode, timeToLive and priority.
      *
      * @param message the message to publish
      *
      * @exception JMSException if JMS fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if invalid message specified
      * @exception InvalidDestinationException if a client uses
      *                         this method with a Topic Publisher with
      *                         an invalid topic.
      */
    public void
    publish(Message message) throws JMSException {

        super.send(message);

    }

    /** Publish a Message to the topic specifying delivery mode, priority
      * and time to live to the topic.
      *
      * @param message the message to publish
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds).
      *
      * @exception JMSException if JMS fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if invalid message specified
      * @exception InvalidDestinationException if a client uses
      *                         this method with a Topic Publisher with
      *                         an invalid topic.
      */
    public void
    publish(Message message,
        int deliveryMode,
        int priority,
        long timeToLive) throws JMSException {

            super.send(message, deliveryMode, priority, timeToLive);
    }

    /** Publish a Message to a topic for an unidentified message producer.
      * Use the topics default delivery mode, timeToLive and priority.
      *
      * <P>Typically a JMS message producer is assigned a topic at creation
      * time; however, JMS also supports unidentified message producers
      * which require that the topic be supplied on every message publish.
      *
      * @param topic the topic to publish this message to
      * @param message the message to send
      *
      * @exception JMSException if JMS fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if invalid message specified
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid topic.
      */

    public void
    publish(Topic topic, Message message) throws JMSException {

        super.send(topic, message);

    }

    /** Publish a Message to a topic for an unidentified message producer,
      * specifying delivery mode, priority and time to live.
      *
      * <P>Typically a JMS message producer is assigned a topic at creation
      * time; however, JMS also supports unidentified message producers
      * which require that the topic be supplied on every message publish.
      *
      * @param topic the topic to publish this message to
      * @param message the message to send
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds).
      *
      * @exception JMSException if JMS fails to publish the message
      *                         due to some internal error.
      * @exception MessageFormatException if invalid message specified
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid topic.
      */

    public void
    publish(Topic topic,
            Message message,
            int deliveryMode,
            int priority,
            long timeToLive) throws JMSException {

        super.send(topic, message, deliveryMode, priority, timeToLive);

    }

}
