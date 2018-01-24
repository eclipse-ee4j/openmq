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

/** An {@code XAQueueConnectionFactory} provides the same create options as
  * a {@code QueueConnectionFactory} (optional).
  *
  * <P>The {@code XATopicConnectionFactory} interface is optional.  JMS providers 
  * are not required to support this interface. This interface is for 
  * use by JMS providers to support transactional environments. 
  * Client programs are strongly encouraged to use the transactional support
  * available in their environment, rather than use these XA
  * interfaces directly. 
  *
  * @see         javax.jms.QueueConnectionFactory
  * @see         javax.jms.XAConnectionFactory
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface XAQueueConnectionFactory 
       extends XAConnectionFactory, QueueConnectionFactory {

    /** Creates an XA queue connection with the default user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *
      * @return a newly created XA queue connection
      *
      * @exception JMSException if the JMS provider fails to create an XA queue 
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
       */ 

    XAQueueConnection
    createXAQueueConnection() throws JMSException;


    /** Creates an XA queue connection with the specified user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *  
      * @param userName the caller's user name
      * @param password the caller's password
      *  
      * @return a newly created XA queue connection
      *
      * @exception JMSException if the JMS provider fails to create an XA queue 
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      */ 

    XAQueueConnection
    createXAQueueConnection(String userName, String password) 
					     throws JMSException;
}
