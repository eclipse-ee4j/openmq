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
import javax.jms.JMSRuntimeException;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatRuntimeException;

/**
 * This interface must be implemented by all MQ MesageCOnsumer implementations
 * It adds some additional methods which are required by MQMessageConsumer
 *
 */
public interface MQMessageConsumer extends MessageConsumer {
	
	/**
	 * Receives the next message produced for this {@code MQMessageConsumer} and
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
	 * {@code MQMessageConsumer} is closed.
	 * <p>
	 * If this method is called within a transaction, the
	 * {@code MQMessageConsumer} retains the message until the transaction commits.
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
	 *            assigned.<br/>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br/>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br/>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 * 
	 * @return the body of the next message produced for this
	 *         {@code MQMessageConsumer}, or null if this {@code MQMessageConsumer} is
	 *         concurrently closed
	 * 
	 * @throws MessageFormatException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 * @throws JMSException
	 *             if the JMS provider fails to receive the next message due to
	 *             some internal error
	 */
	<T> T receiveBody(Class<T> c) throws JMSException;
	
	/**
	 * Receives the next message produced for this {@code MQMessageConsumer} 
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
	 * {@code MQMessageConsumer} is closed. A timeout of zero never expires, and the
	 * call blocks indefinitely.
	 * <p>
	 * If this method is called within a transaction, the
	 * {@code MQMessageConsumer} retains the message until the transaction commits.
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
	 *            assigned.<br/>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br/>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br/>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 * 
	 * @return the body of the next message produced for this {@code MQMessageConsumer},
	 *         or null if the timeout expires or this {@code MQMessageConsumer} is concurrently closed
	 * @throws JMSException 
	 * 
	 * @throws MessageFormatException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 * @throws JMSException
	 *             if the JMS provider fails to receive the next message due
	 *             to some internal error
	 */
    <T> T receiveBody(Class<T> c, long timeout) throws JMSException;
    
    
	/**
	 * Receives the next message produced for this {@code MQMessageConsumer} 
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
	 * {@code MQMessageConsumer} retains the message until the transaction commits.
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
	 *            assigned.<br/>
	 *            If the next message is expected to be a {@code TextMessage}
	 *            then this should be set to {@code String.class} or another
	 *            class to which a {@code String} is assignable.<br/>
	 *            If the next message is expected to be a {@code ObjectMessage}
	 *            then this should be set to {@code java.io.Serializable.class}
	 *            or another class to which the body is assignable. <br/>
	 *            If the next message is expected to be a {@code MapMessage}
	 *            then this should be set to {@code java.util.Map.class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 *            If the next message is expected to be a {@code BytesMessage}
	 *            then this should be set to {@code byte[].class}
	 *            (or {@code java.lang.Object.class}).<br/>
	 * 
	 * @return the body of the next message produced for this {@code MQMessageConsumer},
	 *         or null if one is not immediately available or this {@code MQMessageConsumer} is concurrently closed
	 * 
	 * @throws MessageFormatException
	 *             <ul>
	 *             <li>if the message is not one of the supported types listed above
	 *             <li>if the message body cannot be assigned to the specified type
	 *             <li>if the message has no body
	 *             <li>if the message is an {@code ObjectMessage} and object deserialization fails.
	 *             </ul>
	 *             
	 * @throws JMSException
	 *             if the JMS provider fails to receive the next message due
	 *             to some internal error

	 */
    <T> T receiveBodyNoWait(Class<T> c) throws JMSException;

}
