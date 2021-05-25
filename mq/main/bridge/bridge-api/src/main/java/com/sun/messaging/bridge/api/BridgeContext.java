/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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
import jakarta.jms.ConnectionFactory;
import jakarta.jms.XAConnectionFactory;

/**
 * The runtime context for a Bridge Service
 *
 * @author amyk
 */
public interface BridgeContext {

    String BRIDGE_PROP_PREFIX = "BRIDGE_PROP_PREFIX";

    /**
     * SSL configuration properties
     */
    String KEYSTORE_FILE = BridgeBaseContext.KEYSTORE_FILE;
    String KEYSTORE_PASSWORD = BridgeBaseContext.KEYSTORE_PASSWORD;
    String KEYSTORE_TYPE = BridgeBaseContext.KEYSTORE_TYPE;

    String TRUSTSTORE_FILE = BridgeBaseContext.TRUSTSTORE_FILE;
    String TRUSTSTORE_PASSWORD = BridgeBaseContext.TRUSTSTORE_PASSWORD;
    String TRUSTSTORE_TYPE = BridgeBaseContext.TRUSTSTORE_TYPE;

    String KEYSTORE_ALGORITHM = BridgeBaseContext.KEYSTORE_ALGORITHM;
    String TRUSTSTORE_ALGORITHM = BridgeBaseContext.TRUSTSTORE_ALGORITHM;
    String SECURESOCKET_PROTOCOL = BridgeBaseContext.SECURESOCKET_PROTOCOL;

    /**
     * @return true if it's embeded in a broker process
     */
    boolean isEmbeded();

    /**
     */
    boolean doBind();

    /**
     * @return true if should disable console logging
     */
    boolean isSilentMode();

    /**
     * @return null if PUService not enabled
     */
    Object getPUService();

    /**
     *
     * @return the runtime configuration for a bridge service
     */
    Properties getConfig();

    String getRootDir();

    String getLibDir();

    /**
     *
     * @param props additional properties to set to the connection factory
     *
     * @return a JMS connection factory for the bridge service
     */
    ConnectionFactory getConnectionFactory(Properties props) throws Exception;

    /**
     *
     * @param props additional properties to set to the XA connection factory
     *
     * @return a JMS XA connection factory for the bridge service
     */
    XAConnectionFactory getXAConnectionFactory(Properties props) throws Exception;

    /**
     *
     * @return a JMS connection factory for the bridge service
     */
    ConnectionFactory getAdminConnectionFactory(Properties props) throws Exception;

    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
    boolean handleGlobalError(Throwable ex, String reason);

    /**
     * Register (optional) a service with host
     */
    void registerService(String protocol, String type, int port, HashMap props);

    /**
     * Get default configuration properties for SSLContext
     *
     * @return the default configuration properties for SSLContext
     */
    Properties getDefaultSSLContextConfig() throws Exception;

    /**
     * Get unique identifier for this instance
     *
     * @return an unique identifier for this instance
     */
    String getIdentityName() throws Exception;

    String getBrokerHostName();

    String getTransactionManagerClass() throws Exception;

    /**
     * return an empty Properties object if no property set
     */
    Properties getTransactionManagerProps() throws Exception;

    boolean isJDBCStoreType() throws Exception;

    Object getJDBCStore(String type) throws Exception;

    boolean getPoodleFixEnabled();

    String[] getKnownSSLEnabledProtocols();

    /**
     * Logging method for Bridge Service Manager
     */
    void logError(String message, Throwable t);

    /**
     * Logging method for Bridge Service Manager
     */
    void logInfo(String message, Throwable t);
}
