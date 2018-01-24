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
 * @(#)ServiceUtil.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetServicesHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.PauseHandler;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jms.management.server.MQObjectName;

public class ServiceUtil {
    public static ServiceInfo getServiceInfo(String service)  {
	ServiceInfo si = GetServicesHandler.getServiceInfo(service);

	return (si);
    }

    public static void pauseService(String service) 
		throws BrokerException {
	PauseHandler.pauseService(true, service);
    }

    public static void resumeService(String service)
		throws BrokerException {
	PauseHandler.pauseService(false, service);
    }

    /*
     * Returns a List of service names that are visible to
     * the outside
     */
    public static List getVisibleServiceNames()  {

        ServiceManager sm = Globals.getServiceManager();
        List serviceNames = sm.getAllServiceNames();
	return (serviceNames);
    }
   
    /*
     * Returns an ArrayList of services (ServiceInfo) that are visible to
     * the outside
     */
    public static List getVisibleServices()  {

        List serviceNames = getVisibleServiceNames();
        Iterator iter = serviceNames.iterator();

	ArrayList al = new ArrayList();

        while (iter.hasNext()) {
            String service = (String)iter.next();
	    /*
            System.out.println("\t" + service);
	    */
	    ServiceInfo sInfo = GetServicesHandler.getServiceInfo(service);
	    al.add(sInfo);
        }

	return (al);
    }

    public static int toExternalServiceState(int internalServiceState)  {
	switch (internalServiceState)  {
	case com.sun.messaging.jmq.util.ServiceState.RUNNING:
	    return (com.sun.messaging.jms.management.server.ServiceState.RUNNING);

	case com.sun.messaging.jmq.util.ServiceState.PAUSED:
	    return (com.sun.messaging.jms.management.server.ServiceState.PAUSED);

	case com.sun.messaging.jmq.util.ServiceState.QUIESCED:
	    return (com.sun.messaging.jms.management.server.ServiceState.QUIESCED);

	default:
	    return (com.sun.messaging.jms.management.server.ServiceState.UNKNOWN);
	}
    }

    public static int toInternalServiceState(int externalServiceState)  {
	switch (externalServiceState)  {
	case com.sun.messaging.jms.management.server.ServiceState.RUNNING:
	    return (com.sun.messaging.jmq.util.ServiceState.RUNNING);

	case com.sun.messaging.jms.management.server.ServiceState.PAUSED:
	    return (com.sun.messaging.jmq.util.ServiceState.PAUSED);

	case com.sun.messaging.jms.management.server.ServiceState.QUIESCED:
	    return (com.sun.messaging.jmq.util.ServiceState.QUIESCED);

	default:
	    return (com.sun.messaging.jmq.util.ServiceState.UNKNOWN);
	}
    }

    public static List getConsumerIDs(String service)  {
	List	consumerIDs = new ArrayList(),
		connections = ConnectionUtil.getConnectionInfoList(service);

	if ((connections == null) || (connections.size() == 0))  {
	    return (consumerIDs);
	}

	Iterator itr = connections.iterator();
	int i = 0;
	while (itr.hasNext()) {
	    ConnectionInfo cxnInfo = (ConnectionInfo)itr.next();
	    long cxnID = cxnInfo.uuid;
	    List oneCxnConsumerIDs = ConnectionUtil.getConsumerIDs(cxnID);

	    consumerIDs.addAll(oneCxnConsumerIDs);
	}

	return (consumerIDs);
    }

    public static List getProducerIDs(String service)  {
	List	producerIDs = new ArrayList(),
		connections = ConnectionUtil.getConnectionInfoList(service);

	if ((connections == null) || (connections.size() == 0))  {
	    return (producerIDs);
	}

	Iterator itr = connections.iterator();
	int i = 0;
	while (itr.hasNext()) {
	    ConnectionInfo cxnInfo = (ConnectionInfo)itr.next();
	    long cxnID = cxnInfo.uuid;
	    List oneCxnProducerIDs = ConnectionUtil.getProducerIDs(cxnID);

	    producerIDs.addAll(oneCxnProducerIDs);
	}

	return (producerIDs);
    }

}
