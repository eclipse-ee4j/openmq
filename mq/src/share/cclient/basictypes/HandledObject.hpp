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
 * @(#)HandledObject.hpp	1.6 06/26/07
 */ 

#ifndef HANDLEDOBJECT_HPP
#define HANDLEDOBJECT_HPP


#include <nspr.h>
#include "../error/ErrorCodes.h"
#include "Monitor.hpp"
#include "Object.hpp"
#include "../containers/BasicTypeHashtable.hpp"


/** Instead of void* pointer, we use a 32-bit integer to identify
    objects that are passed out by the C shim layer. */
typedef PRInt32 ObjectHandle;

/** So we can ensure that the handle has the proper type, each
    subclass of HandledObject, implements the getObjectType() method
    which returns one of the following as its type. */
enum HandledObjectType {
  // This is the super class for all types except the various message types
  UNDEFINED_HANDLED_OBJECT,
  
  TEST_HANDLED_OBJECT,
  CONNECTION_OBJECT,
  SESSION_OBJECT,

  DESTINATION_OBJECT,
  MESSAGE_CONSUMER_OBJECT,
  MESSAGE_PRODUCER_OBJECT,
  PROPERTIES_OBJECT,

  // This is the super class for (TEXT|BYTES)_MESSAGE_OBJECT
  MESSAGE_OBJECT,

  TEXT_MESSAGE_OBJECT,
  BYTES_MESSAGE_OBJECT
};

/** This constant specifies that the Handle is invalid.  We chose this
    value because in debug mode, MSVC sets fields of a deleted class
    to this value. */
static const ObjectHandle HANDLED_OBJECT_INVALID_HANDLE = 0xFEEEFEEE; 

/** Handles are allocated in the range HANDLED_OBJECT_MIN_HANDLE to
    HANDLED_OBJECT_MAX_HANDLE.  When HANDLED_OBJECT_MAX_HANDLE -
    HANDLED_OBJECT_MIN_HANDLE handles have been allocated, the
    allocation rolls over to allocate the first unallocated handle
    larger than HANDLED_OBJECT_MIN_HANDLE.  These values can be
    changed with the following constraints: 1) the total number of
    possible handle values should be at least as large as the number
    of HandledObjects that will exist at a given time, and 2)
    HANDLED_OBJECT_INVALID_HANDLE is not between
    HANDLED_OBJECT_MIN_HANDLE and HANDLED_OBJECT_MAX_HANDLE.  The
    range between these values can be made artificially small to test
    the ability of the handle manager to roll over. */
static const ObjectHandle HANDLED_OBJECT_MIN_HANDLE = 100; 
static const ObjectHandle HANDLED_OBJECT_MAX_HANDLE = 2000000000;

/**
 * This class allows various C++ objects (Connection, MessageConsumer,
 * etc.) to be accessible to C programs that use this library.  For
 * safety and modularity, pointers to these objects are not passed out
 * of the library, but instead a 32-bit integer handle is used.  All
 * of the classes that are exported outside of the library are
 * subclasses of HandledObject, which automatically allocates a new
 * handle when an object is created and deallocates the handle when it
 * is destroyed.  It also tries to ensure that no object is deleted
 * while an external reference to it exists, but this facility should
 * not be relied upon.  */
class HandledObject : public Object {
private:
  /** This object's handle.  A pointer to this object can be retrieved
      by looking it up in the static member hashtable
      allocatedHandles. */
  ObjectHandle objectHandle;

  /** True iff this handle should be accessible outside of this
   *  library.  Some objects are not exported outside of the library,
   *  by setting isExported to FALSE, these objects cannot be accessed
   *  outside of the library.  */
  PRBool isExported;

  /** The number of pointers (not handles) held by the cshim layer to
   *  this object.  This does not include pointers held by objects
   *  (e.g. Connection or Session) in the C++ code.  This prevents us
   *  from deleting an object while the external code is active. */
  PRInt32 externalReferences;

  /** True iff the object has been deleted internally, and should be
   *  deleted by the cshim layer whenever the last external reference
   *  is returned */
  PRBool deletedInternally;

  /* The following 2 variables are added for objects that has opposite
   * of deletedInternally memory management semantic
   */
  PRBool checkDeletedExternally;
  /** True iff the object has been deleted externally, and should be
    * deleted whenever the last external reference is returned. It's
    * only used when checkDeletedExternally is true */ 
  PRBool deletedExternally;

  /**
    * If true, create object handle lazily, that is, when export */
  PRBool lazy;

  void init(PRBool lazy);  

public:  
  /** Constructor */
  HandledObject();
  HandledObject(PRBool lazy);

  /** Destructor.  It's made virtual so that subclasses destructors
      get called */
  virtual ~HandledObject();

  /** @return true iff this handle should be accessible outside of
      this library */
  PRBool getIsExported() const;

  /** Returns the 32-bit handle for this object.
   *  @return the handle for this object */
  ObjectHandle getHandle() const;
  
  /** Sets whether or not this object is exported outside of the
   *  class.
   *  @param export true iff this handle should be accessible * *
   *  outside of this library */
  MQError setIsExported(const PRBool isExported);

  /** For objects that has opposite of deletedInternally
   * memory management semantic */
  void setCheckDeletedExternally();

  /** Each sub class must implement this.
   *  @return the type of the class (e.g. the subclass of HandledObject) */
  virtual HandledObjectType getObjectType() const = 0;

  /** Each class that has a super class that can be used in place of
   *  the object should override this.  This is currently only used
   *  for the message types.
   *  @return the super type of the class */
  virtual HandledObjectType getSuperObjectType() const;

  /** If we run out of memory, then allocating a handle for this
   *  object could fail.  If this is the case, we can detect it by calling
   *  this method after the object is constructed.
   *
   *  @return IMQ_SUCCESS if the object was successfully initialized
   *         and an error otherwise */
  virtual iMQError getInitializationError() const;

/** These are the static members and methods for managing the handles */
private:

  /** The next handle to allocated */
  static ObjectHandle nextHandle;

  /** A hashtable used to translate a handle to an object pointer */
  static BasicTypeHashtable * allocatedHandles;

  /** A monitor to ensure synchronous access to the other member
      variables */
  static Monitor handleMonitor;
  static PRInt32 numAllocatedHandles;
  
  /** Allocates the next handle to handledObject.  This is called from the
   *  constructor.
   *
   * @param handledObject the object to allocate a handle for
   * @return the handled allocated to handledObject */
  static ObjectHandle allocateNextHandle(HandledObject * const handledObject);

  /** Deallocates handle.  This is called from the destructor.
   * 
   * @param handle the handle to deallocate
   * @param handledObject the object that handle refers to.
   */
  static void deallocateHandle(const ObjectHandle handle,
                               const HandledObject * const handledObject);
  
  /** Returns the handle to the object handled by handle.
   * 
   * @param the handle of the object to return
   * @retrun the object whose handle is handle
   */
  static HandledObject * getObject(const ObjectHandle handle);
  
public:

  /** Tests the HandledObject class */
  static iMQError test(const PRInt32 numTests, const PRBool checkAllErrors);

  /**
   * If there are no outstanding external pointers held to
   * handledObject (i.e. externalReferences == 0), then it is deleted.
   * Otherwise deletedInternally is set to TRUE, and when
   * externalReferences reaches 0, releaseExternalReferences will
   * delete the object.
   *
   * @param handledObject the object to delete 
   * @return IMQ_SUCCESS if successful and an error otherwise */
  static iMQError internallyDelete(HandledObject * handledObject);

  static iMQError internallyDeleteWithCheck(HandledObject * handledObject, PRBool assertionCheck);

  /**
   * If there are no outstanding external pointers held to
   * handledObject (i.e. externalReferences == 0), then it is deleted.
   * Otherwise deletedInternally is set to TRUE, and when
   * externalReferences reaches 0, releaseExternalReferences will
   * delete the object.
   *
   * @param handledObject the object to delete 
   * @return IMQ_SUCCESS if successful and an error otherwise */
  static iMQError externallyDelete(const ObjectHandle handle);

  /**
   * Used by the C shim layer to acquire a pointer to the object whose
   * handle is handle.  This increments externalReferences.
   *
   * @param handle the handle of the object to acquire the reference for
   * @return the object handled by handle. */
  static HandledObject * acquireExternalReference(const ObjectHandle handle);

  /**
   * Used by the C shim layer to release a pointer to the object whose
   * handle is handle.  This decrements externalReferences.
   *
   * @param handledObject the object to release the reference for
   * @return IMQ_SUCCESS if successful and an error otherwise */
  static iMQError releaseExternalReference(HandledObject * handledObject);
};


#endif



