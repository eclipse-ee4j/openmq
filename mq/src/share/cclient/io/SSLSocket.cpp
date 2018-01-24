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
 * @(#)SSLSocket.cpp	1.14 06/26/07
 */ 

#include "SSLSocket.hpp"
#include "../util/UtilityMacros.h"
#include "../util/PRTypesUtils.h"
#include "../util/LogUtils.hpp"
#include "../basictypes/UTF8String.hpp"
#include "../client/NSSInitCall.h"

#include <private/pprio.h>

// nss includes
#include <cert.h>
#include <certt.h>
#include <pk11func.h>
#include <ssl.h>
#include <sslproto.h>
#include <hasht.h>

PRBool SSLSocket::initialized = PR_FALSE;


// The handler used to check certificates that fail NSPR's checks
extern "C" {
 static SECStatus sslBadCertHandler(void *arg, PRFileDesc *socket);
}

/*
 *
 */
SSLSocket::SSLSocket() : TCPSocket()
{
  CHECK_OBJECT_VALIDITY();

  this->init();
}

/*
 *
 */
SSLSocket::~SSLSocket()
{
  this->reset();
}

/*
 *
 */
void
SSLSocket::init()
{
  this->hostIsTrusted = PR_FALSE;
  this->useCertMD5Hash = PR_FALSE;
  this->hostCertificateMD5HashStr = NULL;
}

/*
 *
 */
void
SSLSocket::reset()
{
  this->TCPSocket::reset();

  DELETE( this->hostCertificateMD5HashStr );
  init();
}

/*
 *
 */
iMQError
SSLSocket::setSSLParameters(const PRBool hostIsTrustedArg,
                            const PRBool useCertMD5HashArg,
                            const char * const hostCertMD5HashStr)
{
  iMQError errorCode = IMQ_SUCCESS;
  this->hostIsTrusted = hostIsTrustedArg;
  this->useCertMD5Hash = useCertMD5HashArg;
  CNDCHK( this->initialized == PR_FALSE, MQ_SSL_NOT_INITIALIZED );

  if (this->hostIsTrusted == PR_FALSE && calledNSS_Init() == PR_FALSE) {
    LOG_SEVERE(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, MQ_SSL_INIT_ERROR,
                 "MQInitializeSSL must be called before creating any connections" ));
    ERRCHK( MQ_SSL_INIT_ERROR );
  }

  if (hostCertMD5HashStr != NULL) {
    MEMCHK( this->hostCertificateMD5HashStr = new UTF8String(hostCertMD5HashStr) );
  } else {
    DELETE( this->hostCertificateMD5HashStr );
  }

  return IMQ_SUCCESS;
Cleanup:
  return errorCode;
}

/*
 *
 */
iMQError
SSLSocket::preConnect(const char * hostName)
{
  iMQError errorCode = IMQ_SUCCESS;
  PRFileDesc * sslSocket = NULL;

  CNDCHK( this->hostSocket == NULL, IMQ_SSL_ERROR );
  CNDCHK( this->initialized == PR_FALSE, MQ_SSL_NOT_INITIALIZED );
  
  // The new file descriptor returned by SSL_ImportFD is not
  // necessarily equal to the original NSPR file descriptor. If, after
  // calling SSL_ImportFD, the file descriptors are not equal, you
  // should perform all operations on the new PRFileDesc structure,
  // never the old one. Even when it's time to close the file
  // descriptor, always close the new PRFileDesc structure, never the
  // old one.
  
  sslSocket = SSL_ImportFD(NULL, this->hostSocket);
  CNDCHK( sslSocket == NULL, IMQ_SSL_SOCKET_INIT_ERROR );
  LOG_FINE(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "Successfully converted socket=0x%p to SSL socket=0x%p.",
             this->hostSocket, sslSocket ));
  this->hostSocket = sslSocket;

  // Set configuration options.
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_SECURITY, PR_TRUE) );
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_HANDSHAKE_AS_CLIENT, PR_TRUE) );
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_ENABLE_FDX, PR_TRUE) );
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_ENABLE_SSL2, PR_FALSE) );
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_ENABLE_SSL3, PR_TRUE) );
  NSPRCHK( SSL_OptionSet(this->hostSocket, SSL_ENABLE_TLS, PR_TRUE) );
  NSPRCHK( SSL_SetURL(this->hostSocket, hostName) );
  NSPRCHK( SSL_BadCertHook(this->hostSocket,
                           (SSLBadCertHandler)sslBadCertHandler,
                           this) );

  return IMQ_SUCCESS;
