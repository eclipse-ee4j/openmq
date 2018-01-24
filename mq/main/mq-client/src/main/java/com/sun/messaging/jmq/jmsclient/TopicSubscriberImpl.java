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

package com.sun.messaging.jmq.jmsclient;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

/** A client uses a TopicSubscriber for receiving messages that have been
  * published to a topic. TopicSubscriber is the Pub/Sub variant of a JMS
  * message consumer.
  *
  * <P>A topic session allows the creation of multiple topic subscriber's
  * per Destination, it will deliver each message for a Destination to each
  * topic subscriber eligible to receive it. Each copy of the message
  * is treated as a completely separate message. Work done on one copy has
  * no affect on the other; acknowledging one does not acknowledge the
  * other; one message may be delivered immediately while another waits
  * for its consumer to process messages ahead of it.
  *
  * <P>Regular TopicSubscriber's are not durable. They only receive messages
  * that are published while they are active.
  *
  * <P>Messages filtered out by a subscriber's message selector will never
  * be delivered to the subscriber. From the subscriber's perspective they
  * simply don't exist.
  *
  * <P>In some cases, a connection may both publish and subscribe to a topic.
  * The subscriber NoLocal attribute allows a subscriber to inhibit the
  * delivery of messages published by its own connection.
  *
  * <P>If a client needs to receive all the messages published on a topic
  * including the ones published while the subscriber is inactive, it uses
  * a durable TopicSubscriber. JMS retains a record of this durable
  * subscription and insures that all messages from the topic's publishers
  * are retained until they are either acknowledged by this durable
  * subscriber or they have expired.
  *
  * <P>Sessions with durable subscribers must always provide the same client
  * identifier. In addition, each client must specify a name which uniquely
  * identifies (within client identifier) each durable subscription it creates.
  * Only one session at a time can have a TopicSubscriber for a particular
  * durable subscription.
  *
  * <P>A client can change an existing durable subscription by creating a
  * durable TopicSubscriber with the same name and a new topic and/or message
  * selector. Changing a durable subscription is equivalent to deleting and
  * recreating it.
  *
  * <P>TopicSessions provide the unsubscribe method for deleting a durable
  * subscription created by their client. This deletes the state being
  * maintained on behalf of the subscriber by its provider.
  *
  * @see         javax.jms.TopicSession
  * @see         javax.jms.TopicSession#createSubscriber(Topic)
  * @see         javax.jms.TopicSession#createSubscriber(Topic, String, boolean)
  * @see         javax.jms.TopicSession#createDurableSubscriber(Topic, String)
  * @see         javax.jms.TopicSession#createDurableSubscriber(Topic, String, String, boolean)
  * @see         javax.jms.MessageConsumer
  */

public class TopicSubscriberImpl extends MessageConsumerImpl implements TopicSubscriber {

    private Topic topic = null;

    /**
     * Create an unshared non-durable consumer
     */
    public TopicSubscriberImpl(SessionImpl session, Topic topic,
                                String selector, boolean noLocal)
                                throws JMSException {
        super (session, topic, selector, noLocal);  //isTopic removed
        this.topic = topic;
    }

    /**
     * Create a shared or unshared durable consumer
     */
    public TopicSubscriberImpl(SessionImpl session, Topic topic,
                               String durableName, String selector,
                               boolean noLocal, boolean shared)
                              throws JMSException {
    	
    	// if shared then noLocal MUST be false 

        super (session, topic);
        this.topic = topic;

        setMessageSelector (selector);
        setNoLocal (noLocal);
        setShared (shared );
        setDurable (true);
        setDurableName ( durableName );

        init();
    }

    /**
     * Create a shared non-durable consumer
     */
    public TopicSubscriberImpl(SessionImpl session, Topic topic,
                               String selector,  
                               String sharedSubscriptionName)
                               throws JMSException {

        super (session, topic);
        this.topic = topic;

        setMessageSelector (selector);
        setNoLocal (false);
        setShared( true );
        setSharedSubscriptionName( sharedSubscriptionName );

        init();
    }

    /** Get the topic associated with this subscriber.
      *
      * @return this subscriber's topic
      *
      * @exception JMSException if JMS fails to get topic for
      *                         this topic subscriber
      *                         due to some internal error.
      */

    public Topic
    getTopic() throws JMSException {
        checkState();
        return topic;
    }


    /** Get the NoLocal attribute for this TopicSubscriber.
      * The default value for this attribute is false.
      *
      * @return set to true if locally published messages are being inhibited.
      *
      * @exception JMSException if JMS fails to get noLocal attribute for
      *                         this topic subscriber
      *                         due to some internal error.
      */

    public boolean
    getNoLocal() throws JMSException {
        checkState();
        return noLocal;
    }
}
