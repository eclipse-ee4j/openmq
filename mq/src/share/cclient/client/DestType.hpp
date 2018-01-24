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
 * @(#)DestType.hpp	1.3 06/26/07
 */ 

#ifndef DESTTYPE_HPP
#define DESTTYPE_HPP

#include <nspr.h>

static const PRInt32 DEST_TYPE_QUEUE         = 0x00000001;
static const PRInt32 DEST_TYPE_TOPIC         = 0x00000002;
static const PRInt32 DEST_TEMP               = 0x00000010;
static const PRInt32 DEST_FLAVOR_SINGLE      = 0x00000100;
static const PRInt32 DEST_FLAVOR_RROBIN      = 0x00000200;
static const PRInt32 DEST_FLAVOR_FAILOVER    = 0x00000400;

#endif // DESTTYPE_HPP
