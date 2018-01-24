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
 * @(#)Session.cpp	1.43 10/17/07
 */ 

#include "Session.hpp"
#include "Connection.hpp"
#include "SessionQueueReader.hpp"
#include "../util/LogUtils.hpp"
#include "../io/Status.hpp"
#include "TextMessage.hpp"
#include "MessageID.hpp"


/*
 *
 */
Session::Session(      Connection * const connectionArg,
                 const PRBool             isTransactedArg, 
                 const AckMode            ackModeArg,
                 const ReceiveMode        receiveModeArg)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  this->init();
  this->isClosed             = PR_FALSE;
  this->isStopped            = PR_FALSE;
  this->isXA                 =  PR_FALSE;

  this->connection = connectionArg;

  ASSERT( connectionArg != NULL );
  if (this->connection == NULL) { //XXX
    LOG_WARNING(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                  "Session created with a NULL connection." ));
    ERRCHK( MQ_NULL_PTR_ARG );
  }

  if (isTransactedArg == PR_TRUE) {
    ackMode = SESSION_TRANSACTED;  
  } else {
    ackMode = ackModeArg;
    if (ackMode == 0) {
      ackMode = AUTO_ACKNOWLEDGE;
    }
  }

  ASSERT( receiveModeArg == SESSION_SYNC_RECEIVE || receiveModeArg == SESSION_ASYNC_RECEIVE );
  this->receiveMode = receiveModeArg;


  if (receiveModeArg == SESSION_ASYNC_RECEIVE) {
    MEMCHK( this->sessionQueue = new ReceiveQueue() );
    if (this->connection->getIsStopped() == PR_TRUE) {
      this->sessionQueue->stop();
      this->isStopped = PR_TRUE;
    }
    MEMCHK( this->sessionQueueReader = new SessionQueueReader(this) )
    ERRCHK( this->sessionQueueReader->getInitializationError() );
    sessionThread = this->sessionQueueReader->getReaderThread();
    if (this->isStopped == PR_FALSE) {
      ERRCHK( this->sessionMutex.trylock(sessionThread, &lockedByMe) );
    }
  }

  ERRCHK( this->connection->registerSession(this) );

  if (this->ackMode == SESSION_TRANSACTED) {
    errorCode =  this->startTransaction(); 
    if (errorCode != MQ_SUCCESS) {
      MQError err = this->connection->unregisterSession(this->sessionID);
      ERRCHK( errorCode );
    }
  }

  LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
             "Session created." ));

  return;

Cleanup:
  
  if (this->sessionQueueReader != NULL) {
    this->sessionQueueReader->close();
  }
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(sessionThread);
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::Session() cleanup: unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  DELETE( this->sessionQueue );
  DELETE( this->sessionQueueReader );

  this->initializationError = errorCode;

  this->isClosed             = PR_TRUE;
  this->isStopped            = PR_TRUE;

}


/*
 *
 */
Session::~Session()
{
  CHECK_OBJECT_VALIDITY();
  this->close(PR_FALSE);

  DELETE( this->sessionQueue );
  DELETE( this->sessionQueueReader );

  this->init();
}

/*
 *
 */
void
Session::init()
{
  CHECK_OBJECT_VALIDITY();

  this->sessionID            = 0;
  this->connection           = NULL;
  this->isTransacted         = PR_FALSE;
  this->ackMode              = CLIENT_ACKNOWLEDGE;
  this->receiveMode          = SESSION_SYNC_RECEIVE;
  this->sessionQueue         = NULL;
  this->sessionQueueReader   = NULL;
  this->sessionThread        = NULL;
  this->initializationError  = MQ_SUCCESS;
  this->transactionID        = 0;
  this->dupsOkLimit          = DEFAULT_DUPS_OK_LIMIT;

}


MQError
Session::getInitializationError() const
{
  CHECK_OBJECT_VALIDITY();
  RETURN_IF_ERROR( HandledObject::getInitializationError() );

  return initializationError;
}


PRBool
Session::getIsXA() const
{
  CHECK_OBJECT_VALIDITY();

  return this->isXA;
}

ReceiveMode
Session::getReceiveMode() const
{
  CHECK_OBJECT_VALIDITY();
  return this->receiveMode;
}

AckMode
Session::getAckMode() const
{
  CHECK_OBJECT_VALIDITY();
  return this->ackMode;
}


PRInt64
Session::getSessionID() const
{
  CHECK_OBJECT_VALIDITY();

  return this->sessionID;
}

void
Session::setSessionID(PRInt64 sessionIDArg)
{
  CHECK_OBJECT_VALIDITY();

  this->sessionID = sessionIDArg;
}


PRInt64
Session::getTransactionID() const
{
  CHECK_OBJECT_VALIDITY();

  return this->transactionID;
}



ReceiveQueue *
Session::getSessionQueue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->sessionQueue;
}


/*
 *
 */
iMQError
Session::checkSessionState()
{
  CHECK_OBJECT_VALIDITY();

  if (this->isClosed) {
    return IMQ_SESSION_CLOSED;
  }
  return IMQ_SUCCESS;
}


MQError
Session::start() 
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  closeMonitor.enter();

  errorCode = checkSessionState();
  if (errorCode != MQ_SUCCESS) {
    closeMonitor.exit();
    return errorCode;
  }

  isStopped = PR_FALSE;

  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
    if (this->sessionQueue != NULL && this->sessionQueueReader != NULL) {
      errorCode = this->sessionMutex.trylock(sessionThread, &lockedByMe);
      if (errorCode == MQ_SUCCESS) {
        this->sessionQueue->start();
      }
    }
  }
  else {
    errorCode = consumerTable.operationAll(MessageConsumerTable::START_CONSUMER, NULL);
  }

  closeMonitor.exit();
  return errorCode;
}


