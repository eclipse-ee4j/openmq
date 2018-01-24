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
 * @(#)Message.hpp	1.9 06/26/07
 */ 

#ifndef MESSAGE_HPP
#define MESSAGE_HPP

#include "../io/Packet.hpp"
#include "Destination.hpp"
#include "../basictypes/HandledObject.hpp"
#include <nspr.h>


class Session;

/** 
 * This is the lowest overhead delivery mode because it does not
 * require that the message be logged to stable storage. The level of
 * JMS provider failure that causes a NON_PERSISTENT_DELIVERY message
 * to be lost is not defined.
 *
 * A JMS provider must deliver a NON_PERSISTENT_DELIVERY message with
 * an at-most-once guarantee. This means it may lose the message but
 * it must not deliver it twice.  
 */
static const PRInt32 NON_PERSISTENT_DELIVERY = 1;

/**
 * This mode instructs the JMS provider to log the message to stable
 * storage as part of the client's send operation. Only a hard media
 * failure should cause a PERSISTENT_DELIVERY message to be lost.
 */
static const PRInt32 PERSISTENT_DELIVERY = 2;

/**
 * This class very closely follows the Java iMQ MessageImpl class.  It
 * encapsulates a JMS message.  It is an abstract base class for
 * actual message types such as TextMessage.  
 */
class Message : public HandledObject {
protected:
  /**
   * The Message class acts as an adaptor for most of the fields of
   * packet.  
   */
  Packet * packet;

  /**
   * Destination (if any) for this message
   */
  const Destination * dest;


  /**
   * Reply to destination (if any) for this message
   */
  Destination * replyToDest; 

  /**
   * cache for received message */
  SysMessageID sysMessageID;
  PRUint64     consumerID;


  /**
   * The session (if any) that is associated with this message. */
  const Session * session;

  PRBool ackProcessed;

private:
  /**
   * Initializes member variables.
   */
  void init();
  
  /**
   * Deallocates all memory associated with this packet.
   */
  void reset();

public:
  /**
   * Constructor.  It allocates a new Packet to base this message on. */
  Message();

  /**
   * Constructor.  It bases the message on the packet parameter.
   * 
   * @param packet the Packet to base the Message on.
   */
  Message(Packet * const packet);

  /**
   * Destructor.
   */
  virtual ~Message();

  /** @return IMQ_SUCCESS if the constructor was successful and an
      error otherwise.  The default constructor could fail to allocate
      a packet if we've run out of memory. */
  virtual iMQError getInitializationError() const;

  /**
   * @return the type of the message as defined in PacketType.
   */
  virtual PRUint16 getType();

  /**
   * @return the iMQ packet corresponding to this JMS message 
   */
  Packet * getPacket();


  /**
   * Constructs a new Message of a specific type based on the type of
   * the packet. 
   * 
   * @param packet the packet to use to construct the message
   * @return a Message of the type specified in packet (e.g. TEXT_MESSAGE) */
  static Message* createMessage(Packet * const packet);

  //
  // These are accessors for the packet fields.  These can be made
  // virtual as needed.  
  //
  
  iMQError setJMSMessageID(UTF8String * const messageID);
  iMQError getJMSMessageID(const UTF8String ** const messageID);

  iMQError setJMSTimestamp(const PRInt64 timestamp);
  iMQError getJMSTimestamp(PRInt64 * const timestamp);

  iMQError setJMSCorrelationID(UTF8String * const correlationID);
  iMQError getJMSCorrelationID(const UTF8String ** const correlationID);

  iMQError setJMSReplyTo(const Destination * const replyTo);
  iMQError getJMSReplyTo(const Destination ** const replyTo);

  iMQError setJMSDestination(const Destination * const destination);
  iMQError getJMSDestination(const Destination ** const destination);

  iMQError setJMSDeliveryMode(const PRInt32 deliveryMode);
  iMQError getJMSDeliveryMode(PRInt32 * const deliveryMode);

  iMQError setJMSRedelivered(const PRBool redelivered);
  iMQError getJMSRedelivered(PRBool * const redelivered);
  
  iMQError setJMSType(UTF8String * const messageType);
  iMQError getJMSType(const UTF8String ** const messageType);
  
  iMQError setJMSExpiration(const PRInt64 expiration);
  iMQError getJMSExpiration(PRInt64 * const expiration);

  iMQError setJMSDeliveryTime(const PRInt64 deliveryTime);
  iMQError getJMSDeliveryTime(PRInt64 * const deliveryTime);

  iMQError setJMSPriority(const PRUint8 priority);
  iMQError getJMSPriority(PRUint8 * const priority);

  iMQError setProperties(Properties * const properties);
  iMQError getProperties(const Properties ** const properties);

  iMQError setHeaders(Properties * const headers);
  iMQError getHeaders(Properties ** const headers) const;

  PRUint64 getConsumerID() const;
  const SysMessageID * getSystemMessageID() const; 
  
  void setSession(const Session * session);
  const Session * getSession() const;
  PRBool isAckProcessed() const;
  void setAckProcessed();
  PRBool isExpired() const;

  /** @return the type of this object for HandledObject */
  virtual HandledObjectType getObjectType() const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Message(const Message& message);
  Message& operator=(const Message& message);
};


#endif // MESSAGE_HPP
