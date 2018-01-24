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
 * @(#)PropertyUpdateException.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.config;

import com.sun.messaging.jmq.jmsserver.util.*;


/**
 * Exception thrown when a property could not be updated
 * because a ConfigListener rejected the change.
 */

public class PropertyUpdateException extends Exception
{
    public static final int Unknown = 0;
    public static final int OutOfBounds = 1;
    public static final int InvalidSetting = 2;

    private int reason = 0;


    public PropertyUpdateException(String msg) {
        this(0,msg, null);
    }

    public PropertyUpdateException(int reason, String msg) {
        this(0, msg, null);
    }

    public PropertyUpdateException(int reason, String msg, Throwable rootcause)
    {
        super(msg, rootcause);
        this.reason  = reason;
    }

    public int getReason() {
        return reason;
    }
}

