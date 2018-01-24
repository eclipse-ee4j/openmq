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

package com.sun.messaging.bridge.service.jms;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ResourceBundle;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.jms.Destination;
import javax.jms.Session;
import javax.jms.XASession;
import javax.jms.Connection;
import javax.jms.XAConnection;
import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.jms.JMSException;
import javax.jms.ExceptionListener;
import javax.jms.ConnectionMetaData;
import javax.jms.MessageListener;
import javax.transaction.Status; 
import javax.transaction.Transaction; 
import javax.transaction.TransactionManager; 
import javax.transaction.RollbackException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.xa.XAResource; 
import com.sun.messaging.bridge.service.jms.xml.JMSBridgeXMLConstant;
import com.sun.messaging.bridge.service.jms.tx.TransactionManagerImpl;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.api.BridgeCmdSharedResources;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;
import com.sun.messaging.bridge.api.Bridge;

/**
 * A JMS Bridge Link
 *
 * @author amyk
 *
 */
public class Link implements Runnable {

    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public enum LinkState {
        UNINITIALIZED { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_UNINITIALIZED); }},
        STARTING { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STARTING); }},
        STARTED  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STARTED); }},
        STOPPING { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STOPPING); }},
        STOPPED  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STOPPED); }},
        PAUSING  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_PAUSING); }},
        PAUSED   { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_PAUSED); }};

        public abstract String toString(ResourceBundle rb);
        public String toString() {
            return toString(_jbr);
        };
    };

    private Logger _logger = null;

    private TransactionManager _tm = null;
    private Object _sourceCF = null;
    private Object _targetCF = null;

    private Object _sourceDest = null;
    private Object _targetDest = null;

    private MessageConsumer _consumer = null; 
    private MessageProducer _producer = null; 
    private Session _sourceSession = null;
    private Session _targetSession = null;
    private Connection _sourceConn = null;
    private Connection _targetConn = null;

    private String _name = null;
    private Properties _linkAttrs = null;
    private Properties _srcAttrs = null;
    private Properties _tgtAttrs = null;
    private Properties _tgtProps = null;
    private JMSBridge _parent = null;

    private Properties _msgTransformerProps = null;

    private LinkState _state = LinkState.UNINITIALIZED;
    private Thread _thread = null;

    private boolean _targetStayConnected = false;
    private boolean _sourceConnException = false;
    private boolean _targetConnException = false;
    private String _sourceProviderName = null;
    private String _targetProviderName = null;
    private String _sourceDestName = null;
    private String _targetDestName = null;
	private EventNotifier _notifier = null;
    private static final int MIN_TRANSACTION_TIMEOUT = 60; //seconds
    private boolean _enabled = true;
    private boolean _isTransacted = true;
    private MessageTransformer<Message, Message> _msgTransformer = null;
    private boolean _consumeOnTransformError = false;
    private boolean _retainReplyTo = false;
    private FaultInjection _fi = FaultInjection.getInjection();


    private String _targetConnType = null;
    private String _sourceConnType = null;

    private MessageProducer _branchProducer = null;
    private String _targetCurrentDestinationName = null;
    private boolean _unidentifiedProducer = false;

    private boolean _firstTransformerBranchTo = true;
    private boolean _firstTransformerNoTransfer = true;
    private boolean _firstTransformerAsSourceChange = true;
    private int _sourceAttemptInterval = 5; //sec
    private int _targetAttemptInterval = 5; //sec

    public synchronized void init(Properties linkAttrs, 
                                  Properties srcAttrs,
                                  Properties tgtAttrs,
                                  Properties tgtProps,
                                  JMSBridge parent)
                                  throws Exception {
        _linkAttrs = linkAttrs;
        _srcAttrs = srcAttrs;
        _tgtAttrs = tgtAttrs;
        _tgtProps = tgtProps;
        _parent = parent;
        _notifier = parent._notifier;

        _msgTransformerProps = (_tgtProps == null ? (new Properties()): (new Properties(_tgtProps)));
        _msgTransformerProps.setProperty(JMSBridge.BRIDGE_NAME_PROPERTY, _parent.getBridgeName());

        if (_sourceDest == null || _targetDest == null ||
            _sourceCF == null || _targetCF == null) { 
            throw new IllegalStateException(_jbr.getKString(_jbr.X_SOURCE_TARGET_NO_INFO,
            (_sourceCF == null ? _jbr.getString(_jbr.M_SOURCE_1):_jbr.getString(_jbr.M_TARGET_1)), _name));
        }
        if (_logger == null) {
            throw new IllegalStateException("No logger set for "+this);
        }
        _enabled = Boolean.valueOf(_linkAttrs.getProperty(
                   JMSBridgeXMLConstant.Link.ENABLED,
                   JMSBridgeXMLConstant.Link.ENABLED_DEFAULT)).booleanValue();

        _isTransacted = Boolean.valueOf(_linkAttrs.getProperty(
                        JMSBridgeXMLConstant.Link.TRANSACTED,
                        JMSBridgeXMLConstant.Link.TRANSACTED_DEFAULT)).booleanValue();

        if (_isTransacted && 
            !(_sourceCF instanceof XAConnectionFactory)) {
            String[] eparam = {"XAConnectionFactory", _jbr.getString(_jbr.M_TRANSACTED), this.toString()};
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_REQUIRED_FOR_LINK, eparam));
        }
        if (!_isTransacted && 
            (_sourceCF instanceof XAConnectionFactory)) {
            String[] eparam = {"ConnectionFactory", _jbr.getString(_jbr.M_NONTRANSACTED), this.toString()};
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_REQUIRED_FOR_LINK, eparam));
        }

        String cn = _tgtAttrs.getProperty(JMSBridgeXMLConstant.Target.MTFCLASS);
        if (cn != null ) {
            _msgTransformer = (MessageTransformer<Message, Message>)
                                     Class.forName(cn).newInstance();
        }
        _consumeOnTransformError = Boolean.valueOf(_tgtAttrs.getProperty(
                         JMSBridgeXMLConstant.Target.CONSUMEONTRANSFORMERROR,
                         JMSBridgeXMLConstant.Target.CONSUMEONTRANSFORMERROR_DEFAULT)).booleanValue();

        _retainReplyTo = Boolean.valueOf(_tgtAttrs.getProperty(
                         JMSBridgeXMLConstant.Target.RETAINREPLYTO,
                         JMSBridgeXMLConstant.Target.RETAINREPLYTO_DEFAULT)).booleanValue();

        String val = _parent.getCFAttributes(_sourceCF).getProperty(
                                             JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL,
                                             JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT);
        if (val != null) {
            _sourceAttemptInterval = Integer.parseInt(val);
        }

        val = _parent.getCFAttributes(_targetCF).getProperty(
                                      JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL,
                                      JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT);
        if (val != null) {
            _targetAttemptInterval = Integer.parseInt(val);
        }

        if (_sourceCF instanceof XAConnectionFactory) {

            _tm = _parent.getTransactionManager();
            if (_tm == null) {
                throw new IllegalStateException("No transaction manager for XA in "+this);
            }
            if (_parent.supportTransactionTimeout()) {
                if (_parent.getTransactionTimeout() < MIN_TRANSACTION_TIMEOUT) {
			        throw new IllegalArgumentException(
                    "Transaction timeout "+_parent.getTransactionTimeout()+" is too small");
                }
                _logger.log(Level.INFO, "Link "+this+" uses TM "+
                            _tm.getClass().toString()+", transaction timeout "+
                            _parent.getTransactionTimeout()+" seconds");
            } else {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_LINK_USE_TM, this.toString(),
                                                       _tm.getClass().toString()));
            }
        }

        String[] param = { this.toString(), (_linkAttrs == null ? "null":_linkAttrs.toString()),
                                            (_srcAttrs == null ? "null":_srcAttrs.toString()),
                                            (_tgtAttrs == null ? "null":_tgtAttrs.toString()),
                                            (_tgtProps == null ? "null":_tgtProps.toString()) };
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_INITED_LINK_WITH, param)); 

        _state = LinkState.STOPPED;
    }

    /**
     *  To be called before start() and after init()
     *  only needed for certain external TM like Atomikos
     */
    public void registerXAResources() throws Exception {

        if (!(_sourceCF instanceof XAConnectionFactory)) return;

           XAResourceHandle srh =  new XAResourceHandle(false);
           XAResourceHandle trh =  new XAResourceHandle(true);
           Transaction tx = null;
           try {
           _logger.log(Level.INFO, _jbr.getString(_jbr.I_REGISTER_RMS_FOR, this.toString()));
           initSource(false, false, false);
           initTarget(false, false, false);
           srh.xar = ((XASession)_sourceSession).getXAResource();
           if (((Refable)_targetCF).isEmbeded()&&((Refable)_sourceCF).isEmbeded()) {
               trh.xar = srh.xar;
           } else {
               trh.xar = ((XASession)_targetSession).getXAResource();
               if (trh.xar.getClass().getName().equals(srh.xar.getClass().getName())) {
                   trh.xar = new XAResourceImpl(trh.xar);
                   _logger.log(Level.INFO, _jbr.getString(_jbr.I_USE_XARESOURCE_WRAP_TARGET,
                                                               trh.xar.getClass().getName()));
               }
           }
           _tm.begin();
           tx = _tm.getTransaction();
           if (!doEnlistAndRollbackOnError(tx, _tm, srh)) {
               throw srh.ex;                       
           }
           if (srh.xar != trh.xar) {
               if (!doEnlistAndRollbackOnError(_tm.getTransaction(), _tm, trh)) {
               throw trh.ex;                       
               }
           }

           } finally {
              doFinally(tx, _tm, srh, trh);
              closeSource();
              closeTarget();
           }
           _logger.log(Level.INFO, _jbr.getString(_jbr.I_REGISTERED_RMS_FOR, this.toString()));
    }

    public boolean isEnabled() {
        if (_state == LinkState.UNINITIALIZED) {
            throw new IllegalStateException("Link is not initialized");
        }
        return _enabled;
    }

    public boolean isTransacted() {
        if (_state == LinkState.UNINITIALIZED) {
            throw new IllegalStateException("Link is not initialized");
        }
        return _isTransacted;
    }

    public void enable() throws Exception {
        if (isEnabled()) {
           _logger.log(Level.INFO, this+" is already enabled");
           return;
        }
        _enabled = true; //XXX todo
    }

    public synchronized void start(boolean doReconnect) throws Exception {

        if (_state == LinkState.UNINITIALIZED) {
            throw new IllegalStateException("Link not initialized !"); 
        }
        if (_state == LinkState.STARTED) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_ALREADY_STARTED, this.toString()));
            return;
        }
        _state = LinkState.STARTING;

        try {
            initSource(true, doReconnect, true);
            initTarget(true, doReconnect, true);

            _thread = new Thread(this);
            _thread.setDaemon(true);
            _thread.setName(toString());
            _thread.start();

        } catch (Throwable t) {
            _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_UNABLE_START, this.toString(), t.getMessage()), t); 
            try {
                stop(!doReconnect);
            } catch (Throwable t1) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.E_UNABLE_STOP_AFTER_START_FAILURE, this.toString()), t1);
            }
            throw new Exception(t);
        }
    }

    public synchronized void postStart() throws Exception {
        if (_state == LinkState.STARTED) {
            return;
        }
        if (_state != LinkState.STARTING) {
            throw new IllegalStateException(_jbr.getKString(_jbr.X_HAS_STATE, this.toString(), _state.toString()));
        }
        try {
            resume(false);
            _state = LinkState.STARTED;
        } catch (Exception e) {
           try {
               stop();
           } catch (Throwable t) {
               _logger.log(Level.WARNING, 
               _jbr.getKString(_jbr.E_UNABLE_STOP_AFTER_POSTSTART_FAILURE, this.toString()), t);
           }
           throw e;
        }
    }

    /**
     */
     public synchronized void pause() throws Exception {
        if (_sourceConn == null) {
            throw new IllegalStateException("Source connection not created");
        }
        if (_state == LinkState.UNINITIALIZED || 
            _state == LinkState.STOPPED || _state == LinkState.STOPPING) {
            throw new IllegalStateException(_jbr.getKString(_jbr.X_PAUSE_NOT_ALLOWED_IN_STATE,
                                                            this.toString(), _state.toString()));
        }
        if (_state == LinkState.PAUSED || _state == LinkState.PAUSING) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_ALREADY_PAUSED, this.toString()));
            return;
        }
        _state = LinkState.PAUSING;
        try {
            _sourceConn.stop();
        } catch (Exception e) {
            _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_UNABLE_PAUSE_SOURCE_CONN, this.toString()), e);
            try {
            stop(); 
            } catch (Exception e1) {
            _logger.log(Level.SEVERE, _jbr.getString(_jbr.E_UNABLE_STOP_AFTER_PAUSE_FAILURE, this.toString()), e);
            }
            throw e;
        }
        _state = LinkState.PAUSED;
    }

    /**
     * to be called immediately after start()
     */
     public synchronized void resume(boolean resume) throws Exception {
        if (_sourceConn == null) {
            throw new IllegalStateException("Source connection not created");
        }
        if (_state == LinkState.UNINITIALIZED || 
            _state == LinkState.STOPPED || _state ==  LinkState.STOPPING) {
            String[] eparam = {(resume ? _jbr.getString(_jbr.M_RESUMING_LINK):_jbr.getString(_jbr.M_STARTING_SOURCE_CONN)),
                               this.toString(), _state.toString()};
            throw new IllegalStateException(_jbr.getKString(_jbr.X_NOT_ALLOWED_IN_STATE, eparam));
        }
        if (resume) {
            if (_state ==  LinkState.STARTING || _state == LinkState.STARTED) { 
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_ALREADY_RUNNING, this.toString()));
                return;
            }
        } else if (_state == LinkState.PAUSING || _state == LinkState.PAUSED) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_IGNORE_START_SOURCE_REQUEST, this.toString(), _state.toString()));
            return;
        }

        try {
            _sourceConn.start();
        } catch (Exception e) {
            if (resume) {
            _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_UNABLE_RESUME_SOURCE_CONN, this.toString()), e);
            } else {
            _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_UNABLE_START_SOURCE_CONN, this.toString()), e);
            }
            try {
            stop(); 
            } catch (Exception e1) {
            _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_UNABLE_STOP_LINK_AFTER, this.toString(),
                    (resume ? _jbr.getString(_jbr.M_RESUME):_jbr.getString(_jbr.M_START))), e);
            }
            throw e;
        }
        _state = LinkState.STARTED;
    }

    public void stop() throws Exception {
        stop(false);
    }

    public void stop(boolean waitExit) throws Exception {

        _notifier.notifyEvent(EventListener.EventType.LINK_STOP, this);

        Thread thr = null;
        Throwable t = null;

        synchronized(this) {

        _state = LinkState.STOPPING;

        thr = _thread;

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_STOPPING_LINK, this.toString()));

        if (_thread != null && Thread.currentThread() != _thread)  {
            _thread.interrupt();
        }

        try {
            if (_sourceConn != null) _sourceConn.close();
        } catch (Throwable t0) {
            t = t0;
        }
        try {
            closeTarget();
        } catch (Throwable t1) {
            if (t == null) t = t1;
        }

        if (t == null) {
            _state = LinkState.STOPPED;
            notifyAll();
            _thread = null;
        }

        }
        if (thr != null && Thread.currentThread() != thr)  {
            thr.interrupt();
            if (!waitExit) {
               thr.join(30000);
            } else {
               while (true) {
                   try {
                   thr.join(60000);
                   _logger.log(Level.INFO, _jbr.I_WAITING_LINK_THREAD_EXIT, this.toString()); 
                   } catch (InterruptedException e) {}
               }
            }
        }
        if (t != null) {
            throw new  Exception(t);
        }
    }

    private void initSource() throws Exception {
        initSource(false, true, true);
    }

    private synchronized void initSource(boolean start, 
                                         boolean doReconnect,
                                         boolean checkState) throws Exception {
        if (checkState) {
            if (_state == LinkState.STOPPING || _state == LinkState.STOPPED) {
                throw new JMSException(_jbr.getKString(_jbr.X_LINK_IS_STOPPED, this.toString()));
            }
        }

        _sourceConnException = false;

        String val = _srcAttrs.getProperty(JMSBridgeXMLConstant.Source.CLIENTID);
        _sourceConnType = "D";
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_DEDICATED_SOURCE_CONN, 
                          (val == null ? "":"[ClientID="+val+"]"), this.toString()));

        EventListener l = new EventListener(this);
        try {
             _notifier.addEventListener(EventListener.EventType.LINK_STOP, l);
             _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, l);
             _sourceConn = JMSBridge.openConnection(_sourceCF, _parent.getCFAttributes(_sourceCF), 
                                          _jbr.getString(_jbr.M_SOURCE), this, l, _logger, doReconnect);
        } finally {
             _notifier.removeEventListener(l);
        }
        if (val != null) {
            try {
            _sourceConn.setClientID(val);
            } catch (JMSException e) {
            _logger.log(Level.WARNING, "Set client id "+ val+" to source connection failed in "+this+", try again ...", e);
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            Thread.sleep(_sourceAttemptInterval);
            _sourceConn.setClientID(val);
            }
        }

        _sourceConn.stop();

        _sourceConn.setExceptionListener( new ExceptionListener() { 

            public void onException(JMSException exception) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_CONN_EXCEPTION_OCCURRED,
                            _jbr.getString(_jbr.M_SOURCE_1), this.toString()), exception);
                if (_targetConn instanceof PooledConnection) {
                    ((PooledConnection)_targetConn).invalid();
                } else if (_targetConn instanceof SharedConnection) {
                    ((SharedConnection)_targetConn).invalid();
                }
                _sourceConnException = true;
            }
        });
        
        try {
            ConnectionMetaData md = _sourceConn.getMetaData();
            _sourceProviderName = md.getJMSProviderName();
        } catch (Exception e) {
            _sourceProviderName = null;
            _logger.log(Level.WARNING, 
            "Unable to get source JMSProvider from conn "+_sourceConn+
            " in "+this+": "+e.getMessage());
        }

        if (_sourceConn instanceof XAConnection) {
            _sourceSession = ((XAConnection)_sourceConn).createXASession();
            XAResource xar = ((XASession)_sourceSession).getXAResource();
            String rm = ((Refable)_sourceCF).getRef();
            try {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_REGISTER_RM, rm, xar.toString()));
                _parent.getTransactionManagerAdapter().registerRM(rm,  xar);
            } catch (Exception e) {
                String[] eparam = {xar.toString(), rm, this.toString()};
               _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_REGISTER_SOURCE_XARESOURCE_FAILED, eparam), e);
               throw e;
            }
        } else {
            _sourceSession = _sourceConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        }
        String selector = _srcAttrs.getProperty(JMSBridgeXMLConstant.Source.SELECTOR);

        Object dest = _sourceDest;
        if (_sourceDest instanceof AutoDestination) {
             AutoDestination ad = (AutoDestination)_sourceDest;
             if (ad.isQueue()) {
                 dest = _sourceSession.createQueue(ad.getName());
             } else {
                 dest = _sourceSession.createTopic(ad.getName()); 
             }
        }
        if (dest instanceof Topic) {
            String duraname = _srcAttrs.getProperty(JMSBridgeXMLConstant.Source.DURABLESUB);
            if (duraname != null) {
                _consumer = _sourceSession.createDurableSubscriber(
                            (Topic)dest, duraname, selector, true);
            } else {
                _consumer = _sourceSession.createConsumer(
                            (Topic)dest, selector, true);
            }
        } else if (dest instanceof Queue) {
            _consumer = _sourceSession.createConsumer((Queue)dest, selector);
        } else {
           throw new IllegalArgumentException(
           "Unknown source destination type: "+dest.getClass().getName()+ " in "+this);
        }
    }
    
    private void initTarget() throws Exception {
        initTarget(false, true, true);
    }

    private synchronized void initTarget(boolean start, 
                                         boolean doReconnect,
                                         boolean checkState) throws Exception {
        if (checkState) {
            if (_state == LinkState.STOPPING || _state == LinkState.STOPPED) {
                throw new JMSException("Link "+this+" is stopped");
            }
        }
        _targetConnException = false;

        String val = _tgtAttrs.getProperty(JMSBridgeXMLConstant.Target.STAYCONNECTED, 
                                           JMSBridgeXMLConstant.Target.STAYCONNECTED_DEFAULT);
        _targetStayConnected = Boolean.valueOf(val).booleanValue();

        val = _tgtAttrs.getProperty(JMSBridgeXMLConstant.Target.CLIENTID);
        if (val != null || _targetStayConnected) {

            _targetConnType = "D";
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_DEDICATED_TARGET_CONN,
                             (val == null ? "":"[ClientID="+val+"]"), this.toString()));
  
            EventListener l = new EventListener(this);
            try {
                _notifier.addEventListener(EventListener.EventType.LINK_STOP, l);
                _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, l);
                _targetConn = JMSBridge.openConnection(_targetCF, _parent.getCFAttributes(_targetCF), 
                                              _jbr.getString(_jbr.M_TARGET), this, l, _logger, doReconnect);
            } finally {
                _notifier.removeEventListener(l);
            }
            if (val != null) {
            try {
                _targetConn.setClientID(val);
            } catch (JMSException e) {
                _logger.log(Level.WARNING, "Set client id "+ val+" to target connection failed in "+this+", try again ...", e);
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                Thread.sleep(_targetAttemptInterval);
                _targetConn.setClientID(val);
            }
            }

        } else if (!start) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_GET_TARGET_CONN, this.toString()));
            _targetConn = _parent.obtainConnection(_targetCF, _jbr.getString(_jbr.M_TARGET), this, start);
        } else {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_DEFER_GET_TARGET_CONN, this.toString()));
            return;
        }
        if (_targetConn instanceof PooledConnection) {
            _targetConnType = "P";
        } else if (_targetConn instanceof SharedConnection) {
            _targetConnType = "S";
        }

        _targetConn.setExceptionListener( new ExceptionListener() { 

            public void onException(JMSException exception) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_CONN_EXCEPTION_OCCURRED,
                            _jbr.getString(_jbr.M_TARGET_1), this.toString()), exception); 
                if (_targetConn instanceof PooledConnection) {
                    ((PooledConnection)_targetConn).invalid();
                } else if (_targetConn instanceof SharedConnection) {
                    ((SharedConnection)_targetConn).invalid();
                }
                _targetConnException = true;
            }
        });

        try {
            ConnectionMetaData md = _targetConn.getMetaData();
            _targetProviderName = md.getJMSProviderName();
        } catch (Exception e) {
            _targetProviderName = null;
            _logger.log(Level.WARNING, 
            "Unable to get target JMSProvider from conn "+_targetConn+
            " in "+this+": "+e.getMessage());
        }

        if (_targetConn instanceof XAConnection) {
            _targetSession = ((XAConnection)_targetConn).createXASession();
            XAResource xar = ((XASession)_targetSession).getXAResource();
            String rm = ((Refable)_targetCF).getRef();
            try {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_REGISTER_RM, rm, xar.toString()));
                _parent.getTransactionManagerAdapter().registerRM(rm,  xar);
            } catch (Exception e) {
                String[] eparam = {xar.toString(), rm, this.toString()};
               _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_REGISTER_TARGET_XARESOURCE_FAILED, eparam), e);
               throw e;
            }
        } else {
            _targetSession = _targetConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        }
        if (_targetDest instanceof Destination) {
            _producer = _targetSession.createProducer((Destination)_targetDest);
        } else if (_targetDest instanceof AutoDestination) {
            AutoDestination ad = (AutoDestination)_targetDest;
            if (ad.isQueue()) {
                _producer = _targetSession.createProducer(
                                 _targetSession.createQueue(ad.getName()));
            } else {
                _producer = _targetSession.createProducer(
                                 _targetSession.createTopic(ad.getName()));
            }
        } else if (_targetDest.equals(JMSBridgeXMLConstant.Target.DESTINATIONREF_AS_SOURCE)) {
           _unidentifiedProducer = true;
           _producer = null;
        } else {
           throw new IllegalArgumentException(
           "Unknown target destination type: "+_targetDest.getClass().getName()+ " in "+this);
        }
        if (_producer != null && 
            _producer instanceof com.sun.messaging.jmq.jmsclient.MessageProducerImpl) {
            ((com.sun.messaging.jmq.jmsclient.MessageProducerImpl)_producer)._setForJMSBridge();
        }
    }

    private MessageProducer createProducer(Object d, String mid, boolean targetd) throws Exception {
        MessageProducer p = null;

        Object dest = d;

        if (d instanceof String) {
            dest = _parent.createDestination((String)d);
        }  
        if (dest instanceof Destination) {
            Destination td = null;
            if (targetd) {
                td = (Destination)dest;
            } else {
                if (dest instanceof Queue) {
                    td = _targetSession.createQueue(((Queue)dest).getQueueName());
                } else if (dest instanceof Topic) {
                    td = _targetSession.createTopic(((Topic)dest).getTopicName());
                } else {
                    throw new JMSException("Unsupported JMS Destination type: "+d);
                }
            }
            p = _targetSession.createProducer(td);

        } else if (dest instanceof AutoDestination) {
            AutoDestination ad = (AutoDestination)dest;
            if (ad.isQueue()) {
                p = _targetSession.createProducer(_targetSession.createQueue(ad.getName()));
            } else {
                p = _targetSession.createProducer(_targetSession.createTopic(ad.getName()));
            }
        } else {
           throw new IllegalArgumentException(
           "Unsupported destination type: "+dest.getClass().getName()+
           " to create target producer for message "+mid+" in "+this);
        }

        if (p != null && 
            p instanceof com.sun.messaging.jmq.jmsclient.MessageProducerImpl) {
            ((com.sun.messaging.jmq.jmsclient.MessageProducerImpl)p)._setForJMSBridge();
        }

        return p;
    }

    private synchronized void closeTarget() {
        _targetConnException = false;

        if (_targetConn != null) {
            if (_targetConn instanceof SharedConnection ||
                _targetConn instanceof PooledConnection) {
                try {
                    _targetSession.close();
                } catch (Throwable t) {
                    _logger.log(Level.WARNING, 
                    "Unable to close target Session in "+this, t);
                } finally {
                    try {

                    _parent.returnConnection(_targetConn, _targetCF);

                    } catch (Throwable t) {
                    _logger.log(Level.WARNING, 
                    "Unable to return target connection "+_targetConn+" in "+this, t);
                    }
                }
            } else {
                try {
                    _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_TARGET_CONNECTION, 
                                _targetConn.getClass().getName()+'@'+Integer.toHexString(_targetConn.hashCode()),
                                this.toString()));
                    _targetConn.close(); 
                } catch (Throwable t) {
                    _logger.log(Level.WARNING, 
                    "Unable to close target connection "+_targetConn+" in "+this, t);
                }
            }
            _targetConn = null;
        }
    }

    private synchronized void closeSource() {
        _sourceConnException = false;

        if (_sourceConn != null) {
            if (_sourceConn instanceof SharedConnection ||
                _sourceConn instanceof PooledConnection) {
                try {
                    _sourceSession.close();
                } catch (Throwable t) {
                    _logger.log(Level.WARNING, 
                    "Unable to close source Session in "+this, t);
                } finally {
                    try {

                    _parent.returnConnection(_sourceConn, _sourceCF);

                    } catch (Throwable t) {
                    logWarning("Unable to return source connection "+_sourceConn+" in "+this, t);
                    }
                }
            } else {
                try {
                    _logger.log(Level.INFO, _jbr.getString(_jbr.I_CLOSE_SOURCE_CONNECTION, 
                                _sourceConn.getClass().getName()+'@'+Integer.toHexString(_sourceConn.hashCode()),
                                this.toString()));
                    _sourceConn.close(); 
                } catch (Throwable t) {
                    logWarning("Unable to close source connection "+_sourceConn+" in "+this, t);
                }
            }
            _sourceConn = null;
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("link("+getName()+")[");
        sb.append(getSourceString());
        sb.append("-->");
        sb.append(getTargetString());
        sb.append("]");
        return sb.toString();
    }

    public String getSourceString() {
        if (_state == LinkState.UNINITIALIZED) return "";
        return (((Refable)_sourceCF).getRef()+":"+(_sourceConnType == null ? "":_sourceConnType)+":"+getSourceDestinationName());
    }

    public String getTargetString() {
        if (_state == LinkState.UNINITIALIZED) return "";
        return (((Refable)_targetCF).getRef()+":"+(_targetConnType == null ? "":_targetConnType)+":"+getTargetDestinationName());
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getSourceProviderName() {
        String pn = _sourceProviderName; 
        if (pn != null) return pn;

        return ((Refable)_sourceCF).getRefed().getClass().getName();
    }

    public String getTargetProviderName() {
        String pn = _targetProviderName; 
        if (pn != null) return pn;

        return ((Refable)_targetCF).getRefed().getClass().getName();
    }

    public String getSourceDestinationName() {
        if (_sourceDestName != null) return _sourceDestName;

        Object dest = _sourceDest;
        try {

        if (dest instanceof Queue) {
            return (_sourceDestName = "queue:"+((Queue)(dest)).getQueueName());
        } else if (dest instanceof Topic) {
            return (_sourceDestName = "topic:"+((Topic)(dest)).getTopicName());
        } else {
            return (_sourceDestName = dest.toString());
        }

        } catch (Throwable t) {
        _logger.log(Level.WARNING, 
        "Unable to get source destination name in "+this, t);
        return (_sourceDestName = dest.toString());
        }
    }

    public String getTargetDestinationName() {
        if (_targetDestName == null) { 
           _targetDestName =  toDestinationName(_targetDest);
        }
        return _targetDestName; 
    }

    private String toDestinationName(Object d) {
        Object dest = d;
        try {

        if (dest instanceof Queue) {
            return ("queue:"+((Queue)(dest)).getQueueName());
        } else if (d instanceof Topic) {
            return ("topic:"+((Topic)(dest)).getTopicName());
        } else {
            return (dest.toString());
        }

        } catch (Throwable t) {
        _logger.log(Level.WARNING, 
        "Unable to get destination from object "+d+" in "+this, t);
        return (dest.toString());
        }
    }

    public String getTargetCurrentDestinationName() {
        if (_targetCurrentDestinationName == null) {
            return getTargetDestinationName();
        }
        return _targetCurrentDestinationName;
    }

    public void setSourceConnectionFactory(Object cf) {
        _sourceCF = cf;
    }

    public void setTargetConnectionFactory(Object cf) {
        _targetCF = cf;
    }

    public void setSourceDestination(Object dest) {
        _sourceDest = dest;
    }

    public void setTargetDestination(Object dest) { 
        _targetDest = dest;
    }

    public Object getSourceDestination() {
        return _sourceDest;
    }

    public Object getTargetDestination() {
        return _targetDest;
    }

    public void setLogger(Logger l) {
        _logger = l;
    }

    private final Object _listenerLock = new Object();

    public void run() {
        try {

        if (_sourceConn instanceof XAConnection) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_RUNNING_XA_CONSUMER, this.toString()));
            runTransacted();
        } else {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_RUNNING_NONTXN_CONSUMER, this.toString()));
            runNonTransacted();
        }

        } catch (Throwable t) {
            Level l = Level.WARNING;
            if (_state != LinkState.STOPPING && 
                _state != LinkState.STOPPED) {
                l = Level.SEVERE; 
            }
            if (t instanceof InterruptedException) {
            _logger.log(l, "Link thread is interrupted in "+this);
            } else {
            _logger.log(l, "Runtime exception in "+this, t);
            }

        } finally {
            closeSource();
            closeTarget();
        }
        if (_state != LinkState.STOPPING && _state != LinkState.STOPPED) {
            try {
                stop();
            } catch (Throwable t) {
                _logger.log(Level.WARNING, "Exception in stopping link "+this+": "+t.getMessage());
            }
        }

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_LINK_THREAD_EXIT, this.toString()));
    }

    static class XAResourceHandle {

        XAResource xar = null;
        boolean enlisted = false; 
        boolean delisted = true; 
        boolean istarget = false;
        RuntimeException ex = null;

        public XAResourceHandle(boolean target) {
            istarget = target;
        }

        public String toString() {
            return "["+(istarget ? _jbr.getString(_jbr.M_TARGET):_jbr.getString(_jbr.M_SOURCE))+"]"+xar;
        }
    }

    private static final int MAX_CONSECUTIVE_THROWABLES = 5;

    private void runTransacted() throws Throwable {
        long msgCount = 0;
   
        long receiveTimeout = 0L;
        if (_parent.supportTransactionTimeout()) {
           receiveTimeout = (((long)_parent.getTransactionTimeout())/(long)2)*(long)1000;
        }

        int consecutiveThrowables = 0;

        while (_state != LinkState.STOPPING && _state != LinkState.STOPPED) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(
                _jbr.getKString(_jbr.X_LINK_INTERRUPTED, this.toString()));
            }

            Transaction transaction = null;
            XAResourceHandle srh = new XAResourceHandle(false);
            XAResourceHandle trh = new XAResourceHandle(true);
            Message m = null;
            MessageHeaders srcmhs  = null;
            String mid = null;
            Throwable currentThrowable = null;
            _branchProducer = null;
            _targetCurrentDestinationName = null;
            try {

            if (_sourceConnException) {
                closeSource();
                initSource();
				resume(false);
            }

            if (_targetConnException) {
                closeTarget();
                if (_targetStayConnected) {
                    initTarget();
                }
            }

            srh.xar = ((XASession)_sourceSession).getXAResource();

            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "BEGIN transaction in "+this);
            }
            try {
                _tm.begin();
            } catch (Exception e) {
                currentThrowable = e;
                _logger.log(Level.SEVERE, "Unable to start transaction in"+this, e);
                _sourceConnException = true;
                _targetConnException = true;
                continue;
            }

            try {
                transaction = _tm.getTransaction();
            } catch (Exception e) {
                currentThrowable = e;
                _logger.log(Level.SEVERE, "Exception to get transaction in"+this, e);
                _sourceConnException = true;
                _targetConnException = true;
                continue;
            }

            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "enlist source in transaction "+transaction+" in "+this);
            }
            if (!doEnlistAndRollbackOnError(transaction, _tm, srh)) { 
                currentThrowable = srh.ex; 
                continue;
            }

            try {
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, 
                    "Receiving("+receiveTimeout+") message from source in transaction "+transaction+" in "+this);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_RECEIVE_1, null, "javax.jms.JMSException", true);
                }
                m = _consumer.receive(receiveTimeout);
                if (m  == null) {
                    try {
                        String logmsg = ((receiveTimeout > 0L)? 
                                         _jbr.getKString(_jbr.W_SOURCE_CONN_CLOSED_OR_RECEIVE_TIMEOUT, this.toString()):
                                         _jbr.getKString(_jbr.W_SOURCE_CONN_CLOSED, this.toString()));
                        _logger.log(Level.WARNING, logmsg);
                    } catch (Throwable t) {}
                    try {
                        _tm.rollback();
                    } catch (Throwable t1) {
                        try {
                        if (receiveTimeout == 0L) {
                            _logger.log(Level.FINE, "Unable to rollback transaction "+transaction+
                                         " on closed source connection or receive() timeout ", t1);
                        } else {
                            logWarning("Unable to rollback transaction "+transaction+
                                        " on closed source connection or receive() timeout ", t1);
                        }
                        } catch (Throwable t) {}
                    }
                    continue;
                }

                msgCount++;
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, 
                    "Received message "+m+" from source in transaction "+transaction+" in "+this+", msgCount="+msgCount);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_RECEIVE_2, null, "javax.jms.JMSException", true);
                }
            } catch (Throwable t) {
                currentThrowable = t;
                logWarning("Exception in receiving message in "+this, t);
                try {
                _tm.rollback();
                } catch (Throwable t1) {
                logWarning("Exception on rollback transaction "+transaction+" on receiving failure", t1);
                }
                continue;
            }
            if (!isTransactionActive(transaction, _tm)) continue;

            
            srcmhs  = MessageHeaders.getMessageHeaders(m);
            mid = srcmhs.mid;
            long ttl  = srcmhs.expiration;
            if (ttl != 0L) {
                ttl = ttl - System.currentTimeMillis();
            }
            if (ttl < 0) {
                handleExpiredMessage(m, mid, transaction, _tm, srh);
                continue;
            }
            if (!isTransactionActive(transaction, _tm)) continue;

            if (!_targetStayConnected) {
                try {
                    initTarget();
                } catch (Exception e) {
                    _logger.log(Level.SEVERE, "Unable to connect to target in "+this, e);
                    try {
                        _tm.rollback();
                    } catch (Throwable t1) {
                        _logger.log(Level.WARNING, 
                        "Unable to rollback transaction "+transaction+" on init target failure for message "+mid+" in "+this, t1);
                    }
                    throw e;   
                }
            }
            if (!isTransactionActive(transaction, _tm)) continue;

            Message sm = null;
            String midSent = null;

            try {
                sm = handleMessageTransformer(m, srcmhs);
            } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                        _jbr.getString(_jbr.W_STOP_LINK_BECAUSE_OF, this, t.getMessage()), t); 
                throw t;
            }
            if (sm == null) {
                _logger.log(Level.WARNING, _jbr.getString(_jbr.W_CONSUME_NO_TRANSFER, mid, this));
                if (!isTransactionActive(transaction, _tm)) continue;

            } else {

            if (((Refable)_targetCF).isEmbeded()&&((Refable)_sourceCF).isEmbeded()) {
                trh.xar = srh.xar;
            } else {
                trh.xar = ((XASession)_targetSession).getXAResource();
                if (!(_tm instanceof TransactionManagerImpl) &&
                    trh.xar.getClass().getName().equals(srh.xar.getClass().getName())) {
                    trh.xar = new XAResourceImpl(trh.xar);
                    _logger.log(Level.INFO,
                    "Use XAResource wrapper "+trh.xar+" for target XAResource "+trh.xar.getClass().getName());
                }
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "enlist target in transaction "+transaction+" in "+this);
                }
                if (!doEnlistAndRollbackOnError(transaction, _tm, trh)) {
                    currentThrowable = trh.ex;
                    continue;
                }
            }

            try {
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Sending message "+mid+" to target in transaction "+transaction+" in "+this);
                }

                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_SEND_1, null, "javax.jms.JMSException", true);
                }

                MessageProducer producer = _producer;
                if (_branchProducer != null) {
                    producer = _branchProducer;
                }
                if (!getTargetCurrentDestinationName().equals(toDestinationName(producer.getDestination()))) {
                    throw new BridgeException("Unexpected target producer's destination name "+
                        toDestinationName(producer.getDestination())+": not match current target destination name "+
                        getTargetCurrentDestinationName());
                }
                
                try {
                    if (_parent.needTagBridgeName()) {
                        _parent.tagBridgeName(sm, _sourceSession.createMessage());
                    }
                    producer.send(sm, srcmhs.deliverymode, srcmhs.priority, ttl);
                    midSent = sm.getJMSMessageID();
                } finally {
                    if (_branchProducer != null) {
                        try {
                            _branchProducer.close();
                        } catch (Throwable t) {
                            _logger.log(Level.WARNING,  
                            "Closing temporary target producer failed: "+ t.getMessage()+" in "+this, t);
                        } finally {
                            _branchProducer = null;
                        }
                    }
                    MessageHeaders.resetMessageHeaders(m, srcmhs);
                }

                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Sent message "+mid+" to target in transaction "+transaction+" in "+this);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_SEND_2, null, "javax.jms.JMSException", true);
                }
            } catch (Throwable t) {
                currentThrowable = t;
                _logger.log(Level.SEVERE, 
                "Unable to send message "+mid+" to target in "+this, t);
                try {
                 _tm.rollback();
                } catch (Throwable t1) {
                _logger.log(Level.WARNING, 
                "Unable to rollback transaction "+transaction+" on send failure for message "+mid+" in "+this, t1);
                }
                continue;
            }

            if (!isTransactionActive(transaction, _tm)) continue;

            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "delist "+trh+" in transaction "+transaction+" in "+this);
            }
            if (trh.xar != srh.xar) {
                if (!doDelistAndRollbackOnError(transaction, _tm, trh)) {
                    currentThrowable = trh.ex;
                    continue;
                }
            }

            } //sm != null

            if (!isTransactionActive(transaction, _tm)) continue;

            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "delist "+srh+" in transaction "+transaction+" in "+this);
            }
            if (!doDelistAndRollbackOnError(transaction, _tm, srh)) {
                currentThrowable = srh.ex;
                continue;
            }

            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "COMMIT transaction "+transaction+" for message "+mid+" in "+this);
            }
            try {
                _tm.commit();
                String[] param = { mid, midSent, this.toString() };
                if (_parent.logMessageTransfer()) {
                    _logger.log(Level.INFO,   _jbr.getString(_jbr.I_MESSAGE_TRANSFER_SUCCESS, param));
                } else if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE,   _jbr.getString(_jbr.I_MESSAGE_TRANSFER_SUCCESS, param));
                }
            } catch (RollbackException e) {
                _logger.log(Level.WARNING,
                "RollbackException on commit transaction "+transaction+" for message "+mid+" in "+this, e);
                sendToDMQ(m, mid, DMQ.DMQReason.COMMIT_FAILURE, e);
                continue;
            } catch (HeuristicMixedException e) {
                _logger.log(Level.WARNING,
                "HeuristicMixedException on commit transaction "+transaction+" for message "+mid+" in "+this, e);
                sendToDMQ(m, mid, DMQ.DMQReason.COMMIT_FAILURE, e);
                continue;
            } catch (HeuristicRollbackException e) {
                _logger.log(Level.WARNING,
                "HeuristicRollbackException on commit transaction "+transaction+" for message "+mid+" in "+this, e);
                sendToDMQ(m, mid, DMQ.DMQReason.COMMIT_FAILURE, e);
                continue;
            } catch (Throwable t) {
                currentThrowable = t;
                _logger.log(Level.SEVERE,
                "Failed to commit transaction "+transaction+" for message "+mid+" in "+this, t);
                sendToDMQ(m, mid, DMQ.DMQReason.COMMIT_FAILURE, t);
                continue;
            }

            } finally {
                doFinally(transaction, _tm, srh, trh);
                if (currentThrowable == null) { 
                    consecutiveThrowables = 0;
                } else {
                    consecutiveThrowables++; 
                    if (consecutiveThrowables > MAX_CONSECUTIVE_THROWABLES) {
                    throw new RuntimeException("Maximum consecutive exceptions exceeded", currentThrowable);
                    }
                 
                }
            }
        }
    }

    private void logWarning(String msg, Throwable t) {
        if (_state == LinkState.STOPPING || _state == LinkState.STOPPED) {
            _logger.log(Level.WARNING, msg+": "+t.getMessage()); 
        } else {
            _logger.log(Level.WARNING, msg, t); 
        }
    }

    private void sendToDMQ(Message m, String mid, DMQ.DMQReason reason, Throwable t) throws Throwable {
        try {
            _parent.toDMQ(m,  mid, reason, t, this);
        } catch (Throwable t1) {
            _logger.log(Level.SEVERE, "Failed to send "+reason+" message "+mid +" to DMQ in "+this, t1);
            throw t1;
        }
        return;
    }

    private void handleExpiredMessage(Message m, String mid, Transaction tx, 
                                      TransactionManager tm, 
                                      XAResourceHandle sourceRH) throws Throwable {

        String[] param = {mid, sourceRH.toString(), tx.toString(), this.toString()};
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_TXN_MESSAGE_EXPIRED, param));
        try {
            sendToDMQ(m, mid, DMQ.DMQReason.MESSAGE_EXPIRED, (Throwable)null);
        } finally {
            try {
                tm.rollback();
            } catch (Throwable t1) {
                _logger.log(Level.WARNING, 
                "Unable to rollback transaction "+tx+" for expired message "+mid+" in "+this, t1);
            }
        }
    }

    private boolean doEnlistAndRollbackOnError(Transaction tx, 
                                              TransactionManager tm, 
                                              XAResourceHandle rh) {
        try {
             rh.enlisted = tx.enlistResource(rh.xar);             
        } catch (RollbackException e) {
            _logger.log(Level.SEVERE, 
            "The transaction "+tx+" is marked rollback only by "+rh+" in "+this ,e );
            try {
                tm.rollback();
            } catch (Throwable t) {
                _logger.log(Level.SEVERE, 
                "Exception in rollback transacion "+tx+" after enlist returned rollback-only in "+this, t);
            } 
            rh.ex = new RuntimeException(
               "The transaction "+tx+" is marked rollback only by "+rh+" in "+this ,e );
            return false;
        } catch (Throwable t) {
             _logger.log(Level.SEVERE, 
            "Exception to enlist "+rh+" to transaction "+tx+" in "+this, t);
            try {
                tm.rollback();
            } catch (Throwable t1) {
                _logger.log(Level.SEVERE, 
                "Exception in rollback transacion "+tx+" after enlist "+rh+" failure in "+this, t1);
            }
            rh.ex =  new RuntimeException(
               "Exception to enlist "+rh+" to transaction "+tx+" in "+this, t);
            return false;
        }
        if (!rh.enlisted) {
            _logger.log(Level.SEVERE, 
            rh+" was not enlisted to transaction "+tx+" successfully in "+this);
            try {
                tm.rollback();
            } catch (Throwable t) {
                _logger.log(Level.SEVERE, 
                "Exception in rollback transacion "+tx+" after enlist "+rh+" failure in "+this, t);
            }
            rh.ex =  new RuntimeException(
                rh+" was not enlisted to transaction "+tx+" successfully in "+this);
            return false;
        }
        return true;
    }

    private boolean doDelistAndRollbackOnError(Transaction tx, 
                                               TransactionManager tm,
                                               XAResourceHandle rh) { 
        try {
            rh.delisted = tx.delistResource(rh.xar, XAResource.TMSUCCESS);
        } catch (Throwable t) {
            _logger.log(Level.WARNING, "Unable delist "+rh+" from transaction "+tx+" in " +this, t); 
            try {
                 tm.rollback();
            } catch (Throwable t1) {
                _logger.log(Level.WARNING, 
                "Unable to rollback transaction "+tx+" after delist "+rh+" failure in "+this, t1);
            }
            rh.ex =  new RuntimeException(
               "Unable delist "+rh+" from transaction "+tx+" in " +this, t); 
            return false;
        }   
           
        if (!rh.delisted) {
            _logger.log(Level.SEVERE, 
            rh+" was not delisted from transaction "+tx+" successfully in "+this);
            try {
                tm.rollback();
            } catch (Exception e) {
                _logger.log(Level.SEVERE, 
                "Exception in rollback transacion "+tx+" in "+this, e);
            }
            rh.ex =  new RuntimeException(
                rh+" was not delisted from transaction "+tx+" successfully in "+this);
            return false;
        }
		return true;
    }

    private void doFinally(Transaction tx, TransactionManager tm, 
                                           XAResourceHandle sourceRH,
                                           XAResourceHandle targetRH) {
        if (tx != null) {
            int status = Status.STATUS_ACTIVE;
            try {
                status = tx.getStatus();
            } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                "Unable to get transaction "+tx+" status in "+this, t);
            }
            if (status != Status.STATUS_NO_TRANSACTION) {
                if (sourceRH.enlisted && !sourceRH.delisted) {
                    try {
                        tx.delistResource(sourceRH.xar, XAResource.TMFAIL);
                    } catch (Throwable t) {
                        _logger.log(Level.WARNING, 
                        "Unable to delist "+sourceRH+" in transaction "+tx+" "+this+": "+t.getMessage()); 
                    }
                }
                if (targetRH != null && targetRH.xar != sourceRH.xar && 
                    targetRH.enlisted && !targetRH.delisted) {
                    try {
                        tx.delistResource(targetRH.xar, XAResource.TMFAIL);
                    } catch (Throwable t) {
                        _logger.log(Level.WARNING,
                        "Unable to delist "+targetRH+" in transaction "+tx+" "+this+": "+t.getMessage()); 
                    }
                }
                _sourceConnException = true;
                _targetConnException = true;
            }
            try {
                status = tx.getStatus();
            } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                "Unable to get transaction "+tx+" status in "+this, t);
            }
            if (status != Status.STATUS_NO_TRANSACTION && 
                status != Status.STATUS_COMMITTED && 
                status != Status.STATUS_COMMITTING &&
                status != Status.STATUS_ROLLEDBACK &&
                status != Status.STATUS_ROLLING_BACK &&
                status != Status.STATUS_UNKNOWN &&
                status != Status.STATUS_MARKED_ROLLBACK) {
                _logger.log(Level.WARNING, "Rolling back transaction (status="+status+")"+tx+" in "+this); 
                try {
                tm.rollback();
                } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                "Exception to rollback transaction "+tx+" in "+this, t); 
                }
            }
        }
        if (!_targetStayConnected) {
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Close target in "+this);
            }
            closeTarget();
        }
    }

    /**
     * return true if transaction can continue
     */
    private boolean isTransactionActive(Transaction tx, TransactionManager tm) {
        boolean rollback = false;
        int status = Status.STATUS_ACTIVE;
        try {
             status = tx.getStatus();
        } catch (Throwable t) {
             _logger.log(Level.SEVERE, 
             "Unable to get transaction "+tx+" status in "+this, t);
             rollback = true;
        }
        if (!rollback) {
            switch (status) {
            case Status.STATUS_ACTIVE:
                 return true;
            case Status.STATUS_COMMITTED:
                 _logger.log(Level.WARNING, 
                 "Unexpected STATUS_COMMITTED for transaction " +tx+" in "+this);
                 return false;
            case Status.STATUS_COMMITTING:
                 _logger.log(Level.WARNING, 
                 "Unexpected STATUS_COMMITTED for transaction " +tx+" in "+this);
                 return false;
            case Status.STATUS_MARKED_ROLLBACK:
                 _logger.log(Level.WARNING, 
                 "Transaction "+tx+" marked rollback, rolling back ... in "+this);
                 rollback = true;
                 break;
            case Status.STATUS_NO_TRANSACTION: 
                 _logger.log(Level.WARNING, 
                 "Transaction "+tx+" has STATUS_NO_TRANSACTION");
                 return false;
            case Status.STATUS_PREPARED: 
                 _logger.log(Level.WARNING, 
                 "Unexpected STATUS_PREPARED for transaction "+tx+", rolling back ... in "+this);
                  rollback = true;
                  break;
            case Status.STATUS_PREPARING: 
                 _logger.log(Level.WARNING, 
                 "Unexpected STATUS_PREPARING for transaction "+tx+", set rollback only ... in "+this);
                 try {
                 tx.setRollbackOnly();
                 } catch (Throwable t) {
                 _logger.log(Level.WARNING, 
                 "Unable to set transaction "+tx+" rollback only in "+this);
                 }
                  rollback = true;
            case Status.STATUS_ROLLEDBACK:
                 _logger.log(Level.WARNING, 
                 "Transaction "+tx+" has STATUS_ROLLEDBACK in "+this);
                 return false;
            case Status.STATUS_ROLLING_BACK: 
                 _logger.log(Level.WARNING, 
                 "Transaction "+tx+" has STATUS_ROLLING_BACK in "+this);
                 return false;
            case Status.STATUS_UNKNOWN: 
                 _logger.log(Level.WARNING, 
                 "Unexpected STATUS_UNKNOWN for transaction "+tx+", rolling back ... in "+this);
                 rollback = true;
                 break;
            default:
                 _logger.log(Level.WARNING, 
                 "Unexpected status "+status+" for transaction "+tx+", rolling back ... in "+this);
                 rollback = true;
                 break;
            }
        }
        try {
            tm.rollback();
        } catch (Throwable t) {
            _logger.log(Level.WARNING, 
            "Exception in rollback transacion "+tx+" in "+this, t);
        } 
        return false;
    }

    private void runNonTransacted() throws Throwable {
        long msgCount = 0;

        int consecutiveThrowables = 0;

        while (_state != LinkState.STOPPING && _state != LinkState.STOPPED) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(
                _jbr.getKString(_jbr.X_LINK_INTERRUPTED, this.toString()));
            }

            _branchProducer = null;
            _targetCurrentDestinationName = null;
            Throwable currentThrowable = null;
            Message m = null;
            MessageHeaders srcmhs = null;
            String mid = null;
            try {

            if (_sourceConnException) {
                closeSource();
                initSource();
                resume(false);
            }

            if (_targetConnException) {
                closeTarget();
                if (_targetStayConnected) {
                    initTarget();
                }
            }

            try {
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Receiving message from source in "+this+" ...");
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_RECEIVE_1, null, "javax.jms.JMSException", true);
                }
                m = _consumer.receive();
                if (m  == null) {
                    try {
                    _logger.log(Level.WARNING, "receive() returned null,  source connection may have closed in "+this);
                    } catch (Throwable e) {}
                    continue;
                }

                msgCount++;
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Received message "+m+" from source in "+this+", msgCount="+msgCount);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_RECEIVE_2, null, "javax.jms.JMSException", true);
                }
            } catch (Throwable t) {
                currentThrowable = t;
                logWarning("Exception in receiving message: "+t.getMessage()+" in "+this, t);

                if (_state == LinkState.STOPPING || _state == LinkState.STOPPED) return;

                closeSource();
                _sourceConnException = true;
                continue;
            }

            srcmhs = MessageHeaders.getMessageHeaders(m);
            mid  = srcmhs.mid;
            long ttl  = srcmhs.expiration;
            if (ttl != 0L) {
                ttl = ttl - System.currentTimeMillis();
            }
            if (ttl < 0) {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_NONTXN_MESSAGE_EXPIRED, mid, this.toString())); 
                try {
                    sendToDMQ(m, mid, DMQ.DMQReason.MESSAGE_EXPIRED, (Throwable)null);
                } finally {
                    closeSource();
                    _sourceConnException = true;
                    continue;
                } 
            }

            if (_targetConnException || !_targetStayConnected) {
                if (_targetConnException) {
                    closeTarget();
                }
                try {
                    initTarget();
                } catch (Exception e) {
                    _logger.log(Level.SEVERE,  
                    "Unable to connect to target for message "+mid+" in "+this); 
                    throw e;
                }
            }

            Message sm = null;
            String midSent = null;

            try {
                sm = handleMessageTransformer(m, srcmhs);
            } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                        _jbr.getString(_jbr.W_STOP_LINK_BECAUSE_OF, this, t.getMessage()), t); 
                throw t;
            }
            if (sm == null) {
                _logger.log(Level.WARNING, _jbr.getString(_jbr.W_CONSUME_NO_TRANSFER, mid, this));

            } else {

            try {
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Sending message "+mid+" to target in "+this);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_SEND_1, null, "javax.jms.JMSException", true);
                }

                MessageProducer producer = _producer;
                if (_branchProducer != null) {
                    producer = _branchProducer;
                }
                if (!getTargetCurrentDestinationName().equals(toDestinationName(producer.getDestination()))) {
                    throw new BridgeException("Unexpected target producer's destination name "+
                        toDestinationName(producer.getDestination())+": not match current target destination name "+
                        getTargetCurrentDestinationName());
                }

                try {
                    if (_parent.needTagBridgeName()) {
                        _parent.tagBridgeName(sm, _sourceSession.createMessage());
                    }
                    producer.send(sm, srcmhs.deliverymode, srcmhs.priority, ttl);
                    midSent = sm.getJMSMessageID();
                } finally {
                    if (_branchProducer != null) {
                        try {
                            _branchProducer.close();
                        } catch (Throwable t) {
                            _logger.log(Level.WARNING,  
                            "Closing temporary target producer failed: "+ t.getMessage()+" in "+this, t);
                        } finally {
                            _branchProducer = null;
                        }
                    }
                    MessageHeaders.resetMessageHeaders(m, srcmhs);
                }

                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "Sent message "+mid+" to target in "+this);
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_SEND_2, null, "javax.jms.JMSException", true);
                }
            } catch (Throwable t) {
                currentThrowable = t;
                _logger.log(Level.SEVERE, 
                "Unable to send message "+mid+" to target in "+this, t);
                try {
                    sendToDMQ(m, mid, DMQ.DMQReason.SEND_FAILURE, t);
                } finally {
                    try {
                    _sourceSession.recover(); //XXX
                    } catch (Throwable t1) {
                    _logger.log(Level.WARNING, 
                    "Unable to recover session for expired message "+mid+" in "+this, t1);
                    closeSource();
                    _sourceConnException = true;
                    }
                    closeTarget();
                    _targetConnException = true;
                    continue;
                }
            }

            } // sm != null

            try {
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_ACK_1, null, "javax.jms.JMSException", true);
                }
                if (((Refable)_sourceCF).getRefed() instanceof 
                     com.sun.messaging.ConnectionFactory) {
                    ((com.sun.messaging.jmq.jmsclient.MessageImpl)m).acknowledgeThisMessage();
                } else {
                    m.acknowledge();
                }
                String[] param = { mid, midSent, this.toString() };
                if (_parent.logMessageTransfer()) {
                    _logger.log(Level.INFO,   _jbr.getString(_jbr.I_MESSAGE_TRANSFER_SUCCESS, param));
                } else if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE,   _jbr.getString(_jbr.I_MESSAGE_TRANSFER_SUCCESS, param));
                }
                if (_fi.FAULT_INJECTION) {
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_ACK_2, null, "javax.jms.JMSException", true);
                }
            } catch(Throwable t) {
                currentThrowable = t;
                _logger.log(Level.SEVERE, "Failed to acknowledge message "+mid+" in "+this, t);
                sendToDMQ(m, mid, DMQ.DMQReason.ACK_FAILURE, t);
            }
            continue;

            } finally {

                if (!_targetStayConnected) {
                    closeTarget();
                }
                if (currentThrowable == null) { 
                    consecutiveThrowables = 0;
                } else {
                    consecutiveThrowables++; 
                    if (consecutiveThrowables > MAX_CONSECUTIVE_THROWABLES) {
                    throw new RuntimeException("Maximum consecutive exceptions exceeded", currentThrowable);
                    }
                }
            }
        }
    }

    /**
     * @param m The message to be transformed
     * @param mid The JMSMessageID of 'm' for logging convenience
     *
     * @return a Message object that is tranformed from 'm' 
     *         or
     *         null to inform caller to consume 'm' from source and no forward to target
     * @exception to inform caller that the link must be stopped
     * 
     */
    private Message handleMessageTransformer(Message m, MessageHeaders srcmhs) throws Throwable {
        Message msgToSend = m;
        String mid = srcmhs.mid;

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "Retain JMSReplyTo "+_retainReplyTo+" for message "+mid+" in "+this);
        }

        if (!_retainReplyTo) {
            try {
                if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Setting JMSReplyTo null for message "+mid+" in "+this);
                }
                msgToSend.setJMSReplyTo((Destination)null);
            } catch (Throwable t) {
                _logger.log(Level.WARNING, 
                "setJMSReplyTo(null) failed: "+t.getMessage()+" in "+this, t);
            }
        }


        boolean transformed = false;
        Destination asSourceDest = m.getJMSDestination(); 

        if (_msgTransformer != null) {

        _msgTransformer.init(_targetSession, Bridge.JMS_TYPE);
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, 
            "Transforming message "+mid+" using "+_msgTransformer.getClass().getName()+" in "+this);
        }
        try {
            try {
                msgToSend = _msgTransformer.transform(m, true, null,
                                                      getSourceProviderName(),
                                                      getTargetProviderName(),
                                                      _msgTransformerProps);
                transformed = true;
                if (msgToSend == null) {
                    throw new BridgeException(_jbr.getKString(_jbr.X_NULL_RETURN_FROM_FOR_MESSAGE,
                                              _msgTransformer.getClass().getName()+".transform()", mid));
                }
            } finally {
                MessageHeaders.resetMessageHeaders(m, srcmhs);
            }
            if (_fi.FAULT_INJECTION) {
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_TRANSFORM_2, null, "javax.jms.JMSException", true);
            }

            if (_msgTransformer.isNoTransfer()) {

                String[] param = {"MessageTransformer.transform()", mid, this.toString()};
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_INSTRUCTED_NO_TRANSFER_AND_CONSUME, param));

                if (_firstTransformerNoTransfer) {
                    sendToDMQ(m, mid, DMQ.DMQReason.FIRST_TRANSFORMER_NOTRANSFER, null);
                    _firstTransformerNoTransfer = false;
                }
                return null;
            }
        } catch (Throwable t) {
            if (_consumeOnTransformError && (!transformed || msgToSend == null)) {
                _logger.log(Level.WARNING,
                "Exception from message transformer: "+t.getMessage()+" for message "+
                           m+" in "+this, t);
                sendToDMQ(m, mid, DMQ.DMQReason.TRANSFORMER_FAILURE, t);
                return null;
            }
            throw t;
        }
        if (_logger.isLoggable(Level.FINE)) {
            StringBuffer buf = new StringBuffer();
            buf.append("Transformed message "+mid);
            try {
                buf.append(" to "+msgToSend+" in "+this);
            } catch (Throwable t) {
                buf.append(" in "+this);
            }
            _logger.log(Level.FINE, buf.toString()); 
        }

        } // if (_msgTransformer != null)

        handleBranchTo(m, msgToSend, mid, transformed, asSourceDest);

        return msgToSend;
    }

    /**
     * Exception from this call will cause link stop 
     */
    private void handleBranchTo(Message oldm, Message newm, String mid, 
                                boolean transformed, Destination asSourceDest)
                                throws Throwable {
        _branchProducer = null;

        if (!transformed) {
            if (_unidentifiedProducer) { 
                _targetCurrentDestinationName = toDestinationName(asSourceDest);
                _branchProducer = createProducer(asSourceDest, mid, false);
                String[] param = { mid, 
                                   "Message.getJMSDestination:"+getTargetCurrentDestinationName(),
                                   this.toString() };
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_TRANSFER_TO_GETJMSDESTINATION, param));
            }
            return;
        }
        Object d = _msgTransformer.getBranchTo();
        if (d == null) {
            if (_unidentifiedProducer) {
                _targetCurrentDestinationName = toDestinationName(asSourceDest);
                _branchProducer = createProducer(asSourceDest, mid, false);
                /*
                if (newm.getJMSDestination() != asSourceDest) {
                    String[] param = { mid, 
                                      "Message.getJMSDestination:"+getTargetCurrentDestinationName(),
                                      "Message.getJMSDestination:"+toDestinationName(oldm.getJMSDestination()),
                                      this.toString() };
                    _logger.log(Level.INFO, _jbr.getString(_jbr.I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION_DIFF, param));
                     if (_firstTransformerAsSourceChange) {
                         sendToDMQ(oldm, mid, DMQ.DMQReason.FIRST_TRANSFORMER_AS_SOURCE_CHANGE, null);
                         _firstTransformerAsSourceChange = false;
                     }
                } else {
                */
                String[] param = { mid, 
                                   "Message.getJMSDestination:"+getTargetCurrentDestinationName(),
                                   this.toString() };
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION, param));
            }
            return;
        }

        _branchProducer = createProducer(d, mid, true);
        _targetCurrentDestinationName = toDestinationName(_branchProducer.getDestination());
        String[] param = { mid, 
                           ((d instanceof String) ? "["+JMSBridgeXMLConstant.Target.DESTINATIONREF+":"+d+"]":"")+
                           getTargetCurrentDestinationName(), this.toString() }; 
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_TRANSFORMER_BRANCHTO, param));
        if (_firstTransformerBranchTo) {
            sendToDMQ(oldm, mid, DMQ.DMQReason.FIRST_TRANSFORMER_BRANCHTO, null);
            _firstTransformerBranchTo = false;
        }
    }

    public LinkState getState() {
        return _state;
    }
}

