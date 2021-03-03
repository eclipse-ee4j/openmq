/*
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

package com.sun.messaging.jmq.jmsclient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import jakarta.jms.JMSException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@ExtendWith(MockitoExtension.class)
public class XAQueueSessionImplTest {
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

    @Test
    void createTopicShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createTopic(null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createTopic");
    }

    @Test
    void createTemporaryTopicShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createTemporaryTopic(); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createTemporaryTopic");
    }

    @Test
    void createDurableSubscriber2ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createDurableSubscriber(null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createDurableSubscriber");
    }

    @Test
    void createDurableSubscriber4ArgTopicShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createDurableSubscriber(null, null, null, false); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createDurableSubscriber");
    }

    @Test
    void createSharedConsumer2ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createSharedConsumer(null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createSharedConsumer");
    }

    @Test
    void createSharedConsumer3ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createSharedConsumer(null, null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createSharedConsumer");
    }

    @Test
    void createDurableConsumer2ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createDurableConsumer(null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createDurableConsumer");
    }

    @Test
    void createDurableConsumer4ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createDurableConsumer(null, null, null, false); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createDurableConsumer");
    }

    @Test
    void createSharedDurableConsumer2ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createSharedDurableConsumer(null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createSharedDurableConsumer");
    }

    @Test
    void createSharedDurableConsumer3ArgShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.createSharedDurableConsumer(null, null, null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "createSharedDurableConsumer");
    }

    @Test
    void unsubscribeShouldThrowISEx() throws JMSException {
        assertThatExceptionOfType(jakarta.jms.IllegalStateException.class)
          .isThrownBy(() -> { queueSession.unsubscribe(null); })
          .withMessage("[C4071]: Invalid method in this domain: %s", "unsubscribe");
    }
}
