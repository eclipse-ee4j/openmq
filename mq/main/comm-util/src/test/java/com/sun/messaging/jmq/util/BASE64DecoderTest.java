/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class BASE64DecoderTest {
    private BASE64Decoder decoder = new BASE64Decoder();

    @Test
    void testDecodingDate() throws IOException {
        var encoded = "VGh1IDE2IERlYyAyMDozNzo1NyBVVEMgMjAyMQ==";

        var decoded = new String(decoder.decodeBuffer(encoded));

        assertThat(decoded).isEqualTo("Thu 16 Dec 20:37:57 UTC 2021");
    }
}
