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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.util.Map;
import java.io.IOException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ServiceFactory;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQIPServiceFactory;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 * @author amyk
 */
public class WebSocketIPServiceFactory extends IMQIPServiceFactory
{

    private static final String myHandlerName = ServiceFactory.WEBSOCKET_HANDLER_NAME;

    
    @Override
    public void enforceServiceHandler(String service, BrokerConfig config)
    throws BrokerException {

        String prototype = config.getProperty(
            ServiceFactory.SERVICE_PREFIX+service+
            ServiceFactory.SERVICE_PROTOCOLTYPE_SUFFIX);
        if (prototype == null) {
            return;
        }
        if (!prototype.equalsIgnoreCase("ws") && 
            !prototype.equalsIgnoreCase("wss")) {
            return;
        }

        /**
         * Client (e.g. MQ client runtime) may use the default 
         * standard service names as default service name. Although
         * the default standard service names can be renamed by 
         * broker properties on broker side, it's better to not
         * use them as websocket service name.
         */ 
        if (isDefaultStandardServiceName(service)) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_PROTOCOLTYPE_NO_SUPPORT, 
                prototype, service));
        }
        String hn = config.getProperty(
            ServiceFactory.SERVICE_PREFIX+service+
            ServiceFactory.SERVICE_HANDLER_SUFFIX);
        if (hn != null) {
            if (!hn.equals(myHandlerName)) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_PROTOCOLTYPE_NO_SUPPORT, 
                    prototype, service+"["+hn+"]"));
            }
        }
        if (hn == null) {
            try {
                config.updateProperty(
                    ServiceFactory.SERVICE_PREFIX+service+
                    ServiceFactory.SERVICE_HANDLER_SUFFIX,
                    myHandlerName, false);
            } catch (Exception e) {
                throw new BrokerException(e.getMessage(), e);
            }

            String tpmodel = config.getProperty(
                       ServiceFactory.SERVICE_PREFIX+service+
                       ServiceFactory.SERVICE_THREADPOOL_MODEL_SUFFIX);
            if (tpmodel != null) {
                Globals.getLogger().log(Logger.WARNING, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.W_IGNORE_PROP_SETTING, 
                    ServiceFactory.SERVICE_PREFIX+service+
                    ServiceFactory.SERVICE_THREADPOOL_MODEL_SUFFIX+"="+tpmodel));
            }
        }
    }

    public WebSocketIPServiceFactory() {
    }

    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        if (!myHandlerName.equals(handlerName)) {
            throw new IllegalAccessException(
            "Unexpected service Handler name "+handlerName+", expected "+myHandlerName);
        }
    }

    @Override
    public  void updateService(Service s) throws BrokerException {
        if (!(s instanceof WebSocketIPService)) {
            throw new BrokerException(
                br.getKString(br.E_INTERNAL_BROKER_ERROR,
                "Unexpected service class: "+s));
        }
        WebSocketIPService ss = (WebSocketIPService)s;
        try {
            ss.updateService(((WebSocketProtocolImpl)ss.getProtocol()).getPort(),
                getThreadMin(s.getName()), getThreadMax(s.getName()), false);
        } catch (Exception e) {
            throw new BrokerException(e.getMessage(), e);
        }
    }

    @Override
    public Service createService(String name, int type)
    throws BrokerException {
        IMQService s =  new WebSocketIPService(name, type, Globals.getPacketRouter(type),
                                   getThreadMin(name), getThreadMax(name), this);
        long timeout = getPoolTimeout(name);
        if (timeout > 0) {
            s.setDestroyWaitTime(timeout);
        }
        return s;
    }

    protected IMQService createService(String instancename,
        Protocol proto, PacketRouter router, int type, int min, int max)
        throws IOException {
        throw new UnsupportedOperationException("Unexpected call");
    }

    @Override
    protected Map getProtocolParams(String protoname, String prefix) {
        return super.getProtocolParams(protoname, prefix);
    }

}

