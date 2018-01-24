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
 * @(#)CompactDestinationHandler.java	1.9 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.Iterator;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.DestType;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class CompactDestinationHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public CompactDestinationHandler(AdminDataHandler parent) {
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
                "Compacting: " + cmd_props);
        }
        logger.log(Logger.INFO,
              Globals.getBrokerResources().I_COMPACTING,
              cmd_props);

 	String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
        Integer type = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);

        int status = Status.OK;
        String errMsg = null;
	boolean compactAll = false;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {
	try {
	    if (destination != null) {
		// compact one destination
		Destination[] ds = DL.getDestination(null, destination,
					DestType.isQueue(type.intValue()));
                Destination d = ds[0]; //PART
		if (d != null) {
		    if (d.isPaused()) {
			d.compact();
		    } else {
			status = Status.ERROR;
			String msg = rb.getString(rb.E_DESTINATION_NOT_PAUSED);
			errMsg = rb.getString(rb.X_COMPACT_DST_EXCEPTION,
					destination, msg);
			logger.log(Logger.ERROR, errMsg);
		    }
		} else {
		    status = Status.ERROR;
		    String subError = rb.getString(rb.E_NO_SUCH_DESTINATION,
				getDestinationType(type.intValue()),
				destination);
		    errMsg = rb.getString(rb.X_COMPACT_DST_EXCEPTION,
				destination, subError);
		    logger.log(Logger.ERROR, errMsg);
		}
	    } else {
		Iterator[] itrs = DL.getAllDestinations(null);
                Iterator itr = itrs[0];
		boolean docompact = true;
		while (itr.hasNext()) {
		    // make sure all are paused
		    Destination d = (Destination)itr.next();

		    /*
		     * Skip internal, admin, or temp destinations.
		     * Skipping temp destinations may need to be
		     * revisited.
		     */
		    if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
			continue;
		    }

		    if (!d.isPaused()) {
			docompact = false;
			status = Status.ERROR;
			String msg = rb.getString(
					rb.E_SOME_DESTINATIONS_NOT_PAUSED);
			errMsg = rb.getString(rb.X_COMPACT_DSTS_EXCEPTION,
					msg);
			logger.log(Logger.ERROR, errMsg);
		    }
		}

		if (docompact) {
		    itrs = DL.getAllDestinations(null);
                    itr = itrs[0]; //PART
		    while (itr.hasNext()) {
			Destination d = (Destination)itr.next();

			/*
			 * Skip internal, admin, or temp destinations.
			 * Skipping temp destinations may need to be
			 * revisited.
			 */
			if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
			    continue;
			}

			d.compact();
		    }
		}
	    }
        } catch (Exception e) {
            status = Status.ERROR;
	    if (compactAll)  {
                errMsg = rb.getString( rb.X_COMPACT_DSTS_EXCEPTION, e.toString());
	    } else  {
                errMsg = rb.getString( rb.X_COMPACT_DST_EXCEPTION, 
                            destination, e.toString());
	    }
            logger.log(Logger.ERROR, errMsg, e);
         }
         }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.COMPACT_DESTINATION_REPLY,
		status, errMsg);

	parent.sendReply(con, cmd_msg, reply);

	return true;
    }
}
