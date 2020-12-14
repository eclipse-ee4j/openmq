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

package com.sun.messaging.jmq.jmsclient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionHandlerTest {

    @Test
    void testGetExceptionMessageForNullErrorCode() {
        String errorCode = null;
        Exception source = new NullPointerException();

        String message = ExceptionHandler.getExceptionMessage(source, errorCode);

        assertThat(message).isEqualTo("[C4038]: java.lang.NullPointerException");
    }

    @Test
    void testGetExceptionMessageForNonNullErrorCode() {
        String errorCode = "C4039";
        Exception source = new NullPointerException();

        String message = ExceptionHandler.getExceptionMessage(source, errorCode);

        assertThat(message).isEqualTo("[C4039]: Cannot delete destination - cause: java.lang.NullPointerException");
    }

    @Test
    void testGetExceptionMessageForNonNullErrorCodeAsNewString() {
        String errorCode = new String("C4039");
        Exception source = new NullPointerException();

        String message = ExceptionHandler.getExceptionMessage(source, errorCode);

        assertThat(message).isEqualTo("[C4039]: Cannot delete destination - cause: java.lang.NullPointerException");
    }

    @Test
    void testGetExceptionMessageForExplicit4038ErrorCode() {
        String errorCode = "C4038";
        Exception source = new NullPointerException();

        String message = ExceptionHandler.getExceptionMessage(source, errorCode);

        assertThat(message).isEqualTo("[C4038]: java.lang.NullPointerException");
    }

    @Test
    void testGetExceptionMessageForExplicit4038ErrorCodeAsNewString() {
        String errorCode = new String("C4038");
        Exception source = new NullPointerException();

        String message = ExceptionHandler.getExceptionMessage(source, errorCode);

        assertThat(message).isEqualTo("[C4038]: java.lang.NullPointerException");
    }
}
