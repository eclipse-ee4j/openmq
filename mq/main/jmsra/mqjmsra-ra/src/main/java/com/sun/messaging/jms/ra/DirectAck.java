/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectAck implements JMSAck {

    /** The connectionId of the JMSAck */
    @Getter(onMethod_ = @Override)
    private final long connectionId;

    /** The sessionId of the JMSAck */
    @Getter(onMethod_ = @Override)
    private final long sessionId;

    /** The consumerId of the JMSAck */
    @Getter(onMethod_ = @Override)
    private final long consumerId;

    /** The OpenMQ SysMessageID of the JMSAck */
    @Getter(onMethod_ = @Override)
    private final SysMessageID sysMessageID;

    /** The messageAckType of the JMSAck */
    @Getter(onMethod_ = @Override)
    private final MessageAckType messageAckType;

    /**
     * Return the transactionId of this JMSAck
     *
     * @return 0
     */
    @Override
    public long getTransactionId() {
        return 0;
    }
}
