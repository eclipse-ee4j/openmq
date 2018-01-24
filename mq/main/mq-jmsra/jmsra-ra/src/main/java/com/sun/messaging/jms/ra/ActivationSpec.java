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

import java.util.Hashtable;
import java.util.Enumeration;
import javax.resource.*;
import javax.resource.spi.*;

import java.util.logging.Logger;

import com.sun.messaging.jmq.DestinationName;
import com.sun.messaging.jms.ra.util.CustomTokenizer;

/**
 *  Encapsulates the configuration of a MessageEndpoint.
 *  An Application Server configures an instance of this class and uses it
 *  to activate message inflow to a message listener in the application server.
 */

public class ActivationSpec
implements javax.resource.spi.ActivationSpec,
           javax.resource.spi.ResourceAdapterAssociation,
           java.io.Serializable,
           GenericConnectionFactoryProperties
{

    /** String constants used to map standard values to JMS equivalents */
    private static final String AUTOACKNOWLEDGE = "Auto-acknowledge";
    private static final String DUPSOKACKNOWLEDGE = "Dups-ok-acknowledge";
    private static final String NOACKNOWLEDGE = "No-acknowledge";
    private static final String DURABLE = "Durable";
    private static final String NONDURABLE = "NonDurable";
    private static final String INSTANCE = "Instance";
    private static final String CLUSTER = "Cluster";
    private static final String QUEUE = "javax.jms.Queue";
    private static final String TOPIC = "javax.jms.Topic";

    /* Loggers */
    private static final String _className = "com.sun.messaging.jms.ra.ActivationSpec";
    protected static final String _lgrNameInboundMessage = "javax.resourceadapter.mqjmsra.inbound.message";
    protected static final Logger _loggerIM = Logger.getLogger(_lgrNameInboundMessage);
    protected static final String _lgrMIDPrefix = "MQJMSRA_AS";
    protected static final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** The resource adapter instance that this instance is bound to */
    private com.sun.messaging.jms.ra.ResourceAdapter ra = null;

    /** The instance carries on properties of connection factory */
    private com.sun.messaging.jms.ra.ManagedConnectionFactory mcf;

    /* ActivationSpec attributes recommended for JMS RAs */
    /** The type of the destination for the MessageEndpoint consumer */
    private String destinationType = null;

    /** The name of the destination for the MessageEndpoint consumer */
    private String destination = null;

    /** The message selector for the MessageEndpoint consumer */
    private String messageSelector = null;

    /** The acknowledgement mode for the MessageEndpoitn consumer */
    private String acknowledgeMode = AUTOACKNOWLEDGE;

    /** The subscription durability of the MessageEndpoint consumer */
    private String subscriptionDurability = NONDURABLE;

    /** The clientId of the MessageEndpoint consumer */
    private String clientId = null;

    /** The subscription name of the MessageEndpoint consumer */
    private String subscriptionName = null;

    /** The subscription scope of the MessageEndpoint consumer */
    private String subscriptionScope = null;

    /* ActivationSpec attributes for the GlassFish(tm) MQ JMS Resource Adapter */

    /** The ConnectionFactory JNDI Name to be used */
    private String connectionFactoryJNDIName;

    /** The Maximum endpoint pool size that will be used */
    private int endpointPoolMaxSize = 15;

    /** The Steady endpoint pool size that will be used */
    private int endpointPoolSteadySize = 10;

    /** The endpoint pool re-size count that will be used */
    private int endpointPoolResizeCount = 1;

    /** The endpoint pool re-size timeout in seconds that will be used */
    private int endpointPoolResizeTimeout = 5;

    /** The Maximum # of endpoint delivery attempts for endpoints that throw an Exception */
    private int endpointExceptionRedeliveryAttempts = 6;

    /** The interval for endpoint delivery attempts for endpoints that throw an Exception */
    private int endpointExceptionRedeliveryInterval = 500;

    /** The flag indicating whether to send undeliverable messages to the DMQ */
    private boolean sendUndeliverableMsgsToDMQ = true;

    /** Indicates whether the endpoint deployment requires a shared client Identifier */
    //private boolean enableSharedClientID = false;

    /** The Message Service Address List to use to connect to GlassFish(tm) MQ */
    private String addressList = null;
    private boolean addressListSet = false;
    
    private String addressListBehavior=null;

    /** The custom acknowledgement mode for this MessageEndpoint consumer */
    private String customAcknowledgeMode = null;


    /** The username that will be used for this endpoint - default is to use the one from the RA */
    private String userName = null;

    /** The password that will be used for this endpoint - default is to use the one from the RA */
    private String password = null;
    
    private int addressListIterations;
    private boolean addressListIterationsSet=false;
    private int reconnectAttempts;
    private boolean reconnectAttemptsSet=false;
    private int reconnectInterval;
    private boolean reconnectIntervalSet=false;

    private String destinationLookup;
    private String connectionFactoryLookup;

    /** ContextClassLoader for the onMessage Thread */
    private transient ClassLoader contextClassLoader = null;
        
    /**
     * The name of the MDB (or a name which represents it). 
     * This is used when sharing non-durable topic subscriptions in a clustered container to
     * set the clientID. This then allows the subscription to be shared with the MDB of the same name
     * in the other container instances. It must be unique within an individual container instance.
     * Only used in a clustered container when shared subscriptions are enabled.
     * Only used for non-durable topic subscriptions.
     * Only used if clientID is not explicitly set, and is mandatory if this is the case.
     */
    private String mdbName = null;
   
    /**
     * If this is a clustered container, this is a name which identifies the cluster, and is always optional.
     * When sharing non-durable topic subscriptions in a clustered container this is appended to the clientID.
     * Only used in a clustered container when shared subscriptions are enabled.
     * Only used for non-durable topic subscriptions.
     * Only used if clientID is not explicitly set, and is always optional.
     * 
     * (For info) In GlassFish 3.1 this will be set to <domainName>#<clusterName> 
     */
    private String groupName = null;

    /** The Resource Adapter UID to use to ensure that ClientIDs are unique within an RA */
    private String raUID = null;

    /** The flag indicating whether this is being activated in a Clustered Container */
    private boolean inClusteredContainer = false;
    
    /**
     * The property useSharedSubscriptionInClusteredContainer is only used 
     * if this is a clustered container (as determined by inClusteredContainer), 
     * If the property useSharedSubscriptionInClusteredContainer is set  
     * then all subscriptions using this activation will be shared
     * otherwise subscriptions will not be shared
     */
    private boolean useSharedSubscriptionInClusteredContainer = true;

    /** Internal flag indicating whether this is being activated using serial msg delivery */
    /** Default (false) implies concurrent msg delivery */
    private boolean deliverySerial = false;
    
    /** additional connection factory properties, as a comma-separated list of name=value pairs */
    private String options;

    /** ActivationSpec must provide a default constructor */
    public ActivationSpec ()
    {
        _loggerIM.entering(_className, "constructor()");
    }


    // ActivationSpec interface defined methods //

    /** Validates the configuration of this ActivationSpec instance
     *  ActivationSpec instance.
     *
     *  @throws InvalidPropertyException If this activation spec
     *          instance has any invalid property
     */
    public void
    validate()
    throws InvalidPropertyException
    {
        InvalidPropertyException ipe;
        _loggerIM.entering(_className, "validate()", this);
        //Require destinationName to be valid if destinationLookup is not set
        if (destinationLookup == null && !DestinationName.isSyntaxValid(destination)) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:Invalid destination name=" + destination);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }

        if (QUEUE.equals(destinationType) && subscriptionScope != null) {
            ipe = new InvalidPropertyException(_lgrMID_EXC + "validate:subscriptionScope must not be set if destinationType is " + QUEUE);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }

        if (TOPIC.equals(destinationType)) {
            if (subscriptionScope != null && clientId != null) {
                ipe = new InvalidPropertyException(_lgrMID_EXC + "validate:clientId must not be set if subscriptionScope is set");
                _loggerIM.throwing(_className, "validate()", ipe);
                throw ipe;
            }
            //Require subscriptionName to be
            //valid for durable subscriptions
            if (subscriptionDurability.equals(DURABLE)) {
                if ((subscriptionName == null) ||
                    ("".equals(subscriptionName))) {
                        ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:subscriptionName must be non-null"+
                            "\n\tsubscriptionName="+subscriptionName);
                        _loggerIM.throwing(_className, "validate()", ipe);
                        throw ipe;
                }
            }
        }
        //Require valid values of endpoint properties
        if (endpointExceptionRedeliveryInterval < 1) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointExceptionRedeliveryInterval must be greater than 0"+
                    "\nInvalid value="+endpointExceptionRedeliveryInterval);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointExceptionRedeliveryAttempts < 0) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointExceptionRedeliveryAttempts must be greater than or equal to 0"+
                    "\nInvalid value="+endpointExceptionRedeliveryAttempts);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointPoolResizeTimeout < 1) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointPoolResizeTimeout must be greater than 0"+
                    "\nInvalid value="+endpointPoolResizeTimeout);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointPoolResizeCount < 1) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointPoolResizeCount must be greater than 0"+
                    "\nInvalid value="+endpointPoolResizeCount);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointPoolMaxSize < 1) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointPoolMaxSize must be greater than 0"+
                    "\nInvalid value="+endpointPoolMaxSize);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointPoolSteadySize < 0) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointPoolSteadySize must be greater than or equal to 0"+
                    "\nInvalid value="+endpointPoolSteadySize);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        if (endpointPoolSteadySize > endpointPoolMaxSize) {
            ipe = new InvalidPropertyException(_lgrMID_EXC+"validate:"+
                    "\nendpointPoolSteadySize must be less than or equal to endpointPoolMaxSize"+
                    "\nendpointPoolSteadySize value="+endpointPoolSteadySize+
                    "\nendpointPoolMaxSize value="+endpointPoolSteadySize);
            _loggerIM.throwing(_className, "validate()", ipe);
            throw ipe;
        }
        _loggerIM.exiting(_className, "validate()");
    }

    // ResourceAdapterAssociation interface defined methods //

    /** Sets the Resource Adapter Javabean that is associated with this
     *  ActivationSpec instance.
     *
     *  @param ra The ResourceAdapter Javabean
     */
    public void
    setResourceAdapter(javax.resource.spi.ResourceAdapter ra)
    throws ResourceException
    {
        _loggerIM.entering(_className, "setResourceAdapter()", ra);
        synchronized (this) {
            if (this.ra == null) {
                if (!(ra instanceof com.sun.messaging.jms.ra.ResourceAdapter)) {
                    ResourceException rae = new ResourceException(_lgrMID_EXC+"setResourceAdapter:Incompatible ResourceAdapter class="
                        + ra.getClass());
                    _loggerIM.warning(rae.getMessage());
                    _loggerIM.throwing(_className, "setResourceAdapter()", rae);
                    throw rae;
                }
                this.ra = (com.sun.messaging.jms.ra.ResourceAdapter)ra;
                this.groupName = this.ra.getGroupName();
                this.inClusteredContainer = this.ra.getInClusteredContainer();
                this.raUID = this.ra._getRAUID();
            } else {
                ResourceException rae = new ResourceException(_lgrMID_EXC+"setResourceAdapter:Illegal to re-associate ResourceAdapter");
                _loggerIM.warning(rae.getMessage());
                _loggerIM.throwing(_className, "setResourceAdapter()", rae);
                throw rae;
            }
        }
        _loggerIM.exiting(_className, "setResourceAdapter()");
    }

    /** Gets the Resource Adapter Javabean that is associated with this
     *  ActivationSpec instance.
     *
     *  @return The ResourceAdapter Javabean
     */
    public javax.resource.spi.ResourceAdapter
    getResourceAdapter()
    {
        _loggerIM.entering(_className, "getResourceAdapter()");
        return ra;
    }

    public void setMCF(com.sun.messaging.jms.ra.ManagedConnectionFactory mcf) {
        this.mcf = mcf;
    }

    // ActivationSpec Javabean configuration methods //
    // These Methods can throw java.lang.RuntimeException or subclasses //

    /** Sets the type of the destination for the MessageEndpoint consumer
     *
     *  @param destinationType The destination type
     *         valid values are "javax.jms.Queue" and "javax.jms.Topic"
     *
     *  @throws IllegalArgumentException If destinationType is not one of the above
     */
    public void
    setDestinationType(String destinationType)
    {
        _loggerIM.entering(_className, "setDestinationType()", destinationType);
        //Must be javax.jms.Queue or Topic
        if (QUEUE.equals(destinationType) ||
            TOPIC.equals(destinationType)) {

            this.destinationType = destinationType;

        } else {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setDestinationType:Invalid destinationType="+destinationType);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setDestinationType()", iae);
            throw iae;
        }
    }

    /** Gets the type of the destination for the MessageEndpoint consumer
     *
     *  @return The destination type
     *          values are "javax.jms.Queue" or "javax.jms.Topic"
     */
    public String
    getDestinationType()
    {
        return destinationType;
    }

    /** Sets the name of the destination for the MessageEndpoint consumer
     *
     *  @param destination The destination name
     *
     *  @throws IllegalArgumentException If destination is not a valid name
     */
    public void
    setDestination(String destination)
    {
        _loggerIM.entering(_className, "setDestination()", destination);
        //Destination must be valid name according to MQ rule
        if (!DestinationName.isSyntaxValid(destination)) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setDestination:Invalid destination name="+destination);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setDestination()", iae);
            throw iae;
        }
        this.destination = destination;
    }

    /** Gets the name of the destination for the MessageEndpoint consumer
     *
     *  @return The destination name
     */
    public String
    getDestination()
    {
        _loggerIM.entering(_className, "getDestination()", destination);
        return destination;
    }

    /** Sets the message selector for the MessageEndpoint consumer
     *
     *  @param messageSelector The selector
     */
    public void
    setMessageSelector(String messageSelector)
    {
        _loggerIM.entering(_className, "setMessageSelector()", messageSelector);
        this.messageSelector = messageSelector;
    }

    /** Gets the message selector for the MessageEndpoint consumer
     *
     *  @return The message selector
     */
    public String
    getMessageSelector()
    {
        _loggerIM.entering(_className, "getMessageSelector()", messageSelector);
        return messageSelector;
    }

    /** Sets the acknowledgement mode for the MessageEndpoint consumer
     *
     *  @param acknowledgeMode The acknowledgement mode
     *         valid values are "Auto-acknowledge" and "Dups-ok-acknowledge"
     *         and "No-acknowledge" (Non-Durable/Topic only)
     *
     *  @throws IllegalArgumentException If acknowledgement mode is not valid
     */
    public void
    setAcknowledgeMode(String acknowledgeMode)
    {
        _loggerIM.entering(_className, "setAcknowledgeMode()", acknowledgeMode);
        //Must be Auto-acknowledge or Dups-ok-acknowledge
        if (AUTOACKNOWLEDGE.equals(acknowledgeMode) ||
            DUPSOKACKNOWLEDGE.equals(acknowledgeMode)) {

            this.acknowledgeMode = acknowledgeMode;

        } else {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setAcknowledgeMode:Invalid acknowledgeMode="+acknowledgeMode);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setAcknowledgeMode()", iae);
            throw iae;
        }
    }

    /** Gets the acknowledgement mode for the MessageEndpoint consumer
     *
     *  @return The acknowledgement mode
     *          one of either "Auto-acknowledge" or "Dups-ok-acknowledge" or null
     */
    public String
    getAcknowledgeMode()
    {
        _loggerIM.entering(_className, "getAcknowledgeMode()", acknowledgeMode);
        return acknowledgeMode;
    }

    /** Sets a custom acknowledgement mode for the MessageEndpoint consumer
     *
     *  @param customAcknowledgeMode The customAcknowledgement mode
     *         valid values are "No-acknowledge" (Non-Durable/Topic only)
     *
     *  @throws IllegalArgumentException If customAcknowledgement mode is not valid
     */
    public void
    setCustomAcknowledgeMode(String customAcknowledgeMode)
    {
        _loggerIM.entering(_className, "setCustomAcknowledgeMode()", customAcknowledgeMode);
        //Must be No-acknowledge
        if (NOACKNOWLEDGE.equals(customAcknowledgeMode)) {
            this.customAcknowledgeMode = customAcknowledgeMode;
        } else {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setCustomAcknowledgeMode:Invalid customAcknowledgeMode="+customAcknowledgeMode);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setCustomAcknowledgeMode()", iae);
            throw iae;
        }
    }

    /** Gets the CustomAcknowledgement mode for the MessageEndpoint consumer
     *
     *  @return The CustomAcknowledgement mode
     *          currently only "No-acknowledge" or null
     */
    public String
    getCustomAcknowledgeMode()
    {
        _loggerIM.entering(_className, "getCustomAcknowledgeMode()", customAcknowledgeMode);
        return customAcknowledgeMode;
    }

    /** Sets the subscription durability for the MessageEndpoint consumer
     *
     *  @param subscriptionDurability The durability mode
     *         valid values are "Durable" and "NonDurable"
     *
     *  @throws IllegalArgumentException If subscriptionDurability is not valid
     */
    public void
    setSubscriptionDurability(String subscriptionDurability)
    {
        _loggerIM.entering(_className, "setSubscriptionDurability()", subscriptionDurability);
        //Must be Durable or NonDurable
        if (DURABLE.equals(subscriptionDurability) ||
            NONDURABLE.equals(subscriptionDurability)) {

            this.subscriptionDurability = subscriptionDurability;

        } else {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setSubscriptionDurability:Invalid subscriptionDurability="+subscriptionDurability);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setSubscriptionDurability()", iae);
            throw iae;
        }
    }

    /** Gets the subscription durability for the MessageEndpoint consumer
     *
     *  @return The subscription durability
     *          one of either "Durable" or "NonDurable" or null
     */
    public String
    getSubscriptionDurability()
    {
        _loggerIM.entering(_className, "getSubscriptionDurability()", subscriptionDurability);
        return subscriptionDurability;
    }

    /** Sets the client identifier for the MessageEndpoint consumer
     *
     *  @param clientId The client identifier
     */
    public void
    setClientId(String clientId)
    {
        _loggerIM.entering(_className, "setClientId()", clientId);
        this.clientId = clientId;
    }

    /** Return the client identifier for the MessageEndpoint consumer
     *
     *  @return The client identifier
     */
    public String
    getClientId()
    {
        _loggerIM.entering(_className, "getClientId()", clientId);
        return clientId;
    }

    /** Sets the subscription name for the MessageEndpoint consumer
     *
     *  @param subscriptionName The name of the subscription
     */
    public void
    setSubscriptionName(String subscriptionName)
    {
        _loggerIM.entering(_className, "setSubscriptionName()", subscriptionName);
        this.subscriptionName = subscriptionName; 
    }

    /** Returns the subscription name for the MessageEndpoint consumer
     *
     *  @return The name of the subscription
     */
    public String
    getSubscriptionName()
    {
        _loggerIM.entering(_className, "getSubscriptionName()", subscriptionName);
        return subscriptionName;
    }

    /** Sets the subscription scope for the MessageEndpoint consumer
     *
     *  @param subscriptionScope The scope of the subscription
     *         valid values are "Instance" and "Cluster"
     *
     *  @throws IllegalArgumentException If subscriptionScope is not valid
     */
