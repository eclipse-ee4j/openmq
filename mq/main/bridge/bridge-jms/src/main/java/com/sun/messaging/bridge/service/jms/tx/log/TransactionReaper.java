/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;

class TransactionReaper implements TimerEventHandler {
    private TxLog _txlog = null;
    private Logger _logger = null;

    private Vector removes = new Vector();
    private WakeupableTimer reapTimer = null;
    private int _reapLimit = 1;
    private long _reapInterval = 1000; // in millisecs

    /*
     * @param reapInterval in secs
     */
    TransactionReaper(TxLog txlog, int reapLimit, int reapInterval) {
        _txlog = txlog;
        _logger = _txlog.getLogger();
        _reapLimit = reapLimit;
        if (_reapInterval <= 0) {
            _reapInterval = TxLog.DEFAULT_REAPINTERVAL;
        }
        _reapInterval = reapInterval * 1000L;
    }

    public void addTransaction(GlobalXid gxid) {
        if (_reapLimit == 0 || removes.size() > (2 * _reapLimit)) {
            try {
                _txlog.reap(gxid.toString());
                return;
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Failed to cleanup global transaction " + gxid + ":" + e + ", will retry later", e);
            }
        }
        removes.add(gxid);
        createTimer();
        if (removes.size() > _reapLimit) {
            synchronized (this) {
                if (reapTimer != null) {
                    reapTimer.wakeup();
                }
            }
        }
    }

    private synchronized void createTimer() {
        if (reapTimer == null) {
            try {
                String startString = "Transaction reaper thread has started for TM " + _txlog.getTMName();
                String exitString = "Transaction reaper thread for TM " + _txlog.getTMName() + " is exiting";

                reapTimer = new WakeupableTimer("JMSBridgeTMTransactionReaper-" + _txlog.getTMName(), this, _reapInterval, _reapInterval, startString,
                        exitString);
            } catch (Throwable ex) {
                _logger.log(Level.WARNING, "Unable to start transaction reaper thread for TM " + _txlog.getTMName(), ex);
                try {
                    _txlog.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /***************************************************
     * Methods for TimerEventHandler for WakeupableTimer
     ***************************************************/
    @Override
    public void handleOOMError(Throwable e) {
    }

    @Override
    public void handleLogInfo(String msg) {
        if (_logger == null) {
            return;
        }
        _logger.log(Level.INFO, msg);
    }

    @Override
    public void handleLogWarn(String msg, Throwable e) {
        if (_logger == null) {
            return;
        }
        _logger.log(Level.WARNING, msg, e);
    }

    @Override
    public void handleLogError(String msg, Throwable e) {
        if (_logger == null) {
            return;
        }
        _logger.log(Level.SEVERE, msg, e);
    }

    @Override
    public void handleTimerExit(Throwable e) {
        synchronized (this) {
            if (reapTimer == null) {
                return;
            }
        }
        if (_txlog.isClosed()) {
            return;
        }
        _logger.log(Level.SEVERE, "[" + _txlog.getTMName() + "]Unexpected transaction log reaper thread exit", e);
        try {
            _txlog.close();
        } catch (Exception ex) {
            _logger.log(Level.WARNING, "[" + _txlog.getTMName() + "]" + e.getMessage(), e);
        }
    }

    public synchronized void destroy() {
        if (reapTimer != null) {
            reapTimer.cancel();
            reapTimer = null;
        }
        removes.clear();
    }

    @Override
    public long runTask() {

        GlobalXid[] gxids = null;
        synchronized (removes) {
            gxids = (GlobalXid[]) removes.toArray(new GlobalXid[removes.size()]);
        }
        int cnt = gxids.length - _reapLimit;
        for (int i = 0; i < cnt; i++) {
            String key = gxids[i].toString();
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Cleaning up global transaction " + key);
            }
            try {
                _txlog.reap(key);
                removes.remove(gxids[i]);
            } catch (Exception e) {
                _logger.log(Level.WARNING, "Failed to cleanup global transaction " + key, e);
            }
        }
        return 0L;
    }
}
