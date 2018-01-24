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
 * @(#)AckMode.h	1.4 06/26/07
 */ 

#ifndef ACKMODE_H
#define ACKMODE_H

/* Do not change these values as iMQTypes.h depends on them*/
typedef enum _AckMode {AUTO_ACKNOWLEDGE    = 1, 
                       CLIENT_ACKNOWLEDGE  = 2,
                       DUPS_OK_ACKNOWLEDGE = 3,
                       SESSION_TRANSACTED  = 0} AckMode;

#endif /* ACKMODE_H */
