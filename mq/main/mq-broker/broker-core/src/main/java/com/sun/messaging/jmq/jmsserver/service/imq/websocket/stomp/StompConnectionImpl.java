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
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.Consumer;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.ConnectionClosedListener;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompSubscriber;
import com.sun.messaging.bridge.api.StompConnection;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompFrameParseException;
import com.sun.messaging.bridge.api.StompNotConnectedException;

/**
 * @author amyk 
 */
public class StompConnectionImpl 
implements StompConnection, ConnectionClosedListener {

    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();

    private StompProtocolHandlerImpl sph = null;
    private JMSService jmsservice = null;
    private String clientID = null;
    private Long connectionID = null;

    private StompSenderSession pubSession = null;
    private StompTransactedSession txSession = null;

    private Map<String, StompSubscriberSession> subSessions =
                Collections.synchronizedMap(
                new HashMap<String, StompSubscriberSession>());

    private int nextTempDestIndex = 0;

    private Object closeLock = new Object();
    private boolean closing = false, closed = true;

    public StompConnectionImpl(StompProtocolHandlerImpl h) {
        sph = h;
        jmsservice = sph.getJMSService();
    }

    protected boolean getDEBUG() {
        return sph.getDEBUG();
    }

    protected StompProtocolHandler getProtocolHandler() {
        return sph;
    }

    protected JMSService getJMSService() {
        return jmsservice;
    }

    protected synchronized String getIdForTemporaryDestination()
    throws Exception {
        checkConnection();
        return sph.getRemoteAddress().getHostAddress()+
               (clientID == null ? "":"/"+clientID)+
               "/"+connectionID+"/"+nextTempDestIndex++;
    }

    protected synchronized void fillRemoteIPAndPort(Packet pkt)
    throws Exception {
        checkConnection();
        pkt.setIP(sph.getRemoteAddress().getAddress());
        pkt.setPort(sph.getRemotePort());
    }

    /**
     * @return the connection id
     */
    @Override
    public synchronized String connect(
        String login, String passcode, String clientid) 
        throws Exception {

        this.clientID = clientid;

        if (connectionID != null) {
            throw new javax.jms.IllegalStateException(
            "Unexpected "+StompFrameMessage.Command.CONNECT+", already connected");
        }

        JMSServiceReply reply = jmsservice.createConnection(login, passcode, null);
        long connid = reply.getJMQConnectionID();
        connectionID = Long.valueOf(connid);
        closed = false;

        Connection conn = Globals.getConnectionManager().
                   getConnection(new ConnectionUID(connid));
        if (conn != null) {
            conn.addConnectionClosedListener(this);
        } else {
            throw new StompProtocolException("No connection");
        }
        jmsservice.setClientId(connid, clientid, false/*share*/, null/*namespace*/);
        jmsservice.startConnection(connid);
        return connectionID.toString();
    }

    @Override
    public void connectionClosed(Connection conn) {
        if (!conn.getConnectionUID().equals(
            new ConnectionUID(connectionID.longValue()))) {
            return;
        }
        if (!isClosed()) {
            String emsg = br.getKString(br.I_STOMP_CLOSE_CONN,
                              String.valueOf(connectionID));
            //StompFrameMessage frame = sph.toStompErrorMessage(emsg, null);
            logger.log(logger.WARNING, emsg);
            sph.close(true);
        }
    }

    @Override
    public void disconnect(boolean closeCheck) throws Exception {
        synchronized(closeLock) {
            if (closeCheck) {
                checkConnection();
            }
            if (closed || closing) {
                if (logger.isFineLoggable() || getProtocolHandler().getDEBUG()) {
                    logger.log(Logger.INFO,
                    br.getKString(br.I_STOMP_NOT_CONNECTED,
                    String.valueOf(connectionID)));
                }
                return;
            }
            closing = true;
        }
        try {
            if (pubSession != null) {
                pubSession.close();
            }       
            if (txSession != null) {
                txSession.close();
            }
            synchronized(subSessions) {
                for (String subid: subSessions.keySet()) {
                    StompSubscriberSession ss = subSessions.get(subid);
                    ss.close();
                }
                subSessions.clear();
            }
        } catch (Exception e) {
            if (getDEBUG()) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            } 
        } finally {
            try {
                jmsservice.destroyConnection(connectionID.longValue());
            } catch (Exception e) {
                if (getDEBUG()) {
                    logger.logStack(logger.WARNING, e.getMessage(), e);
                } else {
                    logger.log(logger.WARNING, e.getMessage());
                }
            }
            synchronized(closeLock) {
                closed = true;
            }
        }
        logger.log(Logger.INFO, br.getKString(
            br.I_STOMP_CONN_CLOSED, String.valueOf(connectionID)));
    }

    private StompSenderSession getSenderSession() throws Exception {
        checkConnection();
        if (pubSession == null) {
            pubSession = new StompSenderSession(this);
        }
        return pubSession;
    }

    public void sendMessage(StompFrameMessage message, String tid)
    throws Exception {

        StompSenderSession ss = null;
        if (tid != null) {
            ss = (StompSenderSession)getTransactedSession(tid);
            if (logger.isFineLoggable()) {
                logger.logFine("Sending message on transacted session: "+ss+" for transaction "+tid, null);
            }
        } else {
            ss = getSenderSession();
        }

        ss.sendStompMessage(message);
    }

    private StompSession getTransactedSession(String tid) throws Exception {
        checkConnection();

        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (txSession == null) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_NO_SESSION_FOR_TXN, tid));
        }
        String currtid = txSession.getStompTransactionId();
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_TXN_NOT_FOUND, 
                tid+"["+(currtid == null ?"":"current:"+currtid)+"]"));
        }
        return txSession;
    }

    public StompSession getTransactedSession() throws Exception {
        checkConnection();
        if (txSession == null) {
            return null;
        } 

        if (txSession.getStompTransactionId() == null) {
            return null;
        }
        return txSession;
    }

    private StompSubscriberSession createSubscriberSession(
        String subid, StompAckMode ackMode) 
        throws Exception {
       checkConnection();

       if (subid == null) {
           throw new IllegalArgumentException("No subscription id");
       }

       StompSubscriberSession ss = subSessions.get(subid);
       if (ss != null) {
           throw new StompProtocolException(
           br.getKString(br.X_STOMP_SUBID_ALREADY_EXISTS, subid));
       }
       ss = new StompSubscriberSession(subid, ackMode, this);
       subSessions.put(subid, ss);

       return ss;
    }

    /**
     */
    @Override
    public StompSubscriber createSubscriber(String subid, String stompdest,
        StompAckMode ackMode, String selector, String duraname, boolean nolocal,
        String tid, StompOutputHandler aout)
        throws Exception {

        StompSubscriber sub = null;
        if (tid == null) {
            StompSubscriberSession ss = createSubscriberSession(subid, ackMode);
            sub = ss.createSubscriber(sph.toStompDestination(stompdest, ss, true),
                                      selector, duraname, nolocal, aout);
        } else {
            StompTransactedSession ts = (StompTransactedSession)getTransactedSession(tid);
            sub = ts.createSubscriber(subid,
                      sph.toStompDestination(stompdest, ts, true),
                      selector, duraname, nolocal, aout);
        }
        return sub;
    }

    private StompSubscriberSession getSubscriberSession(String subid)
    throws Exception {
        checkConnection();

        if (subid == null) {
            throw new IllegalArgumentException("No subscription id");
        }

        StompSubscriberSession ss = subSessions.get(subid);
        return ss;
    }

    @Override
    public void ack10(String subidPrefix, String msgid, String tid)
    throws Exception {
        throw new StompProtocolException(
        "STOMP 1.0 no subscription id ACK is not supported");
    }

    @Override
    public void ack(String id, String tid, String subid,
                    String msgid, boolean nack)
                    throws Exception {

        if (id == null && subid == null) {
            throw new IllegalArgumentException("ack(): null subid");
        }
        if (tid != null) {
            StompTransactedSession ts = (StompTransactedSession)
                                        getTransactedSession(tid);
            ts.ack(subid, msgid, nack);
        } else {
            StompSubscriberSession ss = (StompSubscriberSession)
                                     getSubscriberSession(subid);
            if (ss != null ) {
                ss.ack(msgid, nack);
            } else {
                StompTransactedSession ts = (StompTransactedSession)
                                             getTransactedSession();
                if (ts == null) {
                    throw new StompProtocolException(
                    br.getKString(br.X_STOMP_NO_SESSION_FOR_SUBSCRIBER_ACK,
                                  subid, msgid));
                }
                ts.ack(subid, msgid, nack);
            }
        }
    }

    /**
     * @return subid if duraname not null
     */
    @Override
    public String closeSubscriber(
        String subid, String duraname) throws Exception {

        checkConnection();

        StompSubscriberSession ss = null;
        if (duraname == null) {
            ss = subSessions.get(subid);
            if (ss != null) {
                ss.close();
                subSessions.remove(subid);
                return null;
            }
        } else {
            if (getClientID() == null) {
                throw new StompProtocolException(
                br.getKString(br.X_UNSUBSCRIBE_DURA_NO_CLIENTID, duraname));
            }
            String dn = null;
            for (String sid: subSessions.keySet()) {
                ss = subSessions.get(sid);
                dn = ss.getDurableName();
                if (dn == null) {
                    continue;
                }
                if (dn.equals(duraname)) {
                    ss.closeSubscribers();
                    ss.unsubscribeDurable(duraname);
                    ss.close();
                    subSessions.remove(sid);
                    return sid;
                }
            }
        }

        if (txSession != null) {
            String sid = txSession.closeSubscriber(subid, duraname);
            if (duraname != null) return sid;
            if (sid != null) return sid;
        } else if (duraname != null) {
            ((StompSessionImpl)getSenderSession()).unsubscribeDurable(duraname);
        }
        throw new StompProtocolException(
        br.getKString(br.X_STOMP_SUBSCRIBER_ID_NOT_FOUND, subid));
    }

    @Override
    public void beginTransactedSession(String tid) throws Exception {
        checkConnection();

        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (txSession == null) {
            txSession =  new StompTransactedSession(this);
        }
        String currtid =  txSession.getStompTransactionId();
        if (currtid != null) {
            throw new StompProtocolException(
             br.getKString(br.X_STOMP_NEST_TXN_NOT_ALLOWED, tid, currtid));
        }
        txSession.begin(tid);
    }

    @Override
    public void commitTransactedSession(String tid) throws Exception {
        checkConnection();

        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }
        if (txSession == null) {
            throw new StompProtocolException(
            br.getKString(br.X_STOMP_NO_SESSION_FOR_TXN, tid));
        }
        String currtid =  txSession.getStompTransactionId();
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_TXN_NOT_FOUND, 
                tid+"["+(currtid == null ?"":"current:"+currtid)+"]"));
        }
        txSession.commit();

    }

    @Override
    public void abortTransactedSession(String tid) throws Exception {
        checkConnection();

        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (txSession == null) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_NO_SESSION_FOR_TXN, tid));
        }
        String currtid =  txSession.getStompTransactionId();
        String lastrb = txSession.getLastRolledbackStompTransactionId();
        if (currtid == null && lastrb != null && lastrb.equals(tid)) {
            logger.log(Logger.INFO, 
                br.getKString(br.X_STOMP_TXN_ALREADY_ROLLEDBACK, tid));
            return;
        }
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_TXN_NOT_FOUND,
                tid+"["+(currtid == null ?"":"current:"+currtid)+"]"));
        }
        txSession.rollback();
    }

    protected Long getConnectionID() throws Exception {
        checkConnection();
        return connectionID;
    }

    protected synchronized String getClientID() throws Exception {
        checkConnection();
        return clientID;
    }

    public String toString() {
        Long id = connectionID;
        return "["+(id == null ? "":id.toString())+"]";
    }
    
    /**
     */
    private void checkConnection() throws Exception {
        synchronized(closeLock) {
            if (closing || closed) {
                throw new StompNotConnectedException(
                br.getKString(br.I_STOMP_NOT_CONNECTED, 
                String.valueOf(connectionID)));
            }
        }
    }

    protected boolean isClosed() {
        synchronized(closeLock) {
            return (closing || closed);
        }
    }

}
