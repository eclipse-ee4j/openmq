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
 * @(#)BrokerAdmin.java	1.85 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Properties;
import javax.jms.*;

import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.admin.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.util.DestMetricsCounters;

import com.sun.messaging.jmq.admin.event.BrokerCmdStatusEvent;
import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jms.management.server.BrokerClusterInfo;

/**
 * This class provides the convenient methods for sending administration
 * messages to the JMQ broker.
 *
 * <P>
 * The information needed to create this object are:
 * <UL>
 * <LI>connection factory attributes
 * <LI>username/passwd
 * <LI>timeout (for receiving replies)
 * </UL>
 */
public class BrokerAdmin extends BrokerAdminConn  {

    private Object			aObj = null;

    private BrokerCmdStatusEvent  statusEvent = null;

    /*
     * Temporary convenient constructor.
     */
    public BrokerAdmin(String brokerHost, int brokerPort) throws BrokerAdminException  {
    this(brokerHost, brokerPort, null, null, -1, false, -1, -1);
    }

    public BrokerAdmin(String brokerHost, int brokerPort, 
	               String username, String passwd) 
		       throws BrokerAdminException  {
	this(brokerHost, brokerPort, username, passwd, -1, false, -1, -1);
    }

    public BrokerAdmin(String brokerHost, int brokerPort, 
	               String username, String passwd, int timeout) 
		       throws BrokerAdminException  {
	this(brokerHost, brokerPort, username, passwd, timeout, false, -1, -1);
    }

    public BrokerAdmin(String brokerAddress,
	               String username, String passwd, int timeout, boolean useSSL) 
		       throws BrokerAdminException  {
	this(brokerAddress, username, passwd, timeout, false, -1, -1, useSSL);
    }

    /**
     * Instantiates a BrokerAdmin object. This is a wrapper for
     * this other constructor:
     *
     *  public BrokerAdmin(Properties, String, String, long)
     *
     * @param brokerHost	host name of the broker to administer
     * @param brokerPort 	primary port for broker
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     * @param reconnect		true if reconnect is enabled; false otherwise
     * @param reconnectRetries	number of reconnect retries
     * @param reconnectDelay	interval of reconnect retries in milliseconds
     */
    public BrokerAdmin(String brokerHost, int brokerPort, 
	               String username, String passwd, long timeout,
		       boolean reconnect, int reconnectRetries, long reconnectDelay) 
		       throws BrokerAdminException  {
    super(brokerHost, brokerPort, username, passwd, timeout, 
          reconnect, reconnectRetries, reconnectDelay);    
    }

    /**
     * Instantiates a BrokerAdmin object. This is a wrapper for
     * this other constructor:
     *
     *  public BrokerAdmin(Properties, String, String, long)
     *
     * @param brokerAddress 	address/url of broker
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     * @param reconnect		true if reconnect is enabled; false otherwise
     * @param reconnectRetries	number of reconnect retries
     * @param reconnectDelay	interval of reconnect retries in milliseconds
     * @param useSSL		Use encrypted transport via SSL
     */
    public BrokerAdmin(String brokerAddress, 
	               String username, String passwd, 
		       long timeout,
		       boolean reconnect, int reconnectRetries, 
		       long reconnectDelay, boolean useSSL) 
		           throws BrokerAdminException  {

    super(brokerAddress, username, passwd, timeout,
           reconnect, reconnectRetries, reconnectDelay, useSSL);
    }


    /**
     * The constructor for the class.
     *
     * @param brokerAttrs 	Properties object containing
     *				the broker attributes. This is
     *				basically what is used to create
     *				the connection factory.
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     */
    public BrokerAdmin(Properties brokerAttrs,
			String username, String passwd, 
			long timeout) 
		       throws BrokerAdminException  {

    super(brokerAttrs, username, passwd, timeout);
    }

    /**********************************************************
     * BEGIN impl of admin protocol specific abstract methods  
     **********************************************************/
    public String getAdminQueueDest() {
        return MessageType.JMQ_ADMIN_DEST; 
    }

    public String getAdminMessagePropNameMessageType() {
        return MessageType.JMQ_MESSAGE_TYPE;
    }

    public String getAdminMessagePropNameErrorString() {
        return MessageType.JMQ_ERROR_STRING;
    }

    public String getAdminMessagePropNameStatus() {
        return MessageType.JMQ_STATUS;
    }

    public int getAdminMessageStatusOK() {
        return MessageType.OK;
    }

    public int getAdminMessageTypeSHUTDOWN_REPLY() {
        return MessageType.SHUTDOWN_REPLY;
    }
    /**********************************************************
     * END impl of admin protocol specific abstract methods  
     **********************************************************/

    /************************************************************
     * BEGIN impl of BrokerAdmin specific abstract methods
     ***********************************************************/
    public CommonCmdStatusEvent newCommonCmdStatusEvent(int type) {
        return new BrokerCmdStatusEvent(this, this, type);
    }

    public CommonCmdStatusEvent getCurrentStatusEvent() {
        return this.statusEvent;
    }

    public void clearStatusEvent() {
        statusEvent = null;
    }
    /************************************************************
     * END impl of BrokerAdmin specific abstract methods
     ***********************************************************/

    private BrokerCmdStatusEvent createStatusEvent(int type, int replyType,
                    String replyTypeString)  {
    CommonCmdStatusEvent cse = newCommonCmdStatusEvent(type);
    cse.setReplyType(replyType);
    cse.setReplyTypeString(replyTypeString);

    return (BrokerCmdStatusEvent)cse;
    }

    public void sendHelloMessage() throws BrokerAdminException  {
	BrokerAdminException bae;

	if (getDebug()) Globals.stdOutPrintln("***** sendHelloMessage *****");
	ObjectMessage mesg = null;

	checkIfBusy();

	try {
	    mesg = session.createObjectMessage();
	    mesg.setJMSReplyTo(replyQueue);		
	    mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.HELLO);
            statusEvent = createStatusEvent(BrokerCmdStatusEvent.HELLO,
                                            MessageType.HELLO_REPLY,
                                            "HELLO_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.HELLO, "HELLO");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_PROTOCOL_LEVEL
			+ "=" 
			+ 102);
	    }
	    sender.send(mesg);	

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveHelloReplyMessage() throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** receiveHelloReplyMessage() *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.HELLO_REPLY, "HELLO_REPLY");

	    isConnected = true;

        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendGetServicesMessage(String svcName) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendGetServicesMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_SERVICES);
	    if (svcName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_SERVICE_NAME, svcName);
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_SVC,
						MessageType.GET_SERVICES_REPLY,
						"GET_SERVICES_REPLY");
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_SVC,
						MessageType.GET_SERVICES_REPLY,
						"GET_SERVICES_REPLY");
	    }

	    statusEvent.setServiceName(svcName);
	    if (getDebug())  {
		printMsgType(MessageType.GET_SERVICES, "GET_SERVICES");
		if (svcName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_SERVICE_NAME
			+ "=" 
			+ svcName);
		}
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetServicesReplyMessage() throws BrokerAdminException {
	return receiveGetServicesReplyMessage(true);
    }

    public Vector receiveGetServicesReplyMessage(boolean waitForResponse) 
	throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveGetServicesReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_SERVICES_REPLY, "GET_SERVICES_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector) {
		    if (getDebug())  {
		        printServiceInfoList((Vector)obj);
		    }
                    return (Vector)obj;
                }
            }

        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }


    public void sendPauseMessage(String svcName) 
				throws BrokerAdminException  {

        if (getDebug()) Globals.stdOutPrintln("***** sendPauseMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.PAUSE);
	    mesg.setStringProperty(MessageType.JMQ_PAUSE_TARGET, MessageType.JMQ_SERVICE_NAME);
	    if (svcName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_SERVICE_NAME, svcName);
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.PAUSE_SVC,
						MessageType.PAUSE_REPLY,
						"PAUSE_REPLY");
	        statusEvent.setServiceName(svcName);
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.PAUSE_BKR,
						MessageType.PAUSE_REPLY,
						"PAUSE_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.PAUSE, "PAUSE");
		if (svcName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_SERVICE_NAME
			+ "=" 
			+ svcName);
		}
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void sendPauseMessage(String dstName, int dstType, int pauseType) 
				throws BrokerAdminException  {

        if (getDebug()) Globals.stdOutPrintln("***** sendPauseMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.PAUSE);
	    mesg.setStringProperty(MessageType.JMQ_PAUSE_TARGET, MessageType.JMQ_DESTINATION);
	    if (dstName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	        mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 
	    }
	    if (pauseType != DestState.UNKNOWN)  {
	        mesg.setIntProperty(MessageType.JMQ_DEST_STATE, pauseType); 
	    }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.PAUSE_DST,
						MessageType.PAUSE_REPLY,
						"PAUSE_REPLY");
	    statusEvent.setDestinationName(dstName);
	    statusEvent.setDestinationType(dstType);


	    if (getDebug())  {
		printMsgType(MessageType.PAUSE, "PAUSE");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION
			+ "=" 
			+ dstName);

		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_TYPE
			+ "=" 
			+ dstType);

		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_STATE
			+ "=" 
			+ pauseType);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receivePauseReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receivePauseReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.PAUSE_REPLY, "PAUSE_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }

    public void sendResetBrokerMessage(String resetType) 
				throws BrokerAdminException  {

        if (getDebug()) Globals.stdOutPrintln("***** sendResetBrokerMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.RESET_BROKER);
	    if (resetType != null)  {
	        mesg.setStringProperty(MessageType.JMQ_RESET_TYPE, resetType); 
	    }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.RESET_BKR,
						MessageType.RESET_BROKER_REPLY,
						"RESET_BROKER_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.RESET_BROKER, "RESET_BROKER");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_RESET_TYPE
			+ "=" 
			+ resetType);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }
    
    
    public void receiveResetBrokerReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveResetBrokerReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.RESET_BROKER_REPLY, "RESET_BROKER_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }

    
    public void sendCheckpointBrokerMessage() throws BrokerAdminException {

		if (getDebug())
			Globals.stdOutPrintln("***** sendCheckpointBrokerMessage *****");
		ObjectMessage mesg = null;

		checkIfBusy();

		try {
			mesg = session.createObjectMessage();
			mesg.setJMSReplyTo(replyQueue);
			mesg.setIntProperty(MessageType.JMQ_MESSAGE_TYPE,
					MessageType.CHECKPOINT_BROKER);

			statusEvent = createStatusEvent(BrokerCmdStatusEvent.CHECKPOINT_BKR,
					MessageType.CHECKPOINT_BROKER_REPLY, "CHECKPOINT_BROKER_REPLY");

			if (getDebug()) {
				printMsgType(MessageType.CHECKPOINT_BROKER, "CHECKPOINT_BROKER");
			}
			sender.send(mesg);
		} catch (Exception e) {
			handleSendExceptions(e);
		}
	}

    public void receiveCheckpointBrokerReplyMessage()
			throws BrokerAdminException {

		if (getDebug())
			Globals.stdOutPrintln("***** receiveCheckpointBrokerReplyMessage *****");
		Message mesg = null;

		try {
			mesg = (ObjectMessage) receiveCheckMessageTimeout(false);

			mesg.acknowledge();
			clearStatusEvent();
			checkReplyTypeStatus(mesg, MessageType.CHECKPOINT_BROKER_REPLY,
					"CHECKPOINT_BROKER_REPLY");
		} catch (Exception e) {
			handleReceiveExceptions(e);
		}
	}


    public void sendResumeMessage(String svcName) throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendResumeMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.RESUME);
	    mesg.setStringProperty(MessageType.JMQ_PAUSE_TARGET, MessageType.JMQ_SERVICE_NAME);
	    if (svcName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_SERVICE_NAME, svcName);
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.RESUME_SVC,
						MessageType.RESUME_REPLY,
						"RESUME_REPLY");
	        statusEvent.setServiceName(svcName);
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.RESUME_BKR,
						MessageType.RESUME_REPLY,
						"RESUME_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.RESUME, "RESUME");
		if (svcName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_SERVICE_NAME
			+ "=" 
			+ svcName);
		}
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void sendResumeMessage(String dstName, int dstType) 
					throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendResumeMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.RESUME);
	    mesg.setStringProperty(MessageType.JMQ_PAUSE_TARGET, MessageType.JMQ_DESTINATION);
	    if (dstName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	        mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 
	    }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.RESUME_DST,
						MessageType.RESUME_REPLY,
						"RESUME_REPLY");
	    statusEvent.setDestinationName(dstName);
	    statusEvent.setDestinationType(dstType);

	    if (getDebug())  {
		printMsgType(MessageType.RESUME, "RESUME");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION
			+ "=" 
			+ dstName);

		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_TYPE
			+ "=" 
			+ dstType);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveResumeReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveResumeReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.RESUME_REPLY, "RESUME_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendQuiesceMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendQuiesceMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.QUIESCE_BROKER);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUIESCE_BKR,
						MessageType.QUIESCE_BROKER_REPLY,
						"QUIESCE_BROKER_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.QUIESCE_BROKER, "QUIESCE_BROKER");
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveQuiesceReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveQuiesceReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.QUIESCE_BROKER_REPLY, "QUIESCE_BROKER_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendUnquiesceMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendUnquiesceMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.UNQUIESCE_BROKER);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.UNQUIESCE_BKR,
						MessageType.UNQUIESCE_BROKER_REPLY,
						"UNQUIESCE_BROKER_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.UNQUIESCE_BROKER, "UNQUIESCE_BROKER");
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveUnquiesceReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveUnquiesceReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.UNQUIESCE_BROKER_REPLY, "UNQUIESCE_BROKER_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }



    public void sendTakeoverMessage(String brokerID) throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendTakeoverMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.TAKEOVER_BROKER);
	    mesg.setStringProperty(MessageType.JMQ_BROKER_ID, brokerID);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.TAKEOVER_BKR,
						MessageType.TAKEOVER_BROKER_REPLY,
						"TAKEOVER_BROKER_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.TAKEOVER_BROKER, "TAKEOVER_BROKER");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_BROKER_ID
			+ " = "
			+ brokerID);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveTakeoverReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveTakeoverReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.TAKEOVER_BROKER_REPLY, 
				"TAKEOVER_BROKER_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }

    public void sendMigrateStoreMessage(String brokerID, String partition)
    throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** sendMigrateStoreMessage *****");
        ObjectMessage mesg = null;
        checkIfBusy();
        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty(MessageType.JMQ_MESSAGE_TYPE,
                                MessageType.MIGRATESTORE_BROKER);
            if (brokerID != null) {
                mesg.setStringProperty(MessageType.JMQ_BROKER_ID, brokerID);
            }
            if (partition != null) {
                mesg.setStringProperty(MessageType.JMQ_MIGRATESTORE_PARTITION, partition);
            }
            statusEvent = createStatusEvent(BrokerCmdStatusEvent.MIGRATESTORE_BKR,
                                            MessageType.MIGRATESTORE_BROKER_REPLY,
                                            "MIGRATESTORE_BROKER_REPLY");
            if (getDebug())  {
                printMsgType(MessageType.MIGRATESTORE_BROKER, "MIGRATESTORE_BROKER");
                Globals.stdOutPrintln("\t"
                + MessageType.JMQ_BROKER_ID
                + " = "
                + brokerID);
            }
            sender.send(mesg);
        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }

    public String receiveMigrateStoreReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveMigrateStoreReplyMessage *****");
        Message mesg = null;
        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);
            mesg.acknowledge();
            clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.MIGRATESTORE_BROKER_REPLY, 
                                       "MIGRATESTORE_BROKER_REPLY");
            String bk = mesg.getStringProperty(MessageType.JMQ_BROKER_ID);
            String hp = mesg.getStringProperty(MessageType.JMQ_MQ_ADDRESS);
            if (bk != null) {
                bk = bk + (hp == null ? "":"["+hp+"]");
            }
            return bk;
        } catch (Exception e) {
            handleReceiveExceptions(e);
            return null;
        }
    }

    public void sendGetDestinationsMessage(String dstName, int dstType) 
			throws BrokerAdminException {
        sendGetDestinationsMessage(dstName, dstType, false, false);
    }
    public void sendGetDestinationsMessage(String dstName, int dstType,
        boolean showpartition, boolean loaddestination) 
        throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetDestinationsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_DESTINATIONS);
            if (showpartition) {
	        mesg.setBooleanProperty(MessageType.JMQ_SHOW_PARTITION, Boolean.valueOf(true));
            }
            if (loaddestination) {
	        mesg.setBooleanProperty(MessageType.JMQ_LOAD_DESTINATION, Boolean.valueOf(true));
            }

	    if (dstName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	        mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 

	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_DST,
						MessageType.GET_DESTINATIONS_REPLY,
						"GET_DESTINATIONS_REPLY");
	        statusEvent.setDestinationName(dstName);
	        statusEvent.setDestinationType(dstType);
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_DST,
						MessageType.GET_DESTINATIONS_REPLY,
						"GET_DESTINATIONS_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.GET_DESTINATIONS, "GET_DESTINATIONS");
		if (dstName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION
			+ "=" 
			+ dstName);
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_TYPE
			+ "=" 
			+ dstType);
		}
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetDestinationsReplyMessage() throws BrokerAdminException {
	return receiveGetDestinationsReplyMessage(true);
    }

    public Vector receiveGetDestinationsReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetDestinationsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_DESTINATIONS_REPLY, "GET_DESTINATIONS_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
		        printDestinationInfoList((Vector)obj);
		    }
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }


    public void sendCreateDestinationMessage(DestinationInfo dstInfo) 
				throws BrokerAdminException {

        if (getDebug()) 
	    Globals.stdOutPrintln("***** sendCreateDestinationMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
	    (MessageType.JMQ_MESSAGE_TYPE, MessageType.CREATE_DESTINATION);
            mesg.setObject(dstInfo);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.CREATE_DST,
						MessageType.CREATE_DESTINATION_REPLY,
						"CREATE_DESTINATION_REPLY");
	    statusEvent.setDestinationInfo(dstInfo);

	    if (getDebug())  {
		printMsgType(MessageType.CREATE_DESTINATION, "CREATE_DESTINATION");
		printDestinationInfo(dstInfo);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveCreateDestinationReplyMessage() 
	throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveCreateDestinationReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.CREATE_DESTINATION_REPLY,
				"CREATE_DESTINATION_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendDestroyDestinationMessage(String dstName, int dstType) 
			throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** sendDestroyDestinationMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.DESTROY_DESTINATION);
            mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
            mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.DESTROY_DST,
						MessageType.DESTROY_DESTINATION_REPLY,
						"DESTROY_DESTINATION_REPLY");
	    statusEvent.setDestinationName(dstName);
	    statusEvent.setDestinationType(dstType);

	    if (getDebug())  {
		printMsgType(MessageType.DESTROY_DESTINATION, "DESTROY_DESTINATION");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION
			+ "=" 
			+ dstName);
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_TYPE
			+ "=" 
			+ dstType);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveDestroyDestinationReplyMessage() 
				throws BrokerAdminException {
        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveDestroyDestinationReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.DESTROY_DESTINATION_REPLY,
				"DESTROY_DESTINATION_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendPurgeDestinationMessage(String dstName, int dstType) 
				throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** sendPurgeDestinationMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.PURGE_DESTINATION);
            mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
            mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.PURGE_DST,
						MessageType.PURGE_DESTINATION_REPLY,
						"PURGE_DESTINATION_REPLY");
	    statusEvent.setDestinationName(dstName);
	    statusEvent.setDestinationType(dstType);

	    if (getDebug())  {
		printMsgType(MessageType.PURGE_DESTINATION, "PURGE_DESTINATION");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION 
			+ "=" 
			+ dstName);
		Globals.stdOutPrintln("\t" 
			+ MessageType.JMQ_DEST_TYPE 
			+ "=" 
			+ dstType);
	    }

            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receivePurgeDestinationReplyMessage() 
				throws BrokerAdminException {
        if (getDebug())
	Globals.stdOutPrintln("***** receivePurgeDestinationReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.PURGE_DESTINATION_REPLY,
				"PURGE_DESTINATION_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendGetBrokerPropsMessage() 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendGetBrokerPropsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_BROKER_PROPS);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_BKR,
						MessageType.GET_BROKER_PROPS_REPLY,
						"GET_BROKER_PROPS_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.GET_BROKER_PROPS, "GET_BROKER_PROPS");
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Properties receiveGetBrokerPropsReplyMessage() throws BrokerAdminException {
	return receiveGetBrokerPropsReplyMessage(true);
    }

    public Properties receiveGetBrokerPropsReplyMessage(boolean waitForResponse) 
	throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveGetBrokerPropsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);
            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_BROKER_PROPS_REPLY,
				"GET_BROKER_PROPS_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Properties)
                    return (Properties)obj;
            }

        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }


    public void sendUpdateBrokerPropsMessage(Properties props) 
				throws BrokerAdminException {
        if (getDebug()) 
	Globals.stdOutPrintln("***** sendUpdateBrokerPropsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.UPDATE_BROKER_PROPS);
	    mesg.setObject(props);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.UPDATE_BKR,
						MessageType.UPDATE_BROKER_PROPS_REPLY,
						"UPDATE_BROKER_PROPS_REPLY");
	    statusEvent.setBrokerProperties(props);

	    if (getDebug())  {
		printMsgType(MessageType.UPDATE_BROKER_PROPS, "UPDATE_BROKER_PROPS");
		Globals.stdOutPrintln("\tProperties=" 
			+ props.toString());
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveUpdateBrokerPropsReplyMessage()
        			throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveUpdateBrokerPropsReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.UPDATE_BROKER_PROPS_REPLY,
					"UPDATE_BROKER_PROPS_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendUpdateDestinationMessage(DestinationInfo dstInfo) 
				throws BrokerAdminException {

        if (getDebug())
        Globals.stdOutPrintln("***** sendUpdateDestinationMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.UPDATE_DESTINATION);
            mesg.setObject(dstInfo);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.UPDATE_DST,
						MessageType.UPDATE_DESTINATION_REPLY,
						"UPDATE_DESTINATION_REPLY");
	    statusEvent.setDestinationInfo(dstInfo);

	    if (getDebug())  {
		printMsgType(MessageType.UPDATE_DESTINATION, "UPDATE_DESTINATION");
		printDestinationInfo(dstInfo);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveUpdateDestinationReplyMessage()
        			throws BrokerAdminException {

        if (getDebug())
        Globals.stdOutPrintln("***** receiveUpdateDestinationReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.UPDATE_DESTINATION_REPLY,
					"UPDATE_DESTINATION_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendUpdateServiceMessage(ServiceInfo svcInfo) 
				throws BrokerAdminException {

        if (getDebug())
        Globals.stdOutPrintln("***** sendUpdateServiceMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.UPDATE_SERVICE);
            mesg.setObject(svcInfo);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.UPDATE_SVC,
						MessageType.UPDATE_SERVICE_REPLY,
						"UPDATE_SERVICE_REPLY");
	    statusEvent.setServiceInfo(svcInfo);

	    if (getDebug())  {
		printMsgType(MessageType.UPDATE_SERVICE, "UPDATE_SERVICE");
		printServiceInfo(svcInfo);
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveUpdateServiceReplyMessage()
        			throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveUpdateServiceReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
	    checkReplyTypeStatus(mesg, MessageType.UPDATE_SERVICE_REPLY,
					"UPDATE_SERVICE_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    public void sendShutdownMessage(boolean restart) 
				throws BrokerAdminException {
        sendShutdownMessage(restart, false);
    }

    public void sendShutdownMessage(boolean restart, boolean kill) 
				throws BrokerAdminException {
        sendShutdownMessage(restart, kill, false, -1);
    }

    public void sendShutdownMessage(boolean restart, boolean kill,
			boolean noFailover, int time) 
				throws BrokerAdminException {
        if (getDebug()) Globals.stdOutPrintln("***** sendShutdownMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
	    (MessageType.JMQ_MESSAGE_TYPE, MessageType.SHUTDOWN);
	    if (restart)  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.RESTART_BKR,
						MessageType.SHUTDOWN_REPLY,
						"SHUTDOWN_REPLY");
                mesg.setBooleanProperty(MessageType.JMQ_RESTART, true);
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.SHUTDOWN_BKR,
						MessageType.SHUTDOWN_REPLY,
						"SHUTDOWN_REPLY");

                mesg.setBooleanProperty(MessageType.JMQ_NO_FAILOVER, noFailover);
		if (time > 0)  {
                    mesg.setIntProperty(MessageType.JMQ_TIME, time);
		}
	    }

	    if (kill)  {
                mesg.setBooleanProperty(MessageType.JMQ_KILL, true);
	    }


	    if (getDebug())  {
		printMsgType(MessageType.SHUTDOWN, "SHUTDOWN");
		if (restart)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_RESTART
			+ "=true");
		} else  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_NO_FAILOVER
			+ "="
			+ noFailover);
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_TIME
			+ "="
			+ time);
		}
		if (kill)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_KILL
			+ "=true");
		}
	    }
            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveShutdownReplyMessage() 
				throws BrokerAdminException {
        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveShutdownReplyMessage *****");
        Message mesg = null;

        try {
            mesg = receiver.receive(timeout);
	    /* 
	     * DO NOT ack shutdown, as the broker is already GONE!
             * mesg.acknowledge();
	     */
	    /* 
	     * Message can be null if receive() times out.
	     * On shutdownReply, it can be null if the broker shuts
	     * down prior to this method receiving the message.  If the 
	     * message is null, simply treat it as successful.  This
	     * is done in checkReplyTypeStatus() method.
	     */
	     checkReplyTypeStatus(mesg, MessageType.SHUTDOWN_REPLY,
	    			 "SHUTDOWN_REPLY");

        } catch (JMSException jmsee) {
	    /* 
	     * One exception that we will most likely encounter is 
	     * javax.jms.IllegalStateException.
	     * We may run into this state when receive() is called
	     * after session is closed.  Similar to the null
	     * message case above, we treat this as successful.
	     * We are ignoring any JMSExceptions, since most likely the
	     * shutdown of the broker is successful when a JMSException is
	     * thrown.
	     */
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }


    /**
     * Note: The protocol assumes that the destination is of type topic.
     */
    public void sendGetDurablesMessage(String topicName, String durName)
                                throws BrokerAdminException {
        BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendGetDurablesMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_DURABLES);
            if (topicName != null)
                mesg.setStringProperty(MessageType.JMQ_DESTINATION, topicName);
            if (durName != null)  {
                mesg.setStringProperty(MessageType.JMQ_DURABLE_NAME, durName);
            }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_DUR,
						MessageType.GET_DURABLES_REPLY,
						"GET_DURABLES_REPLY");
	    statusEvent.setDestinationName(topicName);
	    statusEvent.setDurableName(durName);

	    if (getDebug())  {
		printMsgType(MessageType.GET_DURABLES, "GET_DURABLES");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION 
			+ "=" 
			+ topicName);
		if (durName != null)  {
		    Globals.stdOutPrintln("\t" 
			+ MessageType.JMQ_DURABLE_NAME 
			+ "=" 
			+ durName);
	        }
	    }
            sender.send(mesg);

        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }

    public Vector receiveGetDurablesReplyMessage() throws BrokerAdminException {
	return receiveGetDurablesReplyMessage(true);
    }

    public Vector receiveGetDurablesReplyMessage(boolean waitForResponse) 
	throws BrokerAdminException {

        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetDurablesReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.GET_DURABLES_REPLY,
					"GET_DURABLES_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
		        printDurableInfoList((Vector)obj);
		    }
                    return (Vector)obj;
                }
            }

        } catch (Exception e) {
            handleReceiveExceptions(e);
        }

        return null;
    }


    public void sendDestroyDurableMessage(String durName, String clientID)
                                throws BrokerAdminException {
        BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendDestroyDurableMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.DESTROY_DURABLE);
            mesg.setStringProperty(MessageType.JMQ_DURABLE_NAME, durName);
            if (clientID != null) {
                mesg.setStringProperty(MessageType.JMQ_CLIENT_ID, clientID);
            }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.DESTROY_DUR,
						MessageType.DESTROY_DURABLE_REPLY,
						"DESTROY_DURABLE_REPLY");
	    statusEvent.setDurableName(durName);
	    statusEvent.setClientID(clientID);

	    if (getDebug())  {
		printMsgType(MessageType.DESTROY_DURABLE, "DESTROY_DURABLE");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DURABLE_NAME 
			+ "=" 
			+ durName);
		Globals.stdOutPrintln("\t" 
			+ MessageType.JMQ_CLIENT_ID 
			+ "=" 
			+ clientID);
	    }
            sender.send(mesg);

        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }

    public void sendPurgeDurableMessage(String durName, String clientID)
                                throws BrokerAdminException {
        BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendPurgeDurableMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.PURGE_DURABLE);
            mesg.setStringProperty(MessageType.JMQ_DURABLE_NAME, durName);
            if (clientID != null) {
                mesg.setStringProperty(MessageType.JMQ_CLIENT_ID, clientID);
            }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.PURGE_DUR,
						MessageType.PURGE_DURABLE_REPLY,
						"PURGE_DURABLE_REPLY");
	    statusEvent.setDurableName(durName);
	    statusEvent.setClientID(clientID);

	    if (getDebug())  {
		printMsgType(MessageType.PURGE_DURABLE, "PURGE_DURABLE");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DURABLE_NAME 
			+ "=" 
			+ durName);
		Globals.stdOutPrintln("\t" 
			+ MessageType.JMQ_CLIENT_ID 
			+ "=" 
			+ clientID);
	    }
            sender.send(mesg);

        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }


    public void receiveDestroyDurableReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveDestroyDurableReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.DESTROY_DURABLE_REPLY,
					"DESTROY_DURABLE_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }

    public void receivePurgeDurableReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receivePurgeDurableReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.PURGE_DURABLE_REPLY,
					"PURGE_DURABLE_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }


    public void sendGetMetricsMessage(String svcName) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendGetMetricsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_METRICS);
	    if (svcName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_SERVICE_NAME, svcName);

	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.METRICS_SVC,
						MessageType.GET_METRICS_REPLY,
						"GET_METRICS_REPLY");
	        statusEvent.setServiceName(svcName);
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.METRICS_BKR,
						MessageType.GET_METRICS_REPLY,
						"GET_METRICS_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.GET_METRICS, "GET_METRICS");
		if (svcName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_SERVICE_NAME 
			+ "=" 
			+ svcName);
		}
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void sendGetMetricsMessage(String dstName, int dstType) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendGetMetricsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_METRICS);
	    mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	    mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.METRICS_DST,
						MessageType.GET_METRICS_REPLY,
						"GET_METRICS_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.GET_METRICS, "GET_METRICS");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION
			+ "=" 
			+ dstName);
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DEST_TYPE
			+ "=" 
			+ dstType);
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public Object receiveGetMetricsReplyMessage() 
					throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveGetMetricsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_METRICS_REPLY,
					"GET_METRICS_REPLY");

	    String metricType = mesg.getStringProperty(MessageType.JMQ_BODY_TYPE);
            Object obj;

            if ((obj = mesg.getObject()) != null) {
		if ("DESTINATION".equals(metricType))  {
                    if (obj instanceof DestMetricsCounters)
                        return (DestMetricsCounters)obj;
		}

		if ((metricType == null) || ("SERVICE".equals(metricType)))  {
                    if (obj instanceof MetricCounters)
                        return (MetricCounters)obj;
		}
            }

        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendReloadClusterMessage() 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendReloadClusterMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.RELOAD_CLUSTER);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.RELOAD_CLS,
						MessageType.RELOAD_CLUSTER_REPLY,
						"RELOAD_CLUSTER_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.RELOAD_CLUSTER, "RELOAD_CLUSTER");
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveReloadClusterReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveReloadClusterReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.RELOAD_CLUSTER_REPLY,
					"RELOAD_CLUSTER_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }

    public void sendClusterChangeMasterMessage(Properties props)
    throws BrokerAdminException {

        if (getDebug()) {
           Globals.stdOutPrintln("***** sendClusterChangeMasterMessage *****");
        }

        ObjectMessage mesg = null;
        checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty(MessageType.JMQ_MESSAGE_TYPE,
                MessageType.CHANGE_CLUSTER_MASTER_BROKER);
            mesg.setStringProperty(MessageType.JMQ_CLUSTER_NEW_MASTER_BROKER,
                props.getProperty(BrokerConstants.PROP_NAME_BKR_CLS_CFG_SVR));
            mesg.setObject(props);

            statusEvent = createStatusEvent(BrokerCmdStatusEvent.CLUSTER_CHANGE_MASTER,
                        MessageType.CHANGE_CLUSTER_MASTER_BROKER_REPLY,
                        "CHANGE_CLUSTER_MASTER_BROKER_REPLY");
            statusEvent.setBrokerProperties(props);

            if (getDebug())  {
                printMsgType(MessageType.CHANGE_CLUSTER_MASTER_BROKER, "CHANGE_CLUSTER_MASTER_BROKER");
                Globals.stdOutPrintln("\tProperties=" + props.toString());
            }
            sender.send(mesg);
        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }

    public void receiveClusterChangeMasterReplyMessage()
    throws BrokerAdminException {

        if (getDebug()) {
            Globals.stdOutPrintln("***** receiveClusterChangeMasterReplyMessage *****");
        }
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);
            mesg.acknowledge();
            clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.CHANGE_CLUSTER_MASTER_BROKER_REPLY,
                    "CHANGE_CLUSTER_MASTER_BROKER_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }


    public void sendGetClusterMessage(boolean listBkr) 
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetClusterMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_CLUSTER);


	    if (listBkr)  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_BKR,
						MessageType.GET_CLUSTER_REPLY,
						"GET_CLUSTER_REPLY");
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_BKR,
						MessageType.GET_CLUSTER_REPLY,
						"GET_CLUSTER_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.GET_CLUSTER, "GET_CLUSTER");
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetClusterReplyMessage() throws BrokerAdminException {
	return receiveGetClusterReplyMessage(true);
    }

    public Vector receiveGetClusterReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetClusterReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_CLUSTER_REPLY, "GET_CLUSTER_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
		        printClusterList((Vector)obj);
		    }
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendGetJMXConnectorsMessage(String name) 
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetJMXConnectorsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_JMX);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_JMX,
						MessageType.GET_JMX_REPLY,
						"GET_JMX_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.GET_JMX, "GET_JMX");
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetJMXConnectorsReplyMessage() throws BrokerAdminException {
	return receiveGetJMXConnectorsReplyMessage(true);
    }

    public Vector receiveGetJMXConnectorsReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetJMXConnectorsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_JMX_REPLY, "GET_JMX_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
		        printJMXList((Vector)obj);
		    }
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendGetMessagesMessage(String dstName, int dstType, 
						boolean getBody,
						String msgID,
						Long startMessageIndex, 
						Long maxNumMsgsRetrieved) 
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetMessagesMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_MESSAGES);
	    mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	    mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 
            mesg.setBooleanProperty(MessageType.JMQ_GET_MSG_BODY, getBody);
	    if (msgID != null)  {
                mesg.setStringProperty(MessageType.JMQ_MESSAGE_ID, msgID);
	    }

	    if (startMessageIndex != null)  {
                mesg.setLongProperty(MessageType.JMQ_START_MESSAGE_INDEX, 
						startMessageIndex.longValue());
	    }
	    if (maxNumMsgsRetrieved != null)  {
                mesg.setLongProperty(MessageType.JMQ_MAX_NUM_MSGS_RETRIEVED, 
						maxNumMsgsRetrieved.longValue());
	    }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.GET_MSGS,
						MessageType.GET_MESSAGES_REPLY,
						"GET_MESSAGE_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.GET_MESSAGES, "GET_MESSAGES");
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetMessagesReplyMessage() throws BrokerAdminException {
	return receiveGetMessagesReplyMessage(true);
    }

    public Vector receiveGetMessagesReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetMessagesReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_MESSAGES_REPLY, 
							"GET_MESSAGES_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    /*
		    if (getDebug())  {
		        printJMXList((Vector)obj);
		    }
		    */
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendDestroyMessagesMessage(String dstName, int dstType, String msgID) 
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendDestroyMessagesMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.DELETE_MESSAGE);
	    mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	    mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType); 
	    if (msgID != null)  {
                mesg.setStringProperty(MessageType.JMQ_MESSAGE_ID, msgID);
	    }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.DELETE_MSG,
						MessageType.DELETE_MESSAGE_REPLY,
						"DELETE_MESSAGE_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.DELETE_MESSAGE, "DELETE_MESSAGE");
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveDestroyMessagesReplyMessage() throws BrokerAdminException {
	receiveDestroyMessagesReplyMessage(true);
    }

    public void receiveDestroyMessagesReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveDestroyMessagesReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.DELETE_MESSAGE_REPLY, 
							"DELETE_MESSAGE_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }

    public void sendCommitTxnMessage(Long tid) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendCommitTxnMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.COMMIT_TRANSACTION);
            mesg.setLongProperty(MessageType.JMQ_TRANSACTION_ID, tid.longValue());

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.COMMIT_TXN,
						MessageType.COMMIT_TRANSACTION_REPLY,
						"COMMIT_TRANSACTION_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.COMMIT_TRANSACTION, "COMMIT_TRANSACTION");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_TRANSACTION_ID
			+ "=" 
			+ tid.longValue());
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveCommitTxnReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveCommitTxnReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.COMMIT_TRANSACTION_REPLY,
					"COMMIT_TRANSACTION_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }

    public void sendRollbackTxnMessage(Long tid, boolean processActiveConsumers) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendRollbackTxnMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.ROLLBACK_TRANSACTION);
            mesg.setLongProperty(MessageType.JMQ_TRANSACTION_ID, tid.longValue());
            if (processActiveConsumers) {
                mesg.setBooleanProperty(MessageType.JMQ_PROCESS_ACTIVE_CONSUMERS,
                                       Boolean.valueOf(true));
            }

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.ROLLBACK_TXN,
						MessageType.ROLLBACK_TRANSACTION_REPLY,
						"ROLLBACK_TRANSACTION_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.ROLLBACK_TRANSACTION, "ROLLBACK_TRANSACTION");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_TRANSACTION_ID
			+ "=" 
			+ tid.longValue());
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveRollbackTxnReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveRollbackTxnReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.ROLLBACK_TRANSACTION_REPLY,
					"ROLLBACK_TRANSACTION_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }


    public void sendGetTxnsMessage(Long tid, boolean showpartition) 
    throws BrokerAdminException {
        sendGetTxnsMessage(true, tid, showpartition);
    }

    public void sendGetTxnsMessage(boolean showpartition) 
    throws BrokerAdminException {
        sendGetTxnsMessage(false, null, showpartition);
    }

    /*
     * We have a flag to indicate whether a long value was passed in or
     * not.
     * This was necessary back when tid was a 'long'. Now that it's a
     * 'Long', this is no longer needed (can check for null), but keeping
     * the same logic until everything is finalized; doesn't hurt
     * to keep it this way...
     */
    private void sendGetTxnsMessage(boolean tid_specified, Long tid,
                                    boolean showpartition) 
                                    throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetTxnsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_TRANSACTIONS);
            if (showpartition) {
                mesg.setBooleanProperty(MessageType.JMQ_SHOW_PARTITION, Boolean.valueOf(true));
            }

	    if (tid_specified)  {
                mesg.setLongProperty(MessageType.JMQ_TRANSACTION_ID, tid.longValue());

	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_TXN,
						MessageType.GET_TRANSACTIONS_REPLY,
						"GET_TRANSACTIONS_REPLY");
	        statusEvent.setTid(tid.longValue());
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_TXN,
						MessageType.GET_TRANSACTIONS_REPLY,
						"GET_TRANSACTIONS_REPLY");
	    }

	    if (getDebug())  {
		printMsgType(MessageType.GET_TRANSACTIONS, "GET_TRANSACTIONS");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_TRANSACTION_ID
			+ "=");
		if (tid_specified)  {
		    Globals.stdOutPrintln(tid.toString());
		} else  {
	            Globals.stdOutPrintln("NOT SPECIFIED");
	        }
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetTxnsReplyMessage() throws BrokerAdminException {
	return receiveGetTxnsReplyMessage(true);
    }

    public Vector receiveGetTxnsReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetTxnsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.GET_TRANSACTIONS_REPLY, 
				"GET_TRANSACTIONS_REPLY");

            Object obj;

            obj = mesg.getObject();

	    if (getDebug())  {
	        int	quantity = 0;	
	        Globals.stdOutPrintln("obj returned: " + obj);
	        try {
	            quantity = mesg.getIntProperty(MessageType.JMQ_QUANTITY);
	        } catch (JMSException jmse)  {
	            Globals.stdOutPrintln("failed to get JMQ_QUANTITY: " + jmse);
	        }
	        Globals.stdOutPrintln("JMQ_QUANTTY: " + quantity);
	    }

            if (obj != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
		        printTxnInfoList((Vector)obj);
		    }
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendCompactDestinationMessage(String dstName, int dstType) 
				throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** sendCompactDestinationMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
            (MessageType.JMQ_MESSAGE_TYPE, MessageType.COMPACT_DESTINATION);
	    if (dstName != null)
                mesg.setStringProperty(MessageType.JMQ_DESTINATION, dstName);
	    if (dstType != -1)
                mesg.setIntProperty(MessageType.JMQ_DEST_TYPE, dstType);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.COMPACT_DST,
						MessageType.COMPACT_DESTINATION_REPLY,
						"COMPACT_DESTINATION_REPLY");
	    statusEvent.setDestinationName(dstName);
	    statusEvent.setDestinationType(dstType);

	    if (getDebug())  {
		printMsgType(MessageType.COMPACT_DESTINATION, "COMPACT_DESTINATION");
		Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_DESTINATION 
			+ "=" 
			+ dstName);
		Globals.stdOutPrintln("\t" 
			+ MessageType.JMQ_DEST_TYPE 
			+ "=" 
			+ dstType);
	    }

            sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }


    public void receiveCompactDestinationReplyMessage() 
				throws BrokerAdminException {
        if (getDebug())
	Globals.stdOutPrintln("***** receiveCompactDestinationReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.COMPACT_DESTINATION_REPLY,
				"COMPACT_DESTINATION_REPLY");
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }
    }

    /*
     * Send a GET_CONNECTIONS admin msg
     *
     * NOTE: The current GET_CONNECTIONS protocol only supports
     * only one of {JMQServiceName,JMQConnectionID} being set.
     */
    public void sendGetConnectionsMessage(String svcName, Long cxnId)
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendGetConnectionsMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_CONNECTIONS);

	    if (cxnId != null)  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.QUERY_CXN,
						MessageType.GET_CONNECTIONS_REPLY,
						"GET_CONNECTIONS_REPLY");
                mesg.setLongProperty(MessageType.JMQ_CONNECTION_ID, cxnId.longValue());
	        statusEvent.setCxnid(cxnId.longValue());
	    } else  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.LIST_CXN,
						MessageType.GET_CONNECTIONS_REPLY,
						"GET_CONNECTIONS_REPLY");
	    }


	    if (svcName != null)  {
	        mesg.setStringProperty(MessageType.JMQ_SERVICE_NAME, svcName);
	        statusEvent.setServiceName(svcName);
	    }

	    if (getDebug())  {
		printMsgType(MessageType.GET_CONNECTIONS, "GET_CONNECTIONS");
		if (svcName != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_SERVICE_NAME
			+ "=" 
			+ svcName);
		}
		if (cxnId != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_CONNECTION_ID
			+ "=" 
			+ cxnId.longValue());
		}
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public Vector receiveGetConnectionsReplyMessage() throws BrokerAdminException {
	return receiveGetConnectionsReplyMessage(true);
    }

    public Vector receiveGetConnectionsReplyMessage(boolean waitForResponse)
					throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveGetConnectionsReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);

            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, 
		MessageType.GET_CONNECTIONS_REPLY, "GET_CONNECTIONS_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
		    if (getDebug())  {
			printConnectionInfoList((Vector)obj);
		    }
                    return (Vector)obj;
		}
            }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }


    /*
     * Send a DESTROY_CONNECTION admin msg
     */
    public void sendDestroyConnectionMessage(Long cxnId)
			throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln ("***** sendDestroyConnectionMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.DESTROY_CONNECTION);

	    if (cxnId != null)  {
	        statusEvent = createStatusEvent(BrokerCmdStatusEvent.DESTROY_CXN,
						MessageType.DESTROY_CONNECTION_REPLY,
						"DESTROY_CONNECTION_REPLY");
                mesg.setLongProperty(MessageType.JMQ_CONNECTION_ID, cxnId.longValue());
	        statusEvent.setCxnid(cxnId.longValue());
	    }


	    if (getDebug())  {
		printMsgType(MessageType.DESTROY_CONNECTION, "DESTROY_CONNECTION");
		if (cxnId != null)  {
		    Globals.stdOutPrintln("\t"
			+ MessageType.JMQ_CONNECTION_ID
			+ "=" 
			+ cxnId.longValue());
		}
	    }
	    sender.send(mesg);
        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    public void receiveDestroyConnectionReplyMessage()
                                throws BrokerAdminException {
        if (getDebug())
        Globals.stdOutPrintln("***** receiveDestroyConnectionReplyMessage *****");
        Message mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
	    clearStatusEvent();
            checkReplyTypeStatus(mesg, MessageType.DESTROY_CONNECTION_REPLY,
					"DESTROY_CONNECTION_REPLY");
        } catch (Exception e) {
            handleReceiveExceptions(e);
        }
    }


    /*
     * Send DEBUG message to broker.
     * Parameters are:
     *  operation
     *  type
     *  id
     *  optional properties
     */
    public void sendDebugMessage(String cmd, String cmdarg, String target, 
			String targetType, Properties optionalProps) 
				throws BrokerAdminException {
	BrokerAdminException bae;

        if (getDebug()) Globals.stdOutPrintln("***** sendDebugMessage *****");
        ObjectMessage mesg = null;

	checkIfBusy();

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.DEBUG);

	    if (cmd != null)
	        mesg.setStringProperty(MessageType.JMQ_CMD, cmd);
	    if (cmdarg != null)
	        mesg.setStringProperty(MessageType.JMQ_CMDARG, cmdarg);
	    if (target != null)  {
	        mesg.setStringProperty(MessageType.JMQ_TARGET, target);
	    }
	    if (targetType != null)  {
	        mesg.setStringProperty(MessageType.JMQ_TARGET_TYPE, targetType);
	    }
	    if (optionalProps != null)
	        mesg.setObject(optionalProps);

	    statusEvent = createStatusEvent(BrokerCmdStatusEvent.DEBUG,
						MessageType.DEBUG_REPLY,
						"DEBUG_REPLY");

	    if (getDebug())  {
		printMsgType(MessageType.DEBUG, "DEBUG");
	    }
            sender.send(mesg);

        } catch (Exception e) {
	    handleSendExceptions(e);
        }
    }

    /*
     * Receive DEBUG_REPLY message from broker.
     * A Hashtable is returned, containing name=value pairs.
     */
    public Hashtable receiveDebugReplyMessage() throws BrokerAdminException {
	return receiveDebugReplyMessage(true);
    }

    public Hashtable receiveDebugReplyMessage(boolean waitForResponse) 
	throws BrokerAdminException {

        if (getDebug()) 
	Globals.stdOutPrintln("***** receiveDebugReplyMessage *****");
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);
            mesg.acknowledge();
	    clearStatusEvent();

	    checkReplyTypeStatus(mesg, MessageType.DEBUG_REPLY,
				"DEBUG_REPLY");

            Object obj;

            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Hashtable)
                    return (Hashtable)obj;
            }

        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

        return null;
    }

    private void printMsgType(int msgType, String msgTypeString)  {
        Globals.stdOutPrintln("\t"
		+ MessageType.JMQ_MESSAGE_TYPE
		+ "="
		+ msgType
		+ "("
		+ msgTypeString
		+ ")");
    }

    private void printDestinationInfoList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof DestinationInfo))  {
	        Globals.stdOutPrintln("\tprintDestinationInfoList: Vector contained object of type: "
				+ o.getClass().getName());
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    DestinationInfo dInfo = (DestinationInfo)o;

	    printDestinationInfo(dInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printDestinationInfo(DestinationInfo dstInfo)  {
        Globals.stdOutPrintln("\tDestinationInfo:");
        Globals.stdOutPrintln("\t  name=" + dstInfo.name);
        Globals.stdOutPrintln("\t  type=" + dstInfo.type);
        Globals.stdOutPrintln("\t  nMessages=" + dstInfo.nMessages);
        Globals.stdOutPrintln("\t  nMessageBytes=" + dstInfo.nMessageBytes);
        Globals.stdOutPrintln("\t  nConsumers=" + dstInfo.nConsumers);
        Globals.stdOutPrintln("\t  maxMessages=" + dstInfo.maxMessages);
        Globals.stdOutPrintln("\t  maxMessageBytes=" + dstInfo.maxMessageBytes);
        Globals.stdOutPrintln("\t  maxMessageSize=" + dstInfo.maxMessageSize);
        Globals.stdOutPrintln("\t  maxFailoverConsumers=" + dstInfo.maxFailoverConsumers);
        Globals.stdOutPrintln("\t  maxActiveConsumers=" + dstInfo.maxActiveConsumers);
        Globals.stdOutPrintln("\t  destScope=" + dstInfo.destScope);
        Globals.stdOutPrintln("\t  destLimitBehavior=" + dstInfo.destLimitBehavior);
        Globals.stdOutPrintln("\t  destCDP=" + dstInfo.destCDP);
        Globals.stdOutPrintln("\t  maxPrefetch=" + dstInfo.maxPrefetch);
        Globals.stdOutPrintln("\t  maxProducers=" + dstInfo.maxProducers);
        Globals.stdOutPrintln("\t  autocreated=" + dstInfo.autocreated);
        Globals.stdOutPrintln("\t  naConsumers=" + dstInfo.naConsumers);
        Globals.stdOutPrintln("\t  nfConsumers=" + dstInfo.nfConsumers);
        Globals.stdOutPrintln("\t  destState=" + dstInfo.destState);
    }

    private void printServiceInfoList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof ServiceInfo))  {
	        Globals.stdOutPrintln("\tprintServiceInfoList: Vector contained object of type: "
				+ o.getClass().getName());
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    ServiceInfo svcInfo = (ServiceInfo)o;

	    printServiceInfo(svcInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printServiceInfo(ServiceInfo svcInfo)  {
        Globals.stdOutPrintln("\tServiceInfo:");
        Globals.stdOutPrintln("\t  name=" + svcInfo.name);
        Globals.stdOutPrintln("\t  protocol=" + svcInfo.protocol);
        Globals.stdOutPrintln("\t  type=" + svcInfo.type);
        Globals.stdOutPrintln("\t  state=" + svcInfo.state);
        Globals.stdOutPrintln("\t  nConnections=" + svcInfo.nConnections);
        Globals.stdOutPrintln("\t  currentThreads=" + svcInfo.currentThreads);
        Globals.stdOutPrintln("\t  dynamicPort=" + svcInfo.dynamicPort);
        Globals.stdOutPrintln("\t  metrics=" + svcInfo.metrics);
        Globals.stdOutPrintln("\t  port=" + svcInfo.port);
        Globals.stdOutPrintln("\t  minThreads=" + svcInfo.minThreads);
        Globals.stdOutPrintln("\t  maxThreads=" + svcInfo.maxThreads);
    }

    private void printConnectionInfoList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof Hashtable))  {
	        Globals.stdOutPrintln("\tprintConnectionInfoList: Vector contained object of type: "
				+ o.getClass().getName()
				+ "(expected java.util.Hashtable)");
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    Hashtable cxnInfo = (Hashtable)o;

	    printConnectionInfo(cxnInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printConnectionInfo(Hashtable cxnInfo)  {
        Globals.stdOutPrintln("\tConnection Info:");

	for (Enumeration e = cxnInfo.keys() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();
	    String curValue;
	    Object tmpObj;

	    tmpObj = cxnInfo.get(curPropName);
	    curValue = tmpObj.toString();
    
            Globals.stdOutPrintln("\t  "
			+ curPropName
			+ "="
			+ curValue);
	}
    }

    private void printDurableInfoList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof DurableInfo))  {
	        Globals.stdOutPrintln("\tprintDurableInfoList: Vector contained object of type: "
				+ o.getClass().getName());
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    DurableInfo durInfo = (DurableInfo)o;

	    printDurableInfo(durInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printDurableInfo(DurableInfo durInfo)  {
        Globals.stdOutPrintln("\tDurableInfo:");
        Globals.stdOutPrintln("\t  name=" + durInfo.name);
        Globals.stdOutPrintln("\t  clientID=" + durInfo.clientID);
        Globals.stdOutPrintln("\t  isDurable=" + durInfo.isDurable);
        Globals.stdOutPrintln("\t  nMessages=" + durInfo.nMessages);
        Globals.stdOutPrintln("\t  isActive=" + durInfo.isActive);
        Globals.stdOutPrintln("\t  ConsumerInfo=" + durInfo.consumer);
    }

    private void printTxnInfoList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof Hashtable))  {
	        Globals.stdOutPrintln("\tprintTxnInfoList: Vector contained object of type: "
				+ o.getClass().getName()
				+ "(expected java.util.Hashtable)");
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    Hashtable txnInfo = (Hashtable)o;

	    printTxnInfo(txnInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printTxnInfo(Hashtable txnInfo)  {
        Globals.stdOutPrintln("\tTransaction Info:");

	for (Enumeration e = txnInfo.keys() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();
	    String curValue;
	    Object tmpObj;

	    tmpObj = txnInfo.get(curPropName);
	    curValue = tmpObj.toString();
    
            Globals.stdOutPrintln("\t  "
			+ curPropName
			+ "="
			+ curValue);
	}
    }



    private void printClusterList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof BrokerClusterInfo))  {
	        Globals.stdOutPrintln("\tprintClusterList: Vector contained object of type: "
				+ o.getClass().getName()
				+ "(expected BrokerClusterInfo)");
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    BrokerClusterInfo bkrClsInfo = (BrokerClusterInfo)o;

	    printBkrClsInfo(bkrClsInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printBkrClsInfo(BrokerClusterInfo bkrClsInfo)  {
        Globals.stdOutPrintln("\tBroker Cluster Info:");
    }


    private void printJMXList(Vector v)  {
	Enumeration e = v.elements();

	Globals.stdOutPrintln("\t************************");
	while (e.hasMoreElements()) {
	    Object o = e.nextElement();

	    if (!(o instanceof Hashtable))  {
	        Globals.stdOutPrintln("\tprintJMXList: Vector contained object of type: "
				+ o.getClass().getName()
				+ "(expected java.util.Hashtable)");
	        Globals.stdOutPrintln("\t************************");
		return;
	    }
	    Hashtable jmxInfo = (Hashtable)o;

	    printJMXInfo(jmxInfo);

	    if (e.hasMoreElements())
	        Globals.stdOutPrintln("");
	}
	Globals.stdOutPrintln("\t************************");
    }

    private void printJMXInfo(Hashtable jmxInfo)  {
        Globals.stdOutPrintln("\tJMX Connector Info:");

	for (Enumeration e = jmxInfo.keys() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();
	    String curValue;
	    Object tmpObj;

	    tmpObj = jmxInfo.get(curPropName);
	    curValue = tmpObj.toString();
    
            Globals.stdOutPrintln("\t  "
			+ curPropName
			+ "="
			+ curValue);
	}
    }

    public void setAssociatedObj(Object obj)  {
	this.aObj = obj;
    }

    public Object getAssociatedObj()  {
	return (aObj);
    }
}

