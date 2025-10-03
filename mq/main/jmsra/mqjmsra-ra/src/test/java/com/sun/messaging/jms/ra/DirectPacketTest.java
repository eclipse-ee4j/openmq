/*
 * Copyright (c) 2021 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jms.ra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DirectPacketTest {
    private DirectPacket directPacket;

    @BeforeEach
    public void setUp() throws JMSException {
        directPacket = new DirectPacket(null);
    }

    @Test
    void setJMSExpirationToNegativeValShouldThrowJMSEx() throws JMSException {
        long expiration = -1;
        assertThatExceptionOfType(jakarta.jms.JMSException.class)
          .isThrownBy(() -> { directPacket.setJMSExpiration(expiration); })
          .withMessage("MQJMSRA_DM4001: %s:Invalid expiration=%d", "setJMSExpiration()", expiration);
    }
}
