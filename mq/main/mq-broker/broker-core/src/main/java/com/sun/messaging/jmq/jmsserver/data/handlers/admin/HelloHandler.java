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
 * @(#)HelloHandler.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.bridge.api.BridgeServiceManager;

public class HelloHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    public HelloHandler(AdminDataHandler parent) {
	super(parent);
    }

    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                "Got Hello: " + cmd_props);
        }

	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	Hashtable props = new Hashtable();
	props.put(MessageType.JMQ_MESSAGE_TYPE,
		  Integer.valueOf(MessageType.HELLO_REPLY));

	props.put(MessageType.JMQ_INSTANCE_NAME, Globals.getConfigName());
	props.put(MessageType.JMQ_STATUS, Integer.valueOf(Status.OK));
     
    try {
        if (cmd_msg.getDestination().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)) {

            BridgeServiceManager bsm = null;
            if (!Globals.bridgeEnabled()) {
                String emsg = rb.getKString(rb.W_BRIDGE_SERVICE_NOT_ENABLED);
                logger.log(Logger.WARNING, emsg);
	            props.put(MessageType.JMQ_STATUS, Integer.valueOf(Status.UNAVAILABLE));
                props.put(MessageType.JMQ_ERROR_STRING, emsg);
            } else { 
                bsm = Globals.getBridgeServiceManager();
                if (bsm == null || !bsm.isRunning()) {
                    String emsg = rb.getKString(rb.W_BRIDGE_SERVICE_MANAGER_NOT_RUNNING);
                    logger.log(Logger.WARNING, emsg);
	                props.put(MessageType.JMQ_STATUS, Integer.valueOf(Status.UNAVAILABLE));
                    props.put(MessageType.JMQ_ERROR_STRING, emsg);
                } else {
                    reply.setReplyTo(bsm.getAdminDestinationName());
                    reply.setReplyToClass(bsm.getAdminDestinationClassName());
                }
            }
        }
    } catch (Exception e) {
        String emsg = "XXXI18N in processing admin message: "+e.getMessage();
        logger.logStack(Logger.ERROR, emsg, e); 
        props.put(MessageType.JMQ_STATUS, Integer.valueOf(Status.ERROR));
        props.put(MessageType.JMQ_ERROR_STRING, emsg);
    }
	reply.setProperties(props);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
