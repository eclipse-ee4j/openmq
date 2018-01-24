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
 * @(#)SSLProtocolHandler.hpp	1.4 06/26/07
 */ 

#ifndef SSLPROTOCOLHANDLER_HPP
#define SSLPROTOCOLHANDLER_HPP


#include "../../debug/DebugUtils.h"
#include "../../util/PRTypesUtils.h"
#include "../../containers/Properties.hpp"
#include "../../error/ErrorCodes.h"
#include "../TransportProtocolHandler.hpp"
#include "../../io/SSLSocket.hpp"
#include "../iMQConstants.hpp"
#include <nspr.h>

/**
 * A string defining this transport protocol
 */
static const char * SSL_PROTOCOL_STR = "tls";

/**
 * SSLProtocolHandler is a SSL-based implementation of the abstract
 * base class TransportProtocolHandler.  It basically acts as an
 * Adapter of the SSLSocket class.  The connect method is the only
 * method that is non-trivial.  
 */
class SSLProtocolHandler : public TransportProtocolHandler {
private:
  /**
   * The actual socket that is used to connect to the broker
   */
  SSLSocket brokerSocket;

  /**
   * Initializes member variables.  This currently doesn't do anything.
   */
  void init();

  /**
   * Resets brokerSocket.
   */
  void reset();
  
public:
  /**
   * Default constructor.
   */
  SSLProtocolHandler();

  /**
   * Default destructor.  It closes the socket if it hasn't already be closed.
   */
  virtual ~SSLProtocolHandler();
  
  //
  // These are the virtual functions of TransportProtocolHandler that
  // must be implemented.  See the comments in
  // TransportProtocolHandler.hpp to see descriptions of these
  // methods.
  //

  virtual MQError connect(const Properties * const connectionProperties);
  virtual MQError getLocalPort(PRUint16 * const port) const;
  virtual MQError getLocalIP(const IPAddress ** const ipAddr) const;
  virtual MQError read(const PRInt32         numBytesToRead,
                        const PRUint32        timeoutMicroSeconds, 
                              PRUint8 * const bytesRead, 
                              PRInt32 * const numBytesRead);
  virtual MQError write(const PRInt32          numBytesToWrite,
                         const PRUint8 * const  bytesToWrite,
                         const PRUint32         timeoutMicroSeconds, 
                               PRInt32 * const  numBytesWritten);
  virtual MQError close();
  virtual MQError shutdown();
  virtual PRBool isClosed();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  SSLProtocolHandler(const SSLProtocolHandler& handler);
  SSLProtocolHandler& operator=(const SSLProtocolHandler& handler);

};

#endif // TCPPROTOCOLHANDLER_HPP
