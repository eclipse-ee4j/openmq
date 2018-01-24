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

package com.sun.messaging.bridge.api;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import com.sun.messaging.jmq.util.LoggerWrapper;

/**
 * @author amyk 
 */
public abstract class StompProtocolHandler {

    protected LoggerWrapper logger = null;

    private static final String DEFAULT_SUBID_PREFIX = "/subscription-to/";

    private Map<String, StompDestination> tempQueues = Collections.synchronizedMap(
                                            new HashMap<String, StompDestination>());
    private Map<String, StompDestination> tempTopics = Collections.synchronizedMap(
                                            new HashMap<String, StompDestination>());

    private Map<String, StompDestination> mqtempQueues = Collections.synchronizedMap(
                                              new HashMap<String, StompDestination>());
    private Map<String, StompDestination> mqtempTopics = Collections.synchronizedMap(
                                              new HashMap<String, StompDestination>());


    protected List<String> subids = Collections.synchronizedList(
                                         new ArrayList<String>());

    protected String version = StompFrameMessage.STOMP_PROTOCOL_VERSION_10;

    protected StompConnection stompConnection = null;

    public static enum StompAckMode {
        AUTO_ACK,
        CLIENT_ACK,
        CLIENT_INDIVIDUAL_ACK,
    }

    protected StompProtocolHandler(LoggerWrapper loggerw) {
        logger = loggerw;
    }

    public boolean getDEBUG() {
        return false;
    }

    public void close(boolean spawnthread) {

        logger.logInfo(getKStringI_CLOSE_STOMP_CONN(stompConnection.toString())+
                       "("+spawnthread+")", null);

        if (!spawnthread) {
            try {
            stompConnection.disconnect(false);
            return;

            } catch (Throwable t) {
            logger.logWarn(getKStringW_CLOSE_STOMP_CONN_FAILED(
                stompConnection.toString(), t.getMessage()), null);
            }
        }
        Thread thr = new Thread (new Runnable() {
            public void run() {
                try {
                    logger.logInfo(getKStringI_CLOSE_STOMP_CONN(stompConnection.toString()), null);
                    stompConnection.disconnect(false);

                } catch (Throwable t) {
                    logger.logWarn(getKStringW_CLOSE_STOMP_CONN_FAILED(
                        stompConnection.toString(), t.getMessage()), null);
                }
           }
        });
        thr.setName("SpawnedClosingThread");
        thr.setDaemon(true);
        thr.start();
    }

    public abstract StompFrameMessageFactory getStompFrameMessageFactory();

    public abstract String getTemporaryQueuePrefix();
    public abstract String getTemporaryTopicPrefix();

    /**
     * @throw StompProtocolException if no support version
     */
    public abstract String negotiateVersion(String acceptVersions)
    throws StompProtocolException;

    public abstract String getSupportedVersions(); 
    public abstract String getServerName(); 

    /**
     */
    public void onCONNECT(StompFrameMessage message, StompOutputHandler out, Object ctx) {
        StompFrameMessage reply = null;

        String supportedVersions = null; 
        try {

        String acceptversions = message.getHeader(
              (StompFrameMessage.ConnectHeader.ACCEPT_VERSION));
        try {
            version = negotiateVersion(acceptversions);
        } catch (StompProtocolException e) {
            supportedVersions = getSupportedVersions();
            throw e;
        }

        String login = message.getHeader(
                       (StompFrameMessage.ConnectHeader.LOGIN));
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", login="+login, null);
        }

        String passcode = message.getHeader(
                          (StompFrameMessage.ConnectHeader.PASSCODE));
        String clientid = message.getHeader(
                         (StompFrameMessage.ConnectHeader.CLIENTID));

        String id = stompConnection.connect(login, passcode, clientid);

        reply = getStompFrameMessageFactory().newStompFrameMessage(
                    StompFrameMessage.Command.CONNECTED, logger);

