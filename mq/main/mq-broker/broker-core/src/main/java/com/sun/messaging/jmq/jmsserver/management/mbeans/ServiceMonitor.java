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
 * @(#)ServiceMonitor.java	1.19 06/28/07
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
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.management.util.ServiceUtil;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;

public class ServiceMonitor extends MQMBeanReadOnly  {
    private String service;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ServiceAttributes.MSG_BYTES_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_MSG_BYTES_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.MSG_BYTES_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_MSG_BYTES_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NAME,
					String.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_ACTIVE_THREADS,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_ACTIVE_THREADS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_CONNECTIONS,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_CONNECTIONS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_CONNECTIONS_OPENED,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_CONNECTIONS_OPENED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_CONNECTIONS_REJECTED,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_CONNECTIONS_REJECTED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_MSGS_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_MSGS_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_MSGS_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_MSGS_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_PKTS_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_PKTS_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_PKTS_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_PKTS_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_NUM_PRODUCERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.PORT,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_MON_ATTR_PORT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.PKT_BYTES_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_PKT_BYTES_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.PKT_BYTES_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_PKT_BYTES_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.STATE,
					Integer.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_STATE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ServiceAttributes.STATE_LABEL,
					String.class.getName(),
					mbr.getString(mbr.I_SVC_ATTR_STATE_LABEL),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ServiceOperations.GET_CONNECTIONS,
		mbr.getString(mbr.I_SVC_OP_GET_CONNECTIONS),
		null , 
		ObjectName[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ServiceOperations.GET_CONSUMER_IDS,
		mbr.getString(mbr.I_SVC_OP_GET_CONSUMER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ServiceOperations.GET_PRODUCER_IDS,
		mbr.getString(mbr.I_SVC_OP_GET_PRODUCER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO)
		    };

    private static String[] cxnNotificationTypes = {
		    ConnectionNotification.CONNECTION_OPEN,
		    ConnectionNotification.CONNECTION_CLOSE,
		    ConnectionNotification.CONNECTION_REJECT
		};

    private static String[] svcNotificationTypes = {
		    ServiceNotification.SERVICE_PAUSE,
		    ServiceNotification.SERVICE_RESUME
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    cxnNotificationTypes,
		    ConnectionNotification.class.getName(),
		    mbr.getString(mbr.I_CXN_NOTIFICATIONS)
		    ),

	    new MBeanNotificationInfo(
		    svcNotificationTypes,
		    ServiceNotification.class.getName(),
		    mbr.getString(mbr.I_SVC_NOTIFICATIONS)
		    )
		};

    private long numConnectionsOpened = 0;
    private long numConnectionsRejected = 0;

    public ServiceMonitor(String service)  {
	super();
	this.service = service;
    }

    public String getName()  {
	return (service);
    }

    public Integer getState()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	return (Integer.valueOf(ServiceUtil.toExternalServiceState(si.state)));
    }

    public String getStateLabel()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	return (ServiceState.toString(ServiceUtil.toExternalServiceState(si.state)));
    }

    public Integer getPort()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	return (Integer.valueOf(si.port));
    }

    public Integer getNumActiveThreads()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Integer.valueOf(metrics.threadsActive));
	} else  {
	    return (Integer.valueOf(-1));
	}
    }

    public Integer getNumConnections()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	return (Integer.valueOf(si.nConnections));
    }

    public long getNumConnectionsOpened()  {
	return (numConnectionsOpened);
    }

    public long getNumConnectionsRejected()  {
	return (numConnectionsRejected);
    }

    public Long getNumMsgsIn()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.messagesIn));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getNumMsgsOut()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.messagesOut));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getMsgBytesIn()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.messageBytesIn));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getMsgBytesOut()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.messageBytesOut));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getNumPktsIn()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.packetsIn));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getNumPktsOut()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.packetsOut));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getPktBytesIn()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.packetBytesIn));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Long getPktBytesOut()  {
	ServiceInfo si = ServiceUtil.getServiceInfo(service);
	MetricCounters metrics = si.metrics;
	if (metrics != null)  {
	    return (Long.valueOf(metrics.packetBytesOut));
	} else  {
	    return (Long.valueOf(-1));
	}
    }

    public Integer getNumConsumers()  {
	List consumerIDs = ServiceUtil.getConsumerIDs(service);

	if (consumerIDs == null)  {
	    return (Integer.valueOf(0));
	}

	return (Integer.valueOf(consumerIDs.size()));
    }

    public String[] getConsumerIDs() throws MBeanException  {
	List consumerIDs = ServiceUtil.getConsumerIDs(service);
	String ids[];

	if ((consumerIDs == null) || (consumerIDs.size() == 0))  {
	    return (null);
	}

	ids = new String[ consumerIDs.size() ];

	Iterator iter = consumerIDs.iterator();

	int i = 0;
	while (iter.hasNext()) {
	    ConsumerUID cid = (ConsumerUID)iter.next();
	    long conID = cid.longValue();
	    String id;

	    try  {
                id = Long.toString(conID);

                ids[i] = id;
            } catch (Exception ex)  {
		handleOperationException(ServiceOperations.GET_CONSUMER_IDS, ex);
    	    }

	    i++;
	}

	return (ids);
    }

    public Integer getNumProducers()  {
	List producerIDs = ServiceUtil.getProducerIDs(service);

	if (producerIDs == null)  {
	    return (Integer.valueOf(0));
	}

	return (Integer.valueOf(producerIDs.size()));
    }

    public String[] getProducerIDs() throws MBeanException  {
	List producerIDs = ServiceUtil.getProducerIDs(service);
	String ids[];

	if ((producerIDs == null) || (producerIDs.size() == 0))  {
	    return (null);
	}

	ids = new String[ producerIDs.size() ];

	Iterator iter = producerIDs.iterator();

	int i = 0;
	while (iter.hasNext()) {
	    ProducerUID pid = (ProducerUID)iter.next();
	    long prdID = pid.longValue();
	    String id;

	    try  {
                id = Long.toString(prdID);

                ids[i] = id;
            } catch (Exception ex)  {
		handleOperationException(ServiceOperations.GET_PRODUCER_IDS, ex);
    	    }

	    i++;
	}

	return (ids);
    }

    public ObjectName[] getConnections() throws MBeanException  {
	List connections = ConnectionUtil.getConnectionInfoList(service);

	if (connections.size() == 0)  {
	    return (null);
	}

	ObjectName oNames[] = new ObjectName [ connections.size() ];

	Iterator itr = connections.iterator();
	int i = 0;
	while (itr.hasNext()) {
	    ConnectionInfo cxnInfo = (ConnectionInfo)itr.next();
	    try  {
	        ObjectName o = 
		    MQObjectName.createConnectionMonitor(Long.toString(cxnInfo.uuid));

	        oNames[i++] = o;
	    } catch (Exception e)  {
		handleOperationException(ServiceOperations.GET_CONNECTIONS, e);
	    }
        }

	return (oNames);
    }

    public void resetMetrics()  {
        numConnectionsOpened = 0;
        numConnectionsRejected = 0;
    }

    public String getMBeanName()  {
	return ("ServiceMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_SVC_MON_DESC));
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

    public void notifyConnectionClose(long id)  {
	ConnectionNotification n;
	n = new ConnectionNotification(ConnectionNotification.CONNECTION_CLOSE, 
			this, sequenceNumber++);
	n.setConnectionID(Long.toString(id));

	sendNotification(n);
    }

    public void notifyConnectionOpen(long id)  {
	ConnectionNotification n;
	n = new ConnectionNotification(ConnectionNotification.CONNECTION_OPEN, 
			this, sequenceNumber++);
	n.setConnectionID(Long.toString(id));

	sendNotification(n);
        numConnectionsOpened++;
    }

    public void notifyConnectionReject(String serviceName, String userName,
				String remoteHostString)  {
	ConnectionNotification cn;
	cn = new ConnectionNotification(ConnectionNotification.CONNECTION_REJECT, 
			this, sequenceNumber++);
	cn.setServiceName(serviceName);
	cn.setUserName(userName);
	cn.setRemoteHost(remoteHostString);

	sendNotification(cn);
        numConnectionsRejected++;
    }

    public void notifyServicePause()  {
	ServiceNotification n;
	n = new ServiceNotification(ServiceNotification.SERVICE_PAUSE, 
			this, sequenceNumber++);
	n.setServiceName(getName());

	sendNotification(n);
    }

    public void notifyServiceResume()  {
	ServiceNotification n;
	n = new ServiceNotification(ServiceNotification.SERVICE_RESUME, 
			this, sequenceNumber++);
	n.setServiceName(getName());

	sendNotification(n);
    }
}
