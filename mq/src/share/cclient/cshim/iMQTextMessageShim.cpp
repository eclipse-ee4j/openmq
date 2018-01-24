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
 * @(#)iMQTextMessageShim.cpp	1.9 06/26/07
 */ 

#include "mqproperties.h"
#include "mqtext-message.h"
#include "shimUtils.hpp"
#include "../client/TextMessage.hpp"
#include "../client/MessageConsumer.hpp"

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQCreateTextMessage(MQMessageHandle * messageHandle)
{
   static const char FUNCNAME[] = "MQCreateTextMessage";
  MQError errorCode = MQ_SUCCESS;
  TextMessage * textMessage = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageHandle == NULL, MQ_NULL_PTR_ARG );
  messageHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Create a new TextMessage
  MEMCHK( textMessage = new TextMessage() );
  ERRCHK( textMessage->getInitializationError() );
  
  // Make the TextMessage into a valid handle
  textMessage->setIsExported(PR_TRUE);
  messageHandle->handle = textMessage->getHandle();
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  DELETE( textMessage );
  MQ_ERROR_TRACE(FUNCNAME, errorCode);
  RETURN_STATUS( errorCode );
}


/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQGetTextMessageText(const MQMessageHandle messageHandle,
                       ConstMQString *       messageText)
{
  static const char FUNCNAME[] = "MQGetTextMessageText";
  MQError errorCode = MQ_SUCCESS;
  TextMessage * textMessage = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
                                                               
  // Make sure messageText is not NULL and then initialize it
  CNDCHK( messageText == NULL, MQ_NULL_PTR_ARG );
  *messageText = NULL;
  
  // Convert messageHandle to a TextMessage pointer
  textMessage = (TextMessage*)getHandledObject(messageHandle.handle,
                                               TEXT_MESSAGE_OBJECT);
  CNDCHK( textMessage == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( textMessage->getMessageTextString(messageText) );

  releaseHandledObject(textMessage);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(textMessage);
  MQ_ERROR_TRACE(FUNCNAME, errorCode);
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQSetTextMessageText(const MQMessageHandle messageHandle,
                       ConstMQString         messageText)
{
  static const char FUNCNAME[] = "MQSetTextMessageText";
  MQError errorCode = MQ_SUCCESS;
  TextMessage * textMessage = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
                                                               
  // Make sure messageText is not NULL and then initialize it
  CNDCHK( messageText == NULL, MQ_NULL_PTR_ARG );
  
  // Convert messageHandle to a TextMessage pointer
  textMessage = (TextMessage*)getHandledObject(messageHandle.handle,
                                               TEXT_MESSAGE_OBJECT);
  CNDCHK( textMessage == NULL, MQ_STATUS_INVALID_HANDLE );
  
  ERRCHK( textMessage->setMessageTextString(messageText) );

  releaseHandledObject(textMessage);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(textMessage);
  MQ_ERROR_TRACE(FUNCNAME, errorCode);
  RETURN_STATUS( errorCode );
}
