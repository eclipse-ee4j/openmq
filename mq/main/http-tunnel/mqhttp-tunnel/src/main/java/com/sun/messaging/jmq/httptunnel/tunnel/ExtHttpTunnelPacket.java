/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.httptunnel.tunnel;

class ExtHttpTunnelPacket extends HttpTunnelPacket {
    private long txTime = 0;
    private int retransmitCount = 0;
    private boolean dirtyFlag = false;

    public void setTxTime(long txTime) {
        this.txTime = txTime;
    }

    public long getTxTime() {
        return txTime;
    }

    public void setRetransmitCount(int retransmitCount) {
        this.retransmitCount = retransmitCount;
    }

    public int getRetransmitCount() {
        return retransmitCount;
    }

    public void setDirtyFlag(boolean dirtyFlag) {
        this.dirtyFlag = dirtyFlag;
    }

    public boolean getDirtyFlag() {
        return dirtyFlag;
    }
}

