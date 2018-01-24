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
 * @(#)ConnectionUtil.java	1.12 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.util.admin.MessageType;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;

import com.sun.messaging.jmq.jmsserver.service.Service;

public class ConnectionUtil {
    /**
     * Returns a List of IMQConnection
     */
    public static List getConnections()  {
	List connections = getConnections(null);

	return (connections);
    }

    /**
     * Returns a List of IMQConnection for a given service
     */
    public static List getConnections(String service)  {
	ConnectionManager cm = Globals.getConnectionManager();
	List connections = null;

	try  {
	    Service s = null;

	    if (service != null)  {
	        s = Globals.getServiceManager().getService(service);

		/*
		 * If service object is null, service may not exist or is inactive
		 */
		if (s == null)  {
		    return (connections);
		}
	    }

	    connections = cm.getConnectionList(s);
	} catch(Exception e)  {
            BrokerResources	rb = Globals.getBrokerResources();
	    Logger logger = Globals.getLogger();

            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_FAILED_TO_OBTAIN_CONNECTION_LIST),
		e);
	}

	return (connections);
    }

    /**
     * Returns a List of ConnectionInfo for the given service
     * or all services if the passed service is null.
     */
    public static List getConnectionInfoList(String service)  {
	ConnectionManager cm = Globals.getConnectionManager();
	List connections, connectionInfoList = new ArrayList();
	IMQConnection  cxn;
	ConnectionInfo cxnInfo;

	try  {
	    Service s = null;

	    if (service != null)  {
	        s = Globals.getServiceManager().getService(service);

		/*
		 * If service object is null, service may not exist or is inactive
		 */
		if (s == null)  {
		    return (connectionInfoList);
		}
	    }

	    connections = cm.getConnectionList(s);
	} catch(Exception e)  {
            BrokerResources	rb = Globals.getBrokerResources();
	    Logger logger = Globals.getLogger();

            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_FAILED_TO_OBTAIN_CONNECTION_LIST),
		e);

	    return (connectionInfoList);
	}

	if (connections.size() == 0)  {
	    return (connectionInfoList);
	}

        Iterator iter = connections.iterator();

        while (iter.hasNext()) {
	    cxn     = (IMQConnection)iter.next();
	    cxnInfo = cxn.getConnectionInfo();

	    connectionInfoList.add(cxnInfo);
        }

	return (connectionInfoList);
    }

    /**
     * Returns the ConnectionInfo for the passed connection ID.
     */
    public static ConnectionInfo getConnectionInfo(long id)  {
	ConnectionManager cm = Globals.getConnectionManager();
	ConnectionInfo cxnInfo = null;
	IMQConnection  cxn = null;

	cxn = (IMQConnection)cm.getConnection(new ConnectionUID(id));

	if (cxn == null)  {
	    return (null);
	}

	cxnInfo = cxn.getConnectionInfo();

	return (cxnInfo);
    }

    public static String getServiceOfConnection(long id)  {
	ConnectionInfo cxnInfo = getConnectionInfo(id);

	if (cxnInfo == null)  {
	    return (null);
	}

	return(cxnInfo.service);
    }

    public static Long getCreationTime(long cxnId)  {
	long currentTime = System.currentTimeMillis();
	ConnectionUID cxnUID = new ConnectionUID(cxnId);

	return (Long.valueOf(currentTime - cxnUID.age(currentTime)));
    }


    public static List getConsumerIDs(long cxnId)  {
	ConnectionManager	cm = Globals.getConnectionManager();
	ConnectionInfo		cxnInfo = null;
	IMQConnection		cxn = null;
	List			consumerIDs;

	cxn = (IMQConnection)cm.getConnection(new ConnectionUID(cxnId));
	consumerIDs = cxn.getConsumersIDs();

	return (consumerIDs);
    }

    public static List getProducerIDs(long cxnId)  {
	ConnectionManager	cm = Globals.getConnectionManager();
	ConnectionInfo		cxnInfo = null;
	IMQConnection		cxn = null;
	List			producerIDs;

	cxn = (IMQConnection)cm.getConnection(new ConnectionUID(cxnId));
	producerIDs = cxn.getProducerIDs();

	return (producerIDs);
    }

    public static void destroyConnection(long cxnId, String reasonString)  {
	ConnectionManager	cm = Globals.getConnectionManager();
	IMQConnection		cxn = null;

	cxn = (IMQConnection)cm.getConnection(new ConnectionUID(cxnId));

	if (cxn != null)  {
	    cxn.destroyConnection(true, GoodbyeReason.ADMIN_KILLED_CON,
				    reasonString);
	}
    }

    public static void destroyConnection(String serviceName, String reasonString)  {
	List			cxnList = getConnections(serviceName);
	IMQConnection		cxn = null;

	/*
	 * Return if no connections to destroy
	 */
	if ((cxnList == null) || (cxnList.size() == 0))  {
	    return;
	}

        Iterator iter = cxnList.iterator();

        while (iter.hasNext()) {
	    cxn     = (IMQConnection)iter.next();

	    if (cxn != null)  {
	        cxn.destroyConnection(true, GoodbyeReason.ADMIN_KILLED_CON,
				    reasonString);
	    }
        }
    }
}