MQError
Session::stop() 
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  closeMonitor.enter();

  if (this->isStopped == PR_TRUE) {
      closeMonitor.exit();
      return MQ_SUCCESS;
  }

  isStopped = PR_TRUE;

  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
    if (this->sessionQueue != NULL && this->sessionQueueReader != NULL) {
      if (PR_GetCurrentThread() == sessionThread) {
        isStopped = PR_FALSE;
        closeMonitor.exit();
        return MQ_CONCURRENT_DEADLOCK;
      }
      errorCode = this->sessionMutex.trylock(sessionThread, &lockedByMe);
      ASSERT( errorCode == MQ_SUCCESS && !lockedByMe );
      this->sessionQueue->stop();
      errorCode = this->sessionMutex.unlock(sessionThread);
    }
  }
  else {
    errorCode = consumerTable.operationAll(MessageConsumerTable::STOP_CONSUMER, NULL);
  }

  closeMonitor.exit();
  return errorCode;
}


/*
 *
 */
iMQError
Session::close(PRBool clean)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  closeMonitor.enter();
    if (this->isClosed) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), MQ_SUCCESS, 
                 "Trying to close a closed session." ));  
      closeMonitor.exit();
      return IMQ_SUCCESS;
    }

    LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), MQ_SUCCESS, 
               "Session::close() called on session: 0x%p.", this ));  

    this->isClosed = PR_TRUE;

    // Close all of the producers
    MessageProducer * producer = NULL;
    producersMonitor.enter();
    while (producers.size() > 0) {
      producers.get(0, (void**)&producer);
      ASSERT( producer != NULL );
      this->closeProducer(producer);
    }
    producersMonitor.exit();

    errorCode = this->stop();
    if (errorCode != MQ_SUCCESS) {
      this->isClosed = PR_FALSE;
      closeMonitor.exit();
      LOG_SEVERE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), 
                     errorCode, 
                    "Closing session 0x%p failed: unable to stop session because '%s' (%d).",
                     this, errorStr(errorCode), errorCode ));  
      return errorCode;
    }

    this->sessionMutex.lock(PR_INTERVAL_NO_TIMEOUT, &lockedByMe);
    ASSERT( lockedByMe == PR_TRUE );

    errorCode =consumerTable.operationAll(MessageConsumerTable::CLOSE_CONSUMER, NULL);
    if (errorCode != MQ_SUCCESS && errorCode != MQ_BROKER_CONNECTION_CLOSED) {
      LOG_WARNING(( CODELOC, SESSION_LOG_MASK, this->connection->id(), errorCode, 
                    "Closing session 0x%p: unable to close all consumers because '%s' (%d).",
                     this, errorStr(errorCode), errorCode ));  
    }

    if (this->ackMode == SESSION_TRANSACTED && this->isXA == PR_FALSE) {
      errorCode = this->connection->rollbackTransaction(this->transactionID); 
      if (errorCode != MQ_SUCCESS && errorCode != MQ_BROKER_CONNECTION_CLOSED) {
        LOG_WARNING(( CODELOC, SESSION_LOG_MASK, this->connection->id(), errorCode, 
                    "Closing session 0x%p: unable to rollback transaction because '%s' (%d).",
                     this, errorStr(errorCode), errorCode ));  
      }
    }

    if (this->sessionQueueReader != NULL) {
	  this->sessionQueueReader->close();
    }


    errorCode = this->connection->unregisterSession(this->sessionID);
    if (errorCode != MQ_SUCCESS && errorCode != MQ_BROKER_CONNECTION_CLOSED) {
        LOG_WARNING(( CODELOC, SESSION_LOG_MASK, this->connection->id(), 
                     errorCode, 
                    "Unregister session 0x%p failed because '%s' (%d).",
                     this, errorStr(errorCode), errorCode ));  
    }

    // The handle to this object is no longer valid
    this->setIsExported(PR_FALSE);
  
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "Closed the session" ));  

    this->sessionMutex.unlock();

  closeMonitor.exit();

  if (clean) this->connection->removeSession(this);

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
Session::createDestination(const UTF8String * const name, 
                           const PRBool isQueue,
                           Destination ** const destination)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  NULLCHK( name );
  NULLCHK( destination );
  *destination = NULL;

  ERRCHK( checkSessionState() );
  
  MEMCHK( *destination = new Destination(this->connection, name, isQueue, PR_FALSE) );
  ERRCHK( (*destination)->getInitializationError() );

  // Make sure the destination creation succeeded
  CNDCHK( !name->equals((*destination)->getName()), IMQ_OUT_OF_MEMORY );

  LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
             "Create destination '%s' succeeded.",
             name->getCharStr() ));  

  return IMQ_SUCCESS;
  
Cleanup:
  if (destination != NULL) {
    HANDLED_DELETE( *destination );
  }
  LOG_WARNING(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
                "Failed to create destination '%s' because '%s' (%d).", 
                (name == NULL) ? "<NULL>" : name->getCharStr(), 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError 
Session::createTemporaryDestination(const PRBool isQueue, Destination ** const destination)
{
  CHECK_OBJECT_VALIDITY();

  char tempDestinationStr[MAX_DESTINATION_NAME_LEN];
  char *tempDestinationStrPrefix = NULL;
  iMQError errorCode = IMQ_SUCCESS;
  UTF8String * destinationName = NULL;
  const char * clientIDCharStr = NULL;
  const UTF8String * clientID = NULL;

  ERRCHK( checkSessionState() );

  NULLCHK( destination );
  *destination = NULL;
 
  tempDestinationStrPrefix = connection->getTemporaryDestinationPrefix(isQueue);
  CNDCHK( tempDestinationStrPrefix == NULL, MQ_OUT_OF_MEMORY );

  // Create a temporary destination name:
  SNPRINTF( tempDestinationStr, MAX_DESTINATION_NAME_LEN, "%s/%d",
                        tempDestinationStrPrefix,
                        connection->getTemporaryDestinationSequence() );
  tempDestinationStr[MAX_DESTINATION_NAME_LEN-1] = '\0'; // just to be safe
  DELETE_ARR( tempDestinationStrPrefix );

  MEMCHK( destinationName = new UTF8String(tempDestinationStr) );

  MEMCHK( *destination = new Destination(this->connection, destinationName, isQueue, PR_TRUE) );
  ERRCHK( (*destination)->getInitializationError() );
  DELETE( destinationName );  // Destination makes a copy

  ERRCHK( this->connection->createDestination(*destination) );

  LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
             "Create temporary destination succeeded" ));

  return IMQ_SUCCESS;
  
