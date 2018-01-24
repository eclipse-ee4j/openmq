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

import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class InfoHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public InfoHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG) {
            logger.log(logger.INFO, "InfoHandler");
        }

        if (pkt.getType() == ProtocolGlobals.G_INFO_REQUEST) {
            ClusterInfoRequestInfo cir = ClusterInfoRequestInfo.newInstance(pkt);
            if (ClusterInfoInfo.isStoreSessionOwnerInfo(pkt)) {
                p.receiveLookupStoreSessionOwnerRequest(sender, cir);
            }
        } else if (pkt.getType() == ProtocolGlobals.G_INFO) {
            ClusterInfoInfo cii = ClusterInfoInfo.newInstance(pkt);
            if (cii.isStoreSessionOwnerInfo(pkt)) {
                p.receiveStoreSessionOwnerInfo(sender, cii, pkt);
            }
            if (cii.isPartitionAddedInfo(pkt)) {
                p.receivePartitionAddedInfo(sender, cii, pkt);
            }
        } else {
            logger.log(logger.WARNING, "InfoHandler " +
                "Cannot handle this packet :" + pkt.toLongString());
        }
    }
}
