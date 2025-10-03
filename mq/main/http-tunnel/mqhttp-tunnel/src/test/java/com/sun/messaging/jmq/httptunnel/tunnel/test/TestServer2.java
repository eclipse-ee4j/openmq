/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.httptunnel.tunnel.test;

import com.sun.messaging.jmq.httptunnel.tunnel.server.*;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;

class TestServer2 {
    public static void main(String args[]) throws Exception {
        HttpTunnelServerDriverImpl driver = new HttpTunnelServerDriverImpl();
        driver.init("TestServer2");
        driver.start();
        HttpTunnelServerSocketImpl ss = new HttpTunnelServerSocketImpl();
        ss.init(driver);
        while (true) {
            HttpTunnelSocket s = ss.accept();
            System.out.println("[" + s.getConnId() + "] -- Accepted.");

            Reader r = new Reader(s);
            r.start();
        }
    }
}

