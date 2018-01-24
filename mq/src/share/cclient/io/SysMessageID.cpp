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
 * @(#)SysMessageID.cpp	1.6 06/26/07
 */ 

#include "SysMessageID.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"

#include <prlong.h>


/*
 * Construct an unititialized system message ID. It is assumed
 * the caller will set the fields either explicitly or via
 * readID()
 */
SysMessageID::SysMessageID()
{
  CHECK_OBJECT_VALIDITY();

  this->init();
}

/*
 *
 */
SysMessageID::SysMessageID(const SysMessageID& sysMessageID)
{
  CHECK_OBJECT_VALIDITY();
  this->init();

  this->sequence  = sysMessageID.sequence;
  this->port      = sysMessageID.port;
  this->timestamp = sysMessageID.timestamp;
  this->ip        = sysMessageID.ip;
  this->msgIDStr  = NULL;
}

/*
 *
 */
SysMessageID& 
SysMessageID::operator=(const SysMessageID& sysMessageID)
{
  CHECK_OBJECT_VALIDITY();

  this->reset();

  this->sequence  = sysMessageID.sequence;
  this->port      = sysMessageID.port;
  this->timestamp = sysMessageID.timestamp;
  this->ip        = sysMessageID.ip;
  this->msgIDStr  = NULL;

  return *this;
}

/*
 *
 */
SysMessageID::~SysMessageID()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}

/*
 *
 */
void
SysMessageID::init()
{
  sequence     = 0;
  port         = 0;
  timestamp    = 0;
  msgIDStr     = NULL;
}

/*
 * Clears the message id
 */
void
SysMessageID::reset()
{
  CHECK_OBJECT_VALIDITY();
  ip.reset();
  DELETE( msgIDStr );
  init();
}

/*
 * 
 */
PRUint32
SysMessageID::getSequence() const
{
  CHECK_OBJECT_VALIDITY();

  return sequence;
}

/*
 * 
 */
PRUint32
SysMessageID::getPort() const
{
  CHECK_OBJECT_VALIDITY();

  return port;
}

/*
 * 
 */
PRUint64
SysMessageID::getTimestamp() const
{
  CHECK_OBJECT_VALIDITY();

  return timestamp;
}


/*
 * Returns the IPv6 address
 */
iMQError
SysMessageID::getIPv6Address(PRUint8 * const ipv6Addr) const
{
  CHECK_OBJECT_VALIDITY();

  return ip.getIPv6Address(ipv6Addr);
}

/*
 * 
 */
void
SysMessageID::setSequence(const PRUint32 sequenceArg) 
{
  CHECK_OBJECT_VALIDITY();

  this->sequence = sequenceArg;
}

/*
 * 
 */
void
SysMessageID::setPort(const PRUint32 portArg) 
{
  CHECK_OBJECT_VALIDITY();

  this->port = portArg;
}

/*
 * 
 */
void
SysMessageID::setTimestamp(const PRUint64 timestampArg)
{
  CHECK_OBJECT_VALIDITY();

  this->timestamp = timestampArg;
}

/*
 * Sets the IPv6 address
 */
void
SysMessageID::setIPv6Address(const PRUint8 * const ipv6Addr) 
{
  CHECK_OBJECT_VALIDITY();

  ip.setAddressFromIPv6Address(ipv6Addr);
}

/*
 * Sets the IPv6 address
 */
void
SysMessageID::setIPv6Address(const IPAddress * const ipv6Addr) 
{
  CHECK_OBJECT_VALIDITY();

  if (ipv6Addr != NULL) {
    this->ip = *ipv6Addr;
  }
}

/*
 * 
 */
iMQError
SysMessageID::readID(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );
  reset();

  //
  // Read in the timestamp, ip address, port, and sequence
  //
  RETURN_IF_ERROR( in->readUint64(&(this->timestamp)) );
  RETURN_IF_ERROR( ip.readAddress(in) );
  RETURN_IF_ERROR( in->readUint32(&(this->port)) );
  RETURN_IF_ERROR( in->readUint32(&(this->sequence)) );

  return IMQ_SUCCESS;
}


/*
 * 
 */
iMQError
SysMessageID::writeID(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  //
  // Write in the timestamp, ip address, port, and sequence
  //
  RETURN_IF_ERROR( out->writeUint64(this->timestamp) );
  RETURN_IF_ERROR( ip.writeAddress(out) );
  RETURN_IF_ERROR( out->writeUint32(this->port) );
  RETURN_IF_ERROR( out->writeUint32(this->sequence) );
  
  return IMQ_SUCCESS;
}



/*
 *
 */
const int MAX_ID_STR_LEN = 1000;
const UTF8String * 
SysMessageID::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (msgIDStr != NULL) {
    return msgIDStr;
  }

  char idStr[MAX_ID_STR_LEN];

  int bytesWritten = 0;
  bytesWritten = PR_snprintf( idStr, sizeof(idStr), "ID:%u-%s-%u-%lld", 
                                                 sequence,
                                                 ip.toCharStr(),
                                                 port,
                                                 timestamp );
  msgIDStr = new UTF8String(idStr);
  return msgIDStr;
}






/*
 *
 */
PRBool 
SysMessageID::equals(const SysMessageID * const id) const
{
  CHECK_OBJECT_VALIDITY();

  if (id == NULL) {
    return PR_FALSE;
  }

  return 
    (this->sequence == id->sequence)        &&
    (this->port     == id->port)            &&
    (LL_EQ(this->timestamp, id->timestamp)) &&
    (this->ip.equals(&(id->ip)));
}
