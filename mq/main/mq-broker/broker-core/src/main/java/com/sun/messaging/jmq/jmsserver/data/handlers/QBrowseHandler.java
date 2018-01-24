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
 * @(#)QBrowseHandler.java	1.54 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.SelectorFilter;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.lists.*;

import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;



/**
 * Handler class which deals with requests for the current queue state
 */
public class QBrowseHandler extends PacketHandler 
{

    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;
    private DestinationList DL = Globals.getDestinationList();

    public QBrowseHandler() {
    }

    public ArrayList getQBrowseList(Destination d, String selectorstr)
        throws BrokerException, SelectorFormatException
    {

        Collection msgs = null;

        if (selectorstr == null) {
            msgs = d.getAll((Filter)null).values();
        } else {
            SelectorFilter f = new SelectorFilter(selectorstr);
            Map m =  d.getAll(f);
            msgs = m.values();
        }

        // sort the messages
        ArrayList sorted = new ArrayList(msgs);
        Collections.sort(sorted, new RefCompare());   

        TransactionList[] tls = DL.getTransactionList(d.getPartitionedStore());
        TransactionList tlist = tls[0];

        // remove any expired messages or messages in open txn
        Iterator itr = sorted.iterator();
        while (itr.hasNext()) {
            PacketReference p = (PacketReference)itr.next();
            if (p.isExpired()) {
                itr.remove();
            }
            if (!p.isDeliveryDue()) {
                itr.remove();
            }
            if (p.getTransactionID() != null) {
                // look up txn
                TransactionState ts = tlist.retrieveState(p.getTransactionID());
                if (ts != null && ts.getState() != TransactionState.COMMITTED) {
                    // open txn, remove
                    itr.remove();
                }
            }
            //check in takeover processing 
            if (p.checkLock(false) == null) {
                itr.remove();
            }
        }

        ArrayList returnmsgs = new ArrayList();

        itr = sorted.iterator();
        while (itr.hasNext()) {
            PacketReference p = (PacketReference)itr.next();
            if (p == null) continue;
            returnmsgs.add(p.getSysMessageID());
        }
        return returnmsgs;
        
    }

    /**
     * Method to handle Destination (create or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {

        int status = Status.OK;
        String reason = null;

        // XXX - REVISIT 2/25/00 racer
        // do we need to create a reply packet each time ?

        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID());

        pkt.setPacketType(PacketType.BROWSE_REPLY);

        Hashtable hash = new Hashtable();

        // pass back a generated ID
        ConsumerUID uid = new ConsumerUID();
        hash.put("JMQConsumerID", Long.valueOf(uid.longValue()));

        Hashtable props = null;
        byte[] body = null;

        String destination = null;

        String selectorstr = null;
        int type = 0;
        try {
            props = msg.getProperties();
            destination = (String )props.get("JMQDestination");
            type = DestType.DEST_TYPE_QUEUE;

            selectorstr = (String )props.get("JMQSelector");
            if (selectorstr != null && selectorstr.trim().length() == 0) {
                selectorstr = null;
            }

            if (DEBUG) {
                logger.log(Logger.DEBUG, "QBrowse request: destination =  " + destination + "  selector = " + selectorstr);
            }
        
            Destination[] ds = DL.getDestination(con.getPartitionedStore(),
                                       destination, DestType.isQueue(type));
            Destination d = ds[0]; //PART

            Boolean getMetrics = (Boolean)props.get("JMQMetrics");

            if (d == null) {
                status = Status.NOT_FOUND;
                reason = "Destination " + destination + " not found";
                logger.log(Logger.WARNING,BrokerResources.W_QUEUE_BROWSE_FAILED_NODEST, 
                     (destination==null? "unknown" : destination));
            } else if (getMetrics != null && getMetrics.equals(Boolean.TRUE)) {
                logger.log(Logger.INFO,"Getting destination metrics on " + d);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(d.getMetrics());
                    oos.close();
                    body = bos.toByteArray();
                } catch (Exception e) {
                    // Programing error. Do not need to localize
                    logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                        this.getClass().getName() +
                        " : Got exception writing metrics to browse reply message:\n" +
                        e );
                }
                hash.put("JMQMetrics", getMetrics);

               
            } else {
                logger.log(Logger.DEBUG, "QueueBrowser created: destination =  " + destination + "  selector = " + selectorstr);

                ArrayList returnmsgs= getQBrowseList(d, selectorstr); //PART

    
            
                if (DEBUG) {
                    logger.log(Logger.DEBUG, "QBrowse request: current queue size is " + returnmsgs.size());
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                for (int i =0; i < returnmsgs.size(); i ++) {
                    SysMessageID id = (SysMessageID) returnmsgs.get(i);
                    if (DEBUG) {
                        logger.log(Logger.DEBUG, "\t[" + i + "] id = " + id);
                    }
                    id.writeID(new DataOutputStream(bos));
                }
                bos.flush();
                body = bos.toByteArray();
                bos.close();            
            }
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Unable to verify destination - no properties",ex);
            reason = ex.getMessage();
            status = Status.ERROR;
        } catch (ClassNotFoundException ex) {
            logger.logStack(Logger.ERROR, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Unable to verify destination -bad class", ex);
            reason = ex.getMessage();
            status = Status.ERROR;
        } catch (SelectorFormatException ex) {
            reason = ex.getMessage();
            status = Status.BAD_REQUEST;
            logger.log(Logger.WARNING,BrokerResources.W_SELECTOR_PARSE, 
                 selectorstr, ex);
        } catch (BrokerException ex) {
            reason = ex.getMessage();
            status = ex.getStatusCode();
            logger.logStack(Logger.WARNING, 
                BrokerResources.W_QUEUE_BROWSE_FAILED, 
                (destination==null? "unknown" : destination), ex);
        } catch (SecurityException ex) {
            reason = ex.getMessage();
            status = Status.FORBIDDEN;
            logger.logStack(Logger.DEBUG, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                " access to destination " + destination + " is forbidden ",ex);
            reason = ex.getMessage();
            // XXX - log message
        }

        hash.put("JMQStatus", Integer.valueOf(status));

        if (reason != null) {
            hash.put("JMQReason", reason);
        }
        if (((IMQBasicConnection)con).getDumpPacket() ||
                 ((IMQBasicConnection)con).getDumpOutPacket())
            hash.put("JMQReqID", msg.getSysMessageID().toString());

        if (status == Status.NOT_FOUND) {
            hash.put("JMQCanCreate", Boolean.valueOf(
                DL.canAutoCreate(DestType.isQueue(type))));
        }

        pkt.setProperties(hash);
        if (body != null)
            pkt.setMessageBody(body);
        con.sendControlMessage(pkt);
        return true;
    }

}
