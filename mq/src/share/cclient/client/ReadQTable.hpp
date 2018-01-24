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
 * @(#)ReadQTable.hpp	1.6 06/26/07
 */ 

#ifndef READQTABLE_HPP
#define READQTABLE_HPP

#include "../containers/BasicTypeHashtable.hpp"
#include "../basictypes/Monitor.hpp"
#include "../basictypes/Object.hpp"
#include "ReceiveQueue.hpp"
#include <nspr.h>

class Packet;

/** 
 * This class maps a consumerID to a ReceiveQueue.  A consumerID is a
 * unique number that is associated with a subscriber, receiver, or
 * ack receiver (i.e. ProtocolHandler).  It allows packets from the
 * broker to be demultiplexed to the appropriate place.  A
 * ReceiveQueue is where incoming packets are placed.  */
class ReadQTable : public Object {
private:
  /**
   * A hashtable that maps a consumerID to its ReceiveQueue
   */
  BasicTypeHashtable *  table;

  /**
   * Ensures synchronous access to the hashtable.
   */
  Monitor monitor;

  /** The next id  -  currently only used when for ack qtable */  
  PRInt64                          nextID;

  void getNextID(PRInt64 * const id);
  
public:
  /**
   * Initializes the hashtable so that the hashtable will automatically
   * delete each consumerID and ReceiveQueue when it is destructed.
   */
  ReadQTable();

  /**
   * Deconstructor that deletes the hashtable.
   */
  virtual ~ReadQTable();
  
  /**
   * Removes the ReceiveQueue associated with consumerID from the
   * ReadQTable.  The ReceiveQueue associated with consumerID is also
   * deleted.
   *  
   * @param consumerID is the consumerID to remove from the table
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError remove(const PRInt64 consumerID);

  /**
   * Adds the consumerID to receiveQ mapping to the ReadQTable.
   *
   * @param consumerID is the consumerID to add to the table
   * @param receiveQ is the ReceiveQueue to associate with consumerID
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError add(const PRInt64 consumerID, ReceiveQueue * const receiveQ);

  /**
   * Generate a consumerID and add the receiveQ to the ReadQTable.
   *
   * @param consumerID output parameter for consumerID 
   * @param receiveQ is the ReceiveQueue to associate with consumerID
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError add(PRInt64 * consumerID, ReceiveQueue * const receiveQ);


  /**
   * @param consumerID is the consumerID to add to the table
   * @param receiveQ is the output parameter for the ReceiveQueue associated 
   *        with consumerID
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError get(const PRInt64 consumerID, ReceiveQueue ** const receiveQ);

  MQError enqueue(const PRInt64 consumerID, Packet * const packet);

  MQError closeAll();


  /**
   * Static method that tests the general functionality of this class.
   */
  static MQError test();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  ReadQTable(const ReadQTable& readQTable);
  ReadQTable& operator=(const ReadQTable& readQTable);
};

#endif // READQTABLE_HPP

