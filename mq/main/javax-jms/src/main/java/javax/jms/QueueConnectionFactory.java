/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;

/** A client uses a {@code QueueConnectionFactory} object to create 
  * {@code QueueConnection} objects with a point-to-point JMS provider.
  *
  * <P>{@code QueueConnectionFactory} can be used to create a 
  * {@code QueueConnection}, from which specialized queue-related objects
  * can be  created. A more general, and recommended,  approach 
  * is to use the {@code ConnectionFactory} object.
  *
  *<P> The {@code QueueConnectionFactory} object
  * can be used to support existing code that already uses it.
  *
  * @see javax.jms.ConnectionFactory
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface QueueConnectionFactory extends ConnectionFactory {

    /** Creates a queue connection with the default user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *
      .
      *
      * @return a newly created queue connection
      *
      * @exception JMSException if the JMS provider fails to create the queue 
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      */ 

    QueueConnection
    createQueueConnection() throws JMSException;


    /** Creates a queue connection with the specified user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *  
      * @param userName the caller's user name
      * @param password the caller's password
      *  
      * @return a newly created queue connection
      *
      * @exception JMSException if the JMS provider fails to create the queue 
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      */ 

    QueueConnection
    createQueueConnection(String userName, String password) 
					     throws JMSException;
}
