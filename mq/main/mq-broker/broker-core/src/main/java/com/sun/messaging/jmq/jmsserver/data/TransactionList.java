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
 * @(#)TransactionList.java	1.124 10/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.cluster.api.RemoteTransactionAckEntry;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionListener;
import com.sun.messaging.jmq.jmsserver.persist.api.NoPersistPartitionedStoreImpl;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException;
import com.sun.messaging.jmq.jmsserver.util.UnknownTransactionException;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.timer.MQTimer;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;

public class TransactionList implements ClusterListener, PartitionListener
{
    protected static boolean DEBUG = false;

    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) DEBUG = true;
    }

    public static final boolean DEBUG_CLUSTER_TXN =
                        (Globals.getConfig().getBooleanProperty(
                                   Globals.IMQ + ".cluster.debug.txn") || DEBUG);

    public static final boolean AUTO_ROLLBACK = Globals.getConfig().getBooleanProperty(
                                          Globals.IMQ + ".transaction.autorollback", 
                                          false);

    public static final long TXN_REAPINTERVAL = getTXN_REAPINTERVAL();

    public static final int TXN_REAPLIMIT = 
        Broker.isInProcess() ?
            Globals.getConfig().getIntProperty(Globals.IMQ + ".txn.reapLimit", 0):
            Globals.getConfig().getIntProperty(Globals.IMQ + ".txn.reapLimit", 500);

    public static final int TXN_REAPLIMIT_OVERTHRESHOLD = Globals.getConfig().getIntProperty(
                            Globals.IMQ + ".txn.reapLimitOverThreshold", 100); //in percent

    public static final String XA_TXN_DETACHED_TIMEOUT_PROP = 
                         Globals.IMQ+".transaction.detachedTimeout"; //in secs
    public static final String XA_TXN_DETACHED_RETAINALL_PROP = 
                         Globals.IMQ+".transaction.detachedRetainAll";
    public static final String TXN_PRODUCER_MAX_NUM_MSGS_PROP =
                         Globals.IMQ+".transaction.producer.maxNumMsgs";
    public static final String TXN_CONSUMER_MAX_NUM_MSGS_PROP =
                         Globals.IMQ+".transaction.consumer.maxNumMsgs";

    /**
     *  Max number of msgs that can be produced/consumed in a txn
     */
    protected static final int defaultProducerMaxMsgCnt = Globals.getConfig().
        getIntProperty(TXN_PRODUCER_MAX_NUM_MSGS_PROP, 10000);
    protected static final int defaultConsumerMaxMsgCnt = Globals.getConfig().
        getIntProperty(TXN_CONSUMER_MAX_NUM_MSGS_PROP, 1000);

    TransactionReaper txnReaper = null;
    DetachedTransactionReaper detachedTxnReaper = null;

    HashSet inuse_translist = null;
    HashMap translist = null;
    HashMap remoteTranslist = null;
    HashMap xidTable = null;  // Maps XIDs to UIDs

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
        long v = Globals.getConfig().getLongProperty(
                     Globals.IMQ + ".txn.reapInterval", 900)*1000L;
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
            logsuffix = " ["+pstore+"]";
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

            if (Globals.isNewTxnLogEnabled() || 
                ((pstore instanceof TxnLoggingStore) &&
                 ((TxnLoggingStore)pstore).isTxnConversionRequired())) {
                // start replaying the transaction log, if present
                ((TxnLoggingStore)pstore).init();
            } 
            if ((pstore instanceof TxnLoggingStore) && 
                ((TxnLoggingStore)pstore).isTxnConversionRequired()) {
                ((TxnLoggingStore)pstore).convertTxnFormats(this);
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
            logger.log(logger.INFO, br.getKString(br.I_TXN_LOADING_COMPLETE)+logsuffix); 

        } catch (Exception ex) {
            String emsg = br.getKString(br.W_TRANS_LOAD_ERROR);
            logger.logStack(Logger.INFO, emsg, ex);
            throw new BrokerException(emsg, ex);
	}
    }
    public String toString() {
        if (DL == null || !DL.isPartitionMode()) {
            return super.toString();
        }
        return logsuffix;
    }

    /**
     * @param tid 
     * @return null if not found
     * @exception BrokerException if error
     */
    public static TransactionList getTransListByTID(TransactionUID tid)
    throws BrokerException {
        TransactionList tl = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            if (tls[i].retrieveState(tid, true) != null) {
                if (tl != null) {
                    throw new BrokerException(Globals.getBrokerResources().getString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "More than 1 TransactionList exist for transaction "+tid+": "+tl+", "+tls[i]));
                }
                tl = tls[i];
            }
        }
        return tl;
    }

    public static Object[] getTransListAndRemoteState(TransactionUID tid)
    throws BrokerException {
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


    public static Object[] getTransactionByCreator(String creator)
    throws BrokerException {
        TransactionUID tid = null;
        TransactionList tl = null;
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        for (int i = 0; i < tls.length; i++) {
            tid = tls[i].getTransaction(creator);
            if (tid != null) {
                if (tl != null) {
                    throw new BrokerException(Globals.getBrokerResources().getString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "More than 1 TransactionList exist for creator "+creator+": "+tl+", "+tls[i]));
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
    public static TransactionList getTransListByPartitionID(UID pid)
    throws BrokerException {

        TransactionList tl = null;
        PartitionedStore ps = new NoPersistPartitionedStoreImpl(pid);
        TransactionList[] tls = Globals.getDestinationList().getTransactionList(ps);
        tl = tls[0];
        return tl;
    }

    /**
     * @return null if none found
     */
    public static List<Object[]> getTransListsAndRemoteTranStates(TransactionUID tid) {

         ArrayList<Object[]> ret = new ArrayList<Object[]>();
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
     * @return Object[0] translist, Object[1] TransactionState;
     *         null if the transaction not found
     */
    public static Object[] getTransListAndState(TransactionUID tid,
                               IMQConnection con, boolean inquiry, 
                               boolean newtran) {

         Object[] oo = new Object[2];
         oo[0] = null;
         oo[1] = null;
         TransactionList[] tls = null;
         TransactionList tl = null;
         TransactionState ts = null;
         if (con != null) {
             tls = Globals.getDestinationList().getTransactionList(
                       con.getPartitionedStore());
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
      * @con can be null
      */
     public static Object[] mapXidToTid(JMQXid xid, IMQConnection con) {

	 TransactionList[] tls = null;
	 TransactionList tl = null;
	 TransactionState ts = null;
	 TransactionUID tid = null;
	 if (con != null) {
	     tls = Globals.getDestinationList().getTransactionList(con.getPartitionedStore());
	     tl = tls[0];
             if (tl != null) {
                 tid = tl.xidToUID(xid);
                 if (tid != null) {
                     Object[] oo =  new Object[2];
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
                 Object[] oo =  new Object[2];
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

    public Map getTransactionListMap()
    {
    	return translist;
    }

    public Map getRemoteTransactionListMap()
    {
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
            logger.log(Logger.INFO,  "Closing transaction list "+this);
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
        RemoteTransactionInformation  rti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        Hashtable ht = new Hashtable();
        if (ti == null && rti == null) {
            ht.put(id.toString(), "UNKNOWN TID");
            return ht;
        }

        if (ti != null) ht.put(id.toString(), ti.getDebugState());
        if (rti != null) ht.put(id.toString()+"(remote)", rti.getDebugState());
        return ht;
    }

    public void unlockTakeoverTxns(List txns) {
        TransactionInformation ti = null;
        Iterator itr = txns.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            if (tid == null) continue;

            shareLock.lock();
            try {
                ti = (TransactionInformation)translist.get(tid);
            } finally {
                shareLock.unlock();
            }

            if (ti == null) continue;
            ti.releaseTakeoverLock();
        }
    }

    // returns transaction acks
    public Map loadTakeoverTxns(List txns,
        List remoteTxns, Map<String, String> msgs)
        throws BrokerException, IOException {

        logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
            BrokerResources.I_PROCESSING_TAKEOVER_TRANS, Integer.valueOf(txns.size())));

        // hey process through the states
        Iterator itr = txns.iterator();
        Map acks = new HashMap();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            TransactionInfo ti = null; 
            try {
                ti = pstore.getTransactionInfo(tid);
            } catch (Exception e) {
                String em = "Failed to get transaction "+tid+
                            " information from store after takeover";
                logger.logStack(logger.ERROR, 
                                BrokerResources.E_INTERNAL_BROKER_ERROR, em, e);
                throw new BrokerException(em);
            }
            TransactionState ts = ti.getTransactionState();
            logger.log(Logger.DEBUG, "Processing transaction " + tid +ti.toString()); 
            try {
                 if (ts.getState() != TransactionState.COMMITTED &&
                     ts.getState() != TransactionState.PREPARED) {
                     pstore.removeTransactionAck(tid, false);
                 }
                 TransactionAcknowledgement  ta[] = pstore.getTransactionAcks(tid);
                 logger.log(Logger.DEBUG, "Processing transaction acks " 
                               + tid + " number=" + ta.length);
                 List l = Arrays.asList(ta);
                 acks.put(tid, l);
                 addTransactionID(tid, ts, true, ti.getType(), false);
                 if (ti.getType() == TransactionInfo.TXN_CLUSTER) {
                     logClusterTransaction(tid, ts, 
                         ti.getTransactionBrokers(), true, false);
                 }
                 if (ts.getState() == TransactionState.PREPARED &&
                     ts.getOnephasePrepare()) {
                     addDetachedTransactionID(tid);
                 }
            } catch (Exception ex) {
                 logger.logStack(Logger.ERROR,
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "error taking over " 
                             + tid, ex);
                 acks.remove(tid);
            }
        }

        itr = remoteTxns.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID) itr.next();
            if (txns.contains(tid)) continue;
            TransactionInfo ti = null;
            try {
                ti = pstore.getTransactionInfo(tid);
            } catch (Exception e) {
                String em = "Failed to get remote transaction "+tid+
                            " information from store after takeover";
                logger.logStack(logger.ERROR, 
                                BrokerResources.E_INTERNAL_BROKER_ERROR, em, e);
                throw new BrokerException(em);
            }
            TransactionState ts = ti.getTransactionState();
            if (DEBUG || DEBUG_CLUSTER_TXN) {
                logger.log(Logger.INFO, Globals.getBrokerResources().getString(
                           BrokerResources.I_PROCESSING_REMOTE_TXN, 
                           tid+"["+TransactionState.toString(ts.getState())+"]"+ti.toString()));
            } else {
                logger.log(Logger.INFO, Globals.getBrokerResources().getString(
                           BrokerResources.I_PROCESSING_REMOTE_TXN, 
                           tid+"["+TransactionState.toString(ts.getState())+"]"));
            }
            try {
                 TransactionAcknowledgement  ta[] = pstore.getTransactionAcks(tid);
                 ArrayList l = new ArrayList();
                 Iterator mitr = null;
                 for (int i = 0; i < ta.length; i++) {
                     mitr = msgs.keySet().iterator();
                     while (mitr.hasNext()) {
                         String msgID = (String)mitr.next();
                         if (msgID.equals(ta[i].getSysMessageID().toString())) {
                             l.add(ta[i]);
                             if (DEBUG || DEBUG_CLUSTER_TXN) {
                                 logger.log(Logger.INFO,
                                 "Processing remote transaction ack for TUID=" +
                                  tid + " " + ta[i].toString());
                             }
                         }
                     }
                 }
                 if ((l.size() > 0)) {
                     acks.put(tid, l);
                     logger.log(Logger.INFO, "Processing remote transaction "+tid+
                     "["+TransactionState.toString(ts.getState())+"] with acks "+l.size());
                     if (ts.getState() != TransactionState.PREPARED && 
                         ts.getState() != TransactionState.COMMITTED) {
                         ts.setState(TransactionState.PREPARED);
                     }
                 }
                 logRemoteTransaction(tid, ts, 
                       (TransactionAcknowledgement[])l.toArray(
                                 new TransactionAcknowledgement[l.size()]),
                       ti.getTransactionHomeBroker(), true, false, false);
                 if (ts.getState() == TransactionState.COMMITTED) {
                     txnReaper.addRemoteTransaction(tid, true);
                 }
            } catch (Exception ex) {
                 logger.logStack(Logger.ERROR,
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "error taking over "
                             + tid, ex);
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
                TransactionUID tid = (TransactionUID)itr.next();
                ht.put(tid.toString(), getDebugState(tid));
            }
            ht.put("TransactionCount(remote)", Integer.valueOf(remoteTranslist.size()));
            itr = remoteTranslist.keySet().iterator();
            while (itr.hasNext()) {
                TransactionUID tid = (TransactionUID)itr.next();
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
                Map.Entry me = (Map.Entry)itr.next();
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
                TransactionInformation info = (TransactionInformation)itr.next();
                TransactionState ts = info.getState();
                String creator = ts.getCreator();
                if (creator != null && creator.equals(id))
                    return info.getTID();
            }
        } finally {
            shareLock.unlock();
        }
        return null;
    }

    public void addTransactionID(TransactionUID id, TransactionState ts)
        throws BrokerException
    {
        addTransactionID(id, ts, false, TransactionInfo.TXN_LOCAL, true);
    }

    public TransactionInformation addTransactionID(TransactionUID id,
        TransactionState ts, boolean persist) throws BrokerException  {
        return addTransactionID(id, ts, false, TransactionInfo.TXN_LOCAL, persist);
    }

    private TransactionInformation addTransactionID(TransactionUID id,
        TransactionState ts, boolean takeover, int type, boolean persist)
        throws BrokerException  {
        JMQXid xid = ts.getXid();

        shareLock.lock();
        try {
            if (inuse_translist.contains(id)) {
                if (!takeover || type != TransactionInfo.TXN_CLUSTER ||
                     translist.containsKey(id) || !remoteTranslist.containsKey(id)) {
                        throw new BrokerException(
                            Globals.getBrokerResources().getKString(
                            BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                            BrokerResources.X_TRANSACTIONID_INUSE,
                            (Throwable) null, Status.CONFLICT);
                }
            }

            // If transaction is an XA (has an xid) save it for reverse mapping
            if (xid != null && xidTable.containsKey(xid)) {
                // Xid already in use
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_TRANSACTIONID_INUSE,
                    id.toString() + "[Xid=" + xid.toString() + "]"),
                    BrokerResources.X_TRANSACTIONID_INUSE,
                    (Throwable) null,
                    Status.CONFLICT);
            }
        } finally {
            shareLock.unlock();
        }

        try {
            if (persist) {
                pstore.storeTransaction(id, ts, Destination.PERSIST_SYNC);
            }
        } catch (Exception ex) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_TRANSACTION_STORE_ERROR,
                    id.toString()),
                BrokerResources.X_TRANSACTION_STORE_ERROR,
                (Throwable) ex,
                Status.ERROR);
        }

        TransactionInformation ti = new TransactionInformation(id, ts, persist);
        if (takeover) ti.getTakeoverLock();

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
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ts = ti.getState();
            if (ts != null) {
                return (ts.isXA());
            }
        }
        throw new BrokerException("Transaction "+id + "not found", Status.NOT_FOUND);
    }

    public void removeTransaction(TransactionUID id, boolean noremove)
                throws BrokerException  
    {
        removeTransactionID(id, noremove, false, true);
    }

    public void removeTransactionID(TransactionUID id)
                throws BrokerException  
    {
        removeTransactionID(id, false, false, true);
    }

    protected void reapTransactionID(TransactionUID id, boolean noremove) 
                                throws BrokerException 
    {
        removeTransactionID(id, noremove, true, true);
    }

    private void removeTransactionID(TransactionUID id, boolean noremove, 
                                     boolean fromReaper, boolean persist) 
                                     throws BrokerException
    {
        TransactionState ts = null;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ts = ti.getState();
            if (!fromReaper && ts != null &&
                ts.getState() == TransactionState.COMMITTED) {
                if (ti.getType() == TransactionInfo.TXN_CLUSTER) {
                    ti.processed();
                    txnReaper.addClusterTransaction(id, noremove);
                    return;
                } 
                if (ti.getType() ==  TransactionInfo.TXN_LOCAL) {
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
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unable to remove the transaction id " + id), ex);
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

    public void removeRemoteTransactionAck(TransactionUID id)
        throws BrokerException 
    {
        if (Globals.getHAEnabled()) return;
        removeTransactionAck(id, true);
    }

    //To be called only if acks to be removed when transaction ID removes
    public void removeTransactionAck(TransactionUID id)
                                    throws BrokerException 
    {
        //remove with transactionID
        return;
    }

    public void removeTransactionAck(TransactionUID id, boolean persist) 
                                       throws BrokerException 
    {
        try {
            pstore.removeTransactionAck(id, false);
        } catch (Exception ex) {
            throw new BrokerException(br.getKString(
                br.X_RM_TXN_ACK_IN_STORE, id, ex.getMessage()));
        }
    }

    public void addDetachedTransactionID(TransactionUID tid) {
        detachedTxnReaper.addDetachedTID(tid);
    }

    public void addMessage(PacketReference ref)
        throws BrokerException {

        TransactionUID tid = ref.getTransactionID();
        if (tid == null) {
            return;
        }
        SysMessageID sysid = ref.getSysMessageID(); 

        boolean delaypersist = false;
        if (ref.isPersistent() && !ref.getNeverStore() &&
            Globals.isMinimumPersistLevel2()) {
            TransactionState ts = retrieveState(tid, true);
            if (ts == null) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    br.X_RECEIVED_MSG_WITH_UNKNOWN_TID, sysid, tid), Status.GONE);
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

    public void addMessage(TransactionUID id, SysMessageID sysid, boolean anyState)
        throws BrokerException {

        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                br.X_RECEIVED_MSG_WITH_UNKNOWN_TID, sysid, id), Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            int state = info.getState().getState();
            if (state == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ id + ": is has timed out "),
                    Status.TIMEOUT);

            }
            if (!anyState && state != TransactionState.STARTED) {
                // bad state
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ id + 
                        "["+TransactionState.toString(state)+"]: is not in "+
                         TransactionState.toString(TransactionState.STARTED)+" state"),
                    Status.PRECONDITION_FAILED);

            }
            info.addPublishedMessage(sysid);
        }
    }

    public Hashtable getTransactionMap(TransactionUID tid, boolean ext)
         throws BrokerException
    {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "received acknowledgement with Unknown Transaction ID "+ tid ),
                Status.GONE);
        }

        TransactionState ts = info.getState();
        if (ts == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
            BrokerResources.X_INTERNAL_EXCEPTION,
                "received acknowledgement with Unknown Transaction state "+ tid ),
                Status.ERROR);
        }

        Hashtable ht = new Hashtable();
        ht.put("JMQAutoRollback", Integer.valueOf(ts.getType().intValue()));
        if (ts.getXid() != null)
            ht.put("JMQXid", ts.getXid().toString());
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

    public boolean checkAcknowledgement(TransactionUID tid,
       SysMessageID sysid, ConsumerUID cuid)
         throws BrokerException
    {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(br.getKString(
                br.X_TRAN_NOT_FOUND_FOR_ACK, tid, "["+sysid+", "+cuid+"]"),
                Status.GONE);
        }

        if (info.getState().getState() == TransactionState.TIMED_OUT) {
            // bad state
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ cuid + ": is has timed out "),
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
                info = (TransactionInformation)itr.next();
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
    public boolean addAcknowledgement(TransactionUID tid,
        SysMessageID sysid, ConsumerUID intid, ConsumerUID sid)
        throws BrokerException {

        PacketReference pr = DL.get(null, sysid);
        if (pr == null) { 
            if (!DL.isLocked(pstore, sysid)) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
            BrokerResources.I_ACK_FAILED_MESSAGE_GONE, 
            ""+sysid+"["+intid+":"+sid+"]TUID="+tid), Status.CONFLICT);
            } else {
            throw new BrokerException(Globals.getBrokerResources().getKString(
            BrokerResources.I_ACK_FAILED_MESSAGE_LOCKED, 
            ""+sysid+"["+intid+":"+sid+"]TUID="+tid), Status.CONFLICT);
            }
        }
        boolean persist = sid.shouldStore() && 
                          pr.isPersistent() && pr.isLocal() && 
                          (!DL.isPartitionMode() || 
                           pr.getPartitionedStore().getPartitionID().
                               equals(pstore.getPartitionID()));
        return addAcknowledgement(tid, sysid, intid, sid, false, persist);
    }

    public boolean addAcknowledgement(TransactionUID tid, 
        SysMessageID sysid, ConsumerUID intid, ConsumerUID sid,
        boolean anystate, boolean persist)
        throws BrokerException
    {
        boolean isXA = false;
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "Received acknowledgement with Unknown Transaction ID "+ tid),
                Status.GONE);
        }

        TransactionState ts = null;
        // lock TransactionInformation object
        synchronized (info) {
            ts = info.getState();
            int state = ts.getState();
            if (state == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ tid + " has timed out "),
                    Status.TIMEOUT);

            }
            if (!anystate && state != TransactionState.STARTED) {
                // bad state
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ tid + " has state ["+
					TransactionState.toString(state)+"],  not in ["+
					TransactionState.toString(TransactionState.STARTED)+"] state"), 
                    Status.PRECONDITION_FAILED);

            }
            info.addConsumedMessage(sysid, intid, sid);
            isXA = info.getState().isXA();
        }

        boolean delaypersist = false;
        if ((ts.getType() != AutoRollbackType.NEVER &&
             Globals.isMinimumPersistLevel2()) || 
            Globals.isNewTxnLogEnabled()) {
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
            pstore.storeTransactionAck(tid, 
                 new TransactionAcknowledgement(sysid, intid, sid), false);
        }
        return isXA;
    }

    public void setAckBrokerAddress(TransactionUID tid, 
                SysMessageID sysid, ConsumerUID id, BrokerAddress addr) 
                throws BrokerException
    {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "Unknown Transaction ID "+ tid + " for transaction ack ["+sysid+":"+id+"]"+addr),
                Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            int state = info.getState().getState();
            if (state == TransactionState.TIMED_OUT) {
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ tid + ": has timed out "),
                    Status.TIMEOUT);
            }
            if (state != TransactionState.STARTED) {
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION, "Transaction "+ tid + 
                        "["+TransactionState.toString(state)+"]: is not in "+
                         TransactionState.toString(TransactionState.STARTED)+" state"),
                    Status.PRECONDITION_FAILED);
            }
            info.setAckBrokerAddress(sysid, id, addr);
        }
    }

    public BrokerAddress getAckBrokerAddress(TransactionUID tid, 
                             SysMessageID sysid, ConsumerUID id) 
        throws BrokerException
    {
        TransactionInformation info = null;

        shareLock.lock();
        try {
            info = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (info == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "Unknown Transaction ID "+ tid + " for transaction ack ["+sysid+":"+id+"]"),
                Status.GONE);
        }

        // lock TransactionInformation object
        synchronized (info) {
            return info.getAckBrokerAddress(sysid, id);
        }
    }

    public List retrieveSentMessages(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getPublishedMessages();
    }

    public int retrieveNSentMessages(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return 0;
        return ti.getNPublishedMessages();
    }

    public HashMap retrieveConsumedMessages(TransactionUID id)
    {
        return retrieveConsumedMessages(id, false);
    }

    public HashMap retrieveConsumedMessages(TransactionUID id, boolean inrollback)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getConsumedMessages(inrollback);
    }

    // this retrieves a mapping of consumerUID to stored consumerUID
    // the stored consumerUId is the ID we use to store ack info
    // for a durable
    public HashMap retrieveStoredConsumerUIDs(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getStoredConsumerUIDs();
    }

    public HashMap retrieveAckBrokerAddresses(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getAckBrokerAddresses();
    }

    public int retrieveNConsumedMessages(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return 0;
        return ti.getNConsumedMessages();
    }

    public int retrieveNRemoteConsumedMessages(TransactionUID id)
    {
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return 0;
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
            ti = (TransactionInformation)translist.get(id);
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
            Globals.getLogger().log(Logger.WARNING,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_TXN_LOCKED, id));
            return null;
        }
        if (ti.isProcessed()) {
            return null;
        }
        return ti.getState();
    }

    public TransactionWork[] getTransactionWork(TransactionUID tid)
    throws BrokerException {

        List plist = retrieveSentMessages(tid);
        HashMap cmap = retrieveConsumedMessages(tid);
        HashMap sToCmap = retrieveStoredConsumerUIDs(tid);

        TransactionWork localwork = new TransactionWork();
        TransactionWork remotework = new TransactionWork();
        TransactionWork[] txnworks = new TransactionWork[2];
        txnworks[0] = localwork;
        txnworks[1] = remotework;

        //iterate produced messages
        for (int i = 0; plist != null && i < plist.size(); i++) {
            SysMessageID sysid = (SysMessageID) plist.get(i);
            PacketReference ref = DL.get(pstore, sysid);
            if (ref == null || ref.isDestroyed()) {
                logger.log(Logger.WARNING,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.W_MSG_REMOVED_BEFORE_SENDER_COMMIT, sysid));
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

        //iterate consumed messages
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
                        intid = (ConsumerUID)interests.get(0);
                        sid = (ConsumerUID)sToCmap.get(intid);
                        if (sid == null) {
                            sid = intid;
                        }
                    }
                    if (!DL.isLocked(pstore, sysid)) {
                         throw new BrokerException(Globals.getBrokerResources().getKString(
                         BrokerResources.I_ACK_FAILED_MESSAGE_GONE,
                         ""+sysid+"["+intid+":"+sid+"]TUID="+tid), Status.CONFLICT);
                    } else {
                        throw new BrokerException(Globals.getBrokerResources().getKString(
                            BrokerResources.I_ACK_FAILED_MESSAGE_LOCKED,
                            ""+sysid+"["+intid+":"+sid+"]TUID="+tid), Status.CONFLICT);
                    }
                }
                Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                                     ref.getDestinationUID());
                Destination dst = ds[0];

                for (int i = 0; i < interests.size(); i++) {
                    ConsumerUID intid = (ConsumerUID) interests.get(i);
                    ConsumerUID sid = (ConsumerUID) sToCmap.get(intid);
                    if (sid == null) {
                        sid = intid;
                    }
                    if (sid.shouldStore() && ref.isPersistent()) {
                        TransactionAcknowledgement ta = new TransactionAcknowledgement(sysid, intid, sid);
                        if (ref.isLocal() &&
                            (!DL.isPartitionMode() ||
                             ref.getPartitionedStore().getPartitionID().equals(pstore.getPartitionID()))) {
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

    public TransactionState
    updateState(TransactionUID tid, int state, boolean persist)
    throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, 
                           false, TransactionState.NULL, persist, null);
    }

    public TransactionState
    updateStateCommitWithWork(TransactionUID tid, int state, boolean persist)
    throws BrokerException {

        if (state != TransactionState.COMMITTED) {
            throw new BrokerException("Unexpected call TransactionList.updateStateCommitWithWork(tid="+
                                       tid+", "+TransactionState.toString(state)+", "+persist+")");
        }    
        TransactionWork[] txnworks = getTransactionWork(tid);
        TransactionState ts = updateState(tid, state, TransactionState.NULL, 
                                  false, TransactionState.NULL, persist, txnworks[0]);

        Iterator<TransactionWorkMessage> itr = txnworks[0].getSentMessages().iterator();
        while (itr.hasNext()) {
            PacketReference ref = itr.next().getPacketReference();
            ref.setIsStored();
        }
        return ts;
    }

    public TransactionState
    updateState(TransactionUID tid, int state, boolean onephasePrepare, boolean persist)
    throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, 
                           onephasePrepare, TransactionState.NULL, persist, null);
    }

    public TransactionState 
    updateStatePrepareWithWork(TransactionUID tid, int state, 
                               boolean onephasePrepare, boolean persist)
                               throws BrokerException {

        if (state != TransactionState.PREPARED) {
            throw new BrokerException("Unexpected call TransactionList.updateStatePrepareWithWork(tid="+
                          tid+", "+TransactionState.toString(state)+", "+onephasePrepare+", "+persist+")");
        }    
        TransactionWork[] txnworks = getTransactionWork(tid);
        TransactionState ts = updateState(tid, state, TransactionState.NULL, 
                                  onephasePrepare, TransactionState.NULL, persist, txnworks[0]);

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
    public TransactionState
    updateState(TransactionUID tid, int state, int oldstate, boolean persist)
    throws BrokerException {
        return updateState(tid, state, oldstate, false, TransactionState.NULL, persist, null);
    }

    public TransactionState
    updateState(TransactionUID tid, int state, boolean onephasePrepare, 
                int failedToState,  boolean persist)
                throws BrokerException {
        return updateState(tid, state, TransactionState.NULL, 
                           onephasePrepare, failedToState, persist, null);
    }

    public TransactionState
    updateState(TransactionUID id, int state, int oldstate, 
                boolean onephasePrepare, int failToState, 
                boolean persist, TransactionWork txnwork)
                throws BrokerException {

        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new UnknownTransactionException("Update state "+
              TransactionState.toString(state)+ " for unknown transaction: " + id);
        }
        if (ti.isTakeoverLocked()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
            BrokerResources.X_TXN_LOCKED, id), Status.NOT_FOUND);
        }

        TransactionState ts = null;

        // lock TransactionInformation object
        synchronized (ti) {
            ts = ti.getState();
            if (ts == null) {
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "updateState(): No state for transaction: " + id),
                    Status.GONE);
            }
            // timed out
            if (ts.getState() == TransactionState.TIMED_OUT) {
                // bad state
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,"Transaction "+ id
                          + ": is has timed out"),
                    Status.TIMEOUT);

            }
            int currstate = ts.getState();
            if (oldstate != TransactionState.NULL && currstate != oldstate) {
                String args[] = {id.toString(), TransactionState.toString(state),
                                                TransactionState.toString(ts.getState()),
                                                TransactionState.toString(oldstate)};
                throw new BrokerException(Globals.getBrokerResources().getKString(
                      BrokerResources.X_UPDATE_TXN_STATE_CONFLICT, args), Status.CONFLICT);
            }
            ts.setState(state);
            if (state == TransactionState.PREPARED) {
                ts.setOnephasePrepare(onephasePrepare);
            }
            if (state == TransactionState.FAILED && currstate != TransactionState.FAILED) {
                ts.setFailFromState(currstate);
                if (failToState != TransactionState.NULL) ts.setFailToState(failToState);
            }
        }

        // Update state in persistent store
        if (persist) {
            if (fi.FAULT_INJECTION && 
                (state == TransactionState.COMPLETE || state == TransactionState.PREPARED)) {
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
     * Given an Xid this routine converts it to an internal Transaction
     * Resource ID.
     */
    public TransactionUID xidToUID(JMQXid xid) {

        shareLock.lock();
        try {
            return (TransactionUID)xidTable.get(xid);
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
     * Get a list of transactions that are owned by this broker and in 
     * the specified state. This method is a bit expensive, so it shouldn't
     * be called often.  It is expected that this will only be called when 
     * processing a "recover" or an admin request.
     *
     * If state is < 0 get all transaction IDs.
     *
     * Returns a vector of TransactionUIDs.
     */
    public Vector getTransactions(int state) {
        return getTransactions(translist, state);
    }

    /**
     * Get a list of transactions involves remote broker and owned by
     * this broker and in the specified state.
     */
    public Vector getClusterTransactions(int state) {
        return getTransactions(translist, state, TransactionInfo.TXN_CLUSTER);
    }

    public Vector getRemoteTransactions(int state) {
        return getTransactions(remoteTranslist, state);
    }

    /**
     * Get a list of transactions that are in the specified state.
     * This method is a bit expensive, so it shouldn't be called often.
     * It is expected that this will only be called when processing a
     * "recover" or an admin request.
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
            Map.Entry pair  = null;
            while (iter.hasNext()) {
                pair = iter.next();
                tid = (TransactionUID)pair.getKey();
                TransactionInformation ti = (TransactionInformation)pair.getValue();
                if (state < 0 &&
                    (type == TransactionInfo.TXN_NOFLAG || ti.getType() == type)) {
                    if (ti.isProcessed()) {
                        if (type == TransactionInfo.TXN_LOCAL ||
                            type == TransactionInfo.TXN_NOFLAG) {
                            continue;
                        }
                        if (type == TransactionInfo.TXN_CLUSTER &&
                            ti.isClusterTransactionBrokersCompleted()) {
                            continue;
                        }
                    }
                    v.add(tid);
                } else {
                    ts = retrieveState(tid);
                    if (ts != null && ts.getState() == state &&
                        (type == TransactionInfo.TXN_NOFLAG || ti.getType() == type)) {
                        v.add(tid);
                    }
                }
            }
        } finally {
            shareLock.unlock();
        }

        return v;
    }

    public void addOrphanAck(TransactionUID id,
           SysMessageID sysid, ConsumerUID sid)
    {
        addOrphanAck(id, sysid, sid, null);
    }

    public void addOrphanAck(TransactionUID id,
           SysMessageID sysid, ConsumerUID sid, ConsumerUID cid)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ti.addOrphanAck(sysid, sid, cid);
        }
    }

    public void removeOrphanAck(TransactionUID id,
           SysMessageID sysid, ConsumerUID sid)
    {
        removeOrphanAck(id, sysid, sid, null);
    }

    public void removeOrphanAck(TransactionUID id,
           SysMessageID sysid, ConsumerUID sid, ConsumerUID cid)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            ti.removeOrphanAck(sysid, sid, cid);
        }
    }


    public Map getOrphanAck(TransactionUID id)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti != null) {
            return ti.getOrphanAck();
        }
        return null;
    }

    public void loadTransactions()
        throws BrokerException, IOException
    {
        // before we do anything else, make sure we dont have any
        // unexpected exceptions

        // FIRST ... look at transaction table (tid,state)
        LoadException load_ex = pstore.getLoadTransactionException();

        if (load_ex != null) {
            // some messages could not be loaded
            LoadException processing = load_ex;
            while (processing != null) {
                TransactionUID tid = (TransactionUID)processing.getKey();
                TransactionInfo ti = (TransactionInfo)processing.getValue();
                if (tid == null && ti == null) {
                    logger.log(Logger.WARNING, "LoadTransactionException: "+
                               "Both key and value for a transactions entry"
                               + " are corrupted with key exception "+ 
                               processing.getKeyCause().getMessage()+
                               " and value exception "+ processing.getValueCause());
                    processing = processing.getNextException();
                    continue;
                }
                if (tid == null ) { 
                    // at this point, there is nothing we can do ...
                    // store with valid key
                    // improve when we address 5060661
                    logger.logStack(Logger.WARNING, BrokerResources.W_TRANS_ID_CORRUPT,
                                    ti.toString(), processing.getKeyCause());
                    processing = processing.getNextException();
                    continue;
                }
                if (ti == null) { 
                    // if we dont know ... so make it prepared
                    logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                               BrokerResources.W_TRAN_INFO_CORRUPTED, tid.toString()));
                    TransactionState ts = new TransactionState(
                                AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, 
                                        "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_NOFLAG) {
                    logger.logStack(Logger.WARNING, Globals.getBrokerResources().getKString(
                           BrokerResources.W_TXN_TYPE_CORRUPTED, tid+"["+ti.toString()+"]",
                           TransactionInfo.toString(TransactionInfo.TXN_LOCAL)),
                                                    processing.getValueCause());
                    TransactionState ts = new TransactionState(
                                AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, 
                                        "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getTransactionState() == null) {
                    logger.log(Logger.WARNING, BrokerResources.W_TRANS_STATE_CORRUPT, 
                               tid, processing.getValueCause());
                    TransactionState ts = new TransactionState(
                                AutoRollbackType.NOT_PREPARED, 0, true);
                    ts.setState(TransactionState.PREPARED);
                    try {
                        pstore.storeTransaction(tid, ts, false);
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, 
                                        "Error updating transaction " + tid, ex);
                    }
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_CLUSTER && ti.getTransactionBrokers() == null) {
                    logger.log(Logger.WARNING,  BrokerResources.W_CLUSTER_TXN_BROKER_INFO_CORRUPTED, 
                                                tid, processing.getValueCause());
                    pstore.storeClusterTransaction(tid, ti.getTransactionState(), null, false);
                    processing = processing.getNextException();
                    continue;
                }
                if (ti.getType() == TransactionInfo.TXN_REMOTE && 
                    ti.getTransactionHomeBroker() == null) {
                    logger.log(Logger.WARNING, BrokerResources.W_REMOTE_TXN_HOME_BROKER_INFO_CORRUPTED,
                                               tid, processing.getValueCause());
                    pstore.storeRemoteTransaction(tid, ti.getTransactionState(), null, null, false);
                    processing = processing.getNextException();
                    continue;
                }
                logger.log(Logger.ERROR, "XXXI18N Internal Error: unknown load error for TUID="
                           +tid+"["+ti.toString()+"]");

            } // end while
        } 

        // now look at acks load exception
        load_ex = pstore.getLoadTransactionAckException();

        if (load_ex != null) {
            // some messages could not be loaded
            LoadException processing = load_ex;
            while (processing != null) {
                TransactionUID tid = (TransactionUID)processing.getKey();
                TransactionAcknowledgement ta[] = 
                         (TransactionAcknowledgement[])processing.getValue();
                if (tid == null && ta == null) {
                    logger.log(Logger.WARNING,  "LoadTransactionAckException: "+
                               "both key and value for a Transaction Ack entry"
                               + " are corrupted");
                    processing = processing.getNextException();
                    continue;
                }
                if (tid == null ) { 
                    // at this point, there is nothing we can do ...
                    // store with valid key
                    // improve when we address 5060661
                    logger.log(Logger.WARNING,
                        BrokerResources.W_TRANS_ID_CORRUPT,
                            Arrays.toString(ta));
                    processing = processing.getNextException();
                    continue;
                }
                //ta == null, nothing we can do, remove it
                logger.log(Logger.WARNING,
                           BrokerResources.W_TRANS_ACK_CORRUPT,
                           tid.toString());
                try {
                    pstore.removeTransactionAck(tid, false);
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING, 
                                    "Error updating transaction acks " + tid, ex);
                } 
            } // end while
        } 

        logger.log(Logger.INFO, br.getKString(br.I_PROCESSING_TRANS)+logsuffix);
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

        int totalN = 0, clusterN = 0, remoteN = 0;
        int prepareN = 0, commitN = 0, rollbackN = 0;
        int prepareCN = 0, commitCN = 0, commitWaitCN = 0;
        Iterator itr = trans.entrySet().iterator();
        while (itr.hasNext()) {
          totalN++;
          try {
             Map.Entry entry = (Map.Entry)itr.next();
             TransactionUID tid = (TransactionUID)entry.getKey();
             TransactionInfo tif = (TransactionInfo)entry.getValue();
             TransactionState ts = tif.getTransactionState();
             TransactionAcknowledgement  ta[] = pstore.getTransactionAcks(tid);
             if (ta == null) {
                 ta = new TransactionAcknowledgement[0];
             }
             int state = ts.getState();
             if (DEBUG) {
                 logger.log(Logger.INFO, "Load transaction: TUID="+tid+
                 "["+TransactionState.toString(state)+
                 (ts.getCreationTime() == 0 ? "":" createTime="+ts.getCreationTime())+"]");
             }
             switch (state) {
                 // no longer valid, ignore
                 case TransactionState.CREATED:
                     clearTrans.add(tid);
                     break;
                 case TransactionState.PREPARED:
                     prepareN++;
                     // if autorollback, fall through to rollback
                     if (!AUTO_ROLLBACK) {
                         // nothing happens w/ preparedTransactions 
                         // they go back into the list
                         // We don't persist this because it is already
                         // in the store
                         addTransactionID(tid, ts, false);
                         if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                             logClusterTransaction(tid, ts, 
                                 tif.getTransactionBrokers(), true, false);
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
                         logClusterTransaction(tid, ts, 
                             tif.getTransactionBrokers(), true, false);
                     }
                     openTransactions.put(tid, Boolean.FALSE);
                     clearTrans.add(tid);
                     ts.setState(TransactionState.ROLLEDBACK);
                     rollbackN++;
                     if (state == TransactionState.PREPARED) {
                         clearAckOnlyTrans.add(tid);
                         try {
                         updateState(tid, TransactionState.ROLLEDBACK, true);  
                         } catch (Exception e) {
                         logger.logStack(Logger.WARNING, 
                         "Unable to update auto-rollback PREPARED transaction "+tid+" state to ROLLEDBACK", e); 
                         }
                     }
                     break; 
                 case TransactionState.COMMITTED:
                     commitN++;
                     committingTransactions.put(tid, "");
                     if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                         commitCN++;
                         boolean completed = true;
                         TransactionBroker[] brokers = tif.getTransactionBrokers();
                         logClusterTransaction(tid, ts, brokers, false, false);
                         for (int i = 0; brokers != null && i < brokers.length; i++) {
                             completed = brokers[i].isCompleted();
                             if (!completed) { 
                                 if (DEBUG_CLUSTER_TXN) {
                                 logger.log(logger.INFO, "COMMITTED cluster transaction "+tid+", incomplete broker:"+ brokers[i]);
                                 }
                                 break;
                             }
                         }
                         if (!completed) commitWaitCN++;
                     } else {
                         addTransactionID(tid, ts, false);
                     }
                     clearTrans.add(tid);
                     break; 
                 default: logger.log(logger.ERROR, 
                     "Internal Error unexpected transaction state:"+
                      TransactionState.toString(state)+ " TUID="+tid+", set to PREPARE"); 
                     addTransactionID(tid, ts, false);
                     if (tif.getType() == TransactionInfo.TXN_CLUSTER) {
                         logClusterTransaction(tid, ts, 
                             tif.getTransactionBrokers(), true, false);
                     }
                     updateState(tid, TransactionState.PREPARED, true);  
                     openTransactions.put(tid, Boolean.TRUE);
            }

            for (int i=0; i < ta.length; i ++) {
                if (DEBUG) {
                    logger.log(Logger.INFO, "Load transaction ack "+ta[i]+" [TUID="+tid+"]");
                }
                ConsumerUID cuid = ta[i].getConsumerUID();
                ConsumerUID scuid = ta[i].getStoredConsumerUID();
                SysMessageID sysid = ta[i].getSysMessageID();
                Map imap = (Map)inprocessAcks.get(sysid);
                if (scuid == null) {
                     logger.log(Logger.WARNING,"Internal Error: " +
                         " Unable to locate stored ConsumerUID :" + Arrays.toString(ta));
                     scuid = cuid;
                }
                    
                if (imap == null) {
                    imap = new HashMap();
                    inprocessAcks.put(sysid, imap);
                }
                imap.put(scuid, tid);
                if (openTransactions.get(tid) != null) { 
                    TransactionInformation ti = (TransactionInformation)translist.get(tid);
                    if (ti == null) {
                        logger.log(Logger.INFO, "Unable to retrieve "
                             + " transaction information " + ti +
                             " for " + tid + 
                            " we may be clearing the transaction");
                        continue;
                    }
                    if (openTransactions.get(tid) == Boolean.TRUE) {
                        ti.addConsumedMessage(sysid, cuid, scuid);
                    }
                    ti.addOrphanAck(sysid, scuid);
                 }
            } 
          } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
               BrokerResources.E_INTERNAL_BROKER_ERROR,
               "Error parsing transaction ", ex);
          }
        }

        {
            Object[] args = { Integer.valueOf(prepareCN), Integer.valueOf(commitWaitCN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                BrokerResources.I_NCLUSTER_TRANS, args));
        }

        HashMap remoteTrans = pstore.getAllRemoteTransactionStates();

        // list of transactions which need to be cleaned up
        HashSet clearRemoteTrans = new HashSet(remoteTrans.size());

        int prepareRN = 0, rollbackRN = 0, commitRN = 0, completeRN = 0;
        itr = remoteTrans.entrySet().iterator();
        while (itr.hasNext()) {
          try {
             Map.Entry entry = (Map.Entry)itr.next();
             TransactionUID tid = (TransactionUID)entry.getKey();
             TransactionState ts = (TransactionState)entry.getValue();
             TransactionAcknowledgement  ta[] = pstore.getTransactionAcks(tid);
             int state = ts.getState();
             if (DEBUG) {
                 logger.log(Logger.INFO, "Load remote transaction: TUID="+tid+
                 "["+TransactionState.toString(state)+
                 (ts.getCreationTime() == 0 ? "":" createTime="+ts.getCreationTime())+"]");
             }
             switch (state) {
                 case TransactionState.CREATED:
                     clearRemoteTrans.add(tid);
                     rollbackRN++;
                     break;
                 case TransactionState.PREPARED:
                     prepareRN++;
                     logRemoteTransaction(tid, ts, ta, 
                         pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                     openTransactions.put(tid, Boolean.TRUE);
                     break; 
                 case TransactionState.COMPLETE:
                     if (Globals.getHAEnabled() && ta != null && ta.length > 0) {
                         completeRN++;
                         ts.setState(TransactionState.PREPARED);
                         logRemoteTransaction(tid, ts, ta, 
                           pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                         openTransactions.put(tid, Boolean.TRUE);
                         break; 
                     }
                 case TransactionState.STARTED:
                 case TransactionState.ROLLEDBACK:
                 case TransactionState.FAILED:
                 case TransactionState.INCOMPLETE:
                     openTransactions.put(tid, Boolean.FALSE);
                     clearRemoteTrans.add(tid);
                     rollbackRN++;
                     break; 
                 case TransactionState.COMMITTED:
                     commitRN++;
                     logRemoteTransaction(tid, ts, ta, 
                         pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                     committingTransactions.put(tid, "");
                     break; 
                 default: 
                     logger.log(logger.ERROR, 
                         "Internal Error unexpected transaction state:"+
                          TransactionState.toString(state)+ " TUID="+tid+", set to PREPARED"); 
                     logRemoteTransaction(tid, ts, ta, 
                         pstore.getRemoteTransactionHomeBroker(tid), true, true, false);
                     updateRemoteTransactionState(tid, TransactionState.PREPARED, true, true, true);  
                     openTransactions.put(tid, Boolean.TRUE);
            }

            for (int i=0; i < ta.length; i ++) {
                if (DEBUG) {
                    logger.log(Logger.INFO, "Load remote transaction ack "+ta[i]+" [TUID="+tid+"]");
                }
                ConsumerUID cuid = ta[i].getConsumerUID();
                ConsumerUID scuid = ta[i].getStoredConsumerUID();
                SysMessageID sysid = ta[i].getSysMessageID();
                Map imap = (Map)inprocessAcks.get(sysid);
                if (scuid == null) {
                     logger.log(Logger.WARNING,"Internal Error: " +
                         " Unable to locate stored ConsumerUID :" + Arrays.toString(ta));
                     scuid =cuid;
                }
                    
                if (imap == null) {
                    imap = new HashMap();
                    inprocessAcks.put(sysid, imap);
                }
                imap.put(scuid, tid);
            }
          } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
               BrokerResources.E_INTERNAL_BROKER_ERROR,
               "Error parsing remote transaction ", ex);
          }
        } 

        if (Globals.getHAEnabled()) {
            Object[] args = { String.valueOf(remoteTrans.size()), String.valueOf(prepareRN),
                              String.valueOf(completeRN), String.valueOf(commitRN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getString(
                                            BrokerResources.I_NREMOTE_TRANS_HA, args));
        } else {
            Object[] args = { String.valueOf(remoteTrans.size()), String.valueOf(prepareRN),
                              String.valueOf(commitRN) };
            logger.log(logger.INFO, Globals.getBrokerResources().getString(
                                            BrokerResources.I_NREMOTE_TRANS, args));
        }

        // if we have ANY inprocess Acks or openTransactions we have to
        // load the database now and fix it
        if (openTransactions.size() > 0 || inprocessAcks.size() > 0) {
            LinkedHashMap m = DL.processTransactions(inprocessAcks,
                                          openTransactions, committingTransactions);
            if (m != null && !m.isEmpty()) {
                Iterator meitr = m.entrySet().iterator();
                while (meitr.hasNext()) {
                    Map.Entry me = (Map.Entry)meitr.next();
                    TransactionInformation ti = (TransactionInformation)
                              translist.get(me.getValue());
                    ti.addPublishedMessage((SysMessageID)me.getKey());
                }
            }
        }

        // OK -> now clean up the cleared transactions
        // this removes them from the disk
        TransactionState ts = null; 
        TransactionInformation ti = null;
        itr = clearTrans.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID)itr.next();
            logger.log(Logger.DEBUG,"Clearing transaction " + tid);
            if (!clearAckOnlyTrans.contains(tid)) {
                removeTransactionAck(tid);
                removeTransactionID(tid);
            } else {
                removeTransactionAck(tid, true);
            }
        }
        itr = clearRemoteTrans.iterator();
        while (itr.hasNext()) {
            TransactionUID tid = (TransactionUID)itr.next();
            try {
                logger.log(Logger.DEBUG, "Clearing remote transaction " + tid);
                removeRemoteTransactionAck(tid);
                removeRemoteTransactionID(tid, true);
            } catch (Exception e) {
                logger.log(logger.WARNING, 
                "Failed to remove remote transaction TUID="+tid+": "+e.getMessage());
            }
        }
    }

    public static void logTransactionInfo(HashMap transactions, 
                  boolean autorollback, String logsuffix) {

        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();

        /*
         * Loop through all transactions and count how many are in
         * what state. This is done strictly for informational purposes.
         */
        int nRolledBack = 0;    // Number of transactions rolledback
        int nPrepared = 0;      // Number of prepared transactions
        int nCommitted = 0;      // Number of committed transactions
        if (transactions != null && transactions.size() > 0) {
            Iterator itr = transactions.values().iterator();
            while (itr.hasNext()) {
                TransactionState _ts = ((TransactionInfo)itr.next()).getTransactionState();
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

            logger.log(Logger.INFO, br.getKString(br.I_NTRANS, 
                       Integer.valueOf(transactions.size()), 
                       Integer.valueOf(nRolledBack))+logsuffix);
            Object[] args = { Integer.valueOf(transactions.size()),
                              Integer.valueOf(nPrepared),  Integer.valueOf(nCommitted) };
            logger.log(Logger.INFO, br.getKString(br.I_NPREPARED_TRANS, args)+logsuffix);
            if (nPrepared > 0) {
                if (autorollback) {
                    logger.log(Logger.INFO, br.getKString(br.I_PREPARED_ROLLBACK)+logsuffix);
                } else {
                    logger.log(Logger.INFO, br.getKString(br.I_PREPARED_NOROLLBACK)+logsuffix);
                }
            }
        }
    }
    
    public void logClusterTransaction(TransactionUID id, TransactionState ts, 
            TransactionBroker[] brokers, boolean exist, boolean persist) 
            throws BrokerException {
    	
        logClusterTransaction(id, ts, brokers, exist, persist, null);
    } 

    public void logClusterTransaction(TransactionUID id, TransactionState ts, 
                          TransactionBroker[] brokers, boolean exist, boolean persist,
                          ClusterTransaction clusterTxn) 
                          throws BrokerException {
        boolean added = false;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            if (exist) {
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "logClusterTransaction(): Unknown transaction: " + id));
            }

            ti = addTransactionID(id, ts, false);
            added = true;
        }
        if (ti != null && ti.isTakeoverLocked()) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_TXN_LOCKED, id), Status.NOT_FOUND);
        }
        if (ti != null)
            ti.setClusterTransactionBrokers(brokers);

        if (persist) {
            try {
                if (added) {
                    pstore.storeClusterTransaction(id, ts, brokers,
                           Destination.PERSIST_SYNC);
                } else {
                    if (Globals.isNewTxnLogEnabled()) {
                        ((TxnLoggingStore)pstore).logTxn(clusterTxn);
                    } else {
                        pstore.updateClusterTransaction(id, brokers,
                               Destination.PERSIST_SYNC);
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

    //called by txn home broker
    public void completeClusterTransactionBrokerState(TransactionUID id, int expectedTranState, 
                                                      BrokerAddress broker, boolean persist)
                                                      throws BrokerException { 
        boolean changed = false;
        TransactionBroker b = null;
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(
                br.getKString(br.X_TXN_NOT_FOUND, id), Status.NOT_FOUND);
        }

        // lock TransactionInformation object
        synchronized (ti) {
            if (ti.getState().getState() != expectedTranState) {
                throw new BrokerException(Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "updateClusterTransactionBrokerState(): Unexpected cluster transaction state: " +
                     ti.getState())+", expect:"+expectedTranState);
            }
            b = ti.getClusterTransactionBroker(broker);
            if (b == null) {
                throw new BrokerException("Can't update transaction broker state "+
                                  broker.toString()+": not found", Status.NOT_FOUND);
            }
            changed = b.copyState(new TransactionBroker(broker, true));
        }

        if (persist && changed) {
            pstore.updateClusterTransactionBrokerState(id, expectedTranState, b, Destination.PERSIST_SYNC);
        }
        txnReaper.clusterTransactionCompleted(id);
    }

    public boolean updateRemoteTransactionState(TransactionUID id, int state, 
                   boolean recovery, boolean sync, boolean persist) 
                throws BrokerException { 
        boolean updated = false;
        TransactionState ts = null;
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            if (state == TransactionState.COMMITTED) {
                if (txnReaper.hasRemoteTransaction(id)) {
                    if (DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, "Remote transaction "+id+" has already been committed");
                    }
                    return false;
                }
            }
            throw new BrokerException("Update remote transaction state to "+
                  TransactionState.toString(state)+": transaction "+id+
                  " not found, the transaction may have already been committed.",
                                       Status.NOT_FOUND);
        }

        // lock TransactionInformation object
        synchronized (ti) {
            ts = ti.getState();
            if (TransactionState.remoteTransactionNextState(ts, state) != state) {
                throw new BrokerException(
                "Update remote transaction "+id + " to state "+
                TransactionState.toString(state) + " not allowed", Status.NOT_ALLOWED);
            }

            if (ts.getState() != state) {
                ti.getState().setState(state);
                updated = true;
            }
        }

        if (persist && !Globals.getHAEnabled()) {
            try {
                pstore.updateTransactionState(id, ts, (sync ? Destination.PERSIST_SYNC:false));
                if(Globals.isNewTxnLogEnabled()){
                    
                    ((TxnLoggingStore)pstore).logTxnCompletion(id, state, 
                                      BaseTransaction.REMOTE_TRANSACTION_TYPE);
                 }
            } catch (IOException e) {
            String[] args = {id.toString(), TransactionState.toString(state), e.getMessage()}; 
            throw new BrokerException(Globals.getBrokerResources().getKString(
                  BrokerResources.X_REMOTE_TXN_STATE_UPDATE_FAIL, args), e);
            }
        }

        if (updated && state == TransactionState.COMMITTED) {
            txnReaper.addRemoteTransaction(id, recovery);
        }
        return updated;
    }

    public RemoteTransactionAckEntry[] getRecoveryRemoteTransactionAcks(TransactionUID id) 
        throws BrokerException {
        RemoteTransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getRecoveryTransactionAcks();
    }

    public void logLocalRemoteTransaction(TransactionUID id, TransactionState ts, 
                          TransactionAcknowledgement[] txnAcks,
                          BrokerAddress txnHomeBroker, boolean recovery,
                          boolean newtxn, boolean persist) 
                          throws BrokerException {
        logRemoteTransaction(id, ts, txnAcks, txnHomeBroker, 
                             recovery, newtxn, true, persist);
    }

    //called by txn remote broker
    public void logRemoteTransaction(TransactionUID id, TransactionState ts, 
                          TransactionAcknowledgement[] txnAcks,
                          BrokerAddress txnHomeBroker, boolean recovery,
                          boolean newtxn, boolean persist) 
                          throws BrokerException {

        logRemoteTransaction(id, ts, txnAcks, txnHomeBroker, 
                             recovery, newtxn, false, persist);
   }

    private void logRemoteTransaction(TransactionUID id, TransactionState ts, 
                          TransactionAcknowledgement[] txnAcks,
                          BrokerAddress txnHomeBroker, boolean recovery,
                          boolean newtxn, boolean localremote, boolean persist) 
                          throws BrokerException {

        RemoteTransactionInformation rti = null; 
        boolean added = false;

        exclusiveLock.lock();
        try {
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);

            if (newtxn) {
                if (rti != null ||
                    inuse_translist.contains(id) || translist.containsKey(id)) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                        BrokerResources.X_TRANSACTIONID_INUSE,
                        (Throwable) null, Status.CONFLICT);
                }
            }
            if (rti == null) {
                rti = new RemoteTransactionInformation(
                    id, ts, txnAcks, txnHomeBroker, recovery, localremote, persist);

                inuse_translist.add(id);
                remoteTranslist.put(id, rti);
                added = true;
            }
        } finally {
            exclusiveLock.unlock();
        }

        if (!added) {
            if (!Globals.getHAEnabled()) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_TRANSACTIONID_INUSE, id.toString()),
                    BrokerResources.X_TRANSACTIONID_INUSE,
                    (Throwable) null, Status.CONFLICT);
            }

            // lock TransactionInformation object
            synchronized (rti) {
                if (!rti.getTransactionHomeBroker().equals(new TransactionBroker(txnHomeBroker))) {
                    throw new BrokerException("Transaction home broker mismatch:"+
                    txnHomeBroker.toString()+ " but existed "+rti.getTransactionHomeBroker());
                }
                if (!recovery) {
                    throw new BrokerException(
                    "XXXI18N-Internal Error: unexpected non-recovery, TUID="+id);
                }
                if (rti.getState().getState() != ts.getState()) {
                    throw new BrokerException("XXXI18N-Internal Error: state mismatch:"+
                    TransactionState.toString(ts.getState())+", but exist"+
                    TransactionState.toString(rti.getState().getState())+"TUID="+id);
                }
                rti.addRecoveryTransactionAcks(txnAcks);
            }
        }

        if (persist && added) {
            try {
                if (!Globals.getHAEnabled() || Globals.getSFSHAEnabled()) {
                     if(!Globals.isNewTxnLogEnabled()){
                        pstore.storeRemoteTransaction(id, ts, txnAcks, txnHomeBroker,
                                               Destination.PERSIST_SYNC);
                  	}
                  	else {
                  		// store the dest ids as well so we can process txns more efficiently on restart
                  		//(no need to load all destinations)
                  		DestinationUID[] destIds = new DestinationUID[txnAcks.length];
                  		for(int i=0;i<txnAcks.length;i++)
                  		{ 
                  			SysMessageID sid = txnAcks[i].getSysMessageID();
                  			PacketReference p = DL.get(pstore, sid);
                  			DestinationUID destID = null;
                  			if(p!=null)
                  			{
                  				destID = p.getDestinationUID();
                  			}
                  			else
                  			{
                  				logger.log(Logger.WARNING, "Could not find packet for "+sid);
                  			}
                  			destIds[i] = destID;
                  		}
  						RemoteTransaction remoteTransaction = new RemoteTransaction(
  								id, ts, txnAcks, destIds,txnHomeBroker);
  						((TxnLoggingStore)pstore).logTxn(remoteTransaction);
  					}
                    
                } else {
                    pstore.updateRemoteTransaction(id, txnAcks, txnHomeBroker,
                                             Destination.PERSIST_SYNC);
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
                logger.logStack(Logger.ERROR, ex.getMessage()+
                       (ex.getCause()==null? "":": "+ex.getCause().getMessage()), ex);
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_TRANSACTION_STORE_ERROR, id.toString()),
                    BrokerResources.X_TRANSACTION_STORE_ERROR, (Throwable) ex, Status.ERROR);
	    }
        }
    }

    public RemoteTransactionAckEntry getRemoteTransactionAcks(TransactionUID id) 
           throws BrokerException
    {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) return null;
        return rti.getTransactionAcks();
    }

    public void removeRemoteTransactionID(TransactionUID id, boolean persist)
                throws BrokerException {

        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
            BrokerResources.X_REMOTE_TXN_UNKOWN, id.toString()), Status.NOT_FOUND);
        }
        if (!rti.isProcessed()) return;

        try {
            if (persist && !Globals.getHAEnabled()) {
                pstore.removeTransaction(id, false, false);
            }
        } catch (Exception ex) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "Unable to remove cluster the transaction id " + id), ex);
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

    public TransactionBroker[] getClusterTransactionBrokers(TransactionUID id)
    throws BrokerException {

        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(
                br.getKString(br.X_TXN_NOT_FOUND, id), Status.NOT_FOUND);
        }
        return ti.getClusterTransactionBrokers();
    }

    public TransactionBroker getClusterTransactionBroker(TransactionUID id, BrokerAddress broker) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getClusterTransactionBroker(broker);
    }

    public boolean isClusterTransactionBroker(TransactionUID id, UID ssid) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return false;
        return ti.isClusterTransactionBroker(ssid);
    }

    public boolean hasRemoteBroker(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
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
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
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
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (rti == null) return null;
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
                Map.Entry entry = (Map.Entry)itr.next();
                tid = (TransactionUID)entry.getKey();
                rti = (RemoteTransactionInformation)entry.getValue();
                if (rti == null) continue;
                ts = rti.getState();
                if (ts != null && ts.getState() == TransactionState.PREPARED) {
                    if (timeout == null || 
                        rti.isPendingTimeout(timeout.longValue())) {
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
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
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

    public void removeAcknowledgement(TransactionUID tid,
        SysMessageID sysid, ConsumerUID id, boolean rerouted)
                                      throws BrokerException {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(tid);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
            BrokerResources.X_INTERNAL_EXCEPTION,
            "Removing acknowledgement with Unknown Transaction ID "+ tid));
        }
        ti.removeConsumedMessage(sysid, id, rerouted);
    }

    public HashMap retrieveRemovedConsumedMessages(TransactionUID id, boolean rerouted)
    {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return null;
        return ti.getRemovedConsumedMessages(rerouted);
    }

    public void reapTakeoverCommittedTransaction(TransactionUID id) {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
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

    public boolean isLocalTransaction(TransactionUID id)  {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return false;
        return (ti.getType() == TransactionInfo.TXN_LOCAL);
    }

    public String getTransactionAsString(TransactionUID id)
    {
    	shareLock.lock();
        try {
        	 TransactionInformation  ti = (TransactionInformation)translist.get(id);        	
             return (ti==null?"null":ti.toString());
        } finally {
            shareLock.unlock();
        }
    	
    }
    public boolean isClusterTransaction(TransactionUID id)  {
        TransactionInformation ti = null;

        shareLock.lock();
        try {
            ti = (TransactionInformation)translist.get(id);
        } finally {
            shareLock.unlock();
        }

        if (ti == null) return false;
        return (ti.getType() == TransactionInfo.TXN_CLUSTER);
    }

    public boolean isRemoteTransaction(TransactionUID id)  {
        RemoteTransactionInformation rti = null;

        shareLock.lock();
        try {
            rti = (RemoteTransactionInformation)remoteTranslist.get(id);
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

    public void clusterPropertyChanged(String name, String value) {
    }

    public void brokerAdded(ClusteredBroker broker, UID brokerSession) {
    }

    public void brokerRemoved(ClusteredBroker broker, UID brokerSession) {
    }

    public void masterBrokerChanged(ClusteredBroker oldMaster,
                    ClusteredBroker newMaster) {
    }

   /**
    * Called when the status of a broker has changed. The
    * status may not be accurate if a previous listener updated
    * the status for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldStatus the previous status.
    * @param newStatus the new status.
    * @param brokerSession uid associated with the changed broker
    * @param userData data associated with the state change
    */
    public void brokerStatusChanged(String brokerid,
                  int oldStatus, int newStatus, UID uid, Object userData) {
        ClusterManager clsmgr = Globals.getClusterManager();
        ClusteredBroker cb = clsmgr.getBroker(brokerid);
        if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "TransactionList:brokerStatusChanged:broker="+cb+", oldStatus="+
                       BrokerStatus.toString(oldStatus)+", newStatus="+BrokerStatus.toString(newStatus)+
                       ", brokerSession="+uid+", userData="+userData);
        }
        if (BrokerStatus.getBrokerLinkIsUp(newStatus) &&
            !BrokerStatus.getBrokerLinkIsUp(oldStatus)) {
            newlyActivatedBrokers.add((BrokerMQAddress)cb.getBrokerURL());
            if (txnReaper != null) {
                txnReaper.wakeupReaperTimer();
            }
        }
    }

    public void brokerStateChanged(String brokerid,
                  BrokerState oldState, BrokerState newState) {
    }

    public void brokerVersionChanged(String brokerid,
                  int oldVersion, int newVersion) {
    }

    public void brokerURLChanged(String brokerid,
                  MQAddress oldAddress, MQAddress newAddress) {
    }

    /****************************************************
     * implement PartitionListener
     *****************************************************/

    /**
     * @param partitionID the partition id
     */
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
    public void partitionRemoved(UID partitionID, Object source, Object destinedTo) {
    }
}

