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
 * @(#)RemoveReason.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util.lists;

import com.sun.messaging.jmq.util.lists.*;

/**
 * Reason a packet may be removed from a list.
 */

public class RemoveReason extends Reason
{
    public static final RemoveReason EXPIRED = new RemoveReason(1,"EXPIRED");
    public static final RemoveReason ACKNOWLEDGED = new RemoveReason(2,"ACKNOWLEDGED");
    public static final RemoveReason DELIVERED = new RemoveReason(3,"DELIVERED");
    public static final RemoveReason REMOVED_LOW_PRIORITY = new RemoveReason(4,"LOW_PRIORITY");
    public static final RemoveReason REMOVED_OLDEST = new RemoveReason(5,"OLDEST");
    public static final RemoveReason REMOVED_REJECTED = new RemoveReason(6,"REJECTED");
    public static final RemoveReason REMOVED_OTHER = new RemoveReason(7,"REMOVED");
    public static final RemoveReason PURGED = new RemoveReason(8,"PURGED");
    public static final RemoveReason UNLOADED = new RemoveReason(9,"UNLOADED");
    public static final RemoveReason ROLLBACK = new RemoveReason(10,"ROLLBACK");
    public static final RemoveReason OVERFLOW = new RemoveReason(11,"OVERFLOW");
    public static final RemoveReason ERROR = new RemoveReason(12,"ERROR");
    public static final RemoveReason UNDELIVERABLE = new RemoveReason(13,"UNDELIVERABLE");
    public static final RemoveReason EXPIRED_ON_DELIVERY = new RemoveReason(14,"EXPIRED_ON_DELIVERY");
    public static final RemoveReason EXPIRED_BY_CLIENT = new RemoveReason(15,"EXPIRED_BY_CLIENT");
    public static final RemoveReason REMOVE_ADMIN = new RemoveReason(16,"REMOVE_ADMIN");

    private static final RemoveReason[] reasons =
        { EXPIRED, 
          ACKNOWLEDGED,
          DELIVERED,
          REMOVED_LOW_PRIORITY,
          REMOVED_OLDEST,
          REMOVED_REJECTED,
          REMOVED_OTHER,
          PURGED,
          UNLOADED,
          ROLLBACK,
          OVERFLOW,
          ERROR,
          UNDELIVERABLE,
          EXPIRED_ON_DELIVERY,
          EXPIRED_BY_CLIENT,
          REMOVE_ADMIN };

    private RemoveReason(int id, String str) {
        super(id, str);
    }

    public static RemoveReason findReason(int id) {
        return reasons[id -1];
    }
}
