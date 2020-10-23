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

package com.sun.messaging.jmq.jmsclient;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.jms.JMSException;

class ConnectionMetaDataImplTest {
    @Test
    void testJMSVersionConsistency() throws JMSException {
        ConnectionImpl stubCon = makeConnectionImpl();
        ConnectionMetaDataImpl cmdi = new ConnectionMetaDataImpl(stubCon);

        int majorVersion = cmdi.getJMSMajorVersion();
        int minorVersion = cmdi.getJMSMinorVersion();
        String version = cmdi.getJMSVersion();

        assertEquals(String.format("%d.%d", majorVersion, minorVersion), version);
    }

    private static ConnectionImpl makeConnectionImpl() throws JMSException {
        ConnectionImpl cimpl = Mockito.mock(ConnectionImpl.class);
        return cimpl;
    }
}