// OK .. we know that the only time we will ever be retrieving
// messages with these apis is after a commit or rollback
//
// that message will always occurs after all updates have happened
//
// there are never 2 connections sharing a single transaction, and
// access from a connection is on a single thread
//
// SO .. dont have to lock

class TransactionInformation
{
    int type = TransactionInfo.TXN_NOFLAG;

    ArrayList published;
    LinkedHashMap consumed;
    LinkedHashMap removedConsumedRBD; //remote broker down
    LinkedHashMap removedConsumedRRT; //remote rerouted
    TransactionState state;
    HashMap cuidToStored;
    HashMap sysidToAddr;
    LinkedHashMap orphanedMessages;
    TransactionUID tid = null;
    //boolean persisted = false;
    boolean inROLLBACK = false;

    TransactionBroker[] brokers = null;
    //boolean cluster = false;
    boolean processed = false;

    private ReentrantLock takeoverLock = new ReentrantLock();

    public TransactionInformation(TransactionUID tid, 
                                  TransactionState state,
                                  boolean persist) {
        published = new ArrayList();
        consumed = new LinkedHashMap();
        removedConsumedRBD = new LinkedHashMap();
        removedConsumedRRT = new LinkedHashMap();
        cuidToStored = new HashMap();
        sysidToAddr = new HashMap();
        orphanedMessages = new LinkedHashMap();

        this.state = state;
        this.tid = tid;
        //this.persisted = persist;
        type = TransactionInfo.TXN_LOCAL;
    }

