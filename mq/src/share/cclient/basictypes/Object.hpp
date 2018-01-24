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
 * @(#)Object.hpp	1.4 06/26/07
 */ 

#ifndef OBJECT_HPP
#define OBJECT_HPP

#include <nspr.h>
#include "../util/LogUtils.hpp"

/**
 * Object is the base object for objects stored in containers.  It is
 * primarily used to allow the subclasses destructor to be called on
 * delete, and to ensure that only methods of valid members are
 * accessed. */
class Object {
private:
  /** true iff this object has been constructed and not destructed.
   *  This is used primarily during debugging to ensure that we never
   *  envoke a method on a class that has been deleted. */
  PRBool validObject;

public:
  Object();

  /** 
   * Virtual destructor so the subclass's desctructor will get called. 
   */
  virtual ~Object();

  /**
   * @return true iff this object is a BasicType
   */
  virtual PRBool getIsBasicType() const;

  /**
   * @return true iff this object has not been deleted.  This is only
   * used to detect bugs.
   */
  PRBool isValidObject() const;
};


#endif // OBJECT_HPP
