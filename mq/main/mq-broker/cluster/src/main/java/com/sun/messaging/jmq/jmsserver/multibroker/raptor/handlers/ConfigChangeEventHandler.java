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
 * @(#)ConfigChangeEventHandler.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class ConfigChangeEventHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public ConfigChangeEventHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "ConfigChangeEventHandler");

        if (pkt.getType() == ProtocolGlobals.G_CONFIG_CHANGE_EVENT) {
            handleConfigChangeEvent(sender, pkt);
        }
        else if (pkt.getType() ==
            ProtocolGlobals.G_CONFIG_CHANGE_EVENT_REPLY) {
            handleConfigChangeEventReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "ConfigChangeEventHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public void handleConfigChangeEvent(BrokerAddress sender,
        GPacket pkt) {
        Long xidProp = (Long) pkt.getProp("X");
        p.receiveConfigChangeEvent(sender, xidProp,
            pkt.getPayload().array());
    }

    public void handleConfigChangeEventReply(BrokerAddress sender,
        GPacket pkt) {
	//	Bug ID 6252184 Escalation ID 1-8243878
	//
	//	Backported by Tom Ross tom.ross@sun.com
	//
	//	14 April 2005
	// old line below
	// long xid = ((Long) pkt.getProp("X")).longValue();
	// new line below
	Long xid = (Long)pkt.getProp("X");
        int status = ((Integer)pkt.getProp("S")).intValue();
        String reason = (String)pkt.getProp("reason");

        p.receiveConfigChangeEventReply(sender, xid, status, reason);
    }
}


/*
 * EOF
 */
