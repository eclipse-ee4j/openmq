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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;

import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.timer.MQTimer;

class DetachedTransactionReaper {
    static final int DEFAULT_TIMEOUT = 0; // no timeout
    List txns = null;
    TimerTask mytimer = null;
    TransactionList translist = null;
    private static MQTimer timer = Globals.getTimer();
    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();

    private static final int timeoutsec = Globals.getConfig().getIntProperty(TransactionList.XA_TXN_DETACHED_TIMEOUT_PROP, DEFAULT_TIMEOUT);

    boolean destroyed = false;

    DetachedTransactionReaper(TransactionList tl) {
        this.translist = tl;
        txns = new ArrayList();
    }

    public void addDetachedTID(TransactionUID tid) {
        if (translist.DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "addDetachedTID: " + tid);
        }
        if (timeoutsec <= 0) {
            return;
        }
        synchronized (this) {
            txns.add(tid);
            if (mytimer == null || txns.size() == 1) {
                addTimer(timeoutsec);
            }
        }
    }

    public synchronized void removeDetachedTID(TransactionUID tid) {
        boolean removed = txns.remove(tid);
        if (removed && txns.isEmpty()) {
            removeTimer();
        }
    }

    public synchronized TransactionUID[] getDetachedTIDs() {
        return (TransactionUID[]) txns.toArray(new TransactionUID[txns.size()]);
    }

    public synchronized void detachOnephasePrepared() {
        long to = Destination.RECONNECT_MULTIPLIER * (timeoutsec * 1000L + Globals.getConnectionManager().getMaxReconnectInterval());
        TransactionState ts = null;
        TransactionUID tid = null;
        Iterator itr = txns.iterator();
        while (itr.hasNext()) {
            tid = (TransactionUID) itr.next();
            ts = translist.retrieveState(tid);
            if (ts == null) {
                continue;
            }
            if (ts.getState() == TransactionState.PREPARED && ts.getOnephasePrepare() && !ts.isDetachedFromConnection()) {
                ts.detachedFromConnection();
                logger.log(logger.INFO, br.getKString(br.I_SET_TXN_TIMEOUT, tid + "[" + ts + "]", to));
            }
        }
    }

    public synchronized void destroy() {
        destroyed = true;
        if (mytimer != null) {
            removeTimer();
        }
        txns.clear();
    }

    private void addTimer(int timeoutsec) {
        assert Thread.holdsLock(this);
        assert mytimer == null;
        mytimer = new DetachedTransactionTimerTask(timeoutsec);
        try {
            long tm = timeoutsec * 1000L;
            Globals.getLogger().log(Logger.INFO,
                    Globals.getBrokerResources().getKString(BrokerResources.I_SCHEDULE_DETACHED_TXN_REAPER, Integer.valueOf(timeoutsec)));
            timer.schedule(mytimer, tm, tm);
        } catch (IllegalStateException ex) {
            Globals.getLogger().logStack(Logger.INFO, BrokerResources.E_INTERNAL_BROKER_ERROR, "Failed to schedule detached-transaction reaper " + this, ex);
        }
    }

    private void removeTimer() {
        assert Thread.holdsLock(this);
        try {
            if (mytimer != null) {
                mytimer.cancel();
            }
        } catch (IllegalStateException ex) {
            Globals.getLogger().logStack(Logger.DEBUG, "Failed to cancel detached-transaction reaper timer ", ex);
        }
        mytimer = null;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put(TransactionList.XA_TXN_DETACHED_TIMEOUT_PROP, String.valueOf(timeoutsec));
        ht.put("reconnectionMultiplier", String.valueOf(Destination.RECONNECT_MULTIPLIER));
        ht.put("maxConnectionReconnectInterval(ms)", Long.valueOf(Globals.getConnectionManager().getMaxReconnectInterval()));

        ArrayList list = null;
        synchronized (this) {
            list = new ArrayList(txns);
        }
        ht.put("DetachedTransactionCount", list.size());
        TransactionUID tid = null;
        TransactionState ts = null;
        Iterator itr = list.iterator();
        while (itr.hasNext()) {
            tid = (TransactionUID) itr.next();
            ts = translist.retrieveState(tid, true);
            ht.put(tid.toString(), "" + ts);
        }
        return ht;
    }

    class DetachedTransactionTimerTask extends TimerTask {
        private long timeout;

        DetachedTransactionTimerTask(int timeoutsec) {
            this.timeout = timeoutsec * 1000L;
        }

        @Override
        public void run() {
            TransactionHandler rbh = (TransactionHandler) Globals.getPacketRouter(0).getHandler(PacketType.ROLLBACK_TRANSACTION);
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
                    if (ts.getState() != TransactionState.INCOMPLETE && ts.getState() != TransactionState.COMPLETE) {
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
                    realtimeout = Destination.RECONNECT_MULTIPLIER * (timeout + Globals.getConnectionManager().getMaxReconnectInterval());
                }
                long timeoutTime = ts.getDetachedTime() + realtimeout;
                if (currentTime >= timeoutTime) {
                    try {
                        String[] args = { tids[i] + "[" + TransactionState.toString(ts.getState()) + "]" + (ts.getOnephasePrepare() ? "onephase=true" : ""),
                                String.valueOf(ts.getCreationTime()), String.valueOf(ts.getDetachedTime()) };
                        Globals.getLogger().log(Logger.WARNING,
                                Globals.getBrokerResources().getKString(BrokerResources.W_ROLLBACK_TIMEDOUT_DETACHED_TXN, args));
                        rbh.doRollback(translist, tids[i], ts.getXid(), null, ts, null, null, RollbackReason.TIMEOUT);
                        removeDetachedTID(tids[i]);
                    } catch (Exception ex) {
                        Globals.getLogger().logStack(Logger.WARNING, Globals.getBrokerResources().getKString(
                                BrokerResources.W_ROLLBACK_TIMEDOUT_DETACHED_TXN_FAILED, tids[i] + "[" + TransactionState.toString(ts.getState()) + "]"), ex);
                    }
                }
            }
        }
    }
}
