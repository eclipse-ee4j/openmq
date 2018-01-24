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
 * @(#)ClusterListener.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.MQAddress;

/**
 * A listener on changes to ClusterManager.
 *
 * The listener has a method for cluster configuration changes and a seperate method
 * for cluster state changes.
 * <P>
 * 
 * @see ClusterManager
 */
public interface ClusterListener extends java.util.EventListener
{

   /**
    * Called to notify ClusterListeners when the cluster service
    * configuration. Configuration changes include:
    * <UL><LI>cluster service port</LI>
    *     <LI>cluster service hostname</LI>
    *     <LI>cluster service transport</LI>
    * </UL><P>
    *
    * @param name the name of the changed property
    * @param value the new value of the changed property
    */
    public void clusterPropertyChanged(String name, String value);


   /**
    * Called when a new broker has been added.
    * @param brokerSession uid associated with the added broker
    * @param broker the new broker added.
    */
    public void brokerAdded(ClusteredBroker broker, UID brokerSession);

   /**
    * Called when a broker has been removed.
    * @param brokerSession uid associated with the removed broker
    * @param broker the broker removed.
    */
    public void brokerRemoved(ClusteredBroker broker, UID brokerSession);

   /**
    * Called when the broker who is the master broker changes
    * (because of a reload properties).
    * @param oldMaster the previous master broker.
    * @param newMaster the new master broker.
    */
    public void masterBrokerChanged(ClusteredBroker oldMaster,
                    ClusteredBroker newMaster);

   /**
    * Called when the status of a broker has changed. The
    * status may not be accurate if a previous listener updated
    * the status for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldStatus the previous status.
    * @param newStatus the new status.
    * @param brokerSession uid associated with the changed broker
    * @param userData data associated with the state change
    */
    public void brokerStatusChanged(String brokerid,
                  int oldStatus, int newStatus, UID uid, Object userData);

   /**
    * Called when the state of a broker has changed. The
    * state may not be accurate if a previous listener updated
    * the state for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldState the previous state.
    * @param newState the new state.
    */
    public void brokerStateChanged(String brokerid,
                  BrokerState oldState, BrokerState newState);

   /**
    * Called when the version of a broker has changed. The
    * state may not be accurate if a previous listener updated
    * the version for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldVersion the previous version.
    * @param newVersion the new version.
    */
    public void brokerVersionChanged(String brokerid,
                  int oldVersion, int newVersion);

   /**
    * Called when the url address of a broker has changed. The
    * address may not be accurate if a previous listener updated
    * the address for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param newAddress the previous address.
    * @param oldAddress the new address.
    */
    public void brokerURLChanged(String brokerid,
                  MQAddress oldAddress, MQAddress newAddress);



} 

