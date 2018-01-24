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
 * @(#)ServiceManagerConfig.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.List;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.management.util.ServiceUtil;

public class ServiceManagerConfig extends MQMBeanReadWrite  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ServiceAttributes.MAX_THREADS,
					Integer.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_MAX_THREADS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.MIN_THREADS,
					Integer.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_MIN_THREADS),
					true,
					false,
					false),
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ServiceOperations.GET_SERVICES,
	        mbr.getString(mbr.I_SVC_MGR_CFG_OP_GET_SERVICES),
		    null, 
		    ObjectName[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ServiceOperations.PAUSE,
	        mbr.getString(mbr.I_SVC_MGR_OP_PAUSE),
		    null, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(ServiceOperations.RESUME,
	        mbr.getString(mbr.I_SVC_MGR_OP_RESUME),
		    null, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION)
		};

    public ServiceManagerConfig()  {
	super();
    }

    public Integer getMaxThreads()  {
	MetricManager mm = Globals.getMetricManager();
	MetricCounters mc = mm.getMetricCounters(null);

	return (Integer.valueOf(mc.threadsHighWater));
    }

    public Integer getMinThreads()  {
	MetricManager mm = Globals.getMetricManager();
	MetricCounters mc = mm.getMetricCounters(null);

	return (Integer.valueOf(mc.threadsLowWater));
    }

    public ObjectName[] getServices() throws MBeanException  {
	List l = ServiceUtil.getVisibleServiceNames();

	if (l.size() == 0)  {
	    return (null);
	}

	ObjectName oNames[] = new ObjectName [ l.size() ];

        Iterator iter = l.iterator();

	int i = 0;
        while (iter.hasNext()) {
            String service = (String)iter.next();

	    try  {
	        ObjectName o = MQObjectName.createServiceConfig(service);

	        oNames[i++] = o;
	    } catch (Exception e)  {
		handleOperationException(ServiceOperations.GET_SERVICES, e);
	    }
        }

	return (oNames);
    }

    public void pause() throws MBeanException  {
	try  {
	    logger.log(Logger.INFO, "Pausing all services");
	    ServiceUtil.pauseService(null);
	} catch(BrokerException e)  {
	    handleOperationException(ServiceOperations.PAUSE, e);
	}
    }

    public void resume() throws MBeanException  {
	try  {
	    logger.log(Logger.INFO, "Resuming all services");
	    ServiceUtil.resumeService(null);
	} catch(BrokerException e)  {
	    handleOperationException(ServiceOperations.RESUME, e);
	}
    }

    public String getMBeanName()  {
	return ("ServiceManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_SVC_MGR_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (null);
    }
}
