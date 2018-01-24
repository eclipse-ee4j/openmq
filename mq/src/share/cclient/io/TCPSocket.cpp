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
 * @(#)TCPSocket.cpp	1.11 06/26/07
 */ 

#include "TCPSocket.hpp"
#include "../util/UtilityMacros.h"
//#include <private/pprio.h>
#include "../util/PRTypesUtils.h"
#include "../util/LogUtils.hpp"

/**
 *
 */
TCPSocket::TCPSocket()
{
  CHECK_OBJECT_VALIDITY();

  init();
}

/**
 *
 */
TCPSocket::TCPSocket(const char * const debugFileBase)
{
  CHECK_OBJECT_VALIDITY();

  init();

  // This is only used for debugging purposes
  // Open the files to output the stream of bytes read and written
  if (debugFileBase != NULL) {
    char writtenBytesFileName[1000];
    char readBytesFileName[1000];
    sprintf(writtenBytesFileName, "%s_bytesWritten", debugFileBase);
    sprintf(readBytesFileName, "%s_bytesRead", debugFileBase);
    writtenBytesFile = fopen(writtenBytesFileName, "wb");
    readBytesFile = fopen(readBytesFileName, "wb");
    if ((writtenBytesFile == NULL) || (readBytesFile == NULL)) {
      if (writtenBytesFile != NULL) {
        fclose(writtenBytesFile);
      }
      if (readBytesFile != NULL) {
        fclose(readBytesFile);
      }
      writtenBytesFile = NULL;
      readBytesFile = NULL;
    }
  }
}

/**
 *
 */
TCPSocket::~TCPSocket()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}



/**
 *
 */
void
TCPSocket::init()
{
  CHECK_OBJECT_VALIDITY();

  hostSocket = NULL;
  MEMZERO( hostEntry );
  MEMZERO( hostEntryData );
  MEMZERO( localEntry );
  MEMZERO( localEntryData );
  MEMZERO( hostAddr );
  MEMZERO( localAddr );
  MEMZERO( localPort );
  localIP.reset();

  // These are only used for debugging
  writtenBytesFile = NULL;
  readBytesFile = NULL;
}

/**
 *
 */
void
TCPSocket::reset()
{
  CHECK_OBJECT_VALIDITY();

  if (hostSocket != NULL) {
    this->close();
  }

  if (writtenBytesFile != NULL) {
    fclose(writtenBytesFile);
  }
  if (readBytesFile != NULL) {
    fclose(readBytesFile);
  }
  init();
}




/*
 *
 */
iMQError 
TCPSocket::connect(const char * hostName, 
                   const PRUint16 hostPort, const PRBool useIPV6,
                   const PRUint32 timeoutMicroSeconds)
{
  CHECK_OBJECT_VALIDITY();

  iMQError     errorCode = IMQ_SUCCESS;

  // Validate the parameters
  NULLCHK( hostName );
  CNDCHK( hostPort == 0, IMQ_INVALID_PORT );


  // Make sure we are not already connected, and that
  // connectionProperties is valid
  ASSERT( this->hostSocket == NULL );
  CNDCHK( this->hostSocket != NULL, IMQ_TCP_ALREADY_CONNECTED );

  // Lookup the host.
   
  if (useIPV6 == PR_TRUE) {
    NSPRCHK( PR_GetIPNodeByName(hostName, 
                            PR_AF_INET6, PR_AI_DEFAULT, 
                            this->hostEntryData,    
                            sizeof(this->hostEntryData),
                            &(this->hostEntry)) );
  } else {
    NSPRCHK( PR_GetHostByName(hostName,    
                            this->hostEntryData,    
                            sizeof(this->hostEntryData),
                            &(this->hostEntry)) );
  } 

  // Get the host's address
  CNDCHK( PR_EnumerateHostEnt(0,
                              &(this->hostEntry),
                              (PRUint16)hostPort,
                              &(this->hostAddr)) == -1, NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()));
  
  // Create a new socket
  MEMCHK( this->hostSocket = PR_OpenTCPSocket(this->hostAddr.raw.family) );

  // Make the socket non-blocking and disable Nagel's algorithm
  ERRCHK( this->setDefaultSockOpts() );

  ERRCHK( doConnect(&(this->hostAddr), microSecondToIntervalTimeout(timeoutMicroSeconds), hostName) ); 

  // Get the name of the local connection (address and port)
  ERRCHK( this->cacheLocalAddr() );

  // Log
  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "Socket connected to %s:%d.", hostName, hostPort ));
  
  return IMQ_SUCCESS;