Cleanup:
  DELETE( destinationName );
  DELETE_ARR( tempDestinationStrPrefix );
  if (destination != NULL) {
    HANDLED_DELETE( *destination );
  }

  LOG_WARNING(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
                "Failed to create a temporary destination because '%s' (%d).", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError 
Session::createProducer(MessageProducer ** const producer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRUint32 index;

  NULLCHK( producer );
  *producer = NULL;

  ERRCHK( checkSessionState() );

  // Create the producer
  MEMCHK( *producer = new MessageProducer(this) );
  ERRCHK( (*producer)->getInitializationError() );
  

  producersMonitor.enter();
  errorCode = checkSessionState();
  if (errorCode != MQ_SUCCESS) {
    producersMonitor.exit();
    ERRCHK( errorCode );
  }
  ASSERT( producers.find((*producer), &index) != MQ_SUCCESS );
  errorCode =  producers.add(*producer); 
  producersMonitor.exit();
  ERRCHK( errorCode ); 

  return MQ_SUCCESS;

Cleanup:
  if (producer != NULL) {
    HANDLED_DELETE( *producer );
  }
  return errorCode;
}

/*
 *
 */
iMQError 
Session::createProducer(Destination * const destination, MessageProducer ** const producer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = IMQ_SUCCESS;
  PRUint32 index;

  NULLCHK( destination );
  NULLCHK( producer );
  *producer = NULL;

  ERRCHK( checkSessionState() );

  // Create the producer
  MEMCHK( *producer = new MessageProducer(this, destination) );
  ERRCHK( (*producer)->getInitializationError() );
  
  producersMonitor.enter();
  errorCode = checkSessionState();
  if (errorCode != MQ_SUCCESS) {
    producersMonitor.exit();
    ERRCHK( errorCode );
  }
  ASSERT( producers.find((*producer), &index) != IMQ_SUCCESS );
  errorCode = producers.add((*producer)); 
  producersMonitor.exit();
  ERRCHK( errorCode );

  // Register this producer with the broker
  errorCode = (*producer)->validateDestination((*producer)->getDestination(), NULL); 
  if (errorCode != MQ_SUCCESS) {
    producersMonitor.enter();
    producers.remove(*producer);
    producersMonitor.exit();
    ERRCHK( errorCode );
  }

  return MQ_SUCCESS;

Cleanup:
  if ((producer != NULL) && (*producer != NULL)) {
    HANDLED_DELETE( *producer );
  }

  return errorCode;
}

/*
 *
 */
iMQError 
Session::createConsumer(Destination * const destination, 
                        const PRBool isDurable,
                        const PRBool isShared,
                        const UTF8String * const subscriptionName, 
                        const UTF8String * const messageSelector, 
                        const PRBool noLocal,
                        MQMessageListenerFunc messageListener,
                        void *  messageListenerCallbackData,
                        MessageConsumer ** const consumer)
{
  static const char FUNCNAME[] = "createConsumer"; 
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  NULLCHK( destination );
  NULLCHK( consumer );
  *consumer = NULL;

  ERRCHK( checkSessionState() );

  // You can only have durable or shared subscription on topics.
  CNDCHK( isDurable && destination->getIsQueue(), MQ_QUEUE_CONSUMER_CANNOT_BE_DURABLE );
  CNDCHK( isShared && destination->getIsQueue(), MQ_SHARED_SUBSCRIPTION_NOT_TOPIC );
  if (isDurable || isShared) {
    if (subscriptionName == NULL) {
        if (isDurable) {
          ERRCHK( MQ_CONSUMER_NO_DURABLE_NAME );
        }
        if (isShared) {
          ERRCHK( MQ_CONSUMER_NO_SUBSCRIPTION_NAME );
        }
    }
    if (isDurable && !isShared) {  
      ERRCHK( connection->setClientID(PR_FALSE) ); 
    } else {
      ERRCHK( connection->setClientID(PR_TRUE) ); 
    }
  }

  // Create the consumer
  MEMCHK( *consumer = new MessageConsumer(this, destination, isDurable, isShared, subscriptionName,
                          messageSelector, noLocal, messageListener, messageListenerCallbackData) );
  MQ_ERRCHK_TRACE( (*consumer)->getInitializationError(), FUNCNAME );
  CNDCHK( (isDurable || isShared) && !subscriptionName->equals((*consumer)->getSubscriptionName()), IMQ_OUT_OF_MEMORY );
  CNDCHK( messageSelector != NULL && !messageSelector->equals((*consumer)->getMessageSelector()), IMQ_OUT_OF_MEMORY );

  ERRCHK( sessionMutex.trylock(&lockedByMe) );
  ERRCHK( checkSessionState() );

  // Register this consumer with the broker
  MQ_ERRCHK_TRACE( connection->registerMessageConsumer(*consumer), FUNCNAME );

  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::createConsumer() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }

  return IMQ_SUCCESS;

Cleanup:
  if ((consumer != NULL) && (*consumer != NULL)) {
    Long consumerIDLong((*consumer)->getConsumerID());
    LOG_FINER(( CODELOC, SESSION_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
                "Session::createConsumer() failed, consumerID=%s, registered=%s", 
                consumerIDLong.toString(), 
                (((*consumer)->isRegistered() == PR_TRUE) ? "true":"false") ));

    if ((*consumer)->isRegistered() == PR_TRUE) {
       connection->unregisterMessageConsumer(*consumer);
       this->removeConsumer((*consumer)->getConsumerID(), NULL);
    }
    HANDLED_DELETE( *consumer );

  }
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::createConsumer() cleanup: unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  return errorCode;
}

MQError
Session::addConsumer(PRUint64 consumerID, MessageConsumer * const consumer)
{
  CHECK_OBJECT_VALIDITY();


  RETURN_ERROR_IF_NULL(consumer);

  MQError errorCode = this->consumerTable.add(consumerID, consumer);

  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
        "addConsumer(%lld, 0x%p) in session 0x%p success", consumerID, consumer, this));
  } else {
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, errorCode,
        "Failed to addConsumer(%lld, 0x%p) in session 0x%p because '%s' (%d)",
         consumerID, consumer, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;

}

