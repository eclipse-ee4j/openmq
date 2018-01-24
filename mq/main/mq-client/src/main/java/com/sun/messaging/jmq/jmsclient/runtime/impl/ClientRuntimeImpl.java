/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient.runtime.impl;

import javax.jms.JMSException;
import com.sun.messaging.jmq.jmsclient.ConnectionImpl;
import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;

public class ClientRuntimeImpl extends ClientRuntime {
	
	protected ClientRuntimeImpl() {
		super();
	}

	public static ClientRuntime getClientRuntimeImpl() {
		return MyInstance.runtimeImpl;
	}
	
	public boolean isEmbeddedBrokerRunning() {
		
		if (BrokerInstanceImpl.getInstance() != null) {
			return BrokerInstanceImpl.getInstance().isBrokerRunning();
		} else {
			return false;
		}
	}
	
	public DirectBrokerConnection createDirectConnection() throws JMSException {
		 
		if (BrokerInstanceImpl.getInstance() == null) {
			throw new JMSException ("Cannot create direct connection. No embedded broker running in this JVM.");
		}
		
		DirectBrokerConnection dbc = BrokerInstanceImpl.getInstance().createDirectConnection();	
		
		//ConnectionImpl.getConnectionLogger().info("ClientRuntime created direct connection ...");
		
		return dbc;
	}
	
	public synchronized BrokerInstance createBrokerInstance() throws IllegalAccessException {
		return BrokerInstanceImpl.createInstance();
	}
		
	/**
	 * Client runtime singleton instance is constructed here.
	 */
	private static class MyInstance {
		private final static ClientRuntime runtimeImpl = new ClientRuntimeImpl();
	}
}
