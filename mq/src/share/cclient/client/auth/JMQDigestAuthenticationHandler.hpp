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
 * @(#)JMQDigestAuthenticationHandler.hpp	1.3 06/26/07
 */ 

#ifndef JMQDIGESTAUTHENTICATIONHANDLER_HPP
#define JMQDIGESTAUTHENTICATIONHANDLER_HPP

#include "../../auth/AuthenticationProtocolHandler.hpp"

/**
 * The length of an MD5 hash in bytes.
 */
static const PRInt32 MD5_HASH_LEN = 16; // 128 bits

/**
 * This class implements the client side of the iMQ Digest authentication
 * method.
 */
class JMQDigestAuthenticationHandler : public AuthenticationProtocolHandler {
private:
  /**
   * The username to use for the authentication.
   */
  const UTF8String * username;

  /**
   * The password to use for the authentication.
   */
  const UTF8String * password; 

  /**
   * The authentication properties to use for the authentication.  This is 
   * currently not used.
   */
  const Properties * authProperties;

  /**
   * Calculates the MD5 hash of buf and converts the result to a
   * character string of hex digits.
   *
   * @param buf the buffer to hash
   * @param bufLen the length of buf
   * @param hashedBufStr the output parameter where the output string is placed.
   *  The caller is responsible for freeing this string.
   *
   * @return IMQ_SUCCESS if succesful and an error otherwise.  
   */
  static iMQError getMD5HashString(const char *  const buf,
                                   const PRInt32       bufLen,
                                         char ** const hashedBufStr);

  /**
   * This method converts a 128-bit MD5 hash value (interpreted as a 128-bit
   * 2's complement number) into the absolute value of the number and the
   * sign of the number.
   *
   * @param hashedBuf the buffer to convert.
   * @param signedHashBuf output parameter for the absolute value of the number.
   * @param isNegative output parameter that stores if the original number was
   *  negative.
   */
  static void convertMD5HashToSigned(const PRUint8 hashedBuf[MD5_HASH_LEN], 
                                           PRUint8 signedHashBuf[MD5_HASH_LEN], 
                                           PRBool * const isNegative);
  /**
   * Tests convertMD5HashToSigned. 
   *
   * @return IMQ_SUCCESS if succesful and an error otherwise.  
   * @see convertMD5HashToSigned
   */
  static iMQError testConvertMD5HashToSigned();


public:
  /**
   * Constructor.
   */
  JMQDigestAuthenticationHandler();

  /**
   * Destructor.
   */
  virtual ~JMQDigestAuthenticationHandler();

  virtual iMQError init(const UTF8String * const username, 
                        const UTF8String * const password,
                        const Properties * const authProperties);

  virtual iMQError handleRequest(const PRUint8 *  const authRequest,
                                 const PRInt32          authRequestLen,
                                       PRUint8 ** const authReply,
                                       PRInt32 *  const authReplyLen,
                                 const PRInt32          sequenceNum);

  virtual const char * getType() const;

  /**
   * The static test method that tests the general functionality of this class.
   */
  static iMQError test();

};


#endif // JMQDIGESTAUTHENTICATIONHANDLER_HPP

