/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2025 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.auth.acl;

import java.util.Properties;
import java.security.Principal;
import java.security.Permission;
import java.security.AccessControlException;
import java.security.Policy;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.auth.jaas.*;
import com.sun.messaging.jmq.auth.api.server.model.*;

@SuppressWarnings({
    "deprecation", "removal" // java.security.AccessControlException in java.security has been deprecated and marked for removal
                             // java.security.Policy in java.security has been deprecated and marked for removal
})
public class JAASAccessControlModel implements AccessControlModel {

    public static final String TYPE = "jaas";

    public static final String PROP_PERMISSION_FACTORY = TYPE + ".permissionFactory";

    private Logger logger = Globals.getLogger();

    private PermissionFactory permFactory = null;

    /**
     * This method is called immediately after this AccessControlModel has been instantiated and prior to any calls to its
     * other public methods.
     *
     * @param type the jmq.accesscontrol.type
     * @param authProperties broker auth properties
     *
     * @throws AccessControlException
     */
    @Override
    public void initialize(String type, Properties authProperties) {
        // this.type = type;
        if (!type.equals(TYPE)) {
            String[] args = { type, TYPE, this.getClass().getName() };
            String emsg = Globals.getBrokerResources().getKString(BrokerResources.X_ACCESSCONTROL_TYPE_MISMATCH, args);
            logger.log(Logger.ERROR, emsg);
            throw new AccessControlException(emsg);
        }
        Properties authProps = authProperties;

        String pfclass = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_PREFIX + PROP_PERMISSION_FACTORY);
        try {
            if (pfclass != null) {
                permFactory = (PermissionFactory) Class.forName(pfclass).getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
            throw new AccessControlException(e.getClass().getName() + ": " + e.getMessage());
        }

        load();
    }

    /** @throws AccessControlException */
    @Override
    public void load() {

        try {
            Policy.getPolicy().refresh();
        } catch (SecurityException e) {
            AccessControlException ace = new AccessControlException(e.toString());
            ace.initCause(e);
            throw ace;
        }
    }

    /**
     *
     * Check connection permission
     *
     * @param clientUser The Principal represents the client user that is associated with the subject
     * @param serviceName the service instance name (eg. "broker", "admin")
     * @param serviceType the service type for the service instance ("NORMAL" or "ADMIN")
     * @param subject the authenticated subject
     *
     * @throws AccessControlException
     */
    @Override
    public void checkConnectionPermission(Principal clientUser, String serviceName, String serviceType, Subject subject) {

        Permission perm;
        try {
            perm = permFactory.newPermission(PermissionFactory.CONN_RESOURCE_PREFIX + serviceType, (String) null);
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.toString(), e);
            AccessControlException ace = new AccessControlException(e.toString());
            ace.initCause(e);
            throw ace;
        }
        try {
            checkPermission(subject, perm);
        } catch (AccessControlException e) {
            AccessControlException ace = new AccessControlException(e.getMessage() + ": " + clientUser + " [" + subject.getPrincipals() + "]");
            ace.initCause(e);
            throw ace;
        }
    }

    /**
     * Check permission for an operation on a destination for this role
     *
     * @param clientUser The Principal represents the client user that is associated with the subject
     * @param serviceName the service instance name (eg. "broker", "admin")
     * @param serviceType the service type for the service instance ("NORMAL" or "ADMIN")
     * @param subject the authenticated subject
     * @param operation the operaction
     * @param destination the destination
     *
     * @throws AccessControlException
     */
    @Override
    public void checkDestinationPermission(Principal clientUser, String serviceName, String serviceType, Subject subject, String operation, String destination,
            String destinationType) {
        Permission perm;
        try {
            if (operation.equals(PacketType.AC_DESTCREATE)) {
                perm = permFactory.newPermission(PermissionFactory.AUTO_RESOURCE_PREFIX + PermissionFactory.DEST_QUEUE, (String) null);
            } else {
                perm = permFactory.newPermission(PermissionFactory.DEST_RESOURCE_PREFIX + PermissionFactory.DEST_QUEUE_PREFIX + destination,
                        operation);
            }
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.toString(), e);
            AccessControlException ace = new AccessControlException(e.toString());
            ace.initCause(e);
            throw ace;
        }
        try {
            checkPermission(subject, perm);
        } catch (AccessControlException e) {
            AccessControlException ace = new AccessControlException(e.getMessage() + ": " + clientUser + " [" + subject.getPrincipals() + "]");
            ace.initCause(e);
            throw ace;
        }
    }

    /** @throws AccessControlException */
    private void checkPermission(Subject subject, Permission p) {

        final Permission perm = p;
        Subject.doAsPrivileged(subject, new PrivilegedAction() {
            @Override
            public Object run() {
                java.security.AccessController.checkPermission(perm);
                return null; // nothing to return
            }
        }, null);

    }

}
