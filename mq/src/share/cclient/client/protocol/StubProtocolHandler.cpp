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
 * @(#)StubProtocolHandler.cpp	1.3 06/26/07
 */ 

#include "StubProtocolHandler.hpp"
#include "../../util/UtilityMacros.h"
#include "../../error/ErrorCodes.h"

 
StubProtocolHandler::StubProtocolHandler(const char * fileToReadArg, 
                                         const char * fileToWriteArg)
{
  CHECK_OBJECT_VALIDITY();

  if ((fileToReadArg == NULL) || (fileToWriteArg ==NULL)) {
    return;
  }

  FILE * in = fopen(fileToReadArg, "rb");
  if (in == NULL) {
    return;
  }

  bufferSize = (PRUint32)fread(buffer, sizeof(PRUint8), sizeof(buffer), in);
  fclose(in);
  inputStream.setNetOrderStream(buffer, bufferSize);

  this->fileToWrite = fileToWriteArg;
}

StubProtocolHandler::~StubProtocolHandler()
{
  CHECK_OBJECT_VALIDITY();

  FILE* out = fopen(fileToWrite, "wb");
  outputStream.writeToFile(out);
  fclose(out);
}



iMQError 
StubProtocolHandler::connect(const Properties * const connectionProperties)
{
  CHECK_OBJECT_VALIDITY();

  UNUSED( connectionProperties );
  return IMQ_SUCCESS;
}


iMQError 
StubProtocolHandler::getLocalPort(PRUint16 * const port) const
{
  CHECK_OBJECT_VALIDITY();

  *port = 0;
  return IMQ_SUCCESS;
}

iMQError
StubProtocolHandler::read(const PRInt32          numBytesToRead,
                          const PRUint32         timeoutMicroSeconds, 
                                PRUint8 * const  bytesRead, 
                                PRInt32 * const  numBytesRead)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_IF_ERROR( inputStream.readUint8Array(bytesRead, numBytesToRead) );
  UNUSED( timeoutMicroSeconds );

  *numBytesRead = numBytesToRead;

  return IMQ_SUCCESS;
}

iMQError
StubProtocolHandler::write(const PRInt32          numBytesToWrite,
                           const PRUint8 * const  bytesToWrite,
                           const PRUint32         timeoutMicroSeconds, 
                                 PRInt32 * const  numBytesWritten)
{
  CHECK_OBJECT_VALIDITY();

  UNUSED( timeoutMicroSeconds );

  outputStream.writeUint8Array(bytesToWrite, numBytesToWrite);
  *numBytesWritten = numBytesToWrite;

  return IMQ_SUCCESS;
}


iMQError 
StubProtocolHandler::close()
{
  CHECK_OBJECT_VALIDITY();

  return IMQ_SUCCESS;
}

iMQError 
StubProtocolHandler::shutdown()
{
  CHECK_OBJECT_VALIDITY();

  return IMQ_SUCCESS;
}

PRBool 
StubProtocolHandler::isClosed()
{
  CHECK_OBJECT_VALIDITY();

  return PR_FALSE;
}

iMQError 
StubProtocolHandler::getLocalIP(const IPAddress ** const ipAddr) const
{
  CHECK_OBJECT_VALIDITY();

  *ipAddr = NULL;
  return IMQ_SUCCESS;
}
