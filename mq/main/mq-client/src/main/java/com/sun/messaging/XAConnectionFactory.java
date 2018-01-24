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
 * @(#)XAConnectionFactory.java	1.5 06/28/07
 */ 

package com.sun.messaging;

import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.sun.messaging.jmq.jmsclient.ContainerType;
import com.sun.messaging.jmq.jmsclient.XAConnectionImpl;
import com.sun.messaging.jmq.jmsclient.XAJMSContextImpl;
import com.sun.messaging.jmq.jmsclient.XAQueueConnectionImpl;
import com.sun.messaging.jmq.jmsclient.XATopicConnectionImpl;

/**
 * An <code>XAConnectionFactory</code> is used to create XAConnections with
 * the Sun MQ Java Message Service (JMS) provider.
 *
 * @see         javax.jms.XAConnectionFactory javax.jms.XAConnectionFactory
 */
public class XAConnectionFactory extends com.sun.messaging.ConnectionFactory implements javax.jms.XAConnectionFactory {
	
    /* The type of container in which this class is operating. See the ContainerType enum for possible values */
    private static ContainerType containerType;

    /**
     * Create an XA connection with default user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *   
     * @return a newly created XA connection.
     *   
     * @exception JMSException if JMS Provider fails to create XA Connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */  

    public XAConnection createXAConnection() throws JMSException {
        return createXAConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                  getProperty(ConnectionConfiguration.imqDefaultPassword));
    }

    /**
     * Create an XA connection with specified user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *   
     * @param username the caller's user name
     * @param password the caller's password
     *   
     * @return a newly created XA connection.
     *   
     * @exception JMSException if JMS Provider fails to create XA connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */
 
    public XAConnection createXAConnection(String username, String password) throws JMSException {
        return new XAConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }
 
    /**
     * Create an XA queue connection with default user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *  
     * @return a newly created XA queue connection.
     *  
     * @exception JMSException if JMS Provider fails to create XA queue Connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */

    public XAQueueConnection createXAQueueConnection() throws JMSException {
        return createXAQueueConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                        getProperty(ConnectionConfiguration.imqDefaultPassword));
    }

    /**
     * Create an XA queue connection with specific user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *  
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created XA queue connection.
     *
     * @exception JMSException if JMS Provider fails to create XA queue Connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */
 
    public XAQueueConnection createXAQueueConnection(String username,
                                                     String password) throws JMSException {
        return new XAQueueConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

    /**
     * Create an XA topic connection with default user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *   
     * @return a newly created XA topic connection.
     *   
     * @exception JMSException if JMS Provider fails to create XA topic Connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */  

    public XATopicConnection createXATopicConnection() throws JMSException {
        return createXATopicConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                        getProperty(ConnectionConfiguration.imqDefaultPassword));
    }

    /**
     * Create an XA topic connection with specified user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *   
     * @param username the caller's user name
     * @param password the caller's password
     *   
     * @return a newly created XA topic connection.
     *   
     * @exception JMSException if JMS Provider fails to create XA topi connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */
 
    public XATopicConnection createXATopicConnection(String username, String password) throws JMSException {
        return new XATopicConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

	@Override
	public XAJMSContext createXAContext() {
		return new XAJMSContextImpl(this, getContainerType());
	}

	@Override
	public XAJMSContext createXAContext(String userName, String password) {
		return new XAJMSContextImpl(this, getContainerType(), userName, password);
	}
	
	protected static ContainerType getContainerType(){
		// Overrides the implementation in BasicConnectionFactory which always returns JavaSE
		
		if (containerType==null){
			// See Java EE 7 section EE.5.17 "5.17 Application Client Container Property"
			String lookupName = "java:comp/InAppClientContainer";
			try {
				InitialContext ic = new InitialContext();
				Boolean inAppClientContainer = (Boolean)ic.lookup(lookupName);
				if (inAppClientContainer){
					containerType=ContainerType.JavaEE_ACC;
				} else {
					containerType=ContainerType.JavaEE_Web_or_EJB;
				}
			} catch (NamingException e) {
				containerType=ContainerType.JavaSE;
			}
		}
		return containerType;
	}
}
