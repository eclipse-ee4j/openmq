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
 * @(#)mqdestination.h	1.12 06/26/07
 */ 

#ifndef MQ_DESTINATION_H
#define MQ_DESTINATION_H

/*
 * declarations of C interface for destination
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"

/**
 * Frees the destination object specified by destinationHandle.
 *
 * @param destinationHandle the destination to free.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQFreeDestination(MQDestinationHandle destinationHandle);

/**
 * Get the destination type of a destination 
 *
 * @param destinationHandle the destination to type from
 * @param destinationType the output parameter for the type
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQGetDestinationType(const MQDestinationHandle destinationHandle,
                     MQDestinationType *       destinationType);

/**
 * Get the destination name of a destination. The returned  
 * destinationName is a copy which the caller is responsible
 * to free by calling MQFreeString
 *
 * @param destinationHandle the destination to get name from
 * @param destinationName the output parameter for the name 
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQGetDestinationName(const MQDestinationHandle destinationHandle,
                     MQString *                destinationName);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_DESTINATION_H */
