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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException;
import com.sun.messaging.jmq.util.UID;

class TransactionInformation {
    int type = TransactionInfo.TXN_NOFLAG;

    ArrayList published;
    LinkedHashMap consumed;
    LinkedHashMap removedConsumedRBD; // remote broker down
    LinkedHashMap removedConsumedRRT; // remote rerouted
    TransactionState state;
    HashMap cuidToStored;
    HashMap sysidToAddr;
    LinkedHashMap orphanedMessages;
    TransactionUID tid = null;
    // boolean persisted = false;
    boolean inROLLBACK = false;

    TransactionBroker[] brokers = null;
    // boolean cluster = false;
    boolean processed = false;

    private ReentrantLock takeoverLock = new ReentrantLock();

    TransactionInformation(TransactionUID tid, TransactionState state) {
        published = new ArrayList();
        consumed = new LinkedHashMap();
        removedConsumedRBD = new LinkedHashMap();
        removedConsumedRRT = new LinkedHashMap();
        cuidToStored = new HashMap();
        sysidToAddr = new HashMap();
        orphanedMessages = new LinkedHashMap();

        this.state = state;
        this.tid = tid;
        // this.persisted = persist;
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
        if (processed) {
            return true;
        }
        processed = true;
        return false;
    }

    public synchronized boolean isProcessed() {
        return processed;
    }

