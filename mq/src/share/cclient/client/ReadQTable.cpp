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
 * @(#)ReadQTable.cpp	1.6 06/26/07
 */ 

#include "ReadQTable.hpp"
#include "../basictypes/Long.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"
#include "../io/Packet.hpp"

static const PRInt64 MINIMUM_ID_VALUE = LL_MinInt();
static const PRInt64 MAXIMUM_ID_VALUE = LL_MaxInt();

/*
 *
 */
ReadQTable::ReadQTable()
{
  CHECK_OBJECT_VALIDITY();

  PRBool autoDeleteKey, autoDeleteValue;
  this->table = new BasicTypeHashtable(autoDeleteKey=PR_TRUE, 
                                       autoDeleteValue=PR_FALSE); 
  this->nextID = MINIMUM_ID_VALUE;
}


/*
 *
 */
ReadQTable::~ReadQTable()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( this->table );
}

/*
 *
 */
MQError
ReadQTable::remove(const PRInt64 consumerIDArg)
{
  CHECK_OBJECT_VALIDITY();
  
  MQError errorCode = MQ_SUCCESS;
  Long consumerIDLong(consumerIDArg);

  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);  

  monitor.enter();
  errorCode = this->table->removeEntry(&consumerIDLong);
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
               "Failed to remove comsumerID=%lld from the ReadQTable 0x%p because '%s' (%d)", 
               consumerIDArg, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

/*
 *
 */
MQError 
ReadQTable::add(const PRInt64 consumerIDArg, ReceiveQueue * const receiveQ)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  ReceiveQueue * prevQ = NULL;
  Long * consumerIDLong = new Long(consumerIDArg);

  RETURN_ERROR_IF_NULL(receiveQ);

  // in case the 'new' in the constructor failed
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  RETURN_ERROR_IF(consumerIDLong == NULL, MQ_OUT_OF_MEMORY);

  monitor.enter();
  errorCode = this->table->getValueFromKey(consumerIDLong, (const Object** const)&prevQ);
  if (errorCode == MQ_SUCCESS) {
    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, MQ_READQTABLE_ERROR,
               "ReadQTable:comsumerID=%lld exists in ReadQTable 0x%p", consumerIDArg, this ));
    errorCode = MQ_REUSED_CONSUMER_ID;
  } else if (errorCode == MQ_NOT_FOUND) {
    errorCode = this->table->addEntry(consumerIDLong, receiveQ);
  }
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    DELETE( consumerIDLong );

    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode, 
               "Failed to add comsumerID=%lld to ReadQTable 0x%p because '%s' (%d)",
                consumerIDArg, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

/*
 *
 */
MQError 
ReadQTable::add(PRInt64 * consumerIDArg, ReceiveQueue * const receiveQ)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  ReceiveQueue * prevQ = NULL;
  Long * consumerIDLong = NULL;

  RETURN_ERROR_IF_NULL(receiveQ);
 
  // in case the 'new' in the constructor failed
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  monitor.enter();
  do {
    this->getNextID(consumerIDArg);
    consumerIDLong = new Long(*consumerIDArg);
    if ( consumerIDLong == NULL )  {
      errorCode = MQ_OUT_OF_MEMORY;
    } else {
      errorCode = this->table->getValueFromKey(consumerIDLong, (const Object** const)&prevQ);
      if (errorCode == MQ_SUCCESS) {
        DELETE ( consumerIDLong );
        LOG_FINER(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, MQ_READQTABLE_ERROR,
                   "ReadQTable:comsumerID=%lld exists in ReadQTable 0x%p", *consumerIDArg, this ));
      }
    }
  } while ( errorCode == MQ_SUCCESS );

  if (errorCode == MQ_NOT_FOUND) {
    ASSERT( consumerIDLong != NULL );
    errorCode = this->table->addEntry(consumerIDLong, receiveQ);
  }
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    DELETE( consumerIDLong );

    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
               "Failed to add comsumerID=%lld to ReadQTable 0x%p because '%s' (%d)",
                consumerIDArg, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


/*
 *
 */
MQError
ReadQTable::get(const PRInt64 consumerID, ReceiveQueue ** const receiveQ)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Long consumerIDLong(consumerID);

  RETURN_ERROR_IF_NULL(receiveQ);

  // in case the 'new' in the constructor failed
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  monitor.enter();
  // Without synchronization or error handling this line is all we
  // would need to do
  errorCode = this->table->getValueFromKey(&consumerIDLong, (const Object** const)receiveQ);
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
               "Failed to get comsumerID=%lld from ReadQTable 0x%p because '%s' (%d)",
                consumerID, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

