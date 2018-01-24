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
 * @(#)InterestUpdateHandler.java	1.9 07/23/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Iterator;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;

public class InterestUpdateHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public InterestUpdateHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "InterestUpdateHandler");

        if (pkt.getType() == ProtocolGlobals.G_INTEREST_UPDATE) {
            handleInterestUpdate(cb, sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_INTEREST_UPDATE_REPLY) {
            handleInterestUpdateReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "InterestUpdateHandler " +
            "Internal error : Cannot handle this packet :" +
            pkt.toLongString());
        }
    }

    private void handleInterestUpdate(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        ClusterConsumerInfo cci = ClusterConsumerInfo.newInstance(pkt, c);
        int c = cci.getConsumerCount();
        int t = cci.getSubtype();
        if (DEBUG) {
        logger.log(logger.INFO, "handleInterestUpdate: subtype= "+t+", count="+c);
        }

        ConsumerUID intid;

        try {
            Iterator itr = cci.getConsumerUIDs();
            switch (t) {
            case ProtocolGlobals.G_DURABLE_DETACH:
            case ProtocolGlobals.G_REM_INTEREST:
                while (itr.hasNext()) {
                    intid  = (ConsumerUID)itr.next();

                    Consumer cons = Consumer.getConsumer(intid);
                    if (cons == null && cci.isCleanup()) cons =Consumer.newInstance(intid);
                    if (cons != null) {
                        if (DEBUG) {
                        logger.log(logger.INFO, "Remove remote interest: "+cons+ 
                        ", pending="+cci.getPendingMessages()+", cleanup="+cci.isCleanup());
                        }
                        cb.interestRemoved(cons, cci.getPendingMessages(), cci.isCleanup());
                    }
                }
                break;

            case ProtocolGlobals.G_NEW_PRIMARY_INTEREST:
                while (itr.hasNext()) {
                    intid  = (ConsumerUID)itr.next();
                    Consumer cons = Consumer.getConsumer(intid);
                    if (cons != null) {
                        cb.activeStateChanged(cons);
                    }
                }
                break;
            }
        }
        catch (Exception e) {
            if (DEBUG) {
            logger.logStack(logger.INFO, "Exception processing packet ", e);
            }
        }
    }

    private void handleInterestUpdateReply(BrokerAddress sender, GPacket pkt) {
        logger.log(logger.DEBUG,
            "MessageBus: Received G_INTEREST_UPDATE_REPLY " +
            "from {0} : STATUS = {1}",
            sender, ((Integer) pkt.getProp("S")));
    }
}


/*
 * EOF
 */
