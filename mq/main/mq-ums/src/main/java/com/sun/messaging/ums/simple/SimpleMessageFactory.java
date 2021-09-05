/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.ums.simple;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 *
 * @author chiaming
 */
public class SimpleMessageFactory {

    public static final String UTF8 = "UTF-8";

    public static SimpleMessage createMessage(Map props, InputStream in) throws IOException {

        String body = readHttpBody(props, in);

        SimpleMessage message = new SimpleMessage(props, body);

        // message.setMessageProperties(props);

        // message.setText(body);

        return message;
    }

    public static String readHttpBody(Map props, InputStream in) throws IOException {

        DataInputStream din = new DataInputStream(in);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] bytes = new byte[1024];

        boolean more = true;
        int len = 0;

        while (more) {

            len = din.read(bytes);

            if (len > 0) {
                baos.write(bytes, 0, len);
            } else if (len < 0) {
                more = false;
            }
        }

        byte[] body = baos.toByteArray();

        // String enc = req.getCharacterEncoding();

        String enc = UTF8;

        baos.close();
        din.close();

        String text = new String(body, enc);
        return text;
    }

}
