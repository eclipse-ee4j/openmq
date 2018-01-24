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
 * @(#)ConnectionClosingEvent.java	1.3 07/02/07
 */ 

package com.sun.messaging.jms.notification;

import com.sun.messaging.jms.Connection;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * MQ Connection closing Event.  This event is generated (if application
 * had set a connection event listener) when MQ client runtime received
 * a notification from MQ broker that a connection is about to be closed
 * due to a soft shutdown.
 */
public class ConnectionClosingEvent extends ConnectionEvent {

    /**
     * Connection closing event code - admin requested shutdown.
     */
    public static final String CONNECTION_CLOSING_ADMIN =
                               ClientResources.E_CONNECTION_CLOSING_ADMIN;

    private long closingTimePeriod = 0;

    /**
     * Construct a ConnectionClosingEvent object associated with the
     * connection specified.
     * @param conn the connection associated with the closing event.
     * @param evCode the event code that represents this event object.
     * @param evMessage the event message that describes this event object.
     * @param timePeriod the closing time period (in milli secs) since the
     *                   broker announces the connection is to be closed.
     */
    public ConnectionClosingEvent
        (Connection conn, String evCode, String evMessage, long timePeriod) {

        super (conn, evCode, evMessage);
        this.closingTimePeriod = timePeriod;
    }

    /**
     * Get the connection closing time period in milli seconds.  The time
     * period is calculated from the announcement time by broker.
     *
     * @return the closing time period.
     */
    public long getClosingTimePeriod() {
        return this.closingTimePeriod;
    }

}
