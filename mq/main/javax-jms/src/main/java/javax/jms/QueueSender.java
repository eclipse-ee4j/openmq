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

/** A client uses a {@code QueueSender} object to send messages to a queue.
  * 
  * <P>Normally, the {@code Queue} is specified when a 
  * {@code QueueSender} is created.  In this case, an attempt to use
  * the {@code send} methods for an unidentified 
  * {@code QueueSender} will throw a 
  * {@code java.lang.UnsupportedOperationException}.
  * 
  * <P>If the {@code QueueSender} is created with an unidentified 
  * {@code Queue}, an attempt to use the {@code send} methods that 
  * assume that the {@code Queue} has been identified will throw a
  * {@code java.lang.UnsupportedOperationException}.
  *
  * <P>During the execution of its {@code send} method, a message 
  * must not be changed by other threads within the client. 
  * If the message is modified, the result of the {@code send} is 
  * undefined.
  * 
  * <P>After sending a message, a client may retain and modify it
  * without affecting the message that has been sent. The same message
  * object may be sent multiple times.
  * 
  * <P>The following message headers are set as part of sending a 
  * message: {@code JMSDestination}, {@code JMSDeliveryMode}, 
  * {@code JMSExpiration}, {@code JMSPriority}, 
  * {@code JMSMessageID} and {@code JMSTimeStamp}.
  * When the message is sent, the values of these headers are ignored. 
  * After the completion of the {@code send}, the headers hold the values 
  * specified by the method sending the message. It is possible for the 
  * {@code send} method not to set {@code JMSMessageID} and 
  * {@code JMSTimeStamp} if the 
  * setting of these headers is explicitly disabled by the 
  * {@code MessageProducer.setDisableMessageID} or
  * {@code MessageProducer.setDisableMessageTimestamp} method.
  *
  * <P>Creating a {@code MessageProducer} provides the same features as
  * creating a {@code QueueSender}. A {@code MessageProducer} object is 
  * recommended when creating new code. The  {@code QueueSender} is
  * provided to support existing code.
  *
  * @see javax.jms.MessageProducer
  * @see javax.jms.Session#createProducer(Destination)
  * @see javax.jms.QueueSession#createSender(Queue)
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface QueueSender extends MessageProducer {

    /** Gets the queue associated with this {@code QueueSender}.
      *  
      * @return this sender's queue 
      *  
      * @exception JMSException if the JMS provider fails to get the queue for
      *                         this {@code QueueSender}
      *                         due to some internal error.
      */ 
 
    Queue
    getQueue() throws JMSException;


    /** Sends a message to the queue. Uses the {@code QueueSender}'s 
      * default delivery mode, priority, and time to live.
      *
      * @param message the message to send 
      *  
      * @exception JMSException if the JMS provider fails to send the message 
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with a {@code QueueSender} with
      *                         an invalid queue.
      * @exception java.lang.UnsupportedOperationException if a client uses this
      *                         method with a {@code QueueSender} that did
      *                         not specify a queue at creation time.
      * 
      * @see javax.jms.MessageProducer#getDeliveryMode()
      * @see javax.jms.MessageProducer#getTimeToLive()
      * @see javax.jms.MessageProducer#getPriority()
      */

    void 
    send(Message message) throws JMSException;


    /** Sends a message to the queue, specifying delivery mode, priority, and 
      * time to live.
      *
      * @param message the message to send
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds)
      *  
      * @exception JMSException if the JMS provider fails to send the message 
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with a {@code QueueSender} with
      *                         an invalid queue.
      * @exception java.lang.UnsupportedOperationException if a client uses this
      *                         method with a {@code QueueSender} that did
      *                         not specify a queue at creation time.
      */

    void 
    send(Message message, 
	 int deliveryMode, 
	 int priority,
	 long timeToLive) throws JMSException;


    /** Sends a message to a queue for an unidentified message producer.
      * Uses the {@code QueueSender}'s default delivery mode, priority,
      * and time to live.
      *
      * <P>Typically, a message producer is assigned a queue at creation 
      * time; however, the JMS API also supports unidentified message producers,
      * which require that the queue be supplied every time a message is
      * sent.
      *  
      * @param queue the queue to send this message to
      * @param message the message to send
      *  
      * @exception JMSException if the JMS provider fails to send the message 
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid queue.
      * 
      * @see javax.jms.MessageProducer#getDeliveryMode()
      * @see javax.jms.MessageProducer#getTimeToLive()
      * @see javax.jms.MessageProducer#getPriority()
      */ 
 
    void
    send(Queue queue, Message message) throws JMSException;
 
 
    /** Sends a message to a queue for an unidentified message producer, 
      * specifying delivery mode, priority and time to live.
      *  
      * <P>Typically, a message producer is assigned a queue at creation 
      * time; however, the JMS API also supports unidentified message producers,
      * which require that the queue be supplied every time a message is
      * sent.
      *  
      * @param queue the queue to send this message to
      * @param message the message to send
      * @param deliveryMode the delivery mode to use
      * @param priority the priority for this message
      * @param timeToLive the message's lifetime (in milliseconds)
      *  
      * @exception JMSException if the JMS provider fails to send the message 
      *                         due to some internal error.
      * @exception MessageFormatException if an invalid message is specified.
      * @exception InvalidDestinationException if a client uses
      *                         this method with an invalid queue.
      */ 

    void
    send(Queue queue, 
	 Message message, 
	 int deliveryMode, 
	 int priority,
	 long timeToLive) throws JMSException;
}
