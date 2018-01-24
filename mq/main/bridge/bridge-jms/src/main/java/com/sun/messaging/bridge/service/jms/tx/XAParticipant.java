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

package com.sun.messaging.bridge.service.jms.tx;

import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.transaction.*;
import javax.transaction.xa.*;
import java.lang.IllegalStateException;
import java.lang.SecurityException;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.service.jms.JMSBridge;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 * @author amyk
 */

public class XAParticipant { 

    private Logger _logger = null;
    private FaultInjection _fi = FaultInjection.getInjection();

    private enum XAState {
        NOT_STARTED, START_FAILED, STARTED, 
        END_FAILED, ENDED, 
        PREPARE_FAILED, PREPARED,
        COMMIT_FAILED, COMMITTED, 
        ROLLBACK_FAILED, ROLLEDBACK,
        ROLLEDBACK_ONCOMMIT, ROLLEDBACK_ONCOMMIT_1PHASE,
        COMMITTED_ONROLLBACK };

    private XAState _state = XAState.NOT_STARTED; 

    private String _rm = null;
    private XAResource _xar = null;
    private BranchXid _bxid = null;
    private boolean _recovery = false;
    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public XAParticipant(String rm, XAResource xar, BranchXid bxid) 
                         throws SystemException {
        this(rm, xar, bxid, false);
    }

    public XAParticipant(String rm, XAResource xar, 
                         BranchXid bxid, boolean recover) 
                         throws SystemException {
        if (rm == null) {
            throw new SystemException("null RM name");
        }
        if (xar == null) {
            throw new SystemException("null XAResource object");
        }
        if (bxid == null) {
            throw new SystemException("null branch xid");
        }
        _bxid = bxid;
        _xar = xar;
        _rm = rm;
        _recovery = recover;
        _state = XAState.NOT_STARTED; 
    }

    protected void setLogger(Logger logger) {
        _logger = logger;
    }
    
    public BranchXid getBranchXid() {
        return _bxid;
    }

    public void start(int flags) throws RollbackException, SystemException {
        if (_state != XAState.NOT_STARTED) {
            _logger.log(Level.SEVERE, "start called at an illegal state "+this);
            throw new IllegalStateException(toString());
        }
        Exception ex = null;
        
		try {
            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_START_1, p, "javax.transaction.xa.XAException", true);
             }

