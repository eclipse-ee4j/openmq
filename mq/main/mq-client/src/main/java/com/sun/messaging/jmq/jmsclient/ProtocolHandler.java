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
 *  %W% %G%
 */ 

/**
 * This class is the JMQ protocol handler for the JMQ 2.0 JMS client
 * implementation. This is the application level protocol handler.  The
 * transport layer protocol to be used is specified in the ConnectionFactory
 * and is instantiated during runtime.
 *
 * <p>The StreamHandlerFactory selects the transport protocol - StreamHandler
 * and uses StreamHandler.openConnection() to obtain the ConnectionHandler
 * object.  From that, it gets the Input/Output Stream to communicate with
 * the broker.
 */

package com.sun.messaging.jmq.jmsclient;

import javax.transaction.xa.*;
import javax.jms.*;

import java.net.*;
import java.io.*;
import java.util.Hashtable;
import java.util.logging.*;
import javax.security.auth.login.LoginException;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.JMQXid;
//import com.sun.messaging.jmq.auth.*;
import com.sun.messaging.jmq.auth.api.client.*;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.auth.UnsupportedAuthTypeException;
import com.sun.messaging.jmq.jmsclient.protocol.direct.DirectConnectionHandler;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.jmsclient.runtime.impl.BrokerInstanceImpl;
import com.sun.messaging.jmq.jmsclient.validation.ValidatorFactory;
import com.sun.messaging.jmq.jmsclient.validation.XMLValidator;

import java.util.Date;

//import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

public class ProtocolHandler {

    /**
     * if setClientID() is called, this is set to true.
     * The property "JMQBlock" in the GOODBYE packet will
     * set its value based on this flag.
     */
    private volatile boolean sentSetClientID = false;
    
    private volatile boolean hasConnectionConsumer = false;

    // iMQ Version class
    private static final Version version = com.sun.messaging.jmq.jmsclient.
                                           ConnectionImpl.version;

    //string used for key value to put to the hashtable.
    public static final String REQUEST_META_DATA = "requestMetaData";

    //acknowledge message body size. int interestID + SysMessageID
    public static final int ACK_MESSAGE_BODY_SIZE = 4 + SysMessageID.ID_SIZE;

    private static final int DIRECT_ACK_TIMEOUT = 60000;
    protected int timeout = 0; //default to 0.
    protected int pingTimeout = 0; //default to 0.

    //broker return code 
    public static final int SERVER_OK = 200;

    /**
     * Minimum ack ID.  starting from 1 to Long.MAX_VALUE.
     * Ack ID is now (from protocol 2.1) in different name space
     * from consumer ID *which is assigned from broker.
     */
    //XXX PROTOCOL2.1
    //public static final long MIN_ACK_ID = InterestTable.MAX_INTEREST_ID;
    public static final long MIN_ACK_ID = 0;
    //XXX PROTOCOL2.1
    private long nextAckID = MIN_ACK_ID;

    //The IP address of the local host.
    private byte[] ipAddress = null;
    //The IP address of the local host.
    private byte[] macAddress = null;
    //The local port number used by the current connection.
    private int localPort = 0;

    private ConnectionImpl connection = null;
    //private InterestTable interestTable = null;

    protected Hashtable requestMetaData = null;

    //private StreamHandler streamHandler = null;
    private ConnectionHandler connectionHandler = null;

    //The following three vars are used in writeJMSMessage method.
    //The rules for the producer to wait for ack from broker is as
    //explained below.
    //1. If -Dack=true, wait for ack from broker for each produced
    //2. If -Dack=false, do not wait for broker's ack.
    //3. If ack is not set, for persist messages, wait for ack.
    //   For non-persist messages, do not wait for ack.

    //to hold value set from system property
    private boolean ackEnabled = true;
    //flag to indicate if ackEnabled value is defined in system property
    private boolean ackEnabledFlag = false;
    //to hold value to indicate if the sending message require ack
    private boolean produceAck = false;

    private boolean debug = Debug.debug;

    private boolean isClosed = false;
    //private boolean initializing = false;

    //GT Only if this count is 0 should the START protocol message get sent
    //Session.rollback() and Session.recover() will increment and decrement this
    protected int stoppedCount = 0;
    //sync object for stops/starts
    private Object incObj = new Object();

    //flag to determine if require broker to ack back for
    //auto ack and client ack mode.
    private boolean ackAck = true;

    /** flag to indicate if connection is authenticated
     * This is used only in ReadChannel to determine
     * if error messages should be printed in the condition of
     * connection is broken and no exception listener is set.
     */
    protected boolean authenticated = false;

    /**
     * connection recover thread reference.  This is used to determine
     * if an operation is called from the recover thread. During recover,
     * all operations are blocked except from the recover thread.
     */
    protected Thread recoverThread = null;

    // for JMSX prop values
    private boolean setJMSXAppID = false;
    private boolean setJMSXUserID = false;
    //private boolean setJMSXProducerTXID = false;
    private boolean setJMSXRcvTimestamp = false;

    private String jmsxAppID = null;
    private String jmsxUserID = null;

    private static final String AUTHTYPE_JMQADMINKEY = "jmqadminkey";
    private static final String AUTHTYPE_JMQBASIC = "basic";
    private static final String AUTHTYPE_JMQDIGEST = "digest";

    /**
     * flag set to true by flow control thread.
     * flag set to false by any pkt traffic.
     */
    private boolean timeToPing = false;

    /**
     * non responsive ping time stamp
     */
    private long nonRespPingTimeStamp = 0;
    private Object nonResponsiveSyncObj = new Object();
    //private Object nextAckIDSyncObj = new Object();

    private boolean isPingTimeStampSet = false;

    /**
     * flag to instruct client runtime if abort on PING time out.
     */
    private boolean imqAbortOnPingAckTimeout = false;

    private boolean enableZip = Boolean.getBoolean("imq.zip.enable");

    //logging name for inbound packet logging
    public static final String INBOUND_PACKET_LOGGING_NAME =
        "com.sun.messaging.jms.pkt.in";

    //logging name for outbound packet logging.
    public static final String OUTBOUND_PACKET_LOGGING_NAME =
        "com.sun.messaging.jms.pkt.out";
    
    //pkt dump at connection level.  app set this flag to true to dump pkt.
    private boolean debugInboundPkt = false;
    
    private boolean debugOutboundPkt = false;
    
    private String pktFilter = null;
    
    /**
     * packet i/o logger
     */
    private static Logger inpktLogger = null;
    private static Logger outpktLogger = null;

    private Logger connLogger = ConnectionImpl.connectionLogger;

    //this flag is set to true if the connection is from a HA standalone client.
    //the START_TRANSACTION pkt will have the "JMQAutoRollback" property
    //set to NOT_PREPARED if the flag is set to true.
    protected boolean twoPhaseCommitFlag = false;
    
    //flag to turn off xml validation.
    private static boolean turnOffXMLValidation = Boolean.getBoolean("imq.xml.validation.disabled");
    
    //hold Destination/XMLValidator in the entry.
    private Hashtable xmlValidationTable = new Hashtable();
    
    private Object getNextAckIDMutex = new Object();
    
    //XXX PROTOCOL2.1
    private  Long getNextAckID() {

    	Long result;
    	synchronized (getNextAckIDMutex) {
    		nextAckID++;
    		if (nextAckID == Long.MAX_VALUE) {
    			nextAckID = MIN_ACK_ID + 1;
    		}

    		//XXX PROTOCOL2.1
    		result= Long.valueOf(nextAckID);
        }
    	return result;
    }

    //XXX PROTOCOL2.1
//private synchronized Long getNextAckID() {
//
//        nextAckID++;
//
//        if (nextAckID == Long.MAX_VALUE) {
//            nextAckID = MIN_ACK_ID + 1;
//        }
//        //XXX PROTOCOL2.1
//        return new Long(nextAckID);
//    }

    /**
     * set time to ping flag
     * @param pflag timeToPing flag.
     */
    protected void setTimeToPing(boolean pflag) {
        timeToPing = pflag;
    }

    /**
     * get time to ping flag.
     * @return timeToPing.
     */
    protected boolean getTimeToPing() {
        return timeToPing;
    }

    protected void setPingTimeStamp() {

        //only set ping timestamp if imqAckTimeout is set.
        //and only when it is not set yet.
        synchronized (nonResponsiveSyncObj) {

            isPingTimeStampSet = true;

            if (nonRespPingTimeStamp == 0) {
                nonRespPingTimeStamp = System.currentTimeMillis();

                if ( debug ) {
                    Debug.println("*** ping time stamp: " + nonRespPingTimeStamp);
                }

            } else {
                //the timestamp was set, check if we reached timeout.
                long currentTime = System.currentTimeMillis();
                long waitTime = currentTime - nonRespPingTimeStamp;
                if ( (waitTime) > pingTimeout ) {
                    //no response from broker time is longer than timeout.
                    //1. if imqAbortOnPingAckTimeout is set, abort this connection.

                    this.connection.readChannel.setBrokerNonResponsive();

                    if ( debug ) {
                        Debug.println("*** timeout on ping.  wait time: " + waitTime);
                    }
                }
            }
        }
    }

    protected void resetPingTimeStamp() {

        synchronized (nonResponsiveSyncObj) {

            isPingTimeStampSet = false;
            nonRespPingTimeStamp = 0;
            if ( debug ) {
                Debug.println("*** ping time stamp reset to 0 ...");
            }
        }
    }

    /**
     * Find local host's IP address and port for the current connection
     * connection.
     */
    private void findLocalHostIP() throws Exception {

        String useMac = System.getProperty("imq.useMac", "true");

        try {
            ipAddress = InetAddress.getLocalHost().getAddress();
            if (useMac.equalsIgnoreCase("true")) {
                macAddress = IPAddress.getRandomMac();
            }
            localPort = connectionHandler.getLocalPort();
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            //if can not get IP and port, set to 0.
            //We will get the values from server in HELLO_REPLY.
            ipAddress = null;
            localPort = 0;
        }
    }

    /**
     * Write packet to the output stream.  This Method deligate the write
     * operation to the ReadWritePacket.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    private void writePacketNoAck(ReadWritePacket pkt) throws JMSException {

        checkConnectionState(pkt);

        try {

            synchronized (this) {

                //set IP and port for SysMessageID
                if (macAddress == null) {
                    pkt.setIP(getIPAddress());
                } else {
                    pkt.setIP(getIPAddress(), getMacAddress());
                }
                pkt.setPort(getLocalPort());

                connectionHandler.writePacket(pkt);
                
                setTimeToPing(false);

                //debug
                if ( debugOutboundPkt ) {
                	Debug.matchAndPrintPacket(pkt, pktFilter, Debug.WRITING_PACKET);
                } else if (debug) {
                    Debug.println(new Date().toString() +
                                  " ---> writing packet: " + pkt);

                    Debug.printWritePacket(pkt);
                }

                if ( connLogger.isLoggable(Level.FINEST) ) {

                    //String msg = new Date().toString() +
                    //             " ---> writing packet: " +
                    //             pkt +
                    //             ", ConnectionID="+connection.getConnectionID();

                    //connLogger.log(Level.FINEST, msg);

                    Object params[] = new Object[2];
                    params[0] = pkt;
                    params[1] = connection;

                    connLogger.log(Level.FINEST, ClientResources.I_WRITE_PACKET, params);
                }

                //private logging
                if (outpktLogger.isLoggable(Level.FINEST)) {
                    outpktLogger.log(Level.FINEST, "sent packet ... " + pkt,
                                     pkt);
                }

            }

        } catch (Exception e) {
            ExceptionHandler.handleException(
                e, ClientResources.X_NET_WRITE_PACKET, true);
        }
    }

    /**
     * @param pkt the packet to write
     * @param expectedAckType the expected reply packet type
     *
     * @return the reply packet
     *
     * @exception JMSException if reply packet type not the expected type
     */
    private ReadOnlyPacket
        writePacketWithReply(ReadWritePacket pkt,
                             int expectedReplyType) throws JMSException {

    	ReadOnlyPacket ack = writePacketWithAck(pkt);
        
        //if (ack.getPacketType() != expectedReplyType) {

        //    if ( debug ) {
        //        Debug.println ("expected pkt type: " + expectedReplyType);
        //        Debug.println ("pkt type: " + ack.getPacketType());
        //    }

        //    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_NET_ACK);
        //    throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_NET_ACK);
        //}

        checkReplyType(ack, expectedReplyType);

        return ack;
    }

