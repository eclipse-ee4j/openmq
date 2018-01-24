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
 * @(#)QueueReceiverImpl.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;

/** A client uses a QueueReceiver for receiving messages that have been
  * delivered to a queue.
  *
  * <P>Although it is possible to have multiple QueueReceivers for the same queue,
  * JMS does not define how messages are distributed between the QueueReceivers.
  *
  * @see         javax.jms.QueueSession#createReceiver(Queue, String)
  * @see         javax.jms.QueueSession#createReceiver(Queue)
  * @see         javax.jms.MessageConsumer
  */

public class QueueReceiverImpl extends MessageConsumerImpl implements QueueReceiver{

    private Queue queue = null;


    public QueueReceiverImpl (SessionImpl session, Queue queue)
                              throws JMSException {

        super (session, queue);
        this.queue = queue;
        //setIsTopic ( false );
        init(); //register interest
    }

    public QueueReceiverImpl (SessionImpl session, Queue queue,
                              String selector) throws JMSException {
        //isTopic is false;
        super (session, queue);
        this.queue = queue;
        //setIsTopic ( false );
        setMessageSelector ( selector );
        init(); //register interest
    }

    /** Get the queue associated with this queue receiver.
      *
      * @return the queue
      *
      * @exception JMSException if JMS fails to get queue for
      *                         this queue receiver
      *                         due to some internal error.
      */

    public Queue
    getQueue() throws JMSException {
        checkState();
        return queue;
    }
}
