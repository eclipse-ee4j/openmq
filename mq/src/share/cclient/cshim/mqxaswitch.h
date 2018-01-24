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
 * @(#)mqxaswitch.h	1.1 10/15/07
 */

#ifndef MQ_XA_SWITCH_H
#define MQ_XA_SWITCH_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "xa.h"
#include "mqtypes.h"

EXPORTED_SYMBOL int
mq_xa_open(char *xa_info, int rmid, long flags);

EXPORTED_SYMBOL int 
mq_xa_close(char *xa_info, int rmid, long flags);

EXPORTED_SYMBOL int 
mq_xa_start(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int
mq_xa_end(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int 
mq_xa_prepare(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int
mq_xa_rollback(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int
mq_xa_commit(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int 
mq_xa_recover(XID *xid, long count, int rmid, long flags);

EXPORTED_SYMBOL int
mq_xa_forget(XID *xid, int rmid, long flags);

EXPORTED_SYMBOL int
mq_xa_complete(int *handle, int *retval, int rmid, long flags);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_XA_SWITCH_H */
