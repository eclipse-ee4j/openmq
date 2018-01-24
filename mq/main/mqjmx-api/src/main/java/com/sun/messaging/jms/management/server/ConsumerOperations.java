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
 * @(#)ConsumerOperations.java	1.8 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on consumer operations.
 */
public class ConsumerOperations {
    /** 
     * Get list of consumer IDs
     */
    public static final String		GET_CONSUMER_IDS = "getConsumerIDs";

    /** 
     * Get info on all consumers
     */
    public static final String		GET_CONSUMER_INFO = "getConsumerInfo";

    /** 
     * Get info on specified consumer (via ID)
     */
    public static final String		GET_CONSUMER_INFO_BY_ID = "getConsumerInfoByID";

    /** 
     * Purge a durable.
     */
    public static final String		PURGE = "purge";

    /** 
     * Get consumer wildcards
     */
    public static final String		GET_CONSUMER_WILDCARDS = "getConsumerWildcards";

    /** 
     * Get number of consumers that use a specific wildcard
     */
    public static final String		GET_NUM_WILDCARD_CONSUMERS = "getNumWildcardConsumers";

    /*
     * Class cannot be instantiated
     */
    private ConsumerOperations() {
    }
    
}