Cleanup:
  
  LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_CONNECT_FAILED,
                "Failed to connect to %s:%d because '%s' (%d)", 
                hostName, hostPort, errorStr(errorCode), errorCode ));

  this->reset();
  MQ_ERROR_TRACE( "connect", errorCode );
  return errorCode;
}


/*
 *
 */
iMQError
TCPSocket::preConnect(const char * hostName)
{
  return IMQ_SUCCESS;
}

MQError
TCPSocket::doConnect(PRNetAddr *addr, PRIntervalTime timeout, const char * hostName)
{
  MQError errorCode = MQ_SUCCESS;
  PRStatus status;

  // Perform some pre connect duties
  ERRCHK( this->preConnect(hostName) );

  //
  // Connect to the host.  Because this is non-blocking, we have to
  // use poll to wait for it to complete.
  //
  status = PR_Connect(this->hostSocket, addr, 0);
  if ((status != PR_SUCCESS) && (PR_GetError() != PR_IN_PROGRESS_ERROR)) {
    LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SOCKET_ERROR,
                  "TCP socket connect failed, error=%d, oserror=%d", PR_GetError(), PR_GetOSError() ));
    ERRCHK_TRACE( NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()), "PR_Connect", "nspr");
  }

  // Poll for the connection to complete
  PRPollDesc pd;
  pd.in_flags = PR_POLL_WRITE | PR_POLL_EXCEPT;
  errorCode =  this->pollForEvents(&pd, timeout);
  if (errorCode != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, errorCode,
                  "TCP poll for connect completion failed, error=%d, oserror=%d",
                  PR_GetError(), PR_GetOSError() ));
    ERRCHK_TRACE( errorCode, "pollForEvents", "mq" );
  }
  
  NSPRCHK( PR_GetConnectStatus(&pd) );

  // Perform some post connect duties
  ERRCHK( this->postConnect() );
  return MQ_SUCCESS;

Cleanup:  
  return errorCode;

}

/*
 *
 */
iMQError
TCPSocket::postConnect()
{
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
TCPSocket::getLocalPort(PRUint16 * const port) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( port );
  *port = 0;
  if (this->hostSocket == NULL) {
    RETURN_UNEXPECTED_ERROR( IMQ_TCP_CONNECTION_CLOSED );
  }

  *port = localPort;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
TCPSocket::getLocalIP(const IPAddress ** const ipAddr) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( ipAddr );
  *ipAddr = 0;
  if (this->hostSocket == NULL) {
    RETURN_UNEXPECTED_ERROR( IMQ_TCP_CONNECTION_CLOSED );
  }

  *ipAddr = &(this->localIP);

  return IMQ_SUCCESS;
}



/*
 *
 */
