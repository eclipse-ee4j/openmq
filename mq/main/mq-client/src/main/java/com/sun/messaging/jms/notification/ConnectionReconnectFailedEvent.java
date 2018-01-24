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
 * @(#)ConnectionReconnectFailedEvent.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.notification;

import com.sun.messaging.jms.Connection;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import javax.jms.JMSException;

/**
 * MQ Connection Reconnect Failed Event is generated and delivered to the
 * event listener if a MQ reconnect failed and an event listener is
 * set to the MQ connection.
 * <p>
 * The application can also obtain the current broker's address from the API
 * defined in the ConnectionEvent.
 */
public class ConnectionReconnectFailedEvent extends ConnectionEvent {

    // if there is any exception that caused the connection to be closed,
    //it is set to this event.
    private JMSException exception = null;

    /**
     * Connection reconnect failed event code.
     */
    public static final String CONNECTION_RECONNECT_FAILED =
                  ClientResources.E_CONNECTION_RECONNECT_FAILED;

    /**
     * Connection reconnect failed event code - reconnect to the
     * same broker failed.
     */
    //public static final String CONNECTION_RECONNECT_FAILED_SAME_BROKER =
    //              ClientResources.E_CONNECTION_RECONNECT_FAILED_SAME_BROKER;

    /**
     * Connection reconnect event code - reconnect to a different
     * broker failed.
     */
    //public static final String CONNECTION_RECONNECT_FAILED_DIFF_BROKER =
    //              ClientResources.E_CONNECTION_RECONNECT_FAILED_DIFF_BROKER;

    /**
     * Construct a connection reconnect failed event associated with the
     * specified connection.
     *
     * @param conn the connection associated with the reconnect event.
     *             MQ may automatically reconnect to the same broker
     *             or a different broker depends on the client runtime
     *             configuration.
     * @param evCode the event code that represents this event object.
     * @param evMessage the event message that describes this event object.
     * @param jmse the JMSException that caused this event.

     */
    public ConnectionReconnectFailedEvent
    (Connection conn, String evCode, String evMessage, JMSException jmse) {

        super (conn, evCode, evMessage);

        this.exception = jmse;
    }

    /**
     * Get the JMSException that caused the connection to be closed.
     *
     * @return the JMSException that caused the connection to be closed.
     *         return null if no JMSException associated with this event,
     *         such as connection closed caused by admin requested shutdown.
     */
    public JMSException getJMSException() {
        return exception;
    }

}