Cleanup:

  LOG_WARNING(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SSL_SOCKET_INIT_ERROR,
                "Failed to initialize ssl socket=0x%p to SSL socket "
                "because '%s' (%d).", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}

MQError
SSLSocket::doConnect(PRNetAddr *addr, PRIntervalTime timeout, const char * hostName)
{

  MQError errorCode = MQ_SUCCESS;
  PRStatus status;

  ERRCHK_TRACE( this->preConnect(hostName), "doConnect", "mq" );

  status = PR_Connect(this->hostSocket, addr, timeout);
  if (status != PR_SUCCESS) {
    LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SOCKET_ERROR,
                 "SSL TCP socket connect failed, error=%d, oserror=%d", PR_GetError(), PR_GetOSError() ));
    ntCancelIO();
    ERRCHK_TRACE( NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()), "PR_Connect", "nspr" );
  }

  ERRCHK_TRACE( this->postConnect(), "doConnect", "mq" );

  return MQ_SUCCESS;

Cleanup:
  MQ_ERROR_TRACE( "doConnect", errorCode );
  return errorCode;
}

/*
 *
 */
iMQError
SSLSocket::postConnect()
{
  iMQError errorCode = IMQ_SUCCESS;

  CNDCHK( this->hostSocket == NULL, IMQ_TCP_CONNECTION_CLOSED );

  LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "SSL reseting handshake." ));
  NSPRCHK( SSL_ResetHandshake(this->hostSocket, PR_FALSE) );

  LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "SSL force handshake." ));
  NSPRCHK( SSL_ForceHandshake(this->hostSocket) );

  return IMQ_SUCCESS;

Cleanup:
  return errorCode;
}


/**
 *
 */
iMQError
SSLSocket::setDefaultSockOpts()
{
  CHECK_OBJECT_VALIDITY();

    // Disable Nagel's algorithm
  PRSocketOptionData sockOptionData;
  sockOptionData.value.no_delay = PR_TRUE;
  sockOptionData.option = PR_SockOpt_NoDelay;
  RETURN_IF_NSPR_ERROR( PR_SetSocketOption(this->hostSocket, &sockOptionData) );

  // Make the socket blocking
  sockOptionData.value.non_blocking = PR_FALSE;
  sockOptionData.option = PR_SockOpt_Nonblocking;
  RETURN_IF_NSPR_ERROR( PR_SetSocketOption(this->hostSocket, &sockOptionData) );

  return IMQ_SUCCESS;
}


/**
 * In the WINNT build, we need to use PR_NT_CancelIo if
 * an IO operation on a blocking socket fails with 
 * PR_IO_TIMEOUT_ERROR or PR_PENDING_INTERRUPT_ERROR.
 *
 * From NSPR core engineer: 
 * PR_NT_CancelIo is a supported API. It is fine to use it.
 *
 */
void
SSLSocket::ntCancelIO()
{
#ifdef XP_WIN32

  PRErrorCode err = PR_GetError();
  PRInt32     oserr = PR_GetOSError();

  if ((err == PR_IO_TIMEOUT_ERROR) || (err == PR_PENDING_INTERRUPT_ERROR)) {
    if (PR_NT_CancelIo(this->hostSocket) != PR_SUCCESS) {
      //should  not happen
      LOG_SEVERE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SOCKET_ERROR,
             "Failed to cancel IO for  socket 0x%p, error=%d, oserror=%d", 
              this->hostSocket, PR_GetError(), PR_GetOSError() ));
      ASSERT(0);
    }
  }

  PR_SetError(err, oserr);

