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
 * @(#)MessageDataHandler.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.util.lists.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;

public class MessageDataHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public MessageDataHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket gp) {
        if (DEBUG)
            logger.log(logger.DEBUG, "MessageDataHandler");

        if (gp.getType() == ProtocolGlobals.G_MESSAGE_DATA) {
            handleMessageData(cb, sender, gp);
        }
        else if (gp.getType() == ProtocolGlobals.G_MESSAGE_DATA_REPLY) {
            handleMessageDataReply(sender, gp);
        }
        else {
            logger.log(logger.WARNING, "MessageDataHandler " +
                "Internal error : Cannot handle this packet :" +
                gp.toLongString());
        }
    }

    public void handleMessageData(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {

        ClusterMessageInfo cmi =  ClusterMessageInfo.newInstance(pkt, c);
        boolean sendMsgDeliveredAck = cmi.getSendMessageDeliveredAck();

        LinkedHashMap<ConsumerUID, Integer> cuids = 
            new LinkedHashMap<ConsumerUID, Integer>();

        Packet roPkt;

        if (DEBUG) {
            logger.log(logger.DEBUGMED, "MessageBus: receiving message.");
        }


        try {
            cmi.initPayloadRead();
            Iterator itr = cmi.readPayloadConsumerUIDs();
            while (itr.hasNext()) {
                ConsumerUID intid = (ConsumerUID)itr.next();
                cuids.put(intid, cmi.getDeliveryCount(intid));
            }
            roPkt = cmi.readPayloadMessage();
            BrokerAddress home = cmi.getHomeBrokerAddress();
            if (home ==  null) {
                home = sender;
            }
            Long partitionid = cmi.getPartitionID();
            if (home != null && partitionid != null) {
                home.setStoreSessionUID(new UID(partitionid.longValue()));
            }
            cb.processRemoteMessage(roPkt, cuids, home, sendMsgDeliveredAck);
        } catch (Exception e) {
            logger.logStack(logger.ERROR,"Internal Exception, unable to process message " +
                       pkt, e);
        }

        if (cmi.needReply()) {
            try {
                c.unicast(sender, cmi.getReplyGPacket(ProtocolGlobals.G_SUCCESS));
            }
            catch (IOException e) {}
        }
    }


    public void handleMessageDataReply(BrokerAddress sender, GPacket gp) {
        logger.log(logger.DEBUG,
"MessageBus: Received reset G_MESSAGE_DATA_REPLY from {0} : STATUS = {1}",
            sender, ((Integer) gp.getProp("S")));
    }
}


/*
 * EOF
 */
