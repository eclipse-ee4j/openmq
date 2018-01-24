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

import java.io.Serializable;

/** <P>A {@code Session} object is a single-threaded context for producing and consuming 
  * messages. Although it may allocate provider resources outside the Java 
  * virtual machine (JVM), it is considered a lightweight JMS object.
  *
  * <P>A session serves several purposes:
  *
  * <UL>
  *   <LI>It is a factory for its message producers and consumers.
  *   <LI>It supplies provider-optimized message factories.
  *   <LI>It is a factory for {@code TemporaryTopics} and 
  *        {@code TemporaryQueues}. 
  *   <LI> It provides a way to create {@code Queue} or {@code Topic}
  *      objects for those clients that need to dynamically manipulate 
  *      provider-specific destination names.
  *   <LI>It supports a single series of transactions that combine work 
  *       spanning its producers and consumers into atomic units.
  *   <LI>It defines a serial order for the messages it consumes and 
  *       the messages it produces.
  *   <LI>It retains messages it consumes until they have been 
  *       acknowledged.
  *   <LI>It serializes execution of message listeners registered with 
  *       its message consumers.
  *   <LI> It is a factory for {@code QueueBrowsers}.
  * </UL>
  *
  * <P>A session can create and service multiple message producers and 
  * consumers.
  *
  * <P>One typical use is to have a thread block on a synchronous 
  * {@code MessageConsumer} until a message arrives. The thread may then
  * use one or more of the {@code Session}'s {@code MessageProducer}s.
  *
  * <P>If a client desires to have one thread produce messages while others 
  * consume them, the client should use a separate session for its producing 
  * thread.
  *
  * <P>Once a connection has been started, any session with one or more 
  * registered message listeners is dedicated to the thread of control that 
  * delivers messages to it. It is erroneous for client code to use this session
  * or any of its constituent objects from another thread of control. The
  * only exception to this rule is the use of the session or message consumer 
  * {@code close} method.
  *
  * <P>It should be easy for most clients to partition their work naturally
  * into sessions. This model allows clients to start simply and incrementally
  * add message processing complexity as their need for concurrency grows.
  *
  * <P>The {@code close} method is the only session method that can be 
  * called while some other session method is being executed in another thread.
  *
  * <P>A session may be specified as transacted. Each transacted 
  * session supports a single series of transactions. Each transaction groups 
  * a set of message sends and a set of message receives into an atomic unit 
  * of work. In effect, transactions organize a session's input message 
  * stream and output message stream into series of atomic units. When a 
  * transaction commits, its atomic unit of input is acknowledged and its 
  * associated atomic unit of output is sent. If a transaction rollback is 
  * done, the transaction's sent messages are destroyed and the session's input 
  * is automatically recovered.
  *
  * <P>The content of a transaction's input and output units is simply those 
  * messages that have been produced and consumed within the session's current 
  * transaction.
  *
  * <P>A transaction is completed using either its session's {@code commit}
  * method or its session's {@code rollback} method. The completion of a
  * session's current transaction automatically begins the next. The result is
  * that a transacted session always has a current transaction within which its 
  * work is done.  
  *
  * <P>The Java Transaction Service (JTS) or some other transaction monitor may 
  * be used to combine a session's transaction with transactions on other 
  * resources (databases, other JMS sessions, etc.). Since Java distributed 
  * transactions are controlled via the Java Transaction API (JTA), use of the 
  * session's {@code commit} and {@code rollback} methods in 
  * this context is prohibited.
  *
  * <P>The JMS API does not require support for JTA; however, it does define 
  * how a provider supplies this support.
  *
  * <P>Although it is also possible for a JMS client to handle distributed 
  * transactions directly, it is unlikely that many JMS clients will do this.
  * Support for JTA in the JMS API is targeted at systems vendors who will be 
  * integrating the JMS API into their application server products.
  * 
  * @see         javax.jms.QueueSession
  * @see         javax.jms.TopicSession
  * @see         javax.jms.XASession
  *
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */ 
 
public interface Session extends Runnable, AutoCloseable {

    /** With this acknowledgment mode, the session automatically acknowledges
      * a client's receipt of a message either when the session has successfully 
      * returned from a call to {@code receive} or when the message 
      * listener the session has called to process the message successfully 
      * returns.
      */ 

    static final int AUTO_ACKNOWLEDGE = 1;

    /** With this acknowledgment mode, the client acknowledges a consumed 
      * message by calling the message's {@code acknowledge} method. 
      * Acknowledging a consumed message acknowledges all messages that the 
      * session has consumed.
      *
      * <P>When client acknowledgment mode is used, a client may build up a 
      * large number of unacknowledged messages while attempting to process 
      * them. A JMS provider should provide administrators with a way to 
      * limit client overrun so that clients are not driven to resource 
      * exhaustion and ensuing failure when some resource they are using 
      * is temporarily blocked.
      *
      * @see javax.jms.Message#acknowledge()
      */ 

    static final int CLIENT_ACKNOWLEDGE = 2;

    /** This acknowledgment mode instructs the session to lazily acknowledge 
      * the delivery of messages. This is likely to result in the delivery of 
      * some duplicate messages if the JMS provider fails, so it should only be 
      * used by consumers that can tolerate duplicate messages. Use of this  
      * mode can reduce session overhead by minimizing the work the 
      * session does to prevent duplicates.
      */

    static final int DUPS_OK_ACKNOWLEDGE = 3;
    
