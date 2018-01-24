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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsservice.Consumer;
import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;
import com.sun.messaging.jmq.jmsservice.JMSService.TransactionAutoRollback;
import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;
import com.sun.messaging.bridge.api.StompSubscriber;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolException;

/**
 * @author amyk 
 */
public class StompTransactedSession extends StompSenderSession {

    private String lastRolledbackTID = null;
    private String tid = null;
    private long transactionId = 0L;

    private Map<String, TransactedSubscriber> subscribers =
        Collections.synchronizedMap(new HashMap<String, TransactedSubscriber>());

    private List<SubscribedMessage> unackqueue = new ArrayList<SubscribedMessage>();

    public StompTransactedSession(StompConnectionImpl stompc) 
    throws Exception {
        super(stompc, true);
    }

    @Override
    public String toString() {
        return "[StompTransactedSession@"+hashCode()+", tid="+
                tid+"["+transactionId+"], lastRB="+lastRolledbackTID+
               ", subs="+subscribers.size()+", unacks="+unackqueue.size()+"]";
    }

    public synchronized String getLastRolledbackStompTransactionId() {
        return lastRolledbackTID;
    }

    @Override
    protected synchronized void closeSubscribers() {
        TransactedSubscriber sub = null;
        for (String subid: subscribers.keySet()) {
             sub = subscribers.get(subid);
             try {
                 sub.close(false);
             } catch (Exception e) {
                 if ((!isClosing() && !sub.isClosing()) || getDEBUG()) {
                     logger.logStack(logger.WARNING, e.getMessage(), e);
                 }
             }
        }
    }

    @Override
    protected synchronized long getTransactionId() {
        return transactionId;
    }

    public synchronized String getStompTransactionId() {
        return tid;
    }

    public synchronized void setStompTransactionId(String tid) {
        this.tid = tid;
        if (tid == null) {
            this.transactionId = 0L;
        }
    }

    public void ack(String subid, String msgid, boolean nack)
    throws Exception {

        checkSession();
        String cmd = (nack ? "NACK ":"ACK ");
        synchronized(this) {

        if (getStompTransactionId() == null) {
            throw new StompProtocolException(
            "Transacted "+cmd+"["+subid+", "+msgid+"] no current transaction");
        }

        TransactedSubscriber sub = subscribers.get(subid);
        if (sub == null) {
            throw new StompProtocolException(
            "Subscription "+subid+" not found to transacted "+cmd+" message "+msgid);
        }

        SysMessageID sysid = SysMessageID.get(msgid);
        SubscribedMessage sm = new SubscribedMessage(subid, sysid);

        int index =  unackqueue.indexOf(sm);
        if (index == -1) {
            throw new StompProtocolException(
            "Message "+msgid+ " for subscription "+subid+" not found in transaction "+tid);
        }
        ArrayList<SubscribedMessage> acks = new ArrayList<SubscribedMessage>();
        if (nack) {
            acks.add(sm);
        } else {
            for (int i = 0; i <= index; i++) {
                sm = unackqueue.get(i);
                if (sm.subid.equals(subid)) {
                    acks.add(sm);
                }
            }
        }
        Iterator<SubscribedMessage> itr = acks.iterator();
        while (itr.hasNext()) {
            sm = itr.next();
            if (!nack) {
                jmsservice.acknowledgeMessage(connectionId, sessionId,
                       sm.consumerId, sm.sysid, transactionId,
                       MessageAckType.ACKNOWLEDGE); 
            } else {
                jmsservice.acknowledgeMessage(connectionId, sessionId,
                       sm.consumerId, sm.sysid, transactionId,
                       MessageAckType.DEAD, 1, "STOMP:NACK", null); 
            }
            unackqueue.remove(sm);
        }
        return;
        }
    }

    /**
     * STOMP protocol 1.0
     */
    public void ack10(String subidPrefix, String msgid) throws Exception {
        throw new StompProtocolException(
        "STOMP 1.0 no subscription id ACK is not supported");
    }

