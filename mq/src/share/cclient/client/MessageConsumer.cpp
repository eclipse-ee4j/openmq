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
 * @(#)MessageConsumer.cpp	1.32 11/09/07
 */ 

#include "MessageConsumer.hpp"
#include "TextMessage.hpp"
#include "../io/PacketType.hpp"
#include "Session.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"
#include "../cshim/iMQCallbackUtils.hpp"

// The NSPR mutex may return a little earlier than when the timeout 
// completely expires.  We use this threshold to evaluate if it's likely
// that the timeout expired.  
static const double TIMEOUT_THRESHOLD_RATIO = 0.50;
static const PRIntervalTime TIMEOUT_THRESHOLD = 100;
static const UTF8String DMQ("mq.sys.dmq");

/*
 *
 */
MessageConsumer::MessageConsumer(Session * const sessionArg,
                                 Destination * const destinationArg,
                                 const PRBool isDurableArg,
                                 const PRBool isSharedArg,
                                 const UTF8String * const subscriptionNameArg,
                                 const UTF8String * const messageSelectorArg,
                                 const PRBool noLocalArg,
                                 MQMessageListenerFunc messageListenerArg,
                                 void * messageListenerCallbackDataArg)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  ASSERT( sessionArg != NULL );
  ASSERT( destinationArg != NULL );

  init();
  NULLCHK( sessionArg );
  NULLCHK( destinationArg );
  if (isDurableArg == PR_TRUE && subscriptionNameArg == NULL) {
    ERRCHK( MQ_CONSUMER_NO_DURABLE_NAME );
  }
  if (isSharedArg == PR_TRUE && subscriptionNameArg == NULL) {
    ERRCHK( MQ_CONSUMER_NO_SUBSCRIPTION_NAME );
  }
  
  // Set member variables to parameters
  this->session = sessionArg;
  this->isDurable = isDurableArg;
  this->isShared = isSharedArg;
  if (subscriptionNameArg != NULL) {
    this->subscriptionName = (UTF8String*)subscriptionNameArg->clone();
  } else {
    this->subscriptionName = NULL;
  }
  if (messageSelectorArg != NULL) {
    this->messageSelector = (UTF8String*)messageSelectorArg->clone();
  } else {
    this->messageSelector = NULL;
  }

  if (destinationArg->getIsQueue() == PR_TRUE) {
    this->noLocal = PR_FALSE;
  } else {
    this->noLocal = noLocalArg;
    CNDCHK( (this->isShared && this->noLocal == PR_TRUE), 
            MQ_UNSUPPORTED_ARGUMENT_VALUE );
    CNDCHK( (this->isDurable && 
              this->noLocal == PR_TRUE && 
              this->session->getConnection()->getClientID() == NULL), 
            MQ_NOLOCAL_DURABLE_CONSUMER_NO_CLIENTID );
  }

  this->messageListener = messageListenerArg;
  this->messageListenerCallbackData = messageListenerCallbackDataArg;
  this->receiveMode = this->session->getReceiveMode();
  if (this->messageListener == NULL) {
    ASSERT(this->receiveMode == SESSION_SYNC_RECEIVE);
  } else {
    ASSERT(this->receiveMode == SESSION_ASYNC_RECEIVE);
  }

  // If the destination is temporary, then we should enforce that it
  // was created by this connection.

  if (destinationArg->getIsTemporary()) {
    const UTF8String * destName = NULL;
    char * tempDestPrefix = NULL;
	destName = destinationArg->getName();
    CNDCHK( destName == NULL, MQ_DESTINATION_NO_NAME );
	tempDestPrefix = this->session->getConnection()->getTemporaryDestinationPrefix(destinationArg->getIsQueue());
    CNDCHK( tempDestPrefix == NULL, MQ_OUT_OF_MEMORY );
    if (STRNCMP(destName->getCharStr(), STRLEN(tempDestPrefix), tempDestPrefix) != 0) {
     DELETE_ARR( tempDestPrefix );
     ERRCHK( MQ_TEMPORARY_DESTINATION_NOT_IN_CONNECTION );
    }
    DELETE_ARR( tempDestPrefix );
  }
  this->destination = destinationArg->clone();
  CNDCHK( this->destination == NULL, MQ_OUT_OF_MEMORY );
  if (DMQ.equals(this->destination->getName())) {
    this->isDMQConsumer = PR_TRUE;
  }

  if (this->receiveMode == SESSION_SYNC_RECEIVE) { 
    MEMCHK( this->receiveQueue = new ReceiveQueue() );
    this->receiveQueue->setSyncReceiveConsumer(this);
    if (this->session->getIsStopped()) { 
      this->receiveQueue->stop();
    }
  }

  this->isInitialized = PR_TRUE;
  return;
Cleanup:

  // Have connection delete the receive queues through session.
  DELETE( this->receiveQueue );
  DELETE( this->subscriptionName );
  DELETE( this->messageSelector );

  this->isInitialized = PR_FALSE;
  this->initializationError = errorCode;

  HANDLED_DELETE( this->destination ) ;
}


