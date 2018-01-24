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
 * @(#)ConnectionEvent.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.notification;

import com.sun.messaging.jms.Connection;

/**
 * MQ Connection Event.  This is the super class for all MQ connection
 * events. MQ may notify an application when a connection event is
 * about to occur or occurred.
 *
 * <p>
 * The following are a list of connection
 * notification types that defined in MQ hawk release:
 * <p>
 *
 * 1. Connection closing in "time period":
 * <UL>
 *    <li>SHUTDOWN (admin requested shutdown)
 * </UL>
 * <p>
 *
 * 2. Connection closed because of:
 * <UL>
 *   <li>    SHUTDOWN (admin requested shutdown)
 *   <li>    RESTART (admin requested restart)
 *   <li>    ERROR (server error, e.g. out of memory)
 *   <li>    ADMIN  (admin killed connection)
 *   <li>    BROKER_DOWN (broker crash)
 * </UL>
 *<p>
 *
 *
 * 3. Reconnected:
 * <UL>
 *   <li>    RECONNECTED to a broker
 * </UL>
 * <p>
 *
 * 4. Reconnect Failed:
 * <UL>
 *   <li>    RECONNECT_FAILED to a broker
 * </UL>
 */
public class ConnectionEvent extends Event {

    private String brokerAddress = null;

    /**
     * The connection object that associated with this event.
     */
    protected transient Connection connection = null;

    /**
     * Construct a MQ connection event.
     *
     * @param conn the connection associated with this event object.
     * @param evCode the event code that represents the this event object.
     * @param evMessage the event message that describes this event object.

     */
    public ConnectionEvent (Connection conn, String evCode, String evMessage) {
        super (conn, evCode, evMessage);

        this.connection = conn;
        this.brokerAddress = conn.getBrokerAddress();
    }

    /**
     * Get the current connection associated with this event.
     * @return the current connection associated with this event.
     */
    public Connection getConnection() {
        return this.connection;
    }

    /**
     * Get the broker's address that the event is associated with.
     *
     * @return the broker's address that the event is associated with.
     */
    public String getBrokerAddress() {
        return this.brokerAddress;
    }

    /**
     * Return com.sun.messaging.jms.Connection object.
     * @return the connection object associated with this event.
     */
    public Object getSource() {
        return this.getConnection();
    }
}