MQError
Session::getConsumer(PRUint64 consumerID, MessageConsumer ** const consumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = this->consumerTable.get(consumerID, consumer);
  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
        "getConsumer(%lld) in session 0x%p success", consumerID, this));
  } else {
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, errorCode,
        "Failed to getConsumer(%lld) in session 0x%p because '%s' (%d)",
         consumerID, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;

}

MQError
Session::removeConsumer(PRUint64 consumerID, MessageConsumer ** const consumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = this->consumerTable.remove(consumerID, consumer);
  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
        "removeConsumer(%lld) in session 0x%p success", consumerID, this));
  } else {
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, errorCode,
        "Failed to removeConsumer(%lld) in session 0x%p because '%s' (%d)",
         consumerID, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


MQError
Session::commit()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;
  PRInt32 replyStatus = STATUS_ERROR;

  if (this->isXA == PR_TRUE) return MQ_XA_SESSION_IN_PROGRESS;

  if (this->receiveMode == SESSION_SYNC_RECEIVE
      || (this->receiveMode == SESSION_ASYNC_RECEIVE 
          && PR_GetCurrentThread() != this->sessionThread)) { 
    RETURN_IF_ERROR(checkSessionState());
  }
  CNDCHK( this->ackMode != SESSION_TRANSACTED, MQ_NOT_TRANSACTED_SESSION ); 

  ERRCHK( sessionMutex.trylock(&lockedByMe) ); 
  if (this->receiveMode == SESSION_SYNC_RECEIVE
      || (this->receiveMode == SESSION_ASYNC_RECEIVE 
          && PR_GetCurrentThread() != this->sessionThread)) { 
    ERRCHK( checkSessionState() );
  }

  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
    if (PR_GetCurrentThread() == sessionThread) {
      ERRCHK( acknowledge(this->sessionQueueReader->getCurrentMessage(),
                          SESSION_TRANSACTED, PR_TRUE) );
    }
  }

  ERRCHK( connection->commitTransaction(this->transactionID, &replyStatus) );
  unAckedMessageQueue.reset();
  {
  Long transactionIDLong(this->transactionID);
  LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
            "Session::commit transaction success for session 0x%p: transactionID=%s",
            this, transactionIDLong.toString() ));
  }

  ERRCHK( this->startTransaction() ); //XXX

  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::commit() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return MQ_SUCCESS;

Cleanup:
  if (replyStatus == STATUS_OK) {
      unAckedMessageQueue.reset();
  }
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::commit() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return errorCode;

}


MQError
Session::rollback()
{ 
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  if (this->isXA == PR_TRUE) return MQ_XA_SESSION_IN_PROGRESS;

  if (this->receiveMode == SESSION_SYNC_RECEIVE
      || (this->receiveMode == SESSION_ASYNC_RECEIVE 
          && PR_GetCurrentThread() != this->sessionThread)) { 
    RETURN_IF_ERROR(checkSessionState());
  }

  CNDCHK( this->ackMode != SESSION_TRANSACTED, MQ_NOT_TRANSACTED_SESSION ); 

  ERRCHK( this->sessionMutex.trylock(&lockedByMe) );

  ERRCHK( this->recover(PR_TRUE) );
  ERRCHK( connection->rollbackTransaction(this->transactionID) );
  {
  Long transactionIDLong(this->transactionID);
  LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
            "Session::rollback transaction success for session 0x%p: transactionID=%s",
            this, transactionIDLong.toString() ));
  }

  ERRCHK( this->startTransaction() ); //XXX
   
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::rollback() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return MQ_SUCCESS;

Cleanup:
 if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::rollback() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return errorCode;
}


MQError
Session::recover(PRBool fromRollback)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool lockedByMe = PR_FALSE;

  if (this->receiveMode == SESSION_SYNC_RECEIVE
      || (this->receiveMode == SESSION_ASYNC_RECEIVE 
          && PR_GetCurrentThread() != this->sessionThread)) { 
    RETURN_IF_ERROR(checkSessionState());
  }

  if (fromRollback == PR_TRUE) {
    CNDCHK( this->ackMode != SESSION_TRANSACTED, MQ_NOT_TRANSACTED_SESSION ); 
  } else {
    CNDCHK( this->ackMode == SESSION_TRANSACTED, MQ_TRANSACTED_SESSION ); 
  }

  ERRCHK( this->sessionMutex.trylock(&lockedByMe) );
  if (this->receiveMode == SESSION_SYNC_RECEIVE
      || (this->receiveMode == SESSION_ASYNC_RECEIVE 
          && PR_GetCurrentThread() != this->sessionThread)) { 
    ERRCHK( checkSessionState() );
  }

  ERRCHK( this->connection->stopSession(this) );
  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
    ERRCHK( this->redeliverMessagesInQueue(this->sessionQueue, fromRollback) );
  } else { 
    ERRCHK( consumerTable.operationAll(MessageConsumerTable::RECOVER_RECEIVEQUEUE, &fromRollback) );
  }
  ERRCHK( this->redeliverUnAckedMessages() );
  
  ERRCHK( this->connection->startSession(this) ); //XXX

  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::recover() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return MQ_SUCCESS;

Cleanup:
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock(); 
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::recover() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return errorCode;
}

MQError
Session::redeliverMessagesInQueue(ReceiveQueue *queue, PRBool setRedelivered)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Packet * packet = NULL;
  Message * message = NULL;
  MessageID * messageID = NULL;
  PRInt32 cnt = 0;

  if (queue == NULL) {
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_NULL_PTR_ARG,
               "Session::redeliverMessagesInQueue(setRedelivered=%d): queue NULL",
                setRedelivered ));
    return MQ_NULL_PTR_ARG;
  }
  if (queue->size() == 0) { //XXX
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "Session::redeliverMessagesInQueue(0x%p, setRedelivered=%d): queue empty",
                queue, setRedelivered ));
    return MQ_SUCCESS;
  }

  while (queue->size() > 0) {
    packet = (Packet *)queue->dequeue();  
    MEMCHK( message = Message::createMessage(packet) );
    packet = NULL;  // message owns it now
    MEMCHK( messageID = new MessageID(message) );
    ERRCHK( messageID->write(&ackBlockStream) );
    DELETE( messageID );
    HANDLED_DELETE( message );
    cnt++;
  }
  LOG_FINEST(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
              "Session::redeliverMessagesInQueue(0x%p, setRedelivered=%d): cnt=%d",
               queue, setRedelivered, cnt ));
  ERRCHK( connection->redeliver(this, PR_FALSE, 
                                ackBlockStream.getStreamBytes(),
                                ackBlockStream.numBytesWritten() ));
  ackBlockStream.reset();
  return MQ_SUCCESS;

