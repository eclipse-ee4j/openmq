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
 * @(#)mqcrt.h	1.8 06/26/07
 */ 

#ifndef MQ_CRT_H
#define MQ_CRT_H

/*
 * include all of the MQ C-API public headers 
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"
#include "mqversion.h"
#include "mqerrors.h"
#include "mqstatus.h"
#include "mqproperties.h"
#include "mqconnection.h"
#include "mqsession.h"
#include "mqdestination.h"
#include "mqconsumer.h"
#include "mqproducer.h"
#include "mqmessage.h"
#include "mqtext-message.h"
#include "mqbytes-message.h"
#include "mqssl.h"

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_CRT_H */
