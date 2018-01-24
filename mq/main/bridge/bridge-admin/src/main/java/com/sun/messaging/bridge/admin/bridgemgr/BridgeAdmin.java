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

package com.sun.messaging.bridge.admin.bridgemgr;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Locale;
import javax.jms.*;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminConn;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.bridge.api.BridgeCmdSharedReplyData;

/**
 * This class provides the convenient methods for sending messages to the 
 * MQ broker for MQ Bridge administration
 *
 * <P>
 * The information needed to create this object are:
 * <UL>
 * <LI>connection factory attributes
 * <LI>username/passwd
 * <LI>timeout (for receiving replies)
 * </UL>
 */
public class BridgeAdmin extends BrokerAdminConn {

    private BridgeMgrStatusEvent  statusEvent = null;
    private QueueSender           _sender = null;

    public BridgeAdmin(String brokerHost, int brokerPort) throws BrokerAdminException  {
        this(brokerHost, brokerPort, null, null, -1, false, -1, -1);
    }

    public BridgeAdmin(String brokerHost, int brokerPort, 
	                   String username, String passwd) 
		               throws BrokerAdminException  {
	    this(brokerHost, brokerPort, username, passwd, -1, false, -1, -1);
    }

    public BridgeAdmin(String brokerHost, int brokerPort, 
	                   String username, String passwd, int timeout) 
		               throws BrokerAdminException  {
        this(brokerHost, brokerPort, username, passwd, timeout, false, -1, -1);
    }

    public BridgeAdmin(String brokerAddress,
                       String username, String passwd, 
                       int timeout, boolean useSSL) 
		               throws BrokerAdminException  {
        this(brokerAddress, username, passwd, timeout, false, -1, -1, useSSL);
    }

    /**
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
    public BridgeAdmin(String brokerHost, int brokerPort, 
                       String username, String passwd, long timeout,
                       boolean reconnect, int reconnectRetries, long reconnectDelay) 
                       throws BrokerAdminException  {
        super(brokerHost, brokerPort, username, passwd, timeout, 
              reconnect, reconnectRetries, reconnectDelay);
    }

    /**
     * Instantiates a BridgeAdmin object. This is a wrapper for
     * this other constructor:
     *
     *  public BridgeAdmin(Properties, String, String, long)
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
    public BridgeAdmin(String brokerAddress, 
	                   String username, String passwd, 
                       long timeout,
                       boolean reconnect, int reconnectRetries, 
                       long reconnectDelay, boolean useSSL) 
                       throws BrokerAdminException  {

        super(brokerAddress, username, passwd, timeout, reconnect,
              reconnectRetries, reconnectDelay, useSSL);
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
    public BridgeAdmin(Properties brokerAttrs,
			String username, String passwd, 
			long timeout) 
		       throws BrokerAdminException  {
        super(brokerAttrs, username, passwd, timeout);
    }


    /**********************************************************
     * BEGIN impl of admin protocol specific abstract methods
     **********************************************************/
    public String getAdminQueueDest() {
        return AdminMessageType.JMQ_BRIDGE_ADMIN_DEST;
    }

    public String getAdminMessagePropNameMessageType() {
        return AdminMessageType.PropName.MESSAGE_TYPE;
    }

    public String getAdminMessagePropNameErrorString() {
        return AdminMessageType.PropName.ERROR_STRING;
    }

    public String getAdminMessagePropNameStatus() {
        return AdminMessageType.PropName.STATUS;
    }

    public int getAdminMessageStatusOK() {
        return Status.OK;
    }

    public int getAdminMessageTypeSHUTDOWN_REPLY() {
        return AdminMessageType.Type.LAST;
    }
    /**********************************************************
     * END impl of admin protocol specific abstract methods
     **********************************************************/

