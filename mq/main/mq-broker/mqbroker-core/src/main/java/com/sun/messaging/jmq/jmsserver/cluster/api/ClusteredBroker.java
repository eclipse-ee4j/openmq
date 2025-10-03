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
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.UID;

/**
 * represents an instance of a broker in a cluster
 */
public interface ClusteredBroker {
    /**
     * A unique identifier assigned to the broker (randomly assigned).
     * <P>
     *
     * This name is only unique to this broker. The broker at this URL may be assigned a different name on another broker in
     * the cluster.
     *
     * @return the name of the broker
     */
    String getBrokerName();

    /**
     * Returns the URL to the portmapper of this broker.
     *
     * @return the URL of this broker
     */
    MQAddress getBrokerURL();

    /**
     * @return the instance name of this broker, null if not available
     */
    String getInstanceName();

    /**
     *
     * @param instName the instance name of this broker, can be null
     */
    void setInstanceName(String instName);

    /**
     * Sets the URL to the portmapper of this broker
     *
     * @param addr the URL of this broker
     */
    void setBrokerURL(MQAddress addr) throws Exception;

    /**
     * Returns if this is the address of the broker running in this VM.
     *
     * @return true if this is the broker running in the current vm
     */
    boolean isLocalBroker();

    /**
     * Retrieves the status of the broker.
     *
     * @return the status of the broker
     */
    int getStatus();

    /**
     * Gets the protocol version of the broker.
     *
     * @return the protocol version (if known) or 0 if not known.
     */
    int getVersion();

    /**
     * Sets the protocol version of the broker.
     *
     * @param version the protocol version
     * @throws UnsupportedOperationException if the version can not be set for this broker
     */
    void setVersion(int version) throws Exception;

    /**
     * Sets the status of the broker. Do not hold locks while calling this routine.
     *
     * @param status the broker status to set for this broker
     * @param userData optional data associated with the change
     */
    void setStatus(int status, Object userData);

    /**
     * Updates the BROKER_UP bit flag on status.
     *
     * @param up setting for the bit flag (true/false)
     * @param userData optional data associated with the change
     */
    void setBrokerIsUp(boolean up, UID brokerSession, Object userData);

    /**
     * Updates the BROKER_LINK_UP bit flag on status.
     *
     * @param up setting for the bit flag (true/false)
     * @param userData optional data associated with the change
     */
    void setBrokerLinkUp(boolean up, Object userData);

    /**
     * Updates the BROKER_INDOUBT bit flag on status.
     *
     * @param indoubt setting for the bit flag (true/false)
     * @param userData optional data associated with the change
     */
    void setBrokerInDoubt(boolean indoubt, Object userData);

    /**
     * Destroys the ClusteredBroker.
     */
    void destroy();

    /**
     * Gets the state of the broker.
     *
     * @return the broker state
     * @throws BrokerException if the state can not be retrieved
     */
    BrokerState getState() throws BrokerException;

    /**
     * Sets the state of the broker. * @throws IllegalAccessException if the broker does not have permission to change the
     * broker (e.g. one broker is updating anothers state).
     *
     * @throws IllegalStateException if the broker state changed unexpectedly.
     * @throws IllegalArgumentException if the state is not supported for this cluster type.
     * @param state the new broker state
     */
    void setState(BrokerState state) throws IllegalAccessException;

    /**
     * Is the broker static or dynmically configured
     */
    boolean isConfigBroker();

    /**
     * equals method
     */
    @Override
    boolean equals(Object o);

    /**
     * hashcode method
     */
    @Override
    int hashCode();

    /**
     * Gets the UID associated with the broker session.
     *
     * @return the broker session uid (if known)
     */
    UID getBrokerSessionUID();

    /**
     * Sets the UID associated with the broker session.
     *
     * @param uid the new broker session uid
     */
    void setBrokerSessionUID(UID uid);

    /**
     * returns if the brokerID was generated.
     *
     * @return true if the ID was generated
     */
    boolean isBrokerIDGenerated();

    /**
     * used by replicated BDB
     */
    String getNodeName() throws BrokerException;

}
