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
 * @(#)ConvertPacket.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.handlers.*;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;

/**
 * this class handles converting data from old to new
 * interest types, etc
 * Its special case code (and probably slow) but the idea
 * is to keep the kludgy code centralized
 */



// LKS - XXX update to handle both protocol and packet version

public class ConvertPacket
{
    private Logger logger = Globals.getLogger();

    Hashtable consumer_to_interest = new Hashtable();
    Hashtable interest_to_consumer = new Hashtable();
    Hashtable consumer_to_deliver = new Hashtable();

    IMQConnection con = null;

    int oldversion = 0;
    int targetVersion = 0;

    public ConvertPacket(IMQConnection con, int oldversion, int targetVersion)
    {
        this.con = con;
        this.oldversion = oldversion; 
        this.targetVersion = targetVersion; 
    }


    public void handleReadPacket(Packet msg) {
        // OK .. convert to new version

        // If we are VERSION2 ... dont do anything
        // EXCEPT convert the properties
        if (oldversion == Packet.VERSION2)
            return;
        
        msg.setVersion(targetVersion); 

        int type = msg.getPacketType();
        switch (type) {
            case PacketType.TEXT_MESSAGE:
            case PacketType.BYTES_MESSAGE:
            case PacketType.MAP_MESSAGE:
            case PacketType.STREAM_MESSAGE:
            case PacketType.OBJECT_MESSAGE:
            case PacketType.MESSAGE:
                handleDataRead(msg);
                break;
            case PacketType.DELETE_CONSUMER:
                removeConsumerRequest(msg);
                break;
            case PacketType.ACKNOWLEDGE:
            case PacketType.REDELIVER:
                handleAcknowledgeRead(msg);
                break;
            case PacketType.DELIVER:
                handleDeliverRead(msg);
            default:
                break;
        }
    }

    public void handleWritePacket(Packet msg) {

        msg.setVersion(oldversion); 
        if (oldversion == Packet.VERSION2) {
           return;
        }

        int type = msg.getPacketType();
        switch (type) {
            case PacketType.TEXT_MESSAGE:
            case PacketType.BYTES_MESSAGE:
            case PacketType.MAP_MESSAGE:
            case PacketType.STREAM_MESSAGE:
            case PacketType.OBJECT_MESSAGE:
            case PacketType.MESSAGE:
                handleDataWrite(msg);
                break;
            case PacketType.ADD_CONSUMER_REPLY:
                handleConsumerResponse(msg);
                break;
            default:
        }
    }

    static final int OLD_ACK_BLOCK_SIZE =  4 + SysMessageID.ID_SIZE;

    private void handleAcknowledgeRead(Packet msg) {
        if (msg.getTransactionID() != 0) {
                TransactionHandler.convertPacketTid(con, msg);
        }
        
        DataInputStream is = new DataInputStream(
                msg.getMessageBodyStream());
        int size = msg.getMessageBodySize();
        int ackcount = size/OLD_ACK_BLOCK_SIZE;
        int[] clientids = new int[ackcount];
        SysMessageID[] sysids = new SysMessageID[ackcount];
        try {
            for (int i = 0; i < ackcount; i ++) {
                clientids[i] = is.readInt();
                sysids[i] = new SysMessageID();
                sysids[i].readID(is); 
            }
        } catch (IOException ex) {
            logger.logStack(Logger.INFO, "bad sysmessageid ", ex);
        }             
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        // reset the body  XXX - LKS

        try {
            for (int i = 0; i < ackcount; i ++) {
                Long newid = (Long)interest_to_consumer.get(
                                  Integer.valueOf(clientids[i]));
                if (newid == null) continue;

                dos.writeLong(newid.longValue());
                sysids[i].writeID(dos); 
            }  
            dos.flush();
            bos.flush();
        } catch (IOException ex) {
            logger.logStack(Logger.WARNING, "Unable to convert "
                 + " old packet ", ex);
        }             
        msg.setMessageBody(bos.toByteArray());

        
    }

    // handle transaction
    private void handleDataRead(Packet msg) {
        if (msg.getTransactionID() != 0) {
                TransactionHandler.convertPacketTid(con, msg);
        }
    }

    // handle interest
    private void handleDataWrite(Packet msg) {
        Long newid = Long.valueOf(msg.getConsumerID());
        Integer oldid = (Integer)consumer_to_interest.get(newid);
        if (oldid == null) { // try deliver
            oldid = (Integer)consumer_to_deliver.get(newid);
            if (oldid != null && msg.getIsLast())
                consumer_to_deliver.remove(newid);  
        }
        if (oldid == null) { // consumer no longer exists
            Globals.getLogger().log(Logger.DEBUG, 
                   "Throwing out packet, could not find "
                   +"old consumer id for new id " 
                   + newid);
            return; // throw out packet
        }
        msg.setConsumerID((long)oldid.intValue());
    }


    /* map old id -> new ID */
    private void handleConsumerResponse(Packet msg) {
        Hashtable props;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, "bad properties", ex);
            return; // no properties
        }
        Integer intr = (Integer)props.remove("JMQOldConsumerID");
        Long newcid = (Long)props.get("JMQConsumerID");

        /* Map them */
        if (newcid != null && intr != null) {
            consumer_to_interest.put(newcid, intr);
            interest_to_consumer.put(intr, newcid);
        }
    }

   /* map old id -> new ID */
    private void handleDeliverRead(Packet msg) {
        Hashtable props;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, "bad propertis", ex);
            return; // no properties
        }

        Integer oldid = (Integer)props.get("JMQConsumerID");

        
        if (oldid != null) {
            ConsumerUID newcid = new ConsumerUID();
            Long longcid = Long.valueOf(newcid.longValue());
            props.put("JMQConsumerID", longcid);
            consumer_to_deliver.put(longcid, oldid);
        }
           
    }

    /* map old id -> new ID */
    private void removeConsumerRequest(Packet msg) {
        Hashtable props;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, "bad propertis ", ex);
            return; // no properties
        }

        Integer oldid = (Integer)props.get("JMQConsumerID");

        if (oldid != null) {
            Long newid = (Long)interest_to_consumer.get(oldid);
            props.put("JMQConsumerID", newid);
            // remove from tables
            consumer_to_interest.remove(newid);
            interest_to_consumer.remove(oldid);
        }
           
    }



}
