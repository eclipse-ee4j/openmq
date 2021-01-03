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

package com.sun.messaging.bridge.api;

import java.util.Properties;
import java.util.HashMap;
import org.jvnet.hk2.annotations.Contract;
import jakarta.inject.Singleton;

@Contract
@Singleton
public interface BridgeBaseContext {

    String PROP_BRIDGE = "bridge";
    String PROP_PREFIX = "PROP_PREFIX";
    String PROP_ADMIN_USER_SUFFIX = ".admin.user";
    String PROP_ADMIN_PASSWORD_SUFFIX = ".admin.password";

    /**
     * SSL configuration properties
     */
    String KEYSTORE_FILE = "javax.net.ssl.keyStore";
    String KEYSTORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    String KEYSTORE_TYPE = "javax.net.ssl.keyStoreType";

    String TRUSTSTORE_FILE = "javax.net.ssl.trustStore";
    String TRUSTSTORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    String TRUSTSTORE_TYPE = "javax.net.ssl.trustStoreType";

    String KEYSTORE_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    String TRUSTSTORE_ALGORITHM = "ssl.TrustManagerFactory.algorithm";
    String SECURESOCKET_PROTOCOL = "securesocket.protocol";

    /**
     *
     * @return true if embeded in a broker process
     */
    boolean isEmbeded();

    /**
     * @return true if do network bind
     */
    boolean doBind();

    /**
     * @return true if the broker does not have its own JVM
     */
    boolean isEmbededBroker();

    /**
     * @return true if running on nucleus
     */
    boolean isRunningOnNucleus();

    /**
     * @return true if broker started in silent mode
     */
    boolean isSilentMode();

    /**
     * @return null if PUService not enabled
     */
    Object getPUService();

    /**
     *
     * @return the runtime configuration for bridge service manager
     */
    Properties getBridgeConfig();

    /**
     * @return true if JDBC store type
     */
    boolean isJDBCStoreType() throws Exception;

    /**
     * Get object that implements JDBC persist store for bridges
     *
     * @return null if not JDBC type store
     */
    Object getJDBCStore() throws Exception;

    /**
     *
     * @return true if the broker has HA enabled
     */
    boolean isHAEnabled();

    /**
     *
     * @param props Bridge properties to update
     */
    void updateBridgeConfig(Properties props) throws Exception;

    /**
     *
     * @param protocol The MQ Connection Service protocol string, like "tcp", "ssl"
     * @param serviceType The MQ Connection Service type "NORMAL" or "ADMIN"
     */
    String getBrokerServiceAddress(String protocol, String serviceType) throws Exception;

    /**
     *
     */
    String getBrokerHostName();

    /**
     * Logging method for Bridge Service Manager
     */
    void logError(String message, Throwable t);

    /**
     * Logging method for Bridge Service Manager
     */
    void logWarn(String message, Throwable t);

    /**
     * Logging method for Bridge Service Manager
     */
    void logInfo(String message, Throwable t);

    /**
     * Logging method for Bridge Service Manager
     */
    void logDebug(String message, Throwable t);

    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
    boolean handleGlobalError(Throwable ex, String reason);

    /**
     * Register (optional) a service with host
     */
    void registerService(String name, String protocol, String type, int port, HashMap props);

    /**
     * Get default configuration properties for SSLContext
     *
     * @return the default configuration properties for SSLContext
     */
    Properties getDefaultSSLContextConfig(String caller) throws Exception;

    /**
     * Get unique identifier for this instance
     *
     * @return an unique identifier for this instance
     */
    String getIdentityName() throws Exception;

    /**
     * Whether start with reset
     *
     * @return true if start with reset
     */
    boolean isStartWithReset();

    /**
     * @return true if ok to allocate size bytes of mem
     */
    boolean allocateMemCheck(long size);

    boolean getPoodleFixEnabled();

    String[] getKnownSSLEnabledProtocols();
}
