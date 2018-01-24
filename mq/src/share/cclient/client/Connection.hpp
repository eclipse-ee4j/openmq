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
 * @(#)Connection.hpp	1.32 10/17/07
 */ 

#ifndef CONNECTION_HPP
#define CONNECTION_HPP

#include "../error/ErrorCodes.h"
#include "ProtocolHandler.hpp"
#include "TransportProtocolHandler.hpp"
#include "FlowControl.hpp"
#include "../containers/Properties.hpp"
#include "../containers/Vector.hpp"
#include "ReadQTable.hpp"
#include "MessageConsumerTable.hpp"
#include "ReadChannel.hpp"
#include "PingTimer.hpp"
#include "../basictypes/Runnable.hpp"
#include "../basictypes/Monitor.hpp"
#include "../basictypes/HandledObject.hpp"
#include "../auth/AuthenticationProtocolHandler.hpp"
#include "AckMode.h"
#include "ReceiveMode.h"
#include "Session.hpp"
#include "XASession.hpp"
#include "MessageProducer.hpp"
#include "../cshim/mqcallback-types-priv.h"
#include "../cshim/mqversion.h"
#include "../cshim/xaswitch.hpp"

class ProducerFlow;

static const char *  PRODUCT_NAME          = MQ_NAME;
static const char *  PRODUCT_VERSION       = MQ_VERSION;
static const PRInt32 PRODUCT_MAJOR_VERSION = (PRInt32)MQ_VMAJOR;
static const PRInt32 PRODUCT_MINOR_VERSION = (PRInt32)MQ_VMINOR;
static const PRInt32 PRODUCT_MICRO_VERSION = (PRInt32)MQ_VMICRO;
static const PRInt32 PRODUCT_SERVICE_PACK =  (PRInt32)MQ_SVCPACK;
static const PRInt32 PRODUCT_UPDATE_RELEASE =  (PRInt32)MQ_URELEASE;

/**
 * This is the core class associated with a single connection to an
 * MQ Broker.  This class also is a mediator for the other client
 * classes limiting the interaction between these other classes For
 * example, other classes go through this class to access the
 * protocolHandler.  */
class Connection : public HandledObject {
private:

  PRInt64 connectionID;
  PRBool  isXA;
  static PRBool nsprVersionChecked;

  /** The properties of the connection that are passed in to
   *  openConnection.  Connection deletes these properties when the
   *  connection is closed. */
  Properties *                     properties;          

  /** Handles the MQ protocol communication with the broker. */
  ProtocolHandler *                protocolHandler;     

  /** Handles the transport layer of actually sending/receiving bytes
   *  to/from the broker. */
  TransportProtocolHandler *       transport;

  /** Handles flow control with the broker. */
  FlowControl *                    flowControl;

  /** Handles authentication to the broker for this connection. */
  AuthenticationProtocolHandler *  authHandler;     

  /** The user name that is used to authenticate to the broker.  It is
   *  passed in to openConnection.  Connection is responsible for
   *  deleting this. */
  UTF8String *                     username;

  /** The password that is used to authenticate to the broker.  It is
   *  passed in to openConnection.  Connection is responsible for
   *  deleting this. */
  UTF8String *                     password;       

  BasicTypeHashtable               producerFlowTable;
  Monitor                          producerFlowTableMonitor;

  /** Maps a consumerID (that is located in a packet) to the
   *  corresponding ReceiveQueue. This is for message packets */
  ReadQTable                       receiveQTable;

  /** Maps a ackID for Acks - control message replies
   */
  ReadQTable                       ackQTable;

  /** Pending ADD_CONSUMER table
   */
  MessageConsumerTable              pendingConsumerTable;

  /** The readChannel thread is responsible for reading packets from
   *  the wire and dispatching them to the appropriate ReceiveQueue */
  ReadChannel *                    readChannel;         
  PingTimer *                      pingTimer;         

  /** Holds all of the sessions that were created by this connection. */
  ObjectVector                     sessionVector;
  Monitor                          sessionsMonitor;

  /** True iff the connection has been closed. */
  PRBool                           isClosed;

  /** True iff the connection has been aborted. */
  PRBool                           isAborted;

  /** True iff the connection has been stopped.
   *  @see stop */
  PRBool                           isStopped;

  /** Ensures synchronous access to certain member variables. */
  Monitor                          monitor;

  /** Ensures synchronous access to aborting the connection */
  Monitor                          exitMonitor;
  
  /** For temporary destination name generation to make sure that
   *  temporary destination names are unique.  */
  PRInt32                          tempDestSequence;

  /** The client ID associated with this broker connection  */
  UTF8String *                     clientID;


  // The following fields are optionally set by the client by setting
  // properties passed to openConnection:
  //   transportConnectionType              via  "JMQConnectionType"
  //   ackTimeoutMS                         via  "JMQAckTimeout"
  //   ackOnPersistentProduce               via  "JMQAckOnProduce"
  //   ackOnNonPersistentProduce            via  "JMQAckOnProduce"
  //   ackOnAcknowledge                     via  "JMQAckOnAcknowledge"
  //   flowControlIsLimited                 via  "JMQFlowControlLimit"
  //   flowControlWaterMark                 via  "JMQFlowControlIsLimited"
  //   flowControlNumMessagesBeforePausing  via  "JMQFlowControlCount"
  //   pingIntervalSec                      via  "JMQPingInterval"

  /** The type of the transport connection to the broker (i.e. "TCP" or "TLS").
   *  Defaults to "TCP". */
  const char *                     transportConnectionType;

  /** The maximum time in microseconds that the client will wait for any broker
   *  acknowledgement before throwing an exception. */
  PRInt32                          ackTimeoutMicroSec;

  /* The maximum time in microseconds to wait for a write to complete */
  PRInt32                          writeTimeoutMicroSec;

  /** True iff the broker should send an acknowledgement for each
   *  persistent message that is produced by the client, and the
   *  client should block waiting for this ack to arrive.  This value
   *  defaults to TRUE and can be changed via the JMQAckOnProduce
   *  property. */
  PRBool                           ackOnPersistentProduce;

  /** True iff the broker should send an acknowledgement for each
   *  non-persistent message that is produced by the client, and the
   *  client should block waiting for this ack to arrive.  This value
   *  defaults to FALSE and can be changed via the JMQAckOnProduce
   *  property. */
  PRBool                           ackOnNonPersistentProduce;

  /** True iff the broker should send an acknowledgement for each ack
   *  message that the client sends, and the client should block
   *  waiting for this ack to arrive.  This defauults to TRUE. */
  PRBool                           ackOnAcknowledge;

  /** True iff the connection has requested the broker use flow
      control.  This defaults to FALSE.*/
  PRBool                           flowControlIsLimited;

  /** The number of received but not delivered messages that can be
   *  tolerated.  This defaults to 1000. */
  PRInt32                          flowControlWaterMark;

  /** The number of messages that the broker should send before pausing
   *  message delivery and waiting for a RESUME_FLOW packet */
  PRInt32                          flowControlNumMessagesBeforePausing;

  /** The number of seconds the connection has to be idle before ping
   */
  PRInt32                          pingIntervalSec;

  PRInt32                          consumerPrefetchMaxMsgCount;

  PRFloat64                        consumerPrefetchThresholdPercent;

  /** The optional callback function that notifies the user that an exception
   *  occurred on the connection (e.g. the connection to the broker was lost). */
  MQConnectionExceptionListenerFunc         exceptionListenerCallback;

  /** The void* user data that was passed to setExceptionListenerFunc.
   *  It is passed to exceptionListenerFunc*/
  void *                           exceptionListenerCallbackData;

  /** The optional callback function that enables the user to create
   *  the threads used by the library. */
  MQCreateThreadFunc              createThreadCallback;

  /** The void* user data that was passed to setCreateThreadFunc.
   *  It is passed to createThreadFunc*/
  void *                           createThreadCallbackData;


  /** Initializes member variables (mostly to NULL). It must only be
      called from the constructor. */
  void init(); // only call from constructor


  /** Sets fields of the Connection object based on the properties
   *  that are passed into openConnection. */
  MQError setFieldsFromProperties();
  
  /**
   * Calls ProtocolHandler::hello to connect to and and authenticate
   * with the broker at the MQ protocol layer.
   *
   * @see ProtocolHandler::hello
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError hello();

  /**
   * Calls TransportProtocolHandler::connect to connect to the broker
   * at the socket layer.
   *
   * @see TransportProtocolHandler::connect
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError connectToBroker();

  /**
   * Called from exitConnection to close all Sessions that were
   * created on this connection.
   * 
   * @see exitConnection
   * @see Session::close
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError closeSessions();
  MQError startSessions();
  MQError stopSessions();


  /**
   * Adds session to the list of Sessions created by this Connection.
   *
   * @param session is the Session to add to the list of sessions
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError addSession(const Session * const session);


public:
  void setIsXA();
  PRBool getIsXA();

  MQError getProperties(const Properties ** const props);
   
  /**
   * Removes session from the list of Sessions created by this Connection.
   *
   * @param session is the Session to remove from the list of sessions
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError removeSession(const Session * const session);


  /** 
   * Sets the clientID of this connection to the one set in
   * connectionProperties, or the dotted IP address if there is
   * property.  */
  MQError setClientID(PRBool ifnotNULL);

private:

  /**
   * Returns the Session at index from the vector of sessions.  The
   * index must be between 0 and numSessions()-1.
   *
   * @param index the index of the session to retrieve
   * @param session the output parameter for the retrieved session
   * @return MQ_SUCCESS if successful and an error otherwise 
   * @see numSessions */
  MQError getSession(const PRInt32 index, Session ** const session);

  /** @return the number of Sessions created by this connection. */
  PRInt32 numSessions();

  /** Deletes all member variables of Connection that should be deleted. */
  void deleteMemberVariables();

  
  /**
   * Converts the millisecond timeout parameter where 0 implies no timeout
   * to the output microsecond parameter where 0xffffffff imples no timeout.
   */
  static PRInt32 msTimeoutToMicroSeconds(const PRInt32 timeoutMS);


  /**
   * Sets this->transportHandler based on MQ_CONNECTION_TYPE_PROPERTY.  If
   * this optional property is not set, then TCP is used.
   *
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError createTransportHandler();

  /** Set the callback that allows the user to create the threads allocated by 
   *  this connection. */
  MQError setCreateThreadCallback(const MQCreateThreadFunc createThreadFunc,
                                   void * createThreadFuncData);

  MQError addProducerFlow(PRInt64 producerID, const ProducerFlow * const producerFlow);
  MQError removeProducerFlow(PRInt64 producerID);
  MQError closeAllProducerFlow();
  void cleanupConnection();

public:

  /**
   * Constructor for Connection that merely calls init
   * @see init */
  Connection();

  /**
   * Destructor for Connection that closes the connection and deletes all member
   * variables.
   * @see exitConnection */
  virtual ~Connection();

  /**
   * Connects to the broker specified in connectionProperties using
   * username and password for authentication.  The string property
   * TRANSPORT_BROKER_NAME_PROPERTY must be set to the name of broker,
   * and the integer property TRANSPORT_BROKER_PORT_PROPERTY must be
   * set to the broker's portmapper port (typically 7676).  Whether
   * this method succeeds or fails, Connection is responsible for
   * freeing connectionProperties, username, and properties.
   *
   * @param connectionProperties the connection properties to be used 
   *   for this connection
   * @param username the user name used for authentication
   * @param password the password used for authentication
   * @param createThreadFunc is the callback to call to create threads
   *  used by this connection.  If NULL, Connection creates its own threads.
   * @param createThreadFuncData this value is passed to createThreadFunc
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError openConnection(Properties * connectionProperties, 
                          UTF8String * username,
                          UTF8String * password,
                          UTF8String * clientIDArg,
                          MQConnectionExceptionListenerFunc exceptionListener,
                          void *              exceptionCallBackData,
                          const MQCreateThreadFunc createThreadFunc,
                          void * createThreadFuncData);


  /**
   * This method closes down the connection to the broker including
   * all Sessions, Producers, and Consumers associated with this
   * connection.  When this method is invoked it will not return until
   * message processing has been orderly shut down.
   *
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError close();

  MQError getMetaData(Properties ** const metaProperties);

  MQError ping();

  /**
   * This is used to start a Connection's delivery of incoming
   * messages. Message delivery can be paused by calling stop.
   *
   * @return MQ_SUCCESS if successful and an error otherwise 
   * @see stop */
  MQError start();


  /**
   * This is used to temporarily stop a Connection's delivery of
   * incoming messages. It can be restarted calling start.  When
   * stopped, delivery to all the Connection's message consumers is
   * inhibited.  Stopping a Connection has no affect on its ability to
   * send messages.  Stopping a stopped connection is ignored.  This
   * method does not return until delivery of messages has paused.
   *
   * @return MQ_SUCCESS if successful and an error otherwise 
   * @see start */
  MQError stop();

  PRBool getIsStopped() const;

  /**
   * Creates a Session with the properties given by isTransacted and ackMode.
   *
   * @param isTransacted true iff the session is transacted.  This value must 
   *  be PR_FALSE.
   * @param ackMode AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE.  
   *  This value must be CLIENT_ACKNOWLEDGE.
   * @param receiveMode
   * @param session the output parameter for the created session
   * @return MQ_SUCCESS if successful and an error otherwise  */
  MQError createSession(const PRBool        isTransacted, 
                         const AckMode      ackMode,
                         const ReceiveMode  receiveMode,
                         const PRBool       isXASession,
                         MQMessageListenerBAFunc beforeMessageListener,
                         MQMessageListenerBAFunc afterMessageListener,
                         void *  callbackData,
                         Session **         const session);

  /** Set the callback for when an exception occurs on the connection */
  MQError setExceptionListenerCallback(
            const MQConnectionExceptionListenerFunc exceptionListenerFunc,
                                        void * exceptionListenerFuncData);

  /** @return the type of this object for HandledObject */
  virtual HandledObjectType getObjectType() const;
  
  /**
   * This static method tests the basic functionality of this class.
   *
   * @return MQ_SUCCESS if successful and an error otherwise  */
  static MQError test(const PRInt32 simultaneousConnections);

//
// Connection acts as a mediator between all of the other client
// classes.  The following methods are called only from these
// classes.  These methods are currently public, but they could be
// made protected, and each of the other classes
// (e.g. ProtocolHandler, ReadChannel, etc.) could be made friends
// of this class.
//

  /**
   * Returns the TransportProtocolHandler associated with this connection.  It 
   * is used primarily by ProtocolHandler. 
   *
   * @return the TransportProtocolHandler for this connection
   * @see TransportProtocolHandler
   * @see ProtocolHandler  */
  TransportProtocolHandler * getTransport() const;


  /**
   * Returns the ProtocolHandler associated with this connection.  It 
   * is used primarily by ReadChannel.
   *
   * @return the ProtocolHandler for this connection
   * @see ProtocolHandler
   * @see ReadChannel */
  ProtocolHandler * getProtocolHandler() const;

  /**
   * Returns the AuthenticationProtocolHandler associated with this
   * connection.  It is used primarily by ProtocolHandler.
   *
   * @return the AuthenticationProtocolHandler for this connection
   * @see AuthenticationProtocolHandler
   * @see ProtocolHandler */
  AuthenticationProtocolHandler * getAuthenticationHandler() const;

  /**
   * Sets the AuthenticationProtocolHandler associated with this connection.  It 
   * is used primarily by ProtocolHandler.
   *
   * @param authHandler the AuthenticationProtocolHandler for this connection
   * @see AuthenticationProtocolHandler
   * @see ProtocolHandler
   */
  void setAuthenticationHandler(AuthenticationProtocolHandler * const handler);

  /**
   * This deletes Destination dest at the broker.  It does not delete
   * the dest object. 
   *
   * @param dest the destination to delete
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see ProtocolHandler::deleteDestination */
  MQError deleteDestination(Destination * const dest);

  MQError createDestination(Destination * const dest);

  MQError getProducerFlow(PRInt64 producerID, ProducerFlow ** const producerFlow);
  void    releaseProducerFlow(ProducerFlow ** producerFlow);

  
  /**
   * Associates message consumer receiveQ with consumerID in the ReadQTable.
   *
   * @param consumerID is the consumer ID that will appear in incoming
   *  MQ packets that are destined for receiveQ
   * @param receiveQ is the receiveQ associated with consumerID
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see readQTable
   * @see ReceiveQueue */
  MQError addToReceiveQTable(PRInt64  consumerID, ReceiveQueue * const receiveQ);

  // for ackQ, ackID to ack receiveQ
  MQError addToAckQTable(PRInt64  * ackID, ReceiveQueue * const receiveQ);

  // for pendingQ on creating message consumer, ackID to message consumer receiveQ
  MQError addToPendingConsumerTable(PRInt64  ackID, MessageConsumer * const consumer);


  /**
   * Remove the ReceiveQueue associated with consumerID from the ReadQTable.
   *
   * @param consumerID is the consumer ID to remove from the ReadQTable
   * @param receiveQ if not NULL, return the receiveQ removed  
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see readQTable */
  MQError removeFromReceiveQTable(const PRInt64 consumerID);

  // for ackQ
  MQError removeFromAckQTable(const PRInt64 consumerID);

  // for pendingQ, return receiveQ
  MQError removeFromPendingConsumerTable(const PRInt64 consumerID,  MessageConsumer** const consumer);


  /** @return true iff the connection is closed  */
  PRBool getIsClosed() const;

  /** @return true iff the connection is actually closed by close() */ 
  PRBool getIsConnectionClosed() const;

  /**
   * @return the IPAddress of the local connection
   * @see IPAddress
   * @see getLocalPort */
  const IPAddress * getLocalIP() const;

  /**
   * @return the port of the local connection
   * @see getLocalIP */
  PRUint16 getLocalPort() const;

  /**
   * Returns the next consumerID to use for a message consumer
   * @param idToReturn the next consumer id
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see consumerID */
  //MQError getNextConsumerID(PRInt32 * const idToReturn);

  /**
   * @return the transport connection type (e.g. "TCP" or "TLS") used for 
   * this connection.
   */
  const char * getTransportConnectionType() const;

  /**
   * Returns the timeout value for how long to wait for an 
   * acknowledgement packet.  The value is represented in microseconds.
   * @return timeout duration in microseconds for waiting for an ack
   */
  PRInt32 getAckTimeoutMicroSec() const;

  /**
   * Returns the timeout value for how long to wait for a write 
   * to complete.  The value is represented in microseconds.
   * @return timeout duration in microseconds 
   */
  PRInt32 getWriteTimeoutMicroSec() const;

  PRInt32 getPingIntervalSec() const;

  /** @return true iff the broker should acknowledge messages sent by
      persistent producers */
  PRBool getAckOnPersistentProduce() const;

  /** @return true iff the broker should acknowledge messages sent by
      non-persistent producers */
  PRBool getAckOnNonPersistentProduce() const;

  /** @return true iff the broker should acknowledge acknowledgement
      messages sent by the client */
  PRBool getAckOnAcknowledge() const;

  /** @return true iff the connection has requested the broker use
      flow control. */
  PRBool getFlowControlIsLimited() const;

  /** @return the flow control watermark, which is the number of received
   *  but not delivered messages that can be tolerated. */
  PRInt32 getFlowControlWaterMark() const;

  /** @return the number of messages the broker should send before pausing
   *  message delivery. */
  PRInt32 getNumMessagesBeforePausing() const;

  PRInt32 getConsumerPrefetchMaxMsgCount() const;
  PRFloat64 getConsumerPrefetchThresholdPercent() const;

  /** @return true iff admin key authentication is used for this connection. */
  PRBool isAdminKeyUsed() const;

  /** @return the clientID used for this connection */
  const UTF8String * getClientID();
  
  /** @return the next unique (to this connection) temporary destination ID */
  PRInt32 getTemporaryDestinationSequence();
  char * getTemporaryDestinationPrefix(PRBool isQueue);


  // @return a unique ID for this connection. 
  PRInt64 id() const;
  void setid(PRInt64 id);

  static char * getUserAgent();
  static MQError versionCheck(PRBool mq);


  /**
   * Shuts down the connection to the broker including closing
   * Sessions, Producers and Consumers created for this connection.
   * It is typically called by Connection::close, but it can also be
   * called from other classes if an unrecoverable error occurs.
   *
   * @param errorCode the error that caused exitConnection to be called
   * @param calledFromReadChannel true iff called from ReadChannel::run
   * @see close
   * @see ReadChannel::run */
  void exitConnection(const MQError errorCode, 
                      const PRBool calledFromReadChannel,
                      const PRBool abortConnection);

  /**
   * If the user has installed an exception listener, then this method
   * calls the exception listener passing error.
   *
   * @param error the error to pass to the exception listener
   * @see setExceptionListenerFunc 
   */
  void notifyExceptionListener(const MQError error) const;

  /**
   * Creates a new thread and starts it at threadToRun::run.
   *
   * @param threadToRun the thread to run
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see Runnable */
  MQError startThread(Runnable * const threadToRun);

  /**
   * Calls ProtocolHandler::registerMessageProducer to register
   * messageProducer with the broker.
   * 
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see ProtocolHandler::registerMessageProducer */
  MQError registerMessageProducer(const Session * const session, 
                                  const Destination * const destination, PRInt64 * producerID);

  MQError unregisterMessageProducer(PRInt64 producerID);

  /**
   * Adds messageConsumer to the ReadQTable, and calls
   * ProtocolHandler::registerMessageConsumer to register
   * messageConsumer with the broker.
   * 
   * @param messageConsumer the consumer to register with the broker
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see ProtocolHandler::registerMessageProducer */
  MQError registerMessageConsumer(MessageConsumer * const messageConsumer);

  /**
   * Removes messageConsumer from the ReadQTable, and calls
   * ProtocolHandler::unregisterMessageConsumer to unregister
   * messageConsumer with the broker.
   * 
   * @param messageConsumer the consumer to unregister from the broker
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see ProtocolHandler::unregisterMessageProducer */
  MQError unregisterMessageConsumer(MessageConsumer * const messageConsumer);

  /**
   * Calls ProtocolHandler::unsubscribeDurableConsumer to unsubscribe
   * from the broker the durable consumer specified by durableName.
   *
   * @param durableName the name of the durable subscriber to unsubscribe
   * @return MQ_SUCCESS if successful and an error otherwise  
   * @see ProtocolHandler::unsubscribeDurableConsumer*/
  MQError unsubscribeDurableConsumer(const UTF8String * const durableName);

  /**
   * Sends message to the destination specified in the message.
   *
   * @param message the Message to send 
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError writeJMSMessage(const Session * const session, Message * const message, PRInt64 producerID);

  /**
   * Enqueues packet on the queue of the consumer with consumerID.
   *
   * @param consumerID the ID of the destination consumer 
   * @param packet the packet to enqueue
   * @param qtype which QTable
   * @return MQ_SUCCESS if successful and an error otherwise */
  MQError enqueueReceiveQPacket(const PRInt64 consumerID, Packet * const packet);

  //for ackQ
  MQError enqueueAckQPacket(const PRInt64 ackID, Packet * const packet);

  /**
   * Sends the acknowledgement block ackBlock to the broker by calling
   * ProtocolHandler::acknowledge.
   *
   * @param ackBlock the acknowledgement block to send to the broker
   * @param ackBlockSize the size of ackBlock @param
   * @return MQ_SUCCESS if successful and an error otherwise @see
   * ProtocolHandler::acknowledge */
  MQError acknowledge(const Session * const session,
                      const PRUint8 * const ackBlock,
                      const PRInt32   ackBlockSize);

  /**
   * Sends the acknowledgement block, expired type, to the broker  
   *
   * @param ackBlock the acknowledgement block to send to the broker
   * @param ackBlockSize the size of ackBlock @param
   * @return MQ_SUCCESS if successful and an error otherwise @see
   * ProtocolHandler::acknowledgeExpired */
  MQError acknowledgeExpired(const PRUint8 * const ackBlock,
                             const PRInt32   ackBlockSize);

  MQError redeliver(const Session * const session, PRBool setRedelivered,
                    const PRUint8 * const redeliverBlock,
                    const PRInt32   redeliverBlockSize);

  MQError registerSession(Session * session);
  MQError unregisterSession(PRInt64  sessionID);
  MQError startSession(const Session * session);
  MQError stopSession(const Session * session);

  MQError startTransaction(PRInt64 sessionID, PRInt64 * transactionID);
  MQError startTransaction(XID *xid, long xaflags, PRInt64 * transactionID);
  MQError endTransaction(PRInt64 transactionID, XID *xid, long xaflags);
  MQError prepareTransaction(PRInt64 transactionID, XID *xid);

  MQError commitTransaction(PRInt64 transactionID, PRInt32 * const replyStatus);
  MQError commitTransaction(PRInt64 transactionID, XID *xid, long xaflags, PRInt32 * const replyStatus);
  MQError rollbackTransaction(PRInt64 transactionID);
  MQError rollbackTransaction(PRInt64 transactionID, XID *xid);
  MQError recoverTransaction(long xaflags, ObjectVector ** const xidv);

  /**
   * Calls ProtocolHandler::resumeFlow to resume the flow of messages
   * from the broker
   * 
   * @return MQ_SUCCESS if successful and an error otherwise
   * @see ProtocolHandler::resumeFlow
   */
  MQError resumeFlow();

  /** Notifies the flow control object that a message was received.
   *  @see FlowControl::messageReceived */
  void messageReceived();

  /** Notifies the flow control object that a message was delivered to
   *  a consumer.  
   *  @see FlowControl::messageReceived */
  void messageDelivered();

  /** Notifies the flow control object that the broker has paused the flow
   *  of messages, and the flow of messages should be resumed if the number
   *  of undelivered messages is below the watermark.
   *
   *  @see FlowControl::messageReceived */
  void requestResume();

  //when per-consumer flowcontrol supported in C-API, change this
  MQError requestResumeConsumer(PRInt64 consumerID);

  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Connection(const Connection& connection);
  Connection& operator=(const Connection& connection);
};



#endif // CONNECTION_HPP




