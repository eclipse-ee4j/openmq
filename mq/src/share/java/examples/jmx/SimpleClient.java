/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import javax.management.*;
import javax.management.remote.*;
import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.messaging.jms.management.server.BrokerAttributes;

public class SimpleClient {
    public static void main(String[] args) {
	try  {
	    AdminConnectionFactory acf;

	    /*
	     * Create admin connection factory and connect to JMX Connector
	     * server using administrator username/password.
	     * A JMX connector client object is obtained from this.
	     */
	    acf = new AdminConnectionFactory();
	    JMXConnector jmxc = acf.createConnection("admin","admin");

	    /*
	     * Get MBeanServer interface.
	     */
	    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

	    /*
	     * Create object name of broker config MBean.
	     */
	    ObjectName objName
		= new ObjectName(MQObjectName.BROKER_MONITOR_MBEAN_NAME);

	    /*
	     * Get attributes:
	     *  InstanceName
	     *  Version
	     */
	    System.out.println("Broker Instance Name = " +
			   mbsc.getAttribute(objName, BrokerAttributes.INSTANCE_NAME));
	    System.out.println("Broker Version = " +
			   mbsc.getAttribute(objName, BrokerAttributes.VERSION));

	    jmxc.close();
	} catch (Exception e)  {
	    e.printStackTrace();
	}
    }
}
