/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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
 * @(#)DialogEvent.java	1.12 06/28/07
 */

package com.sun.messaging.jmq.admin.apps.console.event;

import com.sun.messaging.jmq.admin.event.AdminEvent;

/**
 * Events related to showing any kind of dialog in the admin console application.
 * <P>
 * The event type field is not used in this class. Instead, a dialog type field is used instead. This is because the
 * type of dialog events that are needed for now is an event that says: Please show this dialog.
 *
 * <P>
 * If the need arises for an event that indicates that a dialog needs to be hidden, maybe distinct dialog event types
 * need to be introduced or a flag that indicates whether dialog needs to be shown/hidden.
 */
public class DialogEvent extends AdminEvent {
    /**
     * 
     */
    private static final long serialVersionUID = -9209790170099361107L;
    /*
     * Dialog type
     */
    public static final int ADD_DIALOG = 0;
    public static final int DELETE_DIALOG = 1;
    public static final int PURGE_DIALOG = 2;
    public static final int PROPS_DIALOG = 3;
    public static final int SHUTDOWN_DIALOG = 4;
    public static final int RESTART_DIALOG = 5;
    public static final int PAUSE_DIALOG = 6;
    public static final int RESUME_DIALOG = 7;
    public static final int CONNECT_DIALOG = 8;
    public static final int DISCONNECT_DIALOG = 9;
    public static final int HELP_DIALOG = 10;

    private int dialogType;

    /**
     * Creates an instance of DialogEvent
     *
     * @param source the object where the event originated
     */
    public DialogEvent(Object source) {
        super(source);
    }

    /*
     * Sets the dialog type. This is the dialog that needs to be shown.
     */
    public void setDialogType(int dialogType) {
        this.dialogType = dialogType;
    }

    /*
     * Returns the dialog type. This is the dialog that needs to be shown.
     */
    public int getDialogType() {
        return (dialogType);
    }
}
