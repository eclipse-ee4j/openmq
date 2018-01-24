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
 * @(#)PingTimer.cpp	1.7 06/26/07
 */ 

#include "PingTimer.hpp"
#include "Connection.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"

/*
 * When the C client supports consumer-based flowcontrol which would need
 * a thread, then this pinging work should be done by that thread so that   
 * to reduce the number of threads in the client runtime.
 *
 */
PingTimer::PingTimer(Connection * const connectionArg)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  this->init();
  ASSERT( connectionArg != NULL );
  this->connection = connectionArg;
  NULLCHK( this->connection );

  this->connectionID = this->connection->id();

  ASSERT( connectionArg->getPingIntervalSec() > 0 ); 
  this->pingInterval = microSecondToIntervalTimeout(connectionArg->getPingIntervalSec()*1000*1000);

  monitor.enter();
  errorCode = this->connection->startThread(this);
  if (errorCode == MQ_SUCCESS) {
    this->isAlive = PR_TRUE;
  }
  monitor.exit();

Cleanup:
  this->initializationError = errorCode;
}

/*
 *
 */
PingTimer::~PingTimer()
{
  CHECK_OBJECT_VALIDITY();
  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
             "PingTimer::~PingTimer() called" ));

  ASSERT( this->exit );
  
  this->init();
}

/*
 * Connection::openConnection calls this method to make sure that the
 * constructor was able to successfully create the reader thread.
 */
MQError
PingTimer::getInitializationError() const
{
  return this->initializationError;
}

/*
 *
 */
void
PingTimer::init()
{
  CHECK_OBJECT_VALIDITY();

  this->connection           = NULL;
  this->connectionID         = NULL_CONN_ID;
  this->pingInterval         = PR_INTERVAL_NO_TIMEOUT;
  this->isAlive              = PR_FALSE;
  this->exit                 = PR_FALSE;
  this->initializationError  = MQ_SUCCESS; 
  this->pingThread = NULL;
}


/*
 *
 */
void
PingTimer::run()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  this->pingThread = PR_GetCurrentThread();

  monitor.enter();
  if (this->connection == NULL) {
    this->isAlive = PR_FALSE;
    monitor.notifyAll();
    monitor.exit();
    return;
  }
  monitor.exit();

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
             "PingTimer:: started;  isAlive=%d "
             "exit=%d ", this->isAlive, this->exit ));

   monitor.enter();

   while(!this->exit) {

     LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
                "PingTimer calling wait() ..." ));

     monitor.wait(this->pingInterval);

     LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
                "PingTimer wakeup" ));

     if (this->exit == PR_FALSE) {
       errorCode = connection->ping(); //error is logged in ProtocolHandler
     }

   } //while

  CLEAR_ERROR_TRACE(PR_TRUE);

  this->isAlive = PR_FALSE;
  monitor.notifyAll();
  monitor.exit();
  return;
}

void
PingTimer::terminate()
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();
  this->exit = PR_TRUE;
  monitor.notifyAll();
  while(this->isAlive) {
    LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
              "PingTimer::terminate() waiting for the ping thread to finish .."
             "this->exit=%d", this->exit ));
    monitor.wait();
  }
  monitor.exit();

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connectionID, MQ_SUCCESS,
             "PingTimer::terminate() return;  isAlive=%d "
             "exit=%d ", this->isAlive, this->exit ));
}

