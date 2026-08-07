/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.auth.jaas;

import java.security.Permission;

public interface PermissionFactory {

    String DEST_RESOURCE_PREFIX = "mq-dest::";
    String CONN_RESOURCE_PREFIX = "mq-conn::";
    String AUTO_RESOURCE_PREFIX = "mq-auto::";

    String CONN_NORMAL = "NORMAL";
    String CONN_ADMIN = "ADMIN";

    String DEST_QUEUE = "queue";
    String DEST_TOPIC = "topic";

    String DEST_QUEUE_PREFIX = "queue:";
    String DEST_TOPIC_PREFIX = "topic:";
    String ACTION_PRODUCE = "produce";
    String ACTION_CONSUME = "consume";
    String ACTION_BROWSE = "browse";

    /**
     *
     * @param resourceName The name of
     * the protected resource to access @param actions A comma separated list of allowable actions on the resource
     *
     * @return a java.security.Permission object
     *
     */
    Permission newPermission(String resourceName, String actions);
}