    /**
     * only called by the creator before put into MT access
     */
    public void getTakeoverLock() {
        takeoverLock.lock();
    }

    public void releaseTakeoverLock() {
        takeoverLock.unlock();
    }

    /**
     * Return true if it is locked by another thread and false otherwise
     */
    public boolean isTakeoverLocked() {
        boolean isTakeoverLocked = takeoverLock.isLocked();
        if (isTakeoverLocked && takeoverLock.isHeldByCurrentThread()) {
            // takeoverLock is held by current thread
            return false;
        }

        return isTakeoverLocked;
    }

    public synchronized int getType() {
        return type;
    }

    public synchronized boolean processed() {
        if (processed) return true;
        processed = true;
        return false;
    }

    public synchronized boolean isProcessed() {
        return processed;
    }

    public synchronized String toString() {
        if (type == TransactionInfo.TXN_CLUSTER) {
            StringBuffer buf = new StringBuffer();
            buf.append("TransactionInfo["+tid+"]cluster - ");
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) buf.append(", ");
                buf.append(brokers[i].toString());
            }
            return buf.toString();
        }
        return "TransactionInfo["+tid+"]local";
    }
    public synchronized void addOrphanAck(SysMessageID sysid, ConsumerUID sid) {
        addOrphanAck(sysid, sid, null);
    }

    public synchronized void addOrphanAck(SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        Map m = (Map)orphanedMessages.get(sysid);
        if (m == null) {
            m = new LinkedHashMap();
            orphanedMessages.put(sysid, m);
        }
        List l = (List)m.get(sid);
        if (l == null) { 
            l = new ArrayList();
            m.put(sid, l);
        }
        if (cid != null) l.add(cid);
    }

    public synchronized void removeOrphanAck(SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        Map m = (Map)orphanedMessages.get(sysid);
        if (m == null) return;
        if (cid == null) {
            m.remove(sid);
            if (m.size() == 0) orphanedMessages.remove(sysid);
            return;
        }
        List l = (List)m.get(sid);
        if (l == null) return;
        l.remove(cid);
    }

    public synchronized Map getOrphanAck() {
        return orphanedMessages;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        synchronized (this) {
            ht.put("state", state.getDebugState());
            ht.put("inROLLBACK", Boolean.valueOf(inROLLBACK));
            ht.put("processed", String.valueOf(processed));
            ht.put("consumed#", Integer.valueOf(consumed.size()));
            ht.put("removedConsumedRBD#", Integer.valueOf(removedConsumedRBD.size()));
            ht.put("removedConsumedRRT#", Integer.valueOf(removedConsumedRRT.size()));
            ht.put("published#", Integer.valueOf(published.size()));
            ht.put("cuidToStored#", Integer.valueOf(cuidToStored.size()));
            if (cuidToStored.size() > 0) {
               Hashtable cid = new Hashtable();
               Iterator itr = ht.entrySet().iterator();
               while (itr.hasNext()) {
                   Map.Entry me = (Map.Entry)itr.next();
                   cid.put(me.getKey().toString(),
                       me.getValue().toString());
               }
               ht.put("cuidToStored", cid);
            }
            if (consumed.size() > 0) {
                Hashtable m = new Hashtable();
                Iterator itr = consumed.entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry me = (Map.Entry)itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList)me.getValue();
                    if (ids.size() == 0) continue;
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i=0; i < ids.size(); i ++)
                            v.add(ids.get(i).toString());
                        m.put(id, v);
                    }

                 }
                 if (m.size() > 0) {
                     ht.put("consumed", m);
                 }
            }
            if (removedConsumedRBD.size() > 0) {
                Hashtable m = new Hashtable();
                Iterator itr = removedConsumedRBD.entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry me = (Map.Entry)itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList)me.getValue();
                    if (ids.size() == 0) continue;
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i=0; i < ids.size(); i ++)
                            v.add(ids.get(i).toString());
                        m.put(id, v);
                    }
                }
                if (m.size() > 0) {
                    ht.put("removedConsumedRBD", m);
                }
            }

            if (removedConsumedRRT.size() > 0) {
                Hashtable m = new Hashtable();
                Iterator itr = removedConsumedRRT.entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry me = (Map.Entry)itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList)me.getValue();
                    if (ids.size() == 0) continue;
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i=0; i < ids.size(); i ++)
                            v.add(ids.get(i).toString());
                        m.put(id, v);
                    }
                }
                if (m.size() > 0) {
                    ht.put("removedConsumedRRT", m);
                }
            }

            if (published.size() > 0) {
                Vector v = new Vector();
                for (int i =0; i < published.size(); i ++) {
                    v.add(published.get(i).toString());
                }
                ht.put("published", v);
            }
            if (type == TransactionInfo.TXN_CLUSTER) {
                Vector v = new Vector();
                for (int i = 0;  i < brokers.length; i++) {
                    v.add(brokers[i].toString());
                }
                ht.put("brokers", v);
            }
        }

        return ht;
    }

    public synchronized List getPublishedMessages() {
        return published;
    }

    public synchronized int getNPublishedMessages() {
        if (published != null) {
            return published.size();
        } else {
            return 0;
        }
    }

    public synchronized LinkedHashMap getConsumedMessages(boolean inrollback) {
        inROLLBACK = inrollback;
        return consumed;
    }

    public synchronized HashMap getStoredConsumerUIDs() {
        return cuidToStored;
    }

    public synchronized int getNConsumedMessages() {
        if (consumed != null) {
            return consumed.size();
        } else {
            return 0;
        }
    }

    public synchronized TransactionState getState() {
        return state;
    }

    public synchronized void addPublishedMessage(SysMessageID id)
        throws BrokerException {

        // first check if we have exceeded our maximum message count per txn
        if (published.size() < TransactionList.defaultProducerMaxMsgCnt) {
            published.add(id);
        } else {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_TXN_PRODUCER_MAX_MESSAGE_COUNT_EXCEEDED,
                    TransactionList.defaultProducerMaxMsgCnt, tid),
                BrokerResources.X_TXN_PRODUCER_MAX_MESSAGE_COUNT_EXCEEDED,
                (Throwable) null,
                Status.RESOURCE_FULL);
        }
    }

    public synchronized boolean checkConsumedMessage(
        SysMessageID sysid, ConsumerUID id) {

        List l = (List)consumed.get(sysid);
        if (l == null) {
            return false;
        }
        return l.contains(id);
    }

    public synchronized boolean isConsumedMessage(
        SysMessageID sysid, ConsumerUID id) {
        if (state == null) return false;
        if (state.getState() == TransactionState.ROLLEDBACK) return false;
        if (inROLLBACK) return false;

        List l = (List)consumed.get(sysid);
        if (l == null) {
            return false;
        }
        return l.contains(id);
    }

    public synchronized void addConsumedMessage(SysMessageID sysid,
        ConsumerUID id, ConsumerUID sid) throws BrokerException {

        // first check if we have exceeded our maximum message count per txn
        if (consumed.size() < TransactionList.defaultConsumerMaxMsgCnt) {
            List l = (List)consumed.get(sysid);
            if (l == null) {
                l = new ArrayList();
                consumed.put(sysid, l);
            } else if (l.contains(id)) {
                throw new TransactionAckExistException(
                Globals.getBrokerResources().getKString(
                  Globals.getBrokerResources().X_ACK_EXISTS_IN_TRANSACTION,
                  "["+sysid+":"+id+","+sid+"]", tid), Status.CONFLICT);
            }
            l.add(id);
            cuidToStored.put(id, sid);
        } else {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_TXN_CONSUMER_MAX_MESSAGE_COUNT_EXCEEDED,
                    TransactionList.defaultConsumerMaxMsgCnt, tid),
                BrokerResources.X_TXN_CONSUMER_MAX_MESSAGE_COUNT_EXCEEDED,
                (Throwable) null,
                Status.RESOURCE_FULL);
        }
    }

    public synchronized void setAckBrokerAddress(SysMessageID sysid,
        ConsumerUID id, BrokerAddress addr) throws BrokerException {

        BrokerAddress ba = (BrokerAddress)sysidToAddr.get(sysid);
        if (ba != null && (!ba.equals(addr) ||
            !ba.getBrokerSessionUID().equals(addr.getBrokerSessionUID()))) {
            BrokerException bex = new BrokerException(
                "Message requeued:"+sysid, Status.GONE);
            bex.setRemoteConsumerUIDs(String.valueOf(id.longValue()));
            bex.setRemote(true);
            throw bex;
        }
        sysidToAddr.put(sysid, addr);
    }

    public synchronized BrokerAddress getAckBrokerAddress(SysMessageID sysid, ConsumerUID id) {
        return (BrokerAddress)sysidToAddr.get(sysid);
    }

    public synchronized HashMap getAckBrokerAddresses() {
        return sysidToAddr;
    }

    public TransactionUID getTID() {
        return tid;
    }

    public synchronized ConsumerUID removeConsumedMessage(SysMessageID sysid,
        ConsumerUID id, boolean rerouted) throws BrokerException {

        List l = (List)consumed.get(sysid);
        if (l == null) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                      BrokerResources.X_CONSUMED_MSG_NOT_FOUND_IN_TXN, 
                      "["+sysid+","+id+"]", tid.toString()));
        }
        l.remove(id);
        if (l.size() == 0) {
            consumed.remove(sysid);
        }
        if (!rerouted) {
            l = (List)removedConsumedRBD.get(sysid);
            if (l == null) {
                l = new ArrayList();
                removedConsumedRBD.put(sysid,  l);
            }
            l.add(id);
        } else {
            l = (List)removedConsumedRRT.get(sysid);
            if (l == null) {
                l = new ArrayList();
                removedConsumedRRT.put(sysid,  l);
            }
            l.add(id);
        }
        return (ConsumerUID)cuidToStored.get(id);
    }

    public synchronized LinkedHashMap getRemovedConsumedMessages(boolean rerouted) {
        if (!rerouted) {
            return removedConsumedRBD;
        }
        return removedConsumedRRT;
    }

    public synchronized void setClusterTransactionBrokers(
        TransactionBroker[] brokers) {

        this.brokers = brokers;
        type = TransactionInfo.TXN_CLUSTER;
    }

    public synchronized TransactionBroker[] getClusterTransactionBrokers() {
        return brokers;
    }

    public synchronized boolean isClusterTransactionBrokersCompleted() {

        boolean completed = true;
        for (int j = 0; j < brokers.length; j++) {
            if (!brokers[j].isCompleted()) {
                completed = false;
                break;
            }
        }
        return completed;
    }

    public synchronized TransactionBroker getClusterTransactionBroker(
        BrokerAddress b) {

        if (brokers == null) {
            return null;
        }
       
        TransactionBroker tba = new TransactionBroker(b);
        for (int i = 0; i < brokers.length; i++) {
            if (brokers[i].equals(tba)) { 
                return brokers[i];
            }
        }
        return null;
    }

    public synchronized boolean isClusterTransactionBroker(UID ssid) {

        if (brokers == null) return false;
        for (int i = 0; i < brokers.length; i++) {
            if (brokers[i].isSame(ssid)) return true;
        }
        return false;
    }
}

