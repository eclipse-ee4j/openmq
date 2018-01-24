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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * A {@code JMSProducer} is a simple object used to send messages on behalf
 * of a {@code JMSContext}. An instance of {@code JMSProducer} is
 * created by calling the {@code createProducer} method on a
 * {@code JMSContext}. It provides various {@code send} methods to
 * send a message to a specified destination. It also provides methods to allow
 * message send options, message properties and message headers to be specified
 * prior to sending a message or set of messages.
 * <p>
 * Message send options may be specified using one or more of the following
 * methods: {@code setDeliveryMode}, {@code setPriority},
 * {@code setTimeToLive}, {@code setDeliveryDelay},
 * {@code setDisableMessageTimestamp}, {@code setDisableMessageID} and
 * {@code setAsync}.
 * <p>
 * Message properties may be may be specified using one or more of nine
 * {@code setProperty} methods. Any message properties set using these
 * methods will override any message properties that have been set directly on
 * the message.
 * <p>
 * Message headers may be specified using one or more of the following methods:
 * {@code setJMSCorrelationID}, {@code setJMSCorrelationIDAsBytes},
 * {@code setJMSType} or {@code setJMSReplyTo}. Any message headers
 * set using these methods will override any message headers that have been set
 * directly on the message.
 * <p>
 * All the above methods return the {@code JMSProducer} to allow method
 * calls to be chained together, allowing a fluid programming style. For
 * example:
 * <p>
 * <tt>context.createProducer().setDeliveryMode(DeliveryMode.NON_PERSISTENT).setTimeToLive(1000).send(destination, message);</tt>
 * <p>
 * Instances of {@code JMSProducer} are intended to be lightweight objects
 * which can be created freely and which do not consume significant resources.
 * This interface therefore does not provide a {@code close} method.
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 */

public interface JMSProducer {
	
	/**
	 * Sends a message to the specified destination, using any send options,
	 * message properties and message headers that have been defined on this
	 * {@code JMSProducer}.
	 * 
	 * @param destination
	 *            the destination to send this message to
	 * @param message
	 *            the message to send
	 * @return this {@code JMSProducer}
	 * @throws MessageFormatRuntimeException
	 *             if an invalid message is specified.
	 * @throws InvalidDestinationRuntimeException
	 *             if a client uses this method with an invalid destination.
	 * @throws MessageNotWriteableRuntimeException
	 *             if this {@code JMSProducer} has been configured to set a
	 *             message property, but the message's properties are read-only
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to send the message due to some
	 *             internal error.
	 */
	JMSProducer send(Destination destination, Message message);

	/**
	 * Send a {@code TextMessage} with the specified body to the
	 * specified destination, using any send options, message properties and
	 * message headers that have been defined on this {@code JMSProducer}.
	 * 
	 * @param destination
	 *            the destination to send this message to
	 * @param body
	 *            the body of the {@code TextMessage} that will be sent. 
	 *            If a null value is specified then a {@code TextMessage} 
	 *            with no body will be sent.
	 * @return this {@code JMSProducer}
	 * @throws MessageFormatRuntimeException
	 *             if an invalid message is specified.
	 * @throws InvalidDestinationRuntimeException
	 *             if a client uses this method with an invalid destination.
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to send the message due to some
	 *             internal error.
	 */
	JMSProducer send(Destination destination, String body);

	/**
	 * Send a {@code MapMessage} with the specified body to the
	 * specified destination, using any send options, message properties and
	 * message headers that have been defined on this {@code JMSProducer}.
	 * 
	 * @param destination
	 *            the destination to send this message to
	 * @param body
	 *            the body of the {@code MapMessage} that will be sent.
	 *            If a null value is specified then a {@code MapMessage} 
	 *            with no map entries will be sent.
	 * @return this {@code JMSProducer}
	 * @throws MessageFormatRuntimeException
	 *             if an invalid message is specified.
	 * @throws InvalidDestinationRuntimeException
	 *             if a client uses this method with an invalid destination.
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to send the message due to some
	 *             internal error.
	 */
	JMSProducer send(Destination destination, Map<String, Object> body);

