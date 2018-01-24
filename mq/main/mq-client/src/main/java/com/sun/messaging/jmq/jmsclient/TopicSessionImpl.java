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
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.TemporaryQueue;
import javax.jms.TopicSession;

import com.sun.messaging.AdministeredObject;

/**
 * A TopicSession provides methods for creating TopicPublisher's,
 * TopicSubscriber's and TemporaryTopics. It also provides a method for deleting
 * its client's durable subscribers.
 * 
 * @see javax.jms.Session
 * @see javax.jms.TopicConnection#createTopicSession(boolean, int)
 * @see javax.jms.XATopicSession#getTopicSession()
 */

public class TopicSessionImpl extends UnifiedSessionImpl implements TopicSession {

	public TopicSessionImpl(ConnectionImpl connection, boolean transacted, int ackMode) throws JMSException {
		super(connection, transacted, ackMode);
	}

	public TopicSessionImpl(ConnectionImpl connection, int ackMode) throws JMSException {
		super(connection, ackMode);
	}

	@Override
	public Queue createQueue(String queueName) throws JMSException {
		String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createQueue");
		throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
	}

	@Override
	public TemporaryQueue createTemporaryQueue() throws JMSException {
		String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createTemporaryQueue");
		throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
	}

	@Override
	public QueueBrowser createBrowser(Queue queue) throws JMSException {
		String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createBrowser");
		throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
	}

	@Override
	public QueueBrowser createBrowser(Queue queue, String selector) throws JMSException {
		String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createBrowser");
		throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
	}
}
