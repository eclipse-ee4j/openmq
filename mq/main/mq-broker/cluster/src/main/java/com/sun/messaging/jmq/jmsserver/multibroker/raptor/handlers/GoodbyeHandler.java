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
 * @(#)GoodbyeHandler.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Hashtable;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

public class GoodbyeHandler extends GPacketHandler {

    public GoodbyeHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_GOODBYE) {
            handleGoodbye(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_GOODBYE_REPLY) {
            handleGoodbyeReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, br.E_INTERNAL_BROKER_ERROR,
                       "Cannot handle this packet :" + pkt.toLongString());
        }
    }

    public void handleGoodbye(BrokerAddress sender, GPacket pkt) {
         
        try {
            ClusterGoodbyeInfo cgi = ClusterGoodbyeInfo.newInstance(pkt, c);
            p.goodbyeReceived(sender, cgi);
            p.sendGoodbye(sender);
            cbDispatcher.processGoodbye(sender, cgi);
            if (cgi.needReply()) {
                GPacket gp = cgi.getReplyGPacket(ProtocolGlobals.G_SUCCESS);
                try {
                c.unicastAndClose(sender, gp);
                p.goodbyeReplySent(sender);
                } catch (IOException e) {
                  logger.logStack(logger.DEBUG, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.W_CLUSTER_UNICAST_FAILED, 
                    ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), sender),e);
                }
            }
        }
        catch (Exception e) {
            logger.logStack(logger.INFO, br.E_INTERNAL_BROKER_ERROR,
                            "Unable to process packet: " + pkt, e);
            return;
        }
    }

    public void handleGoodbyeReply(BrokerAddress sender, GPacket pkt) {
        cbDispatcher.processGoodbyeReply(sender);
        p.goodbyeReplyReceived(sender);
    }

}