    @Override
    public synchronized String toString() {
        if (type == TransactionInfo.TXN_CLUSTER) {
            StringBuilder buf = new StringBuilder();
            buf.append("TransactionInfo[").append(tid).append("]cluster - ");
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                buf.append(brokers[i]);
            }
            return buf.toString();
        }
        return "TransactionInfo[" + tid + "]local";
    }

    public synchronized void addOrphanAck(SysMessageID sysid, ConsumerUID sid) {
        addOrphanAck(sysid, sid, null);
    }

    public synchronized void addOrphanAck(SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        Map m = (Map) orphanedMessages.get(sysid);
        if (m == null) {
            m = new LinkedHashMap();
            orphanedMessages.put(sysid, m);
        }
        List l = (List) m.get(sid);
        if (l == null) {
            l = new ArrayList();
            m.put(sid, l);
        }
        if (cid != null) {
            l.add(cid);
        }
    }

    public synchronized void removeOrphanAck(SysMessageID sysid, ConsumerUID sid, ConsumerUID cid) {
        Map m = (Map) orphanedMessages.get(sysid);
        if (m == null) {
            return;
        }
        if (cid == null) {
            m.remove(sid);
            if (m.size() == 0) {
                orphanedMessages.remove(sysid);
            }
            return;
        }
        List l = (List) m.get(sid);
        if (l == null) {
            return;
        }
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
                    Map.Entry me = (Map.Entry) itr.next();
                    cid.put(me.getKey().toString(), me.getValue().toString());
                }
                ht.put("cuidToStored", cid);
            }
            if (consumed.size() > 0) {
                Hashtable m = new Hashtable();
                Iterator itr = consumed.entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry me = (Map.Entry) itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList) me.getValue();
                    if (ids.size() == 0) {
                        continue;
                    }
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i = 0; i < ids.size(); i++) {
                            v.add(ids.get(i).toString());
                        }
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
                    Map.Entry me = (Map.Entry) itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList) me.getValue();
                    if (ids.size() == 0) {
                        continue;
                    }
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i = 0; i < ids.size(); i++) {
                            v.add(ids.get(i).toString());
                        }
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
                    Map.Entry me = (Map.Entry) itr.next();
                    String id = me.getKey().toString();
                    ArrayList ids = (ArrayList) me.getValue();
                    if (ids.size() == 0) {
                        continue;
                    }
                    if (ids.size() == 1) {
                        m.put(id, ids.get(0).toString());
                    } else {
                        Vector v = new Vector();
                        for (int i = 0; i < ids.size(); i++) {
                            v.add(ids.get(i).toString());
                        }
                        m.put(id, v);
                    }
                }
                if (m.size() > 0) {
                    ht.put("removedConsumedRRT", m);
                }
            }

            if (published.size() > 0) {
                Vector v = new Vector();
                for (int i = 0; i < published.size(); i++) {
                    v.add(published.get(i).toString());
                }
                ht.put("published", v);
            }
            if (type == TransactionInfo.TXN_CLUSTER) {
                Vector v = new Vector();
                for (int i = 0; i < brokers.length; i++) {
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

    public synchronized void addPublishedMessage(SysMessageID id) throws BrokerException {

        // first check if we have exceeded our maximum message count per txn
        if (published.size() < TransactionList.defaultProducerMaxMsgCnt) {
            published.add(id);
        } else {
            throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TXN_PRODUCER_MAX_MESSAGE_COUNT_EXCEEDED,
                    TransactionList.defaultProducerMaxMsgCnt, tid), BrokerResources.X_TXN_PRODUCER_MAX_MESSAGE_COUNT_EXCEEDED, (Throwable) null,
                    Status.RESOURCE_FULL);
        }
    }

    public synchronized boolean checkConsumedMessage(SysMessageID sysid, ConsumerUID id) {

        List l = (List) consumed.get(sysid);
        if (l == null) {
            return false;
        }
        return l.contains(id);
    }

    public synchronized boolean isConsumedMessage(SysMessageID sysid, ConsumerUID id) {
        if (state == null) {
            return false;
        }
        if (state.getState() == TransactionState.ROLLEDBACK) {
            return false;
        }
        if (inROLLBACK) {
            return false;
        }

        List l = (List) consumed.get(sysid);
        if (l == null) {
            return false;
        }
        return l.contains(id);
    }

    public synchronized void addConsumedMessage(SysMessageID sysid, ConsumerUID id, ConsumerUID sid) throws BrokerException {

        // first check if we have exceeded our maximum message count per txn
        if (consumed.size() < TransactionList.defaultConsumerMaxMsgCnt) {
            List l = (List) consumed.get(sysid);
            if (l == null) {
                l = new ArrayList();
                consumed.put(sysid, l);
            } else if (l.contains(id)) {
                throw new TransactionAckExistException(Globals.getBrokerResources().getKString(Globals.getBrokerResources().X_ACK_EXISTS_IN_TRANSACTION,
                        "[" + sysid + ":" + id + "," + sid + "]", tid), Status.CONFLICT);
            }
            l.add(id);
            cuidToStored.put(id, sid);
        } else {
            throw new BrokerException(Globals.getBrokerResources().getKString(BrokerResources.X_TXN_CONSUMER_MAX_MESSAGE_COUNT_EXCEEDED,
                    TransactionList.defaultConsumerMaxMsgCnt, tid), BrokerResources.X_TXN_CONSUMER_MAX_MESSAGE_COUNT_EXCEEDED, (Throwable) null,
                    Status.RESOURCE_FULL);
        }
    }

    public synchronized void setAckBrokerAddress(SysMessageID sysid, ConsumerUID id, BrokerAddress addr) throws BrokerException {

        BrokerAddress ba = (BrokerAddress) sysidToAddr.get(sysid);
        if (ba != null && (!ba.equals(addr) || !ba.getBrokerSessionUID().equals(addr.getBrokerSessionUID()))) {
            BrokerException bex = new BrokerException("Message requeued:" + sysid, Status.GONE);
            bex.setRemoteConsumerUIDs(String.valueOf(id.longValue()));
            bex.setRemote(true);
            throw bex;
        }
        sysidToAddr.put(sysid, addr);
    }

    public synchronized BrokerAddress getAckBrokerAddress(SysMessageID sysid, ConsumerUID id) {
        return (BrokerAddress) sysidToAddr.get(sysid);
    }

    public synchronized HashMap getAckBrokerAddresses() {
        return sysidToAddr;
    }

    public TransactionUID getTID() {
        return tid;
    }

    public synchronized ConsumerUID removeConsumedMessage(SysMessageID sysid, ConsumerUID id, boolean rerouted) throws BrokerException {

        List l = (List) consumed.get(sysid);
        if (l == null) {
            throw new BrokerException(
                    Globals.getBrokerResources().getKString(BrokerResources.X_CONSUMED_MSG_NOT_FOUND_IN_TXN, "[" + sysid + "," + id + "]", tid.toString()));
        }
        l.remove(id);
        if (l.size() == 0) {
            consumed.remove(sysid);
        }
        if (!rerouted) {
            l = (List) removedConsumedRBD.get(sysid);
            if (l == null) {
                l = new ArrayList();
                removedConsumedRBD.put(sysid, l);
            }
            l.add(id);
        } else {
            l = (List) removedConsumedRRT.get(sysid);
            if (l == null) {
                l = new ArrayList();
                removedConsumedRRT.put(sysid, l);
            }
            l.add(id);
        }
        return (ConsumerUID) cuidToStored.get(id);
    }

    public synchronized LinkedHashMap getRemovedConsumedMessages(boolean rerouted) {
        if (!rerouted) {
            return removedConsumedRBD;
        }
        return removedConsumedRRT;
    }

    public synchronized void setClusterTransactionBrokers(TransactionBroker[] brokers) {

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

    public synchronized TransactionBroker getClusterTransactionBroker(BrokerAddress b) {

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

        if (brokers == null) {
            return false;
        }
        for (int i = 0; i < brokers.length; i++) {
            if (brokers[i].isSame(ssid)) {
                return true;
            }
        }
        return false;
    }
}

