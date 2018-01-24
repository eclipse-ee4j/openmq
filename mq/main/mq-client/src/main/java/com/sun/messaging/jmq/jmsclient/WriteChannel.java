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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

//import com.sun.messaging.AdministeredObject;
import java.io.IOException;


public class WriteChannel {

    private boolean debug = Debug.debug;

    private ProtocolHandler protocolHandler = null;
    //private ReadChannel readChannel = null;
    //private InterestTable interestTable = null;
    private ConnectionImpl connection = null;
    /**
     * flow control vars.
     */
    public static final String JMQSize = "JMQSize";

    //private boolean shouldPause = false;
    private int flowCount = -1;

    protected boolean turnOffFlowControl = false;
    
    protected boolean noFlowControl = false;
    
    public WriteChannel ( ConnectionImpl conn ) {
        this.connection = conn;
        this.protocolHandler = conn.getProtocolHandler();
        //this.readChannel = conn.getReadChannel();
        //this.interestTable = conn.getInterestTable();

        if ( System.getProperty("NoimqProducerFlowControl") != null ) {
            turnOffFlowControl = true;
        }
        
        if ( System.getProperty("imq.producer.flowControl.disabled") != null) {
        	noFlowControl = true;
        	ConnectionImpl.getConnectionLogger().info("Producer flow control is turned off.");
        }
    }

    //protected void
    //setReadChannel (ReadChannel readChannel) {
        //this.readChannel = readChannel;
    //}

    /**
     * Register interest to the broker.
     */
    protected void
    addInterest (Consumer consumer) throws JMSException {
        protocolHandler.addInterest ( consumer );
    }

    protected void
    removeInterest (Consumer consumer) throws JMSException {
        protocolHandler.removeInterest(consumer);
    }

    protected void
    unsubscribe (String durableName) throws JMSException {
        protocolHandler.unsubscribe (durableName);
    }

    protected void writeJMSMessage(Message message, AsyncSendCallback asynccb)
    throws JMSException {
		
        if (this.noFlowControl) {
            protocolHandler.writeJMSMessage(message, asynccb);
        } else if (turnOffFlowControl && 
                   connection.getBrokerProtocolLevel() < PacketType.VERSION350) {
            protocolHandler.writeJMSMessage(message, asynccb);
        } else {
            sendWithFlowControl(message, asynccb);
        }
    }

    /**
     * The follwing methods are for producer flow control.
     * This method is called by ReadChannel when it received
     * RESUME_FLOW packet from the broker.
     */
    protected void
    updateFlowControl (ReadOnlyPacket pkt) throws JMSException {

        int jmqSize = -1;

        try {
            Integer prop = (Integer) pkt.getProperties().get(JMQSize);
            if ( prop != null ) {
                jmqSize = prop.intValue();
            }
        } catch (IOException e) {
        ExceptionHandler.handleException(e, ClientResources.X_PACKET_GET_PROPERTIES, true);

        } catch (ClassNotFoundException e) {
        ExceptionHandler.handleException(e, ClientResources.X_PACKET_GET_PROPERTIES, true);
        }

        setFlowCount (jmqSize);
    }

    private synchronized void setFlowCount (int jmqSize) {
        flowCount = jmqSize;
        notifyAll();
    }

    /**
     * Send a message with flow control feature.
     */
    private void sendWithFlowControl (Message message, AsyncSendCallback asynccb) 
    throws JMSException {

        /**
         * wait until allow to send.
         */
        pause(message, true);

        /**
         * send message.
         */
        protocolHandler.writeJMSMessage (message, asynccb);
    }

    private synchronized void pause (Message message, boolean block)
    throws JMSException {

        while (flowCount == 0) {

            if (debug) {
                Debug.println(
                    "WriteChannel : Waiting for RESUME_FLOW with bock="+block);
            }
            if (!block) {
                throw new JMSException("XXXWOULD-BLOCK");
            }

            try {
                wait();
            }
            catch (InterruptedException e) {
                ;
            }
        }

        if (debug) {
            Debug.println("WriteChannel : wait() returned...");
        }

        if (flowCount > 0) {
            flowCount--;
        }

        if (flowCount == 0) {
            ( (MessageImpl) message).getPacket().setFlowPaused(true);
        }
        else {
            ( (MessageImpl) message).getPacket().setFlowPaused(false);
        }
    }

    protected void close() {
        if (debug) {
            Debug.println(
                "WriteChannel.close() : Waking up blocked producers");
        }

        // Set flow count to -1 to unblock the producers waiting for
        // RESUME_FLOW. There is no need to throw an exception
        // directly from the pause() method because
        // protocolHandler.writeJMSMessage() is guaranteed to fail
        // down the line...

        setFlowCount(-1);
    }

}