	/**
	 * Send a {@code BytesMessage} with the specified body to the
	 * specified destination, using any send options, message properties and
	 * message headers that have been defined on this {@code JMSProducer}.
	 * 
	 * @param destination
	 *            the destination to send this message to
	 * @param body
	 *            the body of the {@code BytesMessage} that will be
	 *            sent.
	 *            If a null value is specified then a {@code BytesMessage} 
	 *            with no body will be sent.
	 * @return this {@code JMSProducer}
	 * @throws MessageFormatRuntimeException
	 *             if an invalid message is specified.
	 * @throws InvalidDestinationRuntimeException
	 *             if a client uses this method with an invalid destination.
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to send the message due to some
	 *             internal error.
	 */
	JMSProducer send(Destination destination, byte[] body);

	/**
	 * Send an {@code ObjectMessage} with the specified body to the
	 * specified destination, using any send options, message properties and
	 * message headers that have been defined on this {@code JMSProducer}.
	 * 
	 * @param destination
	 *            the destination to send this message to
	 * @param body
	 *            the body of the ObjectMessage that will be sent.
	 *            If a null value is specified then an {@code ObjectMessage} 
	 *            with no body will be sent.
	 * @return this {@code JMSProducer}
	 * @throws MessageFormatRuntimeException
	 *             if an invalid message is specified.
	 * @throws InvalidDestinationRuntimeException
	 *             if a client uses this method with an invalid destination.
	 * @throws JMSRuntimeException
	 *             if JMS provider fails to send the message due to some
	 *             internal error.
	 */
	JMSProducer send(Destination destination, Serializable body);

	/**
	 * Specifies whether message IDs may be disabled for messages that are sent
	 * using this {@code JMSProducer}
	 * <p>
	 * Since message IDs take some effort to create and increase a message's
	 * size, some JMS providers may be able to optimise message overhead if they
	 * are given a hint that the message ID is not used by an application. By
	 * calling this method, a JMS application enables this potential
	 * optimisation for all messages sent using this {@code JMSProducer}.
	 * If the JMS provider accepts this hint, these messages must have the
	 * message ID set to null; if the provider ignores the hint, the message ID
	 * must be set to its normal unique value.
	 * <p>
	 * Message IDs are enabled by default.
	 * <p>
	 * 
	 * @param value
	 *            indicates whether message IDs may be disabled
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set message ID to disabled due
	 *             to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getDisableMessageID
	 */
	JMSProducer setDisableMessageID(boolean value);

	/**
	 * Gets an indication of whether message IDs are disabled.
	 * 
	 * @return an indication of whether message IDs are disabled
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to determine if message IDs are
	 *             disabled due to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setDisableMessageID
	 */

	boolean getDisableMessageID();

	/**
	 * Specifies whether message timestamps may be disabled for messages that
	 * are sent using this {@code JMSProducer}. <pP> Since timestamps take
	 * some effort to create and increase a message's size, some JMS providers
	 * may be able to optimise message overhead if they are given a hint that
	 * the timestamp is not used by an application. By calling this method, a
	 * JMS application enables this potential optimisation for all messages sent
	 * using this {@code JMSProducer}. If the JMS provider accepts this
	 * hint, these messages must have the timestamp set to zero; if the provider
	 * ignores the hint, the timestamp must be set to its normal value.
	 * <p>
	 * Message timestamps are enabled by default.
	 * 
	 * @param value
	 *            indicates whether message timestamps may be disabled
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set timestamps to disabled due
	 *             to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getDisableMessageTimestamp
	 */
	JMSProducer setDisableMessageTimestamp(boolean value);

	/**
	 * Gets an indication of whether message timestamps are disabled.
	 * 
	 * @return an indication of whether message timestamps are disabled
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to determine if timestamps are
	 *             disabled due to some internal error.
	 * @see javax.jms.JMSProducer#setDisableMessageTimestamp
	 */
	boolean getDisableMessageTimestamp();

