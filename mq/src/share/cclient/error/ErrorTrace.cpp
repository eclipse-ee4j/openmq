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
 * @(#)ErrorTrace.cpp	1.11 06/26/07
 */ 

#include <string.h>
#include <stdlib.h>
#include <assert.h>
#include "ErrorTrace.h"

#define ASSERT assert

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

static PRUintn        _errorTraceKey;
static PRCallOnceType once;

static void PR_CALLBACK deleteErrorTrace(void * data)
{
  PRUint32 i;
  ErrorTrace * trace = (ErrorTrace *)data;
  if (trace == NULL) return;
  if (trace->trace != NULL) {
    for (i = 0; i < trace->num_elements; i++) { 
      ASSERT( trace->trace[i] != NULL );
      free(trace->trace[i]);
    }
    trace->num_elements = 0;
    free(trace->trace);
  } 
  trace->num_allocated = 0;
  free(trace);
}

static PRStatus once_func(void)
{
  return PR_NewThreadPrivateIndex(&_errorTraceKey, &deleteErrorTrace);
}

static PRStatus
getErrorTraceAlloc(ErrorTrace **trace, PRBool alloc)
{
  if (once.initialized == 0) {
    if(PR_CallOnce(&once, once_func) != PR_SUCCESS) return PR_FAILURE;
  }

  *trace = (ErrorTrace *)PR_GetThreadPrivate(_errorTraceKey);
  if (*trace != (ErrorTrace *)NULL)  {
    if ((*trace)->usable == PR_FALSE) {
      *trace = (ErrorTrace *)NULL;
      return PR_FAILURE;
    }
    return PR_SUCCESS;
  }

  if (alloc == PR_FALSE) return PR_SUCCESS;

  *trace = (ErrorTrace *)calloc(1, sizeof(ErrorTrace));
  if (*trace == (ErrorTrace *)NULL) {
    PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
    return PR_FAILURE; 
  }

  (*trace)->trace = (char **)calloc(16, sizeof(char *));
  if ((*trace)->trace == NULL) {
    free(*trace);
    *trace = NULL;
    PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
    return PR_FAILURE; 
  }
  (*trace)->usable = PR_TRUE;
  (*trace)->num_allocated = 16;
  (*trace)->num_elements = 0;

  if (PR_SetThreadPrivate(_errorTraceKey, *trace) != PR_SUCCESS) {
    PR_SetThreadPrivate(_errorTraceKey, NULL);
    free((*trace)->trace);
    free(*trace);
    *trace = NULL;
    PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
    return PR_FAILURE;
  }
  return PR_SUCCESS;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */


PRStatus
getErrorTrace(ErrorTrace **trace)
{
  return getErrorTraceAlloc(trace, PR_FALSE);
}

PRStatus
setErrorTraceElement(const char * method ,
                     const char * file, PRInt32 lineNumber,
                     const char * errorType, PRUint32 errorCode)
{
  return setVErrorTraceElement(method, file, lineNumber, errorType, errorCode, "");
}

PRStatus
setVErrorTraceElement(const char * method ,
                      const char * file, PRInt32 lineNumber,
                      const char * errorType, PRUint32 errorCode,
                      const char * const format, ...)
{
  va_list argptr;

  ErrorTrace * trace = NULL;
  char * element = NULL;
               /* :::::\0     line  error */  
  size_t size = 7 +         12   + 12;
  char ** newtrace = NULL;

  char errorStr[10000];
  size_t errorStrLen = 0; 
  va_start(argptr, format);
  PR_vsnprintf(errorStr, sizeof(errorStr), format, argptr);
  va_end(argptr);
  errorStr[sizeof(errorStr)-1] = '\0';
  errorStrLen = strlen(errorStr);

  PRStatus status = getErrorTraceAlloc(&trace, PR_TRUE);
  if (status != PR_SUCCESS) return status;

  if (trace == (ErrorTrace *)NULL) {
    PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
    return PR_FAILURE;
  }

  if (method != NULL)     size += strlen(method);
  if (file != NULL)       size += strlen(file);
  if (errorType != NULL)  size += strlen(errorType);
  size += errorStrLen;

  element = (char *)malloc(size);
  if (element == NULL) {
    PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
    return PR_FAILURE;
  }

  if (trace->num_elements == trace->num_allocated -1) {  
    newtrace = (char **)realloc(trace->trace, (trace->num_allocated+16) * sizeof(char *));  
    if (newtrace == NULL) {
      free(element);
      PR_SetError(PR_OUT_OF_MEMORY_ERROR, 0);
      return PR_FAILURE;
    }
    trace->trace = newtrace;
    trace->num_allocated += 16;
  }
  if (errorStrLen == 0) {
  sprintf(element, "%s:%s:%d:%s:%d", method, file, lineNumber, errorType, errorCode); 
  } else {
  sprintf(element, "%s:%s:%d:%s:%d:%s", method, file, lineNumber, errorType, errorCode, errorStr); 
  }
  ASSERT( trace->num_elements < (trace->num_allocated-1) );
  trace->trace[trace->num_elements] = element;
  trace->trace[++(trace->num_elements)] = NULL;

  return PR_SUCCESS;
}


PRStatus
clearErrorTrace(PRBool all)
{
  ErrorTrace * trace = NULL;
  PRUint32 i;

  PRStatus status = getErrorTraceAlloc(&trace, PR_FALSE);
  if (status != PR_SUCCESS) return status;

  if (trace == (ErrorTrace *)NULL) return PR_SUCCESS;
  if (trace->usable == PR_FALSE) return PR_FAILURE;

  if (all == PR_TRUE) {
    if (PR_SetThreadPrivate(_errorTraceKey, NULL) != PR_SUCCESS) {
      trace->usable = PR_FALSE;
      return PR_FAILURE;
    } 
    return PR_SUCCESS;
  } 

  ASSERT( trace->trace != NULL );
  for (i = 0; i < trace->num_elements; i++) {
    ASSERT( trace->trace[i] != NULL );
    free(trace->trace[i]);
  }
  trace->num_elements = 0;
  return PR_SUCCESS;

}



