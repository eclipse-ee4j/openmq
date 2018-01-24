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
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.manager.ha;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.cluster.manager.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStoreUtil;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.TakingoverTracker;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverStoreInfo;

/**
 */
public class RepHAClusteredBrokerImpl extends ClusteredBrokerImpl
implements HAClusteredBroker 
{
    UID storeSession = null;

    public RepHAClusteredBrokerImpl(RepHAClusterManagerImpl parent,
                                    MQAddress url, boolean local, UID id) {
        super(parent, url, local, id);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public String toString() {
        if (!isLocalBroker()) {
            return "-"+getInstanceName()+getBrokerURL() + ":" + getState() +
                "[StoreSession:" + storeSession + ", BrokerSession:"+getBrokerSessionUID()+"]"+  ":"+
                 BrokerStatus.toString(getStatus());
        }
        return "*" +getInstanceName() + "@" + getBrokerURL() + ":" + getState() + 
                "[StoreSession:" + storeSession + ", BrokerSession:"+getBrokerSessionUID()+"]"+  ":"+
                 BrokerStatus.toString( getStatus());
                   
    }

    /**
     * Gets the UID associated with the store session.
     *
     * @return the store session uid (if known)
     */
    public synchronized UID getStoreSessionUID() {
            return storeSession;
    }

    public synchronized void setStoreSessionUID(UID uid) {
            storeSession = uid;
    }

    public synchronized String getNodeName() throws BrokerException {
        String instn = getInstanceName();
        UID storeSession = getStoreSessionUID();
        return MigratableStoreUtil.makeEffectiveBrokerID(instn, storeSession);
    }

    /**
     * Retrieves the id of the broker who has taken over this broker's store.
     *
     * @return the broker id of the takeover broker (or null if there is not
     *      a takeover broker).
     */
    public String getTakeoverBroker() throws BrokerException {
        return null;
    }

    public long getHeartbeat() throws BrokerException {
        return 0L;
    }
 
    public long updateHeartbeat() throws BrokerException {
        throw new BrokerException("Operation not supported");
    }

    public long updateHeartbeat(boolean reset) throws BrokerException {
        throw new BrokerException("Operation not supported");
    }

    /**
     * Attempt to take over the persistent state of the broker.
     * 
     * @param force force the takeover
     * @param tracker for tracking takingover stages
     * @throws IllegalStateException if this broker can not takeover.
     * @return data associated with previous broker
     */
    public TakeoverStoreInfo takeover(boolean force,
                                      Object extraInfo,
                                      TakingoverTracker tracker)
                                      throws BrokerException {

        String targetRepHostPort = (String)extraInfo;
        Store store = Globals.getStore(); 
        TakeoverStoreInfo o = store.takeoverBrokerStore(getInstanceName(),
                              tracker.getDownStoreSessionUID(), targetRepHostPort, tracker);
        ((RepHAClusterManagerImpl)parent).addSupportedStoreSessionUID(
             tracker.getStoreSessionUID());
        return o;
    }

    /**
     * Remove takeover broker ID and set state to OPERATING
     *
     * @throws Exception if operation fails
     */
    public void resetTakeoverBrokerReadyOperating() throws Exception {
        throw new BrokerException("Operation not supported");
    }

    /**
     * Set another broker's state to FAILOVER_PROCESSED if same store session
     *
     * @param storeSession the store session that the failover processed
     * @throws Exception if operation fails
     */
    public void setStateFailoverProcessed(UID storeSession) throws Exception {
    }

    /**
     * Set another broker's state to FAILOVER_FAILED if same broker session
     *
     * @param brokerSession the broker session that the failover failed
     * @throws Exception if operation fails
     */
    public void setStateFailoverFailed(UID brokerSession) throws Exception {
    }
}
