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
 * @(#)iMQStatusShim.cpp	1.13 06/26/07
 */ 

#include "mqstatus.h"
#include "../error/ErrorTrace.h"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"

const MQStatus MQ_STATUS_SUCCESS = {MQ_SUCCESS};

/*
 *
 */
EXPORTED_SYMBOL MQBool
MQStatusIsError(const MQStatus status)
{
  return status.errorCode != MQ_SUCCESS;
}

/*
 *
 */
EXPORTED_SYMBOL MQError
MQGetStatusCode(const MQStatus status)
{
  return status.errorCode;
}

/*
 *
 */
EXPORTED_SYMBOL MQString
MQGetStatusString(const MQStatus status)
{
  const ConstMQString errorString = errorStr(status.errorCode);
  MQString returnString = new MQChar[STRLEN(errorString)+1];
  if (returnString == NULL) {
    return NULL;
  }
  STRCPY(returnString, errorString);
      
  return returnString;
}


EXPORTED_SYMBOL void 
MQFreeString(MQString statusString)
{
  if (statusString != NULL) {
    delete[] (MQString)statusString;
  }
}


EXPORTED_SYMBOL MQString 
MQGetErrorTrace()
{
  MQString returnString = NULL;

#ifndef MQ_NO_ERROR_TRACE
  ErrorTrace *trace = NULL;
  size_t size = 0, i = 0;

  if (getErrorTrace(&trace) != PR_SUCCESS || trace == NULL) {
    return NULL; //XXX return some error in string ?
  }

  for (i = 0; i < trace->num_elements; i++) { 
     if (trace->trace[i] ==  NULL) break;
     size += STRLEN(trace->trace[i]);
     size += 2;
  }

  returnString = new MQChar[size+1];
  if (returnString == NULL) {
    return NULL; //XXX
  }
  returnString[0] = '\0';
  for (i = 0; i < trace->num_elements; i++) { 
    if (trace->trace[i] ==  NULL) break;
    STRCAT(returnString, trace->trace[i]);
    STRCAT(returnString, "\n");
  }
#endif
  return returnString; 
}
