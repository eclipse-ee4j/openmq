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
 * @(#)SysMessageID.hpp	1.3 06/26/07
 */ 

#ifndef SYSMESSAGEID_HPP
#define SYSMESSAGEID_HPP

#include <prtypes.h>
#include "../net/IPAddress.hpp"
#include "../error/ErrorCodes.h"
#include "../io/IMQDataInputStream.hpp"
#include "../io/IMQDataOutputStream.hpp"
#include "../basictypes/UTF8String.hpp"
#include "../basictypes/Object.hpp"

/**
 * This class stores the sequence number, port number, timestamp, and ip address
 * of the packet header.  This is enough information to uniquely identify each
 * packet.  
 */
class SysMessageID : public Object {
protected:
  //
  // These four fields unique identify a message.
  //

  /** The iMQ sequence number of the packet. */
  PRUint32        sequence;

  /** The transport port number of the packet. */
  PRUint32        port;

  /** The timestamp of the packet. */
  PRUint64        timestamp;

  /** The ip address of the packet.  It is stored in IPv6 format.*/
  IPAddress       ip;


  /** A string representation of the message ID */
  UTF8String *    msgIDStr;

  /** Initializes all member variables */
  void init();

public:
  SysMessageID();

  SysMessageID(const SysMessageID& sysMessageID);
  SysMessageID& operator=(const SysMessageID& sysMessageID);

  ~SysMessageID();

  /**
   * This method resets each field of the packet 
   */
  void reset();

  /**
   * This method reads the ID from the input stream in.
   *
   * @param in is the input stream to read from
   * @returns IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError readID(IMQDataInputStream * const in);

  /**
   * This method writes the ID to the output stream out.
   *
   * @param out is the output stream to write to
   * @returns IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError writeID(IMQDataOutputStream * const out) const;

  /**
   * This method returns the iMQ sequence number of the packet.
   *
   * @returns the iMQ sequence number of the packet in host order format.
   */
  PRUint32 getSequence() const;

  /**
   * This method returns the port number of the packet.
   *
   * @returns the port number of the packet in host order format.
   */
  PRUint32 getPort() const;

  /**
   * This method returns the timestamp of the packet.
   *
   * @returns the timestamp of the packet in host order format.
   */
  PRUint64 getTimestamp() const;


  /**
   * This method returns the IP address of the packet in IPv6 format.
   *
   * @param ipv6Addr is the buffer where the address is placed
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  iMQError getIPv6Address(PRUint8 * const ipv6Addr) const;

  void setSequence(const PRUint32 sequence);
  void setPort(const PRUint32 port);
  void setTimestamp(const PRUint64 timestamp);
  void setIPv6Address(const PRUint8 * const ipv6Addr);
  void setIPv6Address(const IPAddress * const ipv6Addr);

  /**
   * Returns a string representation of the system message ID.
   *
   * @returns a string representation of the system message ID.  NULL
   * is returned if memory cannot be allocated to store the string.  */
  const UTF8String * toString();

  /**
   * Returns true iff id matches this
   *
   * @param id the SysMessageID to compare to
   * @return true iff the two SysMessageID's are equivalent
   */
  PRBool equals(const SysMessageID * const id) const;

};

#endif // SYSMESSAGEID_HPP 




