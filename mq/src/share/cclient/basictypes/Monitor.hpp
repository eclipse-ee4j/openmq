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
 * @(#)Monitor.hpp	1.4 06/26/07
 */ 

#ifndef MONITOR_HPP
#define MONITOR_HPP

#include <nspr.h>

/** This class is used to ensure synchronous access to shared data. It
    delegates to the NSPR monitor, PRMonitor*/
class Monitor {
private:
  /** The NSPR monitor */
  PRMonitor *   monitor;

  /** The nested depth of the monitor.  This is only used for detecting
   *  errors.  For example, if the monitor is deleted when depth != 0.*/
  PRInt32 depth;

public:
  Monitor();
  virtual ~Monitor();

  /** Enter a critical section */
  void enter();

  /** Leave a critical section */
  void exit();

  /** Wait until the monitor is signalled */
  void wait();

  /** Wait until the monitor is signalled or timeout expires */
  void wait(const PRIntervalTime timeout);

  /** Signal one waiter */
  void notifyOne();
  
  /** Signal all waiters */
  void notifyAll();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Monitor(const Monitor& monitor);
  Monitor& operator=(const Monitor& monitor);
};


#endif // MONITOR_HPP
