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
 * @(#)PingHandler.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.memory.MemoryManager;



public class PingHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    //private BrokerResources rb = Globals.getBrokerResources();
    private static boolean DEBUG = false;

    public PingHandler()
    {
    }

    /**
     * Method to handle Acknowledgement messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 

        if (DEBUG) {
             logger.log(Logger.DEBUGHIGH, "PingHandler: handle() [ Received Ping Message]");
        }

        if (msg.getSendAcknowledge()) {
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setPacketType(PacketType.PING_REPLY);
            pkt.setConsumerID(msg.getConsumerID());
            Hashtable hash = new Hashtable();
            int status = Status.OK;
            
            hash.put("JMQStatus", Integer.valueOf(status));
            pkt.setProperties(hash);

            con.sendControlMessage(pkt);
         }

        return true;

    }

}
