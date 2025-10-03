/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util.log;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(MockitoExtension.class)
class SysLogHandlerTest {
    @Mock
    private Logger logger;

    @Nested
    class Issue289 {
        @Test
        void testLevelForLibraryErrorLog() {
            SysLogHandler.logSharedLibraryError(logger, "Library not found");

            Mockito.verify(logger).log(eq(Level.INFO), anyString());
        }
    }

    @Test
    void testLibraryErrorLogMessage() {
        String expectedLog = "[S2004]: Log output channel com.sun.messaging.jmq.util.log.SysLogHandler is disabled: Library not found (2)";

        SysLogHandler.logSharedLibraryError(logger, "Library not found (2)");

        Mockito.verify(logger).log(any(Level.class), eq(expectedLog));
    }
}
