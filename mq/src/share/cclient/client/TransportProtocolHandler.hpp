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
 * @(#)TransportProtocolHandler.hpp	1.3 06/26/07
 */ 

#ifndef TRANSPORTPROTOCOLHANDLER_HPP
#define TRANSPORTPROTOCOLHANDLER_HPP

#include "../debug/DebugUtils.h"
#include "../containers/Properties.hpp"
#include "../error/ErrorCodes.h"
#include "../net/IPAddress.hpp"
#include "iMQConstants.hpp"
#include "../basictypes/Object.hpp"
#include <nspr.h>


// These constants should be declared within TransportProtocolHandler,
// but C++ only allows const static INTEGRAL data members to be
// initialized.



/**
 * TransportProtocolHandler is the abstract base class interface that
 * each transport protocol (i.e. TCP or SSL) must implement.  
 */
class TransportProtocolHandler : public Object {
public:
  /**
   * The destructor.  It is declared virtual so that the destructor of
   * the sub-classes will be called.
   */
  virtual ~TransportProtocolHandler() {}

  /**
   * Connects to the broker with the connection properties specified
   * in connectionProperties.  At a minimum the properties
   * TRANSPORT_BROKER_NAME_PROPERTY and TRANSPORT_BROKER_PORT_PROPERTY
   * must be filled in.  
   *
   * @param connectionProperties the properties used to specify which broker to 
   *  connect to.
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  virtual iMQError connect(const Properties * const connectionProperties) = 0;

  /**
   * Returns the local port number of the connection.
   *
   * @param port output parameter for the port number of the local end of the 
   *  connection to the broker.  
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see getLocalIP
   */
  virtual iMQError getLocalPort(PRUint16 * const port) const = 0;

  /**
   * Returns the local IP address of the connection.
   *
   * @param ipAddr output parameter for the IP address of the local end of the 
   *  connection to the broker.  
   * @return IMQ_SUCCESS if successful and an error otherwise
   * @see getLocalPort
   */
  virtual iMQError getLocalIP(const IPAddress ** const ipAddr) const = 0;

  /**
   * Reads bytes from the connection.
   *
   * @param numBytesToRead the number of bytes to read
   * @param timeoutMicroSeconds the timeout specified in microseconds. 
   *  (The granularity of timeoutMicroSeconds may only be 1-10 MILLIseconds.
   *  A value of 0 implies no wait, and a value of 0xFFFFFFFF implies wait 
   *  forever (i.e. no timeout).  
   * @param bytesRead the output buffer where the read bytes are
   *  placed.  It must be at least numBytesToRead bytes big.  
   * @param numBytesRead output parameter for the actual number of bytes read
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  virtual iMQError read(const PRInt32          numBytesToRead,
                        const PRUint32         timeoutMicroSeconds, 
                              PRUint8 * const  bytesRead, 
                              PRInt32 * const  numBytesRead) = 0;

  /**
   * Write bytes to the connection.
   *
   * @param numBytesToWrite the number of bytes to write
   * @param bytesToWrite the bytes to write to the connection
   * @param timeoutMicroSeconds the timeout specified in microseconds. 
   *  (The granularity of timeoutMicroSeconds may only be 1-10 MILLIseconds.
   *  A value of 0 implies no wait, and a value of 0xFFFFFFFF implies wait 
   *  forever (i.e. no timeout).  
   * @param numBytesWritten output parameter for the actual number of bytes 
   *  written
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  virtual iMQError write(const PRInt32          numBytesToWrite,
                         const PRUint8 * const  bytesToWrite,
                         const PRUint32         timeoutMicroSeconds, 
                               PRInt32 * const  numBytesWritten) = 0;

  /**
   * Shuts down the connection to the broker (but does not close the 
   * socket file descriptor.)  Causes any thread currently blocked on
   * a read() to return.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  virtual iMQError shutdown() = 0;
  
  /**
   * Closes the connection to the broker.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  virtual iMQError close() = 0;


  /**
   * @return true iff the connection is closed
   */
  virtual PRBool isClosed() = 0;
};


#endif // TRANSPORTPROTOCOLHANDLER_HPP
