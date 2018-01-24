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
 * @(#)iMQConsumerShim.cpp	1.14 06/26/07
 */ 

#include "mqconsumer.h"
#include "shimUtils.hpp"
#include "../client/MessageConsumer.hpp"
#include "../client/Session.hpp"
#include "../client/Message.hpp"
#include "../io/PacketType.hpp"


/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQCloseMessageConsumer(MQConsumerHandle consumerHandle)
{
  static const char FUNCNAME[] = "MQCloseMessageConsumer";
  MQError errorCode = MQ_SUCCESS;
  Session * session = NULL;
  MessageConsumer * consumer = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  // Convert consumerHandle to a MessageConsumer pointer
  consumer = (MessageConsumer*)getHandledObject(consumerHandle.handle, 
                                                MESSAGE_CONSUMER_OBJECT);
  CNDCHK( consumer == NULL, MQ_STATUS_INVALID_HANDLE);

  // Close the consumer via the session
  session = consumer->getSession();
  if (session != NULL) {
    // This won't actually delete the destination because this function
    // still owns a pointer to it.
    ERRCHK( session->closeConsumer(consumer) );
  }

  // Release our pointer to the object, this actually deletes the consumer
  releaseHandledObject(consumer);

  // This only has an effect if consumer->getSession() was NULL, which
  // should never happen.
  // freeHandledObject(consumerHandle.handle, MESSAGE_CONSUMER_OBJECT);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(consumer);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageWait(const MQConsumerHandle consumerHandle, 
                       MQMessageHandle *      messageHandle)
{
  return MQReceiveMessageWithTimeout(consumerHandle, 
                                       PR_INTERVAL_NO_TIMEOUT,
                                       messageHandle);
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageNoWait(const MQConsumerHandle consumerHandle, 
                         MQMessageHandle *      messageHandle)
{
  static const char FUNCNAME[] = "MQReceiveMessageNoWait";
  MQError errorCode = MQ_SUCCESS;
  MessageConsumer * consumer = NULL;
  Message * message = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageHandle == NULL, MQ_NULL_PTR_ARG );
  messageHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Convert consumerHandle to a MessageConsumer pointer
  consumer = (MessageConsumer*)getHandledObject(consumerHandle.handle,
                                                MESSAGE_CONSUMER_OBJECT);
  CNDCHK( consumer == NULL, MQ_STATUS_INVALID_HANDLE);

  CNDCHK( consumer->getReceiveMode() != SESSION_SYNC_RECEIVE, MQ_NOT_SYNC_RECEIVE_MODE );
  ERRCHK( consumer->receive(&message, PR_INTERVAL_NO_WAIT) );

  // Export the message
  message->setIsExported(PR_TRUE);
  messageHandle->handle = message->getHandle();

  releaseHandledObject(consumer);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(consumer);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageWithTimeout(const MQConsumerHandle consumerHandle, 
                              MQInt32 timeoutMilliSeconds,
                              MQMessageHandle *      messageHandle)
{
  static const char FUNCNAME[] = "MQReceiveMessageWithTimeout";
  MQError errorCode = MQ_SUCCESS;
  MessageConsumer * consumer = NULL;
  Message * message = NULL;
                                                                  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageHandle == NULL, MQ_NULL_PTR_ARG );
  messageHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Convert consumerHandle to a MessageConsumer pointer
  consumer = (MessageConsumer*)getHandledObject(consumerHandle.handle, 
                                                MESSAGE_CONSUMER_OBJECT);
  CNDCHK( consumer == NULL, MQ_STATUS_INVALID_HANDLE);

  CNDCHK( consumer->getReceiveMode() != SESSION_SYNC_RECEIVE, MQ_NOT_SYNC_RECEIVE_MODE );
  // Block until an error occurs or the next message is received
  if (timeoutMilliSeconds == 0) {
  ERRCHK( consumer->receive(&message,  PR_INTERVAL_NO_TIMEOUT) );
  } else {
  ERRCHK( consumer->receive(&message, timeoutMilliSeconds) );
  }

  // Export the message 
  message->setIsExported(PR_TRUE);
  messageHandle->handle = message->getHandle();

  releaseHandledObject(consumer);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(consumer);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

