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
 * @(#)Short.hpp	1.5 06/26/07
 */ 

#ifndef SHORT_HPP
#define SHORT_HPP

#include "BasicType.hpp"
#include <prtypes.h>

/** Similar to Java's Short class */
static const PRInt16 SHORT_MIN_VALUE     = SHRT_MIN;
static const PRInt16 SHORT_MAX_VALUE     = SHRT_MAX;
static const PRInt16 SHORT_DEFAULT_VALUE = 0;
static const PRInt32 SHORT_MAX_STR_SIZE  = 7; // "-32768\0"
class Short : public BasicType 
{
protected:

  PRInt16 value;
  char * valueStr;

public:
  Short();
  Short(const PRInt16 value);
  virtual ~Short();
  void     setValue(const PRInt16 value);
  PRInt16  getValue() const;

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
  Short(const Short& shortValue);
  Short& operator=(const Short& shortValue);
};


#endif
