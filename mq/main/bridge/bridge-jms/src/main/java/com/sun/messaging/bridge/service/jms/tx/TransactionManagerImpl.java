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


import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.io.UnsupportedEncodingException;
import javax.transaction.*;
import javax.transaction.xa.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.bridge.service.jms.tx.log.TxLog;
import com.sun.messaging.bridge.service.jms.tx.log.LogRecord;
import com.sun.messaging.bridge.service.jms.tx.log.GlobalXidDecision;
import com.sun.messaging.bridge.service.jms.tx.log.BranchXidDecision;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.service.jms.tx.log.JDBCTxLogImpl;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.service.jms.JMSBridge;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;


/**
 * Implements JTA TransactionManager interface
 *
 * @author amyk
 */
public class TransactionManagerImpl implements TransactionManager, 
                                       TransactionManagerAdapter {

    private static int FORMATID = 1246580992; //JMQ'\0'

    private enum TMState { UNINITIALIZED, INITIALIZED, CLOSING, CLOSED }

    private Logger _logger = null;

    private static final int DEFAULT_TRANSACTION_TIMEOUT = 0;    //no timeout
    private int _transactionTimeout = DEFAULT_TRANSACTION_TIMEOUT;

    private String _tmName = null; 
    private String _jmsbridge = null; 

    private static final int DEFAULT_MAX_BRANCHES = 16;
    private int _maxBranches = DEFAULT_MAX_BRANCHES;

    private TMState _state = TMState.UNINITIALIZED;

    private Map<String, List<XAResource>> _rmToXAResources = new LinkedHashMap<String, List<XAResource>>();

    private ThreadLocal<TransactionImpl> _threadLocal =  new ThreadLocal<TransactionImpl>();

    private String _txlogdir = null;
    private TxLog _txlog = null;
    private String _txlogType = TxLog.FILETYPE; 
    private String _txlogClass = null;

    private JMSBridgeStore _jdbcStore = null; 

    private List<LogRecord> _recoveredLRs = new ArrayList<LogRecord>();
    private LinkedHashMap<String, ArrayList<String>> _keepGxidsForRM = new LinkedHashMap<String, ArrayList<String>>();

    private FaultInjection _fi = FaultInjection.getInjection();

    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources(); 

    private boolean _sameXARSameRM = true;

    public TransactionManagerImpl() {}

    /**
     * to be called before init() if JDBC store
     */
    public void setJDBCStore(JMSBridgeStore store) 
                    throws IllegalStateException {

        if (_state != TMState.UNINITIALIZED) {
            throw new IllegalStateException("setJDBCStore");
        }
        _jdbcStore = store;
    }

    public synchronized void init(Properties props, boolean reset) throws Exception {
        if (_logger == null) {
            throw new IllegalStateException("TM has no logger set");
        }

        if (props != null) {
            Enumeration en = props.propertyNames();
            String name = null;
            String value = null;
            while (en.hasMoreElements()) {
                name = (String)en.nextElement();
                value = props.getProperty(name);
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_SET_PROP_TM, name+"="+value, this.getClass().getName()));
                setProperty(name, value);
            }
        }

        if (_tmName == null) {
            throw new IllegalStateException("TM name not set for "+this.getClass().getName());
        }

        if (_state != TMState.UNINITIALIZED) {
           _logger.log(Level.WARNING, "init "+this); 
           throw new IllegalStateException("init "+this);
        }

        String txlogc = null;
        if (_txlogClass == null) {
            if (_txlogType.equals(TxLog.JDBCTYPE)) {
                _txlog = (TxLog)Class.forName(TxLog.JDBCCLASS).newInstance();
                ((JDBCTxLogImpl)_txlog).setJDBCStore(_jdbcStore);
            } else {
                _txlog = (TxLog)Class.forName(TxLog.FILECLASS).newInstance();
            }
        } else {
            _logger.log(Level.INFO, "loading txlog class "+_txlogClass);
            _txlog = (TxLog)Class.forName(_txlogClass).newInstance();
        }
        _txlog.setLogger(_logger);
        if (props.getProperty("txlogMaxBranches") == null) {
            props.setProperty("txlogMaxBranches", String.valueOf(_maxBranches));
        }
        try {
            _txlog.init(props, reset);
        } catch (Exception e) {
            _txlog.close();
            throw e;
        }
        _recoveredLRs = _txlog.getAllLogRecords();
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_TM_START_WITH, _tmName, String.valueOf(_recoveredLRs.size()))); 
        if (_logger.isLoggable(Level.FINE)) {
            LogRecord lr = null;
            Iterator<LogRecord> itr = _recoveredLRs.iterator();
            while (itr.hasNext()) {
                lr = itr.next();
                _logger.log(Level.INFO, "\t"+lr+"\n");
            }
        }
        _state = TMState.INITIALIZED;
    }

    /**
     */
    public String[] getAllTransactions() throws Exception {
        if (_state != TMState.INITIALIZED || _txlog == null) {
            return null;
        } 
        try {
            List<LogRecord> lrs = _txlog.getAllLogRecords();

            Iterator<LogRecord> itr = lrs.iterator();
            String[] lrss = new String[lrs.size()];
            int i = 0;
            while (itr.hasNext()) {
                lrss[i++] = itr.next().toString();
            }
            return lrss;
        } catch (Exception e) { 
            _logger.log(Level.WARNING, "Unable to get "+this+" all log records: "+e.getMessage(), e);
        }
        return null;

    }


    /**
     *
     */
    public TransactionManager getTransactionManager() 
                             throws SystemException {
        return this;
    }

    public void setProperty(String key, String value) 
                                   throws Exception {
        if (_state != TMState.UNINITIALIZED) { 
            throw new IllegalStateException("setProperty("+key+", "+value+")");
        }
        if (key.equals("tmname")) {
            setName(value);
            return;
        } 
        if (key.equals("txlogType")) {
            setTxlogType(value); 
            return;
        }
        if (key.equals("txlogClass")) {
            setTxlogClass(value); 
            return;
        }
        if (key.equals("jmsbridge")) {
            _jmsbridge = value;
            return;
        }
        if (key.equals("txSameXAResourceSameRM")) {
            _sameXARSameRM = Boolean.valueOf(value); 
            return;
        }
    }

    /**
     */
    public void setName(String v) throws SystemException {
        if (_state != TMState.UNINITIALIZED) { 
            throw new IllegalStateException("setName("+v+")");
        }
        if (v.getBytes().length > MAX_TMNAME_LENGTH) {
            throw new SystemException(
            "TM name "+v+" exceeds maximum "+MAX_TMNAME_LENGTH+" bytes");
        }
        _tmName = v;
    }

    public void setTxlogType(String type) throws Exception {
        if (_state != TMState.UNINITIALIZED) { 
            throw new IllegalStateException("setTxlogType("+type+")");
        }

        if (type == null || 
            (!type.trim().equals(TxLog.FILETYPE) &&
             !type.trim().equals(TxLog.JDBCTYPE))) {
            throw new IllegalArgumentException(this+": Invalid txlog type "+type);
        }
        _txlogType = type;
    }

    public void setTxlogClass(String cs) throws Exception {
        if (_state != TMState.UNINITIALIZED) { 
            throw new IllegalStateException("setTxlogClass("+cs+")");
        }

        if (cs == null || cs.trim().length() == 0) { 
            throw new IllegalArgumentException(this+": Invalid txlog class "+cs);
        }
        Class c = Class.forName(cs.trim());
        c.newInstance();
        _txlogClass = cs.trim();
    }

    public void setMaxBranches(int v) {
        if (_state != TMState.UNINITIALIZED) { 
            throw new IllegalStateException("setMaxBranches("+v+")");
        }

        if (v < 0 || v > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
            "Invalid value "+v+ " for maximum branches"); 
        }
        _maxBranches = v;
    }

    protected int getMaxBranches() {
        return _maxBranches;
    }

    public boolean registerRM() {
        return true;
    }

    /**
     * More than one XAResource object can be registered to a same rmName 
     * and isSameRM can be false among them; However resources registered 
     * to a same rmName and if isSameRM true among them, they must have same
     * class name 
     * 
     */
    public void registerRM(String rmName, XAResource xaRes) 
                                       throws Exception {
        if (_state != TMState.INITIALIZED) {
            throw new IllegalStateException("TM not initialized");
        }
        if (rmName == null) {
            throw new SystemException("null RM name");
        }
        if (xaRes == null) {
            throw new SystemException("null XAResource object for RM "+rmName);
        }

        if (rmName.getBytes().length > MAX_RMNAME_LENGTH) {
            throw new SystemException(
            "RM name "+rmName+" exceeds maximum "+MAX_RMNAME_LENGTH+" bytes");
        }
        
        synchronized(_rmToXAResources) {
            List<XAResource> l = _rmToXAResources.get(rmName); 
            if (l == null) {
                l = new ArrayList<XAResource>();
                _rmToXAResources.put(rmName, l);
            }
            for (XAResource xar : l) {
                if (xar.isSameRM(xaRes) || xaRes.isSameRM(xar)) {
                    if (!xar.getClass().getName().equals(xaRes.getClass().getName())) { //XXX
                        String emsg = "XAResource "+xaRes+" has different class name from what's registered "+xar+" for RM "+rmName;
                        _logger.log(Level.SEVERE, emsg);
                        throw new IllegalArgumentException(emsg);
                    }
                }
            }
            if (!l.contains(xaRes)) l.add(xaRes);
        } 
        ArrayList<Xid[]> axids = new ArrayList<Xid[]>();
        Xid[] xidsr = null;
        int flag = XAResource.TMSTARTRSCAN;
        do {
            
            try {
                if (_fi.FAULT_INJECTION) {
                    Map p = new HashMap();
                    p.put(FaultInjection.CFREF_PROP, rmName);
                    _fi.setLogger(_logger);
                    _fi.checkFaultAndThrowException(FaultInjection.FAULT_XA_RECOVER_1, p, "javax.transaction.xa.XAException", true);
                }

                xidsr = xaRes.recover(flag);
                if (xidsr.length > 0) flag = XAResource.TMNOFLAGS;
                axids.add(xidsr);
            } catch (Throwable t) {
                _logger.log(Level.SEVERE, "Recovering XAResource "+xaRes+" from RM "+rmName+ " failed", t);
                SystemException ex = new SystemException(t.getMessage());
                ex.initCause(t);
                throw ex;
            }
        } while (xidsr.length > 0);

        ArrayList<String> rmNameKeepGxids = new ArrayList<String>();
        ArrayList<String> rmNameRealNames = new ArrayList<String>();
        
        BranchXid bxid = null;
        byte[] gdata, bdata;
        boolean commit = false;
        for (Xid[] xids : axids) {
            for (int i = 0; i < xids.length; i++) {
                bxid = new BranchXid();
                bxid.copy(xids[i]);
                if (bxid.getFormatId() != FORMATID) {
                    _logger.log(Level.WARNING, "Ignore foreign XID "+bxid +"["+bxid.getFormatId()+"]");
                    continue;
                }
                gdata = bxid.getGlobalTransactionId();
                int tmnlen = (int)gdata[0];
                String tmn = new  String(gdata, 1, tmnlen, "UTF-8");
                if (!tmn.equals(_tmName)) {
                    _logger.log(Level.WARNING, "Ignore global XID "+bxid +" from different TM name ["+tmn+"] from mine ["+_tmName+"]");
                    continue;
                }
                bdata = bxid.getBranchQualifier();
                int rmnlen = (int)bdata[0];
                String rmn = new String(bdata, 1, rmnlen, "UTF-8");
                if (!rmn.equals(rmName)) {
                    _logger.log(Level.WARNING, "XID "+bxid +" from RM ["+rmName+"]"+xaRes+" has different RM name ["+rmn+"]");
                    rmNameRealNames.add(rmn);
                }
                commit = false;
                GlobalXid gxid = new GlobalXid();
                gxid.setFormatId(bxid.getFormatId());
                gxid.setGlobalTransactionId(gdata);
                _logger.log(Level.INFO, 
                "Recovering branch "+bxid +" for global transaction "+gxid+" from RM ["+rmName+"("+rmn+")]"+xaRes);

                LogRecord lr = _txlog.getLogRecord(gxid);
                if (lr != null && 
                    (lr.getGlobalDecision() == GlobalXidDecision.COMMIT)) {
                    commit = true;
                }
                try {
                     if (lr != null && lr.isHeuristicBranch(bxid)) {
                         _logger.log(Level.WARNING, 
                         "Branch "+bxid+" was heuristically completed in "+gxid+"["+commit+"]"); //XXX
                         rmNameKeepGxids.add(gxid.toString());
                         continue; //XXX
                     }
                } catch (NoSuchElementException e) {
                     rmNameKeepGxids.add(gxid.toString());
                     _logger.log(Level.WARNING, "Unable to find branch "+bxid+" in "+lr+" for recovery");
                     continue;
                }
                XAParticipant party = new XAParticipant(rmn, xaRes, bxid, true);
                party.setLogger(_logger);
                Throwable et = null;
                if (commit) {
                    try {
                        _logger.log(Level.INFO, "Commiting recovered branch "+bxid+" to RM ["+rmName+"("+rmn+")]"+xaRes);
                        party.commit(false); 
                    } catch (Throwable t) { //XXX
                        rmNameKeepGxids.add(gxid.toString());
                        et = t;
                        _logger.log(Level.WARNING, "Failed to commit recovered branch "+bxid, t);
                    }
                } else {
                    try {
                        _logger.log(Level.INFO, "Rolling back recovered branch "+bxid+" to RM "+rmName);
                        party.rollback();
                    } catch (Throwable t) { //XXX
                        et = t;
                        _logger.log(Level.WARNING, "Failed to rollback recovered branch "+bxid, t);
                    }
                }
            }
        }
        cleanupRecoveredLRs(rmName, rmNameKeepGxids, rmNameRealNames);
    }

    private void keepGxidForRM(String gxid, String effectiveRM, String realRM) {
        synchronized(_keepGxidsForRM) {
            if (effectiveRM != null) {
                ArrayList<String> gxids = _keepGxidsForRM.get(effectiveRM);
                if (gxids == null) {
                    gxids =  new ArrayList<String>();
                    _keepGxidsForRM.put(effectiveRM, gxids);
                }
                if (gxid != null) {
                    if (!gxids.contains(gxid)) gxids.add(gxid);
                }
            }

            if (realRM != null) {
                ArrayList<String> gxids = _keepGxidsForRM.get(realRM);
                if (gxids == null) {
                    gxids =  new ArrayList<String>();
                    _keepGxidsForRM.put(realRM, gxids);
                }
                if (gxid != null) {
                    if (!gxids.contains(gxid)) gxids.add(gxid);
                }
            }
        }
    }

    private synchronized void cleanupRecoveredLRs(String rmName, 
                                  ArrayList<String> keepGxids,
                                  ArrayList<String> realRMNames) {

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_UPDATE_RECOVER_INFO_GXIDS, rmName, Integer.valueOf(keepGxids.size())));
        keepGxidForRM(null, rmName, null);
        String realn = null;
        Iterator<String> itr1 = realRMNames.iterator(); 
        while (itr1.hasNext()) {
            realn = itr1.next();
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_UPDATE_RECOVER_INFO_FOR, rmName, realn));
            keepGxidForRM(null, null, realn);
        }

        String gxid = null;
        Iterator<String> itr0 = keepGxids.iterator();
        while(itr0.hasNext()) {
            gxid = itr0.next();
            keepGxidForRM(gxid, rmName, null);
            itr1 = realRMNames.iterator();
            while (itr1.hasNext()) {
                realn = itr1.next();
                keepGxidForRM(gxid, null, realn);
            }
        }

        synchronized(_recoveredLRs) {

        gxid = null;
        LogRecord lr = null;
        BranchXidDecision[] bxidds = null;
        Iterator<LogRecord> itr = _recoveredLRs.iterator();
        while (itr.hasNext()) {
            lr =itr.next();
            gxid = lr.getGlobalXid().toString();
            bxidds = lr.getBranchXidDecisions();
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.INFO, "Check recovery completion for global xid "+gxid);
            }

            BranchXid bxid = null;
            byte[] bq = null;
            int len = 0;
            String rmn = null;
            boolean keep = false;
            for (int i = 0; i < bxidds.length; i++) {
                bxid = bxidds[i].getBranchXid();
                bq = bxid.getBranchQualifier();
                len = (int)bq[0];
                try {
                    rmn = new String(bq, 1, len, "UTF-8"); 
                } catch (UnsupportedEncodingException e) {
                    _logger.log(Level.WARNING, 
                    "Unable to get RM name from branch "+bxid.toString()+" for recovery global xid "+gxid+": "+e.getMessage()); 
                    keep = true; 
                    break;
                }
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.INFO, "Check recovery completion for branch xid "+bxid+" with RM["+rmn+"]");
                }
                ArrayList<String> g = _keepGxidsForRM.get(rmn);
                if (g == null || g.contains(gxid)) {
                    if (_logger.isLoggable(Level.FINE)) {
                        if (g == null) {
                        _logger.log(Level.INFO, "Keep global xid "+gxid+" for no RM["+rmn+"] info");
                        } else {
                        _logger.log(Level.INFO, "Keep global xid "+gxid+" for RM["+rmn+"]");
                        }
                    }
                    keep = true;
                    break;
                }
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.INFO, "GXIDs "+g.size()+" to keep for RM["+rmn+"]");
                    Iterator<String> gitr = g.iterator(); 
                    while (gitr.hasNext()) {
                    _logger.log(Level.INFO, "GXIDs to keep for RM["+rmn+"]: "+gitr.next());
                    }
                }
            }
            if (!keep) {
                try {
                    _logger.log(Level.INFO, _jbr.getString(_jbr.I_TM_CLEANUP_RECOVERED_GTXN, gxid));
                    _txlog.reap(gxid);
                    itr.remove();
                } catch (Exception e) {
                    _logger.log(Level.WARNING, "Unable to cleanup recovered global xid "+gxid, e); 
                }
            }
        }
        }
    }

    /**
     *
     */
    public void unregisterRM(String rmName) throws Exception { 
        if (rmName == null) {
            throw new SystemException("null RM name");
        }
        synchronized(_rmToXAResources) {
            List<XAResource> l = _rmToXAResources.get(rmName); 
            if (l == null) {
                _logger.log(Level.WARNING,  "Removing a unknown RM "+rmName);
                return;
            }
            if (l.size() == 0) _rmToXAResources.remove(rmName);
        } 
    }

    /**
     */
    protected String getRM(XAResource xaRes) throws Exception {
        synchronized(_rmToXAResources) {
            for (Map.Entry<String, List<XAResource>> pair: _rmToXAResources.entrySet()) {
                List<XAResource> l = pair.getValue();
                for (XAResource xar : l) {
                     if (xar.isSameRM(xaRes) || (_sameXARSameRM && xar == xaRes)) {
                         if (!xar.getClass().getName().equals(xaRes.getClass().getName())) {
                             _logger.log(Level.WARNING, 
                             "XAResource "+xaRes+" has different class name from what's registered "+xar+" for RM "+pair.getKey());
                             continue;
                         }
                         return pair.getKey();
                     }
                }
            }
        }
        return null;
    }


    /**
     * Create a new transaction and associate it with the current thread.
     *
     * @exception NotSupportedException Thrown if the thread is already
     *    associated with a transaction and the Transaction Manager
     *    implementation does not support nested transactions.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void begin() throws NotSupportedException, SystemException {
        checkState();
        TransactionImpl tx = _threadLocal.get();
        if (tx != null) {
            throw new NotSupportedException(
            "Nested transaction is not supported. The calling thread "+
             Thread.currentThread()+" is currently associated with transaction "+tx);
        }
        GlobalXid xid = genGlobalXid();
        tx = new TransactionImpl(xid, this);
        _threadLocal.set(tx);
    }

    /**
     * Complete the transaction associated with the current thread. When this
     * method completes, the thread is no longer associated with a transaction.
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
     * @exception IllegalStateException Thrown if the current thread is
     *    not associated with a transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void commit() throws RollbackException,
	HeuristicMixedException, HeuristicRollbackException, SecurityException,
	IllegalStateException, SystemException {

        TransactionImpl tx = _threadLocal.get();
        if (tx == null) {
            throw new IllegalStateException(
            "No transaction associate with the calling thread "+Thread.currentThread());
        }
        try {
            tx.commit();
        } finally {
            _threadLocal.set(null);
            if (tx.getStatus() == Status.STATUS_NO_TRANSACTION) {
            }
        }
    }

    /**
     * Obtain the status of the transaction associated with the current thread.
     *
     * @return The transaction status. If no transaction is associated with
     *    the current thread, this method returns the Status.NoTransaction
     *    value.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public int getStatus() throws SystemException {
        TransactionImpl tx = _threadLocal.get();
        if (tx == null) {
            return Status.STATUS_NO_TRANSACTION;
        }
        return tx.getStatus();
    }

    /**
     * Get the transaction object that represents the transaction
     * context of the calling thread.
     *
     * @return the <code>Transaction</code> object representing the
     *	  transaction associated with the calling thread.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public Transaction getTransaction() throws SystemException {
        TransactionImpl tx = _threadLocal.get();
        if (tx == null) {
            throw new SystemException(
            "No transaction associated with calling thread "+Thread.currentThread());
        }
        return tx;
    }

    /**
     * Resume the transaction context association of the calling thread
     * with the transaction represented by the supplied Transaction object.
     * When this method returns, the calling thread is associated with the
     * transaction context specified.
     *
     * @param tobj The <code>Transaction</code> object that represents the
     *    transaction to be resumed.
     *
     * @exception InvalidTransactionException Thrown if the parameter
     *    transaction object contains an invalid transaction.
     *
     * @exception IllegalStateException Thrown if the thread is already
     *    associated with another transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     */
    public void resume(Transaction tobj)
            throws InvalidTransactionException, IllegalStateException,
            SystemException {
        throw new SystemException("operation not supported");
        
    }

    /**
     * Roll back the transaction associated with the current thread. When this
     * method completes, the thread is no longer associated with a
     * transaction.
     *
     * @exception SecurityException Thrown to indicate that the thread is
     *    not allowed to roll back the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is
     *    not associated with a transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void rollback() throws IllegalStateException, SecurityException,
                            SystemException {
        TransactionImpl tx = _threadLocal.get();
        if (tx == null) {
            throw new IllegalStateException(
            "No transaction associate with the calling thread "+Thread.currentThread());
        }
        try {
            tx.rollback();
        } finally {
            _threadLocal.set(null); 
        }
    }

    /**
     * Modify the transaction associated with the current thread such that
     * the only possible outcome of the transaction is to roll back the
     * transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is
     *    not associated with a transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        TransactionImpl tx = _threadLocal.get();
        if (tx == null) {
            throw new IllegalStateException(
            "No transaction associate with the calling thread "+Thread.currentThread());
        }
        tx.setRollbackOnly();
    }

    /**
     * Modify the timeout value that is associated with transactions started
     * by the current thread with the begin method.
     *
     * <p> If an application has not called this method, the transaction
     * service uses some default value for the transaction timeout.
     *
     * @param seconds The value of the timeout in seconds. If the value is zero,
     *        the transaction service restores the default value. If the value
     *        is negative a SystemException is thrown.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public synchronized void setTransactionTimeout(int seconds) throws SystemException {
        throw new SystemException("operation not supported");
        /*
        checkState();

        if (seconds = 0) {
            _transactionTimeout = DEFAULT_TIMEOUT;
            _logger.log(Level.INFO, "Restored TM default transaction timeout "+DEFAULT_TIMEOUT);
            return;
        }
        if (seconds > 0) {
            _transactionTimeout = seconds;
            _logger.log(Level.INFO, "Set TM transaction timeout to "+seconds);
            return;
        }
        throw new SystemException("Invalid timeout value: "+seconds);
        */
    }

    /**
     * Suspend the transaction currently associated with the calling
     * thread and return a Transaction object that represents the
     * transaction context being suspended. If the calling thread is
     * not associated with a transaction, the method returns a null
     * object reference. When this method returns, the calling thread
     * is not associated with a transaction.
     *
     * @return Transaction object representing the suspended transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     *    encounters an unexpected error condition.
     *
     */
    public Transaction suspend() throws SystemException {
        throw new SystemException("operation not supported");
    }

    public synchronized void shutdown() throws SystemException {
        if (_state == TMState.CLOSED) { 
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_TM_ALREADY_SHUTDOWN, this.toString()));
            return;
        }

        _state = TMState.CLOSING; 
        try {
            _txlog.close();
        } catch (Exception e) {
            _logger.log(Level.WARNING, "Failed to close txlog: ", e.getMessage());
            SystemException ex = new SystemException();
            ex.initCause(e);
            throw ex;
        }

        _state = TMState.CLOSED; 
    }

    private void checkState() throws SystemException {
        if (_state == TMState.CLOSING || _state ==  TMState.CLOSED) {
            throw new SystemException("TM is shuting down");
        }
    }

    private static final int MAX_TMNAME_LENGTH = Xid.MAXGTRIDSIZE - 9;

    private GlobalXid genGlobalXid() throws SystemException {
        try {

        GlobalXid xid = new GlobalXid();
        xid.setFormatId(FORMATID);

        byte[] tmn = _tmName.getBytes("UTF-8");
        if (tmn.length > (MAX_TMNAME_LENGTH)) {
            throw new SystemException(
            "TM name "+_tmName+" byte length exceeds "+MAX_RMNAME_LENGTH);
        }
        UID uid = new UID();

        ByteBuffer bf = ByteBuffer.wrap(new byte[Xid.MAXGTRIDSIZE]);
        bf.put((byte)tmn.length);
        bf.put(tmn);
        bf.putLong(uid.longValue());
        xid.setGlobalTransactionId(bf.array());
        return xid;

        } catch (Exception e) {
        if (e instanceof SystemException) throw (SystemException)e;
        SystemException se = new SystemException(e.getMessage());
        se.initCause(e);
        throw se;
        }
    }

    private static final int MAX_RMNAME_LENGTH = Xid.MAXBQUALSIZE - 2;

    protected BranchXid genBranchXid(GlobalXid xid, String rmName,
                                     String className, byte nth)
                                     throws SystemException {
        try {

        BranchXid bxid = new BranchXid();
        bxid.copy(xid);
        byte[] rn = rmName.getBytes("UTF-8");  
        if (rn.length > MAX_RMNAME_LENGTH) {
            throw new SystemException(
            "Resource manager name "+rmName+" byte length exceeds "+MAX_RMNAME_LENGTH);
        }
        byte[] cn = className.getBytes("UTF-8");
        int cnlen = MAX_RMNAME_LENGTH - rn.length;
        if (cnlen > cn.length) cnlen = cn.length;

        if (_logger.isLoggable(Level.FINE)) {
        _logger.log(Level.INFO, "genBranchXid:rmName="+rmName+"["+rn.length+"], className="+className+"["+cnlen+"]"); 
        }

        ByteBuffer bf = ByteBuffer.wrap(new byte[Xid.MAXBQUALSIZE]);
        bf.put((byte)rn.length);
        bf.put(rn);
        bf.put(cn, 0, cnlen);
        bf.put(nth);
        bxid.setBranchQualifier(bf.array());
        return bxid;

        } catch (Exception e) {
        if (e instanceof SystemException) throw (SystemException)e;
        SystemException se = new SystemException(e.getMessage());
        se.initCause(e);
        throw se;
        }
    }

    public TxLog getTxLog() {
        return _txlog;
    }

    private static String stateString(TMState s) {
        switch(s) {
            case UNINITIALIZED: return "UNINITIALIZED";
            case INITIALIZED: return "INITIALIZED";
            case CLOSING: return "CLOSING";
            case CLOSED: return "CLOSED";
            default: return "UNKNOWN";
        }
    }

    public String toString() {
        return "TM:"+_tmName+"["+stateString(_state)+"]";
    }

    public void setLogger(Logger logger) {
        _logger = logger;
    }

    protected Logger getLogger() {
        return _logger;
    }

}
