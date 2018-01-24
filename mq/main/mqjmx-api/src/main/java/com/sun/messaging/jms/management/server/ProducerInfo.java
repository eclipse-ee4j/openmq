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
 * @(#)ProducerInfo.java	1.3 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * This class contains constants/names for fields in the CompositeData
 * that is returned by the operations of the Producer Manager Monitor 
 * MBean.
 */
public class ProducerInfo implements java.io.Serializable  {

    /** 
     * Connection ID
     */
    public static final String		CONNECTION_ID = "ConnectionID";

    /** 
     * Creation Time
     */
    public static final String		CREATION_TIME = "CreationTime";

    /** 
     * Destination Name
     */
    public static final String		DESTINATION_NAME = "DestinationName";

    /** 
     * Destination Names (that match wildcard)
     */
    public static final String		DESTINATION_NAMES = "DestinationNames";

    /** 
     * Destination Type
     */
    public static final String		DESTINATION_TYPE = "DestinationType";

    /** 
     * Flow Paused
     */
    public static final String		FLOW_PAUSED = "FlowPaused";

    /** 
     * Host
     */
    public static final String		HOST = "Host";

    /** 
     * Number of messages sent from producer.
     */
    public static final String		NUM_MSGS = "NumMsgs";

    /** 
     * Producer ID
     */
    public static final String		PRODUCER_ID = "ProducerID";

    /** 
     * Service Name
     */
    public static final String		SERVICE_NAME = "ServiceName";

    /** 
     * User
     */
    public static final String		USER = "User";

    /** 
     * Wildcard (whether the producer is a wildcard or not)
     */
    public static final String		WILDCARD = "Wildcard";

    /*
     * Class cannot be instantiated
     */
    private ProducerInfo() {
    }
}