    /** This value may be passed as the argument to the 
     * method {@code createSession(int sessionMode)}
     * on the {@code Connection} object
     * to specify that the session should use a local transaction.
     * <p>
     * This value is returned from the method 
     * {@code getAcknowledgeMode} if the session is using a local transaction,
     * irrespective of whether the session was created by calling the
     * method {@code createSession(int sessionMode)} or the 
     * method {@code createSession(boolean transacted, int acknowledgeMode)}.
     * 
     * @since JMS 1.1
     */
    static final int SESSION_TRANSACTED = 0;

    /** Creates a {@code BytesMessage} object. A {@code BytesMessage} 
      * object is used to send a message containing a stream of uninterpreted 
      * bytes.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      *  
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 
    BytesMessage 
    createBytesMessage() throws JMSException; 

 
    /** Creates a {@code MapMessage} object. A {@code MapMessage} 
      * object is used to send a self-defining set of name-value pairs, where 
      * names are {@code String} objects and values are primitive values 
      * in the Java programming language.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      *  
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    MapMessage 
    createMapMessage() throws JMSException; 

 
    /** Creates a {@code Message} object. The {@code Message} 
      * interface is the root interface of all JMS messages. A 
      * {@code Message} object holds all the 
      * standard message header information. It can be sent when a message 
      * containing only header information is sufficient.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      *  
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    Message
    createMessage() throws JMSException;


    /** Creates an {@code ObjectMessage} object. An 
      * {@code ObjectMessage} object is used to send a message 
      * that contains a serializable Java object.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      *  
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    ObjectMessage
    createObjectMessage() throws JMSException; 


    /** Creates an initialized {@code ObjectMessage} object. An 
      * {@code ObjectMessage} object is used 
      * to send a message that contains a serializable Java object.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      *  
      * @param object the object to use to initialize this message
      *
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    ObjectMessage
    createObjectMessage(Serializable object) throws JMSException;

 
    /** Creates a {@code StreamMessage} object. A 
      * {@code StreamMessage} object is used to send a 
      * self-defining stream of primitive values in the Java programming 
      * language.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      * 
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */

    StreamMessage 
    createStreamMessage() throws JMSException;  

 
    /** Creates a {@code TextMessage} object. A {@code TextMessage} 
      * object is used to send a message containing a {@code String}
      * object.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      * 
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    TextMessage 
    createTextMessage() throws JMSException; 


    /** Creates an initialized {@code TextMessage} object. A 
      * {@code TextMessage} object is used to send 
      * a message containing a {@code String}.
      * <p>
      * The message object returned may be sent using any {@code Session} or {@code JMSContext}. 
      * It is not restricted to being sent using the {@code JMSContext} used to create it.
      * <p>
      * The message object returned may be optimised for use with the JMS provider
      * used to create it. However it can be sent using any JMS provider, not just the 
      * JMS provider used to create it.
      * 
      * @param text the string used to initialize this message
      *
      * @exception JMSException if the JMS provider fails to create this message
      *                         due to some internal error.
      */ 

    TextMessage
    createTextMessage(String text) throws JMSException;


    /** Indicates whether the session is in transacted mode.
      *  
      * @return true if the session is in transacted mode
      *  
      * @exception JMSException if the JMS provider fails to return the 
      *                         transaction mode due to some internal error.
      */ 

    boolean
    getTransacted() throws JMSException;
    
    /** Returns the acknowledgement mode of the session. The acknowledgement
     * mode is set at the time that the session is created. If the session is
     * transacted, the acknowledgement mode is ignored.
     *
     * @return          If the session is not transacted, returns the 
     *                  current acknowledgement mode for the session.
     *                  If the session
     *                  is transacted, returns SESSION_TRANSACTED.
     *
     * @exception JMSException   if the JMS provider fails to return the 
     *                         acknowledgment mode due to some internal error.
     *
     * @see Connection#createSession
     * 
     * @since JMS 1.1
     * 
     */
    int 
    getAcknowledgeMode() throws JMSException;


    /**
	 * Commits all messages done in this transaction and releases any locks
	 * currently held.
	 * <p>
	 * This method must not return until any incomplete asynchronous send
	 * operations for this <tt>Session</tt> have been completed and any
	 * <tt>CompletionListener</tt> callbacks have returned. Incomplete sends
	 * should be allowed to complete normally unless an error occurs.
	 * <p>
	 * A <tt>CompletionListener</tt> callback method must not call
	 * <tt>commit</tt> on its own <tt>Session</tt>. Doing so will cause an
	 * <tt>IllegalStateException</tt> to be thrown.
	 * <p>
	 * 
	 * @exception IllegalStateException
	 *                <ul>
	 *                <li>the session is not using a local transaction
	 *                <li>this method has been called by a <tt>CompletionListener</tt> callback method on its own <tt>Session</tt></li>
	 *                </ul>
	 * @exception JMSException
	 *                if the JMS provider fails to commit the transaction due to
	 *                some internal error.
	 * @exception TransactionRolledBackException
	 *                if the transaction is rolled back due to some internal
	 *                error during commit.
	 */

    void
    commit() throws JMSException;


    /** Rolls back any messages done in this transaction and releases any locks 
      * currently held.
	 * <p>
	 * This method must not return until any incomplete asynchronous send
	 * operations for this <tt>Session</tt> have been completed and any
	 * <tt>CompletionListener</tt> callbacks have returned. Incomplete sends
	 * should be allowed to complete normally unless an error occurs.
	 * <p>
	  * A <tt>CompletionListener</tt> callback method must not call
	  * <tt>commit</tt> on its own <tt>Session</tt>. Doing so will cause an
	  * <tt>IllegalStateException</tt> to be thrown.
	  * <p>
	  * 
	  * @exception IllegalStateException
	  *                <ul>
	  *                <li>the session is not using a local transaction
	  *                <li>this method has been called by a <tt>CompletionListener</tt> callback method on its own <tt>Session</tt></li>
	  *                </ul>
      * @exception JMSException if the JMS provider fails to roll back the
      *                         transaction due to some internal error.
      *                                     
      */

