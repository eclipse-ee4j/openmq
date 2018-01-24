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
 * @(#)Event.java	1.3 07/02/07
 */ 

package com.sun.messaging.jms.notification;

/**
 * MQ Event.  This is the super class for all MQ notification
 * events. MQ may notify an application when a specific MQ event is
 * about to occur or occurred.
 * <p>
 */
public class Event extends java.util.EventObject {

    /**
     * MQ event code.
     */
    private String eventCode = null;

    /**
     * MQ event message.  An event message describes a MQ specific event.
     */
    private String eventMessage = null;

    /**
     * Construct a MQ event associated with the source specified.
     *
     * @param source the source associated with the event.
     * @param evCode the event code that represents the this event object.
     * @param evMessage the event message that describes this event object.
     */
    public Event (Object source, String evCode, String evMessage) {
        super (source);

        this.eventCode = evCode;
        this.eventMessage = evMessage;
    }

    /**
     * Get the event code associated with the MQ event.
     * @return the event code associated with the MQ event.
     */
    public String getEventCode() {
        return this.eventCode;
    }

    /**
     * Get the event message associated with the connection event.
     * @return the event message associated with the connection event.
     */
    public String getEventMessage() {
        return this.eventMessage;
    }

    /**
    * Returns a MQ event notification event message.  The format is as follows.
    * <p>
    * eventCode + ":" + eventMessage + ", " + source=" + source.toString().
    *
    * @return a String representation of this EventObject.
    */
    public String toString() {
        return this.getEventCode() + ":" + this.getEventMessage() + ", " +
            super.toString();
    }
}