/*
 *
 */
MessageConsumer::~MessageConsumer()
{
  CHECK_OBJECT_VALIDITY();

  this->close();
  DELETE( this->receiveQueue );
  DELETE( this->subscriptionName );
  DELETE( this->messageSelector );
  this->isInitialized = PR_FALSE;

  HANDLED_DELETE( this->destination );
}


/*
 *
 */
void
MessageConsumer::init()
{
  CHECK_OBJECT_VALIDITY();

  this->session       = NULL;
  this->destination   = NULL;
  this->isDurable     = PR_FALSE;
  this->isShared      = PR_FALSE;
  this->isTopic       = PR_FALSE;
  this->subscriptionName   = NULL;
  this->messageSelector  = NULL;
  this->noLocal       = PR_FALSE;
  this->consumerID    = LL_Zero();
  this->receiveQueue  = NULL;
  this->isInitialized = PR_FALSE;
  this->initializationError = MQ_SUCCESS;
  this->messageArrivedCallback = NULL;
  this->messageArrivedCallbackData = NULL;
  this->registered   = PR_FALSE;
  this->messageListener = NULL;
  this->messageListenerCallbackData = NULL;
  this->receiveMode  = SESSION_SYNC_RECEIVE;
  this->isClosed     = PR_FALSE;
  this->prefetchMaxMsgCount = -1;
  this->prefetchThresholdPercent = 50;
  this->hasLastDeliveredSysMessageID = PR_FALSE;
  this->isDMQConsumer = PR_FALSE;

}

/*
 * This is should only be called by Session::closeConsumer
 * and MessageConsumer::~MessageConsumer.
 */
MQError
MessageConsumer::close()
{
  CHECK_OBJECT_VALIDITY();

  this->isClosed = PR_TRUE;

  // Close the receiveQueue
  if (this->receiveMode == SESSION_SYNC_RECEIVE) {
    if ((this->receiveQueue == NULL) || (this->receiveQueue->getIsClosed())) {
    return MQ_SUCCESS;
    }
  
    this->receiveQueue->stop();
    this->receiveQueue->close(PR_TRUE);
  }

  return MQ_SUCCESS;
}

/*
 * This should only be called by Session::stop
 */
void
MessageConsumer::stop()
{
  CHECK_OBJECT_VALIDITY();

  if (this->receiveQueue != NULL) {
    this->receiveQueue->stop();
  }
}

/*
 * This should only be called by Session::start
 */
void
MessageConsumer::start()
{
  CHECK_OBJECT_VALIDITY();

  if (this->receiveQueue != NULL) {
    this->receiveQueue->start();
  }
}

/*
 *
 */
const Destination *
MessageConsumer::getDestination() const
{
  CHECK_OBJECT_VALIDITY();

  return this->destination;
}

/*
 *
 */
PRInt64
MessageConsumer::getConsumerID() const
{
  CHECK_OBJECT_VALIDITY();

  return this->consumerID;
}

/*
 *
 */
void
MessageConsumer::setConsumerID(PRInt64 id)
{
  CHECK_OBJECT_VALIDITY();

  this->consumerID = id;
  registered = PR_TRUE;
}

PRBool
MessageConsumer::isRegistered() const
{
  CHECK_OBJECT_VALIDITY();

  return registered;
}

/*
 *
 */
PRBool
MessageConsumer::getIsTopic() const
{
  CHECK_OBJECT_VALIDITY();

  return this->isTopic;
}

/*
 *
 */
PRBool
MessageConsumer::getIsDurable() const
{
  CHECK_OBJECT_VALIDITY();

  return this->isDurable;
}

/*
 *
 */
PRBool
MessageConsumer::getIsShared() const
{
  CHECK_OBJECT_VALIDITY();

  return this->isShared;
}

/*
 *
 */
