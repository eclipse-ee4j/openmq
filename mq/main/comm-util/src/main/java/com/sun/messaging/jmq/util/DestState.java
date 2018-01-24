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
 * @(#)DestState.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util;


/**
 * State of a destination.
 * <P>
 * <B>XXX</B> How should serialization be handled.
 */

public class DestState 
{
    public static final int UNKNOWN = -1;
    public static final int RUNNING = 0;
    public static final int CONSUMERS_PAUSED = 1;
    public static final int PRODUCERS_PAUSED = 2;
    public static final int PAUSED = 3;

    public static int getStateFromString(String str) {
        if (str.equals("RUNNING")) 
            return RUNNING;
        if (str.equals("CONSUMERS_PAUSED")) 
            return CONSUMERS_PAUSED;
        if (str.equals("PRODUCERS_PAUSED") )
            return PRODUCERS_PAUSED;
        if (str.equals("PAUSED")) 
            return PAUSED;
        return UNKNOWN;
    }


    public static String toString(int state)
    {
        switch (state) {
            case RUNNING:
                return "RUNNING";

            case CONSUMERS_PAUSED:
                return "CONSUMERS_PAUSED";

            case PRODUCERS_PAUSED:
                return "PRODUCERS_PAUSED";

            case PAUSED:
                return "PAUSED";

        }
        return "UNKNOWN";

    }
}
