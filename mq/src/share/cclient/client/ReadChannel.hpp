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
 * @(#)ReadChannel.hpp	1.6 06/26/07
 */ 

#ifndef READCHANNEL_HPP
#define READCHANNEL_HPP

#include "../error/ErrorCodes.h"
#include "../basictypes/Runnable.hpp"
#include "../basictypes/Monitor.hpp"
#include "../io/Packet.hpp"

class Connection; // because we can't include Connection.hpp

/**
 * This class is responsible for reading packets from the broker and
 * then dispatching them to the correct ReceiveQueue based on the
 * consumerID in the packet.
 *
 * @see ProtocolHandler::readPacket 
 */
class ReadChannel : public Runnable {
private:
  /** The connection for which this ReadChannel was created. */
  Connection * connection;

  /** True iff a GOODBYE or GOODBYE_REPLY packet has been received.  */
  PRBool receivedGoodBye;

  /** True iff the reader thread was successfully started and running  */
  PRBool isAlive;

  /** True iff an exception occurred in ReadChannel::run */
  PRBool abortConnection;

  /** True if Connection has requested the ReadChannel to exit */
  PRBool closeConnection;

  Monitor monitor;

  MQError initializationError;

  /** The ID of the connection that created this ReadChannel.  It is only used 
   *  so information can be logged after the connection goes away. */
  PRInt64 connectionID;

  /** Initializes all member variables to NULL.  */
  void init();

  /**
   * Using the type of the packet and the consumerID in the packet, this 
   * method dispatches the packet to the appropriate ReceiveQueue.
   * 
   * @param packet the iMQ packet to dispatch
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  iMQError dispatch(Packet * const packet);

  PRThread * readerThread;
  
public:
  /**
   * Starts the reader thread.
   * @param connection the connection on which this ReadChannel was created.
   * @param Connection:;startThread
   */
  ReadChannel(Connection * const connection);

  /** Destructor.  It assumes that Connection has already called
   *  exitConnection */
  virtual ~ReadChannel();

  /** @return IMQ_SUCCESS if the reader started successfully, and an 
  *   error otherwise */
  iMQError getInitializationError() const;

  /**
   * The entry point for the reader thread.  Until the connection
   * closes, this method reads an iMQ packet from the wire and then
   * calls dispatch to place the packet on the correct ReceiveQueue.
   * If an exception occurs, it calls Connection::exitConnection to
   * close down the connection.
   * 
   * @see dispatch
   * @see Connection::exitConnection
   */
  void run();

  /**
   * Signals the reader thread to exit, and then waits for it to exit.
   * When this method returns, the reader thread has exited.  
   */
  void exitConnection(); 

  PRThread * getReaderThread() const;
  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  ReadChannel(const ReadChannel& readChannel);
  ReadChannel& operator=(const ReadChannel& readChannel);
};

#endif
