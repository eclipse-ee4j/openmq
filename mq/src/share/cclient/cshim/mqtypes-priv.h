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
 * @(#)mqtypes-priv.h	1.8 06/26/07
 */ 

#ifndef MQ_TYPES_PRIV_H
#define MQ_TYPES_PRIV_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"

/* These methods manipulate an MQInt64 */

/** Return an MQInt64 with its high 32 bits set to hi and its low 32
    bits set to lo */
EXPORTED_SYMBOL MQInt64 
int64FromInt32Parts(MQInt32 hi, MQInt32 lo);

/** Return the high part of value64 in hi, and the low part in lo */
EXPORTED_SYMBOL void 
int32PartsFromInt64(MQInt32 * hi, MQInt32 * lo, MQInt64 value64);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_TYPES_PRIV_H */