class RemoteTransactionInformation extends TransactionInformation
{
    RemoteTransactionAckEntry txnAckEntry = null;
    ArrayList recoveryTxnAckEntrys = new ArrayList();
    TransactionBroker txnhome = null;
    long pendingStartTime = 0L;

    public RemoteTransactionInformation(TransactionUID tid, TransactionState state, 
                                        TransactionAcknowledgement[] acks,
                                        BrokerAddress txnhome, boolean recovery,
                                        boolean localremote, boolean persist) {
        super(tid, state, persist);
        this.type = TransactionInfo.TXN_REMOTE;
        this.txnhome = new TransactionBroker(txnhome, true);
        if (recovery) {
            addRecoveryTransactionAcks(acks);
        } else {
            this.txnAckEntry = new RemoteTransactionAckEntry(acks, localremote);
        }
    }

    public void pendingStarted() {
        pendingStartTime = System.currentTimeMillis();
    }

    public boolean isPendingTimeout(long timeout) {
        return ((System.currentTimeMillis() - pendingStartTime) >= timeout);
    }

    public synchronized String toString() {
        return "RemoteTransactionInfo["+tid+"]remote - "+txnhome.toString();
    }

    public synchronized TransactionBroker getTransactionHomeBroker() {
        return txnhome;
    }

