/*
 * Copyright 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.bridge.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StompFrameMessageTest {

    /*
     * "All frames except the CONNECT and CONNECTED frames will also escape any carriage return, line feed or colon
     * found in the resulting UTF-8 encoded headers."
     * - https://stomp.github.io/stomp-specification-1.2.html#Value_Encoding
     */
    @Test
    void unescapeSpecialChars() {
        // carriage return, line feed, colon and backslash
        assertEquals("\r\n:\\", StompFrameMessage.unescapeSpecialChars("\\r\\n\\c\\\\"));

        // opposite order
        assertEquals("\\:\n\r", StompFrameMessage.unescapeSpecialChars("\\\\\\c\\n\\r"));

        // real world examples
        assertEquals("c:\\Users", StompFrameMessage.unescapeSpecialChars("c\\c\\\\Users"));
        assertEquals("http:\\\\server:port", StompFrameMessage.unescapeSpecialChars("http\\c\\\\\\\\server\\cport"));

        // backslash, c, colon ("\c:")
        assertEquals("\\c:", StompFrameMessage.unescapeSpecialChars("\\\\c\\c"));
    }

    @Test
    void escapeSpecialChars() {
        // carriage return, line feed, colon and backslash
        assertEquals("\\r\\n\\c\\\\", StompFrameMessage.escapeSpecialChars("\r\n:\\"));

        // opposite order
        assertEquals("\\\\\\c\\n\\r", StompFrameMessage.escapeSpecialChars("\\:\n\r"));

        // real world examples
        assertEquals("c\\c\\\\Users", StompFrameMessage.escapeSpecialChars("c:\\Users"));
        assertEquals("http\\c\\\\\\\\server\\cport", StompFrameMessage.escapeSpecialChars("http:\\\\server:port"));

        // backslash, c, colon ("\c:")
        // correctly is escaped to "\\c\c"
        assertEquals("\\\\c\\c", StompFrameMessage.escapeSpecialChars("\\c:"));
    }
}