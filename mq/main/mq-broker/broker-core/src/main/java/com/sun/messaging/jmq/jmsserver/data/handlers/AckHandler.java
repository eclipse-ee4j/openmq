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
 * @(#)AckHandler.java	1.89 10/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers;

import java.io.*;
import java.util.*;

import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException;
import com.sun.messaging.jmq.jmsserver.util.UnknownTransactionException;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.FaultInjection;



/**
 * Handler class which deals with recording message acknowldegements
 */
public class AckHandler extends PacketHandler 
{
    // An Ack block is a Long ConsumerUID and a SysMessageID
    static final int ACK_BLOCK_SIZE =  8 + SysMessageID.ID_SIZE;

    private int ackProcessCnt = 0; // used for fault injection
    private FaultInjection fi = null;

    private final Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;

    public static final int ACKNOWLEDGE_REQUEST=0;
    public static final int UNDELIVERABLE_REQUEST=1;
    public static final int DEAD_REQUEST=2;

    public static final int DEAD_REASON_UNDELIVERABLE = 0;
    public static final int DEAD_REASON_EXPIRED = 1;

    static {
	if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
	}
    }

    public static void checkRequestType(int ackType)
        throws BrokerException
    {
        if (ackType > DEAD_REQUEST || ackType < ACKNOWLEDGE_REQUEST) {
            // L10N .. localize internal error
            throw new BrokerException("Internal Error: unknown ackType "
                   + ackType);
        }
    }

    public AckHandler() {

        fi = FaultInjection.getInjection();
    }

    /**
     * Method to handle Acknowledgement messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException {

        int size = msg.getMessageBodySize();
        int ackcount = size/ACK_BLOCK_SIZE;
        int mod = size%ACK_BLOCK_SIZE;
        int status = Status.OK;
        String reason = null;

        if (DEBUG) {
            logger.log(Logger.INFO, "AckHandler: processing packet "+ 
                msg.toString()+", on connection "+con);
        }

        PartitionedStore pstore = con.getPartitionedStore();
        TransactionList[] tls = DL.getTransactionList(pstore);
        TransactionList translist = tls[0];

        if (!con.isAdminConnection() && fi.FAULT_INJECTION) {
           ackProcessCnt ++; // for fault injection
        } else {
           ackProcessCnt = 0;
        }

        if (ackcount == 0 ) {
            logger.log(Logger.ERROR,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Internal Error: Empty Ack Message "+
                msg.getSysMessageID().toString());
            reason = "Empty ack message";
            status = Status.ERROR;
        }
        if (mod != 0) {
            logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                     "Internal Error: Invalid Ack Message Size "
                     + String.valueOf(size) +  " for message " +
                     msg.getSysMessageID().toString());
            reason = "corrupted ack message";
            status = Status.ERROR;
        }

        TransactionUID tid = null;
        if (msg.getTransactionID() != 0) { // HANDLE TRANSACTION
            try {
                tid = new TransactionUID(msg.getTransactionID());
            } catch (Exception ex) {
               logger.logStack(Logger.ERROR, 
                   BrokerResources.E_INTERNAL_BROKER_ERROR,
                   "Internal Error: can not create transactionID for "+msg, ex);
               status = Status.ERROR;
            }
        }

        ArrayList<PacketReference> cleanList = new ArrayList<PacketReference>();
        try { //cleanList

        boolean markDead = false;
        Hashtable props = null;
        Throwable deadthr = null;
        String deadcmt = null;
        int deadrs = DEAD_REASON_UNDELIVERABLE;
        int deliverCnt = 0;
        boolean deliverCntUpdateOnly = false;
        int ackType = ACKNOWLEDGE_REQUEST;
        boolean JMQValidate = false;
        try {
            props = msg.getProperties();
            Integer iackType = (props == null ? null 
                  :(Integer)props.get("JMQAckType"));
            ackType = (iackType == null ? ACKNOWLEDGE_REQUEST
                 : iackType.intValue());

            Boolean validateFlag = (props == null ? null 
                  :(Boolean)props.get("JMQValidate"));

            JMQValidate = (validateFlag == null ? false
                 : validateFlag.booleanValue());

            checkRequestType(ackType);
            if (ackType == DEAD_REQUEST ) {
                deadthr = (Throwable)props.get("JMQException");
                deadcmt = (String)props.get("JMQComment");
                Integer val = (Integer)props.get("JMQDeadReason");
                if (val != null) {
                    deadrs = val.intValue();
                }
            } 
            if (props != null) {
                if (ackType == DEAD_REQUEST ||
                    ackType == UNDELIVERABLE_REQUEST || tid != null) {
                    //Client runtime retry count
                    Integer val = (Integer)props.get("JMSXDeliveryCount");
                    deliverCnt = (val == null ? -1 : val.intValue());
                    if (tid == null) {
                        if (deliverCnt >= 0) {
                            deliverCnt += 1;
                        } 
                    }
                }   
                if (ackType == UNDELIVERABLE_REQUEST) {
                    Boolean val = (Boolean)props.get("JMSXDeliveryCountUpdateOnly");
                    deliverCntUpdateOnly = (val == null ? false : val.booleanValue());
                }
            }
            
        } catch (Exception ex) {
            // assume not dead
            logger.logStack(Logger.INFO, "Internal Error: bad protocol", ex);
            ackType = ACKNOWLEDGE_REQUEST;
        }

       
        // OK .. handle Fault Injection
        if (!con.isAdminConnection() && fi.FAULT_INJECTION) {
            Map m = new HashMap();
            if (props != null)
                m.putAll(props);
            m.put("mqAckCount", Integer.valueOf(ackProcessCnt));
            m.put("mqIsTransacted", Boolean.valueOf(tid != null));
            fi.checkFaultAndExit(FaultInjection.FAULT_ACK_MSG_1,
                 m, 2, false);
        }
        boolean remoteStatus = false;
        StringBuffer remoteConsumerUIDs = null; 
        SysMessageID ids[] = null;
        ConsumerUID cids[] = null;
        try {
            if (status == Status.OK) {
                DataInputStream is = new DataInputStream(
		            msg.getMessageBodyStream());

                // pull out the messages into two lists
                ids = new SysMessageID[ackcount];
                cids = new ConsumerUID[ackcount];
                for (int i = 0; i < ackcount; i ++) {
                    long newid = is.readLong();
                    cids[i] = new ConsumerUID(newid);
                    cids[i].setConnectionUID(con.getConnectionUID());
                    ids[i] =  new SysMessageID();
                    ids[i].readID(is);
                }
                if (JMQValidate) {
                    if (ackType == DEAD_REQUEST || 
                           ackType == UNDELIVERABLE_REQUEST) {
                        status = Status.BAD_REQUEST;
                        reason = "Can not use JMQValidate with ackType of "
                                  + ackType;
                    } else if (tid == null) {
                        status = Status.BAD_REQUEST;
                        reason = "Can not use JMQValidate with no tid ";
                    } else if (!validateMessages(translist, tid, ids, cids)) {
                        status = Status.NOT_FOUND;
                        reason = "Acknowledgement not processed";
                    }
                } else if (ackType == DEAD_REQUEST) {
                     handleDeadMsgs(con, ids, cids, deadrs,
                                     deadthr, deadcmt, deliverCnt, cleanList);
                } else if (ackType == UNDELIVERABLE_REQUEST) {
                     handleUndeliverableMsgs(con, ids, cids, cleanList, 
                                             deliverCnt, deliverCntUpdateOnly);
                } else {
                    if (tid != null) {
                        handleTransaction(translist, con, tid, ids, cids, deliverCnt);
                     } else  {
                        handleAcks(con, ids, cids, msg.getSendAcknowledge(), cleanList);
                     }
                }
           } 

        } catch (Throwable thr) {
            status = Status.ERROR;
            if (thr instanceof BrokerException) {
                status = ((BrokerException)thr).getStatusCode();
                remoteStatus = ((BrokerException)thr).isRemote();
                if (remoteStatus && ids != null && cids != null) {
                    remoteConsumerUIDs = new StringBuffer();
                    remoteConsumerUIDs.append(((BrokerException)thr).getRemoteConsumerUIDs());
                    remoteConsumerUIDs.append(" ");
                    String cidstr = null;
                    ArrayList remoteConsumerUIDa = new ArrayList();
                    for (int i = 0; i < ids.length; i++) {
                        PacketReference ref = DL.get(pstore, ids[i]);
                        Consumer c = Consumer.getConsumer(cids[i]); 
                        if (c == null) continue;
                        ConsumerUID sid = c.getStoredConsumerUID();
                        if (sid == null || sid.equals(cids[i])) {
                            continue;
                        }
                        BrokerAddress ba = (ref == null ? null: ref.getBrokerAddress());
                        BrokerAddress rba = (BrokerAddress)((BrokerException)thr).getRemoteBrokerAddress();
                        if (ref != null && 
                            ba != null && rba != null && ba.equals(rba)) {
                            cidstr = String.valueOf(c.getConsumerUID().longValue());
                            if (!remoteConsumerUIDa.contains(cidstr)) {
                                remoteConsumerUIDa.add(cidstr);
                                remoteConsumerUIDs.append(cidstr);
                                remoteConsumerUIDs.append(" ");
                            }
                        }
                    }
                }
            }
            reason = thr.getMessage();
            if (status == Status.ERROR) { // something went wrong
                logger.logStack(Logger.ERROR,  BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "-------------------------------------------" +
                    "Internal Error: Invalid Acknowledge Packet processing\n" +
                    " " + 
                    (msg.getSendAcknowledge() ? " notifying client\n"
                          : " can not notify the client" )
                    + com.sun.messaging.jmq.io.PacketUtil.dumpPacket(msg)
                    + "--------------------------------------------",
                    thr);
            }
        }

        // OK .. handle Fault Injection
        if (!con.isAdminConnection() && fi.FAULT_INJECTION) {
            Map m = new HashMap();
            if (props != null)
                m.putAll(props);
            m.put("mqAckCount", Integer.valueOf(ackProcessCnt));
            m.put("mqIsTransacted", Boolean.valueOf(tid != null));
            fi.checkFaultAndExit(FaultInjection.FAULT_ACK_MSG_2,
                 m, 2, false);
        }

          // send the reply (if necessary)
        if (msg.getSendAcknowledge()) {
 
             Packet pkt = new Packet(con.useDirectBuffers());
             pkt.setPacketType(PacketType.ACKNOWLEDGE_REPLY);
             pkt.setConsumerID(msg.getConsumerID());
             Hashtable hash = new Hashtable();
             hash.put("JMQStatus", Integer.valueOf(status));
             if (reason != null)
                 hash.put("JMQReason", reason);
             if (remoteStatus) {
                 hash.put("JMQRemote", Boolean.valueOf(true));
                 if (remoteConsumerUIDs != null) { 
                 hash.put("JMQRemoteConsumerIDs", remoteConsumerUIDs.toString());
                 }
             }  
             if (((IMQBasicConnection)con).getDumpPacket() || ((IMQBasicConnection)con).getDumpOutPacket())
                 hash.put("JMQReqID", msg.getSysMessageID().toString());
             pkt.setProperties(hash);
             con.sendControlMessage(pkt);

        }

        // OK .. handle Fault Injection
        if (!con.isAdminConnection() && fi.FAULT_INJECTION) {
            Map m = new HashMap();
            if (props != null)
                m.putAll(props);
            m.put("mqAckCount", Integer.valueOf(ackProcessCnt));
            m.put("mqIsTransacted", Boolean.valueOf(tid != null));
            fi.checkFaultAndExit(FaultInjection.FAULT_ACK_MSG_3,
                 m, 2, false);
        }

        } finally {
            // we dont need to clear the memory up until after we reply
            cleanUp(cleanList);
        }
        return true;
    }

    public void cleanUp(List cleanList) {
        if (cleanList == null || cleanList.isEmpty()) {
            return; 
        }
        try {
            Iterator itr = cleanList.iterator();
            while (itr.hasNext()) {
                PacketReference ref = (PacketReference)itr.next();
                if (ref == null) {
                    continue;
                }
                try {

                Destination d= ref.getDestination();
                try {
                    if (ref.isDead()) {
                        d.removeDeadMessage(ref);
                    } else {
                        d.removeMessage(ref.getSysMessageID(),
                           RemoveReason.ACKNOWLEDGED);
                    }
                } catch (Exception ex) {
                    Object[] eparam = {""+ref.toString(),
                                       (d == null ? "null":d.getUniqueName()), ex.getMessage()};
                    String emsg = Globals.getBrokerResources().getKString(
                                      BrokerResources.E_CLEANUP_MSG_AFTER_ACK, eparam);
                    if (DEBUG) {
                    logger.logStack(Logger.INFO, emsg, ex);
                    } else {
                    logger.log(Logger.INFO, emsg);
                    }
                }

                } finally {
                    ref.postAcknowledgedRemoval();
                    itr.remove();
                }
            }
        } finally {
            if (!cleanList.isEmpty()) {
                Iterator<PacketReference> itr = cleanList.iterator();
                while (itr.hasNext()) {
                    PacketReference ref = itr.next();
                    if (ref != null) {
                        ref.postAcknowledgedRemoval();
                    }
                }
            }
        }
    }

    public void handleAcks(IMQConnection con, 
        SysMessageID[] ids, ConsumerUID[] cids, boolean ackack, List cleanList) 
        throws BrokerException, IOException {

        // XXX - we know that everything on this message
        // we could eliminate on of the lookups
        for (int i = 0; i < ids.length; i ++) {
            if (DEBUG) {
            logger.log(logger.INFO, "handleAcks["+i+", "+ids.length+"]:sysid="+
                                     ids[i]+", cid="+cids[i]+", on connection "+con);
            }
            Session s = Session.getSession(cids[i]);
            if (s == null) { // consumer does not have session
                Consumer c = Consumer.getConsumer(cids[i]);
                if (c == null) {
                    if (!con.isValid() || con.isBeingDestroyed()) {
                        if (DEBUG) {
                        logger.log(logger.INFO, "Received ack for consumer " 
                                   + cids[i] +  " on closing connection " + con);
                        }
                        continue;
                    }
                    if (BrokerStateHandler.isShutdownStarted()) {
                        throw new BrokerException(br.I_ACK_FAILED_BROKER_SHUTDOWN);
                    }
                    throw new BrokerException(br.getKString(
                        br.I_ACK_FAILED_NO_CONSUMER, cids[i]), Status.NOT_FOUND);

                } else if (c.getConsumerUID().getBrokerAddress() !=
                           Globals.getClusterBroadcast().getMyAddress()) {
                    // remote consumer
                    PacketReference ref = DL.get(null, ids[i]);
                    if (ref == null) {
                        BrokerException bex = new BrokerException(br.getKString(
                            br.I_ACK_FAILED_MESSAGE_REF_GONE, ids[i])+"["+cids[i]+"]",
                            Status.GONE);
                        bex.setRemoteConsumerUIDs(String.valueOf(cids[i].longValue()));
                        bex.setRemote(true);
                        throw bex;
                    }
                    ConsumerUID cuid = c.getConsumerUID();
                    // right now we dont store messages for remote
                    // brokers so the sync flag is unnecessary
                    // but who knows if that will change
                    if (ref.acknowledged(cuid, c.getStoredConsumerUID(), 
                                         !cuid.isDupsOK(), true, ackack)) {
                        cleanList.add(ref);
                    }
                } else {
                    String emsg = br.getKString(br.I_LOCAL_CONSUMER_ACK_FAILED_NO_SESSION,
                                                "["+ids[i]+", "+cids[i]+"]", cids[i]);
                    logger.log(Logger.WARNING, emsg);
                    throw new BrokerException(emsg);
                }
            } else { // we have a session

               if (fi.FAULT_INJECTION) {
                   PacketReference ref = DL.get(null, ids[i]);
                   if (ref != null && !ref.getDestination().isAdmin() &&
                       !ref.getDestination().isInternal()) {
                       if (fi.checkFault(fi.FAULT_ACK_MSG_1_5, null)) {
                           fi.unsetFault(fi.FAULT_ACK_MSG_1_5);
                           BrokerException bex = new BrokerException(
                               "FAULT:"+fi.FAULT_ACK_MSG_1_5, Status.GONE);
                           bex.setRemoteConsumerUIDs(String.valueOf(cids[i].longValue()));
                           bex.setRemote(true);
                           throw bex;
                       }
                   }
               }
               PacketReference ref = (PacketReference)s.ackMessage(cids[i], ids[i], ackack);
               try {
                   s.postAckMessage(cids[i], ids[i], ackack);
               } finally {
                   if (ref != null) {
                       cleanList.add(ref);
                   }
               }
            } 
        }
    }

    public void handleTransaction(TransactionList translist, IMQConnection con,
                                  TransactionUID tid, SysMessageID[] ids,
                                  ConsumerUID[] cids, int deliverCnt) 
                                  throws BrokerException {

        for (int i = 0; i < ids.length; i++) {
            Consumer consumer = null;
            try {
                // lookup the session by consumerUID

                Session s = Session.getSession(cids[i]);

                // look up the consumer by consumerUID
                consumer = Consumer.getConsumer(cids[i]);

                if (consumer == null) {
                    throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_ACK_FAILED_NO_CONSUMER, cids[i])+"[TID="+tid+"]",
                        Status.NOT_FOUND);
                }
                // try and find the stored consumerUID
                ConsumerUID sid = consumer.getStoredConsumerUID();

                // if we still dont have a session, attempt to find one
                if (s == null) {
                    SessionUID suid = consumer.getSessionUID();
                    s = Session.getSession(suid);
                }
                if (s == null)  {
                   if (BrokerStateHandler.isShutdownStarted()) {
                       throw new BrokerException(
                           BrokerResources.I_ACK_FAILED_BROKER_SHUTDOWN);
                   }
                   throw new BrokerException(
                       br.getKString(br.I_ACK_FAILED_NO_SESSION,
                       "["+ids[i]+", "+cids[i]+"]TID="+tid)); 
                }
                if (DEBUG) {
                logger.log(logger.INFO, "handleTransaction.addAck["+i+", "+ids.length+"]:tid="+tid+
                           ", sysid="+ids[i]+", cid="+cids[i]+", sid="+sid+" on connection "+con);
                }
                boolean isxa = translist.addAcknowledgement(tid, ids[i], cids[i], sid);
                BrokerAddress addr = (BrokerAddress)s.ackInTransaction(
                                         cids[i], ids[i], tid, isxa, deliverCnt);
                if (addr != null && addr != Globals.getMyAddress()) {
                    translist.setAckBrokerAddress(tid, ids[i], cids[i], addr);
                }
                if (fi.FAULT_INJECTION) {
                    if (fi.checkFault(fi.FAULT_TXN_ACK_1_5, null)) {
                        fi.unsetFault(fi.FAULT_TXN_ACK_1_5);
                        TransactionAckExistException tae = new TransactionAckExistException("FAULT:"+fi.FAULT_TXN_ACK_1_5, Status.GONE);
                        tae.setRemoteConsumerUIDs(String.valueOf(cids[i].longValue()));
                        tae.setRemote(true);
                        consumer.recreationRequested();
                        throw tae;
                    }
                }
            } catch (Exception ex) {
                String emsg = "["+ids[i]+", "+cids[i] + "]TUID="+tid;
                if ((ex instanceof BrokerException) && 
                    ((BrokerException)ex).getStatusCode() !=  Status.ERROR) {
                    logger.log(Logger.WARNING , Globals.getBrokerResources().getKString(
                    BrokerResources.E_TRAN_ACK_PROCESSING_FAILED, emsg, ex.getMessage()), ex);
                } else {
                    logger.log(Logger.ERROR , Globals.getBrokerResources().getKString(
                    BrokerResources.E_TRAN_ACK_PROCESSING_FAILED, emsg, ex.getMessage()), ex);
                }
                int state = -1;
                JMQXid xid = null;
                try {
                    TransactionState ts = translist.retrieveState(tid);
                    if (ts != null) {
                        state = ts.getState();
                        xid = ts.getXid();
                    }
                    translist.updateState(tid, TransactionState.FAILED, true);
                } catch (Exception e) {
                    if (!(e instanceof UnknownTransactionException)) {
                    String args[] = { TransactionState.toString(state),
                                      TransactionState.toString(TransactionState.FAILED),
                                      tid.toString(),  (xid == null ? "null": xid.toString()) };
                    logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                                      BrokerResources.W_UPDATE_TRAN_STATE_FAIL, args));
                    }
                }
                if (ex instanceof TransactionAckExistException) {
                    PacketReference ref = DL.get(null, ids[i]);
                    if (ref != null && (ref.isOverrided() || ref.isOverriding())) {
                        ((BrokerException)ex).overrideStatusCode(Status.GONE);
                        ((BrokerException)ex).setRemoteConsumerUIDs(
                                 String.valueOf(cids[i].longValue()));
                        ((BrokerException)ex).setRemote(true);
                        consumer.recreationRequested();
                    }
                }
                if (ex instanceof BrokerException)  throw (BrokerException)ex;

                throw new BrokerException("Internal Error: Unable to " +
                    " complete processing acknowledgements in a tranaction: " 
                     +ex, ex);
            }
        }
    }


    public boolean validateMessages(TransactionList translist, 
        TransactionUID tid, SysMessageID[] ids, ConsumerUID[] cids)
        throws BrokerException {

        // LKS - XXX need to revisit this
        // I'm not 100% sure if we still need this or not (since its really
        // targeted at supporting the NEVER rollback option
        // putting in a mimimal support for the feature

        // OK, get a status on the transaction

        boolean openTransaction = false;
        try {
            translist.getTransactionMap(tid, false);
            openTransaction = true;
        } catch (BrokerException ex) {
        }

       
        // if transaction exists we need to check its information
        if (openTransaction) {
            for (int i=0; i < ids.length; i ++) {
                Consumer c = Consumer.getConsumer(cids[i]);
                if (c == null) { // unknown consumer
                    throw new BrokerException("Internal Error, " +
                       "unknown consumer " + cids[i], 
                       Status.BAD_REQUEST);
                }
                if (!translist.checkAcknowledgement(tid, ids[i], 
                    c.getConsumerUID())) {
                    return false;
                }
           }
        } else { // check packet reference
            for (int i=0; i < ids.length; i ++) {
                Consumer c = Consumer.getConsumer(cids[i]);
                if (c == null) { // unknown consumer
                    throw new BrokerException("Internal Error, " +
                       "unknown consumer " + cids[i], 
                       Status.BAD_REQUEST);
                }
                PacketReference ref = DL.get(null, ids[i]);
                if (ref == null) {
                    if (DEBUG) {
                    logger.log(Logger.INFO, 
                    "AckHandler.validateMessages(): message reference Could not find "+
                     ids[i]+"["+cids[i]+"]");
                    }
                    continue;
                }
                if (!ref.hasConsumerAcked(c.getStoredConsumerUID())) {
                    return false;
                }
            }
        }
        return true;
    }

    public void handleDeadMsgs(IMQConnection con, 
                               SysMessageID[] ids, ConsumerUID[] cids, 
                               int deadrs, Throwable thr,
                               String comment, int deliverCnt, List cleanList) 
                               throws BrokerException {
        RemoveReason deadReason = RemoveReason.UNDELIVERABLE; 
        if (deadrs == DEAD_REASON_EXPIRED) {
            deadReason = RemoveReason.EXPIRED_BY_CLIENT;
        }
        for (int i=0; i < ids.length; i ++) {
            Session s = Session.getSession(cids[i]);
            if (s == null) { // consumer does not have session
                 // really nothing to do, ignore
                logger.log(Logger.DEBUG,"Dead message for Unknown Consumer/Session"
                        + cids[i]);
                continue;
            }
            if (DEBUG) {
            logger.log(logger.INFO, "handleDead["+i+", "+ids.length+"]:sysid="+
                                    ids[i]+", cid="+cids[i]+", on connection "+con);
            }

            PacketReference ref = (PacketReference)s.handleDead(cids[i], ids[i], deadReason,
                                               thr, comment, deliverCnt);

            // if we return the reference, we could not re-deliver it ...
            // no consumers .. so clean it up
            if (ref != null) {
                cleanList.add(ref);
            }
        }
    }


    public void handleUndeliverableMsgs(IMQConnection con, 
        SysMessageID[] ids, ConsumerUID[] cids, List cleanList, 
        int deliverCnt, boolean deliverCntUpdateOnly) 
        throws BrokerException {

        for (int i=0; i < ids.length; i ++) {
            Session s = Session.getSession(cids[i]);
            if (s == null) { // consumer does not have session
                 // really nothing to do, ignore
                if (DEBUG) {
                logger.log(Logger.INFO, "Undeliverable message for Unknown Consumer/Session"
                        + cids[i]);
                }
            }
            if (DEBUG) {
            logger.log(logger.INFO, "handleUndeliverable["+i+", "+ids.length+"]:sysid="+
                                    ids[i]+", cid="+cids[i]+", on connection "+con);
            }
            PacketReference ref = (s == null ? null : 
                                   (PacketReference)s.handleUndeliverable(
                                    cids[i], ids[i], deliverCnt, deliverCntUpdateOnly));

            // if we return the reference, we could not re-deliver it ...
            // no consumers .. so clean it up
            if (ref != null) {
                cleanList.add(ref);
            }
        }
    }
}
