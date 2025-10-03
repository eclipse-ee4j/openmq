/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.ra;

import jakarta.jms.*;

import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;

public class DirectTransactionManagedSession extends DirectSession {

    /**
     * Logging
     */
    private static final String _className = "com.sun.messaging.jms.ra.DirectSession";

    /** Creates a new instance of DirectTransactionManagedSession */
    public DirectTransactionManagedSession(DirectConnection dc, JMSService jmsservice, long sessionId, SessionAckMode ackMode) throws JMSException {
        super(dc, jmsservice, sessionId, ackMode);
    }

    @Override
    protected void _initSession() {
        _loggerOC.entering(_className, "constructor():_init()");
        this.isAsync = false;
        this.inDeliver = false;
        this.isClosed = false;
        this.isClosing = false;
        this.isStopped = true;
        this.ackOnFetch = ((this.ackMode == SessionAckMode.AUTO_ACKNOWLEDGE) || (this.ackMode == SessionAckMode.TRANSACTED)
                || (this.ackMode == SessionAckMode.DUPS_OK_ACKNOWLEDGE));
        if (!this.dc.isStopped()) {
            this._start();
        }
        if (this.dc.isManaged()) {
            if (this.dc.mc.xaTransactionStarted()) {
                this.transactionId = this.dc.mc.getTransactionID();
            }
        }
    }
}
