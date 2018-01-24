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
 * @(#)IPAddress.cpp	1.4 06/26/07
 */ 

#include "IPAddress.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"

static const PRUint32 IPV4_PREFIX_LENGTH = 12;
static const PRUint32 IPV4_MAC_PREFIX_LENGTH = 4;

static const PRUint32 UNKNOWN  = 0;
static const PRUint32 IPV4     = 1; /* IPv4-mapped IPv6 */
static const PRUint32 IPV6     = 2; /* IPv6 unicast */
static const PRUint32 IPV4_MAC = 3; /* MQ IPv4 + MAC */

// Prefix used to create an IPv4-mapped IPv6 address
static const PRUint8  IPV4_PREFIX[]   = { 0x0, 0x0, 0x0, 0x0, 
                                          0x0, 0x0, 0x0, 0x0, 
                                          0x0, 0x0, (PRUint8)0xFF, (PRUint8)0xFF };

// Prefix used to identify MQ IPv4+MAC format address
static const PRUint8  IPV4_MAC_PREFIX[]   = { (PRUint8)0xFF, 0x0, 0x0, 0x0 }; 

// A null IPv6 address
static const PRUint8  nullAddr[] = { 0x0, 0x0, 0x0, 0x0,
                                     0x0, 0x0, 0x0, 0x0,
                                     0x0, 0x0, (PRUint8)0xFF, (PRUint8)0xFF,
                                     0x0, 0x0, 0x0, 0x0 };


/*
 *
 */
IPAddress::IPAddress()
{
  CHECK_OBJECT_VALIDITY();
  
  reset();
}


/*
 *
 */
IPAddress::IPAddress( const IPAddress& ipAddress )
{
  CHECK_OBJECT_VALIDITY();
  
  reset();
  memcpy(this->ip, ipAddress.ip, sizeof(ipAddress.ip));
  this->type = ipAddress.type;
}


/*
 *
 */
IPAddress&
IPAddress::operator=(const IPAddress& ipAddress)
{
  CHECK_OBJECT_VALIDITY();
  
  reset();
  memcpy(this->ip, ipAddress.ip, sizeof(ipAddress.ip));
  this->type = ipAddress.type;

  return *this;
}


/*
 * Set the IP address to the IPv4 address 0.0.0.0
 */
void 
IPAddress::reset()
{
  CHECK_OBJECT_VALIDITY();

  // Clear the IP address
  memcpy(ip, nullAddr, sizeof(nullAddr));
  type = UNKNOWN;
  STRCPY(strValue,"");
}


/*
 * Get the IP address as a network byte order IPv4 integer address.
 */
MQError 
IPAddress::getIPv4AddressAsNetOrderInt(PRUint32 * const ipv4Addr) const
{
  CHECK_OBJECT_VALIDITY();

  //
  // Must be an ipv4 address
  //
  ASSERT( type == IPV4 || type == IPV4_MAC );
  if (type != IPV4 && type != IPV4_MAC) {
    return MQ_NOT_IPV4_ADDRESS;
  }
  ASSERT( isIPv4Mapped(this->ip, sizeof(this->ip)) ||
          isIPv4Mac(this->ip, sizeof(this->ip)) );

  //
  // Validate the parameter
  //
  RETURN_ERROR_IF_NULL( ipv4Addr );

  //
  // Generate address from last 4 bytes of address buffer
  //
  PRUint32 address = 0;
  address  = (((PRUint32)ip[15]) <<  0) & 0x000000FF;
  address |= (((PRUint32)ip[14]) <<  8) & 0x0000FF00;
  address |= (((PRUint32)ip[13]) << 16) & 0x00FF0000;
  address |= (((PRUint32)ip[12]) << 24) & 0xFF000000;

  //
  // Return the address in the caller's parameter.
  //
  *ipv4Addr = address;

  return MQ_SUCCESS;
}


/*
 *
 */
PRBool
IPAddress::isIPv4Mapped(const PRUint8 * const addr, const PRUint32 addrLen) const
{
  CHECK_OBJECT_VALIDITY();

  if (addrLen == IP_ADDRESS_IPV6_SIZE) {
    // Check if prefixed by IPv4-mapped prefix
    for (PRUint32 n = 0; n < IPV4_PREFIX_LENGTH; n++) {
      if (addr[n] != IPV4_PREFIX[n]) {
        return PR_FALSE;
      }
    }
    return PR_TRUE;
  } else {
    return PR_FALSE;
  }
}


/*
 *
 * Check if the passed address is a MQ IPv4+MAC address.  This address format 
 * is currently only used by MQ Java client.  There is no-plan to add this 
 * format to MQ C client for MQ SysMessageID is expected to be changed to use 
 * Java 1.5 UUID in future release
 *
 * MQ 3.0 has defined an alternate format used to make the IPv4 address more 
 * unique. It basically bake a 40 bit MAC address (or psuedo MAC address) into
 * the upper portion of the 128 bits. The format is 8 1 bits followed by 24 0 
 * bits followed by the 48 bit MAC address followed by 16 1 bits followed by 
 * the 32 bit IPv4 address.
 *
 * @param  addr    Raw IPv6 address (128 bits)
 *
 * @return true    If addr is an MQ IPv4+MAC address
 *
 */
PRBool
IPAddress::isIPv4Mac(const PRUint8 * const addr, const PRUint32 addrLen) const
{
  CHECK_OBJECT_VALIDITY();

  if (addrLen != IP_ADDRESS_IPV6_SIZE)  return PR_FALSE;

  for (PRUint32 n = 0; n < IPV4_MAC_PREFIX_LENGTH; n++) {
    if (addr[n] != IPV4_MAC_PREFIX[n]) {
      return PR_FALSE;
    }
  }
  return PR_TRUE;
}

/*
 * Get the IP address as a network byte order IPv4 integer address.
 */
MQError 
IPAddress::setIPv4AddressFromNetOrderInt(const PRUint32 ipv4Addr)
{
  CHECK_OBJECT_VALIDITY();

  //
  // Clear the IP address, which sets the address to the 
  // IPv4 address 0.0.0.0
  //
  reset();

  //
  // Set the ip address byte-by-byte.
  //
  ip[12] = ((PRUint8*)&ipv4Addr)[0];
  ip[13] = ((PRUint8*)&ipv4Addr)[1];
  ip[14] = ((PRUint8*)&ipv4Addr)[2];
  ip[15] = ((PRUint8*)&ipv4Addr)[3];

  type = IPV4;

  return MQ_SUCCESS;
}


/*
 * 
 */
MQError 
IPAddress::setAddressFromIPv6Address(const PRUint8 * const ipv6Addr)
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the parameter
  //
  RETURN_ERROR_IF_NULL( ipv6Addr );
  reset();

  //
  // Copy the specified address into the local address
  //
  memcpy(ip, ipv6Addr, IP_ADDRESS_IPV6_SIZE);

  //
  // Set the cached value, which stores if this is an IPv4 address
  //
  if (isIPv4Mapped(ip, IP_ADDRESS_IPV6_SIZE))  {
    type = IPV4;
  } else if (isIPv4Mac(ip, IP_ADDRESS_IPV6_SIZE)) {  
    type = IPV4_MAC;
  } else {
    type = IPV6;
  }
  
  return MQ_SUCCESS;
}


