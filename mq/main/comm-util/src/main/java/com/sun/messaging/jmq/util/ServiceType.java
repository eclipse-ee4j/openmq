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
 * @(#)ServiceType.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util;

public class ServiceType
{
    public static final int ALL = -1;
    public static final int UNKNOWN = -1;
    public static final int NORMAL = 0;
    public static final int ADMIN = 1;

    public static String getString(int type)
    {
        switch (type) {

            case NORMAL:
                return "Normal";

            case ADMIN:
                return "Administrator";

        }
        return "Unknown Service";

    }

    /**
     * @return the service type string as in config 
     */
    public static String getServiceTypeString(int type)
    {
        switch (type) {

            case NORMAL:
                return "NORMAL";

            case ADMIN:
                return "ADMIN";

        }
        return "UNKNOWN";
    }


    public static int getServiceType(String string)
    {
        if (string == null) {
            // unknown type
            
            // XXX - 9/11/00 - racer - log internal error

            return NORMAL;
        }

        if (string.equalsIgnoreCase("NORMAL"))
            return NORMAL;

        if (string.equalsIgnoreCase("ADMIN"))
            return ADMIN;

        // unknown type
            
        // XXX - 9/11/00 - racer - log internal error

        return NORMAL;

    }
}
