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
 * @(#)RollbackReason.java	1.2 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import com.sun.messaging.jmq.util.lists.Reason;

/**
 * Reasons a packet may be added to list.
 */

public class RollbackReason extends Reason
{
    public static final RollbackReason APPLICATION = new RollbackReason(1,"APPLICATION");
    public static final RollbackReason ADMIN = new RollbackReason(2,"ADMIN");
    public static final RollbackReason CONNECTION_CLEANUP = new RollbackReason(3,"CONNECTION_CLEANUP");
    public static final RollbackReason TAKEOVER_CLEANUP = new RollbackReason(4,"TAKEOVER_CLEANUP");
    public static final RollbackReason TIMEOUT = new RollbackReason(5,"TIMEOUT");

    private RollbackReason(int id, String str) {
        super(id, str);
    }
}
