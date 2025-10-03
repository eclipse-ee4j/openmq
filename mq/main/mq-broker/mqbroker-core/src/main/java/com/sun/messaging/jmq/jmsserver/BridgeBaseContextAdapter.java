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

package com.sun.messaging.jmq.jmsserver;

import java.io.File;
import java.util.Properties;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Iterator;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;
import com.sun.messaging.jmq.jmsserver.tlsutil.SSLPropertyMap;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.bridge.api.BridgeBaseContext;

/**
 *
 */
public class BridgeBaseContextAdapter implements BridgeBaseContext, SSLPropertyMap {
    private static boolean DEBUG = false;

    public static final String PROP_ADMIN_PASSWORD = Globals.IMQ + "." + BridgeBaseContext.PROP_BRIDGE + BridgeBaseContext.PROP_ADMIN_PASSWORD_SUFFIX;

    private Logger logger = null;

    private Broker broker = null;
    private boolean reset = false;

    protected static boolean bridgeEnabled() {
        return Globals.getConfig().getBooleanProperty(Globals.IMQ + "." + BridgeBaseContext.PROP_BRIDGE + ".enabled", false);
    }

    protected static String getManagerClass() {
        return Globals.getConfig().getProperty(Globals.IMQ + "." + BridgeBaseContext.PROP_BRIDGE + ".managerclass",
                "com.sun.messaging.bridge.admin.BridgeServiceManagerImpl");
    }

    /**
     *
     */
    protected BridgeBaseContextAdapter(Broker broker, boolean reset) {
        this.broker = broker;
        this.reset = reset;
        logger = Globals.getLogger();
    }

    @Override
    public boolean isEmbeded() {
        return true;
    }

    @Override
    public boolean doBind() {
        if (!Globals.isNucleusManagedBroker()) {
            return true;
        }
        return Globals.getPortMapper().isDoBind();
    }

    @Override
    public boolean isRunningOnNucleus() {
        return Globals.isNucleusManagedBroker();
    }

    @Override
    public boolean isSilentMode() {
        return broker.isSilentMode();
    }

    @Override
    public boolean isStartWithReset() {
        return reset;
    }

    @Override
    public Object getPUService() {
        return Globals.getPUService();
    }

    /**
     *
     * @return the runtime configuration for Bridge Services Manager
     */
    @Override
    public Properties getBridgeConfig() {

        Properties props = new Properties();

        String prefix = Globals.IMQ + "." + BridgeBaseContext.PROP_BRIDGE;

        BrokerConfig bc = Globals.getConfig();

        List keys = Globals.getConfig().getPropertyNames(prefix);

        String key = null;
        Iterator itr = keys.iterator();
        while (itr.hasNext()) {
            key = (String) itr.next();
            props.put(key, bc.getProperty(key));
        }

        props.put(prefix + ".varhome", Globals.getInstanceDir() + File.separator + BridgeBaseContext.PROP_BRIDGE + "s");

        String lib = bc.getProperty(Globals.JMQ_LIB_HOME_PROPERTY);

        props.put(prefix + ".libhome", lib);

        props.put(prefix + ".stomp.type", "stomp");
        props.put(prefix + ".stomp.class", "com.sun.messaging.bridge.service.stomp.StompBridge");

        props.put(prefix + ".jms.class", "com.sun.messaging.bridge.service.jms.BridgeImpl");

        props.put(BridgeBaseContext.PROP_PREFIX, prefix);

        return props;
    }

    /**
     *
     * @param props Bridge properties to update
     */
    @Override
    public void updateBridgeConfig(Properties props) throws Exception {
        Globals.getConfig().updateProperties(props);
    }

    @Override
    public boolean isJDBCStoreType() throws Exception {
        return (Globals.getStore().getStoreType().equals(Store.JDBC_STORE_TYPE));
    }

    /**
     */
    @Override
    public Object getJDBCStore() throws Exception {
        if (!Globals.getStore().isJDBCStore()) {
            return null;
        }
        return Globals.getStore();
    }

    /**
     *
     * @return true if the broker has HA enabled
     */
    @Override
    public boolean isHAEnabled() {
        return Globals.getHAEnabled();
    }

    /**
     *
     * @param protocol The MQ Connection Service protocol string, like "tcp", "ssl"
     * @param serviceType The MQ Connection Service type "NORMAL" or "ADMIN"
     *
     * @return a MQ Message Service Address for this broker
     */
    @Override
    public String getBrokerServiceAddress(String protocol, String serviceType) throws Exception {
        PortMapperEntry pme = null, e = null;
        
        Iterator itr = Globals.getPortMapper().getServices().values().iterator();
        while (itr.hasNext()) {
            e = (PortMapperEntry) itr.next();
            Locale loc = Locale.getDefault();
            if (e.getProtocol().toLowerCase(loc).equals(protocol.toLowerCase(loc)) && e.getType().equals(serviceType)) {
                pme = e;
                break;
            }
        }
        if (pme == null) {
            throw new Exception("No available service found with protocol " + protocol + " and type " + serviceType);
        }
        String h = pme.getProperty("hostname");
        if (h == null || h.length() == 0 || h.equals(Globals.HOSTNAME_ALL)) {
            h = Globals.getMQAddress().getHostName();
            if (DEBUG) {
                logger.log(logger.INFO, "getBrokerServiceAddress:" + serviceType + " " + protocol + " global hostname=" + h);
            }
        } else {
            h = MQAddress.getMQAddress(h, pme.getPort()).getHostName();
            if (DEBUG) {
                logger.log(logger.INFO, "getBrokerServiceAddress:" + serviceType + " " + protocol + " service hostname=" + h);
            }
        }
        return "mq" + protocol.toLowerCase(Globals.getBrokerResources().getLocale()) + "://" + h + ":" + pme.getPort() + "/" + pme.getName();
    }

