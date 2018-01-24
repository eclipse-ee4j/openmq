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

package com.sun.messaging.bridge.service.jms.tx;

import java.io.*;
import com.sun.messaging.jmq.util.XidImpl;


/**
 * Xid representing a global xid to facilitate tx logging
 * @author amyk
 */ 

public class GlobalXid extends XidImpl {

    public GlobalXid() {
        super();
    }

    public boolean isNullXid() {
        return (formatId == NULL_XID && gtLength == 0 && bqLength == 0);
    }

    /**
     */
    public void write(DataOutput out) throws IOException {

        out.writeInt(formatId);
        out.writeInt(gtLength);
        out.write(globalTxnId, 0, MAXGTRIDSIZE);
    }

    /**
     */
    public static GlobalXid read(DataInput in) throws IOException {

        GlobalXid gxid = new GlobalXid();
        
        gxid.formatId = in.readInt();
        gxid.gtLength = in.readInt();
        in.readFully(gxid.globalTxnId, 0, MAXGTRIDSIZE);
        return gxid;
    }

    /**
     * size in bytes
     */
    public static int size() {
        return 8 + MAXGTRIDSIZE;
    }
}
