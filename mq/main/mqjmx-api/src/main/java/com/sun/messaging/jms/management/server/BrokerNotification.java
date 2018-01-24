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
 * @(#)BrokerNotification.java	1.8 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;
import java.lang.management.MemoryUsage;

/**
 * Class containing information on broker related notifications.
 * This notification is broadcasted from the relevant MBeans in a broker 
 * that is either:
 * <UL>
 * <LI>in the process of quiescing
 * <LI>in the process of shutting down
 * <LI>in the process of taking over another broker's persistence store
 * </UL>
 * 
 * With regards to the takeover related notifications, this notification is 
 * broadcasted by the broker that is performing the takeover operation, not the 
 * broker that is being taken over.
 */
public class BrokerNotification extends MQNotification  {
    /** 
     * A broker's memory level/state has changed
     */
    public static final String		BROKER_RESOURCE_STATE_CHANGE = MQNotification.PREFIX
						+ "broker.resource.state.change";

    /** 
     * A broker has finished quiescing.
     */
    public static final String		BROKER_QUIESCE_COMPLETE = MQNotification.PREFIX
						+ "broker.quiesce.complete";

    /** 
     * A broker has started to quiesce.
     */
    public static final String		BROKER_QUIESCE_START = MQNotification.PREFIX
						+ "broker.quiesce.start";

    /** 
     * A broker has started the process of shutting down.
     */
    public static final String		BROKER_SHUTDOWN_START = MQNotification.PREFIX
						+ "broker.shutdown.start";

    /** 
     * A broker has completed the takeover of another broker.
     */
    public static final String		BROKER_TAKEOVER_COMPLETE = MQNotification.PREFIX
						+ "broker.takeover.complete";

    /** 
     * A broker has failed in the attempt to takeover another broker.
     */
    public static final String		BROKER_TAKEOVER_FAIL = MQNotification.PREFIX
						+ "broker.takeover.fail";

    /** 
     * A broker has started to takeover another broker.
     */
    public static final String		BROKER_TAKEOVER_START = MQNotification.PREFIX
						+ "broker.takeover.start";

    private String brokerID, brokerAddress, failedBrokerID, oldResourceState, newResourceState;
    private MemoryUsage heapMemoryUsage;

    /**
     * Creates a BrokerNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public BrokerNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }

    /**
     * Sets the broker ID. Depending on the type of notification, this can be
     * the ID of the broker that is quiescing, shutting down, or the ID of the
     * broker that is taking over another broker's persistence store.
     *
     * @param brokerID	The broker ID.
     */
    public void setBrokerID(String brokerID)  {
	this.brokerID = brokerID;
    }

    /**
     * Returns the broker ID. Depending on the type of notification, this can be
     * the ID of the broker that is quiescing, shutting down, or the ID of the
     * broker that is taking over another broker's persistence store.
     *
     * @return The broker ID.
     */
    public String getBrokerID()  {
	return(brokerID);
    }

    /**
     * Sets the broker address. Depending on the type of notification, this can be
     * the address of the broker that is quiescing, shutting down, or the address 
     * of the broker that is taking over another broker's persistence store.
     *
     * @param brokerAddress	The broker address.
     */
    public void setBrokerAddress(String brokerAddress)  {
	this.brokerAddress = brokerAddress;
    }

    /**
     * Returns the broker address. Depending on the type of notification, this 
     * can be the address of the broker that is quiescing, shutting down, or the 
     * address of the broker that is taking over another broker's persistence store.
     *
     * @return The broker address.
     */
    public String getBrokerAddress()  {
	return(brokerAddress);
    }

    /**
     * Sets the ID of the broker in the cluster that failed and is in the
     * process of being taken over.
     *
     * @param failedBrokerID	Sets the ID of the broker in the cluster 
     *				that failed and is in the process of being 
     *				taken over.
     */
    public void setFailedBrokerID(String failedBrokerID)  {
	this.failedBrokerID = failedBrokerID;
    }

    /**
     * Returns the ID of the broker in the cluster that failed and is in the
     * process of being taken over.
     *
     * @return	Sets the ID of the broker in the cluster 
     *		that failed and is in the process of being 
     *		taken over.
     */
    public String getFailedBrokerID()  {
	return(failedBrokerID);
    }

    public void setOldResourceState(String oldResourceState)  {
	this.oldResourceState = oldResourceState;
    }

    public String getOldResourceState()  {
	return(oldResourceState);
    }

    public void setNewResourceState(String newResourceState)  {
	this.newResourceState = newResourceState;
    }

    public String getNewResourceState()  {
	return(newResourceState);
    }

    public void setHeapMemoryUsage(MemoryUsage heapMemoryUsage)  {
	this.heapMemoryUsage = heapMemoryUsage;
    }

    public MemoryUsage getHeapMemoryUsage()  {
	return(heapMemoryUsage);
    }
}