Cleanup:
  ackBlockStream.reset();
  DELETE( packet );
  DELETE( messageID );
  HANDLED_DELETE( message ); 
  return errorCode;

}


/*
 *
 */
iMQError 
Session::writeJMSMessage(Message * const message, PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();

  if (this->isClosed) {
    return MQ_SESSION_CLOSED;
  }
  return connection->writeJMSMessage(this, message, producerID);
}

/*
 *
 */
MQError
Session::registerMessageProducer(const Destination * const destination, PRInt64 * producerID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( producerID );
  
  return connection->registerMessageProducer(this, destination, producerID);
}


/*
 *
 */
MQError
Session::unregisterMessageProducer(PRUint64 producerID)
{
  CHECK_OBJECT_VALIDITY();

 
  return connection->unregisterMessageProducer(producerID);
}


/*
 *
 */
iMQError 
Session::unsubscribeDurableConsumer(const UTF8String * const durableName)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  NULLCHK( durableName );

  // Make sure that no active consumer on this session has this durable name
  ERRCHK( consumerTable.operationAll(MessageConsumerTable::UNSUBSCRIBE_DURABLE, durableName) );

  ERRCHK( connection->setClientID(PR_TRUE) );
  ERRCHK( connection->unsubscribeDurableConsumer(durableName) );

  return MQ_SUCCESS;

Cleanup:
  return errorCode;
}

/*
 *
 */
iMQError
Session::closeProducer(MessageProducer * producer)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( producer );

  producersMonitor.enter();
  if (producers.remove(producer) == IMQ_SUCCESS) {
    producersMonitor.exit();
    producer->close();
    
    // Delete the producer
    HANDLED_DELETE( producer );
    return MQ_SUCCESS;
  }

  producersMonitor.exit();
  return IMQ_PRODUCER_NOT_IN_SESSION;

}


/*
 *
 */
MQError
Session::closeConsumer(MessageConsumer * consumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  MessageConsumer *mc = NULL;

  RETURN_ERROR_IF_NULL( consumer );

  errorCode = this->removeConsumer(consumer->getConsumerID(), &mc);
  if (errorCode != MQ_SUCCESS) {
    return MQ_CONSUMER_NOT_IN_SESSION;
  }
  ASSERT( mc != NULL );
  ASSERT( LL_EQ(consumer->getConsumerID(), mc->getConsumerID()) != 0 );

  //no check on return for it has been removed from session 
  errorCode = this->connection->unregisterMessageConsumer(consumer);

  consumer->close();
  HANDLED_DELETE( consumer );

Cleanup:
  return errorCode;
}

/*
 *
 */
Connection *
Session::getConnection()
{
  CHECK_OBJECT_VALIDITY();

  return connection;
}

/*
 *
 */
PRBool
Session::getIsClosed() const
{
  CHECK_OBJECT_VALIDITY();

  return isClosed;
}

/*
 *
 */
PRBool
Session::getIsStopped() const
{
  CHECK_OBJECT_VALIDITY();

  return isStopped;
}

/*
 *
 */
HandledObjectType
Session::getObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return SESSION_OBJECT;
}


void
Session::messageDelivered()
{
  CHECK_OBJECT_VALIDITY();

  if (this->isClosed != PR_TRUE) { 
    this->connection->messageDelivered();
  }
}


MQError
Session::acknowledge(Message * message, PRBool fromMessageListener)
{
  CHECK_OBJECT_VALIDITY();
  return acknowledge(message, this->ackMode, fromMessageListener);
}

MQError
Session::acknowledge(Message * message, AckMode useAckMode, 
                     PRBool fromMessageListener)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  MessageID * messageID = NULL;
  PRBool lockedByMe = PR_FALSE;

  NULLCHK( message );

  if (fromMessageListener) {
  ERRCHK( this->sessionMutex.trylock(sessionThread, &lockedByMe) );
  } else {
  ERRCHK( this->sessionMutex.trylock(&lockedByMe) );
  }

  switch (useAckMode) {

  case CLIENT_ACKNOWLEDGE:
    //not supported yet: clientAck limit if limited
    if (message->isAckProcessed() == PR_FALSE) {
      MEMCHK( messageID = new MessageID(message) );
      ERRCHK( unAckedMessageQueue.add(messageID) );
      messageID = NULL;
      message->setAckProcessed();
    }
    break;
  case DUPS_OK_ACKNOWLEDGE:
    if (message->isAckProcessed() == PR_FALSE) {
      MEMCHK( messageID = new MessageID(message) );
      ERRCHK( unAckedMessageQueue.add(messageID) );
      messageID = NULL;
      message->setAckProcessed();
    }
    if (unAckedMessageQueue.size() >= (PRUint32)dupsOkLimit
        || (this->receiveMode == SESSION_ASYNC_RECEIVE
            && sessionQueue->isEmpty() == PR_TRUE)) {
        ERRCHK( this->acknowledgeMessages(PR_FALSE, NULL) );
    }
    break;
  case AUTO_ACKNOWLEDGE:
    if (message->isAckProcessed() == PR_FALSE) {
      ERRCHK( this->acknowledgeMessage(message) );
      message->setAckProcessed();
    }
    break;
  case SESSION_TRANSACTED:
    if (message->isAckProcessed() == PR_FALSE) {
      if (this->isXA == PR_FALSE) { 
      MEMCHK( messageID = new MessageID(message) );
      ERRCHK( unAckedMessageQueue.add(messageID) );
      messageID = NULL;
      }
      message->setAckProcessed();
      ERRCHK( this->acknowledgeMessage(message) );
    }
    break;
  default://AUTO_ACK
    if (message->isAckProcessed() == PR_FALSE) {
      ERRCHK( this->acknowledgeMessage(message) );
      message->setAckProcessed();
    }

  } //switch

  if (lockedByMe) {
    MQError err = MQ_SUCCESS;
    if (fromMessageListener) err = this->sessionMutex.unlock(sessionThread);
    else                     err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::acknowledge() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return MQ_SUCCESS;

Cleanup:
  DELETE( messageID );
  if (lockedByMe) {
    MQError err = MQ_SUCCESS;
    if (fromMessageListener) err = this->sessionMutex.unlock(sessionThread);
    else                     err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::acknowledge() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return errorCode;
}


MQError
Session::acknowledgeMessage(const Message * const message)
{ 
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = IMQ_SUCCESS;

  RETURN_ERROR_IF_NULL( message );

  ERRCHK( ackBlockStream.writeInt64(message->getConsumerID()) );
  ERRCHK( message->getSystemMessageID()->writeID(&ackBlockStream) );

  ERRCHK( connection->acknowledge(this, ackBlockStream.getStreamBytes(),
                                        ackBlockStream.numBytesWritten()) );
  ackBlockStream.reset();
  return MQ_SUCCESS;

Cleanup:
  ackBlockStream.reset();
  Long sessionIDLong(this->sessionID);
  LOG_SEVERE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, errorCode,
               "Failed in acknowledgeMessage(0x%p), sessionID=%s because '%s' (%d)",
                message, sessionIDLong.toString(), errorStr(errorCode), errorCode ));
  return errorCode;
}


