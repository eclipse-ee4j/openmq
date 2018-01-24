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
 * @(#)TextMessage.cpp	1.3 06/26/07
 */ 

#include "TextMessage.hpp"
#include "../util/UtilityMacros.h"
#include "../io/PacketType.hpp"

/*
 *
 */
TextMessage::TextMessage() : Message()
{
  CHECK_OBJECT_VALIDITY();

  if (packet != NULL) {
    packet->setPacketType(PACKET_TYPE_TEXT_MESSAGE);
  }

  messageBodyText = NULL;
}

/*
 *
 */
TextMessage::TextMessage(Packet * const packetArg) : Message(packetArg)
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( packetArg != NULL );
  ASSERT( packetArg->getPacketType() == PACKET_TYPE_TEXT_MESSAGE );

  messageBodyText = NULL;
}

/*
 *
 */
TextMessage::~TextMessage()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( messageBodyText );
}


/*
 *
 */
PRUint16 
TextMessage::getType()
{
  CHECK_OBJECT_VALIDITY();

  return PACKET_TYPE_TEXT_MESSAGE;
}


/*
 *
 */
iMQError
TextMessage::setMessageText(const UTF8String * const messageText)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  const PRUint8 * textBytes = NULL;
  PRInt32 textBytesSize = 0;
  PRUint8 * textBytesCopy = NULL;
  SerialDataOutputStream textStream;

  NULLCHK( messageText );

  CNDCHK( packet == NULL, IMQ_OUT_OF_MEMORY );

  // Get the raw UTF8
  textBytes = messageText->getBytes();
  textBytesSize = messageText->getBytesSize();
  ASSERT( (textBytes != NULL) || (textBytesSize == 0) );
  ASSERT( textBytesSize >= 0 );

  // Copy the bytes
  MEMCHK( textBytesCopy = new PRUint8[textBytesSize] );
  memcpy( textBytesCopy, textBytes, textBytesSize );
  textBytes = NULL;

  // Set the message body.
  packet->setMessageBody(textBytesCopy, textBytesSize);
  textBytesCopy = NULL;

  return IMQ_SUCCESS;
Cleanup:

  DELETE_ARR( textBytesCopy );
  return errorCode;
}


/*
 *
 */
iMQError
TextMessage::getMessageText(UTF8String ** const messageText)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  NULLCHK( messageText );
  *messageText = NULL;

  CNDCHK( packet == NULL, IMQ_OUT_OF_MEMORY );
  MEMCHK( *messageText = new UTF8String((const char*)packet->getMessageBody(), 
                                        packet->getMessageBodySize()) );
  
  return IMQ_SUCCESS;
Cleanup:
  DELETE( *messageText );
  return errorCode;
}

/*
 *
 */
iMQError 
TextMessage::getMessageTextString(const char ** const messageText)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;

  NULLCHK( messageText );
  *messageText = NULL;
  CNDCHK( packet == NULL, IMQ_OUT_OF_MEMORY );
  
  // Delete the current copy of the message body
  DELETE( messageBodyText );

  // Allocate a new string for the message body
  MEMCHK( messageBodyText = new UTF8String((const char*)packet->getMessageBody(), 
                                           packet->getMessageBodySize()) );
  
  // Return the output string
  *messageText = messageBodyText->getCharStr();

  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}

/*
 *
 */
iMQError 
TextMessage::setMessageTextString(const char * const messageText)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  const PRUint8 * textBytes = NULL;
  PRInt32 textBytesSize = 0;
  PRUint8 * textBytesCopy = NULL;
  SerialDataOutputStream textStream;

  NULLCHK( messageText );
  CNDCHK( packet == NULL, IMQ_OUT_OF_MEMORY );

  // Get the raw UTF8
  textBytes = (const PRUint8*) messageText;
  textBytesSize = (PRInt32)STRLEN( messageText );
  ASSERT( (textBytes != NULL) || (textBytesSize == 0) );
  ASSERT( textBytesSize >= 0 );

  // Copy the bytes
  MEMCHK( textBytesCopy = new PRUint8[textBytesSize] );
  memcpy( textBytesCopy, textBytes, textBytesSize );
  textBytes = NULL;

  // Set the message body.
  packet->setMessageBody(textBytesCopy, textBytesSize);
  textBytesCopy = NULL;

  return IMQ_SUCCESS;
Cleanup:

  DELETE_ARR( textBytesCopy );
  return errorCode;
}


/*
 *
 */
HandledObjectType
TextMessage::getObjectType() const 
{
  CHECK_OBJECT_VALIDITY();

  return TEXT_MESSAGE_OBJECT;
}



/*
 *
 */
HandledObjectType
TextMessage::getSuperObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return MESSAGE_OBJECT;
}

