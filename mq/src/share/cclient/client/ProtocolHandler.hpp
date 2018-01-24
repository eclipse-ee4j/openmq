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
 * @(#)ProtocolHandler.hpp	1.9 10/17/07
 */ 

#ifndef PROTOCOLHANDLER_HPP
#define PROTOCOLHANDLER_HPP

#include "../io/Packet.hpp"
#include "TransportProtocolHandler.hpp"
#include "../basictypes/Monitor.hpp"
#include "../auth/AuthenticationProtocolHandler.hpp"
#include "Destination.hpp"
#include "MessageProducer.hpp"
#include "MessageConsumer.hpp"
#include "../basictypes/Object.hpp"
#include "../cshim/xaswitch.hpp"
#include <nspr.h>

// We can't include Connection.hpp due to circular reference so we
// include it in the cpp file.  
class Connection;
class ProducerFlow;

/**
 * This class is the iMQ protocol handler for the iMQ 2.0 JMS client
 * implementation.  This is the application level protocol handler.
 * It handles translating actions such as registerMessageConsumer into
 * iMQ 2.0 packets that are sent to and received from the broker. */
class ProtocolHandler : public Object {
private:
  /** True iff the connection is closed. */
  PRBool isClosed;

  /** True iff the connection has been authenticated with the broker */
  PRBool isAuthenticated;

  /** A parent pointer to the connection that created this ProtocolHandler */
  Connection * connection;

  /** Used to keep access to members synchronous.  */
  Monitor monitor;
  
  /** Makes sure that we write synchronously to the socket.  */
  Monitor writeMonitor;

  /** true if clientID has been sent */
  PRBool clientIDSent; 

  /** true if has read/write activity to the connection */
  PRBool hasActivity;

  /**
   * The default # of microseconds to wait for a write to complete
   */
  PRUint32 writeTimeout;

  /** Initializes all member variables to NULL/FALSE. */
  void init(); 

  /**
   * Updates whether or not the connection is closed.
   *
   * @see isClosed
   * @see Connection::getIsClosed */
  void updateConnectionState();

  /**
   * Sends packetToSend to the broker. 
   *
   * @param packetToSend the packet to send to the broker
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see writePacketWithAck */
  iMQError writePacketNoAck(Packet * const packetToSend);

  /**
   * Sends packetToSend to the broker and returns the response packet
   * in replyPacket.  If two reply packets are expected from the
   * broker as is the case with HELLO, then expectTwoReplies is set to
   * true, expectedAckType1 is set to the type of the first expected
   * packet, and second reply packet is placed in replyPacket.
   *
   * @param packetToSend the packet to send to the broker
   * @param expectTwoReplies true iff packetToSend will generate 
   *  two reply packets.  Currently, only a HELLO packet will 
   *  generate two replies.
   * @param expectedAckType1 if expectTwoReplies is true then
   *  this is the expected type of the first acknowledgement packet
   *  (The second ack is only waited for if the first ack is of the 
   *  expected type).
   * @param replyPacket the reply packet that was returned by the 
   *  broker.  If expectTwoReplies is true, then the second ack packet
   *  is returned.  The caller is responsible for freeing this packet.
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see writePacketNoAck */
  iMQError writePacketWithAck(Packet * const packetToSend,
                              const PRBool expectTwoReplies,
                              PRUint32 expectedAckType1,
                              Packet ** const replyPacket);

  /**
   * Sends packetToSend to the broker, and returns the reply sent by
   * the broker in replyPacket only if the reply matches
   * expectedAckType.
   *
   * @param packetToSend the packet to send to the broker
   * @param expectedAckType the expected ack type
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see writePacketWithAck */
  iMQError writePacketWithReply(Packet * const packetToSend,
                                const PRUint32 expectedAckType,
                                Packet ** const replyPacket);

  /**
   * Sends packetToSend to the broker, and returns the reply sent by
   * the broker in replyPacket only if the reply matches
   * expectedAckType1 or expectedAckType2.
   *
   * @param packetToSend the packet to send to the broker
   * @param expectedAckType1 one of the potential ack types
   * @param expectedAckType1 the other of the potential ack types
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see writePacketWithAck */
  iMQError writePacketWithReply(Packet * const packetToSend,
                                const PRUint32 expectedAckType1,
                                const PRUint32 expectedAckType2,
                                Packet ** const replyPacket);

  /**
   * Sends packetToSend to the broker, and returns the status sent by
   * the broker in replyPacket only if the reply matches
   * expectedAckType.
   *
   * @param packetToSend the packet to send to the broker
   * @param expectedAckType the expected ack type
   * @param status the status returned by the broker
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see writePacketWithAck  */
  iMQError writePacketWithStatus(      Packet *  const packetToSend,
                                 const PRUint32        expectedAckType,
                                       PRInt32 * const status);

  /**
   * Authenticates to the broker.
   *
   * @param authReqPacket is the authenticate request packet that
   *  was sent by the broker
   * @param username is the username to use to authenticate
   * @param password is hte password used to authenticate
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError authenticate(const Packet * const authReqPacket,
                        const UTF8String * const username,
                        const UTF8String * const password);


  /**
   * Returns the AuthenticationProtocolHandler to be used with 
   * authentication of type authType.
   *
   * @param authType the type of the authentication 
   * @param handler is the output parameter to hold the handler for 
   *  this authentication type.
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError getAuthHandlerInstance(const char * const authType,
                                  AuthenticationProtocolHandler ** const handler) const;

  /**
   * Verifies that the client and the broker match with respect to
   * whether admin key is being used.  It returns an error if they
   * do not match.
   *
   * @param authType the type of the authentication 
   * @return IMQ_SUCCESS the client and broker agree on whether 
   *  admin key is being used and an error otherwise.  */
  iMQError checkAdminKeyAuth(const char * const authType) const;

  /**
   * Returns the type of dest encoded according to the DestType class.
   * 
   * @param dest the destination whose type to encode
   * @return the encoded destination type 
   * @see DestType  */
  PRInt32 getDestinationType( const Destination * const dest) const;


public:

  /**
   * The constructor for ProtocolHandler.  It keeps the connection pointer
   * so it can later refer back to the connection.
   *
   * @param connection the Connection that is using this ProtocolHandler  */
  ProtocolHandler(Connection * const connection);

  /**
   * The destructor for ProtocolHandler.  It does not send any packets to the
   * broker.  It only sets it's private member variables to NULL.  */
  virtual ~ProtocolHandler();

  /**
   * @return true iff the connection to the broker has been closed.  */
  PRBool isConnectionClosed() const;

  PRBool getHasActivity() const;

  void clearHasActivity();

  /**
   * Reads a packet from the broker.  The caller is responsible for
   * freeing packet.  It is only called from ReadChannel::run.
   * 
   * @packet is the output parameter where the packet is stored.
   * @return IMQ_SUCCESS if successful and an error otherwise 
   * @see ReadChannel::run  */
  iMQError readPacket(Packet ** const packet);



  //
  // The following methods send iMQ messages to the broker
  //

  /**
   * Connect to the broker at the iMQ protocol level and authenticate by 
   * calling authenticate.  Sends a HELLO packet to broker, and waits for
   * a HELLO_REPLY and AUTHENTICATE_REQUEST packet.
   *
   * @param username is the username to use for authentication
   * @param password is the password to use for authentication
   * @return IMQ_SUCCESS if successful and an error otherwise 
   * @see authenticate.  */
  iMQError hello(const UTF8String * const username, 
                 const UTF8String * const password);

  /**
   * Sets the client ID to be used with this connection.
   *
   * @param clientID the clientID to set for this connection
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError setClientID(const UTF8String * const clientID);

  /**
   * Disconnects from the broker at the protocol level.  Sends a
   * GOODBYE packet to broker, and waits for a GOODBYE_REPLY if
   * expectReply is true.
   *
   * @param expectReply true if the GOODBYE_REPLY packet should be waited for.
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError goodBye(const PRBool expectReply);

  MQError ping();

  /**
   * Starts (or resumes) message delivery from the broker.  This involves
   * sending a START packet to the broker.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise */
  MQError start(PRBool startSession, const Session * session);

  /**
   * Stops the broker from delivering messages to the client.  This involves
   * sending a STOP packet to the broker.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise */
  MQError stop(PRBool stopSession, const Session * session);

  /**
   * This deletes Destination dest at the broker.  It does not delete
   * the dest object. 
   *
   * @param dest the destination to delete
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see createDestination
   * @see Connection::deleteDestination */
  iMQError deleteDestination(const Destination * const dest);

  /**
   * This verifies Destination dest is a valid destination at the
   * broker.
   *
   * @param dest the destination to create
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see deleteDestination  */
  iMQError createDestination(const Destination * const dest);
  
 /**
  * Registers messageProducer with the broker.
  * 
  * @param destination the Destination to register a producer for
  * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError registerMessageProducer(const Session * const session,
                                   const Destination * const destination,
                                   ProducerFlow * producerFlow);

  MQError unregisterMessageProducer(PRInt64 producerID);

  /**
   * Registers messageConsumer with the broker.
   *
   * @param messageConsumer the consumer to register with the broker
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError registerMessageConsumer(MessageConsumer * messageConsumer);

  /**
   * Unregisters messageConsumer with the broker.
   *
   * @param messageConsumer the consumer to unregister with the broker
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError unregisterMessageConsumer(const MessageConsumer * const messageConsumer);

  /**
   * Unsubscribes the durable consumer specified by durableName from the broker
   *
   * @param durableName the name of the durable subscriber to unsubscribe
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError unsubscribeDurableConsumer(const UTF8String * const durableName);


  /**
   * Sends message to the destination specified in the message.
   *
   * @param message the Message to send 
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError writeJMSMessage(const Session * const session, Message * const message);

  /**
   * Sends the acknowledgement block ackBlock to the broker.
   *
   * @param ackBlock the acknowledgement block to send to the broker
   * @param ackBlockSize the size of ackBlock
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  iMQError acknowledge(const Session * const session,
                       const PRUint8 * const ackBlock,
                       const PRInt32 ackBlockSize);

  /**
   * Sends the acknowledgement block, expiration type, to broker
   *
   * @param ackBlock the acknowledgement block to send to the broker
   * @param ackBlockSize the size of ackBlock
   * @return IMQ_SUCCESS if successful and an error otherwise  */
  MQError acknowledgeExpired(const PRUint8 * const ackBlock,
                             const PRInt32 ackBlockSize);


  MQError redeliver(const Session * const session, PRBool setRedelivered,
                    const PRUint8 * const redeliverBlock,
                    const PRInt32   redeliverBlockSize);

  MQError registerSession(Session * session);
  MQError unregisterSession(PRInt64  sessionID);

  MQError startTransaction(PRInt64 sessionID, PRBool setSessionID,
                           XID * xid, long xaflags, PRInt64 * transactionID);
  MQError endTransaction(PRInt64 transactionID, XID *xid, long xaflags);
  MQError prepareTransaction(PRInt64 transactionID, XID *xid);
  MQError commitTransaction(PRInt64 transactionID,  XID *xid,
                            long xaflags, PRInt32 * const replyStatus);
  MQError rollbackTransaction(PRInt64 transactionID, XID * xid, PRBool setJMQRedeliver);
  MQError recoverTransaction(long xaflags, ObjectVector ** const xidv);

  /**
   * Resumes the flow of messages from the broker
   * 
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError resumeFlow(PRBool consumerFlow, PRInt64 consumerID);

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  MQError prepareTransaction(PRInt64 transactionID, XID *xid, PRBool onePhase);
  //
  // These are not supported and are not implemented
  //
  ProtocolHandler(const ProtocolHandler& handler);
  ProtocolHandler& operator=(const ProtocolHandler& handler);
};



#endif // PROTOCOLHANDLER_HPP

