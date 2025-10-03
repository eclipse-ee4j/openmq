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

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;

class BroadcastAnyOKReplyWaiter extends ReplyWaiter {

    private BrokerAddress okBroker = null;

    BroadcastAnyOKReplyWaiter(BrokerAddress[] tos, short replyType) {
        super(tos, replyType);
    }

    @Override
    public void onReply(BrokerAddress remote, GPacket reply) {
        if (((Integer) reply.getProp("S")).intValue() == Status.OK) {
            this.okBroker = remote;
            waitStatus = Status.OK;
            notifyAll();
            return;
        }
        if (participants.size() == 0) {
            notifyAll();
        }
    }

    @Override
    public void onAddParticipant(BrokerAddress remote) {
        // do nothing
    }

    @Override
    public void onRemoveParticipant(BrokerAddress remote, boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) {
            return;
        }
        participants.remove(remote);

        if (shutdown || participants.size() == 0) {
            waitStatus = Status.GONE;
            notifyAll();
            return;
        }
    }

    @Override
    public ReplyStatus getReply() {
        if (okBroker == null) {
            return null;
        }
        return (ReplyStatus) replies.get(okBroker);
    }
}

