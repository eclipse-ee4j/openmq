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
 * @(#)ServiceManagerMonitor.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.List;
import java.util.Iterator;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.jmsserver.management.util.ServiceUtil;

public class ServiceManagerMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ServiceAttributes.MSG_BYTES_IN,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_MSG_BYTES_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.MSG_BYTES_OUT,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_MSG_BYTES_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_ACTIVE_THREADS,
					Integer.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_ACTIVE_THREADS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_MSGS_IN,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_MSGS_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_MSGS_OUT,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_MSGS_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_PKTS_IN,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_PKTS_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_PKTS_OUT,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_PKTS_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_SERVICES,
					Integer.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_NUM_SERVICES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.PKT_BYTES_IN,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_PKT_BYTES_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.PKT_BYTES_OUT,
					Long.class.getName(),
	                                mbr.getString(mbr.I_SVC_MGR_ATTR_PKT_BYTES_OUT),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ServiceOperations.GET_SERVICES,
	        mbr.getString(mbr.I_SVC_MGR_MON_OP_GET_SERVICES),
		null , 
		ObjectName[].class.getName(),
		MBeanOperationInfo.INFO)
		    };
	
    private static String[] svcNotificationTypes = {
		    ServiceNotification.SERVICE_PAUSE,
		    ServiceNotification.SERVICE_RESUME
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    svcNotificationTypes,
		    ServiceNotification.class.getName(),
		    mbr.getString(mbr.I_SVC_NOTIFICATIONS)
		    )
		};


    public ServiceManagerMonitor()  {
        super();
    }

    public Long getMsgBytesIn()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.messageBytesIn));
    }

    public Long getMsgBytesOut()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.messageBytesOut));
    }

    public Integer getNumActiveThreads()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Integer.valueOf(mc.threadsActive));
    }

    public Long getNumMsgsIn()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.messagesIn));
    }

    public Long getNumMsgsOut()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.messagesOut));
    }

    public Long getNumPktsIn()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.packetsIn));
    }

    public Long getNumPktsOut()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.packetsOut));
    }

    public Integer getNumServices()  {
	List l = ServiceUtil.getVisibleServiceNames();

        return (Integer.valueOf(l.size()));
    }

    public Long getPktBytesIn()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.packetBytesIn));
    }

    public Long getPktBytesOut()  {
	MetricCounters mc = getMetricsForAllServices();
	return (Long.valueOf(mc.packetBytesOut));
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
	        ObjectName o = MQObjectName.createServiceMonitor(service);

	        oNames[i++] = o;
	    } catch (Exception e)  {
		handleOperationException(ServiceOperations.GET_SERVICES, e);
	    }
        }

	return (oNames);
    }

    public String getMBeanName()  {
	return ("ServiceManagerMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_SVC_MGR_MON_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void notifyServicePause(String name)  {
	ServiceNotification sn;
	sn = new ServiceNotification(ServiceNotification.SERVICE_PAUSE, 
			this, sequenceNumber++);
	sn.setServiceName(name);

	sendNotification(sn);
    }

    public void notifyServiceResume(String name)  {
	ServiceNotification sn;
	sn = new ServiceNotification(ServiceNotification.SERVICE_RESUME, 
			this, sequenceNumber++);
	sn.setServiceName(name);

	sendNotification(sn);
    }

    private MetricCounters getMetricsForAllServices()  {
	MetricManager mm = Globals.getMetricManager();
	MetricCounters mc = null;
	mc = mm.getMetricCounters(null);

	return (mc);
    }
}
