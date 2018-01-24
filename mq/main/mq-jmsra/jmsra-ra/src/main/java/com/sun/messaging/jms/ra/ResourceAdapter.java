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

package com.sun.messaging.jms.ra;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkManager;
import javax.transaction.xa.XAResource;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.jmsclient.XAConnectionImpl;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.util.service.PortMapperClientHandler;
import com.sun.messaging.jms.blc.LifecycleManagedBroker;
import com.sun.messaging.jms.ra.api.JMSRAResourceAdapter;

/**
 * <p>This is JMSRA, the native JMS resource adapter for Oracle Glassfish Server Message
 * Queue.
 * <p>It can be used with or without MQ broker lifecycle support. 
 * This is determined by the value of the <tt>manageBrokerLifecycle</tt>
 * JavaBean property.
 * <p>If the property <tt>manageBrokerLifecycle</tt> is set to <tt>true</tt>
 * (default), then: 
 * <ul>
 * <li>this resource adapter will manage the starting and stopping
 * of a MQ broker. The brokerType property should be set to <tt>DIRECT</tt>/
 * <tt>EMBEDDED</tt>, <tt>LOCAL</tt> or <tt>REMOTE</tt> as required. 
 * If <tt>REMOTE</tt> is specified then it is assumed that the broker is already
 * running. <br>
 <li>it is legal to
 * set any of the properties needed to configure the broker lifecycle, even in
 * <tt>REMOTE</tt> mode (this is to support old versions of app server)<br>
 * </ul>
 * <p>If the property <tt>manageBrokerLifecycle</tt> is set to <tt>false</tt> then:
 * <ul>
 * <li>this resource adapter will not manage the starting and stopping of a MQ
 * broker and will expect a broker to be already running.<br>
 * <li>an attempt to
 * set any of the properties needed to configure the broker lifecycle will cause
 * an Exception.
 * </ul>
 */
