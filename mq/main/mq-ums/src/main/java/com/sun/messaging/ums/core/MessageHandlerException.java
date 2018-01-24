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

package com.sun.messaging.ums.core;

import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPException;

/**
 * MessageHandler exception class.  Message handler throws this exception if
 * unable to process SOAP message headers properly.
 *
 * @author Chiaming Yang
 */
public class MessageHandlerException extends SOAPException {

    /**
     * The SOAP fault message. Message handler construct and set fault values in
     * the message before throwing the exception.
     */
    protected SOAPMessage faultMessage = null;

    /**
     * Deafult constructor.
     */
    public MessageHandlerException () {
        super();
    }

    /**
     * Constructor with a reason for the exception.
     */
    public MessageHandlerException (String reason) {
        super (reason);
    }

    /**
     * Constructor with a reason and a cause throwable.
     */
    public MessageHandlerException (String reason, Throwable throwable) {
        super (reason, throwable);
    }

    /**
     * Constructor with a cause throwable.
     */
    public MessageHandlerException (Throwable throwable) {
        super (throwable);
    }

    /**
     * Set SOAP fault message to this exception.
     */
    public void setSOAPFaultMessage (SOAPMessage fault) {
        this.faultMessage = fault;
    }

    /**
     * Get SOAP fault message from this exception.
     */
    public SOAPMessage getSOAPFaultMessage () {
        return faultMessage;
    }
}
