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
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.*;

abstract class ReplyWaiter {

    protected Logger logger = Globals.getLogger();

    // not overlap with jmq.io.Status
    protected static final int WAITING = 0;

    protected int waitStatus = WAITING;
    protected HashMap participants = new HashMap();
    protected HashMap replies = new HashMap();
    private short protocol;

    private static final long DEFAULT_WAIT_INTERVAL = 60000; // 60sec

    /**
     * @param participant Wait reply from
     * @param protocol Cluster protocol to wait for
     */
    ReplyWaiter(BrokerAddress participant, short protocol) {
        this(new BrokerAddress[] { participant }, protocol);
    }

    ReplyWaiter(BrokerAddress[] brokerList, short protocol) {
        this.waitStatus = WAITING;
        for (int i = 0; i < brokerList.length; i++) {
            this.participants.put(brokerList[i], "");
        }
        this.protocol = protocol;
    }

    public synchronized int getWaitStatus() {
        return waitStatus;
    }

    public synchronized void setWaitStatus(int s) {
        waitStatus = s;
        notifyAll();
    }

    private synchronized String currentParticipants() {
        StringBuilder cp = new StringBuilder("");
        Iterator itr = participants.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress addr = (BrokerAddress) itr.next();
            cp.append("\n\t" + addr.toString());
        }
        return cp.toString();
    }

    /**
     *
     * @return ReplyStatus if not aborted and Status is OK else null if aborted
     * @throws BrokerException if Status is not OK
     *
     */
    public synchronized ReplyStatus waitForReply(int timeout) throws BrokerException {
        long endtime = System.currentTimeMillis() + timeout * 1000L;

        long waittime = timeout * 1000L;
        if (waittime > DEFAULT_WAIT_INTERVAL) {
            waittime = DEFAULT_WAIT_INTERVAL;
        }
        int loglevel = Logger.DEBUGHIGH;

        int i = 0;
        while (waitStatus == WAITING) {
            try {
                Object[] args = { Integer.valueOf(i++), ProtocolGlobals.getPacketTypeDisplayString(protocol), currentParticipants() };
                logger.log(loglevel, Globals.getBrokerResources().getKTString(BrokerResources.I_CLUSTER_WAITING_REPLY, args));
                wait(waittime);
            } catch (Exception e) {
            }

            long curtime = System.currentTimeMillis();
            if (curtime >= endtime) {
                if (waitStatus == WAITING) {
                    waitStatus = Status.TIMEOUT;
                }
                continue;
            }

            waittime = endtime - curtime;
            if (waittime > DEFAULT_WAIT_INTERVAL) {
                waittime = DEFAULT_WAIT_INTERVAL;
            }
            loglevel = Logger.INFO;
        }

        if (waitStatus == Status.OK) {
            return getReply();
        }

        throw new BrokerException(Status.getString(waitStatus), waitStatus);
    }

    public synchronized void abort() {
        if (waitStatus != WAITING) {
            return;
        }

        waitStatus = Status.OK;
        notifyAll();
    }

    public synchronized void notifyReply(BrokerAddress from, GPacket reply) {
        if (participants.remove(from) != null) {
            replies.put(from, new ReplyStatus(reply));
            onReply(from, reply);
        }
    }

    public synchronized void addParticipant(BrokerAddress remote) {
        onAddParticipant(remote);
    }

    public synchronized void removeParticipant(BrokerAddress remote, boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) {
            return;
        }

        onRemoveParticipant(remote, goodbyed, shutdown);
    }

    /**
     * a. set waitStatus and notify if necessary b. check participants.size() == 0 notify if necessary
     */
    public abstract void onReply(BrokerAddress remote, GPacket reply);

    public abstract void onAddParticipant(BrokerAddress remote);

    /**
     * a. decide whether to remove from participants b. set waitStatus and notify if necessary
     */
    public abstract void onRemoveParticipant(BrokerAddress remote, boolean goodbyed, boolean shutdown);

    public abstract ReplyStatus getReply();

}

