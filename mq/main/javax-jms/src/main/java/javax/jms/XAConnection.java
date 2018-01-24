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

/**
 * The {@code XAConnection} interface extends the capability of
 * {@code Connection} by providing an {@code XASession} (optional).
 * 
 * <P>
 * The {@code XAConnection} interface is optional. JMS providers are not required
 * to support this interface. This interface is for use by JMS providers to
 * support transactional environments. Client programs are strongly encouraged
 * to use the transactional support available in their environment, rather than
 * use these XA interfaces directly.
 * 
 * @see javax.jms.XAQueueConnection
 * @see javax.jms.XATopicConnection
 * 
 * @version JMS 2.0
 * @since JMS 1.0
 * 
 */

public interface XAConnection extends Connection {

	/**
	 * Creates an {@code XASession} object.
	 * 
	 * @return a newly created {@code XASession}
	 * 
	 * @exception JMSException
	 *                if the {@code XAConnection} object fails to create an
	 *                {@code XASession} due to some internal error.
	 * 
	 * @since JMS 1.1
	 * 
	 */

	XASession createXASession() throws JMSException;

	/**
	 * Creates an {@code Session} object.
	 * 
	 * @param transacted
	 *            usage undefined
	 * @param acknowledgeMode
	 *            usage undefined
	 * 
	 * @return a newly created {@code Session}
	 * 
	 * @exception JMSException
	 *                if the {@code XAConnection} object fails to create a
	 *                {@code Session} due to some internal error.
	 * 
	 * @since JMS 1.1
	 * 
	 */
	Session createSession(boolean transacted, int acknowledgeMode)
			throws JMSException;
}
