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

package com.sun.messaging.bridge.admin.util;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;


class AdminMessageTypeTest {
  @Test
  void testStringForNegativeIndex() {
    assertThat(AdminMessageType.getString(-1)).isEqualTo("INVALID_TYPE(-1)");
  }

  @Test
  void testStringForTooBigIndex() {
    assertThat(AdminMessageType.getString(AdminMessageType.Type.LAST)).isEqualTo("INVALID_TYPE(%d)".formatted(AdminMessageType.Type.LAST));
  }

  @ParameterizedTest
  @MethodSource("getKnownTypes")
  void testKnownTypes(int type, String expected) {
    assertThat(AdminMessageType.getString(type)).isEqualTo(expected);
  }

  static Stream<Arguments> getKnownTypes() {
    return Stream.of(
      Arguments.of(AdminMessageType.Type.NULL, "NULL(0)"),
      Arguments.of(AdminMessageType.Type.DEBUG, "DEBUG(16)"),
      Arguments.of(AdminMessageType.Type.DEBUG_REPLY, "DEBUG_REPLY(17)"),
      Arguments.of(AdminMessageType.Type.LIST, "LIST(18)"),
      Arguments.of(AdminMessageType.Type.LIST_REPLY, "LIST_REPLY(19)"),
      Arguments.of(AdminMessageType.Type.PAUSE, "PAUSE(20)"),
      Arguments.of(AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY(21)"),
      Arguments.of(AdminMessageType.Type.RESUME, "RESUME(22)"),
      Arguments.of(AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY(23)"),
      Arguments.of(AdminMessageType.Type.START, "START(24)"),
      Arguments.of(AdminMessageType.Type.START_REPLY, "START_REPLY(25)"),
      Arguments.of(AdminMessageType.Type.STOP, "STOP(26)"),
      Arguments.of(AdminMessageType.Type.STOP_REPLY, "STOP_REPLY(27)"),
      Arguments.of(AdminMessageType.Type.HELLO, "HELLO(28)"),
      Arguments.of(AdminMessageType.Type.HELLO_REPLY, "HELLO_REPLY(29)")
    );
  }
}
