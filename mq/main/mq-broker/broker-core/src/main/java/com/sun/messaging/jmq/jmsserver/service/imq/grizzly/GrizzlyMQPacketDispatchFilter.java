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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import java.util.List;

/**
 */
public class GrizzlyMQPacketDispatchFilter extends BaseFilter {

    private final Attribute<GrizzlyMQIPConnection>
            connAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
               GrizzlyMQConnectionFilter.GRIZZLY_MQIPCONNECTION_ATTR);

    public GrizzlyMQPacketDispatchFilter() {
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final GrizzlyMQPacketList packetList = ctx.getMessage();
        final List<Packet> list = packetList.getPackets();
        
        GrizzlyMQIPConnection conn = connAttr.get(connection);

        try {
            for (int i = 0; i < list.size(); i++) {
                final Packet packet = list.get(i);
                if (packet == null) {
                    Globals.getLogger().log(Logger.ERROR,
                        "Read null packet from connection "+connection);
                    throw new IOException("Null Packet");
                }
                conn.receivedPacket(packet);
                conn.readData();
            }
        } catch (BrokerException e) {
            Globals.getLogger().logStack(Logger.ERROR, 
                "Failed to process packet from connection "+connection, e);
            throw new IOException(e.getMessage(), e);
        } finally {
            // @TODO investigate. we can dispose buffer, only if nobody still use it asynchronously.
            packetList.recycle(true);
//            packetList.recycle(false);
        }

        return ctx.getInvokeAction();
    }
}
