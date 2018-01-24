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
 * @(#)JMSXATopicConnectionFactoryImpl.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;

import com.sun.jms.spi.xa.*;

import com.sun.messaging.ConnectionConfiguration;

/**
 * An <code>XATopicConnectionFactory</code> is used to create XATopicConnections with
 * a Java Message Service (JMS) Publish/Subscribe (Pub/Sub) provider.
 *
 * @see         javax.jms.XATopicConnectionFactory javax.jms.XATopicConnectionFactory
 */
public class JMSXATopicConnectionFactoryImpl extends com.sun.messaging.TopicConnectionFactory implements JMSXATopicConnectionFactory {

    /**
     * Constructs a JMSXATopicConnectionFactory with the default configuration.
     * 
     */
    public JMSXATopicConnectionFactoryImpl() {
        super("/com/sun/messaging/ConnectionFactory");
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

    public JMSXATopicConnection createXATopicConnection() throws JMSException {
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
 
    public JMSXATopicConnection createXATopicConnection(String username,
                                                        String password) throws JMSException {
        return new JMSXATopicConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

}
