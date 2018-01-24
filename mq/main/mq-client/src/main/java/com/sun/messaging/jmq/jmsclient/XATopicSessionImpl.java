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
import javax.jms.XATopicSession;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;

/**
 * An XATopicSession provides a regular TopicSession which can be used to create
 * TopicSubscribers and TopicPublishers (optional).
 * 
 * <P>
 * XASession extends the capability of Session by adding access to a JMS
 * provider's support for JTA (optional). This support takes the form of a
 * <CODE>javax.transaction.xa.XAResource</CODE> object. The functionality of
 * this object closely resembles that defined by the standard X/Open XA Resource
 * interface.
 * 
 * <P>
 * An application server controls the transactional assignment of an XASession
 * by obtaining its XAResource. It uses the XAResource to assign the session to
 * a transaction; prepare and commit work on the transaction; etc.
 * 
 * <P>
 * An XAResource provides some fairly sophisticated facilities for interleaving
 * work on multiple transactions; recovering a list of transactions in progress;
 * etc. A JTA aware JMS provider must fully implement this functionality. This
 * could be done by using the services of a database that supports XA or a JMS
 * provider may choose to implement this functionality from scratch.
 * 
 * <P>
 * A client of the application server is given what it thinks is a regular JMS
 * Session. Behind the scenes, the application server controls the transaction
 * management of the underlying XASession.
 * 
 * @see javax.jms.XASession javax.jms.XASession
 * @see javax.jms.XATopicSession javax.jms.XATopicSession
 */

public class XATopicSessionImpl extends XASessionImpl implements TopicSession, XATopicSession {

	public XATopicSessionImpl(ConnectionImpl connection, boolean transacted, int ackMode) throws JMSException {
		super(connection, transacted, ackMode);
	}

	public XATopicSessionImpl(ConnectionImpl connection, boolean transacted, int ackMode, JMSRAManagedConnection mc) throws JMSException {
		super(connection, transacted, ackMode, mc);
	}

	@Override
	public TopicSession getTopicSession() throws JMSException {
		return (TopicSession) this;
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
