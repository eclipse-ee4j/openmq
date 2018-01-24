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
 * @(#)DestinationLimitBehavior.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on destination limit behavior.
 * These values specify how a destination responds when a memory-limit 
 * threshold is reached.
 */
public class DestinationLimitBehavior {
    /** 
     * Unknown destination limit behavior.
     */
    public static final String UNKNOWN = "UNKNOWN";

    /** 
     * Flow control - the producers are slowed down.
     */
    public static final String FLOW_CONTROL = "FLOW_CONTROL";

    /** 
     * Remove oldest - throws out the oldest messages.
     */
    public static final String REMOVE_OLDEST = "REMOVE_OLDEST";

    /** 
     * Rejects the newest messages. The producing client gets an exception for 
     * rejection of persistent messages only. To use this limit behavior with non-persistent 
     * messages, set the imqAckOnProduce connection factory attribute.
     */
    public static final String REJECT_NEWEST = "REJECT_NEWEST";

    /** 
     * Throws out the lowest priority messages according to age of the messages 
     * (producing client receives no notification of message deletion).
     */
    public static final String REMOVE_LOW_PRIORITY = "REMOVE_LOW_PRIORITY";

    /*
     * Class cannot be instantiated
     */
    private DestinationLimitBehavior() {
    }
}