iMQError 
TCPSocket::read(const PRInt32           numBytesToRead,
                const PRUint32          timeoutMicroSeconds, 
                      PRUint8 *  const  bytesToRead, 
                      PRInt32 *  const  numBytesRead)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 bytesRead;
  PRIntervalTime timeout;
  PRIntervalTime opStartTime;

  // Parameter validation
  NULLCHK( numBytesRead );
  *numBytesRead = 0;
  if (numBytesToRead == 0) {
    return IMQ_SUCCESS;
  }
  CNDCHK( numBytesToRead < 0, IMQ_NEGATIVE_AMOUNT );
  NULLCHK( bytesToRead );
  CNDCHK( this->hostSocket == NULL, IMQ_TCP_CONNECTION_CLOSED );

  // Convert the timeout in microseconds into a system dependent time
  // interval, and get the time that the operation started.
  bytesRead = 0;
  timeout = microSecondToIntervalTimeout(timeoutMicroSeconds);
  opStartTime = PR_IntervalNow();
  // This code is ugly, but it's getting better.

  // While the timeout hasn't expired and there are still bytes left to read,
  // Wait until we can read them, and then read them.  This is a do while loop
  // because if timeout is 0, then we want to try at least once.
  do {
    
    // Now try to receive the remaining bytes
    bytesRead = PR_Recv(this->hostSocket, 
                        &(bytesToRead[*numBytesRead]),                               
                        numBytesToRead - *numBytesRead,
                        0,
                        0);

    // Update *numBytesRead based on the number of bytes that were just read
    if (bytesRead > 0) {
      if (readBytesFile != NULL) {
        fwrite(&(bytesToRead[*numBytesRead]), sizeof(PRUint8), bytesRead,
               readBytesFile);
        fflush(readBytesFile);
      }
      *numBytesRead += bytesRead;
    } 

    // bytesRead < 0 signifies an error, if it's something other than 
    // PR_WOULD_BLOCK_ERROR then return that error to the user
    else if (bytesRead < 0) {
      PRErrorCode prError = PR_GetError();
      if ((prError != PR_WOULD_BLOCK_ERROR) && (prError != PR_PENDING_INTERRUPT_ERROR)) {
#ifndef NDEBUG        
        if ((prError != PR_CONNECT_RESET_ERROR)   && 
            (prError != PR_CONNECT_ABORTED_ERROR) &&
            (prError != PR_SOCKET_SHUTDOWN_ERROR) &&
            (prError != PR_IO_TIMEOUT_ERROR)) 
        {
          BREAKPOINT(); // let's see what other errors we get
        }
#endif        
        ERRCHK( NSPR_ERROR_TO_IMQ_ERROR(prError));    // always return the error code
      }
    } 

    // bytesRead == 0 implies that the connection is closed
    else { 
      CNDCHK( bytesRead == 0, IMQ_TCP_CONNECTION_CLOSED );
    }

    // If we didn't read everything, then poll to see when there is data to read
    if (*numBytesRead < numBytesToRead) {
      // Determine how much time of the timeout is left and then wait for that 
      // amount of time for the socket to become readable.
      PRPollDesc pd;
      pd.in_flags = PR_POLL_READ; // | PR_POLL_EXCEPT;

      // Copied from a NSPR newsgroup: 
      // "The only thing PR_Poll can tell you is that a socket is
      // readable or writable.  It cannot tell you how many bytes can be
      // read or written. Moreover, with NSPR layered sockets, a read or
      // write on a socket that PR_Poll reports as readable or writable
      // may only result in invisible internal progress and fail with
      // the WOULD_BLOCK error."
      // This is appropriate when using an SSL socket
      if ((errorCode = this->pollForEvents(&pd, timeoutRemaining(opStartTime, timeout))) != IMQ_SUCCESS) {
        if (errorCode == IMQ_TIMEOUT_EXPIRED) {
          errorCode = IMQ_SUCCESS;
        }
        break;
      }
    }
  } while ((timeoutRemaining(opStartTime, timeout) != 0) && 
           (*numBytesRead < numBytesToRead));

  ERRCHK( errorCode );

  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "Read %d (of %d) bytes from socket 0x%p.", 
               *numBytesRead, numBytesToRead, this->hostSocket ));
  
  return IMQ_SUCCESS;

Cleanup:
  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_READ_FAILED,
             "Failed to read from the socket because '%s' (%d)", 
             errorStr(errorCode), errorCode ));


  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_READ_FAILED,
             "Failed to read %d bytes from socket 0x%p because '%s' (%d)", 
             numBytesToRead, this->hostSocket, errorStr(errorCode), errorCode ));
  
  return errorCode;
}

