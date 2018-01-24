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
 * @(#)SessionMutex.hpp	1.3 06/26/07
 */ 

#ifndef SESSIONMUTEX_HPP
#define SESSIONMUTEX_HPP

#include <nspr.h>

#include "../basictypes/Monitor.hpp"
#include "../cshim/mqerrors.h"

/** This class uses Monitor to provide non-recursive locking */

class SessionMutex {
private:
  Monitor    monitor;

  PRThread * owner;

public:
  SessionMutex();
  virtual ~SessionMutex();

  MQError lock(PRUint32 timeoutMicroSeconds, PRBool * lockedByMe);
  MQError trylock(PRBool * lockedByMe);
  MQError trylock(PRThread * me, PRBool * lockedByMe);
  MQError unlock();
  MQError unlock(PRThread * me);

// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  SessionMutex(const SessionMutex& sessionMutex);
  SessionMutex& operator=(const SessionMutex& sessionMutex);
};


#endif // SESSIONMUTEX_HPP
