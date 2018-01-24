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
 * @(#)JMQDigestAuthenticationHandler.cpp	1.9 06/26/07
 */ 

#include "JMQDigestAuthenticationHandler.hpp"
#include "../../util/UtilityMacros.h"
#include "../../util/LogUtils.hpp"
#include "../../client/iMQConstants.hpp"
#include "../../serial/SerialDataOutputStream.hpp"
#include "../NSSInitCall.h"

#include <pk11func.h>
#include <secerr.h>

/*
 *
 */
JMQDigestAuthenticationHandler::JMQDigestAuthenticationHandler()
{
  CHECK_OBJECT_VALIDITY();

  username       = NULL;
  password       = NULL;
  authProperties = NULL;
}


/*
 *
 */
JMQDigestAuthenticationHandler::~JMQDigestAuthenticationHandler()
{
  CHECK_OBJECT_VALIDITY();

  username       = NULL;
  password       = NULL;
  authProperties = NULL;
}

/*
 *
 */
const char * 
JMQDigestAuthenticationHandler::getType() const
{
  CHECK_OBJECT_VALIDITY();

  return IMQ_AUTHTYPE_JMQDIGEST;
}

/*
 *
 */
iMQError
JMQDigestAuthenticationHandler::init(const UTF8String * const usernameArg, 
                                     const UTF8String * const passwordArg,
                                     const Properties * const authPropertiesArg)
{
  CHECK_OBJECT_VALIDITY();

  this->username       = usernameArg;
  this->password       = passwordArg;
  this->authProperties = authPropertiesArg;
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
JMQDigestAuthenticationHandler::handleRequest(const PRUint8 *  const authRequest,
                                              const PRInt32          authRequestLen,
                                                    PRUint8 ** const authReply,
                                                    PRInt32 *  const authReplyLen,
                                              const PRInt32          sequenceNum)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( authRequest );
  RETURN_ERROR_IF_NULL( authReply );
  RETURN_ERROR_IF_NULL( authReplyLen );
  RETURN_ERROR_IF_NULL( this->username );
  RETURN_ERROR_IF_NULL( this->password );
  // RETURN_ERROR_IF_NULL( this->authProperties ); // if authProperties is used, uncomment this
  UNUSED( sequenceNum );

  // Temporary variables
  iMQError     errorCode           = MQ_SUCCESS;
  char *       userpwdStr          = NULL;
  char *       hashedUserpwdStr    = NULL;
  PRInt32      userpwdStrLen       = 0;
  char *       credentialStr       = NULL;
  char *       hashedCredentialStr = NULL;
  PRInt32      credentialStrLen    = 0;
  PRInt32      replyLen            = 0;
  PRUint8 *    reply               = NULL;
  UTF8String * credential          = NULL;
  SerialDataOutputStream out;


  // Put 'username:password' into a char *
  userpwdStrLen = (PRInt32)(username->length() + STRLEN(":") + password->length());
  MEMCHK( userpwdStr = new char[userpwdStrLen + 1] );
  SNPRINTF(userpwdStr, userpwdStrLen + 1, "%s:%s", username->getCharStr(),
                                               password->getCharStr());
  ASSERT( userpwdStr[userpwdStrLen] == '\0' );
  ASSERT( (PRInt32)STRLEN(userpwdStr) == userpwdStrLen );

  // Hash 'username:password'
  ERRCHK( this->getMD5HashString(userpwdStr, (PRInt32)STRLEN(userpwdStr), &hashedUserpwdStr) );
  ASSERT( hashedUserpwdStr != NULL );

  // Put 'userpwdStr:authRequest' into a string
  credentialStrLen = (PRInt32)(STRLEN(hashedUserpwdStr) + STRLEN(":") + authRequestLen);
  MEMCHK( credentialStr = new char[credentialStrLen + 1] );
  SNPRINTF( credentialStr, credentialStrLen + 1, "%s:",
            hashedUserpwdStr);
  memcpy(&credentialStr[STRLEN(hashedUserpwdStr)+1], authRequest, authRequestLen); 
  credentialStr[credentialStrLen] = '\0';


  // Added because of a problem on Solaris
  if ((PRInt32)STRLEN(credentialStr) != credentialStrLen) {
    LOG_FINE(( CODELOC, AUTH_HANDLER_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
               "JMQDigestAuthenticationHandler::handleRequest(), "
               "hashedUserpwdStr='%s', authRequest='%s', "
               "credentialStr='%s', strlen(credentialStr)=%d, "
               "credentialStrLen=%d",
               hashedUserpwdStr, authRequest, credentialStr,
               STRLEN(credentialStr), credentialStrLen ));
  }
               
  ASSERT( (PRInt32)STRLEN(credentialStr) == credentialStrLen );
  ASSERT( credentialStr[credentialStrLen-1] == authRequest[authRequestLen-1] );

  // Hash 'userpwdStr:authRequest'
  ERRCHK( this->getMD5HashString(credentialStr, (PRInt32)STRLEN(credentialStr), 
                                 &hashedCredentialStr) );
  ASSERT( hashedCredentialStr != NULL );

  // We want to output username followed by hashedCredentialStr in
  // UTF8 format with each preceded by a network order byte order
  // short denoting the length of the string.  This is not the most
  // efficient way to do it, but authentication is rare so that's okay.
  MEMCHK( credential = new UTF8String(hashedCredentialStr) );

  // Write out the two strings
  ERRCHK( username->write(&out) );
  ERRCHK( credential->write(&out) );
  
  // Allocate an array for the reply and copy the written bytes into it.
  replyLen = out.numBytesWritten();
  ASSERT( replyLen == (username->length() + 2 + credential->length() + 2) );
  reply = new PRUint8[replyLen];
  MEMCHK( reply );
  memcpy( reply, out.getStreamBytes(), replyLen );
    
  // Assign to output parameters 
  *authReply = reply;
  *authReplyLen = replyLen;
  
  DELETE( credential );
  DELETE_ARR( userpwdStr );
  DELETE_ARR( hashedUserpwdStr );
  DELETE_ARR( credentialStr );
  DELETE_ARR( hashedCredentialStr );

  return IMQ_SUCCESS;

 Cleanup:
  DELETE_ARR( userpwdStr );
  DELETE_ARR( hashedUserpwdStr );
  DELETE_ARR( credentialStr );
  DELETE_ARR( hashedCredentialStr );
  DELETE( credential );
  DELETE( reply );

  return errorCode;
}


/*
 *
 */
static const char * HEX_VALUE_STRS[] = 
{
  "0", "1", "2", "3", "4", "5", "6", "7", 
  "8", "9", "a", "b", "c", "d", "e", "f"
};
//in header now, static const PRInt32 MD5_HASH_LEN = 16; // 128 bits 
static const PRInt32 MD5_HASH_STR_LEN = 32 + 1; // because 0x3D becomes "3D" and one byte for the sign
iMQError
JMQDigestAuthenticationHandler::getMD5HashString(const char *  const buf,
                                                 const PRInt32       bufLen,
                                                       char ** const hashedBufStr)
{
  RETURN_ERROR_IF_NULL( buf );
  RETURN_ERROR_IF_NULL( hashedBufStr );
  *hashedBufStr = NULL;

  PRUint8 hashedBuf[MD5_HASH_LEN];

  // Hash the buffer into a 128 bit (16-byte) buffer
  if (PK11_HashBuf(SEC_OID_MD5, (PRUint8*)hashedBuf, (PRUint8*)buf, bufLen) != SECSuccess) {
    PRErrorCode prerr =PR_GetError();
    ERROR_TRACE( "PK11_HashBuf", "nss", prerr );
    LOG_SEVERE(( CODELOC, AUTH_HANDLER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                    "Failed to MD5 hash a string because '%s' (%d)",
                    errorStr(NSPR_ERROR_TO_IMQ_ERROR(prerr)), NSPR_ERROR_TO_IMQ_ERROR(prerr) ));
    return IMQ_MD5_HASH_FAILURE;
  }

  PRUint8 signedHashedBuf[MD5_HASH_LEN];
  PRBool isNegative = PR_FALSE;
  convertMD5HashToSigned(hashedBuf, signedHashedBuf, &isNegative);

  // Convert the hashed buffer into a string representation
  char * hashStr = new char[MD5_HASH_STR_LEN + 1];
  RETURN_IF_OUT_OF_MEMORY( hashStr );
  STRCPY(hashStr, isNegative ? "-" : "");
  PRBool leadingZeroes = PR_TRUE;
  for (int i = 0; i < MD5_HASH_LEN; i++) { // big endian
    // skip leading 0's
    if (leadingZeroes            &&  // if we haven't seen a nonzero digit and
        signedHashedBuf[i] == 0  &&  // the current digit is 0
        i != (MD5_HASH_LEN -1)       // just in case we get 0x00000000000000000000000000000000
        ) 
    {
      continue;
    }

    // If the first non-zero byte is 0x07, then skip the leading 0
    // and only print out 7
    if (!leadingZeroes ||
        (((signedHashedBuf[i] >> 4) & 0x0F) != 0))
    {
      STRCAT(hashStr, HEX_VALUE_STRS[(signedHashedBuf[i] >> 4) & 0x0F]);
    }
    STRCAT(hashStr, HEX_VALUE_STRS[signedHashedBuf[i] & 0x0F]);
    leadingZeroes = PR_FALSE;
  }

  *hashedBufStr = hashStr;

  return IMQ_SUCCESS;
}


/*
 *
 */
void
JMQDigestAuthenticationHandler::convertMD5HashToSigned(const PRUint8 hashedBuf[MD5_HASH_LEN], 
                                                       PRUint8 signedHashedBuf[MD5_HASH_LEN], 
                                                       PRBool * const isNegative)
{
  // If the leading bit is zero, then it's signed and we're done
  if ((hashedBuf[0] & 0x80) == 0) {
    for (int i = 0; i < MD5_HASH_LEN; i++) {
      signedHashedBuf[i] = hashedBuf[i];
    }
    *isNegative = PR_FALSE;
    return;
  }

  // Otherwise we have to do it ourselves
  *isNegative = PR_TRUE;

  // flip the bits (1 => 0, 0 => 1)
  int i;
  for (i = 0; i < MD5_HASH_LEN; i++) {
    signedHashedBuf[i] = (PRUint8)(hashedBuf[i] ^ (PRUint8)0xFF);  
  }

  // Add 1
  int carry = 1;
  for (i = MD5_HASH_LEN - 1; (i >= 0) && (carry == 1); i--) {
    int sum = (int)signedHashedBuf[i] + carry;
    if (sum > 255) {
      ASSERT(sum == 256);
      carry = 1;
      sum = 0;
    } else {
      carry = 0;
    }
    ASSERT((sum >= 0) && (sum <= 255));
    signedHashedBuf[i] = (PRUint8)sum;
  }
}


/*
 *
 */
const PRInt32 CONVERT_TEST_TEST_CASES = 7;
const PRUint8 TEST_HASH_BUF_UNSIGNED[CONVERT_TEST_TEST_CASES][MD5_HASH_LEN] = 
{ 
  // non-negative
  {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
  {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}, 
  {0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF},
  {0x73, 0xc3, 0xb5, 0xcb, 0x55, 0xd3, 0xc6, 0xd0, 0xc6, 0x12, 0x2e, 0xed, 0xcc, 0xc3, 0xdc, 0xf3},

  // negative
  {0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
  {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF},
  {0xd3, 0xc3, 0xb5, 0xcb, 0x55, 0xd3, 0xc6, 0xd0, 0xc6, 0x12, 0x2e, 0xed, 0xcc, 0xc3, 0xdc, 0xf3}
};
const PRUint8 TEST_HASH_BUF_SIGNED[CONVERT_TEST_TEST_CASES][MD5_HASH_LEN] = 
{ 
  // non-negative
  {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
  {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}, 
  {0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF},
  {0x73, 0xc3, 0xb5, 0xcb, 0x55, 0xd3, 0xc6, 0xd0, 0xc6, 0x12, 0x2e, 0xed, 0xcc, 0xc3, 0xdc, 0xf3},

  // negative
  {0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
  {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01},
  {0x2c, 0x3c, 0x4a, 0x34, 0xaa, 0x2c, 0x39, 0x2f, 0x39, 0xed, 0xd1, 0x12, 0x33, 0x3c, 0x23, 0x0d}
};
const PRBool TEST_IS_NEGATIVE[CONVERT_TEST_TEST_CASES] = 
{
  PR_FALSE,
  PR_FALSE,
  PR_FALSE,
  PR_FALSE,

  PR_TRUE,
  PR_TRUE,
  PR_TRUE,
};


/*
 *
 */
iMQError
JMQDigestAuthenticationHandler::testConvertMD5HashToSigned() 
{
  PRUint8 signedHashBuf[MD5_HASH_LEN];
  PRBool isNegative;
  for (int i = 0; i < CONVERT_TEST_TEST_CASES; i++) {
    convertMD5HashToSigned(TEST_HASH_BUF_UNSIGNED[i], signedHashBuf, &isNegative);
    ASSERT( isNegative == TEST_IS_NEGATIVE[i] );
    ASSERT( memcmp(signedHashBuf, TEST_HASH_BUF_SIGNED[i], MD5_HASH_LEN) == 0 );

    if ((isNegative != TEST_IS_NEGATIVE[i]) ||
        (memcmp(signedHashBuf, TEST_HASH_BUF_SIGNED[i], MD5_HASH_LEN) != 0))
    {
      return IMQ_INTERNAL_ERROR;
    }
  }
 
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
JMQDigestAuthenticationHandler::test()
{
  RETURN_IF_ERROR( JMQDigestAuthenticationHandler::testConvertMD5HashToSigned() );

  JMQDigestAuthenticationHandler authHandler;
  UTF8String username("guest");
  UTF8String password("guest");
  
  authHandler.init(&username, &password, NULL);

  PRUint8 *  authReply    = NULL;
  PRInt32 authReplyLen = 0;

  // hashedUserpwdStr "d3c3b5cb55d3c6d0c6122eedccc3dcf3"
  // from iMQ         "-2c3c4a34aa2c392f39edd112333c230d"
  static const char * nonce = "-34b997a1a2d58a1635f2b0596f8a217";
  static const char expectedResponse[] = 
    { 0,   5, 103, 117, 101, 115, 116,  0, 33,  45, 
     52,  98, 100,  50, 101,  55,  97, 54, 98,  97, 
     51, 100, 101,  56,  50,  56, 101, 56, 98, 100, 
     50,  55,  52,  48,  98,  54,  52, 49, 57,  97, 
     57,  56};

  if (authHandler.handleRequest((PRUint8*)nonce, (PRInt32)STRLEN(nonce), 
                                &authReply, &authReplyLen, 0) == IMQ_SUCCESS) 
  {
    if (memcmp(authReply, expectedResponse, authReplyLen) != 0) {
      DELETE( authReply );
      return IMQ_INTERNAL_ERROR;
    }
    DELETE( authReply );
  } else {
    return IMQ_INTERNAL_ERROR;
  }

  return IMQ_SUCCESS;
}
