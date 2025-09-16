/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.io.*;
import com.sun.messaging.jmq.jmsclient.logging.Loggable;

/**
 * <P>
 * This exception is thrown when a method is invoked at an illegal or inappropriate time or if the provider is not in an
 * appropriate state for the requested operation. For example, this exception must be thrown if
 * <CODE>Session.commit</CODE> is called on a non-transacted session. This exception is also called when a domain
 * inappropriate method is called, such as calling <CODE>TopicSession.CreateQueueBrowser</CODE>.
 **/

public class IllegalStateException extends jakarta.jms.IllegalStateException implements Loggable {

    @Serial
    private static final long serialVersionUID = -6725501689233767640L;

    private boolean isLogged = false;

    /**
     * Constructs a <CODE>IllegalStateException</CODE> with the specified reason and error code.
     *
     * @param reason a description of the exception
     * @param errorCode a string specifying the vendor-specific error code
     **/
    public IllegalStateException(String reason, String errorCode) {
        super(reason, errorCode);
    }

    /**
     * Constructs a <CODE>IllegalStateException</CODE> with the specified reason and with the error code defaulting to null.
     *
     * @param reason a description of the exception
     **/
    public IllegalStateException(String reason) {
        super(reason);
    }

    /**
     *
     * <P>
     * This method will also set the cause of the <CODE>IllegalStateException</CODE>.
     * When a backtrace of the <CODE>IllegalStateException</CODE> is printed using
     * {@link java.lang.Exception#printStackTrace printStackTrace} using {@link java.lang.Throwable#printStackTrace
     * printStackTrace} a backtrace of the cause will also get printed.
     *
     **/
    @Override
    public synchronized void setLinkedException(Exception ex) {
        super.setLinkedException(ex);
        initCause(ex);
    }

    /**
     * set state to true if this object is logged.
     *
     * @param state boolean
     */
    @Override
    public void setLogState(boolean state) {
        this.isLogged = state;
    }

    /**
     * get logging state of this object.
     *
     * @return boolean true if this object is logged.
     */
    @Override
    public boolean getLogState() {
        return this.isLogged;
    }

}
