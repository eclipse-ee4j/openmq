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
 * @(#)TakeoverCompleteHandler.java	1.6 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Hashtable;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class TakeoverCompleteHandler extends GPacketHandler {

    public TakeoverCompleteHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_COMPLETE) {
            if (!Globals.getHAEnabled() && !Globals.isBDBStore()) {
            logger.log(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "Received Unexpected TAKEOVER_COMPLETE from "+sender);
            return;
            }

            handleTakeoverComplete(sender, pkt);
        }
        else {
            logger.log(logger.ERROR,  BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "Cannot handle this packet :" + pkt.toLongString());
        }
    }

    public void handleTakeoverComplete(BrokerAddress sender, GPacket pkt) {
        ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance(pkt);
         
        try {
            p.receivedTakeoverComplete(sender, cti);
        }
        catch (Exception e) {
            logger.logStack(logger.INFO, BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "Unable to process packet: " + pkt, e);
            return;
        }
    }

}
