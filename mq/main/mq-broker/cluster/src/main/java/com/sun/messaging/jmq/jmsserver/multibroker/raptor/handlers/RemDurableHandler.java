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
 * @(#)RemDurableHandler.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Iterator;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;

public class RemDurableHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public RemDurableHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "RemDurableHandler");

        if (pkt.getType() == ProtocolGlobals.G_REM_DURABLE_INTEREST) {
            handleRemDurableInterest(cb, sender, pkt);
        }
        else if (pkt.getType() ==
            ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY) {
            handleRemDurableInterestAck(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "RemDurableHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public void handleRemDurableInterest(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(pkt);

        if (p.getConfigSyncComplete() == false && !csi.isConfigSyncResponse()) {
            // Do not accept the normal interest updates before
            // config sync is complete. Here is 
            if (DEBUG) {
                logger.log(logger.DEBUG,
                    "MessageBus: Dropping the G_REM_DURABLE_INTEREST " +
                    "packet from {0}. Not ready yet.", sender);
            }
            return;
        }

        Iterator itr = csi.getSubscriptions();
        try {
            int i = 0;
            ChangeRecordInfo lastcri = null;
            while(itr.hasNext()) {
                i++;
                Subscription intr = (Subscription)itr.next();
                if (intr != null) {
                    cb.unsubscribe(intr);
                }
                ChangeRecordInfo cri = csi.getShareccInfo(i);
                if (cri != null) {
                    if (lastcri == null) {
                        lastcri = cri;
                    } else if (cri.getSeq().longValue()
                               > lastcri.getSeq().longValue()) {
                        lastcri = cri;
                    }
                }
            }
            if (lastcri != null) {
                cb.setLastReceivedChangeRecord(sender, lastcri);
            }
        } catch (Exception e) { 
           logger.logStack(logger.DEBUG,"Exception processing packet ", e);
        } 

        if (csi.needReply()) {
            GPacket gp = ClusterSubscriptionInfo.getReplyGPacket(
                                                ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY,
                                                ProtocolGlobals.G_SUCCESS);
            try {
                c.unicast(sender, gp);
            }
            catch (IOException e) {}
        }
    }

    private void handleRemDurableInterestAck(BrokerAddress sender,
        GPacket pkt) {
        logger.log(logger.DEBUG,
            "MessageBus: Received G_REM_DURABLE_INTEREST_REPLY " +
            "from {0} : STATUS = {1}",
            sender, ((Integer) pkt.getProp("S")));
    }
}


/*
 * EOF
 */