    @Override
    public String getBrokerHostName() {
        String h = Globals.getHostname();
        if (h != null && h.equals(Globals.HOSTNAME_ALL)) {
            return null;
        }
        return h;
    }

    /**
     *
     */
    @Override
    public String getIdentityName() throws Exception {
        return Globals.getIdentityName();
    }

    @Override
    public boolean getPoodleFixEnabled() {
        return Globals.getPoodleFixEnabled();
    }

    @Override
    public String[] getKnownSSLEnabledProtocols() {
        return Globals.getKnownSSLEnabledProtocols("BridgeService");
    }

    /**
     * Logging methods for Bridge Services Manager
     */
    @Override
    public void logError(String message, Throwable t) {

        String msg = "BridgeManager: " + message;
        if (t != null) {
            logger.logStack(logger.ERROR, msg, t);
        } else {
            logger.log(logger.ERROR, msg);
        }
    }

    @Override
    public void logWarn(String message, Throwable t) {

        String msg = "BridgeManager: " + message;
        if (t != null) {
            logger.logStack(logger.WARNING, msg, t);
        } else {
            logger.log(logger.WARNING, msg);
        }
    }

    @Override
    public void logInfo(String message, Throwable t) {

        String msg = "BridgeManager: " + message;
        if (t != null) {
            logger.logStack(logger.INFO, msg, t);
        } else {
            logger.log(logger.INFO, msg);
        }
    }

    @Override
    public void logDebug(String message, Throwable t) {

        String msg = "BridgeManager: " + message;
        if (t != null) {
            logger.logStack(logger.DEBUG, msg, t);
        } else {
            logger.log(logger.DEBUG, msg);
        }
    }

    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
    @Override
    public boolean handleGlobalError(Throwable ex, String reason) {
        Globals.handleGlobalError(ex, reason);
        return true;
    }

    /**
     * (optional) register the service with host
     */
    @Override
    public void registerService(String name, String protocol, String type, int port, HashMap props) {
        String nam = name + "[" + BridgeBaseContext.PROP_BRIDGE + "]";
        String typ = type + "[" + BridgeBaseContext.PROP_BRIDGE + "]";
        Globals.getPortMapper().addService(nam, protocol, typ, port, props);
    }

    /**
     * Get default SSLContext config
     */
    @Override
    public Properties getDefaultSSLContextConfig(String caller) throws Exception {
        return KeystoreUtil.getDefaultSSLContextConfig(caller + "[" + BridgeBaseContext.PROP_BRIDGE + "]", this);
    }

    @Override
    public String mapSSLProperty(String prop) throws Exception {
        if (prop.equals(KeystoreUtil.KEYSTORE_FILE)) {
            return BridgeBaseContext.KEYSTORE_FILE;
        }
        if (prop.equals(KeystoreUtil.KEYSTORE_PASSWORD)) {
            return BridgeBaseContext.KEYSTORE_PASSWORD;
        }
        if (prop.equals(KeystoreUtil.KEYSTORE_TYPE)) {
            return BridgeBaseContext.KEYSTORE_TYPE;
        }
        if (prop.equals(KeystoreUtil.KEYSTORE_ALGORITHM)) {
            return BridgeBaseContext.KEYSTORE_ALGORITHM;
        }

        if (prop.equals(KeystoreUtil.TRUSTSTORE_FILE)) {
            return BridgeBaseContext.TRUSTSTORE_FILE;
        }
        if (prop.equals(KeystoreUtil.TRUSTSTORE_PASSWORD)) {
            return BridgeBaseContext.TRUSTSTORE_PASSWORD;
        }
        if (prop.equals(KeystoreUtil.TRUSTSTORE_TYPE)) {
            return BridgeBaseContext.TRUSTSTORE_TYPE;
        }
        if (prop.equals(KeystoreUtil.TRUSTSTORE_ALGORITHM)) {
            return BridgeBaseContext.TRUSTSTORE_ALGORITHM;
        }

        if (prop.equals(KeystoreUtil.SECURESOCKET_PROTOCOL)) {
            return BridgeBaseContext.SECURESOCKET_PROTOCOL;
        }

        throw new IllegalArgumentException("unknow " + prop);
    }
}
