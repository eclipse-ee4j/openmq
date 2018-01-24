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
 * @(#)TakeoverHandler.java	1.11 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

public class TakeoverHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public TakeoverHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                "Taking over broker: " + cmd_props);
        }

	String brokerID = (String)cmd_props.get(MessageType.JMQ_BROKER_ID);

        int status = Status.OK;
        String errMsg = null;


        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {
	/*
	 * Add takeover logic here
	 */

        logger.log(Logger.INFO,
              BrokerResources.I_ADMIN_TAKEOVER,
              brokerID);

        try {
            BrokerStateHandler bsh = Globals.getBrokerStateHandler();
            bsh.takeoverBroker(brokerID, null, true);
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR, Globals.getBrokerResources().getKString(
                   BrokerResources.E_UNABLE_TO_TAKEOVER_BKR, brokerID, ex.getMessage()), ex);
            status = Status.ERROR;
            errMsg =ex.toString();
        }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.TAKEOVER_BROKER_REPLY, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);

        return true;
    }

}
