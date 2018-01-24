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
 * @(#)IPAddress.hpp	1.4 06/26/07
 */ 

#ifndef IPADDRESS_H
#define IPADDRESS_H

#include <prtypes.h>
#include "../error/ErrorCodes.h"
#include "../io/IMQDataInputStream.hpp"
#include "../io/IMQDataOutputStream.hpp"


/** Size of an IPv4 address in bytes */
static const PRUint32 IP_ADDRESS_IPV4_SIZE = 4;
/** Size of an IPv6 address in bytes */
static const PRUint32 IP_ADDRESS_IPV6_SIZE = 16;
// Longest IPv4 address: 123.456.789.123
// Longest IPv4 + MACaddress: 123.456.789.123(ab:cd:ef:gh:ij:kl)
// Longest IPv6 address: 12AB:FE90:D8B3:12AB:FE90:D8B3:12AB:FE90
static const PRUint32 IP_ADDRESS_MAX_IPV6_ADDR_STR_LEN = 50; // This could be 40

class IPAddress : public Object {
protected:
  //
  // 128 bit buffer to hold the IP address.  We always store the
  // address as an IPv6 address
  //
  PRUint8     ip[IP_ADDRESS_IPV6_SIZE];
  PRUint32    type;

  char strValue[IP_ADDRESS_MAX_IPV6_ADDR_STR_LEN];

public:

  IPAddress();
  IPAddress( const IPAddress& ipAddress );
  IPAddress& operator=(const IPAddress& ipAddress );

  void reset();

  iMQError readAddress( IMQDataInputStream * const in );
  iMQError writeAddress( IMQDataOutputStream * const out ) const;

  iMQError getIPv6Address( PRUint8 * const ipv6Addr ) const;
  iMQError getIPv4AddressAsNetOrderInt( PRUint32 * const ipv4Addr ) const;

  iMQError setIPv4AddressFromNetOrderInt( const PRUint32 ipv4Addr );
  iMQError setAddressFromIPv6Address( const PRUint8 * const ipv6Addr );

  /**
   * returns a char * representation of the IPv6 address.  If the
   * address is an IPv4 address it looks like 123.456.789.123.  If
   * it's an IPv6 address it looks like
   * 12AB:FE90:D8B3:12AB:FE90:D8B3:12AB:FE90.
   *
   * @returns a char * representation of the IPv6 address.  
   */
  const char * toCharStr();

  /**
   * Pseudonym for toCharStr
   */
  const char * toString() const;


  /**
   * Return true iff this IPAddress is equivalent to ipAddr
   *
   * @param ipAddr the ipaddress to compare to
   * @return true iff this IPAddress is equivalent to ipAddr
   */
  PRBool equals(const IPAddress * const ipAddr) const;


private:
  PRBool isIPv4Mapped( const PRUint8 * const addr, const PRUint32 addrLen ) const;
  PRBool isIPv4Mac( const PRUint8 * const addr, const PRUint32 addrLen ) const;

};


#endif // IPADDRESS_H
