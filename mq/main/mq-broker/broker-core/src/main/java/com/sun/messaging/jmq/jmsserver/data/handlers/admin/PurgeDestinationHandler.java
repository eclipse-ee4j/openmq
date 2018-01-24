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
 * @(#)PurgeDestinationHandler.java	1.32 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.util.lists.Filter;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.audit.api.MQAuditSession;

public class PurgeDestinationHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();


    private Filter deleteAll = new DeleteAllMessages();

    public PurgeDestinationHandler(AdminDataHandler parent) {
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
                "Purging: " + cmd_props);
        }

        assert cmd_props != null;
	String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
	Integer destType = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);
        assert destination != null;
        assert destType != null;
        int status = Status.OK;
        String errMsg = null;


        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {

        // FOR NOW .. we have just one criteria - ALL
        String criteria_str = Globals.getBrokerResources().getKString(
                    BrokerResources.I_ALL_PURGE_CRITERIA);

//LKS
// dont use filter for now
//        Filter f = deleteAll;

        logger.log(Logger.INFO, BrokerResources.I_PURGING_DESTINATION,
                 destination , criteria_str);

        try {
            // for now .. just delete all
            Destination[] ds = DL.getDestination(null, destination, 
                                   DestType.isQueue(destType.intValue()));
            Destination d = null;
            for (int i = 0; i < ds.length; i++) {
                d = ds[i];
                if (d == null) {
                    continue;
                }
            	// audit logging for purge destination
                if (i == 0) {
            	    Globals.getAuditSession().destinationOperation(
                            con.getUserName(), con.remoteHostString(),
                            MQAuditSession.PURGE_DESTINATION,
                            d.isQueue() ? MQAuditSession.QUEUE:MQAuditSession.TOPIC,
                            d.getDestinationName());
                }
            	d.purgeDestination();
            }
            if (d == null) {
                errMsg = Globals.getBrokerResources().getKString(
                             BrokerResources.E_NO_SUCH_DESTINATION,
                             getDestinationType(destType.intValue()), destination);
                Exception e = new BrokerException(errMsg);
                e.fillInStackTrace();
                status = Status.ERROR;
                logger.log(Logger.WARNING, BrokerResources.W_ADMIN_OPERATION_FAILED, e);
            }
        } catch (BrokerException ex) {
            status = Status.ERROR;
            errMsg = getMessageFromException(ex);
            logger.log(Logger.WARNING, BrokerResources.W_ADMIN_OPERATION_FAILED, ex);
        } catch (OutOfMemoryError err) {
            // throw memory error so it is handled by memory code
            throw err;
        } catch (Throwable ex) {
            status = Status.ERROR;
            errMsg = Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION, ex) ;
            logger.logStack(Logger.WARNING, BrokerResources.W_ADMIN_OPERATION_FAILED,
 ex);
        }
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.PURGE_DESTINATION_REPLY,
		status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
        return true;
    }


    
}
