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
 * @(#)BytesMessage.cpp	1.3 06/26/07
 */ 

#include "BytesMessage.hpp"
#include "../util/UtilityMacros.h"
#include "../io/PacketType.hpp"

/*
 *
 */
BytesMessage::BytesMessage() : Message()
{
  CHECK_OBJECT_VALIDITY();

  if (packet != NULL) {
    packet->setPacketType(PACKET_TYPE_BYTES_MESSAGE);
  }
}

/*
 *
 */
BytesMessage::BytesMessage(Packet * const packetArg) : Message(packetArg)
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( packetArg != NULL );
  ASSERT( packetArg->getPacketType() == PACKET_TYPE_BYTES_MESSAGE );
}

/*
 *
 */
BytesMessage::~BytesMessage()
{
  CHECK_OBJECT_VALIDITY();
}


/*
 *
 */
PRUint16 
BytesMessage::getType()
{
  CHECK_OBJECT_VALIDITY();

  return PACKET_TYPE_BYTES_MESSAGE;
}

/*
 *
 */
iMQError
BytesMessage::setMessageBytes(const PRUint8 * const messageBytes,
                              const PRInt32 messageBytesSize)
{
  iMQError errorCode = IMQ_SUCCESS;
  PRUint8 * messageBytesCopy = NULL;
  
  NULLCHK( messageBytes );
  CNDCHK( this->packet == NULL, IMQ_OUT_OF_MEMORY );
  
  // Copy the bytes
  MEMCHK( messageBytesCopy = new PRUint8[messageBytesSize] );
  memcpy( messageBytesCopy, messageBytes, messageBytesSize );
  
  // Set the message body.
  this->packet->setMessageBody(messageBytesCopy, messageBytesSize);
  messageBytesCopy = NULL;
  
  return IMQ_SUCCESS;
Cleanup:
  DELETE( messageBytesCopy );
  
  return errorCode;
}

/*
 *
 */
iMQError
BytesMessage::getMessageBytes(const PRUint8 ** const messageBytes,
                              PRInt32 * const messageBytesSize) const
{
  iMQError errorCode = IMQ_SUCCESS;

  NULLCHK( messageBytes );
  NULLCHK( messageBytesSize );
  CNDCHK( this->packet == NULL, IMQ_OUT_OF_MEMORY );

  *messageBytes = this->packet->getMessageBody();
  *messageBytesSize = this->packet->getMessageBodySize();
  
  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}

/*
 *
 */
HandledObjectType
BytesMessage::getObjectType() const 
{
  CHECK_OBJECT_VALIDITY();

  return BYTES_MESSAGE_OBJECT;
}

/*
 *
 */
HandledObjectType
BytesMessage::getSuperObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return MESSAGE_OBJECT;
}