	/**
	 * Specifies the delivery mode of messages that are sent using this
	 * {@code JMSProducer}
	 * <p>
	 * Delivery mode is set to {@code PERSISTENT} by default.
	 * 
	 * @param deliveryMode
	 *            the message delivery mode to be used; legal values are
	 *            {@code DeliveryMode.NON_PERSISTENT} and
	 *            {@code DeliveryMode.PERSISTENT}
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the delivery mode due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getDeliveryMode
	 * @see javax.jms.DeliveryMode#NON_PERSISTENT
	 * @see javax.jms.DeliveryMode#PERSISTENT
	 * @see javax.jms.Message#DEFAULT_DELIVERY_MODE
	 */
	JMSProducer setDeliveryMode(int deliveryMode);

	/**
	 * Returns the delivery mode of messages that are sent using this
	 * {@code JMSProducer}
	 * 
	 * @return the message delivery mode
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the delivery mode due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setDeliveryMode
	 */

	int getDeliveryMode();

	/**
	 * Specifies the priority of messages that are sent using this
	 * {@code JMSProducer}
	 * <p>
	 * The JMS API defines ten levels of priority value, with 0 as the lowest
	 * priority and 9 as the highest. Clients should consider priorities 0-4 as
	 * gradations of normal priority and priorities 5-9 as gradations of
	 * expedited priority. Priority is set to 4 by default.
	 * <p>
	 * 
	 * @param priority
	 *            the message priority to be used; must be a value between 0 and
	 *            9
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the priority due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#getPriority
	 * @see javax.jms.Message#DEFAULT_PRIORITY
	 */

	JMSProducer setPriority(int priority);

	/**
	 * Return the priority of messages that are sent using this
	 * {@code JMSProducer}
	 * <p>
	 * 
	 * @return the message priority
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the priority due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#setPriority
	 */

	int getPriority();

	/**
	 * Specifies the time to live of messages that are sent using this
	 * {@code JMSProducer}. This is used to determine the expiration time
	 * of a message.
	 * <p>
	 * The expiration time of a message is the sum of the message's time to live
	 * and the time it is sent. For transacted sends, this is the time the
	 * client sends the message, not the time the transaction is committed.
	 * <p>
	 * Clients should not receive messages that have expired; however, JMS does
	 * not guarantee that this will not happen.
	 * <p>
	 * A JMS provider should do its best to accurately expire messages; however,
	 * JMS does not define the accuracy provided. It is not acceptable to simply
	 * ignore time-to-live.
	 * <p>
	 * Time to live is set to zero by default, which means a message never
	 * expires.
	 * 
	 * @param timeToLive
	 *            the message time to live to be used, in milliseconds; a value
	 *            of zero means that a message never expires.
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the time to live due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#getTimeToLive
	 * @see javax.jms.Message#DEFAULT_TIME_TO_LIVE
	 */
	JMSProducer setTimeToLive(long timeToLive);

	/**
	 * Returns the time to live of messages that are sent using this
	 * {@code JMSProducer}.
	 * 
	 * @return the message time to live in milliseconds; a value of zero means
	 *         that a message never expires.
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the time to live due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#setTimeToLive
	 */

	long getTimeToLive();

	/**
	 * Sets the minimum length of time in milliseconds that must elapse after a
	 * message is sent before the JMS provider may deliver the message to a
	 * consumer.
	 * <p>
	 * For transacted sends, this time starts when the client sends the message,
	 * not when the transaction is committed.
	 * <p>
	 * deliveryDelay is set to zero by default.
	 * 
	 * @param deliveryDelay
	 *            the delivery delay in milliseconds.
	 * @return this {@code JMSProducer}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the delivery delay due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getDeliveryDelay
	 * @see javax.jms.Message#DEFAULT_DELIVERY_DELAY
	 */

	JMSProducer setDeliveryDelay(long deliveryDelay);

	/**
	 * Gets the minimum length of time in milliseconds that must elapse after a
	 * message is sent before the JMS provider may deliver the message to a
	 * consumer.
	 * 
	 * @return the delivery delay in milliseconds.
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the delivery delay due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setDeliveryDelay
	 */
	long getDeliveryDelay();

