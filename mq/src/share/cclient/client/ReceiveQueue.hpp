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
 * @(#)ReceiveQueue.hpp	1.9 06/26/07
 */ 

#ifndef RECEIEVEQUEUE_H
#define RECEIEVEQUEUE_H

#include <nspr.h>
#include "../basictypes/Object.hpp"
#include "../basictypes/Monitor.hpp"
#include "../containers/ObjectVector.hpp"

//#ifndef PASSWORD_DECENTRAL
class MessageConsumer;
//#endif


/**
 * This class stores a queue of iMQ packets.  ReadChannel::dispatch enqueues
 * packets and the ProtocolHandler or receiver dequeues packets.  
 *
 * @see ReadChannel::dispatch
 * @see MessageConsumer::receive
 */
class ReceiveQueue : public Object {
private:
  /**
   * A queue to hold the packets.
   */
  ObjectVector  msgQueue;

  /**
   * True iff the ReceiveQueue is closed.
   */
  PRBool        isClosed;

  PRBool        isStopped;

  /**
   * True iff the receiver is in the process of receiving a message
   */
  PRBool        receiveInProgress;

  /**
   * Ensures synchronous access to the packet queue.
   */
  Monitor       monitor;

  /** for sync-receiving message arrival notification,  NULL otherwise  */
  MessageConsumer * syncReceiveConsumer;

  /**
   * references and closeWaited are for MessageConsumer::close */

  /** number of callers currently in dequeueWait() */
  PRInt32      references;
  /** if true a close() call is waiting for references == 0 */
  PRBool       closeWaited;

  /**
   * Initializes all member variables.
   */
  void init();

  /** If a thread is accessing the queue, then this method blocks
      until it is done */
  void waitUntilReceiveIsDone();
  
         
public:

  /** Constructor */
  ReceiveQueue();

  ReceiveQueue(PRUint32 initialSize);

  /** Destructor */
  ~ReceiveQueue();

  /** Waits for an object to be enqueued, and then returns that
   *  object.  NULL is returned if there was an exception
   *  @return the next object in the queue */
  Object * dequeueWait();

  /** Waits for the specified interval for an object to be enqueued,
   *  and then returns that object.  NULL is returned if there was an
   *  exception
   *  @param timeoutMicroSeconds the number of microseconds to wait for
   *  an object to be enqueued.
   *  @return the next object in the queue */
  Object * dequeueWait(const PRUint32 timeoutMicroSeconds);

  /** Enqueues an object and notifies any thread that has called
      dequeueWait */
  iMQError  enqueueNotify(Object * const obj);
  
  /** Called when the receiving thread, the thread that calls dequeue
   *  or dequeueWait is done with receiving a message */
  void receiveDone();

  void stop();
  void start();
  void reset();

  /** Enqueues an object with no special synchronization */
  iMQError  enqueue(Object * const obj);

  /** Dequeues an object with no special synchronization */
  Object *  dequeue();

  /** Close the ReceiveQueue */
  void      close();
  /** wait until references == 0 - called by MessageConsumer::close() */
  void      close(PRBool wait);

  /** Accessors */
  PRBool    getIsClosed();

  PRUint32 size();
  PRBool isEmpty();

  void setSyncReceiveConsumer(MessageConsumer * consumer);

  
  /** Static test method */
  static    iMQError test();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  ReceiveQueue(const ReceiveQueue& queue);
  ReceiveQueue& operator=(const ReceiveQueue& queue);

};


#endif // RECEIEVEQUEUE_H
