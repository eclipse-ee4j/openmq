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
 * @(#)MessageConsumerTable.hpp	1.5 06/26/07
 */ 

#ifndef MESSAGECONSUMERTABLE_HPP
#define MESSAGECONSUMERTABLE_HPP

#include "../containers/BasicTypeHashtable.hpp"
#include "../basictypes/Monitor.hpp"
#include "../basictypes/Object.hpp"
#include <nspr.h>

class MessageConsumer;

/** 
 * This class maps a consumerID to a MessageConsumer. */ 

class MessageConsumerTable : public Object {
public:
  /**
   *
   */
  enum MessageConsumerOP {
    START_CONSUMER       = 0,
    STOP_CONSUMER        = 1,
    CLOSE_CONSUMER       = 2,
    UNSUBSCRIBE_DURABLE  = 3,
    RECOVER_RECEIVEQUEUE = 4
  };

private:
  /**
   * The hashtable that maps a consumerID to its MessageConsumer 
   */
  BasicTypeHashtable *  table;

  /**
   * Protects table for synchronous access
   */
  Monitor monitor;

public:
  /**
   * Initializes the hashtable so that the hashtable will automatically
   * delete each consumerID on destruction 
   */
  MessageConsumerTable();

  /**
   * Deconstructor that deletes the hashtable.
   */
  virtual ~MessageConsumerTable();
  
  /**
   * Removes the MessageConsumer associated with consumerID from the
   * MessageConsumerTable.
   *  
   * @param consumerID is the consumerID to remove from the table
   * @return MQ_SUCCESS if successful and an error otherwise 
   */
  MQError remove(PRUint64 consumerID, MessageConsumer ** const consumer);

  /**
   * Adds the consumerID to MessageConsumer mapping 
   *
   * @param consumerID is the consumerID to add to the table
   * @param consumer is the MessageConsumer to associate with consumerID
   * @return IMQ_SUCCESS if successful and an error otherwise 
   */
  MQError add(PRUint64 consumerID, MessageConsumer * const consumer);


  /**
   * @param consumerID is the consumerID to add to the table
   * @param consumer is the MessageConsumer to return 
   * @return IMQ_SUCCESS if successful and an error otherwise 
   */
  MQError get(PRUint64 consumerID, MessageConsumer ** const consumer);

  MQError operationAll(MessageConsumerOP op, const void * opData);


//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  MessageConsumerTable(const MessageConsumerTable& readQTable);
  MessageConsumerTable& operator=(const MessageConsumerTable& readQTable);
};

#endif // MESSAGECONSUMERTABLE_HPP

