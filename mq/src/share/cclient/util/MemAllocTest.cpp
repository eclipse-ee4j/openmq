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
 * @(#)MemAllocTest.cpp	1.5 06/26/07
 */ 

#include <stdlib.h>
#include "MemAllocTest.h"


static int currentAllocation = 0;
static int failureAllocation = 0;
static int brokerFailureAllocation = 0;
static int failuresContinue  = 0;
static int forceNextAllocSucceeds = 0;

/** location of the script that restarts the broker */
const char * BROKER_RESTART_COMMAND = "c:/temp/restartBroker.bat";

/*
 *
 */
EXPORTED_SYMBOL void
setAllocationFailureProbability(double allocFails)
{
  ((void)allocFails);
}


/*
 *
 */
EXPORTED_SYMBOL void
setAllocationsBeforeFailure(int allocationsBeforeFailure)
{
  currentAllocation = 0;
  failureAllocation = allocationsBeforeFailure;
}

/*
 *
 */
EXPORTED_SYMBOL void
setAllocationsBeforeBrokerFailure(int allocationsBeforeBrokerFailure)
{
  currentAllocation = 0;
  brokerFailureAllocation = allocationsBeforeBrokerFailure;
}

/*
 *
 */
EXPORTED_SYMBOL int
getCurrentAllocation()
{
  return currentAllocation;
}


/*
 *
 */
EXPORTED_SYMBOL void
setAllocationFailuresContinue(int allocationFailuresContinue)
{
  failuresContinue = allocationFailuresContinue;
}


/*
 *
 */
EXPORTED_SYMBOL void
nextAllocSucceeds()
{
  forceNextAllocSucceeds = 1;
}

/*
 *
 */
EXPORTED_SYMBOL int 
mallocSucceeds()
{
  currentAllocation++;
 
  if (currentAllocation == brokerFailureAllocation) {
    brokerFailureAllocation = 0;
    // restart the broker
    system(BROKER_RESTART_COMMAND);
  }
  
  if (forceNextAllocSucceeds) {
    forceNextAllocSucceeds = 0;
    return true;
  }
  else if (failuresContinue            && 
          (failureAllocation != 0)     && 
          (currentAllocation >= failureAllocation))
  {
    return false;
  }
  else if ((failureAllocation != 0) && (currentAllocation == failureAllocation)) {
    failureAllocation = 0;
    return false;
  }

  return true;
}




