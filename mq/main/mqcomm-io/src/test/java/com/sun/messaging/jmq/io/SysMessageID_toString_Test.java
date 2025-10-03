/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SysMessageID_toString_Test {
    @Test
    void testToStringOfUninitialized() {
        var sysMessageId = new SysMessageID();

        assertThat(sysMessageId.toString()).isEqualTo("0-0.0.0.0-0-0");
    }

    @Test
    void testToStringOfManuallyInitialized() {
        var sysMessageId = new SysMessageID();
        sysMessageId.setSequence(10);
        sysMessageId.setIPAddress(new byte [] { 127, 76, 76, 0 });
        sysMessageId.setPort(8076);
        sysMessageId.setTimestamp(500300400);

        assertThat(sysMessageId.toString()).isEqualTo("10-127.76.76.0-8076-500300400");
    }

    @Test
    void testToStringOfManuallyInitializedToMaxLength() {
        var sysMessageId = new SysMessageID();
        sysMessageId.setSequence(2147483647);
        sysMessageId.setIPAddress(new byte [] { 127, -100, 100, 100 });
        sysMessageId.setPort(2147483647);
        sysMessageId.setTimestamp(9223372036854775807L);

        assertThat(sysMessageId.toString()).isEqualTo("2147483647-127.156.100.100-2147483647-9223372036854775807");
    }
}
