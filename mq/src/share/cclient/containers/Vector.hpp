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
 * @(#)Vector.hpp	1.5 06/26/07
 */ 

#ifndef VECTOR_HPP
#define VECTOR_HPP

#include "../debug/DebugUtils.h"
#include "../error/ErrorCodes.h"
#include "../basictypes/Object.hpp"
#include "../basictypes/BasicType.hpp"
#include <nspr.h>

/*
 * 
 * This a general purpose Vector class.  The class only stores void*
 * elements, so it is up to the caller to know the type of what is
 * being stored.
 *
 * When ~Vector() or reset() is called, all memory is deallocated and
 * each element (i.e. each void* pointer that was stored) is deleted.
 * Therefore, it is dangerous to keep alternate pointers to elements
 * stored in the vector because they will be invalid if the Vector is
 * deleted.  Also, note that the destructor for the object will NOT be
 * called.
 *
 * It is currently only optimized for appending elements to the end of
 * the vector by calling add() and for retrieving an element at a
 * specific index.  
 */
// This is the initial number of elements to allocate.
static const PRUint32 VECTOR_INITIAL_NUM_ELEMENTS = 8;
class Vector : public Object {
protected:
  /** elements is a dynamically allocated array of void* pointers, that
   *  stores the elements added by the caller.  The size of elements is
   *  numAllocatedElements, and the first nextElementIndex elements are
   *  valid.  When elements becomes full, its size is doubled. */
  void **  elements;

  /** numAllocatedElements is the size of the dynamically allocated
   *  elements array.  It's value is initially INITIAL_NUM_ELEMENTS, and
   *  then doubles each time that elements is grown. */
  PRUint32  numAllocatedElements;

  /** nextElementIndex is the index into elements array where the next
   *  element will be placed.  Because all of the elements
   *  0..(nextElement-1) are valid, nextElementIndex is also the number
   *  of elements stored in the vector.  If elements is full, then
   *  nextElementIndex == numAllocatedElements, and in this case,
   *  increaseAllocatedElements is called to grow elements. */
  PRUint32  nextElementIndex;

  /** automaticallyDeleteElements = true iff reset() should call delete
   *  on the elements that are stored in the array */
  PRBool    autoDeleteElements;

  /** initial vector size */
  PRUint32 initialSize;

  /** init() initializes all member variables to a default value
   *  automaticallyDeleteElements = true iff ~Vector() and reset() should
   *  call delete on the elements that are stored in the array */
  void init(PRUint32 initialSize, const PRBool autoDeleteElements);

  /** allocateElementsIfNeeded() increases the size of elements array
   *  if it is full by calling increaseAllocatedElements() */
  iMQError allocateElementsIfNeeded();

  /** increaseAllocatedElements() increases the size of elements array */
  iMQError increaseAllocatedElements();
  
public:
  /** constructor that sets automaticallyDeleteElements = true */
  Vector();

  /** automaticallyDeleteElements = true iff ~Vector() and reset() should
   *  call delete on the elements that are stored in the array */
  Vector(const PRBool autoDeleteElements);

  Vector(PRUint32 initialSize, const PRBool autoDeleteElements);

  /*  ~Vector() calls reset().  See warning about reset() below */
  virtual ~Vector();
  
  /** reset() deallocates all memory associated with this vector and
   *  reinitializes all of its member variables.  WARNING:
   *  autoDeleteElements is set to true it also calls delete on each
   *  void* pointer stored in the array.  Therefore either delete each
   *  element in the array yourself, or only leave elements in the
   *  array that were created by calling new (i.e. not new[] or
   *  malloc).  Also, note that the destructor for the elements in the
   *  array will NOT be called.  It is recommended that the caller
   *  delete each element in the Vector by calling pop instead of
   *  calling reset(). */
  virtual void reset();

  /** add() appends element to the end of the vector.  It returns an
   *  error if element could not be added (e.g. if the system is out of
   *  memory). */
  iMQError add(void * const element);

  /** get() returns the element at index in the output parameter
   *  element.  If this function succeeds, then *element is the void*
   *  pointer stored at the indexth position of the vector.  An error
   *  is returned if element is NULL or index is invalid.  The valid
   *  range of indices is 0..size-1. */
  iMQError get(const PRUint32 index, void ** const element) const;

  /** remove() returns the element at index in the output parameter
   *  element.  If this function succeeds, then *element is the void*
   *  pointer stored at the indexth position of the vector.  This
   *  element is then deleted from the vector.  An error is returned if
   *  element is NULL or index is invalid.  The valid range of indices
   *  is 0..size-1.  Note that this does not call delete on the element. */
  iMQError remove(const PRUint32 index, void ** const element);

  /**
   * If element is in this Vector, then it is removed.  If multiple
   * instances of element are found, then only the first one is
   * deleted.  Note that this does not call delete on the element.
   *
   * @param element the element to remove
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError remove(const void * const element);

  /**
   * This method assumes that all of the elements stored in the vector
   * are BasicTypes.  If element is in this Vector, then it is
   * removed.  If multiple instances of element are found, then only
   * the first one is deleted.  Note that this does not call delete on
   * the element.  
   *
   * @param element the element to remove
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError removeBasicType(const BasicType * const element);

  /**
   * If element is in this Vector, then the index of element is
   * returned in elementIndex.  If multiple instances of element are
   * found, then only the first one is returned.
   *
   * @param element the element to find
   * @param elementIndex the index of the element
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError find(const void * const element, PRUint32 * const elementIndex) const;

  /**
   * This method assumes that all of the elements stored in the vector
   * are BasicTypes.  If element is in this Vector, then the index of
   * element is returned in elementIndex.  If multiple instances of
   * element are found, then only the first one is returned.
   *
   * @param element the element to find
   * @param elementIndex the index of the element
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError findBasicType(const BasicType * const element, 
                         PRUint32 * const elementIndex) const;


  /** pop() returns the element at the largest valid index in the
   *  output parameter element, and deletes this element from the
   *  queue.  If this function succeeds, then *element is the void*
   *  pointer stored at the last position of the vector.  An error is
   *  returned if element is NULL or the queue is emtpy. */
  iMQError pop(void ** const element);

  /** size() returns the number of elements stored in the vector */
  PRUint32 size() const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Vector(const Vector& vector);
  Vector& operator=(const Vector& vector);
};


#endif // VECTOR_HPP
