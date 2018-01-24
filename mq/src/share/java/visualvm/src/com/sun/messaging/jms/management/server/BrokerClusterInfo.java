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
 * @(#)BrokerClusterInfo.java	1.13 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * This class contains constants/names for fields in the CompositeData
 * that is returned by the operations of the Cluster Monitor and Cluster
 * Config MBeans.
 */
public class BrokerClusterInfo implements java.io.Serializable  {
    /**
     * Address of broker in the form of host:port
     */
    public static final String ADDRESS			= "Address";

    /**
     * The ID of the broker.
     */
    public static final String ID     			= "ID";

    /**
     * State of broker
     */
    public static final String STATE  			= "State";

    /**
     * State label of broker. This is useful when viewe in a JMX browser
     * like jconsole.
     */
    public static final String STATE_LABEL		= "StateLabel";

    /**
     * Number of messages stored in this broker's message store.
     */
    public static final String NUM_MSGS			= "NumMsgs";

    /**
     * ID of the broker that has taken over this broker's store.
     */
    public static final String TAKEOVER_BROKER_ID	= "TakeoverBrokerID";

    /**
     * Status Timestamp.
     */
    public static final String STATUS_TIMESTAMP		= "StatusTimestamp";

    /*
     * Class cannot be instantiated
     */
    private BrokerClusterInfo() {
    }
}
