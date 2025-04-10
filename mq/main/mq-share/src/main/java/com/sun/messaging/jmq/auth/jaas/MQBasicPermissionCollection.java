/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Collections;
import java.io.Serial;
import java.security.Permission;
import java.security.PermissionCollection;

final class MQBasicPermissionCollection extends PermissionCollection {

    @Serial
    private static final long serialVersionUID = 8958682081876542895L;

    private Map perms = null;
    private Class permClass = null;

    private boolean allowAll = false;

    MQBasicPermissionCollection() {
        perms = new HashMap(11);
        allowAll = false;
    }

    @Override
    public void add(Permission p) {

        if (!(p instanceof MQBasicPermission)) {
            throw new IllegalArgumentException("invalid permission: " + p);
        }

        if (isReadOnly()) {
            throw new SecurityException("Attempt to add to a read only permission " + p);
        }

        MQBasicPermission pm = (MQBasicPermission) p;

        synchronized (this) {
            if (perms.isEmpty()) {
                permClass = pm.getClass();
            } else if (pm.getClass() != permClass) {
                throw new IllegalArgumentException("invalid permission: " + p);
            }
            perms.put(pm.getName(), pm);
        }

        if (!allowAll) {
            if (pm.getName().equals("*")) {
                allowAll = true;
            }
        }
    }

    @Override
    public boolean implies(Permission p) {
        if (!(p instanceof MQBasicPermission)) {
            return false;
        }

        MQBasicPermission pm = (MQBasicPermission) p;

        if (pm.getClass() != permClass) {
            return false;
        }

        if (allowAll) {
            return true;
        }

        Permission x;
        synchronized (this) {
            x = (Permission) perms.get(p.getName());
        }
        if (x != null) {
            return x.implies(p);
        }

        return false;
    }

    @Override
    public Enumeration elements() {
        synchronized (this) {
            return Collections.enumeration(perms.values());
        }
    }

}
