/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsservice;

import java.io.Serial;
import java.util.Map;

public class JMSServiceException extends java.lang.Exception {

    @Serial
    private static final long serialVersionUID = -5745390870553763460L;
    private transient JMSServiceReply reply;

    public JMSServiceException(Map<? extends String, ? extends Object> replyProps) {
        reply = new JMSServiceReply(replyProps);
    }

    /**
     * Creates a new instance of JMSServiceException with a message
     */
    public JMSServiceException(String message, Map<? extends String, ? extends Object> replyProps) {
        super(message);
        reply = new JMSServiceReply(replyProps);
    }

    /**
     * Creates a new instance of JMSServiceException with the specified message that was caused by the specified Throwable.
     * This method appends the cause's own message to the specified message.
     */
    public JMSServiceException(String message, Throwable cause, Map<? extends String, ? extends Object> replyProps) {
        super("" + message + " Caused by:" + cause, cause);
        reply = new JMSServiceReply(replyProps);
    }

    /**
     * gets the JMSServiceReply associated with this JMSServiceException
     *
     * @return The JMSServiceReply associated with this JMSServiceException
     */
    public JMSServiceReply getJMSServiceReply() {
        return reply;
    }
}
