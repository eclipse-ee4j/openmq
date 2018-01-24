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
 * @(#)GoodbyeReason.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util;


public class GoodbyeReason
{
    public static final int SHUTDOWN_BKR = 1;
    public static final int RESTART_BKR = 2;
    public static final int ADMIN_KILLED_CON = 3;
    public static final int CON_FATAL_ERROR = 4;
    public static final int CLIENT_CLOSED = 5;
    public static final int OTHER = 6;
    public static final int BKR_IN_TAKEOVER = 7;
    public static final int REMOTE_BKR_RESTART = 8;
    public static final int MSG_HOME_CHANGE = 9;
    public static final int MIGRATE_PARTITION = 10;

    public static final String toString(int reason) {
        switch(reason) {
            case SHUTDOWN_BKR: return "SHUTDOWN_BKR";
            case RESTART_BKR: return "RESTART_BKR";
            case ADMIN_KILLED_CON: return "ADMIN_KILLED_CON";
            case CON_FATAL_ERROR: return "CON_FATAL_ERROR";
            case CLIENT_CLOSED: return "CLIENT_CLOSED";
            case OTHER: return "OTHER";
            case BKR_IN_TAKEOVER: return "BKR_IN_TAKEOVER";
            case REMOTE_BKR_RESTART: return "REMOTE_BKR_RESTART";
            case MSG_HOME_CHANGE: return "MSG_HOME_CHANGE";
            case MIGRATE_PARTITION: return "MIGRATE_PARTITION";
            default: return "UNKNOWN";
        }
    }
}
