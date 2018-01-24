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
 * @(#)ConsumerInfo.java	1.3 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * This class contains constants/names for fields in the CompositeData
 * that is returned by the operations of the Consumer Manager Monitor 
 * MBean.
 */
public class ConsumerInfo implements java.io.Serializable  {
    /** 
     * Acknowledge mode
     */
    public static final String		ACKNOWLEDGE_MODE = "AcknowledgeMode";

    /** 
     * Acknowledge mode label
     */
    public static final String		ACKNOWLEDGE_MODE_LABEL 
							= "AcknowledgeModeLabel";

    /** 
     * Client ID
     */
    public static final String		CLIENT_ID = "ClientID";

    /** 
     * Connection ID
     */
    public static final String		CONNECTION_ID = "ConnectionID";

    /** 
     * Consumer ID
     */
    public static final String		CONSUMER_ID = "ConsumerID";

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
     * Durable (whether the consume is a durable or not)
     */
    public static final String		DURABLE = "Durable";

    /** 
     * DurableActive (whether the durable is active or not)
     */
    public static final String		DURABLE_ACTIVE = "DurableActive";

    /** 
     * Durable name
     */
    public static final String		DURABLE_NAME = "DurableName";

    /** 
     * Flow Paused
     */
    public static final String		FLOW_PAUSED = "FlowPaused";

    /** 
     * Host
     */
    public static final String		HOST = "Host";

    /** 
     * Last acknowledge time
     */
    public static final String		LAST_ACK_TIME = "LastAckTime";

    /** 
     * Number of messages held for consumer.
     */
    public static final String		NUM_MSGS = "NumMsgs";

    /** 
     * Number of messages still held for consumer because
     * acks for them from the consumer are still pending.
     */
    public static final String		NUM_MSGS_PENDING_ACKS 
						= "NumMsgsPendingAcks";

    /** 
     * Selector
     */
    public static final String		SELECTOR = "Selector";

    /** 
     * Service Name
     */
    public static final String		SERVICE_NAME = "ServiceName";

    /** 
     * User
     */
    public static final String		USER = "User";

    /** 
     * Wildcard (whether the consumer is a wildcard or not)
     */
    public static final String		WILDCARD = "Wildcard";

    /** 
     * Number of messages still held for consumer because
     * either they are queued for deliver or pending an ack
     */
    public static final String		NUM_MSGS_PENDING 
						= "NumMsgsPending";

    /**
     * Next message which should be delivered
     */

     public static final String NEXT_MESSAGE_ID = "NextMessageID";

    /*
     * Class cannot be instantiated
     */
    private ConsumerInfo() {
    }
}
