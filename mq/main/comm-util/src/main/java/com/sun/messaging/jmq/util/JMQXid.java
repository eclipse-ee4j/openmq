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
 * @(#)JMQXid.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.*;
import javax.transaction.xa.Xid;

/**
 * JMQ version of the RI's Xid Implementation. We extend XidImpl
 * to add methods for marshalling and unmarshaling an Xid to I/O Streams.
 *
 * @see javax.transaction.xa.Xid
 */
public class JMQXid extends XidImpl {

    // compatibility w/ 3.0.1, 3.5, 3.6
    static final long serialVersionUID = -5632229224716804510L;

    public JMQXid() {
        super();
    }

    public JMQXid(Xid foreignXid) {
        super(foreignXid);
    }

    public boolean isNullXid() {
        return (formatId == NULL_XID && gtLength == 0 && bqLength == 0);
    }

    /**
     * Write the Xid to the specified DataOutputStream. This is an
     * alternative to serialization that is faster, more compact and
     * language independent.
     *
     * The data written is guaranteed to be a fixed size. In particular
     * It will be of size
     *
     *      4 + 2 + Xid.MAXGTRIDSIZE + 2 + Xid.MAXBQUALSIZE 
     *
     * Which in practice will be 4 + 2 + 2 + 64 + 64 = 136 bytes
     *
     * If the globalTransactionId or the branchQualifierId is less than
     * MAX*SIZE bytes, then it will be padded with trailing 0's.
     * 
     * The format of the written data will be:
     *
     *<PRE>
     *    0                   1                   2                   3
     *   |0 1 2 3 4 5 6 7|8 9 0 1 2 3 4 5|6 7 8 9 0 1 2 3|4 5 6 7 8 9 0 1|
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                     format Id                                 |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   | globalTransactionId Length    |   branchQualifier Length      |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                                                               |
     *   +                                                               +
     *   |                     globalTransactionId                       |
     *   +                    MAXGTRIDSIZE bytes                         +
     *                           .  .  .
     *   +                                                               +
     *   |                                                               |
     *   +                                                               +
     *   |                                                               |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                                                               |
     *   +                                                               +
     *   |                     branchQualifier                           |
     *   +                    MAXBQUALSIZE bytes                         +
     *                           .  .  .
     *   +                                                               +
     *   |                                                               |
     *   +                                                               +
     *   |                                                               |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   </PRE>
     *
     */
    public void write(DataOutput out)
        throws IOException {

        out.writeInt(formatId);
        out.writeShort(gtLength);
        out.writeShort(bqLength);

        // These are fixed size arrays. 
        out.write(globalTxnId, 0, MAXGTRIDSIZE);
        out.write(branchQualifier, 0, MAXBQUALSIZE);
    }

    /**
     * Read the Xid from the input stream
     */
    public static JMQXid read(DataInput in)
        throws IOException {

        JMQXid xid = new JMQXid();
        xid.formatId = in.readInt();
        xid.gtLength = in.readShort();
        xid.bqLength = in.readShort();

        // These are fixed size arrays
        in.readFully(xid.globalTxnId, 0, MAXGTRIDSIZE);
        in.readFully(xid.branchQualifier, 0, MAXBQUALSIZE);

        return xid;
    }

    /**
     * Size in bytes that the object will be when marshalled.
     */
    public static int size() {
        return 8 + MAXGTRIDSIZE + MAXBQUALSIZE;
    }
}
