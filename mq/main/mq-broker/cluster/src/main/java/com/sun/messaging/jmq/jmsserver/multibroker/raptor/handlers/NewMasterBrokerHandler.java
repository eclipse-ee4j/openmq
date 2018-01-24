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

public class NewMasterBrokerHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public NewMasterBrokerHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "NewMasterBrokerHandler");

        if (pkt.getType() == ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE) {
            handleNewMasterBrokerPrepare(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY) {
            handleNewMasterBrokerPrepareReply(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_NEW_MASTER_BROKER) {
            handleNewMasterBroker(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY) {
            handleNewMasterBrokerReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "NewMasterBrokerHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    private void handleNewMasterBrokerPrepare(BrokerAddress sender, GPacket pkt) {
        int status = Status.OK; 
        String reason = null;
        try {
            p.receivedNewMasterBrokerPrepare(sender, pkt);
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
        ClusterNewMasterBrokerPrepareInfo nmpi = 
            ClusterNewMasterBrokerPrepareInfo.newInstance(pkt, c);
        GPacket reply = nmpi.getReplyGPacket(status, reason);
        try {
            c.unicast(sender, reply);  
        } catch (Exception e) {
            String[] args = new String[] {
                ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY),
                    sender.toString(), nmpi.toString() };
            logger.logStack(logger.ERROR, br.getKString(
                br.E_CLUSTER_SEND_PACKET_FAILED, args), e);
        }
    }

    private void handleNewMasterBroker(BrokerAddress sender, GPacket pkt) {
        int status = Status.OK; 
        String reason = null;
        try {
            p.receivedNewMasterBroker(sender, pkt);
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
        ClusterNewMasterBrokerInfo nmi = 
            ClusterNewMasterBrokerInfo.newInstance(pkt, c);
        GPacket reply = nmi.getReplyGPacket(status, reason);
        try {
            c.unicast(sender, reply);  
        } catch (Exception e) {
            String[] args = new String[] {
                ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY),
                    sender.toString(), nmi.toString() };
            logger.logStack(logger.ERROR, br.getKString(
                br.E_CLUSTER_SEND_PACKET_FAILED, args), e);
        }
    }

    private void handleNewMasterBrokerPrepareReply(BrokerAddress sender, GPacket pkt) {
        p.receivedNewMasterBrokerPrepareReply(sender, pkt);
    }

    private void handleNewMasterBrokerReply(BrokerAddress sender, GPacket pkt) {
        p.receivedNewMasterBrokerReply(sender, pkt);
    }
}

/*
 * EOF
 */
