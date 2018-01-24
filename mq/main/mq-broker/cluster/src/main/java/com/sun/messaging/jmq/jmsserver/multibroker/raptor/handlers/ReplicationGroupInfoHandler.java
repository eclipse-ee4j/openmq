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
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class ReplicationGroupInfoHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public ReplicationGroupInfoHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_REPLICATION_GROUP_INFO) {
            if (DEBUG) {
                logger.log(logger.INFO,
                    "ReplicationGroupInfoHandler.G_REPLICATION_GROUP_INFO from : ", sender);
            }
            try {
                p.receivedReplicationGroupInfo(pkt, sender);
            } catch (Exception e) {
                Object[] args = { ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                   sender, e.getMessage() };
                boolean logged = false;
                if (e instanceof BrokerException) {
                    if (((BrokerException)e).getStatusCode() == Status.NOT_ALLOWED) { 
                        logger.log(logger.WARNING, br.getKString(
                            br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args));
                        logged = true;
                    } 
                }
                if (!logged) {
                    logger.logStack(logger.ERROR, br.getKString(
                        br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args), e);
                }
            }
        }
        else {
            logger.log(logger.WARNING, "ReplicationGroupInfoHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }
}


/*
 * EOF
 */
