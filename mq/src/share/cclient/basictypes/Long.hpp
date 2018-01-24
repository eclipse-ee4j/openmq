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
 * @(#)Long.hpp	1.6 06/26/07
 */ 
 
#ifndef LONG_HPP
#define LONG_HPP

#include "BasicType.hpp"
#include <prtypes.h>
#include <prlong.h>

/** Similar to Java's Long class */
static const PRUint32 LONG_DEFAULT_LO_VALUE = 0;
static const PRUint32 LONG_DEFAULT_HI_VALUE = 0;
/*static const PRInt32  LONG_MAX_STR_SIZE = 19; // "0xFFFFFFFFFFFFFFFF\0"*/
static const PRInt32  LONG_MAX_STR_SIZE = 21;   //see system types LONG_MAX 
class Long : public BasicType {
protected:
  PRInt64 value;
  char *  valueStr;

public:
  Long();
  Long(const PRInt64 value);
  virtual ~Long();
  void     setValue(const PRInt64 value);
  PRInt64  getValue() const;

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
  Long(const Long& longValue);
  Long& operator=(const Long& longValue);
};


#endif