    public synchronized RemoteTransactionAckEntry getTransactionAcks() {
        return txnAckEntry;
    }

    public synchronized RemoteTransactionAckEntry[] getRecoveryTransactionAcks() {

        if (recoveryTxnAckEntrys.size() == 0) return null;
        return (RemoteTransactionAckEntry[])recoveryTxnAckEntrys.toArray(
                                         new RemoteTransactionAckEntry[recoveryTxnAckEntrys.size()]);
    }

    public synchronized void addRecoveryTransactionAcks(
                               TransactionAcknowledgement[] acks) {

        if (getState().getState() == TransactionState.PREPARED) {
            recoveryTxnAckEntrys.add(
                new RemoteTransactionAckEntry(acks, true, false));
        } else {
            recoveryTxnAckEntrys.add(
                new RemoteTransactionAckEntry(acks, true, true));
        }
    }

    public synchronized int getNRemoteConsumedMessages() {
        int n = 0;
        if (txnAckEntry != null) {
            n += txnAckEntry.getAcks().length;
        }

        RemoteTransactionAckEntry tae = null;
        Iterator itr = recoveryTxnAckEntrys.iterator();
        while (itr.hasNext()) {
            tae = (RemoteTransactionAckEntry)itr.next();
            n += tae.getAcks().length;
        }
        return n;
    }

