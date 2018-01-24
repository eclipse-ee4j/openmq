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
 * @(#)MQAutoCreateDestPermission.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.auth.jaas;

/**
 * This class is for MQ auto-create destination permissions.  It contains
 * a name (also referred to as a "target name") but no actions list; you 
 * either have the named permission or you don't.
 */

public final class MQAutoCreateDestPermission extends MQBasicPermission 
{ 

	private static final long serialVersionUID = -8834137571861608842L;

    /**
     *
     */
    public MQAutoCreateDestPermission(String name) {
        super(name);
    }

    public void validateName(String name) throws IllegalArgumentException {
        if (!name.equals(PermissionFactory.DEST_QUEUE) && 
            !name.equals(PermissionFactory.DEST_TOPIC)) {
            throw new IllegalArgumentException("invalidate name "+name);
        }
    }
}
