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

class TakeoverPendingReplyWaiter extends ReplyWaiter {

    TakeoverPendingReplyWaiter(BrokerAddress[] brokerList) {
        super(brokerList, ProtocolGlobals.G_TAKEOVER_PENDING_REPLY);
    }

    @Override
    public void onReply(BrokerAddress remote, GPacket reply) {
        if (participants.size() == 0) {
            waitStatus = Status.OK;
            notifyAll();
            return;
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

        if (shutdown) {
            waitStatus = Status.GONE;
            notifyAll();
            return;
        }
        if (goodbyed) {
            participants.remove(remote);
            if (participants.size() == 0) {
                waitStatus = Status.OK;
            }
            notifyAll();
            return;
        }
    }

    @Override
    public ReplyStatus getReply() {
        return null;
    }
}

