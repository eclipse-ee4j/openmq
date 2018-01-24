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
 * @(#)iMQBytesMessageShim.cpp	1.9 06/26/07
 */ 

#include "mqproperties.h"
#include "mqbytes-message.h"
#include "shimUtils.hpp"
#include "../client/BytesMessage.hpp"
#include "../client/MessageConsumer.hpp"

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQCreateBytesMessage(MQMessageHandle * messageHandle)
{
  static const char FUNCNAME[] = "MQCreateBytesMessage";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);

  BytesMessage * bytesMessage = NULL;
  CNDCHK( messageHandle == NULL, MQ_NULL_PTR_ARG );
  messageHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Create a new BytesMessage
  MEMCHK( bytesMessage = new BytesMessage() );
  ERRCHK( bytesMessage->getInitializationError() );
  
  // Make the BytesMessage into a valid handle
  bytesMessage->setIsExported(PR_TRUE);
  messageHandle->handle = bytesMessage->getHandle();

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  DELETE( bytesMessage );
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetBytesMessageBytes(const MQMessageHandle messageHandle,
                         const MQInt8 ** messageBytes,
                         MQInt32 *       messageBytesSize)
{
  static const char FUNCNAME[] = "MQGetBytesMessageBytes";
  MQError errorCode = MQ_SUCCESS;
  BytesMessage * bytesMessage = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageBytes == NULL, MQ_NULL_PTR_ARG );
  CNDCHK( messageBytesSize == NULL, MQ_NULL_PTR_ARG );
  *messageBytes = NULL;
  *messageBytesSize = 0;
  
  // Convert messageHandle to a BytesMessage pointer
  bytesMessage = (BytesMessage*)getHandledObject(messageHandle.handle,
                                                 BYTES_MESSAGE_OBJECT);
  CNDCHK( bytesMessage == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( bytesMessage->getMessageBytes((const PRUint8**)messageBytes,
                                        (PRInt32 *)messageBytesSize) );
  
  releaseHandledObject(bytesMessage);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(bytesMessage);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQSetBytesMessageBytes(const MQMessageHandle messageHandle,
                         const MQInt8 *        messageBytes,
                         MQInt32               messageBytesSize)
{
  static const char FUNCNAME[] = "MQSetBytesMessageBytes";
  MQError errorCode = MQ_SUCCESS;
  BytesMessage * bytesMessage = NULL;
                                                                  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( messageBytes == NULL, MQ_NULL_PTR_ARG );
  
  // Convert messageHandle to a BytesMessage pointer
   bytesMessage = (BytesMessage*)getHandledObject(messageHandle.handle,
                                                  BYTES_MESSAGE_OBJECT);
  CNDCHK( bytesMessage == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( bytesMessage->setMessageBytes((const PRUint8*)messageBytes,
                                        messageBytesSize) );

  releaseHandledObject(bytesMessage);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(bytesMessage);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
