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
 * @(#)SessionReader.java	1.34 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

import java.io.*;
import java.util.Enumeration;

import com.sun.messaging.AdministeredObject;

//XXX REVISIT
public class SessionReader extends ConsumerReader {

    private boolean debug = Debug.debug;

    protected SessionImpl session = null;
    
    //bug 6360068 - Class defines field that obscures a superclass field.
    //protected long timeout = 0;

    //the message that is delivering/delivered to the message consumer
    protected volatile MessageImpl currentMessage = null;

    public SessionReader (SessionImpl session) {
        super(session.getConnection(), session.getSessionQueue());
        this.session = session;

        //set timeout value
        if ( (session.acknowledgeMode==Session.DUPS_OK_ACKNOWLEDGE) &&
             (session.dupsOkAckOnTimeout == true) ) {

            if ( debug ) {
                Debug.println("**** setting dupsOkAckTimeout: " + session.dupsOkAckTimeout);
            }

            //set dups ok ack timeout.
            setTimeout(session.dupsOkAckTimeout);
        }

        init();
    }
    
    protected void setCurrentMessage (MessageImpl cm) {
    	this.currentMessage = cm;
    }

    /**
     * The session thread is waken up with a packet from sessionQueue
     *
     * @param packet the message packet to be delivered to consumer
     *
     * @exception IOException
     * @exception JMSException
     */
    protected void deliver(ReadOnlyPacket packet)
                      throws IOException, JMSException {
    	
        //XXX PROTOCOL2.1
        long interestId = 0;
        Consumer consumer = null;

        currentMessage = getJMSMessage ( packet );
        //get intID
        interestId = currentMessage.getInterestID();

        //delegate to message consumer
        consumer = session.getMessageConsumer(Long.valueOf(interestId));

        if (consumer == null) {
            consumer = session.getBrowserConsumer(Long.valueOf(interestId));
        }

        if (consumer != null) {
            consumer.onMessage(currentMessage);
        } else {
            if ( debug ) {
                String errorString = AdministeredObject.cr.getKString(
                                     ClientResources.X_CONSUMER_NOTFOUND);

                Debug.getPrintStream().println(errorString);
                packet.dump(Debug.getPrintStream());
            }
        }
    }

    /**
     * The session thread is waken up without a packet from sessionQueue
     *
     * @exception IOException
     * @exception JMSException
     */
    protected void deliver() throws IOException, JMSException {

        if ( sessionQueue.getIsClosed() == false ) {

            if ( session.dupsOkAckOnTimeout ) {
                //do dups ok ack.
                if ( debug ) {
                    Debug.println("*** Calling dups ok commit from timeout thread");
                }

                session.syncedDupsOkCommitAcknowledge();
            }

            if ( sessionQueue.isListenerSetLate() ) {
                //someone set message listener
                //after messages were delivered
                //to the receiveQueue of the
                //consumer.
                onMessageToLateListeners();

                //reset flag
                sessionQueue.setListenerLate(false);
            }
        }

    }

    /**
     * Check each message consumer and deliver messages
     * from receive queue to the message listener.
     * Loop through consumers table and call
     * consumer.onMessageToListenerFromReceiveQueue() if
     * the consumer has a message listener set.
     */
    protected void onMessageToLateListeners() throws JMSException {
        MessageConsumerImpl consumer = null;
        Enumeration enum2 = session.consumers.elements();
        while ( enum2.hasMoreElements() ) {
            consumer = (MessageConsumerImpl) enum2.nextElement();
            if ( consumer.getSyncReadFlag() == false ) {
                consumer.onMessageToListenerFromReceiveQueue();
            }
        }
    }

    /**
     * Convert JMQ packet to JMQ message type.
     *
     * @param pkt the packet to be converted.
     */
    protected MessageImpl
    getJMSMessage (ReadOnlyPacket pkt) throws JMSException {
        MessageImpl msg = protocolHandler.getJMSMessage( pkt );
        msg.setSession ( session );

        return msg;
    }

    public void dump (PrintStream ps) {
        ps.println ("------ SessionReader dump ------");
        ps.println ("Session ID: " + session.getSessionId() );
        super.dump(ps);
    }
}
