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
 * @(#)Double.hpp	1.5 06/26/07
 */ 

#ifndef DOUBLE_HPP
#define DOUBLE_HPP

#include "BasicType.hpp"
#include "../util/PRTypesUtils.h"

#include <prtypes.h>

/** Similar to Java's Double class */
static const PRFloat64 DOUBLE_DEFAULT_VALUE = 0.0;
static const PRInt32   DOUBLE_MAX_STR_SIZE  = 20; // %g implies 6 significant digits => "-X.XXXXXe-XXX\0" => 20 is safe
class Double : public BasicType 
{
protected:
  PRFloat64 value;
  char * valueStr;

public:
  Double();
  Double(const PRFloat64 value);
  virtual ~Double();
  void       setValue(const PRFloat64 value);
  PRFloat64  getValue() const;

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
  Double(const Double& doubleValue);
  Double& operator=(const Double& doubleValue);
};


#endif