    void
    rollback() throws JMSException;


    /**
	 * Closes the session.
	 * 
	 * <P>
	 * Since a provider may allocate some resources on behalf of a session
	 * outside the JVM, clients should close the resources when they are not
	 * needed. Relying on garbage collection to eventually reclaim these
	 * resources may not be timely enough.
	 * 
	 * <P>
	 * There is no need to close the producers and consumers of a closed
	 * session.
	 * 
	 * <P>
	 * This call will block until a {@code receive} call or message
	 * listener in progress has completed. A blocked message consumer
	 * {@code receive} call returns {@code null} when this session is
	 * closed.
	 * <p>
	 * However if the close method is called from a message listener 
	 * on its own {@code Session}, then it will either fail and throw a 
	 * {@code javax.jms.IllegalStateException}, or it will succeed and 
	 * close the {@code Session}, blocking until any pending receive call in progress
	 * has completed. If close succeeds and the acknowledge mode of the
	 * {@code Session} is set to {@code AUTO_ACKNOWLEDGE}, the current message will still
	 * be acknowledged automatically when the {@code onMessage} call completes.
	 * <p>
	 * Since two alternative behaviors are permitted in this case, 
	 * applications should avoid calling close from a message listener on 
	 * its own {@code Session} because this is not portable.
	 * <p>
	 * This method must not return until any incomplete asynchronous send
	 * operations for this <tt>Session</tt> have been completed and any
	 * <tt>CompletionListener</tt> callbacks have returned. Incomplete sends
	 * should be allowed to complete normally unless an error occurs.
	 * <p>
	 * For the avoidance of doubt, if an exception listener for this session's
	 * connection is running when {@code close} is invoked, there is no
	 * requirement for the {@code close} call to wait until the exception
	 * listener has returned before it may return.
	 * 
	 * <P>
	 * Closing a transacted session must roll back the transaction in progress.
	 * 
	 * <P>
	 * This method is the only {@code Session} method that can be called
	 * concurrently.
	 * <p>
	 * A <tt>CompletionListener</tt> callback method must not call
	 * <tt>close</tt> on its own <tt>Session</tt>. Doing so will cause an
	 * <tt>IllegalStateException</tt> to be thrown.
	 * <p>
	 * Invoking any other {@code Session} method on a closed session must
	 * throw a {@code IllegalStateException}. Closing a closed
	 * session must <I>not</I> throw an exception.
	 * 
	 * @exception IllegalStateException
	 *                <ul>
	 *                <li>this method has been called by a <tt>MessageListener
	 *                </tt> on its own <tt>Session</tt></li> 
	 *                <li>this method has
	 *                been called by a <tt>CompletionListener</tt> callback
	 *                method on its own <tt>Session</tt></li>
	 *                </ul>
	 * @exception JMSException
	 *                if the JMS provider fails to close the session due to some
	 *                internal error.
	 * 
	 */

    void
    close() throws JMSException;


    /** Stops message delivery in this session, and restarts message delivery
      * with the oldest unacknowledged message.
      *  
      * <P>All consumers deliver messages in a serial order.
      * Acknowledging a received message automatically acknowledges all 
      * messages that have been delivered to the client.
      *
      * <P>Restarting a session causes it to take the following actions:
      *
      * <UL>
      *   <LI>Stop message delivery
      *   <LI>Mark all messages that might have been delivered but not 
      *       acknowledged as "redelivered"
      *   <LI>Restart the delivery sequence including all unacknowledged 
      *       messages that had been previously delivered. Redelivered messages
      *       do not have to be delivered in 
      *       exactly their original delivery order.
      * </UL>
      *
      * @exception JMSException if the JMS provider fails to stop and restart
      *                         message delivery due to some internal error.
      * @exception IllegalStateException if the method is called by a 
      *                         transacted session.
      */ 

    void
    recover() throws JMSException;


    /** Returns the session's distinguished message listener (optional).
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSException} to be thrown though this is not guaranteed.
      * 
      * @return the distinguished message listener associated with this session
      *
      * @exception JMSException if the JMS provider fails to get the session's distinguished message  
      *                         listener for one of the following reasons:
      *                         <ul>
      *                         <li>an internal error has occurred
      *                         <li>this method has been called in a Java EE web or EJB application 
      *                         (though it is not guaranteed that an exception is thrown in this case)
      *                         </ul>
      *      
      * @see javax.jms.Session#setMessageListener
      * @see javax.jms.ServerSessionPool
      * @see javax.jms.ServerSession
      */
    MessageListener getMessageListener() throws JMSException; 
    
    /** Sets the session's distinguished message listener (optional).
     *
     * <P>When the distinguished message listener is set, no other form of 
     * message receipt in the session can 
     * be used; however, all forms of sending messages are still supported.
     * 
     * <P>This is an expert facility not used by ordinary JMS clients.
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSException} to be thrown though this is not guaranteed.
     * 
     * @param listener the message listener to associate with this session
     *
     * @exception JMSException if the JMS provider fails to set the session's distinguished message  
     *                         listener for one of the following reasons:
     *                         <ul>
     *                         <li>an internal error has occurred
     *                         <li>this method has been called in a Java EE web or EJB application 
     *                         (though it is not guaranteed that an exception is thrown in this case)
     *                         </ul>
     *
     * @see javax.jms.Session#getMessageListener
     * @see javax.jms.ServerSessionPool
     * @see javax.jms.ServerSession
     */
    void setMessageListener(MessageListener listener) throws JMSException;
    
