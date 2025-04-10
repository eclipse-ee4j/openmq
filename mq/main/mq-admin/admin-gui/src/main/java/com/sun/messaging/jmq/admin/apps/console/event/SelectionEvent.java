/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.admin.apps.console.event;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.apps.console.ConsoleObj;

import java.io.Serial;

/**
 * Event for indicating something was either selected or deselected. Can also be used to clear selection.
 */
public class SelectionEvent extends AdminEvent {
    @Serial
    private static final long serialVersionUID = 2290552086056307679L;
    /*
     * Type of select event.
     */
    public static final int OBJ_SELECTED = 0;
    public static final int OBJ_DESELECTED = 1;
    public static final int CLEAR_SELECTION = 2;

    private ConsoleObj selObj = null;

    /**
     * Creates an instance of SelectionEvent
     *
     * @param source the object where the event originated
     * @param type the event type
     */
    public SelectionEvent(Object source, int type) {
        super(source, type);
    }

    /**
     * Sets the selected object.
     *
     * @param selectedObject The selected object.
     */
    public void setSelectedObj(ConsoleObj selectedObject) {
        selObj = selectedObject;
    }

    /**
     * Returns the selected object.
     *
     * @return The selected object.
     */
    public ConsoleObj getSelectedObj() {
        return (selObj);
    }
}
