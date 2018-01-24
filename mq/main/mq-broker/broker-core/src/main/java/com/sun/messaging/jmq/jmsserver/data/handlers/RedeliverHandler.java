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
 * @(#)RedeliverHandler.java	1.47 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;



/**
 * Handler class which deals with requests delivering messages
 */
public class RedeliverHandler extends PacketHandler 
{

    private static boolean DEBUG_CLUSTER_TXN =
                  Globals.getConfig().getBooleanProperty(
                                      Globals.IMQ + ".cluster.debug.txn");

    // An Ack block is a 4 byte interest ID and a SysMessageID
    static final int REDELIVER_BLOCK_SIZE =  8 + SysMessageID.ID_SIZE;

    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;
    private DestinationList DL = Globals.getDestinationList();

    public RedeliverHandler() {
    }

    /**
     * Method to handle DELIVER  messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
   {
        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.INFO, "Unable to retrieve "+
                " properties from redeliver message " + msg, ex);
            props = new Hashtable();
        }

        boolean redeliver = false;
        TransactionUID tid = null;

        if (props != null) {
            Boolean bool = (Boolean)props.get("JMQSetRedelivered");
            if (bool != null)  redeliver = bool.booleanValue();

            Object txnid = props.get("JMQTransactionID");
            if (txnid != null) { 
                if (txnid instanceof Integer) {
                    tid = new TransactionUID(((Integer)txnid).intValue()); 
                } else  {
                    tid = new TransactionUID(((Long)txnid).longValue()); 
                }
            }
            if (tid == null) { //for client < 4.1
                long id = msg.getTransactionID(); 
                if (id != 0) tid = new TransactionUID(id);
            }
        } 

        int size = msg.getMessageBodySize();
        int ackcount = size/REDELIVER_BLOCK_SIZE;
        int mod = size%REDELIVER_BLOCK_SIZE;


        if (ackcount == 0 ) {
            return true;
        }
        if (mod != 0) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,"Invalid Redeliver Message Size: " + size +
		". Not multiple of " + REDELIVER_BLOCK_SIZE));
        }

        if (DEBUG) {
            logger.log(Logger.DEBUG,"RedeliverMessage: processing message {0} {1}",
                     msg.toString(), 
                     con.getConnectionUID().toString());
        }
        DataInputStream is = new DataInputStream(
		msg.getMessageBodyStream());

        ConsumerUID ids[] = new ConsumerUID[ackcount];
        SysMessageID sysids[] = new SysMessageID[ackcount];
        try {
            for (int i = 0; i < ackcount; i ++) {
                ids[i] = new ConsumerUID(is.readLong());
                sysids[i] = new SysMessageID();
                sysids[i].readID(is); 
            }


            redeliver(ids, sysids, con, tid, redeliver);
        } catch (Exception ex) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,"Invalid Redeliver Packet", ex), ex);
        }
        return true;
    }


    public void redeliver(ConsumerUID ids[], SysMessageID sysids[], 
               IMQConnection con, TransactionUID tid, boolean redeliver)
        throws BrokerException, IOException
    {
            SysMessageID sysid = null;
            Set sessions = new HashSet(); //really should only have one
                             // but client to broker protocol doesnt specify
            HashMap cToM = new HashMap();

            HashMap noConsumerMap = new HashMap();
            HashMap storedIDToConsumerUIDMap = new HashMap();

            int position = 0;
            for (int i = 0; i < ids.length; i ++ ) {
                ConsumerUID id = ids[i];
                id.setConnectionUID(con.getConnectionUID());
                sysid = sysids[i];

                PacketReference ref = DL.get(null, sysid, false);

  
                if (ref == null || ref.isInvalid()) {
                    continue;
                }
                Session s= Session.getSession(id);
                Consumer c = null;
                if (s != null) {
                    if (!sessions.contains(s)) {
                        s.pause("redeliver");
                        sessions.add(s);
                    }
                    c = (Consumer)s.getConsumerOnSession(id);
                }

                if (c == null) {
                    //ok, make sure the consumer has really gone away
                    //if not, something is really wrong
                    // otherwise ...
                    // consumer has gone away but session is still
                    // valid -> 
                    c = Consumer.getConsumer(id);
                    
                    if (c != null) {
                        logger.log(Logger.WARNING,"Internal Error " +
                           " consumer with id of " + id + " is unavailable "
                           + " on session " + s + "[conuid,sess conuid] ="
                           + "[" + con.getConnectionUID().longValue() +
                             "," + (s == null ? 0 : s.getConnectionUID().longValue())
                           + "] consumer session is : " +c.getSessionUID()) ;
                        continue;
                    } else {
                        if (s != null && s.isClientAck(id) && !s.isTransacted() &&
                            redeliver && tid == null) {

                            ConsumerUID storedID = s.getStoredIDForDetatchedConsumer(id);
                            if (storedID != null && !storedID.equals(id)) {
                                storedIDToConsumerUIDMap.put(id, storedID);
                                SortedSet refset = (SortedSet)noConsumerMap.get(id);
                                if (refset == null) {
                                    refset = new TreeSet(new RefCompare());
                                    noConsumerMap.put(id, refset);
                                }
                                ref.removeInDelivery(storedID);
                                refset.add(ref);
                            }
                        }
                        // the consumer for this message has been 
                        // closed before the redeliver was requested
                        // (this means the message has not been acked and
                        // the session is open)
                        // we dont need to deliver this message, ignore it
                        logger.log(Logger.DEBUG,
                           " consumer with id of " + id + " is unavailable "
                           + " on session " + s + "[conuid,sess conuid] ="
                           + "[" + con.getConnectionUID().longValue() +
                             "," + (s == null ? 0 : s.getConnectionUID().longValue())
                           + "] it has been closed") ;
                        continue;
                    }
                }

                //for client < 4.1, need check 'redeliver' as well 
                if (redeliver && (tid != null || s.isTransacted())) {
                    if (tid == null) {
                        tid = s.getCurrentTransactionID();
                    }
                    TransactionList[] tls = Globals.getDestinationList().
                                      getTransactionList(con.getPartitionedStore());
                    TransactionList translist = tls[0];
                    if (translist != null) {
                        if (checkRemovedConsumedMessage(ref, id, tid, translist, true)) {
                            if (DEBUG_CLUSTER_TXN) {
                                logger.log(logger.INFO,
                                "Ignore redeliver request for ["+sysid+ ":"+id+
                                "], removed with transaction (rerouted)"+tid);
                            }
                            continue;
                        }
                        if (checkRemovedConsumedMessage(ref, id, tid, translist, false)) {
                            if (DEBUG_CLUSTER_TXN) {
                                logger.log(logger.INFO,
                                "Ignore redeliver request for ["+sysid+ ":"+id+
                                "], removed with transaction "+tid);
                            }
                            continue;
                        }
                    }
                }
                      
                Set set = (Set)cToM.get(c);
                if (set == null) {
                    set = new LinkedHashSet();
                    cToM.put(c, set);
                }
                if (!set.contains(ref)) {
                    ref.removeInDelivery((c.getStoredConsumerUID() == null ?
                                              c.getConsumerUID():
                                              c.getStoredConsumerUID()));
                    set.add(ref);
                } else if (DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, 
                    "Ignore duplicated redeliver request ["+sysid+ ":"+id+"]" );
                }
                if (redeliver) {
                    ref.consumed(c.getStoredConsumerUID(),
                        s.isDupsOK(c.getConsumerUID()), false);
                } else {
                    ref.removeDelivered(c.getStoredConsumerUID(), false);
                }
            }
            Iterator itr = cToM.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry)itr.next();
                Consumer c = (Consumer)entry.getKey();
                Set msgs = (Set)entry.getValue();
                c.pause("start redeliver");
                c.routeMessages(msgs, true);
                c.resume("end redeliver");
            }
            cToM.clear(); // help gc

            if (noConsumerMap.size() > 0) {
                try {
                    logger.log(logger.DEBUG, 
                    "REDELIVER unacked for closed consumers: "+noConsumerMap); 

                    TransactionHandler.redeliverUnackedNoConsumer(
                        noConsumerMap, storedIDToConsumerUIDMap,
                        redeliver, null, null); 

                } catch(Exception e) {
                    logger.logStack(logger.WARNING, 
                    "Exception in redelivering unacked messages for closed consumers", e); 
                }
            }
                
            itr = sessions.iterator();
            while (itr.hasNext()) {
                Session s = (Session)itr.next();
                s.resume("redeliver");
            }
    }

    private boolean checkRemovedConsumedMessage(
        PacketReference ref, ConsumerUID id, 
        TransactionUID tid, TransactionList translist, boolean rerouted) 
        throws BrokerException {

        SysMessageID sysid = ref.getSysMessageID();

        HashMap cmap = translist.retrieveRemovedConsumedMessages(tid, rerouted);
        if (cmap != null && cmap.size() > 0) {
            List interests = (List)cmap.get(sysid);
            if (interests == null || interests.size() == 0) {
                return false;
            }
            for (int j = 0; j < interests.size(); j++) {
                ConsumerUID intid = (ConsumerUID)interests.get(j);
                if (intid.equals(id)) {
                    TransactionState ts = translist.retrieveState(tid);
                    if (ts != null && ts.getState() == TransactionState.FAILED) {
                        if (!rerouted) {
                            TransactionHandler.releaseRemoteForActiveConsumer(
                                               ref, id, tid, translist);
                         }
                         return true;
                    }
                }
            }
        }
        return false;
    }
}
