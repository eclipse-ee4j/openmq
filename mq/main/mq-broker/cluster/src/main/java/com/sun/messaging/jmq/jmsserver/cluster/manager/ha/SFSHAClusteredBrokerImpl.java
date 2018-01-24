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
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStoreUtil;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverStoreInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.cluster.manager.*;

/**
 */
public class SFSHAClusteredBrokerImpl extends HAClusteredBrokerImpl
{
    public SFSHAClusteredBrokerImpl(String brokerid, HABrokerInfo m, 
                                    SFSHAClusterManagerImpl parent)
                                    throws BrokerException {
        this.parent = parent;
        this.brokerid = brokerid;
        this.status = Integer.valueOf(BrokerStatus.BROKER_UNKNOWN);
        String urlstr = m.getUrl();
        if (urlstr != null) {
            try {
                address = BrokerMQAddress.createAddress(urlstr);
            } catch (Exception ex) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "invalid URL stored on disk " + urlstr, ex));
            }
        }
        version = parent.VERSION;
        state = BrokerState.INITIALIZING;
        session = null;
        takeoverBroker = null;
        heartbeat = 0L;
    }

    /**
     * Create a <i>new</i> instace of HAClusteredBrokerImpl  and
     * stores it into the database.
     *
     * @param brokerid is the id associated with this broker
     * @param url is the portmapper address
     * @param version is the cluster version of the broker
     * @param state is the current state of the broker
     * @param session is this broker's current store session.
     * @throws BrokerException if something is wrong during creation
     */
    public SFSHAClusteredBrokerImpl(String brokerid,
                 MQAddress url, int version, BrokerState state,
                 UID session, HAClusterManagerImpl parent)
                 throws BrokerException {
        super(brokerid, url, version, state, session, parent);
    }

    public void update(HABrokerInfo m) {
        MQAddress oldaddr = address;
        synchronized (this) {
            this.brokerid = m.getId();
            String urlstr = m.getUrl();
            try {
                address = BrokerMQAddress.createAddress(urlstr);
            } catch (Exception ex) {
                logger.logStack(logger.WARNING, ex.getMessage(), ex);
                address = oldaddr;
            }
        }
        if (!oldaddr.equals(address)) {
            parent.brokerChanged(ClusterReason.ADDRESS_CHANGED,
                this.getBrokerName(), oldaddr, this.address, null, null);
        }
    }

    public void resetTakeoverBrokerReadyOperating() throws Exception {
        setState(BrokerState.OPERATING);
    }

    /**
     * Retrieves the id of the broker who has taken over this broker's store.
     *
     * @return the broker id of the takeover broker (or null if there is not
     *      a takeover broker).
     */
    public synchronized String getTakeoverBroker() throws BrokerException {
        return null;
    }

    public long getHeartbeat() throws BrokerException {
        return 0L;
    }

    public synchronized long updateHeartbeat() throws BrokerException {
        return updateHeartbeat(false);
    }

    public long updateHeartbeat(boolean reset) throws BrokerException {
        Globals.getStore().updateBrokerHeartbeat(brokerid);
        return 0L;
    }

    protected synchronized UID updateEntry(int updateType,
                               Object oldValue, Object newValue)
                               throws Exception {
        if (!local) {
            throw new IllegalAccessException(
            "Can not update entry " + " for broker " + brokerid);
        }

        Store store = Globals.getStore();
        store.updateBrokerInfo(brokerid, updateType, oldValue, newValue);
        return session;
    }
 
    /**
     * Called 
     * at cluster init for local broker;
     * at link activate for remote broker
     */
    public synchronized void setStoreSessionUID(UID uid) {
        session = uid;
    }

    /**
     * gets the state of the broker .
     * <b>Note:</b> the state is always retrieved from the store
     * before it is returned (so its current).
     *
     *
     * @throws BrokerException if the state can not be retrieve
     * @return the current state
     */
     public BrokerState getState() throws BrokerException
     {
         //XXX 
         return state;
     }

     public void setStateFailoverProcessed(UID storeSession) throws Exception {
         //no-op
     }

     public void setStateFailoverFailed(UID brokerSession) throws Exception {
         //no-op
     }

     public void setRemoteBrokerStateOnActivation() {
         if (local) {
             throw new RuntimeException(
             "setRemoteBrokerStateOnActivation: unexpected call to local broker: " +this);
         }
         state = BrokerState.OPERATING;
     }

     public void setRemoteBrokerStateOnDeactivation() {
         if (local) {
             throw new RuntimeException(
             "setRemoteBrokerStateOnDeactivation: unexpected call to local broker: " +this);
         }
         state = BrokerState.INITIALIZING;
     }

     public void setState(BrokerState newstate)
         throws IllegalAccessException, IllegalStateException,
         IndexOutOfBoundsException {
         if (!local) {
             //no-op
         }
         try {
              BrokerState oldState = getState();
              if (newstate != BrokerState.FAILOVER_PENDING
                  && newstate != BrokerState.FAILOVER_PROCESSED
                  && newstate != BrokerState.FAILOVER_FAILED) {
                  if (!Globals.getStore().updateBrokerState(brokerid, newstate, state, local))
                      throw new IllegalStateException(
                      "Could not update broker state from "+oldState+" to state "+newstate+ " for " + brokerid);
                  }
                  state = newstate;
                  parent.brokerChanged(ClusterReason.STATE_CHANGED,
                      this.getBrokerName(), oldState, this.state, null,  null);
         } catch (BrokerException ex) {
             IllegalStateException e =
                 new IllegalStateException(
                        "Failed to update state for " + brokerid);
                 e.initCause(ex);
                 throw e;
         }
     }

    /**
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

        Store store = Globals.getStore(); 
        BrokerState curstate = getState();
        store.getTakeOverLock(parent.getLocalBrokerName(),
                              brokerid, 0L, curstate, 0L, null,
                              force, tracker);
        TakeoverStoreInfo o =  store.takeoverBrokerStore(brokerid, 
                               tracker.getStoreSessionUID(), null, tracker);
        parent.addSupportedStoreSessionUID(tracker.getStoreSessionUID());
        return o;
    }

    public synchronized String getNodeName() throws BrokerException {
        String instn = getInstanceName();
        UID storeSession = getStoreSessionUID();
        return MigratableStoreUtil.makeEffectiveBrokerID(instn, storeSession);
    }
}
