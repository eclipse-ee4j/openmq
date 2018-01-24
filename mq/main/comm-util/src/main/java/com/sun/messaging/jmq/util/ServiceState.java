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
 * @(#)ServiceState.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util;

public class ServiceState
{
    public static final int UNKNOWN = -1;
    public static final int UNINITIALIZED = 0;
    public static final int INITIALIZED = 1;
    public static final int STARTED = 2;
    public static final int RUNNING = 3;
    public static final int PAUSED = 4;
    public static final int SHUTTINGDOWN = 5;
    public static final int STOPPED = 6;
    public static final int DESTROYED = 7;
    public static final int QUIESCED = 8;

    public static int getStateFromString(String str) {
        if (str.equals("UNINITIALIZED"))
            return UNINITIALIZED;
        if (str.equals("INITIALIZED")) 
            return INITIALIZED;
        if (str.equals("STARTED")) 
            return STARTED;
        if (str.equals("RUNNING") )
            return RUNNING;
        if (str.equals("PAUSED")) 
            return PAUSED;
        if (str.equals("SHUTTINGDOWN") )
            return SHUTTINGDOWN;
        if (str.equals("STOPPED") )
            return STOPPED;
        if (str.equals("DESTROYED") )
            return DESTROYED;
        if (str.equals("QUIESCED") )
            return QUIESCED;
        return UNKNOWN;
    }


    public static String getString(int state)
    {
        switch (state) {
            case UNINITIALIZED:
                return "UNINITIALIZED";

            case INITIALIZED:
                return "INITIALIZED";

            case STARTED:
                return "STARTED";

            case RUNNING:
                return "RUNNING";

            case PAUSED:
                return "PAUSED";

            case SHUTTINGDOWN:
                return "SHUTTINGDOWN";

            case STOPPED:
                return "STOPPED";

            case DESTROYED:
                return "DESTROYED";

            case QUIESCED:
                return "QUIESCED";

        }
        return "UNKNOWN";

    }
}