/*
 *
 */
iMQError 
TCPSocket::write(const PRInt32           numBytesToWrite,
                 const PRUint8 * const   bytesToWrite,
                 const PRUint32          timeoutMicroSeconds, 
                       PRInt32 * const   numBytesWritten)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRIntervalTime timeout;
  PRIntervalTime opStartTime;
  PRInt32 bytesWritten;

  
  // Parameter validation
  NULLCHK( numBytesWritten );
  *numBytesWritten = 0;
  if (numBytesToWrite == 0) {
    return IMQ_SUCCESS;
  }
  CNDCHK( numBytesToWrite < 0, IMQ_NEGATIVE_AMOUNT );
  NULLCHK( bytesToWrite );
  CNDCHK( this->hostSocket == NULL, IMQ_TCP_CONNECTION_CLOSED );

  // Convert the timeout in microseconds into a system dependent time
  // interval, and get the time that the operation started.
  timeout = microSecondToIntervalTimeout(timeoutMicroSeconds);
  opStartTime = PR_IntervalNow();
  bytesWritten = 0;

  // This code is ugly, but it's getting better

  // While the timeout hasn't expired and there are still bytes left to write,
  // Wait until we can write them, and then write them.  This is a do while loop
  // because if timeout is 0, then we want to try at least once.
  do {
    // Now try to send the remaining bytes
    //monitor.enter();
    bytesWritten = PR_Send(this->hostSocket, 
                           &(bytesToWrite[*numBytesWritten]),                               
                           numBytesToWrite - *numBytesWritten,
                           0,
                           0);
    
    LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                 "PR_Send(%d) returned %d, (numBytesToWrite=%d, *numBytesWritten=%d)",
                 numBytesToWrite - *numBytesWritten, bytesWritten,
                 numBytesToWrite, *numBytesWritten ));
    
    // Update *numBytesWritten based on the number of bytes that were just written
    if (bytesWritten > 0) {
      if (writtenBytesFile != NULL) {
        fwrite(&(bytesToWrite[*numBytesWritten]), sizeof(PRUint8), bytesWritten,
               writtenBytesFile);
        fflush(writtenBytesFile);
      }
      *numBytesWritten += bytesWritten;
    } 

    // bytesWritten < 0 signifies an error, if it's something other than 
    // PR_WOULD_BLOCK_ERROR then return that error to the user
    else if (bytesWritten < 0) {
      PRErrorCode prError = PR_GetError();
      PRErrorCode prOSErrorCode = PR_GetOSError();
      if ((prError != PR_WOULD_BLOCK_ERROR) &&
          (prError != PR_IO_TIMEOUT_ERROR))
      {
        LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                      "Failed to write data to a TCP socket; error=%d, OSerror=%d",
                      prError, prOSErrorCode ));
#ifndef NDEBUG
        if ((prError != PR_CONNECT_RESET_ERROR)   && 
            (prError != PR_CONNECT_ABORTED_ERROR) &&
            (prError != PR_SOCKET_SHUTDOWN_ERROR))
        {
          BREAKPOINT(); // let's see what errors we get
        }
