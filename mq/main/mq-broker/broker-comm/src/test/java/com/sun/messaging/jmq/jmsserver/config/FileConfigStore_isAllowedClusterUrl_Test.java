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

package com.sun.messaging.jmq.jmsserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileConfigStore_isAllowedClusterUrl_Test {

    @Test
    void shouldRejectNullUrl() {
        assertThat(FileConfigStore.isAllowedClusterUrl(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:/tmp/cluster.properties",
            "file:///etc/openmq/cluster.properties",
            "FILE:///tmp/cluster.properties",
    })
    void shouldAcceptFileUrl(String spec) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(spec);
        assertThat(FileConfigStore.isAllowedClusterUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://attacker.example/exfil",
            "https://attacker.example/exfil",
            "ftp://attacker.example/payload",
            "jar:file:/tmp/x.jar!/cluster.properties",
    })
    void shouldRejectNonFileUrl(String spec) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(spec);
        assertThat(FileConfigStore.isAllowedClusterUrl(url))
                .as("CVE-2026-24457: only file:// is permitted for imq.cluster.url")
                .isFalse();
    }
}
