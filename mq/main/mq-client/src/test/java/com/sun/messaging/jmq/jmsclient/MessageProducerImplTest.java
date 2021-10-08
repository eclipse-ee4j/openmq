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

import jakarta.jms.CompletionListener;
import jakarta.jms.JMSException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@ExtendWith(MockitoExtension.class)
public class MessageProducerImplTest {
    private MessageProducerImpl messageProducer;

    @Mock
    private SessionImpl session;

    @Mock
    private CompletionListener completionListener;

    @BeforeEach
    public void setUp() throws JMSException {
        messageProducer = new MessageProducerImpl(session, null);
    }

    @Test
    void setTimeToLiveToNegativeValShouldThrowJMSEx() throws JMSException {
        long timeToLive = -1;
        assertThatExceptionOfType(jakarta.jms.JMSException.class)
          .isThrownBy(() -> { messageProducer.setTimeToLive(timeToLive); })
          .withMessage("[C4051]: Invalid delivery parameter.  %s : %d", "TimeToLive", timeToLive);
    }

    @Test
    void sendWithNegativeTimeToLiveShouldThrowJMSEx() throws JMSException {
        long timeToLive = -1;
        assertThatExceptionOfType(jakarta.jms.JMSException.class)
          .isThrownBy(() -> { messageProducer.send(null,0,0,timeToLive,completionListener); })
          .withMessage("[C4051]: Invalid delivery parameter.  %s : %d", "TimeToLive", timeToLive);
    }

    @Test
    void sendToDestWithNegativeTimeToLiveShouldThrowJMSEx() throws JMSException {
        long timeToLive = -1;
        assertThatExceptionOfType(jakarta.jms.JMSException.class)
          .isThrownBy(() -> { messageProducer.send(null,null,0,0,timeToLive,completionListener); })
          .withMessage("[C4051]: Invalid delivery parameter.  %s : %d", "TimeToLive", timeToLive);
    }

}
