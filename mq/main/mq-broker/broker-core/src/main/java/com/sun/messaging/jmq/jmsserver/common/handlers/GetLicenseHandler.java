/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * Handler class which deals with the GET_LICENSE message GET_LICENSE requests licensing information so the client can
 * restrict licensed features.
 */
public class GetLicenseHandler extends PacketHandler {
    // private ConnectionManager connectionList;

    private Logger logger = Globals.getLogger();
    // private BrokerResources rb = Globals.getBrokerResources();
    private static boolean DEBUG = false;

    // private static boolean ALLOW_C_CLIENTS = false;

    /**
     * Method to handle GET_LICENSE messages
     */
    @Override
    public boolean handle(IMQConnection con, Packet msg) throws BrokerException {

        if (DEBUG) {
            logger.log(Logger.DEBUGHIGH, "GetLicenseHandler: handle(" + con + ", " + PacketType.getString(msg.getPacketType()) + ")");
        }

        int status = Status.OK;

        // Create reply packet
        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setPacketType(PacketType.GET_LICENSE_REPLY);
        pkt.setConsumerID(msg.getConsumerID());

        Hashtable hash = new Hashtable();

        hash.put("JMQStatus", Integer.valueOf(status));

        // Set packet properties
        pkt.setProperties(hash);

        // Send message
        con.sendControlMessage(pkt);

        return true;
    }

}