/*
 *  The parameter ipv6Addr must be at least IP_ADDRESS_IPV6_SIZE big
 */
MQError 
IPAddress::getIPv6Address(PRUint8 * const ipv6Addr) const
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the parameter
  //
  RETURN_ERROR_IF_NULL( ipv6Addr );

  //
  // Copy the address out
  //
  memcpy(ipv6Addr, ip, IP_ADDRESS_IPV6_SIZE);

  return MQ_SUCCESS;
}


/*
 *
 */
MQError
IPAddress::readAddress(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the parameter
  //
  RETURN_ERROR_IF_NULL( in );
  reset();

  //
  // Read the bytes
  //
  MQError mqError;
  if ((mqError = in->readUint8Array(ip, IP_ADDRESS_IPV6_SIZE)) != MQ_SUCCESS) {
    reset();
    return mqError;
  } 
  
  //
  // If the read was successful, then update whether the 
  // address is actually an IPv4 address
  //
  if (isIPv4Mapped(ip, IP_ADDRESS_IPV6_SIZE))  {
    type = IPV4;
  } else if (isIPv4Mac(ip, IP_ADDRESS_IPV6_SIZE)) {
    type = IPV4_MAC;
  } else {
    type = IPV6;
  }

  return MQ_SUCCESS;
}


