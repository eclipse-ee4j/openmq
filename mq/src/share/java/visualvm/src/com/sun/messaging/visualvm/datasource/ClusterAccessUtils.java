/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.visualvm.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.io.PortMapperTable;
import com.sun.messaging.jms.management.server.BrokerAttributes;
import com.sun.messaging.jms.management.server.BrokerClusterInfo;
import com.sun.messaging.jms.management.server.ClusterAttributes;
import com.sun.messaging.jms.management.server.ClusterOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.tools.jmx.JmxModel;
import com.sun.tools.visualvm.tools.jmx.JmxModelFactory;

public class ClusterAccessUtils {

	/**
	 * Return the JMX URL for the broker with the specified brokerAddress. This
	 * is obtained by connecting to the port mapper.
	 * 
	 * @param brokerAddress
	 * @return The JMX URL, or null if it cannot be obtained for some reason
	 *         (see the VisualVM log for details)
	 */
	public static String getBrokerJMXURL(String brokerAddress) {

		// parse the specified brokerAddress to obtain its host and port
		String host = getHost(brokerAddress);
		int port = getPort(brokerAddress);
		
		return getBrokerJMXURL(host, port);

	}
	
	/**
	 * Parse the specified brokerAddress (which is of the form host:port) and return the host
	 * @param brokerAddress
	 * @return
	 */
	public static String getHost(String brokerAddress){
		int colonPosition = brokerAddress.indexOf(":");
		String host = brokerAddress.substring(0, colonPosition);
		return host;
	}
	
	/**
	 * Parse the specified brokerAddress (which is of the form host:port) and return the host
	 * @param brokerAddress
	 * @return
	 */
	public static int getPort(String brokerAddress){
		int colonPosition = brokerAddress.indexOf(":");
		String portString = brokerAddress.substring(colonPosition + 1);
		int port = Integer.valueOf(portString).intValue();
		return port;
	}

