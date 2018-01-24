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
 * @(#)shimUtils.cpp	1.7 06/26/07
 */ 

#include "../basictypes/HandledObject.hpp"
#include "shimUtils.hpp"


/*
 *
 */
MQStatus 
freeHandledObject(const MQObjectHandle   handle,
                  const HandledObjectType objectType)
{
  MQError errorCode = MQ_SUCCESS;
  HandledObject * object = NULL;
  
  // Convert the handle to a HandledObject pointer.
  // If handle is invalid, then return an error
  object = getHandledObject(handle, objectType);
  CNDCHK( object == NULL, MQ_STATUS_INVALID_HANDLE );
  releaseHandledObject(object);
  object = NULL;

  // Delete the object
  ERRCHK( HandledObject::externallyDelete(handle) );

  //// The handle should be invalid now
  //ASSERT( HandledObject::getObject(handle) == NULL );
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  RETURN_STATUS( errorCode );
}





/*
 *
 */
HandledObject *
getHandledObject(const MQObjectHandle   handle,
                 const HandledObjectType objectType)
{
  // Get the object associated with this handle
  // Return NULL if it is an invalid handle, the type of the
  // handle is an unexpected type, or the object's handle
  // has not been exported
  HandledObject * object = HandledObject::acquireExternalReference(handle);
  if ((object == NULL)                                || 
      ((object->getObjectType() != objectType) &&
       (object->getSuperObjectType() != objectType))  ||
      !object->getIsExported())
  {
    releaseHandledObject(object);
    return NULL;
  }

  return object;
}




/*
 *
 */
MQStatus 
releaseHandledObject(HandledObject * object)
{
  MQError errorCode = MQ_SUCCESS;
  
  CNDCHK( object == NULL, MQ_STATUS_INVALID_HANDLE );

  // This may delete the object, if it was deleted internally.
  ERRCHK( HandledObject::releaseExternalReference(object) );
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  RETURN_STATUS( errorCode );
}
