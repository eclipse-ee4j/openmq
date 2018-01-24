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
 * @(#)EventBroadcastHelper.java	1.20 08/06/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.io.*;


/**
 * this is a helper class to be used by
 * lists that implement EventBroadcaster
 */

public class EventBroadcastHelper implements EventBroadcaster
{
    Collection c[] = new Collection[EventType.EVENT_TYPE_NUM];
    boolean busy[] = new boolean[EventType.EVENT_TYPE_NUM];
    int start[] = null;
    int cnt = 0;
    Boolean orderMaintained = Boolean.valueOf(true);

    Object orderMaintainedLock = new Object();

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Lock shareLock = lock.readLock();
    private Lock exclusiveLock = lock.writeLock();

    // we change the order to address bug 4939969
    // I'm keeping the old behavior in the system incase
    // we ever need the system to respond in a more definitive manner
    /**
     * determines if listeners should always be called in
     * the same order or the system should change the order on
     * each call. Added to address bug 4939969.
     * @param order if true, order will be maintained
     */
    public void setOrderMaintained(boolean order) {
        synchronized (orderMaintainedLock) {
            orderMaintained = order;
            if (!orderMaintained) {
                start = new int[EventType.EVENT_TYPE_NUM];
            }
        }
    }

    /**
     * creates a new EventBroadcastHelper
     */
    public EventBroadcastHelper() {
    }

    /**
     * clears all listeners from the helper
     */
    public void clear() {
        exclusiveLock.lock();
        try {
            c = new Collection[EventType.EVENT_TYPE_NUM];
            for (int i=0; i < EventType.EVENT_TYPE_NUM; i ++)
                busy[i] = false;
        } finally {
            exclusiveLock.unlock();
        }
    }

    /**
     * dumps the state of the helper
     * @param ps stream to write the state to
     */
    public void dump(PrintStream ps) {
        ps.println(toString());
    }

    /**
     * converts the state of the object to a string
     * @return the object as a string
     */
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("EventBroadcastHelper {\n");

        shareLock.lock();
        try {
            str.append("\tcnt="+cnt+"\n");
            for (int i =0, len = c.length; i < len; i++) {
                boolean indent = false;
                str.append("\t"+i+"busy["+i+"]="+busy[i]+" { ");
                if (c[i] == null) {
                    str.append("null");
                } else {
                    Iterator itr = c[i].iterator();
                    boolean first = true;
                    int indx = 0;
                    while (itr.hasNext()) {
                        ListenerInfo li = (ListenerInfo)itr.next();
                        indent = true;
                        if (!first) {
                            str.append("\t    ");
                        }
                        first = false;
                        str.append(indx + ":  "+li.getListener()
                                + "\n\t        "
                                + li.getType()
                                + "\n\t        "
                                + li.getReason()
                                + "\n\t        "
                                + li.getUserData()
                                + "\n");
                        indx ++;
                    }
                }
                if (indent) {
                    str.append("\t  }\n");
                } else {
                    str.append(" }\n");
                }
            }
        } finally {
            shareLock.unlock();
        }

        return str.toString();
    }

    /**
     * Request notification when the specific event occurs.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @return an id associated with this notification
     */
    public Object addEventListener(EventListener listener,
                        EventType type, Object userData) {
        return addEventListener(listener, type, null, userData);
    }

    /**
     * Request notification when the specific event occurs AND
     * the reason matched the passed in reason.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @param reason reason which must be associated with the
     *               event (or null for all events)
     * @return an id associated with this notification
     */
    public Object addEventListener(EventListener listener,
                        EventType type, Reason reason,
                        Object userData) {
        ListenerInfo li = new ListenerInfo(listener, type, reason, userData);
        int indx = type.getEvent();
 
        // OK .. assuming adding & removing listeners are a rare
        // event so it can be slow (limit locks later)
        exclusiveLock.lock();
        try {
            if (c[indx] == null) {
                c[indx] = new ArrayList();
                c[indx].add(li);
            } else {
                ArrayList ls = new ArrayList(c[indx]);
                ls.add(li);
                c[indx] = ls;
            }
            busy[indx]=true;
            cnt ++;
        } finally {
            exclusiveLock.unlock();
        }

        return li;
    }

    /**
     * remove the listener registered with the passed in
     * id.
     * @return the listener callback which was removed
     */
    public Object removeEventListener(Object id) {
        exclusiveLock.lock();
        try {
            if (id == null) return null;
            ListenerInfo li = (ListenerInfo) id;
            if (!li.isValid()) return null;
            int indx = li.getType().getEvent();
            Collection s = c[indx];
            if (s == null) return null;
            ArrayList newset = new ArrayList(s);
            newset.remove(li);
            busy[indx]=!newset.isEmpty();
            c[indx] = newset;
            EventListener l = li.getListener();
            li.clear();
            cnt --;
            return l;
        } finally {
            exclusiveLock.unlock();
        }
    }

    /**
     * method which notifies all listeners an event
     * has occurred.
     * @param type of event that has occurred
     * @param r why the event occurred (may be null)
     * @param target the event occurred on
     * @param oldval value before the event
     * @param newval value after the event
     */
    public void notifyChange(EventType type,  Reason r, 
        Object target, Object oldval, Object newval)
    {
        shareLock.lock();
        try {
            ArrayList l = (ArrayList)c[type.getEvent()];
            if (l == null || l.isEmpty()) {
                return;
            }

            int offset = 0;
            int size = l.size();
            if (size > 1) {
                synchronized (orderMaintainedLock) {
                    if (!orderMaintained && start != null) {
                        offset = start[type.getEvent()];
                        start[type.getEvent()] = (offset>= size-1) ? 0 : offset + 1;
                    }
                }
            }

            for (int count = 0; count < size; count ++) {
                // OK .. this code seems to be very timing senstive
                // on mq880 ... dont know why
                // this obscure calculation insures:
                //     offset = 0, index goes from 0-size
                //     offset = n, index wraps from n -> n-1
                ListenerInfo info = null;
                int index = (offset == 0 ? count : ((count + offset)%size));
                if (index < l.size()) {
                    info = (ListenerInfo)l.get(index);
                } else {
                    continue; // list changed
                }

                if (info == null) continue;

                EventListener ll = info.getListener();
                Reason lr = info.getReason();
                Object ud = info.getUserData();
                if (ll != null && (lr == null || lr == r )) {
                    ll.eventOccured(type, r, target, oldval, newval, ud);
                }
            }
        } finally {
            shareLock.unlock();
        }
    }

    /**
     * quick check to determine if the broadcaster
     * has any listeners of a specific type
     * @param type type of event to look at
     * @return true if the broadcaster has listeners of that
     *          type
     */
    public boolean hasListeners(EventType type) {
        shareLock.lock();
        try {
            return busy[type.getEvent()];
        } finally {
            shareLock.unlock();
        }
    }

    /**
     * quick check to determine if the broadcaster
     * has any listeners of a any type
     * @return true if the broadcaster has any listeners
     */
    public boolean hasListeners() {
        shareLock.lock();
        try {
            return cnt > 0;
        } finally {
            shareLock.unlock();
        }
    }

    /**
     * class maintaining event listener information
     */
    static class ListenerInfo {
        boolean valid = true;
        EventListener l;
        EventType type;
        Object userData;
        Reason reason;

        public ListenerInfo(EventListener l, EventType t, Reason r, Object ud) 
        {
            this.l = l;
            this.type = t;
            this.userData =ud;
            this.reason = r;
        }

        Reason getReason() {
            return reason;
        }
        boolean isValid() {
            return valid;
        }

        Object getUserData() {
            return userData;
        }

        EventType getType() {
            return type;
        }

        EventListener getListener() {
            return l;
        }

        void clear() {
            valid = false;
            l = null;
            userData = null;
            reason = null;
            type = null;
        }
        public String toString() {
            return l+"["+type+", reason="+reason+", userData="+userData+", valid="+valid+"]";
        }
    }
}


