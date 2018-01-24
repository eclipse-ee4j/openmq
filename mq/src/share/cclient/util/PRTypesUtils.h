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
 * @(#)PRTypesUtils.h	1.3 06/26/07
 */ 

/* This file defines utilities that are used with the NSPR types.
 * 
 */

#ifndef PRTYPESUTILS_H
#define PRTYPESUTILS_H

#ifdef __cplusplus
extern "C" {
#endif
    
#include <nspr.h>
#include <memory.h>

/* PORTABLE:  I don't know if float will always be 32 bits? */
typedef float    PRFloat32;

typedef PRUint8  UChar;

/** A 32 bit integer representation of the largest unsigned 16 bit integer.  */
static const PRInt32 MAX_PR_UINT16 = 0x0000FFFF;

/** A 32 bit integer representation of the largest signed 32 bit integer.  */
static const PRInt32 MAX_PR_INT32 = 0x7FFFFFFF;

/** A 64 bit integer representation of the largest unsigned 32 bit integer. */
static const PRUint64 LL_MAX_UINT32 = LL_INIT( 0, 0xFFFFFFFF );

/**
 * This function returns a 64 bit unsigned integer composed of hi for the 
 * high 32 bits and lo for the low 32 bits.
 * 
 * @param hi is the high 32 bits of the 64 bit integer to construct
 * @param lo is the low 32 bits of the 64 bit integer to construct
 * @return the 64 bit integer composed of hi and lo
 */
PRUint64 LL_ULLFromHiLo(const PRUint32 hi, const PRUint32 lo);


/**
 * This function breaks value64 into its 32 bit high and low parts and returns
 * the results in the output parameters hi and lo.  This function has no effect
 * if hi or lo is NULL.
 *
 * @param hi is the output parameter for the high 32 bits of value64
 * @param lo is the output parameter for the low 32 bits of value64
 * @value64 is the 64 bit integer to deconstruct into 32 bit parts
 */
void LL_HiLoFromULL(PRUint32 * const hi, 
                    PRUint32 * const lo, 
                    const PRUint64 value64);
/** 
 * This method converts the parameter, timeoutMicroSeconds, (which is a timeout
 * specified in microseconds) into a system dependent timeout specified in
 * PRIntervalTime.  The special values PR_INTERVAL_NO_WAIT and
 * PR_INTERVAL_NO_TIMEOUT are preserved.  
 *
 * @param timeoutMicroSeconds is the timeout to convert in microseconds
 * @return the timeout in PRIntervalTime units
 */
PRIntervalTime microSecondToIntervalTimeout(const PRUint32 timeoutMicroSeconds);


/**
 * This function returns the amount of timeout remaining based on when
 * the timeout was started (i.e. start), the duration of the timeout
 * (i.e. timeout), and the current time.  
 */
PRIntervalTime timeoutRemaining(const PRIntervalTime start, const PRIntervalTime timeout);

#ifdef __cplusplus
}
#endif

#endif /* PRTYPESUTILS_H */

