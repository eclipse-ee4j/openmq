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

/** A client uses a {@code MessageConsumer} object to receive messages 
 * from a destination.  A {@code MessageConsumer} object is created by 
 * passing a {@code Destination} object to a message-consumer creation
 * method supplied by a session.
 *
 * <P>{@code MessageConsumer} is the parent interface for all message 
 * consumers.
 *
 * <P>A {@code MessageConsumer} can be created with a message selector. A message
 * selector allows 
 * the client to restrict the messages delivered to the message consumer to 
 * those that match the selector.
 * <p>
 * A client may either synchronously receive a {@code MessageConsumer}'s 
 * messages or have the {@code MessageConsumer} asynchronously deliver them 
 * as they arrive. 
 * <p>
 * For synchronous receipt, a client can request the next message from a 
 * {@code MessageConsumer} using one of its {@code receive} methods. There are several 
 * variations of {@code receive} that allow a client to poll or wait for the next message. 
 * <p>
 * For asynchronous delivery, a client can register a {@code MessageListener} object 
 * with a {@code MessageConsumer}.
 * As messages arrive at the {@code MessageConsumer}, it delivers them by calling 
 * the {@code MessageListener}'s {@code onMessage} method.
 * <p>
 * It is a client programming error for a {@code MessageListener} to throw an exception.
 *
 * @see         javax.jms.QueueReceiver
 * @see         javax.jms.TopicSubscriber
 * @see         javax.jms.Session
 * 
 * @version JMS 2.0
 * @since JMS 1.0
 */
public interface MessageConsumer extends AutoCloseable{

    /** Gets this message consumer's message selector expression.
     *  
      * @return this message consumer's message selector, or null if no
      *         message selector exists for the message consumer (that is, if 
      *         the message selector was not set or was set to null or the 
      *         empty string)
      *  
      * @exception JMSException if the JMS provider fails to get the message
      *                         selector due to some internal error.
      */ 

    String
    getMessageSelector() throws JMSException;


    /** Gets the {@code MessageConsumer}'s {@code MessageListener}. 
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSException} to be thrown though this is not guaranteed.
     * 
     * @return the {@code MessageConsumer}'s {@code MessageListener}, or null if one was not set
     *  
     * @exception JMSException if the JMS provider fails to get the {@code MessageListener}
     *                         for one of the following reasons:
     *                         <ul>
     *                         <li>an internal error has occurred or
     *                         <li>this method has been called in a Java EE web or EJB application 
     *                         (though it is not guaranteed that an exception is thrown in this case)
     *                         </ul>                      
     *                         
     * @see javax.jms.MessageConsumer#setMessageListener(javax.jms.MessageListener)
     */
    MessageListener getMessageListener() throws JMSException;
    
    /** Sets the {@code MessageConsumer}'s {@code MessageListener}.
     * <p>
     * Setting the the {@code MessageListener} to null is the equivalent of 
     * unsetting the {@code MessageListener} for the {@code MessageConsumer}. 
     * <p>
     * The effect of calling this method
     * while messages are being consumed by an existing listener
     * or the {@code MessageConsumer} is being used to consume messages synchronously
     * is undefined.
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSException} to be thrown though this is not guaranteed.
     * 
     * @param listener the listener to which the messages are to be 
     *                 delivered
     *  
     * @exception JMSException if the JMS provider fails to set the {@code MessageConsumer}'s {@code MessageListener}
     *                         for one of the following reasons:
     *                         <ul>
     *                         <li>an internal error has occurred or  
     *                         <li>this method has been called in a Java EE web or EJB application 
     *                         (though it is not guaranteed that an exception is thrown in this case)
     *                         </ul>    
     *                         
     * @see javax.jms.MessageConsumer#getMessageListener()
     */ 
    void setMessageListener(MessageListener listener) throws JMSException;
           
    /** Receives the next message produced for this message consumer.
      *  
      * <P>This call blocks indefinitely until a message is produced
      * or until this message consumer is closed.
      *
      * <P>If this {@code receive} is done within a transaction, the 
      * consumer retains the message until the transaction commits.
      *  
      * @return the next message produced for this message consumer, or 
      * null if this message consumer is concurrently closed
      *  
      * @exception JMSException if the JMS provider fails to receive the next
      *                         message due to some internal error.
      * 
      */ 
 
    Message
    receive() throws JMSException;


    /** Receives the next message that arrives within the specified
      * timeout interval.
      *  
      * <P>This call blocks until a message arrives, the
      * timeout expires, or this message consumer is closed.
      * A {@code timeout} of zero never expires, and the call blocks 
      * indefinitely.
      *
      * @param timeout the timeout value (in milliseconds)
      *
      * @return the next message produced for this message consumer, or 
      * null if the timeout expires or this message consumer is concurrently 
      * closed
      *
      * @exception JMSException if the JMS provider fails to receive the next
      *                         message due to some internal error.
      */ 

    Message
    receive(long timeout) throws JMSException;


    /** Receives the next message if one is immediately available.
      *
      * @return the next message produced for this message consumer, or 
      * null if one is not available
      *  
      * @exception JMSException if the JMS provider fails to receive the next
      *                         message due to some internal error.
      */ 

    Message
    receiveNoWait() throws JMSException;


	/**
	 * Closes the message consumer.
	 * <p>
	 * Since a provider may allocate some resources on behalf of a
	 * {@code MessageConsumer} outside the Java virtual machine, clients should
	 * close them when they are not needed. Relying on garbage collection to
	 * eventually reclaim these resources may not be timely enough.
	 * <P>
	 * This call will block until a {@code receive} call in progress on this
	 * consumer has completed. A blocked {@code receive} call returns null when
	 * this message consumer is closed.
	 * <p>
	 * If this method is called whilst a message listener is in progress in
	 * another thread then it will block until the message listener has
	 * completed.
	 * <p>
	 * This method may be called from a message listener's {@code onMessage}
	 * method on its own consumer. After this method returns the
	 * {@code onMessage} method will be allowed to complete normally. 
	 * <p>
	 * This method is the only {@code MessageConsumer} method that can be called
	 * concurrently.
	 * 
	 * @exception JMSException
	 *                if the JMS provider fails to close the consumer due to
	 *                some internal error.
	 */

	void close() throws JMSException;
}
