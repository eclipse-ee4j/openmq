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
 * @(#)HandledObject.cpp	1.8 06/26/07
 */ 

#include "HandledObject.hpp"
#include "Integer.hpp"
#include "../util/LogUtils.hpp"
#include "../util/UtilityMacros.h"

// Define static variables for the HandledObject class
ObjectHandle HandledObject::nextHandle = HANDLED_OBJECT_MIN_HANDLE;
BasicTypeHashtable * HandledObject::allocatedHandles = NULL;
Monitor HandledObject::handleMonitor;
PRInt32 HandledObject::numAllocatedHandles = 0;


/*
 *
 */
HandledObject::HandledObject()
{
  init(PR_FALSE);
}

/*
 * When lazy is true, object handle creation will be deferred
 * to export time, that is, in setIsExported().  Use of 'lazy' 
 * handled object must ensure single thread access to the object
 * before setIsExported(true) call returns
 */
HandledObject::HandledObject(PRBool lazyArg)
{
  init(lazyArg);
}

void
HandledObject::init(PRBool lazyArg)
{
  if (lazyArg == PR_FALSE) {
    this->objectHandle = HandledObject::allocateNextHandle(this);
  } else {
    this->objectHandle = HANDLED_OBJECT_INVALID_HANDLE;
  }
  this->lazy = lazyArg;
  this->isExported = PR_FALSE;

  this->deletedInternally = PR_FALSE;
  this->externalReferences = 0;
  this->checkDeletedExternally = PR_FALSE;
  this->deletedExternally = PR_FALSE;
}

/*
 *
 */
iMQError
HandledObject::getInitializationError() const
{
  if (this->lazy == PR_FALSE) {
  RETURN_ERROR_IF( this->getHandle() == HANDLED_OBJECT_INVALID_HANDLE, IMQ_OUT_OF_MEMORY );
  }
  return IMQ_SUCCESS;
}


/*
 *
 */
HandledObject::~HandledObject()
{
  CHECK_OBJECT_VALIDITY();

  if ((this->lazy == PR_FALSE)
      || ((this->lazy== PR_TRUE)
         && (this->getHandle() != HANDLED_OBJECT_INVALID_HANDLE))) {
    HandledObject::deallocateHandle(this->objectHandle, this);
  }
}

/*
 *
 */
PRBool 
HandledObject::getIsExported() const
{
  return this->isExported;
}


/*
 *
 */
MQError
HandledObject::setIsExported(const PRBool isExportedArg)
{
  if ((isExportedArg == PR_TRUE) && (this->lazy == PR_TRUE) 
      && (this->getHandle() == HANDLED_OBJECT_INVALID_HANDLE)) {
    this->objectHandle = HandledObject::allocateNextHandle(this);
    if (this->getHandle() == HANDLED_OBJECT_INVALID_HANDLE) {
      return MQ_OUT_OF_MEMORY;
    }
  }
  this->isExported = isExportedArg;
  return MQ_SUCCESS;
}

/*
 *
 */
void
HandledObject::setCheckDeletedExternally()
{
  this->checkDeletedExternally = PR_TRUE;
}

/*
 *
 */
ObjectHandle
HandledObject::getHandle() const
{
  CHECK_OBJECT_VALIDITY();

  return this->objectHandle;
}

/*
 *
 */
HandledObjectType
HandledObject::getSuperObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return UNDEFINED_HANDLED_OBJECT;
}


/** These are static functions */

/*
 *
 */
