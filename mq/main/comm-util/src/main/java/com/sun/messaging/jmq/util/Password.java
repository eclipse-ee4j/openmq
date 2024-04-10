/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.util;

import java.io.*;
import java.util.Arrays;

public class Password {

    private static boolean DEBUG = Boolean.getBoolean("imq.debug.com.sun.messaging.jmq.util.Password");

    public boolean echoPassword() {
        return false;
    }

    private String getPasswordFromJavaConsole() {
        if (DEBUG) {
            System.err.println("use java.io.Console");
        }
        try {
            Console console = System.console();
            if (console == null) {
                throw new Exception("Console not available");
            }
            char[] password = console.readPassword();
            if (password == null) {
                return null;
            }
            String pw = new String(password);
            Arrays.fill(password, ' ');
            password = null;
            return pw;
        } catch (Throwable e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    // We should call this guy, since no one else needs to know
    // that this call is system-dependent.
    public String getPassword() {
        return getPasswordFromJavaConsole();
    }
}
