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
 * @(#)iMQSSLShim.cpp	1.11 06/26/07
 */ 

#include "mqssl.h"
#include "../io/SSLSocket.hpp"
#include "shimUtils.hpp"

EXPORTED_SYMBOL MQStatus
MQInitializeSSL(ConstMQString certificateDatabasePath)
{
  static const char FUNCNAME[] = "MQInitializeSSL";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  CNDCHK( certificateDatabasePath == NULL, MQ_NULL_PTR_ARG );

  MQ_ERRCHK_TRACE( SSLSocket::initializeSSL(certificateDatabasePath), FUNCNAME );

  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE(FUNCNAME, errorCode);
  RETURN_STATUS( errorCode );
}