MQError
Session::acknowledgeExpiredMessage(const Message * const message)
{ 
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = IMQ_SUCCESS;

  RETURN_ERROR_IF_NULL( message );

  ERRCHK( ackExpiredBlockStream.writeInt64(message->getConsumerID()) );
  ERRCHK( message->getSystemMessageID()->writeID(&ackExpiredBlockStream) );

  ERRCHK( connection->acknowledgeExpired(ackExpiredBlockStream.getStreamBytes(),
                                         ackExpiredBlockStream.numBytesWritten()) );
  ackExpiredBlockStream.reset();
  return MQ_SUCCESS;

Cleanup:
  ackExpiredBlockStream.reset();
  Long sessionIDLong(this->sessionID);
  LOG_SEVERE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, errorCode,
               "Failed in acknowledgeExpiredMessage(0x%p), sessionID=%s because '%s' (%d)",
                message, sessionIDLong.toString(), errorStr(errorCode), errorCode ));
  return errorCode;
}


/**
 *
 */
MQError
Session::acknowledgeMessages(PRBool fromClientAcknowledge, Message * message)
{ 
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = MQ_SUCCESS;
  MessageID * messageID = NULL;
  PRBool found = PR_FALSE;
  PRInt32 cnt = 0;
  PRUint32 i;
  PRBool lockedByMe = PR_FALSE;
 
  if (fromClientAcknowledge == PR_TRUE) {
    if (this->receiveMode == SESSION_SYNC_RECEIVE
        || (this->receiveMode == SESSION_ASYNC_RECEIVE 
            && PR_GetCurrentThread() != this->sessionThread)) { 
      RETURN_IF_ERROR(checkSessionState());
    }
    ASSERT( message != NULL );
    NULLCHK( message );
    CNDCHK( this->getAckMode() != CLIENT_ACKNOWLEDGE, MQ_SESSION_NOT_CLIENT_ACK_MODE );
    CNDCHK( this != message->getSession(), MQ_MESSAGE_NOT_IN_SESSION )

    ERRCHK( this->sessionMutex.trylock(&lockedByMe) ); 
    if (this->receiveMode == SESSION_SYNC_RECEIVE
        || (this->receiveMode == SESSION_ASYNC_RECEIVE 
            && PR_GetCurrentThread() != this->sessionThread)) { 
      ERRCHK( checkSessionState() );
    }
    if (message->isAckProcessed() == PR_FALSE) {
      MEMCHK( messageID = new MessageID(message) );
      ERRCHK( unAckedMessageQueue.add(messageID) );
      messageID = NULL;
      message->setAckProcessed();
    } else {
      for (i = 0; i < unAckedMessageQueue.size(); i++) {
        ERRCHK( unAckedMessageQueue.get(i, (void**)&messageID) );
        if ((found = messageID->equals(message))) break;
      }
      if (!found) {
        LOG_WARNING(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_NOT_FOUND,
                     "Session::acknowledgeMessages() message 0x%p not found in session 0x%p",
                     message, this ));
        if (lockedByMe) {
          MQError err = this->sessionMutex.unlock();
          if (err != MQ_SUCCESS) {
          LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                     "Session::acknowledgeMessages() unlock() returns '%s' (%d) for session 0x%p",
                     errorStr(errorCode), errorCode, this ));
          }
        }
        //should return a error
        return MQ_SUCCESS;
      }
    }
  }

  if (unAckedMessageQueue.size() == 0) {
    if (lockedByMe) {
      MQError err = this->sessionMutex.unlock();
      if (err != MQ_SUCCESS) {
        LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                   "Session::acknowledgeMessages() unlock() returns '%s' (%d) for session 0x%p",
                   errorStr(errorCode), errorCode, this ));
      }
    }
    return MQ_SUCCESS;
  }

  found = PR_FALSE;
  while (unAckedMessageQueue.size() != 0) {
    ERRCHK( unAckedMessageQueue.remove(0, (void**)&messageID ) );
    if (message != NULL) {
      found = messageID->equals(message);
    }
    ERRCHK( messageID->write(&ackBlockStream) );
    DELETE( messageID );
    cnt++;

    if (message != NULL && found)  break;

  } //while

  if (message != NULL) ASSERT( found );

  // Send the acknowledgement
  /*
  if (this->ackMode == SESSION_TRANSACTED) {
    Long transactionIDLong(this->transactionID);
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), MQ_SUCCESS, 
               "Session::acknowledgeMessages(): sending %d ACKs for session 0x%p, transactionID=%s",
                     cnt, this, transactionIDLong.toString() ));  
  } else {
    LOG_FINE(( CODELOC, SESSION_LOG_MASK, this->connection->id(), MQ_SUCCESS, 
               "Session::acknowledgeMessages(): sending %d ACKs for session 0x%p", cnt, this ));  
  }
  */
  if (cnt > 0) {
    ERRCHK( connection->acknowledge( this, ackBlockStream.getStreamBytes(),
                                   ackBlockStream.numBytesWritten() ));
  }
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::acknowledgeMessages() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }

  ackBlockStream.reset();
  return MQ_SUCCESS;

