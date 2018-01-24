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

package com.sun.messaging.jms.blc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.IllegalSelectorException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.management.remote.JMXServiceURL;
import javax.resource.spi.ResourceAdapterInternalException;

import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsspi.PropertiesHolder;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.service.PortMapperClientHandler;
import com.sun.messaging.jms.ra.ResourceAdapter;

/**
 * An instance of this class represents a broker whose lifecycle is managed by
 * this broker. <br/>
 * <br/>
 * To use this class,<br/>
 * <ul>
 * <li>create an instance</li>
 * <li>configure it by setting the required javabean properties</li>
 * <li>call {@link #start() start} to start the broker</li>
 * <li>call {@link #stop() stop} to shut down the broker</li>
 * </ul>
 * 
 * 
 */
public class LifecycleManagedBroker {

	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * REMOTE.<br/>
	 * This means that this instance will not manage a broker. Calling start()
	 * and stop() will have no effect.
	 */
	public static final String BROKER_TYPE_REMOTE = "REMOTE";
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * LOCAL.<br/>
	 * This means that when start() is called a broker will be started in a
	 * separate JVM, and a subsequent call to stop() will shut it down
	 */
	public static final String BROKER_TYPE_LOCAL = "LOCAL";
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * EMBEDDED.<br/>
	 * This means that when start() is called a broker will be started in the
	 * same JVM.<br/>
	 * Clients running in this JVM will connect to it using TCP connections.<br/>
	 * <b>Note, however, that currently if this value is specified then DIRECT
	 * will be used instead</b><br/>
	 */
	public static final String BROKER_TYPE_EMBEDDED = "EMBEDDED";
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * DIRECT.<br/>
	 * This means that when start() is called a broker will be started in the
	 * same JVM.<br/>
	 * Clients running in this JVM will connect to it using direct (non-TCP)
	 * connections.DB. <br/>
	 */
	public static final String BROKER_TYPE_DIRECT = "DIRECT";
		
	/* Loggers */
	private static transient final String _className = "com.sun.messaging.jms.ra.LifecycleManagedBroker";
	private static transient final String _lgrNameBase = "javax.resourceadapter.mqjmsra";
	private static transient final String _lgrNameLifecycle = "javax.resourceadapter.mqjmsra.lifecycle";
	private static transient final Logger _loggerB = Logger.getLogger(_lgrNameBase);
	private static transient final Logger _loggerL = Logger.getLogger(_lgrNameLifecycle);
	private static transient final String _lgrMIDPrefix = "MQJMSRA_RA";
	private static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
	private static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
	private static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
	private static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
	private static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

	// private fields related to broker lifecycle
	private transient EmbeddedBrokerRunner ebr = null;
	private transient LocalBrokerRunner lbr = null;

	// JMSService object used by RADirect clients 
	// This is for use by DirectConnectionFactory#_createConnectionId 
	// when _getJMSService() doesn't work. This seems to be here as a quick and dirty fix to a bug 
	// and it would be good to get rid of this
	private static JMSService jmsservice = null;

	// private fields used to keep track of current state
	private transient boolean started;

	/*
	 * whether a the lifecycle managed broker should start a PortMapper thread
	 * listening on the configured PortMapper port
	 */
	private transient boolean doBind = true;

	/* Globally started at least once data */
	private static transient boolean _startedAtLeastOnce;
	private static transient int _rmiRegistryPort;

	/* Indicate type for broker lifecycle control */
	private String brokerType = LifecycleManagedBroker.BROKER_TYPE_REMOTE;

	/**
	 * Specifies the instance name of the lifecycle managed broker.<br/>
	 * For an embedded broker this sets the <tt>-name</tt> broker argument.<br/>
	 * In the case of a local broker this is used to specify the logfile
	 * directory
	 */
	private String brokerInstanceName = "imqbroker"; // Default is 'imqbroker'

	/*
	 * The admin userName to be used for JMX connections 
	 */
	private String adminUsername = "admin";

	/*
	 * The admin password to be used for JMX connections 
	 */
	private String adminPassword = null;
		
	/**
	 * the brokerBindAddress for the lifecycle managed broker.<br/>
	 * This specifies the network address that the broker must bind to and is
	 * typically needed in cases where two or more hosts are available (such as
	 * when more than one network interface card is installed in a computer), If
	 * null, the broker will bind to all addresses on the host machine.<br/>
	 * <br/>
	 * This is used to set the <tt>imq.hostname</tt> property
	 */
	private String brokerBindAddress = null; // Default is 'all addresses'

	/*
	 * Indicate the port for the Rmi Registry in the broker when its lifecycle
	 * is controlled by the RA
	 */
	private int rmiRegistryPort = 1099;

	/*
	 * Indicate whether the broker should start its own RMI Registry its
	 * lifecycle is controlled by the RA
	 */
	private boolean startRmiRegistry = false;
	
	/* The JMXServiceURL String that can be used to acquire JMX connections */
	private transient String jmxServiceURL = null;

	/* Indicate main port for broker when its lifecycle is controlled by the RA */
	private int brokerPort = 7676;

	/**
	 * Specifies the brokerHomeDir of the lifecycle managed broker.<br/>
	 * For an embedded broker this sets the <tt>-imqhome</tt> broker argument.<br/>
	 * In the case of a local broker this must be set to the parent of the
	 * <tt>bin</tt> directory containing the broker executable.
	 */
	private String brokerHomeDir = null;

	/*
	 * Indicate Lib Directory for broker when its lifecycle is controlled by the
	 * RA
	 */
	private String brokerLibDir = null;

	/**
	 * the location of the var directory of the lifecycle managed broker.
	 */
	private String brokerVarDir = null;

	/*
	 * * The brokerJavaDir of the lifecycle managed broker.<br/> This is used in
	 * the case of a local broker to set the <-tt>javahome</tt> argument passed
	 * to the broker executable and must be set to a directory containg a Java
	 * JRE<tt/> It is not used in the case of an embedded broker.
	 */
	private String brokerJavaDir = null;

	/**
	 * Additional command-line arguments for the lifecycle managed broker
	 */
	private String brokerArgs = null;

	/*
	 * Indicate whether the JNDI form of the JMXServiceURL is to be used in the
	 * broker when its lifecycle is controlled by the RA
	 */
	private boolean useJNDIRmiServiceURL = true;

	/*
	 * Indicate whether SSL must be used for the JMXConnector in the broker when
	 * its lifecycle is controlled by the RA
	 */
	private boolean useSSLJMXConnector = true;
	
	/**
	 * If set this is used to set the broker property imq.cluster.nowaitForMasterBrokerTimeoutInSeconds 
	 */
	private int nowaitForMasterBrokerTimeoutInSeconds = -1;

	/**
	 * The broker identifier of the lifecycle managed broker. For brokers using
	 * HA, which use a shared JDBC-based data store, this string is appended to
	 * the names of all database tables to identify each table with a particular
	 * broker. It is not required for non-HA brokers.
	 */
	private String brokerId = null;

	/**
	 * The maximum time allowed in ms for a local broker to start. Default =
	 * 10000ms
	 */
	private int brokerStartTimeout = 20000;

	/*
	 * The admin password file to be used when starting the broker w/ admin
	 * user/password checking
	 */
	private String adminPassFile = null;

	/**
	 * Specifies whether the lifecycle managed broker is part of a HA (enhanced)
	 * cluster
	 */
	private boolean brokerEnableHA = false;

	/**
	 * The cluster identifier for the lifecycle managed broker
	 */
	private String clusterId = null;

	/**
	 * The database type for the lifecycle managed broker. Possible values are
	 * define dby the constants DB_TYPE_DERBY, DB_TYPE_HADB, DB_TYPE_ORACLE,
	 * DB_TYPE_POINTBASE and DB_TYPE_CLOUDSCAPE
	 */
	private String dbType = null;

	/**
	 * If the lifecycle managed broker is to be part of a non-HA clusters,
	 * specifies a list of broker addresses belonging to the cluster.
	 */
	private String connectionURL = "";

	// possible values of dbType
	/** Pass this value to setDbType to specify that the database type is Derby */
	public static final String DB_TYPE_DERBY = "derby";
	/** Pass this value to setDbType to specify that the database type is HADB */
	public static final String DB_TYPE_HADB = "hadb";
	/** Pass this value to setDbType to specify that the database type is MYSQL */
	public static final String DB_TYPE_MYSQL = "mysql";
	/** Pass this value to setDbType to specify that the database type is Oracle */
	public static final String DB_TYPE_ORACLE = "oracle";
	/**
	 * Pass this value to setDbType to specify that the database type is
	 * Pointbase
	 */
	public static final String DB_TYPE_POINTBASE = "pointbase";
	/**
	 * Pass this value to setDbType to specify that the database type is
	 * Cloudscape
	 */
	public static final String DB_TYPE_CLOUDSCAPE = "cloudscape";

	/* private constants */// Broker common defs
	private static transient String IMQ_BROKERID = "imq.brokerid";
	private static transient String IMQ_JDBC_VENDOR = "imq.persist.jdbc.dbVendor";
	
	// HADB Properties that are passed into this class using setDBProps()
	private static transient String HADB_USER = DB_TYPE_HADB + ".user";
	private static transient String HADB_PASSWORD = DB_TYPE_HADB + ".password";
	private static transient String HADB_SERVERLIST = DB_TYPE_HADB + ".serverList";
	
