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
 * @(#)FlowPausedHandler.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.log.*;
import java.io.IOException;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;

import java.util.*;



/**
 */
public class FlowPausedHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    public FlowPausedHandler() {
    }

    /**
     * Method to handle FlowPaused messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
        assert msg.getPacketType() == PacketType.FLOW_PAUSED;
        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR,
                  Globals.getBrokerResources().getString(
                      BrokerResources.X_INTERNAL_EXCEPTION,
                      "FlowPaused"
                     ), ex);
            assert false : ex;
        }

        Long size_var = (props != null ?(Long)props.get("JMQSize") : null);
        long size = (size_var == null ? 0 : size_var.longValue());

        con.flowPaused(size);

        // client doesnt sent this message at this time

        assert false : "Unsupported - XXX " + msg;


        return true;
    }

}
