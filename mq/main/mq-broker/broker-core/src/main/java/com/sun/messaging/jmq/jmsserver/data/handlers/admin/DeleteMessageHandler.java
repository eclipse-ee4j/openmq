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
 * @(#)DeleteMessageHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Vector;
import java.util.Hashtable;
import java.util.HashMap;
import java.nio.ByteBuffer;
import javax.jms.*;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class DeleteMessageHandler extends AdminCmdHandler  {
    private static boolean DEBUG = getDEBUG();

    public DeleteMessageHandler(AdminDataHandler parent) {
        super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con    The Connection the message came in on.
     * @param cmd_msg    The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
                       Hashtable cmd_props) {

        if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                            "Getting messages: " + cmd_props);
        }

        int status = Status.OK;
        String errMsg = null;

        String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
        Integer destType = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);
        String msgID = (String)cmd_props.get(MessageType.JMQ_MESSAGE_ID);

	if (destType == null)  {
            errMsg = "DELETE_MESSAGE: destination type not specified";
            logger.log(Logger.ERROR, errMsg);
            status = Status.BAD_REQUEST;
	}
        if (status == Status.OK) { 
            try {
                deleteMessage(msgID, destination, DestType.isQueue(destType.intValue()));
            } catch (Exception e) {
                status = Status.ERROR;
                errMsg= e.getMessage();
                boolean logstack = true;
                if (e instanceof BrokerException) {
                    status = ((BrokerException)e).getStatusCode();
                    if (status == Status.NOT_ALLOWED || status == Status.NOT_FOUND || 
                        status == Status.CONFLICT || status == Status.BAD_REQUEST) {
                        logstack = false;
                    }
                }
                Object[] args = { ""+msgID, ""+destination, e.getMessage() };
                errMsg = rb.getKString(rb.X_ADMIN_DELETE_MSG, args);
                if (logstack) {
                    logger.logStack(Logger.ERROR, errMsg, e);
                } else {
                    logger.log(Logger.ERROR, errMsg, e);
                }
            }
        }
        // Send reply
        Packet reply = new Packet(con.useDirectBuffers());
        reply.setPacketType(PacketType.OBJECT_MESSAGE);
        setProperties(reply, MessageType.DELETE_MESSAGE_REPLY, status, errMsg);
        parent.sendReply(con, cmd_msg, reply);
        return true;
    }

    public void deleteMessage(String msgID,  String destination, boolean isQueue) 
    throws BrokerException, IOException {

	if (destination == null)  {
            String emsg = "DELETE_MESSAGE: destination name not specified";
            throw new BrokerException(emsg, Status.BAD_REQUEST);
	}

	if (msgID == null)  {
            String emsg = "DELETE_MESSAGE: Message ID not specified";
            throw new BrokerException(emsg, Status.BAD_REQUEST);
	}

        Destination[] ds = DL.getDestination(null, destination, isQueue);
        Destination d = ds[0]; //PART
        if (d == null) {
            String emsg = "DELETE_MESSAGE: "+
                           rb.getString(rb.X_DESTINATION_NOT_FOUND, destination);
            throw new BrokerException(emsg, Status.NOT_FOUND);
        }
        if (DEBUG) {
            d.debug();
        }

        logger.log(Logger.INFO, rb.getKString(
                   rb.I_ADMIN_DELETE_MESSAGE, msgID, d.getDestinationUID()));

        SysMessageID sysMsgID = SysMessageID.get(msgID);
        PacketReference pr = DL.get(d.getPartitionedStore(), sysMsgID);
        if (pr == null)  {
            String emsg = "Could not locate message " + msgID+
                          " in destination " + destination;
            throw new BrokerException(emsg, Status.NOT_FOUND);
        }
        if (!pr.isLocal()) {
            Object[] args = { msgID, d.getDestinationUID(), pr.getBrokerAddress() };
            String emsg = rb.getKString(rb.E_ADMIN_DELETE_REMOTE_MSG, args);
            throw new BrokerException(emsg, Status.NOT_ALLOWED);
        }

        Destination.RemoveMessageReturnInfo ret = 
            d.removeMessageWithReturnInfo(sysMsgID, RemoveReason.REMOVE_ADMIN);
        if (ret.inreplacing) {
            String emsg = rb.getKString(rb.E_DELETE_MSG_IN_REPLACING, 
                                        msgID, d.getDestinationUID()); 
            throw new BrokerException(emsg, Status.CONFLICT);
        }
        if (ret.indelivery) {
            String emsg = rb.getKString(rb.X_ADMIN_DELETE_MSG_INDELIVERY, 
                                        msgID, d.getDestinationUID()); 
            throw new BrokerException(emsg, Status.CONFLICT);
        }
        logger.log(logger.INFO, rb.getKString(rb.I_ADMIN_DELETED_MESSAGE, 
                                    msgID, d.getDestinationUID()));  
    }
}
