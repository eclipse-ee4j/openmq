/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Enumeration;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MQResourceBundleTest {

    private static ResourceBundle rb = new ResourceBundle() {
        @Override
        protected Object handleGetObject(String key) {
            return "b98765";
        }

        @Override
        public Enumeration<String> getKeys() {
            return null;
        }
    };

    private static MQResourceBundle mqBundle;

    private static String previousLineSeparator;

    @BeforeEach
    void setUp() {
        previousLineSeparator = System.getProperty("line.separator");
        System.setProperty("line.separator", "\n\r");
        mqBundle = new MQResourceBundle(rb);
    }

    @AfterEach
    void tearDown() {
        System.setProperty("line.separator", previousLineSeparator);
    }

    @Test
    void testNoArgString() {
        String kstring = mqBundle.getKString("a12345");

        assertThat(kstring).isEqualTo("[a12345]: b98765");
    }

    @Test
    void testNoArgStringWithThread() {
        Thread.currentThread().setName("junit test thread");
        String kstring = mqBundle.getKTString("abcdefgh");

        assertThat(kstring).startsWith("[abcdefgh]: [");
        assertThat(kstring).contains("junit test thread");
        assertThat(kstring).endsWith("]b98765");
    }

    @Test
    void testToString() {
        String itsRepresentation = mqBundle.toString();
        assertThat(itsRepresentation).startsWith("com.sun.messaging.jmq.util.MQResourceBundle: convertEOL=true cache={}");
        assertThat(itsRepresentation).contains("\n");
        assertThat(itsRepresentation).contains(" resourceBundle=com.sun.messaging.jmq.util.MQResourceBundleTest$1");
    }
}
