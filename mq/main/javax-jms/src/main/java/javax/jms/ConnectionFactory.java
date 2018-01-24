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

/** A {@code ConnectionFactory} object encapsulates a set of connection 
  * configuration 
  * parameters that has been defined by an administrator. A client uses 
  * it to create a connection with a JMS provider.
  *
  * <P>A {@code ConnectionFactory} object is a JMS administered object and
  *  supports concurrent use.
  *
  * <P>JMS administered objects are objects containing configuration 
  * information that are created by an administrator and later used by 
  * JMS clients. They make it practical to administer the JMS API in the 
  * enterprise.
  *
  * <P>Although the interfaces for administered objects do not explicitly 
  * depend on the Java Naming and Directory Interface (JNDI) API, the JMS API 
  * establishes the convention that JMS clients find administered objects by 
  * looking them up in a JNDI namespace.
  *
  * <P>An administrator can place an administered object anywhere in a 
  * namespace. The JMS API does not define a naming policy.
  *
  * <P>It is expected that JMS providers will provide the tools an
  * administrator needs to create and configure administered objects in a 
  * JNDI namespace. JMS provider implementations of administered objects 
  * should be both {@code javax.jndi.Referenceable} and 
  * {@code java.io.Serializable} so that they can be stored in all 
  * JNDI naming contexts. In addition, it is recommended that these 
  * implementations follow the JavaBeans<SUP><FONT SIZE="-2">TM</FONT></SUP> 
  * design patterns.
  *
  * <P>This strategy provides several benefits:
  *
  * <UL>
  *   <LI>It hides provider-specific details from JMS clients.
  *   <LI>It abstracts administrative information into objects in the Java 
  *       programming language ("Java objects")
  *       that are easily organized and administered from a common 
  *       management console.
  *   <LI>Since there will be JNDI providers for all popular naming 
  *       services, this means that JMS providers can deliver one implementation 
  *       of administered objects that will run everywhere.
  * </UL>
  *
  * <P>An administered object should not hold on to any remote resources. 
  * Its lookup should not use remote resources other than those used by the
  * JNDI API itself.
  *
  * <P>Clients should think of administered objects as local Java objects. 
  * Looking them up should not have any hidden side effects or use surprising 
  * amounts of local resources.
  *
  * @see         javax.jms.Connection
  * @see         javax.jms.QueueConnectionFactory
  * @see         javax.jms.TopicConnectionFactory
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface ConnectionFactory {
        /** Creates a connection with the default user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *
      * @return a newly created connection
      *
      * @exception JMSException if the JMS provider fails to create the
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      * @since JMS 1.1 
     */ 

    Connection
    createConnection() throws JMSException;


    /** Creates a connection with the specified user identity.
      * The connection is created in stopped mode. No messages 
      * will be delivered until the {@code Connection.start} method
      * is explicitly called.
      *  
      * @param userName the caller's user name
      * @param password the caller's password
      *  
      * @return a newly created  connection
      *
      * @exception JMSException if the JMS provider fails to create the 
      *                         connection due to some internal error.
      * @exception JMSSecurityException  if client authentication fails due to 
      *                         an invalid user name or password.
      * @since JMS 1.1  
      */ 

    Connection
    createConnection(String userName, String password) 
					     throws JMSException;
    
    /** 
     * Creates a JMSContext with the default user identity
     * and an unspecified sessionMode. 
     * <p>
     * A connection and session are created for use by the new JMSContext. 
     * The connection is created in stopped mode but will be automatically started
     * when a JMSConsumer is created.
     * <p>
     * The behaviour of the session that is created depends on 
     * whether this method is called in a Java SE environment, 
     * in the Java EE application client container, or in the Java EE web or EJB container.
     * If this method is called in the Java EE web or EJB container then the 
     * behaviour of the session also depends on whether or not 
     * there is an active JTA transaction in progress.   
     * <p>
     * In a <b>Java SE environment</b> or in <b>the Java EE application client container</b>:
     * <ul>
     * <li>The session will be non-transacted and received messages will be acknowledged automatically
     * using an acknowledgement mode of {@code JMSContext.AUTO_ACKNOWLEDGE} 
     * For a definition of the meaning of this acknowledgement mode see the link below.
     * </ul>
     * <p>
     * In a <b>Java EE web or EJB container, when there is an active JTA transaction in progress</b>:
     * <ul>
     * <li>The session will participate in the JTA transaction and will be committed or rolled back
     * when that transaction is committed or rolled back, 
     * not by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods.
     * </ul>
     * <p>
     * In the <b>Java EE web or EJB container, when there is no active JTA transaction in progress</b>:
     * <ul>
     * <li>The session will be non-transacted and received messages will be acknowledged automatically
     * using an acknowledgement mode of {@code JMSContext.AUTO_ACKNOWLEDGE} 
     * For a definition of the meaning of this acknowledgement mode see the link below.
     * </ul> 
     *
     * @return a newly created JMSContext
     *
     * @exception JMSRuntimeException if the JMS provider fails to create the
     *                         JMSContext due to some internal error.
     * @exception JMSSecurityRuntimeException  if client authentication fails due to 
     *                         an invalid user name or password.
     * @since JMS 2.0 
     * 
     * @see JMSContext#AUTO_ACKNOWLEDGE 
     * 
     * @see javax.jms.ConnectionFactory#createContext(int) 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String) 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String, int) 
     * @see javax.jms.JMSContext#createContext(int) 
     */
    JMSContext createContext();
   
    /** 
     * Creates a JMSContext with the specified user identity
     * and an unspecified sessionMode. 
     * <p>
     * A connection and session are created for use by the new JMSContext. 
     * The connection is created in stopped mode but will be automatically started
     * when a JMSConsumer.
     * <p>
     * The behaviour of the session that is created depends on 
     * whether this method is called in a Java SE environment, 
     * in the Java EE application client container, or in the Java EE web or EJB container.
     * If this method is called in the Java EE web or EJB container then the 
     * behaviour of the session also depends on whether or not 
     * there is an active JTA transaction in progress.   
     * <p>
     * In a <b>Java SE environment</b> or in <b>the Java EE application client container</b>:
     * <ul>
     * <li>The session will be non-transacted and received messages will be acknowledged automatically
     * using an acknowledgement mode of {@code JMSContext.AUTO_ACKNOWLEDGE} 
     * For a definition of the meaning of this acknowledgement mode see the link below.
     * </ul>
     * <p>
     * In a <b>Java EE web or EJB container, when there is an active JTA transaction in progress</b>:
     * <ul>
     * <li>The session will participate in the JTA transaction and will be committed or rolled back
     * when that transaction is committed or rolled back, 
     * not by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods.
     * </ul>
     * <p>
     * In the <b>Java EE web or EJB container, when there is no active JTA transaction in progress</b>:
     * <ul>
     * <li>The session will be non-transacted and received messages will be acknowledged automatically
     * using an acknowledgement mode of {@code JMSContext.AUTO_ACKNOWLEDGE} 
     * For a definition of the meaning of this acknowledgement mode see the link below.
     * </ul> 
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
     * @see JMSContext#AUTO_ACKNOWLEDGE 
     * 
     * @see javax.jms.ConnectionFactory#createContext() 
     * @see javax.jms.ConnectionFactory#createContext(int) 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String, int) 
     * @see javax.jms.JMSContext#createContext(int)
     */
    JMSContext createContext(String userName, String password);    

   /** Creates a JMSContext with the specified user identity 
     * and the specified session mode. 
     * <p>
     * A connection and session are created for use by the new JMSContext. 
     * The JMSContext is created in stopped mode but will be automatically started
     * when a JMSConsumer is created.
     * <p>
     * The effect of setting the {@code sessionMode}  
     * argument depends on whether this method is called in a Java SE environment, 
     * in the Java EE application client container, or in the Java EE web or EJB container.
     * If this method is called in the Java EE web or EJB container then the 
     * effect of setting the {@code sessionMode} argument also depends on 
     * whether or not there is an active JTA transaction in progress. 
     * <p>
     * In a <b>Java SE environment</b> or in <b>the Java EE application client container</b>:
     * <ul>
     * <li>If {@code sessionMode} is set to {@code JMSContext.SESSION_TRANSACTED} then the session 
     * will use a local transaction which may subsequently be committed or rolled back 
     * by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods. 
     * <li>If {@code sessionMode} is set to any of 
     * {@code JMSContext.CLIENT_ACKNOWLEDGE}, 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} or
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * then the session will be non-transacted and 
     * messages received by this session will be acknowledged
     * according to the value of {@code sessionMode}.
     * For a definition of the meaning of these acknowledgement modes see the links below.
     * </ul>
     * <p>
     * In a <b>Java EE web or EJB container, when there is an active JTA transaction in progress</b>:
     * <ul>
     * <li>The argument {@code sessionMode} is ignored.
     * The session will participate in the JTA transaction and will be committed or rolled back
     * when that transaction is committed or rolled back, 
     * not by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods.
     * Since the argument is ignored, developers are recommended to use 
     * {@code createContext(String userName, String password)} instead of this method. 
     * </ul>
     * <p>
     * In the <b>Java EE web or EJB container, when there is no active JTA transaction in progress</b>:
     * <ul>
     * <li>The argument {@code acknowledgeMode} must be set to either of 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} or
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * The session will be non-transacted and messages received by this session will be acknowledged
     * automatically according to the value of {@code acknowledgeMode}.
     * For a definition of the meaning of these acknowledgement modes see the links below.
     * The values {@code JMSContext.SESSION_TRANSACTED} and {@code JMSContext.CLIENT_ACKNOWLEDGE} may not be used.
     * </ul> 
     * @param userName the caller's user name
     * @param password the caller's password
     * @param sessionMode indicates which of four possible session modes will be used.
     * <ul>
     * <li>If this method is called in a Java SE environment or in the Java EE application client container, 
     * the permitted values are 
     * {@code JMSContext.SESSION_TRANSACTED}, 
     * {@code JMSContext.CLIENT_ACKNOWLEDGE}, 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} and
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}. 
     * <li> If this method is called in the Java EE web or EJB container when there is an active JTA transaction in progress 
     * then this argument is ignored.
     * <li>If this method is called in the Java EE web or EJB container when there is no active JTA transaction in progress, the permitted values are
     * {@code JMSContext.AUTO_ACKNOWLEDGE} and
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * In this case the values {@code JMSContext.TRANSACTED} and {@code JMSContext.CLIENT_ACKNOWLEDGE} are not permitted.
     * </ul>
     *  
     * @return a newly created JMSContext
     *
     * @exception JMSRuntimeException if the JMS provider fails to create the 
     *                         JMSContext due to some internal error.
     * @exception JMSSecurityRuntimeException  if client authentication fails due to 
     *                         an invalid user name or password.
     * @since JMS 2.0  
     * 
     * @see JMSContext#SESSION_TRANSACTED 
     * @see JMSContext#CLIENT_ACKNOWLEDGE 
     * @see JMSContext#AUTO_ACKNOWLEDGE 
     * @see JMSContext#DUPS_OK_ACKNOWLEDGE 
     * 
     * @see javax.jms.ConnectionFactory#createContext() 
     * @see javax.jms.ConnectionFactory#createContext(int) 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String) 
     * @see javax.jms.JMSContext#createContext(int) 
     */ 
    JMSContext createContext(String userName, String password, int sessionMode);    
   
   /** Creates a JMSContext with the default user identity
     * and the specified session mode. 
     * <p> 
     * A connection and session are created for use by the new JMSContext. 
     * The JMSContext is created in stopped mode but will be automatically started
     * when a JMSConsumer is created.
     * <p>
     * The effect of setting the {@code sessionMode}  
     * argument depends on whether this method is called in a Java SE environment, 
     * in the Java EE application client container, or in the Java EE web or EJB container.
     * If this method is called in the Java EE web or EJB container then the 
     * effect of setting the {@code sessionMode} argument also depends on 
     * whether or not there is an active JTA transaction in progress. 
     * <p>
     * In a <b>Java SE environment</b> or in <b>the Java EE application client container</b>:
     * <ul>
     * <li>If {@code sessionMode} is set to {@code JMSContext.SESSION_TRANSACTED} then the session 
     * will use a local transaction which may subsequently be committed or rolled back 
     * by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods. 
     * <li>If {@code sessionMode} is set to any of 
     * {@code JMSContext.CLIENT_ACKNOWLEDGE}, 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} or
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * then the session will be non-transacted and 
     * messages received by this session will be acknowledged
     * according to the value of {@code sessionMode}.
     * For a definition of the meaning of these acknowledgement modes see the links below.
     * </ul>
     * <p>
     * In a <b>Java EE web or EJB container, when there is an active JTA transaction in progress</b>:
     * <ul>
     * <li>The argument {@code sessionMode} is ignored.
     * The session will participate in the JTA transaction and will be committed or rolled back
     * when that transaction is committed or rolled back, 
     * not by calling the {@code JMSContext}'s {@code commit} or {@code rollback} methods.
     * Since the argument is ignored, developers are recommended to use 
     * {@code createContext()} instead of this method.
     * </ul>
     * <p>
     * In the <b>Java EE web or EJB container, when there is no active JTA transaction in progress</b>:
     * <ul>
     * <li>The argument {@code acknowledgeMode} must be set to either of 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} or
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * The session will be non-transacted and messages received by this session will be acknowledged
     * automatically according to the value of {@code acknowledgeMode}.
     * For a definition of the meaning of these acknowledgement modes see the links below.
     * The values {@code JMSContext.SESSION_TRANSACTED} and {@code JMSContext.CLIENT_ACKNOWLEDGE} may not be used.
     * </ul> 
     *
     * @param sessionMode indicates which of four possible session modes will be used.
     * <ul>
     * <li>If this method is called in a Java SE environment or in the Java EE application client container, 
     * the permitted values are 
     * {@code JMSContext.SESSION_TRANSACTED}, 
     * {@code JMSContext.CLIENT_ACKNOWLEDGE}, 
     * {@code JMSContext.AUTO_ACKNOWLEDGE} and
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}. 
     * <li> If this method is called in the Java EE web or EJB container when there is an active JTA transaction in progress 
     * then this argument is ignored.
     * <li>If this method is called in the Java EE web or EJB container when there is no active JTA transaction in progress, the permitted values are
     * {@code JMSContext.AUTO_ACKNOWLEDGE} and
     * {@code JMSContext.DUPS_OK_ACKNOWLEDGE}.
     * In this case the values {@code JMSContext.TRANSACTED} and {@code JMSContext.CLIENT_ACKNOWLEDGE} are not permitted.
     * </ul>
     * 
     * @return a newly created JMSContext
     * 
     * @exception JMSRuntimeException if the JMS provider fails to create the
     *                         JMSContext due to some internal error.
     * @exception JMSSecurityRuntimeException  if client authentication fails due to 
     *                         an invalid user name or password.
     * @since JMS 2.0 
     * 
     * @see JMSContext#SESSION_TRANSACTED 
     * @see JMSContext#CLIENT_ACKNOWLEDGE 
     * @see JMSContext#AUTO_ACKNOWLEDGE 
     * @see JMSContext#DUPS_OK_ACKNOWLEDGE 
     * 
     * @see javax.jms.ConnectionFactory#createContext() 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String) 
     * @see javax.jms.ConnectionFactory#createContext(java.lang.String, java.lang.String, int) 
     * @see javax.jms.JMSContext#createContext(int) 
	 */
	JMSContext createContext(int sessionMode);
      
}
