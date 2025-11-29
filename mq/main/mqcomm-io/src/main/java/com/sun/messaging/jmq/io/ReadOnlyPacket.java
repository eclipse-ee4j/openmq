/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.io;

import java.io.*;

/**
 * This class is a mostly immutable encapsulation of a JMQ packet. It is only "mostly" immutable because it can be
 * modified by calling the readPacket() method which modifies the state of the object to reflect the packet just read.
 *
 * WARNING! This class emphasizes performance over safety. In particular the readPacket() method is NOT synchronized.
 * You must only call readPacket() in a single threaded environment (or do the synchronization yourself). Once the
 * object is initialized via a readPacket() you can safely use the accessors and the writePacket() methods in a
 * multi-threaded envornment.
 */

/*
 * 10/27/08 - Changed ReadOnlyPacket to use Packet. We can make this change now because the client has updated to a
 * current enough JDK to use nio.
 *
 * The names (ReadOnlyPacket, ReadWritePacket) are maintained to prevent code modification on the client.
 */
public class ReadOnlyPacket extends Packet implements Cloneable {

    /**
     * this method MUST be called before a packet is created
     */
    public static void setDefaultVersion(short version) {
        defaultVersion = version;
    }

    @Override
    public void setIndempotent(boolean set) {
    }

    /**
     * Check if this packet matches the specified system message id.
     *
     * @return "true" if the packet matches the specified id, else "false"
     */
    public boolean isEqual(SysMessageID id) {
        return sysMessageID.equals(id);
    }

    // backwards compatibility
    public int getInterestID() {
        return (int) super.getConsumerID();
    }

    /**
     * Make a deep copy of this packet. This will be slow.
     */
    @Override
    public Object clone() {
        try {
            ReadOnlyPacket rp = new ReadOnlyPacket();
            rp.fill(this, true);
            return rp;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Return a string containing the contents of the packet in a human readable form.
     */
    public String toVerboseString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        this.dump(new PrintStream(bos));

        return bos.toString();
    }
}
