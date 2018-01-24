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
 * @(#)DestinationUpdateHandler.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Hashtable;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;

public class DestinationUpdateHandler extends GPacketHandler {

    private DestinationList DL = Globals.getDestinationList();

    public DestinationUpdateHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_UPDATE_DESTINATION) {
            handleUpdateDestination(cb, sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_REM_DESTINATION) {
            handleRemDestination(cb, sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_UPDATE_DESTINATION_REPLY ||
            pkt.getType() == ProtocolGlobals.G_REM_DESTINATION_REPLY) {
            handleReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "DestinationUpdateHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public void handleUpdateDestination(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        ClusterDestInfo cdi = ClusterDestInfo.newInstance(pkt);

        try {
            DestinationUID duid = cdi.getDestUID(); 
            Hashtable props = cdi.getDestProps();

            Destination[] ds = DL.getDestination(null, duid);
            Destination d = ds[0];
            if (d == null) {
                ds = DL.createDestination(null, cdi.getDestName(), cdi.getDestType(),
                    ! DestType.isTemporary(cdi.getDestType()), false, selfAddress);
                d = ds[0];
                d.setDestinationProperties(props);
                cb.notifyCreateDestination(d);
            }
            else {
                cb.notifyUpdateDestination(duid, props);
            }
            if (cdi.getShareccInfo() != null) {
                cb.setLastReceivedChangeRecord(sender, cdi.getShareccInfo());
            }
        }
        catch (Exception e) {
            logger.logStack(logger.INFO,
                "Internal Exception, unable to process message " +
                pkt, e);
            return;
        }
    }

    public void handleRemDestination(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        try {
            ClusterDestInfo cdi = ClusterDestInfo.newInstance(pkt);
            DestinationUID duid = cdi.getDestUID(); 

            cb.notifyDestroyDestination(duid);
            if (cdi.getShareccInfo() != null) {
                cb.setLastReceivedChangeRecord(sender, cdi.getShareccInfo());
            }
        }
        catch (Exception e) {
            logger.logStack(logger.INFO,
                "Internal Exception, unable to process message " +
                pkt, e);
            return;
        }
    }

    public void handleReply(BrokerAddress sender, GPacket pkt) {
    }
}


/*
 * EOF
 */
