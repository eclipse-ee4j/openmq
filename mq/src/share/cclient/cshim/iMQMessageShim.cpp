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
 * @(#)iMQMessageShim.cpp	1.17 06/26/07
 */ 

#include "mqproperties.h"
#include "mqmessage.h"
#include "shimUtils.hpp"
#include "../client/Message.hpp"
#include "../client/Session.hpp"
#include "../io/PacketType.hpp"

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQCreateMessage(MQMessageHandle * messageHandle)
{
  static const char FUNCNAME[] = "MQCreateMessage";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageHandle == NULL, MQ_NULL_PTR_ARG );
  messageHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  MEMCHK( message = new Message() );
  ERRCHK( message->getInitializationError() );

  message->setIsExported(PR_TRUE);
  messageHandle->handle = message->getHandle();

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  DELETE( message );
  MQ_ERROR_TRACE(FUNCNAME, errorCode);
  RETURN_STATUS( errorCode );
}


/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQFreeMessage(MQMessageHandle messageHandle)
{
  CLEAR_ERROR_TRACE(PR_FALSE);
  return freeHandledObject(messageHandle.handle, MESSAGE_OBJECT);
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQGetMessageProperties(const MQMessageHandle messageHandle,
                         MQPropertiesHandle *  propertiesHandle)
{
  static const char FUNCNAME[] = "MQGetMessageProperties";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  const Properties * messageProperties = NULL;
  Properties * clonedProperties = NULL;
    
  CLEAR_ERROR_TRACE(PR_FALSE);

  // Make sure propertiesHandle is not NULL and then initialize it
  CNDCHK( propertiesHandle == NULL, MQ_NULL_PTR_ARG );
  propertiesHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  CNDCHK( message == NULL, MQ_STATUS_INVALID_HANDLE );

  // Get the message properties
  errorCode = message->getProperties(&messageProperties);
  CNDCHK( errorCode != MQ_SUCCESS, errorCode );
  
  // If message properties is NULL then there were no properties.
  CNDCHK( messageProperties == NULL, MQ_NO_MESSAGE_PROPERTIES );

  // Clone the message properties
  clonedProperties = messageProperties->clone();
  CNDCHK( clonedProperties == NULL, MQ_OUT_OF_MEMORY );
  ERRCHK( clonedProperties->getInitializationError() );

  // Set the output handle
  ERRCHK( clonedProperties->setIsExported(PR_TRUE) );
  propertiesHandle->handle = clonedProperties->getHandle();

  releaseHandledObject(message);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  DELETE( clonedProperties );
  releaseHandledObject(message);
  if (errorCode != MQ_NO_MESSAGE_PROPERTIES) {
    MQ_ERROR_TRACE( FUNCNAME, errorCode );
  }
  RETURN_STATUS( errorCode );
}
  

/*
 * If successful, then propertiesHandle will be invalidated
 */
EXPORTED_SYMBOL MQStatus 
MQSetMessageProperties(const MQMessageHandle messageHandle,
                       MQPropertiesHandle propertiesHandle)
{
  static const char FUNCNAME[] = "MQSetMessageProperties";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  CNDCHK( message == NULL, MQ_STATUS_INVALID_HANDLE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  // Set the message properties
  ERRCHK( message->setProperties(props) );

  // The message owns properties now
  props->setIsExported(PR_FALSE);
  
  releaseHandledObject(props);
  releaseHandledObject(message);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  releaseHandledObject(message);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}  


/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQGetMessageHeaders(const MQMessageHandle messageHandle,
                      MQPropertiesHandle *  headersHandle)
{
  static const char FUNCNAME[] = "MQGetMessageHeaders";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  Properties * messageHeaders = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Make sure headersHandle is not NULL and then initialize it
  NULLCHK( headersHandle );
  headersHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;
  
  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  CNDCHK( message == NULL, MQ_STATUS_INVALID_HANDLE );

  // Get the message headers
  ERRCHK( message->getHeaders(&messageHeaders) );
  ASSERT( messageHeaders != NULL );

  // Set the output handle
  messageHeaders->setIsExported(PR_TRUE);
  headersHandle->handle = messageHeaders->getHandle();
  
  releaseHandledObject(message);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(message);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 * If successful, then headersHandle will be invalidated
 */
EXPORTED_SYMBOL MQStatus 
MQSetMessageHeaders(const MQMessageHandle messageHandle,
                    MQPropertiesHandle headersHandle)
{
  static const char FUNCNAME[] = "MQSetMessageHeaders";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  Properties * messageHeaders = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  CNDCHK( message == NULL, MQ_STATUS_INVALID_HANDLE );

  // Convert the handle to a properties pointer
  messageHeaders = (Properties*)getHandledObject(headersHandle.handle, 
                                                 PROPERTIES_OBJECT);
  CNDCHK( messageHeaders == NULL, MQ_STATUS_INVALID_HANDLE );

  // Set the message headers
  ERRCHK( message->setHeaders(messageHeaders) );

  // We free headersHandle to be consistent with MQSetMessageProperties
  releaseHandledObject(messageHeaders);
  MQFreeProperties(headersHandle);
      
  releaseHandledObject(message);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(messageHeaders);
  releaseHandledObject(message);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}



/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQSetMessageReplyTo(const MQMessageHandle messageHandle,
                      const MQDestinationHandle destinationHandle)
{
  static const char FUNCNAME[] = "MQSetMessageReplyTo";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  Destination * destination = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  MQ_CNDCHK_TRACE( message == NULL, MQ_STATUS_INVALID_HANDLE, FUNCNAME );

  // Convert destinationHandle to a Destination pointer
  destination = (Destination*)getHandledObject(destinationHandle.handle, 
                                               DESTINATION_OBJECT);
  MQ_CNDCHK_TRACE( destination == NULL, MQ_STATUS_INVALID_HANDLE, FUNCNAME);  

  // Set the replyto destination
  MQ_ERRCHK_TRACE( message->setJMSReplyTo(destination), FUNCNAME );

  releaseHandledObject(destination);
  releaseHandledObject(message);  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(destination);
  releaseHandledObject(message);  
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQGetMessageReplyTo(const MQMessageHandle messageHandle,
                      MQDestinationHandle * destinationHandle)
{
  static const char FUNCNAME[] = "MQGetMessageReplyTo";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  const Destination * replyTo = NULL;
  Destination * replyToClone = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  MQ_CNDCHK_TRACE( destinationHandle == NULL, MQ_NULL_PTR_ARG, FUNCNAME );
  destinationHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  MQ_CNDCHK_TRACE( message == NULL, MQ_STATUS_INVALID_HANDLE, FUNCNAME );

  // Get the replyto destination
  ERRCHK( message->getJMSReplyTo(&replyTo) );

  // Clone the destination
  MEMCHK( replyToClone = replyTo->clone() );

  // Make the replyTo destination a valid handle
  replyToClone->setIsExported(PR_TRUE);
  destinationHandle->handle = replyToClone->getHandle();

  releaseHandledObject(message);  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(message);  
  if (errorCode != MQ_NO_REPLY_TO_DESTINATION) { 
    MQ_ERROR_TRACE( FUNCNAME, errorCode );
  }
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQAcknowledgeMessages(const MQSessionHandle sessionHandle,
                      const MQMessageHandle messageHandle)
{
  static const char FUNCNAME[] = "MQAcknowledgeMessages";
  MQError errorCode = MQ_SUCCESS;
  Session * session = NULL;
  Message * message = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  session = (Session*)getHandledObject(sessionHandle.handle, 
                                        SESSION_OBJECT);
  MQ_CNDCHK_TRACE( session == NULL, MQ_STATUS_INVALID_HANDLE, FUNCNAME );

  // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  MQ_CNDCHK_TRACE( message == NULL, MQ_STATUS_INVALID_HANDLE, FUNCNAME );

  // acknowledge the message
  MQ_ERRCHK_TRACE( session->acknowledgeMessages(PR_TRUE, message), FUNCNAME );

  releaseHandledObject(session);  
  releaseHandledObject(message);  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(session);  
  releaseHandledObject(message);  
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQGetMessageType(const MQMessageHandle messageHandle,
                   MQMessageType *       messageType)
{
  static const char FUNCNAME[] = "MQGetMessageType";
  MQError errorCode = MQ_SUCCESS;
  Message * message = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageType == NULL, MQ_NULL_PTR_ARG );
  *messageType = MQ_UNSUPPORTED_MESSAGE;
    
   // Convert messageHandle to a Message pointer
  message = (Message*)getHandledObject(messageHandle.handle,
                                       MESSAGE_OBJECT);
  CNDCHK( message == NULL, MQ_STATUS_INVALID_HANDLE );

  switch(message->getType()) {
  case PACKET_TYPE_TEXT_MESSAGE:
    *messageType = MQ_TEXT_MESSAGE;
    break;
  case PACKET_TYPE_BYTES_MESSAGE:
    *messageType = MQ_BYTES_MESSAGE;
    break;
  case PACKET_TYPE_MESSAGE:
    *messageType = MQ_MESSAGE;
    break;
  default:
    *messageType = MQ_UNSUPPORTED_MESSAGE;
  };
  
  releaseHandledObject(message);  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(message);  
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


