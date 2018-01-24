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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.sun.messaging.jms.management.server.BrokerClusterInfo;
import com.sun.messaging.visualvm.MQBrokerApplicationType;
import com.sun.messaging.visualvm.dataview.BrokerView;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.application.jvm.Jvm;
import com.sun.tools.visualvm.application.jvm.JvmFactory;
import com.sun.tools.visualvm.application.type.ApplicationTypeFactory;
import com.sun.tools.visualvm.core.datasource.DataSource;
import com.sun.tools.visualvm.core.datasource.DataSourceRepository;
import com.sun.tools.visualvm.core.datasupport.DataChangeEvent;
import com.sun.tools.visualvm.core.datasupport.DataChangeListener;
import com.sun.tools.visualvm.core.datasupport.DataRemovedListener;
import com.sun.tools.visualvm.core.datasupport.Stateful;
import com.sun.tools.visualvm.core.ui.DataSourceWindowManager;

public class MQModelProvider implements DataChangeListener<Application> {
	// ~ Static fields/initializers
	// -----------------------------------------------------------------------------------------------

	private static final MQModelProvider INSTANCE = new MQModelProvider();
	private final DataRemovedListener<Application> removelListener = new DataRemovedListener<Application>() {

		@Override
		public void dataRemoved(Application app) {
			
		}
	};