	/**
	 * Specifies whether subsequent calls to {@code send} on this
	 * {@code JMSProducer} object should be synchronous or asynchronous. If
	 * the specified {@code CompletionListener} is not null then subsequent
	 * calls to {@code send} will be asynchronous. If the specified
	 * {@code CompletionListener} is null then subsequent calls to
	 * {@code send} will be synchronous. Calls to {@code send} are
	 * synchronous by default.
	 * <p>
	 * If a call to {@code send} is asynchronous then part of the work
	 * involved in sending the message will be performed in a separate thread
	 * and the specified <tt>CompletionListener</tt> will be notified when the
	 * operation has completed.
	 * <p>
	 * When the message has been successfully sent the JMS provider invokes the
	 * callback method <tt>onCompletion</tt> on the <tt>CompletionListener</tt>
	 * object. Only when that callback has been invoked can the application be
	 * sure that the message has been successfully sent with the same degree of
	 * confidence as if the send had been synchronous. An application which
	 * requires this degree of confidence must therefore wait for the callback
	 * to be invoked before continuing.
	 * <p>
	 * The following information is intended to give an indication of how an
	 * asynchronous send would typically be implemented.
	 * <p>
	 * In some JMS providers, a normal synchronous send involves sending the
	 * message to a remote JMS server and then waiting for an acknowledgement to
	 * be received before returning. It is expected that such a provider would
	 * implement an asynchronous send by sending the message to the remote JMS
	 * server and then returning without waiting for an acknowledgement. When
	 * the acknowledgement is received, the JMS provider would notify the
	 * application by invoking the <tt>onCompletion</tt> method on the
	 * application-specified <tt>CompletionListener</tt> object. If for some
	 * reason the acknowledgement is not received the JMS provider would notify
	 * the application by invoking the <tt>CompletionListener</tt>'s
	 * <tt>onException</tt> method.
	 * <p>
	 * In those cases where the JMS specification permits a lower level of
	 * reliability, a normal synchronous send might not wait for an
	 * acknowledgement. In that case it is expected that an asynchronous send
	 * would be similar to a synchronous send: the JMS provider would send the
	 * message to the remote JMS server and then return without waiting for an
	 * acknowledgement. However the JMS provider would still notify the
	 * application that the send had completed by invoking the
	 * <tt>onCompletion</tt> method on the application-specified
	 * <tt>CompletionListener</tt> object.
	 * <p>
	 * It is up to the JMS provider to decide exactly what is performed in the
	 * calling thread and what, if anything, is performed asynchronously, so
	 * long as it satisfies the requirements given below:
	 * <p>
	 * <b>Quality of service</b>: After the send operation has completed
	 * successfully, which means that the message has been successfully sent
	 * with the same degree of confidence as if a normal synchronous send had
	 * been performed, the JMS provider must invoke the
	 * <tt>CompletionListener</tt>'s <tt>onCompletion</tt> method. The
	 * <tt>CompletionListener</tt> must not be invoked earlier than this.
	 * <p>
	 * <b>Exceptions</b>: If an exception is encountered during the call to the
	 * <tt>send</tt> method then an appropriate exception should be thrown in
	 * the thread that is calling the <tt>send</tt> method. In this case the JMS
	 * provider must not invoke the <tt>CompletionListener</tt>'s
	 * <tt>onCompletion</tt> or <tt>onException</tt> method. If an exception is
	 * encountered which cannot be thrown in the thread that is calling the
	 * <tt>send</tt> method then the JMS provider must call the
	 * <tt>CompletionListener</tt>'s <tt>onException</tt> method. In both cases
	 * if an exception occurs it is undefined whether or not the message was
	 * successfully sent.
	 * <p>
	 * <b>Message order</b>: If the same <tt>JMSContext</tt> is used to send
	 * multiple messages then JMS message ordering requirements must be
	 * satisfied. This applies even if a combination of synchronous and
	 * asynchronous sends has been performed. The application is not required to
	 * wait for an asynchronous send to complete before sending the next
	 * message.
	 * <p>
	 * <b>Close, commit or rollback</b>: If the <tt>close</tt> method is called
	 * on the <tt>JMSContext</tt> then the JMS provider must block until any
	 * incomplete send operations have been completed and all
	 * {@code CompletionListener} callbacks have returned before closing
	 * the object and returning. If the session is transacted (uses a local
	 * transaction) then when the <tt>JMSContext</tt>'s <tt>commit</tt> or
	 * <tt>rollback</tt> method is called the JMS provider must block until any
	 * incomplete send operations have been completed and all
	 * {@code CompletionListener} callbacks have returned before performing
	 * the commit or rollback. Incomplete sends should be allowed to complete
	 * normally unless an error occurs.
	 * <p>
	 * A <tt>CompletionListener</tt> callback method must not call
	 * <tt>close</tt>, <tt>commit</tt> or <tt>rollback</tt> on its own
	 * <tt>JMSContext</tt>. Doing so will cause the <tt>close</tt>,
	 * <tt>commit</tt> or <tt>rollback</tt> to throw an
	 * <tt>IllegalStateRuntimeException</tt>.
	 * <p>
	 * <b>Restrictions on usage in Java EE</b> This method must not be used in a
	 * Java EE EJB or web container. Doing so may cause a {@code JMSRuntimeException} 
	 * to be thrown though this is not guaranteed.
	 * <p>
	 * <b>Message headers</b> JMS defines a number of message header fields and
	 * message properties which must be set by the "JMS provider on send". If
	 * the send is asynchronous these fields and properties may be accessed on
	 * the sending client only after the <tt>CompletionListener</tt> has been
	 * invoked. If the <tt>CompletionListener</tt>'s <tt>onException</tt> method
	 * is called then the state of these message header fields and properties is
	 * undefined.
	 * <p>
	 * <b>Restrictions on threading</b>: Applications that perform an
	 * asynchronous send must confirm to the threading restrictions defined in
	 * JMS. This means that the session may be used by only one thread at a
	 * time.
	 * <p>
	 * Setting a <tt>CompletionListener</tt> does not cause the session to be
	 * dedicated to the thread of control which calls the
	 * <tt>CompletionListener</tt>. The application thread may therefore
	 * continue to use the session after performing an asynchronous send.
	 * However the <tt>CompletionListener</tt>'s callback methods must not use
	 * the session if an application thread might be using the session at the
	 * same time.
	 * <p>
	 * <b>Use of the <tt>CompletionListener</tt> by the JMS provider</b>: A
	 * session will only invoke one <tt>CompletionListener</tt> callback method
	 * at a time. For a given <tt>JMSContext</tt>, callbacks (both
	 * {@code onCompletion} and {@code onException}) will be performed
	 * in the same order as the corresponding calls to the <tt>send</tt> method.
	 * A JMS provider must not invoke the <tt>CompletionListener</tt> from the
	 * thread that is calling the <tt>send</tt> method.
	 * <p>
	 * <b>Restrictions on the use of the Message object</b>: Applications which
	 * perform an asynchronous send must take account of the restriction that a
	 * <tt>Message</tt> object is designed to be accessed by one logical thread
	 * of control at a time and does not support concurrent use.
	 * <p>
	 * After the <tt>send</tt> method has returned, the application must not
	 * attempt to read the headers, properties or body of the
	 * <tt>Message</tt> object until the <tt>CompletionListener</tt>'s
	 * <tt>onCompletion</tt> or <tt>onException</tt> method has been called.
	 * This is because the JMS provider may be modifying the <tt>Message</tt>
	 * object in another thread during this time. The JMS provider may throw an
	 * <tt>JMSException</tt> if the application attempts to access or modify the
	 * <tt>Message</tt> object after the <tt>send</tt> method has returned and
	 * before the <tt>CompletionListener</tt> has been invoked. If the JMS
	 * provider does not throw an exception then the behaviour is undefined.
	 * 
	 * @param completionListener
	 *            If asynchronous send behaviour is required, this should be set
	 *            to a {@code CompletionListener} to be notified when the
	 *            send has completed. If synchronous send behaviour is required,
	 *            this should be set to {@code null}.
	 * @return this {@code JMSProducer}
	 * 
	 * @throws JMSRuntimeException
	 *             if an internal error occurs
	 * 
	 * @see javax.jms.JMSProducer#getAsync
	 * @see javax.jms.CompletionListener
	 * 
	 */
	JMSProducer setAsync(CompletionListener completionListener);