MQError
ReadQTable::enqueue(const PRInt64 consumerIDArg, Packet * const packet)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  ReceiveQueue * receiveQ = NULL;
  Long consumerIDLong(consumerIDArg);

  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  monitor.enter();
  errorCode = this->table->getValueFromKey(&consumerIDLong, (const Object** const)&receiveQ);
  if (errorCode == MQ_SUCCESS) {
    errorCode = receiveQ->enqueueNotify(packet);
  } else {
    LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
     "Failed to get ReceiveQ for comsumerID=%lld from ReadQTable 0x%p because '%s' (%d)",
      consumerIDArg, this, errorStr(errorCode), errorCode ));
  }
  monitor.exit();

  return errorCode;
}


/*
 *
 */
MQError  
ReadQTable::closeAll()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Long * consumerIDLong = NULL;
  ReceiveQueue * receiveQ = NULL;

  // in case the 'new' in the constructor failed
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  LOG_FINEST(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "In closeAll for ReadQTable %p", this));

  monitor.enter();
  errorCode = this->table->keyIterationStart();
  if (errorCode == MQ_SUCCESS) {

  while (this->table->keyIterationHasNext()) {
    errorCode = this->table->keyIterationGetNext((const BasicType**)&consumerIDLong);
    if (errorCode == MQ_SUCCESS) {
       errorCode = this->table->getValueFromKey(consumerIDLong, (const Object** const)&receiveQ);
       if (errorCode == MQ_SUCCESS) {
         LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
             "Closing receiveQ=0x%p in ReadQTable 0x%p", receiveQ, this ));

         receiveQ->close();

       } else {
         LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
            "Failed to get receiveQ for consumerID=%s in ReadQTable 0x%p for closing because '%s' (%d)",
            consumerIDLong->toString(), this, errorStr(errorCode), errorCode ));
       }
    } else {
      LOG_FINE(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
        "Failed to get next consumerID in ReadQTable 0x%p for closing because '%s' (%d)",
        this, errorStr(errorCode), errorCode ));
    }
  } //while

  } else { 
    LOG_INFO(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
          "Failed to start iterating ReadQTable 0x%p for closing  because '%s' (%d)",
          this, errorStr(errorCode), errorCode ));
  }
  monitor.exit();

  LOG_FINEST(( CODELOC, READQTABLE_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "Exiting closeAll for ReadQTable %p", this));

  return errorCode;
}


void
ReadQTable::getNextID(PRInt64 * const id)
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( id != NULL );
  LL_ADD(this->nextID, this->nextID, (PRInt64)1);
  if (LL_CMP(this->nextID, >, MAXIMUM_ID_VALUE) != 0) {
    this->nextID = MINIMUM_ID_VALUE;
  }
  *id = this->nextID;

  return;
}


static const int NUM_QUEUES = 100;
MQError
ReadQTable::test()
{
  MQError      errorCode = MQ_SUCCESS;
  ReadQTable    table;
  ReceiveQueue  * queues[NUM_QUEUES];
  PRInt64       ids[NUM_QUEUES];

  ReceiveQueue * q = NULL;

  int i;
  for (i = 0; i < NUM_QUEUES; i++) {
    queues[i] = NULL;
  }

  for (i = 0; i < NUM_QUEUES; i++) {
    MEMCHK( queues[i] = new ReceiveQueue() );
    ids[i] = i * 3;
    ERRCHK( table.add(ids[i], queues[i]) );
    ERRCHK( table.get(ids[i], &q) );

    ASSERT( q == queues[i] );
  }

  for (i = 0; i < NUM_QUEUES; i++) {
    ERRCHK( table.get(ids[i], &q) );
    ASSERT( q == queues[i] );
  }

  for (i = 0; i < NUM_QUEUES; i++) {
    ERRCHK( table.remove(ids[i]) );
    ASSERT( table.get(ids[i], &q) != MQ_SUCCESS );
    DELETE(queues[i]);
  }

  return MQ_SUCCESS;
Cleanup:
  for (i = 0; i < NUM_QUEUES; i++) {
    DELETE( queues[i] );
  }
  
  return errorCode;
}







