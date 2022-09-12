/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021, 2022 Contributors to the Eclipse Foundation
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

import java.io.OutputStream;
import java.io.IOException;

/**
 * This class implements a BASE64 Character encoder as specified in RFC1521. This RFC is part of the MIME specification
 * as published by the Internet Engineering Task Force (IETF). Unlike some other encoding schemes there is nothing in
 * this encoding that indicates where a buffer starts or ends.
 *
 * This means that the encoded text will simply start with the first line of encoded text and end with the last line of
 * encoded text.
 *
 * @see CharacterEncoder
 * @see BASE64Decoder
 */

public class BASE64Encoder extends CharacterEncoder {

    /** this class encodes three bytes per atom. */
    @Override
    protected int bytesPerAtom() {
        return (3);
    }

    /**
     * this class encodes 57 bytes per line. This results in a maximum of 57/3 * 4 or 76 characters per output line. Not
     * counting the line termination.
     */
    @Override
    protected int bytesPerLine() {
        return (57);
    }

    private static final char pem_array[] = Base64PemCharArray.getPemArray();

    /**
     * encodeAtom - Take three bytes of input and encode it as 4 printable characters. Note that if the length in len is
     * less than three is encodes either one or two '=' signs to indicate padding characters.
     */
    @Override
    protected void encodeAtom(OutputStream outStream, byte data[], int offset, int len) throws IOException {
        byte a, b, c;

        if (len == 1) {
            a = data[offset];
            b = 0;
            c = 0;
            outStream.write(pem_array[(a >>> 2) & 0x3F]);
            outStream.write(pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
            outStream.write('=');
            outStream.write('=');
        } else if (len == 2) {
            a = data[offset];
            b = data[offset + 1];
            c = 0;
            outStream.write(pem_array[(a >>> 2) & 0x3F]);
            outStream.write(pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
            outStream.write(pem_array[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)]);
            outStream.write('=');
        } else {
            a = data[offset];
            b = data[offset + 1];
            c = data[offset + 2];
            outStream.write(pem_array[(a >>> 2) & 0x3F]);
            outStream.write(pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
            outStream.write(pem_array[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)]);
            outStream.write(pem_array[c & 0x3F]);
        }
    }
}