PRBool
MessageConsumer::getNoLocal() const
{
  CHECK_OBJECT_VALIDITY();

  return this->noLocal;
}

/*
 *
 */
const UTF8String *
MessageConsumer::getSubscriptionName() const
{
  CHECK_OBJECT_VALIDITY();

  return this->subscriptionName;
}

const UTF8String *
MessageConsumer::getMessageSelector() const
{
  CHECK_OBJECT_VALIDITY();

  return this->messageSelector;
}

/*
 *
 */
PRInt32
MessageConsumer::getAckMode() const
{
  CHECK_OBJECT_VALIDITY();

  return this->session->getAckMode();
}

/*
 *
 */
ReceiveMode
MessageConsumer::getReceiveMode() const
{
  CHECK_OBJECT_VALIDITY();

  return this->receiveMode;
}


ReceiveQueue * 
MessageConsumer::getReceiveQueue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->receiveQueue;
}


/*
 *
 */
PRInt32
MessageConsumer::getPrefetchMaxMsgCount() const
{
  CHECK_OBJECT_VALIDITY();

  return this->prefetchMaxMsgCount;
}

/*
 *
 */
void
MessageConsumer::setPrefetchMaxMsgCount(PRInt32 prefetchSize)
{
  CHECK_OBJECT_VALIDITY();

  this->prefetchMaxMsgCount = prefetchSize;
}


/*
 *
 */
PRFloat64
MessageConsumer::getPrefetchThresholdPercent() const
{
  CHECK_OBJECT_VALIDITY();

  return this->prefetchThresholdPercent;
}

/*
 *
 */
void
MessageConsumer::setPrefetchThresholdPercent(PRFloat64 prefetchThreshold)
{
  CHECK_OBJECT_VALIDITY();

  this->prefetchThresholdPercent = prefetchThreshold;
}


/*
 *
 */
PRBool
MessageConsumer::getIsInitialized() const
{
  CHECK_OBJECT_VALIDITY();

  return isInitialized;
}

/*
 *
 */
MQError
MessageConsumer::getInitializationError() const
{
  CHECK_OBJECT_VALIDITY();
  RETURN_IF_ERROR( HandledObject::getInitializationError() );

  return initializationError;
}

/*
 *
 */
Session *
MessageConsumer::getSession() const
{
  CHECK_OBJECT_VALIDITY();

  return this->session;
}

PRBool
MessageConsumer::getHasLastDeliveredSysMessageID() const
{
  return hasLastDeliveredSysMessageID;
}

const SysMessageID *
MessageConsumer::getLastDeliveredSysMessageID() const
{
  return &(this->lastDeliveredSysMessageID);
}

/*
 *
 */
MQError
MessageConsumer::receiveNoWait(Message ** const message)
{
  return this->receive(message, PR_INTERVAL_NO_WAIT);
}

/*
 *
 */
MQError
MessageConsumer::receive(Message ** const message)
{
  return this->receive(message, PR_INTERVAL_NO_TIMEOUT);
}

/*
 *
 */
