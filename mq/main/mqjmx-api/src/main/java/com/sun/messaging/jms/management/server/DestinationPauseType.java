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
 * @(#)DestinationPauseType.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing constants for destination pause type.
 */
public class DestinationPauseType {
    /** 
     * Pauses delivery of messages from producers
     */
    public static final String PRODUCERS = "PRODUCERS";

    /** 
     * Pauses delivery of messages to consumers
     */
    public static final String CONSUMERS = "CONSUMERS";

    /** 
     * Pauses delivery of messages to consumers and from producers.
     */
    public static final String ALL = "ALL";

    /*
     * Class cannot be instantiated
     */
    private DestinationPauseType() {
    }
}
