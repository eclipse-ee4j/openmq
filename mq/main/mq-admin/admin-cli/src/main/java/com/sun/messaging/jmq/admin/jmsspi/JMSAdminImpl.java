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
 * @(#)JMSAdminImpl.java	1.39 07/11/07
 */ 

package com.sun.messaging.jmq.admin.jmsspi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.DestinationConfiguration;
import com.sun.messaging.QueueConnectionFactory;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.admin.apps.broker.BrokerCmdOptions;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.jmsclient.ConnectionImpl;
import com.sun.messaging.jmq.jmsclient.JMSXAWrappedConnectionFactoryImpl;
import com.sun.messaging.jmq.jmsspi.JMSAdmin;
import com.sun.messaging.jmq.jmsspi.PropertiesHolder;
import com.sun.messaging.jmq.util.BrokerExitCode;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.MessageType;


public class JMSAdminImpl implements JMSAdmin, ExceptionListener {

	private static AdminResources ar = Globals.getAdminResources();

    public static final long            DEFAULT_TIMEOUT = 5000;

    private String adminuserName = null;
    private String adminPassword = null;
    
    private long timeout = -1;

    private QueueConnectionFactory      qcf;
    private QueueConnection             connection;
    private QueueSession                session;
    private Queue                       requestQueue;
    private TemporaryQueue              replyQueue;
    private QueueSender                 sender;
    protected QueueReceiver             receiver;

    private AdministeredObject		tmpDestination = null;
    private AdministeredObject		tmpConnFactory = null;

    private boolean			secureTransportUsed = false;

    // When this class is used to start a broker, these properties are passed to the broker
	private PropertiesHolder brokerPropertiesHolder;
    
    // use this logger name as enabling Glassfish jms logging uses loggers of the form javax.resourceadapter.mqjmsra    												
    protected static transient final String loggerName = "javax.resourceadapter.mqjmsra.lifecycle";
    protected static transient final Logger logger = Logger.getLogger(loggerName);

    /*
     * This class should only be instantiated via JMSAdminFactoryImpl.
     * REVISIT: hard-coded error messages
     */
    protected JMSAdminImpl(Properties connectionProps, PropertiesHolder brokerPropertiesHolder, String username, String adminPassword)
			throws JMSException {

		this.timeout = DEFAULT_TIMEOUT;
		this.adminuserName = username;
		this.adminPassword = adminPassword;
		this.brokerPropertiesHolder = brokerPropertiesHolder;

		createFactory(connectionProps); // init the qcf !!

		String connType = connectionProps.getProperty(ConnectionConfiguration.imqConnectionType, "");

		if (connType.equals("TLS") || connType.equals("SSL")) {
			secureTransportUsed = true;
		}
	}

    /**
     * Return the SPI version
     */  
    public String getVersion()  {
	Version version = new Version();
	return (version.getJMSAdminSpiVersion());
    }

    /**
     * Create a ConnectionFactory administered object
     *
     * @param type        Either QUEUE or TOPIC.
     * @param properties  Connection specific properties.
     * @return New created ConnectionFactory administered object.
     * @exception JMSException thrown if connectionFactory could not be created.
     */
    public Object createConnectionFactoryObject(int type, 
						java.util.Map properties)
           throws JMSException {

	Object cf = null;
	Properties tmpProps = getProperties(properties);

	fillDefaultConnectionFactoryProperties(tmpProps);

	if (type == TOPIC) {
	    cf = JMSObjFactory.createTopicConnectionFactory(tmpProps);
	} else if (type == QUEUE) {
	    cf = JMSObjFactory.createQueueConnectionFactory(tmpProps);
	} else {
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_INVALID_DOMAIN_TYPE));
	}
	return cf;
    }

    /**
     * Create a XAConnectionFactory administered object
     *
     * @param type        Either QUEUE or TOPIC.
     * @param properties Connection specific properties.
     * @return New created JMSXAConnectionFactory administered object.
     *                     ^^^^^
     * @exception JMSException thrown if XAConnectionFactory could not be 
     * created.
     */
    public Object createXAConnectionFactoryObject(int type, 
						  java.util.Map properties)
           throws JMSException {

        Object xcf = null;
        Properties tmpProps = getProperties(properties);

		fillDefaultConnectionFactoryProperties(tmpProps);

        if (type == TOPIC) {
	    xcf = new 
	    com.sun.messaging.jmq.jmsclient.JMSXATopicConnectionFactoryImpl();
	    setProperties((AdministeredObject)xcf, tmpProps);

	} else if (type == QUEUE) {
	    xcf = new 
	    com.sun.messaging.jmq.jmsclient.JMSXAQueueConnectionFactoryImpl();
	    setProperties((AdministeredObject)xcf, tmpProps);

	} else {
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_INVALID_DOMAIN_TYPE));
        }
        return xcf;
    }


    /**
     * Create a Destination administered object
     *
     * @param destinationName The destination name.
     * @param type        Either QUEUE or TOPIC.
     * @param properties destination specific properties.
     * @return New created Destination administered object.
     * @exception JMSException thrown if destination object could not be 
     * created.
     */
    public Object createDestinationObject(String destinationName,
                                          int type,
                                          java.util.Map properties)
                                          throws JMSException {
        Map props = properties;
	if (props == null) props = new Properties();
	props.put(DestinationConfiguration.imqDestinationName, destinationName);
        return createDestinationObject(type, props);
    }

    /**
     * Create a Destination administered object. The destination name
     * is assumed in properties like imqDestinationName=xxxxx
     *
     * @param type        Either QUEUE or TOPIC.
     * @param properties destination specific properties.
     * @return New created Destination administered object.
     * @exception JMSException thrown if destination object could not be 
     * created.
     */
    public Object createDestinationObject(int type,
                                          java.util.Map properties)
                                          throws JMSException {
	if (properties == null || 
            properties.get(DestinationConfiguration.imqDestinationName) == null)
            throw new JMSException(ar.getKString(ar.X_JMSSPI_NO_DESTINATION_NAME));
	Object dest = null;

        Properties tmpProps = getProperties(properties);

        if (type == TOPIC) {
	    dest = JMSObjFactory.createTopic(tmpProps);

	} else if (type == QUEUE) {
	    dest = JMSObjFactory.createQueue(tmpProps);

	} else {
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_INVALID_DOMAIN_TYPE));
	}
        return dest;
    }

    /**
     * Wrap a standard JMS ConnectionFactory administered object 
     *
     * @param obj a XAQueue/TopicConnectionFactory or Queue/TopicConnectionFactory object   
     * @return a JMSXAConnectionFactory object
     *                     ^^^^^
     * @exception JMSException if fail to wrap
     */
    public Object wrapJMSConnectionFactoryObject(Object obj) throws JMSException {
        if (obj instanceof javax.jms.XAQueueConnectionFactory)
            return new JMSXAWrappedConnectionFactoryImpl((javax.jms.XAQueueConnectionFactory)obj);
        if (obj instanceof javax.jms.XATopicConnectionFactory)
            return new JMSXAWrappedConnectionFactoryImpl((javax.jms.XATopicConnectionFactory)obj);
        if (obj instanceof javax.jms.QueueConnectionFactory)
            return new JMSXAWrappedConnectionFactoryImpl((javax.jms.QueueConnectionFactory)obj);
        if (obj instanceof javax.jms.TopicConnectionFactory)
            return new JMSXAWrappedConnectionFactoryImpl((javax.jms.TopicConnectionFactory)obj);

        throw new JMSException(ar.getKString(ar.X_JMSSPI_INVALID_OBJECT_TYPE));
    }

    public void validateJMSSelector(String selector) throws JMSException {
        com.sun.messaging.jmq.jmsselector.JMSSelector jmsselector = 
                      new com.sun.messaging.jmq.jmsselector.JMSSelector();
        try {
            jmsselector.validateSelectorPattern(selector);
        }
        catch (com.sun.messaging.jmq.jmsselector.InvalidJMSSelectorException e) {
            throw new InvalidSelectorException(e.getMessage());            
        }
        catch (Exception e) {
            throw new JMSException(e.getMessage());            
        }
    }

    /**
     * @return the client-id property name
     */
    public String clientIDPropertyName() {
        return ConnectionConfiguration.imqConfiguredClientID;
    }

    /**
     * Returns a map of all the properties that a destination
     * object has. 
     * <P>
     * For each property name returned, a corresponding label that can 
     * be used for output display purposes is also returned.
     * The Map returned contains the property names and property
     * labels as key-value pairs. This information can be used to display 
     * more readable output containing property labels and not property
     * names.
     *
     * @param type Either QUEUE or TOPIC.
     * @return Map containing attribute value pairs of property names
     *		and their display labels.
     */
    public Map getAllDestinationObjectProperties(int type)
						throws JMSException  {
	Properties	ret;

	if (tmpDestination == null)  {
	    tmpDestination = (AdministeredObject)JMSObjFactory.createTopic(new Properties());
	}

	ret = new Properties();

	for (Enumeration e = tmpDestination.enumeratePropertyNames(); e.hasMoreElements();) {
	    String propName = (String)e.nextElement();
	    String propLabel = tmpDestination.getPropertyLabel(propName);
	    ret.setProperty(propName, propLabel);
	}

	return (ret);
    }

    /**
     * Returns a subset of all the properties that a destination 
     * object has. 
     * <P>
     * This collection of properties can be used to construct a user
     * interface where not all of the object's properties will be 
     * displayed, and only a selected few (more important) ones are.
     * <P>
     * For each property name returned, a corresponding label that can 
     * be used for output display purposes is also returned.
     * The Map returned contains the property names and property
     * labels as key-value pairs. This information can be used to display 
     * more readable output containing property labels and not property
     * names.
     *
     * @param type Either QUEUE or TOPIC.
     * @return Map containing attribute value pairs of property names
     *		and their display labels.
     */
    public Map getDisplayedDestinationObjectProperties(int type)
						throws JMSException  {
	Properties	ret;
	String		propName, propLabel;

	if (tmpDestination == null)  {
	    tmpDestination = (AdministeredObject)JMSObjFactory.createTopic(new Properties());
	}

	ret = new Properties();

	propName = DestinationConfiguration.imqDestinationName;
	propLabel = tmpDestination.getPropertyLabel(propName);
	ret.setProperty(propName, propLabel);

	return (ret);
    }

    /**
     * Returns a map of all the properties that a connection factory
     * object has. 
     * <P>
     * For each property name returned, a corresponding label that can 
     * be used for output display purposes is also returned.
     * The Map returned contains the property names and property
     * labels as key-value pairs. This information can be used to display 
     * more readable output containing property labels and not property
     * names.
     *
     * @param type Either QUEUE or TOPIC.
     * @return Map containing attribute value pairs of property names
     *		and their display labels.
     */
    public Map getAllConnectionFactoryObjectProperies(int type)
						throws JMSException  {
	Properties	ret;

	if (tmpConnFactory == null)  {
	    tmpConnFactory = 
	      (AdministeredObject)JMSObjFactory.createTopicConnectionFactory(new Properties());
	}

	ret = new Properties();

	for (Enumeration e = tmpConnFactory.enumeratePropertyNames(); e.hasMoreElements();) {
	    String propName = (String)e.nextElement();
	    String propLabel = tmpConnFactory.getPropertyLabel(propName);
	    ret.setProperty(propName, propLabel);
	}

	return (ret);
    }

    /**
     * Returns a subset of all the properties that a connection factory 
     * object has. 
     * <P>
     * This collection of properties can be used to construct a user
     * interface where not all of the object's properties will be 
     * displayed, and only a selected few (more important) ones are.
     * <P>
     * For each property name returned, a corresponding label that can 
     * be used for output display purposes is also returned.
     * The Map returned contains the property names and property
     * labels as key-value pairs. This information can be used to display 
     * more readable output containing property labels and not property
     * names.
     *
     * @param type Either QUEUE or TOPIC.
     * @return Map containing attribute value pairs of property names
     *		and their display labels.
     */
    public Map getDisplayedConnectionFactoryObjectProperies(int type)
						throws JMSException  {
	Properties	ret;
	String		propName, propLabel;

	if (tmpConnFactory == null)  {
	    tmpConnFactory = 
	      (AdministeredObject)JMSObjFactory.createTopicConnectionFactory(new Properties());
	}

	ret = new Properties();

	propName = ConnectionConfiguration.imqBrokerHostName;
	propLabel = tmpConnFactory.getPropertyLabel(propName);
	ret.setProperty(propName, propLabel);

	propName = ConnectionConfiguration.imqBrokerHostPort;
	propLabel = tmpConnFactory.getPropertyLabel(propName);
	ret.setProperty(propName, propLabel);

	propName = ConnectionConfiguration.imqConfiguredClientID;
	propLabel = tmpConnFactory.getPropertyLabel(propName);
	ret.setProperty(propName, propLabel);

	return (ret);
    }



    /**
     * Connect to the provider.
     * @exception JMSException thrown if connection to the provider
     * cannot be established or if an error occurs
     */
    public void connectToProvider() throws JMSException {
        connection = qcf.createQueueConnection(adminuserName, adminPassword);
        connection.setExceptionListener(this);
        connection.start();
        session = connection.createQueueSession
            (false, Session.CLIENT_ACKNOWLEDGE);
        requestQueue = session.createQueue(MessageType.JMQ_ADMIN_DEST);
        replyQueue = session.createTemporaryQueue();
        sender = session.createSender(requestQueue);
        sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        receiver = session.createReceiver(replyQueue);

	hello();
    }

    /**
     * Disconnect from the provider.
     * @exception JMSException thrown if connection to the provider cannot
     * be closed or ir an error occurs
     */
    public void disconnectFromProvider() throws JMSException {
        sender.close();
        receiver.close();
        session.close();

	try  {
            connection.close();
	} catch (JMSException jmse)  {
	    if (!secureTransportUsed)  {
		throw (jmse);
	    }
	}
    }

    /**
     * Create a physical Destination within the JMS Provider using the provided
     * properties to define provider specific attributes.
     * Destination is not automatically bound into JNDI namespace.
     *
     * @param destinationName
     * @param destinationType QUEUE or TOPIC
     * @param properties creation properties.
     * @return Identifier for newly created Destination.
     * @exception JMSException thrown if Queue could not be created.
     */
    public void createProviderDestination(String destinationName,
                                          int destinationType,
                                          java.util.Map properties)
        throws JMSException {

        ObjectMessage requestMesg = null;
        Message replyMesg = null;

        // Create DestinationInfo.
        DestinationInfo di = new DestinationInfo();
        di.setName(destinationName);

	// REVISIT:
	// We only check for queueDeliveryPolicy in the properties
	// hard-coded string; left here because we are not sure
	// if we are going to expose this attribute yet
	String qPolicy = null;

	/*
	 * Early initialization to be used in error messages
	 */
	int typeMask = this.getDestTypeMask(destinationType, null);

	if (properties != null)  {
	    if (properties.containsKey("queueDeliveryPolicy")) {
	        qPolicy = (String)properties.get("queueDeliveryPolicy");
	        typeMask = this.getDestTypeMask(destinationType, qPolicy);
	    }

	    if (properties.containsKey(
		BrokerCmdOptions.PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT)) {
		Object tmp = null;
		String val = null;
		tmp = properties.get(
		    BrokerCmdOptions.PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT);

		if (tmp != null)  {
		    if (!(tmp instanceof String))  {
		        JMSException jmse;
		        jmse = new JMSException(
		            ar.getString(ar.E_SPI_DEST_CREATION_FAILED, 
				DestType.toString(typeMask),
		                destinationName)
			    + "\n"
			    + ar.getString(ar.E_SPI_ATTR_TYPE_NOT_STRING, 
		                BrokerCmdOptions.PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT)
			    );

		        throw jmse;
		    }

		    val = (String)tmp;

	    	    try  {
		        di.setMaxActiveConsumers(Integer.parseInt(val));
		    } catch (Exception e)  {
		        JMSException jmse;
		        jmse = new JMSException(
		            ar.getString(ar.E_SPI_DEST_CREATION_FAILED, 
				DestType.toString(typeMask),
		                destinationName)
			    + "\n"
			    + ar.getString(ar.E_INVALID_INTEGER_VALUE, 
		                val, 
		                BrokerCmdOptions.PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT));
                        throw (jmse);
                    }
		}
	    }
	}

	di.setType(typeMask);

        requestMesg = session.createObjectMessage();
        requestMesg.setJMSReplyTo(replyQueue);
        requestMesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.CREATE_DESTINATION);
        requestMesg.setObject(di);
        sender.send(requestMesg);

        replyMesg = receiver.receive(timeout);
        replyMesg.acknowledge();

        checkReplyTypeStatus(replyMesg,
                             MessageType.CREATE_DESTINATION_REPLY,
                             "CREATE_DESTINATION_REPLY");
    }

    /**
     * Delete a physical destination within the JMS Provider.
     * @param type        Either QUEUE or TOPIC.
     * @param destinationName
     * @exception JMSException thrown if Queue could not be deleted.
     */
    public void deleteProviderDestination(String destinationName,
                                          int type)
        throws JMSException {
        ObjectMessage requestMesg = null;
        Message replyMesg = null;

        requestMesg = session.createObjectMessage();
        requestMesg.setJMSReplyTo(replyQueue);
        requestMesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.DESTROY_DESTINATION);
        requestMesg.setStringProperty(MessageType.JMQ_DESTINATION, 
				      destinationName);
        requestMesg.setIntProperty(MessageType.JMQ_DEST_TYPE, 
				   this.getDestTypeMask(type, null));
        sender.send(requestMesg);

        replyMesg = receiver.receive(timeout);
        replyMesg.acknowledge();

        checkReplyTypeStatus(replyMesg,
            MessageType.DESTROY_DESTINATION_REPLY,
            "DESTROY_DESTINATION_REPLY");
    }

    /**
     * Get all provider destinations.
     * 
     * @return A multi dimensional array containing information
     *         about the JMS destinations. array[0] is a String[]
     *         listing the destination names. array[1] is a
     *         String[] listing the destination types.
     * @exception JMSException thrown if array could not be obtained.
     */
    public String[][] getProviderDestinations() throws JMSException {
        ObjectMessage mesg = null;

        mesg = session.createObjectMessage();
        mesg.setJMSReplyTo(replyQueue);
        mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_DESTINATIONS);
        sender.send(mesg);

        mesg = (ObjectMessage)receiver.receive(timeout);
        mesg.acknowledge();
        checkReplyTypeStatus(mesg, MessageType.GET_DESTINATIONS_REPLY,
            "GET_DESTINATIONS_REPLY");

	Object obj;
        if ((obj = mesg.getObject()) != null) {
            if (obj instanceof Vector)  {
        	Vector dests = (Vector)obj;

		/*
		 * Remove temporary, internal and admin destinations
		 * by creating a new Vector and adding all the valid 
		 * destinations into this new Vector.
		 */
		Vector validDests = new Vector();
	
		for (int i = 0; i < dests.size(); i++) {
		    DestinationInfo di = (DestinationInfo)dests.elementAt(i);
		    if ((!MessageType.JMQ_ADMIN_DEST.equals(di.name)) && 
            (!MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(di.name)) &&
			(!DestType.isInternal(di.fulltype)) &&
			(!DestType.isTemporary(di.type))) {
			validDests.add(di);
		    }
		}

	        int numElements = validDests.size();
	        String[][] destinations = new String[2][numElements];

	        for (int i = 0; i < numElements; i++) {
	    	    DestinationInfo di = (DestinationInfo)validDests.get(i);
		    destinations[0][i] = di.name;
		    destinations[1][i] = this.getDestinationType(di.type);
	        }
	        return destinations;
            }
        }
        return null;
    }

    /**
     * Start the provider.
     *
     * @param iMQHome Location of the broker executable.
     * @param optArgs Array of optional broker command line arguments.
     * @param serverName Instance name of the server.
     * @exception IOException thrown if the server startup fails.
     */
    public void startProvider(String iMQHome, String optArgs[],
        String serverName) throws IOException, JMSException {

        String[] cmdLine = makeStartProviderCmdLine(iMQHome, optArgs, serverName, false);

        // Log the command line we are about to execute
        if (logger.isLoggable(Level.FINE)){
            String commandLineToLog = "";
            for (int i = 0; i < cmdLine.length; i++) {
                 commandLineToLog = commandLineToLog + cmdLine[i] + " ";
            }
            logger.fine("MQJMSRA: Starting LOCAL broker with command line: "+commandLineToLog);
        }

        launchAndWatch(cmdLine);
    }

    /**
     * @param iMQHome Location of the broker executable, ignored if argsOnly true
     */
    public String[] makeStartProviderCmdLine(String iMQHome, String optArgs[],
        String serverName, boolean argsOnly) throws IOException, JMSException {

        Vector v = new Vector();

        String append = null;
        if (!argsOnly) {
            String iMQBrokerPath;
        
            if (java.io.File.separator.equals("\\")) {
                // Windows path
                //    <iMQHome>\\imqbrokersvc.exe -console
                iMQBrokerPath  = iMQHome +
                java.io.File.separator + "imqbrokersvc.exe";
                append = "-console";
            } else {
                // Unix path.
                //    <iMQHome>/bin/imqbrokerd
                iMQBrokerPath  = iMQHome +
                    java.io.File.separator + "imqbrokerd";
            }
            v.add(iMQBrokerPath);
        }

        String portStr =
            qcf.getProperty(ConnectionConfiguration.imqBrokerHostPort);

        // Important : Do not change the command line parameter order
        // unless you know what you are doing. Here are the
        // constraints -
        //
        // 1. The "-javahome" argument, if present, must be first.
        //    Otherwise it does not work on Windows. The caller (of
        //    this method) is responsible for making sure that the
        //    "-javahome" argument is first in the 'optArgs' string.
        //
        // 2. The -name, -port and -silent arguments must be at
        //    placed at the end, so that they override anything
        //    specified in the optArgs...
        //
        // 3. The -bgnd argument is required for proper signal
        //    handling on solaris.
        //

        if (optArgs != null) {
            for (int i = 0; i < optArgs.length; i++)
                v.add(optArgs[i]);
        }

        if (serverName != null) {
            v.add("-name");
            v.add(serverName);
        }

        if (append != null)
            v.add(append);

        v.add("-port");
        v.add(portStr);
        // CR 6944162 Disable use of -bgnd (used on Solaris) 
        // because it prevents passwords being passed to the broker via standard input
        //v.add("-bgnd");
        
        // disable use of -errorwarn if FINE logging is enabled
        if (!logger.isLoggable(Level.FINE)){
        	// normally we only want to see errors in stdout
        	v.add("-ttyerrors");
        }
        if (!argsOnly) {
            v.add("-managed");
            v.add("-read-stdin");
        }

        return (String[])v.toArray(new String[0]);
    }
        
	private void launchAndWatch(String[] cmdLine) throws IOException {
        Process p = Runtime.getRuntime().exec(cmdLine);

        BrokerWatcher brokerWatcher = new BrokerWatcher(p,cmdLine);
        brokerWatcher.setDaemon(true);
        brokerWatcher.start();
        
        
        // start a thread to log any error messages from the new broker as a WARN message
        boolean isError=true;
        StreamGobbler errorGobbler = new 
            StreamGobbler(p.getErrorStream(), isError,"Local broker: ");          
        errorGobbler.setDaemon(true);
        errorGobbler.start();
        
        // start a thread to log any output messages from the new broker as an INFO message
        isError=false;
        StreamGobbler outputGobbler = new 
            StreamGobbler(p.getInputStream(), isError,"Local broker: ");
        outputGobbler.setDaemon(true);
        outputGobbler.start();
        
		// generate the broker properties afresh
		Properties brokerProps = brokerPropertiesHolder.getProperties();
        
        // write properties to the input stream of the new process  
		try {
			OutputStreamWriter osw = new OutputStreamWriter(p.getOutputStream());
			BufferedWriter bw = new BufferedWriter(osw);
			for (Enumeration e = brokerProps.propertyNames(); e.hasMoreElements();) {
				String thisPropertyName = (String) e.nextElement();
				String thisPropertyValue = brokerProps.getProperty(thisPropertyName);
				bw.write(thisPropertyName + "=" + thisPropertyValue);
				bw.newLine();
			}
			if (this.adminPassword!=null){
				// if set, override any admin password set via brokerProps
				bw.write("imq.imqcmd.password="+this.adminPassword);
				bw.newLine();
			}
			bw.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
    }

    /**
     * Ping the provider.
     * @exception JMSException thrown if ping fails
     */
	public void pingProvider() throws JMSException {

		// temporarily turn off logging because we do not want to log a message if the provider is not running
		// as this might not be an error.
		Logger rootLogger = Logger.getLogger(ConnectionImpl.ROOT_LOGGER_NAME);
		Level savedLevel = rootLogger.getLevel();
		rootLogger.setLevel(Level.SEVERE);

		Connection pingConn = null;
		try {
			pingConn = qcf.createQueueConnection(adminuserName, adminPassword);
		} finally {
			// re-enable logging logging
			rootLogger.setLevel(savedLevel);
		}

		try {
			pingConn.setExceptionListener(this);
			pingConn.start();
		} finally {
			try {
				pingConn.close();
			} catch (JMSException jmse) {
				if (!secureTransportUsed) {
					throw (jmse);
				}
			}
                }
	}

    /**
     * Get the property value from a provider
     * @exception JMSException thrown if the get fails.
     */
    private String getProviderPropertyValue(String propName) throws JMSException {
        ObjectMessage requestMesg = null;
        ObjectMessage replyMesg = null;
	String	propVal = null;

        requestMesg = session.createObjectMessage();
        requestMesg.setJMSReplyTo(replyQueue);
	requestMesg.setIntProperty
	    (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_BROKER_PROPS);

	sender.send(requestMesg);

	replyMesg = (ObjectMessage)receiver.receive(timeout);
        replyMesg.acknowledge();

        checkReplyTypeStatus(replyMesg,
                             MessageType.GET_BROKER_PROPS_REPLY,
                             "GET_BROKER_PROPS_REPLY");

        Object obj;
		     
	if ((obj = replyMesg.getObject()) != null) {
	    if (obj instanceof Properties)  {
		Properties	props = (Properties)obj;
		propVal = props.getProperty(propName);
	    }
	}

	return (propVal);
    }

    /**
     * Get the instance name of the provider
     * @exception JMSException thrown if the get fails.
     */
    public String getProviderInstanceName() throws JMSException {
        return(getProviderPropertyValue(
		BrokerConstants.PROP_NAME_BKR_INSTANCE_NAME));
    }

    /**
     * Get the VARHOME directory of the provider
     * @exception JMSException thrown if the get fails.
     */
    public String getProviderVarHome() throws JMSException {
        return(getProviderPropertyValue(
		BrokerConstants.PROP_NAME_BKR_VARHOME));
    }


    /**
     * Shutdown the provider.
     * @exception JMSException thrown if the shutdown fails
     * @throws  
     */
    public void shutdownProvider() throws JMSException {        
        try {
        	tryToShutdownProvider();
		} catch (JMSException jmse) {
			if ((com.sun.messaging.jmq.jmsclient.resources.ClientResources.X_CONSUMER_CLOSED.equals(jmse.getErrorCode())) ||
                (com.sun.messaging.jmq.jmsclient.resources.ClientResources.X_SESSION_CLOSED.equals(jmse.getErrorCode()))) {
				// we seem to have lost the connection - try once to reconnect before giving up
			    try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			    connectToProvider();
			    tryToShutdownProvider();
			} else {
				// re-throw the exception if not X_CONSUMER_CLOSED or X_SESSION_CLOSED 
				throw jmse;
			}		
		}
    }
    
	private void tryToShutdownProvider() throws JMSException {
		ObjectMessage requestMesg;
		Message replyMesg;
		requestMesg = session.createObjectMessage();
		requestMesg.setJMSReplyTo(replyQueue);
		requestMesg.setIntProperty(MessageType.JMQ_MESSAGE_TYPE,MessageType.SHUTDOWN);
		sender.send(requestMesg);
		
		// disable reconnect since we don't want the client to try to reconnect
		//ConnectionImpl ci = (ConnectionImpl)connection;
		//ci.setImqReconnect(false);
		
		replyMesg = receiver.receive(timeout);
		checkReplyTypeStatus(replyMesg, MessageType.SHUTDOWN_REPLY,"SHUTDOWN_REPLY");
	}

    /**
	 * Restart the provider.
	 * 
	 * @exception JMSException
	 *                thrown if the restart fails
	 */
    public void restartProvider() throws JMSException {
        ObjectMessage requestMesg = null;
        Message replyMesg = null;

        requestMesg = session.createObjectMessage();
        requestMesg.setJMSReplyTo(replyQueue);
        requestMesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.SHUTDOWN);
        requestMesg.setBooleanProperty(MessageType.JMQ_RESTART, true);
        sender.send(requestMesg);

        replyMesg = receiver.receive(timeout);
        checkReplyTypeStatus(replyMesg, MessageType.SHUTDOWN_REPLY,
             "SHUTDOWN_REPLY");
    }

    /**
     * Return the provider host name
     * @exception JMSException
     */
    public String getProviderHostName() throws JMSException {
        return qcf.getProperty(ConnectionConfiguration.imqBrokerHostName);
    }

    /**
     * Return the provider host port number
     * @exception JMSException
     */
    public String getProviderHostPort() throws JMSException {
        return qcf.getProperty(ConnectionConfiguration.imqBrokerHostPort);
    }

    /**
     * Delete provider instance.
     *
     * @param mqBinDir Location of MQ's bin directory, which contains
     * the broker executable.
     * @param optArgs Optional broker command line arguments.
     * @param serverName instance name of MQ broker to delete
     * @exception JMSException thrown if the delete fails.
     */
    public void deleteProviderInstance(String mqBinDir, 
				String optArgs,
				String serverName) throws IOException, JMSException  {
        String iMQBrokerPath;
	int exitCode = 0;
	boolean interrupted = false;

        if (java.io.File.separator.equals("\\")) {
            // Windows path
            //    <mqBinDir>\\bin\\imqbrokerd.exe
            iMQBrokerPath  = mqBinDir +
                java.io.File.separator + "imqbrokerd.exe";
        }
        else {
            // Unix path.
            //    <mqBinDir>/bin/imqbrokerd
            iMQBrokerPath  = mqBinDir +
                java.io.File.separator + "imqbrokerd";
        }

        //String portStr =
        //    qcf.getProperty(ConnectionConfiguration.imqBrokerHostPort);

        // Important : Do not change the command line parameter order
        // unless you know what you are doing. Here are the
        // constraints -
        //
        // 1. The "-javahome" argument, if present, must be first.
        //    Otherwise it does not work on Windoze. The caller (of
        //    this method) is responsible for making sure that the
        //    "-javahome" argument is first in the 'optArgs' string.
        //
        // 2. The -name, -port and -silent arguments must be at
        //    placed at the end, so that they override anything
        //    specified in the optArgs...
        //
        // 3. The -bgnd argument is required for proper signal
        //    handling on solaris.
        //

        String cmdLine = iMQBrokerPath;
        if (optArgs != null)
            cmdLine = cmdLine + " " + optArgs;
        if (serverName != null)
            cmdLine = cmdLine + " -name " + serverName;
        cmdLine = cmdLine + " -remove instance -silent -force";

        Process p = Runtime.getRuntime().exec(cmdLine);

        // Close the receiver end of stdout and stderr streams.
        // Otherwise the child process blocks while trying to
        // write...
        p.getInputStream().close();
        p.getErrorStream().close();

	try  {
	    p.waitFor();
	    exitCode = p.exitValue();
	} catch (InterruptedException iex)  {
	    interrupted = true;
	}

	if (interrupted)  {
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_INT_PREM,
				serverName));
	}
	
	/*
	 * Proper handling of exit codes will be done once the following
	 * bug is fixed:
	 * 4713518 - imqbrokerd -remove: Enhancements needed for app server 
	 *		integration
	 */
	switch (exitCode)  {
	case BrokerExitCode.NORMAL:
	break;

	case BrokerExitCode.INSTANCE_NOT_EXISTS:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_NOT_EXIST,
				serverName));

	case BrokerExitCode.INSTANCE_BEING_USED:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_BEING_USED,
				serverName));

	case BrokerExitCode.NO_PERMISSION_ON_INSTANCE:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_NO_PERM,
				serverName));

	case BrokerExitCode.PROBLEM_REMOVING_PERSISTENT_STORE:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_PROB_RM_STORE, serverName));

	case BrokerExitCode.IOEXCEPTION:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_IOEXCEPTION, serverName));

	default:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_UNKNOWN, serverName));
	}
    }

    /**
     * Delete provider instance.
     *
     * @param mqBinDir Location of MQ's bin directory, which contains
     * the broker executable.
     * @param optArgs Array of optional broker command line arguments.
     * @param serverName instance name of MQ broker to delete
     * @exception JMSException thrown if the delete fails.
     */
    public void deleteProviderInstance(String mqBinDir, 
				String optArgs[],
				String serverName) throws IOException, JMSException  {
        String iMQBrokerPath;
	int exitCode = 0;
	boolean interrupted = false;

        if (java.io.File.separator.equals("\\")) {
            // Windows path
            //    <mqBinDir>\\bin\\imqbrokerd.exe
            iMQBrokerPath  = mqBinDir +
                java.io.File.separator + "imqbrokerd.exe";
        }
        else {
            // Unix path.
            //    <mqBinDir>/bin/imqbrokerd
            iMQBrokerPath  = mqBinDir +
                java.io.File.separator + "imqbrokerd";
        }

        //String portStr =
        //   qcf.getProperty(ConnectionConfiguration.imqBrokerHostPort);

        // Important : Do not change the command line parameter order
        // unless you know what you are doing. Here are the
        // constraints -
        //
        // 1. The "-javahome" argument, if present, must be first.
        //    Otherwise it does not work on Windoze. The caller (of
        //    this method) is responsible for making sure that the
        //    "-javahome" argument is first in the 'optArgs' string.
        //
        // 2. The -name, -port and -silent arguments must be at
        //    placed at the end, so that they override anything
        //    specified in the optArgs...
        //
        // 3. The -bgnd argument is required for proper signal
        //    handling on solaris.
        //

        Vector v = new Vector();
        v.add(iMQBrokerPath);

        if (optArgs != null) {
            for (int i = 0; i < optArgs.length; i++)
                v.add(optArgs[i]);
        }

        if (serverName != null) {
            v.add("-name");
            v.add(serverName);
        }

        v.add("-remove");
        v.add("instance");
        v.add("-silent");
        v.add("-force");

        String[] cmdLine = (String[]) v.toArray(new String[0]);
        Process p = Runtime.getRuntime().exec(cmdLine);

        // Close the receiver end of stdout and stderr streams.
        // Otherwise the child process blocks while trying to
        // write...
        p.getInputStream().close();
        p.getErrorStream().close();

	try  {
	    p.waitFor();
	    exitCode = p.exitValue();
	} catch (InterruptedException iex)  {
	    interrupted = true;
	}

	if (interrupted)  {
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_INT_PREM,
				serverName));
	}
	
	/*
	 * Proper handling of exit codes will be done once the following
	 * bug is fixed:
	 * 4713518 - imqbrokerd -remove: Enhancements needed for app server 
	 *		integration
	 */
	switch (exitCode)  {
	case BrokerExitCode.NORMAL:
	break;

	case BrokerExitCode.INSTANCE_NOT_EXISTS:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_NOT_EXIST,
				serverName));

	case BrokerExitCode.INSTANCE_BEING_USED:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_BEING_USED,
				serverName));

	case BrokerExitCode.NO_PERMISSION_ON_INSTANCE:
	    throw new javax.jms.JMSException(ar.getKString(ar.X_JMSSPI_DELETE_INST_NO_PERM,
				serverName));

	case BrokerExitCode.PROBLEM_REMOVING_PERSISTENT_STORE:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_PROB_RM_STORE, serverName));

	case BrokerExitCode.IOEXCEPTION:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_IOEXCEPTION, serverName));

	default:
	    throw new javax.jms.JMSException(
		ar.getKString(ar.X_JMSSPI_DELETE_INST_UNKNOWN, serverName));
	}

    }

    private void hello() throws JMSException {
        ObjectMessage requestMesg = null;
        Message replyMesg = null;

        requestMesg = session.createObjectMessage();
        requestMesg.setJMSReplyTo(replyQueue);
        requestMesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.HELLO);
        sender.send(requestMesg);

        replyMesg = receiver.receive(timeout);
        replyMesg.acknowledge();
        checkReplyTypeStatus
            (replyMesg, MessageType.HELLO_REPLY, "HELLO_REPLY");
    }

    /*
     * Map is an interface and its implementation can be anything that
     * implements the Map interface.  Whatever it is, convert it to a 
     * Properties object.
     */
    private Properties getProperties(java.util.Map properties) {

        Properties tmpProps = null;

        if (properties == null)
            tmpProps = new Properties();

        else if (properties instanceof Properties) {
            tmpProps = (Properties)properties;

        } else {
            tmpProps = new Properties();
            Iterator iterator = properties.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry)iterator.next();
                tmpProps.put(entry.getKey(), entry.getValue());
            }
        }
        return tmpProps;
    }

    /*
     * Set the properties on this object.
     */
    private static void setProperties(AdministeredObject obj,
                                Properties objProps)
                                throws JMSException {
        /*
         * Set the specified properties on the new object.
         */
        for (Enumeration e = objProps.propertyNames(); e.hasMoreElements();) {

            String propName = (String)e.nextElement();
            String value  = objProps.getProperty(propName);
            if (value != null)
                obj.setProperty(propName, value);
        }
    }

    private void createFactory(Properties connectionProps)
        throws JMSException {

        qcf = new QueueConnectionFactory();
        qcf.setConnectionType(ClientConstants.CONNECTIONTYPE_ADMIN);

        for (Enumeration e = connectionProps.propertyNames();
                         e.hasMoreElements();) {
            String propName = (String)e.nextElement();
            String value  = connectionProps.getProperty(propName);

            if (value != null)  {
                qcf.setProperty(propName, value);
            }
        }
    }

	private void fillDefaultConnectionFactoryProperties(Properties props)
		throws JMSException {

		String val;
		if (props.getProperty(ConnectionConfiguration.imqBrokerHostName) == null &&
			(val=qcf.getProperty(ConnectionConfiguration.imqBrokerHostName)) != null) {
			props.setProperty(ConnectionConfiguration.imqBrokerHostName, val);			
		}
		if (props.getProperty(ConnectionConfiguration.imqBrokerHostPort) == null &&
			(val=qcf.getProperty(ConnectionConfiguration.imqBrokerHostPort)) != null) {
			props.setProperty(ConnectionConfiguration.imqBrokerHostPort, val);			
		}
	}

    // REVISIT: 
    // hard-coded string
    private static int getDestTypeMask(int type, String policy) {
        int mask = -1;

	if (type == TOPIC)
	    mask = DestType.DEST_TYPE_TOPIC;
	else if (type == QUEUE)
	    mask = DestType.DEST_TYPE_QUEUE;

	if (policy == null)
	    return mask;

	if (type == QUEUE) {
            if (policy.equals("s")) {
                mask |= DestType.DEST_FLAVOR_SINGLE;
            } else if (policy.equals("f")) {
                mask |= DestType.DEST_FLAVOR_FAILOVER;
            } else if (policy.equals("r")) {
                mask |= DestType.DEST_FLAVOR_RROBIN;
	    }
	}
	return mask;
    }

    private static String getDestinationType(int mask) {
        if (DestType.isTopic(mask))
            return String.valueOf(TOPIC);
        else if (DestType.isQueue(mask))
            return String.valueOf(QUEUE);
        else
            return String.valueOf(UNKNOWN);
    }

    private void checkReplyTypeStatus(Message mesg, 
			              int msgType, 
			              String msgTypeString) 
	throws JMSException {

        int actualMsgType = -1,
            actualReplyStatus = -1;

        /* There is a timing problem in the protocol.  
           The GOODBYE message could be processed before the SHUTDOWN_REPLY
           message and therefore could be sending null as a value for 'mesg'
           when receive() returns.  We will assume that the SHUTDOWN operation
           was successful when we receive status == 200 or mesg == null.
         */
        if (mesg == null)  {
            if (msgType == MessageType.SHUTDOWN_REPLY) {
                return;
            }
        }

        actualMsgType = mesg.getIntProperty(MessageType.JMQ_MESSAGE_TYPE);
        actualReplyStatus = mesg.getIntProperty(MessageType.JMQ_STATUS);

        if ((msgType == actualMsgType) &&
            (actualReplyStatus == MessageType.OK))
            return;

        /*
         * Otherwise, report an error
         */
	String error = null;
	try {
            error = mesg.getStringProperty(MessageType.JMQ_ERROR_STRING);
	} catch (Exception e) {
	}	
	throw new JMSException(error);
    }

    /*
     * BEGIN INTERFACE ExceptionListener
     */
    public void onException(JMSException jmse) {
    }
    /*
     * END INTERFACE ExceptionListener
     */
    
    class BrokerWatcher extends Thread {
    	Process p;
    	String[] cmdLine;

		BrokerWatcher(Process p, String[] cmdLine) {
			this.p=p;
			this.cmdLine=cmdLine;
		}

		public void run() {
			logger.fine("BrokerWatcher started");
			try {
				int status = p.waitFor();
				logger.fine("BrokerWatcher: Broker returned with status="+status);
				if (status==255){
					// restart broker
					launchAndWatch(cmdLine);
					logger.fine("BrokerWatcher terminating after restarting broker");
				}
				logger.fine("BrokerWatcher terminating after normal exit");
			} catch (InterruptedException e) {
				logger.warning("InterruptedException watching broker: "+e.getMessage());
			} catch (IOException e) {
				logger.warning("Error restarting broker: "+e.getMessage());
			}

		}
    }
    
    static class StreamGobbler extends Thread {
		InputStream is;
		boolean isError;
		String prefix;

		/**
		 * 
		 * @param is
		 * @param isError If true, log messages as a WARN message. Otherwise log messages as an INFO message
		 * @param prefix
		 */
		StreamGobbler(InputStream is, boolean isError, String prefix) {
			this.is = is;
			this.isError = isError;
			this.prefix = prefix;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				String message = prefix;
				int noOfLines=0;
				while ((line = br.readLine()) != null) {
					noOfLines++;
					if (noOfLines>1) {
						message = message + "\n";
					}
					message = message  + line;
				}

				if (noOfLines>0) {
					if (isError) {
						logger.warning(message);
					} else {
						logger.info(message);
					}
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
    }

}
