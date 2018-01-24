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
 * @(#)MQBasicPermission.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.auth.jaas;

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Collections;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.security.Permission;
import java.security.PermissionCollection;

/**
 * A base permission class for MQ connection and destination
 * auto-creation permissionS
 */

public abstract class MQBasicPermission extends Permission 
{ 

    private static final long serialVersionUID = 7965671047666454007L;

    private transient boolean wildcard;

    /**
     *
     */
    public MQBasicPermission(String name) {
        super(name);
        init(name);
    }

    /**
     *
     */
    private void init(String name) {
        if (name == null) throw new NullPointerException("name null");

        int len = name.length();
        if (len == 0) throw new IllegalArgumentException("name empty");

        if (len == 1 && name.equals("*")) {
            wildcard = true;
        } else {
            validateName(name);
        }
    }

    public abstract void validateName(String name) throws IllegalArgumentException; 

    /**
     * 
     */
    public boolean implies(Permission p) {
	    if (!(p instanceof MQBasicPermission)) return false;
        if (p.getClass() != getClass()) return false;

        MQBasicPermission that = (MQBasicPermission)p;

        if (this.wildcard) return true;
        if (that.wildcard) return false;

	    return this.getName().equals(that.getName());
    }

    /**
     *
     */
    public boolean equals(Object obj) {
	    if (obj == this) return true;

        if (!(obj instanceof MQBasicPermission)) return false;

        if (obj.getClass() != getClass()) return false;

        MQBasicPermission p = (MQBasicPermission)obj;

        return getName().equals(p.getName());
    }

    /**
     *
     */
    public int hashCode() {
        return this.getName().hashCode();
    }

    /**
     *
     */
    public String getActions() {
        return "";
    }

    /**
     *
     */
    public PermissionCollection newPermissionCollection() {
	    return new MQBasicPermissionCollection();
    }

    /**
     *
     */
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        init(getName());
    }

}


/**
 *
 */
final class MQBasicPermissionCollection extends PermissionCollection
{

    private static final long serialVersionUID = 8958682081876542895L;

    private Map perms = null;
    private Class permClass = null;

    private boolean allowAll = false; 

    /**
     *
     */
    public MQBasicPermissionCollection() {
        perms = new HashMap(11);
        allowAll = false;
    }

    /**
     *
     */
    public void add(Permission p) {

        if (! (p instanceof MQBasicPermission)) {
            throw new IllegalArgumentException("invalid permission: "+p);
        }

        if (isReadOnly()) {
	        throw new SecurityException("Attempt to add to a read only permission "+p);
        }

        MQBasicPermission pm = (MQBasicPermission)p;

        synchronized (this) {
            if (perms.size() == 0) {
                permClass = pm.getClass();
            } else if (pm.getClass() != permClass) {
                throw new IllegalArgumentException("invalid permission: " +p);
            }
            perms.put(pm.getName(), pm);
        }

        if (!allowAll) {
            if (pm.getName().equals("*")) allowAll = true;
        }
    }

    /**
     *
     */
    public boolean implies(Permission p) {
        if (! (p instanceof MQBasicPermission)) return false;

        MQBasicPermission pm = (MQBasicPermission)p;

        if (pm.getClass() != permClass) return false;

        if (allowAll) return true;


        Permission x;
        synchronized (this) {
            x = (Permission) perms.get(p.getName());
        }
        if (x != null) return x.implies(p);

        return false;
	}

    /**
     *
     */
    public Enumeration elements() {
        synchronized (this) {
	        return Collections.enumeration(perms.values());
        }
    }

}
