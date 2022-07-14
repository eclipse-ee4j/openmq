/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021, 2022 Contributors to the Eclipse Foundation
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

/**
 * This class is for MQ service connection permissions. It contains a name (also referred to as a "target name") but no
 * actions list; you either have the named permission or you don't.
 */

public final class MQConnectionPermission extends MQBasicPermission {

    private static final long serialVersionUID = 1919911567794615301L;

    public MQConnectionPermission(String name) {
        super(name);
    }

    @Override
    public void validateName(String name) {
        if (!name.equals(PermissionFactory.CONN_NORMAL) && !name.equals(PermissionFactory.CONN_ADMIN)) {
            throw new IllegalArgumentException("invalidate name " + name);
        }
    }
}
