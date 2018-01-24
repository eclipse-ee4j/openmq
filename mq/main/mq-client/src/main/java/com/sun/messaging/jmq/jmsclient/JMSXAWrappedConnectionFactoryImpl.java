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
 * @(#)JMSXAWrappedConnectionFactoryImpl.java	1.6 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import com.sun.jms.spi.xa.*;
import javax.jms.*;
import java.io.Serializable;

/**
 * An <code>XAQueueConnectionFactory</code> is used to create XAQueueConnections with
 * a Java Message Service (JMS) Point-to-Point (PTP) provider.
 *
 * @see         javax.jms.XAQueueConnectionFactory javax.jms.XAQueueConnectionFactory
 */
public class JMSXAWrappedConnectionFactoryImpl implements JMSXAQueueConnectionFactory, JMSXATopicConnectionFactory, Serializable {

    private ConnectionFactory wrapped_connectionfactory;
    public static final boolean debug = Boolean.getBoolean("DEBUG_JMSXAWrappedForExternalJMS"); 

    /** private constuctor - disallow null constructor */
    private JMSXAWrappedConnectionFactoryImpl() {}
     
    /**
     * Constructs a JMSXAWrappedConnectionFactoryImpl using an standard JMS
     *              XAQueueConnectionFactory.
     * 
     */
    public JMSXAWrappedConnectionFactoryImpl(XAQueueConnectionFactory xaqcf) {
        wrapped_connectionfactory = xaqcf;
    }
 
    /**
     * Constructs a JMSXAWrappedConnectionFactoryImpl using an standard JMS
     *              XATopicConnectionFactory.
     * 
     */
    public JMSXAWrappedConnectionFactoryImpl(XATopicConnectionFactory xatcf) {
        wrapped_connectionfactory = xatcf;
    }
 
    /**
     * Constructs a JMSXAWrappedConnectionFactoryImpl using an standard JMS
     *              QueueConnectionFactory.
     * 
     */
    public JMSXAWrappedConnectionFactoryImpl(QueueConnectionFactory qcf) {
        wrapped_connectionfactory = qcf;
    }
 
    /**
     * Constructs a JMSXAWrappedConnectionFactoryImpl using an standard JMS
     *              TopicConnectionFactory.
     * 
     */
    public JMSXAWrappedConnectionFactoryImpl(TopicConnectionFactory tcf) {
        wrapped_connectionfactory = tcf;
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
    public JMSXAQueueConnection createXAQueueConnection() throws JMSException {
        if (wrapped_connectionfactory instanceof XAConnectionFactory) {
            return (JMSXAQueueConnection) (new JMSXAWrappedQueueConnectionImpl(
                ((XAQueueConnectionFactory)wrapped_connectionfactory).createXAQueueConnection(), this, null, null));
        } else {
            //wrapped_connectionfactory cannot be anything than a javax.jms.ConnectionFactory at this point
            return (JMSXAQueueConnection) (new JMSXAWrappedQueueConnectionImpl(
                    ((QueueConnectionFactory)wrapped_connectionfactory).createQueueConnection(), this, null, null));
        }
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
    public JMSXAQueueConnection createXAQueueConnection(String username,
                                                        String password) throws JMSException {
        if (wrapped_connectionfactory instanceof XAConnectionFactory) {
            return (JMSXAQueueConnection) (new JMSXAWrappedQueueConnectionImpl(
                ((XAQueueConnectionFactory)
                  wrapped_connectionfactory).createXAQueueConnection(username, password),
                 this, username, password));
        } else {
            //wrapped_connectionfactory cannot be anything than a javax.jms.ConnectionFactory at this point
            return (JMSXAQueueConnection) (new JMSXAWrappedQueueConnectionImpl(
                    ((QueueConnectionFactory)wrapped_connectionfactory).createQueueConnection(username, password),
                     this, username, password));
        }
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
        if (wrapped_connectionfactory instanceof XAConnectionFactory) {
            return (JMSXATopicConnection) (new JMSXAWrappedTopicConnectionImpl(
                ((XATopicConnectionFactory)wrapped_connectionfactory).createXATopicConnection(), this, null, null));
        } else {
            //wrapped_connectionfactory cannot be anything than a javax.jms.ConnectionFactory at this point
            return (JMSXATopicConnection) (new JMSXAWrappedTopicConnectionImpl(
                    ((TopicConnectionFactory)wrapped_connectionfactory).createTopicConnection(), this, null, null));
        }
    }

    /**
     * Create an XA topic connection with specific user identity.
     * The connection is created in stopped mode. No messages
     * will be delivered until <code>Connection.start</code> method
     * is explicitly called.
     *  
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created XA topic connection.
     *
     * @exception JMSException if JMS Provider fails to create XA topic Connection
     *                         due to some internal error.
     * @exception JMSSecurityException  if client authentication fails due to
     *                         invalid user name or password.
     */
    public JMSXATopicConnection createXATopicConnection(String username,
                                                        String password) throws JMSException {
        if (wrapped_connectionfactory instanceof XAConnectionFactory) {
            return (JMSXATopicConnection) (new JMSXAWrappedTopicConnectionImpl(
                ((XATopicConnectionFactory)
                  wrapped_connectionfactory).createXATopicConnection(username, password),
                 this, username, password));
        } else {
            //wrapped_connectionfactory cannot be anything than a javax.jms.ConnectionFactory at this point
            return (JMSXATopicConnection) (new JMSXAWrappedTopicConnectionImpl(
                    ((TopicConnectionFactory)wrapped_connectionfactory).createTopicConnection(username, password),
                     this, username, password));
        }
    }
}