    public boolean isProcessed() {

        if (txnAckEntry != null && !txnAckEntry.isProcessed()) return false;

        synchronized (this) {
            RemoteTransactionAckEntry tae = null;
            Iterator itr = recoveryTxnAckEntrys.iterator();
            while (itr.hasNext()) {
                tae = (RemoteTransactionAckEntry)itr.next();
                if (!tae.isProcessed()) return false;
            }
        }
        return true;
    }

    public synchronized boolean isRecovery() {
        return (recoveryTxnAckEntrys.size() > 0);
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        synchronized (this) {
            ht.put("state", getState().getDebugState());
            if (txnhome != null) {
                ht.put("txnhome", txnhome.toString());
            }
            if (txnAckEntry != null) {
                ht.put("txnAckEntry", txnAckEntry.getDebugState());
            }
            if (recoveryTxnAckEntrys != null) {
                ArrayList l = new ArrayList();
                for (int i = 0, len =  recoveryTxnAckEntrys.size();  i < len; i++) {
                    l.add(((RemoteTransactionAckEntry)
                           recoveryTxnAckEntrys.get(i)).getDebugState());
                }
                ht.put("recoveryTxnAckEntrys", l);
            }
        }

        return ht;
    }
}

class TransactionReaper implements TimerEventHandler 
{
    TransactionList translist = null;
	Logger logger = Globals.getLogger();