Cleanup:
  ackBlockStream.reset();
  DELETE( messageID );
  if (lockedByMe) {
    MQError err = this->sessionMutex.unlock();
    if (err != MQ_SUCCESS) {
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, err,
                "Session::acknowledgeMessages() unlock() returns '%s' (%d) for session 0x%p",
                 errorStr(errorCode), errorCode, this ));
    }
  }
  return errorCode;
}


MQError
Session::redeliverUnAckedMessages()
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = MQ_SUCCESS;
  MessageID * messageID = NULL;

  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
    if (PR_GetCurrentThread() == sessionThread) {
      ERRCHK( acknowledge(this->sessionQueueReader->getCurrentMessage(),
                          CLIENT_ACKNOWLEDGE, PR_TRUE) );
    }
  }
  if (unAckedMessageQueue.size() == 0) {
    return MQ_SUCCESS;
  }
 
  while (unAckedMessageQueue.size() != 0) {
    ERRCHK( unAckedMessageQueue.remove(0, (void**)&messageID ) );

    ERRCHK( messageID->write(&ackBlockStream) );
    DELETE( messageID );
  }

  ERRCHK( connection->redeliver(this, PR_TRUE, ackBlockStream.getStreamBytes(),
                                   ackBlockStream.numBytesWritten()) );
  ackBlockStream.reset();
  return MQ_SUCCESS;

Cleanup:
  ackBlockStream.reset();
  DELETE( messageID );
  return errorCode;
}


MQError
Session::startTransaction()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  PRBool succeed = PR_FALSE;

  while (succeed == PR_FALSE) {
    errorCode = connection->startTransaction(this->sessionID, &(this->transactionID));
    if (errorCode == MQ_SUCCESS) {
      Long transactionIDLong(transactionID);
      LOG_FINE(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                  "Session::start new transaction success for session 0x%p: new transactionID=%s",
                   this, transactionIDLong.toString() ));
 
      succeed = PR_TRUE;
    } else if (errorCode == MQ_TRANSACTION_ID_IN_USE) {
      LOG_FINEST(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                  "Session::startTransaction() failed for session 0x%p because '%s' (%d). retry ...",
                   this, errorStr(errorCode), errorCode  ));
    } else {
      break;
    }
  }

  return errorCode;
  
}

/*
 *
 */
const int TOTAL_MESSAGES_DURSUB = 20;
iMQError
Session::testDurableConsumer(Connection * const connection)
{
  iMQError errorCode = IMQ_SUCCESS;
  Session * subSession = NULL;
  Session * pubSession = NULL;
  MessageProducer * producer = NULL;
  MessageConsumer * consumer = NULL;
  Destination * pubDestination = NULL;
  Destination * subDestination = NULL;
  UTF8String destinationName("SomeDestination");
  UTF8String msgText("This is the message text");
  TextMessage * textMessage = NULL;
  UTF8String * receivedText = NULL;
  Message * receivedMessage = NULL;
  UTF8String durableName("johnSmith");
  int i = 0;

  NULLCHK( connection );

  // Create a producer and consumer session
  ERRCHK( connection->createSession(PR_FALSE, CLIENT_ACKNOWLEDGE, SESSION_SYNC_RECEIVE, PR_FALSE, NULL, NULL, NULL, &subSession) );
  ERRCHK( connection->createSession(PR_FALSE, CLIENT_ACKNOWLEDGE, SESSION_SYNC_RECEIVE, PR_FALSE, NULL, NULL, NULL, &pubSession) );

  // Create a destination for the producer and consumer
  ERRCHK( pubSession->createDestination(&destinationName, PR_FALSE, &pubDestination) );
  ERRCHK( subSession->createDestination(&destinationName, PR_FALSE, &subDestination) );

  // Create a producer and consumer
  //  ERRCHK( pubSession->createProducer(pubDestination, &producer) );
  ERRCHK( pubSession->createProducer(&producer) );
  ERRCHK( subSession->createConsumer(subDestination, PR_TRUE, PR_FALSE, &durableName, 
                                       NULL, PR_FALSE, NULL, NULL, &consumer) );
  ASSERT( consumer->getIsInitialized() );

  // Create a new message
  MEMCHK( textMessage = new TextMessage() );
  ERRCHK( textMessage->setMessageText(&msgText) );

  // Start the Connection
  ERRCHK( connection->start() );

  // Send the message TOTAL_MESSAGES_DURSUB times
  for( i = 0; i < TOTAL_MESSAGES_DURSUB; i++) {
    ERRCHK( producer->send(textMessage, pubDestination) );
  }

  // Receive TOTAL_MESSAGES messages and acknowledge the last one
  for( i = 0; i < TOTAL_MESSAGES_DURSUB; i++) {
    ERRCHK( consumer->receive(&receivedMessage) );

    ERRCHK( ((TextMessage*)receivedMessage)->getMessageText(&receivedText) );
   
    // Acknowledge the last one
    if (i == TOTAL_MESSAGES_DURSUB - 1) {
      ERRCHK( subSession->acknowledgeMessages(PR_TRUE, receivedMessage) );
    }

    DELETE( receivedText );
    HANDLED_DELETE( receivedMessage );
  }
  
  // Close the consumer
  ERRCHK( subSession->closeConsumer(consumer) );
  consumer = NULL;

  // Send the message TOTAL_MESSAGES_DURSUB more times
  for( i = 0; i < TOTAL_MESSAGES_DURSUB; i++) {
    ERRCHK( producer->send(textMessage, pubDestination) );
  }
  
  // Bring the durable consumer back up, and receive the rest of the messages
  ERRCHK( subSession->createDestination(&destinationName, PR_FALSE, &subDestination) );
  ERRCHK( subSession->createConsumer(subDestination, PR_TRUE, PR_FALSE, &durableName, 
                                       NULL, PR_FALSE, NULL, NULL, &consumer) );
  ASSERT( consumer->getIsInitialized() );
  // Receive TOTAL_MESSAGES_DURSUB messages and acknowledge the last one
  for( i = 0; i < TOTAL_MESSAGES_DURSUB; i++) {
    ERRCHK( consumer->receive(&receivedMessage) );
    
    ERRCHK( ((TextMessage*)receivedMessage)->getMessageText(&receivedText) );
   
    // Acknowledge the last one
    if (i == TOTAL_MESSAGES_DURSUB - 1) {
      ERRCHK( subSession->acknowledgeMessages(PR_TRUE, receivedMessage) );
    }

    DELETE( receivedText );
    HANDLED_DELETE( receivedMessage );
  }

  // Close the producer
  ERRCHK( pubSession->closeProducer(producer) );
  producer = NULL;   // deleted by closeProducer

  // Close the consumer and unregister the name
  ERRCHK( subSession->closeConsumer(consumer) );
  consumer = NULL;  // deleted by closeConsumer
  ERRCHK( subSession->unsubscribeDurableConsumer(&durableName) );

  // Close and delete the sessions
  ERRCHK( pubSession->close(PR_TRUE) );
  ERRCHK( subSession->close(PR_TRUE) );
  HANDLED_DELETE( pubSession );
  HANDLED_DELETE( subSession );

  // Delete the destinations and the text message
  HANDLED_DELETE( pubDestination );
  HANDLED_DELETE( subDestination );
  HANDLED_DELETE( textMessage );

  return IMQ_SUCCESS;
Cleanup:
  // Close the producer and its session
  if (pubSession != NULL) {
    if ((producer != NULL) && (pubSession->closeProducer(producer) != IMQ_SUCCESS)) {
      HANDLED_DELETE( producer );
    }
    pubSession->close(PR_TRUE);
    HANDLED_DELETE( pubSession );
  }

  // Close the consumer and its session
  if (subSession != NULL) {
    if ((consumer != NULL) && (subSession->closeConsumer(consumer) != IMQ_SUCCESS)) {
      HANDLED_DELETE( consumer );
    } else {
      subSession->unsubscribeDurableConsumer(&durableName);
    }
    subSession->close(PR_TRUE);
    HANDLED_DELETE( subSession );
  }

  // Delete the destinations and the text message
  HANDLED_DELETE( pubDestination );
  HANDLED_DELETE( subDestination );
  HANDLED_DELETE( textMessage );
  
  return errorCode;
}

