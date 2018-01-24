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
 * @(#)xaswitch.hpp	1.2 10/23/07
 */
#ifndef MQ_XA_SWITCH_HPP
#define MQ_XA_SWITCH_HPP

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "xa.h"
#include "mqtypes.h"

struct _mq_xid_t {
    XID * xid;
    PRInt64 transactionID; 
  };
typedef struct _mq_xid_t MQXID;

/*
 * utility functions used by XA connection and XA session 
 */
PRUintn mq_getXidIndex();
MQConnectionHandle mq_getXAConnection();

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_XA_SWITCH_HPP */
