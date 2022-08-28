/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to Eclipse Foundation. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.jmsserver.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionListener;
import com.sun.messaging.jmq.jmsserver.persist.api.NoPersistPartitionedStoreImpl;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.UnknownTransactionException;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.FaultInjection;

public class TransactionList implements ClusterListener, PartitionListener {
    protected static boolean DEBUG = false;

    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
        }
    }

    public static final boolean DEBUG_CLUSTER_TXN = (Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.debug.txn") || DEBUG);

    public static final boolean AUTO_ROLLBACK = Globals.getConfig().getBooleanProperty(Globals.IMQ + ".transaction.autorollback", false);

    public static final long TXN_REAPINTERVAL = getTXN_REAPINTERVAL();

    public static final int TXN_REAPLIMIT = Broker.isInProcess() ? Globals.getConfig().getIntProperty(Globals.IMQ + ".txn.reapLimit", 0)
            : Globals.getConfig().getIntProperty(Globals.IMQ + ".txn.reapLimit", 500);

    public static final int TXN_REAPLIMIT_OVERTHRESHOLD = Globals.getConfig().getIntProperty(Globals.IMQ + ".txn.reapLimitOverThreshold", 100); // in percent

    public static final String XA_TXN_DETACHED_TIMEOUT_PROP = Globals.IMQ + ".transaction.detachedTimeout"; // in secs
    public static final String XA_TXN_DETACHED_RETAINALL_PROP = Globals.IMQ + ".transaction.detachedRetainAll";
    public static final String TXN_PRODUCER_MAX_NUM_MSGS_PROP = Globals.IMQ + ".transaction.producer.maxNumMsgs";
    public static final String TXN_CONSUMER_MAX_NUM_MSGS_PROP = Globals.IMQ + ".transaction.consumer.maxNumMsgs";

    /**
     * Max number of msgs that can be produced/consumed in a txn
     */
    protected static final int defaultProducerMaxMsgCnt = Globals.getConfig().getIntProperty(TXN_PRODUCER_MAX_NUM_MSGS_PROP, 10000);
    protected static final int defaultConsumerMaxMsgCnt = Globals.getConfig().getIntProperty(TXN_CONSUMER_MAX_NUM_MSGS_PROP, 1000);

    TransactionReaper txnReaper = null;
    DetachedTransactionReaper detachedTxnReaper = null;

    HashSet inuse_translist = null;
    HashMap translist = null;
    HashMap remoteTranslist = null;
    HashMap xidTable = null; // Maps XIDs to UIDs

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Lock shareLock = lock.readLock();
    private Lock exclusiveLock = lock.writeLock();

    PartitionedStore pstore = null; // persistence access

    protected Vector newlyActivatedBrokers = new Vector();
    protected Vector newlyActivatedPartitions = new Vector();
    protected final CountDownLatch loadCompleteLatch = new CountDownLatch(1);
    protected boolean loadComplete = false;
    private FaultInjection fi = null;

    private Logger logger = Globals.getLogger();
    protected BrokerResources br = Globals.getBrokerResources();

    protected DestinationList DL = null;
    private String logsuffix = "";

    private static long getTXN_REAPINTERVAL() {
        long v = Globals.getConfig().getLongProperty(Globals.IMQ + ".txn.reapInterval", 900) * 1000L;
        if (v <= 0L) {
            v = 1000L;
        }
        return v;
    }

    public TransactionList(DestinationList dl) throws BrokerException {

        fi = FaultInjection.getInjection();

        this.DL = dl;
        this.pstore = dl.getPartitionedStore();
        if (Globals.getStore().getPartitionModeEnabled()) {
            logsuffix = " [" + pstore + "]";
        }
        this.translist = new HashMap(1000);
        this.remoteTranslist = new HashMap(1000);
        this.xidTable = new HashMap(1000);
        this.inuse_translist = new HashSet(1000);
        this.txnReaper = new TransactionReaper(this);
        this.detachedTxnReaper = new DetachedTransactionReaper(this);

        ClusterManager cm = Globals.getClusterManager();
        if (cm != null) {
            cm.addEventListener(this);
        }
        try {

            if (Globals.isNewTxnLogEnabled() || ((pstore instanceof TxnLoggingStore) && ((TxnLoggingStore) pstore).isTxnConversionRequired())) {
                // start replaying the transaction log, if present
                ((TxnLoggingStore) pstore).init();
            }
            if ((pstore instanceof TxnLoggingStore) && ((TxnLoggingStore) pstore).isTxnConversionRequired()) {
                ((TxnLoggingStore) pstore).convertTxnFormats(this);
            } else {
                if (!Globals.isNewTxnLogEnabled()) {
                    loadTransactions();
                } else {
                    if (!AUTO_ROLLBACK) {
                        TransactionListLoader.loadTransactions(pstore, this);
                    } else {
                        TransactionListLoader.rollbackAllTransactions(pstore);
                    }
                }
            }
            newlyActivatedBrokers.add(Globals.getMyAddress().getMQAddress());
            loadComplete = true;
            loadCompleteLatch.countDown();
            this.txnReaper.wakeupReaperTimer();
            logger.log(logger.INFO, br.getKString(br.I_TXN_LOADING_COMPLETE) + logsuffix);

        } catch (Exception ex) {
            String emsg = br.getKString(br.W_TRANS_LOAD_ERROR);
            logger.logStack(Logger.INFO, emsg, ex);
            throw new BrokerException(emsg, ex);
        }
    }

    @Override
    public String toString() {
        if (DL == null || !DL.isPartitionMode()) {
            return super.toString();
        }
        return logsuffix;
    }

    /**
     * @return null if not found
     * @exception BrokerException if error
     */
    public static TransactionList getTransListByTID(TransactionUID tid) throws BrokerException {
        TransactionList tl = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            if (tls[i].retrieveState(tid, true) != null) {
                if (tl != null) {
                    throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "More than 1 TransactionList exist for transaction " + tid + ": " + tl + ", " + tls[i]));
                }
                tl = tls[i];
            }
        }
        return tl;
    }

    public static Object[] getTransListAndRemoteState(TransactionUID tid) {
        TransactionState ts = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            ts = tls[i].getRemoteTransactionState(tid);
            if (ts == null) {
                continue;
            }
            Object[] oo = new Object[2];
            oo[0] = tls[i];
            oo[1] = ts;
            return oo;
        }
        return null;
    }

    public static Object[] getTransactionByCreator(String creator) throws BrokerException {
        TransactionUID tid = null;
        TransactionList tl = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            tid = tls[i].getTransaction(creator);
            if (tid != null) {
                if (tl != null) {
                    throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "More than 1 TransactionList exist for creator " + creator + ": " + tl + ", " + tls[i]));
                }
                tl = tls[i];
            }
        }
        if (tl != null && tid != null) {
            Object[] oo = new Object[2];
            oo[0] = tl;
            oo[1] = tid;
            return oo;
        }
        return null;
    }

    /**
     * @param pid partition id
     * @return null if not found
     */
    public static TransactionList getTransListByPartitionID(UID pid) {

        PartitionedStore ps = new NoPersistPartitionedStoreImpl(pid);
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(ps);
        TransactionList tl = tls[0];
        return tl;
    }

    /**
     * @return null if none found
     */
    public static List<Object[]> getTransListsAndRemoteTranStates(TransactionUID tid) {

        ArrayList<Object[]> ret = new ArrayList<>();
        TransactionList tl = null;
        TransactionState ts = null;
        Object[] oo = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            tl = tls[i];
            if (tl == null) {
                continue;
            }
            ts = tl.getRemoteTransactionState(tid);
            if (ts == null) {
                continue;
            }
            oo = new Object[2];
            oo[0] = tl;
            oo[1] = ts;
            ret.add(oo);
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * @param tid must not be null
     * @param con can be null
     * @return Object[0] translist, Object[1] TransactionState; null if the transaction not found
     */
    public static Object[] getTransListAndState(TransactionUID tid, IMQConnection con, boolean inquiry, boolean newtran) {

        Object[] oo = new Object[2];
        oo[0] = null;
        oo[1] = null;
        TransactionList[] tls = null;
        TransactionList tl = null;
        TransactionState ts = null;
        if (con != null) {
            tls = Globals.getDestinationList().getTransactionList(con.getPartitionedStore());
            tl = tls[0];
            oo[0] = tl;
            if (newtran) {
                if (tl == null) {
                    return null;
                }
                return oo;
            }
            if (tl != null) {
                ts = tl.retrieveState(tid, inquiry);
                if (ts != null) {
                    oo[0] = tl;
                    oo[1] = ts;
                    return oo;
                }
            }
        }

        tl = null;
        tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            tl = tls[i];
            if (tl == null) {
                continue;
            }
            ts = tl.retrieveState(tid, inquiry);
            if (ts == null) {
                continue;
            }
            oo[0] = tl;
            oo[1] = ts;
            return oo;
        }
        return null;
    }

    /**
     * @param xid must not be null
     * @param con can be null
     */
    public static Object[] mapXidToTid(JMQXid xid, IMQConnection con) {

        TransactionList[] tls = null;
        TransactionList tl = null;
        TransactionUID tid = null;
        if (con != null) {
            tls = Globals.getDestinationList().getTransactionList(con.getPartitionedStore());
            tl = tls[0];
            if (tl != null) {
                tid = tl.xidToUID(xid);
                if (tid != null) {
                    Object[] oo = new Object[2];
                    oo[0] = tl;
                    oo[1] = tid;
                    return oo;
                }
            }
        }

        tl = null;
        tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            tl = tls[i];
            if (tl == null) {
                continue;
            }
            tid = tl.xidToUID(xid);
            if (tid != null) {
                Object[] oo = new Object[2];
                oo[0] = tl;
                oo[1] = tid;
                return oo;
            }
        }
        return null;
    }

    public PartitionedStore getPartitionedStore() {
        return pstore;
    }

    public Map getTransactionListMap() {
        return translist;
    }

    public Map getRemoteTransactionListMap() {
        return remoteTranslist;
    }

    protected boolean isLoadComplete() {
        return loadComplete;
    }

    public void postProcess() {
        detachedTxnReaper.detachOnephasePrepared();
    }

    public void destroy() {
        if (DEBUG) {
            logger.log(Logger.INFO, "Closing transaction list " + this);
        }
        if (txnReaper != null) {
            txnReaper.destroy();
        }
        if (detachedTxnReaper != null) {
            detachedTxnReaper.destroy();
        }
    }

    public Hashtable getDebugState(TransactionUID id) {

        TransactionInformation ti = null;
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        Hashtable ht = new Hashtable();
        if (ti == null && rti == null) {
            ht.put(id.toString(), "UNKNOWN TID");
            return ht;
        }

        if (ti != null) {
            ht.put(id.toString(), ti.getDebugState());
        }
        if (rti != null) {
            ht.put(id.toString() + "(remote)", rti.getDebugState());
        }
        return ht;
    }

    public void unlockTakeoverTxns(List txns) {
        TransactionInformation ti = null;
        Iterator itr = txns.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            if (tid == null) {
                continue;
            }

            shareLock.lock();
            try {
                ti = (TransactionInformation) translist.get(tid);
            } finally {
                shareLock.unlock();
            }

            if (ti == null) {
                continue;
            }
            ti.releaseTakeoverLock();
        }
    }

    // returns transaction acks
    public Map loadTakeoverTxns(List txns, List remoteTxns, Map<String, String> msgs) throws BrokerException, IOException {

        logger.log(Logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_PROCESSING_TAKEOVER_TRANS, Integer.valueOf(txns.size())));

        // hey process through the states
        Iterator itr = txns.iterator();
        Map acks = new HashMap();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            TransactionInfo ti = null;
            try {
                ti = pstore.getTransactionInfo(tid);
            } catch (Exception e) {
                String em = "Failed to get transaction " + tid + " information from store after takeover";
                logger.logStack(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, em, e);
                throw new BrokerException(em);
            }
            TransactionState ts = ti.getTransactionState();
            logger.log(Logger.DEBUG, "Processing transaction " + tid + ti.toString());
            try {
                if (ts.getState() != TransactionState.COMMITTED && ts.getState() != TransactionState.PREPARED) {
                    pstore.removeTransactionAck(tid, false);
                }
                TransactionAcknowledgement ta[] = pstore.getTransactionAcks(tid);
                logger.log(Logger.DEBUG, "Processing transaction acks " + tid + " number=" + ta.length);
                List l = Arrays.asList(ta);
                acks.put(tid, l);
                addTransactionID(tid, ts, true, ti.getType(), false);
                if (ti.getType() == TransactionInfo.TXN_CLUSTER) {
                    logClusterTransaction(tid, ts, ti.getTransactionBrokers(), true, false);
                }
                if (ts.getState() == TransactionState.PREPARED && ts.getOnephasePrepare()) {
                    addDetachedTransactionID(tid);
                }
            } catch (Exception ex) {
                logger.logStack(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, "error taking over " + tid, ex);
                acks.remove(tid);
            }
        }

        itr = remoteTxns.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            if (txns.contains(tid)) {
                continue;
            }
            TransactionInfo ti = null;
            try {
                ti = pstore.getTransactionInfo(tid);
            } catch (Exception e) {
                String em = "Failed to get remote transaction " + tid + " information from store after takeover";
                logger.logStack(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, em, e);
                throw new BrokerException(em);
            }
            TransactionState ts = ti.getTransactionState();
            if (DEBUG || DEBUG_CLUSTER_TXN) {
                logger.log(Logger.INFO, Globals.getBrokerResources().getString(BrokerResources.I_PROCESSING_REMOTE_TXN,
                        tid + "[" + TransactionState.toString(ts.getState()) + "]" + ti.toString()));
            } else {
                logger.log(Logger.INFO, Globals.getBrokerResources().getString(BrokerResources.I_PROCESSING_REMOTE_TXN,
                        tid + "[" + TransactionState.toString(ts.getState()) + "]"));
            }
            try {
                TransactionAcknowledgement ta[] = pstore.getTransactionAcks(tid);
                ArrayList l = new ArrayList();
                Iterator mitr = null;
                for (int i = 0; i < ta.length; i++) {
                    mitr = msgs.keySet().iterator();
                    while (mitr.hasNext()) {
                        String msgID = (String) mitr.next();
                        if (msgID.equals(ta[i].getSysMessageID().toString())) {
                            l.add(ta[i]);
                            if (DEBUG || DEBUG_CLUSTER_TXN) {
                                logger.log(Logger.INFO, "Processing remote transaction ack for TUID=" + tid + " " + ta[i].toString());
                            }
                        }
                    }
                }
                if ((l.size() > 0)) {
                    acks.put(tid, l);
                    logger.log(Logger.INFO,
                            "Processing remote transaction " + tid + "[" + TransactionState.toString(ts.getState()) + "] with acks " + l.size());
                    if (ts.getState() != TransactionState.PREPARED && ts.getState() != TransactionState.COMMITTED) {
                        ts.setState(TransactionState.PREPARED);
                    }
                }
                logRemoteTransaction(tid, ts, (TransactionAcknowledgement[]) l.toArray(new TransactionAcknowledgement[l.size()]), ti.getTransactionHomeBroker(),
                        true, false, false);
                if (ts.getState() == TransactionState.COMMITTED) {
                    txnReaper.addRemoteTransaction(tid, true);
                }
            } catch (Exception ex) {
                logger.logStack(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, "error taking over " + tid, ex);
                acks.remove(tid);

            }
        }

        return acks;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        shareLock.lock();
        try {
            ht.put("TransactionCount", Integer.valueOf(translist.size()));
            Iterator itr = translist.keySet().iterator();
            while (itr.hasNext()) {
                TransactionUID tid = (TransactionUID) itr.next();
                ht.put(tid.toString(), getDebugState(tid));
            }
            ht.put("TransactionCount(remote)", Integer.valueOf(remoteTranslist.size()));
            itr = remoteTranslist.keySet().iterator();
            while (itr.hasNext()) {
                TransactionUID tid = (TransactionUID) itr.next();
                if (!translist.containsKey(tid) && !remoteTranslist.containsKey(tid)) {
                    ht.put(tid.toString(), getDebugState(tid));
                }
            }

            if (inuse_translist.size() > 0) {
                ht.put("inUse", inuse_translist.toArray());
            } else {
                ht.put("inUse", "none");
            }

            Hashtable x = new Hashtable();

            itr = xidTable.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry me = (Map.Entry) itr.next();
                x.put(me.getKey().toString(), me.getValue().toString());
            }
            if (x.size() > 0) {
                ht.put("XIDs", x);
            } else {
                ht.put("XIDs", "none");
            }
        } finally {
            shareLock.unlock();
        }

        ht.put("txnReaper", txnReaper.getDebugState());
        ht.put("detachedTxnReaper", detachedTxnReaper.getDebugState());

        return ht;
    }

    public TransactionUID getTransaction(String id) {

        shareLock.lock();
        try {
            Iterator itr = translist.values().iterator();
            while (itr.hasNext()) {
                TransactionInformation info = (TransactionInformation) itr.next();
                TransactionState ts = info.getState();
                String creator = ts.getCreator();
                if (creator != null && creator.equals(id)) {
                    return info.getTID();
                }
            }
        } finally {
            shareLock.unlock();
        }
        return null;
    }

    public void addTransactionID(TransactionUID id, TransactionState ts) throws BrokerException {
        addTransactionID(id, ts, false, TransactionInfo.TXN_LOCAL, true);
    }

    public TransactionInformation addTransactionID(TransactionUID id, TransactionState ts, boolean persist) throws BrokerException {
        return addTransactionID(id, ts, false, TransactionInfo.TXN_LOCAL, persist);
    }

    private TransactionInformation addTransactionID(TransactionUID id, TransactionState ts, boolean takeover, int type, boolean persist)
            throws BrokerException {
        JMQXid xid = ts.getXid();

        shareLock.lock();
        try {
            if (inuse_translist.contains(id)) {
                if (!takeover || type != TransactionInfo.TXN_CLUSTER || translist.containsKey(id) || !remoteTranslist.containsKey(id)) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                            BrokerResources.X_TRANSACTIONID_INUSE, (Throwable) null, Status.CONFLICT);
                }
            }

            // If transaction is an XA (has an xid) save it for reverse mapping
            if (xid != null && xidTable.containsKey(xid)) {
                // Xid already in use
                throw new BrokerException(
                        Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTIONID_INUSE, id.toString() + "[Xid=" + xid.toString() + "]"),
                        BrokerResources.X_TRANSACTIONID_INUSE, (Throwable) null, Status.CONFLICT);
            }
        } finally {
            shareLock.unlock();
        }

        try {
            if (persist) {
                pstore.storeTransaction(id, ts, Destination.PERSIST_SYNC);
            }
        } catch (Exception ex) {
            throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTION_STORE_ERROR, id.toString()),
                    BrokerResources.X_TRANSACTION_STORE_ERROR, ex, Status.ERROR);
        }

        TransactionInformation ti = new TransactionInformation(id, ts);
        if (takeover) {
            ti.getTakeoverLock();
        }

        exclusiveLock.lock();
        try {
            inuse_translist.add(id);
            translist.put(id, ti);
            if (xid != null) {
                xidTable.put(xid, id);
            }
        } finally {
            exclusiveLock.unlock();
        }

        return ti;
    }

    public boolean isXATransaction(TransactionUID id) throws BrokerException {
        TransactionState ts = null;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ts = ti.getState();
            if (ts != null) {
                return (ts.isXA());
            }
        }
        throw new BrokerException("Transaction " + id + "not found", Status.NOT_FOUND);
    }

    public void removeTransaction(TransactionUID id, boolean noremove) throws BrokerException {
        removeTransactionID(id, noremove, false, true);
    }

    public void removeTransactionID(TransactionUID id) throws BrokerException {
        removeTransactionID(id, false, false, true);
    }

    protected void reapTransactionID(TransactionUID id, boolean noremove) throws BrokerException {
        removeTransactionID(id, noremove, true, true);
    }

    private void removeTransactionID(TransactionUID id, boolean noremove, boolean fromReaper, boolean persist) throws BrokerException {
        TransactionState ts = null;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ts = ti.getState();
            if (!fromReaper && ts != null && ts.getState() == TransactionState.COMMITTED) {
                if (ti.getType() == TransactionInfo.TXN_CLUSTER) {
                    ti.processed();
                    txnReaper.addClusterTransaction(id, noremove);
                    return;
                }
                if (ti.getType() == TransactionInfo.TXN_LOCAL) {
                    ti.processed();
                    if (noremove || TransactionList.TXN_REAPLIMIT > 0) {
                        txnReaper.addLocalTransaction(id, noremove);
                        return;
                    }
                }
            }
        }

        if (!noremove && persist) {
            try {
                pstore.removeTransaction(id, true, false);
            } catch (IOException ex) {
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Unable to remove the transaction id " + id), ex);
            }
        }

        exclusiveLock.lock();
        try {
            translist.remove(id);

            if (!remoteTranslist.containsKey(id)) {
                inuse_translist.remove(id);
            }

            // If XA (has Xid) remove it from reverse mapping
            if (ts != null && ts.getXid() != null) {
                xidTable.remove(ts.getXid());
            }
        } finally {
            exclusiveLock.unlock();
        }
    }

    public void removeRemoteTransactionAck(TransactionUID id) throws BrokerException {
        if (Globals.getHAEnabled()) {
            return;
        }
        removeTransactionAck(id, true);
    }

    // To be called only if acks to be removed when transaction ID removes
    public void removeTransactionAck(TransactionUID id) throws BrokerException {
        // remove with transactionID
        return;
    }

    public void removeTransactionAck(TransactionUID id, boolean persist) throws BrokerException {
        try {
            pstore.removeTransactionAck(id, false);
        } catch (Exception ex) {
            throw new BrokerException(br.getKString(br.X_RM_TXN_ACK_IN_STORE, id, ex.getMessage()));
        }
    }

    public void addDetachedTransactionID(TransactionUID tid) {
        detachedTxnReaper.addDetachedTID(tid);
    }

    public void addMessage(PacketReference ref) throws BrokerException {

        TransactionUID tid = ref.getTransactionID();
        if (tid == null) {
            return;
        }
        SysMessageID sysid = ref.getSysMessageID();

        boolean delaypersist = false;
        if (ref.isPersistent() && !ref.getNeverStore() && Globals.isMinimumPersistLevel2()) {
            TransactionState ts = retrieveState(tid, true);
            if (ts == null) {
                throw new BrokerException(Globals.getBrokerResources().getKString(br.X_RECEIVED_MSG_WITH_UNKNOWN_TID, sysid, tid), Status.GONE);
            }
            if (ts.getType() != AutoRollbackType.NEVER) {
                delaypersist = true;
            }
        }
        if (Globals.isNewTxnLogEnabled()) {
            delaypersist = true;
        }
        if (!delaypersist) {
            ref.store();
        }
        addMessage(tid, sysid, false);
    }

    public void addMessage(TransactionUID id, SysMessageID sysid, boolean anyState) throws BrokerException {

        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getKString(br.X_RECEIVED_MSG_WITH_UNKNOWN_TID, sysid, id), Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            int state = info.getState().getState();
            if (state == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + id + ": is has timed out "),
                        Status.TIMEOUT);

            }
            if (!anyState && state != TransactionState.STARTED) {
                // bad state
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + id + "["
                                + TransactionState.toString(state) + "]: is not in " + TransactionState.toString(TransactionState.STARTED) + " state"),
                        Status.PRECONDITION_FAILED);

            }
            info.addPublishedMessage(sysid);
        }
    }

    public Hashtable getTransactionMap(TransactionUID tid, boolean ext) throws BrokerException {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "received acknowledgement with Unknown Transaction ID " + tid),
                    Status.GONE);
        }

        TransactionState ts = info.getState();
        if (ts == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "received acknowledgement with Unknown Transaction state " + tid), Status.ERROR);
        }

        Hashtable ht = new Hashtable();
        ht.put("JMQAutoRollback", Integer.valueOf(ts.getType().intValue()));
        if (ts.getXid() != null) {
            ht.put("JMQXid", ts.getXid().toString());
        }
        ht.put("JMQSessionLess", Boolean.valueOf(ts.isSessionLess()));
        ht.put("JMQCreateTime", Long.valueOf(ts.getCreationTime()));
        ht.put("JMQLifetime", Long.valueOf(ts.getLifetime()));
        if (ext) { // client protocol specifies +1A
            ht.put("State", Integer.valueOf(ts.getState() + 1));
        } else {
            ht.put("State", Integer.valueOf(ts.getState()));
        }

        return ht;
    }

    public boolean checkAcknowledgement(TransactionUID tid, SysMessageID sysid, ConsumerUID cuid) throws BrokerException {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(br.getKString(br.X_TRAN_NOT_FOUND_FOR_ACK, tid, "[" + sysid + ", " + cuid + "]"), Status.GONE);
        }

        if (info.getState().getState() == TransactionState.TIMED_OUT) {
            // bad state
            throw new BrokerException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + cuid + ": is has timed out "),
                    Status.TIMEOUT);
        }

        return info.checkConsumedMessage(sysid, cuid);
    }

    public TransactionUID getConsumedInTransaction(SysMessageID sysid, ConsumerUID id) {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            Iterator itr = translist.values().iterator();
            while (itr.hasNext()) {
                info = (TransactionInformation) itr.next();
                if (info == null) {
                    continue;
                }
                if (info.isConsumedMessage(sysid, id)) {
                    return info.getTID();
                }
            }
        } finally {
            shareLock.unlock();
        }
        return null;
    }

    /**
     * @return true if XA transaction
     */
    public boolean addAcknowledgement(TransactionUID tid, SysMessageID sysid, ConsumerUID intid, ConsumerUID sid) throws BrokerException {

        PacketReference pr = DL.get(null, sysid);
        if (pr == null) {
            if (!DL.isLocked(pstore, sysid)) {
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.I_ACK_FAILED_MESSAGE_GONE,
                        "" + sysid + "[" + intid + ":" + sid + "]TUID=" + tid), Status.CONFLICT);
            } else {
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.I_ACK_FAILED_MESSAGE_LOCKED,
                        "" + sysid + "[" + intid + ":" + sid + "]TUID=" + tid), Status.CONFLICT);
            }
        }
        boolean persist = sid.shouldStore() && pr.isPersistent() && pr.isLocal()
                && (!DL.isPartitionMode() || pr.getPartitionedStore().getPartitionID().equals(pstore.getPartitionID()));
        return addAcknowledgement(tid, sysid, intid, sid, false, persist);
    }

    public boolean addAcknowledgement(TransactionUID tid, SysMessageID sysid, ConsumerUID intid, ConsumerUID sid, boolean anystate, boolean persist)
            throws BrokerException {
        boolean isXA = false;
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Received acknowledgement with Unknown Transaction ID " + tid),
                    Status.GONE);
        }

        TransactionState ts = null;
        // lock TransactionInformation object
        synchronized (info) {
            ts = info.getState();
            int state = ts.getState();
            if (state == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + tid + " has timed out "), Status.TIMEOUT);

            }
            if (!anystate && state != TransactionState.STARTED) {
                // bad state
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + tid + " has state ["
                                + TransactionState.toString(state) + "],  not in [" + TransactionState.toString(TransactionState.STARTED) + "] state"),
                        Status.PRECONDITION_FAILED);

            }
            info.addConsumedMessage(sysid, intid, sid);
            isXA = info.getState().isXA();
        }

        boolean delaypersist = false;
        if ((ts.getType() != AutoRollbackType.NEVER && Globals.isMinimumPersistLevel2()) || Globals.isNewTxnLogEnabled()) {
            delaypersist = true;
        }

        if (persist && !delaypersist) {
            if (fi.FAULT_INJECTION) {
                try {
                    fi.checkFaultAndThrowBrokerException(FaultInjection.FAULT_TXN_ACK_1_3, null);
                } catch (BrokerException e) {
                    fi.unsetFault(fi.FAULT_TXN_ACK_1_3);
                    throw e;
                }
            }
            pstore.storeTransactionAck(tid, new TransactionAcknowledgement(sysid, intid, sid), false);
        }
        return isXA;
    }

    public void setAckBrokerAddress(TransactionUID tid, SysMessageID sysid, ConsumerUID id, BrokerAddress addr) throws BrokerException {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unknown Transaction ID " + tid + " for transaction ack [" + sysid + ":" + id + "]" + addr), Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            int state = info.getState().getState();
            if (state == TransactionState.TIMED_OUT) {
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + tid + ": has timed out "),
                        Status.TIMEOUT);
            }
            if (state != TransactionState.STARTED) {
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + tid + "["
                                + TransactionState.toString(state) + "]: is not in " + TransactionState.toString(TransactionState.STARTED) + " state"),
                        Status.PRECONDITION_FAILED);
            }
            info.setAckBrokerAddress(sysid, id, addr);
        }
    }

    public BrokerAddress getAckBrokerAddress(TransactionUID tid, SysMessageID sysid, ConsumerUID id) throws BrokerException {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unknown Transaction ID " + tid + " for transaction ack [" + sysid + ":" + id + "]"), Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            return info.getAckBrokerAddress(sysid, id);
        }
    }

    public List retrieveSentMessages(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getPublishedMessages();
    }

    public int retrieveNSentMessages(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return 0;
        }
        return ti.getNPublishedMessages();
    }

    public HashMap retrieveConsumedMessages(TransactionUID id) {
        return retrieveConsumedMessages(id, false);
    }

    public HashMap retrieveConsumedMessages(TransactionUID id, boolean inrollback) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getConsumedMessages(inrollback);
    }

    // this retrieves a mapping of consumerUID to stored consumerUID
    // the stored consumerUId is the ID we use to store ack info
    // for a durable
    public HashMap retrieveStoredConsumerUIDs(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getStoredConsumerUIDs();
    }

    public HashMap retrieveAckBrokerAddresses(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getAckBrokerAddresses();
    }

    public int retrieveNConsumedMessages(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return 0;
        }
        return ti.getNConsumedMessages();
    }

    public int retrieveNRemoteConsumedMessages(TransactionUID id) {
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return 0;
        }
        return ti.getNRemoteConsumedMessages();
    }

    // Get the state of a transaction
    public TransactionState retrieveState(TransactionUID id) {
        return retrieveState(id, false);
    }

    public TransactionState retrieveState(TransactionUID id, boolean inquiry) {
        if (id == null) {
            return null;
        }
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        if (inquiry) {
            return ti.getState();
        }
        if (ti.isTakeoverLocked()) {
            Globals.getLogger().log(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.X_TXN_LOCKED, id));
            return null;
        }
        if (ti.isProcessed()) {
            return null;
        }
        return ti.getState();
    }

    public TransactionWork[] getTransactionWork(TransactionUID tid) throws BrokerException {

        List plist = retrieveSentMessages(tid);
        HashMap cmap = retrieveConsumedMessages(tid);
        HashMap sToCmap = retrieveStoredConsumerUIDs(tid);

        TransactionWork localwork = new TransactionWork();
        TransactionWork remotework = new TransactionWork();
        TransactionWork[] txnworks = new TransactionWork[2];
        txnworks[0] = localwork;
        txnworks[1] = remotework;

        // iterate produced messages
        for (int i = 0; plist != null && i < plist.size(); i++) {
            SysMessageID sysid = (SysMessageID) plist.get(i);
            PacketReference ref = DL.get(pstore, sysid);
            if (ref == null || ref.isDestroyed()) {
                logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_MSG_REMOVED_BEFORE_SENDER_COMMIT, sysid));
                continue;
            }
            if (ref.isPersistent() && !ref.getNeverStore() && !ref.isStored()) {
                TransactionWorkMessage txnmsg = new TransactionWorkMessage();
                Destination dest = ref.getDestination();
                txnmsg.setDestUID(dest.getDestinationUID());
                txnmsg.setPacketReference(ref);
                localwork.addMessage(txnmsg);
            }
        }

        // iterate consumed messages
        if (cmap != null && cmap.size() > 0) {
            Iterator itr = cmap.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry) itr.next();
                SysMessageID sysid = (SysMessageID) entry.getKey();
                List interests = (List) entry.getValue();
                if (sysid == null) {
                    continue;
                }
                PacketReference ref = DL.get(null, sysid);
                if (ref == null) {
                    ConsumerUID intid = null;
                    ConsumerUID sid = null;
                    if (interests.size() > 0) {
                        intid = (ConsumerUID) interests.get(0);
                        sid = (ConsumerUID) sToCmap.get(intid);
                        if (sid == null) {
                            sid = intid;
                        }
                    }
                    if (!DL.isLocked(pstore, sysid)) {
                        throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.I_ACK_FAILED_MESSAGE_GONE,
                                "" + sysid + "[" + intid + ":" + sid + "]TUID=" + tid), Status.CONFLICT);
                    } else {
                        throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.I_ACK_FAILED_MESSAGE_LOCKED,
                                "" + sysid + "[" + intid + ":" + sid + "]TUID=" + tid), Status.CONFLICT);
                    }
                }
                Destination[] ds = DL.getDestination(ref.getPartitionedStore(), ref.getDestinationUID());
                Destination dst = ds[0];

                for (int i = 0; i < interests.size(); i++) {
                    ConsumerUID intid = (ConsumerUID) interests.get(i);
                    ConsumerUID sid = (ConsumerUID) sToCmap.get(intid);
                    if (sid == null) {
                        sid = intid;
                    }
                    if (sid.shouldStore() && ref.isPersistent()) {
                        TransactionAcknowledgement ta = new TransactionAcknowledgement(sysid, intid, sid);
                        if (ref.isLocal() && (!DL.isPartitionMode() || ref.getPartitionedStore().getPartitionID().equals(pstore.getPartitionID()))) {
                            if (ref.isDestroyed() || ref.isInvalid()) {
                                // already been deleted .. ignore
                                continue;
                            }
                            TransactionWorkMessageAck txnack = new TransactionWorkMessageAck();
                            txnack.setConsumerID(sid);
                            txnack.setDest(dst.getDestinationUID());
                            txnack.setSysMessageID(sysid);
                            txnack.setTransactionAcknowledgement(ta);
                            localwork.addMessageAcknowledgement(txnack);
                        } else {
                            TransactionWorkMessageAck txnack = new TransactionWorkMessageAck();
                            txnack.setConsumerID(sid);
                            txnack.setDest(dst.getDestinationUID());
                            txnack.setSysMessageID(sysid);
                            txnack.setTransactionAcknowledgement(ta);
                            remotework.addMessageAcknowledgement(txnack);
                        }
                    }
                }
            }
        }
        return txnworks;
    }

    public TransactionState updateState(TransactionUID tid, int state, boolean persist) throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, false, TransactionState.NULL, persist, null);
    }

    public TransactionState updateStateCommitWithWork(TransactionUID tid, int state, boolean persist) throws BrokerException {

        if (state != TransactionState.COMMITTED) {
            throw new BrokerException(
                    "Unexpected call TransactionList.updateStateCommitWithWork(tid=" + tid + ", " + TransactionState.toString(state) + ", " + persist + ")");
        }
        TransactionWork[] txnworks = getTransactionWork(tid);
        TransactionState ts = updateState(tid, state, TransactionState.NULL, false, TransactionState.NULL, persist, txnworks[0]);

        Iterator<TransactionWorkMessage> itr = txnworks[0].getSentMessages().iterator();
        while (itr.hasNext()) {
            PacketReference ref = itr.next().getPacketReference();
            ref.setIsStored();
        }
        return ts;
    }

    public TransactionState updateState(TransactionUID tid, int state, boolean onephasePrepare, boolean persist) throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, onephasePrepare, TransactionState.NULL, persist, null);
    }

    public TransactionState updateStatePrepareWithWork(TransactionUID tid, int state, boolean onephasePrepare, boolean persist) throws BrokerException {

        if (state != TransactionState.PREPARED) {
            throw new BrokerException("Unexpected call TransactionList.updateStatePrepareWithWork(tid=" + tid + ", " + TransactionState.toString(state) + ", "
                    + onephasePrepare + ", " + persist + ")");
        }
        TransactionWork[] txnworks = getTransactionWork(tid);
        TransactionState ts = updateState(tid, state, TransactionState.NULL, onephasePrepare, TransactionState.NULL, persist, txnworks[0]);

        Iterator<TransactionWorkMessage> itr = txnworks[0].getSentMessages().iterator();
        while (itr.hasNext()) {
            PacketReference ref = itr.next().getPacketReference();
            ref.setIsStored();
        }
        return ts;
    }

    // Update the state of a transaction.
    // If persist is true, then the state is updated in the persistent store
    // as well.
    public TransactionState updateState(TransactionUID tid, int state, int oldstate, boolean persist) throws BrokerException {
        return updateState(tid, state, oldstate, false, TransactionState.NULL, persist, null);
    }

    public TransactionState updateState(TransactionUID tid, int state, boolean onephasePrepare, int failedToState, boolean persist) throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, onephasePrepare, failedToState, persist, null);
    }

    public TransactionState updateState(TransactionUID id, int state, int oldstate, boolean onephasePrepare, int failToState, boolean persist,
            TransactionWork txnwork) throws BrokerException {

        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new UnknownTransactionException("Update state " + TransactionState.toString(state) + " for unknown transaction: " + id);
        }
        if (ti.isTakeoverLocked()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TXN_LOCKED, id), Status.NOT_FOUND);
        }

        TransactionState ts = null;

        // lock TransactionInformation object
        synchronized (ti) {
            ts = ti.getState();
            if (ts == null) {
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "updateState(): No state for transaction: " + id),
                        Status.GONE);
            }
            // timed out
            if (ts.getState() == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Transaction " + id + ": is has timed out"),
                        Status.TIMEOUT);

            }
            int currstate = ts.getState();
            if (oldstate != TransactionState.NULL && currstate != oldstate) {
                String args[] = { id.toString(), TransactionState.toString(state), TransactionState.toString(ts.getState()),
                        TransactionState.toString(oldstate) };
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_UPDATE_TXN_STATE_CONFLICT, args), Status.CONFLICT);
            }
            ts.setState(state);
            if (state == TransactionState.PREPARED) {
                ts.setOnephasePrepare(onephasePrepare);
            }
            if (state == TransactionState.FAILED && currstate != TransactionState.FAILED) {
                ts.setFailFromState(currstate);
                if (failToState != TransactionState.NULL) {
                    ts.setFailToState(failToState);
                }
            }
        }

        // Update state in persistent store
        if (persist) {
            if (fi.FAULT_INJECTION && (state == TransactionState.COMPLETE || state == TransactionState.PREPARED)) {
                String fault = FaultInjection.FAULT_TXN_UPDATE_1_3_END;
                if (state == TransactionState.PREPARED) {
                    fault = FaultInjection.FAULT_TXN_UPDATE_1_3_PREPARE;
                }
                try {
                    fi.checkFaultAndThrowBrokerException(fault, null);
                } catch (BrokerException e) {
                    fi.unsetFault(fault);
                    throw e;
                }
            }

            try {
                if (txnwork == null) {
                    pstore.updateTransactionState(id, ts, Destination.PERSIST_SYNC);
                } else {
                    pstore.updateTransactionStateWithWork(id, ts, txnwork, Destination.PERSIST_SYNC);
                }
            } catch (IOException e) {
                throw new BrokerException(null, e);
            }
        }

        return ts;
    }

    /**
     * Given an Xid this routine converts it to an internal Transaction Resource ID.
     */
    public TransactionUID xidToUID(JMQXid xid) {

        shareLock.lock();
        try {
            return (TransactionUID) xidTable.get(xid);
        } finally {
            shareLock.unlock();
        }
    }

    /**
     * Given a TransactionUID this routine returns the corresponding Xid.
     */
    public JMQXid UIDToXid(TransactionUID uid) {
        TransactionState ts = retrieveState(uid);
        if (ts != null) {
            return ts.getXid();
        } else {
            return null;
        }
    }

    /**
     * Get a list of transactions that are owned by this broker and in the specified state. This method is a bit expensive,
     * so it shouldn't be called often. It is expected that this will only be called when processing a "recover" or an admin
     * request.
     *
     * If state is &lt; 0 get all transaction IDs.
     *
     * Returns a vector of TransactionUIDs.
     */
    public Vector getTransactions(int state) {
        return getTransactions(translist, state);
    }

    /**
     * Get a list of transactions involves remote broker and owned by this broker and in the specified state.
     */
    public Vector getClusterTransactions(int state) {
        return getTransactions(translist, state, TransactionInfo.TXN_CLUSTER);
    }

    public Vector getRemoteTransactions(int state) {
        return getTransactions(remoteTranslist, state);
    }

    /**
     * Get a list of transactions that are in the specified state. This method is a bit expensive, so it shouldn't be called
     * often. It is expected that this will only be called when processing a "recover" or an admin request.
     *
     * If state is < 0 get all transaction IDs.
     *
     * Returns a vector of TransactionUIDs.
     */
    private Vector getTransactions(Map list, int state) {
        return getTransactions(list, state, TransactionInfo.TXN_NOFLAG);
    }

    private Vector getTransactions(Map list, int state, int type) {
        TransactionUID tid = null;
        TransactionState ts = null;
        Vector v = new Vector();

        // Note: list param is either translist or remoteTranslist so we'll
        // need to get a share lock while iterating over it

        shareLock.lock();
        try {
            Iterator<Map.Entry> iter = list.entrySet().iterator();
            Map.Entry pair = null;
            while (iter.hasNext()) {
                pair = iter.next();
                tid = (TransactionUID) pair.getKey();
                TransactionInformation ti = (TransactionInformation) pair.getValue();
                if (state < 0 && (type == TransactionInfo.TXN_NOFLAG || ti.getType() == type)) {
                    if (ti.isProcessed()) {
                        if (type == TransactionInfo.TXN_LOCAL || type == TransactionInfo.TXN_NOFLAG) {
                            continue;
                        }
                        if (type == TransactionInfo.TXN_CLUSTER && ti.isClusterTransactionBrokersCompleted()) {
                            continue;
                        }
                    }
                    v.add(tid);
                } else {
                    ts = retrieveState(tid);
                    if (ts != null && ts.getState() == state && (type == TransactionInfo.TXN_NOFLAG || ti.getType() == type)) {
                        v.add(tid);
                    }
                }
            }
        } finally {
            shareLock.unlock();
        }

        return v;
    }

    public void addOrphanAck(TransactionUID id, SysMessageID sysid, ConsumerUID sid) {
        addOrphanAck(id, sysid, sid, null);
    }

    public void addOrphanAck(TransactionUID id, SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ti.addOrphanAck(sysid, sid, cid);
        }
    }

    public void removeOrphanAck(TransactionUID id, SysMessageID sysid, ConsumerUID sid) {
        removeOrphanAck(id, sysid, sid, null);
    }

    public void removeOrphanAck(TransactionUID id, SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ti.removeOrphanAck(sysid, sid, cid);
        }
    }

    public Map getOrphanAck(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            return ti.getOrphanAck();
        }
        return null;
    }

    public void loadTransactions() throws BrokerException, IOException {
        // before we do anything else, make sure we dont have any
        // unexpected exceptions

        // FIRST ... look at transaction table (tid,state)
        LoadException load_ex = pstore.getLoadTransactionException();

        if (load_ex != null) {
            // some messages could not be loaded
            LoadException processing = load_ex;
            while (processing != null) {
                TransactionUID tid = (TransactionUID) processing.getKey();
                TransactionInfo ti = (TransactionInfo) processing.getValue();
                if (tid == null && ti == null) {
                    logger.log(Logger.WARNING,
                            "LoadTransactionException: " + "Both key and value for a transactions entry" + " are corrupted with key exception "
                                    + processing.getKeyCause().getMessage() + " and value exception " + processing.getValueCause());
                    processing = processing.getNextException();
                    continue;
                }
                if (tid == null) {
                    // at this point, there is nothing we can do ...
                    // store with valid key
                    // improve when we address 5060661
                    logger.logStack(Logger.WARNING, BrokerResources.W_TRANS_ID_CORRUPT, ti.toString(), processing.getKeyCause());
                    processing = processing.getNextException();
                    continue;
                }
                if (ti == null) {
                    // if we dont know ... so make it prepared
                    logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_TRAN_INFO_CORRUPTED, tid.toString()));
                    TransactionState ts = new TransactionState(AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_NOFLAG) {
                    logger.logStack(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_TXN_TYPE_CORRUPTED,
                            tid + "[" + ti.toString() + "]", TransactionInfo.toString(TransactionInfo.TXN_LOCAL)), processing.getValueCause());
                    TransactionState ts = new TransactionState(AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getTransactionState() == null) {
                    logger.log(Logger.WARNING, BrokerResources.W_TRANS_STATE_CORRUPT, tid, processing.getValueCause());
                    TransactionState ts = new TransactionState(AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_CLUSTER && ti.getTransactionBrokers() == null) {
                    logger.log(Logger.WARNING, BrokerResources.W_CLUSTER_TXN_BROKER_INFO_CORRUPTED, tid, processing.getValueCause());
                    pstore.storeClusterTransaction(tid, ti.getTransactionState(), null, false);
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_REMOTE && ti.getTransactionHomeBroker() == null) {
                    logger.log(Logger.WARNING, BrokerResources.W_REMOTE_TXN_HOME_BROKER_INFO_CORRUPTED, tid, processing.getValueCause());
                    pstore.storeRemoteTransaction(tid, ti.getTransactionState(), null, null, false);
                    processing = processing.getNextException();
                    continue;
                }
                logger.log(Logger.ERROR, "XXXI18N Internal Error: unknown load error for TUID=" + tid + "[" + ti.toString() + "]");

            } // end while
        }

        // now look at acks load exception
        load_ex = pstore.getLoadTransactionAckException();

        if (load_ex != null) {
            // some messages could not be loaded
            LoadException processing = load_ex;
            while (processing != null) {
                TransactionUID tid = (TransactionUID) processing.getKey();
                TransactionAcknowledgement ta[] = (TransactionAcknowledgement[]) processing.getValue();
                if (tid == null && ta == null) {
                    logger.log(Logger.WARNING, "LoadTransactionAckException: " + "both key and value for a Transaction Ack entry" + " are corrupted");
                    processing = processing.getNextException();
                    continue;
                }
                if (tid == null) {
                    // at this point, there is nothing we can do ...
                    // store with valid key
                    // improve when we address 5060661
                    logger.log(Logger.WARNING, BrokerResources.W_TRANS_ID_CORRUPT, Arrays.toString(ta));
                    processing = processing.getNextException();
                    continue;
                }
                // ta == null, nothing we can do, remove it
                logger.log(Logger.WARNING, BrokerResources.W_TRANS_ACK_CORRUPT, tid.toString());
                try {
                    pstore.removeTransactionAck(tid, false);
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING, "Error updating transaction acks " + tid, ex);
                }
            } // end while
        }

        logger.log(Logger.INFO, br.getKString(br.I_PROCESSING_TRANS) + logsuffix);
        // OK -> first load the list of pending
        // transactions
        HashMap trans = pstore.getAllTransactionStates();

        // Write some info about the transactions to the log file
        // for informational purposes.
        logTransactionInfo(trans, AUTO_ROLLBACK, logsuffix);

        // list of transactions which need to be cleaned up
        HashSet clearTrans = new HashSet(trans.size());
        HashSet clearAckOnlyTrans = new HashSet(trans.size());

        HashMap openTransactions = new HashMap();
        HashMap inprocessAcks = new HashMap();
        HashMap committingTransactions = new HashMap();

        // loop through the list of transactions
        // placing each on the various lists

        int prepareCN = 0, commitWaitCN = 0;
        Iterator itr = trans.entrySet().iterator();
        while (itr.hasNext()) {
            try {
                Map.Entry entry = (Map.Entry) itr.next();
                TransactionUID tid = (TransactionUID) entry.getKey();
                TransactionInfo tif = (TransactionInfo) entry.getValue();
                TransactionState ts = tif.getTransactionState();
                TransactionAcknowledgement ta[] = pstore.getTransactionAcks(tid);
                if (ta == null) {
                    ta = new TransactionAcknowledgement[0];
                }
                int state = ts.getState();
                if (DEBUG) {
                    logger.log(Logger.INFO, "Load transaction: TUID=" + tid + "[" + TransactionState.toString(state)
                            + (ts.getCreationTime() == 0 ? "" : " createTime=" + ts.getCreationTime()) + "]");
                }
                switch (state) {
                // no longer valid, ignore
                case TransactionState.CREATED:
                    clearTrans.add(tid);
                    break;
                case TransactionState.PREPARED:
                    // if autorollback, fall through to rollback
                    if (!AUTO_ROLLBACK) {
                        // nothing happens w/ preparedTransactions
                        // they go back into the list
                        // We don't persist this because it is already
                        // in the store
                        addTransactionID(tid, ts, false);
                        if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                            logClusterTransaction(tid, ts, tif.getTransactionBrokers(), true, false);
                            prepareCN++;
                        }
                        openTransactions.put(tid, Boolean.TRUE);
                        if (ts.getOnephasePrepare()) {
                            addDetachedTransactionID(tid);
                        }
                        break;
                    }
                    // rollback -> we didnt complete
                case TransactionState.STARTED:
                case TransactionState.COMPLETE:
                case TransactionState.ROLLEDBACK:
                case TransactionState.FAILED:
                case TransactionState.INCOMPLETE:
                    addTransactionID(tid, ts, false);
                    if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                        logClusterTransaction(tid, ts, tif.getTransactionBrokers(), true, false);
                    }
                    openTransactions.put(tid, Boolean.FALSE);
                    clearTrans.add(tid);
                    ts.setState(TransactionState.ROLLEDBACK);
                    if (state == TransactionState.PREPARED) {
                        clearAckOnlyTrans.add(tid);
                        try {
                            updateState(tid, TransactionState.ROLLEDBACK, true);
                        } catch (Exception e) {
                            logger.logStack(Logger.WARNING, "Unable to update auto-rollback PREPARED transaction " + tid + " state to ROLLEDBACK", e);
                        }
                    }
                    break;
                case TransactionState.COMMITTED:
                    committingTransactions.put(tid, "");
                    if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                        boolean completed = true;
                        TransactionBroker[] brokers = tif.getTransactionBrokers();
                        logClusterTransaction(tid, ts, brokers, false, false);
                        for (int i = 0; brokers != null && i < brokers.length; i++) {
                            completed = brokers[i].isCompleted();
                            if (!completed) {
                                if (DEBUG_CLUSTER_TXN) {
                                    logger.log(logger.INFO, "COMMITTED cluster transaction " + tid + ", incomplete broker:" + brokers[i]);
                                }
                                break;
                            }
                        }
                        if (!completed) {
                            commitWaitCN++;
                        }
                    } else {
                        addTransactionID(tid, ts, false);
                    }
                    clearTrans.add(tid);
                    break;
                default:
                    logger.log(logger.ERROR,
                            "Internal Error unexpected transaction state:" + TransactionState.toString(state) + " TUID=" + tid + ", set to PREPARE");
                    addTransactionID(tid, ts, false);
                    if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                        logClusterTransaction(tid, ts, tif.getTransactionBrokers(), true, false);
                    }
                    updateState(tid, TransactionState.PREPARED, true);
                    openTransactions.put(tid, Boolean.TRUE);
                }

                for (int i = 0; i < ta.length; i++) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, "Load transaction ack " + ta[i] + " [TUID=" + tid + "]");
                    }
                    ConsumerUID cuid = ta[i].getConsumerUID();
                    ConsumerUID scuid = ta[i].getStoredConsumerUID();
                    SysMessageID sysid = ta[i].getSysMessageID();
                    Map imap = (Map) inprocessAcks.get(sysid);
                    if (scuid == null) {
                        logger.log(Logger.WARNING, "Internal Error: " + " Unable to locate stored ConsumerUID :" + Arrays.toString(ta));
                        scuid = cuid;
                    }

                    if (imap == null) {
                        imap = new HashMap();
                        inprocessAcks.put(sysid, imap);
                    }
                    imap.put(scuid, tid);
                    if (openTransactions.get(tid) != null) {
                        TransactionInformation ti = (TransactionInformation) translist.get(tid);
                        if (ti == null) {
                            logger.log(Logger.INFO,
                                    "Unable to retrieve " + " transaction information " + ti + " for " + tid + " we may be clearing the transaction");
                            continue;
                        }
                        if (openTransactions.get(tid) == Boolean.TRUE) {
                            ti.addConsumedMessage(sysid, cuid, scuid);
                        }
                        ti.addOrphanAck(sysid, scuid);
                    }
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR, "Error parsing transaction ", ex);
            }
        }

        {
            Object[] args = { Integer.valueOf(prepareCN), Integer.valueOf(commitWaitCN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_NCLUSTER_TRANS, args));
        }

        HashMap remoteTrans = pstore.getAllRemoteTransactionStates();

        // list of transactions which need to be cleaned up
        HashSet clearRemoteTrans = new HashSet(remoteTrans.size());

        int prepareRN = 0, commitRN = 0, completeRN = 0;
        itr = remoteTrans.entrySet().iterator();
        while (itr.hasNext()) {
            try {
                Map.Entry entry = (Map.Entry) itr.next();
                TransactionUID tid = (TransactionUID) entry.getKey();
                TransactionState ts = (TransactionState) entry.getValue();
                TransactionAcknowledgement ta[] = pstore.getTransactionAcks(tid);
                int state = ts.getState();
                if (DEBUG) {
                    logger.log(Logger.INFO, "Load remote transaction: TUID=" + tid + "[" + TransactionState.toString(state)
                            + (ts.getCreationTime() == 0 ? "" : " createTime=" + ts.getCreationTime()) + "]");
                }
                switch (state) {
                case TransactionState.CREATED:
                    clearRemoteTrans.add(tid);
                    break;
                case TransactionState.PREPARED:
                    prepareRN++;
                    logRemoteTransaction(tid, ts, ta, pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                    openTransactions.put(tid, Boolean.TRUE);
                    break;
                case TransactionState.COMPLETE:
                    if (Globals.getHAEnabled() && ta != null && ta.length > 0) {
                        completeRN++;
                        ts.setState(TransactionState.PREPARED);
                        logRemoteTransaction(tid, ts, ta, pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                        openTransactions.put(tid, Boolean.TRUE);
                        break;
                    }
                case TransactionState.STARTED:
                case TransactionState.ROLLEDBACK:
                case TransactionState.FAILED:
                case TransactionState.INCOMPLETE:
                    openTransactions.put(tid, Boolean.FALSE);
                    clearRemoteTrans.add(tid);
                    break;
                case TransactionState.COMMITTED:
                    commitRN++;
                    logRemoteTransaction(tid, ts, ta, pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                    committingTransactions.put(tid, "");
                    break;
                default:
                    logger.log(logger.ERROR,
                            "Internal Error unexpected transaction state:" + TransactionState.toString(state) + " TUID=" + tid + ", set to PREPARED");
                    logRemoteTransaction(tid, ts, ta, pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                    updateRemoteTransactionState(tid, TransactionState.PREPARED, true, true, true);
                    openTransactions.put(tid, Boolean.TRUE);
                }

                for (int i = 0; i < ta.length; i++) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, "Load remote transaction ack " + ta[i] + " [TUID=" + tid + "]");
                    }
                    ConsumerUID cuid = ta[i].getConsumerUID();
                    ConsumerUID scuid = ta[i].getStoredConsumerUID();
                    SysMessageID sysid = ta[i].getSysMessageID();
                    Map imap = (Map) inprocessAcks.get(sysid);
                    if (scuid == null) {
                        logger.log(Logger.WARNING, "Internal Error: " + " Unable to locate stored ConsumerUID :" + Arrays.toString(ta));
                        scuid = cuid;
                    }

                    if (imap == null) {
                        imap = new HashMap();
                        inprocessAcks.put(sysid, imap);
                    }
                    imap.put(scuid, tid);
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR, "Error parsing remote transaction ", ex);
            }
        }

        if (Globals.getHAEnabled()) {
            Object[] args = { String.valueOf(remoteTrans.size()), String.valueOf(prepareRN), String.valueOf(completeRN), String.valueOf(commitRN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getString(BrokerResources.I_NREMOTE_TRANS_HA, args));
        } else {
            Object[] args = { String.valueOf(remoteTrans.size()), String.valueOf(prepareRN), String.valueOf(commitRN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getString(BrokerResources.I_NREMOTE_TRANS, args));
        }

        // if we have ANY inprocess Acks or openTransactions we have to
        // load the database now and fix it
        if (openTransactions.size() > 0 || inprocessAcks.size() > 0) {
            LinkedHashMap m = DL.processTransactions(inprocessAcks, openTransactions, committingTransactions);
            if (m != null && !m.isEmpty()) {
                Iterator meitr = m.entrySet().iterator();
                while (meitr.hasNext()) {
                    Map.Entry me = (Map.Entry) meitr.next();
                    TransactionInformation ti = (TransactionInformation) translist.get(me.getValue());
                    ti.addPublishedMessage((SysMessageID) me.getKey());
                }
            }
        }

        // OK -> now clean up the cleared transactions
        // this removes them from the disk
        itr = clearTrans.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            logger.log(Logger.DEBUG, "Clearing transaction " + tid);
            if (!clearAckOnlyTrans.contains(tid)) {
                removeTransactionAck(tid);
                removeTransactionID(tid);
            } else {
                removeTransactionAck(tid, true);
            }
        }
        itr = clearRemoteTrans.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            try {
                logger.log(Logger.DEBUG, "Clearing remote transaction " + tid);
                removeRemoteTransactionAck(tid);
                removeRemoteTransactionID(tid, true);
            } catch (Exception e) {
                logger.log(logger.WARNING, "Failed to remove remote transaction TUID=" + tid + ": " + e.getMessage());
            }
        }
    }

    public static void logTransactionInfo(HashMap transactions, boolean autorollback, String logsuffix) {

        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();

        /*
         * Loop through all transactions and count how many are in what state. This is done strictly for informational purposes.
         */
        int nRolledBack = 0; // Number of transactions rolledback
        int nPrepared = 0; // Number of prepared transactions
        int nCommitted = 0; // Number of committed transactions
        if (transactions != null && transactions.size() > 0) {
            Iterator itr = transactions.values().iterator();
            while (itr.hasNext()) {
                TransactionState _ts = ((TransactionInfo) itr.next()).getTransactionState();
                if (_ts.getState() == TransactionState.PREPARED) {
                    nPrepared++;
                    if (autorollback) {
                        nRolledBack++;
                    }
                } else if (_ts.getState() != TransactionState.COMMITTED) {
                    nRolledBack++;
                } else {
                    nCommitted++;
                }
            }

            logger.log(Logger.INFO, br.getKString(br.I_NTRANS, Integer.valueOf(transactions.size()), Integer.valueOf(nRolledBack)) + logsuffix);
            Object[] args = { Integer.valueOf(transactions.size()), Integer.valueOf(nPrepared), Integer.valueOf(nCommitted) };
            logger.log(Logger.INFO, br.getKString(br.I_NPREPARED_TRANS, args) + logsuffix);
            if (nPrepared > 0) {
                if (autorollback) {
                    logger.log(Logger.INFO, br.getKString(br.I_PREPARED_ROLLBACK) + logsuffix);
                } else {
                    logger.log(Logger.INFO, br.getKString(br.I_PREPARED_NOROLLBACK) + logsuffix);
                }
            }
        }
    }

    public void logClusterTransaction(TransactionUID id, TransactionState ts, TransactionBroker[] brokers, boolean exist, boolean persist)
            throws BrokerException {

        logClusterTransaction(id, ts, brokers, exist, persist, null);
    }

    public void logClusterTransaction(TransactionUID id, TransactionState ts, TransactionBroker[] brokers, boolean exist, boolean persist,
            ClusterTransaction clusterTxn) throws BrokerException {
        boolean added = false;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            if (exist) {
                throw new BrokerException(
                        Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "logClusterTransaction(): Unknown transaction: " + id));
            }

            ti = addTransactionID(id, ts, false);
            added = true;
        }
        if (ti != null && ti.isTakeoverLocked()) {
            throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_TXN_LOCKED, id), Status.NOT_FOUND);
        }
        if (ti != null) {
            ti.setClusterTransactionBrokers(brokers);
        }

        if (persist) {
            try {
                if (added) {
                    pstore.storeClusterTransaction(id, ts, brokers, Destination.PERSIST_SYNC);
                } else {
                    if (Globals.isNewTxnLogEnabled()) {
                        ((TxnLoggingStore) pstore).logTxn(clusterTxn);
                    } else {
                        pstore.updateClusterTransaction(id, brokers, Destination.PERSIST_SYNC);
                    }
                }
            } catch (BrokerException e) {
                if (added) {
                    removeTransactionID(id, false, true, false);
                }
                throw e;
            }
        }
    }

    // called by txn home broker
    public void completeClusterTransactionBrokerState(TransactionUID id, int expectedTranState, BrokerAddress broker, boolean persist) throws BrokerException {
        boolean changed = false;
        TransactionBroker b = null;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(br.getKString(br.X_TXN_NOT_FOUND, id), Status.NOT_FOUND);
        }

        // lock TransactionInformation object
        synchronized (ti) {
            if (ti.getState().getState() != expectedTranState) {
                throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION,
                        "updateClusterTransactionBrokerState(): Unexpected cluster transaction state: " + ti.getState()) + ", expect:" + expectedTranState);
            }
            b = ti.getClusterTransactionBroker(broker);
            if (b == null) {
                throw new BrokerException("Can't update transaction broker state " + broker.toString() + ": not found", Status.NOT_FOUND);
            }
            changed = b.copyState(new TransactionBroker(broker, true));
        }

        if (persist && changed) {
            pstore.updateClusterTransactionBrokerState(id, expectedTranState, b, Destination.PERSIST_SYNC);
        }
        txnReaper.clusterTransactionCompleted(id);
    }

    public boolean updateRemoteTransactionState(TransactionUID id, int state, boolean recovery, boolean sync, boolean persist) throws BrokerException {
        boolean updated = false;
        TransactionState ts = null;
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            if (state == TransactionState.COMMITTED) {
                if (txnReaper.hasRemoteTransaction(id)) {
                    if (DEBUG_CLUSTER_TXN) {
                        logger.log(logger.INFO, "Remote transaction " + id + " has already been committed");
                    }
                    return false;
                }
            }
            throw new BrokerException("Update remote transaction state to " + TransactionState.toString(state) + ": transaction " + id
                    + " not found, the transaction may have already been committed.", Status.NOT_FOUND);
        }

        // lock TransactionInformation object
        synchronized (ti) {
            ts = ti.getState();
            if (TransactionState.remoteTransactionNextState(ts, state) != state) {
                throw new BrokerException("Update remote transaction " + id + " to state " + TransactionState.toString(state) + " not allowed",
                        Status.NOT_ALLOWED);
            }

            if (ts.getState() != state) {
                ti.getState().setState(state);
                updated = true;
            }
        }

        if (persist && !Globals.getHAEnabled()) {
            try {
                pstore.updateTransactionState(id, ts, sync && Destination.PERSIST_SYNC);
                if (Globals.isNewTxnLogEnabled()) {

                    ((TxnLoggingStore) pstore).logTxnCompletion(id, state, BaseTransaction.REMOTE_TRANSACTION_TYPE);
                }
            } catch (IOException e) {
                String[] args = { id.toString(), TransactionState.toString(state), e.getMessage() };
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_REMOTE_TXN_STATE_UPDATE_FAIL, args), e);
            }
        }

        if (updated && state == TransactionState.COMMITTED) {
            txnReaper.addRemoteTransaction(id, recovery);
        }
        return updated;
    }

    public RemoteTransactionAckEntry[] getRecoveryRemoteTransactionAcks(TransactionUID id) throws BrokerException {
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getRecoveryTransactionAcks();
    }

    public void logLocalRemoteTransaction(TransactionUID id, TransactionState ts, TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
            boolean recovery, boolean newtxn, boolean persist) throws BrokerException {
        logRemoteTransaction(id, ts, txnAcks, txnHomeBroker, recovery, newtxn, true, persist);
    }

    // called by txn remote broker
    public void logRemoteTransaction(TransactionUID id, TransactionState ts, TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
            boolean recovery, boolean newtxn, boolean persist) throws BrokerException {

        logRemoteTransaction(id, ts, txnAcks, txnHomeBroker, recovery, newtxn, false, persist);
    }

    private void logRemoteTransaction(TransactionUID id, TransactionState ts, TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
            boolean recovery, boolean newtxn, boolean localremote, boolean persist) throws BrokerException {

        RemoteTransactionInformation rti = null;
        boolean added = false;

        exclusiveLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);

            if (newtxn) {
                if (rti != null || inuse_translist.contains(id) || translist.containsKey(id)) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                            BrokerResources.X_TRANSACTIONID_INUSE, (Throwable) null, Status.CONFLICT);
                }
            }
            if (rti == null) {
                rti = new RemoteTransactionInformation(id, ts, txnAcks, txnHomeBroker, recovery, localremote);

                inuse_translist.add(id);
                remoteTranslist.put(id, rti);
                added = true;
            }
        } finally {
            exclusiveLock.unlock();
        }

        if (!added) {
            if (!Globals.getHAEnabled()) {
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                        BrokerResources.X_TRANSACTIONID_INUSE, (Throwable) null, Status.CONFLICT);
            }

            // lock TransactionInformation object
            synchronized (rti) {
                if (!rti.getTransactionHomeBroker().equals(new TransactionBroker(txnHomeBroker))) {
                    throw new BrokerException(
                            "Transaction home broker mismatch:" + txnHomeBroker.toString() + " but existed " + rti.getTransactionHomeBroker());
                }
                if (!recovery) {
                    throw new BrokerException("XXXI18N-Internal Error: unexpected non-recovery, TUID=" + id);
                }
                if (rti.getState().getState() != ts.getState()) {
                    throw new BrokerException("XXXI18N-Internal Error: state mismatch:" + TransactionState.toString(ts.getState()) + ", but exist"
                            + TransactionState.toString(rti.getState().getState()) + "TUID=" + id);
                }
                rti.addRecoveryTransactionAcks(txnAcks);
            }
        }

        if (persist && added) {
            try {
                if (!Globals.getHAEnabled()) {
                    if (!Globals.isNewTxnLogEnabled()) {
                        pstore.storeRemoteTransaction(id, ts, txnAcks, txnHomeBroker, Destination.PERSIST_SYNC);
                    } else {
                        // store the dest ids as well so we can process txns more efficiently on restart
                        // (no need to load all destinations)
                        DestinationUID[] destIds = new DestinationUID[txnAcks.length];
                        for (int i = 0; i < txnAcks.length; i++) {
                            SysMessageID sid = txnAcks[i].getSysMessageID();
                            PacketReference p = DL.get(pstore, sid);
                            DestinationUID destID = null;
                            if (p != null) {
                                destID = p.getDestinationUID();
                            } else {
                                logger.log(Logger.WARNING, "Could not find packet for " + sid);
                            }
                            destIds[i] = destID;
                        }
                        RemoteTransaction remoteTransaction = new RemoteTransaction(id, ts, txnAcks, destIds, txnHomeBroker);
                        ((TxnLoggingStore) pstore).logTxn(remoteTransaction);
                    }

                } else {
                    pstore.updateRemoteTransaction(id, txnAcks, txnHomeBroker, Destination.PERSIST_SYNC);
                }
            } catch (Exception ex) {
                if (added) {
                    exclusiveLock.lock();
                    try {
                        inuse_translist.remove(id);
                        remoteTranslist.remove(id);
                    } finally {
                        exclusiveLock.unlock();
                    }
                }
                logger.logStack(Logger.ERROR, ex.getMessage() + (ex.getCause() == null ? "" : ": " + ex.getCause().getMessage()), ex);
                throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TRANSACTION_STORE_ERROR, id.toString()),
                        BrokerResources.X_TRANSACTION_STORE_ERROR, ex, Status.ERROR);
            }
        }
    }

    public RemoteTransactionAckEntry getRemoteTransactionAcks(TransactionUID id) throws BrokerException {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) {
            return null;
        }
        return rti.getTransactionAcks();
    }

    public void removeRemoteTransactionID(TransactionUID id, boolean persist) throws BrokerException {

        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) {
            throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_REMOTE_TXN_UNKOWN, id.toString()), Status.NOT_FOUND);
        }
        if (!rti.isProcessed()) {
            return;
        }

        try {
            if (persist && !Globals.getHAEnabled()) {
                pstore.removeTransaction(id, false, false);
            }
        } catch (Exception ex) {
            throw new BrokerException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Unable to remove cluster the transaction id " + id), ex);
        }

        exclusiveLock.lock();
        try {
            remoteTranslist.remove(id);
            if (!translist.containsKey(id)) {
                inuse_translist.remove(id);
            }
        } finally {
            exclusiveLock.unlock();
        }
    }

    public TransactionBroker[] getClusterTransactionBrokers(TransactionUID id) throws BrokerException {

        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(br.getKString(br.X_TXN_NOT_FOUND, id), Status.NOT_FOUND);
        }
        return ti.getClusterTransactionBrokers();
    }

    public TransactionBroker getClusterTransactionBroker(TransactionUID id, BrokerAddress broker) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getClusterTransactionBroker(broker);
    }

    public boolean isClusterTransactionBroker(TransactionUID id, UID ssid) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return false;
        }
        return ti.isClusterTransactionBroker(ssid);
    }

    public boolean hasRemoteBroker(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return false;
        }
        TransactionBroker[] brokers = ti.getClusterTransactionBrokers();
        return !(brokers == null || brokers.length == 0);
    }

    public TransactionState getRemoteTransactionState(TransactionUID id) {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti != null) {
            return rti.getState();
        }
        if (txnReaper.hasRemoteTransaction(id)) {
            TransactionState ts = new TransactionState();
            try {
                ts.setState(TransactionState.COMMITTED);
                return ts;
            } catch (Exception e) {
                logger.logStack(logger.ERROR, e.getMessage(), e);
            }
        }
        return null;
    }

    public TransactionBroker getRemoteTransactionHomeBroker(TransactionUID id) {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) {
            return null;
        }
        return rti.getTransactionHomeBroker();
    }

    public ArrayList getPreparedRemoteTransactions(Long timeout) {
        ArrayList tids = new ArrayList();
        TransactionUID tid = null;
        TransactionState ts = null;
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            Iterator itr = remoteTranslist.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry) itr.next();
                tid = (TransactionUID) entry.getKey();
                rti = (RemoteTransactionInformation) entry.getValue();
                if (rti == null) {
                    continue;
                }
                ts = rti.getState();
                if (ts != null && ts.getState() == TransactionState.PREPARED) {
                    if (timeout == null || rti.isPendingTimeout(timeout.longValue())) {
                        tids.add(tid);
                    }
                }
            }
        } finally {
            shareLock.unlock();
        }

        return tids;
    }

    public void pendingStartedForRemotePreparedTransaction(TransactionUID id) {
        RemoteTransactionInformation rti = null;
        TransactionState ts = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
            if (rti != null) {
                ts = rti.getState();
                if (ts != null && ts.getState() == TransactionState.PREPARED) {
                    rti.pendingStarted();
                }
            }
            return;
        } finally {
            shareLock.unlock();
        }
    }

    public void removeAcknowledgement(TransactionUID tid, SysMessageID sysid, ConsumerUID id, boolean rerouted) throws BrokerException {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "Removing acknowledgement with Unknown Transaction ID " + tid));
        }
        ti.removeConsumedMessage(sysid, id, rerouted);
    }

    public HashMap retrieveRemovedConsumedMessages(TransactionUID id, boolean rerouted) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return null;
        }
        return ti.getRemovedConsumedMessages(rerouted);
    }

    public void reapTakeoverCommittedTransaction(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            if (ti.getType() == TransactionInfo.TXN_CLUSTER) {
                txnReaper.addClusterTransaction(id, false, true);
            } else if (ti.getType() == TransactionInfo.TXN_LOCAL) {
                txnReaper.addLocalTransaction(id, false);
            }
        }
    }

    public boolean isLocalTransaction(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return false;
        }
        return (ti.getType() == TransactionInfo.TXN_LOCAL);
    }

    public String getTransactionAsString(TransactionUID id) {
        shareLock.lock();
        try {
            TransactionInformation ti = (TransactionInformation) translist.get(id);
            return (ti == null ? "null" : ti.toString());
        } finally {
            shareLock.unlock();
        }

    }

    public boolean isClusterTransaction(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation) translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            return false;
        }
        return (ti.getType() == TransactionInfo.TXN_CLUSTER);
    }

    public boolean isRemoteTransaction(TransactionUID id) {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation) remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) {
            return false;
        }
        return true;
    }

    /***************************************************************
     * Implements ClusterListener
     ***************************************************************/

    @Override
    public void clusterPropertyChanged(String name, String value) {
    }

    @Override
    public void brokerAdded(ClusteredBroker broker, UID brokerSession) {
    }

    @Override
    public void brokerRemoved(ClusteredBroker broker, UID brokerSession) {
    }

    @Override
    public void masterBrokerChanged(ClusteredBroker oldMaster, ClusteredBroker newMaster) {
    }

    /**
     * Called when the status of a broker has changed. The status may not be accurate if a previous listener updated the
     * status for this specific broker.
     *
     * @param brokerid the name of the broker updated.
     * @param oldStatus the previous status.
     * @param newStatus the new status.
     * @param uid uid associated with the changed broker
     * @param userData data associated with the state change
     */
    @Override
    public void brokerStatusChanged(String brokerid, int oldStatus, int newStatus, UID uid, Object userData) {
        ClusterManager clsmgr = Globals.getClusterManager();
        ClusteredBroker cb = clsmgr.getBroker(brokerid);
        if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "TransactionList:brokerStatusChanged:broker=" + cb + ", oldStatus=" + BrokerStatus.toString(oldStatus) + ", newStatus="
                    + BrokerStatus.toString(newStatus) + ", brokerSession=" + uid + ", userData=" + userData);
        }
        if (BrokerStatus.getBrokerLinkIsUp(newStatus) && !BrokerStatus.getBrokerLinkIsUp(oldStatus)) {
            newlyActivatedBrokers.add(cb.getBrokerURL());
            if (txnReaper != null) {
                txnReaper.wakeupReaperTimer();
            }
        }
    }

    @Override
    public void brokerStateChanged(String brokerid, BrokerState oldState, BrokerState newState) {
    }

    @Override
    public void brokerVersionChanged(String brokerid, int oldVersion, int newVersion) {
    }

    @Override
    public void brokerURLChanged(String brokerid, MQAddress oldAddress, MQAddress newAddress) {
    }

    /****************************************************
     * implement PartitionListener
     *****************************************************/

    /**
     * @param partitionID the partition id
     */
    @Override
    public void partitionAdded(UID partitionID, Object source) {
        if (source instanceof DestinationList) {
            newlyActivatedPartitions.add(partitionID);
            if (txnReaper != null) {
                txnReaper.wakeupReaperTimer();
            }
        }
    }

    /**
     * @param partitionID the partition id
     */
    @Override
    public void partitionRemoved(UID partitionID, Object source, Object destinedTo) {
    }
}

