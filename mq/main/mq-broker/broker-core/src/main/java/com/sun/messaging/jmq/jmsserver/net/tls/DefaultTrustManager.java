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
 * @(#)DefaultTrustManager.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.tls;

import java.security.cert.*;
import javax.security.cert.X509Certificate;
import javax.net.ssl.*;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;

/**
 * DefaultTrustManager to manage authentication trust decisions for 
 * different types of authentication material.  Here X509TrustManager is 
 * implemented as the keystore is of type JKS which contains certificates
 * of type X509.
 *
 * X509TrustManager is an interface to manage which X509 certificates which
 * are used to authenticate the remote side of a secure socket. Decisions may
 * be based on trusted certificate authorities, certificate revocation lists,
 * online status checking or other means. 
 * 
 * Currently contains some dummy methods.  This should be sufficient for 
 * the 2.0 product.
 *
 * @see java.security.cert.X509TrustManager
 *
 */

public class DefaultTrustManager implements X509TrustManager {

    private static  boolean DEBUG = false;
    Logger logger = Globals.getLogger();

    public void checkClientTrusted(java.security.cert.X509Certificate[] chain) {
        return;
    }

    public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
				String type) {
        return;
    }
    
    public void checkServerTrusted(java.security.cert.X509Certificate[] chain) { 
        if (DEBUG) {
	    logger.log(Logger.DEBUGHIGH,"DefaultTrustManager called to validate certs ..");
	    logger.log(Logger.DEBUGHIGH,"returning 'true' for isServerTrusted call ...");
        }
        return;
    }

    public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
			String type) { 
        if (DEBUG) {
	    logger.log(Logger.DEBUGHIGH,"DefaultTrustManager called to validate certs ..");
	    logger.log(Logger.DEBUGHIGH,"returning 'true' for isServerTrusted call ...");
        }
        return;
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
    }
}
