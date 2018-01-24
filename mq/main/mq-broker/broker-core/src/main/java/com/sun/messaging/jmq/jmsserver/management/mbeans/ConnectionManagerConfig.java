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
 * @(#)ConnectionManagerConfig.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.List;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;

public class ConnectionManagerConfig extends MQMBeanReadWrite  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ConnectionAttributes.NUM_CONNECTIONS,
					Integer.class.getName(),
					mbr.getString(mbr.I_CXN_MGR_ATTR_NUM_CONNECTIONS),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] destroySignature = {
	    new MBeanParameterInfo("connectionID", String.class.getName(), 
					mbr.getString(mbr.I_CXN_MGR_OP_DESTROY_PARAM_CXN_ID_DESC))
		        };

    /*
    private static MBeanParameterInfo[] destroyServiceSignature = {
	    new MBeanParameterInfo("serviceName", String.class.getName(), 
					"Service Name")
		        };
    */

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ConnectionOperations.DESTROY,
		    mbr.getString(mbr.I_CXN_MGR_OP_DESTROY_DESC),
		    destroySignature, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    /*
	    new MBeanOperationInfo("destroyConnectionsInService",
		    "Destroy all connections in the specified service",
		    destroyServiceSignature, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),
	    */

	    new MBeanOperationInfo(ConnectionOperations.GET_CONNECTIONS,
		    mbr.getString(mbr.I_CXN_MGR_CFG_OP_GET_CONNECTIONS_DESC),
		    null , 
		    ObjectName[].class.getName(),
		    MBeanOperationInfo.INFO)
		};

    public ConnectionManagerConfig()  {
	super();
    }

    public Integer getNumConnections()  {
	List connections = ConnectionUtil.getConnectionInfoList(null);

	return (Integer.valueOf(connections.size()));
    }

    public void destroy(String connectionID)  {
        if (connectionID == null)  {
            throw new
                IllegalArgumentException("Null connection ID specified");
        }

	long longCxnID = 0;

	try  {
            longCxnID = Long.parseLong(connectionID);
        } catch (NumberFormatException e)  {
            throw new
                IllegalArgumentException("Invalid connection ID specified: " + connectionID);
        }

	ConnectionUtil.destroyConnection(longCxnID, "Destroy operation invoked from " + getMBeanDescription());
    }

    public void destroyConnectionsInService(String serviceName)  {
        if (serviceName == null)  {
            throw new
                IllegalArgumentException("Null service name specified");
        }

	ConnectionUtil.destroyConnection(serviceName, 
		"Destroy operation invoked from " + getMBeanDescription());
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
		    MQObjectName.createConnectionConfig(Long.toString(cxnInfo.uuid));

	        oNames[i++] = o;
	    } catch (Exception e)  {
		handleOperationException(ConnectionOperations.GET_CONNECTIONS, e);
	    }
        }

	return (oNames);
    }

    public String getMBeanName()  {
	return ("ConnectionManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CXN_MGR_CFG_DESC));
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
