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
 * @(#)DefaultHandler.java	1.23 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.util.Hashtable;

import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;


/**
 * Handler class which deals with handling unexpected messages
 */
public class DefaultHandler extends ErrHandler 
{
    private static boolean DEBUG = false;

    public void sendError(IMQConnection con, BrokerException ex, Packet pkt) {
        // XXX REVISIT 3/7/00 racer
        // add code to return ERROR message
        // also log message

        logger.logStack(Logger.ERROR, 
            BrokerResources.E_INTERNAL_BROKER_ERROR, 
            "Uncaught Exception", ex);
        
        // send the reply
        sendError(con, pkt, ex.getMessage(), ex.getStatusCode());
   }

   // if we get an uncaught exception, we want to make sure
   // that the reply (if any) is sent back to the consumer
   // so it doesnt hang because of a broker error or because
   // the client sent bad protocol
   public void sendError(IMQConnection con, Packet msg, String emsg, int status) {
       sendError(con, 
                 msg.getSendAcknowledge(), 
                 msg.getPacketType(),
                 msg.getConsumerID(),
                 emsg, status);
   }

   public void sendError(IMQConnection con, boolean sendack, 
                         int pktype, long consumerID,
                         String emsg, int status) {
       if (sendack) {
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setPacketType(pktype + 1);
            pkt.setConsumerID(consumerID);
            Hashtable hash = new Hashtable();
            hash.put("JMQStatus", Integer.valueOf(status));
            if (emsg != null) {
                hash.put("JMQReason", emsg);
            }
            pkt.setProperties(hash);
            con.sendControlMessage(pkt);
       }
   }


    /**
     * Method to handle messages we don't recognize. If the message
     * has the 'A' bit set then the client is expecting a reply.
     * By convetion reply packet types are the request packet type + 1.
     */
    public boolean handle(IMQConnection con, Packet msg) throws
            BrokerException
    {
	// Check if A bit is set
	if (msg.getSendAcknowledge()) {
            // 'A' bit is set. Send a NOT_IMPLEMENTED reply
            if (DEBUG) {
                logger.log(Logger.DEBUG,
                    "DefaultHandler: replying to unknown packet type: " +
                    msg.getPacketType());
            }
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setPacketType(msg.getPacketType() + 1);
            pkt.setConsumerID(msg.getConsumerID());
            Hashtable hash = new Hashtable();
            hash.put("JMQStatus", Integer.valueOf(Status.NOT_IMPLEMENTED));
            pkt.setProperties(hash);
            con.sendControlMessage(pkt);
	} else {
            // No 'A' bit. Silently ignore
            if (DEBUG) {
                logger.log(Logger.DEBUG,
                    "DefaultHandler: ignoring unknown packet type : " +
                    msg.getPacketType());
            }
        }
        return true;
    }

}
