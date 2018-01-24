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
 * A {@code CompletionListener} is implemented by the application and may
 * be specified when a message is sent asynchronously.
 * <p>
 * When the sending of the message is complete, the JMS provider notifies the
 * application by calling the {@code onCompletion(Message)} method of the
 * specified completion listener. If the sending if the message fails for any
 * reason, and an exception cannot be thrown by the {@code send} method,
 * then the JMS provider calls the {@code onException(Exception)} method of
 * the specified completion listener.
 * 
 * @see javax.jms.MessageProducer#send(javax.jms.Message,int,int,long,javax.jms.CompletionListener)
 * @see javax.jms.MessageProducer#send(javax.jms.Destination,javax.jms.Message,javax.jms.CompletionListener)
 * @see javax.jms.MessageProducer#send(javax.jms.Destination,javax.jms.Message,int,int,long,javax.jms.CompletionListener)
 * @see javax.jms.JMSProducer#setAsync(javax.jms.CompletionListener)
 * @see javax.jms.JMSProducer#getAsync()
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 */
public interface CompletionListener {

	/**
	 * Notifies the application that the message has been successfully sent
	 * 
	 * @param message
	 *            the message that was sent.
	 */
	void onCompletion(Message message);

	/**
	 * Notifies user that the specified exception was thrown while attempting to
	 * send the specified message. If an exception occurs it is undefined
	 * whether or not the message was successfully sent.
	 * 
	 * @param message
	 *            the message that was sent.
	 * @param exception
	 *            the exception
	 * 
	 */
	void onException(Message message, Exception exception);
}
