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
 * @(#)FlowControl.hpp	1.3 06/26/07
 */ 

#ifndef FLOWCONTROL_HPP
#define FLOWCONTROL_HPP

#include <nspr.h>
#include "../basictypes/Object.hpp"
#include "../basictypes/Monitor.hpp"

class Connection;

/** Handles flow control with the broker. It keeps track of the number
 *  of packets that have been received but not delivered.  If the
 *  broker pauses the flow of messages, then the FlowControl object
 *  resumes the flow after the number of received, but not delivered
 *  messages falls below the watermark. */
class FlowControl : public Object {
private:

  /** The connection that created this object */
  Connection * connection;

  /** Ensures synchronous access to member variables */
  Monitor monitor;

  /** The number of messages that have been received but not delivered */
  PRInt32 unDeliveredMsgCount;

  /** True iff a resume message has been received, which implies the broker
      is asking to resume sending messages. */
  PRBool resumeRequested;

  /** If the number of undelivered messages is below the water mark,
      it resumes the connection to the broker. */
  void tryResume();

  /** Determines if the connection should be resumed based on the number
      of undelivered messages and the watermark. */
  PRBool shouldResume();

public:
  /** Constructor. */
  FlowControl(Connection * const connection);

  /** Destructor. */
  ~FlowControl();

  /** This method is called when a message is received */
  void messageReceived();

  /** This method is called when a message is delivered */
  void messageDelivered();

  /** This method is called when the broker requests that we resume
      the connection */
  void requestResume();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  FlowControl(const FlowControl& flowControl);
  FlowControl& operator=(const FlowControl& flowControl);
};

#endif // FLOWCONTROL_HPP
