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

/** The {@code XAConnectionFactory} interface is a base interface for the
  * {@code XAQueueConnectionFactory} and 
  * {@code XATopicConnectionFactory} interfaces.
  *
  * <P>Some application servers provide support for grouping JTA capable 
  * resource use into a distributed transaction (optional). To include JMS API transactions 
  * in a JTA transaction, an application server requires a JTA aware JMS
  * provider. A JMS provider exposes its JTA support using an
  * {@code XAConnectionFactory} object, which an application server uses 
  * to create {@code XAConnection} objects.
  *
  * <P>{@code XAConnectionFactory} objects are JMS administered objects, 
  * just like {@code ConnectionFactory} objects. It is expected that 
  * application servers will find them using the Java Naming and Directory
  * Interface (JNDI) API.
  *
  *<P>The {@code XAConnectionFactory} interface is optional. JMS providers 
  * are not required to support this interface. This interface is for 
  * use by JMS providers to support transactional environments. 
  * Client programs are strongly encouraged to use the transactional support
  * available in their environment, rather than use these XA
  * interfaces directly. 
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */
public interface XAConnectionFactory {
    
     /** Creates an {@code XAConnection} with the default user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *
      * @return a newly created {@code XAConnection}
      *
      * @exception JMSException if the JMS provider fails to create an XA  
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      * 
      * @since JMS 1.1 
      * 
      */ 

    XAConnection
    createXAConnection() throws JMSException;


    /** Creates an {@code XAConnection} with the specified user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *  
      * @param userName the caller's user name
      * @param password the caller's password
      *  
      * @return a newly created {@code XAConnection}
      *
      * @exception JMSException if the JMS provider fails to create an XA  
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      *
      * @since JMS 1.1 
      * 
      */ 

    XAConnection
    createXAConnection(String userName, String password) 
					     throws JMSException;
    
	/**
	 * Creates a {@code XAJMSContext} with the default user identity
	 * <p>
     * A connection and session are created for use by the new {@code XAJMSContext}. 
     * The connection is created in stopped mode but will be automatically started
     * when a {@code JMSConsumer} is created.
	 * 
	 * @return a newly created {@code XAJMSContext}
	 * 
	 * @exception JMSRuntimeException
	 *                if the JMS provider fails to create the {@code XAJMSContext} due
	 *                to some internal error.
	 * @exception JMSSecurityRuntimeException
	 *                if client authentication fails due to an invalid user name
	 *                or password.
	 * @since JMS 2.0
	 * 
	 */
	XAJMSContext createXAContext();
   
    /** 
     * Creates a JMSContext with the specified user identity
	 * <p>
     * A connection and session are created for use by the new {@code XAJMSContext}. 
     * The connection is created in stopped mode but will be automatically started
     * when a {@code JMSConsumer} is created.
     * 
     * @param userName the caller's user name
     * @param password the caller's password
     *  
     * @return a newly created JMSContext
     *
     * @exception JMSRuntimeException if the JMS provider fails to create the 
     *                         JMSContext due to some internal error.
     * @exception JMSSecurityRuntimeException  if client authentication fails due to 
     *                         an invalid user name or password.
     * @since JMS 2.0 
     * 
     */
    XAJMSContext createXAContext(String userName, String password);    
   
}
