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
 * @(#)JMSXATopicConnectionFactory.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

import javax.jms.*;

/** An JMSXATopicConnectionFactory creates JMSXATopicConnection and provides the 
  * associated TopicConnectionFactory.
  *
  * @see         javax.jms.TopicConnectionFactory
  * @see         com.sun.jms.spi.xa.JMSXAConnectionFactory
  */

public interface JMSXATopicConnectionFactory 
	extends JMSXAConnectionFactory {

    /** Create an XA topic connection with default user identity.
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

    JMSXATopicConnection
    createXATopicConnection() throws JMSException;


    /** Create an XA topic connection with specified user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until <code>Connection.start</code> method
      * is explicitly called.
      *  
      * @param userName the caller's user name
      * @param password the caller's password
      *  
      * @return a newly created XA topic connection.
      *
      * @exception JMSException if JMS Provider fails to create XA topi connection
      *                         due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         invalid user name or password.
      */ 

    JMSXATopicConnection
    createXATopicConnection(String userName, String password) 
					     throws JMSException;
					     
}
