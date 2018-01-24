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
 * @(#)PingHandler.java	1.5 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class PingHandler extends GPacketHandler {
    private static boolean DEBUG = false;
    private HashMap<BrokerAddress, AtomicInteger> pingLogging = 
                   new HashMap<BrokerAddress, AtomicInteger>();

    public PingHandler(RaptorProtocol p) {
        super(p);
    }

    public void enablePingLogging(BrokerAddress addr) {
        AtomicInteger logreq;
        synchronized(pingLogging) {
            logreq = pingLogging.get(addr);
            if (logreq == null) {
                pingLogging.put(addr, new AtomicInteger(2));
                return;
            }
        }
        logreq.compareAndSet(0, 2);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_PING) {

            logPing(ProtocolGlobals.G_PING, sender);

            if (pkt.getBit(pkt.A_BIT)) {
                GPacket gp = GPacket.getInstance();
                gp.setType(ProtocolGlobals.G_PING_REPLY);
                gp.putProp("S", Integer.valueOf(ProtocolGlobals.G_SUCCESS));

                try {
                    c.unicast(sender, gp);
                }
                catch (IOException e) {}
            }

        } else if (pkt.getType() == ProtocolGlobals.G_PING_REPLY) {
            logPing(ProtocolGlobals.G_PING_REPLY, sender);

        } else {
            logger.log(logger.WARNING, "PingHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    private void logPing(int ptype, BrokerAddress sender) {
        AtomicInteger logreq;
        synchronized(pingLogging) {
            logreq = pingLogging.get(sender);
        }
        if ((logreq != null && logreq.get() > 0) || DEBUG) {
            Object[] args = new Object[] {
                     ProtocolGlobals.getPacketTypeDisplayString(ptype),
                     "", sender };
            logger.log(logger.INFO, br.getKString(BrokerResources.I_CLUSTER_RECEIVE, args));
            if (logreq != null && logreq.get() > 0) {
                logreq.decrementAndGet();
            }
        }
    }
}

/*
 * EOF
 */