    /************************************************************
     * BEGIN impl of BridgeAdmin specific abstract methods
     ***********************************************************/
    public CommonCmdStatusEvent newCommonCmdStatusEvent(int type) {
        return new BridgeMgrStatusEvent(this, this, type);
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

    private BridgeMgrStatusEvent createStatusEvent(int type, int replyType,
                                                  String replyTypeString)  {
    CommonCmdStatusEvent cse = newCommonCmdStatusEvent(type);
    cse.setReplyType(replyType);
    cse.setReplyTypeString(replyTypeString);

    return (BridgeMgrStatusEvent)cse;
    }


    public void sendHelloMessage() throws BrokerAdminException  {

	if (getDebug()) Globals.stdOutPrintln("***** sendHelloMessage *****");

	checkIfBusy();

	ObjectMessage mesg = null;
	try {
	    mesg = session.createObjectMessage();
	    mesg.setJMSReplyTo(replyQueue);		
	    mesg.setIntProperty(AdminMessageType.PropName.MESSAGE_TYPE, AdminMessageType.Type.HELLO);
        statusEvent = createStatusEvent(BridgeMgrStatusEvent.Type.HELLO,
                                        AdminMessageType.Type.HELLO_REPLY,
                                        "HELLO_REPLY");

	    if (getDebug()) {
            printMsgType(AdminMessageType.Type.HELLO, "HELLO");
           Globals.stdOutPrintln("\t"
			       + AdminMessageType.PropName.PROTOCOL_LEVEL
			       + "=" 
			       + 440);
	    }
	    sender.send(mesg);	

    } catch (Exception e) {
	    handleSendExceptions(e);
    }

    }


    public void receiveHelloReplyMessage() throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveHelloReplyMessage() *****");

        Message mesg = null;
        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
            clearStatusEvent();
            checkReplyTypeStatus(mesg, AdminMessageType.Type.HELLO_REPLY, "HELLO_REPLY");
            TemporaryQueue replyTo = (TemporaryQueue)mesg.getJMSReplyTo();
            if (replyTo == null) {
                //no need to I18N - internal programming error
                Globals.stdErrPrintln("HELLO_REPLY protocol error: no JMSReplyTo");
                throw new BrokerAdminException(BrokerAdminException.MSG_REPLY_ERROR);
            }
            if (getDebug()) Globals.stdOutPrintln("*****Got replyQueue from broker: " + replyTo);
            _sender = session.createSender(replyTo);
            _sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            if (getDebug()) Globals.stdOutPrintln("***** Created a _sender: " + _sender);

	        isConnected = true;

        } catch (Exception e) {
	        handleReceiveExceptions(e);
        }
    }


    public void sendCommandMessage(String cmd, String bridgeName, String bridgeType, String linkName,
                                   int msgType, String msgTypeString, int eventType, 
                                   int replyType, String replyTypeString) 
				                   throws BrokerAdminException  {
        sendCommandMessage(cmd, bridgeName, bridgeType, linkName, 
                           msgType, msgTypeString, eventType,
                           replyType, replyTypeString, false);
    }
    public void sendCommandMessage(String cmd, String bridgeName, String bridgeType, String linkName,
                                   int msgType, String msgTypeString, int eventType, 
                                   int replyType, String replyTypeString, boolean debugMode)
				                   throws BrokerAdminException  {

        if (getDebug()) Globals.stdOutPrintln("***** send "+cmd+" Message *****");

        checkIfBusy();

        ObjectMessage mesg = null;
        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty(AdminMessageType.PropName.MESSAGE_TYPE, msgType);
	        if (bridgeName != null)  {
                mesg.setStringProperty(AdminMessageType.PropName.BRIDGE_NAME, bridgeName);
            }
	        if (bridgeType != null)  {
                mesg.setStringProperty(AdminMessageType.PropName.BRIDGE_TYPE, bridgeType);
            }
	        if (linkName != null)  {
                mesg.setStringProperty(AdminMessageType.PropName.LINK_NAME, linkName);
            }
            if (debugMode) {
                mesg.setBooleanProperty(AdminMessageType.PropName.DEBUG, Boolean.valueOf(debugMode));
            }
            Locale locale = Locale.getDefault();
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_LANG, locale.getLanguage());
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_COUNTRY, locale.getCountry());
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_VARIANT, locale.getVariant());

	        statusEvent = createStatusEvent(eventType, replyType, replyTypeString);

	        if (getDebug())  {
		        printMsgType(msgType, msgTypeString);
                Globals.stdOutPrintln("\t"
			            + AdminMessageType.PropName.BRIDGE_NAME
			            + "=" 
			            + bridgeName);
                Globals.stdOutPrintln("\t"
			            + AdminMessageType.PropName.BRIDGE_TYPE
			            + "=" 
			            + bridgeType);
                Globals.stdOutPrintln("\t"
			            + AdminMessageType.PropName.LINK_NAME
			            + "=" 
			            + linkName);

	        }
            _sender.send(mesg);
        } catch (Exception e) {
	        handleSendExceptions(e);
        }
    }

    public boolean receiveCommandReplyMessage(String cmd, int replyType, String replyTypeString) 
        throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receive "+replyTypeString+" Message() *****");

        Message mesg = null;
        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false);

            mesg.acknowledge();
            clearStatusEvent();
            checkReplyTypeStatus(mesg, replyType, replyTypeString);
            if (replyType == AdminMessageType.Type.START_REPLY) {
                return !mesg.getBooleanProperty(AdminMessageType.PropName.ASYNC_STARTED);
            }
            return true;

        } catch (Exception e) {
	        handleReceiveExceptions(e);
        }
        return true;
    }

    public ArrayList<BridgeCmdSharedReplyData> receiveListReplyMessage() throws BrokerAdminException {
        return receiveListReplyMessage(true);
    }

    public ArrayList<BridgeCmdSharedReplyData> receiveListReplyMessage(boolean waitForResponse)
        throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveListReplyMessage *****");

        ObjectMessage mesg = null;
        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);
            mesg.acknowledge();
            clearStatusEvent();
            checkReplyTypeStatus(mesg, AdminMessageType.Type.LIST_REPLY, "LIST_REPLY");

            if (getDebug()) Globals.stdErrPrintln("Received list reply: "+mesg);

            Object obj;
            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof ArrayList) {
                    return (ArrayList<BridgeCmdSharedReplyData>)obj;
                }
            }

            if (getDebug()) Globals.stdErrPrintln("Unexpected reply from broker: "+obj);

            throw new RuntimeException("Unexpected reply type "+obj+ " for LIST"); 

        } catch (Exception e) {
            handleReceiveExceptions(e);
        }

        return null;
    }

    public void sendDebugMessage(String debugArg, 
                                 String targetName, 
                                 Properties props)
                                 throws BrokerAdminException  {

        if (getDebug()) Globals.stdOutPrintln("***** send debug "+debugArg+" Message *****");

        checkIfBusy();

        ObjectMessage mesg = null;
        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty(AdminMessageType.PropName.MESSAGE_TYPE, AdminMessageType.Type.DEBUG);
            if (debugArg != null)  {
                mesg.setStringProperty(AdminMessageType.PropName.CMD_ARG, debugArg);
            }
            if (targetName != null)  {
                mesg.setStringProperty(AdminMessageType.PropName.TARGET, targetName);
            }
            if (props != null)  {
                mesg.setObject(props);
            }
            Locale locale = Locale.getDefault();
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_LANG, locale.getLanguage());
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_COUNTRY, locale.getCountry());
            mesg.setStringProperty(AdminMessageType.PropName.LOCALE_VARIANT, locale.getVariant());

            statusEvent = createStatusEvent(BridgeMgrStatusEvent.Type.DEBUG, 
                                            AdminMessageType.Type.DEBUG_REPLY, "DEBUG_REPLY");

            if (getDebug())  {
                printMsgType(AdminMessageType.Type.DEBUG, "DEBUG");
                Globals.stdOutPrintln("\t"
                        + AdminMessageType.PropName.CMD_ARG
                        + "="
                        + debugArg);
                Globals.stdOutPrintln("\t"
                        + AdminMessageType.PropName.TARGET
                        + "="
                        + targetName);
            }
            _sender.send(mesg);
        } catch (Exception e) {
            handleSendExceptions(e);
        }
    }

    public Hashtable receiveDebugReplyMessage() throws BrokerAdminException {
        return receiveDebugReplyMessage(true);
    }

    public Hashtable receiveDebugReplyMessage(boolean waitForResponse)
                                         throws BrokerAdminException {

        if (getDebug()) Globals.stdOutPrintln("***** receiveDebugReplyMessage *****");

        ObjectMessage mesg = null;
        try {
            mesg = (ObjectMessage)receiveCheckMessageTimeout(false, waitForResponse);
            mesg.acknowledge();
            clearStatusEvent();

            checkReplyTypeStatus(mesg, AdminMessageType.Type.DEBUG_REPLY, "DEBUG_REPLY");

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
                + AdminMessageType.PropName.MESSAGE_TYPE
                + "="
                + msgType
                + "(" + msgTypeString + ")");
    }

}
