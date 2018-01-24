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
 * @(#)MQObjectName.java	1.14 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;

/**
 * Utility class for manipulating Message Queue MBean Object Names.
 */
public class MQObjectName {

    /*
     ****************************
     * Start of private constants
     ****************************
     */

    /*
     * Domain name for MQ MBeans
     */
    private static final String MBEAN_DOMAIN_NAME = "com.sun.messaging.jms.server";

    /*
     * MBean names
     */
    private static final String BROKER			= "Broker";
    private static final String SERVICE_MANAGER		= "ServiceManager";
    private static final String CONNECTION_MANAGER	= "ConnectionManager";
    private static final String DESTINATION_MANAGER	= "DestinationManager";
    private static final String CONSUMER_MANAGER	= "ConsumerManager";
    private static final String PRODUCER_MANAGER	= "ProducerManager";
    private static final String TRANSACTION_MANAGER	= "TransactionManager";
    private static final String SERVICE			= "Service";
    private static final String DESTINATION		= "Destination";
    private static final String CONNECTION		= "Connection";
    private static final String CLUSTER			= "Cluster";
    private static final String LOG			= "Log";
    private static final String JVM			= "JVM";

    /*
     * Strings that represent 'partial' object names. The complete
     * object name is created by appending additional name/value pairs.
     * These constants are used by the utility methods in this class.
     */

    /*
     * These strings are used to specify (via the subtype key) if the object
     * name is for a config or monitor MBean.
     */
    private static final String SUBTYPE_SUFFIX_CONFIG	= ",subtype=Config";
    private static final String SUBTYPE_SUFFIX_MONITOR	= ",subtype=Monitor";

    /**
     * The domain name and the type key property in the ObjectName for a 
     * ServiceConfig MBean. The unique ObjectName for a ServiceConfig MBean can be formed 
     * by appending this string with ",name=<EM>service name</EM>".
     */
    private static final String SERVICE_CONFIG_DOMAIN_TYPE
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + SERVICE 
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * The domain name and the type key property in the ObjectName for a 
     * DestinationConfig MBean. The unique ObjectName for a DestinationConfig MBean can 
     * be formed by appending this string with ",desttype=<EM>destination type</EM>,
     * name=<EM>destination name</EM>".
     */
    private static final String DESTINATION_CONFIG_DOMAIN_TYPE 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + DESTINATION 
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * The domain name and the type key property in the ObjectName for a 
     * ConnectionConfig MBean. The unique ObjectName for a ConnectionConfig 
     * MBean can be formed by appending this string with 
     * ",id=<EM>connection id</EM>".
     */
    private static final String CONNECTION_CONFIG_DOMAIN_TYPE 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONNECTION 
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * The domain name and the type key property in the ObjectName for a 
     * ServiceMonitor MBean. The unique ObjectName for a ServiceMonitor MBean 
     * can be formed by appending this string with ",name=<EM>service name</EM>".
     */
    private static final String SERVICE_MONITOR_DOMAIN_TYPE
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + SERVICE
			        + SUBTYPE_SUFFIX_MONITOR;

    /**
     * The domain name and the type key property in the ObjectName for a 
     * DestinationMonitor MBean. The unique ObjectName for a DestinationMonitor 
     * MBean can be formed by appending this string with 
     * ",desttype=<EM>destination type</EM>, name=<EM>destination name</EM>".
     */
    private static final String DESTINATION_MONITOR_DOMAIN_TYPE 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + DESTINATION
			        + SUBTYPE_SUFFIX_MONITOR;

    /**
     * The domain name and the type key property in the ObjectName for a 
     * ConnectionMonitor MBean. The unique ObjectName for a ConnectionMonitor 
     * MBean can * be formed by appending this string with 
     * ",id=<EM>connection id</EM>".
     */
    private static final String CONNECTION_MONITOR_DOMAIN_TYPE 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONNECTION
				+ SUBTYPE_SUFFIX_MONITOR;

    /*
     **************************
     * End of private constants
     **************************
     */


    /*
     ***************************
     * Start of public constants
     ***************************
     */

    /**
     * String representation of the ObjectName for the Broker Config MBean.
     */
    public static final String BROKER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + BROKER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the 
     * ConnectionManager Config MBean.
     */
    public static final String CONNECTION_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONNECTION_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the 
     * ConsumerManager Config MBean.
     */
    public static final String CONSUMER_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONSUMER_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the 
     * ServiceManager Config MBean.
     */
    public static final String SERVICE_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + SERVICE_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the DestinationManager Config MBean.
     */
    public static final String DESTINATION_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + DESTINATION_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the Cluster Config MBean.
     */
    public static final String CLUSTER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CLUSTER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the Log Config MBean.
     */
    public static final String LOG_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + LOG
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the 
     * ProducerManager Config MBean.
     */
    public static final String PRODUCER_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + PRODUCER_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the 
     * TransactionManager Config MBean.
     */
    public static final String TRANSACTION_MANAGER_CONFIG_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + TRANSACTION_MANAGER
				+ SUBTYPE_SUFFIX_CONFIG;

    /**
     * String representation of the ObjectName for the Broker Monitor MBean.
     */
    public static final String BROKER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + BROKER 
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the ServiceManager 
     * Monitor MBean.
     */
    public static final String SERVICE_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + SERVICE_MANAGER 
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the DestinationManager 
     * Monitor MBean.
     */
    public static final String DESTINATION_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + DESTINATION_MANAGER
				+ SUBTYPE_SUFFIX_MONITOR;
    /**
     * String representation of the ObjectName for the TransactionManager 
     * Monitor MBean.
     */
    public static final String TRANSACTION_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + TRANSACTION_MANAGER
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the ConnectionManager 
     * Monitor MBean.
     */
    public static final String CONNECTION_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONNECTION_MANAGER
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the ConsumerManager 
     * Monitor MBean.
     */
    public static final String CONSUMER_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CONSUMER_MANAGER
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the ProducerManager 
     * Monitor MBean.
     */
    public static final String PRODUCER_MANAGER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + PRODUCER_MANAGER
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the JVM Monitor MBean.
     */
    public static final String JVM_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + JVM
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the Cluster Monitor MBean.
     */
    public static final String CLUSTER_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + CLUSTER
				+ SUBTYPE_SUFFIX_MONITOR;

    /**
     * String representation of the ObjectName for the Log Monitor MBean.
     */
    public static final String LOG_MONITOR_MBEAN_NAME 
    			= MBEAN_DOMAIN_NAME 
				+ ":type=" + LOG
				+ SUBTYPE_SUFFIX_MONITOR;

    /*
     *************************
     * End of public constants
     *************************
     */

    private MQObjectName()  {
    }

    /**
     * Creates ObjectName for service configuration MBean.
     *
     * @param serviceName Name of service.
     * @return ObjectName of Service MBean
     */
    public static ObjectName createServiceConfig(String serviceName)  
				throws MalformedObjectNameException,
					NullPointerException  {
	String s = SERVICE_CONFIG_DOMAIN_TYPE
			+ ",name="
			+ serviceName;

	ObjectName o = new ObjectName(s);

	return (o);
    }
    /**
     * Creates ObjectName for service monitoring MBean.
     *
     * @param serviceName Name of service.
     * @return ObjectName of Service MBean
     */
    public static ObjectName createServiceMonitor(String serviceName)  
				throws MalformedObjectNameException,
					NullPointerException  {

	String s = SERVICE_MONITOR_DOMAIN_TYPE
			+ ",name="
			+ serviceName;

	ObjectName o = new ObjectName(s);
	
	return (o);
    }

    /**
     * Creates ObjectName for destination configuration MBean.
     *
     * @param destinationType Type of destination. One of 
     * DestinationType.TOPIC, DestinationType.QUEUE.
     * @param destinationName Name of destination.
     * @return ObjectName of service MBean
     */
    public static ObjectName createDestinationConfig(String destinationType,
					String destinationName)  
				throws MalformedObjectNameException,
					NullPointerException  {
	String s = DESTINATION_CONFIG_DOMAIN_TYPE
			+ ",desttype="
			+ destinationType
			+ ",name="
			+ ObjectName.quote(destinationName);

	ObjectName o = new ObjectName(s);
	
	return (o);
    }

    /**
     * Creates ObjectName for specified destination monitor MBean.
     *
     * @param destinationType Type of destination. One of 
     * DestinationType.TOPIC, DestinationType.QUEUE.
     * @param destinationName Name of destination.
     * @return ObjectName of DestinationMonitor MBean
     */
    public static ObjectName createDestinationMonitor(String destinationType,
					String destinationName)  
				throws MalformedObjectNameException,
					NullPointerException  {
	String s = DESTINATION_MONITOR_DOMAIN_TYPE
			+ ",desttype="
			+ destinationType
			+ ",name="
			+ ObjectName.quote(destinationName);

	ObjectName o = new ObjectName(s);
	
	return (o);
    }

    /**
     * Creates ObjectName for specified connection configuration MBean.
     *
     * @param id Connection ID
     * @return ObjectName of ConnectionConfig MBean
     */
    public static ObjectName createConnectionConfig(String id)  {
	String s = CONNECTION_CONFIG_DOMAIN_TYPE
			+ ",id="
			+ id;

	ObjectName o = null;
	try  {
	    o = new ObjectName(s);
	} catch (MalformedObjectNameException mfe) {
	    /*
	     * Should not get here
	     */
	    
	    throw new RuntimeException("Failed to create Message Queue object name",
					mfe);
	}

	return (o);
    }

    /**
     * Creates ObjectName for specified connection monitoring MBean.
     *
     * @param id Connection ID
     * @return ObjectName of ConnectionMonitor MBean
     */
    public static ObjectName createConnectionMonitor(String id)  {
	String s = CONNECTION_MONITOR_DOMAIN_TYPE
			+ ",id="
			+ id;

	ObjectName o = null;
	try  {
	    o = new ObjectName(s);
	} catch (MalformedObjectNameException mfe) {
	    /*
	     * Should not get here
	     */
	    
	    throw new RuntimeException("Failed to create Message Queue object name",
					mfe);
	}

	return (o);
    }
}