#endif        
        ERRCHK( NSPR_ERROR_TO_IMQ_ERROR(prError));
      }
    } 

    // bytesWritten == 0 implies that the connection is closed
    else if ((bytesWritten == 0) && (numBytesToWrite != 0)) {
      ERRCHK( IMQ_TCP_CONNECTION_CLOSED );
    }
    
    // If we didn't write everything the first time, then poll to determine
    // when we can write the rest.
    if (*numBytesWritten < numBytesToWrite) {
      PRPollDesc pd;
      pd.in_flags = PR_POLL_WRITE; // | PR_POLL_EXCEPT;
      if ((errorCode = this->pollForEvents(&pd, timeoutRemaining(opStartTime, timeout))) != IMQ_SUCCESS) {
        LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                     "TCPSocket::write, pollForEvents(%d us) returned '%s' (%d).",
                     timeoutMicroSeconds, errorStr(errorCode), errorCode ));
        if (errorCode == IMQ_TIMEOUT_EXPIRED) {
          if (timeoutRemaining(opStartTime, timeout) == 0) {
            LOG_SEVERE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, errorCode,
              "Poll timeout(%d microsecs, returned: %s (%d)) for socket write(wrote %d bytes, expect to write %d bytes. Shutdown socket 0x%p",
              timeoutMicroSeconds, errorStr(errorCode), errorCode,
              *numBytesWritten, numBytesToWrite, this ));
            shutdown();
            errorCode = IMQ_SUCCESS; //let caller return PACKET_OUTPOUT_ERR
            break;
          }
          errorCode = IMQ_SUCCESS;
        }
        // We should 'break;' here, but for some reason,
        // PR_Poll returns with TIMEOUT expired even though the whole timeout
        // hasn't expired.
      }
    }
  } while ((timeoutRemaining(opStartTime, timeout) != 0) && 
           (*numBytesWritten < numBytesToWrite));

  ERRCHK( errorCode );

  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "Wrote %d (of %d) bytes to socket 0x%p, ",
               *numBytesWritten, numBytesToWrite, this->hostSocket ));
  
  return IMQ_SUCCESS;

Cleanup:

  LOG_INFO(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_WRITE_FAILED,
             "Failed to write to the socket because '%s' (%d)", 
             errorStr(errorCode), errorCode ));


  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_WRITE_FAILED,
             "Failed to write %d bytes to socket 0x%p because '%s' (%d)", 
             numBytesToWrite, this->hostSocket, errorStr(errorCode), errorCode ));
  
  return errorCode;
}



/*
 *
 */
iMQError 
TCPSocket::shutdown()
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;

  if (this->hostSocket != NULL) {
    NSPRCHK( PR_Shutdown(this->hostSocket, PR_SHUTDOWN_BOTH) );
  }

  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "Shutting down the socket" ));
           
  return IMQ_SUCCESS;

Cleanup:
  if ((PRInt32)errorCode == PR_NOT_CONNECTED_ERROR) {
  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_SHUTDOWN_FAILED,
                "Failed to shutdown socket because '%s' (%d)", 
                errorStr(errorCode), errorCode ));
  } else {
  LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_SHUTDOWN_FAILED,
                "Failed to shutdown socket because '%s' (%d)", 
                errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

/*
 *
 */
iMQError 
TCPSocket::close()
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;

  if (this->hostSocket != NULL) {
    NSPRCHK( PR_Close(this->hostSocket) );
  }
  
  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "Closed the socket" ));

  this->hostSocket = NULL;

  return IMQ_SUCCESS;

Cleanup:
  this->hostSocket = NULL;

  LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_CLOSE_FAILED,
                "Failed to close socket because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}
/**
 *
 */
TCPSocket::Status
TCPSocket::status() const
{
  CHECK_OBJECT_VALIDITY();

  if (this->hostSocket == NULL) {
    return TCPSocket::NOT_CONNECTED;
  } else {
    return TCPSocket::CONNECTED;
  }
}


/**
 *
 */
PRBool 
TCPSocket::isClosed()
{
  CHECK_OBJECT_VALIDITY();

  return this->status() == TCPSocket::NOT_CONNECTED;
}


/**
 *
 */
iMQError
TCPSocket::setDefaultSockOpts()
{
  CHECK_OBJECT_VALIDITY();

    // Disable Nagel's algorithm
  PRSocketOptionData sockOptionData;
  sockOptionData.value.no_delay = PR_TRUE;
  sockOptionData.option = PR_SockOpt_NoDelay;
  RETURN_IF_NSPR_ERROR( PR_SetSocketOption(this->hostSocket, &sockOptionData) );

  // Make the socket non-blocking
  sockOptionData.value.non_blocking = PR_TRUE;
  sockOptionData.option = PR_SockOpt_Nonblocking;
  RETURN_IF_NSPR_ERROR( PR_SetSocketOption(this->hostSocket, &sockOptionData) );

  return IMQ_SUCCESS;
}


