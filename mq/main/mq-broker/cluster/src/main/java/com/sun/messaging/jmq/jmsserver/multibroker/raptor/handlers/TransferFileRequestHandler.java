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

public class TransferFileRequestHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public TransferFileRequestHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "TransferFileRequestHandler");

        if (pkt.getType() == ProtocolGlobals.G_TRANSFER_FILE_REQUEST) {
            handleTransferFileRequest(sender, pkt);
        } else if (pkt.getType() == ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY) {
            handleTransferFileRequestReply(sender, pkt);
        } else {
            logger.log(logger.WARNING, "TakeoverMEHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    private void handleTransferFileRequest(BrokerAddress sender, GPacket pkt) {
        int status = Status.OK;
        String reason = null;
        try {
            p.receivedTransferFileRequest(sender, pkt);
        } catch (Exception e) {
            status = Status.ERROR;
            reason = e.getMessage();
            if (!(e instanceof BrokerException)) {
                String[] args = new String[] {
                    ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                    sender.toString(), e.toString() };
                reason = br.getKString(
                    br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args);
                logger.log(logger.ERROR, reason);
            }
        }
        ClusterTransferFileRequestInfo tfr = ClusterTransferFileRequestInfo.newInstance(pkt);
        try {
            c.unicast(sender, tfr.getReplyGPacket(status, reason));
        } catch (IOException e) {
            Object args = new Object[] { ProtocolGlobals.getPacketTypeDisplayString(
                                         ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY),
                                         sender, tfr.toString() };
            logger.logStack(logger.ERROR, br.getKString(
                br.E_CLUSTER_SEND_PACKET_FAILED, args), e);
        }
    }

    private void handleTransferFileRequestReply(BrokerAddress sender, GPacket pkt) {
        p.receivedTransferFileRequestReply(sender, pkt);
    }
}

/*
 * EOF
 */
