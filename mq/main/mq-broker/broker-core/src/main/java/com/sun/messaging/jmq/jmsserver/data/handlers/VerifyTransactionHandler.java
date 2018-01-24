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
 * @(#)VerifyTransactionHandler.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers;

import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import com.sun.messaging.jmq.jmsserver.data.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;


/**
 * Handler class which deals with starting/stoping the delivery of 
 * messages to a specific connection
 */
public class VerifyTransactionHandler extends PacketHandler 
{

    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;

    public VerifyTransactionHandler() {
    }

    /**
     * Method to handle Destination (create or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {

        int status = Status.OK;
        String reason = null;
        
        TransactionList[] tls = DL.getTransactionList(con.getPartitionedStore());
	TransactionList translist = tls[0];

        assert msg.getPacketType() == PacketType.VERIFY_TRANSACTION;

        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID());

        pkt.setPacketType(PacketType.VERIFY_TRANSACTION_REPLY);

        Hashtable hash = new Hashtable();

        Hashtable props = null;
        TransactionUID tuid = null;
        JMQXid xid = null;
        try {
            props = msg.getProperties();
            Long ttid = (Long)props.get("JMQTransactionID");
            if (ttid == null) {
                throw new BrokerException("Bad/Missing transaction id");
            }

            ByteBuffer body = msg.getMessageBodyByteBuffer();
            if (body != null) {
                JMQByteBufferInputStream  bbis = new JMQByteBufferInputStream(body);
                try {
                    xid = JMQXid.read(new DataInputStream(bbis));
                } catch (IOException e) {
                    logger.logStack(Logger.ERROR,
                           BrokerResources.E_INTERNAL_BROKER_ERROR,
                           "Could not decode xid from packet " + 
                           PacketType.getString(msg.getPacketType()), e);
                    BrokerException bex = new BrokerException(e.getMessage(), Status.BAD_REQUEST);
                    bex.initCause(e);
                    throw bex;
                }
            }
            long tid = ttid.longValue();
            if (tid != 0) {
                tuid = new TransactionUID(tid);
            } else if (xid != null) {
                tuid = translist.xidToUID(xid);
                if (tuid == null) {
                    String emsg = Globals.getBrokerResources().getKString(
                                          BrokerResources.W_UNKNOWN_XID, ""+xid,
                                          PacketType.getString(msg.getPacketType()));
                    logger.log(Logger.WARNING, emsg);
                    throw new BrokerException("Unknown XID "+xid, Status.NOT_FOUND);
                }
            } else {
                logger.log(Logger.ERROR,
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "No transaction ID in " +
                        PacketType.getString(msg.getPacketType()));
                throw new BrokerException("No transaction ID" , Status.BAD_REQUEST);
            }
            TransactionState ts = translist.retrieveState(tuid, true);
            if (ts == null) {
                status = Status.GONE;
            } else {
                int realstate = ts.getState();

                if (realstate != TransactionState.PREPARED) {
                    // LKS - XXX
                    // for now return GONE because it seems to work
                    // better for the client - revisit
     
                    throw new BrokerException("Transaction " + tuid + 
                        " is not PREPARED " + ts, Status.GONE);
                }
                Hashtable m = translist.getTransactionMap(tuid, true);

                // write it to the body
                if (m != null) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(m);
                        oos.flush();
                        bos.flush();
                        pkt.setMessageBody(bos.toByteArray());
                        bos.close();
                    } catch (Exception ex) {
                         logger.logStack(Logger.WARNING,
                              BrokerResources.E_INTERNAL_BROKER_ERROR,
                              " sending back broker data", ex);
                    }
                }
            }
        } catch (BrokerException ex) {
            reason = ex.getMessage();
            status = ex.getStatusCode();
            if (status != Status.GONE && status != Status.NOT_FOUND)
            {
                logger.logStack(Logger.INFO,
                      BrokerResources.E_INTERNAL_BROKER_ERROR,
                     "unknown status", ex);
            } else {
                logger.log(Logger.DEBUG,"Transaction " + tuid 
                       + " not found", ex);
            }
        } catch (Throwable ex) {
            logger.logStack(Logger.INFO,
                      BrokerResources.E_INTERNAL_BROKER_ERROR,
                     "exception processing verify transaction ", ex);
            reason = ex.toString();
            status = Status.ERROR;
        }

        if (status == Status.GONE) { // convert to NF
            status = Status.NOT_FOUND;
        }    

        hash.put("JMQStatus", Integer.valueOf(status));
        if (reason != null)
            hash.put("JMQReason", reason);
        if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
            hash.put("JMQReqID", msg.getSysMessageID().toString());

        pkt.setProperties(hash);
        con.sendControlMessage(pkt);
        return true;
    }

}
