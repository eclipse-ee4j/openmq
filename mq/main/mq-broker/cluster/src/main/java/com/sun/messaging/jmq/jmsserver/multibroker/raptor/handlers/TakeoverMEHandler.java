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

public class TakeoverMEHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public TakeoverMEHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "TakeoverMEHandler");

        if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ME_PREPARE) {
            handleTakeoverMEPrepare(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ME_PREPARE_REPLY) {
            handleTakeoverMEPrepareReply(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ME) {
            handleTakeoverME(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ME_REPLY) {
            handleTakeoverMEReply(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK) {
            handleTakeoverMEReplyAck(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "TakeoverMEHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    private void handleTakeoverMEPrepare(BrokerAddress sender, GPacket pkt) {
        ClusterTakeoverMEPrepareInfo tme = 
            ClusterTakeoverMEPrepareInfo.newInstance(pkt, c);
        try {
            p.receivedTakeoverMEPrepare(sender, pkt, tme);
        } catch (Exception e) {
            int status = Status.ERROR;
            String reason = e.getMessage();
            if (!(e instanceof BrokerException)) {
                String[] args = new String[] {
                    ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                    sender.toString(), e.toString() };
                reason = br.getKString(
                    br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args);
                logger.log(logger.ERROR, reason);
            }
            tme.sendReply(sender, status, reason, null);
        }
    }

    private void handleTakeoverME(BrokerAddress sender, GPacket pkt) {
        ClusterTakeoverMEInfo tme = ClusterTakeoverMEInfo.newInstance(pkt, c);
        try {
            p.receivedTakeoverME(sender, pkt, tme);
        } catch (Exception e) {
            int status = Status.ERROR;
            String reason = e.getMessage();
            if (!(e instanceof BrokerException)) {
                String[] args = new String[] {
                    ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                    sender.toString(), e.toString() };
                reason = br.getKString(
                    br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args);
                logger.log(logger.ERROR, reason);
            }
            tme.sendReply(sender, status, reason, null);
        }
    }

    private void handleTakeoverMEPrepareReply(BrokerAddress sender, GPacket pkt) {
        p.receivedTakeoverMEPrepareReply(sender, pkt);
    }

    private void handleTakeoverMEReply(BrokerAddress sender, GPacket pkt) {
        p.receivedTakeoverMEReply(sender, pkt);
    }

    private void handleTakeoverMEReplyAck(BrokerAddress sender, GPacket pkt) {
        p.receivedTakeoverMEReplyAck(sender, pkt);
    }
}

/*
 * EOF
 */
