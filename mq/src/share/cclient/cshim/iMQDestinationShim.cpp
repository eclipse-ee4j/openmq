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
 * @(#)iMQDestinationShim.cpp	1.12 06/26/07
 */ 

#include "mqdestination.h"
#include "shimUtils.hpp"
#include "../client/Destination.hpp"
#include "../client/Session.hpp"


EXPORTED_SYMBOL MQStatus 
MQFreeDestination(MQDestinationHandle destinationHandle)
{
  CLEAR_ERROR_TRACE(PR_FALSE);
  
  return freeHandledObject(destinationHandle.handle, DESTINATION_OBJECT);
}

/**
 * Get destination type of a destination
 *
 * @param destinationHandle the destination to type from
 * @param destinationType the output parameter for the type
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQGetDestinationType(const MQDestinationHandle destinationHandle,
                     MQDestinationType *       destinationType)
{
  static const char FUNCNAME[] = "MQGetDestinationType";
  MQError errorCode = MQ_SUCCESS;
  Destination * destination = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( destinationType == NULL, MQ_NULL_PTR_ARG );
  destination = (Destination*)getHandledObject(destinationHandle.handle,
                                               DESTINATION_OBJECT);
  CNDCHK( destination == NULL, MQ_STATUS_INVALID_HANDLE);
  if ( destination->getIsQueue() == PR_TRUE) {
    *destinationType = MQ_QUEUE_DESTINATION;
  } else {
    *destinationType = MQ_TOPIC_DESTINATION;
  }

  releaseHandledObject(destination);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(destination);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


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
                     MQString *                destinationName)
{
  static const char FUNCNAME[] = "MQGetDestinationName";
  MQError errorCode = MQ_SUCCESS;
  Destination * destination = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( destinationName == NULL, MQ_NULL_PTR_ARG );
  *destinationName = NULL;

  destination = (Destination*)getHandledObject(destinationHandle.handle,
                                               DESTINATION_OBJECT);
  CNDCHK( destination == NULL, MQ_STATUS_INVALID_HANDLE);
  {
  const UTF8String * name = destination->getName();
  CNDCHK( name == NULL, MQ_DESTINATION_NO_NAME );
  ConstMQString namec = name->getCharStr(); 
  MQString destName = new MQChar[STRLEN(namec)+1];
  CNDCHK( destName == NULL, MQ_OUT_OF_MEMORY );
  STRCPY(destName, namec);
  *destinationName = destName;
  }

  releaseHandledObject(destination);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(destination);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

