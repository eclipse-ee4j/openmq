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

import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;

class RemoteTransactionInformation extends TransactionInformation {
    RemoteTransactionAckEntry txnAckEntry = null;
    ArrayList recoveryTxnAckEntrys = new ArrayList();
    TransactionBroker txnhome = null;
    long pendingStartTime = 0L;

    RemoteTransactionInformation(TransactionUID tid, TransactionState state, TransactionAcknowledgement[] acks, BrokerAddress txnhome, boolean recovery,
            boolean localremote) {
        super(tid, state);
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

    @Override
    public synchronized String toString() {
        return "RemoteTransactionInfo[" + tid + "]remote - " + txnhome.toString();
    }

    public synchronized TransactionBroker getTransactionHomeBroker() {
        return txnhome;
    }

    public synchronized RemoteTransactionAckEntry getTransactionAcks() {
        return txnAckEntry;
    }

    public synchronized RemoteTransactionAckEntry[] getRecoveryTransactionAcks() {

        if (recoveryTxnAckEntrys.size() == 0) {
            return null;
        }
        return (RemoteTransactionAckEntry[]) recoveryTxnAckEntrys.toArray(new RemoteTransactionAckEntry[recoveryTxnAckEntrys.size()]);
    }

    public synchronized void addRecoveryTransactionAcks(TransactionAcknowledgement[] acks) {

        if (getState().getState() == TransactionState.PREPARED) {
            recoveryTxnAckEntrys.add(new RemoteTransactionAckEntry(acks, true, false));
        } else {
            recoveryTxnAckEntrys.add(new RemoteTransactionAckEntry(acks, true, true));
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
            tae = (RemoteTransactionAckEntry) itr.next();
            n += tae.getAcks().length;
        }
        return n;
    }

    @Override
    public boolean isProcessed() {

        if (txnAckEntry != null && !txnAckEntry.isProcessed()) {
            return false;
        }

        synchronized (this) {
            RemoteTransactionAckEntry tae = null;
            Iterator itr = recoveryTxnAckEntrys.iterator();
            while (itr.hasNext()) {
                tae = (RemoteTransactionAckEntry) itr.next();
                if (!tae.isProcessed()) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized boolean isRecovery() {
        return (recoveryTxnAckEntrys.size() > 0);
    }

    @Override
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
                for (int i = 0, len = recoveryTxnAckEntrys.size(); i < len; i++) {
                    l.add(((RemoteTransactionAckEntry) recoveryTxnAckEntrys.get(i)).getDebugState());
                }
                ht.put("recoveryTxnAckEntrys", l);
            }
        }

        return ht;
    }
}

