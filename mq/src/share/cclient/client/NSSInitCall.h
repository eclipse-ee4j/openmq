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
 * @(#)NSSInitCall.h	1.4 06/26/07
 */ 

#ifndef NSSINITCALL_H
#define NSSINITCALL_H

#include <nspr.h>
#include <nss.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

struct CallOnceNSSData {
  const char * certDir;
  PRBool noDB;
};


/**
 * This function should only be called when MQ_SSL_BROKER_IS_TRUSTED true
 *
 * @param certDir if NULL, call NSS_No_DB, otherwise call NSS_Init */

SECStatus callOnceNSS_Init(const char * certDir);

 /**
  * To be called only when callOnceNSS_Init return SECSuccess
  *
  * @return PR_TRUE if NSS_Init (not NSS_NoDB_Init) is called */
PRBool calledNSS_Init();


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif  /* NSSINITCALL_H */

