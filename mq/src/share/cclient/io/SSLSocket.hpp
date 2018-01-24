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
 * @(#)SSLSocket.hpp	1.6 06/26/07
 */ 

#ifndef SSLSOCKET_HPP
#define SSLSOCKET_HPP

#include "TCPSocket.hpp"
#include <nss.h>


/**
 * This class implements a TCP connection to a host. 
 */
class SSLSocket : public TCPSocket {
private:
  PRBool          hostIsTrusted;
  PRBool          useCertMD5Hash;
  UTF8String *    hostCertificateMD5HashStr;

  // true iff SSL has been initialized
  static PRBool   initialized;
  
  void init();
  void ntCancelIO();

public:
  /**
   * Constructor.
   */
  SSLSocket();

  /**
   *
   */
  virtual ~SSLSocket();

  /**
   *
   */
  virtual void reset();

  /**
   *
   */
  virtual iMQError setSSLParameters(const PRBool hostIsTrusted,
                                    const PRBool useCertMD5Hash,
                                    const char * const hostCertMD5HashStr);
  
  /**
   * This method is called immediately before the call to PR_Connect.  It
   * sets some SSL specific options.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise.
   */
  virtual iMQError preConnect(const char * hostName);


  /**
   * This method does the connect 
   *
   * @param addr A pointer to the address of the peer to which this socket
   *        is to be connected
   * @param timeout The time limit for completion of the connect operation.
   */
  virtual MQError doConnect(PRNetAddr *addr, PRIntervalTime timeout, const char * hostName);


  /**
   * This method is called immediately after the connection to the
   * host completes.  It resets the SSL handshake so we don't run into
   * problems using blocking sockets.
   *
   * @return IMQ_SUCCESS if successful and an error otherwise.  */
  virtual iMQError postConnect();

  virtual iMQError setDefaultSockOpts();


 /**
   * This method reads numBytesToRead bytes from the connection and places the
   * results in bytesRead.
   *
   * @param numBytesToRead is the number of bytes to read from the connection
   * @param timeoutMicroSeconds the number of microseconds to wait for the read
   *        to complete.  A value of 0 implies do not wait, and a value of
   *        0xFFFFFFFF implies wait forever.
   * @param bytesRead is the buffer where the bytes read from the input stream
   *        are placed.
   * @param numBytesRead is the number of bytes that were actually read from
   *        the connection.
   * @return IMQ_SUCCESS if successful and an error otherwise.
   */
  virtual iMQError read(const PRInt32          numBytesToRead,
                        const PRUint32         timeoutMicroSeconds,
                              PRUint8 * const  bytesRead,
                              PRInt32 * const  numBytesRead);

  /**
   * This method writes numBytesToWrite bytes from the buffer bytesToWrite to
   * the connection.
   *
   * @param numBytesToWrite is the number of bytes to write to the connection
   * @param bytesToWrite is the buffer where the bytes are written from
   * @param timeoutMicroSeconds the number of microseconds to wait for the write
   *        to complete.  A value of 0 implies do not wait, and a value of
   *        0xFFFFFFFF implies wait forever.
   * @param numBytesWritten is the number of bytes that were actually written to
   *        the connection.
   * @return IMQ_SUCCESS if successful and an error otherwise.
   */
  virtual iMQError write(const PRInt32          numBytesToWrite,
                         const PRUint8 * const  bytesToWrite,
                         const PRUint32         timeoutMicroSeconds,
                               PRInt32 * const  numBytesWritten);


  /**
   *
   */
  virtual SECStatus checkBadCertificate(PRFileDesc * const socket);


  /**
   *
   */
  static iMQError initializeSSL(const char * const certificateDirectory);

  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  SSLSocket(const SSLSocket& sslSocket);
  SSLSocket& operator=(const SSLSocket& sslSocket);
};


#endif // SSLSOCKET_HPP












