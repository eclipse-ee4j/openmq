/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.ums.core;

import lombok.Getter;
import jakarta.xml.soap.SOAPMessage;


import jakarta.xml.soap.SOAPException;

import java.io.Serial;

/**
 * MessageHandler exception class. Message handler throws this exception if unable to process SOAP message headers
 * properly.
 *
 * @author Chiaming Yang
 */
public class MessageHandlerException extends SOAPException {
    @Serial
    private static final long serialVersionUID = 7801657722575841911L;
    /**
     * The SOAP fault message. Message handler construct and set fault values in the message before throwing the exception.
     */
    @Getter
    protected SOAPMessage sOAPFaultMessage = null;

    public MessageHandlerException() {
    }

    /**
     * Constructor with a reason for the exception.
     */
    public MessageHandlerException(String reason) {
        super(reason);
    }

    /**
     * Constructor with a reason and a cause throwable.
     */
    public MessageHandlerException(String reason, Throwable throwable) {
        super(reason, throwable);
    }

    /**
     * Constructor with a cause throwable.
     */
    public MessageHandlerException(Throwable throwable) {
        super(throwable);
    }
}
