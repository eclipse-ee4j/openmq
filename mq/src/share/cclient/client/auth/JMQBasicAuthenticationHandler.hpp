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
 * @(#)JMQBasicAuthenticationHandler.hpp	1.3 06/26/07
 */ 

#ifndef JMQBASICAUTHENTICATIONHANDLER_HPP
#define JMQBASICAUTHENTICATIONHANDLER_HPP

#include "../../auth/AuthenticationProtocolHandler.hpp"


/**
 * This client side implementation of the MQ basic authentication method
 */

class JMQBasicAuthenticationHandler : public AuthenticationProtocolHandler {
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


public:
  /**
   * Constructor.
   */
  JMQBasicAuthenticationHandler();

  /**
   * Destructor.
   */
  virtual ~JMQBasicAuthenticationHandler();

  virtual MQError init(const UTF8String * const username, 
                       const UTF8String * const password,
                       const Properties * const authProperties);

  virtual MQError handleRequest(const PRUint8 *  const authRequest,
                                const PRInt32          authRequestLen,
                                      PRUint8 ** const authReply,
                                      PRInt32 *  const authReplyLen,
                                const PRInt32          sequenceNum);

  virtual const char * getType() const;

  /**
   * The static test method that tests the general functionality of this class.
   */
  static MQError test();

};


#endif // JMQBASICAUTHENTICATIONHANDLER_HPP

