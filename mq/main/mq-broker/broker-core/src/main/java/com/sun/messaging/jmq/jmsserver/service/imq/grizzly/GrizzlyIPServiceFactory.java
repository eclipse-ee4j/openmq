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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;


import java.util.Map;
import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQIPServiceFactory;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;


public class GrizzlyIPServiceFactory extends IMQIPServiceFactory
{

    public GrizzlyIPServiceFactory() {
    }

    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname = "shared";
        if (!myname.equals(handlerName)) {
            throw new IllegalAccessException(
            "Unexpected service Handler name "+handlerName+", expected "+myname);
        }
    }

    @Override
    public void updateService(Service s) throws BrokerException {
        if (!(s instanceof GrizzlyIPService)) {
            throw new BrokerException(
                br.getKString(br.E_INTERNAL_BROKER_ERROR, 
                "Unexpected service class: "+s));
        }
        GrizzlyIPService ss = (GrizzlyIPService)s;
        try {
            ss.updateService(((GrizzlyProtocolImpl)ss.getProtocol()).getPort(),
                getThreadMin(s.getName()), getThreadMax(s.getName()), false);
        } catch (Exception e) {
            throw new BrokerException(e.getMessage(), e);
        }
    }

    @Override
    public Service createService(String name, int type)
    throws BrokerException {
        IMQService s =  new GrizzlyIPService(name, type, Globals.getPacketRouter(type),
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

