/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.util.Arrays;

import javax.transaction.xa.Xid;

public class DebugConverters {
    private DebugConverters() {
    }

    public static String toString(Xid foreignXid) {
         return String.format("(GlobalTransactionID=%s, BranchQualifier=%s) ",
                              Arrays.toString(foreignXid.getGlobalTransactionId()),
                              Arrays.toString(foreignXid.getBranchQualifier()));
    }
}
