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
 * @(#)DefaultTrustManager.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.ssl;

import javax.net.ssl.*;
import java.security.cert.*;

/**
 * The default trust manager.  This class is instantiated when the connection
 * factory sets 'trustServer' value to true.
 *
 * <p>If this class is used, the client does not require to install/configure
 * server certificates because all server certs are accepted.
 *
 * <p>This is useful for intra-net applications where servers are inside
 * firewall and are treated as trusted.
 */
public class DefaultTrustManager implements X509TrustManager {

    public void checkClientTrusted(X509Certificate[] chain,
        String authType) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] chain,
        String authType) throws CertificateException {
    }

    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
