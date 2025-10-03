/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class TakeoverAbortHandler extends GPacketHandler {

    public TakeoverAbortHandler(RaptorProtocol p) {
        super(p);
    }

    @Override
    public void handle(BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_TAKEOVER_ABORT) {
            if (!Globals.getHAEnabled()) {
                logger.log(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, "Received Unexpected TAKEOVER_ABORT from " + sender);
                return;
            }

            handleTakeoverAbort(sender, pkt);
        }
    }

    public void handleTakeoverAbort(BrokerAddress sender, GPacket pkt) {
        ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance(pkt);

        p.receivedTakeoverAbort(sender, cti);
    }

}
