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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.transaction.*;
import javax.transaction.xa.XAResource;
import java.lang.IllegalStateException;
import java.lang.SecurityException;
import com.sun.messaging.bridge.service.jms.tx.log.GlobalXidDecision;
import com.sun.messaging.bridge.service.jms.tx.log.BranchXidDecision;
import com.sun.messaging.bridge.service.jms.tx.log.LogRecord;
import com.sun.messaging.bridge.service.jms.tx.log.TxLog;

/**
 * Implements JTA Transaction interface 
 *
 * @author amyk
 */

public class TransactionImpl implements Transaction {

    private Logger _logger = null;

    private int _status = Status.STATUS_NO_TRANSACTION;

    private GlobalXid _gxid = null;

    private TransactionManagerImpl _tm = null;

    private ArrayList<BranchXid> _seenBranchXids = new ArrayList<BranchXid>();
    private ArrayList<XAResource> _associatedXAResources = new ArrayList<XAResource>();

    private Map<XAResource, XAParticipant> _participants = 
                            new LinkedHashMap<XAResource, XAParticipant>();

    private byte _branchCount = 0;
    private int _maxBranches;

    private TxLog _txlog = null;

    public TransactionImpl(GlobalXid xid, TransactionManagerImpl tm) throws SystemException {
        if (xid == null) {
            throw new SystemException("null xid");
        }
        _tm = tm;
        _logger = tm.getLogger();
        _txlog = tm.getTxLog();
        _gxid = xid;
        _maxBranches = tm.getMaxBranches();
        _branchCount = 0;
        _status = Status.STATUS_ACTIVE;
    }