/**
 *
 */
iMQError
TCPSocket::cacheLocalAddr()
{
  CHECK_OBJECT_VALIDITY();

  RETURN_IF_NSPR_ERROR( PR_GetSockName(this->hostSocket,
                                       &(this->localAddr)) );
  this->localPort = PR_ntohs(localAddr.inet.port);

  // PORTABILITY: this doesn't support IPv6
  if (this->localAddr.raw.family == PR_AF_INET6) {
    this->localIP.setAddressFromIPv6Address((const PRUint8 *)&(localAddr.ipv6.ip));
  } else {
    this->localIP.setIPv4AddressFromNetOrderInt(localAddr.inet.ip);
  }

  return IMQ_SUCCESS;
}



/**
 *
 */
iMQError
TCPSocket::pollForEvents(PRPollDesc * const pd, const PRIntervalTime timeout)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( pd );
  pd->fd = this->hostSocket;
  pd->out_flags = 0;
  PRInt32 availableFDs = PR_Poll(pd, 1, timeout);

  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "PR_Poll(%d) returned %d.", 
               timeout, availableFDs ));

  if (availableFDs == 0) {
    return IMQ_TIMEOUT_EXPIRED;
  }
  if (availableFDs < 0) {
    if (PR_GetError() != PR_PENDING_INTERRUPT_ERROR) {
      BREAKPOINT(); // see what errors we got.
    }
    RETURN_NSPR_ERROR_CODE();
  }
  ASSERT( availableFDs == 1 );
  if ((pd->in_flags & pd->out_flags) == 0) {
    LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_POLL_ERROR,
                  "PR_Poll() returned %d with out_flags=%d, error=%d, oserror=%d",
                  availableFDs, pd->out_flags, PR_GetError(), PR_GetOSError() ));
    //BREAKPOINT(); // see what errors we get.
    return IMQ_POLL_ERROR;
  }
  return IMQ_SUCCESS;
}

#if 0
/**
 *
 */
iMQError
TCPSocket::pollForEvents(PRPollDesc * const pd, const PRIntervalTime timeout)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( pd );
  pd->fd = this->hostSocket;
  pd->out_flags = 0;
  PRInt32 availableFDs = PR_Poll(pd, 1, timeout);

  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "PR_Poll(%d) returned %d.", 
               timeout, availableFDs ));

  if (availableFDs == 0) {
    return IMQ_TIMEOUT_EXPIRED;
  }
  if (availableFDs < 0) {
    if (PR_GetError() != PR_PENDING_INTERRUPT_ERROR) {
      BREAKPOINT(); // see what errors we got.
    }
    RETURN_NSPR_ERROR_CODE();
  }
  ASSERT( availableFDs == 1 );
  if ((pd->in_flags & pd->out_flags) == 0) {
    BREAKPOINT(); // see what errors we get.
    return IMQ_POLL_ERROR;
  }
  return IMQ_SUCCESS;
}
#endif

//
// THE METHODS BELOW ARE ONLY USED FOR TESTING, SO WE CAN TEST TCPSOCKET ON
// THE LOCAL MACHINE.
//

/**
 * 
 */
