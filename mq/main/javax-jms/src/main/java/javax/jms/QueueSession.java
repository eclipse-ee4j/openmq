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

/** A {@code QueueSession} object provides methods for creating 
  * {@code QueueReceiver}, {@code QueueSender}, 
  * {@code QueueBrowser}, and {@code TemporaryQueue} objects.
  *
  * <P>If there are messages that have been received but not acknowledged 
  * when a {@code QueueSession} terminates, these messages will be retained 
  * and redelivered when a consumer next accesses the queue.
  *
  *<P>A {@code QueueSession} is used for creating Point-to-Point specific
  * objects. In general, use the {@code Session} object. 
  * The {@code QueueSession} is used to support
  * existing code. Using the {@code Session} object simplifies the 
  * programming model, and allows transactions to be used across the two 
  * messaging domains.
  * 
  * <P>A {@code QueueSession} cannot be used to create objects specific to the 
  * publish/subscribe domain. The following methods inherit from 
  * {@code Session}, but must throw an
  * {@code IllegalStateException} 
  * if they are used from {@code QueueSession}:
  *<UL>
  *   <LI>{@code createDurableSubscriber}
  *   <LI>{@code createDurableConsumer}
  *   <LI>{@code createSharedConsumer}
  *   <LI>{@code createSharedDurableConsumer}
  *   <LI>{@code createTemporaryTopic}
  *   <LI>{@code createTopic}
  *   <LI>{@code unsubscribe}
  * </UL>
  *
  * @see         javax.jms.Session
  * @see         javax.jms.QueueConnection#createQueueSession(boolean, int)
  * @see         javax.jms.XAQueueSession#getQueueSession()
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface QueueSession extends Session {

    /** Creates a queue identity given a {@code Queue} name.
      *
      * <P>This facility is provided for the rare cases where clients need to
      * dynamically manipulate queue identity. It allows the creation of a
      * queue identity with a provider-specific name. Clients that depend 
      * on this ability are not portable.
      *
      * <P>Note that this method is not for creating the physical queue. 
      * The physical creation of queues is an administrative task and is not
      * to be initiated by the JMS API. The one exception is the
      * creation of temporary queues, which is accomplished with the 
      * {@code createTemporaryQueue} method.
      *
      * @param queueName the name of this {@code Queue}
      *
      * @return a {@code Queue} with the given name
      *
      * @exception JMSException if the session fails to create a queue
      *                         due to some internal error.
      */ 
 
    Queue
    createQueue(String queueName) throws JMSException;


    /** Creates a {@code QueueReceiver} object to receive messages from the
      * specified queue.
      *
      * @param queue the {@code Queue} to access
      *
      * @exception JMSException if the session fails to create a receiver
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid queue is specified.
      */

    QueueReceiver
    createReceiver(Queue queue) throws JMSException;


    /** Creates a {@code QueueReceiver} object to receive messages from the 
      * specified queue using a message selector.
      *  
      * @param queue the {@code Queue} to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector 
      * for the message consumer.
      *  
      * @exception JMSException if the session fails to create a receiver
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid queue is specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      *
      */ 

    QueueReceiver
    createReceiver(Queue queue, 
		   String messageSelector) throws JMSException;


    /** Creates a {@code QueueSender} object to send messages to the 
      * specified queue.
      *
      * @param queue the {@code Queue} to access, or null if this is an 
      * unidentified producer
      *
      * @exception JMSException if the session fails to create a sender
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid queue is specified.
      */
 
    QueueSender
    createSender(Queue queue) throws JMSException;


    /** Creates a {@code QueueBrowser} object to peek at the messages on 
      * the specified queue.
      *
      * @param queue the {@code Queue} to access
      *
      * @exception JMSException if the session fails to create a browser
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid queue is specified.
      */

    QueueBrowser 
    createBrowser(Queue queue) throws JMSException;


    /** Creates a {@code QueueBrowser} object to peek at the messages on 
      * the specified queue using a message selector.
      *  
      * @param queue the {@code Queue} to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector 
      * for the message consumer.
      *  
      * @exception JMSException if the session fails to create a browser
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid queue is specified.
      * @exception InvalidSelectorException if the message selector is invalid.
      */ 

    QueueBrowser
    createBrowser(Queue queue,
		  String messageSelector) throws JMSException;


    /** Creates a {@code TemporaryQueue} object. Its lifetime will be that 
      * of the {@code QueueConnection} unless it is deleted earlier.
      *
      * @return a temporary queue identity
      *
      * @exception JMSException if the session fails to create a temporary queue
      *                         due to some internal error.
      */

    TemporaryQueue
    createTemporaryQueue() throws JMSException;
}
