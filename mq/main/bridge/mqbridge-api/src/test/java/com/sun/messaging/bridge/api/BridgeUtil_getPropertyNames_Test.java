/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class BridgeUtil_getPropertyNames_Test {
    @Test
    void shouldThrowNPExOnBothNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> BridgeUtil.getPropertyNames(null, null));
    }

    @Test
    void shouldThrowNPExOnNullProperties() {
        assertThatNullPointerException()
                .isThrownBy(() -> BridgeUtil.getPropertyNames("abcd", null));
    }

    @Test
    void shouldThrowNPExOnNullPrefix() {
        var properties = new Properties();

        properties.setProperty("xyz", "2900");
        properties.setProperty("zyu", "2901");
        properties.setProperty("abc", "2902");
        properties.setProperty("abcd", "2903");
        properties.setProperty("abce", "2904");
        properties.setProperty("abde", "2905");

        assertThatNullPointerException()
                .isThrownBy(() -> BridgeUtil.getPropertyNames(null, properties));
    }

    @Test
    void shouldFindPrefixedNames() {
        var properties = new Properties();

        properties.setProperty("xyz", "2900");
        properties.setProperty("zyu", "2901");
        properties.setProperty("abc", "2902");
        properties.setProperty("abcd", "2903");
        properties.setProperty("abce", "2904");
        properties.setProperty("abde", "2905");

        var prefixedProperties = BridgeUtil.getPropertyNames("abc", properties);

        assertThat(prefixedProperties).hasSize(3);
        assertThat(prefixedProperties).contains("abc", "abcd", "abce");
    }

    @Test
    void shouldNotFindNotPrefixedNames() {
        var properties = new Properties();

        properties.setProperty("xyz", "2900");
        properties.setProperty("zyu", "2901");
        properties.setProperty("abc", "2902");
        properties.setProperty("abcd", "2903");
        properties.setProperty("abce", "2904");
        properties.setProperty("abde", "2905");

        var prefixedProperties = BridgeUtil.getPropertyNames("abc", properties);

        assertThat(prefixedProperties).hasSize(3);
        assertThat(prefixedProperties).doesNotContain("xyz", "zyu", "abde");
    }

    @Test
    void foundNamesCollectionShouldBeModifiable() {
        var properties = new Properties();

        properties.setProperty("xyz", "2900");
        properties.setProperty("zyu", "2901");
        properties.setProperty("abc", "2902");
        properties.setProperty("abcd", "2903");
        properties.setProperty("abce", "2904");
        properties.setProperty("abde", "2905");

        var prefixedProperties = BridgeUtil.getPropertyNames("abc", properties);

        assertThat(prefixedProperties).hasSize(3);
        assertDoesNotThrow(() -> prefixedProperties.add("vfvg"));
        assertThat(prefixedProperties).hasSize(4);
        assertThat(prefixedProperties).contains("vfvg");
    }
}
