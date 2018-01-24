/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

/*
 */ 

package com.sun.messaging.jmq.io;


import java.io.IOException;

/**
 */
public class InvalidPacketException extends IOException {

    private byte[] bytes = null;

    private String appendMessage = null;

    public InvalidPacketException () {
        super();
    }

    public InvalidPacketException (String s) {
        this(s, null);
    }

    public InvalidPacketException (String s, Throwable e) {
        super(s, e);
    }

    public void appendMessage(String msg) {
        appendMessage = msg;
    }

    @Override
    public String getMessage() {
        return super.getMessage()+
            (appendMessage == null ? "":appendMessage);
    }

    public void setBytes(byte[] data) {
        bytes = data;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return super.toString()+
            (appendMessage == null ? "":appendMessage);
    }
}
