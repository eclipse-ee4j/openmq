/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsclient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import jakarta.jms.JMSException;

@ExtendWith(MockitoExtension.class)
public class XAQueueSessionImplTest extends QueueSessionAbstractBase {
    private XAQueueSessionImpl queueSession;

    @Mock
    private ConnectionImpl connection;

    @Mock
    private ConnectionMetaDataImpl connectionMetaData;

    @Mock
    private ProtocolHandler protocolHandler;

    @BeforeEach
    public void setUp() throws JMSException {
        connectionMetaData.setJMSXConsumerTXID = false;
        connection.connectionMetaData = connectionMetaData;
        connection.protocolHandler = protocolHandler;

        queueSession = new XAQueueSessionImpl(connection, false, 0);
    }

    @Override
    protected XAQueueSessionImpl getQueueSession() {
        return queueSession;
    }
}
