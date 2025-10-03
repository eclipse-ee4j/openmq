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

package com.sun.messaging.bridge.admin;

import java.io.File;
import java.util.Properties;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Enumeration;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.BridgeBaseContext;
import com.sun.messaging.bridge.api.BridgeUtil;

/**
 * The runtime context for a Bridge Service
 *
 * @author amyk
 */
public class BridgeContextImpl implements BridgeContext {

    private BridgeBaseContext _bc;
    private String _name = null;
    private Properties _config = null;

    public BridgeContextImpl(BridgeBaseContext bc, String name) {
        _bc = bc;
        _name = name;

        _config = new Properties();
        Properties props = _bc.getBridgeConfig();
        String prefix = props.getProperty(BridgeBaseContext.PROP_PREFIX) + "." + _name;

        List keys = BridgeUtil.getPropertyNames(prefix, props);

        String key = null;
        Iterator itr = keys.iterator();
        while (itr.hasNext()) {
            key = (String) itr.next();
            _config.put(key, props.getProperty(key));
        }
        _config.put(prefix + ".varhome", props.getProperty(props.getProperty(BridgeBaseContext.PROP_PREFIX) + ".varhome") + File.separator + _name);

        _config.put(prefix + ".libhome", props.getProperty(props.getProperty(BridgeBaseContext.PROP_PREFIX) + ".libhome"));

        _config.put(BRIDGE_PROP_PREFIX, prefix);
    }

    /**
     *
     * @return true if it's embeded in a broker process
     */
    @Override
    public boolean isEmbeded() {
        return _bc.isEmbeded();
    }

    /**
     */
    @Override
    public boolean doBind() {
        return _bc.doBind();
    }

    /**
     * @return true if should disable console logging
     */
    @Override
    public boolean isSilentMode() {
        return _bc.isSilentMode();
    }

    /**
     * @return null if PUService not enabled
     */
    @Override
    public Object getPUService() {
        return _bc.getPUService();
    }

    /**
     *
     * @return the runtime configuration for a bridge service
     */
    @Override
    public Properties getConfig() {
        return _config;
    }

    @Override
    public String getRootDir() {
        return _config.getProperty(_config.getProperty(BRIDGE_PROP_PREFIX) + ".varhome");
    }

    @Override
    public String getLibDir() {
        return _config.getProperty(_config.getProperty(BRIDGE_PROP_PREFIX) + ".libhome");
    }

