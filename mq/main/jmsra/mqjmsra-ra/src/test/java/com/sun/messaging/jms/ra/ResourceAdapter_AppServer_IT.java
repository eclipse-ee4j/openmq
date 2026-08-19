/*
 * Copyright (c) 2026 Contributors to Eclipse Foundation. All rights reserved.
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

import java.util.Properties;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceAdapter_AppServer_IT {
    /*
     * appserver/jms/jms-core/src/main/java/com/sun/enterprise/connectors/jms/system/ActiveJmsResourceAdapter.java
     * appserver/jms/admin/src/main/java/org/glassfish/jms/admin/cli/JMSDestination.java
     */
    @ParameterizedTest()
    @ValueSource(strings = {
            "getPortMapperClientHandler",
            "getJMXServiceURLList",
            "getJMXConnectorEnv",
    })
    void methodsGlassFishUsesWithReflection(String methodName) throws NoSuchMethodException {
        ResourceAdapter.class.getMethod(methodName);
    }

    static Object[][] methodsGlassFishUsesWithReflection() {
        return new Object[][] {
            { "setMasterBroker", new Class [] { String.class } },
            { "setClusterBrokerList", new Class [] { String.class } },
            { "setBrokerProps", new Class [] { Properties.class } },
            { "setConnectionURL", new Class[] { String.class } },
            { "setAdminUsername", new Class[] { String.class } },
            { "setAdminPassword", new Class[] { String.class } },
        };
    }

    /*
     * appserver/jms/jms-core/src/main/java/com/sun/enterprise/connectors/jms/system/ActiveJmsResourceAdapter.java
     * appserver/jms/admin/src/main/java/org/glassfish/jms/admin/cli/JMSDestination.java
     */
    @ParameterizedTest
    @MethodSource
    void methodsGlassFishUsesWithReflection(String methodName, Class[] argTypes) throws NoSuchMethodException {
        ResourceAdapter.class.getMethod(methodName, argTypes);
    }
}
