/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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
 * This exception class is intended to be used for embeded broker
 * static variable access from Globals when the broker has cleaned up
 * due to shutdown.
 */ 
package com.sun.messaging.jmq.jmsserver.util;

public class BrokerShutdownRuntimeException extends RuntimeException 
{
    /**
     * @param msg the detail message
     */
    public BrokerShutdownRuntimeException(String msg) {
        super(msg);
    }
    /**
     * @param msg the detail message
     * @thr the Throwable or null
     */
    public BrokerShutdownRuntimeException(String msg, Throwable thr) {
        super(msg, thr);
    }
}
