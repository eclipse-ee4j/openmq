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
 * @(#)GetConnectionsHandler.java	1.21 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

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
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.Service;

public class GetConnectionsHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();


    public GetConnectionsHandler(AdminDataHandler parent) {
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
                "GetConnections: " + cmd_props);
        }

        ConnectionManager cm = Globals.getConnectionManager();

	String serviceName = (String)cmd_props.get(MessageType.JMQ_SERVICE_NAME);
	Long cxnId = (Long)cmd_props.get(MessageType.JMQ_CONNECTION_ID);

        int status = Status.OK;
        String errMsg = null;

	Vector v = new Vector();

        Service s = null;


	/*
	 * Only one of {JMQServiceName,JMQConnectionID} will be set.
	 *
	 * If JMQServiceName is set, send back only connections on the 
	 * specified service. If JMQServiceName is not set, send back connections
	 * on all services.
	 *
	 * If JMQConnectionID is set, send back only the specified connection;
	 * send back an error if the id specified cannot be found.
	 *
	 * Scenarios:
	 *   JMQServiceName unset, JMQConnectionID unset
	 *   -> send back all connections
	 *   JMQServiceName=jms, JMQConnectionID unset
	 *   -> send back all connections on service 'jms'
	 *   JMQServiceName unset, JMQConnectionID=1234
	 *   -> send back connection with ID=1234
	 *
	 * This won't happen but in case it comes across we can do:
	 *   JMQServiceName=jms, JMQConnectionID=1234
	 *   -> send back connection with ID=1234 on service 'jms'
	 */

        if (serviceName != null) {
            s = Globals.getServiceManager().getService(serviceName);
            if (s == null) {
                status = Status.NOT_FOUND;
                errMsg = rb.getString(rb.X_NO_SUCH_SERVICE, serviceName);
            }
        }

        if (status == Status.OK) {

            ConnectionInfo cxnInfo = null;
            IMQConnection  cxn = null;
            if (cxnId != null) {
                // Get info for one connection
                cxn = (IMQConnection)cm.getConnection(
                                new ConnectionUID(cxnId.longValue()));
                if (cxn != null) {
                    if (DEBUG) {
                        cxn.dump();
                    }
                    cxnInfo = cxn.getConnectionInfo();
	            v.add(getConnectionInfoHashtable(cxnInfo));
                } else {
                    status = Status.NOT_FOUND;
                    errMsg = rb.getString(rb.E_NO_SUCH_CONNECTION,
                        String.valueOf(cxnId.longValue()));
                }
            } else {
                // Get info for all connections on a service
                List connections = cm.getConnectionList(s);
                Iterator itr = connections.iterator();
                while (itr.hasNext()) {
                    cxn     = (IMQConnection)itr.next();
	            cxnInfo = cxn.getConnectionInfo();
	            v.add(getConnectionInfoHashtable(cxnInfo));
                }
            }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.GET_CONNECTIONS_REPLY,
		status, errMsg);

	setBodyObject(reply, v);
	parent.sendReply(con, cmd_msg, reply);
        return true;
    }

    /*
     * Convenience routine used to convert/return hashtable from
     * ConnectionInfo class. This hashtable is returned in the 
     * GET_CONNECTIONS_REPLY message.
     *
     * REVISIT: Currently returns dyummy values - actual implementation
     * needs to be done.
     */
    public static Hashtable getConnectionInfoHashtable(ConnectionInfo cxnInfo) {
        Hashtable table = new Hashtable();

        table.put("cxnid", Long.valueOf(cxnInfo.uuid));

        if (cxnInfo.clientID == null) {
            cxnInfo.clientID = "";
        }
        table.put("clientid", cxnInfo.clientID);

        if (cxnInfo.remoteIP != null) {
            table.put("host", String.valueOf(
                IPAddress.rawIPToString(cxnInfo.remoteIP, true, true)));
        }
        table.put("port", Integer.valueOf(cxnInfo.remPort));
        table.put("user", cxnInfo.user);
        table.put("nproducers", Integer.valueOf(cxnInfo.nproducers));
        table.put("nconsumers", Integer.valueOf(cxnInfo.nconsumers));
        table.put("clientplatform", cxnInfo.userAgent);
        table.put("service", cxnInfo.service);

        return table;
    }

}
