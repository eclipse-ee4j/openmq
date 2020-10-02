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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sun.messaging.jmq.admin.util.Globals;

class BrokerAdminTest {
    @Nested
    class PrintTest {
        private PrintStream beforeTestOut;
        private ByteArrayOutputStream baos;
        private Properties ht;

        @BeforeEach
        void plumbStdOut() {
            beforeTestOut = System.out;
            baos = new ByteArrayOutputStream();
            PrintStream testOut = new PrintStream(baos);
            System.setOut(testOut);
        }

        @BeforeEach
        void prepareHashTable() {
            ht = new Properties();
            ht.setProperty("a", "1");
            ht.setProperty("b", "2");
        }

        @AfterEach
        void restoreStdOut() throws IOException {
            System.setOut(beforeTestOut);
            baos.close();
        }

        @Test
        void shouldHaveExpectedTitle() {
            BrokerAdmin.print(ht, "\tJMX Connector Info:", "\t  ", "=", Globals::stdOutPrintln);

            String printed = baos.toString();

            assertThat(printed).startsWith("\tJMX Connector Info:");
        }

        @Test
        void shouldHaveElements() {
            BrokerAdmin.print(ht, "Any Title Will Do", "\t  ", "=", Globals::stdOutPrintln);

            String printed = baos.toString();

            assertThat(printed).contains("\t  a=1", "\t  b=2");
        }
    }
}