/*
 *
 */
const int TOTAL_MESSAGES = 1000;
iMQError
Session::test(Session * const session)
{
  iMQError errorCode = IMQ_SUCCESS;
  UTF8String destinationName("CurrentTimeDestination");
  UTF8String timeMsgText( 
    // 000-999
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    "123456789 123456789 123456789 123456789 123456789 "
    );
  Destination * subDestination = NULL;
  Destination * pubDestination = NULL;
  Destination * temporaryDestination = NULL;
  const Destination * replyToDestination = NULL;
  MessageProducer * producer = NULL;
  TextMessage * timeMessage = NULL;
  Message * receivedMessage = NULL;
  UTF8String * receivedText = NULL;
  MessageConsumer * consumer = NULL;
  int i = 0;

  NULLCHK( session );

  // Create a destination and a temporary destination
  ERRCHK( session->createDestination(&destinationName, PR_FALSE, &pubDestination) );
  ERRCHK( session->createDestination(&destinationName, PR_FALSE, &subDestination) );
  ERRCHK( session->createTemporaryDestination(PR_FALSE, &temporaryDestination) );

  // Create a producer and consumer
  //ERRCHK( session->createProducer(pubDestination, &producer) );
  ERRCHK( session->createProducer(pubDestination, &producer) );
  ERRCHK( session->createConsumer(subDestination, PR_FALSE, PR_FALSE, NULL, 
                                 NULL, PR_FALSE, NULL, NULL, &consumer) );
  ASSERT( consumer->getIsInitialized() );

  producer->setDeliveryMode(NON_PERSISTENT_DELIVERY);

  // Create a new message
  MEMCHK( timeMessage = new TextMessage() );
  ERRCHK( timeMessage->setMessageText(&timeMsgText) );
  ERRCHK( timeMessage->setJMSReplyTo(temporaryDestination) );

  // Start the Connection
  ERRCHK( session->getConnection()->start() );

  // Send the message TOTAL_MESSAGES times
  for (i = 0; i < TOTAL_MESSAGES; i++) {
    LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                 "Session::test calling producer->send()" ));
    ERRCHK( producer->send(timeMessage) );
  }

//#if 0
  // Receive TOTAL_MESSAGES messages and acknowledge the last one
  for (i = 0; i < TOTAL_MESSAGES; i++) {
    LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                 "Session::test calling consumer->receive()" ));
    ERRCHK( consumer->receive(&receivedMessage) );

    ERRCHK( ((TextMessage*)receivedMessage)->getMessageText(&receivedText) );
    ERRCHK( receivedMessage->getJMSReplyTo(&replyToDestination) );
   
    // Acknowledge the last one
    if (i == TOTAL_MESSAGES - 1) {
      LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                   "Session::test calling consumer->acknowledge()" ));
      ERRCHK( session->acknowledgeMessages(PR_TRUE, receivedMessage) );
    }

    DELETE( receivedText );
    HANDLED_DELETE( receivedMessage );
  }
//#endif

  // Cleanup
  LOG_FINEST(( CODELOC, CONSUMER_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "Session::test calling session->close()" ));
  session->close(PR_FALSE);

  HANDLED_DELETE( timeMessage );
  return IMQ_SUCCESS;

Cleanup:
  LOG_WARNING(( CODELOC, SESSION_LOG_MASK, NULL_CONN_ID, 
                IMQ_SUCCESS, 
                "Session::test() failed "
                "because '%s' (%d).", 
                errorStr(errorCode), errorCode ));

  if (session != NULL) {
    session->close(PR_FALSE);
  }
  DELETE( receivedText );
  HANDLED_DELETE( timeMessage );
  HANDLED_DELETE( receivedMessage );

  return errorCode;
}
