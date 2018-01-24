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
 * @(#)SerialHandleManager.cpp	1.4 06/26/07
 */ 

#include "../debug/DebugUtils.h" // must be first in the file

//#include <typeinfo.h>
#include "SerialHandleManager.hpp"
#include "../util/UtilityMacros.h"


// OPTIMIZATION: it is my feeling that making this class so object
// oriented will make it slow because so much memory allocation is
// performed.  If it is too slow, we can go back to the original
// design of using a struct that stored either a BasicType* pointer or
// a TypeEnum (and a flag field that said which field was valid).

/*
 *
 */
SerialHandleManager::SerialHandleManager()
{
  CHECK_OBJECT_VALIDITY();

  this->init();
}

/*
 *
 */
SerialHandleManager::~SerialHandleManager()
{
  CHECK_OBJECT_VALIDITY();

  this->reset();
}

/*
 *
 */
void
SerialHandleManager::init()
{
  CHECK_OBJECT_VALIDITY();
}


/*
 *
 */
void
SerialHandleManager::reset()
{
  CHECK_OBJECT_VALIDITY();

  SerialHandleManager::HandleInfo * handle = NULL;
  PRUint32 numHandles = this->handles.size();

  // Pop each handle from the vector and delete it.  Deleting it 
  // will also delete the object the handle points to (if any).
  for (PRUint32 i = 0; i < numHandles; i++) {
    this->handles.pop((void**)&handle);
    DELETE( handle );
  }
  ASSERT( this->handles.size() == 0 );

  this->handles.reset();
  
  this->init();
}




/*
 *
 */
iMQError
SerialHandleManager::setNextHandle(
    SerialHandleManager::HandleInfo * const handleInfo)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( handleInfo );
  RETURN_IF_ERROR( this->handles.add(handleInfo) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
SerialHandleManager::setNextHandleToObject(BasicType * object)
{
  CHECK_OBJECT_VALIDITY();

  // Whether this function succeeds or fails, it is responsible for freeing object
  iMQError errorCode = IMQ_SUCCESS;
  SerialHandleManager::ObjectHandleInfo * info = NULL;

  MEMCHK( info = new SerialHandleManager::ObjectHandleInfo(object) );
  ERRCHK( this->setNextHandle(info) );

  return IMQ_SUCCESS;
Cleanup:
  if (info != NULL) {
    DELETE( info );
  } else {
    DELETE( object );
  }
  return errorCode;
}

/*
 *
 */
iMQError
SerialHandleManager::setNextHandleToClass(const TypeEnum classType)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  SerialHandleManager::ClassHandleInfo * info = NULL;
  MEMCHK( info = new SerialHandleManager::ClassHandleInfo(classType) );
  ERRCHK( this->setNextHandle(info) );
  
  return IMQ_SUCCESS;
Cleanup:
  DELETE( info );
  return errorCode;
}

/*
 *
 */
iMQError 
SerialHandleManager::getInfoFromHandle(
    const PRUint32 handle,
    SerialHandleManager::HandleInfo ** const handleInfo) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( handleInfo );
  *handleInfo = NULL;

  // convert the handle to a handle index
  PRUint32 handleIndex = handle - SERIAL_HANDLE_MANAGER_BASE_HANDLE;
  RETURN_ERROR_IF( handleIndex >= this->handles.size(), 
                   IMQ_SERIALIZE_BAD_HANDLE);

  RETURN_IF_ERROR( this->handles.get(handleIndex, (void**)handleInfo) );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialHandleManager::getClassFromHandle(const PRUint32 handle,
                                        TypeEnum * const classType) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( classType );
  *classType = NULL_TYPE;

  SerialHandleManager::HandleInfo * info = NULL;
  RETURN_IF_ERROR( this->getInfoFromHandle(handle, &info) );
  RETURN_ERROR_IF( info == NULL, IMQ_SERIALIZE_BAD_HANDLE );
  RETURN_ERROR_IF( info->getType() != SerialHandleManager::CLASS_HANDLE, 
                   IMQ_SERIALIZE_NOT_CLASS_HANDLE );

  *classType = ((SerialHandleManager::ClassHandleInfo*)info)->classType;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialHandleManager::getObjectCloneFromHandle(
                         const PRUint32 handle,
                         BasicType ** const object) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( object );
  *object = NULL;

  // look-up the reference handle
  SerialHandleManager::HandleInfo * info = NULL;
  RETURN_IF_ERROR( this->getInfoFromHandle(handle, &info) );
  RETURN_ERROR_IF( info == NULL, IMQ_SERIALIZE_BAD_HANDLE );
  RETURN_ERROR_IF( info->getType() != SerialHandleManager::OBJECT_HANDLE, 
                   IMQ_SERIALIZE_NOT_OBJECT_HANDLE );
  
  // now we know that the info is storing an object so we can do this cast
  BasicType * newObject = 
    ((SerialHandleManager::ObjectHandleInfo*)info)->objectValue;

  // Clone the object and return an error if the clone returns NULL
  RETURN_ERROR_IF( newObject == NULL, IMQ_SERIALIZE_NOT_OBJECT_HANDLE );
  *object = newObject->clone();
  RETURN_IF_OUT_OF_MEMORY( *object );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialHandleManager::getHandleFromInfo(
    const SerialHandleManager::HandleInfo * const handleInfoToFind,
    PRUint32 * const handle ) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( handleInfoToFind );
  RETURN_ERROR_IF_NULL( handle );
  *handle = SERIAL_HANDLE_MANAGER_INVALID_HANDLE;

  // search for the handle that matches handleInfo
  for (PRUint32 handleIndex = 0; handleIndex < this->handles.size(); handleIndex++) {
    HandleInfo * handleInfo;
    handleInfo = NULL;
    
    // Get the next HandleInfo and compare it to this handle.
    RETURN_IF_ERROR( this->handles.get(handleIndex, (void**)&handleInfo) );
    if (handleInfoToFind->equals(handleInfo)) {
      *handle = handleIndex + SERIAL_HANDLE_MANAGER_BASE_HANDLE;
      return IMQ_SUCCESS;
    }
  }

  // IMQ_SUCCESS is returned, but handle is set to SERIAL_HANDLE_MANAGER_INVALID_HANDLE
  // if the handle was not found.
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialHandleManager::getHandleFromBasicType( const BasicType * const object,
                                             PRUint32 * handle )
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  BasicType * objectClone = NULL;
  SerialHandleManager::ObjectHandleInfo * info = NULL;

  NULLCHK( object );
  NULLCHK( handle );

  // We have to clone the object because the
  // SerialHandleManager::ObjectHandleInfo destructor will delete whatever
  // object it points to.
  MEMCHK( objectClone = object->clone() );

  // Create a new ObjectHandleInfo object so we can search for a
  // match.
  MEMCHK( info = new SerialHandleManager::ObjectHandleInfo(objectClone) );
  objectClone = NULL;

  // Find the handle that matches the info for object.  Then delete
  // the info object because we don't need it anymore.
  ERRCHK( this->getHandleFromInfo(info, handle) );

  DELETE( info );
  return IMQ_SUCCESS;
Cleanup:

  DELETE( objectClone );
  DELETE( info );
  return errorCode;
}
                                           