    public StompSubscriber createSubscriber(
        String subid, StompDestination d, String selector,
        String duraname, boolean nolocal, StompOutputHandler out)
        throws Exception {

        checkSession();
        synchronized(this) {

        if (subscribers.get(subid) != null) {
            throw new StompProtocolException(
            "Subscriber "+subid+" already exist in transacted session "+this); 
        }

	String stompdest = stompconn.getProtocolHandler().toStompFrameDestination(d, false);
	Destination dest = ((StompDestinationImpl)d).getDestination();
	JMSServiceReply reply = null;
	try {
            reply = jmsservice.createDestination(connectionId, dest);
	} catch (JMSServiceException jmsse) {
            JMSServiceReply.Status status = jmsse.getJMSServiceReply().getStatus();
            if (status == JMSServiceReply.Status.CONFLICT) {
		if (getDEBUG()) {
                    logger.log(logger.INFO, "Destination "+stompdest+" already exist");
		}
            } else {
		throw jmsse;
            }
	}
        reply = jmsservice.startConnection(connectionId);
	reply = jmsservice.addConsumer(connectionId, sessionId,
                    dest, selector, duraname, (duraname != null),
                    false, false, stompconn.getClientID(), nolocal);
	long consumerId = reply.getJMQConsumerID();

        TransactedSubscriber sub = new TransactedSubscriber(subid,
                       consumerId, duraname, stompdest, out);
        subscribers.put(subid, sub);
        
	return sub;
        }
    }
        

    public void begin(String stomptid) throws Exception {
        checkSession();
        synchronized(this) {

        if (getDEBUG()) {
            logger.log(logger.INFO, "Begin transaction "+stomptid+ " in ["+this+"]");
        }
        if (tid != null) {
            throw new StompProtocolException(
            "Transaction session has current transaction "+tid);
        }
        JMSServiceReply reply = jmsservice.startTransaction(
                        connectionId, sessionId, null, 0,
                        TransactionAutoRollback.UNSPECIFIED, 0L);
        transactionId = reply.getJMQTransactionID();
        setStompTransactionId(stomptid);

        }
    }

    public void commit() throws Exception {
        checkSession();
        synchronized(this) {

        if (getStompTransactionId() == null) {
            throw new StompProtocolException(
            "Commit no current transaction in session "+this);
        }

        if (getDEBUG()) {
            logger.log(logger.INFO, "Committing transaction "+tid+ " in ["+this+"]");
        }
        try {
            jmsservice.commitTransaction(connectionId, transactionId, null, 0);
        } catch (Exception e)  {
            String emsg = "Exception in committing transaction "+tid;
            logger.logStack(logger.ERROR, emsg, e);
            try {
                rollback();
                lastRolledbackTID = tid;
            } catch (Exception ee) {
                logger.logStack(logger.WARNING, 
                "Failed to rollback transaction " +tid + " after commit failure", ee);
            } finally {
                throw new StompProtocolException(emsg, e);
            }
        } finally {
            setStompTransactionId(null);
        }
        }
    }

    public void rollback() throws Exception {
        checkSession();
        synchronized(this) {

        if (getStompTransactionId() == null) {
            throw new StompProtocolException(
            "Rollback no current transaction in session "+this);
        }
        if (getDEBUG()) {
            logger.log(logger.INFO, "Rollback transaction "+tid+ " in ["+this+"]");
        }

        try {
            jmsservice.rollbackTransaction(connectionId,
                        transactionId, null, true, true);
        } finally {
            lastRolledbackTID = tid;
            setStompTransactionId(null);
        }
        }
    }

    /**
     * @param duraname if not null, subid will be ignored
     * @return subid if found else return null
     */
    public synchronized String closeSubscriber(
           String subid, String duraname) 
           throws Exception {

	TransactedSubscriber sub = null;

	if (duraname == null) {
            sub = subscribers.get(subid);
            if (sub == null) {
                return null;
            }
            sub.close(false);
            subscribers.remove(subid);
            return subid;
	}

        sub = null;
	String dn = null;
        for (String id: subscribers.keySet()) {
            sub = subscribers.get(id);
            dn = sub.duraName;
            if (dn == null) {
                continue;
            }
            if (dn.equals(duraname)) {
                break;  
            }
        }
        if (sub != null) {
            sub.close(true);
            subscribers.remove(sub.subid);
            return sub.subid;
        }
        return null;
    }

