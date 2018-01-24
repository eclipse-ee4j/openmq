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
 * @(#)ProducerFlow.cpp	1.9 06/26/07
 */ 

#include "ProducerFlow.hpp"
#include "../util/UtilityMacros.h"
#include "Message.hpp"


ProducerFlow::ProducerFlow()
{
  CHECK_OBJECT_VALIDITY();

  this->producerID = 0;
  this->producerIDLong.setValue(this->producerID);
  this->chunkBytes = -1;
  this->chunkSize = -1;
  this->sentCount = 0;

  this->references = 0;

  this->isClosed = PR_FALSE;
  this->closeReason = MQ_SUCCESS;

}


ProducerFlow::~ProducerFlow()
{
  CHECK_OBJECT_VALIDITY();
  this->close(this->closeReason);
}

void
ProducerFlow::setProducerID(PRInt64 producerIDArg)
{
  CHECK_OBJECT_VALIDITY();
  this->producerID = producerIDArg;
  this->producerIDLong.setValue(this->producerID);
}


PRInt64
ProducerFlow::getProducerID() const
{
  CHECK_OBJECT_VALIDITY();
  return this->producerID;
}



void
ProducerFlow::setChunkSize(PRInt32 chunkSizeArg) 
{
  CHECK_OBJECT_VALIDITY();
  this->chunkSize = chunkSizeArg;
}

void
ProducerFlow::setChunkBytes(PRInt64 chunkBytesArg) 
{
  CHECK_OBJECT_VALIDITY();
  this->chunkBytes = chunkBytesArg;
}

MQError
ProducerFlow::checkFlowControl(Message *message) 
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Packet * packet = NULL;
  FlowState flowState = UNDER_LIMIT;

  NULLCHK( message );
  LOG_FINEST(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "Entering ProducerFlow::checkFlowControl(producerID=%s)",
                  producerIDLong.toString()));

  monitor.enter();

  while (this->isClosed == PR_FALSE && (flowState = checkFlowLimit()) == OVER_LIMIT) {
  LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "ProducerFlow::checkFlowControl(producerID=%s, chunckSize=%d, sentCount=%d) calling wait()",
                 producerIDLong.toString(), chunkSize, sentCount ));

  monitor.wait();
  LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                "ProducerFlow::checkFlowControl(producerID=%s) wokeup from wait()",
                 producerIDLong.toString() ));
    
  }
  if (this->isClosed) {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_PRODUCER_CLOSED,
               "ProducerFlow::checkFlowControl(producerID=%s) wokeup from wait() by close",
               producerIDLong.toString() ));
    monitor.exit();
    return this->closeReason;
  }

  packet = message->getPacket();
  packet->setProducerID(this->producerID);
  packet->setConsumerFlow((flowState == ON_LIMIT));
  if (flowState == ON_LIMIT) {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
         "ProducerFlow::checkFlowControl(producerID=%s, sentCount=%d) sending last message %s",
          producerIDLong.toString(), 
          sentCount,
          ((SysMessageID *)message->getSystemMessageID())->toString() ));
  }
  sentCount++;
  monitor.exit();

Cleanup:
  return errorCode;
}

/*
 * only called from ReadChannel thread
 */
void
ProducerFlow::resumeFlow(PRInt64 chunkBytesArg, PRInt32 chunkSizeArg)
{
  CHECK_OBJECT_VALIDITY();
  Long oldchunkBytesLong(this->chunkBytes);
  Long newchunkBytesLong(chunkBytesArg);

  LOG_FINEST(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
   "ProducerFlow::resumeFlow(producerID=%s) to chunkBytes=%s, chunkSize=%d from chunkBytes=%s, chunkSize=%d",
    producerIDLong.toString(),
    newchunkBytesLong.toString(), chunkSizeArg,
    oldchunkBytesLong.toString(), this->chunkSize ));

  monitor.enter();
  this->chunkBytes = chunkBytesArg;
  this->chunkSize = chunkSizeArg;
  sentCount = 0;
  monitor.notifyAll();
  monitor.exit();
  
}

/*
 * only called by sender thread while in monitor
 */
FlowState
ProducerFlow::checkFlowLimit()
{
  CHECK_OBJECT_VALIDITY();

  if (chunkSize < 0) return UNDER_LIMIT;
  if (sentCount >= chunkSize) return OVER_LIMIT;
  if (sentCount == chunkSize-1) return ON_LIMIT;
  return UNDER_LIMIT;
}


/**
 * acquireReference/releaseReference are or should only be called from
 * Connection.getProducerFlow/releaseProducerFlow methods under the same
 * Monitor in Connection for calls ProducerFlow.close() and ~ProducerFow().
 * Therefore these two methods are safe not using this->monitor
 */
MQError
ProducerFlow::acquireReference()
{
  if (this->isClosed == PR_TRUE) {
    return this->closeReason;
  }

  ASSERT( references >= 0 );
  references++;

  return MQ_SUCCESS;
}

PRBool
ProducerFlow::releaseReference()
{
  ASSERT( references > 0 );
  references--;
  if (references == 0 && isClosed == PR_TRUE && closeReason == MQ_PRODUCER_CLOSED) {
    return PR_TRUE;
  }
  return PR_FALSE;
}


void
ProducerFlow::close(MQError reason)
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();
  this->isClosed = PR_TRUE;
  this->closeReason = reason;
  monitor.notifyAll();
  monitor.exit();

}
