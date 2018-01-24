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
 * @(#)HAClusteredBroker.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api.ha;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverStoreInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;



/**
 * Subclass of ClusteredBroker which contains HA specific information.
 */
public interface HAClusteredBroker extends ClusteredBroker
{
    /**
     * The brokerid assigned to the broker. <P>
     *
     * The name is unique to the cluster (and overrides
     * the superclass implementation).
     *
     * @return the name of the broker
     */
    public String getBrokerName(); 

    /**
     * Gets the UID associated with the store session.
     *
     * @return the store session uid (if known)
     */
    public UID getStoreSessionUID();

    /**
     * Retrieves the id of the broker who has taken over this broker's store.
     *
     * @return the broker id of the takeover broker (or null if there is not
     *      a takeover broker).
     */
    public String getTakeoverBroker()
            throws BrokerException;

    /**
     * Returns the heartbeat timestamp associated with this broker.
     *
     * @return the heartbeat in milliseconds
     * @throws BrokerException if the heartbeat can not be retrieve.
     */
    public long getHeartbeat()
            throws BrokerException;
 

    /**
     * Update the timestamp associated with this broker.
     * @return the updated heartbeat in milliseconds
     * @throws BrokerException if the heartbeat can not be set or retrieve.
     */
    public long updateHeartbeat() throws BrokerException;

    /**
     * Update the timestamp associated with this broker.
     *
     * @param reset update heartbeat without check state
     * @return the updated heartbeat in milliseconds
     * @throws BrokerException if the heartbeat can not be set or retrieve.
     */
    public long updateHeartbeat(boolean reset) throws BrokerException;

    /**
     * Attempt to take over the persistent state of the broker.
     * 
     * @param force force the takeover
     * @param tracker for tracking takingover stages
     * @throws IllegalStateException if this broker can not takeover.
     * @return data associated with previous broker
     */
    public TakeoverStoreInfo takeover(boolean force, Object extraInfo,
                                     TakingoverTracker tracker)
                                     throws BrokerException;

    /**
     * Remove takeover broker ID and set state to OPERATING
     *
     * @throws Exception if operation fails
     */
    public void resetTakeoverBrokerReadyOperating() throws Exception;

    /**
     * Set another broker's state to FAILOVER_PROCESSED if same store session
     *
     * @param storeSession the store session that the failover processed
     * @throws Exception if operation fails
     */
    public void setStateFailoverProcessed(UID storeSession) throws Exception;

    /**
     * Set another broker's state to FAILOVER_FAILED if same broker session
     *
     * @param brokerSession the broker session that the failover failed
     * @throws Exception if operation fails
     */
    public void setStateFailoverFailed(UID brokerSession) throws Exception;
  
}
