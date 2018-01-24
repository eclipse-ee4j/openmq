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
 * @(#)TakeoverStoreInfo.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import java.util.List;
import java.util.Date;
import java.util.Map;

/**
 * This immutable object encapsulates general information about
 * the store info of the broker that is being taken over.
 */
public final class TakeoverStoreInfo {

    private String targetName = null;      // Broker that is being taken over
    private List dstList = null;           // Local destination this broker owns
    private Map msgMap = null;             // Msg IDs and corresponding dst IDs this broker owns
    private List txnList = null;           // Transaction this broker owns
    private List remoteTxnList = null;     // Remote txn this broker participates in
    private long lockAcquiredTime = 0L;    // Timestamp when takeover lock is acquired
    private HABrokerInfo savedInfo = null; // Saved state of the broker being taken over
    private List<Long> takeoverStoreSessions = null;    

    /**
     * Constructor
     * @param bkrID the Broker ID that is being taken over
     * @param bkrInfo the saved state of the broker
     * @param ts timestamp when the takeover lock is acquired
     */
    public TakeoverStoreInfo( String bkrID, HABrokerInfo bkrInfo, long ts ) {

        lockAcquiredTime = ts;
        targetName = bkrID;
        savedInfo = bkrInfo;
    }

    public TakeoverStoreInfo(String targetName, long takeoverStartTime) {

        this.lockAcquiredTime = takeoverStartTime;
        this.targetName = targetName;
    }

    /**
     * Return the target name that is being taken over.
     * @return the targetName that is being taken over
     */
    public final String getTargetName() {
        return targetName;
    }

    /**
     * Returns the timestamp when the takeover lock was acquired.
     * @return timestamp when the lock is acquired
     */
    public final long getLockAcquiredTime() {
        return lockAcquiredTime;
    }

    /**
     * Retrieve all local destinations that this broker previously owns.
     * @return a List of all local destinations that this broker owns
     */
    public final List getDestinationList() {
        return dstList;
    }

    /**
     * Retrieve all message IDs and corresponding destination IDs that this broker previously owns.
     * @return a Map of message IDs and corresponding destination IDs
     */
    public final Map getMessageMap() {
        return msgMap;
    }

    /**
     * Retrieve all transactions that this broker previously owns.
     * @return a List of all transactions that this broker owns
     */
    public final List getTransactionList() {
        return txnList;
    }

    /**
     * Retrieve all remote transactions that this broker previously participates in.
     * @return a List of all transactions that this broker owns
     */
    public final List getRemoteTransactionList() {
        return remoteTxnList;
    }

    /**
     * Retrieve the saved state of broker.
     */
    public final HABrokerInfo getSavedBrokerInfo() {
        return savedInfo;
    }

    /**
     * Set local destinations that this broker previously owns.
     * @param list local destination this broker owns
     */
    public final void setDestinationList( List list ) {
        dstList = list;
    }

    /**
     * Set the transactions that this broker previously owns.
     * @param list transaction this broker owns
     */
    public final void setTransactionList( List list ) {
        txnList = list;
    }

    /**
     * Set the remote transactions that this broker previously participates in.
     * @param list transaction this broker owns
     */
    public final void setRemoteTransactionList( List list ) {
        remoteTxnList = list;
    }

    /**
     * Set message IDs and corresponding destination IDs that this broker previously owns.
     * @param map a Map message IDs and corresponding destination IDs
     */
    public final void setMessageMap( Map map ) {
        msgMap = map;
    }

    public final void setTakeoverStoreSessionList( List<Long> list ) {
        takeoverStoreSessions = list;
    }

    public final List<Long> getTakeoverStoreSessionList() {
        return takeoverStoreSessions;
    }
}
