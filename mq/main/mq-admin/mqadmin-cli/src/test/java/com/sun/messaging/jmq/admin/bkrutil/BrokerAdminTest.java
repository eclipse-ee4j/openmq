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

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrokerAdminTest {
    @Nested
    class PrintTest {
        private Properties ht;

        @BeforeEach
        void prepareHashTable() {
            ht = new Properties();
            ht.setProperty("a", "1");
            ht.setProperty("b", "2");
        }

        @Test
        void shouldHaveExpectedTitle() {
            StringBuilder message = new StringBuilder();

            BrokerAdmin.print(ht, "\tExpected title:", "\t  ", "=", message::append);

            assertThat(message).startsWith("\tExpected title:");
        }

        @Test
        void shouldHaveElements() {
            StringBuilder message = new StringBuilder();

            BrokerAdmin.print(ht, "Any Title Will Do", "\t  ", "=", message::append);

            assertThat(message).contains("\t  a=1", "\t  b=2");
        }
    }
}
