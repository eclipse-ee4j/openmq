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
 * @(#)Object.cpp	1.4 06/26/07
 */ 

#include "Object.hpp"


Object::Object()
{
  this->validObject = PR_TRUE;
}


Object::~Object()
{
  this->validObject = PR_FALSE;
}



PRBool
Object::isValidObject() const
{
  // We use the == PR_TRUE test because if validObject gets changed
  // to anything other than PR_TRUE or PR_FALSE, then the object has been
  // freed or there is something wrong.
  return (this->validObject == PR_TRUE);
}


PRBool
Object::getIsBasicType() const
{
  // Objects are not BasicTypes by default
  return PR_FALSE;
}
