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
import com.sun.messaging.bridge.service.jms.tx.BranchXid;

import lombok.Getter;

/**
 * @author amyk
 */

public class BranchXidDecision implements Externalizable {

    // must be same as in GlobalXidDecision values
    public static final int COMMIT = 0;
    public static final int ROLLBACK = 1;

    // must not overlap with above
    public static final int HEUR_COMMIT = 50;
    public static final int HEUR_ROLLBACK = 51;
    public static final int HEUR_MIXED = 52;

    @Getter
    private BranchXid branchXid = null;

    @Getter
    private int branchDecision = COMMIT;

    public BranchXidDecision() {
    }

    public BranchXidDecision(BranchXid xid, int decision) throws Exception {
        if (decision != COMMIT && decision != ROLLBACK && decision != HEUR_COMMIT && decision != HEUR_ROLLBACK && decision != HEUR_MIXED) {

            throw new IllegalArgumentException("Invalid decision value: " + decision);
        }
        branchXid = xid;
        branchDecision = decision;
    }

    public boolean isHeuristic() {
        return (branchDecision == HEUR_COMMIT || branchDecision == HEUR_ROLLBACK || branchDecision == HEUR_MIXED);
    }

    public void setBranchDecision(int d) {
        if (d != COMMIT && d != ROLLBACK && d != HEUR_COMMIT && d != HEUR_ROLLBACK && d != HEUR_MIXED) {
            throw new IllegalArgumentException("Invalid decision value: " + d);
        }
        branchDecision = d;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        branchXid.write(out);
        out.writeInt(branchDecision);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        branchXid = BranchXid.read(in);
        branchDecision = in.readInt();
    }

    private static String decisionString(int d) {
        switch (d) {
        case COMMIT:
            return "COMMIT";
        case ROLLBACK:
            return "ROLLBACK";
        case HEUR_COMMIT:
            return "HEUR_COMMIT";
        case HEUR_ROLLBACK:
            return "HEUR_ROLLBACK";
        case HEUR_MIXED:
            return "HEUR_MIXED";
        default:
            return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        return branchXid.toString() + "(" + decisionString(branchDecision) + ")";
    }
}
