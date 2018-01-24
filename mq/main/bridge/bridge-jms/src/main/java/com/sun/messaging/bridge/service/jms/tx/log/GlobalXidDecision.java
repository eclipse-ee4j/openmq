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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.io.*;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;


/**
 * @author amyk
 */ 

public class GlobalXidDecision implements Externalizable {

    public static final int COMMIT = 0;
    public static final int ROLLBACK = 1;

    private GlobalXid _xid = null;
    private int _decision = COMMIT;

    public GlobalXidDecision() {}

    public GlobalXidDecision(GlobalXid xid, int decision) {

        if (decision != COMMIT && decision != ROLLBACK) {
            throw new IllegalArgumentException(
            "Invalid global decision value: "+decision);
        }
        _xid = xid;
        _decision = decision;
    }

    public GlobalXid getGlobalXid() {
        return _xid;
    }

    public int getGlobalDecision() {
        return _decision;
    }

    public void writeExternal(ObjectOutput out) throws IOException {

        _xid.write(out);
        out.writeInt(_decision);
    }

    public void readExternal(ObjectInput in) throws IOException,
                                        ClassNotFoundException {
        _xid = GlobalXid.read(in);
        _decision = in.readInt();
    }

    private static String decisionString(int d) {
        if (d == COMMIT) return "COMMIT";
        if (d == ROLLBACK) return "ROLLBACK";
        return "UNKNOWN";
    }

    public String toString() { 
        return _xid.toString()+"("+decisionString(_decision)+")";
    }
}
