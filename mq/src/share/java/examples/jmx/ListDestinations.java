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
import com.sun.messaging.jms.management.server.DestinationAttributes;
import com.sun.messaging.jms.management.server.DestinationOperations;

public class ListDestinations {
    public static void main(String[] args) {
	try  {
	    AdminConnectionFactory acf;

	    /*
	     * Create admin connection factory and connect to JMX Connector
	     * server using administrator username/password.
	     * A JMX connector client object is obtained from this.
	     */
	    acf = new AdminConnectionFactory();
	    JMXConnector jmxc = acf.createConnection("admin", "admin");

	    /*
	     * Get MBeanServer interface.
	     */
	    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

	    /*
	     * Create object name of destination monitor mgr MBean.
	     */
	    ObjectName objName
		= new ObjectName(MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME);

	    ObjectName destinationObjNames[] = 
                (ObjectName[])mbsc.invoke(objName, DestinationOperations.GET_DESTINATIONS, null, null);

            System.out.println("Listing destinations:" );
	    for (int i = 0; i < destinationObjNames.length; ++i)  {
		ObjectName oneDestObjName = destinationObjNames[i];
		System.out.println("\tName: " + 
		    mbsc.getAttribute(oneDestObjName, DestinationAttributes.NAME));
		System.out.println("\tType: " + 
		    mbsc.getAttribute(oneDestObjName, DestinationAttributes.TYPE));
		System.out.println("\tState: " + 
		    mbsc.getAttribute(oneDestObjName, DestinationAttributes.STATE_LABEL));

		System.out.println("");
	    }

	    jmxc.close();
	} catch (Exception e)  {
	    e.printStackTrace();
	}
    }
}
