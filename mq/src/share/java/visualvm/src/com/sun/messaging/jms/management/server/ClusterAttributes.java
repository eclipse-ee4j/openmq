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
 * @(#)ClusterAttributes.java	1.6 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on cluster attributes.
 */
public class ClusterAttributes {
    /** 
     * Cluster config file URL
     */
    public static final String		CONFIG_FILE_URL = "ConfigFileURL";

    /** 
     * Cluster ID
     */
    public static final String		CLUSTER_ID = "ClusterID";

    /** 
     * Is cluster highly available ?
     */
    public static final String		HIGHLY_AVAILABLE = "HighlyAvailable";

    /** 
     * Local master broker info
     */
    public static final String		LOCAL_BROKER_INFO = "LocalBrokerInfo";

    /** 
     * Cluster master broker info
     */
    public static final String		MASTER_BROKER_INFO = "MasterBrokerInfo";

    /*
     * Class cannot be instantiated
     */
    private ClusterAttributes() {
    }
    
}
