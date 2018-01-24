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
 * @(#)Reason.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

/**
 * Generic "reason" class which contains an id and a string. Different
 * events will have different reason classes.
 */

public class Reason
{
    int reason;
    String reasonStr;

    protected Reason(int reason, String reasonStr) {
        this.reason = reason;
        this.reasonStr = reasonStr;
    }

    public int intValue() {
        return reason;
    }

    public String toString() {
        return reasonStr;
    }

    public boolean equals(Object o) {
        if (o instanceof Reason) {
             return reason == ((Reason)o).reason;
        }
        return false;
    }

    public int hashCode() {
        return reason;
    }

}
