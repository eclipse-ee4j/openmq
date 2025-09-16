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
 * This exception must be thrown when a provider rejects a user name/password submitted by a client. It may also be
 * thrown for any case where a security restriction prevents a method from completing.
 **/

public class JMSSecurityException extends jakarta.jms.JMSSecurityException implements Loggable {

    @Serial
    private static final long serialVersionUID = -4667462298248936011L;

    private boolean isLogged = false;

    /**
     * Constructs a <CODE>JMSSecurityException</CODE> with the specified reason and error code.
     *
     * @param reason a description of the exception
     * @param errorCode a string specifying the vendor-specific error code
     **/
    public JMSSecurityException(String reason, String errorCode) {
        super(reason, errorCode);
    }

    /**
     * Constructs a <CODE>JMSSecurityException</CODE> with the specified reason and with the error code defaulting to null.
     *
     * @param reason a description of the exception
     **/
    public JMSSecurityException(String reason) {
        super(reason);
    }

    /**
     * Constructs a <CODE>JMSSecurityException</CODE> with the specified reason, error code, and a specified cause.
     *
     * @param reason a description of the exception
     * @param errorCode a string specifying the vendor-specific error code
     * @param cause the cause. A <CODE>null</CODE> value is permitted, and indicates that the cause is non-existent or unknown.
     **/
    public JMSSecurityException(String reason, String errorCode, Throwable cause) {
        super(reason, errorCode);
        if (cause instanceof java.lang.Exception) {
            setLinkedException((Exception) cause);
        }
    }

    /**
     *
     * <P>
     * If running under J2SE1.4 or above, this method will also set the cause of the <CODE>JMSSecurityException</CODE>. When
     * a backtrace of the <CODE>JMSSecurityException</CODE> is printed using {@link java.lang.Exception#printStackTrace
     * printStackTrace} using {@link java.lang.Throwable#printStackTrace printStackTrace} a backtrace of the cause will also
     * get printed.
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
     *
     * <P>
     * If running under versions of the Java platform prior to J2SE1.4, this method will also print the backtrace of the
     * exception linked to this <CODE>JMSSecurityException</CODE> and obtained via
     * {@link jakarta.jms.JMSException#getLinkedException jakarta.jms.JMSException.getLinkedException()}
     **/
    @Override
    public void printStackTrace() {
        this.printStackTrace(System.err);
    }

    /**
     * {@inheritDoc}
     * <P>
     * If running under versions of the Java platform prior to J2SE1.4, this method will also print the backtrace of the
     * exception linked to this <CODE>JMSSecurityException</CODE> and obtained via
     * {@link jakarta.jms.JMSException#getLinkedException jakarta.jms.JMSException.getLinkedException()}
     **/
    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        ExceptionUtils.printStackTrace(this, s);
    }

    /**
     *
     * <P>
     * If running under versions of the Java platform prior to J2SE1.4, this method will also print the backtrace of the
     * exception linked to this <CODE>JMSSecurityException</CODE> and obtained via
     * {@link jakarta.jms.JMSException#getLinkedException}
     **/
    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        ExceptionUtils.printStackTrace(this, s);
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
