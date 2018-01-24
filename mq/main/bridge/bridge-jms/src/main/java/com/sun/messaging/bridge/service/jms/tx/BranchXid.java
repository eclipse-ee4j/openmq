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
 *  Xid for a branch to facilitate tx loging
 *
 * @author amyk
 */ 

public class BranchXid extends XidImpl {

    public BranchXid() {
        super();
    }

    public boolean isNullXid() {
        return (formatId == NULL_XID && gtLength == 0 && bqLength == 0);
    }

    /**
     */
   public void write(DataOutput out) throws IOException {

        out.writeInt(bqLength);
		out.write(branchQualifier, 0, MAXBQUALSIZE);
    }

    /**
     * formatId and globalTxnId must set explicitly afterward 
     */
    public static BranchXid read(DataInput in) throws IOException {

        BranchXid bxid = new BranchXid();

        bxid.bqLength = in.readInt();
        in.readFully(bxid.branchQualifier, 0, MAXBQUALSIZE);
        return bxid;
    }

    /**
     * size in bytes
     */
    public static int size() {
        return 8 + MAXBQUALSIZE;
    }
}