	// Properties to be passed to a managed broker
	private static transient String IMQ_HADB = "imq.persist.jdbc.hadb";
	private static transient String IMQ_HADB_DSPROP = IMQ_HADB + ".property";
	private static transient String IMQ_HADB_USER = IMQ_HADB + ".user";
	private static transient String IMQ_HADB_PASSWORD = IMQ_HADB + ".password";
	private static transient String IMQ_HADB_DSPROP_SERVERLIST = IMQ_HADB_DSPROP + ".serverList";
	
	// MYSQL Properties that are passed into this class using setDBProps()
	private static transient String MYSQL_USER = DB_TYPE_MYSQL + ".user";
	private static transient String MYSQL_PASSWORD = DB_TYPE_MYSQL + ".password";
	private static transient String MYSQL_URL = DB_TYPE_MYSQL + "property.url";
	
	// general DB Properties that are passed into this class using setDBProps()
	private static transient String FALLBACK_DATABASE_PASSWORD = "jdbc.password";
	
	// Properties to be passed to a managed broker
	private static transient String IMQ_MYSQL = "imq.persist.jdbc.mysql";
	private static transient String IMQ_MYSQL_DSPROP = IMQ_MYSQL + ".property";
	private static transient String IMQ_MYSQL_USER = IMQ_MYSQL + ".user";
	private static transient String IMQ_MYSQL_PASSWORD = IMQ_MYSQL + ".password";	
	private static transient String IMQ_MYSQL_DSPROP_URL = IMQ_MYSQL_DSPROP + ".url";
	
	// general DB Properties that are passed to a managed broker
	private static transient String IMQ_FALLBACK_DATABASE_PASSWORD = "imq.persist.jdbc.password";	


	/**
	 * database type-specific config properties for the lifecycle managed broker
	 */
	private Properties dbProps = new Properties();
	
	/**
	 * Broker properties for the lifecycle managed broker
	 */
	private Properties brokerProps = new Properties();

	/**
	 * dataSource-specific properties for the lifecycle-managed broker
	 */
	private Properties dsProps = new Properties();
	

	/**
	 * The JMXConnectorEnv HashMap that is used to acquire JMX connections This
	 * is a read-only property constructed from the properties adminUsername and
	 * adminPassword It is a HashMap whose key is "jmx.remote.credentials" and
	 * whose corresponding value is a string array containing admin username and
	 * admin password
	 */
	private transient HashMap<String, String[]> jmxConnectorEnv = null;
	
	/*
	 * The JMXServiceURLList String that can be used to acquire JMX connections
	 * to all brokers specified on connectionURL
	 */
	private transient String jmxServiceURLList = null;

	/*
	 * Indicate whether the JMXServiceURLList is valid or needs to be
	 * re-acquired
	 */
	private boolean isJMXServiceURLListValid = false;
	
	/**
	 * Value used to set the broker property imq.cluster.masterbroker
	 */
	private String masterBroker = null;

	/**
	 * Value used to set the broker property imq.cluster.brokerlist
	 */
	private String clusterBrokerList = null;
	
	/**
	 * Create an instance of LifecycleManagedBroker.
	 */
	public LifecycleManagedBroker() {

	}

	/**
	 * Starts the lifecycle managed broker.
	 * 
	 * @throws ResourceAdapterInternalException
	 */
	public synchronized void start() throws ResourceAdapterInternalException {

		if (BROKER_TYPE_LOCAL.equals(brokerType)) {
			try {
				lbr = new LocalBrokerRunner(getBrokerUrl(), brokerInstanceName, brokerBindAddress, brokerPort, brokerHomeDir,
						brokerLibDir, brokerVarDir, brokerJavaDir, brokerArgs, useJNDIRmiServiceURL, rmiRegistryPort,
						startRmiRegistry, useSSLJMXConnector, brokerStartTimeout, adminUsername,
						adminPassFile,new EffectiveBrokerProps());
				lbr.start();
			} catch (Exception lbse) {
				ResourceAdapterInternalException raie = new ResourceAdapterInternalException(_lgrMID_EXC
						+ "start:Aborting:Exception starting LOCAL broker=" + lbse.getMessage());
				raie.initCause(lbse);
				_loggerL.severe(raie.getMessage());
				_loggerL.info(this.toString());
				lbse.printStackTrace();
				_loggerL.throwing(_className, "start()", raie);
				throw raie;
			}
		} else {
			if (isInProcess()) {
				try {
					if (!_startedAtLeastOnce) {
						_rmiRegistryPort = rmiRegistryPort;
					}
					if (ebr == null) {
						ebr = new EmbeddedBrokerRunner(brokerType, brokerInstanceName, brokerBindAddress,
								brokerPort, brokerHomeDir, brokerLibDir, brokerVarDir, brokerJavaDir, brokerArgs,
								useJNDIRmiServiceURL, _rmiRegistryPort, startRmiRegistry, useSSLJMXConnector, doBind, _getEffectiveBrokerProps());
						ebr.init();
					}
					ebr.start();
					_setRAJMSService(ebr.getJMSService());
					_startedAtLeastOnce = true;
				} catch (Exception ebse) {
					ResourceAdapterInternalException raie = new ResourceAdapterInternalException(_lgrMID_EXC
							+ "start:Aborting:Exception starting EMBEDDED broker=" + ebse.getMessage());
					raie.initCause(ebse);
					_loggerL.severe(raie.getMessage());
					_loggerL.info(this.toString());
					ebse.printStackTrace();
					_loggerL.throwing(_className, "start()", raie);
					throw raie;
				}
			}
		}

		started = true;
	}

	/**
	 * Return whether this lifecycle managed broker has been started
	 * 
	 * @return whether this lifecycle managed broker has been started
	 */
	public boolean isStarted() {
		return started;
	}

	/**
	 * Stops the lifecycle managed broker
	 */
	public void stop() {

		if (ebr != null) {
			ebr.stop();
			ebr = null;
		}
		if (lbr != null) {
			lbr.stop();
			lbr = null;
		}
		started = false;
	}

	/**
	 * Return all properties that need to be passed to the managed broker.
	 * @return
	 */
	private Properties _getEffectiveBrokerProps() {
		
		Properties props = new Properties();

		// start with the brokerProps specified by the user
		// these may be overridden by properties set later in this method
		for (Iterator<Entry<Object, Object>>  iterator = brokerProps.entrySet().iterator(); iterator.hasNext();) {
			Entry<Object, Object> thisEntry = iterator.next();
			props.put(thisEntry.getKey(), thisEntry.getValue());
		}
		
		// Set this property to tell the broker that it is being managed
		props.setProperty("imq.jmsra.managed","true");
		
		// Set this always to ensure that broker accepts connections from
		// clients
		// before it is able to establish a connection with its MasterBroker
		props.setProperty("imq.cluster.nowaitForMasterBroker", "true");
		if (isInProcess()) {
			props.setProperty("imq.service.activate", "jmsdirect");
		}
		if (brokerEnableHA) {
			props.setProperty("imq.cluster.ha", "true");
			if (clusterId != null)
				props.setProperty("imq.cluster.clusterid", clusterId);
		} else {
			if ((clusterBrokerList!=null) && !clusterBrokerList.equals("")){
				// set imq.cluster.brokerlist
				// override any already value set via brokerProps
				props.setProperty(BrokerConstants.PROP_NAME_BKR_CLS_BKRLIST, clusterBrokerList);
			} else if (!props.containsKey(BrokerConstants.PROP_NAME_BKR_CLS_BKRLIST) && connectionURL != null && !("".equals(connectionURL))) {
				// if it is not specified via setClusterBrokerList or setBrokerProps, set imq.cluster.brokerlist from connectionURL
				// not sure when this is needed, but it must be here for a reason
				props.setProperty(BrokerConstants.PROP_NAME_BKR_CLS_BKRLIST, connectionURL);
			}
			if ((masterBroker!=null) && !masterBroker.equals("")){
				// set imq.cluster.masterbroker
				// override any already value set via brokerProps
				props.setProperty("imq.cluster.masterbroker", masterBroker);
			}
			if (nowaitForMasterBrokerTimeoutInSeconds>-1){
				String strVal = String.valueOf(nowaitForMasterBrokerTimeoutInSeconds);
				props.setProperty("imq.cluster.nowaitForMasterBrokerTimeoutInSeconds", strVal);
			}
		}

		if (dbType != null) {
			props.setProperty("imq.persist.store", "jdbc");
			if (dbProps.containsKey(FALLBACK_DATABASE_PASSWORD)) {
				props.setProperty(IMQ_FALLBACK_DATABASE_PASSWORD, dbProps.getProperty(FALLBACK_DATABASE_PASSWORD));
			}
			if (DB_TYPE_HADB.equals(dbType)) {
				props.setProperty(IMQ_JDBC_VENDOR, dbType);
				props.setProperty(IMQ_BROKERID, brokerId);
				if (dbProps.containsKey(HADB_USER)) {
					props.setProperty(IMQ_HADB_USER, dbProps.getProperty(HADB_USER));
				}
				if (dbProps.containsKey(HADB_PASSWORD)) {
					props.setProperty(IMQ_HADB_PASSWORD, dbProps.getProperty(HADB_PASSWORD));
				}

				if (dsProps.containsKey(HADB_SERVERLIST)) {
					props.setProperty(IMQ_HADB_DSPROP_SERVERLIST, dsProps.getProperty(HADB_SERVERLIST));
				}
			} else if (DB_TYPE_MYSQL.equals(dbType)) {
				props.setProperty(IMQ_JDBC_VENDOR, dbType);
				props.setProperty(IMQ_BROKERID, brokerId);
				if (dbProps.containsKey(MYSQL_USER)) {
					props.setProperty(IMQ_MYSQL_USER, dbProps.getProperty(MYSQL_USER));
				}
				if (dbProps.containsKey(MYSQL_PASSWORD)) {
					props.setProperty(IMQ_MYSQL_PASSWORD, dbProps.getProperty(MYSQL_PASSWORD));
				}

				if (dsProps.containsKey(MYSQL_URL)) {
					props.setProperty(IMQ_MYSQL_DSPROP_URL, dsProps.getProperty(MYSQL_URL));
				}
			}

		}
		
		// admin password can be set by either setAdminPassword or by setting brokerProp imq.imqcmd.password
		// decide which to use of both are set, and set default if neither are set
		String defaultAdminPassword = "admin";
		if (props.containsKey("imq.imqcmd.password")){
			if (adminPassword==null){
				adminPassword=props.getProperty("imq.imqcmd.password");
			} else {
				// password set via setAdminPassword() overrides password set by setBrokerProps()
				props.setProperty("imq.imqcmd.password",adminPassword);
			}
		} else {
			if (adminPassword==null){
				// both null, set default
				adminPassword=defaultAdminPassword;
				props.setProperty("imq.imqcmd.password", defaultAdminPassword);
			} else {
				props.setProperty("imq.imqcmd.password", adminPassword);
			}
		}
		
		return props;
	}