iMQError
TCPSocket::bind(const char * localName, 
                const PRUint16 localPortArg)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode;

  RETURN_ERROR_IF_NULL( localName );
  RETURN_ERROR_IF( localPortArg == 0, IMQ_INVALID_PORT );
  
  // Make sure we are not already connected, and that
  // connectionProperties is valid
  RETURN_UNEXPECTED_ERROR_IF( this->hostSocket != NULL,
                              IMQ_TCP_ALREADY_CONNECTED );

  // Lookup the local host
  RETURN_IF_NSPR_ERROR( PR_GetHostByName(localName,    
                                         this->localEntryData,    
                                         sizeof(this->localEntryData),
                                         &(this->localEntry)) );

  // Get the local host's address
  RETURN_IF_NSPR_ERROR( PR_EnumerateHostEnt(0,
                                            &(this->localEntry),
                                            (PRUint16)localPortArg,
                                            &(this->localAddr)) );
  
  // Create a new socket
  RETURN_IF_OUT_OF_MEMORY( this->hostSocket = PR_OpenTCPSocket(this->localAddr.raw.family) );

  // Make the socket non-blocking and disable Nagel's algorithm
  ERRCHK( this->setDefaultSockOpts() );

  // Bind to the specified address
  NSPRCHK( PR_Bind(this->hostSocket, &(this->localAddr)) );
  
  return IMQ_SUCCESS;

Cleanup:
  this->reset();
  return errorCode;
}


/*
 *
 */
iMQError
TCPSocket::listen()
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->hostSocket == NULL, IMQ_TCP_CONNECTION_CLOSED );
  RETURN_IF_NSPR_ERROR( PR_Listen(this->hostSocket, 1) );
  return IMQ_SUCCESS;
}

/**
 *
 */
iMQError
TCPSocket::accept(TCPSocket ** const acceptingTCPSocket,
                  const PRUint32 timeoutMicroSeconds)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( acceptingTCPSocket );
  *acceptingTCPSocket = NULL;
  RETURN_ERROR_IF( this->hostSocket == NULL, IMQ_TCP_CONNECTION_CLOSED );

  // Convert the timeout in microseconds into a system dependent time
  // interval, and get the time that the operation started.
  PRIntervalTime timeout = microSecondToIntervalTimeout(timeoutMicroSeconds);
  PRIntervalTime opStartTime = PR_IntervalNow();

  PRFileDesc * acceptingSocket = NULL;
  TCPSocket * tcpSocket = NULL;

  // Try to accept an incoming connection
  do {
    acceptingSocket = PR_Accept(this->hostSocket, NULL, 0);
    if (acceptingSocket == NULL) {
      PR_Sleep(0); // Yield
    }
  } while ((timeoutRemaining(opStartTime, timeout) != 0) && 
           (acceptingSocket == NULL) && (PR_GetError() == PR_WOULD_BLOCK_ERROR));

  // Return an error if the accept was not successful
  if (acceptingSocket == NULL) {
    if (timeoutRemaining(opStartTime, timeout) == 0) {
      return IMQ_TIMEOUT_EXPIRED;
    } else {
      RETURN_NSPR_ERROR_CODE();
    }
  }

  // If the accept was successful, then create a new TCPSocket
  iMQError errorCode = IMQ_SUCCESS;


#if defined(WIN32)  
  //MEMCHK( tcpSocket = new TCPSocket("c:/temp/server") );
#else
  //MEMCHK( tcpSocket = new TCPSocket("/home/de134463/temp/server") );
#endif
  MEMCHK( tcpSocket = new TCPSocket() );
  ERRCHK( tcpSocket->setAcceptSocket(acceptingSocket) );

  *acceptingTCPSocket = tcpSocket;

  return IMQ_SUCCESS;

Cleanup:
  if (acceptingSocket != NULL) {
    PR_Close(acceptingSocket);
  }
  DELETE( tcpSocket );

  return errorCode;
}

/**
 * This is only used for testing
 */
iMQError
TCPSocket::setAcceptSocket(PRFileDesc * const acceptingSocket)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( acceptingSocket );
  RETURN_ERROR_IF( this->hostSocket != NULL, IMQ_TCP_ALREADY_CONNECTED );

  this->hostSocket = acceptingSocket;

  // Get the name of the local connection (address and port)
  iMQError errorCode = IMQ_SUCCESS;
  ERRCHK( this->cacheLocalAddr() );

  return IMQ_SUCCESS;

Cleanup:
  this->hostSocket = NULL;
  return errorCode;
}
