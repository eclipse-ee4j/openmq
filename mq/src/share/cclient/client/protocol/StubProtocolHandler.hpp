/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)StubProtocolHandler.hpp	1.3 06/26/07
 */ 

#ifndef STUBPROTOCOLHANDLER_HPP
#define STUBPROTOCOLHANDLER_HPP

#include "../../debug/DebugUtils.h"
#include "../../containers/Properties.hpp"
#include "../../error/ErrorCodes.h"
#include "../TransportProtocolHandler.hpp"
#include "../../serial/SerialDataInputStream.hpp"
#include "../../serial/SerialDataOutputStream.hpp"

#include <nspr.h>


/**
 * This class is only used for testing/debugging purposes.
 */
static const int STUB_PROTOCOL_HANDLER_MAX_BUFFER_SIZE = 100 * 1000;
class StubProtocolHandler : public TransportProtocolHandler {
private:
  SerialDataInputStream inputStream;
  SerialDataOutputStream outputStream;

  PRUint8 buffer[STUB_PROTOCOL_HANDLER_MAX_BUFFER_SIZE];
  PRUint32 bufferSize;

  const char * fileToWrite; 
  
public:

  StubProtocolHandler(const char * fileToRead, const char * fileToWrite);
  ~StubProtocolHandler();

  // Methods from TransportProtocolHandler that must be filled in.
  virtual iMQError connect(const Properties * const connectionProperties);
  virtual iMQError getLocalPort(PRUint16 * const port) const;
  virtual iMQError getLocalIP(const IPAddress ** const ipAddr) const;
  virtual iMQError read(const PRInt32          numBytesToRead,
                        const PRUint32         timeoutMicroSeconds, 
                              PRUint8 * const  bytesRead, 
                              PRInt32 * const  numBytesRead);
  virtual iMQError write(const PRInt32          numBytesToWrite,
                         const PRUint8 * const  bytesToWrite,
                         const PRUint32         timeoutMicroSeconds, 
                               PRInt32 * const  numBytesWritten);
  virtual iMQError shutdown();
  virtual iMQError close();

  
  virtual PRBool isClosed();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  StubProtocolHandler(const StubProtocolHandler& stubProtocolHandler);
  StubProtocolHandler& operator=(const StubProtocolHandler& stubProtocolHandler);
};


#endif // STUBPROTOCOLHANDLER_HPP
