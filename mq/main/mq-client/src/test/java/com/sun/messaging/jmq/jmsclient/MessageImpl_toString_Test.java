/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;

class MessageImpl_toString_Test {
    @Test
    void testToString() throws JMSException {
        var message = new MessageImpl();
        
        assertThat(message.toString()).isEqualTo(
                """
  \nClass:\t\t\tcom.sun.messaging.jmq.jmsclient.MessageImpl
  getJMSMessageID():\tID:0-0.0.0.0-0-0
  getJMSTimestamp():\t0
  getJMSCorrelationID():\tnull
  JMSReplyTo:\t\tnull
  JMSDestination:\t\tnull
  getJMSDeliveryMode():\tPERSISTENT
  getJMSRedelivered():\tfalse
  getJMSType():\t\tnull
  getJMSExpiration():\t0
  getJMSDeliveryTime():\t0
  getJMSPriority():\t4
  Properties:\t\tnull""");
    }
}
