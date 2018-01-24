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
 * @(#)ConsoleActionEvent.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.event;

import java.util.EventObject;
import com.sun.messaging.jmq.admin.event.AdminEvent;

/**
 * Events related to high level semantic console operations like exit, about,
 * add, preferences, etc.
 */
public class ConsoleActionEvent extends AdminEvent {
    /*
     * Event type
     */
    public final static int	ABOUT 		= 0;
    public final static int	PREFERENCES	= 1;
    public final static int	EXPAND_ALL	= 2;
    public final static int	COLLAPSE_ALL	= 3;
    public final static int	EXIT	 	= 4;
    public final static int	REFRESH	 	= 5;

    /**
     * Creates an instance of ConsoleActionEvent
     * @param source the object where the event originated
     */
    public ConsoleActionEvent(Object source) {
	super(source);
    }

    /**
     * Creates an instance of ConsoleActionEvent of the specified type.
     * @param source the object where the event originated
     * @param type the type of event
     */
    public ConsoleActionEvent(Object source, int type) {
	super(source, type);
    }
}