    /**
     * Complete the transaction represented by this Transaction object.
     *
     * @exception RollbackException Thrown to indicate that
     *    the transaction has been rolled back rather than committed.
     *
     * @exception HeuristicMixedException Thrown to indicate that a heuristic
     *    decision was made and that some relevant updates have been committed
     *    while others have been rolled back.
     *
     * @exception HeuristicRollbackException Thrown to indicate that a
     *    heuristic decision was made and that all relevant updates have been
     *    rolled back.
     *
     * @exception SecurityException Thrown to indicate that the thread is
     *    not allowed to commit the transaction.
     *
     * @exception IllegalStateException Thrown if the transaction in the 
     *    target object is inactive.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     */
    public void commit() throws RollbackException,
                HeuristicMixedException, HeuristicRollbackException,
                SecurityException, IllegalStateException, SystemException {

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, _tm+" commit "+this);
        }
        if (_status == Status.STATUS_MARKED_ROLLBACK) {
            rollback();
            throw new RollbackException(toString());
        }
        if (_status != Status.STATUS_ACTIVE) {
            throw new IllegalStateException(toString());
        }
        if (_associatedXAResources.size() != 0) {
            throw new IllegalStateException(
            "There are undelisted XAResources "+_associatedXAResources+" in "+this);  
        }

        boolean onePhase = false;
        if (_participants.size() == 1) {
            onePhase = true;
        }
        if (!onePhase) {
            _status = Status.STATUS_PREPARING;
            XAParticipant party  = null;
            boolean preparedOne = false;
            for (Map.Entry<XAResource, XAParticipant> pair: _participants.entrySet()) {
                party = pair.getValue();
                try { 
                    party.prepare();
                    preparedOne = true;
                } catch (IllegalStateException e) {
                    if (!preparedOne) throw e;
                    setRollbackOnly();
                    rollback();
                    RollbackException ex = new RollbackException(toString()+": "+e.getMessage());
                    ex.initCause(e);
                    throw ex;
                } catch (Throwable t) {
                    setRollbackOnly();
                  
                    if (!(t instanceof RollbackException) && 
                        !(t instanceof SystemException)) {
                        _logger.log(Level.SEVERE, "Unexpected exception on prepare from "+party, t); 
                    }
                    rollback();
                    RollbackException ex = new RollbackException(t.getMessage());
                    ex.initCause(t);
                    throw ex;
                }
            }
            _status = Status.STATUS_PREPARED;
        }
        if (_status == Status.STATUS_MARKED_ROLLBACK) {
            rollback();
            throw new RollbackException(toString());
        }
        if (!onePhase) {
            try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(),  GlobalXidDecision.COMMIT);
                _txlog.logGlobalDecision(lr);
            } catch (Throwable t) {
                setRollbackOnly();
                _logger.log(Level.SEVERE, "Unable to log commit decision "+this, t);
                rollback();
                RollbackException ex = new RollbackException(toString()+": "+t.getMessage());
                ex.initCause(t);
                throw ex;
            }
            _status = Status.STATUS_PREPARED;
        }  
        _status = Status.STATUS_COMMITTING;
        Exception ex = null;
        XAParticipant party = null;
        boolean committedOne = false;
        for (Map.Entry<XAResource, XAParticipant> pair: _participants.entrySet()) {
            party= pair.getValue();
            try { 
                party.commit(onePhase);
                committedOne = true;
            } catch (IllegalStateException e) {
                if (!committedOne) throw e;
                ex = e;
                continue;
            } catch (RollbackException e) {
                if (!onePhase) {
                    _logger.log(Level.SEVERE, 
                    "Unexpected RollbackException on 2-phase commit from "+party, e);
                    ex = new HeuristicMixedException(e.getMessage());
                    ex.initCause(e);
                    continue;
                } else {
                    throw e;
                }
            } catch (HeuristicCommitException e) {
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.COMMIT);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurCommit(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic commit from "+party, t);
                }
                continue;
            } catch (HeuristicMixedException e) {
                ex = e;
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.COMMIT);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurMixed(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic mixed from "+party, t);
                }
                continue;
            } catch (HeuristicRollbackException e) {
                ex = e;
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.COMMIT);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurRollback(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic rollback from "+party, t);
                }
                continue;
            } catch (Throwable t) {
                if (!(t instanceof SystemException)) {
                    _logger.log(Level.SEVERE, "Unexpected exception on commit from "+party, t);
                    ex = new SystemException(t.getMessage());
                    ex.initCause(t);
                } else {
                   ex = (SystemException)t;
                }
                continue;
            }
        }
        _status =  Status.STATUS_COMMITTED;
        if (ex == null) {
            _status = Status.STATUS_NO_TRANSACTION;
            if (!onePhase) {
                try {
                _txlog.remove(_gxid); 
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Exception in removing comitted TM log record "+_gxid, t);
                }
            }
        }
        if (ex != null) {
            if (ex instanceof SystemException) throw (SystemException)ex;
            if (ex instanceof HeuristicMixedException) throw (HeuristicMixedException)ex;
            if (ex instanceof IllegalStateException) throw (IllegalStateException)ex;
            if (ex instanceof HeuristicRollbackException) throw (HeuristicRollbackException)ex;
            SystemException uex =  new SystemException(ex.getMessage()); //should not hapen
            uex.initCause(ex);
            throw uex;
        }
    }

    /**
     * Disassociate the resource specified from the transaction associated 
     * with the target Transaction object.
     *
     * @param xaRes The XAResource object associated with the resource 
     *              (connection).
     *
     * @param flag One of the values of TMSUCCESS, TMSUSPEND, or TMFAIL.
     *
     * @exception IllegalStateException Thrown if the transaction in the
     *    target object is inactive.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     * @return <i>true</i> if the resource was delisted successfully; otherwise
     *	  <i>false</i>.
     *
     */
    public boolean delistResource(XAResource xaRes, int flag)
        throws IllegalStateException, SystemException {

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, _tm+" dellistResource "+xaRes+" from "+this);
        }
        if (_status != Status.STATUS_ACTIVE && 
            _status != Status.STATUS_MARKED_ROLLBACK) {
            throw new IllegalStateException(toString());
        }

        XAParticipant party = _participants.get(xaRes);
        if (party == null) {
            throw new IllegalStateException(
            "XAResource "+xaRes+" had not associated to "+this);
        }

        int flags = flag;
        if (_status == Status.STATUS_MARKED_ROLLBACK) {
            flags = XAResource.TMFAIL;
        }
         
        try {
            party.end(flags);
            _associatedXAResources.remove(xaRes);
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (RollbackException e) {
            setRollbackOnly();
            return true;
        } catch (Throwable t) {
            setRollbackOnly();
            if (t instanceof SystemException) throw (SystemException)t;
            _logger.log(Level.SEVERE, "Unexpected exception occurred on end from "+party, t);
            SystemException ex = new SystemException(t.getMessage());
            ex.initCause(t);
            throw ex;
        }
    }

    /**
     * Enlist the resource specified with the transaction associated with the 
     * target Transaction object.
     *
     * @param xaRes The XAResource object associated with the resource 
     *              (connection).
     *
     * @return <i>true</i> if the resource was enlisted successfully; otherwise
     *    <i>false</i>.
     *
     * @exception RollbackException Thrown to indicate that
     *    the transaction has been marked for rollback only.
     *
     * @exception IllegalStateException Thrown if the transaction in the
     *    target object is in the prepared state or the transaction is
     *    inactive.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public boolean enlistResource(XAResource xaRes)
        throws RollbackException, IllegalStateException,
        SystemException {

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, _tm+" enlistResource "+xaRes+" to "+this);
        }

        if (_status ==  Status.STATUS_MARKED_ROLLBACK) {
            throw new RollbackException(toString());
        }

        if (_status != Status.STATUS_ACTIVE) { 
            throw new IllegalStateException(toString());
        }

        if (_participants.get(xaRes) != null) {
            throw new IllegalStateException("XAResource "+xaRes+" already enlisted in "+this);
        }

        String rm = null;
        try {
            rm = _tm.getRM(xaRes);
        } catch (Exception e) {
            String emsg = "Enlist XAResource "+xaRes+" failed";
            _logger.log(Level.SEVERE, emsg, e);
            SystemException ex = new SystemException(emsg);
            ex.initCause(e);
            throw ex;
        }
        if (rm == null) {
            setRollbackOnly();
            throw new SystemException(
            "No RM is registered for XAResource "+xaRes);
        }
        BranchXid bxid = _tm.genBranchXid(_gxid, rm, xaRes.getClass().getName(), getBranchCount());
        if (_seenBranchXids.contains(bxid)) {
            throw new SystemException(
            "Unexpected duplicated branch "+bxid+" for RM "+rm+" in "+_gxid); 
        }
        _seenBranchXids.add(bxid);
        XAParticipant party = new XAParticipant(rm, xaRes, bxid);
        party.setLogger(_logger);
        _participants.put(xaRes, party); 
        _associatedXAResources.add(xaRes); 
        try {
            party.start(XAResource.TMNOFLAGS);
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            setRollbackOnly();
            if (t instanceof RollbackException) throw (RollbackException)t;
            if (t instanceof SystemException) throw (SystemException)t;
            _logger.log(Level.SEVERE, "Unexpected exception occurred on start from "+party, t);
            SystemException ex = new SystemException(t.getMessage());
            ex.initCause(t);
            throw ex;
        }
    }


    private synchronized byte getBranchCount() throws SystemException {
        _branchCount++;
        if (_branchCount > _maxBranches) {
            throw new SystemException(
            "Number of branches "+_branchCount+" exceeded max "+_maxBranches);
        }
        return _branchCount;
    }

    /**
     * Obtain the status of the transaction associated with the target 
     * Transaction object.
     *
     * @return The transaction status. If no transaction is associated with
     *    the target object, this method returns the 
     *    Status.NoTransaction value.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public int getStatus() throws SystemException {
        return _status; 
    }

    /**
     * Register a synchronization object for the transaction currently
     * associated with the target object. The transction manager invokes
     * the beforeCompletion method prior to starting the two-phase transaction
     * commit process. After the transaction is completed, the transaction
     * manager invokes the afterCompletion method.
     *
     * @param sync The Synchronization object for the transaction associated
     *    with the target object.
     *
     * @exception RollbackException Thrown to indicate that
     *    the transaction has been marked for rollback only.
     *
     * @exception IllegalStateException Thrown if the transaction in the
     *    target object is in the prepared state or the transaction is
     *	  inactive.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void registerSynchronization(Synchronization sync)
                throws RollbackException, IllegalStateException,
                SystemException {
        throw new SystemException("operation not supported");
    }

    /**
     * Rollback the transaction represented by this Transaction object.
     *
     * @exception IllegalStateException Thrown if the transaction in the
     *    target object is in the prepared state or the transaction is
     *    inactive.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
	public void rollback() throws IllegalStateException, SystemException {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, _tm+" rollback "+this);
        }
        if (_status != Status.STATUS_ACTIVE &&
            _status != Status.STATUS_MARKED_ROLLBACK) {
            throw new IllegalStateException(toString()); 
        }
        if (_associatedXAResources.size() > 0) {
            XAResource[] xars = _associatedXAResources.toArray(new XAResource[]{});
            for (int i = 0;  i < xars.length; i++) {
                try {
                    delistResource(xars[i], XAResource.TMFAIL);
                } catch (IllegalStateException e) {
                    throw e;
                } catch (Throwable t) {
                    _logger.log(Level.WARNING, "Unable to delist resource "+xars[i]+" for rollback", t);
                }
            }
        }
        _status = Status.STATUS_ROLLING_BACK;
        Exception ex = null;
        XAParticipant party = null;
        boolean rolledbackOne = false;
        for (Map.Entry<XAResource, XAParticipant> pair: _participants.entrySet()) {
            party = pair.getValue();
            try {
                party.rollback();
                rolledbackOne = true;
            } catch (IllegalStateException e) {
                if (!rolledbackOne) throw e;
                ex = new SystemException(e.getMessage());  
                ex.initCause(e);
                continue;
            } catch (HeuristicCommitException e) {
                ex = e;
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.ROLLBACK);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurCommit(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic commit from "+party, t);
                }
                continue;
            } catch (HeuristicRollbackException e) {
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.ROLLBACK);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurRollback(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic rollback from "+party, t);
                }
                continue;
            } catch (HeuristicMixedException e) {
                ex = e;
                try {
                LogRecord lr = new LogRecord(_gxid, _participants.values(), GlobalXidDecision.ROLLBACK);
                BranchXid bxid = party.getBranchXid();
                lr.setBranchHeurMixed(bxid);
                _txlog.logHeuristicBranch(bxid, lr);
                } catch (Throwable t) {
                _logger.log(Level.WARNING, "Unable to log heuristic mixed from "+party, t);
                }
                continue;
            } catch (Throwable t) {
                if (!(t instanceof SystemException)) {
                   ex = new SystemException(t.getMessage());
                   ex.initCause(t);
                } else {
                   ex = (SystemException)t;
                }
                continue;
            }
        }
        _status  = Status.STATUS_ROLLEDBACK;
        if (ex == null) _status = Status.STATUS_NO_TRANSACTION;
        if (ex != null) { 
            if (ex instanceof SystemException) throw (SystemException)ex;
            if (ex instanceof IllegalStateException) throw (IllegalStateException)ex;
            SystemException uex =  new SystemException(ex.getMessage()); 
            uex.initCause(ex);
            throw uex;
        }
    }

    /**
     * Modify the transaction associated with the target object such that
     * the only possible outcome of the transaction is to roll back the
     * transaction.
     *
     * @exception IllegalStateException Thrown if the target object is
     *    not associated with any transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        _status = Status.STATUS_MARKED_ROLLBACK;
    }

    private String statusString(int status) {
        switch (status) {

        case Status.STATUS_ACTIVE: return "STATUS_ACTIVE";
        case Status.STATUS_COMMITTED: return "STATUS_COMMITTED";
        case Status.STATUS_COMMITTING: return "STATUS_COMMITTING";
        case Status.STATUS_MARKED_ROLLBACK: return "STATUS_MARKED_ROLLBACK";
        case Status.STATUS_NO_TRANSACTION: return "STATUS_NO_TRANSACTION";
        case Status.STATUS_PREPARED: return "STATUS_PREPARED";
        case Status.STATUS_PREPARING: return "STATUS_PREPARING";
        case Status.STATUS_ROLLEDBACK: return "STATUS_ROLLEDBACK";
        case Status.STATUS_ROLLING_BACK: return "STATUS_ROLLING_BACK";
        case Status.STATUS_UNKNOWN: return "STATUS_UNKNOWN";
        default: return "STATUS_UNKNOWN";

        }
    }

    public boolean equals(Object o) {
        if (!(o instanceof TransactionImpl)) return false;
        TransactionImpl that = (TransactionImpl)o;
        if (this == that) return true;
        return _gxid.equals(that._gxid);
    }

    public int hashCode() {
        return _gxid.hashCode();
    }

    public String toString() {
        return _gxid+"["+statusString(_status)+"]";
    }

    protected Logger getLogger() {
        return _logger;
    }

    public String getGXidString() { 
        return _gxid.toString();
    }
 
}
