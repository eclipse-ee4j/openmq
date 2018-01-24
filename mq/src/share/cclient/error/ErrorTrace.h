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
 * @(#)ErrorTrace.h	1.7 06/26/07
 */ 

#ifndef ERRORTRACE_H
#define ERRORTRACE_H

#include <nspr.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

struct ErrorTrace {
  PRBool usable;
  PRUint32 num_elements;
  PRUint32 num_allocated;
  char **trace;
};


/**
 * use PRStatus instead of MQError so that we don't need to
 * include mqtypes.h or MQ headers hence self contained */

PRStatus getErrorTrace(ErrorTrace ** trace);

PRStatus setErrorTraceElement(const char * method,
                              const char * file, PRInt32 lineNumber,
                              const char * errorType, PRUint32 errorCode);

PRStatus setVErrorTraceElement(const char * method,
                               const char * file, PRInt32 lineNumber,
                               const char * errorType, PRUint32 errorCode,
                               const char * const format, ...);

/**
 * clear ErrorTrace,  if all is true, clear thread private date as well */ 
PRStatus clearErrorTrace(PRBool all);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif  /* ERRORTRACE_H */

