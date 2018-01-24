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

package com.sun.messaging.bridge.service.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.*;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompConnection;
import com.sun.messaging.bridge.api.StompSubscriber;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompNotConnectedException;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;


/**
 * @author amyk 
 */
public class StompConnectionImpl 
implements StompConnection, ExceptionListener {

    private Logger _logger = null;

    private BridgeContext _bc = null;
    private Properties _jmsprop = null;
    private Connection _connection = null;
    private String  _connectionUID = "";

    private boolean _connectionException = false;

    private StompSenderSession _pubSession = null;
    private String _clientid = null;
    private StompBridgeResources _sbr = null;

    private Map<String, StompSubscriberSession> _subSessions = 
                Collections.synchronizedMap(
                new HashMap<String, StompSubscriberSession>());  

    private StompTransactedSession _txSession = null;
    private StompProtocolHandlerImpl _sph = null;

    public StompConnectionImpl(StompProtocolHandlerImpl sph) {

        _logger = sph.getLogger();
        _bc =sph.getBridgeContext();
        _jmsprop = sph.getJMSConfig();
        _sph =  sph;
        _sbr = sph.getStompBridgeResources();
    }

    protected StompProtocolHandlerImpl getProtocolHandler() {
        return _sph;
    }

    /**
     */
    @Override 
    public synchronized String connect(String login, 
                                       String passcode,
                                       String clientid) throws Exception { 

        if (_connection != null) {
            throw new StompProtocolException("already connected"); 
        }

        if (clientid == null) {
        _logger.log(Level.INFO, _sbr.getString(_sbr.I_CREATE_JMS_CONN, login));
        } else {
        _logger.log(Level.INFO, _sbr.getString(_sbr.I_CREATE_JMS_CONN_WITH_CLIENTID, login, clientid));
        }

        if (login != null) {
            _connection = _bc.getConnectionFactory(_jmsprop).createConnection(login, passcode);
        } else {
            _connection = _bc.getConnectionFactory(_jmsprop).createConnection();
        }
        if (clientid != null) {
            _clientid = clientid;
            _connection.setClientID(clientid);
        }
        ((com.sun.messaging.jmq.jmsclient.ConnectionImpl)
                                _connection)._setAppTransactedAck();
        
        _connectionUID = ((com.sun.messaging.jmq.jmsclient.ConnectionImpl)
                                             _connection)._getConnectionID()+
                                             "["+_connection.getClientID()+"]";
        
        _connection.start();

        _logger.log(Level.INFO, _sbr.getString(_sbr.I_STARTED_JMS_CONN, _connectionUID, login)); 

        return _connectionUID;
    }

    public synchronized Connection getConnection() {
        return _connection;
    }

    /**
     *
     */
    public String toString() {
        String s = _connectionUID;
        return ((s == null) ? "":s);
    }

    /**
     */
    @Override
    public synchronized void disconnect(boolean check) throws Exception { 
        if (check) checkConnection();

        if (_connection != null) {
            try {
                if (_pubSession != null) {
                    _pubSession.close();
                    _pubSession = null;
                 }
                if (_txSession != null) {
                    _txSession.close();
                    _txSession = null;
                }
                synchronized(_subSessions) {
                    for (String subid: _subSessions.keySet()) {
                        StompSubscriberSession ss = _subSessions.get(subid);
                        ss.close();
                    }
                    _subSessions.clear();
                }
                _connection.close();
            } catch (Exception e) {
                throw e;
            } finally {
                _connection = null;
                _connectionException = false;
            }
            _logger.log(Level.INFO, _sbr.getString(_sbr.I_STOMP_CONN_CLOSED, _connectionUID));
        } else {
            _logger.log(Level.FINE, _sbr.getString(_sbr.I_STOMP_CONN_NOT_CONNECTED, _connectionUID));
        }
    }

    /**
     */
    @Override
    public synchronized void beginTransactedSession(String tid)
                                             throws Exception { 
        Connection conn = _connection;
        checkConnection(conn);


        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (_txSession == null) {
            _txSession =  new StompTransactedSession(this);
        }
        String currtid =  _txSession.getStompTransactionId();
        if (currtid != null) {
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_NESTED_TXN_NOT_ALLOWED, currtid, tid));
        }
        _txSession.setStompTransactionId(tid);
    }

    /**
     */
    @Override
    public synchronized void commitTransactedSession(String tid)
                                              throws Exception { 
        Connection conn = _connection;
        checkConnection(conn);


        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (_txSession == null) { 
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NO_SESSION, tid));
        }
        String currtid =  _txSession.getStompTransactionId();
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NOT_FOUND, tid)+
                        (currtid == null ?"":" "+_sbr.getString(_sbr.I_CURRENT_TXN, currtid)));
        }
        _txSession.commit();
    }

    /**
     */
    @Override
    public synchronized void abortTransactedSession(String tid)
                                             throws Exception { 
        Connection conn = _connection;
        checkConnection(conn);


        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (_txSession == null) { 
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NO_SESSION, tid));
        }
        String currtid =  _txSession.getStompTransactionId();
        String lastrb = _txSession.getLastRolledbackStompTransactionId();
        if (currtid == null && lastrb != null && lastrb.equals(tid)) {
            _logger.log(Level.INFO, _sbr.getString(_sbr.I_TXN_ALREADY_ROLLEDBACK, tid));
            return;
        }
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NOT_FOUND, tid)+
                (currtid == null ?"":" "+_sbr.getString(_sbr.I_CURRENT_TXN, currtid)));
        }
        _txSession.rollback();
    }

    @Override
    public void sendMessage(StompFrameMessage message, String tid)
    throws Exception { 

        StompSenderSession ss = null; 
        if (tid != null) {
            ss = (StompSenderSession)getTransactedSession(tid);
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE,
                "Sending message on transacted session: "+ss+" for transaction "+tid);
            }
        } else {
            ss = getSenderSession();
        }
        ss.sendStompMessage(message);
    }

    @Override
    public void ack10(String subidPrefix, String msgid, String tid)
    throws Exception {
        checkConnection();

        if (tid != null) {
            StompTransactedSession ts = (StompTransactedSession)
                                        getTransactedSession(tid);
            ts.ack10(subidPrefix, msgid);
        } else {
            StompSubscriberSession ss = null;
            for (String subid: _subSessions.keySet()) { 
                if (subid.startsWith(subidPrefix)) {
                    ss = _subSessions.get(subid);
                    ss.ack(msgid);
                    return;
                }
            }
            throw new StompProtocolException(_sbr.getKString(
                _sbr.X_ACK_CANNOT_DETERMINE_SUBSCRIBER, msgid,
                StompFrameMessage.AckHeader.SUBSCRIPTION));
        }
    }

    @Override
    public void ack(String id, String tid, String subid,
                    String msgid, boolean nack)
                    throws Exception {
        if (id == null && subid == null) {
            throw new IllegalArgumentException("ack(): null subid");
        }
        if (nack) {
            throw new StompProtocolException(
            "NACK (non-WebSocket) not implemented");
        }
        if (tid != null) {
            StompTransactedSession ts = (StompTransactedSession)
                                        getTransactedSession(tid);
            ts.ack(subid, msgid);
        } else {
            StompSubscriberSession ss = (StompSubscriberSession)
                                        getSubscriberSession(subid);
            if (ss != null ) {
                ss.ack(msgid);
            } else {
                StompTransactedSession ts = (StompTransactedSession)
                                             getTransactedSession();
                if (ts == null) {
                    throw new StompProtocolException(
                    _sbr.getKString(_sbr.X_SUBSCRIBE_NO_SESSION, subid));
                }
                ts.ack(subid, msgid);
            }
        } 
    }

    /**
     */
    private synchronized StompSession getTransactedSession(String tid) throws Exception { 
        checkConnection();


        if (tid == null) {
            throw new IllegalArgumentException("Unexpected call: null transaction id");
        }

        if (_txSession == null) {
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NO_SESSION, tid));
        }
        String currtid = _txSession.getStompTransactionId();
        if (currtid == null || !currtid.equals(tid)) {
            throw new StompProtocolException(
                _sbr.getKString(_sbr.X_TXN_NOT_FOUND, tid)+
                        (currtid == null ?"":" "+_sbr.getString(_sbr.I_CURRENT_TXN, currtid)));
        }
        return _txSession;
    }

    /**
     */
    private synchronized StompTransactedSession getTransactedSession()
                                                throws Exception { 
        checkConnection();

        if (_txSession == null) return null;

        if (_txSession.getStompTransactionId() == null) return null;

        return _txSession;
    }

    /**
     */
    private synchronized StompSenderSession getSenderSession() throws Exception { 

        Connection conn = _connection;
        checkConnection(conn);

        if (_pubSession == null) {
            _pubSession = new StompSenderSession(this);
        }
        return _pubSession;
   }

   /**
    */
   private synchronized StompSubscriberSession createSubscriberSession(
       String subid, StompAckMode ackMode) 
       throws Exception {

       Connection conn = _connection;
       checkConnection(conn);
       
       if (subid == null) {
           throw new IllegalArgumentException("No subscription id");
       }

       StompSubscriberSession ss = _subSessions.get(subid);
       if (ss != null) {
           throw new StompProtocolException(
               _sbr.getKString(_sbr.X_SUBSCRIBER_ID_EXIST, subid));
       }
       ss = new StompSubscriberSession(subid, ackMode, this);
       _subSessions.put(subid, ss);

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
            sub = ss.createSubscriber(_sph.toStompDestination(stompdest, ss, true),
                                      selector, duraname, nolocal, aout);
        } else {
            StompTransactedSession ts = (StompTransactedSession)getTransactedSession(tid);
            sub = ts.createSubscriber(subid,
                      _sph.toStompDestination(stompdest, ts, true),
                      selector, duraname, nolocal, aout);
        }
        return sub;
    }
 

   /**
    * @return null if not found
    */
   private synchronized StompSubscriberSession getSubscriberSession(
                                              String subid)
                                              throws Exception {
       checkConnection();
       
       if (subid == null) {
           throw new IllegalArgumentException("No subscription id");
       }

       StompSubscriberSession ss = _subSessions.get(subid);
       return ss;
    }

    /**
     * @return subid if duraname not null
     */
    @Override
    public synchronized String closeSubscriber(String subid, String duraname) throws Exception {
        checkConnection();

        StompSubscriberSession ss = null;
        if (duraname == null) {
            ss = _subSessions.get(subid);
            if (ss != null) {
                ss.close();
                _subSessions.remove(subid);
                return null;
            }
        } else {
            if (_clientid == null) {
                throw new StompProtocolException(
                    _sbr.getKString(_sbr.X_UNSUBSCRIBE_NO_CLIENTID, duraname));
            }
            String dn = null;
            for (String sid: _subSessions.keySet()) { 
                ss = _subSessions.get(sid);
                dn = ss.getDurableName();
                if (dn == null) {
                    continue;
                }
                if (dn.equals(duraname)) {
                    ss.closeSubscriber();     
                    ss.getJMSSession().unsubscribe(duraname);
                    ss.close();
                    _subSessions.remove(sid);
                    return sid;
                }
            }
        }
        
        if (_txSession != null) {
            String sid = _txSession.closeSubscriber(subid, duraname);
            if (duraname != null) return sid;
            if (sid != null) return sid;
        } else if (duraname != null) {
            ((StompSenderSession)getSenderSession()).
                getJMSSession().unsubscribe(duraname);
            return null;
        }
        throw new StompProtocolException(
            _sbr.getKString(_sbr.X_SUBSCRIBER_ID_NOT_FOUND, subid)); 
    }

    /**
     *
     */
    private synchronized void checkConnection() throws Exception {
        checkConnection(_connection);
        if (_connectionException) {
            disconnect(false);
            _connectionException = false;
        }
    }

    /**
     *
     */
    private synchronized void checkConnection(Connection conn) throws Exception {
        if (conn == null) {
            throw new StompNotConnectedException(_sbr.getKString(_sbr.X_NOT_CONNECTED));
        }
    }

    public void onException(JMSException e) {
        _logger.log(Level.SEVERE, _sbr.getKString(_sbr.E_ONEXCEPTION_JMS_CONN, _connectionUID, e.getMessage()), e);
        _connectionException = true;
    }

}
