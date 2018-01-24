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
 * @(#)AuthenticationProtocolHandler.hpp	1.4 06/28/07
 */ 

#ifndef AUTHENTICATIONPROTOCOLHANDLER_HPP
#define AUTHENTICATIONPROTOCOLHANDLER_HPP

#include <nspr.h>
#include "../error/ErrorCodes.h"
#include "../basictypes/UTF8String.hpp"
#include "../containers/Properties.hpp"
#include "../basictypes/Object.hpp"


/**
 * AuthenticationProtocolHandler provides the client-side API for an
 * application implementor to plug-in their own authentication request
 * handler.  This allows the client to authenticate with the broker.
 */
class AuthenticationProtocolHandler : public Object {
public:
  virtual ~AuthenticationProtocolHandler() {}

  
  /**
   * This method is called right before start a authentication process
   *
   * @param username the user name passed from createConnection()
   * @param password the password passed from createConnection()
   * @param authProperties not defined yet 
   *
   * Currently for JMQ2.0, username/password always have values (if not
   * passed in createConnection() call, they are assigned default values).
   *
   */
  virtual iMQError init(const UTF8String * const username, 
                        const UTF8String * const password,
                        const Properties * const authProperties) = 0;


  /**
   * This method is called to handle a authentication request.
   *
   * @param authRequest the authentication request data.  This is then
   *        packet body of AUTHENTICATE_REQUEST packet.
   * @param authRequestLen is the length of authRequest
   * @param authReply is the output request length.  This will be the
   *        packet body of AUTHENTICATE packet.  handleRequest allocates
   *        this buffer, and the caller is responsible for freeing it.
   * @param authReplyLen is the length of authReply
   * @param sequenceNum this is the sequence number field in the 
   *        AUTHENTICATE_REQUEST packet.  It can be used for correlation 
   *        in multiple requests case.
   * @return IMQ_SUCCESS if successful and an error otherwise.
   * 
   */
  virtual iMQError handleRequest(const PRUint8 *  const authRequest,
                                 const PRInt32          authRequestLen,
                                       PRUint8 ** const authReply,
                                       PRInt32 *  const authReplyLen,
                                 const PRInt32          sequenceNum) = 0;


  /**
   * @return the type of authentication
   */
  virtual const char * getType() const = 0;
};


#endif // AUTHENTICATIONPROTOCOLHANDLER_HPP

