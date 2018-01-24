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
 * @(#)IMQDualThreadServiceFactory
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import com.sun.messaging.jmq.jmsserver.service.*;

import java.util.*;
import java.io.*;

import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.net.tcp.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

public class IMQDualThreadServiceFactory extends ServiceFactory
{

    protected static final Logger logger = Globals.getLogger();

    protected BrokerConfig props = Globals.getConfig();
  
    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname = "mqdirect2";
        if (!myname.equals(handlerName)) {
            throw new IllegalAccessException(
            "Unexpected service Handler name "+handlerName+", expected "+myname);
        }
    }

    public  void updateService(Service s)
        throws BrokerException
    {
        IMQService ss = (IMQService)s;
        String name = s.getName();

        // Register port with portmapper
        Globals.getPortMapper().addService(name, "none",
            props.getProperty(SERVICE_PREFIX + name + ".servicetype"),
            0, ss.getServiceProperties());
        
    }

// XXX - this is not optimized, but it should rarely happen

    public  void startMonitoringService(Service s)
        throws BrokerException {
    }

    public  void stopMonitoringService(Service s)   
        throws BrokerException
    {
    }


    public  void validate(String name, String value)
        throws PropertyUpdateException {
        // for now, dont bother with validation
    }

    public  boolean update(String name, String value) 
    {

        return true;
    }

    public Service createService(String instancename, int type) 
        throws BrokerException
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG, " Creating new Service("+ instancename +
                  ": Embedded )");
        }

        Service svc = new IMQDualThreadService(instancename,
                type, Globals.getPacketRouter(type));

        return svc;
 
    }

}
/*
 * EOF
 */
