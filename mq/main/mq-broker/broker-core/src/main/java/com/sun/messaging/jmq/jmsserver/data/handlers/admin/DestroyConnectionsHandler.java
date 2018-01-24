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
 * @(#)DestroyConnectionsHandler.java	1.10 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import com.sun.messaging.jmq.util.GoodbyeReason;
import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.Service;

public class DestroyConnectionsHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    public DestroyConnectionsHandler(AdminDataHandler parent) {
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
                "DestroyConnections: " + cmd_props);
        }

        ConnectionManager cm = Globals.getConnectionManager();

	//String serviceName = (String)cmd_props.get(MessageType.JMQ_SERVICE_NAME);
	Long cxnId = (Long)cmd_props.get(MessageType.JMQ_CONNECTION_ID);

        int status = Status.OK;
        String errMsg = null;

        Service s = null;


        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	}

        if (status == Status.OK) {

            ConnectionInfo cxnInfo = null;
            IMQConnection  cxn = null;
            if (cxnId != null) {

                logger.log(Logger.INFO, BrokerResources.I_DESTROY_CXN,
                       String.valueOf(cxnId.longValue()));
                // Get info for one connection
                cxn = (IMQConnection)cm.getConnection(
                                new ConnectionUID(cxnId.longValue()));
                if (cxn != null) {
                    if (DEBUG) {
                        cxn.dump();
                    }
                    cxn.destroyConnection(true, GoodbyeReason.ADMIN_KILLED_CON, 
                       Globals.getBrokerResources().getKString(
                       BrokerResources.M_ADMIN_REQ_CLOSE));
                } else {
                    status = Status.NOT_FOUND;
                    errMsg = rb.getString(rb.E_NO_SUCH_CONNECTION,
                        String.valueOf(cxnId.longValue()));
                }
            }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.DESTROY_CONNECTION_REPLY,
		status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
        return true;
    }


}
