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
 * @(#)AdminEvent.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.admin.event;

import java.util.EventObject;

/**
 * Root class for all semantic events
 * within the admin console application.
 */
public class AdminEvent extends EventObject {
    private int type = -1;

    /**
     * Creates an instance of AdminEvent
     * @param source the object where the event originated
     */
    public AdminEvent(Object source) {
	super(source);
    }

    /**
     * Creates an instance of AdminEvent
     * @param source the object where the event originated
     * @param type the event type
     */
    public AdminEvent(Object source, int type) {
	super(source);
	this.type = type;
    }

    /**
     * Returns the event type
     * @return the event type
     */
    public int getType()  {
	return type;
    }
}
