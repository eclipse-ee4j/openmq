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

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.admin.MessageType;
import java.util.*;

@SuppressWarnings("JdkObsolete")
class DestListMonitor extends Monitor {
    DestListMonitor(Destination d) {
        super(d);
    }

    @Override
    protected Hashtable getMonitorData() {

        Hashtable mapMessages = new Hashtable();

        Iterator[] itrs = Globals.getDestinationList().getAllDestinations(d.getPartitionedStore());
        Iterator itr = itrs[0]; // PART
        while (itr.hasNext()) {
            Destination oneDest = (Destination) itr.next();
            Hashtable values;
            String key;

            if (oneDest.isInternal() || oneDest.isAdmin() || (oneDest.getDestinationName().equals(MessageType.JMQ_ADMIN_DEST))
                    || (oneDest.getDestinationName().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST))) {
                continue;
            }

            values = new Hashtable();

            if (oneDest.isQueue()) {
                key = "mq.metrics.destination.queue." + oneDest.getDestinationName();
                values.put("type", "queue");
            } else {
                key = "mq.metrics.destination.topic." + oneDest.getDestinationName();
                values.put("type", "topic");
            }

            values.put("name", oneDest.getDestinationName());
            values.put("isTemporary", Boolean.valueOf(oneDest.isTemporary()));

            mapMessages.put(key, values);
        }

        return mapMessages;
    }
}

