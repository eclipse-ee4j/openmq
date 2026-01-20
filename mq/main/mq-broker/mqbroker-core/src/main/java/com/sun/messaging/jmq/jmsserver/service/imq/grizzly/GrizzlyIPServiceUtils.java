/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import java.util.Properties;

import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.portunif.PUService;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GrizzlyIPServiceUtils {
    @SuppressWarnings("deprecation")
    public SSLEngineConfigurator initializeSSL(String servicen, GrizzlyProtocolImpl proto) throws Exception {

        boolean reqcauth = proto.getRequireClientAuth();
        Globals.getLogger().log(Logger.INFO,
                Globals.getBrokerResources().getKString(BrokerResources.I_INIT_FOR_SERVICE, proto.getType() + "[ClientAuth=" + reqcauth + "]", servicen));

        Properties sslprops = KeystoreUtil.getDefaultSSLContextConfig(servicen, null);
        SSLContextConfigurator sslcf = new SSLContextConfigurator();
        sslcf.setKeyManagerFactoryAlgorithm(sslprops.getProperty(KeystoreUtil.KEYSTORE_ALGORITHM));
        sslcf.setKeyStoreFile(sslprops.getProperty(KeystoreUtil.KEYSTORE_FILE));
        sslcf.setKeyStorePass(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_PASSWORD));
        sslcf.setKeyStoreType(sslprops.getProperty(KeystoreUtil.KEYSTORE_TYPE));

        sslcf.setTrustManagerFactoryAlgorithm(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_ALGORITHM));
        sslcf.setTrustStoreFile(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_FILE));
        sslcf.setTrustStorePass(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_PASSWORD));
        sslcf.setTrustStoreType(sslprops.getProperty(KeystoreUtil.TRUSTSTORE_TYPE));

        sslcf.setSecurityProtocol(sslprops.getProperty(KeystoreUtil.SECURESOCKET_PROTOCOL));

        SSLEngineConfigurator ec = new SSLEngineConfigurator(sslcf.createSSLContext(), false, reqcauth, reqcauth);
        if (Globals.getPoodleFixEnabled()) {
            PUService.applyPoodleFix(ec, Globals.getKnownSSLEnabledProtocols("GrizzlyIPService[" + servicen + "]"), "GrizzlyIPService[" + servicen + "]");
        }
        return ec;
    }
}