#endif
}


MQError 
SSLSocket::read(const PRInt32           numBytesToRead,
                const PRUint32          timeoutMicroSeconds, 
                      PRUint8 *  const  bytesToRead, 
                      PRInt32 *  const  numBytesRead)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 bytesRead;
  PRIntervalTime timeout;
  PRIntervalTime opStartTime;
  PRIntervalTime timeRemaining;

  NULLCHK( numBytesRead );
  *numBytesRead = 0;
  if (numBytesToRead == 0) {
    return MQ_SUCCESS;
  }
  CNDCHK( numBytesToRead < 0, MQ_NEGATIVE_AMOUNT );
  NULLCHK( bytesToRead );
  CNDCHK( this->hostSocket == NULL, MQ_TCP_CONNECTION_CLOSED );

  bytesRead = 0;
  timeout = microSecondToIntervalTimeout(timeoutMicroSeconds);
  timeRemaining = timeout;

  do {
    opStartTime = PR_IntervalNow();

    bytesRead = PR_Recv(this->hostSocket, 
                        &(bytesToRead[*numBytesRead]),                               
                        numBytesToRead - *numBytesRead,
                        0,
                        timeRemaining);

    if (bytesRead > 0) {

      *numBytesRead += bytesRead;

    } else if (bytesRead < 0) {

      PRErrorCode prError = PR_GetError();
      if (prError != PR_IO_PENDING_ERROR) {
        ntCancelIO();
        ERRCHK( NSPR_ERROR_TO_IMQ_ERROR(prError));
      }

    } else { 

      // bytesRead == 0 implies that the connection is closed
      ERRCHK( MQ_TCP_CONNECTION_CLOSED );
    }

    timeRemaining = timeoutRemaining(opStartTime, timeRemaining);

  } while ((timeRemaining != 0) && (*numBytesRead < numBytesToRead));

  if ((timeRemaining == 0) && ((*numBytesRead) < numBytesToRead)) {
    ERRCHK_TRACE( MQ_TIMEOUT_EXPIRED, "read", "mq" );
  }

  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "Read %d (of %d) bytes from socket 0x%p.", 
               *numBytesRead, numBytesToRead, this->hostSocket ));
  
  return MQ_SUCCESS;

Cleanup:
  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SOCKET_READ_FAILED,
             "Failed to read from the SSL socket because '%s' (%d)", 
             errorStr(errorCode), errorCode ));


  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SOCKET_READ_FAILED,
             "Failed to read %d bytes from SSL socket 0x%p because '%s' (%d)", 
             numBytesToRead, this->hostSocket, errorStr(errorCode), errorCode ));
  
  return errorCode;
}


MQError 
SSLSocket::write(const PRInt32           numBytesToWrite,
                 const PRUint8 * const   bytesToWrite,
                 const PRUint32          timeoutMicroSeconds, 
                       PRInt32 * const   numBytesWritten)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRIntervalTime timeout;
  PRInt32 bytesWritten;

  
  NULLCHK( numBytesWritten );
  *numBytesWritten = 0;
  if (numBytesToWrite == 0) {
    return MQ_SUCCESS;
  }
  CNDCHK( numBytesToWrite < 0, MQ_NEGATIVE_AMOUNT );
  NULLCHK( bytesToWrite );
  CNDCHK( this->hostSocket == NULL, MQ_TCP_CONNECTION_CLOSED );

  timeout = microSecondToIntervalTimeout(timeoutMicroSeconds);

  bytesWritten = PR_Send(this->hostSocket, 
                         &(bytesToWrite[*numBytesWritten]),                               
                         numBytesToWrite,
                         0,
                         timeout);
    
  LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "PR_Send(%d) returned %d, (numBytesToWrite=%d, *numBytesWritten=%d)",
                numBytesToWrite - *numBytesWritten, bytesWritten,
                numBytesToWrite, *numBytesWritten ));
    
  if (bytesWritten != numBytesToWrite) {
    if (bytesWritten < 0) {
      ntCancelIO();
    }
    ERRCHK( NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()) );
  }

  *numBytesWritten = bytesWritten;

  return MQ_SUCCESS;

