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
 *  @(#)ReadChannel.java	1.117 03/14/08
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
//import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.*;

import javax.jms.*;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.DebugPrinter;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;
import com.sun.messaging.jms.notification.*;
import com.sun.messaging.jmq.jmsclient.notification.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
/**
 * This class is instantiated when the connection object is constructed.
 * The thread is started in the constructor to read packets from the broker.
 * The packets read from the broker should not include JMS messages.  Only
 * handshaking packets should be received.
 *
 * <p>After Connection.start() is called, the START packet is sent to the
 * broker.  JMS messages should only be received after that.
 *
 * <p>When Connection.stop() is called, the STOP packet is sent to the broker
 * and no JMS messages should be delivered to this client.
 */
public class ReadChannel implements PacketDispatcher, Runnable {

    //current read channel thread
    //private Thread readChannelThread = null;
    //current connection
    private ConnectionImpl connection = null;
    //current protocol handler
    private ProtocolHandler protocolHandler = null;

    //table to hold SessionQueue objects.
    protected ReadQTable readQTable = null;

    //table to hold ackQueue objects.
    protected ReadQTable ackQTable = null;

    //table to hold message consumers
    protected InterestTable interestTable = null;

    protected Hashtable requestMetaData = null;

    //used in the run method.  if it is true (when closed), the socket will
    //be closed and exception will be caught.  We do not want to throw exception
    //when we close the connection.  Otherwise, exception should be thrown.
    protected boolean isClosed = false;

    //flag is set to true when receiving good bye reply.  We do not
    //want to throw exception after this.
    protected boolean receivedGoodByeReply = false;

    //flow control class
    protected FlowControl flowControl = null;
    //protect mode -- in this mode, we notify flow control thread to
    //increase message counter
    protected boolean protectMode = false;

    //For thread naming
    protected static final String iMQReadChannel = "iMQReadChannel-";

    //reconnect flag.  when true, client will try to recover the connection
    //when connection is broken.
    //private volatile boolean reconnect = false;
    
    //fatal error flag - set by ConsumerReader with setFatalError()
    //call.  After set, ReadChannel will be notified and call
    //fatalError().
    private boolean isFatalErrorSet = false;

    //saved throwable var. set in setFatalError();
    private Throwable savedError = null;
    
    //save JMSException -- set in this.exitConnection();
    protected JMSException savedJMSException = null;

    //flag to indicate that fatal error is processed.
    private boolean fatalErrorIsProcessed = false;

    //broker non-responsive flag
    private boolean isBrokerNonResponsive = false;

    protected Thread readChannelThread = null;

    protected ConnectionRecover conrc = null;

    private boolean debug = Debug.debug;

    //private EventHandler eventHandler = null;
    //private boolean TEST_HELLO = Boolean.getBoolean("testHA");

    public static final int REQUEST_TYPE_STATUS = 1;
    public static final int REQUEST_TYPE_CLUSTER = 2;
    public static final int REQUEST_TYPE_CONSUMER = 3;

    /**
     * Class constructor.
     *
     * @param connection the current connection this read channel associates.
     */
    ReadChannel(ConnectionImpl connection) {

        this.connection = connection;
        this.protocolHandler = connection.getProtocolHandler();
        
        protocolHandler.setReplyDispatcher(this);
        
        this.interestTable = connection.interestTable;
        this.readQTable = connection.readQTable;
        //XXX PROTOCOL2.1 - ack q table for acknowledgement.
        this.ackQTable = connection.ackQTable;
        //XXX PROTOCOL2.1
        this.requestMetaData = connection.requestMetaData;

        init();
    }

    /**
     * Initialize thread state.  When the read channel thread is started,
     * the Connection is in STOP mode.  No JMS messages should be received.
     */
    private void init() {

        //String prop = connection.getProperty(ConnectionConfiguration.
        //                                     imqReconnectEnabled);
        //if (Boolean.valueOf(prop).booleanValue() == true) {
        //    reconnect = true;
        //}

        protectMode = connection.getProtectMode();

        //flow Control
        flowControl = new FlowControl(connection);
        connection.flowControl = flowControl;
        flowControl.start();

        readChannelThread = new Thread(this);
        if (connection.hasDaemonThreads()) {
            readChannelThread.setDaemon(true);
        }
        //readChannelThread.setName(iMQReadChannel+connection.getConnectionID());
        //use local ID instead.
        readChannelThread.setName(iMQReadChannel + connection.getLocalID());
        readChannelThread.start();
    }

    /**
     * Dispatch packets to the sessions based on the interest id in the packet.
     */
    public void dispatch(ReadWritePacket pkt) throws JMSException {

        //System.out.println ("pkt received: " + pkt.getPacketType());
        switch (pkt.getPacketType()) {
        case PacketType.PING:
            //returns PING_REPLY if broker requested a reply pkt.
            processPing(pkt);
            return;
        case PacketType.PING_REPLY:

            //don't complain about this packet
            return;

        case PacketType.INFO : //INFO packet
            processInfoPacket(pkt);
            break;
        case PacketType.ADD_CONSUMER_REPLY:
        case PacketType.BROWSE_REPLY:

            //XXX PROTOCOL2.1
            replaceConsumerID(pkt);
            processAcknowledge(pkt);
            break;
        case PacketType.HELLO_REPLY:

            //XXX PROTOCOL2.1
            //checkVersion(pkt);

            //check if this is a redirect.
            checkRedirectStatus(pkt);

            replaceConnectionID(pkt);

            //processAcknowledge (pkt);
            updateBrokerVersionInfo(pkt);

            processAcknowledge(pkt);

            connection.writeChannel.updateFlowControl(pkt);
            break;
        case PacketType.ADD_PRODUCER_REPLY:
            replaceProducerID(pkt);
            processAcknowledge(pkt);
            break;
        case PacketType.SEND_REPLY:
            if (!asyncSendAcknowledge(pkt)) {
                processAcknowledge(pkt);
            }
            break;
        case PacketType.ACKNOWLEDGE_REPLY:
        case PacketType.DELETE_CONSUMER_REPLY:
        case PacketType.STOP_REPLY:
        case PacketType.AUTHENTICATE_REQUEST:
        case PacketType.AUTHENTICATE_REPLY:
        case PacketType.CREATE_DESTINATION_REPLY:
        case PacketType.START_TRANSACTION_REPLY:
        case PacketType.COMMIT_TRANSACTION_REPLY:
        case PacketType.VERIFY_DESTINATION_REPLY:
        case PacketType.DELIVER_REPLY:
        case PacketType.DESTROY_DESTINATION_REPLY:
        case PacketType.SET_CLIENTID_REPLY:
        case PacketType.GENERATE_UID_REPLY:
        case PacketType.END_TRANSACTION_REPLY:
        case PacketType.PREPARE_TRANSACTION_REPLY:
        case PacketType.ROLLBACK_TRANSACTION_REPLY:
        case PacketType.RECOVER_TRANSACTION_REPLY:
        case PacketType.DELETE_PRODUCER_REPLY:
        case PacketType.CREATE_SESSION_REPLY:
        case PacketType.DESTROY_SESSION_REPLY:
        case PacketType.GET_LICENSE_REPLY:
        case PacketType.VERIFY_TRANSACTION_REPLY:
            //sessionId = new Integer ( pkt.getInterestID() );
            processAcknowledge(pkt);
            break;
        case PacketType.GOODBYE_REPLY:
            processAcknowledge(pkt);

            //sessionId = new Integer ( pkt.getInterestID() );
            receivedGoodByeReply = true;

            /**
             * All communication with broker is done after receiving this
             * packet. This will exit the readchannel thread as well as
             * the flow control thread.
             */
            close();
            break;
        case PacketType.MESSAGE:
        case PacketType.MAP_MESSAGE:
        case PacketType.OBJECT_MESSAGE:
        case PacketType.STREAM_MESSAGE:
        case PacketType.TEXT_MESSAGE:
        case PacketType.BYTES_MESSAGE:
            processJMSMessage(pkt);
            break;
        case PacketType.RESUME_FLOW:
            processResumeFlow(pkt);
            break;
        case PacketType.GOODBYE:
            processBrokerGoodbye(pkt);
            break;
        case PacketType.DEBUG:
            processDebug(pkt);
            break;
        default:
            if (isClosed == false) {
                String errString = AdministeredObject.cr.getKString(
                    AdministeredObject.cr.W_UNKNOWN_PACKET);
                Debug.getPrintStream().println(errString);
                pkt.dump(Debug.getPrintStream());
                
               //check if the connection is still healthy.
                // Bug6664280 - 
                // JMQ client unresponsive, loads CPU at 100% 
                // and generates log output at a high rate.
                this.checkConnectionState();
            }
            break;
        }
    }
    
    /**
     * check protocol handler state.  
     * Bug6664280 - 
     * JMQ client unresponsive, loads CPU at 100% 
     * and generates log output at a high rate.
     * 
     * This is a side effect from bug 6664278.  But we still want to
     * handle here so that it is impossible to generate logs in a 
     * loop.
     */
    private void checkConnectionState() {
        
        try {
            
            //In theory, this state cannot happen.  The bug 6664278 
            //may have contributed to this state and should be fixed.
            //adding this code here to verify and exit the bad state
            //should it ever happen again.
            if (protocolHandler.isClosed()) {
                
                Debug.getPrintStream().println("Fatal Error: ReadChannel closing due to protocol handler closed.");
                
                close();
            }
        } catch (Exception e) {
            Debug.printStackTrace(e);
        } finally {
            ;
        }
    }

    private void processPing(ReadOnlyPacket ping) {

        try {

            if (ping.getFlag(PacketFlag.A_FLAG) == true) {
                protocolHandler.pingReply(ping);
            }

        } catch (Exception jmse) {
            ExceptionHandler.rootLogger.log(Level.WARNING,
                                            jmse.getLocalizedMessage(), jmse);
        }

    }

    /**
     * process INFO packet sent from broker.
     * @param pkt ReadWritePacket
     */
    private void processInfoPacket(ReadWritePacket pkt) {
        try {
            Hashtable prop = pkt.getProperties();

            int requestType = ((Integer) prop.get("JMQRequestType")).intValue();

            if (requestType == REQUEST_TYPE_STATUS) {
                //STATUS TYPE
                processStatusInfoPacket(pkt);

            } else if (requestType == REQUEST_TYPE_CLUSTER) {
                //CLUSTER
                processClusterInfoPacket(pkt);

            } else if (requestType == REQUEST_TYPE_CONSUMER) {
                processConsumerInfoPacket(pkt);

            } else {
                Debug.println("*** received unknown INFO packet: ");
                pkt.dump(Debug.getPrintStream());
            }

            //if contains addresslist, update the list.
            String addrlist = (String) prop.get("JMQBrokerList");
            if ( addrlist != null ) {

                //save the previous list
                connection.savedJMQBrokerList = connection.JMQBrokerList;
                //save the current list
                connection.JMQBrokerList = addrlist;

                connection.triggerConnectionAddressListChangedEvent(addrlist);
            }

        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);

            Debug.printStackTrace(e);
            pkt.dump(Debug.getPrintStream());
        }

    }

    private void processStatusInfoPacket(ReadWritePacket pkt) {
        Hashtable prop = getHashtableFromMessageBody(pkt);
        processStatusInfo(prop);
    }

    private void processStatusInfo(Hashtable prop) {

        boolean isLocal = isLocalBroker(prop);

        /**
         * we only care about if this is the local broker info packet.
         */
        if (isLocal) {
            int state = ((Integer) prop.get("State")).intValue();
            long msecs = 0;

            //shutdown started
            if (state == 7) {

                Long milliSecs = (Long) prop.get("ShutdownMS");
                if (milliSecs != null) {
                    msecs = milliSecs.longValue();
                }

                connection.triggerConnectionClosingEvent(msecs);
            }
        } else {
            //will be caught by processInfoPacket() above
            //throw new RuntimeException("INFO pkt is not for the local broker");
            Debug.println ("INFO pkt is not for the local broker.");
        }
    }

    private boolean isLocalBroker(Hashtable prop) {
        boolean isLocal = ((Boolean) prop.get("isLocal")).booleanValue();

        return isLocal;
    }

    /**
     *
     * @param pkt ReadWritePacket
     */
    private void processClusterInfoPacket(ReadWritePacket pkt) {

        boolean isLocal = false;

        Hashtable table = getHashtableFromMessageBody(pkt);

        java.util.Iterator it = table.values().iterator();

        boolean found = false;
        Hashtable prop = null;

        while (it.hasNext() && (found == false)) {

            Object obj = it.next();

            if (obj instanceof Hashtable) {
                prop = (Hashtable) obj;

                isLocal = isLocalBroker(prop);

                if (isLocal) {
                    found = true;
                }
            }
        }

        if (found) {
            processStatusInfo(prop);
        } else {
            //throw new RuntimeException ("Cannot find local broker from pkt.");
            Debug.println ("INFO pkt is not for the local broker.");
            pkt.dump(Debug.getPrintStream());
        }


    }

    private void processConsumerInfoPacket(ReadWritePacket pkt) {

        Hashtable body = getHashtableFromMessageBody(pkt);
        String destName = (String)body.get("JMQDestination");
        int destType = ((Integer)body.get("JMQDestType")).intValue();
        int infoType = ((Integer)body.get("JMQConsumerInfoType")).intValue();
        connection.triggerConsumerEvent(infoType, destName, destType);
    }


    protected static Hashtable getHashtableFromMessageBody(ReadOnlyPacket pkt) {

        Hashtable prop = null;

        try {
            InputStream is = pkt.getMessageBodyStream();
            ObjectInputStream ois = new FilteringObjectInputStream(is);
            prop = (Hashtable) ois.readObject();
            ois.close();
            is.close();
        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);
            Debug.printStackTrace(e);
        }

        return prop;
    }

    protected void processDebug(ReadWritePacket pkt) {
        try {
            //InputStream is = pkt.getMessageBodyStream();
            //ObjectInputStream ois = new ObjectInputStream(is);
            //Hashtable props = (Hashtable) ois.readObject();
            //ois.close();
            //is.close();

            Hashtable props = getHashtableFromMessageBody(pkt);
            if (props.get("kill.jvm") != null) {
                ConnectionImpl.connectionLogger.log(Level.INFO, 
                "FAULT-INJECTION: BROKER REQUESTS CLIENT JVM to TERMINATE !");	
                System.exit(1);
                return;
            } else if (props.get("close.conn") != null) {
                String emsg = "FAULT-INJECTION: BROKER REQUESTS CLIENT CLOSE CONNECTION !";
                ConnectionImpl.connectionLogger.log(Level.INFO, emsg);
                JMSException jmse = new JMSException(emsg);
                exitConnection(jmse);
                //for direct mode, we need to break out the waiting threads
                if (this.protocolHandler.isDirectMode()) {
                    //close io and notify all
                    this.protocolHandler.close();
                }
                return;
            }

            boolean isLoggingConfigSet = setLoggingConfig (props);

            if ( isLoggingConfigSet ) {
                return;
            }

            DebugPrinter dbp = new DebugPrinter(2);
            String filename = (String) props.get("file");
            dbp.setFile(filename);

            String vstr = (String) props.get("verbose");
            boolean verbose = Boolean.valueOf(vstr).booleanValue();

            Hashtable debugState = connection.getDebugState(verbose);
            debugState.put("DebugCmd", props);

            dbp.setHashtable(debugState);
            dbp.println();

            dbp.close();
        } catch (Throwable e) {

            ExceptionHandler.logCaughtException(e);

            e.printStackTrace();
        }
    }

    /**
     * This is for internal/private use only. This is not a public API.
     *
     * Use the following imqcmd to change logging configurations.
     *
     * imqcmd send cxn -debug
     * -o logging.name=<LOGGER_NAME>
     * -o logging.level=<LEVEL>
     * -o logging.handler=<LOG_HANDLER>
     * -o logging.pattern=<FILE_HANDLER_PATTERN>
     * -o logging.formatter=<FORMATTER>
     * -n <CONN_UID>
     * <p>
     * Where <LOGGER_NAME> is the domain name defined in 7.1,
     * <LEVEL> is the logging level for the logger and handler specified
     * in the above imqcmd command.
     * <LOG_HANDLER> is either java.util.logging.ConsoleHandler or
     * java.util.logging.FileHandler.
     * <FILE_HANDLER_PATTERN> is used only if the handler is a type of
     * java.util.logging.FileHandler.
     * The syntax of file pattern is defined in the logging API Javadoc.
     * http://java.sun.com/j2se/1.4.2/docs/api/index.html
     * <FORMATTER> is the formatter class full name for the handler.
     * <CONN_UID> is the MQ connection ID.
     *
     * <p>
     *
     * For example:
     *
     * imqcmd send cxn -n 125260721183911168 -u admin -p admin -debug
     * -o logging.name=javax.jms -o logging.level=FINEST
     * -o logging.handler=java.util.logging.FileHandler
     * -o logging.pattern="c:/tmp/test123.log"
     */
    private boolean setLoggingConfig (Hashtable props) {
    	
    	/**
    	 * This method was flagged up by findbugs because it doesn't keep a static reference to the Logger instance 
    	 * which means that the Logger may be garbage collected in JDK 6u16  or JDK 7. 
    	 * However this method is intended to modify existing loggers which should already
    	 * be held by static fields. So there is no need for this method to keep a static reference to them. 
    	 */

        boolean isLoggingConfigSet = false;

        try {
            //get logging domain name
            String loggerName = (String) props.get("logging.name");

            if ( loggerName != null ) {

                //get logger
                Logger logger = Logger.getLogger(loggerName);

                String levelName = (String) props.get("logging.level");

                Level level = Level.parse(levelName);

                logger.setLevel(level);

                System.out.println("***** set logger " +
                                   logger.getName() + " to level " + levelName);

                //get handler class
                String handlerClassName = (String) props.get("logging.handler");

                Handler handler = null;
                if (handlerClassName != null) {

                    System.out.println("**** Handler: " + handlerClassName);

                    if (handlerClassName.equals("java.util.logging.FileHandler")) {

                        String pattern =
                        (String) props.get("logging.pattern");

                        if ( pattern != null ) {
                            System.out.println("**** logging pattern: " + pattern);
                            handler = new FileHandler (pattern);
                        } else {
                            handler = new FileHandler();
                        }

                    } else {

                        handler =
                        (Handler) Class.forName(handlerClassName).newInstance();
                    }

                    handler.setLevel(level);

                    String formatterClassName = (String) props.get("logging.formatter");

                    if ( formatterClassName != null ) {

                        Formatter formatter =
                        (Formatter) Class.forName(formatterClassName).newInstance();

                        System.out.println("*** setting formatter to handler: " + formatterClassName);
                        handler.setFormatter(formatter);
                    }

                    logger.addHandler(handler);

                    System.out.println("***** set handler " + handlerClassName +
                                       " to logger " + loggerName);
                }

                isLoggingConfigSet = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return isLoggingConfigSet;
    }

    protected void processBrokerGoodbye(ReadWritePacket pkt) throws
        JMSException {

        Boolean jmqExit = null;
        String evcode = ConnectionClosedEvent.CONNECTION_CLOSED_ERROR;

        try {
            Hashtable props = pkt.getProperties();

            if (props != null) {
                jmqExit = (Boolean) props.get("JMQExit");

                Integer reason = (Integer) props.get("JMQGoodbyeReason");
                int goodbyeReason = -1;
                if ( reason != null ) {
                    goodbyeReason = reason.intValue();
                    switch (goodbyeReason) {
                        case 1:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_SHUTDOWN;
                            break;
                        case 2:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_RESTART;
                            break;
                        case 3:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_KILL;
                            break;
                        case 4:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_ERROR;
                            break;
                        case 5:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_LOST_CONNECTION;
                            break;
                        default:
                            evcode =
                            ConnectionClosedEvent.CONNECTION_CLOSED_ERROR;
                    }
                }
            }


        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);
            e.printStackTrace();
        }

        /**
         * Get the GOOD-BYE reason code from the pkt and determine the event
         * to use.
         *
         */
        connection.triggerConnectionClosedEvent(evcode, null);

        if (connection.imqReconnect == false ||
            jmqExit != null && jmqExit.booleanValue() == true) {
            String errorString = AdministeredObject.cr.getKString(
                AdministeredObject.cr.X_BROKER_GOODBYE);
            JMSException jmse = new JMSException(errorString,
                                                 AdministeredObject.cr.
                                                 X_BROKER_GOODBYE);

            exitConnection(jmse);
        } 
        
        //for direct mode, we need to break out the waiting threads
        if (this.protocolHandler.isDirectMode()) {
        	//close io and notify all
        	this.protocolHandler.close();
        }
    }

    private void updateBrokerVersionInfo(ReadWritePacket pkt) throws
        JMSException {
        try {
            //use get connection ID here.
            Hashtable props = pkt.getProperties();

            String brokerVersion = (String) props.get("JMQVersion");
            if (brokerVersion != null) {
                connection.setBrokerVersion(brokerVersion);
            }

            Integer protoLevel = (Integer) props.get("JMQProtocolLevel");
            if (protoLevel != null) {
                connection.setBrokerProtocolLevel(protoLevel.intValue());
            }

            int statusCode = ((Integer) props.get("JMQStatus")).intValue();

            //TEST_HA -- to be removed
            //props.put("JMQHA", new Boolean (true));
            //props.put("JMQBrokerList", "mq://localhost, mq://niagra2");
            //end test


            if (statusCode == Status.BAD_VERSION &&
                connection.checkBrokerProtocolLevel()) {
                connection.setNegotiateProtocolLevel(true);
                /**
                 * at this point, readchannel should be closed.
                 * we are reconnecting to the broker from Connection.init()
                 */
                close();
            } else if (statusCode == Status.OK) {

                Boolean isHA = (Boolean) props.get("JMQHA");

                if (isHA != null && isHA.booleanValue()) {

                    //connection.isConnectedToHABroker = true;
                    connection.setConnectedToHABroker();

                    //we only assign the value once
                    if (connection.JMQClusterID == null) {
                        connection.JMQClusterID = (String) props.get(
                            "JMQClusterID");
                    }

                    //use the new storeID.
                    Long storeID = (Long) props.get("JMQStoreSession");
                    if (storeID != null) {
                        Debug.println("**** Previous JMQStoreSession: " + connection.JMQStoreSession);
                        Debug.println("**** Using JMQStoreSession: " + storeID);
                        connection.JMQStoreSession = storeID;
                    }

                    String tmp = (String) props.get("JMQBrokerList");

                    if (tmp != null) {
                        //save the previous list
                        connection.savedJMQBrokerList = connection.
                            JMQBrokerList;
                        //save the current list
                        connection.JMQBrokerList = tmp;
                    }

                    Debug.println("*** connected to HA broker.  JMQClusterID=" +
                        connection.JMQClusterID + " JMQStoreSession=" +
                        connection.JMQStoreSession + " JMQBrokerList=" +
                        connection.JMQBrokerList);

                }
            }

        } catch (Exception e) {
            connection.exceptionHandler.handleException(e,
                AdministeredObject.cr.X_NET_ACK, true);
        }
    }

    //XXX PROTOCOL2.1
    protected void
        replaceConnectionID(ReadWritePacket pkt) throws JMSException {
        try {
            //use get connection ID here.
            Hashtable props = pkt.getProperties();
            Long connectionID = (Long) props.get("JMQConnectionID");
            if (connectionID != null) {
                connection.setConnectionID(connectionID);
                //System.out.println("********* New Connection ID: " + connectionID);
            }
        } catch (Exception e) {
            connection.exceptionHandler.handleException(e,
                AdministeredObject.cr.X_NET_ACK, true);
        }

    }

    protected void
        replaceProducerID(ReadWritePacket pkt) throws JMSException {
        try {
            Hashtable props = pkt.getProperties();

            Long ackID = Long.valueOf(pkt.getConsumerID());
            Long newID = (Long) props.get("JMQProducerID");

            MessageProducerImpl producer = (MessageProducerImpl)
                                           requestMetaData.get(ackID);
            requestMetaData.remove(ackID);

            /**
             * smething is wrong. Error will be handled at ProtocolHandler.
             */
            if (newID == null) {

                if (debug) {
                    Debug.getPrintStream().println(
                        "**** No producer for packet: ");
                    pkt.dump(Debug.getPrintStream());
                }

                return;
            }

            int jmqSize = -1;
            long jmqBytes = -1;

            Integer jmqSizeProp = (Integer) props.get("JMQSize");
            if (jmqSizeProp != null) {
                jmqSize = jmqSizeProp.intValue();
            }

            Long jmqBytesProp = (Long) props.get("JMQBytes");
            if (jmqBytesProp != null) {
                jmqBytes = jmqBytesProp.longValue();
            }

            long producerID = newID.longValue();

            //Get current add producer destination object
            Destination dest = (Destination) producer.addProducerDest;
            //set producer ID
            producer.setProducerID(dest, producerID);
            //set flow limit
            producer.setFlowLimit(producerID, jmqSize);
            //set bytes limit.
            producer.setFlowBytesLimit(producerID, jmqBytes);

        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            e.printStackTrace(Debug.getPrintStream());
        }
    }

    //XXX PROTOCOL2.1
    protected void
        replaceConsumerID(ReadWritePacket pkt) throws JMSException {
        try {

            Hashtable props = pkt.getProperties();

            Long ackID = Long.valueOf(pkt.getConsumerID());
            Long newID = (Long) props.get("JMQConsumerID");

            Consumer consumer = (Consumer) requestMetaData.get(ackID);
            requestMetaData.remove(ackID);

            /**
             * smething is wrong. Error will be handled at ProtocolHandler.
             */
            if (newID == null) {

                if (debug) {
                    Debug.getPrintStream().println(
                        "**** No consumer for packet: ");
                    pkt.dump(Debug.getPrintStream());
                }

                return;
            }

            interestTable.removeInterest(consumer);

            //replace with new ID
            consumer.setInterestId(newID);
            //XXX PROTOCOL2.1 set JMQDestType
            Integer destType = (Integer) props.get("JMQDestType");
            consumer.setDestType(destType);

            //add to interest table
            interestTable.addInterest(consumer);
            //System.out.println ("New Interest ID : " + newID);
            //System.out.println( "interest table dump: " + interestTable.table.toString());

            //XXX PROTOCOL3.5 --
            Integer jmqSizeProp = (Integer) props.get("JMQSize");
            if (jmqSizeProp != null) {
                int jmqSize = jmqSizeProp.intValue();
                if (jmqSize > 0) {
                    consumer.setPrefetchMaxMsgCount(jmqSize);
                }
            }

            connection.flowControl.addConsumerFlowControl(consumer);

            //add consumer to session table
            SessionImpl session = null;
            if (pkt.getPacketType() == PacketType.ADD_CONSUMER_REPLY) {
                if (consumer instanceof TopicSubscriberImpl) {
                    TopicSubscriberImpl ts = (TopicSubscriberImpl) consumer;
                    session = ts.getSession();
                    session.addMessageConsumer((MessageConsumerImpl) consumer);
                } else if (consumer instanceof QueueReceiverImpl) {
                    QueueReceiverImpl qr = (QueueReceiverImpl) consumer;
                    session = qr.getSession();
                    session.addMessageConsumer((MessageConsumerImpl) consumer);
                }
                //else Connection consumer -- no session.
            } else {
                BrowserConsumer bc = (BrowserConsumer) consumer;
                session = bc.getSession();
                session.addBrowserConsumer(bc);
                //System.out.println ("New Interest ID : " + newID + " added to session");
            }

        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            //connection.exceptionHandler.handleException(e, AdministeredObject.cr.X_NET_ACK, true);
            e.printStackTrace();
        }
    }

    //XXX PROTOCOL3.5
    // Handle producer or connection RESUME_FLOW packets sent by the
    // broker.
    protected void
        processResumeFlow(ReadWritePacket pkt) throws JMSException {
        try {
            Hashtable props = pkt.getProperties();

            Integer jmqSizeProp = (Integer) props.get("JMQSize");
            Long jmqBytesProp = (Long) props.get("JMQBytes");
            Long producerID = (Long) props.get("JMQProducerID");

            if (debug) {
                Debug.println("processResumeFlow() :" +
                              " JMQSize = " + jmqSizeProp +
                              ", JMQBytes = " + jmqBytesProp +
                              ", ProducerID = " + producerID);
            }

            if (producerID != null) {

                MessageProducerImpl producer =
                    connection.findMessageProducer(producerID);

                if (producer != null) {
                    if (jmqSizeProp != null) {
                        producer.setFlowLimit(producerID.longValue(),
                                              jmqSizeProp.intValue());
                    }

                    if (jmqBytesProp != null) {
                        producer.setFlowBytesLimit(producerID.longValue(),
                            jmqBytesProp.longValue());
                    }
                } else {
                    //producer is null. The producer maybe closed.
                    if (debug) {
                        Debug.info(
                            "*** warning: Cannot find producer for the resume pkt: ");
                        pkt.dump(Debug.getPrintStream());

                        connection.printDebugState();
                    }
                }
            } else {
                if (debug) {
                    Debug.info("Connection Resume Flow pkt dump: ");
                    pkt.dump(Debug.getPrintStream());
                }
                connection.writeChannel.updateFlowControl(pkt);
            }
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            e.printStackTrace();
            pkt.dump(Debug.getPrintStream());
        }
    }

    /**
     * process JMS message.
     */
    protected void
        processJMSMessage(ReadWritePacket pkt) throws JMSException {

        //XXX PROTOCOL2.1
        long id;
        //temp session queue
        SessionQueue sessionQ = null;
        //temp consumer
        Consumer consumer = null;
        //temp session id
        Object sessionId = null;

        flowControl.messageReceived();

        //check flow control here
        if (pkt.getFlowPaused()) {
            flowControl.requestConnectionFlowResume();
        }

        //get interest id
        id = pkt.getConsumerID();
        //get consumer from interest table
        //XXX PROTOCOL2.1
        consumer = interestTable.getConsumer(Long.valueOf(id));
        /* for regular consumer and connection consumer get the
         * ReadQueue Id (or "session id") associated with the consumer
         */
        if (consumer != null) {
            sessionId = consumer.getReadQueueId();
            //check if we got session id
            if (sessionId != null) {
                //get the session queue
                sessionQ = readQTable.get(sessionId);
                //put the packet in the queue and notify the SessionReader.
                if (sessionQ != null) {
                    //XXX PROTOCOL 3.5
                    //Consumer flow control.
                    flowControl.messageReceived(consumer);
                    if (pkt.getConsumerFlow()) {
                        flowControl.requestResume(consumer);
                    }

                    /**
                     * messages are sent to browser consumer directly so
                     * that it can consume messages even when the conn.
                     * is stopped.
                     *
                     * Note: flow control has no effect on browser consumer.
                     */
                    if (consumer instanceof BrowserConsumer) {
                        deliverToBrowserConsumer((BrowserConsumer) consumer,
                                                 pkt);
                    } else {
                        sessionQ.enqueueNotify(pkt);
                    }
                } else {
                    String errorString = AdministeredObject.cr.getKString(
                        AdministeredObject.cr.W_PACKET_NOT_PROCESSED);
                    
                    String pktstr = errorString + "\n" +  pkt.toVerboseString();
    				ConnectionImpl.connectionLogger.log (Level.WARNING, pktstr);	
                }
            } else {
            	
            	//this could be an OK scenario.  the session could be closed.
            	if (debug) {
            		Debug.getPrintStream().println(
                    	"ERROR: NO session (null) for packet: ");
            		pkt.dump(Debug.getPrintStream());
            	}
            	
            	String msg = "No Session for pkt: \n" + pkt.toVerboseString(); 
            	ConnectionImpl.connectionLogger.log (Level.FINE, msg);	
            }
        } else {
        	//this could be an OK scenario.  the consumer could be closed.
            if (debug) {
                Debug.getPrintStream().println(
                    "ERROR: NO consumer for packet: ");
                pkt.dump(Debug.getPrintStream());
            }
            
            String msg = "No consumer for pkt: \n" + pkt.toVerboseString(); 
        	ConnectionImpl.connectionLogger.log (Level.FINE, msg);	
        }
    }

    /**
     * Deliver packet to the browser consumer.  Pkt for the
     * browser consumer is not delivered to the session queue.
     * The reason is to be able to deliver msgs to the browser
     * consumer even when the connection is stopped.
     *
     * @param consumer the browser consumer to receive the pkt.
     * @param pkt the pkt to be delivered to browser consumer.
     * @throws JMSException if any errors occur.
     */
    protected void
        deliverToBrowserConsumer(BrowserConsumer consumer, ReadOnlyPacket pkt) throws
        JMSException {

        /**
         * construct jms message from mq packet.
         */
        MessageImpl message = protocolHandler.getJMSMessage(pkt);

        /**
         * Get session object associated with the consumer.
         */
        SessionImpl session = ((BrowserConsumer) consumer).session;

        /**
         * set session to the message object.
         * Please see SessionReader.getJMSMessage().
         */
        message.setSession(session);

        /**
         * deliver message to the browser consumer.
         */
        consumer.onMessage(message);
    }

    //JMS 2.0
    private boolean asyncSendAcknowledge(ReadWritePacket pkt) {
        boolean synchronousReply = protocolHandler.isDirectModeTwoThreadWithSyncReplies();
    	if (synchronousReply) {
            return true;
        }
        long ackId = pkt.getConsumerID();
        AsyncSendCallback cb = (AsyncSendCallback)requestMetaData.get(Long.valueOf(ackId));
        requestMetaData.remove(ackId); 
        if (cb == null) {
            return false;
        }     
        cb.processCompletion(pkt, true);
        return true;
    }

    /**
     * process request/reply packets. -- protocol 2.1 change.
     */
    protected void
    processAcknowledge(ReadWritePacket pkt) {
    	
        // CR 6897721 always sent STOP_REPLY via the output queue
        boolean synchronousReply = (protocolHandler.isDirectModeTwoThreadWithSyncReplies() &&
                                    (pkt.getPacketType()!=PacketType.STOP_REPLY));  
    	
        if (synchronousReply){
            // replies are sent directly via a ThreadLocal, not via the ack queue, so we have nothing to do here
            return;
        }
    	
        //XXX PROTOCOL2.1
        long ackId = pkt.getConsumerID();
        //get the ack queue
        //XXX PROTOCOL2.1
        SessionQueue ackQ = ackQTable.get(Long.valueOf(ackId));

        //put the packet in the queue and notify the SessionReader.
        if (ackQ != null) {
            if (debug) {
                Debug.println ("*** notify waiting queue ...." + pkt);
            }
            ackQ.enqueueNotify(pkt);
         } else {
            if (connection.connectionIsBroken ||
                connection.reconnecting ||
                connection.isCloseCalled ) {
                ; //silent, do nothing -- may not be a valid connection. 
            } else {
                String errorString = AdministeredObject.cr.getKString(
                                         AdministeredObject.cr.W_PACKET_NOT_PROCESSED);
                String pktstr = errorString + "\n" +  pkt.toVerboseString();
                                ConnectionImpl.connectionLogger.log(Level.WARNING, pktstr);	
            }
        }
    }

    /**
	 * When connection is closed, this is called. After this,
	 * ProtocolHandler.close() is called. An exception will be caught and we
	 * keep silent in this case.
	 */
    protected synchronized void close() {

        if (isClosed) {
            return;
        }

        //break out of the run loop
        //readChannelThread = null;
        isClosed = true;

        //flow control
        flowControl.close();
    }

    /**
     * This method runs until the Connection is closed.  Packets are read and
     * dispatch to the sessions based on the interest id in the packet.
     */
    public void run() {
        //temp packet
        ReadWritePacket packet = null;

        //Thread currentThread = Thread.currentThread();
        while (isClosed == false) {
            try {
                //read packet from the protocol handler
                packet = protocolHandler.readPacket();

                //if (isFatalErrorSet) {
                //    fatalError(savedError);
                //    return;
                //}

                //dispatch the packet
                dispatch(packet);
            } catch (JMSException e) {
                //with the goodbye reply protocol, we may get exception
                //when closing connection.  Broker may close the socket
                //before we have clean up.

                if (debug) {
                    Debug.println("ReadChannel[connection closed=" +
                                  connection.isClosed +
                                  ", received goodbye-reply=" +
                                  receivedGoodByeReply + "] : " + e.getMessage());
                    Debug.printStackTrace(e);
                }

                if (isFatalErrorSet) {
                    fatalError(savedError);
                    return;
                }

                if (connection.isClosed || receivedGoodByeReply) {
                    connection.connectionIsBroken = true;

                    closeIOAndNotify();
                    return;
                }

                if ( isBrokerNonResponsive ) {

                    //reset the flag
                    this.isBrokerNonResponsive = false;

                    //trigger broker non responsive event
                    connection.triggerConnectionClosedEvent
                        (ConnectionClosedEvent.CONNECTION_CLOSED_NON_RESPONSIVE
                         , null);
                } else {
                    connection.triggerConnectionClosedEvent
                        (ConnectionClosedEvent.
                         CONNECTION_CLOSED_LOST_CONNECTION, e);
                }

                //set recover flag so that connection.close can check
                //this flag.
                //connection.setRecoverInProcess(true);
                //check if we want to recover the connection
                //boolean connectStatus = false;
                //if (reconnect == true) {
                //    connectStatus = recover();
                //}
                //reset flag
                //connection.setRecoverInProcess(false);
                //if recover failed, start normal error handling
                //if (connectStatus == false) {
                //    exitConnection(e);
                //}

                recover2 (e);
            } catch (Exception ex) {
            	Debug.printStackTrace(ex);
            } catch (Throwable error) {
                fatalError(error);
            }

        } //while

        if (debug) {
            Debug.println("ReadChannel exit ...");
        }
    }

    /**
     * Set fatal error state.  Called by ConsumerReader.
     *
     */
    protected synchronized void setFatalError(Throwable err) {
        try {

            if (isFatalErrorSet) {
                return;
            }

            isFatalErrorSet = true;

            savedError = err;

            protocolHandler.close();

        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            Debug.printStackTrace(e);
        }
    }

    /**
     * Set broker non responsive flag and close i/o stream.
     */
    protected void setBrokerNonResponsive() {

        try {

            if ( debug ) {
                Debug.println("*** broker is not responsive.  Closing I/O stream ...");
            }

            isBrokerNonResponsive = true;

            //close i/o so that ReadChannel will get Exception and
            //handle the reconnection if necessary.
            protocolHandler.close();
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
        }

    }

    /**
     * Best effort to exit gracefully.
     */
    protected void fatalError(Throwable error) {

        try {

            ExceptionHandler.logError (error);

            /**
             * make sure that this is called only once.
             */
            synchronized (this) {

                if (fatalErrorIsProcessed) {
                    return;
                }

                fatalErrorIsProcessed = true;
            }

            //set connection is broken flag
            connection.connectionIsBroken = true;
            //close all session qs. this will prevent any 'wait'
            //that would normally happen for later session.close.
            readQTable.closeAll();

            String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.
                                                 X_JVM_ERROR,
                                                 error.toString());
            //construct JMSException
            JMSException jmse =
                new JMSException(errorString, AdministeredObject.cr.X_JVM_ERROR);


            //exit connection.
            exitConnection(jmse);

        } catch (Throwable err) {
            if (Debug.debug) {
                err.printStackTrace();
            }
        } finally {
            isClosed = true;
        }
    }

    private void recover2(JMSException e) {
    	
    	try {
    		//pause to reduce connecting to broker shutting down.
    		Thread.sleep(3000);
    	} catch (Exception e2) {
    		e2.printStackTrace();
    	}
    	
        //set recover flag so that connection.close can check
        //this flag.
        connection.setRecoverInProcess(true);
        //check if we want to recover the connection
        boolean connectStatus = false;
          
        if (this.connection.imqReconnect == true) {
            connectStatus = doRecover();
        }
        
        //reset flag
        connection.setRecoverInProcess(false);
        //if recover failed, start normal error handling
        if (connectStatus == false) {
            exitConnection(e);
        } //else {
        //  connection.setRecoverInProcess (false);
        //}
    }

    /**
     * Recover the connection when it's broken.
     * 1. Delete messages in the session queue and receive queue.
     * 2. Delete unacked messages.
     * 3. Reconnect.
     * 4. hello to broker.
     * 5. Register interests.
     */
    private boolean doRecover() {

        //try to reconnect.  The current thread is blocked.
        boolean reconnected = false;

        //bug 6157462
        //ConnectionRecover conrc = null;

        try {

            //connection.checkAndSetReconnecting();
            connection.setReconnecting(true);
            //close IO streams and wake up all waiting threads for broker acks.
            closeIOAndNotify();

            //6157462
            if (conrc == null) {
                conrc = new ConnectionRecover(connection);
            } else {

                conrc.waitUntilInactive();

                if (conrc.getRecoverState() == conrc.RECOVER_ABORTED) {
                    return false;
                }
            }

            //protocolHandler.init(true);
            conrc.init();
            reconnected = true;

        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);

            connection.setReconnecting(false);

            // This method is not expected to throw any exceptions
            // because it is never called in the application thread
            // context. Hence if the reconnect fails for any reason,
            // the ExceptionListener is invoked directly to notify the
            // application.
            //
            // In other words, this exception has already been
            // forwarded to the application and hence there is no need
            // to do anything here...
        }

        if (reconnected) {
            //start recover in seperate thread
            conrc.start();
        }

        return reconnected;
    }

    /**
     * This shutdown the client connection.
     */
    protected void exitConnection(JMSException e) {
        //set connection is broken flag
        connection.connectionIsBroken = true;

        //save the exception so that other thread can reference this.
        this.savedJMSException = e;
        
        //start to clean up connection/sessions, etc.
        try {
            //close IOs and wake up all waiting threads for broker
            //acknowledge
            closeIOAndNotify();

            /**
             * This closes all session queues.
             */
            readQTable.closeAll();

            //clean up connection
            connection.exitConnection();

            //exit flow control thread
            flowControl.close();
        } finally {

            //exit while loop
            isClosed = true;

            /**
             * trigger connection closed event.
             */
            connection.triggerConnectionClosedEvent(ConnectionClosedEvent.
                CONNECTION_CLOSED_LOST_CONNECTION, e);

            //XXX HAWK: revisit.  Move code below to triggerConnectionExitEvent().
            connection.logLifeCycle(ClientResources.E_CONNECTION_EXIT);

            //if there is an exception listener, call the listener
            if (connection.exceptionListener != null) {

                /**
                 * event handler will call exception listener.
                 */
                connection.triggerConnectionExitEvent(e);

            } else { //print the original exception stack trace
                //so that client would know we have a
                //connection exception happened.
                Exception linkedE = e.getLinkedException();
                if (linkedE != null) {
                    /**
                     * If not authenticated yet, keep silent.
                     * Client will get exception from their own
                     * thread.
                     */
                    if (protocolHandler.authenticated == true) {
                        Debug.printStackTrace(linkedE);
                    }
                }
                //print stack only if authenticated.
                if (protocolHandler.authenticated == true) {
                    Debug.printStackTrace(e);
                }
            }
        }
    }

    /**
     * This wakes up all threads that might be blocking for broker
     * acknowledgement.
     */
    protected void closeIOAndNotify() {

        /**
         * to make sure that socket is closed.
         */
        try {
            protocolHandler.close();
        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);

            if (debug) {
                Debug.printStackTrace(e);
            }
        }

        /**
         * notify whoever is still waiting - such as producers, or
         * ack to broker and waiting for broker to ack back, etc
         */
        /*SessionQueue sq = null;
                 Enumeration enum = readQTable.elements();
                 while ( enum.hasMoreElements() ) {
            sq = (SessionQueue) enum.nextElement();
            sq.enqueueNotify(null);
                 }*/

        readQTable.notifyAllQueues();

        /**
         * For ack queues
         */
        /*enum = ackQTable.elements();
                 while ( enum.hasMoreElements() ) {
            sq = (SessionQueue) enum.nextElement();
            sq.enqueueNotify(null);
                 }*/

        ackQTable.notifyAllQueues();
    }

    /**
     * We always redirect the connection to the take over broker.
     * @param pkt ReadWritePacket
     */
    private void checkRedirectStatus(ReadWritePacket pkt) throws JMSException {

        if (connection.reconnecting) {

            try {

                Hashtable props = pkt.getProperties();
                int statusCode = ((Integer) props.get("JMQStatus")).intValue();

                if (statusCode == Status.MOVED_PERMANENTLY) {
                    connection.JMQStoreOwner = (String) props.get(
                        "JMQStoreOwner");
                    //protocolHandler.redirect( connection.JMQStoreOwner );
                    connection.initiator.setRedirectURL(connection.
                        JMQStoreOwner);
                    protocolHandler.close();
                    
                    String str = AdministeredObject.cr.getKString(ClientResources.I_MOVED_PERMANENTLY, connection.getLastContactedBrokerAddress(), connection.JMQStoreOwner);

                    ConnectionImpl.connectionLogger.log(Level.INFO, str);
                    
                    //we will reconnect when run() catches the exception.
                    JMSException jmse = new com.sun.messaging.jms.JMSException (str);
                    
                    ExceptionHandler.throwJMSException(jmse);

                } else if (statusCode == Status.TIMEOUT) {
                	
                	String str = AdministeredObject.cr.getKString(ClientResources.I_TIME_OUT, connection.getLastContactedBrokerAddress());
                	
                	ConnectionImpl.connectionLogger.log(Level.INFO, str);
                	
                    protocolHandler.close();

                    JMSException jmse =
                    new com.sun.messaging.jms.JMSException (str);
                    ExceptionHandler.throwJMSException(jmse);
                }

            } catch (JMSException jmse) {
                throw jmse;
            } catch (Exception e) {

                JMSException jmse =
                    new com.sun.messaging.jms.JMSException (e.toString());

                ExceptionHandler.throwJMSException(jmse);
            }

        }
    }

}
