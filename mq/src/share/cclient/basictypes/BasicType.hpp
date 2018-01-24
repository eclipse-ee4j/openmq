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
 * @(#)BasicType.hpp	1.4 06/26/07
 */ 

#ifndef BASICTYPE_HPP
#define BASICTYPE_HPP

#include "TypeEnum.hpp"
#include "Object.hpp"
#include "../io/IMQDataInputStream.hpp"
#include "../io/IMQDataOutputStream.hpp"
#include "../error/ErrorCodes.h"

#include <stdio.h>
#include <limits.h>
#include <float.h>
#include <plhash.h>


/**
 * Basictype is the abstract type for all basic types (e.g. Boolean,
 * Byte, Integer, ...).  */
class BasicType : public Object {
public:
  /** @return true */
  virtual PRBool getIsBasicType() const;

  /** 
   * @return the TypeEnum corresponding of the implementing class
   * (e.g. Integer returns INTEGER_TYPE).
   */
  virtual TypeEnum     getType() const = 0;

  /** 
   * Creates a deep copy of the object and returns it.  If a deep copy
   * cannot be performed (e.g. out of memory), then NULL is returned.
   * The caller is responsible for freeing the object.
   *
   * @return a deep copy of the object
   */
  virtual BasicType *  clone() const = 0;

  /**
   * Returns PR_TRUE iff object is equal to this object.
   *
   * @param object the object to test for equality
   * @return PR_TRUE iff object is the same type as 'this' and they
   *   have the same value.
   */
  virtual PRBool       equals(const BasicType * const object) const = 0;
  
  /**
   * Returns a hash code for this object.
   */
  virtual PLHashNumber hashCode() const = 0;

  /**
   * Initializes the object from the binary value read from the stream in. 
   *
   * @param in the stream to read from
   */
  virtual iMQError     read(IMQDataInputStream * const in) = 0;

  /**
   * Writes the object's value out in binary form to out.
   *
   * @param out the stream to write to
   */
  virtual iMQError     write(IMQDataOutputStream * const out) const = 0;

  /** 
   * Print the object to file in text form.
   *
   * @param file to print the object to.
   */
  virtual iMQError     print(FILE * const file) const = 0;


  // These conversion routines are used by the Properties class The
  // BasicType class provides default definitions for these converion
  // routine which simply an IMQ_INVALID_TYPE_CONVERSION error.
  virtual iMQError     getBoolValue(PRBool * const value) const;
  virtual iMQError     getInt8Value(PRInt8 * const value) const;
  virtual iMQError     getInt16Value(PRInt16 * const value) const;
  virtual iMQError     getInt32Value(PRInt32 * const value) const;
  virtual iMQError     getInt64Value(PRInt64 * const value) const;
  virtual iMQError     getFloat32Value(PRFloat32 * const value) const;
  virtual iMQError     getFloat64Value(PRFloat64 * const value) const;
  virtual iMQError     getStringValue(const char ** const value) const;

  /**
   * @return a char* representation of the basic type.
   */
  virtual const char *       toString() = 0;


};


#endif // BASICTYPE_HPP