        reply.addHeader(StompFrameMessage.ConnectedHeader.SESSION, id);
        reply.addHeader(StompFrameMessage.ConnectedHeader.VERSION, version);
        reply.addHeader(StompFrameMessage.ConnectedHeader.HEART_BEAT, "0,0");
        reply.addHeader(StompFrameMessage.ConnectedHeader.SERVER, getServerName());
        String requestid = message.getHeader(StompFrameMessage.CommonHeader.RECEIPT);

        if (requestid != null) {
            reply.addHeader(
                 StompFrameMessage.ResponseCommonHeader.RECEIPTID, requestid);
        }

        out.sendToClient(reply, this, ctx);

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage(message.getCommand().toString(), e);
            if (supportedVersions !=  null) {
                reply.addHeader(StompFrameMessage.ConnectedHeader.VERSION,
                    supportedVersions);
            }
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            return;
            }
        } 
    }

    /**
     */
    public void onDISCONNECT(StompFrameMessage message, StompOutputHandler out, Object ctx) {

        try {
            if (logger.isFineLoggable()) {
                logger.logFine("on"+ message.getCommand(), null);
            }
            stompConnection.disconnect(true);
            StompFrameMessage reply = getStompReceiptMessage(message);

            if (reply != null) {
                out.sendToClient(reply, this, ctx);
            }
        } catch (Exception e) {
            if (e instanceof StompNotConnectedException) {
                logger.logSevere(getKStringE_COMMAND_FAILED(
                    message.getCommand().toString(), e.getMessage(),
                    stompConnection.toString()), null);
                return;
            } else {
                logger.logSevere(getKStringE_COMMAND_FAILED(
                    message.getCommand().toString(), e.getMessage(),
                    stompConnection.toString()), e);
            }
            try {
                StompFrameMessage err = toStompErrorMessage(
                    (StompFrameMessage.Command.DISCONNECT).toString(), e);
                out.sendToClient(err, this, ctx);
            } catch (Exception ee) {
                logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(
                    e.getMessage(), ee.getMessage()), ee);
                return;
            }
        }
    }

    /**
     */
    public void onSEND(StompFrameMessage message, StompOutputHandler out, Object ctx) {

        StompFrameMessage reply = null;

        try {

        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.putAll(message.getHeaders());

        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String stompdest = headers.get(
                           (StompFrameMessage.SendHeader.DESTINATION));
        if (stompdest == null) {
            throw new StompProtocolException(
            "SEND without "+StompFrameMessage.SendHeader.DESTINATION+" header!"); 
        }

        String tid = headers.remove(StompFrameMessage.CommonHeader.TRANSACTION);

        stompConnection.sendMessage(message, tid);

        reply = getStompReceiptMessage(message);

        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Throwable e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.SEND).toString(), e);
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }
    }

    /**
     *   
     */
    public void onSUBSCRIBE(StompFrameMessage message, 
                            StompOutputHandler out, 
                            StompOutputHandler aout, Object ctx) 
                            throws Exception {
        StompFrameMessage reply = null;

        String subid = null;
        String duraname = null;
        boolean create = false;
        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
        logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String tid = headers.get(
            (StompFrameMessage.CommonHeader.TRANSACTION));
        subid = headers.get(
            (StompFrameMessage.SubscribeHeader.ID));
        String ack = headers.get(
            (StompFrameMessage.SubscribeHeader.ACK));
        StompAckMode ackMode = StompAckMode.AUTO_ACK;
        if (ack != null) {
            if (ack.equals(StompFrameMessage.AckMode.CLIENT)) {
                ackMode = StompAckMode.CLIENT_ACK;
            } else if (ack.equals(StompFrameMessage.AckMode.CLIENT_INDIVIDUAL)) {
                ackMode = StompAckMode.CLIENT_INDIVIDUAL_ACK;
            } else if (!ack.equals(StompFrameMessage.AckMode.AUTO)) {
                throw new StompProtocolException("Invalid "+
                StompFrameMessage.SubscribeHeader.ACK+" header value ["+ack+"]");
            }
        }
        String selector = headers.get(StompFrameMessage.SubscribeHeader.SELECTOR);

        String stompdest = headers.get(StompFrameMessage.SubscribeHeader.DESTINATION);
        if (stompdest == null) {
            throw new StompProtocolException(
            "SUBSCRIBE without "+StompFrameMessage.SubscribeHeader.DESTINATION+" header!"); 
        }
        if (subid == null) {
            if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10)) {
                subid = makeDefaultSubscriberId(stompdest); 
            } else {
                throw new StompProtocolException(
                    getKStringX_HEADER_NOT_SPECIFIED_FOR(
                    StompFrameMessage.SubscribeHeader.ID,
                    message.getCommand().toString()));
            }
        }
        if (subids.contains(subid)) {
            throw new StompProtocolException(getKStringX_SUBID_ALREADY_EXISTS(subid)); 
        }

        create = true;
        boolean nolocal = false;
        String val = headers.get(StompFrameMessage.SubscribeHeader.NOLOCAL);
        if (val != null && val.equalsIgnoreCase("true")) {
            nolocal = true;
        }

        duraname = headers.get(StompFrameMessage.SubscribeHeader.DURASUBNAME);

        StompSubscriber sub = stompConnection.createSubscriber(
            subid, stompdest, ackMode, selector, duraname, nolocal, tid, aout); 

        subids.add(subid);

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }
        sub.startDelivery();

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
                if (create) {
                    stompConnection.closeSubscriber(subid, null);
                    subids.remove(subid);
                }
            } catch (Exception e1) {
                logger.logFinest(message.getCommand()+
                    ": Unable to close subscriber (subid="+subid+", duraname="+duraname+"): "+
                    e1.getMessage()+" after creation failure: "+e.getMessage(), e1);
            } finally {

            try {
                reply = toStompErrorMessage((StompFrameMessage.Command.SUBSCRIBE).toString(), e);
                out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
                logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(
                    e.getMessage(), ee.getMessage()), ee);
            }

            }
            return;
        }
    }

    /**
     *  	
     */
    public void onUNSUBSCRIBE(StompFrameMessage message, StompOutputHandler out, Object ctx)
                                                              throws Exception {
        StompFrameMessage reply = null;

        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String subid = headers.get(
                       (StompFrameMessage.SubscribeHeader.ID));
        String stompdest = headers.get(
                           (StompFrameMessage.SubscribeHeader.DESTINATION));
        String duraname = headers.get(
                          (StompFrameMessage.SubscribeHeader.DURASUBNAME));
        if (subid == null) {
            if (!version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10)) {
                throw new StompProtocolException(
                    getKStringX_HEADER_NOT_SPECIFIED_FOR(
                    StompFrameMessage.UnsubscribeHeader.ID,
                    message.getCommand().toString()));
            }
        }

        if (subid == null && duraname == null) {
            if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10)) {
                if (stompdest == null) {
                    throw new StompProtocolException(
                        getKStringX_UNSUBSCRIBE_WITHOUT_HEADER(
                        StompFrameMessage.UnsubscribeHeader.DESTINATION, 
                        StompFrameMessage.UnsubscribeHeader.ID));
                }
                subid = makeDefaultSubscriberId(stompdest);
            }
        }

        String id = stompConnection.closeSubscriber(subid, duraname);

        if (duraname == null) {
            subids.remove(subid);
        } else {
            if (id != null) {
                subids.remove(id);
            }
        }

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.UNSUBSCRIBE).toString(), e);
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }
    }

    /**
     *
     */
    public void onBEGIN(StompFrameMessage message, StompOutputHandler out, Object ctx) throws Exception {
        StompFrameMessage reply = null;

        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String tid = headers.get(
                         (StompFrameMessage.CommonHeader.TRANSACTION));
        if (tid == null) {
            throw new StompProtocolException(getKStringX_HEADER_NOT_SPECIFIED_FOR(
                  StompFrameMessage.CommonHeader.TRANSACTION,
                  (StompFrameMessage.Command.BEGIN).toString())); 
        }

        stompConnection.beginTransactedSession(tid);

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.BEGIN).toString(), e);
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }

    }

    /**
     *
     */
    public void onCOMMIT(StompFrameMessage message, StompOutputHandler out, Object ctx) throws Exception {
        StompFrameMessage reply = null;

        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String tid = headers.get(
                        (StompFrameMessage.CommonHeader.TRANSACTION));
        if (tid == null) {
            throw new StompProtocolException(getKStringX_HEADER_NOT_SPECIFIED_FOR(
                  StompFrameMessage.CommonHeader.TRANSACTION,
                  (StompFrameMessage.Command.COMMIT).toString())); 
        }

        stompConnection.commitTransactedSession(tid);

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.COMMIT).toString(), e);
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }
    }

    /**
     *
     */
    public void onABORT(StompFrameMessage message, StompOutputHandler out, Object ctx) throws Exception {
        StompFrameMessage reply = null;

        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }

        String tid = headers.get(
                         (StompFrameMessage.CommonHeader.TRANSACTION));
        if (tid == null) {
            throw new StompProtocolException(getKStringX_HEADER_NOT_SPECIFIED_FOR(
                  StompFrameMessage.CommonHeader.TRANSACTION,
                  (StompFrameMessage.Command.ABORT).toString())); 
        }

        stompConnection.abortTransactedSession(tid);

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.ABORT).toString(), e);
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }

    }

    public void onNACK(StompFrameMessage message, 
                       StompOutputHandler out, 
                       Object ctx) throws Exception {
        doACK(message, out, ctx, true);
    }

    public void onACK(StompFrameMessage message, 
                      StompOutputHandler out, 
                      Object ctx) throws Exception {
        doACK(message, out, ctx, false);
    }

    /**
     */
    public void doACK(StompFrameMessage message, 
                      StompOutputHandler out, 
                      Object ctx, boolean nack)
                      throws Exception {
        StompFrameMessage reply = null;

        try {

        HashMap<String, String> headers = message.getHeaders();
        if (logger.isFineLoggable()) {
            logger.logFine("on"+ message.getCommand()+", headers="+headers, null);
        }
        if (nack) {
           if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10) ||
               version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_11)) {
                throw new StompProtocolException(
                    message.getKStringX_UNKNOWN_STOMP_CMD(
                    StompFrameMessage.Command.NACK.toString()));
           }
        }

        String msgid = headers.get(StompFrameMessage.AckHeader.MESSAGEID);
        if (msgid == null) {
           if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10) ||
               version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_11)) {
                throw new StompProtocolException(
                    getKStringX_HEADER_NOT_SPECIFIED_FOR(
                    StompFrameMessage.AckHeader.MESSAGEID,
                    (StompFrameMessage.Command.ACK).toString())); 
            }
        }
        String subid = headers.get(StompFrameMessage.AckHeader.SUBSCRIPTION);
        String subidPrefix = null;
        if (subid == null) {
            if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_11)) {
                throw new StompProtocolException(
                    getKStringX_HEADER_NOT_SPECIFIED_FOR(
                    StompFrameMessage.AckHeader.SUBSCRIPTION,
                    (StompFrameMessage.Command.ACK).toString())); 
            }
            if (version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10)) {
                subidPrefix = DEFAULT_SUBID_PREFIX;
            }
        }
        if (subid != null) {
            if (!subids.contains(subid)) {
                throw new StompProtocolException(
                    getKStringX_SUBSCRIBER_ID_NOT_FOUND(subid));
            }
        }

        String id = headers.get(StompFrameMessage.AckHeader.ID);
        if (id == null) {
            if (!version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_10) &&
                !version.equals(StompFrameMessage.STOMP_PROTOCOL_VERSION_11)) {
                throw new StompProtocolException(
                    getKStringX_HEADER_NOT_SPECIFIED_FOR(
                    StompFrameMessage.AckHeader.ID,
                    (StompFrameMessage.Command.ACK).toString())); 
            }
        } else {
            int ind = id.lastIndexOf("ID:");
            if (ind < 0) {
                throw new StompProtocolException(
                    getKStringX_INVALID_HEADER_VALUE(
                    StompFrameMessage.AckHeader.ID, id));
            }
            String subidSave = subid; 
            String msgidSave = msgid;
            msgid = id.substring(ind);
            subid = id.substring(0, ind);
            if (subidSave != null || msgidSave != null) {
                logger.logWarn(getKStringI_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR(  
                    StompFrameMessage.AckHeader.ID+"="+id,  
                    StompFrameMessage.AckHeader.SUBSCRIPTION+"="+subidSave+","+
                    StompFrameMessage.AckHeader.MESSAGEID+"="+msgidSave,
                    (StompFrameMessage.Command.ACK).toString()), null); 
            }
        }
        
        String tid = headers.get(StompFrameMessage.AckHeader.TRANSACTION);

        if (subidPrefix != null) {
            if (nack) {
                throw new StompProtocolException(
                    message.getKStringX_UNKNOWN_STOMP_CMD(
                    StompFrameMessage.Command.NACK.toString()));
            }
            if (tid != null) {
                logger.logWarn(getKStringW_NO_SUBID_TXNACK(
                    StompFrameMessage.MessageHeader.SUBSCRIPTION,
                    tid, subidPrefix, msgid), null); 
            } else {
                logger.logWarn(getKStringW_NO_SUBID_NONTXNACK(
                    StompFrameMessage.MessageHeader.SUBSCRIPTION,
                    subidPrefix, msgid), null);
            }
            stompConnection.ack10(subidPrefix, msgid, tid);
        } else {
            stompConnection.ack(id, tid, subid, msgid, nack);
        }

        reply = getStompReceiptMessage(message);
        if (reply != null) {
            out.sendToClient(reply, this, ctx);
        }

        } catch (Exception e) {
            logger.logSevere(getKStringE_COMMAND_FAILED(
                message.getCommand().toString(), e.getMessage(), stompConnection.toString()), e);
            try {
            reply = toStompErrorMessage((StompFrameMessage.Command.ACK).toString(), e, 
                        ((e instanceof StompUnrecoverableAckException) ? true:false));
            out.sendToClient(reply, this, ctx);
            } catch (Exception ee) {
            logger.logWarn(getKStringE_UNABLE_SEND_ERROR_MSG(e.getMessage(), ee.getMessage()), ee);
            }
            return;
        }

    }

    /**
     */	   
    private StompFrameMessage getStompReceiptMessage(
        StompFrameMessage message)
        throws Exception {

        StompFrameMessage reply = null;

        String requestid = message.getHeader(
                           (StompFrameMessage.CommonHeader.RECEIPT));
        if (requestid != null) {
            reply = getStompFrameMessageFactory().newStompFrameMessage(
                        StompFrameMessage.Command.RECEIPT, logger);
            reply.addHeader(
                 StompFrameMessage.ResponseCommonHeader.RECEIPTID, requestid);
        }
        return reply;
    }

    /**
     */
    public StompFrameMessage toStompErrorMessage(
        String where, Throwable e)
        throws Exception {
        return toStompErrorMessage(where, e, false);
    }

    public StompFrameMessage toStompErrorMessage(
        String where, Throwable e, boolean fatal)
        throws Exception {

        StompFrameMessage err = getStompFrameMessageFactory().
            newStompFrameMessage(StompFrameMessage.Command.ERROR, logger);
        err.addHeader((StompFrameMessage.ErrorHeader.MESSAGE),
            where+": "+(e == null ? "":e.getMessage())+
            (fatal ? ", STOMP connection will be closed":""));
        if (e != null) {
            err.writeExceptionToBody(e);
        }
        if (fatal) {
            err.setFatalERROR();
        }
        return err;
    }
  
    /*
     * @throw Exception if not exist
     */
    private StompDestination getMQTempStompDestination(
        String stompdest, boolean isQueue)
        throws Exception {

        if (isQueue) {
            synchronized(mqtempQueues) {
                StompDestination d = mqtempQueues.get(stompdest);
                if (d == null) {
                    throw new StompProtocolException("MQ TemporaryQueue not found: "+stompdest);
                }
                return d;
            }
        } else {
            synchronized(mqtempTopics) {
                StompDestination d = mqtempTopics.get(stompdest);
                 if (d == null) {
                     throw new StompProtocolException("MQ TemporaryTopic not found: "+stompdest);
                 }
                return d;
            }
        }
    }

    private StompDestination getTempStompDestination(
        String stompdest, boolean isQueue, StompSession ss)
        throws Exception {

        if (isQueue) {
            synchronized(tempQueues) {
                StompDestination d = tempQueues.get(stompdest);
                if (d == null) {
                    d = ss.createTempStompDestination(true);
                    tempQueues.put(stompdest, d);
                }
                return d;
            }
        } else {
            synchronized(tempTopics) {
                StompDestination d = tempTopics.get(stompdest);
                if (d == null) {
                    d = ss.createTempStompDestination(false);
                    tempTopics.put(stompdest, d);
                }
                return d;
            }
        }
    }

    private void cacheMQTempStompDestination(StompDestination d, boolean isQueue)
    throws Exception {

        if (isQueue) {
            synchronized(mqtempQueues) {
                if (mqtempQueues.get(d.getName()) == null) {
                    mqtempQueues.put(d.getName(), d);
                }
            }
        } else {
            synchronized(mqtempTopics) {
                if (mqtempTopics.get(d.getName()) == null) {
                    mqtempTopics.put(d.getName(), d);
                }
            }
        }
    }

    public StompDestination toStompDestination(
        String stompdest, StompSession ss, boolean sub)
        throws Exception {

        if (stompdest.startsWith("/queue/")) {
            String name = stompdest.substring("/queue/".length(), stompdest.length()).trim();
            return ss.createStompDestination(name, true);

        } else if (stompdest.startsWith("/topic/")) {
            String name = stompdest.substring("/topic/".length(), stompdest.length()).trim();
            return ss.createStompDestination(name, false);

        } else if (stompdest.startsWith("/temp-queue/")) {
            String name = stompdest.substring("/temp-queue/".length(), stompdest.length()).trim();
            if (name.startsWith(getTemporaryQueuePrefix())) {
                if (sub) {
                    throw new StompProtocolException("Can't subscribe "+stompdest);
                }
                return getMQTempStompDestination(name, true);
            }
            return getTempStompDestination(name, true, ss);

        } else if (stompdest.startsWith("/temp-topic/")) {
            String name = stompdest.substring("/temp-topic/".length(), stompdest.length()).trim();
            if (name.startsWith(getTemporaryTopicPrefix())) {
                if (sub) {
                    throw new StompProtocolException("Can't subscribe "+stompdest);
                }
                return getMQTempStompDestination(name, false);
            }
            return getTempStompDestination(name, false, ss);

        } else {
            throw new StompProtocolException(
            "Invalid header "+StompFrameMessage.SendHeader.DESTINATION+" value:"+stompdest); 
        }
    }

    public String toStompFrameDestination(
        StompDestination destination, boolean cache)
        throws Exception {

        if (destination == null) {
            throw new StompProtocolException("StompDestination is null !"); 
        }

        StringBuffer buf = new StringBuffer();

        if (destination.isTemporary()) {
            if (destination.isQueue()) {
                String d = destination.getName();
                buf.append("/temp-queue/").append(d);
                if (cache) {
                    cacheMQTempStompDestination(destination, true);
                }
                return buf.toString();
            } else {
                String d = destination.getName();
                buf.append("/temp-topic/").append(d);
                if (cache) {
                    cacheMQTempStompDestination(destination, false);
                }
                return buf.toString();
            }
        } else {
            if (destination.isQueue()) {
                buf.append("/queue/").append(destination.getName());
                return buf.toString();
            } else {
                buf.append("/topic/").append(destination.getName());
                return buf.toString();
            }
        }
    }

    private static String makeDefaultSubscriberId(String stompdest) {
        return DEFAULT_SUBID_PREFIX+stompdest;
    }

    public void fromStompFrameMessage(StompFrameMessage message, StompMessage msg) 
    throws Exception {

        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.putAll(message.getHeaders());

        if (message.getContentLength() != -1) {
            headers.remove(
                    (StompFrameMessage.CommonHeader.CONTENTLENGTH));
            msg.setBytes(message);
        } else {
            msg.setText(message);
        }

        String stompdest = headers.remove(StompFrameMessage.SendHeader.DESTINATION);
        msg.setDestination(stompdest);

        String v = headers.remove(StompFrameMessage.SendHeader.PRIORITY);
        if (v != null) {
            msg.setJMSPriority(v);
        }
        v = headers.remove(StompFrameMessage.SendHeader.PERSISTENT);
        if (v != null) {
            msg.setPersistent(v);
        }
        v = headers.remove(StompFrameMessage.SendHeader.EXPIRES);
        if (v != null) {
            msg.setJMSExpiration(v);
        }
        v = headers.remove(StompFrameMessage.SendHeader.CORRELATIONID);
        if (v != null) {
            msg.setJMSCorrelationID(v);
        }
        v = headers.remove(StompFrameMessage.SendHeader.TYPE);
        if (v != null) {
            msg.setJMSType(v);
        }
        v = headers.remove(StompFrameMessage.SendHeader.REPLYTO);
        if (v != null) {
            msg.setReplyTo(v);
        }
        v = headers.remove(StompFrameMessage.CommonHeader.RECEIPT);

        String k = null, h = null;
        Iterator<Map.Entry<String, String>> itr = headers.entrySet().iterator();
        Map.Entry<String, String> e = null;
        while (itr.hasNext()) {
            e = itr.next();
            k = e.getKey();
            v = e.getValue();
            h = k+StompFrameMessage.HEADER_SEPERATOR+v;
            if (logger.isFineLoggable()) {
                logger.logFine("Setting header "+h+" as JMS message property", null);
            }
            msg.setProperty(k, v);
            itr.remove();
        }
    }

    public StompFrameMessage toStompFrameMessage(StompMessage msg, boolean needAck) 
    throws Exception {

	StompFrameMessage message = getStompFrameMessageFactory().
            newStompFrameMessage(StompFrameMessage.Command.MESSAGE, logger);

	HashMap<String, String> headers = message.getHeaders();

        String subid = msg.getSubscriptionID();
	headers.put(StompFrameMessage.MessageHeader.SUBSCRIPTION, subid);

	headers.put((StompFrameMessage.MessageHeader.DESTINATION),
                    msg.getDestination());

        String msgid = msg.getJMSMessageID();
	headers.put(StompFrameMessage.MessageHeader.MESSAGEID, msgid);

        if (needAck) {
            headers.put(StompFrameMessage.MessageHeader.ACK, subid+msgid);
        }

        String replyto = msg.getReplyTo();
	if (replyto != null) {
	    headers.put(StompFrameMessage.MessageHeader.REPLYTO, replyto);
	}

	String val = msg.getJMSCorrelationID();
	if (val != null) {
	    headers.put(StompFrameMessage.MessageHeader.CORRELATIONID, val);
	}

	headers.put(StompFrameMessage.MessageHeader.EXPIRES,
		    String.valueOf(msg.getJMSExpiration()));

	headers.put(StompFrameMessage.MessageHeader.REDELIVERED,
			String.valueOf(msg.getJMSRedelivered()));

	headers.put(StompFrameMessage.SendHeader.PRIORITY,
                    String.valueOf(msg.getJMSPriority()));

	headers.put(StompFrameMessage.MessageHeader.TIMESTAMP,
                    String.valueOf(msg.getJMSTimestamp()));

	val = msg.getJMSType();
	if (val != null) {
            headers.put(StompFrameMessage.MessageHeader.TYPE, val);
	}

        String name, value;
	Enumeration en = msg.getPropertyNames();
	while (en.hasMoreElements()) {
            name = (String)en.nextElement();
            value = msg.getProperty(name);
            headers.put(name, value);
	}

        if (msg.isTextMessage()) {
            String text = msg.getText();
            if (text != null) {
            	byte[] data = text.getBytes("UTF-8");
            	message.setBody(data);
            	headers.put(StompFrameMessage.CommonHeader.CONTENTLENGTH,
                            String.valueOf(data.length));
            } else {
            	headers.put(StompFrameMessage.CommonHeader.CONTENTLENGTH,
                            String.valueOf(0));
            }
            message.setTextMessageFlag();
	} else if (msg.isBytesMessage()) {
            byte[] data = msg.getBytes();
            message.setBody(data);
            headers.put(StompFrameMessage.CommonHeader.CONTENTLENGTH,
            		String.valueOf(data.length));

	} else {
            throw new StompProtocolException("Message type is not supported: "+msg);
	}
        return message;
    }

    public void checkValidMessagePropertyName(String name) 
    throws StompProtocolException {
        //reserved words for JMS message selector
        if (name == null || "".equals(name) ||
            "NULL".equalsIgnoreCase(name) ||
            "TRUE".equalsIgnoreCase(name) ||
            "FALSE".equalsIgnoreCase(name) ||
            "NOT".equalsIgnoreCase(name) ||
            "AND".equalsIgnoreCase(name) ||
            "OR".equalsIgnoreCase(name) ||
            "BETWEEN".equalsIgnoreCase(name) ||
            "LIKE".equalsIgnoreCase(name) ||
            "IN".equalsIgnoreCase(name) ||
            "IS".equalsIgnoreCase(name)) {
            throw new StompProtocolException(getKStringX_INVALID_MESSAGE_PROP_NAME(name));
        }
        //JMS selector rules for identifiers
        char[] namechars = name.toCharArray();
        if (Character.isJavaIdentifierStart(namechars[0])) {
            for (int i = 1; i < namechars.length; i++) {
                if (!Character.isJavaIdentifierPart(namechars[i])) {
                    throw new StompProtocolException(getKStringX_INVALID_MESSAGE_PROP_NAME(name));
                }
            }
        } else {
            throw new StompProtocolException(getKStringX_INVALID_MESSAGE_PROP_NAME(name));
        }
    }

    protected abstract String getKStringI_CLOSE_STOMP_CONN(String stompconn);
    protected abstract String getKStringW_CLOSE_STOMP_CONN_FAILED(String stompconn, String emsg);
    protected abstract String getKStringE_COMMAND_FAILED(String cmd, String emsg, String stompconn);
    protected abstract String getKStringE_UNABLE_SEND_ERROR_MSG(String emsg, String eemsg);
    protected abstract String getKStringX_SUBID_ALREADY_EXISTS(String subid); 
    protected abstract String getKStringX_UNSUBSCRIBE_WITHOUT_HEADER(String destHeader, String subidHeader);
    protected abstract String getKStringX_HEADER_NOT_SPECIFIED_FOR(String header, String cmd);
    protected abstract String getKStringX_SUBSCRIBER_ID_NOT_FOUND(String subid);
    protected abstract String getKStringW_NO_SUBID_TXNACK(String subidHeader, String tid, String subidPrefix, String msgid); 
    protected abstract String getKStringW_NO_SUBID_NONTXNACK(String subidHeader, String subidPrefix, String msgid);
    protected abstract String getKStringX_INVALID_MESSAGE_PROP_NAME(String name);
    protected abstract String getKStringX_INVALID_HEADER_VALUE(String header, String value);
    protected abstract String getKStringI_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR(
                              String useHeader, String ignoreHeaders, String cmd);

}
