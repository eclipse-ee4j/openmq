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
 * @(#)Float.hpp	1.6 06/26/07
 */ 

#ifndef FLOAT_HPP
#define FLOAT_HPP

#include "BasicType.hpp"
#include "../util/PRTypesUtils.h"

#include <prtypes.h>

// These should be members of the class, but 
// "only const static integral data members can be initialized inside a class"
static const PRFloat32 FLOAT_MIN_VALUE = FLT_MIN; 
static const PRFloat32 FLOAT_MAX_VALUE = FLT_MAX;
static const PRFloat32 FLOAT_DEFAULT_VALUE = 0;
static const PRInt32   FLOAT_MAX_STR_SIZE  = 20; // %g implies 6 significant digits => "-X.XXXXXe-XXX\0" => 20 is safe

/** Similar to Java's Float class */
class Float : public BasicType 
{
protected:
  // Only const static _integral_ data members can be initialized here
  // so we can't use PRFloat32

  PRFloat32 value;
  char * valueStr;

public:
  Float();
  Float(const PRFloat32 value);
  virtual ~Float();
  void       setValue(const PRFloat32 value);
  PRFloat32  getValue() const;

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

  virtual iMQError     getFloat32Value(PRFloat32 * const value) const;
  virtual iMQError     getFloat64Value(PRFloat64 * const value) const;
  virtual iMQError     getStringValue(const char ** const value) const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Float(const Float& floatValue);
  Float& operator=(const Float& floatValue);
};


#endif
