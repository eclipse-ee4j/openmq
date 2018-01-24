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
 * @(#)StringKeyHashtable.hpp	1.1 10/17/07
 */ 

#ifndef STRINGKEYHASHTABLE_HPP
#define STRINGKEYHASHTABLE_HPP

#include "BasicTypeHashtable.hpp"
#include "../basictypes/Monitor.hpp"
#include "../basictypes/Object.hpp"
#include <nspr.h>

/** 
 * A hashtable with UTF8String as key and a monitor for 
 * concurrent access */

class StringKeyHashtable : public Object {
private:

  BasicTypeHashtable *  table;

  /**
   * Protects table for synchronous access
   */
  Monitor monitor;

public:
  /**
   * Create a hashtable that auto-delete key and values
   */
  StringKeyHashtable();

  /**
   * Deconstructor that deletes the hashtable.
   */
  virtual ~StringKeyHashtable();
  
  /**
   */
  MQError remove(const char * key);

  /**
   */
  MQError add(const char * key, Object * const value);


  MQError get(const char *key, Object ** const value);


//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  StringKeyHashtable(const StringKeyHashtable& tb);
  StringKeyHashtable& operator=(const StringKeyHashtable& tb);
};

#endif // STRINGKEYHASHTABLE_HPP

