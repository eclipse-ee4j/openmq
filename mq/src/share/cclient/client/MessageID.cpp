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
 * @(#)MessageID.cpp	1.7 06/26/07
 */ 

#include "MessageID.hpp"
#include "../util/UtilityMacros.h"

MessageID::MessageID(const Message * const message)
{
  CHECK_OBJECT_VALIDITY();

  consumerID = 0;
  ASSERT( message != NULL );
  if (message != NULL) {
    consumerID = message->getConsumerID();
    this->sysMessageID = *(message->getSystemMessageID());
  }
}

MessageID::~MessageID()
{
  CHECK_OBJECT_VALIDITY();

  consumerID = 0;
}

PRBool
MessageID::equals(const Message * const message)
{
  CHECK_OBJECT_VALIDITY();

  if (message == NULL) {
    return PR_FALSE;
  }
  
  return (LL_EQ(this->consumerID, message->getConsumerID()) != 0) &&
    this->sysMessageID.equals(message->getSystemMessageID());
}

iMQError
MessageID::write(IMQDataOutputStream * const outStream) const
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  NULLCHK( outStream );

  ERRCHK( outStream->writeInt64(this->consumerID) );
  ERRCHK( this->sysMessageID.writeID(outStream) );
  
  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}
