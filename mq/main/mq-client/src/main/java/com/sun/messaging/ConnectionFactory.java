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
 * @(#)ConnectionFactory.java	1.25 06/28/07
 */ 

package com.sun.messaging;

import javax.jms.*;
import com.sun.messaging.naming.ReferenceGenerator;
import com.sun.messaging.jmq.jmsclient.QueueConnectionImpl;
import com.sun.messaging.jmq.jmsclient.TopicConnectionImpl;

/**
 * A <code>ConnectionFactory</code> is used to create Connections with
 * the Sun MQ Java Message Service (JMS) provider.
 *
 * @see         javax.jms.ConnectionFactory javax.jms.ConnectionFactory
 * @see         com.sun.messaging.ConnectionConfiguration com.sun.messaging.ConnectionConfiguration
 */
public class ConnectionFactory extends BasicConnectionFactory implements javax.naming.Referenceable {

    /**
     * Constructs a ConnectionFactory with the default configuration.
     *
     */
    public ConnectionFactory() {
        super();
    }

    /**
     * Constructs a ConnectionFactory with the specified configuration.
     *
     */
    protected ConnectionFactory(String defaultsBase) {
        super(defaultsBase);
    }

    /**
     * Creates a Queue Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     *   
     * @return a newly created Queue Connection.
     *   
     * @exception JMSException if a JMS error occurs.
     * @see ConnectionConfiguration#imqDefaultUsername
     * @see ConnectionConfiguration#imqDefaultPassword
     */  
    public QueueConnection createQueueConnection() throws JMSException {
        return createQueueConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                     getProperty(ConnectionConfiguration.imqDefaultPassword));
    }
 
    /**
     * Creates a Queue Connection with a specified user identity.
     * 
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created queue connection.
     *
     * @exception JMSException if a JMS error occurs.
     */
    public QueueConnection createQueueConnection(String username, String password) throws JMSException {
        return new QueueConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

    /**
     * Creates a Topic Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     *   
     * @return a newly created Topic Connection.
     *   
     * @exception JMSException if a JMS error occurs.
     * @see ConnectionConfiguration#imqDefaultUsername
     * @see ConnectionConfiguration#imqDefaultPassword
     */  
    public TopicConnection createTopicConnection() throws JMSException {
        return createTopicConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                     getProperty(ConnectionConfiguration.imqDefaultPassword));
    }
 
    /**
     * Creates a Topic Connection with a specified user identity.
     * 
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created topic connection.
     *
     * @exception JMSException if a JMS error occurs.
     */
    public TopicConnection createTopicConnection(String username, String password) throws JMSException {
        return new TopicConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

    /**
     * Returns the reference to this object.
     *   
     * @return  The Reference Object that can be used to reconstruct this object
     *   
     */
    public javax.naming.Reference getReference() {
        return (ReferenceGenerator.getReference(this,
                com.sun.messaging.naming.AdministeredObjectFactory.class.getName()));
    }
}