Cleanup:

  LOG_INFO(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_WRITE_FAILED,
             "Failed to write to the SSL socket because '%s' (%d)", 
             errorStr(errorCode), errorCode ));


  LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SOCKET_WRITE_FAILED,
             "Failed to write %d bytes to SSL socket 0x%p because '%s' (%d)", 
             numBytesToWrite, this->hostSocket, errorStr(errorCode), errorCode ));
  
  return errorCode;
}


/*
 *
 */
const PRInt32 MD5_NUM_HASH_BYTES = 20;
const PRInt32 MD5_HASH_STR_LEN = 47;
SECStatus
SSLSocket::checkBadCertificate(PRFileDesc * const socket)
{
  iMQError errorCode = IMQ_SUCCESS;
  SECStatus certAcceptStatus = SECFailure;
  CERTCertificate * cert = NULL;
  PRErrorCode sslError = IMQ_SUCCESS;
  PRUint8 md5Fingerprint[MD5_NUM_HASH_BYTES];
  char * md5FingerPrintStr = NULL;
  SECItem fingerPrintItem;
  
  CNDCHK( this->hostSocket == NULL, IMQ_SSL_CERT_ERROR );
  CNDCHK( socket != this->hostSocket, IMQ_SSL_CERT_ERROR );


  // Trust any certificate
  if (this->hostIsTrusted) {
    certAcceptStatus = SECSuccess;
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "SSL certificate authentication rejected because %s, but trusting host anyway",
                errorStr(NSPR_ERROR_TO_IMQ_ERROR(PR_GetError())) ));
  }

  // Compare the MD5Hash fingerprint
  else if ((this->useCertMD5Hash) && (this->hostCertificateMD5HashStr != NULL)) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "SSL certificate authentication rejected because %s. Verifying fingerprint.",
                errorStr(NSPR_ERROR_TO_IMQ_ERROR(PR_GetError())) ));

    sslError = PORT_GetError();

    // Get the certificate
    cert = SSL_PeerCertificate(socket);
    CNDCHK( cert == NULL, IMQ_SSL_CERT_ERROR );

    // Calculate the MD5 fingerprint
    memset(md5Fingerprint, 0, sizeof(md5Fingerprint));
    PK11_HashBuf(SEC_OID_MD5,
                 md5Fingerprint,
                 cert->derCert.data,
                 cert->derCert.len);
    
    // Convert the MD5 hash string, that looks like
    //  B1:51:09:B5:32:90:30:DF:9C:16:D1:D5:DE:BF:C5:22
    fingerPrintItem.data = md5Fingerprint;
    fingerPrintItem.len = MD5_LENGTH;
    md5FingerPrintStr = CERT_Hexify(&fingerPrintItem, 1);
    NULLCHK( md5FingerPrintStr );
    ASSERT( STRLEN(md5FingerPrintStr) == (PRUint32)MD5_HASH_STR_LEN );

    if (STRCMP( md5FingerPrintStr,
                this->hostCertificateMD5HashStr->getCharStr() ) == 0)
    {
      certAcceptStatus = SECSuccess;
    } else {
      LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                    "SSL certificate fingerprint mismatch. "
                    "Expected = '%s', Received = '%s'.",
                    this->hostCertificateMD5HashStr->getCharStr(),
                    md5FingerPrintStr ));
    }
  } else {
    LOG_WARNING(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
    "SSL certificate authentication rejected because %s", errorStr(NSPR_ERROR_TO_IMQ_ERROR(PR_GetError())) ));
  }

  if (certAcceptStatus == SECSuccess) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "SSL host certificate verification succeeded." ));
  } else {
    LOG_WARNING(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                  "SSL host certificate verfication failed." ));
  }

  
