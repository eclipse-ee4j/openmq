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
 * @(#)SessionQueueReader.cpp	1.16 06/26/07
 */ 

#include "SessionQueueReader.hpp"
#include "Connection.hpp"
#include "Session.hpp"
#include "../io/Packet.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"

/*
 *
 */
SessionQueueReader::SessionQueueReader(Session * const sessionArg)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  this->init();
  NULLCHK( sessionArg );
  this->session = sessionArg;
  this->sessionQueue = sessionArg->getSessionQueue();
  NULLCHK( this->sessionQueue );
  
  this->connection = sessionArg->getConnection();
  NULLCHK( this->connection );
  this->connectionID = this->connection->id();

  monitor.enter();
  errorCode = this->connection->startThread(this);
  if (errorCode == MQ_SUCCESS) {
    this->isAlive = PR_TRUE;
    while(this->readerThread == NULL) {
      monitor.wait();
    }
  }
  monitor.exit();

Cleanup:

  this->initializationError = errorCode;

}

/*
 *
 */
SessionQueueReader::~SessionQueueReader()
{
  CHECK_OBJECT_VALIDITY();
  LOG_FINE(( CODELOC, SESSION_READER_LOG_MASK, this->connectionID, MQ_SUCCESS,
             "SessionQueueReader::~SessionQueueReader() called" ));

  this->init();
}

/*
 *
 */
MQError
SessionQueueReader::getInitializationError() const
{
  CHECK_OBJECT_VALIDITY();

  return this->initializationError;
}

/*
 *
 */
void
SessionQueueReader::init()
{
  CHECK_OBJECT_VALIDITY();

  this->session              = NULL;
  this->sessionQueue         = NULL;
  this->connection           = NULL;
  this->initializationError  = MQ_SUCCESS;
  this->isAlive              = PR_FALSE;
  this->isClosed             = PR_FALSE;
  this->readerThread         = NULL;
  this->currentMessage       = NULL;
}


/*
 *
 */
void
SessionQueueReader::run()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  readerThread = PR_GetCurrentThread();

  monitor.enter();
  monitor.notifyAll();
  monitor.exit();
  LOG_FINE(( CODELOC, SESSION_READER_LOG_MASK, this->connectionID, MQ_SUCCESS,
             "SessionQueueReader::run() starting;  isAlive=%d, isClosed=%d ",
             this->isAlive, this->isClosed ));

  while (this->isClosed != PR_TRUE) {
    Packet * packet = NULL;
    
    LOG_FINEST(( CODELOC, SESSION_READER_LOG_MASK, this->connectionID, 
                 MQ_SUCCESS, "SessionQueueReader::run() trying to read a packet." ));
    packet =  (Packet *)(this->sessionQueue)->dequeueWait();
    if (this->connection->getIsClosed()) {
      errorCode = MQ_BROKER_CONNECTION_CLOSED;
      break;
    }
    if (packet == NULL) { 
      sessionQueue->receiveDone();
      continue; 
    }
    errorCode = deliver(packet);
    this->session->messageDelivered();
    sessionQueue->receiveDone();
    if (errorCode != MQ_SUCCESS) {//XXX
      if (this->connection->getIsClosed()) {
        errorCode = MQ_BROKER_CONNECTION_CLOSED;
        break;
      }
    }

  }

  LOG_FINE(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
             "SessionQueueReader::run() exiting because '%s' (%d) ",
             errorStr(errorCode), errorCode ));

  CLEAR_ERROR_TRACE(PR_TRUE);

  monitor.enter();
  this->isAlive = PR_FALSE;
  monitor.notifyAll();
  monitor.exit();
  return;
}


/*
 *
 */
