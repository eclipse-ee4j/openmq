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
 * @(#)DestinationOperations.java	1.7 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on destination operations.
 */
public class DestinationOperations {
    /** 
     * Create a destination.
     */
    public static final String		CREATE = "create";

    /** 
     * Compact a destination.
     */
    public static final String		COMPACT = "compact";

    /** 
     * Destroy a destination
     */
    public static final String		DESTROY = "destroy";

    /** 
     * Get active consumers IDs
     */
    public static final String		GET_ACTIVE_CONSUMER_IDS = "getActiveConsumerIDs";

    /** 
     * Get backup consumers IDs
     */
    public static final String		GET_BACKUP_CONSUMER_IDS = "getBackupConsumerIDs";

    /** 
     * Get consumer IDs
     */
    public static final String		GET_CONSUMER_IDS = "getConsumerIDs";

    /** 
     * Get connection - relevant for temporary destinations only.
     */
    public static final String		GET_CONNECTION = "getConnection";

    /** 
     * Get list of destination MBean object names.
     */
    public static final String		GET_DESTINATIONS = "getDestinations";

    /** 
     * Get producer IDs
     */
    public static final String		GET_PRODUCER_IDS = "getProducerIDs";

    /** 
     * Get wildcards used on this destination
     * i.e. Wildcards used by consumers/producers that match this destination
     */
    public static final String		GET_WILDCARDS = "getWildcards";

    /** 
     * Get consumer wildcards used on this destination
     */
    public static final String		GET_CONSUMER_WILDCARDS = "getConsumerWildcards";

    /** 
     * Get number of consumers on this destination that use a specific wildcard
     */
    public static final String		GET_NUM_WILDCARD_CONSUMERS = "getNumWildcardConsumers";

    /** 
     * Get producer wildcards used on this destination
     */
    public static final String		GET_PRODUCER_WILDCARDS = "getProducerWildcards";

    /** 
     * Get number of producers on this destination that use a specific wildcard
     */
    public static final String		GET_NUM_WILDCARD_PRODUCERS = "getNumWildcardProducers";

    /** 
     * Pause a destination.
     */
    public static final String		PAUSE = "pause";

    /** 
     * Purge a destination.
     */
    public static final String		PURGE = "purge";

    /** 
     * Resume a destination.
     */
    public static final String		RESUME = "resume";

    /*
     * Class cannot be instantiated
     */
    private DestinationOperations() {
    }
    
}
