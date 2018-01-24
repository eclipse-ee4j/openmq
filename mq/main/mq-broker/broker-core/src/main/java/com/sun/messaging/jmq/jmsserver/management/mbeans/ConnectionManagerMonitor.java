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
 * @(#)ConnectionManagerMonitor.java	1.16 06/28/07
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
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;
import com.sun.messaging.jmq.jmsserver.Globals;

public class ConnectionManagerMonitor extends MQMBeanReadOnly {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ConnectionAttributes.NUM_CONNECTIONS,
					Integer.class.getName(),
					mbr.getString(mbr.I_CXN_MGR_ATTR_NUM_CONNECTIONS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.NUM_CONNECTIONS_OPENED,
					Long.class.getName(),
					mbr.getString(mbr.I_CXN_MGR_ATTR_NUM_CONNECTIONS_OPENED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ConnectionAttributes.NUM_CONNECTIONS_REJECTED,
					Long.class.getName(),
					mbr.getString(mbr.I_CXN_MGR_ATTR_NUM_CONNECTIONS_REJECTED),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ConnectionOperations.GET_CONNECTIONS,
		mbr.getString(mbr.I_CXN_MGR_MON_OP_GET_CONNECTIONS_DESC),
		null,
		ObjectName[].class.getName(),
		MBeanOperationInfo.INFO)
		};
	

    private static String[] cxnNotificationTypes = {
		    ConnectionNotification.CONNECTION_OPEN,
		    ConnectionNotification.CONNECTION_CLOSE,
		    ConnectionNotification.CONNECTION_REJECT
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    cxnNotificationTypes,
		    ConnectionNotification.class.getName(),
		    mbr.getString(mbr.I_CXN_NOTIFICATIONS)
		    )
		};

    private long numConnectionsOpened = 0;
    private long numConnectionsRejected = 0;

    public ConnectionManagerMonitor()  {
        super();
    }

    public Integer getNumConnections()  {
	List connections = ConnectionUtil.getConnectionInfoList(null);

	return (Integer.valueOf(connections.size()));
    }

    public long getNumConnectionsOpened()  {
	return (numConnectionsOpened);
    }

    public long getNumConnectionsRejected()  {
	return (numConnectionsRejected);
    }

    public void resetMetrics()  {
        numConnectionsOpened = 0;
        numConnectionsRejected = 0;
    }

    public ObjectName[] getConnections() throws MBeanException  {
	List connections = ConnectionUtil.getConnectionInfoList(null);

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
		handleOperationException(ConnectionOperations.GET_CONNECTIONS, e);
	    }
        }

	return (oNames);
    }

    public String getMBeanName()  {
	return ("ConnectionManagerMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CXN_MGR_MON_DESC));
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

    public void notifyConnectionOpen(long id)  {
	ConnectionNotification cn;
	cn = new ConnectionNotification(ConnectionNotification.CONNECTION_OPEN, 
			this, sequenceNumber++);
	cn.setConnectionID(Long.toString(id));

	sendNotification(cn);
        numConnectionsOpened++;
    }

    public void notifyConnectionClose(long id)  {
	ConnectionNotification cn;
	cn = new ConnectionNotification(ConnectionNotification.CONNECTION_CLOSE, 
			this, sequenceNumber++);
	cn.setConnectionID(Long.toString(id));

	sendNotification(cn);
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

}