MQError
MessageConsumer::receive(Message ** const message, 
                         const PRUint32 timeoutMilliSeconds)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Packet * packet = NULL;

  NULLCHK( message );
  *message = NULL;

  CNDCHK( !this->getIsInitialized(), MQ_CONSUMER_NOT_INITIALIZED );

  CNDCHK( this->receiveMode != SESSION_SYNC_RECEIVE, MQ_NOT_SYNC_RECEIVE_MODE );

  // Check if the receiveQueue or Session have been closed 
  ASSERT( receiveQueue != NULL );

  while (1) {

  CNDCHK( this->isClosed == PR_TRUE, MQ_CONSUMER_CLOSED );
  ASSERT( session != NULL );
  //CNDCHK( session->getIsClosed(), MQ_SESSION_CLOSED );

  if (timeoutMilliSeconds == PR_INTERVAL_NO_WAIT ||
      timeoutMilliSeconds == PR_INTERVAL_NO_TIMEOUT) {
  packet = (Packet*)receiveQueue->dequeueWait(timeoutMilliSeconds);
  } else {
  packet = (Packet*)receiveQueue->dequeueWait(timeoutMilliSeconds * 1000);
  }

  // If no packet was returned, the timeout expired, or the connection was closed
  if (packet == NULL) {
    if (timeoutMilliSeconds == PR_INTERVAL_NO_WAIT) {
      ERRCHK( MQ_NO_MESSAGE );
    } else if (this->receiveQueue->getIsClosed() == PR_TRUE) {
      ERRCHK( MQ_CONSUMER_CLOSED );
    } else if (timeoutMilliSeconds != PR_INTERVAL_NO_TIMEOUT) {
      ERRCHK( MQ_TIMEOUT_EXPIRED );
    } else {
      LOG_WARNING(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID,
                    MQ_CONSUMER_EXCEPTION,
                    "Failed to receive a message." ));

      LOG_FINE(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID,
                 MQ_CONSUMER_EXCEPTION,
                 "MessageConsumer::receive() failing.  "
                 "timeoutMilliSeconds = %d", timeoutMilliSeconds ));

      ERRCHK( MQ_CONSUMER_EXCEPTION );
    }
  }

  // Convert the packet to a Message
  MEMCHK( *message = Message::createMessage(packet) );
  packet = NULL;  // message owns it now

  // Make sure the message was properly initialized.
  ERRCHK( (*message)->getInitializationError() );

  LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "MessageConsumer::receive allocated new message 0x%p",
               *message ));
  if (this->isDMQConsumer == PR_FALSE && ((*message)->isExpired()) == PR_TRUE) {
    ERRCHK( session->acknowledgeExpiredMessage(*message) );
    this->session->messageDelivered();
    receiveQueue->receiveDone();
    continue;
  } 
  
  if (this->hasLastDeliveredSysMessageID == PR_FALSE) {
    this->hasLastDeliveredSysMessageID = PR_TRUE;
  }
  this->lastDeliveredSysMessageID = *((*message)->getSystemMessageID()); 

  (*message)->setSession(this->session);
  ERRCHK( session->acknowledge(*message, PR_FALSE) );

  this->session->messageDelivered();

  // The receive has finished
  receiveQueue->receiveDone();

  break;

  } //while

  return MQ_SUCCESS;

Cleanup:
  if (packet != NULL) {
    this->session->messageDelivered();
  }

  DELETE( packet );
  HANDLED_DELETE( *message );

  // The receive has finished
  if (receiveQueue) {
    receiveQueue->receiveDone();
  }

  return errorCode;
}


MQError
MessageConsumer::beforeMessageListener(const Message * message, MQError mqerror)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool invoked = PR_FALSE;
  int xaerr = TM_OK;

  ASSERT( this->session->getIsXA() == PR_TRUE );
  XASession * xasession = (XASession *)this->session;
  errorCode = invokeMessageListenerBA(this, xasession->getBeforeMessageListenerFunc(),
                    xasession->getMessageListenerBACallbackData(), message, mqerror, &invoked);
  if (errorCode != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, xasession->getConnection()->id(), errorCode,
    "Invocation of beforeMessageListener callback failed because '%s' (%d) for message 0x%p to consumer 0x%p",
                errorStr(errorCode), errorCode, message, this ));
    return errorCode;
  }
  return errorCode;
}


void
MessageConsumer::afterMessageListener(const Message * message, MQError mqerror)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool invoked = PR_FALSE;

  ASSERT( this->session->getIsXA() == PR_TRUE );
  XASession * xasession = (XASession *)this->session;
  errorCode = invokeMessageListenerBA(this, xasession->getAfterMessageListenerFunc(),
                xasession->getMessageListenerBACallbackData(), message, mqerror, &invoked);
  if (errorCode != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, xasession->getConnection()->id(), errorCode,
    "Invocation of afterMessageListener callback failed because '%s' (%d) for message 0x%p to consumer 0x%p",
                errorStr(errorCode), errorCode, message, this ));
  }
  return;
}


