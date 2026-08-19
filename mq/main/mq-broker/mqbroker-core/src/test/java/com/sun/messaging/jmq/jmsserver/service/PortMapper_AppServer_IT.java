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

package com.sun.messaging.jmq.jmsserver.service;

import java.nio.channels.SocketChannel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortMapper_AppServer_IT {
    static Object[][] methodsGlassFishUsesWithReflection() {
        return new Object[][] {
            { "handleRequest", new Class [] { SocketChannel.class } },
        };
    }

    /*
     * appserver/jms/jms-core/src/main/java/com/sun/enterprise/connectors/jms/system/ActiveJmsResourceAdapter.java
     */
    @ParameterizedTest
    @MethodSource
    void methodsGlassFishUsesWithReflection(String methodName, Class[] argTypes) throws NoSuchMethodException {
        PortMapper.class.getMethod(methodName, argTypes);
    }
}
