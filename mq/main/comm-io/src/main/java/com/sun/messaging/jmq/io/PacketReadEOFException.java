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


import java.io.EOFException;

/**
 */
public class PacketReadEOFException extends EOFException {

    private int bytesRead = 0;
    private int packetSize = -1;
    private String appendMessage = null;

    public PacketReadEOFException() {
        super();
    }

    public PacketReadEOFException(String s) {
        super(s);
    }

    public void appendMessage(String msg) {
        appendMessage = msg;
    }

    @Override
    public String getMessage() {
        return super.getMessage()+getString();
    }

    public void setBytesRead(int n) {
        bytesRead = n;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public void setPacketSize(int n) {
        packetSize = n;
    }

    public int getPacketSize() {
        return packetSize;
    }

    @Override
    public String toString() {
        return super.toString()+getString();
    }

    private String getString() {
        return "[bytesRead="+bytesRead+", packetSize="+
                 (packetSize == -1 ? "":packetSize)+"]"+
               (appendMessage == null ? "":appendMessage);
    }
}
