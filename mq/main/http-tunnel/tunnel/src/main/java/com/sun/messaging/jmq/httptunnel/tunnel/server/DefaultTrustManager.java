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
 * @(#)DefaultTrustManager.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel.server;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.X509TrustManager;


/**
 * DefaultTrustManager, this is used when we are configured to
 * trust the remote host.
 */
public class DefaultTrustManager implements X509TrustManager {
    private static boolean DEBUG = Boolean.getBoolean("httptunnel.debug");
    private Logger logger = Logger.getLogger("Http Tunneling");

    public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
        String type) {
        if (DEBUG) {
            log("DefaultTrustManager.checkClientTrusted() " +
                "returning 'true'");
        }

        return;
    }

    public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
        String type) {
        if (DEBUG) {
            log("DefaultTrustManager.checkServerTrusted() " +
                "returning 'true'");
        }

        return;
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
    }

    private void log(String msg) {
        logger.log(Level.INFO, msg);
    }
}
