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
 */ 

package com.sun.messaging.jms.notification;

import com.sun.messaging.Destination;
import com.sun.messaging.jms.Connection;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * MQ Consumer Event.  
 * @since 4.5
 */
public class ConsumerEvent extends Event {

    /**
     * Consumer ready event code
     */
    public static final String CONSUMER_READY = ClientResources.E_CONSUMER_READY;


    /**
     * No consumer event code
     */
    public static final String CONSUMER_NOT_READY = ClientResources.E_CONSUMER_NOT_READY;


    /**
     * The broker address that sent the event
     */
    private String brokerAddress = null;

    /**
     * The connection object on which the event was received.
     */
    private transient Connection connection = null;


    /**
     * Construct a MQ consumer event.  
     *
     * <p><code>dest</code> is the {@link com.sun.messaging.Destination} 
     * object that was passed in 
     * {@link com.sun.messaging.jms.Connection#setConsumerEventListener}
     * and is what will be returned by {@link #getDestination()}
     *
     * <p><code>conn</code> is the {@link com.sun.messaging.jms.Connection} 
     * on which this event was received and is what will be returned by 
     * {@link #getConnection()}
     *
     * <p><code>evCode</code> is what will be returned by 
     * {@link #getEventCode()} which can be either 
     * {@link #CONSUMER_READY} or {@link #CONSUMER_NOT_READY}
     *
     * <p><code>evMessage</code> is a description of the <code>evCode</code>
     * and is what will be returned by {@link #getEventMessage()}
     *
     *
     * @param dest the destination on which the event occurred.
     * @param conn the connection on which the event was received
     * @param evCode the event code that represents this event object.
     * @param evMessage the event message that describes this event object.

     */
    public ConsumerEvent (Destination dest, Connection conn,
                          String evCode, String evMessage) {
        super (dest, evCode, evMessage);

        this.connection = conn;
        this.brokerAddress = conn.getBrokerAddress();
    }

    /**
     * Get the connection on which the event was received.
     * @return the connection on which the event was received.
     */
    public Connection getConnection() {
        return this.connection;
    }

    /**
     * Get the broker's address that sent the event.
     *
     * @return the broker's address that sent the event
     */
    public String getBrokerAddress() {
        return this.brokerAddress;
    }

    /**
     * Get the registered destination on which the event was occurred.
     * @return the registered destination on which the event was occurred.
     */
    public Destination getDestination() {
        return (Destination)this.getSource();
    }

}
