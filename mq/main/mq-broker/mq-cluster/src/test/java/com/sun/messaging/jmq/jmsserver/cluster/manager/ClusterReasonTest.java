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

package com.sun.messaging.jmq.jmsserver.cluster.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClusterReasonTest {

    @ParameterizedTest
    @MethodSource("reasonAndString")
    void testStringification(ClusterReason reason, String expectedString) {
        assertThat(reason.toString()).isEqualTo(expectedString);
    }
    
    static Stream<Arguments> reasonAndString() {
        return Stream.of(
                Arguments.of(ClusterReason.ADDED, expectedStringification("ADDED")),
                Arguments.of(ClusterReason.REMOVED, expectedStringification("REMOVED")),
                Arguments.of(ClusterReason.STATUS_CHANGED, expectedStringification("STATUS_CHANGED")),
                Arguments.of(ClusterReason.STATE_CHANGED, expectedStringification("STATE_CHANGED")),
                Arguments.of(ClusterReason.VERSION_CHANGED, expectedStringification("VERSION_CHANGED")),
                Arguments.of(ClusterReason.ADDRESS_CHANGED, expectedStringification("ADDRESS_CHANGED")),
                Arguments.of(ClusterReason.MASTER_BROKER_CHANGED, expectedStringification("MASTER_BROKER_CHANGED"))
        );
    }
    
    private static String expectedStringification(String value) {
        return String.format("ClusterReason[%s]", value);
    }
}
