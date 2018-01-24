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

package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

import javax.net.ssl.*;
import java.security.cert.*;

/**
 * DefaultTrustManager, just returns true when called to authenicate
 * remote side certificates.
 */

public class DefaultTrustManager implements X509TrustManager {

    private static  boolean DEBUG = false;

    public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
        String authType) throws CertificateException {
        if (DEBUG) {
            System.out.println("DefaultTrustManager.checkClientTrusted() - true");
        }
    }

    public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
        String authType) throws CertificateException {
        if (DEBUG) {
            System.out.println("DefaultTrustManager.isServerTrusted() - true");
        }
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
    }
}