    List<TIDEntry> committed = Collections.synchronizedList(new ArrayList<TIDEntry>());
    List<TIDEntry> noremoves = Collections.synchronizedList(new ArrayList<TIDEntry>()); 
    List<TIDEntry> clusterPCommitted = Collections.synchronizedList(new ArrayList<TIDEntry>());
    List<TIDEntry> remoteCommitted = Collections.synchronizedList(new ArrayList<TIDEntry>()); 
    List<TIDEntry> remoteRCommitted = Collections.synchronizedList(new ArrayList<TIDEntry>()); 
    WakeupableTimer reapTimer = null;
    enum ClusterPCommittedState { UNPROCCESSED, PROCCESSED, TAKEOVER };

    static class TIDEntry {
        TransactionUID tid = null; 
        boolean inprocessing = false;
        ClusterPCommittedState pstate = ClusterPCommittedState.UNPROCCESSED; 
        boolean swipemark = false;
        boolean swipeonly = false;

        public TIDEntry(TransactionUID id) {
            tid = id;
        }

        public TIDEntry(TransactionUID id, ClusterPCommittedState s) {
            tid = id;
            pstate = s;
        }

        public int hashCode() {
            return tid.hashCode();
        }

        public boolean equals(Object obj) {
            if (obj instanceof TIDEntry) {
                return tid.equals(((TIDEntry)obj).tid);
            }
            return false;
        }
    }

    public  TransactionReaper(TransactionList tl) { 
        this.translist = tl;
    }

    private boolean needReapOne(List l) {
        if (TransactionList.TXN_REAPLIMIT == 0) {
            return true;
        }
        int size = l.size();
        if (size > TransactionList.TXN_REAPLIMIT*
                (1.0+((float)TransactionList.TXN_REAPLIMIT_OVERTHRESHOLD)/100)) {
            return true;
        }
        return false;
    }

    public void addLocalTransaction(TransactionUID tid, boolean noremove) {
        TIDEntry e = new TIDEntry(tid);
        if (noremove) {
            noremoves.add(e);
        }
        committed.add(e);
        createTimer();
        if (committed.size() > TransactionList.TXN_REAPLIMIT) {
            reapTimer.wakeup();
        }
        if (needReapOne(committed)) { 
            run(true);
        }
    }

    public void addClusterTransaction(TransactionUID tid, boolean noremove) {
        addClusterTransaction(tid, noremove, false);
    }

    public void addClusterTransaction(TransactionUID tid, boolean noremove, boolean takeover) {
        TIDEntry e = null;
        if (!takeover) {
            e = new TIDEntry(tid, ClusterPCommittedState.UNPROCCESSED);
        } else {
            e = new TIDEntry(tid, ClusterPCommittedState.TAKEOVER);
        }
        clusterPCommitted.add(e);
        if (noremove) {
            noremoves.add(e);
        }
        createTimer();
        reapTimer.wakeup();
    }

    public void clusterTransactionCompleted(TransactionUID tid) {
        createTimer();
        reapTimer.wakeup();

        if (!needReapOne(clusterPCommitted) && !needReapOne(committed)) {
            return; 
        }

        TransactionBroker[] brokers = null;
        try {
            brokers = translist.getClusterTransactionBrokers(tid);
        } catch (Exception e) {
            boolean logstack = true;
            if (e instanceof BrokerException &&
                ((BrokerException)e).getStatusCode() == Status.NOT_FOUND) {
                logstack = false;
            }
            String emsg = translist.br.getKString(
                translist.br.W_UNABLE_GET_TXN_BROKERS_ON_TXN_COMPLETE,
                tid, e.getMessage());
            if (logstack) {
                logger.logStack(logger.WARNING, emsg, e);
            } else {
                if (translist.DEBUG) {
                    logger.log(logger.INFO, emsg+" - all completed");
                }
            }
        }
        boolean completed = true;
        if (brokers == null) { 
            completed = false;
        } else {
            for (int i = 0; i < brokers.length; i++) {
                if (!brokers[i].isCompleted()) {
                    completed = false;
                }
            }
        }
        if (completed) {
            TIDEntry entry = null;
            TIDEntry e =  new TIDEntry(tid);
            synchronized(clusterPCommitted) {
                int ind = clusterPCommitted.indexOf(e);
                if (ind >= 0) {
                    entry = clusterPCommitted.get(ind);
                }
                if (entry != null && entry.inprocessing) {
                    entry = null;
                }  
                if (entry != null) {
                    entry.inprocessing = true;
                }
            }
            if (entry != null) {
                if (entry.pstate == ClusterPCommittedState.UNPROCCESSED) {
                    Globals.getConnectionManager().removeFromClientDataList(
                        IMQConnection.TRANSACTION_LIST, entry.tid);
                }
                entry.pstate = ClusterPCommittedState.PROCCESSED;
                clusterPCommitted.remove(entry);
                entry.inprocessing = false;
                committed.add(entry);
            }
        }
        if (needReapOne(committed)) {
            run(true);
        }
    }

    public void addRemoteTransaction(TransactionUID tid, boolean recovery) {
        TIDEntry e = new TIDEntry(tid);
        if (!recovery) {
            remoteCommitted.add(e);
        } else {
            remoteRCommitted.add(e);
        }
        createTimer();
        if (remoteCommitted.size() > TransactionList.TXN_REAPLIMIT ||
            remoteRCommitted.size() > TransactionList.TXN_REAPLIMIT) {
            reapTimer.wakeup();
        }
        if (needReapOne(remoteCommitted) || needReapOne(remoteRCommitted)) {
            run(true);
        }
    }

    public boolean hasRemoteTransaction(TransactionUID tid) {
        TIDEntry e = new TIDEntry(tid);
        if (remoteCommitted.contains(e)) {
            return true;
        }
        return remoteRCommitted.contains(e);
    }

    public synchronized void wakeupReaperTimer() {
        if (reapTimer != null) {
            reapTimer.wakeup();
        }
    }

