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
 * @(#)mqstatus.h	1.15 06/26/07
 */ 

#ifndef MQ_STATUS_H
#define MQ_STATUS_H

/*
 * declarations of C interface for error handling 
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */


#include "mqtypes.h"

/**
 * Returns MQ_TRUE iff status represents an error.
 *
 * @param status the result of an MQ function call to check
 * @return MQ_TRUE iff status represents an error
 */
EXPORTED_SYMBOL MQBool 
MQStatusIsError(const MQStatus status);


/**
 * Returns the 32 bit error code associated with status.
 *
 * @param status the result of an MQ function call to check
 * @return the 32 bit error code associated with status.
 */
EXPORTED_SYMBOL MQError 
MQGetStatusCode(const MQStatus status);

/**
 * Returns a string explanation of status.  The caller is responsible
 * for freeing the returned string by calling MQFreeString.
 *
 * @param status the result of an MQ function call to check
 * @return the string explanation of status */
EXPORTED_SYMBOL MQString 
MQGetStatusString(const MQStatus status);


/**
 * Gets error trace. The caller is responsible for freeing 
 * the returned string by calling MQFreeString.
 *
 * @return the error trace or NULL if no error trace */
EXPORTED_SYMBOL MQString 
MQGetErrorTrace();


/**
 * Frees a MQString that was returned by MQGetStatusString
 * or MQGetErrorTrace
 * 
 * @param string the MQString to free.  It must have been
 *        returned by MQGetStatusString or MQGetErrorTrace */
EXPORTED_SYMBOL void
MQFreeString(MQString string);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_STATUS_H */
