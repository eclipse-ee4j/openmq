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

package com.sun.messaging.jmq.jmsserver;

import java.util.Iterator;
import java.util.List;

import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQDirectService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsservice.JMSRABroker;
import com.sun.messaging.jmq.jmsservice.JMSService;

/**
 * Wrapper used to start the broker. It wraps a singleton class
 * (only one broker can be running in any process).<P>
 *
 * <u>Example</u><P>
 * <code><PRE>
 *      BrokerProcess bp = BrokerProcess.getBrokerProcess();
 *      try {
 *      
 *          Properties ht = BrokerProcess.convertArgs(args);
 *          int exitcode = bp.start(true, ht, null);
 *          System.out.println("Broker exited with " + exitcode);
 *
 *      } catch (IllegalArgumentException ex) {
 *          System.err.println("Bad Argument " + ex.getMessage());
 *          System.out.println(BrokerProcess.usage());
 *      }
 * </PRE></code>
 */
public class JMSRA_BrokerProcess extends BrokerProcess implements JMSRABroker
{
    private static final String	DEFAULT_DIRECTMODE_SERVICE_NAME = "jmsdirect";

    public JMSRA_BrokerProcess() {
        super();
    }

    /**
     *  Return the default JMS Service that supports 'DIRECT' in-JVM Java EE JMS
     *  clients.
     *
     *  @throws IllegalStateException if the broker is already stopped
     * 
     */
    public JMSService getJMSService() 
			throws IllegalStateException  {
	ServiceManager sm = Globals.getServiceManager();
	JMSService jmsService = getJMSService(DEFAULT_DIRECTMODE_SERVICE_NAME);

	if (jmsService != null)  {
	    return (jmsService);
	}

	/*
	 * If "jmsdirect" is not available, loop through all services
	 */
	List serviceNames = sm.getAllServiceNames();
	Iterator iter = serviceNames.iterator();

	while (iter.hasNext())  {
	    jmsService = getJMSService((String)iter.next());

	    if (jmsService != null)  {
	        return (jmsService);
	    }
	}

	return (null);
    }

    /**
     *  Return the named JMS Service that supports 'DIRECT' in-JVM Java EEJMS
     *  clients.
     *
     *  @param  serviceName The name of the service to return
     *
     *  @throws IllegalStateException if the broker is already stopped
     */
    public JMSService getJMSService(String serviceName) 
				throws IllegalStateException  {
	ServiceManager sm = Globals.getServiceManager();
	Service svc;
	IMQService imqSvc;
	IMQDirectService imqDirectSvc;

	if (sm == null)  {
	    return (null);
	}

	svc = sm.getService(serviceName);

	if (svc == null)  {
	    return (null);
	}

	if (!(svc instanceof IMQService))  {
	    return (null);
	}

	imqSvc = (IMQService)svc;

	if (!imqSvc.isDirect())  {
	    return (null);
	}

	if (!(imqSvc instanceof IMQDirectService))  {
	    return (null);
	}

	imqDirectSvc = (IMQDirectService)imqSvc;

	return imqDirectSvc.getJMSService();
    }
    
}