/*
 *
 */
MQError
IPAddress::writeAddress(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the parameter
  //
  RETURN_ERROR_IF_NULL( out );

  //
  // Write the bytes
  //
  RETURN_IF_ERROR( out->writeUint8Array(ip, IP_ADDRESS_IPV6_SIZE) );


  return MQ_SUCCESS;
}

/*
 *
 */
const char *
IPAddress::toCharStr()
{
  CHECK_OBJECT_VALIDITY();

  if (STRLEN( this->strValue ) > 0) {
    return this->strValue;
  }

  PRInt32 bytesWritten = 0;
  if (type == IPV4) {
    ASSERT( isIPv4Mapped(this->ip, sizeof(this->ip)) );
    bytesWritten = PR_snprintf( this->strValue, sizeof(this->strValue), 
                                "%u.%u.%u.%u", ip[12], ip[13], ip[14], ip[15] );

  } else if (type == IPV4_MAC) {
    ASSERT( isIPv4Mac(this->ip, sizeof(this->ip)) );
    bytesWritten = PR_snprintf( this->strValue, sizeof(this->strValue), 
                                "%u.%u.%u.%u(%x:%x:%x:%x:%x:%x)", 
                                 ip[12], ip[13], ip[14], ip[15],
                                 ip[4]&0xFF, ip[5]&0xFF, ip[6]&0xFF,
                                 ip[7]&0xFF, ip[8]&0xFF, ip[9]&0xFF );
  } else {
    bytesWritten = PR_snprintf( this->strValue, sizeof(this->strValue), 
                             "%x:%x:%x:%x:%x:%x:%x:%x",
                             PR_ntohs(*(PRUint16*)&(ip[0])  ),
                             PR_ntohs(*(PRUint16*)&(ip[2])  ),
                             PR_ntohs(*(PRUint16*)&(ip[4])  ),
                             PR_ntohs(*(PRUint16*)&(ip[6])  ),
                             PR_ntohs(*(PRUint16*)&(ip[8])  ),
                             PR_ntohs(*(PRUint16*)&(ip[10]) ),
                             PR_ntohs(*(PRUint16*)&(ip[12]) ),
                             PR_ntohs(*(PRUint16*)&(ip[14]) ) );
  }
  ASSERT( (bytesWritten > 0) && (bytesWritten < (PRInt32)sizeof(this->strValue)) );
  // just in case it wasn't NULL terminated
  this->strValue[sizeof(this->strValue)-1] = '\0';  

  return this->strValue;
}


/*
 *
 */
const char * 
IPAddress::toString() const
{
  CHECK_OBJECT_VALIDITY();

  // Fudge on the 'const'ness because this only changes the string representation
  return (((IPAddress*)this)->toCharStr());
}


/*
 *
 */
PRBool
IPAddress::equals(const IPAddress * const ipAddr) const
{
  CHECK_OBJECT_VALIDITY();

  if (ipAddr == NULL) {
    return PR_FALSE;
  }
  
  return 
    (this->type == ipAddr->type) &&
    (memcmp(this->ip, ipAddr->ip, IP_ADDRESS_IPV6_SIZE) == 0);
}
