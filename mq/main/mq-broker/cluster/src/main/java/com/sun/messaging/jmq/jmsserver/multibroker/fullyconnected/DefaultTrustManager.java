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
 * @(#)DefaultTrustManager.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.io.*;
import javax.net.ssl.*;
import java.security.cert.*;

/**
 * The default trust manager for cluster SSL connections. Does not
 * require a trust store. Any certificate is accepted. This is useful
 * in the trusted intranet environments where users often want
 * encryption, but any self signed certificates may be trusted.
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
/*
 * EOF
 */
