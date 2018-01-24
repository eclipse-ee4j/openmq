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
 * @(#)PRTypesUtils.c	1.3 06/26/07
 */ 

#include "PRTypesUtils.h"
#include <assert.h>

//#include "UtilityMacros.h"

/*
 * Create a PRUint64 from two PRUint32's, which represent the hi and
 * the lo part of the PRUint64.
 */
PRUint64 
LL_ULLFromHiLo(const PRUint32 hi, const PRUint32 lo)
{
  PRUint64 hiPart;
  PRUint64 loPart;
  PRUint64 result;

  LL_UI2L( hiPart, hi );
  LL_UI2L( loPart, lo );

  /* shift the hi part left by 32, and then add in the low part */
  LL_SHL( hiPart, hiPart, 32 );
  LL_ADD( result, hiPart, loPart );
  
  return result;
}

/*
 *
 */
void 
LL_HiLoFromULL(PRUint32 * const hi, 
               PRUint32 * const lo, 
               const PRUint64 value64)
{
  PRUint64 hiPart;
  PRUint64 loPart;

  if (( hi == NULL ) || ( lo == NULL )) {
    return;
  }

  /* The hiPart is value64 shifted down by 32 bits */
  LL_USHR( hiPart, value64, 32 );

  /* The loPart is value64 bitwise ANDed with 0x00000000 FFFFFFFF */
  LL_AND( loPart, value64, LL_MAX_UINT32 );

  /* Assign the 32 bit parts to hi and lo */
  LL_L2UI( *hi, hiPart );
  LL_L2UI( *lo, loPart );
}


/*
 *
 */
PRIntervalTime
microSecondToIntervalTimeout(const PRUint32 timeoutMicroSeconds)
{
  PRIntervalTime timeout = 0;
  if ((timeoutMicroSeconds == PR_INTERVAL_NO_WAIT) ||
      (timeoutMicroSeconds == PR_INTERVAL_NO_TIMEOUT))
  {
    timeout = timeoutMicroSeconds;
  } else {
    timeout = PR_MicrosecondsToInterval(timeoutMicroSeconds);
  }
 
  return timeout;
} 


PRIntervalTime
timeoutRemaining(const PRIntervalTime start, const PRIntervalTime timeout)
{
  PRIntervalTime now = 0;
  PRIntervalTime remaining  = 0;
  PRIntervalTime elapsed  = 0;

  // Special cases for no waiting, and waiting forever
  if ((timeout == PR_INTERVAL_NO_WAIT) ||
      (timeout == PR_INTERVAL_NO_TIMEOUT))
  {
    return timeout;
  }

  now = PR_IntervalNow();
  elapsed = (PRIntervalTime)(now - start);
  if (elapsed > timeout) return (PRIntervalTime)0;

  remaining = (PRIntervalTime)(timeout - elapsed);

  assert( ((PRIntervalTime)remaining < timeout) || 
          ((now == start) && ((PRIntervalTime)remaining == timeout)) );

  return (PRIntervalTime)remaining;
}
