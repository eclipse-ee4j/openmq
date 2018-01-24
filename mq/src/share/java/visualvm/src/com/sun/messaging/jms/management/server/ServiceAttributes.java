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
 * @(#)ServiceAttributes.java	1.7 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on service attributes.
 */
public class ServiceAttributes {
    /** 
     * Service Name
     */
    public static final String		NAME = "Name";

    /** 
     * Service State
     */
    public static final String		STATE = "State";

    /** 
     * String representation of service state
     */
    public static final String		STATE_LABEL = "StateLabel";

    /** 
     * Max threads
     */
    public static final String		MAX_THREADS = "MaxThreads";

    /** 
     * Min threads
     */
    public static final String		MIN_THREADS = "MinThreads";

    /** 
     * Number of connections created
     */
    public static final String		NUM_CONNECTIONS_OPENED = "NumConnectionsOpened";

    /** 
     * Number of connections rejected
     */
    public static final String		NUM_CONNECTIONS_REJECTED = "NumConnectionsRejected";

    /** 
     * Port
     */
    public static final String		PORT = "Port";

    /** 
     * Msg bytes in
     */
    public static final String		MSG_BYTES_IN = "MsgBytesIn";

    /** 
     * Msg bytes out
     */
    public static final String		MSG_BYTES_OUT = "MsgBytesOut";

    /** 
     * Number of active threads
     */
    public static final String		NUM_ACTIVE_THREADS = "NumActiveThreads";

    /** 
     * Number of msgs in
     */
    public static final String		NUM_MSGS_IN = "NumMsgsIn";

    /** 
     * Number of msgs out
     */
    public static final String		NUM_MSGS_OUT = "NumMsgsOut";

    /** 
     * Number of pkts in
     */
    public static final String		NUM_PKTS_IN = "NumPktsIn";

    /** 
     * Number of pkts out
     */
    public static final String		NUM_PKTS_OUT = "NumPktsOut";

    /** 
     * Number of services
     */
    public static final String		NUM_SERVICES = "NumServices";

    /** 
     * Number of connections
     */
    public static final String		NUM_CONNECTIONS = "NumConnections";

    /** 
     * Number of consumers
     */
    public static final String		NUM_CONSUMERS = "NumConsumers";

    /** 
     * Number of producers
     */
    public static final String		NUM_PRODUCERS = "NumProducers";

    /** 
     * Pkt bytes in
     */
    public static final String		PKT_BYTES_IN = "PktBytesIn";

    /** 
     * Pkt bytes out
     */
    public static final String		PKT_BYTES_OUT = "PktBytesOut";

    /** 
     * Thread pool model
     */
    public static final String		THREAD_POOL_MODEL = "ThreadPoolModel";

    /*
     * Class cannot be instantiated
     */
    private ServiceAttributes() {
    }
    
}
