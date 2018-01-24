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
 * @(#)GetMetricsHandler.java	1.20 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;


public class GetMetricsHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    public GetMetricsHandler(AdminDataHandler parent) {
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
                cmd_props);
        }

        int status = Status.OK;
        String errMsg = null;

	String service = (String)cmd_props.get(MessageType.JMQ_SERVICE_NAME);

	String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
        Integer type = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);

        Object replyobj = null;
        String msgtype = null;
        if (destination != null) {
            try {
                Destination[] ds = DL.getDestination(null,
                    destination, DestType.isQueue((type == null ? 0 : type.intValue())));
                Destination d = ds[0]; //PART
                if (d == null) {
                    status = Status.NOT_FOUND;
                    int mytype = (type== null ? 0 : type.intValue());
                    errMsg = rb.getString(rb.E_NO_SUCH_DESTINATION, 
                       getDestinationType(mytype), destination);
                } else {
                    replyobj = d.getMetrics();
                }
               
            } catch (Exception ex) {
                int mytype = (type== null ? 0 : type.intValue());
                errMsg = rb.getString(rb.E_NO_SUCH_DESTINATION, 
                		getDestinationType(mytype),destination);
                status = Status.ERROR;

		// log the error
	    	logger.logStack(Logger.ERROR, rb.E_INTERNAL_BROKER_ERROR,
                	this.getClass().getName() + 
                	": failed to get destination ("+
			DestType.toString(mytype) + ":" +
			destination + ")",  ex);
            }
            msgtype = "DESTINATION";
        } else {	
            ServiceManager sm = Globals.getServiceManager();
            MetricManager mm = Globals.getMetricManager();
            MetricCounters mc = null;

            if (service != null &&
                sm.getServiceType(service) == ServiceType.UNKNOWN) {

                status = Status.NOT_FOUND;
                errMsg = rb.getString(rb.X_NO_SUCH_SERVICE, service);
            } else {
	        // If service is null getMetricCounters() will get counters
	        // for all services
	        mc = mm.getMetricCounters(service);
                if (service != null) {
                    msgtype = "SERVICE";
                }
                replyobj = mc;
            }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

        Hashtable pr = new Hashtable();
        if (msgtype != null) {
            pr.put(MessageType.JMQ_BODY_TYPE, msgtype);
        }

	setProperties(reply, MessageType.GET_METRICS_REPLY, status, errMsg,
              pr);

        if (replyobj != null) {
	    setBodyObject(reply, replyobj);
        }
	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
