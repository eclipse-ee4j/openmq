/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

public class UserInfo {
    public static final String ROLE_ANON = "anonymous";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWD = "admin";

    public static final String DEFAULT_ANON_USERNAME = "guest";
    public static final String DEFAULT_ANON_PASSWD = "guest";

    String user = null, passwd = null, role = null;
    boolean active = true;

    public UserInfo(String user, String passwd) {
        this(user, passwd, "user", true);
    }

    public UserInfo(String user, String passwd, String role) {
        this(user, passwd, role, true);
    }

    public UserInfo(String user, String passwd, String role, boolean active) {
        this.user = user;
        this.passwd = passwd;
        this.role = role;
        this.active = active;
    }

    public String getUser() {
        return user;
    }

    public String getPasswd() {
        return passwd;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getPasswdEntry() {
        return (user + ":" + passwd + ":" + role + ":" + (active ? "1" : "0"));
    }

    @Override
    public String toString() {
        return (getPasswdEntry());
    }
}
