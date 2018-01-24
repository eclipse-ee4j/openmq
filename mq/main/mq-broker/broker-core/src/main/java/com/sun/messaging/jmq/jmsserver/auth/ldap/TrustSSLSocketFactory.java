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
 * @(#)TrustSSLSocketFactory.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.ldap;

import javax.net.*;
import javax.net.ssl.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;

public abstract class TrustSSLSocketFactory extends javax.net.ssl.SSLSocketFactory {

    //private Logger logger = Globals.getLogger();

    public static SocketFactory getDefault() {

        try {

        return getTrustSocketFactory();

        }
        catch (java.security.NoSuchAlgorithmException e) {
        Globals.getLogger().log(Logger.ERROR, e.getMessage(), e);
        }
        catch (java.security.KeyManagementException e) { 
        Globals.getLogger().log(Logger.ERROR, e.getMessage(), e);
        }

        return null;
    }

    private static SSLSocketFactory getTrustSocketFactory() 
                   throws java.security.NoSuchAlgorithmException,
                          java.security.KeyManagementException {

        SSLContext ctx = SSLContext.getInstance("TLS");
        TrustManager[] tm = new TrustManager[1];
        tm[0] = new X509TrustManager() {
/* Unused
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain) {
                return;
                }
*/
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String type) {
                return;
                }
/* Unused
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain) {
                return;
                }
*/
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String type) {
                return;
                }
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
                }
                };

        ctx.init(null, tm, null);
        return ctx.getSocketFactory();
    }
}
