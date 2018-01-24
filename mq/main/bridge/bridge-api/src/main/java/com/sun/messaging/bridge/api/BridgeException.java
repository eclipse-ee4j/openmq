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
 */ 

package com.sun.messaging.bridge.api;

import com.sun.messaging.jmq.io.Status;

/**
 * 
 * @author amyk
 */

public class BridgeException extends Exception
{
    /**
     *
     */
    private int _status = Status.ERROR;


    /**
     *
     * @param msg the detail message
     */
    public BridgeException(String msg) {
        super(msg);
    }

    public BridgeException(String msg, int status) {
        this(msg, null, status);
    }

    /**
     *
     * @param msg the detail message
     * @param thr the root cause
     */
    public BridgeException(String msg, Throwable thr) {
        super(msg, thr);
    }

    /**
     *
     * @param msg the detail message
     * @param thr the root cause
     */
    public BridgeException(String msg, Throwable thr, int status) {
        super(msg, thr);
        _status = status;
    }

    /**
     *
     * @returns the status code
     */
    public int getStatus() {
	    return _status;
    }

}