MQError
MessageConsumer::onMessage(Message * message, PRBool * messageListenerInvoked)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  MQError ackErrorCode = MQ_SUCCESS;
  *messageListenerInvoked = PR_FALSE;

  if (this->isClosed == PR_TRUE) {
    return MQ_CONSUMER_CLOSED;
  }

  if (this->isDMQConsumer == PR_FALSE && message->isExpired() == PR_TRUE) {
    if ((errorCode = session->acknowledgeExpiredMessage(message)) != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, errorCode,
        "Expiring message before async-delivery failed: '%s' (%d) for message 0x%p to consumer 0x%p",
        errorStr(errorCode), errorCode, message, this ));
    }
    return errorCode;
  }

  ASSERT( this->session != NULL );
  if (this->session->getIsXA() == PR_TRUE) { 
    if ((errorCode = beforeMessageListener(message, MQ_SUCCESS)) != MQ_SUCCESS) {
      return errorCode;
    }
  }
  message->setSession(this->session);
  errorCode = invokeMessageListener(this, this->messageListener, 
                                    this->messageListenerCallbackData, 
                                     message, messageListenerInvoked);
  if (*messageListenerInvoked != PR_TRUE) {
    LOG_FINE(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, errorCode,
               "Attempt to invoke message listener failed because '%s' (%d) for message 0x%p to consumer 0x%p",
                errorStr(errorCode), errorCode, message, this ));
    if (this->session->getIsXA() == PR_TRUE) { 
      afterMessageListener(message, errorCode);
    }
    return errorCode;
  }

  if (this->hasLastDeliveredSysMessageID == PR_FALSE) {
    this->hasLastDeliveredSysMessageID = PR_TRUE;
  }
  this->lastDeliveredSysMessageID = *(message->getSystemMessageID());

  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "Successfully delivered message 0x%p to consumer 0x%p message listener",
                  message, this ));
    ERRCHK( this->session->acknowledge(message, PR_TRUE) );
    if (this->session->getIsXA() == PR_TRUE) { 
      afterMessageListener(message, MQ_SUCCESS);
    }
    return MQ_SUCCESS;
  } 

  LOG_WARNING(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, errorCode,
                "Message listener invocation failed because '%s' (%d) for message 0x%p to consumer 0x%p",
                errorStr(errorCode), errorCode, message, this ));
  if (this->session->getAckMode() == AUTO_ACKNOWLEDGE
      || this->session->getAckMode() == DUPS_OK_ACKNOWLEDGE) {
    ERRCHK( message->setJMSRedelivered(PR_TRUE) );
    errorCode = invokeMessageListener(this, this->messageListener, 
                                      this->messageListenerCallbackData,
                                      message, messageListenerInvoked);
    if (*messageListenerInvoked == PR_TRUE && errorCode == MQ_SUCCESS) {
      LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                   "Successfully redelivered message 0x%p to consumer 0x%p message listener",
                   message, this ));
      ERRCHK( this->session->acknowledge(message, PR_TRUE) );
    }
    return errorCode;
  }

  /**
   * now errorCode must be messageListener's return error if not MQ_SUCCESS 
   */
  ackErrorCode = this->session->acknowledge(message, PR_TRUE);
  if (ackErrorCode != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, ackErrorCode,
                 "Async message delivery acknowledgement failed: '%s' (%d) for message 0x%p to consumer 0x%p (MQMessageListenerFunc return=%d)",
                 errorStr(ackErrorCode), ackErrorCode, message, this, errorCode ));
    errorCode = ackErrorCode;
   
  } else if (errorCode != MQ_SUCCESS && errorCode != MQ_CALLBACK_RUNTIME_ERROR) {
    errorCode = MQ_CALLBACK_RUNTIME_ERROR;
  }

Cleanup:
  LOG_WARNING(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, errorCode,
               "Async message delivery processing failed: '%s' (%d) for message 0x%p to consumer 0x%p",
                errorStr(errorCode), errorCode, message, this ));
  if (this->session->getIsXA() == PR_TRUE) { 
    afterMessageListener(message, errorCode);
  }
  return errorCode;
}


/*
 *
 */
HandledObjectType
MessageConsumer::getObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return MESSAGE_CONSUMER_OBJECT;
}


/*
 *
 */
MQError
MessageConsumer::setMessageArrivedCallback(const MQMessageArrivedFunc messageArrivedFunc,
                                           void * messageArrivedFuncData)
{
  CHECK_OBJECT_VALIDITY();

  if (this->receiveMode != SESSION_SYNC_RECEIVE) return MQ_NOT_SYNC_RECEIVE_MODE;

  this->messageArrivedCallback     = messageArrivedFunc;
  this->messageArrivedCallbackData = messageArrivedFuncData;
  return MQ_SUCCESS;
}


/*
 *
 */
void
MessageConsumer::messageEnqueued() const
{
  CHECK_OBJECT_VALIDITY();

  // If the user has installed a callback for message arrival notfication, 
  // then make the callback.
  if (messageArrivedCallback != NULL) {
    MQError errorCode = invokeMessageArrivedCallback(this, messageArrivedCallback, 
                                                 messageArrivedCallbackData);
    if (errorCode != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, errorCode,
          "Invoking message arrived callback failed because '%s' (%d)",
                                         errorStr(errorCode), errorCode ));
    }
  }
}
