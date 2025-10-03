/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.cluster.api;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class NoClusteredBroker implements ClusteredBroker {

    private Logger logger = Globals.getLogger();
    // private BrokerResources br = Globals.getBrokerResources();

    /**
     * Name associated with this broker. For non-ha clusters it is of the form broker# and is not the same across all
     * brokers in the cluster (although it is unique on this broker).
     */
    String brokerName = null;

    /**
     * The portmapper for this broker.
     */
    MQAddress address = null;

    /**
     * The instance name of this broker
     */
    String instanceName = null;

    /**
     * Current status of the broker.
     */
    Integer status = Integer.valueOf(BrokerStatus.BROKER_UNKNOWN);

    /**
     * Current state of the broker.
     */
    BrokerState state = BrokerState.INITIALIZING;

    /**
     * Broker SessionUID for this broker. This uid changes on each restart of the broker.
     */
    UID brokerSessionUID = null;

    /**
     * has brokerID been generated
     */
    boolean isgen = false;

    /**
     * Create a instace of ClusteredBroker.
     *
     * @param url the portmapper address of this broker
     */
    public NoClusteredBroker(MQAddress url, UID id) {
        this.address = url;
        brokerSessionUID = id;
        brokerName = Globals.getBrokerID();
        instanceName = Globals.getConfigName();
        if (brokerName == null) {
            isgen = true;
            brokerName = "broker0";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClusteredBroker)) {
            return false;
        }
        return this.getBrokerName().equals(((ClusteredBroker) o).getBrokerName());
    }

    @Override
    public int hashCode() {
        return this.getBrokerName().hashCode();
    }

    /**
     * String representation of this broker.
     */
    @Override
    public String toString() {
        return brokerName + "* (" + address + ")";
    }

    /**
     * a unique identifier assigned to the broker (randomly assigned).
     * <P>
     *
     * This name is only unique to this broker. The broker at this URL may be assigned a different name on another broker in
     * the cluster.
     *
     * @return the name of the broker
     */
    @Override
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * the URL to the portmapper of this broker.
     *
     * @return the URL of this broker
     */
    @Override
    public MQAddress getBrokerURL() {
        return address;
    }

    /**
     * @return the instance name of this broker, null if not available
     */
    @Override
    public String getInstanceName() {
        return instanceName;
    }

    /**
     * @param instName Set the instance name of this broker, can be null
     */
    @Override
    public void setInstanceName(String instName) {
        instanceName = instName;
    }

    /**
     * sets the URL to the portmapper of this broker.
     *
     * @param address the URL of this broker
     * @throws UnsupportedOperationException if this change can not be made on this broker
     */
    @Override
    public void setBrokerURL(MQAddress address) throws Exception {
        this.address = address;
    }

    @Override
    public boolean isLocalBroker() {
        return true;
    }

    /**
     * gets the status of the broker.
     *
     * @see BrokerStatus
     * @return the status of the broker
     */
    @Override
    public synchronized int getStatus() {
        return status.intValue();
    }

    /**
     * gets the protocol version of the broker .
     *
     * @return the current cluster protocol version (if known) or 0 if not known
     */
    @Override
    public synchronized int getVersion() {
        return 0;
    }

    @Override
    public void setVersion(int version) throws Exception {
    }

    /**
     * sets the status of the broker (and notifies listeners).
     *
     * @param newstatus the status to set
     * @param userData optional user data associated with the status change
     * @see ConfigListener
     */
    @Override
    public void setStatus(int newstatus, Object userData) {

        // ok - for standalone case, adjust so that LINK_DOWN=DOWN
        if (BrokerStatus.getBrokerIsDown(newstatus)) {
            newstatus = BrokerStatus.setBrokerLinkIsDown(newstatus);
        } else if (BrokerStatus.getBrokerLinkIsDown(newstatus)) {
            newstatus = BrokerStatus.setBrokerIsDown(newstatus);
        } else if (BrokerStatus.getBrokerLinkIsUp(newstatus)) {
            newstatus = BrokerStatus.setBrokerIsUp(newstatus);
        } else if (BrokerStatus.getBrokerIsUp(newstatus)) {
            newstatus = BrokerStatus.setBrokerLinkIsUp(newstatus);
        }

        synchronized (this) {
            this.status = Integer.valueOf(newstatus);
        }
        try {
            if (BrokerStatus.getBrokerIsUp(newstatus)) {
                setState(BrokerState.OPERATING);
            }
            if (BrokerStatus.getBrokerIsDown(newstatus)) {
                setState(BrokerState.SHUTDOWN_COMPLETE);
            }
        } catch (Exception ex) {
            logger.logStack(Logger.DEBUG, "Error setting state ", ex);
        }

    }

    /**
     * Updates the BROKER_UP bit flag on status.
     *
     * @param userData optional user data associated with the status change
     * @param up setting for the bit flag (true/false)
     */
    @Override
    public void setBrokerIsUp(boolean up, UID brokerSession, Object userData) {
        synchronized (this) {
            if (up) {
                int newStatus = BrokerStatus.setBrokerIsUp(this.status.intValue());
                status = Integer.valueOf(newStatus);
            } else {
                int newStatus = BrokerStatus.setBrokerIsDown(this.status.intValue());
                status = Integer.valueOf(newStatus);
            }
        }
        try {
            if (up) {
                setState(BrokerState.OPERATING);
            } else {
                setState(BrokerState.SHUTDOWN_COMPLETE);
            }
        } catch (Exception ex) {
            logger.logStack(Logger.DEBUG, "Error setting state ", ex);
        }
    }

    /**
     * Updates the BROKER_LINK_UP bit flag on status.
     *
     * @param userData optional user data associated with the status change
     * @param up setting for the bit flag (true/false)
     */
    @Override
    public void setBrokerLinkUp(boolean up, Object userData) {
        synchronized (this) {
            int newStatus = 0;
            if (up) {
                newStatus = BrokerStatus.setBrokerLinkIsUp(BrokerStatus.setBrokerIsUp(this.status.intValue()));
            } else {
                newStatus = BrokerStatus.setBrokerLinkIsDown(BrokerStatus.setBrokerIsDown(this.status.intValue()));
            }
            this.status = Integer.valueOf(newStatus);
        }
        try {
            if (up) {
                setState(BrokerState.OPERATING);
            } else {
                setState(BrokerState.SHUTDOWN_COMPLETE);
            }
        } catch (Exception ex) {
            logger.logStack(Logger.DEBUG, "Error setting state ", ex);
        }

    }

    /**
     * Updates the BROKER_INDOUBT bit flag on status.
     *
     * @param userData optional user data associated with the status change
     * @param up setting for the bit flag (true/false)
     */
    @Override
    public void setBrokerInDoubt(boolean up, Object userData) {
        throw new UnsupportedOperationException("Unexpected call: " + getClass().getName() + ".setBrokerInDoubt()");
    }

    /**
     * marks this broker as destroyed. This is equivalent to setting the status of the broker to DOWN.
     *
     * @see BrokerStatus#DOWN
     */
    @Override
    public void destroy() {
        synchronized (this) {
            status = Integer.valueOf(BrokerStatus.setBrokerIsDown(status.intValue()));
        }
    }

    /**
     * gets the state of the broker .
     *
     * @return the current state
     */
    @Override
    public BrokerState getState() {
        return state;
    }

    /**
     * sets the state of the broker (and notifies any listeners).
     *
     * @throws IllegalAccessException if the broker does not have permission to change the broker (e.g. one broker is
     * updating anothers state).
     * @throws IllegalStateException if the broker state changed unexpectedly.
     * @throws IllegalArgumentException if the state is not supported for this cluster type.
     * @param state the state to set for this broker
     * @see ConfigListener
     */
    @Override
    public void setState(BrokerState state) throws IllegalAccessException {
        this.state = state;
    }

    /**
     * Is the broker static or dynmically configured
     */
    @Override
    public boolean isConfigBroker() {
        return true;
    }

    @Override
    public synchronized UID getBrokerSessionUID() {
        return brokerSessionUID;
    }

    @Override
    public synchronized void setBrokerSessionUID(UID session) {
        brokerSessionUID = session;
    }

    @Override
    public boolean isBrokerIDGenerated() {
        return isgen;
    }

    @Override
    public String getNodeName() throws BrokerException {
        throw new UnsupportedOperationException("Unexpected call: " + getClass().getName() + ".getNodeName()");
    }
}