public class ResourceAdapter implements javax.resource.spi.ResourceAdapter, 
		java.io.Serializable, 
		com.sun.messaging.jms.notification.EventListener {

	/**
	 * Whether this resource adapter should manage the broker lifecycle. See the
	 * class comment for more information.
	 */
	boolean manageBrokerLifecycle = true;

	/* Unique ID acquired from the broker that this RA connects to */
	// This ID is used to identify the 'Namespace' that this RA is generating
	// connections from.
	// The MQ Broker will enforce JMS Semantics of ClientID within 'Namespace'
	// but will allow sharing of the same ClientID in other 'Namespaces'.
	private String raUID = null;

	/* passed by the Java EE server; used by RA to acquire WorkManager etc. */
	private transient BootstrapContext b_context = null;

	/* WorkManager used to execute message delivery. */
	protected transient WorkManager workMgr = null;

	/* Indicates that RA was successfully started */
	private transient boolean started;

	/* hashmaps */
	private transient HashMap<Integer, MessageEndpointFactory> epFactories = null;
	private transient HashMap<Integer, EndpointConsumer> epConsumers = null;
	private transient HashMap<Integer, Integer> epFactoryToConsumer = null;

	/* IDs */
	private transient int _factoryID = 0;
	private transient int _consumerID = 0;

	/* the ConnectionFactory for this RA */
	private transient com.sun.messaging.XAConnectionFactory xacf = null;

	/* the message listener method */
	private transient Method onMessage = null;

	/* Loggers */
	private static transient final String _className = "com.sun.messaging.jms.ra.ResourceAdapter";
	protected static transient final String _lgrNameBase = "javax.resourceadapter.mqjmsra";
	protected static transient final String _lgrNameLifecycle = "javax.resourceadapter.mqjmsra.lifecycle";
	protected static transient final String _lgrNameInboundMessage = "javax.resourceadapter.mqjmsra.inbound.message";
	protected static transient final Logger _loggerB = Logger.getLogger(_lgrNameBase);
	protected static transient final Logger _loggerL = Logger.getLogger(_lgrNameLifecycle);
	protected static transient final Logger _loggerIM = Logger.getLogger(_lgrNameInboundMessage);
	protected static transient final String _lgrMIDPrefix = "MQJMSRA_RA";
	protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
	protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
	protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
	protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
	protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

	/* Non-Managed Connection Manager */
	protected static transient final com.sun.messaging.jms.ra.ConnectionManager _cm = new com.sun.messaging.jms.ra.ConnectionManager();

	/* Version Info */
	protected static transient final Version _version = new Version();

	// Configurable attributes of the MQ RA //

	/** The UserName to be used for the MQ RA Connection */
	protected String userName = "guest";

	/** The Password to be used for the MQ RA Connection */
	protected String password = "guest";

	/* RA Failover controls */
	private boolean reconnectEnabled = false;
	private int reconnectInterval = 5000;
	private int reconnectAttempts = 6;
	private String addressListBehavior = "PRIORITY";
	private int addressListIterations = 1;

	/* Indicate whether the RA is being used in the application client container */
	private boolean inAppClientContainer = false;

	/* Indicate whether the RA is being used in a clustered container */
	private boolean inClusteredContainer = false;

	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * REMOTE.<br/>
	 * This means that this instance will not manage a broker. Calling start()
	 * and stop() will have no effect.
	 */
	public static final String BROKER_TYPE_REMOTE = LifecycleManagedBroker.BROKER_TYPE_REMOTE;
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * LOCAL.<br/>
	 * This means that when start() is called a broker will be started in a
	 * separate JVM, and a subsequent call to stop() will shut it down
	 */
	public static final String BROKER_TYPE_LOCAL = LifecycleManagedBroker.BROKER_TYPE_LOCAL;
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * EMBEDDED.<br/>
	 * This means that when start() is called a broker will be started in the
	 * same JVM.<br/>
	 * Clients running in this JVM will connect to it using TCP connections.<br/>
	 * <b>Note, however, that currently if this value is specified then DIRECT
	 * will be used instead</b><br/>
	 */
	public static final String BROKER_TYPE_EMBEDDED = LifecycleManagedBroker.BROKER_TYPE_EMBEDDED;
	
	/**
	 * Pass this value to setBrokerType() to specify that the broker type is
	 * DIRECT.<br/>
	 * This means that when start() is called a broker will be started in the
	 * same JVM.<br/>
	 * Clients running in this JVM will connect to it using direct (non-TCP)
	 * connections.DB. <br/>
	 */
	public static final String BROKER_TYPE_DIRECT = LifecycleManagedBroker.BROKER_TYPE_DIRECT;
	// If DIRECT is passed in by app server then some sort of direct mode is
	// required
	// _useAPIDirectDefault() will then be called to determine which
	// implementation will be used, APIDIRECT or RADIRECT
	// note that the app server does not pass in APIDIRECT or RADIRECT directly
	protected static final String BROKER_TYPE_SOMEDIRECT = LifecycleManagedBroker.BROKER_TYPE_DIRECT;

	/**
	 * brokerType used internally to refer to the new direct mode implementation using standard JMS API
	 * <p>Note that the application server does not use this value directly 
	 */
	protected static final String BROKER_TYPE_APIDIRECT= "APIDIRECT";
	
	/**
	 * brokerType used internally to refer to the old direct mode implementation 
	 * <p>Note that the application server does not use this value directly
	 */
	protected static final String BROKER_TYPE_RADIRECT = "RADIRECT";

	/**
	 * This is the default value returned by _isDirectDefaultWhenStandalone() if the system
	 * property imq.jmsra.direct is not set See the comment on
	 * _isDirectDefaultWhenStandalone() for more information
	 */
	private static final String DIRECT_MODE_DEFAULT_STANDALONE = "true";
	
	/**
	 * This is the default value returned by _isDirectDefaultWhenClustered() if the system
	 * property imq.jmsra.direct.clustered is not set See the comment on
	 * _isDirectDefaultWhenClustered() for more information
	 */
	private static final String DIRECT_MODE_DEFAULT_CLUSTERED = "false";	

	/**
	 * This is the default value returned by _useAPIDirectImplementation if the
	 * system property imq.jmsra.apidirect is not set See the comment on
	 * _useAPIDirectImplementation() for more information
	 */
	private static final String APIDIRECT_MODE_DEFAULT = "false";
	
	/**
	 * This is the default value returned by _isFixCR6760301() if the system
	 * property imq.jmsra.fixCR6760301 is not set See the comment on
	 * _isFixCR6760301() for more information
	 */
	private static final String FIX_CR6760301 = "true";	

	private static final String FIX_BUGDB18849350 = "true";	

	/**
	 * The Group Name assigned to this Resource Adapter..
	 * This supports deployment scenarios where multiple instances of a group are acting as a single
	 * logical unit for JMS semantics. All ResourceAdapter instances in the
	 * group must have the same Group Name.
     *
     * (For info) In GlassFish 3.1 this will be set to <domainName>#<clusterName> 
	 */
	private String groupName = null;

	/*
	 * The object that manages the lifecycle managed broker.
	 * 
	 * <br>This is created lazily if the resource adapter is configured to
	 * perform broker lifecycle management (that is, if
	 * <tt>setManageBrokerLifecycle(false)</tt> has not been called).<br>
	 * <br>
	 * Note if this property is set to <tt>true</tt> then a <tt>LifecycleManagedBroker</tt>
	 * is always created and configured, even if <tt>brokerType</tt> is set to <tt>REMOTE</tt>.<br>
	 * This is to support backwards compatibility when the caller may
	 * start setting broker lifecycle javabean properties before setting brokerType.
	 */
	private transient LifecycleManagedBroker lmb;

	public LifecycleManagedBroker getLifecycleManagedBroker() {
		if (lmb == null) {
			lmb = new LifecycleManagedBroker();
		}
		return lmb;
	}

	/*
	 * Indicate main port for broker when its lifecycle is controlled by the RA
	 * (also used by the RA itself)
	 */
	private int brokerPort = 7676;

	/*
	 * Indicate type for broker lifecycle control (also used by the RA itself)
	 */
	private String brokerType = ResourceAdapter.BROKER_TYPE_REMOTE;

	/* The ConnectionURL (used by both the RA and the managed broker) */
	private String connectionURL = "";

	/* Must declare public empty constructor */
	public ResourceAdapter() {
		_loggerL.entering(_className, "constructor()");
		started = false;
	}

	// ResourceAdapter interface methods //
	//

	/**
	 * <p>Starts this instance of the Resource Adapter. 
	 * <br>
	 * <p>If this resource adapter is configured to perform broker
	 * lifecycle management (i.e.<tt>setManageBrokerLifecycle(false)</tt> has not been called)
	 * and the configured broker type is not <tt>REMOTE</tt>, then this also starts the managed broker.
	 */
	public synchronized void start(BootstrapContext ctx) throws ResourceAdapterInternalException {

		_loggerL.entering(_className, "start()", ctx);

                JMSRAResourceAdapter.init();

		if (started) {
			_loggerL.warning(_lgrMID_WRN + "start:Previously started:Ignoring");
		} else {
			fixBrokerType();
			
			
			Version version = new Version();
			_loggerL.info(_lgrMID_INF + "GlassFish MQ JMS Resource Adapter: "+version.getRAVersion());
			_loggerL.info(_lgrMID_INF + "GlassFish MQ JMS Resource Adapter starting: broker is " + getPublicBrokerType()+ ", connection mode is " + getPublicConnectionType());
			this.b_context = ctx;

			if (b_context != null) {
				workMgr = b_context.getWorkManager();
			}

			// Try to set inAppClientContainer correctly
			_adjustInAppClientContainer();

			this.xacf = new com.sun.messaging.XAConnectionFactory();

			AccessController.doPrivileged(new PrivilegedAction<Object>() {
				public Object run() {
					System.setProperty("imq.DaemonThreads", "true");
					return null;
				}
			});

			// if we have been configured to start a managed broker, start it
			if (isManageBrokerLifecycle() && !this._isRemote()) {
				getLifecycleManagedBroker().start();
			}
			try {
				// Configure RA ConnectionFactory
				// will throw a JMSException if the properties are invalid
				configureFactory();
			} catch (JMSException jmse) {
				// We are about to fail the start
				// If we have just started a local broker then stop it
				if (getLifecycleManagedBroker().isStarted() && getLifecycleManagedBroker().isLocal()) {
					getLifecycleManagedBroker().stop();
				}

				ResourceAdapterInternalException raie = new ResourceAdapterInternalException(_lgrMID_EXC
						+ "start:Aborting:JMSException on createConnection=" + jmse.getMessage());
				raie.initCause(jmse);
				_loggerL.severe(raie.getMessage());
				jmse.printStackTrace();
				_loggerL.throwing(_className, "start()", raie);
				throw raie;
			}

			/* init hash maps */
			epFactories = new HashMap<Integer, MessageEndpointFactory>(10);
			epConsumers = new HashMap<Integer, EndpointConsumer>(10);
			epFactoryToConsumer = new HashMap<Integer, Integer>(10);

			_setOnMessageMethod();
			started = true;
			_loggerL.config(_lgrMID_INF + toString());
			_loggerL.config(_lgrMID_INF + "start:SJSMQ JMSRA Connection Factory Config="
					+ xacf.getCurrentConfiguration());
			_loggerL.info(_lgrMID_INF + "GlassFish MQ JMS Resource Adapter Started:" + getPublicBrokerType());
		}
		_loggerL.exiting(_className, "start()");
	}

	/**
	 * <p>Stops this instance of the Resource Adapter. 
	 * <br>
	 * <p>If this resource adapter instance is configured to perform broker
	 * lifecycle management (i.e.<tt>setManageBrokerLifecycle(false)</tt> has not been called)
	 * and the configured broker type is not <tt>REMOTE</tt>, then this also stops the managed broker.
	 */
	public synchronized void stop() {

		_loggerL.entering(_className, "stop()");
		if (!started) {
			_loggerL.warning(_lgrMID_WRN + "stop:Previously stopped:Ignoring");
			// Thread.dumpStack();
		} else {
			_loggerL.info(_lgrMID_INF + "GlassFish MQ JMS Resource Adapter stopping...");
			// remove all epConsumers
			removeAllConsumers();

			if (_cm != null) {
				_cm.destroyConnections();
			}

			// if we have been configured to start a managed broker, stop it
			if (isManageBrokerLifecycle() && !this._isRemote()) {
				getLifecycleManagedBroker().stop();
			}
			started = false;
		}
		_loggerL.info(_lgrMID_INF + "GlassFish MQ JMS Resource Adapter stopped.");
		_loggerL.exiting(_className, "stop()");
	}

	/**
	 * Activate a message endpoint. Called by the Application Server when a
	 * message-driven bean is deployed.
	 * 
	 * {@inheritDoc}
	 */
	public void endpointActivation(MessageEndpointFactory endpointFactory, javax.resource.spi.ActivationSpec spec)
			throws ResourceException {
		Object params[] = new Object[2];
		params[0] = endpointFactory;
		params[1] = spec;

		_loggerIM.entering(_className, "endpointActivation()", params);
		if (!started) {
			_loggerIM.logp(Level.SEVERE, _className, "endpointActivation()", _lgrMID_EXC
					+ "MQJMSRA not started:Aborting:", params);
			NotSupportedException nse = new NotSupportedException(
					"MQJMSRA-endpointActivation:Error:RA not started:aborting");
			_loggerIM.throwing(_className, "endpointActivation()", nse);
			throw nse;
		}
		EndpointConsumer ec;

		if ((System.getProperty("imq.jmsra.endpoint.concurrent", "false")).equals("true")) {
			ec = new ConcurrentEndpointConsumer(this, endpointFactory, spec, this._isRADirect());
		} else {
			ec = new EndpointConsumer(this, endpointFactory, spec);
		}
		try {
			ec.startMessageConsumer();
			// if (this._isDirect()) {
			// ec.startDirectConsumer();
			// } else {
			// ec.createMessageConsumer(endpointFactory, spec);
			// }
			if (_loggerIM.isLoggable(Level.FINER)) {
				_loggerIM.finer(_lgrMID_INF + "endpointActivation:createMessageConsumer:DONE:" + "fID="
						+ ec.getFactoryID() + " cID=" + ec.getConsumerID());
			}
		} catch (Exception ex) {
			_loggerIM.logp(Level.SEVERE, _className, "endpointActivation()", _lgrMID_EXC + ":Failed due to:"
					+ ex.getMessage(), params);
			NotSupportedException nse = new NotSupportedException(
					"MQJMSRA-endpointActivation:Exception on createMessageConsumer:");
			nse.initCause(ex);
			_loggerIM.throwing(_className, "endpointActivation()", nse);
			throw nse;
		}
		_loggerIM.exiting(_className, "endpointActivation()");
	}

	/**
	 * Deactivates a message endpoint Called by the Application Server when a
	 * message-driven bean is undeployed.
	 * 
	 * {@inheritDoc}
	 */
	public void endpointDeactivation(MessageEndpointFactory endpointFactory, javax.resource.spi.ActivationSpec spec) {
		Object params[] = new Object[2];
		params[0] = endpointFactory;
		params[1] = spec;

		_loggerIM.entering(_className, "endpointDeactivation()", params);
		if (!started) {
			_loggerIM.logp(Level.SEVERE, _className, "endpointDeactivation()", _lgrMID_EXC
					+ "MQJMSRA not started:Aborting:", params);
		} else {
			// Can check spec against what the ec contains and error if not
			// correct kind or match
			int factoryID = matchMessageFactory(endpointFactory);
			if (factoryID != -1) {
				int consumerID = _getConsumerIDbyFactoryID(factoryID);
				EndpointConsumer ec = _getEndpointConsumer(consumerID);
				// System.out.println("MQJMSRA-endpointDeactivation:setting deactivated");
				ec.setDeactivated();

				try {
					// System.out.println("MQJMSRA-endpointDeactivation:stopping MessageConsumerfID="+factoryID+" cID="+consumerID+" spec=\n"+spec.toString());
					if (_loggerIM.isLoggable(Level.FINER)) {
						_loggerIM.finer(_lgrMID_INF + "endpointDeactivation:stopMessageConsumer:fID=" + factoryID
								+ " cID=" + consumerID);
					}
					ec.stopMessageConsumer();
					// System.out.println("MQJMSRA-endpointDeactivation:stopped MessageConsumer");
				} catch (Exception ex) {
					// No exception for this method, print
					// System.err.println("MQJMSRA-endpointDeactivation:Error:stopMessageConsumer exception:ignoring");
					_loggerIM.logp(Level.WARNING, _className, "endpointDeactivation()", _lgrMID_WRN
							+ "Exception on stopMessageConsumer:Ignoring:", ex);
					ex.printStackTrace();
				}
				// System.out.println("MQJMSRA-endpointDeactivation:removing from maps-fID="+factoryID);
				removeFromMaps(factoryID);
			} else {
				_loggerIM
						.log(Level.WARNING, _lgrMID_WRN + "endpointDeactivation:Ignoring:Not found:" + spec.toString());
			}
		}
		_loggerIM.exiting(_className, "endpointDeactivation()");
	}

	/**
	 * Returns the XAResource array that correspond to the ActivationSpec
	 * instances parameter. Called by the Application Server when it needs to
	 * determine transaction status for these message endpoints from the
	 * Resource Adapter.
	 * 
	 * {@inheritDoc}
	 */
	public javax.transaction.xa.XAResource[] getXAResources(javax.resource.spi.ActivationSpec[] specs)
			throws ResourceException {
		_loggerL.entering(_className, "getXAResources()");
		XAResource[] xar = new XAResource[0];
		_loggerL.exiting(_className, "getXAResources()");
		return xar;
	}

	// com.sun.messaging.jms.notification.EventListener interface method
	public void onEvent(com.sun.messaging.jms.notification.Event evnt) {
		_loggerL.entering(_className, "onEvent()", evnt);
		_loggerL.info(_lgrMID_INF + "onEvent:Connection Event:" + (evnt == null ? "null" : evnt.toString()));
	}

	/**
	 * Sets the UserName for this ResourceAdapter instance
	 * 
	 * @param userName
	 *            The UserName
	 */
	public synchronized void setUserName(String userName) {
		_loggerL.entering(_className, "setUserName()", userName);
		this.userName = userName;
	}

	/**
	 * Return the UserName for this ResourceAdapter instance
	 * 
	 * @return The UserName
	 */
	public synchronized String getUserName() {
		_loggerL.entering(_className, "getUserName()", userName);
		return userName;
	}

	/**
	 * Sets the Password for this ResourceAdapter instance
	 * 
	 * @param password
	 *            The Password
	 */
	public synchronized void setPassword(String password) {
		_loggerL.entering(_className, "setPassword()");
		this.password = password;
	}

	/**
	 * Return the Password for this ResourceAdapter instance
	 * 
	 * @return The Password
	 */
	public synchronized String getPassword() {
		_loggerL.entering(_className, "getPassword()");
		return password;
	}

	/**
	 * Sets the Reconnect behavior for this Resource Adapter
	 * 
	 * @param reconnectEnabled
	 *            if true, enables reconnect behavior
	 */
	public synchronized void setReconnectEnabled(boolean reconnectEnabled) {
		_loggerL.entering(_className, "setReconnectEnabled()", Boolean.toString(reconnectEnabled));
		this.reconnectEnabled = reconnectEnabled;
	}

	/**
	 * Returns the Reconnect behavior for this Resource Adapter
	 * 
	 * @return the Reconnect behavior for this Resource Adapter
	 */
	public synchronized boolean getReconnectEnabled() {
		_loggerL.entering(_className, "getReconnectEnabled()", Boolean.toString(reconnectEnabled));
		return reconnectEnabled;
	}

	/**
	 * Sets the Reconnect interval for this Resource Adapter
	 * 
	 * @param reconnectInterval
	 *            The reconnect interval in milliseconds when reconnect is
	 *            enabled
	 */
	public synchronized void setReconnectInterval(int reconnectInterval) {
		_loggerL.entering(_className, "setReconnectInterval()", Integer.toString(reconnectInterval));
		this.reconnectInterval = reconnectInterval;
	}

	/**
	 * Returns the Reconnect interval for this Resource Adapter
	 * 
	 * @return the Reconnect interval for this Resource Adapter
	 */
	public synchronized int getReconnectInterval() {
		_loggerL.entering(_className, "getReconnectInterval()", Integer.toString(reconnectInterval));
		return reconnectInterval;
	}

	/**
	 * Sets the reconnectAttempts for this Resource Adapter
	 * 
	 * @param reconnectAttempts
	 *            The number of reconnect attempts when reconnect is enabled
	 */
	public synchronized void setReconnectAttempts(int reconnectAttempts) {
		_loggerL.entering(_className, "setReconnectAttempts()", Integer.toString(reconnectAttempts));
		this.reconnectAttempts = reconnectAttempts;
	}

	/**
	 * Returns the Reconnect attempts for this Resource Adapter
	 * 
	 * @return the reconnectAttempts for this Resource Adapter
	 */
	public synchronized int getReconnectAttempts() {
		_loggerL.entering(_className, "getReconnectAttempts()", Integer.toString(reconnectAttempts));
		return reconnectAttempts;
	}

	/**
	 * Sets the addressListBehavior for this Resource Adapter
	 * 
	 * @param addressListBehavior
	 *            The behavior of connectionURL or addressList on connection
	 *            establishment
	 */
	public synchronized void setAddressListBehavior(String addressListBehavior) {
		_loggerL.entering(_className, "setAddressListBehavior()", addressListBehavior);
		if ("RANDOM".equalsIgnoreCase(addressListBehavior)) {
			this.addressListBehavior = "RANDOM";
			;
		} else {
			this.addressListBehavior = "PRIORITY";
		}
	}

	/**
	 * Returns the addressListBehavior for this Resource Adapter
	 * 
	 * @return the addressListBehavior for this Resource Adapter
	 */
	public synchronized String getAddressListBehavior() {
		_loggerL.entering(_className, "getAddressListBehavior()", addressListBehavior);
		return addressListBehavior;
	}

	/**
	 * Sets the addressListIterations for this Resource Adapter
	 * 
	 * @param addressListIterations
	 *            The number of iterations on addressList to be attempted on
	 *            connection establishment
	 */
	public synchronized void setAddressListIterations(int addressListIterations) {
		_loggerL.entering(_className, "setAddressListIterations()", Integer.toString(addressListIterations));
		if (addressListIterations < 1) {
			_loggerL.warning(_lgrMID_WRN + "setAddressListIterations:Invalid value:"
					+ Integer.toString(addressListIterations) + ":Setting to 1");
			this.addressListIterations = 1;
		} else {
			this.addressListIterations = addressListIterations;
		}
	}

	/**
	 * Returns the addressListIterations for this Resource Adapter
	 * 
	 * @return the addressListIterations for this Resource Adapter
	 */
	public synchronized int getAddressListIterations() {
		_loggerL.entering(_className, "getAddressListIterations()", Integer.toString(addressListIterations));
		return addressListIterations;
	}

	/**
	 * Sets the Resource Adapter for use in the Application Client Container
	 * 
	 * @param inAppClientContainer
	 *            if true, indicates that it is in the ACC
	 */
	public synchronized void setInAppClientContainer(boolean inAppClientContainer) {
		_loggerL.entering(_className, "setInAppClientContainer()", Boolean.toString(inAppClientContainer));
		this.inAppClientContainer = inAppClientContainer;
		_adjustInAppClientContainer();
	}

	/**
	 * Return whether the Resource Adapter is being used in the App Client Cont.
	 * 
	 * @return true if being used in the App Client Cont.
	 */
	public synchronized boolean getInAppClientContainer() {
		_loggerL.entering(_className, "getInAppClientContainer()", Boolean.toString(inAppClientContainer));
		return inAppClientContainer;
	}

	/**
	 * Sets the RA for use in a Clustered Java EE Container
	 * 
	 * @param inClusteredContainer
	 *            if true, indicates that it is in a Clustered Java EE Containe.
	 */
	public synchronized void setInClusteredContainer(boolean inClusteredContainer) {
		_loggerL.entering(_className, "setInClusteredContainer()", Boolean.toString(inClusteredContainer));
		this.inClusteredContainer = inClusteredContainer;
	}

	/**
	 * Return whether this RA is being used in a Clustered Java EE Container.
	 * 
	 * @return true if being used in a Clustered Java EE Container.
	 */
	public synchronized boolean getInClusteredContainer() {
		_loggerL.entering(_className, "getInClusteredContainer()", Boolean.toString(inClusteredContainer));
		return inClusteredContainer;
	}

	/**
	 * Sets the groupName for this ResourceAdapter
	 * 
	 * @param groupName
	 *            The Group Name
	 */
	public synchronized void setGroupName(String groupName) {
		_loggerL.entering(_className, "setGroupName()", groupName);
		this.groupName = groupName;
	}

	/**
	 * Returns the groupName for this ResourceAdapter
	 * 
	 * @return The groupName
	 */
	public synchronized String getGroupName() {
		_loggerL.entering(_className, "getGroupName()", groupName);
		return groupName;
	}
	
	
	/**
	 * Specifies how this resource adapter will connect to the MQ broker. Possible values of brokerType are: 
	 * <ul>
	 * <li><tt>LOCAL</tt> ({@link #BROKER_TYPE_LOCAL BROKER_TYPE_LOCAL}) - A normal TCP connection will be used<br/>
	 * <li><tt>EMBEDDED</tt> ({@link #BROKER_TYPE_EMBEDDED BROKER_TYPE_EMBEDDED}) -- A broker will be started in the same JVM. Identical to <tt>DIRECT</tt>.
	 * <li><tt>DIRECT</tt> ({@link #BROKER_TYPE_DIRECT BROKER_TYPE_DIRECT}) - A broker will be started in the same JVM. Identical to <tt>EMBEDDED</tt>
	 * <li><tt>REMOTE</tt> ({@link #BROKER_TYPE_REMOTE BROKER_TYPE_REMOTE}) - No broker will be started. 
	 * Calling <tt>start()</tt> and <tt>stop()<tt> will have no effect.
	 * </ul>
	 * @param brokerType One of <tt>LOCAL</tt>,<tt>EMBEDDED</tt>,<tt>DIRECT</tt> or <tt>REMOTE</tt>.
	 */

	/**
	 * Sets the brokerType for this ResourceAdapter
	 * 
	 * <br>
	 * If this broker is being used to perform broker lifecycle management then
	 * this is also passed to the underlying <tt>LifecycleManagedBroker</tt>.
	 * 
	 * @param brokerType
	 *            The type of the broker that this RA associates with
	 */
	public synchronized void setBrokerType(String brokerType) {
		_loggerL.entering(_className, "setBrokerType()", brokerType);

		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerType:RA already started:Disallowing change from:"
					+ this.brokerType + ":to:" + brokerType);
			return;
		}
		
		this.brokerType = brokerType;

		if (isManageBrokerLifecycle()) {
			getLifecycleManagedBroker().setBrokerType(brokerType);
		}
	}

	private void fixBrokerType() {
		
		if ((BROKER_TYPE_SOMEDIRECT.equals(brokerType)) || (BROKER_TYPE_EMBEDDED.equals(brokerType))) {
			// either direct or embedded
			if (getInClusteredContainer()){
				// clustered
				if (ResourceAdapter._isDirectDefaultWhenClustered()) {
					// force to direct (RADirect)
					this.brokerType = BROKER_TYPE_SOMEDIRECT;
				} else {
					// force to embedded (TCP)
					this.brokerType = BROKER_TYPE_EMBEDDED;
				}
			} else {
				// standalone
				if (ResourceAdapter._isDirectDefaultWhenStandalone()) {
					// force to direct (RADirect)
					this.brokerType = BROKER_TYPE_SOMEDIRECT;
				} else {
					// force to embedded (TCP)
					this.brokerType = BROKER_TYPE_EMBEDDED;
				}
			}
			
			if (BROKER_TYPE_SOMEDIRECT.equals(this.brokerType)) {
				// direct mode was specified or forced
				if (ResourceAdapter._useAPIDirectImplementation()) {
					// use new DIRECT mode implementation
					_loggerL.fine(_lgrMID_INF + " Using new API DIRECT mode");
					this.brokerType = BROKER_TYPE_APIDIRECT;
				} else {
					// Using old DIRECT mode implementation
					_loggerL.fine(_lgrMID_INF + " Using old JMSRA DIRECT mode");
					this.brokerType = BROKER_TYPE_RADIRECT;
				}
			}
		} else {
			if ((BROKER_TYPE_LOCAL.equals(brokerType)) || (BROKER_TYPE_REMOTE.equals(brokerType))) {
				// either local or remote
				// no need to change broker type
			} else {
				_loggerL.warning(_lgrMID_WRN + "setBrokerType:Invalid value:" + brokerType
						+ ":remaining at brokerType=" + this.brokerType);
			}
		}
	}

	/**
	 * Returns the brokerType for this ResourceAdapter
	 * 
	 * @return The brokerType
	 */
	public synchronized String getBrokerType() {
		_loggerL.entering(_className, "getBrokerType()", brokerType);
		return brokerType;
	}

	/**
	 * Return a public string that represents whether the broker is EMBEDDED (in-process), LOCAL or REMOTE
	 * 
	 * This is for logging only and is NOT a contract
	 *
	 * @return
	 */
	private synchronized String getPublicBrokerType() {
		String result;
		if (_isInProcessBroker()) {
			// here, "EMBEDDED"  simply means that the broker is in-process
			result = BROKER_TYPE_EMBEDDED;
		} else {
			result = getBrokerType();
		}
		return result;
	}
	
	/**
	 * Return a public string that represents how the RA will connect to the broker: RADirect, APIDirect or TCP
	 * 
	 * This is for logging only and is NOT a contract
	 * 
	 * @return
	 */
	public synchronized String getPublicConnectionType(){
		String result;
		if (_isRADirect()){
			result="Direct";
		} else if (_isAPIDirect()){
			result="Direct (APIDirect)";
		} else {
			result="TCP";
		}
		return result;
	}

	/**
	 * Sets the brokerInstanceName for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerInstanceName
	 *            The Instance Name that the broker must use
	 */
	public synchronized void setBrokerInstanceName(String brokerInstanceName) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerInstanceName()", brokerInstanceName);
		getLifecycleManagedBroker().setBrokerInstanceName(brokerInstanceName);
	}

	/**
	 * Returns the brokerInstanceName for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerInstanceName
	 */
	public synchronized String getBrokerInstanceName() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerInstanceName();
		_loggerL.entering(_className, "getBrokerInstanceName()", result);
		return result;
	}

	/**
	 * Sets the brokerBindAddress for both this ResourceAdapter instance and for
	 * any managed broker.
	 * <br>
	 * <p>Also used to construct (when a managed broker is being used) the value returned by <tt>_getEffectiveConnectionURL</tt>.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerBindAddress
	 *            The network address that the broker must bind to
	 */
	public synchronized void setBrokerBindAddress(String brokerBindAddress) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerBindAddress()", brokerBindAddress);
		getLifecycleManagedBroker().setBrokerBindAddress(brokerBindAddress);
	}

	/**
	 * Returns the brokerBindAddress for both this ResourceAdapter instance and
	 * for any managed broker.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerBindAddress
	 */
	public synchronized String getBrokerBindAddress() {
		checkManaged();
		String brokerBindAddress = getLifecycleManagedBroker().getBrokerBindAddress();
		_loggerL.entering(_className, "getBrokerBindAddress()", brokerBindAddress);
		return brokerBindAddress;

	}

	/**
	 * Sets the port used by a managed broker
	 * <br>
	 * <p>Also used to construct (when a managed broker is being used) the value returned by <tt>_getEffectiveConnectionURL</tt>.
     *
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerPort The port used by a managed broker
	 */
	public synchronized void setBrokerPort(int brokerPort) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerPort()", Integer.valueOf(brokerPort));
		if (started) {
			_loggerL.warning(_lgrMID_WRN + "setBrokerPort:RA already started:Disallowing change from:"
					+ this.brokerPort + ":to:" + brokerPort);
			return;
		}
		this.brokerPort = brokerPort;
		
		if (isManageBrokerLifecycle()) {
			getLifecycleManagedBroker().setBrokerPort(brokerPort);
		}
	}

	/**
	 * <p>Returns the port used by a managed broker
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return  The port used by a managed broker
	 */
	public synchronized int getBrokerPort() {
		checkManaged();
		_loggerL.entering(_className, "getBrokerPort()", Integer.valueOf(brokerPort));
		return brokerPort;
	}

	/**
	 * Sets the ConnectionURL used by this ResourceAdapter instance
	 * 
	 * <br>
	 * If this broker is being used to perform broker lifecycle management then
	 * this is also passed to the underlying <tt>LifecycleManagedBroker</tt>.
	 * 
	 * @param connectionURL
	 *            The ConnectionURL
	 */
	public synchronized void setConnectionURL(String connectionURL) {
		// XXX: work around this Logger API stripping the String after the 1st
		// 'space'
		String tConnectionURL = connectionURL;
		_loggerL.entering(_className, "setConnectionURL()", tConnectionURL);
		this.connectionURL = connectionURL;
		if (isManageBrokerLifecycle()) {
			getLifecycleManagedBroker().setConnectionURL(connectionURL);
		}
	}

	/**
	 * Return the ConnectionURL for both this ResourceAdapter instance and for
	 * any managed broker
	 * 
	 * @return The ConnectionURL
	 */
	public synchronized String getConnectionURL() {
		_loggerL.entering(_className, "getConnectionURL()", connectionURL);
//		if ("".equals(connectionURL)) {
//			_loggerL.fine(_lgrMID_INF + "getConnectionURL:returning default of 'localhost' for empty connectionURL");
//			// Nigel: Not sure when this is needed
//			return "localhost";
//		} else {
			return connectionURL;
//		}
	}

	/**
	 * Sets the brokerHomeDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerHomeDir
	 *            The Broker Home Directory
	 */
	public synchronized void setBrokerHomeDir(String brokerHomeDir) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerHomeDir()", brokerHomeDir);
		getLifecycleManagedBroker().setBrokerHomeDir(brokerHomeDir);
	}

	/**
	 * Returns the brokerHomeDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerHomeDir
	 */
	public synchronized String getBrokerHomeDir() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerHomeDir();
		_loggerL.entering(_className, "getBrokerHomeDir()", result);
		return result;
	}

	/**
	 * Sets the brokerVarDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerVarDir
	 *            The Broker VarDir Directory
	 */
	public synchronized void setBrokerVarDir(String brokerVarDir) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerVarDir()", brokerVarDir);
		getLifecycleManagedBroker().setBrokerVarDir(brokerVarDir);
	}

	/**
	 * Returns the brokerVarDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerVarDir
	 */
	public synchronized String getBrokerVarDir() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerVarDir();
		_loggerL.entering(_className, "getBrokerVarDir()", result);
		return result;
	}

	/**
	 * Sets the brokerLibDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerLibDir
	 *            The Broker LibDir Directory
	 */
	public synchronized void setBrokerLibDir(String brokerLibDir) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerLibDir()", brokerLibDir);
		getLifecycleManagedBroker().setBrokerLibDir(brokerLibDir);
	}

	/**
	 * Returns the brokerLibDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerLibDir
	 */
	public synchronized String getBrokerLibDir() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerLibDir();
		_loggerL.entering(_className, "getBrokerLibDir()", result);
		return result;
	}

	/**
	 * Sets the brokerJavaDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerJavaDir
	 *            The Broker JavaDir Directory
	 */
	public synchronized void setBrokerJavaDir(String brokerJavaDir) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerJavaDir()", brokerJavaDir);
		getLifecycleManagedBroker().setBrokerJavaDir(brokerJavaDir);
	}

	/**
	 * Returns the brokerJavaDir for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerJavaDir
	 */
	public synchronized String getBrokerJavaDir() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerJavaDir();
		_loggerL.entering(_className, "getBrokerJavaDir()", result);
		return result;
	}

	/**
	 * Sets the brokerArgs for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerArgs
	 *            The extra cmd line args to be used for the broker that this RA
	 *            controls
	 */
	public synchronized void setBrokerArgs(String brokerArgs) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerArgs()", brokerArgs);
		getLifecycleManagedBroker().setBrokerArgs(brokerArgs);
	}

	/**
	 * Returns the brokerArgs for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is only needed when the resource adapter is
	 * performing broker lifecycle management (that is, when
	 * <tt>setManageBrokerLifecycle(false)</tt> has not been called).<br>
	 * <br>
	 * If this method is called after <tt>setManageBrokerLifecycle(false)</tt>
	 * has been called then an <tt>IllegalOperationException</tt> will be
	 * thrown.<br>
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerArgs
	 */
	public synchronized String getBrokerArgs() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerArgs();
		_loggerL.entering(_className, "getBrokerArgs()", result);
		return result;
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
     *
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * <br>
	 * @param masterBroker
	 */
	public synchronized void setMasterBroker(String masterBroker) {
		checkManaged();
		_loggerL.entering(_className, "setMasterBroker()", masterBroker);
		getLifecycleManagedBroker().setMasterBroker(masterBroker);
	}
	
	/**
	 * Return the master broker of the conventional cluster of which the managed broker is a part. 
	 * Only used if the managed broker is part of a conventional cluster which uses a master broker,
	 * and setMasterBroker() has been used previously to specify the master broker. 
	 * <br>
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return
	 */	
	public synchronized String getMasterBroker() {
		checkManaged();
		String result = getLifecycleManagedBroker().getMasterBroker();
		_loggerL.entering(_className, "getMasterBroker()", result);
		return result;
	}

	/**
	 * Specify the maximum time (milliseconds) allowed for a local broker to start.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerStartTimeout
	 *            the maximum time (milliseconds) allowed for a local broker to start
	 */
	public synchronized void setBrokerStartTimeout(int brokerStartTimeout) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerStartTimeout()", brokerStartTimeout);
		getLifecycleManagedBroker().setBrokerStartTimeout(brokerStartTimeout);
	}

	/**
	 * Return the maximum time (milliseconds) allowed for a local broker to start.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * 
	 * @return the maximum time (milliseconds) allowed for a local broker to start
	 */
	public synchronized int getBrokerStartTimeout() {
		checkManaged();
		int result = getLifecycleManagedBroker().getBrokerStartTimeout();
		_loggerL.entering(_className, "getBrokerStartTimeout()", result);
		return result;
	}

	/**
	 * Sets the admin Username for both this ResourceAdapter instance and for
	 * any managed broker.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param adminUsername
	 *            The adminUsername
	 */
	public synchronized void setAdminUsername(String adminUsername) {
		checkManaged();
		_loggerL.entering(_className, "setAdminUsername()", adminUsername);
		getLifecycleManagedBroker().setAdminUsername(adminUsername);
	}

	/**
	 * Return the adminUsername for both this ResourceAdapter instance and for
	 * any managed broker.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The adminUsername
	 */
	public synchronized String getAdminUsername() {
		checkManaged();
		String adminUsername = getLifecycleManagedBroker().getAdminUsername();
		_loggerL.entering(_className, "getAdminUsername()", adminUsername);
		return adminUsername;
	}

	/**
	 * Sets the admin Password for both this ResourceAdapter instance and for
	 * any managed broker.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param adminPassword
	 *            The adminPassword
	 */
	public synchronized void setAdminPassword(String adminPassword) {
		checkManaged();
		_loggerL.entering(_className, "setAdminPassword()", adminPassword);
		getLifecycleManagedBroker().setAdminPassword(adminPassword);
	}

	/**
	 * Return the adminPassword for both this ResourceAdapter instance and for
	 * any managed broker.
	 * 
	 * <p><i>Note:</i> This method is only needed when the resource adapter is
	 * performing broker lifecycle management (that is, when
	 * <tt>setManageBrokerLifecycle(false)</tt> has not been called).<br>
	 * <br>
	 * 
	 * @return The adminPassword
	 */
	public synchronized String getAdminPassword() {
		String adminPassword = getLifecycleManagedBroker().getAdminPassword();
		_loggerL.entering(_className, "getAdminUsername()", adminPassword);
		return adminPassword;
	}

	/**
	 * Sets the admin Password File for this ResourceAdapter instance.<br>
	 * <br>
	 * <i>Note:</i> This method is deprecated. The method <tt>setBrokerProps()</tt>
	 * should be used instead to pass password properties to the managed broker<br>
	 * <br>
	 * <i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param adminPassFile
	 *            The adminPassFile
	 */
	@Deprecated
	public synchronized void setAdminPassFile(String adminPassFile) {
		checkManaged();
		_loggerL.entering(_className, "setAdminPassFile()", adminPassFile);
		getLifecycleManagedBroker().setAdminPassFile(adminPassFile);
	}

	/**
	 * Return the admin Password File for the lifecycle managed broker.<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * <br/> 
	 * <i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The adminPassFile
	 */
	@Deprecated
	public synchronized String getAdminPassFile() {
		checkManaged();

		String result = getLifecycleManagedBroker().getAdminPassFile();
		_loggerL.entering(_className, "getAdminPassFile()", result);
		return result;
	}
	
	/**
	 * Return the JMXConnectorEnv for the broker that this RA is configured to
	 * connect to.
	 * 
	 * This is a HashMap whose key is "jmx.remote.credentials" and whose
	 * corresponding value is a string array containing admin username and admin
	 * password
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The JMXConnectorEnv
	 */
	public synchronized HashMap getJMXConnectorEnv() {
		checkManaged();
		HashMap result = getLifecycleManagedBroker().getJMXConnectorEnv();
		_loggerL.entering(_className, "getJMXConnectorEnv()", result);
		return result;
	}

	/**
	 * Return the JMXServiceURLList for the brokers that this RA is configured
	 * to connect to. This is a String that can be used to acquire JMX
	 * connections to all brokers specified on connectionURL.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The JMXServiceURLList
	 */
	public synchronized String getJMXServiceURLList() {
		checkManaged();
		_loggerL.entering(_className, "getJMXServiceURLList()", "For addressList = " + getConnectionURL());
		String jmxServiceURLList = getLifecycleManagedBroker().getJMXServiceURLList();
		_loggerL.exiting(_className, "getJMXServiceURLList()", jmxServiceURLList);
		return jmxServiceURLList;
	}

	/**
	 * Return the JMXServiceURL for the broker that this RA is configured to
	 * connect to This is a String that can be used to acquire JMX connections
	 * 
	 * Returns null if the broker is REMOTE
	 * 
	 * @return The JMXServiceURL
	 */
	public synchronized String getJMXServiceURL() {
		checkManaged();
		_loggerL.entering(_className, "getJMXServiceURL()");
		return getLifecycleManagedBroker().getJMXServiceURL();
	}

	/**
	 * Sets useJNDIRmiServiceURL for this ResourceAdapter instance.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param useJNDIRmiServiceURL
	 *            The boolean value of useJNDIRmiServiceURL
	 */
	public synchronized void setUseJNDIRmiServiceURL(boolean useJNDIRmiServiceURL) {
		checkManaged();
		_loggerL.entering(_className, "setUseJNDIRmiServiceURL()", Boolean.valueOf(useJNDIRmiServiceURL));
		getLifecycleManagedBroker().setUseJNDIRmiServiceURL(useJNDIRmiServiceURL);
	}

	/**
	 * Return useJNDIRmiServiceURL of this ResourceAdapter instance
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The useJNDIRmiServiceURL
	 */
	public synchronized boolean getUseJNDIRmiServiceURL() {
		checkManaged();
		boolean result = getLifecycleManagedBroker().getUseJNDIRmiServiceURL();
		_loggerL.entering(_className, "getUseJNDIRmiServiceURL()", Boolean.valueOf(result));
		return result;
	}

	/**
	 * Sets the startRmiRegistry for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param startRmiRegistry
	 *            The RMI Registry Port
	 */
	public synchronized void setStartRmiRegistry(boolean startRmiRegistry) {
		checkManaged();
		_loggerL.entering(_className, "setStartRmiRegistry()", Boolean.valueOf(startRmiRegistry));
		getLifecycleManagedBroker().setStartRmiRegistry(startRmiRegistry);
	}

	/**
	 * Returns the startRmiRegistry for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return startRmiRegistry
	 */
	public synchronized boolean getStartRmiRegistry() {
		checkManaged();
		boolean result = getLifecycleManagedBroker().getStartRmiRegistry();
		_loggerL.entering(_className, "getStartRmiRegistry()", Boolean.valueOf(result));
		return result;
	}

	/**
	 * Sets the rmiRegistryPort for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param rmiRegistryPort
	 *            The RMI Registry Port
	 */
	public synchronized void setRmiRegistryPort(int rmiRegistryPort) {
		checkManaged();
		_loggerL.entering(_className, "setRmiRegistryPort()", Integer.valueOf(rmiRegistryPort));
		getLifecycleManagedBroker().setRmiRegistryPort(rmiRegistryPort);
	}

	/**
	 * Returns the rmiRegistryPort for this ResourceAdapter
	 * 
	 * @return The rmiRegistryPort
	 */
	public synchronized int getRmiRegistryPort() {
		checkManaged();
		int result = getLifecycleManagedBroker().getRmiRegistryPort();
		_loggerL.entering(_className, "getRmiRegistryPort()", Integer.valueOf(result));
		return result;
	}

	/**
	 * Sets useSSLJMXConnector for this ResourceAdapter instance.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param useSSLJMXConnector
	 *            The boolean value of useSSLJMXConnector
	 */
	public synchronized void setUseSSLJMXConnector(boolean useSSLJMXConnector) {
		checkManaged();
		_loggerL.entering(_className, "setUseSSLJMXConnector()", Boolean.valueOf(useSSLJMXConnector));
		getLifecycleManagedBroker().setUseSSLJMXConnector(useSSLJMXConnector);
	}

	/**
	 * Return useSSLJMXConnector of this ResourceAdapter instance.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The useSSLJMXConnector
	 */
	public synchronized boolean getUseSSLJMXConnector() {
		checkManaged();
		boolean result = getLifecycleManagedBroker().getUseSSLJMXConnector();
		_loggerL.entering(_className, "getUseSSLJMXConnector()", Boolean.valueOf(result));
		return result;
	}

	/**
	 * Sets brokerEnableHA for this ResourceAdapter instance.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerEnableHA
	 *            The boolean value of brokerEnableHA
	 */
	public synchronized void setBrokerEnableHA(boolean brokerEnableHA) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerEnableHA()", Boolean.valueOf(brokerEnableHA));
		getLifecycleManagedBroker().setBrokerEnableHA(brokerEnableHA);
	}
	

	

	/**
	 * Return brokerEnableHA of this ResourceAdapter instance.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerEnableHA
	 */
	public synchronized boolean getBrokerEnableHA() {
		checkManaged();
		boolean result = getLifecycleManagedBroker().getBrokerEnableHA();
		_loggerL.entering(_className, "getBrokerEnableHA()", Boolean.valueOf(result));
		return result;
	}
	
	
	/**
	 * Set the value that, if set, is used to set the broker property imq.cluster.nowaitForMasterBrokerTimeoutInSeconds 
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle lmanagement.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param i
	 */
	public synchronized void setNowaitForMasterBrokerTimeoutInSeconds(int i) {
		checkManaged();
		_loggerL.entering(_className, "setNowaitForMasterBrokerTimeoutInSeconds()", i);
		getLifecycleManagedBroker().setNowaitForMasterBrokerTimeoutInSeconds(i);
	}
	
	/**
	 * Return the value that, if set, is used to set the broker property imq.cluster.nowaitForMasterBrokerTimeoutInSeconds.
	 * A value of -1 means the value is unset
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return
	 */
	public synchronized int getNowaitForMasterBrokerTimeoutInSeconds(){
		checkManaged();
		int result = getLifecycleManagedBroker().getNowaitForMasterBrokerTimeoutInSeconds();
		_loggerL.entering(_className, "getNowaitForMasterBrokerTimeoutInSeconds()", result);
		return result;
	}

	/**
	 * Sets the clusterId for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param clusterId
	 *            The cluster Id that the Resource Adapter must use
	 */
	public synchronized void setClusterId(String clusterId) {
		checkManaged();
		_loggerL.entering(_className, "setClusterId()", clusterId);
		getLifecycleManagedBroker().setClusterId(clusterId);
	}

	/**
	 * Returns the clusterId for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The clusterId
	 */
	public synchronized String getClusterId() {
		checkManaged();
		String result = getLifecycleManagedBroker().getClusterId();
		_loggerL.entering(_className, "getClusterId()", result);
		return result;
	}

	/**
	 * Sets the brokerId for this ResourceAdapter.
	 * 
	 * @param brokerId
	 *            The brokerId that the Resource Adapter must use
	 */
	public synchronized void setBrokerId(String brokerId) {
		checkManaged();
		_loggerL.entering(_className, "setBrokerId()", brokerId);
		getLifecycleManagedBroker().setBrokerId(brokerId);
	}

	/**
	 * Returns the brokerId for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerId
	 */
	public synchronized String getBrokerId() {
		checkManaged();
		String result = getLifecycleManagedBroker().getBrokerId();
		_loggerL.entering(_className, "getBrokerId()", result);
		return result;
	}

	/**
	 * Sets the dbType for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * <br/>
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param dbType
	 *            The type of the broker that this RA associates with
	 */
	@Deprecated
	public synchronized void setDBType(String dbType) {
		checkManaged();
		_loggerL.entering(_className, "setDBType()", dbType);
		getLifecycleManagedBroker().setDBType(dbType);
	}

	/**
	 * Returns the dbType for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * <br/>
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The dbType
	 */
	@Deprecated
	public synchronized String getDBType() {
		checkManaged();
		String result = getLifecycleManagedBroker().getDBType();
		_loggerL.entering(_className, "getDBType()", result);
		return result;
	}
	
	/**
	 * Sets the brokerProps for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param brokerProps
	 *            The brokerProps that the Resource Adapter must use
	 */
	public synchronized void setBrokerProps(Properties brokerProps) {
		checkManaged();
		// don't log properties as it may include passwords TODO strip passwords out from logging
		_loggerL.entering(_className, "setBrokerProps()");
//		_loggerL.entering(_className, "setBrokerProps()", brokerProps);
		getLifecycleManagedBroker().setBrokerProps(brokerProps);
	}
	
	/**
	 * Specifies broker properties for the lifecycle managed broker 
	 * @param aString StringReader containing broker properties for the lifecycle managed broker
	 */
	public synchronized void setBrokerProps(String aString) {
		getLifecycleManagedBroker().setBrokerProps(aString);
	}

	/**
	 * Returns the brokerProps for this ResourceAdapter.
	 * 
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The brokerProps
	 */
	public synchronized Properties getBrokerProps() {
		checkManaged();
		// don't log properties as it may include passwords TODO strip passwords out from logging
		_loggerL.entering(_className, "getBrokerProps()");
		Properties result = getLifecycleManagedBroker().getBrokerProps();
//		_loggerL.entering(_className, "getBrokerProps()", result);
		return result;
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
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * <br>
	 * @param clusterBrokerList
	 */
	public synchronized void setClusterBrokerList(String clusterBrokerList) {
		checkManaged();
		_loggerL.entering(_className, "setClusterBrokerList()", clusterBrokerList);
		getLifecycleManagedBroker().setClusterBrokerList(clusterBrokerList);
	}

	/**
	 * Return the list of broker addresses defining the membership of the conventional cluster
	 * of which the managed broker is a part.<br>
	 * <br>
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return
	 */
	public synchronized String getClusterBrokerList() {
		checkManaged();
		String result = getLifecycleManagedBroker().getClusterBrokerList();
		_loggerL.entering(_className, "getClusterBrokerList()", result);
		return result;
	}	

	/**
	 * Sets the dbProps for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * <br/>	 
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param dbProps
	 *            The dbProps that the Resource Adapter must use
	 */
	@Deprecated
	public synchronized void setDBProps(Properties dbProps) {
		checkManaged();
		_loggerL.entering(_className, "setDBProps()", dbProps);
		getLifecycleManagedBroker().setDBProps(dbProps);
	}

	/**
	 * Returns the dbProps for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * <br/>
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The dbProps
	 */
	@Deprecated
	public synchronized Properties getDBProps() {
		checkManaged();
		Properties result = getLifecycleManagedBroker().getDBProps();
		_loggerL.entering(_className, "getDBProps()", result);
		return result;
	}

	/**
	 * Sets the dsProps for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated: callers should instead use <tt>setBrokerProps()</tt>
	 * to set the appropriate broker property directly.<br/>
	 * <br/>
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param dsProps
	 *            The dsProps that the Resource Adapter must use
	 */
	@Deprecated
	public synchronized void setDSProps(Properties dsProps) {
		checkManaged();
		_loggerL.entering(_className, "setDSProps()", dsProps);
		getLifecycleManagedBroker().setDSProps(dsProps);
	}

	/**
	 * Returns the dsProps for this ResourceAdapter.<br/>
	 * <br/>
	 * This method is deprecated because the corresponding setter method is deprecated.<br/>
	 * <br/>
	 * <i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @return The dsProps
	 */
	@Deprecated
	public synchronized Properties getDSProps() {
		checkManaged();
		Properties result = getLifecycleManagedBroker().getDSProps();
		_loggerL.entering(_className, "getDSProps()", result);
		return result;
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
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param whether
	 *            a the lifecycle managed broker should start a PortMapper
	 *            thread listening on the configured PortMapper port.
	 */
	public synchronized boolean isDoBind() {
		checkManaged();
		return getLifecycleManagedBroker().isDoBind();
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
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called.<br>
	 * 
	 * @param whether
	 *            a the lifecycle managed broker should start a PortMapper
	 *            thread listening on the configured PortMapper port.
	 */
	public synchronized void setDoBind(boolean doBind) {
		checkManaged();
		getLifecycleManagedBroker().setDoBind(doBind);
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
	 * <p><i>Note:</i> This method is needed only for broker lifecycle management.
	 * Will throw an <tt>IllegalOperationException</tt> if
	 * <tt>setManageBrokerLifecycle(false)</tt> has previously been called).<br>
	 * 
	 * @return
	 */
	public PortMapperClientHandler getPortMapperClientHandler() {
		checkManaged();
		return getLifecycleManagedBroker().getPortMapperClientHandler();
	}

	/**
	 * Returns the BootstrapContext used by this resource adapter instance
	 *
	 */
    BootstrapContext getBootstrapContext() {
        return b_context;
    }

	/**
	 * Returns the effective connection URL used by this resource adapter instance
	 * to connect to the broker. 
	 * <ul>
	 * <li>If the (effective) brokerType is APIDIRECT then the connection URL that is returned is <tt>mq://localhost/direct</tt>.
	 * 
	 * <li>If this resource adapter instance is performing broker lifecycle management and the brokerType is LOCAL, EMBEDDED
	 * or RADirect then the connection URL that is returned consists of the configured <tt>connectionURL</tt> (if set),
	 * prepended with a URL composed of <tt>localhost</tt> (or the configured <tt>brokerBindAddress</tt>, if set) and the 
	 * configured <tt>brokerPort</tt>. (ND: Not sure whether RADirect case is strictly needed).
	 * 
	 * <li>In all other cases the connection URL that is returned is the configured <tt>connectionURL</tt>.
	 * </ul>
	 */
	protected String _getEffectiveConnectionURL() {
		_loggerL.entering(_className, "_getEffectiveConnectionURL()");
		String eConnectionURL = null;
		
		if (_isAPIDirect()){
			eConnectionURL = "mq://localhost/direct";
		} else if (isManageBrokerLifecycle() && 
				(_isLocal() || _isEmbedded() || _isRADirect())) {
			// local or embedded or radirect
			// adjustment for managed brokers use 
			eConnectionURL = ((getBrokerBindAddress() != null) ? getBrokerBindAddress() : "localhost") + ":"
					+ Integer.toString(getBrokerPort());
			if ((getConnectionURL() != null) && (!("".equals(getConnectionURL())))) {
				eConnectionURL = eConnectionURL + "," + getConnectionURL();
			}
		} else {
			eConnectionURL = getConnectionURL();
		}
		_loggerL.exiting(_className, "_getEffectiveConnectionURL()", eConnectionURL);
		return eConnectionURL;
	}

	/**
	 * Returns the raUID for this ResourceAdapter
	 * 
	 * This is only available if using a remote or local broker
	 * or if using a in-process broker with TCP connections.
	 * 
	 * If using a in-process broker with RADirect connections, return null
	 * 
	 * @throws JMSException 
	 */
	protected String _getRAUID() {
	
		if (raUID==null && !_isRADirect()){
			try {
				// Note that we deliberately create this connection when first needed rather than in start() to avoid CR 6986977
				XAConnectionImpl xaci = (com.sun.messaging.jmq.jmsclient.XAConnectionImpl) xacf.createXAConnection();
				raUID = Long.toString(xaci.generateUID());
				xaci.close();
			} catch (JMSException jmse){
				_loggerB.severe("Unable to generate UID:"+jmse.getMessage());
			}
		}
		
		return raUID;
	}

	/**
	 * Returns the method that is called in the MessageListener to deliver
	 * messages to the endpoint consumer
	 */
	protected Method _getOnMessageMethod() {
		if (onMessage == null) {
			_setOnMessageMethod();
		}
		return onMessage;
	}

	/*
	 * Configure the XAConnectionFactory held by the RA
	 * 
	 * @throws JMSException Invalid properties
	 */
	private void configureFactory() throws JMSException {

		if (!BROKER_TYPE_REMOTE.equals(brokerType)) {
			xacf.setProperty(ConnectionConfiguration.imqAddressList, _getEffectiveConnectionURL());
			xacf.setProperty(ConnectionConfiguration.imqAddressListBehavior, "PRIORITY");
		} else {
			xacf.setProperty(ConnectionConfiguration.imqAddressList, getConnectionURL());
			xacf.setProperty(ConnectionConfiguration.imqAddressListBehavior, addressListBehavior);
		}
		xacf.setProperty(ConnectionConfiguration.imqDefaultUsername, userName);
		xacf.setProperty(ConnectionConfiguration.imqDefaultPassword, password);
		
		xacf.setProperty(ConnectionConfiguration.imqAddressListIterations, Integer.toString(addressListIterations));

		// Force reconnectEnabled to false for XAR succes
		xacf.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(false)); // reconnectEnabled

		xacf.setProperty(ConnectionConfiguration.imqReconnectInterval, Integer.toString(reconnectInterval));
		xacf.setProperty(ConnectionConfiguration.imqReconnectAttempts, Integer.toString(reconnectAttempts));

	}

	/* Keys for factory in epFactories hashmap */
	private int createFactoryID() {
		return ++_factoryID;
	}

	/* Keys for consumer in epConsumers hashmap */
	private int createConsumerID() {
		return ++_consumerID;
	}

	/**
	 * Adds a MessageEndpointFactory to the epFactories hashmap
	 * 
	 * @param endpointFactory
	 *            The MessageEndpointFactory to be added
	 * @return The ID assigned to the MessageEndpoitnFactory added
	 */
	protected int addMessageFactory(MessageEndpointFactory endpointFactory) {
		int factoryID = createFactoryID();
		synchronized (epFactories) {
			epFactories.put(Integer.valueOf(factoryID), endpointFactory);
		}
		return factoryID;
	}

	/**
	 * Removes a MessageEndpointFactory by ID from the epFactories hashmap
	 * 
	 * @param factoryID
	 *            The ID of the MessageEndpointFactory to be removed
	 */
	protected void removeMessageFactory(int factoryID) {
		synchronized (epFactories) {
			epFactories.remove(Integer.valueOf(factoryID));
		}
	}

	/**
	 * Returns the MessageEndpointFactory corresponding to ID from the hashmap
	 * 
	 * @param factoryID
	 *            The ID of the MessageEndpointFactory to be returned
	 * 
	 * @return MessageEndpointFactory
	 */
	protected MessageEndpointFactory _getMessageFactory(int factoryID) {
		synchronized (epFactories) {
			MessageEndpointFactory epFactory = (MessageEndpointFactory) epFactories.get(Integer.valueOf(factoryID));
			return epFactory;
		}
	}

	/**
	 * Searches for a MessageEndpointFactory in the epFactories hashmap
	 * 
	 * @param endpointFactory
	 *            The MessageEndpointFactory to find
	 * @return The ID of the MessageEndpoitnFactory in the epFactories hashmap
	 */
	private int matchMessageFactory(MessageEndpointFactory endpointFactory) {
		int key = -1;

		if (endpointFactory != null) {
			synchronized (epFactories) {

				Set<Entry<Integer, MessageEndpointFactory>>  factories = epFactories.entrySet();
				if (factories != null) {
					Iterator<Entry<Integer, MessageEndpointFactory>> iter = factories.iterator();
					while (iter.hasNext()) {
						/* iterate through the map's entries */
						 Entry<Integer, MessageEndpointFactory> thisEntry = iter.next();

						/*
						 * If the endpointFactory matches an entry in the map,
						 * we have a match. Get the key for that match.
						 */
						if (endpointFactory.equals(thisEntry.getValue())) {
							Integer iKey = (Integer) thisEntry.getKey();
							key = iKey.intValue();
							break;
						}
					}
				}
			}
		}
		return key;
	}

	/**
	 * Adds a link between factoryID for MessageEndpointFactory and consumerID
	 * (endpointConsumer) in the hashmaps
	 */
	protected void addFactorytoConsumerLink(int factoryID, int consumerID) {
		synchronized (epFactoryToConsumer) {
			epFactoryToConsumer.put(Integer.valueOf(factoryID), Integer.valueOf(consumerID));
		}
	}

	/**
	 * Removes Link between factoryID (MessageEndpointFactory) and consumerID
	 * (EndpointConsumer) in the epFactoryToConsumer hashmap
	 * 
	 * @param factoryID
	 *            MessageEndpointFactory ID for which linked consumerID
	 *            (EndpointConsumer) is to be removed
	 */
	private void removeFactorytoConsumerLink(int factoryID) {
		synchronized (epFactoryToConsumer) {
			epFactoryToConsumer.remove(Integer.valueOf(factoryID));
		}
	}

	/**
	 * Returns the ID for the EndpointConsumer that is linked by the ID for the
	 * MessageEndpointFactory
	 * 
	 * @param factoryID
	 *            The ID for the MessageEndpointFactory
	 * 
	 * @return consumerID for the EndpointConsumer linked by this factoryID
	 */
	private int _getConsumerIDbyFactoryID(int factoryID) {
		return ((Integer) epFactoryToConsumer.get(Integer.valueOf(factoryID))).intValue();
	}

	/**
	 * Adds an EndpointConsumer in the hashmap
	 * 
	 * @param endpointConsumer
	 *            The EndpointConsumer to be added
	 * @return The ID assigned to the EndpointConsumer added
	 */
	protected int addEndpointConsumer(EndpointConsumer endpointConsumer) {
		int consumerID = createConsumerID();
		synchronized (epConsumers) {
			epConsumers.put(Integer.valueOf(consumerID), endpointConsumer);
		}
		return consumerID;
	}

	/**
	 * Removes EndpointConsumer from the hashmap given consumerID
	 * 
	 * @param consumerID
	 *            for the EndpointConsumer to remove
	 */
	private void removeEndpointConsumer(int consumerID) {
		synchronized (epConsumers) {
			epConsumers.remove(Integer.valueOf(consumerID));
		}
	}

	/**
	 * Returns the EndpointConsumer for a given consumerID
	 * 
	 * @param consumerID
	 *            The ID for which the EndpointConsumer is desired
	 * 
	 * @return EndpointConsumer for consumerID
	 */
	private EndpointConsumer _getEndpointConsumer(int consumerID) {
		EndpointConsumer endpointConsumer = (EndpointConsumer) epConsumers.get(Integer.valueOf(consumerID));
		return endpointConsumer;
	}

	/**
	 * Removes references MessageEndpointFactory and linked EndpointConsumer
	 * from hashmaps
	 */
	private void removeFromMaps(int factoryID) {
		int consumerID = _getConsumerIDbyFactoryID(factoryID);
		removeEndpointConsumer(consumerID);
		removeMessageFactory(factoryID);
		removeFactorytoConsumerLink(factoryID);
	}

	/**
	 * Removes all the consumers from epConsumers after first closing them.
	 */
	private void removeAllConsumers() {
		synchronized (epFactories) {

			Set<Entry<Integer, MessageEndpointFactory>> factories = epFactories.entrySet();

			if (factories != null) {

				Iterator<Entry<Integer, MessageEndpointFactory>> iter = factories.iterator();
				while (iter.hasNext()) {
				 Entry<Integer, MessageEndpointFactory> entry = iter.next();
					int factoryID = ((Integer) entry.getKey()).intValue();
					int consumerID = _getConsumerIDbyFactoryID(factoryID);
					EndpointConsumer ec = _getEndpointConsumer(consumerID);

					try {
						ec.stopMessageConsumer();
					} catch (Exception ex) {
						// No exception for this method, print
						// XXX:log:tharakan
						System.err.println("MQJMSRA:RA::Error:stopMessageConsumer exception:ignoring");
						// ex.printStackTrace();
					}
					// removeFromMaps(factoryID);
				}
				clearMaps();
			}
		}
	}

	private void clearMaps() {

		/* clear hash maps */
		epFactories.clear();
		epConsumers.clear();
		epFactoryToConsumer.clear();
	}

	/**
	 * Sets the Method that is called in the MessageListener
	 * 
	 */
	private void _setOnMessageMethod() {
		Method onMessageMethod = null;
		try {
			Class[] paramTypes = { Message.class };
			onMessageMethod = MessageListener.class.getMethod("onMessage", paramTypes);
		} catch (NoSuchMethodException ex) {
			ex.printStackTrace();
		}
		onMessage = onMessageMethod;
	}

	/**
	 * Adjust the value of inAppClientContainer to reflect the actual
	 * capability.
	 * <p>
	 * 
	 * For test purposes, we can set imq.jms.ra.inACC to force the setting one
	 * way or another.
	 */
	private void _adjustInAppClientContainer() {
		// System.out.println("MQJMSRA:RA:AIACC()");
		String inACC_SysProp = System.getProperty("imq.jmsra.inACC");
		if (inACC_SysProp != null) {
			System.err.println("MQJMSRA:RA:AIACC:SystemProp imq.jmsra.inACC is NOT null!!");
			if ("true".equals(inACC_SysProp)) {
				System.err.println("MQJMSRA:RA:AIACC:setting inACC true");
				this.inAppClientContainer = true;
			} else {
				System.err.println("MQJMSRA:RA:AIACC:setting inACC false");
				this.inAppClientContainer = false;
			}
		} else {
			// System.out.println("MQJMSRA:RA:AIACC:SystemProp com.sun.messaging.jms.ra.inACC IS NULL!! Try WorkMgr");
			// Try to set inACC correctly depending on workMgr availability
			if (workMgr != null) {
				// System.out.println("MQJMSRA:RA:AIACC:WorkMgr is NOT null!!");
				// Try to do work
				try {
					workMgr.doWork(new Work() {
						public void run() {
							// System.out.println("MQJMSRA:RA:AIACC:Working...!");
							return;
						}

						public void release() {
							// System.out.println("MQJMSRA:RA:AIACC:Released...!");
							return;
						}
					});
					// System.out.println("MQJMSRA:RA:AIACC:leaving inACC as set since WorkMgr available and successful. inACC="+this.inAppClientContainer);
					//
					// Leave inAppClientContainer setting as set -- i.e. don't
					// change it if WorkMgr is successful
					// e.g. If Test wants it to be true - don't set it to false
					// if the workMgr is successful
					//
				} catch (Exception we) {
					// System.out.println("MQJMSRA:RA:AIACC:setting inACC true as WorkMgr available but unsuccessful");
					// Force it to be in the AppClient Container if unable to
					// use WorkMgr
					this.inAppClientContainer = true;
				}
			} else {
				// System.out.println("MQJMSRA:RA:AIACC:setting inACC true as WorkMgr unavailable");
				// Force it to be in the AppClient Container if WorkMgr is NULL
				this.inAppClientContainer = true;
			}
		}
		// System.out.println("MQJMSRA:RA:AIACC:exiting with inACC set to " +
		// this.inAppClientContainer);
	}

	/**
	 * This method is used to determine how a brokerType of DIRECT or EMBEDDED
	 * will be interpreted in the case when the broker is standalone
	 * (i.e. if inClusteredContainer=false).
	 * 
	 * If this method returns true then DIRECT mode will
	 * always be used (when the broker is standalone), 
	 * even if EMBEDDED was specified.
	 * 
	 * If it returns false then
	 * EMBEDDED mode will always be used (when the broker is standalone), 
	 * even if DIRECT was specified
	 * 
	 * The return value is obtained from the system property imq.jmsra.direct.
	 * If this property is not set then the return value is obtained from
	 * the constant ResourceAdapter.DIRECT_MODE_DEFAULT_STANDALONE. 
	 * 
	 * This means that users who specify DIRECT or EMBEDDED mode will actually
	 * get DIRECT mode (when the broker is standalone)
	 * unless they explicitly set imq.jmsra.direct to false
	 * 
	 * Note that this flag has no effect when the broker is clustered
	 * (i.e. if inClusteredContainer=true)
	 * 
	 * Note that if DIRECT mode is chosen, the function
	 * _useAPIDirectImplementation() is then called to determine whether the
	 * APIDIRECT or RADIRECT implementation is used
	 * 
	 * @return true if DIRECT or EMBEDDED mode should (when the broker is standalone) be overridden to DIRECT,
	 *         false if DIRECT or EMBEDDED mode should (when the broker is standalone) be overridden to EMBEDDED
	 * 
	 */
	private static boolean _isDirectDefaultWhenStandalone() {
		return Boolean.valueOf(System.getProperty("imq.jmsra.direct", ResourceAdapter.DIRECT_MODE_DEFAULT_STANDALONE));
	}
	
	/**
	 * This method is used to determine how a brokerType of DIRECT or EMBEDDED
	 * will be interpreted in the case when the broker is clustered
	 * (i.e. if inClusteredContainer=true).
	 * 
	 * If this method returns true then DIRECT mode will
	 * always be used (when the broker is clustered), 
	 * even if EMBEDDED was specified.
	 * 
	 * If it returns false then
	 * EMBEDDED mode will always be used (when the broker is clustered), 
	 * even if DIRECT was specified
	 * 
	 * The return value is obtained from the system property imq.jmsra.direct.clustered
	 * If this property is not set then the return value is obtained from
	 * the constant ResourceAdapter.DIRECT_MODE_DEFAULT_CLUSTERED. 
	 * 
	 * This means that users who specify DIRECT or EMBEDDED mode will actually
	 * get DIRECT mode (when the broker is clustered)
	 * unless they explicitly set imq.jmsra.direct to false
	 * 
	 * Note that this flag has no effect when the broker is standalone
	 * (i.e. if inClusteredContainer=false)
	 * 
	 * Note that if DIRECT mode is chosen, the function
	 * _useAPIDirectImplementation() is then called to determine whether the
	 * APIDIRECT or RADIRECT implementation is used
	 * 
	 * @return true if DIRECT or EMBEDDED mode should (when the broker is clustered) be overridden to DIRECT,
	 *         false if DIRECT or EMBEDDED mode should (when the broker is clustered) be overridden to EMBEDDED
	 * 
	 */
	private static boolean _isDirectDefaultWhenClustered() {
		return Boolean.valueOf(System.getProperty("imq.jmsra.direct.clustered", ResourceAdapter.DIRECT_MODE_DEFAULT_CLUSTERED));
	}	

	/**
	 * This method is used to determine which implementation will be used when
	 * DIRECT mode is chosen
	 * 
	 * If this method returns true then APIDIRECT mode will be used This is the
	 * new (4.4) direct mode implementation using standard JMS API If it returns
	 * false then RADIRECT mode will be used This is the old direct mode
	 * implementation implemented in the RA
	 * 
	 * The return value is obtained from the system property imq.jmsra.apidirect
	 * If this property is not set then the return value is obtained from
	 * ResourceAdapter.APIDIRECT_MODE_DEFAULT 
	 * 
	 * @return true if APIDIRECT mode should be used when DIRECT mode is chosen,
	 *         false if RADIRECT mode should be used when DIRECT mode is chosen
	 * 
	 */
	protected static boolean _useAPIDirectImplementation() {
		return Boolean.valueOf(System.getProperty("imq.jmsra.apidirect", ResourceAdapter.APIDIRECT_MODE_DEFAULT));
	}
		
	/**
	 * This method is used to determine whether a fix to CR 6760301 should be applied
	 * 
	 * If this method returns true then code changes to fix CR 6760301 will be executed.
	 * These were introduced but were backed out until they had been tested better
	 * 
	 * The return value is obtained from the system property imq.jmsra.fixCR6760301
	 * If this property is not set then the return value is obtained from
	 * ResourceAdapter.FIX_CR6760301
	 * 
	 * @return true if a fix to CR 6760301 should be applied
	 *         false if a fix to CR 6760301 should not be applied
	 * 
	 */
	protected static boolean _isFixCR6760301() {
		return Boolean.valueOf(System.getProperty("imq.jmsra.fixCR6760301", ResourceAdapter.FIX_CR6760301));
	}	

	/**
	 * This method is used to determine whether a fix to BugDB #18849350 
	 * should be applied. This is provided to be just in case any existing
	 * application has been depending on this long-standing "bug" behavior 
 	 * 
	 * @return true if a fix to BugDB #18849350 should be applied
	 */ 
	protected static boolean _isFixBUGDB18849350() {
		return Boolean.valueOf(System.getProperty("imq.jmsra.fixBUGDB18849350", ResourceAdapter.FIX_BUGDB18849350));
	}	

	// Info Methods
	public String toString() {
		return ("SJSMQ JMSRA ResourceAdapter configuration=\n" + "\traUID                    =" + raUID + "\n"
				+ "\tGroupName                =" + groupName + "\n" + "\tbrokerType               =" + brokerType
				+ "\tbrokerPort               =" + brokerPort + "\n" + "\tConnectionURL            =" + connectionURL
				+ "\n" + "\tRADIRECT Mode allowed    =" + this._isRADirectAllowed() + "\n"
				+ "\tAPIDIRECT Mode allowed   =" + this._isAPIDirectAllowed() + "\n" + "\tDIRECT Mode default      ="
				+ _isDirectDefaultWhenStandalone() + "\n" + "\tUse APIDIRECT impl       =" + _useAPIDirectImplementation() + "\n"
				+ "\tUserName                 =" + userName + "\n" + "\tPassword                 ="
				+ ("guest".equals(this.password) ? "<default>" : "<modified>") + "\n" + "\tReconnectEnabled         ="
				+ reconnectEnabled + "\n" + "\tReconnectInterval        =" + reconnectInterval + "\n"
				+ "\tReconnectAttempts        =" + reconnectAttempts + "\n" + "\tAddressListBehavior      ="
				+ addressListBehavior + "\n" + "\tAddressListIterations    =" + addressListIterations + "\n"
				+ "\tInAppClientContainer     =" + inAppClientContainer + "\n" + "\tInClusteredContainer     ="
				+ inClusteredContainer + "\n" + getLifecycleManagedBroker().toString());
	}

	protected static com.sun.messaging.jms.ra.ConnectionManager _getConnectionManager() {
		return _cm;
	}

	/**
	 * If the effective broker type is RADIRECT, return the JMSService instance
	 * that a RADirect client can use to communicate with the in-VM broker.<br/>
	 * <br/>
	 * For all other types of broker null is returned<br/>
	 * <br>
	 * For internal resource adapter use only<br>
	 * 
	 * @return the JMSService instance or null
	 */
	public JMSService _getJMSService() {
		JMSService result = null;
		if (getLifecycleManagedBroker() != null) {
			result = getLifecycleManagedBroker()._getJMSService();
		}
		return result;
	}

	protected static final JMSService _getRAJMSService() {
		return LifecycleManagedBroker._getRAJMSService();
	}

	public boolean _isInProcessBroker() {
		return (ResourceAdapter.BROKER_TYPE_RADIRECT.equals(brokerType) | 
				ResourceAdapter.BROKER_TYPE_APIDIRECT.equals(brokerType)| 
				ResourceAdapter.BROKER_TYPE_SOMEDIRECT.equals(brokerType)| 
				ResourceAdapter.BROKER_TYPE_EMBEDDED.equals(brokerType));
	}

	public boolean _isRADirect() {
		return (ResourceAdapter.BROKER_TYPE_RADIRECT.equals(brokerType));
	}

	public boolean _isAPIDirect() {
		return (ResourceAdapter.BROKER_TYPE_APIDIRECT.equals(brokerType));
	}

	public boolean _isEmbedded() {
		return (ResourceAdapter.BROKER_TYPE_EMBEDDED.equals(brokerType));
	}
	
	public boolean _isLocal() {
		return (ResourceAdapter.BROKER_TYPE_LOCAL.equals(brokerType));
	}

	public boolean _isRemote() {
		return (ResourceAdapter.BROKER_TYPE_REMOTE.equals(brokerType));
	}

	public boolean _isRADirectAllowed() {
		return (this._isRADirect() && (!this.inAppClientContainer));
	}

	public boolean _isAPIDirectAllowed() {
		return (this._isAPIDirect() && (!this.inAppClientContainer));
	}

	public void setDoInitOnlyOnStart(boolean start) {

		if (start) {
			// shouldn't happen
			throw new RuntimeException("setDoInitOnlyOnStart(true) is not supported any more");
		}
	}

	/**
	 * Check whether this resource adapter is configured to perform broker
	 * lifecycle management. This is the case by default, or can be configured
	 * by calling <tt>setManageBrokerLifecycle(true)</tt>. If
	 * <tt>setManageBrokerLifecycle(false)</tt> has been called then an
	 * <tt>IllegalStateException</tt> will be thrown.
	 */
	private void checkManaged() {
		if (!isManageBrokerLifecycle()) {
			throw new IllegalStateException(
					"com.sun.messaging.jms.ra.ResourceAdapter: cannot perform broker lifecycle operations if manageBrokerLifecycle=true");
		}

	}

	/**
	 * Return whether this resource adapter should manage the broker lifecycle.
	 * See the class comment for more information.
	 * 
	 * @return
	 */
	public boolean isManageBrokerLifecycle() {
		return manageBrokerLifecycle;
	}

	/**
	 * Set whether this resource adapter should manage the broker lifecycle. See
	 * the class comment for more information.
	 * 
	 * @param manageBrokerLifecycle
	 */
	public void setManageBrokerLifecycle(boolean manageBrokerLifecycle) {
		this.manageBrokerLifecycle = manageBrokerLifecycle;
	}



}