	/**
	 * Return the JMSService instance
	 * that a RADirect client can use to communicate with the in-VM broker.<br/>
	 * <br/>
	 * 
	 * @return the JMSService instance or null
	 */
	public synchronized JMSService _getJMSService() {

		if (ebr != null) {
			// get from the EBR 
			JMSService result = ebr.getJMSService();
			if (_getRAJMSService()==null) {
				_setRAJMSService(result);
			}
			return result;
		} else {
			// EBR unavailable
			return null;
		}
	}

	/**
	 * Static version of _getJMSService(), for use by DirectConnectionFactory#_createConnectionId 
	 * when _getJMSService() doesn't work. This seems to be here as a quick and dirty fix to a bug 
	 * and it would be good to get rid of this
	 * 
	 * @return
	 */
	public static final JMSService _getRAJMSService() {
		return jmsservice;
	}
	
	/**
	 * Save the specified JMSService in a static field, for use by DirectConnectionFactory#_createConnectionId 
	 * when _getJMSService() doesn't work. This seems to be here as a quick and dirty fix to a bug 
	 * and it would be good to be able to get rid of this
	 * 
	 * @param jmsservice
	 */
	public static void _setRAJMSService(JMSService jmsserviceArg){
		jmsservice=jmsserviceArg;
	}

	/**
	 * Specifies whether the lifecycle managed broker should start a new RMI
	 * registry<br/>
	 * This property only takes affect if the <tt>useINDIRmiServiceURL</tt>
	 * property is also set to true.<br/>
	 * Causes the broker arguments <tt>-startRmiRegistry -rmiRegistryPort</tt>
	 * <i>rmiRegistryPort</i><br/>
	 * to be used.
	 * 
	 * @param startRmiRegistry
	 *            whether the lifecycle managed broker should start a new RMI
	 *            registry
	 */
	public synchronized void setStartRmiRegistry(boolean startRmiRegistry) {
		_loggerL.entering(_className, "setStartRmiRegistry()", Boolean.valueOf(startRmiRegistry));
		if (started
				|| (_startedAtLeastOnce && (isInProcess()))) {
			_loggerL.warning(_lgrMID_WRN
					+ "setStartRmiRegistry:RA already started OR run once as EMBEDDED:Disallowing change from:"
					+ this.startRmiRegistry + ":to:" + startRmiRegistry);
			return;
		}
		this.startRmiRegistry = startRmiRegistry;
	}

	/**
	 * Specifies whether the lifecycle manager should start a RMI registry
	 * 
	 * @return startRmiRegistry
	 */
	public synchronized boolean getStartRmiRegistry() {
		_loggerL.entering(_className, "getStartRmiRegistry()", Boolean.valueOf(startRmiRegistry));
		return startRmiRegistry;
	}

	/**
	 * Specifies the rmiRegistryPort used by the lifecycle managed broker.<br/>
	 * This property only takes effect if the <tt>useINDIRmiServiceURL</tt>
	 * property is also set to true.</br> Whether a new RMI registry is started
	 * or whether an existing RMI registry is used depends on the value of the
	 * <tt>startRMIRegistry</tt> property.
	 * 
	 * @param rmiRegistryPort
	 *            the rmiRegistryPort used by the lifecycle managed brokers
	 */
	public synchronized void setRmiRegistryPort(int rmiRegistryPort) {
		_loggerL.entering(_className, "setRmiRegistryPort()", Integer.valueOf(rmiRegistryPort));
		if (started
				|| (_startedAtLeastOnce && (isInProcess()))) {
			_loggerL.warning(_lgrMID_WRN
					+ "setRmiRegistryPort:RA already started OR run once as EMBEDDED:Disallowing change from:"
					+ this.rmiRegistryPort + ":to:" + rmiRegistryPort);
			return;
		}
		this.rmiRegistryPort = rmiRegistryPort;
	}

	/**
	 * Returns the specified rmiRegistryPort to be used by the lifecycle managed
	 * broker
	 * 
	 * @return the specified rmiRegistryPort to be used by the lifecycle managed
	 *         broker
	 */
	public synchronized int getRmiRegistryPort() {
		_loggerL.entering(_className, "getRmiRegistryPort()", Integer.valueOf(rmiRegistryPort));
		return rmiRegistryPort;
	}

	/**
	 * Return the brokerType of the lifecycle managed broker.
	 * 
	 * This returns the value that was specified with
	 * {@link #setBrokerType(java.lang.String) setBrokerType}, or the default
	 * value if {@link #setBrokerType(java.lang.String) setBrokerType} was not
	 * called
	 * 
	 * @return
	 */
	public synchronized String getBrokerType() {
		return brokerType;
	}

	/**
	 * Specifies whether the broker should run in the same or a separate JVM. 
	 * Possible values of brokerType are: 
	 * <ul>
	 * <li><tt>LOCAL</tt> ({@link #BROKER_TYPE_LOCAL BROKER_TYPE_LOCAL}) - Broker will be
	 * started in a separate JVM<br/>
	 * <li><tt>EMBEDDED</tt> ({@link #BROKER_TYPE_EMBEDDED BROKER_TYPE_EMBEDDED}) -- A broker will be started in the same JVM. Identical to <tt>DIRECT</tt>.
	 * <li><tt>DIRECT</tt> ({@link #BROKER_TYPE_DIRECT BROKER_TYPE_DIRECT}) - A broker will be started in the same JVM. Identical to <tt>EMBEDDED</tt>
	 * <li><tt>REMOTE</tt> ({@link #BROKER_TYPE_REMOTE BROKER_TYPE_REMOTE}) - No broker will be started. 
	 * Calling <tt>start()</tt> and <tt>stop()</tt> will have no effect.
	 * </ul>
	 * @param brokerType One of <tt>LOCAL</tt>,<tt>EMBEDDED</tt>,<tt>DIRECT</tt> or <tt>REMOTE</tt>.
	 */
	public synchronized void setBrokerType(String brokerType) {
		_loggerL.entering(_className, "setBrokerType()", brokerType);

		if (started) {
			_loggerL.warning(_lgrMID_WRN
					+ "setBrokerType:lifecycle managed broker already started:Disallowing change from:"
					+ this.brokerType + ":to:" + brokerType);
			return;
		}
		
		if ((BROKER_TYPE_DIRECT.equals(brokerType)) || (BROKER_TYPE_EMBEDDED.equals(brokerType)) ||  (BROKER_TYPE_LOCAL.equals(brokerType)) || (BROKER_TYPE_REMOTE.equals(brokerType))) {
			this.brokerType=brokerType;
		} else {
			_loggerL.warning(_lgrMID_WRN + "setBrokerType:Invalid value:" + brokerType + ":remaining at brokerType="
					+ this.brokerType);
		}
	}

	/**
	 * Return whether the specified broker type is LOCAL
	 * 
	 * @return
	 */
	public boolean isLocal() {
		return (ResourceAdapter.BROKER_TYPE_LOCAL.equals(brokerType));
	}
	
	/**
	 * Return whether the specified broker type is DIRECT or EMBEDDED
	 * 
	 * @return
	 */
	public boolean isInProcess() {
		return (ResourceAdapter.BROKER_TYPE_DIRECT.equals(brokerType) | ResourceAdapter.BROKER_TYPE_EMBEDDED.equals(brokerType));
	}

	/**
	 * Return whether the specified broker type is REMOTE
	 * 
	 * @return
	 */
	public boolean isRemote() {
		return (ResourceAdapter.BROKER_TYPE_REMOTE.equals(brokerType));
	}

	/**
	 * Returns the instance name of the lifecycle managed broker.
	 * 
	 * @return the instance name of the lifecycle managed broker.
	 */
	public synchronized String getBrokerInstanceName() {
		_loggerL.entering(_className, "getBrokerInstanceName()", brokerInstanceName);
		return brokerInstanceName;
	}

