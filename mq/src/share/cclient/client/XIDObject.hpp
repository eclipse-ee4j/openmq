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
 * @(#)XIDObject.hpp	1.1 10/17/07
 */ 

#ifndef XIDOBJECT_HPP
#define XIDOBJECT_HPP

#include "../basictypes/Object.hpp"
#include "../cshim/xa.h"
#include "../io/IMQDataOutputStream.hpp" 
#include "../io/IMQDataInputStream.hpp" 

/**
 */
class XIDObject : public Object {
private:
  XID xid;

  char * xidStr;

public:
  XIDObject();
  virtual ~XIDObject();

  void init();
  void reset();

  MQError write(IMQDataOutputStream * const out) const;

  MQError read(IMQDataInputStream * const in);
  MQError copy(const XID * xid);
  XID * getXID();

  char * toString();

  static PRInt32 xidSize();
  static  char * toString(XID *xid);

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  XIDObject(const XIDObject& xidobj);
  XIDObject& operator=(const XIDObject&  xidobj);
};

#endif  // XIDOBJECT_HPP
