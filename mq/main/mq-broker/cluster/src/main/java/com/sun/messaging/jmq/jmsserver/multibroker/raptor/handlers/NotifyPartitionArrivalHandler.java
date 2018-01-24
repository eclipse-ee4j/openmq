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
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class NotifyPartitionArrivalHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public NotifyPartitionArrivalHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "NotifyPartitionArrivalHandler");

        if (pkt.getType() == ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL) {
            handleNotifyPartitionArrival(sender, pkt);

        } else if (pkt.getType() == ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY) {
            handleNotifyPartitionArrivalReply(sender, pkt);

        } else {
            logger.log(logger.WARNING, 
                "NotifyPartitionArrivalHandler: unexpected packet type " +
                 pkt.toLongString());
        }
    }

    private void handleNotifyPartitionArrival(BrokerAddress sender, GPacket pkt) {
        ClusterNotifyPartitionArrivalInfo npa = 
            ClusterNotifyPartitionArrivalInfo.newInstance(pkt, c);
        int status = Status.OK;
        String reason = null;
        try {
            p.receivedNotifyPartitionArrival(sender, pkt, npa);
        } catch (Exception e) {
            status = Status.ERROR;
            String[] args = new String[] {
                    ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                    sender.toString(), e.toString() };
            reason = br.getKString(
                     br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args);
            logger.logStack(logger.ERROR, reason, e);
        }
        sendReply(sender, npa, status, reason);
    }

    private void handleNotifyPartitionArrivalReply(BrokerAddress sender, GPacket pkt) {
        ClusterNotifyPartitionArrivalInfo npa = 
            ClusterNotifyPartitionArrivalInfo.newInstance(pkt, c);
        try {
            p.receivedNotifyPartitionArrivalReply(sender, pkt, npa);
        } catch (Exception e) {
            String[] args = new String[] {
                    ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                    sender.toString(), e.toString() };
            String reason = br.getKString(
                    br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args);
            logger.logStack(logger.WARNING, reason, e);
        }
    }

    private void sendReply(BrokerAddress sender, ClusterNotifyPartitionArrivalInfo npa,
                           int status, String reason) {
        if (npa.needReply()) {
            try {
                c.unicast(sender, npa.getReplyGPacket(status, reason));
            } catch (IOException e) {
                Object args = new Object[] { ProtocolGlobals.getPacketTypeDisplayString(
                                             ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY),
                                             sender, npa.toString() };
                logger.logStack(logger.ERROR, br.getKString(
                    br.E_CLUSTER_SEND_PACKET_FAILED, args), e);
            }

        }
    }
}

/*
 * EOF
 */
