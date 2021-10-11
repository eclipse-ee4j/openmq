/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

import lombok.Getter;

/**
 * @author amyk
 */

public class GlobalXidDecision implements Externalizable {

    public static final int COMMIT = 0;
    public static final int ROLLBACK = 1;

    @Getter
    private GlobalXid globalXid = null;

    @Getter
    private int globalDecision = COMMIT;

    public GlobalXidDecision() {
    }

    public GlobalXidDecision(GlobalXid xid, int decision) {

        if (decision != COMMIT && decision != ROLLBACK) {
            throw new IllegalArgumentException("Invalid global decision value: " + decision);
        }
        globalXid = xid;
        globalDecision = decision;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        globalXid.write(out);
        out.writeInt(globalDecision);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        globalXid = GlobalXid.read(in);
        globalDecision = in.readInt();
    }

    private static String decisionString(int d) {
        if (d == COMMIT) {
            return "COMMIT";
        }
        if (d == ROLLBACK) {
            return "ROLLBACK";
        }
        return "UNKNOWN";
    }

    @Override
    public String toString() {
        return globalXid.toString() + "(" + decisionString(globalDecision) + ")";
    }
}