ObjectHandle 
HandledObject::allocateNextHandle(HandledObject * const handledObject)
{
  iMQError errorCode = IMQ_SUCCESS;
  Integer * handleInt = NULL;
  const Object * dummy = NULL;  // only used to determine if a handle is being used
  ObjectHandle handleToReturn = HANDLED_OBJECT_INVALID_HANDLE;
  PRInt32 startingHandle = 0;

  handleMonitor.enter();
    NULLCHK( handledObject );
    
    // We delete the hashtable when it is empty, to make detecting
    // memory leaks easier.  So we night need to allocate it here.
    if (allocatedHandles == NULL) {
      MEMCHK( allocatedHandles = new BasicTypeHashtable(PR_TRUE, PR_FALSE) );
    }
    MEMCHK( handleInt = new Integer );

    // find the next handle that is not being used
    startingHandle = nextHandle;
    do {
      nextHandle++;
      if (nextHandle > HANDLED_OBJECT_MAX_HANDLE) {
        nextHandle = HANDLED_OBJECT_MIN_HANDLE;
      }
      // If we've looped all the way around to the handle that we started with,
      // then we are out of handles.
      CNDCHK(startingHandle == nextHandle, IMQ_HANDLED_OBJECT_NO_MORE_HANDLES);

      handleInt->setValue(nextHandle);
    } while (allocatedHandles->getValueFromKey(handleInt, &dummy) == IMQ_SUCCESS);
    handleToReturn = nextHandle;
  
    // mark the handle as allocated
    handleInt->setValue(handleToReturn);
    ERRCHK( allocatedHandles->addEntry(handleInt, handledObject) );
    handleInt = NULL;  // owned by allocatedHandles now

    numAllocatedHandles++;
    LOG_FINEST(( CODELOC, HANDLED_OBJECT_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                 "allocateNextHandle() allocated %d to 0x%p.",
                 handleToReturn, handledObject ));     

  handleMonitor.exit();
  return handleToReturn;
Cleanup:
    // We delete the hashtable when it is empty to ease detecting memory leaks
    if (numAllocatedHandles == 0) {
      DELETE( allocatedHandles );
    }
    DELETE( handleInt );
    LOG_FINE(( CODELOC, HANDLED_OBJECT_LOG_MASK, NULL_CONN_ID, errorCode,
               "allocateNextHandle() couldn't allocate a handle for 0x%p.",
               handledObject ));

  handleMonitor.exit();
  return HANDLED_OBJECT_INVALID_HANDLE;
}



/*
 *
 */
void
HandledObject::deallocateHandle(const ObjectHandle handle, 
                                const HandledObject * const handledObject)
{
  Integer handleInt;

  if (handle == HANDLED_OBJECT_INVALID_HANDLE) {
    LOG_FINE(( CODELOC, HANDLED_OBJECT_LOG_MASK, NULL_CONN_ID, 
               IMQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR,
               "deallocateHandle() couldn't deallocate a handle for 0x%p",
               handledObject ));
    return;
  }

  handleMonitor.enter();
    // For safety, make sure that handle is a handle for handledObject
    if (HandledObject::getObject(handle) == handledObject)
    {
      // remove the handle from the table
      handleInt.setValue(handle);
      allocatedHandles->removeEntry(&handleInt);

      ASSERT( numAllocatedHandles > 0 );
      numAllocatedHandles--;

      // We delete the hashtable when it is empty for debugging
      // purposes, so it's easier to detect a memory leak.
      if (numAllocatedHandles == 0) {
        DELETE( allocatedHandles );
      }
    } else {
      LOG_FINE(( CODELOC, HANDLED_OBJECT_LOG_MASK, NULL_CONN_ID, 
                 IMQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR,
                 "deallocateHandle() couldn't deallocate a handle for 0x%p",
                 handledObject ));
    }
  handleMonitor.exit();

  LOG_FINEST(( CODELOC, HANDLED_OBJECT_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "deallocateHandle() deallocated handle %d for 0x%p.",
               handle, handledObject ));
}

/*
 *
 */
HandledObject * 
HandledObject::getObject(const ObjectHandle handle)
{
  HandledObject * objectFromTable = NULL;
  Integer handleInt;

  if (handle == HANDLED_OBJECT_INVALID_HANDLE) {
    return NULL;
  }

  handleMonitor.enter();
    handleInt.setValue(handle);

    // Make sure handle is in the table.
    if ((allocatedHandles == NULL) ||
        (allocatedHandles->getValueFromKey(&handleInt, (const Object**)&objectFromTable) 
           != IMQ_SUCCESS))
    {
      objectFromTable = NULL;
    }
    
  handleMonitor.exit();

  return objectFromTable;
}


iMQError
HandledObject::externallyDelete(const ObjectHandle handle)
{
  iMQError errorCode = IMQ_SUCCESS;
  HandledObject * handledObject = NULL;
  
  handleMonitor.enter();

    // get a handled to the object, and make sure it is exported
    handledObject = HandledObject::getObject(handle);
    CNDCHK( handledObject == NULL, IMQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR );
    CNDCHK( !handledObject->getIsExported(), IMQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR );

    if (handledObject->checkDeletedExternally == PR_FALSE) {

    // if there are no outstanding external references, then delete the object
    ASSERT( handledObject->externalReferences == 0 );
    CNDCHK( handledObject->externalReferences > 0, IMQ_HANDLED_OBJECT_IN_USE );
    DELETE( handledObject );

    } else {
     if (handledObject->externalReferences > 0) {
       handledObject->deletedExternally = PR_TRUE;
     } else {
      ASSERT( handledObject->externalReferences == 0 );
      DELETE( handledObject );
     }
    }
    
  handleMonitor.exit();
  
  return IMQ_SUCCESS;
Cleanup:
  
  
  handleMonitor.exit();
  return errorCode;
}



