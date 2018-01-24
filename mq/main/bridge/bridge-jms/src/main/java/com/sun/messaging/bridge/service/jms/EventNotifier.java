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

package com.sun.messaging.bridge.service.jms;

import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
/**
 * 
 * @author amyk
 *
 */
public class EventNotifier {

    private EnumMap<EventListener.EventType, ArrayList<EventListener>> _listeners = 
        new EnumMap<EventListener.EventType, ArrayList<EventListener>>(EventListener.EventType.class);

    public EventNotifier() {}

    public void addEventListener(EventListener.EventType evt, EventListener l) {
        synchronized(_listeners) {
            ArrayList<EventListener> ls = _listeners.get(evt); 
            if (ls == null) {
                ls = new ArrayList<EventListener>();
                _listeners.put(evt, ls);
            }
            ls.add(l);
        }
    }

    public void removeEventListener(EventListener l) {
        synchronized(_listeners) {
            Iterator<EventListener.EventType> itr = _listeners.keySet().iterator();
            while (itr.hasNext()) {
                EventListener.EventType key = itr.next();
                ArrayList<EventListener> ls = _listeners.get(key);
                ls.remove(l);
                if (ls.size() == 0) itr.remove();
            }
        }
    }

    public void notifyEvent(EventListener.EventType evt, Object source) {
        EventListener[] als = null;
        synchronized(_listeners) {
            ArrayList<EventListener> ls = _listeners.get(evt);
            if (ls !=  null) {
                 als = ls.toArray(new EventListener[ls.size()]);
            }
        }
        if (als == null) return;

        for (int i = 0; i < als.length; i++) { 
             als[i].onEvent(evt, source);
        }
    }
}
