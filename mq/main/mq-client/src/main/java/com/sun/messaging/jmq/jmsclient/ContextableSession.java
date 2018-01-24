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

/**
 * All Session objects which are able to be wrapped in a JMSContextImpl need to
 * implement this interface, which defines some private methods needed by
 * JMSContextImpl
 * 
 */
public interface ContextableSession {

	/**
	 * Acknowledges all consumed messages of this session
	 * 
	 * Calls to acknowledge are ignored for both transacted sessions and
	 * sessions specified to use implicit acknowledgement modes.
	 * 
	 * Note that if an acknowledge() method is added to Session in JMS 2.0, that
	 * method can be used instead and this method will be redundant
	 * 
	 * @throws JMSException
	 *             if the JMS provider fails to acknowledge the messages due to
	 *             some internal error.
	 * @throws IllegalStateException
	 *             if this method is called on a closed session.
	 */
	public void clientAcknowledge() throws JMSException, IllegalStateException;

}
