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
 * %W% %G%
 */ 

#include "../util/LogUtils.hpp"
#include <assert.h>
#include "NSSInitCall.h"
#include "../cshim/mqerrors.h"

#define ASSERT assert

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

static PRUintn        _callOnceNSSData_key;

static PRCallOnceType _once_nss_init;
static PRCallOnceType _once_thr_priv;

static SECStatus      _callOnceSECStatus = SECFailure;
static PRErrorCode    _callOncePRError;
static PRErrorCode    _callOncePROSError;

static PRBool         _calledNSS_Init = PR_FALSE;
static PRBool         _calledNSS_NoDB_Init = PR_FALSE;

static void PR_CALLBACK deleteCallOnceNSSData(void * data) 
{
  if (data != NULL) free(data);
}


static PRStatus PR_CALLBACK once_fn_thr_priv(void) 
{

  LOG_INFO(( CODELOC, CONNECTION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
		    "Preparing for NSS initialization ..." ));

  return PR_NewThreadPrivateIndex(&_callOnceNSSData_key, &deleteCallOnceNSSData);
}


static PRStatus PR_CALLBACK once_fn_nss_init(void) 
{

  LOG_INFO(( CODELOC, CONNECTION_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
		    "Initializing NSS ..." ));

  ASSERT( _calledNSS_Init == PR_FALSE && _calledNSS_NoDB_Init == PR_FALSE );

  CallOnceNSSData * data = (CallOnceNSSData *)PR_GetThreadPrivate(_callOnceNSSData_key);
  if (data == NULL) return PR_FAILURE;

  if (data->noDB == PR_TRUE) {
    _callOnceSECStatus = NSS_NoDB_Init(NULL);
    _calledNSS_NoDB_Init = PR_TRUE;
  } else {
    _callOnceSECStatus = NSS_Init(data->certDir);
    _calledNSS_Init = PR_TRUE;
  }

  _callOncePRError   = PR_GetError();
  _callOncePROSError = PR_GetOSError();

  return PR_SUCCESS;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */


SECStatus callOnceNSS_Init(const char * certDir)
{
  if (_once_nss_init.initialized == 0) {

  if (_once_thr_priv.initialized == 0) {
    if(PR_CallOnce(&_once_thr_priv, once_fn_thr_priv) != PR_SUCCESS) return SECFailure;
  }

  if (_once_nss_init.initialized == 0) {

    CallOnceNSSData * data = (CallOnceNSSData *)PR_GetThreadPrivate(_callOnceNSSData_key);
    if (data == NULL) {
      data = (CallOnceNSSData *) malloc(sizeof(CallOnceNSSData));
      if (data == NULL) {
        PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
        return SECFailure;
      }
      if (PR_SetThreadPrivate(_callOnceNSSData_key, data) != PR_SUCCESS) {
        free(data);
        return SECFailure;
      }
    }

    data->certDir = certDir;
    if (certDir == NULL) {
      data->noDB = PR_TRUE;
    } else {
      data->noDB = PR_FALSE;
    }

    if(PR_CallOnce(&_once_nss_init, once_fn_nss_init) != PR_SUCCESS) return SECFailure;
  }

  }

  PR_SetError(_callOncePRError, _callOncePROSError);
  return _callOnceSECStatus;

}

PRBool calledNSS_Init() {
  ASSERT( (_calledNSS_Init == PR_TRUE && _calledNSS_NoDB_Init == PR_FALSE) || 
          (_calledNSS_Init == PR_FALSE && _calledNSS_NoDB_Init == PR_TRUE) || 
          (_calledNSS_Init == PR_FALSE && _calledNSS_NoDB_Init == PR_FALSE) );

  return _calledNSS_Init;
}
