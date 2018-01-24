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
 * @(#)ProducerFlow.hpp	1.4 06/26/07
 */ 

#ifndef PRODUCERFLOW_HPP
#define PRODUCERFLOW_HPP


#include "Message.hpp"
#include "../basictypes/Object.hpp"
#include "../basictypes/Monitor.hpp"

class Message;

enum FlowState { UNDER_LIMIT, ON_LIMIT, OVER_LIMIT };

/**
 *
 */
class ProducerFlow : public Object {
private:
  PRInt64 producerID;
  Long producerIDLong; //for logging efficiency only 

  //after sending this many bytes the  producer should pause the flow
  PRInt64 chunkBytes;

  //after sending this many messages the producer should pause the flow
  PRInt32 chunkSize; 

  PRInt32 sentCount;
  PRInt32 references;

  PRBool  isClosed;
  MQError closeReason;

  Monitor        monitor;

  FlowState checkFlowLimit();

public:
  ProducerFlow();
  virtual ~ProducerFlow();

  void setProducerID(PRInt64 producerIDArg);
  void setChunkSize(PRInt32 chunkSizeArg);
  void setChunkBytes(PRInt64 chunkBytesArg);

  PRInt64 getProducerID() const;

  MQError checkFlowControl(Message * message);

  void resumeFlow(PRInt64 chunkBytes, PRInt32 chunkSize);

  MQError acquireReference();
  PRBool releaseReference();


  void close(MQError reason);

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  ProducerFlow(const ProducerFlow& producerFlow);
  ProducerFlow& operator=(const ProducerFlow& producerFlow);
  
};

#endif  // PRODUCERFLOW_HPP