/*
 *
 */
iMQError
SerialHandleManager::getHandleFromClass( const TypeEnum classType,
                                         PRUint32 * handle )
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  SerialHandleManager::ClassHandleInfo * info = NULL;

  NULLCHK( handle );
  *handle = SERIAL_HANDLE_MANAGER_INVALID_HANDLE;


  // Create a new ObjectHandleInfo object so we can search for a
  // match.
  MEMCHK( info = new SerialHandleManager::ClassHandleInfo(classType) );

  // Find the handle that matches the info for classType.  Then delete
  // the info object because we don't need it anymore.
  ERRCHK( this->getHandleFromInfo(info, handle) );

  DELETE( info );
  return IMQ_SUCCESS;
Cleanup:
  DELETE( info );
  return errorCode;
}

/*
 *
 */
iMQError
SerialHandleManager::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );
  
  fprintf(file, "SerialHandleManager::print not implemented\n");

  return IMQ_SUCCESS;
}



/*
 * Implementation for SerialHandleManager::ObjectHandleInfo methods
 */
SerialHandleManager::ObjectHandleInfo::ObjectHandleInfo()
{
  this->objectValue = NULL;
}

SerialHandleManager::ObjectHandleInfo::ObjectHandleInfo(BasicType * const objectValueArg)
{
  this->objectValue = objectValueArg;
}

SerialHandleManager::ObjectHandleInfo::~ObjectHandleInfo()
{
  DELETE(objectValue); 
}

SerialHandleManager::HandleType
SerialHandleManager::ObjectHandleInfo::getType() const
{
  return OBJECT_HANDLE;
}

PRBool
SerialHandleManager::ObjectHandleInfo::equals(const HandleInfo * const handleInfo) const
{
  return (handleInfo != NULL)                     && 
         (handleInfo->getType() == OBJECT_HANDLE) &&
         (this->objectValue->equals(((ObjectHandleInfo*)handleInfo)->objectValue));
}

iMQError
SerialHandleManager::ObjectHandleInfo::print(FILE * const file) const
{
  RETURN_ERROR_IF_NULL( file );

  //fprintf(file, "OBJECT_HANDLE = 0x%08x", (PRUint32)objectValue );

  return IMQ_SUCCESS;
}

/*
 * Implementation for SerialHandleManager::ObjectHandleInfo methods
 */
SerialHandleManager::ClassHandleInfo::ClassHandleInfo()
{
  this->classType = UNKNOWN_TYPE;
}

SerialHandleManager::ClassHandleInfo::ClassHandleInfo(const TypeEnum classTypeArg)
{
  this->classType = classTypeArg;
}

SerialHandleManager::ClassHandleInfo::~ClassHandleInfo()
{

}

SerialHandleManager::HandleType
SerialHandleManager::ClassHandleInfo::getType() const
{
  return CLASS_HANDLE;
}

PRBool
SerialHandleManager::ClassHandleInfo::equals(const HandleInfo * const handleInfo) const
{
  return (handleInfo != NULL)                    && 
         (handleInfo->getType() == CLASS_HANDLE) &&
         (this->classType == ((ClassHandleInfo*)handleInfo)->classType);
}

iMQError
SerialHandleManager::ClassHandleInfo::print(FILE * const file) const
{
  RETURN_ERROR_IF_NULL( file );

  fprintf(file, "CLASS_HANDLE = %d", classType );

  return IMQ_SUCCESS;
}









