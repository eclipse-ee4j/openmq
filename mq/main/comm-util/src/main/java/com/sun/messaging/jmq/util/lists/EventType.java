/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.util.lists;

import lombok.Getter;

/**
 * Class which represents a eventType on a list which may generate a notification.
 *
 */

public enum EventType {
    /**
     * size (count) of object has changed
     */
    SIZE_CHANGED(0, "SIZE_CHANGED"),

    /**
     * bytes of object has changed
     *
     * @see Sized
     */
    BYTES_CHANGED(1, "BYTES_CHANGED"),

    /**
     * the set of objects has changes (an object has been added or removed)
     */
    SET_CHANGED(2, "SET_CHANGED"),

    /**
     * the object has moved to or from an empty state
     */
    EMPTY(3, "EMPTY"),

    /**
     * the object has moved to or from a full state
     */
    FULL(4, "FULL"),

    /**
     * the object has moved to or from a busy state
     */
    BUSY_STATE_CHANGED(5, "BUSY_STATE_CHANGED"),

    /**
     * a change to the set (an item added or removed) has been requested
     */
    SET_CHANGED_REQUEST(6, "SET_CHANGED_REQUEST");

    public static final int EVENT_TYPE_NUM = SET_CHANGED_REQUEST.getEvent() + 1;

    /**
     * integer value associated with this event type
     */
    @Getter
    private final int event;

    private final String name;

    EventType(int id, String name) {
        event = id;
        this.name = name;
    }

    /**
     * EventType displayed as a string
     *
     * @return string representing object
     */
    @Override
    public String toString() {
        return name;
    }
}
