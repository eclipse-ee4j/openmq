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

package com.sun.messaging.jms.ra;

import java.util.logging.Logger;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.resource.ResourceException;

import com.sun.messaging.jmq.jmsclient.XAConnectionImpl;

public class QueueConnectionAdapter extends ConnectionAdapter {
	
	/* Loggers */
	private static transient final String _className = "com.sun.messaging.jms.ra.QueueConnectionAdapter";
	protected static transient final String _lgrNameJMSConnection = "javax.jms.Connection.mqjmsra";
	protected static transient final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
	protected static transient final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
	protected static transient final Logger _loggerJC = Logger.getLogger(_lgrNameJMSConnection);
	protected static transient final String _lgrMIDPrefix = "MQJMSRA_CA";
	protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
	protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
	protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
	protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
	protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

	public QueueConnectionAdapter(ManagedConnection mc, XAConnectionImpl xac, ResourceAdapter ra) throws ResourceException {
		super(mc, xac, ra);
	}

	@Override
	public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
			int maxMessages) throws JMSException {

		// JMS spec and CTS tests require a IllegalStateException to be thrown
		String methodName = "createSharedConnectionConsumer(Topic topic, String subscriptionName,String messageSelector, ServerSessionPool sessionPool, int maxMessages)";
		String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a QueueConnection";
		_loggerJC.warning(isIllegalMsg);
		throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector,
			ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		// JMS spec and CTS tests require a IllegalStateException to be thrown
		String methodName = "createSharedDurableConnectionConsumer(Topic topic, String subscriptionName,String messageSelector, ServerSessionPool sessionPool, int maxMessages)";
		String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a QueueConnection";
		_loggerJC.warning(isIllegalMsg);
		throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
			int maxMessages) throws JMSException {
		// JMS spec and CTS tests require a IllegalStateException to be thrown
		String methodName = "createConnectionConsumer(Queue queue,String messageSelector,ServerSessionPool sessionPool, int maxMessages)";
		String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a QueueConnection";
		_loggerJC.warning(isIllegalMsg);
		throw new javax.jms.IllegalStateException(isIllegalMsg);
	}
	

}
