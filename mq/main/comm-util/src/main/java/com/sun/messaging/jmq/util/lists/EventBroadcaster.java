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
 * @(#)EventBroadcaster.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;


/**
 * Interface for Collection classes who notify interested
 * parties when specific events occur
 */

public interface EventBroadcaster
{
    /**
     * Request notification when the specific event occurs.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *          publish the event type passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Object userData)
        throws UnsupportedOperationException;

    /**
     * Request notification when the specific event occurs AND
     * the reason matched the passed in reason.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @param reason reason which must be associated with the
     *               event (or null for all events)
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *         support the event type or reason passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Reason reason,
                        Object userData)
        throws UnsupportedOperationException;

    /**
     * remove the listener registered with the passed in
     * id.
     * @return the listener callback which was removed
     */
    public Object removeEventListener(Object id);

}