	/**
	 * Return the JMX URL for the broker represented by the specified
	 * application. This is obtained by using the JMX API to obtain the broker's
	 * host and port, and then using this info to connect to the port mapper.
	 * 
	 * @param app
	 * @return The JMX URL, or null if it cannot be obtained for some reason
	 *         (see the VisualVM log for details)
	 */
	public static String getBrokerJMXURL(Application app) {
		String[] hostAndPort = getBrokerHostAndPort(app);
		return getBrokerJMXURL(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
	}

	/**
	 * Return the address of the specified address in the form host:port This is
	 * obtained by using the JMX API to obtain the broker's host and port,
	 * 
	 * @param app
	 * @return
	 */
	public static String getBrokerHostPort(Application app) {
		String[] hostAndPort = getBrokerHostAndPort(app);
		return hostAndPort[0] + ":" + Integer.parseInt(hostAndPort[1]);
	}
	
	/**
	 * Return a String of the form <ipAddress>:<port>
	 * corresponding to the specified <host>:>port>
	 * @param hostPort
	 * @return
	 */
	public static String getIPPort(String hostPort){
		String result = hostPort;
		String host = getHost(hostPort);
		int port = getPort(hostPort);
		try {
			InetAddress hostIP[] = InetAddress.getAllByName(host);
			result = hostIP[0].getHostAddress()+":"+port;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * return Whether the specified host:port corresponds to the local machine
	 * @param brokerAddress
	 * @return
	 */
	public static boolean isLocal(String brokerAddress) {

		String otherIP = getHost(getIPPort(brokerAddress));
	    InetAddress localHost;
		try {
			localHost = InetAddress.getLocalHost();
			InetAddress inetAddresses[] = InetAddress.getAllByName(localHost.getHostName());
			for (int i = 0; i < inetAddresses.length; i++) {
				InetAddress thisInetAddr = inetAddresses[i];
				String thisIP = thisInetAddr.getHostAddress();
				if ( thisIP.equals(otherIP)) {
					return true;
				}

			}
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			return true;
		}
		return false;
	}

	/**
	 * Return the JMX URL for the broker with the specified host and port. This
	 * is obtained by connecting to the port mapper.
	 * 
	 * @param host
	 * @param port
	 * @return The JMX URL, or null if it cannot be obtained for some reason
	 *         (see the VisualVM log for details)
	 */
	public static String getBrokerJMXURL(String host, int port) {

		String jmxURLString = null;

		Socket socket = null;

		try {

			// connect with no timeout
			// TODO Is timeout needed here?
			socket = new Socket(host, port);

			String version = String.valueOf(PortMapperTable.PORTMAPPER_VERSION) + "\n";

			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			// Write version of portmapper we support to broker
			try {
				os.write(version.getBytes());
				os.flush();
			} catch (IOException e) {
				// This can sometimes fail if the server already wrote
				// the port table and closed the connection
			}

			PortMapperTable portMapperTable = new PortMapperTable();
			portMapperTable.read(is);

			for (Object thisObject : portMapperTable.getServices().values()) {
				PortMapperEntry thisEntry = (PortMapperEntry) thisObject;
				if (thisEntry.getType().equals("JMX")) {
					jmxURLString = thisEntry.getProperty("url");
					break;
				}
			}

			is.close();
		} catch (IOException e) {
                    // don't log an error - the broker may not be running and that is not an error
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return jmxURLString;

	}

	/**
	 * Return information about the brokers that the specified broker thinks are
	 * in its cluster
	 * 
	 * This is obtained by querying its Cluster Monitor MBean
	 * 
	 * @param app
	 * @return A List, each of whose entries is a Map representing a broker in
	 *         the cluster. That map has two entries. The key
	 *         BrokerClusterInfo.ID is mapped to the broker id The key
	 *         BrokerClusterInfo.ADDRESS is mapped to the broker address
	 */
	static List<Map<String, String>> getClusteredBrokerInfo(Application app) {

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		List<Map<String, String>> result = new ArrayList<Map<String, String>>();

		if (mbsc != null) {
			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.CLUSTER_MONITOR_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
			} catch (NullPointerException e) {
			}

			CompositeData[] clusteredBrokerData = null;
			try {
				clusteredBrokerData = (CompositeData[]) mbsc.invoke(objName, ClusterOperations.GET_BROKER_INFO, null,
						null);
			} catch (InstanceNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (MBeanException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ReflectionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (clusteredBrokerData != null) {
				for (CompositeData compositeDatas : clusteredBrokerData) {
					Map<String, String> thisEntry = new HashMap<String, String>();
					String id = (String) compositeDatas.get(BrokerClusterInfo.ID);
					String address = (String) compositeDatas.get(BrokerClusterInfo.ADDRESS);
					thisEntry.put(BrokerClusterInfo.ID, id);
					thisEntry.put(BrokerClusterInfo.ADDRESS, address);
					result.add(thisEntry);
				}
			}

		}
		return result;

	}

	public static MBeanServerConnection getMBeanServerConnection(Application app) {
		MBeanServerConnection mbsc = null;
		JmxModel jmxModel1 = JmxModelFactory.getJmxModelFor(app);

		if ((jmxModel1 != null) && (jmxModel1.getConnectionState() == JmxModel.ConnectionState.CONNECTED)) {
			mbsc = jmxModel1.getMBeanServerConnection();
		}
		return mbsc;
	}

	/**
	 * Return a URL (e.g. "mq://foo.sun.com:1234") that can be used to connect
	 * to the broker, using the host and port returned the broker's JMX API
	 * 
	 * @param app
	 * @return
	 */
	static String getBrokerURL(Application app) {

		String[] hostAndPort = getBrokerHostAndPort(app);

		return "mq://" + hostAndPort[0] + ":" + hostAndPort[1].toString();

	}

	/**
	 * Return the hostname and port of the specified broker application, using
	 * the info return from the broker's JMX API
	 * 
	 * @param app
	 * @return A String[] with two elements, host and port
	 */
	static String[] getBrokerHostAndPort(Application app) {

		String host = "";

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		Integer portInt = null;
		if (mbsc != null) {
			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.BROKER_MONITOR_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
			} catch (NullPointerException e) {
			}

			try {
				host = (String) mbsc.getAttribute(objName, "Host");
				portInt = (Integer) mbsc.getAttribute(objName, BrokerAttributes.PORT);
			} catch (AttributeNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstanceNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (MBeanException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ReflectionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String[] result = new String[2];
		result[0] = host;
		result[1] = portInt.toString();

		return result;

	}

	static boolean hasBrokerConfigMBean(final Application app) {
		// is this application JMX enabled with a broker config MBean?
		boolean hasBrokerConfigMBean = false;

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		if (mbsc != null) {

			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.BROKER_CONFIG_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
			} catch (NullPointerException e) {
			}

			if (objName != null) {
				try {
					if (mbsc.isRegistered(objName)) {
						// broker config MBean exists in this JVM
						hasBrokerConfigMBean = true;
					}
				} catch (IOException e) {

				}
			}
		}
		return hasBrokerConfigMBean;
	}

	/**
	 * Return the ClusterID of the specified application
	 * 
	 * This is obtained from the cluster configuration MBean. ClusterID is only
	 * used in HA clusters. If the broker is not part of a HA cluster then null is returned
	 * 
	 * @param app
	 * @return
	 */
	public static String getClusterID(Application app) {
		String clusterID = null;

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		if (mbsc != null) {

			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.CLUSTER_CONFIG_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}

			if (objName != null) {
				try {
					clusterID = (String) mbsc.getAttribute(objName, ClusterAttributes.CLUSTER_ID);
				} catch (AttributeNotFoundException e) {
					e.printStackTrace();
				} catch (InstanceNotFoundException e) {
					e.printStackTrace();
				} catch (MBeanException e) {
					e.printStackTrace();
				} catch (ReflectionException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}
		return clusterID;
	}
	
	/**
	 * Return the BrokerID of the specified broker application (if the broker is HA)
	 * or the InstanceName (if the broker is not HA)
	 * 
	 * This is obtained from the broker configuration MBean. 
	 * 
	 * @param app
	 * @return
	 */
	public static String getBrokerName(Application app) {
		String clusterID = null;

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		if (mbsc != null) {

			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.BROKER_CONFIG_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}

			if (objName != null) {
				try {
					clusterID = (String) mbsc.getAttribute(objName, BrokerAttributes.BROKER_ID);
				} catch (AttributeNotFoundException e) {
					e.printStackTrace();
				} catch (InstanceNotFoundException e) {
					e.printStackTrace();
				} catch (MBeanException e) {
					e.printStackTrace();
				} catch (ReflectionException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (clusterID==null){
					try {
						clusterID = (String) mbsc.getAttribute(objName, BrokerAttributes.INSTANCE_NAME);
					} catch (AttributeNotFoundException e) {
						e.printStackTrace();
					} catch (InstanceNotFoundException e) {
						e.printStackTrace();
					} catch (MBeanException e) {
						e.printStackTrace();
					} catch (ReflectionException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}
		}
		return clusterID;
	}

	/**
	 * Return the master broker address (host:port) for the specified application
	 * 
	 * This is obtained from the cluster configuration MBean. Master brokers are
	 * only used in conventional clusters. If the specified application is a
	 * member of a HA cluster, or if it is a member of a conventional cluster
	 * but no master broker is defined, or if the broker is not clustered, then
	 * an empty string is returned.
	 * 
	 * @param app
	 * @return
	 */
	public static String getMasterBroker(Application app) {
		String masterBroker = "";

		MBeanServerConnection mbsc = getMBeanServerConnection(app);

		if (mbsc != null) {

			ObjectName objName = null;
			try {
				objName = new ObjectName(MQObjectName.CLUSTER_CONFIG_MBEAN_NAME);
			} catch (MalformedObjectNameException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}

			if (objName != null) {
				try {
					CompositeData masterbrokerInfo = (CompositeData) mbsc.getAttribute(objName, ClusterAttributes.MASTER_BROKER_INFO);
					if (masterbrokerInfo!=null){
						masterBroker = (String) masterbrokerInfo.get(BrokerClusterInfo.ADDRESS);
					}
				} catch (AttributeNotFoundException e) {
					e.printStackTrace();
				} catch (InstanceNotFoundException e) {
					e.printStackTrace();
				} catch (MBeanException e) {
					e.printStackTrace();
				} catch (ReflectionException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}
		
		
		
		return masterBroker;
	}



}
