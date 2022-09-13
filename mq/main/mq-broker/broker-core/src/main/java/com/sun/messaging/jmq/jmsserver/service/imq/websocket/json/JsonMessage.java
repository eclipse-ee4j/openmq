/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.json;

/**
 * @author amyk
 */
public final class JsonMessage {

    private JsonMessage() {
    }

    public enum Key {
        ;
        public static final String COMMAND = "command";
        public static final String HEADERS = "headers";
        public static final String BODY = "body";
    }

    public enum BodySubKey {
        ;
        public static final String TYPE = "type";
        public static final String ENCODER = "encoder";
        public static final String TEXT = "text";
    }

    public static final String ENCODER_BASE64 = "base64";
    public static final String BODY_TYPE_TEXT = "text";
    public static final String BODY_TYPE_BYTES = "bytes";
}
