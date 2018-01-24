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

import java.util.Properties;
import java.util.HashMap;
import javax.net.ssl.SSLContext;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;
import com.sun.messaging.bridge.api.Bridge;

@Contract
@Singleton
public interface BridgeBaseContext 
{

    public static final String PROP_BRIDGE = "bridge";
    public static final String PROP_PREFIX = "PROP_PREFIX";
    public static final String PROP_ADMIN_USER_SUFFIX = ".admin.user";
    public static final String PROP_ADMIN_PASSWORD_SUFFIX = ".admin.password";

    /**
     * SSL configuration properties
     */
    public static final String KEYSTORE_FILE = "javax.net.ssl.keyStore";
    public static final String KEYSTORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    public static final String KEYSTORE_TYPE = "javax.net.ssl.keyStoreType";

    public static final String TRUSTSTORE_FILE = "javax.net.ssl.trustStore";
    public static final String TRUSTSTORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    public static final String TRUSTSTORE_TYPE = "javax.net.ssl.trustStoreType";

    public static final String KEYSTORE_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    public static final String TRUSTSTORE_ALGORITHM = "ssl.TrustManagerFactory.algorithm";
    public static final String SECURESOCKET_PROTOCOL = "securesocket.protocol";


    /**
     *
     * @return true if embeded in a broker process
     */
    public boolean isEmbeded();

    /**
     * @return true if do network bind
     */
    public boolean doBind();

    /**
     * @return true if the broker does not have its own JVM
     */
    public boolean isEmbededBroker();

    /**
     * @return true if running on nucleus
     */
    public boolean isRunningOnNucleus();

    /**
     * @return true if broker started in silent mode
     */
    public boolean isSilentMode();

    /**
     * @return null if PUService not enabled
     */
    public Object getPUService();

    /**
     *
     * @return the runtime configuration for bridge service manager
     */
    public Properties getBridgeConfig(); 

    /**
     * @return true if JDBC store type
     */
    public boolean isJDBCStoreType() throws Exception;

    /**
     * Get object that implements JDBC persist store for bridges
     *
     * @return null if not JDBC type store
     */
    public Object getJDBCStore() throws Exception;

    /**
     *
     * @return true if the broker has HA enabled 
     */
    public boolean isHAEnabled();

    /**
     *
     * @param props Bridge properties to update 
     */
    public void updateBridgeConfig(Properties props) throws Exception;

    /**
     *
     * @param protocol The MQ Connection Service protocol string, like "tcp", "ssl"
     * @param serviceType The MQ Connection Service type "NORMAL" or "ADMIN"
     */
    public String getBrokerServiceAddress(String protocol, String serviceType) throws Exception; 

    /**
     *
     */
    public String getBrokerHostName(); 

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


    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
	public boolean handleGlobalError(Throwable ex, String reason); 


    /**
     * Register (optional) a service with host  
     */
	public void registerService(String name, String protocol, 
                                String type, int port, HashMap props); 


    /**
     * Get default configuration properties for SSLContext   
     *
     * @return the default configuration properties for SSLContext 
     */
    public Properties getDefaultSSLContextConfig(String caller) throws Exception;

    /**
     * Get unique identifier for this instance 
     *
     * @return an unique identifier for this instance
     */
    public String getIdentityName() throws Exception;
    

    /**
     * Whether start with reset 
     *
     * @return true if start with reset
     */
    public boolean isStartWithReset();

    /**
     * @return true if ok to allocate size bytes of mem
     */
    public boolean allocateMemCheck(long size); 

    public boolean getPoodleFixEnabled();

    public String[] getKnownSSLEnabledProtocols();
}

