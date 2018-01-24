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
 * @(#)Byte.hpp	1.7 06/26/07
 */ 

#ifndef BYTE_HPP
#define BYTE_HPP

#include "BasicType.hpp"
#include <prtypes.h>

/** Similar to Java's Byte class */
static const PRInt8   BYTE_MIN_VALUE = SCHAR_MIN;
static const PRInt8   BYTE_MAX_VALUE = SCHAR_MAX;
static const PRInt8   BYTE_DEFAULT_VALUE = 0;
static const PRInt32  BYTE_MAX_STR_SIZE  = 5; // "-128\0"
class Byte : public BasicType 
{
protected:
  /** Size of the string representation of this object */
  PRInt8 value;

  char * valueStr;

public:
  Byte();
  Byte(const PRInt8 value);
  virtual ~Byte();
  void   setValue(const PRInt8 value);
  PRInt8 getValue() const;

  //
  // virtual functions to implement from BasicType
  //
  virtual TypeEnum      getType() const;
  virtual PRBool        equals(const BasicType * const object) const;
  virtual PLHashNumber  hashCode() const;
  virtual iMQError      read(IMQDataInputStream * const in);
  virtual iMQError      write(IMQDataOutputStream * const out) const;
  virtual iMQError      print(FILE * const file) const;
  virtual BasicType *   clone() const;

  virtual const char *  toString();

  virtual iMQError     getInt8Value(PRInt8 * const value) const;
  virtual iMQError     getInt16Value(PRInt16 * const value) const;
  virtual iMQError     getInt32Value(PRInt32 * const value) const;
  virtual iMQError     getInt64Value(PRInt64 * const value) const;
  virtual iMQError     getStringValue(const char ** const value) const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Byte(const Byte& byteValue);
  Byte& operator=(const Byte& byteValue);
};


#endif
