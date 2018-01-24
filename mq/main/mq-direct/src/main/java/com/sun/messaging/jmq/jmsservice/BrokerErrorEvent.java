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
 * @(#)BrokerErrorEvent.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

/**
 *
 */
public class BrokerErrorEvent extends BrokerEvent {

    /**
     *  The error number of the error
     */
    private int errorNumber;

    /**
     *  The timestamp of when the error occurred
     */
    private long errorTimestamp;

    /**
     *  The error level of the error
     */
    private int errorLevel;

    /** Creates a new instance of BrokerErrorEvent */
    public BrokerErrorEvent(Object source, int errno, int level,
            long timestamp, String msg) {
        super (source, BrokerEvent.Type.ERROR, msg);
        errorNumber = errno;
        errorLevel = level;
        errorTimestamp = timestamp;
    }
    
}