    /**
     * Optional operation, intended to be used only by Application Servers,
     * not by ordinary JMS clients.
     * <p>
     * This method must not be used in a Java EE web or EJB application. 
     * Doing so may cause a {@code JMSRuntimeException} to be thrown though this is not guaranteed.
     * 
      * @exception JMSRuntimeException if this method has been called in a Java EE web or EJB application 
      *                         (though it is not guaranteed that an exception is thrown in this case)
      *                           
     * @see javax.jms.ServerSession
     */
    public void run();
    
    /** Creates a {@code MessageProducer} to send messages to the specified 
      * destination.
      *
      * <P>A client uses a {@code MessageProducer} object to send 
      * messages to a destination. Since {@code Queue} and {@code Topic} 
      * both inherit from {@code Destination}, they can be used in
      * the destination parameter to create a {@code MessageProducer} object.
      * 
      * @param destination the {@code Destination} to send to, 
      * or null if this is a producer which does not have a specified 
      * destination.
      *
      * @exception JMSException if the session fails to create a MessageProducer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      * is specified.
      *
      * @since JMS 1.1 
      * 
     */

    MessageProducer
    createProducer(Destination destination) throws JMSException;
    
    
       /** Creates a {@code MessageConsumer} for the specified destination.
      * Since {@code Queue} and {@code Topic} 
      * both inherit from {@code Destination}, they can be used in
      * the destination parameter to create a {@code MessageConsumer}.
      *
      * @param destination the {@code Destination} to access. 
      *
      * @exception JMSException if the session fails to create a consumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination 
      *                         is specified.
      *
      * @since JMS 1.1 
      */

    MessageConsumer
    createConsumer(Destination destination) throws JMSException;

       /** Creates a {@code MessageConsumer} for the specified destination, 
      * using a message selector. 
      * Since {@code Queue} and {@code Topic} 
      * both inherit from {@code Destination}, they can be used in
      * the destination parameter to create a {@code MessageConsumer}.
      *
      * <P>A client uses a {@code MessageConsumer} object to receive 
      * messages that have been sent to a destination.
      *  
      *       
      * @param destination the {@code Destination} to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector 
      * for the message consumer. 
      * 
      *  
      * @exception JMSException if the session fails to create a MessageConsumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
       * is specified.
     
      * @exception InvalidSelectorException if the message selector is invalid.
      *
      * @since JMS 1.1 
      * 
      */
    MessageConsumer     
    createConsumer(Destination destination, java.lang.String messageSelector) 
    throws JMSException;
    
    
     /** Creates a {@code MessageConsumer} for the specified destination, specifying a
      * message selector and the {@code noLocal} parameter.
      *<P> Since {@code Queue} and {@code Topic} 
      * both inherit from {@code Destination}, they can be used in
      * the destination parameter to create a {@code MessageConsumer}.
      * <P>A client uses a {@code MessageConsumer} object to receive 
      * messages that have been published to a destination. 
      *               
      * <P>The {@code noLocal} argument is for use when the
      * destination is a topic and the session's connection 
      * is also being used to publish messages to that topic. 
      * If {@code noLocal} is set to true then the 
      * {@code MessageConsumer} will not receive messages published
      * to the topic by its own connection. The default value of this 
      * argument is false. If the destination is a queue
      * then the effect of setting {@code noLocal}
      * to true is not specified.
      *
      * @param destination the {@code Destination} to access 
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered. A value of null or
      * an empty string indicates that there is no message selector 
      * for the message consumer.
      * @param noLocal  - if true, and the destination is a topic,
      *                   then the {@code MessageConsumer} will 
      *                   not receive messages published to the topic
      *                   by its own connection. 
      * 
      * @exception JMSException if the session fails to create a MessageConsumer
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      * is specified.
      *     
      * @exception InvalidSelectorException if the message selector is invalid.
      *
      * @since JMS 1.1 
      *
      */
    MessageConsumer     
    createConsumer(Destination destination, java.lang.String messageSelector, 
    boolean noLocal)   throws JMSException;
    
	/**
	 * Creates a shared non-durable subscription with the specified name on the
	 * specified topic (if one does not already exist) and creates a consumer on
	 * that subscription. This method creates the non-durable subscription
	 * without a message selector.
	 * <p>
	 * If a shared non-durable subscription already exists with the same name
	 * and client identifier (if set), and the same topic and message selector 
	 * value has been specified, then this method creates a
	 * {@code MessageConsumer} on the existing subscription.
	 * <p>
	 * A non-durable shared subscription is used by a client which needs to be
	 * able to share the work of receiving messages from a topic subscription
	 * amongst multiple consumers. A non-durable shared subscription may
	 * therefore have more than one consumer. Each message from the subscription
	 * will be delivered to only one of the consumers on that subscription. Such
	 * a subscription is not persisted and will be deleted (together with any
	 * undelivered messages associated with it) when there are no consumers on
	 * it. The term "consumer" here means a {@code MessageConsumer} or
	 * {@code  JMSConsumer} object in any client.
	 * <p>
	 * A shared non-durable subscription is identified by a name specified by
	 * the client and by the client identifier (which may be unset). An
	 * application which subsequently wishes to create a consumer on that shared
	 * non-durable subscription must use the same client identifier.
	 * <p>
	 * If a shared non-durable subscription already exists with the same name
	 * and client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is a consumer already
	 * active (i.e. not closed) on the subscription, then a {@code JMSException}
	 * will be thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId (which may be unset).
	 * Such subscriptions would be completely separate.
	 * 
	 * @param topic
	 *            the {@code Topic} to subscribe to
	 * @param sharedSubscriptionName
	 *            the name used to identify the shared non-durable subscription
	 * 
	 * @throws JMSException
	 *             if the session fails to create the shared non-durable
	 *             subscription and {@code MessageConsumer} due to some internal
	 *             error.
	 * @throws InvalidDestinationException
	 *             if an invalid topic is specified.
	 * @throws InvalidSelectorException
	 *             if the message selector is invalid.
	 * 
	 * @since JMS 2.0
	 */
	MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException;

