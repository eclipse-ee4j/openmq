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

package com.sun.messaging.visualvm.datasource;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import org.openide.util.ImageUtilities;

import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.io.PortMapperTable;
import com.sun.messaging.jms.management.server.BrokerClusterInfo;
import com.sun.messaging.jms.management.server.BrokerState;
import com.sun.messaging.jms.management.server.ClusterOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.messaging.visualvm.datasource.MQResourceDescriptor.ResourceType;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.jmx.JmxApplicationException;
import com.sun.tools.visualvm.jmx.JmxApplicationsSupport;
import com.sun.tools.visualvm.tools.jmx.JmxModel;
import com.sun.tools.visualvm.tools.jmx.JmxModelFactory;

public class ClusteredBrokerDataSource extends MQDataSource {

	private static final Image BROKER_RUNNING_ICON = ImageUtilities.loadImage(
			"com/sun/messaging/visualvm/ui/resources/brokerrunning.gif", true);
	private static final Image BROKER_DOWN_ICON = ImageUtilities.loadImage(
			"com/sun/messaging/visualvm/ui/resources/brokerdown.gif", true);
	private static final Image BROKER_OTHER_ICON = ImageUtilities.loadImage(
			"com/sun/messaging/visualvm/ui/resources/brokerother.gif", true);

	String brokerAddress;
	String name;
	Updater updater;
	
	/**
	 * 
	 * @param app
	 * @param master
	 * @param brokerAddress
	 * @param name
	 */
	public ClusteredBrokerDataSource(Application app, MQDataSource master,
			String brokerAddress, String name) {
		super(master);
		application = app;
		this.name = name;
		this.brokerAddress = brokerAddress;
		this.descriptor = new MQResourceDescriptor(this, name,
				ResourceType.CLUSTERED_BROKER, null, null);

		// start refresh thread
		updater = new Updater();
		updater.start();

		// note that the refresh thread will terminate when the connection is lost
	}

	public String getBrokerAddress() {
		return brokerAddress;
	}

	public void stop(){
		if (updater!=null){
			updater.stop();
			updater=null;
		}
	}

	class Updater {

		Timer timer = null;
		private int refreshInterval = 2;

		public void start() {
			if (timer != null) {
				return;
			}

			TimerTask task = new TimerTask() {

				@Override
				public void run() {
					refresh();
				}
			};

			timer = new Timer("ClusteredBrokerDataSource updating thread");
			timer.schedule(task, 0, (refreshInterval * 1000));
		}

		public boolean autoLoadStarted() {
			if (timer != null) {
				return (true);
			}
			return (false);
		}

		public void stop() {
			if (timer == null) {
				return;
			}

			timer.cancel();
			timer = null;
		}

		public void refresh() {
            
	        MBeanServerConnection mbsc = ClusterAccessUtils.getMBeanServerConnection(application);
		
			if (mbsc == null) {
				// assume we've lost the connection because the application has terminated
                stop();
				return;
			} else {
				ObjectName objName = null;
				try {
					objName = new ObjectName(
							MQObjectName.CLUSTER_MONITOR_MBEAN_NAME);
				} catch (MalformedObjectNameException e) {
				} catch (NullPointerException e) {
				}

				CompositeData clusteredBrokerData = null;
				try {
					Object opParams[] = { brokerAddress };
					String opSig[] = { String.class.getName() };
					clusteredBrokerData = (CompositeData) mbsc.invoke(objName,
							ClusterOperations.GET_BROKER_INFO_BY_ADDRESS,
							opParams, opSig);
				} catch (InstanceNotFoundException e) {
					// don't log an exception as the broker has probably terminated
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
					// fetch the BrokerID (only for HA clusters(
					String brokerID = (String) clusteredBrokerData.get(BrokerClusterInfo.ID);					
					
					int state = ((Integer) clusteredBrokerData
							.get(BrokerClusterInfo.STATE)).intValue();
					String stateLabel = (String) clusteredBrokerData
							.get(BrokerClusterInfo.STATE_LABEL);
					if (state == BrokerState.OPERATING) {
						descriptor.setIcon(BROKER_RUNNING_ICON);
					} else if (state == BrokerState.BROKER_DOWN) {
						descriptor.setIcon(BROKER_DOWN_ICON);
					} else {
						descriptor.setIcon(BROKER_OTHER_ICON);
					}
					if (brokerID==null){
						descriptor.setName(brokerAddress + " (" + stateLabel + ")");
					} else {
						// HA
						descriptor.setName(brokerID + " " + brokerAddress + " (" + stateLabel + ")");
					}
				}
			}

		}
	}
	
	public void xconnectToClusteredBroker() {
		
		// connect to port mapper and obtain JMX URL
		String brokerAddress = this.getBrokerAddress();
		String jmxURL = ClusterAccessUtils.getBrokerJMXURL(brokerAddress);
		
		// open it
		JmxApplicationsSupport jmxApplicationsSupport = JmxApplicationsSupport.getInstance();
		try {
			Application newApplication=jmxApplicationsSupport.createJmxApplication(jmxURL,brokerAddress , "", "");
		} catch (JmxApplicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

}
