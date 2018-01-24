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
 * @(#)SelectionEvent.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.event;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.apps.console.ConsoleObj;

/**
 * Event for indicating something was either selected
 * or deselected. Can also be used to clear selection.
 */
public class SelectionEvent extends AdminEvent {
    /*
     * Type of select event.
     */
    public final static int	OBJ_SELECTED	= 0;
    public final static int	OBJ_DESELECTED	= 1;
    public final static int	CLEAR_SELECTION	= 2;

    private ConsoleObj	selObj = null;

    /**
     * Creates an instance of SelectionEvent
     * @param source the object where the event originated
     */
    public SelectionEvent(Object source) {
	super(source);
    }

    /**
     * Creates an instance of SelectionEvent
     * @param source the object where the event originated
     * @id the event type
     */
    public SelectionEvent(Object source, int type) {
	super(source, type);
    }

    /**
     * Sets the selected object.
     * @param selectedObject The selected object.
     */
    public void setSelectedObj(ConsoleObj selectedObject)  {
	selObj = selectedObject;
    }
    /**
     * Returns the selected object.
     * @return The selected object.
     */
    public ConsoleObj getSelectedObj()  {
	return (selObj);
    }
}
