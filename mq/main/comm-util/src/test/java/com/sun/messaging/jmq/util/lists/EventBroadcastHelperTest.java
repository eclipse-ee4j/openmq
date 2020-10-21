/*
 * Copyright (c) 2020 Contributors to Eclipse Foundation. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventBroadcastHelperTest {
    private EventBroadcastHelper helper = new EventBroadcastHelper();

    @Test
    void testAddingEventListener() {
        Object userData = new Object();
        Reason reason = new Reason(3, "testing");
        EventType type = EventType.SET_CHANGED;
        EventListener listener = EventBroadcastHelperTest::eventListenerImpl;

        Object id = helper.addEventListener(listener, type, reason, userData);

        assertThat(id).isNotNull();
    }

    private static void eventListenerImpl(EventType t, Reason r, Object s, Object ov, Object nv, Object ud) {
    }
}

