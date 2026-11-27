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

package com.sun.messaging.jmq.io;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Packet_requireValid_Test {
    private Packet packet;

    @BeforeEach
    void prepareTemplatePacket() {
        packet = new Packet();
        packet.packetSize = 200;
        packet.propertyOffset = 100;
        packet.propertySize = 100;
    }

    @Test
    void testTemplatePacket() throws InvalidPacketException {
        Packet.requireValid(packet);
    }

    @Test
    void testForPacketSizeSmallerThanHeader() {
        packet.packetSize = 34;
        assertThatExceptionOfType(InvalidPacketException.class)
                .isThrownBy(() -> Packet.requireValid(packet))
                .withMessage("Packet size (34 bytes) is smaller than the header size (72 bytes).");
    }

    @Test
    void testForPropertyOffsetSmallerThanHeader() {
        packet.propertyOffset = 50;
        assertThatExceptionOfType(InvalidPacketException.class)
                .isThrownBy(() -> Packet.requireValid(packet))
                .withMessage("Property offset (50) is less than the header size (72 bytes).");
    }

    @Test
    void testForNegativePropertySize() {
        packet.propertySize = -11;
        assertThatExceptionOfType(InvalidPacketException.class)
                .isThrownBy(() -> Packet.requireValid(packet))
                .withMessage("Packet size (-11 bytes) cannot be negative.");
    }

    @Test
    void testForPropertyOffsetGreaterThanPacketSize() {
        packet.propertyOffset = 300;
        assertThatExceptionOfType(InvalidPacketException.class)
                .isThrownBy(() -> Packet.requireValid(packet))
                .withMessage("Property offset (300) exceeds the packet size (200 bytes).");
    }

    @Test
    void testForPropertyBoundaryGreaterThanPacketSize() {
        packet.propertyOffset = 100;
        packet.propertySize = 150;
        assertThatExceptionOfType(InvalidPacketException.class)
                .isThrownBy(() -> Packet.requireValid(packet))
                .withMessage("Property end offset (100 + 150) exceeds the packet size (200 bytes).");
    }
}
