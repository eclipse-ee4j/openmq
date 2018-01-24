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
 * @(#)PermissionFactoryImpl.java	1.5 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.auth.acl;

import java.util.Map;
import com.sun.messaging.jmq.auth.jaas.*;

/**
 */

public class PermissionFactoryImpl implements PermissionFactory {

    //private static boolean DEBUG = false;

    /**
     *
     */
    public java.security.Permission newPermission (String privateString, //can be null
                                                   String resourceName, 
                                                   String actions,
                                                   Map conditions) {
         
        if (resourceName.startsWith(PermissionFactory.CONN_RESOURCE_PREFIX)) {
            return new MQConnectionPermission(
                   resourceName.substring(PermissionFactory.CONN_RESOURCE_PREFIX.length()));
        }
        if (resourceName.startsWith(PermissionFactory.DEST_RESOURCE_PREFIX)) {
            return new MQDestinationPermission(
                   resourceName.substring(PermissionFactory.DEST_RESOURCE_PREFIX.length()),
                   actions);
        }
        if (resourceName.startsWith(PermissionFactory.AUTO_RESOURCE_PREFIX)) {
            return new MQAutoCreateDestPermission(
                   resourceName.substring(PermissionFactory.AUTO_RESOURCE_PREFIX.length()));
        }
        throw new IllegalArgumentException("invalid resource name " + resourceName);
    }

}
