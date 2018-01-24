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
 * @(#)ReloadClusterHandler.java	1.4 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class ReloadClusterHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public ReloadClusterHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_RELOAD_CLUSTER) {
            if (DEBUG) {
                logger.log(logger.DEBUG,
"MessageBus: Received reset cluster notification from {0}.",
                    sender);
            }

            if (pkt.getBit(pkt.A_BIT)) {
                GPacket gp = GPacket.getInstance();
                gp.setType(ProtocolGlobals.G_RELOAD_CLUSTER_REPLY);
                gp.putProp("S", Integer.valueOf(ProtocolGlobals.G_SUCCESS));

                try {
                    c.unicast(sender, gp);
                }
                catch (IOException e) {}
            }

            c.reloadCluster();
        }
        else if (pkt.getType() == ProtocolGlobals.G_RELOAD_CLUSTER_REPLY) {
            logger.log(logger.DEBUG,
"MessageBus: Received reset cluster reply from {0} : STATUS = {1}",
                sender, ((Integer) pkt.getProp("S")));
        }
        else {
            logger.log(logger.WARNING, "ReloadClusterHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }
}


/*
 * EOF
 */
