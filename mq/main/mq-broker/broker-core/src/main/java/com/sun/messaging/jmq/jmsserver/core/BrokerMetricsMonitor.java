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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.util.MetricData;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.admin.MessageType;
import java.util.*;

class BrokerMetricsMonitor extends Monitor {

    BrokerMetricsMonitor(Destination d) {
        super(d);
    }

    @Override
    protected Hashtable getMonitorData() {

        Hashtable mapMessage = new Hashtable();

        MetricManager mm = Globals.getMetricManager();
        MetricData md = mm.getMetrics();
        mapMessage.put("numConnections", Long.valueOf(md.nConnections));
        mapMessage.put("numMsgsIn", Long.valueOf(md.totals.messagesIn));
        mapMessage.put("numMsgsOut", Long.valueOf(md.totals.messagesOut));
        mapMessage.put("numMsgs", Long.valueOf(Globals.getDestinationList().totalCount()));

        mapMessage.put("msgBytesIn", Long.valueOf(md.totals.messageBytesIn));
        mapMessage.put("msgBytesOut", Long.valueOf(md.totals.messageBytesOut));
        mapMessage.put("numPktsIn", Long.valueOf(md.totals.packetsIn));
        mapMessage.put("numPktsOut", Long.valueOf(md.totals.packetsOut));
        mapMessage.put("pktBytesIn", Long.valueOf(md.totals.packetBytesIn));
        mapMessage.put("pktBytesOut", Long.valueOf(md.totals.packetBytesOut));

        mapMessage.put("totalMsgBytes", Long.valueOf(Globals.getDestinationList().totalBytes()));

        /*
         * Calculate number of destinations. We cannot use Destination.destinationsSize() here because that includes all
         * destinations. We need to filter out internal/admin destinations - basically what is returned by DestListMonitor.
         */
        Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0];
        long numDests = 0;
        while (itr.hasNext()) {
            Destination oneDest = (Destination) itr.next();

            if (oneDest.isInternal() || oneDest.isAdmin() || (oneDest.getDestinationName().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST))
                    || (oneDest.getDestinationName().equals(MessageType.JMQ_ADMIN_DEST))) {
                continue;
            }
            numDests++;
        }

        mapMessage.put("numDestinations", Long.valueOf(numDests));

        return mapMessage;
    }
}