    /**
     *
     * @param props additional properties to set to the connection factory
     *
     * @return a JMS connection factory for the bridge service
     */
    @Override
    public jakarta.jms.ConnectionFactory getConnectionFactory(Properties props) throws Exception {

        com.sun.messaging.ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();

        if (props != null) {
            String name = null;
            Enumeration en = props.propertyNames();
            while (en.hasMoreElements()) {
                name = (String) en.nextElement();
                if (!name.equals(com.sun.messaging.ConnectionConfiguration.imqAddressList)) {
                    cf.setProperty(name, props.getProperty(name));
                }
            }
        }

        cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressList,
                _bc.getBrokerServiceAddress("tcp", com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_NORMAL));
        // cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqSetJMSXConsumerTXID, "true");
        return cf;
    }

    /**
     *
     * @param props additional properties to set to the XA connection factory
     *
     * @return a JMS XA connection factory for the bridge service
     */
    @Override
    public jakarta.jms.XAConnectionFactory getXAConnectionFactory(Properties props) throws Exception {

        com.sun.messaging.XAConnectionFactory cf = new com.sun.messaging.XAConnectionFactory();

        if (props != null) {
            String name = null;
            Enumeration en = props.propertyNames();
            while (en.hasMoreElements()) {
                name = (String) en.nextElement();
                if (!name.equals(com.sun.messaging.ConnectionConfiguration.imqAddressList)) {
                    cf.setProperty(name, props.getProperty(name));
                }
            }
        }

        cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressList,
                _bc.getBrokerServiceAddress("tcp", com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_NORMAL));
        // cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqSetJMSXConsumerTXID, "true");
        return cf;
    }

    /**
     *
     * @return a JMS connection factory for the bridge service
     */
    @Override
    public jakarta.jms.ConnectionFactory getAdminConnectionFactory(Properties props) throws Exception {

        com.sun.messaging.ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();

        if (props != null) {
            String name = null;
            Enumeration en = props.propertyNames();
            while (en.hasMoreElements()) {
                name = (String) en.nextElement();
                if (!name.equals(com.sun.messaging.ConnectionConfiguration.imqAddressList)) {
                    cf.setProperty(name, props.getProperty(name));
                }
            }
        }

        cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressList,
                _bc.getBrokerServiceAddress("tcp", com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_NORMAL));

        Properties bprops = _bc.getBridgeConfig();
        String keyu = bprops.getProperty(BridgeBaseContext.PROP_PREFIX) + _bc.PROP_ADMIN_USER_SUFFIX;
        String keyp = bprops.getProperty(BridgeBaseContext.PROP_PREFIX) + _bc.PROP_ADMIN_PASSWORD_SUFFIX;
        String user = bprops.getProperty(keyu);
        String passwd = bprops.getProperty(keyp);
        cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqDefaultUsername, user);
        cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqDefaultPassword, passwd);
        return cf;
    }

    /**
     * Handle global errors like OOM
     *
     * @return true if the method actually did something with the error
     */
    @Override
    public boolean handleGlobalError(Throwable ex, String reason) {
        return _bc.handleGlobalError(ex, reason);
    }

    /**
     * Register (optional) a service with host
     */
    @Override
    public void registerService(String protocol, String type, int port, HashMap props) {

        _bc.registerService(_name, protocol, type, port, props);
    }

    /**
     * Get default configuration properties for SSLContext
     *
     * @return the default configuration properties for SSLContext
     */
    @Override
    public Properties getDefaultSSLContextConfig() throws Exception {
        return _bc.getDefaultSSLContextConfig(_name);
    }

    /**
     * Get unique identifier for this instance
     *
     * @return an unique identifier for this instance
     */
    @Override
    public String getIdentityName() throws Exception {
        return _bc.getIdentityName();
    }

    @Override
    public String getBrokerHostName() {
        return _bc.getBrokerHostName();
    }

    @Override
    public String getTransactionManagerClass() throws Exception {
        Properties props = _bc.getBridgeConfig();

        String key = props.getProperty(BridgeBaseContext.PROP_PREFIX) + "." + _name + ".tm.class";
        String value = props.getProperty(key);
        if (value != null) {
            return value;
        }

        key = BridgeBaseContext.PROP_PREFIX + ".tm.class";
        return props.getProperty(key);
    }

    /**
     * return an empty Properties object if no property set
     */
    @Override
    public Properties getTransactionManagerProps() throws Exception {
        Properties tmp = new Properties();
        Properties props = _bc.getBridgeConfig();

        String key = BridgeBaseContext.PROP_PREFIX + ".tm.props";
        List<String> plist0 = BridgeUtil.getListProperty(key, props);

        key = props.getProperty(BridgeBaseContext.PROP_PREFIX) + "." + _name + ".tm.props";
        List<String> plist1 = BridgeUtil.getListProperty(key, props);

        if (plist0 == null && plist1 == null) {
            return tmp;
        }

        if (plist0 != null) {
            for (String value : plist0) {
                List<String> l = BridgeUtil.breakToList(value, "=");
                if (l.size() != 2) {
                    throw new IllegalArgumentException("Invalid element for broker property " + key);
                }
                tmp.setProperty(l.get(0), l.get(1));
            }
        }
        if (plist1 != null) {
            for (String value : plist1) {
                List<String> l = BridgeUtil.breakToList(value, "=");
                if (l.size() != 2) {
                    throw new IllegalArgumentException("Invalid element for broker property " + key);
                }
                tmp.setProperty(l.get(0), l.get(1));
            }
        }
        return tmp;
    }

    @Override
    public boolean isJDBCStoreType() throws Exception {
        return _bc.isJDBCStoreType();
    }

    @Override
    public Object getJDBCStore(String type) throws Exception {
        if (type.toUpperCase(Locale.getDefault()).equals("JMS")) {
            return _bc.getJDBCStore();
        }
        return null;
    }

    @Override
    public boolean getPoodleFixEnabled() {
        return _bc.getPoodleFixEnabled();
    }

    @Override
    public String[] getKnownSSLEnabledProtocols() {
        return _bc.getKnownSSLEnabledProtocols();
    }

    /**
     * Logging method for Bridge Service Manager
     */
    @Override
    public void logError(String message, Throwable t) {
        _bc.logError(message, t);
    }

    /**
     * Logging method for Bridge Service Manager
     */
    @Override
    public void logInfo(String message, Throwable t) {
        _bc.logInfo(message, t);
    }
}
