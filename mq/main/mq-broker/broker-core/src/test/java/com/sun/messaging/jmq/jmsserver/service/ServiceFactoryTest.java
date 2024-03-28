/*
 * Copyright (c) 2024 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceFactoryTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jms",
            "ssljms",
            "admin",
            "ssladmin",
            "httpjms",
            "httpsjms",
    })
    void testStandardServiceName(String serviceName) {
        assertThat(ServiceFactory.isDefaultStandardServiceName(serviceName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ldap",
            "ldaps",
    })
    void testNonStandardServiceName(String serviceName) {
        assertThat(ServiceFactory.isDefaultStandardServiceName(serviceName)).isFalse();
    }


    @Test
    void testNullServiceName() {
        assertThatThrownBy(() -> ServiceFactory.isDefaultStandardServiceName(null)).isInstanceOf(NullPointerException.class);
    }
}