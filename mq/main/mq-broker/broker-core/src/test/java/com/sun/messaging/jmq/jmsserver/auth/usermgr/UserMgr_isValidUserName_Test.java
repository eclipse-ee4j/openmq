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

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UserMgr_isValidUserName_Test {
    @ParameterizedTest
    @MethodSource
    void shouldBeRecognizedAsValidUserName(String userName) {
        assertThat(UserMgr.isValidUserName(userName)).isTrue();
    }

    @ParameterizedTest
    @MethodSource
    void shouldBeRecognizedAsNotValidUserName(String userName) {
        assertThat(UserMgr.isValidUserName(userName)).isFalse();
    }

    static String [] shouldBeRecognizedAsValidUserName() {
        return new String[] {
                "admin",
                "guest",
                "",
                " ",
                "    ",
                "  \t",
                "\t  ",
                " \t ",
        };
    }

    static String [] shouldBeRecognizedAsNotValidUserName() {
        return new String[] {
                null,
                "With:Colon",
                "With*Star",
                "With,Comma",
                "With\nLineFeed",
                "With\rCarriageReturn"
        };
    }
}
