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
 * @(#)MemAllocTest.h	1.5 06/26/07
 */ 

#ifndef MEMALLOCTEST_H
#define MEMALLOCTEST_H

#ifdef __cplusplus
extern "C" {
#endif

#include "../cshim/mqtypes.h"


/** These functions are only used during testing.  Some are used to
 *  force a memory allocation failure, which exercises the code that
 *  cleans up when these types of failures occur.  Other functions
 *  are used to force the broker to shutdown to simulate the broker
 *  crashing. */
  

/** Sets the number of memory allocations that succeed before a memory
 *  allocation fails. */
EXPORTED_SYMBOL void
setAllocationsBeforeFailure(int allocationsBeforeFailure);

/** Sets the number of memory allocations before the broker is
 *  restarted.  The BROKER_RESTART_COMMAND constant located in
 *  MemAllocTest.cpp must be set correctly if this function is used.*/
EXPORTED_SYMBOL void
setAllocationsBeforeBrokerFailure(int allocationsBeforeBrokerFailure);

/** Gets the number of allocations since the last call to
 *  setAllocationsBeforeFailure */
EXPORTED_SYMBOL int
getCurrentAllocation();

/** Sets whether or not memory allocations fail after the first memory
 *  allocation fails. */
EXPORTED_SYMBOL void
setAllocationFailuresContinue(int allocationFailuresContinue);

/** Forces the next memory allocation to succeed no matter what (except
 *  if we are actually out of memory). */
EXPORTED_SYMBOL void  
nextAllocSucceeds();

/** Determines based on the current allocation and the values set for allocations
 *  before failure whether or not this malloc should succeed. */
EXPORTED_SYMBOL int
mallocSucceeds();

#ifdef __cplusplus
}
#endif

#endif /* MEMALLOCTEST_H */


