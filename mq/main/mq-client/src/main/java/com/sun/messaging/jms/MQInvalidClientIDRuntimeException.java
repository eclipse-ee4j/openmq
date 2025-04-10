/*
 * Copyright (c) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jms;

import java.io.Serial;


import jakarta.jms.InvalidClientIDException;
import com.sun.messaging.jmq.jmsclient.logging.Loggable;

/**
 * <P>
 * This class is the MQ-specific implementation of jakarta.jms.InvalidClientIDRuntimeException and adds the methods
 * setLogState and getlogState
 **/
public class MQInvalidClientIDRuntimeException extends jakarta.jms.InvalidClientIDRuntimeException implements Loggable {

    /**
     * 
     */
    @Serial
    private static final long serialVersionUID = 1586734540691627038L;
    private boolean isLogged = false;

    /**
     * Constructs a <code>MQInvalidClientIDRuntimeException</code> with the specified detail message
     *
     * @param detailMessage a description of the exception
     **/
    public MQInvalidClientIDRuntimeException(String detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs a <code>MQInvalidClientIDRuntimeException</code> with the specified detail message and error code.
     *
     * @param detailMessage a description of the exception
     * @param errorCode a provider-specific error code
     **/
    public MQInvalidClientIDRuntimeException(String detailMessage, String errorCode) {
        super(detailMessage, errorCode);
    }

    /**
     * Constructs a <code>MQInvalidClientIDRuntimeException</code> with the specified detail message, error code and cause
     *
     * @param detailMessage a description of the exception
     * @param errorCode a provider-specific error code
     * @param cause the underlying cause of this exception
     */
    public MQInvalidClientIDRuntimeException(String detailMessage, String errorCode, Throwable cause) {
        super(detailMessage, errorCode, cause);
    }

    /**
     * Construct a <code>MQInvalidClientIDRuntimeException</code> to wrap the specified InvalidClientIDException
     *
     * @param cause the underlying cause of this exception
     */
    public MQInvalidClientIDRuntimeException(InvalidClientIDException cause) {
        super(cause.getMessage(), cause.getErrorCode(), cause);
    }

    /**
     * Specify whether this object is logged.
     *
     * @param state whether this object is logged
     */
    @Override
    public void setLogState(boolean state) {
        this.isLogged = state;
    }

    /**
     * return whether this object is logged
     *
     * @return whether this object is logged
     */
    @Override
    public boolean getLogState() {
        return this.isLogged;
    }
}
