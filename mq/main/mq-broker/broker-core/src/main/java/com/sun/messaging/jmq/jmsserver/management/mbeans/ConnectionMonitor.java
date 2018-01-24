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
 * @(#)ConnectionMonitor.java	1.19 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.HashMap;
import java.util.Properties;
import java.util.List;
import java.util.Iterator;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.net.IPAddress;

import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;
import com.sun.messaging.jmq.jmsserver.management.util.DestinationUtil;

public class ConnectionMonitor extends MQMBeanReadOnly  {
    private long id;
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ConnectionAttributes.CLIENT_ID,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_CLIENT_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.CLIENT_PLATFORM,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_CLIENT_PLATFORM),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.CONNECTION_ID,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_CXN_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.CREATION_TIME,
					Long.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_CXN_CREATION_TIME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.HOST,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_HOST),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_NUM_CONSUMERS),
					true,
					false,
					false),
	    new MBeanAttributeInfo(ConnectionAttributes.NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_NUM_PRODUCERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.PORT,
					Integer.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_PORT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.SERVICE_NAME,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_SERVICE_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.USER,
					String.class.getName(),
					mbr.getString(mbr.I_CXN_ATTR_USER),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ConnectionOperations.GET_CONSUMER_IDS,
		mbr.getString(mbr.I_CXN_OP_GET_CONSUMER_IDS_DESC),
		    null , 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ConnectionOperations.GET_PRODUCER_IDS,
		mbr.getString(mbr.I_CXN_OP_GET_PRODUCER_IDS_DESC),
		    null , 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ConnectionOperations.GET_SERVICE,
		mbr.getString(mbr.I_CXN_OP_GET_SERVICE_DESC),
		    null , 
		    ObjectName.class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ConnectionOperations.GET_TEMP_DESTINATIONS,
		mbr.getString(mbr.I_CXN_OP_GET_TEMP_DESTINATIONS_DESC),
		    null , 
		    ObjectName[].class.getName(),
		    MBeanOperationInfo.INFO)
		};
	
    public ConnectionMonitor(long id)  {
	super();
	this.id = id;
    }

    public String getClientID()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (cxnInfo.clientID);
    }

    public String getClientPlatform()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (cxnInfo.userAgent);
    }

    public String getConnectionID()  {
	return (Long.toString(id));
    }

    public String getHost()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);
	String host = null;

	if (cxnInfo.remoteIP != null) {
            host = String.valueOf(
		IPAddress.rawIPToString(cxnInfo.remoteIP, true, true));
        }

	return (host);
    }

    public Integer getNumConsumers()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (Integer.valueOf(cxnInfo.nconsumers));
    }

    public Integer getNumProducers()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (Integer.valueOf(cxnInfo.nproducers));
    }

    public Integer getPort()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (Integer.valueOf(cxnInfo.remPort));
    }

    public ObjectName getService() throws MBeanException  {
	String serviceName = ConnectionUtil.getServiceOfConnection(id);
	ObjectName oName = null;

	try  {
	    oName = MQObjectName.createServiceMonitor(serviceName);
        } catch (Exception e)  {
	    handleOperationException(ConnectionOperations.GET_SERVICE, e);
        }

	return (oName);
    }

    public Long getCreationTime()  {
	long ts = ConnectionUtil.getCreationTime(id);
	return (Long.valueOf(ts));
    }

    public String getServiceName()  {
	String serviceName = ConnectionUtil.getServiceOfConnection(id);

	return (serviceName);
    }

    public String getUser()  {
	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(id);

	return (cxnInfo.user);
    }

    public String[] getConsumerIDs() throws MBeanException  {
	List consumerIDs = ConnectionUtil.getConsumerIDs(id);
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
	        handleOperationException(ConnectionOperations.GET_CONSUMER_IDS, ex);
    	    }

	    i++;
	}

	return (ids);
    }

    public String[] getProducerIDs() throws MBeanException  {
	List producerIDs = ConnectionUtil.getProducerIDs(id);
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
	        handleOperationException(ConnectionOperations.GET_PRODUCER_IDS, ex);
    	    }

	    i++;
	}

	return (ids);
    }

    public ObjectName[] getTemporaryDestinations() throws MBeanException  {
	List dests = DestinationUtil.getVisibleTemporaryDestinations(id);

	if (dests.size() == 0)  {
	    return (null);
	}

	ObjectName destONames[] = new ObjectName [ dests.size() ];

	for (int i =0; i < dests.size(); i ++) {
	    Destination d = (Destination)dests.get(i);

	    try  {
	        ObjectName o = MQObjectName.createDestinationMonitor(
				d.isQueue() ? DestinationType.QUEUE : DestinationType.TOPIC,
				d.getDestinationName());

	        destONames[i] = o;
	    } catch (Exception e)  {
		handleOperationException(ConnectionOperations.GET_TEMP_DESTINATIONS, e);
	    }
        }

	return (destONames);
    }

    public String getMBeanName()  {
	return ("ConnectionMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CXN_MON_DESC));
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
