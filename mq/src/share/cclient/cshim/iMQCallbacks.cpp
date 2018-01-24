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
 * @(#)iMQCallbacks.cpp	1.19 11/09/07
 */ 

#include "mqcallbacks-priv.h"
#include "iMQCallbackUtils.hpp"
#include "../client/MessageConsumer.hpp"
#include "../client/Connection.hpp"
#include "../client/Session.hpp"
#include "shimUtils.hpp"

//
// These methods are used to install callbacks 
//

MQError
invokeMessageListener(const MessageConsumer *   consumer,
                      MQMessageListenerFunc     messageListener,
                      void *                    callbackData,
                      Message *                 message, PRBool *invoked)
{
  MQError errorCode = MQ_SUCCESS;

  MQConsumerHandle consumerHandle;
  MQSessionHandle  sessionHandle;
  MQMessageHandle  messageHandle;
  Session *        session;

  ASSERT( invoked != NULL );
  NULLCHK( invoked );
  *invoked = PR_FALSE;

  ASSERT( consumer != NULL );
  NULLCHK( consumer );
  ASSERT( messageListener != NULL );
  NULLCHK( messageListener );
  ASSERT( message != NULL );
  NULLCHK( message );

  consumerHandle.handle = consumer->getHandle();
  
  session = consumer->getSession();
  ASSERT( session != NULL );
  NULLCHK( session );
  sessionHandle.handle = session->getHandle();

  messageHandle.handle = message->getHandle();

  *invoked = PR_TRUE;
  errorCode = (messageListener)(sessionHandle, consumerHandle, messageHandle, callbackData);

Cleanup:
  CLEAR_ERROR_TRACE( PR_FALSE );
  return errorCode;
}


MQError
invokeMessageListenerBA(const MessageConsumer *   consumer,
                        MQMessageListenerBAFunc   messageListenerBA,
                        void *                    callbackData,
                        const Message *           message, 
                        MQError                   mqerror, 
                        PRBool                    *invoked)
{
  MQError errorCode = MQ_SUCCESS;

  MQConsumerHandle consumerHandle;
  MQSessionHandle  sessionHandle;
  MQMessageHandle  messageHandle;
  Session *        session;

  ASSERT( invoked != NULL );
  NULLCHK( invoked );
  *invoked = PR_FALSE;

  ASSERT( consumer != NULL );
  NULLCHK( consumer );
  ASSERT( messageListenerBA != NULL );
  NULLCHK( messageListenerBA );
  ASSERT( message != NULL );
  NULLCHK( message );

  consumerHandle.handle = consumer->getHandle();

  session = consumer->getSession();
  ASSERT( session != NULL );
  NULLCHK( session );
  sessionHandle.handle = session->getHandle();

  messageHandle.handle = message->getHandle();

  *invoked = PR_TRUE;
  errorCode = (messageListenerBA)(sessionHandle, consumerHandle, 
                                  messageHandle, mqerror, callbackData);

Cleanup:
  CLEAR_ERROR_TRACE( PR_FALSE );
  return errorCode;
}


EXPORTED_SYMBOL MQStatus
MQSetMessageArrivedFunc(const MQConsumerHandle     consumerHandle,
                        MQMessageArrivedFunc       messageCallback,
                        void *                     callbackData)
{
  static const char FUNCNAME[] = "MQSetMessageArrivedFunc";
  MQError errorCode = MQ_SUCCESS;
  MessageConsumer * consumer = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
                                                                  
  // Convert consumerHandle to a MessageConsumer pointer
  consumer = (MessageConsumer*)getHandledObject(consumerHandle.handle, 
                                                MESSAGE_CONSUMER_OBJECT);
  CNDCHK( consumer == NULL, MQ_STATUS_INVALID_HANDLE);

  ERRCHK( consumer->setMessageArrivedCallback(messageCallback, callbackData) );

  releaseHandledObject(consumer);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(consumer);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}



//
// These methods are used by the classes in the library to call out
//


/*
 *
 */
MQError
invokeMessageArrivedCallback(const MessageConsumer * consumer,
                             MQMessageArrivedFunc callback,
                             void* callbackData)
{
  ASSERT( consumer != NULL );
  ASSERT( consumer->getSession() != NULL );
  if ((consumer == NULL)             || 
      (consumer->getSession() == NULL) || 
      (callback == NULL)) 
  {
    return MQ_NULL_PTR_ARG;
  }

  // Invoke the callback
  MQConsumerHandle consumerHandle;
  MQSessionHandle sessionHandle;
  consumerHandle.handle = consumer->getHandle();
  sessionHandle.handle = consumer->getSession()->getHandle();
  (callback)(sessionHandle, consumerHandle, callbackData);
  CLEAR_ERROR_TRACE( PR_FALSE );

  return MQ_SUCCESS;
}

/*
 *
 */
MQBool
invokeCreateThreadCallback(MQThreadFunc startFunc,
                           void * arg,
                           MQCreateThreadFunc callback,
                           void * callbackData)
{
  MQBool success = MQ_FALSE;
  ASSERT( callback != NULL );
  if (callback == NULL) {
    return MQ_FALSE;
  }

  // invoke the callback
  success = (callback)(startFunc, arg, callbackData);

  CLEAR_ERROR_TRACE( PR_FALSE );
  return success;
}

/*
 *
 */
void 
invokeExceptionListenerCallback(const Connection * const connection,
                                MQError exceptionError,
                                MQConnectionExceptionListenerFunc callback,
                                void * callbackData)
{
  ASSERT( connection != NULL );
  ASSERT( callback != NULL );
  if ((connection == NULL) || (callback == NULL)) {
    return;
  }

  // Invoke the callback
  MQStatus status;
  status.errorCode = exceptionError;
  MQConnectionHandle connectionHandle;
  connectionHandle.handle = connection->getHandle();
  (callback)(connectionHandle, status, callbackData);
  CLEAR_ERROR_TRACE( PR_FALSE );
}


void 
invokeLoggingCallback(const PRInt32 severity,
                      const PRInt32 logCode,
                      ConstMQString logMessage,
                      const PRInt64 timeOfMessage,
                      const PRInt64 connectionID,
                      ConstMQString filename,
                      const PRInt32 fileLineNumber,
                      MQLoggingFunc callback,
                      void* callbackData)
{
  /* caller checks callback != NULL before calling this function
   * still has chance that callback becomes NULL because application 
   * set callback func after the check and  before at here - this should
   * be a rare case - application should set log callback at begining  */

  if (callback == NULL) {
    return;
  }

  // invoke the callback
  (callback)((MQLoggingLevel)severity, (MQInt32)logCode, logMessage, 
             (MQInt64)timeOfMessage, (MQInt64)connectionID, 
             filename, (MQInt32)fileLineNumber, callbackData);
  CLEAR_ERROR_TRACE( PR_FALSE );
}