    /**
     * @param pkt the packet to write
     * @param expectedAckType the expected reply packet type
     * @param altExpectedAckType the alternative expected reply packet type
     *
     * @return the reply packet
     *
     * @exception JMSException if reply packet type not expected types
     */
    private ReadOnlyPacket
        writePacketWithReply(ReadWritePacket pkt,
                             int expectedReplyType,
                             int altExpectedReplyType) throws JMSException {
        ReadOnlyPacket ack = writePacketWithAck(pkt);
        int packetType = ack.getPacketType();
        if (packetType != expectedReplyType &&
            packetType != altExpectedReplyType) {
            if (debug) {
                Debug.println("expected pkt type: " + expectedReplyType);
                Debug.println("alt expected pkt type: " + altExpectedReplyType);
                Debug.println("pkt type: " + packetType);
            }
            String errorString = AdministeredObject.cr.getKString(
            		ClientResources.X_NET_ACK) +
                                 this.getUserBrokerInfo();
            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSException(errorString,
            		ClientResources.X_NET_ACK));
        }
        return ack;
    }

    /**
     * @param pkt the packet to write
     * @param expectedReplyType1 the first expected reply type
     * @param expectedReplyType2 the second expected reply type
     */
    private ReadOnlyPacket
        writePacketWithReply2(ReadWritePacket pkt,
                              int expectedReplyType1,
                              int expectedReplyType2) throws JMSException {
        ReadOnlyPacket ack = writePacketWithAck(pkt, true, expectedReplyType1);
        //int packetType = ack.getPacketType();
        if (ack.getPacketType() != expectedReplyType2) {

            if (debug) {
                Debug.println("expected pkt type: " + expectedReplyType2);
                Debug.println("pkt type: " + ack.getPacketType());
            }

            String errorString = AdministeredObject.cr.getKString(
            		ClientResources.X_NET_ACK) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSException(errorString,
            		ClientResources.X_NET_ACK));
        }

        return ack;
    }

    /**
     * Use this method when expected statusCode either OK or SERVER ERROR
     *
     * @param pkt the packet to write
     * @param expectedAckType the expected ack packet type
     *
     * @exception JMSException if ack packet statusCode != OK (ie. SERVER_ERROR)
     *                      or if ack packet type not expected type
     */
    private void
        writePacketWithAck(ReadWritePacket pkt,
                           int expectedAckType) throws JMSException {

        //int statusCode = writePacketWithAckStatus(pkt, expectedAckType);

        ReadOnlyPacket ack = this.writePacketWithReply(pkt, expectedAckType);
        int statusCode = getReplyStatus(ack);

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
    }

    /**
     * @param pkt the packet to write
     * @param expectedAckType the expected ack packet type
     *
     * @return ack status code
     *
     * @exception JMSException if ack packet type not expected type
     */
    /*private int
         writePacketWithAckStatus(ReadWritePacket pkt,
                        int expectedAckType) throws JMSException {
        int packetType = -1;
        int statusCode = -1;

        ReadOnlyPacket ack = writePacketWithAck(pkt);
        try {
            Hashtable ackProperties = ack.getProperties();
            Integer value = (Integer) ackProperties.get("JMQStatus");
            statusCode = value.intValue();
            packetType = ack.getPacketType();
        }
        catch (IOException e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_NET_ACK, true);
        }
        catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_NET_ACK, true);
        }
        if (packetType != expectedAckType) {
            if ( debug ) {
                Debug.println ("expected pkt type: " + expectedAckType);
     Debug.println ("statusCode: " +statusCode+"  pkt type: "+ packetType);
            }
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_NET_ACK);
            throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_NET_ACK);
        }
        return statusCode;
         }*/

    private ReadOnlyPacket
        writePacketWithAck(ReadWritePacket pkt) throws JMSException {
        return writePacketWithAck(pkt, false, -1);
    }

    /**
     * @param pkt the packet to write to broker
     * @param reply2 true if expect two reply packets from broker
     *               false if expect only one reply packet from broker
     * @param expectedAckType1 the first reply type expected if reply2 true
     *                         ignored if reply2 false,
     */
    //XXX REVISIT chiaming: Error handling if statusCode != 200
    private ReadOnlyPacket
        writePacketWithAck(ReadWritePacket pkt,
                           boolean reply2,
                           int expectedAckType1) throws JMSException {
        //ack packet
        ReadOnlyPacket ack = null;

        //get next available interest ID
        //XXX PROTOCOL2.1
        Long ackId = getNextAckID();
        //XXX PROTOCOL2.1
        pkt.setConsumerID(ackId.longValue());

        //set ack required
        pkt.setSendAcknowledge(true);
        
    	AckQueue tmpQ=null;
                
        // variant of twoThreadDirectMode in which replies are returned via a ThreadLocal
        // rather than using the AckQueue
    	// CR 6897721 always sent STOP_REPLY via the output queue
        boolean synchronousReply=isDirectModeTwoThreadWithSyncReplies() && (pkt.getPacketType()!=PacketType.STOP);        
        
        if (!synchronousReply) {
        	// prepare to get the reply via the reader thread
        	if (reply2) {
        		tmpQ = new AckQueue(true, 2);
        	} else {
        		tmpQ = new AckQueue(true, 1);
        	}
        	connection.addToAckQTable(ackId, tmpQ);
        }

        //add meta data, if any
        addMetaData(pkt);

        //write packet to broker
        writePacketNoAck(pkt);
        
        if (synchronousReply) {
        	// get reply from directly from a ThreadLocal
        	ack = (ReadOnlyPacket) ((DirectConnectionHandler)connectionHandler).fetchReply();
        } else {
	        // wait until the reply arrives via the reader thread 
        	// before we start waiting check whether the connection has been broken
	        if ((connection.connectionIsBroken || connection.recoverInProcess) &&
	            tmpQ.isEmpty()) {
	            ack = null;
	        } else {
	            ack = (ReadOnlyPacket) tmpQ.dequeueWait(connection, pkt, timeout);
	        }
        }

        if (reply2 && ack != null) {
            try {
                int statusCode = ((Integer) ack.getProperties().get("JMQStatus")).
                                 intValue();

                int packetType = ack.getPacketType();
                if (packetType != expectedAckType1) {
                    String errorString = AdministeredObject.cr.getKString(
                    		ClientResources.X_NET_ACK) +
                                         this.getUserBrokerInfo();
                    ExceptionHandler.throwJMSException (
                    new com.sun.messaging.jms.JMSException(errorString,
                    		ClientResources.X_NET_ACK));
                }

                //XXX REVISIT currently only HELLO use reply2
                if (packetType == PacketType.HELLO_REPLY) {

                    if (statusCode == Status.UNAVAILABLE) {
                        //XXX REVISIT any auto retry or leave to application  ?
                        String errorString = AdministeredObject.cr.getKString(
                        		ClientResources.
                            X_SERVER_UNAVAILABLE) +
                                             getUserBrokerInfo();
                        ExceptionHandler.throwJMSException (
                        new com.sun.messaging.jms.ResourceAllocationException
                        (errorString,ClientResources.X_SERVER_UNAVAILABLE));
                    } else if (statusCode == Status.TIMEOUT) {
                        String errorString =
                            AdministeredObject.cr.getKString(ClientResources.X_TAKE_OVER_IN_PROCESS) +
                            getUserBrokerInfo();

                        ExceptionHandler.throwJMSException (
                        new com.sun.messaging.jms.ResourceAllocationException
                        (errorString,ClientResources.X_TAKE_OVER_IN_PROCESS));

                    } else if (statusCode == Status.MOVED_PERMANENTLY) {
                        String errorString =
                            AdministeredObject.cr.getKString(ClientResources.X_MOVE_PERMANENTLY) +
                            getUserBrokerInfo();

                        ExceptionHandler.throwJMSException (
                        new com.sun.messaging.jms.ResourceAllocationException
                        (errorString,ClientResources.X_MOVE_PERMANENTLY));
                    }
                    
                    Long brokerSessionIDLong = ((Long) ack.getProperties().get("JMQBrokerSessionID"));
                    if (brokerSessionIDLong!=null){
                    	connection.setBrokerSessionID(brokerSessionIDLong.longValue());
                    }
                }

                if (statusCode != Status.OK) {
                    //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
                    //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
                    this.throwServerErrorException(ack);
                }

                // Now receive the second reply, if there is one  
                if (isDirectModeTwoThreadWithSyncReplies()){
                	// get reply from directly from a ThreadLocal
                	ack =(ReadOnlyPacket) ((DirectConnectionHandler)connectionHandler).fetchReply();
                } else {
        	        // wait until the reply arrives via the reader thread 
                	// before we start waiting check again whether the connection has been broken
                	if ((connection.connectionIsBroken || connection.recoverInProcess) && tmpQ.isEmpty()) {
                		ack = null;
                	} else {
                		//print pkt info and wait time if wait longer than 2 mins.
                		ack = (ReadOnlyPacket) tmpQ.dequeueWait(connection, pkt, timeout);
                	}
                }

            } catch (IOException e) {
                ExceptionHandler.handleException(e,
                		ClientResources.X_NET_ACK, true);
            } catch (ClassNotFoundException e) {
                ExceptionHandler.handleException(e,
                		ClientResources.X_NET_ACK, true);
            }
        }

        connection.removeFromAckQTable(ackId);
        if (ack == null) {
            String errorString = AdministeredObject.cr.getKString(
            		ClientResources.X_NET_ACK) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException(errorString,
            		ClientResources.X_NET_ACK));
        }

        return ack;
    }

    //JMS 2.0
    private ReadOnlyPacket
    writeJMSMessagePacketAsync(ReadWritePacket pkt, AsyncSendCallback asynccb)
    throws JMSException {

        ReadOnlyPacket ack = null;

        Long ackId = getNextAckID();
        pkt.setConsumerID(ackId.longValue());

        pkt.setSendAcknowledge(true);

        // variant of twoThreadDirectMode in which replies are returned via a ThreadLocal
        // rather than using the AckQueue
        // CR 6897721 always sent STOP_REPLY via the output queue
        boolean synchronousReply = isDirectModeTwoThreadWithSyncReplies();

        if (!synchronousReply) {
            requestMetaData.put(ackId, asynccb);
            asynccb.asyncSendStart();
        }
        writePacketNoAck(pkt);

        if (synchronousReply) {
            // get reply from directly from a ThreadLocal
            ack = (ReadOnlyPacket) ((DirectConnectionHandler)connectionHandler).fetchReply();
        }

        return ack;
    }

    //XXX PROTOCOL2.1
    protected void addMetaData(ReadWritePacket pkt) throws JMSException {

        int pktType = pkt.getPacketType();
        //add meta data tag if any
        if (pktType == PacketType.ADD_CONSUMER ||
            pktType == PacketType.BROWSE ||
            pktType == PacketType.ADD_PRODUCER) {

            try {
                Hashtable props = pkt.getProperties();
                //retrieve consumer object.  This was added in
                //writePacketWithAck() above.
                Object consumer = props.get(REQUEST_META_DATA);
                //remove from props.  broker does not need this
                props.remove(REQUEST_META_DATA);
                //XXX PROTOCOL2.1
                Long ackID = Long.valueOf(pkt.getConsumerID());
                //put to meta data table.  ReadChannel will use it.
                requestMetaData.put(ackID, consumer);
            } catch (IOException e) {
                ExceptionHandler.handleException(e,
                		ClientResources.X_NET_ACK, true);
            } catch (ClassNotFoundException e) {
                ExceptionHandler.handleException(e,
                		ClientResources.X_NET_ACK, true);
            }
        }
    }

    //private synchronized void init_begin() {
    //    initializing = true;
    //}

    //private synchronized void init_end() {
    //    initializing = false;
    //    notifyAll();
    //}

    private void checkConnectionState(ReadWritePacket pkt) throws JMSException {

        if (connection.imqReconnect && connection.reconnecting) {

            if ((Thread.currentThread() != this.recoverThread) &&
                (Thread.currentThread() !=
                 connection.readChannel.readChannelThread)) {
                connection.checkReconnecting(pkt);
            }

            /*synchronized ( this ) {
                while (initializing) {
                    try {
                        wait();
                    }
                    catch (Exception e) {}
                }
                         }*/
        }
    }

    /**
     * The protocol handler creates a connection on behave of the
     * Connection object.  An InputStream and OutputStream is created from
     * the ConnectionHandler connection.  ReadWritePacket/ReadOnlyPacket use
     * them to send and receive messages.
     *
     * @param host the target host to connect to.
     * @param port the port of the ConnectionHandler connects to.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    protected void init(boolean isReconnect) throws JMSException {

        /**
         * Reset to false so that reconnect behaves properly.
         */
        isClosed = false;

        try {


            // XXX PROTOCOL2.1
            // Improved reconnect and failover.
            if (isReconnect) {
                connectionHandler = connection.initiator.reconnect();
            } else {
                connectionHandler = connection.initiator.createConnection();
            }
            
            //set timeout for this connection
            setTimeout();
            
            if (isDirectModeTwoThread()){
            	//client thread is used by the broker            	
          		this.setAckAck(false);
           		this.enableWriteAcknowledge(false);
            }
            

            
            connectionHandler.configure(connection.getConfiguration());

            findLocalHostIP();

            //check if set JMSXAppID is required
            setJMSXAppID = connection.connectionMetaData.setJMSXAppID;
            if (setJMSXAppID) {
                jmsxAppID = InetAddress.getLocalHost().getHostAddress() + "-" +
                            getLocalPort() + "-" + System.currentTimeMillis();
            }

            setJMSXUserID = connection.connectionMetaData.setJMSXUserID;
            if (setJMSXUserID) {
                jmsxUserID = connection.getUserName();
            }

            setJMSXRcvTimestamp = connection.connectionMetaData.
                                  setJMSXRcvTimestamp;

            String prop1 = connection.getProperty("imqAbortOnPingAckTimeout", "false");
            String prop2 = connection.getProperty("imqAbortOnTimeout", "false");
            if ( "true".equals(prop1) || "true".equals(prop2) ) {
                this.imqAbortOnPingAckTimeout = true;
            }

            //set/reset time stamp
            nonRespPingTimeStamp = 0;
            isPingTimeStampSet = false;

            if (debug) {
                Debug.println("*** Connected to broker: " +
                              this.getUserBrokerInfo());
            }

        } catch (JMSException jmse) {
            throw jmse;
        } catch (Exception e) {
            //XXX BUG chiaming:
            //get IP and local port from the router if it is Applet.
            ExceptionHandler.handleException(
                e, ClientResources.X_CAUGHT_EXCEPTION, true);
        } finally {
            //init_end();
        }
    }

    public void hello(String name, String password) throws JMSException {
        hello(name, password, null);
    }

    /**
     * Send HELLO packet and wait for reply from the broker.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void hello(String name, String password, Long connectionID) throws
        JMSException {

        //init to false
        authenticated = false;

        ReadWritePacket pkt = new ReadWritePacket();

        //flow control
        Hashtable ht = new Hashtable(1);
        ht.put("JMQRBufferSize", Integer.valueOf(connection.flowControlMsgSize));
        //ZZZ: PacketType class MUST BE changed to indicate new protocol level.
        ht.put("JMQProtocolLevel",
               Integer.valueOf(connection.getBrokerProtocolLevel()));

        ht.put("JMQVersion", version.getProductVersion());
        ht.put("JMQUserAgent", version.getUserAgent());
        if (connection.isAdminKeyUsed()) {
            ht.put("JMQAuthType", AUTHTYPE_JMQADMINKEY);
        }

        String dp = System.getProperty("imqDestinationProvider");
        if (dp != null) {
            ht.put("JMQDestinationProvider", dp);
        }

        ht.put("JMQReconnectable", Boolean.valueOf (connection.imqReconnect));
        
        if (connectionID != null) {
            ht.put("JMQConnectionID", connectionID);
        }
        
        /**
         * Tell broker client will reconnect if connects to HA brokers.
         * This over ride JMQReconnectable property.
         */
        ht.put("JMQHAClient", Boolean.valueOf( connection.isHAEnabled()) );

        //if ( connection.isConnectedToHABroker && connection.reconnecting ) {

        if (connection.JMQClusterID != null) {
            ht.put("JMQClusterID", connection.JMQClusterID);
        }

        if (connection.JMQStoreSession != null) {
            ht.put("JMQStoreSession", connection.JMQStoreSession);
        }

        //}

        pkt.setProperties(ht);

        //packet type
        pkt.setPacketType(PacketType.HELLO);
        ReadOnlyPacket authReq = writePacketWithReply2(pkt,
            PacketType.HELLO_REPLY,
            PacketType.AUTHENTICATE_REQUEST);
        Integer status = null;
        try {
            status = (Integer) authReq.getProperties().get("JMQStatus");

        } catch (IOException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_PACKET_GET_PROPERTIES, true);

        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_PACKET_GET_PROPERTIES, true);
        }
        if (status != null) {
            if (status.intValue() == Status.FORBIDDEN) {

                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_FORBIDDEN) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.JMSSecurityException
                (errorString,ClientResources.X_FORBIDDEN));

            }
            if (status.intValue() == Status.UNAVAILABLE) {
                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_SERVER_UNAVAILABLE) +
                                     this.getUserBrokerInfo();
                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.ResourceAllocationException
                (errorString,ClientResources.X_SERVER_UNAVAILABLE));
            }

            //ConnectionRecover.hello() will retry if ReadChannel redirect the
            //connection successfully.

            if (status.intValue() != Status.OK) {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
                //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
                this.throwServerErrorException(authReq);
            }
        }

        authenticate(authReq, name, password);

        //set to true
        authenticated = true;

        if (debug) {
            Debug.println("got hello reply ...");

            //connection.printDebugState();

        }
    }

    private void
        authenticate(ReadOnlyPacket authRequest,
                     String name, String password) throws JMSException {
        String authType;
        byte[] reqData, resData;
        ReadWritePacket response;
        Hashtable properties;
        Hashtable authProperties = (Hashtable) connection.getConfiguration();
        AuthenticationProtocolHandler hd = null;
        ReadWritePacket request = (ReadWritePacket) authRequest;

        try {
            //String authinfo = name + "@" + connectionHandler.getBrokerAddress();

            authType = (String) request.getProperties().get("JMQAuthType");
            Boolean chanllenge = (Boolean) request.getProperties().get(
                "JMQChallenge");
            if (chanllenge != null && chanllenge.booleanValue()) {
                checkAdminKeyAuth(authType);
                hd = getAuthHandlerInstance(authType);
                hd.init(name, password, authProperties);
                connection.setAuthenticationHandler(hd);
            } else {
                hd = connection.getAuthenticationHandler();
            }
            if (hd == null) {
                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_AUTHSTATE_ILLEGAL) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.JMSSecurityException(
                    errorString, ClientResources.X_AUTHSTATE_ILLEGAL));
            }

            while (request.getPacketType() != PacketType.AUTHENTICATE_REPLY) {
                if (!hd.getType().equals(authType)) {

                    String errorString = AdministeredObject.cr.getKString(
                    		ClientResources.X_AUTHTYPE_MISMATCH,
                        hd.getType(), authType) +
                                         this.getUserBrokerInfo();

                    ExceptionHandler.throwJMSException (
                    new com.sun.messaging.jms.JMSSecurityException(
                    errorString, ClientResources.X_AUTHTYPE_MISMATCH));
                }

                reqData = request.getMessageBody();
                resData = hd.handleRequest(reqData, authRequest.getSequence());

                response = new ReadWritePacket();
                response.setPacketType(PacketType.AUTHENTICATE);
                properties = new Hashtable();
                properties.put("JMQAuthType", hd.getType());
                response.setProperties(properties);
                response.setMessageBody(resData);
                request = (ReadWritePacket) writePacketWithReply(response,
                    PacketType.AUTHENTICATE_REPLY,
                    PacketType.AUTHENTICATE_REQUEST);
                authType = (String) request.getProperties().get("JMQAuthType");

            } //while
            int statusCode = ((Integer) request.getProperties().get("JMQStatus")).
                             intValue();
            if (statusCode == Status.FORBIDDEN) {
                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_AUTHENTICATE_DENIED,
                    this.getUserBrokerInfo());

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.JMSSecurityException(
                    errorString, ClientResources.X_AUTHENTICATE_DENIED));
            }

            if (statusCode == Status.INVALID_LOGIN) {
                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_INVALID_LOGIN,
                    this.getUserBrokerInfo());

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.JMSSecurityException(
                    errorString, ClientResources.X_INVALID_LOGIN));
            }
            if (statusCode == Status.UNAVAILABLE) {
                String errorString = AdministeredObject.cr.getKString(
                		ClientResources.X_SERVER_UNAVAILABLE) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.ResourceAllocationException
                (errorString,ClientResources.X_SERVER_UNAVAILABLE));
            }
            if (statusCode != Status.OK) {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
                //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
                this.throwServerErrorException(request);
            }

        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_PACKET_GET_PROPERTIES, true);
        } catch (UnsupportedAuthTypeException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_CAUGHT_EXCEPTION);
        } catch (LoginException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_CAUGHT_EXCEPTION);
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
            		ClientResources.X_CAUGHT_EXCEPTION);
        }

    }

    private void checkAdminKeyAuth(String authType) throws JMSException {
        if (connection.isAdminKeyUsed() &&
            !authType.equals(AUTHTYPE_JMQADMINKEY)) {
            String errorString = AdministeredObject.cr.getKString(
            		ClientResources.X_AUTHTYPE_MISMATCH, AUTHTYPE_JMQADMINKEY,
                authType) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSSecurityException(errorString,
            		ClientResources.X_AUTHTYPE_MISMATCH));
        }
        if (!connection.isAdminKeyUsed() &&
            authType.equals(AUTHTYPE_JMQADMINKEY)) {
            JMSSecurityException e = new com.sun.messaging.jms.
                                     JMSSecurityException(authType +
                this.getUserBrokerInfo());
            e.setLinkedException(new UnsupportedAuthTypeException(authType));

            ExceptionHandler.throwJMSException (e);
        }
    }

    private AuthenticationProtocolHandler getAuthHandlerInstance(String
        authType) throws UnsupportedAuthTypeException {
        if (authType == null) {
            throw new UnsupportedAuthTypeException("authType is null" +
                this.getUserBrokerInfo());
        }
        if (authType.equals(AUTHTYPE_JMQBASIC)) {
            return new com.sun.messaging.jmq.auth.handlers.BasicAuthenticationHandler();
        }
        if (authType.equals(AUTHTYPE_JMQDIGEST)) {
            return new com.sun.messaging.jmq.auth.handlers.DigestAuthenticationHandler();
        }
        if (authType.equals(AUTHTYPE_JMQADMINKEY)) {
            return new com.sun.messaging.jmq.jmsclient.auth.
                JMQAdminKeyAuthenticationHandler();
        }

        String c = connection.getProperty("JMQAuthClass" + "." + authType, "");
        if (c == null || c.trim().equals("") ||
            c.trim().equals(AUTHTYPE_JMQADMINKEY)) {
            throw new UnsupportedAuthTypeException(authType + ": " + c +
                this.getUserBrokerInfo());
        }

        try {
            return (AuthenticationProtocolHandler) Class.forName(c).newInstance();
        } catch (Exception e) {

            ExceptionHandler.logCaughtException(e);

            throw new
                UnsupportedAuthTypeException(authType + " " + e.getMessage() +
                                             this.getUserBrokerInfo());
        }
    }

    /*
     * Method to send flow control pkt to the broker
     */
    //int resc = 0;
    public void resumeFlow(int maxMessages) throws JMSException {

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.RESUME_FLOW);

        /*
         * The following code should be uncommented if we have
         * dynamic flow control implemented.
         */
        //Hashtable ht = new Hashtable(1);
        //ht.put("JMQRBufferSize", new Integer (maxMessages) );
        //pkt.setProperties(ht);

        writePacketNoAck(pkt);
    }

    public void resumeConsumerFlow(Consumer consumer, int maxMessages) throws
        JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.RESUME_FLOW);

        Hashtable ht = new Hashtable(1);

        //XXX PROTOCOL3.5 --
        //Consumer flow control.
        if (debug && maxMessages == 0) {
            Debug.getPrintStream().println(
                "\n\n######## SENDING RESUME_FLOW WITH JMQSIZE = 0. (POTENTIAL PROBLEM) ########");
        }

        ht.put("JMQConsumerID", consumer.getInterestId());
        ht.put("JMQSize", Integer.valueOf(maxMessages));

        pkt.setProperties(ht);
        writePacketNoAck(pkt);
    }

    public void createMessageProducer(MessageProducerImpl producer) throws
        JMSException {
        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) producer.getDestination();
        createMessageProducer(producer, dest);
    }

    public void createMessageProducer(MessageProducerImpl producer,
                                      Destination destination) throws
        JMSException {
        createMessageProducer(producer, destination, false);
    }

    public void createMessageProducer(MessageProducerImpl producer,
                                      Destination destination, boolean isRetry)
                                      throws JMSException {
        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) destination;

        createDestination(dest);

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.ADD_PRODUCER);

        Hashtable ht = new Hashtable();
        ht.put("JMQDestination", dest.getName());

        //set dest type
        Integer destinationType = getDestinationType(dest);
        ht.put("JMQDestType", destinationType);

        //XXXPROTOCOL3.5 --
        //Add Session ID.
        ht.put("JMQSessionID", Long.valueOf(
            producer.getSession().getBrokerSessionID()));

        //Add producer meta data
        producer.addProducerDest = dest;
        ht.put(REQUEST_META_DATA, producer);

        pkt.setProperties(ht);

        ReadOnlyPacket reply = writePacketWithReply(pkt,
            PacketType.ADD_PRODUCER_REPLY);

        //XXX PROTOCOL3.5 --
        //Producer flow control.
        int statusCode = -1;
        //int jmqSize = -1;
        //long jmqBytes = -1;
        //long producerID = -1;

        try {
            Hashtable replyProps = reply.getProperties();
            statusCode = ((Integer) replyProps.get("JMQStatus")).intValue();

            //Integer jmqSizeProp = (Integer) replyProps.get("JMQSize");
            //if (jmqSizeProp != null)
            //jmqSize = jmqSizeProp.intValue();

            //Long jmqBytesProp = (Long) replyProps.get("JMQBytes");
            //if (jmqBytesProp != null)
            //jmqBytes = jmqBytesProp.longValue();

            //Long jmqpidprop = (Long) replyProps.get("JMQProducerID");
            //if (jmqpidprop != null)
            //producerID = jmqpidprop.longValue();
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }

        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_NOTFOUND, dest.getName()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.InvalidDestinationException
            (errorString,ClientResources.X_DESTINATION_NOTFOUND));
        }

        if (statusCode == Status.FORBIDDEN) {

            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_ADD_PRODUCER_DENIED, dest.getName()) +
                                 this.getUserBrokerInfo();
            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_ADD_PRODUCER_DENIED));
        }

        if (statusCode == Status.NOT_ALLOWED) {
            String destString = AdministeredObject.cr.getString((dest.isQueue() ?
                ClientResources.L_QUEUE : ClientResources.L_TOPIC));
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_PRODUCER_LIMIT_EXCEEDED,
                destString, dest.getName()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.ResourceAllocationException
            (errorString,ClientResources.X_DESTINATION_PRODUCER_LIMIT_EXCEEDED));
        }

        if (statusCode == Status.RETRY && !isRetry) {
            createMessageProducer(producer, destination, true);
            return;
        }

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(reply);
        }
        
        //XXX PROTOCOL3.5 --
        //Producer flow control.
        //producer.setProducerID(destination, producerID);

        //producer.setFlowLimit(producerID, jmqSize);
        //producer.setFlowBytesLimit(producerID, jmqBytes);

        if (debug) {
            Debug.println("got create producer reply ...");
        }
    }

    public void deleteMessageProducer(long producerID) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.DELETE_PRODUCER);

        Hashtable ht = new Hashtable(1);
        ht.put("JMQProducerID", Long.valueOf(producerID));
        pkt.setProperties(ht);

        //int statusCode = writePacketWithAckStatus(pkt,
        //    PacketType.DELETE_PRODUCER_REPLY);

        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DELETE_PRODUCER_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(
            //    AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString,
            //    AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }

        if (debug) {
            Debug.println("got delete producer reply ...");
        }
    }

    public void
        createDestination(Destination dest) throws JMSException {
        createDestination(dest, false);
    }

    /**
     * @param dest
     * @param isRetry if this is a retry call
     */
    private void
        createDestination(Destination dest, boolean isRetry) throws JMSException {
        com.sun.messaging.Destination destination = (com.sun.messaging.Destination) dest;

		if (destination.isTemporary()){
			if (destination instanceof TemporaryDestination){
	            TemporaryDestination tmpDest = (TemporaryDestination) destination;
	            
	            if (!tmpDest.checkSendCreateDest(destination,this.connection)){
	                return;
	            }
	        }
		}

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.CREATE_DESTINATION);

        Hashtable ht = new Hashtable(2);
        ht.put("JMQDestination", destination.getName());

        Integer destinationType = getDestinationType(destination);
        ht.put("JMQDestType", destinationType);

        pkt.setProperties(ht);

        //int statusCode = writePacketWithAckStatus(pkt, PacketType.CREATE_DESTINATION_REPLY);

        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.CREATE_DESTINATION_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_NOTFOUND,
                destination.getName()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.InvalidDestinationException
            (errorString,ClientResources.X_DESTINATION_NOTFOUND));
        }

        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_CREATE_DESTINATION_DENIED,
                destination.getName()) +
                                 this.getUserBrokerInfo();
            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_CREATE_DESTINATION_DENIED));
        }

        if (statusCode == Status.RETRY && !isRetry) {
            createDestination(destination, true);
            return;
        }

        if (statusCode != Status.OK && statusCode != Status.CONFLICT) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
        
        //set xml validation values, if any
        this.setXMLValidation(destination, ack);

        if (debug) {
            Debug.println("got create destination reply ...");
        }
        
    }

    //XXX chiaming REVISIT: error code.
    public void
        deleteDestination(Destination dest) throws JMSException {
        com.sun.messaging.Destination destination =
            (com.sun.messaging.Destination) dest;

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.DESTROY_DESTINATION);

        Hashtable ht = new Hashtable(2);
        ht.put("JMQDestination", destination.getName());

        Integer destinationType = getDestinationType(destination);
        ht.put("JMQDestType", destinationType);

        pkt.setProperties(ht);

        //int statusCode = writePacketWithAckStatus(pkt, PacketType.DESTROY_DESTINATION_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DESTROY_DESTINATION_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode != Status.OK) {
            /**
             * For temp destinations, the destination may not be there yet.
             */
            if (statusCode != Status.NOT_FOUND) {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
                //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
                this.throwServerErrorException(ack);
            }
        }

        if (debug) {
           Debug.println("got delete destination reply ...");
        }
    }

    public Integer
        getDestinationType(com.sun.messaging.Destination destination) {

        int type = 0;
        if (destination.isQueue()) {
            type = DestType.DEST_TYPE_QUEUE;
        } else {
            type = DestType.DEST_TYPE_TOPIC;
        }

        if (destination.isTemporary()) {
            type |= DestType.DEST_TEMP;
        }

        return Integer.valueOf(type);
    }

    protected void getIPFromPacket(ReadOnlyPacket rpkt) throws JMSException {
        try {
            Hashtable properties = rpkt.getProperties();
            //get my ip address from packet - assigned by server.
            Object value = properties.get("JMQIPAddr");
            ipAddress = (byte[]) value;

            //get my local port from packet - assigned by server
            value = properties.get("JMQPort");
            localPort = ((Integer) value).intValue();
        } catch (Exception e) {
            ExceptionHandler.handleException(
                e, ClientResources.X_PACKET_GET_PROPERTIES, true);
        }
    }

    /**
     * The constructor.
     *
     * @param connection the current Connection object.
     *
     */
    public ProtocolHandler(ConnectionImpl connection) throws JMSException {
        this.connection = connection;
        //this.interestTable = connection.interestTable;
        this.requestMetaData = connection.requestMetaData;

        inpktLogger = Logger.getLogger(INBOUND_PACKET_LOGGING_NAME);
        outpktLogger = Logger.getLogger(OUTBOUND_PACKET_LOGGING_NAME);

        init(false);
    }

    /**
     * Enable reliable delivery.  Each call requires ack from the broker.
     * If enabled, non-persistent messages also requirs acks from the broker.
     */
    public void enableWriteAcknowledge(boolean state) {
        ackEnabled = state;
        ackEnabledFlag = true;

        if (debug) {
            Debug.println("Producer ack required: " + ackEnabled);
        }

    }

    public boolean getAckEnabled() {
        return ackEnabled;
    }

    /**
     * For auto ack and client ack mode, each ack requires broker to ack back
     * so that we are sure the message will not be redelievred if broker
     * crashes during ack.
     */
    public void setAckAck(boolean state) {
        ackAck = state;

        if (debug) {
            Debug.println("Auto/Client acknowledge require ack from broker: " +
                          ackAck);
        }

    }

    public boolean getAckAck() {
        return ackAck;
    }

    /**
     * set timeout from connection configuration
     */
    protected void setTimeout() {
        String prop = connection.getTrimmedProperty(ConnectionConfiguration.imqAckTimeout);
        if (prop != null) {
            timeout = Integer.parseInt(prop);
        }
        
        if (isDirectMode()){
        	// set the default timeout to 60 secs for direct mode connections.
        	if (timeout == 0) {
        		timeout = DIRECT_ACK_TIMEOUT;
        	}
        }

        prop = connection.getTrimmedProperty(ConnectionConfiguration.imqPingAckTimeout);
        if (prop != null) {
            pingTimeout = Integer.parseInt(prop);
        }

        if (debug) {
            Debug.println("Ack timeout: " + timeout);
            Debug.println("Ping Ack timeout: " + pingTimeout);
        }
    }

    public int getPingAckTimeout() {
        return pingTimeout;
    }

    /**
     * Get the IP address of the local host.
     *
     * @return the IP address of the local host.
     */
    public byte[] getIPAddress() {
        return ipAddress;
    }

    /**
     * Get the Mac address of the local host.
     *
     * @return the Mac address of the local host.
     */
    public byte[] getMacAddress() {
        return macAddress;
    }

    /**
     * Get the local port number used by the current ConnectionHandler connection.
     *
     * @return the local port number used by the current ConnectionHandler connection.
     */
    public int getLocalPort() {
        return localPort;
    }

    public void incStoppedCount() {
        synchronized (incObj) {
            stoppedCount += 1;
        }
    }

    public void decStoppedCount() {
        synchronized (incObj) {
            stoppedCount -= 1;
        }
    }

    public int getStoppedCount() {
        synchronized (incObj) {
            return stoppedCount;
        }
    }

    /**
     * Send START packet to the broker to indicate this client is ready to
     * receive messages.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void start() throws JMSException {

        try {
            //GT
            synchronized (incObj) {
                if (stoppedCount == 0) {
                    ReadWritePacket pkt = new ReadWritePacket();
                    pkt.setPacketType(PacketType.START);

                    writePacketNoAck(pkt);
                }
            }
        } catch (Exception e) {
            ExceptionHandler.handleException(
                e, ClientResources.X_NET_WRITE_PACKET, true);
        }
    }

    public void resumeSession(long brokerSessionID) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.START);

        Hashtable props = new Hashtable(1);
        props.put("JMQSessionID", Long.valueOf(brokerSessionID));
        pkt.setProperties(props);
        writePacketNoAck(pkt);
    }

    /**
     * Send STOP packet to the broker to stop deliver messages to this
     * connection.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void stop() throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.STOP);

        writePacketWithAck(pkt, PacketType.STOP_REPLY);
    }

    /**
     * Send STOP packet to the broker to stop delivery of messages to
     * the specified session.
     */
    public void stopSession(long brokerSessionID) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.STOP);

        Hashtable props = new Hashtable(1);
        props.put("JMQSessionID", Long.valueOf(brokerSessionID));
        pkt.setProperties(props);
        writePacketWithAck(pkt, PacketType.STOP_REPLY);
    }

    /**
     * Send GOODBYE packet to the broker to indicate this client is closing
     * connection.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void goodBye(boolean reply) throws JMSException {
        try {
            ReadWritePacket pkt = new ReadWritePacket();
            pkt.setPacketType(PacketType.GOODBYE);

            //XXX PROTOCOL2.1
            Hashtable props = new Hashtable();

            /**
             * get JMQBlock property
             */

            //props.put("JMQBlock", new Boolean(sentSetClientID));
            boolean jmqblock = sentSetClientID || hasConnectionConsumer;
            props.put("JMQBlock", Boolean.valueOf (jmqblock));

            props.put("JMQConnectionID", connection.connectionID);
            pkt.setProperties(props);

            if (reply) {
                //need goodbye reply so that we know broker has
                //finished processing our request.
                writePacketWithAck(pkt, PacketType.GOODBYE_REPLY);
//LKS System.err.println("got goodbye reply");
            } else {
                writePacketNoAck(pkt);
            }
        } catch (Exception e) {
            ExceptionHandler.handleException(
                e, ClientResources.X_NET_WRITE_PACKET, true);
        }
    }

    /**
     * Read packet from the input stream. This method deligate the read
     * operation to the ReadWritePacket object.
     *
     * @return ReadWritePacket read from the input stream.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public ReadWritePacket readPacket() throws JMSException {

        ReadWritePacket pkt = null;

        try {
        	pkt = connectionHandler.readPacket();

        	setTimeToPing(false);

            //reset ping
            if ( isPingTimeStampSet ) {
               resetPingTimeStamp();
            }

            //connection debug has higher priority
            if (debugInboundPkt) {
            	Debug.matchAndPrintPacket(pkt, pktFilter, Debug.READING_PACKET);
            } else if (debug) {
                Debug.println(new Date().toString()
                              + " <--- read packet: " + pkt );

                Debug.printReadPacket(pkt);
            }

            if ( connLogger.isLoggable(Level.FINEST) ) {

                Object params[] = new Object[2];
                params[0] = pkt;
                params[1] = connection;

                connLogger.log(Level.FINEST, ClientResources.I_READ_PACKET, params);
            }

            if (inpktLogger.isLoggable(Level.FINEST)) {
                inpktLogger.log(Level.FINEST, "read packet ... " + pkt, pkt);
            }
            
        } catch (Exception e) {

            if (isClosed == false) {
                ExceptionHandler.handleException (
                    e, ClientResources.X_NET_READ_PACKET, true);
            } else {
                //internal exception.  this is caught and handled by readchannel.
                throw new JMSException ("ProtocolHandler is closed");
            }

            //pkt = null;
        }

        return pkt;
    }

    /**
     * Write JMS message to the output stream.
     * Message packet type was set when Message was constructed.
     */
    protected void writeJMSMessage(Message message, AsyncSendCallback asynccb)
    throws JMSException {
        ReadWritePacket pkt = null;
        MessageImpl messageImpl = null;

        messageImpl = (MessageImpl) message;
        long jmsExpiration = message.getJMSExpiration();
        //set JMS expiration value.
        if (jmsExpiration != 0) {
            jmsExpiration = jmsExpiration + System.currentTimeMillis();
            message.setJMSExpiration(jmsExpiration);
        }

        long jmsDeliveryTime = message.getJMSDeliveryTime();
        //set JMS delivery delay value.
        if (jmsDeliveryTime != 0L) {
            jmsDeliveryTime = jmsDeliveryTime + System.currentTimeMillis();
            message.setJMSDeliveryTime(jmsDeliveryTime);
        }

        //set JMSXAppID if requested
        if (setJMSXAppID) {
            message.setStringProperty(ConnectionMetaDataImpl.JMSXAppID,
                                      jmsxAppID);
        }

        //set JMSXUserID if requested
        if (setJMSXUserID) {
            message.setStringProperty(ConnectionMetaDataImpl.JMSXUserID,
                                      jmsxUserID);
        }

        //convert message type to byte[]
        messageImpl.setMessageBodyToPacket();

        //if enable zip all messages, compress the message
        if (enableZip) {
            messageImpl.compress();
        } else if (messageImpl.shouldCompress) {
            //if message JMS_SUN_COMPRESS is set in the prop, zip it.
            messageImpl.compress();
        } else {
            //clear the bit.
            messageImpl.getPacket().setFlag(PacketFlag.Z_FLAG, false);
        }

        //properties are set AFTER message is compressed.
        messageImpl.setPropertiesToPacket();

        messageImpl.resetJMSMessageID();
        pkt = messageImpl.getPacket();

        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) messageImpl.getJMSDestination();
        
        //xml validation
        if (this.xmlValidationTable.containsKey(dest)) {
            if (pkt.getPacketType() == PacketType.TEXT_MESSAGE) {
                //do validation here
                if (debug) {
                    Debug.println("*** Validating xml message ....");
                }
                XMLValidator validator = (XMLValidator) this.xmlValidationTable.get(dest);
                String xml = ((TextMessage) message).getText();
                validator.validate(xml);
                if (debug) {
                    Debug.println("*** xml message validated against xsd at URI: " + validator.getURIList());
                }
            }
        } else {
            if (debug) {
                Debug.println("***** no validation for message ... on dest: " + dest.getName() );
            }
        }

        //set destination name
        pkt.setDestination(dest.getName());
        //set dest class name
        pkt.setDestinationClass(dest.getClass().getName());
        //set destination bit
        pkt.setIsQueue(dest.isQueue());

        //set jms reply to
        if (message.getJMSReplyTo() != null) {
            com.sun.messaging.Destination replyTo =
                (com.sun.messaging.Destination) message.getJMSReplyTo();

            pkt.setReplyTo(replyTo.getName());
            pkt.setReplyToClass(replyTo.getClass().getName());
            //pkt.setIsQueue( replyTo.isQueue() );
        }

        if (asynccb == null) {

        //block to decide if require ack from broker
        if (ackEnabledFlag) {
            //if set, use the value set
            produceAck = ackEnabled;
        } else { //if not set
            //persist default is true
            if (message.getJMSDeliveryMode() == DeliveryMode.PERSISTENT) {
                produceAck = true;
                if(pkt.getTransactionID()!=0 && SessionImpl.noBlockUntilTxnCompletes)
            	{
                	// If we are in a transaction and optimisation flag is set,
                	// then do not block waiting for an ack.
                	// Transactional reliability should be ensured by blocking 
                	// on commit until ack received.
            		produceAck = false;
            	}
            } else {
                //non-persist default is false
                produceAck = false;
            }
        }
        }

        if (asynccb != null) { 
            ReadOnlyPacket ack = writeJMSMessagePacketAsync(pkt, asynccb);
            if (ack != null) {
                int statusCode = this.getReplyStatus(ack);
                if (statusCode != Status.OK) {
                    try {
                        checkWriteJMSMessageStatus(statusCode, dest, ack, this);
                    } catch (JMSException e) {
                        asynccb.processException(e);
                        throw e;
                    }
                }
                asynccb.processCompletion(ack, false);
            }

        } else if (produceAck) {
            //wait for ack status
            //int statusCode = writePacketWithAckStatus(pkt, PacketType.SEND_REPLY);
            ReadOnlyPacket ack = this.writePacketWithReply(pkt,
                PacketType.SEND_REPLY);
            int statusCode = this.getReplyStatus(ack);

            //if (statusCode == Status.FORBIDDEN) {
            //    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SEND_DENIED, dest.getName());
            //    throw new com.sun.messaging.jms.JMSSecurityException(errorString, AdministeredObject.cr.X_SEND_DENIED);
            //}

            if (statusCode != Status.OK) {
                checkWriteJMSMessageStatus(statusCode, dest, ack, this);
            }

            //if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            //}
        } else {
            //set no ack flag
            pkt.setSendAcknowledge(false);
            writePacketNoAck(pkt);
        }
    }

    protected static void checkWriteJMSMessageStatus(
        int statusCode, com.sun.messaging.Destination dest,
        ReadOnlyPacket ack, ProtocolHandler ph)
        throws JMSException { 

        if (statusCode == Status.OK) {
            return;
        }
        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.
                       getKString(ClientResources.X_SEND_DENIED, dest.getName())+
                       getUserBrokerInfo(ph);
            ExceptionHandler.throwJMSException(
                       new com.sun.messaging.jms.JMSSecurityException(
                            errorString, ClientResources.X_SEND_DENIED));

        } else if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.
                       getKString(ClientResources.X_SEND_NOT_FOUND, dest.getName())+
                       getUserBrokerInfo(ph);
            ExceptionHandler.throwJMSException(
                       new com.sun.messaging.jms.JMSException(errorString,
                           ClientResources.X_SEND_NOT_FOUND));
        } else if (statusCode == Status.ENTITY_TOO_LARGE) {
            String errorString = AdministeredObject.cr.
                       getKString(ClientResources.X_SEND_TOO_LARGE, dest.getName())+
                       getUserBrokerInfo(ph);
            ExceptionHandler.throwJMSException(
                        new com.sun.messaging.jms.JMSException(errorString,
                            ClientResources.X_SEND_TOO_LARGE));
        } else if (statusCode == Status.RESOURCE_FULL) {
            String errorString = AdministeredObject.cr.
                       getKString(ClientResources.X_SEND_RESOURCE_FULL, dest.getName())+
                       getUserBrokerInfo(ph);
            ExceptionHandler.throwJMSException (
                        new com.sun.messaging.jms.ResourceAllocationException(
                            errorString, ClientResources.X_SEND_RESOURCE_FULL));
        } else {
            throwServerErrorException(ack, ph);
        }
    }

    /**
     * Convert ReadOnlyPacket to MessageImpl.
     * Called by SessionReader when receiving messages.
     *
     * @param pkt the packet to be convertd to MessageImpl.
     *
     * @return MessageImpl object converted from ReadOnlyPacket.
     */
    public MessageImpl getJMSMessage(ReadOnlyPacket pkt) throws JMSException {

        MessageImpl message = null;

        switch (pkt.getPacketType()) {
        case PacketType.TEXT_MESSAGE:
            message = new TextMessageImpl();
            break;
        case PacketType.BYTES_MESSAGE:
            message = new BytesMessageImpl();
            break;
        case PacketType.STREAM_MESSAGE:
            message = new StreamMessageImpl();
            break;
        case PacketType.MAP_MESSAGE:
            message = new MapMessageImpl();
            break;
        case PacketType.OBJECT_MESSAGE:
            message = new ObjectMessageImpl();
            break;
        case PacketType.MESSAGE:
            message = new MessageImpl();
            break;
        default:
            throw new com.sun.messaging.jms.JMSException("not implemented.");
        }

        ((ReadWritePacket) pkt).setTransactionID(0L);
        message.setPacket((ReadWritePacket) pkt);

        //ZZZ -- the order of statement now matters -- compression requires
        //props in order to do the unzip work.
        //getPropertiesFromPacket MUST preceed getMessageBodyFromPacket.
        message.getPropertiesFromPacket();
        message.getMessageBodyFromPacket();

        //check if setJMSXRcvTimestamp is requested
        if (setJMSXRcvTimestamp) {
            message.setStringProperty(ConnectionMetaDataImpl.JMSXRcvTimestamp,
                                      String.valueOf(System.currentTimeMillis()));
        }
        message.setIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount,
                               pkt.getDeliveryCount());

        message.setMessageReadMode(true);
        message.setPropertiesReadMode(true);

        //set message ID for acknowledgement
        //MID is cloned in the setAckID() method.
        message.setMessageID(pkt.getSysMessageID());
        //XXX PROTOCOL2.1
        message.setInterestID(pkt.getConsumerID());

        return message;
    }

    /**
     * Create a new ReadWritePacket to use.
     *
     * @return a new instance of ReadWritePacket.
     */
    public ReadWritePacket createReadWritePacket() {
        return new ReadWritePacket();
    }

    /**
     * Get the interest id if the packet.
     *
     * @param pkt the packet from whick the interest id to be obtained.
     *
     * @return the interest id of the packet.
     */
    //XXX PROTOCOL2.1
    public long getInterestId(ReadWritePacket pkt) {
        return pkt.getConsumerID();
    }

    public void resetClientID() throws JMSException {
        if (sentSetClientID) {
            setClientID(connection.clientID);
        }
    }

    /**
     * Clear client ID for this con.
     */
    public void
        unsetClientID() throws JMSException {
        //System.out.println("PH:unsetClientID()");
        ReadWritePacket pkt = new ReadWritePacket();
        //set packet type
        pkt.setPacketType(PacketType.SET_CLIENTID);
        Hashtable props = new Hashtable();
        
        if (connection.useNamespace()) {
            props.put("JMQNamespace", connection.getRANamespaceUID());
        }
        
        pkt.setProperties(props);
        //int statusCode = writePacketWithAckStatus(pkt, PacketType.SET_CLIENTID_REPLY);

        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.SET_CLIENTID_REPLY);
        int statusCode = this.getReplyStatus(ack);

        //check status code
        if (statusCode != Status.OK) {
            //System.out.println("PH:unsetClientID:Throwing Exc:statusCode="+statusCode);
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
        sentSetClientID = false;
        //System.out.println("PH:unsetClientID:done");
    }

    /**
     * Set client ID.
     * @param clientID the client ID to be verified by the broker.
     */
    public void setClientID(String clientID) throws JMSException {
        //System.out.println("PH:setClientID()");
        Hashtable props = new Hashtable();
        ReadWritePacket pkt = new ReadWritePacket();
        //set packet type
        pkt.setPacketType(PacketType.SET_CLIENTID);

        props.put("JMQClientID", clientID);

        if (connection.useNamespace()) {
            //System.out.println("PH:setClientID:usingNS:raUID="+connection.getRANamespaceUID());
            props.put("JMQNamespace", connection.getRANamespaceUID());
        }

        props.put("JMQShare", Boolean.valueOf (connection.imqEnableSharedClientID));
        
        //set properties to the packet
        pkt.setProperties(props);

        //int statusCode = writePacketWithAckStatus(pkt, PacketType.SET_CLIENTID_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.SET_CLIENTID_REPLY);
        int statusCode = this.getReplyStatus(ack);

        //check status code
        if (statusCode != Status.OK) {
            if (statusCode == Status.CONFLICT) {
                String errorString = AdministeredObject.cr.getKString(
                    ClientResources.X_CLIENT_ID_INUSE, clientID) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.InvalidClientIDException(
                    errorString, ClientResources.X_CLIENT_ID_INUSE));
            } else if (statusCode == Status.BAD_REQUEST) {
                String errorString = AdministeredObject.cr.getKString(
                    ClientResources.X_SET_CLIENTID_INVALID, clientID) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.InvalidClientIDException(
                    errorString, ClientResources.X_SET_CLIENTID_INVALID));
            } else if (statusCode == Status.TIMEOUT) {
                String errorString = AdministeredObject.cr.getKString(
                    ClientResources.X_SET_CLIENTID_TIMEOUT, clientID, this) +
                                     this.getUserBrokerInfo();

                ExceptionHandler.throwJMSException (
                new com.sun.messaging.jms.JMSException(
                    errorString, ClientResources.X_SET_CLIENTID_TIMEOUT));
            } else {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
                //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
                this.throwServerErrorException(ack);
            }
        }
        sentSetClientID = true;
        //System.out.println("PH:setClientID:done");
    }

    /**
     */
    public void requestConsumerInfo(com.sun.messaging.Destination dest, boolean off) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.INFO_REQUEST);
        Hashtable props = new Hashtable();

        props.put("JMQDestination", dest.getName());
        props.put("JMQDestType", getDestinationType(dest));
        props.put("JMQRequestType", Integer.valueOf(3));
        props.put("JMQRequestOff", Boolean.valueOf(off));
        
        pkt.setProperties(props);
        writePacketNoAck(pkt);
    }

    /**
     * Register interest to the broker.
     *
     * @param consumer the message consumer to be registered.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void addInterest(Consumer consumer) throws JMSException {
        addInterest(consumer, false);
    }

    /**
     * @param consumer
     * @param isRetry if this is a retry call
     */
    private void addInterest(Consumer consumer, boolean isRetry) throws JMSException {
        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) consumer.getDestination();
        //check if the destination is valid ...
        createDestination(dest);

        //add interest to broker
        Hashtable props = new Hashtable();
        ReadWritePacket pkt = new ReadWritePacket();

        //set packet type
        pkt.setPacketType(PacketType.ADD_CONSUMER);

        //set durable flag
        if (consumer.getDurable() == true) {
            props.put("JMQDurableName", consumer.getDurableName());
        }

        //set correct share flag
        if (consumer.getShared()) { //JMS2.0
            if (connection.getBrokerProtocolLevel() < PacketType.VERSION500) {
                String errorString = AdministeredObject.cr.getKString(
                       ClientResources.X_BROKER_JMS2_SHARED_SUB_NO_SUPPORT,
                       connection.getBrokerVersion());
                JMSException jmse = new com.sun.messaging.jms.JMSException(
                       errorString,
                       ClientResources.X_BROKER_JMS2_SHARED_SUB_NO_SUPPORT);
                ExceptionHandler.throwJMSException(jmse);
            }
            props.put("JMQJMSShare", Boolean.valueOf(consumer.getShared()));
            if (consumer.getSharedSubscriptionName() != null) {
                props.put("JMQSharedSubscriptionName", consumer.getSharedSubscriptionName());
            }
        } else { 
            if (consumer.getDurable()) {
                props.put("JMQShare",
                    Boolean.valueOf(connection.imqEnableSharedClientID));
            } else {
                props.put("JMQShare",
                    Boolean.valueOf(connection.imqEnableSharedSubscriptions && 
                                    (!dest.isTemporary())));
            }
	}

        props.put("JMQDestination", dest.getName());
        //set dest type
        Integer destinationType = getDestinationType(dest);
        props.put("JMQDestType", destinationType);

        //XXX PROTOCOL2.1 - get consumer ID from broker.
        //props.put ("JMQConsumerID", consumer.getInterestId());

        String selector = consumer.getMessageSelector();
        if (selector != null && selector.trim().length() == 0) {
            selector = null;
        }
        if (selector != null) {
            props.put("JMQSelector", selector);
        }
        //set client ID and no local flag
        props.put("JMQNoLocal", Boolean.valueOf (consumer.getNoLocal()));

        //reconnect flag  - false
        //props.put("JMQReconnect", new Boolean(false));
        props.put("JMQReconnect", Boolean.FALSE);

        /**
         * acknowledge mode - set this only for auto/client/dups_ok ack mode.
         * Not for transacted session and Connection consumer.
         */
        if (consumer.acknowledgeMode > 0) {
            props.put("JMQAckMode", Integer.valueOf(consumer.acknowledgeMode));
        }

        //XXX PROTOCOL2.1 --
        //this will be used in addMetaData and then removed.
        props.put(REQUEST_META_DATA, consumer);

        //XXX PROTOCOL3.5 --
        //Consumer flow control.
        props.put("JMQSize", Integer.valueOf(consumer.getPrefetchMaxMsgCount()));

        //XXX PROTOCOL3.5 --
        // Session support
        if (consumer instanceof MessageConsumerImpl) {
            props.put("JMQSessionID", Long.valueOf(
                ((MessageConsumerImpl) consumer).getSession().
                getBrokerSessionID()));
        } else if ( consumer instanceof ConnectionConsumerImpl ) {
        	this.hasConnectionConsumer = true;
        }
        
        if ( consumer.getInterestId() != null ) {
        	props.put("JMQOldConsumerID", consumer.getInterestId());
        }

        //set properties to the packet
        pkt.setProperties(props);

        ReadOnlyPacket reply = writePacketWithReply(pkt,
            PacketType.ADD_CONSUMER_REPLY);

        int statusCode = -1;
        String reason = null;
        try {
            Hashtable replyProps = reply.getProperties();
            statusCode = ((Integer) replyProps.get("JMQStatus")).intValue();
            reason = (String)replyProps.get("JMQReason");
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }

        if (statusCode == Status.BAD_REQUEST && consumer.getMessageSelector() != null) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_SELECTOR_INVALID,
                consumer.getMessageSelector()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.InvalidSelectorException
            (errorString,ClientResources.X_SELECTOR_INVALID));
        }

        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_NOTFOUND,
                dest.getName()) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_DESTINATION_NOTFOUND));
        }
        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_ADD_CONSUMER_DENIED,
                dest.getName()) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_ADD_CONSUMER_DENIED));
        }
        if (statusCode == Status.NOT_ALLOWED) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TEMP_DESTINATION_INVALID,
                dest.getName()) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.JMSException(errorString,
                ClientResources.X_TEMP_DESTINATION_INVALID));
        }

        if (statusCode == Status.CONFLICT) {
            String destString = AdministeredObject.cr.getString((dest.isQueue() ?
                ClientResources.L_QUEUE : ClientResources.L_TOPIC));
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_CONSUMER_LIMIT_EXCEEDED,
                destString, dest.getName()) +this.getUserBrokerInfo()+
                (reason == null ? "":"["+reason+"]");

            ExceptionHandler.throwJMSException (
            new com.sun.messaging.jms.ResourceAllocationException(errorString,
            ClientResources.X_DESTINATION_CONSUMER_LIMIT_EXCEEDED));
        }

        if (statusCode == Status.RETRY && !isRetry) {
            addInterest(consumer, true);
            return;
        }

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(reply);
        }

        if (debug) {
            Debug.println("added interest, JMQConsumerID: " +
                          consumer.getInterestId());
        }
    }

    /**
     * Remove a registered interest from the broker.
     *
     * Closing a message consumer will cause the client to send DELETE_CONSUMER
     * packet to the broker.
     *
     * @param consumer the message consumer to be removed from the interest
     *                 list.
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void removeInterest(Consumer consumer) throws JMSException {
        Hashtable props = new Hashtable();
        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.DELETE_CONSUMER);

        //set durable info
        //if ( consumer.getDurable() == true ) {
        //    props.put("JMQDurableName", consumer.getDurableName());
        //}

        //set interest ID
        props.put("JMQConsumerID", consumer.getInterestId());
        //XXX PROTOCOL2.1
        props.put("JMQBlock", Boolean.TRUE);
            
        //XXX PROTOCOL3.5
        // Add last delivered SysMessageID as body and set the
        // JMQBodyType property.
        if (consumer instanceof MessageConsumerImpl) {
            SysMessageID lastID =
                ((MessageConsumerImpl) consumer).getLastDeliveredID();
            boolean lastIDInTransaction =
                ((MessageConsumerImpl) consumer).getLastDeliveredIDInTransaction();
            if (lastID != null) {
                ByteArrayOutputStream bos =
                    new ByteArrayOutputStream(ACK_MESSAGE_BODY_SIZE);
                DataOutputStream dos = new DataOutputStream(bos);

                try {
                    lastID.writeID(dos);
                    dos.flush();
                    byte[] lastIDBody = bos.toByteArray();
                    dos.close();
                    bos.close();

                    pkt.setMessageBody(lastIDBody);
                    props.put("JMQBodyType",
                              Integer.valueOf(PacketType.SYSMESSAGEID));
                    if (lastIDInTransaction) {
                        props.put("JMQLastDeliveredIDInTransaction", Boolean.valueOf(true));
                    }
                } catch (IOException ioe) {
                    ExceptionHandler.handleException(ioe,
                        ClientResources.X_CAUGHT_EXCEPTION);
                }
            } else {
                props.put("JMQBodyType", Integer.valueOf(PacketType.NONE));
                
                //bug 6388624 - Messages sent (but not delivered) are not 
                //redelivered until session closed (if no msgs seen)
                //default is set to true
                props.put("JMQRedeliverAll", Boolean.TRUE);
            }
        }

        //set properties to the packet
        pkt.setProperties(props);

        if (debug) {
            Debug.println("removing interest ....");
        }

        //int statusCode = writePacketWithAckStatus(pkt, PacketType.DELETE_CONSUMER_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DELETE_CONSUMER_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DELETE_CONSUMER_DENIED,
                consumer.getInterestId()) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_DELETE_CONSUMER_DENIED));
        }
        if (statusCode == Status.NOT_FOUND) {
            String cid = consumer.getInterestId().toString();
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DELETE_CONSUMER_NOTFOUND, cid) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_DELETE_CONSUMER_NOTFOUND));
        }
        if (statusCode == Status.CONFLICT && consumer.getDurable() == true) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DURABLE_INUSE, consumer.getDurableName()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_DURABLE_INUSE));
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }

        if (debug) {
            Debug.println("interest removed: " + consumer.getInterestId());
        }
    }

    /**
     * Deregister a durable subscriber.  This is originated from
     * TopicSession.unsubscribe()
     */
    public void unsubscribe(String durableName) throws JMSException {
        if (connection.getClientID() == null) {
            if (connection.getBrokerProtocolLevel() < PacketType.VERSION500) {
                String errorString = AdministeredObject.cr.getKString(
                    ClientResources.X_BROKER_DURA_SUB_NO_CLIENTID_NO_SUPPORT,
                    connection.getBrokerVersion());
                JMSException jmse = new com.sun.messaging.jms.JMSException(
                    errorString,
                    ClientResources.X_BROKER_DURA_SUB_NO_CLIENTID_NO_SUPPORT);
                ExceptionHandler.throwJMSException(jmse);
            }
        }
        Hashtable props = new Hashtable();
        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.DELETE_CONSUMER);
        props.put("JMQDurableName", durableName);

        //set properties to the packet
        pkt.setProperties(props);
        //int statusCode = writePacketWithAckStatus(pkt, PacketType.DELETE_CONSUMER_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DELETE_CONSUMER_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_UNSUBSCRIBE_DENIED,
                durableName) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_UNSUBSCRIBE_DENIED));
        }
        if (statusCode == Status.CONFLICT) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DURABLE_INUSE, durableName) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.ResourceAllocationException(errorString,
                                                  ClientResources.
                                                  X_DURABLE_INUSE));
        }
        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_UNSUBSCRIBE_NOTFOUND, durableName) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_UNSUBSCRIBE_NOTFOUND));
        }

        //XXX REVISIT other errorCode e.g. NOTFOUND possible ?
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
    }

    /**
     * Close the protocol handler.  All the open resources should be released.
     * The ConnectionHandler is closed.  InputStream and OutputStream are closed.
     * Send STOP and GOODBYE packets to the router.
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public synchronized void close() throws JMSException {

        if (isClosed) {
            return;
        }

        try {
            isClosed = true;
            
            connectionHandler.close();
            
            //clean the entry.
            this.xmlValidationTable.clear();
            
            //don't do this -- cause NULLPointerException if closed by
            //a different threads.
            //connectionHandler = null;
        } catch (Exception e) {
            ExceptionHandler.handleException(
                e, ClientResources.X_NET_CLOSE_CONNECTION, true);
        }

        if (debug) {
            Debug.println("ConnectionHandler closed ...");
        }
    }

    /**
     * Send acknowledge packet to the broker.  Default for ackAck
     * is true for auto/client acknowledge.  Turn this off if system
     * property ackAck is set to false.
     *
     * @param pkt the acknowledge packet sent to the broker.
     * @param sendToDMQ whether to send the msg to the DMQ or mark UNDELIVERABLE
     *
     * @exception JMSException any internal errors caused by the
     *                         ReadWritePacket IO.
     */
    public void acknowledgeUndeliverable(ReadWritePacket pkt, boolean sendToDMQ) 
    throws JMSException {
        acknowledgeUndeliverable(pkt, sendToDMQ, 0 /*undeliverable*/);
    }

    public void acknowledgeUndeliverable(ReadWritePacket pkt, 
                                         boolean sendToDMQ,
                                         int deadReason)
                                         throws JMSException {
        try {
            Hashtable props = new Hashtable();
            if (sendToDMQ) {
                props.put("JMQAckType", Integer.valueOf(2));
            } else {
                props.put("JMQAckType", Integer.valueOf(1));
            }
            if (sendToDMQ) {
                props.put("JMQDeadReason", Integer.valueOf(deadReason));
            }
            pkt.setProperties(props);
            // set packet type
            pkt.setPacketType(PacketType.ACKNOWLEDGE);

            // check if ack back required
            if (pkt.getSendAcknowledge() == true && ackAck == true) {
                // System.out.println ("need ack back ....");
                writePacketWithAck(pkt, PacketType.ACKNOWLEDGE_REPLY);
            } else {
                pkt.setSendAcknowledge(false);
                writePacketNoAck(pkt);
            }
        } finally {
            pkt.reset();
        }
    }


    /**
	 * Send auto acknowledge packet to the broker. Default for ackAck is true
	 * for auto/client acknowledge. Turn this off if system property ackAck is
	 * set to false.
	 * 
	 * @param pkt
	 *            the auto acknowledge packet sent to the broker.
	 * 
	 * @exception JMSException
	 *                any internal errors caused by the ReadWritePacket IO.
	 */
    public void acknowledge(ReadWritePacket pkt) throws JMSException {
        //set packet type
        pkt.setPacketType(PacketType.ACKNOWLEDGE);

        //check if ack back required
        if (pkt.getSendAcknowledge() == true && ackAck == true) {
            //System.out.println ("need ack back ....");
            //writePacketWithAck(pkt, PacketType.ACKNOWLEDGE_REPLY);
        	ReadOnlyPacket ack = writePacketWithReply(pkt, PacketType.ACKNOWLEDGE_REPLY);
            int statusCode = getReplyStatus(ack);
            
            //if OK, return immediately.
            if ( statusCode == Status.OK) {
            	return;
            } 
            
            //throws remote failed exception if Status.Gone and "JMQRemote" is true
            checkRemoteFailedStatus(statusCode, ack);
            
            //otherwise,  throws a server error exception   
            throwServerErrorException(ack);     
        } else {
            pkt.setSendAcknowledge(false);
            writePacketNoAck(pkt);
        }
    }

    public void
        redeliver(ReadWritePacket pkt, boolean flag, boolean isTransacted) throws JMSException {
        //set packet type
        pkt.setPacketType(PacketType.REDELIVER);
        //set redeliver properties
        Hashtable props = new Hashtable(1);
        props.put("JMQSetRedelivered",  Boolean.valueOf (flag));
        
        if (isTransacted) {
        	long tid = pkt.getTransactionID();
        
        	if (tid > 0) {
        		props.put("JMQTransactionID", Long.valueOf(tid));
        	}
        }
        
        pkt.setProperties(props);

        writePacketNoAck(pkt);
    }

    /**
     * Start a transacted session.
     *
     * In iMQ2.0 the client provided the transactionID in the JMQTransactionID
     * property. For Falcon the broker returns the transactionID in this
     * property. We go ahead and always set it just in case we want to
     * ineroperate with older brokers.
     */
    public long
        startTransaction(long transactionID, int xaflags, JMQXid xid) throws
        JMSException {
        return startTransaction(transactionID, xaflags, xid, false, 0);
    }

    public long
        startTransaction(long transactionID, int xaflags, JMQXid xid,
                         long brokerSessionID) throws JMSException {
        return startTransaction(transactionID, xaflags, xid, true,
                                brokerSessionID);
    }

    public long
        startTransaction(long transactionID, int xaflags, JMQXid xid,
                         boolean setSessionID, long brokerSessionID) throws
        JMSException {
        ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;

        pkt.setPacketType(PacketType.START_TRANSACTION);

        //START has got to have either transactionID or xid (or both)
        Hashtable ht = new Hashtable(1);
        
        //
        //if (transactionID != 0) {
        	//This must be the correct transactionID for XAStart with TMRESUME flag
        //	ht.put("JMQTransactionID", new Long(transactionID));
        //}

        if (setSessionID) {
            ht.put("JMQSessionID", Long.valueOf(brokerSessionID));
        }

        //two phase commit transaction, "2" means NOT_PREPARED
        if ( this.twoPhaseCommitFlag ) {
            ht.put("JMQAutoRollback", Integer.valueOf(2) );
        }

        //Place XAFlags and Xid into the packet - not for local transactions
        if (xaflags != -1) {
            ht.put("JMQXAFlags", Integer.valueOf(xaflags));

            if (xid != null) {
                try {
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    dos = new DataOutputStream(byteArrayOutputStream);
                    xid.write(dos);
                    dos.flush();

                    xidBody = byteArrayOutputStream.toByteArray();

                    dos.close();
                    byteArrayOutputStream.close();

                    pkt.setMessageBody(xidBody);
                } catch (IOException ioe) {
                    //XXX:GT TBF
                    ExceptionHandler.handleException(ioe,
                        ClientResources.X_CAUGHT_EXCEPTION);
                    //ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_NET_ACK, true);
                }
            }
        }

        pkt.setProperties(ht);
        int statusCode = -1;

        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.START_TRANSACTION_REPLY);
        try {
            Hashtable replyProps = replypkt.getProperties();
            Integer value = (Integer) replyProps.get("JMQStatus");
            statusCode = value.intValue();
            Long lvalue = (Long) replyProps.get("JMQTransactionID");
            if (lvalue != null) {
                transactionID = lvalue.longValue();
            }
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode == Status.CONFLICT) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_ID_INUSE,
                Long.valueOf(transactionID)) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException(errorString,
                ClientResources.X_TRANSACTION_ID_INUSE));
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(replypkt);
        }
        return transactionID;
    }

    /**
     * End an transaction (XA only)
     */
    public void
        endTransaction(long transactionID, int xaflags, JMQXid xid) throws
        JMSException {
        endTransaction(transactionID, false, xaflags, xid);
    }
    
    public void
        endTransaction(long transactionID, boolean jmqnoop, int xaflags, JMQXid xid) throws
        JMSException {
        ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;

        pkt.setPacketType(PacketType.END_TRANSACTION);

        //END has got to have either transactionID or xid (or both)
        Hashtable ht = new Hashtable(1);
        if (transactionID != 0) {
            ht.put("JMQTransactionID", Long.valueOf(transactionID));
        }

        //Place XAFlags and Xid into the packet
        if (xaflags != -1) {
            ht.put("JMQXAFlags", Integer.valueOf(xaflags));
            if (xid != null) {
                try {
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    dos = new DataOutputStream(byteArrayOutputStream);
                    xid.write(dos);
                    dos.flush();

                    xidBody = byteArrayOutputStream.toByteArray();

                    dos.close();
                    byteArrayOutputStream.close();

                    pkt.setMessageBody(xidBody);
                } catch (IOException ioe) {
                    //XXX:GT TBF
                    ExceptionHandler.handleException(ioe,
                        ClientResources.X_CAUGHT_EXCEPTION);
                    //ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_NET_ACK, true);
                }
            }
        }
        
        if (jmqnoop == true) {
            ht.put("JMQNoOp", jmqnoop);
        }

        pkt.setProperties(ht);
        int statusCode = -1;

        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.END_TRANSACTION_REPLY);
        try {
            Hashtable replyProps = replypkt.getProperties();
            Integer value = (Integer) replyProps.get("JMQStatus");
            statusCode = value.intValue();
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(replypkt);
        }
    }

    /**
     * Recover XA Transactions (XA only) - returns Xids
     */
    public JMQXid[]
        recover(int xaflags) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.RECOVER_TRANSACTION);

        Hashtable ht = new Hashtable(1);
        ht.put("JMQXAFlags", Integer.valueOf(xaflags));

        pkt.setProperties(ht);
        int statusCode = -1;
        int quantity = 0;

        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.RECOVER_TRANSACTION_REPLY);
        try {
            Hashtable replyProps = replypkt.getProperties();
            Integer value = (Integer) replyProps.get("JMQStatus");
            statusCode = value.intValue();
            value = (Integer) replyProps.get("JMQQuantity");
            quantity = value.intValue();
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(replypkt);
        }
        byte[] body = ((ReadWritePacket) replypkt).getMessageBody();
        JMQXid[] xids;
        if (body == null || body.length == 0) {
            xids = new JMQXid[] {};
        } else {
            int return_qty = body.length / JMQXid.size();
            if ((body.length % JMQXid.size() != 0) || (return_qty != quantity)) {
                ExceptionHandler.handleException(new
                    StreamCorruptedException(),
                    ClientResources.X_NET_READ_PACKET, true);
            }
            xids = new JMQXid[quantity];
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(
                body));
            for (int i = 0; i < quantity; i++) {
                try {
                    xids[i] = JMQXid.read(dis);
                } catch (IOException ioe) {
                    ExceptionHandler.handleException(ioe,
                        ClientResources.X_NET_READ_PACKET, true);
                }
            }
        }
        return xids;
    }


    /**
     * End a HA transaction
     */
    public void endHATransaction(long transactionID) throws
        JMSException {

        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.END_TRANSACTION);

        Hashtable ht = new Hashtable(1);
        if (transactionID != 0) {
            ht.put("JMQTransactionID", Long.valueOf(transactionID));
        }

        ht.put("JMQXAFlags", Integer.valueOf(XAResource.TMSUCCESS));

        pkt.setProperties(ht);

        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.END_TRANSACTION_REPLY);

        int statusCode = -1;

        Hashtable replyProps = getReplyProperties(replypkt);
        Integer value = (Integer) replyProps.get("JMQStatus");

        if (value != null) {
           statusCode = value.intValue();
        }

        //we could get Status.NOT_MODIFIED if this is a resent pkt.
        if (statusCode != Status.OK  && statusCode != Status.NOT_MODIFIED) {

            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_END_FAILED,
                String.valueOf(transactionID), String.valueOf(statusCode) ) +
                this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException (errorString,
            ClientResources.X_TRANSACTION_END_FAILED));
        }

    }


    /**
     * prepare HA transaction for stand alone client.
     * @param transactionID long
     * @throws TransactionRolledBackException if prepare failed.
     */
    public void prepareHATransaction (long transactionID)
        throws JMSException {

        int statusCode = -1;

        //Exception ex = null;

        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.PREPARE_TRANSACTION);

        //set transaction IDtransactionID present
        Hashtable ht = new Hashtable(1);
        ht.put("JMQTransactionID", Long.valueOf(transactionID));
        pkt.setProperties(ht);

        /**
         * write prepare pkt and wait for reply status
         */
        ReadOnlyPacket ack = writePacketWithReply(pkt,
                                                  PacketType.
                                                  PREPARE_TRANSACTION_REPLY);

        Hashtable replyProps = getReplyProperties(ack);
        Integer value = (Integer) replyProps.get("JMQStatus");

        if (value != null) {
            statusCode = value.intValue();
        }

        //we could get Status.NOT_MODIFIED if this is a resent pkt.
        if (statusCode != Status.OK && statusCode != Status.NOT_MODIFIED) {
        	
        	//check if this is a remote failed
        	this.checkRemoteFailedStatus(statusCode, ack);

            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_PREPARE_FAILED,
                String.valueOf(transactionID), String.valueOf(statusCode)) +
                                 this.getUserBrokerInfo();

            JMSException jmse = null;

            if (statusCode == Status.TIMEOUT) {
                jmse =
                    new com.sun.messaging.jms.TransactionRolledBackException(
                    errorString,
                    ClientResources.X_TRANSACTION_PREPARE_FAILED);
            } else {
                jmse =
                    new com.sun.messaging.jms.JMSException(errorString,
                    ClientResources.X_TRANSACTION_PREPARE_FAILED);
            }

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * Commit a transaction.
     * Check JMSException.getErrorCode().  if error code is
     * X_NET_WRITE_PACKET or X_NET_ACK
     * the transaction must be verified after reconnect.
     *
     */
    public void commitHATransaction (long transactionID) throws JMSException {
        int statusCode = -1;

        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.COMMIT_TRANSACTION);

        //COMMIT has got to have either transactionID or xid (or both)
        Hashtable ht = new Hashtable(1);

        ht.put("JMQTransactionID", Long.valueOf(transactionID));

        pkt.setProperties(ht);

        ReadOnlyPacket ack = null;

        ack = writePacketWithReply(pkt, PacketType.COMMIT_TRANSACTION_REPLY);

        statusCode = getReplyStatus(ack);

        switch ( statusCode ) {
            case Status.OK:
                return;
            case Status.TIMEOUT:
                //XXX HAWK: I18N.
            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.TransactionRolledBackException ("Timeout"));
            
            case Status.GONE:
            	//either throws remote failed exception or fall through and throws server error exception.
            	this.checkRemoteFailedStatus(statusCode, ack);

            default:
                Debug.getPrintStream().println("**** Tranaction ERROR, statusCode " + statusCode);
                this.throwServerErrorException(ack);
        }

    }

    /**
     * stand alone client calls this method to verify a transaction after failover.
     * @param transactionID
     * @param tstate
     * @return
     * @throws JMSException
     */
    public int verifyHATransaction (long transactionID, int tstate) throws JMSException {
    	return verifyHATransaction(transactionID, tstate, null);
    }

    /**
     * Verify transaction -- XA uses this to verify a transaction.
     * 
     * The transaction ID is 0 when Xid contains a value.
     * 
     * @param transactionID long
     * @return the state of transaction.
     *
     * 1	CREATED
     * 2	STARTED
     * 3	FAILED
     * 4	INCOMPLETE
     * 5	COMPLETE
     * 6	PREPARED
     * 7	COMMITED
     * 8	ROLLEDBACK
     * 9	TIMEDOUT

     *
     * @throws JMSException
     */
    public int verifyHATransaction (long transactionID, int tstate, JMQXid xid) throws JMSException {

    	ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;
   
        pkt.setPacketType(PacketType.VERIFY_TRANSACTION);
        //set transaction IDtransactionID present
        Hashtable ht = new Hashtable(1);
        
        //if (transactionID != 0) {
            ht.put("JMQTransactionID", Long.valueOf(transactionID));
        //}
        
        //Place Xid into the packet if not null
        if (xid != null) {
        	
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                dos = new DataOutputStream(byteArrayOutputStream);
                xid.write(dos);
                dos.flush();

                xidBody = byteArrayOutputStream.toByteArray();
                dos.close();
                byteArrayOutputStream.close();
                
                pkt.setMessageBody(xidBody);
            } catch (IOException ioe) {
                ExceptionHandler.handleException(ioe,
                                                 ClientResources.X_CAUGHT_EXCEPTION);
            }
        }
        
        //set propertyies
        pkt.setProperties(ht);
        
        int statusCode = -1;
        int transactionState = -1;

        ReadOnlyPacket replypkt = writePacketWithReply(pkt, PacketType.VERIFY_TRANSACTION_REPLY);

        Hashtable replyProps = getReplyProperties(replypkt);
        Integer value = (Integer) replyProps.get("JMQStatus");
        statusCode = value.intValue();

        if ( statusCode == Status.OK ) {

            Hashtable replyBody = ReadChannel.getHashtableFromMessageBody(replypkt);

            Integer tmp = (Integer) replyBody.get("State");
            if (tmp != null) {
                transactionState = tmp.intValue();
            }
        }

        if ( statusCode == Status.NOT_FOUND ) {
            if ( tstate == Transaction.TRANSACTION_ENDED ) {
                //transactionState = 8; //transaction is rolled back
            	transactionState = Transaction.TRANSACTION_VERIFY_STATUS_ROLLEDBACK;
            } else if ( tstate == Transaction.TRANSACTION_PREPARED ) {
                //transactionState = 7; //committed
            	transactionState = Transaction.TRANSACTION_VERIFY_STATUS_COMMITTED;
            }
        } else if ( statusCode != Status.OK ) {
            this.throwServerErrorException(replypkt);
        }

        if (debug) {
           Debug.println(new Date().toString() + " transaction state: " + transactionState);
        }

        //the status of the transaction
        return transactionState;
    }

    /**
     * Prepare a transaction (XA only)
     */
    public void
        prepare(long transactionID, JMQXid xid) throws JMSException {
    	//param false is to indicate this is a XA two phase commit.
    	this.prepare(transactionID, xid, false);
    }
    
    /**
     * Prepare a transaction (XA only).
     * 
     */
    public void
        prepare(long transactionID, JMQXid xid, boolean onePhase) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;

        pkt.setPacketType(PacketType.PREPARE_TRANSACTION);

        Hashtable ht = null;
        if (transactionID != 0) {
            //use property only if transactionID present
            ht = new Hashtable(1);
            ht.put("JMQTransactionID", Long.valueOf(transactionID));     
        }
        
        if (onePhase) {	
        	
        	if (ht == null) {
        		ht = new Hashtable(1);
        	} 
        	//set this property if it is XA one phase commit.
        	ht.put("JMQXAOnePhase", Boolean.TRUE);
        	
        }
        
        if (ht != null) {
        	pkt.setProperties(ht);
        }

        //Place Xid into the packet
        if (xid != null) {
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                dos = new DataOutputStream(byteArrayOutputStream);
                xid.write(dos);
                dos.flush();

                xidBody = byteArrayOutputStream.toByteArray();

                dos.close();
                byteArrayOutputStream.close();

                pkt.setMessageBody(xidBody);
            } catch (IOException ioe) {
                //XXX:GT TBF
                ExceptionHandler.handleException(ioe,
                                                 ClientResources.X_CAUGHT_EXCEPTION);
                //ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_NET_ACK, true);
            }
        }

        int statusCode = -1;

        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.PREPARE_TRANSACTION_REPLY);
        try {
            Hashtable replyProps = replypkt.getProperties();
            Integer value = (Integer) replyProps.get("JMQStatus");
            statusCode = value.intValue();
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode != Status.OK) {
        	
        	this.checkRemoteFailedStatus(statusCode, replypkt);
        	
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(replypkt);
        }
    }

    /**
     * Get License information from the broker
     *
     * The license data is returned in the Hashtable
     */
    public Hashtable
        getLicense() throws JMSException {
        Hashtable licenseProps = null;
        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.GET_LICENSE);

        int statusCode = -1;
        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.GET_LICENSE_REPLY);
        try {
            licenseProps = replypkt.getProperties();
            Integer value = (Integer) licenseProps.get("JMQStatus");
            statusCode = value.intValue();
        } catch (IOException ioe) {
            ExceptionHandler.handleException(ioe,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException cnfe) {
            ExceptionHandler.handleException(cnfe,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode != Status.OK) {
            this.throwServerErrorException(replypkt);
        }

        return licenseProps;
    }

    /**
     * Allocate a GlobalUniqueID from the broker
     *
     * This globally unique id is used by XAResource to identify the Resouce Manager
     */
    public long
        generateUID() throws JMSException {
        long globalUID = 0L;
        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.GENERATE_UID);

        //
        //XXX:RFE TBD enhance if need more than one
        //No props will return only one
        //
        //Hashtable ht = new Hashtable(1);
        //ht.put("JMQQuantity", new Integer (numUIDs) );
        //pkt.setProperties(ht);

        int statusCode = -1;
        //int quantity = 0;
        ReadOnlyPacket replypkt = writePacketWithReply(pkt,
            PacketType.GENERATE_UID_REPLY);
        try {
            Hashtable replyProps = replypkt.getProperties();
            Integer value = (Integer) replyProps.get("JMQQuantity");
            //quantity = value.intValue();
            value = (Integer) replyProps.get("JMQStatus");
            statusCode = value.intValue();
        } catch (IOException ioe) {
            ExceptionHandler.handleException(ioe,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException cnfe) {
            ExceptionHandler.handleException(cnfe,
                ClientResources.X_NET_ACK, true);
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(replypkt);
        }

        byte[] data = ((ReadWritePacket) replypkt).getMessageBody();
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(data));
        //XXX:Only accepts one right now.
        //for (int n = 0; n < quantity; n++) {
        try {
            globalUID = is.readLong();
        } catch (IOException ioe2) {
            ExceptionHandler.handleException(ioe2,
                ClientResources.X_NET_READ_PACKET, true);
        }
        //}
        return globalUID;
    }

    /**
     * Rollback a transaction. (XA transactions only)
     * This form is used by XA Impl for AS7
     */
    public void
        rollback(long transactionID, JMQXid xid) throws JMSException {
        _rollbackXA(transactionID, xid, false, false);
    }

    /**
     * Rollback a transaction. (XA transactions only)
     * This form is used by XA Impl for AS8 from the RA
     */
    public void
        rollback(long transactionID, JMQXid xid, boolean redeliver) throws
        JMSException {
    	
    	//default set I bit to false
        _rollbackXA(transactionID, xid, redeliver, false);
    }
    
    public void
    rollback(long transactionID, JMQXid xid, boolean redeliver, boolean setIBit) throws
    JMSException {
	
	//default set I bit to false
    _rollbackXA(transactionID, xid, redeliver, setIBit);
    }


    private void
        _rollbackXA(long transactionID, JMQXid xid, boolean setJMQRedeliver, boolean setIBit) throws
        JMSException {
        rollbackXA(transactionID, xid, setJMQRedeliver, setIBit, -1, false);
    }

    public void
        rollbackXA(long transactionID, JMQXid xid, 
                   boolean setJMQRedeliver,
                   boolean setIBit, int maxRollbacks,
                   boolean dmqOnMaxRollbacks)
                   throws JMSException {

        ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;
        Hashtable ht = null;

        pkt.setPacketType(PacketType.ROLLBACK_TRANSACTION);

        if (setIBit) {
            pkt.setIndempotent(true);
        }
        
        if ((transactionID != 0) || 
            (setJMQRedeliver == true) || (maxRollbacks > 0)) {

            int htsz = 0;
            if (maxRollbacks > 0) {
                htsz = 2;
            }
            if (transactionID != 0) {
                htsz += 1;
            }
            if (setJMQRedeliver == true) {
                htsz += 1;
            }
            ht = new Hashtable(htsz);
       
            if (transactionID != 0) {
                ht.put("JMQTransactionID", Long.valueOf(transactionID));
            }
            if (setJMQRedeliver == true) {
                ht.put("JMQRedeliver", Boolean.valueOf (setJMQRedeliver));
            }
            if (maxRollbacks > 0) {
                ht.put("JMQMaxRollbacks", Integer.valueOf(maxRollbacks));
                ht.put("JMQDMQOnMaxRollbacks", Boolean.valueOf(dmqOnMaxRollbacks));
            }
             
            pkt.setProperties(ht);
        }

        //Place Xid into the packet
        if (xid != null) {
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                dos = new DataOutputStream(byteArrayOutputStream);
                xid.write(dos);
                dos.flush();

                xidBody = byteArrayOutputStream.toByteArray();

                dos.close();
                byteArrayOutputStream.close();

                pkt.setMessageBody(xidBody);
            } catch (IOException ioe) {
                //XXX:GT TBF
                ExceptionHandler.handleException(ioe,
                                                 ClientResources.X_CAUGHT_EXCEPTION);
                //ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_NET_ACK, true);
            }
        }
        //int statusCode = writePacketWithAckStatus(pkt, PacketType.ROLLBACK_TRANSACTION_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.ROLLBACK_TRANSACTION_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode == Status.BAD_REQUEST) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_ID_INVALID,
                Long.valueOf(transactionID)) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException(errorString,
                ClientResources.X_TRANSACTION_ID_INVALID));
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
    }
    
    public long
    commit (long transactionID, int xaflags, JMQXid xid) throws JMSException {
    	return this.commit(transactionID, xaflags, xid, false);
    }

    /**
     * Commit a transaction.
     */
    public long
        commit(long transactionID, int xaflags, JMQXid xid, boolean onePhasePropRequired) throws JMSException {
        
    	long nextTransactionID = -1;
    	ReadWritePacket pkt = new ReadWritePacket();

        ByteArrayOutputStream byteArrayOutputStream = null;
        DataOutputStream dos = null;
        byte[] xidBody = null;

        pkt.setPacketType(PacketType.COMMIT_TRANSACTION);

        //COMMIT has got to have either transactionID or xid (or both)
        Hashtable ht = new Hashtable(1);
        if (transactionID != 0) {
            ht.put("JMQTransactionID", Long.valueOf(transactionID));
        }
        
        if (onePhasePropRequired) {
        	ht.put("JMQXAOnePhase", Boolean.TRUE);
        }

        if(SessionImpl.autoStartTxn)
        {
        	// if we are using fast transactions optimisation, 
        	// then ask broker to start a new transaction for us after commit
        	ht.put("JMQStartNextTransaction", Boolean.TRUE);
        }
        
        //Place XAFlags and Xid into the packet
        if (xid != null) {
            ht.put("JMQXAFlags", Integer.valueOf(xaflags));
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                dos = new DataOutputStream(byteArrayOutputStream);
                xid.write(dos);
                dos.flush();

                xidBody = byteArrayOutputStream.toByteArray();

                dos.close();
                byteArrayOutputStream.close();

                pkt.setMessageBody(xidBody);
            } catch (IOException ioe) {
                //XXX:GT TBF
                ExceptionHandler.handleException(ioe,
                                                 ClientResources.X_CAUGHT_EXCEPTION);
                //ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_NET_ACK, true);
            }
        }

        pkt.setProperties(ht);
        //int statusCode = writePacketWithAckStatus(pkt, PacketType.COMMIT_TRANSACTION_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.COMMIT_TRANSACTION_REPLY);
        int statusCode = getReplyStatus(ack);
        
        nextTransactionID = getNextTransactionID(ack);
        if (statusCode == Status.OK) {
        	return nextTransactionID;
        }

        if (statusCode == Status.BAD_REQUEST) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_ID_INVALID,
                Long.valueOf(transactionID)) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException(errorString,
                ClientResources.X_TRANSACTION_ID_INVALID));

        }

        this.checkRemoteFailedStatus(statusCode, ack);
        
        this.throwServerErrorException(ack);
        
        return nextTransactionID;
    }

    /**
     * Rollback a transaction. (Local transactions only)
     */
    public void
        rollback(long transactionID) throws JMSException {
        rollback(transactionID, false);
    }

    public void
        rollback(long transactionID, boolean updateConsumed) throws JMSException {

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.ROLLBACK_TRANSACTION);

        Hashtable ht = new Hashtable(1);
        ht.put("JMQTransactionID", Long.valueOf(transactionID));
        if (updateConsumed) {
            ht.put("JMQUpdateConsumed", Boolean.valueOf(true));
        }
        pkt.setProperties(ht);

        //pkt.setTransactionID(transactionID);

        writePacketNoAck(pkt);

        //changed.  Called from Transaction.rollback
        //startTransaction ( transactionID );
    }

    /**
     * verify destination and message selector
     *
     * if called for QueueBrowser create, no exception on NOT_FOUND if JMQCanCreate set
     *
     * @param dest the destination
     * @param selector the message selector
     * @param browser true if called for QueueBrowser create
     *
     * @exception InvalidDestinationException
     * @exception InvalidSelectorException
     * @exception JMSSecurityException
     * @exception JMSException
     */
    protected void
        verifyDestination(Destination destination, String selector,
                          boolean browser) throws JMSException {
        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) destination;

        ReadWritePacket pkt = new ReadWritePacket();

        pkt.setPacketType(PacketType.VERIFY_DESTINATION);
        Hashtable props = new Hashtable(3);
        props.put("JMQDestination", dest.getName());
        props.put("JMQDestType", getDestinationType(dest));
        if (selector != null && selector.trim().length() == 0) {
            selector = null;
        }
        if (selector != null) {
            props.put("JMQSelector", selector);
        }
        pkt.setProperties(props);

        ReadOnlyPacket pktrev = writePacketWithReply(pkt,
            PacketType.VERIFY_DESTINATION_REPLY);
        int statusCode = -1;
        try {
            Hashtable propsrev = pktrev.getProperties();
            statusCode = ((Integer) propsrev.get("JMQStatus")).intValue();
            if (browser && statusCode == Status.NOT_FOUND) {
                Boolean autoCreate = (Boolean) propsrev.get("JMQCanCreate");
                if (autoCreate != null && autoCreate.booleanValue()) {
                    return;
                }
            }

        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }

        if (statusCode == Status.BAD_REQUEST && selector != null) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_SELECTOR_INVALID, selector) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidSelectorException(errorString,
                                               ClientResources.
                                               X_SELECTOR_INVALID));
        }
        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_NOTFOUND, dest.getName()) +
                                 this.getUserBrokerInfo();
            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_DESTINATION_NOTFOUND));
        }
        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_VERIFY_DESTINATION_DENIED,
                dest.getName()) + this.getUserBrokerInfo();
            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_VERIFY_DESTINATION_DENIED));
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(pktrev);
        }
    }

    /**
     * get content (list of SysMessageIDs) in the destination
     *
     * @param dest the destination
     * @param selector the message selector to use
     *
     * @return an array of SysMessageIDs
     *
     * @exception InvalidDestinationException
     * @exception InvalidSelectorException
     * @exception JMSSecurityException
     * @exception JMSException
     */
    protected SysMessageID[]
        browse(Consumer consumer) throws JMSException {
        //browse(Destination destination, String selector) throws JMSException {
        com.sun.messaging.Destination dest =
            (com.sun.messaging.Destination) consumer.getDestination();
        String selector = consumer.getMessageSelector();
        if (selector != null && selector.trim().length() == 0) {
            selector = null;
        }
        ReadWritePacket pktsend = new ReadWritePacket();
        pktsend.setPacketType(PacketType.BROWSE);
        Hashtable props = new Hashtable(2);
        props.put("JMQDestination", dest.getName());
        props.put("JMQDestType", getDestinationType(dest));
        if (selector != null) {
            props.put("JMQSelector", selector);
        }

        //XXX PROTOCOL2.1 --
        //this will be used in addMetaData and then removed.
        props.put(REQUEST_META_DATA, consumer);

        pktsend.setProperties(props);

        ReadOnlyPacket pktrev = writePacketWithReply(pktsend,
            PacketType.BROWSE_REPLY);

        int statusCode = -1;
        try {
            Hashtable propsrev = pktrev.getProperties();
            statusCode = ((Integer) propsrev.get("JMQStatus")).intValue();
            if (statusCode == Status.NOT_FOUND) {
                Boolean autoCreate = (Boolean) propsrev.get("JMQCanCreate");
                if (autoCreate != null && autoCreate.booleanValue()) {
                    return new SysMessageID[] {};
                }
            }

        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }

        if (statusCode == Status.BAD_REQUEST && selector != null) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_SELECTOR_INVALID, selector) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidSelectorException(errorString,
                                               ClientResources.
                                               X_SELECTOR_INVALID));
        }
        if (statusCode == Status.NOT_FOUND) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_DESTINATION_NOTFOUND, dest.getName()) +
                                 this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.InvalidDestinationException(errorString,
                                                  ClientResources.
                                                  X_DESTINATION_NOTFOUND));
        }
        if (statusCode == Status.FORBIDDEN) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_BROWSE_DESTINATION_DENIED,
                dest.getName()) + this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSSecurityException(errorString,
                ClientResources.X_BROWSE_DESTINATION_DENIED));
        }
        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(pktrev);
        }

        byte[] data = ((ReadWritePacket) pktrev).getMessageBody();
        SysMessageID[] ids;
        //XXX REVISIT data.length == 0 legal ?
        if (data == null || data.length == 0) {
            ids = new SysMessageID[] {};
        } else {
            if ((data.length % SysMessageID.ID_SIZE) != 0) {
                ExceptionHandler.handleException(
                    new StreamCorruptedException(),
                    ClientResources.X_NET_READ_PACKET, true);
            }
            int numItems = data.length / SysMessageID.ID_SIZE;
            ids = new SysMessageID[numItems];
            DataInputStream is = new DataInputStream(new ByteArrayInputStream(
                data));
            for (int i = 0; i < numItems; i++) {
                ids[i] = new SysMessageID();
                try {
                    ids[i].readID(is);
                } catch (IOException e) {
                    ExceptionHandler.handleException(
                        e, ClientResources.X_NET_READ_PACKET, true);
                }
            }
        }
        return ids;
    }

    /**
     * The caller should flush the ByteArrayOutputStream if flush needed
     *
     * @return true  if deliver at least one message is guaranteed
     * @return false if all messages in bos not exist in broker
     *
     * @exception JMSException
     */
    protected boolean
        deliver(ByteArrayOutputStream bos, Consumer consumer) throws
        JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setMessageBody(bos.toByteArray());
        pkt.setPacketType(PacketType.DELIVER);
        Hashtable props = new Hashtable(1);
        props.put("JMQConsumerID", consumer.getInterestId());
        pkt.setProperties(props);

        //int statusCode = writePacketWithAckStatus(pkt, PacketType.DELIVER_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DELIVER_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode == Status.NOT_FOUND) {
            return false;
        } else if (statusCode == Status.OK) {
            return true;
        } else {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException (errorString, AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
            return false;
        }

    }

    /**
     * Suspend message delivery
     *
     */
    public void suspendMessageDelivery() throws JMSException {
        stop();
    }

    public void resumeMessageDelivery() throws JMSException {
        start();
    }

    /**
     * Create Session.
     */
    public void createSession(SessionImpl session) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.CREATE_SESSION);
        //XXX - PacketType.VERSION350
        //start hawk - broker version 350 or below is not supported.
        //if ( session.acknowledgeMode == com.sun.messaging.jms.Session.NO_ACKNOWLEDGE &&
        //     connection.getBrokerProtocolLevel() <= PacketType.VERSION350) {
        //if ( session.acknowledgeMode == com.sun.messaging.jms.Session.NO_ACKNOWLEDGE &&
        //     connection.getBrokerProtocolLevel() <= PacketType.VERSION350) {
        //    String errorString = AdministeredObject.cr.getKString(
        //        AdministeredObject.cr.X_BROKER_NOT_SUPPORT_NO_ACK_MODE, connection.getBrokerVersion());
        //    throw new com.sun.messaging.jms.JMSException (errorString,
        //        AdministeredObject.cr.X_BROKER_NOT_SUPPORT_NO_ACK_MODE);
        //}

        //must add session mode for NO_ACKNOWLEDGE mode.
        //Only if non-transacted
        if (!session.isTransacted) {
            Hashtable props = new Hashtable(1);
            //System.out.println("PH:createSession-ackMode="+session.acknowledgeMode);
            props.put("JMQAckMode", Integer.valueOf(session.acknowledgeMode));
            pkt.setProperties(props);
        }
        //end hawk

        ReadOnlyPacket reply = writePacketWithReply(pkt,
            PacketType.CREATE_SESSION_REPLY);

        int statusCode = -1;
        long sessionID = -1;

        try {
            Hashtable replyProps = reply.getProperties();
            statusCode = ((Integer) replyProps.get("JMQStatus")).intValue();
            Long jmqSIDProp = (Long) replyProps.get("JMQSessionID");
            if (jmqSIDProp != null) {
                sessionID = jmqSIDProp.longValue();
            }
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_NET_ACK, true);
        }

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(
            //    AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException(errorString,
            //    AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(reply);
        }

        session.setBrokerSessionID(sessionID);

        if (debug) {
            Debug.println("Added session, JMQSessionID: " +
                          session.getBrokerSessionID());
        }
    }

    public void deleteSession(SessionImpl session) throws JMSException {
        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.DESTROY_SESSION);

        Hashtable props = new Hashtable(1);
        props.put("JMQSessionID", Long.valueOf(session.getBrokerSessionID()));
        pkt.setProperties(props);

        //int statusCode = writePacketWithAckStatus(pkt,
        //    PacketType.DESTROY_SESSION_REPLY);
        ReadOnlyPacket ack = this.writePacketWithReply(pkt,
            PacketType.DESTROY_SESSION_REPLY);
        int statusCode = this.getReplyStatus(ack);

        if (statusCode != Status.OK) {
            //String errorString = AdministeredObject.cr.getKString(
            //    AdministeredObject.cr.X_SERVER_ERROR);
            //throw new com.sun.messaging.jms.JMSException (errorString,
            //    AdministeredObject.cr.X_SERVER_ERROR);
            this.throwServerErrorException(ack);
        }
    }


    /**
     * Get packet reply nextTransactionID.  This method may be called after
     * writePacketWithReply.
     */
    protected static long getNextTransactionID(ReadOnlyPacket ack) throws JMSException {
        long nextTransactionID = -1;

        Hashtable ackProperties = getReplyProperties(ack);        
        Long value = (Long) ackProperties.get("JMQNextTransactionID");

        if(value!=null)
        	nextTransactionID = value.longValue();

        return nextTransactionID;

    }
    
    /**
     * Get packet reply status.  This method may be called after
     * writePacketWithReply to get the reply status.
     */
    protected static int getReplyStatus(ReadOnlyPacket ack) throws JMSException {
        int statusCode = -1;

        Hashtable ackProperties = getReplyProperties(ack);

        Integer value = (Integer) ackProperties.get("JMQStatus");

        statusCode = value.intValue();

        return statusCode;

    }

    /**
     * Get properties object from reply packet.
     * @param ack the reply packet.
     * @return The hashtable in the reply pkt.
     * @throws JMSException if any internal error occurs.
     */
    protected static Hashtable
        getReplyProperties(ReadOnlyPacket ack) throws JMSException {

        Hashtable ackProperties = null;

        try {
            ackProperties = ack.getProperties();
        } catch (IOException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_PACKET_GET_PROPERTIES, true);
        } catch (ClassNotFoundException e) {
            ExceptionHandler.handleException(e,
                ClientResources.X_PACKET_GET_PROPERTIES, true);
        }

        return ackProperties;
    }

    /**
     *
     * @param ack
     * @throws JMSException
     */
    protected void
        checkReplyType(ReadOnlyPacket ack, int expectedType) throws
        JMSException {

        int receivedType = ack.getPacketType();

        if (receivedType != expectedType) {

            String errorString =
                AdministeredObject.cr.getKString(
                    ClientResources.X_NET_ACK_TYPE,
                    PacketType.getString(expectedType),
                    PacketType.getString(receivedType)) +
                this.getUserBrokerInfo();

            ExceptionHandler.throwJMSException(
            new com.sun.messaging.jms.JMSException(
                errorString, ClientResources.X_NET_ACK_TYPE));

        }
    }

    /**
     * When received a reply packet with status code != Status.OK,
     * this is the method to call if no other appropriate status
     * code to map to.
     *
     * @param ack the reply packet received from broker.
     * @throws JMSException the exception to be thrown to the client
     *                      application.
     */
    private void
    throwServerErrorException(ReadOnlyPacket ack) 
    throws JMSException {
        throwServerErrorException(ack, this);
    }

    private static void
    throwServerErrorException(ReadOnlyPacket ack, ProtocolHandler ph)
    throws JMSException {

		Integer statusCode = 0;

		String errorString = AdministeredObject.cr.getKString(ClientResources.X_SERVER_ERROR);

		com.sun.messaging.jms.JMSException serverex = null;
		try {
			Hashtable properties = getReplyProperties(ack);

			// get server error reason.
			String reason = (String) properties.get("JMQReason");

			statusCode = (Integer) properties.get("JMQStatus");

			if (reason != null) {

				int type = ack.getPacketType();
				String pktName = PacketType.getString(type);
				// added pkt type and reason to the exception message.
				errorString = "[" + pktName + "] " + errorString + " :[" + statusCode + "] " + reason;
			}
			// append user name/broker name.
			errorString = errorString + ph.getUserBrokerInfo();
			if (statusCode != null) {
                            serverex = new com.sun.messaging.jms.JMSException(
                                errorString, Status.getString(statusCode.intValue()));
			}

		} catch (Exception e) {
			// this should never happen. If this happens, we just
			// want to provide the initial exception information, not this.
			if (ph.debug) {
				Debug.printStackTrace(e);
			}
		}

		// if (debug) {
		// ack.dump(Debug.getPrintStream());
		// }

		com.sun.messaging.jms.JMSException jmse = new com.sun.messaging.jms.JMSException(errorString,
				ClientResources.X_SERVER_ERROR);
		if (serverex != null) {
			jmse.setLinkedException(serverex);
		}
		
		// now log this exception 
		// and rethrow the exception
		Level level;
		if (statusCode==Status.BAD_VERSION){
			// this is expected when connecting to a broker that uses an older protocol
			// this client will automatically reconnect using the older protocol
			// there is no need to bother the user about this
			level=Level.FINE;
		} else {
			level=Level.WARNING;
		}
		ExceptionHandler.throwJMSException(level,jmse);
	}

    public void checkRemoteFailedStatus(int statusCode, ReadOnlyPacket ack)
			throws JMSException {
    	
    	//only check if it is a GONE status
		if (statusCode == Status.OK) {
			return;
		}

		Hashtable replyProps = getReplyProperties(ack);
		
		if ( replyProps == null) {
			return;
		}
		
		Boolean value = (Boolean) replyProps.get("JMQRemote");

		if (value != null && value.booleanValue() && statusCode == Status.GONE) {
			// throw JMSException with error code
			String errorString = AdministeredObject.cr
					.getKString(ClientResources.X_ACK_FAILED_REMOTE);
			
			RemoteAcknowledgeException raex = 
				new RemoteAcknowledgeException (errorString, ClientResources.X_ACK_FAILED_REMOTE);

			raex.setProperties(replyProps);
			
			ExceptionHandler
					.throwJMSException(raex);
		}

                value = (Boolean) replyProps.get("JMQPrepareStateFAILED");
                if (value != null && value.booleanValue()) {
                    Long tid = (Long)replyProps.get("JMQTransactionID");
                    String errorString = AdministeredObject.cr
                        .getKString(ClientResources.X_BROKER_TXN_PREPARE_FAILED, 
                                    (tid == null ?"":String.valueOf(tid.longValue())));
                    TransactionPrepareStateFAILEDException be = 
                        new TransactionPrepareStateFAILEDException(errorString,
                            ClientResources.X_BROKER_TXN_PREPARE_FAILED);

                    ExceptionHandler.throwJMSException(be);
                }
	}

    /**
	 * When connection recovery failed, this is called. We try to close IO
	 * streams and connection. Called by ConnectionImpl.abort().
	 */
    public void abort() {
        try {
            connectionHandler.close();
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            Debug.printStackTrace(e);
        }
    }

    /**
     * Send Ping Packet to the broker.
     * @throws JMSException
     */
    public void ping() throws JMSException {

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.PING);

        //set reply bit.
        if ( (imqAbortOnPingAckTimeout) && (pingTimeout >0) ) {
            pkt.setFlag(PacketFlag.A_FLAG, true);
            setPingTimeStamp();
        }

        this.writePacketNoAck(pkt);
    }

    /**
     * Send Ping reply Packet to the broker.
     * @throws JMSException
     */
    public void pingReply(ReadOnlyPacket ping) throws JMSException {

        ReadWritePacket pkt = new ReadWritePacket();
        pkt.setPacketType(PacketType.PING_REPLY);

        long consumerID = ping.getConsumerID();
        pkt.setConsumerID(consumerID);

        Hashtable props = new Hashtable(1);
        props.put("JMQStatus", Integer.valueOf(200) );
        pkt.setProperties(props);

        this.writePacketNoAck(pkt);
    }


    public ConnectionHandler getConnectionHandler() {
        return this.connectionHandler;
    }

    protected String getUserBrokerInfo() {
        return getUserBrokerInfo(this);
    }

    private static String getUserBrokerInfo(ProtocolHandler ph) {
        String lname = ph.connection.getUserName();

        if (lname == null) {
            lname = "null";
        } else if (lname.length() == 0) {
            lname = "empty/blank";
        }

        String info = null;
        if (ph.connectionHandler == null) {
            info = "unavailable";
        } else {
            info = ph.connectionHandler.getBrokerAddress();
        }

        return " user=" + lname + ", broker=" + info;
    }

    public boolean isClosed() {
        return isClosed;
    }

    /**
     * redirect the connection to the new url.
     * @return String
     */
    public void redirect(String url) throws JMSException {

        if (debug) {
            Debug.info("ProtocolHandler: redirect connection to the URL : " +
                       url);
        }

        //close the current connection.  there are no body else using the
        //socket connection.  We cannot send good bye to the broker because
        //it would be blocked by the ConnectionImpl.checkReconnecting()...
        //this.goodBye(false);

        try {
            this.close();
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
            if (debug) {
                Debug.printStackTrace(e);
            }
        }

        //set the redirect url.
        connection.initiator.setRedirectURL(url);
        //re init the protocol handler.
        this.init(true);

        if (debug) {
            Debug.info(
                "*** ProtocolHandler: connection redirected to the URL: " + url);
        }
    }

    public void resend (ReadWritePacket pkt) throws JMSException {
        pkt.setFlag(PacketFlag.I_FLAG, true);
        writePacketNoAck(pkt);
    }
    
    public void setDebugInboundPkt (boolean flag) {
    	this.debugInboundPkt = flag;
    }
    
    public void setDebugOutboundPkt (boolean flag) {
    	this.debugOutboundPkt = flag;
    }
    
    public void setPktFilter (String filterSpec) {
    	this.pktFilter = filterSpec;
    }
    
    /**
     * this method id called from UnifiedSessionImpl.
     * if this is true, we don't need to send client ID again.
     * @return
     */
    protected boolean isClientIDsent() {
    	return this.sentSetClientID;
    }

    public boolean isDirectMode(){
    	return connectionHandler.isDirectMode();
    }
    
    /**
     * Return whether we are using direct mode, using two-threads rather than four
     * @return
     */
    public boolean isDirectModeTwoThread(){
    	return (isDirectMode() && BrokerInstanceImpl.isTwoThread);
    }
    
    /**
     * Return whether we are using direct mode, using two-threads rather than four, and receiving replies synchronusly using a ThreadLocal
     * @return
     */
    public boolean isDirectModeTwoThreadWithSyncReplies(){
    	return (isDirectModeTwoThread() && BrokerInstanceImpl.isTwoThreadSyncReplies);
    }

    public String toString() {
        return connectionHandler.toString();
    }
    
    /**
     * add destination/validator entry if TextMessage is to be validated
     * when sending to this destination.
     *  
     * @param dest
     * @param ack
     * @throws JMSException
     */
    private void 
    setXMLValidation(com.sun.messaging.Destination dest, ReadOnlyPacket ack) throws JMSException {
    	
    	//we can disable validation with the property enabled
    	if (turnOffXMLValidation) {
    		return;
    	}
    	
    	try {
    		Hashtable ackProperties = this.getReplyProperties(ack);
    		
    		Object validateObj = ackProperties.get("JMQValidateXMLSchema");
    		
    		boolean shouldValidate = false;
    		
    		if (validateObj != null) {
    			shouldValidate = ((Boolean)validateObj).booleanValue();
    		}
    		
    		if (shouldValidate) {
    			
    			XMLValidator validator = null;
    			String xsdURIList = null;
    			
    			Object uriobj = ackProperties.get("JMQXMLSchemaURIList");
    			
    			if (uriobj != null) {
    				xsdURIList = (String)uriobj;
    				
    				//minimum length of a valid uri
    				validator = ValidatorFactory.newValidator(xsdURIList);
    				
    				//set reloadXSDOnFailure flag
    				Object reloadObj = ackProperties.get("JMQReloadXMLSchemaOnFailure");
    				if (reloadObj != null) {
    					boolean doReload = ((Boolean)reloadObj).booleanValue();
    					validator.setReloadOnFailure(doReload);
    				}
    				
    			} else {
    				validator = ValidatorFactory.newValidator();
    			}
    			
    			//we do this so that after fail over occurred, we still want to validate
    			//the content in case the take over broker does not have the same config.
    			if ( this.xmlValidationTable.containsKey(dest) == false ) {
    				this.xmlValidationTable.put(dest, validator);
    				
    				if (debug) {
    					Debug.println("Adding xml validation entry for destination: " + dest.getName() + ", uriList: " + xsdURIList);
    				}
    			}
    		}
    		
    	} catch (Exception e) {
    		ExceptionHandler.handleException(e,
                    ClientResources.X_PACKET_GET_PROPERTIES, true);
    	}
    	
    }
    
    /**
     * Specify a ReplyDispatcher that can be used by an embedded broker to process reply packets if required 
     * 
     * This can safely be called in all cases, but is only used with an embedded broker using two threads and sync replies
     *
     * @param rd a ReplyDispatcher that can be used by an embedded broker to process reply packets
     */
    public void setReplyDispatcher(PacketDispatcher rd){
        if (isDirectModeTwoThread()){
        	((DirectConnectionHandler)connectionHandler).setReplyDispatcher(rd);
        }
    }
    
    /**
     * Return a long that uniquely represents this connection uniquely within this JVM
     * and which can be used to construct the name of temporary connections
     * @return
     */
    public long getConnID(){
    	if (connection.isDirectMode()){
    		return connection.getConnectionID();
    	} else {
    		return getLocalPort();
    	}
    }

}
