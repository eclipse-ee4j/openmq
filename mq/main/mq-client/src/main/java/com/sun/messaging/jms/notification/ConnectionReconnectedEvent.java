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
 * @(#)ConnectionReconnectedEvent.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.notification;

import com.sun.messaging.jms.Connection;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * MQ Connection Reconnected Event is generated and delivered to the event
 * listener if the MQ client runtime reconnected to a broker and an event
 * listener is set to the MQ connection.
 * <p>
 * The application can obtain the current broker's address from the API
 * provided.
 */
public class ConnectionReconnectedEvent extends ConnectionEvent {

    /**
     * Connection reconnected event code.
     */
    public static final String CONNECTION_RECONNECTED =
                  ClientResources.E_CONNECTION_RECONNECTED;


    /**
     * Connection reconnect event code - reconnected to the same broker.
     */
    //public static final String CONNECTION_RECONNECTED_SAME_BROKER =
    //              ClientResources.E_CONNECTION_RECONNECTED_SAME_BROKER;
    /**
     * Connection reconnect event code - reconnected to a different broker.
     */
    //public static final String CONNECTION_RECONNECTED_DIFF_BROKER =
    //              ClientResources.E_CONNECTION_RECONNECTED_DIFF_BROKER;

    /**
     * Construct a connection reconnect event.
     *
     * @param conn the connection associated with this event object.
     * @param evCode the event code that represents this event object.
     * @param evMessage the event message that describes this event object.

     */
    public ConnectionReconnectedEvent
        (Connection conn, String evCode, String evMessage) {

        super (conn, evCode, evMessage);
    }

}
