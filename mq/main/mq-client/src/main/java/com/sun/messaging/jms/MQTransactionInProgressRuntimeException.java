/*
 * Copyright (c) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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


import jakarta.jms.TransactionInProgressException;
import com.sun.messaging.jmq.jmsclient.logging.Loggable;

/**
 * This class is the MQ-specific implementation of jakarta.jms.MQTransactionInProgressRuntimeException and adds the
 * methods setLogState and getlogState
 **/
public class MQTransactionInProgressRuntimeException extends jakarta.jms.TransactionInProgressRuntimeException implements Loggable {

    @Serial
    private static final long serialVersionUID = -2076716839992436363L;
    private boolean isLogged = false;

    /**
     * Construct a <code>MQMQTransactionInProgressRuntimeException</code> to wrap the specified
     * TransactionInProgressException
     *
     * @param cause the underlying cause of this exception
     */
    public MQTransactionInProgressRuntimeException(TransactionInProgressException cause) {
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
