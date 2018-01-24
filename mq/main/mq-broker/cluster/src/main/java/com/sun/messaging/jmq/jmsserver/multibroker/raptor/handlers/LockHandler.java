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
 * @(#)LockHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class LockHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public LockHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "LockHandler");

        if (pkt.getType() == ProtocolGlobals.G_LOCK) {
            handleLock(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_LOCK_REPLY) {
            handleLockReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "LockHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    private void handleLock(BrokerAddress sender, GPacket pkt) {
        String resId;
        long timestamp;
        long xid;
        boolean shared;

        resId = (String) pkt.getProp("I");
        timestamp = ((Long) pkt.getProp("TS")).longValue();
        xid = ((Long) pkt.getProp("X")).longValue();
        shared = ((Boolean) pkt.getProp("SH")).booleanValue();

        p.receiveLockRequest(sender, resId, timestamp, xid, shared);
    }

    private void handleLockReply(BrokerAddress sender, GPacket pkt) {
        String resId;
        long xid;
        int response;

        resId = (String) pkt.getProp("I");
        xid = ((Long) pkt.getProp("X")).longValue();
        response = ((Integer) pkt.getProp("S")).intValue();

        p.receiveLockResponse(sender, resId, xid, response);
    }
}

/*
 * EOF
 */
