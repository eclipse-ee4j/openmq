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
 * @(#)XASession.cpp	1.2 10/23/07
 */ 

#include "XASession.hpp"
#include "Connection.hpp"

/*
 *
 */
XASession::XASession(Connection * const connectionArg,
                     const ReceiveMode  receiveModeArg,
                     MQMessageListenerBAFunc beforeMessageListenerArg, 
                     MQMessageListenerBAFunc afterMessageListenerArg, 
                     void * callbackDataArg
                     ) : Session(connectionArg, PR_FALSE, AUTO_ACKNOWLEDGE, receiveModeArg) 
{
  CHECK_OBJECT_VALIDITY();

  ASSERT (connectionArg->getIsXA() == PR_TRUE ); 
  this->isXA = PR_TRUE; 
  this->ackMode = SESSION_TRANSACTED; 
  this->transactionID = LL_Zero();
  this->xidIndex = mq_getXidIndex();
  this->beforeMessageListener = NULL;
  this->afterMessageListener = NULL;
  this->baMLCallbackData = NULL;
  if (this->receiveMode == SESSION_ASYNC_RECEIVE) {
      this->beforeMessageListener = beforeMessageListenerArg;
      this->afterMessageListener = afterMessageListenerArg;
      this->baMLCallbackData = callbackDataArg;
  }
  LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, connectionArg->id(), MQ_SUCCESS,
             "XASession (0x%p) created.", this ));
  return;
}


MQMessageListenerBAFunc
XASession::getBeforeMessageListenerFunc()
{
  CHECK_OBJECT_VALIDITY();
  return this->beforeMessageListener;
  
}


MQMessageListenerBAFunc
XASession::getAfterMessageListenerFunc()
{
  CHECK_OBJECT_VALIDITY();
  return this->afterMessageListener;
}


void *
XASession::getMessageListenerBACallbackData()
{
  CHECK_OBJECT_VALIDITY();
  return this->baMLCallbackData;
}


MQError
XASession::writeJMSMessage(Message * const message, PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();

  if (this->isClosed) return MQ_SESSION_CLOSED;

  if (this->isXA == PR_TRUE) {
    MQXID *xid = (MQXID *) PR_GetThreadPrivate(this->xidIndex);
    if (xid == NULL) { 
      return MQ_THREAD_OUTSIDE_XA_TRANSACTION;
    }
    if (LL_IS_ZERO(xid->transactionID) != 0) {
      return MQ_XA_SESSION_NO_TRANSATION;
    }
    this->transactionID = xid->transactionID;
    {
      Long transactionIDLong(this->transactionID);
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, this->connection->id(), MQ_SUCCESS,
                "XASession::writeJMSMessage with transactionID=%s, in XASession (0x%p)",
                 transactionIDLong.toString(), this ));
    }
  }
  return Session::writeJMSMessage(message, producerID);
}

MQError
XASession::acknowledge(Message * message, PRBool fromMessageListener)
{
  CHECK_OBJECT_VALIDITY();

  if (this->isClosed)  return MQ_SESSION_CLOSED;

  if (this->isXA == PR_TRUE) {
    MQXID *xid = (MQXID *) PR_GetThreadPrivate(this->xidIndex);
    if (xid == NULL) {
      return MQ_THREAD_OUTSIDE_XA_TRANSACTION;
    }
    if (LL_IS_ZERO(xid->transactionID) != 0) {
      return MQ_XA_SESSION_NO_TRANSATION;
    }
    this->transactionID = xid->transactionID;
    {
      Long transactionIDLong(this->transactionID);
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, this->connection->id(), MQ_SUCCESS,
                "XASession::acknowledge with transactionID=%s, in XASession (0x%p)",
                 transactionIDLong.toString(), this ));
    }
  }

  return Session::acknowledge(message, fromMessageListener);
}


/*
 *
 */
XASession::~XASession()
{
  CHECK_OBJECT_VALIDITY();
}