Cleanup:  
  if (cert != NULL) {
    CERT_DestroyCertificate(cert);
  }
  if (md5FingerPrintStr != NULL) {
    PORT_Free(md5FingerPrintStr);
  }
  
  return certAcceptStatus;
}

/*
 *
 */
SECStatus
sslBadCertHandler(void *arg, PRFileDesc *socket)
{
  SSLSocket * sslSocket = NULL;
  if ((arg == NULL) || (socket == NULL)) {
    return SECFailure;
  }
  sslSocket = (SSLSocket*)arg;
  
  return sslSocket->checkBadCertificate(socket);
}


/**
 * Static SSL initialization function.
 */
iMQError
SSLSocket::initializeSSL(const char * const certificateDirectory)
{
  iMQError errorCode = IMQ_SUCCESS;
   char exportPolicy[] = "DOMESTIC";

  NULLCHK( certificateDirectory );
  NULLCHK( exportPolicy );

  // Only initialize it once
  CNDCHK( SSLSocket::initialized, IMQ_SSL_ALREADY_INITIALIZED );

  if (NSS_VersionCheck(NSS_VERSION) == PR_FALSE) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, MQ_INCOMPATIBLE_LIBRARY,
              "The version of the NSS or NSPR library linked to by this application is not compatible with the version supported by the MQ API (NSS %s)", NSS_VERSION ));
    ERRCHK( MQ_INCOMPATIBLE_LIBRARY );
  }

  // Initialize the NSS libraries.
  if (PR_Initialized() != PR_TRUE) {
    PR_Init(PR_SYSTEM_THREAD, PR_PRIORITY_NORMAL, 1);
    LOG_FINE(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS, "Initialized NSPR runtime." ));
  }
  //certificateDirectory must not NULL 
  NSPRCHK_TRACE( callOnceNSS_Init(certificateDirectory), "callOnceNSS_Init", "mq" );
  if (calledNSS_Init() == PR_TRUE) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
              "Using '%s' as the SSL certificate db directory.", certificateDirectory ));
  }

  // Set the ciphers that can be used based on our location
  if (STRCMPI(exportPolicy, "DOMESTIC") == 0) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "SSL Using DOMESTIC cipher policy.",
               certificateDirectory ));
    NSPRCHK_TRACE( NSS_SetDomesticPolicy(), "NSS_SetDomesticPolicy", "nss" );
    // All cipher suites except RSA_NULL_MD5 are enabled by Domestic Policy.
    SSL_CipherPrefSetDefault(SSL_RSA_WITH_NULL_MD5, PR_TRUE);
  } else if (STRCMPI(exportPolicy, "FRANCE") == 0) {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "SSL Using FRANCE cipher policy.",
               certificateDirectory ));
    NSPRCHK( NSS_SetFrancePolicy() );
  } else {
    LOG_INFO(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "SSL Using EXPORT cipher policy.",
               certificateDirectory ));
    NSPRCHK( NSS_SetExportPolicy() );
  }
  SSL_ClearSessionCache();

  // Now we are initialized.
  SSLSocket::initialized = PR_TRUE;
  
  return IMQ_SUCCESS;
Cleanup:
  LOG_SEVERE(( CODELOC, SSL_LOG_MASK, NULL_CONN_ID, IMQ_SSL_INIT_ERROR,
               "Failed to initialize SSL because '%s' (%d).", 
               errorStr(errorCode), errorCode ));
  MQ_ERROR_TRACE( "initializeSSL", errorCode ); 
  return errorCode;
}

