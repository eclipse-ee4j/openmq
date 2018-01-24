/*
 * Copyright (c) 2011, 2017 Oracle and/or its affiliates. All rights reserved.
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
 
/**
 * A client using the simplified JMS API introduced for JMS 2.0 uses a
 * {@code JMSConsumer} object to receive messages from a queue or topic. A
 * {@code JMSConsumer} object may be created either created by passing a
 * {@code Queue} or {@code Topic} object to one of the {@code createConsumer}
 * methods on a {@code JMSContext}. or by passing a {@code Topic} object to one
 * of the {@code createSharedConsumer} or {@code createDurableConsumer} methods
 * on a {@code JMSContext}.
 * <P>
 * A {@code JMSConsumer} can be created with a message selector. A message
 * selector allows the client to restrict the messages delivered to the
 * {@code JMSConsumer} to those that match the selector.
 * <p>
 * A client may either synchronously receive a {@code JMSConsumer}'s messages or
 * have the {@code JMSConsumer} asynchronously deliver them as they arrive.
 * <p>
 * For synchronous receipt, a client can request the next message from a
 * {@code JMSConsumer} using one of its {@code receive} methods. There are
 * several variations of {@code receive} that allow a client to poll or wait for
 * the next message.
 * <p>
 * For asynchronous delivery, a client can register a {@code MessageListener}
 * object with a {@code JMSConsumer}. As messages arrive at the
 * {@code JMSConsumer}, it delivers them by calling the {@code MessageListener}
 * 's {@code onMessage} method.
 * <p>
 * It is a client programming error for a {@code MessageListener} to throw an
 * exception.
 * 
 * @see javax.jms.JMSContext
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 */

public interface JMSConsumer extends AutoCloseable {

    /** Gets this {@code JMSConsumer}'s message selector expression.
      *  
      * @return this {@code JMSConsumer}'s message selector, or null if no
      *         message selector exists for the {@code JMSConsumer} (that is, if 
      *         the message selector was not set or was set to null or the 
      *         empty string)
      *  
      * @exception JMSRuntimeException if the JMS provider fails to get the message
      *            selector due to some internal error.
      */ 

    String getMessageSelector();
    
    /** Gets the {@code JMSConsumer}'s {@code MessageListener}. 
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSRuntimeException} to be thrown though this is not guaranteed.
     * 
      * @return the {@code JMSConsumer}'s {@code MessageListener}, or null if one was not set
      *  
      * @exception JMSRuntimeException if the JMS provider fails to get the {@code MessageListener}
      *                         for one of the following reasons:
      *                         <ul>
      *                         <li>an internal error has occurred or
      *                         <li>this method has been called in a Java EE web or EJB application 
      *                         (though it is not guaranteed that an exception is thrown in this case)
      *                         </ul>                      
      *                         
      * @see javax.jms.JMSConsumer#setMessageListener(javax.jms.MessageListener)
      */ 
    MessageListener getMessageListener() throws JMSRuntimeException;
    
    /** Sets the {@code JMSConsumer}'s {@code MessageListener}.
      * <p>
      * Setting the {@code MessageListener} to null is the equivalent of 
      * unsetting the {@code MessageListener} for the {@code JMSConsumer}. 
      * <p>
      * The effect of calling this method
      * while messages are being consumed by an existing listener
      * or the {@code JMSConsumer} is being used to consume messages synchronously
      * is undefined.
      * <p>
      * This method must not be used in a Java EE web or EJB application. 
      * Doing so may cause a {@code JMSRuntimeException} to be thrown though this is not guaranteed.
      * 
      * @param listener the listener to which the messages are to be 
      *                 delivered
      *  
      * @exception JMSRuntimeException if the JMS provider fails to set the {@code JMSConsumer}'s {@code MessageListener}
      *                         for one of the following reasons:
      *                         <ul>
      *                         <li>an internal error has occurred or  
      *                         <li>this method has been called in a Java EE web or EJB application 
      *                         (though it is not guaranteed that an exception is thrown in this case)
      *                         </ul>    
      *                         
      * @see javax.jms.JMSConsumer#getMessageListener()
      */ 
    void setMessageListener(MessageListener listener) throws JMSRuntimeException;
    

