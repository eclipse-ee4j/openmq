/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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

package com.sun.messaging.jmq.util.lists;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

class EventTypeTest {
    @ParameterizedTest
    @MethodSource("typesWithNames")
    void testName(EventType type, String expectedName) {
        assertThat(type.toString()).isEqualTo(expectedName);
    }

    @ParameterizedTest
    @MethodSource("typesWithEvents")
    void testEvent(EventType type, int expectedEvent) {
        assertThat(type.getEvent()).isEqualTo(expectedEvent);
    }

    static Stream<Arguments> typesWithNames() {
        return Stream.of(
            Arguments.of(EventType.SIZE_CHANGED, "SIZE_CHANGED"),
            Arguments.of(EventType.BYTES_CHANGED, "BYTES_CHANGED"),
            Arguments.of(EventType.SET_CHANGED, "SET_CHANGED"),
            Arguments.of(EventType.EMPTY, "EMPTY"),
            Arguments.of(EventType.FULL, "FULL"),
            Arguments.of(EventType.BUSY_STATE_CHANGED, "BUSY_STATE_CHANGED"),
            Arguments.of(EventType.SET_CHANGED_REQUEST, "SET_CHANGED_REQUEST")
        );
    }

    static Stream<Arguments> typesWithEvents() {
        return Stream.of(
            Arguments.of(EventType.SIZE_CHANGED, 0),
            Arguments.of(EventType.BYTES_CHANGED, 1),
            Arguments.of(EventType.SET_CHANGED, 2),
            Arguments.of(EventType.EMPTY, 3),
            Arguments.of(EventType.FULL, 4),
            Arguments.of(EventType.BUSY_STATE_CHANGED, 5),
            Arguments.of(EventType.SET_CHANGED_REQUEST, 6)
        );
    }
}
