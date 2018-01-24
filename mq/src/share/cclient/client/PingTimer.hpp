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
 * @(#)PingTimer.hpp	1.4 06/26/07
 */ 

#ifndef PINGTIMER_HPP
#define PINGTIMER_HPP

#include "../error/ErrorCodes.h"
#include "../basictypes/Runnable.hpp"
#include "../basictypes/Monitor.hpp"

class Connection;

/**
 *
 */
class PingTimer : public Runnable {
private:
  /** The connection for which this PingTimer was created. */
  Connection * connection;

  PRIntervalTime pingInterval;

  /** True iff the ping thread was successfully started and running  */
  PRBool isAlive;

  /** True if the PingTimer is asked to exit */
  PRBool exit;

  Monitor monitor;

  MQError initializationError;

  /** The ID of the connection that created this PingTimer. */ 
  PRInt64 connectionID;

  void init();

  PRThread * pingThread;
  
public:
  /**
   * @param connection The connection on which this PingTimer was created.
   * @param interval The ping interval in millisec
   */
  PingTimer(Connection * const connection);

  virtual ~PingTimer();

  /** @return MQ_SUCCESS if the ping thread started successfully, error otherwise 
   */
  MQError getInitializationError() const;

  /**
   */
  void run();

  /**
   * Signals and wait the ping timer thread to exit
   */
  void terminate(); 

private:
  PingTimer(const PingTimer& pingTimer);
  PingTimer& operator=(const PingTimer& pingTimer);
};

#endif