            _xar.start(_bxid, flags);

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_START_2, p, "javax.transaction.xa.XAException", true);
             }

            _state = XAState.STARTED;
        } catch(XAException e) {
            switch (e.errorCode) {

            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            _state = XAState.NOT_STARTED;
            _logger.log(Level.SEVERE, "start returns XA_RB exception from "+this, e);
            ex = new RollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on start tranaction from "+this);
            ex.initCause(e);
            throw (RollbackException)ex;

            default: 
            _state = XAState.START_FAILED;
            _logger.log(Level.SEVERE, "start failed from "+this, e);
            ex = new RollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on start transaction from "+this);
            ex.initCause(e);
            throw (RollbackException)ex;
            }
        } catch (Throwable t) {
            _state = XAState.START_FAILED;
            _logger.log(Level.SEVERE, "start failed from "+this, t);
            String emsg =  t.getMessage();
            if (t instanceof XAException) {
                emsg = xaEString(((XAException)t).errorCode)+": "+t.getMessage();
            }
            ex = new SystemException(emsg+" on start transaction from "+this); 
            ex.initCause(t);
            throw (SystemException)ex;
        }
    }

    public void end(int flags) throws IllegalStateException, 
                                      RollbackException,
                                      SystemException {

        if (_state != XAState.STARTED && 
            _state != XAState.START_FAILED && 
            _state != XAState.END_FAILED) {
            _logger.log(Level.SEVERE, "end called at an illegal state "+this);
            throw new IllegalStateException(toString());
        }
        Exception ex = null;
        try {

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_END_1, p, "javax.transaction.xa.XAException", true);
             }

            _xar.end(_bxid, flags);

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_END_2, p, "javax.transaction.xa.XAException", true);
             }

            _state = XAState.ENDED;
        } catch(XAException e) {
            switch (e.errorCode) {

            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            _state = XAState.ENDED;
            if (_logger.isLoggable(Level.FINEST)) {
            _logger.log(Level.INFO, "XAResource.end() returned XA_RB exception from "+this, e);
            } else {
            _logger.log(Level.INFO, "XAResource.end() returned XA_RB exception from "+this);
            }
            ex = new RollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on end transaction from "+this);
            ex.initCause(e);
            throw (RollbackException)ex;

            default: 
            _state = XAState.END_FAILED;
            _logger.log(Level.SEVERE, "end failed from "+this, e);
            ex = new SystemException(xaEString(e.errorCode)+": "+e.getMessage()+ " on end transaction from "+this); 
            ex.initCause(e);
            throw (SystemException)ex;
            }
        } catch (Throwable t) {
            _logger.log(Level.SEVERE, "end failed from "+this, t);
            _state = XAState.END_FAILED;
            String emsg = t.getMessage();
            if (t instanceof XAException) {
                emsg = xaEString(((XAException)t).errorCode)+": "+t.getMessage();
            }
            ex = new SystemException(emsg+ " on end transaction from "+this); 
            ex.initCause(t);
            throw (SystemException)ex;
        }
    }

    public void rollback() throws IllegalStateException,
                                  SystemException,
                                  HeuristicCommitException,
                                  HeuristicRollbackException,
                                  HeuristicMixedException {
        if (!_recovery &&
            _state != XAState.ENDED && 
            _state != XAState.END_FAILED && 
            _state != XAState.PREPARED && 
            _state != XAState.PREPARE_FAILED && 
            _state != XAState.ROLLEDBACK && 
            _state != XAState.ROLLBACK_FAILED) {
            _logger.log(Level.SEVERE, "rollback called at an illegal state "+this);
            throw new IllegalStateException(toString());
        }
        if (_state == XAState.ROLLEDBACK) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_ALREADY_ROLLEDBACK, this.toString())); 
            return;
        }
        Exception ex = null;
        try {
            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_ROLLBACK_1, p, "javax.transaction.xa.XAException", true);
             }

            _xar.rollback(_bxid);

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_ROLLBACK_2, p, "javax.transaction.xa.XAException", true);
             }

            _state = XAState.ROLLEDBACK;
        } catch(XAException e) {
            switch (e.errorCode) {


            case XAException.XA_HEURCOM:
            _logger.log(Level.SEVERE, _bxid+" has been heuristically committed by "+this);
            _state = XAState.COMMITTED_ONROLLBACK;
            ex = new HeuristicCommitException(xaEString(e.errorCode)+": "+e.getMessage()+" on rollback transaction from "+this); 
            ex.initCause(e);
            throw (HeuristicCommitException)ex;

            case XAException.XA_HEURHAZ:
            _logger.log(Level.SEVERE, 
                     _bxid+" may have been heuristically completed by "+this);

            case XAException.XA_HEURMIX:
            _logger.log(Level.SEVERE, 
                     _bxid+" has been heuristically partialy committed or partially rolledback by "+this);
            _state = XAState.ROLLBACK_FAILED;
            ex = new HeuristicMixedException(xaEString(e.errorCode)+": "+e.getMessage()+" on rollback transaction from "+this); 
            ex.initCause(e);
            throw (HeuristicMixedException)ex;

            case XAException.XA_HEURRB:
            _state = XAState.ROLLEDBACK;
            try {
                _xar.forget(_bxid);
                return;
            } catch (Throwable t) {
               String emsg = "Failed to forget heuristically rolledback transaction from "+this;
                _logger.log(Level.WARNING, emsg, t);
                ex = new HeuristicRollbackException(emsg);
                ex.initCause(e);
                throw (HeuristicRollbackException)ex;
            }

            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            _state = XAState.ROLLEDBACK;
            _logger.log(Level.INFO, _bxid+" reported already rolled back by "+this);
            return;

            default: 
            _state = XAState.ROLLBACK_FAILED;
            ex = new SystemException(xaEString(e.errorCode)+": "+e.getMessage()+" on rollback transaction from "+this); 
            ex.initCause(e);
            throw (SystemException)ex;
            }
        } catch (Throwable t) {
            _state = XAState.ROLLBACK_FAILED;
            String emsg = t.getMessage();
            if (t instanceof XAException) {
                emsg = xaEString(((XAException)t).errorCode)+": "+t.getMessage();
            }
            ex = new SystemException(emsg+" on rollback transaction from "+this); 
            ex.initCause(t);
            throw (SystemException)ex;
        }
    }

    public void prepare() throws IllegalStateException,
                                 RollbackException,
                                 SystemException {
        if (_state != XAState.ENDED) {
            throw new IllegalStateException(toString());
        }
        Exception ex = null;
        int vote; 
        try {
            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_PREPARE_1, p, "javax.transaction.xa.XAException", true);
            }

            vote = _xar.prepare(_bxid);

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_PREPARE_2, p, "javax.transaction.xa.XAException", true);
            }

            if (vote == XAResource.XA_OK) {
                _state = XAState.PREPARED;
            } else if (vote == XAResource.XA_RDONLY) {
                _state = XAState.COMMITTED;
            } else {
                throw new SystemException(
                "Unexpected prepare return :"+vote+" from "+this);
            }
        } catch(XAException e) {
            switch (e.errorCode) {

            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            _state = XAState.ROLLEDBACK;
            ex = new RollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on prepare transaction from "+this);
            ex.initCause(e);
            throw (RollbackException)ex;

            default: 
            _state = XAState.PREPARE_FAILED;
            ex = new SystemException(xaEString(e.errorCode)+": "+e.getMessage()+"on prepare transaction from "+this); 
            ex.initCause(e);
            throw (SystemException)ex;
            }
        } catch (Throwable t) {
            _state = XAState.PREPARE_FAILED;
            if (t instanceof SystemException) {
                throw (SystemException)t;
            }
            String emsg = t.getMessage();
            if (t instanceof XAException) {
                emsg = xaEString(((XAException)t).errorCode)+": "+t.getMessage();
            }
            ex = new SystemException(emsg+"on prepare transaction from "+this);
            ex.initCause(t);
            throw (SystemException)ex;
        }
    }

    public void commit(boolean onePhase) throws IllegalStateException,
                                                RollbackException,
                                                HeuristicCommitException,
                                                HeuristicMixedException,
                                                HeuristicRollbackException,
                                                SystemException {
        if (onePhase) {
           if (_state != XAState.ENDED) {
               throw new IllegalStateException(toString());
           }
        } else {
           if (!_recovery && _state != XAState.PREPARED && _state != XAState.COMMITTED) {
               throw new IllegalStateException(toString());
           }
        }
        if (_state == XAState.COMMITTED) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_ALREADY_COMMITTED, this.toString())); 
        }
        Exception ex = null;
        try {
            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_COMMIT_1, p, "javax.transaction.xa.XAException", true);
            }

            _xar.commit(_bxid, onePhase);

            if (_fi.FAULT_INJECTION) {
                Map p = new HashMap();
                p.put(FaultInjection.CFREF_PROP, _rm);
                _fi.setLogger(_logger);
                _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_COMMIT_2, p, "javax.transaction.xa.XAException", true);
            }

            _state = XAState.COMMITTED;
        } catch(XAException e) {
            switch (e.errorCode) {

            case XAException.XA_HEURCOM:
            _state = XAState.COMMITTED;
            _logger.log(Level.INFO, _bxid+" has been heuristically committed by "+this);
            try {
                _xar.forget(_bxid);
                return;
            } catch (Throwable t) {
                String emsg = "Failed to forget heuristically committed transaction from "+this; 
                _logger.log(Level.WARNING, emsg, t);
                ex = new HeuristicCommitException(emsg); 
                ex.initCause(e);
                throw (HeuristicCommitException)ex;
            }

            case XAException.XA_HEURHAZ:
            _logger.log(Level.SEVERE, 
                        _bxid+" may have been heuristically completed by "+this);

            case XAException.XA_HEURMIX:
            _logger.log(Level.SEVERE, 
                     _bxid+" has been heuristically partialy committed or partially rolledback by "+this);
            _state = XAState.COMMIT_FAILED;
            ex = new HeuristicMixedException(xaEString(e.errorCode)+": "+e.getMessage()+" on commit transaction from "+this); 
            ex.initCause(e);
            throw (HeuristicMixedException)ex;

            case XAException.XA_HEURRB:
            _state = XAState.ROLLEDBACK_ONCOMMIT;
            _logger.log(Level.SEVERE, _bxid+" has been heuristically rolledback by "+this);
            ex = new HeuristicRollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on commit transaction from "+this); 
            ex.initCause(e);
            throw (HeuristicRollbackException)ex;

            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            _state = XAState.ROLLEDBACK_ONCOMMIT;
            if (!onePhase) {
                _logger.log(Level.SEVERE, "Unexpected rollback exception on commit from "+this);
                ex = new SystemException("Unexpected "+xaEString(e.errorCode)+": "+e.getMessage()+" on commit transaction from "+this);
                ex.initCause(e);
                throw (SystemException)ex;
            }
            ex = new RollbackException(xaEString(e.errorCode)+": "+e.getMessage()+" on commit transation from "+this);
            ex.initCause(e);
            throw (RollbackException)ex;

            default: 
            _state = XAState.COMMIT_FAILED;
            ex = new SystemException(xaEString(e.errorCode)+": "+e.getMessage()+" on commit transation from "+this); 
            ex.initCause(e);
            throw (SystemException)ex;
            }
        } catch (Throwable t) {
            _state = XAState.START_FAILED;
            String emsg = t.getMessage();
            if (t instanceof XAException) {
                emsg = xaEString(((XAException)t).errorCode)+": "+t.getMessage();
            }
            ex = new SystemException(emsg+" on commit transaction from "+this); 
            ex.initCause(t);
            throw (SystemException)ex;
        }
    }

    public String getRM() {
        return _rm;
    }

    public boolean equals(Object o) {
        if (!(o instanceof XAParticipant)) return false;
        XAParticipant that = (XAParticipant)o;
        if (that == this) return true;
        return (_rm.equals(that._rm) && 
                _bxid.equals(that._bxid) && 
                _xar.equals(that._xar));
    }

    public int hashCode() {
        return _xar.hashCode();
    }

    public String toString() {
        return _bxid+"["+_rm+":"+_xar+"]"+toString(_state);
    }

    public static String toString(XAState state) { 
        switch(state) {
        case NOT_STARTED: return "NOT_STARTED";
        case START_FAILED: return "STARTED_FAILED";
        case STARTED: return "STARTED";
        case END_FAILED: return "END_FAILED";
        case ENDED: return "ENDED";
        case PREPARE_FAILED: return "PREPARE_FAILED";
        case PREPARED: return "PREPARED";
        case COMMIT_FAILED: return "COMMIT_FAILED";
        case COMMITTED: return "COMMITTED";
        case ROLLBACK_FAILED: return "ROLLBACK_FAILED";
        case ROLLEDBACK: return "ROLLEDBACK";
        default: return "UNKNOWN";
        }
    }

    private String xaEString(int errorCode) {
        switch (errorCode) {
            case XAException.XA_RBROLLBACK: return "XA_RBROLLBACK";
            case XAException.XA_RBCOMMFAIL: return "XA_RBCOMMFAIL";
            case XAException.XA_RBDEADLOCK: return "XA_RBDEADLOCK";
            case XAException.XA_RBINTEGRITY: return "XA_RBINTEGRITY";
            case XAException.XA_RBOTHER: return "XA_RBOTHER";
            case XAException.XA_RBPROTO: return "XA_RBPROTO";
            case XAException.XA_RBTIMEOUT: return "XA_RBTIMEOUT";
            case XAException.XA_RBTRANSIENT: return "XA_RBTRANSIENT";
            case XAException.XA_HEURCOM: return "XA_HEURCOM";
            case XAException.XA_HEURHAZ: return "XA_HEURHAZ";
            case XAException.XA_HEURMIX: return "XA_HEURMIX";
            case XAException.XA_HEURRB: return "XA_HEURRB";
            case XAException.XA_NOMIGRATE: return "XA_NOMIGRATE";
            case XAException.XA_RETRY: return "XA_RETRY";
            case XAException.XA_RDONLY: return "XA_RDONLY";
            case XAException.XAER_ASYNC: return "XAER_ASYNC";
            case XAException.XAER_RMERR: return "XAER_RMERR";
            case XAException.XAER_NOTA: return "XAER_NOTA";
            case XAException.XAER_INVAL: return "XAER_INVAL";
            case XAException.XAER_PROTO: return "XAER_PROTO";
            case XAException.XAER_RMFAIL: return "XAER_RMFAIL";
            case XAException.XAER_DUPID: return "XAER_DUPID";
            case XAException.XAER_OUTSIDE: return "XAER_OUTSIDE";
            default: return "UNKNOWN";
        }
    }
}