    /** Receives the next message produced for this {@code JMSConsumer}.
      *  
      * <P>This call blocks indefinitely until a message is produced
      * or until this {@code JMSConsumer} is closed.
      *
      * <P>If this {@code receive} is done within a transaction, the 
      * JMSConsumer retains the message until the transaction commits.
      *  
      * @return the next message produced for this {@code JMSConsumer}, or 
      * null if this {@code JMSConsumer} is concurrently closed
      *  
      * @exception JMSRuntimeException if the JMS provider fails to receive the next
      *            message due to some internal error.
      * 
      */ 
 
    Message receive();


    /** Receives the next message that arrives within the specified
      * timeout interval.
      *  
      * <P>This call blocks until a message arrives, the
      * timeout expires, or this {@code JMSConsumer} is closed.
      * A {@code timeout} of zero never expires, and the call blocks 
      * indefinitely.
      *
      * @param timeout the timeout value (in milliseconds)
      *
      * @return the next message produced for this {@code JMSConsumer}, or 
      * null if the timeout expires or this {@code JMSConsumer} is concurrently 
      * closed
      *
      * @exception JMSRuntimeException if the JMS provider fails to receive the next
      *            message due to some internal error.
      */ 

    Message receive(long timeout);


    /** Receives the next message if one is immediately available.
      *
      * @return the next message produced for this {@code JMSConsumer}, or 
      * null if one is not available
      *  
      * @exception JMSRuntimeException if the JMS provider fails to receive the next
      *            message due to some internal error.
      */ 

    Message receiveNoWait();


	/**
	 * Closes the {@code JMSConsumer}.
	 * <p>
	 * Since a provider may allocate some resources on behalf of a
	 * {@code JMSConsumer} outside the Java virtual machine, clients should
	 * close them when they are not needed. Relying on garbage collection to
	 * eventually reclaim these resources may not be timely enough.
	 * <P>
	 * This call will block until a {@code receive} call in progress on this
	 * consumer has completed. A blocked {@code receive} call returns null when
	 * this consumer is closed.
	 * <p>
	 * If this method is called whilst a message listener is in progress in
	 * another thread then it will block until the message listener has
	 * completed.
	 * <p>
	 * This method may be called from a message listener's {@code onMessage}
	 * method on its own consumer. After this method returns the
	 * {@code onMessage} method will be allowed to complete normally.
	 * <p>
	 * This method is the only {@code JMSConsumer} method that can be called
	 * concurrently.
	 * 
	 * @exception JMSRuntimeException
	 *                if the JMS provider fails to close the consumer due to
	 *                some internal error.
	 */

	void close();
    
	/**
	 * Receives the next message produced for this {@code JMSConsumer} and
	 * returns its body as an object of the specified type. 
	 * This method may be used to receive any type of message except 
	 * for {@code StreamMessage} and {@code Message}, so long as the message
	 * has a body which is capable of being assigned to the specified type.
	 * This means that the specified class or interface must either be the same
	 * as, or a superclass or superinterface of, the class of the message body. 
	 * If the message is not one of the supported types, 
	 * or its body cannot be assigned to the specified type, or it has no body, 
	 * then a {@code MessageFormatRuntimeException} is thrown.
	 * <p>
	 * This method does not give access to the message headers or properties
	 * (such as the {@code JMSRedelivered} message header field or the
	 * {@code JMSXDeliveryCount} message property) and should only be used if
	 * the application has no need to access them.
	 * <P>
	 * This call blocks indefinitely until a message is produced or until this
	 * {@code JMSConsumer} is closed.
	 * <p>
	 * If this method is called within a transaction, the
	 * {@code JMSConsumer} retains the message until the transaction commits.
	 * <p>
	 * The result of this method throwing a
	 * {@code MessageFormatRuntimeException} depends on the session mode:
	 * <ul>
	 * <li>{@code AUTO_ACKNOWLEDGE} or {@code DUPS_OK_ACKNOWLEDGE}: The JMS
	 * provider will behave as if the unsuccessful call to {@code receiveBody} had
	 * not occurred. The message will be delivered again before any subsequent
	 * messages.
	 * <p>
	 * This is not considered to be redelivery and does not cause the
	 * {@code JMSRedelivered} message header field to be set or the
	 * {@code JMSXDeliveryCount} message property to be incremented.</li>
	 * <li>{@code CLIENT_ACKNOWLEDGE}: The JMS provider will behave as if the
	 * call to {@code receiveBody} had been successful and will not deliver the
	 * message again.
	 * <p>
	 * As with any message that is delivered with a session mode of
	 * {@code CLIENT_ACKNOWLEDGE}, the message will not be acknowledged until
	 * {@code acknowledge} is called on the {@code JMSContext}. If an
	 * application wishes to have the failed message redelivered, it must call
	 * {@code recover} on the {@code JMSContext}. The redelivered message's
	 * {@code JMSRedelivered} message header field will be set and its
	 * {@code JMSXDeliveryCount} message property will be incremented.</li>
	 * 
	 * <li>Transacted session: The JMS provider will behave as if the call to
	 * {@code receiveBody} had been successful and will not deliver the message
	 * again.
	 * <p>
	 * As with any message that is delivered in a transacted session, the
	 * transaction will remain uncommitted until the transaction is committed or
	 * rolled back by the application. If an application wishes to have the
	 * failed message redelivered, it must roll back the transaction. The
	 * redelivered message's {@code JMSRedelivered} message header field will be
	 * set and its {@code JMSXDeliveryCount} message property will be
	 * incremented.</li>
	 * </ul>
	 * 
	 * @param c
	 *            The type to which the body of the next message should be
	 *            assigned.<br>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br>
	 * 
	 * @return the body of the next message produced for this
	 *         {@code JMSConsumer}, or null if this {@code JMSConsumer} is
	 *         concurrently closed
	 * 
	 * @throws MessageFormatRuntimeException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to receive the next message due to
	 *             some internal error
	 */
	<T> T receiveBody(Class<T> c);
	
