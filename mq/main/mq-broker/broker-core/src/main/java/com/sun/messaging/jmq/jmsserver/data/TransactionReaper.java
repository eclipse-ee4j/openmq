/*
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;

class TransactionReaper implements TimerEventHandler {
    TransactionList translist = null;
    Logger logger = Globals.getLogger();

    List<TIDEntry> committed = Collections.synchronizedList(new ArrayList<>());
    List<TIDEntry> noremoves = Collections.synchronizedList(new ArrayList<>());
    List<TIDEntry> clusterPCommitted = Collections.synchronizedList(new ArrayList<>());
    List<TIDEntry> remoteCommitted = Collections.synchronizedList(new ArrayList<>());
    List<TIDEntry> remoteRCommitted = Collections.synchronizedList(new ArrayList<>());
    WakeupableTimer reapTimer = null;

    enum ClusterPCommittedState {
        UNPROCCESSED, PROCCESSED, TAKEOVER
    }

    static class TIDEntry {
        TransactionUID tid = null;
        boolean inprocessing = false;
        ClusterPCommittedState pstate = ClusterPCommittedState.UNPROCCESSED;
        boolean swipemark = false;
        boolean swipeonly = false;

        TIDEntry(TransactionUID id) {
            tid = id;
        }

        TIDEntry(TransactionUID id, ClusterPCommittedState s) {
            tid = id;
            pstate = s;
        }

        @Override
        public int hashCode() {
            return tid.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TIDEntry) {
                return tid.equals(((TIDEntry) obj).tid);
            }
            return false;
        }
    }

    TransactionReaper(TransactionList tl) {
        this.translist = tl;
    }

    private boolean needReapOne(List l) {
        if (TransactionList.TXN_REAPLIMIT == 0) {
            return true;
        }
        int size = l.size();
        if (size > TransactionList.TXN_REAPLIMIT * (1.0 + ((float) TransactionList.TXN_REAPLIMIT_OVERTHRESHOLD) / 100)) {
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
            if (e instanceof BrokerException && ((BrokerException) e).getStatusCode() == Status.NOT_FOUND) {
                logstack = false;
            }
            String emsg = translist.br.getKString(translist.br.W_UNABLE_GET_TXN_BROKERS_ON_TXN_COMPLETE, tid, e.getMessage());
            if (logstack) {
                logger.logStack(logger.WARNING, emsg, e);
            } else {
                if (translist.DEBUG) {
                    logger.log(logger.INFO, emsg + " - all completed");
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
            TIDEntry e = new TIDEntry(tid);
            synchronized (clusterPCommitted) {
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
                    Globals.getConnectionManager().removeFromClientDataList(IMQConnection.TRANSACTION_LIST, entry.tid);
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
        if (remoteCommitted.size() > TransactionList.TXN_REAPLIMIT || remoteRCommitted.size() > TransactionList.TXN_REAPLIMIT) {
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
                String startString = Globals.getBrokerResources().getKString(BrokerResources.I_COMMITTED_TRAN_REAPER_THREAD_START,
                        TransactionList.TXN_REAPLIMIT, TransactionList.TXN_REAPINTERVAL / 1000);
                String exitString = Globals.getBrokerResources().getKString(BrokerResources.I_COMMITTED_TRAN_REAPER_THREAD_EXIT);

                reapTimer = new WakeupableTimer("TransactionReaper", this, TransactionList.TXN_REAPINTERVAL, TransactionList.TXN_REAPINTERVAL, startString,
                        exitString);
            } catch (Throwable ex) {
                String emsg = Globals.getBrokerResources().getKString(BrokerResources.E_TXN_REAPER_START, ex.getMessage());
                logger.logStack(Logger.ERROR, emsg, ex);
                Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(), emsg, BrokerEvent.Type.RESTART, ex, false, true, false);
            }
        }
    }

    /***************************************************
     * Methods for TimerEventHandler for WakeupableTimer
     ***************************************************/
    @Override
    public void handleOOMError(Throwable e) {
        Globals.handleGlobalError(e, "OOM:TransactionReaper");
    }

    @Override
    public void handleLogInfo(String msg) {
        logger.log(Logger.INFO, msg);
    }

    @Override
    public void handleLogWarn(String msg, Throwable e) {
        if (e == null) {
            logger.log(Logger.WARNING, msg);
        } else {
            logger.logStack(Logger.WARNING, msg, e);
        }
    }

    @Override
    public void handleLogError(String msg, Throwable e) {
        if (e == null) {
            logger.log(Logger.ERROR, msg);
        } else {
            logger.logStack(Logger.ERROR, msg, e);
        }
    }

    @Override
    public void handleTimerExit(Throwable e) {
        if (BrokerStateHandler.isShuttingDown() || reapTimer == null) {
            return;
        }
        String emsg = Globals.getBrokerResources().getKString(BrokerResources.E_TXN_REAPER_UNEXPECTED_EXIT, e.getMessage());
        Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(), emsg, BrokerEvent.Type.RESTART, e, false, true, false);
    }

    public synchronized void destroy() {
        if (reapTimer != null) {
            reapTimer.cancel();
            reapTimer = null;
        }
        committed.clear();
        remoteCommitted.clear();
        remoteRCommitted.clear();
    }

    public Hashtable getDebugState(TransactionUID id) {
        TIDEntry e = new TIDEntry(id);
        Hashtable ht = new Hashtable();
        if (committed.contains(e)) {
            ht.put(id.toString(), TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (clusterPCommitted.contains(e)) {
            ht.put(id.toString() + "(cluster)", TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (remoteCommitted.contains(e)) {
            ht.put(id.toString() + "(remote)", TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        if (remoteRCommitted.contains(e)) {
            ht.put(id.toString() + "(remote-r)", TransactionState.toString(TransactionState.COMMITTED));
            return ht;
        }
        return null;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        List l = null;

        ht.put("committedCount", Integer.valueOf(committed.size()));
        synchronized (committed) {
            l = new ArrayList(committed);
        }
        TIDEntry e = null;
        Iterator<TIDEntry> itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString(), TransactionState.toString(TransactionState.COMMITTED) + ":" + e.inprocessing);

        }
        l.clear();

        ht.put("clusterPCommittedCount", Integer.valueOf(clusterPCommitted.size()));
        synchronized (clusterPCommitted) {
            l = new ArrayList(clusterPCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString() + "(cluster)", TransactionState.toString(TransactionState.COMMITTED) + ":" + e.inprocessing + ":" + e.pstate);
        }
        l.clear();

        ht.put("noremovesCount", Integer.valueOf(noremoves.size()));
        synchronized (noremoves) {
            l = new ArrayList(noremoves);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString(), TransactionState.toString(TransactionState.COMMITTED));
        }
        l.clear();

        ht.put("remoteCommittedCount", Integer.valueOf(remoteCommitted.size()));
        synchronized (remoteCommitted) {
            l = new ArrayList(remoteCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString() + "(remote)", TransactionState.toString(TransactionState.COMMITTED) + ":" + e.inprocessing);
        }
        l.clear();

        ht.put("remoteRCommittedCount", Integer.valueOf(remoteRCommitted.size()));
        synchronized (remoteRCommitted) {
            l = new ArrayList(remoteRCommitted);
        }
        itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            ht.put(e.tid.toString() + "(remote-r)", TransactionState.toString(TransactionState.COMMITTED) + ":" + e.inprocessing);
        }
        l.clear();
        return ht;
    }

    private boolean clusterPCommittedHasUnprocessed() {
        List l = null;
        synchronized (clusterPCommitted) {
            l = new ArrayList(clusterPCommitted);
        }
        TIDEntry e = null;
        Iterator<TIDEntry> itr = l.iterator();
        while (itr.hasNext()) {
            e = itr.next();
            if (e.pstate == ClusterPCommittedState.UNPROCCESSED || e.pstate == ClusterPCommittedState.TAKEOVER) {
                return true;
            }
        }
        l.clear();
        return false;
    }

    private void clearSwipeMark(List<TIDEntry> list) {
        List l = null;
        synchronized (list) {
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
        synchronized (l) {
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
        synchronized (l) {
            entry.inprocessing = false;
        }
    }

    @Override
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
                logger.log(logger.INFO, Globals.getBrokerResources().getString(BrokerResources.I_REAPER_WAIT_TXN_LOAD));
                translist.loadCompleteLatch.await(TransactionList.TXN_REAPINTERVAL, TimeUnit.MILLISECONDS);
                if (!translist.isLoadComplete()) {
                    return;
                }
            } catch (InterruptedException e) {
                logger.log(logger.WARNING, "Transaction reaper thread is interrupted in waiting for transaction loading completion");
                return;
            }
        }

        ArrayList activatedBrokers = null;
        ArrayList activatedPartitions = null;
        if (!oneOnly) {
            synchronized (translist.newlyActivatedBrokers) {
                activatedBrokers = new ArrayList(translist.newlyActivatedBrokers);
                translist.newlyActivatedBrokers.clear();
            }
            synchronized (translist.newlyActivatedPartitions) {
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

            if (!oneOnly && (activatedBrokers.size() > 0 || activatedPartitions.size() > 0 || clusterPCommittedHasUnprocessed())) {

                tmpe = getNextEntry(clusterPCommitted, 0, !oneOnly);

                if (tmpe != null) {
                    entry = tmpe;
                    TransactionUID cpcTid = entry.tid;
                    ClusterPCommittedState processState = entry.pstate;
                    if (processState != null && processState == ClusterPCommittedState.UNPROCCESSED) {
                        Globals.getConnectionManager().removeFromClientDataList(IMQConnection.TRANSACTION_LIST, cpcTid);
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
                                        if ((to.equals(Globals.getMyAddress()) && to.equals(brokers[j].getBrokerAddress()))
                                                && (!translist.DL.isPartitionMode() || tranpid.equals(brokers[j].getBrokerAddress().getStoreSessionUID()))) {
                                            try {
                                                translist.completeClusterTransactionBrokerState(cpcTid, TransactionState.COMMITTED, to, true);
                                            } catch (Exception e) {
                                                logger.logStack(logger.WARNING, "Unable to update transaction broker state for " + to + ", TUID=" + cpcTid, e);
                                            }
                                            if (!Globals.getHAEnabled()) {
                                                continue;
                                            }
                                        }

                                        if (activatedBrokers.size() == 0 && activatedPartitions.size() == 0 && processState != null
                                                && processState == ClusterPCommittedState.PROCCESSED) {
                                            continue;
                                        }

                                        if (activatedBrokers.size() == 0 && !to.equals(Globals.getMyAddress()) && processState != null
                                                && processState != ClusterPCommittedState.TAKEOVER) {
                                            continue;
                                        }

                                        if (activatedBrokers.size() > 0 && !activatedBrokers.contains(to.getMQAddress()) && !to.equals(Globals.getMyAddress())
                                                && processState != null && processState != ClusterPCommittedState.TAKEOVER) {
                                            continue;
                                        }

                                        if (TransactionList.DEBUG_CLUSTER_TXN) {
                                            logger.log(logger.INFO, "txnReaperThread: sendClusterTransactionInfo for TID=" + cpcTid + " to " + to);
                                        }

                                        Globals.getClusterBroadcast().sendClusterTransactionInfo(cpcTid.longValue(), to);

                                    } else {
                                        if (processState != null && processState != ClusterPCommittedState.PROCCESSED) {
                                            logger.log(logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.W_NOTIFY_TXN_COMPLETE_UNREACHABLE,
                                                    cpcTid.toString(), brokers[j].toString()));
                                        }
                                    }
                                }
                            }

                        } // if brokers != null;
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
                    logger.log(logger.INFO, "Cleaning up committed transaction " + entry.tid);
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
                        logger.logStack(logger.WARNING, "Cleanup committed transaction: " + e.getMessage(), e);
                    }
                    committed.remove(entry);
                    noremoves.remove(entry);
                } catch (Exception e) {
                    logger.logStack(logger.WARNING, "Failed to cleanup committed transaction " + entry.tid, e);
                }
            }

            tmpe = getNextEntry(remoteCommitted, TransactionList.TXN_REAPLIMIT, !oneOnly);
            if (tmpe != null) {
                entry = tmpe;
                remoteCommitted.remove(entry);
                if (TransactionList.DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, "Cleaned up committed remote transaction " + entry.tid);
                }
            }
            tmpe = getNextEntry(remoteRCommitted, TransactionList.TXN_REAPLIMIT, !oneOnly);
            if (tmpe != null) {
                entry = tmpe;
                remoteRCommitted.remove(entry);
                if (TransactionList.DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, "Cleaned up committed remote transaction " + entry.tid);
                }
            }
        }
    }
}

