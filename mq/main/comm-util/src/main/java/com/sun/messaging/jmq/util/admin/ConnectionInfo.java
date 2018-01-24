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
 * @(#)ConnectionInfo.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

import com.sun.messaging.jmq.util.MetricCounters;

/**
 * ConnectionInfo encapsulates information about a JMQ Connection. It is used
 * to pass this information between the Broker and an administration client.
 *
 * This class has no updateable fields. The admin client should consider
 * it "read-only"
 */
public class ConnectionInfo extends AdminInfo {

    /**
     * Broker internal connection ID.
     */
    public byte[]	id;

    /**
     * Connection UUID
     */
    public long         uuid;

    /** 
     * Number of consumers on this connection
     */
    public int          nconsumers = 0;

    /** 
     * Number of producers on this connection
     */
    public int          nproducers = 0;

    /**
     * Remote port number
     */
    public int          remPort = 0;

    /**
     * IP address of client on the connection
     */
    public byte[]	remoteIP;

    /**
     * Metrics for connection
     */
    public MetricCounters metrics;

    /**
     * Name of user authenticated on connection. Null if not authenticated
     * by a user.
     */
    public String	user = "";

    /**
     * JMS ClientID of client on connection
     */
    public String	clientID = "";

    /**
     * User agent string
     */
    public String	userAgent = "";

    /**
     * Service this connection is connected to
     */
    public String	service = "";

    /**
     * Constructor for Consumer.
     */
    public ConnectionInfo() {
	reset();
    }

    public void reset() {
	id = null;
	remoteIP = null;
	metrics = null;
	user = "";
	clientID = "";
        service = "";
        userAgent = "";
    }


    /**
     * Return a string representation of the connection.
     * <pre>
     * dipol@client1(129.144.252.154:0)
     * </pre>
     *
     * @return String representation of connection.
     */
    public String toString() {

	return user + "@" + clientID + "(" +
	    com.sun.messaging.jmq.util.net.IPAddress.rawIPToString(remoteIP, true, true) + ")";
    }

}
