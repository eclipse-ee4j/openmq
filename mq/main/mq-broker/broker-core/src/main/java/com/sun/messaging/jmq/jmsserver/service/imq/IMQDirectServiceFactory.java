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
 * @(#)IMQDirectServiceFactory.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import com.sun.messaging.jmq.jmsserver.service.ServiceFactory;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

//XXX-LKS we currently dont do anything with changed properties
//        we may want to change this in future releases
public class IMQDirectServiceFactory extends ServiceFactory
{

    private static final Logger logger = Globals.getLogger();

    private BrokerConfig props = Globals.getConfig();


    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname = "direct";
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

        Globals.getPortMapper().addService(name, "none", 
        	Globals.getConfig().getProperty(
        	SERVICE_PREFIX + name + ".servicetype"),
        	0, ss.getServiceProperties());
    }

// XXX - this is not optimized, but it should rarely happen

    public  void startMonitoringService(Service s)
        throws BrokerException {
    }

    public  void stopMonitoringService(Service s)   
        throws BrokerException {
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
        try {
            Service svc = new IMQDirectService(instancename, type,
                              getThreadMin(instancename), 
                              getThreadMax(instancename),
                              getAccessControl(instancename));

            return svc;
        } catch (Exception ex) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.E_ERROR_STARTING_SERVICE, instancename), ex);
        }
 
    }


    private int getThreadMin(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".min_threads";
        return props.getIntProperty(bstr); 
    }

    private int getThreadMax(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".max_threads";
        return props.getIntProperty(bstr); 
    }

    private boolean getAccessControl(String instancename)
    {
        String bstr = SERVICE_PREFIX + instancename + ".accesscontrol.enabled";
        return props.getBooleanProperty(bstr, false); 
    }


}
/*
 * EOF
 */
