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
 * @(#)SessionMutex.cpp	1.3 06/26/07
 */ 

#include "SessionMutex.hpp"

/*
 *
 */ 
SessionMutex::SessionMutex()
{
  this->owner = NULL; 
}

/*
 *
 */
SessionMutex::~SessionMutex()
{
  this->owner = NULL;
}


/*
 * Call unlock() unless if lockedByMe returns true
 */
MQError
SessionMutex:: trylock(PRBool * lockedByMe) 
{
  return trylock(PR_GetCurrentThread(), lockedByMe);
}


MQError
SessionMutex:: trylock(PRThread * me, PRBool * lockedByMe)
{
  *lockedByMe = PR_FALSE;

  monitor.enter();
  if (owner == NULL) {
    owner = me;
    monitor.exit();
    *lockedByMe = PR_TRUE;
    return MQ_SUCCESS;
  }
  if (owner != me) {
    monitor.exit();
    return MQ_CONCURRENT_ACCESS;
  }

  monitor.exit();

  return MQ_SUCCESS;
}


MQError
SessionMutex::unlock() 
{
  return unlock(PR_GetCurrentThread());
}

MQError
SessionMutex::unlock(PRThread * me) 
{
  monitor.enter();
  if (owner == me) {
    owner = NULL;
    monitor.notifyAll();
    monitor.exit();
    return MQ_SUCCESS;
  }

  monitor.exit();
  return MQ_CONCURRENT_NOT_OWNER;
}


/*
 * Call unlock() unless if lockedByMe returns true
 */
MQError
SessionMutex::lock(PRUint32 timeoutMicroSeconds, PRBool * lockedByMe)
{

  MQError errorCode = MQ_SUCCESS;

  PRIntervalTime intervalBefore = PR_INTERVAL_NO_WAIT;
  PRIntervalTime intervalAfter = PR_INTERVAL_NO_WAIT;
  PRIntervalTime intervalWaited = PR_INTERVAL_NO_WAIT;
  PRIntervalTime newTimeout = PR_INTERVAL_NO_WAIT;
  PRBool firstTimeout = PR_TRUE;

  PRIntervalTime timeout = PR_INTERVAL_NO_WAIT;
  PRThread * me = PR_GetCurrentThread();

  if ((timeoutMicroSeconds == PR_INTERVAL_NO_WAIT) ||
      (timeoutMicroSeconds == PR_INTERVAL_NO_TIMEOUT))
  {
    timeout = timeoutMicroSeconds;
  } else {
    timeout = PR_MicrosecondsToInterval(timeoutMicroSeconds);
  }

  newTimeout = timeout;
  *lockedByMe = PR_FALSE;

  monitor.enter();

  while (owner != NULL && owner != me) {

    if (timeout == PR_INTERVAL_NO_WAIT) {
      errorCode = MQ_CONCURRENT_ACCESS; 
      break;
    }

    if (timeout != PR_INTERVAL_NO_TIMEOUT && firstTimeout == PR_FALSE) {
      if ((intervalAfter - intervalBefore) > 0) {
        intervalWaited = intervalAfter - intervalBefore;
      } else {
        intervalWaited =  (PR_INTERVAL_NO_TIMEOUT - intervalBefore) + intervalAfter;
      }
      if (intervalWaited >= newTimeout)  {
        errorCode = MQ_TIMEOUT_EXPIRED;
        break;
      }
      newTimeout -= intervalWaited;
    }

    if (timeout != PR_INTERVAL_NO_TIMEOUT) {
      intervalBefore =PR_IntervalNow();
    }

    monitor.wait(newTimeout);

    if (timeout != PR_INTERVAL_NO_TIMEOUT) {
      intervalAfter = PR_IntervalNow();
      firstTimeout = PR_FALSE;
    }

  } //while

  if (owner == NULL) {
    owner = me;
    *lockedByMe = PR_TRUE;
  }

  monitor.exit();

  return errorCode;

}
