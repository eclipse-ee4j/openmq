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
 * @(#)ServiceFactory.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * A service handler is an abstract class which
 * handles creating services and managing their
 * resources (e.g. updating them when properties
 * change)
 */

public abstract class ServiceFactory implements ConfigListener
{

    public static final String SERVICE_PREFIX = Globals.IMQ + ".";
    public static final String SERVICE_PROTOCOLTYPE_SUFFIX = ".protocoltype";
    public static final String SERVICE_HANDLER_SUFFIX = ".handler_name";
    public static final String SERVICE_THREADPOOL_MODEL_SUFFIX = ".threadpool_model";
    public static final String WEBSOCKET_HANDLER_NAME = "websocket";

    protected static boolean DEBUG = false;

    protected final Logger logger = Globals.getLogger();
    protected final BrokerResources br = Globals.getBrokerResources();

    private String factoryHandlerName = null;

    ConnectionManager conmgr = null;

    /**
     * subclass should add code entry here if override 
     * instance method enforceServiceHandler()
     */
    public static void enforceServiceHandler(
        String service, BrokerConfig config, ServiceManager sm)
        throws BrokerException {
        if (sm == null) {
            return;
        }
        if (service.equals(MQAddress.DEFAULT_WS_SERVICE) ||
            service.equals(MQAddress.DEFAULT_WSS_SERVICE)) {
            String prototype = config.getProperty(
                       SERVICE_PREFIX+service+SERVICE_PROTOCOLTYPE_SUFFIX);
            if (prototype != null && 
                !prototype.equalsIgnoreCase("ws") &&
                !prototype.equalsIgnoreCase("wss")) {
                throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_PROTOCOLTYPE_NO_SUPPORT,
                prototype, service));
            }
        }

        ServiceFactory sf = null;
        try {
            sf = sm.createServiceFactory(WEBSOCKET_HANDLER_NAME, true);
        } catch (ClassNotFoundException e) {
        } catch (Exception e) {
            throw new BrokerException(e.getMessage(), e);
        }
        if (sf != null) {
            sf.enforceServiceHandler(service, config);
        }
    }

    /**
     */
    public void enforceServiceHandler(String service, BrokerConfig config)
    throws BrokerException {
    }

    /**
     */
    public static boolean isDefaultStandardServiceName(String name) {
        if (name.equals("jms") || name.equals("ssljms") ||
            name.equals("admin") || name.equals("ssladmin") ||
            name.equals("httpjms") || name.equals("httpsjms")) {
            return true;
        }
        return false;
    }

    protected final void setFactoryHandlerName(String handlerName) {
        factoryHandlerName = handlerName;
    }

    public final String getFactoryHandlerName() {
        return factoryHandlerName;
    }

    /**
     * @param handlerName for the ServiceFactory
     * @throws IllegalAccessException if the handlerName not supported
     */
    protected abstract void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException;

    public void setConnectionManager(ConnectionManager conmgr)
    {
        this.conmgr = conmgr;
    }


    public abstract Service createService(String instancename, int servicetype)
        throws BrokerException;

    public abstract void updateService(Service s)
        throws BrokerException;

    public abstract void startMonitoringService(Service s)
        throws BrokerException;

    public abstract void stopMonitoringService(Service s)   
        throws BrokerException;

    public abstract void validate(String name, String value)
        throws PropertyUpdateException;

    public abstract boolean update(String name, String value);
 
    public ConnectionManager getConnectionManager()
    {
        return conmgr;
    }

}
