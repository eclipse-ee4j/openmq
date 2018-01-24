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
 * @(#)ClusterOperations.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on cluster operations.
 */
public class ClusterOperations {
    /** 
     * Get addresses for all brokers in cluster.
     */
    public static final String		GET_BROKER_ADDRESSES = "getBrokerAddresses";

    /** 
     * Get IDs for all brokers in cluster.
     */
    public static final String		GET_BROKER_IDS = "getBrokerIDs";

    /** 
     * Get info on all brokers in cluster.
     */
    public static final String		GET_BROKER_INFO = "getBrokerInfo";

    /** 
     * Get info on specified broker (via address) in cluster.
     */
    public static final String		GET_BROKER_INFO_BY_ADDRESS = "getBrokerInfoByAddress";

    /** 
     * Get info on specified broker (via ID) in cluster.
     */
    public static final String		GET_BROKER_INFO_BY_ID = "getBrokerInfoByID";

    /** 
     * Cluster reload operation.
     */
    public static final String		RELOAD = "reload";
    
    /** 
     * Get info on specified broker (via address) in cluster.
     */
    public static final String		CHANGE_MASTER_BROKER = "changeMasterBroker";

    /*
     * Class cannot be instantiated
     */
    private ClusterOperations() {
    }
    
}
