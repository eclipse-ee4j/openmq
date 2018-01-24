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
 * @(#)Event.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

/**
 * Class which represents a event on a list which may
 * generate a notification.
 *
 * @deprecated since 3.0
 * @see EventBroadcaster
 */

public class Event extends java.util.EventObject
{

    /**
     * type of this event
     */
    protected EventType id;

    /**
     * original value before the event occurred
     */
    protected Object original_value;

    /**
     * new value after the event occurred
     */
    protected Object new_value;

    /**
     * reason the event occurred
     */
    protected Reason reason;
    
    /**
     * create a new event without a reason
     */
    public Event(EventType id, Object target, Object original,
           Object newval) 
    {
        this(id, target, original, newval, null);
    }
    
    /**
     * create a new event with a reason
     */
    public Event(EventType id, Object target, Object original,
         Object newval, Reason reason) {
        super(target);
        this.id = id;
        this.reason = reason;
        this.original_value = original;
        this.new_value = newval;
    }
    
    /**
     * @returns the event type for this event
     */
    public EventType getEventType() {
        return id;
    }
   
    
    /**
     * @returns the original object for this event
     *  (may be null)
     */
    public Object getOriginalValue() {
        return original_value;
    }
    
    /**
     * @returns the current object for this event
     *  (may be null)
     */
    public Object getCurrentValue() {
        return new_value;
    }
    
    /**
     * @returns the reasont this event occurred
     *  (may be null)
     */
    public Reason getReason() {
        return reason;
    }
    
    
    /**
     * string representation of this event
     * @returns the string of this object
     */
    public String toString() {
        return id.toString() + " target(" +
               getSource() + ") Reason(" +
               getReason() + ") [was,is]=[" +
          original_value + "," + new_value + "]";
    }

    /**
     * compares this object against another object
     * @param o the object to compare
     * @returns true if the objects are the same, false otherwise
     */
    public boolean equals(Object o) {
        // compares reference to the item
        return super.equals(o);
    }

    public int hashCode() {
        return super.hashCode();
    }


}
