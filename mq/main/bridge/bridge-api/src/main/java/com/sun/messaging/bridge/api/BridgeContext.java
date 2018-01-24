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

package com.sun.messaging.bridge.api;

import java.io.File;
import java.util.Properties;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Enumeration;
import javax.net.ssl.SSLContext;
import com.sun.messaging.bridge.api.BridgeBaseContext;
import com.sun.messaging.bridge.api.BridgeUtil;

/**
 * The runtime context for a Bridge Service
 *
 * @author amyk
 */
public interface BridgeContext 
{

    public static final String BRIDGE_PROP_PREFIX = "BRIDGE_PROP_PREFIX"; 

    /**
     * SSL configuration properties
     */
    public static final String KEYSTORE_FILE = BridgeBaseContext.KEYSTORE_FILE;
    public static final String KEYSTORE_PASSWORD = BridgeBaseContext.KEYSTORE_PASSWORD;
    public static final String KEYSTORE_TYPE = BridgeBaseContext.KEYSTORE_TYPE;

    public static final String TRUSTSTORE_FILE = BridgeBaseContext.TRUSTSTORE_FILE;
    public static final String TRUSTSTORE_PASSWORD = BridgeBaseContext.TRUSTSTORE_PASSWORD;
    public static final String TRUSTSTORE_TYPE = BridgeBaseContext.TRUSTSTORE_TYPE;

    public static final String KEYSTORE_ALGORITHM = BridgeBaseContext.KEYSTORE_ALGORITHM;
    public static final String TRUSTSTORE_ALGORITHM = BridgeBaseContext.TRUSTSTORE_ALGORITHM;
    public static final String SECURESOCKET_PROTOCOL = BridgeBaseContext.SECURESOCKET_PROTOCOL;


    /**
     * @return true if it's embeded in a broker process
     */
     public boolean isEmbeded();

    /**
     */
     public boolean doBind();

    /**
     *
     * @return true if the broker does not have its own JVM
     */
     public boolean isEmbededBroker();

    /**
     *
     * @return true if running on nucleus
     */
     public boolean isRunningOnNucleus();

     /**
      * @return true if should disable console logging
      */
     public boolean isSilentMode();

     /**
      * @return null if PUService not enabled
      */
     public Object getPUService(); 

    /**
     *
     * @return the runtime configuration for a bridge service
     */
    public Properties getConfig();

    public String getRootDir();

    public String getLibDir();

    public String getProperty(String suffix); 

    /**
     *
     * @param props additional properties to set to the connection factory
     *
     * @return a JMS connection factory for the bridge service
     */
    public javax.jms.ConnectionFactory getConnectionFactory(
                         Properties props) throws Exception; 
    /**
     *
     * @param props additional properties to set to the XA connection factory
     *
     * @return a JMS XA connection factory for the bridge service
     */
    public javax.jms.XAConnectionFactory getXAConnectionFactory(
                              Properties props) throws Exception; 

    /**
     *
     * @return a JMS connection factory for the bridge service
     */
    public javax.jms.ConnectionFactory getAdminConnectionFactory(Properties props)
                                                throws Exception; 

    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
    public boolean handleGlobalError(Throwable ex, String reason); 

    /**
     * Register (optional) a service with host
     */
    public void registerService(String protocol, String type, 
                                int port, HashMap props); 

    /**
     * Get default configuration properties for SSLContext
     *
     * @return the default configuration properties for SSLContext
     */
    public Properties getDefaultSSLContextConfig() throws Exception; 

    /**
     * Get unique identifier for this instance
     *
     * @return an unique identifier for this instance
     */
    public String getIdentityName() throws Exception; 

    public String getBrokerHostName(); 

    public String getTransactionManagerClass() throws Exception; 

    /**
     * return an empty Properties object if no property set 
     */
    public Properties getTransactionManagerProps() throws Exception; 

    public boolean isJDBCStoreType() throws Exception; 

    public Object getJDBCStore(String type) throws Exception; 

    /**
     * @return true if ok to allocate size bytes of mem
     */
    public boolean allocateMemCheck(long size); 

    public boolean getPoodleFixEnabled();

    public String[] getKnownSSLEnabledProtocols();

    /**
     * Logging method for Bridge Service Manager
     */
    public void logError(String message, Throwable t); 

    /**
     * Logging method for Bridge Service Manager
     */
    public void logWarn(String message, Throwable t); 

    /**
     * Logging method for Bridge Service Manager
     */
    public void logInfo(String message, Throwable t); 

    /**
     * Logging method for Bridge Service Manager
     */
    public void logDebug(String message, Throwable t); 

}
