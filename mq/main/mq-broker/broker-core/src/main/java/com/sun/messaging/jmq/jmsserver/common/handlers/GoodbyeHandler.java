/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.common.handlers;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.GoodbyeReason;

import java.util.*;

/**
 * Handler class which deals with a "goodbye" message which is sent when a client quits talking to the broker
 */
public class GoodbyeHandler extends PacketHandler {

    private static long timeout = Globals.getConfig().getLongProperty(Globals.IMQ + ".goodbye.timeout", 0);

    // ConnectionManager conlist = null;

    public GoodbyeHandler() {
        // conlist = mgr;
        timeout = Globals.getConfig().getLongProperty(Globals.IMQ + ".goodbye.timeout", 0);
        GoodbyeTask.initialize(timeout);
    }

    /**
     * Method to handle goodbye messages
     */
    @Override
    public boolean handle(IMQConnection con, Packet msg) throws BrokerException {
        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, "GOODBY Packet.getProperties()", ex);
            props = new Hashtable();
        }

        boolean notAuthenticated = !con.isAuthenticated();
        if (con.isValid() && notAuthenticated) {
            logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_RECEIVED_GOODBYE_UNAUTHENTICATED_CONN,
                    con.getConnectionUID().longValue() + "[" + con.getRemoteConnectionString() + "]"));
        }

        Boolean blockprop = (props != null ? (Boolean) props.get("JMQBlock") : null);
        boolean block = (blockprop != null && blockprop.booleanValue());

        // send the reply (if necessary)
        con.stopConnection();
        if (block) {
            con.cleanupConnection();
        }
        boolean destroy = false;
        if (msg.getSendAcknowledge()) {
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setPacketType(PacketType.GOODBYE_REPLY);
            pkt.setConsumerID(msg.getConsumerID());
            Hashtable hash = new Hashtable();
            hash.put("JMQStatus", Integer.valueOf(Status.OK));
            if (((IMQBasicConnection) con).getDumpPacket() || ((IMQBasicConnection) con).getDumpOutPacket()) {
                hash.put("JMQReqID", msg.getSysMessageID().toString());
            }
            pkt.setProperties(hash);
            con.sendControlMessage(pkt);
            // increase timeout
            if (con.isBlocking()) {
                if (con instanceof IMQBasicConnection) {
                    IMQBasicConnection ipCon = (IMQBasicConnection) con;
                    ipCon.flushControl(timeout);
                }
                destroy = true;
            } else {
                con.setDestroyReason(Globals.getBrokerResources().getKString(BrokerResources.M_CLIENT_SHUTDOWN));
                GoodbyeTask.addConnection(con.getConnectionUID(), Globals.getBrokerResources().getKString(BrokerResources.M_CLIENT_SHUTDOWN));
            }
        } else {
            destroy = true;
        }
        if (destroy) {
            con.destroyConnection(false /* no reply */, GoodbyeReason.CLIENT_CLOSED,
                    Globals.getBrokerResources().getKString(BrokerResources.M_CLIENT_SHUTDOWN));
        }
        return true;
    }

}

