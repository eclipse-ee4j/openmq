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

class SysMessageID_setIPAddress_Test {
    @Test
    void testSetIpV4AddressWithMac() {
        var msgId = new SysMessageID();

        msgId.setIPAddress(new byte[] { 10, 91, 92, 93 },
                new byte[] { 31, 32, 33, 34, 35, 36 });

        assertThat(msgId.getIPAddress())
                .containsExactly(-1, 0, 0, 0, 31, 32, 33, 34, 35, 36, -1, -1, 10, 91, 92, 93);
    }

    @Test
    void testSetIpV6AddressWithIgnoredMac() {
        var msgId = new SysMessageID();

        msgId.setIPAddress(new byte[] { 11, 12, 13, 14, 21, 22, 23, 24, 31, 32, 33, 34, 41, 42, 43, 44 },
                new byte[] { 71, 72, 73, 74, 75, 76 });

        assertThat(msgId.getIPAddress())
                .containsExactly(11, 12, 13, 14, 21, 22, 23, 24, 31, 32, 33, 34, 41, 42, 43, 44);
    }
}
