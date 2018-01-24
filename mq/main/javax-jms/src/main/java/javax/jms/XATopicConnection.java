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
 * An {@code XATopicConnection} provides the same create options as
 * {@code TopicConnection} (optional). The Topic connections created are
 * transactional.
 * 
 * <P>
 * The {@code XATopicConnection} interface is optional. JMS providers are not
 * required to support this interface. This interface is for use by JMS
 * providers to support transactional environments. Client programs are strongly
 * encouraged to use the transactional support available in their environment,
 * rather than use these XA interfaces directly.
 * 
 * @see javax.jms.XAConnection
 * 
 * @version JMS 2.0
 * @since JMS 1.0
 * 
 */

public interface XATopicConnection extends XAConnection, TopicConnection {

	/**
	 * Creates an {@code XATopicSession} object.
	 * 
	 * @return a newly created {@code XATopicSession}
	 * 
	 * @exception JMSException
	 *                if the {@code XATopicConnection} object fails to create an
	 *                {@code XATopicSession} due to some internal error.
	 */

	XATopicSession createXATopicSession() throws JMSException;

	/**
	 * Creates a {@code TopicSession} object.
	 * 
	 * @param transacted
	 *            usage undefined
	 * @param acknowledgeMode
	 *            usage undefined
	 * 
	 * @return a newly created {@code TopicSession}
	 * 
	 * @exception JMSException
	 *                if the {@code XATopicConnection} object fails to create a
	 *                {@code TopicSession} due to some internal error.
	 */

	TopicSession createTopicSession(boolean transacted, int acknowledgeMode)
			throws JMSException;
}
