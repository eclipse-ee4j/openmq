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
 * @(#)DestroyDurableHandler.java	1.21 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.audit.api.MQAuditSession;

public class DestroyDurableHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public DestroyDurableHandler(AdminDataHandler parent) {
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
            logger.log(Logger.INFO, this.getClass().getName() + ": " +
                "DestroyDurable: " + cmd_props);
        }

    	int status = Status.OK;
    	String errMsg = null;

	String durable = (String)cmd_props.get(MessageType.JMQ_DURABLE_NAME);
	String clientID = (String)cmd_props.get(MessageType.JMQ_CLIENT_ID);
        try {
            if (clientID != null && clientID.trim().length() == 0) {
                throw new BrokerException(
                    rb.getKString(rb.X_INVALID_CLIENTID, clientID),
                    Status.BAD_REQUEST);
            }
            HAMonitorService hamonitor = Globals.getHAMonitorService(); 
            if (hamonitor != null && hamonitor.inTakeover()) {
                status = Status.UNAVAILABLE;
                errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);
                logger.log(Logger.WARNING, this.getClass().getName() + ": " + errMsg);
            } else  {
                // audit logging for destroy durables
                Globals.getAuditSession().durableSubscriberOperation(
                                con.getUserName(), con.remoteHostString(),
                                MQAuditSession.DESTROY_DURABLE, durable, clientID);

                Subscription.unsubscribe(durable, clientID, true);
            }
        } catch (BrokerException ex) {
            errMsg = getMessageFromException(ex);
            status = ex.getStatusCode();
            if (status == Status.BAD_REQUEST ||
                status == Status.NOT_FOUND ||
                status == Status.UNAVAILABLE) {
                logger.log(logger.ERROR, errMsg);
            } else {
                logger.logStack(logger.ERROR, ex.getMessage(), ex);
            }
        }

	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.DESTROY_DURABLE_REPLY, status, errMsg);
	parent.sendReply(con, cmd_msg, reply);
        return true;
    }
}
