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
 * @(#)ResetMetricsHandler.java	1.7 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import java.util.*;


public class ResetMetricsHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public ResetMetricsHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */

    public static void resetAllMetrics()
    {
        // reset destination
        Globals.getDestinationList().resetAllMetrics(null);

        // reset all services
        List services = Globals.getServiceManager().getAllServiceNames();
        Iterator itr = services.iterator();
        while (itr.hasNext()) {
            String name = (String)itr.next();
            IMQService service = (IMQService)Globals.getServiceManager().getService(name);
            if (service != null)
                service.resetCounters();
        }

        // reset metrics manager
        MetricManager mm = Globals.getMetricManager();
        mm.reset();

	/*
	 * Reset metrics that are kept track of by JMX MBeans
	 */
	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.resetMetrics();
	}
    }

    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                cmd_props);
        }

        int status = Status.OK;
        String errMsg = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {
	String resetType = (String)cmd_props.get(MessageType.JMQ_RESET_TYPE);

	if ((resetType == null) || (resetType.equals("")))  {
            logger.log(Logger.INFO, rb.I_RESET_BROKER_METRICS);
            resetAllMetrics();
	} else  {
	    if (MessageType.JMQ_METRICS.equals(resetType))  {
                logger.log(Logger.INFO, rb.I_RESET_BROKER_METRICS);
                resetAllMetrics();
	    } else  {
		logger.log(Logger.ERROR, rb.E_INVALID_RESET_BROKER_TYPE, resetType);
		errMsg = rb.getString(rb.E_INVALID_RESET_BROKER_TYPE, resetType);
		status = Status.ERROR;
	    }
	}
	}

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.RESET_BROKER_REPLY, status, errMsg,
              null);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
