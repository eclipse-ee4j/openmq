/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.cluster.manager;

/**
 * Represents a Reason passed into broker changed
 */
public enum ClusterReason {
    /**
     * A broker has been added to the cluster.
     */
    ADDED,

    /**
     * A broker has been removed from the cluster.
     */
    REMOVED,

    /**
     * The status of a broker has changed.
     *
     * @see BrokerStatus
     */
    STATUS_CHANGED,

    /**
     * The state of a broker has changed.
     *
     * @see BrokerState
     */
    STATE_CHANGED,

    /**
     * The protocol version of a broker has changed.
     */
    VERSION_CHANGED,

    /**
     * The portmapper address of a broker has changed.
     */
    ADDRESS_CHANGED,

    /**
     * The address of the master broker in the cluster has changed.
     */
    MASTER_BROKER_CHANGED;

    /**
     * a string representation of the object
     */
    @Override
    public String toString() {
        return "ClusterReason[" + name() + "]";
    }
}