    private void deliverMessage(TransactedSubscriber sub, 
        SubscribedMessage sm, StompOutputHandler out) 
        throws Exception {
        synchronized(closeLock) {
            if (closing || closed || stompconn.isClosed() || sub.isClosing()) { 
                throw new ConsumerClosedNoDeliveryException(
                    "StompSubscriber "+this+" is closed");
            }
        }
        synchronized(this) {
            unackqueue.add(sm);
            out.sendToClient(sm.msg, stompconn.getProtocolHandler(), null);
        }
    }

    private class TransactedSubscriber 
        implements Consumer, StompSubscriber {

        String subid = null;
        long consumerId = 0L;
        String duraName = null;
        String stompdest = null;
        StompOutputHandler out = null;
        SubscribedMessage lastseen = null;
        boolean subscriberClosing = false;
        boolean subscriberClosed = false;

        public TransactedSubscriber(String subid, long id, 
            String duraname, String stompdest, StompOutputHandler out) {
            this.subid = subid;
            this.consumerId = id;
            this.duraName =  duraname;
            this.stompdest = stompdest;
            this.out = out;
        }

        public boolean isClosing() {
            synchronized(closeLock) {
                return (subscriberClosing || subscriberClosed);
            }
        }

        public void close(boolean unsubscribe) throws Exception {
            SysMessageID lastsysid = null;
            synchronized(closeLock) {
                if (subscriberClosed) {
                    return;
                }
                subscriberClosing = true;
                lastsysid = lastseen.sysid;
            }
            jmsservice.deleteConsumer(connectionId, sessionId,
                       consumerId, lastsysid, true, 
                       (unsubscribe ? duraName:null),
                       stompconn.getClientID());

            synchronized(closeLock) {
                subscriberClosed = true;
            }

            SubscribedMessage sm = null;
            Iterator<SubscribedMessage> itr = null;
            synchronized(StompTransactedSession.this) {
                itr = unackqueue.iterator();
                while (itr.hasNext()) {
                    sm = itr.next();
                    if (sm.subid.equals(subid)) {
                        itr.remove();
                    }
                }
            }
        }

        @Override
        public JMSAck deliver(JMSPacket msgpkt)
            throws ConsumerClosedNoDeliveryException {

            if (isClosing() || stompconn.isClosed()) {
                throw new ConsumerClosedNoDeliveryException(
                    "StompSubscriber "+this+" is closed");
            }
            try {
                StompFrameMessage msg = toStompFrameMessage(subid,
                                  stompdest, msgpkt.getPacket(), true);
                if (getDEBUG()) {
                    logger.log(logger.INFO, " SEND message "+msg+" for "+toString());
                }
                SubscribedMessage sm = new SubscribedMessage(
                    subid, consumerId, msgpkt.getPacket().getSysMessageID(), msg);
                deliverMessage(this, sm, out);
                synchronized(closeLock) {
                    lastseen = sm;
                }

            } catch (Exception e) {
                if (e instanceof ConsumerClosedNoDeliveryException) {
                    throw (ConsumerClosedNoDeliveryException)e; 
                }
                logger.logStack(logger.WARNING, e.getMessage(), e);
            }
            return null;
        }

        @Override
        public void startDelivery() throws Exception {
            jmsservice.setConsumerAsync(connectionId, sessionId, consumerId, this);
        }
    }

    static class SubscribedMessage {

        String subid = null;
	long consumerId = 0L;
	SysMessageID sysid = null;
        StompFrameMessage msg = null;

	public SubscribedMessage(String subid, long consumerId,
            SysMessageID sysid, StompFrameMessage msg) {
            this.subid = subid;
            this.consumerId = consumerId;
            this.sysid = sysid;
            this.msg = msg;
        }

	public SubscribedMessage(String subid, SysMessageID sysid) {
            this.subid = subid;
            this.sysid = sysid;
	}

	public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof SubscribedMessage)) {
                return false;
            }

            SubscribedMessage that = (SubscribedMessage)obj;
            if (that.subid.equals(this.subid) &&
		that.sysid.equals(this.sysid)) {
		return true;
            }
            return false;
	}

	public int hashCode() {
            return subid.hashCode()+sysid.hashCode();
	}
    }
}
