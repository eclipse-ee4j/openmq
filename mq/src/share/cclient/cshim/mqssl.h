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
 * @(#)mqssl.h	1.13 06/26/07
 */ 

#ifndef MQ_SSL_H
#define MQ_SSL_H

/*
 * declaration for initializing SSL library
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"  

/**
 * This function initializes the SSL library.  It must be called before
 * connecting to the broker over SSL.  
 *
 * @param certificateDatabasePath the path to the NSS certificate database
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQInitializeSSL(ConstMQString certificateDatabasePath);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_SSL_H */
