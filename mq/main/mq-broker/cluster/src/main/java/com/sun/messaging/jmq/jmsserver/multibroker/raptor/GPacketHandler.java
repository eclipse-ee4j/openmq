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
 * @(#)GPacketHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.multibroker.*;

public abstract class GPacketHandler {
    protected RaptorProtocol p = null;

    protected Cluster c = null;
    protected BrokerAddress selfAddress = null;
    protected CallbackDispatcher cbDispatcher = null;

    protected Logger logger = Globals.getLogger();
    protected BrokerResources br = Globals.getBrokerResources();

    public GPacketHandler(RaptorProtocol p) {
        this.p = p;
        this.c = p.c;
        this.selfAddress = p.selfAddress;
        this.cbDispatcher = p.cbDispatcher;
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        handle(sender, pkt);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
	logger.log(logger.ERROR, "Unexpected "+
             ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+ " packet received");
    }

}

/*
 * EOF
 */
