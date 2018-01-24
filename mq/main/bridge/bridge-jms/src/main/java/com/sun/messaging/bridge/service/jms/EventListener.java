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
import java.util.Collections;
/**
 * 
 * @author amyk
 *
 */
public class EventListener {
    
    public enum EventType { BRIDGE_STOP, LINK_STOP, DMQ_STOP, CONN_CLOSE };

    private List<EventType> _occurredEvents = Collections.synchronizedList(
                                                 new ArrayList<EventType>());
    private Object src = null;
    private boolean eventOccurred = false;

    public EventListener(Object source) {
        this.src = source;
    }
    public void onEvent(EventType evt, Object source) {
        if (src == source || evt == EventType.BRIDGE_STOP) {
            eventOccurred = true;
        }
        _occurredEvents.add(evt);
    }

    public boolean hasEventOccurred() {
        return eventOccurred;
    }
    
    public EventType occurredEvent() {
        return _occurredEvents.get(0); 
    }

    public List<EventType> getOccurredEvents() {
        synchronized(_occurredEvents) {
            ArrayList<EventType> list = new ArrayList<EventType>(_occurredEvents);
            return list;
        }
    }
}
