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
 * @(#)EventType.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

/**
 * Class which represents a eventType on a list which may
 * generate a notification.
 * @see Event
 */

public class EventType
{
    private int event = 0;
    private String name = null;

    /**
     * size (count) of object has changed
     */ 
    public static final EventType  SIZE_CHANGED 
                   = new EventType(0, "SIZE_CHANGED");

    /**
     * bytes of object has changed
     * @see Sized
     */ 
    public static final EventType  BYTES_CHANGED 
                   = new EventType(1, "BYTES_CHANGED");


    /**
     * the set of objects has changes (an object has been added
     * or removed)
     */ 
    public static final EventType  SET_CHANGED 
                   = new EventType(2, "SET_CHANGED");

    /**
     * the object has moved to or from an empty state
     */ 
    public static final EventType  EMPTY 
                   = new EventType(3, "EMPTY");

    /**
     * the object has moved to or from a full state
     */ 
    public static final EventType  FULL 
                   = new EventType(4, "FULL");

    /**
     * the object has moved to or from a busy state
     */ 
    public static final EventType BUSY_STATE_CHANGED
                   = new EventType(5, "BUSY_STATE_CHANGED");

    /**
     * a change to the set (an item added or removed) has been
     * requested
     */
    public static final EventType SET_CHANGED_REQUEST
                   = new EventType(6, "SET_CHANGED_REQUEST");

    public static final int EVENT_TYPE_NUM = SET_CHANGED_REQUEST.getEvent()+1;

    protected EventType(int id, String name) {
        event = id;
        this.name = name;
    }

    /**
     * integer value associated with this event type
     * @returns integer value of eventType
     */
    public final int getEvent() {
        return event;
    }

    /**
     * EventType displayed as a string
     * @returns string representing object
     */
    public String toString() {
        return name;
    }

    /**
     * compares this event type against another object.
     * @returns true if the objects are the same
     */
    public boolean equals(Object o) {
        if (o instanceof EventType) {
            return event == ((EventType)o).event;
        }
        return false;
    }

    public int hashCode() {
         return event;
    }


}
