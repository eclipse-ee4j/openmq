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
 * @(#)iASBrokerAdmin.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import javax.jms.*;
import java.util.Vector;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.QueueConnectionFactory;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.util.admin.*;

/**
 * This class is a simplified verion of BrokerAdmin.
 * Its main purpose is to provide basic functionality so that iAS
 * can use this class to do some basic iMQ administration. 
 * 
 * This class supports the following functionality:
 * 1.  shutdown broker
 * 2.  creating a destination
 * 3.  listing destinations
 */
public class iASBrokerAdmin {

    public final static String          DEFAULT_ADMIN_USERNAME  = "admin";
    public final static String          DEFAULT_ADMIN_PASSWD    = "admin";

    private QueueConnectionFactory	qcf;
    private QueueConnection		connection;
    private QueueSession	       	session;
    private Queue			requestQueue;
    private TemporaryQueue	       	replyQueue;
    private QueueSender	       		sender;
    protected QueueReceiver	       	receiver;

    private int timeout			= 5000;


    public iASBrokerAdmin(String host, String port) {
	try {
	    qcf = new QueueConnectionFactory();
            qcf.setConnectionType(ClientConstants.CONNECTIONTYPE_ADMIN);
	    qcf.setProperty(ConnectionConfiguration.imqBrokerHostName, host);
	    qcf.setProperty(ConnectionConfiguration.imqBrokerHostPort, port);

            connection = qcf.createQueueConnection
	        (DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWD); 
	    connection.start();

	    session = connection.createQueueSession(false, 
		Session.CLIENT_ACKNOWLEDGE);
	    requestQueue = session.createQueue(MessageType.JMQ_ADMIN_DEST);
	    replyQueue = session.createTemporaryQueue();

	    sender = session.createSender(requestQueue);
	    sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	    receiver = session.createReceiver(replyQueue);

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void sendHelloMessage() throws BrokerAdminException {
        ObjectMessage mesg = null;

	try {
	    mesg = session.createObjectMessage();
	    mesg.setJMSReplyTo(replyQueue);		
	    mesg.setIntProperty
		(MessageType.JMQ_MESSAGE_TYPE, MessageType.HELLO);
	    sender.send(mesg);	

        } catch (Exception e) {
	    BrokerAdminException bae = new BrokerAdminException(0);
	    bae.setLinkedException(e);
	    throw bae;
        }
    }

    public void receiveHelloReplyMessage() throws BrokerAdminException {
        Message mesg = null;

        try {
            mesg = receiver.receive(timeout);
            mesg.acknowledge();
	    checkReplyTypeStatus(mesg, MessageType.HELLO_REPLY, 
		"HELLO_REPLY");

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public void sendGetDestinationsMessage() throws BrokerAdminException {
        ObjectMessage mesg = null;

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
                (MessageType.JMQ_MESSAGE_TYPE, MessageType.GET_DESTINATIONS);
	    sender.send(mesg);

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public Vector receiveGetDestinationsReplyMessage() 
	throws BrokerAdminException {
        ObjectMessage mesg = null;

        try {
            mesg = (ObjectMessage)receiver.receive(timeout);
            mesg.acknowledge();
	    checkReplyTypeStatus(mesg, MessageType.GET_DESTINATIONS_REPLY, 
		"GET_DESTINATIONS_REPLY");

            Object obj;
            if ((obj = mesg.getObject()) != null) {
                if (obj instanceof Vector)  {
                    return (Vector)obj;
		}
            }

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }

        return null;
    }

    public void sendCreateDestinationMessage(DestinationInfo dstInfo) 
	throws BrokerAdminException {
        ObjectMessage mesg = null;

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
	    	(MessageType.JMQ_MESSAGE_TYPE, MessageType.CREATE_DESTINATION);
            mesg.setObject(dstInfo);
            sender.send(mesg);

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public void receiveCreateDestinationReplyMessage() 
	throws BrokerAdminException {
        Message mesg = null;

        try {
            mesg = receiver.receive(timeout);
            mesg.acknowledge();
	    checkReplyTypeStatus(mesg, MessageType.CREATE_DESTINATION_REPLY,
		"CREATE_DESTINATION_REPLY");

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public void sendShutdownMessage() throws BrokerAdminException {
        ObjectMessage mesg = null;

        try {
            mesg = session.createObjectMessage();
            mesg.setJMSReplyTo(replyQueue);
            mesg.setIntProperty
	        (MessageType.JMQ_MESSAGE_TYPE, MessageType.SHUTDOWN);
            sender.send(mesg);

        } catch (Exception e) {
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public void receiveShutdownReplyMessage() throws BrokerAdminException {
        Message mesg = null;

        try {
            mesg = receiver.receive(timeout);

            /* 
             * Message can be null if receive() times out.
             * On shutdownReply, it can be null if the broker shuts
             * down prior to this method receiving the message.  If the 
             * message is null, simply treat it as successful.  This
             * is done in checkReplyTypeStatus() method.
             */
	    checkReplyTypeStatus(mesg, MessageType.SHUTDOWN_REPLY,
	    	 "SHUTDOWN_REPLY");

        } catch (JMSException jmse) {
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
            BrokerAdminException bae = new BrokerAdminException(0);
            bae.setLinkedException(e);
            throw bae;
        }
    }

    public void close() {
	try {
	    sender.close();
	    receiver.close();
	    session.close();
	    connection.close();

        } catch (Exception e) {
	    e.printStackTrace();
        }
    }

    private void checkReplyTypeStatus
	(Message mesg, int msgType, String msgTypeString) {

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

	/*
	 * Fetch reply message type
	 */
	try  {
            actualMsgType = mesg.getIntProperty(MessageType.JMQ_MESSAGE_TYPE);
	} catch (JMSException jmse)  {
            jmse.printStackTrace();
	    System.exit(1);
	}
        
	/*
	 * Fetch reply status code
	 */
	try  {
            actualReplyStatus = mesg.getIntProperty(MessageType.JMQ_STATUS);
	} catch (JMSException jmse)  {
            jmse.printStackTrace();
	    System.exit(1);
	}

	/*
	 * Both values must be correct
	 */
	if ((msgType == actualMsgType) && 
	    (actualReplyStatus == MessageType.OK)) {
	    return;
	}

	System.out.println("Error occurred while checking the reply.");
	System.exit(1);
    }
}
