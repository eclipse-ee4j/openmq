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
 * @(#)ProducerOperations.java	1.7 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on producer operations.
 */
public class ProducerOperations {
    /** 
     * Get list of producer IDs.
     */
    public static final String		GET_PRODUCER_IDS = "getProducerIDs";

    /** 
     * Get info on all producers
     */
    public static final String		GET_PRODUCER_INFO = "getProducerInfo";

    /** 
     * Get info on specified producer (via ID)
     */
    public static final String		GET_PRODUCER_INFO_BY_ID = "getProducerInfoByID";

    /** 
     * Get producer wildcards
     */
    public static final String		GET_PRODUCER_WILDCARDS = "getProducerWildcards";

    /** 
     * Get number of producers that use a specific wildcard
     */
    public static final String		GET_NUM_WILDCARD_PRODUCERS = "getNumWildcardProducers";

    /*
     * Class cannot be instantiated
     */
    private ProducerOperations() {
    }
    
}
