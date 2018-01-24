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
 * @(#)AccessControlContext.java	1.7 06/28/07
 */ 
 
package com.sun.messaging.jmq.auth.api.server;

import java.util.Properties;
import java.security.Principal;
import java.security.AccessControlException;

/**
 * An AccessControlContext encapsulates a context for the current authenticated
 * user (the subject) and permissions.  It has 2 check permission methods,
 *
 * checkConnectionPermission
 * checkDestinationPermission
 *
 * It makes these access decisions based on the context it encapsulates.
 * An object of AccessControlContext is returned from
 *
 * AuthenticationProtocolHandler.getAccessControlContext()
 *
 * after the login() (authentication) compeletes successfully.
 */

public interface AccessControlContext {

    /**
     *
     * @return The Principal that represents the client user 
     *         that associated with the subject. 
     */
    public Principal getClientUser();

    /**
     * Check connection permission based on the access control context
     *
     * @param serviceName The name of the service instance the connection belongs
     * @param serviceType The service type as in broker configuration <BR>
     *                    ("NORMAL" or "ADMIN") <BR>
     *
     * @exception AccessControlException
     */
    public void checkConnectionPermission(String serviceName,
                                          String serviceType)
                                          throws AccessControlException;

    /**
     * Check permission for an operation on a destination based on the 
     * access control context. 
     * 
     * @param serviceName The name of the service instance the connection belongs
     * @param serviceType The service type as in broker configuration <BR>
     *                    ("NORMAL" or "ADMIN") <BR>
     * @param operation "send" "receive" "publish" "subscribe" "browse"
     * @param destination The destination name
     * @param destinationType "queue" or "topic"
     *
     * @exception AccessControlException
     */
    public void checkDestinationPermission(String serviceName,
                                           String serviceType,
                                           String operation,
                                           String destination,
                                           String destinationType)
                                           throws AccessControlException; 
}