	/**
	 * Specifies the instance name of the lifecycle managed broker.<br/>
	 * For an embedded broker this sets the <tt>-name</tt> broker argument.<br/>
	 * In the case of a local broker this is used to determine the logfile
	 * directory
	 * 
	 * @param brokerInstanceName
	 *            The Broker Home Directory
	 */
	public synchronized void setBrokerInstanceName(String brokerInstanceName) {
		_loggerL.entering(_className, "setBrokerInstanceName()", brokerInstanceName);
		if (isNameValidAlphaNumeric_(brokerInstanceName)) {
			this.brokerInstanceName = brokerInstanceName;
		} else {
			_loggerL.warning(_lgrMID_WRN + "setBrokerInstanceName:Invalid value:" + brokerInstanceName);
		}
	}

	/**
	 * Sets the brokerBindAddress for the lifecycle managed broker.<br/>
	 * This specifies the network address that the broker must bind to and is
	 * typically needed in cases where two or more hosts are available (such as
	 * when more than one network interface card is installed in a computer), If
	 * null, the broker will bind to all addresses on the host machine.<br/>
	 * <br/>
	 * This sets the <tt>imq.hostname</tt> property
	 * 
	 * @param the
	 *            brokerBindAddress for the lifecycle managed broker
	 */
	public synchronized void setBrokerBindAddress(String brokerBindAddress) {
		_loggerL.entering(_className, "setBrokerBindAddress()", brokerBindAddress);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerBindAddress:RA already started:Disallowing change from:"
					+ this.brokerBindAddress + ":to:" + brokerBindAddress);
			return;
		}
		try {
			// validate the specified address (throws an exception if invalid)
			InetAddress.getByName(brokerBindAddress);
			this.brokerBindAddress = brokerBindAddress;
		} catch (UnknownHostException e) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerBindAddress:Ignoring Invalid Address:" + brokerBindAddress
					+ ":ExceptionMsg=" + e.getMessage());
		}
	}

	/**
	 * Returns the brokerBindAddress for the lifecycle managed broker.<br/>
	 * 
	 * @return the brokerBindAddress for the lifecycle managed broker
	 */
	public synchronized String getBrokerBindAddress() {
		_loggerL.entering(_className, "getBrokerBindAddress()", brokerBindAddress);
		return brokerBindAddress;
	}

	/**
	 * Specifies the port on which the lifecycle managed broker's port mapper will listen for connections.
	 * 
	 * @param brokerPort The port on which the lifecycle managed broker's port mapper will listen for connections.
	 */
	public synchronized void setBrokerPort(int brokerPort) {
		_loggerL.entering(_className, "setBrokerPort()", Integer.valueOf(brokerPort));
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerPort:RA already started:Disallowing change from:"
					+ this.brokerPort + ":to:" + brokerPort);
			return;
		}
		this.brokerPort = brokerPort;
	}

	/**
	 * Returns the brokerPort for the lifecycle managed broker
	 * 
	 * @return the brokerPort for the lifecycle managed broker
	 */
	public synchronized int getBrokerPort() {
		_loggerL.entering(_className, "getBrokerPort()", Integer.valueOf(brokerPort));
		return brokerPort;
	}

	/**
	 * Return the maximum time (milliseconds) allowed for a local broker to start
	 * 
	 * @return the maximum time (milliseconds) allowed for a local broker to start
	 */
	public int getBrokerStartTimeout() {
		return brokerStartTimeout;
	}

	/**
	 * Specify the maximum time (milliseconds) allowed for a local broker to start
	 * 
	 * @param brokerStartTimeout
	 *            the maximum time (milliseconds) allowed for a local broker to start
	 */
	public void setBrokerStartTimeout(int brokerStartTimeout) {
		this.brokerStartTimeout = brokerStartTimeout;
	}

	/**
	 * Specifies the brokerHomeDir of the lifecycle managed broker.<br/>
	 * For an embedded broker this sets the <tt>-imqhome</tt> broker argument.<br/>
	 * In the case of a local broker this must be set to the parent of the
	 * <tt>bin</tt> directory containing the broker executable.
	 * 
	 * @param brokerHomeDir
	 *            The Broker Home Directory
	 */
	public synchronized void setBrokerHomeDir(String brokerHomeDir) {
		_loggerL.entering(_className, "setBrokerHomeDir()", brokerHomeDir);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerHomeDir:RA already started:Disallowing change from:"
					+ this.brokerHomeDir + ":to:" + brokerHomeDir);
			return;
		}
		try {
			String path = new File(brokerHomeDir).getCanonicalPath();
			this.brokerHomeDir = path;
		} catch (IOException e) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerHomeDir:Invalid value:" + brokerHomeDir + ":Exception Message="
					+ e.getMessage());
		}
	}

	/**
	 * Returns the brokerHomeDir of the lifecycle managed broker
	 * 
	 * @return The brokerHomeDir of the lifecycle managed broker
	 */
	public synchronized String getBrokerHomeDir() {
		_loggerL.entering(_className, "getBrokerHomeDir()", brokerHomeDir);
		return brokerHomeDir;
	}

	/**
	 * Specifies the lib directory for the lifecycle managed broker.<br/>
	 * This is used in the case of an embedded broker to set the
	 * <-tt>-libhome</tt> argument passed to the broker executable.
	 * 
	 * @param brokerHomeDir
	 *            the lib directory for the lifecycle managed broker.
	 */
	public synchronized void setBrokerLibDir(String brokerLibDir) {
		_loggerL.entering(_className, "setBrokerLibDir()", brokerLibDir);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerLibDir:RA already started:Disallowing change from:"
					+ this.brokerLibDir + ":to:" + brokerLibDir);
			return;
		}
		try {
			String path = new File(brokerLibDir).getCanonicalPath();
			this.brokerLibDir = path;
		} catch (IOException e) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerLibDir:Invalid value:" + brokerLibDir + ":Exception Message="
					+ e.getMessage());
		}
	}

	/**
	 * Returns the specified lib directory for the lifecycle managed broker.
	 * 
	 * @return the specified lib directory for the lifecycle managed broker.
	 */
	public synchronized String getBrokerLibDir() {
		_loggerL.entering(_className, "getBrokerLibDir()", brokerLibDir);
		return brokerLibDir;
	}

	/**
	 * Specifies the location of the var directory of the lifecycle managed
	 * broker.<br/>
	 * This sets the <tt>-varhome</tt> broker argument.<br/>
	 * 
	 * @param brokerHomeDir
	 *            The Broker var Directory
	 */
	public synchronized void setBrokerVarDir(String brokerVarDir) {
		_loggerL.entering(_className, "setBrokerVarDir()", brokerVarDir);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerVarDir:RA already started:Disallowing change from:"
					+ this.brokerVarDir + ":to:" + brokerVarDir);
			return;
		}
		try {
			String path = new File(brokerVarDir).getCanonicalPath();
			this.brokerVarDir = path;
		} catch (IOException e) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerVarDir:Invalid value:" + brokerVarDir + ":Exception Message="
					+ e.getMessage());
		}
	}

	/**
	 * Returns the specified location of the var directory of the lifecycle
	 * managed broker
	 * 
	 * @return the specified location of the var directory of the lifecycle
	 *         managed broker
	 */
	public synchronized String getBrokerVarDir() {
		_loggerL.entering(_className, "getBrokerVarDir()", brokerVarDir);
		return brokerVarDir;
	}

	/**
	 * Specifies the Java home directory for the lifecycle managed broker.<br/>
	 * This is used in the case of a local broker to set the <-tt>javahome</tt>
	 * argument passed to the broker executable and must be set to a directory
	 * containg a Java JRE.<br/>
	 * It is not used in the case of an embedded broker.
	 * 
	 * @param brokerHomeDir
	 *            the Java home directory for the lifecycle managed broker
	 */
	public synchronized void setBrokerJavaDir(String brokerJavaDir) {
		_loggerL.entering(_className, "setBrokerJavaDir()", brokerJavaDir);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerJavaDir:RA already started:Disallowing change from:"
					+ this.brokerJavaDir + ":to:" + brokerJavaDir);
			return;
		}
		try {
			String path = new File(brokerJavaDir).getCanonicalPath();
			this.brokerJavaDir = path;
		} catch (IOException e) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerJavaDir:Invalid value:" + brokerJavaDir + ":Exception Message="
					+ e.getMessage());
		}
	}

	/**
	 * Returns the specified Java home directory for the lifecycle managed
	 * broker.
	 * 
	 * @return the specified Java home directory for the lifecycle managed
	 *         broker.
	 */
	public synchronized String getBrokerJavaDir() {
		_loggerL.entering(_className, "getBrokerJavaDir()", brokerJavaDir);
		return brokerJavaDir;
	}

	/**
	 * Specifies additional command-line arguments for the lifecycle managed
	 * broker
	 * 
	 * @param brokerArgs
	 *            additional command-line arguments for the lifecycle managed
	 *            broker
	 */
	public synchronized void setBrokerArgs(String brokerArgs) {
		_loggerL.entering(_className, "setBrokerArgs()", brokerArgs);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerArgs:RA already started:Disallowing change from:"
					+ this.brokerArgs + ":to:" + brokerArgs);
			return;
		}
		this.brokerArgs = brokerArgs;
	}

	/**
	 * Returns the additional command-line arguments what were specified for the
	 * lifecycle managed broker
	 * 
	 * @return the additional command-line arguments what were specified for the
	 *         lifecycle managed broker
	 */
	public synchronized String getBrokerArgs() {
		_loggerL.entering(_className, "getBrokerArgs()", brokerArgs);
		return brokerArgs;
	}

	/**
	 * Configure the lifecycle managed broker to use a RMI registry, so that the
	 * JMX runtime uses a static JMX service URL to perform a JNDI lookup of the
	 * JMX connector stub in the RMI registry.<br/>
	 * <br/>
	 * By default, the broker does not use an RMI registry, and the JMX runtime
	 * obtains a JMX connector stub by extracting it from a dynamic JMX service
	 * URL. However, if the broker is configured to use an RMI registry, then
	 * JMX runtime uses a static JMX service URL to perform a JNDI lookup of the
	 * JMX connector stub in the RMI registry. This approach has the advantage
	 * of providing a fixed location at which the connector stub resides, one
	 * that does not change across broker startups.<br/>
	 * <br/>
	 * So if this property is set to true then an RMI registry will be
	 * configured using the port specified by the <i>rmiRegistryPort</i>
	 * property.<br/>
	 * If the <i>startRmiRegistry</i> property is set a new RMI registry is
	 * started using the broker arguments
	 * <tt>-startRmiRegistry -rmiRegistryPort</tt> <i>rmiRegistryPort</i><br/>
	 * If the <i>startRmiRegistry</i> property is not set an external RMI
	 * registry is used using the broker arguments
	 * <tt>-useRmiRegistry -rmiRegistryPort</tt> <i>rmiRegistryPort</i><br/>
	 * 
	 * @param useJNDIRmiServiceURL
	 *            whether the lifecycle managed broker should use a RMI
	 *            registry.
	 */
	public synchronized void setUseJNDIRmiServiceURL(boolean useJNDIRmiServiceURL) {
		_loggerL.entering(_className, "setUseJNDIRmiServiceURL()", Boolean.valueOf(useJNDIRmiServiceURL));
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setUseJNDIRmiServiceURL:RA already started:Disallowing change from:"
					+ this.useJNDIRmiServiceURL + ":to:" + useJNDIRmiServiceURL);
			return;
		}
		this.useJNDIRmiServiceURL = useJNDIRmiServiceURL;
	}
	
	/**
	 * Return the JMXConnectorEnv for the broker that this RA is configured to
	 * connect to
	 * 
	 * This is a HashMap whose key is "jmx.remote.credentials" and whose
	 * corresponding value is a string array containing admin username and admin
	 * password
	 * 
	 * @return The JMXConnectorEnv
	 */
	public synchronized HashMap getJMXConnectorEnv() {
		_loggerL.entering(_className, "getJMXConnectorEnv()");
		if (jmxConnectorEnv == null) {
			jmxConnectorEnv = new HashMap<String, String[]>();
			String[] credentials = new String[] { getAdminUsername(), getAdminPassword() };
			jmxConnectorEnv.put(javax.management.remote.JMXConnector.CREDENTIALS, credentials);
		}
		return jmxConnectorEnv;
	}
	
	/**
	 * Return the JMXServiceURL for the lifecycle managed broker
	 * This is a String that can be used to acquire JMX connections
	 * 
	 * Returns null if the broker is REMOTE 
	 * 
	 * @return The JMXServiceURL
	 */
	public synchronized String getJMXServiceURL() {
		_loggerL.entering(_className, "getJMXServiceURL()");
		if (!started) {
			_loggerL.warning(_lgrMID_WRN + "getJMXServiceURL:RA not started:Returning null");
			return null;
		}

		if ((jmxServiceURL == null) && !ResourceAdapter.BROKER_TYPE_REMOTE.equals(brokerType)) {
			com.sun.messaging.AdminConnectionFactory tacf = new com.sun.messaging.AdminConnectionFactory();
			try {
				tacf.setProperty(AdminConnectionConfiguration.imqAddress, "mq://"
						+ (((getBrokerBindAddress() != null) ? getBrokerBindAddress() : "localhost") + ":" + Integer
								.toString(getBrokerPort())) + "/jmxrmi");
				tacf.setProperty(AdminConnectionConfiguration.imqDefaultAdminUsername, getAdminUsername());
				tacf.setProperty(AdminConnectionConfiguration.imqDefaultAdminPassword, getAdminPassword());
			} catch (Exception e) {
				_loggerL.warning(_lgrMID_EXC + "getJMXServiceURL:Exception configuring AdminConnectionFactory:Message="
						+ e.getMessage());
			}
			try {
				JMXServiceURL _jmxserviceURL = tacf.getJMXServiceURL();
				jmxServiceURL = _jmxserviceURL.toString();
			} catch (Exception e) {
				_loggerL.warning(_lgrMID_EXC + "getJMXServiceURL:Exception:Message=" + e.getMessage());
			}
		}
		_loggerL.exiting(_className, "getJMXServiceURL()", jmxServiceURL);
		return jmxServiceURL;
	}
	
	/**
	 * Return the JMXServiceURLList for the brokers that this RA is configured
	 * to connect to. This is a String that can be used to acquire JMX
	 * connections to all brokers specified on connectionURL
	 * 
	 * @return The JMXServiceURLList
	 */
	public synchronized String getJMXServiceURLList() {
		_loggerL.entering(_className, "getJMXServiceURLList()", "For addressList = " + getConnectionURL());
		if (isJMXServiceURLListValid) {
			_loggerL.exiting(_className, "getJMXServiceURLList()", jmxServiceURLList);
			return jmxServiceURLList;
		}
		com.sun.messaging.AdminConnectionFactory tacf = new com.sun.messaging.AdminConnectionFactory();
		try {
			tacf.setProperty(AdminConnectionConfiguration.imqDefaultAdminUsername, getAdminUsername());
			tacf.setProperty(AdminConnectionConfiguration.imqDefaultAdminPassword, getAdminPassword());
		} catch (Exception e) {
			_loggerL.warning(_lgrMID_EXC + "getJMXServiceURLList:Exception configuring AdminConnectionFactory:Message="
					+ e.getMessage());
		}

		// XXXJava_5 Use StringBuilder
		StringBuffer jb = new StringBuffer(256);
		jb.append("");

		String jurl = null;
		StringTokenizer st = new StringTokenizer(getConnectionURL(), " ,");
		while (st.hasMoreTokens()) {
			String t = st.nextToken().trim();
			if (_loggerL.isLoggable(Level.FINER)) {
				_loggerL.finer(_lgrMID_INF + "getJMXServiceURLList:addressList component = " + t);
			}
			// XXX:tharakan:Needs to be resolved when ACF is updated
			// XXX: ACF currently takes a normal MQ address and defaults the
			// service name to jmxrmi
			/*
			 * if (t.indexOf(":") == -1) { t = "mq://" + t + ":7676"; } else {
			 * if (t.indexOf("://") == -1) { t = "mq://" + t; } else { t = t +
			 * ":7676"; } }
			 */
			try {
				// XXX:tharakan:This depends on AdminConnectionFactory
				// defaulting the serviceName to jmxrmi
				tacf.setProperty(AdminConnectionConfiguration.imqAddress, t);
				// tacf.setProperty(AdminConnectionConfiguration.imqAddress, t +
				// "/jmxrmi" );
				if (_loggerL.isLoggable(Level.FINER)) {
					_loggerL.finer(_lgrMID_INF + "getJMXServiceURLList:address=" + t);
				}
				jurl = tacf.getJMXServiceURL().toString();
				if (_loggerL.isLoggable(Level.FINER)) {
					_loggerL.finer(_lgrMID_INF + "getJMXServiceURLList:JMXServiceURL string for addressList component "
							+ t + " = " + jurl);
				}
				jb.append(jurl + " ");
			} catch (Exception e) {
				_loggerL.warning(_lgrMID_EXC + "getJMXServiceURLList:Exception:Message=" + e.getMessage());
			}
		}
		jmxServiceURLList = jb.toString();
		isJMXServiceURLListValid = true;
		_loggerL.exiting(_className, "getJMXServiceURLList()", jmxServiceURLList);
		return jmxServiceURLList;
	}

	/**
	 * Return whether the lifecycle managed broker has been configured to use a
	 * RMI registry.
	 * 
	 * @return whether the lifecycle managed broker has been configured to use a
	 *         RMI registry.
	 */
	public synchronized boolean getUseJNDIRmiServiceURL() {
		_loggerL.entering(_className, "getUseJNDIRmiServiceURL()", Boolean.valueOf(useJNDIRmiServiceURL));
		return useJNDIRmiServiceURL;
	}

	/**
	 * Specifies whether a SSL JMX connector should be used for the lifecycle
	 * managed broker
	 * 
	 * @param whether
	 *            a SSL JMX connector should be used for the lifecycle managed
	 *            broker
	 */
	public synchronized void setUseSSLJMXConnector(boolean useSSLJMXConnector) {
		_loggerL.entering(_className, "setUseSSLJMXConnector()", Boolean.valueOf(useSSLJMXConnector));
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setUseSSLJMXConnector:RA already started:Disallowing change from:"
					+ this.useSSLJMXConnector + ":to:" + useSSLJMXConnector);
			return;
		}
		this.useSSLJMXConnector = useSSLJMXConnector;
	}

	/**
	 * Returns whether a SSL JMX connector should be used for the lifecycle
	 * managed broker
	 * 
	 * @return whether a SSL JMX connector should be used for the lifecycle
	 *         managed broker
	 */
	public synchronized boolean getUseSSLJMXConnector() {
		_loggerL.entering(_className, "getUseSSLJMXConnector()", Boolean.valueOf(useSSLJMXConnector));
		return useSSLJMXConnector;
	}

	/**
	 * Sets the admin Username for the lifecycle managed broker
	 * 
	 * @param adminUsername
	 *            The adminUsername
	 */
	public synchronized void setAdminUsername(String adminUsername) {
		_loggerL.entering(_className, "setAdminUsername()", adminUsername);
		this.adminUsername = adminUsername;
	}

	/**
	 * Return the admin username for the lifecycle managed broker
	 * 
	 * @return The adminUsername
	 */
	public synchronized String getAdminUsername() {
		_loggerL.entering(_className, "getAdminUsername()", adminUsername);
		return adminUsername;
	}

	/**
	 * Specifies the admin password for the lifecycle managed broker
	 * 
	 * @param adminPassword
	 *            The adminPassword
	 */
	public synchronized void setAdminPassword(String adminPassword) {
		_loggerL.entering(_className, "setAdminPassword()");
		this.adminPassword = adminPassword;
	}

	/**
	 * Returns the admin password for the lifecycle managed broker
	 * 
	 * @return The admin password for the lifecycle managed broker
	 */
	public synchronized String getAdminPassword() {
		_loggerL.entering(_className, "getAdminPassword()");
		return adminPassword;
	}
	
	/**
	 * Sets the name of the admin password file for the lifecycle managed broker<br>
	 * <br>
	 * <i>Note:</i> This method is deprecated. The method <tt>setBrokerProps()</tt>
	 * should be used instead to pass password properties to the managed broker<br>
	 * <br>
	 * @param adminPassFile
	 *            The name of the admin password file for the lifecycle managed
	 *            broker
	 */
	@Deprecated
	public synchronized void setAdminPassFile(String adminPassFile) {
		_loggerL.entering(_className, "setAdminPassFile()", adminPassFile);
		this.adminPassFile = adminPassFile;
	}

	/**
	 * Returns the name of the admin password file for the lifecycle managed
	 * broker<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * <br/> 
	 * 
	 * @return The name of the admin password file
	 */
	@Deprecated
	public synchronized String getAdminPassFile() {
		_loggerL.entering(_className, "getAdminPassFile()", adminPassFile);
		return adminPassFile;
	}
	
	/**
	 * Specifies whether the lifecycle managed broker is part of a HA (enhanced)
	 * cluster
	 * 
	 * @param brokerEnableHA
	 *            whether the lifecycle managed broker is part of a HA
	 *            (enhanced) cluster
	 */
	public synchronized void setBrokerEnableHA(boolean brokerEnableHA) {
		_loggerL.entering(_className, "setBrokerEnableHA()", Boolean.valueOf(brokerEnableHA));
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerEnableHA:RA already started:Disallowing change from:"
					+ this.brokerEnableHA + ":to:" + brokerEnableHA);
			return;
		}
		this.brokerEnableHA = brokerEnableHA;
	}

	/**
	 * Returns whether the lifecycle managed broker is part of a HA (enhanced)
	 * cluster
	 * 
	 * @return whether the lifecycle managed broker is part of a HA (enhanced)
	 *         cluster
	 */
	public synchronized boolean getBrokerEnableHA() {
		_loggerL.entering(_className, "getBrokerEnableHA()", Boolean.valueOf(brokerEnableHA));
		return brokerEnableHA;
	}

	/**
	 * Specifies the cluster identifier for the lifecycle managed broker.<br/>
	 * <br/>
	 * This sets the <tt>imq.cluster.clusterid</tt> property
	 * 
	 * @param clusterId
	 *            the cluster identifier for the lifecycle managed broker
	 */
	public synchronized void setClusterId(String clusterId) {
		_loggerL.entering(_className, "setClusterId()", clusterId);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setClusterId:RA already started:Disallowing change from:" + this.clusterId
					+ ":to:" + clusterId);
			return;
		}
		if (isNameValidAlphaNumeric_(clusterId)) {
			this.clusterId = clusterId;
		} else {
			_loggerL.warning(_lgrMID_WRN + "setClusterId:Invalid value:" + clusterId);
		}
	}

	/**
	 * Returns the cluster identifier for the lifecycle managed broker.
	 * 
	 * @return the cluster identifier for the lifecycle managed broker.
	 */
	public synchronized String getClusterId() {
		_loggerL.entering(_className, "getClusterId()", clusterId);
		return clusterId;
	}

	/**
	 * If the lifecycle managed broker is to be part of a non-HA clusters,
	 * specifies a list of broker addresses belonging to the cluster.<br/>
	 * <br/>
	 * This sets the <tt>imq.cluster.brokerlist</tt> broker property
	 * 
	 * @param connectionURL
	 *            The list of broker addresses belonging to the cluster
	 */
	public synchronized void setConnectionURL(String connectionURL) {
		// XXX: work around this Logger API stripping the String after the 1st
		// 'space'
		String tConnectionURL = connectionURL;
		_loggerL.entering(_className, "setConnectionURL()", tConnectionURL);
		this.connectionURL = connectionURL;
		this.isJMXServiceURLListValid = false;
	}

	/**
	 * If the lifecycle managed broker is to be part of a non-HA clusters,
	 * returns the list of broker addresses belonging to the cluster
	 * 
	 * @return The list of broker addresses belonging to the cluster
	 */
	public synchronized String getConnectionURL() {
		_loggerL.entering(_className, "getConnectionURL()", connectionURL);
		if ("".equals(connectionURL)) {
			_loggerL.fine(_lgrMID_INF + "getConnectionURL:returning default of 'localhost' for empty connectionURL");
			return "localhost";
		} else {
			return connectionURL;
		}
	}

	/**
	 * Specify the master broker of the conventional cluster of which the managed broker is a part. 
	 * Only used if the managed broker is part of a conventional cluster which uses a master broker.<br>
	 * The string should have the format required by the broker property imq.cluster.masterbroker,
	 * which is host:port.<br>
	 * <br>
	 * If the broker is not yet started, 
	 * this value will be used to set the property imq.cluster.masterbroker on the managed broker.<br>
	 * <br>
	 * If the broker is already started, the broker will be notified of the new value. 
	 * Note that this is for checking purposes only, the brokers must have previously been informed 
	 * by sending a JMX command to the old master broker.
	 * <br>
	 * If the managed broker is the old or new master broker, a check will be made that the managed
	 * broker agrees that the the specified new master broker is indeed the master broker. If this check
	 * fails then the broker will shutdown and this method will throw an IllegalStateException.
	 * <br>
	 * If the managed broker is neither the old or new master broker than it will simply log this notification.
	 * <br>
	 * If the broker is already started and is subsequently restarted, 
	 * this value will be used to set the property imq.cluster.masterbroker on the restarted broker.
	 * <br>
	 * @param masterBroker
	 */
	public synchronized void setMasterBroker(String newMasterBroker) {
		_loggerL.entering(_className, "setMasterBroker()", newMasterBroker);

		if (started && !isRemote()){
			if (updateMasterBroker(masterBroker, newMasterBroker)){
				this.masterBroker = newMasterBroker;
			} else {
				// the error will have already been logged
				throw new IllegalStateException("Cannot update master broker");
			}
		} else {
			this.masterBroker = newMasterBroker;
		}	

	}

	/**
	 * Return the master broker of the conventional cluster of which the managed broker is a part. 
	 * Only used if the managed broker is part of a conventional cluster which uses a master broker,
	 * and setMasterBroker() has been used previously to specify the master broker. 
	 * 
	 * @return
	 */	
	public synchronized String getMasterBroker() {
		_loggerL.entering(_className, "getMasterBroker()", masterBroker);
		return masterBroker;
	}
	
	/**
	 * Return the value that, if set, is used to set the broker property imq.cluster.nowaitForMasterBrokerTimeoutInSeconds.
	 * A value of -1 means the value is unset
	 * @return
	 */
	public int getNowaitForMasterBrokerTimeoutInSeconds() {
		return nowaitForMasterBrokerTimeoutInSeconds;
	}

	/**
	 * Set the value that, if set, is used to set the broker property imq.cluster.nowaitForMasterBrokerTimeoutInSeconds 
	 * @param nowaitForMasterBrokerTimeoutInSeconds
	 */
	public void setNowaitForMasterBrokerTimeoutInSeconds(
			int nowaitForMasterBrokerTimeoutInSeconds) {
		this.nowaitForMasterBrokerTimeoutInSeconds = nowaitForMasterBrokerTimeoutInSeconds;
	}

	/**
	 * Specifies the broker identifier for the lifecycle managed broker.<br/>
	 * <br/>
	 * This is required for brokers which use a shared JDBC-based data store.
	 * For non-HA brokers it is appended to the table name, for HA brokers it is
	 * used as an additional column in the table<br/>
	 * <br/>
	 * This sets the broker property <tt>imq.brokerid</tt>
	 * 
	 * @param brokerId
	 *            the broker identifier for the lifecycle managed broker
	 */
	public synchronized void setBrokerId(String brokerId) {
		_loggerL.entering(_className, "setBrokerId()", brokerId);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerId:RA already started:Disallowing change from:" + this.brokerId
					+ ":to:" + brokerId);
			return;
		}
		if (isNameValidAlphaNumeric_(brokerId)) {
			this.brokerId = brokerId;
		} else {
			_loggerL.warning(_lgrMID_WRN + "setBrokerId:Invalid value:" + brokerId);
		}
	}

	/**
	 * Returns the broker identifier for the lifecycle managed broker.<br/>
	 * 
	 * @return the broker identifier for the lifecycle managed broker
	 */
	public synchronized String getBrokerId() {
		_loggerL.entering(_className, "getBrokerId()", brokerId);
		return brokerId;
	}

	/**
	 * Specifies the database type for the lifecycle managed broker.<br/>
	 * Possible values are {@link #DB_TYPE_HADB DB_TYPE_HADB},
	 * {@link #DB_TYPE_ORACLE DB_TYPE_ORACLE}, {@link #DB_TYPE_POINTBASE
	 * DB_TYPE_POINTBASE} {@link #DB_TYPE_CLOUDSCAPE DB_TYPE_CLOUDSCAPE} and
	 * {@link #DB_TYPE_DERBY DB_TYPE_DERBY}<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * 
	 * @param dbType
	 *            the database type for the lifecycle managed broker
	 */
	@Deprecated
	public synchronized void setDBType(String dbType) {
		_loggerL.entering(_className, "setDBType()", dbType);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setDBType:RA already started:Disallowing change from:" + this.dbType
					+ ":to:" + dbType);
			return;
		}
		if (DB_TYPE_HADB.equals(dbType) || DB_TYPE_MYSQL.equals(dbType) || DB_TYPE_ORACLE.equals(dbType) || DB_TYPE_POINTBASE.equals(dbType)
				|| DB_TYPE_CLOUDSCAPE.equals(dbType) || DB_TYPE_DERBY.equals(dbType)) {
			this.dbType = dbType;
		} else {
			_loggerL.warning(_lgrMID_WRN + "setDBType:Invalid value:" + dbType);
		}
	}
	
	/**
	 * Specifies broker properties for the lifecycle managed broker
	 * 
	 * @param brokerProps  broker properties for the lifecycle managed broker
	 */
	public synchronized void setBrokerProps(Properties brokerProps) {
		// don't log properties as it may include passwords TODO strip passwords out from logging
		_loggerL.entering(_className, "setBrokerProps()");
		//_loggerL.entering(_className, "setBrokerProps()", brokerProps);
		if (started) {
			// don't log properties as it may include passwords TODO strip passwords out from logging
			_loggerL.warning(_lgrMID_WRN + "setBrokerProps:RA already started:Disallowing change of broker properties");
//			_loggerL.warning(_lgrMID_WRN + "setDBProps:RA already started:Disallowing change from:"
//					+ this.brokerProps.toString() + ":to:" + brokerProps.toString());
			return;
		}
				
		this.brokerProps = brokerProps;
	}
	
	/**
	 * Specifies broker properties for the lifecycle managed broker 
	 * @param aString StringReader containing broker properties for the lifecycle managed broker
	 */
	public synchronized void setBrokerProps(String aString) {
		Properties props = new Properties();
		try {
	        byte[] bytes = aString.getBytes();
	        ByteArrayInputStream byteInStream = new ByteArrayInputStream (bytes);
	        props.load(byteInStream); 
		} catch (IOException e) {
			_loggerL.log(Level.WARNING,_lgrMID_WRN + "setBrokerProps: Exception reading properties as string",e);			
		}
		setBrokerProps(props);
	}

	/**
	 * Returns the broker properties for the lifecycle managed broker
	 * 
	 * @return the broker properties for the lifecycle managed broker
	 */
	public synchronized Properties getBrokerProps() {
		// don't log properties as it may include passwords TODO strip passwords out from logging
		_loggerL.entering(_className, "getBrokerProps()");
		//_loggerL.entering(_className, "getBrokerProps()", brokerProps);
		return brokerProps;
	}
	
	/**
	 * Define or update a comma-separated list of broker addresses defining the membership of the conventional cluster
	 * of which the managed broker is a part. The string should have the format required by the broker property imq.cluster.brokerlist.<br>
	 * <br>
	 * If the broker is not yet started, 
	 * this value will be used to set the property imq.cluster.brokerlist on the managed broker.<br>
	 * <br>
	 * If the broker is already started, the broker will be notified of the new value<br>
	 * <br>
	 * If the broker is already started and is subsequently restarted, 
	 * this value will be used to set the property imq.cluster.brokerlist on the restarted broker.
	 * <br>
	 * @param clusterBrokerList
	 */
	public void setClusterBrokerList(String clusterBrokerList){
		_loggerL.entering(_className, "setClusterBrokerList()", clusterBrokerList);
		this.clusterBrokerList=clusterBrokerList;
		
		if (started && !isRemote()){
			// notify the managed broker of the updated broker list
			updateClusterBrokerList(clusterBrokerList);
		}	
	}
	
	/**
	 * Notify the managed broker of an updated broker list.
	 * Note: This private method is for use by setClusterBrokerList only
	 * 
	 * @param newClusterBrokerList
	 */
	private void updateClusterBrokerList(String newClusterBrokerList) {

		int timeout = 60000;
		MessageConsumer replyReceiver = null;
		Message replyMessage = null;

		// Create an admin connection to the managed broker
		com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
		((com.sun.messaging.ConnectionFactory) connectionFactory)
				.setConnectionType(com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_ADMIN);
		Connection connection;
		try {
			connectionFactory.setProperty("imqAddressList", getBrokerUrl()+"/admin");
			connection = connectionFactory.createConnection(getAdminUsername(), getAdminPassword());
		} catch (JMSException e) {
			_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Cannot send updated broker list to managed broker: error creating connection: ",e);
			return;
		}

		try {
			try {
				Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

				// get ready to receive reply
				Queue replyQueue = session.createTemporaryQueue();
				replyReceiver = session.createConsumer(replyQueue);
				connection.start();

				// Send the update to the broker
				Queue adminQueue = session.createQueue(MessageType.JMQ_ADMIN_DEST);
				MessageProducer sender = session.createProducer(adminQueue);
				ObjectMessage updateMessage = session.createObjectMessage();
				updateMessage.setJMSReplyTo(replyQueue);
				updateMessage.setIntProperty(MessageType.JMQ_MESSAGE_TYPE, MessageType.UPDATE_CLUSTER_BROKERLIST);
				updateMessage.setStringProperty(MessageType.JMQ_CLUSTER_BROKERLIST, newClusterBrokerList);
				sender.send(updateMessage, DeliveryMode.NON_PERSISTENT, 4, 0);

			} catch (JMSException e) {
				_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Cannot send updated broker list to managed broker: error sending update: ",e);
				return;
			}

			try {
				// now wait for the reply
				replyMessage = replyReceiver.receive(timeout);

				if (replyMessage == null) {
					// timed out
					_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Error sending updated broker list to managed broker: no reply received from broker after "+timeout+"ms");
					return;
				}

				if (replyMessage.getIntProperty("JMQStatus") != 200
						&& replyMessage.getIntProperty("JMQMessageType") != MessageType.UPDATE_CLUSTER_BROKERLIST_REPLY) {
					_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Error sending updated broker list to managed broker. " +
							"Reply received from broker: JMQStatus=" + replyMessage.getIntProperty("JMQStatus") + ", " +
							"JMQMessageTypee="+ replyMessage.getIntProperty("JMQMessageType"));
				}

			} catch (JMSException e) {
				_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Error sending updated broker list to managed broker: error receiving reply from broker: ",e);
				return;
			}

		} finally {
			try {
				connection.close();
			} catch (JMSException e) {
				_loggerL.log(Level.WARNING,_lgrMID_WRN + "setClusterBrokerList(): Error closing connection to broker after successful update: ",e);

			}
		}
	}
	
	/**
	 * Notify the managed broker of an updated master broker
	 * Note: This private method is for use by setMasterBroker() only
	 * 
	 * @param newMasterBroker
	 */
	private boolean updateMasterBroker(String oldMasterBroker,String newMasterBroker) {

		int timeout = 60000;
		MessageConsumer replyReceiver = null;
		Message replyMessage = null;

		// Create an admin connection to the managed broker
		com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
		((com.sun.messaging.ConnectionFactory) connectionFactory)
				.setConnectionType(com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_ADMIN);
		Connection connection;
		try {
			connectionFactory.setProperty("imqAddressList", getBrokerUrl()+"/admin");
			connection = connectionFactory.createConnection(getAdminUsername(), getAdminPassword());
		} catch (JMSException e) {
			_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setMasterBroker(): Cannot send updated master broker to managed broker: error creating connection: ",e);
			return false;
		}

		try {
			try {
				Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

				// get ready to receive reply
				Queue replyQueue = session.createTemporaryQueue();
				replyReceiver = session.createConsumer(replyQueue);
				connection.start();

				// Send the update to the broker
				Queue adminQueue = session.createQueue(MessageType.JMQ_ADMIN_DEST);
				MessageProducer sender = session.createProducer(adminQueue);
				ObjectMessage updateMessage = session.createObjectMessage();
				updateMessage.setJMSReplyTo(replyQueue);
				updateMessage.setIntProperty(MessageType.JMQ_MESSAGE_TYPE, MessageType.CHANGE_CLUSTER_MASTER_BROKER);
				updateMessage.setStringProperty(MessageType.JMQ_CLUSTER_OLD_MASTER_BROKER, oldMasterBroker);
				updateMessage.setStringProperty(MessageType.JMQ_CLUSTER_NEW_MASTER_BROKER, newMasterBroker);
				updateMessage.setBooleanProperty(MessageType.JMQ_JMSRA_MANAGED_BROKER, true);
				updateMessage.setBooleanProperty(MessageType.JMQ_JMSRA_NOTIFICATION_ONLY, true);
				sender.send(updateMessage, DeliveryMode.NON_PERSISTENT, 4, 0);

			} catch (JMSException e) {
				_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setMasterBroker(): Cannot send updated master broker to managed broker: error sending update: ",e);
				return false;
			}

			try {
				// now wait for the reply
				replyMessage = replyReceiver.receive(timeout);

				if (replyMessage == null) {
					// timed out
					_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setMasterBroker(): Error sending updated master broker to managed broker: no reply received from broker after "+timeout+"ms");
					return false;
				}

				if (replyMessage.getIntProperty("JMQStatus") != 200
						&& replyMessage.getIntProperty("JMQMessageType") != MessageType.CHANGE_CLUSTER_MASTER_BROKER_REPLY) {
					_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setClusterBrokerList(): Error sending updated broker list to managed broker. " +
							"Reply received from broker: JMQStatus=" + replyMessage.getIntProperty("JMQStatus") + ", " +
							"JMQMessageTypee="+ replyMessage.getIntProperty("JMQMessageType"));
					return false;
				}

			} catch (JMSException e) {
				_loggerL.log(Level.SEVERE,_lgrMID_ERR + "setMasterBroker(): Error sending updated broker list to managed broker: error receiving reply from broker: ",e);
				return false;
			}

		} finally {
			try {
				connection.close();
			} catch (JMSException e) {
				_loggerL.log(Level.WARNING,_lgrMID_WRN + "setMasterBroker(): Error closing connection to broker after successful update: ",e);

			}
		}
		return true;
	}	
		
	/**
	 * Return a URL (imqAddressList) which can be used to connect to the managed broker for administrative purposes.
	 * This should be of the form host:port. Note that callers may append "/admin" (etc) to this URL
	 * @return
	 */
	private String getBrokerUrl(){
        return ((brokerBindAddress == null) ? "localhost" : brokerBindAddress ) + ":" + brokerPort;
	}

	/**
	 * Return the list of broker addresses defining the membership of the conventional cluster
	 * of which the managed broker is a part.
	 * @return
	 */
	public String getClusterBrokerList(){
		_loggerL.entering(_className, "getClusterBrokerList()", clusterBrokerList);
		return clusterBrokerList;
	}

	/**
	 * Returns the database type for the lifecycle managed broker.<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * 
	 * @return the database type for the lifecycle managed broker
	 */
	@Deprecated
	public synchronized String getDBType() {
		_loggerL.entering(_className, "getDBType()", dbType);
		return dbType;
	}

	/**
	 * Specifies database type-specific config properties for the lifecycle
	 * managed broker<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * 
	 * @param dbProps
	 *            database type-specific config properties for the lifecycle
	 *            managed broker
	 */
	@Deprecated
	public synchronized void setDBProps(Properties dbProps) {
		_loggerL.entering(_className, "setDBProps()", dbProps);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setDBProps:RA already started:Disallowing change from:"
					+ this.dbProps.toString() + ":to:" + dbProps.toString());
			return;
		}
		this.dbProps = dbProps;
	}

	/**
	 * Returns the database type-specific config properties for the lifecycle
	 * managed broker<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * 
	 * @return the database type-specific config properties for the lifecycle
	 *         managed broker
	 */
	@Deprecated
	public synchronized Properties getDBProps() {
		_loggerL.entering(_className, "getDBProps()", dbProps);
		return dbProps;
	}

	/**
	 * Specifies dataSource-specific properties for the lifecycle-managed broker<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * @param dsProps
	 *            dataSource-specific properties for the lifecycle-managed
	 *            broker
	 */
	@Deprecated
	public synchronized void setDSProps(Properties dsProps) {
		_loggerL.entering(_className, "setDSProps()", dsProps);
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setDSProps:RA already started:Disallowing change from:"
					+ this.dsProps.toString() + ":to:" + dsProps.toString());
			return;
		}
		this.dsProps = dsProps;
	}

	/**
	 * Returns the dataSource-specific properties for the lifecycle-managed
	 * broker<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * 
	 * @return the dataSource-specific properties for the lifecycle-managed
	 *         broker
	 */
	@Deprecated
	public synchronized Properties getDSProps() {
		_loggerL.entering(_className, "getDSProps()", dsProps);
		return dsProps;
	}

	/**
	 * <p>
	 * Return whether a the lifecycle managed broker should start a PortMapper
	 * thread listening on the configured PortMapper port.
	 * </p>
	 * <p>
	 * This should be set to false if this is an embedded broker and a proxy
	 * port mapper will be listening on this port instead.
	 * </p>
	 * 
	 * @param whether
	 *            a the lifecycle managed broker should start a PortMapper
	 *            thread listening on the configured PortMapper port.
	 */
	public boolean isDoBind() {
		return doBind;
	}

	/**
	 * <p>
	 * Specifies whether a the lifecycle managed broker should start a
	 * PortMapper thread listening on the configured PortMapper port.
	 * </p>
	 * <p>
	 * Set to false if this is an embedded broker and a proxy port mapper will
	 * be listening on this port instead.
	 * </p>
	 * <p>
	 * This has no affect on a local or remote broker.
	 * </p>
	 * 
	 * @param whether
	 *            a the lifecycle managed broker should start a PortMapper
	 *            thread listening on the configured PortMapper port.
	 */
	public void setDoBind(boolean doBind) {
		this.doBind = doBind;
	}

	/**
	 * <p>
	 * If an external proxy port mapper is being used to listen for connections
	 * to the port mapper, then after accepting the connection it should forward
	 * the new client socket to the PortMapperClientHandler returned by this
	 * method.
	 * </p>
	 * 
	 * <p>
	 * This method should not be called except in this case.
	 * </p>
	 * 
	 * <p>
	 * This method should only be called after the in-process broker has been
	 * started. If it is called before the broker has been started, or if the
	 * broker type is LOCAL or REMOTE, then a
	 * <tt>java.lang.IllegalStateException</tt> will be thrown.
	 * </p>
	 * 
	 * @return
	 */
	public PortMapperClientHandler getPortMapperClientHandler() {

		if (isRemote() || isLocal()) {
			throw new IllegalStateException("Cannot access PortMapperClientHandler for LOCAL or REMOTE brokers");
		}

		if (isStarted()) {
			return Globals.getPortMapper();
		} else {
			throw new IllegalStateException(
					"Cannot access PortMapperClientHandler until embedded broker has been started ");
		}
	}

	/**
	 * Validates a string to be a valid name (alphanumeric + '_' only)
	 * 
	 * @param name
	 *            The string to be validated.
	 * 
	 * @return <code>true</code> if the name is valid; <code>false</code> if the
	 *         name is invalid.
	 */
	private static final boolean isNameValidAlphaNumeric_(String name) {
		// Invalid if name is null or empty.
		if (name == null || "".equals(name)) {
			return false;
		}
		char[] namechars = name.toCharArray();
		for (int i = 0; i < namechars.length; i++) {
			if (!(Character.isLetterOrDigit(namechars[i])) && (namechars[i] != '_')) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns a string representation of this object, suitable for use when
	 * debugging
	 */
	public String toString() {
		return ("SJSMQ LifecycleManagedBroker configuration=\n" + "\tbrokerInstanceName       =" + brokerInstanceName
				+ "\n" + "\tbrokerBindAddress        =" + brokerBindAddress + "\n" + "\tbrokerPort               ="
				+ brokerPort + "\n" + "\tbrokerHomeDir            =" + brokerHomeDir + "\n"
				+ "\tbrokerLibDir             =" + brokerLibDir + "\n" + "\tbrokerVarDir             =" + brokerVarDir
				+ "\n" + "\tbrokerJavaDir            =" + brokerJavaDir + "\n" + "\tbrokerArgs               ="
				+ brokerArgs + "\n" + "\tMasterBroker             =" + masterBroker + "\n"
				+ "\tbrokerId                 =" + brokerId + "\n" 
				+ "\tadminUsername            =" + adminUsername + "\n" + "\tadminPassword            ="
				+ ("admin".equals(this.adminPassword) ? "<default>" : "<modified>") + "\n"
				+ "\tadminPassFile            =" + adminPassFile + "\n" + "\tConnectionURL            ="
				+ connectionURL + "\n" + "\tdbType                   =" + dbType + "\n"
				+ "\tdbProps                  =" + (dbProps != null ? dbProps.toString() : "null") + "\n"
				+ "\tdsProps                  =" + (dsProps != null ? dsProps.toString() : "null") + "\n"
				+ "\tuseJNDIRmiServiceURL     =" + useJNDIRmiServiceURL + "\n" + "\tuseSSLJMXConnector       ="
				+ useSSLJMXConnector + "\n" + "\tbrokerEnableHA           =" + brokerEnableHA + "\n"
				+ "\tclusterId                =" + clusterId + "\n" + "\trmiRegistryPort          =" + rmiRegistryPort
				+ "\n" + "\tstartRmiRegistry         =" + startRmiRegistry + "\n" + "\tbrokerStartTimeout       ="
				+ "\tjmxServiceURL            =" + jmxServiceURL + "\n" 
				+ brokerStartTimeout + "\n");
	}
	
	/**
	 * An instance of EffectiveBrokerProps can be used to obtain the current values of the broker properties,
	 * using the PropertiesHolder pattern. Simply call getProperties() and the current version of the 
	 * broker properties will be returned. It is more elegant to pass a special factory object like this
	 * around than the whole instance of LifecycleManagedBroker.
	 */
	public class EffectiveBrokerProps implements PropertiesHolder {

		public Properties getProperties() {
			return _getEffectiveBrokerProps();
		}
		
	}

}