	/**
	 * If subsequent calls to {@code send} on this
	 * {@code JMSProducer} object have been configured to be asynchronous 
	 * then this method returns the {@code CompletionListener}
	 * that has previously been configured.
	 * If subsequent calls to {@code send} have been configured to be synchronous
	 * then this method returns {@code null}.
	 * 
	 * @return the {@code CompletionListener} or {@code null}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the required information due
	 *             to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setAsync
	 */
	CompletionListener getAsync();

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code boolean}
	 * value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code boolean} value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getBooleanProperty
	 */

	JMSProducer setProperty(String name, boolean value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code byte} value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code byte} value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getByteProperty
	 */
	JMSProducer setProperty(String name, byte value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code short}
	 * value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code short} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getShortProperty
	 */

	JMSProducer setProperty(String name, short value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code int} value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code int} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getIntProperty
	 */

	JMSProducer setProperty(String name, int value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code long} value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code long} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getLongProperty
	 */
	JMSProducer setProperty(String name, long value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code float}
	 * value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code float} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getFloatProperty
	 */
	JMSProducer setProperty(String name, float value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code double}
	 * value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code double} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getDoubleProperty
	 */
	JMSProducer setProperty(String name, double value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified {@code String}
	 * value.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the {@code String} property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * 
	 * @see javax.jms.JMSProducer#getStringProperty
	 */
	JMSProducer setProperty(String name, String value);

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have the specified property set to the specified Java object value.
	 * <p>
	 * Note that this method works only for the objectified primitive object
	 * types ({@code Integer}, {@code Double}, {@code Long} ...)
	 * and {@code String} objects.
	 * <p>
	 * This will replace any property of the same name that is already set on
	 * the message being sent.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the Java object property value to set
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the property due to some
	 *             internal error.
	 * @throws IllegalArgumentException
	 *             if the name is null or if the name is an empty string.
	 * @throws MessageFormatRuntimeException
	 *             if the object is invalid
	 * 
	 * @see javax.jms.JMSProducer#getObjectProperty
	 */
	JMSProducer setProperty(String name, Object value);

	/**
	 * Clears any message properties set on this {@code JMSProducer}
	 * 
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to clear the message properties due
	 *             to some internal error.
	 */
	JMSProducer clearProperties();

	/**
	 * Indicates whether a message property with the specified name has been set
	 * on this {@code JMSProducer}
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return true whether the property exists
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to determine whether the property
	 *             exists due to some internal error.
	 */
	boolean propertyExists(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code boolean}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code boolean}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,boolean)
	 */

	boolean getBooleanProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code String}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code byte}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,byte)
	 */

	byte getByteProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code short}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code short}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,short)
	 */
	short getShortProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code int}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code int}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,int)
	 */
	int getIntProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code long}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code long}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,long)
	 */
	long getLongProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code float}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code float}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,float)
	 */
	float getFloatProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code double}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code double}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,double)
	 */
	double getDoubleProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to a {@code String}.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the property value, converted to a {@code boolean}; if there
	 *         is no property by this name, a null value is returned
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * @throws MessageFormatRuntimeException
	 *             if this type conversion is invalid.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,String)
	 */
	String getStringProperty(String name);

	/**
	 * Returns the message property with the specified name that has been set on
	 * this {@code JMSProducer}, converted to objectified format.
	 * <p>
	 * This method can be used to return, in objectified format, an object that
	 * has been stored as a property in the message with the equivalent
	 * {@code setObjectProperty} method call, or its equivalent primitive
	 * <code>set<I>type</I>Property</code> method.
	 * 
	 * @param name
	 *            the name of the property
	 * 
	 * @return the Java object property value with the specified name, in
	 *         objectified format (for example, if the property was set as an
	 *         {@code int}, an {@code Integer} is returned); if there
	 *         is no property by this name, a null value is returned
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property value due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setProperty(java.lang.String,java.lang.Object)
	 */
	Object getObjectProperty(String name);

	/**
	 * Returns an unmodifiable {@code Set} view of the names of all the message
	 * properties that have been set on this JMSProducer.
	 * <p>
	 * Note that JMS standard header fields are not considered properties and
	 * are not returned in this Set.
	 * <p>
	 * The set is backed by the {@code JMSProducer}, so changes to the map are
	 * reflected in the set. However the set may not be modified. Attempts to
	 * modify the returned collection, whether directly or via its iterator,
	 * will result in an {@code java.lang.UnsupportedOperationException}. Its behaviour matches
	 * that defined in the {@code java.util.Collections} method
	 * {@code unmodifiableSet}.
	 * 
	 * @return a {@code Set} containing the names of all the message properties
	 *         that have been set on this {@code JMSProducer}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the property names due to
	 *             some internal error.
	 * 
	 * @see java.util.Collections#unmodifiableSet
	 */
	Set<String> getPropertyNames();
 
	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have their {@code JMSCorrelationID} header value set to the
	 * specified correlation ID, where correlation ID is specified as an array
	 * of bytes.
	 * <p>
	 * This will override any {@code JMSCorrelationID} header value that is
	 * already set on the message being sent.
	 * <p>
	 * The array is copied before the method returns, so future modifications to
	 * the array will not alter the value in this {@code JMSProducer}.
	 * <p>
	 * If a provider supports the native concept of correlation ID, a JMS client
	 * may need to assign specific {@code JMSCorrelationID} values to match
	 * those expected by native messaging clients. JMS providers without native
	 * correlation ID values are not required to support this method and its
	 * corresponding get method; their implementation may throw a
	 * {@code java.lang.UnsupportedOperationException}.
	 * <p>
	 * The use of a {@code byte[]} value for {@code JMSCorrelationID}
	 * is non-portable.
	 * 
	 * @param correlationID
	 *            the correlation ID value as an array of bytes
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the correlation ID due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setJMSCorrelationID(String)
	 * @see javax.jms.JMSProducer#getJMSCorrelationID()
	 * @see javax.jms.JMSProducer#getJMSCorrelationIDAsBytes()
	 */
	JMSProducer setJMSCorrelationIDAsBytes(byte[] correlationID);

	/**
	 * Returns the {@code JMSCorrelationID} header value that has been set
	 * on this {@code JMSProducer}, as an array of bytes.
	 * <p>
	 * The use of a {@code byte[]} value for {@code JMSCorrelationID}
	 * is non-portable.
	 * 
	 * @return the correlation ID as an array of bytes
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the correlation ID due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setJMSCorrelationID(String)
	 * @see javax.jms.JMSProducer#getJMSCorrelationID()
	 * @see javax.jms.JMSProducer#setJMSCorrelationIDAsBytes(byte[])
	 */

	byte[] getJMSCorrelationIDAsBytes();

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have their {@code JMSCorrelationID} header value set to the
	 * specified correlation ID, where correlation ID is specified as a
	 * {@code String}.
	 * <p>
	 * This will override any {@code JMSCorrelationID} header value that is
	 * already set on the message being sent.
	 * <p>
	 * A client can use the {@code JMSCorrelationID} header field to link
	 * one message with another. A typical use is to link a response message
	 * with its request message.
	 * <p>
	 * {@code JMSCorrelationID} can hold one of the following:
	 * <UL>
	 * <LI>A provider-specific message ID
	 * <LI>An application-specific {@code String}
	 * <LI>A provider-native {@code byte[]} value
	 * </UL>
	 * <p>
	 * Since each message sent by a JMS provider is assigned a message ID value,
	 * it is convenient to link messages via message ID. All message ID values
	 * must start with the {@code 'ID:'} prefix.
	 * <p>
	 * In some cases, an application (made up of several clients) needs to use
	 * an application-specific value for linking messages. For instance, an
	 * application may use {@code JMSCorrelationID} to hold a value
	 * referencing some external information. Application-specified values must
	 * not start with the {@code 'ID:'} prefix; this is reserved for
	 * provider-generated message ID values.
	 * <p>
	 * If a provider supports the native concept of correlation ID, a JMS client
	 * may need to assign specific {@code JMSCorrelationID} values to match
	 * those expected by clients that do not use the JMS API. A
	 * {@code byte[]} value is used for this purpose. JMS providers without
	 * native correlation ID values are not required to support
	 * {@code byte[]} values. The use of a {@code byte[]} value for
	 * {@code JMSCorrelationID} is non-portable.
	 * 
	 * @param correlationID
	 *            the message ID of a message being referred to
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the correlation ID due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getJMSCorrelationID()
	 * @see javax.jms.JMSProducer#getJMSCorrelationIDAsBytes()
	 * @see javax.jms.JMSProducer#setJMSCorrelationIDAsBytes(byte[])
	 */
	JMSProducer setJMSCorrelationID(String correlationID);

	/**
	 * Returns the {@code JMSCorrelationID} header value that has been set
	 * on this {@code JMSProducer}, as a {@code String}.
	 * <p>
	 * This method is used to return correlation ID values that are either
	 * provider-specific message IDs or application-specific {@code String}
	 * values.
	 * 
	 * @return the correlation ID of a message as a {@code String}
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the correlation ID due to
	 *             some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setJMSCorrelationID(String)
	 * @see javax.jms.JMSProducer#getJMSCorrelationIDAsBytes()
	 * @see javax.jms.JMSProducer#setJMSCorrelationIDAsBytes(byte[])
	 */
	String getJMSCorrelationID();

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have their {@code JMSType} header value set to the specified message
	 * type.
	 * <p>
	 * This will override any {@code JMSType} header value that is already
	 * set on the message being sent.
	 * <p>
	 * Some JMS providers use a message repository that contains the definitions
	 * of messages sent by applications. The {@code JMSType} header field
	 * may reference a message's definition in the provider's repository.
	 * <p>
	 * The JMS API does not define a standard message definition repository, nor
	 * does it define a naming policy for the definitions it contains.
	 * <p>
	 * Some messaging systems require that a message type definition for each
	 * application message be created and that each message specify its type. In
	 * order to work with such JMS providers, JMS clients should assign a value
	 * to {@code JMSType}, whether the application makes use of it or not.
	 * This ensures that the field is properly set for those providers that
	 * require it.
	 * <p>
	 * To ensure portability, JMS clients should use symbolic values for
	 * {@code JMSType} that can be configured at installation time to the
	 * values defined in the current provider's message repository. If string
	 * literals are used, they may not be valid type names for some JMS
	 * providers.
	 * 
	 * @param type
	 *            the message type
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the message type due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#getJMSType()
	 */
	JMSProducer setJMSType(String type);

	/**
	 * Returns the {@code JMSType} header value that has been set on this
	 * {@code JMSProducer}.
	 * 
	 * @return the message type
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the message type due to some
	 *             internal error.
	 * 
	 * @see javax.jms.JMSProducer#setJMSType(String)
	 */
	String getJMSType();

	/**
	 * Specifies that messages sent using this {@code JMSProducer} will
	 * have their {@code JMSReplyTo} header value set to the specified
	 * {@code Destination} object.
	 * <p>
	 * This will override any {@code JMSReplyTo} header value that is
	 * already set on the message being sent.
	 * <p>
	 * The {@code JMSReplyTo} header field contains the destination where a
	 * reply to the current message should be sent. If it is null, no reply is
	 * expected. The destination may be either a {@code Queue} object or a
	 * {@code Topic} object.
	 * <p>
	 * Messages sent with a null {@code JMSReplyTo} value may be a
	 * notification of some event, or they may just be some data the sender
	 * thinks is of interest.
	 * <p>
	 * Messages with a {@code JMSReplyTo} value typically expect a
	 * response. A response is optional; it is up to the client to decide. These
	 * messages are called requests. A message sent in response to a request is
	 * called a reply.
	 * <p>
	 * In some cases a client may wish to match a request it sent earlier with a
	 * reply it has just received. The client can use the
	 * {@code JMSCorrelationID} header field for this purpose.
	 * 
	 * @param replyTo
	 *            {@code Destination} to which to send a response to this
	 *            message
	 * @return this {@code JMSProducer}
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to set the {@code JMSReplyTo}
	 *             destination due to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#getJMSReplyTo()
	 */
	JMSProducer setJMSReplyTo(Destination replyTo);

	/**
	 * Returns the {@code JMSReplyTo} header value that has been set on
	 * this {@code JMSProducer}.
	 * <p>
	 * 
	 * @return {@code Destination} the {@code JMSReplyTo} header value
	 * 
	 * @throws JMSRuntimeException
	 *             if the JMS provider fails to get the {@code JMSReplyTo}
	 *             destination due to some internal error.
	 * 
	 * @see javax.jms.JMSProducer#setJMSReplyTo(Destination)
	 */
	Destination getJMSReplyTo();

}
