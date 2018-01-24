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
 * @(#)TakeoverLockException.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * This class provides information about problems encountered when acquiring
 * takeover lock for a failed broker. The exception is thrown to signify a
 * takeover lock could not be obtained.
 */

public class TakeoverLockException extends BrokerException {

    private HABrokerInfo bkrInfo = null; // Broker info before takeover started

    /**
     * Constructs a TakeoverLockException
     */
    public TakeoverLockException(String msg) {
        super(msg);
    }

    public TakeoverLockException(String msg, Throwable t) {
        super(msg, t);
    }

    /**
     * Set the broker info before takeover started.
     * @param bkrInfo the broker info before takeover started
     */ 
    public void setBrokerInfo(HABrokerInfo bkrInfo) {
        this.bkrInfo = bkrInfo;
    }

    /**
     * Get the broker info before takeover started.
     * @return HABrokerInfo broker info before takeover started
     */
    public HABrokerInfo getBrokerInfo() {
	return bkrInfo;
    }
}