    private synchronized void createTimer() {
        if (reapTimer == null) { 
            try {
            String startString = Globals.getBrokerResources().getKString(
                                 BrokerResources.I_COMMITTED_TRAN_REAPER_THREAD_START,
                                 TransactionList.TXN_REAPLIMIT,
                                 TransactionList.TXN_REAPINTERVAL/1000);
            String exitString = Globals.getBrokerResources().getKString(
                                BrokerResources.I_COMMITTED_TRAN_REAPER_THREAD_EXIT);

            reapTimer = new WakeupableTimer("TransactionReaper",
                                      this,
                                      TransactionList.TXN_REAPINTERVAL, 
                                      TransactionList.TXN_REAPINTERVAL,
                                      startString, exitString);
            } catch (Throwable ex) {
            String emsg = Globals.getBrokerResources().getKString(
                          BrokerResources.E_TXN_REAPER_START, ex.getMessage());
            logger.logStack(Logger.ERROR, emsg, ex);
            Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(),
                               emsg, BrokerEvent.Type.RESTART, ex, false, true, false);
            }
        }
    }


   /***************************************************
    * Methods for TimerEventHandler for WakeupableTimer
    ***************************************************/
    public void handleOOMError(Throwable e) {
        Globals.handleGlobalError(e, "OOM:TransactionReaper");
    }

    public void handleLogInfo(String msg) {
        logger.log(Logger.INFO, msg);
    }

    public void handleLogWarn(String msg, Throwable e) {
        if (e == null) {
            logger.log(Logger.WARNING, msg);
        } else {
            logger.logStack(Logger.WARNING, msg, e);
        }
    }

    public void handleLogError(String msg, Throwable e) {
        if (e == null) {
            logger.log(Logger.ERROR, msg);
        } else {
            logger.logStack(Logger.ERROR, msg, e);
        }
    }

    public void handleTimerExit(Throwable e) {
        if (BrokerStateHandler.isShuttingDown() || reapTimer == null) {
            return;
        }
        String emsg = Globals.getBrokerResources().getKString(
                      BrokerResources.E_TXN_REAPER_UNEXPECTED_EXIT, e.getMessage());
        Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(),
                           emsg, BrokerEvent.Type.RESTART, e, false, true, false);
    }


    public synchronized void destroy() {
        if (reapTimer != null)  {
            reapTimer.cancel();
            reapTimer = null;
        }
        committed.clear();
        remoteCommitted.clear();
        remoteRCommitted.clear();
    }

    public Hashtable getDebugState(TransactionUID id) {
        TIDEntry e = new TIDEntry(id);
        Hashtable ht =  new Hashtable();
        if (committed.contains(e)) { 
            ht.put(id.toString(), 
                TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (clusterPCommitted.contains(e)) { 
            ht.put(id.toString()+"(cluster)",
                TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (remoteCommitted.contains(e)) { 
            ht.put(id.toString()+"(remote)",
                TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (remoteRCommitted.contains(e)) { 
            ht.put(id.toString()+"(remote-r)",
                TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        return null;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        List l = null;

        ht.put("committedCount", Integer.valueOf(committed.size()));
        synchronized(committed) {
            l = new ArrayList(committed);
        }
        TIDEntry e = null;
        Iterator<TIDEntry> itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString(), 
                TransactionState.toString(TransactionState.COMMITTED)+
                ":"+e.inprocessing);

        }
        l.clear();

        ht.put("clusterPCommittedCount", Integer.valueOf(clusterPCommitted.size()));
        synchronized(clusterPCommitted) {
            l = new ArrayList(clusterPCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString()+"(cluster)", 
                TransactionState.toString(TransactionState.COMMITTED)+
                ":"+e.inprocessing+":"+e.pstate);
        }
        l.clear();

        ht.put("noremovesCount", Integer.valueOf(noremoves.size()));
        synchronized(noremoves) {
            l = new ArrayList(noremoves);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString(), 
                TransactionState.toString(TransactionState.COMMITTED));
        }
        l.clear();

        ht.put("remoteCommittedCount", Integer.valueOf(remoteCommitted.size()));
        synchronized(remoteCommitted) {
            l = new ArrayList(remoteCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString()+"(remote)", 
                TransactionState.toString(TransactionState.COMMITTED)+
                ":"+e.inprocessing);
        }
        l.clear();

        ht.put("remoteRCommittedCount", Integer.valueOf(remoteRCommitted.size()));
        synchronized(remoteRCommitted) {
            l = new ArrayList(remoteRCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString()+"(remote-r)", 
                TransactionState.toString(TransactionState.COMMITTED)+
                ":"+e.inprocessing);
        }
        l.clear();
        return ht;
    }

    private boolean clusterPCommittedHasUnprocessed() {
        List l = null;
        synchronized(clusterPCommitted) {
            l = new ArrayList(clusterPCommitted);
        }
        TIDEntry e = null;
        Iterator<TIDEntry> itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            if (e.pstate == ClusterPCommittedState.UNPROCCESSED ||
                e.pstate == ClusterPCommittedState.TAKEOVER) {
                return true;
            }
        }
        l.clear();
        return false;
    }

    private void clearSwipeMark(List<TIDEntry> list) {
        List l = null;
        synchronized(list) {
            l = new ArrayList(list);
        }
        TIDEntry e = null;
        Iterator<TIDEntry> itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            e.swipemark = false;
        }
        l.clear();
    }

    private TIDEntry getNextEntry(List<TIDEntry> l, int limit, boolean swipe) {
        TIDEntry entry = null;
        int i = 0, rsize = 0;
        synchronized(l) {
            rsize = l.size();
            while (rsize > limit) {
                entry = l.get(i++);
                if (entry.inprocessing) {
                    rsize--; 
                    entry = null;
                    continue;
                }
                if (!swipe && (entry.swipeonly || entry.swipemark)) { 
                    rsize--; 
                    entry = null;
                    continue;
                }
                if (swipe && entry.swipemark) {
                    rsize--; 
                    entry = null;
                    continue;
                }
                entry.inprocessing = true;
                if (swipe) {
                    entry.swipemark = true;
                }
                break;
            }
        }
        return entry;
    }

    private void releaseNextEntry(List<TIDEntry> l, TIDEntry entry) {
        if (l == null) {
            entry.inprocessing = false;
            return;
        } 
        synchronized(l) {
            entry.inprocessing = false;
        }
    }

    public long runTask() {
        try {
            run(false); 
        } finally {
            clearSwipeMark(clusterPCommitted);
            clearSwipeMark(committed);
        }
        return 0L;
    }

    public void run(boolean oneOnly) {

            if (!translist.isLoadComplete()) {
                if (oneOnly) {
                    return;
                }
                try {
                    logger.log(logger.INFO, Globals.getBrokerResources().getString(
                                            BrokerResources.I_REAPER_WAIT_TXN_LOAD)); 
                    translist.loadCompleteLatch.await(TransactionList.TXN_REAPINTERVAL,
                                                      TimeUnit.MILLISECONDS);
                    if (!translist.isLoadComplete()) {
                        return;
                    }
                } catch (InterruptedException e) {
                    logger.log(logger.WARNING,  
                    "Transaction reaper thread is interrupted in waiting for transaction loading completion");
                    return;
               }
            }

            ArrayList activatedBrokers = null;
            ArrayList activatedPartitions = null;
            if (!oneOnly) {
                synchronized(translist.newlyActivatedBrokers) {
                    activatedBrokers = new ArrayList(translist.newlyActivatedBrokers);
                    translist.newlyActivatedBrokers.clear();
                }
                synchronized(translist.newlyActivatedPartitions) {
                    activatedPartitions = new ArrayList(translist.newlyActivatedPartitions);
                    translist.newlyActivatedPartitions.clear();
                }
            }

            TIDEntry entry = null, tmpe = null;
         
            int count = 0;
            while (reapTimer != null) {
             
            if (count > 0 && (entry == null || oneOnly)) {
                break;
            }
            entry = null;
            count++;

            if (!oneOnly && 
                (activatedBrokers.size() > 0 ||
                 activatedPartitions.size() > 0 ||
                 clusterPCommittedHasUnprocessed())) {

            tmpe = getNextEntry(clusterPCommitted, 0, !oneOnly);

            if (tmpe != null) {
                entry = tmpe;
                TransactionUID cpcTid = entry.tid;
                ClusterPCommittedState processState = entry.pstate;
                if (processState != null && 
                    processState == ClusterPCommittedState.UNPROCCESSED) {
                    Globals.getConnectionManager().removeFromClientDataList(
                                         IMQConnection.TRANSACTION_LIST, cpcTid);
                }
                try {
                    UID tranpid = translist.getPartitionedStore().getPartitionID();
                    TransactionBroker[] brokers = translist.getClusterTransactionBrokers(cpcTid);
                    boolean completed = true;
                    if (brokers != null) { 

                    BrokerAddress to = null; 
                    for (int j = 0; j < brokers.length; j++) {
                        if (!brokers[j].isCompleted()) {
                            completed = false;
                            to = brokers[j].getCurrentBrokerAddress();
                            if (to != null) {
                                if ((to.equals(Globals.getMyAddress()) && 
                                     to.equals(brokers[j].getBrokerAddress())) &&
                                    (!translist.DL.isPartitionMode() || 
                                     tranpid.equals(brokers[j].getBrokerAddress().getStoreSessionUID()))) { 
                                    try {
                                        translist.completeClusterTransactionBrokerState(cpcTid,
                                                          TransactionState.COMMITTED, to, true);
                                    } catch (Exception e) {
                                        logger.logStack(logger.WARNING,
                                        "Unable to update transaction broker state for "+to+", TUID="+cpcTid, e);
                                    }
                                    if (!Globals.getHAEnabled()) {
                                        continue;
                                    }
                                 } 

                                 if (activatedBrokers.size() == 0 && 
                                     activatedPartitions.size() == 0 &&
                                     processState != null && 
                                     processState == ClusterPCommittedState.PROCCESSED) {
                                     continue;
                                 }

                                 if (activatedBrokers.size() == 0 && 
                                     !to.equals(Globals.getMyAddress()) &&
                                     processState != null && 
                                     processState != ClusterPCommittedState.TAKEOVER) {
                                     continue;
                                 }

                                 if (activatedBrokers.size() > 0 && 
                                     !activatedBrokers.contains(to.getMQAddress()) && 
                                     !to.equals(Globals.getMyAddress()) &&
                                     processState != null &&
                                     processState != ClusterPCommittedState.TAKEOVER) {
                                     continue;
                                 }

                                 if (TransactionList.DEBUG_CLUSTER_TXN) { 
                                     logger.log(logger.INFO, 
                                     "txnReaperThread: sendClusterTransactionInfo for TID="+cpcTid+" to "+to);
                                 }

                                 Globals.getClusterBroadcast().
                                     sendClusterTransactionInfo(cpcTid.longValue(), to);

                            } else {
                                if (processState != null && 
                                    processState != ClusterPCommittedState.PROCCESSED) {
                                    logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                           BrokerResources.W_NOTIFY_TXN_COMPLETE_UNREACHABLE, 
                                           cpcTid.toString(), brokers[j].toString()));
                                }
                            }
                        }
                    }

                    } //if brokers != null;
                    entry.pstate = ClusterPCommittedState.PROCCESSED;
                    if (!completed) {
                        releaseNextEntry(clusterPCommitted, entry);
                    } else {
                        clusterPCommitted.remove(entry);
                        releaseNextEntry(null, entry);
                        committed.add(entry);
                    }
                } catch (Throwable e) {
                    logger.logStack(logger.WARNING, e.getMessage(), e);
                }
            }
            }

            tmpe = getNextEntry(committed, TransactionList.TXN_REAPLIMIT, !oneOnly);
            if (tmpe != null) {
                entry = tmpe;
                if (TransactionList.DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, "Cleaning up committed transaction "+entry.tid);
                }
                try {
                    try {
                        translist.reapTransactionID(entry.tid, noremoves.contains(entry));
                    } catch (BrokerException e) {
                        if (e.getStatusCode() != Status.NOT_FOUND) {
                            releaseNextEntry(committed, entry);
                            entry.swipeonly = true;
                            throw e;
                        }
                        logger.logStack(logger.WARNING,
                            "Cleanup committed transaction: "+e.getMessage(), e);
                    }
                    committed.remove(entry);
                    noremoves.remove(entry);
                } catch (Exception e) {
                    logger.logStack(logger.WARNING,
                           "Failed to cleanup committed transaction "+entry.tid, e);
                }
            }

            tmpe = getNextEntry(remoteCommitted, TransactionList.TXN_REAPLIMIT, !oneOnly);
            if (tmpe != null) {
                entry = tmpe;
                remoteCommitted.remove(entry);
                if (TransactionList.DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Cleaned up committed remote transaction "+entry.tid);
                }
            }
            tmpe = getNextEntry(remoteRCommitted, TransactionList.TXN_REAPLIMIT, !oneOnly);
            if (tmpe != null) {
                entry = tmpe;
                remoteRCommitted.remove(entry);
                if (TransactionList.DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Cleaned up committed remote transaction "+entry.tid);
                }
            }
        }
    }
}

class DetachedTransactionReaper 
{
    static final int DEFAULT_TIMEOUT = 0; //no timeout
    List txns = null;
    TimerTask mytimer = null;
    TransactionList translist = null;
    private static MQTimer timer = Globals.getTimer();
    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();

    private static final int timeoutsec = Globals.getConfig().getIntProperty(
        TransactionList.XA_TXN_DETACHED_TIMEOUT_PROP, DEFAULT_TIMEOUT);

    boolean destroyed = false;

    public  DetachedTransactionReaper(TransactionList tl) {
        this.translist = tl;
        txns = new ArrayList();
    }

    public void addDetachedTID(TransactionUID tid) {
        if (translist.DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "addDetachedTID: "+tid);
        }
        if (timeoutsec <= 0) {
            return;
        }
        synchronized(this) {
            txns.add(tid);
            if (mytimer == null || txns.size() == 1) addTimer(timeoutsec);
        }
    }

    public synchronized void removeDetachedTID(TransactionUID tid) {
        boolean removed  = txns.remove(tid);
        if (removed && txns.isEmpty()) {
            removeTimer();
        }
    }

    public synchronized TransactionUID[] getDetachedTIDs() {
        return (TransactionUID[])txns.toArray(new TransactionUID[txns.size()]);
    }

    public synchronized void detachOnephasePrepared() {
        long to = Destination.RECONNECT_MULTIPLIER*(timeoutsec*1000L+
                 Globals.getConnectionManager().getMaxReconnectInterval());
        TransactionState ts = null;
        TransactionUID tid = null;
        Iterator itr = txns.iterator();
        while (itr.hasNext()) {
            tid = (TransactionUID)itr.next();
            ts = translist.retrieveState(tid);
            if (ts == null) {
                continue;
            }
            if (ts.getState() == TransactionState.PREPARED &&
                ts.getOnephasePrepare() && !ts.isDetachedFromConnection()) {
                ts.detachedFromConnection();
                logger.log(logger.INFO, br.getKString(br.I_SET_TXN_TIMEOUT, tid+"["+ts+"]", to));
            }
        }
    }

    public synchronized void destroy() {
        destroyed = true;
        if (mytimer != null) removeTimer();
        txns.clear();            
    } 
 
    private void addTimer(int timeoutsec) {
        assert Thread.holdsLock(this);
        assert mytimer == null;
        mytimer = new DetachedTransactionTimerTask(timeoutsec);
        try {
            long tm = timeoutsec*1000L; 
            Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().getKString(
                BrokerResources.I_SCHEDULE_DETACHED_TXN_REAPER, Integer.valueOf(timeoutsec)));
            timer.schedule(mytimer, tm, tm);
        } catch (IllegalStateException ex) {
            Globals.getLogger().logStack(Logger.INFO, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Failed to schedule detached-transaction reaper " + this, ex);
        }
    }

    private void removeTimer() {
        assert Thread.holdsLock(this);
        try {
            if (mytimer != null) mytimer.cancel();
        } catch (IllegalStateException ex) {
            Globals.getLogger().logStack(Logger.DEBUG, 
            "Failed to cancel detached-transaction reaper timer ", ex);
        }
        mytimer = null;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put(TransactionList.XA_TXN_DETACHED_TIMEOUT_PROP,
               String.valueOf(timeoutsec));
        ht.put("reconnectionMultiplier",  
               String.valueOf(Destination.RECONNECT_MULTIPLIER));
        ht.put("maxConnectionReconnectInterval(ms)", 
            Long.valueOf(Globals.getConnectionManager().getMaxReconnectInterval()));

        ArrayList list = null;
        synchronized(this) {
            list = new ArrayList(txns);
        }
        ht.put("DetachedTransactionCount", list.size());
        TransactionUID tid = null;
        TransactionState ts = null;
        Iterator itr = list.iterator();
        while (itr.hasNext()) {
            tid = (TransactionUID)itr.next();
            ts = translist.retrieveState(tid, true);
            ht.put(tid.toString(), ""+ts);
        }
        return ht;
    }

    class DetachedTransactionTimerTask extends TimerTask {
        private long timeout;

        public DetachedTransactionTimerTask(int timeoutsec) {
            super();
            this.timeout = timeoutsec*1000L;
        }
        public void run() {
            TransactionHandler rbh = (TransactionHandler)
                                       Globals.getPacketRouter(0).getHandler(
                                               PacketType.ROLLBACK_TRANSACTION);
            long currentTime = System.currentTimeMillis();
            TransactionUID[] tids = getDetachedTIDs();
            for (int i = 0; (i < tids.length && !destroyed); i++) {
                TransactionState ts = translist.retrieveState(tids[i]);
                if (ts == null) { 
                    if (translist.retrieveState(tids[i], true) == null) {
                        removeDetachedTID(tids[i]);
                    }
                    continue;
                }
                if (ts.getState() == TransactionState.PREPARED) {
                    if (!ts.getOnephasePrepare()) {
                        removeDetachedTID(tids[i]);
                        continue;
                    }
                } else {
                    if (ts.getState() != TransactionState.INCOMPLETE && 
                        ts.getState() != TransactionState.COMPLETE) { 
                        removeDetachedTID(tids[i]);
                        continue;
                    }
                }
                if (!ts.isDetachedFromConnection()) { 
                    continue;
                }

                if (timeout <= 0L) {
                    continue;
                }
                long realtimeout = timeout;
                if (ts.getState() == TransactionState.PREPARED) {
                    realtimeout = Destination.RECONNECT_MULTIPLIER*(timeout+
                        Globals.getConnectionManager().getMaxReconnectInterval());
                }
                long timeoutTime = ts.getDetachedTime()+realtimeout;
                if (currentTime >= timeoutTime) {
                    try {
                        String[] args = { tids[i]+"["+TransactionState.toString(ts.getState())+"]"+
                                          (ts.getOnephasePrepare() ? "onephase=true":""),
                                          String.valueOf(ts.getCreationTime()),
                                          String.valueOf(ts.getDetachedTime()) };
                        Globals.getLogger().log(Logger.WARNING, 
                        Globals.getBrokerResources().getKString(
                                BrokerResources.W_ROLLBACK_TIMEDOUT_DETACHED_TXN, args));
                        rbh.doRollback(translist, tids[i], ts.getXid(), null, ts, null,
                                                null, RollbackReason.TIMEOUT);
                        removeDetachedTID(tids[i]);
                    } catch (Exception ex) {
                        Globals.getLogger().logStack(Logger.WARNING, 
                               Globals.getBrokerResources().getKString(
                               BrokerResources.W_ROLLBACK_TIMEDOUT_DETACHED_TXN_FAILED,
                               tids[i]+"["+TransactionState.toString(ts.getState())+"]"), ex);
                    }
                }
            }
        }
    }
}