	/**
	 * Creates a shared non-durable subscription with the specified name on the
	 * specified topic (if one does not already exist) specifying a message selector,
	 * and creates a consumer on that subscription. 
	 * <p>
	 * If a shared non-durable subscription already exists with the same name
	 * and client identifier (if set), and the same topic and message selector 
	 * has been specified, then this method creates a
	 * {@code MessageConsumer} on the existing subscription.
	 * <p>
	 * A non-durable shared subscription is used by a client which needs to be
	 * able to share the work of receiving messages from a topic subscription
	 * amongst multiple consumers. A non-durable shared subscription may
	 * therefore have more than one consumer. Each message from the subscription
	 * will be delivered to only one of the consumers on that subscription. Such
	 * a subscription is not persisted and will be deleted (together with any
	 * undelivered messages associated with it) when there are no consumers on
	 * it. The term "consumer" here means a {@code MessageConsumer} or
	 * {@code  JMSConsumer} object in any client.
	 * <p>
	 * A shared non-durable subscription is identified by a name specified by
	 * the client and by the client identifier (which may be unset). An
	 * application which subsequently wishes to create a consumer on that shared
	 * non-durable subscription must use the same client identifier.
	 * <p>
	 * If a shared non-durable subscription already exists with the same name
	 * and client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is a consumer already
	 * active (i.e. not closed) on the subscription, then a {@code JMSException}
	 * will be thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId (which may be unset).
	 * Such subscriptions would be completely separate.
	 * 
	 * @param topic
	 *            the {@code Topic} to subscribe to
	 * @param sharedSubscriptionName
	 *            the name used to identify the shared non-durable subscription
	 * @param messageSelector
	 *            only messages with properties matching the message selector
	 *            expression are added to the shared non-durable subscription. A
	 *            value of null or an empty string indicates that there is no
	 *            message selector for the shared non-durable subscription.
	 * 
	 * @throws JMSException
	 *             if the session fails to create the shared non-durable
	 *             subscription and {@code MessageConsumer} due to some
	 *             internal error.
	 * @throws InvalidDestinationException
	 *             if an invalid topic is specified.
	 * @throws InvalidSelectorException
	 *             if the message selector is invalid.
	 * 
	 * @since JMS 2.0
	 */ 
	MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, java.lang.String messageSelector)
			throws JMSException;
    
	/**
	 * Creates a {@code Queue} object which encapsulates a specified
	 * provider-specific queue name.
	 * <p>
	 * The use of provider-specific queue names in an application may render the
	 * application non-portable. Portable applications are recommended to not
	 * use this method but instead look up an administratively-defined
	 * {@code Queue} object using JNDI.
	 * <p>
	 * Note that this method simply creates an object that encapsulates the name
	 * of a queue. It does not create the physical queue in the JMS provider.
	 * JMS does not provide a method to create the physical queue, since this
	 * would be specific to a given JMS provider. Creating a physical queue is
	 * provider-specific and is typically an administrative task performed by an 
	 * administrator, though some providers may create them automatically when
	 * needed. The one exception to this is the creation of a temporary queue,
	 * which is done using the {@code createTemporaryQueue} method.
	 * 
	 * @param queueName
	 *            A provider-specific queue name
	 * @return a Queue object which encapsulates the specified name
	 * 
	 * @throws JMSException
	 *             if a Queue object cannot be created due to some internal error 
	 */
	Queue createQueue(String queueName) throws JMSException;

	/**
	 * Creates a {@code Topic} object which encapsulates a specified
	 * provider-specific topic name.
	 * <p>
	 * The use of provider-specific topic names in an application may render the
	 * application non-portable. Portable applications are recommended to not
	 * use this method but instead look up an administratively-defined
	 * {@code Topic} object using JNDI.
	 * <p>
	 * Note that this method simply creates an object that encapsulates the name
	 * of a topic. It does not create the physical topic in the JMS provider.
	 * JMS does not provide a method to create the physical topic, since this
	 * would be specific to a given JMS provider. Creating a physical topic is
	 * provider-specific and is typically an administrative task performed by an
	 * administrator, though some providers may create them automatically when
	 * needed. The one exception to this is the creation of a temporary topic,
	 * which is done using the {@code createTemporaryTopic} method.
	 * 
	 * @param topicName
	 *            A provider-specific topic name
	 * @return a Topic object which encapsulates the specified name
	 * 
	 * @throws JMSException
	 *             if a Topic object cannot be created due to some internal
	 *             error
	 */
	Topic createTopic(String topicName) throws JMSException;
    
	/**
	 * Creates an unshared durable subscription on the specified topic (if one
	 * does not already exist) and creates a consumer on that durable
	 * subscription. This method creates the durable subscription without a
	 * message selector and with a {@code noLocal} value of {@code false}.
	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with unshared durable subscriptions. Any
	 * durable subscription created using this method will be unshared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by the
	 * client and by the client identifier, which must be set. An application
	 * which subsequently wishes to create a consumer on that unshared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and the same topic, message selector and
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then
	 * this method creates a {@code TopicSubscriber} on the existing durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, then a {@code JMSException} will be
	 * thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier but a different topic, message selector or
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId. Such subscriptions would
	 * be completely separate.
	 * <p>
	 * This method is identical to the corresponding
	 * {@code createDurableConsumer} method except that it returns a
	 * {@code TopicSubscriber} rather than a {@code MessageConsumer} to
	 * represent the consumer.
	 * 
	 * @param topic
	 *            the non-temporary {@code Topic} to subscribe to
	 * @param name
	 *            the name used to identify this subscription
	 * @exception InvalidDestinationException
	 *                if an invalid topic is specified.
	 * @exception IllegalStateException
	 *                if the client identifier is unset 
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the unshared durable
	 *                subscription and {@code TopicSubscriber} due to some
	 *                internal error 
	 *                <li>
	 *                if an unshared durable subscription already exists with
	 *                the same name and client identifier, and there is a
	 *                consumer already active 
	 *                <li>if a shared durable subscription already exists 
	 *                with the same name and client identifier
	 *                </ul>
	 *
 	 * @since JMS 1.1
	 */
    TopicSubscriber createDurableSubscriber(Topic topic, 
			    String name) throws JMSException;

	/**
	 * Creates an unshared durable subscription on the specified topic (if one
	 * does not already exist), specifying a message selector and the
	 * {@code noLocal} parameter, and creates a consumer on that durable
	 * subscription.
	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with unshared durable subscriptions. Any
	 * durable subscription created using this method will be unshared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by the
	 * client and by the client identifier, which must be set. An application
	 * which subsequently wishes to create a consumer on that unshared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and the same topic, message selector and
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then
	 * this method creates a {@code TopicSubscriber} on the existing durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, then a {@code JMSException} will be
	 * thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier but a different topic, message selector or
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * If {@code noLocal} is set to true then any messages published to the topic
	 * using this session's connection, or any other connection with the same client
	 * identifier, will not be added to the durable subscription. 
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId. Such subscriptions would
	 * be completely separate.
	 * <p>
	 * This method is identical to the corresponding
	 * {@code createDurableConsumer} method except that it returns a
	 * {@code TopicSubscriber} rather than a {@code MessageConsumer} to
	 * represent the consumer.
	 * 
	 * @param topic
	 *            the non-temporary {@code Topic} to subscribe to
	 * @param name
	 *            the name used to identify this subscription
	 * @param messageSelector
	 *            only messages with properties matching the message selector
	 *            expression are added to the durable subscription. A value of
	 *            null or an empty string indicates that there is no message
	 *            selector for the durable subscription.
	 * @param noLocal
	 *            if true then any messages published to the topic using this
	 *            session's connection, or any other connection with the same
	 *            client identifier, will not be added to the durable
	 *            subscription.
	 * @exception InvalidDestinationException
	 *                if an invalid topic is specified.
	 * @exception InvalidSelectorException
	 *                if the message selector is invalid.
	 * @exception IllegalStateException
	 *                if the client identifier is unset 
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the unshared durable
	 *                subscription and {@code TopicSubscriber} due to some
	 *                internal error 
	 *                <li>
	 *                if an unshared durable subscription already exists with
	 *                the same name and client identifier, and there is a
	 *                consumer already active 
	 *                <li>if a shared durable
	 *                subscription already exists with the same name and client
	 *                identifier
	 *                </ul>
	 *
 	 * @since JMS 1.1
	 */
	TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
			throws JMSException;
     
	/**
	 * Creates an unshared durable subscription on the specified topic (if one
	 * does not already exist) and creates a consumer on that durable
	 * subscription. This method creates the durable subscription without a
	 * message selector and with a {@code noLocal} value of {@code false}.
	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with unshared durable subscriptions. Any
	 * durable subscription created using this method will be unshared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by the
	 * client and by the client identifier, which must be set. An application
	 * which subsequently wishes to create a consumer on that unshared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and the same topic, message selector and
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then
	 * this method creates a {@code MessageConsumer} on the existing durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, then a {@code JMSException} will be
	 * thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier but a different topic, message selector or
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId. Such subscriptions would
	 * be completely separate.
	 * <p>
	 * This method is identical to the corresponding
	 * {@code createDurableSubscriber} method except that it returns a
	 * {@code MessageConsumer} rather than a {@code TopicSubscriber} to
	 * represent the consumer.
	 * 
	 * @param topic
	 *            the non-temporary {@code Topic} to subscribe to
	 * @param name
	 *            the name used to identify this subscription
	 * @exception InvalidDestinationException
	 *                if an invalid topic is specified.
	 * @exception IllegalStateException
	 *                if the client identifier is unset 
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the unshared durable
	 *                subscription and {@code MessageConsumer} due to some
	 *                internal error 
	 *                <li>
	 *                if an unshared durable subscription already exists with
	 *                the same name and client identifier, and there is a
	 *                consumer already active 
	 *                <li>if a shared durable
	 *                subscription already exists with the same name and client
	 *                identifier
	 *                </ul>
	 * 
	 * @since JMS 2.0
	 */
	MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException;

 	/**
 	 * Creates an unshared durable subscription on the specified topic (if one
 	 * does not already exist), specifying a message selector and the
 	 * {@code noLocal} parameter, and creates a consumer on that durable
 	 * subscription.
	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with unshared durable subscriptions. Any
	 * durable subscription created using this method will be unshared. This
	 * means that only one active (i.e. not closed) consumer on the subscription
	 * may exist at a time. The term "consumer" here means a
	 * {@code TopicSubscriber}, {@code  MessageConsumer} or {@code JMSConsumer}
	 * object in any client.
	 * <p>
	 * An unshared durable subscription is identified by a name specified by the
	 * client and by the client identifier, which must be set. An application
	 * which subsequently wishes to create a consumer on that unshared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and the same topic, message selector and
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then
	 * this method creates a {@code MessageConsumer} on the existing durable subscription.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier, and there is a consumer already active (i.e. not
	 * closed) on the durable subscription, then a {@code JMSException} will be
	 * thrown.
	 * <p>
	 * If an unshared durable subscription already exists with the same name and
	 * client identifier but a different topic, message selector or
	 * {@code noLocal} value has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * If {@code noLocal} is set to true then any messages published to the topic
	 * using this session's connection, or any other connection with the same client
	 * identifier, will not be added to the durable subscription. 
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier. If a shared durable
	 * subscription already exists with the same name and client identifier then
	 * a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId. Such subscriptions would
	 * be completely separate.
	 * <p>
	 * This method is identical to the corresponding
	 * {@code createDurableSubscriber} method except that it returns a
	 * {@code MessageConsumer} rather than a {@code TopicSubscriber} to
	 * represent the consumer.
 	 * 
 	 * @param topic
 	 *            the non-temporary {@code Topic} to subscribe to
 	 * @param name
 	 *            the name used to identify this subscription
 	 * @param messageSelector
 	 *            only messages with properties matching the message selector
 	 *            expression are added to the durable subscription. A value of
 	 *            null or an empty string indicates that there is no message
 	 *            selector for the durable subscription.
 	 * @param noLocal
 	 *            if true then any messages published to the topic using this
 	 *            session's connection, or any other connection with the same
 	 *            client identifier, will not be added to the durable
 	 *            subscription.
 	 * @exception InvalidDestinationException
 	 *                if an invalid topic is specified.
 	 * @exception InvalidSelectorException
 	 *                if the message selector is invalid.
	 * @exception IllegalStateException
	 *                if the client identifier is unset 
 	 * @exception JMSException
 	 *                <ul>
 	 *                <li>if the session fails to create the unshared durable
 	 *                subscription and {@code MessageConsumer} due to some
 	 *                internal error 
 	 *                <li>
 	 *                if an unshared durable subscription already exists with
 	 *                the same name and client identifier, and there is a
 	 *                consumer already active 
 	 *                <li>if a shared durable
 	 *                subscription already exists with the same name and client
 	 *                identifier
 	 *                </ul>
 	 * 
	 * @since JMS 2.0
	 */ 
      MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException;     
      
	/**
	 * Creates a shared durable subscription on the specified topic (if one does
	 * not already exist), specifying a message selector and the {@code noLocal}
	 * parameter, and creates a consumer on that durable subscription. This
	 * method creates the durable subscription without a message selector.
	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with shared durable subscriptions. Any
	 * durable subscription created using this method will be shared. This means
	 * that multiple active (i.e. not closed) consumers on the subscription may
	 * exist at the same time. The term "consumer" here means a
	 * {@code  MessageConsumer} or {@code JMSConsumer} object in any client.
	 * <p>
	 * A shared durable subscription is identified by a name specified by the
	 * client and by the client identifier (which may be unset). An application
	 * which subsequently wishes to create a consumer on that shared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set), and the same topic and message selector 
	 * has been specified, then this method creates a
	 * {@code MessageConsumer} on the existing shared durable subscription.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is a consumer already
	 * active (i.e. not closed) on the durable subscription, then a
	 * {@code JMSException} will be thrown.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier (if set). If an unshared
	 * durable subscription already exists with the same name and client
	 * identifier (if set) then a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId (which may be unset).
	 * Such subscriptions would be completely separate.
	 * <p>
	 * 
	 * @param topic
	 *            the non-temporary {@code Topic} to subscribe to
	 * @param name
	 *            the name used to identify this subscription
	 * @exception JMSException
	 *                <ul>
	 *                <li>if the session fails to create the shared durable
	 *                subscription and {@code MessageConsumer} due to some
	 *                internal error 
	 *                <li> if a shared durable subscription
	 *                already exists with the same name and client identifier,
	 *                but a different topic or message selector, 
	 *                and there is a consumer already active 
	 *                <li>if an
	 *                unshared durable subscription already exists with the same
	 *                name and client identifier
	 *                </ul>
	 * @exception InvalidDestinationException
	 *                if an invalid topic is specified.
	 * 
	 * @since JMS 2.0
	 */
	MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException;

   	/**
   	 * Creates a shared durable subscription on the specified topic (if one
   	 * does not already exist), specifying a message selector,
   	 * and creates a consumer on that durable subscription.
   	 * <p>
	 * A durable subscription is used by an application which needs to receive
	 * all the messages published on a topic, including the ones published when
	 * there is no active consumer associated with it. The JMS provider retains
	 * a record of this durable subscription and ensures that all messages from
	 * the topic's publishers are retained until they are delivered to, and
	 * acknowledged by, a consumer on this durable subscription or until they
	 * have expired.
	 * <p>
	 * A durable subscription will continue to accumulate messages until it is
	 * deleted using the {@code unsubscribe} method.
	 * <p>
	 * This method may only be used with shared durable subscriptions. Any
	 * durable subscription created using this method will be shared. This means
	 * that multiple active (i.e. not closed) consumers on the subscription may
	 * exist at the same time. The term "consumer" here means a
	 * {@code  MessageConsumer} or {@code JMSConsumer} object in any client.
	 * <p>
	 * A shared durable subscription is identified by a name specified by the
	 * client and by the client identifier (which may be unset). An application
	 * which subsequently wishes to create a consumer on that shared durable
	 * subscription must use the same client identifier.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set), and the same topic and message selector 
	 * has been specified, then this method creates a
	 * {@code MessageConsumer} on the existing shared durable subscription.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is no consumer
	 * already active (i.e. not closed) on the durable subscription then this is
	 * equivalent to unsubscribing (deleting) the old one and creating a new
	 * one.
	 * <p>
	 * If a shared durable subscription already exists with the same name and
	 * client identifier (if set) but a different topic or message selector 
	 * has been specified, and there is a consumer already
	 * active (i.e. not closed) on the durable subscription, then a
	 * {@code JMSException} will be thrown.
	 * <p>
	 * A shared durable subscription and an unshared durable subscription may
	 * not have the same name and client identifier (if set). If an unshared
	 * durable subscription already exists with the same name and client
	 * identifier (if set) then a {@code JMSException} is thrown.
	 * <p>
	 * There is no restriction on durable subscriptions and shared non-durable
	 * subscriptions having the same name and clientId (which may be unset).
	 * Such subscriptions would be completely separate.
	 * <p>
   	 * 
   	 * @param topic
   	 *            the non-temporary {@code Topic} to subscribe to
   	 * @param name
   	 *            the name used to identify this subscription
   	 * @param messageSelector
   	 *            only messages with properties matching the message selector
   	 *            expression are added to the durable subscription. A value of
   	 *            null or an empty string indicates that there is no message
   	 *            selector for the durable subscription.
   	 * @exception JMSException
   	 *                <ul>
   	 *                <li>if the session fails to create the shared durable
   	 *                subscription and {@code MessageConsumer} due to some
   	 *                internal error 
   	 *                <li>
   	 *                if a shared durable subscription already exists with
   	 *                the same name and client identifier, but a different topic
   	 *                or message selector,
   	 *                and there is a consumer already active 
   	 *                <li>if an unshared durable
   	 *                subscription already exists with the same name and client
   	 *                identifier
   	 *                </ul>
   	 * @exception InvalidDestinationException
   	 *                if an invalid topic is specified.
   	 * @exception InvalidSelectorException
   	 *                if the message selector is invalid.
   	 *
     * @since JMS 2.0
   	 */
        MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException;           
    
  /** Creates a {@code QueueBrowser} object to peek at the messages on 
      * the specified queue.
      *  
      * @param queue the {@code queue} to access
      *
      *  
      * @exception JMSException if the session fails to create a browser
      *                         due to some internal error.
      * @exception InvalidDestinationException if an invalid destination
      *                         is specified 
      *
      * @since JMS 1.1 
      */ 
    QueueBrowser 
    createBrowser(Queue queue) throws JMSException;


	/**
	 * Creates a {@code QueueBrowser} object to peek at the messages on the
	 * specified queue using a message selector.
	 * 
	 * @param queue
	 *            the {@code queue} to access
	 * 
	 * @param messageSelector
	 *            only messages with properties matching the message selector
	 *            expression are delivered. A value of null or an empty string
	 *            indicates that there is no message selector for the message
	 *            consumer.
	 * 
	 * @exception JMSException
	 *                if the session fails to create a browser due to some
	 *                internal error.
	 * @exception InvalidDestinationException
	 *                if an invalid destination is specified
	 * @exception InvalidSelectorException
	 *                if the message selector is invalid.
	 * 
	 * @since JMS 1.1
	 * 
	 */
	QueueBrowser createBrowser(Queue queue, String messageSelector)
			throws JMSException;

    
     /** Creates a {@code TemporaryQueue} object. Its lifetime will be that 
      * of the {@code Connection} unless it is deleted earlier.
      *
      * @return a temporary queue identity
      *
      * @exception JMSException if the session fails to create a temporary queue
      *                         due to some internal error.
      *
      * @since JMS 1.1
      */

    TemporaryQueue
    createTemporaryQueue() throws JMSException;
   

     /** Creates a {@code TemporaryTopic} object. Its lifetime will be that 
      * of the {@code Connection} unless it is deleted earlier.
      *
      * @return a temporary topic identity
      *
      * @exception JMSException if the session fails to create a temporary
      *                         topic due to some internal error.
      *
      * @since JMS 1.1  
      */
 
    TemporaryTopic
    createTemporaryTopic() throws JMSException;


    /** Unsubscribes a durable subscription that has been created by a client.
      *  
      * <P>This method deletes the state being maintained on behalf of the 
      * subscriber by its provider.
      * <p> 
      * A durable subscription is identified by a name specified by the client
      * and by the client identifier if set. If the client identifier was set
      * when the durable subscription was created then a client which 
      * subsequently wishes to use this method to
      * delete a durable subscription must use the same client identifier.
      *
      * <P>It is erroneous for a client to delete a durable subscription
      * while there is an active (not closed) consumer for the 
      * subscription, or while a consumed message is part of a pending 
      * transaction or has not been acknowledged in the session.
      *
      * @param name the name used to identify this subscription
      *  
      * @exception JMSException if the session fails to unsubscribe to the 
      *                         durable subscription due to some internal error.
      * @exception InvalidDestinationException if an invalid subscription name
      *                                        is specified.
      *
      * @since JMS 1.1
      */

    void
    unsubscribe(String name) throws JMSException;
   
}
