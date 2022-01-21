/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.core.*;

class TakeoverCleanupThread extends Thread {

    private Logger logger = Globals.getLogger();
    private RaptorProtocol p = null;
    private BrokerAddress sender = null;
    private ClusterTakeoverInfo cti = null;
    private TakingoverEntry toe = null;
    private short protocol;
    private boolean doconverge = true;

    TakeoverCleanupThread(ThreadGroup tg, RaptorProtocol p, BrokerAddress sender, ClusterTakeoverInfo cti, TakingoverEntry toe, short protocol) {
        this(tg, p, sender, cti, toe, protocol, false);
    }

    TakeoverCleanupThread(ThreadGroup tg, RaptorProtocol p, BrokerAddress sender, ClusterTakeoverInfo cti, TakingoverEntry toe, short protocol,
            boolean doconverge) {
        super(tg, "TakeoverCleanup");
        if (Thread.MAX_PRIORITY - 1 > 0) {
            setPriority(Thread.MAX_PRIORITY - 1);
        }
        setDaemon(true);
        this.p = p;
        this.sender = sender;
        this.cti = cti;
        this.toe = toe;
        this.protocol = protocol;
        this.doconverge = doconverge;
    }

    @Override
    public void run() {
        logger.log(Logger.DEBUG, "Processing " + ProtocolGlobals.getPacketTypeString(protocol));
        p.takeoverCleanup(toe, protocol == ProtocolGlobals.G_TAKEOVER_COMPLETE);

        if (protocol == ProtocolGlobals.G_TAKEOVER_COMPLETE) {
            return;
        }
        if (doconverge) {
            p.takeoverPendingConvergecast(sender, cti);
        }
        toe.preTakeoverDone(cti.getXid());
        logger.log(Logger.DEBUG, "Done processing " + ProtocolGlobals.getPacketTypeString(protocol));
    }
}
