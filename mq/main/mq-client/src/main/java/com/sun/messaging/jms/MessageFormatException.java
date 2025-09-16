/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2025 Contributors to the Eclipse Foundation
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
 * This exception must be thrown when a JMS client attempts to use a data type not supported by a message or attempts to
 * read data in a message as the wrong type. It must also be thrown when equivalent type errors are made with message
 * property values. For example, this exception must be thrown if <CODE>StreamMessage.writeObject</CODE> is given an
 * unsupported class or if <CODE>StreamMessage.readShort</CODE> is used to read a <CODE>boolean</CODE> value. Note that
 * the special case of a failure caused by an attempt to read improperly formatted <CODE>String</CODE> data as numeric
 * values must throw the <CODE>java.lang.NumberFormatException</CODE>.
 **/

public class MessageFormatException extends jakarta.jms.MessageFormatException implements Loggable {

    @Serial
    private static final long serialVersionUID = 3690579937453753234L;

    private boolean isLogged = false;

    /**
     * Constructs a <CODE>MessageFormatException</CODE> with the specified reason and error code.
     *
     * @param reason a description of the exception
     * @param errorCode a string specifying the vendor-specific error code
     **/
    public MessageFormatException(String reason, String errorCode) {
        super(reason, errorCode);
    }

    /**
     *
     * <P>
     * If running under J2SE1.4 or above, this method will also set the cause of the <CODE>MessageFormatException</CODE>.
     * When a backtrace of the <CODE>MessageFormatException</CODE> is printed using
     * {@link java.lang.Exception#printStackTrace printStackTrace} using {@link java.lang.Throwable#printStackTrace
     * printStackTrace} a backtrace of the cause will also get printed.
     *
     **/
    @Override
    public synchronized void setLinkedException(Exception ex) {
        super.setLinkedException(ex);
        try {
            initCause(ex);
        } catch (Throwable t) {

        }
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
