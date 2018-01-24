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
 * @(#)ObjectVector.hpp	1.5 06/26/07
 */ 

#ifndef OBJECTVECTOR_HPP
#define OBJECTVECTOR_HPP

#include "../debug/DebugUtils.h"
#include "Vector.hpp"
#include "../basictypes/BasicType.hpp"
#include <nspr.h>


/**
 * ObjectVector is a trivial subclass of Vector.  It only stores
 * objects of type Object.  It is only used to ensure that the
 * destructors of these are called when the vector is destroyed.  
 */
class ObjectVector : public Vector {

public:
  ObjectVector();
  ObjectVector(PRUint32 initialSize);
  virtual ~ObjectVector();
  virtual void reset();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  ObjectVector(const ObjectVector& vector);
  ObjectVector& operator=(const ObjectVector& vector);
};

#endif // OBJECTVECTOR_HPP