	/**
	 * Receives the next message produced for this {@code JMSConsumer} 
	 * that arrives within the specified timeout period and
	 * returns its body as an object of the specified type. 
	 * This method may be used to receive any type of message except 
	 * for {@code StreamMessage} and {@code Message}, so long as the message
	 * has a body which is capable of being assigned to the specified type.
	 * This means that the specified class or interface must either be the same
	 * as, or a superclass or superinterface of, the class of the message body. 
	 * If the message is not one of the supported types, 
	 * or its body cannot be assigned to the specified type, or it has no body, 
	 * then a {@code MessageFormatRuntimeException} is thrown.
	 * <p>
	 * This method does not give access to the message headers or properties
	 * (such as the {@code JMSRedelivered} message header field or the
	 * {@code JMSXDeliveryCount} message property) and should only be used if
	 * the application has no need to access them.
	 * <P>
	 * This call blocks until a message arrives, the timeout expires, or this
	 * {@code JMSConsumer} is closed. A timeout of zero never expires, and the
	 * call blocks indefinitely.
	 * <p>
	 * If this method is called within a transaction, the
	 * {@code JMSConsumer} retains the message until the transaction commits.
	 * <p>
	 * The result of this method throwing a
	 * {@code MessageFormatRuntimeException} depends on the session mode:
	 * <ul>
	 * <li>{@code AUTO_ACKNOWLEDGE} or {@code DUPS_OK_ACKNOWLEDGE}: The JMS
	 * provider will behave as if the unsuccessful call to {@code receiveBody} had
	 * not occurred. The message will be delivered again before any subsequent
	 * messages.
	 * <p>
	 * This is not considered to be redelivery and does not cause the
	 * {@code JMSRedelivered} message header field to be set or the
	 * {@code JMSXDeliveryCount} message property to be incremented.</li>
	 * <li>{@code CLIENT_ACKNOWLEDGE}: The JMS provider will behave as if the
	 * call to {@code receiveBody} had been successful and will not deliver the
	 * message again.
	 * <p>
	 * As with any message that is delivered with a session mode of
	 * {@code CLIENT_ACKNOWLEDGE}, the message will not be acknowledged until
	 * {@code acknowledge} is called on the {@code JMSContext}. If an
	 * application wishes to have the failed message redelivered, it must call
	 * {@code recover} on the {@code JMSContext}. The redelivered message's
	 * {@code JMSRedelivered} message header field will be set and its
	 * {@code JMSXDeliveryCount} message property will be incremented.</li>
	 * 
	 * <li>Transacted session: The JMS provider will behave as if the call to
	 * {@code receiveBody} had been successful and will not deliver the message
	 * again.
	 * <p>
	 * As with any message that is delivered in a transacted session, the
	 * transaction will remain uncommitted until the transaction is committed or
	 * rolled back by the application. If an application wishes to have the
	 * failed message redelivered, it must roll back the transaction. The
	 * redelivered message's {@code JMSRedelivered} message header field will be
	 * set and its {@code JMSXDeliveryCount} message property will be
	 * incremented.</li>
	 * </ul>
	 * 
	 * @param c
	 *            The type to which the body of the next message should be
	 *            assigned.<br>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br>
	 * 
	 * @return the body of the next message produced for this {@code JMSConsumer},
	 *         or null if the timeout expires or this {@code JMSConsumer} is concurrently closed
	 * 
	 * @throws MessageFormatRuntimeException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to receive the next message due
	 *             to some internal error
	 */
    <T> T receiveBody(Class<T> c, long timeout);
    
    
	/**
	 * Receives the next message produced for this {@code JMSConsumer} 
	 * if one is immediately available and
	 * returns its body as an object of the specified type. 
	 * This method may be used to receive any type of message except 
	 * for {@code StreamMessage} and {@code Message}, so long as the message
	 * has a body which is capable of being assigned to the specified type.
	 * This means that the specified class or interface must either be the same
	 * as, or a superclass or superinterface of, the class of the message body. 
	 * If the message is not one of the supported types, 
	 * or its body cannot be assigned to the specified type, or it has no body, 
	 * then a {@code MessageFormatRuntimeException} is thrown.
	 * <p>
	 * This method does not give access to the message headers or properties
	 * (such as the {@code JMSRedelivered} message header field or the
	 * {@code JMSXDeliveryCount} message property) and should only be used if
	 * the application has no need to access them.
	 * <P>
	 * If a message is not immediately available null is returned. 
	 * <p>
	 * If this method is called within a transaction, the
	 * {@code JMSConsumer} retains the message until the transaction commits.
	 * <p>
	 * The result of this method throwing a
	 * {@code MessageFormatRuntimeException} depends on the session mode:
	 * <ul>
	 * <li>{@code AUTO_ACKNOWLEDGE} or {@code DUPS_OK_ACKNOWLEDGE}: The JMS
	 * provider will behave as if the unsuccessful call to {@code receiveBodyNoWait} had
	 * not occurred. The message will be delivered again before any subsequent
	 * messages.
	 * <p>
	 * This is not considered to be redelivery and does not cause the
	 * {@code JMSRedelivered} message header field to be set or the
	 * {@code JMSXDeliveryCount} message property to be incremented.</li>
	 * <li>{@code CLIENT_ACKNOWLEDGE}: The JMS provider will behave as if the
	 * call to {@code receiveBodyNoWait} had been successful and will not deliver the
	 * message again.
	 * <p>
	 * As with any message that is delivered with a session mode of
	 * {@code CLIENT_ACKNOWLEDGE}, the message will not be acknowledged until
	 * {@code acknowledge} is called on the {@code JMSContext}. If an
	 * application wishes to have the failed message redelivered, it must call
	 * {@code recover} on the {@code JMSContext}. The redelivered message's
	 * {@code JMSRedelivered} message header field will be set and its
	 * {@code JMSXDeliveryCount} message property will be incremented.</li>
	 * 
	 * <li>Transacted session: The JMS provider will behave as if the call to
	 * {@code receiveBodyNoWait} had been successful and will not deliver the message
	 * again.
	 * <p>
	 * As with any message that is delivered in a transacted session, the
	 * transaction will remain uncommitted until the transaction is committed or
	 * rolled back by the application. If an application wishes to have the
	 * failed message redelivered, it must roll back the transaction. The
	 * redelivered message's {@code JMSRedelivered} message header field will be
	 * set and its {@code JMSXDeliveryCount} message property will be
	 * incremented.</li>
	 * </ul>
	 * 
	 * @param c
	 *            The type to which the body of the next message should be
	 *            assigned.<br>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br>
	 * 
	 * @return the body of the next message produced for this {@code JMSConsumer},
	 *         or null if one is not immediately available or this {@code JMSConsumer} is concurrently closed
	 * 
	 * @throws MessageFormatRuntimeException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 *             
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to receive the next message due
	 *             to some internal error

	 */
    <T> T receiveBodyNoWait(Class<T> c);
    
}
