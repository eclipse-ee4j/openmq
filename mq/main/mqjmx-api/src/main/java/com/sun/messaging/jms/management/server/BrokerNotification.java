/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.management.server;

import lombok.Getter;
import lombok.Setter;
import java.lang.management.MemoryUsage;

/**
 * Class containing information on broker related notifications. This notification is broadcasted from the relevant
 * MBeans in a broker that is either:
 * <UL>
 * <LI>in the process of quiescing
 * <LI>in the process of shutting down
 * <LI>in the process of taking over another broker's persistence store
 * </UL>
 *
 * With regards to the takeover related notifications, this notification is broadcasted by the broker that is performing
 * the takeover operation, not the broker that is being taken over.
 */
public class BrokerNotification extends MQNotification {
    private static final long serialVersionUID = 369199942794379731L;

    /**
     * A broker's memory level/state has changed
     */
    public static final String BROKER_RESOURCE_STATE_CHANGE = MQNotification.PREFIX + "broker.resource.state.change";

    /**
     * A broker has finished quiescing.
     */
    public static final String BROKER_QUIESCE_COMPLETE = MQNotification.PREFIX + "broker.quiesce.complete";

    /**
     * A broker has started to quiesce.
     */
    public static final String BROKER_QUIESCE_START = MQNotification.PREFIX + "broker.quiesce.start";

    /**
     * A broker has started the process of shutting down.
     */
    public static final String BROKER_SHUTDOWN_START = MQNotification.PREFIX + "broker.shutdown.start";

    /**
     * A broker has completed the takeover of another broker.
     */
    public static final String BROKER_TAKEOVER_COMPLETE = MQNotification.PREFIX + "broker.takeover.complete";

    /**
     * A broker has failed in the attempt to takeover another broker.
     */
    public static final String BROKER_TAKEOVER_FAIL = MQNotification.PREFIX + "broker.takeover.fail";

    /**
     * A broker has started to takeover another broker.
     */
    public static final String BROKER_TAKEOVER_START = MQNotification.PREFIX + "broker.takeover.start";

    /**
    * Depending on the type of notification, this can be the ID of the broker that is quiescing,
    * shutting down, or the ID of the broker that is taking over another broker's persistence store.
    */
    @Getter
    @Setter
    private String brokerID;

    /**
    * Depending on the type of notification, this can be the address of the broker that is
    * quiescing, shutting down, or the address of the broker that is taking over another broker's persistence store.
    */
    @Getter
    @Setter
    private String brokerAddress;

    /**
     * The ID of the broker in the cluster that failed and is in the process of being taken over.
     */
    @Getter
    @Setter
    private String failedBrokerID;

    @Getter
    @Setter
    private String oldResourceState;

    @Getter
    @Setter
    private String newResourceState;

    @Getter
    @Setter
    private MemoryUsage heapMemoryUsage;

    /**
     * Creates a BrokerNotification object.
     *
     * @param type The notification type.
     * @param source The notification source.
     * @param sequenceNumber The notification sequence number within the source object.
     */
    public BrokerNotification(String type, Object source, long sequenceNumber) {
        super(type, source, sequenceNumber);
    }
}
