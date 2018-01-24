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
 * @(#)IMQEmbeddedServiceFactory.java	10/28/08
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

public class IMQEmbeddedServiceFactory extends ServiceFactory
{

    protected static final Logger logger = Globals.getLogger();

    protected BrokerConfig props = Globals.getConfig();

    protected int DEFAULT_DESTROY_TIMEOUT=30;

    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname = "mqdirect";
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

        // set changes to the service
        int newmin = getThreadMin(name);
        int newmax = getThreadMax(name);
        try {
        ss.setMinMaxThreadpool(newmin, newmax);
        } catch (IllegalArgumentException e) {
            throw new BrokerException(
                     Globals.getBrokerResources().getKString(
                         BrokerResources.X_THREADPOOL_BAD_SET,
                         String.valueOf(newmin),
                         String.valueOf(newmax)),
                     e);
        }

        // Register port with portmapper
        Globals.getPortMapper().addService(name, "none",
            props.getProperty(SERVICE_PREFIX + name + ".servicetype"),
            0, ss.getServiceProperties());
        
    }

// XXX - this is not optimized, but it should rarely happen

    public  void startMonitoringService(Service s)
        throws BrokerException {

        String name = s.getName();

        // add min/max properties
        String bstr = SERVICE_PREFIX + name + ".min_threads";
        props.addListener(bstr, this);


        bstr = SERVICE_PREFIX + name + ".max_threads";
        props.addListener(bstr, this);
    }

    public  void stopMonitoringService(Service s)   
        throws BrokerException
    {
        String name = s.getName();

        // remove min/max properties
        String bstr = SERVICE_PREFIX + name + ".min";
        props.removeListener(bstr, this);


        bstr = SERVICE_PREFIX + name + ".max";
        props.removeListener(bstr, this);
    }


    public  void validate(String name, String value)
        throws PropertyUpdateException {
        // for now, dont bother with validation
    }

    public  boolean update(String name, String value) 
    {

        return true;
    }

    protected int getThreadMin(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".min_threads";
        return props.getIntProperty(bstr); 
    }

    protected int getPoolTimeout(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".destroy_timeout";

        // get timer and covert to seconds
        return props.getIntProperty(bstr,DEFAULT_DESTROY_TIMEOUT )*1000; 
    }

    protected int getThreadMax(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".max_threads";
        return props.getIntProperty(bstr); 
    }

    public Service createService(String instancename, int type) 
        throws BrokerException
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG, " Creating new Service("+ instancename +
                  ": Embedded )");
        }

        Service svc = new IMQEmbeddedService(instancename,
                type, Globals.getPacketRouter(type), getThreadMin(instancename),
                getThreadMax(instancename)); 

        // bug 4433282 -> support optional timeout for pool
        long timeout = getPoolTimeout(instancename);
        if (timeout > 0)
               ((IMQService)svc).setDestroyWaitTime(timeout);
        return svc;
 
    }

}
/*
 * EOF
 */
