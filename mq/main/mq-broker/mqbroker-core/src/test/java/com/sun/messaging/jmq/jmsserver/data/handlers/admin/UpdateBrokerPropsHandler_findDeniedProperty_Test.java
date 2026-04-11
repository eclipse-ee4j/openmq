/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class UpdateBrokerPropsHandler_findDeniedProperty_Test {

    @Test
    void shouldReturnNullForNullProperties() {
        assertThat(UpdateBrokerPropsHandler.findDeniedProperty(null)).isNull();
    }

    @Test
    void shouldReturnNullForEmptyProperties() {
        assertThat(UpdateBrokerPropsHandler.findDeniedProperty(new Properties())).isNull();
    }

    @Test
    void shouldAllowUnrelatedProperty() {
        Properties p = new Properties();
        p.setProperty("imq.autocreate.queue", "true");
        p.setProperty("imq.log.level", "INFO");

        assertThat(UpdateBrokerPropsHandler.findDeniedProperty(p)).isNull();
    }

    @Test
    void shouldRejectClusterUrl() {
        Properties p = new Properties();
        p.setProperty("imq.cluster.url", "file:///etc/passwd");

        assertThat(UpdateBrokerPropsHandler.findDeniedProperty(p)).isEqualTo("imq.cluster.url");
    }

    @Test
    void shouldRejectClusterUrlEvenWhenMixedWithAllowedProperties() {
        Properties p = new Properties();
        p.setProperty("imq.log.level", "DEBUG");
        p.setProperty("imq.cluster.url", "http://attacker.example/exfil");
        p.setProperty("imq.autocreate.queue", "false");

        assertThat(UpdateBrokerPropsHandler.findDeniedProperty(p)).isEqualTo("imq.cluster.url");
    }
}