MQError
SessionQueueReader::deliver(Packet * packet)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  MessageConsumer * consumer = NULL;
  PRUint64 consumerID = 0;
  PRBool messageListenerInvoked = PR_FALSE;

  currentMessage = NULL;
  MEMCHK( currentMessage = Message::createMessage(packet) );
  packet = NULL;  // message owns it now
  ERRCHK( (currentMessage)->getInitializationError() );

  LOG_FINEST(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "SessionQueueReader::receive allocated new message 0x%p",
                currentMessage ));

  consumerID = currentMessage->getConsumerID();
  ERRCHK( this->session->getConsumer(consumerID, &consumer) );
  if (consumer == NULL) {
    Long consumerIDLong(consumerID);
    LOG_FINE(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, MQ_CONSUMER_NOT_IN_SESSION,
               "SessionQueueReader::consumer %s not found in session 0x%p for message 0x%p",
                consumerIDLong.toString(), this->session, currentMessage ));
    
    ERRCHK( MQ_CONSUMER_NOT_IN_SESSION ); 
  } else {
    HandledObject * object = HandledObject::acquireExternalReference(consumer->getHandle());
    if (object == NULL) {
      Long consumerIDLong(consumerID);
      LOG_WARNING(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, MQ_CONSUMER_CLOSED,
      "Unable to deliver message 0x%p for consumer %s in session 0x%p has been closed",
               currentMessage, consumerIDLong.toString(), this->session ));
      ERRCHK( MQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR );
    }
  }

  currentMessage->setIsExported(PR_TRUE);
  {
  HandledObject * object = HandledObject::acquireExternalReference(currentMessage->getHandle());
  ASSERT( object == currentMessage );
  ASSERT( object->getObjectType() == currentMessage->getObjectType() );
  ASSERT( object->getIsExported() == PR_TRUE );
  }
  currentMessage->setCheckDeletedExternally();
  errorCode = consumer->onMessage(currentMessage, &messageListenerInvoked);
  {
  MQError error = HandledObject::releaseExternalReference(consumer);
  ASSERT( error == MQ_SUCCESS );
  ERRCHK( error );
  if (!messageListenerInvoked) { 
     MQError error = HandledObject::externallyDelete(currentMessage->getHandle());
     ASSERT( error == MQ_SUCCESS );
     ERRCHK( error );
  } 
  error = HandledObject::releaseExternalReference(currentMessage);
  ASSERT( error == MQ_SUCCESS );
  ERRCHK( error );
  }

  if (errorCode == MQ_SUCCESS) {
      LOG_FINEST(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                  "SessionQueueReader:: sucessfully delivered message 0x%p",
                  currentMessage ));
      return MQ_SUCCESS;
  } else {
    if (messageListenerInvoked) {
    LOG_FINE(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, errorCode,
               "SessionQueueReader:: delivering message 0x%p got error '%s' (%d) from message listener", 
               currentMessage, errorStr(errorCode), errorCode ));
    } else {
    LOG_WARNING(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, errorCode,
               "SessionQueueReader:: delivering message 0x%p to consumer got error '%s' (%d)", 
               currentMessage, errorStr(errorCode), errorCode ));
    }
  }
  return errorCode;

Cleanup:
  if (currentMessage == NULL) {
    LOG_SEVERE(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, errorCode,
                   "SessionQueueReader:: unable to create message because '%s' (%d)", 
                   errorStr(errorCode), errorCode ));
  } else {
    LOG_WARNING(( CODELOC, SESSION_READER_LOG_MASK, NULL_CONN_ID, errorCode,
               "SessionQueueReader:: delivering message 0x%p got error '%s' (%d)", 
                currentMessage, errorStr(errorCode), errorCode ));
  }
  DELETE( packet );
  HANDLED_DELETE( currentMessage );
    
  return errorCode;
}

PRThread *
SessionQueueReader::getReaderThread() const
{
  CHECK_OBJECT_VALIDITY();
  return this->readerThread;
}

Message *
SessionQueueReader::getCurrentMessage() const
{
  CHECK_OBJECT_VALIDITY();
  return this->currentMessage;
}


void
SessionQueueReader::close() 
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();

  this->isClosed = PR_TRUE;
  this->sessionQueue->close();

  while (this->isAlive == PR_TRUE) {
        monitor.wait();
  }
  monitor.exit();

}

