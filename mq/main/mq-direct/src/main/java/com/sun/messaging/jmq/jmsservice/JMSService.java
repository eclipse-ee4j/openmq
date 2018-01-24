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
 * @(#)JMSService.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import javax.transaction.xa.Xid;
import javax.transaction.xa.XAResource;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.JMSPacket;

/**
 *  The interface definition for the interaction between the Sun MQ JMS client
 *  and broker.
 */
public interface JMSService {

    //XXX: Update to implement EnumConverter w/ explicit values for these if req
    //....

    //javax.jms.Message.DEFAULT_TIME_TO_LIVE
    public static final long DEFAULT_TIME_TO_LIVE = 0L;

    //javax.jms.Message.DEFAULT_DELIVERY_DELAY
    public static final long DEFAULT_DELIVERY_DELAY = 0L;
    
    //javax.jms.Message.DEFAULT_DELIVERY_MODE
    public static final MessageDeliveryMode DEFAULT_MessageDeliveryMode = 
                                            MessageDeliveryMode.PERSISTENT;

    public static enum MessageDeliveryMode {
        NON_PERSISTENT,
        PERSISTENT
    }

    //javax.jms.Message.DEFAULT_PRIORITY
    public static final MessagePriority DEFAULT_MessagePriority = 
                                        MessagePriority.PRIORITY_4;

    public static enum MessagePriority {
        PRIORITY_0(0),
        PRIORITY_1(1),
        PRIORITY_2(2),
        PRIORITY_3(3),
        PRIORITY_4(4),
        PRIORITY_5(5),
        PRIORITY_6(6),
        PRIORITY_7(7),
        PRIORITY_8(8),
        PRIORITY_9(9);

        private int pri = 4;

        MessagePriority(int pri) {
            this.pri = pri;
        }
        public int priority() {
            return pri;
        }
    }

    public static enum SessionAckMode {
        UNSPECIFIED,
        TRANSACTED,
        AUTO_ACKNOWLEDGE,
        CLIENT_ACKNOWLEDGE,
        DUPS_OK_ACKNOWLEDGE,
        NO_ACKNOWLEDGE
    }

    /**
     *  Enum values that specify the state of a Transaction
     */
    public static enum TransactionState {
        UNKNOWN,
        CREATED,
        STARTED,
        FAILED,
        INCOMPLETE,
        COMPLETE,
        PREPARED,
        COMMITED,
        ROLLEDBACK,
        TIMEDOUT
    }

    /**
     *  Enum values that specify the AutoRollback behavior of a Transaction
     */
    public static enum TransactionAutoRollback {
        /**
         * XA txns default to LESSTHAN_COMMITTED or LESSTHAN_PREPARED depending
         * on the setting of the property imq.transaction.autolrollback<p>
         * Local txns default to LESSTHAN_COMMITTED
         */
        UNSPECIFIED,
        
        /**
         *  Rollback all transactions that are not in a committed state when
         *  the broker restarts after a failure.
         */
        LESSTHAN_COMMITTED,
        
        /**
         *  Rollback all transactions that are not in a prepared state when the
         *  broker restarts after a failure. When this value is used, the
         *  transaction timeout must be used when starting a transaction.
         */
        LESSTHAN_PREPARED,
        
        /**
         *  Disable the rollback of all transactions when the broker restarts
         *  after a failure.
         */
        DISABLED
    }

    public static enum MessageAckType {
        ACKNOWLEDGE,
        UNDELIVERABLE,
        DEAD
    }    

    public static enum JMSXProperties {
        JMSXGroupID,
        JMSXGroupSeq,
        JMSXAppID,
        JMSXUserID,
        JMSXProducerTXID,
        JMSXConsumerTXID,
        JMSXRcvTimestamp,
        JMSXDeliveryCount
    }

    /**
     *  Return an Identifier for the JMSService.<p>
     *  The identification string returned should enable a user to identify the
     *  specific broker instance that this JMSService is communicating with
     *  from a log message that contains this Identifier string.<br>
     *  for example -- {@literal <hostname>:<primary_port>:<servicename>}
     *
     *  @return The JMSServiceID string that identifies the broker address and
     *          servicename that is being used.
     */
    public String getJMSServiceID();

    /**
     *  Create a connection with the service.<p>
     *  When created, the connection is in the stopped state. The connection
     *  must be explicitly started using the
     *  {@link JMSService#startConnection startConnection()} 
     *  method before any message delivery can be started in any session of
     *  this connection using the {@link Consumer#deliver deliver()} method.
     *
     *  @param  username    The identity with which to establish the connection
     *  @param  password    The password for the identity
     *  @param  ctx         The JMSServiceBootStrapContext to use for broker
     *                      thread resources etc.
     *
     *  @return the JMSServiceReply of the request to create the connection. The
     *          Id of the connection created is obtained from the
     *          JMSServiceReply
     *
     *  @see JMSServiceReply#getJMQVersion
     *  @see JMSServiceReply#getJMQConnectionID
     *  @see JMSServiceReply#getJMQHA
     *  @see JMSServiceReply#getJMQClusterID
     *  @see JMSServiceReply#getJMQMaxMsgBytes
     *  @see JMSServiceReply#getJMQBrokerList
     *
     *  @throws JMSServiceException If the Status returned for the
     *          createConnection method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     */
    public JMSServiceReply createConnection(String username, String password,
            JMSServiceBootStrapContext ctx)
    throws JMSServiceException;

    /**
     *  Destroy a connection.
     *
     *  @param  connectionId The Id of the connection to destroy
     *
     *  @return The JMSServiceReply of the request to destroy the connection
     *
     *  @throws JMSServiceException If the Status returned for the
     *          destroyConnection method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     */
    public JMSServiceReply destroyConnection(long connectionId)
    throws JMSServiceException;

    /**
     *  Generate a set of Unique IDs.<p>
     *  Each Unique ID generated has the following properties:
     *    <UL>
     *       <LI>It will stay unique for a very long time (years)</LI>
     *       <LI>It will be unique across all other IDs gneerated in the Broker
     *           (ConnectionID, ConsumerID, TransactionID</LI>
     *       <LI>It will be unique acrosss all brokers in the cluster</LI>
     *    </UL>
     *
     *  @param  connectionId The Id of the connection
     *  @param  quantity The number of Unique IDs to generate
     *
     *  @return An array of size 'quantity' of long Unique IDs 
     */
    public long[] generateUID(long connectionId, int quantity) 
    throws JMSServiceException;

    /**
     *  Generate a Unique ID.<p>
     *  The Unique ID generated has the following properties:
     *    <UL>
     *       <LI>It will stay unique for a very long time (years)</LI>
     *       <LI>It will be unique across all other IDs gneerated in the Broker
     *           (ConnectionID, ConsumerID, TransactionID</LI>
     *       <LI>It will be unique acrosss all brokers in the cluster</LI>
     *    </UL>
     *
     *  @param  connectionId The Id of the connection
     *
     *  @return The Unique ID
     */
    public long generateUID(long connectionId) 
    throws JMSServiceException;

    /**
     *  Set the clientId for a connection.
     *
     *  @param  connectionId The Id of the connection to set the clientId on
     *  @param  clientId The clientId to be set
     *  @param  shareable If <code>true</code> then the clientId can be shared
     *                   with other connections.
     *                   If <code>false</code>, it cannot be shared.
     *  @param  nameSpace The scope for clientId sharing.<p>
     *                   The server must ensure that all clientId requests
     *                   within a single namespace are unique.
     *
     *  @return The JMSServiceReply of the request to set the clientId on the
     *          connection.
     *
     *  @throws JMSServiceException if the Status returned for the setClientId
     *          method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     */
    public JMSServiceReply setClientId(long connectionId, String clientId,
            boolean shareable, String nameSpace)
    throws JMSServiceException;
    
    /**
     *  Unset the clientId for a connection.
     *
     *  @param  connectionId The Id of the connection whose clientId is to be unset
     *
     *  @return The JMSServiceReply of the request to unset the clientId on the
     *          connection.
     *
     *  @throws JMSServiceException if the Status returned for the unsetClientId
     *          method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply unsetClientId(long connectionId)
    throws JMSServiceException;

    /**
     *  Start messge delivery for a connection.<p>
     *  When the connection is started using this method, all of the sessions
     *  that belong to this connection must be started at the same time as a
     *  result of this method call.
     *  Message delivery is threaded per session with all the consumers for a
     *  single session  being called using the same thread.
     *
     *  @param  connectionId The Id of the connection on which to start delivery
     *
     *  @return The JMSServiceReply of the request to start the connection.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          startConnection method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply startConnection(long connectionId)
    throws JMSServiceException;

    /**
     *  Stop message delivery for a connection.<p>
     *
     *
     *  @param  connectionId The Id of the connection on which to stop delivery
     *
     *  @return The JMSServiceReply of the request to stop the connection.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          stopConnection method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply stopConnection(long connectionId)
    throws JMSServiceException;

    /**
     *  Create a session within a connection.<p>
     *  When a session is created
     *
     *  @param  connectionId The Id of the connection in which the session is to
     *                      be created
     *  @param  ackMode The acknowledgement mode of the session to be created
     *
     *  @return The JMSServiceReply of the request to create a session. The Id
     *          of the session is obtained from the JMSServiceReply
     *
     *  @throws JMSServiceException if the Status returned for the
     *          createSession method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *  @see JMSServiceReply#getJMQSessionID
     */
    public JMSServiceReply createSession(long connectionId,
            SessionAckMode ackMode)
    throws JMSServiceException;
    
    
    /**
     *  Destroy a session.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session to be destroyed
     *
     *  @return The JMSServiceReply of the request to destroy the session
     *
     *  @throws JMSServiceException If the Status returned for the
     *          destroySession method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     */
    public JMSServiceReply destroySession(long connectionId, long sessionId)
    throws JMSServiceException;

    /**
     *  Start messge delivery for a session.<p>
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session on which to start delivery
     *
     *  @return The JMSServiceReply of the request to start the session.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          startSession method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply startSession(long connectionId, long sessionId)
    throws JMSServiceException;

    /**
     *  Stop message delivery for a session.<p>
     *  When this method is called, any async message delivery thread that 
     *  is delivering messages to consumers in this session, must first be
     *  stopped before this method returns.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session on which to stop delivery
     *  @param  dowait if true wait for stopped before return
     *
     *  @return The JMSServiceReply of the request to stop the session
     *
     *  @throws JMSServiceException if the Status returned for the
     *          stopSession method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply stopSession(long connectionId, long sessionId, boolean dowait)
    throws JMSServiceException;

    /**
     * Stop a session with no wait
     */
    public JMSServiceReply stopSession(long connectionId, long sessionId)
    throws JMSServiceException;

    /**
     *  Verify the existence / auto-createability of a physical destination.<p>
     *  If the destination exists the Status returned is OK.<br>
     *  If the destination does not exist but can be auto-created the Status
     *  returned is NOT_FOUND along with the JMQCanCreate property is set to
     *  true.<br>
     *  If the destination does not exist and cannot be auto-created the Status
     *  returned is NOT_FOUND along with JMQCanCreate property set to false.
     *  
     *  @param  connectionId The Id of the connection
     *  @param  dest The Destination object that defines the physical destination
     *
     *  @return The JMSServiceReply of the request to verify the destination
     *
     *  @throws JMSServiceException if the Status returned for the
     *          verifyDestination method is not either
     *          {@link JMSServiceReply.Status#OK} or 
     *          {@link JMSServiceReply.Status#NOT_FOUND}
     *
     *  @see JMSServiceReply#getJMQCanCreate
     *  @see JMSServiceReply#getJMQDestType
     *
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#ERROR
     */
    public JMSServiceReply verifyDestination(long connectionId,
            Destination dest)
    throws JMSServiceException;

    /**
     *  Create a physical destination.
     *
     *  @param  connectionId The Id of the connection
     *  @param  dest The Destination object that defines the physical destination
     *          to be created.
     *          <p>If the physical destination does not exist, it will be
     *          automatically created if the configuration allows. [DEFAULT]
     * 
     *  @return The JMSServiceReply of the request to create the destination
     *
     *  @throws JMSServiceException if the Status returned for the
     *          createDestination method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply#getJMQDestType
     *
     *  @see JMSServiceReply.Status#ERROR
     */
    public JMSServiceReply createDestination(long connectionId,
            Destination dest)
    throws JMSServiceException;

    /**
     *  Destroy a physical destination.
     *
     *  @param  connectionId The Id of the connection
     *  @param  dest The Destination object that identifies the physical
     *          destination to be destroyed
     *
     *  @return The JMSServiceReply of the request to destroy the destination
     *
     *  @throws JMSServiceException if the Status returned for the
     *          destroyDestination method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply destroyDestination(long connectionId,
            Destination dest)
    throws JMSServiceException;

    /**
     *  Add a producer.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session in which to add the producer
     *  @param  dest The Destination on which to add a producer
     *
     *  @return The JMSServiceReply of the request to add a producer
     *
     *  @throws JMSServiceException if the Status returned for the
     *          addProducer method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQProducerID
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply addProducer(long connectionId, long sessionId,
            Destination dest)
    throws JMSServiceException;

    /**
     *  Delete a producer.
     *
     *  @param  connectionId The Id of the connection in which to delete the
     *          producer
     *  @param  sessionId The Id of the session in which to delete the producer
     *  @param  producerId The Id of the producer to delete
     *
     *  @return The JMSServiceReply of the request to delete a producer
     *
     *  @throws JMSServiceException if the Status returned for the
     *          deleteProducer method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply deleteProducer(long connectionId, long sessionId,
            long producerId)
    throws JMSServiceException;

    /**
     *  Add a consumer.<p> The initial state of the consumer must be the
     *  <u>sync</u> state.
     *
     *  @param  connectionId The Id of the connection in which to add the
     *          consumer
     *  @param  sessionId The Id of the session in which to add the consumer.
     *          The acknowledgement mode of the consumer will be that of the
     *          session
     *  @param  dest The Destination from which the consumer will receive
     *          messages
     *  @param  selector The selector which will be used to filter messages
     *  @param subscriptionName if dest is Topic and 
     *         if either durable true or share true, the subscription name
     *  @param durable if dest is Topic, if true, this is a durable subscription
     *  @param share if dest is Topic, if true, this is a shared subscription
     *  @param jmsshare if dest is Topic, 
     *         if true and share true, this is a JMS 2.0 Shared Subscription
     *         if false and share true, this is a MQ Shared Subscription
     *
     *         MQ Shared Subscription: messages will be shared with other
     *         consumers in the same group that have the same
     *         clientId+DurableName for Shared Durable Subscriptions<p>
     *         OR<p>
     *         clientId+TopicName+Selector for Shared Non-Durable Sunscriptions
     *
     *  @param  clientId The clientId to use when this is a durable subscription
     *          with a non-null durableName. This clientId must match the one
     *          that has been set on the connection previously.
     *  @param  noLocal If {@code true}, consumer does not wnat to receive
     *          messages produced on the same connection<br>
     *          If {@code false}, consumer wants to receive messages produced
     *          on the same connection as well.
     *
     *  @return The JMSServiceReply of the request to add a consumer
     *
     *  @throws JMSServiceException if the Status returned for the
     *          addConsumer method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSService#setConsumerAsync
     *
     *  @see JMSServiceReply.Status#getJMQConsumerID
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#NOT_ALLOWED
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply addConsumer(long connectionId, 
        long sessionId, Destination dest, String selector, 
        String subscriptionName, boolean durable, 
        boolean share, boolean jmsshare,
        String clientId, boolean noLocal)
        throws JMSServiceException;

    /**
     *  Delete a consumer.<p>
     *  If this operation is deleting a consumer that is a durable subscription,
     *  then the durableName <b>and</b> the clientId must be non-null. In
     *  addition, the clientId must match the clientId that is currently set
     *  on the connection identified by  connectionId.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session
     *  @param  consumerId The Id of the consumer to delete
     *  @param  lastMessageSeen The last message received by this consumer which
     *  		has been seen by the application. Set to null if deleting a durable subscription.
     *  @param  durableName The name of the durable subscription to remove
     *          if the consumer is unsubscribing.
     *  @param  clientId The clientId of the connection
     *
     *  @return The JMSServiceReply of the request to delete a consumer
     *
     *  @throws JMSServiceException if the Status returned for the
     *          deleteConsumer method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply deleteConsumer(long connectionId, long sessionId,
        long consumerId, SysMessageID lastMessageSeen, 
        boolean lastMessageSeenInTransaction, String durableName, String clientId)
        throws JMSServiceException;

    /**
     *  Configure a consumer for async or sync message consumption.<p>
     *  This method is used to enable and disable async delivery of messages
     *  from the broker to the client.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session
     *  @param  consumerId The Id of the consumer for which the async state is
     *          being set.
     *  @param  consumer The Consumer object that is to be used to deliver the
     *          message.<br>
     *          If <b>non-null</b>, the consumer is being set
     *          into the <u>async</u> state and the server must start delivering
     *          messagesto the {@code Consumer.deliver()} method when the
     *          connectionId and sessionId have been started via the
     *          {@code startConnection} and {@code startSession} methods.<br>
     *          If <b>null</b>, the consumer is being set into the <u>sync</u>
     *          state and the delivery of messages must stop.<br>
     *          The session must first be stopped, before changing a
     *          consumer from the async state to the sync state. However, the
     *          connection and session are not required to be stopped when
     *          a consumer is changed from the sync state, which is the default
     *          for {@code addConsumer()}, to the async state.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          setConsumerAsync method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSService#startConnection
     *  @see JMSService#startSession
     *  @see JMSService#stopSession
     *  @see Consumer#deliver
     *
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply setConsumerAsync(long connectionId, long sessionId,
            long consumerId, Consumer consumer)
    throws JMSServiceException;

    /**
     *  Add a destination browser.<p> Messages that match the browser critera
     *  are fetched by the client using the browseMessages method
     *
     *  @param  connectionId The Id of the connection in which to add the
     *          browser
     *  @param  sessionId The Id of the session in which to add the browser.
     *  @param  dest The Destination for which the browser will fetch messages.
     *  @param  selector The selector which will be used to filter messages
     *
     *  @return The JMSServiceReply of the request to add a browser
     *
     *  @throws JMSServiceException if the Status returned for the
     *          addBrowser method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSService#browseMessages
     *
     *  @see JMSServiceReply.Status#getJMQConsumerID
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#NOT_ALLOWED
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply addBrowser(long connectionId, long sessionId,
            Destination dest, String selector)
    throws JMSServiceException;

    /**
     *  Delete a browser.
     *
     *  @param  connectionId The Id of the connection in which to delete the
     *          browser
     *  @param  sessionId The Id of the session in which to delete the browser
     *  @param  consumerId The Id of the browser to delete
     *
     *  @return The JMSServiceReply of the request to delete a browser
     *
     *  @throws JMSServiceException if the Status returned for the
     *          deleteBrowser method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#FORBIDDEN
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply deleteBrowser(long connectionId, long sessionId,
            long consumerId)
    throws JMSServiceException;

    /**
     *  Start a transaction.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId If non-zero, this is the Id of the session in which the
     *          transaction is being created. This parameter is zero
     *          for XA transactions
     *  @param  xid If non-null, an XA transaction is being started
     *  @param  flags If xId is non-null, then flags is one of:<p>
     *  <UL>
     *  <LI>XAResource.TMNOFLAGS = start a brand new transaction</LI>
     *  <LI>XAResource.TMRESUNE = resume a previously suspended transaction</LI>
     *  </UL>
     *  @param  rollback The type of transaction rollback behavior to use
     *  @param  timeout The transaction timeout to use. The timeout is the
     *          maximum time in seconds that the transaction will be allowed to
     *          be in an un-prepared state.
     *
     *  @return The JMSServiceReply of the request to start a transaction
     *
     *  @throws JMSServiceException if the Status returned for the
     *          startTransaction method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQTransactionID
     *
     *  @see JMSServiceReply.Status#NOT_IMPLEMENTED
     *  @see JMSServiceReply.Status#CONFLICT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply startTransaction(long connectionId, long sessionId,
            Xid xid, int flags, JMSService.TransactionAutoRollback rollback,
            long timeout)
    throws JMSServiceException;

    /**
     *  End a transaction.
     *
     *  @param  connectionId The Id of the connection
     *  @param  transactionId If non-zero, the transaction being ended is
     *          identified by this broker-generated id
     *  @param  xid If transactionId is zero, then xid contains the Xid of the
     *          XA transaction being ended
     *  @param  flags If this is an XA transaction, then flags is one of:
     *  <UL>
     *  <LI>XAResource.TMSUSPEND: If suspending a transaction</LI>
     *  <LI>XAResource.TMFAIL:    If failing a transaction</LI>
     *  <LI>XAResource.TMSUCCESS: If ending a transaction</LI>
     *  </UL>
     *  
     *  @return The JMSServiceReply of the request to end a transaction
     *
     *  @throws JMSServiceException if the Status returned for the
     *          endTransaction method is not {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQTransactionID
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#TIMEOUT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply endTransaction(long connectionId, long transactionId,
            Xid xid, int flags)
    throws JMSServiceException;

    /**
     *  Pepare a transaction.
     *
     *  @param  connectionId The Id of the connection
     *  @param  transactionId If non-zero, the transaction being prepared is
     *          identified by this broker-generated id
     *  @param  xid If transactionId is zero, then xid contains the Xid of the
     *          XA transaction being prepared
     *  
     *  @return The JMSServiceReply of the request to prepare a transaction
     *
     *  @throws JMSServiceException if the Status returned for the
     *          prepareTransaction method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQTransactionID
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#TIMEOUT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply prepareTransaction(long connectionId,
            long transactionId, Xid xid)
    throws JMSServiceException;

    /**
     *  Commit a transaction.
     *
     *  @param  connectionId The Id of the connection
     *  @param  transactionId If non-zero, the transaction being committed is
     *          identified by this broker-generated id
     *  @param  xid If transactionId is zero, then xid contains the Xid of the
     *          XA transaction being committed
     *  @param  flags If this is an XA transaction, then flags is one of:
     *  <UL>
     *  <LI>XAResource.TMONEPHASE:One phase commit. The transaction need not be
     *                            in the PREPARED state</LI>
     *  <LI>XAResource.TMNOFLAGS: Two phase commit. The transaction must be in
     *                            the PREPARED state</LI>
     *  </UL>
     *  
     *  @return The JMSServiceReply of the request to commit a transaction
     *
     *  @throws JMSServiceException if the Status returned for the
     *          commitTransaction method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQTransactionID
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#TIMEOUT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply commitTransaction(long connectionId,
            long transactionId, Xid xid, int flags)
    throws JMSServiceException;

    /**
     *  Rollback a transaction.
     *
     *  @param  connectionId The Id of the connection
     *  @param  transactionId If non-zero, the transaction being rolledback is
     *          identified by this broker-generated id
     *  @param  xid If transactionId is zero, then xid contains the Xid of the
     *          XA transaction being rolledback
     *  @param  redeliver If <code>true</code>, then the broker must redeliver
     *          the messages that were rolledback by this operation.
     *  @param  setRedelivered If true <b><u>and</u></b> <code>redeliver</code>
     *          is <code>true</code> then the broker must set the
     *          REDELIVERED flag on messages it redelivers. 
     *  @param  maxRollbacks maximum consecutive rollbacks allowed
     *          for redelivery of consumed messages for active consumers
     *  @param  dmqOnMaxRollbacks if true place the message to DMQ if 
     *          maxRollbacks reached for a consumed message to active consumer
     *  
     *  @return The JMSServiceReply of the request to rollback a transaction
     *
     *  @throws JMSServiceException if the Status returned for the
     *          rollbackTransaction method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     *  @see JMSServiceReply.Status#getJMQTransactionID
     *
     *  @see JMSServiceReply.Status#BAD_REQUEST
     *  @see JMSServiceReply.Status#NOT_FOUND
     *  @see JMSServiceReply.Status#PRECONDITION_FAILED
     *  @see JMSServiceReply.Status#TIMEOUT
     *  @see JMSServiceReply.Status#ERROR
     *
     */
    public JMSServiceReply rollbackTransaction(long connectionId,
            long transactionId, Xid xid, boolean redeliver,
            boolean setRedelivered, int maxRollbacks, boolean dmqOnMaxRollbacks)
            throws JMSServiceException;

    public JMSServiceReply rollbackTransaction(long connectionId,
            long transactionId, Xid xid, boolean redeliver,
            boolean setRedelivered)
            throws JMSServiceException;

    /**
     *  Recover XA transactions from the broker.
     *
     *  @param  connectionId The Id of the connection
     *  @param  flags Controls the cursor positioning for the scanning of Xids
     *          returned.
     *          flags is set by the transaction manager and passed in 
     *          directly through the XAResource interface.
     *          flags can be one of:<p>
     *
     *<p>
     *  <UL>
     *  <LI>{@link javax.transaction.xa.XAResource#TMNOFLAGS XAResource.TMNOFLAGS}: Used when neither of the following two flags are used</LI>
     *  <LI>{@link javax.transaction.xa.XAResource#TMSTARTRSCAN XAResource.TMSTARTRSCAN}: Starts a recovery scan</LI>
     *  <LI>{@link javax.transaction.xa.XAResource#TMENDRSCAN XAResource.TMENDRSCAN}:  Ends a recovery scan</LI>
     *  </UL>
     *
     *  @return An array of transaction Xid is returned for the transactions
     *          that are in the PREPARED state.
     *          If no transactions are found or transactions are not in the
     *          PREPARED state an empty array is returned.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          recoverTransaction method would be anything other than
     *          {@link JMSServiceReply.Status#OK}
     *          The JMSServiceReply, which contains the Status must be
     *          retreieved from the JMSServiceException
     *
     *  @see javax.transaction.xa.XAResource#recover javax.transaction.xa.XAResource.recover()
     *
     *  @see JMSServiceException#getJMSServiceReply
     */
    public Xid[] recoverXATransactions(long connectionId, int flags)
    throws JMSServiceException;

    /**
     *  Recover a transaction from the broker.
     *
     *  @param connectionId The Id of the connection
     *  @param transactionId The id of the transaction to recover
     *
     *  @return The transactionId is returned if the transaction is in the
     *          PREPARED state. If the transaction is not found or not in the
     *          PREPARED state a zero (0L) is returned.
     *
     *  @throws JMSServiceException if the Status returned for the
     *          recoverTransaction method would be anything other than
     *          {@link JMSServiceReply.Status#OK}
     *
     */
    public long recoverTransaction(long connectionId, long transactionId)
    throws JMSServiceException;

    /**
     *  Send a message to the broker.
     *
     *  @param  connectionId The Id of the connection
     *  @param  message The Message to be sent
     *
     *  @throws JMSServiceException if the Status returned for the
     *          sendMessage method is not {@link JMSServiceReply.Status#OK}
     *
     */
    public JMSServiceReply sendMessage(long connectionId, JMSPacket message)
    throws JMSServiceException;

    /**
     *  Fetch a message from the broker.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session
     *  @param  consumerId The Id of the consumer for which to fetch the message
     *  @param  timeout The maximum time to wait (in milliseconds) for a message
     *          to be available before returning.<br>
     *          Note that the method must return immediately if there is a
     *          message available for this consumerId at the time the call is
     *          made.<br>
     *  <UL>
     *  <LI>    When timeout is positive, the call must wait for a maximum of
     *          the specificied number of milliseconds before returning.
     *          If a message is available before the timeout expires, the method
     *          returns with the message as soon as it is available.
     *  </LI>
     *  <LI>    When timeout is 0, the call must block until a message is
     *          available to return or until the session is stopped.
     *  </LI>
     *  <LI>    When the timeout is negative (less than 0), the call must
     *          return immediately, with a message if one is available or
     *          with a null, if a message is not available immediately.
     *  </LI>
     *  </UL>
     *  @param  acknowledge If this is set to {@code true} then it implies that
     *          the caller is asking for the message to be <b>acknowledged</b> 
     *          before the method returns. If this operation is part of a
     *          transaction, the {@code transactionId} parameter will be
     *          non-zero.
     *  @param  transactionId If non-zero, this is the transactionId in which
     *          to acknowledge the message being returned if the
     *          {@code acknowledge} parameter is set to {@code true}.
     *
     *  @return The JMSPacket which contains the message being returned.
     *
     *  @throws JMSServiceException If broker encounters an error.<br>
     *          {@link JMSServiceException#getJMSServiceReply} should be used
     *          to obtain the broker reply in case of an exception.<br>
     *          The reason for the exception can be obtained from
     *          {@link JMSServiceReply.Status}
     */
    public JMSPacket fetchMessage(long connectionId, long sessionId,
            long consumerId, long timeout, boolean acknowledge,
            long transactionId)
    throws JMSServiceException;

    /**
     *  Acknowledge a message to the broker.
     *
     *  @param  connectionId The Id of the connection
     *  @param  sessionId The Id of the session
     *  @param  consumerId The Id of the consumer for which to acknowledge
     *          the message
     *
     *  @param  sysMessageID The SysMessageID of the message to be acknowledged
     *
     *  @param  transactionId If non-zero, this is the transactionId in which
     *          to acknowledge the message.
     *
     *  @param  ackType The MessageAckType for this message acknowledgement
     *              0 ACKNOWLEDGE_REQUEST
     *              1 UNDELIVERABLE_REQUEST
     *              2 DEAD_REQUEST
     *
     *  @return The JMSServiceReply which contains status and information
     *          about the acknowledge request.
     *
     *  @param  retryCnt retry count of client runtime in delivery the message
     *                   applicable to ackType
     *                   DEAD_REQUEST
     *                   UNDELIVERABLE_REQUEST
     *                   or non-null transactionId
     *                   should be 0 otherwise
     *  @param  deadComment if ackType is DEAD_REQUEST
     *  @param  deadThr if ackType is DEAD_REQUEST
     *
     *  @throws JMSServiceException If the Status returned for the
     *          acknowledgeMessage method is not
     *          {@link JMSServiceReply.Status#OK}.<br>
     *          {@link JMSServiceException#getJMSServiceReply} should be used
     *          to obtain the broker reply in case of an exception.<br>
     *          The reason for the exception can be obtained from
     *          {@link JMSServiceReply.Status}
     *
     */
    public JMSServiceReply acknowledgeMessage(long connectionId, 
        long sessionId, long consumerId, SysMessageID sysMessageID,
        long transactionId, MessageAckType ackType)
        throws JMSServiceException;

    public JMSServiceReply acknowledgeMessage(long connectionId, 
         long sessionId, long consumerId, SysMessageID sysMessageID,
         long transactionId, MessageAckType ackType, int retryCnt)
         throws JMSServiceException;

    public JMSServiceReply acknowledgeMessage(long connectionId, 
         long sessionId, long consumerId, SysMessageID sysMessageID,
         long transactionId, MessageAckType ackType, int retryCnt,
         String deadComment, Throwable deadThr)
         throws JMSServiceException;

    /**
     *  Fetch the messages for a browser.<p> All the messages that match the
     *  browser criteria identified by consumerId will be returned in the
     *  array. 
     *
     *  @param  connectionId The Id of the connection in which the browser was
     *          created
     *  @param  sessionId The Id of the session in which the browser was
     *          created
     *  @param  consumerId The Id of the consumer that is associated with the
     *          browser
     *
     *  @return The JMSPacket array which contains the messages being returned.
     *
     *  @throws JMSServiceException If broker encounters an error.<br>
     *          {@link JMSServiceException#getJMSServiceReply} should be used
     *          to obtain the broker reply in case of an exception.<br>
     *          The reason for the exception can be obtained from
     *          {@link JMSServiceReply.Status}
     */
    public JMSPacket[] browseMessages(long connectionId, long sessionId,
            long consumerId)
    throws JMSServiceException;

    /**
     *  Redeliver messages for a Session.<p>
     *  All the messages that are specified by the parameters will be
     *  redelivered by the broker.
     *
     *  @param  connectionId The Id of the connection in which the messages were
     *          received
     *  @param  sessionId The Id of the session in which the messages were
     *          received
     *  @param  SysMessageID[] The array of SysMessageID objects for the
     *          messages that were received and are to be redelivered
     *  @param  consumerId[] The array of consumerId longs for the messages
     *          that were received and are to be redelivered
     *  @param  transactionId The Id of the transaction in which the messages
     *          were received
     *  @param  setRedelivered Indicates whether to set the Redelivered flag
     *          when redelivering the messages.<br>
     *          If <code>true</code> then the Redelivered flag must be set for
     *          the messages when they are redelivered.<br>
     *          If <code>false</code>, then the Redelivered flag must not be
     *          set for the messages when they are redelivered.
     *
     *  @throws JMSServiceException If broker encounters an error.<br>
     *          {@link JMSServiceException#getJMSServiceReply} should be used
     *          to obtain the broker reply in case of an exception.<br>
     *          The reason for the exception can be obtained from
     *          {@link JMSServiceReply.Status}
     */
    public JMSServiceReply redeliverMessages(long connectionId, long sessionId,
            SysMessageID[] messageIDs, Long[] consumerId, long transactionId,
            boolean setRedelievered)
    throws JMSServiceException;

    /**
     *  Send a message acknowledgement to the broker.
     *  All messages acknowledged by the method must be of the same
     *  MessageAckType
     *
     *  @param  connectionId The Id of the connection
     *  @param  ackType The type of the acknowledgement
     *  @param  acks The acknowledgements
     *
     *  @throws JMSServiceException if the Status returned for the
     *          sendAcknowledgement method is not
     *          {@link JMSServiceReply.Status#OK}
     *
     */
    public JMSServiceReply sendAcknowledgement(long connectionId,
            MessageAckType ackType, JMSPacket acks)
    throws JMSServiceException;

}
