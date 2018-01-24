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
 * @(#)DestroyDestinationHandler.java	1.29 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.audit.api.MQAuditSession;

public class DestroyDestinationHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public DestroyDestinationHandler(AdminDataHandler parent) {
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
                "Destroying destination: " + cmd_props);
        }

	String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
	Integer destType = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);

        int status = Status.OK;
        String errMsg = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {
        try {

            if (destType == null) {
                throw new Exception(rb.X_NO_DEST_TYPE_SET);
            }

            // audit logging for destroy destination
            Globals.getAuditSession().destinationOperation(
            		con.getUserName(), con.remoteHostString(),
            		MQAuditSession.DESTROY_DESTINATION,
            		DestType.isQueue(destType.intValue())?MQAuditSession.QUEUE:MQAuditSession.TOPIC,
					destination);

            Destination[] ds = DL.removeDestination(null, destination,
                             DestType.isQueue(destType.intValue()),
                             rb.getString(rb.M_ADMIN_REQUEST));
            Destination d = ds[0];
            boolean ok = (d != null);

            if (!ok) {
                status = Status.ERROR;
                String subError = rb.getString(rb.E_NO_SUCH_DESTINATION,
                        getDestinationType(destType.intValue()), destination);
                errMsg = rb.getString( rb.X_DESTROY_DEST_EXCEPTION, 
                            destination, subError);

            }
        } catch (Exception ex) {
            status = Status.ERROR;
            errMsg = rb.getString( rb.X_DESTROY_DEST_EXCEPTION, 
                            destination, getMessageFromException(ex));
            logger.logStack(Logger.WARNING, rb.X_DESTROY_DEST_EXCEPTION,
                             destination, "",ex);

        }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.DESTROY_DESTINATION_REPLY,
		status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
