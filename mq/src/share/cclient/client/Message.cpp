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
 * @(#)Message.cpp	1.10 06/26/07
 */ 

#include "Message.hpp"
#include "TextMessage.hpp"
#include "BytesMessage.hpp"
#include "../io/PacketType.hpp"
#include "../util/UtilityMacros.h"

/*
 *
 */
Message::Message()
{
  CHECK_OBJECT_VALIDITY();

  this->init();
  this->packet = new Packet();
  if (this->packet != NULL) {
      this->packet->setPacketType(PACKET_TYPE_MESSAGE);
  }
}

/*
 *
 */
Message::Message(Packet * const packetArg)
{
  CHECK_OBJECT_VALIDITY();

  this->init();
  this->packet = packetArg;
  ASSERT( this->packet != NULL );
  this->sysMessageID = *(packetArg->getSystemMessageID());
  this->consumerID = packetArg->getConsumerID();
}

/*
 *
 */
Message::~Message()
{
  CHECK_OBJECT_VALIDITY();

  this->reset();
}

/*
 *
 */
iMQError
Message::getInitializationError() const
{
  RETURN_IF_ERROR( HandledObject::getInitializationError() );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  return IMQ_SUCCESS;
}

/*
 *
 */
void
Message::init()
{
  CHECK_OBJECT_VALIDITY();

  packet      = NULL;
  dest        = NULL;
  replyToDest = NULL;
  session    = NULL;
  sysMessageID.reset();
  consumerID = 0;
  ackProcessed = PR_FALSE;
}


/*
 *
 */
void
Message::reset()
{
  CHECK_OBJECT_VALIDITY();

  HANDLED_DELETE( replyToDest );
  DELETE( packet );
  dest = NULL;
  session = NULL;
  sysMessageID.reset();
  consumerID = 0;
}


/*
 *
 */
PRUint16
Message::getType()
{
  CHECK_OBJECT_VALIDITY();

  return PACKET_TYPE_MESSAGE;
}


/*
 *
 */
Packet *
Message::getPacket()
{
  CHECK_OBJECT_VALIDITY();

  return packet;
}


/*
 *
 */
Message* 
Message::createMessage(Packet * const packet)
{
  Message * message = NULL;
  if (packet == NULL) {
    return NULL;
  }
  switch (packet->getPacketType()) {
  case PACKET_TYPE_TEXT_MESSAGE:
    message = new TextMessage(packet);
    break;
  case PACKET_TYPE_BYTES_MESSAGE:
    message = new BytesMessage(packet);
    break;
  case PACKET_TYPE_MESSAGE:
    message = new Message(packet);
    break;
  default:
    message = NULL;
  }

  return message;
}



// -----------------------------------------------------------------
// Accessor methods
// -----------------------------------------------------------------

/*
 *
 */
void
Message::setSession(const Session * sessionArg)
{
  CHECK_OBJECT_VALIDITY();
  // We should only set it once
  ASSERT( this->session == NULL );

  this->session = sessionArg;
}

/*
 *
 */
const Session * 
Message::getSession() const
{
  CHECK_OBJECT_VALIDITY();
  return this->session;
}

PRBool
Message::isAckProcessed() const
{
  CHECK_OBJECT_VALIDITY();
  return this->ackProcessed;
}

void
Message::setAckProcessed()
{
  CHECK_OBJECT_VALIDITY();
  ASSERT( this->ackProcessed == PR_FALSE );
  this->ackProcessed = PR_TRUE;
}

/*
 *
 */
iMQError 
Message::setJMSMessageID(UTF8String * const messageID)
{
  CHECK_OBJECT_VALIDITY();
  
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  RETURN_ERROR_IF_NULL( messageID );
  packet->setMessageID(messageID);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSMessageID(const UTF8String ** const messageID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( messageID );
  *messageID = NULL;

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  *messageID = packet->getMessageID();
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSTimestamp(const PRInt64 timestamp)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  packet->setTimestamp(timestamp);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSTimestamp(PRInt64 * const timestamp)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( timestamp );

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  *timestamp = packet->getTimestamp();
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSCorrelationID(UTF8String * const correlationID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( correlationID );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  packet->setCorrelationID(correlationID);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSCorrelationID(const UTF8String ** const correlationID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( correlationID );
  *correlationID = NULL;
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  *correlationID = packet->getCorrelationID();
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSReplyTo(const Destination * const replyTo)
{
  CHECK_OBJECT_VALIDITY();
  iMQError errorCode = IMQ_SUCCESS;
  
  const UTF8String * replyToName      = NULL; 
  const UTF8String * replyToClassName = NULL;
  UTF8String * replyToNameClone       = NULL;
  UTF8String * replyToClassNameClone  = NULL;

  NULLCHK( replyTo );
  CNDCHK( packet == NULL, IMQ_OUT_OF_MEMORY );

  // Invalidate the current replyToDest
  HANDLED_DELETE( this->replyToDest );

  // Get the destination name and class name and clone them
  replyToName = replyTo->getName();
  replyToClassName = replyTo->getClassName();
  CNDCHK( replyToName == NULL, IMQ_DESTINATION_NO_NAME );
  CNDCHK( replyToClassName == NULL, IMQ_DESTINATION_NO_CLASS );
  MEMCHK( replyToNameClone = (UTF8String*)replyToName->clone() );
  MEMCHK( replyToClassNameClone = (UTF8String*)replyToClassName->clone() );

  // Set the replyTo destination name and class name
  packet->setReplyTo(replyToNameClone);
  packet->setReplyToClass(replyToClassNameClone);
  replyToNameClone = NULL;      // these are owned by the packet now
  replyToClassNameClone = NULL;

  return IMQ_SUCCESS;
Cleanup:
  DELETE( replyToNameClone );
  DELETE( replyToClassNameClone );

  return errorCode;
}

/*
 *
 */
iMQError 
Message::getJMSReplyTo(const Destination ** const replyTo)
{
  CHECK_OBJECT_VALIDITY();
  const UTF8String * replyToName      = NULL; 
  const UTF8String * replyToClassName = NULL;

  RETURN_ERROR_IF_NULL( replyTo );
  *replyTo = NULL;

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  *replyTo = NULL;

  // If we have a cached reply to object, then return it
  if (this->replyToDest != NULL) {
    *replyTo = this->replyToDest;
    return IMQ_SUCCESS;
  }

  // Get the reply name and class name
  replyToName  = packet->getReplyTo();
  replyToClassName = packet->getReplyToClass();
  RETURN_ERROR_IF( replyToName == NULL, IMQ_NO_REPLY_TO_DESTINATION );
  RETURN_ERROR_IF( replyToClassName == NULL, IMQ_NO_REPLY_TO_DESTINATION );

  // Create a destination and make sure that it was constructed properly
  this->replyToDest = new Destination(replyToName, replyToClassName, NULL);
  if ((this->replyToDest == NULL) ||
      (!replyToName->equals(this->replyToDest->getName())) ||
      (this->replyToDest->getInitializationError() != IMQ_SUCCESS))
  {
    HANDLED_DELETE(this->replyToDest);
    return IMQ_OUT_OF_MEMORY;
  }

  *replyTo = this->replyToDest;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSDestination(const Destination * const destination)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( destination );
  ASSERT( destination->getName() );
  RETURN_ERROR_IF_NULL( destination->getName() );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  this->dest = destination;

  // Get the class name
  const UTF8String * destinationClassName = destination->getClassName();
  RETURN_ERROR_IF( destinationClassName == NULL, 
                   IMQ_DESTINATION_NO_CLASS );

  packet->setDestination((UTF8String*)destination->getName()->clone());
  packet->setDestinationClass((UTF8String*)destinationClassName->clone());
  packet->setIsQueue(destination->getIsQueue());

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSDestination(const Destination ** const destination)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( destination );
  *destination = NULL;

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  if (this->dest != NULL) {
    *destination = this->dest;
  } else {
    ASSERT( PR_FALSE );  // need to construct a destination
  }

  /*
  UTF8String * destStr = packet->getDestination();
  UTF8String * destClassStr = packet->getDestinationClass();
  
  if ((destStr == NULL) || (destClassStr == NULL)) {
    return IMQ_MESSAGE_NO_DESTINATION;
  }
    
  RETURN_IF_ERROR( this->createDestinationObject(destStr, destClassStr, destination) );
  */

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSDeliveryMode(const PRInt32 deliveryMode)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  ASSERT( (deliveryMode == PERSISTENT_DELIVERY) || (deliveryMode == NON_PERSISTENT_DELIVERY ) );
  packet->setFlag(PACKET_FLAG_PERSISTENT, (deliveryMode == PERSISTENT_DELIVERY));
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSDeliveryMode(PRInt32 * const deliveryMode)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( deliveryMode );

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  if (packet->getFlag(PACKET_FLAG_PERSISTENT)) {
    *deliveryMode = PERSISTENT_DELIVERY;
  } else {
    *deliveryMode = NON_PERSISTENT_DELIVERY;
  }

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSRedelivered(const PRBool redelivered)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  packet->setFlag(PACKET_FLAG_REDELIVERED, redelivered);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSRedelivered(PRBool * const redelivered)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( redelivered );

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  *redelivered = packet->getFlag(PACKET_FLAG_REDELIVERED);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSType(UTF8String * const messageType)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( messageType );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  packet->setMessageType(messageType);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSType(const UTF8String ** const messageType)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( messageType );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  *messageType = packet->getMessageType();
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::setJMSExpiration(const PRInt64 expiration)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  packet->setExpiration(expiration);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSExpiration(PRInt64 * const expiration)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( expiration );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  *expiration = packet->getExpiration();
  return IMQ_SUCCESS;
}

iMQError
Message::setJMSDeliveryTime(const PRInt64 deliveryTime)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  packet->setDeliveryTime(deliveryTime);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Message::getJMSDeliveryTime(PRInt64 * const deliveryTime)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( deliveryTime );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  *deliveryTime = packet->getDeliveryTime();
  return IMQ_SUCCESS;
}


/*
 *
 */
PRBool 
Message::isExpired() const
{
  CHECK_OBJECT_VALIDITY();
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  PRInt64 exp = packet->getExpiration();
  if (LL_IS_ZERO( exp ) != 0) {
    return PR_FALSE;
  }
  PRInt64 tmp = 0, now = 0;
  LL_DIV( now, PR_Now(), (PRUint64)PR_USEC_PER_MSEC );
  LL_SUB( tmp, exp, now );
  if ((LL_IS_ZERO( tmp ) != 0 || LL_GE_ZERO( tmp ) == 0)) {
    return PR_TRUE;
  }
  return PR_FALSE;
}

/*
 *
 */
iMQError 
Message::setJMSPriority(const PRUint8 priority)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  packet->setPriority(priority);
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
Message::getJMSPriority(PRUint8 * const priority)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( priority );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  *priority = packet->getPriority();
  return IMQ_SUCCESS;
}



/*
 *
 */
iMQError 
Message::setProperties(Properties * const properties)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( properties );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  packet->setProperties(properties);

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
Message::getProperties(const Properties ** const properties)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( properties );
  *properties = NULL;
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  *properties = packet->getProperties();

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
Message::setHeaders(Properties * const headers)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( headers );
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  RETURN_IF_ERROR( packet->setHeaders(headers) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
Message::getHeaders(Properties ** const headers) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( headers );
  *headers = NULL;
  RETURN_ERROR_IF( packet == NULL, IMQ_OUT_OF_MEMORY );
  RETURN_IF_ERROR( packet->getHeaders(headers) );

  return IMQ_SUCCESS;
}

PRUint64
Message::getConsumerID() const
{
  CHECK_OBJECT_VALIDITY();
  
  return this->consumerID;
}


const SysMessageID * 
Message::getSystemMessageID() const
{
  CHECK_OBJECT_VALIDITY();
  if (packet == NULL) {
    return NULL;
  }

  return &(this->sysMessageID);
}

/*
 *
 */
HandledObjectType
Message::getObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return MESSAGE_OBJECT;
}

