/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation.
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

package com.sun.messaging.jmq.admin.bkrutil;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerErrorEvent;
import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

import jakarta.jms.JMSException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

class BrokerAdminConnTest {
    @Nested
    class OnExceptionTest {
        class TestBrokerAdminConn extends BrokerAdminConn {
            TestBrokerAdminConn() throws Exception { super("localhost", 7676); }
            @Override public void clearStatusEvent() {}
            @Override public String getAdminQueueDest() { return null; }
            @Override public String getAdminMessagePropNameMessageType() { return null; }
            @Override public String getAdminMessagePropNameErrorString() { return null; }
            @Override public String getAdminMessagePropNameStatus() { return null; }
            @Override public int getAdminMessageStatusOK() { return 0; }
            @Override public int getAdminMessageTypeSHUTDOWN_REPLY() { return 0; }
            @Override public CommonCmdStatusEvent newCommonCmdStatusEvent(int type) { return null; }
            @Override public CommonCmdStatusEvent getCurrentStatusEvent() { return null; }
            AdminEvent brokerEvent;
            @Override public void fireAdminEventDispatched(AdminEvent bee) { brokerEvent = bee; }
        };

        private TestBrokerAdminConn bac;

        @BeforeEach
        void setUp() throws Exception {
            bac = new TestBrokerAdminConn();
        }

        @Test
        void testOnExceptionWithGoodbyeCodeByReference() throws Exception {
            testOnException(ClientResources.X_BROKER_GOODBYE, BrokerErrorEvent.ALT_SHUTDOWN);
        }

        @Test
        void testOnExceptionWithGoodbyeCodeByNewValue() throws Exception {
            testOnException(new String(ClientResources.X_BROKER_GOODBYE), BrokerErrorEvent.ALT_SHUTDOWN);
        }

        private void testOnException(String errorCode, int eventExpectedType) throws Exception {
            JMSException jmse = new JMSException("test", errorCode);

            bac.onException(jmse);

            assertThat(bac.brokerEvent).isNotNull();
            assertThat(bac.brokerEvent.getType()).isEqualTo(eventExpectedType);
        }
    }
}
