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
 * @(#)GetLogsHandler.java	1.12 06/28/07
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

public class GetLogsHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public GetLogsHandler(AdminDataHandler parent) {
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

        String body = "log.txt|log_1.txt";

	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.TEXT_MESSAGE);

	setProperties(reply, MessageType.GET_LOGS_REPLY, Status.NOT_IMPLEMENTED,
            null);

        try {
            reply.setMessageBody(body.getBytes("UTF8"));
        } catch (Exception e) {
            // Programing error. No need to localize
	    logger.logStack(Logger.ERROR, rb.E_INTERNAL_BROKER_ERROR,
                this.getClass().getName()+": could not set message body: ", e);
        }

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
