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

/**
 * All Connection objects which are able to be wrapped in a JMSContextImpl need
 * to implement this interface, which defines some private methods needed by
 * JMSContextImpl
 * 
 */
public interface ContextableConnection {

	/**
	 * Set clientID to the specified value, bypassing any checks as to whether
	 * calling setClientID is allowed at this time. (This method is permitted to
	 * check whether the connection is closed and whether it is being called in
	 * a Java EE web or EJB container, but it doesn't need to)
	 * 
	 * @param clientID
	 */
	public void _setClientIDForContext(String clientID);

}
