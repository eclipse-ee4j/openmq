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
 * @(#)JMSAdminFactoryImpl.java	1.12 06/27/07
 */

package com.sun.messaging.jmq.admin.jmsspi;

import java.util.Properties;

import javax.jms.*;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsspi.JMSAdmin;
import com.sun.messaging.jmq.jmsspi.JMSAdminFactory;
import com.sun.messaging.jmq.jmsspi.PropertiesHolder;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;

public class JMSAdminFactoryImpl implements JMSAdminFactory {

	private static AdminResources ar = Globals.getAdminResources();

	public final static String DEFAULT_ADMIN_USERNAME = "admin";
	public final static String DEFAULT_ADMIN_PASSWD = "admin";

	/**
	 * This constructor should only be used when no need to communicate with broker e.g. only create administered
	 * objects
	 */
	public JMSAdmin getJMSAdmin() throws JMSException {
		return (getJMSAdmin(false));
	}

	/**
	 * This constructor should only be used when no need to communicate with broker e.g. only create administered
	 * objects
	 * 
	 * @param secure Use secure transport
	 * @return Implementation of JMSAdmin.
	 */
	public JMSAdmin getJMSAdmin(boolean secure) throws JMSException {
		Properties connectionProps = createProviderProperties(null, secure);
		return new JMSAdminImpl(connectionProps, null, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWD);
	}

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param brokerPropertiesHolder holder of Properties to be passed to managed broker
	 * @param adminUserName Administrator username
	 * @param password Administrator password (needed for client connections and when starting broker if not specified in brokerProperties)
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	public JMSAdmin getJMSAdmin(String jmsAdminURL, PropertiesHolder brokerPropertiesHolder, String adminUserName, String adminPassword) throws JMSException {
		return getJMSAdmin(jmsAdminURL, brokerPropertiesHolder, adminUserName, adminPassword, false);
	}

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param adminUserName Administrator username
	 * @param adminPassword Administrator password
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	public JMSAdmin getJMSAdmin(String jmsAdminURL, String adminUserName, String adminPassword) throws JMSException {
		return getJMSAdmin(jmsAdminURL, null, adminUserName, adminPassword, false);
	}

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
	public JMSAdmin getJMSAdmin(String jmsAdminURL, PropertiesHolder brokerPropertiesHolder, String userName, String adminPassword,
			boolean secure) throws JMSException {
		Properties connectionProps = createProviderProperties(jmsAdminURL, secure);
		JMSAdmin admin = new JMSAdminImpl(connectionProps, brokerPropertiesHolder, userName, adminPassword);
		return admin;
	}

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param adminUserName Administrator username
	 * @param adminPassword Administrator password
	 * @param secure Use secure transport
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException  thrown if JMSAdmin could not be created/returned.
	 */
	public JMSAdmin getJMSAdmin(String jmsAdminURL, String adminUserName, String adminPassword, boolean secure)
			throws JMSException {
		Properties connectionProps = createProviderProperties(jmsAdminURL, secure);
		JMSAdmin admin = new JMSAdminImpl(connectionProps, null, adminUserName, adminPassword);
		return admin;
	}

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	public JMSAdmin getJMSAdmin(String jmsAdminURL) throws JMSException {
		return getJMSAdmin(jmsAdminURL, false);
	}

	/**
	 * Create/return an instance implementing JMSAdmin.
	 * 
	 * @param jmsAdminURL JMSAdmin URL
	 * @param secure Use secure transport
	 * @return Implementation of JMSAdmin.
	 * @exception JMSException thrown if JMSAdmin could not be created/returned.
	 */
	public JMSAdmin getJMSAdmin(String jmsAdminURL, boolean secure) throws JMSException {
		Properties connectionProps = createProviderProperties(jmsAdminURL, secure);
		JMSAdmin admin = new JMSAdminImpl(connectionProps, null, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWD);
		return admin;
	}

	private Properties createProviderProperties(String jmsAdminURL, boolean secure) throws JMSException {

		Properties tmpProps = new Properties();

		String host = getBrokerHost(jmsAdminURL);
		int port = getBrokerPort(jmsAdminURL);

		if (host != null) {
			tmpProps.setProperty(ConnectionConfiguration.imqBrokerHostName, host);
		}

		if (port > 0) {
			tmpProps.setProperty(ConnectionConfiguration.imqBrokerHostPort, String.valueOf(port));
		}

		if (secure) {
			tmpProps.setProperty(ConnectionConfiguration.imqConnectionType, "TLS");
		}

		return tmpProps;
	}

	/*
	 * Returns the broker host name. Returns null if not specified.
	 * 
	 * @param brokerHostPort String in the form of host:port
	 * 
	 * @return host value or null if not specified
	 */
	private String getBrokerHost(String brokerHostPort) {
		String host = brokerHostPort;

		if (brokerHostPort == null)
			return (null);

		int i = brokerHostPort.indexOf(':');
		if (i >= 0)
			host = brokerHostPort.substring(0, i);

		if (host.equals("")) {
			return null;
		}
		return host;
	}

	/*
	 * Returns the broker port number. Return -1 if not specified.
	 * 
	 * @param brokerHostPort String in the form of host:port
	 * 
	 * @return port value or -1 if not specified
	 * 
	 * @throw BrokerAdminException if port value is not valid
	 */
	private int getBrokerPort(String brokerHostPort) throws JMSException {
		int port = -1;

		if (brokerHostPort == null)
			return (port);

		int i = brokerHostPort.indexOf(':');

		if (i >= 0) {
			try {
				port = Integer.parseInt(brokerHostPort.substring(i + 1));

			} catch (Exception e) {
				throw new JMSException(ar.getKString(AdminResources.X_JMSSPI_INVALID_PORT, brokerHostPort));
			}
		}
		return port;
	}
}
