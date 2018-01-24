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
 * @(#)TypeEnum.hpp	1.4 06/26/07
 */ 

#ifndef TYPEENUM_HPP
#define TYPEENUM_HPP

#include <prtypes.h>

/** An enumeration of the types that appear in a serialized Java
 *  hashtable.  Do not change the order of the enum because various
 *  lookup tables depend on them.  */
enum TypeEnum { 
  BOOLEAN_TYPE,          // = 0,

  BYTE_TYPE,             // = 1,
  SHORT_TYPE,            // = 2,
  INTEGER_TYPE,          // = 3,
  LONG_TYPE,             // = 4,

  FLOAT_TYPE,            // = 5
  DOUBLE_TYPE,           // = 6,

  UTF8_STRING_TYPE,      // = 7,
  UTF8_LONG_STRING_TYPE, // = 8,

  HASHTABLE_TYPE,        // = 9,

  NUMBER_TYPE,           // = 10,
  UNKNOWN_TYPE,          // = 11,
  NULL_TYPE              // = 12
};

#endif // TYPEENUM_HPP
