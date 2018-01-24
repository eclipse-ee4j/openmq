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
 * @(#)GetConfigChangesHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class GetConfigChangesHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public GetConfigChangesHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "GetConfigChangesHandler");

        if (pkt.getType() == ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST) {
            handleGetConfigChanges(sender, pkt);
        }
        else if (pkt.getType() ==
            ProtocolGlobals.G_GET_CONFIG_CHANGES_REPLY) {
            handleGetConfigChangesReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "GetConfigChangesHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public void handleGetConfigChanges(BrokerAddress sender, GPacket pkt) {
        long timestamp = ((Long) pkt.getProp("TS")).longValue();
        p.receiveConfigChangesRequest(sender, timestamp);
    }

    public void handleGetConfigChangesReply(BrokerAddress sender, GPacket pkt) {

        int status = ((Integer)pkt.getProp("S")).intValue();
        long timestamp = ((Long) pkt.getProp("TS")).longValue();
        
        String emsg = null;

        int c = 0; 
        byte[] buf = null;
        if (status == ProtocolGlobals.G_SUCCESS) {
            c = ((Integer) pkt.getProp("C")).intValue();

            if (pkt.getPayload() != null) {
                buf = pkt.getPayload().array();
            }
        } else {
            emsg = (String)pkt.getProp("reason");
            if (emsg == null) {
                emsg = Status.getString(status);
            }
        }

        p.receiveConfigChangesReply(sender, timestamp, c, buf, emsg);
    }
}


/*
 * EOF
 */
