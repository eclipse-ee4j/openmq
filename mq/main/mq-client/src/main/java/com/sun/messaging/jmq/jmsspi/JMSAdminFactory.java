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

/*
 * @(#)JMSAdminFactory.java	1.7 06/29/07
 */

package com.sun.messaging.jmq.jmsspi;

import javax.jms.JMSException;

/**
 * Interface definition to return an instance which implements JMSAdmin.
 */

public interface JMSAdminFactory {

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin() throws JMSException;

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param secure Use secure transport
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin(boolean secure) throws JMSException;

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param brokerPropertiesHolder holder of Properties to be passed to managed broker
	 * @param userName Administrator username
	 * @param password Administrator password (needed for client connections and when starting broker if not specified in brokerProperties)
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException  thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin(String jmsAdminURL, PropertiesHolder brokerPropertiesHolder, String userName, String password)
			throws JMSException;

	JMSAdmin getJMSAdmin(String jmsAdminURL, String userName, String password) throws JMSException;

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param brokerPropertiesHolder holder of Properties to be passed to managed broker
	 * @param userName Administrator username
	 * @param password Administrator password (needed for client connections and when starting broker if not specified in brokerProperties)
	 * @param secure Use secure transport
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin(String jmsAdminURL, PropertiesHolder brokerPropertiesHolder, String userName, String password,
			boolean secure) throws JMSException;

	/**
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param userName Administrator username
	 * @param password Administrator password (needed for client connections and when starting broker if not specified in brokerProperties)
	 * @param secure Use secure transport
	 * @return
	 * @throws JMSException
	 */
	JMSAdmin getJMSAdmin(String jmsAdminURL, String userName, String password, boolean secure) throws JMSException;

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin(String jmsAdminURL) throws JMSException;

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param secure Use secure transport.
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	JMSAdmin getJMSAdmin(String jmsAdminURL, boolean secure) throws JMSException;

}
