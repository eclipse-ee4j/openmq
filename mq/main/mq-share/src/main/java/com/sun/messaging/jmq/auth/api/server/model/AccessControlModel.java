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

package com.sun.messaging.jmq.auth.api.server.model;

import java.util.Properties;
import java.security.Principal;
import java.security.AccessControlException;
import javax.security.auth.Subject;

/**
 * An AccessControlModel contains access controls which guards access JMQ resources (connections, destinations)
 */

public interface AccessControlModel {
    /**
     * This method is called immediately after this AccessControlModel has been instantiated and prior to any calls to its
     * other public methods.
     *
     * @param type The jmq.accesscontrol.type value in authProperties
     * @param authProperties The broker authentication/access control properties
     *
     * @throws AccessControlException
     */
    void initialize(String type, Properties authProperties);

    /**
     * load the access control model
     *
     * @throws AccessControlException
     */
    void load();

    /**
     * Check connection permission for the subject
     *
     * @param clientUser The Principal represents the client user that associated with the subject
     * @param serviceName The service instance name (eg. "broker", "admin")
     * @param serviceType The service type for the service instance <BR>
     * ("NORMAL" or "ADMIN") <BR>
     * @param subject The subject
     *
     * @throws AccessControlException
     */
    void checkConnectionPermission(Principal clientUser, String serviceName, String serviceType, Subject subject);

    /**
     * Check permission for an operation on a destination for the subject
     *
     * @param clientUser The Principal represents the client user that associated with the subject
     * @param serviceName The service instance name (eg. "broker", "admin")
     * @param serviceType The service type for the service instance <BR>
     * ("NORMAL" or "ADMIN") <BR>
     * @param subject The subject
     * @param operation The operaction ("send", "receive", "browse","publish", "subscribe")
     * @param destination The destination name
     * @param destinationType The destination Type ("queue" or "topic")
     *
     * @throws AccessControlException
     */
    void checkDestinationPermission(Principal clientUser, String serviceName, String serviceType, Subject subject, String operation, String destination,
            String destinationType);
}
