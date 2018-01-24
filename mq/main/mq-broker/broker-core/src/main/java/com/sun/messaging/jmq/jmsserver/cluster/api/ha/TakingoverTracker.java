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
 * @(#)TakingoverTracker.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api.ha;

import java.util.Map;
import java.util.List;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.Destination;


/**
 */
public final class TakingoverTracker {

    public static final int BEFORE_GET_LOCK   = 0;
    public static final int AFTER_GET_LOCK    = 1;

    public static final int BEFORE_TAKE_STORE = 2;
    public static final int BEFORE_DB_SWITCH_OWNER = 3;
    public static final int AFTER_DB_SWITCH_OWNER  = 4;
    public static final int AFTER_TAKE_STORE  = 5;

    public static final int BEFORE_PROCESSING = 6;
    public static final int AFTER_PROCESSING  = 7;

    private String targetName = null;   // Broker that is being taken over
    private UID storeSession = null;  
    private Thread runner = null;
    private Map msgMap = null; // Message IDs & destination IDS to be takeover
    private int stage = -1;
    private int substage = -1;

    private UID brokerSession = null;
    private UID downStoreSession = null;
    private long lastHeartbeat = 0;
    private List<Long> takeoverStoreSessions = null;

    /**
     */
    public TakingoverTracker(String targetName, Thread runnerThread) {

        this.targetName = targetName;
        runner = runnerThread;
    }

    public void setStoreSession(long sid) {
        storeSession = new UID(sid);
    }

    public UID getStoreSessionUID() {
        return storeSession;
    }

    public void setBrokerSessionUID(UID bss) {
        brokerSession = bss;
    }

    public UID getBrokerSessionUID() {
        return brokerSession;
    }

    public void setDownStoreSessionUID(UID ss) {
        downStoreSession = ss;
    }

    public UID getDownStoreSessionUID() {
        return downStoreSession;
    }

    public void setLastHeartbeat(long ts) {
        lastHeartbeat = ts;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setTakeoverStoreSessionList(List<Long> l) {
        takeoverStoreSessions = l;
    }

    public List<Long> getTakeoverStoreSessionList() {
        return takeoverStoreSessions; 
    }

    /**
     * Return the brokerID that is being taken over.
     * @return the brokerID that is being taken over
     */
    public final String getTargetName() {
        return targetName;
    }

    public final Thread getTakeoverRunner() { 
        return runner;
    }

    public final int getStage() {
        if (substage != -1) return substage;
        return stage;
    }

    /**
     * @param mMap of message IDs and corresponding destination IDs to be taken over
     */
    public final void setMessageMap(Map mMap) throws BrokerException {
        if (Thread.currentThread() != runner) {
            throw new BrokerException(
        "Internal Error: TakingoverTracker.setMessageMap() not runner thread");
        }
        if (stage < AFTER_GET_LOCK ||
            stage > BEFORE_DB_SWITCH_OWNER) {
            throw new BrokerException(
        "Internal Error: TakingoverTracker.setMessageMap() unexpected stage "+
         stage+"("+substage+")");
        }
        msgMap = mMap;
    }

    public final boolean containDestination(Destination d) {
        if (msgMap == null) return false;
        return msgMap.containsValue(d.getDestinationUID().toString());
    }

    public final boolean containMessage(Packet m) {
        if (msgMap == null) return false;
        return msgMap.containsKey(m.getSysMessageID().toString());
    }

    public final boolean containStoreSession(Long ss) {
        if (takeoverStoreSessions == null) {
            return false;
        }
        return takeoverStoreSessions.contains(ss);
    }

    public final void setStage_BEFORE_GET_LOCK() {
        stage = BEFORE_GET_LOCK;
    }
    public final void setStage_AFTER_GET_LOCK() {
        stage = AFTER_GET_LOCK;
    }
    public final void setStage_BEFORE_TAKE_STORE() {
        stage = BEFORE_TAKE_STORE;
    }
    public final void setStage_BEFORE_DB_SWITCH_OWNER() {
        substage = BEFORE_DB_SWITCH_OWNER;
    }
    public final void setStage_AFTER_DB_SWITCH_OWNER() {
        substage = AFTER_DB_SWITCH_OWNER;
    }
    public final void setStage_AFTER_TAKE_STORE() {
        stage = AFTER_TAKE_STORE;
    }
    public final void setStage_BEFORE_PROCESSING() {
        stage = BEFORE_PROCESSING;
    }
    public final void setStage_AFTER_PROCESSING() {
        stage = AFTER_PROCESSING;
    }

    public String toString() {
        return getTargetName()+"[StoreSession:"+
         (getStoreSessionUID() == null ? "":getStoreSessionUID())+"], "+
         (getBrokerSessionUID() == null ? "":getBrokerSessionUID())+
         ", "+lastHeartbeat+", ("+getStage()+")";
    }
}