	/**
	 * Listener that is invoked when the application changes its state
	 */
	private final PropertyChangeListener applicationPropertyChangeListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt.getSource() instanceof Application) {
				Application app = (Application) evt.getSource();
				if (evt.getPropertyName().equals(Stateful.PROPERTY_STATE)) {
					if ((((Integer) evt.getOldValue()).intValue() == Stateful.STATE_AVAILABLE)
							&& (((Integer) evt.getNewValue()).intValue() == Stateful.STATE_UNAVAILABLE)) {
						// lost contact with application: it has probably
						// terminated
						processFinishedApplication(app);
					}
				}
			}

		}

	};

	private static Map<String, ClusterRootDataSource> clusters = new HashMap<String, ClusterRootDataSource>();

	/**
	 * Each key is a broker's JMXURL (as returned by the port mapper)
	 * Corresponding value is a placeholder BrokerDataSource
	 */
	private static Map<String, BrokerDataSource> brokerPlaceholders = new HashMap<String, BrokerDataSource>();

	/**
	 * Each key is a broker's JMXURL (as returned by the port mapper)
	 * Corresponding value is an open BrokerDataSource
	 * Only used for clustered brokers. NOT for standalone brokers,
	 */
	public static Map<String, BrokerDataSource> brokerDataSources = new HashMap<String, BrokerDataSource>();

	/**
	 * Each key is an Application Corresponding value is an open
	 * BrokerDataSource
	 */
	public static Map<Application, BrokerDataSource> appToBroker = new HashMap<Application, BrokerDataSource>();

	public MQModelProvider() {
	}

	/**
	 * Needed because this class implements DataChangeListener
	 */
	public void dataChanged(DataChangeEvent<Application> event) {
		if (event.getAdded().isEmpty() && event.getRemoved().isEmpty()) {
			// Initial event to deliver DataSources already created by the
			// provider before registering to it as a listener
			// NOTE: already existing hosts are treated as new for this provider
			Set<Application> newApplications = event.getCurrent();

			for (Application app : newApplications) {
				processNewApplication(app);
			}
		} else {
			// Real delta event
			Set<Application> newApplications = event.getAdded();

			for (Application app : newApplications) {
				processNewApplication(app);
			}
		}
	}

	public static void initialize() {
		DataSourceRepository.sharedInstance().addDataChangeListener(INSTANCE, Application.class);
	}

	public static void shutdown() {
		DataSourceRepository.sharedInstance().removeDataChangeListener(INSTANCE);
	}

	private void processFinishedApplication(Application app) {
	
		/**
		 * If this was a clustered broker, we "close" it, remove its subnodes, and convert it back to a disconnected placeholder
		 * 
		 * If this was a standalone (or non-JMX-enabled) broker, then the broker node will be owned by the application
		 * node and so will be removed automatically when the application terminates, so we don't need to anything here
		 */

		BrokerDataSource bds = appToBroker.get(app);
		if (bds == null)
			throw new RuntimeException("App not found in appTobroker");

		// remove from brokerDataSources map (only JMX-enabled BrokerDataSources
		// will be in this map)
		boolean wasClustered=false;
		for (Entry<String, BrokerDataSource> entry : brokerDataSources.entrySet()) {
			if (entry.getValue().equals(bds)) {
				wasClustered=true;
				brokerDataSources.remove(entry.getKey());
				break;
			}
		}
		
		if (!wasClustered){
			// nothing else to do
			return;
		}

		// add to brokerPlaceholders map
		// with the IP:port as the key (previous JMXURL is no longer valid)
		brokerPlaceholders.put(ClusterAccessUtils.getIPPort(bds.getBrokerAddress()), bds);

		bds.setPlaceholder();
		
		// remove subnodes
		removeApplicationSubnodes(app,bds);
		
		// change name back to disconnected
		bds.updateLabel(bds.getNameLabel(), "(disconnected)");

		// remove from appToBroker map
		appToBroker.remove(app);
		
		bds.setApplication(null);

	}

	/**
	 * Return whether the specified application represents a Glassfish instance
	 * 
	 * @param app
	 * @return whether the specified application represents a Glassfish instance
	 */
	public static boolean isGlassfish(Application app) {
		// this needs to work regardless of whether the Glassfish plugin is
		// installed,
		// so simply examine the main class
		Jvm jvm = JvmFactory.getJVMFor(app);
		if (jvm.getMainClass() != null) {
			if (jvm.getMainClass().equals("com.sun.enterprise.server.PELaunch")) {
				return true;
			}
		}
		return false;
	}

	private void processNewApplication(final Application app) {

		// is this a standalone Broker main class? (this test only works on a
		// local JVM)
		boolean isBrokerMainClassLocal = ApplicationTypeFactory.getApplicationTypeFor(app) instanceof MQBrokerApplicationType;

		// can we make a JMX connection to the Broker MBean?
		boolean hasBrokerConfigMBean = ClusterAccessUtils.hasBrokerConfigMBean(app);

		if (hasBrokerConfigMBean) {
			// we can initialise right now
			initialiseForApplication(app, isBrokerMainClassLocal, hasBrokerConfigMBean);
		} else {
			// we can't make a JMX connection to the Broker MBean, but that
			// might be because the broker hasn't initialised yet
			// we don't want to block on every type of JVM, so spawn a thread to
			// sleep for a second and try again
			Thread t = new Thread() {
				public void run() {
					// make several attempts, sleeping for one second in between
					// each attempt
					//TODO We should do better than this
					for (int i = 0; i < 300; i++) {
						try {
							sleep(1000);
						} catch (InterruptedException e) {
						}
						boolean hasBrokerConfigMBean = ClusterAccessUtils.hasBrokerConfigMBean(app);
						if (hasBrokerConfigMBean) {
							boolean isBrokerMainClassLocal = ApplicationTypeFactory.getApplicationTypeFor(app) instanceof MQBrokerApplicationType;
							initialiseForApplication(app, isBrokerMainClassLocal, hasBrokerConfigMBean);
							return;
						}
					}
					// we can't create a JMX connection. But is it a MQ broker?
					// If so we can create a special explanatory subnode
					boolean isBrokerMainClassLocal = ApplicationTypeFactory.getApplicationTypeFor(app) instanceof MQBrokerApplicationType;
					if (isBrokerMainClassLocal) {
						initialiseForApplication(app, true, false);
					}
				}
			};
			t.start();
		}
	}

	private synchronized void initialiseForApplication(final Application app, boolean isBrokerMainClassLocal,
			boolean hasBrokerConfigMBean) {
	
		if (isBrokerMainClassLocal) {
			if (hasBrokerConfigMBean) {
				// it's a broker and it's JMX enabled
				// we can create a full set of subnodes
			} else {
				// it's a broker, but it's not JMX enabled
				// we will create only a Broker subnode that displays an error
				// message
			}
		} else {
			// it could be a broker but remote
			// it could be glassfish instance
			// it could be anything else
			if (hasBrokerConfigMBean) {
				// yes it is, and it's JMX enabled
				// we can create a full set of subnodes
			} else {
				// it could be a glassfish instance without a embedded broker
				// it could be a glassfish instance with an embedded broker but
				// which isn't JMX enabled
				// it could be some other application entirely
				// we don't know enough to do anything at all
				return;
			}
		}
	
		// work out what label it should have
		String brokerNodeLabel;
		String pidLabel = "(pid " + app.getPid() + ")";
		if (hasBrokerConfigMBean) {
			String brokerName = ClusterAccessUtils.getBrokerName(app);
			String[] hostAndPort = ClusterAccessUtils.getBrokerHostAndPort(app);
			String hostName = hostAndPort[0];
			int port = Integer.parseInt(hostAndPort[1]);
	
			// set the broker label to the real URL and pid of the
			// broker
			brokerNodeLabel = "Broker " + brokerName + " " + hostName + ":" + port;
		} else {
			// set the broker label with just the JMX host and pid (not
			// quite as informative)
			String hostName = app.getHost().getHostName();
			brokerNodeLabel = "Broker (" + hostName + ")";
		}
	
		BrokerDataSource bkr;
		
		// Return the address of the specified address in the form host:port
		// this is obtained from the port mapper
		String address = ClusterAccessUtils.getBrokerHostPort(app);
	
		if (hasBrokerConfigMBean && isClustered(app)) {
			// get or create a name for this cluster
			String clusterName = getClusterName(app);
	
			// Is there already a node for this cluster?
			//TODOwarning - this we might have a different name
			ClusterRootDataSource clusterRoot = clusters.get(clusterName);
			if (clusterRoot == null) {
				// create a new cluster root
				clusterRoot = new ClusterRootDataSource(app, null, clusterName);
				DataSource.ROOT.getRepository().addDataSource(clusterRoot);
	
				// save it in the cluster map
				clusters.put(clusterName, clusterRoot);
	
			}
	
			// get the broker's JMXURL, which is a handy global identifier for
			// the running broker
			String jmxurl = ClusterAccessUtils.getBrokerJMXURL(app);
				
			// was there a BrokerDataSource placeholder for this broker?
			// the key might be either the JMXURL or the address
			bkr = brokerPlaceholders.get(jmxurl);
			if (bkr != null) {
				// remove from placeholder map 
				// (we'll add it to open broker map below)
				brokerPlaceholders.remove(jmxurl);
			} else {
                            String ipport = ClusterAccessUtils.getIPPort(address);
				bkr = brokerPlaceholders.get(ClusterAccessUtils.getIPPort(ipport));
				if (bkr != null) {
					// remove from placeholder map 
					// (we'll add it to open broker map below)
					brokerPlaceholders.remove(ipport);
				}
			}
			if (bkr != null) {
				// yes there was a BrokerDataSource placeholder for this broker
		
				// update the placeholder with the JMX-enabled app that it
				// represents
				bkr.setApplication(app);
	
				// if the placeholder has already been opened it will have a BrokerView containing an error message
				// need to replace this with the real data
				BrokerView bv = bkr.getBrokerView();
				if (bv!=null){
					// check that we're not in the middle of initialising this BrokerView in another thread
					// if we are, then leave that thread to do its work
					if (!bv.isInitialising()){
						bv.createSubPanels(app);
					}
				}
				
				// update the label
				bkr.updateLabel(brokerNodeLabel,pidLabel);
				bkr.setConnected();
			} else {
				// there was no placeholder for this broker, so we need to
				// create a new BrokerDataSource
	
				// create the BrokerDataSource
				bkr = new BrokerDataSource(app, brokerNodeLabel, pidLabel, address);
				clusterRoot.getRepository().addDataSource(bkr);
	
			}
	
			if (hasBrokerConfigMBean) {
				// Create the sub tree under the new broker node
				createApplicationSubnodes(app, bkr);
			}
	
			// Add the BrokerDataSource to the brokerDataSources map with
			// the JMXURL as the key
			brokerDataSources.put(jmxurl, bkr);
	
			if (hasBrokerConfigMBean) {
				// Create placeholder broker nodes under the cluster root
				// corresponding to all the other brokers in the cluster that
				// this broker knows about
				createPlaceholderBrokerNodes(app, clusterRoot);
			}
	
		} else {
			// non-clustered or non-JMX-enabled
	
			// create the BrokerDataSource and add it under the new application
			bkr = new BrokerDataSource(app, brokerNodeLabel, pidLabel, address);
			app.getRepository().addDataSource(bkr);
	
			if (hasBrokerConfigMBean) {
				// Create the subtree under the new broker node
				createApplicationSubnodes(app, bkr);
			}
		}
	
		appToBroker.put(app, bkr);
		app.notifyWhenRemoved(removelListener);
	
		// listen for application events
		app.addPropertyChangeListener(applicationPropertyChangeListener);
	}

	/**
	 * Obtain a name for the cluster of which this broker is a member.
	 * 
	 * If this is a HA cluster simply return the defined clusterID
	 * 
	 * If this is a conventional cluster with a master broker then use the name of
	 * the master broker as the cluster name
	 * 
	 * Id this is a conventional cluster without a master broker then
	 * simply return "MQ Cluster"
	 * 
	 * @return
	 */
	private String getClusterName(Application app) {

		
		// If this is a HA cluster we can simply use its ClusterID
		String clusterID = ClusterAccessUtils.getClusterID(app);
		if (clusterID==null){
			// This is not part of a HA cluster so ClusterID is not used
			String masterBrokerHostPort = ClusterAccessUtils.getMasterBroker(app);
			if (masterBrokerHostPort.equals("")){
				// TODO Analyse non-master-broker conventional clusters and assign an artificial ID
				clusterID = "(no master)";
			} else {
				// we want all brokers in the cluster to return the same master broker name, so 
				// use IP address instead of host
				String host = ClusterAccessUtils.getHost(masterBrokerHostPort);
				try {
					InetAddress hostIP[] = InetAddress.getAllByName(host);
					host = hostIP[0].getHostAddress();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				int port = ClusterAccessUtils.getPort(masterBrokerHostPort);
				clusterID = "(master="+host+":"+port+")";
			}
		} else {
			clusterID = clusterID + " (HA)";
		}
		return "MQ Cluster "+clusterID;
	}

	/**
	 * Create all the necessary subnodes of the broker node that are needed for
	 * the specified application
	 * 
	 * @param app
	 *            The application for which subnodes will be created
	 * @param parent
	 *            The BrokerDataSource under which the subnode tree will be
	 *            added.
	 */
	private void createApplicationSubnodes(Application app, BrokerDataSource parent) {

		ServicesDataSource svcs = new ServicesDataSource(app, parent, "Services");
		parent.getRepository().addDataSource(svcs);

		ConnectionsDataSource cxns = new ConnectionsDataSource(app, parent, "Connections");
		parent.getRepository().addDataSource(cxns);

		DestinationsDataSource dests = new DestinationsDataSource(app, parent, "Destinations");
		parent.getRepository().addDataSource(dests);

		TransactionsDataSource txns = new TransactionsDataSource(app, parent, "Transactions");
		parent.getRepository().addDataSource(txns);

		ProducersDataSource prods = new ProducersDataSource(app, parent, "Producers");
		parent.getRepository().addDataSource(prods);

		ConsumersDataSource cons = new ConsumersDataSource(app, parent, "Consumers");
		parent.getRepository().addDataSource(cons);

		// Create the ClusterDataSource
		// This will also take care of creating any ClusteredBrokerDataSource nodes beneath it 
		ClusterDataSource cds = new ClusterDataSource(app, parent, "Cluster");
		parent.getRepository().addDataSource(cds);

		LogDataSource lds = new LogDataSource(app, parent, "Log");
		parent.getRepository().addDataSource(lds);

	}
	
	private void removeApplicationSubnodes(Application app, BrokerDataSource parent) {

		
		Set dataSources = new HashSet(parent.getRepository().getDataSources());
		for (Iterator iterator = dataSources.iterator(); iterator.hasNext();) {
			DataSource thisDataSource = (DataSource) iterator.next();
			parent.getRepository().removeDataSource(thisDataSource);
			DataSourceWindowManager.sharedInstance().closeDataSource(thisDataSource);
		}
				
	}

	/**
	 * Return whether the specified application (being a MQ broker) is part of a cluster.
	 * This is determined by querying its Cluster Monitor MBean 
	 * 
	 * @param app
	 * @return 
	 */
	private boolean isClustered(Application app) {
		
		// Is this a HA cluster?
		String clusterID = ClusterAccessUtils.getClusterID(app);
		if (clusterID!=null) return true;
		
		// Is this a conventional cluster with more than one broker?
		int clusterSize = ClusterAccessUtils.getClusteredBrokerInfo(app).size();
		if (clusterSize>1) return true;
		
		return false;
	}

	/**
	 * Create placeholder broker nodes under the cluster root corresponding to
	 * all the other brokers in the cluster that this broker knows about
	 * 
	 * @param app
	 * @param cds
	 */
	private void createPlaceholderBrokerNodes(Application app, ClusterRootDataSource crds) {

		// Ask the broker what other brokers are in its cluster
		List<Map<String, String>> clusteredBrokers = ClusterAccessUtils.getClusteredBrokerInfo(app);

		// Iterate through the other brokers in the cluster
		for (Map<String, String> thisBrokerInfo : clusteredBrokers) {

			String id = (String) thisBrokerInfo.get(BrokerClusterInfo.ID);
			String address = (String) thisBrokerInfo.get(BrokerClusterInfo.ADDRESS);
			String label;
			if (id != null) {
				// HA
				label = id + " " + address;
			} else {
				label = address;

			}

			// find the JMXURL of the broker (by asking its port mapper)
			String jmxurl = ClusterAccessUtils.getBrokerJMXURL(address);

			BrokerDataSource bds = null;
			if (jmxurl == null) {
				// broker is probably not running
				
				// Have we already got a placeholder
				// BrokerDataSource for
				// this broker (as identified by its address)?
				bds = brokerPlaceholders.get(ClusterAccessUtils.getIPPort(address));
			} else {
				// broker is running

				// Have we already got an open BrokerDataSource for
				// this broker (as identified by its JMXURL)?
				bds = brokerDataSources.get(jmxurl);
				if (bds == null) {
					// No, we don't have an open BrokerDataSource
					//
					// Have we already got a placeholder
					// BrokerDataSource for
					// this broker (as identified by its JMXURL or IP:port)?
					bds = brokerPlaceholders.get(jmxurl);
					if (bds == null) {
						bds = brokerPlaceholders.get(ClusterAccessUtils.getIPPort(address));
					}
				}
			}
			if (bds == null) {
				// Create a placeholder BrokerDataSource under the
				// Cluster root
				bds = new BrokerDataSource(address, "Broker " + label, "(disconnected)");
				crds.getRepository().addDataSource(bds);

				// save the placeholder BrokerDataSource using the
				// JMXURL as the key if available, otherwise IP:port
				String key = (jmxurl == null) ? ClusterAccessUtils.getIPPort(address) : jmxurl;
				brokerPlaceholders.put(key, bds);
			}

		}

	}
}
