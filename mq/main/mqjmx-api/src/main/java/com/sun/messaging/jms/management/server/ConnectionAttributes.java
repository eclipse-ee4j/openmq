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
 * @(#)ConnectionAttributes.java	1.7 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on connection attributes.
 */
public class ConnectionAttributes {
    /** 
     * Connection ID
     */
    public static final String		CONNECTION_ID = "ConnectionID";

    /** 
     * Client ID
     */
    public static final String		CLIENT_ID = "ClientID";

    /** 
     * Client Platform
     */
    public static final String		CLIENT_PLATFORM = "ClientPlatform";

    /** 
     * Connection Host
     */
    public static final String		HOST = "Host";

    /** 
     * Number of connections
     */
    public static final String		NUM_CONNECTIONS = "NumConnections";

    /** 
     * Number of connections created
     */
    public static final String		NUM_CONNECTIONS_OPENED = "NumConnectionsOpened";

    /** 
     * Number of connections rejected
     */
    public static final String		NUM_CONNECTIONS_REJECTED = "NumConnectionsRejected";

    /** 
     * Number of consumers
     */
    public static final String		NUM_CONSUMERS = "NumConsumers";

    /** 
     * Number of producers
     */
    public static final String		NUM_PRODUCERS = "NumProducers";

    /** 
     * Connection Port
     */
    public static final String		PORT = "Port";

    /** 
     * Service name
     */
    public static final String		SERVICE_NAME = "ServiceName";

    /** 
     * Creation Timestamp
     */
    public static final String		CREATION_TIME = "CreationTime";

    /** 
     * User name.
     */
    public static final String		USER = "User";

    /*
     * Class cannot be instantiated
     */
    private ConnectionAttributes() {
    }
    
}
