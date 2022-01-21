/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;

class InterestRemovedCallbackEvent extends CallbackEvent {
    private Consumer intr;
    private Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs = null;
    private boolean cleanup = false;

    InterestRemovedCallbackEvent(Consumer intr, Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs, boolean cleanup) {
        this.intr = intr;
        this.pendingMsgs = pendingMsgs;
        this.cleanup = cleanup;
    }

    @Override
    public void dispatch(MessageBusCallback cb) {
        cb.interestRemoved(intr, pendingMsgs, cleanup);
    }

    @Override
    public String toString() {
        return "InterestRemoved: " + intr + ", cleanup=" + cleanup + ", pendingMsgs=" + pendingMsgs;
    }
}

