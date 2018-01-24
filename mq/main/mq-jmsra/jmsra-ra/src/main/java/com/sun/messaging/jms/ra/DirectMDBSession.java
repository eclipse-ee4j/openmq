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

import javax.jms.*;

import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;

/**
 *
 */
public class DirectMDBSession extends DirectSession {
    
    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectMDBSession";

    /** Creates a new instance of DirectMDBSession */
    public DirectMDBSession(DirectConnection dc,
            JMSService jmsservice, long sessionId, SessionAckMode ackMode)
    throws JMSException {
        super (dc, jmsservice, sessionId, ackMode);
    }

    protected void _initSession() {
        _loggerOC.entering(_className, "constructor():_init()");
    }
    /**
     *  Deliver a message from this DirectSession - only one thread can do this
     *  at a time.
     */
    protected synchronized JMSAck _deliverMessage(
            javax.jms.MessageListener msgListener, JMSPacket jmsPacket,
            long consumerId) throws ConsumerClosedNoDeliveryException {
        JMSAck jmsAck = null;
        if (this.enableThreadCheck) {
            //Relies on the *same* thread being used to deliver all messages
            //while this sesion is alive
            long tId = Thread.currentThread().getId();
            if (this.deliverThreadId == 0L) {
                //first time
                this.deliverThreadId = tId;
            } else {
                if (this.deliverThreadId != tId) {
                    throw new RuntimeException("Invalid to call deliver from two different threads!");
                }
            }
        }
        javax.jms.Message jmsMsg = null;
        if (msgListener == null) {
            throw new RuntimeException("DirectConsumer:MessageListener not set!");
        }
        if (jmsPacket == null){
            throw new RuntimeException(
                    "DirectConsumer:JMSPacket is null!");
        }
        try {
            jmsMsg = DirectPacket.constructMessage(jmsPacket, consumerId,
                    this, this.jmsservice, false);
        } catch (Exception e) {
            
        }
                
        if (jmsMsg == null) {
            throw new RuntimeException(
                    "DirectConsumer:JMS Message in Packet is null!");
        }
        try {
            this.inDeliver = true;
            msgListener.onMessage(jmsMsg);
            //this.ds._deliverMessage(this.msgListener, jmsMsg);
            this.inDeliver = false;
            if (this.ackMode != SessionAckMode.CLIENT_ACKNOWLEDGE) {
                jmsAck = new DirectAck(this.connectionId, this.sessionId,
                        consumerId,
                        ((DirectPacket)jmsMsg).getReceivedSysMessageID(),
                        this._getTransactionId(),
                        JMSService.MessageAckType.ACKNOWLEDGE);
            }
        } catch (Exception e){
            System.out.println(
                    "DirectConsumer:Caught Exception delivering message"
                    + e.getMessage());;
        }
        return jmsAck;
    }

    protected synchronized void _acknowledgeMDBMessage()
    throws Exception {
        
    }
}

