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
 * @(#)DestLimitBehavior.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util;


/**
 * Limiting behavior used by the destination when it 
 * becomes full.
 * <P>
 * <B>XXX</B> How should serialization be handled.
 */

public class DestLimitBehavior 
{
    public static final int UNKNOWN = -1;
    public static final int FLOW_CONTROL = 0;
    public static final int REMOVE_OLDEST = 1;
    public static final int REJECT_NEWEST = 2;
    public static final int REMOVE_LOW_PRIORITY = 3;

    public static int getStateFromString(String str) {
        if (str.equals("FLOW_CONTROL")) 
            return FLOW_CONTROL;
        if (str.equals("REMOVE_OLDEST")) 
            return REMOVE_OLDEST;
        if (str.equals("REJECT_NEWEST") )
            return REJECT_NEWEST;
        if (str.equals("REMOVE_LOW_PRIORITY")) 
            return REMOVE_LOW_PRIORITY;
        return UNKNOWN;
    }


    public static String getString(int state)
    {
        switch (state) {
            case FLOW_CONTROL:
                return "FLOW_CONTROL";

            case REMOVE_OLDEST:
                return "REMOVE_OLDEST";

            case REJECT_NEWEST:
                return "REJECT_NEWEST";

            case REMOVE_LOW_PRIORITY:
                return "REMOVE_LOW_PRIORITY";

        }
        return "UNKNOWN";

    }
}
