/*
 * Copyright (c) 2020 Contributors to Eclipse Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;

class ConnectionMetaDataTest {
    @Test
    void testJMSVersionConsistency() throws JMSException {
        ConnectionMetaData cmdi = makeConnectionMetaData();

        int maj = cmdi.getJMSMajorVersion();
        int min = cmdi.getJMSMinorVersion();
        String ver = cmdi.getJMSVersion();

        assertEquals(String.format("%d.%d", maj, min), ver);
    }

    private static ConnectionMetaData makeConnectionMetaData() {
        ConnectionMetaData cmd = new ConnectionMetaData(null) {

            @Override
            protected boolean hasJMSXAppID() {
                return false;
            }

            @Override
            protected boolean hasJMSXUserID() {
                return false;
            }

            @Override
            protected boolean hasJMSXProducerTXID() {
                return false;
            }

            @Override
            protected boolean hasJMSXConsumerTXID() {
                return false;
            }

            @Override
            protected boolean hasJMSXRcvTimestamp() {
                return false;
            }};
        return cmd;
    }
}
