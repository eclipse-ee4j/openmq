/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.bridge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeStateTest {
    private static final int RESOURCE_KEY_POS = 0;
    private static final int STATE_POS = RESOURCE_KEY_POS + 1;
    private static final int STRING_POS = STATE_POS + 1;

    private static final Object[][] KEY_STATE_STRING = {
            { "BS1501", Bridge.State.STARTING, "It's 1" },
            { "BS1502", Bridge.State.STARTED, "It's 2" },
            { "BS1503", Bridge.State.STOPPING, "It's 3" },
            { "BS1504", Bridge.State.STOPPED, "It's 4" },
            { "BS1505", Bridge.State.PAUSING, "It's 5" },
            { "BS1506", Bridge.State.PAUSED, "It's 6" },
            { "BS1507", Bridge.State.RESUMING, "It's 7" },
    };

    private static final ResourceBundle BUNDLE = new ListResourceBundle() {
        @Override
        protected Object[][] getContents() {
            return extractKeysAndStrings(KEY_STATE_STRING, RESOURCE_KEY_POS, STRING_POS);
        }
    };

    @ParameterizedTest
    @MethodSource("stateAndString")
    void testThatEveryStateUsesBundleToGetString(Bridge.State state, String expectedString) {
        assertThat(state.toString(BUNDLE)).isEqualTo(expectedString);
    }

    @Test
    void testThatMethodSourceMapsAllEnumValues() {
        List<Object> mappedStates = extractStates(KEY_STATE_STRING, STATE_POS);

        assertThat(mappedStates).containsAll(Arrays.asList(Bridge.State.values()));
    }

    static Stream<Arguments> stateAndString() {
        return extractStatesAndStrings(KEY_STATE_STRING, STATE_POS, STRING_POS);
    }

    private static List<Object> extractStates(Object[][] keyStateAndString, int statePos) {
        return Stream.of(keyStateAndString).map((Object[] tuple) -> tuple[statePos])
                .collect(Collectors.toList());
    }

    private static Stream<Arguments> extractStatesAndStrings(Object[][] keyStateAndString, int statePos, int stringPos) {
        return Stream.of(keyStateAndString).map((Object[] tuple) -> {
            return Arguments.of(tuple[statePos], tuple[stringPos]);
        });
    }

    private static Object[][] extractKeysAndStrings(Object[][] keyStateAndString, int keyPos, int stringPos) {
        return (Stream.of(keyStateAndString).map((Object[] tuple) -> {
            return new Object[] { tuple[keyPos], tuple[stringPos] };
        }).collect(Collectors.toList())).toArray(new Object[][] {});
    }
}
