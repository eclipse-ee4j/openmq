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
 * @(#)Monitor.cpp	1.4 06/26/07
 */ 

#include "Monitor.hpp"
#include "../util/UtilityMacros.h"

/*
 *
 */
Monitor::Monitor()
{
  /* This is a little worrisome.  If it fails, then we really don't
     have any way to know about it. */
  this->monitor = PR_NewMonitor();
  this->depth = 0;
}

/*
 *
 */
Monitor::~Monitor()
{
  // Sometimes the following assert would fail, but when examining the
  // code this->depth would be 0 because a separate thread changed the value
  // by calling exit().  This allows us to see what the value was before.
  int curDepth = this->depth;
  ASSERT( this->depth == 0 );
  
  PR_DestroyMonitor(this->monitor);
  this->depth = -1000;
}


/*
 *
 */
void
Monitor::enter()
{
  PR_EnterMonitor(this->monitor);
  ASSERT( this->depth >= 0 );
  this->depth++;
}

/*
 *
 */
void
Monitor::exit()
{
  this->depth--;
  ASSERT( this->depth >= 0 );
  PR_ExitMonitor(this->monitor);
}



/*
 *
 */
void
Monitor::wait()
{
  ASSERT( this->depth > 0 );
  this->wait(PR_INTERVAL_NO_TIMEOUT);
}

/*
 *
 */
void 
Monitor::wait(const PRIntervalTime timeout)
{
  ASSERT( this->depth > 0 );

  PR_Wait(this->monitor, timeout);
}

/*
 *
 */
void
Monitor::notifyOne()
{
  ASSERT( this->depth > 0 );
  PR_Notify(this->monitor);
}



/*
 *
 */
void
Monitor::notifyAll()
{
  ASSERT( this->depth > 0 );
  PR_NotifyAll(this->monitor);
}

