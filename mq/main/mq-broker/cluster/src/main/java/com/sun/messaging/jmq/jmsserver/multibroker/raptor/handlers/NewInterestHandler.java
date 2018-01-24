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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;

public class NewInterestHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public NewInterestHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "NewInterestHandler");

        if (pkt.getType() == ProtocolGlobals.G_NEW_INTEREST) {
            handleNewInterest(cb, sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_NEW_INTEREST_REPLY) {
            handleNewInterestAck(sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_DURABLE_ATTACH) {
            handleAttachDurable(cb, sender, pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_DURABLE_ATTACH_REPLY) {
            handleAttachDurableReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "MessageDataHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public boolean ignoreNewInterest(ClusterConsumerInfo ci, GPacket pkt) {
		ClusterConsumerInfo cci = ci;
        if (cci == null) {
		   cci = ClusterConsumerInfo.newInstance(pkt, c);
        }
        if (p.getConfigSyncComplete() == false && !cci.isConfigSyncResponse()) {
            // Do not accept normal interest updates before config sync is complete.
            if (DEBUG) {
            logger.log(logger.INFO,  "Ignore "+
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+", not ready yet");
            }
            return true;
        }
        return false;
    }

    public void handleNewInterest(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG) {
            logger.log(logger.INFO, "handleNewInterest from : " + sender);
        }

        ClusterConsumerInfo cci = ClusterConsumerInfo.newInstance(pkt, c);
        if (ignoreNewInterest(cci, pkt)) {
            return;
        }

        int count = cci.getConsumerCount();
        if (DEBUG) {
            logger.log(logger.INFO, "handleNewInterest count : " + count);
        }

        try {
            int i = 0;
            ChangeRecordInfo lastcri = null;
            Iterator itr = cci.getConsumers();
            while (itr.hasNext()) {
                i++;
                Consumer cons = null;
                try {
                    cons = (Consumer)itr.next();
                } catch (RuntimeException e) {
                    Throwable ex = e.getCause();
                    if (ex instanceof ConsumerAlreadyAddedException) {
                        logger.log(logger.WARNING, ex.getMessage()+
                        " ("+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+") "+sender);
                        continue;
                    }
                    throw e;
                }

                // Make sure that the destination exists...
                DestinationUID duid = cons.getDestinationUID();

                if (duid.isWildcard()) {
                    //we don't need to do anything here
                    // the logic below autocreates the destination
                    // and we need to do that.
                } else {
                    //autocreate the destination if we need to
                    int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE : DestType.DEST_TYPE_TOPIC);
                    Globals.getDestinationList().
                        getDestination(null, duid.getName(), type, true, true);
                }

                cb.interestCreated(cons);
                if (DEBUG) {
                logger.log(logger.INFO, "Added newInterest("+count+")"+cons+ " from "+sender);
                }
                ChangeRecordInfo cri = cci.getShareccInfo(i); 
                if (cri != null) {
                    if (lastcri == null) {
                        lastcri = cri;
                    } else if (cri.getSeq().longValue() 
                               > lastcri.getSeq().longValue()) {
                        lastcri = cri;
                    }
                }
            }
            if (lastcri != null) {
                cb.setLastReceivedChangeRecord(sender, lastcri);
            }
        }
        catch (Exception e) { 
            logger.logStack(logger.WARNING, e.getMessage()+
            " ("+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+") "+sender+" "+pkt, e);
        }

        if (cci.needReply()) {
            GPacket gp = ClusterConsumerInfo.getReplyGPacket(
                                ProtocolGlobals.G_NEW_INTEREST_REPLY,
                                            ProtocolGlobals.G_SUCCESS);
            try {
                c.unicast(sender, gp);
            }
            catch (IOException e) {}
        }
    }

    private void handleNewInterestAck(BrokerAddress sender, GPacket pkt) {
        logger.log(logger.DEBUG,
            "MessageBus: Received G_NEW_INTEREST_REPLY from {0} : STATUS = {1}",
            sender, ((Integer) pkt.getProp("S")));
    }

    public void handleAttachDurable(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (DEBUG) {
            logger.log(logger.INFO, "handleAttachDurable from : " + sender);
        }

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(pkt, c);
        try {
            String dname = csi.getDurableName();
            String cid = csi.getClientID();
            String ndsubname = csi.getNDSubscriptionName();

            // if we receive a packet which does not support
            // shared subscriptions -> we are dealing with an
            // older (e.g. 3.5) broker .. so we should never
            // have a null durable name
            Boolean allowsNonDurable = csi.allowsNonDurable(); 

            boolean nonDurableOK = (allowsNonDurable == null
                         ? false : allowsNonDurable.booleanValue());

            // check if anything is bogus in the packet
            if (!((cid != null && (nonDurableOK || dname != null)) ||
                  (cid == null && (dname != null || ndsubname != null)))) {
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.E_INTERNAL_BROKER_ERROR,
                              " in handleAttachDurable: "+
                              dname +":"+ cid+", "+ndsubname+", "+nonDurableOK);
                logger.logStack(logger.ERROR, emsg, (new RuntimeException(emsg)));
                return;
            }

            Consumer cons = null;
            try {
                cons = csi.getConsumer();
            } catch (IOException e) {
                Throwable ex = e.getCause();
                if (ex instanceof ConsumerAlreadyAddedException) {
                    logger.log(logger.WARNING, ex.getMessage()+
                    " ("+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+") "+sender);
                    return;
                }
                throw e; 
            }

            Subscription sub = null;

            if (dname == null) { // non-durable shared subscription
                DestinationUID duid = cons.getDestinationUID();
                String selector = cons.getSelectorStr();
                sub = Subscription.findNonDurableSubscription(
                                   cid, duid, selector, ndsubname);
                if (sub == null) {
                    String[] args = { Subscription.getNDSubLongLogString(
                                          cid, duid, selector, ndsubname,
                                          cons.getNoLocal()),
                                      cons.toString(), sender.toString() };
                    logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                        BrokerResources.W_NON_DURA_SUB_NOT_FOUND_ON_ATTACH, args));
                }
            } else {
                sub = Subscription.findDurableSubscription(cid, dname);
                if (sub == null) {
                    logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                    BrokerResources.W_DURA_SUB_NOT_FOUND_ON_ATTACH, 
                    Subscription.getDSubLogString(cid, dname), sender));
                }
            }

            if (DEBUG) {
            logger.log(logger.INFO, "handleAttachDurable: subscription="+
                       sub+Subscription.getDSubLogString(cid, dname)+
                       ", consumer="+cons+" from "+sender);
            }
            if (sub == null) {
                return;
            }

            try {
                sub.attachConsumer(cons);
                cb.interestCreated(cons);
            } catch (Exception ex) {
                if (ex instanceof ConsumerAlreadyAddedException) {
                logger.log(logger.INFO,
                    ex.getMessage()+" ("+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+")"); 
                } else if (ex instanceof BrokerException && 
                    ((BrokerException)ex).getStatusCode() == Status.CONFLICT) {
                logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                    BrokerResources.W_CLUSTER_ATTACH_CONSUMER_FAIL, ex.getMessage()));
                } else {
                logger.log(logger.ERROR, Globals.getBrokerResources().getKString(
                    BrokerResources.W_CLUSTER_ATTACH_CONSUMER_FAIL, ex.getMessage()), ex);
                }
            }

        } catch (Exception e) {
            logger.logStack(logger.WARNING, e.getMessage()+
            " ("+ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+") "+sender+" "+pkt, e);
        }

        if (csi.needReply()) {
            GPacket gp = ClusterSubscriptionInfo.getReplyGPacket(
                                         ProtocolGlobals.G_DURABLE_ATTACH_REPLY,
                                                       ProtocolGlobals.G_SUCCESS);
            try {
                c.unicast(sender, gp);
            }
            catch (IOException e) {}
        }
    }

    private void handleAttachDurableReply(BrokerAddress sender, GPacket pkt) {
        logger.log(logger.DEBUG,
            "MessageBus: Received G_DURABLE_ATTACH_REPLY from {0} : STATUS = {1}",
            sender, ((Integer) pkt.getProp("S")));
    }
}


/*
 * EOF
 */