// This is only called from the C++ code.  If handledObject does not
// have any outstanding cshim layer references, then it is deleted.
// Otherwise, the object will be deleted when the last pointer to the
// object is returned by the cshim layer.
iMQError
HandledObject::internallyDelete(HandledObject * handledObject)
{
  return internallyDeleteWithCheck(handledObject, PR_FALSE);
}

iMQError
HandledObject::internallyDeleteWithCheck(HandledObject * handledObject, PRBool assertionCheck)
{
  iMQError errorCode = IMQ_SUCCESS;

  NULLCHK( handledObject );
  
  handleMonitor.enter();
    if (assertionCheck == PR_TRUE && 
      handledObject->deletedInternally == PR_TRUE && 
      handledObject->externalReferences == 0 &&
      handledObject->checkDeletedExternally == PR_FALSE) { //see releaseExternalReference
      return IMQ_SUCCESS;
    }
    ASSERT( !handledObject->deletedInternally );
    if (handledObject->externalReferences == 0)
    {
      DELETE( handledObject );
    } else {
      ASSERT( handledObject->externalReferences > 0 );
      // The cshim layer has an outstanding pointer, so let
      // it delete this object when it releases the pointer.
      handledObject->deletedInternally = PR_TRUE;
    }

  handleMonitor.exit();
  
  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}



/*
 *
 */
HandledObject * 
HandledObject::acquireExternalReference(const ObjectHandle handle)
{
  HandledObject * handledObject = NULL;
  Integer handleInt;

  if (handle == HANDLED_OBJECT_INVALID_HANDLE) {
    return NULL;
  }

  handleMonitor.enter();
    handledObject = HandledObject::getObject(handle);
    
    if (handledObject != NULL) {
       ASSERT( handledObject->externalReferences >= 0 );
       handledObject->externalReferences++;
    }
  handleMonitor.exit();

  return handledObject;
}


// This is only called from the C++ code.  If handledObject does not
// have any outstanding cshim layer references, then it is deleted.
// Otherwise, the object will be deleted when the last pointer to the
// object is returned by the cshim layer.
iMQError
HandledObject::releaseExternalReference(HandledObject * handledObject)
{
  iMQError errorCode = IMQ_SUCCESS;

  NULLCHK( handledObject );
  
  handleMonitor.enter();

    // Decrement the number of external references.  If this object
    // has been deleted internally and there are no more external
    // references, then delete the object.
    ASSERT( handledObject->externalReferences > 0 );
    handledObject->externalReferences--;
    if (handledObject->checkDeletedExternally == PR_FALSE) {

    if ((handledObject->deletedInternally) &&
        (handledObject->externalReferences == 0))
    {
      DELETE( handledObject );
    }

	} else {
    if ((handledObject->deletedExternally) &&
        (handledObject->externalReferences == 0))
    {
      DELETE( handledObject );
    }
    }

  handleMonitor.exit();
  
  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}


// This is a test class used by the static test method
class TestHandledObject : public HandledObject {
public:
  virtual HandledObjectType getObjectType() const;
};

HandledObjectType
TestHandledObject::getObjectType() const
{
  CHECK_OBJECT_VALIDITY();
  return TEST_HANDLED_OBJECT;
}


/*
 *
 */
iMQError
HandledObject::test(const PRInt32 numTests, const PRBool checkAllErrors)
{
  TestHandledObject * handledObject = NULL;
  TestHandledObject * firstHandledObject = NULL;

  firstHandledObject = new TestHandledObject;

  for (int i = 0; i < numTests; i++) {
    handledObject = new TestHandledObject;
    if (handledObject != NULL) {
      if (checkAllErrors) {
        ASSERT( handledObject->getHandle() != HANDLED_OBJECT_INVALID_HANDLE);
        ASSERT( handledObject == 
                  HandledObject::getObject(handledObject->getHandle()) );
        
      }
    }
    HandledObject::internallyDelete( handledObject );
  }
  HandledObject::internallyDelete( firstHandledObject );
  return IMQ_SUCCESS;
}
