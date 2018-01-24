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
 * @(#)Vector.cpp	1.5 06/26/07
 */ 

#include "../debug/DebugUtils.h"

#include "../util/UtilityMacros.h"

#include "Vector.hpp"

// All methods here are documented in Vector.hpp


/**
 *
 */
Vector::Vector()
{
  CHECK_OBJECT_VALIDITY();

  init(VECTOR_INITIAL_NUM_ELEMENTS, PR_TRUE);
}

/**
 *
 */
Vector::Vector(const PRBool autoDeleteElementsArg)
{
  CHECK_OBJECT_VALIDITY();

  init(VECTOR_INITIAL_NUM_ELEMENTS, autoDeleteElementsArg);
}


/**
 *
 */
Vector::Vector(PRUint32 initialSizeArg, const PRBool autoDeleteElementsArg)
{
  CHECK_OBJECT_VALIDITY();

  init(initialSizeArg, autoDeleteElementsArg);
}

/**
 *
 */
Vector::~Vector()
{
  CHECK_OBJECT_VALIDITY();

  this->reset(); // calls Vector::reset() even if this class is overridden
}

/**
 *
 */
void
Vector::init(PRUint32 initialSizeArg, const PRBool autoDeleteElementsArg)
{
  CHECK_OBJECT_VALIDITY();

  this->elements             = NULL;
  this->numAllocatedElements = 0;
  this->nextElementIndex     = 0;
  this->autoDeleteElements   = autoDeleteElementsArg;
  this->initialSize          = initialSizeArg;
}


/**
 *
 */
void
Vector::reset()
{
  CHECK_OBJECT_VALIDITY();

  // Delete each element if autoDelete is turned on
  if (autoDeleteElements) {
    for (PRUint32 i = 0; i < nextElementIndex; i++) {
      ASSERT( elements != NULL );
      if (elements == NULL) {
        // This shouldn't ever happen, but we shouldn't crash if it does
        return;  
      }
      ASSERT( elements[i] != NULL );

#if defined(__linux__) || defined(LINUX)
      if (elements[i] != NULL) {
        delete ((PRUint8 *)elements[i]);
        elements[i] = NULL;
      }
#else
      DELETE( elements[i] );
#endif
    }
  }

  // Delete the elements array
  DELETE_ARR( elements );
  
  // Reinitialize all member variables to their default values
  init(this->initialSize, this->autoDeleteElements);
}


/**
 *
 */
iMQError 
Vector::allocateElementsIfNeeded()
{
  CHECK_OBJECT_VALIDITY();

  // If entries is full. then increase its size.
  if (nextElementIndex >= numAllocatedElements) {
    ASSERT( nextElementIndex == numAllocatedElements );
    RETURN_IF_ERROR( increaseAllocatedElements() );
  } 
  ASSERT( nextElementIndex < numAllocatedElements );

  return IMQ_SUCCESS;
}

/**
 *
 */
iMQError 
Vector::increaseAllocatedElements()
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( nextElementIndex == numAllocatedElements );
  
  // Allocate a bigger elements array
  PRUint32 newNumAllocatedElements =
    MAX(this->initialSize, numAllocatedElements * 2);
  RETURN_UNEXPECTED_ERROR_IF( numAllocatedElements >= newNumAllocatedElements, 
                              IMQ_VECTOR_TOO_BIG );
#if defined(__linux__) || defined(LINUX)
  void ** newElements = (void **)new PRUint8*[newNumAllocatedElements];
#else
  void ** newElements = new void*[newNumAllocatedElements];
#endif
  RETURN_IF_OUT_OF_MEMORY( newElements );
  
  // Copy the old elements into the new elements, and set the rest to NULL
  PRUint32 i;
  for (i = 0; i < nextElementIndex; i++) {
    newElements[i] = elements[i];
  }
  for (i = nextElementIndex; i < newNumAllocatedElements; i++) {
    newElements[i] = NULL;
  }
  
  // Delete the old elements
  DELETE_ARR( elements );
  
  // Update elements to be the newly allocated elements
  elements = newElements;
  numAllocatedElements = newNumAllocatedElements;

  return IMQ_SUCCESS;
}


/**
 * Append element to the array of elements
 */
iMQError
Vector::add(void * const element)
{
  CHECK_OBJECT_VALIDITY();

  //RETURN_ERROR_IF_NULL( element );
  RETURN_IF_ERROR( allocateElementsIfNeeded() );
  
  elements[nextElementIndex] = element;
  nextElementIndex++;

  return IMQ_SUCCESS;
}

/**
 * Get the element at the specified index
 */
iMQError
Vector::get(const PRUint32 index, void ** const element) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  RETURN_ERROR_IF( index >= nextElementIndex, IMQ_BAD_VECTOR_INDEX );
  
  *element = elements[index];

  return IMQ_SUCCESS;
}

/**
 * Get the element at the specified index
 */
iMQError
Vector::remove(const PRUint32 index, void ** const element) 
{
  CHECK_OBJECT_VALIDITY();

  RETURN_IF_ERROR( get(index,&(*element)) );

  // Slide all of the elements down
  for (PRUint32 i = index; i < nextElementIndex - 1; i++) {
    elements[i] = elements[i+1];
  }
  nextElementIndex--;

  return IMQ_SUCCESS;
}

/**
 * Find the element.
 */
iMQError
Vector::find(const void * const element, PRUint32 * const elementIndex) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  RETURN_ERROR_IF_NULL( elementIndex );
  *elementIndex = 0;
  PRUint32 index = 0;

  for (index = 0; index < nextElementIndex; index++) {
    if (elements[index] == element) {
      *elementIndex = index;
      return IMQ_SUCCESS;
    }
  }

  return IMQ_NOT_FOUND;
}


/**
 * Find the BasicType element.
 */
iMQError
Vector::findBasicType(const BasicType * const element, 
                      PRUint32 * const elementIndex) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  RETURN_ERROR_IF_NULL( elementIndex );
  *elementIndex = 0;
  PRUint32 index = 0;

  for (index = 0; index < nextElementIndex; index++) {
    if (element->equals((BasicType*)elements[index])) {
      *elementIndex = index;
      return IMQ_SUCCESS;
    }
  }

  return IMQ_NOT_FOUND;
}

/**
 * Find the element and remove it.
 */
iMQError
Vector::remove(const void * const element) 
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  void * dummy = NULL;
  PRUint32 index = 0;

  if (this->find(element, &index) == IMQ_SUCCESS) {
    return this->remove(index, &dummy);
  }

  return IMQ_NOT_FOUND;
}


/**
 * Find the BasicType element and remove it
 */
iMQError
Vector::removeBasicType(const BasicType * const element)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  void * dummy = NULL;
  PRUint32 index = 0;

  if (this->findBasicType(element, &index) == IMQ_SUCCESS) {
    return this->remove(index, &dummy);
  }

  return IMQ_NOT_FOUND;
}


/*
 * Pop the last element from the queue.
 */
iMQError 
Vector::pop(void ** const element)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( element );
  RETURN_UNEXPECTED_ERROR_IF( size() <= 0, IMQ_BAD_VECTOR_INDEX );
  *element = NULL;

  RETURN_IF_ERROR( get(nextElementIndex - 1, element) );
  elements[nextElementIndex - 1] = NULL;
  nextElementIndex--;

  return IMQ_SUCCESS;
}


/**
 *
 */
PRUint32
Vector::size() const
{
  CHECK_OBJECT_VALIDITY();

  return nextElementIndex;
}

