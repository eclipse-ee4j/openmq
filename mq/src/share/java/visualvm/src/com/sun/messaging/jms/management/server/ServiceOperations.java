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
 * @(#)ServiceOperations.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on service operations.
 */
public class ServiceOperations {
    /** 
     * Get list of service MBean object names
     */
    public static final String		GET_SERVICES = "getServices";

    /** 
     * Get list of connection MBean object names
     */
    public static final String		GET_CONNECTIONS = "getConnections";

    /** 
     * Get list of consumer IDs.
     */
    public static final String		GET_CONSUMER_IDS = "getConsumerIDs";

    /** 
     * Get list of producer IDs.
     */
    public static final String		GET_PRODUCER_IDS = "getProducerIDs";

    /** 
     * Pause service.
     */
    public static final String		PAUSE = "pause";

    /** 
     * Resume service.
     */
    public static final String		RESUME = "resume";

    /*
     * Class cannot be instantiated
     */
    private ServiceOperations() {
    }
    
}
