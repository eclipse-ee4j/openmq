/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * Class containing information on cluster related notifications.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class ClusterNotification extends MQNotification {
    @Serial
    private static final long serialVersionUID = 8569120463426095123L;
    /**
     * A broker in the cluster has gone down
     */
    public static final String CLUSTER_BROKER_DOWN = MQNotification.PREFIX + "cluster.broker.down";
    /**
     * A broker joined the cluster.
     */
    public static final String CLUSTER_BROKER_JOIN = MQNotification.PREFIX + "cluster.broker.join";

    @Getter
    @Setter
    private String brokerID;

    @Getter
    @Setter
    private String brokerAddress;

    @Getter
    @Setter
    private String clusterID;

    @Getter
    @Setter
    private boolean highlyAvailable;

    @Getter
    @Setter
    private boolean isMasterBroker;

    /**
     * Creates a ClusterNotification object.
     *
     * @param type The notification type.
     * @param source The notification source.
     * @param sequenceNumber The notification sequence number within the source object.
     */
    public ClusterNotification(String type, Object source, long sequenceNumber) {
        super(type, source, sequenceNumber);
    }
}
