/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)SessionQueueReader.hpp	1.8 06/26/07
 */ 

#ifndef SESSIONQUEUEREADER_HPP
#define SESSIONQUEUEREADER_HPP

#include "../error/ErrorCodes.h"
#include "../basictypes/Runnable.hpp"
#include "../basictypes/Monitor.hpp"

class Connection; 
class Session;   
class Packet;
class ReceiveQueue;
class Message;

/**
 *
 */
class SessionQueueReader : public Runnable {
private:
  Session *      session;
  Connection   * connection;
  ReceiveQueue * sessionQueue;
  PRThread     * readerThread;
  Message      * currentMessage;

  PRInt64 connectionID;

  MQError initializationError;

  PRBool isClosed;
  PRBool isAlive;
  Monitor        monitor;

  void init();

  MQError deliver(Packet * const packet);
  
public:

  SessionQueueReader(Session * const sessionArg);

  virtual ~SessionQueueReader();

  /** @return IMQ_SUCCESS if the reader started successfully, and an 
  *   error otherwise */
  MQError getInitializationError() const;


  /**
   * The entry point for the reader thread. 
   */
  void run();
  void close();
  PRThread * getReaderThread() const;
  Message * getCurrentMessage() const;

  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  SessionQueueReader(const SessionQueueReader& sessionQueueReader);
  SessionQueueReader& operator=(const SessionQueueReader& sessionQueueReader);
};

#endif
