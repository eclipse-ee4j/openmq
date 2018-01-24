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


/** A client uses a {@code QueueReceiver} object to receive messages that 
  * have been delivered to a queue.
  *
  * <P>Although it is possible to have multiple {@code QueueReceiver}s 
  * for the same queue, the JMS API does not define how messages are 
  * distributed between the {@code QueueReceiver}s.
  *
  * <P>If a {@code QueueReceiver} specifies a message selector, the 
  * messages that are not selected remain on the queue. By definition, a message
  * selector allows a {@code QueueReceiver} to skip messages. This 
  * means that when the skipped messages are eventually read, the total ordering
  * of the reads does not retain the partial order defined by each message 
  * producer. Only {@code QueueReceiver}s without a message selector
  * will read messages in message producer order.
  *
  * <P>Creating a {@code MessageConsumer} provides the same features as
  * creating a {@code QueueReceiver}. A {@code MessageConsumer} object is 
  * recommended for creating new code. The  {@code QueueReceiver} is
  * provided to support existing code.
  *
  * @see         javax.jms.Session#createConsumer(Destination, String)
  * @see         javax.jms.Session#createConsumer(Destination)
  * @see         javax.jms.QueueSession#createReceiver(Queue, String)
  * @see         javax.jms.QueueSession#createReceiver(Queue)
  * @see         javax.jms.MessageConsumer
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface QueueReceiver extends MessageConsumer {

    /** Gets the {@code Queue} associated with this queue receiver.
      *  
      * @return this receiver's {@code Queue} 
      *  
      * @exception JMSException if the JMS provider fails to get the queue for
      *                         this queue receiver
      *                         due to some internal error.
      */ 
 
    Queue
    getQueue() throws JMSException;
}
