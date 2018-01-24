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
 * @(#)MessageProducer.hpp	1.5 06/26/07
 */ 

#ifndef MESSAGEPRODUCER_HPP
#define MESSAGEPRODUCER_HPP

#include "Destination.hpp"
#include "Message.hpp"
#include "../basictypes/HandledObject.hpp"

class Session; // can't have mutual inclusion

/** These are default values for message header values that are set by
 *  a producer. */
static const PRInt32 MESSAGE_PRODUCER_DEFAULT_DELIVERY_MODE = PERSISTENT_DELIVERY;
static const PRInt32 MESSAGE_PRODUCER_DEFAULT_PRIORITY = 4;
static const PRInt32 MESSAGE_PRODUCER_MIN_PRIORITY = 0;
static const PRInt32 MESSAGE_PRODUCER_MAX_PRIORITY = 9;
static const PRInt64 MESSAGE_PRODUCER_DEFAULT_TIME_TO_LIVE = LL_INIT( 0, 0 );  // 0
static const PRInt64 MESSAGE_PRODUCER_DEFAULT_DELIVERY_DELAY = LL_INIT( 0, 0 );  // 0

/**
 * This class implements a topic publisher and a queue sender.  
 */
class MessageProducer : public HandledObject {
private:
  MQError initializationError;
protected:
  PRBool isClosed;
  /**
   * Session that created this MessageProducer.
   */
  Session * session;

  /**
   * Destination where messages are sent.
   */
  Destination * destination;

  /**
   * Destinations that this producer can send to. 
   */
  BasicTypeHashtable validatedDestinations;
  Monitor monitor;

  /**
   * Whether message delivery is persistent or non-persistent.  Soec
   * defaults it to persistent.  
   */
  PRInt32 deliveryMode;

  /**
   * The JMS API defines ten levels of priority value, with 0 as the
   * lowest priority and 9 as the highest. Clients should consider
   * priorities 0-4 as gradations of normal priority and priorities
   * 5-9 as gradations of expedited priority. Priority is set to 4 by
   * default.
   */
  PRInt32 priority;

  /**
   * The default length of time in milliseconds from its dispatch time
   * that a produced message should be retained by the message system.
   * Time to live is set to zero by default.
   */
  PRInt64 timeToLive;

  PRInt64 deliveryDelay;

  /**
   * Sends message to the with the default destination, delivery mode,
   * priority, and time-to-live.
   *
   * @param message the Message to send 
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  iMQError writeJMSMessage(Message * const message);

  /**
   * Sends message to the with the default destination, delivery mode,
   * priority, and time-to-live.
   *
   * @param message the Message to send 
   * @param msgDestination the Destination to send the message to
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  iMQError writeJMSMessage(Message * const message,
                           const Destination * const msgDestination);

  /**
   * Sends message to the specified destination, with the specified
   * delivery mode, priority, and time-to-live.
   *
   * @param message the Message to send 
   * @param msgDeliveryMode persistent or non-persistent delivery
   * @param msgPriority the delivery priority of the message
   * @param msgTimeToLive the time-to-live of the message
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError writeJMSMessage(Message * const message,
                           const Destination * const msgDestination,
                           const PRInt32 msgDeliveryMode,
                           const PRInt8  msgPriority,
                           const PRInt64 msgTimeToLive);

  /**
   * Sends message to the default destination, with the specified
   * delivery mode, priority, and time-to-live.
   *
   * @param message the Message to send 
   * @param msgDeliveryMode persistent or non-persistent delivery
   * @param msgPriority the delivery priority of the message
   * @param msgTimeToLive the time-to-live of the message
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError writeJMSMessage(Message * const message,
                           const PRInt32 msgDeliveryMode,
                           const PRInt8  msgPriority,
                           const PRInt64 msgTimeToLive);

private:
  /**
   * Initializes all member variables to default values.
   */
  void init();

public:
  /**
   * Constructor.
   *
   * @param session the Session that created this MessageProducer
   */
  MessageProducer(Session * const session);

  /**
   * Constructor.
   *
   * @param session the Session that created this MessageProducer
   * @param destination the Destination where this MessageProducer sends messages
   */
  MessageProducer(Session * const session, Destination * const destination);

  virtual MQError getInitializationError() const;

  /**
   * Destructor.
   */
  virtual ~MessageProducer();

  /**
   * @return the Destination where this producer sends messages
   */
  const Destination * getDestination() const;


  /**
   * Sends message to the destination.
   *
   * @param message the Message to send
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError send(Message * const message);  

  /**
   * Sends message to the destination.
   *
   * @param message the Message to send
   * @param msgDestination the Destination to send the message to
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError send(Message * const message, const Destination * const msgDestination);

  /**
   * Sends message to the default destination, with the specified
   * delivery mode, priority, and time-to-live.
   *
   * @param message the Message to send 
   * @param msgDeliveryMode persistent or non-persistent delivery
   * @param msgPriority the delivery priority of the message
   * @param msgTimeToLive the time-to-live of the message
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError send(Message * const message,
                const PRInt32 msgDeliveryMode,
                const PRInt8  msgPriority,
                const PRInt64 msgTimeToLive);

  /**
   * Sends message to the specified Destination, with the specified
   * delivery mode, priority, and time-to-live.
   *
   * @param message the Message to send 
   * @param msgDestination the Destination to send the message to
   * @param msgDeliveryMode persistent or non-persistent delivery
   * @param msgPriority the delivery priority of the message
   * @param msgTimeToLive the time-to-live of the message
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError send(Message * const message,
                const Destination * const msgDestination,
                const PRInt32 msgDeliveryMode,
                const PRInt8  msgPriority,
                const PRInt64 msgTimeToLive);


  /**
   * Validates with the broker that this producer can send to
   * msgDestination.
   *
   * @param msgDestination the Destination to validate
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError validateDestination(const Destination * const msgDestination, PRInt64 * producerID);

  
  /** Accessors */
  PRInt32 getDeliveryMode() const;
  PRInt32 getPriority() const;
  PRInt64 getTimeToLive() const;
  PRInt64 getDeliveryDelay() const;
  void setDeliveryMode(const PRInt32 deliveryMode);
  void setPriority(const PRInt32 priority);
  void setTimeToLive(const PRInt64 timeToLive);
  void setDeliveryDelay(const PRInt64 deliveryDelay);

  /** @return the session that created this Producer */
  Session * getSession() const; 

  /**
   * Closes the MessageProducer.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  iMQError close();

  /** Needed to implement HandledObject */
  virtual HandledObjectType getObjectType() const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  MessageProducer(const MessageProducer& messageProducer);
  MessageProducer& operator=(const MessageProducer& messageProducer);
};


#endif // MESSAGEPRODUCER_HPP
