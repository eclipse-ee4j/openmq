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
import com.sun.messaging.jmq.Version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.jms.JMSException;

@ExtendWith(MockitoExtension.class)
class ConnectionMetaDataImplTest {
    @Mock
    private ConnectionImpl stubCon;

    @Test
    void testJMSVersionConsistency() throws JMSException {
        var cmdi = new ConnectionMetaDataImpl(stubCon);

        int majorVersion = cmdi.getJMSMajorVersion();
        int minorVersion = cmdi.getJMSMinorVersion();
        var version = cmdi.getJMSVersion();

        assertEquals(String.format("%d.%d", majorVersion, minorVersion), version);
    }

    @Test
    void testProviderVersionConsistency() throws JMSException {
        var cmdi = new ConnectionMetaDataImpl(stubCon);

        int majorVersion = cmdi.getProviderMajorVersion();
        int minorVersion = cmdi.getProviderMinorVersion();
        var version = cmdi.getProviderVersion();

        assertEquals(String.format("%d.%d", majorVersion, minorVersion), version);
    }

    @Test
    void testProviderVersionMatchesProject() throws JMSException {
        var cmdi = new ConnectionMetaDataImpl(stubCon);

        int providerMajorVersion = cmdi.getProviderMajorVersion();
        int providerMinorVersion = cmdi.getProviderMinorVersion();
        var versionFromProperties = new Version(false);

        assertEquals(versionFromProperties.getMajorVersion(), providerMajorVersion);
        assertEquals(versionFromProperties.getMinorVersion(), providerMinorVersion);
    }
}
