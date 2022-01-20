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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.util.*;

class ReplyTracker {

    private Map waiters = null;

    ReplyTracker() {
        waiters = Collections.synchronizedMap(new LinkedHashMap());
    }

    /**
     * @return The xid
     */
    public Long addWaiter(ReplyWaiter waiter) {
        Long xid = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        waiters.put(xid, waiter);
        return xid;
    }

    public void addWaiter(Long xid, ReplyWaiter waiter) {
        waiters.put(xid, waiter);
    }

    protected ReplyWaiter getWaiter(Long xid) {
        return (ReplyWaiter) waiters.get(xid);
    }

    public void removeWaiter(Long xid) {
        waiters.remove(xid);
    }

    /**
     * @param xid The reply correlation ID
     * @param timeout in seconds
     *
     * @return The reply status or null if unable to get reply status
     */
    public ReplyStatus waitForReply(Long xid, int timeout) throws BrokerException {
        ReplyWaiter waiter = (ReplyWaiter) waiters.get(xid);
        assert (waiter != null);
        try {
            return waiter.waitForReply(timeout);
        } finally {
            waiters.remove(xid);
        }
    }

    /**
     * @return The same ReplyStatus object or null if not found
     */
    public boolean notifyReply(Long xid, BrokerAddress from, GPacket reply) {
        ReplyWaiter waiter = (ReplyWaiter) waiters.get(xid);
        if (waiter == null) {
            return false;
        }
        waiter.notifyReply(from, reply);
        return true;
    }

    public void abortWaiter(Long xid) {
        ReplyWaiter waiter = (ReplyWaiter) waiters.get(xid);
        if (waiter == null) {
            return;
        }
        waiter.abort();
    }

    public void addBroker(BrokerAddress remote) {
        // not implemented
    }

    public void removeBroker(BrokerAddress remote, boolean goodbyed, boolean shutdown) {
        Long xid = null;
        ReplyWaiter waiter = null;
        Set s = waiters.keySet();
        synchronized (waiters) {
            Iterator itr = s.iterator();
            while (itr.hasNext()) {
                xid = (Long) itr.next();
                waiter = (ReplyWaiter) waiters.get(xid);
                waiter.removeParticipant(remote, goodbyed, shutdown);
            }
        }
    }
}

