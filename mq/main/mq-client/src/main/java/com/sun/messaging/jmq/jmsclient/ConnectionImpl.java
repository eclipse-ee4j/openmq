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
 *  @(#)ConnectionImpl.java	1.184 03/21/08
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;

import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.*;

import java.io.PrintStream;
import java.net.InetAddress;
//import java.net.UnknownHostException;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.auth.api.client.*;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.ReadWritePacket;

import com.sun.messaging.jmq.util.DebugPrinter;
import com.sun.messaging.jms.MQRuntimeException;
import com.sun.messaging.jms.notification.*;

import com.sun.messaging.jmq.jmsclient.notification.*;
import com.sun.messaging.jmq.jmsclient.resources.*;
import com.sun.messaging.jms.MQIllegalStateRuntimeException;
import com.sun.messaging.jms.MQInvalidClientIDRuntimeException;

//import java.util.List;

/** A JMS Connection is a client's active connection to its JMS provider.
  * It will typically allocate provider resources outside the Java virtual
  * machine.
  *
  * <P>Connections support concurrent use.
  *
  * <P>A Connection serves several purposes:
  *
  * <UL>
  *   <LI>It encapsulates an open connection with a JMS provider. It
  *       typically represents an open TCP/IP socket between a client and
  *       a provider service daemon.
  *   <LI>Its creation is where client authenticating takes place.
  *   <LI>It can specify a unique client identifier.
  *   <LI>It provides ConnectionMetaData.
  *   <LI>It supports an optional ExceptionListener.
  * </UL>
  *
  * <P>Due to the authentication and communication setup done when a
  * Connection is created, a Connection is a relatively heavy-weight JMS
  * object. Most clients will do all their messaging with a single Connection.
  * Other more advanced applications may use several Connections. JMS does
  * not architect a reason for using multiple connections; however, there may
  * be operational reasons for doing so.
  *
  * <P>A JMS client typically creates a Connection; one or more Sessions;
  * and a number of message producers and consumers. When a Connection is
  * created it is in stopped mode. That means that no messages are being
  * delivered.
  *
  * <P>It is typical to leave the Connection in stopped mode until setup
  * is complete. At that point the Connection's start() method is called
  * and messages begin arriving at the Connection's consumers. This setup
  * convention minimizes any client confusion that may result from
  * asynchronous message delivery while the client is still in the process
  * of setting itself up.
  *
  * <P>A Connection can immediately be started and the setup can be done
  * afterwards. Clients that do this must be prepared to handle asynchronous
  * message delivery while they are still in the process of setting up.
  *
  * <P>A message producer can send messages while a Connection is stopped.
  *
  * @see         javax.jms.ConnectionFactory
  * @see         javax.jms.QueueConnection
  * @see         javax.jms.TopicConnection
  */

public class ConnectionImpl implements com.sun.messaging.jms.Connection,Traceable,ContextableConnection {

    private static final String ENABLE_FAILOVER_PROP = "imq.enable_failover";

    protected static final Version version = new Version();

    private boolean daemonThreads = false;

    private boolean hasNamespace = false;
    private String raNamespaceUID = null;
    private Object nsSyncObj = new Object();

    ExceptionListener exceptionListener = null;

    protected String clientID = null;

    protected String clientIPAddress = null;

    protected ReadChannel readChannel = null;
    protected WriteChannel writeChannel = null;
    protected ProtocolHandler protocolHandler = null;
    private AuthenticationProtocolHandler authenticationHandler = null;

    // XXX PROTOCOL3.5 --
    protected FlowControl flowControl = null;

    // XXX PROTOCOL3.5 --
    // Improved reconnect and connection failover.
    protected ConnectionInitiator initiator = null;
    protected boolean reconnecting = false;
    private Object reconnectSyncObj = new Object();

    // XXX PROTOCOL3.5 --
    // All the producers for this connection. We need to quickly find
    // the producer object when we get RESUME_FLOW packets...
    protected Hashtable producers = new Hashtable();

    //exception handler
    protected ExceptionHandler exceptionHandler = null;
    //table to hold Consumer objects
    protected InterestTable interestTable = null;
    //table to hold SessionQueue objects.  pkts are put in SessionQueue based
    //on sessionID
    protected ReadQTable readQTable = null;

    //table to hold queues for acknowledgements. - XXX PROTOCOL2.1 change.
    protected ReadQTable ackQTable = null;

    //XXX PROTOCOL2.1 change.
    //table to hold RequestMetaData.  Such as interestID/consumer pair.
    protected Hashtable requestMetaData = null ;

    //bug 6172663
    //protected boolean isTopicConnection = true;
    protected boolean isTopicConnection = false;

    //bug 6172663
    protected boolean isQueueConnection = false;

    protected boolean isStopped = true;

    //XXX:GT TBF **** this is closed until opened !!!
    protected boolean isClosed = true;

    //bug 6189645 -- general blocking issues.
    //this flag is set to true when close() is called.
    protected boolean isCloseCalled = false;

    //protected boolean isClosing = false;

    protected boolean isSuspended = false;
    //table to hold Sessions.  When a session is created, it is put in here.
    protected Vector sessionTable = null;

    protected Vector connectionConsumerTable = null;

    protected Vector tempDestTable = null;
    //next available session ID
    protected long nextSessionId = 0;
    //if nextSessionId reached MAX_INTEREST_ID, this flag is set to true
    protected boolean sessionIdReset = false;
    //next available transactionID
    protected int nextTransactionID = 0;
    protected boolean openedFromXA = false;

    //max queue size in the session queue.
    //protected int maxQueueSize = 1000;

    //protected int minQueueSize = 10;

    //setting this to true if wanted to have session reader to check queue
    //sizes and protect the system from memory over flow.
    protected boolean protectMode = false;
    //default value for flow control message size
    protected int flowControlMsgSize = 100;
    //water mark - used in flow control
    //not used if protectMode == false
    protected int flowControlWaterMark = 1000;

    //XXX PROTOCOL3.5 New consumer flow control attributes.
    protected int prefetchMaxMsgCount = 100;
    protected int prefetchThresholdPercent = 50;
    //4.5
    protected boolean consumerFlowLimitPrefetch = true;

    //5.0
    protected int onMessageExRedeliveryAttempts = 1; 
    protected long onMessageExRedeliveryIntervals = 500L; //millisecs

    //XXX PROTOCOL3.5 Connection reconnect & failover attributes.
    protected volatile boolean imqReconnect = false;
    protected boolean failoverEnabled = false;
    protected Hashtable licenseProps = null;

    //Enable Shared ClientID for this connection
    protected boolean imqEnableSharedClientID = false;
    //Enable Shared Subscriptions for this connection (non-durable)
    protected boolean imqEnableSharedSubscriptions = false;

    //dups ok limit.  default is 10
    protected int dupsOkLimit = 10;

    //if set, we check if unack msgs has exceeded the limit.
    protected boolean isAckLimited = false;

    //client ack limit.  default 100.
    protected int ackLimit = 100;

    //for temporary destination name generation.
    private int tempDestSequence = 0;
    //for connection recovery to check if this connection has associated
    //with temp destination.  We do not recover connection if there is
    //active temp destination for the connection.
    private int tempDestCounter = 0;

    //flag for session reader to check if the connection is broken
    //This flag is set by ReadChannel when connection is broken.
    protected volatile boolean connectionIsBroken = false;

    //flag to indicate connection recover is in process
    protected volatile boolean recoverInProcess = false;

    //connection ID - for thread naming purpose
    //XXX PROTOCOL2.1 from int to long.
    private static long nextConnectionID = 0;
    //current connection ID
    protected Long connectionID = null;

    //local connection ID.  This is used for local ID. The connectionID
    //will be reassigned by broker.
    protected long localID = 0;
    
    // brokerSessionID (sent by broker in HELLO_REPLY)
    protected long brokerSessionID = 0;

    //Override flags and values for Administered JMS Msg Headers
    protected boolean jmqOverrideJMSMsgHeaders = false;
    protected boolean jmqOverrideJMSDeliveryMode = false;
    protected boolean jmqOverrideJMSExpiration = false;
    protected boolean jmqOverrideJMSPriority = false;
    protected boolean jmqOverrideMsgsToTempDests = false;

    protected int jmqJMSDeliveryMode = DeliveryMode.PERSISTENT;
    protected long jmqJMSExpiration = 0L;
    protected int jmqJMSPriority = 4;

    //dups ok ack time out properties - dupsOkPerf
    //if set, dups ok acks when reached unacked limit or session Q is empty.
    protected boolean dupsOkAckOnEmptyQueue = false;

    //The magic number from Dipol.
    protected long dupsOkAckTimeout = 7000L; //seven seconds.

    protected long asyncSendCompletionWaitTimeout = 180000L; //3min

    /**
     * 
     * Value of ConnectionFactory property imqSocketConnectTimeout
     * This property defines the socket timeout, in milliseconds, 
     * used when a TCP connection is made to the broker. 
     * A timeout of zero is interpreted as an infinite timeout. 
     * The connection will then block until established or an error occurs. 
     * This property is used when connecting to the port mapper as well
     * as when connecting to the required service.
     */
    protected int imqSocketConnectTimeout = 0;

    /**
     * Port Mapper client socket read timeout
     */
    protected Integer imqPortMapperSoTimeout = null;

    /**
     * flag used for setting client ID.
     * If set to false, API user can not set clientID.
     */
    protected boolean allowToSetClientID = true;

    protected boolean adminSetClientID = false;

    //sync object for synchronizing sequences
    private Object syncObj = new Object();

    //save this for reconnect.  'transient' is for paranoid.
    transient private String userName = null;
    transient private String password = null;

    private String ackOnProduce = null;
    private String ackOnAcknowledge = null;
    private String connectionType = ClientConstants.CONNECTIONTYPE_NORMAL;
    private boolean adminKeyUsed = false;

    protected ConnectionMetaDataImpl connectionMetaData = null;

    private boolean debug = Debug.debug;

    //package protect field, so that protocol handler can access it.
    Properties configuration = null;

    //ConnectionConsumer workaround for 4715054
    private boolean isDedicatedToConnectionConsumer = true;

    private volatile boolean negotiateProtocolLevel = false;
    private int brokerProtocolLevel = 0;
    private String brokerVersion = "Unknown";

    //ping interval -- default to 30 seconds.
    private long imqPingInterval = 30 * 1000;

    //event listener
    //private EventListener eventListener = null;

    //blocking wait timeout -- 30 secs.
    private static long WAIT_TIME_OUT = 30000;

    //event listener
    protected EventListener eventListener = null;

    protected EventHandler eventHandler = null;

    //HA variables.
    protected volatile boolean isConnectedToHABroker = false;

    protected String JMQClusterID = null;

    protected Long JMQStoreSession = null;

    protected String JMQBrokerList = null;

    protected String savedJMQBrokerList = null;

    protected String JMQStoreOwner = null;

    protected String lastContactedBrokerAddress = null;

    //if set to true, client runtime will notify the connection event listener
    //with all the MQ event available.  Such as broker address list changed.
    private boolean extendedEventNotification = false;

    private boolean _appTransactedAck = false;

    //root logging domain name
    public static final String ROOT_LOGGER_NAME = "javax.jms";

    //connection logging domain name.
    public static final String CONNECTION_LOGGER_NAME =
                               ROOT_LOGGER_NAME + ".connection";

    protected static final Logger connectionLogger =
        Logger.getLogger(CONNECTION_LOGGER_NAME,
                         ClientResources.CLIENT_RESOURCE_BUNDLE_NAME);
    
    /**
     * This flag is to over-ride imqReconnectEnabled flag.
     * 
     * For HA, client runtime will always fail-over to HA broker if connection is broken.
     * Th imqReconnectEnabled flag is ignored. 
     * 
     * This flag (private) is used to over-ride this behavior.
     * This flag is NOT static as it is chaged in individual instances
     */
    private boolean isHADisabled = Boolean.getBoolean("imq.ha.disabled");
    
    /**
     * called by protocol handler to check if JMQHAReconnect property should be set to true/false.
     * 
     * Default is set to true.
     * 
     * @return true if HA enabled, otherwise return false.
     */
    public boolean isHAEnabled() {
    	return (isHADisabled == false);
    }
    
    /**
     * @return true if connected to broker with pkt direct mode.
     */
    public boolean isDirectMode() {
    	return this.protocolHandler.isDirectMode();
    }
    
    //Bug6664278 -- JMQ connections won?t close after broker is bounced.
    //flag to turn off RA (from XAResorceImpl) re-open connection.
    private volatile boolean disableReopenFromRA = false;

    //private boolean isReconnectEnabledForRA = true;

    //This is the only constructor to be survived ...
    public
    ConnectionImpl(Properties configuration, String userName,
                   String password, String type) throws JMSException {

        this.configuration = configuration;
        this.userName = userName;
        this.password = password;
        if (ClientConstants.CONNECTIONTYPE_ADMINKEY.equals(type)) {
           this.connectionType = ClientConstants.CONNECTIONTYPE_ADMIN;
           adminKeyUsed = true;
           
        } else {
             //Only set if non-null; else default to initialized NORMAL
             if (type != null) {
            this.connectionType = type;
            }
        }
        if (this.connectionType == ClientConstants.CONNECTIONTYPE_ADMIN) {
            isHADisabled = true;
        }
        
        //obtain unique connectionID in this VM
        //this is for debugging purpose only
        connectionID = Long.valueOf(getNextConnectionID());
        localID = connectionID.longValue();
        
        //this over-ride auto detection feature for ra 
        //the information is logged in init method -- after "imqReconnect" flag is set.
        //if (isHADisabled) {
        //	this.configuration.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(false));
        //} else if (isHAEnabled) {
        //	this.configuration.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(true));
        //}

        init();
        
        //if ( isConnectedToHABroker && (imqReconnect == false) ) {
        //	if ( ClientConstants.CONNECTIONTYPE_ADMIN.equals(connectionType)==false) {
        //	String reconnectInfo = AdministeredObject.cr.getKString(ClientResources.I_MQ_AUTO_RECONNECT_IS_DISABLED, this.getLastContactedBrokerAddress());
        //	connectionLogger.log (Level.WARNING,  reconnectInfo);
        //	}
        //}

        logLifeCycle(ClientResources.I_CONNECTION_CREATED);
    }

    public long getLocalID() {
        return localID;
    }

    public String getBrokerVersion() {
        return brokerVersion;
    }
    
    protected void setBrokerSessionID(long id){
    	brokerSessionID = id;
    }
    
    protected long getBrokerSessionID(){
    	return brokerSessionID;
    }

    /**
     * Invoked from ReadChannel on HELLO_REPLY
     */
    public void setBrokerVersion(String brokerVersion) {
        if (debug) {
            Debug.println("setBrokerVersion : " +
                brokerVersion);
        }
        this.brokerVersion = brokerVersion;
    }

    public long generateUID()
    throws JMSException
    {
        return protocolHandler.generateUID();
    }

    protected int getBrokerProtocolLevel() {
        return brokerProtocolLevel;
    }

    /**
     * Invoked from ReadChannel on HELLO_REPLY
     */
    protected void setBrokerProtocolLevel(int brokerProtocolLevel) {
        if (debug) {
            Debug.println("setBrokerProtocolLevel : " +
                brokerProtocolLevel);
        }
        this.brokerProtocolLevel = brokerProtocolLevel;
    }

    protected boolean checkBrokerProtocolLevel() throws JMSException {
        //System.out.println("***** broker protocol level: " + brokerProtocolLevel);
        return (brokerProtocolLevel >= PacketType.VERSION2);
    }


     /**
      * check if the specified port number is valid.
      * @param host the host to connect to.
      * @param port the port number to connect to.
      * @throws JMSException if port number is 0.
      */
     public static void
     checkHostPort (String host, int port) throws JMSException {

        //check if broker is paused.
        if ( port <= 0 ) {
            String errorString0 = "[" + host + "," + port + "]";
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_BROKER_PAUSED, errorString0);

            JMSException jmse =
            new JMSException(errorString, AdministeredObject.cr.X_BROKER_PAUSED);

            ExceptionHandler.throwJMSException(jmse);
        }

     }

    public boolean getNegotiateProtocolLevel() {
        return negotiateProtocolLevel;
    }

    public void setNegotiateProtocolLevel(boolean negotiateProtocolLevel) {
        if (debug) {
            Debug.println("setNegotiateProtocolLevel : " +
                negotiateProtocolLevel);
        }
        this.negotiateProtocolLevel = negotiateProtocolLevel;
    }

    protected void updateLicenseProps () throws JMSException {
        licenseProps = null;
        failoverEnabled = false;

        //6165485 -- only get license if 3.6 or later.
        if (getBrokerProtocolLevel() > PacketType.VERSION350) {
            licenseProps = protocolHandler.getLicense();
        }

        if (licenseProps != null) {
            String fo = (String)licenseProps.get(ENABLE_FAILOVER_PROP);

            if (fo != null && "true".equalsIgnoreCase(fo)) {
                failoverEnabled = true;
            }

            //bug 6156985 -- we need to check if allow to failover.
            checkLicense();
        }
    }

    protected void hello () throws JMSException {
        protocolHandler.hello(userName, password);
        updateLicenseProps();
    }

    protected void hello(boolean reconnect) throws JMSException {
        protocolHandler.hello(userName, password, connectionID);
        updateLicenseProps();
    }

    private void checkLicense() throws JMSException {
        //app said we should reconnect
        if ( this.imqReconnect ) {
            //broker said that we cannot fail over
            if ( this.failoverEnabled == false ) {
                //we now check if there is more than one MQAddress in the
                //address list.  If true, we throw a JMSException.
                if ( this.initiator.getAddrListSize() > 1 ) {

                    String bname =
                    protocolHandler.getConnectionHandler().getBrokerHostName();

                    String errorString =
                    AdministeredObject.cr.getKString(AdministeredObject.cr.X_FAILOVER_NOT_SUPPORTED, bname);

                    JMSException jmse =
                    new JMSException(errorString, AdministeredObject.cr.X_FAILOVER_NOT_SUPPORTED);

                    ExceptionHandler.throwJMSException(jmse);
                }
            }
        }
    }
    
    /**
     * This method blocks (if root cause is IOException)
     *  until reconnected to a take over broker.
     * 
     * @param jmse
     * @return
     */
    protected boolean waitForReconnecting(Exception e)  {
    	
    	boolean isReconnected = false;
    	
    	/**
    	 * proceed only if auto reconnect is enabled. 
    	 */
    	if ( imqReconnect == false) {
    	        return false;
    	}
    	
    	if ( (e instanceof JMSException) == false ) {
    		return false;
    	}
    	
    	JMSException jmse = (JMSException) e;
    	
    	/**
    	 * wait for reconnecting only if IO errors.
    	 */
    	 String ecode = jmse.getErrorCode();
    	 
		if (ClientResources.X_NET_WRITE_PACKET.equals(ecode)
				|| ClientResources.X_NET_ACK.equals(ecode)) {

			SessionImpl.yield();

			try {
				checkReconnecting(null);

				if (isCloseCalled || connectionIsBroken) {
					isReconnected = false;
				} else {
					isReconnected = true;
				}

			} catch (Exception e2) {
				isReconnected = false;
			}
		}
         
         return isReconnected;
    }

    //bug 6189645 -- general blocking issues.
    protected void checkReconnecting(ReadWritePacket pkt) throws JMSException {
        checkReconnecting(pkt, true);
    }

    protected void checkReconnecting(ReadWritePacket pkt, boolean block)
    throws JMSException {

        synchronized (reconnectSyncObj) {

            while (reconnecting) {

                if (!block) {
                    throw new JMSException("XXXWOULD-BLOCK");
                }
                try {
                    reconnectSyncObj.wait( WAIT_TIME_OUT ); //wake up 30 secs
                }
                catch (Exception e) {
                    ;
                }

                //we want to exit if any of the following is true.
                if ( connectionIsBroken || isCloseCalled ) {
                    return;
                }

            }
        }

        //XXX HAWK: throw JMSException if the pkt contains session ID.
        //the session ID is invalidated because of fail over.
        if ( pkt != null ) {
            checkPacketType(pkt);
        }

    }

    private void checkPacketType (ReadWritePacket pkt) throws JMSException {

        boolean isAllowed = this.isAllowedToFailover(pkt);

        if ( isAllowed == false ) {
            String msg = AdministeredObject.cr.getKString(AdministeredObject.cr.X_NET_WRITE_PACKET) +
                         ", packet type = " + PacketType.getString( pkt.getPacketType() );

            JMSException jmse = new com.sun.messaging.jms.JMSException (
                                msg, AdministeredObject.cr.X_NET_WRITE_PACKET);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * Do not send pkt to fail-over broker if
     * 1. pkt header contains JMQSessionID.
     * 2. pkt is one of the pkt types as below.
     */
    private boolean isAllowedToFailover (ReadWritePacket pkt) {

        Hashtable ht = null;

        try {
            ht = pkt.getProperties();
        } catch (Exception e) {

            connectionLogger.log
                (Level.WARNING, ClientResources.X_PACKET_GET_PROPERTIES, e);
            //unexpected error. should never happen. do not continue.
            return false;
        }

        if ( ht != null ) {
            Object sid = ht.get("JMQSessionID");

            if ( sid != null ) {
                //not allowed if contains JMQSessionID.
                return false;
            }
        }

        int packetType = pkt.getPacketType();

        //do not send pkt to take-over broker for any of the pkt below.
        if (packetType == PacketType.ACKNOWLEDGE ||
            packetType == PacketType.ADD_CONSUMER ||
            packetType == PacketType.ADD_PRODUCER ||
            packetType == PacketType.AUTHENTICATE ||
            packetType == PacketType.CREATE_DESTINATION ||
            packetType == PacketType.CREATE_SESSION ||
            packetType == PacketType.DELETE_CONSUMER ||
            packetType == PacketType.DELETE_PRODUCER ||
            packetType == PacketType.DESTROY_DESTINATION ||
            packetType == PacketType.DESTROY_SESSION ||
            packetType == PacketType.PREPARE_TRANSACTION ||
            packetType == PacketType.SET_CLIENTID ||
            packetType == PacketType.START_TRANSACTION ||
            packetType == PacketType.ROLLBACK_TRANSACTION) {

            return false;
        }

        //do not send pkt for transacted messages.  The tid is no longer valid.
        if (packetType == PacketType.MESSAGE ||
            packetType == PacketType.BYTES_MESSAGE ||
            packetType == PacketType.MAP_MESSAGE ||
            packetType == PacketType.TEXT_MESSAGE ||
            packetType == PacketType.OBJECT_MESSAGE ||
            packetType == PacketType.STREAM_MESSAGE) {

            long tid = pkt.getTransactionID();
            if (tid > 0) {
               return false;
            }

        }

        //default to true
        return true;
    }

    public boolean getReconnecting() {
        synchronized (reconnectSyncObj) {
            return reconnecting;
        }
    }

    public void setReconnecting(boolean reconnecting) {
        synchronized (reconnectSyncObj) {
            this.reconnecting = reconnecting;
            reconnectSyncObj.notifyAll();
        }
    }

    /**
     * called by ReadChannel before peform recover.
     * @throws JMSException
     */
    protected void checkAndSetReconnecting() throws JMSException {
        /**
         * If this is true, we do nothing here.
         */
        synchronized (reconnectSyncObj) {
            if ( this.reconnecting ) {
                //throw new JMSException ("Cannot recover -- Recover in progress.");
            } else {
                //we only set this if the following conditions are met.
                if ( connectionIsBroken == false && isCloseCalled == false) {
                    this.setReconnecting(true);
                }
            }
        }
    }

    public boolean useNamespace() {
        synchronized (nsSyncObj) {
            return hasNamespace;
        }
    }

    public synchronized void setRANamespaceUID(String raNamespaceUID) {
        synchronized (nsSyncObj) {
            imqEnableSharedClientID = true;
            imqEnableSharedSubscriptions = true;
            this.raNamespaceUID = raNamespaceUID;
            hasNamespace = true;
        }
    }

    public String getRANamespaceUID() {
        synchronized (nsSyncObj) {
            return raNamespaceUID;
        }
    }

    /**
     * When connection recovery failed, this is called to ensure
     * the system quit gracefully.
     *
     * Errors will be handled by ReadChannel class.
     */
    //protected void abort(JMSException jmse) {
    //    readChannel.abort();
    //    protocolHandler.abort();

    //    if ( eventListener != null ) {
    //        triggerConnectionExitEvent(jmse);
    //    }
    //}


    /**
     * Get host and port of the broker to establish connection.
     * Construct an instance of InterestTable.
     * Construct an instance of ReadChannel.
     * Construct an instance of WriteChannel.
     * Construct an instance of sessionTable.
     *
     * @param args the parameter passed to the constructor.
     *
     * @exception JMSException  any internal errors caused by constructing the
     *                           above tables.
     */
    private void init() throws JMSException {
        try {
            //do this asap ...
            exceptionHandler = new ExceptionHandler();

            //interest table
            interestTable = new InterestTable();
            //table to hold session Qs
            readQTable = new ReadQTable();
            //table to store ack qs. -- protocol 2.1 change.
            ackQTable = new ReadQTable();

            requestMetaData = new Hashtable();

            //construct session table
            sessionTable = new Vector();
            connectionConsumerTable = new Vector();

            //construct temp dest table
            tempDestTable = new Vector();

            String prop = null;
            int propval = 0;
            long lpropval = 0L;

            /**
             * Check if admin has set the client ID for the user.
             */
            String cid = getTrimmedProperty ( ConnectionConfiguration.imqConfiguredClientID );

            if ( cid != null ) {
                clientID = cid;
                /**
                 * After init(), we send setClientID protocol to broker if
                 * this is set to true.
                 */
                adminSetClientID = true;
            } else {
                //bugID: 4630229.
                //clientID = InetAddress.getLocalHost().getHostAddress();
            }

            prop = getProperty("imq.DaemonThreads", "false");
            if (Boolean.valueOf(prop).booleanValue() == true) {
                daemonThreads = true;
            }
            
            // Bug6664278 -- JMQ connections won?t close after broker is
			// bounced.
			// flag to turn off RA (from XAResorceImpl) re-open connection.
			prop = getProperty("imq.disableReopenFramRA", "false");
			if (Boolean.valueOf(prop).booleanValue() == true) {
				disableReopenFromRA = true;
			}

            /**
			 * Check and initialize override properties for msg headers
			 */
            prop = getTrimmedProperty(ConnectionConfiguration.imqOverrideJMSDeliveryMode);
            if (Boolean.valueOf(prop).booleanValue() == true) {
                prop = getTrimmedProperty(ConnectionConfiguration.imqJMSDeliveryMode);
                if (ConnectionConfiguration.JMSDeliveryMode_PERSISTENT.equals(prop)) {
                    jmqJMSDeliveryMode = DeliveryMode.PERSISTENT;
                    jmqOverrideJMSDeliveryMode = true;
                }
                if (ConnectionConfiguration.JMSDeliveryMode_NON_PERSISTENT.equals(prop)) {
                    jmqJMSDeliveryMode = DeliveryMode.NON_PERSISTENT;
                    jmqOverrideJMSDeliveryMode = true;
                }
            }
            prop = getTrimmedProperty(ConnectionConfiguration.imqOverrideJMSExpiration);
            if (Boolean.valueOf(prop).booleanValue() == true) {
                prop = getTrimmedProperty(ConnectionConfiguration.imqJMSExpiration);
                lpropval = Long.parseLong(prop);
                if (lpropval >= 0L) {
                    jmqJMSExpiration = lpropval;
                    jmqOverrideJMSExpiration = true;
                }
            }
            prop = getTrimmedProperty(ConnectionConfiguration.imqOverrideJMSPriority);
            if (Boolean.valueOf(prop).booleanValue() == true) {
                prop = getTrimmedProperty(ConnectionConfiguration.imqJMSPriority);
                propval = Integer.parseInt(prop);
                if (propval >= 0 && propval <= 9) {
                    jmqJMSPriority = propval;
                    jmqOverrideJMSPriority = true;
                }
            }
            if (jmqOverrideJMSDeliveryMode || jmqOverrideJMSExpiration || jmqOverrideJMSPriority) {
                jmqOverrideJMSMsgHeaders = true;
            }
            prop = getTrimmedProperty(ConnectionConfiguration.imqOverrideJMSHeadersToTemporaryDestinations);
            jmqOverrideMsgsToTempDests = Boolean.valueOf(prop).booleanValue();

            /**
             * Check if the the admin disallow to set client id.
             */
            String disableSetID = getProperty(ConnectionConfiguration.imqDisableSetClientID);
            if ( Boolean.valueOf(disableSetID).booleanValue() == true ) {
                setClientIDFlag();
            }

            //protect mode
            prop = getProperty(ConnectionConfiguration.imqConnectionFlowLimitEnabled);
            if ( Boolean.valueOf(prop).booleanValue() == true ) {
                protectMode = true;
            }

            /** water mark - used in flow control
             * used only if protectMode == true
             */
            prop = getTrimmedProperty (ConnectionConfiguration.imqConnectionFlowLimit);
            if ( prop != null ) {
                flowControlWaterMark = Integer.parseInt(prop);
            }

            //flow control message size
            prop = getTrimmedProperty (ConnectionConfiguration.imqConnectionFlowCount);
            if ( prop != null ) {
                flowControlMsgSize = Integer.parseInt( prop );
            }

            //XXX PROTOCOL3.5
            // Consumer flow control attribute
            prop = getTrimmedProperty(ConnectionConfiguration.imqConsumerFlowLimit);
            if (prop != null) {
                prefetchMaxMsgCount = Integer.parseInt(prop);
            }

            //XXX PROTOCOL3.5
            // Consumer flow control attribute
            prop = getTrimmedProperty(ConnectionConfiguration.imqConsumerFlowThreshold);
            if (prop != null) {
                prefetchThresholdPercent = Integer.parseInt(prop);
            }

            //4.5
            prop = getProperty(ConnectionConfiguration.imqConsumerFlowLimitPrefetch, "true");
            consumerFlowLimitPrefetch = Boolean.valueOf(prop).booleanValue();
            if (!consumerFlowLimitPrefetch) {
                prefetchMaxMsgCount = 1;
                prefetchThresholdPercent = 0;
            }

            //5.0
            prop = getProperty(ConnectionConfiguration.imqOnMessageExceptionRedeliveryAttempts,
                               String.valueOf(onMessageExRedeliveryAttempts));
            if (prop != null) {
                int val = Integer.parseInt(prop);
                if (val < 1) {
                    val = 1;
                }
                onMessageExRedeliveryAttempts = val;
            }

            //5.0
            prop = getProperty(ConnectionConfiguration.imqOnMessageExceptionRedeliveryIntervals,
                               String.valueOf(onMessageExRedeliveryIntervals));
            if (prop != null) {
                long val = Long.parseLong(prop);
                if (val < 0) {
                    val = 0;
                }
                onMessageExRedeliveryIntervals = val;
            }

            prop = getTrimmedProperty(ConnectionConfiguration.imqReconnectEnabled);
            if (prop != null) {
                imqReconnect = Boolean.valueOf(prop).booleanValue();
            }
            
            prop = getTrimmedProperty(ConnectionConfiguration.imqEnableSharedClientID);
            if (prop != null) {
                imqEnableSharedClientID = Boolean.valueOf(prop).booleanValue();
            }

            ackOnProduce = getTrimmedProperty ( ConnectionConfiguration.imqAckOnProduce );
            ackOnAcknowledge = getTrimmedProperty ( ConnectionConfiguration.imqAckOnAcknowledge );

            String valstr = getTrimmedProperty ( 
                ConnectionConfiguration.imqAsyncSendCompletionWaitTimeout );
            if (valstr != null) {
                long val = Long.parseLong(valstr);
                if (val > 0L) {
                    asyncSendCompletionWaitTimeout = val;
                }
            }

            //dups ok limit
            String dupsOk = System.getProperty ("imqDupsOkLimit");
            if ( dupsOk != null ) {
                dupsOkLimit = Integer.parseInt( dupsOk );
            }

            //dups ok ack on timeout
            prop = System.getProperty ("imqDupsOkAckTimeout");
            if ( prop != null ) {
                dupsOkAckTimeout = Integer.parseInt( prop );
            }

            prop = getTrimmedProperty(ConnectionConfiguration.imqPortMapperSoTimeout);
            if (prop != null) {
                imqPortMapperSoTimeout = Integer.valueOf(prop);
                if (imqPortMapperSoTimeout.intValue() < 0 ) {
                    imqPortMapperSoTimeout = null;
                }
            }


            // Work out what socket timeout, in milliseconds, should be used when a TCP connection is made to the broker. 
            // this may be defined either using a system property imqSocketConnectTimeout 
            // or using a connection factory property imqSocketConnectTimeout
            // Since the connection factory property was added after the system property was added
            // in order to allow backwards compatibility, the system property  is always used if set
    		String tmpVal = System.getProperty("imqSocketConnectTimeout");
    		if (tmpVal!=null){
    			// set via system property
    			imqSocketConnectTimeout = Integer.parseInt(tmpVal);
    		} else {
    			// not set via system property
    			prop = getTrimmedProperty (ConnectionConfiguration.imqSocketConnectTimeout);
            	if ( prop != null ) {
            		// set via connection factory property
            		imqSocketConnectTimeout = Integer.parseInt( prop );
            	}
            }

            //dups ok ack on empty queue
            prop = System.getProperty ("imqDupsOkAckOnEmptyQueue");
            if ( prop != null ) {
                dupsOkAckOnEmptyQueue = Boolean.valueOf(prop).booleanValue();
            }

            //client Ack Limit
            String ackCount = System.getProperty ("imqAckLimit");
            if ( ackCount != null ) {
                ackLimit = Integer.parseInt( ackCount );
            }

            //if ack is limited, we check the ack count to make sure
            //unacked messages do not exceed the ack count limit
            //default is false
            String isLimited = System.getProperty("imqAckIsLimited");
            if ( isLimited != null ) {
                if ( isLimited.equals("true") ) {
                    isAckLimited = true;
                }
            }

            //ConnectionConsumer workaround 4715054
            String dedicateToCC = System.getProperty("imq.dedicateToConnectionConsumer");
            if (dedicateToCC != null) {
                if (dedicateToCC.equals("false")) {
                    isDedicatedToConnectionConsumer = false;
                }
            }

            String pInterval = getTrimmedProperty(ConnectionConfiguration.imqPingInterval);
            if ( pInterval != null ) {
                int tmp = Integer.parseInt(pInterval);

                if ( tmp <= 0 ) {
                    imqPingInterval = 0;
                } else {
                    imqPingInterval = tmp * 1000L;
                }
            }

            // XXX PROTOCOL2.1 --
            // Improved reconnect and connection failover.
            initiator = new ConnectionInitiator(this);

            // Let's start with an assumption that the broker supports
            // same protocol version. We will know more about it when
            // we get HELLO_REPLY.
            setBrokerProtocolLevel(PacketType.getProtocolVersion());

            //Open the connection in non-XA mode
            try {
                openConnection(false);
            }
            catch (Exception e) {
                if (negotiateProtocolLevel) {
                    //
                    // XXX PROTOCOL3.5 : Compatibility with old brokers...
                    // If the HELLO_REPLY contains -
                    //
                    //    JMQStatus = BAD_VERSION
                    //    JMQProtocolLevel = 200 (or any old protocol level)
                    //
                    // then try to open the connection again! In the
                    // second attempt, the protocol handler will
                    // automatically use a JMQProtocolLevel value that the
                    // broker can handle...
                    //
                    openConnection(false);
                }
                else {

                    if ( e instanceof JMSException ) {
                        throw e;
                    }

                    /**
                     * Connection creation exception SHOULD BE handled
                     * already.  This is the unexpected exception that
                     * we just wanted to propagate to the client.
                     */
                    exceptionHandler.handleException(
                           e, AdministeredObject.cr.X_CAUGHT_EXCEPTION, true);
                }
            }
            
        } catch (JMSException jmse) {
            //if this is a jms exception, we simply propagate up to the application.
            throw jmse;
        } catch (Exception e) {
            /**
             * Connection creation exception SHOULD BE handled already.
             * This is the unexpected exception that we just wanted to
             * propagate to the client.
             */
            exceptionHandler.handleException(
                   e, AdministeredObject.cr.X_CAUGHT_EXCEPTION, true);
        }

    }

    /**
     * Get the next available connection ID. This only blocks another
     * connection construction.  No other objects sync methods will
     * be blocked.
     */
    private static synchronized long getNextConnectionID() {
        return nextConnectionID ++;
    }

    /**
     * Get the current connection's ID.
     */
    public Long getConnectionID() {
        return connectionID;
    }

    public Long _getConnectionID() {
        return connectionID;
    }

    /**
     * Set connection id.  This is set by ReadChannel assigned by broker.
     */
    protected void setConnectionID (Long id) {
        connectionID = id;
    }

    protected void setAuthenticationHandler(AuthenticationProtocolHandler authHdr) {
        authenticationHandler = authHdr;
    }

    protected AuthenticationProtocolHandler getAuthenticationHandler() {
        return authenticationHandler;
    }

    /**
     * Called by Temporary Destination constructors.  This is part of the temp
     * destination name.
     * protocol://clientID/localPort/sequence
     */
    protected int getTempDestSequence() {
        synchronized ( syncObj ) {
            tempDestCounter ++;
            return (++ tempDestSequence);
        }
    }

    /**
     * decrease Temporary Destination Counter.  Called by
     * TemporaryTopic.delete() or TemporaryQueue.delete()
     *
     * <p>tempDestCounter is increased during construction.  The
     * counter is increased in getTempDestSequence() method.
     */
    protected void decreaseTempDestCounter() {
        synchronized ( syncObj ) {
            tempDestCounter --;
        }
    }
    /**
     * Temporary destination count in this connection.
     */
    protected int getTempDestCounter() {
        synchronized ( syncObj ) {
            return tempDestCounter;
        }
    }

    //protected int getMaxQueueSize() {
    //    return maxQueueSize;
    //}

    //protected int getMinQueueSize() {
    //    return minQueueSize;
    //}

    protected void setProtectMode (boolean mode) {
        protectMode = mode;
    }

    protected boolean getProtectMode() {
        return protectMode;
    }

    protected int getDupsOkLimit() {
        return dupsOkLimit;
    }

    protected int getAckLimit() {
        return ackLimit;
    }

    protected boolean getIsAckLimited() {
        return isAckLimited;
    }

    protected boolean getIsDedicatedToConnectionConsumer() {
        return (isDedicatedToConnectionConsumer && !connectionConsumerTable.isEmpty());
    }

    //protected boolean getCreateProducerChk() {
    //    return createProducerChk;
    //}

    //protected boolean getAutoCreateDestination() {
    //    return autoCreateDestination;
    //}

    /**
     * Get next session ID.  When a session is created, the number is increased
     * by 1.
     *
     * @return  the next available session ID.
     */
    protected Long getNextSessionId() {
        synchronized ( syncObj ) {
            nextSessionId ++;

            //check if it has reached max value
            if (nextSessionId == Long.MAX_VALUE) {
                nextSessionId = 1;
                sessionIdReset = true;
            }

            //if it has reached to the limit at least once.
            if ( sessionIdReset == true ) {
                boolean found = false;
                while ( !found ) {
                    //check if still in use
                    Object key = readQTable.get ( Long.valueOf(nextSessionId) );
                    if ( key == null ) {
                        //not in use
                        found = true;
                    } else {
                        //increase one and keep trying
                        nextSessionId ++;
                        //still need to check the limit
                        if (nextSessionId == Long.MAX_VALUE) {
                            nextSessionId = 1;
                        }
                    }
                }
            }

            return Long.valueOf(nextSessionId);
        } //syncObj


    }

    /**
     * Get next available transaction ID
     */
    protected int getNextTransactionID() throws JMSException {

        synchronized ( syncObj ) {

            nextTransactionID ++;

            if ( nextTransactionID == Integer.MAX_VALUE ) {
                nextTransactionID = 1;
            }

            return nextTransactionID;
        }


    }

    protected void
    addToReadQTable (Object sid, Object readQueue) {
        readQTable.put (sid, readQueue);
    }

    protected void
    removeFromReadQTable (Object sid) {
        readQTable.remove(sid);
    }

    /**
     * Protocol 2.1 change.  New table for ack queues.
     */
    protected void
    addToAckQTable (Object aid, Object readQueue) {
        ackQTable.put (aid, readQueue);
    }

    /**
     * Protocol 2.1 change.  New table for ack queues.
     */
    protected void
    removeFromAckQTable (Object aid) {
        ackQTable.remove(aid);
    }

    /**
     * Add a producer to the connections producer table.
     *
     * XXX PROTOCOL3.5
     * Producer flow control : The connection's producer table is used
     * when ReadChannel receives a RESUME_FLOW message and needs to
     * lookup the MessageProducerImpl object quickly.
     */
    protected void
    addMessageProducer (Object producerIDKey, MessageProducerImpl producer) {
        Object o = producers.put(producerIDKey, producer);

        if (debug && o != null) {
            Debug.println(
    "ERROR : Duplicate ProducerID in connection.addMessageProducer : " +
                producerIDKey);
        }
    }

    /**
     * Remove a producer from the connection's producer table.
     */
    protected void
    removeMessageProducer (Object producerIDKey) {
        Object o = producers.remove(producerIDKey);
        if (debug && o == null) {
            Debug.println(
    "ERROR : Unknown producer in connection.removeMessageProducer : " +
                producerIDKey);
        }
    }

    protected MessageProducerImpl findMessageProducer(Object producerIDKey) {
        return (MessageProducerImpl) producers.get(producerIDKey);
    }

    /**
     * add a cosumer interest to local interest table
     *
     * @param consumer the consumer whos interest to be added
     */
    //XXX PROTOCOL2.1 --
    // Interest is added by ReadChannel when received
    //a reply from broker for a consumer ID.
    protected void
    addLocalInterest(Consumer consumer) {
        //interestTable.addInterest(consumer);
    }

    /**
     * remove a consumer interest from local interest table
     *
     * @param consumer the consumer whos interest to be removed
     */
    protected void
    removeLocalInterest(Consumer consumer) {
        interestTable.removeInterest(consumer);
    }

    /**
     * add a consumer interest to local interest table and register to broker
     *
     * @param consumer the consumer whos interest to be added
     *
     * @exception JMSException if fails to register to broker
     */
    protected void
    addInterest (Consumer consumer ) throws JMSException {
        addLocalInterest (consumer);
        //XXX REVISIT cleanup from interestTable if following fails
        writeChannel.addInterest (consumer); // register to router
    }

    /**
     * remove a consumer interest from local interest table and
     * deregister from broker
     *
     * @param consumer the consumer whos interest to be removed
     * @param destroy if true deregister the interest from broker
     *                if false do not deregister from broker (for durable)
     *
     * @exception JMSException if fails to deregister from broker
     */
    protected void
    removeInterest(Consumer consumer) throws JMSException {
        writeChannel.removeInterest (consumer); //deregister from router
        removeLocalInterest (consumer);
    }


    /**
     * deregister a durable interest from broker
     *
     * @param durableName the name used to identify this durable interest
     *
     * @exception JMSException if broker fails to deregister the interset
     */
    protected void
    unsubscribe(String durableName) throws JMSException {
        // broker also checks durable inuse on DELETE_CONSUMER
        Enumeration enum2 = connectionConsumerTable.elements();
        ConnectionConsumerImpl connConsumer = null;
        while (enum2.hasMoreElements()) {
            connConsumer = (ConnectionConsumerImpl)enum2.nextElement();
            if (connConsumer.getDurable()) {
                if (connConsumer.getDurableName().equals(durableName)) {
                    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_DURABLE_INUSE, durableName);

                    JMSException jmse =
                    new JMSException(errorString, AdministeredObject.cr.X_DURABLE_INUSE);

                    ExceptionHandler.throwJMSException(jmse);
                }
            }
        }
        writeChannel.unsubscribe (durableName);
    }

    /**
     * get next interest Id
     *
     * @return the next interest Id number
     */
    //XXX PROTOCOL2.1 -- to be removed.
    /*protected Long
    getNextInterestId() {
        return interestTable.getNextInterestId();
    }*/

    /**
     * Get the protocol handler for this connection.
     *
     * @return  the protocol handler.
     */
    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    /**
     * Get the write channel for the current connection.
     *
     * @return  the write channel.
     */
    protected WriteChannel
    getWriteChannel() {
        return writeChannel;
    }

    /**
     * Returns the type of this connection
     *
     * @return The type of this connection. This is currently
     *         either <code>NORMAL</code> or <code>ADMIN</code>
     */
    public String getConnectionType() {
        return connectionType;
    }

    protected boolean isAdminKeyUsed() {
        return adminKeyUsed;
    }

    /**
     * Private contract with embeded stomp bridge
     *
     * When set, client runtime auto txn ack will be disabled
     * and all txn ack will be driven by application calling
     * SessionImpl.appTransactedAck(Message) directly
     */
    public void _setAppTransactedAck() {
        _appTransactedAck = true;
        imqReconnect = false;
    }

    protected boolean isAppTransactedAck() {
        return _appTransactedAck;
    }

    /**
     * Returns a clone of the configuration
     */
    protected Properties getConfiguration() {
        return (Properties)configuration.clone();
    }
    /**
     * Returns a configuration property
     *
     * @return The property value of the property key <code>propname</code>.
     */
    public String getProperty(String propname) {
        String propval = (String)configuration.get(propname);

        if ( debug ) {
            Debug.println ("****** property " + propname + " : " + propval);
        }

        return propval;
    }

    /**
     * Property need to be trimed should call this method. If the property
     * value is empty, null is returned.
     *
     * @return The property value of the property key <code>propname</code>.
     */
    public String getTrimmedProperty (String propName) {

        String prop = getProperty ( propName );
        if ( prop != null ) {
            if ( prop.trim().length() == 0 ) {
                prop = null;
            }
        }

        return prop;
    }

    /**
     * Returns a configuration property.
     * Uses a System property if non-existant and a default if
     * the System property doesn't exist.
     *
     * @param propname The key with which to retreive the property value.
     * @param propdefault The default value to be returned.
     *
     * @return The property value of the property key <code>propname</code>
     *         If the key <code>propname</code> does not exist, then if a System
     *         property named <code>propname</code> exists, return that, otherwise
     *         return the value <code>propdefault</code>.
     */
    public String getProperty(String propname, String propdefault) {
        String propval = getProperty(propname);
        if (propval == null) {
            propval = System.getProperty(propname) ;
        }
        return (propval == null ? propdefault : propval);
    }

    /**
     * Get the read channel for the currect connection.
     *
     * @return  the read channel.
     */

    public ReadChannel
    getReadChannel() {
        return readChannel;
    }

    /**
     * Set protocol handler for the current connection.
     *
     * @param handler the protocol handler to be used by this connection.
     */
    protected void
    setProtocolHandler ( ProtocolHandler handler ) {
        this.protocolHandler = handler;
    }

    /**
     * Get domain type of this connection.
     *
     * @return  true if this is a topic connection.
     */
    protected boolean
    getIsTopicConnection() {
        return isTopicConnection;
    }

    /**
     * Set the domain type of this connection.
     *
     * @param isTopic set to true if it is topic connection.
     */
    protected void
    setIsTopicConnection (boolean isTopic) {
        isTopicConnection = isTopic;
    }

    /**
     * Get if this is a queue domain.
     * @return true if this is a queue domain.
     * bug 6172663
     */
    protected boolean getISQueueConnection() {
        return this.isQueueConnection;
    }

    /**
     * set if this is a queue connection domain
     * @param isQueue set to true if this is a queue domain.
     * bug 6172663
     */
    protected void setIsQueueConnection (boolean isQueue) {
        this.isQueueConnection = isQueue;
    }

    /**
     * Get the interest table for this connection.
     *
     * @return  the interest table for this connection.
     */
    protected InterestTable
    getInterestTable() {
        return interestTable;
    }

    /**
     * Add a session to the session table.  When a new session is created,
     * the session is added to the table.
     *
     * @param session the session to be added to the session table.
     */
    protected void
    addSession (SessionImpl session) {
        sessionTable.addElement( session );
    }

    /**
     * Remove the session from the session table.
     *
     * When a session is closed, it is removed from the session table.
     *
     * @param session the session to be removed.
     */
    protected boolean
    removeSession (SessionImpl session) {
        return sessionTable.removeElement( session );
    }

    /**
     * Add a connection consumer to the connection consumer table.
     * Called when a new connection consumer is created.
     *
     * @param connectionConsumer the connection consumer to be added
     */
    protected /*synchronized*/ void
    addConnectionConsumer(ConnectionConsumerImpl connectionConsumer) {
        connectionConsumerTable.addElement(connectionConsumer);
    }

    /**
     * Remove the connection consumer from the connection consumer table.
     * Called when a connection consumer is closed.
     *
     * @param connectionConsumer the connection consumer to be removed.
     */
    protected /*synchronized*/ void
    removeConnectionConsumer(ConnectionConsumerImpl connectionConsumer) {
        connectionConsumerTable.removeElement(connectionConsumer);
    }

    /**
    * Add a Temp Dest to the Temp Dest table
    */
    protected void
    addTempDest(TemporaryDestination tempDest)
    {
        tempDestTable.addElement(tempDest);
    }

    /**
    * Remove a Temp Dest from the Temp Dest table
    */
    protected void
    removeTempDest(TemporaryDestination tempDest)
    {
        tempDestTable.removeElement(tempDest);
    }

    protected void startSessions() throws JMSException {
        Enumeration enum2 = sessionTable.elements();
        SessionImpl session = null;

        //start all sessions.  all session queues are unlocked.
        while ( enum2.hasMoreElements() ) {
            session = (SessionImpl) enum2.nextElement();

            if ( debug ) {
                Debug.println ("starting session: " + session.getSessionId().longValue());
            }

            session.start();
        }
    }

    protected void stopSessions() throws JMSException {
        Enumeration enum2 = sessionTable.elements();
        SessionImpl session = null;

        //stop all sessions.  all session readers are locked in the session queues
        while ( enum2.hasMoreElements() ) {
            session = (SessionImpl) enum2.nextElement();

            if ( debug ) {
                Debug.println ("stopping session: " + session.getSessionId().longValue());
            }

            session.stop();
        }
    }

    /**
     * Start all connection consumers. Called from Connection.start()
     */
    private void startConnectionConsumers() {
        Enumeration enum2 = connectionConsumerTable.elements();
        ConnectionConsumerImpl connectionConsumer = null;

        while ( enum2.hasMoreElements() ) {
            connectionConsumer = (ConnectionConsumerImpl)enum2.nextElement();
            if ( debug ) {
                Debug.println ("starting connectionConsumer: "
                        + connectionConsumer.getReadQueueId().intValue());
            }
            connectionConsumer.start();
        }
    }

    /**
     * Stop all connection consumers. Called from Connection.stop()
     */
    private void stopConnectionConsumers() {
        Enumeration enum2 = connectionConsumerTable.elements();
        ConnectionConsumerImpl connectionConsumer = null;

        while ( enum2.hasMoreElements() ) {
            connectionConsumer = (ConnectionConsumerImpl)enum2.nextElement();
            if ( debug ) {
                Debug.println ("stopping connectionConsumer: "
                            + connectionConsumer.getReadQueueId().intValue());
            }
            connectionConsumer.stop();
        }
    }

    /**
     * Close all connection consumers. Called from Connection.close()
     *
     * @exception JMSException if close a connection consumer fails
     */
    private void closeConnectionConsumers() throws JMSException {
        ConnectionConsumerImpl connectionConsumer = null;

        try {
            while ( connectionConsumerTable.isEmpty() == false ) {
                connectionConsumer = (ConnectionConsumerImpl)connectionConsumerTable.firstElement();
                connectionConsumer.close();
                connectionConsumerTable.remove(connectionConsumer);
            }
        } catch (Exception e) {
            if (debug) {
                Debug.printStackTrace(e);
            }
        }
    }

    protected synchronized void suspendMessageDelivery() throws JMSException {

        if ( getIsSuspended() ) {
            return;
        }

        if ( debug ) {
            Debug.println ("sending STOP to broker ...");
        }

        protocolHandler.stop();
        isSuspended = true;
    }

    protected synchronized void resumeMessageDelivery() throws JMSException {

        if ( debug ) {
            Debug.println ("sending START to broker ...");
        }

        protocolHandler.start();
        isSuspended = false;
    }

    public boolean getIsSuspended() {
        return isSuspended;
    }

    /**
     * The state of the connection.
     *
     * @return true if the connection is stopped.  Otherwise, return false.
     */
     public boolean getIsStopped() {
        return isStopped;
     }

     //public synchronized void setIsStopped (boolean state) {
     //   isStopped = state;
     //}

    /** Get the client identifier for this connection.
      *
      * This value is JMS Provider specific.
      * Either pre-configured by an administrator in a ConnectionFactory
      * or assigned dynamically by the application by calling
      * <code>setClientID</code> method.
      *
      *
      * @return the unique client identifier.
      *
      * @exception JMSException if JMS implementation fails to return
      *                         the client ID for this Connection due
      *                         to some internal error.
      *
      **/
    public String
    getClientID() throws JMSException {
        checkConnectionState();

        /**
         * This is to fix cts bug
         * test.jmsclient.cts.ee.appclient.queueconn.QueueConnTests.changeClientIDQueueTest().
         * The default value will be returned after allowToSetClientID is set
         * to false.
        if ( allowToSetClientID ) {
            return null;
        }
         This is being removed to fix the requirement that clientID returned is corrcet
         right after connection creation - even if it was set via configuration
        */
        return clientID;
    }


    /** Set the client identifier for this connection.
      *
      * <P>The preferred way to assign a Client's client identifier is for
      * it to be configured in a client-specific ConnectionFactory and
      * transparently assigned to the Connection it creates.
      *
      * <P>Alternatively, a client can set a connection's client identifier
      * using a provider-specific value. The facility to explicitly set a
      * connection's client identifier is not a mechanism for overriding the
      * identifier that has been administratively configured. It is provided
      * for the case where no administratively specified identifier exists.
      * If one does exist, an attempt to change it by setting it must throw a
      * IllegalStateException. If a client explicitly does the set it must do
      * this immediately after creating the connection and before any other
      * action on the connection is taken. After this point, setting the
      * client identifier is a programming error that should throw an
      * IllegalStateException.
      *
      * <P>The purpose of client identifier is to associate a connection and
      * its objects with a state maintained on behalf of the client by a
      * provider. The only such state identified by JMS is that required
      * to support durable subscriptions
      *
      *
      * <P>If another connection with <code>clientID</code> is already running when
      * this method is called, the JMS Provider should detect the duplicate id and throw
      * InvalidClientIDException.
      *
      * @param clientID the unique client identifier
      *
      * @exception JMSException general exception if JMS implementation fails to
      *                         set the client ID for this Connection due
      *                         to some internal error.
      *
      * @exception InvalidClientIDException if JMS client specifies an
      *                         invalid or duplicate client id.
      * @exception IllegalStateException if attempting to set
      *       a connection's client identifier at the wrong time or
      *       when it has been administratively configured.
      */

    public void
    setClientID(String clientID) throws JMSException {
        checkConnectionState();

        //local check if permission to set client ID
        checkSetClientID(clientID);
        //check with broker if this is a valid client ID
        protocolHandler.setClientID(clientID);
        this.clientID = clientID;
        //XXX REVISIT chiaming: should we allow to set client ID twice?
        setClientIDFlag();
    }

    /** Get the meta data for this connection.
      *
      * @return the connection meta data.
      *
      * @exception JMSException general exception if JMS implementation fails to
      *                         get the Connection meta-data for this Connection.
      *
      * @see javax.jms.ConnectionMetaData
      */

    public ConnectionMetaData
    getMetaData() throws JMSException {
        checkConnectionState();
        return connectionMetaData;
    }

    /**
     * Get the ExceptionListener for this Connection.
     *
     * @return the ExceptionListener for this Connection.
     *
     * @exception JMSException general exception if JMS implementation fails to
     *                         get the Exception listener for this Connection.
     */

    public ExceptionListener
    getExceptionListener() throws JMSException {
        checkConnectionState();
        return exceptionListener;
    }

    /** Set an exception listener for this connection.
      *
      * <P>If a JMS provider detects a serious problem with a connection it
      * will inform the connection's ExceptionListener if one has been
      * registered. It does this by calling the listener's onException()
      * method passing it a JMSException describing the problem.
      *
      * <P>This allows a client to be asynchronously notified of a problem.
      * Some connections only consume messages so they would have no other
      * way to learn their connection has failed.
      *
      * <P>A Connection serializes execution of its ExceptionListener.
      *
      * <P>A JMS provider should attempt to resolve connection problems
      * itself prior to notifying the client of them.
      *
      * @param handler the exception listener.
      *
      * @exception JMSException general exception if JMS implementation fails to
      *                         set the Exception listener for this Connection.
      */

    public void
    setExceptionListener(ExceptionListener listener) throws JMSException {

        checkConnectionState();

        exceptionListener = listener;
        setClientIDFlag();
        //exceptionHandler.setExceptionListener(listener);
    }

    /** Start (or restart) a Connection's delivery of incoming messages.
      * Starting a started session is ignored.
      *
      * @exception JMSException if JMS implementation fails to start the
      *                         message delivery due to some internal error.
      *
      * @see javax.jms.Connection#stop
      */

    public void
    start() throws JMSException {

        checkConnectionState();

        if ( isStopped == false ) {
            return;
        }

        setClientIDFlag();

        synchronized ( this ) {
            protocolHandler.start();
            isStopped = false;
            startSessions();
            startConnectionConsumers();
        }
    }


    /** Used to temporarily stop a Connection's delivery of incoming messages.
      * It can be restarted using its <CODE>start</CODE> method. When stopped,
      * delivery to all the Connection's message consumers is inhibited:
      * synchronous receive's block and messages are not delivered to message
      * listeners.
      *
      * <P>This call blocks until receives and/or message listeners in progress
      * have completed.
      *
      * <P>Stopping a Session has no affect on its ability to send messages.
      * Stopping a stopped session is ignored.
      *
      * <P>A stop method call must not return until delivery of messages has
      * paused. This means a client can rely on the fact that none of its
      * message listeners will be called and all threads of control waiting
      * for receive to return will not return with a message until the
      * connection is restarted. The receive timers for a stopped connection
      * continue to advance so receives may time out while the connection is
      * stopped.
      *
      * <P>If MessageListeners are running when stop is invoked, stop must
      * wait until all of them have returned before it may return. While these
      * MessageListeners are completing, they must have full services of the
      * connection available to them.
      *
      * @exception JMSException if JMS implementation fails to stop the
      *                         message delivery due to some internal error.
      *
      * @see javax.jms.Connection#start
      */

    public void
    stop() throws JMSException {

        checkConnectionState();
        if ( isStopped || isClosed ) {
            return;
        }

        //when connection is broken, simply return
        //the clean up work will be done by ReadChannel
        if ( connectionIsBroken ) {
            exitConnection();
            return;
        }
        checkPermission(false);

        setClientIDFlag();

        if ( debug ) {
            Debug.println ("stopping readChannel ...");
        }

        synchronized ( this ) {
            protocolHandler.stop();
            stopSessions();
            stopConnectionConsumers();

            isStopped = true;
        }
    }


    /** Since a provider typically allocates significant resources outside
      * the JVM on behalf of a Connection, clients should close them when
      * they are not needed. Relying on garbage collection to eventually
      * reclaim these resources may not be timely enough.
      *
      * <P>There is no need to close the sessions, producers, and consumers
      * of a closed connection.
      *
      * <P>When this method is invoked it should not return until message
      * processing has been orderly shut down. This means that all message
      * listeners that may have been running have returned and that all pending
      * receives have returned. A close terminates all pending message receives
      * on the connection's sessions' consumers. The receives may return with a
      * message or null depending on whether there was a message or not available
      * at the time of the close. If one or more of the connection's sessions'
      * message listeners is processing a message at the point connection
      * close is invoked, all the facilities of the connection and it's sessions
      * must remain available to those listeners until they return control to the
      * JMS provider.
      *
      * <P>Closing a connection causes any of its sessions' in-process
      * transactions to be rolled back. In the case where a session's
      * work is coordinated by an external transaction manager, when
      * using XASession, a session's commit and rollback methods are
      * not used and the result of a closed session's work is determined
      * later by a transaction manager.
      *
      * Closing a connection does NOT force an
      * acknowledge of client acknowledged sessions.
      *
      * <P>Invoking the session acknowledge method on a closed connection's
      * session must throw a JMSException. Closing a closed connection must
      * NOT throw an exception.
      *
      * @exception JMSException if JMS implementation fails to close the
      *                         connection due to internal error. For
      *                         example, a failure to release resources
      *                         or to close socket connection can lead
      *                         to throwing of this exception.
      *
      */

    public void
    close() throws JMSException {

        //this flag is used in checkReconnecting() method.  The method checks
        //if this flag is set when timeout.
        this.isCloseCalled = true;

        /**
         * We want to stop event delivery asap.
         */
        if ( eventHandler != null ) {
           eventHandler.close();
        }

        //This is to fullfil the above requirement:
        //Closing a closed connection must NOT throw an exception.
        //when a connection is created, the protocol handler is constructed.
        //when it is closed, it is null.
        if ( isClosed ) {
            return;
        }

        //isClosing = true;

        if ( connectionIsBroken || recoverInProcess ) {
            exitConnection();
            return;
        }

        //check if this call is allowed.
        checkPermission(true);

        synchronized ( this ) {

            /**
             * This makes sure no close calls pass here twice, which
             * caused NULLPointer Exception.
             *
             * We may be able to optimize how close is handled in the future.
             */
            if ( isClosed ) {
                return;
            }

            try {
                //GT
                //So that this connection can never be started
                //by a recover/rollback that is in process.
                protocolHandler.incStoppedCount();

                //stop message delivery, all sessions are stopped.
                //when this returns, we are gauranteed no messages
                //will be delivered.
                stop();
                //close all sessions in this connection
                closeAllSessions();
                //sessionTable.removeAllElements();
                closeConnectionConsumers();

                /**
                 * Connection could be closed after sending GOOD_BYE packet.
                 * Broker could close connection after received GOOD_BYE
                 * and replied with GOODBYE_REPLY.
                 *
                 */
                isClosed = true;
                protocolHandler.goodBye(true);

            } catch (JMSException e) {

                if ( connectionIsBroken || recoverInProcess || isClosed ) {
                    exitConnection();
                } else {
                    throw e;
                }
            } finally {
                /**
                 * do final clean up ....
                 */
                try {
                    //exit read channel thread.
                    readChannel.close();
                    //exit write channel
                    writeChannel.close();
                    //close socket.
                    protocolHandler.close();

                } catch (Exception ex) {
                    if ( debug ) {
                        Debug.printStackTrace( ex );
                    }
                }

                protocolHandler = null;
                readChannel = null;
                writeChannel = null;
                authenticationHandler = null;

                //closed by application
                this.logLifeCycle(ClientResources.I_CONNECTION_CLOSED);

            } //finally
        } //synchronized
    } //close

    /**
     * Get the connection's exception handler
     */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * check permission for setting client ID
     * @see ConnectionImpl#setClientID
     */

    protected void checkSetClientID(String cid) throws JMSException {

        if ( allowToSetClientID == false ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SET_CLIENT_ID);
            JMSException jmse = new javax.jms.IllegalStateException
                      (errorString, AdministeredObject.cr.X_SET_CLIENT_ID);

            ExceptionHandler.throwJMSException(jmse);
        }

        if ( cid == null || (cid.trim().length() == 0) ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_CLIENT_ID, "\"\"");
            JMSException jmse = new javax.jms.InvalidClientIDException
                      (errorString, AdministeredObject.cr.X_INVALID_CLIENT_ID);
            ExceptionHandler.throwJMSException(jmse);
        }

    }

    /**
     * setClientIDFlag
     */
    protected void setClientIDFlag() throws JMSException {
        allowToSetClientID = false;
    }

    protected void closeAllSessions() {

        connectionLogger.log(Level.FINEST, "closing all sessions ...");

        com.sun.messaging.jms.IllegalStateException ex = null;

        //close all sessions in this connection
        SessionImpl session = null;
        try {
            while ( sessionTable.size() > 0 ) {
                session = (SessionImpl) sessionTable.firstElement();
                this.closeSession(session);
            }
        } finally {
            connectionLogger.log(Level.FINEST, "all sessions closed ...");
        }
    }
    
    private void closeSession (SessionImpl session) { 
        try {
            session.close();
        } catch (Exception e) {
            ExceptionHandler.rootLogger.log(Level.WARNING, e.getMessage(), e);
    	} finally {
            sessionTable.remove(session);
    	}
    }
    
    protected void closeConsumerQueues() {
    	Object tmp[] = interestTable.toArray();
    	
    	for (int i=0; i<tmp.length; i++) {
    		if (tmp[i] instanceof MessageConsumerImpl) {
    			((MessageConsumerImpl)tmp[i]).receiveQueue.close();
    			((MessageConsumerImpl)tmp[i]).isClosed = true;
    			
    			connectionLogger.log(Level.FINEST, "Message consumer closed: " + tmp[i]);
    		}
    	}
    }

    /**
     * This is called by ReadChannel when connection is broken.
     * After called the exception listener and the exception listener
     * has returned, this method is called to ensure the client may
     * exit gracefully.
     */
    protected void exitConnection() {
    	
    	connectionLogger.log(Level.FINEST, "Starting to exit connection ...");   	
    	
        //if closed, it has been clean up
        if ( isClosed ) {
            return;
        }
        
        try {
            //close sessions
            closeAllSessions();
            
            //close consumers in the interest table. 
            closeConsumerQueues();
            
            closeConnectionConsumers();

            // Wakeup blocked producers..
            writeChannel.close();

        } catch (Exception e) {
            ExceptionHandler.rootLogger.log(Level.WARNING, e.getMessage(), e);   
        } finally {

            //set flag so that this will not be called again
            isClosed = true;

            setReconnecting(false);
        }
    }

    /**
     * This closes a connection if it has been opened from an XAResource
     * Bug6664278 -- JMQ connections won?t close after broker is bounced.
     * must synchronized.
     */
    protected void closeConnectionFromRA() throws JMSException {
        
        synchronized (this.syncObj) {
            //If this was opened from an XAResource, close it
            if (openedFromXA) {
                //This will close the physical connection
                close();
            }
        }
    }
    
    /**
     * bug 6664278 - concurrent opening the connection caused corruption.
     * this also could cause bug 6664280.
     * 
     * @param mode
     * @throws javax.jms.JMSException
     */
    protected void openConnectionFromRA (boolean mode) throws JMSException {
        
        //cannot re-open if connection is broken or re-connecting.
        if (connectionIsBroken || reconnecting) {
            return;
        }
        
        //turn off re-open from RA.
        if (disableReopenFromRA) {
            return;
        }
        
        //must sync the call.
        synchronized (syncObj) {
            this.openConnection(mode);
        }
    }

    /**
     * This opens a connection if it has been closed
     * - used by init() as well as XAResource
     * >>Depends on init() being performed
     *
     *   This can be called by the XAResource (from TM)
     *   long after the connection has been
     *   closed by the API user
     *   
     *   Bug 6664278 -- changed from protected to private
     *   so that this cannot be called outside of this class.
     *   RA must call a different method (openConnectionFromRA).
     *   
     *   @param mode true if opening from an XAResource
     */
    private void openConnection(boolean mode) throws JMSException {
        //Perform only if this is truely closed
        if ( isClosed() == false ) {
            return;
        }
        //setup connection meta data
        //NOTE: connectionMetaData must be instantiated before
        //protocolhandler
        connectionMetaData = new ConnectionMetaDataImpl(this);
        protocolHandler = new ProtocolHandler(this);

        if (ackOnProduce != null ) {
            if (ackOnProduce.equals("true") ) {
                protocolHandler.enableWriteAcknowledge(true);
            } else if (ackOnProduce.equals("false") ){
                    protocolHandler.enableWriteAcknowledge(false);
            }
        }

        if (ackOnAcknowledge != null ) {
            if (ackOnAcknowledge.equals("false") ){
                protocolHandler.setAckAck(false);
            }
        }

        connectionIsBroken = false;
        recoverInProcess = false;

        //start read channel
        readChannel = new ReadChannel (this);
        //construct write channel
        writeChannel = new WriteChannel(this);

        //you have to define the name of the super class ...
        //for example, java -Ddebug=true -DTopicConnectionImpl TestClass
        if ( debug ) {
                Debug.println(this);
        }
        try {
            hello();
            /**
             * Check with broker if this is a valid client ID.
             * This is concidered as part of the connection hand
             * shaking if admin has set the client ID for this user.
             */
            if ( adminSetClientID ) {
                protocolHandler.setClientID(clientID);
                setClientIDFlag();
            }
        } catch (JMSException e) {
            if (!connectionIsBroken) {
                try {
                    protocolHandler.goodBye(false);
                } catch (JMSException e1) {} //broker may already closed socket
            }
            try {
                readChannel.close();
                protocolHandler.close();
            } catch (JMSException e2) {
                e2.setLinkedException(e);
                throw e2;
            }
            throw e;
        }
        //Mark as open only after connection is truely open
        isClosed = false;
        //If this was truely opened from XA, then this will be true
        openedFromXA = mode;
    }

    /**
     * Calling stop/close from message listener is not allowed.
     * We check this by comparing the current thread and each session
     * reader thread in this connection.  If any comparison returns true
     * in the Session.checkPermission(), IllegalStateException is thrown.
     */
    private void
    checkPermission(boolean checkAsyncSend) throws JMSException {
        SessionImpl session = null;

        try {
            Enumeration enum2 = sessionTable.elements();
            while (enum2.hasMoreElements()) {
                session = (SessionImpl) enum2.nextElement();
                session.checkPermission();
                if (checkAsyncSend) {
                    session.checkPermissionForAsyncSend();
                }
            }
        } catch (JMSException ie) {
            throw ie;
        } catch (Exception ex) {
            javax.jms.IllegalStateException jmse =
                new javax.jms.IllegalStateException (ex.toString());

            jmse.setLinkedException(ex);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * From JMS spec 4.3.5 - page 53:
     * Once a connection has been closed an attempt to use it or its sessions
     * or their message consumers and producers must throw an
     * IllegalStateException (calls to the close method of these objects must
     * be ignored). It is valid to continue to use message objects created or
     * received via the connection with the exception of a received message
     * acknowledge method.  Closing a closed connection must NOT throw an
     * exception.
     */
    protected void
    checkConnectionState() throws JMSException {

        if ( isClosed ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CONNECTION_CLOSED);

            javax.jms.IllegalStateException jmse =
                new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_CONNECTION_CLOSED);

            //connectionLogger.throwing
            //    (getClass().getName(), "checkConnectionState", jmse);

            //throw jmse;

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * Check if connection is broken.  Called by SessionReader when it
     * detects an Exception.
     */
    protected boolean
    isBroken() {
        return connectionIsBroken;
    }

    /**
     * called by openConnection.
     */
    protected synchronized boolean
    isClosed() {
        return isClosed;
    }

    public synchronized boolean
    _isClosed() {
        return isClosed();
    }

    public synchronized void
    _unsetClientID()
    throws JMSException
    {
        //System.out.println("CI:_unsetClientID()");
        this.clientID = null;
        allowToSetClientID = true;
        protocolHandler.unsetClientID();
    }

    public synchronized void
    _setClientID(String cid)
    throws JMSException
    {
        //System.out.println("CI:_setClientID()");
        checkConnectionState();
        protocolHandler.setClientID(cid);
        this.clientID = cid;
        allowToSetClientID = false;
    }
    
    /**
     * Set clientID to the specified value, bypassing any checks as to whether calling setClientID is allowed
     * @param clientID
     */
    @Override
    public void _setClientIDForContext(String clientID) {
        try {
            _setClientID(clientID);
        } catch (javax.jms.IllegalStateException e) {
            throw new MQIllegalStateRuntimeException(e);
        } catch (javax.jms.InvalidClientIDException e) {
            throw new MQInvalidClientIDRuntimeException(e);
        } catch (JMSException e) {
            throw new MQRuntimeException(e);
        }
    }

    public synchronized String
    _getClientID()
    {
        //System.out.println("CI:_getClientID()");
        return clientID;
    }

    public synchronized void
    _closeForPooling()
    throws JMSException
    {
        //System.out.println("CI:_closeForPooling()");
        TemporaryDestination tDest = null;

        try {
            while (tempDestTable.isEmpty() == false) {
                tDest = (TemporaryDestination) tempDestTable.firstElement();
                tDest.delete();
                tempDestTable.remove(tDest);
            }
        } catch (Exception e) {
            if (debug) {
                Debug.printStackTrace(e);
            }
        }
        if (this.clientID != null) {
            //System.out.println("CI:_closeForPooling:unsettingClientID");
            _unsetClientID();
        }
    }

    public void
    _setExceptionListenerFromRA(ExceptionListener listener) throws JMSException {
        //Enables RA to set an ExceptionLister w/o affecting setClientID()
        checkConnectionState();
        exceptionListener = listener;
    }

    public boolean hasDaemonThreads()
    {
        return daemonThreads;
    }

    /**
     * called by ReadChannel when connection is broken.
     */
    protected void
    setIsBroken (boolean flag) {
        connectionIsBroken = flag;
    }

    /**
     * Set connection recover in process flag.  Called by ReadChannel.
     */
    protected void setRecoverInProcess (boolean state) {
        recoverInProcess = state;
    }

    /**
     * Get connection recover in process flag.  Called by close/stop
     */
    protected boolean getRecoverInProcess() {
        return recoverInProcess;
    }

    /**
     * Get user name for this connection
     */
     protected String getUserName() {
        return userName;
     }

     /**
      * Get client ID or its IP Address.
      * @return client ID if not null.  Otherwise, return IP address.
      */
    protected String getClientIDOrIPAddress() {

        if ( clientID != null ) {
            return clientID;
        } else if ( clientIPAddress == null ) {
            //set it if not set yet.
            synchronized (syncObj) {
                try {
                    clientIPAddress = InetAddress.getLocalHost().getHostAddress();
                } catch (Exception e) {
                    //default.
                    clientIPAddress = "127.0.0.1";
                }
            }
        }

        return clientIPAddress;
    }

    /**
     * The debugging method for Traceable interface.
     */
     public void dump (PrintStream ps) {
        try {
        ps.println ("------ ConnectionImpl dump ------");
        ps.println ("clientID: " + clientID);
        ps.println ("host: " + configuration.getProperty(ConnectionConfiguration.imqBrokerHostName));
        ps.println ("port: " + configuration.getProperty(ConnectionConfiguration.imqBrokerHostPort));
        ps.println("protectMode: " + protectMode);
        ps.println ("Flow control waterMark: " + flowControlWaterMark);
        ps.println("Flow Control Message size: " + flowControlMsgSize);

        if ( protocolHandler != null ) {
            ps.println ("Broker acknowledge mode: " +
                         protocolHandler.getAckEnabled());
            ps.println ("Require ack back from broker for auto/client ack: " +
                         protocolHandler.getAckAck());
        }

        //ps.println("maxq: " + maxQueueSize);
        //ps.println("minq: " + minQueueSize);
        ps.println("dupsOkLimit: " + dupsOkLimit);

        ps.println ("isAckLimited: " + isAckLimited);
        ps.println("ackLimit: " + ackLimit);
        ps.println("failoverEnabled: " + failoverEnabled);
        
        ps.println("imqReconnectEnabled: " + imqReconnect);

        ps.println("isConnectedToHaBroker: " + isConnectedToHABroker);
        
        //ps.println("createProducerChk: " + createProducerChk);
        //ps.println("autoCreateDestination: " + autoCreateDestination);

        //ps.println("licenseProps: {" + (licenseProps == null ? "null" : licenseProps.toString()) + "}");

        ps.println("Connection is stopped: " + isStopped);

        Enumeration enum2 = sessionTable.elements();
        while ( enum2.hasMoreElements() ) {
            SessionImpl session = (SessionImpl) enum2.nextElement();
            session.dump(ps);
        }
        } catch ( Exception e ) {
            Debug.printStackTrace(e);
        }
     }

     /**
      * Get ping interval for this connection.
      * @return imqPingInterval.
      */
     protected long getPingInterval() {
         return imqPingInterval;
     }

     public String toString() {
        //if (protocolHandler != null)
        //    return protocolHandler.toString();
        //else
        //    return null;
        return "BrokerAddress=" + this.getLastContactedBrokerAddress() +
               ", ConnectionID=" + this.getConnectionID() + 
               ", ReconnectEnabled: " + this.imqReconnect +
               ", IsConnectedToHABroker: " + this.isConnectedToHABroker;
     }

    public Hashtable getDebugState(boolean verbose) {
        Hashtable ht = new Hashtable();

        ht.put("Configuration", configuration);

        ht.put("connectionID", String.valueOf(connectionID));
        ht.put("clientID", String.valueOf(clientID));
        ht.put("brokerProtocolLevel", String.valueOf(brokerProtocolLevel));

        ht.put("reconnecting", String.valueOf(reconnecting));
        ht.put("isTopicConnection", String.valueOf(isTopicConnection));

        ht.put("isQueueConnection", String.valueOf(isQueueConnection));

        ht.put("isStopped", String.valueOf(isStopped));
        ht.put("isClosed", String.valueOf(isClosed));
        ht.put("connectionIsBroken", String.valueOf(connectionIsBroken));
        ht.put("recoverInProcess", String.valueOf(recoverInProcess));
        ht.put("failoverEnabled", String.valueOf(failoverEnabled));

        ht.put("imqReconnectEnabled", String.valueOf(imqReconnect));
        ht.put("isConnectedToHABroker", String.valueOf(isConnectedToHABroker));

        if ( this.JMQBrokerList != null ) {
            ht.put("JMQBrokerList", this.JMQBrokerList);
        }

        if ( JMQClusterID != null ) {
            ht.put("JMQClusterID", this.JMQClusterID);
        }

        if ( JMQStoreOwner != null ) {
            ht.put("JMQStoreOwner", this.JMQStoreOwner);
        }

        if ( JMQStoreSession != null ) {
            ht.put("JMQStoreSession", String.valueOf(JMQStoreSession));
        }

        boolean isExpLsrSet = false;
        if (exceptionListener != null ){
            isExpLsrSet = true;
        }

        ht.put("IsExceptionListenerSet", String.valueOf(isExpLsrSet));

        if ( this.readChannel != null ) {
            ht.put("readChannnelIsClosed", String.valueOf(readChannel.isClosed));
            ht.put("readChannnelReceivedGoodByeReply", String.valueOf(readChannel.receivedGoodByeReply));
        }

        if ( this.protocolHandler != null ) {
            ht.put("protocolHandlerIsClosed", String.valueOf(protocolHandler.isClosed()));
            ht.put("UserBrokerInfo", protocolHandler.getUserBrokerInfo());
        }

        ht.put("FlowControl", flowControl.getDebugState(this));

        ht.put("# sessions", String.valueOf(sessionTable.size()));
        int n = 0;
        Enumeration enum2 = sessionTable.elements();
        while ( enum2.hasMoreElements() ) {
            SessionImpl session = (SessionImpl) enum2.nextElement();
            ht.put("Session[" + n + "]", session.getDebugState(verbose));
            n++;
        }

        ht.put("# connectionConsumers",
            String.valueOf(connectionConsumerTable.size()));
        n = 0;
        enum2 = connectionConsumerTable.elements();
        while ( enum2.hasMoreElements() ) {
            ConnectionConsumerImpl connectionConsumer =
                (ConnectionConsumerImpl)enum2.nextElement();
            ht.put("ConnectionConsuer[" + n + "]",
                connectionConsumer.getDebugState(verbose));
            n++;
        }

        return ht;
    }

    public Object TEST_GetAttribute(String name) {
        if (name.startsWith("FlowControl")) {
            return flowControl.TEST_GetAttribute(name, this);
        }

        return null;
    }

    public void printDebugState() {

        try {

            DebugPrinter dbp = new DebugPrinter(2);

            Hashtable debugState = getDebugState(false);

            dbp.setHashtable(debugState);

            dbp.println();

            dbp.close();

        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    /**
     * The following methods are moved back from UnifiedConnectionImpl.  This
     * class is to be removed.
     */

    /** Create a Session.
      *
      * @param transacted if true, the session is transacted.
      * @param acknowledgeMode indicates whether the consumer or the
      * client will acknowledge any messages it receives. This parameter
      * will be ignored if the session is transacted. Legal values
      * are <code>Session.AUTO_ACKNOWLEDGE</code>,
      * <code>Session.CLIENT_ACKNOWLEDGE</code> and
      * <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
      *
      * @return a newly created session.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         session due to some internal error or
      *                         lack of support for specific transaction
      *                         and acknowledgement mode.
      *
      * @see Session#AUTO_ACKNOWLEDGE
      * @see Session#CLIENT_ACKNOWLEDGE
      * @see Session#DUPS_OK_ACKNOWLEDGE
      */
    public Session
    createSession(boolean transacted, int acknowledgeMode) throws JMSException {

        checkConnectionState();

        //disallow to set client ID after this action.
        setClientIDFlag();

        return new UnifiedSessionImpl ( this, transacted, acknowledgeMode );
    }
    
    /* (non-Javadoc)
     * @see com.sun.messaging.jms.Connection#createSession(int)
     */
	@Override
    public Session createSession(int acknowledgeMode) throws JMSException {

        checkConnectionState();

        //disallow to set client ID after this action.
        setClientIDFlag();
        
        if (acknowledgeMode==Session.SESSION_TRANSACTED){
        	// JMS 2.0
        	return new UnifiedSessionImpl(this, true, acknowledgeMode);
        } else {
        	return new UnifiedSessionImpl ( this, acknowledgeMode );
        }
    }
    
	/* (non-Javadoc)
	 * @see javax.jms.Connection#createSession()
	 */
	@Override
	public Session createSession() throws JMSException {
		return createSession(false,Session.AUTO_ACKNOWLEDGE);
	}    

    /**
     * Create an XASession.
     *
     * @exception JMSException if JMS Connection fails to create an
     *                         XA session due to some internal error.
     */
    public XASession
    createXASession() throws JMSException {

        checkConnectionState();

        //disallow to set client ID after this action.
        setClientIDFlag();

        return new XASessionImpl (this, false, 0);
    }

    /** Create a connection consumer for this connection (optional operation).
      * This is an expert facility not used by regular JMS clients.
      *
      *
      * @param destination the destination to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      * @param sessionPool the server session pool to associate with this
      * connection consumer.
      * @param maxMessages the maximum number of messages that can be
      * assigned to a server session at one time.
      *
      * @return the connection consumer.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         a connection consumer due to some internal
      *                         error or invalid arguments for sessionPool
      *                         and message selector.
      * @exception InvalidSelectorException if the message selector is invalid.
      * @see javax.jms.ConnectionConsumer
      */
    @Override 
    public ConnectionConsumer
    createConnectionConsumer(Destination destination,
                             String messageSelector,
                             ServerSessionPool sessionPool,
                             int maxMessages)
                             throws JMSException {

        return createUnifiedConnectionConsumer(destination,
                       messageSelector, sessionPool,
                       maxMessages, null, false);
    }

    /** Create a QueueSession.
      *
      * @param transacted if true, the session is transacted.
      * @param acknowledgeMode indicates whether the consumer or the
      * client will acknowledge any messages it receives. This parameter
      * will be ignored if the session is transacted. Legal values
      * are <code>Session.AUTO_ACKNOWLEDGE</code>,
      * <code>Session.CLIENT_ACKNOWLEDGE</code> and
      * <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
      *
      * @return a newly created queue session.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         session due to some internal error or
      *                         lack of support for specific transaction
      *                         and acknowledgement mode.
      *
      * @see Session#AUTO_ACKNOWLEDGE
      * @see Session#CLIENT_ACKNOWLEDGE
      * @see Session#DUPS_OK_ACKNOWLEDGE
      */
    protected QueueSession
    createQueueSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {

        checkConnectionState();

        //disallow to set client ID after this action.
        setClientIDFlag();

        return new QueueSessionImpl ( this, transacted, acknowledgeMode );
    }


    /** Create a connection consumer for this connection (optional operation).
      * This is an expert facility not used by regular JMS clients.
      *
      *
      * @param queue the queue to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      * @param sessionPool the server session pool to associate with this
      * connection consumer.
      * @param maxMessages the maximum number of messages that can be
      * assigned to a server session at one time.
      *
      * @return the connection consumer.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         a connection consumer due to some internal
      *                         error or invalid arguments for sessionPool
      *                         and message selector.
      * @exception InvalidSelectorException if the message selector is invalid.
      * @see javax.jms.ConnectionConsumer
      */
    public ConnectionConsumer
    createConnectionConsumer(Queue queue,
                             String messageSelector,
                             ServerSessionPool sessionPool,
                             int maxMessages)
                             throws JMSException {

        return createUnifiedConnectionConsumer(queue,
                       messageSelector, sessionPool,
                       maxMessages, null, false);
    }

    /** Create a TopicSession
      *
      * @param transacted if true, the session is transacted.
      * @param acknowledgeMode indicates whether the consumer or the
      * client will acknowledge any messages it receives. This parameter
      * will be ignored if the session is transacted. Legal values
      * are <code>Session.AUTO_ACKNOWLEDGE</code>,
      * <code>Session.CLIENT_ACKNOWLEDGE</code> and
      * <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
      *
      * @return a newly created topic session.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         session due to some internal error or
      *                         lack of support for specific transaction
      *                         and acknowledgement mode.
      *
      * @see Session#AUTO_ACKNOWLEDGE
      * @see Session#CLIENT_ACKNOWLEDGE
      * @see Session#DUPS_OK_ACKNOWLEDGE
      */
    public TopicSession
    createTopicSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {
        checkConnectionState();

        TopicSessionImpl ts = new TopicSessionImpl (this, transacted, acknowledgeMode);

        //disallow to set client ID after this action.
        setClientIDFlag();

        return ( ts );
    }

    /** Create a connection consumer for this connection (optional operation).
      * This is an expert facility not used by regular JMS clients.
      *
      * @param topic the topic to access
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      * @param sessionPool the server session pool to associate with this
      * connection consumer.
      * @param maxMessages the maximum number of messages that can be
      * assigned to a server session at one time.
      *
      * @return the connection consumer.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         a connection consumer due to some internal
      *                         error or invalid arguments for sessionPool.
      * @exception InvalidSelectorException if the message selector is invalid.
      * @see javax.jms.ConnectionConsumer
      */
    public ConnectionConsumer
    createConnectionConsumer(Topic topic,
                             String messageSelector,
                             ServerSessionPool sessionPool,
                             int maxMessages)
                             throws JMSException {
        return createUnifiedConnectionConsumer(topic,
                       messageSelector, sessionPool,
                       maxMessages, null, false);

    }

    /** Create a durable connection consumer for this connection (optional operation).
      * This is an expert facility not used by regular JMS clients.
      *
      * @param topic the topic to access
      * @param subscriptionName durable subscription name
      * @param messageSelector only messages with properties matching the
      * message selector expression are delivered
      * @param sessionPool the serversession pool to associate with this
      * durable connection consumer.
      * @param maxMessages the maximum number of messages that can be
      * assigned to a server session at one time.
      *
      * @return the durable connection consumer.
      *
      * @exception JMSException if JMS Connection fails to create a
      *                         a connection consumer due to some internal
      *                         error or invalid arguments for sessionPool
      *                         and message selector.
      *
      * @see javax.jms.ConnectionConsumer
      */
    @Override
    public ConnectionConsumer
    createDurableConnectionConsumer(Topic topic,
                                    String subscriptionName,
                                    String messageSelector,
                                    ServerSessionPool sessionPool,
                                    int maxMessages)
                                    throws JMSException {

        return createUnifiedConnectionConsumer(topic,
                       messageSelector, sessionPool,
                       maxMessages, subscriptionName, true);

    }

    @Override 
    public ConnectionConsumer
    createSharedConnectionConsumer(Topic topic,
                                   String subscriptionName,
                                   String messageSelector,
                                   ServerSessionPool sessionPool,
                                   int maxMessages)
                                   throws JMSException {

        return createUnifiedConnectionConsumer(topic,
                       messageSelector, sessionPool,
                       maxMessages, subscriptionName, false, true);
    }

    @Override 
    public ConnectionConsumer
    createSharedDurableConnectionConsumer(Topic topic,
                                    String subscriptionName,
                                    String messageSelector,
                                    ServerSessionPool sessionPool,
                                    int maxMessages)
                                    throws JMSException {

        return createUnifiedConnectionConsumer(topic,
                       messageSelector, sessionPool,
                       maxMessages, subscriptionName, true, true);
    }

    private ConnectionConsumer createUnifiedConnectionConsumer(Destination destination,
            String messageSelector, ServerSessionPool sessionPool,
            int maxMessages, String subscriptionName, boolean durable) throws JMSException {

        return createUnifiedConnectionConsumer(destination, messageSelector,
                   sessionPool, maxMessages, subscriptionName, durable, false);
    }

    private ConnectionConsumer createUnifiedConnectionConsumer(Destination destination,
            String messageSelector, ServerSessionPool sessionPool,
            int maxMessages, String subscriptionName, boolean durable, boolean share)
            throws JMSException {


        checkConnectionState();

        //Disallow null/empty durable subscription names
        if ((durable || share) && (subscriptionName == null || "".equals(subscriptionName))) {
            String ekey = AdministeredObject.cr.X_INVALID_DURABLE_NAME;
            if (!durable) {
                ekey =  AdministeredObject.cr.X_INVALID_SHARED_SUBSCRIPTION_NAME;
            }
            String errorString = AdministeredObject.cr.getKString(ekey, "\"\"");
            JMSException jmse =
            new JMSException(errorString, ekey);

            ExceptionHandler.throwJMSException(jmse);
        }
        if (maxMessages < 1) {
            String mmsg = String.valueOf(maxMessages);
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SVRSESSION_MAXMESSAGES, mmsg);
            JMSException jmse =
            new JMSException(errorString, AdministeredObject.cr.X_SVRSESSION_MAXMESSAGES);
            ExceptionHandler.throwJMSException(jmse);
        }

        int load = 1;
        String lm = getProperty(ConnectionConfiguration.imqLoadMaxToServerSession);
        if (lm != null && lm.equals("true")) {
            load = maxMessages;
        }

        //disallow to set client ID after this action.
        setClientIDFlag();

        return new ConnectionConsumerImpl(this, destination, messageSelector, 
                       sessionPool, load, subscriptionName, durable, share);
    }

    //End of methods moved from UnifiedConnectionImpl.

    /**
     * Hawk event listener.
     */

    /**
     * Set MQ connection event listener to the current connection.
     *
     * @param listener EventListener
     * @throws JMSException
     */
    public void
    setEventListener (EventListener eventListener)
    throws JMSException {

        this.checkConnectionState();

        if ( eventListener == null ) {
            return; 
        }

        synchronized (syncObj) {
            if (eventHandler == null) {
        		eventHandler = new EventHandler(this);
        	}
        	this.eventListener = eventListener;
        }
    }

    public EventListener getEventListener() {
    	synchronized (syncObj) {
    		return this.eventListener;
    	}
    }

    /**
     * Set MQ consumer event listener on a destination to this connection.
     *
     * @param dest the destination on which consumer event is interested in
     * @param listener EventListener
     * @throws JMSException
     */
    public void 
    setConsumerEventListener (com.sun.messaging.Destination dest,
                              EventListener listener) throws JMSException {
        if (listener == null) { 
            throw new JMSException("listener is null");
        }

        this.checkConnectionState();

        synchronized (syncObj) {
            if (eventHandler == null) {
        		eventHandler = new EventHandler(this);
        	}
            eventHandler.addConsumerEventListener(dest, listener);
        }

        try {
            protocolHandler.requestConsumerInfo(dest, false);
        } catch (Exception ex) { 
            try {
            eventHandler.removeConsumerEventListener(dest);
            } catch (Exception e) {}

            JMSException jmsex =  new JMSException(AdministeredObject.cr.getKString(
                                  ClientResources.X_ADD_CONSUMER_EVENT_LISTENER,
                                  dest.getName(), ex.getMessage()));
            jmsex.setLinkedException(ex);
            throw jmsex;
        }
    }

    /**
     * Remove a MQ consumer event listener from the current connection.
     *
     * @param dest the destination on which consumer event was interested in
     * @throws JMSException
     */
    public void 
    removeConsumerEventListener (com.sun.messaging.Destination dest) throws JMSException {
        this.checkConnectionState();

        synchronized (syncObj) {
            if (eventHandler == null) {    
                throw new javax.jms.IllegalStateException(AdministeredObject.cr.getKString(
                                    ClientResources.X_NO_EVENT_LISTENER_REGISTERED));
            }
            eventHandler.removeConsumerEventListener(dest);
        }

        try {
            protocolHandler.requestConsumerInfo(dest, true);
        } catch (Exception e) {
            connectionLogger.log(Level.WARNING, AdministeredObject.cr.getKString(
                                 ClientResources.W_RM_CONSUMER_EVENT_LISTENER,
                                 dest.getName(), e.getMessage()), e);
        }
    }


    /**
     * Get the current connected broker's address.
     *
     * @return the broker address that the current connection is associated with.
     * @throws JMSException if any internal error occurs.
     */
    public String getBrokerAddress() {
    	
    	String brokerAddr = null;
    	
    	try {
    		brokerAddr = 
    		getProtocolHandler().getConnectionHandler().getBrokerAddress();
    	} catch (Exception e) {
    		/**
    		 * When reconnecting and client aborts, we return the
    		 * last connected broker.
    		 */
    		brokerAddr = getLastContactedBrokerAddress();
    	}
    	
    	return brokerAddr;
        
    }
    
    /**
     * check if the specified connection is connected to the same broker
     * as the current connection.
     * 
     * broker host/port must be the same.
     * 
     * @param foreignConn
     * @return true if connected to same broker host and port.
     * otherwise return false.
     */
    public boolean isConnectedToSameBroker (ConnectionImpl foreignConn) {
    	
    	boolean isSame = false;
    	
    	if (foreignConn == null) {
    		return false;
    	}
    	
    	String foreignAddr = foreignConn.getBrokerAddress();
    	String myAddr = this.getBrokerAddress();
    	
    	if (myAddr.equals(foreignAddr)) {
    		isSame = true;
    	}
    	
    	return isSame;
    }

    /**
     * Get the up to date broker AddressList.
     * @return String the up to date broker address list.
     */
    public String getBrokerAddressList() {
        return this.JMQBrokerList;
    }

    public  boolean isConnectedToHABroker (){
        return isConnectedToHABroker;
    }

    public boolean getIsCloseCalled() {
        return this.isCloseCalled;
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    public void triggerConnectionReconnectedEvent () {

        if ( this.eventListener != null ) {
            eventHandler.triggerConnectionReconnectedEvent();
        } else {
            setReconnecting(false);
        }

        this.logLifeCycle(ClientResources.E_CONNECTION_RECONNECTED);
    }

    public void triggerConnectionReconnectFailedEvent (JMSException jmse) {

        if ( this.eventListener != null ) {
            eventHandler.triggerConnectionReconnectFailedEvent(jmse, lastContactedBrokerAddress);
        }

        this.logLifeCycle(ClientResources.E_CONNECTION_RECONNECT_FAILED);
    }

    public void triggerConnectionClosingEvent (long timePeriod) {

        if ( this.eventListener != null ) {
            eventHandler.triggerConnectionClosingEvent(
                ConnectionClosingEvent.CONNECTION_CLOSING_ADMIN, timePeriod);
        }

        this.logLifeCycle(ClientResources.E_CONNECTION_CLOSING_ADMIN);
    }

    public void triggerConnectionClosedEvent (String evcode, JMSException jmse) {

        if ( this.eventListener != null ) {
            eventHandler.triggerConnectionClosedEvent(evcode, jmse);
        }
        //XXX HAWK -- filter multiple *closed* logs.
        this.logLifeCycle(evcode);
    }

    public void triggerConnectionExitEvent (JMSException jmse) {

        if ( this.eventListener != null ) {
            eventHandler.triggerConnectionExitEvent(jmse, exceptionListener);
        } else if ( exceptionListener != null ) {
            exceptionListener.onException(jmse);
        }

    }

    public void triggerConnectionAddressListChangedEvent (String addressList) {

        if ( this.eventListener != null ) {

            if ( this.extendedEventNotification ) {
                eventHandler.triggerConnectionAddressListChangedEvent(addressList);
            }
        }

        this.logLifeCycle(ClientResources.E_CONNECTION_ADDRESS_LIST_CHANGED);
    }

    public void triggerConsumerEvent (int infoType, String destName, int destType) {
        if ( this.eventHandler != null ) {
            eventHandler.triggerConsumerEvent(infoType, destName, destType);
        }

    }

    public void logLifeCycle (String key) {

        if ( connectionLogger.isLoggable(Level.FINE) ) {
            connectionLogger.log(Level.FINE, key, this);
        }
    }

    /**
     *
     * @return boolean
     */
    public boolean shouldUpdateAddressList () {
        boolean flag = false;

        if ( this.JMQBrokerList == null ) {
            flag = false;
        } else {
            //the test may return true if broker returns the list in different
            //order for the same content.  But even so is ok ...
            flag = (JMQBrokerList.equals(savedJMQBrokerList) == false);
        }

        return flag;
    }

    public void setLastContactedBrokerAddress (String addr) {
        this.lastContactedBrokerAddress = addr;
    }

    public String getLastContactedBrokerAddress() {
        return lastContactedBrokerAddress;
    }

    public static Logger getConnectionLogger() {
        return connectionLogger;
    }

    public synchronized void setExtendedEventNotification (boolean flag) {
        this.extendedEventNotification = flag;
    }

    public synchronized boolean getExtendedEventNotification () {
        return this.extendedEventNotification;
    }

    public long getPingAckTimeout() {
        return protocolHandler.getPingAckTimeout();
    }
    
    public int getSocketConnectTimeout(){
    	return this.imqSocketConnectTimeout;
    }

    public Integer getPortMapperSoTimeout(){
    	return this.imqPortMapperSoTimeout;
    }

    public long getAsyncSendCompletionWaitTimeout() {
        return asyncSendCompletionWaitTimeout;
    }
    
    /**
     * Enable share subscription for standalone client.
     * bug 6396251 - AS SharedSubscriber functionality should be 
     * accessable for internal customer.
     *  
     * @param flag 
     */
    public synchronized void setEnableSharedClientID (boolean flag) {
        this.imqEnableSharedClientID = flag;
    }

    public synchronized boolean getEnableSharedClientID() {
        return this.imqEnableSharedClientID;
    }

    public synchronized void setEnableSharedSubscriptions (boolean flag) {
        this.imqEnableSharedSubscriptions = flag;
    }

    public synchronized boolean getEnableSharedSubscriptions () {        
    	return this.imqEnableSharedSubscriptions;
    }
    
    /**
     * Called by ReadChannel.updateBrokerVersionInfo().
     * 
     * This reset if imqReconnect flag and imqReconnectEnabled property.
     */
    public void setConnectedToHABroker () {
    	
    	if (this.isHAEnabled()) {
    		this.isConnectedToHABroker = true;
        	
    		//admin user -- do not set flag to true.
    		if (ClientConstants.CONNECTIONTYPE_ADMIN.equals(this.connectionType) == false) {
    			this.imqReconnect = true;
    			this.configuration.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(true));
    		
    			connectionLogger.fine ("Connected to HA broker, auto-reconnect is enabled");
    		} else {
    			connectionLogger.fine ("*** admin user, no auto-reconnect");
    		}
    		
		} else {
    		if (ClientConstants.CONNECTIONTYPE_ADMIN.equals(this.connectionType) == false) {
                //log warning message if HA is disabled
                String info = AdministeredObject.cr.getKString(ClientResources.I_MQ_AUTO_RECONNECT_IS_DISABLED, 
                                                               getLastContactedBrokerAddress());
                connectionLogger.log(Level.WARNING, info);
            }
    	}
    }    
}
