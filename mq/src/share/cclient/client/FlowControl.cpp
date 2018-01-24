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
 * @(#)FlowControl.cpp	1.4 06/26/07
 */ 

#include "FlowControl.hpp"
#include "Connection.hpp"
#include "../util/LogUtils.hpp"

/*
 *
 */
FlowControl::FlowControl(Connection * const connectionArg)
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( connectionArg != NULL );
  this->connection = connectionArg;
  this->unDeliveredMsgCount = 0;
  this->resumeRequested = PR_FALSE;
}

/*
 *
 */
FlowControl::~FlowControl()
{
  CHECK_OBJECT_VALIDITY();

  this->connection = NULL;
  this->unDeliveredMsgCount = 0;
  this->resumeRequested = PR_FALSE;
}

/*
 *
 */
void
FlowControl::messageReceived()
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();
    this->unDeliveredMsgCount++;

    LOG_FINEST(( CODELOC, FLOW_CONTROL_LOG_MASK, connection->id(), IMQ_SUCCESS,
                 "FlowControl::messageReceived().  msgs/watermark = %d/%d",
                 this->unDeliveredMsgCount, 
                 connection->getFlowControlWaterMark() ));
    
  monitor.exit();
}


/*
 *
 */
void
FlowControl::messageDelivered()
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();
    this->unDeliveredMsgCount--;

    LOG_FINEST(( CODELOC, FLOW_CONTROL_LOG_MASK, connection->id(), IMQ_SUCCESS,
                 "FlowControl::messageDelivered().  msgs/watermark = %d/%d.  "
                 "Trying to resume the flow.",
                 this->unDeliveredMsgCount, 
                 connection->getFlowControlWaterMark() ));

    this->tryResume();
  monitor.exit();
}


/*
 *
 */
void
FlowControl::requestResume()
{
  CHECK_OBJECT_VALIDITY();

  monitor.enter();
    this->resumeRequested = PR_TRUE;

    LOG_FINEST(( CODELOC, FLOW_CONTROL_LOG_MASK, connection->id(), IMQ_SUCCESS,
                 "FlowControl::requestResume().  msgs/watermark = %d/%d.  "
                 "Trying to resume the flow.",
                 this->unDeliveredMsgCount,
                 connection->getFlowControlWaterMark() ));

    this->tryResume();
  monitor.exit();
}


// This method does not have to be syncrhonized because it is only
// called from synchronized methods.
void
FlowControl::tryResume()
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;

  if (!shouldResume()) {
    return;
  }

  errorCode = connection->resumeFlow();
  if (errorCode == IMQ_SUCCESS) {
    resumeRequested = PR_FALSE;
  }
}


// This method does not have to be syncrhonized because it is only
// called from synchronized methods.
PRBool
FlowControl::shouldResume()
{
  CHECK_OBJECT_VALIDITY();

  PRBool doResume = PR_FALSE;
  
  // If the broker hasn't stopped sending messages, then we shouldn't
  // request it to resume sending messages.
  if (!this->resumeRequested) {
    doResume = PR_FALSE;
  }
  
  // If the connection is not flow control limited, then always
    // resume the flow.
  else if (!connection->getFlowControlIsLimited()) {
    doResume = PR_TRUE;
  }
  
  // Otherwise, resume if the number of undelivered messages is below the
  // watermark
  else {
    doResume = 
      this->unDeliveredMsgCount < connection->getFlowControlWaterMark();
  }
  
  return doResume;
}


