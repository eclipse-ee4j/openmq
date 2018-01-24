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
import java.util.Timer;
import java.util.TimerTask;

import org.openide.util.ImageUtilities;

import com.sun.messaging.visualvm.datasource.MQResourceDescriptor.ResourceType;
import com.sun.messaging.visualvm.dataview.BrokerView;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.jmx.JmxApplicationException;
import com.sun.tools.visualvm.jmx.JmxApplicationsSupport;

public class BrokerDataSource extends MQDataSource {

	private static final Image CONNECTED_ICON = ImageUtilities.loadImage(
			"com/sun/messaging/visualvm/ui/resources/brokerconnected.gif", true);

	private static final Image DISCONNECTED_ICON = ImageUtilities.loadImage(
			"com/sun/messaging/visualvm/ui/resources/brokerdisconnected.gif", true);

	private BrokerView brokerView;

	/**
	 * First part of label
	 */
	private String nameLabel;
	/**
	 * Second part of label, typically "(pid=1234) or "(disconnected)"
	 */
	private String pidLabel;

	/**
	 * Properties used then this instance is a placeholder which is referenced
	 * in the cluster list of an open broker Only used when isOpened=false
	 */
	String brokerAddress;

	/**
	 * Used for connecting to remote broker in a background thread
	 */
	RemoteBrokerConector connector;

	/**
	 * Create a BrokerDataSource representing a broker whose application has
	 * been opened in the current VisualVM session
	 * 
	 * @param app
	 * @param nameLabel
	 *            First part of label, typically either "(Broker host:port)" or
	 *            just "(Broker host)" if not JMX enabled
	 * @param pidLabel
	 *            Second part of label, typically either "(pid 1234)" or
	 *            "(disconnected)" if application not running or connected
	 * @param address
	 */
	public BrokerDataSource(Application app, String nameLabel, String pidLabel, String address) {
		this.application = app;
		this.nameLabel = nameLabel;
		this.pidLabel = pidLabel;
		this.brokerAddress = address;
		this.descriptor = new MQResourceDescriptor(this, nameLabel + " " + pidLabel, ResourceType.BROKER, null,
				CONNECTED_ICON);
	}

	/**
	 * Create a placeholder representing a broker which is referenced in the
	 * cluster list of an open broker, but which has not yet been opened in the
	 * current VisualVM session
	 * 
	 * @param brokerAddress
	 * @param nameLabel
	 *            First part of label, typically either "(Broker host:port)" or
	 *            just "(Broker host)" if not JMX enabled
	 * @param pidLabel
	 *            Second part of label, typically either "(pid 1234)" or
	 *            "(disconnected)" if application not running or connected
	 */
	public BrokerDataSource(String brokerAddress, String nameLabel, String pidLabel) {
		this.nameLabel = nameLabel;
		this.pidLabel = pidLabel;
		this.brokerAddress = brokerAddress;
		this.descriptor = new MQResourceDescriptor(this, this.nameLabel + " " + this.pidLabel,
				ResourceType.BROKER_PROXY, null, DISCONNECTED_ICON);

		// start connector thread
		if (!ClusterAccessUtils.isLocal(brokerAddress)) {
			connector = new RemoteBrokerConector();
			connector.start();
		}

		// note that the connector thread will terminate when the connection is
		// lost
	}

	/**
	 * Update the label of this data source
	 * 
	 * @param nameLabel
	 *            First part of label, typically either "(Broker host:port)" or
	 *            just "(Broker host)" if not JMX enabled
	 * @param pidLabel
	 *            Second part of label, typically either "(pid 1234)" or
	 *            "(disconnected)" if application not running or connected
	 */
	public void updateLabel(String nameLabel, String pidLabel) {
		this.nameLabel = nameLabel;
		this.pidLabel = pidLabel;
		this.descriptor.setName(this.nameLabel + " " + this.pidLabel);
	}

	public void connectToClusteredBroker() throws MQPluginException {

		// connect to port mapper and obtain JMX URL
		String brokerAddress = this.getBrokerAddress();
		String jmxURL = ClusterAccessUtils.getBrokerJMXURL(brokerAddress);

		if (jmxURL == null) {
			// cannot connect to port mapper. Perhaps broker is not running
			throw new MQPluginException("No response from portmapper at " + brokerAddress
					+ ". It is probably not running");
		}

		// open it
		JmxApplicationsSupport jmxApplicationsSupport = JmxApplicationsSupport.getInstance();
		try {
			Application newApplication = jmxApplicationsSupport.createJmxApplication(jmxURL, brokerAddress, "", "");
		} catch (JmxApplicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		descriptor.setIcon(CONNECTED_ICON);
	}

	public String getBrokerAddress() {
		return brokerAddress;
	}

	public void setBrokerView(BrokerView bv) {
		brokerView = bv;
	}

	public BrokerView getBrokerView() {
		return brokerView;
	}

	public String getNameLabel() {
		return nameLabel;
	}

	public String getPidLabel() {
		return pidLabel;
	}

	class RemoteBrokerConector {

		Timer timer = null;
		private int refreshInterval = 10;

		public void start() {
			if (timer != null) {
				return;
			}

			TimerTask task = new TimerTask() {

				@Override
				public void run() {
					connect();
				}

			};

			timer = new Timer("BrokerDataSource connecting thread");
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

		/**
		 * Try to connect to the remote broker
		 */
		private void connect() {

			// connect to port mapper and obtain JMX URL
			String brokerAddress = getBrokerAddress();
			String jmxURL = ClusterAccessUtils.getBrokerJMXURL(brokerAddress);

			if (jmxURL == null) {
				// cannot connect to port mapper. Perhaps broker is not running
				return;
			}

			// open it
			JmxApplicationsSupport jmxApplicationsSupport = JmxApplicationsSupport.getInstance();
			try {
				Application newApplication = jmxApplicationsSupport.createJmxApplication(jmxURL, brokerAddress, "", "");

				stop();
			} catch (JmxApplicationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}

		}
	}

	public void setPlaceholder() {
		
		descriptor.setIcon(DISCONNECTED_ICON);
		if (getBrokerView() != null) {
			getBrokerView().revertToPlaceholder();
		}
		if (connector != null) {
			connector.start();
		}
	}
	
	public void setConnected() {
		
		descriptor.setIcon(CONNECTED_ICON);

	}
}