// Disable the feature of JMS_SPEC-73 temporarily for it is removed from JMS 2.0.
// It will be added back in future release.
/*
    public void
    setSubscriptionScope(String subscriptionScope)
    {
        _loggerIM.entering(_className, "setSubscriptionScope()", subscriptionScope);
        //Must be Instance or Cluster
        if (INSTANCE.equals(subscriptionScope) ||
            CLUSTER.equals(subscriptionScope)) {

            this.subscriptionScope = subscriptionScope;

        } else {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC + "setSubscriptionScope:Invalid subscriptionScope=" + subscriptionScope);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setSubscriptionScope()", iae);
            throw iae;
        }
    }
*/
    /** Returns the subscription scope for the MessageEndpoint consumer
     *
     *  @return The scope of the subscription
     *          one of either "Instance" or "Cluster" or null
     */
    public String
    getSubscriptionScope()
    {
        _loggerIM.entering(_className, "getSubscriptionScope()", subscriptionScope);
        return subscriptionScope;
    }

    /** Sets the connectionFactoryJNDIName for the MessageEndpoint consumer
     *
     *  @param connectionFactoryJNDIName The connectionFactoryJNDIName
     */
    public void
    _setConnectionFactoryJNDIName(String connectionFactoryJNDIName)
    {
        _loggerIM.entering(_className, "_setConnectionFactoryJNDIName()", connectionFactoryJNDIName);
        this.connectionFactoryJNDIName = connectionFactoryJNDIName; 
    }

    /** Returns the connectionFactoryJNDIName for the MessageEndpoint consumer
     *
     *  @return The connectionFactoryJNDIName
     */
    public String
    _getConnectionFactoryJNDIName()
    {
        _loggerIM.entering(_className, "_getConnectionFactoryJNDIName()", connectionFactoryJNDIName);
        return connectionFactoryJNDIName;
    }

    /** Sets the endpointPoolMaxSize for the MessageEndpoint consumer
     *
     *  @param endpointPoolMaxSize The endpointPoolMaxSize
     */
    public void
    setEndpointPoolMaxSize(int endpointPoolMaxSize)
    {
        _loggerIM.entering(_className, "setEndpointPoolMaxSize()", Integer.toString(endpointPoolMaxSize));
        if (endpointPoolMaxSize < 1) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointPoolMaxSize:"+
                    "Value must be greater than 0"+
                    "Invalid value="+endpointPoolMaxSize);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointPoolMaxSize()", iae);
            throw iae;
        }
        this.endpointPoolMaxSize = endpointPoolMaxSize; 
    }

    /** Returns the endpointPoolMaxSize for the MessageEndpoint consumer
     *
     *  @return The endpointPoolMaxSize
     */
    public int
    getEndpointPoolMaxSize()
    {
        _loggerIM.entering(_className, "getEndpointPoolMaxSize()", Integer.toString(endpointPoolMaxSize));
        return endpointPoolMaxSize;
    }

    /** Sets the endpointPoolSteadySize for the MessageEndpoint consumer
     *
     *  @param endpointPoolSteadySize The endpointPoolSteadySize
     */
    public void
    setEndpointPoolSteadySize(int endpointPoolSteadySize)
    {
        _loggerIM.entering(_className, "setEndpointPoolSteadySize()", Integer.toString(endpointPoolSteadySize));
        if (endpointPoolSteadySize < 0) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointPoolSteadySize:"+
                    "Value must be greater than or equal to 0"+
                    "Invalid value="+endpointPoolSteadySize);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointPoolSteadySize()", iae);
            throw iae;
        }
        this.endpointPoolSteadySize = endpointPoolSteadySize; 
    }

    /** Returns the endpointPoolSteadySize for the MessageEndpoint consumer
     *
     *  @return The endpointPoolSteadySize
     */
    public int
    getEndpointPoolSteadySize()
    {
        _loggerIM.entering(_className, "getEndpointPoolSteadySize()", Integer.toString(endpointPoolSteadySize));
        return endpointPoolSteadySize;
    }

    /** Sets the endpointPoolResizeCount for the MessageEndpoint consumer
     *
     *  @param endpointPoolResizeCount The endpointPoolResizeCount
     */
    public void
    setEndpointPoolResizeCount(int endpointPoolResizeCount)
    {
        _loggerIM.entering(_className, "setEndpointPoolResizeCount()", Integer.toString(endpointPoolResizeCount));
        if (endpointPoolResizeCount < 1) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointPoolResizeCount:"+
                    "Value must be greater than 0:"+
                    "Invalid value="+endpointPoolResizeCount);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointPoolResizeCount()", iae);
            throw iae;
        }
        this.endpointPoolResizeCount = endpointPoolResizeCount; 
    }

    /** Returns the endpointPoolResizeCount for the MessageEndpoint consumer
     *
     *  @return The endpointPoolResizeCount
     */
    public int
    getEndpointPoolResizeCount()
    {
        _loggerIM.entering(_className, "getEndpointPoolResizeCount()", Integer.toString(endpointPoolResizeCount));
        return endpointPoolResizeCount;
    }

    /** Sets the endpointPoolResizeTimeout for the MessageEndpoint consumer
     *
     *  @param endpointPoolResizeTimeout The endpointPoolResizeTimeout
     */
    public void
    setEndpointPoolResizeTimeout(int endpointPoolResizeTimeout)
    {
        _loggerIM.entering(_className, "setEndpointPoolResizeTimeout()", Integer.toString(endpointPoolResizeTimeout));
        if (endpointPoolResizeTimeout < 1) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointPoolResizeTimeout:"+
                    "Value must be greater than 0:"+
                    "Invalid value="+endpointPoolResizeTimeout);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointPoolResizeTimeout()", iae);
            throw iae;
        }
        this.endpointPoolResizeTimeout = endpointPoolResizeTimeout; 
    }

    /** Returns the endpointPoolResizeTimeout for the MessageEndpoint consumer
     *
     *  @return The endpointPoolResizeTimeout
     */
    public int
    getEndpointPoolResizeTimeout()
    {
        _loggerIM.entering(_className, "getEndpointPoolResizeTimeout()", Integer.toString(endpointPoolResizeTimeout));
        return endpointPoolResizeTimeout;
    }

    /** Sets the maximum number of Redelivery attempts
     *  to an Endpoint that throws an Exception.
     *  This enables the RA to stop
     *  endlessly delivering messages to an Endpoint
     *  that repeatedly throws an Exception
     *
     *  @param endpointExceptionRedeliveryAttempts The maximum number of Redelivery attempts
     */
    public void
    setEndpointExceptionRedeliveryAttempts(int endpointExceptionRedeliveryAttempts)
    {
        _loggerIM.entering(_className, "setEndpointExceptionRedeliveryAttempts()", Integer.toString(endpointExceptionRedeliveryAttempts));
        if (endpointExceptionRedeliveryAttempts < 0) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointExceptionRedeliveryAttempts:"+
                    "Value must be greater than or equal to 0:"+
                    "Invalid value="+endpointExceptionRedeliveryAttempts);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointExceptionRedeliveryAttempts()", iae);
            throw iae;
        }
        this.endpointExceptionRedeliveryAttempts = endpointExceptionRedeliveryAttempts; 
    }

    /** Returns the the maximum number of Redelivery attempts
     *  to an Endpoint that throws an Exception.
     *  This enables the RA to stop
     *  endlessly delivering messages to an Endpoint
     *  that repeatedly throws an Exception
     *
     *  @return The maximum number of Redelivery attempts
     *          to an Endpoint.
     */
    public int
    getEndpointExceptionRedeliveryAttempts()
    {
        _loggerIM.entering(_className, "getEndpointExceptionRedeliveryAttempts()", Integer.toString(endpointExceptionRedeliveryAttempts));
        return endpointExceptionRedeliveryAttempts;
    }

    /** Sets the interval for Redelivery attempts
     *  to an Endpoint that throws an Exception.
     *
     *  @param endpointExceptionRedeliveryInterval The maximum number of Redelivery attempts
     */
    public void
    setEndpointExceptionRedeliveryInterval(int endpointExceptionRedeliveryInterval)
    {
        _loggerIM.entering(_className, "setEndpointExceptionRedeliveryInterval()", Integer.toString(endpointExceptionRedeliveryInterval));
        if (endpointExceptionRedeliveryInterval < 1) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setEndpointExceptionRedeliveryInterval:"+
                    "Value must be greater than 0:"+
                    "Invalid value="+endpointExceptionRedeliveryInterval);
            _loggerIM.warning(iae.getMessage());
            _loggerIM.throwing(_className, "setEndpointExceptionRedeliveryInterval()", iae);
            throw iae;
        }
        this.endpointExceptionRedeliveryInterval = endpointExceptionRedeliveryInterval; 
    }

    /** Returns the interval for Redelivery attempts
     *  to an Endpoint that throws an Exception.
     *
     *  @return The interval for Redelivery attempts
     *          to an Endpoint.
     */
    public int
    getEndpointExceptionRedeliveryInterval()
    {
        _loggerIM.entering(_className, "getEndpointExceptionRedeliveryInterval()", Integer.toString(endpointExceptionRedeliveryInterval));
        return endpointExceptionRedeliveryInterval;
    }

    /** Sets whether to send undeliverable messages to the DMQ
     *
     *  @param sendUndeliverableMsgsToDMQ If true; sends the undeliverable
     *         messages to the DMQ.
     */
    public void
    setSendUndeliverableMsgsToDMQ(boolean sendUndeliverableMsgsToDMQ)
    {
        _loggerIM.entering(_className, "setSendUndeliverableMsgsToDMQ()", Boolean.toString(sendUndeliverableMsgsToDMQ));
        this.sendUndeliverableMsgsToDMQ = sendUndeliverableMsgsToDMQ; 
    }

    /** Returns whether to send undeliverable messages to the DMQ
     *
     *  @return sendUndeliverableMsgsToDM
     *  Whether to send undeliverable messages to the DMQ
     */
    public boolean
    getSendUndeliverableMsgsToDMQ()
    {
        _loggerIM.entering(_className, "getSendUndeliverableMsgsToDMQ()", Boolean.toString(sendUndeliverableMsgsToDMQ));
        return sendUndeliverableMsgsToDMQ;
    }

    /** Sets the ContextClassLoader to be used for the MessageEndpoint consumer
     *
     *  @param contextClassLoader The contextClassLoader
     */
    public void
    setContextClassLoader(ClassLoader contextClassLoader)
    {
        _loggerIM.entering(_className, "setContextClassLoader()", contextClassLoader);
        this.contextClassLoader = contextClassLoader; 
    }

    /** Returns the contextClassLoader used for the MessageEndpoint consumer
     *
     *  @return The contextClassLoader
     */
    public ClassLoader
    getContextClassLoader()
    {
        _loggerIM.entering(_className, "getContextClassLoader()", contextClassLoader);
        return contextClassLoader;
    }

	/**
	 * Sets the specified addressList for this ActivationSpec
	 * 
	 * @param addressList
	 */
	public void setAddressList(String addressList) {
		_loggerIM.entering(_className, "setAddressList()", addressList);
		this.addressList = addressList;
		if ((addressList != null) && !"".equals(addressList)) {
			this.addressListSet = true;
		}
	}

	/**
	 * Returns the specified addressList for this ActivationSpec
	 * 
	 * @return The addressList
	 */
	public String getAddressList() {
		_loggerIM.entering(_className, "getAddressList()", addressList);
		return addressList;
	}
	
	public void setAddressListBehavior(String addressListBehavior) {
		_loggerIM.entering(_className, "setAddressListBehavior()", addressListBehavior);
		this.addressListBehavior = addressListBehavior;		
	}

    /**
     * Returns the specified addressListBehavior for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The addressListBehavior
     */
	public String getAddressListBehavior() {
		_loggerIM.entering(_className, "getAddressListBehavior()", addressListBehavior);
		
        if (addressListBehavior != null) {
            return addressListBehavior;
        } else {
            if (mcf != null)
                return mcf.getAddressListBehavior();
            else if (ra != null)
                return ra.getAddressListBehavior();
            else
                return null;
        }
	}	

	/**
	 * Set the name of the MDB (or a name which represents it). 
     * This is used when sharing non-durable topic subscriptions in a clustered container to
     * set the clientID. This then allows the subscription to be shared with the MDB of the same name
     * in the other container instances. It must be unique within an individual container instance.
     * Only used in a clustered container when shared subscriptions are enabled.
     * Only used for non-durable topic subscriptions.
     * Only used if clientID is not explicitly set, and is mandatory if this is the case.
     * 
     * For info: In GlassFish 3.1 this will be set to <domainName>#<clusterName>#<EJBMessageBeanDescriptor.uniqueId>. 
     * 
     * @param mdbName
     */
    public void  setMdbName(String mdbName)
    {
        _loggerIM.entering(_className, "setMdbName()", mdbName);
        this.mdbName = mdbName; 
    }

	/**
	 * Returns the name of the MDB (or a name which represents it).
	 * 
	 * @return
	 */
	public String getMdbName() {
		_loggerIM.entering(_className, "getMdbName()", mdbName);
		return mdbName;
	}

	/**
	 * Sets the userName for this activationSpec.
	 * 
	 * @param userName
	 */
	public void setUserName(String userName) {
		_loggerIM.entering(_className, "setUserName()", userName);
		this.userName = userName;
	}

    /**
     * Returns the specified username for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The username
     */
	public String getUserName() {
		_loggerIM.entering(_className, "getUserName()", userName);
		if (userName != null) {
		    return userName;
		} else {
		    if (mcf != null && mcf.getUserName() != null)
		        return mcf.getUserName();
		    else if (ra != null)
			    return ra.getUserName();
			else
			    return null;
		}
	}

	/**
	 * Sets the password for this activationSpec.
	 * 
     * @param password
     */
    public void setPassword(String password) {
        _loggerIM.entering(_className, "setPassword()");
        this.password = password;
    }
 
    /**
     * Returns the specified password for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The password
     */
    public String getPassword() {
        _loggerIM.entering(_className, "getPassword()");
        if (password != null) {
            return password;
        } else {
            if (mcf != null && mcf.getPassword() != null)
                return mcf.getPassword();
            else if (ra != null)
                return ra.getPassword();
            else
                return null;
        }
    }
    
	/**
	 * Sets the value of addressListIterations for this activationSpec.
	 * 
     * @param password
     */
	public void setAddressListIterations(int addressListIterations) {
        _loggerIM.entering(_className, "setAddressListIterations()");
        this.addressListIterations = addressListIterations;
        this.addressListIterationsSet=true;
	}

    /**
     * Returns the specified addressListIterations for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The addressListIterations
     */
	public int getAddressListIterations() {
		if (addressListIterationsSet){
			return addressListIterations;
		} else {
		    if (mcf != null)
		        return mcf.getAddressListIterations();
		    else
			    return ra.getAddressListIterations();
		}
	}

	/**
	 * Sets the value of reconnectAttempts for this activationSpec.
	 * 
     * @param password
     */
	public void setReconnectAttempts(int reconnectAttempts) {
        _loggerIM.entering(_className, "setReconnectAttempts()");
        this.reconnectAttempts = reconnectAttempts;
		this.reconnectAttemptsSet=true;
	}

    /**
     * Returns the specified reconnectAttempts for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The reconnectAttempts
     */
	public int getReconnectAttempts() {
		if (reconnectAttemptsSet){
			return reconnectAttempts;
		} else {
		    if (mcf != null)
		        return mcf.getReconnectAttempts();
		    else
			    return ra.getReconnectAttempts();
		}
	}
	

	/**
	 * Sets the value of reconnectInterval for this activationSpec.
	 * 
     * @param password
     */
	public void setReconnectInterval(int reconnectInterval) {
        _loggerIM.entering(_className, "setReconnectInterval()");
        this.reconnectInterval = reconnectInterval;
		this.reconnectIntervalSet=true;
	}


    /**
     * Returns the specified reconnectInterval for this ActivationSpec if it is set,
     * otherwise, returns the value specified on ManagedConnectionFactory if it is set,
     * otherwise, returns the value specified on ResourceAdaptor.
     * @return The reconnectInterval
     */
	public int getReconnectInterval() {
		if (reconnectIntervalSet){
			return reconnectInterval;
		} else {
		    if (mcf != null)
		        return mcf.getReconnectInterval();
		    else
			    return ra.getReconnectInterval();
		}
	}
	
	/**
	 * This is the method for setting reconnectEnabled on this activationSpec
	 * It logs a warning to say that this is not allowed
	 */
	public void setReconnectEnabled(boolean flag) {
		 _loggerIM.warning(_lgrMID_WRN+"Property reconnectEnabled cannot be set on con.sun.messaging.jms.ra.ActivationSpec, ignoring specified value");
	}

	/**
	 * Returns the value of reconnectEnabled that should be used for this activationSpec.
	 * This is always false, as the reconnection needs to be done by the resource adapter
	 * rather than the MQ client.
	 * 
	 * @return 
	 */
	public boolean getReconnectEnabled() {
		// Force reconnectEnabled to false for XAR success
		return false;
	}

    /**
     * Returns the destinationLookup configured for this activationSpec.
     * 
     * @return 
     */
    public String getDestinationLookup() {
        return destinationLookup;
    }

    /**
     * Set the destinationLookup configured for this activationSpec.
     * 
     * @param destinationLookup
     */
    public void setDestinationLookup(String destinationLookup) {
        this.destinationLookup = destinationLookup;
    }

    /**
     * Returns the connectionFactoryLookup configured for this activationSpec.
     * 
     * @return 
     */
    public String getConnectionFactoryLookup() {
        return connectionFactoryLookup;
    }

    /**
     * Set the connectionFactoryLookup configured for this activationSpec.
     * 
     * @param destinationLookup
     */
    public void setConnectionFactoryLookup(String connectionFactoryLookup) {
        this.connectionFactoryLookup = connectionFactoryLookup;
    }

    /**
     * Set additional arbitrary connection factory properties
     * 
     * The properties must be specified as a String containing a comma-separated list of name=value pairs
     * e.g. prop1=value1,prop2=value2
     * If a value contains a = or , you can either 
     * place the whole value between quotes (prop1="val=ue") or 
     * use \ as an escape character (prop1=val\,ue)
     * 
     * This method cannot be used to set properties which are configured internally or which have their own setter methods. These are:
     * imqReconnectEnabled, imqReconnectAttempts, imqReconnectInterval, imqDefaultUsername, 
     * imqDefaultPassword, imqAddressList, imqAddressListIterations, imqAddressListBehavior
     * 
     * Any values specified for those properties will be ignored
     *
     *
     * @param props connection factory properties as a comma-separated list of name=value pairs
     */
    public void setOptions(String props){
    	options=props;
    }

    /**
     * Get additional arbitrary connection factory properties
     *
     * The properties will be merged among ActivationSpec and ManagedConnectionFactory.
     * For example, if the ActivationSpec defines options="prop1=value1,prop2=value2"
     * and the ManagedConnectionFactory defines options="prop2=valueB,prop3=valueC"
     * then the merged version should be "prop1=value1,prop2=value2,prop3=valueC"
     */
    public String getOptions(){
        String mergedOptions = null;
        if (options != null) {
            if (mcf != null && mcf.getOptions() != null) {
                mergedOptions = mergeOptions(options, mcf.getOptions());
            } else {
                mergedOptions = options;
            }
        } else if (mcf != null && mcf.getOptions() != null) {
            mergedOptions = mcf.getOptions();
        }
        return mergedOptions;
    }

    /**
     * Merge the options of ActivationSpec and ManagedConnectionFactory.
     * A property of Options defined in ActivationSpec will win
     * if ManagedConnectionFactory has a property defined with the same name.
     */
    private String mergeOptions(String options, String mcfOptions) {
        Hashtable<String, String> props = null;
        try {
            props = CustomTokenizer.parseToProperties(options);
        } catch (InvalidPropertyException ipe) {
            // syntax error in properties
            String message="ActivationSpec property options has invalid value " + options + " error is: " + ipe.getMessage(); 
            _loggerIM.warning(_lgrMID_WRN + message);
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC + message); 
            iae.initCause(ipe);
            throw iae;
        }
        Hashtable<String, String> mcfProps = null;
        try {
            if (mcf != null)  // mcf should always be non-null logically
                mcfProps = CustomTokenizer.parseToProperties(mcf.getOptions());
        } catch (InvalidPropertyException ipe) {
            // syntax error in properties
            String message="ManagedConnectionFactory property options has invalid value " + mcf.getOptions() + " error is: " + ipe.getMessage(); 
            _loggerIM.warning(_lgrMID_WRN + message);
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC + message); 
            iae.initCause(ipe);
            throw iae;
        }
        Hashtable<String, String> mergedProps = new Hashtable<String, String>();
        mergedProps.putAll(mcfProps);
        Enumeration<String> keys = props.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            mergedProps.put(key, props.get(key));
        }
        StringBuffer mergedOptions = new StringBuffer();
        keys = mergedProps.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            mergedOptions.append(",").append(key).append("=").append(mergedProps.get(key));
        }
        return mergedOptions.substring(1);
    }
     
    //////////////////////////////////////////////////////////////////////////////////


	/**
	 * Return the addressList to use for this activation
	 * 
	 * If addressList is specified on this ActivatioSpec then this value is returned, unmodified.
	 * If addressList is not specified on this ActivationSpec then a suitably adjusted value is obtained from the ManagedConnectionFactory
	 * If addressList is not specified on this ManagedConnectionFactory then a suitably adjusted value is obtained from the ResourceAdapter
	 * 
	 * @return
	 */
	protected String _AddressList() {
		if (addressList != null) {
			return addressList;
		} else {
		    if (mcf != null) {
		        return mcf.getAddressList();
			} else if (ra != null) {
				return ((com.sun.messaging.jms.ra.ResourceAdapter) ra)._getEffectiveConnectionURL();
			} else {
				return "localhost";
			}
		}
	}

    public void _setDeliverySerial(boolean mode) {
        deliverySerial = mode;
    }

    protected boolean _getDeliverySerial() {
        return deliverySerial;
    }

    protected boolean _deliverySerial() {
        return deliverySerial;
    }

    /**
     * If this is a clustered container, return a name which identifies the cluster
     * @return
     */
    protected String _getGroupName() {
        return groupName;
    }

    /**
     * If this is a clustered container, specify a name which identifies the cluster. This is always optional.
     * When sharing non-durable topic subscriptions in a clustered container this is appended to the clientID.
     * Only used in a clustered container when shared subscriptions are enabled.
     * Only used for non-durable topic subscriptions.
     * Only used if clientID is not explicitly set, and is always optional.
     *
     * @param groupName
     */
    protected void _setGroupName(String groupName) {
        this.groupName = groupName;
    }

    protected String _getRAUID() {
        return raUID;
    }

    protected void _setRAUID(String raUID) {
        this.raUID = raUID;
    }

    protected void _setInClusteredContainer(boolean inClusteredContainer) {
        this.inClusteredContainer = inClusteredContainer;
    }

    protected boolean _isInClusteredContainerSet() {
        return inClusteredContainer;
    }

    protected boolean _isNoAckDeliverySet() {
        return (NOACKNOWLEDGE.equals(customAcknowledgeMode));
    }

    protected boolean _isDurableSet() {
        return (DURABLE.equals(subscriptionDurability));
    }

    protected boolean _isDestTypeQueueSet() {
        return (QUEUE.equals(destinationType));
    }

    protected boolean _isDestTypeTopicSet() {
        return (TOPIC.equals(destinationType));
    }

    /**
     * Return whether this activation should use RADirect. 
     * Return true if the RA is configured to use RADirect and we haven't overridden addressList in the activation spec.
     * Otherwise return false.
     * @return
     */
    protected boolean useRADirect() {
        if (this.ra != null){
            return (ra._isRADirect() && !addressListSet);
        } else {
            return false; 
        }
    }
    
    public String
    toString()
    {
        return ("ActvationSpec configuration=\n"+
                "\tDestinationType                     ="+destinationType+"\n"+
                "\tDestination                         ="+destination+"\n"+
                "\tDestinationLookup                   ="+destinationLookup+"\n"+
                "\tConnectionFactoryLookup             ="+connectionFactoryLookup+"\n"+
                "\tMessageSelector                     ="+messageSelector+"\n"+
                "\tAcknowledgeMode                     ="+acknowledgeMode+"\n"+
                "\tSubscriptionDurability              ="+subscriptionDurability+"\n"+
                "\tuseSharedSubscriptionInClusteredContainer="+useSharedSubscriptionInClusteredContainer+"\n"+
                "\tClientId                            ="+clientId+"\n"+
                "\tSubscriptionName                    ="+subscriptionName+"\n"+
                "\tEndpointPoolMaxSize                 ="+endpointPoolMaxSize+"\n"+
                "\tEndpointPoolSteadySize              ="+endpointPoolSteadySize+"\n"+
                "\tEndpointPoolResizeCount             ="+endpointPoolResizeCount+"\n"+
                "\tEndpointPoolResizeTimeout           ="+endpointPoolResizeTimeout+"\n"+
                "\tEndpointExceptionRedeliveryAttempts ="+endpointExceptionRedeliveryAttempts+"\n"+
                "\tEndpointExceptionRedeliveryInterval ="+endpointExceptionRedeliveryInterval+"\n"+
                "\tSendUndeliverableMsgsToDMQ          ="+sendUndeliverableMsgsToDMQ+"\n"+
                "\tGroupName                           ="+groupName+"\n"+
                "\tRAUID                               ="+raUID+"\n"+
                "\tInClusteredContainer                ="+inClusteredContainer+"\n"+
                "\tMdbName                             ="+mdbName+"\n"+
                "\tUserName                            ="+userName+"\n"+
                "\tAddressList (configured)            ="+addressList+"\n"+
                "\tAddressList (in effect)             ="+_AddressList()+"\n"+
                "\taddressListBehavior (configured)    ="+addressListBehavior+"\n"+
                "\taddressListBehavior (in effect)     ="+getAddressListBehavior()+"\n"+
                "\taddressListIterations (configured)  ="+addressListIterations+"\n"+
                "\taddressListIterations (in effect)   ="+getAddressListIterations()+"\n"+
                "\treconnectAttempts (configured)      ="+reconnectAttempts+"\n"+
                "\treconnectAttempts (in effect)       ="+getReconnectAttempts()+"\n"+
                "\treconnectInterval (configured)      ="+reconnectInterval+"\n"+
                "\treconnectInterval  (in effect)      ="+getReconnectInterval()+"\n"+
                "\toptions  (configured)               ="+options+"\n"+
                "\toptions  (in effec)                 ="+getOptions()+"\n");
    }


	public boolean isUseSharedSubscriptionInClusteredContainer() {
		return useSharedSubscriptionInClusteredContainer;
	}


	public void setUseSharedSubscriptionInClusteredContainer(
			boolean useSharedSubscriptionInClusteredContainer) {
		this.useSharedSubscriptionInClusteredContainer = useSharedSubscriptionInClusteredContainer;
	}














}
